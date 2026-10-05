package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.firstCompoundWarmupPercentSets
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.PlanEvaluationStage
import com.example.kpkn.domain.onboarding.PlanGoalProfile
import com.example.kpkn.domain.onboarding.PlanRejectionPresenter
import com.example.kpkn.domain.onboarding.PlanRepair
import com.example.kpkn.domain.onboarding.PlanRepairAdvisor
import com.example.kpkn.domain.onboarding.PresentationContext
import com.example.kpkn.domain.onboarding.RejectionAction
import com.example.kpkn.domain.onboarding.RejectionPresentation
import com.example.kpkn.domain.onboarding.RejectionView
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.onboarding.SetupControlKind
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.NativeProfileSplitWitness
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardRadioMark
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.programs.PlanInfoMode
import com.example.kpkn.screens.programs.PlanInfoSheet

/**
 * Contenido de cada paso del bloque **Entreno** del wizard de configuración.
 *
 * Contrato de la capa raíz (`SetupStepScreen`): la raíz pinta la pregunta, el
 * subtítulo y el CTA del scaffold; aquí solo vive el control del paso, según
 * las definiciones actuales de `SetupStepDefinitions` (sin placeholders ni
 * pasos en blanco) y con el patrón de diseño compartido `WizardChoiceCard`.
 *
 * Escritura, siempre por la API nueva del ViewModel — y **ningún setter
 * navega**: el avance exclusivo es el CTA del host (`submitCurrentStep`):
 *  - opciones estables → `setStepChoice` / `setStepChoices` (el valor estable
 *    es el que recibe el motor; nunca `answerChoice`/`answerMulti` legacy);
 *  - un número por paso → `setStepNumber`, con el crudo en `setStepText`;
 *  - estados tipados del contrato de entrenamiento (`draft.trainingOptions`,
 *    `draft.powerliftingProfile`, split, …) → `updateStep(step) { … }`;
 *  - `selectPlan` solo selecciona candidatos; `skipStep` solo aparece si el
 *    paso lo permite; `editStep` mueve el cursor a un paso para editarlo.
 */
@Composable
fun SetupTrainingStepContent(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    when (step) {
        SetupStepId.EXPERIENCE,
        SetupStepId.ROUTE,
        SetupStepId.GOAL,
        SetupStepId.STYLE,
        SetupStepId.VOLUME_TECHNIQUE,
        SetupStepId.VOLUME_CONSISTENCY,
        SetupStepId.VOLUME_STRENGTH,
        SetupStepId.VOLUME_MOBILITY,
        SetupStepId.EQUIPMENT,
        SetupStepId.CARDIO_TYPE,
        SetupStepId.CARDIO_TIME,
        SetupStepId.DAYS,
        SetupStepId.TRAINING_MAX,
        SetupStepId.HOME_EQUIPMENT,
        -> TrainingChoiceStep(step = step, state = state, vm = vm)

        SetupStepId.AVAILABILITY -> TrainingAvailabilityStep(state = state, vm = vm)

        SetupStepId.WEEKDAYS -> TrainingWeekdaysStep(state = state, vm = vm)
        SetupStepId.SESSION_TIME -> TrainingSessionTimeStep(state = state, vm = vm)
        SetupStepId.PRIORITIES -> TrainingPrioritiesStep(state = state, vm = vm)
        SetupStepId.SPLIT -> TrainingSplitStep(state = state, vm = vm)
        SetupStepId.PLAN -> TrainingPlanStep(state = state, vm = vm)
        SetupStepId.TRAINING_MARKS -> TrainingMarksStep(state = state, vm = vm)
        SetupStepId.AUTOREGULATION -> TrainingAutoregulationStep(state = state, vm = vm)
        SetupStepId.AUTOREGULATION_CONFIRM -> TrainingAutoregulationConfirmStep(state = state, vm = vm)
        SetupStepId.WARMUPS -> TrainingWarmupsStep(state = state, vm = vm)
        SetupStepId.TRAINING_REVIEW -> TrainingReviewStep(state = state, vm = vm)
        // La raíz corta los hitos antes de delegar (StepQuestion + hitos), así
        // que este resumen solo pinta si el hito llega aquí: nunca duplica.
        SetupStepId.MILESTONE_TRAINING -> TrainingMilestoneSummary(state = state)

        // Pasos de otros bloques (datos básicos, nutrición, rings y revisión
        // final): su contenido pertenece al dueño de ese bloque.
        else -> Unit
    }
}

// ─── Lógica pura (testeable) ────────────────────────────────────────────────
//
// La selección canónica la posee M1: `draft.selectedValues(step)` devuelve lo
// guardado con `setStepChoice(s)` y, si todavía no hay selección, proyecta la
// reserva tipada de un borrador rehidratado. Aquí solo se lee; nunca se
// duplica esa lógica (un duplicado propio preseleccionaba «recomendado» en una
// ruta sin responder).

/**
 * Splits aplicables hoy: visibles para aplicación, con el nº de días real y, desde A.E2 (D6), compatibles con el
 * objetivo: los repartos de powerlifting solo se ofrecen en Fuerza ([isSplitOfferedForGoal]). Sin objetivo
 * ([goal] null) no se filtra por él.
 */
internal fun compatibleSplitTemplates(daysPerWeek: Int?, startDay: Int, goal: SetupGoal? = null): List<SplitTemplate> =
    SPLIT_TEMPLATES.filter { split ->
        if (!split.isVisibleForApplication) return@filter false
        if (!isSplitOfferedForGoal(split, goal)) return@filter false
        daysPerWeek == null ||
            SplitApplicationEngine.patternToTrainingDays(split.pattern, startDay).size == daysPerWeek
    }

/**
 * D6 (A.E2): un reparto de powerlifting (etiqueta `POWERLIFTING` del catálogo de repartos) solo se ofrece en Fuerza;
 * Músculo, Fuerza y músculo y Atleta completo no lo listan. Sin objetivo ([goal] null) todo se ofrece.
 */
internal fun isSplitOfferedForGoal(split: SplitTemplate, goal: SetupGoal?): Boolean =
    goal == null || goal == SetupGoal.STRENGTH || SplitTag.POWERLIFTING !in split.tags

/**
 * El reparto que se destaca en la lista: el equivalente del calendario propio del objetivo con estos días
 * ([NativeProfileSplitWitness]) si está entre los [compatible]s, para que la propuesta destacada sea siempre una que
 * el plan propio acepta; si no, el recomendado de KPKN o el primero. En Fuerza con 3 días es «SBD Full Body x3», no
 * «Cuerpo completo, 3 días» (que el plan propio de Fuerza rechazaría con el motivo `SPLIT`).
 */
internal fun featuredSplitTemplate(compatible: List<SplitTemplate>, goal: SetupGoal?, daysPerWeek: Int?): SplitTemplate? {
    val witnessId = daysPerWeek?.let { days ->
        ownPlanIdOf(planGoalProfileOf(goal))
            ?.let { ownId -> NativeProfileKind.fromEntryId(ownId) }
            ?.let { kind -> NativeProfileSplitWitness.witnessSplitId(kind, days) }
    }
    return compatible.firstOrNull { it.id == witnessId }
        ?: compatible.firstOrNull { SplitTag.RECOMENDADO_KPKN in it.tags }
        ?: compatible.firstOrNull()
}

/** Etiquetas del patrón personalizado hasta completar las siete posiciones. */
internal fun customSplitPatternFromLabels(labels: List<String>): List<String> {
    val kept = labels.take(7)
    return kept + List((7 - kept.size).coerceAtLeast(0)) { "Descanso" }
}

/**
 * Filas crudas (porcentaje, repeticiones) → recetas reales. Una fila a medio
 * completar queda con el hueco a null: la validación la señala en lugar de
 * inventar un valor.
 */
internal fun warmupRecipesFromRaw(raw: List<Pair<String, String>>): List<SetRecipe> =
    raw.map { (percentRaw, repsRaw) ->
        SetRecipe(
            percent = parseLocalizedNumber(percentRaw),
            reps = parseLocalizedNumber(repsRaw)?.toInt(),
            isWarmup = true,
        )
    }

// ─── Pasos de opción (defs actuales, valores estables) ──────────────────────

/**
 * Opción simple o múltiple según el control declarado en la definición: el
 * valor estable de cada tarjeta es el que viaja a `setStepChoice(s)`. La
 * respuesta se lee con `draft.selectedValues(step)`; aquí no se fabrica ninguna.
 */
@Composable
private fun TrainingChoiceStep(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    if (SetupStepDefinitions.options(step).isEmpty()) {
        TrainingNotice("Este paso no tiene opciones disponibles.", TrainingNoticeTone.ERROR)
        return
    }
    val selected = state.draft.selectedValues(step)
    LegacyGoalSuggestion(step = step, state = state)
    BikePresenceConfirmation(step = step, state = state, vm = vm)
    if (SetupStepDefinitions.control(step) == SetupControlKind.MULTI_CHOICE) {
        SetupBodyMultiChoiceCards(
            step = step,
            selected = selected,
            onSelect = { value -> vm.toggleStepChoice(step, value) },
        )
    } else {
        SetupBodyChoiceCards(
            step = step,
            selected = selected,
            // Re-pulsar la tarjeta elegida no la deselecciona (selección única).
            onSelect = { value -> if (value !in selected) vm.setStepChoice(step, value) },
        )
    }
    SetupBodySkipAction(step = step, vm = vm)
}

/**
 * T-005 / §15.4 — GOAL legacy (HEALTH/MIXED): se SUGIERE «Atleta completo»
 * sin seleccionarlo nunca; la respuesta original se conserva como dato.
 */
@Composable
private fun LegacyGoalSuggestion(step: SetupStepId, state: SetupWizardState) {
    if (step != SetupStepId.GOAL) return
    val goal = state.draft.goal
    if (goal != SetupGoal.HEALTH && goal != SetupGoal.MIXED) return
    TrainingNotice(
        text = "Tu objetivo anterior «${goal.label}» corresponde hoy a «Atleta completo». " +
            "Elígelo solo si quieres actualizarlo: tu respuesta se conserva hasta entonces.",
        tone = TrainingNoticeTone.INFO,
    )
}

/**
 * T-005 / §15.1 — BIKE_OUTDOOR exige confirmar acceso a bicicleta con
 * PRESENCIA si no consta: no se hereda de la categoría «Cardio» ni del resto
 * del material de gimnasio. Solo presencia, sin kilos ni cantidades.
 */
