package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.cos
import kotlin.math.sin

/*
 * Los símbolos de implemento (lienzo de 64 × 64 unidades). Cada uno es un dibujo de línea fina que, solo al estar
 * seleccionado, hace el movimiento que explica el implemento. Aquí: barra, rack, banco, mancuernas, kettlebell,
 * poleas, máquinas, Smith y barra de dominadas. El resto está en `EquipmentSymbolArtBody.kt`.
 *
 * Cada acento es uno de los cinco de módulo y rota por símbolo, así una cuadrícula no es de un solo color.
 */

/** El dibujo de un implemento. */
internal fun equipmentArt(id: EquipmentSymbolId): SymbolArt = when (id) {
    EquipmentSymbolId.BARBELL -> BarbellArt
    EquipmentSymbolId.RACK -> RackArt
    EquipmentSymbolId.BENCH -> BenchArt
    EquipmentSymbolId.DUMBBELLS -> DumbbellsArt
    EquipmentSymbolId.KETTLEBELL -> KettlebellArt
    EquipmentSymbolId.CABLE -> CableArt
    EquipmentSymbolId.MACHINES -> MachinesArt
    EquipmentSymbolId.SMITH -> SmithArt
    EquipmentSymbolId.PULL_UP_BAR -> PullUpBarArt
    EquipmentSymbolId.PARALLEL_BARS -> ParallelBarsArt
    EquipmentSymbolId.RINGS -> RingsArt
    EquipmentSymbolId.BANDS -> BandsArt
    EquipmentSymbolId.BALL -> BallArt
    EquipmentSymbolId.BOX -> BoxArt
    EquipmentSymbolId.CARDIO -> CardioArt
    EquipmentSymbolId.BODYWEIGHT_ONLY -> BodyweightArt
}

// ---------------------------------------------------------------- Barra y discos

/**
 * La barra con dos discos: al seleccionarla, los discos entran rodando desde fuera y se asientan; después la barra
 * entera sube 6 u y baja. En reposo, montada en el suelo. Acento: músculo.
 */
internal object BarbellArt : EquipmentArt(SymbolPalette.musculo, period = 2.6f, restT = 0.7f) {
    private const val BAR_Y = 34f
    private const val LEFT_X = 17f
    private const val RIGHT_X = 47f
    private const val DISC_R = 11f

    /** 0 = barra en el suelo, 1 = arriba (sube 6 u). */
    internal fun lift(t: Float): Float = riseHoldFall(t, 0.8f, 1.4f, 1.7f, 2.3f)

    /** Asiento de los discos al entrar: 0 = fuera, 1 = en su sitio (frenado suave en 0,6 s). */
    internal fun seat(t: Float): Float = eOutCubic(seg(t, 0f, 0.6f))

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(8f, 46f, 56f, 46f, soft, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val y = BAR_Y - 6f * lift(t)
        val s = seat(t)
        // Cada disco viene desde 10 u fuera y da un cuarto de vuelta mientras entra.
        val turn = 90f * s * DEG
        line(6f, y, 58f, y, ink)
        line(24.5f, y - 3.5f, 24.5f, y + 3.5f, ink)
        line(39.5f, y - 3.5f, 39.5f, y + 3.5f, ink)
        plate(LEFT_X - 10f * (1f - s), y, turn)
        plate(RIGHT_X + 10f * (1f - s), y, turn)
    }

    /** Un disco de frente: aro, buje y una marca que gira con él. */
    private fun SymbolPen.plate(cx: Float, cy: Float, rad: Float) {
        ring(cx, cy, DISC_R, accent, SymbolStroke.BODY)
        line(cx + 4.4f * cos(rad), cy + 4.4f * sin(rad), cx + 8.4f * cos(rad), cy + 8.4f * sin(rad), ink, SymbolStroke.FINE)
        ring(cx, cy, 2.6f, ink, SymbolStroke.FINE)
    }
}

// ---------------------------------------------------------------- Rack

