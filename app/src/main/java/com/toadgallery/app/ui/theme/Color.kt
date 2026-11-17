package com.toadgallery.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ToadExtendedColors(
  val surfaceHigh: Color,
  val surfaceHighest: Color,
  val glassSurface: Color,
  val borderSubtle: Color,
  val borderStrong: Color,
  val textMuted: Color,
  val textDim: Color,
  val dangerContainer: Color,
  val onDangerContainer: Color,
  val isLight: Boolean,
)

val LocalToadColors = staticCompositionLocalOf { ToadGreenExtended }

// 1. Toad-Green Dark (Default)
val ToadGreenDarkScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFF6EE7B7),
    onPrimary = Color(0xFF032619),
    primaryContainer = Color(0xFF134E3A),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF032619),
    secondaryContainer = Color(0xFF18362A),
    onSecondaryContainer = Color(0xFFECFDF5),
    tertiary = Color(0xFFA7F3D0),
    onTertiary = Color(0xFF042F2E),
    background = Color(0xFF07130E),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF0E1F18),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF152B22),
    onSurfaceVariant = Color(0xFF94B8A8),
    outline = Color(0x598FD9B6),
    outlineVariant = Color(0x248FD9B6),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0x38EF4444),
    onErrorContainer = Color(0xFFFCA5A5),
  )

val ToadGreenExtended =
  ToadExtendedColors(
    surfaceHigh = Color(0xFF152B22),
    surfaceHighest = Color(0xFF1D392E),
    glassSurface = Color(0xD607130E),
    borderSubtle = Color(0x248FD9B6),
    borderStrong = Color(0x598FD9B6),
    textMuted = Color(0xFFA3C9B8),
    textDim = Color(0xFF749989),
    dangerContainer = Color(0x2EEF4444),
    onDangerContainer = Color(0xFFFCA5A5),
    isLight = false,
  )

// 2. Dynamic Amber Dark
val DynamicAmberDarkScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFFFBBF24),
    onPrimary = Color(0xFF291800),
    primaryContainer = Color(0xFF5C3C08),
    onPrimaryContainer = Color(0xFFFEF3C7),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF291800),
    secondaryContainer = Color(0xFF362A1A),
    onSecondaryContainer = Color(0xFFFFFBEB),
    tertiary = Color(0xFFFDE68A),
    onTertiary = Color(0xFF291800),
    background = Color(0xFF14100B),
    onBackground = Color(0xFFFFFBEB),
    surface = Color(0xFF201A13),
    onSurface = Color(0xFFFFFBEB),
    surfaceVariant = Color(0xFF2B231A),
    onSurfaceVariant = Color(0xFFD2C2A6),
    outline = Color(0x61FBBF24),
    outlineVariant = Color(0x26FBBF24),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0x38EF4444),
    onErrorContainer = Color(0xFFFCA5A5),
  )

val DynamicAmberExtended =
  ToadExtendedColors(
    surfaceHigh = Color(0xFF2B231A),
    surfaceHighest = Color(0xFF382E22),
    glassSurface = Color(0xD914100B),
    borderSubtle = Color(0x26FBBF24),
    borderStrong = Color(0x61FBBF24),
    textMuted = Color(0xFFD2C2A6),
    textDim = Color(0xFF9C8B70),
    dangerContainer = Color(0x2EEF4444),
    onDangerContainer = Color(0xFFFCA5A5),
    isLight = false,
  )

// 3. Ocean Slate Dark
val OceanSlateDarkScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF042233),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF042233),
    secondaryContainer = Color(0xFF153147),
    onSecondaryContainer = Color(0xFFF0F9FF),
    tertiary = Color(0xFFBAE6FD),
    onTertiary = Color(0xFF042233),
    background = Color(0xFF08111A),
    onBackground = Color(0xFFF0F9FF),
    surface = Color(0xFF0F1D2B),
    onSurface = Color(0xFFF0F9FF),
    surfaceVariant = Color(0xFF172A3D),
    onSurfaceVariant = Color(0xFFA0C0D8),
    outline = Color(0x5C38BDF8),
    outlineVariant = Color(0x2438BDF8),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0x38EF4444),
    onErrorContainer = Color(0xFFFCA5A5),
  )

