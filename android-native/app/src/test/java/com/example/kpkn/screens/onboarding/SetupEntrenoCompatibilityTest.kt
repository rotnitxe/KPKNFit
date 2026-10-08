package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupProgressOrigin
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.effectiveEquipment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Borradores guardados ANTES de la ruta de Entreno v2: se leen sin perder respuestas, se llevan a los datos nuevos
 * (lugares, perfil de objetivo, marcas, días) SIN confirmar nada y su cursor vuelve a una pregunta de la ruta nueva.
 */
class SetupEntrenoCompatibilityTest {

    /** Todo lo que un alta antigua ya había respondido antes de llegar a los días. */
    private val earlyAnswers = listOf(
        SetupStepId.NAME, SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT, SetupStepId.EQUATION_SEX,
        SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS, SetupStepId.EXPERIENCE, SetupStepId.EQUIPMENT,
        SetupStepId.GOAL, SetupStepId.DAYS, SetupStepId.WEEKDAYS,
    )

    private val retiredSteps = listOf(
        SetupStepId.ROUTE, SetupStepId.STYLE, SetupStepId.DAYS, SetupStepId.SPLIT, SetupStepId.TRAINING_MARKS,
        SetupStepId.AUTOREGULATION, SetupStepId.AUTOREGULATION_CONFIRM, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW,
    )

    /** Un borrador nativo de la ruta anterior (revisión 3 del grafo): solo campos tipados antiguos. */
    private fun oldDraft(
        environment: String? = "gym",
        goal: SetupGoal? = SetupGoal.STRENGTH,
        experience: SetupExperience? = SetupExperience.INTERMEDIATE,
        answered: List<SetupStepId> = earlyAnswers,
        cursor: SetupStepId = SetupStepId.SESSION_TIME,
        graphRevision: Int = 3,
        availability: EquipmentAvailability? = null,
        options: TrainingOptions = TrainingOptions(),
    ) = SetupWizardDraft(
        draftScope = "full",
        trainingPath = SetupTrainingPath.PERSONALIZE,
        ageYears = 30,
        heightCm = 175.0,
        weightKg = 72.0,
        experience = experience,
        trainingEnvironment = environment,
        equipment = if (environment == "gym") setOf(SetupEquipment.GYM) else emptySet(),
        goal = goal,
        daysPerWeek = 3,
        selectedWeekdays = setOf(1, 3, 5),
        minutesPerSession = 60,
        trainingOptions = options.copy(availability = availability ?: options.availability),
        stepProgress = SetupStepProgress(
            block = SetupWizardBlock.TRAINING,
            currentStepId = cursor,
            answers = answered.associateWith { SetupAnswerProvenance.USER_DECLARED },
            visited = answered + cursor,
            origin = SetupProgressOrigin.NATIVE,
            graphRevision = graphRevision,
        ),
    )

    private fun route(draft: SetupWizardDraft) = SetupStepGraph.stepIds(draft.stepContext())

    // ── Lugares ────────────────────────────────────────────────────────────────

    @Test
    fun theOldEnvironmentBecomesItsPlaceAndTheDeclaredMaterialIsKept() {
        val material = EquipmentAvailability(categories = setOf(EquipmentCategory.BARBELL, EquipmentCategory.DUMBBELLS))
        mapOf(
            "gym" to TrainingPlace.GYM,
            "machines" to TrainingPlace.GYM,
            "home" to TrainingPlace.HOME,
            "none" to TrainingPlace.HOME,
        ).forEach { (environment, place) ->
            val repaired = SetupDraftCompatibility.repair(oldDraft(environment = environment, availability = material))
            assertEquals(environment, setOf(place), repaired.trainingPlaces)
            // La disponibilidad declarada NO se resiembra ni se da por confirmada.
            assertEquals(environment, material, repaired.trainingOptions.availability)
        }
        // Sin entorno no se inventa ningún lugar.
        assertTrue(SetupDraftCompatibility.repair(oldDraft(environment = null)).trainingPlaces.isEmpty())
    }