private val RACK_HOLES = floatArrayOf(13f, 20f, 39f, 54f)

/**
 * El rack con la barra apoyada en los ganchos J y los discos fijos. No tiene parte dinámica: la barra no se mueve
 * (`MOVES` lo comprueba `EquipmentSymbolGeometryTest`). Acento: energía.
 */
internal object RackArt : EquipmentArt(SymbolPalette.energia, period = 2.8f, restT = 0.7f) {
    /** Altura de la barra: sobre la punta de los ganchos (y = 28,6). */
    internal const val BAR_Y = 27f

    /** El rack no se mueve: todo lo dibuja [drawStatic]. */
    internal const val MOVES = false

    /** Sin movimiento la barra nunca sube; la función queda para las pruebas de bucle (`EntrenoSymbolMathTest`). */
    internal fun lift(@Suppress("UNUSED_PARAMETER") t: Float): Float = 0f

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(17f, 6f, 17f, 58f, ink)
        line(47f, 6f, 47f, 58f, ink)
        line(17f, 6f, 47f, 6f, ink)
        line(10f, 58f, 24f, 58f, ink)
        line(40f, 58f, 54f, 58f, ink)
        for (y in RACK_HOLES) {
            dot(17f, y, 0.9f, soft)
            dot(47f, y, 0.9f, soft)
        }
        // Ganchos en J y topes de seguridad.
        poly3(17f, 32f, 23.5f, 32f, 23.5f, 28.6f, ink)
        poly3(47f, 32f, 40.5f, 32f, 40.5f, 28.6f, ink)
        line(17f, 47f, 26f, 47f, soft)
        line(47f, 47f, 38f, 47f, soft)
        // La barra apoyada en los ganchos, con los discos fijos en los extremos.
        line(4f, BAR_Y, 60f, BAR_Y, ink)
        box(5f, BAR_Y - 11f, 9.5f, BAR_Y + 11f, 2f, accent)
        box(11f, BAR_Y - 8f, 14.5f, BAR_Y + 8f, 1.8f, accent)
        box(54.5f, BAR_Y - 11f, 59f, BAR_Y + 11f, 2f, accent)
        box(49.5f, BAR_Y - 8f, 53f, BAR_Y + 8f, 1.8f, accent)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = Unit
}

// ---------------------------------------------------------------- Banco

/** Banco plano con una figura tumbada que hace press. Acento: ok. */
internal object BenchArt : EquipmentArt(SymbolPalette.ok, period = 2.6f, restT = 0.7f) {
    /** 0 = barra en el pecho, 1 = brazos extendidos. */
    internal fun press(t: Float): Float = riseHoldFall(t, 0.3f, 1.1f, 1.4f, 2.3f)

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        box(5f, 47f, 43f, 52f, 2.5f, ink)
        line(12f, 52f, 12f, 58f, ink)
        line(36f, 52f, 36f, 58f, ink)
        line(8f, 58f, 16f, 58f, ink)
        line(32f, 58f, 40f, 58f, ink)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val hy = lerpF(37.5f, 19f, press(t))
        // Figura tumbada: cabeza, tronco, muslo y espinilla (los pies llegan al suelo pasado el banco).
        ring(10f, 42.4f, 3.6f, ink, 2.4f)
        line(15f, 44.6f, 33f, 44.6f, ink, SymbolStroke.BODY)
        poly(Offset(33f, 44.6f), Offset(44f, 40f), Offset(48f, 57f), ink, 2.6f)
        // El brazo es vertical y los discos suben con él.
        line(21f, 44.6f, 21f, hy, ink, 2.6f)
        plateFace(21f, hy, 7f, accent, ink)
    }
}

// ---------------------------------------------------------------- Mancuernas

/** Curl: la mancuerna recorre un arco alrededor del codo. Acento: columna. */
internal object DumbbellsArt : EquipmentArt(SymbolPalette.columna, period = 2.6f, restT = 0.74f) {
    private const val PX = 20f
    private const val PY = 52f
    private const val FOREARM = 24f
    private const val HALF = 16f

