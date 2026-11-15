package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.domain.training.LoopPresetPreview
import com.example.kpkn.domain.training.LoopTestMovement
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Preview and confirmation for a generated loop preset. The program is not changed until [onConfirm]. */
@Composable
internal fun LoopPresetPreviewSheet(
    preview: LoopPresetPreview,
    onConfirm: (selectedMovementIds: Set<String>, attemptKgByMovement: Map<String, Double>) -> Unit,
    onDismiss: () -> Unit,
) {
    val isTest = preview.presetId == "1rm-8" || preview.presetId == "deload-1rm"
    val includesDeload = preview.presetId == "deload-4" || preview.presetId == "deload-1rm"
    var selected by remember(preview.presetId, preview.testMovements) { mutableStateOf(emptySet<String>()) }
    val attemptText = remember(preview.presetId, preview.testMovements) {
        mutableStateMapOf<String, String>().apply {
            preview.testMovements.forEach { movement -> movement.referenceKg?.let { put(movement.id, kgText(it)) } }
        }
    }
    val parsedAttempts = preview.testMovements.mapNotNull { movement ->
        attemptText[movement.id]?.trim()?.replace(',', '.')?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }?.let { movement.id to it }
    }.toMap()
    val invalidSelectedWeight = preview.testMovements.any { movement ->
        movement.id in selected && !attemptText[movement.id].isNullOrBlank() && movement.id !in parsedAttempts
    }
    val canSave = preview.cadenceConflicts.isEmpty() && preview.sourceWeekId != null &&
        (!isTest || selected.isNotEmpty()) && !invalidSelectedWeight

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(StructureSheetPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StructureSheetHeader(title = presetTitle(preview.presetId), subtitle = preview.sourceWeekName?.let { "Desde la última semana entrenable · $it" })
            if (preview.sourceWeekId == null) {
                StructureCaption(text = "No hay una semana base con sesiones entrenables para preparar este ciclo.", color = WizardColors.danger)
            }
            if (includesDeload) {
                StructureSheetGroup(text = "Vista previa de la descarga · cada 4 ciclos")
                StructureCaption(
                    text = "${preview.sourceWorkingSets} → ${preview.deloadWorkingSets} series de trabajo · volumen ajustado con el factor 0,55 de descarga.",
                )
                preview.deloadSessions.forEachIndexed { index, session ->
                    StructureMenuRow(
                        icon = KpknLineIcons.Repeat,
                        title = session.name,
                        detail = listOfNotNull(session.dayOfWeek?.let(::dayName), "${session.allExercises().size} ejercicios · ${session.allExercises().sumOf { it.sets.size }} series" ).joinToString(" · "),
                        divider = index > 0,
                        onClick = {},
                        enabled = false,
                    )
                }
            }
            if (isTest) {
                StructureSheetGroup(text = "Movimientos para el test · cada 8 ciclos")
                if (preview.testMovements.isEmpty()) {
                    StructureCaption(text = "La rutina no tiene movimientos principales con una configuración exacta para probar.", color = WizardColors.danger)
                } else {
                    preview.testMovements.forEachIndexed { index, movement ->
                        movementRow(movement, selected = movement.id in selected, divider = index > 0) {
                            selected = if (movement.id in selected) selected - movement.id else selected + movement.id
                        }
                        if (movement.id in selected) {
                            if (movement.referenceKg != null) {
                                StructureTextField(
                                    label = "Intento (kg)",
                                    value = attemptText[movement.id].orEmpty(),
                                    onValueChange = { typed -> attemptText[movement.id] = typed.filter { it.isDigit() || it == '.' || it == ',' } },
                                    placeholder = kgText(movement.referenceKg),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.padding(start = 52.dp),
                                )
                            } else {
                                StructureCaption(
                                    text = "Sin referencia exacta. El peso quedará pendiente y podrás elegirlo al entrenar.",
                                    modifier = Modifier.padding(start = 52.dp),
                                )
                            }
                        }
                    }
                }
                if (selected.isEmpty() && preview.testMovements.isNotEmpty()) {
                    StructureCaption(text = "Elige al menos un movimiento para confirmar el test.")
                }
            }
            preview.collisionNotes.forEach { note -> StructureCaption(text = note) }
            preview.cadenceConflicts.forEach { message -> StructureCaption(text = message, color = WizardColors.danger) }
            StructurePrimaryPill(
                text = "Guardar ciclo listo",
                onClick = { onConfirm(selected, parsedAttempts) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            StructureAction(text = "Cancelar", onClick = onDismiss, faint = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun movementRow(movement: LoopTestMovement, selected: Boolean, divider: Boolean, onClick: () -> Unit) {
    StructureMenuRow(
        icon = if (selected) KpknLineIcons.Check else KpknLineIcons.Target,
        title = movement.exerciseName,
        detail = listOfNotNull(movement.sessionName, movement.dayOfWeek?.let(::dayName), movement.referenceKg?.let { "Referencia ${kgText(it)} kg" })
            .joinToString(" · "),
        divider = divider,
        onClick = onClick,
        trailing = { StructureChip(text = if (selected) "Elegido" else "Probar", selected = selected, onClick = onClick) },
    )
}

private fun presetTitle(id: String): String = when (id) {
    "deload-4" -> "Descarga"
    "1rm-8" -> "Test 1RM"
    "deload-1rm" -> "Descarga + Test 1RM"
    else -> "Preparar ciclo"
}

private fun dayName(day: Int): String = when (day) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Día $day"
}

private fun kgText(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(java.util.Locale.US, "%.1f", kg)
