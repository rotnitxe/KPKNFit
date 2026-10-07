package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import com.example.kpkn.screens.onboarding.design.clamp01
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Símbolos de implemento de cuerpo y movimiento: paralelas, anillas, bandas, balón, cuerda, cajón, cardio y solo
 * peso corporal. Misma familia que los de `EquipmentSymbolArt.kt` (lienzo de 64 × 64, línea fina, un acento).
 */

// ---------------------------------------------------------------- Paralelas

/** Fondos entre dos barras paralelas vistas en perspectiva (se juntan hacia el fondo). Acento: mente. */
internal object ParallelBarsArt : EquipmentArt(SymbolPalette.mente, period = 2.5f, restT = 0.65f) {
    private val handL = Offset(18f, 39f)
    private val handR = Offset(46f, 39f)

    /** 0 = brazos extendidos, 1 = abajo. */
    internal fun dip(t: Float): Float = riseHoldFall(t, 0.25f, 1.05f, 1.3f, 2.1f)

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        // Postes lejanos (tenues), postes cercanos y las dos barras.
        line(26f, 34f, 26f, 52f, soft, SymbolStroke.FINE)
        line(38f, 34f, 38f, 52f, soft, SymbolStroke.FINE)
        line(8f, 45f, 8f, 59f, ink)
        line(56f, 45f, 56f, 59f, ink)
        line(4f, 59f, 12f, 59f, ink)
        line(52f, 59f, 60f, 59f, ink)
        line(8f, 45f, 26f, 34f, accent, 2.8f)
        line(56f, 45f, 38f, 34f, accent, 2.8f)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val d = dip(t)
        val sy = lerpF(24f, 31.5f, d)
        val shL = Offset(28f, sy)
        val shR = Offset(36f, sy)
        val hip = Offset(32f, sy + 13f)
        val footY = hip.y + 21f - 6.5f * d
        val ftL = Offset(29.5f, footY)
        val ftR = Offset(34.5f, footY)
        frontFigure(
            FrontPose(
                head = Offset(32f, sy - 10.5f),
                shL = shL, shR = shR,
                elL = ik(shL, handL, 9.5f, 9f, 1f), haL = handL,
                elR = ik(shR, handR, 9.5f, 9f, -1f), haR = handR,
                hip = hip,
                knL = Offset((hip.x + ftL.x) / 2f - 0.8f, (hip.y + ftL.y) / 2f), ftL = ftL,
                knR = Offset((hip.x + ftR.x) / 2f + 0.8f, (hip.y + ftR.y) / 2f), ftR = ftR,
            ),
            4f, ink, 2.6f,
        )
    }
}

// ---------------------------------------------------------------- Anillas o TRX

/** Dos anillas cuelgan de sus correas y se balancean. Acento: músculo. */
internal object RingsArt : EquipmentArt(SymbolPalette.musculo, period = 2.2f, restT = 0f) {
    /** Ángulo de balanceo en grados; [lag] desfasa la segunda anilla para que no vayan al compás. */
    internal fun swingDeg(t: Float, lag: Float): Float = 13f * sin(TAU * t / period - lag)

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(8f, 4f, 56f, 4f, ink)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        hanging(22f, swingDeg(t, 0f))
        hanging(42f, swingDeg(t, 0.3f))
    }

    private fun SymbolPen.hanging(ax: Float, deg: Float) {
        rotated(deg, ax, 4f) {
            line(ax, 4f, ax, 30f, ink)
            box(ax - 1.8f, 12f, ax + 1.8f, 19f, 1f, soft, SymbolStroke.FINE)
            ring(ax, 38.5f, 8.5f, accent, 2.6f)
        }
    }
}

// ---------------------------------------------------------------- Bandas elásticas

/** Dos asas en aro y una banda que se estira y se recoge. Acento: energía. */
internal object BandsArt : EquipmentArt(SymbolPalette.energia, period = 2.4f, restT = 0.43f) {
    private const val HANDLE_R = 5f

