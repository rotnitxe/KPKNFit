package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import com.example.kpkn.domain.onboarding.LiftMark
import kotlin.math.PI
import kotlin.math.floor

/*
 * Poses de los seis levantamientos de «¿Conoces tus marcas?»: figura de palitos de perfil mirando a la derecha,
 * con el disco de la barra visto de lado (anillos concéntricos). Todo es geometría pura de una fase `u` en 0..1
 * (0 = posición de salida, 1 = final del recorrido): los PARÁMETROS (cadera, inclinación, pies y posición de la barra
 * RESPECTO DEL HOMBRO) se interpolan entre fotogramas clave y la cinemática inversa resuelve piernas y brazos en cada
 * cuadro, así los huesos nunca cambian de largo y la barra siempre queda al alcance de las manos. Lienzo lógico de
 * [CANVAS_W] × [CANVAS_H] con el suelo en [GROUND].
 */
internal object LiftPoses {
    const val CANVAS_W = 80f
    const val CANVAS_H = 76f

    /** Eje horizontal de las figuras (la x que se centra en el símbolo). */
    const val AXIS_X = 40f

    /** Altura (y) del suelo en el lienzo. */
    const val GROUND = 70f

    /** Donde se dibuja la línea del suelo (un punto por debajo de los pies: el trazo no los tapa). */
    const val GROUND_LINE = GROUND + 1f

    /** Alto del lienzo que se muestra: del suelo hacia arriba, lo que mide la figura más alta con el disco arriba. */
    const val VIEW_H = 72f

    /** Radio del disco de la barra. */
    const val PLATE_R = 7.5f

    /** Segundos de una repetición completa. Divide a [FIG_CLOCK_WRAP] para que el bucle no dé un salto al envolver. */
    const val PERIOD = 4f

    /** Altura del banco (y de su cara superior) en el press de banca. */
    const val BENCH_Y = GROUND - 13.5f

    private const val LEG = FigGeo.LEG
    private const val ARM = FigGeo.ARM

    /** Alcance con el brazo colgando casi recto (hombro a barra): algo menos que [ARM] para que no quede tenso. */
    private const val HANG = 16.5f
    private const val STAND_HIP_Y = GROUND - LEG + 0.2f

    /**
     * Fase del cuadro de reposo (el símbolo plegado o con movimiento reducido): el instante más reconocible de cada
     * levantamiento.
     */
    fun restU(lift: LiftMark): Float = when (lift) {
        LiftMark.SQUAT -> 0.92f
        LiftMark.BENCH -> 0.40f
        LiftMark.DEADLIFT -> 0.18f
        LiftMark.OVERHEAD_PRESS -> 1.0f
        LiftMark.SNATCH -> 0.64f
        LiftMark.CLEAN_AND_JERK -> 0.72f
    }

    /**
     * Envolvente de una repetición en la fase [x] (0..1 del periodo): espera en la salida, sube, aguanta arriba y
     * baja. Lineal a trozos: la suavidad la ponen los fotogramas clave de cada levantamiento.
     */
    fun repEnvelope(x: Float): Float {
        val up = FigGeo.clamp01((x - 0.25f) / 0.40f)
        val down = FigGeo.clamp01((x - 0.75f) / 0.25f)
        return up - down
    }

    /** Fase del periodo (0..1) en la que la envolvente vale [u] mientras sube: así el bucle arranca en el cuadro de reposo. */
    fun phaseOfRest(u: Float): Float = 0.25f + FigGeo.clamp01(u) * 0.40f

    /** La fase `u` de [lift] [t] segundos después de empezar a moverse desde el cuadro de reposo. */
    fun uAt(lift: LiftMark, t: Float): Float {
        val x = phaseOfRest(restU(lift)) + t / PERIOD
        return repEnvelope(x - floor(x))
    }

