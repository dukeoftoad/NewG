package com.toadgallery.app.domain

/**
 * Font picker (Appearance page), adapted from toadFILES' font system.
 * [SYSTEM] follows the device's default typeface; every other entry is bundled
 * under res/font as static weights, subsetted to Latin.
 *
 * [TOAD] is the unique toad font: Baloo 2 (SIL OFL, see the toadFILES licenses
 * docs) instanced to static weights and renamed to "Toad" — the OFL requires a
 * renamed Modified Version, and the chunky rounded letterforms suit the mascot.
 * Defaults keep the shipped look ([DEFAULT]); every pick is the user's.
 */
enum class AppFont(val label: String, val description: String) {
  SYSTEM("System", "Your device's default typeface"),
  TOAD("Toad", "Chunky, bouncy letters shaped like the mascot"),
  MANROPE("Manrope", "Rounded and friendly — the toadFILES look"),
  INTER("Inter", "Crisp and neutral, built for screens"),
  SPACE_GROTESK("Space Grotesk", "Quirky technical shapes with wide caps"),
  JETBRAINS_MONO("JetBrains Mono", "Monospace — every glyph the same width"),
  ;

  companion object {
    /** Preserves the shipped gallery look for upgraders. */
    val DEFAULT = SYSTEM
  }
}
