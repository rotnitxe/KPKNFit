package com.example.kpkn.domain.discomfort

import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.DISCOMFORT_KNOWLEDGE_BY_ID
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PROFESSIONAL_ADVICE
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.domain.auge.AugeTtcEngine
import com.example.kpkn.domain.auge.AugeUtils
import com.example.kpkn.domain.training.VolumeCalculator
import java.time.Instant
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Id de una molestia del `DISCOMFORT_CATALOG` (`"knee_patellar"`, `"lumbar"`…). */
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
    val axialLoadFactor: Double? = null,
    val isImpact: Boolean = false,
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
    HEAVY_SETS, LOAD_JUMP, LOW_TECHNIQUE, FAILURE_SETS, IMPACT_OR_UNILATERAL_CHAIN,

    // Esta semana
    WEEKLY_VOLUME_RISE, FREQUENCY, REPEATED_REPORT, NO_DELOAD,
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

// ─── Motor ───────────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Motor puro (sin Android, sin Room, reloj inyectado) que (1) ordena las molestias más probables para el ejercicio
 * o la sesión y (2) explica, con datos reales de hoy y de la semana, por qué una molestia puede estar apareciendo.
 *
 * No diagnostica: solo enseña lo que los números demuestran y propone acciones de entrenamiento. Cada umbral es una
 * constante con su comentario. Los volúmenes semanales se cuentan con [VolumeCalculator] (la misma regla de series
 * efectivas que usa la app).
 */
object DiscomfortCauseEngine {

    // ── Umbrales: ejercicio ↔ zona ──
    /** Aporte mínimo (secundario = 0,5) de un músculo de la zona para decir que el ejercicio «carga la zona». */
    const val ZONE_EXPOSURE_MIN: Double = 0.5

    /** Series efectivas mínimas de un músculo de la zona en UNA sesión para decir que esa sesión «carga la zona». */
    const val ZONE_SESSION_MIN_SETS: Double = 2.0

    // ── Umbrales: hoy ──
    /** Serie «pesada»: carga ≥ 80 % del e1RM de ese ejercicio en el historial… */
    const val HEAVY_LOAD_FRACTION_OF_E1RM: Double = 0.80

    /** …o con 1 repetición en reserva o menos. */
    const val HEAVY_MAX_RIR: Double = 1.0

    /** Series pesadas sobre la zona a partir de las cuales se considera evidencia. */
    const val HEAVY_SETS_MIN: Int = 2

    /** Subida de carga frente a la última sesión del mismo ejercicio que se considera un salto (+7,5 %). */
    const val LOAD_JUMP_MIN_RATIO: Double = 1.075

    /** Técnica (1–10) igual o por debajo de la cual se avisa. */
    const val LOW_TECHNIQUE_MAX: Int = 6

    /** Series al fallo (RIR 0 o marcadas como fallo) a partir de las cuales se considera evidencia. */
    const val FAILURE_SETS_MIN: Int = 1

    /** Ejercicios de impacto o a una pierna seguidos (que cargan la zona) a partir de los cuales se avisa. */
    const val CHAIN_MIN_EXERCISES: Int = 2

    // ── Umbrales: semana ──
    const val DAYS_PER_WEEK: Int = 7
    private const val DAY_MS: Long = 24L * 60L * 60L * 1000L

    /** Semanas anteriores que forman «tu media». */
    const val BASELINE_WEEKS: Int = 4

    /** De esas semanas, las que deben tener trabajo en el músculo para que la media signifique algo. */
    const val BASELINE_MIN_WEEKS_WITH_DATA: Int = 2

    /** Series semanales medias mínimas del músculo (sin ellas, un +300 % sería ruido). */
    const val BASELINE_MIN_SETS: Double = 3.0

    /** Series mínimas esta semana para hablar de subida. */
    const val WEEK_MIN_SETS: Double = 4.0

    /** Subida del volumen semanal frente a la media a partir de la cual se avisa (+25 %). */
    const val WEEKLY_RISE_MIN_FRACTION: Double = 0.25

    /** Sesiones que cargan la zona en 7 días a partir de las cuales se avisa. */
    const val FREQUENCY_MIN_SESSIONS: Int = 3

    /** Misma molestia en sesiones seguidas con el ejercicio: desde 2 se muestra y con 3 es persistente. */
    const val REPEATED_REPORT_MIN_SESSIONS: Int = 2

    /** Igual que `WorkoutViewModel.persistentDiscomfortForExercise(minConsecutiveSessions = 3)`. */
    const val PERSISTENT_MIN_SESSIONS: Int = 3

    /** Semanas de subida sin bajar (necesita datos de semanas 0…5, es decir 6 semanas). */
    const val NO_DELOAD_MIN_WEEKS: Int = 5

