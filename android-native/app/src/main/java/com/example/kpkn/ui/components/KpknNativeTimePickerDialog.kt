package com.example.kpkn.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Official Material3 time picker (clock dial or keyboard [TimeInput]) hosted in [KpknAlertDialog].
 *
 * For rests: pass minutes→[initialHour], seconds→[initialMinute].
 * For session budgets: pass hours→[initialHour], minutes→[initialMinute].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KpknNativeTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
) {
    var useKeyboard by remember { mutableStateOf(false) }
    val pickerState = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = true,
    )

    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(onClick = { useKeyboard = !useKeyboard }) {
                    Icon(
                        imageVector = if (useKeyboard) Icons.Default.Schedule else Icons.Default.Keyboard,
                        contentDescription = if (useKeyboard) "Usar reloj" else "Usar teclado",
                        tint = Color.White,
                    )
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                if (useKeyboard) {
                    TimeInput(state = pickerState)
                } else {
                    TimePicker(state = pickerState)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(pickerState.hour, pickerState.minute)
                },
            ) {
                Text("Aceptar", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.85f))
            }
        },
    )
}