    /** 0 = banda floja, 1 = tensa. */
    internal fun stretch(t: Float): Float = riseHoldFall(t, 0.2f, 0.9f, 1.2f, 2.0f)

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val s = stretch(t)
        val xl = lerpF(16f, 11f, s)
        val xr = lerpF(48f, 53f, s)
        val sag = lerpF(9f, 0.8f, s)
        val th = lerpF(3.6f, 2f, s)
        val y0 = 29f
        val mid = (xl + xr) / 2f
        ring(xl - HANDLE_R, y0, HANDLE_R, ink)
        ring(xr + HANDLE_R, y0, HANDLE_R, ink)
        curve(xl, y0 - th / 2f, mid, y0 - th / 2f + 2f * sag, xr, y0 - th / 2f, accent)
        curve(xl, y0 + th / 2f, mid, y0 + th / 2f + 2f * sag, xr, y0 + th / 2f, accent)
    }
}

// ---------------------------------------------------------------- Balón

/** Un balón que bota (se aplasta al tocar el suelo y deja su sombra). Acento: ok. */
internal object BallArt : EquipmentArt(SymbolPalette.ok, period = 1.3f, restT = 0.1f) {
    private const val R = 14f
    private const val GROUND = 57f
    private const val APEX = 20f

    /** Altura del balón sobre el suelo en el instante [t] (parábola, 0 al tocar). */
    internal fun height(t: Float): Float {
        val u = wrapLoop(t, period) / period
        return APEX * 4f * u * (1f - u)
    }

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val h = height(t)
        val k = h / APEX
        val cy = GROUND - R - h
        val sq = clamp01(1f - h / 3f) * 0.18f
        oval(32f, GROUND + 1.6f, 13f - 6f * k, 2.4f - 1f * k, soft, SymbolStroke.FINE)
        scaled(1f + sq * 0.7f, 1f - sq, 32f, GROUND) {
            ring(32f, cy, R, ink)
            line(32f, cy - R, 32f, cy + R, accent, 1.8f)
            line(32f - R, cy, 32f + R, cy, accent, 1.8f)
            curve(22f, cy - 9.8f, 29f, cy, 22f, cy + 9.8f, accent, 1.8f)
            curve(42f, cy - 9.8f, 35f, cy, 42f, cy + 9.8f, accent, 1.8f)
        }
    }
}

// ---------------------------------------------------------------- Cuerda de saltar

private val ROPE_GRIPS = floatArrayOf(38f, 42f, 46f)

/** La cuerda gira entre dos empuñaduras: pasa por encima y por debajo. Acento: columna. */
internal object JumpRopeArt : EquipmentArt(SymbolPalette.columna, period = 1.1f, restT = 0.7f) {
    private const val CX = 32f
    private const val CY = 30f
    private const val RX = 25.25f
    private const val REACH = 26f

    /** Posición de la cuerda: > 0 pasa por encima de las asas, < 0 por debajo (entre −1 y 1). */
    internal fun ropeLift(t: Float, lagRad: Float = 0f): Float = sin(TAU * t / period - lagRad)

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        box(4f, 30f, 9.5f, 52f, 2.4f, ink)
        box(54.5f, 30f, 60f, 52f, 2.4f, ink)
        // Rayas de agarre de las empuñaduras.
        for (y in ROPE_GRIPS) {
            line(5.8f, y, 7.7f, y, soft, SymbolStroke.FINE)
            line(56.3f, y, 58.2f, y, soft, SymbolStroke.FINE)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        // Un rastro tenue detrás de la cuerda da sensación de velocidad.
        rope(ropeLift(t, 0.55f), accent.fainter(0.3f))
        rope(ropeLift(t), accent)
    }

    private fun SymbolPen.rope(s: Float, color: androidx.compose.ui.graphics.Color) {
        val ry = REACH * abs(s)
        if (ry < 0.8f) {
            line(6.75f, CY, 57.25f, CY, color)
        } else {
            ovalArc(CX, CY, RX, ry, 180f, if (s > 0f) 180f else -180f, color)
        }
    }
}

// ---------------------------------------------------------------- Cajón o step

/** Un cajón pliométrico: una figura salta desde el suelo hasta lo alto y baja. Acento: mente. */
internal object BoxArt : EquipmentArt(SymbolPalette.mente, period = 3.4f, restT = 2.0f) {
    private val dims = FigureDims(thigh = 6.5f, shin = 6.5f, torso = 8.5f, uarm = 4.5f, farm = 4.5f, head = 2.6f)
    private val groundRoot = Offset(8f, 58f)
    private val topRoot = Offset(37f, 28.4f)

