package com.example.kpkn.screens.home.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/*
 * Músculos declarados en la sesión autorada, sin porcentajes ni anillos. La fila se desplaza en horizontal.
 */

/** A muscle named in the authored session. */
internal data class HomeSessionMuscle(val name: String)

/** Keep the authored order; a name does not imply a recovery measurement. */
internal fun homeMuscleOrder(muscles: List<HomeSessionMuscle>): List<HomeSessionMuscle> =
    muscles.distinctBy { it.name }

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
 * La fila de músculos: un chip por músculo, en el orden del plan, en horizontal (8 dp entre chips) y con 16 dp de aire arriba. Cada chip
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
    val shown = remember(muscles) { homeMuscleOrder(muscles) }
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
                shown.forEach { gauge -> MuscleChip(gauge = gauge, onOpen = onOpen) }
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

/**
 * Nombre del músculo autorado en una zona táctil de48dp; no representa una medición corporal.
 */
@Composable
private fun MuscleChip(gauge: HomeSessionMuscle, onOpen: () -> Unit) {
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

