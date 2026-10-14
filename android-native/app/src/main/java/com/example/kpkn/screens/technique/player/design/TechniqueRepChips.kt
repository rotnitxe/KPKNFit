package com.example.kpkn.screens.technique.player.design

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.screens.technique.player.TechniqueRepMark
import com.example.kpkn.ui.design.KpknChip

/** Etiqueta de prueba de la fila de chips; cada chip lleva la suya con su número (`technique-rep-3`). */
const val TECHNIQUE_REP_CHIPS_TAG = "technique-rep-chips"

/** Etiqueta de prueba del chip de la repetición número [number] (empieza en 1). */
fun techniqueRepChipTag(number: Int): String = "technique-rep-$number"

/**
 * La fila de repeticiones del reproductor (sin estado): un `KpknChip` por repetición, «1 2 3 …», con desplazamiento. Las parciales
 * llevan «·p» («3·p») y las fallidas «·f». Tocar uno llama a [onSelectRep] con su posición en [reps]; la elegida se pinta de tinta y
 * se trae a la vista si estaba fuera.
 *
 * Cada chip mide al menos 48 × 48 dp de zona táctil y entre píldoras hay 12 dp parejos (el aire también es táctil); la fila sale 6 dp
 * por la izquierda para que la primera píldora quede alineada con el margen de la pantalla. Sin repeticiones no pinta nada.
 */
@Composable
fun TechniqueRepChips(
    reps: List<TechniqueRepMark>,
    selectedRep: Int,
    onSelectRep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (reps.isEmpty()) return
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var viewport by remember { mutableIntStateOf(0) }

    // La elegida, a la vista: si no cabe entera, se centra.
    LaunchedEffect(selectedRep, reps.size, viewport) {
        if (selectedRep !in reps.indices || viewport <= 0) return@LaunchedEffect
        val chipPx = with(density) { (ChipSlot + ChipGap).roundToPx() }
        val bleedPx = with(density) { (ChipGap / 2).roundToPx() }
        val fadePx = with(density) { EdgeFade.roundToPx() }
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedRep }
        // «A la vista» deja fuera los bordes difuminados (solo donde la fila sigue) y cuenta con que la fila empieza `bleedPx` por la
        // izquierda del ancho medido: lo que cabe a la derecha es `viewport + bleedPx`.
        val startInset = if (listState.canScrollBackward) fadePx else 0
        val endInset = if (listState.canScrollForward) fadePx else 0
        val fullyVisible = info != null && info.offset >= startInset && info.offset + info.size <= viewport + bleedPx - endInset
        if (!fullyVisible) listState.animateScrollToItem(selectedRep, scrollOffset = -((viewport - chipPx) / 2))
    }

    val showStartFade = listState.canScrollBackward
    val showEndFade = listState.canScrollForward
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TECHNIQUE_REP_CHIPS_TAG)
            .onSizeChanged { viewport = it.width },
    ) {
        LazyRow(
            state = listState,
            contentPadding = contentPadding,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .bleedStart(ChipGap / 2)
                .edgeFades(start = showStartFade, end = showEndFade, width = EdgeFade),
        ) {
            itemsIndexed(reps, key = { index, _ -> index }) { index, rep ->
                RepChip(number = index + 1, rep = rep, isSelected = index == selectedRep, onSelect = { onSelectRep(index) })
            }
        }
    }
}

/** Zona táctil mínima de cada chip. */
private val ChipSlot: Dp = 48.dp

/** Aire entre dos píldoras: 6 dp a cada lado de cada una. También es zona táctil, así que el hueco es parejo con píldoras de ancho distinto. */
private val ChipGap: Dp = 12.dp

/** Ancho del difuminado de los bordes cuando la fila se sale por ese lado. */
private val EdgeFade: Dp = 20.dp

@Composable
private fun RepChip(number: Int, rep: TechniqueRepMark, isSelected: Boolean, onSelect: () -> Unit) {
    val label = when (rep.kind) {
        RepKind.FULL -> "$number"
        RepKind.PARTIAL -> "$number·p"
        RepKind.FAILED -> "$number·f"
    }
    val spoken = when (rep.kind) {
        RepKind.FULL -> "Repetición $number"
        RepKind.PARTIAL -> "Repetición $number, parcial"
        RepKind.FAILED -> "Repetición $number, fallida"
    }
    val latestSelect by rememberUpdatedState(onSelect)
    Box(
        modifier = Modifier
            .testTag(techniqueRepChipTag(number))
            // Un solo nodo para TalkBack: «Repetición 3, parcial», con su estado, en vez del texto «3·p» de la píldora.
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Tab
                selected = isSelected
                onClick(label = "Elegir la repetición") {
                    onSelect()
                    true
                }
            }
            .widthIn(min = ChipSlot)
            .pointerInput(Unit) { detectTapGestures { latestSelect() } }
            .padding(horizontal = ChipGap / 2),
        contentAlignment = Alignment.Center,
    ) {
        KpknChip(text = label, selected = isSelected, onClick = onSelect)
    }
}

/**
 * Saca la fila [amount] por la izquierda sin cambiar su ancho: el aire de la zona táctil del primer chip no debe correr su borde
 * visible respecto al margen de la pantalla (el de la línea de tiempo y los mandos de arriba).
 */
private fun Modifier.bleedStart(amount: Dp): Modifier = layout { measurable, constraints ->
    val bleed = amount.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = if (constraints.minWidth > 0) constraints.minWidth + bleed else 0,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + bleed else constraints.maxWidth,
        ),
    )
    layout(placeable.width - bleed, placeable.height) { placeable.placeRelative(-bleed, 0) }
}

/** Difumina el borde de [width] por cada lado cuyo flag esté activo: la señal de que la fila sigue por ahí. */
private fun Modifier.edgeFades(start: Boolean, end: Boolean, width: Dp): Modifier =
    if (!start && !end) {
        this
    } else {
        this
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = width.toPx()
                if (start) {
                    drawRect(
                        brush = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = fade),
                        size = Size(fade, size.height),
                        blendMode = BlendMode.DstIn,
                    )
                }
                if (end) {
                    drawRect(
                        brush = Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - fade, endX = size.width),
                        topLeft = Offset(size.width - fade, 0f),
                        size = Size(fade, size.height),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
    }
