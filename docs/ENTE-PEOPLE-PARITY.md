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
| `models/ml/face/person.dart` person model | `data/local/FacePersonEntity.kt`, `FaceAssignmentEntity.kt`, `FaceExclusionEntity.kt`; projected groups in `data/ai/FacePersonClusterer.kt` | Partial | Core local fields include names, ignored state, explicit face assignments/rejections and stable per-photo face IDs. Assignment, exclusion, correction-history and custom-cover records now persist faceId alongside the legacy index; ViewModel/undo resolve the current detector index by that ID after re-indexing. Room v9 adds nullable identity columns, preserving legacy rows. Remote-only pinned/memory/contact fields are intentionally omitted. Legacy v8 correction rows have null faceId and retain their historical index fallback; a safe backfill/recovery policy remains to be completed. |
| `services/.../person/person_service.dart` | `data/repository/GalleryRepository.kt`, `data/local/GalleryDao.kt`, `ui/viewmodel/GalleryViewModel.kt` | Partial | Compare create/update/rename, add/remove membership, merge/detach, cover updates, transactions, and reconciliation after new detections. |
| `services/.../feedback/cluster_feedback.dart` | `data/repository/GalleryRepository.kt` correction methods; `data/ai/FacePersonClusterer.kt`; `GalleryViewModel.kt` | Partial | Room writes are transactional. Rejected faces are emitted as standalone unassigned candidates instead of disappearing; assigning one clears rejection. New corrections carry stable face IDs and are remapped after detector ordering changes; legacy null-ID rows still need a migration/backfill strategy. Persistence and undo after re-index still need Room tests. |
| `dart_cluster_averages.dart`, `dart_face_distances.dart` | `data/ai/FacePersonClusterer.kt` | Partial | Compare normalization, cosine-distance metric, centroid calculation, thresholds, minimum cluster size, deterministic grouping and edge cases against Ente. Do not assume algorithm parity from similar names. |
| `cluster_app_bar.dart`, `people_app_bar.dart` | `ui/screens/SearchScreen.kt`, `GalleryViewModel.kt` | Partial | Audit all person/cluster actions, confirmation, ignored state, restore path and action availability. |
| `people_page.dart`, `cluster_page.dart`, `person_clusters_page.dart` | `ui/screens/SearchScreen.kt` | Partial | Compare screen states and navigation: named people, unnamed clusters, empty states, ignored people, cluster membership inspection, detach/split and merge flows. |
| `person_face_widget.dart`, `file_face_widget.dart`, `face_thumbnail_squircle.dart` | `ui/screens/SearchScreen.kt` (currently includes `FaceCropPreview`); planned `ui/components/people/FaceThumbnail.kt` | Partial | Extract a reusable crop component; verify crop coordinates, clipping, placeholder, decode failure, orientation, caching and lifecycle. |
| `face_thumbnail_cache.dart`, `face_thumbnail_generator.dart` | Planned `data/ai/FaceCropper.kt`, `ui/components/people/FaceThumbnail.kt`; existing crop preview in `SearchScreen.kt` | Not verified | Port crop bounds, source-image selection, cache key/invalidation, async generation and fallback. Verify crop is the detected face, not the full photo. |
| `pick_person_avatar.dart` | `GalleryRepository.setFacePersonCover()`, `GalleryViewModel.setFacePersonCover()`, `SearchScreen.kt` | Partial | The UI now selects from actual face crops, persists both photo ID and face index in Room v8, and falls back to the first available face when the selected one is unavailable. Dedicated picker UX and source-deletion fallback tests remain. |
| `save_or_edit_person.dart` | `GalleryViewModel.setFacePersonName()`, `SearchScreen.kt` | Partial | Verify rename/create UX, duplicate/blank names, validation, persistence and identity stability. |
| `merge_clusters_to_person_sheet.dart` | Same-name selection merges clusters; removing the name from one underlying cluster detaches it. This is functionally equivalent for basic merge/split but lacks Ente's dedicated cluster-selection sheet and its full membership history. | Partial | Merge projection extracted to `FacePersonClusterer.mergeSameNameGroups` and unit-tested (`FacePersonMergeParityTest`): membership migration, duplicate resolution (photo/face/bounds dedupe), trim/case-insensitive name matching, blank names never merge, detach-by-clearing-name, size-then-name ordering. DAO-level test proves merge/detach never mutates assignment rows and stays undo-safe. Dedicated cluster-selection sheet remains an intentional deviation. |
| `person_gallery_suggestion.dart` | Face grouping in `FacePersonClusterer.kt`; UI in `SearchScreen.kt` | Partial | Compare review suggestions, accept/reject flows, face thumbnails and prevention of rejected suggestions reappearing. |
| `add_files_to_person_page.dart` | No dedicated Kotlin equivalent established | Deferred (v2.1.0 founder decision) | Whole-photo assignment stays out of scope for v2.1.0 — per-face assignment is the shipped interaction and the two are not conflated. Revisit as a candidate for v2.2.0. |
| Ente correction feedback | `FaceCorrectionHistoryEntity.kt`, repository undo/history methods, `SearchScreen.kt` history UI | NewG extension | Test every correction type, prior assignment/exclusion restoration, multiple sequential undos, and history ordering. Ente source does not provide this durable undo ledger. |

