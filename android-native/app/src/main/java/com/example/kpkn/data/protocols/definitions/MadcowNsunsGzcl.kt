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
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.attributed
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.dropT3
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.repeatPercentSets
import com.example.kpkn.data.protocols.rpeSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe

object MadcowProtocol {
    private val ramp = listOf(50.0, 62.5, 75.0, 87.5, 100.0)

    private fun rampSets(top: Double, reps: Int = 5, lastReps: Int = 5) = ramp.mapIndexed { index, pct ->
        SetRecipe(
            reps = if (index == ramp.lastIndex) lastReps else reps,
            percent = top * pct / 100.0,
            loadBasis = LoadBasis.PERCENT_OF_TOP_SET,
        )
    }

    fun recipe(): TrainingPlanRecipe {
        val weeks = (1..8).map { w ->
            val onRamp = when (w) {
                1 -> 0.925
                2 -> 0.95
                3 -> 0.975
                else -> 1.0
            }
            val top = 100.0 * onRamp
            weekRecipe(
                w, if (w <= 4) 0 else 1, if (w <= 4) "On-ramp" else "Progresión",
                if (w <= 4) BlockGoal.ACCUMULATION else BlockGoal.INTENSIFICATION,
                listOf(
                    day("Volumen", weekday = 1, slots = listOf(
                        slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, rampSets(top), 240, LiftSlot.SQUAT, isCompetitionLift = true),
                        slot("bp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, rampSets(top), 180, LiftSlot.BENCH, isCompetitionLift = true),
                        slot("row", SlotRole.T3_ACCESSORY, CatalogIds.ROW, rpeSets(5, 5, 8.0), 120),
                        kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
                        kpknAssist("pallof", CatalogIds.PALLOF, 3, 10, 60),
                    )),
                    day("Recuperación", weekday = 3, slots = listOf(
                        slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(180, 5 to top * 0.5, 5 to top * 0.625, 5 to top * 0.75, 5 to top * 0.75, basis = LoadBasis.PERCENT_OF_TOP_SET), 180, LiftSlot.SQUAT, isCompetitionLift = true),
                        slot("inc", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP_INC, repeatPercentSets(4, 5, top * 0.7, 150, basis = LoadBasis.PERCENT_OF_TOP_SET), 150, LiftSlot.BENCH, supplementalOf = "sq"),
                        slot("dl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL, percentSets(180, 5 to top * 0.5, 5 to top * 0.625, 5 to top * 0.75, 5 to top * 0.75, basis = LoadBasis.PERCENT_OF_TOP_SET), 180, LiftSlot.DEADLIFT, isCompetitionLift = true),
                        kpknAssist("chin", CatalogIds.CHIN, 3, 8, 120),
                        kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
                    )),
                    day("Intensidad", weekday = 5, slots = listOf(
                        slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(240, 5 to top * 0.5, 5 to top * 0.625, 5 to top * 0.75, 5 to top * 0.875, 3 to top * 1.025, basis = LoadBasis.PERCENT_OF_TOP_SET).mapIndexed { index, set ->
                            if (index == 4) set.copy(isTopSet = true) else set
                        } + listOf(SetRecipe(reps = 8, percent = top * 0.75, loadBasis = LoadBasis.PERCENT_OF_TOP_SET)), 240, LiftSlot.SQUAT, isCompetitionLift = true),
                        slot("bp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, rampSets(top * 0.9, lastReps = 3), 180, LiftSlot.BENCH, isCompetitionLift = true, supplementalOf = "sq"),
                        slot("row", SlotRole.T3_ACCESSORY, CatalogIds.ROW, rpeSets(5, 5, 8.0), 120),
                        kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
                        kpknAssist("wheel", CatalogIds.WHEEL, 3, 8, 60),
                    )),
                ),
            )
        }
        return TrainingPlanRecipe(
            id = "madcow-5x5",
            weeks = weeks,
            trainingMaxPercent = 1.0,
            liftSlots = sbdSlots(),
            progression = ProgressionRule.WeeklyPercent(2.5),
            claimedDaysPerWeek = 3,
            claimedLevel = "intermedio",
        )
    }

