package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionDistributionStatus
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardRadioMark
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.WizardWeightScale

/**
 * Revisión y activación: los cuatro bloques obligatorios como tarjetas
 * resumen con **datos reales**, más las confirmaciones que el alta exige.
 *
 * Reglas:
 * - Edición **por fila concreta** (`peso → WEIGHT`, `macros → NUTRITION_MANUAL_*`,
 *   `sensaciones → RINGS_*_FEELING`…): cada fila que tiene un paso en la ruta
 *   efectiva es su propio destino de `vm.editStep`, y la cabecera de la tarjeta
 *   lleva al primer paso del bloque. La tarjeta contenedora **no** es
 *   clicable, para que ningún click de padre absorba al hijo.
 * - Los datos se leen del borrador y de las vistas previas. Nada se rellena:
 *   lo desconocido aparece como «Sin declarar»/«Sin datos».
 * - Entreno: vista previa representativa de la **primera semana** y opción de
 *   expandir **semana a semana conservando las ocurrencias** (sin
 *   deduplicar: un `distinctBy` borraría sesiones reales), con tope de semanas
 *   y recuento real en el pie, sin fingir que es «todas las sesiones». Los
 *   ejercicios salen de `Session.allExercises()`, que lee también `parts`: un
 *   programa ejecutable nunca se resume como «Sin ejercicios».
 * - Nutrición: la cifra del plan es **base diaria (media)**, nunca el objetivo
 *   de hoy; el reparto sale de `nutritionPreparation.days`.
 * - RINGS: cobra `state.ringsCoveragePreview` (nullable). Sin cobertura →
 *   [RingsCoverage.NO_DATA] y «Sin datos» por canal; jamás se deriva un 100 %
 *   global.
 * - Confirmaciones con las APIs existentes: `requiresActivationConfirmation` →
 *   `vm.confirmActivation`, diferencia de receta fija →
 *   `vm.acceptFixedRecipeDifference`. Copy contextual: en solo registro no se
 *   promete un plan nutricional.
 * - El CTA de activación sigue siendo del Host; esta pantalla no confirma ni
 *   avanza.
 */
@Composable
fun SetupReviewStep(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    val route = SetupStepGraph.stepIds(state.draft.stepContext())

    fun firstStepOf(block: SetupWizardBlock): SetupStepId? =
        route.firstOrNull { SetupStepGraph.blockOf(it) == block && !SetupStepGraph.isMilestone(it) }

    /** Destino de edición sólo si el paso existe en la ruta (nunca un no-op). */
    fun editTarget(candidate: SetupStepId): (() -> Unit)? =
        if (candidate in route && candidate != state.currentStep) ({ vm.editStep(candidate) }) else null

    val edit: (SetupStepId) -> (() -> Unit)? = { candidate -> editTarget(candidate) }

    firstStepOf(SetupWizardBlock.BASICS)?.let { target ->
        ReviewBlockCard(title = "Datos básicos", onEdit = editTarget(target)) {
            BasicsSummary(state = state, edit = edit)
        }
    }
    firstStepOf(SetupWizardBlock.TRAINING)?.let { target ->
        ReviewBlockCard(title = "Entreno", onEdit = editTarget(target)) {
            TrainingSummary(state = state, route = route, edit = edit)
        }
    }
    firstStepOf(SetupWizardBlock.NUTRITION)?.let { target ->
        ReviewBlockCard(title = "Nutrición", onEdit = editTarget(target)) {
            NutritionSummary(state = state, edit = edit)
        }
    }
    firstStepOf(SetupWizardBlock.RINGS)?.let { target ->
        ReviewBlockCard(title = "Rings", onEdit = editTarget(target)) {
            RingsSummary(state = state, edit = edit)
        }
    }

    if (state.requiresActivationConfirmation) {
        SetupConfirmationCard(
            title = "Confirmo la activación",
            body = activationBody(state),
            confirmed = state.draft.confirmActivation,
            onToggle = { vm.confirmActivation(!state.draft.confirmActivation) },
        )
    }
    if (fixedRecipeDifference(state)) {
        SetupConfirmationCard(
            title = "Confirmo la rotación y la duración reales",
            body = "La receta fija propone unos días y unos minutos distintos a los que declaraste.",
            confirmed = state.draft.acceptFixedRecipeDifference,
            onToggle = { vm.acceptFixedRecipeDifference(!state.draft.acceptFixedRecipeDifference) },
        )
    }
}

