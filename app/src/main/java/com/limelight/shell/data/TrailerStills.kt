package com.limelight.shell.data

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A still from each Steam trailer, its carousel poster (Steam's own trailer stills are only 293 × 165). The trailer's HLS stream is
 * read, not played: the 1280 px rendition's init section and the one segment at a sixth of its length are joined into a small
 * fragmented MP4 (about 1 MB) and its first frame is decoded; a near-black frame (a fade) tries a third of the length instead. The
 * still is kept as `cache/trailer-stills/<movie id>.jpg`; one trailer is read at a time, and only when an overview shows it.
 */
class TrailerStills(cacheDir: File, private val http: OkHttpClient) {
    private val dir = File(cacheDir, "trailer-stills")
    private val busy = Mutex()

    /** The still of Steam movie [id], when it has been made. */
    fun cached(id: String): File? = File(dir, "$id.jpg").takeIf { it.length() > 0 }

    /** The still of Steam movie [id] from its HLS [stream], made now if needed; null when it cannot be made. Never throws except to cancel. */
    suspend fun still(id: String, stream: String): File? = cached(id) ?: busy.withLock {
        cached(id) ?: runInterruptible(Dispatchers.IO) { runCatching { make(id, stream) }.getOrNull() }
    }

    private fun make(id: String, stream: String): File? {
        val master = stream.toHttpUrlOrNull() ?: return null
        val variant = HlsStill.variant(text(master.toString()) ?: return null) ?: return null
        val playlistUrl = resolve(master, variant) ?: return null
        val playlist = HlsStill.playlist(text(playlistUrl.toString()) ?: return null) ?: return null
        for (fraction in HlsStill.FRACTIONS) {
            val segment = playlist.segmentAt(fraction) ?: continue
            val init = bytes(resolve(playlistUrl, playlist.init) ?: continue) ?: continue
            val chunk = bytes(resolve(playlistUrl, segment) ?: continue) ?: continue
            val frame = decode(init + chunk) ?: continue
            try {
                if (HlsStill.isDark(frame)) continue
                dir.mkdirs()
                val out = File(dir, "$id.jpg")
                val part = File(dir, "$id.part")
                part.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                if (!part.renameTo(out)) return null
                return out
            } finally {
                frame.recycle()
            }
        }
        return null
    }

    /** [relative] against [base], keeping [base]'s query (Steam's access token) when the reference has none. */
    private fun resolve(base: okhttp3.HttpUrl, relative: String) = base.resolve(relative)?.let { url ->
        if (url.query == null && base.query != null) url.newBuilder().encodedQuery(base.encodedQuery).build() else url
    }

    private fun text(url: String): String? = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (response.isSuccessful) response.body.string() else null
    }

    private fun bytes(url: okhttp3.HttpUrl): ByteArray? = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful || response.body.contentLength() > MAX_SEGMENT_BYTES) null else response.body.bytes()
    }

    private fun decode(mp4: ByteArray): Bitmap? {
        val file = File.createTempFile("still", ".mp4", dir.apply { mkdirs() })
        return try {
            file.writeBytes(mp4)
            MediaMetadataRetriever().run {
                try {
                    setDataSource(file.path)
                    getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    release()
                }
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val MAX_SEGMENT_BYTES = 8L * 1024 * 1024
    }
}

/** The pure parts of [TrailerStills]: choosing the rendition and the segment, and telling a dark frame. */
object HlsStill {
    /** Where the still is taken: a sixth of the trailer, else (a dark frame) a third. */
    val FRACTIONS = listOf(1.0 / 6, 1.0 / 3)

    /** Width of the rendition read: enough for the viewer's blurred still and the carousel. */
    private const val WIDTH = 1280

    /** The media playlist of a master playlist's rendition closest to [WIDTH] wide; null for no rendition. */
    fun variant(master: String): String? {
        val lines = master.lines().map { it.trim() }
        val renditions = lines.withIndex().filter { it.value.startsWith("#EXT-X-STREAM-INF") }.mapNotNull { (i, info) ->
            val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@mapNotNull null
            val width = Regex("RESOLUTION=(\\d+)x\\d+").find(info)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            width to uri
        }
        return renditions.minByOrNull { (width, _) -> kotlin.math.abs(width - WIDTH) }?.second
    }

    /** A media playlist: its init section and its segments with their lengths (seconds). */
    data class Playlist(val init: String, val segments: List<Pair<String, Double>>) {
        /** The segment that plays at [fraction] of the whole. */
        fun segmentAt(fraction: Double): String? {
            val total = segments.sumOf { it.second }
            if (total <= 0) return null
            var at = 0.0
            for ((uri, length) in segments) {
                at += length
                if (at > total * fraction) return uri
            }
            return segments.lastOrNull()?.first
        }
    }

    /** Parses a fragmented-MP4 media playlist; null without an init section (`EXT-X-MAP`) or segments. */
    fun playlist(text: String): Playlist? {
        val lines = text.lines().map { it.trim() }
        val init = lines.firstOrNull { it.startsWith("#EXT-X-MAP") }?.let { Regex("URI=\"([^\"]+)\"").find(it)?.groupValues?.get(1) } ?: return null
        val segments = mutableListOf<Pair<String, Double>>()
        var length: Double? = null
        for (line in lines) when {
            line.startsWith("#EXTINF:") -> length = line.removePrefix("#EXTINF:").substringBefore(',').toDoubleOrNull()
            line.isNotEmpty() && !line.startsWith("#") -> { segments += line to (length ?: 0.0); length = null }
        }
        return if (segments.isEmpty()) null else Playlist(init, segments)
    }

    /** A frame darker than about 8 % mean luma (a fade, a black title card). */
    fun isDark(frame: Bitmap): Boolean {
        val small = Bitmap.createScaledBitmap(frame, 32, 18, true)
        var sum = 0.0
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val c = small.getPixel(x, y)
            sum += (.299 * Color.red(c) + .587 * Color.green(c) + .114 * Color.blue(c)) / 255.0
        }
        if (small !== frame) small.recycle()
        return sum / (small.width * small.height) < .08
    }
}
