package com.example.kpkn.data.programs

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.onboarding.SetupTrainingPlanner
import com.example.kpkn.domain.onboarding.SetupTrainingPlannerInput
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
        // 8 familias históricas + los cuatro planes propios de §11.1 (T-004a).
        assertEquals(12, entries.count { it.source == CatalogSource.NATIVE })
        listOf(
            "native:full-body",
            "native:gym-muscle",
            "native:machine-muscle",
            "native:home-training",
            "native:bodyweight",
            "native:strength-cardio",
            "native:return-training",
            "native:one-day",
            "native:strength-foundation-v2",
            "native:muscle-foundation-v2",
            "native:powerbuilding-foundation-v2",
            "native:complete-athlete-v2",
        ).forEach { id -> assertTrue("falta la entrada nativa $id", entries.any { it.id == id }) }
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
                // The new competition-strength plan requires explicit SBD support;
                // `general_gym` is intentionally not a substitute for those declarations.
                "strength-foundation" -> setOf("barbell", "rack", "bench")
                "powerbuilding-foundation" -> setOf("barbell", "rack", "bench")
                else -> setOf("general_gym")
            }
            val input = PersonalizerInput(entry.id, TrainingFocus.FULL_BODY, entry.supportedFrequencies.first, equipment = equipment, level = CatalogLevel.INTERMEDIATE, availableMinutes = 100)
            val result = planner.personalize("p-${entry.sourceId}", input)
            assertNotNull("${entry.id}: ${result.report.limitations}", result.program)
            val program = result.program!!
            assertEquals("p-${entry.sourceId}", program.id)
            val weeks = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            val sessions = weeks.flatMap { it.sessions }
            // Los planes propios materializan 6 semanas (§12.1); los históricos
            // siguen siendo su microciclo cíclico. En ambos: una sesión por día.
            weeks.forEach { week ->
                assertEquals("${entry.id} en la semana ${week.name}", input.frequency, week.sessions.size)
            }
            assertTrue("${entry.id} sin sesiones", sessions.size == input.frequency * weeks.size)
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

    // ─── §10.1: entradas autoradas e IDs históricos (paquete E, T-002b) ──────

    @Test
    fun authored_entries_are_published_with_identity_days_and_provenance() {
        val entries = PersonalizedPlanCatalog.entries()
        val ids = entries.map { it.id }
        listOf(
            AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
            AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID,
            AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID,
            AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID,
        ).forEach { assertTrue("falta la entrada $it", it in ids) }
        assertEquals("ids de entrada únicos", entries.size, ids.distinct().size)

        val phul = requireNotNull(PersonalizedPlanCatalog.find(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID))
        assertEquals(4, phul.supportedFrequencies.first)
        assertEquals(12, phul.recipe!!.weeks.size)
        assertEquals(CatalogLevel.INTERMEDIATE, phul.level)
        assertEquals(CatalogDuration.FINITE_CYCLE, phul.duration)
        assertEquals(PublicationState.PUBLISHED, phul.publication)
        assertEquals(PlanProvenanceClass.ORIGINAL, phul.provenance!!.category)
        val phulSource = requireNotNull(phul.authoredSource)
        assertEquals("2026-09-28", phulSource.consultedOn)
        assertTrue(phulSource.effectiveRules.isNotEmpty())
        assertTrue(phulSource.kpknDefaults.isNotEmpty())
        assertTrue(phul.technicalSubtitle.contains("Original fiel"))

        val phat = requireNotNull(PersonalizedPlanCatalog.find(AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID))
        assertEquals(5, phat.supportedFrequencies.first)
        assertEquals(6, phat.recipe!!.weeks.size)
        assertEquals(CatalogLevel.ADVANCED, phat.level)
        assertEquals("Layne Norton", phat.provenance!!.sourceAuthor)
        assertTrue(phat.provenance!!.sourceEdition!!.contains("2016-05-30"))
        assertTrue(phat.provenance!!.sourceEdition!!.contains("2026-09-28"))

        val adaptedPhat = requireNotNull(PersonalizedPlanCatalog.find(AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID))
        assertEquals(PlanProvenanceClass.ADAPTED, adaptedPhat.provenance!!.category)
        assertEquals(AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID, adaptedPhat.provenance!!.parentId)
        assertEquals(1, adaptedPhat.provenance!!.parentRevision)
        assertTrue(adaptedPhat.technicalSubtitle.contains("Adaptación KPKN"))
        // Misma tabla que su original: mismos días y oráculos de series.
        assertEquals(
            phat.recipe!!.weeks.first().days.map { day -> day.id to day.slots.size },
            adaptedPhat.recipe!!.weeks.first().days.map { day -> day.id to day.slots.size },
        )
        // Disciplina: powerbuilding real; nunca powerlifting (§11.1).
        listOf(phul, phat, adaptedPhat, requireNotNull(PersonalizedPlanCatalog.find(AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID))).forEach {
            assertEquals("${it.id} referencias", setOf(TrainingReference.POWERBUILDING, TrainingReference.HYPERTROPHY), it.references)
            assertFalse("${it.id} no es powerlifting", TrainingReference.POWERLIFTING in it.references)
        }
    }

    @Test
    fun legacy_phul_phat_ids_still_resolve_and_report_the_previous_version() {
        listOf(
            "protocol:phul-verified" to AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
            "protocol:phat-verified" to AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID,
        ).forEach { (legacyId, successor) ->
            val direct = PersonalizedPlanCatalog.find(legacyId)
            assertNotNull("el id histórico sigue resolviendo: $legacyId", direct)
            val lookup = requireNotNull(PersonalizedPlanCatalog.lookup(legacyId))
            assertTrue("$legacyId debe marcarse como histórico", lookup.isLegacyVersion)
            assertEquals("Versión anterior", lookup.versionLabel)
            assertEquals(successor, lookup.successorId)
            assertEquals(legacyId, lookup.entry.id)
            // El deep link por el id desnudo también resuelve.
            val bare = requireNotNull(PersonalizedPlanCatalog.lookup(legacyId.removePrefix("protocol:")))
            assertEquals(legacyId, bare.entry.id)

            // Snapshot histórico intacto: la receta no se muta ni se reinterpretan sus porcentajes.
            val recipe = requireNotNull(direct!!.recipe)
            assertEquals(legacyId.removePrefix("protocol:"), recipe.id)
            assertEquals(4, recipe.weeks.size)
            val t1 = recipe.weeks.first().days.first().slots.first { it.role == SlotRole.T1_MAIN }
            assertEquals("ancla de %TM histórica preservada", 82.0, t1.sets.first().percent!!, 0.0001)
            assertEquals(LoadBasis.PERCENT_TM, t1.sets.first().loadBasis)
            assertTrue(recipe.exemptions.isNotEmpty())
        }
        // La entrada nueva NO es histórica.
        val current = requireNotNull(PersonalizedPlanCatalog.lookup(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID))
        assertFalse(current.isLegacyVersion)
        assertNull(current.versionLabel)
        assertNull(current.successorId)
        assertNull(PersonalizedPlanCatalog.lookup("no-existe"))
    }

    @Test
    fun recommendations_offer_the_authored_entries_and_keep_the_legacy_protocols() {
        val base = SetupTrainingPlannerInput(
            reference = TrainingReference.POWERBUILDING,
            frequency = 4,
            equipment = setOf("general_gym"),
            level = CatalogLevel.INTERMEDIATE,
            focus = TrainingFocus.FULL_BODY,
        )
        val fourDays = SetupTrainingPlanner.candidates(base).map { it.id }
        assertTrue("PHUL original recomendable: $fourDays", AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID in fourDays)
        assertTrue("PHUL adaptado recomendable", AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID in fourDays)
        assertTrue("el PHUL histórico sigue disponible", "protocol:phul-verified" in fourDays)

        val fiveDays = SetupTrainingPlanner.candidates(base.copy(frequency = 5)).map { it.id }
        assertTrue("PHAT original recomendable: $fiveDays", AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID in fiveDays)
        assertTrue("PHAT adaptado recomendable", AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID in fiveDays)
        assertTrue("el PHAT histórico sigue disponible", "protocol:phat-verified" in fiveDays)
    }
}
