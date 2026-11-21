package com.limelight.shell

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.limelight.shell.screens.OverviewModel
import com.limelight.shell.screens.OverviewViewer
import com.limelight.shell.screens.ViewerRequest
import dev.chrisbanes.haze.rememberHazeState

/** Debug-only, real players and public Hades media; no paired PC or profile is required.
 * `preview_index` selects an item; `preview_reduced` enables the 200 ms fade. */
@Composable
internal fun OverviewViewerPreview(intent: Intent, shell: ShellState) {
    val media = remember { viewerMedia() }
    var request by remember { mutableStateOf(ViewerRequest(intent.getIntExtra("preview_index", 0).coerceIn(media.indices), null)) }
    var visible by remember { mutableStateOf(true) }
    val input = remember { com.limelight.shell.screens.OverviewViewerInput() }
    val haze = rememberHazeState()
    CompositionLocalProvider(LocalShell provides shell) {
        Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
            val direction = when (event.key) {
                Key.ButtonL1, Key.LeftBracket -> -1
                Key.ButtonR1, Key.RightBracket -> 1
                else -> return@onPreviewKeyEvent false
            }
            if (event.type == KeyEventType.KeyDown) shell.onShoulder?.invoke(direction)
            true
        }) {
            if (visible) OverviewViewer(
                media, request, haze, intent.getBooleanExtra("preview_reduced", false), thumbRect = { null },
                onIndex = { request = request.copy(index = it) }, onClosed = { visible = false },
                onCloseRequest = { request = request.copy(closing = true) },
                input = input, onControlsVisibilityChanged = { shell.overviewViewerControlsVisible = it },
            )
        }
    }
}

private fun viewerMedia(): List<OverviewModel.Media> {
    val base = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1145360/"
    val shots = listOf("c0fed447426b69981cf1721756acf75369801b31", "8a9f0953e8a014bd3df2789c2835cb787cd3764d")
    val poster = base + "ss_${shots[0]}.1920x1080.jpg"
    val stream = "https://video.akamai.steamstatic.com/store_trailers/1145360/312686/b0312c49135823cc34946e98148abfb8da71f0fb/1750627549/hls_264_master.m3u8"
    val video = OverviewModel.Media(OverviewModel.MediaKind.VIDEO, "256801252", "Hades - Launch Trailer", poster, poster, 1, stream)
    val youtube = OverviewModel.Media(OverviewModel.MediaKind.VIDEO, "91t0ha9x0AE", "Hades - Animated Trailer", poster, poster, 1)
    return listOf(video, video.copy(id = "native-repeat", number = 2)) + shots.mapIndexed { index, id ->
        OverviewModel.Media(OverviewModel.MediaKind.SCREENSHOT, id, null, base + "ss_$id.600x338.jpg", base + "ss_$id.1920x1080.jpg", index + 1)
    } + listOf(youtube, youtube.copy(number = 2))
}