    val definition = Protocol(
        id = "madcow-5x5",
        name = "Madcow 5×5",
        emoji = "🐮",
        description = "3 días, 8 semanas: ramp 50/62,5/75/87,5/100 % del 5RM; viernes triple @ 102,5 % + 1×8 @ 75 %; +2,5 %/semana.",
        author = "Madcow (Bill Starr)",
        tags = listOf("powerlifting", "intermedio", "3 días", "8 semanas", "%"),
        blocks = listOf(ProtocolBlock("On-ramp", 4, "Acumulación", 50, 100), ProtocolBlock("Progresión", 4, "Intensificación", 50, 110)),
        defaultSplit = "madcow_5x5",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        source = attributed("Madcow 5x5", "https://stronglifts.com/madcow-5x5/", "Madcow / Bill Starr"),
        recipe = recipe(),
        fidelitySpec = ProtocolFidelitySpec(8, 3, requiresPercent = true, claimedLevel = "intermedio", percentAnchors = mapOf("ramp" to listOf(50.0, 62.5, 75.0, 87.5, 100.0))),
    )
}

object NSunsProtocol {
    private fun t1Bench() = percentSets(
        180,
        8 to 65.0, 6 to 75.0, 4 to 85.0, 4 to 85.0, 4 to 85.0, 5 to 80.0, 6 to 75.0, 7 to 70.0, 8 to 65.0,
        amrapLast = true,
    )

    private fun t1Lower() = percentSets(
        180,
        5 to 75.0, 3 to 85.0, 1 to 95.0, 3 to 90.0, 3 to 85.0, 3 to 80.0, 5 to 75.0, 5 to 70.0, 5 to 65.0,
        amrapLast = true,
    )

    private fun t2() = percentSets(
        150,
        6 to 50.0, 5 to 60.0, 3 to 70.0, 5 to 70.0, 7 to 70.0, 4 to 70.0, 6 to 70.0, 8 to 70.0,
    )

    fun recipe(): TrainingPlanRecipe {
        val weeks = (1..6).map { w ->
            weekRecipe(w, 0, "LP", BlockGoal.INTENSIFICATION, listOf(
                day("Banca/OHP", weekday = 1, slots = listOf(
                    slot("t1", SlotRole.T1_MAIN, CatalogIds.BP, t1Bench(), 240, LiftSlot.BENCH, isCompetitionLift = true),
                    slot("t2", SlotRole.T2_SUPPLEMENTAL, CatalogIds.OHP, t2(), 150, LiftSlot.OVERHEAD, supplementalOf = "t1"),
                    kpknAssist("row", CatalogIds.PENDLAY, 3, 8, 120),
                    kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
                    kpknAssist("jm", CatalogIds.JM, 3, 8, 90),
                )),
                day("Sentadilla/Sumo", weekday = 2, slots = listOf(
                    slot("t1", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, t1Lower(), 240, LiftSlot.SQUAT, isCompetitionLift = true),
                    slot("t2", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL_SUMO, t2(), 150, LiftSlot.DEADLIFT, supplementalOf = "t1"),
                    kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
                    kpknAssist("pallof", CatalogIds.PALLOF, 3, 10, 60),
                    kpknAssist("calf", CatalogIds.CALF, 3, 12, 60),
                )),
                day("Banca/Cerrado", weekday = 4, slots = listOf(
                    slot("t1", SlotRole.T1_MAIN, CatalogIds.BP, t1Bench(), 240, LiftSlot.BENCH, isCompetitionLift = true),
                    slot("t2", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, t2(), 150, LiftSlot.BENCH, technique = TechniqueModifier.CLOSE_GRIP, supplementalOf = "t1"),
                    kpknAssist("pull", CatalogIds.PULLUP, 3, 6, 120),
                    kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
                    kpknAssist("oh-tri", CatalogIds.OH_TRI, 3, 10, 60),
                )),
                day("Peso muerto/Frontal", weekday = 5, slots = listOf(
                    slot("t1", SlotRole.T1_MAIN, CatalogIds.DL, t1Lower(), 240, LiftSlot.DEADLIFT, isCompetitionLift = true),
                    slot("t2", SlotRole.T2_SUPPLEMENTAL, CatalogIds.SQ_FRONT, t2(), 150, LiftSlot.SQUAT, supplementalOf = "t1"),
                    kpknAssist("row", CatalogIds.ROW, 3, 8, 120),
                    kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
                    kpknAssist("shrug", CatalogIds.SHRUG, 2, 8, 60),
                )),
            ))
        }
        return TrainingPlanRecipe(
            id = "nsuns-531-lp-4d",
            weeks = weeks,
            trainingMaxPercent = 0.90,
            liftSlots = sbdSlots(),
            progression = ProgressionRule.AmrapDrivenTm(),
            exemptions = listOf(
                RecipeCompositionExemption("H5b", "*", "nSuns T1 9 + T2 8 series axiales por diseño"),
                RecipeCompositionExemption("H6", "*", "nSuns 17 series de dos levantamientos pesados"),
            ),
            claimedDaysPerWeek = 4,
            claimedLevel = "avanzado",
            autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.AMRAP_TM)),
        )
    }

    val definition = Protocol(
        id = "nsuns-531-lp-4d",
        name = "nSuns 5/3/1 LP 4 días",
        emoji = "📈",
        description = "4 días, 6 semanas: T1 9 series y T2 8 series con la tabla nSuns; TM por AMRAP. Agarre cerrado = banca + CLOSE_GRIP.",
        author = "nSuns",
        tags = listOf("powerlifting", "avanzado", "4 días", "6 semanas", "AMRAP", "%"),
        blocks = listOf(ProtocolBlock("LP", 6, "Intensificación", 50, 95, 1.3)),
        defaultSplit = "nsuns_4day",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        source = attributed("nSuns 5/3/1 LP", "https://www.reddit.com/r/nSuns/", "nSuns"),
        recipe = recipe(),
        fidelitySpec = ProtocolFidelitySpec(6, 4, requiresAmrap = true, requiresPercent = true, claimedLevel = "avanzado", percentAnchors = mapOf("t1" to listOf(65.0, 75.0, 85.0))),
        exemptions = recipe().exemptions,
    )
}