    private const val CROUCH_AT = 0.45f
    private const val TAKEOFF = 0.75f
    private const val LANDING = 1.45f
    private const val SETTLED = 1.75f
    private const val FADE_FROM = 2.5f
    private const val FADE_TO = 2.85f

    /** Opacidad de la figura que espera en el suelo: se ve al principio y reaparece al final del bucle. */
    internal fun groundAlpha(t: Float): Float = when {
        t < TAKEOFF -> 1f
        t >= FADE_FROM -> seg(t, FADE_FROM, FADE_TO)
        else -> 0f
    }

    /** Opacidad de la figura que ya está arriba: entra al aterrizar y se desvanece antes de volver al suelo. */
    internal fun topAlpha(t: Float): Float = when {
        t < LANDING -> 0f
        t >= FADE_FROM -> 1f - seg(t, FADE_FROM, FADE_TO)
        else -> 1f
    }

    /** Posición de la raíz (los pies) en el vuelo: de suelo a lo alto en arco. */
    internal fun flightRoot(u: Float): Offset = Offset(
        lerpF(groundRoot.x, topRoot.x, u),
        lerpF(groundRoot.y, topRoot.y, u) - 17f * sin(PI.toFloat() * u),
    )

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        box(18f, 32f, 46f, 58f, 1.5f, ink)
        poly4(18f, 32f, 26f, 24f, 54f, 24f, 46f, 32f, accent)
        poly4(46f, 32f, 54f, 24f, 54f, 50f, 46f, 58f, ink, close = false)
        box(25f, 39f, 39f, 43f, 2f, soft, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val ground = groundAlpha(t)
        if (ground > 0f) {
            fade(ground) { jumper(groundRoot, crouch = smooth(seg(t, CROUCH_AT, TAKEOFF)), tuck = 0f, arms = 0f) }
        }
        if (t in TAKEOFF..LANDING) {
            val u = seg(t, TAKEOFF, LANDING)
            val crouch = 1f - smooth(seg(u, 0f, 0.2f))
            val tuck = sin(PI.toFloat() * seg(u, 0.12f, 0.88f))
            jumper(flightRoot(u), crouch, tuck, arms = smooth(seg(u, 0f, 0.3f)))
        }
        val top = topAlpha(t)
        if (top > 0f) {
            fade(top) { jumper(topRoot, crouch = 0.8f * (1f - smooth(seg(t, LANDING, SETTLED))), tuck = 0f, arms = 0f) }
        }
    }

    internal fun jumperPose(root: Offset, crouch: Float, tuck: Float, arms: Float): Pose {
        val legLen = dims.thigh + dims.shin
        val hipH = lerpF(legLen - 0.6f, 7.4f, crouch) - tuck * 2.4f
        val hip = root + Offset(-2.4f * crouch, -hipH)
        val footN = root + Offset(1.3f, -tuck * 5.5f)
        val footF = root + Offset(-1.5f, -tuck * 6.8f)
        val lean = 0.1f + 0.6f * crouch
        val sh = hip + Offset(sin(lean), -cos(lean)) * dims.torso
        val head = sh + Offset(sin(lean * 0.9f), -cos(lean * 0.9f)) * (1.4f + dims.head)
        val reach = dims.uarm + dims.farm
        val down = sh + Offset(0.8f, reach - 0.4f)
        val up = sh + Offset(3.8f, -reach * 0.85f)
        val back = sh + Offset(-6.5f, 5.5f)
        // Con crouch las manos se van atrás para el impulso.
        val a = arms - 0.8f * crouch
        val handN = if (a >= 0f) lerp(down, up, a) else lerp(down, back, -a)
        val handF = handN + Offset(1.4f, 0.4f)
        return Pose(
            hip = hip,
            kneeN = ik(hip, footN, dims.thigh, dims.shin, -1f), footN = footN,
            kneeF = ik(hip, footF, dims.thigh, dims.shin, -1f), footF = footF,
            sh = sh, head = head,
            elbowN = ik(sh, handN, dims.uarm, dims.farm, 1f), handN = handN,
            elbowF = ik(sh, handF, dims.uarm, dims.farm, 1f), handF = handF,
        )
    }

