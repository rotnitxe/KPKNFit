package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts

internal data class WorkoutKeyboardField(
    val label: String,
    val title: String,
    val initialValue: String,
    val allowDecimal: Boolean,
    val allowColon: Boolean = false,
    val stepDelta: Double? = null,
)

private fun stepKeyboardValue(currentRaw: String, delta: Double, allowColon: Boolean, allowDecimal: Boolean): String {
    if (allowColon) {
        val parts = currentRaw.trim().split(':')
        val currentSeconds = when (parts.size) {
            1 -> parts[0].replace(',', '.').toDoubleOrNull()?.toInt() ?: 0
            2 -> {
                val mins = parts[0].toIntOrNull()
                val secs = parts[1].toIntOrNull()
                if (mins != null && secs != null && mins >= 0 && secs in 0..59) mins * 60 + secs else 0
            }
            else -> 0
        }
        val next = (currentSeconds + delta.toInt()).coerceAtLeast(0)
        return if (':' in currentRaw || next >= 60) "${next / 60}:${(next % 60).toString().padStart(2, '0')}" else next.toString()
    }
    val current = currentRaw.replace(',', '.').toDoubleOrNull() ?: 0.0
    val next = (current + delta).coerceAtLeast(0.0)
    return if (!allowDecimal) {
        kotlin.math.round(next).toInt().toString()
    } else {
        val rounded = kotlin.math.round(next * 1000.0) / 1000.0
        if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            rounded.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', ',')
        }
    }
}

private fun formatKeyboardStepPill(step: Double, allowColon: Boolean, signPrefix: String): String {
    if (allowColon) return "$signPrefix${step.toInt()}s"
    return if (step % 1.0 == 0.0) "$signPrefix${step.toInt()}" else "$signPrefix${step.toString().replace('.', ',')}"
}

