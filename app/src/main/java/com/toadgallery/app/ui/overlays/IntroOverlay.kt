package com.toadgallery.app.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.toadgallery.app.ui.theme.LocalToadColors
import com.toadgallery.app.ui.theme.ToadMarkTokens
import kotlinx.coroutines.launch

/**
 * First-run intro (v3.0.0): three pages — your photos, on-device AI,
 * memories — then the gallery. Shown exactly once per install
 * ([com.toadgallery.app.data.model.UserPreferences.introDone]); an upgrade
 * never re-triggers it because the flag persisted in DataStore survives
 * updates.
 *
 * Tone ported from toadHEALTH's "Welcome to your pond" first-run card
 * (MIT, dukeoftoad): short, warm, concrete — no marketing filler. Each page
 * states one true thing about what the app does with the user's photos.
 */
@Composable
fun IntroOverlay(
  onFinish: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = LocalToadColors.current
  val pagerState = rememberPagerState(pageCount = { PAGES.size })
  val scope = rememberCoroutineScope()

  Surface(
    modifier = modifier.fillMaxSize().testTag("intro_overlay"),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.statusBars)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
        val spec = PAGES[page]
        Column(
          modifier = Modifier.fillMaxSize().padding(top = 48.dp, bottom = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          // Soft brand glow behind the page icon — the ToadMark accent set
          // (toad green here) instead of a stock radial.
          Box(
            modifier =
              Modifier.size(120.dp)
                .background(
                  Brush.radialGradient(
                    colors =
                      listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
                      )
                  ),
                ),
            contentAlignment = Alignment.Center,
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(96.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = spec.icon,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(44.dp).testTag("intro_icon_${page}"),
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(28.dp))
          Text(
            text = spec.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag("intro_title_${page}"),
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = spec.body,
            style = MaterialTheme.typography.bodyLarge,
            color = extended.textMuted,
            modifier = Modifier.padding(horizontal = 12.dp).testTag("intro_body_${page}"),
          )
        }
      }

      // Page dots
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        repeat(PAGES.size) { index ->
          Box(
            modifier =
              Modifier.padding(horizontal = 4.dp)
                .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                .background(
                  color =
                    if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                    else extended.borderSubtle,
                  shape = CircleShape,
                )
          )
        }
      }

      // Actions
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = onFinish,
          modifier = Modifier.testTag("intro_skip"),
        ) {
          Text("Skip")
        }
        Button(
          onClick = {
            if (pagerState.currentPage < PAGES.lastIndex) {
              scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            } else {
              onFinish()
            }
          },
          shape = RoundedCornerShape(ToadMarkTokens.RadiusControl),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          modifier = Modifier.testTag("intro_next"),
        ) {
          Text(
            if (pagerState.currentPage < PAGES.lastIndex) "Next" else "Start browsing",
          )
        }
      }
    }
  }
}

/** One intro page's static content; icons are Material so no new assets. */
private data class IntroPage(
  val icon: ImageVector,
  val title: String,
  val body: String,
)

private val PAGES =
  listOf(
    IntroPage(
      icon = Icons.Filled.Collections,
      title = "Your photos, your pond",
      body =
        "Every photo lives on your device — no account, no cloud, no tracking. " +
          "Browse a collage or grid, group by day, week or month, and pull any " +
          "moment back up with Magic Search.",
    ),
    IntroPage(
      icon = Icons.Filled.AutoAwesome,
      title = "Smart, and private",
      body =
        "People clustering, OCR screenshots and semantic search all run " +
          "on-device. Models download only when you ask, and nothing ever " +
          "leaves this phone.",
    ),
    IntroPage(
      icon = Icons.Filled.Lock,
      title = "Memories & a hidden vault",
      body =
        "On This Day and This Week bring past years back when they matter. " +
          "Photos you want kept safe go into the encrypted vault — " +
          "AES-256, behind your PIN or biometrics.",
    ),
  )
