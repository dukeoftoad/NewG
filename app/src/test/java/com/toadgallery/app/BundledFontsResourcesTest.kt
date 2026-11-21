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
 * v3.0.0 — every Appearance font entry resolves to a usable family: SYSTEM
 * maps to the platform default and every other enum value must produce a
 * non-null FontFamily (its backing R.font resource must exist and load).
 * Guards the picker against a dead entry after a rename or a dropped ttf.
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
        family != com.toadgallery.app.ui.theme.FontFamily.Default || font == AppFont.SYSTEM,
      )
      // And the family actually renders: its first typeface loads a resource.
      if (font != AppFont.SYSTEM) {
        val hasResource = family.fonts.any { it.resourceId != 0 }
        assertTrue("font ${font.name} has no backing resource", hasResource)
      }
    }
    // The v3.0.0 sketch pair specifically (regression guard for the copy).
    assertTrue(context.resources.getIdentifier("caveat", "font", context.packageName) != 0)
    assertTrue(context.resources.getIdentifier("patrick_hand", "font", context.packageName) != 0)
  }
}