    /** 0 = brazo extendido hacia abajo, 1 = curl completo. */
    internal fun curl(t: Float): Float = riseHoldFall(t, 0.25f, 1.15f, 1.5f, 2.4f)

    /** Ángulo del antebrazo en grados (0 = horizontal hacia delante, negativo = hacia arriba). */
    internal fun forearmDeg(t: Float): Float = lerpF(-12f, -100f, curl(t))

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val deg = forearmDeg(t)
        val a = deg * DEG
        val hx = PX + FOREARM * cos(a)
        val hy = PY + FOREARM * sin(a)
        // El brazo solo se ve al moverse; la mancuerna va cruzada al antebrazo, como en la mano.
        line(PX, 22f, PX, PY, motion)
        line(PX, PY, hx, hy, motion)
        dot(PX, PY, 2f, motion)
        dumbbell(hx, hy, HALF, deg + 90f, accent, ink)
    }
}

// ---------------------------------------------------------------- Kettlebell

/**
 * La kettlebell cuelga de un asa en arco y se balancea como un péndulo desde la mano (un punto de movimiento en el
 * agarre). Asa en tinta; campana y base, en el acento. Acento: mente.
 */
internal object KettlebellArt : EquipmentArt(SymbolPalette.mente, period = 1.8f, restT = 0f) {
    /** Radio de la campana: centro en (32, 38), base plana en y = 50. */
    internal const val BELL_R = 14f

    /** Semilargo del asa: de x 22,5 a 41,5, 19 u = 0,68 del diámetro de la campana. */
    internal const val HANDLE_HALF = 9.5f

    // Coordenadas relativas al agarre (32, 12), que es el punto de giro del péndulo.
    private const val BODY_CY = 26f
    private const val BASE_DY = 12f
    private const val SWING_DEG = 28f

    /** Asa: arco exterior de un hombro al otro por el agarre, y ventana interior más baja. */
    private val handle: Path by lazy {
        Path().apply {
            moveTo(-HANDLE_HALF, 17f)
            cubicTo(-HANDLE_HALF, -5.7f, HANDLE_HALF, -5.7f, HANDLE_HALF, 17f)
            moveTo(-6f, 17f)
            cubicTo(-6f, 0.5f, 6f, 0.5f, 6f, 17f)
        }
    }

    /** Ángulo del péndulo en grados: + hacia la derecha; en reposo cuelga vertical (0). */
    internal fun swingDeg(t: Float): Float = SWING_DEG * sin(TAU * t / period)

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        // La mano (solo seleccionado) en el agarre, y la kettlebell colgando de ella.
        dot(32f, 12f, 1.8f, motion)
        shifted(32f, 12f) {
            rotated(-swingDeg(t), 0f, 0f) {
                path(handle, ink)
                val half = acosDeg(BASE_DY / BELL_R)
                arc(0f, BODY_CY, BELL_R, 90f + half, 360f - 2f * half, accent, SymbolStroke.BODY)
                val chord = BELL_R * sin(half * DEG)
                line(-chord, BODY_CY + BASE_DY, chord, BODY_CY + BASE_DY, accent, SymbolStroke.BODY)
            }
        }
    }

    private fun acosDeg(x: Float): Float = (kotlin.math.acos(x.coerceIn(-1f, 1f)) / DEG)
}

// ---------------------------------------------------------------- Poleas

