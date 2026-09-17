package com.example.kpkn.data.programs

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.replaceT1Work
import com.example.kpkn.data.protocols.topSetAndBackoff
import com.example.kpkn.data.protocols.weekRecipe

object KpknAdvancedProgramRecipes {
    private val sbd = mapOf(
        LiftSlot.SQUAT to CatalogIds.SQ_LOW,
        LiftSlot.BENCH to CatalogIds.BP,
        LiftSlot.DEADLIFT to CatalogIds.DL,
    )

    fun plBeginner12(): TrainingPlanRecipe {
        val weeks = (1..12).map { w ->
            val (block, name, goal, squat, bench, dl) = when {
                w <= 4 -> Quad(0, "Base", BlockGoal.ACCUMULATION, 70.0 + w, 68.0 + w, 68.0 + w / 2)
                w <= 8 -> Quad(1, "Intensificación", BlockGoal.INTENSIFICATION, 78.0 + (w - 4), 75.0 + (w - 4), 75.0 + (w - 4))
                else -> Quad(2, "Peak", BlockGoal.PEAK, 88.0 + (w - 9), 85.0 + (w - 9), 88.0 + (w - 9))
            }
            val t1Sets = if (goal == BlockGoal.PEAK) 3 else 4
            val t1Reps = if (goal == BlockGoal.PEAK) 2 else if (goal == BlockGoal.INTENSIFICATION) 4 else 5
            weekRecipe(w, block, name, goal, listOf(
                DayArchetypes.plSquat(squat, t1Sets = t1Sets, t1Reps = t1Reps, weekday = 1, t1Amrap = w == 1 || w == 5 || w == 9),
                DayArchetypes.plBenchHeavy(bench, t1Sets = t1Sets, t1Reps = t1Reps, weekday = 3),
                DayArchetypes.plDeadlift(dl, t1Sets = t1Sets.coerceAtMost(3), t1Reps = t1Reps.coerceAtLeast(2), weekday = 5),
            ))
        }
        return TrainingPlanRecipe("power-12-3", weeks, 0.90, sbd, claimedDaysPerWeek = 3, claimedLevel = "principiante")
    }

