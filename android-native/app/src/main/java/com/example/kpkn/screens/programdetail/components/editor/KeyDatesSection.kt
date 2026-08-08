package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.screens.programdetail.components.LegacyKeyDatesManagementSheet

/** Key-date/calendar sheet boundary. Mutations remain callback-driven. */
@Composable
internal fun KeyDatesSection(
    program: Program,
    timelineStartDate: String,
    competitionDate: String,
    manualEndDate: String,
    otherKeyDates: List<ProgramKeyDate>,
    onTimelineStartDateChange: (String) -> Unit,
    onCompetitionDateChange: (String) -> Unit,
    onManualEndDateChange: (String) -> Unit,
    onAddOtherKeyDate: () -> Unit,
    onEditOtherKeyDate: (ProgramKeyDate) -> Unit,
    onDeleteOtherKeyDate: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    LegacyKeyDatesManagementSheet(
        program = program,
        timelineStartDate = timelineStartDate,
        competitionDate = competitionDate,
        manualEndDate = manualEndDate,
        otherKeyDates = otherKeyDates,
        onTimelineStartDateChange = onTimelineStartDateChange,
        onCompetitionDateChange = onCompetitionDateChange,
        onManualEndDateChange = onManualEndDateChange,
        onAddOtherKeyDate = onAddOtherKeyDate,
        onEditOtherKeyDate = onEditOtherKeyDate,
        onDeleteOtherKeyDate = onDeleteOtherKeyDate,
        onSave = onSave,
        onDismiss = onDismiss,
    )
}
