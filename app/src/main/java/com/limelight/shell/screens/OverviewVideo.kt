package com.limelight.shell.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.limelight.R
import com.limelight.shell.data.YouTubeEmbeds
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.Symbol
import com.limelight.shell.ui.Symbols
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.px
import com.limelight.shell.ui.tpx
import java.util.Locale
import org.json.JSONObject


/** Without any word from the player this long after it began to load, the video counts as failed. */
internal const val LOAD_TIMEOUT_MS = 15_000L

/** A YouTube video id as IGDB stores it; anything else is never put into the player's page. */
private val VIDEO_ID = Regex("[A-Za-z0-9_-]{6,20}")

/** [seconds] as `m:ss`, or `h:mm:ss` from an hour on. */
internal fun videoTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = total / 60 % 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/**
 * The page of the in-app player: YouTube's IFrame player API in privacy-enhanced mode (youtube-nocookie.com), inline, without YouTube's
 * own controls (the viewer's dock drives it), related videos only from the same channel, no annotations. [origin] is the app's identity
 * (`https://<application id>`), the page's base URL and so the Referer YouTube requires of embedding apps. The page reports to
 * `DelosPlayer.post`: `ready` (with `glass`: whether it can blur behind an element), `status` every 500 ms and on every change (with
 * the loaded fraction and the volume), `error` with the player's code (-1: the API did not load). Null for an id that is no YouTube id.
 *
 * The dock's glass over the video lives in this page: `glass(on, rects, blur)` places one element per rectangle (`[x, y, w, h, corner]`
 * in device pixels) with a real backdrop blur of `blur` device pixels over the player and fades them in (420 ms) or out (240 ms) by their own opacity. A blur in
 * the app's views cannot sample the web view's video.
 */
internal fun youTubePlayerHtml(videoId: String, origin: String): String? {
    if (!VIDEO_ID.matches(videoId)) return null
    return """
        <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;inset:0;width:100%;height:100%}
        .g{position:absolute;pointer-events:none;opacity:0;background:rgba(10,16,32,.2);transition:opacity .24s cubic-bezier(.5,0,.75,0)}
        .g.on{opacity:1;transition:opacity .42s cubic-bezier(.16,1,.3,1)}</style></head>
        <body><div id="p"></div><script>
        var player,ready=false,panes=[];
        function post(o){DelosPlayer.post(JSON.stringify(o));}
        function status(){if(!ready||!player.getPlayerState)return;post({type:'status',state:player.getPlayerState(),time:player.getCurrentTime()||0,duration:player.getDuration()||0,muted:player.isMuted(),loaded:player.getVideoLoadedFraction()||0,volume:player.getVolume()});}
        function setPlaybackAllowed(allowed,wanted){if(!ready)return;if(allowed&&wanted)player.playVideo();else player.pauseVideo();}
        function glass(on,rects,blur){var d=window.devicePixelRatio||1,f='blur('+blur/d+'px) saturate(1.8) brightness(.86)';
          while(panes.length<rects.length){var e=document.createElement('div');e.className='g';document.body.appendChild(e);panes.push(e);}
          panes.forEach(function(e,i){var r=rects[i];if(r){e.style.left=r[0]/d+'px';e.style.top=r[1]/d+'px';e.style.width=r[2]/d+'px';e.style.height=r[3]/d+'px';e.style.borderRadius=r[4]/d+'px';e.style.backdropFilter=f;e.style.webkitBackdropFilter=f;}
            e.classList.toggle('on',on&&!!r);});}
        function onYouTubeIframeAPIReady(){
          player=new YT.Player('p',{host:'https://www.youtube-nocookie.com',videoId:'$videoId',
            playerVars:{playsinline:1,rel:0,controls:0,fs:0,disablekb:1,iv_load_policy:3,autoplay:0,enablejsapi:1,origin:'$origin'},
            events:{onReady:function(){ready=true;post({type:'ready',glass:CSS.supports('backdrop-filter','blur(1px)')||CSS.supports('-webkit-backdrop-filter','blur(1px)')});status();setInterval(status,500);},
                    onStateChange:function(){status();},
                    onError:function(e){post({type:'error',code:e.data});}}});
        }
        </script><script src="https://www.youtube.com/iframe_api" onerror="post({type:'error',code:-1})"></script></body></html>
    """.trimIndent()
}

