package com.toadgallery.app.data.media

import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.model.MediaType

/**
 * Decides which lightbox items get direct video playback.
 *
 * Only MediaStore-backed videos play directly. Vault items keep their
 * encrypted file — decrypting whole videos into memory is unsafe for large
 * files, so they stay on the static encrypted-preview path until a
 * decrypt-to-temp-file pipeline exists. Edited variants (editedUriString)
 * point at exported IMAGE frames, so they are deliberately ignored for
 * playback: the video file itself is always the source of truth.
 */
object VideoPlaybackPolicy {

  fun isDirectlyPlayable(item: GalleryMediaItem): Boolean =
    item.mediaType == MediaType.VIDEO && item.encryptedVaultFile.isNullOrBlank() &&
      !playbackUri(item).isNullOrBlank()

  fun playbackUri(item: GalleryMediaItem): String? =
    item.uriString?.takeIf { it.isNotBlank() }
}
