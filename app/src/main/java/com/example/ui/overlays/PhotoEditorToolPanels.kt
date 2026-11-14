package com.example.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.DepthModelPackState
import com.example.data.model.AdjustParam
import com.example.data.model.AspectRatioPreset
import com.example.data.model.BokehParams
import com.example.data.model.BokehShape
import com.example.data.model.CropParams
import com.example.data.model.DepthPreviewMode
import com.example.data.model.FilterLook
import com.example.data.model.MarkupToolType
import com.example.data.model.PhotoEditRecipe
import com.example.ui.theme.LocalToadColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AdjustToolPanel(
  recipe: PhotoEditRecipe,
  selectedParam: AdjustParam,
  onSelectParam: (AdjustParam) -> Unit,
  onValueChange: (AdjustParam, Float) -> Unit,
  onValueCommit: () -> Unit,
) {
  val extended = LocalToadColors.current
  val currentValue = recipe.adjust.valueOf(selectedParam)

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    LazyRow(
      modifier = Modifier.testTag("adjust_param_row"),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
      items(AdjustParam.entries) { param ->
        val selected = param == selectedParam
        val valNow = recipe.adjust.valueOf(param)
        val edited = abs(valNow - param.defaultValue) > 0.01f
        Surface(
          onClick = { onSelectParam(param) },
          modifier = Modifier.testTag("adjust_param_${param.name.lowercase()}"),
          shape = CircleShape,
          color =
            if (selected) MaterialTheme.colorScheme.primary
            else if (edited) MaterialTheme.colorScheme.primaryContainer
            else extended.surfaceHigh,
          border =
            BorderStroke(
              1.dp,
              if (selected) MaterialTheme.colorScheme.primary else extended.borderSubtle,
            ),
        ) {
          Text(
            text = param.label,
            style = MaterialTheme.typography.labelSmall,
            color =
              if (selected) MaterialTheme.colorScheme.onPrimary
              else if (edited) MaterialTheme.colorScheme.onPrimaryContainer
              else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = selectedParam.label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
          Text(
            text = String.format(Locale.US, "%+.0f", currentValue * 100f),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
        if (abs(currentValue - selectedParam.defaultValue) > 0.01f) {
          TextButton(
            onClick = {
              onValueChange(selectedParam, selectedParam.defaultValue)
              onValueCommit()
            },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
          ) {
            Text("Reset", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }

    Slider(
      value = currentValue,
      onValueChange = { onValueChange(selectedParam, it) },
      onValueChangeFinished = onValueCommit,
      valueRange = selectedParam.min..selectedParam.max,
      colors =
        SliderDefaults.colors(
          thumbColor = MaterialTheme.colorScheme.primary,
          activeTrackColor = MaterialTheme.colorScheme.primary,
          inactiveTrackColor = extended.surfaceHighest,
        ),
      modifier = Modifier.fillMaxWidth().testTag("editor_adjust_slider"),
    )
  }
}

@Composable
fun FiltersToolPanel(
  recipe: PhotoEditRecipe,
  onSelectFilter: (FilterLook) -> Unit,
  onIntensityChange: (Float) -> Unit,
  onGrainChange: (Float) -> Unit,
  onCommit: () -> Unit,
) {
  val extended = LocalToadColors.current

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    LazyRow(
      modifier = Modifier.testTag("filter_look_row"),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(FilterLook.entries) { look ->
        val selected = recipe.filterLook == look
        Surface(
          onClick = { onSelectFilter(look) },
          modifier = Modifier.testTag("filter_look_${look.name.lowercase()}"),
          shape = RoundedCornerShape(14.dp),
          color = if (selected) MaterialTheme.colorScheme.primary else extended.surfaceHigh,
          border =
            BorderStroke(
              1.dp,
              if (selected) MaterialTheme.colorScheme.primary else extended.borderSubtle,
            ),
        ) {
          Text(
            text = look.label,
            style = MaterialTheme.typography.labelSmall,
            color =
              if (selected) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
          )
        }
      }
    }

    if (recipe.filterLook != FilterLook.NATURAL) {
      LabeledEditorSlider(
        label = "Filter Strength",
        valueText = "${(recipe.filterIntensity * 100f).roundToInt()}%",
        value = recipe.filterIntensity,
        onValueChange = onIntensityChange,
        onFinish = onCommit,
        testTag = "editor_filter_intensity_slider",
      )
    }

    LabeledEditorSlider(
      label = "Film Grain",
      valueText = "${(recipe.filmGrain * 100f).roundToInt()}%",
      value = recipe.filmGrain,
      onValueChange = onGrainChange,
      onFinish = onCommit,
      testTag = "editor_film_grain_slider",
    )
  }
}

@Composable
fun CropToolPanel(
  crop: CropParams,
  sourceAspect: Float,
  onSelectAspect: (AspectRatioPreset, Float) -> Unit,
  onStraightenChange: (Float) -> Unit,
  onStraightenCommit: () -> Unit,
  onRotateLeft: () -> Unit,
  onRotateRight: () -> Unit,
  onFlipH: () -> Unit,
  onFlipV: () -> Unit,
  onResetCrop: () -> Unit,
) {
  val extended = LocalToadColors.current

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    LazyRow(
      modifier = Modifier.testTag("crop_aspect_row"),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(AspectRatioPreset.entries) { preset ->
        val selected = crop.aspectPreset == preset
        Surface(
          onClick = { onSelectAspect(preset, sourceAspect) },
          modifier = Modifier.testTag("crop_aspect_${preset.name.lowercase()}"),
          shape = CircleShape,
          color = if (selected) MaterialTheme.colorScheme.primary else extended.surfaceHigh,
          border =
            BorderStroke(
              1.dp,
              if (selected) MaterialTheme.colorScheme.primary else extended.borderSubtle,
            ),
        ) {
          Text(
            text = preset.label,
            style = MaterialTheme.typography.labelSmall,
            color =
              if (selected) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(
        onClick = onRotateLeft,
        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("crop_rotate_left"),
        shape = RoundedCornerShape(12.dp),
        color = extended.surfaceHigh,
      ) {
        Row(
          modifier = Modifier.padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Rotate 90 left", modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("-90°", style = MaterialTheme.typography.labelSmall)
        }
      }
      Surface(
        onClick = onRotateRight,
        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("crop_rotate_right"),
        shape = RoundedCornerShape(12.dp),
        color = extended.surfaceHigh,
      ) {
        Row(
          modifier = Modifier.padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90 right", modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("+90°", style = MaterialTheme.typography.labelSmall)
        }
      }
      Surface(
        onClick = onFlipH,
        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("crop_flip_h"),
        shape = RoundedCornerShape(12.dp),
        color = if (crop.flipHorizontal) MaterialTheme.colorScheme.primaryContainer else extended.surfaceHigh,
      ) {
        Row(
          modifier = Modifier.padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.Flip, contentDescription = "Flip Horizontal", modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Flip H", style = MaterialTheme.typography.labelSmall)
        }
      }
      Surface(
        onClick = onFlipV,
        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("crop_flip_v"),
        shape = RoundedCornerShape(12.dp),
        color = if (crop.flipVertical) MaterialTheme.colorScheme.primaryContainer else extended.surfaceHigh,
      ) {
        Row(
          modifier = Modifier.padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.Refresh, contentDescription = "Flip Vertical", modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Flip V", style = MaterialTheme.typography.labelSmall)
        }
      }
      if (!crop.isDefault) {
        TextButton(onClick = onResetCrop, modifier = Modifier.testTag("crop_reset_btn")) {
          Text("Reset", style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    LabeledEditorSlider(
      label = "Straighten",
      valueText = String.format(Locale.US, "%+.1f°", crop.straightenDegrees),
      value = crop.straightenDegrees,
      valueRange = -45f..45f,
      onValueChange = onStraightenChange,
      onFinish = onStraightenCommit,
      testTag = "editor_straighten_slider",
    )
  }
}

@Composable
fun BokehToolPanel(
  bokeh: BokehParams,
  depthState: DepthModelPackState,
  depthInferenceMs: Long?,
  onStartDownload: () -> Unit,
  onCancelDownload: () -> Unit,
  onDeleteModel: () -> Unit,
  onBokehChange: (BokehParams) -> Unit,
  onBokehCommit: () -> Unit,
) {
  val extended = LocalToadColors.current

  if (!depthState.isInstalled) {
    // On-demand Download Card directly inside Bokeh Tool
    Surface(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).testTag("editor_bokeh_download_card"),
      shape = RoundedCornerShape(20.dp),
      color = extended.surfaceHigh,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              modifier =
                Modifier.size(38.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.BlurOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "bokeh-depth-dav2",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "Depth Anything V2 Small (int8 ONNX) • ~27.3 MB",
                style = MaterialTheme.typography.bodySmall,
                color = extended.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            Text(
              text = if (depthState.isDownloading) "${depthState.progressPercent}%" else "On-Demand",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }

        Text(
          text =
            "Unlocks tap-to-focus aperture bokeh (f/1.4–f/16), 3D depth map inspection, foreground color pop, atmospheric fog, and subject relighting offline.",
          style = MaterialTheme.typography.bodySmall,
          color = extended.textMuted,
        )

        if (depthState.isDownloading) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(
              progress = { depthState.progressPercent / 100f },
              modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
              color = MaterialTheme.colorScheme.primary,
              trackColor = extended.surfaceHighest,
            )
            Text(
              text = depthState.progressLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        } else if (!depthState.errorMessage.isNullOrBlank()) {
          Text(
            text = depthState.errorMessage,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          if (depthState.isDownloading) {
            OutlinedButton(
              onClick = onCancelDownload,
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("editor_bokeh_cancel_btn"),
              border = BorderStroke(1.dp, extended.borderStrong),
            ) {
              Text("Cancel Download")
            }
          } else {
            Button(
              onClick = onStartDownload,
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("editor_bokeh_download_btn"),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
              Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Download bokeh-depth-dav2 (~27.3 MB)")
            }
          }
        }
      }
    }
    return
  }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .height(176.dp)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 14.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text("Depth Anything V2 Studio", style = MaterialTheme.typography.labelLarge)
        Text(
          text =
            if (depthInferenceMs != null) "ONNX Depth Ready • ${depthInferenceMs}ms"
            else "bokeh-depth-dav2 INT8 Active",
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = onDeleteModel, contentPadding = PaddingValues(horizontal = 6.dp)) {
          Text("Remove Pack", style = MaterialTheme.typography.labelSmall, color = extended.textMuted)
        }
        Switch(
          checked = bokeh.enabled,
          onCheckedChange = {
            onBokehChange(bokeh.copy(enabled = it))
            onBokehCommit()
          },
          modifier = Modifier.testTag("editor_bokeh_switch"),
        )
      }
    }

    // View Modes (Photo vs Depth Heatmap vs Depth B&W) + Bokeh Shapes
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(DepthPreviewMode.entries) { mode ->
        val active = bokeh.depthPreviewMode == mode
        Surface(
          onClick = {
            onBokehChange(bokeh.copy(enabled = true, depthPreviewMode = mode))
            onBokehCommit()
          },
          modifier = Modifier.testTag("depth_mode_${mode.name.lowercase()}"),
          shape = CircleShape,
          color = if (active) MaterialTheme.colorScheme.primary else extended.surfaceHigh,
        ) {
          Text(
            text = mode.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
      items(BokehShape.entries) { shape ->
        val active = bokeh.bokehShape == shape
        Surface(
          onClick = {
            onBokehChange(bokeh.copy(enabled = true, bokehShape = shape))
            onBokehCommit()
          },
          shape = CircleShape,
          color = if (active) MaterialTheme.colorScheme.primaryContainer else extended.surfaceHigh,
        ) {
          Text(
            text = shape.label,
            style = MaterialTheme.typography.labelSmall,
            color =
              if (active) MaterialTheme.colorScheme.onPrimaryContainer
              else extended.textMuted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
    }

    LabeledEditorSlider(
      label = "Aperture Blur",
      valueText = bokeh.fStopLabel,
      value = bokeh.aperture,
      onValueChange = { onBokehChange(bokeh.copy(enabled = true, aperture = it)) },
      onFinish = onBokehCommit,
      testTag = "editor_bokeh_aperture_slider",
    )
    LabeledEditorSlider(
      label = "Depth-of-Field Width",
      valueText = "${(bokeh.focusRange * 100f).roundToInt()}%",
      value = bokeh.focusRange,
      valueRange = 0.06f..0.50f,
      onValueChange = { onBokehChange(bokeh.copy(enabled = true, focusRange = it)) },
      onFinish = onBokehCommit,
      testTag = "editor_bokeh_range_slider",
    )
    LabeledEditorSlider(
      label = "Foreground Color Pop",
      valueText = "${(bokeh.foregroundColorPop * 100f).roundToInt()}%",
      value = bokeh.foregroundColorPop,
      onValueChange = { onBokehChange(bokeh.copy(enabled = true, foregroundColorPop = it)) },
      onFinish = onBokehCommit,
      testTag = "editor_bokeh_colorpop_slider",
    )
    LabeledEditorSlider(
      label = "Atmospheric Depth Fog",
      valueText = "${(bokeh.depthFog * 100f).roundToInt()}%",
      value = bokeh.depthFog,
      onValueChange = { onBokehChange(bokeh.copy(enabled = true, depthFog = it)) },
      onFinish = onBokehCommit,
      testTag = "editor_bokeh_fog_slider",
    )
    LabeledEditorSlider(
      label = "Subject Relight",
      valueText = String.format(Locale.US, "%+.0f", bokeh.subjectRelight * 100f),
      value = bokeh.subjectRelight,
      valueRange = -1f..1f,
      onValueChange = { onBokehChange(bokeh.copy(enabled = true, subjectRelight = it)) },
      onFinish = onBokehCommit,
      testTag = "editor_bokeh_relight_slider",
    )
    LabeledEditorSlider(
      label = "Background Dim",
      valueText = "${(bokeh.backgroundDim * 100f).roundToInt()}%",
      value = bokeh.backgroundDim,
      onValueChange = { onBokehChange(bokeh.copy(enabled = true, backgroundDim = it)) },
      onFinish = onBokehCommit,
      testTag = "editor_bokeh_bgdim_slider",
    )
  }
}

@Composable
fun MarkupToolPanel(
  selectedTool: MarkupToolType,
  selectedColorHex: Long,
  strokeWidthNorm: Float,
  hasMarkup: Boolean,
  onSelectTool: (MarkupToolType) -> Unit,
  onSelectColor: (Long) -> Unit,
  onStrokeWidthChange: (Float) -> Unit,
  onOpenAddTextDialog: () -> Unit,
  onClearMarkup: () -> Unit,
) {
  val extended = LocalToadColors.current
  val paletteColors =
    listOf(
      0xFF6EE7B7L,
      0xFFFFFFFFL,
      0xFF111827L,
      0xFFEF4444L,
      0xFFF59E0BL,
      0xFF38BDF8L,
      0xFFA78BFAL,
    )

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    LazyRow(
      modifier = Modifier.testTag("markup_tool_row"),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(MarkupToolType.entries) { tool ->
        val selected = tool == selectedTool
        Surface(
          onClick = {
            onSelectTool(tool)
            if (tool == MarkupToolType.TEXT) onOpenAddTextDialog()
          },
          modifier = Modifier.testTag("markup_tool_${tool.name.lowercase()}"),
          shape = CircleShape,
          color = if (selected) MaterialTheme.colorScheme.primary else extended.surfaceHigh,
          border =
            BorderStroke(
              1.dp,
              if (selected) MaterialTheme.colorScheme.primary else extended.borderSubtle,
            ),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (tool == MarkupToolType.TEXT) {
              Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
              text = tool.label,
              style = MaterialTheme.typography.labelSmall,
              color =
                if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        paletteColors.forEach { hex ->
          val active = selectedColorHex == hex
          Box(
            modifier =
              Modifier.size(if (active) 28.dp else 22.dp)
                .clip(CircleShape)
                .background(Color(hex))
                .border(
                  width = if (active) 2.5.dp else 1.dp,
                  color = if (active) Color.White else extended.borderStrong,
                  shape = CircleShape,
                )
                .clickable { onSelectColor(hex) }
          )
        }
      }

      if (hasMarkup) {
        TextButton(onClick = onClearMarkup, modifier = Modifier.testTag("markup_clear_btn")) {
          Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text("Clear", style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    LabeledEditorSlider(
      label = if (selectedTool == MarkupToolType.PRIVACY_BLUR) "Mosaic Brush Size" else "Stroke Width",
      valueText = "${(strokeWidthNorm * 1000f).roundToInt()}pt",
      value = strokeWidthNorm,
      valueRange = 0.005f..0.045f,
      onValueChange = onStrokeWidthChange,
      onFinish = {},
      testTag = "editor_markup_size_slider",
    )
  }
}

@Composable
private fun LabeledEditorSlider(
  label: String,
  valueText: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  onValueChange: (Float) -> Unit,
  onFinish: () -> Unit,
  testTag: String,
) {
  val extended = LocalToadColors.current
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label, style = MaterialTheme.typography.labelSmall, color = extended.textMuted)
      Text(
        text = valueText,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
      )
    }
    Slider(
      value = value.coerceIn(valueRange.start, valueRange.endInclusive),
      onValueChange = onValueChange,
      onValueChangeFinished = onFinish,
      valueRange = valueRange,
      colors =
        SliderDefaults.colors(
          thumbColor = MaterialTheme.colorScheme.primary,
          activeTrackColor = MaterialTheme.colorScheme.primary,
          inactiveTrackColor = extended.surfaceHighest,
        ),
      modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
  }
}
