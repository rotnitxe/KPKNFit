package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.DefaultIntensityType
import com.example.kpkn.screens.sessioneditor.RuleScope
import com.example.kpkn.screens.sessioneditor.RuleTemplate
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.KpknRestPickerChain
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** Alcances de las reglas, en el orden del control segmentado: Sesión · Grupo · Compuestos y aislados. */
private val RuleScopeOrder = listOf(RuleScope.ALL_SESSION, RuleScope.PER_GROUP, RuleScope.COMPOUND_ISOLATION)

/** Rótulos cortos del control de alcance; su nombre completo se lee por accesibilidad. */
private val RuleScopeLabels = listOf("Sesión", "Grupo", "Comp. y aisl.")
private val RuleScopeSpokenNames = listOf("Toda la sesión", "Por grupo", "Compuestos y aislados")

/** Intensidades que ofrece la regla, en orden. */
private val RuleIntensityOptions = listOf(
    DefaultIntensityType.RPE to "RPE",
    DefaultIntensityType.RIR to "RIR",
    DefaultIntensityType.FALLO to "Fallo",
)

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

    val focusManager = LocalFocusManager.current
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
        // El selector de descanso de la app (KPKN, dentro del vidrio de la hoja), en un solo paso.
        KpknRestPickerChain(
            primaryTitle = title,
            primarySeconds = currentSecs,
            onConfirm = { seconds, _ ->
                onConfirmCallback(seconds)
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Reglas y tiempo",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.fillMaxWidth(),
            )
            KpknSegmentedControl(
                options = listOf("Reglas", "Tiempo"),
                selectedIndex = activeTab,
                onSelect = { activeTab = it },
                modifier = Modifier.fillMaxWidth(),
            )
            SheetCaption(
                if (activeTab == 0) {
                    "Ajustes por defecto de series, intensidad y descansos para nuevos ejercicios."
                } else {
                    "Presupuesto de duración y ajustes para entrar en tu límite."
                },
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            if (activeTab == 0) {
                // 1. Alcance de las reglas: sesión, grupo o compuestos y aislados.
                KpknModule(modifier = Modifier.fillMaxWidth()) {
                    KpknModuleHeader(label = "Alcance de las reglas")
                    Spacer(Modifier.height(12.dp))
                    KpknSegmentedControl(
                        options = RuleScopeLabels,
                        selectedIndex = RuleScopeOrder.indexOf(selectedScope),
                        onSelect = { index -> selectScope(RuleScopeOrder[index]) },
                        modifier = Modifier.fillMaxWidth(),
                        descriptions = RuleScopeSpokenNames,
                    )
                    if (selectedScope == RuleScope.PER_GROUP) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            (uiState.activeVariantSession ?: uiState.session)?.parts?.forEach { part ->
                                KpknChip(
                                    text = part.name,
                                    selected = scopePartId == part.id,
                                    onClick = { scopePartId = part.id },
                                )
                            }
                        }
                    }
                }

                if (selectedScope != RuleScope.COMPOUND_ISOLATION) {
                    // 2. Valores de serie, descansos y «Aplicar a nuevos elementos».
                    KpknModule(modifier = Modifier.fillMaxWidth()) {
                        KpknModuleHeader(label = "Valores de serie")
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RuleIntensityOptions.forEach { (type, label) ->
                                KpknChip(
                                    text = label,
                                    selected = defaults.intensityType == type,
                                    onClick = {
                                        onRuleDefaultsChange(
                                            scopePartId, null, null, null, null, null, null, null, null, type,
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RulesNumberField(
                                label = "Series",
                                value = defaults.setCount.toString(),
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            ) {
                                onRuleDefaultsChange(
                                    scopePartId, it.safeIntOrNull(), null, null, null, null, null, null, null, null,
                                )
                            }
                            RulesNumberField(
                                label = "Reps",
                                value = defaults.reps.toString(),
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            ) {
                                onRuleDefaultsChange(
                                    scopePartId, null, it.safeIntOrNull(), null, null, null, null, null, null, null,
                                )
                            }
                            if (defaults.intensityType != DefaultIntensityType.FALLO) {
                                RulesNumberField(
                                    label = if (defaults.intensityType == DefaultIntensityType.RPE) "RPE" else "RIR",
                                    value = formatDecimalComma(defaults.rpe),
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onRuleDefaultsChange(
                                        scopePartId, null, null, it.safeDoubleOrNull(), null, null, null, null, null, null,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        SheetEyebrow("Descansos")
                        Spacer(Modifier.height(4.dp))
                        SheetCaption("Define descansos base para series, lados y superseries.")
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RulesRestCell("Normal", defaults.normalRestSeconds, Modifier.weight(1f)) {
                                activeRestDialog = "normal"
                            }
                            RulesRestCell("Lados", defaults.betweenSidesRestSeconds, Modifier.weight(1f)) {
                                activeRestDialog = "sides"
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RulesRestCell("Entre ej.", defaults.supersetBetweenRestSeconds, Modifier.weight(1f)) {
                                activeRestDialog = "between"
                            }
                            RulesRestCell("Rondas", defaults.supersetRoundRestSeconds, Modifier.weight(1f)) {
                                activeRestDialog = "round"
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Aplicar a nuevos elementos",
                                    style = KpknType.bodyStrong,
                                    color = WizardColors.text,
                                )
                                SheetCaption("Ejercicios, series, lados y supersets nuevos heredan estos valores.")
                            }
                            KpknSwitch(
                                checked = defaults.applyToNewItems,
                                onCheckedChange = {
                                    onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, null, it, null)
                                },
                            )
                        }
                    }
                } else {
                    // 2 bis. Compuestos y aislados: una regla para cada uno, plegada salvo que haya overrides.
                    KpknModule(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { compoundIsolationExpanded = !compoundIsolationExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Compuestos y aislados",
                                    style = KpknType.bodyStrong,
                                    color = WizardColors.text,
                                )
                                SheetCaption("Configura una regla para compuestos y otra para aislamientos.")
                            }
                            ExpandChevron(expanded = compoundIsolationExpanded)
                        }
                        if (compoundIsolationExpanded) {
                            Spacer(Modifier.height(12.dp))
                            SheetEyebrow("Compuestos")
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RulesRestCell(
                                    label = "Descanso",
                                    seconds = defaults.compoundRestSeconds ?: defaults.normalRestSeconds,
                                    modifier = Modifier.weight(1f),
                                ) { activeRestDialog = "compound" }
                                RulesNumberField(
                                    label = "Reps",
                                    value = (defaults.compoundReps ?: defaults.reps).toString(),
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onPatchRuleDefaults(scopePartId) { d -> d.copy(compoundReps = it.safeIntOrNull()) }
                                }
                                RulesNumberField(
                                    label = if ((defaults.compoundIntensityType ?: defaults.intensityType) == DefaultIntensityType.RIR) "RIR" else "RPE",
                                    value = formatDecimalComma(defaults.compoundRpe ?: defaults.rpe),
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onPatchRuleDefaults(scopePartId) { d -> d.copy(compoundRpe = it.safeDoubleOrNull()) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            IntensityChipRow(
                                selectedType = defaults.compoundIntensityType ?: defaults.intensityType,
                                onSelect = { type ->
                                    onPatchRuleDefaults(scopePartId) { d -> d.copy(compoundIntensityType = type) }
                                },
                            )

                            Spacer(Modifier.height(16.dp))
                            SheetEyebrow("Aislamientos")
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RulesRestCell(
                                    label = "Descanso",
                                    seconds = defaults.isolationRestSeconds ?: defaults.normalRestSeconds,
                                    modifier = Modifier.weight(1f),
                                ) { activeRestDialog = "isolation" }
                                RulesNumberField(
                                    label = "Reps",
                                    value = (defaults.isolationReps ?: defaults.reps).toString(),
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onPatchRuleDefaults(scopePartId) { d -> d.copy(isolationReps = it.safeIntOrNull()) }
                                }
                                RulesNumberField(
                                    label = if ((defaults.isolationIntensityType ?: defaults.intensityType) == DefaultIntensityType.RIR) "RIR" else "RPE",
                                    value = formatDecimalComma(defaults.isolationRpe ?: defaults.rpe),
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onPatchRuleDefaults(scopePartId) { d -> d.copy(isolationRpe = it.safeDoubleOrNull()) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            IntensityChipRow(
                                selectedType = defaults.isolationIntensityType ?: defaults.intensityType,
                                onSelect = { type ->
                                    onPatchRuleDefaults(scopePartId) { d -> d.copy(isolationIntensityType = type) }
                                },
                            )
                        }
                    }
                }

                // 3. Plantillas de reglas, como chips.
                KpknModule(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { templatesExpanded = !templatesExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Plantillas",
                                style = KpknType.bodyStrong,
                                color = WizardColors.text,
                            )
                            SheetCaption(
                                if (templatesExpanded) {
                                    "Toca una plantilla para precargar defaults."
                                } else {
                                    "${uiState.ruleTemplates.size} guardadas · tocar para desplegar"
                                },
                            )
                        }
                        ExpandChevron(expanded = templatesExpanded)
                    }
                    if (templatesExpanded) {
                        Spacer(Modifier.height(12.dp))
                        RuleTemplateChips(
                            uiState = uiState,
                            onApply = { template ->
                                onApplyRuleTemplate(template.id, scopePartId)
                                if (template.defaults.hasCompoundOverrides || template.defaults.hasIsolationOverrides) {
                                    compoundIsolationExpanded = true
                                }
                            },
                            onRename = { template -> renameTemplate = template.id to template.name },
                            onDelete = { template -> onDeleteRuleTemplate(template.id) },
                            onSaveNew = { saveTemplateName = "" },
                        )
                    }
                }
            } else {
                // Tiempo: el reloj, las sugerencias para entrar en el límite y los tiempos (en RulesTimeTab).
                RulesTimeTab(
                    uiState = uiState,
                    setTargetDuration = setTargetDuration,
                    onOpenDuration = { durationPickerTarget = it },
                    onDistributeTargetAcrossParts = onDistributeTargetAcrossParts,
                    onApplyTimeCoachSuggestion = onApplyTimeCoachSuggestion,
                    onDismissTimeCoachSuggestion = onDismissTimeCoachSuggestion,
                )
            }
        }

        StickyActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onApply = {
                // Flush de los campos antes de aplicar: al salir de cada campo se confirma su valor.
                focusManager.clearFocus(force = true)
                val outcome = onApplyRules(activeScopePartId)
                if (outcome is ApplyRulesOutcome.Applied) onDismiss()
            },
        )
    }

    saveTemplateName?.let { draftName ->
        var localName by remember(draftName) { mutableStateOf(draftName) }
        EditorDialogImeArea {
            KpknAlertDialog(
                onDismissRequest = { saveTemplateName = null },
                title = { Text("Guardar plantilla", style = KpknType.dialogTitle, color = WizardColors.text) },
                text = {
                    KpknTextField(
                        value = localName,
                        onValueChange = { localName = it },
                        label = "Nombre",
                        singleLine = true,
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
    }

    renameTemplate?.let { (id, currentName) ->
        var localName by remember(id, currentName) { mutableStateOf(currentName) }
        EditorDialogImeArea {
            KpknAlertDialog(
                onDismissRequest = { renameTemplate = null },
                title = { Text("Renombrar plantilla", style = KpknType.dialogTitle, color = WizardColors.text) },
                text = {
                    KpknTextField(
                        value = localName,
                        onValueChange = { localName = it },
                        label = "Nombre",
                        singleLine = true,
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
}

/** Dos chips de intensidad (RPE · RIR · Fallo) de una regla de compuestos o aislamientos. */
@Composable
private fun IntensityChipRow(
    selectedType: DefaultIntensityType,
    onSelect: (DefaultIntensityType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RuleIntensityOptions.forEach { (type, label) ->
            KpknChip(
                text = label,
                selected = selectedType == type,
                onClick = { onSelect(type) },
            )
        }
    }
}

/** Las plantillas de reglas: cada una es un chip que la aplica; las del usuario se renombran o se borran; al final, guardar. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RuleTemplateChips(
    uiState: SessionEditorUiState,
    onApply: (RuleTemplate) -> Unit,
    onRename: (RuleTemplate) -> Unit,
    onDelete: (RuleTemplate) -> Unit,
    onSaveNew: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uiState.ruleTemplates.forEach { template ->
            KpknChip(text = template.name, selected = false, onClick = { onApply(template) })
            if (!template.isFactory) {
                KpknChip(text = "Renombrar", selected = false, onClick = { onRename(template) })
                KpknChip(text = "Eliminar", selected = false, onClick = { onDelete(template) })
            }
        }
        KpknChip(text = "Guardar como plantilla…", selected = false, onClick = onSaveNew)
    }
}

/**
 * Número de una regla (series, reps, RPE o RIR): cifra en su celda `inset` con la etiqueta encima. El valor se confirma al pulsar
 * «Hecho» o al salir del campo, como antes.
 */
@Composable
private fun RulesNumberField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetCaption(label)
        KpknNumberCell(
            value = value,
            input = KpknCellInput.Text(keyboardType = keyboardType, onCommit = onCommit),
            contentDescription = label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Un descanso de la regla: su tiempo en la celda; al tocarla abre el selector de descanso de la app. */
@Composable
private fun RulesRestCell(
    label: String,
    seconds: Int,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val display = KpknSetFormat.rest(seconds)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetCaption(label)
        KpknNumberCell(
            value = display,
            input = KpknCellInput.Picker(onOpen = onOpen),
            contentDescription = "Descanso $label, $display",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Flecha de plegar: ▲ desplegado, ▼ plegado (20 dp, en `textMuted`). */
@Composable
private fun ExpandChevron(expanded: Boolean) {
    Icon(
        imageVector = if (expanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
        contentDescription = if (expanded) "Colapsar" else "Desplegar",
        tint = WizardColors.textMuted,
        modifier = Modifier.size(20.dp),
    )
}

/** Barra fija de abajo: «Aplicar» como píldora de tinta, sobre el vidrio de la hoja. */
@Composable
internal fun StickyActionBar(
    modifier: Modifier = Modifier,
    onApply: () -> Unit,
) {
    val hazeState = LocalHazeState.current
    val barShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .kpknGlassOrFallback(hazeState, barShape)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpknPrimaryPill(
            text = "Aplicar",
            onClick = onApply,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
