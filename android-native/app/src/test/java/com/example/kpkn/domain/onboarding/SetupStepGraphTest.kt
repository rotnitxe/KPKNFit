package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SetupStepGraphTest {

    private val fullContext = SetupStepContext()

    @Test
    fun defaultContextBuildsTheFullNormalSignUpExactRoute() {
        assertEquals(
            listOf(
                // Bloque 1: Datos básicos
                SetupStepId.NAME, SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT,
                SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS,
                // Bloque 2: Entreno v2 (material antes del objetivo; sin ROUTE, STYLE, DAYS, SPLIT, marcas
                // sueltas, autorregulación, calentamientos ni revisión del plan). Sin datos del borrador, la ruta
                // base no trae cardio, capacidades ni marcas, pero sí la técnica y la semana armada.
                SetupStepId.EXPERIENCE, SetupStepId.EQUIPMENT, SetupStepId.AVAILABILITY, SetupStepId.GOAL,
                SetupStepId.FRESH_DAY, SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME,
                SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY,
                SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY,
                SetupStepId.PRIORITIES, SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT,
                SetupStepId.MILESTONE_TRAINING,
                // Bloque 3: Nutrición
                SetupStepId.NUTRITION_START, SetupStepId.NUTRITION_ELIGIBILITY,
                SetupStepId.NUTRITION_DIRECTION, SetupStepId.NUTRITION_TARGET,
                SetupStepId.NUTRITION_HISTORY_CONTEXT, SetupStepId.NUTRITION_ACTIVITY,
                SetupStepId.NUTRITION_DISTRIBUTION, SetupStepId.NUTRITION_WEIGH_INS,
                SetupStepId.NUTRITION_RESULT, SetupStepId.MILESTONE_NUTRITION,
                // Bloque 4: Rings
                SetupStepId.RINGS_RECENT, SetupStepId.RINGS_MUSCLE_FEELING,
                SetupStepId.RINGS_ENERGY_FEELING, SetupStepId.RINGS_STRUCTURE_FEELING,
                SetupStepId.RINGS_DISCOMFORT, SetupStepId.RINGS_RESULT,
                SetupStepId.MILESTONE_RINGS,
                // Revisión
                SetupStepId.REVIEW_ACTIVATE,
            ),
            SetupStepGraph.stepIds(fullContext),
        )
    }

    @Test
    fun routeCoversTheFourMandatoryBlocksPlusReviewAndStartsAtName() {
        val route = SetupStepGraph.stepIds(fullContext)

        assertEquals(SetupStepId.NAME, route.first())
        assertEquals(SetupStepId.REVIEW_ACTIVATE, route.last())
        assertEquals(
            setOf(SetupWizardBlock.BASICS, SetupWizardBlock.TRAINING, SetupWizardBlock.NUTRITION,
                SetupWizardBlock.RINGS, SetupWizardBlock.REVIEW),
            route.map(SetupStepGraph::blockOf).toSet(),
        )
        // Los hitos separan los bloques.
        assertTrue(route.indexOf(SetupStepId.MILESTONE_BASICS) < route.indexOf(SetupStepId.EXPERIENCE))
        assertTrue(route.indexOf(SetupStepId.MILESTONE_TRAINING) < route.indexOf(SetupStepId.NUTRITION_START))
        assertTrue(route.indexOf(SetupStepId.MILESTONE_NUTRITION) < route.indexOf(SetupStepId.RINGS_RECENT))
        assertTrue(route.indexOf(SetupStepId.MILESTONE_RINGS) < route.indexOf(SetupStepId.REVIEW_ACTIVATE))
    }

    @Test
    fun legacyOnlyStepsNeverAppearInAProductiveRoute() {
        val route = SetupStepGraph.stepIds(fullContext).toSet()
        assertFalse(SetupStepId.GENDER in route)
        assertFalse(SetupStepId.HOME_EQUIPMENT in route)
        assertFalse(SetupStepId.NUTRITION_SEX in route)
        assertFalse(SetupStepId.RINGS_START in route)
        // Los pasos de entreno retirados tampoco: el enum y sus definiciones solo sirven para leer borradores viejos.
        for (retired in listOf(
            SetupStepId.ROUTE, SetupStepId.STYLE, SetupStepId.DAYS, SetupStepId.SPLIT, SetupStepId.TRAINING_MARKS,
            SetupStepId.AUTOREGULATION, SetupStepId.AUTOREGULATION_CONFIRM, SetupStepId.WARMUPS,
            SetupStepId.TRAINING_REVIEW,
        )) {
            assertFalse("$retired", retired in route)
        }
    }

    @Test
    fun inventoryGroupsInsertTheirStepsAfterTheEnvironmentInGroupOrder() {
        val base = SetupStepGraph.stepIds(fullContext)
        val withInventory = SetupStepGraph.stepIds(fullContext.copy(
            inventoryGroups = setOf(
                SetupInventoryGroup.DUMBBELLS, SetupInventoryGroup.BARBELL, SetupInventoryGroup.MACHINES,
            ),
        ))

        assertFalse(SetupStepId.INVENTORY_BARBELL in base)
        assertTrue(SetupStepId.INVENTORY_BARBELL in withInventory)
        assertTrue(SetupStepId.INVENTORY_DUMBBELLS in withInventory)
        assertTrue(SetupStepId.INVENTORY_MACHINES in withInventory)
        // Orden estable por grupo, tras el material y antes del objetivo.
        assertTrue(withInventory.indexOf(SetupStepId.INVENTORY_BARBELL) > withInventory.indexOf(SetupStepId.AVAILABILITY))
        assertTrue(withInventory.indexOf(SetupStepId.INVENTORY_BARBELL) < withInventory.indexOf(SetupStepId.GOAL))
        assertTrue(withInventory.indexOf(SetupStepId.INVENTORY_BARBELL) < withInventory.indexOf(SetupStepId.INVENTORY_DUMBBELLS))
        assertTrue(withInventory.indexOf(SetupStepId.INVENTORY_DUMBBELLS) < withInventory.indexOf(SetupStepId.INVENTORY_MACHINES))
        // El resto de la ruta no cambia: solo se insertan los pasos nuevos.
        assertEquals(
            base.filterNot { it in setOf(SetupStepId.INVENTORY_BARBELL, SetupStepId.INVENTORY_DUMBBELLS, SetupStepId.INVENTORY_MACHINES) },
            withInventory.filterNot { it in setOf(SetupStepId.INVENTORY_BARBELL, SetupStepId.INVENTORY_DUMBBELLS, SetupStepId.INVENTORY_MACHINES) },
        )
    }

    @Test
    fun materialComesBeforeTheGoalAndTheWeekQuestionsComeBeforeCalibration() {
        val route = SetupStepGraph.stepIds(fullContext)
        // Lugares y material ANTES del objetivo: la elegibilidad de los perfiles depende del material.
        assertTrue(route.indexOf(SetupStepId.EXPERIENCE) < route.indexOf(SetupStepId.EQUIPMENT))
        assertTrue(route.indexOf(SetupStepId.EQUIPMENT) < route.indexOf(SetupStepId.AVAILABILITY))
        assertTrue(route.indexOf(SetupStepId.AVAILABILITY) < route.indexOf(SetupStepId.GOAL))
        // Objetivo → día con más energía → días → tiempo → calibración → prioridades → programa → semana.
        assertTrue(route.indexOf(SetupStepId.GOAL) < route.indexOf(SetupStepId.FRESH_DAY))
        assertTrue(route.indexOf(SetupStepId.FRESH_DAY) < route.indexOf(SetupStepId.WEEKDAYS))
        assertTrue(route.indexOf(SetupStepId.WEEKDAYS) < route.indexOf(SetupStepId.SESSION_TIME))
        assertTrue(route.indexOf(SetupStepId.SESSION_TIME) < route.indexOf(SetupStepId.VOLUME_CONSISTENCY))
        assertTrue(route.indexOf(SetupStepId.VOLUME_MOBILITY) < route.indexOf(SetupStepId.PRIORITIES))
        assertTrue(route.indexOf(SetupStepId.PRIORITIES) < route.indexOf(SetupStepId.PLAN))
        assertTrue(route.indexOf(SetupStepId.PLAN) < route.indexOf(SetupStepId.WEEK_LAYOUT))
        assertEquals(SetupStepId.MILESTONE_TRAINING, SetupStepGraph.next(SetupStepId.WEEK_LAYOUT, fullContext))
        // ROUTE, STYLE, DAYS y SPLIT ya no son preguntas.
        assertFalse(SetupStepId.ROUTE in route)
        assertFalse(SetupStepId.STYLE in route)
        assertFalse(SetupStepId.DAYS in route)
        assertFalse(SetupStepId.SPLIT in route)
        assertEquals(SetupStepId.VOLUME_TECHNIQUE, SetupStepGraph.next(SetupStepId.SESSION_TIME, fullContext))
        assertEquals(SetupStepId.PLAN, SetupStepGraph.next(SetupStepId.PRIORITIES, fullContext))
    }

    @Test
    fun deferredProgramDropsTheWeekLayoutAndKeepsThePlanAndTheTrainingMilestone() {
        val later = fullContext.copy(hasWeekLayout = false)
        val route = SetupStepGraph.stepIds(later)
        assertFalse(SetupStepId.WEEK_LAYOUT in route)
        assertTrue(SetupStepId.PLAN in route)
        assertTrue(SetupStepId.MILESTONE_TRAINING in route)
        assertEquals(SetupStepId.MILESTONE_TRAINING, SetupStepGraph.next(SetupStepId.PLAN, later))
        assertEquals(SetupStepId.PLAN, SetupStepGraph.previous(SetupStepId.MILESTONE_TRAINING, later))
        // Con la semana armada, el programa va seguido de ella.
        assertEquals(SetupStepId.WEEK_LAYOUT, SetupStepGraph.next(SetupStepId.PLAN, fullContext))
        assertEquals(SetupStepId.PLAN, SetupStepGraph.previous(SetupStepId.WEEK_LAYOUT, fullContext))
    }

    @Test
    fun marksStepOnlyEntersWhenTheContextAsksForLifts() {
        assertFalse(SetupStepId.TRAINING_MAX in SetupStepGraph.stepIds(fullContext))
        val withMarks = fullContext.copy(asksMarks = true)
        assertEquals(SetupStepId.TRAINING_MAX, SetupStepGraph.next(SetupStepId.PRIORITIES, withMarks))
        assertEquals(SetupStepId.PLAN, SetupStepGraph.next(SetupStepId.TRAINING_MAX, withMarks))
        // La rama vieja de marcas sueltas (TRAINING_MARKS) ya no existe en la ruta.
        assertFalse(SetupStepId.TRAINING_MARKS in SetupStepGraph.stepIds(withMarks))
    }

    @Test
    fun techniqueIsSkippedForBeginnersAndCapabilitiesComeAfterTheCalibration() {
        val novice = SetupStepGraph.stepIds(fullContext.copy(asksTechnique = false))
        assertFalse(SetupStepId.VOLUME_TECHNIQUE in novice)
        assertEquals(SetupStepId.VOLUME_CONSISTENCY, SetupStepGraph.next(SetupStepId.SESSION_TIME, fullContext.copy(asksTechnique = false)))
        val asks = SetupStepGraph.stepIds(fullContext.copy(asksCapabilities = true))
        assertTrue(asks.indexOf(SetupStepId.VOLUME_MOBILITY) < asks.indexOf(SetupStepId.CAPABILITIES))
        assertTrue(asks.indexOf(SetupStepId.CAPABILITIES) < asks.indexOf(SetupStepId.PRIORITIES))
        assertFalse(SetupStepId.CAPABILITIES in SetupStepGraph.stepIds(fullContext))
    }

    @Test
    fun cardioBranchOnlyAppearsWhenTheGoalIncludesCardio() {
        val plain = SetupStepGraph.stepIds(fullContext)
        val withCardio = SetupStepGraph.stepIds(fullContext.copy(goalIncludesCardio = true))

        assertFalse(SetupStepId.CARDIO_TYPE in plain)
        assertFalse(SetupStepId.CARDIO_TIME in plain)
        assertTrue(SetupStepId.CARDIO_TYPE in withCardio && SetupStepId.CARDIO_TIME in withCardio)
        // Siempre después del tiempo por sesión y antes de la calibración.
        assertTrue(withCardio.indexOf(SetupStepId.SESSION_TIME) < withCardio.indexOf(SetupStepId.CARDIO_TYPE))
        assertTrue(withCardio.indexOf(SetupStepId.CARDIO_TYPE) < withCardio.indexOf(SetupStepId.CARDIO_TIME))
        assertTrue(withCardio.indexOf(SetupStepId.CARDIO_TIME) < withCardio.indexOf(SetupStepId.VOLUME_TECHNIQUE))
        assertTrue(withCardio.indexOf(SetupStepId.VOLUME_MOBILITY) < withCardio.indexOf(SetupStepId.PRIORITIES))
    }

    @Test
    fun nutritionTrackingOnlyIsAnExplicitChoiceThatSkipsReferences() {
        val route = SetupStepGraph.stepIds(fullContext.copy(nutritionStartChoice = "tracking_only"))

        assertTrue(SetupStepId.NUTRITION_START in route)
        assertTrue(SetupStepId.NUTRITION_RESULT in route)
        assertTrue(SetupStepId.MILESTONE_NUTRITION in route)
        assertFalse(SetupStepId.NUTRITION_ELIGIBILITY in route)
        assertFalse(SetupStepId.NUTRITION_DIRECTION in route)
        assertFalse(SetupStepId.NUTRITION_RHYTHM in route)
        assertFalse(SetupStepId.NUTRITION_TARGET in route)
        assertFalse(SetupStepId.NUTRITION_HISTORY_CONTEXT in route)
        assertFalse(SetupStepId.NUTRITION_ACTIVITY in route)
        assertFalse(SetupStepId.NUTRITION_MANUAL_CALORIES in route)
        assertFalse(SetupStepId.NUTRITION_DISTRIBUTION in route)
        assertFalse(SetupStepId.NUTRITION_WEIGH_INS in route)
    }

    @Test
    fun nutritionSelfDefinedInsertsBothManualMacroScreens() {
        val selfDefined = fullContext.copy(nutritionStartChoice = "self_defined")
        val route = SetupStepGraph.stepIds(selfDefined)

        assertTrue(SetupStepId.NUTRITION_MANUAL_CALORIES in route)
        assertTrue(SetupStepId.NUTRITION_MANUAL_CARBS_FAT in route)
        // El reparto semanal y los pesajes siguen presentes: no se saltan.
        assertTrue(SetupStepId.NUTRITION_DISTRIBUTION in route)
        assertTrue(SetupStepId.NUTRITION_WEIGH_INS in route)
        assertEquals(
            SetupStepId.NUTRITION_MANUAL_CARBS_FAT,
            SetupStepGraph.next(SetupStepId.NUTRITION_MANUAL_CALORIES, selfDefined),
        )
    }

    @Test
    fun nutritionSelfDefinedSkipsTheEerChainAndKeepsDirectionAndContext() {
        val selfDefined = fullContext.copy(nutritionStartChoice = "self_defined")
        val route = SetupStepGraph.stepIds(selfDefined)

        // Modo manual: NO preguntas de elegibilidad ni de actividad (cadena EER).
        assertFalse(SetupStepId.NUTRITION_ELIGIBILITY in route)
        assertFalse(SetupStepId.NUTRITION_ACTIVITY in route)
        // Sí dirección + contexto de objetivo + números explícitos.
        assertTrue(SetupStepId.NUTRITION_DIRECTION in route)
        assertTrue(SetupStepId.NUTRITION_TARGET in route)
        assertTrue(SetupStepId.NUTRITION_HISTORY_CONTEXT in route)
        assertTrue(SetupStepId.NUTRITION_MANUAL_CALORIES in route)
        assertTrue(SetupStepId.NUTRITION_MANUAL_CARBS_FAT in route)
        assertTrue(SetupStepId.NUTRITION_RESULT in route)
        assertEquals(
            SetupStepId.NUTRITION_TARGET,
            SetupStepGraph.next(SetupStepId.NUTRITION_DIRECTION, selfDefined),
        )
    }

    @Test
    fun nutritionAutomaticKeepsTheFullEerChainWithTargetAndHistoryContext() {
        val route = SetupStepGraph.stepIds(fullContext)

        assertTrue(SetupStepId.NUTRITION_ELIGIBILITY in route)
        assertTrue(SetupStepId.NUTRITION_DIRECTION in route)
        assertTrue(SetupStepId.NUTRITION_TARGET in route)
        assertTrue(SetupStepId.NUTRITION_HISTORY_CONTEXT in route)
        assertTrue(SetupStepId.NUTRITION_ACTIVITY in route)
        assertTrue(SetupStepId.NUTRITION_DISTRIBUTION in route)
        assertTrue(SetupStepId.NUTRITION_WEIGH_INS in route)
        assertTrue(SetupStepId.NUTRITION_RESULT in route)
    }

    @Test
    fun nutritionRhythmOnlyFollowsANonMaintenanceDirection() {
        assertEquals(
            SetupStepId.NUTRITION_RHYTHM,
            SetupStepGraph.next(SetupStepId.NUTRITION_DIRECTION, fullContext.copy(nutritionDirection = "deficit")),
        )
        assertTrue(SetupStepId.NUTRITION_RHYTHM in SetupStepGraph.stepIds(fullContext.copy(nutritionDirection = "surplus")))
        assertFalse(SetupStepId.NUTRITION_RHYTHM in SetupStepGraph.stepIds(fullContext.copy(nutritionDirection = "maintenance")))
        assertFalse(SetupStepId.NUTRITION_RHYTHM in SetupStepGraph.stepIds(fullContext.copy(nutritionDirection = null)))
    }

    @Test
    fun ringsBlockAlwaysAsksFeelingsAndDiscomfortAndNeverFabricatesEvidence() {
        val unknown = SetupStepGraph.stepIds(fullContext.copy(recentTraining = null))
        val noHistory = SetupStepGraph.stepIds(fullContext.copy(recentTraining = false))
        val trained = SetupStepGraph.stepIds(fullContext.copy(recentTraining = true))

        for (route in listOf(unknown, noHistory, trained)) {
            assertTrue(SetupStepId.RINGS_RECENT in route)
            assertTrue(SetupStepId.RINGS_MUSCLE_FEELING in route)
            assertTrue(SetupStepId.RINGS_ENERGY_FEELING in route)
            assertTrue(SetupStepId.RINGS_STRUCTURE_FEELING in route)
            assertTrue(SetupStepId.RINGS_DISCOMFORT in route)
        }
        // La evidencia de historial solo existe cuando es conocida ("sí").
        assertFalse(SetupStepId.RINGS_SESSIONS in unknown)
        assertFalse(SetupStepId.RINGS_RECENCY in unknown)
        assertFalse(SetupStepId.RINGS_AXIAL in unknown)
        assertFalse(SetupStepId.RINGS_SESSIONS in noHistory)
        assertTrue(SetupStepId.RINGS_SESSIONS in trained)
        assertTrue(SetupStepId.RINGS_AXIAL in trained)
    }

    @Test
    fun questionForStepOnlyReturnsRenderableOneToOneCopies() {
        // N_SEX migra a EQUATION_SEX, pero no se pinta como copia del bloque nutrición.
        assertEquals(SetupStepId.EQUATION_SEX, SetupStepGraph.stepForQuestion(WizChatQuestionId.N_SEX))
        assertNull(SetupStepGraph.questionForStep(SetupStepId.EQUATION_SEX))
        // Identidad de género, material en casa y arranque de Rings no migran.
        assertNull(SetupStepGraph.stepForQuestion(WizChatQuestionId.P_GENDER))
        assertNull(SetupStepGraph.stepForQuestion(WizChatQuestionId.T_HOME_EQUIPMENT))
        assertNull(SetupStepGraph.stepForQuestion(WizChatQuestionId.R_START))
        // Los pasos nuevos no pretenden tener copia conversacional.
        assertNull(SetupStepGraph.questionForStep(SetupStepId.PRIORITIES))
        assertNull(SetupStepGraph.questionForStep(SetupStepId.NUTRITION_RHYTHM))
        // Los resultados y el review son pantallas finales, nunca preguntas legacy.
        assertNull(SetupStepGraph.questionForStep(SetupStepId.NUTRITION_RESULT))
        assertNull(SetupStepGraph.questionForStep(SetupStepId.RINGS_RESULT))
        assertNull(SetupStepGraph.questionForStep(SetupStepId.REVIEW_ACTIVATE))
        // Los hitos no llevan pregunta legacy.
        for (milestone in listOf(SetupStepId.MILESTONE_BASICS, SetupStepId.MILESTONE_TRAINING,
                SetupStepId.MILESTONE_NUTRITION, SetupStepId.MILESTONE_RINGS)) {
            assertNull(SetupStepGraph.questionForStep(milestone))
        }
    }

    @Test
    fun confirmedBlocksDeriveFromAnswerRecordsNotFromVisiting() {
        var progress = SetupStepProgress.initial(fullContext)
        // Visitar el hito sin confirmarlo no completa el bloque.
        progress = progress.at(SetupStepId.MILESTONE_BASICS, fullContext)
        assertEquals(emptySet<SetupWizardBlock>(), progress.completedBlocks)
        // Pasar al siguiente paso sin confirmar el hito tampoco completa nada.
        progress = progress.at(SetupStepId.EXPERIENCE, fullContext)
        assertEquals(emptySet<SetupWizardBlock>(), progress.completedBlocks)
        // Confirmar el hito (registrar su respuesta) completa el bloque.
        progress = progress.recordAnswer(
            SetupStepId.MILESTONE_BASICS, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED,
        )
        assertEquals(setOf(SetupWizardBlock.BASICS), progress.completedBlocks)
        // Volver al hito no pierde la confirmación.
        progress = progress.at(SetupStepId.MILESTONE_BASICS, fullContext)
        assertEquals(setOf(SetupWizardBlock.BASICS), progress.completedBlocks)
        // El bloque siguiente sigue sin confirmar: su hito no tiene registro.
        assertFalse(SetupWizardBlock.TRAINING in progress.completedBlocks)
    }

    @Test
    fun confirmedBlocksOnlyCountsMilestonesWithAnswerRecordsInsideTheRoute() {
        val answered = mapOf(
            SetupStepId.MILESTONE_BASICS to SetupAnswerProvenance.USER_DECLARED,
            SetupStepId.EXPERIENCE to SetupAnswerProvenance.USER_DECLARED,
        )
        assertEquals(
            setOf(SetupWizardBlock.BASICS),
            SetupStepGraph.confirmedBlocks(answered, fullContext),
        )
        // Un hito inexistente en la ruta del contexto (scope sin nutrición ni entreno)
        // jamás confirma su bloque.
        val ringsOnly = SetupStepContext(includeTraining = false, includeNutrition = false)
        assertEquals(
            emptySet<SetupWizardBlock>(),
            SetupStepGraph.confirmedBlocks(
                mapOf(SetupStepId.MILESTONE_BASICS to SetupAnswerProvenance.USER_DECLARED), ringsOnly,
            ),
        )
    }

    @Test
    fun ringsFeelingsAllowUnknownOrOmitWithoutFabricatingAZeroLevel() {
        for (step in listOf(
            SetupStepId.RINGS_MUSCLE_FEELING, SetupStepId.RINGS_ENERGY_FEELING,
            SetupStepId.RINGS_STRUCTURE_FEELING,
        )) {
            val definition = requireNotNull(SetupStepDefinitions.of(step))
            assertTrue("$step must allow skip (omit)", definition.allowSkip)
            assertEquals("unknown", definition.option("unknown")?.value)
            assertTrue("$step must offer No lo sé", "unknown" in SetupStepDefinitions.optionValues(step))
            // "unknown" no es un nivel 0: los niveles declarados siguen siendo 1..5.
            assertFalse("0" in SetupStepDefinitions.optionValues(step))
        }
        // El historial desconocido nunca fabrica sesiones falsas.
        val unknownHealing = SetupStepGraph.stepIds(fullContext.copy(recentTraining = null))
        assertFalse(SetupStepId.RINGS_SESSIONS in unknownHealing)
    }

    @Test
    fun stepIdentifiersStayStableWhenTheRouteGainsBranches() {
        val before = fullContext
        val progress = SetupStepProgress.initial(before).at(SetupStepId.WEEKDAYS, before)
        val after = before.copy(
            asksMarks = true,
            asksCapabilities = true,
            goalIncludesCardio = true,
        )
        assertTrue(SetupStepId.WEEKDAYS in SetupStepGraph.stepIds(after))
        val restored = progress.at(progress.currentStepId, after)
        assertEquals(SetupStepId.WEEKDAYS, restored.currentStepId)
        assertEquals(SetupWizardBlock.TRAINING, restored.block)
    }

    @Test
    fun backNavigationFollowsTheVisitedOrderNeverFabricatedBranches() {
        val context = SetupStepContext(asksMarks = true)
        val progress = SetupStepProgress.initial(context)
            .at(SetupStepId.PRIORITIES, context)
            .at(SetupStepId.TRAINING_MAX, context)
            .at(SetupStepId.PLAN, context)

        assertEquals(
            SetupStepId.TRAINING_MAX,
            SetupStepGraph.previous(SetupStepId.PLAN, context, progress.visited),
        )
        // Sin marcas en la ruta y con la visita a las marcas en el historial, atrás se salta las marcas.
        val withoutMarks = SetupStepContext(asksMarks = false)
        assertEquals(
            SetupStepId.PRIORITIES,
            SetupStepGraph.previous(SetupStepId.PLAN, withoutMarks, progress.visited),
        )
        // Sin visitas previas, el back cae en la ruta del contexto.
        assertEquals(
            SetupStepId.PRIORITIES,
            SetupStepGraph.previous(SetupStepId.PLAN, withoutMarks, emptyList()),
        )
    }

    @Test
    fun migrateFromLegacyResumesExactlyAndCompletesNoBlock() {
        val migrated = SetupStepGraph.migrateFromLegacy(
            WizChatQuestionId.T_WEEKDAYS,
            fullContext,
            mapOf(SetupStepId.WEIGHT to SetupAnswerProvenance.USER_DECLARED),
        )

        assertEquals(SetupStepId.WEEKDAYS, migrated.currentStepId)
        assertEquals(SetupWizardBlock.TRAINING, migrated.block)
        assertEquals(emptySet<SetupWizardBlock>(), migrated.completedBlocks)
        assertEquals(SetupProgressOrigin.MIGRATED_FROM_WIZCHAT, migrated.origin)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, migrated.answers[SetupStepId.WEIGHT])
    }

    @Test
    fun aLegacyDraftAtARetiredQuestionResumesInsideTheNewRouteAndCompletesNoBlock() {
        // T_DAYS, T_STYLE, T_ROUTE, T_MARKS y T_REVIEW ya no son preguntas: el cursor cae en un paso pendiente de la
        // ruta nueva (nunca en un paso retirado) y la migración no completa ningún bloque.
        val retiredQuestions = listOf(
            WizChatQuestionId.T_DAYS, WizChatQuestionId.T_STYLE, WizChatQuestionId.T_ROUTE,
            WizChatQuestionId.T_MARKS, WizChatQuestionId.T_REVIEW,
        )
        val route = SetupStepGraph.stepIds(fullContext)
        val retiredSteps = setOf(
            SetupStepId.ROUTE, SetupStepId.STYLE, SetupStepId.DAYS, SetupStepId.SPLIT, SetupStepId.TRAINING_MARKS,
            SetupStepId.AUTOREGULATION, SetupStepId.AUTOREGULATION_CONFIRM, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW,
        )
        retiredQuestions.forEach { question ->
            val migrated = SetupStepGraph.migrateFromLegacy(question, fullContext)
            assertTrue("$question -> ${migrated.currentStepId}", migrated.currentStepId in route)
            assertFalse("$question -> ${migrated.currentStepId}", migrated.currentStepId in retiredSteps)
            assertEquals("$question", SetupProgressOrigin.MIGRATED_FROM_WIZCHAT, migrated.origin)
            assertEquals("$question", emptySet<SetupWizardBlock>(), migrated.completedBlocks)
        }
    }

    @Test
    fun migrateFromLegacyMapsNSexExactlyToEquationSex() {
        val migrated = SetupStepGraph.migrateFromLegacy(WizChatQuestionId.N_SEX, fullContext)

        assertEquals(SetupStepId.EQUATION_SEX, migrated.currentStepId)
        assertEquals(SetupWizardBlock.BASICS, migrated.block)
        assertEquals(emptySet<SetupWizardBlock>(), migrated.completedBlocks)
        assertEquals(SetupProgressOrigin.MIGRATED_FROM_WIZCHAT, migrated.origin)
    }

    @Test
    fun legacyRingsStartResumesInsideTheRingsBlock() {
        // R_START no es un paso productivo; el resume cae en el primer paso Rings.
        val migrated = SetupStepGraph.migrateFromLegacy(WizChatQuestionId.R_START, fullContext)
        assertEquals(SetupStepId.RINGS_RECENT, migrated.currentStepId)
        assertEquals(SetupWizardBlock.RINGS, migrated.block)
        assertEquals(SetupProgressOrigin.MIGRATED_FROM_WIZCHAT, migrated.origin)
    }

    @Test
    fun migrateFromLegacyMarksOutOfScopeQuestionsAsNotConvertible() {
        val ringsOnly = SetupStepContext(includeTraining = false, includeNutrition = false, includeRings = true)
        val migrated = SetupStepGraph.migrateFromLegacy(WizChatQuestionId.T_WEEKDAYS, ringsOnly)

        assertEquals(SetupProgressOrigin.NOT_CONVERTIBLE, migrated.origin)
        // La posición legacy se conserva para no perder nada.
        assertEquals(SetupStepId.WEEKDAYS, migrated.currentStepId)
        assertEquals(SetupWizardBlock.TRAINING, migrated.block)
    }

    @Test
    fun isExplicitLegacyRejectsOmittedNeutralAndLegacyOnlyQuestions() {
        fun record(
            id: WizChatQuestionId,
            kind: WizChatAnswerKind,
            text: String? = null,
            number: Double? = null,
            values: List<String> = emptyList(),
            source: WizChatAnswerSource = WizChatAnswerSource.DECLARED,
        ) = WizChatAnswerRecord(id, kind, textValue = text, numberValue = number, values = values, source = source)

        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.N_SEX, record(WizChatQuestionId.N_SEX, WizChatAnswerKind.CHOICE, text = "Femenino")))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.N_SEX, record(WizChatQuestionId.N_SEX, WizChatAnswerKind.CHOICE, text = "Prefiero no responder")))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.N_SEX, record(WizChatQuestionId.N_SEX, WizChatAnswerKind.CHOICE, text = "Femenino", source = WizChatAnswerSource.OMITTED)))
        // El género no tiene target de migración: nunca es "explícito".
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.P_GENDER, record(WizChatQuestionId.P_GENDER, WizChatAnswerKind.CHOICE, text = "Mujer")))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_HOME_EQUIPMENT, record(WizChatQuestionId.T_HOME_EQUIPMENT, WizChatAnswerKind.MULTI_CHOICE, values = listOf("Bandas"))))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.R_START, record(WizChatQuestionId.R_START, WizChatAnswerKind.ACTION, text = "Calibrar")))
        // Un número sin valor nunca cuenta como respuesta declarada.
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.P_AGE, record(WizChatQuestionId.P_AGE, WizChatAnswerKind.NUMBER)))
        // Los valores mapeados de rutas productivas sí cuentan (T_TIME: el tiempo por sesión).
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_TIME, record(WizChatQuestionId.T_TIME, WizChatAnswerKind.NUMBER, number = 45.0)))
        // T_DAYS ya no es una pregunta: nunca es explícita aunque traiga un número.
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_DAYS, record(WizChatQuestionId.T_DAYS, WizChatAnswerKind.NUMBER, number = 3.0)))
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.P_EXPERIENCE, record(WizChatQuestionId.P_EXPERIENCE, WizChatAnswerKind.CHOICE, text = "Tengo experiencia")))
    }

    @Test
    fun isExplicitLegacyOnlyAcceptsMappedLabelsOrDeclaredCandidateIds() {
        fun record(
            id: WizChatQuestionId,
            kind: WizChatAnswerKind,
            text: String? = null,
            values: List<String> = emptyList(),
            source: WizChatAnswerSource = WizChatAnswerSource.DECLARED,
        ) = WizChatAnswerRecord(id, kind, textValue = text, values = values, source = source)

        // CHOICE/TOGGLE: una etiqueta no vacía que no está mapeada queda pendiente.
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_ROUTE, record(WizChatQuestionId.T_ROUTE, WizChatAnswerKind.CHOICE, text = "Lo decidiré después")))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_STYLE, record(WizChatQuestionId.T_STYLE, WizChatAnswerKind.CHOICE, text = "Hipertrofia")))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_DAYS, record(WizChatQuestionId.T_DAYS, WizChatAnswerKind.CHOICE, text = "7")))
        // Un día suelto de T_DAYS tampoco: la semana se declara con los días concretos (T_WEEKDAYS).
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_DAYS, record(WizChatQuestionId.T_DAYS, WizChatAnswerKind.CHOICE, text = "3")))
        // El objetivo antiguo sí migra: su etiqueta se lee como el perfil que hoy le corresponde.
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_GOAL, record(WizChatQuestionId.T_GOAL, WizChatAnswerKind.CHOICE, text = "Fuerza")))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_GOAL, record(WizChatQuestionId.T_GOAL, WizChatAnswerKind.CHOICE, text = "Crossfit")))
        // MULTI_CHOICE: solo cuentan valores mapeados; etiquetas desconocidas se descartan.
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_WEEKDAYS, record(WizChatQuestionId.T_WEEKDAYS, WizChatAnswerKind.MULTI_CHOICE, values = listOf("Lunes"))))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_WEEKDAYS, record(WizChatQuestionId.T_WEEKDAYS, WizChatAnswerKind.MULTI_CHOICE, values = listOf("Finde"))))
        // R_DISCOMFORT: las etiquetas reales del catálogo ahora migran explícitas.
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.R_DISCOMFORT, record(WizChatQuestionId.R_DISCOMFORT, WizChatAnswerKind.MULTI_CHOICE, values = listOf("Rodilla anterior"))))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.R_DISCOMFORT, record(WizChatQuestionId.R_DISCOMFORT, WizChatAnswerKind.MULTI_CHOICE, values = listOf("Algo raro"))))
        // T_PLAN responde con el id del candidato generado: explícito por declaración.
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_PLAN, record(WizChatQuestionId.T_PLAN, WizChatAnswerKind.CHOICE, text = "plan-abc")))
    }

    private fun record(
        id: WizChatQuestionId,
        kind: WizChatAnswerKind,
        text: String? = null,
        number: Double? = null,
        values: List<String> = emptyList(),
        source: WizChatAnswerSource = WizChatAnswerSource.DECLARED,
    ) = WizChatAnswerRecord(id, kind, textValue = text, numberValue = number, values = values, source = source)

    @Test
    fun isExplicitLegacyRejectsUnknownImportedOmittedAndNonFiniteNumbers() {
        // NINGUNA fuente no declarada puede parecer una declaración explícita.
        for (source in listOf(
            WizChatAnswerSource.OMITTED, WizChatAnswerSource.UNKNOWN, WizChatAnswerSource.IMPORTED,
        )) {
            assertFalse("$source age must stay pending", SetupStepGraph.isExplicitLegacy(
                WizChatQuestionId.P_AGE, record(WizChatQuestionId.P_AGE, WizChatAnswerKind.NUMBER, number = 30.0, source = source)))
            assertFalse("$source experience must stay pending", SetupStepGraph.isExplicitLegacy(
                WizChatQuestionId.P_EXPERIENCE,
                record(WizChatQuestionId.P_EXPERIENCE, WizChatAnswerKind.CHOICE, text = "Tengo experiencia", source = source)))
        }
        // Un número no finito nunca es un valor declarado.
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.P_AGE, record(WizChatQuestionId.P_AGE, WizChatAnswerKind.NUMBER, number = Double.NaN)))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.P_AGE,
            record(WizChatQuestionId.P_AGE, WizChatAnswerKind.NUMBER, number = Double.POSITIVE_INFINITY)))
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.P_AGE, record(WizChatQuestionId.P_AGE, WizChatAnswerKind.NUMBER, number = 30.0)))
        // Las fuentes declarables siguen contando.
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.P_EXPERIENCE,
            record(WizChatQuestionId.P_EXPERIENCE, WizChatAnswerKind.CHOICE,
                text = "Tengo experiencia", source = WizChatAnswerSource.SUGGESTED_ACCEPTED)))
    }

    @Test
    fun multiChoiceIsOnlyExplicitWhenEverySelectedValueMaps() {
        // Selección parcialmente válida: TODO queda pendiente, no la mitad.
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_WEEKDAYS,
            record(WizChatQuestionId.T_WEEKDAYS, WizChatAnswerKind.MULTI_CHOICE, values = listOf("Lunes", "Finde"))))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.R_DISCOMFORT,
            record(WizChatQuestionId.R_DISCOMFORT, WizChatAnswerKind.MULTI_CHOICE,
                values = listOf("Rodilla anterior", "Algo raro"))))
        // Valores 100% mapeados o lista vacía sin respuesta.
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_WEEKDAYS,
            record(WizChatQuestionId.T_WEEKDAYS, WizChatAnswerKind.MULTI_CHOICE, values = listOf("Lunes", "Domingo"))))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_WEEKDAYS,
            record(WizChatQuestionId.T_WEEKDAYS, WizChatAnswerKind.MULTI_CHOICE, values = emptyList())))
    }

    @Test
    fun bareActionsAreOnlyExplicitWhereTheActionIsTheConfirmation() {
        // T_MARKS ya no es una pregunta: ni sola ni con su payload migra a ningún paso.
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_MARKS, record(WizChatQuestionId.T_MARKS, WizChatAnswerKind.ACTION)))
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_MARKS,
            record(WizChatQuestionId.T_MARKS, WizChatAnswerKind.ACTION, values = listOf("100", "60", "120"))))
        // La acción del plan (T_PLAN) trae el id del candidato: sí es explícita.
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_PLAN,
            record(WizChatQuestionId.T_PLAN, WizChatAnswerKind.CHOICE, text = "plan-abc")))
        // En las vistas de resultado y el cierre la acción ES la confirmación.
        // T_REVIEW (la revisión del plan retirada) ya no migra: no hay nada que confirmar.
        assertFalse(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.T_REVIEW, record(WizChatQuestionId.T_REVIEW, WizChatAnswerKind.ACTION)))
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.N_RESULT, record(WizChatQuestionId.N_RESULT, WizChatAnswerKind.ACTION)))
        assertTrue(SetupStepGraph.isExplicitLegacy(
            WizChatQuestionId.REVIEW, record(WizChatQuestionId.REVIEW, WizChatAnswerKind.ACTION)))
    }

    @Test
    fun engineOrDerivedMilestoneRecordsNeverCompleteTheirBlock() {
        for (provenance in listOf(SetupAnswerProvenance.ENGINE_RESULT, SetupAnswerProvenance.DERIVED)) {
            // Un hito persistido con procedencia de preview/motor no completa nada.
            assertEquals(
                emptySet<SetupWizardBlock>(),
                SetupStepGraph.confirmedBlocks(
                    mapOf(SetupStepId.MILESTONE_BASICS to provenance), fullContext,
                ),
            )
            // Y en origen ni siquiera puede registrarse: el bloque solo se
            // completa con una confirmación que el usuario pudo hacer.
            try {
                SetupStepProgress.initial(fullContext).recordAnswer(
                    SetupStepId.MILESTONE_BASICS, provenance, SetupValueState.ESTIMATED,
                )
                fail("Un hito con procedencia $provenance no puede registrarse")
            } catch (expected: IllegalArgumentException) {
                // esperado
            }
            assertEquals(emptySet<SetupWizardBlock>(), SetupStepProgress.initial(fullContext).completedBlocks)
        }
        // Las confirmaciones aceptadas (declarada o sugerida) sí completan.
        assertEquals(
            setOf(SetupWizardBlock.BASICS),
            SetupStepGraph.confirmedBlocks(
                mapOf(SetupStepId.MILESTONE_BASICS to SetupAnswerProvenance.SUGGESTED), fullContext,
            ),
        )
    }

    @Test
    fun backNeverReturnsAStepThatTheCurrentBranchRemoved() {
        val withCardio = SetupStepContext(goalIncludesCardio = true)
        val withoutCardio = SetupStepContext(goalIncludesCardio = false)
        // Historial real de una sesión que sí pasó por el cardio.
        val trail = listOf(
            SetupStepId.NAME, SetupStepId.SESSION_TIME, SetupStepId.CARDIO_TYPE,
            SetupStepId.CARDIO_TIME, SetupStepId.VOLUME_TECHNIQUE,
        )

        // Con la rama intacta el historial sigue mandando.
        assertEquals(
            SetupStepId.CARDIO_TIME,
            SetupStepGraph.previous(SetupStepId.VOLUME_TECHNIQUE, withCardio, trail),
        )
        // Con el cardio fuera de la ruta, atrás nunca aterriza en esos pasos.
        assertEquals(
            SetupStepId.SESSION_TIME,
            SetupStepGraph.previous(SetupStepId.VOLUME_TECHNIQUE, withoutCardio, trail),
        )
        // Un paso eliminado de la ruta no genera navegación.
        assertNull(SetupStepGraph.previous(SetupStepId.CARDIO_TYPE, withoutCardio, trail))
        // Sin visitas previas la ruta del contexto manda.
        assertEquals(
            SetupStepId.SESSION_TIME,
            SetupStepGraph.previous(SetupStepId.VOLUME_TECHNIQUE, withoutCardio, emptyList()),
        )
    }

    @Test
    fun nutritionNotStartedKeepsTheBlockAndOnlyAnExplicitChoiceSkipsTheChain() {
        val notStarted = fullContext.copy(nutritionStarted = false)
        val route = SetupStepGraph.stepIds(notStarted)

        // "Sin iniciar" no desmonta el bloque: el arranque sigue preguntándose.
        assertTrue(SetupStepId.NUTRITION_START in route)
        assertTrue(SetupStepId.RINGS_RECENT in route)
        assertEquals(SetupStepId.NUTRITION_START, SetupStepGraph.next(SetupStepId.MILESTONE_TRAINING, notStarted))
        // La cadena de referencias espera a una elección explícita.
        assertFalse(SetupStepId.NUTRITION_ELIGIBILITY in route)
        assertFalse(SetupStepId.MILESTONE_NUTRITION in route)

        // El único salto permitido es la elección explícita "solo registrar".
        val tracking = fullContext.copy(nutritionStarted = false, nutritionStartChoice = "tracking_only")
        val trackingRoute = SetupStepGraph.stepIds(tracking)
        assertTrue(SetupStepId.NUTRITION_RESULT in trackingRoute)
        assertTrue(SetupStepId.MILESTONE_NUTRITION in trackingRoute)
        assertFalse(SetupStepId.NUTRITION_ELIGIBILITY in trackingRoute)
        assertFalse(SetupStepId.NUTRITION_DISTRIBUTION in trackingRoute)

        // El modo profesional legacy tampoco pierde su bloque.
        val professional = fullContext.copy(nutritionStarted = false, nutritionProfessional = true)
        val professionalRoute = SetupStepGraph.stepIds(professional)
        assertTrue(SetupStepId.NUTRITION_RESULT in professionalRoute)
        assertTrue(SetupStepId.MILESTONE_NUTRITION in professionalRoute)
        assertFalse(SetupStepId.NUTRITION_DISTRIBUTION in professionalRoute)
    }

    @Test
    fun completedBlocksDropForReviewWithoutLosingAnswersAndReturnOnConfirmation() {
        val progress = SetupStepProgress.initial(fullContext)
            .recordAnswer(SetupStepId.MILESTONE_BASICS, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)
            .recordAnswer(SetupStepId.MILESTONE_TRAINING, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)
            .recordAnswer(SetupStepId.PLAN, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)
        assertEquals(setOf(SetupWizardBlock.BASICS, SetupWizardBlock.TRAINING), progress.completedBlocks)

        // Un cambio posterior marca el bloque para revisión: deja de estar
        // "hecho" pero NINGUNA respuesta se borra.
        val reviewed = progress.withPendingReview(setOf(SetupStepId.PLAN))
        assertEquals(setOf(SetupWizardBlock.BASICS), reviewed.completedBlocks)
        assertEquals(setOf(SetupStepId.PLAN), reviewed.pendingReview)
        assertEquals(progress.answers, reviewed.answers)
        assertTrue(SetupStepId.MILESTONE_TRAINING in reviewed.answers)

        // Navegar no resucita el bloque mientras siga en revisión.
        val moved = reviewed.at(SetupStepId.REVIEW_ACTIVATE, fullContext)
        assertEquals(setOf(SetupWizardBlock.BASICS), moved.completedBlocks)
        assertEquals(
            setOf(SetupWizardBlock.BASICS),
            SetupStepGraph.confirmedBlocks(moved.answers, fullContext, moved.pendingReview),
        )

        // Reconfirmar el hito cierra la revisión del bloque y lo completa.
        val reconfirmed = moved.recordAnswer(
            SetupStepId.MILESTONE_TRAINING, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED,
        )
        assertEquals(
            setOf(SetupWizardBlock.BASICS, SetupWizardBlock.TRAINING),
            reconfirmed.completedBlocks,
        )
        assertEquals(emptySet<SetupStepId>(), reconfirmed.pendingReview)
        assertEquals(progress.answers[SetupStepId.PLAN], reconfirmed.answers[SetupStepId.PLAN])
    }

    /**
     * Confirmar un paso es revisarlo: cierra SU marca de revisión y deja las demás. Los días se marcan «por revisar» con su propia
     * primera respuesta (cambia la frecuencia) y, sin esto, seguían marcados después de confirmarlos.
     */
    @Test
    fun confirmingAStepClosesItsOwnReviewFlagAndLeavesTheOthersPending() {
        val flagged = SetupStepProgress.initial(fullContext)
            .withPendingReview(setOf(SetupStepId.WEEKDAYS, SetupStepId.PLAN))
        val confirmed = flagged.recordAnswer(SetupStepId.WEEKDAYS, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)
        assertEquals(setOf(SetupStepId.PLAN), confirmed.pendingReview)
        assertTrue(SetupStepId.WEEKDAYS in confirmed.answers)
    }
}
