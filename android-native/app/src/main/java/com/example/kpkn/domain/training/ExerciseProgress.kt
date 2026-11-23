package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.normalizeExerciseIdentityToken
import com.example.kpkn.domain.exercises.analyticsExerciseKey
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.time.ActivityLocalDate
import com.example.kpkn.domain.workout.CompletedSetLoadSemantics
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ExerciseProgressMetric(val title: String, val unit: String) {
    E1RM("e1RM", "kg"),
    BEST_SET("Mejor serie", "kg × reps"),
    MAX_WEIGHT("Peso máx.", "kg"),
    MAX_REPS("Reps máx.", "reps"),
    VOLUME("Volumen", "kg"),
    EFFORT("RIR / RPE", "RIR"),
    SETS("Series", "series"),
    DURATION("Duración", "s"),
    DISTANCE("Distancia", "m"),
}

enum class ExerciseProgressWindow(val title: String, val shortLabel: String) {
    MONTH("1 mes", "1M"),
    QUARTER("3 meses", "3M"),
    YEAR("1 año", "1A"),
    ALL("Todo", "Todo");

    fun start(today: LocalDate): LocalDate? = when (this) {
        MONTH -> today.minusMonths(1)
        QUARTER -> today.minusMonths(3)
        YEAR -> today.minusYears(1)
        ALL -> null
    }
}

data class ExerciseProgressChoice(
    val id: String,
    val name: String,
    val configuration: String,
    val sessions: Int,
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null,
    val latestE1rm: Double? = null,
    val deltaE1rm: Double? = null,
    val sparkline: List<Double> = emptyList(),
    val avgRir: Double? = null,
    val totalTonnageKg: Double = 0.0,
    val totalSets: Int = 0,
)

data class ExerciseProgressPoint(
    val logId: String,
    val date: LocalDate,
    val session: String,
    val lineage: String,
    val context: String,
    val mode: LoadModeV2,
    val unitMode: UnitModeV2,
    val value: Double,
    val higherIsBetter: Boolean,
    val bestSet: CompletedSet,
    val sets: Int,
    val explanation: String,
    val isPr: Boolean = false,
    val breaksBefore: Boolean = false,
)

data class ExerciseProgressRecord(
    val metric: ExerciseProgressMetric,
    val point: ExerciseProgressPoint,
)

data class ExerciseTimelineMeta(
    val firstLoggedDate: LocalDate?,
    val firstPlannedDate: LocalDate?,
    val totalSessions: Int,
    val totalEffectiveSets: Int,
    val totalTonnageKg: Double,
    val avgRir: Double?,
    val avgFrequencyPerWeek: Double,
)

data class ExerciseAdherenceReport(
    val setsPlanned: Int,
    val setsDone: Int,
    val setsCompliancePct: Int?,
    val repsInTargetPct: Int?,
    val rirHitRatePct: Int?,
)

data class ExerciseMilestone(
    val date: LocalDate,
    val title: String,
    val subtitle: String,
    val metric: ExerciseProgressMetric?,
    val value: Double?,
    val isPr: Boolean,
)

data class ExerciseSessionSetLog(
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int,
    val rir: Double?,
    val rpe: Double?,
    val isFailure: Boolean,
    val isDropSet: Boolean,
    val isRestPause: Boolean,
    val isPr: Boolean,
    val displayLine: String,
)

data class ExerciseSessionLogEntry(
    val logId: String,
    val date: LocalDate,
    val sessionName: String,
    val programName: String?,
    val tonnageKg: Double,
    val avgRir: Double?,
    val setsDone: Int,
    val setsPlanned: Int?,
    val sets: List<ExerciseSessionSetLog>,
)

data class ExerciseProgressReport(
    val choice: ExerciseProgressChoice?,
    val metric: ExerciseProgressMetric,
    val available: Set<ExerciseProgressMetric>,
    val points: List<ExerciseProgressPoint>,
    val records: List<ExerciseProgressRecord>,
    val timelineMeta: ExerciseTimelineMeta? = null,
    val adherence: ExerciseAdherenceReport? = null,
    val milestones: List<ExerciseMilestone> = emptyList(),
    val sessionLogs: List<ExerciseSessionLogEntry> = emptyList(),
) {
    val insufficient: Boolean get() = points.groupBy { it.lineage }.values.none { it.size >= 2 }
}

/** Proyección de solo lectura. Cada trayectoria conserva modalidad, contexto e identidad exacta. */
object ExerciseProgress {
    fun identity(exercise: CompletedExercise): String =
        exercise.catalogConfigurationId?.takeIf(String::isNotBlank)?.let { "configuration:$it" }
            ?: exercise.catalogDefinitionId?.takeIf(String::isNotBlank)?.let { "definition:$it" }
            ?: "name:${normalizeExerciseIdentityToken(exercise.exerciseName)}"

    fun identity(exercise: Exercise): String =
        exercise.catalogConfigurationId?.takeIf(String::isNotBlank)?.let { "configuration:$it" }
            ?: exercise.catalogDefinitionId?.takeIf(String::isNotBlank)?.let { "definition:$it" }
            ?: "name:${normalizeExerciseIdentityToken(exercise.name)}"

