package com.toadgallery.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.toadgallery.app.domain.AppIcon
import com.toadgallery.app.domain.PackageManagerAppIconSwitcher
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Launcher-icon switching plan + ghost-alias guard, ported from toadFILES'
 * AppIconSwitcherTest. The PackageManager is stubbed via the injected
 * [PackageManagerAppIconSwitcher.componentInstalled] lookup because Robolectric
 * does not resolve activity-aliases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppIconSwitcherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun switcher(installedAliases: Set<String>): PackageManagerAppIconSwitcher =
        PackageManagerAppIconSwitcher(context) { component, _: PackageManager ->
            component.className in installedAliases
        }

    private fun alias(icon: AppIcon): String = "${context.packageName}.Launcher${icon.aliasSuffix}"

    private val allAliases = AppIcon.entries.map { alias(it) }.toSet()

    @Test
    fun `component naming matches the manifest aliases`() {
        assertEquals(
            "com.toadgallery.app.LauncherFrogPhoto",
            PackageManagerAppIconSwitcher.componentNameFor(
                "com.toadgallery.app", AppIcon.FROG_PHOTO
            ).className,
        )
        assertEquals(
            "com.toadgallery.app.LauncherClassic",
            PackageManagerAppIconSwitcher.componentNameFor(
                "com.toadgallery.app", AppIcon.CLASSIC_TOAD
            ).className,
        )
    }

    @Test
    fun `plan enables the target first then disables every other alias`() {
        val plan = switcher(allAliases).plan(AppIcon.CAMERA)
        assertEquals(AppIcon.entries.size, plan.size)
        val first = plan.first()
        assertEquals(alias(AppIcon.CAMERA), first.first.className)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, first.second)
        // No point in the plan leaves the app without a launcher entry: the target
        // is enabled BEFORE anything else is disabled.
        val disabled = plan.drop(1)
        assertTrue(disabled.all { it.second == PackageManager.COMPONENT_ENABLED_STATE_DISABLED })
        assertEquals(
            AppIcon.entries.filter { it != AppIcon.CAMERA }.map { alias(it) }.toSet(),
            disabled.map { it.first.className }.toSet(),
        )
    }

    @Test
    fun `apply refuses to strand the app when the target alias is missing`() {
        // Robolectric's PackageManager does not resolve activity-aliases, so the
        // DEFAULT componentInstalled lookup reports false for every alias — exactly
        // the ghost-alias situation (downgrade / restored backup). apply() must
        // leave every component state untouched instead of disabling the only
        // launcher entry.
        val pm = context.packageManager
        switcher(emptySet()).apply(AppIcon.FROG_PHOTO)
        AppIcon.entries.forEach { icon ->
            assertEquals(
                "ghost-alias guard must keep ${icon.aliasSuffix} untouched",
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                pm.getComponentEnabledSetting(
                    android.content.ComponentName(context.packageName, alias(icon))
                ),
            )
        }
    }

    @Test
    fun `default classic keeps the shipped icon`() {
        assertEquals("ic_launcher", AppIcon.CLASSIC_TOAD.mipmapName)
    }
}
