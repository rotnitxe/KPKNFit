package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.firstCompoundWarmupPercentSets
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.SetupControlKind
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

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

        SetupStepId.INVENTORY_BARBELL -> InventoryBarbellStep(step = step, state = state, vm = vm)
        SetupStepId.INVENTORY_PLATES -> InventoryPlatesStep(step = step, state = state, vm = vm)
        SetupStepId.INVENTORY_DUMBBELLS -> InventoryDumbbellsStep(step = step, state = state, vm = vm)
        SetupStepId.INVENTORY_KETTLEBELLS -> InventoryKettlebellsStep(step = step, state = state, vm = vm)
        SetupStepId.INVENTORY_MACHINES -> InventoryMachinesStep(step = step, state = state, vm = vm)
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

/** Splits aplicables hoy: visibles para aplicación y con el nº de días real. */
internal fun compatibleSplitTemplates(daysPerWeek: Int?, startDay: Int): List<SplitTemplate> =
    SPLIT_TEMPLATES.filter { split ->
        if (!split.isVisibleForApplication) return@filter false
        daysPerWeek == null ||
            SplitApplicationEngine.patternToTrainingDays(split.pattern, startDay).size == daysPerWeek
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
    SetupBodyHint(
        text = if (target == null) {
            "Días elegidos: ${selected.size}"
        } else {
            "Elige $target días · ${selected.size} de $target elegidos"
        },
    )
    SetupBodyMultiChoiceCards(
        step = step,
        selected = selected,
        onSelect = { value -> vm.toggleStepChoice(step, value) },
    )
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

    PriorityPresetRow(onApply = { preset ->
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

@Composable
private fun PriorityPresetRow(onApply: (Map<String, Int>) -> Unit) {
    PRIORITY_PRESETS.forEach { (label, preset) ->
        TextButton(onClick = { onApply(preset) }) {
            Text(label, color = WizardColors.text)
        }
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

private fun splitDisplayName(template: SplitTemplate): String = when (template.id) {
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
    val compatible = compatibleSplitTemplates(draft.daysPerWeek, startDay)
    var query by remember(step) { mutableStateOf("") }
    val featured = compatible.firstOrNull { SplitTag.RECOMENDADO_KPKN in it.tags } ?: compatible.firstOrNull()
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
                text = "Define el foco de tus $target días ($defined de $target).",
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
 * Candidatos reales con sus metadatos; se eligen antes de las marcas (el grafo
 * coloca PLAN antes de TRAINING_MAX/TRAINING_MARKS). Carga, error y vacío son
 * estados explícitos con reintento, no carrusel infinito.
 */
@Composable
private fun TrainingPlanStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.PLAN
    val draft = state.draft
    if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
        FromScratchSessions(state = state)
        return
    }

    val candidates = state.planCandidates.ifEmpty { state.availablePlanCandidates }
    val candidateError = state.previewError
    when {
        state.isCandidateLoading -> TrainingLoading("Buscando planes compatibles con tus respuestas…")
        candidateError != null -> TrainingNotice(
            text = candidateError,
            tone = TrainingNoticeTone.ERROR,
            actionLabel = "Reintentar",
            onAction = { vm.retryFailedOperation(SetupRetryOperation.CANDIDATES) },
        )

        candidates.isEmpty() -> TrainingNotice(
            text = "Ahora mismo no hay un plan compatible con tus respuestas. Puedes revisar días, tiempo o equipo y volver a intentarlo.",
            tone = TrainingNoticeTone.ERROR,
            actionLabel = "Reintentar",
            onAction = { vm.retryFailedOperation(SetupRetryOperation.CANDIDATES) },
        )

        else -> {
            val selected = draft.selectedValues(step)
            candidates.forEach { candidate ->
                WizardChoiceCard(
                    title = candidate.title,
                    subtitle = planCandidateSubtitle(candidate),
                    selected = candidate.id in selected,
                    onClick = { vm.selectPlan(candidate.id) },
                )
            }
            val hidden = state.availablePlanCandidates.size - state.planCandidates.size
            if (hidden > 0) {
                TextButton(onClick = { vm.showMoreCandidates() }) {
                    Text("Ver más opciones ($hidden)", color = WizardColors.text, style = WizardTypography.cardTitle)
                }
            }
        }
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
                        text = "${item.name} · ${item.sets ?: "—"} series × ${item.reps ?: "—"} reps",
                        style = WizardTypography.cardSubtitle,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }
    }
}

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
        subtitle = "Mantengo AUTO: los cambios se aplican con esta confirmación y siempre puedes revertirlos.",
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
        subtitle = "Vuelvo a PROPOSE: nada se aplica sin que tú lo confirmes.",
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

private fun exerciseSummary(exercise: Exercise): String = buildString {
    append(exercise.sets.size)
    append(" series")
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
    val draft = state.draft
    val rows: List<Pair<String, String>> = buildList {
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
        draft.selectedSplitId?.let { id ->
            val name = SPLIT_TEMPLATES.firstOrNull { it.id == id }?.name
                ?: if (id == SPLIT_CUSTOM) "Personalizado" else id
            add("Split" to name)
        }
        draft.selectedCatalogId?.let { id ->
            add("Plan" to (PersonalizedPlanCatalog.find(id)?.title ?: id))
        }
        if (draft.knowsTrainingMarks) {
            val marks = listOfNotNull(
                draft.powerliftingProfile?.squat1RM,
                draft.powerliftingProfile?.bench1RM,
                draft.powerliftingProfile?.deadlift1RM,
            )
            add("Marcas" to if (marks.isEmpty()) "Sin marcas declaradas" else marks.joinToString(" / ") { "${formatTrainingNumber(it)} kg" })
        }
        add("Autorregulación" to autoregulationSummary(draft))
        add("Calentamientos" to warmupSummary(draft))
    }
    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        rows.forEach { (label, value) -> TrainingSummaryRow(label = label, value = value) }
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
        else -> "Personalizado (${warmup.size} pasos)"
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
