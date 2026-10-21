package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.domain.discomfort.DiscomfortCauseEngine
import com.example.kpkn.domain.discomfort.DiscomfortHistory
import com.example.kpkn.domain.discomfort.DiscomfortInsight
import com.example.kpkn.domain.discomfort.DiscomfortSession
import com.example.kpkn.domain.discomfort.DiscomfortSessionExercise
import java.time.DayOfWeek
import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/*
 * Lo que el motor de molestias (LV7a) aporta al feedback tras el ejercicio (LV6, B1/B2/B3): los chips destacados, el aviso de una línea
 * y la explicación de cada molestia. Aquí se arman las entradas del motor desde la sesión en vivo (puro, sin Android), se cuenta lo que
 * sale y se guarda en una caché pequeña. Si el motor no demuestra nada, no hay aviso ni chips: la interfaz no inventa.
 */

/** Cuántos días de entrenos pasados se le dan al motor (12 semanas: las 4–6 de su media y las de «molestia persistente»). */
internal const val FEEDBACK_HISTORY_DAYS: Long = 84L

/** Una molestia que la persona ya reportó antes. [lastReportedAtMs] es null si solo se sabe que viene de su última sesión. */
internal data class ReportedDiscomfort(val id: String, val lastReportedAtMs: Long?)

/** Lo que el motor dice para el ejercicio que acaba de terminar. */
internal data class FeedbackInsights(
    /** Las molestias más probables para el ejercicio (chips «Frecuentes en…»), sin las ya reportadas. */
    val likely: List<String> = emptyList(),
    /** Las ya reportadas antes con este ejercicio (o en la última sesión), la más reciente primero. */
    val reported: List<ReportedDiscomfort> = emptyList(),
    /** El aviso de una línea (F2); null si los datos no demuestran nada. */
    val notice: DiscomfortInsight? = null,
) {
    val isEmpty: Boolean get() = likely.isEmpty() && reported.isEmpty() && notice == null

    companion object {
        val Empty = FeedbackInsights()
    }
}

/** Arma lo que el feedback muestra a partir del motor. */
internal object FeedbackInsightsBuilder {
    const val REPORTED_LIMIT: Int = 3
    const val LIKELY_LIMIT: Int = 3

    fun build(
        session: DiscomfortSession,
        history: DiscomfortHistory,
        lastSessionDiscomfortIds: List<String> = emptyList(),
    ): FeedbackInsights {
        val fromExercise = DiscomfortCauseEngine.previousReports(session, history, REPORTED_LIMIT)
            .map { ReportedDiscomfort(it.discomfortId, it.lastReportedAtMs) }
        val known = fromExercise.map { it.id }.toSet()
        val fromLastSession = lastSessionDiscomfortIds
            .filter { it != "none" && it !in known && it in DISCOMFORT_CATALOG_BY_ID }
            .distinct()
            .map { ReportedDiscomfort(it, null) }
        val reported = (fromExercise + fromLastSession).take(REPORTED_LIMIT)
        val reportedIds = reported.map { it.id }.toSet()
        val likely = DiscomfortCauseEngine.rankLikely(session, history, LIKELY_LIMIT + reportedIds.size)
            .filter { it !in reportedIds }
            .take(LIKELY_LIMIT)
        val notice = DiscomfortCauseEngine.sessionNotice(session, history)
        return FeedbackInsights(likely = likely, reported = reported, notice = notice)
    }
}

/** «la reportaste el lunes», «la reportaste ayer», «la reportaste el 3 de septiembre» o, sin fecha, «de tu última sesión». */
internal fun reportedAgoLabel(lastReportedAtMs: Long?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (lastReportedAtMs == null) return "de tu última sesión"
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(lastReportedAtMs).atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo <= 0L -> "la reportaste hoy"
        daysAgo == 1L -> "la reportaste ayer"
        daysAgo < 7L -> "la reportaste el ${weekdayName(day.dayOfWeek)}"
        else -> "la reportaste el ${day.dayOfMonth} de ${monthName(day.month)}"
    }
}

/** La fecha corta de la tarjeta: «hoy», «ayer», «martes», «9 oct» o, sin fecha, «última sesión». Cabe en un chip pequeño. */
internal fun reportedAgoShort(lastReportedAtMs: Long?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (lastReportedAtMs == null) return "última sesión"
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(lastReportedAtMs).atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo <= 0L -> "hoy"
        daysAgo == 1L -> "ayer"
        daysAgo < 7L -> weekdayName(day.dayOfWeek)
        else -> "${day.dayOfMonth} ${monthName(day.month).take(3)}"
    }
}

private fun weekdayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}

private fun monthName(month: Month): String = when (month) {
    Month.JANUARY -> "enero"
    Month.FEBRUARY -> "febrero"
    Month.MARCH -> "marzo"
    Month.APRIL -> "abril"
    Month.MAY -> "mayo"
    Month.JUNE -> "junio"
    Month.JULY -> "julio"
    Month.AUGUST -> "agosto"
    Month.SEPTEMBER -> "septiembre"
    Month.OCTOBER -> "octubre"
    Month.NOVEMBER -> "noviembre"
    Month.DECEMBER -> "diciembre"
}

