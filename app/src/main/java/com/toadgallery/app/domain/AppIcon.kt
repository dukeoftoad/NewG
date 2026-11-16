package com.toadgallery.app.domain

/**
 * User-selectable launcher icon. Ported from toadFILES' AppIcon system, collapsed to
 * a single dimension: each value names one of the `<activity-alias>` launcher entries
 * declared in AndroidManifest; [AppIconSwitcher] enables exactly that alias so the
 * home screen shows this icon.
 *
 * [CLASSIC_TOAD] is the icon shipped since v1.0 and stays the DEFAULT — an upgrade
 * must never change the icon a user already has pinned.
 *
 * Persisted by name in DataStore; the preferences loader falls back to
 * [CLASSIC_TOAD] for unknown values, so adding or removing designs later never
 * breaks a user's preferences.
 */
enum class AppIcon(val label: String, val aliasSuffix: String, val mipmapName: String) {
    CLASSIC_TOAD("Classic Toad", "Classic", "ic_launcher"),
    FROG_PHOTO("Frog & Photo", "FrogPhoto", "ic_launcher_frog_photo"),
    TOAD_FACE("Toad Face", "ToadFace", "ic_launcher_toad_face"),
    SILHOUETTE("Elegant Silhouette", "Silhouette", "ic_launcher_silhouette"),
    FRAME("Framed Photo", "Frame", "ic_launcher_frame"),
    PHOTO_STACK("Photo Stack", "PhotoStack", "ic_launcher_photo_stack"),
    ON_LEAF("Toad on Leaf", "OnLeaf", "ic_launcher_on_leaf"),
    CAMERA("Gallery Camera", "Camera", "ic_launcher_camera"),
}
