NewG includes and adapts face-management workflows from Ente Photos.

Upstream project: https://github.com/ente/ente
Reference revision inspected: `94a348f686e3ac98977cd7704d14bd5877832eee` (2026-11-14)

Feature-specific upstream source files (all pinned to the revision above):
- `mobile/apps/photos/lib/models/ml/face/person.dart`
- `mobile/apps/photos/lib/services/machine_learning/face_ml/person/person_service.dart`
- `mobile/apps/photos/lib/services/machine_learning/face_ml/feedback/cluster_feedback.dart`
- `mobile/apps/photos/lib/services/machine_learning/face_ml/feedback/dart_cluster_averages.dart`
- `mobile/apps/photos/lib/services/machine_learning/face_ml/feedback/dart_face_distances.dart`
- `mobile/apps/photos/lib/ui/viewer/people/cluster_app_bar.dart`
- `mobile/apps/photos/lib/ui/viewer/people/people_app_bar.dart`
- `mobile/apps/photos/lib/ui/viewer/people/cluster_page.dart`
- `mobile/apps/photos/lib/ui/viewer/people/people_page.dart`
- `mobile/apps/photos/lib/ui/viewer/people/person_clusters_page.dart`
- `mobile/apps/photos/lib/ui/viewer/people/person_face_widget.dart`
- `mobile/apps/photos/lib/ui/viewer/people/file_face_widget.dart`
- `mobile/apps/photos/lib/ui/viewer/people/face_thumbnail_squircle.dart`
- `mobile/apps/photos/lib/ui/viewer/people/person_gallery_suggestion.dart`
- `mobile/apps/photos/lib/ui/viewer/people/save_or_edit_person.dart`
- `mobile/apps/photos/lib/ui/viewer/people/merge_clusters_to_person_sheet.dart`
- `mobile/apps/photos/lib/ui/viewer/gallery/hooks/pick_person_avatar.dart`
- `mobile/apps/photos/lib/utils/face/face_thumbnail_cache.dart`
- `mobile/apps/photos/lib/services/machine_learning/face_thumbnail_generator.dart`

The upstream face-crop/cache implementation is `utils/face/face_thumbnail_cache.dart`; there is no `utils/face_crop_util.dart` at the pinned revision. The crop generator is `services/machine_learning/face_thumbnail_generator.dart`.

Ente is licensed under GNU Affero General Public License version 3.0 (AGPL-3.0). The applicable license text is provided in LICENSE. Ente source copyright remains with its respective authors and contributors.

NewG is a Kotlin/Jetpack Compose application. The planned port keeps the selected upstream source files as unmodified reference snapshots under `third_party/ente/` and ports their feature behavior into Kotlin/Room/Compose. Flutter widgets, server-backed entity synchronization, contact integrations, and account-specific features are not runtime dependencies of NewG.

Ente's referenced face-management code does not provide a persistent correction undo/history ledger. NewG's local Room correction-history and undo feature is an intentional local-only extension, not an upstream Ente copy.

The independently licensed FlutterFace model assets are documented separately in `app/src/main/assets/licenses/ENTE-FACE-MODELS-NOTICE.md`; their licensing must not be inferred from Ente's source-code license.