    internal fun effectiveRir(set: CompletedSet): Double? {
        set.rir?.let { return it.toDouble().coerceAtLeast(0.0) }
        set.rpe?.takeIf { it.isFinite() && it in 1.0..10.0 }?.let { return (10.0 - it).coerceAtLeast(0.0) }
        val payload = set.recordedPayloadV3
        val mode = payload?.actualIntensityMode ?: set.actualIntensityMode
        val value = payload?.actualIntensityValue ?: set.actualIntensityValue
        if (mode != null && value != null && value.isFinite()) {
            return when (mode) {
                IntensityMode.RIR -> value.coerceAtLeast(0.0)
                IntensityMode.RPE -> (10.0 - value).coerceAtLeast(0.0)
                IntensityMode.FAILURE -> 0.0
                else -> null
            }
        }
        if (set.isFailure || payload?.reachedFailure == true) return 0.0
        return null
    }

    fun choices(
        logs: List<WorkoutLog>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        programs: List<Program> = emptyList(),
        programId: String? = null,
    ): List<ExerciseProgressChoice> {
        val loggedChoices = logs
            .flatMap { log ->
                val date = runCatching { ActivityLocalDate.fromInstantIsoOrDatePrefix(log.actualDate ?: log.date, zone) }.getOrNull()
                log.completedExercises.map { Triple(log, date, it) }
            }
            .groupBy { identity(it.third) }
            .map { (id, triples) ->
                val exercise = triples.last().third
                val configLabel = listOfNotNull(
                    exercise.variantName?.takeIf(String::isNotBlank),
                    exercise.selectedAspects?.values?.joinToString(" · ")?.takeIf(String::isNotBlank),
                ).joinToString(" · ")
                val validSessionTriples = triples
                    .filter { (_, date, _) -> date == null || date <= today }
                    .sortedBy { it.second ?: LocalDate.MIN }
                val dates = validSessionTriples.mapNotNull { it.second }.sorted()
                val sessionE1rms = validSessionTriples
                    .groupBy { it.first.id }
                    .values
                    .mapNotNull { sessionItems ->
                        val rows = sessionItems.flatMap { (_, _, ex) -> ex.sets.mapNotNull { s -> row(ex, s) } }
                        val e1rms = rows
                            .filter { it.mode == LoadModeV2.LOAD && (it.load ?: 0.0) > 0.0 && CompletedSetLoadSemantics.supportsEstimatedOneRm(it.set) }
                            .map { StrengthModel.estimate1RM(it.set) }
                            .filter { it.isFinite() && it > 0.0 }
                        e1rms.maxOrNull()
                    }
                val fallbackSeries = if (sessionE1rms.isEmpty()) {
                    validSessionTriples
                        .groupBy { it.first.id }
                        .values
                        .mapNotNull { sessionItems ->
                            val rows = sessionItems.flatMap { (_, _, ex) -> ex.sets.mapNotNull { s -> row(ex, s) } }
                            rows.maxOfOrNull { it.load?.takeIf { l -> l > 0.0 } ?: it.amount }
                        }
                } else {
                    emptyList()
                }
                val allRows = validSessionTriples.flatMap { (_, _, ex) -> ex.sets.mapNotNull { s -> row(ex, s) } }
                val rirs = allRows.mapNotNull { effectiveRir(it.set) }
                val tonnage = allRows
                    .filter { it.mode == LoadModeV2.LOAD || it.mode == LoadModeV2.LASTRE }
                    .sumOf { CompletedSetLoadSemantics.totalExternalTonnageKg(it.set, it.set.effectiveRepEquivalent()) }
                val latestE1rm = sessionE1rms.lastOrNull()
                val deltaE1rm = if (sessionE1rms.size >= 2) sessionE1rms.last() - sessionE1rms.first() else null
                val sparkline = (sessionE1rms.ifEmpty { fallbackSeries }).takeLast(12)
                ExerciseProgressChoice(
                    id = id,
                    name = exercise.exerciseName,
                    configuration = configLabel,
                    sessions = triples.map { it.first.id }.distinct().size,
                    firstDate = dates.firstOrNull(),
                    lastDate = dates.lastOrNull(),
                    latestE1rm = latestE1rm,
                    deltaE1rm = deltaE1rm,
                    sparkline = sparkline,
                    avgRir = rirs.takeIf { it.isNotEmpty() }?.average(),
                    totalTonnageKg = tonnage,
                    totalSets = allRows.size,
                )
            }
            .sortedWith(compareByDescending<ExerciseProgressChoice> { it.sessions }.thenBy { it.name })

        if (programs.isEmpty()) return loggedChoices
        val loggedIds = loggedChoices.map { it.id }.toSet()
        val loggedNames = loggedChoices.map { normalizeExerciseIdentityToken(it.name) }.toSet()
        val targetPrograms = if (programId != null) {
            programs.filter { it.id == programId }.ifEmpty { programs }
        } else {
            programs
        }
        val plannedOnly = targetPrograms
            .flatMap { program ->
                program.macrocycles.flatMap { macro ->
                    macro.blocks.flatMap { block ->
                        block.mesocycles.flatMap { meso ->
                            meso.weeks.filter { !it.isLoopWeek }.flatMap { week ->
                                week.sessions.flatMap { session -> session.exercises }
                            }
                        }
                    }
                }
            }
            .groupBy { identity(it) }
            .mapNotNull { (id, exercises) ->
                val sample = exercises.first()
                val normName = normalizeExerciseIdentityToken(sample.name)
                if (id in loggedIds || normName in loggedNames) return@mapNotNull null
                val configLabel = listOfNotNull(
                    sample.variantName?.takeIf(String::isNotBlank),
                    sample.selectedAspects?.values?.joinToString(" · ")?.takeIf(String::isNotBlank),
                ).joinToString(" · ")
                ExerciseProgressChoice(
                    id = id,
                    name = sample.name,
                    configuration = configLabel,
                    sessions = 0,
                )
            }
            .sortedBy { it.name }
        return loggedChoices + plannedOnly
    }

