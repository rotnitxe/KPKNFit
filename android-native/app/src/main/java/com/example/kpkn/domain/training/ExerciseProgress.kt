package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.normalizeExerciseIdentityToken
import com.example.kpkn.domain.exercises.analyticsExerciseKey
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.time.ActivityLocalDate
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import java.time.LocalDate
import java.time.ZoneId

enum class ExerciseProgressMetric(val title: String, val unit: String) {
    E1RM("e1RM", "kg"), BEST_SET("Mejor serie", "kg × reps"), MAX_WEIGHT("Peso máx.", "kg"),
    MAX_REPS("Reps máx.", "reps"), VOLUME("Volumen", "kg"), SETS("Series", "series"),
    DURATION("Duración", "s"), DISTANCE("Distancia", "m")
}
enum class ExerciseProgressWindow(val title: String) { MONTH("1 mes"), QUARTER("3 meses"), YEAR("1 año"), ALL("Todo");
    fun start(today: LocalDate): LocalDate? = when (this) { MONTH -> today.minusMonths(1); QUARTER -> today.minusMonths(3); YEAR -> today.minusYears(1); ALL -> null }
}
data class ExerciseProgressChoice(val id: String, val name: String, val configuration: String, val sessions: Int)
data class ExerciseProgressPoint(
    val logId: String, val date: LocalDate, val session: String, val lineage: String, val context: String,
    val mode: LoadModeV2, val unitMode: UnitModeV2, val value: Double, val higherIsBetter: Boolean,
    val bestSet: CompletedSet, val sets: Int, val explanation: String, val isPr: Boolean = false,
    val breaksBefore: Boolean = false,
)
data class ExerciseProgressRecord(val metric: ExerciseProgressMetric, val point: ExerciseProgressPoint)
data class ExerciseProgressReport(
    val choice: ExerciseProgressChoice?, val metric: ExerciseProgressMetric, val available: Set<ExerciseProgressMetric>,
    val points: List<ExerciseProgressPoint>, val records: List<ExerciseProgressRecord>,
) { val insufficient: Boolean get() = points.groupBy { it.lineage }.values.none { it.size >= 2 } }

