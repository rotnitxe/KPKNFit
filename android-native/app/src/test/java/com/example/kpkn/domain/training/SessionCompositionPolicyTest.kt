package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.CompositionSeverity
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.RecipeCompositionExemption
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.rangeRirSets
import com.example.kpkn.data.protocols.rpeSets
import com.example.kpkn.data.protocols.repeatPercentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ─── §14.3: despacho por perfil de composición (paquete F, T-004a) ──────

    private fun nativeCompactDay(
        id: String = "nativo",
        minimumDose: com.example.kpkn.data.protocols.DayMinimumDose? = com.example.kpkn.data.protocols.DayMinimumDose(2, 4, listOf("sq")),
        sessionKind: com.example.kpkn.data.protocols.RecipeSessionKind = com.example.kpkn.data.protocols.RecipeSessionKind.STRENGTH,
        cardioSeconds: Int? = null,
        slots: List<com.example.kpkn.data.protocols.SlotRecipe> = listOf(
            slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_HIGH, rangeRirSets(2, 4, 6, 3), 180, LiftSlot.SQUAT),
            slot("push", SlotRole.T3_ACCESSORY, CatalogIds.BP_DB, rangeRirSets(2, 8, 12, 3), 120),
        ),
    ): com.example.kpkn.data.protocols.DayRecipe = com.example.kpkn.data.protocols.DayRecipe(
        label = id,
        slots = slots,
        weekday = 1,
        minimumDose = minimumDose,
        sessionKind = sessionKind,
        cardioBlocks = cardioSeconds?.let { seconds ->
            listOf(
                com.example.kpkn.data.protocols.RecipeCardioBlock(
                    id = "cardio-1",
                    details = com.example.kpkn.data.models.CardioDetails(
                        type = com.example.kpkn.data.models.CardioType.WALK,
                        targetDurationSeconds = seconds,
                    ),
                ),
            )
        }.orEmpty(),
    )

    private fun hardOf(day: com.example.kpkn.data.protocols.DayRecipe, profile: com.example.kpkn.data.protocols.RecipeCompositionProfile, goal: BlockGoal = BlockGoal.ACCUMULATION) =
        SessionCompositionPolicy.evaluateDay(
            day,
            weekRecipe(1, 0, "nativo", goal, listOf(day)),
            metadata,
            0.90,
            profile,
        ).filter { it.severity == com.example.kpkn.data.protocols.CompositionSeverity.HARD }

    @Test
    fun native_compact_uses_declared_floors_while_legacy_keeps_h6() {
        val day = nativeCompactDay()
        val nativeHard = hardOf(day, com.example.kpkn.data.protocols.RecipeCompositionProfile.NATIVE_COMPACT)
        assertTrue("NATIVE_COMPACT con 2 configuraciones/4 series debe pasar: $nativeHard", nativeHard.isEmpty())
        val legacyHard = hardOf(day, com.example.kpkn.data.protocols.RecipeCompositionProfile.LEGACY_STANDARD)
        assertTrue(
            "LEGACY_STANDARD sigue exigiendo H6 (3 ejercicios/10 series)",
            legacyHard.any { it.rule == "H6" },
        )
        // Un día de 1 ejercicio/2 series NO pasa el suelo declarado de2/4.
        val tooSmall = nativeCompactDay(
            slots = listOf(
                slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_HIGH, rangeRirSets(2, 4, 6, 3), 180, LiftSlot.SQUAT),
            ),
            minimumDose = com.example.kpkn.data.protocols.DayMinimumDose(2, 4, emptyList()),
        )
        assertTrue(
            hardOf(tooSmall, com.example.kpkn.data.protocols.RecipeCompositionProfile.NATIVE_COMPACT)
                .any { it.rule == "H6" },
        )
    }

    @Test
    fun native_deload_keeps_essentials_and_one_set_per_slot() {
        val deloadDay = nativeCompactDay(
            slots = listOf(
                slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_HIGH, rangeRirSets(1, 4, 6, 4), 180, LiftSlot.SQUAT),
                slot("push", SlotRole.T3_ACCESSORY, CatalogIds.BP_DB, rangeRirSets(1, 8, 12, 4), 120),
            ),
            minimumDose = com.example.kpkn.data.protocols.DayMinimumDose(2, 4, listOf("sq")),
        )
        assertTrue(
            hardOf(
                deloadDay,
                com.example.kpkn.data.protocols.RecipeCompositionProfile.NATIVE_COMPACT,
                BlockGoal.DELOAD,
            ).isEmpty(),
        )
        val missingEssential = deloadDay.copy(minimumDose = com.example.kpkn.data.protocols.DayMinimumDose(2, 4, listOf("no-existe")))
        assertTrue(
            hardOf(
                missingEssential,
                com.example.kpkn.data.protocols.RecipeCompositionProfile.NATIVE_COMPACT,
                BlockGoal.DELOAD,
            ).any { it.rule == "H6" },
        )
    }

    @Test
    fun mixed_cardio_dispatches_on_session_kind() {
        val profile = com.example.kpkn.data.protocols.RecipeCompositionProfile.MIXED_CARDIO
        // CARDIO: cero resistencia y cardio real ≥10 min.
        val cardioOnly = nativeCompactDay(
            slots = emptyList(),
            minimumDose = com.example.kpkn.data.protocols.DayMinimumDose(0, 0, emptyList()),
            sessionKind = com.example.kpkn.data.protocols.RecipeSessionKind.CARDIO,
            cardioSeconds = 600,
        )
        assertTrue("día solo cardio válido: ${hardOf(cardioOnly, profile)}", hardOf(cardioOnly, profile).isEmpty())
        val shortCardio = cardioOnly.copy(
            cardioBlocks = cardioOnly.cardioBlocks.map { it.copy(details = it.details.copy(targetDurationSeconds = 480)) },
        )
        assertTrue(
            "cardio de 8 min no vale",
            hardOf(shortCardio, profile).any { it.rule == "H6" && it.message.contains("10 min") },
        )
        val cardioWithResistance = cardioOnly.copy(
            slots = listOf(slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_HIGH, rangeRirSets(2, 4, 6, 3), 180, LiftSlot.SQUAT)),
        )
        assertTrue(
            "un día solo cardio no admite resistencia",
            hardOf(cardioWithResistance, profile).any { it.rule == "H6" },
        )
        val cardioWithSpeed = cardioOnly.copy(
            slots = listOf(slot("speed", SlotRole.SPEED, CatalogIds.SQ_HIGH, rangeRirSets(2, 3, 3, 5), 90)),
        )
        assertTrue(
            "SPEED también es un slot de resistencia y no puede colarse en CARDIO",
            hardOf(cardioWithSpeed, profile).any { it.rule == "H6" },
        )
        // STRENGTH_CARDIO: suelo NATIVE + cardio válido.
        val strengthCardio = nativeCompactDay(
            sessionKind = com.example.kpkn.data.protocols.RecipeSessionKind.STRENGTH_CARDIO,
            cardioSeconds = 900,
        )
        assertTrue(hardOf(strengthCardio, profile).isEmpty())
        val strengthCardioNoCardio = strengthCardio.copy(cardioBlocks = emptyList())
        assertTrue(
            hardOf(strengthCardioNoCardio, profile).any { it.rule == "H6" },
        )
        // CARDIO_ACCESSORY: cardio + accesorios esenciales; 1 ejercicio/1 serie vale.
        val accessory = nativeCompactDay(
            slots = listOf(
                slot("lunge", SlotRole.T3_ACCESSORY, CatalogIds.LUNGE_REVERSE_BODYWEIGHT, rangeRirSets(1, 8, 12, 3), 120),
            ),
            minimumDose = com.example.kpkn.data.protocols.DayMinimumDose(0, 0, listOf("lunge")),
            sessionKind = com.example.kpkn.data.protocols.RecipeSessionKind.CARDIO_ACCESSORY,
            cardioSeconds = 600,
        )
        assertTrue(
            "cardio+accesorios con1 ejercicio/1 serie debe pasar: ${hardOf(accessory, profile)}",
            hardOf(accessory, profile).isEmpty(),
        )
        assertTrue(
            "cardio+accesorios exige cardio real",
            hardOf(accessory.copy(cardioBlocks = emptyList()), profile).any { it.rule == "H6" },
        )
        assertTrue(
            "cardio+accesorios exige los esenciales",
            hardOf(accessory.copy(minimumDose = com.example.kpkn.data.protocols.DayMinimumDose(0, 0, listOf("otro"))), profile)
                .any { it.rule == "H6" },
        )
    }

    @Test
    fun mrv_upper_bound_survives_an_empty_lift_slots_map() {
        // Receta con `liftSlots` vacío (§14.3): el mapa no desactiva el techo.
        val chestDays = (1..6).map { dayIndex ->
            com.example.kpkn.data.protocols.DayRecipe(
                label = "pecho$dayIndex",
                weekday = dayIndex,
                slots = listOf(
                    slot("bp", SlotRole.T3_ACCESSORY, CatalogIds.BP, rangeRirSets(4, 8, 12, 2), 120, LiftSlot.BENCH),
                    slot("inc", SlotRole.T3_ACCESSORY, CatalogIds.BP_INC_DB, rangeRirSets(4, 8, 12, 2), 120),
                    slot("fly", SlotRole.T3_ACCESSORY, CatalogIds.FLY, rangeRirSets(4, 10, 15, 2), 90),
                ),
            )
        }
        fun recipe(profile: com.example.kpkn.data.protocols.RecipeCompositionProfile) = TrainingPlanRecipe(
            id = "native:muscle-foundation-v2",
            weeks = listOf(
                com.example.kpkn.data.protocols.WeekRecipe(
                    weekNumber = 1,
                    blockIndex = 0,
                    blockName = "Acumulación",
                    blockGoal = BlockGoal.ACCUMULATION,
                    days = chestDays,
                ),
            ),
            claimedDaysPerWeek = 6,
            compositionProfile = profile,
        )
        val nativeHard = ProgramRecipeValidator.hardFindings(recipe(com.example.kpkn.data.protocols.RecipeCompositionProfile.NATIVE_COMPACT), metadata)
        assertTrue(
            "MRV debe seguir activo con liftSlots vacío: $nativeHard",
            nativeHard.any { it.rule == "W2" && it.message.contains("MRV") },
        )
        val legacyHard = ProgramRecipeValidator.hardFindings(recipe(com.example.kpkn.data.protocols.RecipeCompositionProfile.LEGACY_STANDARD), metadata)
        assertTrue(
            "LEGACY conserva su comportamiento actual (sin W2-MRV con mapa vacío): $legacyHard",
            legacyHard.none { it.rule == "W2" && it.message.contains("MRV") },
        )
    }

    @Test
    fun native_mrv_is_hard_even_when_mev_is_zero_but_legacy_contract_stays_soft() {
        // 19 series superan el techo blando de 17,5 (B-02): entre 16 y 17,5 el exceso es SOFT y se prueba
        // aparte en `native_glutes_soft_band_is_soft_up_to_the_ceiling_and_hard_above_it`.
        val highGluteDay = day(
            "glúteo",
            listOf(
                slot(
                    "bridge",
                    SlotRole.T3_ACCESSORY,
                    CatalogIds.GLUTE_BRIDGE_BODYWEIGHT,
                    rangeRirSets(19, 8, 12, 2),
                    120,
                ),
                slot("core", SlotRole.T3_ACCESSORY, CatalogIds.CRUNCH, rangeRirSets(1, 8, 12, 2), 60),
            ),
        )
        fun profileRecipe(profile: RecipeCompositionProfile) = TrainingPlanRecipe(
            id = "native:muscle-foundation-v2",
            weeks = listOf(weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, listOf(highGluteDay))),
            compositionProfile = profile,
        )

        val nativeFindings = ProgramRecipeValidator.hardFindings(
            profileRecipe(RecipeCompositionProfile.NATIVE_COMPACT),
            metadata,
        )
        assertTrue(
            "Glúteos tiene MEV 0 pero MRV 16; pasar el techo blando de 17,5 sigue siendo HARD: $nativeFindings",
            nativeFindings.any { it.rule == "W2" && it.message.contains("GLUTES") && it.message.contains("MRV") },
        )

        val legacyFindings = ProgramRecipeValidator.hardFindings(
            profileRecipe(RecipeCompositionProfile.LEGACY_STANDARD),
            metadata,
        )
        assertTrue(
            "no se cambia el umbral legacy de especialización",
            legacyFindings.none { it.rule == "W2" && it.message.contains("MRV") },
        )
    }

    // ─── B-02: banda blanda de glúteos (límite 16, techo blando 17,5) ────────────────────────

    /** Receta propia de una semana: [bridgeSets] series de puente (1,0 de glúteo por serie) más los [extra]. */
    private fun nativeGluteRecipe(
        bridgeSets: Int,
        extra: List<com.example.kpkn.data.protocols.SlotRecipe> = emptyList(),
        profile: RecipeCompositionProfile = RecipeCompositionProfile.NATIVE_COMPACT,
        recipeId: String = "native:muscle-foundation-v2",
        liftSlots: Map<LiftSlot, String> = emptyMap(),
    ): TrainingPlanRecipe {
        val gluteDay = day(
            "glúteo",
            listOf(
                slot(
                    "bridge",
                    SlotRole.T3_ACCESSORY,
                    CatalogIds.GLUTE_BRIDGE_BODYWEIGHT,
                    rangeRirSets(bridgeSets, 8, 12, 2),
                    120,
                ),
            ) + extra,
        )
        return TrainingPlanRecipe(
            id = recipeId,
            weeks = listOf(weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, listOf(gluteDay))),
            liftSlots = liftSlots,
            compositionProfile = profile,
        )
    }

    /** Hallazgos de volumen semanal de glúteos por encima de su MRV (SOFT y HARD). */
    private fun gluteMrvFindings(recipe: TrainingPlanRecipe) =
        ProgramRecipeValidator.validate(recipe, metadata)
            .filter { it.rule == "W2" && it.message.contains("GLUTES") && it.message.contains("> MRV") }

    @Test
    fun native_glutes_soft_band_is_soft_up_to_the_ceiling_and_hard_above_it() {
        val superman = slot(
            "superman",
            SlotRole.T3_ACCESSORY,
            "back_superman_suelo__default",
            rangeRirSets(1, 8, 12, 2),
            60,
        )

        // Hasta el límite (16): volumen normal, sin hallazgo.
        assertTrue(gluteMrvFindings(nativeGluteRecipe(16)).isEmpty())

        // Entre el límite y el techo (17 y 17,5 series): «volumen alto», SOFT. No bloquea la receta.
        val seventeen = nativeGluteRecipe(17)
        // 17 de puente (1,0 por serie) + 1 de superman (0,5 de glúteo secundario) = 17,5 exactos.
        val seventeenAndAHalf = nativeGluteRecipe(17, listOf(superman))
        assertEquals(
            "el contador único mide 17,5 series de glúteo",
            17.5,
            SessionCompositionPolicy.weeklyGroupSets(seventeenAndAHalf.weeks.first(), metadata)
                .getValue(com.example.kpkn.data.programs.KpknMuscleGroup.GLUTES),
            0.0,
        )
        listOf(seventeen, seventeenAndAHalf).forEach { recipe ->
            val findings = gluteMrvFindings(recipe)
            assertEquals("un solo hallazgo de glúteos: $findings", 1, findings.size)
            val finding = findings.single()
            assertEquals(com.example.kpkn.data.protocols.CompositionSeverity.SOFT, finding.severity)
            assertTrue("conserva el texto «> MRV 16»: ${finding.message}", finding.message.contains("> MRV 16"))
            assertTrue("avisa del volumen alto: ${finding.message}", finding.message.contains("volumen alto"))
            assertTrue(
                "la banda no bloquea la receta (sin HARD de glúteos): ${ProgramRecipeValidator.hardFindings(recipe, metadata)}",
                ProgramRecipeValidator.hardFindings(recipe, metadata).none { it.message.contains("GLUTES") },
            )
        }

        // Por encima del techo blando (18 y 19 series): HARD, como antes, sin la marca de volumen alto.
        listOf(18, 19).forEach { sets ->
            val findings = gluteMrvFindings(nativeGluteRecipe(sets))
            assertEquals("un solo hallazgo de glúteos con $sets series: $findings", 1, findings.size)
            val finding = findings.single()
            assertEquals(com.example.kpkn.data.protocols.CompositionSeverity.HARD, finding.severity)
            assertTrue("conserva el texto «> MRV 16»: ${finding.message}", finding.message.contains("> MRV 16"))
            assertFalse("no lleva la marca de volumen alto: ${finding.message}", finding.message.contains("volumen alto"))
            assertTrue(
                ProgramRecipeValidator.hardFindings(nativeGluteRecipe(sets), metadata).any { it.message == finding.message },
            )
        }
    }

    @Test
    fun native_soft_band_does_not_extend_to_other_muscles_nor_to_legacy_recipes() {
        fun chestRecipe(sets: Int) = TrainingPlanRecipe(
            id = "native:muscle-foundation-v2",
            weeks = listOf(
                weekRecipe(
                    1, 0, "Base", BlockGoal.ACCUMULATION,
                    listOf(
                        day(
                            "pecho",
                            listOf(
                                slot("bp", SlotRole.T3_ACCESSORY, CatalogIds.BP, rangeRirSets(sets, 8, 12, 2), 120, LiftSlot.BENCH),
                            ),
                        ),
                    ),
                ),
            ),
            compositionProfile = RecipeCompositionProfile.NATIVE_COMPACT,
        )
        fun chestFindings(sets: Int) = ProgramRecipeValidator.validate(chestRecipe(sets), metadata)
            .filter { it.rule == "W2" && it.message.contains("CHEST") && it.message.contains("> MRV") }

        // Pecho: MRV 22. Con 22 series no hay hallazgo y con 23 (una sola de más) ya es HARD: sin tolerancia.
        assertTrue(chestFindings(22).isEmpty())
        val chestOver = chestFindings(23)
        assertEquals(1, chestOver.size)
        assertEquals(com.example.kpkn.data.protocols.CompositionSeverity.HARD, chestOver.single().severity)
        assertFalse(chestOver.single().message.contains("volumen alto"))

        // Receta no propia (perfil legacy, dos levantamientos para que el MRV aplique): el exceso de glúteos
        // sigue siendo SOFT por MEV 0 y NUNCA lleva la marca de «volumen alto».
        val legacyLifts = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP)
        listOf(17, 19).forEach { sets ->
            val findings = gluteMrvFindings(
                nativeGluteRecipe(
                    sets,
                    profile = RecipeCompositionProfile.LEGACY_STANDARD,
                    recipeId = "legacy-glute-volume",
                    liftSlots = legacyLifts,
                ),
            )
            assertEquals("un hallazgo legacy de glúteos con $sets series: $findings", 1, findings.size)
            assertEquals(com.example.kpkn.data.protocols.CompositionSeverity.SOFT, findings.single().severity)
            assertFalse(findings.single().message.contains("volumen alto"))
        }
    }

    // ─── B.S1: H11 sobre kg resueltos, H11b (Epley) y ámbitos de exención en glob anclado ───

    /** Día de fuerza: un T1 de sentadilla y dos accesorios por RPE (así solo se mide la intensidad del T1). */
    private fun intensityDay(label: String, t1Sets: List<SetRecipe>): DayRecipe = day(
        label,
        listOf(
            slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, t1Sets, 240, LiftSlot.SQUAT, isCompetitionLift = true),
            slot("row", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, rpeSets(3, 8, 8.0), 120),
            slot("ghr", SlotRole.T3_ACCESSORY, CatalogIds.GHR, rpeSets(3, 8, 8.0), 90),
        ),
    )

    private fun intensityRecipe(
        days: List<DayRecipe>,
        trainingMaxPercent: Double = 0.90,
        exemptions: List<RecipeCompositionExemption> = emptyList(),
    ) = TrainingPlanRecipe(
        id = "intensity",
        weeks = listOf(weekRecipe(1, 0, "Base", BlockGoal.INTENSIFICATION, days)),
        trainingMaxPercent = trainingMaxPercent,
        exemptions = exemptions,
    )

    private fun intensityFindings(
        rule: String,
        t1Sets: List<SetRecipe>,
        trainingMaxPercent: Double = 0.90,
        exemptions: List<RecipeCompositionExemption> = emptyList(),
    ): List<CompositionFinding> = ProgramRecipeValidator.hardFindings(
        intensityRecipe(listOf(intensityDay("Intensidad", t1Sets)), trainingMaxPercent, exemptions),
        metadata,
    ).filter { it.rule == rule }

    private fun pct1rm(count: Int, reps: Int, percent: Double) =
        repeatPercentSets(count, reps, percent, 180, basis = LoadBasis.PERCENT_1RM)

    private fun topSetOf(percent: Double, reps: Int) =
        SetRecipe(reps = reps, percent = percent, isTopSet = true, loadBasis = LoadBasis.PERCENT_OF_TOP_SET)

    @Test
    fun h11b_rejects_five_reps_at_90_percent_1rm_and_accepts_four() {
        val five = intensityFindings("H11b", pct1rm(3, 5, 90.0))
        assertEquals("un hallazgo por serie: $five", 3, five.size)
        assertTrue(five.all { it.severity == CompositionSeverity.HARD && it.scope == "w1/Intensidad" })
        assertTrue(five.first().message, five.first().message.contains("5 reps") && five.first().message.contains("como máximo 4"))
        assertTrue(intensityFindings("H11b", pct1rm(3, 4, 90.0)).isEmpty())
        // 5 reps al 90 % no rompe H11 (no pasa del 92 %): solo lo caza Epley.
        assertTrue(intensityFindings("H11", pct1rm(3, 5, 90.0)).isEmpty())
    }

    @Test
    fun h11b_skips_amrap_rep_max_warmups_and_sets_without_a_1rm_base() {
        val sets = listOf(
            SetRecipe(reps = 5, percent = 90.0, amrap = true, loadBasis = LoadBasis.PERCENT_1RM),
            SetRecipe(reps = 5, percent = 90.0, loadBasis = LoadBasis.REP_MAX),
            SetRecipe(reps = 5, percent = 95.0, isWarmup = true, loadBasis = LoadBasis.PERCENT_1RM),
            SetRecipe(reps = 8, rir = 2, loadBasis = LoadBasis.RPE),
        )
        assertTrue(intensityFindings("H11b", sets).isEmpty())
    }

    @Test
    fun h11b_uses_the_lower_bound_of_a_rep_range() {
        // 8–12 reps al 75 % del 1RM: caben 11. Pasa porque manda la cota inferior (8), aunque la superior (12) no.
        val inside = listOf(SetRecipe(repsMin = 8, repsMax = 12, percent = 75.0, loadBasis = LoadBasis.PERCENT_1RM))
        assertTrue(intensityFindings("H11b", inside).isEmpty())
        val beyond = listOf(SetRecipe(repsMin = 12, repsMax = 15, percent = 75.0, loadBasis = LoadBasis.PERCENT_1RM))
        val findings = intensityFindings("H11b", beyond)
        assertEquals(1, findings.size)
        assertTrue(findings.single().message, findings.single().message.contains("12 reps"))
    }

    @Test
    fun h11b_converts_tm_to_1rm_with_the_recipe_training_max() {
        val five = listOf(SetRecipe(reps = 5, percent = 100.0, isTopSet = true, loadBasis = LoadBasis.PERCENT_TM))
        // TM al 90 %: 100 % TM = 90 % del 1RM, caben 4.
        assertEquals(1, intensityFindings("H11b", five, trainingMaxPercent = 0.90).size)
        // TM al 87 % (≈ 5RM): 100 % TM = 87 % del 1RM, caben 5.
        assertTrue(intensityFindings("H11b", five, trainingMaxPercent = 0.87).isEmpty())
        assertTrue(intensityFindings("H11", five, trainingMaxPercent = 0.90).isEmpty())
    }

    @Test
    fun h11b_is_never_exemptable() {
        val sets = pct1rm(1, 5, 90.0)
        listOf("*", "w*", "w*/Intensidad", "w1/Intensidad").forEach { scope ->
            val findings = intensityFindings(
                "H11b",
                sets,
                exemptions = listOf(RecipeCompositionExemption("H11b", scope, "no debe silenciarse")),
            )
            assertEquals("el ámbito '$scope' no puede silenciar H11b", 1, findings.size)
        }
    }

    @Test
    fun h11_measures_percent_of_top_set_in_resolved_kg() {
        fun volume(percent: Double) = repeatPercentSets(5, 5, percent, 240, basis = LoadBasis.PERCENT_OF_TOP_SET)
        // Top set al 102,5 con TM 1,0: es el 102,5 % del 1RM aunque el valor crudo (≤ 105) pase el techo del top set.
        val over = intensityFindings("H11", listOf(topSetOf(102.5, 3)), trainingMaxPercent = 1.0)
        assertEquals(1, over.size)
        assertTrue(over.single().message, over.single().message.contains("del top set") && over.single().message.contains("1RM"))
        // Cinco repeticiones por encima del 92 % del 1RM.
        assertEquals(1, intensityFindings("H11", listOf(topSetOf(95.0, 5)), trainingMaxPercent = 1.0).size)
        // El mismo top set con TM al 87 %: 95 % TM = 82,65 % del 1RM, sin hallazgo.
        assertTrue(intensityFindings("H11", listOf(topSetOf(95.0, 5)), trainingMaxPercent = 0.87).isEmpty())
        // Volumen colgado del top set de otro día: 5 × 100 sobre un top de 102,5 resuelve a 102,5 % TM.
        val monday = intensityDay("Volumen", volume(100.0))
        val friday = intensityDay("Intensidad PR", listOf(topSetOf(102.5, 3)))
        val recipe = intensityRecipe(listOf(monday, friday), trainingMaxPercent = 1.0)
        val findings = ProgramRecipeValidator.hardFindings(recipe, metadata)
            .filter { it.rule == "H11" && it.scope == "w1/Volumen" }
        assertEquals("el volumen resuelto a 102,5 % del 1RM debe fallar: $findings", 5, findings.size)
        // Texas con TM al 87 %: lunes 5 × 90 y viernes top 5 × 100 (87 % del 1RM) no incumplen ni H11 ni H11b.
        val texas = intensityRecipe(
            listOf(intensityDay("Volumen", volume(90.0)), intensityDay("Intensidad PR", listOf(topSetOf(100.0, 5)))),
            trainingMaxPercent = 0.87,
        )
        val ok = ProgramRecipeValidator.hardFindings(texas, metadata).filter { it.rule == "H11" || it.rule == "H11b" }
        assertTrue(ok.joinToString("\n"), ok.isEmpty())
        // ...pero con TM al 90 % el mismo top de 5 repeticiones es el 90 % del 1RM y Epley lo rechaza.
        val strict = ProgramRecipeValidator.hardFindings(texas.copy(trainingMaxPercent = 0.90), metadata)
            .filter { it.rule == "H11b" }
        assertEquals(1, strict.size)
    }

    @Test
    fun exemption_scope_is_an_anchored_glob() {
        fun finding(rule: String, scope: String) = CompositionFinding(CompositionSeverity.HARD, rule, scope, "m")
        fun silenced(rule: String, scope: String, pattern: String): Boolean = SessionCompositionPolicy
            .applyExemptions(listOf(finding(rule, scope)), listOf(RecipeCompositionExemption(rule, pattern, "justificación")))
            .isEmpty()
        // "*" lo silencia todo.
        assertTrue(silenced("H6", "w3/S1", "*"))
        assertTrue(silenced("W3", "w3", "*"))
        assertTrue(silenced("W5", "block0/Base", "*"))
        // "w*" casa semana y día, no bloque.
        assertTrue(silenced("W3", "w3", "w*"))
        assertTrue(silenced("H6", "w3/S1", "w*"))
        assertFalse(silenced("W5", "block0/w", "w*"))
        // "w*/S1": ese día en cualquier semana; ni S10, ni otra etiqueta que acabe parecido, ni la semana.
        assertTrue(silenced("H6", "w1/S1", "w*/S1"))
        assertTrue(silenced("H6", "w12/S1", "w*/S1"))
        assertFalse(silenced("H6", "w1/S10", "w*/S1"))
        assertFalse(silenced("H6", "w1/XS1", "w*/S1"))
        assertFalse(silenced("H6", "w1", "w*/S1"))
        // Etiquetas con barra y con cola.
        assertTrue(silenced("H2", "w5/Sentadilla/Banca", "w*/Sentadilla/Banca"))
        assertFalse(silenced("H2", "w5/Sentadilla/Banca 2", "w*/Sentadilla/Banca"))
        assertTrue(silenced("H2", "w5/Sentadilla/Banca 2", "w*/Sentadilla/Banca*"))
        // Bloques.
        assertTrue(silenced("W5", "block0/Conjugate", "block*/Conjugate"))
        assertFalse(silenced("W5", "block0/Switching", "block*/Conjugate"))
        // Sin '*' el ámbito solo casa con el texto exacto: se acabaron contains y startsWith.
        assertFalse(silenced("W5", "block0/Conjugate", "Conjugate"))
        assertFalse(silenced("H6", "w1/S1", "S1"))
        assertTrue(silenced("H6", "w1/S1", "w1/S1"))
        assertFalse(silenced("H6", "w1/S10", "w1/S1"))
        // Los caracteres de expresión regular son literales.
        assertFalse(silenced("H6", "w1/axb", "w*/a.b"))
        assertTrue(silenced("H6", "w1/a.b", "w*/a.b"))
        // La regla también tiene que coincidir.
        val otherRule = SessionCompositionPolicy.applyExemptions(
            listOf(finding("H2", "w1/S1")),
            listOf(RecipeCompositionExemption("H6", "*", "j")),
        )
        assertEquals(1, otherRule.size)
    }

    @Test
    fun h11_and_h11b_survive_any_exemption() {
        fun finding(rule: String) = CompositionFinding(CompositionSeverity.HARD, rule, "w1/Intensidad", "m")
        val kept = SessionCompositionPolicy.applyExemptions(
            listOf(finding("H11"), finding("H11b"), finding("H6")),
            listOf("H11", "H11b", "H6").map { RecipeCompositionExemption(it, "*", "j") },
        )
        assertEquals(listOf("H11", "H11b"), kept.map { it.rule })
    }

    @Test
    fun h11_and_h11b_do_not_measure_sets_with_an_observed_working_set_or_bodyweight_reference() {
        fun referenced(kind: PlanLoadReferenceKind) = listOf(
            SetRecipe(
                reps = 5,
                percent = 100.0,
                loadBasis = LoadBasis.PERCENT_TM,
                reference = PlanLoadReference(kind = kind, configurationId = CatalogIds.SQ_LOW),
            ),
        )
        // Control: sin referencia, 5 reps al 100 % del TM con TM = 1RM rompen H11 (3+ reps sobre el 92 %) y H11b (caben 1).
        val plain = listOf(SetRecipe(reps = 5, percent = 100.0, loadBasis = LoadBasis.PERCENT_TM))
        assertEquals(1, intensityFindings("H11", plain, trainingMaxPercent = 1.0).size)
        assertEquals(1, intensityFindings("H11b", plain, trainingMaxPercent = 1.0).size)
        // Con una referencia de trabajo observado o de lastre la serie no se expresa sobre el 1RM: no se mide.
        listOf(PlanLoadReferenceKind.OBSERVED_WORKING_SET, PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL).forEach { kind ->
            assertTrue("$kind: H11", intensityFindings("H11", referenced(kind), trainingMaxPercent = 1.0).isEmpty())
            assertTrue("$kind: H11b", intensityFindings("H11b", referenced(kind), trainingMaxPercent = 1.0).isEmpty())
        }
        // Una referencia 1RM o TM del mismo ejercicio sí es una base de 1RM: se sigue midiendo.
        listOf(PlanLoadReferenceKind.EXERCISE_1RM, PlanLoadReferenceKind.EXERCISE_TM).forEach { kind ->
            assertEquals("$kind: H11", 1, intensityFindings("H11", referenced(kind), trainingMaxPercent = 1.0).size)
            assertEquals("$kind: H11b", 1, intensityFindings("H11b", referenced(kind), trainingMaxPercent = 1.0).size)
        }
    }

    @Test
    fun h11b_does_not_repeat_what_h11_already_flags_above_100_percent_1rm() {
        // 1 rep al 102,5 % del 1RM: lo caza H11 y H11b se calla (antes añadía «caben como máximo 0»).
        val single = pct1rm(1, 1, 102.5)
        assertEquals(1, intensityFindings("H11", single).size)
        assertTrue(intensityFindings("H11b", single).isEmpty())
        // Lo mismo con un TM que resuelve por encima del 1RM: 110 % TM con TM = 1RM (antes «caben como máximo -2»).
        val overTm = listOf(SetRecipe(reps = 5, percent = 110.0, loadBasis = LoadBasis.PERCENT_TM))
        assertTrue(intensityFindings("H11", overTm, trainingMaxPercent = 1.0).isNotEmpty())
        assertTrue(intensityFindings("H11b", overTm, trainingMaxPercent = 1.0).isEmpty())
        // En el 100 % exacto Epley sigue midiendo: 2 reps al 100 % del 1RM no caben (cabe 1).
        assertEquals(1, intensityFindings("H11b", pct1rm(1, 2, 100.0)).size)
    }
}