    fun plIntermediate16(): TrainingPlanRecipe {
        val weeks = (1..16).map { w ->
            val spec = when {
                w <= 5 -> Quad(0, "Hipertrofia específica", BlockGoal.ACCUMULATION, 70.0 + (w - 1), 68.0 + (w - 1), 66.0 + (w - 1) / 2)
                w <= 10 -> Quad(1, "Fuerza", BlockGoal.INTENSIFICATION, 78.0 + (w - 6), 76.0 + (w - 6), 75.0 + (w - 6))
                w <= 14 -> Quad(2, "Pico", BlockGoal.PEAK, 88.0 + (w - 11) * 2, 86.0 + (w - 11) * 2, 88.0 + (w - 11))
                else -> Quad(3, "Taper/Test", BlockGoal.TAPER, if (w == 15) 91.0 else 90.0, if (w == 15) 90.0 else 90.0, if (w == 15) 90.0 else 90.0)
            }
            val (sqId, sqTech, bpId, bpTech, dlId, dlTech) = when (spec.block) {
                0 -> Variant(CatalogIds.SQ_HIGH, null, CatalogIds.BP_SPOTO, null, CatalogIds.DL_DEF, TechniqueModifier.DEFICIT)
                1 -> Variant(CatalogIds.SQ_BOX, TechniqueModifier.BOX, CatalogIds.BP_INC, null, CatalogIds.RDL, null)
                else -> Variant(CatalogIds.SQ_LOW, null, CatalogIds.BP, null, CatalogIds.DL, null)
            }
            val drop = when (spec.goal) {
                BlockGoal.INTENSIFICATION -> 1
                BlockGoal.PEAK, BlockGoal.TAPER -> 2
                else -> 0
            }
            val amrap = w == 1 || w == 6 || w == 11
            val days = when {
                w == 15 -> listOf(
                    openerDay(DayArchetypes.plSquat(91.0, weekday = 1, label = "Sentadilla pesada"), 91.0),
                    openerDay(DayArchetypes.plBenchHeavy(90.0, weekday = 2, label = "Banca pesada"), 90.0),
                    openerDay(DayArchetypes.plDeadlift(90.0, weekday = 4), 90.0),
                    openerDay(DayArchetypes.plBenchVolume(70.0, weekday = 5), 80.0),
                )
                w == 16 -> listOf(
                    testDay(DayArchetypes.plSquat(90.0, weekday = 1), listOf(90.0, 95.0, 100.0)),
                    testDay(DayArchetypes.plBenchHeavy(90.0, weekday = 2), listOf(90.0, 95.0, 100.0)),
                    testDay(DayArchetypes.plDeadlift(90.0, weekday = 4), listOf(90.0, 95.0, 100.0)),
                    testDay(DayArchetypes.plBenchVolume(70.0, weekday = 5), listOf(90.0, 95.0)),
                )
                spec.goal == BlockGoal.PEAK -> listOf(
                    peakDay(DayArchetypes.plSquat(spec.squat, weekday = 1, t1Amrap = amrap, t1ConfigurationId = sqId, t1Technique = sqTech), spec.squat, (spec.squat - 8).coerceAtLeast(80.0), if (w >= 13) 1 else 2),
                    peakDay(DayArchetypes.plBenchHeavy(spec.bench, weekday = 2, t1ConfigurationId = bpId, t1Technique = bpTech), spec.bench, (spec.bench - 8).coerceAtLeast(80.0), if (w >= 13) 1 else 2),
                    peakDay(DayArchetypes.plDeadlift(spec.dl, weekday = 4, t1ConfigurationId = dlId, t1Technique = dlTech), spec.dl, (spec.dl - 8).coerceAtLeast(80.0), 1),
                    DayArchetypes.plBenchVolume((spec.bench - 8).coerceAtLeast(58.0), weekday = 5).dropT3(drop),
                )
                else -> listOf(
                    DayArchetypes.plSquat(spec.squat, t1Sets = 4, t1Reps = if (spec.goal == BlockGoal.ACCUMULATION) 6 else 4, weekday = 1, t1Amrap = amrap, t1ConfigurationId = sqId, t1Technique = sqTech).dropT3(drop),
                    DayArchetypes.plBenchHeavy(spec.bench, t1Sets = 4, t1Reps = if (spec.goal == BlockGoal.ACCUMULATION) 6 else 4, weekday = 2, t1ConfigurationId = bpId, t1Technique = bpTech).dropT3(drop),
                    DayArchetypes.plDeadlift(spec.dl, t1Sets = 3, t1Reps = if (spec.goal == BlockGoal.ACCUMULATION) 5 else 3, weekday = 4, t1ConfigurationId = dlId, t1Technique = dlTech).dropT3(drop),
                    DayArchetypes.plBenchVolume((spec.bench - 8).coerceAtLeast(58.0), weekday = 5).dropT3(drop),
                )
            }
            weekRecipe(w, spec.block, spec.name, spec.goal, days)
        }
        return TrainingPlanRecipe("power-16-4", weeks, 0.90, sbd, claimedDaysPerWeek = 4, claimedLevel = "intermedio")
    }

