package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts

/** Shared staged number entry for live List and Cards; changes reach the draft only after Apply. */
@Composable
internal fun WorkoutNumericKeyboardDialog(
    title: String,
    initialValue: String,
    allowDecimal: Boolean,
    allowColon: Boolean = false,
    contextValues: List<Pair<String, String>> = emptyList(),
    activeContextIndex: Int? = null,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue.replace('.', ',')) }
    val keys = buildList {
        addAll(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"))
        add(if (allowColon) ":" else if (allowDecimal) "," else "")
        add("0")
        add("⌫")
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF111111)).padding(18.dp)
                .testTag("workout-numeric-keyboard"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = LiveSheetType.title, color = WizardColors.text)
            if (contextValues.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    contextValues.forEachIndexed { index, (label, current) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B1B1B)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(label, style = LiveSheetType.contextLabel, color = WizardColors.textMuted)
                            Text(
                                if (index == activeContextIndex) value.ifBlank { "—" } else current.ifBlank { "—" },
                                style = LiveSheetType.contextValue,
                                color = if (index == activeContextIndex) WizardColors.text else WizardColors.textMuted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Text(
                value.ifBlank { "—" },
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1B)).padding(12.dp)
                    .testTag("workout-numeric-keyboard-value"),
                style = LiveSheetType.value,
                color = WizardColors.text,
                textAlign = TextAlign.End,
            )
            keys.chunked(3).forEachIndexed { rowIndex, rowKeys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowKeys.forEachIndexed { columnIndex, key ->
                        val shown = key.ifBlank { " " }
                        val action: () -> Unit = {
                            value = when (key) {
                                "⌫" -> value.dropLast(1)
                                "," -> if (',' !in value && '.' !in value) value + ',' else value
                                ":" -> if (':' !in value) value + ':' else value
                                "" -> value
                                else -> value + key
                            }
                        }
                        Text(
                            shown,
                            Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222222)).clickable(enabled = key.isNotBlank(), role = Role.Button, onClick = action)
                                .semantics { contentDescription = if (key == "⌫") "Borrar último dígito" else key }
                                .testTag("workout-numeric-key-$rowIndex-$columnIndex"),
                            style = LiveSheetType.key,
                            color = if (key.isBlank()) Color.Transparent else WizardColors.text,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Limpiar", Modifier.weight(1f).clickable(role = Role.Button) { value = "" }.padding(vertical = 13.dp),
                    style = LiveSheetType.action, color = WizardColors.textMuted, textAlign = TextAlign.Center)
                Text("Cancelar", Modifier.weight(1f).clickable(role = Role.Button, onClick = onDismiss).padding(vertical = 13.dp),
                    style = LiveSheetType.action, color = WizardColors.textMuted, textAlign = TextAlign.Center)
                Text("Aplicar", Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(WizardColors.text)
                    .clickable(role = Role.Button) { onApply(value.replace('.', ',')); onDismiss() }.padding(vertical = 13.dp)
                    .testTag("workout-numeric-keyboard-apply"),
                    style = LiveSheetType.action, color = Color(0xFF111111), textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.size(1.dp))
        }
    }
}

private object LiveSheetType {
    val title = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.display, fontSize = 18.sp)
    val value = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.display, fontSize = 28.sp)
    val key = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.body, fontSize = 18.sp)
    val contextLabel = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.body, fontSize = 10.sp)
    val contextValue = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.display, fontSize = 20.sp)
    val action = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.body, fontSize = 13.sp)
}
