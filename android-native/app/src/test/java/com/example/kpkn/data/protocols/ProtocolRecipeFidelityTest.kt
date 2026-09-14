package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.BlockGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolRecipeFidelityTest {
    private fun visible() = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }

    @Test
    fun wendler_week1_percent_anchors() {
        val protocol = visible().first { it.id == "wendler-531-bbb" }
        val t1 = protocol.recipe!!.weeks.first().days.first().slots.first { it.role == SlotRole.T1_MAIN }
        assertEquals(listOf(65.0, 75.0, 85.0), t1.sets.map { it.percent })
        assertTrue(t1.sets.last().amrap)
    }

    @Test
    fun nsuns_t1_bench_has_nine_sets() {
        val protocol = visible().first { it.id == "nsuns-531-lp-4d" }
        val t1 = protocol.recipe!!.weeks.first().days.first().slots.first { it.role == SlotRole.T1_MAIN }
        assertEquals(9, t1.sets.size)
        assertEquals(listOf(65.0, 75.0, 85.0, 85.0, 85.0, 80.0, 75.0, 70.0, 65.0), t1.sets.map { it.percent })
    }

    @Test
    fun texas_friday_is_top_set_of_five() {
        val protocol = visible().first { it.id == "texas-method-3d" }
        val friday = protocol.recipe!!.weeks.first().days.first { it.weekday == 5 }
        val squat = friday.slots.first { it.role == SlotRole.T1_MAIN }
        assertEquals(1, squat.sets.size)
        assertEquals(5, squat.sets.first().reps)
        assertTrue(squat.sets.first().isTopSet)
    }

    @Test
    fun smolov_base_week_matches_4x9_at_70() {
        val protocol = visible().first { it.id == "smolov" }
        val base = protocol.recipe!!.weeks.first { it.blockName == "Base" }
        val first = base.days.first().slots.first { it.role == SlotRole.T1_MAIN }
        assertEquals(4, first.sets.size)
        assertTrue(first.sets.all { it.reps == 9 && it.percent == 70.0 })
    }

    @Test
    fun coan_week10_is_desired_max_single() {
        val protocol = visible().first { it.id == "coan-phillipi-dl" }
        val week10 = protocol.recipe!!.weeks.first { it.weekNumber == 10 }
        val heavy = week10.days.first().slots.first { it.role == SlotRole.T1_MAIN }.sets.first()
        assertEquals(100.0, heavy.percent)
        assertEquals(1, heavy.reps)
        assertEquals(LoadBasis.PERCENT_DESIRED_MAX, heavy.loadBasis)
    }

    @Test
    fun madcow_ramp_constants() {
        val protocol = visible().first { it.id == "madcow-5x5" }
        val monday = protocol.recipe!!.weeks[3].days.first()
        val percents = monday.slots.first { it.role == SlotRole.T1_MAIN }.sets.map { it.percent!! }
        val ratios = percents.map { it / percents.last() * 100.0 }
        assertEquals(listOf(50.0, 62.5, 75.0, 87.5, 100.0), ratios.map { kotlin.math.round(it * 10) / 10.0 })
    }

    @Test
    fun juggernaut_10s_wave_matches_table() {
        val protocol = visible().first { it.id == "juggernaut-2" }
        val week1 = protocol.recipe!!.weeks.first()
        assertEquals(listOf(60.0, 60.0, 60.0, 60.0, 60.0), week1.days.first().slots.first { it.role == SlotRole.T1_MAIN }.sets.map { it.percent })
    }

    @Test
    fun texas_volume_is_90_percent_of_friday_top() {
        val protocol = visible().first { it.id == "texas-method-3d" }
        val week = protocol.recipe!!.weeks[1]
        val monday = week.days.first { it.weekday == 1 }.slots.first { it.role == SlotRole.T1_MAIN }
        val wednesday = week.days.first { it.weekday == 3 }.slots.first { it.role == SlotRole.T1_MAIN }
        val friday = week.days.first { it.weekday == 5 }.slots.first { it.role == SlotRole.T1_MAIN }
        assertEquals(5, monday.sets.size)
        assertTrue(monday.sets.all { it.reps == 5 && it.percent == 90.0 && it.loadBasis == LoadBasis.PERCENT_OF_TOP_SET })
        assertEquals(2, wednesday.sets.size)
        assertTrue(wednesday.sets.all { it.reps == 5 && it.percent == 80.0 && it.loadBasis == LoadBasis.PERCENT_OF_TOP_SET })
        assertEquals(1, friday.sets.size)
        assertTrue(friday.sets.first().isTopSet)
        assertEquals(5, friday.sets.first().reps)
    }

    @Test
    fun nsuns_t2_has_eight_sets() {
        val protocol = visible().first { it.id == "nsuns-531-lp-4d" }
        val t2 = protocol.recipe!!.weeks.first().days.first().slots.first { it.role == SlotRole.T2_SUPPLEMENTAL }
        assertEquals(8, t2.sets.size)
    }

    @Test
    fun sheiko_monday_is_squat_bench_squat() {
        val protocol = visible().first { it.id == "sheiko-29-32" }
        val monday = protocol.recipe!!.weeks.first().days.first { it.weekday == 1 }
        val main = monday.slots.filter { it.role != SlotRole.T3_ACCESSORY }
        assertEquals(listOf(CatalogIds.SQ_LOW, CatalogIds.BP, CatalogIds.SQ_LOW), main.map { it.lift.configurationId })
        val squat = main.first().sets.filter { !it.isWarmup }
        assertEquals(50.0, squat.first().percent)
        assertEquals(5, squat.first().reps)
    }

    @Test
    fun smolov_jr_includes_10x3_at_85() {
        val protocol = visible().first { it.id == "smolov-jr" }
        val found = protocol.recipe!!.weeks.any { week ->
            week.days.any { day ->
                day.slots.any { slot ->
                    slot.role == SlotRole.T1_MAIN && slot.sets.size == 10 && slot.sets.all { it.reps == 3 && it.percent == 85.0 }
                }
            }
        }
        assertTrue(found)
    }

    @Test
    fun smolov_base_week_has_four_distinct_day_schemes() {
        val protocol = visible().first { it.id == "smolov" }
        val base = protocol.recipe!!.weeks.first { it.blockName == "Base" && it.weekNumber == 3 }
        val schemes = base.days.map { day ->
            val work = day.slots.first { it.role == SlotRole.T1_MAIN }.sets.filter { !it.isWarmup }
            Triple(work.size, work.first().reps, work.first().percent)
        }
        assertEquals(
            listOf(
                Triple(4, 9, 70.0),
                Triple(5, 7, 75.0),
                Triple(7, 5, 80.0),
                Triple(10, 3, 85.0),
            ),
            schemes,
        )
    }

    @Test
    fun no_visible_recipe_prescribes_over_100_percent() {
        val recipes = visible().mapNotNull { it.recipe } +
            com.example.kpkn.data.programs.PROGRAM_TEMPLATES.mapNotNull { it.recipe }
        val failures = recipes.flatMap { recipe ->
            recipe.weeks.flatMap { week ->
                week.days.flatMap { day ->
                    day.slots.flatMap { slot ->
                        slot.sets.filter { !it.isWarmup && it.percent != null }.mapNotNull { set ->
                            val pct = set.percent!!
                            val implied = when (set.loadBasis) {
                                LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX -> pct
                                LoadBasis.PERCENT_TM -> pct * recipe.trainingMaxPercent
                                else -> null
                            } ?: return@mapNotNull null
                            if (implied > 100.0) {
                                "${recipe.id} w${week.weekNumber}/${day.label} ${slot.id} ${set.reps} @ $pct → $implied %1RM"
                            } else {
                                null
                            }
                        }
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun juggernaut_10s_week2_matches_table() {
        val protocol = visible().first { it.id == "juggernaut-2" }
        val week2 = protocol.recipe!!.weeks[1]
        assertEquals(
            listOf(55.0, 62.5, 67.5, 67.5, 67.5),
            week2.days.first().slots.first { it.role == SlotRole.T1_MAIN }.sets.map { it.percent },
        )
        assertTrue(protocol.recipe!!.weeks[2].days.first().slots.first { it.role == SlotRole.T1_MAIN }.sets.last().amrap)
    }

    @Test
    fun cube_week1_heavy_is_5x2_at_80() {
        val protocol = visible().first { it.id == "cube-method" }
        val heavy = protocol.recipe!!.weeks.first().days.first { it.label == "Pesado" }.slots.first { it.role == SlotRole.T1_MAIN }
        assertEquals(5, heavy.sets.size)
        assertTrue(heavy.sets.all { it.reps == 2 && it.percent == 80.0 })
    }

    @Test
    fun westside_de_lower_wave1_is_12x2() {
        val protocol = visible().first { it.id == "westside-conjugate" }
        val de = protocol.recipe!!.weeks.first().days.first { it.label.contains("DE Lower") }
        val speed = de.slots.first { it.role == SlotRole.SPEED }
        assertEquals(12, speed.sets.size)
        assertTrue(speed.sets.all { it.reps == 2 })
    }

    @Test
    fun korte_phase1_is_8x5_at_58() {
        val protocol = visible().first { it.id == "korte-3x3" }
        val squat = protocol.recipe!!.weeks.first().days.first().slots.first { it.role == SlotRole.T1_MAIN }
        assertEquals(8, squat.sets.size)
        assertTrue(squat.sets.all { it.reps == 5 && it.percent == 58.0 })
    }

    @Test
    fun calgary_week5_has_distinct_top_and_backoff_per_lift() {
        val protocol = visible().first { it.id == "calgary-16" }
        val week5 = protocol.recipe!!.weeks.first { it.weekNumber == 5 }
        val squat = week5.days.first { it.weekday == 1 }.slots.first { it.role == SlotRole.T1_MAIN }.sets.filter { !it.isWarmup }
        val bench = week5.days.first { it.weekday == 2 }.slots.first { it.role == SlotRole.T1_MAIN }.sets.filter { !it.isWarmup }
        val deadlift = week5.days.first { it.weekday == 4 }.slots.first { it.role == SlotRole.T1_MAIN }.sets.filter { !it.isWarmup }
        assertTrue(squat.any { it.percent == 76.0 && it.reps == 3 })
        assertTrue(squat.any { it.percent == 66.0 && it.reps == 5 })
        assertTrue(bench.map { it.percent }.toSet() != squat.map { it.percent }.toSet() || bench.map { it.reps }.toSet() != squat.map { it.reps }.toSet())
        assertTrue(deadlift.size != squat.size || deadlift.first().percent != squat.first().percent)
        val squatDays = week5.days.count { day ->
            day.slots.any { it.lift.liftSlot == LiftSlot.SQUAT || it.lift.configurationId.contains("squat") }
        }
        val benchDays = week5.days.count { day ->
            day.slots.any { it.lift.liftSlot == LiftSlot.BENCH || it.lift.configurationId.contains("bench") }
        }
        val dlDays = week5.days.count { day ->
            day.slots.any {
                it.lift.liftSlot == LiftSlot.DEADLIFT ||
                    it.lift.configurationId.contains("deadlift") ||
                    it.lift.configurationId.contains("rdl")
            }
        }
        assertTrue("SQ×3, hay $squatDays", squatDays >= 3)
        assertTrue("BP×4, hay $benchDays", benchDays >= 4)
        assertTrue("DL×3, hay $dlDays", dlDays >= 3)
    }

    @Test
    fun tsa_week1_has_four_distinct_day_schemes() {
        val protocol = visible().first { it.id == "tsa-9" }
        val week1 = protocol.recipe!!.weeks.first()
        val schemes = week1.days.map { day ->
            val work = day.slots.first { it.role == SlotRole.T1_MAIN }.sets.filter { !it.isWarmup }
            Triple(work.size, work.first().reps, work.first().percent)
        }
        assertEquals(4, schemes.distinct().size)
        assertEquals(Triple(4, 6, 71.0), schemes[0])
        assertEquals(Triple(5, 5, 69.0), schemes[1])
    }

    @Test
    fun candito_week1_has_five_conditioning_days() {
        val protocol = visible().first { it.id == "candito-6" }
        val week1 = protocol.recipe!!.weeks.first()
        assertEquals(5, week1.days.size)
        val squat = week1.days.first { it.weekday == 1 }.slots.first { it.role == SlotRole.T1_MAIN }.sets.filter { !it.isWarmup }
        val deadlift = week1.days.first { it.weekday == 3 }.slots.first { it.role == SlotRole.T1_MAIN }.sets.filter { !it.isWarmup }
        assertTrue(squat.all { it.reps == 6 && it.percent == 70.0 })
        assertEquals(4, squat.size)
        assertTrue(deadlift.all { it.reps == 8 && it.percent == 70.0 })
        assertEquals(4, protocol.recipe!!.daysPerWeek)
    }

    @Test
    fun calgary_week1_is_4x7_at_64() {
        val protocol = visible().first { it.id == "calgary-16" }
        val squat = protocol.recipe!!.weeks.first().days.first { it.weekday == 1 }.slots.first { it.role == SlotRole.T1_MAIN }
        val work = squat.sets.filter { !it.isWarmup }
        assertEquals(4, work.size)
        assertTrue(work.all { it.reps == 7 && it.percent == 64.0 })
    }

    @Test
    fun tsa_week5_is_deload_at_60() {
        val protocol = visible().first { it.id == "tsa-9" }
        val week5 = protocol.recipe!!.weeks.first { it.weekNumber == 5 }
        assertEquals(BlockGoal.DELOAD, week5.blockGoal)
        val squat = week5.days.first { it.weekday == 1 }.slots.first { it.role == SlotRole.T1_MAIN }
        val work = squat.sets.filter { !it.isWarmup }
        assertTrue(work.all { it.percent == 60.0 })
        assertEquals(3, work.size)
        assertTrue(work.all { it.reps == 5 })
    }

    @Test
    fun phul_power_is_3x3_and_hypertrophy_8_12() {
        val protocol = visible().first { it.id == "phul-verified" }
        val power = protocol.recipe!!.weeks.first().days.first { it.label == "Upper Power" }.slots.first { it.role == SlotRole.T1_MAIN }
        assertEquals(3, power.sets.size)
        assertTrue(power.sets.all { it.reps == 3 && it.percent == 82.0 })
        val hyper = protocol.recipe!!.weeks.first().days.first { it.label == "Upper Hypertrophy" }.slots.first { it.role == SlotRole.T1_MAIN }
        assertTrue(hyper.sets.all { it.reps == 8 && it.repsMax == 12 && it.rir == 2 })
    }

    @Test
    fun phat_speed_is_6x3() {
        val protocol = visible().first { it.id == "phat-verified" }
        val speed = protocol.recipe!!.weeks.first().days.first { it.label == "Power Upper" }.slots.first { it.role == SlotRole.SPEED }
        assertEquals(6, speed.sets.size)
        assertTrue(speed.sets.all { it.reps == 3 })
    }

    @Test
    fun every_visible_recipe_matches_fidelity_spec_weeks_and_days() {
        visible().forEach { protocol ->
            val spec = protocol.fidelitySpec!!
            val recipe = protocol.recipe!!
            assertEquals("${protocol.id} semanas", spec.expectedWeeks, recipe.weeks.size)
            assertEquals("${protocol.id} días", spec.expectedDaysPerWeek, recipe.daysPerWeek)
            if (spec.requiresAmrap) {
                assertTrue("${protocol.id} promete AMRAP", recipe.weeks.any { week -> week.days.any { day -> day.slots.any { slot -> slot.sets.any { it.amrap } } } })
            }
            if (spec.requiresPercent) {
                assertTrue("${protocol.id} promete %", recipe.weeks.any { week -> week.days.any { day -> day.slots.any { slot -> slot.sets.any { it.percent != null } } } })
            }
            if (spec.requiresRpe) {
                assertTrue("${protocol.id} promete RPE", recipe.weeks.any { week -> week.days.any { day -> day.slots.any { slot -> slot.sets.any { it.rpe != null } } } })
            }
            if (protocol.publicationStatus == ProtocolPublicationStatus.VERIFIED) {
                assertTrue("${protocol.id} VERIFIED de terceros debe declarar percentAnchors", spec.percentAnchors.isNotEmpty())
            }
            spec.percentAnchors.forEach { (key, expected) ->
                assertTrue(
                    "${protocol.id} ancla '$key' $expected no aparece en la receta",
                    recipeContainsPercents(recipe, expected),
                )
            }
        }
    }

    private fun recipeContainsPercents(recipe: TrainingPlanRecipe, expected: List<Double>): Boolean {
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                day.slots.forEach { slot ->
                    val work = slot.sets.filter { !it.isWarmup }.mapNotNull { it.percent }
                    if (work.containsAll(expected)) return true
                    if (containsInOrder(work, expected)) return true
                }
            }
            val weekT1 = week.days.flatMap { day ->
                day.slots.filter { it.role == SlotRole.T1_MAIN || it.role == SlotRole.SPEED }
                    .flatMap { it.sets.filter { set -> !set.isWarmup }.mapNotNull { it.percent } }
            }
            if (weekT1.containsAll(expected)) return true
        }
        val all = recipe.weeks.flatMap { week ->
            week.days.flatMap { day -> day.slots.flatMap { slot -> slot.sets.filter { !it.isWarmup }.mapNotNull { it.percent } } }
        }
        return all.containsAll(expected)
    }

    private fun containsInOrder(haystack: List<Double>, needle: List<Double>): Boolean {
        if (needle.isEmpty()) return true
        if (needle.size > haystack.size) return false
        return haystack.indices.any { start ->
            start + needle.size <= haystack.size && haystack.subList(start, start + needle.size) == needle
        }
    }
}
