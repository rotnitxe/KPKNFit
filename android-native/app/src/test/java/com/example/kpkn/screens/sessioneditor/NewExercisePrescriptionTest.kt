package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseMuscleInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** SE11: un ejercicio añadido trae las series de su tipo, las mismas que le pondría el generador para su papel de accesorio. */
class NewExercisePrescriptionTest {

    @Test
    fun unAislamientoEntraConTresSeriesDeDiezAQuinceConRirDosYUnMinuto() {
        val info = ExerciseMuscleInfo(id = "abducciones-maquina", name = "Abducciones de pierna · Máquina", type = "Aislamiento")
        val exercise = createExerciseFromInfo(info, emptyList())
        assertEquals("3 × 10–15 · RIR 2 · 1:00", sessionExerciseDose(exercise.sets, exercise.trainingMode, exercise.restTime))
    }

    @Test
    fun unCompuestoEntraConTresSeriesDeSeisADiezConRirDosYDosMinutos() {
        val info = ExerciseMuscleInfo(id = "sentadilla-barra", name = "Sentadilla trasera con barra", type = "Básico")
        val exercise = createExerciseFromInfo(info, emptyList())
        assertEquals("3 × 6–10 · RIR 2 · 2:00", sessionExerciseDose(exercise.sets, exercise.trainingMode, exercise.restTime))
    }
}
