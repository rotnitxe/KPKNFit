package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.programs.AdaptationPolicy
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingCapability
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C.P3 (DEC-w2-06): `PlanGoalMatcher` es la regla única de «esta entrada sirve a este
 * objetivo». La usan el prefiltro del wizard y la biblioteca de planes; lo que sigue fija la
 * regla, que la biblioteca y el wizard ofrecen lo mismo, y que ningún objetivo se decide por
 * la procedencia de la entrada ni por capacidades sueltas.
 */
class PlanGoalMatcherTest {

    private val productGoals = listOf(
        PlanGoalProfile.STRENGTH,
        PlanGoalProfile.MUSCLE,
        PlanGoalProfile.STRENGTH_MUSCLE,
        PlanGoalProfile.COMPLETE_ATHLETE,
    )

    private val all: List<CatalogEntry> = PersonalizedPlanCatalog.entries()

    private fun entry(id: String): CatalogEntry =
        requireNotNull(PersonalizedPlanCatalog.find(id)) { "falta $id en el catálogo" }

    private fun goalsOf(entry: CatalogEntry): List<PlanGoalProfile> =
        productGoals.filter { PlanGoalMatcher.matches(entry, it) }

    // ─── Los cuatro planes propios ────────────────────────────────────────────

    @Test
    fun theFourOwnPlansMatchOnlyTheirOwnGoal() {
        val own = mapOf(
            "native:strength-foundation-v2" to PlanGoalProfile.STRENGTH,
            "native:muscle-foundation-v2" to PlanGoalProfile.MUSCLE,
            "native:powerbuilding-foundation-v2" to PlanGoalProfile.STRENGTH_MUSCLE,
            "native:complete-athlete-v2" to PlanGoalProfile.COMPLETE_ATHLETE,
        )
        own.forEach { (id, goal) ->
            assertEquals("$id solo debe casar con $goal", listOf(goal), goalsOf(entry(id)))
            assertFalse("$id no es del modo mixto heredado", PlanGoalMatcher.matches(entry(id), PlanGoalProfile.LEGACY_MIXED))
        }
    }

    @Test
    fun powerbuildingFoundationDoesNotMatchStrengthEvenThoughItDeclaresStrengthCapability() {
        val powerbuilding = entry("native:powerbuilding-foundation-v2")
        assertTrue(
            "la entrada declara la capacidad de fuerza (el motivo del error de la biblioteca)",
            TrainingCapability.STRENGTH in powerbuilding.capabilities,
        )
        assertFalse(PlanGoalMatcher.matches(powerbuilding, PlanGoalProfile.STRENGTH))
        assertFalse(PlanGoalMatcher.matches(powerbuilding, PlanGoalProfile.MUSCLE))
        assertTrue(PlanGoalMatcher.matches(powerbuilding, PlanGoalProfile.STRENGTH_MUSCLE))
    }

    @Test
    fun completeAthleteMatchesExactlyTheEntriesThatDeclareTheFourCapabilities() {
        val athletes = all.filter { PlanGoalMatcher.matches(it, PlanGoalProfile.COMPLETE_ATHLETE) }.map { it.id }
        assertEquals(listOf("native:complete-athlete-v2"), athletes)
        all.forEach { entry ->
            assertEquals(
                "${entry.id}: Atleta completo ⇔ las cuatro capacidades",
                entry.capabilities.containsAll(PlanGoalMatcher.requiredCapabilities(PlanGoalProfile.COMPLETE_ATHLETE)),
                PlanGoalMatcher.matches(entry, PlanGoalProfile.COMPLETE_ATHLETE),
            )
        }
    }

    // ─── Métodos de tercero y planes de autor ─────────────────────────────────

    @Test
    fun boringButBigMatchesStrengthAndStrengthMuscleButNotMuscle() {
        val bbb = entry("protocol:wendler-531-bbb")
        assertEquals(listOf(PlanGoalProfile.STRENGTH, PlanGoalProfile.STRENGTH_MUSCLE), goalsOf(bbb))
        assertFalse(PlanGoalMatcher.matches(bbb, PlanGoalProfile.MUSCLE))
    }

    @Test
    fun firstSetLastAndTheSpecializationsStayInStrengthOnly() {
        listOf("protocol:wendler-531-fsl", "protocol:smolov", "protocol:smolov-jr", "protocol:coan-phillipi-dl").forEach { id ->
            assertEquals("$id solo es de Fuerza", listOf(PlanGoalProfile.STRENGTH), goalsOf(entry(id)))
        }
    }

