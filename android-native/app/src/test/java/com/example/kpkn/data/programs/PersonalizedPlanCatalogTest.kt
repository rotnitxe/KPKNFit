package com.example.kpkn.data.programs

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanProvenance
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
    fun legacy_volume_cap_uses_primary_and_keeps_stabilizer_totals_in_the_report() {
        fun withNeckRole(primary: Boolean) = catalog.copy(families = catalog.families.map { family ->
            family.copy(definitions = family.definitions.map { definition ->
                definition.copy(configurations = definition.configurations.map { configuration ->
                    val profile = configuration.profile
                    configuration.copy(profile = profile.copy(
                        primaryMuscles = (profile.primaryMuscles.filterNot { it == "neck" } + if (primary) listOf("neck") else emptyList()).distinct(),
                        secondaryMuscles = profile.secondaryMuscles.filterNot { it == "neck" },
                        stabilizerMuscles = (profile.stabilizerMuscles.filterNot { it == "neck" } + if (primary) emptyList() else listOf("neck")).distinct(),
                    ))
                })
            })
        })
        val input = PersonalizerInput(
            "native:machine-muscle", TrainingFocus.FULL_BODY, 2,
            equipment = setOf("machine"), level = CatalogLevel.INTERMEDIATE, availableMinutes = 100,
            volumeRecommendations = listOf(VolumeRecommendation("Cuello", 0, 1, 1)),
        )
        val control = personalizer().personalize("legacy-role-neck-control", input)
        assertNotNull("control con frecuencia publicada: ${control.report.limitations}", control.program)
        val indirectCatalog = withNeckRole(primary = false)
        val allowed = SimpleCyclePersonalizer(InMemoryExerciseCatalogRepositoryV2(indirectCatalog).also {
            runBlocking { it.load() }
        }).personalize("legacy-role-neck", input)
        assertNotNull("el total indirecto no impide la sesión: ${allowed.report.limitations}", allowed.program)
        val row = allowed.report.muscles.single { it.muscle == "Cuello" }
        assertEquals(1, row.mrv)
        assertEquals(0.0, row.directSets, 0.001)
        assertTrue("el informe conserva el volumen estabilizador real", row.indirectSets > row.mrv)
        assertTrue(allowed.report.limitations.any { it.startsWith("Cuello:") && "0 principales" in it })
        assertTrue("LEGACY no consume la banda propia", allowed.report.highVolume.isEmpty())
        val primaryCatalog = withNeckRole(primary = true)
        val rejected = SimpleCyclePersonalizer(InMemoryExerciseCatalogRepositoryV2(primaryCatalog).also {
            runBlocking { it.load() }
        }).personalize("legacy-role-neck-primary", input)
        assertNull("con límite PRIMARY=1 no caben las tres variantes mínimas", rejected.program)
        assertTrue("un rechazo legacy no usa la banda propia", rejected.report.highVolume.isEmpty())
    }

    @Test
    fun nativeFamiliesArePublishedAndNamespaced() {
        val entries = PersonalizedPlanCatalog.entries()
        // 8 familias históricas + los cuatro planes propios de §11.1 (T-004a) que arma el personalizador nativo, y los
        // diez programas «a medida» del generador de Entreno v2 (también NATIVE, sin listar).
        assertEquals(12, entries.count { it.source == CatalogSource.NATIVE && !it.isGenerated })
        assertEquals(PersonalizedPlanCatalog.GENERATED_IDS, entries.filter { it.isGenerated }.map { it.id })
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

    /**
     * C.P2b (D2): `entries()` conserva los 12 nativos, pero solo 7 se ofrecen. Los cinco ocultos
     * son los históricos que ya no se listan; `machine-muscle`, `bodyweight` y `strength-cardio`
     * siguen listados (relegados) y los cuatro planes propios también.
     */
    @Test
    fun listedEntriesOfferSevenNativesAndHideTheFiveHistoricals() {
        val hidden = listOf(
            "native:full-body",
            "native:gym-muscle",
            "native:one-day",
            "native:return-training",
            "native:home-training",
        )
        val all = PersonalizedPlanCatalog.entries()
        val listed = PersonalizedPlanCatalog.listedEntries()

        // Los diez programas «a medida» del generador (Entreno v2) también son NATIVE sin listar: no cuentan aquí.
        assertEquals("nativos en entries()", 12, all.count { it.source == CatalogSource.NATIVE && !it.isGenerated })
        assertEquals("nativos en listedEntries()", 7, listed.count { it.source == CatalogSource.NATIVE })
        assertEquals(
            "ocultos exactos",
            (hidden + PersonalizedPlanCatalog.GENERATED_IDS).sorted(),
            all.filterNot { it.listed }.map { it.id }.sorted(),
        )
        assertEquals(
            "los nativos que se ofrecen",
            listOf(
                "native:bodyweight",
                "native:complete-athlete-v2",
                "native:machine-muscle",
                "native:muscle-foundation-v2",
                "native:powerbuilding-foundation-v2",
                "native:strength-cardio",
                "native:strength-foundation-v2",
            ),
            listed.filter { it.source == CatalogSource.NATIVE }.map { it.id }.sorted(),
        )
        // Lo que no es nativo no se oculta: plantillas y métodos están completos en ambas listas.
        assertEquals(
            all.count { it.source != CatalogSource.NATIVE },
            listed.count { it.source != CatalogSource.NATIVE },
        )
        // Ocultar no borra: cada histórico sigue resolviendo y sigue siendo ejecutable por id.
        hidden.forEach { id ->
            val entry = requireNotNull(PersonalizedPlanCatalog.find(id)) { "falta $id en find()" }
            assertFalse("$id debe estar oculto", entry.listed)
            assertEquals(PublicationState.PUBLISHED, entry.publication)
        }
    }

    /**
     * C.P2b (D2): el planner no devuelve nunca ninguno de los cinco ocultos, sea cual sea la
     * referencia, la frecuencia (1..6 y sin frecuencia), el nivel, el material o el modo.
     * BBB aparece en Fuerza y músculo a 4 días; `strength-cardio` ya no cuenta como Músculo
     * pero sigue siendo el único candidato del modo mixto.
     */
    @Test
    fun plannerNeverOffersTheHiddenHistoricalsAndStillServesTheMixedGoal() {
        val hidden = setOf(
            "native:full-body",
            "native:gym-muscle",
            "native:one-day",
            "native:return-training",
            "native:home-training",
        )
        val references = listOf<TrainingReference?>(null) + TrainingReference.entries
        val frequencies = listOf<Int?>(null) + (1..6).toList()
        val materials = listOf(setOf("general_gym"), setOf("bodyweight"), emptySet())
        var evaluated = 0
        references.forEach { reference ->
            frequencies.forEach { frequency ->
                CatalogLevel.entries.forEach { level ->
                    materials.forEach { equipment ->
                        listOf(false, true).forEach { mixed ->
                            listOf(false, true).forEach { protocolOnly ->
                                val ids = SetupTrainingPlanner.candidates(
                                    SetupTrainingPlannerInput(
                                        reference = reference,
                                        frequency = frequency,
                                        equipment = equipment,
                                        level = level,
                                        focus = TrainingFocus.FULL_BODY,
                                        protocolOnly = protocolOnly,
                                        mixedTraining = mixed,
                                    ),
                                ).map { it.id }
                                evaluated++
                                assertTrue(
                                    "histórico oculto en el planner: ${ids.filter { it in hidden }} " +
                                        "(ref=$reference freq=$frequency nivel=$level mixto=$mixed solo-protocolos=$protocolOnly)",
                                    ids.none { it in hidden },
                                )
                            }
                        }
                    }
                }
            }
        }
        assertEquals("combinaciones recorridas", 4 * 7 * 3 * 3 * 2 * 2, evaluated)

        fun plan(
            reference: TrainingReference?,
            frequency: Int,
            level: CatalogLevel = CatalogLevel.INTERMEDIATE,
            mixed: Boolean = false,
        ) = SetupTrainingPlanner.candidates(
            SetupTrainingPlannerInput(
                reference = reference,
                frequency = frequency,
                equipment = setOf("general_gym"),
                level = level,
                focus = TrainingFocus.FULL_BODY,
                mixedTraining = mixed,
            ),
        ).map { it.id }

        // BBB: powerlifting y powerbuilding, solo a 4 días.
        val bbb = "protocol:wendler-531-bbb"
        assertTrue("BBB en Fuerza y músculo a 4 días", bbb in plan(TrainingReference.POWERBUILDING, 4))
        assertTrue("BBB sigue en Fuerza a 4 días", bbb in plan(TrainingReference.POWERLIFTING, 4))
        assertFalse("BBB no está en Músculo", bbb in plan(TrainingReference.HYPERTROPHY, 4))
        listOf(1, 2, 3, 5, 6).forEach { days ->
            assertFalse("BBB solo existe a 4 días (pedidos $days)", bbb in plan(TrainingReference.POWERBUILDING, days))
        }
        assertTrue(
            "FSL no entra en Fuerza y músculo: sigue solo en powerlifting",
            "protocol:wendler-531-fsl" !in plan(TrainingReference.POWERBUILDING, 4),
        )

        // strength-cardio: fuera de Músculo, dentro del modo mixto con cualquier frecuencia 1..6.
        val strengthCardio = "native:strength-cardio"
        (1..6).forEach { days ->
            assertFalse(
                "strength-cardio ya no es Músculo ($days días)",
                strengthCardio in plan(TrainingReference.HYPERTROPHY, days),
            )
            assertTrue(
                "strength-cardio sigue sirviendo el modo mixto ($days días)",
                strengthCardio in plan(TrainingReference.HYPERTROPHY, days, mixed = true),
            )
            assertEquals(
                "en el modo mixto es el único candidato ($days días)",
                listOf(strengthCardio),
                plan(TrainingReference.HYPERTROPHY, days, mixed = true),
            )
        }
        // Los históricos que se conservan listados siguen ofreciéndose en Músculo.
        val muscleAtThreeDays = plan(TrainingReference.HYPERTROPHY, 3, CatalogLevel.BEGINNER)
        assertTrue("machine-muscle sigue listado", "native:machine-muscle" in muscleAtThreeDays)
        assertTrue("bodyweight sigue listado", "native:bodyweight" in muscleAtThreeDays)
        assertTrue("el propio de Músculo está", "native:muscle-foundation-v2" in muscleAtThreeDays)
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
        // Los programas «a medida» no son del personalizador nativo: los arma el generador (sus pruebas, aparte).
        PersonalizedPlanCatalog.entries().filter { it.source == CatalogSource.NATIVE && !it.isGenerated }.forEach { entry ->
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
                val ceiling = if (com.example.kpkn.data.protocols.definitions.NativeProfileKind.fromEntryId(entry.id) != null) {
                    com.example.kpkn.domain.training.VolumeSoftBand.softCeilingFor(row.muscle, row.mrv)
                } else minOf(row.mav, row.mrv).toDouble()
                assertTrue("${entry.id}/${row.muscle}: principales=${row.directSets}, total=${row.directSets + row.indirectSets}",
                    row.directSets <= ceiling + 0.001)
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
        // PHUL original: receta con repeats = true y 12 semanas, es un ciclo que se repite.
        assertEquals(CatalogDuration.REPEATING_CYCLE, phul.duration)
        assertEquals(PublicationState.PUBLISHED, phul.publication)
        assertEquals(PlanProvenanceClass.ORIGINAL, phul.provenance!!.category)
        val phulSource = requireNotNull(phul.authoredSource)
        assertEquals("2026-09-28", phulSource.consultedOn)
        assertTrue(phulSource.effectiveRules.isNotEmpty())
        assertTrue(phulSource.kpknDefaults.isNotEmpty())
        assertTrue(PlanLabels.provenanceLabel(phul).contains("Original fiel"))

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
        assertTrue(PlanLabels.provenanceLabel(adaptedPhat).contains("Adaptación KPKN"))
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
            // B.S6 parte 1: la H5a `*` de PHUL heredado estaba muerta (sus series de potencia van al 80-82 % del TM, por debajo del 85 % que
            // cuenta como «pesada») y se retiró, así que solo PHAT heredado conserva su exención (la H3, que sigue viva). El oráculo
            // anterior pedía exenciones en las dos.
            assertEquals(
                "$legacyId: exenciones del histórico",
                legacyId == "protocol:phat-verified",
                recipe.exemptions.isNotEmpty(),
            )
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

    // ─── C.P1 · niveles de plantillas, tercera duración y findForProgram ─────

    @Test
    fun complex_templates_take_their_level_from_the_recipe_and_simple_ones_serve_every_level() {
        val expected = mapOf(
            "template:power-12-3" to CatalogLevel.BEGINNER,
            "template:power-16-4" to CatalogLevel.INTERMEDIATE,
            "template:power-20-5" to CatalogLevel.ADVANCED,
            "template:powerbuild-16-4" to CatalogLevel.ADVANCED,
            "template:body-12-3" to CatalogLevel.INTERMEDIATE,
            "template:body-16-4" to CatalogLevel.ADVANCED,
            "template:body-20-5" to CatalogLevel.ADVANCED,
        )
        expected.forEach { (id, level) ->
            val entry = requireNotNull(PersonalizedPlanCatalog.find(id)) { "falta $id" }
            assertEquals("$id nivel base", level, entry.level)
            assertEquals("$id niveles", setOf(level), entry.levels)
        }
        listOf("template:simple-1", "template:simple-ab", "template:simple-4").forEach { id ->
            val entry = requireNotNull(PersonalizedPlanCatalog.find(id)) { "falta $id" }
            assertEquals("$id nivel base", CatalogLevel.BEGINNER, entry.level)
            assertEquals("$id niveles", CatalogLevel.entries.toSet(), entry.levels)
        }
    }

    @Test
    fun recipes_that_repeat_over_several_weeks_are_a_repeating_cycle() {
        listOf(
            "protocol:texas-method-3d",
            "protocol:texas-method-4d",
            "protocol:wendler-531-bbb",
            "protocol:wendler-531-fsl",
            "protocol:madcow-5x5",
            "protocol:nsuns-531-lp-4d",
            "protocol:gzclp",
            "protocol:westside-conjugate",
            "protocol:phul-verified",
            "protocol:phat-verified",
            AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
        ).forEach { id ->
            val entry = requireNotNull(PersonalizedPlanCatalog.find(id)) { "falta $id" }
            val weeks = entry.recipe!!.weeks.size
            assertEquals("$id duración", CatalogDuration.REPEATING_CYCLE, entry.duration)
            assertEquals("$id semanas", weeks, entry.durationWeeks)
            assertEquals("$id etiqueta", "Ciclo de $weeks semanas que se repite", PlanLabels.durationLabel(entry.duration, weeks))
        }
    }

    @Test
    fun finite_recipes_stay_finite_and_a_week_that_repeats_stays_a_week() {
        listOf(
            "protocol:calgary-16",
            "protocol:smolov",
            "protocol:coan-phillipi-dl",
            "template:power-12-3",
            AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID,
        ).forEach { id ->
            assertEquals(id, CatalogDuration.FINITE_CYCLE, requireNotNull(PersonalizedPlanCatalog.find(id)).duration)
        }
        listOf("native:machine-muscle", "native:one-day", "template:simple-1").forEach { id ->
            assertEquals(id, CatalogDuration.REPEATING_WEEK, requireNotNull(PersonalizedPlanCatalog.find(id)).duration)
        }
    }

    private fun programOf(
        structureTemplateId: String? = null,
        sourceProtocolId: String? = null,
        planId: String? = null,
    ) = Program(
        id = "programa-de-prueba",
        name = "Programa de prueba",
        structureTemplateId = structureTemplateId,
        sourceProtocolId = sourceProtocolId,
        planProvenance = planId?.let { PlanProvenance(planId = it) },
    )

    @Test
    fun findForProgram_resolves_native_template_protocol_and_authored_programs() {
        fun resolve(program: Program) = PersonalizedPlanCatalog.findForProgram(program)?.id
        // Nativo: el personalizador guarda el id completo de la entrada.
        assertEquals("native:machine-muscle", resolve(programOf(structureTemplateId = "native:machine-muscle")))
        assertEquals("native:muscle-foundation-v2", resolve(programOf(structureTemplateId = "native:muscle-foundation-v2")))
        // Plantilla: los motores guardan el id desnudo de la plantilla.
        assertEquals("template:power-12-3", resolve(programOf(structureTemplateId = "power-12-3", sourceProtocolId = "power-12-3")))
        assertEquals("template:simple-1", resolve(programOf(structureTemplateId = "simple-1")))
        // Protocolo: id desnudo de la receta y del protocolo, o solo el del protocolo.
        assertEquals(
            "protocol:texas-method-3d",
            resolve(programOf(structureTemplateId = "texas-method-3d", sourceProtocolId = "texas-method-3d")),
        )
        assertEquals("protocol:gzclp", resolve(programOf(sourceProtocolId = "gzclp")))
        // Autoradas: la procedencia declarada o el id de la entrada.
        assertEquals(
            AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
            resolve(programOf(planId = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID)),
        )
        assertEquals(
            AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID,
            resolve(programOf(structureTemplateId = AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID)),
        )
    }

    @Test
    fun findForProgram_prefers_the_declared_provenance_and_returns_null_without_a_match() {
        assertEquals(
            AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
            PersonalizedPlanCatalog.findForProgram(
                programOf(planId = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID, structureTemplateId = "native:machine-muscle"),
            )?.id,
        )
        assertNull(PersonalizedPlanCatalog.findForProgram(programOf()))
        assertNull(PersonalizedPlanCatalog.findForProgram(programOf(structureTemplateId = "no-existe", sourceProtocolId = "tampoco")))
    }

    @Test
    fun findForProgram_resolves_every_entry_from_the_ids_the_engines_store() {
        PersonalizedPlanCatalog.entries().filter { it.source == CatalogSource.NATIVE }.forEach { entry ->
            assertEquals(entry.id, PersonalizedPlanCatalog.findForProgram(programOf(structureTemplateId = entry.id))?.id)
        }
        PROGRAM_TEMPLATES.forEach { template ->
            val program = programOf(structureTemplateId = template.id, sourceProtocolId = template.id)
            assertEquals("template:${template.id}", PersonalizedPlanCatalog.findForProgram(program)?.id)
        }
        PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.forEach { protocol ->
            val program = programOf(structureTemplateId = protocol.recipe!!.id, sourceProtocolId = protocol.id)
            assertEquals("protocol:${protocol.id}", PersonalizedPlanCatalog.findForProgram(program)?.id)
        }
    }
}
