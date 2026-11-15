package com.example.ui.overlays

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.CropParams
import com.example.data.model.EditorToolTab
import com.example.data.model.MarkupStroke
import com.example.data.model.MarkupToolType
import com.example.data.model.NormalizedPoint
import com.example.data.model.PhotoEditRecipe
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PhotoEditorCanvas(
  originalBitmap: Bitmap?,
  renderedBitmap: Bitmap?,
  recipe: PhotoEditRecipe,
  activeTab: EditorToolTab,
  isComparingOriginal: Boolean,
  isSplitCompare: Boolean,
  isComputingDepth: Boolean,
  activeMarkupTool: MarkupToolType,
  activeMarkupColorHex: Long,
  activeStrokeWidthNorm: Float,
  depthInstalled: Boolean,
  onCropChange: (CropParams) -> Unit,
  onBokehFocusTap: (Float, Float) -> Unit,
  onAddMarkupStroke: (MarkupStroke) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  var splitFraction by remember { mutableFloatStateOf(0.5f) }
  var containerSize by remember { mutableStateOf(IntSize.Zero) }
  var liveStrokePoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }

  val baseDisplay =
    if (isComparingOriginal) originalBitmap
    else (renderedBitmap ?: originalBitmap)

  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .padding(8.dp)
        .onSizeChanged { containerSize = it }
        .testTag("editor_canvas"),
    contentAlignment = Alignment.Center,
  ) {
    if (baseDisplay == null) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      return@BoxWithConstraints
    }

    val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
    val bmpAspect = baseDisplay.width.toFloat() / baseDisplay.height.coerceAtLeast(1).toFloat()
    val fitW: Float
    val fitH: Float
    if (boxW / boxH > bmpAspect) {
      fitH = boxH
      fitW = fitH * bmpAspect
    } else {
      fitW = boxW
      fitH = fitW / bmpAspect
    }
    val fitWdp = with(density) { fitW.toDp() }
    val fitHdp = with(density) { fitH.toDp() }

    val imageBitmap = remember(baseDisplay) { baseDisplay.asImageBitmap() }
    val origImageBitmap = remember(originalBitmap) { originalBitmap?.asImageBitmap() }

    Box(
      modifier =
        Modifier.width(fitWdp)
          .height(fitHdp)
          .clipToBounds()
          .then(
            if (activeTab == EditorToolTab.BOKEH && depthInstalled && !isSplitCompare) {
              Modifier.pointerInput(activeTab, depthInstalled) {
                detectTapGestures { offset ->
                  val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                  val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                  onBokehFocusTap(nx, ny)
                }
              }
            } else {
              Modifier
            }
          )
          .then(
            if (
              activeTab == EditorToolTab.MARKUP &&
                activeMarkupTool != MarkupToolType.TEXT &&
                !isSplitCompare
            ) {
              Modifier.pointerInput(activeMarkupTool, activeMarkupColorHex, activeStrokeWidthNorm) {
                detectDragGestures(
                  onDragStart = { start ->
                    val nx = (start.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val ny = (start.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    liveStrokePoints = listOf(NormalizedPoint(nx, ny))
                  },
                  onDrag = { change, _ ->
                    change.consume()
                    val nx = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val ny = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    liveStrokePoints =
                      if (
                        activeMarkupTool == MarkupToolType.PEN ||
                          activeMarkupTool == MarkupToolType.HIGHLIGHTER ||
                          activeMarkupTool == MarkupToolType.PRIVACY_BLUR
                      ) {
                        liveStrokePoints + NormalizedPoint(nx, ny)
                      } else {
                        listOf(liveStrokePoints.firstOrNull() ?: NormalizedPoint(nx, ny), NormalizedPoint(nx, ny))
                      }
                  },
                  onDragEnd = {
                    if (liveStrokePoints.size >= 2) {
                      onAddMarkupStroke(
                        MarkupStroke(
                          id = System.currentTimeMillis(),
                          tool = activeMarkupTool,
                          colorHex = activeMarkupColorHex,
                          strokeWidthNorm = activeStrokeWidthNorm,
                          points = liveStrokePoints,
                        )
                      )
                    }
                    liveStrokePoints = emptyList()
                  },
                  onDragCancel = { liveStrokePoints = emptyList() },
                )
              }
            } else {
              Modifier
            }
          )
    ) {
      Image(
        bitmap = imageBitmap,
        contentDescription = "Edited preview",
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize(),
      )

      // Split Compare Curtain
      if (isSplitCompare && origImageBitmap != null && !isComparingOriginal) {
        Canvas(
          modifier =
            Modifier.fillMaxSize().pointerInput(Unit) {
              detectDragGestures { change, _ ->
                change.consume()
                splitFraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0.08f, 0.92f)
              }
            }
        ) {
          val splitPx = size.width * splitFraction
          // Draw clipped original on the left side via drawContext.canvas.save/clip
          drawContext.canvas.save()
          drawContext.canvas.clipRect(0f, 0f, splitPx, size.height)
          drawImage(
            image = origImageBitmap,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
          )
          drawContext.canvas.restore()

          drawLine(
            color = Color.White,
            start = Offset(splitPx, 0f),
            end = Offset(splitPx, size.height),
            strokeWidth = 3f,
          )
        }

        // Split handle thumb & Before/After labels
        Box(
          modifier =
            Modifier.align(Alignment.CenterStart)
              .offset { IntOffset((fitW * splitFraction - with(density) { 18.dp.toPx() }).roundToInt(), 0) }
              .size(36.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.72f))
              .border(1.5.dp, Color.White, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
            contentDescription = "Drag split comparison",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
          )
        }

        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.65f),
          modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
          Text(
            text = "ORIGINAL",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
          modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
          Text(
            text = "EDITED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }

      // Live Markup Drawing Stroke Preview
      if (liveStrokePoints.size >= 2) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val strokePx = activeStrokeWidthNorm * min(size.width, size.height)
          val color =
            if (activeMarkupTool == MarkupToolType.HIGHLIGHTER) {
              Color(activeMarkupColorHex).copy(alpha = 0.45f)
            } else if (activeMarkupTool == MarkupToolType.PRIVACY_BLUR) {
              Color.White.copy(alpha = 0.55f)
            } else {
              Color(activeMarkupColorHex)
            }
          when (activeMarkupTool) {
            MarkupToolType.PEN,
            MarkupToolType.HIGHLIGHTER,
            MarkupToolType.PRIVACY_BLUR -> {
              val path = Path()
              liveStrokePoints.forEachIndexed { idx, p ->
                val px = p.x * size.width
                val py = p.y * size.height
                if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
              }
              drawPath(
                path = path,
                color = color,
                style =
                  Stroke(
                    width =
                      if (activeMarkupTool == MarkupToolType.HIGHLIGHTER) strokePx * 2.2f
                      else strokePx
                  ),
              )
            }
            MarkupToolType.RECT -> {
              val a = liveStrokePoints.first()
              val b = liveStrokePoints.last()
              drawRect(
                color = color,
                topLeft = Offset(min(a.x, b.x) * size.width, min(a.y, b.y) * size.height),
                size = Size(abs(b.x - a.x) * size.width, abs(b.y - a.y) * size.height),
                style = Stroke(width = strokePx),
              )
            }
            MarkupToolType.CIRCLE -> {
              val a = liveStrokePoints.first()
              val b = liveStrokePoints.last()
              drawOval(
                color = color,
                topLeft = Offset(min(a.x, b.x) * size.width, min(a.y, b.y) * size.height),
                size = Size(abs(b.x - a.x) * size.width, abs(b.y - a.y) * size.height),
                style = Stroke(width = strokePx),
              )
            }
            MarkupToolType.ARROW -> {
              val a = liveStrokePoints.first()
              val b = liveStrokePoints.last()
              drawLine(
                color = color,
                start = Offset(a.x * size.width, a.y * size.height),
                end = Offset(b.x * size.width, b.y * size.height),
                strokeWidth = strokePx,
              )
            }
            MarkupToolType.TEXT -> Unit
          }
        }
      }

      // Interactive Crop Overlay when Crop tab is active
      if (activeTab == EditorToolTab.CROP && !isComparingOriginal) {
        InteractiveCropOverlay(
          crop = recipe.crop,
          onCropChange = onCropChange,
        )
      }

      // Focus Reticle when Bokeh tab is active and model is ready
      if (activeTab == EditorToolTab.BOKEH && depthInstalled && recipe.bokeh.enabled && !isComparingOriginal) {
        val fx = (recipe.bokeh.focusX * fitW - with(density) { 24.dp.toPx() }).roundToInt()
        val fy = (recipe.bokeh.focusY * fitH - with(density) { 24.dp.toPx() }).roundToInt()
        Box(
          modifier =
            Modifier.offset { IntOffset(fx, fy) }
              .size(48.dp)
              .clip(CircleShape)
              .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
              .background(Color.Black.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              Modifier.size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
          )
        }

        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.65f),
          modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
        ) {
          Text(
            text =
              String.format(
                Locale.US,
                "Focus Plane %d%% • %s • Tap to refocus",
                (recipe.bokeh.focusDepth * 100f).roundToInt(),
                recipe.bokeh.fStopLabel,
              ),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      if (isComparingOriginal) {
        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.72f),
          modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
        ) {
          Text(
            text = "Original",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
          )
        }
      }

      if (isComputingDepth) {
        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.78f),
          modifier = Modifier.align(Alignment.Center),
        ) {
          Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
              text = "Estimating ONNX depth map...",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InteractiveCropOverlay(
  crop: CropParams,
  onCropChange: (CropParams) -> Unit,
) {
  var dragTarget by remember { mutableStateOf(CropDragTarget.NONE) }
  var currentCrop by remember(crop) { mutableStateOf(crop) }

  Canvas(
    modifier =
      Modifier.fillMaxSize().pointerInput(crop) {
        detectDragGestures(
          onDragStart = { start ->
            val nx = start.x / size.width.coerceAtLeast(1)
            val ny = start.y / size.height.coerceAtLeast(1)
            // A fixed touch radius avoids the oversized 8% edge zones that
            // previously stole drags intended to move the crop rectangle.
            val hitRadiusPx = 12.dp.toPx()
            val nearL = abs(start.x - currentCrop.left * size.width) <= hitRadiusPx
            val nearR = abs(start.x - currentCrop.right * size.width) <= hitRadiusPx
            val nearT = abs(start.y - currentCrop.top * size.height) <= hitRadiusPx
            val nearB = abs(start.y - currentCrop.bottom * size.height) <= hitRadiusPx
            dragTarget =
              when {
                nearL && nearT -> CropDragTarget.TOP_LEFT
                nearR && nearT -> CropDragTarget.TOP_RIGHT
                nearL && nearB -> CropDragTarget.BOTTOM_LEFT
                nearR && nearB -> CropDragTarget.BOTTOM_RIGHT
                nearL -> CropDragTarget.LEFT
                nearR -> CropDragTarget.RIGHT
                nearT -> CropDragTarget.TOP
                nearB -> CropDragTarget.BOTTOM
                nx in currentCrop.left..currentCrop.right &&
                  ny in currentCrop.top..currentCrop.bottom -> CropDragTarget.MOVE
                else -> CropDragTarget.NONE
              }
          },
          onDrag = { change, dragAmount ->
            change.consume()
            val dx = dragAmount.x / size.width.coerceAtLeast(1)
            val dy = dragAmount.y / size.height.coerceAtLeast(1)
            val minSpan = 0.16f
            currentCrop =
              when (dragTarget) {
                CropDragTarget.LEFT ->
                  currentCrop.copy(left = (currentCrop.left + dx).coerceIn(0f, currentCrop.right - minSpan))
                CropDragTarget.RIGHT ->
                  currentCrop.copy(right = (currentCrop.right + dx).coerceIn(currentCrop.left + minSpan, 1f))
                CropDragTarget.TOP ->
                  currentCrop.copy(top = (currentCrop.top + dy).coerceIn(0f, currentCrop.bottom - minSpan))
                CropDragTarget.BOTTOM ->
                  currentCrop.copy(bottom = (currentCrop.bottom + dy).coerceIn(currentCrop.top + minSpan, 1f))
                CropDragTarget.TOP_LEFT ->
                  currentCrop.copy(
                    left = (currentCrop.left + dx).coerceIn(0f, currentCrop.right - minSpan),
                    top = (currentCrop.top + dy).coerceIn(0f, currentCrop.bottom - minSpan),
                  )
                CropDragTarget.TOP_RIGHT ->
                  currentCrop.copy(
                    right = (currentCrop.right + dx).coerceIn(currentCrop.left + minSpan, 1f),
                    top = (currentCrop.top + dy).coerceIn(0f, currentCrop.bottom - minSpan),
                  )
                CropDragTarget.BOTTOM_LEFT ->
                  currentCrop.copy(
                    left = (currentCrop.left + dx).coerceIn(0f, currentCrop.right - minSpan),
                    bottom = (currentCrop.bottom + dy).coerceIn(currentCrop.top + minSpan, 1f),
                  )
                CropDragTarget.BOTTOM_RIGHT ->
                  currentCrop.copy(
                    right = (currentCrop.right + dx).coerceIn(currentCrop.left + minSpan, 1f),
                    bottom = (currentCrop.bottom + dy).coerceIn(currentCrop.top + minSpan, 1f),
                  )
                CropDragTarget.MOVE -> {
                  val wSpan = currentCrop.right - currentCrop.left
                  val hSpan = currentCrop.bottom - currentCrop.top
                  val newL = (currentCrop.left + dx).coerceIn(0f, 1f - wSpan)
                  val newT = (currentCrop.top + dy).coerceIn(0f, 1f - hSpan)
                  currentCrop.copy(left = newL, top = newT, right = newL + wSpan, bottom = newT + hSpan)
                }
                CropDragTarget.NONE -> currentCrop
              }
          },
          onDragEnd = {
            if (dragTarget != CropDragTarget.NONE) {
              onCropChange(currentCrop)
            }
            dragTarget = CropDragTarget.NONE
          },
          onDragCancel = { dragTarget = CropDragTarget.NONE },
        )
      }
  ) {
    val l = currentCrop.left * size.width
    val t = currentCrop.top * size.height
    val r = currentCrop.right * size.width
    val b = currentCrop.bottom * size.height
    val cw = r - l
    val ch = b - t

    // Dim outer region
    val dimColor = Color.Black.copy(alpha = 0.56f)
    drawRect(dimColor, Offset.Zero, Size(size.width, t))
    drawRect(dimColor, Offset(0f, b), Size(size.width, size.height - b))
    drawRect(dimColor, Offset(0f, t), Size(l, ch))
    drawRect(dimColor, Offset(r, t), Size(size.width - r, ch))

    // Rule of thirds lines
    val gridColor = Color.White.copy(alpha = 0.35f)
    drawLine(gridColor, Offset(l + cw / 3f, t), Offset(l + cw / 3f, b), 1f)
    drawLine(gridColor, Offset(l + cw * 2f / 3f, t), Offset(l + cw * 2f / 3f, b), 1f)
    drawLine(gridColor, Offset(l, t + ch / 3f), Offset(r, t + ch / 3f), 1f)
    drawLine(gridColor, Offset(l, t + ch * 2f / 3f), Offset(r, t + ch * 2f / 3f), 1f)

    // Crop border
    drawRect(Color.White, Offset(l, t), Size(cw, ch), style = Stroke(width = 2.5f))

    // Corner handles
    val hLen = max(14f, min(cw, ch) * 0.1f)
    val accent = Color(0xFF6EE7B7)
    val sw = 5f
    // Top-left
    drawLine(accent, Offset(l, t), Offset(l + hLen, t), sw)
    drawLine(accent, Offset(l, t), Offset(l, t + hLen), sw)
    // Top-right
    drawLine(accent, Offset(r, t), Offset(r - hLen, t), sw)
    drawLine(accent, Offset(r, t), Offset(r, t + hLen), sw)
    // Bottom-left
    drawLine(accent, Offset(l, b), Offset(l + hLen, b), sw)
    drawLine(accent, Offset(l, b), Offset(l, b - hLen), sw)
    // Bottom-right
    drawLine(accent, Offset(r, b), Offset(r - hLen, b), sw)
    drawLine(accent, Offset(r, b), Offset(r, b - hLen), sw)
  }
}

private enum class CropDragTarget {
  NONE,
  LEFT,
  RIGHT,
  TOP,
  BOTTOM,
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  MOVE,
}
