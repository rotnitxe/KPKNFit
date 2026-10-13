package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

/** Letra mínima de un segmento al encogerse: 11 sp antes de cortar, como en `KpknSegmentedControl`. */
private const val TrackMinSp = 11f

/** Margen interior de la pista: 4 dp por cada lado. */
private val TrackInset = 4.dp

/** Aire del texto a cada lado de su segmento. */
private val TrackTextPad = 6.dp

/**
 * Control segmentado del editor. Pinta igual que `KpknSegmentedControl` (pista `inset`, indicador de tinta, segmentos de 48 dp y letra que
 * se encoge hasta 11 sp), pero admite lo que aquel no: con [selectedIndex] nulo no hay nada elegido y no se pinta indicador (la dirección
 * sin elegir o la ecuación sin dato no parecen elegidas), y con [enabled] en falso va al 38 % y no responde. Sin estado.
 */
@Composable
fun EditorSegmentedTrack(
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: KpknSegmentedSize = KpknSegmentedSize.Regular,
) {
    if (options.isEmpty()) return
    val reduced = wizardReducedMotion()
    val compact = size == KpknSegmentedSize.Compact
    val trackHeight = if (compact) 36.dp else 44.dp
    val baseStyle = if (compact) KpknType.segment.copy(fontSize = 13.sp, lineHeight = 18.sp) else KpknType.segment
    val chosen = selectedIndex?.takeIf { it in options.indices }
    val slideSpec: AnimationSpec<Float> = if (reduced) {
        snap()
    } else {
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    }
    val slide by animateFloatAsState(targetValue = (chosen ?: 0).toFloat(), animationSpec = slideSpec, label = "editorTrackSlide")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .selectableGroup(),
    ) {
        val segment: Dp = (maxWidth - TrackInset * 2) / options.size
        val availablePx = with(density) { (segment - TrackTextPad * 2).toPx() }
        val fittedSp = remember(measurer, options, baseStyle, availablePx) {
            fitTrackSize(measurer, options, baseStyle, availablePx)
        }
        val labelStyle = baseStyle.copy(fontSize = fittedSp)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(KpknShape.pill)
                .background(KpknSurface.inset, KpknShape.pill),
        )
        if (chosen != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(x = (TrackInset.toPx() + slide * segment.toPx()).roundToInt(), y = 0) }
                    .size(width = segment, height = trackHeight - TrackInset * 2)
                    .clip(KpknShape.pill)
                    .background(WizardColors.text, KpknShape.pill),
            )
        }
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = TrackInset)) {
            options.forEachIndexed { index, label ->
                val interaction = remember { MutableInteractionSource() }
                val selected = index == chosen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = labelStyle,
                        color = if (selected) WizardColors.ctaContent else WizardColors.textMuted,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Mayor letra (de la base hacia abajo, en pasos de 0,5 sp, sin bajar de 11 sp) con la que la etiqueta más ancha cabe en [availablePx]. */
private fun fitTrackSize(measurer: TextMeasurer, options: List<String>, style: TextStyle, availablePx: Float): TextUnit {
    var sp = style.fontSize.value
    while (sp > TrackMinSp) {
        val widest = options.maxOf { label ->
            measurer.measure(AnnotatedString(label), style.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width
        }
        if (widest <= availablePx) break
        sp -= 0.5f
    }
    return maxOf(sp, TrackMinSp).sp
}
