package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.LoadBasis
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ER2a: la carga guardada manda. Solo un cambio de %RM, de referencia o el paso a RM la recalcula, redondeada como el generador
 * (pasos de 2,5 kg). Normalizar, guardar, crear una serie o una superserie no la tocan.
 */
class SessionEditorRmLoadTest {

    /** Sentadilla del generador: 4 × 4–6 al 80 % de un 1RM de 120 kg, con la carga que escribe el generador (95 kg). */
    private fun generatedSquat(weight: Double? = 95.0): Exercise = Exercise(
        id = "squat",
        name = "Sentadilla trasera con barra alta",
        trainingMode = TrainingMode.RM,
        reference1RM = 120.0,
        sets = List(4) { index ->
            ExerciseSet(
                id = "squat-$index",
                targetReps = 6,
                targetRepsRange = RepRange(4, 6),
                targetRIR = 2,
                intensityMode = IntensityMode.SOLO_RM,
                targetPercentageRM = 80.0,
                weight = weight,
                loadBasis = LoadBasis.PERCENT_1RM,
            )
        },
    )

    private val generatedWeights = listOf(95.0, 95.0, 95.0, 95.0)

    private fun List<ExerciseSet>.weights(): List<Double?> = map { it.weight }

    @Test
    fun normalizarNoPisaLaCargaGuardadaDelGenerador() {
        assertEquals(generatedWeights, generatedSquat().normalizeExercise().sets.weights())
    }

    @Test
    fun elGuardadoDelEditorDejaLas95KgDelGenerador() {
        val session = Session(id = "pierna-a", name = "Pierna A", exercises = listOf(generatedSquat()))
        assertEquals(generatedWeights, session.normalizedForEditorSave().exercises.single().sets.weights())
    }

    @Test
    fun sinCargaGuardadaLaRellenaConElRedondeoDelGenerador() {
        // 120 × 80 % = 96: el generador escribe 95 (pasos de 2,5). Con los pasos de 0,25 del cálculo daría 96.
        assertEquals(generatedWeights, generatedSquat(weight = null).normalizeExercise().sets.weights())
    }

    @Test
    fun sinReferenciaLaCargaGuardadaSeConserva() {
        assertEquals(generatedWeights, generatedSquat().copy(reference1RM = null).normalizeExercise().sets.weights())
    }

    @Test
    fun cambiarElPorcentajeDeUnaSerieRecalculaConRedondeoDe2Con5() {
        val before = generatedSquat()
        val after = before.copy(
            sets = before.sets.mapIndexed { index, set -> if (index == 0) set.copy(targetPercentageRM = 85.0) else set },
        )
        // 120 × 85 % = 102: el generador escribe 102,5. La serie que no cambió conserva sus 95 kg.
        assertEquals(listOf(102.5, 95.0, 95.0, 95.0), after.withExplicitRmLoads(before).sets.weights())
    }

    @Test
    fun cambiarLaReferenciaRecalculaTodasLasSeriesRmRedondeadas() {
        val before = generatedSquat()
        // 130 × 80 % = 104 → 105.
        assertEquals(listOf(105.0, 105.0, 105.0, 105.0), before.copy(reference1RM = 130.0).withExplicitRmLoads(before).sets.weights())
    }

    @Test
    fun pasarALmodoRmRecalculaLaCargaDeLaPrescripcionAnterior() {
        val before = generatedSquat(weight = 60.0).copy(trainingMode = TrainingMode.REPS)
        assertEquals(generatedWeights, before.copy(trainingMode = TrainingMode.RM).withExplicitRmLoads(before).sets.weights())
    }

    @Test
    fun unaEdicionQueNoEsDeCargaNoLaToca() {
        val before = generatedSquat()
        val after = before.copy(name = "Sentadilla", sets = before.sets.map { it.copy(targetRIR = 1) })
        assertEquals(generatedWeights, after.withExplicitRmLoads(before).sets.weights())
    }

    @Test
    fun laSerieNuevaDeLaFatigaSeCalculaConSuPorcentaje() {
        val squat = generatedSquat()
        val next = createNextSetTemplate(squat, squat.sets.last())
        // 80 % − 4 (6 repeticiones) = 76 % de 120 = 91,2 → 90.
        val normalized = squat.copy(sets = squat.sets + next).normalizeExercise()
        assertEquals(90.0, normalized.sets.last().weight!!, 1e-9)
        assertEquals(generatedWeights, normalized.sets.dropLast(1).weights())
    }
}
