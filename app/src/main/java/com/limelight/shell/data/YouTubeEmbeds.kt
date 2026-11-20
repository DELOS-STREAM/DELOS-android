package com.limelight.shell.data

import androidx.compose.runtime.mutableStateMapOf
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Which YouTube videos the game overview can play in its own player (docs/gui/GAME_INFO.md, "Videos"). Videos play only inside the
 * app, so one whose owner does not allow embedding, or that is gone, is left out of the carousel instead of being sent to a browser.
 * YouTube's oEmbed endpoint tells that before the user gets there ([UNPLAYABLE_STATUS]); the player's own errors add to it
 * ([unplayableError]). The answers are kept for the process; a check that fails (offline, a timeout) keeps the video.
 */
object YouTubeEmbeds {
    private val unplayable = mutableStateMapOf<String, Boolean>()
    private val checked = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    }

    /** HTTP answers of oEmbed that mean the player cannot play the video: 401 embedding disabled, 400 / 404 no such video, 403 private. */
    private val UNPLAYABLE_STATUS = setOf(400, 401, 403, 404)

    /** Whether [videoId] may be offered; read in composition, so the carousel drops a video the moment it is known to be unplayable. */
    fun playable(videoId: String): Boolean = unplayable[videoId] != true

    /** Asks oEmbed once per process about every id of [videoIds] not asked yet; no cookies, nothing stored on disk. */
    fun check(videoIds: List<String>) {
        for (id in videoIds) if (checked.add(id)) scope.launch {
            val status = runCatching {
                http.newCall(Request.Builder().url(oembedUrl(id)).build()).execute().use { it.code }
            }.getOrNull() ?: return@launch
            if (unplayableStatus(status)) withContext(Dispatchers.Main) { unplayable[id] = true }
        }
    }

    /** The player reported [code] for [videoId] (main thread); an error that will not go away removes the video from the carousel. */
    fun playerError(videoId: String, code: Int) {
        if (unplayableError(code)) unplayable[videoId] = true
    }

    internal fun unplayableStatus(status: Int): Boolean = status in UNPLAYABLE_STATUS

    /** IFrame player errors: 100 removed or private, 101 and 150 embedding not allowed by the owner. */
    internal fun unplayableError(code: Int): Boolean = code == 100 || code == 101 || code == 150

    /** The owner does not allow the video in other apps (rather than a network or player failure). */
    fun blockedByOwner(code: Int): Boolean = code == 101 || code == 150

    internal fun oembedUrl(videoId: String): String =
        "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(IgdbImageUrls.youtubeWatch(videoId), "UTF-8")
}
