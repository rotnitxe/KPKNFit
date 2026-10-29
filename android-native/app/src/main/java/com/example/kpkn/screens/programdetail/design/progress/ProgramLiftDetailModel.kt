package com.example.kpkn.screens.programdetail.design.progress

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.exercises.normalizeExerciseIdentityToken
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.training.ExerciseProgress
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.domain.training.progress.LiftTrend
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import com.example.kpkn.domain.training.progress.isWorkingSet
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Modelo de la hoja de detalle de un ejercicio (lienzo v6, g-lift): la línea de e1RM por sesión con la ventana de estancamiento, las
 * mejores series y una lectura que solo junta hechos registrados (carga, repeticiones, RIR y fase del plan). Sin Compose.
 */

/** Una sesión de un ejercicio: su fecha, su semana, el mejor e1RM y la serie que lo dio. Lo mismo que cuenta [LiftTrendEngine]. */
internal data class LiftSession(val date: LocalDate, val weekId: String?, val e1rm: Double, val set: CompletedSet)

/** Un punto de la línea: el e1RM de la sesión y, en la primera sesión de cada semana, la etiqueta de la semana («S3»). */
internal data class LiftDetailPoint(val e1rmKg: Double, val weekLabel: String?)

/** Una de las mejores series: el día («Hoy», «Lun 20»), la serie («100 kg × 8 · RIR 2») y su e1RM. */
internal data class LiftBestSet(val dateLabel: String, val detail: String, val e1rmKg: Double)

/** Dónde está el ejercicio en el plan, para «Cambiar ejercicio»: la sesión y el ejercicio que se sustituyen. */
internal data class LiftSwapTarget(val sessionId: String, val exerciseId: String)

/** Todo lo que pinta la hoja de un ejercicio. [shadeFrom] es el índice del primer punto de la ventana sombreada; null = sin ventana. */
internal data class ProgressLiftDetail(
    val key: String,
    val name: String,
    val status: LiftTrendStatus,
    val title: String,
    val summary: String?,
    val points: List<LiftDetailPoint>,
    val shadeFrom: Int?,
    val bestSets: List<LiftBestSet>,
    val reading: String?,
    val swapTarget: LiftSwapTarget?,
)

/**
 * Las sesiones de cada ejercicio de [logs], por su identidad ([ExerciseProgress.identity]), en orden de fecha: una por registro con el
 * mejor e1RM de sus series de trabajo con carga. Mismo orden y mismo criterio que [LiftTrendEngine], para que los puntos coincidan.
 */
internal fun liftSessionsOf(logs: List<WorkoutLog>): Map<String, List<LiftSession>> {
    val dated = logs.mapNotNull { log -> runCatching { LocalDate.parse(log.date.take(10)) }.getOrNull()?.let { log to it } }
        .sortedWith(compareBy<Pair<WorkoutLog, LocalDate>> { it.second }.thenBy { it.first.id })
    val result = LinkedHashMap<String, MutableList<LiftSession>>()
    for ((log, date) in dated) {
        val best = LinkedHashMap<String, Pair<Double, CompletedSet>>()
        for (exercise in log.completedExercises) {
            val key = ExerciseProgress.identity(exercise)
            for (set in exercise.sets) {
                if (!set.isWorkingSet() || set.weight <= 0.0) continue
                val e1rm = StrengthModel.estimate1RM(set)
                if (!e1rm.isFinite() || e1rm <= 0.0) continue
                val current = best[key]
                if (current == null || e1rm > current.first) best[key] = e1rm to set
            }
        }
        for ((key, pair) in best) result.getOrPut(key) { mutableListOf() } += LiftSession(date, log.weekId, pair.first, pair.second)
    }
    return result
}

/**
 * La hoja de [row]: título y explicación según el estado, la línea con las semanas, las mejores series (la última sesión y, si es otra,
 * la mejor de las anteriores), la lectura y dónde cambiar el ejercicio. [weekNumberOf] da el número de una semana del programa;
 * [deloadWeekNumber] la próxima descarga, si la hay.
 */
internal fun buildLiftDetail(
    row: ProgressLiftRow,
    trend: LiftTrend,
    sessions: List<LiftSession>,
    weekNumberOf: (String?) -> Int?,
    deloadWeekNumber: Int?,
    program: Program,
    currentPosition: Int,
    today: LocalDate,
): ProgressLiftDetail {
    var previousWeek: Int? = null
    val points = sessions.map { session ->
        val week = weekNumberOf(session.weekId)
        val label = if (week != null && week != previousWeek) "S$week" else null
        previousWeek = week
        LiftDetailPoint(session.e1rm, label)
    }
    val last = sessions.lastOrNull()
    val previousBest = sessions.dropLast(1).maxByOrNull { it.e1rm }
    val bestSets = listOfNotNull(last, previousBest?.takeIf { it != last }).map { session ->
        LiftBestSet(progressDayLabel(session.date, today), setDetail(session.set), session.e1rm)
    }
    return ProgressLiftDetail(
        key = row.key,
        name = row.name,
        status = row.status,
        title = liftTitle(trend),
        summary = liftSummary(sessions),
        points = points,
        shadeFrom = if (row.status == LiftTrendStatus.STAGNANT && points.size >= 3) points.size - 3 else null,
        bestSets = bestSets,
        reading = if (last != null && previousBest != null && previousBest != last) {
            liftReading(last, previousBest, row.status, deloadWeekNumber)
        } else {
            null
        },
        swapTarget = swapTargetOf(program, row.key, currentPosition),
    )
}

