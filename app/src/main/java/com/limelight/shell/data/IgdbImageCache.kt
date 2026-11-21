package com.limelight.shell.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The game overview's IGDB screenshots, artworks and covers, Steam's screenshots and its YouTube thumbnails ([IgdbImageUrls]). The files sit in an HTTP
 * disk cache of [DISK_BYTES] (cache/igdb-images, least recently used dropped first) keyed by URL, which names the image id and
 * size; an image of an id and size never changes, so every answer is kept as long as the disk allows. Decoded bitmaps are kept
 * in memory per URL and width.
 */
class IgdbImageCache(context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cache(Cache(File(context.applicationContext.cacheDir, "igdb-images"), DISK_BYTES))
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (!response.isSuccessful) response
            else response.newBuilder().removeHeader("Pragma").removeHeader("Expires").removeHeader("Vary") // l10n-ignore
                .header("Cache-Control", "public, max-age=$MAX_AGE_SECONDS").build() // l10n-ignore
        }
        .build()
    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_KIB) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }
    private val network = Semaphore(4)
    private val prefetching = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The Steam trailers' own stills ([TrailerStills]); their files load here as `file:` URLs. */
    val stills = TrailerStills(context.applicationContext.cacheDir, http)

    /** [url] decoded for [maxWidthPx] when it is already in memory; for the first frame of a screen. */
    fun cached(url: String, maxWidthPx: Int): ImageBitmap? = memory.get(key(url, maxWidthPx))

    /**
     * [url] decoded no wider than needed for [maxWidthPx] (halving while it stays at least that wide), from memory, disk or the
     * network; null when it cannot be had or decoded. Never throws except to cancel.
     */
    suspend fun load(url: String, maxWidthPx: Int): ImageBitmap? {
        val key = key(url, maxWidthPx)
        memory.get(key)?.let { return it }
        return try {
            val bytes = if (url.startsWith(FILE)) runInterruptible(Dispatchers.IO) { File(url.removePrefix(FILE)).takeIf { it.isFile }?.readBytes() } ?: return null
            else network.withPermit { runInterruptible(Dispatchers.IO) { download(url) } } ?: return null
            runInterruptible(Dispatchers.Default) { decode(bytes, maxWidthPx) }?.also { memory.put(key, it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    /** Puts [urls] in the disk cache in the background, without decoding them; failures are ignored. */
    fun prefetch(urls: List<String>) {
        for (url in urls) prefetching.launch {
            network.withPermit {
                runCatching {
                    runInterruptible {
                        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                            // The cache stores a network answer only once its body has been read through
                            if (response.isSuccessful && response.networkResponse != null) response.body.source().readAll(okio.blackholeSink())
                        }
                    }
                }
            }
        }
    }

    private fun download(url: String): ByteArray? =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body
            if (body.contentLength() > MAX_FILE_BYTES) return null
            body.bytes().takeIf { it.isNotEmpty() && it.size <= MAX_FILE_BYTES }
        }

    private fun decode(bytes: ByteArray, maxWidthPx: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxWidthPx > 0 && bounds.outWidth / (sample * 2) >= maxWidthPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.also { it.prepareToDraw() }?.asImageBitmap()
    }

    private fun key(url: String, maxWidthPx: Int) = "$maxWidthPx $url"

    private companion object {
        const val FILE = "file:"
        const val DISK_BYTES = 150L * 1024 * 1024
        /** About ten 1280 x 720 screenshots. */
        const val MEMORY_KIB = 32 * 1024
        const val MAX_FILE_BYTES = 8L * 1024 * 1024
        val MAX_AGE_SECONDS = TimeUnit.DAYS.toSeconds(365)
    }
}

/** [url] decoded for [maxWidthPx]: at once when it is in memory, else once loaded; null while loading, on failure and for no [url]. */
@Composable
fun rememberIgdbImage(url: String?, maxWidthPx: Int): State<ImageBitmap?> {
    val images = LocalIgdbImages.current
    val state = remember(images, url, maxWidthPx) { mutableStateOf(url?.let { images.cached(it, maxWidthPx) }) }
    LaunchedEffect(state) { if (state.value == null && url != null) state.value = images.load(url, maxWidthPx) }
    return state
}

/**
 * A Steam trailer's own still ([TrailerStills]) for movie [id] and its HLS [stream], decoded for [maxWidthPx]: null until it is made
 * (the carousel shows the trailer's poster meanwhile), and for anything but a Steam HLS trailer.
 */
@Composable
fun rememberTrailerStill(id: String, stream: String?, maxWidthPx: Int): State<ImageBitmap?> {
    val images = LocalIgdbImages.current
    val state = remember(images, id, stream, maxWidthPx) {
        mutableStateOf(images.stills.cached(id)?.let { images.cached("file:" + it.path, maxWidthPx) })
    }
    LaunchedEffect(state) {
        if (state.value != null || stream == null || !stream.contains(".m3u8")) return@LaunchedEffect
        val file = images.stills.still(id, stream) ?: return@LaunchedEffect
        state.value = images.load("file:" + file.path, maxWidthPx)
    }
    return state
}

val LocalIgdbImages = staticCompositionLocalOf<IgdbImageCache> { error("IgdbImageCache not provided") }
