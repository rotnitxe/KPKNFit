package com.example.kpkn.screens.technique.compare

import com.example.kpkn.screens.technique.player.TechniquePlayerState
import com.example.kpkn.screens.technique.player.TechniqueRepMark
import kotlin.math.abs

/** Coordina acciones y límites de repetición; el reloj se observa con snapshotFlow desde la pantalla. */
internal class TechniqueComparisonPlayback(
    private val a: TechniquePlayerState,
    private val b: TechniquePlayerState,
    private val repsA: List<TechniqueRepMark>,
    private val repsB: List<TechniqueRepMark>,
) {
    val startAUs = repsA.firstOrNull()?.startUs ?: 0L
    val startBUs = repsB.firstOrNull()?.startUs ?: 0L
    val isPlaying: Boolean get() = a.isPlaying || b.isPlaying
    val durationUs: Long get() = if (a.durationUs > 0L && b.durationUs > 0L) {
        minOf((a.durationUs - startAUs).coerceAtLeast(0L), (b.durationUs - startBUs).coerceAtLeast(0L))
    } else 0L
    private var requestedPlay = false
    private var started = false
    private var restart = false
    private var scrubbing = false

    fun alignInitially() {
        // Conservar el fotograma restaurado al girar la pantalla.
        if (a.positionUs == 0L && b.positionUs == 0L) alignFirstRep() else started = true
    }

    fun togglePlayback() {
        if (isPlaying) {
            pause()
        } else {
            val align = !started || restart
            started = true
            restart = false
            requestedPlay = true
            a.play()
            b.play()
            // play() de un reproductor finalizado busca primero 0; aplicar después la alineación compartida.
            if (align) alignFirstRep()
        }
    }

    fun onPlaybackChanged() {
        if (!requestedPlay || scrubbing || (a.isPlaying && b.isPlaying)) return
        restart = atEnd(a) || atEnd(b)
        pause()
    }

    fun repIndexAt(positionUs: Long): Int {
        var low = 0
        var high = repsA.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (repsA[mid].startUs <= positionUs) {
                result = mid
                low = mid + 1
            } else high = mid - 1
        }
        return result
    }

    fun onRepBoundary(index: Int) {
        if (!requestedPlay || scrubbing || index !in repsB.indices || index !in repsA.indices) return
        val target = repsB[index].startUs
        if (abs(b.positionUs - target) > 200_000L) b.seekToUs(target)
    }

    fun scrubTo(fraction: Float) {
        if (!scrubbing) {
            scrubbing = true
            a.scrubStart()
            b.scrubStart()
        }
        val relative = (fraction.coerceIn(0f, 1f) * durationUs).toLong()
        a.scrubTo(startAUs + relative)
        b.scrubTo(startBUs + relative)
        started = true
        restart = false
    }

    fun finishScrub() {
        if (!scrubbing) return
        a.scrubEnd()
        b.scrubEnd()
        scrubbing = false
        onPlaybackChanged()
    }

    private fun alignFirstRep() {
        a.seekToUs(startAUs)
        b.seekToUs(startBUs)
    }

    private fun pause() {
        requestedPlay = false
        a.pause()
        b.pause()
    }

    private fun atEnd(state: TechniquePlayerState): Boolean =
        state.durationUs > 0L && state.positionUs >= state.durationUs - 100_000L
}
