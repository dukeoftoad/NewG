package com.toadgallery.app.domain

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Applies a launcher-icon choice by flipping the enabled state of the
 * `<activity-alias>` entries in AndroidManifest. Ported verbatim from toadFILES'
 * AppIconSwitcher (collapsed to the single icon dimension). Exactly one alias is
 * left enabled at a time, and the home screen then shows that alias's icon.
 *
 * Implementations must never throw: [apply] runs on the app-startup path and on
 * every Appearance-screen change, and a failure should just leave the previous
 * icon in place.
 */
interface AppIconSwitcher {
    /** Enable the alias for [icon] and disable the others. Must not throw. */
    fun apply(icon: AppIcon)
}

/**
 * Production switcher backed by [PackageManager.setComponentEnabledSetting].
 *
 * The enabled alias is set FIRST, then the others are disabled: at no point is the
 * app left without a launcher entry, which on some launchers would remove it from
 * the home screen and break pinned shortcuts. [PackageManager.DONT_KILL_APP] keeps
 * the running process alive across the switch.
 *
 * Before touching any component state, [apply] verifies the TARGET alias actually
 * exists in the installed build (the ghost-alias guard): enabling an alias the
 * manifest doesn't declare while disabling every real one would leave the app with
 * NO launcher entry at all — it would vanish from the home screen until the next
 * install. This can only happen when a persisted icon choice names a component the
 * running build doesn't ship (a downgrade or a restored backup), but the cost of
 * the guard is one PackageManager lookup and the failure it prevents is the
 * "toadGALLERY app not showing" report class.
 *
 * The whole body is wrapped in [runCatching]: this runs at app startup, and a
 * PackageManager failure must never take the app down.
 */
class PackageManagerAppIconSwitcher(
    private val context: Context,
    /**
     * How to decide that a component exists in this build. Defaults to the real
     * PackageManager lookup; tests inject a stub because Robolectric's PackageManager
     * does not resolve activity-aliases (it always throws for them).
     *
     * v3.0.1: the lookup MUST pass [PackageManager.MATCH_DISABLED_COMPONENTS].
     * Every alias except the currently-enabled one ships `android:enabled="false"`
     * in the manifest, and a plain getActivityInfo(component, 0) does not match
     * disabled components — so the guard rejected every switch to a different
     * icon with "target alias is not installed" (founder device, v3.0.0 logs),
     * silently breaking the whole App Icon picker. With the flag, manifest-declared
     * but disabled aliases resolve, while an alias that genuinely is not in the
     * build (downgrade / restored backup) still throws and still trips the guard.
     */
    private val componentInstalled: (ComponentName, PackageManager) -> Boolean = { component, pm ->
        try {
            pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    },
) : AppIconSwitcher {

    override fun apply(icon: AppIcon) {
        runCatching {
            val pm = context.packageManager
            val target = componentNameFor(icon)
            // Ghost-alias guard: never disable the other aliases unless the target
            // really exists — otherwise the app ends up with zero launcher entries.
            check(componentInstalled(target, pm)) { "target alias is not installed: $target" }
            for ((component, state) in plan(icon)) {
                pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }.onFailure { Log.w(TAG, "could not switch launcher alias to $icon", it) }
    }

    /**
     * The exact sequence of component changes [apply] performs once the ghost-alias
     * guard has passed: the target alias FIRST (enabled), then the others
     * (disabled) — at no point is the app left without a launcher entry. Pure and
     * unit-testable; [apply] adds the guard plus the PackageManager calls.
     */
    fun plan(icon: AppIcon): List<Pair<ComponentName, Int>> {
        val target = componentNameFor(icon)
        val disable = AppIcon.entries.asSequence()
            .filter { it != icon }
            .map { componentNameFor(it) to PackageManager.COMPONENT_ENABLED_STATE_DISABLED }
            .toList()
        return listOf(target to PackageManager.COMPONENT_ENABLED_STATE_ENABLED) + disable
    }

    /**
     * The alias of [icon] in this app's package, e.g. FROG_PHOTO →
     * com.toadgallery.app.LauncherFrogPhoto. Delegates to the pure companion
     * mapping [componentNameFor] with the Context's package name.
     */
    fun componentNameFor(icon: AppIcon): ComponentName =
        componentNameFor(context.packageName, icon)

    companion object {
        private const val TAG = "AppIcon"

        /**
         * Pure, Context-free form of the mapping rule:
         * `<pkg>.Launcher<AliasSuffix>` — e.g. FROG_PHOTO in com.toadgallery.app →
         * com.toadgallery.app.LauncherFrogPhoto. Must stay in sync with the
         * activity-alias names in AndroidManifest.xml.
         */
        @JvmStatic
        fun componentNameFor(pkg: String, icon: AppIcon): ComponentName =
            ComponentName(pkg, "$pkg.Launcher${icon.aliasSuffix}")
    }
}
