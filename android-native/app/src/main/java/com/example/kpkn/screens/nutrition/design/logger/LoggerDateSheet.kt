package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.kpkn.domain.nutrition.MealTime
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknPrimaryPill
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknMonthCalendar
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate

/**
 * Hoja de la fecha de la comida (N3b): un calendario de mes que no deja elegir días futuros ([today] es el último). Elegir un día llama a
 * [onPick], y la hoja la cierra quien la muestra. La hora local se puede editar sin cambiar el día.
 */
@Composable
fun LoggerDateSheet(
    selected: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    selectedTime: String = "12:00",
    onTimePick: (String) -> Unit = {},
) {
    var time by remember(selectedTime) { mutableStateOf(selectedTime) }
    val validTime = MealTime.parse(time) != null
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
            KpknTextField(time, { time = it }, label = "Hora (HH:mm)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                error = if (validTime) null else "Escribe una hora entre 00:00 y 23:59.")
            KpknPrimaryPill("Usar esta hora", { if (validTime) onTimePick(time) }, enabled = validTime,
                modifier = Modifier.fillMaxWidth())
        }
    }
}
