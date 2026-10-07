package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityUnit
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.training.SessionDurationEstimator
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiempo de las piezas de una sesión, MEDIDO con el estimador común (`SessionDurationEstimator`), nunca con una fórmula
 * propia: cada pieza se mide una vez con una sesión mínima y se memoriza por su firma (series, repeticiones, descanso).
 *
 * El estimador es aditivo por ejercicio salvo el calentamiento general (constante de la sesión con fuerza) y los grupos
 * de superserie (el descanso del grupo se cuenta una vez), así que `constante + Σ piezas` reproduce su resultado; el
 * ensamblado verifica siempre la sesión REAL completa con el estimador (y la corrige si difiere: aproximaciones,
 * movilidad que añada el planificador, etc.).
 *
 * Es solo una memoización de una función pura: no cambia el resultado, solo evita medir miles de veces lo mismo.
 */
internal object TimeModel {

    private val cache = ConcurrentHashMap<String, Int>()

    private fun sets(prefix: String, count: Int, repMax: Int, seconds: Int?): List<ExerciseSet> = List(count) { index ->
        if (seconds != null) {
            ExerciseSet(id = "$prefix-s$index", targetDuration = seconds)
        } else {
            val reps = repMax.coerceAtLeast(1)
            ExerciseSet(id = "$prefix-s$index", targetReps = reps, targetRepsRange = RepRange(reps, reps))
        }
    }

    private fun probe(id: String, count: Int, repMax: Int, seconds: Int?, rest: Int, group: String? = null): Exercise = Exercise(
        id = id,
        name = id,
        sets = sets(id, count, repMax, seconds),
        restTime = rest,
        trainingMode = if (seconds != null) TrainingMode.TIME else TrainingMode.REPS,
        supersetId = group,
        supersetGroupRef = group,
    )

    private fun measure(
        exercises: List<Exercise>,
        groups: List<SupersetGroup> = emptyList(),
        parts: List<SessionPart> = emptyList(),
    ): Int = SessionDurationEstimator.estimate(
        Session(id = "t", name = "t", exercises = exercises, supersetGroups = groups, parts = parts),
    ).totalSeconds

    /** Calentamiento general de una sesión con fuerza (constante del estimador), deducido midiendo dos ejercicios y su suma. */
    val base: Int by lazy {
        val a = probe("a", 1, 10, null, 90)
        val b = probe("b", 1, 10, null, 90)
        measure(listOf(a)) + measure(listOf(b)) - measure(listOf(a, b))
    }

    /** Segundos que añade un ejercicio de fuerza suelto a una sesión (preparación, series y descansos entre series). */
    fun exerciseSeconds(sets: Int, repMax: Int, seconds: Int?, rest: Int): Int =
        cache.getOrPut("e:$sets:$repMax:${seconds ?: 0}:$rest") {
            measure(listOf(probe("x", sets, repMax, seconds, rest))) - base
        }

    /** Segundos de dos ejercicios en superserie (los descansos del grupo se cuentan una sola vez). */
    fun pairSeconds(
        setsA: Int, repMaxA: Int, secondsA: Int?,
        setsB: Int, repMaxB: Int, secondsB: Int?,
        between: Int, after: Int,
    ): Int = cache.getOrPut("p:$setsA:$repMaxA:${secondsA ?: 0}:$setsB:$repMaxB:${secondsB ?: 0}:$between:$after") {
        val a = probe("pa", setsA, repMaxA, secondsA, after, group = "g")
        val b = probe("pb", setsB, repMaxB, secondsB, after, group = "g")
        val group = SupersetGroup(id = "g", exerciseOrder = listOf("pa", "pb"), restBetweenExercises = between, restAfterSuperset = after)
        measure(listOf(a, b), listOf(group)) - base
    }

    /** Segundos de un bloque de movilidad de [seconds] segundos en total. */
    fun mobilitySeconds(seconds: Int): Int = cache.getOrPut("m:$seconds") {
        val anchor = probe("anchor", 1, 10, null, 90)
        val series = MobilitySeries(id = "m0", name = "m", sets = 1, durationSeconds = seconds, unit = MobilityUnit.SECONDS)
        val part = SessionPart(id = "mp", name = "m", isMobilityGroup = true, mobilitySeries = listOf(series))
        measure(listOf(anchor), parts = listOf(part)) - measure(listOf(anchor))
    }

    /** Segundos de un bloque de cardio continuo de [seconds] (incluye su preparación). */
    fun cardioSeconds(seconds: Int): Int = cache.getOrPut("c:$seconds") {
        val exercise = Exercise(
            id = "c",
            name = "c",
            cardioDetails = CardioDetails(type = CardioType.WALK, intensity = CardioIntensity.BAJA, targetDurationSeconds = seconds),
        )
        measure(emptyList(), parts = listOf(SessionPart(id = "cp", name = "c", exercises = listOf(exercise), isCardioGroup = true)))
    }

    /** Medición directa de una sesión real (la verdad del ajuste). */
    fun sessionSeconds(session: Session): Int = SessionDurationEstimator.estimate(session).totalSeconds
}
