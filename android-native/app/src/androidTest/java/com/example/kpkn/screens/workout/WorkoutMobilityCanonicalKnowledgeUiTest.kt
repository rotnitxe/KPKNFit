package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogv2.canonicalJointKnowledge
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem
import com.example.kpkn.screens.workout.components.WorkoutMobilityOverlay
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test

class WorkoutMobilityCanonicalKnowledgeUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun liveMobility_jointAndPatternOverlays_onlyExposeCanonicalIntro() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val catalog = context.assets.open("exercise_catalog_v2.json").bufferedReader().use { reader ->
            ExerciseCatalogV2Loader.decodeApproved(reader.readText())
        }
        val definition = catalog.families
            .asSequence()
            .flatMap { it.definitions.asSequence() }
            .first { definition -> definition.configurations.any { it.profile.jointInvolvement.isNotEmpty() } }
        val configuration = definition.configurations.first { it.profile.jointInvolvement.isNotEmpty() }
        val canonicalJoint = canonicalJointKnowledge(configuration.profile.jointInvolvement.first().jointId)
            ?: error("test fixture joint must have an explicit canonical mapping")
        val exercise = Exercise(
            id = "mobility-ui",
            name = definition.canonicalName,
            catalogDefinitionId = definition.id,
            catalogConfigurationId = configuration.id,
            selectedMovementPattern = configuration.profile.movementPatternId,
        )
        val mobilityItem = WorkoutMobilityChecklistItem(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            mobility = MobilitySeries(id = "mobility", name = "Movilidad guiada"),
            stepKey = "mobility",
        )

        composeRule.setContent {
            MaterialTheme {
                WorkoutMobilityOverlay(
                    exercise = exercise,
                    mobilityItems = listOf(mobilityItem),
                    completedExerciseIds = emptySet(),
                    activeMobilityKey = null,
                    globalTimerMinutes = 1,
                    globalTimerRemainingSeconds = 60,
                    globalTimerRunning = false,
                    onStartGlobalTimer = {},
                    onPauseGlobalTimer = {},
                    onToggleComplete = { _, _ -> },
                    onAddOptionalMobility = {},
                    onClose = {},
                    hazeState = HazeState(),
                    catalog = catalog,
                )
            }
        }

        composeRule.onNodeWithText("Involucramiento Articular del Ejercicio").assertExists()
        composeRule.onNodeWithContentDescription("Tarjeta 1 de", substring = true).performClick()
        composeRule.onNodeWithText(canonicalJoint.name).assertExists()
        composeRule.onNodeWithText(canonicalJoint.description).assertExists()
        composeRule.onNodeWithText("Principal:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("biomechanicalReason", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Drenaje", substring = true).assertDoesNotExist()
    }
}
