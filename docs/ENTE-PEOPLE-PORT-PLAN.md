# Ente People / face-management source port plan

## Objective

Port the complete Ente Photos face-management workflows needed by NewG: person/cluster membership, per-face correction, ignore/unignore, custom cover selection, face-crop thumbnails, and the People management UX. Use the pinned Ente implementation as the behavioral source of truth rather than designing a new workflow.

Target repository: `dukeoftoad/NewG`  
Target PR: [#2](https://github.com/dukeoftoad/NewG/pull/2)  
Target branch: `ai/ente-face-model-downloader`  
Upstream repository: [ente/ente](https://github.com/ente/ente)  
Pinned upstream revision: `94a348f686e3ac98977cd7704d14bd5877832eee`

## Porting rule

NewG is Kotlin/Jetpack Compose, while Ente's mobile client is Flutter/Dart. Keep exact upstream snapshots of only the relevant source files under `third_party/ente/mobile/apps/photos/lib/` for auditability and behavioral reference. Port their feature logic into Kotlin files in NewG's existing package structure. Do not add Dart/Flutter runtime dependencies or copy unrelated Ente application infrastructure.

## Upstream files to vendor as source snapshots

Copy these files byte-for-byte from the pinned revision, preserving their original relative paths below `third_party/ente/`:

### Domain model and behavior
- `models/ml/face/person.dart` — person state, assigned clusters/faces, rejected face IDs, avatar face ID, ignored/hidden flags.
- `services/machine_learning/face_ml/person/person_service.dart` — create/update person, attach/detach clusters and faces, avatar update, and reconciliation semantics.
- `services/machine_learning/face_ml/feedback/cluster_feedback.dart` — add/remove face and cluster membership, rejection feedback, ignore workflow, cluster split and feedback semantics.
- `services/machine_learning/face_ml/feedback/dart_cluster_averages.dart` and `dart_face_distances.dart` — reference implementations for face-distance and cluster-average behavior used by feedback/suggestions.

### People and correction UI
- `ui/viewer/people/cluster_app_bar.dart` — actions for an unassigned cluster, including ignore.
- `ui/viewer/people/people_app_bar.dart` — person actions including custom cover, ignore and unignore.
- `ui/viewer/people/cluster_page.dart` — cluster gallery and correction entry points.
- `ui/viewer/people/people_page.dart` — person gallery and ignored-person state behavior.
- `ui/viewer/people/person_clusters_page.dart` — inspect and detach individual clusters from a person.
- `ui/viewer/people/person_face_widget.dart` and `file_face_widget.dart` — person/face thumbnail display and fallback behavior.
- `ui/viewer/people/face_thumbnail_squircle.dart` — face-thumbnail clipping/shape.
- `ui/viewer/people/person_gallery_suggestion.dart` — face thumbnail review and cluster suggestion UX.
- `ui/viewer/people/save_or_edit_person.dart` and `merge_clusters_to_person_sheet.dart` — naming, editing and merge-selection behavior.
- `ui/viewer/gallery/hooks/pick_person_avatar.dart` — select a photo containing a face as the person's cover.
- `utils/face/face_thumbnail_cache.dart` — cached face crop selection, generation and fallback.
- `services/machine_learning/face_thumbnail_generator.dart` — crop generation worker interface.

Also vendored for reference, but not required for the initial local-only port:
- `ui/viewer/people/add_files_to_person_page.dart`

Do not vendor unrelated memory-lane screens, contact-linking workflows, server/API clients, upload/sync code, or account-specific "this is me" flows.

## NewG destination map

| Ente source responsibility | NewG implementation location |
|---|---|
| `PersonData`, cluster membership, rejected faces, avatar and ignore flags | `app/src/main/java/com/example/data/local/FacePersonEntity.kt`, `FaceAssignmentEntity.kt`, `FaceExclusionEntity.kt` |
| Person/cluster/face persistence and reconciliation | `app/src/main/java/com/example/data/local/GalleryDao.kt`, `ToadGalleryDatabase.kt`, `app/src/main/java/com/example/data/repository/GalleryRepository.kt` |
| PersonService + relevant ClusterFeedbackService behavior | `GalleryRepository.kt` with atomic `@Transaction` operations in `GalleryDao.kt`; grouping in `FacePersonClusterer.kt` |
| Stable assignment/exclusion effects on automatic grouping | `app/src/main/java/com/example/data/ai/FacePersonClusterer.kt` |
| Cover face selection and cover-face resolution | `FacePersonEntity.coverPhotoId/coverFaceIndex`, `GalleryRepository.setFacePersonCover`, and the face picker in `SearchScreen.kt` |
| Face crop generation, EXIF orientation, and fallback | `app/src/main/java/com/example/ui/components/people/FaceThumbnail.kt` (shared local crop pipeline) |
| Person list, person details, merge/split and ignore/unignore actions | New Compose components under `app/src/main/java/com/example/ui/components/people/`; route/integration in `app/src/main/java/com/example/ui/screens/SearchScreen.kt` |
| Correction review and unassigned face assignment | `SearchScreen.kt` backed by `GalleryViewModel.kt`; reusable thumbnails in `ui/components/people/FaceThumbnail.kt` |
| App-level callback wiring | `app/src/main/java/com/example/ui/ToadGalleryApp.kt` |
| Behavior and persistence tests | `app/src/test/java/com/example/` plus Room instrumentation tests under `app/src/androidTest/java/com/example/` |

Existing `FaceAssignmentEntity`, `FaceExclusionEntity`, and `FaceCorrectionHistoryEntity` are retained. Correction writes and undo now run in DAO-level Room transactions. Room v8 adds nullable `coverFaceIndex` to preserve the exact selected avatar face; migration 7→8 is additive.

## Required behavioral parity

1. Assign an individual detected face to a selected person; assignment remains stable across automatic reclustering.
2. Remove a wrongly assigned face from a person without deleting the source photo; persist the rejection/exclusion so it is not immediately reintroduced.
3. Ignore a person or unassigned cluster, and provide a visible ignored-people area with an explicit unignore/restore action.
4. Select a custom cover from eligible photos containing a face belonging to that person; resolve and render the selected face crop, with safe fallback when the crop cannot be produced.
5. Show actual face crops in correction/review UI, never database row IDs as the primary user-facing identifier.
6. Support person merge and cluster split/detach using stable local IDs.
7. Record each local correction in Room and provide undo/history. **This is a NewG extension:** the inspected Ente files provide correction operations but not a durable undo/history ledger. Corrections and undo are now atomic Room transactions, with DAO persistence tests.
8. Keep all face metadata, assignments, exclusions, covers and correction history local to the device. Do not import Ente's remote entity sync or contact integrations.

## Implementation sequence

1. Vendor the selected upstream snapshots under `third_party/ente/`, add the pinned revision and upstream license attribution, and correct `NOTICE-ENTE.md`.
2. Compare each existing NewG implementation with its Ente counterpart; document parity gaps before changing behavior.
3. Keep person/cluster feedback operations in `GalleryRepository.kt` and atomic DAO transactions. Keep model download and ONNX inference isolated from person-management logic.
4. Port Ente's face crop selection/cache/fallback pipeline to Android bitmaps and local media URIs; shared EXIF-aware cropping is now in `FaceThumbnail.kt`.
5. Continue separating the People/review UX into dedicated Compose components; current SearchScreen now includes unassigned face review, assignment, face-based cover selection, and correction history.
6. Add Room migration/persistence tests, cluster behavior tests, and Compose UI tests for assignment, exclusion, ignore/unignore, cover changes, merge/split and undo.
7. Update PR description and provenance. Run the Android CI workflow on the exact final PR head and report its actual result.

## Explicit exclusions and compatibility decisions

- No Flutter/Dart runtime, Ente server, account, contact, cloud sync or memory-lane dependencies.
- Do not copy the entire Ente app or its unrelated people/memory infrastructure.
- Do not replace NewG's ONNX model inference pipeline with unrelated Ente infrastructure.
- Do not claim undo/history is copied from Ente; it is NewG-local functionality.
- Keep the independent FlutterFace model-weight licensing caveat separate from Ente source-code licensing.
- Do not merge PR #2 or modify `main` or PR #1.