    /** Crecimiento total mínimo del volumen entre la semana más antigua y la actual para hablar de «subida» (+10 %). */
    const val NO_DELOAD_MIN_TOTAL_RISE: Double = 1.10

    // ── Ranking de molestias probables ──
    /** Peso de los ejercicios de la sesión que no son el ejercicio en foco. */
    private const val OTHER_EXERCISE_WEIGHT: Double = 0.25

    /** Cada sesión previa con la molestia reportada en este ejercicio suma esto… */
    private const val REPORT_BONUS_PER_SESSION: Double = 1.0

    /** …hasta este número de sesiones. */
    private const val REPORT_BONUS_MAX_SESSIONS: Int = 3

    /** Peso de la coincidencia de articulaciones y de la carga axial frente a la de músculos. */
    private const val ARTICULAR_WEIGHT: Double = 0.5
    private const val AXIAL_WEIGHT: Double = 0.5

    /** Palabras de ejercicios de impacto (saltos, pliometría, carrera, comba…). */
    private val IMPACT_KEYWORDS = listOf(
        "salto", "saltar", "jump", "pliom", "plyo", "burpee", "comba", "cuerda", "sprint", "carrera",
        "correr", "skipping", "rebote",
    )

    /** `true` si el nombre del ejercicio es de impacto; úsalo para rellenar [DiscomfortSessionExercise.isImpact]. */
    fun isImpactExercise(name: String): Boolean {
        val normalized = DiscomfortSearch.normalize(name)
        return IMPACT_KEYWORDS.any { normalized.contains(it) }
    }

    private val searchableCatalog: List<DiscomfortCatalogEntry> = DISCOMFORT_CATALOG.filter { it.id != "none" }

    // ─────────────────────────────────────────────────────────────────────────────────────────────────────────────
    // 1. Molestias más probables
    // ─────────────────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Las [limit] molestias más probables para el ejercicio en foco (o la sesión entera): por músculos y
     * articulaciones cargados, carga axial para columna y cuello, y las que la persona ya reportó con ese ejercicio.
     * Determinista: a igual puntuación manda el orden del catálogo.
     */
    fun rankLikely(
        session: DiscomfortSession,
        history: DiscomfortHistory,
        limit: Int = 3,
    ): List<DiscomfortId> {
        if (limit <= 0) return emptyList()
        val focus = session.focusExercise()
        return searchableCatalog
            .mapIndexed { index, entry -> Triple(index, entry.id, likelihood(entry, session, focus, history)) }
            .filter { it.third > 0.0 }
            .sortedWith(compareBy({ -it.third }, { it.first }))
            .take(limit)
            .map { it.second }
    }

    private fun likelihood(
        entry: DiscomfortCatalogEntry,
        session: DiscomfortSession,
        focus: DiscomfortSessionExercise?,
        history: DiscomfortHistory,
    ): Double {
        var score = 0.0
        if (focus != null) {
            score += exerciseLikelihood(entry, focus)
            session.exercises.filter { it !== focus }.forEach { score += OTHER_EXERCISE_WEIGHT * exerciseLikelihood(entry, it) }
        } else {
            session.exercises.forEach { score += exerciseLikelihood(entry, it) }
        }
        val scope = focus?.let(::listOf) ?: session.exercises
        val reportedSessions = reportedSessionsCount(entry.id, scope, history.logs)
        score += REPORT_BONUS_PER_SESSION * min(reportedSessions, REPORT_BONUS_MAX_SESSIONS)
        return score
    }

    /** Puntuación de una molestia para un ejercicio: 0 si el ejercicio no carga la zona. */
    private fun exerciseLikelihood(entry: DiscomfortCatalogEntry, exercise: DiscomfortSessionExercise): Double {
        if (exposure(entry, exercise) < ZONE_EXPOSURE_MIN) return 0.0
        val contributions = contributionsOf(exercise)
        val related = entry.relatedMuscles
        val coverage = if (related.isEmpty()) 0.0 else related.sumOf { contributions[canonical(it)] ?: 0.0 } / related.size
        val articular = if (entry.relatedArticular.isEmpty()) {
            0.0
        } else {
            entry.relatedArticular.count { it in articularOf(exercise) }.toDouble() / entry.relatedArticular.size
        }
        return coverage + ARTICULAR_WEIGHT * articular + AXIAL_WEIGHT * axial(entry, exercise)
    }

