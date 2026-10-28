package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.keyDateTypeLabel
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import com.example.kpkn.ui.components.KpknSheet
import java.time.LocalDate

/** Los tipos que se eligen en la hoja, en su orden. La competición no se elige aquí: se fija en la hoja «Fechas». */
private val KEY_DATE_TYPES = listOf(KeyDateType.EXAMS, KeyDateType.VACATION, KeyDateType.TRAVEL, KeyDateType.CUSTOM)

/** Campos de fecha de la hoja. Solo uno puede estar desplegado a la vez. */
private const val ROW_START = "empieza"
private const val ROW_END = "termina"

/**
 * Hoja «Nueva fecha clave» o la de una fecha ya puesta, con el patrón de e-dates y e-block (el lienzo no la dibuja): «Nombre» y «Nota»,
 * «Tipo» con chips, «Empieza» y «Termina» como campos con el calendario de mes, «Guardar» y, al editar, «Eliminar fecha clave» en
 * `--danger`. «Termina» no puede ser anterior a «Empieza». Una fecha de competición no se cambia aquí (su texto lo dice), pero sí se elimina.
 */
@OptIn(ExperimentalLayoutApi::class)
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

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(StructureSheetPadding),
        ) {
            StructureSheetHeader(
                title = if (isNew) "Nueva fecha clave" else keyDate.title.ifBlank { "Fecha clave" },
                subtitle = if (isCompetition) "La competición se fija en «Fechas». Aquí solo puedes eliminarla." else null,
            )
            StructureTextField(
                label = "Nombre",
                value = title,
                onValueChange = { title = it },
                placeholder = "Por ejemplo, examen de química",
                enabled = !isCompetition,
            )
            StructureTextField(
                label = "Nota",
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Opcional",
                singleLine = false,
                enabled = !isCompetition,
            )
            if (!isCompetition) {
                StructureSheetGroup(text = "Tipo")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KEY_DATE_TYPES.forEach { option ->
                        StructureChip(text = keyDateTypeLabel(option), selected = type == option, onClick = { type = option })
                    }
                }
            }
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
                label = "Termina",
                selected = end,
                today = today,
                expanded = openRow == ROW_END,
                onToggle = { openRow = if (openRow == ROW_END) null else ROW_END },
                onPick = { picked ->
                    end = picked
                    openRow = null
                },
                minDate = start,
                emptyValue = "Opcional",
                onClear = if (end != null) ({ end = null }) else null,
            )
            if (endBeforeStart) {
                StructureCaption(
                    text = "La fecha de fin no puede ser anterior al inicio.",
                    color = WizardColors.danger,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!isCompetition) {
                StructurePrimaryPill(
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
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                )
            }
            if (!isNew) {
                StructureDangerAction(text = "Eliminar fecha clave", onClick = onDelete)
            }
        }
    }
}
