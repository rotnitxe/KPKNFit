package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Poses de «¿Qué ejercicios ya te salen?»: dominada (de frente), flexión, fondo y sentadilla a una pierna (de perfil
 * mirando a la derecha). Cada ejercicio es una función de la PROFUNDIDAD `d` del movimiento (0 = posición de salida,
 * 1 = punto más exigente: mentón sobre la barra, pecho al suelo, abajo del fondo, abajo de la sentadilla); el ritmo y
 * la cantidad de repeticiones de cada nivel salen de [CapabilityMotion]. Geometría pura: la cinemática inversa
 * conserva el largo de los huesos.
 */

/** Figura de FRENTE (la dominada): dos hombros, dos brazos y dos piernas casi paralelas. */
internal class FigFrontPose {
    var head = Offset.Zero
    var shoulderL = Offset.Zero
    var shoulderR = Offset.Zero
    var elbowL = Offset.Zero
    var elbowR = Offset.Zero
    var handL = Offset.Zero
    var handR = Offset.Zero
    var hip = Offset.Zero
    var kneeL = Offset.Zero
    var kneeR = Offset.Zero
    var footL = Offset.Zero
    var footR = Offset.Zero
}

/** Lo que dibuja el entorno de un ejercicio: lienzo lógico, suelo y aparatos. */
internal class CapabilityStage(
    /** Esquina y tamaño de la parte del espacio de la pose que se muestra. */
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** Altura del suelo; `NaN` si no se dibuja. */
    val groundY: Float,
)

internal object CapabilityPoses {
    private const val LEG = FigGeo.LEG
    private const val ARM = FigGeo.ARM

    // ---------------------------------------------------------------- dominada (de frente)
    const val PULL_BAR_Y = 8f
    const val PULL_CX = 40f
    const val PULL_BAR_HALF = 24f
    private const val PULL_SHOULDER_HALF = 6f
    private const val PULL_GRIP_HALF = 11.5f

    /** Hombros colgados: a esta distancia bajo la barra (los brazos quedan tensos sin pasarse de largo). */
    private const val PULL_HANG_REACH = 15.6f

    /** Hombros arriba: casi a la altura de la barra, con el mentón sobre ella. */
    private const val PULL_TOP_REACH_Y = 11f

    /** Escenario de la dominada. */
    val pullStage = CapabilityStage(left = 10f, top = 0f, width = 60f, height = 70f, groundY = Float.NaN)

    fun pullUp(d: Float, out: FigFrontPose) {
        val k = FigGeo.clamp01(d)
        val shY = FigGeo.lerp(PULL_BAR_Y + PULL_HANG_REACH, PULL_TOP_REACH_Y, k)
        out.shoulderL = Offset(PULL_CX - PULL_SHOULDER_HALF, shY)
        out.shoulderR = Offset(PULL_CX + PULL_SHOULDER_HALF, shY)
        out.handL = Offset(PULL_CX - PULL_GRIP_HALF, PULL_BAR_Y)
        out.handR = Offset(PULL_CX + PULL_GRIP_HALF, PULL_BAR_Y)
        // Codos hacia fuera y abajo: el signo de la cinemática inversa es el del lado.
        out.elbowL = FigGeo.ik(out.shoulderL, out.handL, FigGeo.UARM, FigGeo.FARM, -1f)
        out.elbowR = FigGeo.ik(out.shoulderR, out.handR, FigGeo.UARM, FigGeo.FARM, 1f)
        out.hip = Offset(PULL_CX, shY + FigGeo.TORSO)
        out.head = Offset(PULL_CX, shY - (FigGeo.NECK + FigGeo.HEAD_R))
        val legY = out.hip.y
        out.kneeL = Offset(PULL_CX - (1.5f + 0.5f * k), legY + FigGeo.THIGH - 0.6f)
        out.kneeR = Offset(PULL_CX + (1.5f + 0.5f * k), legY + FigGeo.THIGH - 0.6f)
        out.footL = Offset(PULL_CX - (1.2f + 0.7f * k), legY + LEG - 1.2f)
        out.footR = Offset(PULL_CX + (1.2f + 0.7f * k), legY + LEG - 1.2f)
    }

    // ---------------------------------------------------------------- flexión (de perfil)
    const val PUSH_GROUND = 70f
    private const val PUSH_TOE_X = 12f

    val pushStage = CapabilityStage(left = 6f, top = 42f, width = 64f, height = 32f, groundY = PUSH_GROUND + 1.5f)

