package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupPreviewKind
import com.example.kpkn.domain.onboarding.SetupProgressOrigin
import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-005 / AC-T005-05 — la tabla de reparación de borradores §15.4, caso a
 * caso e IDEMPOTENTE (reparar dos veces == reparar una).
 */
class SetupDraftRepairTableTest {

    private fun baseDraft(): SetupWizardDraft = SetupWizardDraft(
        draftId = "setup-wizard:full",
        commitId = "commit-1",
        ageYears = 30,
        heightCm = 175.0,
        weightKg = 72.0,
        stepProgress = SetupStepProgress.initial(SetupStepContext()).at(SetupStepId.DAYS, SetupStepContext()),
    )

    private fun draftAtReview(goal: SetupGoal, through: SetupStepId): SetupWizardDraft {
        val draft = baseDraft().copy(goal = goal)
        val context = draft.stepContext()
        val route = SetupStepGraph.stepIds(context)
        val confirmed = route.take(route.indexOf(through) + 1)
            .associateWith { SetupAnswerProvenance.USER_DECLARED }
        val review = SetupStepId.REVIEW_ACTIVATE
        val progress = SetupStepProgress(
            block = SetupStepGraph.blockOf(review),
            stepIndex = route.indexOf(review),
            completedBlocks = SetupStepGraph.confirmedBlocks(confirmed, context),
            currentStepId = review,
            visited = route,
            answers = confirmed,
            origin = SetupProgressOrigin.NATIVE,
            graphRevision = SetupStepGraph.REVISION,
            terminal = true,
        )
        return draft.copy(stepProgress = progress)
    }

    private fun assertIdempotent(draft: SetupWizardDraft) {
        val once = SetupDraftCompatibility.repair(draft)
        val twice = SetupDraftCompatibility.repair(once)
        assertEquals("repair debe ser idempotente", once, twice)
    }

    // ─── HEALTH/MIXED: traducidos al perfil de hoy, GOAL en revisión, nada se confirma solo ────

    @Test
    fun legacyHealthAndMixedGoalsBecomeTheProfileOfTodayAndAreFlaggedForReviewWithoutConfirmingAnything() {
        val expected = mapOf(
            SetupGoal.HEALTH to com.example.kpkn.domain.onboarding.TrainingGoalProfile.FUNCTIONAL_HEALTH,
            SetupGoal.MIXED to com.example.kpkn.domain.onboarding.TrainingGoalProfile.STRENGTH_CARDIO,
        )
        for ((legacy, profile) in expected) {
            val draft = baseDraft().copy(goal = legacy)

            val repaired = SetupDraftCompatibility.repair(draft)

            // El objetivo antiguo se lee como el perfil que hoy le corresponde; la persona lo reconfirma al revisar.
            assertEquals("$legacy", profile, repaired.goalProfile)
            assertEquals("$legacy", GoalProfileMapping.setupGoalOf(profile), repaired.goal)
            assertFalse("$legacy ya no es un objetivo legacy", repaired.goal?.isLegacyOnly == true)
            assertTrue(
                "GOAL queda marcado para revisar",
                SetupStepId.GOAL in repaired.stepProgress.pendingReview,
            )
            assertNull(
                "ninguna selección del paso se confirma sola",
                repaired.stepSelections[SetupStepId.GOAL],
            )
            assertNull("GOAL no consta como respondido", repaired.stepProgress.answers[SetupStepId.GOAL])
            assertEquals(
                "las respuestas del resto del bloque no se borran",
                draft.stepProgress.answers,
                repaired.stepProgress.answers,
            )
            assertIdempotent(draft)
        }
    }

