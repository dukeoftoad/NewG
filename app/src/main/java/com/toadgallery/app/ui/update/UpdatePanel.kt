package com.toadgallery.app.ui.update

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.toadgallery.app.util.Intents
import java.util.Locale

private const val NOTES_PREVIEW_LINES = 6
private const val UPDATE_PROGRESS_BAR = "update_progress_bar"
private const val UPDATE_PROGRESS_TEXT = "update_progress_text"
private const val UPDATE_DOWNLOAD_ERROR = "update_download_error"
private const val UPDATE_RETRY_BUTTON = "update_retry_button"
private const val DOWNLOAD_PROGRESS_DESC = "Update download progress"

/** 7340032 → "7.0 MB" (the only Format helper this panel needs). */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

/** State-driven update panel for Settings → About. Ported from toadFILES' AboutScreen. */
@Composable
fun UpdatePanel(
    update: UpdateState,
    onCheck: () -> Unit,
    onDownloadInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (update) {
        is UpdateState.Idle -> {
            Text(
                "Contacts api.github.com once per app open (silently, in the background) and " +
                    "shows the result here. No account, no telemetry, no periodic polling.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onCheck) { Text("Check for updates") }
        }

        is UpdateState.Checking -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking GitHub Releases…", style = MaterialTheme.typography.labelMedium)
            }
        }

        is UpdateState.UpToDate -> {
            Text(
                "You're up to date (${update.currentVersion}).",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onCheck) { Text("Check again") }
        }

        is UpdateState.Available -> UpdateAvailable(
            available = update,
            onDownloadInstall = onDownloadInstall,
            onDismiss = onDismiss,
        )

        is UpdateState.Error -> {
            Text(
                update.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onCheck) { Text("Retry") }
        }
    }
}

@Composable
private fun UpdateAvailable(
    available: UpdateState.Available,
    onDownloadInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var notesExpanded by rememberSaveable { mutableStateOf(false) }
    Text(
        "toadGALLERY ${available.version} is available",
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(8.dp))
    VersionRow("New version", available.version)
    VersionRow("Size", formatSize(available.sizeBytes))
    available.notes?.let { notes ->
        Spacer(Modifier.height(8.dp))
        val lines = notes.split('\n')
        val truncated = !notesExpanded && lines.size > NOTES_PREVIEW_LINES
        Text(
            if (truncated) lines.take(NOTES_PREVIEW_LINES).joinToString("\n") + "\n…" else notes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (lines.size > NOTES_PREVIEW_LINES) {
            TextButton(onClick = { notesExpanded = !notesExpanded }) {
                Text(if (notesExpanded) "Show less" else "Show full notes")
            }
        }
    }
    if (available.needsInstallPermission) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Allow \"Install unknown apps\" for toadGALLERY in the Android screen that just " +
                "opened, then tap Install again — the APK is already downloaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (available.downloadInProgress) {
        Spacer(Modifier.height(10.dp))
        // Real in-app progress. Determinate when the release's total size is known
        // (GitHub always sends Content-Length), indeterminate otherwise — the bar
        // never lies. The byte counters come from the system DownloadManager's own
        // watch loop; the notification shade keeps mirroring the same job.
        val fraction = available.downloadProgress
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .testTag(UPDATE_PROGRESS_BAR)
                    .semantics { contentDescription = DOWNLOAD_PROGRESS_DESC },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Downloading… ${(fraction * 100).toInt()}%" +
                    " — also visible in the notification shade.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(UPDATE_PROGRESS_TEXT),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .testTag(UPDATE_PROGRESS_BAR)
                    .semantics { contentDescription = DOWNLOAD_PROGRESS_DESC },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Downloading… — also visible in the notification shade.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(UPDATE_PROGRESS_TEXT),
            )
        }
    }
    if (available.downloadError != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            available.downloadError,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(UPDATE_DOWNLOAD_ERROR),
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        val buttonLabel = when {
            available.downloadedUri != null -> "Install downloaded APK"
            available.downloadError != null -> "Retry download"
            else -> "Download & install"
        }
        Button(
            onClick = onDownloadInstall,
            enabled = !available.downloadInProgress,
            modifier = Modifier.testTag(UPDATE_RETRY_BUTTON),
        ) {
            Text(buttonLabel)
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { Intents.openUrl(context, available.htmlUrl) }) {
            Text("View on GitHub")
        }
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onDismiss) { Text("Dismiss") }
}

@Composable
internal fun VersionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
