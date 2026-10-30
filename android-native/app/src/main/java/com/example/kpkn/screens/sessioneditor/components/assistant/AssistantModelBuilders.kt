package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateEquipmentBias
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.sessionassistant.AssistantActionParameters
import com.example.kpkn.domain.sessionassistant.AssistantLimitStatus
import com.example.kpkn.domain.sessionassistant.AssistantMuscleBreakdown
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionPreview
import com.example.kpkn.domain.sessionassistant.SessionLimitStatuses
import com.example.kpkn.domain.sessionassistant.SessionTotals
import com.example.kpkn.domain.sessionassistant.TemplatePreviewBuilder
import com.example.kpkn.domain.templates.SessionTemplateFacets
import com.example.kpkn.domain.templates.TemplateCatalogFilterLogic
import com.example.kpkn.domain.templates.TemplateSessionType
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.sessioneditor.AssistantSuggestionOutcome
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.TemplateApplyProgress
import com.example.kpkn.screens.sessioneditor.SessionIntensityTier
import com.example.kpkn.screens.sessioneditor.averageSessionIntensityTier
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.data.models.MuscleRole
import kotlin.math.max

/*
 * Del estado del editor a lo que pintan las pestañas del asistente. Todo función pura sobre datos (el catálogo de ejercicios llega
 * como parámetro): así las mismas pestañas se dibujan con datos de ejemplo en la hoja de render.
 */

private val TypeChips = listOf(
    TemplateSessionType.PIERNAS, TemplateSessionType.TORSO, TemplateSessionType.BRAZOS, TemplateSessionType.FULL_BODY,
    TemplateSessionType.PECHO, TemplateSessionType.ESPALDA, TemplateSessionType.HOMBROS, TemplateSessionType.GLUTEOS,
)

private val TimeColors = listOf(Color(0xFF5F5C57), Color(0xFFF2EEE6), Color(0xFF8E8980), Color(0xFF3A3936))

// ── Métricas ─────────────────────────────────────────────────────────────────────────────────────────────────────

internal fun buildMetricsModel(uiState: SessionEditorUiState, exerciseIndex: Map<String, ExerciseMuscleInfo>): AsMetricsModel {
    val session = uiState.activeVariantSession ?: uiState.session
    val exercises = session?.allExercises().orEmpty().distinctBy { it.id }
    val strength = exercises.filter { it.cardioDetails == null }
    val minutes = uiState.sessionTimeBreakdown?.totalMinutes ?: uiState.estimatedDurationMinutes
    val rows = session?.let { AssistantMuscleBreakdown.rows(it, exerciseIndex) }.orEmpty()
    val intensities = session?.let { intensityByMuscle(it, exerciseIndex) }.orEmpty()
    val week = session?.let { s ->
        val sessions = if (uiState.weekSessions.any { it.id == s.id }) uiState.weekSessions.map { if (it.id == s.id) s else it } else uiState.weekSessions + s
        AssistantMuscleBreakdown.weekly(sessions, exerciseIndex)
    }.orEmpty()
    val worked = rows.map { it.muscle }.toSet()
    val cardios = exercises.filter { it.cardioDetails != null }.map { ex ->
        AsCardioCard(
            name = ex.name,
            position = if (session?.cardioFirst == true) "Cardio · al inicio de la sesión" else "Cardio · al final de la sesión",
            minutes = ((ex.cardioDetails?.targetDurationSeconds ?: 0) / 60.0).let { Math.round(it).toInt() },
        )
    }
    val breakdown = uiState.sessionTimeBreakdown
    val time = if (breakdown == null) emptyList() else {
        val setup = breakdown.setupMinutes.coerceAtLeast(0)
        val rest = breakdown.restMinutes.coerceAtLeast(0)
        val warmup = breakdown.warmupMinutes.coerceAtLeast(0)
        val execution = (breakdown.totalMinutes - setup - rest - warmup).coerceAtLeast(0)
        buildList {
            add(AsTimeSegment("Preparación", setup, TimeColors[0]))
            add(AsTimeSegment("Ejecución", execution, TimeColors[1]))
            add(AsTimeSegment("Descansos", rest, TimeColors[2]))
            if (warmup > 0) add(AsTimeSegment("Calentamiento", warmup, TimeColors[3]))
        }
    }
    return AsMetricsModel(
        exercises = exercises.size,
        sets = strength.sumOf { it.sets.size },
        minutes = minutes,
        cardioCount = cardios.size,
        cardios = cardios,
        muscles = rows.map { row ->
            AsMuscle(
                name = row.muscle,
                value = row.volume,
                intensity = intensities[row.muscle]?.label?.lowercase(),
                contributions = row.lines.map { AsContribution(it.exerciseName, it.sets, it.weight) },
                weekSets = week[row.muscle]?.let { (it * 10).toInt() / 10.0 },
            )
        },
        unworked = VolumeCalculator.standardVolumeMuscles.filter { it !in worked },
        sessionLimit = uiState.ruleLimits.maxVolumePerMuscleSession,
        weeklyLimit = uiState.ruleLimits.maxVolumePerMuscleWeekly,
        timeLimit = session?.targetDurationMinutes,
        time = time,
    )
}