    fun plAdvanced20(): TrainingPlanRecipe {
        val weeks = (1..20).map { w ->
            val spec = when {
                w <= 6 -> Quad(0, "Acumulación", BlockGoal.ACCUMULATION, 68.0 + w, 66.0 + w, 66.0)
                w <= 11 -> Quad(1, "Transmutación", BlockGoal.INTENSIFICATION, 78.0 + (w - 7), 76.0 + (w - 7), 74.0 + (w - 7))
                w <= 15 -> Quad(2, "Realización", BlockGoal.SPECIFICITY, 84.0 + (w - 12), 82.0 + (w - 12), 82.0)
                w <= 18 -> Quad(3, "Pico", BlockGoal.PEAK, 90.0 + (w - 16), 88.0 + (w - 16), 90.0)
                else -> Quad(4, "Taper", BlockGoal.TAPER, if (w == 19) 91.0 else 90.0, if (w == 19) 90.0 else 88.0, if (w == 19) 90.0 else 88.0)
            }
            val (sqId, sqTech, bpId, bpTech, dlId, dlTech) = when (spec.block) {
                0 -> Variant(CatalogIds.SQ_HIGH, null, CatalogIds.BP_SPOTO, null, CatalogIds.DL_DEF, TechniqueModifier.DEFICIT)
                1 -> Variant(CatalogIds.SQ_BOX, TechniqueModifier.BOX, CatalogIds.BP_INC, null, CatalogIds.RDL, null)
                2 -> Variant(CatalogIds.SQ_LOW, TechniqueModifier.PAUSE_2S, CatalogIds.BP_PAUSE, null, CatalogIds.DL, TechniqueModifier.DEFICIT)
                else -> Variant(CatalogIds.SQ_LOW, null, CatalogIds.BP, null, CatalogIds.DL, null)
            }
            val drop = when (spec.goal) {
                BlockGoal.INTENSIFICATION, BlockGoal.SPECIFICITY -> 1
                BlockGoal.PEAK, BlockGoal.TAPER -> 2
                else -> 0
            }
            val amrap = w == 1 || w == 7 || w == 12 || w == 16
            val techDay = when (spec.block) {
                0 -> DayArchetypes.plSquat((spec.squat - 12).coerceAtLeast(58.0), t1Sets = 3, t1Reps = 5, weekday = 6, label = "Sentadilla técnica", t1ConfigurationId = CatalogIds.SQ_SSB)
                1 -> DayArchetypes.plSquat((spec.squat - 12).coerceAtLeast(58.0), t1Sets = 3, t1Reps = 4, weekday = 6, label = "Sentadilla técnica", t1ConfigurationId = CatalogIds.SQ_PIN, t1Technique = TechniqueModifier.PIN)
                2 -> DayArchetypes.plBenchHeavy((spec.bench - 10).coerceAtLeast(58.0), t1Sets = 3, t1Reps = 3, weekday = 6, label = "Press técnico", t1ConfigurationId = CatalogIds.BP_FLOOR, t2ConfigurationId = CatalogIds.BP_CHAINS, t1Technique = TechniqueModifier.CHAINS_BANDS)
                else -> DayArchetypes.plSquat((spec.squat - 12).coerceAtLeast(58.0), t1Sets = 3, t1Reps = 3, weekday = 6, label = "Sentadilla técnica")
            }
            val days = when {
                w == 19 -> listOf(
                    openerDay(DayArchetypes.plSquat(91.0, weekday = 1, label = "Sentadilla pesada"), 91.0),
                    openerDay(DayArchetypes.plBenchHeavy(90.0, weekday = 2, label = "Banca pesada"), 90.0),
                    openerDay(DayArchetypes.plDeadlift(90.0, weekday = 4), 90.0),
                    openerDay(DayArchetypes.plBenchVolume(70.0, weekday = 5), 80.0),
                    techDay.dropT3(2),
                )
                w == 20 -> listOf(
                    testDay(DayArchetypes.plSquat(90.0, weekday = 1), listOf(90.0, 95.0, 100.0)),
                    testDay(DayArchetypes.plBenchHeavy(90.0, weekday = 2), listOf(90.0, 95.0, 100.0)),
                    testDay(DayArchetypes.plDeadlift(90.0, weekday = 4), listOf(90.0, 95.0, 100.0)),
                    testDay(DayArchetypes.plBenchVolume(70.0, weekday = 5), listOf(90.0, 95.0)),
                    testDay(DayArchetypes.plSquat(80.0, weekday = 6, label = "Sentadilla técnica"), listOf(90.0)),
                )
                spec.goal == BlockGoal.PEAK -> listOf(
                    peakDay(DayArchetypes.plSquat(spec.squat, weekday = 1, t1Amrap = amrap), spec.squat, (spec.squat - 8).coerceAtLeast(80.0), 2),
                    peakDay(DayArchetypes.plBenchHeavy(spec.bench, weekday = 2), spec.bench, (spec.bench - 8).coerceAtLeast(80.0), 2),
                    peakDay(DayArchetypes.plDeadlift(spec.dl, weekday = 4), spec.dl, (spec.dl - 8).coerceAtLeast(80.0), 1),
                    DayArchetypes.plBenchVolume((spec.bench - 8).coerceAtLeast(58.0), weekday = 5).dropT3(drop),
                    techDay.dropT3(drop),
                )
                else -> listOf(
                    DayArchetypes.plSquat(spec.squat, t1Sets = if (spec.goal == BlockGoal.SPECIFICITY) 3 else 4, t1Reps = if (spec.goal == BlockGoal.ACCUMULATION) 5 else 4, weekday = 1, t1Amrap = amrap, t1ConfigurationId = sqId, t1Technique = sqTech).dropT3(drop),
                    DayArchetypes.plBenchHeavy(spec.bench, t1Sets = if (spec.goal == BlockGoal.SPECIFICITY) 3 else 4, t1Reps = if (spec.goal == BlockGoal.ACCUMULATION) 5 else 4, weekday = 2, t1ConfigurationId = bpId, t1Technique = bpTech, t2ConfigurationId = if (spec.block == 2) CatalogIds.BP_CHAINS else CatalogIds.BP_INC).dropT3(drop),
                    DayArchetypes.plDeadlift(spec.dl, t1Sets = 3, t1Reps = if (spec.goal == BlockGoal.ACCUMULATION) 5 else 3, weekday = 4, t1ConfigurationId = dlId, t1Technique = dlTech).dropT3(drop),
                    DayArchetypes.plBenchVolume((spec.bench - 8).coerceAtLeast(58.0), weekday = 5).dropT3(drop),
                    techDay.dropT3(drop),
                )
            }
            weekRecipe(w, spec.block, spec.name, spec.goal, days)
        }
        return TrainingPlanRecipe("power-20-5", weeks, 0.90, sbd, claimedDaysPerWeek = 5, claimedLevel = "avanzado")
    }

