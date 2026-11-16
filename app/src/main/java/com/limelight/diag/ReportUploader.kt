package com.limelight.diag

import com.google.gson.JsonParser
import com.limelight.BuildConfig
import com.limelight.shell.data.MetadataProxy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/** Why a report could not be uploaded. The app then saves the file to Downloads, as it did before the service existed. */
enum class UploadFailure {
    /** No connection to the service (offline, DNS, refused, TLS). */
    OFFLINE,
    /** The service did not answer in time. */
    TIMEOUT,
    /** Too many uploads from this address (HTTP 429). */
    RATE_LIMITED,
    /** The service stopped accepting reports for today (HTTP 503 `daily_limit`). */
    DAILY_LIMIT,
    /** The report is larger than the service accepts (HTTP 413). */
    TOO_LARGE,
    /** The service failed (5xx other than the daily limit). */
    SERVICE_ERROR,
    /** The service refused the request (any other status). */
    REJECTED,
    /** The service said 201 with an answer that is not a link of this service. */
    MALFORMED,
}

sealed interface ReportUpload {
    /** [link] is the address of the stored report on the service, [expires] its expiry (ISO 8601), [rescrubbed] what the service's second scrub still changed. */
    data class Uploaded(val link: String, val expires: String, val rescrubbed: Int) : ReportUpload
    data class Failed(val reason: UploadFailure) : ReportUpload
}

/**
 * Posts an already scrubbed report to the Delos report service (`tools/report-worker/`, docs/DIAGNOSTICS.md, "The report
 * service") and returns the link of the stored copy. The service address is the build setting `DELOS_REPORTS_URL`; without
 * one (the default in builds that did not set it) [enabled] is false and nothing here is used.
 *
 * Blocking: callers run it off the main thread. The text is sent gzip-compressed as `text/plain; charset=utf-8`. Redirects are
 * not followed, the address must be HTTPS (loopback addresses are accepted for tests), and the link in the answer is used only
 * when it is `<service address>/r/<22 characters of an id>`. Neither the text nor the link is logged.
 */
class ReportUploader(baseUrl: String = BuildConfig.DELOS_REPORTS_URL, http: OkHttpClient = DEFAULT_CLIENT) {
    private val endpoint: HttpUrl? = MetadataProxy.url(baseUrl, "v1/reports")
    private val http = http.newBuilder().followRedirects(false).followSslRedirects(false).build()

    val enabled: Boolean get() = endpoint != null

    fun upload(text: String): ReportUpload {
        val target = endpoint ?: return ReportUpload.Failed(UploadFailure.REJECTED)
        val raw = text.toByteArray(Charsets.UTF_8)
        if (raw.size > MAX_BYTES) return failed(UploadFailure.TOO_LARGE)
        val body = gzip(raw).toRequestBody(TEXT_UTF8.toMediaType())
        val request = Request.Builder().url(target).header("Content-Encoding", "gzip").post(body).build()
        return try {
            http.newCall(request).execute().use { response -> answer(target, response.code, response.peekBody(MAX_ANSWER_BYTES).string()) }
        } catch (_: InterruptedIOException) {
            failed(UploadFailure.TIMEOUT)
        } catch (_: IOException) {
            failed(UploadFailure.OFFLINE)
        }
    }

    private fun answer(endpoint: HttpUrl, status: Int, body: String): ReportUpload = when {
        status == 201 -> parse(endpoint, body)?.also { DiagLog.i(TAG, "Log uploaded (the service's second scrub changed ${it.rescrubbed})") }
            ?: failed(UploadFailure.MALFORMED)
        status == 429 -> failed(UploadFailure.RATE_LIMITED)
        status == 413 -> failed(UploadFailure.TOO_LARGE)
        status == 503 && errorCode(body) == "daily_limit" -> failed(UploadFailure.DAILY_LIMIT)
        status >= 500 -> failed(UploadFailure.SERVICE_ERROR)
        else -> failed(UploadFailure.REJECTED)
    }

    private fun failed(reason: UploadFailure): ReportUpload {
        DiagLog.w(TAG, "Log upload failed: $reason")
        return ReportUpload.Failed(reason)
    }

    companion object {
        private const val TAG = "Report"
        private const val TEXT_UTF8 = "text/plain; charset=utf-8"
        private const val MAX_ANSWER_BYTES = 4096L

        /** What the service accepts (`MAX_BYTES` of `tools/report-worker/src/handler.js`): 2 MiB of text. */
        const val MAX_BYTES = 2 * 1024 * 1024

        /** How long the service keeps a report (`TTL_SECONDS` of the Worker), for the texts that say so. */
        const val KEPT_DAYS = 30

        private val ID = Regex("[A-Za-z0-9_-]{22}")

        private val DEFAULT_CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        /** The uploader of this build: its address is `BuildConfig.DELOS_REPORTS_URL`. */
        val shared: ReportUploader by lazy { ReportUploader() }

        private fun gzip(bytes: ByteArray): ByteArray {
            val out = ByteArrayOutputStream(bytes.size / 4 + 64)
            GZIPOutputStream(out).use { it.write(bytes) }
            return out.toByteArray()
        }

        private fun errorCode(body: String): String? = runCatching { JsonParser.parseString(body).asJsonObject.get("error").asString }.getOrNull()

        /** The upload an answer of status 201 stands for, or null when the body is not `{id, url, expires[, rescrubbed]}` with a link of [endpoint]'s service. */
        internal fun parse(endpoint: HttpUrl, body: String): ReportUpload.Uploaded? = try {
            val json = JsonParser.parseString(body).asJsonObject
            val id = json.get("id").asString
            val link = json.get("url").asString
            val expected = endpoint.newBuilder().encodedPath("/r/$id").query(null).fragment(null).build().toString()
            if (!ID.matches(id) || link.toHttpUrlOrNull()?.toString() != expected) null
            else ReportUpload.Uploaded(expected, json.get("expires").asString, json.get("rescrubbed")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0)
        } catch (_: RuntimeException) {
            // Not JSON, not an object, a field missing or of another type: the answer is not the service's.
            null
        }

        /**
         * [text] cut to what the service accepts: the header, the device block and the sections stay, the oldest log lines go
         * first and a note says so. Text that fits is returned as it is.
         */
        fun fit(text: String, maxBytes: Int = MAX_BYTES): String {
            if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
            val lines = text.lines()
            val start = lines.indexOfFirst { it.startsWith(DiagnosticReport.LOG_HEADING) }.let { if (it < 0) 0 else it + 1 }
            val head = lines.subList(0, start) + NOTE
            var size = head.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }
            val kept = ArrayDeque<String>()
            for (line in lines.subList(start, lines.size).asReversed()) {
                val bytes = line.toByteArray(Charsets.UTF_8).size + 1
                if (size + bytes > maxBytes) break
                size += bytes
                kept.addFirst(line)
            }
            return (head + kept).joinToString("\n")
        }

        private const val NOTE = "(older log lines were left out: the report service accepts 2 MiB)"
    }
}
