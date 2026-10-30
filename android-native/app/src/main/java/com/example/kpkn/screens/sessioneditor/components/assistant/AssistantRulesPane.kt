@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.kpknGradient
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionPreview
import com.example.kpkn.domain.sessionassistant.SessionLimitStatuses
import com.example.kpkn.domain.sessionassistant.TimeCoachFatigueDelta
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.DefaultIntensityType
import com.example.kpkn.screens.sessioneditor.RuleScope
import com.example.kpkn.screens.sessioneditor.RuleTemplate
import com.example.kpkn.screens.sessioneditor.RulesSection
import com.example.kpkn.screens.sessioneditor.SessionEditorRuleDefaults
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.components.sheets.DurationPickerTarget
import com.example.kpkn.screens.sessioneditor.components.sheets.RulesClockStatus
import com.example.kpkn.screens.sessioneditor.components.sheets.formatDurationMinutes
import com.example.kpkn.screens.sessioneditor.components.sheets.rulesClockStatusOf
import com.example.kpkn.screens.sessioneditor.sessionTimeBreakdownCaption
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.KpknRestPickerChain
import com.example.kpkn.ui.design.KpknSetFormat

/** Lo que Reglas puede hacer sobre la sesión: las mismas llamadas del editor que usaba la hoja «Reglas y tiempo». */
internal class AsRulesActions(
    val onApplyRules: (partId: String?) -> ApplyRulesOutcome,
    val onRuleDefaultsChange: (String?, Int?, Int?, Double?, Int?, Int?, Int?, Int?, Boolean?, DefaultIntensityType?) -> Unit,
    val onPatchRuleDefaults: (String?, (SessionEditorRuleDefaults) -> SessionEditorRuleDefaults) -> Unit,
    val onRuleLimitsChange: (Double?, Int?) -> Unit,
    val onAdvancedRuleLimitsChange: (Double?, Double?, Int?, Boolean) -> Unit,
    val onApplyGlobalIntensityAdjustment: (IntensityMode, Double, Set<String>?) -> Unit,
    val setTargetDuration: (Int?) -> Unit,
    val setPartTargetDuration: (String, Int?) -> Unit,
    val setExerciseTargetDuration: (String, Int?) -> Unit,
    val onDistributeTargetAcrossParts: () -> Unit,
    val onApplyRuleTemplate: (String, String?) -> Unit,
    val onSaveRuleTemplate: (String) -> Unit,
    val onRenameRuleTemplate: (String, String) -> Unit,
    val onDeleteRuleTemplate: (String) -> Unit,
    val onApplyTimeCoachSuggestion: (String) -> Unit,
    val onDismissTimeCoachSuggestion: (String) -> Unit,
) {
    companion object {
        /** Acciones vacías (hoja de dibujo y pruebas). */
        val None = AsRulesActions(
            onApplyRules = { ApplyRulesOutcome.NoChanges },
            onRuleDefaultsChange = { _, _, _, _, _, _, _, _, _, _ -> },
            onPatchRuleDefaults = { _, _ -> },
            onRuleLimitsChange = { _, _ -> },
            onAdvancedRuleLimitsChange = { _, _, _, _ -> },
            onApplyGlobalIntensityAdjustment = { _, _, _ -> },
            setTargetDuration = {},
            setPartTargetDuration = { _, _ -> },
            setExerciseTargetDuration = { _, _ -> },
            onDistributeTargetAcrossParts = {},
            onApplyRuleTemplate = { _, _ -> },
            onSaveRuleTemplate = {},
            onRenameRuleTemplate = { _, _ -> },
            onDeleteRuleTemplate = {},
            onApplyTimeCoachSuggestion = {},
            onDismissTimeCoachSuggestion = {},
        )
    }
}

private val ScopeLabels = listOf("Sesión", "Grupo", "Comp. y aisl.")
private val ScopeOrder = listOf(RuleScope.ALL_SESSION, RuleScope.PER_GROUP, RuleScope.COMPOUND_ISOLATION)
private val ScopeSpoken = listOf("Toda la sesión", "Por grupo", "Compuestos y aislados")
private val IntensityLabels = listOf("RPE", "RIR", "Fallo")
private val IntensityTypes = listOf(DefaultIntensityType.RPE, DefaultIntensityType.RIR, DefaultIntensityType.FALLO)

private fun fm(value: Double) = AssistantSuggestionPreview.number(value)

/**
 * Pestaña Reglas (`as-reg-*`): tres apartados, Valores · Límites · Tiempo. «Aplicar a la sesión» queda fijo solo en Valores.
 * Las reglas son las de la hoja «Reglas y tiempo» de antes; cambia la piel y el sitio donde viven.
 */
