# Vendored Ente Photos People feature sources

These are unmodified upstream source snapshots retained as the behavioral/source reference for NewG's Ente People feature port.

- Upstream: https://github.com/ente/ente
- Revision: 94a348f686e3ac98977cd7704d14bd5877832eee
- Original relative paths are preserved below this directory.
- Upstream license: GNU AGPL-3.0; see the repository-root LICENSE and NOTICE-ENTE.md.

These Dart files are reference snapshots only. NewG is a Kotlin/Jetpack Compose application and does not compile or execute Flutter code. Runtime behavior is ported into the Kotlin/Room files mapped in docs/ENTE-PEOPLE-PORT-PLAN.md.

The selected files cover person state and operations, face/cluster assignment and exclusion, ignore/unignore, cover selection, person/cluster screens, face crop caching and thumbnails, and review/suggestion UI. Unrelated Ente services, account/server synchronization, contact linking, and memory-lane features are intentionally not vendored.

The upstream feature does not include durable correction undo/history. NewG implements that separately as a local Room-backed extension.
