package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.domain.training.generator.WeekPlanner
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.WeekCalendar
import com.example.kpkn.screens.onboarding.design.entreno.dayFullName
import com.example.kpkn.screens.onboarding.effectiveDayPlaces
import com.example.kpkn.screens.onboarding.orderedWeekdays

/**
 * WEEKDAYS · «¿Qué días puedes entrenar?»: el calendario de la semana (de 1 a 7 días), el primer día de la semana y, con
 * dos o más lugares, el lugar de cada día.
 *
 * Lee `state.draft.selectedWeekdays` (y `freshestDay`, `weekStartDay`, `trainingPlaces`, `effectiveDayPlaces()`) y
 * escribe SOLO con `vm.toggleWeekday(day)`, `vm.setWeekStart(day)` y `vm.setDayPlace(day, place)`. El número de días es
 * el de los días elegidos (el motor lo lee de `daysPerWeek`, que el reductor mantiene sincronizado). El inicio de
 * semana sigue al día con más energía mientras la persona no lo mueva (`weekStartDay` ya lo trae resuelto); el día más
 * fuerte lleva su marca en el calendario. Si ese día se quita de los de entreno, una nota dice adónde pasa la sesión
 * más fuerte ([freshDayNote]).
 */
@Composable
internal fun EntrenoWeekdaysStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    Column(modifier = Modifier.fillMaxWidth()) {
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
        EntrenoStepNote(freshDayNote(draft.freshestDay, draft.orderedWeekdays()))
    }
}

/**
 * El aviso de COPY («Calendario») cuando el día con más energía [freshest] no está entre los días de entreno
 * [orderedDays] (en el orden de la semana de la persona): la sesión más fuerte no puede caer ese día y el generador de
 * programas la pone en el primer día de entreno posterior de la semana. La cuenta es la del propio generador
 * (`WeekPlanner.mainDay`, la que usa para colocar la sesión principal), así que la nota dice lo que de verdad pasará.
 * Los planes de autor no usan este día: la nota habla del programa «a medida», que es el que se arma con él.
 *
 * Null si el día sí se entrena, si todavía no hay día con más energía o si no hay días elegidos.
 */
internal fun freshDayNote(freshest: Int?, orderedDays: List<Int>): String? {
    if (freshest == null || freshest !in 1..7 || orderedDays.isEmpty() || freshest in orderedDays) return null
    val main = WeekPlanner.mainDay(orderedDays, freshest)
    return "Sin entrenar el ${dayFullName(freshest).lowercase()}, tu sesión más fuerte pasa al ${dayFullName(main).lowercase()}."
}