    fun powerbuilding16(): TrainingPlanRecipe {
        val weeks = (1..16).map { w ->
            val spec = when {
                w <= 4 -> Quad(0, "Acumulación", BlockGoal.ACCUMULATION, 70.0 + w, 68.0 + w, 68.0)
                w <= 8 -> Quad(1, "Fuerza", BlockGoal.INTENSIFICATION, 78.0 + (w - 4), 76.0 + (w - 4), 74.0)
                w <= 12 -> Quad(2, "Hipertrofia dirigida", BlockGoal.SPECIFICITY, 75.0, 72.0, 70.0)
                else -> Quad(3, "Realización", BlockGoal.REALIZATION, 88.0 + (w - 13), 85.0 + (w - 13), 88.0)
            }
            val t1Sets = if (spec.goal == BlockGoal.REALIZATION) 3 else 4
            val t1Reps = if (spec.goal == BlockGoal.REALIZATION) 2 else 5
            val drop = if (spec.goal == BlockGoal.REALIZATION) 2 else if (spec.goal == BlockGoal.INTENSIFICATION) 1 else 0
            weekRecipe(w, spec.block, spec.name, spec.goal, listOf(
                DayArchetypes.plSquat(spec.squat, t1Sets = t1Sets, t1Reps = t1Reps, weekday = 1).dropT3(drop),
                DayArchetypes.bbTorso(if (w <= 8) 2 else 1).copy(weekday = 2, label = "Hipertrofia torso").dropT3(drop),
                DayArchetypes.plDeadlift(spec.dl, weekday = 4).dropT3(drop),
                DayArchetypes.plBenchHeavy(spec.bench, weekday = 5).dropT3(drop),
            ))
        }
        return TrainingPlanRecipe("powerbuild-16-4", weeks, 0.90, sbd, claimedDaysPerWeek = 4, claimedLevel = "avanzado")
    }

    fun hypertrophyUl12(): TrainingPlanRecipe {
        val weeks = (1..12).map { w ->
            val mapped = when {
                w <= 5 -> Triple(0, "Volumen", BlockGoal.ACCUMULATION)
                w == 6 -> Triple(1, "Descarga", BlockGoal.DELOAD)
                w <= 11 -> Triple(2, "Intensificación", BlockGoal.INTENSIFICATION)
                else -> Triple(3, "Descarga final", BlockGoal.DELOAD)
            }
            val rir = when {
                w <= 5 -> (3 - (w - 1) / 2).coerceAtLeast(1)
                w == 6 || w == 12 -> 4
                else -> 1
            }
            val days = listOf(
                DayArchetypes.bbTorso(rir).copy(weekday = 1, label = "Torso A"),
                DayArchetypes.bbLegs(rir).copy(weekday = 2, label = "Pierna A"),
                DayArchetypes.bbPush(rir).copy(weekday = 4, label = "Torso B"),
                DayArchetypes.bbLegs((rir - 1).coerceAtLeast(0), hipDominant = true, label = "Pierna B", weekday = 5),
            ).map { day -> if (mapped.third == BlockGoal.DELOAD) day.deload() else day }
            weekRecipe(
                w, mapped.first, mapped.second, mapped.third, days,
                kind = if (mapped.third == BlockGoal.DELOAD) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING,
            )
        }
        return TrainingPlanRecipe("body-12-3", weeks, claimedDaysPerWeek = 4, claimedLevel = "intermedio")
    }