/** La manilla tira del cable por una polea y sube la pesa. Acento: músculo. */
internal object CableArt : EquipmentArt(SymbolPalette.musculo, period = 2.4f, restT = 0.6f) {
    /** Recorrido de la manilla en unidades (0 = reposo, 8 = tirada). */
    internal fun pull(t: Float): Float = 8f * riseHoldFall(t, 0.2f, 1.0f, 1.2f, 2.0f)

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(25f, 3f, 39f, 3f, ink)
        line(32f, 3f, 32f, 10f, ink)
        ring(32f, 18f, 8f, ink)
        dot(32f, 18f, 1.7f, ink)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val d = pull(t)
        // Radios de la polea: giran con el cable.
        for (i in 0..2) {
            val a = (i * 120f + d * 16f) * DEG
            line(32f + 2.4f * cos(a), 18f + 2.4f * sin(a), 32f + 5.4f * cos(a), 18f + 5.4f * sin(a), soft, SymbolStroke.FINE)
        }
        // Cable y manilla (izquierda), cable y pesa (derecha).
        line(24f, 18f, 24f, 38f + d, ink)
        poly(Offset(18f, 48f + d), Offset(24f, 38f + d), Offset(30f, 48f + d), ink)
        line(16f, 48f + d, 32f, 48f + d, ink, 2.8f)
        line(40f, 18f, 40f, 36f - d, ink)
        box(34.5f, 36f - d, 45.5f, 52f - d, 1.6f, accent)
        line(34.5f, 42f - d, 45.5f, 42f - d, accent, SymbolStroke.FINE)
        line(34.5f, 47f - d, 45.5f, 47f - d, accent, SymbolStroke.FINE)
    }
}

// ---------------------------------------------------------------- Máquinas

/**
 * Extensión de cuádriceps con su figura: torre de placas a la izquierda, asiento y una persona sentada. Al extender
 * la pierna, las dos placas de arriba suben. En reposo, la pierna extendida. Acento: energía.
 */
internal object MachinesArt : EquipmentArt(SymbolPalette.energia, period = 2.4f, restT = 1.15f) {
    private val hip = Offset(28f, 38f)
    private val knee = Offset(41f, 38f)
    private val shoulder = Offset(25f, 22f)
    private val head = Offset(24f, 15f)
    private val hand = Offset(31f, 36f)
    private val grip = Offset(32f, 37f)

    /** 0 = pierna colgando, 1 = extendida. */
    internal fun extend(t: Float): Float = riseHoldFall(t, 0.2f, 1.0f, 1.3f, 2.1f)

    /** Cuánto suben las dos placas de arriba (0 a 6 u). */
    internal fun rise(t: Float): Float = 6f * extend(t)

    /** Ángulo de la espinilla en grados: 90 colgando, 5 bajo la horizontal al extender. */
    internal fun shinDeg(t: Float): Float = lerpF(90f, 5f, extend(t))

    /** Tobillo, donde va el rodillo: la espinilla mide 13 u desde la rodilla. */
    private fun ankle(t: Float): Offset {
        val a = shinDeg(t) * DEG
        return knee + Offset(cos(a), sin(a)) * 13f
    }

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        // Torre de placas: marco tenue y las cuatro de abajo (las dos de arriba se mueven).
        box(4f, 8f, 16f, 56f, 1f, soft, SymbolStroke.FINE)
        for (i in 2..5) plate(i, 0f, ink)
        // Asiento con respaldo, poste y base.
        box(24f, 40f, 42f, 43f, 1f, ink)
        line(24f, 40f, 22f, 20f, ink)
        line(33f, 43f, 33f, 56f, ink)
        line(20f, 56f, 46f, 56f, ink)
        // Persona sentada: tronco, cuello, cabeza, muslo y brazo hasta el asidero.
        line(hip, shoulder, ink, SymbolStroke.BODY)
        line(shoulder, Offset(24f, head.y + 3.4f), ink, SymbolStroke.BODY)
        ring(head.x, head.y, 3.4f, ink, SymbolStroke.BODY)
        line(hip, knee, ink, SymbolStroke.BODY)
        line(shoulder, hand, ink, SymbolStroke.BODY)
        dot(grip.x, grip.y, 1.6f, accent)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val r = rise(t)
        plate(0, r, ink)
        plate(1, r, ink)
        // La espinilla y el brazo de la máquina, de la rodilla al rodillo, que va en el tobillo.
        val a = ankle(t)
        line(knee, a, ink, SymbolStroke.BODY)
        line(knee, a, accent, SymbolStroke.FINE)
        ring(a.x, a.y, 2.6f, accent)
    }

    /** Una placa de la torre (10 × 4,2): la i-ésima desde arriba, 4,4 u por debajo de la anterior; sube [lift]. */
    private fun SymbolPen.plate(i: Int, lift: Float, color: Color) {
        val y = 33f + 4.4f * i - lift
        box(5f, y, 15f, y + 4.2f, 1.2f, color, 1.9f)
    }
}

