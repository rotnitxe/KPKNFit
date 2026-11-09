package com.example.kpkn.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts

/** Tipografía numérica local: evita heredar Syne y mantiene dígitos tabulares al tamaño accesible del sistema. */
internal fun timePickerNumericTypography(base: Typography): Typography {
    return base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = WizardFonts.body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 48.sp,
            lineHeight = 56.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = "'tnum' 1",
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = WizardFonts.body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 40.sp,
            lineHeight = 48.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = "'tnum' 1",
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = WizardFonts.body,
            fontWeight = FontWeight.Medium,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = "'tnum' 1",
        ),
    )
}

/**
 * Gives the Material picker the dialog panel width. KpknAlertDialog reserves 24 dp per side for ordinary text; reclaim that space for
 * the clock and keyboard so both time fields remain inside the panel at large font scales.
 */
private fun Modifier.timePickerPanelWidthBleed() = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val child = measurable.measure(constraints)
        layout(child.width, child.height) { child.placeRelative(0, 0) }
    } else {
        val bleed = 24.dp.roundToPx()
        val expandedWidth = constraints.maxWidth + bleed * 2
        val child = measurable.measure(constraints.copy(minWidth = expandedWidth, maxWidth = expandedWidth))
        layout(constraints.maxWidth, child.height) { child.placeRelative(-bleed, 0) }
    }
}

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
    val numericTypography = timePickerNumericTypography(MaterialTheme.typography)
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
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .timePickerPanelWidthBleed(),
                ) {
                    MaterialTheme(typography = numericTypography) {
                        if (useKeyboard) {
                            TimeInput(
                                state = pickerState,
                                modifier = Modifier.fillMaxWidth().testTag("native-time-picker-keyboard"),
                            )
                        } else {
                            TimePicker(
                                state = pickerState,
                                modifier = Modifier.fillMaxWidth().testTag("native-time-picker-dial"),
                            )
                        }
                    }
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

/**
 * Rest picker composed entirely inside the KPKN glass host.
 *
 * The second picker is shown only after the first value is confirmed. This
 * preserves the old two-step rest flow without creating platform windows that
 * cannot sample the editor blur surface.
 */
@Composable
fun KpknRestPickerChain(
    primaryTitle: String,
    primarySeconds: Int,
    secondaryTitle: String? = null,
    secondarySeconds: Int? = null,
    onConfirm: (primarySeconds: Int, secondarySeconds: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasSecondary = secondaryTitle != null && secondarySeconds != null
    var step by remember(primaryTitle, primarySeconds, secondaryTitle, secondarySeconds) {
        mutableStateOf(0)
    }
    var chosenPrimarySeconds by remember(primaryTitle, primarySeconds, secondaryTitle, secondarySeconds) {
        mutableStateOf<Int?>(null)
    }

    if (step == 0) {
        KpknNativeTimePickerDialog(
            title = primaryTitle,
            initialHour = (primarySeconds.coerceAtLeast(0) / 60).coerceIn(0, 23),
            initialMinute = (primarySeconds.coerceAtLeast(0) % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { minutes, seconds ->
                val selected = (minutes * 60 + seconds).coerceAtLeast(0)
                if (hasSecondary) {
                    chosenPrimarySeconds = selected
                    step = 1
                } else {
                    onConfirm(selected, null)
                }
            },
            onDismiss = onDismiss,
        )
    } else {
        KpknNativeTimePickerDialog(
            title = secondaryTitle.orEmpty(),
            initialHour = (secondarySeconds.orZero() / 60).coerceIn(0, 23),
            initialMinute = (secondarySeconds.orZero() % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { minutes, seconds ->
                val selected = (minutes * 60 + seconds).coerceAtLeast(0)
                onConfirm(chosenPrimarySeconds ?: primarySeconds.coerceAtLeast(0), selected)
            },
            onDismiss = onDismiss,
        )
    }
}

private fun Int?.orZero(): Int = this ?: 0
