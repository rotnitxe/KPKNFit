package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.screens.programdetail.components.buildCalendarPreview
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockRange
import com.example.kpkn.screens.programdetail.design.structure.ProgramCalendarSheet
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import java.time.LocalDate

/**
 * Hoja «Calendario del programa» (P3a): calcula la vista previa con el motor y se la pasa a la hoja de diseño. Las reglas de guardado
 * son las de siempre (una fecha escrita debe leerse y la competición exige inicio). Las mutaciones siguen siendo callbacks.
 */
@Composable
internal fun KeyDatesSection(
    program: Program,
    today: LocalDate,
    focusKeyDates: Boolean,
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
    val preview = remember(program, timelineStartDate, competitionDate, manualEndDate, today) {
        buildCalendarPreview(program, timelineStartDate, competitionDate, manualEndDate, today)
    }
    val canSaveDates = (timelineStartDate.isBlank() || parseIsoDateExact(timelineStartDate) != null) &&
        (competitionDate.isBlank() || parseIsoDateExact(competitionDate) != null) &&
        (manualEndDate.isBlank() || parseIsoDateExact(manualEndDate) != null)
    val hasRequiredStart = competitionDate.isBlank() || timelineStartDate.isNotBlank()
    ProgramCalendarSheet(
        today = today,
        timelineStartDate = timelineStartDate,
        competitionDate = competitionDate,
        manualEndDate = manualEndDate,
        projectedEnd = preview.programEnd,
        blockRanges = preview.blockStarts.map { block ->
            ProgramBlockRange(name = block.blockName, start = block.startDate, end = block.endDate)
        },
        messages = preview.messages,
        otherKeyDates = otherKeyDates,
        focusKeyDates = focusKeyDates,
        canSave = canSaveDates && hasRequiredStart,
        hasRequiredStart = hasRequiredStart,
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