    fun ppl16(): TrainingPlanRecipe {
        val weeks = (1..16).map { w ->
            val rir = when {
                w <= 4 -> 3
                w <= 8 -> 2
                w <= 12 -> 1
                else -> 2
            }
            val mapped = when {
                w <= 4 -> Triple(0, "Volumen largo", BlockGoal.ACCUMULATION)
                w <= 8 -> Triple(1, "Especialización", BlockGoal.ACCUMULATION)
                w <= 12 -> Triple(2, "Definición", BlockGoal.DENSITY)
                else -> Triple(3, "Pico de hipertrofia", BlockGoal.INTENSIFICATION)
            }
            weekRecipe(w, mapped.first, mapped.second, mapped.third, listOf(
                DayArchetypes.bbPush(rir).copy(weekday = 1),
                DayArchetypes.bbPull(rir).copy(weekday = 2),
                DayArchetypes.bbLegs(rir).copy(weekday = 3),
                DayArchetypes.bbPush((rir - 1).coerceAtLeast(0)).copy(weekday = 4, label = "Empuje 2"),
                DayArchetypes.bbPull((rir - 1).coerceAtLeast(0)).copy(weekday = 5, label = "Tirón 2"),
                DayArchetypes.bbLegs((rir - 1).coerceAtLeast(0)).copy(weekday = 6, label = "Pierna 2"),
            ))
        }
        return TrainingPlanRecipe("body-16-4", weeks, claimedDaysPerWeek = 6, claimedLevel = "avanzado")
    }

    fun offSeason20(): TrainingPlanRecipe {
        val weeks = (1..20).map { w ->
            val rir = if (w % 6 == 0) 4 else ((w - 1) % 5).let { 3 - it / 2 }.coerceAtLeast(1)
            val mapped = when {
                w <= 4 -> Triple(0, "Off-season", BlockGoal.ACCUMULATION)
                w <= 8 -> Triple(1, "Volumen", BlockGoal.ACCUMULATION)
                w <= 12 -> Triple(2, "Especialización", BlockGoal.INTENSIFICATION)
                w <= 16 -> Triple(3, "Definición", BlockGoal.DENSITY)
                else -> Triple(4, "Pico de hipertrofia", BlockGoal.INTENSIFICATION)
            }
            val days = when (mapped.first) {
                0 -> listOf(
                    DayArchetypes.bbPush(rir).copy(weekday = 1),
                    DayArchetypes.bbPull(rir).copy(weekday = 2),
                    DayArchetypes.bbLegs(rir).copy(weekday = 3),
                    DayArchetypes.bbTorso(rir).copy(weekday = 5, label = "Torso extra"),
                    DayArchetypes.bbLegs((rir - 1).coerceAtLeast(0), weekday = 6, label = "Pierna extra"),
                )
                1 -> listOf(
                    DayArchetypes.bbPush(rir).copy(weekday = 1),
                    DayArchetypes.bbPull(rir).copy(weekday = 2),
                    DayArchetypes.bbLegs(rir).copy(weekday = 3),
                    DayArchetypes.bbPush((rir - 1).coerceAtLeast(0)).copy(weekday = 5, label = "Empuje volumen"),
                    DayArchetypes.bbPull((rir - 1).coerceAtLeast(0)).copy(weekday = 6, label = "Tirón volumen"),
                )
                2 -> listOf(
                    DayArchetypes.bbPush(rir).copy(weekday = 1, label = "Empuje especialización"),
                    DayArchetypes.bbPull(rir).copy(weekday = 2),
                    DayArchetypes.bbLegs(rir).copy(weekday = 3),
                    DayArchetypes.bbTorso(rir).copy(weekday = 5, label = "Torso especialización"),
                    DayArchetypes.bbPush((rir - 1).coerceAtLeast(0)).copy(weekday = 6, label = "Hombro/pecho extra"),
                )
                3 -> listOf(
                    DayArchetypes.bbPush(rir).copy(weekday = 1),
                    DayArchetypes.bbPull(rir).copy(weekday = 2),
                    DayArchetypes.bbLegs(rir, hipDominant = true).copy(weekday = 3, label = "Pierna cadera"),
                    DayArchetypes.bbTorso(rir).copy(weekday = 5),
                    DayArchetypes.bbLegs((rir - 1).coerceAtLeast(0)).copy(weekday = 6, label = "Pierna rodilla"),
                )
                else -> listOf(
                    DayArchetypes.bbPush(rir.coerceAtMost(1)).copy(weekday = 1),
                    DayArchetypes.bbPull(rir.coerceAtMost(1)).copy(weekday = 2),
                    DayArchetypes.bbLegs(rir.coerceAtMost(1)).copy(weekday = 3),
                    DayArchetypes.bbTorso(rir.coerceAtMost(1)).copy(weekday = 5, label = "Torso pump"),
                    DayArchetypes.bbLegs(0, hipDominant = true, weekday = 6, label = "Pierna pump"),
                )
            }
            weekRecipe(w, mapped.first, mapped.second, mapped.third, days)
        }
        return TrainingPlanRecipe("body-20-5", weeks, claimedDaysPerWeek = 5, claimedLevel = "avanzado")
    }

