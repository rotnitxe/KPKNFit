package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Recorrido que hay que pasar para que al soltar se abra el borrado: el mismo umbral que la tarjeta que se desliza en la lista. */
private val SessionSwipeThreshold = 80.dp

/** Lo que se descubre a la derecha al deslizar: el fondo `danger` con la papelera. */
private val SessionSwipeReveal = 112.dp

/**
 * Cabecera de grupo que se borra deslizándola a la izquierda, como un ejercicio (ER2b): el contenido sigue al dedo y deja ver a su derecha
 * el fondo `danger` con la papelera. Al soltar pasado el umbral se abre el diálogo de borrar ([onRequestDelete]) y la cabecera queda
 * descubierta mientras [deleteOpen] esté abierto; al cerrarlo sin borrar, vuelve a su sitio con su nombre.
 */
@Composable
internal fun SessionSwipeDeleteRow(
    deleteOpen: Boolean,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { SessionSwipeThreshold.toPx() }
    val revealPx = with(density) { SessionSwipeReveal.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var thresholdReached by remember { mutableStateOf(false) }
    val latestRequest = rememberUpdatedState(onRequestDelete)

    // Con el diálogo abierto, la cabecera queda descubierta; cerrado sin borrar, vuelve a su sitio.
    LaunchedEffect(deleteOpen) {
        offsetX.animateTo(
            targetValue = if (deleteOpen) -revealPx else 0f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // Igual que la tarjeta compartida: el estado y la acción de borrar se anuncian a TalkBack.
            .semantics {
                stateDescription = if (offsetX.value <= -thresholdPx) {
                    "Acción revelada"
                } else {
                    "Desliza a la izquierda para eliminar"
                }
                customActions = listOf(
                    CustomAccessibilityAction("Eliminar grupo") {
                        latestRequest.value()
                        true
                    },
                )
            },
    ) {
        // El fondo `danger` con la papelera: lo que se descubre al deslizar. En reposo queda invisible, para que su borde
        // antialiasado no asome por las esquinas redondeadas del contenido (ER2b).
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (offsetX.value <= -1f) 1f else 0f }
                .background(WizardColors.danger),
            contentAlignment = Alignment.CenterEnd,
        ) {
            KpknIconButton(
                icon = KpknLineIcons.Trash,
                contentDescription = "Eliminar",
                onClick = onRequestDelete,
                modifier = Modifier.padding(end = 12.dp),
                tint = WizardColors.ctaContent,
            )
        }

        // La cabecera, que se desliza sobre ese fondo. Solo se mueve en horizontal: la lista sigue bajando con el dedo.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val next = (offsetX.value + delta).coerceIn(-revealPx, 0f)
                        scope.launch { offsetX.snapTo(next) }
                        val reached = next <= -thresholdPx
                        if (reached != thresholdReached) {
                            thresholdReached = reached
                            if (reached) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragStopped = {
                        thresholdReached = false
                        val settle = spring<Float>(dampingRatio = 0.78f, stiffness = 650f)
                        if (offsetX.value <= -thresholdPx) {
                            latestRequest.value()
                            scope.launch { offsetX.animateTo(-revealPx, settle) }
                        } else {
                            scope.launch { offsetX.animateTo(0f, settle) }
                        }
                    },
                ),
        ) {
            content()
        }
    }
}
