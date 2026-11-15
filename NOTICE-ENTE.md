NewG includes and adapts face-management workflows from Ente Photos.

Upstream project: https://github.com/ente/ente
Reference revision inspected: `94a348f686e3ac98977cd7704d14bd5877832eee` (2026-11-14)
Upstream source paths used as design and implementation references:
- mobile/apps/photos/lib/services/machine_learning/face_ml/person/person_service.dart
- mobile/apps/photos/lib/services/machine_learning/face_ml/feedback/cluster_feedback.dart
- mobile/apps/photos/lib/services/machine_learning/face_ml/face_clustering/face_clustering_service.dart
- mobile/apps/photos/lib/services/machine_learning/face_ml/face_clustering/store_face_clustering.dart
- mobile/apps/photos/lib/services/machine_learning/face_thumbnail_generator.dart
- mobile/apps/photos/lib/utils/face_crop_util.dart
- mobile/apps/photos/lib/ui/viewer/people/cluster_page.dart
- mobile/apps/photos/lib/ui/viewer/people/person_clusters_page.dart
- mobile/apps/photos/lib/ui/viewer/people/add_files_to_person_page.dart
- mobile/apps/photos/lib/ui/viewer/people/merge_clusters_to_person_sheet.dart
- mobile/apps/photos/lib/ui/viewer/people/person_face_widget.dart
- mobile/apps/photos/lib/ui/viewer/people/file_face_widget.dart
- mobile/apps/photos/lib/ui/viewer/people/face_thumbnail_squircle.dart
- mobile/apps/photos/lib/ui/viewer/people/save_or_edit_person.dart

All source references above are pinned to the revision listed above; do not treat the moving `main` branch as the source version.

Ente is licensed under GNU Affero General Public License version 3.0 (AGPL-3.0).
The applicable license text is provided in LICENSE.

NewG is a Kotlin/Jetpack Compose application. The Ente Flutter/Dart workflows
are being ported and adapted to NewG's Android/Room architecture; Flutter UI
and server/account-dependent operations are not copied verbatim. Ente-derived
adaptations should retain this notice and AGPL-3.0 licensing.

Ente source copyright remains with its respective authors and contributors.