    @Test
    fun aLegacyGoalAtReviewReopensAtGoalUntilTheUserChoosesExplicitly() {
        for (legacy in listOf(SetupGoal.HEALTH, SetupGoal.MIXED)) {
            val draft = draftAtReview(legacy, SetupStepId.GOAL)

            val repaired = SetupDraftCompatibility.repair(draft)

            assertEquals(
                GoalProfileMapping.profileOfLegacy(legacy),
                repaired.goalProfile,
            )
            assertEquals("la respuesta legacy debe revisarse antes de continuar", SetupStepId.GOAL,
                repaired.stepProgress.currentStepId)
            assertTrue(SetupStepId.GOAL in repaired.stepProgress.pendingReview)
            assertEquals(draft.stepProgress.answers, repaired.stepProgress.answers)
            assertIdempotent(draft)
        }
    }

    @Test
    fun supportedGoalsAreNeverFlaggedForGoalReview() {
        for (goal in listOf(SetupGoal.STRENGTH, SetupGoal.MUSCLE, SetupGoal.STRENGTH_MUSCLE, SetupGoal.COMPLETE_ATHLETE)) {
            val repaired = SetupDraftCompatibility.repair(baseDraft().copy(goal = goal))
            assertFalse(
                "$goal no debe quedar en revisión",
                SetupStepId.GOAL in repaired.stepProgress.pendingReview,
            )
        }
    }

    // ─── ROUTE retirado del nodo del cursor ────────────────────────────────

    @Test
    fun cursorOnTheRemovedRouteNodeResolvesToTheFirstValidPendingStepAndKeepsHistory() {
        val routeContext = SetupStepContext()
        assertFalse("ROUTE ya no está en la ruta", SetupStepId.ROUTE in SetupStepGraph.stepIds(routeContext))
        val draft = baseDraft().copy(
            // Origen histórico conservado (ruta protocol/recomendado).
            programRoute = SetupProgramRoute.PROTOCOL,
            stepProgress = SetupStepProgress(
                origin = SetupProgressOrigin.NATIVE,
                block = com.example.kpkn.domain.onboarding.SetupWizardBlock.TRAINING,
                currentStepId = SetupStepId.ROUTE,
                visited = listOf(SetupStepId.NAME, SetupStepId.ROUTE),
                answers = mapOf(SetupStepId.EXPERIENCE to SetupAnswerProvenance.USER_DECLARED),
            ),
            wizChat = baseDraft().wizChat.copy(currentQuestionId = WizChatQuestionId.T_ROUTE),
        )

        val repaired = SetupDraftCompatibility.repair(draft)

        assertTrue(
            "el cursor sale del nodo eliminado",
            repaired.stepProgress.currentStepId != SetupStepId.ROUTE,
        )
        assertEquals(
            "el nodo eliminado reanuda en el primer paso válido pendiente",
            SetupStepId.NAME,
            repaired.stepProgress.currentStepId,
        )
        assertTrue(
            "y cae en un paso válido de la ruta",
            repaired.stepProgress.currentStepId in SetupStepGraph.stepIds(repaired.stepContext()),
        )
        assertEquals(
            "el origen histórico de la ruta se conserva",
            SetupProgramRoute.PROTOCOL,
            repaired.programRoute,
        )
        assertTrue(
            "la estela histórica se conserva (el reparador solo añade el paso de reanudación)",
            repaired.stepProgress.visited.take(2) == listOf(SetupStepId.NAME, SetupStepId.ROUTE),
        )
        assertEquals(
            "las respuestas sobreviven",
            SetupAnswerProvenance.USER_DECLARED,
            repaired.stepProgress.answers[SetupStepId.EXPERIENCE],
        )
        assertIdempotent(draft)
    }