    private data class Row(
        val set: CompletedSet,
        val mode: LoadModeV2,
        val unit: UnitModeV2,
        val load: Double?,
        val reps: Int,
        val amount: Double,
        val bodyKnown: Boolean,
        val lineage: String,
        val context: String,
    )

    private fun row(exercise: CompletedExercise, set: CompletedSet): Row? {
        val payload = set.recordedPayloadV3
        val homologated = set.homologatedResultV3
        val outcome = set.setOutcomeV2
        if (set.skipped || set.isWarmup || payload?.skipped == true || set.isFailedSet || payload?.failedSet == true || homologated?.failedSet == true || outcome?.failedSet == true) return null
        val mode = CompletedSetLoadSemantics.mode(set) ?: LoadModeV2.LOAD
        val unit = payload?.unitMode ?: homologated?.unitMode ?: outcome?.unitMode ?: when {
            set.distanceKm != null -> UnitModeV2.DISTANCE
            set.timeSeconds != null -> UnitModeV2.TIME
            else -> UnitModeV2.REPS
        }
        if (unit == UnitModeV2.CUSTOM) return null
        val reps = payload?.completedReps ?: set.reps
        val load: Double? = when (mode) {
            LoadModeV2.BODYWEIGHT -> 0.0
            LoadModeV2.ASSISTED -> CompletedSetLoadSemantics.assistanceKg(set)
            LoadModeV2.LASTRE, LoadModeV2.LOAD -> CompletedSetLoadSemantics.externalLoadKg(set)
        }
        val amount = when (unit) {
            UnitModeV2.TIME -> (payload?.durationSeconds ?: set.timeSeconds)?.toDouble()
            UnitModeV2.DISTANCE -> set.distanceKm?.times(1000.0) ?: homologated?.actualValue ?: outcome?.actualValue
            else -> reps.toDouble()
        } ?: return null
        if (load != null && (!load.isFinite() || load < 0)) return null
        if (!amount.isFinite() || amount <= 0 || (unit == UnitModeV2.REPS && mode == LoadModeV2.LOAD && (load ?: 0.0) <= 0)) return null
        // No reinterpretar reps parciales antiguas como repeticiones completas.
        if (unit == UnitModeV2.REPS && (set.isPartial || (payload?.partialReps ?: set.partialReps ?: 0) > 0)) return null
        val context = listOfNotNull(set.machineBrand, set.tagName, set.side ?: payload?.side).joinToString(" · ")
        val lineage = listOf(
            identity(exercise),
            mode.name,
            unit.name,
            exercise.selectedAspects.orEmpty().toSortedMap().toString(),
            set.contextProfileId ?: payload?.contextProfileId ?: homologated?.contextKey ?: outcome?.contextKey ?: "legacy",
            exercise.variantName.orEmpty(),
            exercise.performanceProfileId.orEmpty(),
            exercise.exerciseDbId.orEmpty(),
            set.setupProfileId.orEmpty(),
            set.machineBrand.orEmpty(),
            set.side ?: payload?.side.orEmpty(),
            set.tagId.orEmpty(),
            set.subTagIds.sorted().joinToString(","),
            if (unit != UnitModeV2.REPS) (load?.toString() ?: "sin-detalle") else "",
        ).joinToString("|")
        return Row(
            set = set,
            mode = mode,
            unit = unit,
            load = load,
            reps = reps,
            amount = amount,
            bodyKnown = payload?.bodyWeightSnapshot?.let { it.isFinite() && it > 0 } == true,
            lineage = lineage,
            context = context,
        )
    }

    private fun available(row: Row): Set<ExerciseProgressMetric> = when (row.unit) {
        UnitModeV2.TIME -> buildSet {
            add(ExerciseProgressMetric.DURATION)
            add(ExerciseProgressMetric.SETS)
            if (effectiveRir(row.set) != null) add(ExerciseProgressMetric.EFFORT)
        }
        UnitModeV2.DISTANCE -> buildSet {
            add(ExerciseProgressMetric.DISTANCE)
            add(ExerciseProgressMetric.SETS)
            if (effectiveRir(row.set) != null) add(ExerciseProgressMetric.EFFORT)
        }
        else -> buildSet {
            add(ExerciseProgressMetric.MAX_REPS)
            add(ExerciseProgressMetric.SETS)
            if (row.mode != LoadModeV2.BODYWEIGHT && row.load != null) {
                add(ExerciseProgressMetric.MAX_WEIGHT)
                add(ExerciseProgressMetric.BEST_SET)
            }
            if (row.mode == LoadModeV2.LOAD && (row.load ?: 0.0) > 0.0) add(ExerciseProgressMetric.E1RM)
            if ((row.mode == LoadModeV2.LOAD || row.mode == LoadModeV2.LASTRE) &&
                (row.load != null || row.set.dropSets.any { it.weight.isFinite() && it.weight > 0.0 && it.reps > 0 })
            ) {
                add(ExerciseProgressMetric.VOLUME)
            }
            if (effectiveRir(row.set) != null) {
                add(ExerciseProgressMetric.EFFORT)
            }
        }
    }