    @Test
    fun aSavedEnvironmentSelectionAndAMaterialSelectionAreNotKeptAsValuesOfTheNewSteps() {
        val old = oldDraft(environment = "machines").copy(
            stepSelections = mapOf(
                SetupStepId.EQUIPMENT to listOf("machines"),
                SetupStepId.AVAILABILITY to listOf("MACHINES", "CABLE", "BODYWEIGHT_ONLY_OLD"),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)
        assertNull(repaired.stepSelections[SetupStepId.EQUIPMENT])
        assertNull(repaired.stepSelections[SetupStepId.AVAILABILITY])
        assertEquals(setOf("gym"), repaired.selectedValues(SetupStepId.EQUIPMENT))
    }

    // ── Material retirado: la cuerda de saltar ─────────────────────────────────

    /** Lo que escribía el símbolo retirado: su llave y, con ella, la categoría de cardio (que por sí sola acredita las máquinas). */
    private fun withTheRope(base: EquipmentAvailability, rope: ApparatusPresence = ApparatusPresence.PRESENT) = base.copy(
        categories = base.categories + EquipmentCategory.CARDIO,
        supports = base.supports + (EquipmentSymbols.RETIRED_JUMP_ROPE_KEY to rope) +
            (EquipmentSymbols.CARDIO_MACHINE_KEY to ApparatusPresence.ABSENT),
    )

    private fun cardioToken(availability: EquipmentAvailability) =
        "cardio" in TrainingOptions(availability = availability).effectiveEquipment(emptySet())

    @Test
    fun aDraftThatHadTheJumpRopeIsReadWithoutItAndWithoutTheMachinesItBroughtAlong() {
        val home = setOf(TrainingPlace.HOME)
        val chosen = setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BENCH)
        val old = withTheRope(EquipmentSymbols.availabilityOf(chosen, home))
        assertTrue("el material de antes acreditaba máquinas por la categoría de cardio de la cuerda", cardioToken(old))

        val repaired = SetupDraftCompatibility.repair(oldDraft(environment = "home", availability = old, graphRevision = 4))
        val availability = checkNotNull(repaired.trainingOptions.availability)
        assertEquals(chosen, EquipmentSymbols.selectedFrom(availability))
        assertEquals("el resto del material sale igual", EquipmentSymbols.availabilityOf(chosen, home), availability)
        assertFalse(EquipmentSymbols.RETIRED_JUMP_ROPE_KEY in availability.supports || EquipmentCategory.CARDIO in availability.categories)
        assertFalse("sin la cuerda ya no hay máquinas", cardioToken(availability))
        // Idempotente: volver a abrir el borrador no cambia nada.
        assertEquals(availability, SetupDraftCompatibility.repair(repaired).trainingOptions.availability)
    }