    /**
     * Molestias que la persona ya reportó con el ejercicio en foco (o con la sesión), la más reciente primero:
     * son los chips de «reportes anteriores». Usa los mismos criterios de coincidencia que la regla persistente.
     */
    fun previousReports(
        session: DiscomfortSession,
        history: DiscomfortHistory,
        limit: Int = 3,
    ): List<DiscomfortPreviousReport> {
        val scope = session.focusExercise()?.let(::listOf) ?: session.exercises
        val keys = scope.flatMap { keysOf(it) }.toSet()
        if (keys.isEmpty() || limit <= 0) return emptyList()
        val perDiscomfort = linkedMapOf<DiscomfortId, MutableList<Long>>()
        history.logs.forEach { log ->
            reportedIdsForLog(log, keys).forEach { id ->
                perDiscomfort.getOrPut(id) { mutableListOf() }.add(AugeUtils.logDateMs(log))
            }
        }
        return perDiscomfort.map { (id, dates) ->
            DiscomfortPreviousReport(id, lastReportedAtMs = dates.max(), sessionsCount = dates.size)
        }
            .filter { it.discomfortId in DISCOMFORT_CATALOG_BY_ID }
            .sortedWith(compareBy({ -it.lastReportedAtMs }, { it.discomfortId }))
            .take(limit)
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────────────────────
    // 2. Explicación: hoy y esta semana
    // ─────────────────────────────────────────────────────────────────────────────────────────────────────────────

    /** Evidencias de hoy y de esta semana para [discomfort]; vacío si los datos no demuestran nada. */
    fun explain(
        discomfort: DiscomfortId,
        session: DiscomfortSession,
        history: DiscomfortHistory,
    ): DiscomfortInsight {
        val entry = DISCOMFORT_CATALOG_BY_ID[discomfort]
        if (entry == null || entry.id == "none") return emptyInsight(discomfort)
        val zone = DISCOMFORT_KNOWLEDGE_BY_ID[entry.id]?.zonePhrase ?: "la zona"
        val zoneExercises = session.exercises.filter { exposure(entry, it) >= ZONE_EXPOSURE_MIN }

        val today = listOfNotNull(
            heavySets(zoneExercises, history, zone),
            loadJump(zoneExercises, history),
            lowTechnique(zoneExercises),
            failureSets(zoneExercises),
            impactOrUnilateralChain(entry, session),
        )
        val weekContext = WeekContext.build(entry, session, history)
        val persistentCount = consecutiveReportCount(entry.id, session, history)
        val week = listOfNotNull(
            repeatedReport(persistentCount, session),
            weeklyVolumeRise(entry, weekContext, zone),
            frequency(weekContext, zone),
            noDeload(weekContext),
        )

        val ordered = { list: List<DiscomfortEvidence> ->
            list.sortedWith(compareBy({ -it.strength }, { it.kind.ordinal }))
        }
        val todaySorted = ordered(today)
        val weekSorted = ordered(week)
        val strongest = ordered(todaySorted + weekSorted)
        // Aviso de F2: manda la evidencia de la semana; si no hay, la de hoy.
        val headlineEvidence = weekSorted.firstOrNull() ?: todaySorted.firstOrNull()
        val advice = if (strongest.isEmpty()) {
            emptyList()
        } else {
            strongest.map { it.advice }.distinct().take(MAX_ADVICE_ACTIONS) + PROFESSIONAL_ADVICE
        }
        return DiscomfortInsight(
            discomfortId = entry.id,
            headline = headlineEvidence?.headline,
            today = todaySorted,
            week = weekSorted,
            advice = advice,
            persistent = persistentCount >= PERSISTENT_MIN_SESSIONS,
            strength = headlineEvidence?.strength ?: 0.0,
        )
    }

    /**
     * El aviso de la sesión (F2): de las molestias más probables, la explicación con la evidencia más fuerte y
     * con aviso; `null` si ninguna tiene nada que decir.
     */
    fun sessionNotice(
        session: DiscomfortSession,
        history: DiscomfortHistory,
        limit: Int = 3,
    ): DiscomfortInsight? = rankLikely(session, history, limit)
        .map { explain(it, session, history) }
        .filter { it.headline != null }
        .maxByOrNull { it.strength }

    private const val MAX_ADVICE_ACTIONS: Int = 2

    private fun emptyInsight(id: DiscomfortId) = DiscomfortInsight(
        discomfortId = id, headline = null, today = emptyList(), week = emptyList(),
        advice = emptyList(), persistent = false, strength = 0.0,
    )

    // ── Evidencias de hoy ────────────────────────────────────────────────────────────────────────────────────────

    private fun heavySets(
        zoneExercises: List<DiscomfortSessionExercise>,
        history: DiscomfortHistory,
        zone: String,
    ): DiscomfortEvidence? {
        val perExercise = zoneExercises.map { exercise ->
            val reference = referenceE1rm(exercise, history.logs)
            exercise to workingSets(exercise).count { isHeavy(it, reference) }
        }.filter { it.second > 0 }
        val total = perExercise.sumOf { it.second }
        if (total < HEAVY_SETS_MIN) return null
        val top = perExercise.maxBy { it.second }.first
        val subject = if (perExercise.size == 1) "de ${lower(top.name)}" else "en ${perExercise.size} ejercicios que cargan $zone"
        val text = "${plural(total, "serie pesada", "series pesadas")} $subject"
        return DiscomfortEvidence(
            kind = DiscomfortEvidenceKind.HEAVY_SETS,
            number = total.toDouble(),
            text = text,
            headline = "Hoy llevas $text. Si $zone avisa, deja más repeticiones en reserva.",
            advice = "Deja 2 repeticiones en reserva en las series pesadas de la próxima sesión.",
            strength = min(1.0, 0.5 * total / HEAVY_SETS_MIN),
        )
    }

    private fun loadJump(
        zoneExercises: List<DiscomfortSessionExercise>,
        history: DiscomfortHistory,
    ): DiscomfortEvidence? {
        val jumps = zoneExercises.mapNotNull { exercise ->
            val todayTop = workingSets(exercise).maxOfOrNull { it.weight } ?: return@mapNotNull null
            val previousTop = previousTopWeight(exercise, history) ?: return@mapNotNull null
            if (todayTop <= 0.0 || previousTop <= 0.0) return@mapNotNull null
            val ratio = todayTop / previousTop
            if (ratio < LOAD_JUMP_MIN_RATIO - EPSILON) null else exercise to ratio
        }
        val (exercise, ratio) = jumps.maxByOrNull { it.second } ?: return null
        val percent = (ratio - 1.0) * 100.0
        val text = "+${formatNumber(percent)}${NBSP}% de carga en ${lower(exercise.name)} frente a la última sesión"
        return DiscomfortEvidence(
            kind = DiscomfortEvidenceKind.LOAD_JUMP,
            number = percent,
            text = text,
            headline = "Hoy subiste ${formatNumber(percent)}${NBSP}% la carga en ${lower(exercise.name)}. Una subida grande de golpe suele notarse en las articulaciones.",
            advice = "Vuelve a la carga de la última sesión y sube en saltos más pequeños.",
            strength = min(1.0, 0.5 * (ratio - 1.0) / (LOAD_JUMP_MIN_RATIO - 1.0)),
        )
    }

    private fun lowTechnique(zoneExercises: List<DiscomfortSessionExercise>): DiscomfortEvidence? {
        val worst = zoneExercises
            .filter { (it.technicalQuality ?: Int.MAX_VALUE) <= LOW_TECHNIQUE_MAX }
            .minByOrNull { it.technicalQuality ?: Int.MAX_VALUE } ?: return null
        val quality = worst.technicalQuality ?: return null
        val text = "Técnica de $quality/10 en ${lower(worst.name)}"
        return DiscomfortEvidence(
            kind = DiscomfortEvidenceKind.LOW_TECHNIQUE,
            number = quality.toDouble(),
            text = text,
            headline = "$text. Cuando la técnica baja, la zona recibe más carga de la que le toca.",
            advice = "Baja la carga hasta que la técnica vuelva a 8/10 antes de volver a subir.",
            strength = min(1.0, 0.5 + 0.125 * (LOW_TECHNIQUE_MAX - quality)),
        )
    }

    private fun failureSets(zoneExercises: List<DiscomfortSessionExercise>): DiscomfortEvidence? {
        val perExercise = zoneExercises.map { exercise ->
            exercise to workingSets(exercise).count { isFailure(it) }
        }.filter { it.second > 0 }
        val total = perExercise.sumOf { it.second }
        if (total < FAILURE_SETS_MIN) return null
        val subject = if (perExercise.size == 1) " en ${lower(perExercise.first().first.name)}" else ""
        val text = "${plural(total, "serie al fallo", "series al fallo")}$subject"
        return DiscomfortEvidence(
            kind = DiscomfortEvidenceKind.FAILURE_SETS,
            number = total.toDouble(),
            text = text,
            headline = "Hoy llevas $text. El fallo cansa más la zona que una serie con repeticiones en reserva.",
            advice = "Termina las series con 1 o 2 repeticiones en reserva en vez de ir al fallo.",
            strength = min(1.0, 0.5 * total / FAILURE_SETS_MIN),
        )
    }

    private fun impactOrUnilateralChain(
        entry: DiscomfortCatalogEntry,
        session: DiscomfortSession,
    ): DiscomfortEvidence? {
        var best = emptyList<DiscomfortSessionExercise>()
        var current = mutableListOf<DiscomfortSessionExercise>()
        session.exercises.forEach { exercise ->
            val counts = (exercise.isImpact || exercise.isUnilateral) &&
                exposure(entry, exercise) >= ZONE_EXPOSURE_MIN &&
                workingSets(exercise).isNotEmpty()
            if (counts) {
                current.add(exercise)
                if (current.size > best.size) best = current.toList()
            } else {
                current = mutableListOf()
            }
        }
        if (best.size < CHAIN_MIN_EXERCISES) return null
        val names = best.take(3).joinToString(", ") { lower(it.name) }
        val text = "${best.size} ejercicios seguidos de impacto o a una pierna: $names"
        return DiscomfortEvidence(
            kind = DiscomfortEvidenceKind.IMPACT_OR_UNILATERAL_CHAIN,
            number = best.size.toDouble(),
            text = text,
            headline = "Hoy encadenaste $text.",
            advice = "Separa los ejercicios de impacto o a una pierna con uno de menos impacto o con más descanso.",
            strength = min(1.0, 0.5 * best.size / CHAIN_MIN_EXERCISES),
        )
    }

    // ── Evidencias de la semana ──────────────────────────────────────────────────────────────────────────────────

    private fun repeatedReport(count: Int, session: DiscomfortSession): DiscomfortEvidence? {
        if (count < REPEATED_REPORT_MIN_SESSIONS) return null
        val withWhat = if (session.focusExercise() != null) "con este ejercicio" else "con estos ejercicios"
        val text = "Reportada en las últimas $count sesiones $withWhat"
        val persistent = count >= PERSISTENT_MIN_SESSIONS
        return DiscomfortEvidence(
            kind = DiscomfortEvidenceKind.REPEATED_REPORT,
            number = count.toDouble(),
            text = text,
            headline = if (persistent) {
                "Has reportado esta molestia en $count sesiones seguidas $withWhat."
            } else {
                "Has reportado esta molestia en las $count últimas sesiones $withWhat."
            },
            advice = if (persistent) {
                "Baja un 10 % la carga y prueba una variante que no moleste durante unos días."
            } else {
                "Baja un 10 % la carga en la próxima sesión y observa cómo responde."
            },
            strength = if (persistent) 1.0 else 0.6,
        )
    }

    private fun weeklyVolumeRise(
        entry: DiscomfortCatalogEntry,
        context: WeekContext,
        zone: String,
    ): DiscomfortEvidence? {
        data class Rise(val muscle: String, val fraction: Double)
        val rises = entry.relatedMuscles.map(::canonical).distinct().mapNotNull { muscle ->
            val thisWeek = context.weeklySets.first()[muscle] ?: 0.0
            val prior = (1..BASELINE_WEEKS).map { context.weeklySets.getOrNull(it)?.get(muscle) ?: 0.0 }
            val weeksWithData = prior.count { it > 0.0 }
            val baseline = prior.sum() / BASELINE_WEEKS
            if (weeksWithData < BASELINE_MIN_WEEKS_WITH_DATA || baseline < BASELINE_MIN_SETS || thisWeek < WEEK_MIN_SETS) {
                return@mapNotNull null
            }
            val fraction = thisWeek / baseline - 1.0
            if (fraction < WEEKLY_RISE_MIN_FRACTION - EPSILON) null else Rise(muscle, fraction)
        }
        val top = rises.maxByOrNull { it.fraction } ?: return null
        val percent = top.fraction * 100.0
        val muscle = lower(top.muscle)
        return DiscomfortEvidence(
            kind = DiscomfortEvidenceKind.WEEKLY_VOLUME_RISE,
            number = percent,
            text = "+${formatNumber(percent)}${NBSP}% de series de $muscle sobre tu media",
            headline = "Esta semana llevas un ${formatNumber(percent)}${NBSP}% más de series de $muscle que tu media. " +
                "Si $zone avisa, empieza por ahí.",
            advice = "Baja el volumen de $muscle a tu media unos días, por ejemplo con una serie menos por ejercicio.",
            strength = min(1.0, 0.5 * top.fraction / WEEKLY_RISE_MIN_FRACTION),
        )
    }

    private fun frequency(context: WeekContext, zone: String): DiscomfortEvidence? {
        val sessions = context.zoneSessionsLast7Days
        if (sessions < FREQUENCY_MIN_SESSIONS) return null
        return DiscomfortEvidence(
            kind = DiscomfortEvidenceKind.FREQUENCY,
            number = sessions.toDouble(),
            text = "$sessions sesiones que cargan $zone en 7 días",
            headline = "Esta semana ya van $sessions sesiones que cargan $zone. Si avisa, dale un día más de descanso.",
            advice = "Deja al menos un día libre entre las sesiones que cargan esta zona.",
            strength = min(1.0, 0.5 * sessions / FREQUENCY_MIN_SESSIONS),
        )
    }

    private fun noDeload(context: WeekContext): DiscomfortEvidence? {
        val totals = context.zoneWeeklyTotals
        if (totals.size <= NO_DELOAD_MIN_WEEKS) return null
        val oldest = totals[NO_DELOAD_MIN_WEEKS]
        if (oldest <= 0.0) return null
        val neverDropped = (0 until NO_DELOAD_MIN_WEEKS).all { totals[it] >= totals[it + 1] - EPSILON }
        if (!neverDropped || totals[0] < oldest * NO_DELOAD_MIN_TOTAL_RISE - EPSILON) return null
        return DiscomfortEvidence(
            kind = DiscomfortEvidenceKind.NO_DELOAD,
            number = NO_DELOAD_MIN_WEEKS.toDouble(),
            text = "$NO_DELOAD_MIN_WEEKS semanas seguidas subiendo el volumen sin descarga",
            headline = "Llevas $NO_DELOAD_MIN_WEEKS semanas seguidas subiendo el volumen de esta zona sin una semana de descarga.",
            advice = "Haz una semana de descarga: baja el volumen a la mitad y mantén la técnica.",
            strength = 0.7,
        )
    }

    // ── Semana: volúmenes con la regla de series efectivas de la app ──────────────────────────────────────────────

    private class WeekContext(
        /** Series efectivas por músculo de cada ventana de 7 días: 0 = últimos 7 días (con la sesión de hoy). */
        val weeklySets: List<Map<String, Double>>,
        /** Total de series de los músculos de la zona por ventana. */
        val zoneWeeklyTotals: List<Double>,
        val zoneSessionsLast7Days: Int,
    ) {
        companion object {
            const val WINDOWS: Int = 6

            fun build(entry: DiscomfortCatalogEntry, session: DiscomfortSession, history: DiscomfortHistory): WeekContext {
                val nowMs = history.now.toEpochMilli()
                val today = todayAsLog(session, history.now)
                val allLogs = (history.logs + listOfNotNull(today)).map { it to AugeUtils.logDateMs(it) }
                val zoneMuscles = entry.relatedMuscles.map(::canonical).distinct()
                val windows = (0 until WINDOWS).map { i ->
                    val to = nowMs - i * DAYS_PER_WEEK * DAY_MS
                    val from = to - DAYS_PER_WEEK * DAY_MS
                    allLogs.filter { (_, ms) -> ms > from && ms <= to }.map { it.first }
                }
                val weeklySets = windows.map { logs -> volumeOf(logs, history.exerciseCatalog) }
                val totals = weeklySets.map { sets -> zoneMuscles.sumOf { sets[it] ?: 0.0 } }
                val zoneSessions = windows.first().count { log ->
                    val sets = volumeOf(listOf(log), history.exerciseCatalog)
                    zoneMuscles.any { (sets[it] ?: 0.0) >= ZONE_SESSION_MIN_SETS - EPSILON }
                }
                return WeekContext(weeklySets, totals, zoneSessions)
            }

            private fun volumeOf(logs: List<WorkoutLog>, catalog: List<ExerciseMuscleInfo>): Map<String, Double> =
                if (logs.isEmpty()) {
                    emptyMap()
                } else {
                    VolumeCalculator.calculateCompletedWeeklyMuscleVolume(logs, catalog)
                        .associate { it.muscleName to it.weeklySets }
                }

            /** La sesión en curso como un log más (con los músculos ya resueltos), o `null` si no hay series. */
            private fun todayAsLog(session: DiscomfortSession, now: Instant): WorkoutLog? {
                val exercises = session.exercises.filter { workingSets(it).isNotEmpty() }
                if (exercises.isEmpty()) return null
                return WorkoutLog(
                    id = "discomfort-engine-today",
                    programId = "",
                    sessionId = "",
                    sessionName = "",
                    date = now.toString(),
                    durationMinutes = 0,
                    completedExercises = exercises.map { exercise ->
                        CompletedExercise(
                            exerciseId = exercise.exerciseId,
                            exerciseName = exercise.name,
                            exerciseDbId = exercise.exerciseDbId,
                            canonicalExerciseId = exercise.canonicalExerciseId,
                            effectiveMuscles = exercise.muscles.filter { it.role != MuscleRole.NEUTRALIZER },
                            sets = workingSets(exercise),
                        )
                    },
                )
            }
        }
    }

    // ── Molestia persistente: misma regla que WorkoutViewModel.persistentDiscomfortForExercise ──────────────────

    /**
     * Sesiones seguidas (la más reciente primero) en que se reportó [discomfortId] con el ejercicio en foco (o la
     * sesión). Es la regla de `WorkoutViewModel.persistentDiscomfortForExercise`: cuenta desde la sesión más
     * reciente que tenga ALGUNA molestia reportada con el ejercicio; si la molestia no estaba en ella, 0. Con
     * [PERSISTENT_MIN_SESSIONS] sesiones es persistente.
     */
    fun consecutiveReportCount(
        discomfortId: DiscomfortId,
        session: DiscomfortSession,
        history: DiscomfortHistory,
    ): Int {
        val scope = session.focusExercise()?.let(::listOf) ?: session.exercises
        return consecutiveReports(discomfortId, scope, history.logs)
    }

    private fun consecutiveReports(
        discomfortId: DiscomfortId,
        scope: List<DiscomfortSessionExercise>,
        logs: List<WorkoutLog>,
    ): Int {
        val keys = scope.flatMap { keysOf(it) }.toSet()
        if (keys.isEmpty()) return 0
        val sessions = logs.sortedByDescending { AugeUtils.logDateMs(it) }
            .mapNotNull { log -> reportedIdsForLog(log, keys).takeIf { it.isNotEmpty() } }
        val first = sessions.firstOrNull() ?: return 0
        if (discomfortId !in first) return 0
        var count = 1
        for (next in sessions.drop(1)) {
            if (discomfortId in next) count++ else break
        }
        return count
    }

    /** Sesiones (sin exigir que sean seguidas) con la molestia reportada con el ejercicio: bonus del ranking. */
    private fun reportedSessionsCount(
        discomfortId: DiscomfortId,
        scope: List<DiscomfortSessionExercise>,
        logs: List<WorkoutLog>,
    ): Int {
        val keys = scope.flatMap { keysOf(it) }.toSet()
        if (keys.isEmpty()) return 0
        return logs.count { discomfortId in reportedIdsForLog(it, keys) }
    }

    /**
     * Molestias de un log con esos ejercicios, igual que `idsForLog` del ViewModel: prioriza las marcadas como
     * aún presentes al cerrar la sesión (`stillPresentDiscomfortIds`) cuando coinciden con las reportadas.
     */
    private fun reportedIdsForLog(log: WorkoutLog, matchKeys: Set<String>): Set<String> {
        val reports = log.postExerciseReports.filter { report ->
            report.discomfortIds.any { it != "none" } &&
                (report.exerciseId in matchKeys || report.canonicalExerciseId in matchKeys || report.exerciseDbId in matchKeys)
        }
        if (reports.isEmpty()) return emptySet()
        val reported = reports.flatMap { it.discomfortIds }.filter { it != "none" }.toSet()
        if (reported.isEmpty()) return emptySet()
        val still = log.stillPresentDiscomfortIds.filter { it != "none" }.toSet()
        if (still.isNotEmpty()) {
            val overlap = still.intersect(reported)
            if (overlap.isNotEmpty()) return overlap
        }
        return reported
    }

    // ── Piezas comunes ───────────────────────────────────────────────────────────────────────────────────────────

    private const val EPSILON: Double = 1e-9
    private const val NBSP: Char = ' '

    private fun DiscomfortSession.focusExercise(): DiscomfortSessionExercise? =
        focusExerciseId?.let { id -> exercises.firstOrNull { it.exerciseId == id } }

    private fun keysOf(exercise: DiscomfortSessionExercise): Set<String> = buildSet {
        add(exercise.exerciseId)
        exercise.exerciseDbId?.let(::add)
        exercise.canonicalExerciseId?.let(::add)
        exercise.relativeToCanonicalExerciseId?.let(::add)
    }

    private fun keysOf(exercise: CompletedExercise): Set<String> = buildSet {
        add(exercise.exerciseId)
        exercise.exerciseDbId?.let(::add)
        exercise.canonicalExerciseId?.let(::add)
        exercise.relativeToCanonicalExerciseId?.let(::add)
    }

    private fun canonical(muscle: String): String = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle)

