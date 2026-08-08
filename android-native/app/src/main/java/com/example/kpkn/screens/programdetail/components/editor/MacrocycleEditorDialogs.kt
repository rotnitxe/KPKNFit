package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.screens.programdetail.components.LegacyBlockDialog
import com.example.kpkn.screens.programdetail.components.LegacyKeyDateDialog
import com.example.kpkn.screens.programdetail.components.LegacyMesoDialog
import com.example.kpkn.screens.programdetail.components.LegacyWeekDialog
import com.example.kpkn.screens.programdetail.components.LegacyWeekMetadataDialog

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

@Composable
internal fun KeyDateDialog(
    keyDate: ProgramKeyDate,
    onSave: (ProgramKeyDate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) = LegacyKeyDateDialog(
    keyDate = keyDate,
    onSave = onSave,
    onDelete = onDelete,
    onDismiss = onDismiss,
)
