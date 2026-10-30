package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.drawKpknGradient
import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.KpknFill
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.kpknKeyframes
import com.example.kpkn.ui.design.motion.rememberKpknClockMs

/*
 * REPARTO · movimiento del flujo (punto 23). Los `@keyframes` propios del paquete (`rp-in`, `rp-pop`) con sus valores exactos (la familia
 * `entrada-sube` del catálogo sube 14 dp y `aparece-escala` parte de 0,86: aquí son 8 dp y 0,4) y las dos animaciones infinitas con su periodo
 * exacto: el barrido del esqueleto (1400 ms lineal) y el giro del indicador (800 ms lineal). Con movimiento reducido todo queda en su estado final.
 */
internal object RpMotion {

    /** `@keyframes rp-in`: aparece subiendo 8 dp. */
    private val RpIn = kpknKeyframes("rp-in", "@keyframes rp-in { 0% { opacity: 0; transform: translateY(8px); } 100% { opacity: 1; transform: none; } }") {
        at(0f) { alpha(0f); translateY(8f) }
        at(1f) { alpha(1f); transformNone() }
    }

    /** `@keyframes rp-pop`: crece desde 0,4 con opacidad. */
    private val RpPop = kpknKeyframes("rp-pop", "@keyframes rp-pop { 0% { transform: scale(0.4); opacity: 0; } }") {
        at(0f) { scale(0.4f); alpha(0f) }
        at(1f) { alpha(1f); transformNone() }
    }

    /** rp-A04: `.rp-anim .rp-day` · 320 ms estándar, `both`, con 40 ms más de retardo por fila (`:nth-child(2..7)`). */
    fun dayIn(index: Int): KpknAnimation =
        KpknAnimation(RpIn, KpknDuration(320), KpknMotion.Estandar, KpknDuration(40 * index), fill = KpknFill.Both)

    /** rp-A06: `.rp-detail` · 260 ms estándar, sin relleno. */
    val Detail: KpknAnimation = KpknAnimation(RpIn, KpknDuration(260), KpknMotion.Estandar)

    /** rp-A07: `.rp-ok svg` · 500 ms estándar, sin relleno. */
    val Pop: KpknAnimation = KpknAnimation(RpPop, KpknDuration(500), KpknMotion.Estandar)

    /** Periodo del barrido del esqueleto (rp-A08): 1,4 s, lineal, infinito. */
    const val SkeletonPeriodMs = 1400f
}

/**
 * `.rp-sk` (rp-A08, rp-G02): el esqueleto de la semana. El degradado `brillo-esqueleto-4` mide el doble del ancho (`background-size: 200%`) y se
 * desplaza `-200 %` en 1,4 s lineal: una vuelta entera del dibujo, así que el bucle no da saltos. Con movimiento reducido se queda quieto.
 */
@Composable
internal fun RpSkeleton(shape: Shape, modifier: Modifier = Modifier) {
    val reduced = wizardReducedMotion()
    val clock = rememberKpknClockMs(running = !reduced, key = "rp-sk", loopMs = RpMotion.SkeletonPeriodMs)
    val spec = remember { KpknGradient.BrilloEsqueleto4.spec() }
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val tile = size.width * 2f
                val fraction = if (reduced) 0f else (clock.value % RpMotion.SkeletonPeriodMs) / RpMotion.SkeletonPeriodMs
                val offset = tile * fraction
                drawKpknGradient(spec, topLeft = Offset(offset - tile, 0f), size = Size(tile, size.height))
                drawKpknGradient(spec, topLeft = Offset(offset, 0f), size = Size(tile, size.height))
            },
    )
}

/** `.rp-spin` (rp-A10): anillo de 14 dp con el cuarto de arriba en tinta, girando 800 ms lineal sin fin (`giro`). */
@Composable
internal fun RpSpinner(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp).kpknAnimate(KpknFamilies.Giro.demo)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2f
        val box = Size(size.width - stroke, size.height - stroke)
        drawArc(Color(0x2AFFFFFF), 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = box, style = Stroke(stroke))
        drawArc(RpColors.ink, -135f, 90f, useCenter = false, topLeft = Offset(inset, inset), size = box, style = Stroke(stroke))
    }
}
