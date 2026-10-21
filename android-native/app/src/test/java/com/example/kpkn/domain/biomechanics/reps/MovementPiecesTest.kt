package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

/** El ajuste de tramos de movimiento (jerk mínimo): exacto sin ruido y mucho mejor que los cruces con ruido. */
class MovementPiecesTest {
    private val fs = 30.0

    /** Una repetición: reposo 2 s, sube en [up] s, meseta [top] s, baja en [down] s, reposo 2 s. Devuelve la señal y los índices exactos. */
    private class Rep(val x: DoubleArray, val riseStart: Double, val riseEnd: Double, val fallStart: Double, val fallEnd: Double, val peak: Int)

    private fun rep(up: Double, top: Double, down: Double, sigma: Double = 0.0, seed: Long = 1L): Rep {
        val b = SignalBuilder(fs).hold(2.0)
        val riseStart = (b.size - 1).toDouble()
        b.go(up, 1.0)
        val riseEnd = (b.size - 1).toDouble()
        b.hold(top)
        val fallStart = (b.size - 1).toDouble()
        b.go(down, 0.0)
        val fallEnd = (b.size - 1).toDouble()
        b.hold(2.0)
        val rnd = Random(seed)
        val clean = b.values()
        val raw = DoubleArray(clean.size) { clean[it] + if (sigma > 0.0) sigma * rnd.nextGaussian() else 0.0 }
        val x = ZeroPhaseLowPass.filter(raw, fs, 5.0)
        val peak = ((riseEnd + fallStart) / 2.0).toInt()
        return Rep(x, riseStart, riseEnd, fallStart, fallEnd, peak)
    }

    private fun pieces(r: Rep) = MovementPieces.cycle(r.x, 40, r.peak, r.x.size - 40, 9)

    @Test
    fun aCleanRiseAndFallAreRecoveredToAFractionOfASample() {
        for ((up, top, down) in listOf(Triple(2.0, 1.0, 1.0), Triple(1.0, 0.5, 2.0), Triple(1.5, 0.0, 1.5), Triple(3.0, 0.8, 1.5))) {
            val r = rep(up, top, down)
            val p = pieces(r)
            val rise = p.rise
            val fall = p.fall
            assertNotNull("subida $up/$top/$down", rise)
            assertNotNull("bajada $up/$top/$down", fall)
            assertEquals("salida de la subida $up/$top/$down", r.riseStart, rise!!.start, 0.6)
            assertEquals("llegada de la subida $up/$top/$down", r.riseEnd, rise.end, 0.6)
            assertEquals("salida de la bajada $up/$top/$down", r.fallStart, fall!!.start, 0.6)
            assertEquals("llegada de la bajada $up/$top/$down", r.fallEnd, fall.end, 0.6)
        }
    }

    @Test
    fun withNoiseTheFitBeatsTheThreeCrossings() {
        // 2 s de subida con SNR ≈ 15 (σ = 0,067 sobre un recorrido de 1) tras el paso bajo de 5 Hz.
        var refinedSquares = 0.0
        var roughSquares = 0.0
        var count = 0
        for (seed in 1L..30L) {
            val r = rep(2.0, 1.0, 1.0, sigma = 0.067, seed = seed)
            val p = pieces(r)
            val rise = p.rise ?: continue
            val rough = MovementPieces.fit(r.x, 40, r.peak - 15, 0.0, 1.0) ?: continue
            refinedSquares += (rise.start - r.riseStart) * (rise.start - r.riseStart) + (rise.end - r.riseEnd) * (rise.end - r.riseEnd)
            roughSquares += (rough.start - r.riseStart) * (rough.start - r.riseStart) + (rough.end - r.riseEnd) * (rough.end - r.riseEnd)
            count += 2
        }
        val refined = sqrt(refinedSquares / count) / fs * 1000.0
        val crossings = sqrt(roughSquares / count) / fs * 1000.0
        println("[tramos] error RMS de salida y llegada con SNR ≈ 15: cruces %.0f ms, mínimos cuadrados %.0f ms".format(crossings, refined))
        assertTrue("el ajuste ($refined ms) no mejora a los cruces ($crossings ms)", refined < crossings)
        // La cota de Cramér–Rao de este tramo (2 s, σ = 0,067 sobre un recorrido de 1, cuatro parámetros libres) da 59 ms de
        // desviación para la salida y para la llegada: el ajuste no puede bajar de ahí, y se le pide acercarse.
        assertTrue("el ajuste tiene un error RMS de $refined ms (cota de Cramér–Rao: 59 ms)", refined <= 80.0)
    }

