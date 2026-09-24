package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStepGraphTest {

    private val fullContext = SetupStepContext(nutritionStarted = true)

    @Test
    fun fullRouteContainsFourBlocksMilestonesAndFinalReview() {
        val route = SetupStepGraph.stepIds(fullContext)

        assertEquals(
            setOf(SetupWizardBlock.BASICS, SetupWizardBlock.TRAINING, SetupWizardBlock.NUTRITION,
                SetupWizardBlock.RINGS, SetupWizardBlock.REVIEW),
            route.map(SetupStepGraph::blockOf).toSet(),
        )
        assertTrue(route.containsAll(listOf(
            SetupStepId.MILESTONE_BASICS, SetupStepId.MILESTONE_TRAINING,
            SetupStepId.MILESTONE_NUTRITION, SetupStepId.MILESTONE_RINGS,
        )))
        assertEquals(SetupStepId.REVIEW_ACTIVATE, route.last())
        // Los hitos separan bloques: cada uno va al final de su bloque.
        assertTrue(route.indexOf(SetupStepId.MILESTONE_BASICS) < route.indexOf(SetupStepId.ROUTE))
        assertTrue(route.indexOf(SetupStepId.MILESTONE_TRAINING) < route.indexOf(SetupStepId.NUTRITION_START))
        assertTrue(route.indexOf(SetupStepId.MILESTONE_NUTRITION) < route.indexOf(SetupStepId.RINGS_START))
        assertTrue(route.indexOf(SetupStepId.MILESTONE_RINGS) < route.indexOf(SetupStepId.REVIEW_ACTIVATE))
    }

    @Test
    fun conditionalHomeEquipmentBranchInsertsStepWithoutChangingOtherStepIds() {
        val base = SetupStepGraph.stepIds(fullContext)
        val withHome = SetupStepGraph.stepIds(fullContext.copy(homeEquipmentSelected = true))

        assertTrue(SetupStepId.HOME_EQUIPMENT in withHome)
        assertFalse(SetupStepId.HOME_EQUIPMENT in base)
        assertEquals(
            base.filterNot { it == SetupStepId.HOME_EQUIPMENT },
            withHome.filterNot { it == SetupStepId.HOME_EQUIPMENT },
        )
    }

    @Test
    fun stepIdentifiersStayStableWhenStepsAreInsertedBeforeTheCurrentOne() {
        val before = SetupStepContext()
        val progress = SetupStepProgress.initial(before).at(SetupStepId.WEEKDAYS, before)
        val after = before.copy(homeEquipmentSelected = true, hasTrainingMarks = true)
        val restored = progress.at(progress.currentStepId, after)

        // El índice cambia con las ramas; la identidad del paso no.
        assertEquals(SetupStepId.WEEKDAYS, restored.currentStepId)
        assertNotEquals(progress.stepIndex, restored.stepIndex)
        assertEquals(SetupWizardBlock.TRAINING, restored.block)
    }

    @Test
    fun frequencyBranchesSkipCardioStepsUnlessTheGoalIsMixed() {
        val plain = SetupStepGraph.stepIds(fullContext)
        val mixed = SetupStepGraph.stepIds(fullContext.copy(mixedTraining = true))

        assertFalse(SetupStepId.CARDIO_TYPE in plain)
        assertFalse(SetupStepId.CARDIO_TIME in plain)
        assertTrue(SetupStepId.CARDIO_TYPE in mixed && SetupStepId.CARDIO_TIME in mixed)
        assertEquals(SetupStepId.TRAINING_MAX, SetupStepGraph.next(SetupStepId.SESSION_TIME, fullContext))
    }

    @Test
    fun marksBranchOnlyAddsTrainingMarksWhenTheUserKnowsThem() {
        assertEquals(
            SetupStepId.TRAINING_MARKS,
            SetupStepGraph.next(SetupStepId.TRAINING_MAX, fullContext.copy(hasTrainingMarks = true)),
        )
        assertEquals(
            SetupStepId.PLAN,
            SetupStepGraph.next(SetupStepId.TRAINING_MAX, fullContext.copy(hasTrainingMarks = false)),
        )
    }

    @Test
    fun unknownRecencySkipsHistoryButKeepsSensationsAndDiscomfort() {
        val unknown = SetupStepGraph.stepIds(fullContext.copy(recentTraining = null))
        val trained = SetupStepGraph.stepIds(fullContext.copy(recentTraining = true))

        assertFalse(SetupStepId.RINGS_SESSIONS in unknown)
        assertFalse(SetupStepId.RINGS_AXIAL in unknown)
        assertTrue(unknown.containsAll(listOf(
            SetupStepId.RINGS_MUSCLE_FEELING, SetupStepId.RINGS_ENERGY_FEELING,
            SetupStepId.RINGS_STRUCTURE_FEELING, SetupStepId.RINGS_DISCOMFORT,
        )))
        assertTrue(SetupStepId.RINGS_AXIAL in trained)
    }

    @Test
    fun nutritionProfessionalBranchGoesStraightToResult() {
        val route = SetupStepGraph.stepIds(fullContext.copy(nutritionProfessional = true, nutritionStarted = false))

        assertTrue(SetupStepId.NUTRITION_START in route)
        assertTrue(SetupStepId.NUTRITION_RESULT in route)
        assertFalse(SetupStepId.NUTRITION_SEX in route)
    }

    @Test
    fun ringsSkipActionGoesStraightToTheMilestoneAndReview() {
        val route = SetupStepGraph.stepIds(fullContext.copy(ringsAction = "Dejar sin calibrar"))

        assertEquals(
            listOf(SetupStepId.RINGS_START, SetupStepId.MILESTONE_RINGS, SetupStepId.REVIEW_ACTIVATE),
            route.takeLast(3),
        )
    }

    @Test
    fun milestonesCompleteBlocksAndBackNavigationNeverLosesThem() {
        var progress = SetupStepProgress.initial(fullContext)
        progress = progress.at(SetupStepId.EXPERIENCE, fullContext)
        progress = progress.at(SetupStepId.MILESTONE_BASICS, fullContext)
        progress = progress.at(SetupStepId.ROUTE, fullContext)

        assertEquals(setOf(SetupWizardBlock.BASICS), progress.completedBlocks)
        val previous = SetupStepGraph.previous(SetupStepId.ROUTE, fullContext, progress.visited)
        val rewound = progress.at(previous!!, fullContext)
        assertEquals(SetupStepId.MILESTONE_BASICS, rewound.currentStepId)
        assertEquals(setOf(SetupWizardBlock.BASICS), rewound.completedBlocks)
    }

    @Test
    fun backNeverLandsOnBranchesTheUserNeverVisited() {
        val context = SetupStepContext(hasTrainingMarks = false)
        val progress = SetupStepProgress.initial(context).at(SetupStepId.TRAINING_MAX, context)

        assertEquals(SetupStepId.NAME, SetupStepGraph.previous(SetupStepId.TRAINING_MAX, context, progress.visited))
    }

    @Test
    fun legacyQuestionMappingIsBidirectionalAndMilestonesHaveNoQuestion() {
        for (step in SetupStepId.entries.filterNot(SetupStepGraph::isMilestone)) {
            val question = requireNotNull(SetupStepGraph.questionForStep(step))
            assertEquals(step, SetupStepGraph.stepForQuestion(question))
        }
        assertNull(SetupStepGraph.questionForStep(SetupStepId.MILESTONE_BASICS))
        assertNull(SetupStepGraph.questionForStep(SetupStepId.MILESTONE_TRAINING))
        assertNull(SetupStepGraph.questionForStep(SetupStepId.MILESTONE_NUTRITION))
        assertNull(SetupStepGraph.questionForStep(SetupStepId.MILESTONE_RINGS))
    }

    @Test
    fun migrateFromLegacyMapsCurrentQuestionToTheStableStep() {
        val migrated = SetupStepGraph.migrateFromLegacy(
            WizChatQuestionId.T_DAYS,
            fullContext,
            mapOf(SetupStepId.WEIGHT to SetupAnswerProvenance.USER_DECLARED),
        )

        assertEquals(SetupStepId.DAYS, migrated.currentStepId)
        assertEquals(SetupWizardBlock.TRAINING, migrated.block)
        assertEquals(setOf(SetupWizardBlock.BASICS), migrated.completedBlocks)
        assertEquals(SetupProgressOrigin.MIGRATED_FROM_WIZCHAT, migrated.origin)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, migrated.answers[SetupStepId.WEIGHT])
    }

    @Test
    fun migrateFromLegacyMarksUnplaceableQuestionsAsNotConvertible() {
        val ringsOnly = SetupStepContext(includeTraining = false, includeNutrition = false, includeRings = true)
        val migrated = SetupStepGraph.migrateFromLegacy(WizChatQuestionId.T_DAYS, ringsOnly)

        assertEquals(SetupProgressOrigin.NOT_CONVERTIBLE, migrated.origin)
        // La posición legacy se conserva para no perder nada.
        assertEquals(SetupStepId.DAYS, migrated.currentStepId)
    }
}
