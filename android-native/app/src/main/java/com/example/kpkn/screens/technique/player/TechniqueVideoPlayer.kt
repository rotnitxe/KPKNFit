package com.example.kpkn.screens.technique.player

import android.net.Uri
import android.os.Looper
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.kpkn.R
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.technique.player.design.TechniqueOverlay
import com.example.kpkn.screens.technique.player.design.TechniqueOverlayOptions
import com.example.kpkn.ui.design.KpknType
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlin.math.max

/**
 * El reproductor de técnica (VT7a): posee el ExoPlayer de [file] y le dibuja encima la capa de [overlay] sincronizada con el
 * fotograma que se ve. Todo lo que se hace con él (reproducir, pausar, buscar, paso a paso, velocidad, bucle) pasa por [state].
 *
 * - **Vídeo:** Media3 1.5.1 con `PlayerView` (sin controles, FIT) sobre un `TextureView` —su propio layout,
 *   `technique_player_view`, y no el de la bienvenida—, para que reciba los recortes redondeados, la opacidad y las
 *   transformaciones de Compose; `SeekParameters.EXACT`.
 * - **Capa sin sondeo:** `setVideoFrameMetadataListener` anota el PTS y la hora de salida de cada fotograma; un bucle `withFrameNanos`
 *   copia al estado el que ya se ve en esa pantalla, y la capa lo lee SOLO al dibujar: ni una recomposición por fotograma. En pausa el
 *   bucle duerme hasta que el reproductor pinte otro fotograma (una búsqueda o un paso).
 * - **Rectángulo del vídeo:** sale de `player.videoSize` (ya girado) y del tamaño del contenedor, con el mismo ajuste FIT del
 *   `PlayerView`.
 * - **Ciclo de vida:** suelta el reproductor al salir de la composición y pausa en `ON_PAUSE`.
 * - Tocar el vídeo ejecuta [onTap] (por defecto, play/pausa); pasa null para desactivarlo (p. ej. dos reproductores con un solo mando).
 *
 * El tamaño lo da quien lo usa con [modifier] y tiene que ser definido (p. ej. `fillMaxWidth().aspectRatio(9f / 16f)` o una altura fija);
 * el vídeo se ajusta dentro con bandas negras. [description] es lo que lee TalkBack: con dos reproductores en pantalla, uno por cada.
 */