/** Intensidad media por músculo: solo con los ejercicios que lo trabajan como músculo principal (la cuenta de `buildMuscleVolumeRows`). */
internal fun intensityByMuscle(session: Session, exerciseIndex: Map<String, ExerciseMuscleInfo>): Map<String, SessionIntensityTier> {
    val sets = mutableMapOf<String, MutableList<com.example.kpkn.data.models.ExerciseSet>>()
    session.allExercises().forEach { exercise ->
        if (VolumeCalculator.countEffectiveSets(exercise.sets) <= 0) return@forEach
        ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)
            .filter { it.role == MuscleRole.PRIMARY }
            .map { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
            .distinct()
            .forEach { muscle -> sets.getOrPut(muscle) { mutableListOf() }.addAll(exercise.sets.filterNot { it.isIneffective || it.isEmptySlot }) }
    }
    return sets.mapValues { averageSessionIntensityTier(it.value) }
}

// ── Sugerencias ──────────────────────────────────────────────────────────────────────────────────────────────────

internal fun buildSuggestionsModel(uiState: SessionEditorUiState, limits: SessionLimitStatuses?): AsSuggestionsModel {
    val report = uiState.assistantReport
    val session = uiState.activeVariantSession ?: uiState.session
    val stamp = session?.lastModifiedAtMs
    val outcomes = uiState.assistantOutcomes.filter { it.stamp == stamp && it.variant == uiState.activeVariant }
    val entries = report?.let { r ->
        val live = r.ajustes.take(3).filter { s -> outcomes.none { it.suggestionId == s.id } }.map<AssistantSuggestion, AsSuggestionEntry> { AsSuggestionEntry.Card(it) }.toMutableList()
        outcomes.sortedBy { it.position }.forEach { outcome ->
            val folded = AsSuggestionEntry.Folded(AsOutcomeRow(outcome.suggestionId, applied = outcome.kind == AssistantSuggestionOutcome.Kind.APPLIED, summary = outcome.summary))
            live.add(outcome.position.coerceIn(0, live.size), folded)
        }
        live.toList()
    }
    val estimated = uiState.sessionTimeBreakdown?.totalMinutes ?: uiState.estimatedDurationMinutes
    val limit = session?.targetDurationMinutes
    return AsSuggestionsModel(
        entries = entries,
        notes = report?.notas.orEmpty().take(max(0, 3 - (entries?.count { it is AsSuggestionEntry.Card } ?: 0))),
        timeReview = AsReviewLine(
            "Tu tiempo",
            if (limit == null) "Sin límite de tiempo" else "$estimated min estimados de $limit · ${if (estimated <= limit) "dentro" else "por encima"}",
        ),
        limitsReview = AsReviewLine("Tus límites rígidos", if (uiState.ruleLimits.rigidLimits) "Activados" else "Ninguno activado"),
    )
}

