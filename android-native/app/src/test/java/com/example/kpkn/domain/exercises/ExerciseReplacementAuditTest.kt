package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseReplacementAuditTest {
    @Test fun assistedBodyweightReplacementUsesAssistanceInsteadOfBodyweight() {
        val original = Exercise(id = "e", name = "Remo", sets = listOf(ExerciseSet(id = "s", targetReps = 8)))
        val replaced = original.replacedWithCatalogExercise(ExerciseMuscleInfo(
            id = "assisted_pullup", name = "Dominada asistida", equipment = "Peso corporal",
        ))
        assertEquals(LoadModeV2.ASSISTED, replaced.sets.single().loadModeV2)
    }
}
