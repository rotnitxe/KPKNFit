package com.example.kpkn.ui.design

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.roundToInt

/** Tamaño del control segmentado: Regular (pista de 44 dp, texto de 14 sp) o Compact (pista de 36 dp, texto de 13 sp). */
enum class KpknSegmentedSize { Regular, Compact }

/** Letra mínima de un segmento al encogerse: 11 sp antes de cortar (KPKN_UI_V2 §5). */
private const val SegmentMinSp = 11f

/** Margen interior de la pista: 4 dp por cada lado. */
private val SegmentInset = 4.dp

/** Aire del texto a cada lado de su segmento. */
private val SegmentTextPad = 6.dp

/**
 * Control segmentado de la app: pista `inset` en píldora, con el segmento elegido en tinta y texto negro. El indicador se desliza con
 * resorte (amortiguación 0,8, rigidez media-baja). Cada segmento es `Role.Tab` y expone `selected`. Su zona táctil mide 48 dp de alto
 * aunque la pista sea de 44 o de 36. El texto va en una línea y se encoge hasta 11 sp antes de cortar, también al 130 % de letra.
 * Con movimiento reducido, el indicador salta.
 */
@Composable
fun KpknSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: KpknSegmentedSize = KpknSegmentedSize.Regular,
) {
    if (options.isEmpty()) return
    val reduced = wizardReducedMotion()
    val compact = size == KpknSegmentedSize.Compact
    val trackHeight = if (compact) 36.dp else 44.dp
    val baseStyle = if (compact) KpknType.segment.copy(fontSize = 13.sp, lineHeight = 18.sp) else KpknType.segment
    val chosen = selectedIndex.coerceIn(0, options.lastIndex)
    val slideSpec: AnimationSpec<Float> = if (reduced) {
        snap()
    } else {
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    }
    val slide = animateFloatAsState(targetValue = chosen.toFloat(), animationSpec = slideSpec, label = "kpknSegmentSlide")
    val measurer = rememberTextMeasurer()
    // BoxWithConstraintsScope no es un Density: la conversión a px se hace con la densidad capturada fuera.
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectableGroup(),
    ) {
        val segment: Dp = (maxWidth - SegmentInset * 2) / options.size
        val availablePx = with(density) { (segment - SegmentTextPad * 2).toPx() }
        val fittedSp = remember(measurer, options, baseStyle, availablePx) {
            fitSegmentSize(measurer, options, baseStyle, availablePx)
        }
        val labelStyle = baseStyle.copy(fontSize = fittedSp)

        // Pista e indicador: solo dibujo. Los toques van en la capa de encima, que mide 48 dp.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(KpknShape.pill)
                .background(KpknSurface.inset, KpknShape.pill),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(x = (SegmentInset.toPx() + slide.value * segment.toPx()).roundToInt(), y = 0) }
                .size(width = segment, height = trackHeight - SegmentInset * 2)
                .clip(KpknShape.pill)
                .background(WizardColors.text, KpknShape.pill),
        )
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = SegmentInset)) {
            options.forEachIndexed { index, label ->
                val interaction = remember { MutableInteractionSource() }
                val selected = index == chosen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
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
private fun fitSegmentSize(measurer: TextMeasurer, options: List<String>, style: TextStyle, availablePx: Float): TextUnit {
    var sp = style.fontSize.value
    while (sp > SegmentMinSp) {
        val widest = options.maxOf { label ->
            measurer.measure(AnnotatedString(label), style.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width
        }
        if (widest <= availablePx) break
        sp -= 0.5f
    }
    return maxOf(sp, SegmentMinSp).sp
}