/** Arma las entradas del motor desde la sesión en vivo. Puro: lo que haga falta de Android llega por lambdas. */
internal object FeedbackDiscomfortInputs {
    /**
     * La sesión en curso para el motor: los ejercicios con series hechas hoy (y los del propio feedback aunque aún no tengan), cada uno
     * con sus series, sus músculos y su técnica. [technicalByExerciseId] es la técnica elegida (el borrador) o la ya guardada.
     */
    fun session(
        exercises: List<Exercise>,
        completedSets: Map<String, CompletedSet>,
        technicalByExerciseId: Map<String, Int>,
        focusExerciseIds: List<String>,
        infoOf: (Exercise) -> ExerciseMuscleInfo?,
        nameOf: (Exercise) -> String,
    ): DiscomfortSession {
        val setsByExercise: Map<String, List<CompletedSet>> = completedSets.entries
            .mapNotNull { (key, set) -> parseCompletedSetKey(key)?.let { it.exerciseId to set } }
            .groupBy({ it.first }, { it.second })
        val sessionExercises = exercises.mapNotNull { exercise ->
            val sets = setsByExercise[exercise.id].orEmpty()
            if (sets.isEmpty() && exercise.id !in focusExerciseIds) return@mapNotNull null
            val info = infoOf(exercise)
            val name = nameOf(exercise)
            DiscomfortSessionExercise(
                exerciseId = exercise.id,
                name = name,
                muscles = exercise.effectiveMuscles?.takeIf { it.isNotEmpty() } ?: info?.involvedMuscles.orEmpty(),
                sets = sets,
                exerciseDbId = exercise.exerciseDbId,
                canonicalExerciseId = exercise.canonicalExerciseId,
                relativeToCanonicalExerciseId = exercise.relativeToCanonicalExerciseId,
                technicalQuality = technicalByExerciseId[exercise.id],
                axialLoadFactor = info?.axialLoadFactor,
                isImpact = DiscomfortCauseEngine.isImpactExercise(name),
                isUnilateral = exercise.isEffectivelyUnilateral(),
            )
        }
        return DiscomfortSession(exercises = sessionExercises, focusExerciseId = focusExerciseIds.singleOrNull())
    }
}

/**
 * Qué se le pide al motor: los ejercicios del feedback, su técnica (solo importa si está en la zona baja, por eso se agrupa) y cuántas
 * series hay hechas (si cambia, lo calculado ya no vale). Es la clave de la caché.
 */
internal data class FeedbackInsightsRequest(
    val exerciseIds: List<String>,
    val technicalBuckets: Map<String, Int>,
    val completedSetsCount: Int,
) {
    companion object {
        /** La técnica solo cambia lo que dice el motor cuando baja de este umbral: por encima, todas valen igual. */
        private const val ABOVE_THRESHOLD = DiscomfortCauseEngine.LOW_TECHNIQUE_MAX + 1

        fun of(exerciseIds: List<String>, technical: Map<String, Int>, completedSetsCount: Int): FeedbackInsightsRequest =
            FeedbackInsightsRequest(
                exerciseIds = exerciseIds,
                technicalBuckets = exerciseIds.associateWith { id ->
                    TechniqueQuality.clamp(technical[id] ?: TechniqueQuality.DEFAULT).coerceAtMost(ABOVE_THRESHOLD)
                },
                completedSetsCount = completedSetsCount,
            )
    }
}

/**
 * Calcula (fuera del hilo principal) y recuerda lo del motor para el feedback. [inputs] arma las entradas del motor para una petición;
 * [lastSessionDiscomfortIds] da las molestias de la última sesión. Una caché de pocas entradas: la pantalla completa y la tarjeta
 * piden lo mismo y el motor corre una sola vez.
 */
internal class FeedbackInsightSource(
    private val inputs: (FeedbackInsightsRequest) -> Pair<DiscomfortSession, DiscomfortHistory>,
    private val lastSessionDiscomfortIds: () -> List<String>,
    private val dispatcher: CoroutineDispatcher,
) {
    private val insightsCache = object : LinkedHashMap<FeedbackInsightsRequest, FeedbackInsights>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FeedbackInsightsRequest, FeedbackInsights>?): Boolean =
            size > MAX_ENTRIES
    }
    private val explainCache = object : LinkedHashMap<Pair<FeedbackInsightsRequest, String>, DiscomfortInsight?>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<FeedbackInsightsRequest, String>, DiscomfortInsight?>?): Boolean =
            size > MAX_ENTRIES * 4
    }

    /** Lo ya calculado para [request], sin esperar. */
    fun peek(request: FeedbackInsightsRequest): FeedbackInsights? = synchronized(insightsCache) { insightsCache[request] }

    /** Los chips y el aviso para [request]. */
    suspend fun load(request: FeedbackInsightsRequest): FeedbackInsights {
        peek(request)?.let { return it }
        val computed = try {
            withContext(dispatcher) {
                val (session, history) = inputs(request)
                FeedbackInsightsBuilder.build(session, history, lastSessionDiscomfortIds())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Los chips y el aviso son un extra: si el motor falla no hay ni uno ni otro, pero el feedback sigue guardándose.
            return FeedbackInsights.Empty
        }
        synchronized(insightsCache) { insightsCache[request] = computed }
        return computed
    }

    /** La explicación de [discomfortId] para la ficha («En tu caso»). Null si los datos no demuestran nada. */
    suspend fun explain(request: FeedbackInsightsRequest, discomfortId: String): DiscomfortInsight? {
        val key = request to discomfortId
        synchronized(explainCache) {
            if (explainCache.containsKey(key)) return explainCache[key]
        }
        val computed = try {
            withContext(dispatcher) {
                val (session, history) = inputs(request)
                DiscomfortCauseEngine.explain(discomfortId, session, history).takeIf { it.hasEvidence }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return null
        }
        synchronized(explainCache) { explainCache[key] = computed }
        return computed
    }

    private companion object {
        const val MAX_ENTRIES = 4
    }
}
