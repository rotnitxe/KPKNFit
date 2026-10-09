package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.domain.onboarding.ArmStyle
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.CardioBase
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compatibilidad del punto de partida: un borrador anterior (sin los campos nuevos) se lee con la misma ruta, la reparación del
 * cursor se hace una vez y es idempotente, y la clave de la vista previa cambia con cada campo nuevo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupStartingPointCompatTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Los campos que añade el punto de partida: un borrador anterior no los trae. */
    private val newFields = setOf("liftMarkReps", "liftVariants", "avoidedExercises", "cardioBase", "armStyle", "maxReps")

    private fun powerliftingDraft(): SetupWizardDraft = SetupWizardDraft()
        .withPlaces(setOf(TrainingPlace.GYM))
        .withGoalProfile(TrainingGoalProfile.POWERLIFTING)
        .copy(experience = SetupExperience.INTERMEDIATE)
        .withLiftMark(LiftMark.SQUAT, 120.0)
        .withLiftMark(LiftMark.BENCH, 90.0)
        .withLiftMark(LiftMark.DEADLIFT, 150.0)

    @Test
    fun aDraftWithoutTheNewFieldsReadsTheSameRouteAndAsksTheVariantsBeforeTheMarks() {
        val draft = powerliftingDraft()
        val encoded = json.encodeToString(SetupWizardDraft.serializer(), draft)
        val withoutNewFields = JsonObject(json.parseToJsonElement(encoded).jsonObject.filterKeys { it !in newFields })
        val legacy = json.decodeFromJsonElement(SetupWizardDraft.serializer(), withoutNewFields)

        assertTrue(legacy.liftMarkReps.isEmpty() && legacy.liftVariants.isEmpty() && legacy.avoidedExercises.isEmpty())
        assertEquals(draft.liftMarks, legacy.liftMarks)
        assertEquals(SetupStepGraph.stepIds(draft.stepContext()), SetupStepGraph.stepIds(legacy.stepContext()))
        // Un borrador antiguo de powerlifting ve ahora la pregunta de variantes, antes de las marcas.
        val steps = SetupStepGraph.stepIds(legacy.stepContext())
        assertTrue(steps.indexOf(SetupStepId.TRAINING_VARIANTS) < steps.indexOf(SetupStepId.TRAINING_MAX))
    }

    @Test
    fun theRepairOfAnOlderCursorLandsOnTheNewStepAndRunsOnlyOnce() {
        val draft = SetupWizardDraft()
            .withPlaces(setOf(TrainingPlace.GYM))
            .withGoalProfile(TrainingGoalProfile.POWERLIFTING)
            .copy(experience = SetupExperience.INTERMEDIATE)
            .withLiftMark(LiftMark.SQUAT, 120.0)
        val context = draft.stepContext()
        val route = SetupStepGraph.stepIds(context)
        // Todo lo anterior a PLAN respondido salvo las variantes (paso nuevo): un borrador de la revisión 4 parado en PLAN.
        val answered = route.takeWhile { it != SetupStepId.PLAN }
            .filter { it != SetupStepId.TRAINING_VARIANTS }
            .associateWith { SetupAnswerProvenance.USER_DECLARED }
        val older = draft.copy(
            stepProgress = SetupStepProgress.initial(context).copy(
                currentStepId = SetupStepId.PLAN,
                answers = answered,
                graphRevision = 4,
            ),
        )

        val once = SetupDraftCompatibility.repair(older)
        assertEquals(SetupStepId.TRAINING_VARIANTS, once.stepProgress.currentStepId)
        assertEquals(SetupStepGraph.REVISION, once.stepProgress.graphRevision)
        // Repetir la reparación no cambia nada: es idempotente.
        assertEquals(once, SetupDraftCompatibility.repair(once))
    }

    @Test
    fun theTrainingKeyOfThePreviewChangesWithEveryNewField() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val viewModel = SetupWizardViewModel(app, SavedStateHandle())
        val base = SetupWizardDraft().withLiftMark(LiftMark.SQUAT, 120.0)
        val key = viewModel.trainingKey(base)
        val changed = listOf(
            base.withLiftMarkReps(LiftMark.SQUAT, 5),
            base.withLiftVariant(LiftMark.SQUAT, "low_bar_back_squat__barbell"),
            base.withAvoidedToggled(AvoidGroup.DEADLIFT),
            base.withCardioBase(CardioBase.LOW),
            base.withArmStyle(ArmStyle.HOOK),
            base.withMaxReps(CapabilitySkill.PULL_UP, 8),
        )
        changed.forEachIndexed { index, draft ->
            assertNotEquals("campo nuevo $index sin efecto en la vista previa", key, viewModel.trainingKey(draft))
        }
    }

    @Test
    fun theRevisionOfTheGraphIsTheOneThatRepairsTheNewRoute() {
        assertEquals(5, SetupStepGraph.REVISION)
    }
}
