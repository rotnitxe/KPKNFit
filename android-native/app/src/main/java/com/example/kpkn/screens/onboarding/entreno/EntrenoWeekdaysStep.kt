package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.effectiveDayPlaces

/**
 * WEEKDAYS · «¿Qué días puedes entrenar?»: de 1 a 7 días, el primer día de la semana y, con dos o más lugares, el
 * lugar de cada día.
 *
 * Punto de enganche del control animado (`WeekCalendar`): sustituir el cuerpo de este archivo. Lee
 * `state.draft.selectedWeekdays` (y `freshestDay`, `weekStartDay`, `trainingPlaces`, `placeForDay(day)`) y escribe SOLO
 * con `vm.toggleWeekday(day)`, `vm.setWeekStart(day)` y `vm.setDayPlace(day, place)`. El número de días es el de los
 * días elegidos (el motor lo lee de `daysPerWeek`, que el reductor mantiene sincronizado).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntrenoWeekdaysStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val days = draft.selectedWeekdays
    val weekStart = draft.weekStartDay ?: draft.freshestDay
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..7).forEach { day ->
                EntrenoChip(
                    label = ENTRENO_WEEKDAY_SHORT[day - 1],
                    selected = day in days,
                    onClick = { vm.toggleWeekday(day) },
                    description = ENTRENO_WEEKDAY_NAMES[day - 1],
                    multi = true,
                    tag = "entreno-weekday-$day",
                )
            }
        }
        EntrenoCaption(weekdaysCountText(days.size))
        if (days.isNotEmpty() && draft.freshestDay != null && draft.freshestDay in days) {
            EntrenoCaption("Tu sesión más fuerte: ${ENTRENO_WEEKDAY_NAMES[draft.freshestDay - 1].lowercase()}.")
        }

        EntrenoSectionLabel("La semana empieza el ${weekStart?.let { ENTRENO_WEEKDAY_NAMES[it - 1].lowercase() } ?: "…"}")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..7).forEach { day ->
                EntrenoChip(
                    label = ENTRENO_WEEKDAY_SHORT[day - 1],
                    selected = weekStart == day,
                    onClick = { vm.setWeekStart(day) },
                    description = "Empezar la semana el ${ENTRENO_WEEKDAY_NAMES[day - 1].lowercase()}",
                    tag = "entreno-week-start-$day",
                )
            }
        }

        if (draft.trainingPlaces.size >= 2 && days.isNotEmpty()) {
            val places = TrainingPlace.entries.filter { it in draft.trainingPlaces }
            val byDay = draft.effectiveDayPlaces()
            EntrenoSectionLabel("¿Dónde entrenas ese día?")
            days.sorted().forEach { day ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EntrenoCaption(ENTRENO_WEEKDAY_NAMES[day - 1])
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        places.forEach { place ->
                            EntrenoChip(
                                label = place.label,
                                selected = byDay[day] == place,
                                onClick = { vm.setDayPlace(day, place) },
                                tag = "entreno-day-place-$day-${place.name.lowercase()}",
                            )
                        }
                    }
                }
            }
        }
    }
}

/** «1 día por semana» / «N días por semana»; sin días, la invitación a elegir. */
internal fun weekdaysCountText(count: Int): String =
    if (count <= 0) "Elige entre 1 y 7 días." else "${SpanishPlurals.days(count)} por semana"
