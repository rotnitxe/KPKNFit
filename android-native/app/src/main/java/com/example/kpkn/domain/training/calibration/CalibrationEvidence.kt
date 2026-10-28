package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.models.CalibrationResponseState
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.VolumeCalibrationResponses
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.ProgramProgressEngine
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

data class RecordedSessionOccurrence(val programId: String, val runId: String, val cycleNumber: Int, val weekId: String, val sessionId: String)

data class CalibrationEvidenceInput(
    val logs: List<WorkoutLog>,
    val programId: String,
    val programRunId: String?,
    val throughDate: LocalDate,
    val zone: ZoneId,
    val lookbackDays: Int = 90,
    /** Sólo si el caller conoce las ocurrencias planificadas de esta misma ventana. */
    val plannedOccurrences: Set<RecordedSessionOccurrence>? = null,
)

data class CalibrationEvidenceSummary(
    val fromDate: LocalDate,
    val throughDate: LocalDate,
    val recordedSessions: Int,
    val observedWeeks: Int,
    val coveredPlannedSessions: Int?,
    val plannedSessions: Int?,
    val ambiguousRecordIds: Int,
    val notes: List<String>,
)

/** Datos de los que [CalibrationEvidence.derive] saca las cuatro respuestas del calibrador (IMP-02, T-90). */
data class CalibrationDerivationInput(
    val logs: List<WorkoutLog>,
    val programId: String,
    val programRunId: String?,
    val throughDate: LocalDate,
    val zone: ZoneId,
    /** Sesiones planificadas en las 4 últimas semanas, si el llamador las conoce; null = sin plan comparable. */
    val plannedSessionsLastFourWeeks: Int? = null,
    val squat1RmKg: Double? = null,
    val bench1RmKg: Double? = null,
    val deadlift1RmKg: Double? = null,
    val bodyWeightKg: Double? = null,
    val feedbacks: List<PostSessionFeedback> = emptyList(),
)

/** Una respuesta derivada (1 a 3) y el dato en español que la sostiene, para mostrarlo entre paréntesis. */
data class DerivedCalibrationAnswer(val value: Int, val detail: String)

data class DerivedCalibration(
    val technique: DerivedCalibrationAnswer,
    val consistency: DerivedCalibrationAnswer,
    val strength: DerivedCalibrationAnswer,
    val mobility: DerivedCalibrationAnswer,
)

enum class CalibrationTrait(val label: String) {
    TECHNIQUE("Técnica"), CONSISTENCY("Constancia"), STRENGTH("Fuerza"), MOBILITY("Movilidad"),
}

/**
 * Respuesta con su origen. El origen reutiliza [CalibrationResponseState] (que ya trae DERIVED): no se añaden valores a
 * ningún enum serializado.
 */
data class ResolvedCalibrationAnswer(
    val trait: CalibrationTrait,
    val value: Int,
    val origin: CalibrationResponseState,
    val detail: String? = null,
) {
    /** «Declarada 2/3», «Según tus registros 3/3 (11 de 12 sesiones)» o «Sugerido por KPKN 2/3». */
    fun label(): String = when (origin) {
        CalibrationResponseState.DERIVED -> "Según tus registros $value/3" + (detail?.let { " ($it)" } ?: "")
        CalibrationResponseState.DECLARED -> "Declarada $value/3"
        CalibrationResponseState.IMPORTED -> "Importada $value/3"
        CalibrationResponseState.SUGGESTED -> "Sugerido por KPKN $value/3"
        CalibrationResponseState.UNKNOWN -> "Origen sin confirmar $value/3"
    }
}

