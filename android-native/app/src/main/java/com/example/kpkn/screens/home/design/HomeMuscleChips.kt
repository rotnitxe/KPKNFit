package com.example.kpkn.screens.home.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

/*
 * Los músculos de la sesión como fila de chips (Home v1.1, I10a): un mini anillo de recuperación por músculo, en una fila que se
 * desplaza en horizontal. Sin estado: la fila recuerda solo si su arco ya creció (HomeGaugeMemory).
 */

/** A muscle named in the authored session. */
internal data class HomeSessionMuscle(val name: String)

/** Keep the authored order; a name does not imply a recovery measurement. */
internal fun homeMuscleOrder(muscles: List<HomeSessionMuscle>): List<HomeSessionMuscle> =
    muscles.distinctBy { it.name }

/**
 * Recuerda la última fila de chips que ya trazó. El arco crece al verla por primera vez y al cambiar de día; al volver a una fila ya
 * vista (p. ej. tras hacer scroll) aparece directamente en su valor.
 */
internal object HomeGaugeMemory {
    private var lastKey: String? = null

    /** true si [key] es distinta de la última que trazó; la guarda para la siguiente vez. */
    @Synchronized
    fun firstPlay(key: String): Boolean {
        if (key == lastKey) return false
        lastKey = key
        return true
    }
}

/** Lo que la fila de chips cruza a cada lado: el margen interior del módulo (20 dp), así el desplazamiento llega al borde del módulo. */
private val ChipsBleed = 20.dp

/** Estira el elemento [edge] a cada lado, hasta el borde del módulo que lo contiene. Ocupa el mismo sitio en su columna. */
private fun Modifier.bleed(edge: Dp): Modifier = layout { measurable, constraints ->
    val extra = edge.roundToPx()
    val width = constraints.maxWidth + 2 * extra
    val placeable = measurable.measure(
        Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = constraints.maxHeight),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

/**
 * La fila de músculos: un chip por músculo, de menor a mayor %, en horizontal (8 dp entre chips) y con 16 dp de aire arriba. Cada chip
 * es un botón que abre [onOpen] (el detalle de Músculos). Si queda contenido por la derecha, un degradado de 24 dp lo indica (I12).
 * Sin músculos no pinta nada.
 */
@Composable
internal fun HomeMuscleChips(
    muscles: List<HomeSessionMuscle>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (muscles.isEmpty()) return
    val reduced = wizardReducedMotion()
    val shown = remember(muscles) { homeMuscleOrder(muscles) }
    val rowKey = shown.joinToString("|") { it.name }
    // Con `key`, cada lista nueva (otro día) recompone la fila y sus arcos empiezan de cero.
    key(rowKey) {
        val play = remember { HomeGaugeMemory.firstPlay(rowKey) && !reduced }
        val scroll = rememberScrollState()
        Box(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bleed(ChipsBleed)
                    .horizontalScroll(scroll)
                    .padding(horizontal = ChipsBleed),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shown.forEach { gauge -> MuscleChip(gauge = gauge, play = play, onOpen = onOpen) }
            }
            // Solo si queda contenido por la derecha: el degradado va sobre el borde del módulo (la fila cruza 20 dp a cada lado).
            // `maxValue` vale Int.MAX_VALUE hasta medir la fila: el guard evita que el degradado asome un cuadro en las filas que caben.
            if (scroll.maxValue != Int.MAX_VALUE && scroll.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = ChipsBleed)
                        .size(width = 24.dp, height = 48.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, KpknSurface.module))),
                )
            }
        }
    }
}

/**
 * Un chip: píldora `inset` de 32 dp con el mini anillo, el nombre y el valor. Su zona táctil mide 48 dp de alto, con la píldora
 * centrada. Sin dato: «—» y el anillo vacío. Con [play], el arco y la cifra suben de 0 al valor en 700 ms. Sin ripple de Material.
 */
@Composable
private fun MuscleChip(gauge: HomeSessionMuscle, play: Boolean, onOpen: () -> Unit) {
    val description = "Músculo previsto en la sesión: ${gauge.name}"
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onOpen,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(KpknShape.pill)
                .background(KpknSurface.inset)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = gauge.name,
                style = KpknType.caption,
                color = WizardColors.text,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )

        }
    }
}