/** Copy de la confirmación según el alcance real: solo registro no promete plan. */
private fun activationBody(state: SetupWizardState): String = when {
    !state.draft.includeNutrition -> "El programa se activa al confirmar."
    state.draft.stepContext().nutritionStartChoice == "tracking_only" ->
        "El programa se activa al confirmar. En solo registro no hay plan nutricional que activar."
    else -> "El programa y el plan nutricional se activan juntos al confirmar."
}

// ─── Resumen: datos básicos ──────────────────────────────────────────────────

@Composable
private fun BasicsSummary(
    state: SetupWizardState,
    edit: (SetupStepId) -> (() -> Unit)?,
) {
    val draft = state.draft
    val equationSex = draft.selectedValues(SetupStepId.EQUATION_SEX).firstOrNull()?.let { value ->
        when (value) {
            "female" -> "Femenino"
            "male" -> "Masculino"
            "unknown" -> "No lo sé"
            else -> value
        }
    } ?: when (draft.nutritionDraft?.equationSex) {
        EerSex.FEMALE -> "Femenino"
        EerSex.MALE -> "Masculino"
        null -> null
    }
    val bodyFat = when (val source = draft.bodyFatSource) {
        null -> null
        SetupBodyFatSource.UNKNOWN -> "No lo sé"
        SetupBodyFatSource.MEASURED, SetupBodyFatSource.VISUAL_ESTIMATE ->
            draft.bodyFatPercent?.let { percent ->
                "${formatPercent(percent)} % · " +
                    if (source == SetupBodyFatSource.MEASURED) "medido" else "estimación visual"
            }
    }
    SetupDataLine(label = "Nombre", value = draft.name.ifBlank { null }, onEdit = edit(SetupStepId.NAME))
    SetupDataLine(label = "Edad", value = draft.ageYears?.let { "$it años" }, onEdit = edit(SetupStepId.AGE))
    SetupDataLine(label = "Altura", value = draft.heightCm?.let { "${it.toInt()} cm" }, onEdit = edit(SetupStepId.HEIGHT))
    SetupDataLine(
        label = "Peso",
        value = draft.weightKg?.let { WizardWeightScale.formatWithUnit(it, WizardMassUnit.KG) },
        onEdit = edit(SetupStepId.WEIGHT),
    )
    SetupDataLine(label = "Sexo de cálculo", value = equationSex, onEdit = edit(SetupStepId.EQUATION_SEX))
    SetupDataLine(label = "Grasa corporal", value = bodyFat, onEdit = edit(SetupStepId.BODY_FAT))
}

// ─── Resumen: entreno ────────────────────────────────────────────────────────

private val INVENTORY_STEPS = listOf(
    SetupStepId.INVENTORY_BARBELL,
    SetupStepId.INVENTORY_PLATES,
    SetupStepId.INVENTORY_DUMBBELLS,
    SetupStepId.INVENTORY_KETTLEBELLS,
    SetupStepId.INVENTORY_MACHINES,
)

