package com.example.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.GalleryMediaItem
import com.example.ui.components.DecryptedVaultImage

@Composable
fun HiddenVaultViewerOverlay(
  vaultItems: List<GalleryMediaItem>,
  decryptedPreviews: Map<Long, ByteArray>,
  initialItemId: Long,
  onUnhideItem: (Long) -> Unit,
  onDeletePermanentItem: (Long) -> Unit,
  onClose: () -> Unit,
) {
  if (vaultItems.isEmpty()) {
    LaunchedEffect(Unit) { onClose() }
    return
  }

  val initialPage =
    remember(initialItemId, vaultItems) {
      vaultItems.indexOfFirst { it.id == initialItemId }.coerceAtLeast(0)
    }
  val pagerState = rememberPagerState(initialPage = initialPage) { vaultItems.size }
  var showChrome by remember { mutableStateOf(true) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var viewportSize by remember { mutableStateOf(IntSize.Zero) }

  LaunchedEffect(pagerState.currentPage) {
    scale = 1f
    offset = Offset.Zero
  }

  BackHandler {
    if (showDeleteConfirm) {
      showDeleteConfirm = false
    } else {
      onClose()
    }
  }

  val currentItem = vaultItems.getOrNull(pagerState.currentPage) ?: vaultItems.first()
  val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black)
        .onSizeChanged { viewportSize = it }
        .testTag("vault_decrypted_viewer")
  ) {
    HorizontalPager(
      state = pagerState,
      userScrollEnabled = scale <= 1.05f,
      modifier = Modifier.fillMaxSize(),
    ) { pageIndex ->
      val pageItem = vaultItems[pageIndex]
      val isCurrentPage = pageIndex == pagerState.currentPage
      val effectiveScale = if (isCurrentPage) scale else 1f
      val effectiveOffset = if (isCurrentPage) offset else Offset.Zero

      Box(
        modifier =
          Modifier.fillMaxSize()
            .pointerInput(pageItem.id, isCurrentPage) {
              if (!isCurrentPage) return@pointerInput
              detectTapGestures(
                onTap = { showChrome = !showChrome },
                onDoubleTap = {
                  if (scale > 1.05f) {
                    scale = 1f
                    offset = Offset.Zero
                  } else {
                    scale = 2.5f
                  }
                },
              )
            }
            .pointerInput(pageItem.id, isCurrentPage) {
              if (!isCurrentPage) return@pointerInput
              awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                while (true) {
                  val event = awaitPointerEvent(PointerEventPass.Initial)
                  val pressedCount = event.changes.count { it.pressed }
                  if (pressedCount == 0) break

                  if (pressedCount >= 2 || scale > 1.05f) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val nextScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = nextScale
                    if (nextScale <= 1.02f) {
                      scale = 1f
                      offset = Offset.Zero
                    } else {
                      val maxX = (viewportSize.width * (nextScale - 1f)) / 2f
                      val maxY = (viewportSize.height * (nextScale - 1f)) / 2f
                      offset =
                        Offset(
                          x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                          y = (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                    }
                    if (pressedCount >= 2 || scale > 1.05f) {
                      event.changes.forEach { it.consume() }
                    }
                  }
                }
              }
            },
        contentAlignment = Alignment.Center,
      ) {
        DecryptedVaultImage(
          item = pageItem,
          decryptedBytes = decryptedPreviews[pageItem.id],
          contentScale = ContentScale.Fit,
          modifier =
            Modifier.fillMaxSize().graphicsLayer {
              scaleX = effectiveScale
              scaleY = effectiveScale
              translationX = effectiveOffset.x
              translationY = effectiveOffset.y
            },
        )
      }
    }

    // Top Chrome
    AnimatedVisibility(
      visible = showChrome,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(
              Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
              )
            )
            .padding(top = statusTop + 8.dp, bottom = 18.dp, start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        IconButton(
          onClick = onClose,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("vault_viewer_back_button")
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.14f)),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to vault grid",
            tint = Color.White,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = currentItem.title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "Decrypted in memory • ${pagerState.currentPage + 1} of ${vaultItems.size}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6EE7B7),
          )
        }
        if (scale > 1.05f) {
          Surface(
            onClick = {
              scale = 1f
              offset = Offset.Zero
            },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.16f),
          ) {
            Text(
              text = "${String.format(java.util.Locale.US, "%.1f", scale)}x",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        }
      }
    }

    // Bottom Actions Bar
    AnimatedVisibility(
      visible = showChrome,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(
              Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))
              )
            )
            .padding(
              top = 18.dp,
              bottom = navBottom + 14.dp,
              start = 16.dp,
              end = 16.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(
          onClick = {
            val targetId = currentItem.id
            onUnhideItem(targetId)
            if (vaultItems.size <= 1) onClose()
          },
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("vault_restore_item_${currentItem.id}"),
          shape = RoundedCornerShape(16.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          Icon(
            imageVector = Icons.Filled.Restore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text("Unhide")
        }

        OutlinedButton(
          onClick = { showDeleteConfirm = true },
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("vault_delete_item_${currentItem.id}"),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.7f)),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteForever,
            contentDescription = null,
            tint = Color(0xFFFCA5A5),
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text("Delete", color = Color(0xFFFCA5A5))
        }
      }
    }

    if (showDeleteConfirm) {
      AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = { Text("Delete Permanently?") },
        text = {
          Text(
            "This encrypted vault file will be permanently shredded and cannot be recovered."
          )
        },
        confirmButton = {
          Button(
            onClick = {
              val targetId = currentItem.id
              showDeleteConfirm = false
              onDeletePermanentItem(targetId)
              if (vaultItems.size <= 1) onClose()
            },
            modifier = Modifier.testTag("vault_viewer_confirm_delete_button"),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
              ),
          ) {
            Text("Delete Forever")
          }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
        },
      )
    }
  }
}
