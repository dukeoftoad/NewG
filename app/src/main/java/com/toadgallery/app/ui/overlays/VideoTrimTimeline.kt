package com.toadgallery.app.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toadgallery.app.data.video.TrimTimelineState
import com.toadgallery.app.data.video.VideoFrameExtractor
import kotlin.math.roundToInt

/**
 * Visual trim timeline (v2.3.0): a frame-thumbnail strip sampled across the
 * whole video, the trimmed-out head and tail dimmed, and a RangeSlider
 * driving the selection. All range math lives in [TrimTimelineState]; this
 * composable only renders and reports back whole-millisecond positions.
 *
 * The strip decodes on a background dispatcher through a single retriever
 * session ([VideoFrameExtractor.extractStrip]); frames fade in as they
 * decode, placeholders fill the gaps so the timeline stays usable on slow
 * or corrupt media.
 */
@Composable
fun VideoTrimTimeline(
  uriString: String,
  durationMs: Long,
  startMs: Long,
  endMs: Long,
  onRangeChange: (startMs: Long, endMs: Long) -> Unit,
  modifier: Modifier = Modifier,
  frameCount: Int = 8,
) {
  val context = LocalContext.current
  var strip by remember(uriString, durationMs) {
    mutableStateOf<List<android.graphics.Bitmap?>>(List(frameCount) { null })
  }
  LaunchedEffect(uriString, durationMs, frameCount) {
    strip = VideoFrameExtractor(context).extractStrip(uriString, frameCount, durationMs)
  }

  val startFrac = (startMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
  val endFrac = (endMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxWidth()
          .height(64.dp)
          .clip(RoundedCornerShape(10.dp)),
    ) {
      Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        strip.forEach { bitmap ->
          Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (bitmap != null) {
              androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxSize(),
              ) {}
            }
          }
        }
      }

      // Trimmed-out head and tail, dimmed; thin white markers at the edges.
      Box(
        modifier =
          Modifier.align(Alignment.CenterStart)
            .fillMaxHeight()
            .fillMaxWidth(startFrac)
            .background(Color.Black.copy(alpha = 0.55f)),
      )
      Box(
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .fillMaxHeight()
            .fillMaxWidth((1f - endFrac))
            .background(Color.Black.copy(alpha = 0.55f)),
      )
      Box(
        modifier =
          Modifier.align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(3.dp)
            .background(Color.White.copy(alpha = 0.85f))
            .offset { androidx.compose.ui.unit.IntOffset((maxWidth.toPx() * startFrac).roundToInt(), 0) },
      )
      Box(
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(3.dp)
            .background(Color.White.copy(alpha = 0.85f))
            .offset { androidx.compose.ui.unit.IntOffset(-(maxWidth.toPx() * (1f - endFrac)).roundToInt(), 0) },
      )
    }

    RangeSlider(
      value = (startMs / 1000f)..(endMs / 1000f),
      onValueChange = { range ->
        onRangeChange(
          (range.start * 1000f).toLong().coerceAtLeast(0L),
          (range.endInclusive * 1000f).toLong(),
        )
      },
      valueRange = 0f..(durationMs / 1000f),
      modifier = Modifier.fillMaxWidth(),
    )

    Text(
      text =
        "${TrimTimelineState.formatClock(startMs)} – ${TrimTimelineState.formatClock(endMs)}" +
          " · ${TrimTimelineState.formatClock(endMs - startMs)} selected",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 4.dp),
    )
  }
}