    @Test
    fun theAuthoredPhulAndPhatServeStrengthMuscleAndMuscleButNotStrength() {
        listOf("original:phul-ms-2021-r1", "adapted:phul-kpkn-r1", "original:phat-biolayne-2016-r1", "adapted:phat-kpkn-r1").forEach { id ->
            assertEquals(
                "$id sirve a Fuerza y músculo y a Músculo",
                listOf(PlanGoalProfile.MUSCLE, PlanGoalProfile.STRENGTH_MUSCLE),
                goalsOf(entry(id)),
            )
        }
    }

    // ─── Modo mixto y salud heredados ─────────────────────────────────────────

    @Test
    fun strengthCardioMatchesOnlyTheLegacyMixedGoal() {
        val strengthCardio = entry("native:strength-cardio")
        assertTrue("sin disciplina propia: no es de ningún objetivo de producto", goalsOf(strengthCardio).isEmpty())
        assertTrue(PlanGoalMatcher.matches(strengthCardio, PlanGoalProfile.LEGACY_MIXED))
        assertEquals(
            "es la única entrada que programa cardio",
            listOf("native:strength-cardio"),
            all.filter { PlanGoalMatcher.matches(it, PlanGoalProfile.LEGACY_MIXED) }.map { it.id },
        )
    }

    @Test
    fun legacyHealthDoesNotFilterByDiscipline() {
        // El wizard no le da disciplina propia: su referencia sale del estilo declarado en la calibración.
        all.forEach { assertTrue("${it.id} con salud heredada", PlanGoalMatcher.matches(it, PlanGoalProfile.LEGACY_HEALTH)) }
    }

    @Test
    fun onlyCompleteAthleteRequiresCapabilities() {
        assertEquals(
            setOf(
                TrainingCapability.STRENGTH,
                TrainingCapability.HYPERTROPHY,
                TrainingCapability.POWER,
                TrainingCapability.CARDIO,
            ),
            PlanGoalMatcher.requiredCapabilities(PlanGoalProfile.COMPLETE_ATHLETE),
        )
        PlanGoalProfile.entries.filter { it != PlanGoalProfile.COMPLETE_ATHLETE }.forEach { goal ->
            assertTrue("$goal se decide por disciplina, no por capacidades", PlanGoalMatcher.requiredCapabilities(goal).isEmpty())
        }
    }

    // ─── Reparto de las 55 entradas ───────────────────────────────────────────

    @Test
    fun everyEntryIsAccountedForAndOnlyTheStructuresAndStrengthCardioMatchNoProductGoal() {
        val table = all.associate { it.id to goalsOf(it) }
        println("[C.P3] entradas del catálogo: ${all.size}")
        productGoals.forEach { goal ->
            val matched = table.filterValues { goal in it }.keys
            println("[C.P3] objetivo $goal: ${matched.size} entradas -> ${matched.sorted()}")
        }
        val unmatched = table.filterValues { it.isEmpty() }.keys
        println("[C.P3] entradas sin ningún objetivo de producto (caen en «Otros»): ${unmatched.sorted()}")
        assertEquals(
            setOf("template:simple-1", "template:simple-ab", "template:simple-4", "native:strength-cardio"),
            unmatched,
        )
    }

    // ─── La biblioteca y el wizard ofrecen lo mismo ───────────────────────────

    @Test
    fun theWizardPlannerAndTheLibraryMatcherOfferTheSameEntriesForEveryGoal() {
        val published = PersonalizedPlanCatalog.listedEntries().filter { it.publication == PublicationState.PUBLISHED }
        val equipment = setOf("general_gym")
        val referenceOf = mapOf(
            PlanGoalProfile.STRENGTH to TrainingReference.POWERLIFTING,
            PlanGoalProfile.MUSCLE to TrainingReference.HYPERTROPHY,
            PlanGoalProfile.STRENGTH_MUSCLE to TrainingReference.POWERBUILDING,
        )
        (1..6).forEach { days ->
            referenceOf.forEach { (goal, reference) ->
                val planner = SetupTrainingPlanner.candidates(
                    SetupTrainingPlannerInput(reference, days, equipment, CatalogLevel.INTERMEDIATE, TrainingFocus.FULL_BODY),
                ).map { it.id }.toSet()
                val library = published
                    .filter { PlanGoalMatcher.matches(it, goal) && days in it.supportedFrequencies }
                    .map { it.id }
                    .toSet()
                assertEquals("$goal/${days}d: la biblioteca y el wizard ofrecen lo mismo", library, planner)
            }
            val athletePlanner = SetupTrainingPlanner.candidates(
                SetupTrainingPlannerInput(
                    reference = null,
                    frequency = days,
                    equipment = equipment,
                    level = CatalogLevel.INTERMEDIATE,
                    focus = TrainingFocus.FULL_BODY,
                    requiredCapabilities = PlanGoalMatcher.requiredCapabilities(PlanGoalProfile.COMPLETE_ATHLETE),
                ),
            ).map { it.id }.toSet()
            val athleteLibrary = published
                .filter { PlanGoalMatcher.matches(it, PlanGoalProfile.COMPLETE_ATHLETE) && days in it.supportedFrequencies }
                .map { it.id }
                .toSet()
            assertEquals("Atleta/${days}d", athleteLibrary, athletePlanner)

            val mixedPlanner = SetupTrainingPlanner.candidates(
                SetupTrainingPlannerInput(
                    reference = null,
                    frequency = days,
                    equipment = equipment,
                    level = CatalogLevel.INTERMEDIATE,
                    focus = TrainingFocus.FULL_BODY,
                    mixedTraining = true,
                ),
            ).map { it.id }.toSet()
            val mixedLibrary = published
                .filter { PlanGoalMatcher.matches(it, PlanGoalProfile.LEGACY_MIXED) && days in it.supportedFrequencies }
                .map { it.id }
                .toSet()
            assertEquals("Mixto/${days}d", mixedLibrary, mixedPlanner)
        }
    }