@Composable
private fun BikePresenceConfirmation(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    if (step != SetupStepId.CARDIO_TYPE) return
    if ("BIKE_OUTDOOR" !in state.draft.selectedValues(step)) return
    val presence = SetupApparatusPanel.presenceOf(
        state.draft.trainingOptions.availability,
        SetupApparatusPanel.OUTDOOR_BIKE_KEY,
    )
    if (presence == ApparatusPresence.PRESENT) return

    fun write(value: ApparatusPresence) {
        vm.updateStep(SetupStepId.CARDIO_TYPE) { draft ->
            draft.withApparatusPresence(SetupApparatusPanel.OUTDOOR_BIKE_KEY, value, isSupport = false)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        TrainingNotice(
            text = if (presence == ApparatusPresence.ABSENT) {
                "Confirmaste que no tienes bicicleta: elige otro tipo de cardio o corrige aquí."
            } else {
                "¿Tienes acceso a una bicicleta? Confírmalo para continuar con bicicleta al aire libre."
            },
            tone = TrainingNoticeTone.INFO,
        )
        WizardChoiceCard(
            title = "Sí, tengo bicicleta",
            selected = presence == ApparatusPresence.PRESENT,
            onClick = { write(ApparatusPresence.PRESENT) },
        )
        WizardChoiceCard(
            title = "No tengo bicicleta",
            selected = presence == ApparatusPresence.ABSENT,
            onClick = { write(ApparatusPresence.ABSENT) },
        )
    }
}

/** WEEKDAYS: multiselección con contador de días; tocar nunca avanza de paso. */
@Composable
private fun TrainingWeekdaysStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.WEEKDAYS
    if (SetupStepDefinitions.options(step).isEmpty()) {
        TrainingNotice("Este paso no tiene opciones disponibles.", TrainingNoticeTone.ERROR)
        return
    }
    val selected = state.draft.selectedValues(step)
    val target = state.draft.daysPerWeek
    SetupBodyHint(text = weekdaysCounterText(target = target, selectedCount = selected.size))
    SetupBodyMultiChoiceCards(
        step = step,
        selected = selected,
        onSelect = { value -> vm.toggleStepChoice(step, value) },
    )
}

/** Contador del paso WEEKDAYS; con un solo día el sustantivo y el participio van en singular. */
internal fun weekdaysCounterText(target: Int?, selectedCount: Int): String =
    if (target == null) {
        "Días elegidos: $selectedCount"
    } else {
        "Elige ${SpanishPlurals.days(target)} · $selectedCount de $target " +
            SpanishPlurals.choose(target, "elegido", "elegidos")
    }

/** SESSION_TIME: un número por paso; el crudo se conserva en `inputTexts`. */
@Composable
private fun TrainingSessionTimeStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.SESSION_TIME
    val range = SetupStepDefinitions.of(step)?.range
    var raw by remember(step) {
        mutableStateOf(state.draft.rawInput(step) ?: state.draft.minutesPerSession?.toString().orEmpty())
    }
    val parsed = parseLocalizedNumber(raw)
    val outOfRange = parsed != null && range != null && parsed !in range.min..range.max
    val unit = range?.unit

    TrainingNumberField(
        label = if (unit != null) "Minutos por sesión ($unit)" else "Minutos por sesión",
        value = raw,
        onValueChange = { text ->
            raw = text
            vm.setStepText(step, text)
            val value = parseLocalizedNumber(text)
            vm.setStepNumber(step, value?.takeIf { v -> range == null || v in range.min..range.max })
        },
        isError = raw.isNotBlank() && (parsed == null || outOfRange),
    )
    if (raw.isNotBlank() && (parsed == null || outOfRange)) {
        val message = if (range != null) {
            "Usa un valor entre ${range.min.toInt()} y ${range.max.toInt()} ${range.unit.orEmpty()}.".trim()
        } else {
            "Escribe un número válido."
        }
        TrainingNotice(message, TrainingNoticeTone.ERROR)
    }
}

/**
 * Crudo del único campo numérico del paso. `inputTexts` es
 * `Map<String, String>` con clave `step.name`: conserva lo que se escribió
 * aunque el valor parseado sea inválido o el borrador se restaure.
 */
private fun SetupWizardDraft.rawInput(step: SetupStepId): String? = inputTexts[step.name]

@Composable
private fun TrainingAvailabilityStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.AVAILABILITY
    val definition = SetupStepDefinitions.of(step) ?: return
    val selected = state.draft.selectedValues(step)
    val categories = definition.options.filter { it.value != AVAILABILITY_BODYWEIGHT }
    TextButton(onClick = { vm.setStepChoices(step, categories.map { it.value }.toSet()) }) {
        Text("Seleccionar todo", color = WizardColors.text)
    }
    categories.forEach { option ->
        WizardChoiceCard(
            title = option.label,
            selected = option.value in selected,
            onClick = { vm.toggleStepChoice(step, option.value) },
        )
    }
    val bodyweight = definition.options.firstOrNull { it.value == AVAILABILITY_BODYWEIGHT }
    WizardChoiceCard(
        title = bodyweight?.label ?: "Solo peso corporal",
        subtitle = "Sin mancuernas, barras ni máquinas.",
        selected = AVAILABILITY_BODYWEIGHT in selected,
        onClick = { vm.toggleStepChoice(step, AVAILABILITY_BODYWEIGHT) },
    )
    // §13.2: subpanel de aparatos DENTRO del paso de material (subpanel de
    // EQUIPMENT), después de las categorías. Solo presencia Sí/No/No sé: aquí
    // no hay kilos ni cantidades (AC-T005-01).
    TrainingApparatusPanel(state = state, vm = vm)
}

/**
 * Subpanel «¿Qué tienes disponible?» (§13.2): presencia agrupada de las claves
 * curadas relevantes a las categorías elegidas. Omitir deja UNKNOWN (nunca
 * PRESENT) y «No tengo otros» marca ausentes los ítems visibles desconocidos.
 */
@Composable
private fun TrainingApparatusPanel(state: SetupWizardState, vm: SetupWizardViewModel) {
    val availability = state.draft.trainingOptions.availability ?: return
    if (availability.categories.isEmpty()) return
    val items = remember(availability.categories) { SetupApparatusPanel.itemsFor(availability.categories) }
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        Text(
            text = "¿Qué tienes disponible? (solo presencia, sin kilos ni cantidades)",
            style = WizardTypography.cardTitle,
            color = WizardColors.text,
            modifier = Modifier.testTag("setup-apparatus-panel"),
        )
        Text(
            text = "Puedes continuar con «No lo sé»: los planes te dirán qué falta confirmar.",
            style = WizardTypography.bodySmall,
            color = WizardColors.textMuted,
        )
        items.groupBy { it.group }.entries
            .sortedBy { SetupApparatusPanel.groupOrder.indexOf(it.key).let { index -> if (index < 0) Int.MAX_VALUE else index } }
            .forEach { (group, groupItems) ->
                Text(text = group, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
                groupItems.forEach { item ->
                    val presence = SetupApparatusPanel.presenceOf(availability, item.key)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WizardColors.cardFill, WizardShapes.card)
                            .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("setup-apparatus-${item.key}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.label,
                            style = WizardTypography.cardSubtitle,
                            color = WizardColors.text,
                            modifier = Modifier.weight(1f),
                        )
                        PresenceButton("Sí", presence == ApparatusPresence.PRESENT) {
                            vm.updateStep(SetupStepId.AVAILABILITY) { draft ->
                                draft.withApparatusPresence(item.key, ApparatusPresence.PRESENT, item.isSupport)
                            }
                        }
                        PresenceButton("No", presence == ApparatusPresence.ABSENT) {
                            vm.updateStep(SetupStepId.AVAILABILITY) { draft ->
                                draft.withApparatusPresence(item.key, ApparatusPresence.ABSENT, item.isSupport)
                            }
                        }
                        PresenceButton("No sé", presence == ApparatusPresence.UNKNOWN) {
                            vm.updateStep(SetupStepId.AVAILABILITY) { draft ->
                                draft.withApparatusPresence(item.key, ApparatusPresence.UNKNOWN, item.isSupport)
                            }
                        }
                    }
                }
            }
        TextButton(
            onClick = { vm.updateStep(SetupStepId.AVAILABILITY) { draft -> draft.markVisibleApparatusAbsent(items) } },
            modifier = Modifier.testTag("setup-apparatus-none-others"),
        ) {
            Text("No tengo otros", color = WizardColors.text, style = WizardTypography.cardSubtitle)
        }
    }
}

@Composable
private fun PresenceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            style = WizardTypography.bodySmall,
            color = if (selected) WizardColors.text else WizardColors.textMuted,
            fontWeight = if (selected) FontWeight.Bold else null,
            modifier = Modifier.testTag("setup-apparatus-presence-$label"),
        )
    }
}

// ─── PRIORITIES: bolsa de orden (5 en total, 2 por músculo) ─────────────────

@Composable
private fun TrainingPrioritiesStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.PRIORITIES
    val definition = SetupStepDefinitions.of(step)
    val options = definition?.options.orEmpty()
    if (options.isEmpty()) {
        TrainingNotice("Este paso no tiene opciones disponibles.", TrainingNoticeTone.ERROR)
        return
    }
    val budget = definition?.budget ?: 5
    val maxPerItem = definition?.maxPerItem ?: 2
    val bag = state.draft.trainingOptions.orderPriorities
    val used = bag.values.sum()
    val remaining = (budget - used).coerceAtLeast(0)

    // Los chips leen la bolsa real para marcar la selección: `selected` es
    // igualdad EXACTA entre la bolsa y el preset, no un remembers de "lo último
    // tocado". Un ajuste manual que caiga en otro preset lo selecciona; uno que
    // no coincide con ninguno deja la fila sin marcar.
    PriorityPresetRow(current = bag, onApply = { preset ->
        vm.updateStep(step) { draft ->
            draft.copy(trainingOptions = draft.trainingOptions.copy(orderPriorities = preset))
        }
    })
    Text(
        text = if (used == 0) {
            "Todo el cuerpo queda equilibrado. Si quieres un énfasis, usa hasta $budget puntos."
        } else {
            "Te quedan $remaining de $budget. Esto solo ordena los ejercicios."
        },
        style = WizardTypography.bodySmall,
        color = WizardColors.textMuted,
    )
    options.forEach { option ->
        val points = bag[option.value] ?: 0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WizardColors.cardFill, WizardShapes.card)
                .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.label,
                style = WizardTypography.cardTitle,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = points > 0,
                onClick = { writePriorities(vm, step, option.value, delta = -1, budget = budget, maxPerItem = maxPerItem) },
                modifier = Modifier.testTag("$PRIORITY_REMOVE_TAG_PREFIX${option.value}"),
            ) { Text("−", color = WizardColors.text) }
            Text(
                text = points.toString(),
                style = WizardTypography.cardTitle,
                color = if (points > 0) WizardColors.text else WizardColors.textFaint,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            TextButton(
                enabled = points < maxPerItem && remaining > 0,
                onClick = { writePriorities(vm, step, option.value, delta = +1, budget = budget, maxPerItem = maxPerItem) },
                modifier = Modifier.testTag("$PRIORITY_ADD_TAG_PREFIX${option.value}"),
            ) { Text("+", color = WizardColors.text) }
        }
    }
}

