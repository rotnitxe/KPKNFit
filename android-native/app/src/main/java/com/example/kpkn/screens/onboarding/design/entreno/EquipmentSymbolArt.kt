package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.screens.onboarding.design.lerpF
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

/** La barra con discos sube y baja. Acento: músculo. */
internal object BarbellArt : EquipmentArt(SymbolPalette.musculo, period = 2.4f, restT = 0.6f) {
    /** 0 = barra abajo, 1 = arriba. */
    internal fun lift(t: Float): Float = riseHoldFall(t, 0.2f, 1.0f, 1.3f, 2.1f)

    internal fun barY(t: Float): Float = lerpF(41f, 26f, lift(t))

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(12f, 58f, 52f, 58f, soft, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val y = barY(t)
        line(3f, y, 61f, y, ink)
        box(11f, y - 14f, 16.5f, y + 14f, 2.2f, accent)
        box(18.5f, y - 10f, 23f, y + 10f, 2f, accent)
        box(25.5f, y - 3.6f, 28f, y + 3.6f, 1f, ink)
        box(47.5f, y - 14f, 53f, y + 14f, 2.2f, accent)
        box(41f, y - 10f, 45.5f, y + 10f, 2f, accent)
        box(36f, y - 3.6f, 38.5f, y + 3.6f, 1f, ink)
    }
}

// ---------------------------------------------------------------- Rack

private val RACK_HOLES = floatArrayOf(13f, 20f, 39f, 54f)

/** La barra sale de los ganchos y vuelve. Acento: energía. */
internal object RackArt : EquipmentArt(SymbolPalette.energia, period = 2.8f, restT = 0.7f) {
    internal fun lift(t: Float): Float = riseHoldFall(t, 0.3f, 1.1f, 1.5f, 2.3f)

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
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val l = lift(t)
        val y = lerpF(26.4f, 17.5f, l)
        // Al salir de los ganchos la barra se acerca a quien mira: crece un poco.
        val s = 1f + 0.07f * l
        scaled(s, s, 32f, y) {
            line(4f, y, 60f, y, ink)
            box(5f, y - 11f, 9.5f, y + 11f, 2f, accent)
            box(11f, y - 8f, 14.5f, y + 8f, 1.8f, accent)
            box(54.5f, y - 11f, 59f, y + 11f, 2f, accent)
            box(49.5f, y - 8f, 53f, y + 8f, 1.8f, accent)
        }
    }
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

/** Balanceo: la kettlebell oscila como un péndulo desde la mano. Acento: mente. */
internal object KettlebellArt : EquipmentArt(SymbolPalette.mente, period = 1.8f, restT = 0f) {
    private const val PX = 32f
    private const val PY = 14f
    private const val BODY_CY = 26f
    private const val BODY_R = 13f
    private const val BASE_DY = 11.4f

    /** Asa: del cuerpo al aire y de vuelta (el origen es el punto de agarre). */
    private val handle: Path by lazy {
        Path().apply {
            moveTo(-7f, 15.2f)
            lineTo(-7f, 5.5f)
            quadraticTo(-7f, 0f, -2.5f, 0f)
            lineTo(2.5f, 0f)
            quadraticTo(7f, 0f, 7f, 5.5f)
            lineTo(7f, 15.2f)
        }
    }

    /** Ángulo del balanceo en grados: + hacia la derecha, − hacia la izquierda (péndulo en reposo en 0). */
    internal fun swingDeg(t: Float): Float = 36f * sin(TAU * t / period)

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        // La mano (solo seleccionado) y la kettlebell colgando de ella.
        dot(PX, PY - 1.5f, 1.8f, motion)
        shifted(PX, PY) {
            rotated(-swingDeg(t), 0f, 0f) {
                path(handle, ink)
                val half = acosDeg(BASE_DY / BODY_R)
                arc(0f, BODY_CY, BODY_R, 90f + half, 360f - 2f * half, accent)
                val chord = BODY_R * sin(half * DEG)
                line(-chord, BODY_CY + BASE_DY, chord, BODY_CY + BASE_DY, accent)
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

/** Pila de discos con pasador: al tirar, las placas de arriba suben. Acento: energía. */
internal object MachinesArt : EquipmentArt(SymbolPalette.energia, period = 2.4f, restT = 0.6f) {
    private const val STACK_Y0 = 19f
    private const val PITCH = 6.2f
    private const val SLAB_H = 4.8f
    private const val LIFTED = 3

    /** Cuánto suben las placas de arriba (0 a 8). */
    internal fun rise(t: Float): Float = 8f * riseHoldFall(t, 0.2f, 1.0f, 1.2f, 2.0f)

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(2f, 57f, 24f, 57f, ink)
        line(8f, 12f, 8f, 56f, soft, SymbolStroke.FINE)
        line(18f, 12f, 18f, 56f, soft, SymbolStroke.FINE)
        for (i in LIFTED..5) slab(i, 0f, ink)
        // Polea de arriba y el tramo que cruza hacia la manilla.
        ring(13f, 9f, 3f, ink)
        line(13f, 6f, 45f, 6f, ink, SymbolStroke.FINE)
        // Asiento con respaldo.
        box(33f, 47f, 54f, 51f, 2f, ink)
        line(54f, 47f, 56.5f, 30f, ink)
        line(43f, 51f, 43f, 57f, ink)
        line(33f, 57f, 54f, 57f, ink)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val d = rise(t)
        for (i in 0 until LIFTED) slab(i, d, ink)
        // Pasador en la tercera placa, cable al pasar por la polea y manilla que baja lo mismo que sube la pila.
        line(21f, STACK_Y0 + 2f * PITCH + SLAB_H / 2f - d, 26f, STACK_Y0 + 2f * PITCH + SLAB_H / 2f - d, ink)
        dot(26.8f, STACK_Y0 + 2f * PITCH + SLAB_H / 2f - d, 1.2f, ink)
        line(10f, STACK_Y0 - d, 10f, 9f, ink, SymbolStroke.FINE)
        arc(13f, 9f, 3f, 180f, 180f, ink, SymbolStroke.FINE)
        line(45f, 6f, 45f, 24f + d, ink, SymbolStroke.FINE)
        ring(45f, 27f + d, 3f, accent)
    }

    private fun SymbolPen.slab(i: Int, lift: Float, color: androidx.compose.ui.graphics.Color) {
        val y = STACK_Y0 + i * PITCH - lift
        box(5f, y, 21f, y + SLAB_H, 1.2f, color, 1.9f)
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
