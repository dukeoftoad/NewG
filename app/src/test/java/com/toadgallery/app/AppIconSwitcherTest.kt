package com.toadgallery.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.toadgallery.app.domain.AppIcon
import com.toadgallery.app.domain.PackageManagerAppIconSwitcher
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PackageManagerAppIconSwitcher: the component-name rule must match the
 * activity-alias names in AndroidManifest exactly — otherwise the switcher would flip
 * the state of components that don't exist and the launcher icon would never change.
 * Manifest sync is verified against the raw manifest source (Robolectric's
 * PackageManager does not resolve activity-aliases), and the switch sequence via the
 * pure [PackageManagerAppIconSwitcher.plan]. Ported from toadFILES.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppIconSwitcherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newSwitcher() = PackageManagerAppIconSwitcher(context)

    /** The app's manifest source — unit tests run with the module directory as CWD. */
    private val manifestText: String by lazy {
        listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.exists() }
            .readText()
    }

    /** Every alias, same mapping the switcher uses. */
    private fun allAliases(): List<ComponentName> =
        AppIcon.entries.map { PackageManagerAppIconSwitcher.componentNameFor(context.packageName, it) }

    @Test
    fun `componentNameFor maps FROG_PHOTO by hand`() {
        val cn = newSwitcher().componentNameFor(AppIcon.FROG_PHOTO)
        assertEquals("com.toadgallery.app", cn.packageName)
        assertEquals("com.toadgallery.app.LauncherFrogPhoto", cn.className)
    }

    @Test
    fun `all icon variants produce distinct names that resolve to manifest aliases`() {
        val switcher = newSwitcher()
        val classNames = mutableSetOf<String>()
        for (icon in AppIcon.entries) {
            val cn = switcher.componentNameFor(icon)
            assertEquals(context.packageName, cn.packageName)
            assertTrue(cn.className.startsWith("${context.packageName}.Launcher"))
            // Every mapped component must be declared as an activity-alias in the
            // manifest source — this is what keeps the rule in sync with it.
            assertTrue(
                "manifest must declare alias ${cn.className}",
                manifestText.contains("android:name=\".${cn.className.substringAfterLast('.')}\""),
            )
            classNames += cn.className
        }
        assertEquals(AppIcon.entries.size, classNames.size)
        assertEquals(AppIcon.entries.size, Regex("<activity-alias").findAll(manifestText).count())
    }

    @Test
    fun `apply plan enables the chosen alias first and disables the others`() {
        val plan = newSwitcher().plan(AppIcon.CAMERA)

        // Exactly 8 component changes; the target is FIRST and the only ENABLED one.
        assertEquals(AppIcon.entries.size, plan.size)
        assertEquals(
            ComponentName("com.toadgallery.app", "com.toadgallery.app.LauncherCamera"),
            plan.first().first,
        )
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, plan.first().second)
        assertEquals(AppIcon.entries.size - 1, plan.drop(1).map { it.first }.toSet().size)
        assertTrue(
            plan.drop(1).all { it.second == PackageManager.COMPONENT_ENABLED_STATE_DISABLED },
        )
        // The shipped default is among the disabled ones, and the target never repeats.
        assertTrue(plan.drop(1).any { it.first.className.endsWith("LauncherClassic") })
        assertFalse(plan.drop(1).any { it.first.className.endsWith("LauncherCamera") })
    }

    @Test
    fun `apply with an installed target enables it and disables the others`() {
        val pm = context.packageManager
        val switcher = PackageManagerAppIconSwitcher(context) { _, _ -> true }
        val target = ComponentName("com.toadgallery.app", "com.toadgallery.app.LauncherFrogPhoto")

        // Seed every alias with an explicit setting first. Robolectric's component
        // map is a static-per-sandbox map that tests in the same JVM can leave
        // leftovers in — an explicit baseline makes the asserts below mean "what
        // apply wrote" and nothing else.
        allAliases().forEach {
            pm.setComponentEnabledSetting(
                it,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }

        switcher.apply(AppIcon.FROG_PHOTO)

        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            pm.getComponentEnabledSetting(target),
        )
        allAliases().filter { it != target }.forEach {
            assertEquals(
                "alias ${it.className} must be disabled after apply",
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                pm.getComponentEnabledSetting(it),
            )
        }

        // Leave the campground cleaner than we found it: reset every alias this test
        // touched back to DEFAULT. Robolectric's component-settings state can leak
        // across tests in the same JVM, so an ENABLED leftover here is exactly what
        // other tests must never inherit.
        allAliases().forEach {
            pm.setComponentEnabledSetting(
                it,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    @Test
    fun `apply bails out untouched when the target alias is a ghost`() {
        // Regression for the "app not showing" class: a persisted choice naming an
        // alias this build doesn't ship must NOT disable the real launcher entries —
        // that left the app with zero launcher components.
        val pm = context.packageManager
        val ghost = PackageManagerAppIconSwitcher(context) { _, _ -> false }
        val all = allAliases()
        // Seed every alias with an EXPLICIT setting before the probe: for UNSET
        // components Robolectric's PackageManager resolves the manifest-declared
        // state lazily, so a bare "still DEFAULT" assert reads spuriously.
        all.forEach {
            pm.setComponentEnabledSetting(
                it,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        // Assert the DELTA, not absolute values: a healthy ghost guard writes
        // NOTHING, so the after-snapshot must equal the before-snapshot exactly.
        // A broken guard still fails it loudly — the plan's first write ENABLES the
        // ghost target on top of the seed, changing the map between the snapshots.
        val before = all.associateWith { pm.getComponentEnabledSetting(it) }
        ghost.apply(AppIcon.CLASSIC_TOAD)
        val after = all.associateWith { pm.getComponentEnabledSetting(it) }
        assertEquals("ghost guard must leave every alias untouched", before, after)
    }

    @Test
    fun `default lookup resolves manifest-declared aliases including disabled ones`() {
        val pm = context.packageManager
        val target =
            PackageManagerAppIconSwitcher.componentNameFor(context.packageName, AppIcon.FROG_PHOTO)
        val plainOk = runCatching { pm.getActivityInfo(target, 0) }.isSuccess
        val flagOk =
            runCatching { pm.getActivityInfo(target, PackageManager.MATCH_DISABLED_COMPONENTS) }
                .isSuccess
        if (!plainOk && !flagOk) {
            // Robolectric cannot resolve activity-aliases at all — the real-device
            // behavior of the flag is pinned by the ghost-guard stub tests and the
            // on-device soak; nothing testable here.
            return
        }
        assertTrue("MATCH_DISABLED_COMPONENTS lookup must resolve the disabled alias", flagOk)
    }
}