    private fun peakDay(day: DayRecipe, topPct: Double, backPct: Double, topReps: Int): DayRecipe {
        val topSets = if (topReps <= 1) 1 else 2
        return day.replaceT1Work(
            topSetAndBackoff(topSets, topReps, topPct, 3, 3, backPct),
        ).withHeavyT1Rest().dropT3(2)
    }

    private fun openerDay(day: DayRecipe, pct: Double): DayRecipe = day.replaceT1Work(
        listOf(SetRecipe(reps = 1, percent = pct, isTopSet = true, loadBasis = LoadBasis.PERCENT_TM)),
    ).withHeavyT1Rest().dropT3(2)

    private fun testDay(day: DayRecipe, pcts: List<Double>): DayRecipe {
        val singles = pcts.mapIndexed { index, percent ->
            SetRecipe(
                reps = 1,
                percent = percent,
                isTopSet = index == pcts.lastIndex,
                loadBasis = LoadBasis.PERCENT_1RM,
            )
        }
        return day.replaceT1Work(singles, keepWarmup = false).withHeavyT1Rest().dropT3(2)
    }

    private fun DayRecipe.withHeavyT1Rest(): DayRecipe = copy(
        slots = slots.map { slot ->
            if (slot.role != SlotRole.T1_MAIN) slot
            else {
                val heavy = slot.sets.any { set -> !set.isWarmup && (set.percent ?: 0.0) >= 85.0 }
                slot.copy(restSeconds = if (heavy) maxOf(slot.restSeconds, 240) else slot.restSeconds)
            }
        },
    )

    private data class Variant(
        val sqId: String,
        val sqTech: TechniqueModifier?,
        val bpId: String,
        val bpTech: TechniqueModifier?,
        val dlId: String,
        val dlTech: TechniqueModifier?,
    )

    private fun DayRecipe.dropT3(n: Int): DayRecipe {
        if (n <= 0) return this
        return copy(
            slots = slots.map { slot ->
                if (slot.role != SlotRole.T3_ACCESSORY) slot
                else slot.copy(sets = slot.sets.take((slot.sets.size - n).coerceAtLeast(1)))
            },
        )
    }

    private fun DayRecipe.deload(): DayRecipe = copy(
        slots = slots.map { slot ->
            val warmups = slot.sets.filter { it.isWarmup }
            val work = slot.sets.filter { !it.isWarmup }
            val keep = (work.size * 0.5).toInt().coerceAtLeast(1)
            slot.copy(
                sets = warmups + work.take(keep).map { set ->
                    set.copy(
                        rir = maxOf(set.rir ?: 4, 4),
                        rpe = minOf(set.rpe ?: 6.0, 6.0),
                        percent = set.percent?.coerceAtMost(70.0),
                    )
                },
            )
        },
    )

    private data class Quad(
        val block: Int,
        val name: String,
        val goal: BlockGoal,
        val squat: Double,
        val bench: Double,
        val dl: Double,
    )
}
