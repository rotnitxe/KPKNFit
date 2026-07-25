package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorRulesEngineValidationTest {

    @Test
    fun validateBeforeSave_blankNameIsBlocking() {
        val result = SessionEditorRulesEngine.validateBeforeSave(
            draft = Session(id = "s1", name = "  "),
            weekSessions = emptyList(),
            ruleLimits = SessionEditorRuleLimits(),
            exerciseIndex = emptyMap(),
        )
        assertEquals("La sesión debe tener un nombre antes de guardar.", result.blockingError)
    }

    @Test
    fun validateBeforeSave_namedSessionPassesWithoutLegacyLimitWarnings() {
        val draft = Session(
            id = "s1",
            name = "Push",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Bench",
                    exerciseDbId = "bench",
                    sets = listOf(
                        ExerciseSet(
                            id = "set1",
                            targetReps = 8,
                            targetRPE = 9.5,
                            intensityMode = IntensityMode.RPE,
                        ),
                    ),
                ),
            ),
        )
        val result = SessionEditorRulesEngine.validateBeforeSave(
            draft = draft,
            weekSessions = listOf(draft),
            ruleLimits = SessionEditorRuleLimits(maxRPE = 8.0, rigidLimits = true),
            exerciseIndex = emptyMap(),
        )
        assertNull(result.blockingError)
        assertTrue(result.warnings.isEmpty())
    }
}