@Composable
internal fun AssistantRulesPane(
    uiState: SessionEditorUiState,
    actions: AsRulesActions,
    limitStatuses: SessionLimitStatuses?,
    clashBanner: AsClashBanner?,
    section: RulesSection,
    onSection: (RulesSection) -> Unit,
    onGoSuggestions: () -> Unit,
    onToast: (title: String, sub: String?) -> Unit,
    onApplied: () -> Unit,
    modifier: Modifier = Modifier,
    initialScope: RuleScope? = null,
) {
    val session = uiState.activeVariantSession ?: uiState.session
    var selectedScope by rememberSaveable(uiState.ruleDefaults.scope) { mutableStateOf(initialScope ?: uiState.ruleDefaults.scope) }
    var scopePartId by rememberSaveable { mutableStateOf<String?>(null) }
    val activePartId = scopePartId.takeIf { selectedScope == RuleScope.PER_GROUP }
    val defaults = remember(selectedScope, activePartId, uiState.ruleDefaults, uiState.partRuleDefaults) {
        val source = if (activePartId == null) uiState.ruleDefaults else (uiState.partRuleDefaults[activePartId] ?: uiState.ruleDefaults)
        source.copy(scope = selectedScope)
    }
    var restDialog by remember { mutableStateOf<String?>(null) }
    var durationTarget by remember { mutableStateOf<DurationPickerTarget?>(null) }

    RestDialog(restDialog, defaults, activePartId, actions) { restDialog = null }
    durationTarget?.let { target ->
        val total = target.currentMinutes?.coerceAtLeast(0) ?: 0
        KpknNativeTimePickerDialog(
            title = target.title,
            initialHour = (total / 60).coerceIn(0, 23),
            initialMinute = (total % 60).coerceIn(0, 59),
            hint = "Horas : minutos de presupuesto",
            onConfirm = { hour, minute ->
                val mins = (hour * 60 + minute).takeIf { it > 0 }
                when (target) {
                    is DurationPickerTarget.Global -> actions.setTargetDuration(mins)
                    is DurationPickerTarget.Part -> actions.setPartTargetDuration(target.partId, mins)
                    is DurationPickerTarget.Exercise -> actions.setExerciseTargetDuration(target.exerciseId, mins)
                }
                durationTarget = null
            },
            onDismiss = { durationTarget = null },
        )
    }

    Box(modifier.fillMaxSize()) {
        val footer = section == RulesSection.VALUES
        AsScrollPane(Modifier.fillMaxSize(), bottomPadding = if (footer) 128.dp else 36.dp) {
            val clashDot = limitStatuses?.anyClash == true
            AsSegmented(
                options = RulesSection.entries.map { it.label },
                selected = section.ordinal,
                onSelect = { onSection(RulesSection.entries[it]) },
                descriptions = RulesSection.entries.map { if (it == RulesSection.LIMITS && clashDot) "${it.label}, hay un choque" else it.label },
                dots = if (clashDot) setOf(RulesSection.LIMITS.ordinal) else emptySet(),
            )
            when (section) {
                RulesSection.VALUES -> ValuesSection(
                    uiState = uiState,
                    session = session,
                    defaults = defaults,
                    selectedScope = selectedScope,
                    scopePartId = scopePartId,
                    actions = actions,
                    onScope = { scope ->
                        selectedScope = scope
                        if (scope != RuleScope.PER_GROUP) scopePartId = null
                        if (scope == RuleScope.PER_GROUP && scopePartId == null) scopePartId = session?.parts?.firstOrNull()?.id
                        val target = scopePartId.takeIf { scope == RuleScope.PER_GROUP }
                        actions.onPatchRuleDefaults(target) { it.copy(scope = scope) }
                    },
                    onPart = { scopePartId = it },
                    onRest = { restDialog = it },
                    onToast = onToast,
                )
                RulesSection.LIMITS -> LimitsSection(uiState, actions, limitStatuses, clashBanner, onGoSuggestions)
                RulesSection.TIME -> TimeSection(uiState, actions, onOpenDuration = { durationTarget = it }, onRest = { restDialog = it }, onToast = onToast)
            }
        }
        if (footer) {
            val count = if (activePartId != null) session?.parts?.firstOrNull { it.id == activePartId }?.exercises?.size ?: 0 else session?.allExercises()?.size ?: 0
            // as-G01 as-foot — `linear-gradient(#1b1b1d00, #1b1b1d 30%)` sobre el contenido (preset velo-hoja)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .kpknGradient(KpknGradient.VeloHoja)
                    .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsButton(
                    "Aplicar a la sesión", Modifier.fillMaxWidth(), leading = AsIcons.Check,
                    onClick = {
                        when (val outcome = actions.onApplyRules(activePartId)) {
                            is ApplyRulesOutcome.Applied -> {
                                onToast(
                                    "Reglas aplicadas a ${outcome.exercisesChanged} ${if (outcome.exercisesChanged == 1) "ejercicio" else "ejercicios"}",
                                    "Series, repeticiones, esfuerzo y descansos actualizados.",
                                )
                                onApplied()
                            }
                            ApplyRulesOutcome.NoChanges -> onToast("Sin cambios", "Las series ya tenían estos valores.")
                            is ApplyRulesOutcome.ScopeNotFound -> onToast("Ese grupo no existe en esta sesión", null)
                        }
                    },
                )
                AsText(
                    "Aplica a los $count ${if (count == 1) "ejercicio" else "ejercicios"} y sustituye lo ya escrito",
                    12f, Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = AsColors.faint, lineHeight = 1.4f, align = TextAlign.Center,
                )
            }
        }
    }
}

/** El aviso de arriba de Límites cuando la sesión choca con un máximo. */
internal data class AsClashBanner(val strong: String, val rest: String)

