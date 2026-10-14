package com.example.kpkn.screens.programdetail.components

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TrainingMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** La dosis de la tarjeta de sesión (SE9): «3 × 40 s», «descanso 3:15» y, por debajo de un minuto, «descanso 45 s». */
class SessionPrescriptionSummaryTest {

    private fun ejercicio(vararg sets: ExerciseSet, descanso: Int? = null) = Exercise(
        id = "ej",
        name = "Ejercicio",
        trainingMode = TrainingMode.REPS,
        sets = sets.toList(),
        restTime = descanso,
    )

    @Test
    fun descansoDeMasDeUnMinutoVaEnMinSS() {
        val ej = ejercicio(ExerciseSet(id = "s1", targetReps = 8), descanso = 195)
        assertEquals("1 × 8 · descanso 3:15", formatExercisePrescription(ej))
    }

    @Test
    fun descansoDeMenosDeUnMinutoVaConSuUnidadConEspacioInseparable() {
        val ej = ejercicio(ExerciseSet(id = "s1", targetReps = 8), descanso = 45)
        assertEquals("1 × 8 · descanso 45 s", formatExercisePrescription(ej))
    }

    @Test
    fun tiempoPorSerieVaConSuUnidad() {
        val sets = (1..3).map { ExerciseSet(id = "s$it", targetDuration = 40) }
        assertEquals("3 × 40 s", formatExercisePrescription(ejercicio(*sets.toTypedArray())))
    }

    @Test
    fun sinRepeticionesNiDuracionNoQuedaLaCruzColgando() {
        val sets = (1..3).map { ExerciseSet(id = "s$it") }
        assertEquals("3 series", formatExercisePrescription(ejercicio(*sets.toTypedArray())))
    }
}
