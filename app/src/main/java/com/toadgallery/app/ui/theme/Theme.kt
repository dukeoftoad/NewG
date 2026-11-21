package com.toadgallery.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toadgallery.app.data.model.AppearanceMode
import com.toadgallery.app.data.model.ThemePalette
import com.toadgallery.app.domain.AppFont

/**
 * The M3 shape ladder mapped onto the ToadMark radius scale (see
 * [ToadMarkTokens]): extraSmall = thumb radius, small = control, medium =
 * card, large = sheet-adjacent cards, extraLarge = sheets and overlays.
 */
val ToadShapes =
  Shapes(
    extraSmall = RoundedCornerShape(ToadMarkTokens.RadiusThumb),
    small = RoundedCornerShape(ToadMarkTokens.RadiusControl),
    medium = RoundedCornerShape(ToadMarkTokens.RadiusCard),
    large = RoundedCornerShape(ToadMarkTokens.RadiusSheet - 4.dp),
    extraLarge = RoundedCornerShape(ToadMarkTokens.RadiusSheet),
  )

@Composable
fun ToadGalleryTheme(
  palette: ThemePalette = ThemePalette.TOAD_GREEN,
  appearanceMode: AppearanceMode = AppearanceMode.DARK,
  useDynamicColors: Boolean = false,
  appFont: AppFont = AppFont.DEFAULT,
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val effectiveDark =
    when (appearanceMode) {
      AppearanceMode.SYSTEM -> palette != ThemePalette.BOTANICAL_LIGHT && systemDark
      AppearanceMode.LIGHT -> false
      AppearanceMode.DARK -> true
    }

  val resolvedPalette =
    when {
      !effectiveDark && appearanceMode == AppearanceMode.LIGHT -> ThemePalette.BOTANICAL_LIGHT
      effectiveDark && palette == ThemePalette.BOTANICAL_LIGHT -> ThemePalette.TOAD_GREEN
      !effectiveDark && palette != ThemePalette.BOTANICAL_LIGHT && appearanceMode == AppearanceMode.SYSTEM ->
        ThemePalette.BOTANICAL_LIGHT
      else -> palette
    }

  val baseScheme =
    when (resolvedPalette) {
      ThemePalette.TOAD_GREEN -> ToadGreenDarkScheme
      ThemePalette.DYNAMIC_AMBER -> DynamicAmberDarkScheme
      ThemePalette.OCEAN_SLATE -> OceanSlateDarkScheme
      ThemePalette.OLED_BLACK -> OledBlackDarkScheme
      ThemePalette.TOAD_FOREST -> ToadForestDarkScheme
      ThemePalette.SLATE_NEON -> SlateNeonDarkScheme
      ThemePalette.M3_BASELINE -> M3BaselineDarkScheme
      ThemePalette.BOTANICAL_LIGHT -> BotanicalLightScheme
    }

  val colorScheme =
    if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val context = LocalContext.current
      if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
      baseScheme
    }

  val extended =
    when (resolvedPalette) {
      ThemePalette.TOAD_GREEN -> ToadGreenExtended
      ThemePalette.DYNAMIC_AMBER -> DynamicAmberExtended
      ThemePalette.OCEAN_SLATE -> OceanSlateExtended
      ThemePalette.OLED_BLACK -> OledBlackExtended
      ThemePalette.TOAD_FOREST -> ToadForestExtended
      ThemePalette.SLATE_NEON -> SlateNeonExtended
      ThemePalette.M3_BASELINE -> M3BaselineExtended
      ThemePalette.BOTANICAL_LIGHT -> BotanicalLightExtended
    }

  CompositionLocalProvider(LocalToadColors provides extended) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = toadTypographyFor(appFont),
      shapes = ToadShapes,
      content = content,
    )
  }
}
