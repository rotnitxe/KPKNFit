package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.data.models.SmartLoadBasis
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceMetadata
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLoadModeSuggestionTest {

    @Test
    fun assistedLoadIncreasesAssistanceForHigherRepTargets() {
        val exercise = Exercise(
            id = "pullup-assisted",
            name = "Dominada asistida",
            prFor1RM = PrReference(weight = 20.0, reps = 5),
        )

        val heavierTarget = calculateSuggestedLoad(
            exercise,
            ExerciseSet(id = "set-1", targetReps = 1, loadModeV2 = LoadModeV2.ASSISTED),
        )
        val easierTarget = calculateSuggestedLoad(
            exercise,
            ExerciseSet(id = "set-2", targetReps = 10, loadModeV2 = LoadModeV2.ASSISTED),
        )

        assertTrue((heavierTarget ?: 0.0) < 20.0)
        assertTrue((easierTarget ?: 0.0) > 20.0)
    }

    @Test
    fun bodyweightLoadSuggestionIsZero() {
        val exercise = Exercise(
            id = "pullup-bodyweight",
            name = "Dominada",
            prFor1RM = PrReference(weight = 20.0, reps = 5),
        )

        val suggested = calculateSuggestedLoad(
            exercise,
            ExerciseSet(id = "set-1", targetReps = 5, loadModeV2 = LoadModeV2.BODYWEIGHT),
        )

        assertEquals(0.0, suggested ?: -1.0, 0.0)
    }

    @Test
    fun ballastLoadKeepsPositiveProgression() {
        val exercise = Exercise(
            id = "pullup-weighted",
            name = "Dominada con lastre",
            prFor1RM = PrReference(weight = 20.0, reps = 5),
        )

        val heavierTarget = calculateSuggestedLoad(
            exercise,
            ExerciseSet(id = "set-1", targetReps = 1, loadModeV2 = LoadModeV2.LASTRE),
        )
        val easierTarget = calculateSuggestedLoad(
            exercise,
            ExerciseSet(id = "set-2", targetReps = 10, loadModeV2 = LoadModeV2.LASTRE),
        )

        assertTrue((heavierTarget ?: 0.0) > 20.0)
        assertTrue((easierTarget ?: 0.0) < 20.0)
    }

    @Test
    fun lastreSuggestionDoesNotTreatPrWeightAsBarbellOneRm() {
        val exercise = Exercise(
            id = "dip-lastre",
            name = "Fondo con lastre",
            prFor1RM = PrReference(weight = 100.0, reps = 8),
        )
        val suggested = calculateSuggestedLoad(
            exercise,
            ExerciseSet(
                id = "set-8",
                targetReps = 8,
                loadModeV2 = LoadModeV2.LASTRE,
            ),
        )
        val epleyAsIfOneRm = calculateWeightFrom1RM(100.0, 8)
        assertTrue((suggested ?: 0.0) > 90.0)
        assertTrue((suggested ?: 0.0) > epleyAsIfOneRm + 5.0)
    }

    @Test
    fun smartLoadBasisSelectsTrainingMaxWithoutChangingExplicitSetBases() {
        val exercise = Exercise(
            id = "bench",
            name = "Bench",
            trainingMode = TrainingMode.RM,
            reference1RM = 100.0,
            referenceTrainingMax = 80.0,
            smartLoadBasis = SmartLoadBasis.TRAINING_MAX,
        )
        val smart = ExerciseSet(id = "smart", targetPercentageRM = 75.0)
        val explicitOneRm = smart.copy(id = "one-rm", loadBasis = LoadBasis.PERCENT_1RM)
        val explicitTm = smart.copy(id = "tm", loadBasis = LoadBasis.PERCENT_TM)

        assertEquals(60.0, calculateSuggestedLoad(exercise, smart)!!, 0.0)
        assertEquals(75.0, calculateSuggestedLoad(exercise, explicitOneRm)!!, 0.0)
        assertEquals(60.0, calculateSuggestedLoad(exercise.copy(smartLoadBasis = SmartLoadBasis.ONE_RM), explicitTm)!!, 0.0)
    }

    @Test
    fun smartTrainingMaxUsesExplicitThenRecipeFractionThenNinetyPercent() {
        val base = Exercise(
            id = "squat",
            name = "Squat",
            reference1RM = 200.0,
            smartLoadBasis = SmartLoadBasis.TRAINING_MAX,
        )
        val recipe = base.copy(loadReference = PlanLoadReference(
            kind = PlanLoadReferenceKind.EXERCISE_TM,
            configurationId = "squat",
            quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
            state = PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = 170.0,
            metadata = PlanLoadReferenceMetadata(tmFraction = 0.82),
        ))

        assertEquals(164.0, resolveSmartLoadReference(recipe)!!.kilograms, 0.0)
        assertEquals(SmartLoadReferenceSource.RECIPE_TRAINING_MAX, resolveSmartLoadReference(recipe)!!.source)
        assertEquals(180.0, resolveSmartLoadReference(base)!!.kilograms, 0.0)
        assertEquals(SmartLoadReferenceSource.DEFAULT_TRAINING_MAX, resolveSmartLoadReference(base)!!.source)
        val explicit = recipe.copy(referenceTrainingMax = 175.0)
        assertEquals(175.0, resolveSmartLoadReference(explicit)!!.kilograms, 0.0)
        assertEquals(SmartLoadReferenceSource.EXPLICIT_TRAINING_MAX, resolveSmartLoadReference(explicit)!!.source)
    }
}