    fun pushUp(d: Float, out: FigPose) {
        val k = FigGeo.clamp01(d)
        val len = FigGeo.THIGH + FigGeo.SHIN + FigGeo.TORSO
        val shH = FigGeo.lerp(ARM - 0.6f, 5.0f, k)
        val shY = PUSH_GROUND - shH
        // El cuerpo es una barra rígida de largo `len` que pivota en la punta del pie (sobre el suelo).
        val shX = PUSH_TOE_X + sqrt((len * len - shH * shH).coerceAtLeast(1f))
        val sh = Offset(shX, shY)
        val toe = Offset(PUSH_TOE_X, PUSH_GROUND)
        val t = (FigGeo.THIGH + FigGeo.SHIN) / len
        val hip = Offset(toe.x + (sh.x - toe.x) * t, toe.y + (sh.y - toe.y) * t)
        val lean = kotlin.math.atan2(sh.x - hip.x, -(sh.y - hip.y))
        out.solve(
            hip, lean,
            toe, Offset(PUSH_TOE_X + 1.4f, PUSH_GROUND),
            Offset(sh.x + 1.2f, PUSH_GROUND), Offset(sh.x - 0.8f, PUSH_GROUND),
            elbowSign = 1f, headTilt = -0.3f,
        )
        out.bar = Offset.Unspecified
        out.bench = Float.NaN
    }

    // ---------------------------------------------------------------- fondo (de perfil)
    const val DIP_BAR_Y = 36f
    const val DIP_GROUND = 74f
    const val DIP_BAR_LEFT = 30f
    const val DIP_BAR_RIGHT = 54f

    val dipStage = CapabilityStage(left = 14f, top = 4f, width = 46f, height = 74f, groundY = DIP_GROUND + 1.5f)

    fun dip(d: Float, out: FigPose) {
        val k = FigGeo.clamp01(d)
        val hx = 42f
        val reach = FigGeo.lerp(ARM - 0.4f, 9.0f, k)
        val sh = Offset(hx - 1.0f + 1.5f * k, DIP_BAR_Y - reach)
        // El tronco se inclina hacia delante: así el brazo (vertical) y el tronco se ven como dos trazos y no uno solo.
        val lean = FigGeo.lerp(0.45f, 0.75f, k)
        val hip = sh - FigGeo.up(lean) * FigGeo.TORSO
        out.solve(
            hip, lean,
            Offset(hip.x - 7.5f, hip.y + 19f), Offset(hip.x - 10f, hip.y + 18f),
            Offset(hx, DIP_BAR_Y), Offset(hx - 2f, DIP_BAR_Y),
            elbowSign = 1f, headTilt = -0.05f,
        )
        out.bar = Offset.Unspecified
        out.bench = Float.NaN
    }

    // ---------------------------------------------------------------- sentadilla a una pierna (de perfil)
    const val PISTOL_GROUND = 72f

    val pistolStage = CapabilityStage(left = 26f, top = 14f, width = 44f, height = 62f, groundY = PISTOL_GROUND + 1.5f)

    fun pistol(d: Float, out: FigPose) {
        val k = FigGeo.clamp01(d)
        val hip = Offset(FigGeo.lerp(43f, 36f, k), FigGeo.lerp(PISTOL_GROUND - LEG + 0.2f, PISTOL_GROUND - 13.5f, k))
        val lean = FigGeo.lerp(0.03f, 0.62f, k)
        // Pierna libre estirada al frente: arriba de la horizontal al empezar, casi horizontal abajo.
        val reach = FigGeo.lerp(0.92f * LEG, LEG - 0.1f, k)
        val ang = FigGeo.lerp(-0.55f, -0.12f, k)
        val footF = Offset(hip.x + reach * cos(-ang), hip.y + reach * sin(ang))
        val sh = hip + FigGeo.up(lean) * FigGeo.TORSO
        out.solve(
            hip, lean,
            Offset(44f, PISTOL_GROUND), footF,
            Offset(sh.x + 14.5f, sh.y + 1f), Offset(sh.x + 13.5f, sh.y + 2.4f),
            elbowSign = 1f, headTilt = -0.1f,
        )
        out.bar = Offset.Unspecified
        out.bench = Float.NaN
    }

    fun stageOf(skill: CapabilitySkill): CapabilityStage = when (skill) {
        CapabilitySkill.PULL_UP -> pullStage
        CapabilitySkill.PUSH_UP -> pushStage
        CapabilitySkill.DIP -> dipStage
        CapabilitySkill.PISTOL_SQUAT -> pistolStage
    }