// ── Selector de descanso ─────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun RestDialog(kind: String?, defaults: SessionEditorRuleDefaults, partId: String?, actions: AsRulesActions, onDismiss: () -> Unit) {
    if (kind == null) return
    val (title, seconds, confirm) = when (kind) {
        "normal" -> Triple("Descanso de series", defaults.normalRestSeconds) { s: Int -> actions.onRuleDefaultsChange(partId, null, null, null, s, null, null, null, null, null) }
        "sides" -> Triple("Descanso entre lados", defaults.betweenSidesRestSeconds) { s: Int -> actions.onRuleDefaultsChange(partId, null, null, null, null, s, null, null, null, null) }
        "between" -> Triple("Descanso entre ejercicios", defaults.supersetBetweenRestSeconds) { s: Int -> actions.onRuleDefaultsChange(partId, null, null, null, null, null, s, null, null, null) }
        "round" -> Triple("Descanso de rondas", defaults.supersetRoundRestSeconds) { s: Int -> actions.onRuleDefaultsChange(partId, null, null, null, null, null, null, s, null, null) }
        "compound" -> Triple("Descanso compuestos", defaults.compoundRestSeconds ?: defaults.normalRestSeconds) { s: Int -> actions.onPatchRuleDefaults(partId) { it.copy(compoundRestSeconds = s) } }
        "isolation" -> Triple("Descanso aislamientos", defaults.isolationRestSeconds ?: defaults.normalRestSeconds) { s: Int -> actions.onPatchRuleDefaults(partId) { it.copy(isolationRestSeconds = s) } }
        else -> return
    }
    // El selector de descanso de la app (no se redibuja): se abre desde cada celda de descanso.
    KpknRestPickerChain(primaryTitle = title, primarySeconds = seconds, onConfirm = { s, _ -> confirm(s); onDismiss() }, onDismiss = onDismiss)
}

private fun rest(seconds: Int) = KpknSetFormat.rest(seconds)

// ── Valores ──────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ValuesSection(
    uiState: SessionEditorUiState,
    session: com.example.kpkn.data.models.Session?,
    defaults: SessionEditorRuleDefaults,
    selectedScope: RuleScope,
    scopePartId: String?,
    actions: AsRulesActions,
    onScope: (RuleScope) -> Unit,
    onPart: (String) -> Unit,
    onRest: (String) -> Unit,
    onToast: (String, String?) -> Unit,
) {
    val partId = scopePartId.takeIf { selectedScope == RuleScope.PER_GROUP }
    AsModule {
        AsModuleHeader("Alcance de las reglas")
        AsSegmented(ScopeLabels, ScopeOrder.indexOf(selectedScope), { onScope(ScopeOrder[it]) }, descriptions = ScopeSpoken)
        if (selectedScope == RuleScope.PER_GROUP) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                session?.parts?.forEach { part -> AsChip(part.name, on = scopePartId == part.id, onClick = { onPart(part.id) }) }
            }
        }
        AsCaption(
            when (selectedScope) {
                RuleScope.COMPOUND_ISOLATION -> "Una regla para los compuestos y otra para los aislamientos."
                RuleScope.PER_GROUP -> "Las reglas de este grupo se aplican solo a él."
                RuleScope.ALL_SESSION -> "Series, intensidad y descansos por defecto para toda la sesión."
            },
            Modifier.padding(top = 10.dp),
        )
    }
    if (selectedScope != RuleScope.COMPOUND_ISOLATION) {
        AsModule {
            AsModuleHeader("Valores de serie")
            AsSegmented(IntensityLabels, IntensityTypes.indexOf(defaults.intensityType).coerceAtLeast(0), {
                actions.onRuleDefaultsChange(partId, null, null, null, null, null, null, null, null, IntensityTypes[it])
            })
            Column(Modifier.padding(top = 6.dp)) {
                AsRuleRow("Series", first = true) {
                    StepperFor(defaults.setCount.toString(), "series", { actions.onRuleDefaultsChange(partId, (defaults.setCount - 1).coerceIn(1, 12), null, null, null, null, null, null, null, null) },
                        { actions.onRuleDefaultsChange(partId, (defaults.setCount + 1).coerceIn(1, 12), null, null, null, null, null, null, null, null) })
                }
                AsRuleRow("Repeticiones") {
                    StepperFor(defaults.reps.toString(), "repeticiones", { actions.onRuleDefaultsChange(partId, null, (defaults.reps - 1).coerceIn(1, 50), null, null, null, null, null, null, null) },
                        { actions.onRuleDefaultsChange(partId, null, (defaults.reps + 1).coerceIn(1, 50), null, null, null, null, null, null, null) })
                }
                if (defaults.intensityType != DefaultIntensityType.FALLO) {
                    val label = if (defaults.intensityType == DefaultIntensityType.RIR) "RIR" else "RPE"
                    val min = if (defaults.intensityType == DefaultIntensityType.RIR) 0.0 else 1.0
                    AsRuleRow(label) {
                        StepperFor(fm(defaults.rpe), label, { actions.onRuleDefaultsChange(partId, null, null, (defaults.rpe - 0.5).coerceIn(min, 10.0), null, null, null, null, null, null) },
                            { actions.onRuleDefaultsChange(partId, null, null, (defaults.rpe + 0.5).coerceIn(min, 10.0), null, null, null, null, null, null) })
                    }
                }
            }
        }
        AsModule {
            AsModuleHeader("Descansos")
            AsRuleRow("Entre series", sub = "Descanso de series", first = true) { AsPicker(rest(defaults.normalRestSeconds), "Entre series") { onRest("normal") } }
            AsRuleRow("Entre lados", sub = "Ejercicios unilaterales") { AsPicker(rest(defaults.betweenSidesRestSeconds), "Entre lados") { onRest("sides") } }
            AsRuleRow("Entre ejercicios", sub = "Dentro de una superserie") { AsPicker(rest(defaults.supersetBetweenRestSeconds), "Entre ejercicios") { onRest("between") } }
            AsRuleRow("Entre rondas", sub = "Al cerrar una ronda de superserie") { AsPicker(rest(defaults.supersetRoundRestSeconds), "Entre rondas") { onRest("round") } }
        }
    } else {
        CompoundIsolationModules(defaults, partId, actions, onRest)
    }
    AsModule {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                AsText("Aplicar a nuevos elementos", 14.5f, weight = FontWeight.SemiBold)
                AsText("Ejercicios, series, lados y superseries nuevas heredan estos valores.", 12f, Modifier.padding(top = 2.dp), color = AsColors.faint, lineHeight = 1.35f)
            }
            AsSwitch(defaults.applyToNewItems, "Aplicar a nuevos elementos") { actions.onRuleDefaultsChange(partId, null, null, null, null, null, null, null, it, null) }
        }
    }
    RuleTemplatesModule(uiState.ruleTemplates, partId, actions)
    IntensityModule(uiState, defaults, actions, onToast)
}

