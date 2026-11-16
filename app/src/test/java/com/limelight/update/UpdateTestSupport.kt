package com.limelight.update

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import java.security.MessageDigest

/** A fake web for the updater's tests: loopback-free, an application interceptor answers every request and records it. */
class FakeWeb(var handler: (Request) -> Response) {
    val requests = mutableListOf<Request>()

    fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request()
        handler(chain.request())
    }.build()

    companion object {
        fun reply(request: Request, code: Int, body: String = "", vararg headers: Pair<String, String>): Response =
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fake")
                .body(body.toResponseBody("application/json".toMediaType()))
                .apply { headers.forEach { (k, v) -> header(k, v) } }.build()

        fun replyBytes(request: Request, bytes: ByteArray): Response =
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fake")
                .body(bytes.toResponseBody("application/octet-stream".toMediaType())).build()

        /** A body of [size] bytes that does not announce its length, like a chunked response. */
        fun replyUnknownLength(request: Request, size: Long): Response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fake")
            .body(object : ResponseBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = -1L
                override fun source(): BufferedSource = Buffer().apply { write(ByteArray(size.toInt())) }
            }).build()
    }
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** A release answer of the shape `GET /repos/{owner}/{repo}/releases/latest` returns, cut to the fields the updater reads plus a few it ignores. */
fun releaseJson(
    tag: String, body: String = "## Added\\n- Something new", draft: Boolean = false, prerelease: Boolean = false,
    assets: List<Triple<String, String, Long>> = listOf(
        Triple("delos-${tag.removePrefix("v")}-arm64-v8a.apk", "https://github.com/Meitoncz/Delos/releases/download/$tag/delos-${tag.removePrefix("v")}-arm64-v8a.apk", 52_428_800L),
        Triple("delos-${tag.removePrefix("v")}-armeabi-v7a.apk", "https://github.com/Meitoncz/Delos/releases/download/$tag/delos-${tag.removePrefix("v")}-armeabi-v7a.apk", 41_943_040L),
        Triple("SHA256SUMS.txt", "https://github.com/Meitoncz/Delos/releases/download/$tag/SHA256SUMS.txt", 220L),
    ),
): String = """
{
  "url": "https://api.github.com/repos/Meitoncz/Delos/releases/1",
  "html_url": "https://github.com/Meitoncz/Delos/releases/tag/$tag",
  "id": 1,
  "author": {"login": "Meitoncz", "id": 7},
  "tag_name": "$tag",
  "target_commitish": "main",
  "name": "Delos ${tag.removePrefix("v")}",
  "draft": $draft,
  "prerelease": $prerelease,
  "created_at": "2026-12-01T10:00:00Z",
  "published_at": "2026-12-01T10:05:00Z",
  "assets": [${assets.joinToString(",") { (name, url, size) -> """{"id": 9, "name": "$name", "content_type": "application/octet-stream", "state": "uploaded", "size": $size, "download_count": 3, "browser_download_url": "$url"}""" }}],
  "tarball_url": "https://api.github.com/repos/Meitoncz/Delos/tarball/$tag",
  "body": "$body"
}
""".trimIndent()
