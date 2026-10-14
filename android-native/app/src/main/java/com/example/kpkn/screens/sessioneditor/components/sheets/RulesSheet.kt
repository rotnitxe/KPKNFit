package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.domain.sessionassistant.TimeCoachFatigueDelta
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.DefaultIntensityType
import com.example.kpkn.screens.sessioneditor.RuleScope
import com.example.kpkn.screens.sessioneditor.RuleTemplate
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** Pestañas de la hoja: 0 «Reglas» (ajustes por defecto y plantillas) y 1 «Tiempo» (límites y reloj). */
private const val RULES_TAB = 0
private const val TIME_TAB = 1

/** Intensidades en el orden de su segmento. */
private val intensityOptions = listOf(
    DefaultIntensityType.RPE to "RPE",
    DefaultIntensityType.RIR to "RIR",
    DefaultIntensityType.FALLO to "Fallo",
)

/** Una fila de «Mis reglas» con su menú abierto: la plantilla y el rectángulo del botón `⋯` en la ventana. */
private data class RuleTemplateMenu(val template: RuleTemplate, val anchor: Rect)

/**
 * Hoja «Reglas de la sesión» (SE7). Dos pestañas: «Reglas», con los ajustes por defecto, el alcance, los compuestos y aislados y las
 * plantillas de «Mis reglas»; y «Tiempo», con los límites de la sesión, el reloj, las sugerencias y los tiempos por grupo. La lógica no
 * cambia: cada cifra se confirma al terminar de editarla y «Aplicar» aplica las reglas al alcance elegido.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    var activeTab by remember { mutableIntStateOf(RULES_TAB) }
    LaunchedEffect(uiState.rulesSheetInitialTab) {
        if (uiState.rulesSheetInitialTab == TIME_TAB) {
            activeTab = TIME_TAB
            onInitialTabConsumed()
        }
    }
    LaunchedEffect(activeTab) {
        if (activeTab == TIME_TAB) onRefreshTimeCoach()
    }
    var scopePartId by remember { mutableStateOf<String?>(null) }
    var selectedScope by remember(uiState.ruleDefaults.scope) { mutableStateOf(uiState.ruleDefaults.scope) }
    var saveTemplateName by remember { mutableStateOf<String?>(null) }
    var renameTemplate by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteTemplate by remember { mutableStateOf<RuleTemplate?>(null) }
    var templateMenu by remember { mutableStateOf<RuleTemplateMenu?>(null) }
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
            Text(text = "Reglas de la sesión", style = KpknType.title, color = WizardColors.text)
            Text(
                text = if (activeTab == RULES_TAB) {
                    "Ajustes por defecto de series, intensidad y descansos para nuevos ejercicios."
                } else {
                    "Presupuesto de duración y ajustes para entrar en tu límite."
                },
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            KpknSegmentedControl(
                options = listOf("Reglas", "Tiempo"),
                selectedIndex = activeTab,
                onSelect = { activeTab = it },
                modifier = Modifier.fillMaxWidth(),
            )

            if (activeTab == RULES_TAB) {
                KpknModule {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        KpknModuleHeader(label = "Ajustes por defecto")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Alcance de las reglas", style = KpknType.caption, color = WizardColors.textMuted)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                KpknChip(
                                    text = "Toda la sesión",
                                    selected = selectedScope == RuleScope.ALL_SESSION,
                                    onClick = { selectScope(RuleScope.ALL_SESSION) },
                                )
                                KpknChip(
                                    text = "Por grupo",
                                    selected = selectedScope == RuleScope.PER_GROUP,
                                    onClick = { selectScope(RuleScope.PER_GROUP) },
                                )
                                KpknChip(
                                    text = "Compuestos / Aislados",
                                    selected = selectedScope == RuleScope.COMPOUND_ISOLATION,
                                    onClick = { selectScope(RuleScope.COMPOUND_ISOLATION) },
                                )
                            }
                            if (selectedScope == RuleScope.PER_GROUP) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            IntensityControl(
                                selected = defaults.intensityType,
                                onSelect = { type ->
                                    onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, null, null, type)
                                },
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RuleValueCell(
                                    label = "Series",
                                    value = defaults.setCount.toString(),
                                    keyboardType = KeyboardType.Number,
                                    unit = null,
                                    modifier = Modifier.weight(1f),
                                    onCommit = { onRuleDefaultsChange(scopePartId, it.safeIntOrNull(), null, null, null, null, null, null, null, null) },
                                )
                                RuleValueCell(
                                    label = "Reps",
                                    value = defaults.reps.toString(),
                                    keyboardType = KeyboardType.Number,
                                    unit = null,
                                    modifier = Modifier.weight(1f),
                                    onCommit = { onRuleDefaultsChange(scopePartId, null, it.safeIntOrNull(), null, null, null, null, null, null, null) },
                                )
                                if (defaults.intensityType != DefaultIntensityType.FALLO) {
                                    RuleValueCell(
                                        label = if (defaults.intensityType == DefaultIntensityType.RPE) "RPE" else "RIR",
                                        value = formatEditableNumber(defaults.rpe),
                                        keyboardType = KeyboardType.Decimal,
                                        unit = null,
                                        modifier = Modifier.weight(1f),
                                        onCommit = { onRuleDefaultsChange(scopePartId, null, null, it.safeDoubleOrNull(), null, null, null, null, null, null) },
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "Descansos", style = KpknType.bodyStrong, color = WizardColors.text)
                                Text(
                                    text = "Descanso base para series, lados y superseries.",
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    RestCell(
                                        label = "Normal",
                                        seconds = defaults.normalRestSeconds,
                                        modifier = Modifier.weight(1f),
                                        onOpen = { activeRestDialog = "normal" },
                                    )
                                    RestCell(
                                        label = "Lados",
                                        seconds = defaults.betweenSidesRestSeconds,
                                        modifier = Modifier.weight(1f),
                                        onOpen = { activeRestDialog = "sides" },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    RestCell(
                                        label = "Entre ejercicios",
                                        seconds = defaults.supersetBetweenRestSeconds,
                                        modifier = Modifier.weight(1f),
                                        onOpen = { activeRestDialog = "between" },
                                    )
                                    RestCell(
                                        label = "Rondas",
                                        seconds = defaults.supersetRoundRestSeconds,
                                        modifier = Modifier.weight(1f),
                                        onOpen = { activeRestDialog = "round" },
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Aplicar a nuevos elementos",
                                        style = KpknType.bodyStrong,
                                        color = WizardColors.text,
                                    )
                                    Text(
                                        text = "Ejercicios, series, lados y superseries nuevos heredan estos valores.",
                                        style = KpknType.caption,
                                        color = WizardColors.textMuted,
                                    )
                                }
                                KpknSwitch(
                                    checked = defaults.applyToNewItems,
                                    onCheckedChange = {
                                        onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, null, it, null)
                                    },
                                )
                            }
                        } else {
                            RulesDisclosure(
                                title = "Compuestos / Aislados",
                                caption = "Configura una regla para compuestos y otra para aislamientos.",
                                expanded = compoundIsolationExpanded,
                                onToggle = { compoundIsolationExpanded = !compoundIsolationExpanded },
                            )
                            if (compoundIsolationExpanded) {
                                CompoundRuleBlock(
                                    title = "Compuestos",
                                    restSeconds = defaults.compoundRestSeconds ?: defaults.normalRestSeconds,
                                    reps = defaults.compoundReps ?: defaults.reps,
                                    rpe = defaults.compoundRpe ?: defaults.rpe,
                                    intensity = defaults.compoundIntensityType ?: defaults.intensityType,
                                    onOpenRest = { activeRestDialog = "compound" },
                                    onRepsCommit = { value ->
                                        onPatchRuleDefaults(scopePartId) { d -> d.copy(compoundReps = value.safeIntOrNull()) }
                                    },
                                    onRpeCommit = { value ->
                                        onPatchRuleDefaults(scopePartId) { d -> d.copy(compoundRpe = value.safeDoubleOrNull()) }
                                    },
                                    onIntensity = { type ->
                                        onPatchRuleDefaults(scopePartId) { d -> d.copy(compoundIntensityType = type) }
                                    },
                                )
                                CompoundRuleBlock(
                                    title = "Aislamientos",
                                    restSeconds = defaults.isolationRestSeconds ?: defaults.normalRestSeconds,
                                    reps = defaults.isolationReps ?: defaults.reps,
                                    rpe = defaults.isolationRpe ?: defaults.rpe,
                                    intensity = defaults.isolationIntensityType ?: defaults.intensityType,
                                    onOpenRest = { activeRestDialog = "isolation" },
                                    onRepsCommit = { value ->
                                        onPatchRuleDefaults(scopePartId) { d -> d.copy(isolationReps = value.safeIntOrNull()) }
                                    },
                                    onRpeCommit = { value ->
                                        onPatchRuleDefaults(scopePartId) { d -> d.copy(isolationRpe = value.safeDoubleOrNull()) }
                                    },
                                    onIntensity = { type ->
                                        onPatchRuleDefaults(scopePartId) { d -> d.copy(isolationIntensityType = type) }
                                    },
                                )
                            }
                        }
                    }
                }

                KpknModule {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpknModuleHeader(
                            label = "Mis reglas",
                        )
                        if (uiState.ruleTemplates.isEmpty()) {
                            Text(
                                text = "Aún no hay reglas guardadas. Guarda las de arriba para reutilizarlas.",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        }
                        uiState.ruleTemplates.forEachIndexed { index, template ->
                            if (index > 0) SheetDivider()
                            RuleTemplateRow(
                                template = template,
                                onApply = {
                                    onApplyRuleTemplate(template.id, scopePartId)
                                    if (template.defaults.hasCompoundOverrides || template.defaults.hasIsolationOverrides) {
                                        compoundIsolationExpanded = true
                                    }
                                },
                                onOpenMenu = { anchor -> templateMenu = RuleTemplateMenu(template, anchor) },
                            )
                        }
                        KpknSecondaryPill(
                            text = "Guardar como plantilla…",
                            onClick = { saveTemplateName = "" },
                            modifier = Modifier.fillMaxWidth(),
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
                    val sessionBudget = session.targetDurationMinutes ?: 0

                    KpknModule {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpknModuleHeader(label = "Límites")
                            Text(
                                text = "Cuenta atrás en el entreno. No corta la sesión.",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(30, 45, 60, 90).forEach { mins ->
                                    KpknChip(
                                        text = "$mins min",
                                        selected = session.targetDurationMinutes == mins,
                                        onClick = { setTargetDuration(mins) },
                                    )
                                }
                                if (estimated > 0) {
                                    KpknChip(
                                        text = "Estimado",
                                        selected = session.targetDurationMinutes == estimated,
                                        onClick = { setTargetDuration(estimated) },
                                    )
                                }
                                KpknChip(
                                    text = "Sin límite",
                                    selected = session.targetDurationMinutes == null,
                                    onClick = { setTargetDuration(null) },
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Tiempo de sesión",
                                    style = KpknType.bodyStrong,
                                    color = WizardColors.text,
                                    modifier = Modifier.weight(1f),
                                )
                                val display = formatDurationMinutes(session.targetDurationMinutes)
                                KpknNumberCell(
                                    value = display,
                                    input = KpknCellInput.Picker(
                                        onOpen = {
                                            durationPickerTarget = DurationPickerTarget.Global(
                                                currentMinutes = session.targetDurationMinutes,
                                            )
                                        },
                                    ),
                                    contentDescription = "Tiempo de sesión, $display",
                                    modifier = Modifier.width(144.dp),
                                )
                            }
                            if (sessionBudget > 0) {
                                val isOverBudget = assignedMinutes > sessionBudget
                                val remaining = sessionBudget - assignedMinutes
                                Text(
                                    text = if (isOverBudget) {
                                        "Asignados $assignedMinutes min; el tiempo de sesión es $sessionBudget min"
                                    } else {
                                        "$assignedMinutes de $sessionBudget min asignados ($remaining min libres)"
                                    },
                                    style = KpknType.caption,
                                    color = if (isOverBudget) WizardColors.danger else WizardColors.text,
                                )
                            }
                        }
                    }

                    KpknModule {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpknModuleHeader(label = "Tiempo")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TimeStat(
                                    label = "Estimado",
                                    value = if (estimated > 0) "≈ $estimated" else "—",
                                    unit = if (estimated > 0) "min" else null,
                                    color = WizardColors.text,
                                )
                                TimeStat(
                                    label = "Límite",
                                    value = limit?.toString() ?: "Sin límite",
                                    unit = limit?.let { "min" },
                                    color = WizardColors.text,
                                )
                                TimeStat(
                                    label = "Diferencia",
                                    value = when {
                                        gap == null -> "—"
                                        gap > 0 -> "+$gap"
                                        gap < 0 -> "−${-gap}"
                                        else -> "0"
                                    },
                                    unit = if (gap != null && gap != 0) "min" else null,
                                    color = when {
                                        gap == null -> WizardColors.textMuted
                                        gap > 0 -> WizardColors.danger
                                        gap < 0 -> SymbolPalette.ok
                                        else -> WizardColors.text
                                    },
                                )
                            }
                            uiState.sessionTimeBreakdown?.let { bd ->
                                Text(
                                    text = "Prep ${bd.setupMinutes} · Ejec ${bd.executionMinutes} · Descansos ${bd.restMinutes}" +
                                        if (bd.warmupMinutes > 0) " · Calentamiento ${bd.warmupMinutes}" else "",
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            }
                            if (uiState.timeCoachSuggestions.isNotEmpty()) {
                                SheetDivider()
                                Text(
                                    text = "Para entrar en tu límite",
                                    style = KpknType.bodyStrong,
                                    color = WizardColors.text,
                                )
                                uiState.timeCoachSuggestions.forEach { suggestion ->
                                    TimeCoachRow(
                                        title = suggestion.title,
                                        explanation = suggestion.explanation,
                                        minutesSaved = suggestion.minutesSaved,
                                        fatigue = when (suggestion.fatigueDelta) {
                                            TimeCoachFatigueDelta.LOWER -> "Fatiga ↓"
                                            TimeCoachFatigueDelta.SIMILAR -> "Fatiga ≈"
                                            TimeCoachFatigueDelta.HIGHER -> "Fatiga ↑"
                                        },
                                        onDismiss = { onDismissTimeCoachSuggestion(suggestion.id) },
                                        onApply = { onApplyTimeCoachSuggestion(suggestion.id) },
                                    )
                                }
                            } else if (limit != null && gap != null && gap > 0) {
                                Text(
                                    text = "Calculando sugerencias… o no hay ajustes seguros aún.",
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            }

                            SheetDivider()
                            RulesDisclosure(
                                title = "Tiempos por grupos y ejercicios",
                                caption = if (groupTimesExpanded) {
                                    "Por grupo o ejercicio. El del grupo vale."
                                } else {
                                    "Tocar para tiempos por grupo"
                                },
                                expanded = groupTimesExpanded,
                                onToggle = { groupTimesExpanded = !groupTimesExpanded },
                            )
                            if (groupTimesExpanded) {
                                if (session.parts.size >= 2 && (session.targetDurationMinutes ?: 0) > 0) {
                                    KpknSecondaryPill(
                                        text = "Repartir global en grupos",
                                        onClick = onDistributeTargetAcrossParts,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                session.parts.forEach { part ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = part.name,
                                            style = KpknType.bodyStrong,
                                            color = WizardColors.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        DurationCell(
                                            name = part.name,
                                            minutes = part.targetDurationMinutes,
                                            onOpen = {
                                                durationPickerTarget = DurationPickerTarget.Part(
                                                    partId = part.id,
                                                    title = part.name,
                                                    currentMinutes = part.targetDurationMinutes,
                                                )
                                            },
                                        )
                                    }
                                    part.exercises.forEach { ex ->
                                        ExerciseDurationRow(
                                            name = ex.name,
                                            minutes = ex.targetDurationMinutes,
                                            indented = true,
                                            onOpen = {
                                                durationPickerTarget = DurationPickerTarget.Exercise(
                                                    exerciseId = ex.id,
                                                    title = ex.name,
                                                    currentMinutes = ex.targetDurationMinutes,
                                                )
                                            },
                                        )
                                    }
                                }
                                if (session.exercises.isNotEmpty()) {
                                    SheetDivider()
                                    Text(
                                        text = "Otros ejercicios",
                                        style = KpknType.bodyStrong,
                                        color = WizardColors.text,
                                    )
                                    session.exercises.forEach { ex ->
                                        ExerciseDurationRow(
                                            name = ex.name,
                                            minutes = ex.targetDurationMinutes,
                                            indented = false,
                                            onOpen = {
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
            }
        }

        StickyActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onApply = {
                // Confirma las cifras en edición (KpknNumberCell lo hace al perder el foco) antes de aplicar.
                focusManager.clearFocus(force = true)
                val outcome = onApplyRules(activeScopePartId)
                if (outcome is ApplyRulesOutcome.Applied) onDismiss()
            },
        )
    }

    templateMenu?.let { menu ->
        KpknContextMenu(anchor = menu.anchor, rows = 2, onDismiss = { templateMenu = null }) {
            KpknContextMenuRow(
                label = "Renombrar",
                onClick = {
                    templateMenu = null
                    renameTemplate = menu.template.id to menu.template.name
                },
            )
            KpknContextMenuRow(
                label = "Eliminar",
                destructive = true,
                onClick = {
                    templateMenu = null
                    deleteTemplate = menu.template
                },
            )
        }
    }

    deleteTemplate?.let { template ->
        KpknAlertDialog(
            onDismissRequest = { deleteTemplate = null },
            title = "Eliminar plantilla",
            text = "¿Eliminar la plantilla «${template.name}»?",
            confirmLabel = "Eliminar",
            onConfirm = {
                onDeleteRuleTemplate(template.id)
                deleteTemplate = null
            },
            dismissLabel = "Cancelar",
            onDismiss = { deleteTemplate = null },
            destructive = true,
        )
    }

    saveTemplateName?.let { draftName ->
        var localName by remember(draftName) { mutableStateOf(draftName) }
        KpknAlertDialog(
            onDismissRequest = { saveTemplateName = null },
            title = { Text("Guardar plantilla") },
            text = {
                KpknTextField(
                    value = localName,
                    onValueChange = { localName = it },
                    label = "Nombre",
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
            title = { Text("Renombrar plantilla") },
            text = {
                KpknTextField(
                    value = localName,
                    onValueChange = { localName = it },
                    label = "Nombre",
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

/** Selector de intensidad (RPE, RIR o Fallo) en un segmentado. */
@Composable
private fun IntensityControl(
    selected: DefaultIntensityType,
    onSelect: (DefaultIntensityType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Intensidad", style = KpknType.caption, color = WizardColors.textMuted)
        KpknSegmentedControl(
            options = intensityOptions.map { it.second },
            selectedIndex = intensityOptions.indexOfFirst { it.first == selected },
            onSelect = { index -> onSelect(intensityOptions[index].first) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Una cifra de regla: su nombre en `caption` encima y la celda editable, con la unidad al lado en `caption`. Confirma al terminar de
 * escribir (lo que haga la celda) con el texto tal cual; quien llama lo convierte.
 */
@Composable
private fun RuleValueCell(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    unit: String?,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KpknNumberCell(
                value = value,
                input = KpknCellInput.Text(keyboardType = keyboardType, onCommit = onCommit),
                contentDescription = if (unit != null) "$label, $value $unit" else "$label, $value",
                modifier = Modifier.weight(1f),
            )
            if (unit != null) {
                Spacer(Modifier.width(6.dp))
                Text(text = unit, style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
    }
}

/** Un descanso de la regla: su nombre en `caption` encima y la celda en m:ss, que abre el selector de tiempo. */
@Composable
private fun RestCell(
    label: String,
    seconds: Int,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val display = KpknSetFormat.rest(seconds)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
        KpknNumberCell(
            value = display,
            input = KpknCellInput.Picker(onOpen = onOpen),
            contentDescription = "Descanso $label, $display",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Un bloque de compuestos o de aislamientos: descanso, repeticiones, RPE o RIR e intensidad. Cada valor se confirma como los de arriba.
 */
@Composable
private fun CompoundRuleBlock(
    title: String,
    restSeconds: Int,
    reps: Int,
    rpe: Double,
    intensity: DefaultIntensityType,
    onOpenRest: () -> Unit,
    onRepsCommit: (String) -> Unit,
    onRpeCommit: (String) -> Unit,
    onIntensity: (DefaultIntensityType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            RestCell(label = "Descanso", seconds = restSeconds, modifier = Modifier.weight(1f), onOpen = onOpenRest)
            RuleValueCell(
                label = "Reps",
                value = reps.toString(),
                keyboardType = KeyboardType.Number,
                unit = null,
                modifier = Modifier.weight(1f),
                onCommit = onRepsCommit,
            )
            RuleValueCell(
                label = if (intensity == DefaultIntensityType.RIR) "RIR" else "RPE",
                value = formatEditableNumber(rpe),
                keyboardType = KeyboardType.Decimal,
                unit = null,
                modifier = Modifier.weight(1f),
                onCommit = onRpeCommit,
            )
        }
        IntensityControl(selected = intensity, onSelect = onIntensity)
    }
}

/** Una plantilla de «Mis reglas»: el nombre y su origen, «Aplicar» y, si es del usuario, el menú `⋯` con renombrar y eliminar. */
@Composable
private fun RuleTemplateRow(
    template: RuleTemplate,
    onApply: () -> Unit,
    onOpenMenu: (Rect) -> Unit,
) {
    var buttonBounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = template.name,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (template.isFactory) "De fábrica" else "Tuya",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        KpknTextAction(text = "Aplicar", onClick = onApply)
        // Hueco fijo de 48 dp para el menú: «Aplicar» queda en la misma columna, con o sin menú.
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (!template.isFactory) {
                Box(modifier = Modifier.onGloballyPositioned { buttonBounds = it.boundsInWindow() }) {
                    KpknIconButton(
                        icon = KpknLineIcons.More,
                        contentDescription = "Más opciones de ${template.name}",
                        onClick = { onOpenMenu(buttonBounds) },
                    )
                }
            }
        }
    }
}

/** Un ajuste de tiempo sugerido: título y minutos ahorrados, la explicación, la fatiga y «Descartar» o «Aplicar». */
@Composable
private fun TimeCoachRow(
    title: String,
    explanation: String,
    minutesSaved: Int,
    fatigue: String,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "−$minutesSaved min",
                style = KpknType.bodyStrong,
                color = SymbolPalette.ok,
            )
        }
        Text(text = explanation, style = KpknType.caption, color = WizardColors.textMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fatigue,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier.weight(1f),
            )
            KpknTextAction(text = "Descartar", onClick = onDismiss)
            Spacer(Modifier.width(12.dp))
            KpknPrimaryPill(text = "Aplicar", onClick = onApply)
        }
    }
}