/** Cuántas tarjetas vivas hay (lo que cuenta la insignia de la pestaña). */
internal fun liveSuggestionCount(model: AsSuggestionsModel): Int = model.entries?.count { it is AsSuggestionEntry.Card } ?: 0

/** La caja de efecto en vivo de una sugerencia (tiempo o límite de series); null si la sugerencia no la tiene. */
internal fun buildFx(
    uiState: SessionEditorUiState,
    suggestion: AssistantSuggestion,
    accepted: List<AssistantSuggestionDetail>,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): AsFx? {
    val session = uiState.activeVariantSession ?: uiState.session ?: return null
    return when (suggestion.id) {
        "time_overage" -> {
            val limit = session.targetDurationMinutes ?: return null
            val base = uiState.assistantReport?.duracionEstimada ?: uiState.estimatedDurationMinutes
            val preview = AssistantSuggestionPreview.duration(base, limit, accepted)
            val scale = max(base, limit) * 1.06f
            AsFx("Duración", null, "$base", "${preview.afterMinutes}", "min", preview.afterMinutes / scale, limit / scale, preview.text, preview.fits)
        }
        "user_limits" -> {
            val limit = uiState.ruleLimits.maxVolumePerMuscleSession ?: return null
            val params = AssistantActionParameters(
                exerciseIndex = exerciseIndex,
                supersetBetweenRestSeconds = uiState.ruleDefaults.supersetBetweenRestSeconds,
                supersetRoundRestSeconds = uiState.ruleDefaults.supersetRoundRestSeconds,
            )
            val rows = AssistantMuscleBreakdown.rows(session, exerciseIndex)
            val preview = AssistantSuggestionPreview.muscleLimit(session, rows, limit, accepted, params, exerciseIndex) ?: return null
            val scale = (max(preview.before, limit) * 1.06).toFloat()
            AsFx(
                preview.muscle, "límite ${AssistantSuggestionPreview.number(limit)}",
                AssistantSuggestionPreview.number(preview.before), AssistantSuggestionPreview.number(preview.after), "",
                (preview.after / scale).toFloat(), (limit / scale).toFloat(), preview.text, preview.fits,
            )
        }
        else -> null
    }
}

// ── Reglas · límites ─────────────────────────────────────────────────────────────────────────────────────────────

internal fun buildLimitStatuses(uiState: SessionEditorUiState, exerciseIndex: Map<String, ExerciseMuscleInfo>): SessionLimitStatuses? {
    val session = uiState.activeVariantSession ?: uiState.session ?: return null
    val l = uiState.ruleLimits
    return AssistantLimitStatus.compute(
        session = session,
        weekSessions = uiState.weekSessions,
        maxRpe = l.maxRPE,
        maxExercisesPerMuscle = l.maxExercisesPerMuscle,
        maxVolumePerMuscleSession = l.maxVolumePerMuscleSession,
        maxVolumePerMuscleWeekly = l.maxVolumePerMuscleWeekly,
        maxSamePattern = l.maxSamePatternPerSession,
        exerciseIndex = exerciseIndex,
    )
}

/** El aviso de Límites: el músculo que pasa del máximo de series por sesión. */
internal fun buildClashBanner(uiState: SessionEditorUiState, exerciseIndex: Map<String, ExerciseMuscleInfo>): AsClashBanner? {
    val session = uiState.activeVariantSession ?: uiState.session ?: return null
    val limit = uiState.ruleLimits.maxVolumePerMuscleSession ?: return null
    val top = AssistantMuscleBreakdown.rows(session, exerciseIndex).firstOrNull { it.volume > limit } ?: return null
    val num = AssistantSuggestionPreview::number
    return AsClashBanner(
        strong = "${top.muscle} tiene ${num(top.volume)} series",
        rest = "y tu máximo es ${num(limit)}. " + if (uiState.ruleLimits.rigidLimits) "Con límites rígidos no podrás guardar la sesión así." else "Te avisaremos al guardar.",
    )
}

// ── Plantillas ───────────────────────────────────────────────────────────────────────────────────────────────────

