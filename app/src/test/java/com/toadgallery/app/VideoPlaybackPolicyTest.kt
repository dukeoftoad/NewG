package com.toadgallery.app

import com.toadgallery.app.data.media.VideoPlaybackPolicy
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.model.MediaCategory
import com.toadgallery.app.data.model.MediaType
import com.toadgallery.app.data.model.TonePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lightbox playback policy: only MediaStore-backed videos play directly. */
class VideoPlaybackPolicyTest {

  private fun makeItem(
    mediaType: MediaType,
    uriString: String? = "content://media/external/video/media/42",
    encryptedVaultFile: String? = null,
    editedUriString: String? = null,
  ): GalleryMediaItem =
    GalleryMediaItem(
      id = 42L,
      title = "Clip",
      uriString = uriString,
      drawableResName = null,
      category = MediaCategory.CAMERA,
      mediaType = mediaType,
      durationLabel = "0:31",
      dateLabel = "16 Nov 2026",
      weekGroup = "Nov 15 – 21, 2026",
      monthGroup = "November 2026",
      timestamp = 1763251200000L,
      locationName = "",
      hasGps = false,
      mapX = 0f,
      mapY = 0f,
      aspectRatio = 1.78f,
      isFavorite = false,
      isHidden = false,
      isTrashed = false,
      tags = emptyList(),
      ocrText = "",
      exifSummary = "",
      tonePreset = TonePreset.NATURAL,
      albumId = null,
      editedUriString = editedUriString,
      encryptedVaultFile = encryptedVaultFile,
    )

  @Test
  fun `media store video with uri is directly playable`() {
    assertTrue(VideoPlaybackPolicy.isDirectlyPlayable(makeItem(MediaType.VIDEO)))
  }

  @Test
  fun `vault video falls back to the static encrypted preview`() {
    val vaultVideo =
      makeItem(MediaType.VIDEO, encryptedVaultFile = "vault/clip.bin")
    assertFalse(VideoPlaybackPolicy.isDirectlyPlayable(vaultVideo))
  }

  @Test
  fun `video without uri is not playable`() {
    assertFalse(
      VideoPlaybackPolicy.isDirectlyPlayable(makeItem(MediaType.VIDEO, uriString = null))
    )
  }

  @Test
  fun `edited variant never replaces the video source`() {
    val item = makeItem(MediaType.VIDEO, editedUriString = "file://export/frame.jpg")
    assertEquals(
      "content://media/external/video/media/42",
      VideoPlaybackPolicy.playbackUri(item),
    )
  }

  @Test
  fun `photos gifs and screenshots stay on the image path`() {
    assertFalse(VideoPlaybackPolicy.isDirectlyPlayable(makeItem(MediaType.PHOTO)))
    assertFalse(VideoPlaybackPolicy.isDirectlyPlayable(makeItem(MediaType.GIF)))
    assertFalse(VideoPlaybackPolicy.isDirectlyPlayable(makeItem(MediaType.SCREENSHOT)))
  }

  @Test
  fun `blank uri returns null playback uri`() {
    assertNull(VideoPlaybackPolicy.playbackUri(makeItem(MediaType.VIDEO, uriString = "  ")))
  }
}
