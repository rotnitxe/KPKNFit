package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel

/** Nombre completo y abreviatura de cada día (1 = lunes … 7 = domingo) para los controles provisionales. */
internal val ENTRENO_WEEKDAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
internal val ENTRENO_WEEKDAY_SHORT = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/**
 * FRESH_DAY · «¿Qué día llegas con más energía?»: uno de los siete días. Ahí cae la sesión más fuerte y, mientras no
 * se mueva el inicio de semana, es el primer día de la semana.
 *
 * Punto de enganche del control animado (`FreshDayRow`): sustituir el cuerpo de este archivo. Lee
 * `state.draft.freshestDay` y escribe SOLO con `vm.setFreshDay(day)` (1 = lunes … 7 = domingo).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntrenoFreshDayStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val selected = state.draft.freshestDay
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..7).forEach { day ->
                EntrenoChip(
                    label = ENTRENO_WEEKDAY_SHORT[day - 1],
                    selected = selected == day,
                    onClick = { vm.setFreshDay(day) },
                    description = ENTRENO_WEEKDAY_NAMES[day - 1],
                    tag = "entreno-fresh-day-$day",
                )
            }
        }
        EntrenoCaption("También será el primer día de tu semana.")
    }
}