private val PRIORITY_PRESETS = listOf(
    "Todo el cuerpo" to emptyMap(),
    "Quiero los mejores glúteos" to mapOf("Glúteos" to 2, "Isquiosurales" to 2),
    "Espalda amplia" to mapOf("Dorsales" to 2),
    "Espalda densa y fuerte" to mapOf("Trapecio" to 2, "Dorsales" to 2, "Erectores Espinales" to 1),
    "Brazos" to mapOf("Bíceps" to 2, "Tríceps" to 2),
    "Pecho y hombros" to mapOf("Pectorales" to 2, "Deltoides" to 2),
    "Piernas fuertes" to mapOf("Cuádriceps" to 2, "Isquiosurales" to 2, "Glúteos" to 1),
)

/** Contenedor de los siete chips, para poder medirlo en la prueba. */
private const val PRIORITY_PRESETS_TAG = "setup-priority-presets"

/** Chip `i` del catálogo, con `i` = índice real en [PRIORITY_PRESETS]. */
private const val PRIORITY_PRESET_TAG_PREFIX = "setup-priority-preset-"

/** Ajustes manuales `+` / `−` por opción canónica de la bolsa. */
private const val PRIORITY_ADD_TAG_PREFIX = "setup-priority-add-"
private const val PRIORITY_REMOVE_TAG_PREFIX = "setup-priority-remove-"

/**
 * Presets de la bolsa de orden como **chips** que fluyen en varias líneas.
 *
 * Antes eran botones de texto apilados a lo ancho; con etiquetas en español
 * largas y fuente grande no cabían y empujaban el resto del paso. El `FlowRow`
 * deja que cada chip mida su contenido y salte de línea, sin tarjetas de ancho
 * completo.
 *
 * Nada aquí decide la selección: se pinta la **bolsa real** que el ViewModel
 * publicó ([current]) y se marca el preset idéntico. El toque sigue llamando a
 * [onApply], que es el que escribe con `updateStep`; este composable no muta
 * estado ni calcula reducciones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriorityPresetRow(
    current: Map<String, Int>,
    onApply: (Map<String, Int>) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PRIORITY_PRESETS_TAG),
        horizontalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap / 2),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap / 2),
    ) {
        PRIORITY_PRESETS.forEachIndexed { index, (label, preset) ->
            PriorityPresetChip(
                label = label,
                selected = current == preset,
                onClick = { onApply(preset) },
                modifier = Modifier.testTag(PRIORITY_PRESET_TAG_PREFIX + index),
            )
        }
    }
}

/**
 * Chip compacto de preset: mide su contenido, envuelve la etiqueta y nunca
 * fuerza una altura fija.
 *
 * - El área interactiva es ≥ `touchTarget` (48 dp) en el eje vertical, pero
 *   **no** hay altura fija: con fuente al 2× la etiqueta salta de línea y el
 *   chip crece. La etiqueta no se recorta con elipsis.
 * - La selección no depende del color: además del borde blanco grueso hay
 *   radio relleno con punto, `selected` y `Role.RadioButton` para TalkBack, los
 *   mismos tres códigos que usa `WizardChoiceCard`.
 */
@Composable
private fun PriorityPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = WizardSpacing.touchTarget)
            .background(WizardColors.cardFill, WizardShapes.pill)
            .border(
                width = if (selected) WizardColors.selectedBorderWidth else WizardColors.unselectedBorderWidth,
                color = if (selected) WizardColors.selectedBorder else WizardColors.cardBorder,
                shape = WizardShapes.pill,
            )
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WizardRadioMark(selected = selected)
        Text(
            text = label,
            style = WizardTypography.bodySmall,
            color = WizardColors.text,
        )
    }
}

/**
 * Aplica un **delta** (+1 / −1) a la bolsa de orden dentro de `updateStep`, es
 * decir, sobre el último borrador y no sobre el eco que la fila leyó al
 * componer: dos toques seguidos no se pisan y «−» resta de 2 a 1 en lugar de
 * borrar la entrada (2 → 0).
 *
 * El presupuesto (5) y el tope por músculo (2) se validan **aquí, en la
 * closure**, antes de escribir; un movimiento inválido devuelve la bolsa
 * intacta y no gasta revisión. Solo cambia `orderPriorities`: la bolsa reordena
 * ejercicios y nunca altera series, repeticiones ni ninguna dosis.
 */
private fun writePriorities(
    vm: SetupWizardViewModel,
    step: SetupStepId,
    option: String,
    delta: Int,
    budget: Int,
    maxPerItem: Int,
) {
    vm.updateStep(step) { draft ->
        val current = draft.trainingOptions.orderPriorities
        val next = prioritiesAfterDelta(current, option, delta, budget, maxPerItem)
        if (next == current) draft
        else draft.copy(trainingOptions = draft.trainingOptions.copy(orderPriorities = next))
    }
}

/**
 * Reductor puro de la bolsa de orden: `bag[option] + delta` con las reglas del
 * catálogo. Recibe SIEMPRE la bolsa última (el VM la lee dentro de su mutex).
 * Sin dato o con un movimiento que rompería presupuesto/tope, devuelve la
 * entrada sin tocar; a 0 puntos retira la clave en lugar de dejar un cero.
 */
internal fun prioritiesAfterDelta(
    bag: Map<String, Int>,
    option: String,
    delta: Int,
    budget: Int,
    maxPerItem: Int,
): Map<String, Int> {
    val next = (bag[option] ?: 0) + delta
    if (next < 0 || next > maxPerItem) return bag
    if (delta > 0 && bag.values.sum() >= budget) return bag
    return if (next == 0) bag - option else bag + (option to next)
}

// ─── SPLIT ──────────────────────────────────────────────────────────────────

/**
 * Nombre en español llano de un reparto: el mismo en la lista de repartos y en la revisión (C.P5).
 * Los repartos sin nombre propio aquí conservan el `name` de su plantilla.
 */
internal fun splitDisplayName(template: SplitTemplate): String = when (template.id) {
    "ul_x4" -> "Torso y pierna, 4 días"
    "ppl_ul" -> "Empuje, tirón, pierna y torso"
    "fullbody_x3" -> "Cuerpo completo, 3 días"
    "ppl_x6" -> "Empuje, tirón y pierna, 6 días"
    "ul_x6" -> "Torso y pierna, 6 días"
    "ppl_arnold" -> "Empuje, tirón y pierna con énfasis"
    "phat_hybrid" -> "Torso, pierna y cuerpo completo"
    "ant_post_x4" -> "Cadena anterior y posterior, 4 días"
    "arnold_ul" -> "Estético y torso/pierna"
    "ant_post_x6" -> "Cadena anterior y posterior, 6 días"
    "bro_split" -> "Un grupo por día"
    "hybrid_fb_ap" -> "Cuerpo completo y cadenas"
    "minimalist_x2" -> "Dos días, lo esencial"
    "weekend_warrior" -> "Fin de semana"
    "glute_focus" -> "Énfasis en glúteos"
    "beach_body" -> "Más torso"
    "fullbody_x5" -> "Cuerpo completo, 5 días"
    "push_pull_x4" -> "Empuje y tirón, 4 días"
    else -> template.name
}

/** Nombre del reparto [splitId] en español llano; null si el catálogo de repartos no lo conoce (nunca el id). */
internal fun splitDisplayName(splitId: String): String? =
    SPLIT_TEMPLATES.firstOrNull { template -> template.id == splitId }?.let { template -> splitDisplayName(template) }

private const val SPLIT_RECOMMENDED = "recommended"
private const val SPLIT_CUSTOM = "custom"

/** Enfoques que el motor de splits entiende (`SimpleCyclePersonalizer`). */
private val CUSTOM_SPLIT_LABELS = listOf(
    "Empuje", "Tirón", "Pierna", "Cuerpo completo", "Torso", "Cadena anterior", "Cadena posterior",
)

/**
 * SPLIT: solo opciones reales del motor. La ruta de protocolo fija su propio
 * reparto y aquí se dice sin renombrar etiquetas ni simular un selector.
 */
@Composable
private fun TrainingSplitStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.SPLIT
    val draft = state.draft
    if (draft.programRoute == SetupProgramRoute.PROTOCOL) {
        TrainingNotice(
            text = "La ruta de protocolo mantiene el reparto propio del protocolo: aquí no se cambia ni se renombra. Lo verás al elegir el plan.",
            tone = TrainingNoticeTone.INFO,
        )
        return
    }

    val options = SetupStepDefinitions.options(step)
    val selected = draft.selectedValues(step)
    val startDay = draft.selectedWeekdays.minOrNull() ?: 1
    // D6 (A.E2): la lista recibe el objetivo; los repartos de powerlifting solo salen en Fuerza.
    val compatible = compatibleSplitTemplates(draft.daysPerWeek, startDay, draft.goal)
    var query by remember(step) { mutableStateOf("") }
    val featured = featuredSplitTemplate(compatible, draft.goal, draft.daysPerWeek)
    val rest = compatible.filter { it.id != featured?.id }
    val filtered = if (query.isBlank()) rest else compatible.filter { split ->
        val visible = splitDisplayName(split)
        visible.contains(query, ignoreCase = true) || split.name.contains(query, ignoreCase = true) ||
            split.description.contains(query, ignoreCase = true)
    }

    WizardChoiceCard(
        title = options.firstOrNull { it.value == SPLIT_RECOMMENDED }?.label ?: "Recomendado para ti",
        subtitle = "Sin reparto forzado: el motor ordena tus días como mejor encaje.",
        selected = SPLIT_RECOMMENDED in selected,
        onClick = { selectSplit(vm, step, SPLIT_RECOMMENDED, splitId = null) },
    )
    if (featured != null) {
        WizardChoiceCard(
            title = splitDisplayName(featured),
            subtitle = featured.description,
            selected = featured.id in selected,
            onClick = { selectSplit(vm, step, featured.id, splitId = featured.id) },
        )
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Buscar reparto") },
    )
    filtered.forEach { split ->
        if (split.id == featured?.id && query.isBlank()) return@forEach
        WizardChoiceCard(
            title = splitDisplayName(split),
            subtitle = split.description,
            selected = split.id in selected,
            onClick = { selectSplit(vm, step, split.id, splitId = split.id) },
        )
    }
    WizardChoiceCard(
        title = options.firstOrNull { it.value == SPLIT_CUSTOM }?.label ?: "Personalizado",
        subtitle = "Tú repartes el foco de cada día de entrenamiento.",
        selected = SPLIT_CUSTOM in selected,
        onClick = { selectSplit(vm, step, SPLIT_CUSTOM, splitId = SPLIT_CUSTOM) },
    )
    if (SPLIT_CUSTOM in selected) {
        CustomSplitEditor(step = step, state = state, vm = vm)
    }
    SetupBodySkipAction(step = step, vm = vm)
}

