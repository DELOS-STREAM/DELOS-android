package com.limelight.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Why a request of the updater failed; the screen maps it to a text. */
enum class UpdateErrorKind { NETWORK, RATE_LIMITED, HTTP_STATUS, NOT_HTTPS, TOO_LARGE, BAD_ANSWER, STORAGE }

class UpdateException(val kind: UpdateErrorKind, message: String, cause: Throwable? = null) : IOException(message, cause)

/** The answer to a conditional request. */
sealed interface Latest {
    object NotModified : Latest
    class Body(val text: String, val etag: String?) : Latest
}

/**
 * The updater's HTTP client (docs/UPDATES.md "Network"): HTTPS only, redirects followed by hand and only to HTTPS (rule S5 of
 * docs/ENGINEERING_RULES.md), every body limited in size. Blocking: call it off the main thread. [allowCleartext] is for the
 * debug build's local test server and nothing else.
 */
class UpdateHttp(client: OkHttpClient, private val userAgent: String, private val allowCleartext: Boolean = false) {
    private val http = client.newBuilder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** `GET url` with `If-None-Match: etag` when there is one. A status other than 200 and 304 throws. */
    @Throws(IOException::class)
    fun latest(url: String, etag: String?): Latest = open(url, mapOf("Accept" to "application/vnd.github+json") + listOfNotNull(etag?.let { "If-None-Match" to it })).use { response ->
        when (response.code) {
            304 -> Latest.NotModified
            200 -> Latest.Body(readText(response, MAX_JSON_BYTES), response.header("ETag"))
            else -> throw failure(response)
        }
    }

    /** The text file at [url] (the checksum list), at most [maxBytes] long. */
    @Throws(IOException::class)
    fun text(url: String, maxBytes: Long = MAX_TEXT_BYTES): String = open(url, emptyMap()).use { response ->
        if (response.code != 200) throw failure(response)
        readText(response, maxBytes)
    }

    /**
     * Downloads [url] to [target], at most [maxBytes], and reports `(received, total)` (total is -1 when unknown) as it goes. A failure,
     * a too large body and a cancelled coroutine leave no file behind.
     */
    @Throws(IOException::class)
    suspend fun download(url: String, target: File, maxBytes: Long, progress: (Long, Long) -> Unit) = coroutineScope {
        target.parentFile?.mkdirs()
        val call = java.util.concurrent.atomic.AtomicReference<okhttp3.Call?>()
        // The read below blocks its thread, so a second one watches for the cancellation and cancels the call
        val watcher = launch(Dispatchers.Default) { try { awaitCancellation() } finally { call.get()?.cancel() } }
        try {
            open(url, emptyMap()) { call.set(it) }.use { response ->
                if (response.code != 200) throw failure(response)
                val body = response.body
                val total = body.contentLength()
                if (total > maxBytes) throw UpdateException(UpdateErrorKind.TOO_LARGE, "the download is $total bytes, more than $maxBytes")
                try {
                    var received = 0L
                    target.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        val input = body.byteStream()
                        var last = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = try { input.read(buffer) } catch (e: IOException) { throw UpdateException(UpdateErrorKind.NETWORK, "the download broke off", e) }
                            if (read < 0) break
                            received += read
                            if (received > maxBytes) throw UpdateException(UpdateErrorKind.TOO_LARGE, "the download passed $maxBytes bytes")
                            try { out.write(buffer, 0, read) } catch (e: IOException) { throw UpdateException(UpdateErrorKind.STORAGE, "cannot write the download", e) }
                            if (received - last >= PROGRESS_STEP || received == total) { last = received; progress(received, total) }
                        }
                    }
                    progress(received, total)
                } catch (e: Throwable) {
                    target.delete()
                    throw e
                }
            }
        } catch (e: IOException) {
            // A cancelled call surfaces as an IOException; the coroutine's own cancellation wins
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            watcher.cancel()
        }
    }

    /** Follows up to [MAX_REDIRECTS] redirects; the first request and every redirect target must be HTTPS. The caller closes the response. */
    private fun open(url: String, headers: Map<String, String>, onCall: (okhttp3.Call) -> Unit = {}): Response {
        var next = url
        repeat(MAX_REDIRECTS + 1) {
            val parsed = next.toHttpUrlOrNull()
            if (parsed == null || (!parsed.isHttps && !allowCleartext)) {
                throw UpdateException(UpdateErrorKind.NOT_HTTPS, "refusing to fetch a URL that is not HTTPS: ${next.substringBefore('?').take(80)}")
            }
            val request = Request.Builder().url(parsed).header("User-Agent", userAgent).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
            val call = http.newCall(request)
            onCall(call)
            val response = try {
                call.execute()
            } catch (e: IOException) {
                throw UpdateException(UpdateErrorKind.NETWORK, "request failed: ${e.javaClass.simpleName}", e)
            }
            if (response.code !in REDIRECTS) return response
            val location = response.header("Location")
            response.close()
            next = location?.let { parsed.resolve(it)?.toString() } ?: throw UpdateException(UpdateErrorKind.BAD_ANSWER, "a redirect without a usable Location")
        }
        throw UpdateException(UpdateErrorKind.BAD_ANSWER, "more than $MAX_REDIRECTS redirects")
    }

    private fun readText(response: Response, maxBytes: Long): String {
        val body = response.body
        if (body.contentLength() > maxBytes) throw UpdateException(UpdateErrorKind.TOO_LARGE, "the answer is larger than $maxBytes bytes")
        val source = body.source()
        val buffer = okio.Buffer()
        while (buffer.size <= maxBytes) { if (source.read(buffer, 8192) < 0) break }
        if (buffer.size > maxBytes) throw UpdateException(UpdateErrorKind.TOO_LARGE, "the answer is larger than $maxBytes bytes")
        return buffer.readUtf8()
    }

    private fun failure(response: Response): UpdateException {
        val limited = response.code == 429 || (response.code == 403 && response.header("X-RateLimit-Remaining") == "0")
        return if (limited) UpdateException(UpdateErrorKind.RATE_LIMITED, "GitHub rate limit reached (HTTP ${response.code})")
        else UpdateException(UpdateErrorKind.HTTP_STATUS, "HTTP ${response.code}")
    }

    companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_JSON_BYTES = 1L * 1024 * 1024
        const val MAX_TEXT_BYTES = 64L * 1024

        /** The largest APK the updater downloads. */
        const val MAX_APK_BYTES = 250L * 1024 * 1024
        private const val PROGRESS_STEP = 256L * 1024
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
