package com.example.kpkn.screens.programdetail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.domain.training.ProgramSnapshot

/**
 * Punto de entrada de la pestaña Estructura de Program Detail. La implementación vive en `components/editor/`; esta fachada mantiene el
 * contrato con ProgramDetailScreen.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MacrocycleEditor(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    onAddProgramCopy: (Program) -> Unit = {},
    onCompetitionKeyDateSaved: (updatedProgram: Program, keyDate: ProgramKeyDate) -> Unit = { _, _ -> },
    onFocusWeek: (blockId: String, weekId: String) -> Unit = { _, _ -> },
    onWeekLongPress: (weekId: String, anchor: Rect) -> Unit = { _, _ -> },
    onApplyProgramTemplateOverwrite: (ProgramTemplateOption, Boolean) -> Unit = { _, _ -> },
    onApplyProtocol: (Protocol, Boolean) -> Unit = { _, _ -> },
    programSnapshots: List<ProgramSnapshot> = emptyList(),
    onRestoreProgramSnapshot: (String) -> Unit = {},
    onRefreshProgramSnapshots: () -> Unit = {},
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
    macrocycleKeyDatesSheetOpen: Boolean? = null,
    onMacrocycleKeyDatesSheetOpenChange: (Boolean) -> Unit = {},
    macrocycleLibrarySheetOpen: Boolean? = null,
    onMacrocycleLibrarySheetOpenChange: (Boolean) -> Unit = {},
    macrocycleLoopsSheetOpen: Boolean? = null,
    onMacrocycleLoopsSheetOpenChange: (Boolean) -> Unit = {},
    onUpdateBlock: (blockId: String, name: String, description: String?, goal: BlockGoal?, progressionScheme: BlockProgressionScheme?) -> Unit =
        { _, _, _, _, _ -> },
    blockProgressionPreview: (blockId: String) -> String? = { null },
    onDeleteBlock: (blockId: String) -> Unit = {},
    onAddSimpleWeek: () -> Unit = {},
    onOpenTrainingMaxes: (() -> Unit)? = null,
    trainingMaxSummary: String? = null,
    modifier: Modifier = Modifier,
) {
    MacrocycleEditorLegacy(
        program = program,
        onUpdateProgram = onUpdateProgram,
        onAddProgramCopy = onAddProgramCopy,
        onCompetitionKeyDateSaved = onCompetitionKeyDateSaved,
        onFocusWeek = onFocusWeek,
        onWeekLongPress = onWeekLongPress,
        onApplyProgramTemplateOverwrite = onApplyProgramTemplateOverwrite,
        onApplyProtocol = onApplyProtocol,
        programSnapshots = programSnapshots,
        onRestoreProgramSnapshot = onRestoreProgramSnapshot,
        onRefreshProgramSnapshots = onRefreshProgramSnapshots,
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
        macrocycleKeyDatesSheetOpen = macrocycleKeyDatesSheetOpen,
        onMacrocycleKeyDatesSheetOpenChange = onMacrocycleKeyDatesSheetOpenChange,
        macrocycleLibrarySheetOpen = macrocycleLibrarySheetOpen,
        onMacrocycleLibrarySheetOpenChange = onMacrocycleLibrarySheetOpenChange,
        macrocycleLoopsSheetOpen = macrocycleLoopsSheetOpen,
        onMacrocycleLoopsSheetOpenChange = onMacrocycleLoopsSheetOpenChange,
        onUpdateBlock = onUpdateBlock,
        blockProgressionPreview = blockProgressionPreview,
        onDeleteBlock = onDeleteBlock,
        onAddSimpleWeek = onAddSimpleWeek,
        onOpenTrainingMaxes = onOpenTrainingMaxes,
        trainingMaxSummary = trainingMaxSummary,
        modifier = modifier,
    )
}