internal fun levelOf(difficulty: Difficulty): Int = when (difficulty) {
    Difficulty.PRINCIPIANTE -> 0
    Difficulty.INTERMEDIO -> 1
    Difficulty.AVANZADO -> 2
}

internal fun difficultyOf(level: Int): Difficulty = when (level) {
    0 -> Difficulty.PRINCIPIANTE
    2 -> Difficulty.AVANZADO
    else -> Difficulty.INTERMEDIO
}

private val CoverStyles = listOf(
    LineCoverStyle.RELIEVE, LineCoverStyle.PERFIL, LineCoverStyle.ESPIRAL, LineCoverStyle.ONDAS,
    LineCoverStyle.CAMPO, LineCoverStyle.ORBITAS, LineCoverStyle.BRUMA,
)

/** La portada de una plantilla: estilo y semilla estables por id, fondo por grupo (decisión del paquete: el catálogo no trae portada). */
internal fun coverFor(template: SessionTemplate, group: String): LineCoverSpec {
    val seed = template.id.hashCode() and 0x7fffffff
    val background = when (group) {
        "Pierna" -> if (seed % 2 == 0) LineCoverBackground.CARBON else LineCoverBackground.MUSGO
        "Torso" -> LineCoverBackground.NOCHE
        "Brazo" -> LineCoverBackground.GRAFITO
        else -> if (seed % 2 == 0) LineCoverBackground.MUSGO else LineCoverBackground.GRAFITO
    }
    return LineCoverSpec(style = CoverStyles[seed % CoverStyles.size], background = background, seed = seed % 19 + 1)
}

private fun equipmentLine(template: SessionTemplate, facets: SessionTemplateFacets?): String {
    val fromFacets = facets?.equipment?.sorted()?.take(3)?.joinToString(" · ").orEmpty()
    return fromFacets.ifBlank {
        when (template.equipmentBias) {
            SessionTemplateEquipmentBias.MACHINE -> "Máquinas y poleas comunes"
            SessionTemplateEquipmentBias.FREE -> "Pesos libres y barra"
            SessionTemplateEquipmentBias.MIXED -> "Máquinas y pesos libres"
        }
    }
}

/** Las tarjetas del catálogo (solo plantillas de la app; las propias viven en «Mis plantillas»). */
internal fun buildTemplateCards(templates: List<SessionTemplate>, facetsById: Map<String, SessionTemplateFacets>): List<AsTemplateCard> {
    val system = templates.filter { it.sourceType != SessionTemplateSourceType.USER }
    val visible = TemplateCatalogFilterLogic.filterTemplates(system, facetsById, com.example.kpkn.domain.templates.TemplateCatalogFilters())
    val groupOrder = listOf("Pierna", "Torso", "Brazo", "Full body", "Otros")
    val sorted = visible.sortedWith(compareBy({ groupOrder.indexOf(TemplateCatalogFilterLogic.dominantGroup(it.focusCategory).label) }, { it.sortOrder }))
    val rawLines = sorted.associate { it.id to it.shortDescription.ifBlank { it.description.take(120) }.ifBlank { it.muscleGroupsSummary } }
    val repeated = rawLines.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    return sorted.map { template ->
        val facets = facetsById[template.id]
        val group = TemplateCatalogFilterLogic.dominantGroup(template.focusCategory).label
        val line = rawLines.getValue(template.id).let { if (it in repeated || it.isBlank()) equipmentLine(template, facets) else it }
        val muscles = facets?.primaryMuscles?.sortedBy { it.lowercase() }?.take(5)?.joinToString(" · ").orEmpty().ifBlank { template.muscleGroupsSummary }
        val routine = template.splitIds.firstNotNullOfOrNull { id -> SPLIT_TEMPLATES.firstOrNull { it.id == id }?.name }
            ?: template.focusCategory?.let(TemplateCatalogFilterLogic::focusCategoryLabel) ?: group
        AsTemplateCard(
            id = template.id,
            name = template.name,
            line = line,
            minutes = facets?.realDurationMinutes ?: template.estimatedDurationMinutes,
            exercises = template.session.allExercises().size,
            sets = facets?.totalSets ?: template.session.allExercises().sumOf { it.sets.size },
            level = levelOf(template.difficulty),
            muscles = muscles,
            types = TypeChips.filter { TemplateCatalogFilterLogic.matchesSessionType(template, it) }.map { it.label }.toSet(),
            group = group,
            routine = routine,
            searchText = listOf(template.name, line, muscles, template.description, template.muscleGroupsSummary).joinToString(" ").lowercase(),
            cover = coverFor(template, group),
        )
    }
}

