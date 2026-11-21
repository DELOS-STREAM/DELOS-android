package com.limelight.shell.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailCacheTest {
    private var requests = 0
    private lateinit var prefix: String

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Thumbnails.init(context.cacheDir)
        prefix = "https://preview.invalid/${UUID.randomUUID()}"
    }

    // An application interceptor supplies bytes before DNS or any socket can be used.
    private fun http(bytes: ByteArray, status: Int = 200, fail: Boolean = false) =
        OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            if (fail) throw IOException("Offline")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("Test response").body(bytes.toResponseBody()).build()
        }.build()

    private fun image(name: String, mime: String? = "image/webp", animated: Boolean = true, sameUrl: Boolean = false) =
        SgdbImage(1, "$prefix/$name.webp", if (sameUrl) "$prefix/$name.webp" else "$prefix/$name-preview.webp",
            32, 32, mime = mime, animated = animated)

    private fun webp(width: Int = 32, height: Int = 32, animated: Boolean = true): ByteArray = ByteArray(30).also { b ->
        "RIFF".toByteArray().copyInto(b, 0)
        "WEBP".toByteArray().copyInto(b, 8)
        "VP8X".toByteArray().copyInto(b, 12)
        b[16] = 10
        b[20] = if (animated) 2 else 0
        for ((offset, value) in listOf(24 to width - 1, 27 to height - 1)) {
            repeat(3) { b[offset + it] = (value shr (8 * it)).toByte() }
        }
    }

    private fun png(width: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, 32, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { out ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                out.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    @Test fun thumbnailsAreDecodedDownsampledAndCachedWithoutAnotherRequest() = runBlocking {
        val url = "$prefix/large.png"
        val client = http(png(2080, Color.RED))
        assertNull(Thumbnails.cached(url))
        val loaded = Thumbnails.load(client, url)!!
        assertEquals(520, loaded.width)
        assertSame(loaded, Thumbnails.cached(url))
        assertSame(loaded, Thumbnails.load(client, url))
        assertFalse(Thumbnails.isRemoved(url))
        assertFalse(Thumbnails.progress.containsKey(url))
        assertEquals(1, requests)
    }

    @Test fun takedownPicturesAreMarkedButTransparentLogosAreNot() = runBlocking {
        val removed = "$prefix/removed.png"
        val logo = "$prefix/logo.png"
        assertNotNull(Thumbnails.load(http(png(32, Color.BLACK)), removed))
        assertTrue(Thumbnails.isRemoved(removed))
        assertNotNull(Thumbnails.load(http(png(32, Color.TRANSPARENT)), logo))
        assertFalse(Thumbnails.isRemoved(logo))
    }

    @Test fun badDownloadsAndNonImagesLeaveNoMemoryImageOrProgress() = runBlocking {
        for ((name, client) in listOf(
            "not-found" to http(byteArrayOf(), status = 404),
            "offline" to http(byteArrayOf(), fail = true),
            "invalid" to http("not an image".toByteArray())
        )) {
            val url = "$prefix/$name"
            assertNull(Thumbnails.load(client, url))
            assertNull(Thumbnails.cached(url))
            assertFalse(Thumbnails.progress.containsKey(url))
        }
    }

    @Test fun playablePreviewIsReusedFromDiskUntilItsFileDisappears() = runBlocking {
        val image = image("playable")
        val client = http(webp())
        assertNull(Thumbnails.cachedAnimation(image))
        assertFalse(Thumbnails.hasNoAnimation(image))
        val file = Thumbnails.loadAnimation(client, image)!!
        assertArrayEquals(webp(), file.readBytes())
        assertEquals(file, Thumbnails.cachedAnimation(image))
        assertEquals(file, Thumbnails.loadAnimation(client, image))
        assertEquals(1, requests)
        assertTrue(file.delete())
        assertNull(Thumbnails.cachedAnimation(image))
        assertNotNull(Thumbnails.loadAnimation(client, image))
        assertEquals(2, requests)
    }

    @Test fun unplayablePreviewFallsBackToTheFullWebpAndRemembersBothResults() = runBlocking {
        val image = image("fallback")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val bytes = webp(animated = !chain.request().url.encodedPath.contains("preview"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(bytes.toResponseBody()).build()
        }.build()
        val file = Thumbnails.loadAnimation(client, image)!!
        assertArrayEquals(webp(), file.readBytes())
        assertFalse(Thumbnails.hasNoAnimation(image))
        assertEquals(file, Thumbnails.cachedAnimation(image))
        assertEquals(file, Thumbnails.loadAnimation(client, image))
        assertEquals(2, requests)
    }

    @Test fun stillUnsupportedAndOversizedAnimationsKeepTheirFirstFrame() = runBlocking {
        val still = image("still", animated = false)
        assertNull(Thumbnails.loadAnimation(http(webp()), still))
        assertEquals(0, requests)
        for (item in listOf(image("gif", mime = "image/gif"), image("same", sameUrl = true), image("both"))) {
            assertFalse(Thumbnails.hasNoAnimation(item))
            assertNull(Thumbnails.loadAnimation(http(webp(animated = false)), item))
            assertTrue(Thumbnails.hasNoAnimation(item))
            assertNull(Thumbnails.cachedAnimation(item))
        }
        val huge = image("huge", sameUrl = true)
        assertNull(Thumbnails.loadAnimation(http(webp(10_001, 10_000)), huge))
        assertTrue(Thumbnails.hasNoAnimation(huge))
    }

    @Test fun failedAnimationDownloadCanBeRetriedRatherThanMarkedUnplayable() = runBlocking {
        val image = image("retry", sameUrl = true)
        assertNull(Thumbnails.loadAnimation(http(byteArrayOf(), status = 500), image))
        assertFalse(Thumbnails.hasNoAnimation(image))
        assertNotNull(Thumbnails.loadAnimation(http(webp()), image))
    }
}