## Current NewG entry points already present

- Persistence entities: `FacePersonEntity.kt`, `FaceAssignmentEntity.kt`, `FaceExclusionEntity.kt`, `FaceCorrectionHistoryEntity.kt`.
- Automatic grouping, rejected singleton suggestions and explicit assignment overlay: `FacePersonClusterer.kt`.
- EXIF-aware LRU/disk face crop cache: `FaceCropper.kt` + `FaceThumbnail.kt`.
- Storage operations: `GalleryRepository.kt`.
- State and action orchestration: `GalleryViewModel.kt`.
- People list and unassigned/rejected face review: `SearchScreen.kt`.
- Person editor, cover selection, correction and history: `ui/components/people/PersonManagementSheet.kt`.
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


## Detailed comparison findings — 2026-11-15

### 1. Person model (`person.dart`)

Ente models stable person identity, assigned clusters and face membership, rejected face IDs, avatar face identity, and ignore state (including legacy sentinel names). It also carries pinning, memory visibility, birth date and contact-link fields. NewG splits the local core across `FacePersonEntity`, `FaceAssignmentEntity` and `FaceExclusionEntity`; remote/contact/memory fields are intentionally excluded. **Finding: Partial parity.** NewG cover identity is `(photoId, faceIndex)` whereas Ente uses a face ID. Detector array ordering must be validated across re-indexing; assignments, exclusions and covers need an explicit invalidation/migration policy when detections change.

### 2. PersonService and ClusterFeedbackService

Ente distinguishes removing a face from a person, removing it from a cluster, adding it to a cluster, and removing selected files from a cluster. Removing a face from a person records rejection feedback so it is not suggested back. Membership changes invalidate cached cover-face selections and notify dependent screens. NewG has Room assignment/exclusion/history methods, rejected faces are surfaced as singleton unassigned candidates, and ViewModel flows connect these to the UI. DAO writes/undo are transactional. Merge/detach still uses name assignment rather than a separate person membership entity; repeated correction and re-index semantics need tests. **Finding: Partial parity.** Consolidate operations in `FacePersonFeedbackManager` and make membership/exclusion/history updates transactional.

### 3. Clustering and suggestions (`dart_cluster_averages.dart`, `dart_face_distances.dart`)

Ente maintains normalized cluster-centroid summaries and supports nearest-centroid search, mean-distance suggestions, and median pairwise face-distance suggestions using sampled embeddings. Suggestions exclude ignored and rejected clusters. NewG `FacePersonClusterer` still builds groups chronologically and enforces one face per photo. Automatic group matching now uses a weighted centroid/median-member score to reduce outlier sensitivity. Separately, `FacePersonSuggestionRanker` ports Ente's two-stage local suggestion behavior: filter candidate people by normalized mean-centroid distance, then rank/accept them using the upper median of sampled cross-face cosine distances. `GalleryViewModel` computes these suggestions only for unassigned review faces, and `SearchScreen` places suggested people first in the Assign menu. Sampling is deterministic/evenly spaced instead of Ente's random sampling, and NewG computes summaries from local Room embeddings rather than persisting centroid summaries or using Ente's vector database. Remote/account-dependent suggestion flows, nearest-centroid vector search and Ente's adaptive/batched suggestion orchestration are not ported. **Finding: Core mean-plus-median suggestion behavior is now present locally; infrastructure and orchestration parity remain partial.**

### 4. Face thumbnail pipeline

Ente `face_thumbnail_cache.dart` uses bounded in-memory LRU caches for full crops and thumbnails, a person/cluster-to-cover-face cache, optional disk-persisted crops, bounded generation queues, retry/backoff, and source-file fallback. `face_thumbnail_generator.dart` delegates crop generation to a worker isolate and distinguishes unsupported formats. NewG now has `data/ai/FaceCropper.kt`, called by `FaceThumbnail.kt`. It performs EXIF-aware background cropping, uses an 8 MiB memory LRU and app-private disk cache with a 32 MiB trim limit, and falls back to `GalleryMediaImage` on failure. The cache key includes source URI, crop bounds, cache version and the media row's faceIndexedAt revision, so forced re-indexing invalidates old crops. It still lacks Ente's separate thumbnail/full-crop caches, bounded per-photo generation queues and retry/backoff. **Finding: Improved partial parity.** Add queue/cancellation and detection-version invalidation; test cache hit, corrupt cache recovery, EXIF variants and source removal.

### 5. People screens and cover selection