    /** Resuelve la pose de [lift] en la fase [u] y la escribe en [out]. */
    @Synchronized
    fun solve(lift: LiftMark, u: Float, out: FigPose) {
        val c = FigGeo.clamp01(u)
        out.bench = Float.NaN
        when (lift) {
            LiftMark.SQUAT -> squat(c, out)
            LiftMark.BENCH -> bench(c, out)
            LiftMark.DEADLIFT -> fromKeys(deadliftTrack, c, out, headTilt = -0.35f, elbowSign = 1f)
            LiftMark.OVERHEAD_PRESS -> press(c, out)
            LiftMark.SNATCH -> fromKeys(snatchTrack, c, out, headTilt = -0.15f, elbowSign = -1f)
            LiftMark.CLEAN_AND_JERK -> fromKeys(jerkTrack, c, out, headTilt = -0.10f, elbowSign = 1f)
        }
    }

    // ---------------------------------------------------------------- interpolación de fotogramas clave

    /** Camino de parámetros: valores en instantes `us`, interpolados por tramos con entrada y salida suaves. */
    private class Track(private val us: FloatArray, private val vs: Array<FloatArray>) {
        private val dims = vs[0].size

        fun at(u: Float, out: FloatArray) {
            val c = FigGeo.clamp01(u)
            var i = 0
            while (i < us.size - 2 && c > us[i + 1]) i++
            val span = us[i + 1] - us[i]
            val t = if (span > 0f) FigGeo.smooth((c - us[i]) / span) else 1f
            for (k in 0 until dims) out[k] = FigGeo.lerp(vs[i][k], vs[i + 1][k], t)
        }
    }

    private val scratch = FloatArray(8)

    private fun hand2(bar: Offset) = Offset(bar.x - 1.1f, bar.y + 0.5f)

    /**
     * Resuelve un camino de siete parámetros: cadera (x, y), inclinación del tronco, barra respecto del hombro (dx, dy)
     * y la x de los pies cercano y lejano.
     */
    private fun fromKeys(track: Track, u: Float, out: FigPose, headTilt: Float, elbowSign: Float) {
        track.at(u, scratch)
        val hip = Offset(scratch[0], scratch[1])
        val lean = scratch[2]
        val sh = hip + FigGeo.up(lean) * FigGeo.TORSO
        val bar = Offset(sh.x + scratch[3], sh.y + scratch[4])
        out.solve(
            hip, lean, Offset(scratch[5], GROUND), Offset(scratch[6], GROUND), bar, hand2(bar),
            elbowSign = elbowSign, headTilt = headTilt,
        )
        out.bar = bar
    }

    // ---------------------------------------------------------------- sentadilla (barra a la espalda)
    private val squatTrack = Track(
        floatArrayOf(0f, 1f),
        arrayOf(floatArrayOf(41f, STAND_HIP_Y, 0.05f), floatArrayOf(33.5f, GROUND - 13f, 0.80f)),
    )

    private fun squat(u: Float, out: FigPose) {
        squatTrack.at(u, scratch)
        val hip = Offset(scratch[0], scratch[1])
        val lean = scratch[2]
        val sh = hip + FigGeo.up(lean) * FigGeo.TORSO
        val bar = Offset(sh.x - 1.6f, sh.y - 1.2f)
        out.solve(
            hip, lean, Offset(43f, GROUND), Offset(41.5f, GROUND), bar, Offset(bar.x - 1f, bar.y + 0.4f),
            elbowSign = 1f, headTilt = -0.10f, elbowRel = Offset(-3.6f, 7.6f),
        )
        out.bar = bar
    }

    // ---------------------------------------------------------------- press de banca (tumbada en el banco)
    private fun bench(u: Float, out: FigPose) {
        val hip = Offset(30f, BENCH_Y - 2.2f)
        val lean = (PI / 2.0).toFloat() - 0.02f
        val sh = hip + FigGeo.up(lean) * FigGeo.TORSO
        val reach = FigGeo.lerp(ARM - 0.6f, 5.0f, FigGeo.smooth(u))
        val bar = Offset(sh.x + 0.5f, sh.y - reach)
        out.solve(hip, lean, Offset(16f, GROUND), Offset(18.5f, GROUND), bar, hand2(bar), elbowSign = -1f, headTilt = -0.5f)
        out.bar = bar
        out.bench = BENCH_Y
    }