    /** Aporte (0–1) de cada músculo estándar del ejercicio, con la regla de volumen de la app. */
    private fun contributionsOf(exercise: DiscomfortSessionExercise): Map<String, Double> =
        VolumeCalculator.buildPerExerciseMuscleContributions(
            exercise.muscles.filter { it.role != MuscleRole.NEUTRALIZER },
        )

    /** Articulaciones que carga el ejercicio: las de sus músculos con aporte de [ZONE_EXPOSURE_MIN] o más. */
    private fun articularOf(exercise: DiscomfortSessionExercise): Set<ArticularBattery> {
        val contributions = contributionsOf(exercise)
        return exercise.muscles
            .filter { it.role != MuscleRole.NEUTRALIZER }
            .filter { (contributions[canonical(it.muscle)] ?: 0.0) >= ZONE_EXPOSURE_MIN }
            .flatMap { AugeTtcEngine.articularBatteriesFor(it.muscle, it.emphasis) }
            .toSet()
    }

    /** Carga axial que llega a columna o cuello; 0 para el resto de zonas. */
    private fun axial(entry: DiscomfortCatalogEntry, exercise: DiscomfortSessionExercise): Double {
        val spine = entry.relatedArticular.any { it == ArticularBattery.LUMBAR || it == ArticularBattery.CERVICAL }
        return if (spine) (exercise.axialLoadFactor ?: 0.0).coerceIn(0.0, 1.0) else 0.0
    }

