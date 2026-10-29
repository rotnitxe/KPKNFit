package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMonthCalendar
import java.time.LocalDate

/**
 * Campo de fecha de las hojas de Estructura, con el patrón de e-dates: un `.field` con la etiqueta pequeña y la fecha larga («Lunes 6 oct
 * 2026»). Tocarlo despliega debajo el calendario de mes; elegir un día lo fija y el padre lo pliega con [onToggle]. Con [estimate] y sin
 * fecha, la estimación en `--muted`; sin nada, [emptyValue] en `--faint`. [onClear] muestra «Quitar» bajo el calendario cuando hay fecha.
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
    val formatted = selected?.let(::datesFieldLabel)
    val shown = formatted ?: estimate ?: emptyValue
    val shownColor = when {
        formatted != null -> StructureInk
        estimate != null -> StructureMuted
        else -> StructureFaint
    }
    Column(modifier = modifier.fillMaxWidth()) {
        StructureField(
            label = label,
            onClick = onToggle,
            modifier = Modifier.semantics { stateDescription = if (expanded) "Desplegado" else "Plegado" },
            trailing = {
                Icon(
                    imageVector = if (expanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = StructureMuted,
                )
            },
        ) {
            Text(text = shown, style = StructureFieldValueStyle.tabular(), color = shownColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                KpknMonthCalendar(selected = selected, onSelect = onPick, today = today, minDate = minDate)
                if (onClear != null && selected != null) {
                    StructureAction(text = "Quitar", onClick = onClear, faint = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
