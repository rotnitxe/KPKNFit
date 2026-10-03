package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
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
