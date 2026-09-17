package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.RecipeCompositionExemption
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.rpeSets
import com.example.kpkn.data.protocols.repeatPercentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class SessionCompositionPolicyTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val metadata get() = CatalogCompositionTestSupport.metadata

    @Test
    fun antiExample_four_quads_fails_h2_h3_h4() {
        val day = day(
            "Anti",
            listOf(
                slot("a", SlotRole.T1_MAIN, CatalogIds.SQ_FRONT, repeatPercentSets(3, 5, 70.0, 180), 180, LiftSlot.SQUAT),
                slot("b", SlotRole.T2_SUPPLEMENTAL, CatalogIds.SQ_HIGH, repeatPercentSets(3, 5, 70.0, 150), 150, LiftSlot.SQUAT),
                slot("c", SlotRole.T3_ACCESSORY, CatalogIds.PRESS_LEG, rpeSets(3, 10, 8.0), 120),
                slot("d", SlotRole.T3_ACCESSORY, CatalogIds.LEG_EXT, rpeSets(3, 12, 8.0), 60),
                slot("e", SlotRole.T3_ACCESSORY, CatalogIds.RDL, rpeSets(3, 8, 8.0), 120, LiftSlot.DEADLIFT),
            ),
        )
        val recipe = TrainingPlanRecipe(
            id = "anti",
            weeks = listOf(weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, listOf(day))),
        )
        val hard = ProgramRecipeValidator.hardFindings(recipe, metadata)
        val rules = hard.map { it.rule }.toSet()
        assertTrue("Debe fallar H2: $hard", "H2" in rules)
        assertTrue("Debe fallar H3: $hard", "H3" in rules)
        assertTrue("Debe fallar H4: $hard", "H4" in rules)
    }

    @Test
    fun exampleA_pl_squat_passes() {
        val recipe = TrainingPlanRecipe(
            id = "ex-a",
            weeks = listOf(
                weekRecipe(
                    2, 1, "Fuerza", BlockGoal.INTENSIFICATION,
                    listOf(
                        DayArchetypes.plSquat(80.0, weekday = 1),
                        DayArchetypes.plDeadlift(75.0, weekday = 3),
                        DayArchetypes.plBenchHeavy(80.0, weekday = 5),
                        DayArchetypes.plBenchVolume(70.0, weekday = 6),
                    ),
                ),
            ),
            liftSlots = mapOf(
                LiftSlot.SQUAT to CatalogIds.SQ_LOW,
                LiftSlot.BENCH to CatalogIds.BP,
                LiftSlot.DEADLIFT to CatalogIds.DL,
            ),
            claimedDaysPerWeek = 4,
        )
        val hard = ProgramRecipeValidator.hardFindings(recipe, metadata)
        assertTrue(hard.joinToString("\n"), hard.isEmpty())
    }

    @Test
    fun exampleB_pl_bench_passes() {
        val findings = SessionCompositionPolicy.evaluateDay(
            DayArchetypes.plBenchHeavy(80.0),
            weekRecipe(1, 0, "Fuerza", BlockGoal.INTENSIFICATION, listOf(DayArchetypes.plBenchHeavy(80.0))),
            metadata,
        ).filter { it.severity == com.example.kpkn.data.protocols.CompositionSeverity.HARD }
        assertTrue(findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun exampleC_hypertrophy_legs_passes() {
        val findings = SessionCompositionPolicy.evaluateDay(
            DayArchetypes.bbLegs(),
            weekRecipe(3, 0, "Volumen", BlockGoal.ACCUMULATION, listOf(DayArchetypes.bbLegs())),
            metadata,
        ).filter { it.severity == com.example.kpkn.data.protocols.CompositionSeverity.HARD }
        assertTrue(findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun hipThrust_catalog_aislado_stays_compound_for_h1() {
        assertTrue(
            !CompositionTaxonomy.isIsolation(
                PatternFamily.HIP_EXTENSION,
                "AISLADO",
                "hip_thrust__bilateral__barbell",
            ),
        )
        assertTrue(
            CompositionTaxonomy.isIsolation(
                PatternFamily.HIP_EXTENSION,
                "AISLADO",
                "glutes_patada_gluteo__cable",
            ),
        )
        val day = day(
            "Glúteo",
            listOf(
                slot("ht", SlotRole.T1_MAIN, CatalogIds.HIP, rpeSets(4, 10, 8.0), 180),
                slot("rdl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.RDL, rpeSets(3, 8, 8.0), 120, LiftSlot.DEADLIFT),
                slot("curl", SlotRole.T3_ACCESSORY, CatalogIds.CURL_H, rpeSets(3, 12, 8.5), 75),
                slot("abd", SlotRole.T3_ACCESSORY, "hip_abduction__standing__cable__unilateral", rpeSets(3, 15, 8.5), 60),
            ),
        )
        val hard = SessionCompositionPolicy.evaluateDay(
            day,
            weekRecipe(1, 0, "Glúteo", BlockGoal.ACCUMULATION, listOf(day)),
            metadata,
        ).filter { it.severity == com.example.kpkn.data.protocols.CompositionSeverity.HARD }
        assertTrue(hard.joinToString("\n"), hard.isEmpty())
    }

    @Test
    fun westside_speed_first_passes_h1() {
        val day = day(
            "DE Lower",
            listOf(
                slot(
                    "speed", SlotRole.SPEED, CatalogIds.SQ_BOX,
                    List(8) { SetRecipe(reps = 2, percent = 60.0, loadBasis = LoadBasis.PERCENT_TM) },
                    60,
                    LiftSlot.SQUAT,
                    priority = SlotPriority.SPEED,
                    technique = com.example.kpkn.data.protocols.TechniqueModifier.SPEED,
                ),
                slot("dl", SlotRole.T1_MAIN, CatalogIds.DL, repeatPercentSets(4, 2, 70.0, 180), 180, LiftSlot.DEADLIFT, isCompetitionLift = true),
                slot("ghr", SlotRole.T3_ACCESSORY, CatalogIds.GHR, rpeSets(3, 8, 8.0), 90),
                slot("rev", SlotRole.T3_ACCESSORY, CatalogIds.REV_HYPER, rpeSets(3, 10, 7.0), 90),
                slot("wheel", SlotRole.T3_ACCESSORY, CatalogIds.WHEEL, rpeSets(3, 8, 7.0), 60),
            ),
            priority = SlotPriority.SPEED,
            weekday = 4,
        )
        val hard = SessionCompositionPolicy.evaluateDay(
            day,
            weekRecipe(1, 0, "ME/DE", BlockGoal.INTENSIFICATION, listOf(day)),
            metadata,
        ).filter { it.rule == "H1" && it.severity == com.example.kpkn.data.protocols.CompositionSeverity.HARD }
        assertTrue(hard.joinToString("\n"), hard.isEmpty())
    }

    @Test
    fun h8_rejects_high_rep_heavy_t1_in_peak() {
        val day = day(
            "Peak",
            listOf(
                slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, listOf(SetRecipe(reps = 10, percent = 90.0, isTopSet = true)), 240, LiftSlot.SQUAT, isCompetitionLift = true),
                slot("row", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, rpeSets(3, 8, 8.0), 120),
                slot("ghr", SlotRole.T3_ACCESSORY, CatalogIds.GHR, rpeSets(3, 8, 8.0), 90),
            ),
        )
        val hard = SessionCompositionPolicy.evaluateDay(
            day,
            weekRecipe(1, 0, "Peak", BlockGoal.PEAK, listOf(day)),
            metadata,
        ).filter { it.rule == "H8" && it.severity == com.example.kpkn.data.protocols.CompositionSeverity.HARD }
        assertTrue(hard.joinToString("\n"), hard.isNotEmpty())
    }

    @Test
    fun exemption_silences_only_declared_rule() {
        val day = day(
            "Solo squat",
            listOf(
                slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, repeatPercentSets(10, 3, 85.0, 180), 180, LiftSlot.SQUAT, isCompetitionLift = true),
            ),
        )
        val recipe = TrainingPlanRecipe(
            id = "smolov-like",
            weeks = listOf(weekRecipe(1, 0, "Base", BlockGoal.INTENSIFICATION, listOf(day))),
            exemptions = listOf(RecipeCompositionExemption("H6", "*", "Especialización")),
        )
        val hard = ProgramRecipeValidator.hardFindings(recipe, metadata)
        assertTrue(hard.none { it.rule == "H6" })
        assertTrue(hard.any { it.rule != "H6" })
    }

    @Test
    fun h11_rejects_triple_plus_over_92_percent_1rm() {
        val day = day(
            "Peligroso",
            listOf(
                slot(
                    "sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW,
                    listOf(SetRecipe(reps = 5, percent = 102.5, loadBasis = LoadBasis.PERCENT_1RM)),
                    240, LiftSlot.SQUAT, isCompetitionLift = true,
                ),
                slot("row", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, rpeSets(3, 8, 8.0), 120),
                slot("ghr", SlotRole.T3_ACCESSORY, CatalogIds.GHR, rpeSets(3, 8, 8.0), 90),
            ),
        )
        val recipe = TrainingPlanRecipe(
            id = "over-1rm",
            weeks = listOf(weekRecipe(1, 0, "Base", BlockGoal.INTENSIFICATION, listOf(day))),
            trainingMaxPercent = 1.0,
            exemptions = listOf(RecipeCompositionExemption("H6", "*", "no silencia H11")),
        )
        val hard = ProgramRecipeValidator.hardFindings(recipe, metadata)
        assertTrue("H11 debe fallar: $hard", hard.any { it.rule == "H11" })
        assertTrue("H11 no es exentable: $hard", hard.any { it.rule == "H11" })
    }

    @Test
    fun s_duplicate_slot_reports_same_config_same_technique_as_soft() {
        val day = day(
            "Duplicado",
            listOf(
                slot("a", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, repeatPercentSets(3, 5, 70.0, 180), 180, LiftSlot.SQUAT, isCompetitionLift = true),
                slot("b", SlotRole.T2_SUPPLEMENTAL, CatalogIds.SQ_HIGH, rpeSets(3, 8, 8.0), 150, LiftSlot.SQUAT),
                slot("c", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, rpeSets(3, 8, 8.0), 120),
                slot("d", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, rpeSets(3, 8, 8.0), 120),
            ),
        )
        val findings = SessionCompositionPolicy.evaluateDay(
            day,
            weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, listOf(day)),
            metadata,
        )
        val dupes = findings.filter { it.rule == "S_duplicate_slot" }
        assertTrue("Debe reportar S_duplicate_slot: $findings", dupes.isNotEmpty())
        assertTrue(
            "S_duplicate_slot debe ser SOFT",
            dupes.all { it.severity == com.example.kpkn.data.protocols.CompositionSeverity.SOFT },
        )
        assertTrue(
            "SOFT no debe bloquear: ${findings.filter { it.severity == com.example.kpkn.data.protocols.CompositionSeverity.HARD }}",
            findings.none { it.severity == com.example.kpkn.data.protocols.CompositionSeverity.HARD && it.rule == "S_duplicate_slot" },
        )
    }

    @Test
    fun s_duplicate_slot_exempts_same_config_with_different_technique() {
        val day = day(
            "Velocidad",
            listOf(
                slot("t1", SlotRole.T1_MAIN, CatalogIds.BP, repeatPercentSets(3, 5, 70.0, 180), 180, LiftSlot.BENCH, isCompetitionLift = true),
                slot(
                    "speed", SlotRole.SPEED, CatalogIds.BP, repeatPercentSets(6, 3, 68.0, 60), 60, LiftSlot.BENCH,
                    technique = com.example.kpkn.data.protocols.TechniqueModifier.SPEED,
                    supplementalOf = "t1",
                ),
                slot("row", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, rpeSets(3, 8, 8.0), 120),
                slot("curl", SlotRole.T3_ACCESSORY, CatalogIds.CURL, rpeSets(3, 10, 8.0), 90),
            ),
        )
        val findings = SessionCompositionPolicy.evaluateDay(
            day,
            weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, listOf(day)),
            metadata,
        )
        assertTrue(
            "PHAT speed-vs-main debe quedar exento de S_duplicate_slot: $findings",
            findings.none { it.rule == "S_duplicate_slot" },
        )
    }
}