/** Una cifra del reloj: la etiqueta en `caption` y el valor en `metricM` con su unidad en la misma línea base. */
@Composable
private fun TimeStat(
    label: String,
    value: String,
    unit: String?,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
        KpknMetric(value = value, unit = unit, style = KpknType.metricM, color = color)
    }
}

/** Una fila de tiempo de un ejercicio, con la celda en minutos a la derecha. [indented] la sangra bajo su grupo. */
@Composable
private fun ExerciseDurationRow(
    name: String,
    minutes: Int?,
    indented: Boolean,
    onOpen: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 12.dp else 0.dp),
    ) {
        Text(
            text = name,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DurationCell(name = name, minutes = minutes, onOpen = onOpen)
    }
}

/** La celda de un tiempo de grupo o de ejercicio: muestra el presupuesto (o «Sin límite») y abre el selector. */
@Composable
private fun DurationCell(
    name: String,
    minutes: Int?,
    onOpen: () -> Unit,
) {
    val display = formatDurationMinutes(minutes)
    KpknNumberCell(
        value = display,
        input = KpknCellInput.Picker(onOpen = onOpen),
        contentDescription = "Tiempo de $name, $display",
        modifier = Modifier.width(120.dp),
    )
}

/** Cabecera plegable de un bloque: título y resumen, con una flecha que gira al abrir. Toda la fila es el botón. */
@Composable
private fun RulesDisclosure(
    title: String,
    caption: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
            Text(text = caption, style = KpknType.caption, color = WizardColors.textMuted)
        }
        Icon(
            imageVector = KpknLineIcons.ChevronDown,
            contentDescription = if (expanded) "Ocultar" else "Mostrar",
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            tint = WizardColors.textMuted,
        )
    }
}

/** Línea separadora de 1 dp dentro de un módulo (`divider`). */
@Composable
private fun SheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}

/** Barra inferior de vidrio con «Aplicar» a todo el ancho. Va pegada al borde de la hoja. */
@Composable
internal fun StickyActionBar(
    modifier: Modifier = Modifier,
    onApply: () -> Unit,
) {
    val hazeState = LocalHazeState.current
    val barShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .kpknGlassOrFallback(hazeState, barShape)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpknPrimaryPill(
            text = "Aplicar",
            onClick = onApply,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Formato de duración de un presupuesto: «Sin límite», «45 min» o «1 h 30 min». */
private fun formatDurationMinutes(minutes: Int?): String {
    if (minutes == null || minutes <= 0) return "Sin límite"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "%d h %02d min".format(h, m)
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
