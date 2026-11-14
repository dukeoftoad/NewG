package com.example.ui.overlays

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.DepthMapResult
import com.example.data.ai.DepthModelPackState
import com.example.data.editor.PhotoEditorRenderer
import com.example.data.model.AdjustParam
import com.example.data.model.AspectRatioPreset
import com.example.data.model.CropParams
import com.example.data.model.EditorToolTab
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MarkupTextItem
import com.example.data.model.MarkupToolType
import com.example.data.model.PhotoEditRecipe
import com.example.ui.theme.LocalToadColors
import kotlinx.coroutines.delay

@Composable
fun PhotoEditorScreen(
  item: GalleryMediaItem,
  depthModelState: DepthModelPackState,
  isSaving: Boolean,
  onEstimateDepth: suspend (Bitmap) -> Result<DepthMapResult>,
  onStartDepthDownload: () -> Unit,
  onCancelDepthDownload: () -> Unit,
  onDeleteDepthModel: () -> Unit,
  onSaveAsCopy: (PhotoEditRecipe) -> Unit,
  onApplyNonDestructive: (PhotoEditRecipe) -> Unit,
  onRevertToOriginal: () -> Unit,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  val extended = LocalToadColors.current
  val initialRecipe = remember(item.id, item.editRecipeJson) { PhotoEditRecipe.fromJson(item.editRecipeJson) }

  var sourceBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
  var croppedSourceBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
  var renderedBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
  var depthMap by remember(item.id) { mutableStateOf<DepthMapResult?>(null) }
  var isComputingDepth by remember { mutableStateOf(false) }

  var recipe by remember(item.id) { mutableStateOf(initialRecipe) }
  val undoStack = remember(item.id) { mutableStateListOf<PhotoEditRecipe>() }
  val redoStack = remember(item.id) { mutableStateListOf<PhotoEditRecipe>() }
  var lastCommittedRecipe by remember(item.id) { mutableStateOf(initialRecipe) }

  var activeTab by remember { mutableStateOf(EditorToolTab.ADJUST) }
  var selectedAdjustParam by remember { mutableStateOf(AdjustParam.EXPOSURE) }
  var selectedMarkupTool by remember { mutableStateOf(MarkupToolType.PEN) }
  var selectedMarkupColorHex by remember { mutableStateOf(0xFF6EE7B7L) }
  var strokeWidthNorm by remember { mutableFloatStateOf(0.014f) }

  var isComparingOriginal by remember { mutableStateOf(false) }
  var isSplitCompare by remember { mutableStateOf(false) }
  var showSaveDialog by remember { mutableStateOf(false) }
  var showDiscardDialog by remember { mutableStateOf(false) }
  var showAddTextDialog by remember { mutableStateOf(false) }
  var textDraft by remember { mutableStateOf("") }

  fun commitChange(nextRecipe: PhotoEditRecipe = recipe) {
    if (nextRecipe != lastCommittedRecipe) {
      undoStack.add(lastCommittedRecipe)
      if (undoStack.size > 30) undoStack.removeAt(0)
      redoStack.clear()
      lastCommittedRecipe = nextRecipe
    }
    recipe = nextRecipe
  }

  fun performUndo() {
    if (undoStack.isEmpty()) return
    redoStack.add(recipe)
    val prev = undoStack.removeAt(undoStack.lastIndex)
    recipe = prev
    lastCommittedRecipe = prev
  }

  fun performRedo() {
    if (redoStack.isEmpty()) return
    undoStack.add(recipe)
    val next = redoStack.removeAt(redoStack.lastIndex)
    recipe = next
    lastCommittedRecipe = next
  }

  val hasUnsavedChanges = recipe != initialRecipe

  BackHandler {
    when {
      showSaveDialog -> showSaveDialog = false
      showAddTextDialog -> showAddTextDialog = false
      showDiscardDialog -> showDiscardDialog = false
      hasUnsavedChanges -> showDiscardDialog = true
      else -> onClose()
    }
  }

  // Load bounded preview source bitmap
  LaunchedEffect(item.id, item.uriString, item.drawableResName) {
    sourceBitmap = PhotoEditorRenderer.loadSourceBitmap(context, item, maxDimension = 1024)
  }

  // Recompute geometry-applied base for Crop + Depth alignment whenever crop geometry changes
  LaunchedEffect(sourceBitmap, recipe.crop, activeTab) {
    val src = sourceBitmap ?: return@LaunchedEffect
    croppedSourceBitmap =
      if (activeTab == EditorToolTab.CROP) PhotoEditorRenderer.applyTransformOnly(src, recipe.crop)
      else PhotoEditorRenderer.applyGeometryOnly(src, recipe.crop)
    depthMap = null
  }

  // Run ONNX Depth Anything V2 automatically when Bokeh tab opens & pack is installed
  LaunchedEffect(activeTab, depthModelState.isInstalled, croppedSourceBitmap) {
    val base = croppedSourceBitmap ?: return@LaunchedEffect
    if (
      (activeTab == EditorToolTab.BOKEH || recipe.bokeh.enabled) &&
        depthModelState.isInstalled &&
        depthMap == null &&
        !isComputingDepth
    ) {
      isComputingDepth = true
      val res = onEstimateDepth(base)
      depthMap = res.getOrNull()
      isComputingDepth = false
      val computed = depthMap
      if (computed != null && activeTab == EditorToolTab.BOKEH && !recipe.bokeh.enabled) {
        val focalD = computed.sampleAt(recipe.bokeh.focusX, recipe.bokeh.focusY)
        val next = recipe.copy(bokeh = recipe.bokeh.copy(enabled = true, focusDepth = focalD))
        commitChange(next)
      }
    }
  }

  // Debounced live render pass
  LaunchedEffect(sourceBitmap, recipe, depthMap, activeTab) {
    val src = sourceBitmap ?: return@LaunchedEffect
    delay(24)
    val includeCrop = activeTab != EditorToolTab.CROP
    renderedBitmap =
      PhotoEditorRenderer.renderFullPreview(
        source = src,
        recipe = recipe,
        depthMap = depthMap,
        includeCrop = includeCrop,
      )
  }

  val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize()
        .background(Color(0xFF060B08))
        .testTag("photo_editor_screen")
  ) {
    val isWide = maxWidth >= 600.dp

    Column(modifier = Modifier.fillMaxSize()) {
      // Top Darkroom Toolbar
      Surface(color = Color(0xFF0A120E)) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(top = statusTop + 4.dp, bottom = 6.dp, start = 8.dp, end = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = {
              if (hasUnsavedChanges) showDiscardDialog = true else onClose()
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_close_button"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close editor", tint = Color.White)
          }

          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(
              onClick = ::performUndo,
              enabled = undoStack.isNotEmpty(),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_undo_button"),
            ) {
              Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.28f),
              )
            }
            IconButton(
              onClick = ::performRedo,
              enabled = redoStack.isNotEmpty(),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_redo_button"),
            ) {
              Icon(
                Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.28f),
              )
            }
            IconButton(
              onClick = { commitChange(PhotoEditRecipe()) },
              enabled = !recipe.isIdentity,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_reset_button"),
            ) {
              Icon(
                Icons.Filled.RestartAlt,
                contentDescription = "Reset all edits",
                tint = if (!recipe.isIdentity) Color.White else Color.White.copy(alpha = 0.28f),
              )
            }

            // Hold-to-Compare
            Surface(
              shape = CircleShape,
              color = if (isComparingOriginal) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
              modifier =
                Modifier.testTag("editor_compare_button")
                  .clip(CircleShape)
                  .pointerInput(Unit) {
                    detectTapGestures(
                      onPress = {
                        isComparingOriginal = true
                        tryAwaitRelease()
                        isComparingOriginal = false
                      }
                    )
                  },
            ) {
              Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = "Hold to compare original",
                tint = if (isComparingOriginal) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.padding(8.dp).size(18.dp),
              )
            }

            IconButton(
              onClick = { isSplitCompare = !isSplitCompare },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_split_toggle_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Compare,
                contentDescription = "Split screen before after comparison",
                tint = if (isSplitCompare) MaterialTheme.colorScheme.primary else Color.White,
              )
            }
          }

          Button(
            onClick = { showSaveDialog = true },
            enabled = !isSaving,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_save_button"),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
          ) {
            if (isSaving) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
              Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Save", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
          }
        }
      }

      HorizontalDivider(color = extended.borderSubtle)

      // Adaptive Body: Side-by-side on wide tablets/foldables, vertical stack on handhelds
      if (isWide) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
          PhotoEditorCanvas(
            originalBitmap = croppedSourceBitmap ?: sourceBitmap,
            renderedBitmap = renderedBitmap,
            recipe = recipe,
            activeTab = activeTab,
            isComparingOriginal = isComparingOriginal,
            isSplitCompare = isSplitCompare,
            isComputingDepth = isComputingDepth,
            activeMarkupTool = selectedMarkupTool,
            activeMarkupColorHex = selectedMarkupColorHex,
            activeStrokeWidthNorm = strokeWidthNorm,
            depthInstalled = depthModelState.isInstalled,
            onCropChange = { newCrop -> commitChange(recipe.copy(crop = newCrop)) },
            onBokehFocusTap = { nx, ny ->
              val d = depthMap?.sampleAt(nx, ny) ?: recipe.bokeh.focusDepth
              commitChange(
                recipe.copy(
                  bokeh = recipe.bokeh.copy(enabled = true, focusX = nx, focusY = ny, focusDepth = d)
                )
              )
            },
            onAddMarkupStroke = { stroke -> commitChange(recipe.copy(strokes = recipe.strokes + stroke)) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )

          VerticalDivider(color = extended.borderSubtle)

          Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(340.dp).fillMaxHeight().padding(bottom = navBottom),
          ) {
            Column(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              EditorActiveToolTray(
                activeTab = activeTab,
                recipe = recipe,
                selectedAdjustParam = selectedAdjustParam,
                selectedMarkupTool = selectedMarkupTool,
                selectedMarkupColorHex = selectedMarkupColorHex,
                strokeWidthNorm = strokeWidthNorm,
                sourceAspect =
                  ((croppedSourceBitmap ?: sourceBitmap)?.let { it.width.toFloat() / it.height.coerceAtLeast(1) })
                    ?: item.aspectRatio,
                depthState = depthModelState,
                depthInferenceMs = depthMap?.inferenceTimeMs,
                onSelectAdjustParam = { selectedAdjustParam = it },
                onAdjustChange = { p, v -> recipe = recipe.copy(adjust = recipe.adjust.withParam(p, v)) },
                onCommit = { commitChange(recipe) },
                onSelectFilter = { look -> commitChange(recipe.copy(filterLook = look)) },
                onFilterIntensityChange = { recipe = recipe.copy(filterIntensity = it) },
                onFilmGrainChange = { recipe = recipe.copy(filmGrain = it) },
                onUpdateCrop = { c, commit -> if (commit) commitChange(recipe.copy(crop = c)) else recipe = recipe.copy(crop = c) },
                onUpdateBokeh = { b, commit -> if (commit) commitChange(recipe.copy(bokeh = b)) else recipe = recipe.copy(bokeh = b) },
                onStartDepthDownload = onStartDepthDownload,
                onCancelDepthDownload = onCancelDepthDownload,
                onDeleteDepthModel = onDeleteDepthModel,
                onSelectMarkupTool = { selectedMarkupTool = it },
                onSelectMarkupColor = { selectedMarkupColorHex = it },
                onStrokeWidthChange = { strokeWidthNorm = it },
                onOpenAddText = { showAddTextDialog = true },
                onClearMarkup = { commitChange(recipe.copy(strokes = emptyList(), textItems = emptyList())) },
              )
              EditorToolTabRow(activeTab = activeTab, onSelectTab = { activeTab = it })
            }
          }
        }
      } else {
        PhotoEditorCanvas(
          originalBitmap = croppedSourceBitmap ?: sourceBitmap,
          renderedBitmap = renderedBitmap,
          recipe = recipe,
          activeTab = activeTab,
          isComparingOriginal = isComparingOriginal,
          isSplitCompare = isSplitCompare,
          isComputingDepth = isComputingDepth,
          activeMarkupTool = selectedMarkupTool,
          activeMarkupColorHex = selectedMarkupColorHex,
          activeStrokeWidthNorm = strokeWidthNorm,
          depthInstalled = depthModelState.isInstalled,
          onCropChange = { newCrop -> commitChange(recipe.copy(crop = newCrop)) },
          onBokehFocusTap = { nx, ny ->
            val d = depthMap?.sampleAt(nx, ny) ?: recipe.bokeh.focusDepth
            commitChange(
              recipe.copy(
                bokeh = recipe.bokeh.copy(enabled = true, focusX = nx, focusY = ny, focusDepth = d)
              )
            )
          },
          onAddMarkupStroke = { stroke -> commitChange(recipe.copy(strokes = recipe.strokes + stroke)) },
          modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(bottom = navBottom + 4.dp)) {
            HorizontalDivider(color = extended.borderSubtle)
            EditorActiveToolTray(
              activeTab = activeTab,
              recipe = recipe,
              selectedAdjustParam = selectedAdjustParam,
              selectedMarkupTool = selectedMarkupTool,
              selectedMarkupColorHex = selectedMarkupColorHex,
              strokeWidthNorm = strokeWidthNorm,
              sourceAspect =
                ((croppedSourceBitmap ?: sourceBitmap)?.let { it.width.toFloat() / it.height.coerceAtLeast(1) })
                  ?: item.aspectRatio,
              depthState = depthModelState,
              depthInferenceMs = depthMap?.inferenceTimeMs,
              onSelectAdjustParam = { selectedAdjustParam = it },
              onAdjustChange = { p, v -> recipe = recipe.copy(adjust = recipe.adjust.withParam(p, v)) },
              onCommit = { commitChange(recipe) },
              onSelectFilter = { look -> commitChange(recipe.copy(filterLook = look)) },
              onFilterIntensityChange = { recipe = recipe.copy(filterIntensity = it) },
              onFilmGrainChange = { recipe = recipe.copy(filmGrain = it) },
              onUpdateCrop = { c, commit -> if (commit) commitChange(recipe.copy(crop = c)) else recipe = recipe.copy(crop = c) },
              onUpdateBokeh = { b, commit -> if (commit) commitChange(recipe.copy(bokeh = b)) else recipe = recipe.copy(bokeh = b) },
              onStartDepthDownload = onStartDepthDownload,
              onCancelDepthDownload = onCancelDepthDownload,
              onDeleteDepthModel = onDeleteDepthModel,
              onSelectMarkupTool = { selectedMarkupTool = it },
              onSelectMarkupColor = { selectedMarkupColorHex = it },
              onStrokeWidthChange = { strokeWidthNorm = it },
              onOpenAddText = { showAddTextDialog = true },
              onClearMarkup = { commitChange(recipe.copy(strokes = emptyList(), textItems = emptyList())) },
            )
            HorizontalDivider(color = extended.borderSubtle)
            EditorToolTabRow(activeTab = activeTab, onSelectTab = { activeTab = it })
          }
        }
      }
    }
  }

  // Dual Save Mode Dialog: Save as Copy vs Apply Non-Destructively
  if (showSaveDialog) {
    AlertDialog(
      onDismissRequest = { showSaveDialog = false },
      modifier = Modifier.testTag("editor_save_dialog"),
      title = { Text("Save Edited Photo", style = MaterialTheme.typography.titleMedium) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Choose how to persist your edits to the gallery:",
            style = MaterialTheme.typography.bodySmall,
            color = extended.textMuted,
          )
          Surface(
            onClick = {
              showSaveDialog = false
              onApplyNonDestructive(recipe)
            },
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("editor_save_nondestructive_btn"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = "Apply Non-Destructively",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text = "Updates this photo while preserving the original for instant revert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
              )
            }
          }

          Surface(
            onClick = {
              showSaveDialog = false
              onSaveAsCopy(recipe)
            },
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("editor_save_copy_btn"),
            shape = RoundedCornerShape(16.dp),
            color = extended.surfaceHigh,
            border = BorderStroke(1.dp, extended.borderStrong),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = "Save as New Copy",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "Renders a new JPEG file alongside the original in gallery & Pictures.",
                style = MaterialTheme.typography.bodySmall,
                color = extended.textMuted,
              )
            }
          }

          if (item.hasNonDestructiveEdits) {
            OutlinedButton(
              onClick = {
                showSaveDialog = false
                onRevertToOriginal()
                onClose()
              },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("editor_revert_original_btn"),
            ) {
              Text("Revert Existing Photo to Original")
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showDiscardDialog) {
    AlertDialog(
      onDismissRequest = { showDiscardDialog = false },
      title = { Text("Discard unsaved edits?") },
      text = { Text("Your current adjustments will be lost if you exit without saving.") },
      confirmButton = {
        Button(
          onClick = {
            showDiscardDialog = false
            onClose()
          },
          modifier = Modifier.testTag("editor_confirm_discard_btn"),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
          Text("Discard")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDiscardDialog = false }) { Text("Keep Editing") }
      },
    )
  }

  if (showAddTextDialog) {
    AlertDialog(
      onDismissRequest = { showAddTextDialog = false },
      title = { Text("Add Text Label") },
      text = {
        OutlinedTextField(
          value = textDraft,
          onValueChange = { textDraft = it.take(48) },
          label = { Text("Caption or note") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("markup_text_input"),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val clean = textDraft.trim()
            if (clean.isNotEmpty()) {
              val offsetY = (0.35f + (recipe.textItems.size % 5) * 0.10f).coerceIn(0.2f, 0.8f)
              val nextItem =
                MarkupTextItem(
                  id = System.currentTimeMillis(),
                  text = clean,
                  x = 0.5f,
                  y = offsetY,
                  colorHex = selectedMarkupColorHex,
                  hasPillBackdrop = true,
                )
              commitChange(recipe.copy(textItems = recipe.textItems + nextItem))
            }
            textDraft = ""
            showAddTextDialog = false
          },
          enabled = textDraft.isNotBlank(),
          modifier = Modifier.testTag("markup_text_confirm_btn"),
        ) {
          Text("Add")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddTextDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun EditorActiveToolTray(
  activeTab: EditorToolTab,
  recipe: PhotoEditRecipe,
  selectedAdjustParam: AdjustParam,
  selectedMarkupTool: MarkupToolType,
  selectedMarkupColorHex: Long,
  strokeWidthNorm: Float,
  sourceAspect: Float,
  depthState: DepthModelPackState,
  depthInferenceMs: Long?,
  onSelectAdjustParam: (AdjustParam) -> Unit,
  onAdjustChange: (AdjustParam, Float) -> Unit,
  onCommit: () -> Unit,
  onSelectFilter: (com.example.data.model.FilterLook) -> Unit,
  onFilterIntensityChange: (Float) -> Unit,
  onFilmGrainChange: (Float) -> Unit,
  onUpdateCrop: (CropParams, Boolean) -> Unit,
  onUpdateBokeh: (com.example.data.model.BokehParams, Boolean) -> Unit,
  onStartDepthDownload: () -> Unit,
  onCancelDepthDownload: () -> Unit,
  onDeleteDepthModel: () -> Unit,
  onSelectMarkupTool: (MarkupToolType) -> Unit,
  onSelectMarkupColor: (Long) -> Unit,
  onStrokeWidthChange: (Float) -> Unit,
  onOpenAddText: () -> Unit,
  onClearMarkup: () -> Unit,
) {
  when (activeTab) {
    EditorToolTab.ADJUST ->
      AdjustToolPanel(
        recipe = recipe,
        selectedParam = selectedAdjustParam,
        onSelectParam = onSelectAdjustParam,
        onValueChange = onAdjustChange,
        onValueCommit = onCommit,
      )
    EditorToolTab.FILTERS ->
      FiltersToolPanel(
        recipe = recipe,
        onSelectFilter = onSelectFilter,
        onIntensityChange = onFilterIntensityChange,
        onGrainChange = onFilmGrainChange,
        onCommit = onCommit,
      )
    EditorToolTab.CROP ->
      CropToolPanel(
        crop = recipe.crop,
        sourceAspect = sourceAspect,
        onSelectAspect = { preset, aspect ->
          onUpdateCrop(applyAspectPresetToCrop(recipe.crop, preset, aspect), true)
        },
        onStraightenChange = { deg -> onUpdateCrop(recipe.crop.copy(straightenDegrees = deg), false) },
        onStraightenCommit = onCommit,
        onRotateLeft = {
          onUpdateCrop(recipe.crop.copy(rotationQuarterTurns = (recipe.crop.rotationQuarterTurns + 3) % 4), true)
        },
        onRotateRight = {
          onUpdateCrop(recipe.crop.copy(rotationQuarterTurns = (recipe.crop.rotationQuarterTurns + 1) % 4), true)
        },
        onFlipH = { onUpdateCrop(recipe.crop.copy(flipHorizontal = !recipe.crop.flipHorizontal), true) },
        onFlipV = { onUpdateCrop(recipe.crop.copy(flipVertical = !recipe.crop.flipVertical), true) },
        onResetCrop = { onUpdateCrop(CropParams(), true) },
      )
    EditorToolTab.BOKEH ->
      BokehToolPanel(
        bokeh = recipe.bokeh,
        depthState = depthState,
        depthInferenceMs = depthInferenceMs,
        onStartDownload = onStartDepthDownload,
        onCancelDownload = onCancelDepthDownload,
        onDeleteModel = onDeleteDepthModel,
        onBokehChange = { b -> onUpdateBokeh(b, false) },
        onBokehCommit = onCommit,
      )
    EditorToolTab.MARKUP ->
      MarkupToolPanel(
        selectedTool = selectedMarkupTool,
        selectedColorHex = selectedMarkupColorHex,
        strokeWidthNorm = strokeWidthNorm,
        hasMarkup = recipe.strokes.isNotEmpty() || recipe.textItems.isNotEmpty(),
        onSelectTool = onSelectMarkupTool,
        onSelectColor = onSelectMarkupColor,
        onStrokeWidthChange = onStrokeWidthChange,
        onOpenAddTextDialog = onOpenAddText,
        onClearMarkup = onClearMarkup,
      )
  }
}

@Composable
private fun EditorToolTabRow(
  activeTab: EditorToolTab,
  onSelectTab: (EditorToolTab) -> Unit,
) {
  val extended = LocalToadColors.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EditorToolTab.entries.forEach { tab ->
      val selected = tab == activeTab
      val icon: ImageVector =
        when (tab) {
          EditorToolTab.ADJUST -> Icons.Filled.Tune
          EditorToolTab.FILTERS -> Icons.Filled.AutoFixHigh
          EditorToolTab.CROP -> Icons.Filled.Crop
          EditorToolTab.BOKEH -> Icons.Filled.BlurOn
          EditorToolTab.MARKUP -> Icons.Filled.Brush
        }
      Surface(
        onClick = { onSelectTab(tab) },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_tab_${tab.name.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Icon(
            imageVector = icon,
            contentDescription = tab.label,
            tint = if (selected) MaterialTheme.colorScheme.primary else extended.textMuted,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else extended.textMuted,
          )
        }
      }
    }
  }
}

private fun applyAspectPresetToCrop(
  crop: CropParams,
  preset: AspectRatioPreset,
  sourceAspect: Float,
): CropParams {
  if (preset == AspectRatioPreset.FREE || preset == AspectRatioPreset.ORIGINAL) {
    return crop.copy(left = 0f, top = 0f, right = 1f, bottom = 1f, aspectPreset = preset)
  }
  val targetRatio = preset.ratio ?: return crop.copy(aspectPreset = preset)
  val relRatio = targetRatio / sourceAspect.coerceAtLeast(0.3f)
  return if (relRatio < 1f) {
    val halfW = (relRatio / 2f).coerceIn(0.15f, 0.5f)
    crop.copy(left = 0.5f - halfW, right = 0.5f + halfW, top = 0f, bottom = 1f, aspectPreset = preset)
  } else {
    val halfH = ((1f / relRatio) / 2f).coerceIn(0.15f, 0.5f)
    crop.copy(left = 0f, right = 1f, top = 0.5f - halfH, bottom = 0.5f + halfH, aspectPreset = preset)
  }
}
