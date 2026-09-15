package com.example.kpkn.data.protocols.definitions

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.ProtocolBlock
import com.example.kpkn.data.protocols.ProtocolFidelitySpec
import com.example.kpkn.data.protocols.ProtocolKind
import com.example.kpkn.data.protocols.ProtocolPublicationStatus
import com.example.kpkn.data.protocols.RecipeCompositionExemption
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.attributed
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.dropT3
import com.example.kpkn.data.protocols.rangeRirSets
import com.example.kpkn.data.protocols.repeatPercentSets
import com.example.kpkn.data.protocols.rpeSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe

object BodybuildingProtocols {
    private val phulExemptions = listOf(
        RecipeCompositionExemption("H5a", "*", "PHUL Lower Power programa sentadilla y peso muerto pesados el mismo día"),
    )

    private fun powerUpper() = day(
        "Upper Power",
        weekday = 1,
        slots = listOf(
            slot("bp", SlotRole.T1_MAIN, CatalogIds.BP, repeatPercentSets(3, 3, 82.0, 240), 240, LiftSlot.BENCH, isCompetitionLift = true),
            slot("row", SlotRole.T2_SUPPLEMENTAL, CatalogIds.ROW, rpeSets(3, 5, 8.0), 150),
            slot("ohp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.OHP, repeatPercentSets(3, 5, 75.0, 150), 150, LiftSlot.OVERHEAD),
            slot("pull", SlotRole.T3_ACCESSORY, CatalogIds.PULLUP, rpeSets(3, 6, 8.0), 120),
            slot("jm", SlotRole.T3_ACCESSORY, CatalogIds.JM, rpeSets(3, 8, 8.0), 90),
            kpknAssist("curl", CatalogIds.CURL, 3, 10, 60),
        ),
    )

    private fun powerLower() = day(
        "Lower Power",
        weekday = 2,
        slots = listOf(
            slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, repeatPercentSets(3, 3, 82.0, 240), 240, LiftSlot.SQUAT, isCompetitionLift = true),
            slot("dl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL, repeatPercentSets(3, 3, 80.0, 240), 240, LiftSlot.DEADLIFT, isCompetitionLift = true),
            slot("ghr", SlotRole.T3_ACCESSORY, CatalogIds.GHR, rpeSets(3, 8, 8.0), 90),
            slot("calf", SlotRole.T3_ACCESSORY, CatalogIds.CALF, rpeSets(3, 10, 8.0), 60),
            kpknAssist("pallof", CatalogIds.PALLOF, 3, 10, 60),
        ),
    )

    private fun hypUpper() = day(
        "Upper Hypertrophy",
        weekday = 4,
        slots = listOf(
            slot("bp", SlotRole.T1_MAIN, CatalogIds.BP, rangeRirSets(4, 8, 12, 2), 180, LiftSlot.BENCH),
            slot("inc", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP_INC_DB, rangeRirSets(3, 8, 12, 2), 120, supplementalOf = "bp"),
            slot("row", SlotRole.T3_ACCESSORY, CatalogIds.CSR, rangeRirSets(3, 8, 12, 2), 90),
            slot("lat", SlotRole.T3_ACCESSORY, CatalogIds.LATERAL, rangeRirSets(3, 12, 15, 1), 60),
            slot("fly", SlotRole.T3_ACCESSORY, CatalogIds.FLY_INC, rangeRirSets(3, 10, 12, 1), 60),
            slot("tri", SlotRole.T3_ACCESSORY, CatalogIds.OH_TRI, rangeRirSets(3, 10, 12, 1), 60),
        ),
    )

    private fun hypLower() = day(
        "Lower Hypertrophy",
        weekday = 5,
        slots = listOf(
            slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_HIGH, rangeRirSets(4, 8, 12, 2), 180, LiftSlot.SQUAT),
            slot("rdl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.RDL, rangeRirSets(3, 8, 12, 2), 120, LiftSlot.DEADLIFT),
            slot("press", SlotRole.T3_ACCESSORY, CatalogIds.PRESS_LEG, rangeRirSets(3, 10, 12, 1), 90),
            slot("curl", SlotRole.T3_ACCESSORY, CatalogIds.CURL_H, rangeRirSets(3, 10, 12, 1), 90),
            slot("ext", SlotRole.T3_ACCESSORY, CatalogIds.LEG_EXT, rangeRirSets(2, 12, 15, 1), 60),
            slot("calf", SlotRole.T3_ACCESSORY, CatalogIds.CALF, rangeRirSets(4, 10, 12, 1), 60),
        ),
    )

    val phul = Protocol(
        id = "phul-verified",
        name = "PHUL",
        emoji = "🧱",
        description = "4 días, 4 semanas: power upper/lower 3-5×3-5 @≈80-85 % e hipertrofia upper/lower 8-12 en la misma semana.",
        author = "Brandon Campbell",
        tags = listOf("powerbuilding", "intermedio", "4 días", "4 semanas", "%", "RPE"),
        blocks = listOf(ProtocolBlock("PHUL", 4, "Acumulación", 70, 85)),
        defaultSplit = "ul_x4",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        kind = ProtocolKind.WEEKLY_SPLIT,
        source = attributed("PHUL workout", "https://www.muscleandstrength.com/workouts/phul-workout", "Brandon Campbell"),
        recipe = TrainingPlanRecipe(
            id = "phul-verified",
            weeks = (1..4).map { w ->
                weekRecipe(w, 0, "PHUL", BlockGoal.ACCUMULATION, listOf(powerUpper(), powerLower(), hypUpper(), hypLower()), weekName = "Semana $w")
            },
            trainingMaxPercent = 0.90,
            liftSlots = sbdSlots(),
            exemptions = phulExemptions,
            claimedDaysPerWeek = 4,
            claimedLevel = "intermedio",
            repeats = true,
        ),
        fidelitySpec = ProtocolFidelitySpec(4, 4, requiresPercent = true, claimedLevel = "intermedio", percentAnchors = mapOf("power" to listOf(82.0))),
        exemptions = phulExemptions,
    )

    private val phatExemptions = listOf(
        RecipeCompositionExemption("H3", "*", "PHAT Lower Power y Pecho/Brazos agrupan el mismo dominante por diseño de Norton"),
    )

    private fun phatPowerUpper() = day(
        "Power Upper",
        weekday = 1,
        slots = listOf(
            slot("speed", SlotRole.SPEED, CatalogIds.BP, repeatPercentSets(6, 3, 68.0, 60), 60, LiftSlot.BENCH, technique = TechniqueModifier.SPEED, priority = SlotPriority.SPEED, supplementalOf = "bp"),
            slot("bp", SlotRole.T1_MAIN, CatalogIds.BP, repeatPercentSets(3, 3, 82.0, 240), 240, LiftSlot.BENCH, isCompetitionLift = true),
            slot("row", SlotRole.T2_SUPPLEMENTAL, CatalogIds.PENDLAY, rpeSets(3, 5, 8.0), 150),
            slot("ohp", SlotRole.T3_ACCESSORY, CatalogIds.OHP, rpeSets(3, 6, 8.0), 120),
            slot("pull", SlotRole.T3_ACCESSORY, CatalogIds.PULLUP, rpeSets(3, 6, 8.0), 120),
            kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
        ),
        priority = SlotPriority.SPEED,
    )

    private fun phatPowerLower() = day(
        "Power Lower",
        weekday = 2,
        slots = listOf(
            slot("speed", SlotRole.SPEED, CatalogIds.SQ_BOX, repeatPercentSets(6, 3, 68.0, 60), 60, LiftSlot.SQUAT, technique = TechniqueModifier.SPEED, priority = SlotPriority.SPEED, supplementalOf = "sq"),
            slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, repeatPercentSets(3, 3, 82.0, 240), 240, LiftSlot.SQUAT, isCompetitionLift = true),
            slot("hack", SlotRole.T2_SUPPLEMENTAL, CatalogIds.SQ_HACK, rpeSets(2, 6, 8.0), 120, supplementalOf = "sq"),
            slot("ext", SlotRole.T3_ACCESSORY, CatalogIds.LEG_EXT, rpeSets(1, 12, 8.0), 60),
            slot("ghr", SlotRole.T3_ACCESSORY, CatalogIds.GHR, rpeSets(3, 8, 8.0), 90),
            kpknAssist("calf", CatalogIds.CALF, 3, 12, 60),
        ),
        priority = SlotPriority.SPEED,
    )

