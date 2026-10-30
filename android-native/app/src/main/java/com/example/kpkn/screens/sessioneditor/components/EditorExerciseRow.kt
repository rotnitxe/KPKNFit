package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.frame.EdListIcons
import com.example.kpkn.screens.sessioneditor.frame.EdMotion
import kotlinx.coroutines.withTimeoutOrNull

/*
 * La fila de un ejercicio plegado (`.ed-row` de `ed.css`, Pulido v1 · EDITOR · A2): asa de 44 × 56, miniatura de 40, nombre y dosis,
 * estrella de Meta y flecha, en una fila de 68 dp como mínimo. La usan la lista del editor y la ficha que se levanta al ordenar.
 */

/** El aviso «Ejercicio eliminado · Deshacer» que lee la fila que está pendiente de borrarse (lo pone la lista). */
internal data class EdRemoval(
    val pending: Boolean = false,
    val onUndo: () -> Unit = {},
    val label: String = "Ejercicio eliminado",
)

/** El borrado pendiente de la fila actual: la lista lo provee por ejercicio y la tarjeta lo lee para cambiar su fila por el aviso. */
internal val LocalEdRemoval = compositionLocalOf { EdRemoval() }

/** Hay una fila arrastrándose en la lista: las tarjetas abiertas se pliegan al levantarla (`dragBegin` de `scenes-ed.js`). */
internal val LocalEdListDragging = compositionLocalOf { false }

/** Tiempos del gesto de levantar una fila por el asa: se mantiene 210 ms y, si el dedo se aleja 14 dp antes, no es un arrastre (`scenes-ed.js`, ed-J38). */
internal object EdDragTiming {
    const val HoldMillis = 210L
    val CancelDistance = 14.dp
}

/**
 * Arrastre por el asa (ed-J38): al mantener pulsado 210 ms empieza ([onDragStart] con la posición dentro del asa), después cada movimiento
 * llega a [onDrag] y al soltar, [onDragEnd]. Si el dedo se levanta o se aleja más de 14 dp antes de los 210 ms, no se hace nada y el gesto
 * sigue siendo del scroll de la lista.
 */
internal fun Modifier.edGripDrag(
    key: Any?,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = this.pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val cancelPx = EdDragTiming.CancelDistance.toPx()
        var released = false
        withTimeoutOrNull(EdDragTiming.HoldMillis) {
            while (!released) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.isConsumed || change.changedToUpIgnoreConsumed() ||
                    (change.position - down.position).getDistance() > cancelPx
                ) {
                    released = true
                }
            }
        }
        if (released) return@awaitEachGesture
        onDragStart(down.position)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onDragCancel()
                return@awaitEachGesture
            }
            if (change.changedToUpIgnoreConsumed()) {
                change.consume()
                onDragEnd()
                return@awaitEachGesture
            }
            val delta = change.positionChange()
            if (delta != Offset.Zero) {
                change.consume()
                onDrag(delta)
            }
        }
    }
}

/**
 * Cuerpo de la fila de un ejercicio (`.ed-row`): el asa a la izquierda ([gripModifier] lleva su gesto) y, a su derecha, miniatura, texto,
 * estrella y flecha, que van en [bodyModifier] (el borrado por deslizamiento escucha ahí y no en el asa). [star] nulo = sin estrella (cardio).
 * Todo cuelga de un fondo opaco `module` solo mientras la fila se desliza (lo pone quien la desliza), no aquí.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EdExerciseRow(
    title: AnnotatedString,
    dose: String,
    thumbnail: @Composable () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    gripModifier: Modifier = Modifier,
    gripLifted: Boolean = false,
    bodyModifier: Modifier = Modifier,
    showGrip: Boolean = true,
    starActive: Boolean? = null,
    onStar: () -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    // pulido:transicion ed-T15 .ed-ib2 svg.chev giro 0,26 s --ed-ease · ed-X04 rotate(180deg) con la tarjeta abierta
    val chevronTurn by animateFloatAsState(if (expanded) 180f else 0f, tween(260, easing = EdMotion.Standard), label = "edRowChevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showGrip) {
            Box(
                modifier = gripModifier
                    .size(width = 44.dp, height = 56.dp)
                    .semantics { contentDescription = "Arrastra para reordenar ejercicio" },
                contentAlignment = Alignment.Center,
            ) {
                // pulido:transicion ed-T17 la asa pasa de `dim` a tinta al levantar la fila
                Icon(
                    imageVector = EdListIcons.Grip,
                    contentDescription = null,
                    tint = if (gripLifted) EdColors.ink else EdColors.dim,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Box(Modifier.size(width = 44.dp, height = 56.dp))
        }
        Row(
            modifier = bodyModifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            thumbnail()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                        onLongClick = onLongPress,
                    )
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    text = title,
                    style = edText(14f, FontWeight.SemiBold, EdColors.ink, lineHeight = 17.5f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dose,
                    modifier = Modifier.padding(top = 2.dp),
                    style = edText(12f, FontWeight.Medium, EdColors.muted, lineHeight = 15.6f, tabularNums = true),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                below()
            }
            if (starActive != null) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 56.dp)
                        .clickable(role = Role.Button, onClick = onStar)
                        .semantics { contentDescription = "Meta del ejercicio" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (starActive) EdListIcons.StarOn else EdListIcons.Star,
                        contentDescription = null,
                        tint = if (starActive) SymbolPalette.energia else EdColors.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 56.dp)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { contentDescription = if (expanded) "Plegar" else "Desplegar" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = EdListIcons.Down,
                    contentDescription = null,
                    tint = EdColors.muted,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronTurn },
                )
            }
        }
    }
}

/** Color con el que se pinta lo que cuelga de la fila mientras se desliza: el fondo `module` (#111). */
internal val EdRowSurface = Color(0xFF111111)

/**
 * El nombre de la fila (`.ed-rt strong`): el nombre en tinta y cada chip («· Barra», «· Unilateral») en `muted` y peso medio. Sin nombre,
 * «Seleccionar ejercicio».
 */
internal fun edRowTitle(parentName: String, chips: List<String>, unilateral: Boolean): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = EdColors.ink)) { append(parentName.ifBlank { "Seleccionar ejercicio" }) }
    chips.forEach { chip ->
        withStyle(SpanStyle(color = EdColors.muted, fontWeight = FontWeight.Medium)) {
            append(" · ")
            append(chip)
        }
    }
    // B1: la lateralidad sigue visible también con el marco de superserie.
    if (unilateral) {
        withStyle(SpanStyle(color = EdColors.muted, fontWeight = FontWeight.Medium)) { append(" · Unilateral") }
    }
}
