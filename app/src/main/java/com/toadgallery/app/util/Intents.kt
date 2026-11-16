package com.toadgallery.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Installer + URL helpers for the self-update flow. Ported from toadFILES'
 * Intents.kt (the exact subset toadGALLERY needs).
 */
object Intents {

    /** Outcome of handing an APK to the system installer. */
    enum class InstallResult {
        /** The package installer was launched. */
        STARTED,

        /** "Install unknown apps" was off; the settings toggle was opened — a retry is valid. */
        NEEDS_PERMISSION,

        /** The permission was missing AND the settings screen could not be opened — no retry path. */
        SETTINGS_FAILED,

        /** The installer could not be started even though permission was granted. */
        FAILED,
    }

    /**
     * Hands an APK already available as a content URI to the system installer, with
     * the "Install unknown apps" gate and outcome contract. Used by the self-update
     * flow, whose APK comes out of a completed DownloadManager job as
     * content://downloads/… — such a URI is served to the installer by the download
     * system itself, so no FileProvider round-trip is needed (an Android/data path
     * could never pass a FileProvider grant for another app anyway).
     */
    fun installApkFromUri(context: Context, uri: Uri): InstallResult {
        if (!canRequestPackageInstalls(context)) {
            // Distinct outcome: "go flip the toggle in Settings" is only honest if we
            // actually took the user there. If both settings intents fail, say so.
            return if (openInstallPermissionSettings(context)) {
                InstallResult.NEEDS_PERMISSION
            } else {
                InstallResult.SETTINGS_FAILED
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndTypeAndNormalize(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // The self-update handoff can run from the application context after the
            // download finishes; NEW_TASK keeps that legal, and is a no-op difference
            // when launched from an activity.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent) // no chooser: this must reach the installer
            InstallResult.STARTED
        } catch (_: Exception) {
            // Permission is already granted here; this is a real handoff failure.
            InstallResult.FAILED
        }
    }

    /**
     * Opens an https URL in the user's browser (used by About → "View on GitHub").
     * No Custom Tabs dependency — the plain ACTION_VIEW launch resolves whatever
     * browser the user has. User-initiated only; refuses everything that is not
     * https so this helper can never be talked into launching file/content intents
     * or shipping the user to a cleartext http endpoint. Returns an honest false
     * when the URL is not an https URL or nothing can handle it.
     */
    fun openUrl(context: Context, url: String): Boolean {
        val scheme = try {
            Uri.parse(url).scheme?.lowercase()
        } catch (_: Exception) {
            null
        }
        if (scheme != "https") return false
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun canRequestPackageInstalls(context: Context): Boolean = try {
        context.packageManager.canRequestPackageInstalls()
    } catch (_: Exception) {
        false
    }

    /** Opens the "Install unknown apps" screen for this app; false when both attempts fail. */
    private fun openInstallPermissionSettings(context: Context): Boolean = try {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /** True when the app currently holds the "Install unknown apps" grant. Exposed for the UI. */
    fun hasInstallPermission(context: Context): Boolean =
        try {
            context.packageManager.canRequestPackageInstalls()
        } catch (_: Exception) {
            false
        }
}
