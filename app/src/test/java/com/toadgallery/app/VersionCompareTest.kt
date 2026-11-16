package com.toadgallery.app

import com.toadgallery.app.domain.VersionCompare
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Table-driven checks for the semantic-ish version comparison used to decide whether a
 * GitHub release tag is newer than the installed version. Pure JVM. Ported from toadFILES.
 * The rule under test: only a STRICTLY newer candidate may ever trigger the update panel.
 */
class VersionCompareTest {

    private fun assertNewer(current: String, candidate: String, expected: Boolean) {
        assertEquals(
            "isNewer(current=\"$current\", candidate=\"$candidate\")",
            expected,
            VersionCompare.isNewer(current, candidate),
        )
    }

    @Test
    fun `equal versions are never newer`() {
        assertNewer("1.2.0", "1.2.0", false)
        assertNewer("1.2", "1.2.0", false)
        assertNewer("v1.2.0", "1.2.0", false)
    }

    @Test
    fun `patch minor and major bumps are newer`() {
        assertNewer("1.2.0", "1.2.1", true)
        assertNewer("1.2.0", "1.3.0", true)
        assertNewer("1.2.0", "2.0.0", true)
    }

    @Test
    fun `older candidates are not newer`() {
        assertNewer("1.2.0", "1.1.9", false)
        assertNewer("2.0.0", "1.9.9", false)
    }

    @Test
    fun `components compare numerically not lexicographically`() {
        assertNewer("1.2.10", "1.2.9", false)
        assertNewer("1.9.0", "1.10.0", true)
    }

    @Test
    fun `v prefix on either side is ignored`() {
        assertNewer("1.2.0", "v1.3.0", true)
        assertNewer("V1.2.0", "v1.3.0", true)
        assertNewer("v1.2.0", "1.3.0", true)
        assertNewer("v1.3.0", "1.3.0", false)
    }

    @Test
    fun `two-component tags pad to three`() {
        assertNewer("1.2.1", "1.2", false)
        assertNewer("1.2.1", "1.3", true)
    }

    @Test
    fun `pre-release suffixes rank below the stable release`() {
        assertNewer("2.0.0", "2.0.0-beta", false)
        assertNewer("2.0.0-beta", "2.0.0", true)
    }

    @Test
    fun `garbage versions never claim an update`() {
        assertNewer("1.2.0", "", false)
        assertNewer("1.2.0", "v", false)
        assertNewer("1.2.0", "blob", false)
    }
}