@Composable
private fun TrainingSummary(
    state: SetupWizardState,
    route: List<SetupStepId>,
    edit: (SetupStepId) -> (() -> Unit)?,
) {
    var expanded by rememberSaveable(state.draft.draftId, state.draft.commitId) { mutableStateOf(false) }
    val program = state.programPreview
    if (program == null) {
        SetupDataLine(label = "Programa", value = null, missing = "Sin vista previa")
        SetupFormCaption(
            state.previewError
                ?: if (state.isPreviewLoading) "Preparando el programa…" else "Todavía no hay un programa preparado.",
        )
        return
    }

    SetupDataLine(
        label = "Plan",
        value = program.name.ifBlank { null },
        onEdit = edit(SetupStepId.PLAN),
    )
    SetupDataLine(
        label = "Reparto semanal",
        value = draftSplitLabel(state),
        onEdit = edit(SetupStepId.SPLIT),
    )
    val inventoryStep = INVENTORY_STEPS.firstOrNull { it in route }
    if (inventoryStep != null) {
        SetupDataLine(label = "Inventario", value = inventoryLabel(state), onEdit = edit(inventoryStep))
    }

    val weeks = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
    val previewSessions = weeks.firstOrNull()?.sessions.orEmpty()
    val previewExercises = previewSessions.sumOf { it.allExercises().size }

    SetupDataLine(
        label = "Sesiones · muestra 1ª semana",
        value = "${previewSessions.size} sesiones · $previewExercises ejercicios",
        onEdit = edit(SetupStepId.TRAINING_REVIEW),
    )
    if (previewSessions.isEmpty()) {
        SetupFormCaption("La primera semana todavía no tiene sesiones prescritas.")
    } else {
        previewSessions.forEach { session -> SessionBlock(session) }
    }

    if (weeks.size <= 1) {
        SetupFormCaption("Programa de una sola semana.")
        return
    }

    if (!expanded) {
        TextButton(onClick = { expanded = true }) {
            Text(text = "Ver las ${weeks.size} semanas", color = WizardColors.text)
        }
    } else {
        val allSessions = weeks.sumOf { it.sessions.size }
        SetupDataLine(
            label = "Sesiones por semana",
            value = "$allSessions en ${weeks.size} semanas",
            onEdit = edit(SetupStepId.TRAINING_REVIEW),
        )
        // Ocurrencias reales: cada semana se lista tal cual. Sin deduplicar:
        // un distinctBy borraría sesiones reales que comparten id, día o nombre.
        weeks.take(SESSION_WEEK_LIMIT).forEachIndexed { index, week ->
            SetupFormCaption("Semana ${index + 1}")
            week.sessions.forEach { session -> SessionBlock(session) }
        }
        if (weeks.size > SESSION_WEEK_LIMIT) {
            SetupFormCaption(
                "Se muestran las primeras $SESSION_WEEK_LIMIT de ${weeks.size} semanas " +
                    "($allSessions sesiones en total).",
            )
        }
        TextButton(onClick = { expanded = false }) {
            Text(text = "Ocultar lista completa", color = WizardColors.textMuted)
        }
    }
}

private const val SESSION_WEEK_LIMIT = 8

@Composable
private fun SessionBlock(session: Session) {
    Text(
        text = "${dayLabel(session.dayOfWeek)} · ${session.name}",
        style = WizardTypography.cardTitle,
        color = WizardColors.text,
    )
    val exercises = session.allExercises()
    if (exercises.isEmpty()) {
        Text(
            text = "Sin ejercicios prescritos",
            style = WizardTypography.cardSubtitle,
            color = WizardColors.textMuted,
        )
    } else {
        exercises.forEach { exercise ->
            val brief = briefSetsReps(exercise)?.let { " — $it" }.orEmpty()
            Text(
                text = "• ${exercise.name}$brief",
                style = WizardTypography.bodySmall,
                color = WizardColors.textMuted,
            )
        }
    }
}

/** Serie × reps breves del primer set; sin datos no se inventa cifra. */
private fun briefSetsReps(exercise: Exercise): String? {
    val sets = exercise.sets
    if (sets.isEmpty()) return null
    val first = sets.first()
    val reps = first.effectiveRepRange()?.format()
        ?: first.targetDuration?.takeIf { it > 0 }?.let { "${it}s" }
    return if (reps != null) "${sets.size} × $reps" else "${sets.size} series"
}

private fun draftSplitLabel(state: SetupWizardState): String? {
    val draft = state.draft
    return draft.customSplitName
        ?: draft.selectedSplitId
        ?: state.programPreview?.selectedSplitId
        ?: null
}