/** «Estancado 3 semanas», «Baja», «Sube» o «Sin tendencia». */
private fun liftTitle(trend: LiftTrend): String = when (trend.status) {
    LiftTrendStatus.STAGNANT -> "Estancado ${stalledWeeksOf(trend)} semanas"
    LiftTrendStatus.UP -> "Sube"
    LiftTrendStatus.DOWN -> "Baja"
    LiftTrendStatus.NO_TREND -> "Sin tendencia"
}

/**
 * Explicación del estado con las cifras del cálculo: el mejor e1RM de las 3 últimas sesiones frente al de las 3 anteriores. Con menos de
 * 4 sesiones, cuántas faltan.
 */
private fun liftSummary(sessions: List<LiftSession>): String {
    if (sessions.size < 4) {
        val count = if (sessions.size == 1) "1 sesión" else "${sessions.size} sesiones"
        return "Con $count aún no hay tendencia: hacen falta 4."
    }
    val last = sessions.takeLast(3).maxOf { it.e1rm }
    val previous = sessions.dropLast(3).takeLast(3)
    val prev = previous.maxOf { it.e1rm }
    val before = when (previous.size) {
        1 -> "la anterior"
        else -> "las ${previous.size} anteriores"
    }
    val verb = when {
        last > prev * 1.01 -> "supera al de"
        last < prev * 0.975 -> "queda por debajo del de"
        else -> "no supera al de"
    }
    return "El mejor e1RM de las 3 últimas sesiones (${kgText(last)} kg) $verb $before (${kgText(prev)} kg)."
}

/**
 * Lectura en lenguaje claro: solo si las dos mejores series tienen la misma carga y las mismas repeticiones y su RIR cambia. «Misma carga y
 * mismas repeticiones con un RIR menos: cuesta más que hace dos semanas.» Si está estancado o baja y hay una descarga por delante, se dice.
 */
private fun liftReading(last: LiftSession, before: LiftSession, status: LiftTrendStatus, deloadWeek: Int?): String? {
    val lastRir = last.set.rir ?: return null
    val beforeRir = before.set.rir ?: return null
    if (last.set.weight != before.set.weight || last.set.reps != before.set.reps || lastRir == beforeRir) return null
    val gap = beforeRir - lastRir
    val amount = if (kotlin.math.abs(gap) == 1) "un RIR" else "${kotlin.math.abs(gap)} RIR"
    val days = ChronoUnit.DAYS.between(before.date, last.date)
    val ago = agoText(days)
    val base = if (gap > 0) {
        "Misma carga y mismas repeticiones con $amount menos: cuesta más que $ago."
    } else {
        "Misma carga y mismas repeticiones con $amount más: cuesta menos que $ago."
    }
    val fatigue = if (gap > 0 && deloadWeek != null && status in setOf(LiftTrendStatus.STAGNANT, LiftTrendStatus.DOWN)) {
        " Puede ser fatiga acumulada; la descarga de la semana $deloadWeek debería ayudar."
    } else {
        ""
    }
    return base + fatigue
}

/** «hace dos semanas», «hace una semana», «hace 3 días». */
private fun agoText(days: Long): String {
    if (days < 7) return if (days == 1L) "hace un día" else "hace $days días"
    val weeks = (days / 7).toInt()
    if (weeks == 1) return "hace una semana"
    val words = listOf("", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez")
    return "hace ${words.getOrNull(weeks) ?: weeks.toString()} semanas"
}

/** «100 kg × 8 · RIR 2»; sin RIR registrado, sin esa parte. */
private fun setDetail(set: CompletedSet): String {
    val base = "${kgText(set.weight)} kg × ${set.reps}"
    return set.rir?.let { "$base · RIR $it" } ?: base
}

/** Kilos con coma y sin decimal sobrante: «128», «128,5». */
internal fun kgText(kg: Double): String = formatDecimalEs(kg, 1).removeSuffix(",0")

/**
 * La sesión y el ejercicio del plan que corresponden a la identidad [key] («configuration:…», «definition:…» o «name:…»): primero desde la
 * semana en curso hacia delante y, si no, en cualquier semana. Null si el ejercicio ya no está en el programa.
 */
internal fun swapTargetOf(program: Program, key: String, currentPosition: Int): LiftSwapTarget? {
    val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
    val weeks = ProgramDetailHelpers.getAllWeeks(roadmap, program)
    val ordered = if (currentPosition in weeks.indices) weeks.drop(currentPosition) + weeks.take(currentPosition) else weeks
    for (week in ordered) {
        for (session in week.sessions) {
            val exercise = session.allExercises().firstOrNull { exercise ->
                when {
                    key.startsWith("configuration:") -> exercise.catalogConfigurationId == key.removePrefix("configuration:")
                    key.startsWith("definition:") -> exercise.catalogConfigurationId.isNullOrBlank() &&
                        exercise.catalogDefinitionId == key.removePrefix("definition:")
                    key.startsWith("name:") -> normalizeExerciseIdentityToken(exercise.name) == key.removePrefix("name:")
                    else -> false
                }
            }
            if (exercise != null) return LiftSwapTarget(session.id, exercise.id)
        }
    }
    return null
}
