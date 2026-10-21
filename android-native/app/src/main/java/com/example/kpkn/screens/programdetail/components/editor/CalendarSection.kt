package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.screens.programdetail.design.structure.ProgramSimpleCalendarSheet
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate

/**
 * Hoja de calendarización de un programa simple (P3a): la piel nueva. Decide su estado (calendarizado o no) y si hay ciclo guardado a
 * partir del programa; los callbacks y los datos de los borradores son los de siempre.
 */
@Composable
internal fun CalendarSection(
    program: Program,
    today: LocalDate,
    onDismiss: () -> Unit,
    startDate: String,
    onStartDateChange: (String) -> Unit,
    endDate: String,
    onEndDateChange: (String) -> Unit,
    startDayOfWeek: Int,
    onStartDayOfWeekChange: (Int) -> Unit,
    trainingDays: Set<Int>,
    onTrainingDaysChange: (Set<Int>) -> Unit,
    onStartBreak: () -> Unit,
    onCalendarizeCycle: () -> Unit,
    onRecoverCycle: () -> Unit,
    onStartFreshCycle: () -> Unit,
    hazeState: HazeState,
) {
    val isCalendarized = program.simpleProgramKind == SimpleProgramKind.CALENDARIZED &&
        program.calendarization?.mode == ProgramCalendarizationMode.SIMPLE_DATED
    ProgramSimpleCalendarSheet(
        today = today,
        isCalendarized = isCalendarized,
        startDate = startDate,
        endDate = endDate,
        startDayOfWeek = startDayOfWeek,
        trainingDays = trainingDays,
        hasSavedCycle = program.pausedCyclicSnapshot != null,
        onStartDateChange = onStartDateChange,
        onEndDateChange = onEndDateChange,
        onStartDayOfWeekChange = onStartDayOfWeekChange,
        onTrainingDaysChange = onTrainingDaysChange,
        onStartBreak = onStartBreak,
        onCalendarizeCycle = onCalendarizeCycle,
        onRecoverCycle = onRecoverCycle,
        onStartFreshCycle = onStartFreshCycle,
        onDismiss = onDismiss,
        hazeState = hazeState,
    )
}
