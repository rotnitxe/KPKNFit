package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.PercentBasis
import com.example.kpkn.domain.training.ProgramAutoregulationEngine
import com.example.kpkn.domain.training.ProgramRecipeValidator
import com.example.kpkn.domain.training.RecipeContractPolicy
import com.example.kpkn.domain.training.SessionCompositionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ProtocolRecipeFidelityTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private fun visible() = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }

    private fun recipeOf(id: String): TrainingPlanRecipe = visible().first { it.id == id }.recipe!!

    private fun workSets(slot: SlotRecipe): List<SetRecipe> = slot.sets.filter { !it.isWarmup }

    private fun slotsOf(recipe: TrainingPlanRecipe): List<SlotRecipe> =
        recipe.weeks.flatMap { week -> week.days.flatMap { day -> day.slots } }

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
    fun candito_week1_has_four_days_on_the_same_weekdays_as_the_rest_of_the_plan() {
        // B.S6 (L-20, E-10): la semana 1 tenía 5 días («Lower C») en lunes a viernes distintos de los del resto del plan, contra el
        // claim de 4 días; ahora son los mismos cuatro días (lunes, martes, jueves y viernes) en las seis semanas.
        val protocol = visible().first { it.id == "candito-6" }
        val recipe = protocol.recipe!!
        val week1 = recipe.weeks.first()
        assertEquals(4, week1.days.size)
        assertEquals(listOf(1, 2, 4, 5), week1.days.map { it.weekday })
        assertEquals(listOf("Lower A", "Upper A", "Lower B", "Upper B"), week1.days.map { it.label })
        recipe.weeks.forEach { week ->
            assertEquals("semana ${week.weekNumber}", listOf(1, 2, 4, 5), week.days.map { it.weekday })
        }
        val squat = week1.days.first { it.weekday == 1 }.slots.first { it.role == SlotRole.T1_MAIN }.sets.filter { !it.isWarmup }
        val deadlift = week1.days.first { it.weekday == 4 }.slots.first { it.role == SlotRole.T1_MAIN }.sets.filter { !it.isWarmup }
        assertTrue(squat.all { it.reps == 6 && it.percent == 70.0 })
        assertEquals(4, squat.size)
        assertTrue(deadlift.all { it.reps == 8 && it.percent == 70.0 })
        assertEquals(4, deadlift.size)
        assertEquals(4, recipe.daysPerWeek)
        assertEquals("los días reales coinciden con los declarados", 4, realTrainingDays(recipe))
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
    fun no_visible_recipe_repeats_variant_config_as_technique() {
        // D2: si la configuración YA es la variante (cajón/déficit/pausa),
        // el slot no debe llevar además el TechniqueModifier redundante.
        val offenders = visible().flatMap { protocol ->
            protocol.recipe!!.weeks.flatMap { week ->
                week.days.flatMap { day ->
                    day.slots.mapNotNull { slot ->
                        val id = slot.lift.configurationId.lowercase()
                        val tech = slot.technique ?: return@mapNotNull null
                        val redundant = when (tech) {
                            TechniqueModifier.BOX -> "cajon" in id
                            TechniqueModifier.DEFICIT -> "deficit" in id
                            TechniqueModifier.PAUSE_2S -> "paused" in id || "pausa" in id
                            else -> false
                        }
                        if (redundant) "${protocol.id} w${week.weekNumber}/${day.label} ${slot.id} $id + $tech" else null
                    }
                }
            }
        }
        assertTrue("Slots con técnica redundante sobre la config: $offenders", offenders.isEmpty())
    }

    @Test
    fun every_visible_recipe_configuration_exists_in_catalog() {
        val catalogIds = CatalogCompositionTestSupport.catalog.families
            .flatMap { it.definitions }.flatMap { it.configurations }.map { it.id.lowercase() }.toSet()
        val missing = visible().flatMap { protocol ->
            protocol.recipe!!.weeks.flatMap { week ->
                week.days.flatMap { day ->
                    day.slots.mapNotNull { slot ->
                        val id = slot.lift.configurationId
                        if (id.trim().lowercase() in catalogIds) null
                        else "${protocol.id} w${week.weekNumber}/${day.label} ${slot.id} $id"
                    }
                }
            }
        }
        assertTrue("Configs de receta fuera del catálogo: $missing", missing.isEmpty())
    }

    /**
     * Protocolos cuya frecuencia real no cuadra con `fidelitySpec.expectedDaysPerWeek` (E-10): usan más días
     * distintos que los que declaran. B.S6 parte 1 alineó los tres que quedaban (Smolov, con la fase intensa en
     * lunes, miércoles y sábado dentro de los cuatro días de la base; Candito, con la semana 1 de 4 días en los
     * mismos días que el resto; Lilliebridge, con el taper en lunes, miércoles y viernes), así que la lista queda
     * vacía y cualquier receta nueva que no cuadre falla. No es un comodín: una receta que ya cuadre y siga en la
     * lista también falla (hay que sacarla).
     */
    private val KNOWN_FREQUENCY_MISMATCH: Set<String> = emptySet()

    /**
     * Días reales de una receta: los `weekday` distintos que usa en todo el plan. Es la misma cuenta
     * que hace `PlanCandidateEvaluator.programSessionDays` para el rechazo «usa N días distintos»
     * (el materializador copia el weekday de cada día al `dayOfWeek` de su sesión), no el claim
     * `claimedDaysPerWeek`. Si algún día no declara weekday cuenta, en su lugar, los días de la
     * semana más cargada.
     */
    private fun realTrainingDays(recipe: TrainingPlanRecipe): Int {
        val weekdays = recipe.weeks.flatMap { week -> week.days.map { it.weekday } }
        return if (weekdays.isNotEmpty() && weekdays.all { it != null }) {
            weekdays.filterNotNull().distinct().size
        } else {
            recipe.weeks.maxOfOrNull { it.days.size } ?: 0
        }
    }

    @Test
    fun every_visible_recipe_matches_fidelity_spec_weeks_and_days() {
        val frequencyMismatches = mutableMapOf<String, String>()
        visible().forEach { protocol ->
            val spec = protocol.fidelitySpec!!
            val recipe = protocol.recipe!!
            assertEquals("${protocol.id} semanas", spec.expectedWeeks, recipe.weeks.size)
            // El claim de la receta (lo que publica el catálogo) coincide con el spec...
            assertEquals("${protocol.id} días declarados", spec.expectedDaysPerWeek, recipe.daysPerWeek)
            // ...y los días reales (weekday distintos de todo el plan) también, salvo los conocidos.
            val realDays = realTrainingDays(recipe)
            if (realDays != spec.expectedDaysPerWeek) {
                val perWeek = recipe.weeks.map { it.days.size }
                frequencyMismatches[protocol.id] =
                    "spec ${spec.expectedDaysPerWeek} días, reales $realDays (por semana ${perWeek.minOrNull()}..${perWeek.maxOrNull()})"
            }
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
        frequencyMismatches.forEach { (id, detail) -> println("FRECUENCIA REAL $id: $detail") }
        val unexpected = frequencyMismatches.keys - KNOWN_FREQUENCY_MISMATCH
        assertTrue(
            "Recetas cuya frecuencia real no cuadra con su fidelitySpec (corrige el dato o el spec; " +
                "KNOWN_FREQUENCY_MISMATCH no admite más): " + unexpected.sorted().joinToString { "$it → ${frequencyMismatches.getValue(it)}" },
            unexpected.isEmpty(),
        )
        val stale = KNOWN_FREQUENCY_MISMATCH - frequencyMismatches.keys
        assertTrue(
            "Estas recetas ya cuadran con su fidelitySpec: sácalas de KNOWN_FREQUENCY_MISMATCH: ${stale.sorted()}",
            stale.isEmpty(),
        )
    }

    // ─── B.S6 parte 1: correcciones ALTA receta a receta ─────────────────────

    private val metadata get() = CatalogCompositionTestSupport.metadata

    private fun templateRecipe(id: String): TrainingPlanRecipe =
        PROGRAM_TEMPLATES.mapNotNull { it.recipe }.first { it.id == id }

    @Test
    fun madcow_is_a_5rm_ramp_with_cycle_increments_and_no_cross_pattern_supplementals() {
        // L-03: la subida de +2,5 %/semana ya va dentro de la rampa; `WeeklyPercent` la aplicaba dos veces y no tiene consumidor.
        val recipe = recipeOf("madcow-5x5")
        assertEquals(0.87, recipe.trainingMaxPercent, 1e-9)
        assertEquals(ProgressionRule.CycleIncrement(upperKg = 2.5, lowerKg = 5.0), recipe.progression)
        assertEquals(IncrementScope.CYCLE, (recipe.progression as ProgressionRule.CycleIncrement).scope)
        assertTrue("Madcow no cuelga ningún slot de otro (C8)", slotsOf(recipe).none { it.supplementalOf != null })
        val week4 = recipe.weeks[3]
        val monday = week4.days.first { it.weekday == 1 }.slots.first { it.id == "sq" }
        assertEquals("rampa del lunes de la semana 4 al 100 % del TM", 100.0, workSets(monday).last().percent!!, 1e-9)
        val friday = week4.days.first { it.weekday == 5 }.slots.first { it.id == "sq" }
        val triple = friday.sets.first { it.isTopSet }
        assertEquals(3, triple.reps)
        assertEquals("triple del viernes al 102,5 % del TM", 102.5, triple.percent!!, 1e-9)
        val aboveTheTm = slotsOf(recipe).flatMap { slot -> workSets(slot).filter { (it.percent ?: 0.0) > 100.0 } }
        assertEquals("el triple de la semana 4 es el único salto sobre el TM", 1, aboveTheTm.size)
    }

    @Test
    fun texas_3d_chin_ups_are_three_sets_of_eight_at_rpe_8_without_amrap() {
        // L-16: las dominadas llevaban las tres series como AMRAP y a RPE 8 a la vez.
        val recipe = recipeOf("texas-method-3d")
        assertEquals(ProgressionRule.TopSetPr(upperKg = 1.25, lowerKg = 2.5), recipe.progression)
        recipe.weeks.forEach { week ->
            val chin = week.days.first { it.weekday == 3 }.slots.first { it.id == "chin" }
            assertEquals(3, chin.sets.size)
            assertTrue(chin.sets.all { it.reps == 8 && it.rpe == 8.0 && !it.amrap })
        }
    }

    @Test
    fun texas_4d_uses_the_5rm_training_max_with_a_top_five_at_100_and_the_volume_at_90() {
        // L-02/L-18 (D7): TM = 87 % del 1RM; el top 1×5 al 100 % del TM y el volumen 5×5 al 90 % (3×5 en peso muerto).
        val recipe = recipeOf("texas-method-4d")
        assertEquals(0.87, recipe.trainingMaxPercent, 1e-9)
        assertEquals(ProgressionRule.TopSetPr(upperKg = 1.25, lowerKg = 2.5), recipe.progression)
        assertEquals(listOf(1, 2, 4, 5), recipe.weeks.first().days.map { it.weekday })
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                val label = "w${week.weekNumber}/${day.label}"
                val t1 = day.slots.single { it.id == "t1" }
                val t2 = day.slots.single { it.id == "t2" }
                val top = t1.sets.single()
                assertEquals("$label: top de 5 repeticiones", 5, top.reps)
                assertEquals("$label: al 100 % del TM", 100.0, top.percent!!, 1e-9)
                assertTrue("$label: es el top set", top.isTopSet)
                val volumeSets = if (t2.lift.configurationId == CatalogIds.DL) 3 else 5
                assertEquals("$label: series del volumen", volumeSets, t2.sets.size)
                assertTrue("$label: volumen 5 repeticiones al 90 %", t2.sets.all { it.reps == 5 && it.percent == 90.0 })
            }
        }
        val squatTop = recipe.weeks.first().days.first { it.label == "Sentadilla/PM" }.slots.first { it.id == "t1" }
        val effective = PercentBasis.effective1RmPercent(squatTop.sets.single(), squatTop, recipe.weeks.first(), recipe.trainingMaxPercent)
        assertEquals("el top de 1×5 es el 87 % del 1RM", 87.0, effective!!, 1e-9)
    }

    @Test
    fun nsuns_amrap_is_on_the_heavy_single() {
        // L-04/L-08: el AMRAP estaba en la última serie, al 65 %, donde `AmrapDrivenTm` nunca sube el TM; ahora es la serie 1+ al 95 %.
        val recipe = recipeOf("nsuns-531-lp-4d")
        assertEquals(ProgressionRule.AmrapDrivenTm(), recipe.progression)
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                val t1 = day.slots.single { it.id == "t1" }
                val amraps = t1.sets.filter { it.amrap }
                val label = "w${week.weekNumber}/${day.label}"
                if (day.label == "Banca/OHP") {
                    assertTrue("$label: la banca de volumen ya no lleva AMRAP", amraps.isEmpty())
                } else {
                    val amrap = amraps.single()
                    assertEquals("$label: AMRAP en la serie 1+", 1, amrap.reps)
                    assertEquals("$label: al 95 % del TM", 95.0, amrap.percent!!, 1e-9)
                    assertEquals("$label: la tercera serie", 2, t1.sets.indexOf(amrap))
                }
            }
        }
        val tooLight = slotsOf(recipe).flatMap { slot -> workSets(slot).filter { it.amrap && (it.percent ?: 0.0) < ProgramAutoregulationEngine.AMRAP_TM_MIN_PERCENT } }
        assertTrue("AMRAP por debajo del umbral que mueve el TM: $tooLight", tooLight.isEmpty())
        // El segundo día de banca lleva banca con agarre cerrado como T2 (configuración propia, sin técnica).
        val closeGrip = recipe.weeks.first().days.first { it.label == "Banca/Cerrado" }.slots.first { it.id == "t2" }
        assertEquals(CatalogIds.BP_CLOSE_GRIP, closeGrip.lift.configurationId)
        assertNull(closeGrip.technique)
        // C1: las cuatro series de 9 se declaran por slot (el `t1` de cada día), nunca con un comodín.
        assertEquals(
            listOf("w*/Banca/OHP/t1", "w*/Sentadilla/Sumo/t1", "w*/Banca/Cerrado/t1", "w*/Peso muerto/Frontal/t1"),
            recipe.exemptions.map { it.scope },
        )
        assertTrue(recipe.exemptions.all { it.rule == RecipeContractPolicy.C1_SET_RANGE && it.justification.length >= 25 && it.sourceUrl != null })
        val c1 = SessionCompositionPolicy.evaluateRecipe(recipe, metadata).filter { it.rule == RecipeContractPolicy.C1_SET_RANGE }
        assertTrue("C1 sin silenciar: $c1", c1.isEmpty())
    }

    @Test
    fun lilliebridge_has_heavy_deadlift_in_even_weeks() {
        // L-07: el lunes alterna sentadilla pesada (semanas impares) y peso muerto pesado (pares); el peso muerto par era 3×3 al 70 %.
        val recipe = recipeOf("lilliebridge")
        val heavyDeadlift = mapOf(2 to 85.0, 4 to 87.0, 6 to 90.0, 8 to 92.0)
        val heavySquat = mapOf(1 to 87.0, 3 to 90.0, 5 to 92.0, 7 to 95.0, 9 to 90.0)
        (1..9).forEach { week ->
            val t1 = recipe.weeks[week - 1].days.first { it.weekday == 1 }.slots.first { it.role == SlotRole.T1_MAIN }
            val work = workSets(t1)
            assertEquals("semana $week: dos singles", listOf(1, 1), work.map { it.reps })
            assertTrue("semana $week: la última serie es el top set", work.last().isTopSet)
            if (week % 2 == 0) {
                assertEquals("semana $week: lunes de peso muerto pesado", CatalogIds.DL, t1.lift.configurationId)
                assertEquals(heavyDeadlift.getValue(week), work.last().percent!!, 1e-9)
                assertTrue("semana $week: el peso muerto pesado llega al 85 % o más", work.last().percent!! >= 85.0)
            } else {
                assertEquals("semana $week: lunes de sentadilla pesada", CatalogIds.SQ_LOW, t1.lift.configurationId)
                assertEquals(heavySquat.getValue(week), work.last().percent!!, 1e-9)
            }
        }
        // Sin regla de subida (los porcentajes semanales ya son la progresión) y siempre lunes, miércoles y viernes.
        assertEquals(ProgressionRule.None, recipe.progression)
        recipe.weeks.forEach { week ->
            assertEquals("semana ${week.weekNumber}", listOf(1, 3, 5), week.days.map { it.weekday })
        }
        assertEquals(3, realTrainingDays(recipe))
    }

    @Test
    fun wendler_deload_week_has_no_supplemental_work_and_is_marked_as_deload() {
        // L-12: la cuarta semana del ciclo es de descarga y Wendler no programa suplementario ahí; BBB va siempre 5×10 al 50 %.
        listOf("wendler-531-bbb" to "bbb", "wendler-531-fsl" to "fsl").forEach { (id, supplementalId) ->
            val recipe = recipeOf(id)
            val deload = recipe.weeks.last()
            assertEquals("$id: la semana de descarga es la 4", 4, deload.weekNumber)
            assertEquals("$id: kind", WeekExecutionKind.DELOAD, deload.kind)
            assertEquals("$id: blockGoal", BlockGoal.DELOAD, deload.blockGoal)
            assertTrue("$id: la descarga no lleva suplementario", deload.days.all { day -> day.slots.none { it.id == supplementalId } })
            recipe.weeks.dropLast(1).forEach { week ->
                assertEquals("$id w${week.weekNumber}: kind", WeekExecutionKind.TRAINING, week.kind)
                assertTrue(
                    "$id w${week.weekNumber}: cada día lleva su suplementario",
                    week.days.all { day -> day.slots.count { it.id == supplementalId } == 1 },
                )
            }
            val closeGrip = slotsOf(recipe).filter { it.id == "cg" }
            assertTrue("$id: el press cerrado es la configuración propia", closeGrip.isNotEmpty() && closeGrip.all { it.lift.configurationId == CatalogIds.BP_CLOSE_GRIP && it.technique == null })
        }
        val bbb = slotsOf(recipeOf("wendler-531-bbb")).filter { it.id == "bbb" }
        assertTrue("BBB 5×10 al 50 % del TM", bbb.all { slot -> slot.sets.size == 5 && slot.sets.all { it.reps == 10 && it.percent == 50.0 } })
        val fslFirstPercent = recipeOf("wendler-531-fsl").weeks.dropLast(1).map { week -> week.days.first().slots.first { it.id == "fsl" }.sets.first().percent }
        assertEquals(listOf(65.0, 70.0, 75.0), fslFirstPercent)
    }

    @Test
    fun kpkn_sbd4_trains_the_deadlift_heavy_on_its_own_day_without_composition_findings() {
        // L-05/L-06: peso muerto entre semana sin W3 con la sentadilla, y un pico de sentadilla de 85 % del 1RM o más.
        val recipe = recipeOf("kpkn-native-sbd-4")
        assertTrue("cero exenciones", recipe.exemptions.isEmpty())
        recipe.weeks.forEach { week ->
            assertEquals("semana ${week.weekNumber}", listOf(1, 2, 4, 5), week.days.map { it.weekday })
            assertEquals(listOf("Sentadilla/Banca", "Banca Volumen", "Peso Muerto", "Banca pesada"), week.days.map { it.label })
        }
        val deadlift = recipe.weeks.map { week -> week.days.first { it.weekday == 4 }.slots.first { it.id == "dl" } }
        assertEquals(listOf(72.0, 73.0, 74.0, 75.0, 80.0, 82.0, 84.0, 86.0, 94.0, 98.0, 80.0), deadlift.map { workSets(it).first().percent })
        assertEquals(listOf(3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2), deadlift.map { workSets(it).size })
        assertEquals(listOf(4, 4, 4, 4, 3, 3, 3, 3, 2, 1, 2), deadlift.map { workSets(it).first().reps })
        val squatPeak = recipe.weeks.filter { it.blockGoal == BlockGoal.PEAK }.flatMap { week ->
            val slot = week.days.first { it.weekday == 1 }.slots.first { it.id == "sq" }
            workSets(slot).map { set -> PercentBasis.effective1RmPercent(set, slot, week, recipe.trainingMaxPercent)!! }
        }
        assertTrue("el pico de sentadilla llega al 85 % del 1RM: ${squatPeak.maxOrNull()}", squatPeak.maxOrNull()!! >= 85.0)
        val findings = SessionCompositionPolicy.evaluateRecipe(recipe, metadata).filter { it.rule == "W3" || it.rule == "W4" }
        assertTrue("W3/W4 en SBD-4: $findings", findings.isEmpty())
    }

    @Test
    fun uhf9_has_a_volume_and_an_intensity_block_and_a_squat_only_saturday() {
        // L-10: la semana 5 pertenece al bloque de volumen y el sábado es solo sentadilla ligera (sin peso muerto con déficit ni banca pausa).
        val protocol = visible().first { it.id == "gzcl-uhf-9" }
        val recipe = protocol.recipe!!
        assertEquals(listOf("Volumen", "Intensidad"), protocol.blocks.map { it.name })
        assertEquals(listOf(5, 4), protocol.blocks.map { it.weeks })
        assertEquals(List(5) { "Volumen" } + List(4) { "Intensidad" }, recipe.weeks.map { it.blockName })
        assertEquals(List(5) { 0 } + List(4) { 1 }, recipe.weeks.map { it.blockIndex })
        recipe.weeks.forEach { week ->
            val saturday = week.days.single { it.weekday == 6 }
            val mainWork = saturday.slots.filter { it.role != SlotRole.T3_ACCESSORY }
            assertEquals("semana ${week.weekNumber}: el sábado solo trae la sentadilla", listOf("sq"), mainWork.map { it.id })
            assertEquals(CatalogIds.SQ_LOW, mainWork.single().lift.configurationId)
            val deficitDeadlifts = week.days.sumOf { day -> day.slots.count { it.lift.configurationId == CatalogIds.DL_DEF } }
            assertEquals("semana ${week.weekNumber}: el peso muerto con déficit cae una vez por semana", 1, deficitDeadlifts)
        }
    }

    @Test
    fun westside_rotates_three_me_variants_without_a_progression_rule_or_exemptions() {
        // L-14: sin exenciones (W5 muerta), sin variantes inalcanzables y sin `RepMaxAutoregulated` (las variantes no cuentan).
        val protocol = visible().first { it.id == "westside-conjugate" }
        val recipe = protocol.recipe!!
        assertEquals(ProgressionRule.None, recipe.progression)
        assertTrue(recipe.exemptions.isEmpty())
        assertTrue(protocol.exemptions.isEmpty())
        val lower = recipe.weeks.map { week -> week.days.first { it.label == "ME Lower" }.slots.first { it.id == "me" }.lift.configurationId }
        val upper = recipe.weeks.map { week -> week.days.first { it.label == "ME Upper" }.slots.first { it.id == "me" }.lift.configurationId }
        assertEquals(listOf(CatalogIds.SQ_BOX, CatalogIds.GM, CatalogIds.DL_DEF), lower)
        assertEquals(listOf(CatalogIds.BP_FLOOR, CatalogIds.BP_CHAINS, CatalogIds.BP_INC), upper)
    }

    @Test
    fun rts_has_no_progression_rule_because_it_has_no_set_that_moves_the_tm() {
        // L-26: `RepTargetDrivenTm` pide AMRAP y RTS no trabaja al fallo; sin regla hasta que la Fase 2 lleve el método a top sets por esfuerzo.
        val recipe = recipeOf("kpkn-rts-style")
        assertEquals(ProgressionRule.None, recipe.progression)
        assertTrue("RTS no tiene series AMRAP", slotsOf(recipe).flatMap { workSets(it) }.none { it.amrap })
        val c6 = RecipeContractPolicy.evaluate(recipe, metadata).filter { it.rule == RecipeContractPolicy.C6_PROGRESSION_CONSUMER }
        assertTrue("C6 en RTS: $c6", c6.isEmpty())
    }

    @Test
    fun gzclp_description_matches_implementation() {
        // L-09: la descripción prometía subida lineal por sesión, cambio de etapa y T3 de 15 a 20; la receta es solo la etapa 1.
        val protocol = visible().first { it.id == "gzclp" }
        val recipe = protocol.recipe!!
        assertEquals(4, recipe.weeks.size)
        assertEquals(4, recipe.daysPerWeek)
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                val label = "w${week.weekNumber}/${day.label}"
                val t1 = day.slots.single { it.id == "t1" }
                assertEquals("$label: T1 5×3", 5, t1.sets.size)
                assertTrue("$label: T1 al 85 % del TM", t1.sets.all { it.reps == 3 && it.percent == 85.0 })
                assertEquals("$label: solo la última serie es AMRAP", listOf(false, false, false, false, true), t1.sets.map { it.amrap })
                val t2 = day.slots.single { it.id == "t2" }
                assertEquals("$label: T2 3×10", 3, t2.sets.size)
                assertTrue("$label: T2 al 65 % del TM", t2.sets.all { it.reps == 10 && it.percent == 65.0 })
                val t3a = day.slots.single { it.id == "t3a" }
                assertTrue("$label: T3 de 15 a 20 repeticiones", t3a.sets.all { it.repsMin == 15 && it.repsMax == 20 })
            }
        }
        assertEquals(ProgressionRule.CycleIncrement(upperKg = 2.5, lowerKg = 5.0), recipe.progression)
        val description = protocol.description
        listOf(
            "4 días, 4 semanas",
            "T1 5×3+ al 85 % del TM",
            "T2 3×10 al 65 %",
            "T3 de 15 a 20 repeticiones",
            "2,5 kg (banca y press)",
            "5 kg (sentadilla y peso muerto)",
            "sin cambio de etapa ni reinicio por fallo",
        ).forEach { fragment ->
            assertTrue("la descripción de GZCLP debe decir «$fragment»: $description", description.contains(fragment))
        }
    }

    @Test
    fun smolov_declares_the_c4_exemption_only_for_the_intense_weeks_and_fits_four_distinct_days() {
        // E-10: la fase intensa son 3 sesiones de sentadilla por semana (lunes, miércoles y sábado) dentro de los 4 días de la base.
        val protocol = visible().first { it.id == "smolov" }
        val recipe = protocol.recipe!!
        val c4 = recipe.exemptions.filter { it.rule == RecipeContractPolicy.C4_CLAIMED_DAYS }
        assertEquals((9..13).map { "w$it" }, c4.map { it.scope })
        c4.forEach { exemption ->
            assertTrue("justificación de 25 caracteres o más", exemption.justification.length >= 25)
            assertFalse("sin comodines", exemption.scope.contains("*"))
            assertTrue("con fuente", !exemption.sourceUrl.isNullOrBlank())
        }
        assertEquals(setOf("H6", RecipeContractPolicy.C4_CLAIMED_DAYS), recipe.exemptions.map { it.rule }.toSet())
        assertTrue("sin la W3 ni la W6 muertas", recipe.exemptions.none { it.rule == "W3" || it.rule == "W6" })
        assertTrue("sin la H6 muerta de S4", recipe.exemptions.none { it.scope == "w*/S4" })
        assertEquals(setOf(1, 3, 4, 6), recipe.weeks.flatMap { week -> week.days.map { it.weekday } }.toSet())
        recipe.weeks.filter { it.blockName == "Intenso" }.forEach { week ->
            assertEquals("semana ${week.weekNumber}", listOf(1, 3, 6), week.days.map { it.weekday })
        }
        assertEquals(4, realTrainingDays(recipe))
        val c4Findings = SessionCompositionPolicy.evaluateRecipe(recipe, metadata).filter { it.rule == RecipeContractPolicy.C4_CLAIMED_DAYS }
        assertTrue("C4 sin silenciar: $c4Findings", c4Findings.isEmpty())
    }

    @Test
    fun smolov_jr_labels_its_days_s1_to_s4_and_inherits_no_exemptions() {
        // L-35: los cuatro días se llamaban «Sesión» y heredaban siete exenciones muertas del Smolov completo.
        val protocol = visible().first { it.id == "smolov-jr" }
        val recipe = protocol.recipe!!
        recipe.weeks.forEach { week ->
            assertEquals("semana ${week.weekNumber}", listOf("S1", "S2", "S3", "S4"), week.days.map { it.label })
            assertEquals(listOf(1, 3, 4, 6), week.days.map { it.weekday })
        }
        assertTrue(recipe.exemptions.isEmpty())
        assertTrue(protocol.exemptions.isEmpty())
        assertEquals(ProgressionRule.WeeklyKg(mapOf(2 to 5.0, 3 to 10.0)), recipe.progression)
    }

    @Test
    fun ppl_never_prescribes_rir_zero_on_compounds() {
        // L-24/L-25: la segunda sesión de cada grupo (PPL «2», pierna B del estilo RP y de la plantilla de 12 semanas) bajaba hasta
        // RIR 0 en los compuestos (semanas 5 a 10 de PPL, 3 a 5 de RP, 5 y 7 a 11 de body-12-3), contra el «RIR 3→1» de la descripción.
        listOf(recipeOf("kpkn-ppl-6"), recipeOf("kpkn-rp-style"), templateRecipe("body-12-3")).forEach { recipe ->
            val zeroRir = recipe.weeks.flatMap { week ->
                week.days.flatMap { day ->
                    day.slots.filter { slot -> workSets(slot).any { (it.rir ?: Int.MAX_VALUE) <= 0 } }.map { "w${week.weekNumber}/${day.label}/${it.id}" }
                }
            }
            assertTrue("${recipe.id}: series de trabajo a RIR 0: $zeroRir", zeroRir.isEmpty())
            val compounds = slotsOf(recipe).filter { it.role == SlotRole.T1_MAIN || it.role == SlotRole.T2_SUPPLEMENTAL }
            assertTrue("${recipe.id}: tiene compuestos T1/T2", compounds.isNotEmpty())
            assertTrue("${recipe.id}: todos los compuestos llevan RIR", compounds.all { slot -> workSets(slot).all { it.rir != null } })
        }
        // PPL conserva el «RIR 3→1»: la primera sesión empieza en RIR 3 y el suelo de todo el plan es 1 fuera de la descarga.
        val ppl = recipeOf("kpkn-ppl-6")
        val trainingRir = ppl.weeks.filter { it.kind == WeekExecutionKind.TRAINING }
            .flatMap { week -> week.days.flatMap { day -> day.slots.flatMap { workSets(it) } } }.mapNotNull { it.rir }
        assertEquals(3, trainingRir.maxOrNull())
        assertEquals(1, trainingRir.minOrNull())
    }

    @Test
    fun hypertrophy_deload_weeks_are_marked_as_deload_and_cut_the_series() {
        // L-24/L-25: las semanas de descarga repetían las mismas series y no llevaban `kind = DELOAD`.
        val recipes = listOf(
            recipeOf("kpkn-ppl-6") to listOf(11, 12),
            recipeOf("kpkn-rp-style") to listOf(6),
            templateRecipe("body-12-3") to listOf(6, 12),
        )
        recipes.forEach { (recipe, deloadWeeks) ->
            assertEquals("${recipe.id}: semanas de descarga", deloadWeeks, recipe.weeks.filter { it.blockGoal == BlockGoal.DELOAD }.map { it.weekNumber })
            fun workSetCount(week: WeekRecipe) = week.days.sumOf { day -> day.slots.sumOf { workSets(it).size } }
            deloadWeeks.forEach { number ->
                val deload = recipe.weeks.first { it.weekNumber == number }
                assertEquals("${recipe.id} w$number: kind", WeekExecutionKind.DELOAD, deload.kind)
                val reference = recipe.weeks.last { it.blockGoal != BlockGoal.DELOAD && it.weekNumber < number }
                assertTrue(
                    "${recipe.id} w$number: la descarga recorta las series (${workSetCount(deload)} frente a ${workSetCount(reference)})",
                    workSetCount(deload) * 100 <= workSetCount(reference) * 60,
                )
                val easy = deload.days.flatMap { day -> day.slots.flatMap { workSets(it) } }
                assertTrue("${recipe.id} w$number: RIR 4 o más y RPE 6 o menos", easy.all { (it.rir ?: 0) >= 4 && (it.rpe ?: 10.0) <= 6.0 })
            }
            assertTrue(
                "${recipe.id}: C5 no debería dar hallazgo con descarga",
                RecipeContractPolicy.evaluate(recipe, metadata).none { it.rule == RecipeContractPolicy.C5_DELOAD_REQUIRED },
            )
        }
    }

    @Test
    fun cube_repetitions_day_is_one_amrap_set_and_its_incline_bench_does_not_hang_from_the_deadlift() {
        // L-13: el día de repeticiones era una serie suelta sin marcar (C1) y la banca inclinada colgaba del peso muerto (C8).
        val protocol = visible().first { it.id == "cube-method" }
        val recipe = protocol.recipe!!
        recipe.weeks.forEach { week ->
            val repetitions = week.days.first { it.label == "Repeticiones" }
            val deadliftSets = workSets(repetitions.slots.first { it.id == "dl" })
            assertEquals("semana ${week.weekNumber}: una sola serie de peso muerto", 1, deadliftSets.size)
            if (week.weekNumber <= 9) {
                assertTrue("semana ${week.weekNumber}: la serie es AMRAP", deadliftSets.single().amrap)
            } else {
                assertEquals("semana 10: test de una repetición", 1, deadliftSets.single().reps)
            }
            assertNull("semana ${week.weekNumber}: la banca inclinada no cuelga del peso muerto", repetitions.slots.first { it.id == "bp" }.supplementalOf)
        }
        assertTrue(protocol.description.contains("una serie AMRAP"))
        assertTrue(protocol.description.contains("rotación semanal de los tres métodos del Cube no está implementada"))
    }

    @Test
    fun the_shrug_and_the_pushdown_are_two_sets_and_not_a_loose_set() {
        // L-33: el encogimiento (arquetipo de peso muerto) y el pushdown (arquetipo de empuje) llevaban 1 serie, que el contrato C1 no admite.
        val shrug = DayArchetypes.plDeadlift(70.0).slots.single { it.lift.configurationId == CatalogIds.SHRUG }
        assertEquals(2, workSets(shrug).size)
        val pushdown = DayArchetypes.bbPush().slots.single { it.lift.configurationId == CatalogIds.PUSHDOWN }
        assertEquals(2, workSets(pushdown).size)
    }

    @Test
    fun calgary_and_tsa_percentages_are_percent_of_the_1rm_and_tsa_carries_the_rpe_on_every_heavy_lift() {
        // L-05: los porcentajes de Calgary y TSA son del 1RM (el test de la semana 9 al 95 % era un 85,5 % con un TM del 90 %); L-21: RPE en SBD.
        listOf("calgary-16", "tsa-9").forEach { id ->
            assertEquals("$id: TM al 100 % del 1RM", 1.0, recipeOf(id).trainingMaxPercent, 1e-9)
        }
        val tsa = recipeOf("tsa-9")
        val test = tsa.weeks.last().days.first { it.weekday == 1 }.slots.first { it.role == SlotRole.T1_MAIN }
        assertEquals(95.0, workSets(test).single().percent!!, 1e-9)
        tsa.weeks.forEach { week ->
            listOf(1, 2, 4).forEach { weekday ->
                val t1 = week.days.first { it.weekday == weekday }.slots.first { it.role == SlotRole.T1_MAIN }
                assertTrue("TSA w${week.weekNumber} día $weekday: RPE en todas las series de trabajo", workSets(t1).all { it.rpe != null })
            }
        }
        val calgarySquatTech = slotsOf(recipeOf("calgary-16")).filter { it.id == "sq-tech" }
        assertEquals(16, calgarySquatTech.size)
        assertTrue(calgarySquatTech.all { it.lift.configurationId == CatalogIds.SQ_PAUSED && it.technique == null })
    }

    @Test
    fun the_curated_configurations_replace_the_technique_patches_in_every_published_recipe() {
        // D2: agarre cerrado, pausa en sentadilla, peso muerto hasta la rodilla, jalón cerrado y curl inclinado son configuraciones
        // aprobadas del catálogo (M1 a M5), no `técnica + configuración base`.
        val recipes = visible().map { it.recipe!! } + PROGRAM_TEMPLATES.mapNotNull { it.recipe } + AuthoredPhulPhatRecipes.all
        fun patchesOf(slot: SlotRecipe): List<String> = listOfNotNull(
            "BP + CLOSE_GRIP".takeIf { slot.lift.configurationId == CatalogIds.BP && slot.technique == TechniqueModifier.CLOSE_GRIP },
            "SQ + PAUSE_2S".takeIf {
                slot.lift.configurationId in setOf(CatalogIds.SQ_LOW, CatalogIds.SQ_HIGH) && slot.technique == TechniqueModifier.PAUSE_2S
            },
            "DL + TO_KNEES".takeIf { slot.lift.configurationId == CatalogIds.DL && slot.technique == TechniqueModifier.TO_KNEES },
            "LAT + CLOSE_GRIP".takeIf { slot.lift.configurationId == CatalogIds.LAT && slot.technique == TechniqueModifier.CLOSE_GRIP },
        )
        val offenders = recipes.flatMap { recipe ->
            slotsOf(recipe).flatMap { slot -> patchesOf(slot).map { "${recipe.id}/${slot.id}: $it" } }.distinct()
        }
        assertTrue("Recetas publicadas con la técnica parche retirada: $offenders", offenders.isEmpty())

        fun configurationsOf(recipe: TrainingPlanRecipe): Set<String> = slotsOf(recipe).map { it.lift.configurationId }.toSet()
        assertTrue(CatalogIds.BP_CLOSE_GRIP in configurationsOf(recipeOf("nsuns-531-lp-4d")))
        assertTrue(CatalogIds.BP_CLOSE_GRIP in configurationsOf(recipeOf("wendler-531-bbb")))
        assertTrue(CatalogIds.SQ_PAUSED in configurationsOf(recipeOf("calgary-16")))
        assertTrue(CatalogIds.DL_TO_KNEES in configurationsOf(recipeOf("sheiko-29-32")))
        assertTrue(CatalogIds.SQ_PAUSED in configurationsOf(templateRecipe("power-20-5")))
        assertTrue(CatalogIds.LAT_CLOSE_GRIP in configurationsOf(AuthoredPhulPhatRecipes.phatOriginal))
        assertTrue(CatalogIds.CURL_INCLINE in configurationsOf(AuthoredPhulPhatRecipes.phulOriginal))
        // El peso muerto hasta la rodilla es una variante: no compite.
        assertTrue(slotsOf(recipeOf("sheiko-29-32")).filter { it.lift.configurationId == CatalogIds.DL_TO_KNEES }.none { it.isCompetitionLift })
    }

    @Test
    fun the_corrected_recipes_have_no_hard_composition_findings_with_their_declared_exemptions() {
        val ids = listOf(
            "madcow-5x5", "texas-method-3d", "texas-method-4d", "kpkn-native-sbd-4", "lilliebridge", "smolov", "smolov-jr",
            "wendler-531-bbb", "wendler-531-fsl", "nsuns-531-lp-4d", "kpkn-ppl-6", "kpkn-rp-style", "gzclp", "gzcl-uhf-9",
            "westside-conjugate", "calgary-16", "tsa-9", "candito-6", "cube-method", "kpkn-rts-style", "sheiko-29-32",
            "coan-phillipi-dl", "korte-3x3",
        )
        val failures = ids.mapNotNull { id ->
            val hard = ProgramRecipeValidator.hardFindings(recipeOf(id), metadata)
            if (hard.isEmpty()) null else "$id:\n" + hard.joinToString("\n") { "  ${it.rule} ${it.scope}: ${it.message}" }
        } + listOf("body-12-3", "power-20-5").mapNotNull { id ->
            val hard = ProgramRecipeValidator.hardFindings(templateRecipe(id), metadata)
            if (hard.isEmpty()) null else "$id:\n" + hard.joinToString("\n") { "  ${it.rule} ${it.scope}: ${it.message}" }
        }
        assertTrue("HARD sin cubrir:\n" + failures.joinToString("\n\n"), failures.isEmpty())
    }

    // ─── §10.2/§10.3: tablas normativas de los originales autorados (E) ──────

    private val phul get() = AuthoredPhulPhatRecipes.phulOriginal
    private val phat get() = AuthoredPhulPhatRecipes.phatOriginal

    private fun workingSets(day: DayRecipe): List<SetRecipe> = day.slots.flatMap { slot -> slot.sets.filter { !it.isWarmup } }

    private fun setTotals(recipe: TrainingPlanRecipe): List<Int> = recipe.weeks.first().days.map { workingSets(it).size }

    private fun repRanges(day: DayRecipe): List<Pair<Int?, Int?>> = day.slots.map { slot ->
        val set = slot.sets.first { !it.isWarmup }
        set.repsMin to set.repsMax
    }

    private fun setCounts(day: DayRecipe): List<Int> = day.slots.map { slot -> slot.sets.count { !it.isWarmup } }

    @Test
    fun phul_original_matches_the_muscle_and_strength_table() {
        // §10.2: 12 semanas, 4 días (lunes, martes, jueves, viernes).
        assertEquals(12, phul.weeks.size)
        assertEquals(4, phul.claimedDaysPerWeek)
        assertEquals("intermedio", phul.claimedLevel)
        assertEquals(listOf(1, 2, 4, 5), phul.weeks.first().days.map { it.weekday })
        assertEquals(
            listOf("Superior fuerza", "Inferior fuerza", "Superior hipertrofia", "Inferior hipertrofia"),
            phul.weeks.first().days.map { it.label },
        )
        // Oráculos independientes: series iniciales 18 / 16 / 21 / 18.
        assertEquals(listOf(18, 16, 21, 18), setTotals(phul))
        // Orden exacto de la tabla, día a día.
        assertEquals(listOf("bp", "inc-db", "row", "lat", "ohp", "curl", "skull"), phul.weeks.first().days[0].slots.map { it.id })
        assertEquals(listOf("sq", "dl", "press", "curl", "calf"), phul.weeks.first().days[1].slots.map { it.id })
        assertEquals(
            listOf("bp-inc", "fly", "row-cable", "row-db", "lat-raise", "curl-inc", "tri-polea"),
            phul.weeks.first().days[2].slots.map { it.id },
        )
        assertEquals(
            listOf("sq-front", "lunge", "ext", "curl", "calf-sit", "calf-press"),
            phul.weeks.first().days[3].slots.map { it.id },
        )
        // Series por slot = mínimo de cada rango publicado.
        assertEquals(listOf(3, 3, 3, 3, 2, 2, 2), setCounts(phul.weeks.first().days[0]))
        assertEquals(listOf(3, 3, 3, 3, 4), setCounts(phul.weeks.first().days[1]))
        assertEquals(listOf(3, 3, 3, 3, 3, 3, 3), setCounts(phul.weeks.first().days[2]))
        assertEquals(listOf(3, 3, 3, 3, 3, 3), setCounts(phul.weeks.first().days[3]))
        // Rangos de repeticiones publicados, día a día.
        assertEquals(
            listOf(
                (3 to 5), (6 to 10), (3 to 5), (6 to 10), (5 to 8), (6 to 10), (6 to 10),
            ),
            repRanges(phul.weeks.first().days[0]),
        )
        assertEquals(listOf((3 to 5), (3 to 5), (10 to 15), (6 to 10), (6 to 10)), repRanges(phul.weeks.first().days[1]))
        assertEquals(List(7) { 8 to 12 }, repRanges(phul.weeks.first().days[2]))
        assertEquals(
            listOf((8 to 12), (8 to 12), (10 to 15), (10 to 15), (8 to 12), (8 to 12)),
            repRanges(phul.weeks.first().days[3]),
        )
    }

    @Test
    fun phul_original_prescribes_no_percent_no_training_max_and_no_invented_speed() {
        val allDays = phul.weeks.flatMap { it.days }
        val allSets = allDays.flatMap { workingSets(it) }
        // Sin %1RM/%TM: ningún set lleva porcentaje ni base de porcentaje.
        assertTrue("PHUL no publica porcentajes", allSets.all { it.percent == null })
        assertTrue(
            "PHUL no usa bases de porcentaje",
            allSets.none { it.loadBasis in setOf(LoadBasis.PERCENT_TM, LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX, LoadBasis.PERCENT_OF_TOP_SET, LoadBasis.REP_MAX) },
        )
        assertTrue("PHUL no tiene referencias de carga porcentual", allSets.none { it.reference != null })
        // Ni calentamientos de % (la fuente no los publica): la aproximación es
        // el preset operativo KPKN, declarado en `operationalDefaults`.
        assertTrue(allDays.flatMap { it.slots }.flatMap { it.sets }.none { it.isWarmup })
        // Sin SPEED inventado.
        assertTrue(allDays.flatMap { it.slots }.none { it.role == SlotRole.SPEED })
        // Esfuerzo inicial: RIR 2 en todos los sets (fuente: al menos 1 rep en reserva).
        assertTrue(allSets.all { it.rir == 2 })
        // Sin mapa SBD genérico (§14.3): auditoría por configuración/slot.
        assertTrue(phul.liftSlots.isEmpty())
        assertTrue(allDays.flatMap { it.slots }.all { it.lift.liftSlot == null })
        // §10.2: principales sin sustitución automática DENTRO del Original.
        assertEquals(
            setOf(CatalogIds.BP, CatalogIds.SQ_HIGH, CatalogIds.DL, CatalogIds.SQ_FRONT, CatalogIds.OHP),
            allDays.flatMap { it.slots }.filter { it.isCompetitionLift }.map { it.lift.configurationId }.toSet(),
        )
    }

    @Test
    fun phat_original_matches_the_biolayne_table() {
        // §10.3: ventana de 6 semanas SIN semana 7 fabricada, 5 días.
        assertEquals(6, phat.weeks.size)
        assertEquals(5, phat.claimedDaysPerWeek)
        assertEquals("avanzado", phat.claimedLevel)
        assertEquals(listOf(1, 2, 4, 5, 6), phat.weeks.first().days.map { it.weekday })
        assertEquals(
            listOf("Superior fuerza", "Inferior fuerza", "Espalda/hombros hipertrofia", "Inferior hipertrofia", "Pecho/brazos hipertrofia"),
            phat.weeks.first().days.map { it.label },
        )
        // Oráculos: 21 / 17 / 24 / 28 / 28 series incluyendo SPEED.
        assertEquals(listOf(21, 17, 24, 28, 28), setTotals(phat))
        // Orden exacto de la tabla, día a día.
        assertEquals(listOf("row-pendlay", "pullup", "rack-chin", "bench-db", "dips", "press-db", "curl-ez", "skull"), phat.weeks.first().days[0].slots.map { it.id })
        assertEquals(listOf("sq", "hack", "ext", "sldl", "curl-lying", "calf-stand", "calf-sit"), phat.weeks.first().days[1].slots.map { it.id })
        assertEquals(
            listOf("speed-row", "rack-chin", "row-cable", "row-cs", "lat-close", "press-db", "upright", "lat-raise"),
            phat.weeks.first().days[2].slots.map { it.id },
        )
        assertEquals(
            listOf("speed-sq", "hack", "press", "ext", "rdl", "curl-lying", "curl-seated", "calf-donkey", "calf-sit"),
            phat.weeks.first().days[3].slots.map { it.id },
        )
        assertEquals(
            listOf("speed-bp", "inc-db", "hammer", "fly-inc", "preacher", "conc", "spider", "overhead-ez", "pushdown", "kickback"),
            phat.weeks.first().days[4].slots.map { it.id },
        )
        assertEquals(listOf(3, 2, 2, 3, 2, 3, 3, 3), setCounts(phat.weeks.first().days[0]))
        assertEquals(listOf(3, 2, 2, 3, 2, 3, 2), setCounts(phat.weeks.first().days[1]))
        assertEquals(listOf(6, 3, 3, 2, 2, 3, 2, 3), setCounts(phat.weeks.first().days[2]))
        assertEquals(listOf(6, 3, 2, 3, 3, 2, 2, 4, 3), setCounts(phat.weeks.first().days[3]))
        assertEquals(listOf(6, 3, 3, 2, 3, 2, 2, 3, 2, 2), setCounts(phat.weeks.first().days[4]))
        // RIR inicial: 2 en semanas 1–4; 1 (dentro del rango 1–2) en 5–6.
        (1..4).forEach { week ->
            val sets = phat.weeks[week - 1].days.flatMap { workingSets(it) }.filter { it.percent == null }
            assertTrue("semana $week sin RIR 2", sets.all { it.rir == 2 })
        }
        (5..6).forEach { week ->
            val sets = phat.weeks[week - 1].days.flatMap { workingSets(it) }.filter { it.percent == null }
            assertTrue("semana $week fuera del rango 1–2", sets.all { it.rir == 1 || it.rir == 2 })
        }
        // Sin %TM/%1RM en ninguna parte: los únicos porcentajes son los SPEED.
        val percentSets = phat.weeks.flatMap { it.days }.flatMap { workingSets(it) }.filter { it.percent != null }
        assertEquals("3 bloques SPEED × 6 series × 6 semanas", 108, percentSets.size)
        assertTrue(
            "Ninguna base de porcentaje de autor/1RM/TM",
            percentSets.none { it.loadBasis in setOf(LoadBasis.PERCENT_TM, LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX, LoadBasis.PERCENT_OF_TOP_SET, LoadBasis.REP_MAX) },
        )
        assertTrue(phat.liftSlots.isEmpty())
    }

    @Test
    fun phat_speed_blocks_sit_on_the_three_hypertrophy_days_at_65_percent_of_the_same_exercise() {
        val days = phat.weeks.first().days
        val heavyById = days.flatMap { day -> day.slots.filter { it.role != SlotRole.SPEED } }
            .associate { it.id to it.lift.configurationId }
        val speedSlots = days.flatMap { day -> day.slots.filter { it.role == SlotRole.SPEED }.map { day to it } }
        // Exactamente tres bloques SPEED, al INICIO de los tres días de hipertrofia.
        assertEquals(3, speedSlots.size)
        assertEquals(listOf(4, 5, 6), speedSlots.map { (day, _) -> day.weekday })
        speedSlots.forEach { (day, slot) ->
            assertEquals("SPEED primero en ${day.label}", slot.id, day.slots.first().id)
            assertEquals(6, slot.sets.size)
            assertTrue(slot.sets.all { it.reps == 3 })
            assertEquals("65 % del rango 65–70 %", AuthoredPhulPhatRecipes.SPEED_PERCENT, slot.sets.first().percent!!, 0.0001)
            assertTrue(slot.sets.all { it.percent == AuthoredPhulPhatRecipes.SPEED_PERCENT })
            assertEquals(TechniqueModifier.SPEED, slot.technique)
            assertEquals(SlotPriority.SPEED, slot.priority)
            // Misma configuración técnica que el pesado enlazado (§14.2).
            val reference = requireNotNull(slot.sets.first().reference) { "${slot.id} sin referencia" }
            assertEquals(PlanLoadReferenceKind.OBSERVED_WORKING_SET, reference.kind)
            assertEquals(3, reference.repMin)
            assertEquals(5, reference.repMax)
            assertEquals(slot.lift.configurationId, reference.configurationId)
            assertEquals(PlanLoadReferenceState.PENDING, reference.state)
            val sourceSlotId = requireNotNull(reference.sourceSlotId)
            assertEquals(
                "El SPEED enlaza el pesado de SU ejercicio (§14.2)",
                slot.lift.configurationId,
                heavyById[sourceSlotId],
            )
            assertEquals(slot.lift.configurationId, slot.explicitReference?.configurationId)
            assertEquals(90, slot.restSeconds)
        }
        // Ni SPEED en los dos días de fuerza.
        assertTrue(days.filter { it.weekday in setOf(1, 2) }.flatMap { it.slots }.none { it.role == SlotRole.SPEED })
        // Descansos publicados por rol: 240 s en los pesados 3–5 iniciales.
        val heavyT1 = days.flatMap { it.slots }.filter { it.role == SlotRole.T1_MAIN }
        assertTrue(heavyT1.all { it.restSeconds >= 240 })
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
