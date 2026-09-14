package com.example.kpkn.screens.workout.components

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.kpkn.data.media.PoseTrajectoryAnalysis
import com.example.kpkn.domain.biomechanics.PoseTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun WorkoutVideoPlayer(
    file: File,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
    poseOverlayEnabled: Boolean = false,
    poseSidecarPath: String? = null,
    poseTrack: PoseTrack? = null,
) {
    val context = LocalContext.current
    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            this.playWhenReady = playWhenReady
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    val loadedTrack = remember(file.absolutePath, poseSidecarPath, poseOverlayEnabled, poseTrack) {
        if (!poseOverlayEnabled) {
            null
        } else {
            poseTrack
                ?: poseSidecarPath?.let { PoseTrajectoryAnalysis.readSidecar(File(it)) }
                ?: PoseTrajectoryAnalysis.readSidecar(PoseTrajectoryAnalysis.sidecarFile(file))
        }
    }
    var positionMs by remember(file.absolutePath) { mutableLongStateOf(0L) }
    LaunchedEffect(player, loadedTrack != null) {
        if (loadedTrack == null) return@LaunchedEffect
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(32)
        }
    }
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.player = player
                    useController = true
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
            },
        )
        val track = loadedTrack
        if (track != null) {
            PoseTrajectoryOverlay(
                track = track,
                positionMs = positionMs,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