/**
 * One video of the viewer in the in-app player: what the player reports ([receive]) and the commands of the controls and the keys,
 * sent to it through the IFrame API. [started]: it has played, so the web view shows instead of the thumbnail. [error] is the player's
 * error code (-1: it did not load); the viewer then shows why instead of the player. [loaded] is the buffered fraction, [volume] 0–100.
 */
@Stable
internal class OverviewVideoPlayer(val videoId: String) {
    var started by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var muted by mutableStateOf(false)
        private set
    var time by mutableFloatStateOf(0f)
        private set
    var duration by mutableFloatStateOf(0f)
        private set
    var loaded by mutableFloatStateOf(0f)
        private set
    var volume by mutableIntStateOf(100)
        private set
    var error by mutableStateOf<Int?>(null)
        private set

    /** The page can blur behind the dock's glass (CSS `backdrop-filter`); until it says so, and if it cannot, the dock is tinted darker over the video. */
    var glassSupported by mutableStateOf(false)
        private set

    /** The page has reported at least once. */
    var heard = false
        private set
    var ended by mutableStateOf(false)
        private set
    internal var web: WebView? = null
    private var active = false
    private var wantsPlayback = true
    private var released = false

    /** When the slider last set the volume: a report older than the change does not move the slider back. */
    private var volumeSetAt = 0L
    private var glassScript: String? = null

    /** Backgrounding revokes autoplay too, including an iframe whose onReady arrives later. */
    fun setActive(value: Boolean) {
        active = value && !released
        if (!active) {
            wantsPlayback = false
            playing = false
        }
        applyPlayback()
    }

    private fun applyPlayback() {
        web?.evaluateJavascript("if(window.setPlaybackAllowed){setPlaybackAllowed($active,$wantsPlayback)}", null)
    }

    fun release() {
        setActive(false)
        released = true
        web = null
    }

    fun togglePlay() {
        if (!active || released || error != null) return
        wantsPlayback = !playing
        if (wantsPlayback && ended) run("player.seekTo(0,true)")
        playing = wantsPlayback
        applyPlayback()
    }

    fun pause() {
        wantsPlayback = false
        playing = false
        applyPlayback()
    }

    fun seekBy(seconds: Int) {
        time = (time + seconds).coerceIn(0f, if (duration > 0f) duration else Float.MAX_VALUE)
        run("player.seekTo(Math.max(0,player.getCurrentTime()+($seconds)),true)")
    }

    /** Jumps to [fraction] of the video (a tap on the progress track). */
    fun seekTo(fraction: Float) {
        if (duration <= 0f) return
        time = fraction.coerceIn(0f, 1f) * duration
        run("player.seekTo($time,true)")
    }

    fun toggleMute() {
        muted = !muted
        run(if (muted) "player.mute()" else "player.unMute()")
    }

    /** Sets the volume (0–100); any volume above 0 turns the sound on. */
    fun changeVolume(value: Int) {
        val v = value.coerceIn(0, 100)
        volume = v
        volumeSetAt = SystemClock.uptimeMillis()
        if (v > 0 && muted) muted = false
        run("player.setVolume($v);" + if (v > 0) "player.unMute()" else "")
    }

    /**
     * The dock's glass in the page: [rects] (`x, y, w, h, corner` in the web view's pixels) shown when [on]. Sent again once the page is
     * ready, so a call made while it loads is not lost.
     */
    fun glass(on: Boolean, rects: List<FloatArray>, blur: Float) {
        val list = rects.joinToString(",", "[", "]") { r -> r.joinToString(",", "[", "]") { it.toInt().toString() } }
        val script = "if(window.glass){glass($on,$list,${blur.toInt()})}"
        if (script == glassScript) return
        glassScript = script
        web?.evaluateJavascript(script, null)
    }

