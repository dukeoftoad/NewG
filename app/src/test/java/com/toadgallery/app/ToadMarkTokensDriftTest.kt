package com.toadgallery.app

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drift guard for the ToadMark token pipeline (v2.5.0):
 * design/tokens/toadmark.json is the single source of truth, and the committed
 * Kotlin and Dart consumers must stay in sync with it. If this test fails, a
 * token was edited without re-running scripts/gen_tokens.py — regenerate and
 * commit all three files together. Pure file/text checks, no UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ToadMarkTokensDriftTest {

    private fun repoFile(relative: String): File {
        // Gradle runs JVM tests with the module dir (app/) as working dir.
        val candidates = listOf(File(relative), File("../$relative"), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: error("cannot locate $relative from ${File(".").absolutePath}")
    }

    private fun toadMarkNames(json: JSONObject): List<Pair<String, String>> {
        val color = json.getJSONObject("color")
        val out = mutableListOf<Pair<String, String>>()
        for (group in listOf("light", "dark", "accent", "neutral")) {
            val obj = color.getJSONObject(group)
            for (key in obj.keys()) {
                val prefix = if (group == "light" || group == "dark") group.replaceFirstChar { it.uppercase() } else ""
                val name = key.replaceFirstChar { it.uppercase() }
                out.add("ToadMark$prefix$name" to obj.getString(key))
            }
        }
        return out
    }

    @Test
    fun `kotlin token file matches toadmark json`() {
        val kt = repoFile("app/src/main/java/com/toadgallery/app/ui/theme/ToadMarkTokens.kt").readText()
        val json = JSONObject(repoFile("design/tokens/toadmark.json").readText())

        toadMarkNames(json).forEach { (name, hex) ->
            val argb = 0xFF000000L or hex.removePrefix("#").toLong(16)
            val expected = "const val $name = 0x${argb.toString(16).uppercase()}L  // ${hex.uppercase()}"
            assertTrue(
                "ToadMarkTokens.kt is missing '$expected' — run scripts/gen_tokens.py",
                kt.contains(expected),
            )
        }

        // Metric spot checks across every family of the scale.
        val radius = json.getJSONObject("radius")
        val gap = json.getJSONObject("gap")
        val motion = json.getJSONObject("motion").getJSONObject("durationMs")
        val row = json.getJSONObject("row")
        val expectedMetrics =
            listOf(
                "val RadiusBadge = ${radius.getInt("badge")}.dp",
                "val RadiusSheet = ${radius.getInt("sheet")}.dp",
                "val GapXl = ${gap.getInt("xl")}.dp",
                "val MotionFast = ${motion.getInt("fast")}  // ms",
                "val MotionMedium = ${motion.getInt("medium")}  // ms",
                "val MotionSlow = ${motion.getInt("slow")}  // ms",
                "val RowPaddingH = ${row.getInt("paddingH")}.dp",
                "val RowChevronSize = ${row.getInt("chevronSize")}.dp",
            )
        expectedMetrics.forEach { expected ->
            assertTrue("ToadMarkTokens.kt is missing '$expected' — run scripts/gen_tokens.py", kt.contains(expected))
        }
    }

    @Test
    fun `dart token file matches toadmark json`() {
        val dart = repoFile("design/flutter/toadmark_tokens.dart").readText()
        val json = JSONObject(repoFile("design/tokens/toadmark.json").readText())

        toadMarkNames(json).forEach { (name, hex) ->
            val argb = 0xFF000000L or hex.removePrefix("#").toLong(16)
            val dartName = "toadmark" + name.removePrefix("ToadMark")
            val expected = "('$dartName', Color(0x${argb.toString(16).uppercase()})),  // ${hex.uppercase()}"
            assertTrue(
                "toadmark_tokens.dart is missing '$expected' — run scripts/gen_tokens.py",
                dart.contains(expected),
            )
        }
    }

    @Test
    fun `spring presets keep the spatial-effects split`() {
        val kt = repoFile("app/src/main/java/com/toadgallery/app/ui/theme/ToadMarkTokens.kt").readText()
        val json = JSONObject(repoFile("design/tokens/toadmark.json").readText())
        val springs = json.getJSONObject("motion").getJSONObject("spring")

        // Spatial presets overshoot (dampingRatio < 1); effects presets settle flat (= 1).
        for (name in listOf("spatialFast", "spatialDefault", "spatialSlow")) {
            val damping = springs.getJSONObject(name).getDouble("dampingRatio")
            assertTrue("$name must be under-damped, was $damping", damping < 1.0)
        }
        for (name in listOf("effectsFast", "effectsDefault")) {
            val damping = springs.getJSONObject(name).getDouble("dampingRatio")
            assertTrue("$name must settle without overshoot, was $damping", damping == 1.0)
        }
        assertTrue("spring helpers missing", kt.contains("fun springSpatialDefault(): SpringSpec<Float>"))
    }
}