    private fun phatChest() = day(
        "Pecho/Brazos",
        weekday = 4,
        slots = listOf(
            slot("bp", SlotRole.T1_MAIN, CatalogIds.BP, rangeRirSets(3, 8, 10, 2), 180, LiftSlot.BENCH),
            slot("inc", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP_INC, rangeRirSets(3, 8, 10, 2), 120, supplementalOf = "bp"),
            slot("dips", SlotRole.T3_ACCESSORY, CatalogIds.DIPS, rangeRirSets(3, 8, 10, 1), 90),
            slot("fly", SlotRole.T3_ACCESSORY, CatalogIds.FLY, rangeRirSets(2, 10, 12, 1), 60),
            slot("oh-tri", SlotRole.T3_ACCESSORY, CatalogIds.OH_TRI, rangeRirSets(3, 10, 12, 1), 60),
            slot("curl", SlotRole.T3_ACCESSORY, CatalogIds.CURL, rangeRirSets(3, 10, 12, 1), 60),
        ),
    )

    val phat = Protocol(
        id = "phat-verified",
        name = "PHAT",
        emoji = "🦏",
        description = "5 días, 4 semanas: 2 power + 3 hipertrofia con speed work 6×3 @ 65-70 % en la misma semana.",
        author = "Layne Norton",
        tags = listOf("powerbuilding", "avanzado", "5 días", "4 semanas", "%", "RPE"),
        blocks = listOf(ProtocolBlock("PHAT", 4, "Acumulación", 65, 85)),
        defaultSplit = "phat_hybrid",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        kind = ProtocolKind.WEEKLY_SPLIT,
        source = attributed("PHAT: Power Hypertrophy Adaptive Training", "https://www.simplyshredded.com/mega-feature-layne-norton-training-series-full-power-hypertrophy-routine-updated-2011.html", "Layne Norton"),
        recipe = TrainingPlanRecipe(
            id = "phat-verified",
            weeks = (1..4).map { w ->
                weekRecipe(w, 0, "PHAT", BlockGoal.ACCUMULATION, listOf(
                    phatPowerUpper(),
                    phatPowerLower(),
                    DayArchetypes.bbPull(2).copy(weekday = 3, label = "Espalda/Hombros"),
                    phatChest(),
                    DayArchetypes.bbLegs(2).copy(weekday = 6, label = "Pierna hipertrofia"),
                ), weekName = "Semana $w")
            },
            trainingMaxPercent = 0.90,
            liftSlots = sbdSlots(),
            exemptions = phatExemptions,
            claimedDaysPerWeek = 5,
            claimedLevel = "avanzado",
            repeats = true,
        ),
        fidelitySpec = ProtocolFidelitySpec(4, 5, requiresPercent = true, claimedLevel = "avanzado", percentAnchors = mapOf("power" to listOf(82.0), "speed" to listOf(68.0))),
        exemptions = phatExemptions,
    )
}

