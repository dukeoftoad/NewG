package com.toadgallery.app

import com.toadgallery.app.data.GithubReleaseChecker
import com.toadgallery.app.data.UpdateCheckRateLimitException
import com.toadgallery.app.data.UpdateCheckRetryableException
import java.io.IOException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Parsing of the real GitHub releases/latest payload shape with org.json (provided by
 * Robolectric on the JVM). Runs against a realistic raw string, no network involved.
 * Ported from toadFILES' UpdateCheckerParsingTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateCheckerParsingTest {

    private val payload = """
        {
          "html_url": "https://github.com/dukeoftoad/toadGALLERY/releases/tag/v2.0.0",
          "tag_name": "v2.0.0",
          "name": "toadGALLERY 2.0.0",
          "body": "### New\r\n- In-app updates\r\n- Icon picker\r\n",
          "assets": [
            {
              "name": "toadGALLERY-2.0.0-sources.zip",
              "browser_download_url": "https://github.com/dukeoftoad/toadGALLERY/releases/download/v2.0.0/sources.zip",
              "size": 204800
            },
            {
              "name": "toadGALLERY-2.0.0.apk",
              "browser_download_url": "https://github.com/dukeoftoad/toadGALLERY/releases/download/v2.0.0/toadGALLERY-2.0.0.apk",
              "size": 7340032
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parseRelease picks the apk asset and the release fields`() {
        val release = GithubReleaseChecker.parseRelease(payload)
        assertEquals("v2.0.0", release.tagName)
        assertEquals(
            "https://github.com/dukeoftoad/toadGALLERY/releases/tag/v2.0.0",
            release.htmlUrl,
        )
        // The zip asset must be skipped in favor of the .apk one.
        assertEquals("toadGALLERY-2.0.0.apk", release.apkName)
        assertEquals(
            "https://github.com/dukeoftoad/toadGALLERY/releases/download/v2.0.0/toadGALLERY-2.0.0.apk",
            release.apkUrl,
        )
        assertEquals(7340032L, release.apkSizeBytes)
        assertTrue(release.notes!!.startsWith("### New"))
        assertTrue(!release.notes!!.contains("\r"))
    }

    @Test
    fun `parseRelease tolerates a release with no apk asset`() {
        val noApk = """
            {"tag_name": "v9.9.9", "html_url": "", "body": "", "assets": []}
        """.trimIndent()
        val release = GithubReleaseChecker.parseRelease(noApk)
        assertEquals("v9.9.9", release.tagName)
        assertNull(release.apkName)
        assertNull(release.apkUrl)
        // html_url falls back to the releases page for "View on GitHub".
        assertEquals(
            "https://github.com/dukeoftoad/toadGALLERY/releases",
            release.htmlUrl,
        )
    }

    @Test
    fun `parseRelease rejects a payload without a tag`() {
        try {
            GithubReleaseChecker.parseRelease("""{"html_url": "https://example.com"}""")
            fail("expected JSONException")
        } catch (expected: JSONException) {
            assertEquals("Release payload has no tag_name", expected.message)
        }
    }

    @Test
    fun `exceptionFor classifies rate limits as fatal and 5xx as retryable`() {
        assertTrue(GithubReleaseChecker.exceptionFor(403) is UpdateCheckRateLimitException)
        val e = GithubReleaseChecker.exceptionFor(502)
        assertTrue(e is UpdateCheckRetryableException)
        assertTrue((e as UpdateCheckRetryableException).message.contains("502"))
        val other = GithubReleaseChecker.exceptionFor(404)
        assertTrue(other is IOException && other !is UpdateCheckRetryableException)
    }

    @Test
    fun `runWithRetry retries transient failures only`() {
        var attempts = 0
        val ok = GithubReleaseChecker.runWithRetry(2) { attempt ->
            attempts = attempt
            if (attempt == 1) throw UpdateCheckRetryableException(503)
            "recovered"
        }
        assertEquals("recovered", ok)
        assertEquals(2, attempts)

        try {
            GithubReleaseChecker.runWithRetry(3) { throw UpdateCheckRateLimitException() }
            fail("rate limit must not be retried")
        } catch (expected: UpdateCheckRateLimitException) {
            // surfaced immediately
        }
    }
}