@Composable
private fun StepperFor(value: String, label: String, onMinus: () -> Unit, onPlus: () -> Unit) =
    // as-A05 as-flash — la cifra que cambia parpadea del color de músculo a la tinta en 0,6 s (lo hace `AsStepper` con `asFlashColor`)
    AsStepper(value, label, onMinus, onPlus)

@Composable
private fun CompoundIsolationModules(defaults: SessionEditorRuleDefaults, partId: String?, actions: AsRulesActions, onRest: (String) -> Unit) {
    @Composable
    fun block(
        title: String,
        type: DefaultIntensityType?,
        restKey: String,
        restSeconds: Int?,
        reps: Int?,
        rpe: Double?,
        onType: (DefaultIntensityType) -> Unit,
        onReps: (Int) -> Unit,
        onRpe: (Double) -> Unit,
    ) {
        val effectiveType = type ?: defaults.intensityType
        val effectiveReps = reps ?: defaults.reps
        val effectiveRpe = rpe ?: defaults.rpe
        val label = if (effectiveType == DefaultIntensityType.RIR) "RIR" else "RPE"
        val min = if (effectiveType == DefaultIntensityType.RIR) 0.0 else 1.0
        AsModule {
            AsModuleHeader(title)
            AsSegmented(IntensityLabels, IntensityTypes.indexOf(effectiveType).coerceAtLeast(0), { onType(IntensityTypes[it]) })
            Column(Modifier.padding(top = 6.dp)) {
                AsRuleRow("Descanso", first = true) { AsPicker(rest(restSeconds ?: defaults.normalRestSeconds), "Descanso $title") { onRest(restKey) } }
                AsRuleRow("Repeticiones") { StepperFor("$effectiveReps", "repeticiones", { onReps((effectiveReps - 1).coerceIn(1, 50)) }, { onReps((effectiveReps + 1).coerceIn(1, 50)) }) }
                if (effectiveType != DefaultIntensityType.FALLO) {
                    AsRuleRow(label) { StepperFor(fm(effectiveRpe), label, { onRpe((effectiveRpe - 0.5).coerceIn(min, 10.0)) }, { onRpe((effectiveRpe + 0.5).coerceIn(min, 10.0)) }) }
                }
            }
        }
    }
    block("Compuestos", defaults.compoundIntensityType, "compound", defaults.compoundRestSeconds, defaults.compoundReps, defaults.compoundRpe,
        { t -> actions.onPatchRuleDefaults(partId) { it.copy(compoundIntensityType = t) } },
        { r -> actions.onPatchRuleDefaults(partId) { it.copy(compoundReps = r) } },
        { v -> actions.onPatchRuleDefaults(partId) { it.copy(compoundRpe = v) } })
    block("Aislamientos", defaults.isolationIntensityType, "isolation", defaults.isolationRestSeconds, defaults.isolationReps, defaults.isolationRpe,
        { t -> actions.onPatchRuleDefaults(partId) { it.copy(isolationIntensityType = t) } },
        { r -> actions.onPatchRuleDefaults(partId) { it.copy(isolationReps = r) } },
        { v -> actions.onPatchRuleDefaults(partId) { it.copy(isolationRpe = v) } })
}

@Composable
private fun RuleTemplatesModule(templates: List<RuleTemplate>, partId: String?, actions: AsRulesActions) {
    var saving by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<RuleTemplate?>(null) }
    var renaming by remember { mutableStateOf<RuleTemplate?>(null) }
    AsModule {
        AsModuleHeader("Plantillas de reglas")
        AsFlow {
            templates.forEach { template ->
                // Tocar aplica; mantener pulsada una propia ofrece renombrarla o eliminarla.
                AsChip(
                    template.name,
                    on = false,
                    modifier = if (template.isFactory) Modifier else Modifier.semantics { contentDescription = "${template.name}, mantén pulsado para renombrar o eliminar" },
                    onLongClick = if (template.isFactory) null else ({ optionsFor = template }),
                    onClick = { actions.onApplyRuleTemplate(template.id, partId) },
                )
            }
            AsChip("Guardar actuales", on = false, ghost = true, leading = AsIcons.Plus, onClick = { saving = true })
        }
        AsCaption("Toca una para precargar los valores. Las tuyas se pueden renombrar o eliminar.", Modifier.padding(top = 10.dp))
    }
    if (saving) NameDialog("Guardar plantilla de reglas", "", "Guardar", onConfirm = { actions.onSaveRuleTemplate(it); saving = false }, onDismiss = { saving = false })
    renaming?.let { template ->
        NameDialog("Renombrar plantilla", template.name, "Guardar", onConfirm = { actions.onRenameRuleTemplate(template.id, it); renaming = null }, onDismiss = { renaming = null })
    }
    optionsFor?.let { template ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { optionsFor = null }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(AsColors.decision).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsText(template.name, 19f, weight = FontWeight.Bold, family = AsSyne, lineHeight = 1.2f, letterSpacing = -0.3f)
                AsButton("Renombrar", Modifier.fillMaxWidth(), kind = AsButtonKind.GHOST, onClick = { renaming = template; optionsFor = null })
                AsButton("Eliminar", Modifier.fillMaxWidth(), kind = AsButtonKind.DANGER, onClick = { actions.onDeleteRuleTemplate(template.id); optionsFor = null })
                AsTextAction("Cancelar", onClick = { optionsFor = null })
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(AsColors.decision).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsText(title, 19f, weight = FontWeight.Bold, family = AsSyne, lineHeight = 1.2f, letterSpacing = -0.3f)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(AsColors.inset)
                    .border(1.dp, AsColors.white(0x1a), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    textStyle = TextStyle(color = AsColors.ink, fontFamily = AsInter, fontSize = asSp(15f)),
                    cursorBrush = SolidColor(AsColors.ink), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Nombre" },
                )
                if (name.isEmpty()) AsText("Nombre", 15f, color = AsColors.faint)
            }
            AsButton(confirm, Modifier.fillMaxWidth(), onClick = { onConfirm(name) })
            AsTextAction("Cancelar", onClick = onDismiss)
        }
    }
}

