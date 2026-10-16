package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.RepRange
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SE11: un ejercicio añadido trae las series de su tipo, las mismas que le pondría el generador para su papel de accesorio.
 * Port: se miran las series y el descanso; la línea de dosis («3 × 10–15 · RIR 2 · 1:00») es aspecto y no se porta.
 */
class NewExercisePrescriptionTest {

    @Test
    fun unAislamientoEntraConTresSeriesDeDiezAQuinceConRirDosYUnMinuto() {
        val info = ExerciseMuscleInfo(id = "abducciones-maquina", name = "Abducciones de pierna · Máquina", type = "Aislamiento")
        val exercise = createExerciseFromInfo(info, emptyList())
        assertEquals(3, exercise.sets.size)
        exercise.sets.forEach { set ->
            assertEquals(RepRange(10, 15), set.targetRepsRange)
            assertEquals(2, set.targetRIR)
        }
        assertEquals(60, exercise.restTime)
    }

    @Test
    fun unCompuestoEntraConTresSeriesDeSeisADiezConRirDosYDosMinutos() {
        val info = ExerciseMuscleInfo(id = "sentadilla-barra", name = "Sentadilla trasera con barra", type = "Básico")
        val exercise = createExerciseFromInfo(info, emptyList())
        assertEquals(3, exercise.sets.size)
        exercise.sets.forEach { set ->
            assertEquals(RepRange(6, 10), set.targetRepsRange)
            assertEquals(2, set.targetRIR)
        }
        assertEquals(120, exercise.restTime)
    }
}
