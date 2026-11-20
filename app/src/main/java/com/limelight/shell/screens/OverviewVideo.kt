package com.limelight.shell.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.limelight.R
import com.limelight.shell.data.YouTubeEmbeds
import com.limelight.shell.ui.Cue
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalFeedback
import com.limelight.shell.ui.Symbol
import com.limelight.shell.ui.Symbols
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.hoverLook
import com.limelight.shell.ui.pressLook
import com.limelight.shell.ui.px
import com.limelight.shell.ui.rememberPressPulse
import com.limelight.shell.ui.tpx
import java.util.Locale
import kotlinx.coroutines.delay
import org.json.JSONObject


/** Without any word from the player this long after it began to load, the video counts as failed. */
private const val LOAD_TIMEOUT_MS = 15_000L

/** A YouTube video id as IGDB stores it; anything else is never put into the player's page. */
private val VIDEO_ID = Regex("[A-Za-z0-9_-]{6,20}")

/** The native player owns the full viewport; its iframe preserves the video's natural aspect ratio. */
internal fun viewerVideoFit(width: Float, height: Float): Rect = Rect(0f, 0f, width, height)

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
 * own controls (the viewer's bar drives it), related videos only from the same channel, no annotations. [origin] is the app's identity
 * (`https://<application id>`), the page's base URL and so the Referer YouTube requires of embedding apps. The page reports to
 * `DelosPlayer.post`: `status` every 500 ms and on every change, `error` with the player's code (-1: the API did not load). Null for
 * an id that is no YouTube id.
 */
internal fun youTubePlayerHtml(videoId: String, origin: String): String? {
    if (!VIDEO_ID.matches(videoId)) return null
    return """
        <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;inset:0;width:100%;height:100%}</style></head>
        <body><div id="p"></div><script>
        var player,ready=false;
        function post(o){DelosPlayer.post(JSON.stringify(o));}
        function status(){if(!ready||!player.getPlayerState)return;post({type:'status',state:player.getPlayerState(),time:player.getCurrentTime()||0,duration:player.getDuration()||0,muted:player.isMuted()});}
        function setPlaybackAllowed(allowed,wanted){if(!ready)return;if(allowed&&wanted)player.playVideo();else player.pauseVideo();}
        function onYouTubeIframeAPIReady(){
          player=new YT.Player('p',{host:'https://www.youtube-nocookie.com',videoId:'$videoId',
            playerVars:{playsinline:1,rel:0,controls:0,fs:0,disablekb:1,iv_load_policy:3,autoplay:0,enablejsapi:1,origin:'$origin'},
            events:{onReady:function(){ready=true;post({type:'ready'});status();setInterval(status,500);},
                    onStateChange:function(){status();},
                    onError:function(e){post({type:'error',code:e.data});}}});
        }
        </script><script src="https://www.youtube.com/iframe_api" onerror="post({type:'error',code:-1})"></script></body></html>
    """.trimIndent()
}

/**
 * One video of the viewer in the in-app player: what the player reports ([receive]) and the commands of the controls and the keys,
 * sent to it through the IFrame API. [started]: it has played, so the web view shows instead of the thumbnail. [error] is the player's
 * error code (-1: it did not load); the viewer then shows why instead of the player.
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
    var error by mutableStateOf<Int?>(null)
        private set

    /** The page has reported at least once. */
    var heard = false
        private set
    private var ended = false
    internal var web: WebView? = null
    private var active = false
    private var wantsPlayback = true
    private var released = false

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
            "ready" -> applyPlayback()
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
            }
        }
    }
}

/**
 * A fully opaque native surface, never alpha-gated by readiness. The viewer draws the loading thumbnail ABOVE it as a sibling.
 * This view must not acquire a graphicsLayer, blur, rounded clip or transformed/offscreen ancestor.
 */
@Composable
internal fun OverviewVideoView(player: OverviewVideoPlayer, modifier: Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        player.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> player.setActive(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> player.setActive(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.setActive(false)
        }
    }
    LaunchedEffect(player) {
        delay(LOAD_TIMEOUT_MS)
        if (!player.heard) player.fail(-1)
    }
    AndroidView(
        factory = { context -> playerView(context, player) },
        modifier = modifier,
        onRelease = { view ->
            player.release()
            view.removeJavascriptInterface("DelosPlayer")
            view.stopLoading()
            view.destroy()
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun playerView(context: Context, player: OverviewVideoPlayer): WebView {
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

/** The viewer's overlay transport controls: seeking, playback, elapsed time, duration and mute. */
@Composable
internal fun VideoControls(player: OverviewVideoPlayer, modifier: Modifier) {
    val colors = LocalDelosColors.current
    val feedback = LocalFeedback.current
    val time = TextStyle(color = colors.text2, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 20.tpx, fontFeatureSettings = "tnum")
    Row(
        modifier.overviewGlass(null, 36f).padding(horizontal = 14.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.px),
    ) {
        ControlButton(Symbols.PLAYER_BACK, stringResource(R.string.overview_video_back)) {
            feedback.play(Cue.MOVE)
            player.seekBy(-10)
        }
        ControlButton(
            if (player.playing) Symbols.PLAYER_PAUSE else Symbols.PLAYER_PLAY,
            stringResource(if (player.playing) R.string.overview_hint_pause else R.string.overview_hint_play),
            accent = true,
        ) {
            feedback.play(Cue.SELECT)
            player.togglePlay()
        }
        ControlButton(Symbols.PLAYER_FORWARD, stringResource(R.string.overview_video_forward)) {
            feedback.play(Cue.MOVE)
            player.seekBy(10)
        }
        BasicText(videoTime(player.time), Modifier.padding(start = 8.px), style = time)
        val fraction = if (player.duration > 0f) (player.time / player.duration).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .weight(1f)
                .height(40.px)
                .pointerInput(player) { detectTapGestures { offset -> if (size.width > 0) player.seekTo(offset.x / size.width) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.px).clip(RoundedCornerShape(3.px)).background(Color.White.copy(alpha = .18f)))
            Box(Modifier.fillMaxWidth(fraction).height(6.px).clip(RoundedCornerShape(3.px)).background(colors.accent))
        }
        BasicText(videoTime(player.duration), style = time)
        ControlButton(
            if (player.muted) Symbols.PLAYER_MUTED else Symbols.PLAYER_SOUND,
            stringResource(if (player.muted) R.string.overview_hint_unmute else R.string.overview_hint_mute),
        ) {
            feedback.play(Cue.SELECT)
            player.toggleMute()
        }
    }
}

/** A round button of the control bar; [accent]: the filled play or pause button. */
@Composable
private fun ControlButton(icon: String, description: String, accent: Boolean = false, onClick: () -> Unit) {
    val colors = LocalDelosColors.current
    val press = rememberPressPulse()
    val size = if (accent) 56f else 50f
    Box(
        Modifier
            .size(size.px)
            .hoverLook(999f, press)
            .pressLook(press)
            .clip(CircleShape)
            .then(if (accent) Modifier.background(colors.accent) else Modifier)
            .semantics { contentDescription = description }
            .pointerInput(onClick) { detectTapGestures { press.afterSqueeze(onClick) } },
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, (if (accent) 28f else 26f).tpx, if (accent) Color.White else colors.text, filled = true) }
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