    @Test
    fun legacyProtocolIsNormalizedWhenUnifiedPlanSelectionIsEnteredNotBefore() {
        val context = SetupStepContext()
        val legacy = baseDraft().copy(
            programRoute = SetupProgramRoute.PROTOCOL,
            trainingPath = SetupTrainingPath.PERSONALIZE,
            stepProgress = baseDraft().stepProgress.at(SetupStepId.DAYS, context),
        )
        assertEquals(
            "la lectura legacy conserva su ruta antes de PLAN",
            SetupProgramRoute.PROTOCOL,
            SetupDraftCompatibility.normalizeLegacyRouteAtPlan(legacy).programRoute,
        )

        val atPlan = legacy.copy(stepProgress = legacy.stepProgress.at(SetupStepId.PLAN, context))
        val repaired = SetupDraftCompatibility.repair(atPlan)
        assertEquals(SetupProgramRoute.CUSTOMIZABLE, repaired.programRoute)
        assertEquals(SetupTrainingPath.PERSONALIZE, repaired.trainingPath)
        assertEquals("el espejo conversacional conserva la respuesta histórica",
            atPlan.wizChat, repaired.wizChat)
        assertIdempotent(atPlan)
    }

    @Test
    fun olderNativeGraphProgressRewindsToTheFirstUnansweredStableStepAndUpgradesIdempotently() {
        val context = SetupStepContext()
        val oldProgress = SetupStepProgress(
            block = SetupWizardBlock.BASICS,
            stepIndex = 0,
            currentStepId = SetupStepId.PLAN,
            visited = listOf(SetupStepId.NAME, SetupStepId.PLAN),
            answers = mapOf(SetupStepId.MILESTONE_BASICS to SetupAnswerProvenance.USER_DECLARED),
            origin = SetupProgressOrigin.NATIVE,
            graphRevision = 1,
        )
        val legacy = baseDraft().copy(stepProgress = oldProgress)

        val repaired = SetupDraftCompatibility.repair(legacy)

        assertEquals(SetupStepGraph.REVISION, repaired.stepProgress.graphRevision)
        assertEquals(SetupStepId.NAME, repaired.stepProgress.currentStepId)
        assertEquals(SetupStepGraph.blockOf(SetupStepId.NAME), repaired.stepProgress.block)
        assertEquals(SetupStepGraph.stepIds(context).indexOf(SetupStepId.NAME), repaired.stepProgress.stepIndex)
        assertEquals(
            SetupStepGraph.confirmedBlocks(oldProgress.answers, context, oldProgress.pendingReview),
            repaired.stepProgress.completedBlocks,
        )
        assertEquals("reparar de nuevo no vuelve a mutar el progreso", repaired,
            SetupDraftCompatibility.repair(repaired))
    }

    @Test
    fun aValidButOutOfOrderCursorRewindsToANewlyInsertedUnansweredPrerequisite() {
        val context = SetupStepContext()
        val route = SetupStepGraph.stepIds(context)
        val weekdaysIndex = route.indexOf(SetupStepId.WEEKDAYS)
        val answersBeforeWeekdaysExceptGoal = route.take(weekdaysIndex)
            .filterNot { it == SetupStepId.GOAL }
            .associateWith { SetupAnswerProvenance.USER_DECLARED }
        val oldProgress = SetupStepProgress(
            block = SetupWizardBlock.TRAINING,
            stepIndex = weekdaysIndex,
            currentStepId = SetupStepId.WEEKDAYS,
            visited = route.take(weekdaysIndex + 1),
            answers = answersBeforeWeekdaysExceptGoal,
            origin = SetupProgressOrigin.NATIVE,
            graphRevision = SetupStepGraph.REVISION,
        )
        val draft = baseDraft().copy(stepProgress = oldProgress)

        val repaired = SetupDraftCompatibility.repair(draft)

        assertEquals("el perfil va antes de los días incluso si el cursor era válido", SetupStepId.GOAL,
            repaired.stepProgress.currentStepId)
        assertEquals(answersBeforeWeekdaysExceptGoal, repaired.stepProgress.answers)
        assertIdempotent(draft)
    }

    // ─── CUSTOMIZABLE + trainingPath null ──────────────────────────────────

    @Test
    fun customizableWithNullPathFillsPersonalizeWhenThereAreNoManualSessions() {
        val draft = baseDraft().copy(
            programRoute = SetupProgramRoute.CUSTOMIZABLE,
            trainingPath = null,
            sessions = emptyList(),
        )

        val repaired = SetupDraftCompatibility.repair(draft)

        assertEquals(SetupTrainingPath.PERSONALIZE, repaired.trainingPath)
        assertIdempotent(draft)
    }