/**
 * Selección por valor estable (`setStepChoice`) y, en paralelo, el id real que
 * consume el motor (`updateStep`): ambas rutas escriben el mismo valor.
 */
private fun selectSplit(vm: SetupWizardViewModel, step: SetupStepId, value: String, splitId: String?) {
    vm.setStepChoice(step, value)
    vm.updateStep(step) { draft ->
        draft.copy(
            selectedSplitId = splitId,
            customSplitPattern = if (value == SPLIT_CUSTOM) draft.customSplitPattern else emptyList(),
            customSplitName = if (value == SPLIT_CUSTOM) draft.customSplitName else null,
        )
    }
}

/** Editor del patrón personalizado: una fila por día real seleccionado. */
@Composable
private fun CustomSplitEditor(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val weekdays = draft.selectedWeekdays.sorted().ifEmpty { (1..7).toList() }
    val stored = draft.customSplitPattern.filter { label ->
        label.isNotBlank() && !label.equals("Descanso", ignoreCase = true)
    }
    val labels = weekdays.mapIndexed { index, _ -> stored.getOrNull(index).orEmpty() }
    val defined = labels.count { it.isNotBlank() }
    val target = draft.daysPerWeek ?: weekdays.size

    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        if (defined < target) {
            TrainingNotice(
                text = customSplitPendingText(target = target, defined = defined),
                tone = TrainingNoticeTone.ERROR,
            )
        }
        weekdays.forEachIndexed { index, weekday ->
            val current = labels[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WizardColors.cardFill, WizardShapes.card)
                    .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = weekdayLabel(weekday) ?: "Día ${index + 1}",
                    style = WizardTypography.cardSubtitle,
                    color = WizardColors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    writeCustomPattern(vm, step, labels.replaceAt(index, adjacentSplitLabel(current, -1)))
                }) { Text("◀", color = WizardColors.text) }
                Text(
                    text = current.ifBlank { "Sin definir" },
                    style = WizardTypography.cardTitle,
                    color = if (current.isBlank()) WizardColors.textFaint else WizardColors.text,
                )
                TextButton(onClick = {
                    writeCustomPattern(vm, step, labels.replaceAt(index, adjacentSplitLabel(current, +1)))
                }) { Text("▶", color = WizardColors.text) }
            }
        }
    }
}

/** Aviso del split personalizado; con un solo día no se escribe «tus 1 días». */
internal fun customSplitPendingText(target: Int, defined: Int): String = SpanishPlurals.choose(
    target,
    "Define el foco de tu día ($defined de $target).",
    "Define el foco de tus $target días ($defined de $target).",
)

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    toMutableList().also { rows -> if (index in rows.indices) rows[index] = value }

private fun adjacentSplitLabel(current: String, delta: Int): String {
    val index = CUSTOM_SPLIT_LABELS.indexOf(current)
    if (index < 0) return CUSTOM_SPLIT_LABELS.first()
    return CUSTOM_SPLIT_LABELS[(index + delta + CUSTOM_SPLIT_LABELS.size) % CUSTOM_SPLIT_LABELS.size]
}

private fun writeCustomPattern(vm: SetupWizardViewModel, step: SetupStepId, labels: List<String>) {
    val pattern = customSplitPatternFromLabels(labels)
    vm.updateStep(step) { draft -> draft.copy(customSplitPattern = pattern, customSplitName = draft.customSplitName ?: "Mi split") }
}

// ─── PLAN: candidatos reales ────────────────────────────────────────────────

/**
 * Qué enseña la lista de planes (Paquete A · D2, B-01). Es una decisión pura sobre el estado, separada del
 * composable para poder probarla sin montar la pantalla:
 *
 *  - [Loading]: el barrido de candidatos sigue en curso.
 *  - [SearchFailed]: la BÚSQUEDA falló (el catálogo no quedó listo o hubo un error global): no hay lista, solo
 *    el motivo (`errors["candidates"]`) y «Reintentar». Se distingue del «ningún plan es viable» porque el
 *    fallo de búsqueda deja un rechazo global (`planId == null`) y el otro, rechazos por plan.
 *  - [NoneViable]: la búsqueda terminó y ningún plan es viable: la explicación con acciones
 *    ([CandidateIncompatibility]).
 *  - [Candidates]: hay lista y se enseña SIEMPRE. El error del preview de la SELECCIÓN ya no la esconde: va
 *    como aviso encima de las tarjetas, igual que la selección caída (el plan elegido que ya no encaja).
 */
internal sealed interface CandidateListGate {
    data object Loading : CandidateListGate

    data class SearchFailed(val message: String) : CandidateListGate

    data object NoneViable : CandidateListGate

    data class Candidates(
        val cards: List<SetupPlanCandidate>,
        /** Error del preview del plan elegido (`previewError`); null si no hay. */
        val previewError: String?,
        /** Selección caída que sigue pendiente de explicar; null si ya no corresponde a la selección actual. */
        val dropped: SetupDroppedSelection?,
        /**
         * Paquete A · C4: aviso del plan PROPIO del objetivo cuando quedó rechazado y tiene reparación de un toque,
         * aunque haya otros planes viables debajo («No pudimos armar tu plan de fuerza… [Sí, tengo rack y banco]»);
         * null cuando el propio es viable, no tiene reparación o ya lo explica el aviso de la selección caída.
         */
        val ownPlanNotice: RejectionNotice? = null,
    ) : CandidateListGate
}

internal fun candidateListGate(state: SetupWizardState): CandidateListGate {
    if (state.isCandidateLoading) return CandidateListGate.Loading
    val cards = state.planCandidates.ifEmpty { state.availablePlanCandidates }
    if (cards.isEmpty()) {
        val searchError = state.errors["candidates"]
        return if (searchError != null && state.candidateRejections.any { it.planId == null }) {
            CandidateListGate.SearchFailed(searchError)
        } else {
            CandidateListGate.NoneViable
        }
    }
    val selected = state.draft.selectedCatalogId
    // Elegir otro plan descarta el aviso (el ViewModel ya lo limpia); esta comprobación evita enseñarlo
    // si, por la vía que sea, la selección actual ya no es la que cayó.
    val dropped = state.droppedSelection?.takeIf { selected == null || selected == it.planId }
    return CandidateListGate.Candidates(
        cards = cards,
        previewError = state.previewError,
        dropped = dropped,
        ownPlanNotice = ownPlanNotice(state, droppedPlanId = dropped?.planId),
    )
}

// ─── Avisos de rechazo (Paquete A · C3/C4 y Paquete C · C.P11) ─────────────────────────────────────────────────
//
// Un SOLO camino de texto para todo rechazo que la persona ve: la lista sin planes viables, el aviso de la selección
// caída y el aviso del plan propio encima de la lista. El texto y los botones salen de `PlanRejectionPresenter` (el
// presentador único de dominio); aquí solo se hace lo que el presentador no sabe: elegir el rechazo, ofrecer la
// reparación de un toque del plan propio con la etiqueta de D5 y traducir cada botón a la API del asistente. El
// texto crudo del motor (`SetupCandidateRejection.reason`) no se pinta nunca: solo va al registro.

/** Qué hace un botón de un aviso de rechazo con la API del asistente. */
internal sealed interface NoticeEffect {
    /** Aplica reparaciones de un toque (`applyRepairs`): justo lo que el asesor probó y deja el plan propio listo. */
    data class Apply(val repairs: List<PlanRepair>) : NoticeEffect

    /** Una acción del presentador: ir a un paso, reintentar o ver las alternativas. */
    data class Act(val action: RejectionAction) : NoticeEffect
}

/** Un botón del aviso: su texto y su efecto. */
internal data class NoticeButton(val label: String, val effect: NoticeEffect)

/** Un aviso de rechazo listo para pintar: un texto llano y hasta dos botones, el principal primero. */
internal data class RejectionNotice(
    val text: String,
    val primary: NoticeButton?,
    val secondary: NoticeButton? = null,
) {
    val buttons: List<NoticeButton> get() = listOfNotNull(primary, secondary)
}

/** Arranque común del aviso de la selección caída. */
internal const val DROPPED_SELECTION_LEAD = "Tu plan elegido ya no encaja con tus respuestas."

/**
 * Frase que se añade al aviso de «Cambiar a Fuerza y músculo» (recomendación 2): con mancuernas, los ejercicios
 * principales de ese plan serán versiones con mancuernas, no con barra. El asesor solo propone ese destino cuando la
 * categoría de mancuernas está marcada.
 */
internal const val STRENGTH_MUSCLE_DUMBBELLS_HINT =
    " Con tus mancuernas, los ejercicios principales serán versiones con mancuernas."

/** Como mucho dos botones por aviso (el principal y uno secundario). */
private const val MAX_NOTICE_BUTTONS = 2

/** Minúscula inicial para insertar una etiqueta en una frase; deja intactas las siglas («EZ»). */
private fun String.lowercaseFirst(): String =
    if (isEmpty() || (length > 1 && this[1].isUpperCase())) this else replaceFirstChar { it.lowercaseChar() }

private fun joinSpanish(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.first()
    else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
}

/** Nombre corto de una llave para el botón de un toque: el MISMO que usa el texto del presentador (H14). */
private fun shortApparatusLabel(key: String): String = PlanRejectionPresenter.shortLabelOf(key) ?: "material"

/**
 * La proyección mínima de un rechazo del asistente para el presentador. NO lleva `reason` (el texto crudo del motor),
 * así que ni ids ni tokens pueden llegar a la pantalla.
 */
internal fun SetupCandidateRejection.toRejectionView(): RejectionView = RejectionView(
    planId = planId,
    reasonCode = reasonCode,
    requiredMinutes = requiredMinutes,
    apparatusKey = apparatusKey,
    needsApparatusConfirmation = needsApparatusConfirmation,
    missingRequirements = missingRequirements,
    stage = when (stage) {
        SetupCandidateRejectionStage.CATALOG -> PlanEvaluationStage.CATALOG
        SetupCandidateRejectionStage.PROFILE -> PlanEvaluationStage.PROFILE
        SetupCandidateRejectionStage.FREQUENCY -> PlanEvaluationStage.FREQUENCY_SPLIT
        SetupCandidateRejectionStage.MATERIAL -> PlanEvaluationStage.MATERIAL
        SetupCandidateRejectionStage.MATERIALIZATION -> PlanEvaluationStage.MATERIALIZATION
        SetupCandidateRejectionStage.DURATION -> PlanEvaluationStage.SESSION_DURATION
        SetupCandidateRejectionStage.COMPOSITION -> PlanEvaluationStage.COMPOSITION
    },
)

