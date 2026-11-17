package com.toadgallery.app.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.video.VideoEditPlan
import com.toadgallery.app.ui.theme.LocalToadColors

/**
 * v2.2.0 E1 video edit surface: seconds-based trim, mute, speed, and frame
 * extraction. Deliberately minimal — no timeline scrubbing in this release;
 * the numeric fields talk in whole seconds and validate against
 * [VideoEditPlan]'s rules, so an invalid plan is refused before it reaches
 * the engine.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoEditSheet(
  item: GalleryMediaItem,
  onDismiss: () -> Unit,
  onApply: (VideoEditPlan) -> Unit,
  onExtractFrame: (positionMs: Long) -> Unit,
) {
  BackHandlerCompat(onBack = onDismiss)
  val extended = LocalToadColors.current
  val sourceDurationMs = VideoEditPlan.parseDurationLabel(item.durationLabel)

  var startSec by remember { mutableStateOf("0") }
  var endSec by remember { mutableStateOf(if (sourceDurationMs > 0) (sourceDurationMs / 1000L).toString() else "") }
  var muted by remember { mutableStateOf(false) }
  var speed by remember { mutableStateOf(1.0f) }
  var frameSec by remember { mutableStateOf("0") }
  var errorText by remember { mutableStateOf<String?>(null) }

  fun buildPlan(): VideoEditPlan? =
    try {
      val start = startSec.toLongOrNull() ?: 0L
      val end = endSec.toLongOrNull()?.takeIf { it > 0 }
      VideoEditPlan(
        trimStartMs = start * 1000L,
        trimEndMs = end?.times(1000L),
        removeAudio = muted,
        speed = speed,
      )
    } catch (t: IllegalArgumentException) {
      errorText = t.message
      null
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth(0.92f).widthIn(max = 500.dp).padding(vertical = 24.dp),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, extended.borderStrong),
    ) {
      Column(
        modifier =
          Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Edit Video", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Saved as a new copy — original untouched",
              style = MaterialTheme.typography.bodySmall,
              color = extended.textMuted,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close video editor")
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = startSec,
            onValueChange = { startSec = it.filter(Char::isDigit).take(6) },
            label = { Text("Start (s)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = endSec,
            onValueChange = { endSec = it.filter(Char::isDigit).take(6) },
            label = { Text("End (s)") },
            supportingText = {
              if (sourceDurationMs > 0) Text("source: ${item.durationLabel}")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Mute audio", style = MaterialTheme.typography.bodyMedium)
          Switch(checked = muted, onCheckedChange = { muted = it })
        }

        Text("Speed", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(0.25f, 0.5f, 1.0f, 2.0f, 4.0f).forEach { s ->
            FilterChip(
              selected = speed == s,
              onClick = {
                speed = s
                errorText = null
              },
              label = { Text(if (s == 1.0f) "1x" else "${s}x") },
            )
          }
        }

        errorText?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = extended.dangerContainer)
        }

        Button(
          onClick = {
            errorText = null
            val plan = buildPlan()
            if (plan != null) {
              if (plan.isNoOp) {
                errorText = "Nothing would change — adjust trim, mute, or speed"
              } else {
                onApply(plan)
              }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Save edited copy")
        }

        Spacer(modifier = Modifier.size(4.dp))

        Text("Extract frame", style = MaterialTheme.typography.bodyMedium)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = frameSec,
            onValueChange = { frameSec = it.filter(Char::isDigit).take(6) },
            label = { Text("At (s)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          OutlinedButton(
            onClick = {
              val pos = (frameSec.toLongOrNull() ?: 0L).coerceAtLeast(0L)
              onExtractFrame(pos * 1000L)
            },
          ) {
            Text("Save as photo")
          }
        }
      }
    }
  }
}

/** Small indirection so the sheet does not import androidx.activity directly. */
@Composable
private fun BackHandlerCompat(onBack: () -> Unit) {
  androidx.activity.compose.BackHandler(onBack = onBack)
}
