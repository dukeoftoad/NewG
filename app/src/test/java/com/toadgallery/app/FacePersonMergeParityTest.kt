package com.toadgallery.app

import androidx.room.Room
import com.toadgallery.app.data.ai.FacePersonClusterer
import com.toadgallery.app.data.ai.FacePersonFeedbackManager
import com.toadgallery.app.data.ai.FaceCropBounds
import com.toadgallery.app.data.ai.FaceReference
import com.toadgallery.app.data.local.GalleryDao
import com.toadgallery.app.data.local.MediaItemEntity
import com.toadgallery.app.data.local.ToadGalleryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Ente-parity verification for `merge_clusters_to_person_sheet.dart`
 * (ENTE-PEOPLE-PARITY.md row 34, previously "Not verified").
 *
 * NewG's merge/detach is name-assignment semantics: clusters sharing a
 * non-blank name project as one person; clearing a name detaches the cluster.
 * These tests pin the merge projection (membership migration, duplicate
 * resolution, detach, ordering) and the undo-safe property that merge/detach
 * never mutates the underlying assignment rows.
 *
 * `add_files_to_person_page.dart` (row 35) is an intentional deviation for
 * v2.1.0: whole-photo assignment is out of scope — per-face assignment is the
 * shipped interaction, and the two are not conflated.
 */
class FacePersonMergeProjectionTest {

  private fun group(
    id: String,
    photoIds: List<Long>,
    name: String? = null,
    faces: List<FaceReference> = emptyList(),
    bounds: Map<FaceReference, FaceCropBounds> = emptyMap(),
    faceCount: Int = photoIds.size,
  ) = FacePersonGroup(
    id = id,
    photoIds = photoIds,
    faceCount = faceCount,
    coverPhotoId = photoIds.first(),
    name = name,
    faces = faces,
    faceBounds = bounds,
  )

  @Test
  fun sameNameClustersMergeIntoOnePersonWithMigratedMembership() {
    val merged =
      FacePersonClusterer.mergeSameNameGroups(
        listOf(
          group("cluster-a", photoIds = listOf(3L, 1L), name = "Alice",
            faces = listOf(FaceReference(3L, 0), FaceReference(1L, 0))),
          group("cluster-b", photoIds = listOf(2L), name = "Alice",
            faces = listOf(FaceReference(2L, 1))),
        ),
      )

    assertEquals("one merged person", 1, merged.size)
    val person = merged.single()
    assertEquals("synthetic id is person:<lowercased name>", "person:alice", person.id)
    assertEquals("membership migrates, sorted descending", listOf(3L, 2L, 1L), person.photoIds)
    assertEquals("face counts sum", 3, person.faceCount)
    assertEquals("both source clusters recorded", setOf("cluster-a", "cluster-b"), person.clusterIds.toSet())
    assertEquals("faces migrate", 3, person.faces.size)
  }

  @Test
  fun duplicatePhotosFacesAndBoundsDedupeOnMerge() {
    val faceA = FaceReference(7L, 0)
    val merged =
      FacePersonClusterer.mergeSameNameGroups(
        listOf(
          group("cluster-a", photoIds = listOf(7L, 8L), name = "Bo",
            faces = listOf(faceA), bounds = mapOf(faceA to FaceCropBounds(0f, 0f, 1f, 1f))),
          group("cluster-b", photoIds = listOf(7L), name = "Bo",
            faces = listOf(faceA), bounds = mapOf(faceA to FaceCropBounds(0f, 0f, 1f, 1f))),
        ),
      )

    val person = merged.single()
    assertEquals("photo 7 appears once", listOf(8L, 7L), person.photoIds)
    assertEquals("face appears once", listOf(faceA), person.faces)
    assertEquals("bounds keyed once", 1, person.faceBounds.size)
  }

  @Test
  fun clearingNameDetachesClusterFromMergedPerson() {
    val named = listOf(
      group("cluster-a", photoIds = listOf(1L), name = "Cara"),
      group("cluster-b", photoIds = listOf(2L), name = "Cara"),
    )
    assertEquals("named clusters merge", 1, FacePersonClusterer.mergeSameNameGroups(named).size)

    // Clearing the name on cluster-b detaches it: it falls back to its own
    // cluster key while cluster-a keeps the person identity and membership.
    val detached =
      FacePersonClusterer.mergeSameNameGroups(
        listOf(
          group("cluster-a", photoIds = listOf(1L), name = "Cara"),
          group("cluster-b", photoIds = listOf(2L), name = null),
        ),
      )

    assertEquals("person + detached cluster", 2, detached.size)
    val person = detached.single { it.id == "person:cara" }
    assertEquals("person keeps only its own membership", listOf(1L), person.photoIds)
    val cluster = detached.single { it.id == "cluster-b" }
    assertEquals("detached cluster keeps its data", listOf(2L), cluster.photoIds)
    assertNull("detached cluster is unnamed", cluster.name)
  }