/**
 * La disciplina de un plan con las palabras del asistente (Fuerza, Fuerza y músculo, Músculo), nunca «powerlifting»:
 * los nombres deportivos internos no se ofrecen como etiquetas de la interfaz.
 */
internal fun disciplineLabelOf(planId: String?): String? {
    val references = planId?.let(PersonalizedPlanCatalog::find)?.references ?: return null
    return when {
        TrainingReference.POWERLIFTING in references -> SetupGoal.STRENGTH.label
        TrainingReference.POWERBUILDING in references -> SetupGoal.STRENGTH_MUSCLE.label
        TrainingReference.HYPERTROPHY in references -> SetupGoal.MUSCLE.label
        else -> null
    }
}

/**
 * Lo que el presentador necesita saber de las respuestas de la persona para escribir el texto.
 *
 *  - H14: [PresentationContext.apparatusKeyOf] resuelve cada requisito de material con la llave que sigue SIN
 *    responder ([PlanRepairAdvisor.confirmableKeyFor]), la misma que confirma el botón de un toque; si todas están
 *    respondidas (un rechazo por material ausente) conserva la primera del panel, como antes.
 *  - H12: [PresentationContext.ownPlanId] y [PresentationContext.goalProfile] dejan que el presentador diga, con su
 *    único texto, el requisito de resistencia del plan propio de Fuerza y músculo.
 */
internal fun presentationContextOf(draft: SetupWizardDraft): PresentationContext {
    val availability = draft.trainingOptions.availability
    val goalProfile = planGoalProfileOf(draft.goal)
    return PresentationContext(
        userMinutes = draft.minutesPerSession,
        goalLabel = draft.goal?.label,
        daysChosen = draft.daysPerWeek,
        disciplineLabelOf = ::disciplineLabelOf,
        planDaysOf = { planId -> planId?.let(PersonalizedPlanCatalog::find)?.supportedFrequencies },
        apparatusKeyOf = { token ->
            PlanRepairAdvisor.confirmableKeyFor(token, availability) ?: SetupApparatusPanel.keyForToken(token)
        },
        ownPlanId = ownPlanIdOf(goalProfile),
        goalProfile = goalProfile,
    )
}

/** La presentación del presentador único, con el contexto de las respuestas de la persona (sin excepciones locales). */
private fun presentationFor(rejection: SetupCandidateRejection, draft: SetupWizardDraft): RejectionPresentation =
    PlanRejectionPresenter.present(rejection.toRejectionView(), presentationContextOf(draft))

/**
 * Etiqueta del botón de las reparaciones (D5): «Sí, tengo rack y banco», «Cambiar a Músculo», «Ajustar a N min»,
 * «Cardio de N min», «Quitar el reparto». Con una reparación encadenada (confirmar material y, con él, más minutos)
 * o un cambio de objetivo que además pide minutos, la etiqueta lo dice: «Sí, tengo rack y banco · ajustar a 75 min».
 */
internal fun repairLabelOf(repairs: List<PlanRepair>): String? {
    val first = repairs.firstOrNull() ?: return null
    val chainedMinutes = repairs.drop(1).filterIsInstance<PlanRepair.SetMinutes>().firstOrNull()?.minutes
    return when (first) {
        is PlanRepair.ConfirmApparatus -> {
            val items = first.keys.map(::shortApparatusLabel).distinct()
            val have = if (items.isEmpty()) "Sí, tengo el material" else "Sí, tengo ${joinSpanish(items)}"
            if (chainedMinutes != null) "$have · ajustar a $chainedMinutes min" else have
        }
        is PlanRepair.SwitchGoal -> {
            val label = "Cambiar a ${first.goal.toSetupGoal()?.label ?: "otro objetivo"}"
            first.alsoMinutes?.let { minutes -> "$label · ajustar a $minutes min" } ?: label
        }
        is PlanRepair.SetMinutes -> "Ajustar a ${first.minutes} min"
        is PlanRepair.SetCardioMinutes -> "Cardio de ${first.minutes} min"
        PlanRepair.ClearSplit -> "Quitar el reparto"
    }
}

/**
 * El aviso de UN rechazo: el texto del presentador y hasta dos botones. Si el rechazo trae reparaciones de un toque
 * (solo el del plan propio), la reparación es el botón principal y el primero del presentador (la acción de navegación
 * equivalente: «Confirmar material», «Cambiar objetivo»…) pasa a secundario; «Ajustar a N min» no se duplica.
 *
 * [keepSeeAlternatives] decide si «Ver alternativas» sobrevive: solo tiene sentido donde las alternativas están a la
 * vista (el aviso de la selección caída, encima de la lista). Sin lista (ningún plan viable) no hay nada que ver y, si
 * el botón era el único, queda «Reintentar».
 */
internal fun rejectionNotice(
    rejection: SetupCandidateRejection,
    draft: SetupWizardDraft,
    keepSeeAlternatives: Boolean,
): RejectionNotice {
    val presentation = presentationFor(rejection, draft)
    val repairs = rejection.repairs
    val repairButton = repairLabelOf(repairs)?.let { label -> NoticeButton(label, NoticeEffect.Apply(repairs)) }
    val fromPresenter = presentation.actions
        .filter { action -> keepSeeAlternatives || action != RejectionAction.SeeAlternatives }
        .filterNot { action -> action is RejectionAction.SetMinutes && repairs.any { it is PlanRepair.SetMinutes } }
        .map { action -> NoticeButton(action.label, NoticeEffect.Act(action)) }
    val buttons = (listOfNotNull(repairButton) + fromPresenter)
        .take(MAX_NOTICE_BUTTONS)
        .ifEmpty { listOf(NoticeButton(RejectionAction.Retry.label, NoticeEffect.Act(RejectionAction.Retry))) }
    // Cuando solo cabe bajando el cardio, el botón lo nombra y el texto dice por qué es la salida.
    val cardioHint = repairs.filterIsInstance<PlanRepair.SetCardioMinutes>().firstOrNull()
        ?.let { repair -> " Con ${repair.minutes} min de cardio sí cabe." }
        .orEmpty()
    // «Cambiar a Fuerza y músculo» solo se propone con mancuernas: el texto avisa de que los principales las usarán.
    val dumbbellHint = if (repairs.any { it is PlanRepair.SwitchGoal && it.goal == PlanGoalProfile.STRENGTH_MUSCLE }) {
        STRENGTH_MUSCLE_DUMBBELLS_HINT
    } else {
        ""
    }
    return RejectionNotice(
        text = presentation.text + cardioHint + dumbbellHint,
        primary = buttons.first(),
        secondary = buttons.getOrNull(1),
    )
}

/**
 * Aviso de la selección caída: [DROPPED_SELECTION_LEAD] y el motivo del presentador, con sus botones (y «Ver
 * alternativas», que solo cierra el aviso). Sin rechazo —el plan ni se evaluó, el planificador ya lo descartó por
 * objetivo, nivel o días— explica eso y ofrece «Ver alternativas» y «Cambiar objetivo». El `reason` crudo no se pinta.
 */
internal fun droppedSelectionNotice(dropped: SetupDroppedSelection, draft: SetupWizardDraft): RejectionNotice {
    val rejection = dropped.rejection
        ?: return RejectionNotice(
            text = "$DROPPED_SELECTION_LEAD Ya no está entre los planes que corresponden a tus respuestas.",
            primary = NoticeButton(RejectionAction.SeeAlternatives.label, NoticeEffect.Act(RejectionAction.SeeAlternatives)),
            secondary = NoticeButton(RejectionAction.ChangeGoal.label, NoticeEffect.Act(RejectionAction.ChangeGoal)),
        )
    val notice = rejectionNotice(rejection, draft, keepSeeAlternatives = true)
    return notice.copy(text = "$DROPPED_SELECTION_LEAD ${notice.text}")
}

/**
 * Aviso del plan propio del objetivo encima de la lista (C4): solo cuando su rechazo trae reparaciones, no está entre
 * los viables y no es ya la selección caída (que tiene su propio aviso, con las mismas reparaciones). Es lo que ve la
 * persona que pidió Fuerza con el gimnasio sin confirmar y tiene otros planes debajo: el plan que esperaba, por qué no
 * está y el botón que lo arregla.
 */
internal fun ownPlanNotice(state: SetupWizardState, droppedPlanId: String?): RejectionNotice? {
    val draft = state.draft
    val goalLabel = draft.goal?.label ?: return null
    val ownId = ownPlanIdOf(planGoalProfileOf(draft.goal)) ?: return null
    if (droppedPlanId == ownId) return null
    if (state.availablePlanCandidates.any { candidate -> candidate.id == ownId }) return null
    val rejection = state.candidateRejections.firstOrNull { it.planId == ownId && it.repairs.isNotEmpty() } ?: return null
    val notice = rejectionNotice(rejection, draft, keepSeeAlternatives = false)
    return notice.copy(text = "No pudimos armar tu plan de ${goalLabel.lowercaseFirst()} con tus respuestas. ${notice.text}")
}

/**
 * El aviso de la lista SIN planes viables: el rechazo más accionable (el del plan propio; luego material por confirmar
 * con llave; luego el de menos minutos; luego perfil o material ausente; si no, el primero) y su reparación. Sin
 * ningún rechazo por plan (el planificador no encontró nada) queda el resumen de la búsqueda y «Reintentar».
 */
internal fun incompatibilityNotice(state: SetupWizardState): RejectionNotice {
    val draft = state.draft
    val rejections = state.candidateRejections
    val views = rejections.map { it.toRejectionView() }
    val primary = PlanRejectionPresenter.primary(views, ownPlanIdOf(planGoalProfileOf(draft.goal)))
        ?.let { view -> views.indexOf(view) }
        ?.let { index -> rejections.getOrNull(index) }
        ?: return RejectionNotice(
            text = state.errors["candidates"] ?: "Ahora mismo no hay un plan compatible con tus respuestas.",
            primary = NoticeButton(RejectionAction.Retry.label, NoticeEffect.Act(RejectionAction.Retry)),
        )
    return rejectionNotice(primary, draft, keepSeeAlternatives = false)
}

/** Lo que hace un botón con la API del asistente. [onSeeAlternatives] cierra el aviso donde las alternativas están a la vista. */
internal fun performNoticeEffect(effect: NoticeEffect, vm: SetupWizardViewModel, onSeeAlternatives: () -> Unit = {}) {
    when (effect) {
        is NoticeEffect.Apply -> vm.applyRepairs(effect.repairs)
        is NoticeEffect.Act -> performRejectionAction(effect.action, vm, onSeeAlternatives)
    }
}