    @Test
    fun aDraftThatOnlyHadTheRopeBecomesBodyweightOnlyAndKeepsTheBikeItConfirmed() {
        val rope = withTheRope(EquipmentAvailability())
        val old = rope.copy(apparatus = rope.apparatus + (SetupApparatusPanel.OUTDOOR_BIKE_KEY to ApparatusPresence.PRESENT))
        val repaired = SetupDraftCompatibility.repair(oldDraft(environment = "home", availability = old))
        val availability = checkNotNull(repaired.trainingOptions.availability)
        assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), EquipmentSymbols.selectedFrom(availability))
        assertTrue("la bicicleta que ya había confirmado se conserva", SetupApparatusPanel.hasBike(availability))
        assertFalse(cardioToken(availability))
    }

    @Test
    fun theKeyOfARopeTheyLeftOutIsCleanedToo_andTheCardioMachinesTheyDidChooseStay() {
        val gym = setOf(TrainingPlace.GYM)
        // Cualquier borrador que abrió el paso de material con la cuadrícula anterior trae la llave de la cuerda, también ausente.
        val seeded = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(gym), gym)
        val old = seeded.copy(supports = seeded.supports + (EquipmentSymbols.RETIRED_JUMP_ROPE_KEY to ApparatusPresence.ABSENT))
        val repaired = SetupDraftCompatibility.repair(oldDraft(environment = "gym", availability = old)).trainingOptions.availability
        assertEquals("el gimnasio sale igual (con su «Cardio»)", seeded, repaired)
        assertTrue(EquipmentSymbolId.CARDIO in EquipmentSymbols.selectedFrom(repaired))
        // Un borrador sin la llave no se toca.
        val untouched = SetupDraftCompatibility.repair(oldDraft(environment = "gym", availability = seeded)).trainingOptions.availability
        assertEquals(seeded, untouched)
    }

    // ── Objetivo ───────────────────────────────────────────────────────────────

    @Test
    fun theOldGoalBecomesTheProfileOfTodayAndNothingIsConfirmedBecauseOfIt() {
        val expected = mapOf(
            SetupGoal.STRENGTH to TrainingGoalProfile.POWERLIFTING,
            SetupGoal.MUSCLE to TrainingGoalProfile.BODYBUILDING,
            SetupGoal.STRENGTH_MUSCLE to TrainingGoalProfile.STRENGTH_MUSCLE,
            SetupGoal.COMPLETE_ATHLETE to TrainingGoalProfile.STRENGTH_CARDIO,
            SetupGoal.HEALTH to TrainingGoalProfile.FUNCTIONAL_HEALTH,
            SetupGoal.MIXED to TrainingGoalProfile.STRENGTH_CARDIO,
        )
        expected.forEach { (goal, profile) ->
            val old = oldDraft(goal = goal)
            val repaired = SetupDraftCompatibility.repair(old)
            assertEquals("$goal", profile, repaired.goalProfile)
            assertEquals("$goal", GoalProfileMapping.setupGoalOf(profile), repaired.goal)
            // La respuesta de GOAL sigue registrada (nada se borra) y con la misma procedencia.
            assertEquals("$goal", SetupAnswerProvenance.USER_DECLARED, repaired.stepProgress.answers[SetupStepId.GOAL])
            // Los objetivos que ya no se ofrecen (Salud, Fuerza + cardio) quedan para que la persona los reconfirme.
            val needsReview = goal == SetupGoal.HEALTH || goal == SetupGoal.MIXED
            assertEquals("$goal", needsReview, SetupStepId.GOAL in repaired.stepProgress.pendingReview)
        }
        assertNull(SetupDraftCompatibility.repair(oldDraft(goal = null)).goalProfile)
    }

    @Test
    fun aDraftThatAlreadyHasTheNewDataKeepsItAndNothingIsRewritten() {
        val modern = oldDraft(goal = SetupGoal.STRENGTH, graphRevision = SetupStepGraph.REVISION).copy(
            trainingPlaces = setOf(TrainingPlace.GYM, TrainingPlace.HOME),
            goalProfile = TrainingGoalProfile.POWERBUILDING,
        )
        val repaired = SetupDraftCompatibility.repair(modern)
        assertEquals(setOf(TrainingPlace.GYM, TrainingPlace.HOME), repaired.trainingPlaces)
        // El perfil elegido manda sobre el objetivo antiguo: no se pisa con la traducción de `goal`.
        assertEquals(TrainingGoalProfile.POWERBUILDING, repaired.goalProfile)
    }

    // ── Cursor y ruta ──────────────────────────────────────────────────────────

    @Test
    fun aCursorOnARetiredStepResumesAtTheFirstPendingStepOfTheNewRoute() {
        retiredSteps.forEach { retired ->
            val old = oldDraft(cursor = retired)
            val repaired = SetupDraftCompatibility.repair(old)
            val newRoute = route(repaired)
            val cursor = repaired.stepProgress.currentStepId
            assertTrue("$retired -> $cursor", cursor in newRoute)
            assertFalse("$retired -> $cursor", cursor in retiredSteps)
            // El primero que falta de la ruta nueva: antes del día con más energía (que un borrador viejo no conoce)
            // solo está el material, que tampoco se había declarado.
            assertEquals("$retired", SetupStepId.AVAILABILITY, cursor)
            assertEquals(SetupStepGraph.REVISION, repaired.stepProgress.graphRevision)
            assertEquals(SetupWizardBlock.TRAINING, repaired.stepProgress.block)
        }
    }

    @Test
    fun whenTheMaterialWasAlreadyDeclaredTheResumeIsTheNewDayStep() {
        val answered = earlyAnswers + SetupStepId.AVAILABILITY
        val repaired = SetupDraftCompatibility.repair(oldDraft(answered = answered, cursor = SetupStepId.SPLIT))
        assertEquals(SetupStepId.FRESH_DAY, repaired.stepProgress.currentStepId)
    }

    @Test
    fun noAnswerOfARetiredStepIsEverDropped() {
        val answered = earlyAnswers + listOf(
            SetupStepId.STYLE, SetupStepId.SPLIT, SetupStepId.AUTOREGULATION, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW,
        )
        val old = oldDraft(answered = answered, cursor = SetupStepId.TRAINING_REVIEW)
        val repaired = SetupDraftCompatibility.repair(old)
        assertTrue(repaired.stepProgress.answers.keys.containsAll(old.stepProgress.answers.keys))
        // Y los retirados nunca se exigen: la ruta nueva no los contiene.
        assertTrue(route(repaired).none { it in retiredSteps })
    }

    @Test
    fun aCursorOnAStepThatStillExistsIsKeptWhenEverythingBeforeItIsAnswered() {
        val answered = earlyAnswers + listOf(SetupStepId.AVAILABILITY, SetupStepId.FRESH_DAY)
        val repaired = SetupDraftCompatibility.repair(oldDraft(answered = answered, cursor = SetupStepId.SESSION_TIME))
        assertEquals(SetupStepId.SESSION_TIME, repaired.stepProgress.currentStepId)
    }

    @Test
    fun theRepairIsIdempotent() {
        listOf(
            oldDraft(),
            oldDraft(goal = SetupGoal.MIXED, environment = "home", cursor = SetupStepId.DAYS),
            oldDraft(experience = SetupExperience.NEW, cursor = SetupStepId.AUTOREGULATION),
            oldDraft(goal = SetupGoal.COMPLETE_ATHLETE, answered = earlyAnswers + SetupStepId.MILESTONE_TRAINING),
        ).forEach { old ->
            val once = SetupDraftCompatibility.repair(old)
            assertEquals(once, SetupDraftCompatibility.repair(once))
        }
    }

    // ── Días, marcas y técnica ─────────────────────────────────────────────────

    @Test
    fun theNumberOfDaysIsTheNumberOfChosenWeekdays() {
        val old = oldDraft().copy(daysPerWeek = 5, selectedWeekdays = setOf(1, 3))
        assertEquals(2, SetupDraftCompatibility.repair(old).daysPerWeek)
        val none = oldDraft().copy(daysPerWeek = 4, selectedWeekdays = emptySet())
        assertNull(SetupDraftCompatibility.repair(none).daysPerWeek)
    }

    @Test
    fun theOldPowerliftingMarksPassToTheLiftMarks() {
        val old = oldDraft().copy(
            powerliftingProfile = PowerliftingProfile(squat1RM = 140.0, bench1RM = 100.0, deadlift1RM = 180.0),
            knowsTrainingMarks = true,
        )
        val repaired = SetupDraftCompatibility.repair(old)
        assertEquals(
            mapOf(LiftMark.SQUAT to 140.0, LiftMark.BENCH to 100.0, LiftMark.DEADLIFT to 180.0),
            repaired.liftMarks,
        )
        assertTrue(repaired.knowsTrainingMarks)
        // Con solo una marca viaja solo esa.
        val partial = SetupDraftCompatibility.repair(oldDraft().copy(powerliftingProfile = PowerliftingProfile(bench1RM = 95.0)))
        assertEquals(mapOf(LiftMark.BENCH to 95.0), partial.liftMarks)
        // Sin marcas no se inventa ninguna.
        assertTrue(SetupDraftCompatibility.repair(oldDraft()).liftMarks.isEmpty())
    }

    @Test
    fun aBeginnerGetsTheTechniqueDerivedAsLearningAndKeepsTheOtherAnswers() {
        val old = oldDraft(experience = SetupExperience.NEW)
        assertNull(old.volumeAnswers.technique)
        val repaired = SetupDraftCompatibility.repair(old)
        assertEquals(1, repaired.volumeAnswers.technique)
        assertEquals(SetupAnswerProvenance.DERIVED, repaired.stepProgress.answers[SetupStepId.VOLUME_TECHNIQUE])
        assertNull(repaired.volumeAnswers.consistency)
        // Quien ya la había declarado la conserva tal cual.
        val declared = oldDraft(experience = SetupExperience.NEW)
            .copy(volumeAnswers = SetupVolumeAnswers(technique = 3))
        assertEquals(3, SetupDraftCompatibility.repair(declared).volumeAnswers.technique)
        // Quien no es principiante no recibe nada.
        assertNull(SetupDraftCompatibility.repair(oldDraft()).volumeAnswers.technique)
    }

    // ── Autorregulación y calentamiento ────────────────────────────────────────

    @Test
    fun draftsBeforeTheNewRouteGoBackToTheProgramDefaultsForAutoregulationAndWarmups() {
        val warm = listOf(SetRecipe(percent = 40.0, reps = 5, isWarmup = true))
        val options = TrainingOptions(autoregulationMode = AutoregulationMode.AUTO, automaticConfirmed = true, warmup = warm)
        val repaired = SetupDraftCompatibility.repair(oldDraft(graphRevision = 3, options = options))
        assertEquals(AutoregulationMode.PROPOSE, repaired.trainingOptions.autoregulationMode)
        assertFalse(repaired.trainingOptions.automaticConfirmed)
        assertNull(repaired.trainingOptions.warmup)

        // Con la ruta nueva ya en curso lo declarado se respeta (otro paquete lo gobernará).
        val modern = SetupDraftCompatibility.repair(oldDraft(graphRevision = SetupStepGraph.REVISION, options = options))
        assertEquals(AutoregulationMode.AUTO, modern.trainingOptions.autoregulationMode)
        assertEquals(warm, modern.trainingOptions.warmup)
    }

    // ── Bloque completo ────────────────────────────────────────────────────────

    @Test
    fun aCompletedTrainingBlockWithNewUnansweredStepsIsReopenedForReviewWithoutLosingAnything() {
        val answered = earlyAnswers + listOf(
            SetupStepId.AVAILABILITY, SetupStepId.SESSION_TIME, SetupStepId.STYLE, SetupStepId.VOLUME_TECHNIQUE,
            SetupStepId.VOLUME_CONSISTENCY, SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY,
            SetupStepId.PRIORITIES, SetupStepId.TRAINING_MAX, SetupStepId.SPLIT, SetupStepId.PLAN,
            SetupStepId.AUTOREGULATION, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW, SetupStepId.MILESTONE_TRAINING,
        )
        val old = oldDraft(answered = answered, cursor = SetupStepId.REVIEW_ACTIVATE)
        val repaired = SetupDraftCompatibility.repair(old)
        // FRESH_DAY no existía: la persona tiene que contestarlo, así que el bloque deja de estar «hecho».
        assertTrue(SetupStepId.FRESH_DAY in repaired.stepProgress.pendingReview)
        assertFalse(SetupWizardBlock.TRAINING in repaired.stepProgress.completedBlocks)
        assertTrue(repaired.stepProgress.answers.keys.containsAll(answered.toSet()))
        assertEquals(SetupStepId.FRESH_DAY, repaired.stepProgress.currentStepId)
    }
}
