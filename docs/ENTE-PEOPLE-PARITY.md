# Ente → NewG People feature parity matrix

Purpose: compare the pinned, vendored Ente implementation with the Kotlin production implementation by **behavior and data flow**, not by line numbers. Ente is Dart/Flutter; NewG is Kotlin/Compose/Room, so equivalent behavior may live in several Kotlin files.

Reference: Ente `ente/ente` revision `94a348f686e3ac98977cd7704d14bd5877832eee`.  
Vendored snapshots: `third_party/ente/mobile/apps/photos/lib/`.  
Port plan: `docs/ENTE-PEOPLE-PORT-PLAN.md`.

## How to perform each comparison

For each row:
1. Read the upstream file(s) and record the observable behavior, invariants, edge cases, and state transitions.
2. Trace the Kotlin implementation across UI → ViewModel → Repository → DAO/Room → clusterer / media cropper as appropriate.
3. Compare behavior, not syntax. Verify stable IDs, persistence, error/fallback behavior, and whether the operation survives app restart and reclustering.
4. Add or update a unit, Room, or Compose test that demonstrates parity.
5. Change status only after code inspection and tests. A method existing is not proof of full parity.

Statuses: **Partial** = relevant foundations exist but complete workflow or tests are not verified; **Not implemented / not verified** = no equivalent has been established yet; **NewG extension** = deliberately beyond Ente.

## Initial codebase comparison (based on current PR head inspection)

| Ente source / feature | NewG Kotlin location to compare | Initial status | What must be verified or completed |
|---|---|---|---|
| `models/ml/face/person.dart` person model | `data/local/FacePersonEntity.kt`, `FaceAssignmentEntity.kt`, `FaceExclusionEntity.kt`; projected groups in `data/ai/FacePersonClusterer.kt` | Partial | Map every upstream person field and invariant: stable person identity, assigned clusters/faces, rejected faces, avatar/cover identity, ignored state, rename and deletion semantics. |
| `services/.../person/person_service.dart` | `data/repository/GalleryRepository.kt`, `data/local/GalleryDao.kt`, `ui/viewmodel/GalleryViewModel.kt` | Partial | Compare create/update/rename, add/remove membership, merge/detach, cover updates, transactions, and reconciliation after new detections. |
| `services/.../feedback/cluster_feedback.dart` | `data/repository/GalleryRepository.kt` correction methods; `data/ai/FacePersonClusterer.kt`; `GalleryViewModel.kt` | Partial | Verify reject/remove feedback is durable, prevents reappearance, supports explicit reassignment, and handles conflicting membership/cluster changes exactly once. |
| `dart_cluster_averages.dart`, `dart_face_distances.dart` | `data/ai/FacePersonClusterer.kt` | Partial | Compare normalization, cosine-distance metric, centroid calculation, thresholds, minimum cluster size, deterministic grouping and edge cases against Ente. Do not assume algorithm parity from similar names. |
| `cluster_app_bar.dart`, `people_app_bar.dart` | `ui/screens/SearchScreen.kt`, `GalleryViewModel.kt` | Partial | Audit all person/cluster actions, confirmation, ignored state, restore path and action availability. |
| `people_page.dart`, `cluster_page.dart`, `person_clusters_page.dart` | `ui/screens/SearchScreen.kt` | Partial | Compare screen states and navigation: named people, unnamed clusters, empty states, ignored people, cluster membership inspection, detach/split and merge flows. |
| `person_face_widget.dart`, `file_face_widget.dart`, `face_thumbnail_squircle.dart` | `ui/screens/SearchScreen.kt` (currently includes `FaceCropPreview`); planned `ui/components/people/FaceThumbnail.kt` | Partial | Extract a reusable crop component; verify crop coordinates, clipping, placeholder, decode failure, orientation, caching and lifecycle. |
| `face_thumbnail_cache.dart`, `face_thumbnail_generator.dart` | Planned `data/ai/FaceCropper.kt`, `ui/components/people/FaceThumbnail.kt`; existing crop preview in `SearchScreen.kt` | Not verified | Port crop bounds, source-image selection, cache key/invalidation, async generation and fallback. Verify crop is the detected face, not the full photo. |
| `pick_person_avatar.dart` | `GalleryRepository.setFacePersonCover()`, `GalleryViewModel.setFacePersonCover()`, `SearchScreen.kt` | Partial | Repository and ViewModel setter exist; verify a complete user-facing picker, eligible-photo filtering, selected face crop rendering, persistence and fallback if source media disappears. |
| `save_or_edit_person.dart` | `GalleryViewModel.setFacePersonName()`, `SearchScreen.kt` | Partial | Verify rename/create UX, duplicate/blank names, validation, persistence and identity stability. |
| `merge_clusters_to_person_sheet.dart` | No dedicated Kotlin equivalent established; likely integration in `SearchScreen.kt` / `GalleryViewModel.kt` | Not verified | Implement and test merge selection, confirmation, membership migration, duplicate resolution and undo-safe transaction. |
| `person_gallery_suggestion.dart` | Face grouping in `FacePersonClusterer.kt`; UI in `SearchScreen.kt` | Partial | Compare review suggestions, accept/reject flows, face thumbnails and prevention of rejected suggestions reappearing. |
| `add_files_to_person_page.dart` | No dedicated Kotlin equivalent established | Not verified / optional | Decide whether NewG needs whole-photo assignment in addition to the already exposed per-face assignment. Do not conflate the two. |
| Ente correction feedback | `FaceCorrectionHistoryEntity.kt`, repository undo/history methods, `SearchScreen.kt` history UI | NewG extension | Test every correction type, prior assignment/exclusion restoration, multiple sequential undos, and history ordering. Ente source does not provide this durable undo ledger. |

## Current NewG entry points already present

- Persistence entities: `FacePersonEntity.kt`, `FaceAssignmentEntity.kt`, `FaceExclusionEntity.kt`, `FaceCorrectionHistoryEntity.kt`.
- Automatic grouping and explicit assignment overlay: `FacePersonClusterer.kt`.
- Storage operations: `GalleryRepository.kt`.
- State and action orchestration: `GalleryViewModel.kt`.
- Current People/correction UI: `SearchScreen.kt`.
- Existing test: `app/src/test/java/com/example/FacePersonClustererTest.kt`.

These are the files to compare first. The destination files listed as “planned” are intended refactoring targets, not files confirmed to exist on this branch.

## Completion criteria

The feature is not considered ported until:
- every applicable row is marked **Verified parity**, **Intentional deviation**, or **Not applicable** with a reason;
- every user operation has a persistence/restart test;
- manual assignment and rejection remain stable after automatic reclustering;
- ignore/unignore, custom cover selection, merge/detach and correction history work end-to-end in Compose;
- thumbnails use correct face bounds with caching and graceful fallback;
- Android CI passes on the final PR head.

## Status vocabulary

- **Not started** — no implementation located.
- **Partial** — some code exists; behavior or edge cases remain.
- **In review** — implementation exists and is being compared/tested.
- **Verified parity** — behavior is covered by tests and matches the selected Ente behavior.
- **Intentional deviation** — documented local design difference.
- **NewG extension** — feature not supplied by Ente, e.g. durable local undo/history.
