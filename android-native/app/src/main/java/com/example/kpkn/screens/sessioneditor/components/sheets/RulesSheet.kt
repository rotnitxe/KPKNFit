package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.screens.sessioneditor.DefaultIntensityType
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.SheetHeader
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheetLightChip
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.KpknSheetWhiteButton
import com.example.kpkn.ui.components.kpknSheetWhiteFieldColors
import java.util.Locale

@Composable
internal fun RestTimeField(
    label: String,
    seconds: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val minutes = seconds / 60
    val secs = seconds % 60
    val displayValue = String.format(Locale.US, "%d:%02d", minutes, secs)

    Box(modifier = modifier.clickable(onClick = onClick)) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            shape = RoundedCornerShape(KpknSheetTokens.ControlRadius),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = KpknSheetTokens.ControlLabel,
            ),
            colors = kpknSheetWhiteFieldColors(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun SheetMiniField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onCommit: (String) -> Unit,
) {
    var local by remember(label, value) { mutableStateOf(value) }
    OutlinedTextField(
        value = local,
        onValueChange = {
            local = it
            onCommit(it)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        shape = RoundedCornerShape(KpknSheetTokens.ControlRadius),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Bold,
            color = KpknSheetTokens.ControlLabel,
        ),
        colors = kpknSheetWhiteFieldColors(),
    )
}

@Composable
internal fun RestTimePickerDialog(
    title: String,
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var minInput by remember { mutableStateOf((initialSeconds / 60).toString()) }
    var secInput by remember { mutableStateOf((initialSeconds % 60).toString()) }

    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Ingresa los minutos y segundos para el descanso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = minInput,
                        onValueChange = { minInput = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minutos") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = kpknSheetWhiteFieldColors(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = KpknSheetTokens.ControlLabel),
                    )
                    OutlinedTextField(
                        value = secInput,
                        onValueChange = {
                            secInput = it.filter(Char::isDigit).take(2).let { raw ->
                                val n = raw.toIntOrNull()
                                if (n != null && n > 59) "59" else raw
                            }
                        },
                        label = { Text("Segundos") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = kpknSheetWhiteFieldColors(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = KpknSheetTokens.ControlLabel),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val m = minInput.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val s = (secInput.toIntOrNull() ?: 0).coerceIn(0, 59)
                    onConfirm(m * 60 + s)
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

@Composable
private fun WhiteMinuteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = KpknSheetTokens.ControlLabel,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Bold,
        ),
        cursorBrush = SolidColor(KpknSheetTokens.ControlLabel),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(KpknSheetTokens.ControlFill)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Box(modifier = Modifier.weight(1f, fill = false).clipToBounds()) {
                    if (value.isEmpty()) {
                        Text(
                            "– min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KpknSheetTokens.ControlPlaceholder,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Text(
                        " min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KpknSheetTokens.ControlLabel,
                    )
                }
            }
        },
    )
}

