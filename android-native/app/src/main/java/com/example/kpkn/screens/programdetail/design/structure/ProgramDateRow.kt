package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.programdetail.formatShortDate
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMonthCalendar
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate

/** Duración del despliegue y del plegado del calendario de una fila: 220 ms, o nada con movimiento reducido. */
private const val ROW_EXPAND_MILLIS = 220

/**
 * Fila de fecha de las hojas del calendario (P3a): la etiqueta y el valor formateado con un icono de flecha. Tocarla despliega debajo el
 * calendario de mes (`KpknMonthCalendar`); elegir un día lo fija y el padre la pliega con [onToggle]. Con [estimate] y sin fecha, la
 * estimación va en gris con «≈»; sin nada, [emptyValue] en `textFaint`. [onClear] muestra «Quitar» cuando hay fecha.
 *
 * Es un botón (`Role.Button`) con su estado («Desplegado» o «Plegado») para TalkBack. Nada de campos de texto ni fechas ISO a la vista.
 */
@Composable
internal fun ProgramDateRow(
    label: String,
    selected: LocalDate?,
    today: LocalDate,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    estimate: String? = null,
    emptyValue: String = "Sin fecha",
    onClear: (() -> Unit)? = null,
) {
    val reduced = wizardReducedMotion()
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = if (reduced) 0 else ROW_EXPAND_MILLIS),
        label = "programDateChevron",
    )
    val enter = if (reduced) EnterTransition.None else expandVertically(tween(ROW_EXPAND_MILLIS)) + fadeIn(tween(ROW_EXPAND_MILLIS))
    val exit = if (reduced) ExitTransition.None else shrinkVertically(tween(ROW_EXPAND_MILLIS)) + fadeOut(tween(ROW_EXPAND_MILLIS))
    val formatted = selected?.let(::formatShortDate)
    val shown = formatted ?: estimate ?: emptyValue
    val shownColor = when {
        formatted != null -> WizardColors.text
        estimate != null -> WizardColors.textMuted
        else -> WizardColors.textFaint
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .semantics { stateDescription = if (expanded) "Desplegado" else "Plegado" }
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
                Text(
                    text = shown,
                    style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
                    color = shownColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClear != null && selected != null) {
                KpknTextAction(text = "Quitar", onClick = onClear)
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                imageVector = KpknLineIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronTurn },
                tint = WizardColors.textMuted,
            )
        }
        AnimatedVisibility(visible = expanded, enter = enter, exit = exit) {
            KpknMonthCalendar(
                selected = selected,
                onSelect = onPick,
                today = today,
                modifier = Modifier.padding(bottom = 8.dp),
                minDate = minDate,
            )
        }
    }
}