    @Test
    fun customizableWithNullPathKeepsManualWorkAndMarksPlanForReviewInsteadOfConvertingIt() {
        val manualSession = SetupSessionDraft(
            weekday = 1,
            title = "Mi sesión",
            exercises = listOf(
                SetupExerciseDraft(
                    id = "ex-1",
                    exercise = com.example.kpkn.data.models.Exercise(id = "ex-1", name = "Press"),
                ),
            ),
        )
        val draft = baseDraft().copy(
            programRoute = SetupProgramRoute.CUSTOMIZABLE,
            trainingPath = null,
            sessions = listOf(manualSession),
        )

        val repaired = SetupDraftCompatibility.repair(draft)

        assertNull("no se rellena PERSONALIZE sobre sesiones manuales", repaired.trainingPath)
        assertTrue(SetupStepId.PLAN in repaired.stepProgress.pendingReview)
        assertEquals("las sesiones manuales se conservan", 1, repaired.sessions.size)
        assertIdempotent(draft)
    }

    // ─── Categorías sin aparatos ───────────────────────────────────────────

    private fun planRequiringPrecision(): String = PersonalizedPlanCatalog.entries()
        .first { entry -> entry.requiredEquipment.any { it == "machine" || it.startsWith("machine_config:") } }
        .id

    private fun planWithoutPrecision(): String = PersonalizedPlanCatalog.entries()
        .first { entry ->
            entry.requiredEquipment.isNotEmpty() &&
                entry.requiredEquipment.all { it == "general_gym" || it == "bodyweight" }
        }
        .id

    @Test
    fun categoriesWithoutApparatusKeepEmptyUnknownMapsAndFlagMaterialOnlyWhenThePlanNeedsPrecision() {
        val inventory = EquipmentInventory(dumbbells = listOf(DumbbellPairStock(weightPerUnitKg = 16.0)))
        val categories = setOf(EquipmentCategory.MACHINES, EquipmentCategory.CABLE)
        val draft = baseDraft().copy(
            trainingOptions = SetupTrainingOptions(
                inventory = inventory,
                availability = EquipmentAvailability(categories = categories),
            ),
            selectedCatalogId = planRequiringPrecision(),
        )

        val repaired = SetupDraftCompatibility.repair(draft)

        assertEquals("las categorías se mantienen", categories, repaired.trainingOptions.availability?.categories)
        assertTrue(
            "mapas vacíos = UNKNOWN, nunca PRESENT",
            repaired.trainingOptions.availability?.apparatus.isNullOrEmpty() &&
                repaired.trainingOptions.availability?.supports.isNullOrEmpty(),
        )
        assertTrue("el material queda pendiente cuando el plan exige precisión",
            SetupStepId.AVAILABILITY in repaired.stepProgress.pendingReview)
        assertEquals("nunca se borra el inventario guardado", inventory, repaired.trainingOptions.inventory)
        assertIdempotent(draft)
    }

    @Test
    fun categoriesWithoutApparatusDoNotFlagMaterialWhenThePlanNeedsNoPrecision() {
        val draft = baseDraft().copy(
            trainingOptions = SetupTrainingOptions(
                availability = EquipmentAvailability(setOf(EquipmentCategory.MACHINES)),
            ),
            selectedCatalogId = planWithoutPrecision(),
        )

        val repaired = SetupDraftCompatibility.repair(draft)

        assertFalse(
            SetupStepId.AVAILABILITY in repaired.stepProgress.pendingReview,
        )
        assertIdempotent(draft)
    }

