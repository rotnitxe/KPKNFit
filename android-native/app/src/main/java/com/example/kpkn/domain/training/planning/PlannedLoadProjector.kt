package com.example.kpkn.domain.training.planning

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.requiresNativeWeekInstances

data class PlannedMuscleLoad(val muscleName: String, val plannedSets: Double, val highEffortTargetSets: Double)
data class PlannedLoadProjection(val weekId: String, val loads: List<PlannedMuscleLoad>, val notes: List<String>,
                                val weekName: String = "",val cycleNumber: Int? = null,val nextCycleTemplate: Boolean = false)

/** Describe la dosis prescrita; no estima recuperación ni altera el entrenamiento. */
object PlannedLoadProjector {
    const val HIGH_PERCENT_1RM = 85.0

    fun next(program: Program,catalog: List<ExerciseMuscleInfo>): PlannedLoadProjection? {
        val run = program.runState ?: return null
        if (run.status != ProgramRunStatus.ACTIVE) return null
        val hierarchy = ProgramHierarchyIndex(program)
        if (hierarchy.duplicateIds.isNotEmpty()) return null
        val identities = listOfNotNull(run.weekId,run.weekInstanceId).map { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it }.distinct()
        val current = identities.singleOrNull() ?: return null
        val ordered = hierarchy.orderedWeeks()
        val index = ordered.indexOfFirst { it.week.id == current }
        if (index < 0) return null
        val repeating = program.requiresNativeWeekInstances() || program.isSimpleProgram && !program.isSimpleLinearProgram && !program.isSimpleCalendarizedProgram
        val wrap = index == ordered.lastIndex
        if (wrap && !repeating) return null
        val next = ordered[if (wrap) 0 else index + 1].week
        val projection = project(next,catalog)
        return projection.copy(cycleNumber = run.cycleNumber + if (wrap) 1 else 0,nextCycleTemplate = wrap,
            notes = projection.notes + if (wrap) listOf("Plantilla del ciclo siguiente; las propuestas del cierre pueden cambiarla.") else emptyList())
    }

    fun project(week: ProgramWeek, catalog: List<ExerciseMuscleInfo>): PlannedLoadProjection {
        val index = catalog.associateBy { it.id }
        val planned = VolumeCalculator.calculateVolume(week.sessions,index).muscles
        fun highTargets(exercise: Exercise) = exercise.copy(sets = exercise.sets.filter { set ->
            !set.isIneffective && (set.targetRIR?.let { it in 0..1 } == true ||
                set.targetRPE?.let { it.isFinite() && it in 9.0..10.0 } == true ||
                (set.loadBasis == LoadBasis.PERCENT_1RM &&
                    set.targetPercentageRM?.let { it.isFinite() && it >= HIGH_PERCENT_1RM } == true))
        })
        val filtered = week.sessions.map { session -> session.copy(
            exercises = session.exercises.map(::highTargets).filter { it.sets.isNotEmpty() },
            parts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::highTargets).filter { it.sets.isNotEmpty() }) },
        ) }
        val high = VolumeCalculator.calculateVolume(filtered,index).muscles
        val loads = planned.values.map { muscle -> PlannedMuscleLoad(muscle.muscleName,muscle.sets,
            high[muscle.muscleName]?.sets ?: 0.0) }.sortedByDescending { it.plannedSets }
        return PlannedLoadProjection(week.id,loads,listOf(
            "Cuenta la semana prescrita, incluidas las sesiones opcionales; no confirma lo realizado.",
            "Objetivo exigente: RIR 0–1, RPE 9–10 o al menos 85 % de una referencia explícita de 1RM. Un porcentaje de TM no equivale a un porcentaje de 1RM.",
            "Las series conservan la misma contabilidad muscular que el volumen del programa.",
        ),weekName = week.name)
    }
}