@Composable
private fun IntensityModule(uiState: SessionEditorUiState, defaults: SessionEditorRuleDefaults, actions: AsRulesActions, onToast: (String, String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(IntensityMode.RPE) }
    var value by rememberSaveable { mutableStateOf(defaults.rpe) }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    val session = uiState.activeVariantSession ?: uiState.session
    val muscles = remember(session) { session?.let { com.example.kpkn.screens.sessioneditor.buildMuscleVolumeRows(it).map { r -> r.muscle }.distinct() }.orEmpty() }
    AsModule {
        FoldHeader("Modificar intensidad", "Cambia la intensidad de toda la sesión, o de un músculo.", open) { open = !open }
        if (open) {
            // as-A06 as-fold.open as-fb2 — el cuerpo plegable entra en 0,22 s (asIn, dx 0)
            val modes = listOf(IntensityMode.RPE to "RPE", IntensityMode.RIR to "RIR", IntensityMode.FAILURE to "Fallo", IntensityMode.SOLO_RM to "% RM")
            Column(Modifier.asEnter(AsAnim.FoldBodyIn).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsSegmented(modes.map { it.second }, modes.indexOfFirst { it.first == mode }.coerceAtLeast(0), {
                    mode = modes[it].first
                    value = if (mode == IntensityMode.RPE) defaults.rpe else if (mode == IntensityMode.SOLO_RM) 80.0 else 2.0
                })
                if (mode != IntensityMode.FAILURE) {
                    val step = if (mode == IntensityMode.SOLO_RM) 2.5 else 0.5
                    val max = if (mode == IntensityMode.SOLO_RM) 100.0 else 10.0
                    AsRuleRow("Valor", first = true) {
                        AsStepper(fm(value), "valor", { value = (value - step).coerceIn(0.0, max) }, { value = (value + step).coerceIn(0.0, max) })
                    }
                }
                Column {
                    AsEyebrow("Alcance", Modifier.padding(bottom = 8.dp))
                    AsFlow {
                        AsChip("Toda la sesión", on = muscle == null, onClick = { muscle = null })
                        muscles.forEach { name -> AsChip(name, on = muscle == name, onClick = { muscle = name }) }
                    }
                }
                AsButton("Aplicar intensidad", small = true, onClick = {
                    val applied = if (mode == IntensityMode.FAILURE) 0.0 else value
                    actions.onApplyGlobalIntensityAdjustment(mode, applied, muscle?.let { setOf(it) })
                    onToast("Intensidad aplicada", "${muscle ?: "Toda la sesión"} · ${modes.first { it.first == mode }.second}${if (mode == IntensityMode.FAILURE) "" else " " + fm(value)}")
                })
            }
        }
    }
}

@Composable
private fun FoldHeader(title: String, sub: String, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = if (open) "$title, desplegado" else "$title, plegado" }.asClick(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            AsText(title, 14.5f, weight = FontWeight.SemiBold)
            AsText(sub, 12f, Modifier.padding(top = 2.dp), color = AsColors.faint)
        }
        // as-X03 as-fold.open as-fd .c — el chevron gira 180° · as-T04 as-fd .c — 0,22 s cubic-bezier(.2,.8,.2,1)
        val chevron = asFloatState(if (open) 180f else 0f, 220, AsAnim.Std)
        AsIcon(AsIcons.Down, 20.dp, AsColors.dim, Modifier.asRotation(chevron))
    }
}