object KpknNativeHypertrophyProtocols {
    val ppl = Protocol(
        id = "kpkn-ppl-6",
        name = "PPL 6 días KPKN",
        emoji = "🎯",
        description = "6 días, 12 semanas: Push/Pull/Legs doble frecuencia, RIR 3→1, cero exenciones.",
        author = "KPKN Fit",
        tags = listOf("culturismo", "hipertrofia", "intermedio", "6 días", "12 semanas", "RPE"),
        blocks = listOf(
            ProtocolBlock("Volumen", 5, "Acumulación", 60, 75),
            ProtocolBlock("Intensificación", 5, "Intensificación", 70, 82),
            ProtocolBlock("Descarga", 2, "Descarga", 50, 65),
        ),
        defaultSplit = "ppl_x6",
        publicationStatus = ProtocolPublicationStatus.KPKN_NATIVE,
        kind = ProtocolKind.WEEKLY_SPLIT,
        source = attributed("KPKN PPL hipertrofia", "https://kpkn.fit/protocols/kpkn-ppl-6", "KPKN Fit"),
        recipe = TrainingPlanRecipe(
            id = "kpkn-ppl-6",
            weeks = (1..12).map { w ->
                val rir = when {
                    w <= 5 -> 3 - ((w - 1) / 2).coerceAtMost(2)
                    w <= 10 -> 1
                    else -> 4
                }
                val goal = when {
                    w <= 5 -> BlockGoal.ACCUMULATION
                    w <= 10 -> BlockGoal.INTENSIFICATION
                    else -> BlockGoal.DELOAD
                }
                val secondRir = if (goal == BlockGoal.DELOAD) rir else (rir - 1).coerceAtLeast(0)
                weekRecipe(w, when { w <= 5 -> 0; w <= 10 -> 1; else -> 2 }, if (w <= 5) "Volumen" else if (w <= 10) "Intensificación" else "Descarga", goal, listOf(
                    DayArchetypes.bbPush(rir).copy(weekday = 1),
                    DayArchetypes.bbPull(rir).copy(weekday = 2),
                    DayArchetypes.bbLegs(rir).copy(weekday = 3),
                    DayArchetypes.bbPush(secondRir).copy(weekday = 4, label = "Empuje 2"),
                    DayArchetypes.bbPull(secondRir).copy(weekday = 5, label = "Tirón 2"),
                    DayArchetypes.bbLegs(secondRir).copy(weekday = 6, label = "Pierna 2"),
                ))
            },
            claimedDaysPerWeek = 6,
            claimedLevel = "intermedio",
        ),
        fidelitySpec = ProtocolFidelitySpec(12, 6, requiresRpe = true, claimedLevel = "intermedio"),
    )