    /** Cuánto carga el ejercicio la zona (0–1): su mayor aporte muscular o, en columna y cuello, la carga axial. */
    private fun exposure(entry: DiscomfortCatalogEntry, exercise: DiscomfortSessionExercise): Double {
        val contributions = contributionsOf(exercise)
        val muscular = entry.relatedMuscles.maxOfOrNull { contributions[canonical(it)] ?: 0.0 } ?: 0.0
        return max(muscular, axial(entry, exercise))
    }

    private fun workingSets(exercise: DiscomfortSessionExercise): List<CompletedSet> =
        exercise.sets.filter { !it.isWarmup && !it.skipped && (it.reps > 0 || (it.timeSeconds ?: 0) > 0) }

    /** Repeticiones en reserva efectivas de una serie, o `null` si no hay dato de intensidad. */
    private fun effectiveRir(set: CompletedSet): Double? = when {
        set.isFailure || set.actualIntensityMode == IntensityMode.FAILURE -> 0.0
        set.actualIntensityMode == IntensityMode.RIR && set.actualIntensityValue != null -> set.actualIntensityValue
        set.actualIntensityMode == IntensityMode.RPE && set.actualIntensityValue != null -> 10.0 - set.actualIntensityValue
        set.rir != null -> set.rir.toDouble()
        set.rpe != null -> 10.0 - set.rpe
        else -> null
    }

