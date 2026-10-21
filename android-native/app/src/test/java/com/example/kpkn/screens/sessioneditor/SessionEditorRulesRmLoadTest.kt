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
 * ER2c: aplicar reglas no reescribe la carga RM. Una serie con carga guardada la conserva. La que no la tiene, o la que la regla deja
 * con un %RM que la normalización fija, sale de su %RM con el redondeo del generador (pasos de 2,5 kg), igual que al guardar.
 */
class SessionEditorRulesRmLoadTest {

    /** Sentadilla del generador: 4 × 4–6 al 80 % de un 1RM de 120 kg, con la carga que escribe el generador (95 kg). */
    private fun generatedSquat(): Exercise = Exercise(
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
                weight = 95.0,
                loadBasis = LoadBasis.PERCENT_1RM,
            )
        },
    )

    private fun rirRules(setCount: Int) = SessionEditorRuleDefaults(
        setCount = setCount,
        reps = 6,
        rpe = 2.0,
        intensityType = DefaultIntensityType.RIR,
    )

    @Test
    fun aplicarReglasAUnaSesionDelGeneradorDejaLos95Kg() {
        val session = Session(id = "pierna-a", name = "Pierna A", exercises = listOf(generatedSquat()))
        val result = SessionEditorRulesEngine.applyDefaults(session, rirRules(setCount = 4), partId = null)
        // 120 × 80 % = 96: el cálculo de pasos de 0,25 daría 96; la carga guardada por el generador es 95.
        assertEquals(listOf(95.0, 95.0, 95.0, 95.0), result.exercises.single().sets.map { it.weight })
    }

    @Test
    fun unaSerieSinCargaConSu85PorcientoSaleConElRedondeoDelGenerador() {
        val squat = generatedSquat().copy(sets = listOf(ExerciseSet(id = "squat-0", targetPercentageRM = 85.0)))
        val session = Session(id = "pierna-a", name = "Pierna A", exercises = listOf(squat))
        val result = SessionEditorRulesEngine.applyDefaults(session, rirRules(setCount = 1), partId = null)
        // 120 × 85 % = 102: el generador escribe 102,5 (pasos de 2,5); el cálculo de pasos de 0,25 daría 102.
        assertEquals(102.5, result.exercises.single().sets.single().weight!!, 1e-9)
    }

    @Test
    fun unaSerieSinPorcentajeQueLaReglaPasaAl75SaleConElRedondeoDelGenerador() {
        val press = Exercise(
            id = "bench",
            name = "Press de banca",
            trainingMode = TrainingMode.RM,
            reference1RM = 122.0,
            sets = listOf(ExerciseSet(id = "bench-0", weight = 95.0)),
        )
        val session = Session(id = "pecho", name = "Pecho", exercises = listOf(press))
        val result = SessionEditorRulesEngine.applyDefaults(session, rirRules(setCount = 1), partId = null)
        val set = result.exercises.single().sets.single()
        assertEquals(75.0, set.targetPercentageRM!!, 1e-9)
        // 122 × 75 % = 91,5 → 92,5. Con pasos de 0,25 saldría 91,5.
        assertEquals(92.5, set.weight!!, 1e-9)
    }

    @Test
    fun unaReglaDeIntensidadQueCambiaElPorcentajeA85RecalculaConRedondeoDe2Con5() {
        val session = Session(id = "pierna-a", name = "Pierna A", exercises = listOf(generatedSquat()))
        val changed = SessionEditorRulesEngine.applyGlobalIntensityAdjustment(
            session = session,
            targetMode = IntensityMode.SOLO_RM,
            value = 85.0,
            targetMuscles = null,
            exerciseIndex = emptyMap(),
        ).withExplicitRmLoads(session)
        // Igual que el editor al cambiar el %RM: 120 × 85 % = 102 → 102,5 en las cuatro series.
        assertEquals(listOf(102.5, 102.5, 102.5, 102.5), changed.exercises.single().sets.map { it.weight })
    }
}
