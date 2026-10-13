package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.screens.programdetail.components.LegacyBlockDialog
import com.example.kpkn.screens.programdetail.components.LegacyMesoDialog
import com.example.kpkn.screens.programdetail.components.LegacyWeekDialog
import com.example.kpkn.screens.programdetail.components.LegacyWeekMetadataDialog
import com.example.kpkn.screens.programdetail.design.structure.ProgramKeyDateSheet
import java.time.LocalDate

/** Dialog boundary for block, mesocycle, week and key-date edits. */
@Composable
internal fun BlockDialog(
    block: Block?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) = LegacyBlockDialog(block = block, onSave = onSave, onDismiss = onDismiss)

@Composable
internal fun MesoDialog(
    meso: Mesocycle?,
    canDelete: Boolean = false,
    onSave: (String, MesocycleGoal) -> Unit,
    onDelete: () -> Unit = {},
    onDismiss: () -> Unit,
) = LegacyMesoDialog(
    meso = meso,
    canDelete = canDelete,
    onSave = onSave,
    onDelete = onDelete,
    onDismiss = onDismiss,
)

@Composable
internal fun WeekDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) = LegacyWeekDialog(onSave = onSave, onDismiss = onDismiss)

@Composable
internal fun WeekMetadataEditorDialog(
    week: ProgramWeek,
    canDelete: Boolean,
    onSave: (String, String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) = LegacyWeekMetadataDialog(
    week = week,
    canDelete = canDelete,
    onSave = onSave,
    onDelete = onDelete,
    onDismiss = onDismiss,
)

/** Hoja de fecha clave (P3a): nueva o de edición según [isNew]; [today] es el día de hoy de la pantalla. */
@Composable
internal fun KeyDateDialog(
    keyDate: ProgramKeyDate,
    isNew: Boolean,
    today: LocalDate,
    onSave: (ProgramKeyDate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) = ProgramKeyDateSheet(
    keyDate = keyDate,
    isNew = isNew,
    today = today,
    onSave = onSave,
    onDelete = onDelete,
    onDismiss = onDismiss,
)
