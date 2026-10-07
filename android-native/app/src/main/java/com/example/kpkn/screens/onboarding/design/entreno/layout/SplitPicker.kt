package com.example.kpkn.screens.onboarding.design.entreno.layout

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.max

/*
 * El carril de repartos: un símbolo por reparto (su mini-semana de puntos, su nombre y su resumen), sin cajas. El que se
 * toca se enciende; el reparto actual lleva la marca de «hecho». Aquí solo se elige: aplicar el reparto es del botón
 * «Adaptar mi programa a este reparto» que va debajo (lo pone el tablero).
 */

/** Ancho de un símbolo de reparto con letra normal; con letra grande crece. */
private val SPLIT_ITEM_WIDTH = 124.dp

/** Alto del dibujo de la mini-semana y separación entre sus puntos. */
private val MINI_WEEK_HEIGHT = 36.dp
private val MINI_WEEK_PITCH = 15.dp

/** Los puntos de un título del reparto: un color de módulo por título distinto (el último, tinta). */
private val SPLIT_PALETTE = listOf(
    SymbolPalette.musculo,
    SymbolPalette.columna,
    SymbolPalette.energia,
    SymbolPalette.mente,
    SymbolPalette.ok,
    SymbolPalette.ink,
)

private const val SPLIT_FADE_MS = 300

private val SplitNameStyle get() = WizardTypography.cardTitle.copy(fontFamily = WizardFonts.display, fontWeight = FontWeight.SemiBold)

/**
 * El carril horizontal de símbolos de reparto. [currentId] es el reparto que ya tiene el programa y [pendingId] el que se
 * acaba de tocar (aún sin aplicar): se enciende el pendiente o, si no hay, el actual. [order] es el orden de la semana
 * ([slotOrder]) y [currentDays] los días de entreno de ahora: con ellos la mini-semana de cada reparto cae en los días
 * de la persona cuando coincide el número de días.
 */
@Composable
internal fun SplitPicker(
    options: List<SplitOption>,
    currentId: String?,
    pendingId: String?,
    order: List<Int>,
    currentDays: Set<Int>,
    scroll: ScrollState,
    enabled: Boolean,
    reduced: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val litId = pendingId ?: currentId
    val density = LocalDensity.current
    val itemWidth: Dp = SPLIT_ITEM_WIDTH * max(1f, density.fontScale).coerceAtMost(1.6f)
    val weekStart = order.firstOrNull() ?: 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WEEK_LAYOUT_RAIL_TAG)
            .horizontalEdgeFades(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (option in options) {
            key(option.id) {
                SplitSymbol(
                    option = option,
                    lit = option.id == litId,
                    current = option.id == currentId,
                    patternDays = remember(option.dayTitles.size, weekStart, currentDays) {
                        splitPatternDays(option.dayTitles.size, weekStart, currentDays)
                    },
                    order = order,
                    width = itemWidth,
                    enabled = enabled,
                    reduced = reduced,
                    onPick = { onPick(option.id) },
                )
            }
        }
    }
}

@Composable
private fun SplitSymbol(
    option: SplitOption,
    lit: Boolean,
    current: Boolean,
    patternDays: List<Int>,
    order: List<Int>,
    width: Dp,
    enabled: Boolean,
    reduced: Boolean,
    onPick: () -> Unit,
) {
    val fade: AnimationSpec<Float> = if (reduced) snap() else tween(SPLIT_FADE_MS, easing = FastOutSlowInEasing)
    val popSpec: AnimationSpec<Float> = if (reduced) snap() else spring(dampingRatio = 0.5f, stiffness = 380f)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "splitPress",
    )
    val litAnim = animateFloatAsState(if (lit) 1f else 0f, fade, label = "splitLit")
    val popAnim = animateFloatAsState(if (lit) 1f else 0f, popSpec, label = "splitPop")
    val badgeAnim = animateFloatAsState(if (current) 1f else 0f, fade, label = "splitCurrent")
    val pen = remember { SymbolPen() }
    val colorIndexes = remember(option.dayTitles) { splitTitleColorIndexes(option.dayTitles, SPLIT_PALETTE.size) }
    // Cada punto de entreno de la mini-semana: el día en que cae y el color de su título.
    val dots = remember(patternDays, colorIndexes) {
        patternDays.mapIndexed { index, day -> day to SPLIT_PALETTE[colorIndexes.getOrElse(index) { 0 }] }.toMap()
    }
    val description = remember(option, current) { splitDescription(option, current) }

    Column(
        modifier = Modifier
            .width(width)
            .testTag(weekLayoutSplitTag(option.id))
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .selectable(
                selected = lit,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onPick,
            )
            // Después de `selectable`: conserva su rol y su estado y descarta el texto de las etiquetas (si no, TalkBack lo leería dos veces).
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(MINI_WEEK_HEIGHT)
                .graphicsLayer {
                    val z = 1f + 0.1f * (popAnim.value - litAnim.value)
                    scaleX = z
                    scaleY = z
                },
        ) {
            val pitch = MINI_WEEK_PITCH.toPx()
            val startX = (size.width - pitch * (order.size - 1)) / 2f
            // Los puntos van abajo: arriba, a la derecha, queda sitio para la marca de «hecho» del reparto actual.
            val cy = size.height - 11.dp.toPx()
            val ink = SymbolPalette.ink
            // El riel de la semana: un hilo tenue que une los siete días (el mismo motivo que el riel de etapas del alta).
            drawLine(
                ink.copy(alpha = 0.14f), Offset(startX, cy), Offset(startX + pitch * (order.size - 1), cy), 1.dp.toPx(),
            )
            val lit01 = litAnim.value
            order.forEachIndexed { index, day ->
                val cx = startX + index * pitch
                val dot = dots[day]
                if (dot != null) {
                    // Los colores del patrón se ven siempre (para comparar repartos); el elegido, a plena intensidad.
                    drawCircle(lerpColor(dot.copy(alpha = 0.5f), dot, lit01), lerpF(4.6.dp.toPx(), 5.4.dp.toPx(), lit01), Offset(cx, cy))
                } else {
                    drawCircle(ink.copy(alpha = 0.3f), 1.8.dp.toPx(), Offset(cx, cy))
                }
            }
            val badge = badgeAnim.value
            if (badge > 0.02f) {
                val r = 8.dp.toPx()
                pen.begin(this, 1f, SymbolPalette.ok)
                pen.doneBadge(this, size.width - r - 2.dp.toPx(), r, r, seg(badge, 0.2f, 1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = option.name,
            style = SplitNameStyle,
            color = if (lit) WizardColors.text else WizardColors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = option.summary,
            style = WizardTypography.note,
            color = if (lit) WizardColors.textMuted else WizardColors.textFaint,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «Torso y pierna. 4 días. Torso, Pierna, Torso, Pierna. Reparto actual.» */
internal fun splitDescription(option: SplitOption, current: Boolean): String {
    val parts = mutableListOf(option.name)
    if (option.summary.isNotBlank()) parts += option.summary
    if (option.dayTitles.isNotEmpty()) parts += option.dayTitles.joinToString(", ")
    if (current) parts += "Reparto actual"
    return parts.joinToString(". ") + "."
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))