@OptIn(UnstableApi::class)
@Composable
fun TechniqueVideoPlayer(
    file: File,
    overlay: TechniqueOverlayData?,
    state: TechniquePlayerState,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = { state.togglePlay() },
    options: TechniqueOverlayOptions = TechniqueOverlayOptions(),
    description: String = "Vídeo de la serie con la capa de técnica",
) {
    val context = LocalContext.current
    val path = file.absolutePath
    val player = remember(path, state) {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.EXACT)
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            playWhenReady = false
            prepare()
        }
    }
    val clock = remember(player) { FramePresentationClock() }
    var videoSize by remember(player) { mutableStateOf(VideoSize.UNKNOWN) }

    DisposableEffect(player) {
        val port = ExoPlayerPort(player)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.reportEnded(playbackState == Player.STATE_ENDED)
                if (playbackState == Player.STATE_READY) state.reportDurationMs(player.duration)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                state.reportPlayWhenReady(playWhenReady)
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }

            override fun onPlayerError(error: PlaybackException) {
                state.reportError(true)
            }
        }
        val frameListener = VideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, _, _ ->
            clock.onFrame(presentationTimeUs, releaseTimeNs)
        }
        player.addListener(listener)
        player.setVideoFrameMetadataListener(frameListener)
        videoSize = player.videoSize
        state.attach(port)
        state.reportDurationMs(player.duration)
        onDispose {
            state.detach(port)
            player.clearVideoFrameMetadataListener(frameListener)
            player.removeListener(listener)
            player.release()
        }
    }

    // Del PTS que anota el reproductor al que ve el usuario: una vez por fotograma de pantalla mientras suena, y en pausa solo
    // cuando el reproductor pinta algo nuevo.
    LaunchedEffect(clock, state) {
        while (isActive) {
            if (!state.isPlaying && !clock.hasPending) clock.awaitFrame()
            val frameNs = withFrameNanos { it }
            val pts = clock.displayedAt(frameNs + FRAME_LOOKAHEAD_NS)
            if (pts != FramePresentationClock.NO_FRAME) state.reportFrame(pts)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) state.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val aspect = when {
        videoSize.width > 0 && videoSize.height > 0 -> videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        else -> overlay?.videoAspect ?: 0f
    }
    val playing = state.isPlaying
    val interaction = remember { MutableInteractionSource() }
    val tap = if (onTap != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClickLabel = "Reproducir o pausar",
            onClick = onTap,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .semantics {
                contentDescription = description
                stateDescription = if (playing) "Reproduciendo" else "En pausa"
            }
            .then(tap),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.technique_player_view, null) as PlayerView).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { view -> if (view.player !== player) view.player = player },
            onRelease = { view -> view.player = null },
        )
        if (overlay != null) {
            TechniqueOverlay(
                overlay = overlay,
                positionUs = { state.positionUs },
                modifier = Modifier.fillMaxSize(),
                videoAspect = aspect,
                options = options,
            )
        }
        if (state.hasError) {
            Text(
                text = "No se puede reproducir este vídeo",
                style = KpknType.body,
                color = WizardColors.textMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * Cuánto antes del instante de pantalla se da un fotograma por visible (ns). El `TextureView` pinta el último fotograma disponible
 * cuando Compose dibuja, así que basta un margen pequeño: lo justo para que el fotograma liberado en este mismo vsync cuente.
 */
private const val FRAME_LOOKAHEAD_NS = 6_000_000L

/**
 * El reloj de la capa: guarda el PTS y la hora de salida de los últimos fotogramas que anota el reproductor (hilo de reproducción)
 * y dice cuál se ve en un instante dado (hilo principal). Sin él, la capa se adelantaría hasta 50 ms al vídeo: ExoPlayer avisa
 * del fotograma hasta 50 ms antes de enseñarlo.
 *
 * Sin bloqueos: un anillo de [RING] huecos en `AtomicLongArray`; el escritor publica el contador al final y el lector solo mira
 * los [RING] − 1 huecos más recientes, que el escritor no está tocando.
 */
internal class FramePresentationClock {
    private val pts = AtomicLongArray(RING)
    private val release = AtomicLongArray(RING)
    private val written = AtomicInteger(0)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** `true` si el último [displayedAt] dejó un fotograma por salir (su hora aún no llegó). */
    @Volatile
    var hasPending: Boolean = false
        private set

    /**
     * Hilo de reproducción: el fotograma de PTS [presentationTimeUs] saldrá a las [releaseTimeNs] (reloj `System.nanoTime`). Una hora
     * ausente, anterior o absurda se trata como «ahora». [arrivalNs] solo se cambia en pruebas.
     */
    fun onFrame(presentationTimeUs: Long, releaseTimeNs: Long, arrivalNs: Long = System.nanoTime()) {
        val n = written.get()
        val slot = n and (RING - 1)
        val at = if (releaseTimeNs <= 0L || releaseTimeNs < arrivalNs - MAX_BEHIND_NS) {
            arrivalNs
        } else {
            minOf(releaseTimeNs, arrivalNs + MAX_AHEAD_NS)
        }
        pts.set(slot, presentationTimeUs)
        release.set(slot, at)
        written.set(n + 1)
        wake.trySend(Unit)
    }

    /** Espera a que el reproductor anote un fotograma. */
    suspend fun awaitFrame() {
        wake.receive()
    }

    /** Hilo principal: el PTS del fotograma más reciente cuya hora de salida es ≤ [nowNs], o [NO_FRAME] si todavía no salió ninguno. */
    fun displayedAt(nowNs: Long): Long {
        val n = written.get()
        if (n == 0) {
            hasPending = false
            return NO_FRAME
        }
        val oldest = max(0, n - (RING - 1))
        var i = n - 1
        while (i >= oldest) {
            val slot = i and (RING - 1)
            if (release.get(slot) <= nowNs) {
                hasPending = i < n - 1
                return pts.get(slot)
            }
            i--
        }
        hasPending = true
        return NO_FRAME
    }

    companion object {
        /** Sin fotograma. */
        const val NO_FRAME = Long.MIN_VALUE

        private const val RING = 8
        private const val MAX_AHEAD_NS = 200_000_000L
        private const val MAX_BEHIND_NS = 1_000_000_000L
    }
}

/** El adaptador de ExoPlayer al puerto que usa el estado. Todo en el hilo principal; un reproductor soltado ya no responde. */
@OptIn(UnstableApi::class)
private class ExoPlayerPort(private val player: ExoPlayer) : TechniquePlayerPort {
    private var loopMessage: PlayerMessage? = null

    override fun play() {
        if (!player.isReleased) player.play()
    }

    override fun pause() {
        if (!player.isReleased) player.pause()
    }

    override fun seekToMs(positionMs: Long) {
        if (!player.isReleased) player.seekTo(positionMs)
    }

    override fun setSpeed(factor: Float) {
        if (player.isReleased) return
        player.setPlaybackSpeed(factor)
        // A cámara lenta el audio sale deformado y no aporta nada al análisis: solo suena a velocidad normal.
        player.volume = if (factor < 1f) 0f else 1f
    }

    override fun setExactSeek(exact: Boolean) {
        if (!player.isReleased) player.setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
    }

    override fun scheduleLoop(startMs: Long, endMs: Long) {
        cancelLoop()
        if (player.isReleased) return
        loopMessage = player.createMessage { _, _ -> if (!player.isReleased) player.seekTo(startMs) }
            .setLooper(Looper.getMainLooper())
            .setPosition(endMs)
            .setDeleteAfterDelivery(false)
            .send()
    }

    override fun cancelLoop() {
        loopMessage?.cancel()
        loopMessage = null
    }
}