/** Proyección de solo lectura. Cada trayectoria conserva modalidad, contexto e identidad exacta. */
object ExerciseProgress {
    fun identity(exercise: CompletedExercise): String = exercise.catalogConfigurationId?.takeIf(String::isNotBlank)?.let { "configuration:$it" }
        ?: exercise.catalogDefinitionId?.takeIf(String::isNotBlank)?.let { "definition:$it" }
        ?: "name:${normalizeExerciseIdentityToken(exercise.exerciseName)}"
    fun choices(logs: List<WorkoutLog>): List<ExerciseProgressChoice> = logs.flatMap { log -> log.completedExercises.map { log.id to it } }
        .groupBy { identity(it.second) }.map { (id, rows) ->
            val exercise = rows.last().second
            ExerciseProgressChoice(id, exercise.exerciseName, listOfNotNull(exercise.variantName,
                exercise.selectedAspects?.values?.joinToString(" · ")?.takeIf(String::isNotBlank)).joinToString(" · "), rows.map { it.first }.distinct().size)
        }.sortedWith(compareByDescending<ExerciseProgressChoice> { it.sessions }.thenBy { it.name })
    private data class Row(val set: CompletedSet, val mode: LoadModeV2, val unit: UnitModeV2, val load: Double,
        val reps: Int, val amount: Double, val normalizedLoad: Double, val bodyKnown: Boolean, val lineage: String, val context: String)
    private fun row(exercise: CompletedExercise, set: CompletedSet): Row? {
        val payload = set.recordedPayloadV3; val homologated = set.homologatedResultV3; val outcome = set.setOutcomeV2
        if (set.skipped || set.isWarmup || payload?.skipped == true || set.isFailedSet || payload?.failedSet == true || homologated?.failedSet == true || outcome?.failedSet == true) return null
        val mode = payload?.loadInputMode ?: homologated?.loadMode ?: outcome?.loadMode ?: LoadModeV2.LOAD
        val unit = payload?.unitMode ?: homologated?.unitMode ?: outcome?.unitMode ?: when {
            set.distanceKm != null -> UnitModeV2.DISTANCE; set.timeSeconds != null -> UnitModeV2.TIME; else -> UnitModeV2.REPS }
        if (unit == UnitModeV2.CUSTOM) return null
        val reps = payload?.completedReps ?: set.reps
        val load = if (mode == LoadModeV2.ASSISTED) payload?.assistedLoad ?: set.weight else payload?.externalLoad ?: set.weight
        val amount = when (unit) {
            UnitModeV2.TIME -> (payload?.durationSeconds ?: set.timeSeconds)?.toDouble()
            UnitModeV2.DISTANCE -> set.distanceKm?.times(1000.0) ?: homologated?.actualValue ?: outcome?.actualValue
            else -> reps.toDouble()
        } ?: return null
        if (!load.isFinite() || load < 0 || !amount.isFinite() || amount <= 0 || (unit == UnitModeV2.REPS && mode == LoadModeV2.LOAD && load <= 0)) return null
        // No reinterpretar reps parciales antiguas como repeticiones completas.
        if (unit == UnitModeV2.REPS && (set.isPartial || (payload?.partialReps ?: set.partialReps ?: 0) > 0)) return null
        val entry = SetEntryV2(exercise.exerciseId, setIndex = set.setIndex ?: 0, loadMode = mode, unitMode = unit,
            actualValue = amount, loggedLoad = load, bodyWeight = payload?.bodyWeightSnapshot, contextKey = "progress")
        val normalized = WorkoutPerformanceHomologationEngine.computeNormalizedLoad(entry)
        val context = listOfNotNull(set.machineBrand, set.tagName, set.side ?: payload?.side).joinToString(" · ")
        val lineage = listOf(identity(exercise), mode.name, unit.name, exercise.selectedAspects.orEmpty().toSortedMap().toString(),
            set.contextProfileId ?: payload?.contextProfileId ?: homologated?.contextKey ?: outcome?.contextKey ?: "legacy",
            exercise.variantName.orEmpty(), exercise.performanceProfileId.orEmpty(), exercise.exerciseDbId.orEmpty(), set.setupProfileId.orEmpty(), set.machineBrand.orEmpty(), set.side ?: payload?.side.orEmpty(), set.tagId.orEmpty(),
            set.subTagIds.sorted().joinToString(","), if (unit != UnitModeV2.REPS) load.toString() else "").joinToString("|")
        return Row(set, mode, unit, load, reps, amount, normalized, payload?.bodyWeightSnapshot?.let { it.isFinite() && it > 0 } == true, lineage, context)
    }
    private fun available(row: Row): Set<ExerciseProgressMetric> = when (row.unit) {
        UnitModeV2.TIME -> setOf(ExerciseProgressMetric.DURATION, ExerciseProgressMetric.SETS)
        UnitModeV2.DISTANCE -> setOf(ExerciseProgressMetric.DISTANCE, ExerciseProgressMetric.SETS)
        else -> buildSet {
            add(ExerciseProgressMetric.MAX_REPS); add(ExerciseProgressMetric.SETS)
            if (row.mode != LoadModeV2.BODYWEIGHT) { add(ExerciseProgressMetric.MAX_WEIGHT); add(ExerciseProgressMetric.BEST_SET) }
            if (row.mode == LoadModeV2.LOAD) add(ExerciseProgressMetric.E1RM)
            if (row.mode == LoadModeV2.LOAD || row.mode == LoadModeV2.LASTRE || row.bodyKnown) add(ExerciseProgressMetric.VOLUME)
        }
    }
    private fun displayNumber(value: Double): String = java.math.BigDecimal.valueOf(value)
        .setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')
    private fun point(log: WorkoutLog, date: LocalDate, rows: List<Row>, metric: ExerciseProgressMetric): ExerciseProgressPoint? {
        val usable = rows.filter { metric in available(it) }; if (usable.isEmpty()) return null
        val first = usable.first(); val descending = !(metric in setOf(ExerciseProgressMetric.MAX_WEIGHT, ExerciseProgressMetric.BEST_SET) && first.mode == LoadModeV2.ASSISTED)
        fun score(row: Row): Double = when (metric) {
            ExerciseProgressMetric.E1RM -> StrengthModel.estimate1RM(row.set.copy(weight = row.load, reps = row.reps))
            ExerciseProgressMetric.MAX_WEIGHT -> row.load
            ExerciseProgressMetric.BEST_SET -> if (row.mode == LoadModeV2.ASSISTED) {
                // La puntuación nativa ordena menor asistencia como mejora y respeta las reps; nunca se rotula e1RM.
                WorkoutPerformanceHomologationEngine.evaluate(SetEntryV2(log.id, setIndex = 0, loadMode = row.mode, unitMode = row.unit,
                    actualValue = row.reps.toDouble(), loggedLoad = row.load, contextKey = row.lineage), null).homologated.metricValue
            } else row.load * row.reps
            ExerciseProgressMetric.MAX_REPS -> row.reps.toDouble()
            ExerciseProgressMetric.VOLUME -> if (row.mode == LoadModeV2.LOAD || row.mode == LoadModeV2.LASTRE) row.load * row.reps else row.normalizedLoad * row.reps
            ExerciseProgressMetric.SETS -> 1.0
            else -> row.amount
        }
        val best = if (metric == ExerciseProgressMetric.BEST_SET && first.mode == LoadModeV2.ASSISTED || descending) usable.maxBy(::score) else usable.minBy(::score)
        val value = if (metric == ExerciseProgressMetric.BEST_SET && first.mode == LoadModeV2.ASSISTED) best.load else if (metric == ExerciseProgressMetric.VOLUME || metric == ExerciseProgressMetric.SETS) usable.sumOf(::score) else score(best)
        if (!value.isFinite() || value <= 0 && !(value == 0.0 && best.mode == LoadModeV2.ASSISTED)) return null
        val loadLabel = when(best.mode) { LoadModeV2.ASSISTED -> "kg de asistencia"; LoadModeV2.LASTRE -> "kg de lastre"; LoadModeV2.BODYWEIGHT -> "peso corporal"; else -> "kg" }
        val detail = when(metric) {
            ExerciseProgressMetric.E1RM -> "${displayNumber(best.load)} kg × ${best.reps}" + (best.set.rir?.let { " @ RIR $it" } ?: best.set.rpe?.let { " @ RPE $it" } ?: " · sin reserva declarada") + " → e1RM ${displayNumber(value)} kg estimados"
            ExerciseProgressMetric.DURATION -> "${displayNumber(best.amount)} s · misma carga registrada"
            ExerciseProgressMetric.DISTANCE -> "${displayNumber(best.amount)} m · misma carga registrada"
            ExerciseProgressMetric.SETS -> "${usable.size} series válidas"
            ExerciseProgressMetric.VOLUME -> "${displayNumber(value)} kg de volumen registrado; no incluye peso corporal sin dato"
            else -> "${displayNumber(best.load)} $loadLabel × ${best.reps} reps"
        }
        val trajectory = best.lineage + when {
            metric == ExerciseProgressMetric.MAX_REPS && best.mode != LoadModeV2.BODYWEIGHT -> "|reps@${best.load}"
            metric == ExerciseProgressMetric.BEST_SET && best.mode == LoadModeV2.ASSISTED -> "|assisted@${best.reps}"
            else -> ""
        }
        return ExerciseProgressPoint(log.id, date, log.sessionName, trajectory, best.context, best.mode, best.unit, value, descending, best.set, usable.size, detail)
    }
    fun build(logs: List<WorkoutLog>, exerciseId: String?, metric: ExerciseProgressMetric = ExerciseProgressMetric.E1RM,
        window: ExerciseProgressWindow = ExerciseProgressWindow.QUARTER, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): ExerciseProgressReport {
        val choices = choices(logs)
        val keys = exerciseId?.takeIf { it.startsWith("keys:") }?.removePrefix("keys:")?.split(",").orEmpty().toSet()
        val exactKeyChoices = logs.flatMap { it.completedExercises }.filter { it.catalogConfigurationId in keys }.map { identity(it) }.toSet()
        val definitionKeyChoices = logs.flatMap { it.completedExercises }.filter { it.catalogDefinitionId in keys }.map { identity(it) }.toSet()
        val matchingAliases = logs.flatMap { it.completedExercises }.filter { exercise ->
            exercise.analyticsExerciseKey() == exerciseId || exercise.catalogConfigurationId?.let { "configuration:$it" } == exerciseId ||
                exercise.catalogDefinitionId?.let { "definition:$it" } == exerciseId || "name:${normalizeExerciseIdentityToken(exercise.exerciseName)}" == exerciseId
        }.map { identity(it) }.toSet()
        val choice = choices.firstOrNull { it.id == exerciseId } ?: choices.firstOrNull { it.id in exactKeyChoices } ?: choices.firstOrNull { it.id in definitionKeyChoices } ?: choices.firstOrNull { it.id in matchingAliases }
            ?: if (exerciseId == null) choices.firstOrNull() else null
        val sessions = logs.mapNotNull { log ->
            val date = runCatching { ActivityLocalDate.fromInstantIsoOrDatePrefix(log.actualDate ?: log.date, zone) }.getOrNull() ?: return@mapNotNull null
            if (date > today) return@mapNotNull null
            val rows = log.completedExercises.filter { identity(it) == choice?.id }.flatMap { exercise -> exercise.sets.mapNotNull { row(exercise, it) } }
            Triple(log, date, rows)
        }.sortedWith(compareBy<Triple<WorkoutLog,LocalDate,List<Row>>> { it.second }.thenBy { it.first.actualDate ?: it.first.date }.thenBy { it.first.id })
        val available = sessions.flatMap { it.third.flatMap { row -> available(row) } }.toSet()
        val selected = metric.takeIf { it in available } ?: ExerciseProgressMetric.entries.firstOrNull { it in available } ?: metric
        fun history(m: ExerciseProgressMetric): List<ExerciseProgressPoint> {
            val raw = sessions.flatMap { (log,date,rows) -> rows.groupBy { it.lineage + when {
                m == ExerciseProgressMetric.MAX_REPS && it.mode != LoadModeV2.BODYWEIGHT -> "|reps@${it.load}"
                m == ExerciseProgressMetric.BEST_SET && it.mode == LoadModeV2.ASSISTED -> "|assisted@${it.reps}"
                else -> ""
            } }.values.mapNotNull { point(log,date,it,m) } }
            val best = mutableMapOf<String,Double>(); var prior: String? = null
            return raw.map { point ->
                val key = point.lineage
                val previous = best[key]
                val pr = previous == null || if(point.higherIsBetter) point.value > previous else point.value < previous
                if(pr) best[key] = point.value
                point.copy(isPr = pr, breaksBefore = prior != null && prior != point.lineage).also { prior = point.lineage }
            }
        }
        val records = available.flatMap { m -> history(m).groupBy { it.lineage }.values.map { points ->
            ExerciseProgressRecord(m, points.first { it.value == if(it.higherIsBetter) points.maxOf { p -> p.value } else points.minOf { p -> p.value } })
        } }
        val start = window.start(today)
        return ExerciseProgressReport(choice, selected, available, history(selected).filter { start == null || it.date >= start }, records)
    }
}
