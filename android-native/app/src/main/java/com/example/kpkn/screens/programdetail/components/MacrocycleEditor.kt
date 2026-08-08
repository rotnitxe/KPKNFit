package com.example.kpkn.screens.programdetail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate

/**
 * Stable entry point for the program-detail macrocycle editor.
 *
 * The implementation lives under `components/editor/` while this façade keeps
 * Navigation and the existing ProgramDetailScreen contract unchanged.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MacrocycleEditor(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    onAddProgramCopy: (Program) -> Unit = {},
    onCompetitionKeyDateSaved: (updatedProgram: Program, keyDate: ProgramKeyDate) -> Unit = { _, _ -> },
    onFocusWeek: (blockId: String, weekId: String) -> Unit = { _, _ -> },
    onCreateSessionForWeek: (weekId: String, preferredDayOfWeek: Int, keyDateId: String?) -> Unit = { _, _, _ -> },
    showSimpleCalendarizationSheet: Boolean = false,
    onShowSimpleCalendarizationSheetChange: (Boolean) -> Unit = {},
    calendarizationStartDate: String = "",
    onCalendarizationStartDateChange: (String) -> Unit = {},
    calendarizationEndDate: String = "",
    onCalendarizationEndDateChange: (String) -> Unit = {},
    calendarizationStartDayOfWeek: Int = 1,
    onCalendarizationStartDayOfWeekChange: (Int) -> Unit = {},
    calendarizationTrainingDays: Set<Int> = emptySet(),
    onCalendarizationTrainingDaysChange: (Set<Int>) -> Unit = {},
    onApplySimpleCalendarizedBreak: () -> Unit = {},
    onCalendarizeSimpleCycle: () -> Unit = {},
    onRecoverCyclicProgram: () -> Unit = {},
    onStartFreshCyclicProgram: () -> Unit = {},
    macrocycleRoadmapExpanded: Boolean? = null,
    onMacrocycleRoadmapExpandedChange: (Boolean) -> Unit = {},
    macrocycleKeyDatesSheetOpen: Boolean? = null,
    onMacrocycleKeyDatesSheetOpenChange: (Boolean) -> Unit = {},
    macrocycleLibrarySheetOpen: Boolean? = null,
    onMacrocycleLibrarySheetOpenChange: (Boolean) -> Unit = {},
    macrocycleLoopsSheetOpen: Boolean? = null,
    onMacrocycleLoopsSheetOpenChange: (Boolean) -> Unit = {},
    macrocycleTimelineStartDate: String? = null,
    onMacrocycleTimelineStartDateChange: (String) -> Unit = {},
    macrocycleManualEndDate: String? = null,
    onMacrocycleManualEndDateChange: (String) -> Unit = {},
    macrocycleCompetitionDate: String? = null,
    onMacrocycleCompetitionDateChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    MacrocycleEditorLegacy(
        program = program,
        onUpdateProgram = onUpdateProgram,
        onAddProgramCopy = onAddProgramCopy,
        onCompetitionKeyDateSaved = onCompetitionKeyDateSaved,
        onFocusWeek = onFocusWeek,
        onCreateSessionForWeek = onCreateSessionForWeek,
        showSimpleCalendarizationSheet = showSimpleCalendarizationSheet,
        onShowSimpleCalendarizationSheetChange = onShowSimpleCalendarizationSheetChange,
        calendarizationStartDate = calendarizationStartDate,
        onCalendarizationStartDateChange = onCalendarizationStartDateChange,
        calendarizationEndDate = calendarizationEndDate,
        onCalendarizationEndDateChange = onCalendarizationEndDateChange,
        calendarizationStartDayOfWeek = calendarizationStartDayOfWeek,
        onCalendarizationStartDayOfWeekChange = onCalendarizationStartDayOfWeekChange,
        calendarizationTrainingDays = calendarizationTrainingDays,
        onCalendarizationTrainingDaysChange = onCalendarizationTrainingDaysChange,
        onApplySimpleCalendarizedBreak = onApplySimpleCalendarizedBreak,
        onCalendarizeSimpleCycle = onCalendarizeSimpleCycle,
        onRecoverCyclicProgram = onRecoverCyclicProgram,
        onStartFreshCyclicProgram = onStartFreshCyclicProgram,
        macrocycleRoadmapExpanded = macrocycleRoadmapExpanded,
        onMacrocycleRoadmapExpandedChange = onMacrocycleRoadmapExpandedChange,
        macrocycleKeyDatesSheetOpen = macrocycleKeyDatesSheetOpen,
        onMacrocycleKeyDatesSheetOpenChange = onMacrocycleKeyDatesSheetOpenChange,
        macrocycleLibrarySheetOpen = macrocycleLibrarySheetOpen,
        onMacrocycleLibrarySheetOpenChange = onMacrocycleLibrarySheetOpenChange,
        macrocycleLoopsSheetOpen = macrocycleLoopsSheetOpen,
        onMacrocycleLoopsSheetOpenChange = onMacrocycleLoopsSheetOpenChange,
        macrocycleTimelineStartDate = macrocycleTimelineStartDate,
        onMacrocycleTimelineStartDateChange = onMacrocycleTimelineStartDateChange,
        macrocycleManualEndDate = macrocycleManualEndDate,
        onMacrocycleManualEndDateChange = onMacrocycleManualEndDateChange,
        macrocycleCompetitionDate = macrocycleCompetitionDate,
        onMacrocycleCompetitionDateChange = onMacrocycleCompetitionDateChange,
        modifier = modifier,
    )
}