object GzclProtocols {
    private fun t1Stage(week: Int): List<SetRecipe> {
        val stage = ((week - 1) / 4).coerceIn(0, 2)
        return when (stage) {
            0 -> repeatPercentSets(5, 3, 85.0, 240, amrapLast = true)
            1 -> repeatPercentSets(6, 2, 90.0, 240, amrapLast = true)
            else -> repeatPercentSets(10, 1, 95.0, 240, amrapLast = true)
        }
    }

    private fun t2Stage(week: Int): List<SetRecipe> {
        val stage = ((week - 1) / 4).coerceIn(0, 2)
        val reps = listOf(10, 8, 6)[stage]
        val pct = listOf(65.0, 70.0, 75.0)[stage]
        return repeatPercentSets(3, reps, pct, 120)
    }

    fun gzclpRecipe(): TrainingPlanRecipe {
        val days = listOf(
            Triple("Sentadilla", CatalogIds.SQ_LOW, LiftSlot.SQUAT) to CatalogIds.SQ_FRONT,
            Triple("Banca", CatalogIds.BP, LiftSlot.BENCH) to CatalogIds.BP_INC,
            Triple("Peso muerto", CatalogIds.DL, LiftSlot.DEADLIFT) to CatalogIds.RDL,
            Triple("Press militar", CatalogIds.OHP, LiftSlot.OVERHEAD) to CatalogIds.BP,
        )
        val weeks = (1..12).map { w ->
            weekRecipe(w, (w - 1) / 4, "Etapa ${(w - 1) / 4 + 1}", if (w <= 8) BlockGoal.ACCUMULATION else BlockGoal.INTENSIFICATION, days.mapIndexed { index, (main, t2id) ->
                val (label, id, lift) = main
                day(label, weekday = listOf(1, 2, 4, 5)[index], slots = listOf(
                    slot("t1", SlotRole.T1_MAIN, id, t1Stage(w), 240, lift, isCompetitionLift = lift != LiftSlot.OVERHEAD),
                    slot("t2", SlotRole.T2_SUPPLEMENTAL, t2id, t2Stage(w), 120, lift.takeIf { t2id == id }, supplementalOf = "t1"),
                    slot("t3a", SlotRole.T3_ACCESSORY, if (index % 2 == 0) CatalogIds.LAT else CatalogIds.PENDLAY, rpeSets(3, 15, 8.0, repsMax = 20), 90),
                    slot("t3b", SlotRole.T3_ACCESSORY, if (index < 2) CatalogIds.GHR else CatalogIds.JM, rpeSets(if (index < 2) 3 else 2, 15, 8.0), 60),
                    kpknAssist("core", CatalogIds.PALLOF, 3, 10, 60),
                ))
            })
        }
        return TrainingPlanRecipe(
            id = "gzclp",
            weeks = weeks,
            trainingMaxPercent = 0.90,
            liftSlots = sbdSlots(),
            progression = ProgressionRule.AmrapDrivenTm(),
            claimedDaysPerWeek = 4,
            claimedLevel = "intermedio",
            autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.AMRAP_TM)),
        )
    }

    val gzclp = Protocol(
        id = "gzclp",
        name = "GZCLP",
        emoji = "🏗️",
        description = "4 días, 12 semanas: T1 5×3+ → 6×2+ → 10×1+; T2 3×10 → 3×8 → 3×6; T3 3×15+.",
        author = "Cody Lefever",
        tags = listOf("powerlifting", "intermedio", "4 días", "12 semanas", "AMRAP", "%"),
        blocks = listOf(ProtocolBlock("Etapa 1", 4, "Acumulación", 65, 85), ProtocolBlock("Etapa 2", 4, "Acumulación", 70, 90), ProtocolBlock("Etapa 3", 4, "Intensificación", 75, 95)),
        defaultSplit = "pl_classic_4",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        source = attributed("The GZCL Method", "https://gzclmethod.com/", "Cody Lefever", variant = "GZCLP"),
        recipe = gzclpRecipe(),
        fidelitySpec = ProtocolFidelitySpec(12, 4, requiresAmrap = true, requiresPercent = true, claimedLevel = "intermedio", percentAnchors = mapOf("t1" to listOf(85.0))),
    )

    private fun gzclDay(label: String, weekday: Int, t1Id: String, t1Lift: LiftSlot, t2Id: String, t1Sets: List<SetRecipe>, t2Sets: List<SetRecipe>, t3a: String, t3b: String) = day(
        label, weekday = weekday, slots = listOf(
            slot("t1", SlotRole.T1_MAIN, t1Id, t1Sets, if (t1Sets.any { (it.percent ?: 0.0) >= 85 }) 240 else 180, t1Lift, isCompetitionLift = t1Lift != LiftSlot.OVERHEAD),
            slot("t2", SlotRole.T2_SUPPLEMENTAL, t2Id, t2Sets, 120, supplementalOf = "t1"),
            slot("t3a", SlotRole.T3_ACCESSORY, t3a, rpeSets(3, 12, 8.0), 90),
            slot("t3b", SlotRole.T3_ACCESSORY, t3b, rpeSets(3, 15, 8.0), 60),
            kpknAssist("core", CatalogIds.WHEEL, 3, 10, 60),
        ),
    )

    fun jackedAndTanRecipe(): TrainingPlanRecipe {
        val rms = listOf(10, 8, 6, 4, 2, 1, 8, 6, 4, 3, 2, 1)
        val weeks = rms.mapIndexed { i, reps ->
            val pct = (100.0 - (reps - 1) * 2.5).coerceIn(75.0, 100.0)
            val goal = when {
                i == 5 || i == 11 -> BlockGoal.PEAK
                i < 5 -> BlockGoal.ACCUMULATION
                else -> BlockGoal.INTENSIFICATION
            }
            val t1 = if (reps == 1) listOf(SetRecipe(reps = 1, percent = 100.0, isTopSet = true, amrap = false)) +
                repeatPercentSets(4, 2, 87.5, 180) else repeatPercentSets(1, reps, pct, 240, amrapLast = true) +
                repeatPercentSets(3, (reps - 2).coerceAtLeast(2), pct - 7.5, 180)
            val t2sets = when {
                i == 5 || i == 11 -> rpeSets(3, 5, 7.0)
                i < 6 -> repeatPercentSets(5, 5, 75.0, 120)
                else -> repeatPercentSets(6, 3, 82.5, 120)
            }
            weekRecipe(i + 1, when { i < 6 -> 0; else -> 1 }, if (i < 6) "Ola 1" else "Ola 2", goal, listOf(
                gzclDay("Sentadilla", 1, CatalogIds.SQ_LOW, LiftSlot.SQUAT, CatalogIds.SQ_FRONT, t1, t2sets, CatalogIds.GHR, CatalogIds.PALLOF),
                gzclDay("Banca", 2, CatalogIds.BP, LiftSlot.BENCH, CatalogIds.BP_INC, t1, t2sets, CatalogIds.PENDLAY, CatalogIds.JM),
                gzclDay("Peso muerto", 4, CatalogIds.DL, LiftSlot.DEADLIFT, CatalogIds.RDL, t1.take(3), if (i < 6) repeatPercentSets(3, 6, 70.0, 120) else repeatPercentSets(3, 4, 75.0, 120), CatalogIds.ROW, CatalogIds.GHR),
                gzclDay("Press", 5, CatalogIds.OHP, LiftSlot.OVERHEAD, CatalogIds.BP, t1, t2sets, CatalogIds.LAT, CatalogIds.FACE),
            ))
        }
        return TrainingPlanRecipe("gzcl-jt-2", weeks, 0.90, sbdSlots(), ProgressionRule.RepMaxAutoregulated, claimedDaysPerWeek = 4, claimedLevel = "avanzado", autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.AMRAP_TM)))
    }

    val jackedAndTan = Protocol(
        id = "gzcl-jt-2",
        name = "GZCL Jacked & Tan 2.0",
        emoji = "🏗️",
        description = "12 semanas, 4 días: T1 RM semanal 10→2 y test 1RM en sem 6 y 12 con back-offs 4×2.",
        author = "Cody Lefever",
        tags = listOf("powerlifting", "avanzado", "4 días", "12 semanas", "AMRAP", "%"),
        blocks = listOf(ProtocolBlock("Ola 1", 6, "Acumulación", 75, 100), ProtocolBlock("Ola 2", 6, "Intensificación", 75, 100)),
        defaultSplit = "pl_classic_4",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        source = attributed("Jacked and Tan 2.0", "https://gzclmethod.com/", "Cody Lefever", variant = "J&T 2.0"),
        recipe = jackedAndTanRecipe(),
        fidelitySpec = ProtocolFidelitySpec(12, 4, requiresAmrap = true, requiresPercent = true, claimedLevel = "avanzado", percentAnchors = mapOf("w1" to listOf(77.5))),
    )

    fun ripplerRecipe(): TrainingPlanRecipe {
        val t1 = listOf(85.0, 87.5, 90.0, 92.5, 87.5, 90.0, 92.5, 95.0, 90.0, 92.5, 97.5, 100.0)
        val t2 = listOf(80.0, 85.0, 90.0, 82.5, 87.5, 92.5, 85.0, 90.0, 92.5, 80.0, 0.0, 0.0)
        val weeks = t1.mapIndexed { i, pct ->
            val goal = if (i >= 10) BlockGoal.PEAK else if (i >= 6) BlockGoal.INTENSIFICATION else BlockGoal.ACCUMULATION
            val t2sets = if (t2[i] == 0.0) rpeSets(3, 8, 7.0) else repeatPercentSets(3, 5, t2[i], 120)
            weekRecipe(i + 1, i / 4, "Bloque ${i / 4 + 1}", goal, listOf(
                gzclDay("Sentadilla", 1, CatalogIds.SQ_LOW, LiftSlot.SQUAT, CatalogIds.SQ_FRONT, repeatPercentSets(3, 2, pct, 240, amrapLast = i < 10), t2sets, CatalogIds.GHR, CatalogIds.PALLOF),
                gzclDay("Banca", 2, CatalogIds.BP, LiftSlot.BENCH, CatalogIds.BP_INC, repeatPercentSets(3, 2, pct, 240, amrapLast = i < 10), t2sets, CatalogIds.PENDLAY, CatalogIds.JM),
                gzclDay("Peso muerto", 4, CatalogIds.DL, LiftSlot.DEADLIFT, CatalogIds.RDL, repeatPercentSets(2, 2, pct, 240), if (t2[i] == 0.0) rpeSets(3, 6, 7.0) else repeatPercentSets(3, 5, t2[i] - 5, 120), CatalogIds.ROW, CatalogIds.GHR),
                gzclDay("Press", 5, CatalogIds.OHP, LiftSlot.OVERHEAD, CatalogIds.BP, repeatPercentSets(3, 3, pct - 5, 180, amrapLast = i < 10), t2sets, CatalogIds.LAT, CatalogIds.FACE),
            ))
        }
        return TrainingPlanRecipe("gzcl-rippler", weeks, 0.90, sbdSlots(), ProgressionRule.None, claimedDaysPerWeek = 4, claimedLevel = "intermedio")
    }

    val rippler = Protocol(
        id = "gzcl-rippler",
        name = "GZCL The Rippler",
        emoji = "🌊",
        description = "12 semanas, 4 días: T1 sobre 2RM 85-92,5 % +2,5 por bloque; T2 sobre 5RM; sem 11-12 test.",
        author = "Cody Lefever",
        tags = listOf("powerlifting", "intermedio", "4 días", "12 semanas", "%"),
        blocks = listOf(ProtocolBlock("B1", 4, "Acumulación", 80, 92), ProtocolBlock("B2", 4, "Intensificación", 85, 95), ProtocolBlock("B3", 4, "Peak", 90, 100)),
        defaultSplit = "pl_classic_4",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        source = attributed("The Rippler", "https://gzclmethod.com/", "Cody Lefever", variant = "Rippler"),
        recipe = ripplerRecipe(),
        fidelitySpec = ProtocolFidelitySpec(12, 4, requiresPercent = true, claimedLevel = "intermedio", percentAnchors = mapOf("w1" to listOf(85.0))),
    )

    fun uhf9Recipe(): TrainingPlanRecipe {
        val weeks = (1..9).map { w ->
            val pct = 70.0 + w * 2
            val goal = if (w == 9) BlockGoal.PEAK else if (w >= 6) BlockGoal.INTENSIFICATION else BlockGoal.ACCUMULATION
            weekRecipe(w, if (w <= 4) 0 else 1, if (w <= 4) "Volumen" else "Intensidad", goal, listOf(
                DayArchetypes.plSquat(pct, t1Sets = if (w >= 6) 3 else 5, t1Reps = if (w >= 6) 3 else 6, weekday = 1),
                DayArchetypes.plBenchHeavy(pct + 2, t1Sets = if (w >= 6) 4 else 3, t1Reps = if (w >= 6) 2 else 8, weekday = 2),
                DayArchetypes.plDeadlift(pct - 4, t1Sets = 3, t1Reps = if (w >= 6) 2 else 5, weekday = 3),
                DayArchetypes.plBenchVolume((pct - 8).coerceAtLeast(60.0), weekday = 5),
                DayArchetypes.plSquat((pct - 10).coerceAtLeast(60.0), t1Sets = 3, t1Reps = 5, weekday = 6, label = "Sentadilla ligera"),
            ).map { day -> if (goal == BlockGoal.PEAK) day.dropT3(2) else day })
        }
        return TrainingPlanRecipe("gzcl-uhf-9", weeks, 0.90, sbdSlots(), ProgressionRule.None, claimedDaysPerWeek = 5, claimedLevel = "avanzado")
    }

    val uhf9 = Protocol(
        id = "gzcl-uhf-9",
        name = "GZCL UHF 9",
        emoji = "📅",
        description = "9 semanas, 5 días DUP con ondulación diaria de sentadilla, banca y peso muerto.",
        author = "Cody Lefever",
        tags = listOf("powerlifting", "avanzado", "5 días", "9 semanas", "%"),
        blocks = listOf(ProtocolBlock("Volumen", 4, "Acumulación", 70, 82), ProtocolBlock("Intensidad", 5, "Intensificación", 80, 95)),
        defaultSplit = "pl_hf_bench",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        source = attributed("UHF 9 Week", "https://gzclmethod.com/", "Cody Lefever", variant = "UHF 9"),
        recipe = uhf9Recipe(),
        fidelitySpec = ProtocolFidelitySpec(9, 5, requiresPercent = true, claimedLevel = "avanzado", percentAnchors = mapOf("w1_sq" to listOf(72.0))),
    )
}