    // ---------------------------------------------------------------- peso muerto
    private val deadliftTrack = Track(
        floatArrayOf(0f, 0.5f, 1f),
        arrayOf(
            floatArrayOf(24.5f, 54f, 1.08f, -0.5f, HANG, 38f, 36.5f),
            floatArrayOf(28.5f, 47.5f, 0.85f, 0f, HANG, 38f, 36.5f),
            floatArrayOf(37f, STAND_HIP_Y, 0.02f, 0.6f, HANG, 38f, 36.5f),
        ),
    )

    // ---------------------------------------------------------------- press militar
    private fun press(u: Float, out: FigPose) {
        val s = FigGeo.smooth(u)
        val hip = Offset(38f, STAND_HIP_Y)
        val lean = FigGeo.lerp(0f, -0.07f, s)
        val sh = hip + FigGeo.up(lean) * FigGeo.TORSO
        val down = Offset(sh.x + 3.2f, sh.y - 0.6f)
        val high = Offset(sh.x + 0.8f, sh.y - ARM + 0.5f)
        val bar = FigGeo.lerp(down, high, s)
        out.solve(
            hip, lean, Offset(40f, GROUND), Offset(38.5f, GROUND), bar, hand2(bar),
            elbowSign = 1f, headTilt = FigGeo.lerp(0f, 0.25f, u),
        )
        out.bar = bar
    }

    // ---------------------------------------------------------------- arranque
    // Salida con la barra en el suelo → primer tirón (brazos rectos) → extensión → la barra pasa por delante de la cara (así
    // el codo no se voltea al cruzar el hombro) → recepción en sentadilla con la barra arriba → de pie con la barra arriba.
    private val snatchTrack = Track(
        floatArrayOf(0f, 0.22f, 0.42f, 0.53f, 0.64f, 1f),
        arrayOf(
            floatArrayOf(24.5f, 54f, 1.08f, -0.5f, HANG, 39.5f, 38f),
            floatArrayOf(30f, 47.6f, 0.62f, 0.6f, HANG, 39.5f, 38f),
            floatArrayOf(35.8f, 45.6f, 0.16f, 3.0f, 8.0f, 39.5f, 38f),
            floatArrayOf(35.4f, 46.8f, 0.14f, 6.5f, -4.0f, 39.5f, 38f),
            floatArrayOf(33f, GROUND - 12.5f, 0.34f, 1.0f, -(ARM - 0.7f), 39.5f, 38f),
            floatArrayOf(37.5f, STAND_HIP_Y, 0.02f, 1.0f, -(ARM - 0.7f), 39.5f, 38f),
        ),
    )

    // ---------------------------------------------------------------- dos tiempos
    // Salida → cargada (la barra a los hombros, en sentadilla corta) → flexión → empuje con tijera y la barra arriba
    // → de pie con la barra arriba.
    private val jerkTrack = Track(
        floatArrayOf(0f, 0.20f, 0.36f, 0.52f, 0.72f, 1f),
        arrayOf(
            floatArrayOf(24.5f, 54f, 1.08f, -0.5f, HANG, 38f, 36.5f),
            floatArrayOf(31.5f, 48.4f, 0.36f, 0.5f, HANG, 39f, 37.5f),
            floatArrayOf(36.5f, 51.5f, 0.08f, 3.4f, -0.2f, 39f, 37.5f),
            floatArrayOf(37f, STAND_HIP_Y + 6f, 0f, 3.4f, -0.2f, 39f, 37.5f),
            floatArrayOf(38f, STAND_HIP_Y + 4.2f, -0.03f, 0.8f, -(ARM - 0.5f), 51f, 27f),
            floatArrayOf(38f, STAND_HIP_Y, 0f, 0.8f, -(ARM - 0.5f), 40f, 36f),
        ),
    )
}
