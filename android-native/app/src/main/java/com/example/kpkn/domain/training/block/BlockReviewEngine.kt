package com.example.kpkn.domain.training.block

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/** Registros de una ejecución/ciclo/bloque. La cobertura no demuestra entrenamiento ausente. */
object BlockReviewEngine {
    const val IMPROVING_GAIN = 0.02
    const val IMPROVING_COVERAGE = 85
    const val LOW_COVERAGE = 60

    fun review(
        program: Program,
        blockId: String,
        logs: List<WorkoutLog>,
        context: BlockTransitionEngine.TransitionContext,
        catalog: List<ExerciseMuscleInfo> = emptyList(),
        nowMs: Long = 0L,
    ): BlockReview? {
        val hierarchy = ProgramHierarchyIndex(program)
        val locations = hierarchy.orderedWeeks().filter { it.blockId == blockId }
        if (locations.isEmpty() || hierarchy.duplicateIds.isNotEmpty()) return null
        val block = locations.first().block
        val weeks = locations.map { it.week }
        val run = program.runState
        val cycle = run?.cycleNumber ?: 1
        val eligible = logs.filter { log ->
            if (log.programId != program.id || !log.calendarBreakId.isNullOrBlank()) return@filter false
            val identities = listOfNotNull(log.weekId, log.weekInstanceId).filter { it.isNotBlank() }
            val templates = identities.map { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it }
            if (templates.distinct().size > 1) return@filter false
            val identityCycles = identities.mapNotNull(ProgramProgressEngine::cycleFromInstanceId).distinct()
            if ((identityCycles + listOfNotNull(log.cycleNumber)).distinct().size > 1) return@filter false
            val logCycle = log.cycleNumber ?: identityCycles.singleOrNull()
            val matchingRun = if (run == null) log.programRunId == null else
                log.programRunId == run.runId || (log.programRunId == null && cycle == 1)
            matchingRun && (logCycle == cycle || (logCycle == null && cycle == 1))
        }.groupBy { it.id }.values.mapNotNull { it.distinct().singleOrNull() }
        val byWeek = weeks.associate { week ->
            week.id to ProgramProgressEngine.logsForInstance(eligible, program.id,
                ProgramProgressEngine.instanceIdFor(cycle, week.id), cycle, run?.runId)
                .filter { log -> week.sessions.any { it.id == log.sessionId } }
        }
        // Un registro ambiguo entre dos semanas no acredita ninguna de las dos.
        val uniqueIds = byWeek.values.flatten().groupingBy { it.id }.eachCount().filterValues { it == 1 }.keys
        val blockLogs = byWeek.values.flatten().filter { it.id in uniqueIds }
        val planned = weeks.sumOf { week -> week.sessions.count { it.requirement == SessionRequirement.REQUIRED } }
        val recorded = weeks.sumOf { week ->
            val required = week.sessions.filter { it.requirement == SessionRequirement.REQUIRED }.map { it.id }.toSet()
            byWeek.getValue(week.id).filter { it.id in uniqueIds && it.sessionId in required }
                .map { it.sessionId }.distinct().size
        }
        val coverage = if (planned == 0) 0 else (recorded * 100.0 / planned).roundToInt()
        val exerciseIndex = catalog.associateBy { it.id }
        val plannedVolume = VolumeCalculator.calculateVolume(weeks.flatMap { it.sessions }, exerciseIndex,
            divisor = weeks.size.toDouble())
        // La selección por bloque ya se hizo por identidad, no se infieren sus fechas de los logs.
        val volumeWindow = TrainingWindow(LocalDate.MIN, LocalDate.MAX, ZoneOffset.UTC,
            TrainingWindowType.TOTAL, 1.0, locations.flatMap { location ->
                val scope = TrainingWeekScope(location.week.id, LocalDate.MIN, LocalDate.MAX,
                    instanceId = ProgramProgressEngine.instanceIdFor(cycle, location.week.id),
                    cycleNumber = cycle, runId = run?.runId, templateIsUnique = cycle == 1,
                    uniqueSessionIds = location.week.sessions.map { it.id }.toSet())
                listOfNotNull(scope, scope.copy(runId = null).takeIf { run != null && cycle == 1 })
            })
        val recordedVolume = VolumeCalculator.calculateCompletedVolume(blockLogs, exerciseIndex, volumeWindow,
            programId = program.id)
        val muscles = plannedVolume.muscles.values.sortedByDescending { it.sets }.take(8).map {
            MuscleSetsReview(it.muscleName, round1(it.sets),
                round1((recordedVolume.muscles[it.muscleName]?.sets ?: 0.0) / weeks.size))
        }
        val comparableLogs = blockLogs.map { log -> log.copy(completedExercises = log.completedExercises.mapNotNull { exercise ->
            val identity = exercise.performanceProfileId?.takeIf { it.isNotBlank() }
                ?: exercise.catalogConfigurationId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Evita que el fallback heredado agrupe variantes diferentes por definición o nombre.
            exercise.copy(performanceProfileId = "$identity:${exercise.catalogConfigurationId.orEmpty()}")
        }) }
        val trends = ProgramAnalyticsEngine.analyze(program, comparableLogs, catalog, exerciseIndex).progression.filter {
            it.firstE1rm?.let { value -> value.isFinite() && value > 0.0 } == true &&
                it.lastE1rm?.let { value -> value.isFinite() && value > 0.0 } == true && it.sparkline.size >= 2
        }
        val gain = trends.takeIf { it.isNotEmpty() }?.map { (it.lastE1rm!! - it.firstE1rm!!) / it.firstE1rm!! }?.average()
        val changes = trends.sortedByDescending { abs(it.deltaE1rm ?: 0.0) }.take(3)
            .map { E1rmChange(it.exerciseName, round1(it.firstE1rm!!), round1(it.lastE1rm!!)) }
        val weekIds = weeks.map { it.id }.toSet()
        val answers = program.weeklyCheckins.filter { answer ->
            !answer.skipped && answer.programRunId == run?.runId && answer.feedbackCycleNumber == cycle &&
                answer.feedbackWeekId in weekIds
        }.groupBy { it.feedbackWeekId }.values.mapNotNull { it.distinct().singleOrNull() }
        val difficult = answers.any { answer -> answer.globalPerformance == 4 || answer.globalSoreness == 4 ||
            answer.muscles.any { it.performance == 4 || it.soreness == 4 } }
        val status = when {
            blockLogs.isEmpty() -> BlockReviewStatus.NO_RECORDS
            difficult -> BlockReviewStatus.DECLARED_DIFFICULTY
            coverage < LOW_COVERAGE -> BlockReviewStatus.LOW_RECORDED_COVERAGE
            gain != null && gain >= IMPROVING_GAIN && coverage >= IMPROVING_COVERAGE -> BlockReviewStatus.IMPROVING_RECORDS
            else -> BlockReviewStatus.STABLE_RECORDS
        }
        val trendText = changes.joinToString("; ") { "${it.exerciseName}: ${it.fromKg} → ${it.toKg} kg (e1RM)" }
        val statusText = when (status) {
            BlockReviewStatus.IMPROVING_RECORDS -> "mejora en registros comparables"
            BlockReviewStatus.STABLE_RECORDS -> "registros disponibles revisados"
            BlockReviewStatus.DECLARED_DIFFICULTY -> "dificultad declarada en el chequeo"
            BlockReviewStatus.LOW_RECORDED_COVERAGE -> "cobertura de registros parcial"
            BlockReviewStatus.NO_RECORDS -> "sin registros atribuibles a este bloque"
        }
        val volumeNote = if (recordedVolume.unassignedCount == 0) "" else
            " ${recordedVolume.unassignedCount} registros sin asignación suficiente para contar sus series."
        return BlockReview(blockId, block.name, weeks.size, planned, recorded, coverage, muscles, changes,
            status, "${block.name}: $recorded/$planned sesiones requeridas registradas ($coverage %); $statusText." +
                (if (trendText.isEmpty()) "" else " $trendText.") + volumeNote, nowMs, run?.runId, cycle, answers.size)
    }

    private fun round1(value: Double) = round(value * 10.0) / 10.0
}