    // ─── La regla sobre metadatos, no sobre procedencia ───────────────────────

    private fun handBuilt(
        source: CatalogSource,
        sourceId: String,
        references: Set<TrainingReference> = emptySet(),
        capabilities: Set<TrainingCapability> = emptySet(),
    ) = CatalogEntry(
        id = "${source.name.lowercase()}:$sourceId",
        source = source,
        sourceId = sourceId,
        title = "Plan",
        technicalSubtitle = "Semana",
        description = "Descripción",
        requiredEquipment = setOf("general_gym"),
        supportedFrequencies = 1..6,
        level = CatalogLevel.BEGINNER,
        duration = CatalogDuration.REPEATING_WEEK,
        supportedFocuses = setOf(TrainingFocus.FULL_BODY),
        adaptation = AdaptationPolicy.CURATED_WEEKLY,
        publication = PublicationState.PUBLISHED,
        references = references,
        capabilities = capabilities,
    )

    @Test
    fun theRuleFollowsTheDeclaredReferencesAndNeverTheProvenance() {
        CatalogSource.entries.forEach { source ->
            assertEquals(
                "$source con powerlifting declarado",
                listOf(PlanGoalProfile.STRENGTH),
                goalsOf(handBuilt(source, "pl", references = setOf(TrainingReference.POWERLIFTING))),
            )
            assertEquals(
                "$source con hipertrofia declarada",
                listOf(PlanGoalProfile.MUSCLE),
                goalsOf(handBuilt(source, "hy", references = setOf(TrainingReference.HYPERTROPHY))),
            )
            assertEquals(
                "$source con powerbuilding declarado",
                listOf(PlanGoalProfile.STRENGTH_MUSCLE),
                goalsOf(handBuilt(source, "pb", references = setOf(TrainingReference.POWERBUILDING))),
            )
            assertTrue("$source sin nada declarado", goalsOf(handBuilt(source, "none")).isEmpty())
        }
    }

    @Test
    fun capabilitiesAloneNeverMakeAnEntryStrengthMuscleOrAthleteUnlessAllFourAreDeclared() {
        val strengthOnly = handBuilt(CatalogSource.NATIVE, "caps-s", capabilities = setOf(TrainingCapability.STRENGTH))
        assertTrue("la capacidad de fuerza sin disciplina no es Fuerza", goalsOf(strengthOnly).isEmpty())
        val threeOfFour = handBuilt(
            CatalogSource.NATIVE,
            "caps-3",
            capabilities = setOf(TrainingCapability.STRENGTH, TrainingCapability.HYPERTROPHY, TrainingCapability.POWER),
        )
        assertFalse("tres de cuatro capacidades no son Atleta completo", PlanGoalMatcher.matches(threeOfFour, PlanGoalProfile.COMPLETE_ATHLETE))
        val fourOfFour = handBuilt(CatalogSource.NATIVE, "caps-4", capabilities = TrainingCapability.entries.toSet())
        assertEquals(listOf(PlanGoalProfile.COMPLETE_ATHLETE), goalsOf(fourOfFour))
    }

    @Test
    fun onlyTheNativeStrengthCardioFamilySchedulesCardio() {
        assertTrue(PlanGoalMatcher.matches(handBuilt(CatalogSource.NATIVE, "strength-cardio"), PlanGoalProfile.LEGACY_MIXED))
        assertFalse(PlanGoalMatcher.matches(handBuilt(CatalogSource.TEMPLATE, "strength-cardio"), PlanGoalProfile.LEGACY_MIXED))
        assertFalse(PlanGoalMatcher.matches(handBuilt(CatalogSource.NATIVE, "other"), PlanGoalProfile.LEGACY_MIXED))
    }
}
