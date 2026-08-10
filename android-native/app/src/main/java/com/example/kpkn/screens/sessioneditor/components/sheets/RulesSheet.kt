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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.domain.sessionassistant.TimeCoachFatigueDelta
import com.example.kpkn.screens.sessioneditor.DefaultIntensityType
import com.example.kpkn.screens.sessioneditor.RuleScope
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.SheetHeader
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.KpknSheetGlassChip
import com.example.kpkn.ui.components.KpknSheetLightChip
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.KpknSheetWhiteButton
import com.example.kpkn.ui.components.kpknSheetGlassFieldColors
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = KpknSheetTokens.GlassControlLabelMuted,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(KpknSheetTokens.ControlRadius))
                .background(KpknSheetTokens.GlassControlFill)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                displayValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = KpknSheetTokens.GlassControlLabel,
            )
        }
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
        label = {
            Text(label, color = KpknSheetTokens.GlassControlLabelMuted)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        shape = RoundedCornerShape(KpknSheetTokens.ControlRadius),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Bold,
            color = KpknSheetTokens.GlassControlLabel,
        ),
        colors = kpknSheetGlassFieldColors(),
    )
}

@Composable
private fun DurationTapField(
    label: String,
    display: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = KpknSheetTokens.GlassControlLabelMuted,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(KpknSheetTokens.ControlRadius))
                .background(KpknSheetTokens.GlassControlFill)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                display,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = KpknSheetTokens.GlassControlLabel,
            )
        }
    }
}

private fun formatDurationMinutes(minutes: Int?): String {
    if (minutes == null || minutes <= 0) return "Sin límite"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "%d h %02d min".format(h, m) else "$m min"
}