@Composable
internal fun RulesSheet(
    uiState: SessionEditorUiState,
    onApplyRules: (String?) -> Unit,
    onRuleDefaultsChange: (String?, Int?, Int?, Double?, Int?, Int?, Int?, Int?, Boolean?, DefaultIntensityType?) -> Unit,
    onRuleLimitsChange: (Double?, Int?) -> Unit,
    onAdvancedRuleLimitsChange: (Double?, Double?, Int?, Boolean) -> Unit,
    onApplyGlobalIntensityAdjustment: (IntensityMode, Double, Set<String>?) -> Unit,
    setTargetDuration: (Int?) -> Unit,
    setPartTargetDuration: (String, Int?) -> Unit,
    setExerciseTargetDuration: (String, Int?) -> Unit,
    onDistributeTargetAcrossParts: () -> Unit = {},
    onSave: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    @Suppress("UNUSED_PARAMETER")
    onRuleLimitsChange
    @Suppress("UNUSED_PARAMETER")
    onAdvancedRuleLimitsChange
    @Suppress("UNUSED_PARAMETER")
    onApplyGlobalIntensityAdjustment

    var activeTab by remember { mutableIntStateOf(0) }
    var scopePartId by remember { mutableStateOf<String?>(null) }

    val defaults = remember(scopePartId, uiState.ruleDefaults, uiState.partRuleDefaults) {
        if (scopePartId == null) uiState.ruleDefaults
        else (uiState.partRuleDefaults[scopePartId] ?: uiState.ruleDefaults)
    }

    var activeRestDialog by remember { mutableStateOf<String?>(null) }

    if (activeRestDialog != null) {
        val (title, currentSecs, onConfirmCallback) = when (activeRestDialog) {
            "normal" -> Triple(
                "Descanso de series",
                defaults.normalRestSeconds,
                { secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, secs, null, null, null, null, null) },
            )
            "sides" -> Triple(
                "Descanso entre lados",
                defaults.betweenSidesRestSeconds,
                { secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, null, secs, null, null, null, null) },
            )
            "between" -> Triple(
                "Descanso entre ejercicios",
                defaults.supersetBetweenRestSeconds,
                { secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, null, null, secs, null, null, null) },
            )
            "round" -> Triple(
                "Descanso de rondas",
                defaults.supersetRoundRestSeconds,
                { secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, secs, null, null) },
            )
            else -> Triple("", 0, { _: Int -> })
        }
        RestTimePickerDialog(
            title = title,
            initialSeconds = currentSecs,
            onConfirm = {
                onConfirmCallback(it)
                activeRestDialog = null
            },
            onDismiss = { activeRestDialog = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHeader(
            title = "Reglas y tiempo",
            subtitle = "Configura límites de tiempo y reglas base de la sesión.",
        )

        // Assistant-style tabs — white chips, black labels (never primary/yellow).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpknSheetLightChip(
                label = "REGLAS",
                selected = activeTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 0 },
            )
            KpknSheetLightChip(
                label = "TIEMPO",
                selected = activeTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 1 },
            )
        }

        if (activeTab == 0) {
            Text(
                "Configurar reglas por grupo:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                KpknSheetLightChip(
                    label = "Toda la sesión",
                    selected = scopePartId == null,
                    onClick = { scopePartId = null },
                )
                uiState.session?.parts?.forEach { part ->
                    KpknSheetLightChip(
                        label = part.name,
                        selected = scopePartId == part.id,
                        onClick = { scopePartId = part.id },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(KpknSheetTokens.Panel)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Valores de serie",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Intensidad:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            DefaultIntensityType.RPE to "RPE",
                            DefaultIntensityType.RIR to "RIR",
                            DefaultIntensityType.FALLO to "Fallo",
                        ).forEach { (type, label) ->
                            KpknSheetLightChip(
                                label = label,
                                selected = defaults.intensityType == type,
                                onClick = {
                                    onRuleDefaultsChange(
                                        scopePartId, null, null, null, null, null, null, null, null, type,
                                    )
                                },
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SheetMiniField(
                        "Series",
                        defaults.setCount.toString(),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    ) {
                        onRuleDefaultsChange(
                            scopePartId, it.safeIntOrNull(), null, null, null, null, null, null, null, null,
                        )
                    }
                    SheetMiniField(
                        "Reps",
                        defaults.reps.toString(),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    ) {
                        onRuleDefaultsChange(
                            scopePartId, null, it.safeIntOrNull(), null, null, null, null, null, null, null,
                        )
                    }
                    if (defaults.intensityType != DefaultIntensityType.FALLO) {
                        val label = if (defaults.intensityType == DefaultIntensityType.RPE) "RPE" else "RIR"
                        SheetMiniField(
                            label,
                            formatEditableNumber(defaults.rpe),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        ) {
                            onRuleDefaultsChange(
                                scopePartId, null, null, it.safeDoubleOrNull(), null, null, null, null, null, null,
                            )
                        }
                    }
                }

                Text(
                    "Descansos (Min:Seg)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RestTimeField("Normal", defaults.normalRestSeconds, modifier = Modifier.weight(1f)) {
                        activeRestDialog = "normal"
                    }
                    RestTimeField("Lados", defaults.betweenSidesRestSeconds, modifier = Modifier.weight(1f)) {
                        activeRestDialog = "sides"
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RestTimeField("Entre ej.", defaults.supersetBetweenRestSeconds, modifier = Modifier.weight(1f)) {
                        activeRestDialog = "between"
                    }
                    RestTimeField("Rondas", defaults.supersetRoundRestSeconds, modifier = Modifier.weight(1f)) {
                        activeRestDialog = "round"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KpknSheetTokens.ControlFill)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Aplicar a nuevos elementos",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = KpknSheetTokens.ControlLabel,
                        )
                        Text(
                            "Ejercicios, series, lados y supersets nuevos heredan estos valores.",
                            style = MaterialTheme.typography.labelSmall,
                            color = KpknSheetTokens.ControlLabelMuted,
                        )
                    }
                    Switch(
                        checked = defaults.applyToNewItems,
                        onCheckedChange = {
                            onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, null, it, null)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.Black,
                            uncheckedThumbColor = Color.Black,
                            uncheckedTrackColor = Color.Black.copy(alpha = 0.25f),
                        ),
                    )
                }
            }

            KpknSheetWhiteButton(
                text = "Aplicar",
                onClick = { onApplyRules(scopePartId) },
            )
        } else {
            val session = uiState.session
            if (session != null) {
                var globalMinutesInput by remember(session.targetDurationMinutes) {
                    mutableStateOf(session.targetDurationMinutes?.toString().orEmpty())
                }

                fun applyGlobalTimeBudget() {
                    val minutes = globalMinutesInput.toIntOrNull()?.takeIf { it > 0 }
                    setTargetDuration(minutes)
                }

                // Part budget OR sum of its exercises (never both). Plus loose exercises.
                val assignedMinutes = session.parts.sumOf { part ->
                    part.targetDurationMinutes
                        ?: part.exercises.sumOf { it.targetDurationMinutes ?: 0 }
                } + session.exercises.sumOf { it.targetDurationMinutes ?: 0 }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(KpknSheetTokens.Panel)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Límite de tiempo global (guía)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(
                        "Presupuesto orientativo en minutos. Guía el ritmo en vivo; no corta el entrenamiento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 45, 60, 90).forEach { mins ->
                            KpknSheetLightChip(
                                label = "${mins}m",
                                selected = globalMinutesInput == mins.toString(),
                                onClick = {
                                    globalMinutesInput = mins.toString()
                                    setTargetDuration(mins)
                                },
                            )
                        }
                        if (uiState.estimatedDurationMinutes > 0) {
                            KpknSheetLightChip(
                                label = "Estimado ${uiState.estimatedDurationMinutes}m",
                                selected = globalMinutesInput == uiState.estimatedDurationMinutes.toString(),
                                onClick = {
                                    globalMinutesInput = uiState.estimatedDurationMinutes.toString()
                                    setTargetDuration(uiState.estimatedDurationMinutes)
                                },
                            )
                        }
                        KpknSheetLightChip(
                            label = "Sin límite",
                            selected = globalMinutesInput.isBlank(),
                            onClick = {
                                globalMinutesInput = ""
                                setTargetDuration(null)
                            },
                        )
                    }
                    if (uiState.estimatedDurationMinutes > 0) {
                        val budget = session.targetDurationMinutes
                        Text(
                            if (budget != null && budget > 0) {
                                "Estimado de estructura: ${uiState.estimatedDurationMinutes} min · Objetivo: $budget min"
                            } else {
                                "Estimado de estructura: ${uiState.estimatedDurationMinutes} min"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    OutlinedTextField(
                        value = globalMinutesInput,
                        onValueChange = { globalMinutesInput = it.filter(Char::isDigit).take(3) },
                        label = { Text("Minutos") },
                        placeholder = {
                            Text("60", color = KpknSheetTokens.ControlPlaceholder)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.Center,
                            color = KpknSheetTokens.ControlLabel,
                        ),
                        colors = kpknSheetWhiteFieldColors(),
                        shape = RoundedCornerShape(KpknSheetTokens.ControlRadius),
                        suffix = {
                            Text("min", color = KpknSheetTokens.ControlLabelMuted)
                        },
                    )
                    val sessionBudget = session.targetDurationMinutes ?: 0
                    if (sessionBudget > 0) {
                        val isOverBudget = assignedMinutes > sessionBudget
                        val remaining = sessionBudget - assignedMinutes
                        Text(
                            text = if (isOverBudget) {
                                "Excede el presupuesto global por ${assignedMinutes - sessionBudget} min ($assignedMinutes min asignados)"
                            } else {
                                "$assignedMinutes de $sessionBudget min asignados ($remaining min disponibles)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverBudget) Color(0xFFEF4444) else Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Text(
                    "Tiempos por grupos y ejercicios",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Text(
                    "Presupuestos en minutos (guía). Si el grupo tiene minutos, no se suman los de sus ejercicios al global.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
                if (session.parts.size >= 2 && (session.targetDurationMinutes ?: 0) > 0) {
                    KpknSheetLightChip(
                        label = "Repartir global en grupos",
                        selected = false,
                        onClick = onDistributeTargetAcrossParts,
                    )
                }

                session.parts.forEach { part ->
                    var partMinutesInput by remember(part.targetDurationMinutes) {
                        mutableStateOf(part.targetDurationMinutes?.toString() ?: "")
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(KpknSheetTokens.Panel)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                part.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                            Box(modifier = Modifier.width(90.dp)) {
                                WhiteMinuteField(
                                    value = partMinutesInput,
                                    onValueChange = {
                                        partMinutesInput = it
                                        setPartTargetDuration(part.id, it.toIntOrNull())
                                    },
                                )
                            }
                        }

                        if (part.exercises.isNotEmpty()) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                            part.exercises.forEach { ex ->
                                var exMinutesInput by remember(ex.targetDurationMinutes) {
                                    mutableStateOf(ex.targetDurationMinutes?.toString() ?: "")
                                }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                ) {
                                    Text(
                                        ex.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.width(80.dp)) {
                                        WhiteMinuteField(
                                            value = exMinutesInput,
                                            onValueChange = {
                                                exMinutesInput = it
                                                setExerciseTargetDuration(ex.id, it.toIntOrNull())
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (session.exercises.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(KpknSheetTokens.Panel)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Otros ejercicios",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        session.exercises.forEach { ex ->
                            var exMinutesInput by remember(ex.targetDurationMinutes) {
                                mutableStateOf(ex.targetDurationMinutes?.toString() ?: "")
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    ex.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.width(80.dp)) {
                                    WhiteMinuteField(
                                        value = exMinutesInput,
                                        onValueChange = {
                                            exMinutesInput = it
                                            setExerciseTargetDuration(ex.id, it.toIntOrNull())
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                KpknSheetWhiteButton(
                    text = "Guardar cambios",
                    onClick = {
                        applyGlobalTimeBudget()
                        onSave()
                        onDismiss()
                    },
                )
            }
        }
    }
}
