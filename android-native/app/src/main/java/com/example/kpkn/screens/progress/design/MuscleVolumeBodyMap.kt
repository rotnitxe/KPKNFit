package com.example.kpkn.screens.progress.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.progress.BodySide
import com.example.kpkn.screens.progress.RegionKind
import com.example.kpkn.screens.progress.RegionPaint
import com.example.kpkn.ui.components.rings.RingAdjustBodyHeight
import com.example.kpkn.ui.components.rings.RingAdjustBodyInk
import com.example.kpkn.ui.components.rings.RingAdjustBodyPillars
import com.example.kpkn.ui.components.rings.RingAdjustBodyTouchRadius
import com.example.kpkn.ui.components.rings.RingAdjustBodyView
import com.example.kpkn.ui.components.rings.RingAdjustBodyWidth
import com.example.kpkn.ui.components.rings.bodyPathContains
import com.example.kpkn.ui.components.rings.buildBodyPaths
import com.example.kpkn.ui.components.rings.drawBodyDetails
import com.example.kpkn.ui.components.rings.drawBodyShell
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pintura de una región que no está en [MuscleVolumeBodyMap.paints]: sin datos. */
private val MissingPaint = RegionPaint(RegionKind.NO_DATA, Color.Transparent, Color.White.copy(alpha = 0.16f))

/**
 * Cuerpo de solo lectura (T5a): la silueta y las regiones de `RingAdjustBody` (misma geometría, mismos toques de 48 dp), pintadas con
 * lo que diga [paints]. A diferencia de `MuscleBodyAdjust`, no edita nada. Una región sin datos lleva trazo discontinuo, una a cero un
 * trazo liso y vacío, y una con series un relleno que sube de opacidad; la elegida lleva borde de tinta.
 *
 * Para TalkBack el mapa lleva el [summary], y cada región es un botón con su texto de [regionDescriptions]; la lista de series es la
 * alternativa completa. Con movimiento reducido el color cambia de golpe.
 */
@Composable
fun MuscleVolumeBodyMap(
    side: BodySide,
    paints: Map<String, RegionPaint>,
    selected: String?,
    onSelect: (String) -> Unit,
    summary: String,
    regionDescriptions: Map<String, String>,
    modifier: Modifier = Modifier,
    bodyHeight: Dp = 320.dp,
) {
    val reduced = wizardReducedMotion()
    val view = if (side == BodySide.FRONT) RingAdjustBodyView.FRONT else RingAdjustBodyView.BACK
    val paths = remember { buildBodyPaths() }
    val zones = paths.of(view)
    val density = LocalDensity.current
    val bodyWidth = bodyHeight * (RingAdjustBodyWidth / RingAdjustBodyHeight)
    val unitDp = bodyHeight.value / RingAdjustBodyHeight
    val currentOnSelect by rememberUpdatedState(onSelect)
    // El relleno de cada región se anima al cambiar de ventana, de rol o de métrica.
    val fills: Map<String, Color> = RingAdjustBodyPillars.associateWith { pillar ->
        animateColorAsState(
            targetValue = (paints[pillar] ?: MissingPaint).fill,
            animationSpec = if (reduced) tween(0) else tween(350),
            label = "muscleVolumeFill",
        ).value
    }

    Box(
        modifier = modifier
            .size(bodyWidth, bodyHeight)
            .testTag("muscle-volume-body")
            .semantics { contentDescription = summary },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(view) {
                    detectTapGestures { tap ->
                        val pxPerUnit = size.height / RingAdjustBodyHeight
                        val slop = RingAdjustBodyTouchRadius.toPx() / pxPerUnit
                        val x = tap.x / pxPerUnit
                        val y = tap.y / pxPerUnit
                        val exact = zones.entries.toList().asReversed()
                            .firstOrNull { (_, shapes) -> shapes.any { bodyPathContains(it, x, y) } }?.key
                        val nearby = zones.entries.map { (key, shapes) ->
                            key to shapes.minOf { shape ->
                                val rect = shape.getBounds()
                                val dx = maxOf(rect.left - x, 0f, x - rect.right)
                                val dy = maxOf(rect.top - y, 0f, y - rect.bottom)
                                sqrt(dx * dx + dy * dy)
                            }
                        }.minByOrNull { it.second }?.takeIf { it.second <= slop }?.first
                        (exact ?: nearby)?.let { currentOnSelect(it) }
                    }
                },
        ) {
            val factor = size.height / RingAdjustBodyHeight
            scale(factor, factor, pivot = Offset.Zero) {
                drawBodyShell(paths, view)
                val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
                clipPath(paths.silhouette.first()) {
                    zones.forEach { (pillar, shapes) ->
                        val paint = paints[pillar] ?: MissingPaint
                        val isSelected = pillar == selected
                        shapes.forEach { shape ->
                            when (paint.kind) {
                                RegionKind.NO_DATA -> drawPath(shape, paint.edge, style = Stroke(width = 1f, pathEffect = dash))
                                RegionKind.ZERO -> {
                                    drawPath(shape, paint.fill)
                                    drawPath(shape, paint.edge, style = Stroke(width = 1f))
                                }
                                RegionKind.FILLED -> {
                                    drawPath(shape, fills[pillar] ?: paint.fill)
                                    drawPath(shape, paint.edge, style = Stroke(width = 0.9f))
                                }
                            }
                            if (isSelected) drawPath(shape, RingAdjustBodyInk.copy(alpha = 0.95f), style = Stroke(width = 1.8f))
                        }
                    }
                    drawBodyDetails(paths, view)
                }
            }
        }
        // Un botón de accesibilidad por región, de al menos 48 dp, centrado en su forma.
        zones.forEach { (pillar, shapes) ->
            val box = remember(view, pillar, shapes) {
                val bounds = shapes.map { it.getBounds() }
                floatArrayOf(bounds.minOf { it.left }, bounds.minOf { it.top }, bounds.maxOf { it.right }, bounds.maxOf { it.bottom })
            }
            val width = max(48f, (box[2] - box[0]) * unitDp).dp
            val height = max(48f, (box[3] - box[1]) * unitDp).dp
            val centerX = (box[0] + box[2]) / 2f * unitDp
            val centerY = (box[1] + box[3]) / 2f * unitDp
            val isSelected = selected == pillar
            val description = regionDescriptions[pillar] ?: "$pillar, sin datos"
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            ((centerX - width.value / 2f) * density.density).roundToInt(),
                            ((centerY - height.value / 2f) * density.density).roundToInt(),
                        )
                    }
                    .size(width, height)
                    .testTag("muscle-volume-region-$pillar")
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                        this.selected = isSelected
                        onClick(label = null) {
                            currentOnSelect(pillar)
                            true
                        }
                    },
            )
        }
    }
}