@Composable
internal fun RulesSheet(
    uiState: SessionEditorUiState,
    onApplyRules: (String?) -> ApplyRulesOutcome,
    onRuleDefaultsChange: (String?, Int?, Int?, Double?, Int?, Int?, Int?, Int?, Boolean?, DefaultIntensityType?) -> Unit,
    onRuleLimitsChange: (Double?, Int?) -> Unit,
    onAdvancedRuleLimitsChange: (Double?, Double?, Int?, Boolean) -> Unit,
    onApplyGlobalIntensityAdjustment: (IntensityMode, Double, Set<String>?) -> Unit,
    setTargetDuration: (Int?) -> Unit,
    setPartTargetDuration: (String, Int?) -> Unit,
    setExerciseTargetDuration: (String, Int?) -> Unit,
    onDistributeTargetAcrossParts: () -> Unit = {},
    onApplyRuleTemplate: (String, String?) -> Unit = { _, _ -> },
    onSaveRuleTemplate: (String) -> Unit = {},
    onRenameRuleTemplate: (String, String) -> Unit = { _, _ -> },
    onDeleteRuleTemplate: (String) -> Unit = {},
    onPatchRuleDefaults: (String?, (com.example.kpkn.screens.sessioneditor.SessionEditorRuleDefaults) -> com.example.kpkn.screens.sessioneditor.SessionEditorRuleDefaults) -> Unit = { _, _ -> },
    onApplyTimeCoachSuggestion: (String) -> Unit = {},
    onDismissTimeCoachSuggestion: (String) -> Unit = {},
    onRefreshTimeCoach: () -> Unit = {},
    onInitialTabConsumed: () -> Unit = {},

    onDismiss: () -> Unit = {},
) {
    @Suppress("UNUSED_PARAMETER")
    onRuleLimitsChange
    @Suppress("UNUSED_PARAMETER")
    onAdvancedRuleLimitsChange
    @Suppress("UNUSED_PARAMETER")
    onApplyGlobalIntensityAdjustment

    var activeTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(uiState.rulesSheetInitialTab) {
        if (uiState.rulesSheetInitialTab == 1) {
            activeTab = 1
            onInitialTabConsumed()
        }
    }
    LaunchedEffect(activeTab) {
        if (activeTab == 1) onRefreshTimeCoach()
    }
    var scopePartId by remember { mutableStateOf<String?>(null) }
    var selectedScope by remember(uiState.ruleDefaults.scope) { mutableStateOf(uiState.ruleDefaults.scope) }
    var saveTemplateName by remember { mutableStateOf<String?>(null) }
    var renameTemplate by remember { mutableStateOf<Pair<String, String>?>(null) }
    var templatesExpanded by remember { mutableStateOf(false) }
    var groupTimesExpanded by remember { mutableStateOf(false) }

    val activeScopePartId = scopePartId.takeIf { selectedScope == RuleScope.PER_GROUP }
    val defaults = remember(selectedScope, activeScopePartId, uiState.ruleDefaults, uiState.partRuleDefaults) {
        val source = if (activeScopePartId == null) uiState.ruleDefaults
        else (uiState.partRuleDefaults[activeScopePartId] ?: uiState.ruleDefaults)
        source.copy(scope = selectedScope)
    }
    fun selectScope(scope: RuleScope) {
        selectedScope = scope
        if (scope != RuleScope.PER_GROUP) scopePartId = null
        if (scope == RuleScope.PER_GROUP && scopePartId == null) {
            scopePartId = (uiState.activeVariantSession ?: uiState.session)?.parts?.firstOrNull()?.id
        }
        val targetPart = scopePartId.takeIf { scope == RuleScope.PER_GROUP }
        onPatchRuleDefaults(targetPart) { it.copy(scope = scope) }
    }
    var compoundIsolationExpanded by remember(selectedScope, activeScopePartId) {
        mutableStateOf(
            selectedScope == RuleScope.COMPOUND_ISOLATION ||
                defaults.hasCompoundOverrides ||
                defaults.hasIsolationOverrides,
        )
    }

    var activeRestDialog by remember { mutableStateOf<String?>(null) }
    var durationPickerTarget by remember { mutableStateOf<DurationPickerTarget?>(null) }

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
            "compound" -> Triple(
                "Descanso compuestos",
                defaults.compoundRestSeconds ?: defaults.normalRestSeconds,
                { secs: Int -> onPatchRuleDefaults(scopePartId) { d -> d.copy(compoundRestSeconds = secs) } },
            )
            "isolation" -> Triple(
                "Descanso aislamientos",
                defaults.isolationRestSeconds ?: defaults.normalRestSeconds,
                { secs: Int -> onPatchRuleDefaults(scopePartId) { d -> d.copy(isolationRestSeconds = secs) } },
            )
            else -> Triple("", 0, { _: Int -> })
        }
        KpknNativeTimePickerDialog(
            title = title,
            initialHour = (currentSecs / 60).coerceIn(0, 23),
            initialMinute = (currentSecs % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { hour, minute ->
                onConfirmCallback(hour * 60 + minute)
                activeRestDialog = null
            },
            onDismiss = { activeRestDialog = null },
        )
    }

    durationPickerTarget?.let { target ->
        val total = target.currentMinutes?.coerceAtLeast(0) ?: 0
        KpknNativeTimePickerDialog(
            title = target.title,
            initialHour = (total / 60).coerceIn(0, 23),
            initialMinute = (total % 60).coerceIn(0, 59),
            hint = "Horas : minutos de presupuesto",
            onConfirm = { hour, minute ->
                val mins = (hour * 60 + minute).takeIf { it > 0 }
                when (target) {
                    is DurationPickerTarget.Global -> setTargetDuration(mins)
                    is DurationPickerTarget.Part -> setPartTargetDuration(target.partId, mins)
                    is DurationPickerTarget.Exercise -> setExerciseTargetDuration(target.exerciseId, mins)
                }
                durationPickerTarget = null
            },
            onDismiss = { durationPickerTarget = null },
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .padding(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        SheetHeader(
            title = "Reglas y tiempo",
            subtitle = if (activeTab == 0) {
                "Defaults de series, intensidad y descansos para nuevos ejercicios."
            } else {
                "Presupuesto de duración y ajustes para entrar en tu límite."
            },
        )

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
                "Alcance de las reglas:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                KpknSheetGlassChip(
                    label = "Toda la sesión",
                    selected = selectedScope == RuleScope.ALL_SESSION,
                    onClick = { selectScope(RuleScope.ALL_SESSION) },
                )
                KpknSheetGlassChip(
                    label = "Por grupo",
                    selected = selectedScope == RuleScope.PER_GROUP,
                    onClick = { selectScope(RuleScope.PER_GROUP) },
                )
                KpknSheetGlassChip(
                    label = "Comp. / ais.",
                    selected = selectedScope == RuleScope.COMPOUND_ISOLATION,
                    onClick = { selectScope(RuleScope.COMPOUND_ISOLATION) },
                )
            }
            if (selectedScope == RuleScope.PER_GROUP) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    (uiState.activeVariantSession ?: uiState.session)?.parts?.forEach { part ->
                    KpknSheetGlassChip(
                        label = part.name,
                        selected = scopePartId == part.id,
                        onClick = { scopePartId = part.id },
                    )
                    }
                }
            }

            if (selectedScope != RuleScope.COMPOUND_ISOLATION) {
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
                            KpknSheetGlassChip(
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
                    "Descansos",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    "Define descansos base para series, lados y superseries.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
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
                        .background(KpknSheetTokens.GlassControlFill)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Aplicar a nuevos elementos",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = KpknSheetTokens.GlassControlLabel,
                        )
                        Text(
                            "Ejercicios, series, lados y supersets nuevos heredan estos valores.",
                            style = MaterialTheme.typography.labelSmall,
                            color = KpknSheetTokens.GlassControlLabelMuted,
                        )
                    }
                    Switch(
                        checked = defaults.applyToNewItems,
                        onCheckedChange = {
                            onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, null, it, null)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.White.copy(alpha = 0.35f),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                        ),
                    )
                }
                }
            }

            if (selectedScope == RuleScope.COMPOUND_ISOLATION) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(KpknSheetTokens.Panel)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { compoundIsolationExpanded = !compoundIsolationExpanded }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Compuestos / Aislados",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Text(
                            "Configura una regla para básicos y otra para aislamientos.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                    Text(
                        if (compoundIsolationExpanded) "▲" else "▼",
                        color = Color.White.copy(alpha = 0.65f),
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (compoundIsolationExpanded) {
                    Text(
                        "Básicos",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RestTimeField(
                            "Descanso",
                            defaults.compoundRestSeconds ?: defaults.normalRestSeconds,
                            modifier = Modifier.weight(1f),
                        ) { activeRestDialog = "compound" }
                        SheetMiniField(
                            "Reps",
                            (defaults.compoundReps ?: defaults.reps).toString(),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        ) {
                            onPatchRuleDefaults(scopePartId) { d ->
                                d.copy(compoundReps = it.safeIntOrNull())
                            }
                        }
                        SheetMiniField(
                            if ((defaults.compoundIntensityType ?: defaults.intensityType) == DefaultIntensityType.RIR) "RIR" else "RPE",
                            formatEditableNumber(defaults.compoundRpe ?: defaults.rpe),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        ) {
                            onPatchRuleDefaults(scopePartId) { d ->
                                d.copy(compoundRpe = it.safeDoubleOrNull())
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            DefaultIntensityType.RPE to "RPE",
                            DefaultIntensityType.RIR to "RIR",
                            DefaultIntensityType.FALLO to "Fallo",
                        ).forEach { (type, label) ->
                            KpknSheetGlassChip(
                                label = label,
                                selected = (defaults.compoundIntensityType ?: defaults.intensityType) == type,
                                onClick = {
                                    onPatchRuleDefaults(scopePartId) { d ->
                                        d.copy(compoundIntensityType = type)
                                    }
                                },
                            )
                        }
                    }

                    Text(
                        "Aislamientos",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RestTimeField(
                            "Descanso",
                            defaults.isolationRestSeconds ?: defaults.normalRestSeconds,
                            modifier = Modifier.weight(1f),
                        ) { activeRestDialog = "isolation" }
                        SheetMiniField(
                            "Reps",
                            (defaults.isolationReps ?: defaults.reps).toString(),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        ) {
                            onPatchRuleDefaults(scopePartId) { d ->
                                d.copy(isolationReps = it.safeIntOrNull())
                            }
                        }
                        SheetMiniField(
                            if ((defaults.isolationIntensityType ?: defaults.intensityType) == DefaultIntensityType.RIR) "RIR" else "RPE",
                            formatEditableNumber(defaults.isolationRpe ?: defaults.rpe),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        ) {
                            onPatchRuleDefaults(scopePartId) { d ->
                                d.copy(isolationRpe = it.safeDoubleOrNull())
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            DefaultIntensityType.RPE to "RPE",
                            DefaultIntensityType.RIR to "RIR",
                            DefaultIntensityType.FALLO to "Fallo",
                        ).forEach { (type, label) ->
                            KpknSheetGlassChip(
                                label = label,
                                selected = (defaults.isolationIntensityType ?: defaults.intensityType) == type,
                                onClick = {
                                    onPatchRuleDefaults(scopePartId) { d ->
                                        d.copy(isolationIntensityType = type)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(KpknSheetTokens.Panel)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { templatesExpanded = !templatesExpanded }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Plantillas",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Text(
                            if (templatesExpanded) {
                                "Toca una plantilla para precargar defaults."
                            } else {
                                "${uiState.ruleTemplates.size} guardadas · tocar para desplegar"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                    Text(
                        if (templatesExpanded) "▲" else "▼",
                        color = Color.White.copy(alpha = 0.65f),
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (templatesExpanded) {
                    uiState.ruleTemplates.forEach { template ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KpknSheetGlassChip(
                                label = template.name,
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onApplyRuleTemplate(template.id, scopePartId)
                                    if (template.defaults.hasCompoundOverrides ||
                                        template.defaults.hasIsolationOverrides
                                    ) {
                                        compoundIsolationExpanded = true
                                    }
                                },
                            )
                            if (!template.isFactory) {
                                KpknSheetGlassChip(
                                    label = "✎",
                                    selected = false,
                                    onClick = { renameTemplate = template.id to template.name },
                                )
                                KpknSheetGlassChip(
                                    label = "✕",
                                    selected = false,
                                    onClick = { onDeleteRuleTemplate(template.id) },
                                )
                            }
                        }
                    }
                    KpknSheetGlassChip(
                        label = "Guardar como plantilla…",
                        selected = false,
                        onClick = { saveTemplateName = "" },
                    )
                }
            }

        } else {
            val session = uiState.activeVariantSession ?: uiState.session
            if (session != null) {
                val assignedMinutes = session.parts.sumOf { part ->
                    part.targetDurationMinutes
                        ?: part.exercises.sumOf { it.targetDurationMinutes ?: 0 }
                } + session.exercises.sumOf { it.targetDurationMinutes ?: 0 }
                val estimated = uiState.sessionTimeBreakdown?.totalMinutes
                    ?: uiState.estimatedDurationMinutes
                val limit = session.targetDurationMinutes
                val gap = if (limit != null && estimated > 0) estimated - limit else null

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(KpknSheetTokens.Panel)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Reloj de sesión",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TimeHeroStat(
                            label = "Estimado",
                            value = if (estimated > 0) "$estimated min" else "—",
                            accent = Color.White,
                        )
                        TimeHeroStat(
                            label = "Límite",
                            value = limit?.let { "$it min" } ?: "Sin límite",
                            accent = Color.White.copy(alpha = 0.9f),
                        )
                        TimeHeroStat(
                            label = "Gap",
                            value = when {
                                gap == null -> "—"
                                gap > 0 -> "+$gap min"
                                gap < 0 -> "$gap min"
                                else -> "0"
                            },
                            accent = when {
                                gap == null -> Color.White.copy(alpha = 0.7f)
                                gap > 0 -> Color(0xFFEF4444)
                                gap < 0 -> Color(0xFF22C55E)
                                else -> Color(0xFFF59E0B)
                            },
                        )
                    }
                    uiState.sessionTimeBreakdown?.let { bd ->
                        Text(
                            "Prep ${bd.setupMinutes} · Ejec ${bd.executionMinutes} · Descansos ${bd.restMinutes}" +
                                if (bd.warmupMinutes > 0) " · Warmup ${bd.warmupMinutes}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                }

                if (uiState.timeCoachSuggestions.isNotEmpty()) {
                    Text(
                        "Para entrar en tu límite",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    uiState.timeCoachSuggestions.forEach { suggestion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(KpknSheetTokens.Panel)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    suggestion.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "−${suggestion.minutesSaved} min",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF22C55E),
                                )
                            }
                            Text(
                                suggestion.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KpknSheetGlassChip(
                                    label = when (suggestion.fatigueDelta) {
                                        TimeCoachFatigueDelta.LOWER -> "Fatiga ↓"
                                        TimeCoachFatigueDelta.SIMILAR -> "Fatiga ≈"
                                        TimeCoachFatigueDelta.HIGHER -> "Fatiga ↑"
                                    },
                                    selected = false,
                                    onClick = {},
                                )
                                Spacer(Modifier.weight(1f))
                                KpknSheetGlassChip(
                                    label = "Descartar",
                                    selected = false,
                                    onClick = { onDismissTimeCoachSuggestion(suggestion.id) },
                                )
                                KpknSheetGlassChip(
                                    label = "Aplicar",
                                    selected = true,
                                    onClick = { onApplyTimeCoachSuggestion(suggestion.id) },
                                )
                            }
                        }
                    }
                } else if (limit != null && gap != null && gap > 0) {
                    Text(
                        "Calculando sugerencias… o no hay ajustes seguros aún.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }

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
                        "Presupuesto orientativo. Guía el ritmo en vivo; no corta el entrenamiento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        listOf(30, 45, 60, 90).forEach { mins ->
                            KpknSheetGlassChip(
                                label = "${mins}m",
                                selected = session.targetDurationMinutes == mins,
                                onClick = { setTargetDuration(mins) },
                            )
                        }
                        if (estimated > 0) {
                            KpknSheetGlassChip(
                                label = "Estimado",
                                selected = session.targetDurationMinutes == estimated,
                                onClick = { setTargetDuration(estimated) },
                            )
                        }
                        KpknSheetGlassChip(
                            label = "Sin límite",
                            selected = session.targetDurationMinutes == null,
                            onClick = { setTargetDuration(null) },
                        )
                    }
                    DurationTapField(
                        label = "Presupuesto global",
                        display = formatDurationMinutes(session.targetDurationMinutes),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            durationPickerTarget = DurationPickerTarget.Global(
                                currentMinutes = session.targetDurationMinutes,
                            )
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { groupTimesExpanded = !groupTimesExpanded }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Tiempos por grupos y ejercicios",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                        Text(
                            if (groupTimesExpanded) {
                                "Presupuesto por grupo o ejercicio. El del grupo prevalece."
                            } else {
                                "Tocar para desplegar presupuestos por grupo"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                    Text(
                        if (groupTimesExpanded) "▲" else "▼",
                        color = Color.White.copy(alpha = 0.65f),
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (groupTimesExpanded) {
                if (session.parts.size >= 2 && (session.targetDurationMinutes ?: 0) > 0) {
                    KpknSheetGlassChip(
                        label = "Repartir global en grupos",
                        selected = false,
                        onClick = onDistributeTargetAcrossParts,
                    )
                }

                session.parts.forEach { part ->
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
                            Box(modifier = Modifier.width(110.dp)) {
                                DurationTapField(
                                    label = "",
                                    display = formatDurationMinutes(part.targetDurationMinutes),
                                    onClick = {
                                        durationPickerTarget = DurationPickerTarget.Part(
                                            partId = part.id,
                                            title = part.name,
                                            currentMinutes = part.targetDurationMinutes,
                                        )
                                    },
                                )
                            }
                        }

                        if (part.exercises.isNotEmpty()) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                            part.exercises.forEach { ex ->
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
                                    Box(modifier = Modifier.width(100.dp)) {
                                        DurationTapField(
                                            label = "",
                                            display = formatDurationMinutes(ex.targetDurationMinutes),
                                            onClick = {
                                                durationPickerTarget = DurationPickerTarget.Exercise(
                                                    exerciseId = ex.id,
                                                    title = ex.name,
                                                    currentMinutes = ex.targetDurationMinutes,
                                                )
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
                                Box(modifier = Modifier.width(100.dp)) {
                                    DurationTapField(
                                        label = "",
                                        display = formatDurationMinutes(ex.targetDurationMinutes),
                                        onClick = {
                                            durationPickerTarget = DurationPickerTarget.Exercise(
                                                exerciseId = ex.id,
                                                title = ex.name,
                                                currentMinutes = ex.targetDurationMinutes,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                } // groupTimesExpanded

            }
        }
        }

        StickyActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onApply = {
                val outcome = onApplyRules(activeScopePartId)
                if (outcome is ApplyRulesOutcome.Applied) onDismiss()
            },
        )
    }

    saveTemplateName?.let { draftName ->
        var localName by remember(draftName) { mutableStateOf(draftName) }
        KpknAlertDialog(
            onDismissRequest = { saveTemplateName = null },
            title = { Text("Guardar plantilla", fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = localName,
                    onValueChange = { localName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    colors = kpknSheetGlassFieldColors(),
                )
            },
            confirmButton = {
                KpknAlertConfirmButton(
                    text = "Guardar",
                    onClick = {
                        onSaveRuleTemplate(localName)
                        saveTemplateName = null
                    },
                )
            },
            dismissButton = {
                KpknAlertDismissButton(text = "Cancelar", onClick = { saveTemplateName = null })
            },
        )
    }

    renameTemplate?.let { (id, currentName) ->
        var localName by remember(id, currentName) { mutableStateOf(currentName) }
        KpknAlertDialog(
            onDismissRequest = { renameTemplate = null },
            title = { Text("Renombrar plantilla", fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = localName,
                    onValueChange = { localName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    colors = kpknSheetGlassFieldColors(),
                )
            },
            confirmButton = {
                KpknAlertConfirmButton(
                    text = "Guardar",
                    onClick = {
                        onRenameRuleTemplate(id, localName)
                        renameTemplate = null
                    },
                )
            },
            dismissButton = {
                KpknAlertDismissButton(text = "Cancelar", onClick = { renameTemplate = null })
            },
        )
    }
}

@Composable
internal fun StickyActionBar(
    modifier: Modifier = Modifier,
    onApply: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpknSheetLightChip(
            label = "Aplicar",
            selected = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onApply,
        )
    }
}

@Composable
private fun TimeHeroStat(
    label: String,
    value: String,
    accent: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Black,
        )
    }
}

private sealed class DurationPickerTarget {
    abstract val title: String
    abstract val currentMinutes: Int?

    data class Global(
        override val currentMinutes: Int?,
    ) : DurationPickerTarget() {
        override val title: String = "Presupuesto global"
    }

    data class Part(
        val partId: String,
        override val title: String,
        override val currentMinutes: Int?,
    ) : DurationPickerTarget()

    data class Exercise(
        val exerciseId: String,
        override val title: String,
        override val currentMinutes: Int?,
    ) : DurationPickerTarget()
}
