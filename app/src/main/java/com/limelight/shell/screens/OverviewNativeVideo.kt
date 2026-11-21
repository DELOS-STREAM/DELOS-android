package com.limelight.shell.screens

import android.content.Context
import android.util.Log
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.limelight.diag.DiagLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The log tag of the native trailer player (logcat and the diagnostic log). */
private const val TAG = "OverviewNativePlayer"

/** How often the dock's timeline reads the position and the buffered part; ExoPlayer reports neither by itself. */
private const val POLL_MS = 250L

/**
 * A Steam trailer played by Media3 ExoPlayer ([stream]: an HLS master playlist, else a progressive MP4 or WebM file). Its picture is a
 * [TextureView] inside the viewer's own Compose tree ([NativeVideoSurface]), so the thumbnail, the dock and its glass draw over the
 * video in the same window and the glass blurs the moving picture. The sound plays at the app's media volume; [volume] scales it.
 */
@Stable
internal class NativeVideoPlayer(val stream: String) : OverviewVideoPlayer() {
    /** The picture's width over its height, once the video says. */
    var aspect by mutableFloatStateOf(16f / 9f)
        private set
    private var exo: ExoPlayer? = null
    private var active = false
    private var wantsPlayback = true
    private var released = false

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            started = true
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        }

        override fun onEvents(player: Player, events: Player.Events) = sync()

        override fun onPlayerError(error: PlaybackException) = fail(error)
    }

    /** Builds the player on first use and shows its picture on [view]; it starts as soon as it can unless paused. */
    fun attach(context: Context, view: TextureView) {
        if (released) return
        val player = exo ?: ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build()
            .also { player ->
                exo = player
                player.addListener(listener)
                player.setMediaItem(MediaItem.fromUri(stream))
                player.volume = if (muted) 0f else volume / 100f
                player.prepare()
            }
        player.setVideoTextureView(view)
        applyPlayback()
    }

    /** [view] is leaving the screen: the player stops drawing into it. */
    fun detach(view: TextureView) {
        exo?.clearVideoTextureView(view)
    }

    /** Reads the position, the length, the buffered part and the state into the dock's values. */
    fun sync() {
        val player = exo ?: return
        val state = player.playbackState
        ended = state == Player.STATE_ENDED
        playing = active && wantsPlayback && player.playWhenReady && (state == Player.STATE_READY || state == Player.STATE_BUFFERING)
        val length = player.duration
        if (length != C.TIME_UNSET && length > 0) {
            duration = length / 1000f
            loaded = (player.bufferedPosition.toFloat() / length).coerceIn(0f, 1f)
        }
        time = player.currentPosition.coerceAtLeast(0L) / 1000f
    }

    override fun setActive(value: Boolean) {
        active = value && !released
        if (!active) {
            wantsPlayback = false
            playing = false
        }
        applyPlayback()
    }

    private fun applyPlayback() {
        exo?.playWhenReady = active && wantsPlayback
    }

    override fun release() {
        setActive(false)
        released = true
        exo?.let { player ->
            player.removeListener(listener)
            player.release()
        }
        exo = null
    }

    override fun togglePlay() {
        if (!active || released || error != null) return
        wantsPlayback = !playing
        if (wantsPlayback && ended) exo?.seekTo(0L)
        playing = wantsPlayback
        applyPlayback()
    }

    override fun pause() {
        wantsPlayback = false
        playing = false
        applyPlayback()
    }

    override fun seekBy(seconds: Int) {
        val player = exo ?: return
        val length = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPosition + seconds * 1000L).coerceIn(0L, length)
        player.seekTo(target)
        time = target / 1000f
    }

    override fun seekTo(fraction: Float) {
        val player = exo ?: return
        if (duration <= 0f) return
        val target = (fraction.coerceIn(0f, 1f) * duration * 1000f).toLong()
        player.seekTo(target)
        time = target / 1000f
    }

    override fun toggleMute() {
        muted = !muted
        applyVolume()
    }

    override fun changeVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        if (volume > 0 && muted) muted = false
        applyVolume()
    }

    private fun applyVolume() {
        exo?.volume = if (muted) 0f else volume / 100f
    }

    private fun fail(cause: PlaybackException) {
        if (error != null || released) return
        val message = "Steam trailer failed: ${cause.errorCode} ${cause.message}" // l10n-ignore
        Log.w(TAG, message, cause)
        DiagLog.w(TAG, message)
        pause()
        error = -1
    }
}

/**
 * The trailer's picture, fitted to the viewer: a [TextureView] in the viewer's tree, not a SurfaceView, because a SurfaceView's picture
 * bypasses Compose's layers and the dock's glass could not blur it. The app in the background pauses the video (it stays paused on
 * return); leaving composition (another item, the viewer closed) releases the player and its decoder.
 */
@Composable
internal fun NativeVideoSurface(player: NativeVideoPlayer, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        key(player) {
            AndroidView(
                factory = { context -> TextureView(context).also { player.attach(context, it) } },
                modifier = Modifier.aspectRatio(player.aspect),
                onRelease = { player.detach(it) },
            )
        }
    }
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
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (isActive) {
            player.sync()
            delay(POLL_MS)
        }
    }
}
