package com.example.kpkn.data.programs

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.training.Calibration
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.SimpleCyclePersonalizer
import com.example.kpkn.domain.training.VolumeCalculator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PersonalizedPlanCatalogTest {
    private val catalog get() = CatalogCompositionTestSupport.catalog
    private fun personalizer() = SimpleCyclePersonalizer(InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } })

    @Test
    fun nativeFamiliesArePublishedAndNamespaced() {
        val entries = PersonalizedPlanCatalog.entries()
        assertEquals(8, entries.count { it.source == CatalogSource.NATIVE })
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        assertTrue(entries.all { it.title.isNotBlank() && it.description.isNotBlank() })
    }

    @Test
    fun classificationAndTemporalLabelsDoNotFlattenMultiweekRecipes() {
        assertEquals(CatalogClassification.SIMPLE, PersonalizedPlanCatalog.classify(CatalogSource.PROTOCOL, ProgramStructure.SIMPLE, 1, 1, false, true))
        assertEquals(CatalogClassification.ADVANCED, PersonalizedPlanCatalog.classify(CatalogSource.PROTOCOL, ProgramStructure.SIMPLE, 1, 2, true, false))
        PersonalizedPlanCatalog.entries().filter { (it.recipe?.weeks?.size ?: 0) > 1 }.forEach {
            assertNotEquals(it.id, CatalogDuration.REPEATING_WEEK, it.duration)
        }
    }

    @Test
    fun externalRecipeIdentityAndAttributionArePreserved() {
        PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.forEach { protocol ->
            val entry = requireNotNull(PersonalizedPlanCatalog.find("protocol:${protocol.id}"))
            assertEquals(protocol.recipe, entry.recipe)
            assertEquals(protocol.author, entry.sourceAuthor)
            assertEquals(protocol.source.primaryUrl, entry.sourceUrl)
            assertEquals(AdaptationPolicy.FIXED_PRESCRIPTION, entry.adaptation)
            assertNull(personalizer().personalize("fixed", PersonalizerInput(entry.id, TrainingFocus.FULL_BODY, entry.supportedFrequencies.first)).program)
        }
    }

    @Test
    fun everyNativeFamilyBuildsExecutableCanonicalSessions() {
        val planner = personalizer()
        val names = catalog.toLegacyConfigurationLookup()
        PersonalizedPlanCatalog.entries().filter { it.source == CatalogSource.NATIVE }.forEach { entry ->
            val equipment = when (entry.sourceId) {
                "machine-muscle" -> setOf("machine")
                "home-training" -> setOf("bodyweight", "band", "dumbbells", "ball")
                "bodyweight" -> setOf("bodyweight", "support", "pull_up_bar")
                else -> setOf("general_gym")
            }
            val input = PersonalizerInput(entry.id, TrainingFocus.FULL_BODY, entry.supportedFrequencies.first, equipment = equipment, level = CatalogLevel.INTERMEDIATE, availableMinutes = 100)
            val result = planner.personalize("p-${entry.sourceId}", input)
            assertNotNull("${entry.id}: ${result.report.limitations}", result.program)
            val program = result.program!!
            assertEquals("p-${entry.sourceId}", program.id)
            val sessions = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }
            assertEquals(input.frequency, sessions.size)
            sessions.forEach { session ->
                assertTrue(com.example.kpkn.domain.templates.SessionTemplateEngine.sessionHasCompleteExecutableContent(session))
                session.allExercises().filter { it.cardioDetails == null }.forEach { exercise ->
                    assertEquals(names[exercise.catalogConfigurationId]?.name, exercise.name)
                    assertNotNull(exercise.catalogDefinitionId)
                }
                assertTrue(session.targetDurationMinutes!! <= input.availableMinutes)
            }
            result.report.muscles.forEach { row ->
                assertTrue("${entry.id}/${row.muscle}", row.directSets + row.indirectSets <= minOf(row.mav, row.mrv) + 0.001)
            }
            assertEquals(program, planner.personalize(program.id, input).program)
        }
    }

    @Test
    fun machinePlanNeverLeaksFreeWeightsAndRejectsWrongEquipment() {
        val planner = personalizer()
        val result = planner.personalize("machines", PersonalizerInput("native:machine-muscle", TrainingFocus.FULL_BODY, 3, equipment = setOf("machine"), availableMinutes = 100))
        assertNotNull(result.report.limitations.joinToString(), result.program)
        val configurations = catalog.families.flatMap { it.definitions }.flatMap { it.configurations }.associateBy { it.id }
        result.program!!.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }.flatMap { it.allExercises() }.forEach {
            assertEquals("machine", configurations.getValue(it.catalogConfigurationId!!).profile.equipmentId)
        }
        assertNull(planner.personalize("wrong", PersonalizerInput("native:machine-muscle", TrainingFocus.FULL_BODY, 2, equipment = setOf("bodyweight"))).program)
    }

    @Test
    fun calibratedFocusIsFirstAndVolumeReportMatchesTheAuthority() {
        val input = PersonalizerInput("native:machine-muscle", TrainingFocus.GLUTES, 3, equipment = setOf("machine"), level = CatalogLevel.INTERMEDIATE, availableMinutes = 100,
            calibration = Calibration.CALIBRATED,
            volumeRecommendations = listOf(VolumeRecommendation("Glúteos", 0, 8, 16, 3)))
        val result = personalizer().personalize("focus", input)
        assertNotNull(result.report.limitations.joinToString(), result.program)
        val sessions = result.program!!.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }
        val lookup = catalog.toLegacyConfigurationLookup()
        sessions.forEach { session ->
            val first = session.allExercises().first()
            val contribution = VolumeCalculator.calculateRoleSeparatedMuscleVolume(listOf(session.copy(exercises = listOf(first), parts = emptyList())), lookup.values.toList())
            assertTrue((contribution["Glúteos"]?.directSets ?: 0.0) > 0.0)
        }
        val actual = VolumeCalculator.calculateRoleSeparatedMuscleVolume(sessions, lookup.values.toList())
        val row = result.report.muscles.first { it.muscle == "Glúteos" }
        assertEquals(actual.getValue("Glúteos").totalSets, row.directSets + row.indirectSets, 0.001)
        assertEquals(3, row.frequency)
        assertTrue(row.targetSets >= 6.0 && row.targetSets <= 8.0)
    }

    @Test
    fun nativePlanKeepsCustomSevenSlotSplitPrioritiesAndRecipeProvenance() {
        val input = PersonalizerInput(
            catalogEntryId = "native:machine-muscle",
            focus = TrainingFocus.FULL_BODY,
            frequency = 3,
            weekdays = listOf(1, 3, 5),
            equipment = setOf("machine"),
            level = CatalogLevel.INTERMEDIATE,
            availableMinutes = 100,
            priorityMuscles = setOf("Pecho"),
            lowerEmphasisMuscles = setOf("Bíceps"),
            splitId = "custom",
            splitPattern = listOf("Pecho", "Descanso", "Espalda", "Descanso", "Piernas", "Descanso", "Descanso"),
            splitName = "Mi semana",
        )

        val program = requireNotNull(personalizer().personalize("custom-split", input).program)
        val sessions = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

        assertEquals(listOf(1, 3, 5), sessions.mapNotNull { it.dayOfWeek })
        assertEquals(listOf("Pecho", "Espalda", "Piernas"), sessions.map { it.name })
        assertEquals("custom", program.selectedSplitId)
        assertEquals(input.splitPattern, program.customSplitPattern)
        assertEquals("Mi semana", program.customSplitName)
        assertNotNull(program.sourceRecipe)
        assertEquals(3, program.sourceRecipe?.claimedDaysPerWeek)
        assertTrue(program.sourceRecipe?.autoregulationHooks?.isNotEmpty() == true)
        assertEquals(setOf(1, 3, 5), program.schedulePlan?.trainingDays)
        assertEquals(1, program.schedulePlan?.weekStartDay)
    }

    @Test
    fun nativeSchedulePlanMirrorsSelectedWeekdays() {
        val input = PersonalizerInput(
            catalogEntryId = "native:machine-muscle",
            focus = TrainingFocus.FULL_BODY,
            frequency = 3,
            weekdays = listOf(2, 4, 6),
            equipment = setOf("machine"),
            level = CatalogLevel.INTERMEDIATE,
            availableMinutes = 60,
        )
        val program = requireNotNull(personalizer().personalize("schedule", input).program)
        assertEquals(setOf(2, 4, 6), program.schedulePlan?.trainingDays)
        assertEquals(2, program.schedulePlan?.weekStartDay)
        assertEquals(2, program.startDay)
        assertEquals(setOf(2, 4, 6), program.resolvedSchedulePlan().trainingDays)
    }

    /**
     * Contrato nuevo: el split del asistente es una elección real, no una
     * etiqueta. Un split visible con receta KPKN se persiste y restringe la
     * composición de cada día (antes este test exigía que el nombre se
     * ignorara, la semántica que el usuario decidió cambiar).
     */
    @Test
    fun namedSplitIdFromWizardIsAppliedAndPersistedAsARealChoice() {
        val input = PersonalizerInput(
            catalogEntryId = "native:machine-muscle",
            focus = TrainingFocus.FULL_BODY,
            frequency = 4,
            weekdays = listOf(1, 2, 4, 5),
            equipment = setOf("machine"),
            level = CatalogLevel.INTERMEDIATE,
            availableMinutes = 60,
            splitId = "ul_x4",
        )
        val program = requireNotNull(personalizer().personalize("named-split", input).program)
        assertEquals("ul_x4", program.selectedSplitId)
        val sessions = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }
        assertEquals(listOf("Torso", "Pierna", "Torso", "Pierna"), sessions.map { it.name })
    }
}
