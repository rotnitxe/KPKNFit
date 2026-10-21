package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.keyDateTypeLabel
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate

/** Los tipos que se eligen en la hoja, en su orden. La competición no se elige aquí: se edita desde el calendario. */
private val KEY_DATE_TYPES = listOf(KeyDateType.EXAMS, KeyDateType.VACATION, KeyDateType.TRAVEL, KeyDateType.CUSTOM)

/** Filas de fecha de la hoja. Solo una puede estar desplegada a la vez. */
private const val ROW_START = "empieza"
private const val ROW_END = "termina"

/**
 * Hoja «Nueva fecha clave» o «Editar fecha clave» (P3a, sustituye al diálogo con fechas ISO escritas a mano): nombre, notas, tipo y las
 * dos fechas con el calendario de mes. «Termina» no puede ser anterior a «Empieza». Guardar devuelve la fecha con sus cambios; Eliminar
 * solo aparece al editar. No hay «Cancelar»: la hoja se cierra con el asa o atrás.
 *
 * Una fecha de competición no se cambia aquí (su texto lo dice), pero sí se puede eliminar desde esta hoja.
 */
@Composable
internal fun ProgramKeyDateSheet(
    keyDate: ProgramKeyDate,
    isNew: Boolean,
    today: LocalDate,
    onSave: (ProgramKeyDate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(keyDate.id) { mutableStateOf(keyDate.title) }
    var notes by remember(keyDate.id) { mutableStateOf(keyDate.notes.orEmpty()) }
    var type by remember(keyDate.id) { mutableStateOf(keyDate.type) }
    var start by remember(keyDate.id) { mutableStateOf(parseIsoDateExact(keyDate.startDate) ?: today) }
    var end by remember(keyDate.id) { mutableStateOf(parseIsoDateExact(keyDate.endDate)) }
    var openRow by rememberSaveable(keyDate.id) { mutableStateOf<String?>(null) }

    val isCompetition = keyDate.type == KeyDateType.COMPETITION
    val endBeforeStart = end?.isBefore(start) == true
    val canSave = title.isNotBlank() && !isCompetition && !endBeforeStart
    val clearEnd: (() -> Unit)? = if (end != null) ({ end = null }) else null

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (isNew) "Nueva fecha clave" else "Editar fecha clave",
                style = KpknType.title,
                color = WizardColors.text,
            )
            Spacer(Modifier.height(16.dp))
            KpknTextField(
                value = title,
                onValueChange = { title = it },
                label = "Nombre",
                placeholder = "Por ejemplo, examen de química",
                singleLine = true,
                enabled = !isCompetition,
            )
            Spacer(Modifier.height(12.dp))
            KpknTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notas",
                placeholder = "Opcional",
                singleLine = false,
                minLines = 2,
                enabled = !isCompetition,
            )
            Spacer(Modifier.height(18.dp))
            if (isCompetition) {
                Text(
                    text = "La fecha de competición se edita desde «Calendario del programa». Aquí solo puedes eliminarla.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            } else {
                ProgramSectionLabel(text = "Tipo")
                Spacer(Modifier.height(8.dp))
                KpknSegmentedControl(
                    options = KEY_DATE_TYPES.map(::keyDateTypeLabel),
                    selectedIndex = KEY_DATE_TYPES.indexOf(type).coerceAtLeast(0),
                    onSelect = { index -> type = KEY_DATE_TYPES[index] },
                    size = KpknSegmentedSize.Compact,
                )
            }
            Spacer(Modifier.height(18.dp))
            ProgramDateRow(
                label = "Empieza",
                selected = start,
                today = today,
                expanded = openRow == ROW_START,
                onToggle = { openRow = if (openRow == ROW_START) null else ROW_START },
                onPick = { picked ->
                    start = picked
                    openRow = null
                },
            )
            ProgramDateRow(
                label = "Termina (opcional)",
                selected = end,
                today = today,
                expanded = openRow == ROW_END,
                onToggle = { openRow = if (openRow == ROW_END) null else ROW_END },
                onPick = { picked ->
                    end = picked
                    openRow = null
                },
                minDate = start,
                emptyValue = "Sin fecha de fin",
                onClear = clearEnd,
            )
            if (endBeforeStart) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "La fecha de fin no puede ser anterior al inicio.",
                    style = KpknType.caption,
                    color = WizardColors.danger,
                )
            }
            Spacer(Modifier.height(22.dp))
            // «Guardar» a todo el ancho; «Eliminar», si la fecha ya existe, es una acción de texto en peligro debajo.
            KpknPrimaryPill(
                text = "Guardar",
                onClick = {
                    onSave(
                        keyDate.copy(
                            title = title.trim(),
                            type = type,
                            startDate = start.toString(),
                            endDate = end?.toString(),
                            eventDate = if (type == KeyDateType.COMPETITION) start.toString() else keyDate.eventDate,
                            notes = notes.trim().ifBlank { null },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
            )
            if (!isNew) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    ProgramDangerAction(text = "Eliminar", onClick = onDelete)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