    @Test
    fun theTurnPauseIsTheGapBetweenTheRiseAndTheFall() {
        val r = rep(1.5, 1.0, 1.5, sigma = 0.02, seed = 5L)
        val p = pieces(r)
        val gap = (p.fall!!.start - p.rise!!.end) / fs
        assertEquals("pausa de 1,0 s", 1.0, gap, 0.06)
        val none = rep(1.5, 0.0, 1.5, sigma = 0.02, seed = 6L)
        val q = pieces(none)
        val noGap = (q.fall!!.start - q.rise!!.end) / fs
        assertTrue("sin pausa el hueco es ${noGap} s", abs(noGap) <= 0.1)
    }

    @Test
    fun aPeakIndexBeforeThePlateauStillSplitsTheCycleAtItsCentre() {
        // Con ruido, el máximo de la señal suavizada cae donde sea de una cima plana: aquí, diez muestras antes de la meseta.
        // El centro del ciclo sale de los cruces del 50 %, no del índice del pico, y la subida y la bajada se ajustan bien.
        for (seed in 1L..10L) {
            val r = rep(2.0, 1.0, 1.0, sigma = 0.04, seed = seed)
            val early = r.riseEnd.toInt() - 10
            val p = MovementPieces.cycle(r.x, 40, early, r.x.size - 40, 9)
            val rise = p.rise
            val fall = p.fall
            assertNotNull("subida (semilla $seed)", rise)
            assertNotNull("bajada (semilla $seed)", fall)
            assertEquals("llegada de la subida (semilla $seed)", r.riseEnd, rise!!.end, 6.0)
            assertEquals("salida de la bajada (semilla $seed)", r.fallStart, fall!!.start, 6.0)
        }
    }

    @Test
    fun aValleyRightAtTheEndOfTheFallDoesNotWidenIt() {
        // El mínimo de la señal suavizada cae casi siempre al final de la bajada. Con la ventana de ajuste cortada ahí, el
        // nivel de reposo se queda sin datos y la bajada sale más ancha; con el reposo en la ventana da lo mismo que con el
        // valle en mitad del reposo.
        for (seed in 1L..10L) {
            val r = rep(1.5, 0.5, 1.0, sigma = 0.04, seed = seed)
            val atEnd = MovementPieces.cycle(r.x, 40, r.peak, r.fallEnd.toInt(), 9).fall
            val inTheMiddle = MovementPieces.cycle(r.x, 40, r.peak, r.fallEnd.toInt() + 30, 9).fall
            assertNotNull("bajada con el valle al final (semilla $seed)", atEnd)
            assertNotNull("bajada con el valle en medio (semilla $seed)", inTheMiddle)
            assertEquals("duración de la bajada (semilla $seed)", inTheMiddle!!.end - inTheMiddle.start, atEnd!!.end - atEnd.start, 3.0)
            assertEquals("llegada de la bajada (semilla $seed)", inTheMiddle.end, atEnd.end, 3.0)
        }
    }

    @Test
    fun aFlatSignalOrAMissingValleyGivesNoPieces() {
        val flat = DoubleArray(300) { 1.0 }
        val p = MovementPieces.cycle(flat, 20, 150, 280, 9)
        assertNull(p.rise)
        assertNull(p.fall)
        val r = rep(1.5, 0.5, 1.5)
        val noLeft = MovementPieces.cycle(r.x, -1, r.peak, r.x.size - 40, 9)
        assertNull(noLeft.rise)
        assertNotNull(noLeft.fall)
        val noRight = MovementPieces.cycle(r.x, 40, r.peak, -1, 9)
        assertNotNull(noRight.rise)
        assertNull(noRight.fall)
    }
}