/** Shared staged number entry for live List and Cards; changes reach the draft only after Apply. */
@Composable
internal fun WorkoutNumericKeyboardDialog(
    title: String,
    initialValue: String,
    allowDecimal: Boolean,
    allowColon: Boolean = false,
    contextValues: List<Pair<String, String>> = emptyList(),
    activeContextIndex: Int? = null,
    fields: List<WorkoutKeyboardField> = emptyList(),
    initialFieldIndex: Int = 0,
    onApplyFields: ((List<String>) -> Unit)? = null,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val multiField = fields.isNotEmpty()
    var activeFieldIndex by remember(fields, initialFieldIndex) {
        mutableIntStateOf(if (multiField) initialFieldIndex.coerceIn(0, fields.lastIndex) else 0)
    }
    val stagedValues = remember(fields) {
        mutableStateListOf<String>().apply {
            fields.forEach { add(it.initialValue.replace('.', ',')) }
        }
    }
    var singleValue by remember(title, initialValue) { mutableStateOf(initialValue.replace('.', ',')) }

    val activeField = if (multiField) fields[activeFieldIndex] else null
    val activeTitle = activeField?.title ?: title
    val activeValue = if (multiField) stagedValues[activeFieldIndex] else singleValue
    val activeAllowDecimal = activeField?.allowDecimal ?: allowDecimal
    val activeAllowColon = activeField?.allowColon ?: allowColon

    fun updateActiveValue(next: String) {
        if (multiField) {
            stagedValues[activeFieldIndex] = next
        } else {
            singleValue = next
        }
    }

    val keys = buildList {
        addAll(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"))
        add(if (activeAllowColon) ":" else if (activeAllowDecimal) "," else "")
        add("0")
        add("⌫")
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF111111))
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
                .testTag("workout-numeric-keyboard"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(activeTitle, style = LiveSheetType.title, color = WizardColors.text)
            if (multiField) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fields.forEachIndexed { index, field ->
                        val selected = index == activeFieldIndex
                        val shown = stagedValues.getOrElse(index) { "" }.ifBlank { "—" }
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color(0xFF262626) else Color(0xFF1B1B1B))
                                .border(
                                    width = if (selected) 1.dp else 0.5.dp,
                                    color = if (selected) WizardColors.text.copy(alpha = 0.72f) else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable(role = Role.Button) { activeFieldIndex = index }
                                .semantics { contentDescription = "Editar ${field.label}" }
                                .testTag("workout-numeric-keyboard-tab-$index")
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                field.label,
                                style = LiveSheetType.contextLabel.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (selected) WizardColors.text else WizardColors.textMuted,
                            )
                            Text(
                                shown,
                                style = LiveSheetType.contextValue,
                                color = if (selected) WizardColors.text else WizardColors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else if (contextValues.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    contextValues.forEachIndexed { index, (label, current) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B1B1B)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(label, style = LiveSheetType.contextLabel, color = WizardColors.textMuted)
                            Text(
                                if (index == activeContextIndex) activeValue.ifBlank { "—" } else current.ifBlank { "—" },
                                style = LiveSheetType.contextValue,
                                color = if (index == activeContextIndex) WizardColors.text else WizardColors.textMuted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Text(
                activeValue.ifBlank { "—" },
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1B)).padding(12.dp)
                    .testTag("workout-numeric-keyboard-value"),
                style = LiveSheetType.value,
                color = WizardColors.text,
                textAlign = TextAlign.End,
            )
            val quickStep = activeField?.stepDelta ?: when {
                activeAllowColon -> 5.0
                activeAllowDecimal -> 2.5
                else -> 1.0
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    formatKeyboardStepPill(quickStep, activeAllowColon, "−"),
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1D1D1D))
                        .clickable(role = Role.Button) {
                            updateActiveValue(stepKeyboardValue(activeValue, -quickStep, activeAllowColon, activeAllowDecimal))
                        }
                        .semantics { contentDescription = "Disminuir ${formatKeyboardStepPill(quickStep, activeAllowColon, "")}" }
                        .testTag("workout-numeric-quick-minus")
                        .padding(vertical = 6.dp),
                    style = LiveSheetType.action.copy(fontWeight = FontWeight.SemiBold),
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                )
                Text(
                    formatKeyboardStepPill(quickStep, activeAllowColon, "+"),
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1D1D1D))
                        .clickable(role = Role.Button) {
                            updateActiveValue(stepKeyboardValue(activeValue, quickStep, activeAllowColon, activeAllowDecimal))
                        }
                        .semantics { contentDescription = "Aumentar ${formatKeyboardStepPill(quickStep, activeAllowColon, "")}" }
                        .testTag("workout-numeric-quick-plus")
                        .padding(vertical = 6.dp),
                    style = LiveSheetType.action.copy(fontWeight = FontWeight.SemiBold),
                    color = WizardColors.text,
                    textAlign = TextAlign.Center,
                )
            }
            keys.chunked(3).forEachIndexed { rowIndex, rowKeys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowKeys.forEachIndexed { columnIndex, key ->
                        val shown = key.ifBlank { " " }
                        val action: () -> Unit = {
                            updateActiveValue(
                                when (key) {
                                    "⌫" -> activeValue.dropLast(1)
                                    "," -> if (',' !in activeValue && '.' !in activeValue) activeValue + ',' else activeValue
                                    ":" -> if (':' !in activeValue) activeValue + ':' else activeValue
                                    "" -> activeValue
                                    else -> activeValue + key
                                },
                            )
                        }
                        Text(
                            shown,
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
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
                Text("Limpiar", Modifier.weight(1f).clickable(role = Role.Button) { updateActiveValue("") }.padding(vertical = 10.dp),
                    style = LiveSheetType.action, color = WizardColors.textMuted, textAlign = TextAlign.Center)
                Text("Cancelar", Modifier.weight(1f).clickable(role = Role.Button, onClick = onDismiss).padding(vertical = 10.dp),
                    style = LiveSheetType.action, color = WizardColors.textMuted, textAlign = TextAlign.Center)
                Text("Aplicar", Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(WizardColors.text)
                    .clickable(role = Role.Button) {
                        if (multiField && onApplyFields != null) {
                            onApplyFields(stagedValues.map { it.replace('.', ',') })
                        } else {
                            onApply(activeValue.replace('.', ','))
                        }
                        onDismiss()
                    }.padding(vertical = 10.dp)
                    .testTag("workout-numeric-keyboard-apply"),
                    style = LiveSheetType.action, color = Color(0xFF111111), textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.size(1.dp))
        }
    }
}

/** Envuelve un BasicTextField de solo lectura interceptando toques antes de que el editor absorba el foco. */
@Composable
internal fun WorkoutNumericTriggerBox(
    value: String,
    enabled: Boolean,
    description: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    onSetText: ((String) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val withTag = if (tag != null) modifier.testTag(tag) else modifier
    Box(
        modifier = withTag
            .clickable(enabled = enabled, role = Role.Button, onClick = onOpen)
            .semantics {
                contentDescription = description
                editableText = AnnotatedString(value)
                if (enabled && onSetText != null) {
                    setText { annotated ->
                        onSetText(annotated.text)
                        true
                    }
                }
            },
        contentAlignment = contentAlignment,
    ) {
        content()
        if (enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(onOpen) {
                        detectTapGestures(onTap = { onOpen() })
                    },
            )
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