Ente has separate People, cluster and person-cluster screens; its avatar picker presents eligible files containing the person's faces, limits selection to one, and saves the selected face identity. NewG `SearchScreen.kt` now exposes recurring People, ignored people, singleton unassigned/rejected faces, name/merge-by-name, face assignment/removal, face-based cover selection, split-by-clearing-label and undo/history. The person editor/correction/history surface has now been extracted to `ui/components/people/PersonManagementSheet.kt`; People list and unassigned-face review remain in `SearchScreen.kt`, unlike Ente's separate People, cluster and person-cluster routes. Cover selection shows up to 16 face candidates, not a dedicated eligible-photo picker. Ente's `people_page.dart` also contains memory-lane integration, which is outside this port. **Finding: Partial parity.** Extract dedicated Compose People, person detail, cover picker and correction surfaces; omit memory-lane dependencies.

### 6. Implementation order

1. Stabilize face identity across re-indexing and define assignment/exclusion/cover invalidation when detector results change.
2. Add `FacePersonFeedbackManager` and transactional operations for assign, reject, restore, merge, detach and cover updates.
3. Replace per-composition crop decoding with a bounded LRU + disk-backed `FaceCropper` modeled on Ente's cache/generator.
4. Build dedicated Compose People/detail/cover-picker/correction surfaces using vendored widgets as interaction references.
5. Decide whether candidate-cluster suggestions are in scope; if yes, port mean/median suggestions separately from initial clustering.
6. Add Room restart/migration and Compose interaction tests before marking any row Verified parity.

### Verification limits

This is a source/code inspection comparison only. It does not establish successful Android compilation, device behavior, persistence restart tests or visual parity. The latest observed CI run for the current head is in progress; cancelled runs are not passes.


### Follow-up implementation — local crop cache

`FaceCropper.kt` was added and `FaceThumbnail.kt` now delegates crop generation to it. This ports the essential local cache/generator separation from Ente into Kotlin while intentionally not bringing over Flutter isolates or Ente's download/server fallback. The implementation has not yet been compiled or device-tested.


### Follow-up implementation — stable face identity and crop invalidation

The face index JSON now writes a deterministic per-photo faceId derived from the pinned model ID, quantized normalized face bounds and quantized normalized embedding. Automatic cluster IDs use this identity rather than the detector array index, so detector output reordering alone does not rename a person cluster. A resolver maps persisted IDs to the current detector index.

Room v9 adds nullable faceId columns to face assignments, exclusions and correction history, plus coverFaceId to person records. New feedback operations capture the face ID at the time of the action; the ViewModel remaps assignments/exclusions before clustering, custom covers resolve their current face index, and undo locates the face by ID before restoring state. The crop cache is now keyed by the face-index timestamp, so re-indexing invalidates stale crops.

A Robolectric clusterer test covers identity resolution and stable group IDs after face-array reordering. Not yet verified: legacy v8 correction rows have null faceId and therefore still use their saved index; Room migration/restart tests and the current PR-head CI run are outstanding. This implementation is a compatibility layer, not yet proof of end-to-end stability on device.


### Feedback reconciliation hardening

Re-index writes now run through a Room transaction that remaps assignment and exclusion rows to the current detector indices using their stable face IDs. It also updates correction-history indices and custom-cover indices, while preserving stable IDs as the source of truth. Stable-ID records take precedence if a legacy index-only row collides. Empty or legacy face payloads deliberately skip reconciliation so a failed decode or older payload cannot erase feedback. Rejected-face review group IDs now include the stable face ID rather than only the detector index.

New tests cover both assigned faces swapping detector positions without overwriting one another, undo after re-index, stable rejected-candidate identity, and retaining assignments when re-index returns an empty face list. These are committed tests; they have not yet been executed in this environment.

### Follow-up implementation — identity reconciliation, ranking and regression coverage

- Re-indexing now backfills legacy index-only assignment, exclusion, correction-history and custom-cover records from the *previous* face payload when its stored box and 192-value embedding are available. The generated identity uses the same model ID, photo ID, quantized box and quantized embedding signature as the indexer.
- Legacy automatic person cluster IDs (`person-photoId-faceIndex`) are also migrated to `person-photoId-faceId`, preserving the latest name/cover/ignored metadata and rewriting assignment and correction-history references.
- The replacement index is then used to resolve those stable IDs to current detector indices. Assignment/exclusion rows, correction-history rows and custom-cover face indices are updated in the same Room transaction. Empty or incomplete face payloads deliberately skip reconciliation rather than guessing at identity.
- Automatic group matching uses a weighted centroid/median-member distance score. Separately, `FacePersonSuggestionRanker` implements Ente's mean-centroid filter followed by sampled cross-face median-distance ranking for unassigned review candidates; suggested people appear first in the Assign menu. Sampling is deterministic rather than random, and cluster summaries are computed from local embeddings rather than stored in a vector DB.
- Added Robolectric coverage for legacy feedback backfill across detector reordering, correction-history and cover remapping, ignore/unignore metadata retention, database close/reopen persistence, outlier-resistant automatic grouping, mean/median suggestion filtering, and the Room v8→v9 additive identity migration. A deterministic unit test verifies ranked people appear before fallback people in the face-assignment menu; the actual Android popup is not asserted under Robolectric because its platform popup window is not reliably surfaced by that test environment.
- These tests are committed but are not considered verified until CI completes successfully for the exact PR head. Device-level Compose interaction and model-inference checks remain outstanding.

