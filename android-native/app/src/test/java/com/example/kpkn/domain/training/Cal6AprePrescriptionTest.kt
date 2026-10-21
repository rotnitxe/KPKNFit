package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.CalibrationScope
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.domain.training.Cal5TestSupport.exerciseLog
import com.example.kpkn.domain.training.Cal5TestSupport.exerciseOf
import com.example.kpkn.domain.training.Cal5TestSupport.recipeOf
import com.example.kpkn.domain.training.Cal5TestSupport.weeksOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * CAL6 · APRE 3 / 6 / 10 de Mann (RES1 §2): la tabla de ajuste en porcentaje con redondeo al material (serie 3 → serie 4 y serie 4 → XRM de la
 * semana siguiente), la equivalencia XRM ↔ 1RM, el «Bloque APRE» del editor y el protocolo del catálogo.
 */
class Cal6AprePrescriptionTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    // ─── La tabla de Mann ────────────────────────────────────────────────────────

    private fun bands(variant: ApreVariant, repsList: List<Int>): List<Apre.Band> = repsList.map { Apre.band(variant, it) }

    @Test
    fun the_apre_10_table_in_its_limit_cases() {
        // 4-6: −5 a −10 lb · 7-8: −0 a −5 · 9-11: mantener · 12-16: +5 a +10 · 17+: +10 a +15. Menos de 4 no lo define la fuente: la bajada más grande.
        assertEquals(List(5) { Apre.Band.DROP_LARGE }, bands(ApreVariant.APRE_10, listOf(0, 3, 4, 5, 6)))
        assertEquals(List(2) { Apre.Band.DROP_SMALL }, bands(ApreVariant.APRE_10, listOf(7, 8)))
        assertEquals(List(3) { Apre.Band.HOLD }, bands(ApreVariant.APRE_10, listOf(9, 10, 11)))
        assertEquals(List(5) { Apre.Band.RAISE_SMALL }, bands(ApreVariant.APRE_10, listOf(12, 13, 14, 15, 16)))
        assertEquals(List(3) { Apre.Band.RAISE_LARGE }, bands(ApreVariant.APRE_10, listOf(17, 18, 40)))
    }

    @Test
    fun the_apre_6_table_in_its_limit_cases() {
        // 0-2: −5 a −10 lb · 3-4: −0 a −5 · 5-7: mantener · 8-12: +5 a +10 · 13+: +10 a +15.
        assertEquals(List(3) { Apre.Band.DROP_LARGE }, bands(ApreVariant.APRE_6, listOf(0, 1, 2)))
        assertEquals(List(2) { Apre.Band.DROP_SMALL }, bands(ApreVariant.APRE_6, listOf(3, 4)))
        assertEquals(List(3) { Apre.Band.HOLD }, bands(ApreVariant.APRE_6, listOf(5, 6, 7)))
        assertEquals(List(5) { Apre.Band.RAISE_SMALL }, bands(ApreVariant.APRE_6, listOf(8, 9, 10, 11, 12)))
        assertEquals(List(2) { Apre.Band.RAISE_LARGE }, bands(ApreVariant.APRE_6, listOf(13, 25)))
    }

    @Test
    fun the_apre_3_table_in_its_limit_cases() {
        // 1-2: −5 a −10 lb (0 no lo define la fuente: igual) · 3-4: mantener · 5-6: +5 a +10 · 7+: +10 a +15.
        assertEquals(List(3) { Apre.Band.DROP_LARGE }, bands(ApreVariant.APRE_3, listOf(0, 1, 2)))
        assertEquals(List(2) { Apre.Band.HOLD }, bands(ApreVariant.APRE_3, listOf(3, 4)))
        assertEquals(List(2) { Apre.Band.RAISE_SMALL }, bands(ApreVariant.APRE_3, listOf(5, 6)))
        assertEquals(List(2) { Apre.Band.RAISE_LARGE }, bands(ApreVariant.APRE_3, listOf(7, 15)))
    }

    @Test
    fun negative_reps_count_as_zero_and_the_bands_become_percent_over_the_300_lb_example() {
        assertEquals(Apre.band(ApreVariant.APRE_6, 0), Apre.band(ApreVariant.APRE_6, -4))
        // ±5 lb = 1,7 %, ±10 lb = 3,3 %, ±15 lb = 5 % de 300 lb: cada banda vale el punto medio de su rango en libras.
        assertEquals(-2.5, Apre.Band.DROP_LARGE.percent, 1e-9)
        assertEquals(-2.5 / 3.0, Apre.Band.DROP_SMALL.percent, 1e-9)
        assertEquals(0.0, Apre.Band.HOLD.percent, 0.0)
        assertEquals(2.5, Apre.Band.RAISE_SMALL.percent, 1e-9)
        assertEquals(12.5 / 3.0, Apre.Band.RAISE_LARGE.percent, 1e-9)
    }

    @Test
    fun the_next_load_rounds_to_the_material_and_holds_without_moving_off_step_loads() {
        // APRE 6 con 100 kg y saltos de 2,5 kg.
        fun next(reps: Int, load: Double = 100.0) = Apre.nextLoadKg(load, reps, ApreVariant.APRE_6)
        assertEquals("13 reps: +4,17 % = 104,17 → 105", 105.0, next(13), 1e-9)
        assertEquals("8 reps: +2,5 % = 102,5", 102.5, next(8), 1e-9)
        assertEquals("5 reps: mantener", 100.0, next(5), 1e-9)
        assertEquals("mantener no redondea una carga fuera de salto", 101.3, next(6, 101.3), 1e-9)
        assertEquals("4 reps: −0,83 % = 99,17 → 100", 100.0, next(4), 1e-9)
        assertEquals("2 reps: −2,5 % = 97,5", 97.5, next(2), 1e-9)
        assertEquals("un material de 0,5 kg", 102.5, Apre.nextLoadKg(100.0, 8, ApreVariant.APRE_6, incrementKg = 0.5), 1e-9)
        assertEquals("sin carga no hay carga siguiente", 0.0, Apre.nextLoadKg(0.0, 8, ApreVariant.APRE_6), 0.0)
    }

    @Test
    fun the_reps_with_bad_technique_do_not_count() {
        // Mann: 13 repeticiones con 2 de mala técnica son 11 válidas.
        assertEquals(11, Apre.validReps(13, 2))
        assertEquals(0, Apre.validReps(3, 5))
        assertEquals(7, Apre.validReps(7, -1))
        // Con 13 reps la serie 3 de APRE 6 sube; contadas como 11 es la franja de subida pequeña (8-12), no la grande.
        assertEquals(Apre.Band.RAISE_LARGE, Apre.band(ApreVariant.APRE_6, 13))
        assertEquals(Apre.Band.RAISE_SMALL, Apre.band(ApreVariant.APRE_6, Apre.validReps(13, 2)))
    }

    @Test
    fun set_3_fixes_set_4_and_set_4_fixes_next_weeks_xrm_from_the_load_of_set_4() {
        // Serie 3 de un 6RM de 137,5 kg con 9 repeticiones: +2,5 % → 140,9 → 140; con ese peso, 5 repeticiones en la serie 4 (mantener) → XRM 140 la semana siguiente.
        val set4 = Apre.set4LoadKg(137.5, 9, ApreVariant.APRE_6)
        assertEquals(140.0, set4, 1e-9)
        assertEquals(140.0, Apre.nextWeekXrmKg(set4, 5, ApreVariant.APRE_6), 1e-9)
        // El ejemplo de Mann: la serie 4 sube y sus repeticiones la suben otra vez (semana 2 parte de la carga de la serie 4 más el ajuste).
        assertEquals(142.5, Apre.nextWeekXrmKg(set4, 9, ApreVariant.APRE_6), 1e-9)
    }

    // ─── La equivalencia XRM ↔ 1RM y el Bloque APRE ──────────────────────────────

    @Test
    fun the_xrm_comes_from_the_one_rm_with_the_mann_equivalence() {
        assertEquals(75.0, ApreVariant.APRE_10.oneRmFraction * 100, 1e-9)
        assertEquals(83.0, ApreVariant.APRE_6.oneRmFraction * 100, 1e-9)
        assertEquals(92.0, ApreVariant.APRE_3.oneRmFraction * 100, 1e-9)
        assertEquals("10RM = 75 % de 200, redondeado a 2,5", 150.0, Apre.xrmKg(200.0, ApreVariant.APRE_10), 1e-9)
        assertEquals("6RM = 83 % de 200 = 166 → 165", 165.0, Apre.xrmKg(200.0, ApreVariant.APRE_6), 1e-9)
        assertEquals("3RM = 92 % de 200 = 184 → 185", 185.0, Apre.xrmKg(200.0, ApreVariant.APRE_3), 1e-9)
        assertEquals(0.0, Apre.xrmKg(0.0, ApreVariant.APRE_6), 0.0)
    }

    @Test
    fun the_apre_block_creates_the_four_sets_from_a_one_rm() {
        val block = Apre.block(200.0, ApreVariant.APRE_6)
        assertEquals(4, block.size)
        assertEquals(listOf(1, 2, 3, 4), block.map { it.ordinal })
        // 6RM = 165: serie 1 al 50 % (82,5) × 10, serie 2 al 75 % (123,75 → 125) × 6, y dos series al máximo con 165.
        assertEquals(listOf(82.5, 125.0, 165.0, 165.0), block.map { it.loadKg })
        assertEquals(listOf(10, 6, null, null), block.map { it.targetReps })
        assertEquals(listOf(null, null, 6, 6), block.map { it.minimumReps })
        assertEquals(listOf(50.0, 75.0, 100.0, 100.0), block.map { it.percentOfXrm })
        // La serie 3 es la calibradora de la sesión (fija la carga de la serie 4) y la 4 es AMRAP (sus repeticiones proponen el XRM).
        assertEquals(listOf(SetPurpose.NONE, SetPurpose.NONE, SetPurpose.CALIBRATOR, SetPurpose.AMRAP), block.map { it.purpose })
        assertEquals(CalibrationScope.SESSION, block[2].calibrationScope)
        assertNull(block[3].calibrationScope)

        val apre10 = Apre.block(100.0, ApreVariant.APRE_10)
        assertEquals(listOf(37.5, 57.5, 75.0, 75.0), apre10.map { it.loadKg })
        assertEquals(listOf(12, 10), apre10.take(2).map { it.targetReps })
        val apre3 = Apre.block(200.0, ApreVariant.APRE_3)
        assertEquals(listOf(92.5, 140.0, 185.0, 185.0), apre3.map { it.loadKg })
        assertEquals(listOf(6, 3), apre3.take(2).map { it.targetReps })
    }

    @Test
    fun the_apre_block_without_a_reference_is_empty() {
        assertTrue(Apre.block(0.0, ApreVariant.APRE_6).isEmpty())
        assertTrue(Apre.block(-20.0, ApreVariant.APRE_6).isEmpty())
        assertTrue(Apre.block(Double.NaN, ApreVariant.APRE_6).isEmpty())
    }

    // ─── El protocolo del catálogo ───────────────────────────────────────────────

    private val protocol = PROTOCOL_LIBRARY.first { it.id == "apre-mann" }
    private val recipe = recipeOf("apre-mann")

    @Test
    fun the_protocol_is_published_with_its_recipe_and_its_sources() {
        assertTrue(protocol.isVisibleForApplication)
        assertEquals("Bryan Mann", protocol.author)
        assertEquals("No afiliado a Bryan Mann", protocol.source.disclaimer)
        assertEquals(12, recipe.weeks.size)
        assertEquals(3, recipe.daysPerWeek)
        assertEquals(ProgressionRule.ApreXrm(incrementKg = 2.5), recipe.progression)
        assertEquals(0.92, recipe.trainingMaxPercent, 1e-9)
        assertEquals(setOf(LiftSlot.SQUAT, LiftSlot.BENCH, LiftSlot.DEADLIFT), recipe.liftSlots.keys)
    }

    @Test
    fun each_lift_does_the_four_sets_with_the_xrm_of_its_phase() {
        val expected = mapOf(1..4 to ApreVariant.APRE_10, 5..8 to ApreVariant.APRE_6, 9..12 to ApreVariant.APRE_3)
        expected.forEach { (weeks, variant) ->
            weeks.forEach { w ->
                recipe.weeks.first { it.weekNumber == w }.days.forEach { day ->
                    val t1 = day.slots.single { it.role == SlotRole.T1_MAIN }
                    assertEquals("semana $w", 4, t1.sets.size)
                    assertEquals(listOf(variant.set1Reps, variant.set2Reps, variant.xrmReps, variant.xrmReps), t1.sets.map { it.reps })
                    assertEquals("las dos últimas van al máximo", listOf(false, false, true, true), t1.sets.map { it.amrap })
                    // El XRM de la serie 3 es la fracción del 1RM de la fase (porcentaje del TM × 0,92 = fracción del 1RM).
                    assertEquals(variant.oneRmFraction, t1.sets[2].percent!! / 100.0 * recipe.trainingMaxPercent, 1e-9)
                    assertEquals(0.50 * variant.oneRmFraction, t1.sets[0].percent!! / 100.0 * recipe.trainingMaxPercent, 1e-9)
                    assertEquals(0.75 * variant.oneRmFraction, t1.sets[1].percent!! / 100.0 * recipe.trainingMaxPercent, 1e-9)
                }
            }
        }
    }

    @Test
    fun the_series_4_proposes_the_xrm_of_next_week_and_the_next_week_uses_it() {
        val profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0)
        val program = Cal5TestSupport.materialize(recipe, AutoregulationMode.AUTO, profile, id = "apre")
        val weeks = weeksOf(program)
        val week1 = weeks.first()
        val squatDay = week1.sessions.first()
        val t1 = exerciseOf(squatDay, CatalogIds.SQ_LOW)
        // Semana 1 (APRE 10): el 10RM de sentadilla es el 75 % del 1RM = 150 kg en las dos series al máximo.
        assertEquals(150.0, t1.sets[2].weight!!, 1e-6)
        assertEquals(150.0, t1.sets[3].weight!!, 1e-6)

        // Serie 3: 12 repeticiones con 150 → la serie 4 va a 155 (+2,5 %). Serie 4: 17 repeticiones con 155 → XRM de la semana siguiente 162,5.
        assertEquals(155.0, Apre.set4LoadKg(150.0, 12, ApreVariant.APRE_10), 1e-9)
        val log = exerciseLog(
            program, week1, squatDay, t1,
            weightOf = { index, planned -> if (index == 3) 155.0 else planned.weight },
            repsOf = { index, planned -> when (index) { 2 -> 12; 3 -> 17; else -> planned.targetReps ?: 10 } },
        )
        val proposals = ProgramAutoregulationEngine.evaluate(
            program, week1, listOf(log), recipe, WeeklyAutoregulationSignals(readinessScore = 80),
        ).filter { it.kind == AutoregulationProposalKind.ADJUST_TM }
        val proposal = proposals.single()
        assertEquals("SQUAT", proposal.liftSlot)
        assertEquals("162,5 sobre los 150 kg prescritos: +8,33 %", (162.5 / 150.0 - 1.0) * 100.0, proposal.percentDelta!!, 1e-6)
        assertTrue(proposal.explanation, proposal.explanation.contains("tu 10RM de la semana próxima es 162,5 kg"))

        val applied = ProgramAutoregulationEngine.apply(
            program, weeks[1].id, proposals, recipe, setOf(week1.id), CatalogCompositionTestSupport.metadata,
        )
        assertTrue(applied.applied)
        // La semana 2 (APRE 10) parte del XRM propuesto: sus dos series al máximo van a 162,5 kg.
        val nextWeekSquat = exerciseOf(weeksOf(applied.program)[1].sessions.first(), CatalogIds.SQ_LOW)
        assertEquals(162.5, nextWeekSquat.sets[2].weight!!, 1e-6)
        assertEquals(162.5, nextWeekSquat.sets[3].weight!!, 1e-6)
        // La serie 1 sigue al 50 % del XRM nuevo.
        assertEquals(81.25, nextWeekSquat.sets[0].weight!!, 1e-6)
    }

    @Test
    fun only_the_last_amrap_of_the_exercise_proposes_and_the_modes_are_respected() {
        val profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0)
        // Una serie 3 sola (no es la última AMRAP del ejercicio) no genera ninguna propuesta de XRM.
        val set3Only = AmrapHit(LiftSlot.SQUAT, 81.52, prescribedReps = 10, actualReps = 17, weightKg = 150.0, isLastAmrapOfExercise = false)
        val propose = Cal5TestSupport.materialize(recipe, AutoregulationMode.PROPOSE, profile, id = "apre-p")
        val week = weeksOf(propose).first()
        assertTrue(
            ProgramAutoregulationEngine.evaluate(propose, week, emptyList(), recipe, WeeklyAutoregulationSignals(amrapHits = listOf(set3Only), readinessScore = 80))
                .none { it.kind == AutoregulationProposalKind.ADJUST_TM },
        )
        val set4 = set3Only.copy(isLastAmrapOfExercise = true, weightKg = 155.0)
        val signals = WeeklyAutoregulationSignals(amrapHits = listOf(set4), readinessScore = 80)
        assertTrue(ProgramAutoregulationEngine.evaluate(Cal5TestSupport.materialize(recipe, AutoregulationMode.OFF, profile, "apre-o"), week, emptyList(), recipe, signals).isEmpty())
        val proposals = ProgramAutoregulationEngine.evaluate(propose, week, emptyList(), recipe, signals)
        assertFalse(proposals.isEmpty())
        val pending = ProgramAutoregulationEngine.apply(
            propose, weeksOf(propose)[1].id, proposals, recipe, setOf(week.id), CatalogCompositionTestSupport.metadata,
        )
        assertFalse(pending.applied)
        assertEquals("PROPONER no toca el TM", 184.0, pending.program.powerliftingProfile!!.squatTM!!, 1e-9)
    }

    @Test
    fun the_xrm_of_a_hit_outside_apre_is_ignored() {
        // Un AMRAP cuyo mínimo no es 10, 6 o 3 no es una serie de APRE: sin XRM no hay propuesta.
        val profile = PowerliftingProfile(squat1RM = 200.0)
        val odd = AmrapHit(LiftSlot.SQUAT, 81.52, prescribedReps = 8, actualReps = 12, weightKg = 150.0)
        assertNull(ProgramAutoregulationEngine.apreProposal(recipe, ProgressionRule.ApreXrm(), profile, odd, LiftSlot.SQUAT))
    }
}
