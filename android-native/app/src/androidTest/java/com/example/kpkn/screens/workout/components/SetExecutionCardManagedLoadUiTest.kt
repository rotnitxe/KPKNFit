package com.example.kpkn.screens.workout.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.RecordActionHolder
import org.junit.Rule
import org.junit.Test

/**
 * H-VERIF: un ejercicio con progresión nativa gestionada y sin carga prescrita pide elegirla,
 * con el rango de repeticiones y la reserva del plan.
 */
class SetExecutionCardManagedLoadUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun managedExerciseWithoutLoadAsksToChooseOneWithRepsAndReserve() {
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = managedExercise(),
                    setIndex = 0,
                    currentSet = managedSet(),
                    ghostSet = null,
                    weightSuggestion = null,
                    initialBodyWeight = 80.0,
                    recordActionHolder = RecordActionHolder(),
                    isActivePage = true,
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        // El rango se muestra con guion largo, tal como lo dibuja la tarjeta.
        composeRule
            .onNodeWithText("Elige una carga para 6–8 reps dejando 2 en reserva y registra la serie.")
            .assertExists()
    }

    private fun managedExercise() = Exercise(
        id = "managed-ex",
        name = "Press de banca",
        trainingMode = TrainingMode.REPS,
        sets = listOf(managedSet()),
        nativeProgressionManaged = true,
    )

    private fun managedSet() = ExerciseSet(
        id = "managed-set",
        targetRepsRange = RepRange(6, 8),
        targetRIR = 2,
        weight = null,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
        intensityMode = IntensityMode.RIR,
    )
}