    val rpStyle = Protocol(
        id = "kpkn-rp-style",
        name = "Mesociclo RP-style KPKN",
        emoji = "📈",
        description = "6 semanas, 4 días UL: 5 sem MEV→MRV + descarga, RIR 3→0-1, landmarks por músculo.",
        author = "KPKN Fit",
        tags = listOf("culturismo", "hipertrofia", "avanzado", "4 días", "6 semanas", "RPE"),
        blocks = listOf(ProtocolBlock("Volumen", 5, "Acumulación", 60, 80), ProtocolBlock("Descarga", 1, "Descarga", 50, 65)),
        defaultSplit = "ul_x4",
        publicationStatus = ProtocolPublicationStatus.KPKN_NATIVE,
        kind = ProtocolKind.AUTOREGULATED_FRAMEWORK,
        source = attributed("KPKN mesociclo inspirado en RP", "https://kpkn.fit/protocols/kpkn-rp-style", "KPKN Fit"),
        recipe = TrainingPlanRecipe(
            id = "kpkn-rp-style",
            weeks = (1..6).map { w ->
                val rir = if (w == 6) 4 else (3 - (w - 1) / 2).coerceAtLeast(0)
                val goal = if (w == 6) BlockGoal.DELOAD else BlockGoal.ACCUMULATION
                val secondRir = if (w == 6) 4 else (rir - 1).coerceAtLeast(0)
                weekRecipe(w, if (w == 6) 1 else 0, if (w == 6) "Descarga" else "Volumen", goal, listOf(
                    DayArchetypes.bbTorso(rir).copy(weekday = 1, label = "Torso A"),
                    DayArchetypes.bbLegs(rir).copy(weekday = 2, label = "Pierna A"),
                    DayArchetypes.bbPush(rir).copy(weekday = 4, label = "Torso B"),
                    DayArchetypes.bbLegs(secondRir).copy(weekday = 5, label = "Pierna B"),
                ))
            },
            claimedDaysPerWeek = 4,
            claimedLevel = "avanzado",
            autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.RPE_CAP)),
        ),
        fidelitySpec = ProtocolFidelitySpec(6, 4, requiresRpe = true, claimedLevel = "avanzado"),
    )
}

object KpknNativeAutoregFrameworks {
    private fun rpeTopSet(reps: Int, rpe: Double, backoffs: Int) = listOf(
        SetRecipe(reps = reps, rpe = rpe, isTopSet = true, loadBasis = LoadBasis.RPE),
    ) + rpeSets(backoffs, reps, rpe - 1.0)

