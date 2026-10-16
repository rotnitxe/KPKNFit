package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ER1e: aplicar reglas a un ejercicio en modo RM no debe borrar la intensidad que la regla fija. Antes, la normalización del modo RM
 * (`normalizeSet`) ponía a null el RPE y el RIR recién escritos por la regla. Mismo criterio que SE11 (`normalizedForEditorSave`).
 */
class SessionEditorRulesRmPreservationTest {

    private val squatInfo = ExerciseMuscleInfo(
        id = "squat",
        name = "Back Squat",
        force = "Empuje",
        involvedMuscles = listOf(InvolvedMuscle("cuádriceps", MuscleRole.PRIMARY)),
    )

    private val exerciseIndex = mapOf(squatInfo.id.lowercase() to squatInfo)

    private fun rmSession(): Session = Session(
        id = "session-rm",
        name = "Sesion",
        exercises = listOf(
            Exercise(
                id = "exercise-rm",
                name = "Back Squat",
                exerciseDbId = "squat",
                trainingMode = TrainingMode.RM,
                sets = listOf(ExerciseSet(id = "set-1", targetPercentageRM = 80.0)),
            ),
        ),
    )

    @Test
    fun aplicarReglas_enModoRM_conservaElRpeQueLaReglaFija() {
        val result = SessionEditorRulesEngine.applyDefaults(
            session = rmSession(),
            defaults = SessionEditorRuleDefaults(setCount = 2, reps = 6, rpe = 7.5, intensityType = DefaultIntensityType.RPE),
            partId = null,
            exerciseIndex = exerciseIndex,
        )

        val sets = result.exercises.single().sets
        assertEquals(2, sets.size)
        sets.forEach { set ->
            assertEquals(6, set.targetReps)
            assertEquals(7.5, set.targetRPE!!, 1e-9)
        }
    }

    @Test
    fun aplicarReglas_enModoRM_conservaElRirQueLaReglaFija() {
        val result = SessionEditorRulesEngine.applyDefaults(
            session = rmSession(),
            defaults = SessionEditorRuleDefaults(setCount = 2, reps = 6, rpe = 2.0, intensityType = DefaultIntensityType.RIR),
            partId = null,
            exerciseIndex = exerciseIndex,
        )

        val sets = result.exercises.single().sets
        assertEquals(2, sets.size)
        sets.forEach { set ->
            assertEquals(2, set.targetRIR)
        }
    }

    /**
     * ER1f: aplicar reglas solo cambia lo que la regla fija. La serie de partida está al 80 % RM: el %RM no es de la regla, así que
     * debe sobrevivir (antes `applyDefaults` lo ponía a null y `normalizeSet` lo devolvía a 75).
     */
    @Test
    fun aplicarReglas_enModoRM_conservaElPorcentajeRMDeLaSerie() {
        val result = SessionEditorRulesEngine.applyDefaults(
            session = rmSession(),
            defaults = SessionEditorRuleDefaults(setCount = 1, reps = 6, rpe = 7.5, intensityType = DefaultIntensityType.RPE),
            partId = null,
            exerciseIndex = exerciseIndex,
        )

        assertEquals(80.0, result.exercises.single().sets.single().targetPercentageRM!!, 1e-9)
    }
}
