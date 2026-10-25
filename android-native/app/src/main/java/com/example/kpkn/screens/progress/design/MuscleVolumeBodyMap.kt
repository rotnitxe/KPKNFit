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
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pintura de una región que no está en [MuscleVolumeBodyMap.paints]: sin datos. */
private val MissingPaint = RegionPaint(RegionKind.NO_DATA, Color.Transparent, Color.White.copy(alpha = 0.16f))

/**
 * Cuerpo de solo lectura (T5a): la silueta anatómica y sus regiones (objetivos de toque de 48 dp), pintadas con
 * lo que diga [paints]. No edita los registros. Una región sin datos lleva trazo discontinuo, una a cero un
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
    val view = if (side == BodySide.FRONT) MuscleVolumeBodyView.FRONT else MuscleVolumeBodyView.BACK
    val paths = remember { buildBodyPaths() }
    val zones = paths.of(view)
    val density = LocalDensity.current
    val bodyWidth = bodyHeight * (MuscleVolumeBodyWidth / MuscleVolumeBodyHeight)
    val unitDp = bodyHeight.value / MuscleVolumeBodyHeight
    val currentOnSelect by rememberUpdatedState(onSelect)
    // El relleno de cada región se anima al cambiar de ventana, de rol o de métrica.
    val fills: Map<String, Color> = MuscleVolumeBodyRegions.associateWith { pillar ->
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
                        val pxPerUnit = size.height / MuscleVolumeBodyHeight
                        val slop = MuscleVolumeBodyTouchRadius.toPx() / pxPerUnit
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
            val factor = size.height / MuscleVolumeBodyHeight
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
                            if (isSelected) drawPath(shape, MuscleVolumeBodyInk.copy(alpha = 0.95f), style = Stroke(width = 1.8f))
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