    private fun SymbolPen.jumper(root: Offset, crouch: Float, tuck: Float, arms: Float) {
        figure(jumperPose(root, crouch, tuck, arms), dims.head, ink, ink.fainter(SymbolPalette.FAR_LIMB), 2.2f)
    }
}

// ---------------------------------------------------------------- Cardio

/** Una cinta de correr con una figura corriendo y la banda en movimiento. Acento: músculo. */
internal object CardioArt : EquipmentArt(SymbolPalette.musculo, period = 0.8f, restT = 0.1f) {
    private val dims = FigureDims(thigh = 7.4f, shin = 7.4f, torso = 10.5f, uarm = 5.2f, farm = 5f, head = 3f)
    private const val BELT_Y = 47f
    private const val TICK_SPACING = 7f
    private const val TICKS = 6

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        box(5f, BELT_Y, 45f, 53f, 3f, ink)
        line(11f, 53f, 11f, 58f, ink)
        line(39f, 53f, 39f, 58f, ink)
        line(44.5f, 50f, 50f, 14f, ink)
        box(43f, 5f, 58f, 15f, 2.5f, ink)
        line(46.5f, 10f, 54.5f, 10f, accent, SymbolStroke.FINE)
        line(48.3f, 25f, 38f, 28.5f, ink)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        // La banda corre hacia atrás: sus marcas se desplazan a la izquierda un periodo de marca por zancada.
        val shift = TICK_SPACING * (t / period)
        for (i in 0 until TICKS) {
            val x = 8f + wrapLoop(i * TICK_SPACING - shift, TICKS * TICK_SPACING)
            if (x < 43f) line(x, 49f, x, 51f, soft, SymbolStroke.FINE)
        }
        val pose = runPose(dims, TAU * t / period, hipX = 24f, groundY = BELT_Y - 1f)
        figure(pose, dims.head, accent, accent.fainter(SymbolPalette.FAR_LIMB), 2.3f)
    }
}

// ---------------------------------------------------------------- Solo peso corporal

/** Una figura que salta abriendo brazos y piernas (jumping jack). Acento: energía. */
internal object BodyweightArt : EquipmentArt(SymbolPalette.energia, period = 1.4f, restT = 0.46f) {
    private const val CX = 32f
    private const val HOP = 5f

    /** Cuánto se abren brazos y piernas (0 = cerrado, 1 = abierto). */
    internal fun spread(t: Float): Float {
        val u = t / period
        return smooth(seg(u, 0f, 0.35f)) - smooth(seg(u, 0.5f, 0.85f))
    }

    /** Altura del salto en unidades (dos saltos por bucle: abrir y cerrar). */
    internal fun hop(t: Float): Float {
        val u = t / period
        return HOP * sin(PI.toFloat() * seg(u, 0f, 0.4f)) + HOP * sin(PI.toFloat() * seg(u, 0.5f, 0.9f))
    }

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(18f, 60f, 46f, 60f, soft, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val a = spread(t)
        val h = hop(t)
        val shY = 21.5f - h
        val shL = Offset(CX - 3.2f, shY)
        val shR = Offset(CX + 3.2f, shY)
        val arm = 19f
        val phi = lerpF(8f, 136f, a) * DEG
        val handL = Offset(shL.x - arm * sin(phi), shY + arm * cos(phi))
        val handR = Offset(shR.x + arm * sin(phi), shY + arm * cos(phi))
        val hip = Offset(CX, 37f - h)
        val dx = lerpF(2.2f, 11f, a)
        val ftL = Offset(CX - dx, hip.y + 20f)
        val ftR = Offset(CX + dx, hip.y + 20f)
        oval(CX, 60f, 11f - 4f * (h / HOP), 1.8f, soft, SymbolStroke.FINE)
        frontFigure(
            FrontPose(
                head = Offset(CX, 14.6f - h),
                shL = shL, shR = shR,
                elL = lerp(shL, handL, 0.5f), haL = handL,
                elR = lerp(shR, handR, 0.5f), haR = handR,
                hip = hip,
                knL = lerp(hip, ftL, 0.5f), ftL = ftL,
                knR = lerp(hip, ftR, 0.5f), ftR = ftR,
            ),
            4.4f, accent, 2.8f,
        )
    }
}