    /** Resuelve las poses de perfil (flexión, fondo, sentadilla a una pierna) a la profundidad [d]. */
    fun solveSide(skill: CapabilitySkill, d: Float, out: FigPose) {
        when (skill) {
            CapabilitySkill.PUSH_UP -> pushUp(d, out)
            CapabilitySkill.DIP -> dip(d, out)
            CapabilitySkill.PISTOL_SQUAT -> pistol(d, out)
            CapabilitySkill.PULL_UP -> error("La dominada es de frente: usa pullUp")
        }
    }
}

/**
 * Ritmo y cantidad de repeticiones de cada nivel de [CapabilityLevel]: a qué profundidad está el ejercicio en cada
 * instante. Una función pura del tiempo, sin estado.
 *
 * - **Sin respuesta o «Aún no»**: figura quieta con el ejercicio intentado a medias ([ATTEMPT]).
 * - **«Algunas»**: [SOME_REPS] repeticiones tranquilas y una pausa en la posición de salida; el periodo [SOME_PERIOD]
 *   las contiene enteras.
 * - **«Varias»**: repeticiones fluidas y continuas, una cada [MANY_PERIOD] segundos.
 */
internal object CapabilityMotion {
    /** Profundidad de la figura quieta de «Aún no»: el ejercicio a medias. */
    const val ATTEMPT = 0.45f

    const val SOME_REPS = 3
    const val SOME_PERIOD = 5.0f

    /** Lo que dura cada repetición de «Algunas»; sobra [SOME_PERIOD] − [SOME_REPS] × esto para la pausa. */
    const val SOME_REP_SECONDS = 1.1f
    const val MANY_PERIOD = 1.25f

    /** Profundidad del cuadro estático (movimiento reducido): lo alcanzado, no un instante cualquiera del bucle. */
    fun restDepth(level: CapabilityLevel?): Float = when (level) {
        null, CapabilityLevel.NONE -> ATTEMPT
        CapabilityLevel.SOME -> 0.85f
        CapabilityLevel.MANY -> 1.0f
    }

    /** `true` si el nivel se mueve (hay bucle que animar). */
    fun isMoving(level: CapabilityLevel?): Boolean = level == CapabilityLevel.SOME || level == CapabilityLevel.MANY

    /** Una repetición: sale de 0, sube, aguanta un instante arriba y vuelve a 0. [x] es su fase (0..1). */
    fun rep(x: Float): Float {
        val c = if (x < 0f) 0f else if (x > 1f) 1f else x
        val up = FigGeo.smooth(c / 0.42f)
        val down = FigGeo.smooth((c - 0.52f) / 0.48f)
        return up - down
    }

    /** Profundidad (0..1) de un ejercicio de nivel [level] [t] segundos después de empezar. */
    fun depthAt(level: CapabilityLevel?, t: Float): Float {
        return when (level) {
            null, CapabilityLevel.NONE -> ATTEMPT
            CapabilityLevel.SOME -> {
                val p = wrap(t, SOME_PERIOD)
                val total = SOME_REPS * SOME_REP_SECONDS
                if (p >= total) 0f else rep(wrap(p, SOME_REP_SECONDS) / SOME_REP_SECONDS)
            }
            CapabilityLevel.MANY -> rep(wrap(t, MANY_PERIOD) / MANY_PERIOD)
        }
    }

    /** Resto de [t] entre [period] siempre en [0, period). Entradas inválidas dan 0. */
    fun wrap(t: Float, period: Float): Float {
        if (!(period > 0f) || !t.isFinite()) return 0f
        val m = t % period
        val r = if (m < 0f) m + period else m
        return if (r >= period) 0f else r
    }
}

/** Siguiente nivel al tocar el símbolo (con vuelta al primero) y segmentos encendidos de cada nivel. */
internal object CapabilityLevels {
    /**
     * El nivel que sigue a [current]. Sin respuesta cuenta como «Aún no», así que el primer toque elige «Algunas»;
     * después de «Varias» se vuelve a «Aún no».
     */
    fun next(current: CapabilityLevel?): CapabilityLevel {
        val all = CapabilityLevel.entries
        val i = if (current == null) 0 else current.ordinal
        return all[(i + 1) % all.size]
    }

    /** Segmentos encendidos de los tres: ninguno sin respuesta; 1, 2 o 3 según el nivel (el orden del enum es el nivel). */
    fun litSegments(current: CapabilityLevel?): Int = if (current == null) 0 else current.ordinal + 1
}
