package com.example.kpkn.screens.technique.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.max

/** Velocidades del reproductor de técnica, de la más lenta a la normal. */
enum class TechniqueSpeed(val factor: Float) {
    QUARTER(0.25f),
    HALF(0.5f),
    NORMAL(1f),
}

/**
 * Lo que el estado le pide al reproductor real. Existe para que el estado no dependa de Media3 y se pueda probar con un doble:
 * el adaptador de ExoPlayer vive en `TechniqueVideoPlayer.kt`. Todo se llama desde el hilo principal.
 */
internal interface TechniquePlayerPort {
    fun play()
    fun pause()

    /** Busca [positionMs] con el modo de búsqueda vigente (exacto, salvo durante un arrastre). */
    fun seekToMs(positionMs: Long)
    fun setSpeed(factor: Float)

    /** `true` busca el fotograma exacto; `false`, el punto de sincronía más cercano (rápido, para arrastrar). */
    fun setExactSeek(exact: Boolean)

    /** Repite el tramo `[startMs, endMs]`: al llegar a [endMs] vuelve a [startMs]. Sustituye al bucle anterior. */
    fun scheduleLoop(startMs: Long, endMs: Long)
    fun cancelLoop()
}

/**
 * Estado y acciones del reproductor de técnica (VT7a). Lo crea la pantalla con [rememberTechniquePlayerState], se lo pasa a
 * `TechniqueVideoPlayer` (que posee el ExoPlayer y se engancha aquí) y alimenta con él los controles, la línea de tiempo y los
 * chips de repetición. Las acciones son seguras antes de que el reproductor exista: se aplican al engancharse.
 *
 * - [positionUs] es el PTS del fotograma que se ve (no la posición "a futuro" del reloj de ExoPlayer). Cambia en cada fotograma:
 *   léelo SOLO en la fase de dibujo (un `Canvas`, `drawWithContent`, un lambda `() -> Long`), nunca en composición.
 * - [selectedRep] es la repetición elegida (−1: ninguna). Con [loopEnabled], esa repetición se repite sola.
 * - Paso a paso: [stepFrame] pausa y busca `floor(PTS_siguiente / 1000)` de la lista de PTS de la traza, nunca redondeando hacia
 *   arriba. Sin traza, avanza un paso nominal de 33 ms.
 * - Arrastrar la línea de tiempo: [scrubStart], [scrubTo] y [scrubEnd]. Durante el arrastre se busca el punto de sincronía más
 *   cercano (rápido); al soltar, el fotograma exacto, y se reanuda si estaba sonando.
 *
 * Para la pantalla de comparar (VT9) basta con un estado por reproductor: [seekToUs], [play] y [pause] los gobiernan desde fuera.
 *
 * Uso típico, con `overlay` preparado fuera del hilo principal (`TechniqueOverlayData.from(...)`):
 * ```
 * val state = rememberTechniquePlayerState(overlay)
 * TechniqueVideoPlayer(file, overlay, state, Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(20.dp)))
 * TechniqueScrubber({ state.positionUs }, state.durationUs, reps, state.selectedRep, state::scrubTo, onScrubStart = state::scrubStart, onScrubEnd = state::scrubEnd)
 * TechniquePlayerControls(state.isPlaying, state.speed, state.loopEnabled, state::togglePlay, { state.stepFrame(-1) }, { state.stepFrame(1) }, state::setSpeed, state::toggleLoop)
 * TechniqueRepChips(reps, state.selectedRep, state::selectRep)
 * ```
 */
