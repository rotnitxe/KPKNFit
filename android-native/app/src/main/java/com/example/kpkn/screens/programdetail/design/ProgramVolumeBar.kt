package com.example.kpkn.screens.programdetail.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface

/** Alto del lienzo: la pista de 8 dp y la píldora de la marca, de 18 dp, que sobresale 5 dp a cada lado de la pista. */
private val BarHeight = 22.dp
private val TrackHeight = 8.dp
private val MarkWidth = 7.dp
private val MarkHeight = 18.dp
private val MarkOutline = 1.5.dp
private val NotchWidth = 2.dp
private val NotchOverhang = 2.dp

/** Zonas del objetivo sobre la pista: por debajo del mínimo, adaptativa y hasta el máximo recuperable, en blanco al 6 %, 12 % y 6 %. */
private val ZoneBelowMinimum = Color.White.copy(alpha = 0.06f)
private val ZoneAdaptive = Color.White.copy(alpha = 0.12f)
private val ZoneToRecoverable = Color.White.copy(alpha = 0.06f)

/**
 * Barra de volumen de un músculo (P4, KPKN_UI_V2): pista de 8 dp, zonas del objetivo personalizado, una muesca en el máximo adaptativo y
 * la marca del valor actual como píldora del color de su estado. Sin [zones] (sin calibrar) solo queda la pista y la marca.
 *
 * Con [animateIn], la marca sube desde cero en 600 ms; después, cada cambio de valor anima igual. Con movimiento reducido, todo va al
 * valor final sin animación.
 */
@Composable
fun ProgramVolumeBar(
    sets: Double,
    zones: ProgramVolumeZones?,
    scaleMax: Double,
    markColor: Color,
    modifier: Modifier = Modifier,
    animateIn: Boolean = false,
    /** Series planificadas: en la vista «Hecho» se marcan con un trazo blanco sobre la pista; null = sin trazo. */
    planTick: Double? = null,
) {
    val reduced = wizardReducedMotion()
    val scale = scaleMax.coerceAtLeast(1.0)
    val target = (sets / scale).toFloat().coerceIn(0f, 1f)
    val fraction = remember { Animatable(if (animateIn && !reduced) 0f else target) }
    LaunchedEffect(target, reduced) {
        if (reduced) fraction.snapTo(target) else fraction.animateTo(target, tween(durationMillis = 600))
    }

    Canvas(modifier = modifier.fillMaxWidth().height(BarHeight)) {
        val track = TrackHeight.toPx()
        val top = (size.height - track) / 2f
        val width = size.width
        val radius = track / 2f

        // Pista: el fondo de toda la barra.
        drawRoundRect(
            color = KpknSurface.inset,
            topLeft = Offset(0f, top),
            size = Size(width, track),
            cornerRadius = CornerRadius(radius),
        )

        zones?.let { z ->
            val minX = width * (z.minEffective / scale).toFloat().coerceIn(0f, 1f)
            val adaptiveX = width * (z.maxAdaptive / scale).toFloat().coerceIn(0f, 1f)
            val recoverableX = width * (z.maxRecoverable / scale).toFloat().coerceIn(0f, 1f)
            // Las zonas se recortan a la forma de la pista para que sus extremos sigan siendo redondos.
            val trackPath = Path().apply {
                addRoundRect(RoundRect(0f, top, width, top + track, CornerRadius(radius)))
            }
            clipPath(trackPath) {
                drawRect(color = ZoneBelowMinimum, topLeft = Offset(0f, top), size = Size(minX, track))
                drawRect(
                    color = ZoneAdaptive,
                    topLeft = Offset(minX, top),
                    size = Size((adaptiveX - minX).coerceAtLeast(0f), track),
                )
                drawRect(
                    color = ZoneToRecoverable,
                    topLeft = Offset(adaptiveX, top),
                    size = Size((recoverableX - adaptiveX).coerceAtLeast(0f), track),
                )
            }
            // Muesca en el máximo adaptativo: sobresale un poco de la pista para leerse de un vistazo.
            drawRect(
                color = WizardColors.textMuted,
                topLeft = Offset(adaptiveX - NotchWidth.toPx() / 2f, top - NotchOverhang.toPx()),
                size = Size(NotchWidth.toPx(), track + NotchOverhang.toPx() * 2f),
            )
        }

        // Trazo del plan (vista «Hecho»): blanco, más alto que la pista, para comparar lo hecho con lo planificado.
        planTick?.let { plan ->
            val tickX = width * (plan / scale).toFloat().coerceIn(0f, 1f)
            val tickW = NotchWidth.toPx()
            drawRect(
                color = WizardColors.text.copy(alpha = 0.75f),
                topLeft = Offset((tickX - tickW / 2f).coerceIn(0f, width - tickW), 0f),
                size = Size(tickW, size.height),
            )
        }

        // Marca del valor actual: píldora del color del estado, con un filete negro que la separa de las zonas.
        val markW = MarkWidth.toPx()
        val markH = MarkHeight.toPx()
        val outline = MarkOutline.toPx()
        val half = markW / 2f
        val x = (width * fraction.value).coerceIn(half, (width - half).coerceAtLeast(half))
        val markTop = (size.height - markH) / 2f
        drawRoundRect(
            color = KpknSurface.canvas,
            topLeft = Offset(x - half - outline, markTop - outline),
            size = Size(markW + outline * 2f, markH + outline * 2f),
            cornerRadius = CornerRadius(half + outline),
        )
        drawRoundRect(
            color = markColor,
            topLeft = Offset(x - half, markTop),
            size = Size(markW, markH),
            cornerRadius = CornerRadius(half),
        )
    }
}