val OceanSlateExtended =
  ToadExtendedColors(
    surfaceHigh = Color(0xFF172A3D),
    surfaceHighest = Color(0xFF203952),
    glassSurface = Color(0xD908111A),
    borderSubtle = Color(0x2438BDF8),
    borderStrong = Color(0x5C38BDF8),
    textMuted = Color(0xFFA0C0D8),
    textDim = Color(0xFF6B8DA6),
    dangerContainer = Color(0x2EEF4444),
    onDangerContainer = Color(0xFFFCA5A5),
    isLight = false,
  )

// 4. OLED Pure Black
val OledBlackDarkScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFF6EE7B7),
    onPrimary = Color(0xFF022417),
    primaryContainer = Color(0xFF0F3D2E),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF022417),
    secondaryContainer = Color(0xFF161F1C),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFA7F3D0),
    onTertiary = Color(0xFF022417),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0C0F0E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF161B19),
    onSurfaceVariant = Color(0xFFB4BDC7),
    outline = Color(0x596EE7B7),
    outlineVariant = Color(0x22FFFFFF),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0x3AEF4444),
    onErrorContainer = Color(0xFFFCA5A5),
  )

val OledBlackExtended =
  ToadExtendedColors(
    surfaceHigh = Color(0xFF161B19),
    surfaceHighest = Color(0xFF202724),
    glassSurface = Color(0xE0000000),
    borderSubtle = Color(0x22FFFFFF),
    borderStrong = Color(0x596EE7B7),
    textMuted = Color(0xFFB4BDC7),
    textDim = Color(0xFF818A94),
    dangerContainer = Color(0x33EF4444),
    onDangerContainer = Color(0xFFFCA5A5),
    isLight = false,
  )

// 5. Botanical Light
val BotanicalLightScheme: ColorScheme =
  lightColorScheme(
    primary = Color(0xFF0F7654),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6F6E1),
    onPrimaryContainer = Color(0xFF063726),
    secondary = Color(0xFF0D9488),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1EFE8),
    onSecondaryContainer = Color(0xFF091E16),
    tertiary = Color(0xFF047857),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F9F6),
    onBackground = Color(0xFF091E16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF091E16),
    surfaceVariant = Color(0xFFE8F3ED),
    onSurfaceVariant = Color(0xFF3A584B),
    outline = Color(0x4010412D),
    outlineVariant = Color(0x1A10412D),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0x1ADC2626),
    onErrorContainer = Color(0xFFB91C1C),
  )

val BotanicalLightExtended =
  ToadExtendedColors(
    surfaceHigh = Color(0xFFE8F3ED),
    surfaceHighest = Color(0xFFD7EAE0),
    glassSurface = Color(0xDCF4F9F6),
    borderSubtle = Color(0x1A10412D),
    borderStrong = Color(0x4010412D),
    textMuted = Color(0xFF3A584B),
    textDim = Color(0xFF5D7C6E),
    dangerContainer = Color(0x1ADC2626),
    onDangerContainer = Color(0xFFB91C1C),
    isLight = true,
  )

// 6. Toad Forest Dark — adapted from toadFILES' ToadThemes.DarkForestDark:
// deep moss over near-black, a lively leaf green does the talking, amber
// warms up highlights.
val ToadForestDarkScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFF9FE06B),
    onPrimary = Color(0xFF1A3700),
    primaryContainer = Color(0xFF2E5312),
    onPrimaryContainer = Color(0xFFB9F18A),
    secondary = Color(0xFFBACCB3),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B38),
    onSecondaryContainer = Color(0xFFD5E8CD),
    tertiary = Color(0xFFFFB855),
    onTertiary = Color(0xFF4A2B00),
    tertiaryContainer = Color(0xFF683F00),
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = Color(0xFF0D120E),
    onBackground = Color(0xFFDDE5DA),
    surface = Color(0xFF0D120E),
    onSurface = Color(0xFFDDE5DA),
    surfaceVariant = Color(0xFF3F4A3B),
    onSurfaceVariant = Color(0xFFBFCAB9),
    outline = Color(0xFF89957F),
    outlineVariant = Color(0xFF3F4A3B),
    scrim = Color.Black,
    inverseSurface = Color(0xFFDDE5DA),
    inverseOnSurface = Color(0xFF2A312A),
    inversePrimary = Color(0xFF3D6509),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF080C08),
    surfaceContainerLow = Color(0xFF141A14),
    surfaceContainer = Color(0xFF181F18),
    surfaceContainerHigh = Color(0xFF232B21),
    surfaceContainerHighest = Color(0xFF2D362B),
  )

