package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import kotlin.math.max

/** Estado de una serie en la tira: hecha, la que toca ahora o pendiente. */
enum class WorkoutSetDotState { DONE, CURRENT, FUTURE }

/**
 * Una serie de la tira. [label] es el número que se ve y se lee («Serie 1»). [caption] es el dato de la serie, que se ve debajo
 * («80 × 8») y se lee «80 kilos por 8»; sin dato, es nulo.
 */
data class WorkoutSetDot(val id: String, val label: String, val state: WorkoutSetDotState, val caption: String?)

/**
 * Tira de series (KPKN LS1): un círculo de 44 dp por serie, con 10 dp de aire entre ellos (cada uno toca en 48 dp).
 * - Hecha: relleno `done` al 16 % y un check en `done`.
 * - La actual: anillo de 2 dp en tinta, con su número en negrita.
 * - Pendiente: anillo de 1,5 dp en `divider`, con su número apagado.
 *
 * Con [onAdd], un «+» al final. Con más series de las que caben, la actual queda a la vista (sin animación con movimiento reducido).
 * Tocar un círculo llama a [onOpen] con su id.
 */
@Composable
fun WorkoutSetStrip(
    dots: List<WorkoutSetDot>,
    onOpen: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
) {
    val reduced = wizardReducedMotion()
    val listState = rememberLazyListState()
    val currentIndex = dots.indexOfFirst { it.state == WorkoutSetDotState.CURRENT }
    // Deja una serie de margen a la izquierda para ver de dónde viene la actual.
    LaunchedEffect(currentIndex, dots.size) {
        if (currentIndex >= 0) {
            val target = max(0, currentIndex - 1)
            if (reduced) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }
    LazyRow(
        state = listState,
        modifier = modifier.fadeScrollableEdges(listState),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        items(dots, key = { it.id }) { dot ->
            SetDotItem(dot = dot, onClick = { onOpen(dot.id) })
        }
        if (onAdd != null) {
            item(key = "add-serie") {
                AddDotItem(onClick = onAdd)
            }
        }
    }
}

/** Un círculo con su dato debajo. Toda la columna es un botón y se lee como una frase. */
@Composable
private fun SetDotItem(dot: WorkoutSetDot, onClick: () -> Unit) {
    val description = dotDescription(dot)
    Column(
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                this.contentDescription = description
                role = Role.Button
            }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(
                        when (dot.state) {
                            WorkoutSetDotState.DONE -> Modifier.background(WizardColors.done.copy(alpha = 0.16f), CircleShape)
                            WorkoutSetDotState.CURRENT -> Modifier.border(2.dp, WizardColors.text, CircleShape)
                            WorkoutSetDotState.FUTURE -> Modifier.border(1.5.dp, WizardColors.divider, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when (dot.state) {
                    WorkoutSetDotState.DONE -> Icon(
                        imageVector = KpknLineIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = WizardColors.done,
                    )
                    WorkoutSetDotState.CURRENT -> Text(
                        text = dot.label,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                        maxLines = 1,
                    )
                    WorkoutSetDotState.FUTURE -> Text(
                        text = dot.label,
                        style = KpknType.bodyStrong,
                        color = WizardColors.textFaint,
                        maxLines = 1,
                    )
                }
            }
        }
        dot.caption?.let { caption ->
            Text(
                text = caption,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** El «+» de añadir serie: un anillo de pendiente con el icono en `textMuted`. */
@Composable
private fun AddDotItem(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                this.contentDescription = "Añadir serie"
                role = Role.Button
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .border(1.5.dp, WizardColors.divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = KpknLineIcons.Plus,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = WizardColors.textMuted,
            )
        }
    }
}

/** Lo que se lee de una serie: «Serie 1, hecha, 80 kilos por 8», «Serie 2, la actual», «Serie 3, pendiente». */
private fun dotDescription(dot: WorkoutSetDot): String {
    val state = when (dot.state) {
        WorkoutSetDotState.DONE -> "hecha"
        WorkoutSetDotState.CURRENT -> "la actual"
        WorkoutSetDotState.FUTURE -> "pendiente"
    }
    val parts = listOfNotNull("Serie ${dot.label}", state, dot.caption?.let { spokenCaption(it) })
    return parts.joinToString(", ")
}

/** «80 × 8» se lee «80 kilos por 8»: la tira muestra carga por repeticiones. */
private fun spokenCaption(caption: String): String = caption.replace(" × ", " kilos por ")
