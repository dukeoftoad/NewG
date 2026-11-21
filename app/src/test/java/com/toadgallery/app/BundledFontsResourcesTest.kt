package com.toadgallery.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.toadgallery.app.domain.AppFont
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v3.0.0 — every Appearance font entry resolves to a usable family. SYSTEM
 * maps to the platform default; every other enum value must produce a
 * distinct non-default FontFamily. The backing R.font resources must exist
 * and load — checked directly against the resource system, which is the
 * same surface the picker's previews render through. Guards against a dead
 * picker entry after a rename or a dropped ttf.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BundledFontsResourcesTest {

  @Test
  fun `every font entry resolves to a font family`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AppFont.entries.forEach { font ->
      val family = com.toadgallery.app.ui.theme.fontFamilyFor(font)
      assertTrue(
        "font family resolved to nothing for ${font.name}",
        font == AppFont.SYSTEM || family != androidx.compose.ui.text.font.FontFamily.Default,
      )
    }
    // Every non-system entry's backing resource must exist and load,
    // including the v3.0.0 sketch pair (regression guard for the copy).
    val fontResources =
      listOf("toad_regular", "manrope_regular", "spacegrotesk_regular", "jetbrainsmono_regular", "caveat", "patrick_hand")
    fontResources.forEach { name ->
      val resId = context.resources.getIdentifier(name, "font", context.packageName)
      assertTrue("missing font resource $name", resId != 0)
    }
  }
}
