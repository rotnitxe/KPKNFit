package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.WeekCalendar
import com.example.kpkn.screens.onboarding.effectiveDayPlaces

/**
 * WEEKDAYS · «¿Qué días puedes entrenar?»: el calendario de la semana (de 1 a 7 días), el primer día de la semana y, con
 * dos o más lugares, el lugar de cada día.
 *
 * Lee `state.draft.selectedWeekdays` (y `freshestDay`, `weekStartDay`, `trainingPlaces`, `effectiveDayPlaces()`) y
 * escribe SOLO con `vm.toggleWeekday(day)`, `vm.setWeekStart(day)` y `vm.setDayPlace(day, place)`. El número de días es
 * el de los días elegidos (el motor lo lee de `daysPerWeek`, que el reductor mantiene sincronizado). El inicio de
 * semana sigue al día con más energía mientras la persona no lo mueva (`weekStartDay` ya lo trae resuelto); el día más
 * fuerte lleva su marca en el calendario.
 */
@Composable
internal fun EntrenoWeekdaysStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    WeekCalendar(
        weekStartDay = draft.weekStartDay ?: draft.freshestDay ?: 1,
        selectedDays = draft.selectedWeekdays,
        freshestDay = draft.freshestDay,
        onToggleDay = vm::toggleWeekday,
        onWeekStartChange = vm::setWeekStart,
        places = draft.trainingPlaces,
        // Cada día elegido con su lugar (el primero de la lista mientras no se haya cambiado): el calendario no adivina.
        dayPlaces = draft.effectiveDayPlaces(),
        onDayPlace = vm::setDayPlace,
    )
}
