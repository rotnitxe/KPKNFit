package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.programdetail.components.LegacySimpleCalendarizationSheet
import dev.chrisbanes.haze.HazeState

/** Simple-program calendarization sheet boundary. */
@Composable
internal fun CalendarSection(
    program: Program,
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
    LegacySimpleCalendarizationSheet(
        program = program,
        onDismiss = onDismiss,
        startDate = startDate,
        onStartDateChange = onStartDateChange,
        endDate = endDate,
        onEndDateChange = onEndDateChange,
        startDayOfWeek = startDayOfWeek,
        onStartDayOfWeekChange = onStartDayOfWeekChange,
        trainingDays = trainingDays,
        onTrainingDaysChange = onTrainingDaysChange,
        onStartBreak = onStartBreak,
        onCalendarizeCycle = onCalendarizeCycle,
        onRecoverCycle = onRecoverCycle,
        onStartFreshCycle = onStartFreshCycle,
        hazeState = hazeState,
    )
}