internal fun buildTemplateDetail(
    template: SessionTemplate,
    card: AsTemplateCard?,
    facets: SessionTemplateFacets?,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): AsTemplateDetail {
    val group = TemplateCatalogFilterLogic.dominantGroup(template.focusCategory).label
    val totals = TemplatePreviewBuilder.totals(template.session)
    val base = card ?: AsTemplateCard(
        id = template.id, name = template.name, line = template.shortDescription, minutes = facets?.realDurationMinutes ?: template.estimatedDurationMinutes ?: totals.minutes,
        exercises = totals.exercises, sets = totals.sets, level = levelOf(template.difficulty), muscles = template.muscleGroupsSummary,
        types = emptySet(), group = group, routine = group, searchText = "", cover = coverFor(template, group),
    )
    val volume = AssistantMuscleBreakdown.rows(template.session, exerciseIndex).filter { it.volume >= 0.1 }.take(10).map { it.muscle to it.volume }
    return AsTemplateDetail(
        card = base,
        description = template.description.ifBlank { template.shortDescription },
        parts = TemplatePreviewBuilder.parts(template).map { part ->
            AsPart(part.title, part.exercises.map { AsPartLine(it.name, it.sets, it.reps, it.rpe, it.restSeconds) })
        },
        volume = volume,
    )
}

internal fun buildMineRows(user: List<SessionTemplate>, archived: List<SessionTemplate>): List<AsMineRow> =
    (user.filter { it.sourceType == SessionTemplateSourceType.USER && !it.isArchived }.map { it to false } + archived.map { it to true })
        .map { (t, isArchived) ->
            AsMineRow(id = t.id, name = t.name, description = t.description, level = levelOf(t.difficulty), exercises = t.session.allExercises().size, archived = isArchived)
        }

internal fun buildDecisionModel(decision: SessionTemplateApplyDecision, session: Session?, targetMinutes: Int?): AsDecisionModel {
    val current = session?.let(TemplatePreviewBuilder::totals) ?: SessionTotals(0, 0, 0)
    val outcomes = TemplatePreviewBuilder.outcomes(current, TemplatePreviewBuilder.totals(decision.template.session), targetMinutes)
    return AsDecisionModel(decision.template.name, outcomes.replace, outcomes.append, outcomes.appendOverMinutes, decision.suggestedMode)
}

internal fun buildApplyModel(progress: TemplateApplyProgress): AsApplyModel = when (progress) {
    is TemplateApplyProgress.Applying -> AsApplyModel(
        title = "Aplicando «${progress.templateName}»…",
        body = if (progress.mode == SessionTemplateApplyMode.APPEND) "Sumando ${progress.templateExercises} ejercicios a tu sesión."
        else "Cambiando ${progress.currentExercises} ejercicios por los de la plantilla.",
        done = false,
    )
    is TemplateApplyProgress.Done -> AsApplyModel(
        title = "Plantilla aplicada",
        body = if (progress.mode == SessionTemplateApplyMode.APPEND) {
            "Añadiste ${progress.now.exercises - progress.previous.exercises} ejercicios: ahora son ${progress.now.exercises}, ${progress.now.sets} series, ≈${progress.now.minutes} min."
        } else {
            "Reemplazaste ${progress.previous.exercises} ejercicios por ${progress.now.exercises}: ${progress.now.sets} series, ≈${progress.now.minutes} min."
        },
        done = true,
        note = progress.note,
    )
}
