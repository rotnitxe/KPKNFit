package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.training.generator.GeneratorCatalog
import com.example.kpkn.domain.training.quality.SessionSlopRules

enum class SwapScope { THIS_SESSION, REST_OF_BLOCK, WHOLE_PROGRAM }

/**
 * Cambio manual explícito; las referencias de carga siguen perteneciendo a su configuración original.
 * DUP-03: una sesión afectada que ya tiene el destino (misma configuración, definición o familia de movimiento,
 * [SessionSlopRules.familyOf]) conserva su ejercicio; la decisión cuenta esas sesiones.
 */
fun applyExerciseSwap(
    program: Program,
    sessionId: String,
    exerciseId: String,
    target: SwapOption,
    scope: SwapScope,
    catalog: ExerciseCatalogV2,
    executedSessionIds: Set<String> = emptySet(),
    nowMs: Long = 0L,
): Program {
    val hierarchy = ProgramHierarchyIndex(program)
    if (hierarchy.duplicateIds.isNotEmpty()) return program
    val locations = hierarchy.orderedWeeks()
    fun nested(session: Session): List<Session> = listOf(session) +
        listOfNotNull(session.sessionB,session.sessionC,session.sessionD).flatMap(::nested)
    val sourceLocations = locations.filter { location -> location.week.sessions.flatMap(::nested).any { it.id == sessionId } }
    val sourceLocation = sourceLocations.singleOrNull() ?: return program
    val sourceSession = sourceLocation.week.sessions.flatMap(::nested).filter { it.id == sessionId }.singleOrNull() ?: return program
    val source = sourceSession.allExercises().filter { it.id == exerciseId }.singleOrNull() ?: return program
    val sourceConfiguration = source.catalogConfigurationId?.takeIf { it.isNotBlank() } ?: return program
    if (sourceConfiguration == target.configId) return program
    val entry = GeneratorCatalog.of(catalog).entry(target.configId) ?: return program
    val info = entry.legacy
    val convention = NativeLoadConventions.forEquipment(entry.equipmentId)
    val currentWeek = program.runState?.weekId?.let { hierarchy.locateWeek(ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it) }
    val firstWeek = maxOf(sourceLocation.globalWeekIndex,currentWeek?.globalWeekIndex ?: sourceLocation.globalWeekIndex)
    val changedRoots = linkedMapOf<String,ProgramHierarchyLocation>()
    var changedExercises = 0
    var skippedSessions = 0
    val targetDefinition = info.catalogDefinitionId
    val targetFamily = SessionSlopRules.familyOf(target.configId)
    /** El destino ya está en la sesión (en otro ejercicio distinto del que se cambia). */
    fun alreadyHasTarget(session: Session): Boolean = session.allExercises().any { other ->
        val otherConfig = other.catalogConfigurationId
        otherConfig != sourceConfiguration && otherConfig != null && (
            otherConfig == target.configId ||
                (targetDefinition != null && other.catalogDefinitionId == targetDefinition) ||
                (targetFamily != null && SessionSlopRules.familyOf(otherConfig) == targetFamily)
            )
    }

    fun replace(exercise: Exercise): Exercise {
        if (exercise.catalogConfigurationId != sourceConfiguration) return exercise
        changedExercises++
        return exercise.copy(name = info.name,exerciseDbId = entry.id,exerciseId = entry.id,canonicalExerciseId = entry.id,
            exerciseFamilyId = entry.familyId,catalogRevision = info.catalogRevision,catalogDefinitionId = info.catalogDefinitionId,
            catalogConfigurationId = entry.id,performanceProfileId = info.performanceProfileId,effectiveMuscles = info.involvedMuscles,
            variantName = info.catalogVariantChips.joinToString(" · "),reference1RM = null,referenceTrainingMax = null,prFor1RM = null,
            techniqueModifier = null,relationshipType = null,relativeToCanonicalExerciseId = null,relationshipNotes = null,
            nativeProgressionManaged = false,apreBlock = null,
            loadReference = null,loadQuantityConvention = convention,setupDetails = null,selectedAspects = null,
            contextProfilesV3 = emptyList(),defaultContextProfileIdV3 = null,warmupSets = emptyList(),
            sets = exercise.sets.map { set -> set.copy(weight = null,loadQuantityConvention = convention,
                loadModeV2 = null,unitModeV2 = null,plannedTargetV2 = null,leftTarget = null,rightTarget = null,
                contextKeyV2 = null,contextProfileIdV3 = null,defaultTagIdV3 = null,defaultSetupProfileIdV3 = null,tagId = null,setupId = null) })
    }
    fun change(session: Session): Session {
        if (session.id in executedSessionIds) return session
        var changeOwn = scope != SwapScope.THIS_SESSION || session.id == sessionId
        if (changeOwn && session.allExercises().any { it.catalogConfigurationId == sourceConfiguration } && alreadyHasTarget(session)) {
            skippedSessions++
            changeOwn = false
        }
        return session.copy(
            exercises = if (changeOwn) session.exercises.map(::replace) else session.exercises,
            parts = if (changeOwn) session.parts.map { it.copy(exercises = it.exercises.map(::replace)) } else session.parts,
            sessionB = session.sessionB?.let(::change),sessionC = session.sessionC?.let(::change),sessionD = session.sessionD?.let(::change))
    }
    val result = program.copy(macrocycles = program.macrocycles.map { macro -> macro.copy(blocks = macro.blocks.map { block ->
        block.copy(mesocycles = block.mesocycles.map { meso -> meso.copy(weeks = meso.weeks.map weekLoop@{ week ->
            val location = locations.first { it.week.id == week.id }
            if (scope == SwapScope.REST_OF_BLOCK && (location.blockId != sourceLocation.blockId || location.globalWeekIndex < firstWeek)) return@weekLoop week
            week.copy(sessions = week.sessions.map { root ->
                if (scope == SwapScope.THIS_SESSION && nested(root).none { it.id == sessionId }) root else {
                    val changed = change(root)
                    if (changed != root) changedRoots[root.id] = location
                    changed
                }
            })
        }) })
    }) })
    if (changedExercises == 0) return program
    var marked = result
    changedRoots.forEach { (rootId,location) ->
        val recipeSource = program.sourceRecipe?.let { PlanMaterializer.weekRecipeSourceFor(program,it,location.week.id) }
        marked = PlanMaterializer.withManualSessionOverride(marked,rootId,location.week.id,recipeSource?.weekOccurrence,
            location.week.sessions.firstOrNull { it.id == rootId }?.allExercises()?.firstOrNull()?.recipeDayId,
            reason = "Cambio de ejercicio",nowMs = nowMs)
    }
    return marked.copy(programDecisions = marked.programDecisions + ProgramDecision(
        "manual-swap:${program.runState?.runId}:$nowMs:$sessionId:$exerciseId:${target.configId}:$scope",nowMs,"MANUAL","Ejercicio cambiado",
        "${source.name} → ${info.name} en ${changedRoots.size} ${if (changedRoots.size == 1) "sesión" else "sesiones"} " +
            "($changedExercises ${if (changedExercises == 1) "ejercicio" else "ejercicios"})" +
            (if (skippedSessions > 0) {
                "; $skippedSessions ${if (skippedSessions == 1) "sesión conserva" else "sesiones conservan"} el ejercicio porque ya tenía${if (skippedSessions == 1) "" else "n"} «${info.name}»"
            } else "") +
            ". Repeticiones, RIR y descansos conservados; carga pendiente para la configuración nueva.",
        weekId = sourceLocation.week.id,blockId = sourceLocation.blockId))
}