  @Test
  fun nameMatchingIsTrimmedCaseInsensitiveAndBlankNamesNeverMerge() {
    val merged =
      FacePersonClusterer.mergeSameNameGroups(
        listOf(
          group("cluster-a", photoIds = listOf(1L), name = "Dana "),
          group("cluster-b", photoIds = listOf(2L), name = "dana"),
        ),
      )
    assertEquals("trimmed/case-variant names merge", 1, merged.size)

    // Blank/whitespace names must NOT merge — each unnamed cluster stays its
    // own group keyed by cluster id (matches Ente: no name, no merge).
    val unnamed =
      FacePersonClusterer.mergeSameNameGroups(
        listOf(
          group("cluster-a", photoIds = listOf(1L), name = "  "),
          group("cluster-b", photoIds = listOf(2L), name = null),
        ),
      )
    assertEquals("blank and null names stay separate", 2, unnamed.size)
    assertTrue(unnamed.all { it.id.startsWith("cluster:") })
  }

  @Test
  fun singleNamedGroupKeepsItsClusterIdentity() {
    val merged =
      FacePersonClusterer.mergeSameNameGroups(
        listOf(group("cluster-a", photoIds = listOf(1L, 2L), name = "Evan")),
      )
    val person = merged.single()
    assertEquals("no synthetic rewrite for a lone group", "cluster-a", person.id)
    assertEquals("name preserved", "Evan", person.name)
  }

  @Test
  fun mergedPeopleSortBySizeThenName() {
    val merged =
      FacePersonClusterer.mergeSameNameGroups(
        listOf(
          group("cluster-1", photoIds = listOf(1L), name = "Zoe"),
          group("cluster-2", photoIds = listOf(2L, 3L, 4L), name = "Amy"),
          group("cluster-3", photoIds = listOf(5L, 6L), name = null),
        ),
      )
    assertEquals(
      "bigger people first; ties by name then cluster id",
      listOf("cluster-2", "cluster-3", "person:zoe"),
      merged.map { it.id },
    )
  }
}

/**
 * Undo-safe property of merge/detach at the data layer: naming and renaming
 * clusters (the merge/detach mechanism) must never mutate FaceAssignment rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FacePersonMergeDaoSafetyTest {
  private lateinit var database: ToadGalleryDatabase
  private lateinit var dao: GalleryDao
  private lateinit var feedback: FacePersonFeedbackManager

  @Before
  fun setUp() {
    database = Room.inMemoryDatabaseBuilder(
      RuntimeEnvironment.getApplication(),
      ToadGalleryDatabase::class.java,
    ).allowMainThreadQueries().build()
    dao = database.galleryDao()
    feedback = FacePersonFeedbackManager(dao)
  }

  @After
  fun tearDown() {
    database.close()
  }

  private suspend fun insertPhoto(title: String, faceCount: Int): Long {
    val faces = (0 until faceCount).joinToString(prefix = "{\"faces\":[", postfix = "]}") {
      """{"faceIndex":$it,"faceId":"$title-face-$it"}"""
    }
    dao.insertMedia(
      listOf(
        MediaItemEntity(
          title = title,
          category = "CAMERA",
          mediaType = "PHOTO",
          dateLabel = "2026-01-01",
          weekGroup = "2026-W01",
          monthGroup = "2026-01",
          timestamp = 0L,
          locationName = "",
          hasGps = false,
          mapX = 0f,
          mapY = 0f,
          aspectRatio = 1f,
          tagsCsv = "",
          ocrText = "",
          exifSummary = "",
          faceDataJson = faces,
        ),
      ),
    )
    return dao.observeVisibleMedia().first().firstOrNull { it.title == title }
      ?: error("insert failed: $title")
  }

  @Test
  fun assignmentsSurviveMergeAndDetachAcrossClusters() = runBlocking {
    val photoA = insertPhoto("photo-a", faceCount = 1)
    val photoB = insertPhoto("photo-b", faceCount = 1)
    feedback.assignFace(photoA, 0, "cluster-a")
    feedback.assignFace(photoB, 0, "cluster-b")

    // Merge: both clusters take the same name.
    feedback.setPersonName("cluster-a", "Fern")
    feedback.setPersonName("cluster-b", "Fern")

    val assignmentA = dao.getFaceAssignmentsForPhoto(photoA).single()
    val assignmentB = dao.getFaceAssignmentsForPhoto(photoB).single()
    assertEquals("cluster-a membership intact after merge", "cluster-a", assignmentA.personClusterId)
    assertEquals("cluster-b membership intact after merge", "cluster-b", assignmentB.personClusterId)

    // Detach: clear the name on cluster-b.
    feedback.setPersonName("cluster-b", null)
    assertEquals("detach deletes no membership", 1, dao.getFaceAssignmentsForPhoto(photoB).size)
    assertEquals("cluster-b still owns its face", "cluster-b", dao.getFaceAssignmentsForPhoto(photoB).single().personClusterId)
    assertEquals("cluster-a untouched", "cluster-a", dao.getFaceAssignmentsForPhoto(photoA).single().personClusterId)

    // Undo-safety: merge/detach never wrote correction history, so the only
    // undoable entries are the two original assignments.
    assertTrue("undo 1 returns to pre-assignment state", feedback.undoLastCorrection())
    assertTrue("undo 2 returns to pre-assignment state", feedback.undoLastCorrection())
    assertTrue("all corrections undone", dao.getFaceAssignmentsForPhoto(photoA).isEmpty())
    assertTrue("all corrections undone", dao.getFaceAssignmentsForPhoto(photoB).isEmpty())
  }
}