/** Mapa de las acciones del presentador a la API del asistente (A.C3): navegar con `editStep`, reintentar o ajustar minutos. */
internal fun performRejectionAction(action: RejectionAction, vm: SetupWizardViewModel, onSeeAlternatives: () -> Unit = {}) {
    when (action) {
        RejectionAction.Retry -> vm.retryFailedOperation(SetupRetryOperation.CANDIDATES)
        RejectionAction.ChangeGoal -> vm.editStep(SetupStepId.GOAL)
        RejectionAction.SeeAlternatives -> onSeeAlternatives()
        RejectionAction.ChangeDays -> vm.editStep(SetupStepId.DAYS)
        RejectionAction.ChangeSplit -> vm.editStep(SetupStepId.SPLIT)
        RejectionAction.ConfirmApparatus -> vm.editStep(SetupStepId.AVAILABILITY)
        is RejectionAction.SetMinutes -> vm.applyRepair(PlanRepair.SetMinutes(action.minutes))
    }
}

/** Pinta un aviso de rechazo con sus dos botones; cada botón ejecuta su efecto con la API del asistente. */
@Composable
private fun RejectionNoticeView(
    notice: RejectionNotice,
    vm: SetupWizardViewModel,
    onSeeAlternatives: () -> Unit = {},
) {
    TrainingNotice(
        text = notice.text,
        tone = TrainingNoticeTone.ERROR,
        actionLabel = notice.primary?.label,
        onAction = notice.primary?.let { button -> { performNoticeEffect(button.effect, vm, onSeeAlternatives) } },
        secondaryActionLabel = notice.secondary?.label,
        onSecondaryAction = notice.secondary?.let { button -> { performNoticeEffect(button.effect, vm, onSeeAlternatives) } },
    )
}

/**
 * Candidatos reales con sus metadatos; se eligen antes de las marcas (el grafo
 * coloca PLAN antes de TRAINING_MAX/TRAINING_MARKS). Carga, error y vacío son
 * estados explícitos con reintento, no carrusel infinito.
 *
 * Paquete A · D2 (B-01): la puerta de la lista es [candidateListGate]. Solo el fallo de la BÚSQUEDA
 * esconde la lista; el error del preview del plan elegido y la selección caída salen como avisos encima de
 * las tarjetas, que siguen visibles para poder elegir otro plan.
 */
@Composable
private fun TrainingPlanStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.PLAN
    val draft = state.draft
    // C.P5: el plan cuya hoja «Cómo funciona» está abierta (su id). Sobrevive a girar la pantalla.
    var infoPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
        FromScratchSessions(state = state)
        return
    }

    when (val gate = candidateListGate(state)) {
        CandidateListGate.Loading -> TrainingLoading("Buscando planes compatibles con tus respuestas…")
        is CandidateListGate.SearchFailed -> TrainingNotice(
            text = gate.message,
            tone = TrainingNoticeTone.ERROR,
            actionLabel = "Reintentar",
            onAction = { vm.retryFailedOperation(SetupRetryOperation.CANDIDATES) },
        )

        CandidateListGate.NoneViable -> CandidateIncompatibility(state = state, vm = vm)

        is CandidateListGate.Candidates -> {
            gate.dropped?.let { dropped -> DroppedSelectionBanner(dropped = dropped, state = state, vm = vm) }
            // C4: el plan propio del objetivo rechazado con reparación, aunque debajo haya otros planes viables.
            gate.ownPlanNotice?.let { notice -> RejectionNoticeView(notice = notice, vm = vm) }
            gate.previewError?.let { previewError ->
                TrainingNotice(
                    text = previewError,
                    tone = TrainingNoticeTone.ERROR,
                    actionLabel = "Reintentar",
                    onAction = { vm.retryFailedOperation(SetupRetryOperation.PREVIEW) },
                )
            }
            val selected = draft.selectedValues(step)
            gate.cards.forEach { candidate ->
                WizardChoiceCard(
                    title = candidate.title,
                    subtitle = planCandidateSubtitle(candidate),
                    selected = draft.programRoute != SetupProgramRoute.LATER && candidate.id in selected,
                    onClick = { vm.selectPlan(candidate.id) },
                    footer = { PlanInfoLink(candidate = candidate, onClick = { infoPlanId = candidate.id }) },
                )
            }
            val hidden = state.availablePlanCandidates.size - state.planCandidates.size
            if (hidden > 0) {
                // §15.2: el límite de tarjetas es visual; paginar NO rematerializa
                // (los Ready viven en `availablePlanCandidates` y en la caché).
                TextButton(onClick = { vm.showMoreCandidates() }) {
                    Text("Ver más opciones ($hidden)", color = WizardColors.text, style = WizardTypography.cardTitle)
                }
            }
            CandidateCountsLine(state = state)
        }
    }

    DeferProgramChoice(selected = draft.programRoute == SetupProgramRoute.LATER, onClick = vm::deferProgramUntilLater)

    infoPlanId?.let { planId ->
        CandidatePlanInfo(planId = planId, vm = vm, onDismiss = { infoPlanId = null })
    }
}

/**
 * Enlace «Ver cómo funciona» al pie de la tarjeta de un plan (C.P5). Es un botón aparte de la tarjeta, que solo
 * elige: abre la hoja del plan sin cambiar la selección. Cada enlace dice de qué plan es para TalkBack.
 */
@Composable
private fun PlanInfoLink(candidate: SetupPlanCandidate, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .testTag("plan-info-${candidate.id}")
            .semantics { contentDescription = "$PLAN_INFO_LINK ${candidate.title}" },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
    ) {
        Text(text = PLAN_INFO_LINK, color = WizardColors.text, style = WizardTypography.cardSubtitle)
    }
}

private const val PLAN_INFO_LINK = "Ver cómo funciona"

/** Texto exacto de la tarjeta que aplaza el programa en el paso PLAN. */
internal const val DEFER_PROGRAM_CHOICE = "Yo haré mi programa de entreno manualmente más adelante"

/** Lo que dicen el hito y la revisión cuando no se crea un programa ahora. */
internal const val DEFER_PROGRAM_REVIEW_VALUE = "Lo armarás manualmente más adelante"

/**
 * Escape del catálogo: la persona no elige un plan y armará el suyo después.
 * Visible también mientras la lista carga, falla o no tiene planes compatibles.
 */
@Composable
private fun DeferProgramChoice(selected: Boolean, onClick: () -> Unit) {
    WizardChoiceCard(
        title = DEFER_PROGRAM_CHOICE,
        subtitle = "No creamos un programa ahora. Podrás armarlo desde cero cuando quieras.",
        selected = selected,
        onClick = onClick,
    )
}

/**
 * La hoja «Cómo funciona» de un candidato del asistente (C.P5): la entrada del catálogo con la semana real que
 * ya calculó la evaluación (solo la pintan los planes sin receta) y «Elegir este plan» como botón principal, que
 * elige el plan y cierra la hoja. El glosario enlaza a Conceptos clave (C.P6) cuando la pantalla recibe el
 * callback de navegación ([LocalOpenConcept]); al volver, el asistente sigue donde estaba.
 */
@Composable
private fun CandidatePlanInfo(planId: String, vm: SetupWizardViewModel, onDismiss: () -> Unit) {
    val entry = remember(planId) { PersonalizedPlanCatalog.find(planId) } ?: return
    val readyWeek = remember(planId) { vm.readyWeekSnapshotFor(planId) }
    PlanInfoSheet(
        entry = entry,
        mode = PlanInfoMode.WIZARD,
        readyWeek = readyWeek,
        onDismiss = onDismiss,
        onPrimaryAction = {
            vm.selectPlan(planId)
            onDismiss()
        },
        onOpenConcept = LocalOpenConcept.current,
    )
}

/**
 * Aviso encima de la lista: el plan que la persona tenía elegido ya no encaja con sus respuestas. La acción
 * lleva al paso que lo arregla; «Ver alternativas» solo cierra el aviso (el estado del ViewModel se
 * conserva: sigue sin lanzar el preview de ese plan). Elegir otro plan lo retira del estado.
 */
@Composable
private fun DroppedSelectionBanner(
    dropped: SetupDroppedSelection,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    var closed by rememberSaveable(dropped.planId) { mutableStateOf(false) }
    if (closed) return
    val notice = droppedSelectionNotice(dropped, state.draft)
    RejectionNoticeView(notice = notice, vm = vm, onSeeAlternatives = { closed = true })
}

/** §15.2: revisados / encajan (nunca «publicados» de un subconjunto). */
@Composable
private fun CandidateCountsLine(state: SetupWizardState) {
    val counts = state.candidateCounts
    if (counts.evaluated == 0) return
    Text(
        text = candidateCountsText(counts, adaptedToBodyweight = state.planAdaptedToBodyweight),
        style = WizardTypography.bodySmall,
        color = WizardColors.textMuted,
        modifier = Modifier.testTag("setup-candidate-counts"),
    )
}

/** Cierre del conteo cuando las tarjetas salen del pase a peso corporal (los conteos son los del pase pedido). */
internal const val ADAPTED_TO_BODYWEIGHT_COUNT_SUFFIX = "te mostramos planes de peso corporal"

/**
 * «12 planes revisados · 3 encajan con tus respuestas» (C.P5): cada cifra concuerda con su sustantivo y con su
 * verbo. Con uno solo («1 plan revisado · 1 encaja con tus respuestas», lo normal en Atleta completo) y con
 * ninguno («… · ninguno encaja con tus respuestas») el texto cambia de forma, no solo de número. Los que no
 * encajan no se cuentan aparte: son la resta.
 *
 * Con [adaptedToBodyweight] las tarjetas que se ven NO son las que se contaron (el conteo es del pase pedido y las
 * tarjetas, del segundo pase a peso corporal): el texto lo dice para que «ninguno encaja» no parezca un error al
 * lado de unas tarjetas («… · ninguno encaja con tus respuestas; te mostramos planes de peso corporal»).
 */
internal fun candidateCountsText(counts: SetupCandidateCounts, adaptedToBodyweight: Boolean = false): String {
    val reviewed = SpanishPlurals.withNoun(counts.evaluated, "plan revisado", "planes revisados")
    val fitting = when (counts.viable) {
        0 -> "ninguno encaja con tus respuestas"
        else -> SpanishPlurals.choose(
            counts.viable,
            "1 encaja con tus respuestas",
            "${counts.viable} encajan con tus respuestas",
        )
    }
    val line = "$reviewed · $fitting"
    return if (adaptedToBodyweight) "$line; $ADAPTED_TO_BODYWEIGHT_COUNT_SUFFIX" else line
}

/**
 * T-005 / §15.2: incompatibilidad con ACCIONES concretas, antes de revisión y sin tocar las respuestas de la persona.
 * El texto y los botones los decide [incompatibilityNotice], que se apoya en el presentador único de rechazos (C.P11):
 * error de catálogo → «Reintentar»; material por confirmar → «Sí, tengo rack y banco» cuando hay reparación o
 * «Confirmar material»; tiempo insuficiente → «Ajustar a N min»; objetivo que no cabe → «Cambiar a Músculo»…
 * El texto crudo del motor ya no se pinta: solo va al registro.
 */
