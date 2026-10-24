package com.example.kpkn.domain.discomfort

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.time.TrainingDates
import java.time.Instant

typealias DiscomfortId = String

// ─── Entradas (modelos de dominio, sin Room ni Android) ──────────────────────────────────────────────────────────

/**
 * Un ejercicio de la sesión en curso con lo ya hecho en él.
 *
 * @property muscles músculos del ejercicio tal como los resuelve la app (`ExerciseMuscleResolver.effectiveMuscles…`).
 * @property sets series HECHAS hoy (las de calentamiento se ignoran aquí).
 * @property technicalQuality calidad técnica 1–10 del feedback del ejercicio, si ya existe.
 * @property axialLoadFactor carga axial del ejercicio (`ExerciseMuscleInfo.axialLoadFactor`, 0–1).
 * @property isImpact ejercicio de impacto (saltos…); ver [DiscomfortCauseEngine.isImpactExercise].
 * @property isUnilateral ejercicio a una pierna o un brazo (`Exercise.isEffectivelyUnilateral()`).
 */
data class DiscomfortSessionExercise(
    val exerciseId: String,
    val name: String,
    val muscles: List<InvolvedMuscle>,
    val sets: List<CompletedSet> = emptyList(),
    val exerciseDbId: String? = null,
    val canonicalExerciseId: String? = null,
    val relativeToCanonicalExerciseId: String? = null,
    val technicalQuality: Int? = null,
    val isUnilateral: Boolean = false,
)

/**
 * La sesión en curso. [focusExerciseId] es el ejercicio para el que se piden los chips «Frecuentes en <ejercicio>»
 * (y el que acota la regla de molestia persistente); sin él, se mira toda la sesión.
 */
data class DiscomfortSession(
    val exercises: List<DiscomfortSessionExercise>,
    val focusExerciseId: String? = null,
)

/**
 * Historial y reloj. [logs] son los entrenos pasados (la app pasa las últimas 4–6 semanas; para la regla de
 * molestia persistente se usa todo lo que llegue), [exerciseCatalog] resuelve los músculos de los logs que no
 * guardaron `effectiveMuscles`, y [now] se inyecta para que todo sea determinista.
 */
data class DiscomfortHistory(
    val logs: List<WorkoutLog>,
    val now: Instant,
    val exerciseCatalog: List<ExerciseMuscleInfo> = emptyList(),
)

// ─── Salidas ─────────────────────────────────────────────────────────────────────────────────────────────────────

enum class DiscomfortEvidenceKind {
    // Hoy
    REPORTED_HISTORY,

    // Esta semana

}

/**
 * Un dato que demuestra algo. [number] es el número real (series, %, sesiones…), [text] la frase corta para la
 * celda «Hoy» / «Esta semana» (con su cifra), [headline] la línea del aviso de F2 y [advice] la acción asociada.
 * [strength] (0–1) ordena las evidencias entre sí.
 */
data class DiscomfortEvidence(
    val kind: DiscomfortEvidenceKind,
    val number: Double,
    val text: String,
    val headline: String,
    val advice: String,
    val strength: Double,
)

/**
 * Lo que el motor sabe de una molestia en este momento. Sin evidencia suficiente [headline] es `null` y las listas
 * quedan vacías: la interfaz no inventa nada.
 *
 * @property advice una o dos acciones concretas de la evidencia más fuerte y, al final, `PROFESSIONAL_ADVICE`.
 * @property persistent la misma molestia ya cuenta como persistente (3 sesiones seguidas con el ejercicio).
 */
data class DiscomfortInsight(
    val discomfortId: DiscomfortId,
    val headline: String?,
    val today: List<DiscomfortEvidence>,
    val week: List<DiscomfortEvidence>,
    val advice: List<String>,
    val persistent: Boolean,
    val strength: Double,
) {
    val hasEvidence: Boolean get() = today.isNotEmpty() || week.isNotEmpty()
}

/** Una molestia reportada antes con el ejercicio (para el chip «la reportaste el lunes»). */
data class DiscomfortPreviousReport(
    val discomfortId: DiscomfortId,
    val lastReportedAtMs: Long,
    val sessionsCount: Int,
)

/** Recorded reports only. No likely causes, risk thresholds or load advice. */
object RecordedDiscomfortHistory {
    fun previousReports(session: DiscomfortSession, history: DiscomfortHistory, limit: Int = 3): List<DiscomfortPreviousReport> {
        val scope = session.focusExerciseId?.let { id -> session.exercises.filter { it.exerciseId == id } } ?: session.exercises
        val ids = scope.map { it.exerciseId }.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty() || limit <= 0) return emptyList()
        val nowMs = history.now.toEpochMilli()
        // A conflicting duplicate identity is excluded, never selected by input order.
        val logs = history.logs.groupBy { it.id }.values.mapNotNull { versions -> versions.distinct().singleOrNull() }
        val reports = logs.mapNotNull { log ->
            val date = TrainingDates.logDateMs(log).takeIf { it in 1 until nowMs } ?: return@mapNotNull null
            val reported = log.postExerciseReports.filter { it.exerciseId in ids }
                .flatMap { it.discomfortIds }.filter { it != "none" && it in DISCOMFORT_CATALOG_BY_ID }.toSet()
            date to reported
        }
        return reports.flatMap { (date, ids) -> ids.map { it to date } }.groupBy({ it.first }, { it.second })
            .map { (id, dates) -> DiscomfortPreviousReport(id, dates.max(), dates.size) }
            .sortedWith(compareByDescending<DiscomfortPreviousReport> { it.lastReportedAtMs }.thenBy { it.discomfortId }).take(limit)
    }

    fun explain(id: DiscomfortId, session: DiscomfortSession, history: DiscomfortHistory): DiscomfortInsight {
        val report = previousReports(session, history, DISCOMFORT_CATALOG.size).firstOrNull { it.discomfortId == id }
        val facts = report?.let { listOf(DiscomfortEvidence(
            kind = DiscomfortEvidenceKind.REPORTED_HISTORY,
            number = it.sessionsCount.toDouble(),
            text = "${it.sessionsCount} sesiones anteriores con esta molestia registrada para el mismo ejercicio.",
            headline = "Molestia registrada anteriormente", advice = "", strength = 0.0)) }.orEmpty()
        return DiscomfortInsight(id, if (report != null) "Registro anterior" else null,
            emptyList(), facts, emptyList(), false, 0.0)
    }
}