private fun inventoryLabel(state: SetupWizardState): String? {
    val draft = state.draft
    val equipment = draft.equipment.joinToString(", ") { it.label }
    return listOfNotNull(
        draft.trainingEnvironment,
        equipment.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifBlank { null }
}

private val WEEKDAY_LABELS = listOf("", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

private fun dayLabel(day: Int?): String =
    day?.let { WEEKDAY_LABELS.getOrElse(it) { "Día $it" } } ?: "Sin día"

// ─── Resumen: nutrición ──────────────────────────────────────────────────────

@Composable
private fun NutritionSummary(
    state: SetupWizardState,
    edit: (SetupStepId) -> (() -> Unit)?,
) {
    val context = state.draft.stepContext()
    val preparation = state.nutritionPreparation
    val plan = preparation?.plan ?: state.nutritionPlanPreview
    when {
        context.nutritionStartChoice == "tracking_only" -> {
            SetupDataLine(label = "Modo", value = "Solo registrar comidas", onEdit = edit(SetupStepId.NUTRITION_START))
            SetupFormCaption("Sin plan de comidas ni metas: solo seguimiento.")
        }

        context.nutritionProfessional -> {
            SetupDataLine(
                label = "Modo",
                value = "Pauta indicada por un profesional",
                onEdit = edit(SetupStepId.NUTRITION_START),
            )
            NutritionPlanLines(preparation = preparation, plan = plan, edit = edit)
        }

        else -> {
            SetupDataLine(
                label = "Estado",
                value = preparation?.let { preparationStatusLabel(it.status) }
                    ?: if (plan != null) "Vista previa del plan" else null,
                missing = "Pendiente",
                onEdit = edit(SetupStepId.NUTRITION_START),
            )
            NutritionPlanLines(preparation = preparation, plan = plan, edit = edit)
            val error = preparation?.errors?.values?.firstOrNull()
                ?: state.nutritionErrors.values.firstOrNull()
            if (error != null) SetupFormCaption(error)
        }
    }
}

/**
 * Cifras del plan como **base diaria (media)**: el valor no es el objetivo de
 * hoy. El reparto se resume desde `preparation.days` (días reales con objetivo
 * proyectado), nunca como «hoy».
 */
@Composable
private fun NutritionPlanLines(
    preparation: com.example.kpkn.domain.onboarding.SetupNutritionPreparationResult?,
    plan: NutritionPlan?,
    edit: (SetupStepId) -> (() -> Unit)?,
) {
    if (plan != null) {
        val caloriesEdit = edit(SetupStepId.NUTRITION_MANUAL_CALORIES) ?: edit(SetupStepId.NUTRITION_RESULT)
        val carbsEdit = edit(SetupStepId.NUTRITION_MANUAL_CARBS_FAT) ?: edit(SetupStepId.NUTRITION_RESULT)
        SetupDataLine(label = "Base diaria (media)", value = "${plan.calorieTarget} kcal", onEdit = caloriesEdit)
        SetupDataLine(label = "Proteína (media)", value = "${plan.proteinGoal} g", onEdit = caloriesEdit)
        SetupDataLine(label = "Hidratos (media)", value = "${plan.carbGoal} g", onEdit = carbsEdit)
        SetupDataLine(label = "Grasas (media)", value = "${plan.fatGoal} g", onEdit = carbsEdit)
    }
    val distribution = preparation?.distributionStatus
    if (preparation != null && preparation.days.isNotEmpty()) {
        SetupDataLine(
            label = "Reparto",
            value = "${preparation.days.size} días con objetivo",
            onEdit = edit(SetupStepId.NUTRITION_DISTRIBUTION),
        )
    }
    if (distribution != null) {
        SetupDataLine(label = "Distribución", value = distributionLabel(distribution))
    }
}

private fun preparationStatusLabel(status: NutritionPlanPreparationStatus): String = when (status) {
    NutritionPlanPreparationStatus.READY -> "Lista para activar"
    NutritionPlanPreparationStatus.BLOCKED_EQUATION -> "Faltan datos de la ecuación"
    NutritionPlanPreparationStatus.SELF_DEFINED_MANUAL -> "Revisa tus valores"
    NutritionPlanPreparationStatus.TRACKING_ONLY -> "Solo registro"
}

private fun distributionLabel(status: NutritionDistributionStatus): String = when (status) {
    NutritionDistributionStatus.VARIABLE -> "Variable según el gasto previsto"
    NutritionDistributionStatus.UNIFORM_BY_CHOICE -> "Uniforme por elección"
    NutritionDistributionStatus.UNIFORM_BY_LIMITS -> "Uniforme por límites"
    NutritionDistributionStatus.PROVISIONAL_UNIFORM -> "Uniforme provisional (gasto sin estimar)"
    NutritionDistributionStatus.KEPT_PREVIOUS -> "Reparto anterior conservado"
}

// ─── Resumen: RINGS ──────────────────────────────────────────────────────────

/**
 * Cobertura de RINGS desde `state.ringsCoveragePreview`: es **por canal**, así
 * que una sola sensación conocida no da tres canales «conocidos». Sin
 * cobertura → [RingsCoverage.NO_DATA] (score null) y «Sin datos»: nunca un
 * 100 % derivado de un global.
 */
@Composable
private fun RingsSummary(
    state: SetupWizardState,
    edit: (SetupStepId) -> (() -> Unit)?,
) {
    val coverage = state.ringsCoveragePreview ?: RingsCoverage.NO_DATA
    SetupDataLine(
        label = "Muscular",
        value = coverage.muscular.score?.let { "$it%" },
        missing = "Sin datos",
        onEdit = edit(SetupStepId.RINGS_MUSCLE_FEELING),
    )
    SetupDataLine(
        label = "Sistema",
        value = coverage.system.score?.let { "$it%" },
        missing = "Sin datos",
        onEdit = edit(SetupStepId.RINGS_ENERGY_FEELING),
    )
    SetupDataLine(
        label = "Estructura",
        value = coverage.structure.score?.let { "$it%" },
        missing = "Sin datos",
        onEdit = edit(SetupStepId.RINGS_STRUCTURE_FEELING),
    )
    val channels = listOf(coverage.muscular, coverage.system, coverage.structure)
    val sources = channels.filter { it.hasData }.map { it.label }.distinct()
    SetupFormCaption(
        when {
            sources.isNotEmpty() -> "Procedencia por canal: ${sources.joinToString(" · ")}."
            state.ringsPreviewLoading -> "Preparando la vista previa de RINGS…"
            else -> "Sin datos de RINGS todavía: no se afirma ningún porcentaje."
        },
    )
}

// ─── Tarjetas ────────────────────────────────────────────────────────────────

/**
 * Tarjeta de bloque resumen. El contenedor **no** es clicable: la acción de
 * edición vive en la cabecera y en las filas concretas, para que ningún click
 * de padre absorba al hijo.
 */
@Composable
private fun ReviewBlockCard(
    title: String,
    onEdit: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(
                width = WizardColors.unselectedBorderWidth,
                color = WizardColors.cardBorder,
                shape = WizardShapes.card,
            )
            .clip(WizardShapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = WizardTypography.cardTitle,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            if (onEdit != null) {
                Text(
                    text = "Editar bloque ›",
                    style = WizardTypography.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier
                        .clip(WizardShapes.pill)
                        .clickable(onClick = onEdit)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Editar $title"
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        content()
    }
}

/** Confirmación explícita del alta (borde blanco cuando queda confirmada). */
@Composable
private fun SetupConfirmationCard(
    title: String,
    body: String,
    confirmed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(
                width = if (confirmed) WizardColors.selectedBorderWidth else WizardColors.unselectedBorderWidth,
                color = if (confirmed) WizardColors.selectedBorder else WizardColors.cardBorder,
                shape = WizardShapes.card,
            )
            .semantics {
                role = Role.Checkbox
                selected = confirmed
                contentDescription = title
            }
            .clip(WizardShapes.card)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(text = title, style = WizardTypography.cardTitle, color = WizardColors.text)
            Text(text = body, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
        }
        WizardRadioMark(selected = confirmed)
    }
}

/**
 * Línea etiqueta/valor. Con [onEdit] la fila es su propio destino de edición
 * (`Role.Button` + chevrón); sin ella es texto plano y no finge ser accionable.
 */
@Composable
private fun SetupDataLine(
    label: String,
    value: String?,
    missing: String = "Sin declarar",
    onEdit: (() -> Unit)? = null,
) {
    val rowModifier = if (onEdit == null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .clip(WizardShapes.pill)
            .clickable(onClick = onEdit)
            .semantics {
                role = Role.Button
                contentDescription = "Editar $label"
            }
    }
    Row(
        modifier = rowModifier.padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = WizardTypography.cardSubtitle,
            color = WizardColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value ?: missing,
            style = WizardTypography.cardSubtitle,
            color = if (value == null) WizardColors.textFaint else WizardColors.text,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (onEdit != null) {
            Text(text = "›", style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
        }
    }
}

/**
 * Confirma que la receta fija se aparta de lo declarado (días o minutos). Es la
 * misma condición que valida el ViewModel para `acceptFixedRecipeDifference`.
 */
private fun fixedRecipeDifference(state: SetupWizardState): Boolean {
    val draft = state.draft
    val days = state.fixedTrainingDays
    val minutes = state.fixedSessionEstimateMinutes
    val daysDiffer = days != null && days != draft.selectedWeekdays
    val timeExceeds = minutes != null && minutes > (draft.minutesPerSession ?: 100)
    return daysDiffer || timeExceeds
}

private fun formatPercent(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value).replace('.', ',')
