package com.toadgallery.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.toadgallery.app.data.DownloadProgress
import com.toadgallery.app.data.DownloadRequest
import com.toadgallery.app.data.SystemUpdateDownloader
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Pins the DownloadManager-backed updater contract: the enqueue request fields, the
 * delete-before-enqueue retry rule, and the [DownloadProgress] math the progress bar
 * renders. Ported from toadFILES' UpdateDownloaderTest (Robolectric ShadowDownloadManager).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateDownloaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val downloader = SystemUpdateDownloader()

    private fun request() = DownloadRequest(
        url = "https://github.com/dukeoftoad/toadGALLERY/releases/download/v2.0.0/toadGALLERY-2.0.0.apk",
        fileName = "toadGALLERY-2.0.0.apk",
        title = "toadGALLERY v2.0.0",
        description = "Self-update download",
    )

    private fun dm(): android.app.DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager

    @Test
    fun `enqueue lands in DownloadManager with the apk mime type`() {
        val id = downloader.enqueue(context, request())
        assertNotNull(id)
        val req = Shadows.shadowOf(Shadows.shadowOf(dm()).getRequest(id))
        assertEquals("toadGALLERY v2.0.0", req.title)
        assertEquals("Self-update download", req.description)
        assertEquals("application/vnd.android.package-archive", req.mimeType)
        // User-initiated: metered networks are allowed on purpose.
        assertTrue(req.allowedOverMetered)
        assertEquals(
            android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            req.notificationVisibility,
        )
        assertEquals(request().url, req.uri.toString())
        assertTrue(req.destination.toString().endsWith("toadGALLERY-2.0.0.apk"))
    }

    @Test
    fun `awaitDownload surfaces the uri when the system job reports success`() = kotlinx.coroutines.test.runTest {
        val id = downloader.enqueue(context, request())
        Shadows.shadowOf(Shadows.shadowOf(dm()).getRequest(id))
            .setStatus(android.app.DownloadManager.STATUS_SUCCESSFUL)
        val uri = downloader.awaitDownload(context, id)
        assertNotNull("a successful download must hand its uri to the installer", uri)
    }

    @Test
    fun `awaitDownload returns null when the system job reports failure`() = kotlinx.coroutines.test.runTest {
        val id = downloader.enqueue(context, request())
        Shadows.shadowOf(Shadows.shadowOf(dm()).getRequest(id))
            .setStatus(android.app.DownloadManager.STATUS_FAILED)
        assertNull("a failed download must NOT reach the installer", downloader.awaitDownload(context, id))
    }

    @Test
    fun `enqueue deletes a stale destination so retries really re-download`() {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)!!
        val stale = File(dir, "toadGALLERY-2.0.0.apk")
        stale.writeBytes(ByteArray(128))
        assertTrue(stale.exists())
        val id = downloader.enqueue(context, request())
        assertTrue(id != 0L)
        assertTrue(!stale.exists())
    }

    @Test
    fun `enqueue rejects a blank url with an honest message`() {
        try {
            downloader.enqueue(
                context,
                DownloadRequest(url = "", fileName = "x.apk", title = "t", description = "d"),
            )
            fail("expected IOException for blank URL")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("no downloadable APK"))
        }
    }

    @Test
    fun `download progress fraction is honest about unknown sizes`() {
        assertEquals(0.5f, DownloadProgress(500, 1000).fraction)
        assertEquals(1.0f, DownloadProgress(1200, 1000).fraction) // clamped
        assertEquals(0.0f, DownloadProgress(0, 1000).fraction)
        assertNull(DownloadProgress(500, -1).fraction) // unknown total → indeterminate bar
        assertNull(DownloadProgress(500, 0).fraction)
        assertNull(DownloadProgress(-5, 1000).fraction)
        assertEquals(0.0f, DownloadProgress.UNKNOWN.bytesSoFar)
        assertEquals(-1L, DownloadProgress.UNKNOWN.totalBytes)
    }
}