    @Test
    fun confirmedApparatusPresenceOrMissingSelectionNeverFlagsMaterial() {
        val withPresence = baseDraft().copy(
            trainingOptions = SetupTrainingOptions(
                availability = EquipmentAvailability(
                    categories = setOf(EquipmentCategory.MACHINES),
                    apparatus = mapOf("hack_squat" to ApparatusPresence.PRESENT),
                ),
            ),
            selectedCatalogId = planRequiringPrecision(),
        )
        assertFalse(
            SetupStepId.AVAILABILITY in SetupDraftCompatibility.repair(withPresence).stepProgress.pendingReview,
        )
        assertIdempotent(withPresence)

        val withoutSelection = baseDraft().copy(
            trainingOptions = SetupTrainingOptions(
                availability = EquipmentAvailability(setOf(EquipmentCategory.MACHINES)),
            ),
        )
        assertFalse(
            SetupStepId.AVAILABILITY in SetupDraftCompatibility.repair(withoutSelection).stepProgress.pendingReview,
        )
        assertIdempotent(withoutSelection)
    }

    // ─── IDs de plan: existente / desaparecido / previews viejos ───────────

    @Test
    fun vanishedPlanIdClearsOnlyTheDependentSelectionAndMarksPreviewStale() {
        val base = baseDraft().copy(
            selectedCatalogId = "plan-que ya no existe",
            stepProgress = SetupStepProgress.initial(SetupStepContext()).at(SetupStepId.PLAN, SetupStepContext()),
        )

        val gone = SetupDraftCompatibility.applyCatalogRevision(base, "rev-1", "rev-2") { false }

        assertNull("solo se limpia la selección dependiente", gone.selectedCatalogId)
        assertTrue(SetupStepId.PLAN in gone.stepProgress.pendingReview)
        assertTrue(SetupPreviewKind.PLAN_CANDIDATES in gone.stepProgress.stalePreviews)
        assertTrue(SetupPreviewKind.RECIPE in gone.stepProgress.stalePreviews)
        assertEquals(base.stepProgress.answers, gone.stepProgress.answers)
        assertEquals(base.sessions, gone.sessions)

        val kept = SetupDraftCompatibility.applyCatalogRevision(base, "rev-1", "rev-2") { true }
        assertNotNull("el ID existente se conserva para lectura", kept.selectedCatalogId)
        assertTrue(SetupStepId.PLAN in kept.stepProgress.pendingReview)
    }

    @Test
    fun catalogRevisionReopensAtPlanWhenTheRestOfTheRouteWasAlreadyConfirmed() {
        val draft = draftAtReview(SetupGoal.MUSCLE, SetupStepId.PLAN).copy(
            selectedCatalogId = planWithoutPrecision(),
            catalogRevision = "rev-1",
        )

        val repaired = SetupDraftCompatibility.applyCatalogRevision(draft, "rev-1", "rev-2") { true }

        assertEquals(draft.selectedCatalogId, repaired.selectedCatalogId)
        assertEquals(SetupStepId.PLAN, repaired.stepProgress.currentStepId)
        assertTrue(SetupStepId.PLAN in repaired.stepProgress.pendingReview)
        assertTrue(SetupPreviewKind.RECIPE in repaired.stepProgress.stalePreviews)
    }

    @Test
    fun missingCatalogRevisionIsTreatedAsUnknownAndReopensSelectedPlanSafely() {
        val draft = draftAtReview(SetupGoal.MUSCLE, SetupStepId.PLAN).copy(
            selectedCatalogId = planWithoutPrecision(),
            catalogRevision = null,
        )

        val repaired = SetupDraftCompatibility.applyCatalogRevision(draft, null, "rev-current") { true }

        assertEquals("no se reemplaza la selección existente", draft.selectedCatalogId, repaired.selectedCatalogId)
        assertEquals("versión desconocida no acredita el preview viejo", "rev-current", repaired.catalogRevision)
        assertEquals(SetupStepId.PLAN, repaired.stepProgress.currentStepId)
        assertTrue(SetupPreviewKind.RECIPE in repaired.stepProgress.stalePreviews)
        assertIdempotent(repaired)
    }
}