@Stable
class TechniquePlayerState internal constructor(
    initialPositionUs: Long = 0L,
    initialRep: Int = -1,
    initialSpeed: TechniqueSpeed = TechniqueSpeed.NORMAL,
    initialLoop: Boolean = false,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /** Los datos de la capa que el estado usa para las marcas de repetición y la lista de PTS. Los fija [rememberTechniquePlayerState]. */
    var overlay: TechniqueOverlayData? by mutableStateOf(null)
        private set

    /** `true` si el vídeo está sonando o quiere sonar (se apaga al llegar al final). */
    var isPlaying: Boolean by mutableStateOf(false)
        private set

    // Estos dos se escriben con setSpeed y setLoopEnabled: con un `private set` el setter chocaría con ellas en la JVM.
    private val speedState = mutableStateOf(initialSpeed)
    private val loopState = mutableStateOf(initialLoop)

    val speed: TechniqueSpeed get() = speedState.value

    /** Bucle de la repetición elegida. */
    val loopEnabled: Boolean get() = loopState.value

    /** Posición, en `overlay.reps`, de la repetición elegida; −1 si no hay. */
    var selectedRep: Int by mutableIntStateOf(initialRep)
        private set

    /** Duración del vídeo en µs; 0 hasta que el reproductor la conoce. */
    var durationUs: Long by mutableLongStateOf(0L)
        private set

    /** `true` si el reproductor no pudo abrir o reproducir el vídeo. */
    var hasError: Boolean by mutableStateOf(false)
        private set

    private val position = mutableLongStateOf(initialPositionUs)

    /** PTS (µs) del fotograma que se ve. Léelo solo al dibujar: cambia en cada fotograma. */
    val positionUs: Long get() = position.longValue

    private var port: TechniquePlayerPort? = null
    private var pendingSeekUs: Long = if (initialPositionUs > 0L) initialPositionUs else NONE.toLong()
    private var pendingPlay = false
    private var ended = false

    // Paso a paso: el último fotograma pedido sirve de punto de partida mientras llega a pantalla (pulsaciones seguidas).
    private var stepCursor = NONE
    private var stepCursorAtNs = 0L

    // Arrastre de la línea de tiempo.
    private var scrubbing = false
    private var resumeAfterScrub = false
    private var lastScrubMs = NONE.toLong()
    private var lastScrubUs = 0L

    // ------------------------------------------------------------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------------------------------------------------------------

    /**
     * Reproduce. Si el vídeo había llegado al final, vuelve al principio (o al inicio de la repetición en bucle). Con el bucle
     * activo y la posición fuera de la repetición elegida, entra por su inicio.
     */
    fun play() {
        stepCursor = NONE
        val loop = loopRepOrNull()
        if (port != null) {
            if (ended) {
                ended = false
                seekExact(loop?.startUs ?: 0L)
            } else if (loop != null && (positionUs < loop.startUs || positionUs > loop.endUs)) {
                seekExact(loop.startUs)
            }
        }
        startPlayback()
    }

    fun pause() {
        stepCursor = NONE
        pendingPlay = false
        port?.pause()
        isPlaying = false
    }

    fun togglePlay() {
        if (isPlaying) pause() else play()
    }

    /** Busca el fotograma exacto más cercano a [positionUs] (sin pasarse hacia delante) y deja el vídeo como estaba. */
    fun seekToUs(positionUs: Long) {
        stepCursor = NONE
        seekExact(positionUs)
    }

    /**
     * Pausa y avanza ([direction] > 0) o retrocede ([direction] < 0) un fotograma de la lista de PTS de la traza. Pulsaciones
     * seguidas encadenan los pasos aunque el anterior no haya llegado todavía a pantalla.
     */
    fun stepFrame(direction: Int) {
        val dir = if (direction < 0) -1 else 1
        pendingPlay = false
        port?.pause()
        isPlaying = false
        val steps = overlay?.stepPtsUs
        val ov = overlay
        if (ov == null || steps == null || steps.isEmpty()) {
            stepCursor = NONE
            seekExact(max(0L, positionUs + dir * NOMINAL_STEP_US))
            return
        }
        val now = nanoTime()
        val fresh = stepCursor >= 0 && now - stepCursorAtNs <= STEP_CURSOR_NS
        val base = if (fresh) stepCursor else ov.stepIndexAt(positionUs)
        val from = if (base >= 0) base else if (dir > 0) -1 else 0
        val target = (from + dir).coerceIn(0, steps.size - 1)
        stepCursor = target
        stepCursorAtNs = now
        seekExact(steps[target])
    }

    fun setSpeed(newSpeed: TechniqueSpeed) {
        speedState.value = newSpeed
        port?.setSpeed(newSpeed.factor)
    }

    /**
     * Activa o desactiva el bucle. Al activarlo sin repetición elegida, elige la del instante actual (o la siguiente, o la última) y,
     * si la posición cae fuera de ella, se coloca en su inicio. No arranca el vídeo.
     */
    fun setLoopEnabled(enabled: Boolean) {
        loopState.value = enabled
        val ov = overlay
        if (enabled && ov != null && selectedRep !in ov.reps.indices) {
            val near = ov.repIndexNear(positionUs)
            selectedRep = if (near >= 0) near else ov.reps.lastIndex
        }
        refreshLoop()
        val rep = loopRepOrNull()
        if (rep != null && (positionUs < rep.startUs || positionUs > rep.endUs)) {
            stepCursor = NONE
            seekExact(rep.startUs)
        }
    }

    fun toggleLoop() = setLoopEnabled(!loopEnabled)

    /**
     * Elige la repetición [index] (posición en `overlay.reps`): busca su inicio y, con el bucle activo, la repite. Un índice fuera de
     * rango no hace nada.
     */
    fun selectRep(index: Int) {
        val rep = overlay?.reps?.getOrNull(index) ?: return
        selectedRep = index
        stepCursor = NONE
        refreshLoop()
        seekExact(rep.startUs)
        if (loopEnabled && rep.isLoopable) startPlayback()
    }

    /** Empieza un arrastre de la línea de tiempo: pausa y pasa a buscar el punto de sincronía más cercano. */
    fun scrubStart() {
        if (scrubbing) return
        scrubbing = true
        resumeAfterScrub = isPlaying
        stepCursor = NONE
        lastScrubMs = NONE.toLong()
        lastScrubUs = positionUs
        pendingPlay = false
        port?.pause()
        isPlaying = false
        port?.setExactSeek(false)
    }

    /** Mueve el arrastre a [positionUs]. Sin [scrubStart] previo, es una búsqueda exacta normal. */
    fun scrubTo(positionUs: Long) {
        if (!scrubbing) {
            seekToUs(positionUs)
            return
        }
        val clamped = clampUs(positionUs)
        lastScrubUs = clamped
        val ms = Math.floorDiv(clamped, 1000L)
        if (ms == lastScrubMs) return
        lastScrubMs = ms
        port?.seekToMs(ms)
    }

    /** Termina el arrastre: busca el fotograma exacto de la última posición y reanuda si estaba sonando. */
    fun scrubEnd() {
        if (!scrubbing) return
        scrubbing = false
        port?.setExactSeek(true)
        seekExact(lastScrubUs)
        if (resumeAfterScrub) {
            resumeAfterScrub = false
            startPlayback()
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Lo que alimenta el reproductor (internal)
    // ------------------------------------------------------------------------------------------------------------------------

    /** Engancha el reproductor real. Aplica lo que se pidió antes de que existiera (velocidad, bucle, posición, reproducir). */
    internal fun attach(newPort: TechniquePlayerPort) {
        port = newPort
        hasError = false
        isPlaying = false
        ended = false
        durationUs = 0L
        scrubbing = false
        stepCursor = NONE
        val seek = pendingSeekUs
        position.longValue = if (seek >= 0L) seek else 0L
        newPort.setExactSeek(true)
        newPort.setSpeed(speed.factor)
        refreshLoop()
        if (seek >= 0L) {
            newPort.seekToMs(Math.floorDiv(seek, 1000L))
            pendingSeekUs = NONE.toLong()
        }
        if (pendingPlay) {
            pendingPlay = false
            newPort.play()
            isPlaying = true
        }
    }

    /** Suelta [oldPort] (si sigue siendo el enganchado). */
    internal fun detach(oldPort: TechniquePlayerPort) {
        if (port !== oldPort) return
        oldPort.cancelLoop()
        port = null
        isPlaying = false
        scrubbing = false
    }

    /** Los datos de la capa cambiaron: se vuelven a tomar las marcas de repetición y, con ellas, el bucle. */
    internal fun updateOverlay(newOverlay: TechniqueOverlayData?) {
        if (overlay === newOverlay) return
        overlay = newOverlay
        val reps = newOverlay?.reps
        if (reps == null || selectedRep !in reps.indices) selectedRep = -1
        refreshLoop()
    }

    /** El PTS del fotograma que acaba de llegar a pantalla. Si es el último que pidió el paso a paso, el cursor ya no hace falta. */
    internal fun reportFrame(ptsUs: Long) {
        position.longValue = ptsUs
        if (stepCursor >= 0 && overlay?.stepIndexAt(ptsUs) == stepCursor) stepCursor = NONE
    }

    internal fun reportDurationMs(durationMs: Long) {
        if (durationMs > 0L) durationUs = durationMs * 1000L
    }

    internal fun reportPlayWhenReady(playWhenReady: Boolean) {
        isPlaying = playWhenReady && !ended
    }

    internal fun reportEnded(isEnded: Boolean) {
        ended = isEnded
        if (isEnded) isPlaying = false
    }

    internal fun reportError(error: Boolean) {
        hasError = error
        if (error) isPlaying = false
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------------------------------------------------------------

    private fun startPlayback() {
        val p = port
        if (p == null) {
            pendingPlay = true
        } else {
            p.play()
        }
        isPlaying = true
    }

    private fun clampUs(us: Long): Long {
        val total = durationUs
        return if (total > 0L) us.coerceIn(0L, total) else max(us, 0L)
    }

    private fun seekExact(us: Long) {
        val clamped = clampUs(us)
        val p = port
        if (p == null) {
            pendingSeekUs = clamped
            return
        }
        p.seekToMs(Math.floorDiv(clamped, 1000L))
    }

    /** La repetición que se repite ahora: la elegida, con el bucle activo y si dura lo bastante. */
    private fun loopRepOrNull(): TechniqueRepMark? {
        if (!loopEnabled) return null
        return overlay?.reps?.getOrNull(selectedRep)?.takeIf { it.isLoopable }
    }

    private fun refreshLoop() {
        val p = port ?: return
        val rep = loopRepOrNull()
        if (rep == null) {
            p.cancelLoop()
        } else {
            p.scheduleLoop(Math.floorDiv(rep.startUs, 1000L), Math.floorDiv(rep.endUs, 1000L))
        }
    }

    companion object {
        private const val NONE = -1

        /** Paso nominal (≈ 30 fps) cuando no hay lista de PTS. */
        const val NOMINAL_STEP_US = 33_333L

        /** Cuánto vale el último paso pedido como punto de partida del siguiente. */
        private const val STEP_CURSOR_NS = 400_000_000L

        /** Guarda la posición, la repetición, la velocidad y el bucle para sobrevivir a un giro de pantalla. */
        internal val Saver = listSaver<TechniquePlayerState, Long>(
            save = { s -> listOf(s.positionUs, s.selectedRep.toLong(), s.speed.ordinal.toLong(), if (s.loopEnabled) 1L else 0L) },
            restore = { v -> TechniquePlayerState(v[0], v[1].toInt(), TechniqueSpeed.entries[v[2].toInt()], v[3] == 1L) },
        )
    }
}

/**
 * Crea (y conserva ante giros de pantalla) el estado del reproductor. [overlay] son los datos de la capa del vídeo, o null mientras
 * se preparan: el estado los usa para las marcas de repetición, el bucle y el paso a paso.
 */
@Composable
fun rememberTechniquePlayerState(overlay: TechniqueOverlayData?): TechniquePlayerState {
    val state = rememberSaveable(saver = TechniquePlayerState.Saver) { TechniquePlayerState() }
    SideEffect { state.updateOverlay(overlay) }
    return state
}
