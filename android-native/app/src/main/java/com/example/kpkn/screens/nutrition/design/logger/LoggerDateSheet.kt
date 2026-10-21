package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknMonthCalendar
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate

/**
 * Hoja de la fecha de la comida (N3b): un calendario de mes que no deja elegir días futuros ([today] es el último). Elegir un día llama a
 * [onPick], y la hoja la cierra quien la muestra. Sin hora: el registro se guarda a las 12:00, así que no hay nada que elegir.
 */
@Composable
fun LoggerDateSheet(
    selected: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Fecha de la comida", style = KpknType.title, color = WizardColors.text)
            KpknMonthCalendar(
                selected = selected,
                onSelect = onPick,
                today = today,
                maxDate = today,
            )
        }
    }
}
