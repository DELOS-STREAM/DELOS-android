package com.limelight.ps

import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * The PSN sign-in that yields the account id (spec 3.1): Sony's sign-in page in a browser, the authorization code from the
 * redirect, the token, then the user id, which becomes the 8-byte account id. No password ever reaches DELOS. The client id and
 * secret are those of Sony's Remote Play app [LEGAL, owner decision 2026-11-21].
 */
object PsOAuth {
    private const val CLIENT_ID = "ba495a24-818c-472b-b12d-ff231c1b5745"
    private const val CLIENT_SECRET = "mvaiZkRsAsI1IBkY"
    const val REDIRECT = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
    private const val SCOPE = "psn:clientapp referenceDataService:countryConfig.read pushNotification:webSocket.desktop.connect sessionManager:remotePlaySession.system.update"
    private const val BASE = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth"

    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** The sign-in page to open; the browser ends at [REDIRECT] with `?code=`. */
    fun authorizeUrl(): String = "$BASE/authorize?" + listOf(
        "service_entity" to "urn:service-entity:psn", "response_type" to "code", "client_id" to CLIENT_ID, "redirect_uri" to REDIRECT,
        "scope" to SCOPE, "request_locale" to "en_US", "ui" to "pr", "service_logo" to "ps", "layout_type" to "popup",
        "smcid" to "remoteplay", "prompt" to "always", "PlatformPrivacyWs1" to "minimal",
    ).joinToString("&") { (k, v) -> "$k=${enc(v)}" }

    /** The code of a redirect URL, or null when [url] is not the redirect. */
    fun codeFrom(url: String): String? {
        if (!url.startsWith(REDIRECT)) return null
        return Regex("""[?&]code=([^&#]+)""").find(url)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    private val basic = "Basic " + Base64.getEncoder().encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray())

    /** Code → access token → user id → 8-byte account id. Blocking network calls. */
    fun accountId(code: String): ByteArray {
        val form = FormBody.Builder().add("grant_type", "authorization_code").add("code", code).add("scope", SCOPE).add("redirect_uri", REDIRECT).build()
        val token = client.newCall(Request.Builder().url("$BASE/token").header("Authorization", basic).post(form).build()).execute().use { r ->
            val body = r.body.string()
            if (!r.isSuccessful) throw PsException("PSN token: HTTP ${r.code}")
            JsonParser.parseString(body).asJsonObject.get("access_token")?.asString ?: throw PsException("PSN token: no access token")
        }
        val userId = client.newCall(Request.Builder().url("$BASE/token/$token").header("Authorization", basic).build()).execute().use { r ->
            val body = r.body.string()
            if (!r.isSuccessful) throw PsException("PSN user: HTTP ${r.code}")
            JsonParser.parseString(body).asJsonObject.get("user_id")?.asString ?: throw PsException("PSN user: no user id")
        }
        return PsAccountId.fromUserId(userId)
    }
}
