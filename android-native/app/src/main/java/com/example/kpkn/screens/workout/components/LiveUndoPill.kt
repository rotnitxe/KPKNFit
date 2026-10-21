package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.GodModeUndoSnapshot
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

/** Cuánto se ofrece «Deshacer» tras un cambio de estructura (LV11, B): lo justo para ver el cambio y arrepentirse. */
internal const val UNDO_PILL_VISIBLE_MS = 8_000L

/**
 * La entrada de la pila de deshacer que la píldora puede ofrecer: la última, y solo mientras la sesión siga como estaba al guardarla.
 * Deshacer vuelve la sesión a esa foto, series registradas incluidas; si desde el cambio se registró (o se borró) una serie, la foto ya
 * no las tiene y deshacer se las llevaría por delante: entonces no se ofrece.
 */
internal fun undoPillTarget(
    stack: List<GodModeUndoSnapshot>,
    completedSets: Map<String, CompletedSet>,
): GodModeUndoSnapshot? = stack.lastOrNull()?.takeIf { it.completedSets == completedSets }

/** «Deshacer omitir ejercicio»: el nombre de la acción, en minúscula tras el verbo. */
internal fun undoPillText(label: String): String {
    val action = label.trim().replaceFirstChar { it.lowercase() }
    return if (action.isEmpty()) "Deshacer" else "Deshacer $action"
}

/**
 * «Deshacer <acción>» sobre la barra inferior unos segundos tras cualquier cambio de estructura (desde las opciones o desde reordenar).
 * Solo salta cuando la pila CRECE mientras la pantalla está abierta: lo que ya hubiera al reanudar la sesión no se ofrece. Al deshacer
 * (la pila se acorta) o al registrar una serie, se va. [onUndo] recibe el índice de la entrada (siempre la última).
 */
@Composable
internal fun LiveUndoPillHost(
    undoStack: List<GodModeUndoSnapshot>,
    completedSets: Map<String, CompletedSet>,
    onUndo: (stackIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleMillis: Long = UNDO_PILL_VISIBLE_MS,
    compact: Boolean = false,
) {
    var seenSize by remember { mutableIntStateOf(undoStack.size) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(undoStack.size) {
        if (undoStack.size > seenSize) {
            seenSize = undoStack.size
            shown = true
            delay(visibleMillis)
            shown = false
        } else {
            seenSize = undoStack.size
            shown = false
        }
    }
    val target = if (shown) undoPillTarget(undoStack, completedSets) else null
    var lastText by remember { mutableStateOf("") }
    if (target != null) lastText = undoPillText(target.label)
    val reduced = wizardReducedMotion()
    AnimatedVisibility(
        visible = target != null,
        modifier = modifier,
        enter = when {
            reduced -> fadeIn(tween(0))
            compact -> fadeIn(tween(200))
            else -> fadeIn(tween(200)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 2 }
        },
        exit = when {
            reduced -> fadeOut(tween(0))
            compact -> fadeOut(tween(200))
            else -> fadeOut(tween(200)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it / 2 }
        },
    ) {
        val undo = {
                onUndo(undoStack.lastIndex)
                shown = false
        }
        if (compact) Box(Modifier.size(44.dp).clip(CircleShape).background(WizardColors.text)
            .clickable(role = Role.Button, onClick = undo).semantics { contentDescription = lastText },
            contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(20.dp), tint = WizardColors.ctaContent)
        } else LiveUndoPill(text = lastText, onClick = undo)
    }
}

/** La píldora: tinta con texto negro (la acción principal del momento), de 40 dp de alto y 48 dp de zona táctil. */
@Composable
internal fun LiveUndoPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(KpknShape.pill)
                .background(WizardColors.text, KpknShape.pill)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.action,
                color = WizardColors.ctaContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
