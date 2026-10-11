package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.screens.onboarding.SetupFormTextField
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

/** Clave del menú de un día abierto en la vista previa (`rememberSaveable`: sobrevive a girar la pantalla). */
internal fun importDayMenuKey(dayId: String): String = "day:$dayId"

/** Clave del menú de una semana abierto en la vista previa. */
internal fun importWeekMenuKey(weekIndex: Int): String = "week:$weekIndex"

/** Clave del panel «Añadir ejercicio» abierto en un día. */
internal fun importAddExerciseKey(dayId: String): String = "add:$dayId"

/**
 * El «⋯» que abre el menú de un día o de una semana: 48 dp de área táctil, `Role.Button` y su estado, «abierto» o «cerrado».
 */
@Composable
internal fun ImportMenuButton(
    open: Boolean,
    description: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(WizardSpacing.touchTarget)
            .testTag(testTag)
            .semantics {
                contentDescription = description
                stateDescription = if (open) "abierto" else "cerrado"
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("⋯", style = WizardTypography.controlLabel, color = WizardColors.text)
    }
}

/**
 * El menú de un día, en línea bajo su nombre: «Renombrar» (con su campo), «Subir» y «Bajar» dentro de la semana, «Unir con el
 * siguiente», «Añadir ejercicio» y «Quitar el día». Cada acción deja deshacer. [onClose] cierra el menú cuando el día cambia
 * de forma que ya no tiene sentido abierto (renombrado, unido o quitado) o cuando pasa a «Añadir ejercicio».
 */
@Composable
internal fun ImportDayMenu(
    day: ImportedDay,
    position: Int,
    dayCount: Int,
    onEdit: (ImportEditOp) -> Unit,
    onAddExercise: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by rememberSaveable(day.id) { mutableStateOf(false) }
    var name by rememberSaveable(day.id) { mutableStateOf(day.name) }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .importGlassPanel()
            .semantics { paneTitle = "Opciones de «${day.name}»" }
            .testTag("import-day-menu"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (renaming) {
            SetupFormTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nombre del día",
                testTag = "import-day-name",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ImportTextAction(
                    label = "Guardar",
                    enabled = name.isNotBlank(),
                    onClick = {
                        onEdit(ImportEditOp.RenameDay(day.id, name))
                        onClose()
                    },
                    modifier = Modifier.testTag("import-day-rename-save"),
                )
                ImportTextAction(
                    label = "Cancelar",
                    onClick = { renaming = false },
                    modifier = Modifier.testTag("import-day-rename-cancel"),
                )
            }
        } else {
            ImportTextAction(
                label = "Renombrar",
                onClick = {
                    name = day.name
                    renaming = true
                },
                modifier = Modifier.testTag("import-day-rename"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ImportTextAction(
                    label = "Subir",
                    enabled = position > 0,
                    onClick = { onEdit(ImportEditOp.MoveDay(day.id, position - 1)) },
                    modifier = Modifier.testTag("import-day-up"),
                )
                ImportTextAction(
                    label = "Bajar",
                    enabled = position < dayCount - 1,
                    onClick = { onEdit(ImportEditOp.MoveDay(day.id, position + 1)) },
                    modifier = Modifier.testTag("import-day-down"),
                )
            }
            ImportTextAction(
                label = "Unir con el siguiente",
                enabled = position < dayCount - 1,
                onClick = {
                    onEdit(ImportEditOp.MergeWithNext(day.id))
                    onClose()
                },
                modifier = Modifier.testTag("import-day-merge"),
            )
            ImportTextAction(
                label = "Añadir ejercicio",
                onClick = onAddExercise,
                modifier = Modifier.testTag("import-day-add-exercise"),
            )
            ImportTextAction(
                label = "Quitar el día",
                color = WizardColors.danger,
                onClick = {
                    onEdit(ImportEditOp.RemoveDay(day.id))
                    onClose()
                },
                modifier = Modifier.testTag("import-day-remove"),
            )
        }
    }
}

/**
 * El menú de una semana, en línea bajo «Semana N»: «Duplicar semana» (la copia queda justo después), «Añadir día» (al final, con
 * el nombre «Día <n+1>») y «Quitar semana», con cuántos días se van. Cada acción deja deshacer.
 */
@Composable
internal fun ImportWeekMenu(
    week: ImportedWeek,
    onEdit: (ImportEditOp) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .importGlassPanel()
            .semantics { paneTitle = "Opciones de la semana ${week.index}" }
            .testTag("import-week-menu-panel"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ImportTextAction(
            label = "Duplicar semana",
            onClick = {
                onEdit(ImportEditOp.DuplicateWeek(week.index))
                onClose()
            },
            modifier = Modifier.testTag("import-week-duplicate"),
        )
        ImportTextAction(
            label = "Añadir día",
            onClick = {
                onEdit(ImportEditOp.AddDay(week.index, ""))
                onClose()
            },
            modifier = Modifier.testTag("import-week-add-day"),
        )
        ImportTextAction(
            label = "Quitar semana",
            color = WizardColors.danger,
            onClick = {
                onEdit(ImportEditOp.RemoveWeek(week.index))
                onClose()
            },
            modifier = Modifier.testTag("import-week-remove"),
        )
        Text(weekRemovalNote(week.days.size), style = WizardTypography.note, color = WizardColors.textMuted)
    }
}

/** Lo que se pierde al quitar la semana, bajo «Quitar semana»: «Se quitan sus N días.». */
internal fun weekRemovalNote(days: Int): String = when (days) {
    0 -> "No tiene días."
    1 -> "Se quita su único día."
    else -> "Se quitan sus $days días."
}