    internal fun displayNumber(value: Double): String = java.math.BigDecimal.valueOf(value)
        .setScale(1, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')

    private fun trajectoryLineage(row: Row, metric: ExerciseProgressMetric): String = row.lineage + when {
        metric in setOf(ExerciseProgressMetric.BEST_SET, ExerciseProgressMetric.MAX_WEIGHT) &&
            row.mode != LoadModeV2.BODYWEIGHT -> "|reps@${row.reps}"
        metric == ExerciseProgressMetric.MAX_REPS && row.mode != LoadModeV2.BODYWEIGHT ->
            "|reps@${row.load?.toString() ?: "sin-detalle"}"
        else -> ""
    }

    private fun point(log: WorkoutLog, date: LocalDate, rows: List<Row>, metric: ExerciseProgressMetric): ExerciseProgressPoint? {
        val usable = rows.filter { metric in available(it) }
        if (usable.isEmpty()) return null
        val first = usable.first()
        val descending = !(metric in setOf(ExerciseProgressMetric.MAX_WEIGHT, ExerciseProgressMetric.BEST_SET) && first.mode == LoadModeV2.ASSISTED)
        fun score(row: Row): Double = when (metric) {
            ExerciseProgressMetric.E1RM -> if (CompletedSetLoadSemantics.supportsEstimatedOneRm(row.set)) StrengthModel.estimate1RM(row.set) else 0.0
            ExerciseProgressMetric.MAX_WEIGHT -> row.load ?: 0.0
            ExerciseProgressMetric.BEST_SET -> if (row.mode == LoadModeV2.ASSISTED) {
                // La puntuación nativa ordena menor asistencia como mejora y respeta las reps; nunca se rotula e1RM.
                WorkoutPerformanceHomologationEngine.evaluate(
                    SetEntryV2(
                        log.id,
                        setIndex = 0,
                        loadMode = row.mode,
                        unitMode = row.unit,
                        actualValue = row.reps.toDouble(),
                        loggedLoad = row.load ?: 0.0,
                        contextKey = row.lineage,
                    ),
                    null,
                ).homologated.metricValue
            } else (row.load ?: 0.0) * row.reps
            ExerciseProgressMetric.MAX_REPS -> row.reps.toDouble()
            ExerciseProgressMetric.VOLUME -> CompletedSetLoadSemantics.totalExternalTonnageKg(row.set, row.set.effectiveRepEquivalent())
            ExerciseProgressMetric.EFFORT -> effectiveRir(row.set) ?: 0.0
            ExerciseProgressMetric.SETS -> 1.0
            else -> row.amount
        }
        val best = if (metric == ExerciseProgressMetric.BEST_SET && first.mode == LoadModeV2.ASSISTED || descending) {
            usable.maxBy(::score)
        } else {
            usable.minBy(::score)
        }
        val value = when {
            metric == ExerciseProgressMetric.BEST_SET && first.mode == LoadModeV2.ASSISTED -> (best.load ?: return null)
            metric == ExerciseProgressMetric.VOLUME || metric == ExerciseProgressMetric.SETS -> usable.sumOf(::score)
            metric == ExerciseProgressMetric.EFFORT -> {
                val rirs = usable.mapNotNull { effectiveRir(it.set) }
                if (rirs.isEmpty()) return null else rirs.average()
            }
            else -> score(best)
        }
        val allowZero = (value == 0.0 && best.mode == LoadModeV2.ASSISTED) || (value == 0.0 && metric == ExerciseProgressMetric.EFFORT)
        if (!value.isFinite() || (value <= 0 && !allowZero)) return null
        val loadLabel = when (best.mode) {
            LoadModeV2.ASSISTED -> "kg de asistencia"
            LoadModeV2.LASTRE -> "kg de lastre"
            LoadModeV2.BODYWEIGHT -> "peso corporal"
            else -> "kg"
        }
        val bestLoad = best.load
        val detail = when (metric) {
            ExerciseProgressMetric.E1RM -> "${displayNumber(bestLoad ?: 0.0)} kg × ${best.reps}" +
                (best.set.rir?.let { " @ RIR $it" } ?: best.set.rpe?.let { " @ RPE $it" } ?: " · sin reserva declarada") +
                " → e1RM ${displayNumber(value)} kg estimados"
            ExerciseProgressMetric.DURATION -> if (bestLoad == null) "${displayNumber(best.amount)} s · ${CompletedSetLoadSemantics.displayLoad(best.set)}" else "${displayNumber(best.amount)} s · misma carga registrada"
            ExerciseProgressMetric.DISTANCE -> if (bestLoad == null) "${displayNumber(best.amount)} m · ${CompletedSetLoadSemantics.displayLoad(best.set)}" else "${displayNumber(best.amount)} m · misma carga registrada"
            ExerciseProgressMetric.SETS -> "${usable.size} series válidas"
            ExerciseProgressMetric.VOLUME -> "${displayNumber(value)} kg de volumen registrado; no incluye peso corporal sin dato"
            ExerciseProgressMetric.EFFORT -> "RIR medio ${displayNumber(value)} (RPE equiv. ${displayNumber((10.0 - value).coerceIn(1.0, 10.0))}) en ${usable.size} series"
            else -> when {
                best.mode == LoadModeV2.BODYWEIGHT -> "Peso corporal × ${best.reps} reps"
                bestLoad == null -> "${CompletedSetLoadSemantics.displayLoad(best.set)} × ${best.reps} reps"
                else -> "${displayNumber(bestLoad)} $loadLabel × ${best.reps} reps"
            }
        }
        val trajectory = trajectoryLineage(best, metric)
        return ExerciseProgressPoint(
            logId = log.id,
            date = date,
            session = log.sessionName,
            lineage = trajectory,
            context = best.context,
            mode = best.mode,
            unitMode = best.unit,
            value = value,
            higherIsBetter = descending,
            bestSet = best.set,
            sets = usable.size,
            explanation = detail,
        )
    }

    private fun matchesChoice(planned: Exercise, choice: ExerciseProgressChoice): Boolean {
        if (identity(planned) == choice.id) return true
        val plannedConfig = planned.catalogConfigurationId?.takeIf(String::isNotBlank)?.let { "configuration:$it" }
        val plannedDef = planned.catalogDefinitionId?.takeIf(String::isNotBlank)?.let { "definition:$it" }
        if (plannedConfig != null && plannedConfig == choice.id) return true
        if (plannedDef != null && plannedDef == choice.id) return true
        return normalizeExerciseIdentityToken(planned.name) == normalizeExerciseIdentityToken(choice.name)
    }

    private fun extractProgramStartDate(program: Program, logs: List<WorkoutLog>, zone: ZoneId): LocalDate? {
        val candidates = mutableListOf<LocalDate>()
        program.timelineStartDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let(candidates::add)
        logs.asSequence()
            .filter { it.programId == program.id }
            .mapNotNull { runCatching { ActivityLocalDate.fromInstantIsoOrDatePrefix(it.actualDate ?: it.date, zone) }.getOrNull() }
            .minOrNull()
            ?.let(candidates::add)
        return candidates.minOrNull()
    }

    private fun findPlannedExerciseForLog(
        log: WorkoutLog,
        choice: ExerciseProgressChoice,
        programs: List<Program>,
        preferredProgramId: String?,
    ): Pair<Program?, Exercise?> {
        if (programs.isEmpty()) return null to null
        val program = programs.firstOrNull { it.id == log.programId }
            ?: preferredProgramId?.let { id -> programs.firstOrNull { it.id == id } }
            ?: programs.firstOrNull { p ->
                p.macrocycles.any { m ->
                    m.blocks.any { b ->
                        b.mesocycles.any { me ->
                            me.weeks.any { w ->
                                !w.isLoopWeek && w.sessions.any { s -> s.exercises.any { matchesChoice(it, choice) } }
                            }
                        }
                    }
                }
            }
        if (program == null) return null to null
        val allSessions = program.macrocycles.flatMap { m ->
            m.blocks.flatMap { b ->
                b.mesocycles.flatMap { me ->
                    me.weeks.filter { !it.isLoopWeek }.flatMap { w -> w.sessions }
                }
            }
        }
        val completedIds = log.completedExercises
            .filter { identity(it) == choice.id }
            .map { it.exerciseId }
            .toSet()
        val matchingSession = allSessions.firstOrNull { it.id == log.sessionId }
            ?: allSessions.firstOrNull { it.name.equals(log.sessionName, ignoreCase = true) }
        val inSession = matchingSession?.exercises?.firstOrNull { it.id in completedIds || matchesChoice(it, choice) }
        if (inSession != null) return program to inSession
        val fallback = allSessions.asSequence()
            .flatMap { it.exercises.asSequence() }
            .firstOrNull { it.id in completedIds || matchesChoice(it, choice) }
        return program to fallback
    }

    private fun plannedRirForSet(plannedSet: ExerciseSet): Double? {
        val mode = plannedSet.intensityMode
            ?: plannedSet.leftTarget?.intensityMode
            ?: plannedSet.rightTarget?.intensityMode
        if (mode == IntensityMode.FAILURE) return 0.0
        val rir = plannedSet.targetRIR
            ?: plannedSet.leftTarget?.targetRIR
            ?: plannedSet.rightTarget?.targetRIR
        if (rir != null) return rir.toDouble().coerceAtLeast(0.0)
        val rpe = plannedSet.targetRPE
            ?: plannedSet.leftTarget?.targetRPE
            ?: plannedSet.rightTarget?.targetRPE
        if (rpe != null && rpe.isFinite() && rpe in 1.0..10.0) return (10.0 - rpe).coerceAtLeast(0.0)
        return null
    }

    private fun isRepsInTarget(actualReps: Int, plannedSet: ExerciseSet, amrap: Boolean): Boolean? {
        val range = plannedSet.targetRepsRange
            ?: plannedSet.leftTarget?.targetRepsRange
            ?: plannedSet.rightTarget?.targetRepsRange
        if (range != null && range.min > 0 && range.max >= range.min) {
            return if (amrap) actualReps >= range.min else actualReps in (range.min - 1)..(range.max + 1)
        }
        val target = plannedSet.targetReps
            ?: plannedSet.leftTarget?.targetReps
            ?: plannedSet.rightTarget?.targetReps
        if (target != null && target > 0) {
            return if (amrap) actualReps >= target else abs(actualReps - target) <= 1 || actualReps >= target
        }
        return null
    }

    fun build(
        logs: List<WorkoutLog>,
        exerciseId: String?,
        metric: ExerciseProgressMetric = ExerciseProgressMetric.E1RM,
        window: ExerciseProgressWindow = ExerciseProgressWindow.QUARTER,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        programs: List<Program> = emptyList(),
        programId: String? = null,
    ): ExerciseProgressReport {
        val choices = choices(logs, today, zone, programs, programId)
        val keys = exerciseId?.takeIf { it.startsWith("keys:") }?.removePrefix("keys:")?.split(",").orEmpty().toSet()
        val exactKeyChoices = logs.flatMap { it.completedExercises }.filter { it.catalogConfigurationId in keys }.map { identity(it) }.toSet()
        val definitionKeyChoices = logs.flatMap { it.completedExercises }.filter { it.catalogDefinitionId in keys }.map { identity(it) }.toSet()
        val matchingAliases = logs.flatMap { it.completedExercises }.filter { exercise ->
            exercise.analyticsExerciseKey() == exerciseId ||
                exercise.catalogConfigurationId?.let { "configuration:$it" } == exerciseId ||
                exercise.catalogDefinitionId?.let { "definition:$it" } == exerciseId ||
                "name:${normalizeExerciseIdentityToken(exercise.exerciseName)}" == exerciseId ||
                exercise.exerciseId == exerciseId
        }.map { identity(it) }.toSet()
        val plannedKeyMatch = if (keys.isNotEmpty() || exerciseId != null) {
            choices.firstOrNull { ch ->
                ch.id in keys ||
                    ch.id.removePrefix("configuration:") in keys ||
                    ch.id.removePrefix("definition:") in keys ||
                    (exerciseId != null && normalizeExerciseIdentityToken(ch.name) == normalizeExerciseIdentityToken(exerciseId.removePrefix("name:")))
            }
        } else null
        val choice = choices.firstOrNull { it.id == exerciseId }
            ?: choices.firstOrNull { it.id in exactKeyChoices }
            ?: choices.firstOrNull { it.id in definitionKeyChoices }
            ?: choices.firstOrNull { it.id in matchingAliases }
            ?: plannedKeyMatch
            ?: if (exerciseId == null) choices.firstOrNull() else null

        val sessions = logs.mapNotNull { log ->
            val date = runCatching { ActivityLocalDate.fromInstantIsoOrDatePrefix(log.actualDate ?: log.date, zone) }.getOrNull()
                ?: return@mapNotNull null
            if (date > today) return@mapNotNull null
            val rows = log.completedExercises
                .filter { identity(it) == choice?.id }
                .flatMap { exercise -> exercise.sets.mapNotNull { row(exercise, it) } }
            Triple(log, date, rows)
        }.sortedWith(
            compareBy<Triple<WorkoutLog, LocalDate, List<Row>>> { it.second }
                .thenBy { it.first.actualDate ?: it.first.date }
                .thenBy { it.first.id },
        )

        val activeSessions = sessions.filter { it.third.isNotEmpty() }
        val available = sessions.flatMap { it.third.flatMap { row -> available(row) } }.toSet()
        val preferredOrder = listOf(
            ExerciseProgressMetric.E1RM,
            ExerciseProgressMetric.BEST_SET,
            ExerciseProgressMetric.MAX_WEIGHT,
            ExerciseProgressMetric.MAX_REPS,
            ExerciseProgressMetric.DISTANCE,
            ExerciseProgressMetric.DURATION,
            ExerciseProgressMetric.VOLUME,
            ExerciseProgressMetric.EFFORT,
            ExerciseProgressMetric.SETS,
        )
        val selected = metric.takeIf { it in available }
            ?: preferredOrder.firstOrNull { it in available }
            ?: metric

        fun history(m: ExerciseProgressMetric): List<ExerciseProgressPoint> {
            val raw = sessions.flatMap { (log, date, rows) ->
                rows.groupBy { trajectoryLineage(it, m) }.values.mapNotNull { point(log, date, it, m) }
            }
            val best = mutableMapOf<String, Double>()
            var prior: String? = null
            return raw.map { point ->
                val key = point.lineage
                val previous = best[key]
                val pr = previous == null || if (point.higherIsBetter) point.value > previous else point.value < previous
                if (pr) best[key] = point.value
                point.copy(isPr = pr, breaksBefore = prior != null && prior != point.lineage).also { prior = point.lineage }
            }
        }

        val records = available
            .filter { it != ExerciseProgressMetric.EFFORT }
            .flatMap { m ->
                history(m).groupBy { it.lineage }.values.map { points ->
                    ExerciseProgressRecord(
                        m,
                        points.first {
                            it.value == if (it.higherIsBetter) points.maxOf { p -> p.value } else points.minOf { p -> p.value }
                        },
                    )
                }
            }

        val start = window.start(today)
        val filteredPoints = history(selected).filter { start == null || it.date >= start }

        if (choice == null) {
            return ExerciseProgressReport(
                choice = null,
                metric = selected,
                available = available,
                points = filteredPoints,
                records = records,
            )
        }

        // ─── Timeline meta ──────────────────────────────────────────────
        val firstLoggedDate = activeSessions.firstOrNull()?.second
        val lastLoggedDate = activeSessions.lastOrNull()?.second
        val relevantPrograms = if (programId != null) {
            programs.filter { it.id == programId }.ifEmpty { programs }
        } else {
            programs
        }
        val firstPlannedDate = relevantPrograms
            .filter { program ->
                program.macrocycles.any { m ->
                    m.blocks.any { b ->
                        b.mesocycles.any { me ->
                            me.weeks.any { w ->
                                !w.isLoopWeek && w.sessions.any { s -> s.exercises.any { matchesChoice(it, choice) } }
                            }
                        }
                    }
                }
            }
            .mapNotNull { extractProgramStartDate(it, logs, zone) }
            .minOrNull()

        val allActiveRows = activeSessions.flatMap { it.third }
        val totalTonnageKg = allActiveRows
            .filter { it.mode == LoadModeV2.LOAD || it.mode == LoadModeV2.LASTRE }
            .sumOf { CompletedSetLoadSemantics.totalExternalTonnageKg(it.set, it.set.effectiveRepEquivalent()) }
        val allRirs = allActiveRows.mapNotNull { effectiveRir(it.set) }
        val avgRir = allRirs.takeIf { it.isNotEmpty() }?.average()
        val avgFrequencyPerWeek = when {
            activeSessions.isEmpty() -> 0.0
            activeSessions.size == 1 -> 1.0
            firstLoggedDate != null && lastLoggedDate != null -> {
                val spanDays = ChronoUnit.DAYS.between(firstLoggedDate, lastLoggedDate).coerceAtLeast(7L)
                activeSessions.size / (spanDays / 7.0)
            }
            else -> 0.0
        }
        val timelineMeta = ExerciseTimelineMeta(
            firstLoggedDate = firstLoggedDate,
            firstPlannedDate = firstPlannedDate,
            totalSessions = activeSessions.size,
            totalEffectiveSets = allActiveRows.size,
            totalTonnageKg = totalTonnageKg,
            avgRir = avgRir,
            avgFrequencyPerWeek = avgFrequencyPerWeek,
        )

        // ─── Adherence & Session Logs ───────────────────────────────────
        val primaryPrMetric = when {
            ExerciseProgressMetric.E1RM in available -> ExerciseProgressMetric.E1RM
            ExerciseProgressMetric.MAX_WEIGHT in available -> ExerciseProgressMetric.MAX_WEIGHT
            ExerciseProgressMetric.MAX_REPS in available -> ExerciseProgressMetric.MAX_REPS
            else -> selected
        }
        val primaryHistory = history(primaryPrMetric)
        val prLogAndSetIds = primaryHistory
            .filter { it.isPr }
            .map { it.logId to it.bestSet.id }
            .toSet()

        var setsPlannedTotal = 0
        var setsDoneTotal = 0
        var repsEvaluated = 0
        var repsHit = 0
        var rirEvaluated = 0
        var rirHit = 0

        val sessionLogEntries = activeSessions.map { (log, date, rows) ->
            val (matchedProgram, plannedExercise) = findPlannedExerciseForLog(log, choice, programs, programId)
            val plannedWorkingSets = plannedExercise?.sets
            val sessionSetsPlanned = plannedWorkingSets?.size?.takeIf { it > 0 }
                ?: run {
                    val completedIds = log.completedExercises.filter { identity(it) == choice.id }.map { it.exerciseId }.toSet()
                    val omittedCount = log.omittedSeries.count { key -> completedIds.any { key.startsWith(it) } }
                    (rows.size + omittedCount).takeIf { rows.isNotEmpty() }
                }
            if (sessionSetsPlanned != null && sessionSetsPlanned > 0) {
                setsPlannedTotal += sessionSetsPlanned
                setsDoneTotal += rows.size
            }
            rows.forEachIndexed { idx, r ->
                val plannedSet = plannedWorkingSets?.getOrNull(idx) ?: plannedWorkingSets?.lastOrNull()
                if (plannedSet != null) {
                    isRepsInTarget(r.reps, plannedSet, r.set.amrapPerformed)?.let { hit ->
                        repsEvaluated++
                        if (hit) repsHit++
                    }
                    val targetRir = plannedRirForSet(plannedSet)
                    val actualRir = effectiveRir(r.set)
                    if (targetRir != null && actualRir != null) {
                        rirEvaluated++
                        if (abs(actualRir - targetRir) <= 1.0) rirHit++
                    }
                }
            }
            val sessionTonnage = rows
                .filter { it.mode == LoadModeV2.LOAD || it.mode == LoadModeV2.LASTRE }
                .sumOf { CompletedSetLoadSemantics.totalExternalTonnageKg(it.set, it.set.effectiveRepEquivalent()) }
            val sessionRirs = rows.mapNotNull { effectiveRir(it.set) }
            val setLogs = rows.mapIndexed { idx, r ->
                val rirVal = effectiveRir(r.set)
                val rpeVal = r.set.rpe ?: rirVal?.let { (10.0 - it).coerceIn(1.0, 10.0) }
                val isFailure = r.set.isFailure || r.set.recordedPayloadV3?.reachedFailure == true || r.set.actualIntensityMode == IntensityMode.FAILURE
                val isDropSet = r.set.dropSets.isNotEmpty() || r.set.recordedPayloadV3?.techniques.orEmpty().contains(SetTechniqueV2.DROP_SET)
                val isRestPause = r.set.restPauses.isNotEmpty() || r.set.recordedPayloadV3?.techniques.orEmpty().contains(SetTechniqueV2.REST_PAUSE)
                val isPr = (log.id to r.set.id) in prLogAndSetIds
                val loadPart = when (r.mode) {
                    LoadModeV2.BODYWEIGHT -> "Peso corporal"
                    LoadModeV2.ASSISTED -> "-${displayNumber(r.load ?: 0.0)} kg asist."
                    LoadModeV2.LASTRE -> "+${displayNumber(r.load ?: 0.0)} kg lastre"
                    LoadModeV2.LOAD -> "${displayNumber(r.load ?: 0.0)} kg"
                }
                val amountPart = when (r.unit) {
                    UnitModeV2.TIME -> "${displayNumber(r.amount)} s"
                    UnitModeV2.DISTANCE -> "${displayNumber(r.amount)} m"
                    else -> "${r.reps} reps"
                }
                val intensityPart = when {
                    isFailure -> " @ Fallo"
                    r.set.rir != null -> " @ RIR ${r.set.rir}"
                    r.set.rpe != null -> " @ RPE ${displayNumber(r.set.rpe)}"
                    rirVal != null -> " @ RIR ${displayNumber(rirVal)}"
                    else -> ""
                }
                ExerciseSessionSetLog(
                    setNumber = idx + 1,
                    weightKg = r.load,
                    reps = r.reps,
                    rir = rirVal,
                    rpe = rpeVal,
                    isFailure = isFailure,
                    isDropSet = isDropSet,
                    isRestPause = isRestPause,
                    isPr = isPr,
                    displayLine = "$loadPart × $amountPart$intensityPart",
                )
            }
            ExerciseSessionLogEntry(
                logId = log.id,
                date = date,
                sessionName = log.sessionName,
                programName = matchedProgram?.name,
                tonnageKg = sessionTonnage,
                avgRir = sessionRirs.takeIf { it.isNotEmpty() }?.average(),
                setsDone = rows.size,
                setsPlanned = sessionSetsPlanned,
                sets = setLogs,
            )
        }.filter { start == null || it.date >= start }.asReversed()

        val adherence = ExerciseAdherenceReport(
            setsPlanned = setsPlannedTotal,
            setsDone = setsDoneTotal,
            setsCompliancePct = if (setsPlannedTotal > 0) ((setsDoneTotal.toDouble() / setsPlannedTotal.toDouble()) * 100.0).roundToInt().coerceIn(0, 100) else null,
            repsInTargetPct = if (repsEvaluated > 0) ((repsHit.toDouble() / repsEvaluated.toDouble()) * 100.0).roundToInt().coerceIn(0, 100) else null,
            rirHitRatePct = if (rirEvaluated > 0) ((rirHit.toDouble() / rirEvaluated.toDouble()) * 100.0).roundToInt().coerceIn(0, 100) else null,
        )

        // ─── Milestones ─────────────────────────────────────────────────
        val milestones = buildList {
            if (firstPlannedDate != null) {
                add(
                    ExerciseMilestone(
                        date = firstPlannedDate,
                        title = "Incluido en el programa",
                        subtitle = "Inicio de planificación del ejercicio",
                        metric = null,
                        value = null,
                        isPr = false,
                    ),
                )
            }
            if (firstLoggedDate != null) {
                add(
                    ExerciseMilestone(
                        date = firstLoggedDate,
                        title = "Primera sesión registrada",
                        subtitle = activeSessions.first().first.sessionName,
                        metric = null,
                        value = null,
                        isPr = false,
                    ),
                )
            }
            primaryHistory.filter { it.isPr }.forEachIndexed { index, pt ->
                val titlePrefix = if (index == 0) "Marca base" else "Nuevo PR"
                add(
                    ExerciseMilestone(
                        date = pt.date,
                        title = "$titlePrefix de ${primaryPrMetric.title}: ${displayNumber(pt.value)} ${primaryPrMetric.unit}",
                        subtitle = "${pt.session} · ${pt.explanation}",
                        metric = primaryPrMetric,
                        value = pt.value,
                        isPr = true,
                    ),
                )
            }
        }.sortedWith(compareBy<ExerciseMilestone> { it.date }.thenBy { it.isPr })

        return ExerciseProgressReport(
            choice = choice,
            metric = selected,
            available = available,
            points = filteredPoints,
            records = records,
            timelineMeta = timelineMeta,
            adherence = adherence,
            milestones = milestones,
            sessionLogs = sessionLogEntries,
        )
    }
}