/** Describe registros disponibles. No completa cuestionarios ni modifica objetivos a partir de ellos. */
object CalibrationEvidence {
    fun summarize(input: CalibrationEvidenceInput): CalibrationEvidenceSummary {
        require(input.lookbackDays > 0)
        val start = input.throughDate.minusDays(input.lookbackDays.toLong() - 1)
        val scoped = input.logs.filter { it.programId == input.programId &&
            (input.programRunId == null || it.programRunId == input.programRunId) }
        val grouped = scoped.groupBy { it.id }
        val ambiguous = grouped.values.count { it.distinct().size > 1 }
        val observations = grouped.values.mapNotNull { it.distinct().singleOrNull() }.mapNotNull { log ->
            dateOf(log.date, input.zone)?.takeIf { it in start..input.throughDate }?.let { log to it }
        }
        val weeks = observations.map { it.second.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.distinct().size
        val keys = observations.mapNotNull { occurrenceOf(it.first) }.toSet()
        val planned = input.plannedOccurrences?.filter { it.programId == input.programId && (input.programRunId == null || it.runId == input.programRunId) }?.toSet()
        val covered = planned?.count { it in keys }
        val notes = buildList {
            add("${observations.size} registros de sesión en $weeks semanas con registros.")
            if (planned != null && planned.isNotEmpty()) add("$covered de ${planned.size} sesiones planificadas tienen registro comparable.")
            else add("Sin un calendario comparable no se calcula una tasa de cumplimiento.")
            if (observations.isEmpty()) add("No hay registros disponibles de este programa y ejecución en la ventana; esto no demuestra ausencia de entrenamiento.")
            if (ambiguous > 0) add("$ambiguous identificadores con registros contradictorios quedan fuera del resumen.")
        }
        return CalibrationEvidenceSummary(start, input.throughDate, observations.size, weeks,
            covered, planned?.size, ambiguous, notes)
    }

    // ── Respuestas derivadas (IMP-02) ────────────────────────────────────────────────────────────────

    private const val MIN_SESSIONS = 8
    private const val MIN_WEEKS = 4
    private const val RECENT_DAYS = 28L
    private const val JOINT_DAYS = 30L
    private const val MIN_RIR_SETS = 20
    private val REFERENCE_RATIOS = listOf(1.5, 1.0, 1.75)

    /**
     * Las cuatro respuestas (1 a 3) que se deducen de los registros; null si hay menos de 8 sesiones registradas o menos
     * de 4 semanas distintas en 90 días. No guarda nada: la respuesta DECLARADA gana siempre ([resolve]).
     */
    fun derive(input: CalibrationDerivationInput): DerivedCalibration? {
        val summary = summarize(CalibrationEvidenceInput(input.logs, input.programId, input.programRunId, input.throughDate, input.zone))
        if (summary.recordedSessions < MIN_SESSIONS || summary.observedWeeks < MIN_WEEKS) return null
        val observations = observe(input)
        return DerivedCalibration(
            technique = deriveTechnique(observations.map { it.first }),
            consistency = deriveConsistency(observations, input),
            strength = deriveStrength(input),
            mobility = deriveMobility(input),
        )
    }

    /** Una respuesta declarada o importada gana; si no existe (o era solo sugerida), valen las derivadas y, al final, las sugeridas. */
    fun resolve(declared: VolumeCalibrationResponses?, derived: DerivedCalibration?): List<ResolvedCalibrationAnswer> {
        if (declared == null) {
            return derived?.let { d -> CalibrationTrait.entries.map { trait -> fromDerived(trait, d) } }.orEmpty()
        }
        val stored = listOf(
            ResolvedCalibrationAnswer(CalibrationTrait.TECHNIQUE, declared.technique, declared.state),
            ResolvedCalibrationAnswer(CalibrationTrait.CONSISTENCY, declared.consistency, declared.state),
            ResolvedCalibrationAnswer(CalibrationTrait.STRENGTH, declared.strength, declared.state),
            ResolvedCalibrationAnswer(CalibrationTrait.MOBILITY, declared.mobility, declared.state),
        )
        val keepsDeclared = declared.state == CalibrationResponseState.DECLARED || declared.state == CalibrationResponseState.IMPORTED
        return if (keepsDeclared || derived == null) stored else stored.map { fromDerived(it.trait, derived) }
    }

    /** Sesiones por semana más habituales del programa (la moda entre las semanas con sesiones); null si no hay ninguna. */
    fun typicalSessionsPerWeek(program: Program): Int? =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            .map { it.sessions.size }.filter { it > 0 }
            .groupingBy { it }.eachCount().entries.maxWithOrNull(compareBy<Map.Entry<Int, Int>>({ it.value }, { it.key }))?.key

    private fun fromDerived(trait: CalibrationTrait, derived: DerivedCalibration): ResolvedCalibrationAnswer {
        val answer = when (trait) {
            CalibrationTrait.TECHNIQUE -> derived.technique
            CalibrationTrait.CONSISTENCY -> derived.consistency
            CalibrationTrait.STRENGTH -> derived.strength
            CalibrationTrait.MOBILITY -> derived.mobility
        }
        return ResolvedCalibrationAnswer(trait, answer.value, CalibrationResponseState.DERIVED, answer.detail)
    }

    private fun observe(input: CalibrationDerivationInput): List<Pair<WorkoutLog, LocalDate>> {
        val start = input.throughDate.minusDays(89)
        val scoped = input.logs.filter { it.programId == input.programId && (input.programRunId == null || it.programRunId == input.programRunId) }
        return scoped.groupBy { it.id }.values.mapNotNull { it.distinct().singleOrNull() }.mapNotNull { log ->
            dateOf(log.date, input.zone)?.takeIf { it in start..input.throughDate }?.let { log to it }
        }
    }

    private fun deriveConsistency(observations: List<Pair<WorkoutLog, LocalDate>>, input: CalibrationDerivationInput): DerivedCalibrationAnswer {
        val from = input.throughDate.minusDays(RECENT_DAYS - 1)
        val recorded = observations.count { it.second >= from }
        val planned = input.plannedSessionsLastFourWeeks?.takeIf { it > 0 }
        if (planned != null) {
            val ratio = recorded.toDouble() / planned
            val value = when { ratio >= 0.85 -> 3; ratio >= 0.6 -> 2; else -> 1 }
            return DerivedCalibrationAnswer(value, "${minOf(recorded, planned)} de $planned sesiones")
        }
        val perWeek = recorded / 4.0
        val value = when { perWeek >= 3.0 -> 3; perWeek >= 2.0 -> 2; else -> 1 }
        return DerivedCalibrationAnswer(value, "${decimal(perWeek)} sesiones por semana")
    }

    private fun deriveStrength(input: CalibrationDerivationInput): DerivedCalibrationAnswer {
        val body = input.bodyWeightKg?.takeIf { it.isFinite() && it > 0.0 }
            ?: return DerivedCalibrationAnswer(2, "sin peso corporal registrado")
        val lifts = listOf(input.squat1RmKg, input.bench1RmKg, input.deadlift1RmKg)
        val ratios = lifts.mapIndexedNotNull { index, kg ->
            kg?.takeIf { it.isFinite() && it > 0.0 }?.let { (it / body) / REFERENCE_RATIOS[index] }
        }
        if (ratios.isEmpty()) return DerivedCalibrationAnswer(2, "sin marcas de sentadilla, banca ni peso muerto")
        val mean = ratios.average()
        val value = when { mean >= 1.2 -> 3; mean >= 0.75 -> 2; else -> 1 }
        return DerivedCalibrationAnswer(value, "${decimal(mean)}× la referencia")
    }

    private fun deriveTechnique(logs: List<WorkoutLog>): DerivedCalibrationAnswer {
        val sets: List<CompletedSet> = logs.flatMap { it.completedExercises }.flatMap { it.sets }
            .filter { !it.isWarmup && !it.skipped && it.rir != null }
        if (sets.size < MIN_RIR_SETS) return DerivedCalibrationAnswer(2, "pocas series con RIR registrado")
        val missed = sets.count { (it.isFailure || it.isFailedSet) && (it.rir ?: 0) > 0 }
        val fraction = missed.toDouble() / sets.size
        val value = when { fraction < 0.05 -> 3; fraction < 0.15 -> 2; else -> 1 }
        return DerivedCalibrationAnswer(value, "$missed de ${sets.size} series falladas con RIR mayor que 0")
    }

    private fun deriveMobility(input: CalibrationDerivationInput): DerivedCalibrationAnswer {
        val from = input.throughDate.minusDays(JOINT_DAYS - 1)
        val count = input.feedbacks.count { feedback ->
            feedback.muscleFeedback.values.any { it.jointPain } &&
                dateOf(feedback.date, input.zone)?.let { it in from..input.throughDate } == true
        }
        val value = when { count == 0 -> 3; count <= 2 -> 2; else -> 1 }
        return DerivedCalibrationAnswer(value, if (count == 1) "1 molestia articular en 30 días" else "$count molestias articulares en 30 días")
    }

    private fun decimal(value: Double): String = String.format(java.util.Locale.ROOT, "%.1f", value).replace('.', ',')

    fun occurrenceOf(log: WorkoutLog): RecordedSessionOccurrence? {
        if (!log.calendarBreakId.isNullOrBlank()) return null
        val run = log.programRunId ?: return null
        val week = log.weekInstanceId ?: log.weekId ?: return null
        val fromInstance = ProgramProgressEngine.cycleFromInstanceId(week)
        if (log.cycleNumber != null && fromInstance != null && log.cycleNumber != fromInstance) return null
        val cycle = log.cycleNumber ?: fromInstance ?: return null
        if (run.isBlank() || week.isBlank() || log.sessionId.isBlank() || cycle <= 0) return null
        if (log.weekInstanceId != null && log.weekId != null &&
            ProgramProgressEngine.templateWeekIdFromInstance(log.weekId) != ProgramProgressEngine.templateWeekIdFromInstance(log.weekInstanceId)) return null
        return RecordedSessionOccurrence(log.programId, run, cycle, ProgramProgressEngine.templateWeekIdFromInstance(week) ?: week, log.sessionId)
    }

    private fun dateOf(value: String, zone: ZoneId): LocalDate? =
        runCatching { Instant.parse(value).atZone(zone).toLocalDate() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(value) }.getOrNull()
}