@Composable
private fun CandidateIncompatibility(state: SetupWizardState, vm: SetupWizardViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        RejectionNoticeView(notice = incompatibilityNotice(state), vm = vm)
        CandidateCountsLine(state = state)
    }
}

private fun planCandidateSubtitle(candidate: SetupPlanCandidate): String =
    (listOf(candidate.subtitle) + candidate.reasons)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/** Ruta legacy «desde cero»: resumen real de las sesiones ya montadas. */
@Composable
private fun FromScratchSessions(state: SetupWizardState) {
    val sessions = state.draft.sessions.sortedBy { it.weekday }
    if (sessions.isEmpty()) {
        TrainingNotice("Todavía no has montado tus sesiones.", TrainingNoticeTone.ERROR)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        sessions.forEach { session ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WizardColors.cardFill, WizardShapes.card)
                    .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${weekdayLabel(session.weekday) ?: "Día ${session.weekday}"} · ${session.title}",
                    style = WizardTypography.cardTitle,
                    color = WizardColors.text,
                )
                if (session.exercises.isEmpty()) {
                    Text("Sin ejercicios todavía.", style = WizardTypography.cardSubtitle, color = WizardColors.danger)
                }
                session.exercises.forEach { item ->
                    Text(
                        text = scratchExerciseLine(name = item.name, sets = item.sets, reps = item.reps),
                        style = WizardTypography.cardSubtitle,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }
    }
}

/** Línea de un ejercicio montado a mano: «Sentadilla · 1 serie × 1 rep» o «· 3 series × 8 reps». */
internal fun scratchExerciseLine(name: String, sets: Int?, reps: Int?): String =
    "$name · ${sets?.let(SpanishPlurals::sets) ?: "— series"} × ${reps?.let(SpanishPlurals::reps) ?: "— reps"}"

// ─── TRAINING_MAX / TRAINING_MARKS ──────────────────────────────────────────

/**
 * Marcas SBD en pestañas: un lift visible cada vez y **un** campo de entrada
 * (≤2 elementos relacionados en pantalla), con fila de resumen de las tres.
 */
@Composable
private fun TrainingMarksStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.TRAINING_MARKS
    val options = SetupStepDefinitions.options(step)
    if (options.isEmpty()) {
        TrainingNotice("Este paso no tiene opciones disponibles.", TrainingNoticeTone.ERROR)
        return
    }
    var activeLift by remember(step) { mutableStateOf(options.first().value) }
    var rawLifts by remember(step) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val profile = state.draft.powerliftingProfile

    options.forEach { option ->
        val stored = liftValue(profile, option.value)
        WizardChoiceCard(
            title = option.label,
            subtitle = stored?.let { "Marca actual: ${formatTrainingNumber(it)} kg" } ?: "Sin marca declarada",
            selected = option.value == activeLift,
            onClick = { activeLift = option.value },
        )
    }
    val activeLabel = options.firstOrNull { it.value == activeLift }?.label ?: options.first().label
    val raw = rawLifts[activeLift] ?: liftValue(profile, activeLift)?.let(::formatTrainingNumber).orEmpty()
    val parsed = parseLocalizedNumber(raw)
    val outOfRange = parsed != null && parsed !in 1.0..1000.0

    TrainingNumberField(
        label = "Marca en kg · $activeLabel",
        value = raw,
        onValueChange = { text ->
            rawLifts = rawLifts + (activeLift to text)
            val value = if (text.isBlank()) null else parseLocalizedNumber(text)?.takeIf { it in 1.0..1000.0 }
            writeMark(vm, step, activeLift, value)
        },
        isError = raw.isNotBlank() && (parsed == null || outOfRange),
    )
    if (raw.isNotBlank() && (parsed == null || outOfRange)) {
        TrainingNotice("Usa un valor entre 1 y 1000 kg.", TrainingNoticeTone.ERROR)
    }
}

private fun liftValue(profile: PowerliftingProfile?, lift: String): Double? = when (lift) {
    "squat" -> profile?.squat1RM
    "bench" -> profile?.bench1RM
    else -> profile?.deadlift1RM
}

private fun writeMark(vm: SetupWizardViewModel, step: SetupStepId, lift: String, value: Double?) {
    vm.updateStep(step) { draft ->
        val base = draft.powerliftingProfile ?: PowerliftingProfile()
        draft.copy(
            powerliftingProfile = when (lift) {
                "squat" -> base.copy(squat1RM = value)
                "bench" -> base.copy(bench1RM = value)
                else -> base.copy(deadlift1RM = value)
            },
        )
    }
}

// ─── AUTORREGULACIÓN ────────────────────────────────────────────────────────

/**
 * OFF / PROPOSE / AUTO sobre `trainingOptions.autoregulationMode`. PROPOSE es
 * el estado por defecto del contrato; cambiar de modo exige confirmar AUTO en
 * el paso siguiente (el paso de confirmación solo existe con AUTO).
 */
@Composable
private fun TrainingAutoregulationStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.AUTOREGULATION
    val mode = state.draft.trainingOptions.autoregulationMode
    val modes = listOf(
        Triple(AutoregulationMode.OFF, "No, lo controlo yo", "El plan no propone cambios de series ni de pesos."),
        Triple(AutoregulationMode.PROPOSE, "Propuestas que yo confirmo", "KPKN propone ajustes y tú los aceptas antes de aplicarlos. Es la opción por defecto."),
        Triple(AutoregulationMode.AUTO, "Ajuste automático cada semana", "Series y pesos se ajustan según tu respuesta; te pediremos confirmación."),
    )
    modes.forEach { (id, label, hint) ->
        WizardChoiceCard(
            title = label,
            subtitle = hint,
            selected = mode == id,
            enabled = true,
            onClick = {
                if (mode != id) {
                    vm.updateStep(step) { draft ->
                        draft.copy(
                            trainingOptions = draft.trainingOptions.copy(
                                autoregulationMode = id,
                                // Cualquier cambio de modo vuelve a pedir confirmación explícita.
                                automaticConfirmed = false,
                            ),
                        )
                    }
                }
            },
        )
    }
    SetupBodySkipAction(step = step, vm = vm)
}

/**
 * Confirmación explícita de AUTO: sin `automaticConfirmed` el contrato rechaza
 * el modo. «Solo revisar» vuelve a PROPOSE y regresa al paso de modo para que
 * el cursor nunca quede apuntando a un paso fuera de la ruta.
 */
@Composable
private fun TrainingAutoregulationConfirmStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.AUTOREGULATION_CONFIRM
    val options = SetupStepDefinitions.options(step)
    val training = state.draft.trainingOptions
    val confirmed = training.autoregulationMode == AutoregulationMode.AUTO && training.automaticConfirmed

    WizardChoiceCard(
        title = options.firstOrNull { it.value == "confirmed" }?.label ?: "Confirmar",
        subtitle = "Mantener el ajuste automático: los cambios se aplican con esta confirmación y siempre puedes revertirlos.",
        selected = confirmed,
        onClick = {
            if (!confirmed) {
                vm.updateStep(step) { draft ->
                    draft.copy(
                        trainingOptions = draft.trainingOptions.copy(
                            autoregulationMode = AutoregulationMode.AUTO,
                            automaticConfirmed = true,
                        ),
                    )
                }
            }
        },
    )
    WizardChoiceCard(
        title = options.firstOrNull { it.value == "review_only" }?.label ?: "Solo revisar",
        subtitle = "Volver a proponer cambios: nada se aplica sin que tú lo confirmes.",
        selected = training.autoregulationMode != AutoregulationMode.AUTO,
        onClick = {
            vm.updateStep(step) { draft ->
                draft.copy(
                    trainingOptions = draft.trainingOptions.copy(
                        autoregulationMode = AutoregulationMode.PROPOSE,
                        automaticConfirmed = false,
                    ),
                )
            }
            vm.editStep(SetupStepId.AUTOREGULATION)
        },
    )
}

// ─── WARMUPS ────────────────────────────────────────────────────────────────

/**
 * Calentamientos sobre la carga de trabajo: null = preset del plan
 * (40 % × 8, 60 % × 5, 80 % × 3, leído de `firstCompoundWarmupPercentSets`),
 * lista vacía = sin calentamiento, lista = filas editables (porcentaje +
 * repeticiones). No se mezclan con los calentamientos de recetas de autor.
 */
@Composable
private fun TrainingWarmupsStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.WARMUPS
    val warmup = state.draft.trainingOptions.warmup
    val preset = firstCompoundWarmupPercentSets()
    var rawRows by remember(step) { mutableStateOf(rawRowsFor(warmup ?: preset)) }
    val custom = warmup != null && warmup.isNotEmpty()

    WizardChoiceCard(
        title = "Estándar del plan",
        subtitle = presetSummary(preset),
        selected = warmup == null,
        onClick = { writeWarmupMode(vm, step, null) },
    )
    WizardChoiceCard(
        title = "Personalizado",
        subtitle = "Editas porcentaje y repeticiones sobre la carga de trabajo.",
        selected = custom,
        onClick = {
            val seed = rawRowsFor(warmup?.takeIf { it.isNotEmpty() } ?: preset)
            rawRows = seed
            writeWarmups(vm, step, seed)
        },
    )
    WizardChoiceCard(
        title = "Sin calentamiento automático",
        subtitle = "No se añaden aproximaciones del plan; las recetas de autor se conservan intactas.",
        selected = warmup?.isEmpty() == true,
        onClick = { writeWarmupMode(vm, step, emptyList()) },
    )

    if (custom) {
        Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
            rawRows.forEachIndexed { index, (percentRaw, repsRaw) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrainingNumberField(
                        label = "% de la carga",
                        value = percentRaw,
                        onValueChange = { text ->
                            rawRows = rawRows.replaceAt(index, text to repsRaw)
                            writeWarmups(vm, step, rawRows)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TrainingNumberField(
                        label = "Reps",
                        value = repsRaw,
                        onValueChange = { text ->
                            rawRows = rawRows.replaceAt(index, percentRaw to text)
                            writeWarmups(vm, step, rawRows)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            rawRows = rawRows.filterIndexed { i, _ -> i != index }
                            writeWarmups(vm, step, rawRows)
                        },
                    ) { Text("Quitar", color = WizardColors.danger) }
                }
            }
            TextButton(onClick = {
                rawRows = rawRows + ("" to "")
                writeWarmups(vm, step, rawRows)
            }) { Text("Añadir paso", color = WizardColors.text) }

            val recipes = warmupRecipesFromRaw(rawRows)
            val invalid = recipes.any { recipe ->
                val percent = recipe.percent
                val reps = recipe.reps
                percent == null || percent <= 0.0 || percent > 100.0 || reps == null || reps !in 1..60
            }
            if (invalid) {
                TrainingNotice(
                    text = "Cada paso necesita un porcentaje entre 1 y 100 y entre 1 y 60 repeticiones.",
                    tone = TrainingNoticeTone.ERROR,
                )
            }
        }
    }
    SetupBodySkipAction(step = step, vm = vm)
}