val ToadForestExtended =
  ToadExtendedColors(
    surfaceHigh = Color(0xFF232B21),
    surfaceHighest = Color(0xFF2D362B),
    glassSurface = Color(0xD60D120E),
    borderSubtle = Color(0x249FE06B),
    borderStrong = Color(0x599FE06B),
    textMuted = Color(0xFFBFCAB9),
    textDim = Color(0xFF89957F),
    dangerContainer = Color(0x2EEF4444),
    onDangerContainer = Color(0xFFFFB4AB),
    isLight = false,
  )

// 7. Slate Neon Dark — adapted from toadFILES' ToadThemes.SlateNeonDark:
// graphite surfaces, OLED-true blacks, exactly one electric green and a cold
// cyan for tertiary contrast.
val SlateNeonDarkScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFF57F2A0),
    onPrimary = Color(0xFF00391E),
    primaryContainer = Color(0xFF005231),
    onPrimaryContainer = Color(0xFF7CFFBC),
    secondary = Color(0xFFB6C0BB),
    onSecondary = Color(0xFF222925),
    secondaryContainer = Color(0xFF38403C),
    onSecondaryContainer = Color(0xFFD2DCD6),
    tertiary = Color(0xFF82E3FF),
    onTertiary = Color(0xFF003441),
    tertiaryContainer = Color(0xFF004C5E),
    onTertiaryContainer = Color(0xFFB4EBFF),
    background = Color(0xFF0A0C0B),
    onBackground = Color(0xFFDDE5E0),
    surface = Color(0xFF0A0C0B),
    onSurface = Color(0xFFDDE5E0),
    surfaceVariant = Color(0xFF39423D),
    onSurfaceVariant = Color(0xFFB7C1BA),
    outline = Color(0xFF737D77),
    outlineVariant = Color(0xFF2A322D),
    inverseSurface = Color(0xFFDDE5E0),
    inverseOnSurface = Color(0xFF252B28),
    inversePrimary = Color(0xFF006D43),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF050605),
    surfaceContainerLow = Color(0xFF101312),
    surfaceContainer = Color(0xFF151918),
    surfaceContainerHigh = Color(0xFF1F2422),
    surfaceContainerHighest = Color(0xFF292F2C),
  )

val SlateNeonExtended =
  ToadExtendedColors(
    surfaceHigh = Color(0xFF1F2422),
    surfaceHighest = Color(0xFF292F2C),
    glassSurface = Color(0xD60A0C0B),
    borderSubtle = Color(0x2457F2A0),
    borderStrong = Color(0x5957F2A0),
    textMuted = Color(0xFFB7C1BA),
    textDim = Color(0xFF737D77),
    dangerContainer = Color(0x2EEF4444),
    onDangerContainer = Color(0xFFFFB4AB),
    isLight = false,
  )

// 8. Material 3 Baseline Dark — adapted from toadFILES' ToadThemes.Material3Dark:
// Google's baseline reference palette, from the M3 spec.
val M3BaselineDarkScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF332D41),
    inversePrimary = Color(0xFF6750A4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
  )

val M3BaselineExtended =
  ToadExtendedColors(
    surfaceHigh = Color(0xFF2B2930),
    surfaceHighest = Color(0xFF36343B),
    glassSurface = Color(0xD6141218),
    borderSubtle = Color(0x24D0BCFF),
    borderStrong = Color(0x59D0BCFF),
    textMuted = Color(0xFFCAC4D0),
    textDim = Color(0xFF938F99),
    dangerContainer = Color(0x2EEF4444),
    onDangerContainer = Color(0xFFFFB4AB),
    isLight = false,
  )
