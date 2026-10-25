package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*

/** Pure forecast. The existing weekly close owns PROPOSE/AUTO and its durable program audit. */
internal data class LiveAmrapProgramPreview(
    val mode: AutoregulationMode,
    val hasRule: Boolean,
    val proposals: List<AutoregulationProposal> = emptyList(),
    val beforeTm: Double? = null,
    val afterTm: Double? = null,
) {
    val message: String get() = when {
        !hasRule -> "Sin regla de progresión: queda registrado"
        mode == AutoregulationMode.OFF -> "Autorregulación OFF: queda registrado"
        proposals.isEmpty() -> "La regla del programa no propone cambios de TM"
        mode == AutoregulationMode.AUTO -> "Confirmación requerida · TM propuesto ${afterTm?.let(::finishNumber) ?: "—"} kg al cerrar la semana"
        else -> "PROPONER · TM ${beforeTm?.let(::finishNumber) ?: "—"} → ${afterTm?.let(::finishNumber) ?: "—"} kg al cerrar la semana"
    }
}

internal fun liveAmrapProgramPreview(program: Program?, exercise: Exercise, set: ExerciseSet,
    completed: CompletedSet, weekId: String): LiveAmrapProgramPreview {
    val mode = program?.autoregulationMode ?: AutoregulationMode.OFF
    val recipe = program?.sourceRecipe ?: return LiveAmrapProgramPreview(mode, false)
    if (!ProgramAutoregulationEngine.amrapMovesTm(recipe)) return LiveAmrapProgramPreview(mode, false)
    val week = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
        .firstOrNull { it.id == weekId } ?: ProgramWeek(weekId, "Sesión en vivo")
    val session = Session("live-amrap-preview", "Vista previa", exercises = listOf(exercise.copy(sets = listOf(set))))
    val log = WorkoutLog("live-amrap-preview", program.id, session.id, session.name, "", 0,
        completedExercises = listOf(CompletedExercise(exercise.id, exercise.name, sets = listOf(completed.copy(setIndex = 0)),
            catalogConfigurationId = exercise.catalogConfigurationId, exerciseDbId = exercise.exerciseDbId)), weekId = week.id)
    val hit = ProgramAutoregulationEngine.collectAmrapHits(week.copy(sessions = listOf(session)), listOf(log), recipe = recipe)
        .singleOrNull() ?: return LiveAmrapProgramPreview(mode, true)
    val lift = hit.liftSlot
    val proposals = ProgramAutoregulationEngine.evaluate(program, week, emptyList(), recipe,
        WeeklyAutoregulationSignals(amrapHits = listOf(hit))).filter {
        it.kind == AutoregulationProposalKind.ADJUST_TM && it.liftSlot == lift?.name }
    val profile = program.powerliftingProfile
    val before = if (lift != null && profile != null) TrainingMaxResolver.trainingMax(profile, lift, recipe.trainingMaxPercent) else null
    val proposal = proposals.firstOrNull()
    val afterProfile = if (profile != null && proposal != null && lift != null) {
        proposal.kgDelta?.let { ProgramAutoregulationEngine.applyTmKgDelta(profile, lift, it, recipe.trainingMaxPercent) }
            ?: ProgramAutoregulationEngine.applyTmDelta(profile, lift, proposal.percentDelta ?: 0.0)
    } else profile
    val after = if (lift != null && afterProfile != null) TrainingMaxResolver.trainingMax(afterProfile, lift, recipe.trainingMaxPercent) else null
    return LiveAmrapProgramPreview(mode, true, proposals, before, after)
}
