package com.example.kpkn.domain.energy

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.data.models.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class StrengthPlannedEnergyParityTest {
    @Test
    fun `energia conserva la carga y el gasto sin esfuerzo planificado`() {
        val set = ExerciseSet("plan", targetReps = 5)
        val exercise = Exercise("press", "Press", reference1RM = 100.0, sets = listOf(set))
        val resolved = TrainingEnergyEngine.resolvePlannedExternalLoadKg(exercise, set, WeightUnit.KG)
        assertEquals(TrainingEnergyEngine.ResolvedLoadSource.ONE_RM_INTENSITY, resolved.source)
        assertEquals(83.3, resolved.externalLoadKg!!, 0.0)
        assertEquals(88.9, TrainingEnergyEngine.resolvePlannedExternalLoadKg(exercise, set.copy(targetRIR = 0), WeightUnit.KG).externalLoadKg!!, 0.0)
        val session = Session("sesion", "Sesión", exercises = listOf(exercise))
        val explicit = session.copy(exercises = listOf(exercise.copy(sets = listOf(set.copy(weight = 83.3)))))
        val settings = Settings(userVitals = UserVitals(weight = 80.0))
        assertEquals(
            TrainingEnergyEngine.estimatePlannedSession(explicit, settings).totalKcal,
            TrainingEnergyEngine.estimatePlannedSession(session, settings).totalKcal,
        )
    }
}