    val rts = Protocol(
        id = "kpkn-rts-style",
        name = "RTS-style KPKN",
        emoji = "🎛️",
        description = "8 semanas, 4 días, inspirado en RTS: top set @RPE 8 + fatiga 5 % (repeats). No es el producto de pago.",
        author = "KPKN Fit",
        tags = listOf("powerlifting", "avanzado", "4 días", "8 semanas", "RPE"),
        blocks = listOf(ProtocolBlock("Desarrollo", 5, "Intensificación", 70, 90), ProtocolBlock("Pivote", 3, "Peak", 80, 95)),
        defaultSplit = "pl_classic_4",
        publicationStatus = ProtocolPublicationStatus.KPKN_NATIVE,
        kind = ProtocolKind.AUTOREGULATED_FRAMEWORK,
        source = attributed("KPKN framework inspirado en RTS", "https://kpkn.fit/protocols/kpkn-rts-style", "KPKN Fit"),
        recipe = TrainingPlanRecipe(
            id = "kpkn-rts-style",
            weeks = (1..8).map { w ->
                val goal = if (w <= 5) BlockGoal.INTENSIFICATION else BlockGoal.PEAK
                val reps = if (w <= 5) 3 else 1
                val rpe = if (w <= 5) 8.0 else 9.0
                weekRecipe(w, if (w <= 5) 0 else 1, if (w <= 5) "Desarrollo" else "Pivote", goal, listOf(
                    DayArchetypes.plSquat(if (w <= 5) 80.0 else 90.0, t1Sets = 1 + 3, t1Reps = reps, weekday = 1).let { d ->
                        d.copy(slots = listOf(slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, rpeTopSet(reps, rpe, 3), if (rpe >= 9) 240 else 180, LiftSlot.SQUAT, isCompetitionLift = true)) + d.slots.drop(1))
                    },
                    DayArchetypes.plBenchHeavy(if (w <= 5) 78.0 else 88.0, weekday = 2),
                    DayArchetypes.plDeadlift(if (w <= 5) 75.0 else 88.0, weekday = 4),
                    DayArchetypes.plBenchVolume(if (w <= 5) 68.0 else 62.0, weekday = 5),
                ).map { day -> if (goal == BlockGoal.PEAK) day.dropT3(2) else day })
            },
            trainingMaxPercent = 0.90,
            liftSlots = sbdSlots(),
            progression = ProgressionRule.RepTargetDrivenTm(),
            claimedDaysPerWeek = 4,
            claimedLevel = "avanzado",
            autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.RPE_CAP)),
        ),
        fidelitySpec = ProtocolFidelitySpec(8, 4, requiresRpe = true, claimedLevel = "avanzado"),
    )

    val sbs = Protocol(
        id = "kpkn-sbs-rtf",
        name = "SBS RTF-style KPKN",
        emoji = "📊",
        description = "8 semanas, 4 días, inspirado en SBS RTF: 4 series + última al fallo, TM +0,5 %/rep extra. No es el producto de pago.",
        author = "KPKN Fit",
        tags = listOf("powerlifting", "intermedio", "4 días", "8 semanas", "AMRAP", "%"),
        blocks = listOf(ProtocolBlock("Base", 4, "Acumulación", 65, 80), ProtocolBlock("Intensificación", 4, "Intensificación", 75, 90)),
        defaultSplit = "pl_classic_4",
        publicationStatus = ProtocolPublicationStatus.KPKN_NATIVE,
        kind = ProtocolKind.AUTOREGULATED_FRAMEWORK,
        source = attributed("KPKN framework inspirado en SBS RTF", "https://kpkn.fit/protocols/kpkn-sbs-rtf", "KPKN Fit"),
        recipe = TrainingPlanRecipe(
            id = "kpkn-sbs-rtf",
            weeks = (1..8).map { w ->
                val pct = if (w <= 4) 70.0 + w else 78.0 + (w - 4)
                val goal = if (w <= 4) BlockGoal.ACCUMULATION else BlockGoal.INTENSIFICATION
                weekRecipe(w, if (w <= 4) 0 else 1, if (w <= 4) "Base" else "Intensificación", goal, listOf(
                    DayArchetypes.plSquat(pct, t1Sets = 4, t1Reps = if (w <= 4) 6 else 4, t1Amrap = true, weekday = 1),
                    DayArchetypes.plBenchHeavy(pct, t1Sets = 4, t1Reps = if (w <= 4) 6 else 4, t1Amrap = true, weekday = 2),
                    DayArchetypes.plDeadlift(pct - 3, t1Sets = 3, t1Reps = if (w <= 4) 5 else 3, t1Amrap = true, weekday = 4),
                    DayArchetypes.plBenchVolume((pct - 8).coerceAtLeast(60.0), weekday = 5),
                ))
            },
            trainingMaxPercent = 0.90,
            liftSlots = sbdSlots(),
            progression = ProgressionRule.RepTargetDrivenTm(),
            claimedDaysPerWeek = 4,
            claimedLevel = "intermedio",
            autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.AMRAP_TM)),
        ),
        fidelitySpec = ProtocolFidelitySpec(8, 4, requiresAmrap = true, requiresPercent = true, claimedLevel = "intermedio"),
    )
}