    private fun isFailure(set: CompletedSet): Boolean = (effectiveRir(set) ?: Double.MAX_VALUE) <= 0.0

    private fun isHeavy(set: CompletedSet, referenceE1rm: Double): Boolean {
        val byLoad = referenceE1rm > 0.0 && set.weight > 0.0 &&
            set.weight / referenceE1rm >= HEAVY_LOAD_FRACTION_OF_E1RM - EPSILON
        val byEffort = (effectiveRir(set) ?: Double.MAX_VALUE) <= HEAVY_MAX_RIR
        return byLoad || byEffort
    }

    /** Mejor e1RM del historial para el mismo ejercicio (0 si no hay): la referencia de «pesada». */
    private fun referenceE1rm(exercise: DiscomfortSessionExercise, logs: List<WorkoutLog>): Double {
        val keys = keysOf(exercise)
        return logs.asSequence()
            .flatMap { it.completedExercises.asSequence() }
            .filter { completed -> keysOf(completed).any { it in keys } }
            .flatMap { it.sets.asSequence() }
            .filter { !it.isWarmup && !it.skipped }
            .maxOfOrNull { StrengthModel.estimate1RM(it) } ?: 0.0
    }

    /** Carga máxima de la última sesión anterior a [DiscomfortHistory.now] con ese ejercicio. */
    private fun previousTopWeight(exercise: DiscomfortSessionExercise, history: DiscomfortHistory): Double? {
        val keys = keysOf(exercise)
        val nowMs = history.now.toEpochMilli()
        return history.logs
            .filter { AugeUtils.logDateMs(it) <= nowMs }
            .sortedByDescending { AugeUtils.logDateMs(it) }
            .firstNotNullOfOrNull { log ->
                log.completedExercises
                    .filter { completed -> keysOf(completed).any { it in keys } }
                    .flatMap { it.sets }
                    .filter { !it.isWarmup && !it.skipped && it.weight > 0.0 }
                    .maxOfOrNull { it.weight }
            }
    }

    // ── Formato en español de producto: coma decimal, espacio inseparable antes de «%» ──────────────────────────

    private fun lower(text: String): String = text.trim().lowercase(Locale.ROOT)

    private fun plural(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"

    /** Entero si no hay decimales relevantes; si no, un decimal con coma («7,5»). */
    internal fun formatNumber(value: Double): String {
        val rounded = (value * 10.0).roundToInt() / 10.0
        return if (rounded == rounded.toInt().toDouble()) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.ROOT, "%.1f", rounded).replace('.', ',')
        }
    }
}