private fun rawRowsFor(recipes: List<SetRecipe>): List<Pair<String, String>> = recipes.map { recipe ->
    (recipe.percent?.let(::formatTrainingNumber) ?: "") to (recipe.reps?.toString() ?: "")
}

private fun writeWarmups(vm: SetupWizardViewModel, step: SetupStepId, raw: List<Pair<String, String>>) {
    val recipes = warmupRecipesFromRaw(raw)
    vm.updateStep(step) { draft ->
        draft.copy(trainingOptions = draft.trainingOptions.copy(warmup = recipes))
    }
}

/** Política de calentamiento: null = preset del plan, lista vacía = sin calentamiento. */
private fun writeWarmupMode(vm: SetupWizardViewModel, step: SetupStepId, warmup: List<SetRecipe>?) {
    vm.updateStep(step) { draft ->
        draft.copy(trainingOptions = draft.trainingOptions.copy(warmup = warmup))
    }
}

private fun presetSummary(preset: List<SetRecipe>): String = preset.joinToString(" · ") { recipe ->
    "${recipe.percent?.let(::formatTrainingNumber) ?: "—"} % × ${recipe.reps ?: "—"}"
}

// ─── TRAINING_REVIEW ────────────────────────────────────────────────────────

/**
 * Revisión real: `programPreview` con sus sesiones, ejercicios, series y
 * días de la semana; error con reintento si no hay preview (nunca spinner
 * infinito) y confirmación genuina de la diferencia de la receta fija.
 */
@Composable
private fun TrainingReviewStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val program = state.programPreview
    val previewError = state.previewError
    when {
        state.isPreviewLoading -> TrainingLoading("Preparando tu programa con tus respuestas…")
        previewError != null -> TrainingNotice(
            text = previewError,
            tone = TrainingNoticeTone.ERROR,
            actionLabel = "Reintentar",
            onAction = { vm.retryFailedOperation(SetupRetryOperation.PREVIEW) },
        )

        program == null -> TrainingNotice(
            text = "Todavía no hay un programa preparado. Revisa días, tiempo disponible y el plan elegido; se preparará con tus respuestas.",
            tone = TrainingNoticeTone.ERROR,
        )

        else -> {
            FixedRecipeConfirmation(state = state, vm = vm)
            ProgramSessions(program = program)
            TrainingEditShortcuts(state = state, vm = vm)
        }
    }
}

/**
 * Diferencia real de una receta fija (días o minutos) con confirmación
 * explícita vía `acceptFixedRecipeDifference`: sin ella la activación queda
 * bloqueada en la revisión final.
 */
@Composable
private fun FixedRecipeConfirmation(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val fixedDays = state.fixedTrainingDays
    val fixedMinutes = state.fixedSessionEstimateMinutes
    val minutesLimit = draft.minutesPerSession ?: 100
    val overTime = fixedMinutes != null && fixedMinutes > minutesLimit
    val daysDiffer = fixedDays != null && fixedDays != draft.selectedWeekdays
    if (!overTime && !daysDiffer) return

    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        TrainingNotice(
            text = buildString {
                append("La receta fija programa ")
                if (fixedDays != null) {
                    append(fixedDays.sorted().joinToString(", ") { weekdayLabel(it) ?: "$it" })
                } else {
                    append("otros días")
                }
                if (overTime && fixedMinutes != null) {
                    append(" y dura hasta $fixedMinutes min por sesión (dispones de $minutesLimit min)")
                }
                append(".")
            },
            tone = TrainingNoticeTone.ERROR,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WizardColors.cardFill, WizardShapes.card)
                .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = draft.acceptFixedRecipeDifference,
                onCheckedChange = { accepted -> vm.acceptFixedRecipeDifference(accepted) },
            )
            Text(
                text = "Confirmo que asumo la rotación y la duración reales de esta receta.",
                style = WizardTypography.cardSubtitle,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProgramSessions(program: Program) {
    val firstWeek = program.macrocycles
        .firstOrNull()?.blocks
        ?.firstOrNull()?.mesocycles
        ?.firstOrNull()?.weeks
        ?.firstOrNull()
    val sessions = firstWeek?.sessions.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        TrainingSummaryRow(label = "Programa", value = program.name)
        if (sessions.isEmpty()) {
            TrainingNotice("El programa todavía no trae sesiones.", TrainingNoticeTone.ERROR)
        } else {
            sessions.sortedBy { session -> session.dayOfWeek ?: session.assignedDays.firstOrNull() ?: Int.MAX_VALUE }
                .forEach { session ->
                val day = session.dayOfWeek ?: session.assignedDays.firstOrNull()
                val dayLabel = session.scheduleLabel?.takeIf { it.isNotBlank() }
                    ?: weekdayLabel(day)
                    ?: "Sin día asignado"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WizardColors.cardFill, WizardShapes.card)
                        .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(dayLabel, style = WizardTypography.cardTitle, color = WizardColors.text)
                    Text(session.name, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
                    session.allExercises().forEach { exercise ->
                        Text(
                            text = "${exercise.name} · ${exerciseSummary(exercise)}",
                            style = WizardTypography.cardSubtitle,
                            color = WizardColors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/** «1 serie × 1 rep» / «3 series × 8 rep» de la vista previa del programa. */
internal fun exerciseSummary(exercise: Exercise): String = buildString {
    append(SpanishPlurals.sets(exercise.sets.size))
    val first = exercise.sets.firstOrNull()
    val reps = first?.targetReps
    val duration = first?.targetDuration
    when {
        reps != null -> append(" × $reps rep")
        duration != null -> append(" × $duration s")
        else -> Unit
    }
}

/** Accesos directos a los pasos del bloque para corregir una respuesta. */
@Composable
private fun TrainingEditShortcuts(state: SetupWizardState, vm: SetupWizardViewModel) {
    val route = SetupStepGraph.stepIds(state.draft.stepContext()).toSet()
    val shortcuts = listOf(
        SetupStepId.EQUIPMENT to "Material",
        SetupStepId.DAYS to "Días por semana",
        SetupStepId.SESSION_TIME to "Tiempo por sesión",
        SetupStepId.PRIORITIES to "Prioridades de orden",
        SetupStepId.TRAINING_MAX to "Marcas",
        SetupStepId.WARMUPS to "Calentamientos",
    ).filter { (id, _) -> id in route }
    if (shortcuts.isEmpty()) return

    shortcuts.forEach { (id, label) ->
        WizardChoiceCard(
            title = label,
            subtitle = "Tocar para cambiar esta respuesta",
            selected = false,
            onClick = { vm.editStep(id) },
        )
    }
}

// ─── MILESTONE_TRAINING ─────────────────────────────────────────────────────

/** Resumen real de lo respondido en el bloque antes de cerrarlo. */
@Composable
private fun TrainingMilestoneSummary(state: SetupWizardState) {
    val rows = trainingMilestoneRows(state)
    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        rows.forEach { (label, value) -> TrainingSummaryRow(label = label, value = value) }
    }
}

/**
 * Las filas del resumen del hito del bloque Entreno (etiqueta, valor), sin pintar. La fila «Reparto» lleva el nombre
 * en español llano que la persona vio al elegirlo, el mismo de la revisión ([draftSplitLabel], que a su vez usa
 * [splitDisplayName]): nunca el nombre técnico de la plantilla ni el id. Un reparto que el catálogo no conoce no
 * añade fila.
 */
internal fun trainingMilestoneRows(state: SetupWizardState): List<Pair<String, String>> {
    val draft = state.draft
    return buildList {
        draft.experience?.let { add("Experiencia" to it.label) }
        draft.goal?.let { add("Objetivo" to it.label) }
        draft.daysPerWeek?.let { add("Días por semana" to it.toString()) }
        if (draft.selectedWeekdays.isNotEmpty()) {
            add("Semana" to draft.selectedWeekdays.sorted().joinToString(", ") { weekdayLabel(it) ?: "$it" })
        }
        draft.minutesPerSession?.let { add("Por sesión" to "$it min") }
        if (draft.equipment.isNotEmpty()) {
            add("Material" to draft.equipment.joinToString(", ") { it.label })
        }
        if (draft.selectedSplitId != null) {
            draftSplitLabel(state)?.let { name -> add("Reparto" to name) }
        }
        if (draft.programRoute == SetupProgramRoute.LATER) {
            add("Plan" to DEFER_PROGRAM_REVIEW_VALUE)
        } else {
            draft.selectedCatalogId?.let { id ->
                // C.P6: el nombre de la ficha editorial; si el id ya no resuelve, nunca se pinta el id crudo.
                add("Plan" to (PersonalizedPlanCatalog.find(id)?.displayName ?: "Tu plan elegido"))
            }
        }
        if (draft.knowsTrainingMarks) {
            val marks = listOfNotNull(
                draft.powerliftingProfile?.squat1RM,
                draft.powerliftingProfile?.bench1RM,
                draft.powerliftingProfile?.deadlift1RM,
            )
            add("Marcas" to if (marks.isEmpty()) "Sin marcas declaradas" else marks.joinToString(" / ") { "${formatTrainingNumber(it)} kg" })
        }
        if (draft.programRoute != SetupProgramRoute.LATER) {
            add("Autorregulación" to autoregulationSummary(draft))
            add("Calentamientos" to warmupSummary(draft))
        }
    }
}

private fun autoregulationSummary(draft: SetupWizardDraft): String {
    val training = draft.trainingOptions
    return when (training.autoregulationMode) {
        AutoregulationMode.OFF -> "Desactivada"
        AutoregulationMode.PROPOSE -> "Propuestas (por defecto)"
        AutoregulationMode.AUTO ->
            if (training.automaticConfirmed) "Automática (confirmada)" else "Automática (pendiente de confirmación)"
    }
}

private fun warmupSummary(draft: SetupWizardDraft): String {
    val warmup = draft.trainingOptions.warmup
    return when {
        warmup == null -> "Estándar del plan"
        warmup.isEmpty() -> "Sin calentamiento automático"
        else -> "Personalizado (${SpanishPlurals.steps(warmup.size)})"
    }
}

/** Etiqueta del día de la semana (1 = lunes … 7 = domingo) desde las defs. */
private fun weekdayLabel(day: Int?): String? {
    if (day == null) return null
    return SetupStepDefinitions.options(SetupStepId.WEEKDAYS)
        .firstOrNull { it.value == day.toString() }
        ?.label
        ?: "Día $day"
}