// ── Límites ──────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun LimitsSection(uiState: SessionEditorUiState, actions: AsRulesActions, statuses: SessionLimitStatuses?, clash: AsClashBanner?, onGoSuggestions: () -> Unit) {
    val limits = uiState.ruleLimits
    val any = listOf(limits.maxRPE, limits.maxExercisesPerMuscle, limits.maxVolumePerMuscleSession, limits.maxVolumePerMuscleWeekly, limits.maxSamePatternPerSession).any { it != null }
    if (clash != null) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AsColors.tint(AsColors.danger, 0x0f))
                .border(1.dp, AsColors.tint(AsColors.danger, 0x29), RoundedCornerShape(18.dp)).padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsIcon(AsIcons.Warn, 20.dp, AsColors.energy)
            Row(Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = AsColors.ink, fontWeight = FontWeight.SemiBold)); append(clash.strong); pop()
                        append(" "); append(clash.rest)
                    },
                    style = TextStyle(color = AsColors.muted, fontFamily = AsInter, fontSize = asSp(13f), lineHeight = (13f * 1.4f).sp),
                )
            }
            AsLink("Ver", onClick = onGoSuggestions)
        }
    }
    if (!any) AsText("Sin límites. Pon un máximo y el asistente avisará cuando la sesión lo supere.", 12f, Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), color = AsColors.faint, lineHeight = 1.4f, align = TextAlign.Center)
    AsModule {
        AsModuleHeader("Máximos por sesión")
        LimitRow("RPE máximo", limits.maxRPE?.let(::fm), statuses?.maxRpe, first = true,
            onMinus = { actions.onRuleLimitsChange(stepLimit(limits.maxRPE, -1, 9.0, 0.5, 1.0), limits.maxExercisesPerMuscle) },
            onPlus = { actions.onRuleLimitsChange(stepLimit(limits.maxRPE, +1, 9.0, 0.5, 1.0).let { it?.coerceAtMost(10.0) }, limits.maxExercisesPerMuscle) })
        LimitRow("Ejercicios por músculo", limits.maxExercisesPerMuscle?.toString(), statuses?.exercisesPerMuscle, first = false,
            onMinus = { actions.onRuleLimitsChange(limits.maxRPE, stepLimit(limits.maxExercisesPerMuscle?.toDouble(), -1, 6.0, 1.0, 1.0)?.toInt()) },
            onPlus = { actions.onRuleLimitsChange(limits.maxRPE, stepLimit(limits.maxExercisesPerMuscle?.toDouble(), +1, 6.0, 1.0, 1.0)?.toInt()) })
        LimitRow("Series por músculo", limits.maxVolumePerMuscleSession?.let(::fm), statuses?.volumePerMuscleSession, first = false,
            onMinus = { actions.onAdvancedRuleLimitsChange(stepLimit(limits.maxVolumePerMuscleSession, -1, 10.0, 1.0, 1.0), limits.maxVolumePerMuscleWeekly, limits.maxSamePatternPerSession, limits.rigidLimits) },
            onPlus = { actions.onAdvancedRuleLimitsChange(stepLimit(limits.maxVolumePerMuscleSession, +1, 10.0, 1.0, 1.0), limits.maxVolumePerMuscleWeekly, limits.maxSamePatternPerSession, limits.rigidLimits) })
        LimitRow("Ejercicios del mismo patrón", limits.maxSamePatternPerSession?.toString(), statuses?.samePattern, first = false,
            onMinus = { actions.onAdvancedRuleLimitsChange(limits.maxVolumePerMuscleSession, limits.maxVolumePerMuscleWeekly, stepLimit(limits.maxSamePatternPerSession?.toDouble(), -1, 4.0, 1.0, 1.0)?.toInt(), limits.rigidLimits) },
            onPlus = { actions.onAdvancedRuleLimitsChange(limits.maxVolumePerMuscleSession, limits.maxVolumePerMuscleWeekly, stepLimit(limits.maxSamePatternPerSession?.toDouble(), +1, 4.0, 1.0, 1.0)?.toInt(), limits.rigidLimits) })
    }
    AsModule {
        AsModuleHeader("Por semana")
        LimitRow("Series por músculo", limits.maxVolumePerMuscleWeekly?.let(::fm), statuses?.volumePerMuscleWeekly, first = true,
            onMinus = { actions.onAdvancedRuleLimitsChange(limits.maxVolumePerMuscleSession, stepLimit(limits.maxVolumePerMuscleWeekly, -1, 24.0, 1.0, 1.0), limits.maxSamePatternPerSession, limits.rigidLimits) },
            onPlus = { actions.onAdvancedRuleLimitsChange(limits.maxVolumePerMuscleSession, stepLimit(limits.maxVolumePerMuscleWeekly, +1, 24.0, 1.0, 1.0), limits.maxSamePatternPerSession, limits.rigidLimits) })
    }
    AsModule {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                AsText("Límites rígidos", 14.5f, weight = FontWeight.SemiBold)
                AsText(
                    if (limits.rigidLimits) "No te dejaré guardar una sesión que los supere, y el asistente propondrá cómo ajustarla."
                    else "Solo te aviso al guardar. El asistente no propone ajustes de límites.",
                    12f, Modifier.padding(top = 2.dp), color = AsColors.faint, lineHeight = 1.35f,
                )
            }
            AsSwitch(limits.rigidLimits, "Límites rígidos") {
                actions.onAdvancedRuleLimitsChange(limits.maxVolumePerMuscleSession, limits.maxVolumePerMuscleWeekly, limits.maxSamePatternPerSession, it)
            }
        }
    }
}

/** Paso de un límite: «+» sin valor arranca en [start]; «−» en el [minimum] lo quita. */
internal fun stepLimit(current: Double?, direction: Int, start: Double, step: Double, minimum: Double): Double? {
    if (current == null) return if (direction > 0) start else null
    val next = current + direction * step
    return if (next < minimum) null else next
}

