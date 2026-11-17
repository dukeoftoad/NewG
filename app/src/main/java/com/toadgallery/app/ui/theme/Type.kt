package com.toadgallery.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.toadgallery.app.R
import com.toadgallery.app.domain.AppFont

val PlusJakartaSansFamily =
  FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
  )

val InterFamily =
  FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
  )

val ToadTypography =
  Typography(
    headlineLarge =
      TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
      ),
  )

/** The Appearance page's font picker entry point: the tuned scale above with the
 *  chosen family swapped into every role (weights keep their per-role tuning —
 *  the family only changes the letterforms; see [fontFamilyFor] in BundledFonts.kt).
 *  [AppFont.DEFAULT] reproduces the shipped look exactly.
 */
fun toadTypographyFor(font: AppFont): Typography =
  withFamily(ToadTypography, fontFamilyFor(font))

/** Swaps [family] into every M3 role of [t], keeping sizes, weights and tracking. */
private fun withFamily(t: Typography, family: FontFamily): Typography =
  Typography(
    displayLarge = t.displayLarge.copy(fontFamily = family),
    displayMedium = t.displayMedium.copy(fontFamily = family),
    displaySmall = t.displaySmall.copy(fontFamily = family),
    headlineLarge = t.headlineLarge.copy(fontFamily = family),
    headlineMedium = t.headlineMedium.copy(fontFamily = family),
    headlineSmall = t.headlineSmall.copy(fontFamily = family),
    titleLarge = t.titleLarge.copy(fontFamily = family),
    titleMedium = t.titleMedium.copy(fontFamily = family),
    titleSmall = t.titleSmall.copy(fontFamily = family),
    bodyLarge = t.bodyLarge.copy(fontFamily = family),
    bodyMedium = t.bodyMedium.copy(fontFamily = family),
    bodySmall = t.bodySmall.copy(fontFamily = family),
    labelLarge = t.labelLarge.copy(fontFamily = family),
    labelMedium = t.labelMedium.copy(fontFamily = family),
    labelSmall = t.labelSmall.copy(fontFamily = family),
  )