    private fun run(js: String) {
        web?.evaluateJavascript("if(window.player&&player.getPlayerState){$js}", null)
    }

    /** The player failed with [code]: no more commands; an error that stays removes the video from the carousel. */
    fun fail(code: Int) {
        if (error != null || released) return
        pause()
        error = code
        YouTubeEmbeds.playerError(videoId, code)
    }

    /** A report of the page (main thread). */
    fun receive(message: String) {
        if (released || error != null) return
        val json = runCatching { JSONObject(message) }.getOrNull() ?: return
        heard = true
        when (json.optString("type")) {
            "ready" -> {
                glassSupported = json.optBoolean("glass", false)
                glassScript?.let { web?.evaluateJavascript(it, null) }
                applyPlayback()
            }
            "error" -> fail(json.optInt("code", -1))
            "status" -> {
                // -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued
                val state = json.optInt("state", -1)
                playing = active && wantsPlayback && (state == 1 || state == 3)
                ended = state == 0
                if (state == 1 && active && wantsPlayback) started = true
                if ((state == 1 || state == 3) && (!active || !wantsPlayback)) applyPlayback()
                time = json.optDouble("time", 0.0).toFloat()
                duration = json.optDouble("duration", 0.0).toFloat()
                muted = json.optBoolean("muted", muted)
                loaded = json.optDouble("loaded", 0.0).toFloat().coerceIn(0f, 1f)
                if (SystemClock.uptimeMillis() - volumeSetAt > 1_500L) volume = json.optInt("volume", volume).coerceIn(0, 100)
            }
        }
    }
}

/** The player's web view: the page of [youTubePlayerHtml], reporting to [player]. It never takes the focus. */
@SuppressLint("SetJavaScriptEnabled")
internal fun playerView(context: Context, player: OverviewVideoPlayer): WebView {
    val origin = "https://" + context.packageName.lowercase(Locale.ROOT)
    val main = Handler(Looper.getMainLooper())
    return WebView(context).apply {
        isFocusable = false
        isFocusableInTouchMode = false
        setBackgroundColor(android.graphics.Color.BLACK)
        alpha = 1f
        settings.javaScriptEnabled = true
        // The player keeps its settings in local storage
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        // With several windows allowed and no window ever made, a link that wants a new window does nothing
        settings.setSupportMultipleWindows(true)
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.isForMainFrame

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                main.post { player.fail(-1) }
                return true
            }
        }
        addJavascriptInterface(PlayerBridge { message -> main.post { if (player.web === this) player.receive(message) } }, "DelosPlayer")
        player.web = this
        val html = youTubePlayerHtml(player.videoId, origin)
        if (html == null) main.post { player.fail(2) } else loadDataWithBaseURL(origin, html, "text/html", "utf-8", null)
    }
}

/** What the page may call: one method that hands over a report; it runs on a binder thread. */
private class PlayerBridge(private val onMessage: (String) -> Unit) {
    @JavascriptInterface
    fun post(message: String) = onMessage(message)
}

/** Instead of a video the player cannot play: the thumbnail dimmed, why, and nothing to press (videos never leave the app). */
@Composable
internal fun VideoError(code: Int, modifier: Modifier) {
    val text = TextStyle(color = Color.White, fontFamily = Figtree, fontSize = 22.tpx, textAlign = TextAlign.Center)
    Box(modifier.background(Color.Black.copy(alpha = .62f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 80.px),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.px),
        ) {
            Symbol(Symbols.WARNING, 56.tpx, Color.White)
            BasicText(
                stringResource(R.string.overview_video_unavailable),
                style = text.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 30.tpx),
            )
            BasicText(
                stringResource(if (YouTubeEmbeds.blockedByOwner(code)) R.string.overview_video_blocked else R.string.overview_video_failed),
                style = text.copy(color = Color.White.copy(alpha = .8f)),
            )
        }
    }
}
