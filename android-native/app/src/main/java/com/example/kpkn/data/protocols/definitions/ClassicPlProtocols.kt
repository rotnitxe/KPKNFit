package com.example.kpkn.data.protocols.definitions

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
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
import com.example.kpkn.data.protocols.SlotSource
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.attributed
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.dropT3
import com.example.kpkn.data.protocols.withMinRir
import com.example.kpkn.data.protocols.asKpknSuggested
import com.example.kpkn.data.protocols.rangeRirSets
import com.example.kpkn.data.protocols.repeatPercentSets
import com.example.kpkn.data.protocols.replaceT1Work
import com.example.kpkn.data.protocols.rpeSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.topSetAndBackoff
import com.example.kpkn.data.protocols.weekRecipe

object ClassicPlProtocols {
    val korte = run {
        val exemptions = listOf(
            RecipeCompositionExemption("H5b", "*", "Korte SQ 8×5 + DL 8×5 por diseño"),
            RecipeCompositionExemption("W3", "*", "SBD las 3 sesiones"),
        )
        fun sbdDay(label: String, weekday: Int, sq: Double, bp: Double, dl: Double, heavy: LiftSlot?) = day(
            label, weekday = weekday, slots = listOf(
                slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, when {
                    heavy == LiftSlot.SQUAT -> listOf(SetRecipe(reps = 1, percent = sq, isTopSet = true))
                    heavy != null -> repeatPercentSets(3, 3, 60.0, 180)
                    else -> repeatPercentSets(8, 5, sq, 180)
                }, if (sq >= 85) 240 else 180, LiftSlot.SQUAT, isCompetitionLift = true),
                slot("bp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, when {
                    heavy == LiftSlot.BENCH -> listOf(SetRecipe(reps = 1, percent = bp, isTopSet = true))
                    heavy != null -> repeatPercentSets(5, 4, 60.0, 150)
                    else -> repeatPercentSets(6, 6, bp, 150)
                }, 150, LiftSlot.BENCH, isCompetitionLift = true),
                slot("dl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL, when {
                    heavy == LiftSlot.DEADLIFT -> listOf(SetRecipe(reps = 1, percent = dl, isTopSet = true))
                    heavy != null -> repeatPercentSets(3, 3, 60.0, 180)
                    else -> repeatPercentSets(8, 5, dl, 180)
                }, 180, LiftSlot.DEADLIFT, isCompetitionLift = true),
                kpknAssist("row", CatalogIds.PENDLAY, 3, 8, 120),
                kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
            ),
        )
        val weeks = (1..8).map { w ->
            val phase1 = w <= 4
            val pct = listOf(58.0, 60.0, 62.0, 64.0).getOrElse(w - 1) { 60.0 }
            val singles = listOf(80.0, 85.0, 90.0, 95.0)
            weekRecipe(w, if (phase1) 0 else 1, if (phase1) "Fase I" else "Fase II", if (phase1) BlockGoal.ACCUMULATION else BlockGoal.INTENSIFICATION, if (phase1) listOf(
                sbdDay("SBD Lun", 1, pct, pct, pct, null),
                sbdDay("SBD Mie", 3, pct, pct, pct, null),
                sbdDay("SBD Vie", 5, pct, pct, pct, null),
            ) else listOf(
                sbdDay("SBD Lun", 1, 60.0, 60.0, singles[w - 5], LiftSlot.DEADLIFT),
                sbdDay("SBD Mie", 3, 60.0, singles[w - 5], 60.0, LiftSlot.BENCH),
                sbdDay("SBD Vie", 5, singles[w - 5], 60.0, 60.0, LiftSlot.SQUAT),
            ))
        }
        Protocol(
            id = "korte-3x3", name = "Korte 3×3", emoji = "🇩🇪",
            description = "8 semanas, 3 días SBD: fase I 58-64 % series altas; fase II single semanal 80-95 % rotando.",
            author = "Stephan Korte", tags = listOf("powerlifting", "avanzado", "3 días", "8 semanas", "%"),
            blocks = listOf(ProtocolBlock("Fase I", 4, "Acumulación", 58, 64, 1.4), ProtocolBlock("Fase II", 4, "Intensificación", 60, 95, 0.7)),
            defaultSplit = "korte_3x3", publicationStatus = ProtocolPublicationStatus.VERIFIED,
            source = attributed("Korte 3x3", "https://www.powerliftingtowin.com/korte-3x3/", "Stephan Korte"),
            recipe = TrainingPlanRecipe("korte-3x3", weeks, 1.0, sbdSlots(), ProgressionRule.None, exemptions, claimedDaysPerWeek = 3, claimedLevel = "avanzado"),
            fidelitySpec = ProtocolFidelitySpec(8, 3, requiresPercent = true, claimedLevel = "avanzado", percentAnchors = mapOf("fase1" to listOf(58.0))),
            exemptions = exemptions,
        )
    }

    val cube = run {
        fun heavySets(w: Int) = when {
            w <= 3 -> repeatPercentSets(5, 2, 80.0, 240)
            w <= 6 -> repeatPercentSets(3, 2, 85.0, 240)
            w == 7 -> listOf(SetRecipe(reps = 1, percent = 90.0, isTopSet = true))
            w == 8 -> listOf(SetRecipe(reps = 1, percent = 92.5, isTopSet = true))
            w == 9 -> listOf(SetRecipe(reps = 1, percent = 95.0, isTopSet = true))
            else -> listOf(SetRecipe(reps = 1, percent = 100.0, isTopSet = true))
        }
        fun speedSets(w: Int) = when {
            w <= 3 -> repeatPercentSets(8, 3, 60.0, 60)
            w <= 6 -> repeatPercentSets(6, 2, 65.0, 60)
            w <= 9 -> repeatPercentSets(5, 2, 70.0, 60)
            else -> repeatPercentSets(4, 2, 50.0, 60)
        }
        fun repsSets(w: Int) = when {
            w <= 3 -> listOf(SetRecipe(reps = 8, percent = 70.0))
            w <= 6 -> listOf(SetRecipe(reps = 6, percent = 80.0))
            w <= 9 -> listOf(SetRecipe(reps = 2, percent = 85.0))
            else -> listOf(SetRecipe(reps = 1, percent = 60.0))
        }
        val weeks = (1..10).map { w ->
            val goal = if (w == 10) BlockGoal.PEAK else if (w >= 8) BlockGoal.INTENSIFICATION else BlockGoal.ACCUMULATION
            val drop = if (w == 10) 2 else if (w >= 8) 1 else 0
            weekRecipe(w, if (w <= 5) 0 else 1, if (w == 10) "Test" else "Cubo", goal, listOf(
                day("Pesado", weekday = 1, slots = listOf(
                    slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, heavySets(w), 240, LiftSlot.SQUAT, isCompetitionLift = true),
                    kpknAssist("row", CatalogIds.PENDLAY, 4, 8, 120),
                    kpknAssist("ghr", CatalogIds.GHR, 4, 8, 90),
                    kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
                    kpknAssist("pallof", CatalogIds.PALLOF, 3, 10, 60),
                )).dropT3(drop),
                day("Explosivo", weekday = 2, slots = listOf(
                    slot("speed", SlotRole.SPEED, CatalogIds.SQ_BOX, speedSets(w), 60, LiftSlot.SQUAT, technique = TechniqueModifier.SPEED, priority = SlotPriority.SPEED),
                    slot("bp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, repeatPercentSets(8, 3, if (w <= 3) 60.0 else if (w <= 6) 65.0 else 70.0, 90), 120, LiftSlot.BENCH, isCompetitionLift = true, technique = TechniqueModifier.SPEED),
                    kpknAssist("pull", CatalogIds.PULLUP, 3, 6, 120),
                    kpknAssist("tate", CatalogIds.TATE, 3, 10, 60),
                    kpknAssist("wheel", CatalogIds.WHEEL, 3, 8, 60),
                ), priority = SlotPriority.SPEED).dropT3(drop),
                day("Repeticiones", weekday = 4, slots = listOf(
                    slot("dl", SlotRole.T1_MAIN, CatalogIds.DL, repsSets(w), if ((repsSets(w).first().percent ?: 0.0) >= 85) 240 else 180, LiftSlot.DEADLIFT, isCompetitionLift = true),
                    slot("bp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP_INC, rpeSets(4, 8, 8.0), 120, LiftSlot.BENCH, supplementalOf = "dl"),
                    kpknAssist("row", CatalogIds.CSR, 4, 10, 90),
                    kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
                    kpknAssist("jm", CatalogIds.JM, 2, 10, 60),
                )).dropT3(drop),
                DayArchetypes.bbTorso().asKpknSuggested().copy(label = "Culturismo", weekday = 5).dropT3(drop),
            ))
        }
        Protocol(
            id = "cube-method", name = "Cube Method", emoji = "🧊",
            description = "10 semanas, 4 días: rotación pesado/explosivo/reps por levantamiento + día de culturismo. Semana 10 test.",
            author = "Brandon Lilly", tags = listOf("powerlifting", "avanzado", "4 días", "10 semanas", "%"),
            blocks = listOf(ProtocolBlock("Cubo 1", 5, "Acumulación", 60, 90), ProtocolBlock("Cubo 2", 5, "Intensificación", 60, 100)),
            defaultSplit = "cube_method", publicationStatus = ProtocolPublicationStatus.VERIFIED,
            source = attributed("The Cube Method", "https://www.powerliftingtowin.com/brandon-lillys-cube-method/", "Brandon Lilly"),
            recipe = TrainingPlanRecipe("cube-method", weeks, 0.95, sbdSlots(), ProgressionRule.None, claimedDaysPerWeek = 4, claimedLevel = "avanzado"),
            fidelitySpec = ProtocolFidelitySpec(10, 4, requiresPercent = true, claimedLevel = "avanzado", percentAnchors = mapOf("heavy" to listOf(80.0))),
        )
    }

    val lilliebridge = run {
        val sharedT3 = listOf(
            kpknAssist("row", CatalogIds.PENDLAY, 3, 8, 120),
            kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
            kpknAssist("wheel", CatalogIds.WHEEL, 3, 8, 60),
        )
        fun stabilize(day: com.example.kpkn.data.protocols.DayRecipe) =
            day.copy(slots = day.slots.filter { it.role != SlotRole.T3_ACCESSORY } + sharedT3)
        val weeks = (1..10).map { w ->
            val heavySq = listOf(87.0, 65.0, 90.0, 65.0, 92.0, 65.0, 95.0, 65.0, 90.0, 0.0)[w - 1]
            val bp = listOf(75.0, 70.0, 80.0, 72.0, 87.0, 74.0, 90.0, 75.0, 92.0, 0.0)[w - 1]
            val goal = if (w == 10) BlockGoal.TAPER else if (w >= 8) BlockGoal.PEAK else BlockGoal.INTENSIFICATION
            weekRecipe(w, if (w <= 6) 0 else 1, if (w == 10) "Descanso" else "Lilliebridge", goal, if (w == 10) listOf(
                stabilize(DayArchetypes.plBenchVolume(60.0, weekday = 2)).dropT3(2),
                stabilize(DayArchetypes.plSquat(60.0, t1Sets = 2, t1Reps = 3, weekday = 4)).dropT3(2),
                stabilize(DayArchetypes.plDeadlift(55.0, t1Sets = 2, t1Reps = 2, weekday = 6)).dropT3(2),
            ) else listOf(
                stabilize(if (w % 2 == 1) DayArchetypes.plSquat(heavySq, t1Sets = 2, t1Reps = 1, t1Top = true, weekday = 1)
                else DayArchetypes.plDeadlift(70.0, t1Sets = 3, t1Reps = 3, weekday = 1)).dropT3(if (w >= 8) 2 else 0),
                stabilize(DayArchetypes.plBenchHeavy(bp, t1Sets = if (w % 2 == 1) 3 else 4, t1Reps = if (w % 2 == 1) 1 else 5, t1Amrap = w % 2 == 0, weekday = 3)).dropT3(if (w >= 8) 2 else 0),
                stabilize(DayArchetypes.plBenchVolume(bp - 8, weekday = 5)).dropT3(if (w >= 8) 2 else 0),
            ))
        }
        Protocol(
            id = "lilliebridge", name = "Lilliebridge Method", emoji = "🌉",
            description = "10 semanas, 3 días: SQ/DL mismo día alternando pesado/ligero; banca singles y AMRAP alternos.",
            author = "Matt Lilliebridge", tags = listOf("powerlifting", "avanzado", "3 días", "10 semanas", "%", "AMRAP"),
            blocks = listOf(ProtocolBlock("Desarrollo", 6, "Intensificación", 65, 92), ProtocolBlock("Pico", 4, "Peak", 70, 96)),
            defaultSplit = "pl_sbd_x3", publicationStatus = ProtocolPublicationStatus.VERIFIED,
            source = attributed("Lilliebridge Method", "https://www.powerliftingtowin.com/the-lilliebridge-method/", "Matt Lilliebridge"),
            recipe = TrainingPlanRecipe("lilliebridge", weeks, 1.0, sbdSlots(), ProgressionRule.TopSetPr, claimedDaysPerWeek = 3, claimedLevel = "avanzado"),
            fidelitySpec = ProtocolFidelitySpec(10, 3, requiresPercent = true, claimedLevel = "avanzado", percentAnchors = mapOf("w1_sq" to listOf(87.0))),
        )
    }

    val westside = run {
        val exemptions = listOf(
            RecipeCompositionExemption("W5", "Conjugate", "Rotación de Max Effort cada 1-3 semanas por definición"),
        )
        val meLower = listOf(CatalogIds.SQ_BOX, CatalogIds.GM, CatalogIds.DL_DEF, CatalogIds.SQ_SSB)
        val meUpper = listOf(CatalogIds.BP_FLOOR, CatalogIds.BP_CHAINS, CatalogIds.BP_INC, CatalogIds.JM)
        val weeks = (1..12).map { w ->
            val wave = (w - 1) % 3
            val deSq = listOf(12 to 50.0, 10 to 55.0, 8 to 60.0)[wave]
            val deBp = listOf(9 to 45.0, 9 to 50.0, 9 to 55.0)[wave]
            weekRecipe(w, (w - 1) / 4, "Conjugate", BlockGoal.INTENSIFICATION, listOf(
                day("ME Lower", weekday = 1, slots = listOf(
                    slot("me", SlotRole.T1_MAIN, meLower[(w - 1) % 4], listOf(SetRecipe(reps = 2, percent = 90.0, isTopSet = true, loadBasis = com.example.kpkn.data.protocols.LoadBasis.REP_MAX)), 240, LiftSlot.SQUAT),
                    kpknAssist("rev", CatalogIds.REV_HYPER, 3, 10, 90),
                    kpknAssist("ghr", CatalogIds.GHR, 4, 8, 90),
                    kpknAssist("thru", CatalogIds.PULL_THRU, 3, 12, 60),
                    kpknAssist("wheel", CatalogIds.WHEEL, 3, 8, 60),
                )),
                day("ME Upper", weekday = 2, slots = listOf(
                    slot("me", SlotRole.T1_MAIN, meUpper[(w - 1) % 4], listOf(SetRecipe(reps = 2, percent = 90.0, isTopSet = true, loadBasis = com.example.kpkn.data.protocols.LoadBasis.REP_MAX)), 240, LiftSlot.BENCH),
                    kpknAssist("row", CatalogIds.PENDLAY, 4, 6, 120),
                    kpknAssist("pull", CatalogIds.PULLUP, 3, 6, 120),
                    kpknAssist("tate", if (meUpper[(w - 1) % 4] == CatalogIds.JM) CatalogIds.FLY else CatalogIds.TATE, 4, 8, 60),
                    kpknAssist("face", CatalogIds.FACE, 4, 15, 60),
                )),
                day("DE Lower", weekday = 4, priority = SlotPriority.SPEED, slots = listOf(
                    slot("de", SlotRole.SPEED, CatalogIds.SQ_BOX, repeatPercentSets(deSq.first, 2, deSq.second, 60), 60, LiftSlot.SQUAT, technique = TechniqueModifier.SPEED, priority = SlotPriority.SPEED),
                    slot("sdl", SlotRole.SPEED, CatalogIds.DL, repeatPercentSets(listOf(6, 5, 4)[wave], 2, 60.0, 90), 90, LiftSlot.DEADLIFT, technique = TechniqueModifier.SPEED, supplementalOf = "de"),
                    kpknAssist("pull", CatalogIds.PULLUP, 3, 6, 120),
                    kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
                    kpknAssist("abs", CatalogIds.WHEEL, 3, 8, 60),
                )),
                day("DE Upper", weekday = 5, priority = SlotPriority.SPEED, slots = listOf(
                    slot("de", SlotRole.SPEED, CatalogIds.BP, repeatPercentSets(deBp.first, 3, deBp.second, 60), 60, LiftSlot.BENCH, technique = TechniqueModifier.SPEED, priority = SlotPriority.SPEED),
                    kpknAssist("row", CatalogIds.CSR, 4, 10, 90),
                    kpknAssist("jm", CatalogIds.JM, 4, 8, 90),
                    kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
                    kpknAssist("lat", CatalogIds.LATERAL, 3, 15, 45),
                )),
            ))
        }
        Protocol(
            id = "westside-conjugate", name = "Westside Conjugate", emoji = "🐺",
            description = "12 semanas, 4 días: ME lower/upper rotando variante; DE olas 12×2/10×2/8×2 y 9×3. Bandas/cadenas no catalogadas: DE usa SPEED % de barra.",
            author = "Louie Simmons", tags = listOf("powerlifting", "avanzado", "4 días", "12 semanas", "%", "RPE"),
            blocks = listOf(ProtocolBlock("Ola 1", 4, "Intensificación", 45, 95), ProtocolBlock("Ola 2", 4, "Intensificación", 45, 95), ProtocolBlock("Ola 3", 4, "Intensificación", 45, 95)),
            defaultSplit = "westside_conjugate", publicationStatus = ProtocolPublicationStatus.VERIFIED,
            source = attributed("Westside Barbell conjugate method", "https://www.westside-barbell.com/blogs/the-blog/the-conjugate-method", "Louie Simmons"),
            recipe = TrainingPlanRecipe("westside-conjugate", weeks, 0.90, sbdSlots(), ProgressionRule.RepMaxAutoregulated, exemptions, claimedDaysPerWeek = 4, claimedLevel = "avanzado"),
            fidelitySpec = ProtocolFidelitySpec(12, 4, requiresPercent = true, claimedLevel = "avanzado", percentAnchors = mapOf("de" to listOf(50.0))),
            exemptions = exemptions,
        )
    }

    val calgary = run {
        data class LiftScheme(val sets: Int, val reps: Int, val pct: Double, val backSets: Int = 0, val backReps: Int = 0, val backPct: Double = 0.0)
        fun work(scheme: LiftScheme, rpe: Double?): List<SetRecipe> {
            val sets = if (scheme.backSets > 0) {
                topSetAndBackoff(scheme.sets, scheme.reps, scheme.pct, scheme.backSets, scheme.backReps, scheme.backPct, rpe)
            } else {
                repeatPercentSets(scheme.sets, scheme.reps, scheme.pct, 180)
            }
            return if (rpe == null) sets else sets.map { it.copy(rpe = rpe) }
        }
        fun withRdl(day: com.example.kpkn.data.protocols.DayRecipe): com.example.kpkn.data.protocols.DayRecipe {
            val t1 = day.slots.filter { it.role == SlotRole.T1_MAIN }
            val demoted = day.slots.filter { it.role == SlotRole.T2_SUPPLEMENTAL }.map { it.copy(role = SlotRole.T3_ACCESSORY) }
            val t3 = day.slots.filter { it.role == SlotRole.T3_ACCESSORY }
            val rdl = slot(
                "rdl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.RDL, rpeSets(3, 6, 7.5), 150,
                LiftSlot.DEADLIFT, source = SlotSource.KPKN_DEFAULT,
            )
            return day.copy(slots = t1 + rdl + demoted + t3)
        }
        fun withCompetitionBenchCoverage(day: com.example.kpkn.data.protocols.DayRecipe): com.example.kpkn.data.protocols.DayRecipe {
            if (day.slots.any { it.lift.configurationId == CatalogIds.BP }) return day
            val t1 = day.slots.filter { it.role == SlotRole.T1_MAIN }
            val t2 = day.slots.filter { it.role == SlotRole.T2_SUPPLEMENTAL }
            val rest = day.slots.filter { it.role != SlotRole.T1_MAIN && it.role != SlotRole.T2_SUPPLEMENTAL }
            val coverage = slot(
                "bp-cov", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, rpeSets(2, 8, 7.0), 150,
                LiftSlot.BENCH, source = SlotSource.KPKN_DEFAULT,
            )
            return day.copy(slots = t1 + t2 + listOf(coverage) + rest)
        }
        fun withSquatTech(day: com.example.kpkn.data.protocols.DayRecipe, pct: Double): com.example.kpkn.data.protocols.DayRecipe {
            val t1 = day.slots.filter { it.role == SlotRole.T1_MAIN }
            val rest = day.slots.filter { it.role != SlotRole.T1_MAIN }
            val tech = slot(
                "sq-tech", SlotRole.T2_SUPPLEMENTAL, CatalogIds.SQ_HIGH,
                repeatPercentSets(3, 5, pct, 150),
                150, LiftSlot.SQUAT, technique = TechniqueModifier.PAUSE_2S,
                source = SlotSource.KPKN_DEFAULT,
            )
            return day.copy(slots = t1 + tech + rest)
        }
        val weeks = (1..16).map { w ->
            val goal = when {
                w <= 4 -> BlockGoal.ACCUMULATION
                w <= 11 -> BlockGoal.INTENSIFICATION
                else -> BlockGoal.PEAK
            }
            val rpe = when {
                w in 12..15 -> 8.0
                w == 16 -> 9.0
                else -> null
            }
            val sq = when (w) {
                1 -> LiftScheme(4, 7, 64.0)
                2 -> LiftScheme(4, 7, 66.0)
                3 -> LiftScheme(4, 6, 68.0)
                4 -> LiftScheme(5, 5, 71.0)
                5 -> LiftScheme(4, 3, 76.0, 3, 5, 66.0)
                6 -> LiftScheme(4, 3, 78.0, 3, 5, 68.0)
                7 -> LiftScheme(4, 3, 80.0, 3, 5, 70.0)
                8 -> LiftScheme(4, 3, 82.0, 3, 4, 72.0)
                9 -> LiftScheme(3, 3, 78.0, 2, 5, 68.0)
                10 -> LiftScheme(3, 3, 80.0, 2, 5, 70.0)
                11 -> LiftScheme(3, 3, 81.0, 2, 4, 71.0)
                12 -> LiftScheme(1, 3, 88.0, 3, 3, 78.0)
                13 -> LiftScheme(1, 2, 90.0, 3, 3, 80.0)
                14 -> LiftScheme(1, 1, 92.0, 3, 2, 82.0)
                15 -> LiftScheme(2, 1, 90.0, 2, 2, 80.0)
                else -> LiftScheme(1, 1, 95.0)
            }
            val bp = when {
                w <= 4 -> LiftScheme(sq.sets, (sq.reps - 1).coerceAtLeast(5), (sq.pct - 2).coerceAtLeast(60.0))
                w in 5..8 -> LiftScheme(4, 3, sq.pct - 2, 3, 6, sq.backPct - 2)
                w in 9..11 -> LiftScheme(3, 3, sq.pct - 1, 2, 5, sq.backPct - 1)
                w == 16 -> LiftScheme(1, 1, 93.0)
                else -> LiftScheme(sq.sets, sq.reps.coerceAtLeast(1), sq.pct - 2, sq.backSets, sq.backReps, (sq.backPct - 2).coerceAtLeast(60.0))
            }
            val dl = when {
                w <= 4 -> LiftScheme(3, 5, (sq.pct - 4).coerceAtLeast(60.0))
                w in 5..11 -> LiftScheme(3, 2, (sq.pct - 4).coerceAtLeast(70.0), 3, 4, (sq.backPct - 4).coerceAtLeast(60.0))
                w == 16 -> LiftScheme(1, 1, 90.0)
                else -> LiftScheme(2, 1, (sq.pct - 4).coerceAtLeast(80.0), 2, 3, (sq.backPct - 4).coerceAtLeast(70.0))
            }
            val drop = if (goal == BlockGoal.PEAK) 2 else if (goal == BlockGoal.INTENSIFICATION) 1 else 0
            val volPct = (sq.pct - 8).coerceAtLeast(55.0)
            weekRecipe(w, when { w <= 4 -> 0; w <= 8 -> 1; w <= 11 -> 2; else -> 3 }, "F${when { w <= 4 -> 1; w <= 8 -> 2; w <= 11 -> 3; else -> 4 }}", goal, listOf(
                DayArchetypes.plSquat(sq.pct, t1Sets = sq.sets, t1Reps = sq.reps, weekday = 1)
                    .replaceT1Work(work(sq, rpe)).dropT3(drop),
                withRdl(
                    DayArchetypes.plBenchHeavy(bp.pct, t1Sets = bp.sets, t1Reps = bp.reps.coerceAtLeast(1), weekday = 2)
                        .replaceT1Work(work(bp, rpe)),
                ).dropT3(drop),
                withCompetitionBenchCoverage(
                    DayArchetypes.plDeadlift(dl.pct, t1Sets = dl.sets, t1Reps = dl.reps.coerceAtLeast(1), weekday = 4)
                        .replaceT1Work(work(dl, rpe)),
                ).dropT3(drop),
                withSquatTech(DayArchetypes.plBenchVolume(volPct, weekday = 5), (sq.pct - 12).coerceAtLeast(55.0)).dropT3(drop),
            ))
        }
        Protocol(
            id = "calgary-16", name = "Calgary Barbell 16", emoji = "🍁",
            description = "16 semanas, 4 días: F1 4×7@64 con frecuencia SQ×3 BP×4 DL×3; F2 top 4×3 + back-off; F4 top @RPE 8 y test.",
            author = "Bryce Krawczyk", tags = listOf("powerlifting", "intermedio", "4 días", "16 semanas", "%", "RPE"),
            blocks = listOf(ProtocolBlock("F1", 4, "Acumulación", 64, 71), ProtocolBlock("F2", 4, "Intensificación", 76, 82), ProtocolBlock("F3", 3, "Intensificación", 78, 81), ProtocolBlock("F4", 5, "Peak", 80, 100)),
            defaultSplit = "pl_classic_4", publicationStatus = ProtocolPublicationStatus.VERIFIED,
            source = attributed("Calgary Barbell 16 Week Program", "https://calgarybarbell.com/", "Bryce Krawczyk"),
            recipe = TrainingPlanRecipe("calgary-16", weeks, 0.90, sbdSlots(), ProgressionRule.None, claimedDaysPerWeek = 4, claimedLevel = "intermedio", autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.RPE_CAP))),
            fidelitySpec = ProtocolFidelitySpec(
                16, 4, requiresPercent = true, requiresRpe = true, claimedLevel = "intermedio",
                percentAnchors = mapOf("w1_sq" to listOf(64.0), "w5_sq" to listOf(76.0, 66.0), "w16" to listOf(95.0)),
            ),
        )
    }

    val tsa = run {
        data class Dup(val pct: Double, val sets: Int, val reps: Int)
        val weeks = (1..9).map { w ->
            val goal = when {
                w <= 4 -> BlockGoal.ACCUMULATION
                w == 5 -> BlockGoal.DELOAD
                w == 9 -> BlockGoal.PEAK
                else -> BlockGoal.INTENSIFICATION
            }
            val schemes = when {
                w <= 4 -> listOf(
                    Dup(70.0 + w, 4, 6),
                    Dup(68.0 + w, 5, 5),
                    Dup(67.0 + w, 3, 5),
                    Dup(62.0 + w / 2.0, 8, 3),
                )
                w == 5 -> listOf(Dup(60.0, 3, 5), Dup(55.0, 3, 6), Dup(55.0, 2, 5), Dup(50.0, 3, 8))
                w <= 8 -> listOf(
                    Dup(85.0 + (w - 6) * 3, 2, 1),
                    Dup(80.0 + (w - 6) * 2, 3, 3),
                    Dup(82.0 + (w - 6) * 3, 2, 1),
                    Dup(70.0, 4, 4),
                )
                else -> listOf(Dup(95.0, 1, 1), Dup(95.0, 1, 1), Dup(92.0, 1, 1), Dup(60.0, 3, 5))
            }
            val squat = schemes[0]
            val bench = schemes[1]
            val deadlift = schemes[2]
            val volume = schemes[3]
            val drop = if (goal == BlockGoal.PEAK || goal == BlockGoal.DELOAD) 2 else if (goal == BlockGoal.INTENSIFICATION) 1 else 0
            val rpe = when {
                w == 5 -> 6.0
                w in 6..8 -> 8.0
                w == 9 -> 9.0
                else -> 7.0
            }
            fun applyRpe(day: com.example.kpkn.data.protocols.DayRecipe) = day.copy(
                slots = day.slots.map { slot ->
                    if (slot.role != SlotRole.T1_MAIN) slot
                    else slot.copy(sets = slot.sets.map { set -> if (set.isWarmup) set else set.copy(rpe = rpe) })
                },
            )
            weekRecipe(
                w, when { w <= 4 -> 0; w == 5 -> 1; else -> 2 }, "TSA", goal,
                listOf(
                    applyRpe(DayArchetypes.plSquat(squat.pct, t1Sets = squat.sets, t1Reps = squat.reps, weekday = 1)).dropT3(drop),
                    DayArchetypes.plBenchHeavy(bench.pct, t1Sets = bench.sets, t1Reps = bench.reps.coerceAtLeast(1), weekday = 2).dropT3(drop),
                    DayArchetypes.plDeadlift(deadlift.pct, t1Sets = deadlift.sets, t1Reps = deadlift.reps.coerceAtLeast(1), weekday = 4).dropT3(drop),
                    DayArchetypes.plBenchVolume(volume.pct, weekday = 5).let { day ->
                        day.replaceT1Work(repeatPercentSets(volume.sets, volume.reps, volume.pct, 180)).dropT3(drop)
                    },
                ).map { day -> if (goal == BlockGoal.DELOAD) day.withMinRir(4) else day },
                kind = if (goal == BlockGoal.DELOAD) com.example.kpkn.data.models.WeekExecutionKind.DELOAD else com.example.kpkn.data.models.WeekExecutionKind.TRAINING,
            )
        }
        Protocol(
            id = "tsa-9", name = "TSA 9-Week Intermediate v2", emoji = "🍁",
            description = "9 semanas, 4 días DUP: volumen 1-4 con esquemas distintos por día, descarga 5, intensidad 6-8, test 9.",
            author = "The Strength Athlete", tags = listOf("powerlifting", "intermedio", "4 días", "9 semanas", "%", "RPE"),
            blocks = listOf(ProtocolBlock("Volumen", 4, "Acumulación", 70, 78), ProtocolBlock("Descarga", 1, "Descarga", 50, 65), ProtocolBlock("Intensidad", 4, "Intensificación", 80, 95)),
            defaultSplit = "pl_classic_4", publicationStatus = ProtocolPublicationStatus.VERIFIED,
            source = attributed("TSA 9 Week Intermediate Program v2", "https://www.thestrengthathlete.com/", "The Strength Athlete"),
            recipe = TrainingPlanRecipe("tsa-9", weeks, 0.90, sbdSlots(), ProgressionRule.None, claimedDaysPerWeek = 4, claimedLevel = "intermedio"),
            fidelitySpec = ProtocolFidelitySpec(
                9, 4, requiresPercent = true, claimedLevel = "intermedio",
                percentAnchors = mapOf("w1_sq" to listOf(71.0), "w1_bp" to listOf(69.0), "w5_deload" to listOf(60.0)),
            ),
        )
    }
}