@Composable
private fun LimitRow(label: String, value: String?, status: com.example.kpkn.domain.sessionassistant.LimitStatus?, first: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    val sub = if (value == null) "Sin límite" else status?.text
    val tone = when {
        value == null || status == null -> null
        status.within -> AsTone.OK
        else -> AsTone.OVER
    }
    AsRuleRow(label, sub = sub, subTone = tone, first = first) {
        AsStepper(value ?: "—", label, onMinus, onPlus, muted = value == null)
    }
}

// ── Tiempo ───────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun TimeSection(
    uiState: SessionEditorUiState,
    actions: AsRulesActions,
    onOpenDuration: (DurationPickerTarget) -> Unit,
    onRest: (String) -> Unit,
    onToast: (String, String?) -> Unit,
) {
    val session = uiState.activeVariantSession ?: uiState.session ?: return
    val estimated = uiState.sessionTimeBreakdown?.totalMinutes ?: uiState.estimatedDurationMinutes
    val limit = session.targetDurationMinutes
    val status = rulesClockStatusOf(estimated, limit)
    val gap = if (limit != null && estimated > 0) estimated - limit else null
    val statusColor = when (status) {
        RulesClockStatus.NEUTRAL -> AsColors.faint
        RulesClockStatus.OK -> AsColors.done
        RulesClockStatus.ENERGY -> AsColors.energy
        RulesClockStatus.OVER -> AsColors.danger
    }
    AsModule {
        AsModuleHeader("Reloj de sesión") {
            when (status) {
                RulesClockStatus.NEUTRAL -> AsStatusPill("Sin límite", AsTone.NEUTRAL)
                RulesClockStatus.OK -> AsStatusPill("Dentro", AsTone.OK)
                RulesClockStatus.ENERGY -> AsStatusPill("Cerca del límite", AsTone.OVER)
                RulesClockStatus.OVER -> AsStatusPill("Por encima", AsTone.BAD)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ClockRing(estimated, limit, statusColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ClockStat("Estimado", if (estimated > 0) "$estimated" else "—", if (estimated > 0) "min" else null, AsColors.ink)
                ClockStat("Límite", limit?.toString() ?: "Sin límite", limit?.let { "min" }, AsColors.ink, plain = limit == null)
                ClockStat("Diferencia", gap?.let { if (it > 0) "+$it" else "$it" } ?: "—", gap?.let { "min" }, if (gap == null) AsColors.muted else statusColor)
            }
        }
        uiState.sessionTimeBreakdown?.let { AsCaption(sessionTimeBreakdownCaption(it), Modifier.padding(top = 14.dp)) }
    }
    AsModule {
        AsModuleHeader("Tiempo de sesión", bottomGap = 6.dp)
        AsCaption("Cuenta atrás en el entreno. No corta la sesión.", Modifier.padding(bottom = 12.dp))
        AsFlow {
            listOf(30, 45, 60, 90).forEach { m -> AsChip("$m min", on = limit == m, onClick = { actions.setTargetDuration(m) }) }
            if (estimated > 0) AsChip("Estimado", on = limit == estimated, onClick = { actions.setTargetDuration(estimated) })
            AsChip("Sin límite", on = limit == null, onClick = { actions.setTargetDuration(null) })
        }
        AsRuleRow("Presupuesto global", Modifier.padding(top = 8.dp)) {
            AsPicker(formatDurationMinutes(limit), "Presupuesto global") { onOpenDuration(DurationPickerTarget.Global(limit)) }
        }
        val budget = limit ?: 0
        if (budget > 0) {
            val assigned = session.parts.sumOf { it.targetDurationMinutes ?: it.exercises.sumOf { ex -> ex.targetDurationMinutes ?: 0 } } +
                session.exercises.sumOf { it.targetDurationMinutes ?: 0 }
            val shares = session.parts.map { it.name to (it.targetDurationMinutes ?: it.exercises.sumOf { ex -> ex.targetDurationMinutes ?: 0 }) }.filter { it.second > 0 }
            val free = budget - assigned
            if (shares.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(RoundedCornerShape(3.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    shares.forEachIndexed { index, (_, minutes) ->
                        Box(Modifier.weight(minutes.toFloat()).fillMaxSize().background(AsColors.ink.copy(alpha = if (index == 0) 1f else 0.5f)))
                    }
                    if (free > 0) Box(Modifier.weight(free.toFloat()).fillMaxSize().background(AsColors.cell))
                }
            }
            val caption = if (free < 0) "Asignados $assigned min; el tiempo de sesión es $budget min"
            else (shares.map { "${it.first} ${it.second} min" } + "$free min libres").joinToString(" · ")
            AsCaption(caption, Modifier.padding(top = 8.dp), color = if (free < 0) AsColors.danger else AsColors.faint)
        }
    }
    if (uiState.timeCoachSuggestions.isNotEmpty()) {
        AsText("Para entrar en tu límite", 10.5f, Modifier.negMargin(bottom = (-4).dp).padding(start = 4.dp), color = AsColors.faint, weight = FontWeight.SemiBold, cap = 1.15f, letterSpacing = 1.3f, uppercase = true)
        uiState.timeCoachSuggestions.forEach { suggestion ->
            CoachCard(
                title = suggestion.title,
                explanation = suggestion.explanation,
                minutes = suggestion.minutesSaved,
                fatigue = when (suggestion.fatigueDelta) {
                    TimeCoachFatigueDelta.LOWER -> "Fatiga ↓"
                    TimeCoachFatigueDelta.SIMILAR -> "Fatiga ≈"
                    TimeCoachFatigueDelta.HIGHER -> "Fatiga ↑"
                },
                onApply = { actions.onApplyTimeCoachSuggestion(suggestion.id); onToast("Sugerencia aplicada", "Se ahorran ${suggestion.minutesSaved} min.") },
                onDismiss = { actions.onDismissTimeCoachSuggestion(suggestion.id) },
            )
        }
    } else if (limit != null && gap != null && gap > 0) {
        AsCaption("Calculando sugerencias… o no hay ajustes seguros aún.", Modifier.padding(horizontal = 4.dp))
    }
    GroupTimesModule(session, limit, actions, onOpenDuration)
}

@Composable
private fun ClockRing(estimated: Int, limit: Int?, color: Color) {
    // pulido:as-ring — el arco se pinta quieto (sin animación de entrada)
    Box(Modifier.size(112.dp).semantics { contentDescription = "Estimado $estimated minutos${limit?.let { " de $it" } ?: ", sin límite"}" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val diameter = 92.dp.toPx()
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arc = Size(diameter, diameter)
            drawArc(AsColors.white(0x1a), -90f, 360f, false, topLeft, arc, style = Stroke(stroke))
            if (limit != null && limit > 0) {
                drawArc(color, -90f, 360f * (estimated.toFloat() / limit).coerceIn(0f, 1f), false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsFigure(if (estimated > 0) "$estimated" else "—", 28f, color = color, cap = 1.15f)
            AsText("min", 11f, Modifier.padding(top = 3.dp), color = AsColors.muted, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ClockStat(label: String, value: String, unit: String?, color: Color, plain: Boolean = false) {
    Column {
        AsText(label, 10f, color = AsColors.faint, weight = FontWeight.SemiBold, cap = 1.1f, letterSpacing = 1.1f, uppercase = true, maxLines = 1)
        Row(Modifier.padding(top = 3.dp)) {
            if (plain) AsText(value, 14f, Modifier.alignByBaseline(), color = AsColors.muted, weight = FontWeight.SemiBold, maxLines = 1)
            else AsText(value, 19f, Modifier.alignByBaseline(), color = color, weight = FontWeight.Bold, family = AsSyne, cap = 1.2f, lineHeight = 1f, tabular = true, maxLines = 1)
            if (unit != null) AsText(unit, 11f, Modifier.padding(start = 2.dp).alignByBaseline(), color = AsColors.muted, weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun CoachCard(title: String, explanation: String, minutes: Int, fatigue: String, onApply: () -> Unit, onDismiss: () -> Unit) {
    // as-J05 coach-ok — al aplicar, la tarjeta queda al 40 % de opacidad y deja de responder (`style.opacity='.4'; pointerEvents='none'`)
    var applied by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().graphicsLayer { alpha = if (applied) 0.4f else 1f }
            .clip(RoundedCornerShape(22.dp)).background(AsColors.module).border(1.dp, AsColors.line, RoundedCornerShape(22.dp)).padding(horizontal = 17.dp, vertical = 15.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            AsText(title, 14.5f, Modifier.weight(1f), weight = FontWeight.SemiBold, lineHeight = 1.3f)
            AsText("−$minutes min", 14f, color = AsColors.done, weight = FontWeight.SemiBold, maxLines = 1)
        }
        AsText(explanation, 13f, Modifier.padding(top = 6.dp, bottom = 10.dp), color = AsColors.muted, lineHeight = 1.45f)
        Box(Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(AsColors.white(0x0d)).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
            AsText(fatigue, 11.5f, color = AsColors.muted, weight = FontWeight.SemiBold, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AsButton("Aplicar", Modifier.weight(1f), small = true, onClick = { if (!applied) { applied = true; onApply() } })
            AsButton("Descartar", Modifier.weight(1f), small = true, kind = AsButtonKind.GHOST, onClick = { if (!applied) onDismiss() })
        }
    }
}

@Composable
private fun GroupTimesModule(session: com.example.kpkn.data.models.Session, limit: Int?, actions: AsRulesActions, onOpenDuration: (DurationPickerTarget) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    AsModule {
        FoldHeader("Tiempos por grupos y ejercicios", "Por grupo o ejercicio. El del grupo vale.", open) { open = !open }
        if (open) {
            Column(Modifier.asEnter(AsAnim.FoldBodyIn).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (session.parts.size >= 2 && (limit ?: 0) > 0) AsChip("Repartir global en grupos", on = false, onClick = actions.onDistributeTargetAcrossParts)
                session.parts.forEach { part ->
                    TimeRow(part.name, part.targetDurationMinutes, bold = true, indented = false) {
                        onOpenDuration(DurationPickerTarget.Part(part.id, part.name, part.targetDurationMinutes))
                    }
                    part.exercises.forEach { ex ->
                        TimeRow(ex.name, ex.targetDurationMinutes, bold = false, indented = true) {
                            onOpenDuration(DurationPickerTarget.Exercise(ex.id, ex.name, ex.targetDurationMinutes))
                        }
                    }
                }
                if (session.exercises.isNotEmpty()) {
                    AsEyebrow("Otros ejercicios")
                    session.exercises.forEach { ex ->
                        TimeRow(ex.name, ex.targetDurationMinutes, bold = false, indented = true) {
                            onOpenDuration(DurationPickerTarget.Exercise(ex.id, ex.name, ex.targetDurationMinutes))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRow(name: String, minutes: Int?, bold: Boolean, indented: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(start = if (indented) 14.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsText(name, if (indented) 12.5f else 13.5f, Modifier.weight(1f), color = if (indented) AsColors.muted else AsColors.ink, weight = if (bold) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AsPicker(formatDurationMinutes(minutes), name, onClick = onClick)
    }
}