// ---------------------------------------------------------------- Smith / Multipower

private val SMITH_TICKS = floatArrayOf(12f, 19f, 26f, 33f, 40f, 47f, 54f)

/** La barra desliza por dos rieles. Acento: ok. */
internal object SmithArt : EquipmentArt(SymbolPalette.ok, period = 2.9f, restT = 0.75f) {
    internal fun lift(t: Float): Float = riseHoldFall(t, 0.3f, 1.2f, 1.6f, 2.5f)

    internal fun barY(t: Float): Float = lerpF(40f, 18f, lift(t))

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(4f, 59f, 60f, 59f, ink)
        box(12.5f, 4f, 17.5f, 59f, 1.6f, ink, 1.9f)
        box(46.5f, 4f, 51.5f, 59f, 1.6f, ink, 1.9f)
        for (y in SMITH_TICKS) {
            line(17.5f, y, 21f, y, soft, SymbolStroke.FINE)
            line(46.5f, y, 43f, y, soft, SymbolStroke.FINE)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val y = barY(t)
        line(2f, y, 62f, y, ink)
        box(10.5f, y - 3.2f, 19.5f, y + 3.2f, 1.2f, ink, 1.9f)
        box(44.5f, y - 3.2f, 53.5f, y + 3.2f, 1.2f, ink, 1.9f)
        box(4f, y - 10f, 8.5f, y + 10f, 2f, accent)
        box(55.5f, y - 10f, 60f, y + 10f, 2f, accent)
    }
}

// ---------------------------------------------------------------- Barra de dominadas

/** Una figura colgada de la barra hace dominadas. Acento: columna. */
internal object PullUpBarArt : EquipmentArt(SymbolPalette.columna, period = 2.6f, restT = 0.7f) {
    /** 0 = colgado, 1 = barbilla sobre la barra. */
    internal fun pull(t: Float): Float = riseHoldFall(t, 0.3f, 1.1f, 1.3f, 2.2f)

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(7f, 9f, 7f, 59f, ink)
        line(57f, 9f, 57f, 59f, ink)
        line(3f, 59f, 11f, 59f, ink)
        line(53f, 59f, 61f, 59f, ink)
        line(3f, 9f, 61f, 9f, accent, 2.8f)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val sy = lerpF(23f, 13f, pull(t))
        val handL = Offset(25f, 9f)
        val handR = Offset(39f, 9f)
        val shL = Offset(29f, sy)
        val shR = Offset(35f, sy)
        val hip = Offset(32f, sy + 13f)
        val ftL = Offset(29.5f, hip.y + 17f)
        val ftR = Offset(34.5f, hip.y + 17f)
        frontFigure(
            FrontPose(
                head = Offset(32f, sy - 7.6f),
                shL = shL, shR = shR,
                elL = ik(shL, handL, 7f, 7f, -1f), haL = handL,
                elR = ik(shR, handR, 7f, 7f, 1f), haR = handR,
                hip = hip,
                knL = Offset((hip.x + ftL.x) / 2f - 0.8f, (hip.y + ftL.y) / 2f), ftL = ftL,
                knR = Offset((hip.x + ftR.x) / 2f + 0.8f, (hip.y + ftR.y) / 2f), ftR = ftR,
            ),
            3.4f, ink, 2.4f,
        )
    }
}
