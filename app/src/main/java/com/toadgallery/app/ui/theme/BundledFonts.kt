package com.toadgallery.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.toadgallery.app.R
import com.toadgallery.app.domain.AppFont

/*
 * The Appearance font picker's families, adapted from toadFILES' font system:
 * static instancing of Google Fonts variable TTFs, subsetted to Latin and
 * de-hinted (sources + OFL texts live in the toadFILES repo's docs/licenses).
 *
 *  - Toad: Baloo 2 (SIL OFL) renamed per the OFL's Reserved Font Name rule.
 *  - Manrope: rounded and friendly, the toadFILES brand family.
 *  - Inter: the gallery already bundles it as a variable font (R.font.inter,
 *    see [InterFamily] in Type.kt) — the picker reuses that family.
 *  - Space Grotesk: quirky techy sans; its weight axis tops out at Bold, so
 *    display styles asking for ExtraBold resolve to Bold via best-match.
 *  - JetBrains Mono: monospace, same width for every glyph.
 */
val ToadFontFamily =
  FontFamily(
    Font(R.font.toad_regular, FontWeight.Normal),
    Font(R.font.toad_medium, FontWeight.Medium),
    Font(R.font.toad_semibold, FontWeight.SemiBold),
    Font(R.font.toad_bold, FontWeight.Bold),
    Font(R.font.toad_extrabold, FontWeight.ExtraBold),
  )

val ManropeFontFamily =
  FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
  )

val SpaceGroteskFontFamily =
  FontFamily(
    Font(R.font.spacegrotesk_regular, FontWeight.Normal),
    Font(R.font.spacegrotesk_medium, FontWeight.Medium),
    Font(R.font.spacegrotesk_semibold, FontWeight.SemiBold),
    Font(R.font.spacegrotesk_bold, FontWeight.Bold),
  )

/** v3.0.0: toadHEALTH's sketch pair — single weight (SIL OFL). */
val CaveatFontFamily = FontFamily(Font(R.font.caveat, FontWeight.Normal))

val PatrickHandFontFamily = FontFamily(Font(R.font.patrick_hand, FontWeight.Normal))

val JetBrainsMonoFontFamily =
  FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
    Font(R.font.jetbrainsmono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
    Font(R.font.jetbrainsmono_extrabold, FontWeight.ExtraBold),
  )

/**
 * The [FontFamily] a picker entry renders with; [AppFont.SYSTEM] is the
 * platform default. The gallery's own Inter family covers [AppFont.INTER].
 */
fun fontFamilyFor(font: AppFont): FontFamily =
  when (font) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.TOAD -> ToadFontFamily
    AppFont.MANROPE -> ManropeFontFamily
    AppFont.INTER -> InterFamily
    AppFont.SPACE_GROTESK -> SpaceGroteskFontFamily
    AppFont.JETBRAINS_MONO -> JetBrainsMonoFontFamily
    AppFont.CAVEAT -> CaveatFontFamily
    AppFont.PATRICK_HAND -> PatrickHandFontFamily
  }
