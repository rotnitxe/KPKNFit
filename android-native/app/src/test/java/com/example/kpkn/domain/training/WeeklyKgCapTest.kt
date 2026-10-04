package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * B.S6 parte 2a · H10: tope de seguridad de `WeeklyKg`.
 *
 * El kilo semanal del método (Smolov y Smolov Jr) se suma a la carga resuelta de la serie principal. Sin
 * tope, con un 1RM corto la serie pasaba del 100 %: Smolov con 1RM de 60 kg, semana 5, S4 (10×3 al 85 %):
 * 51 + 15 = 66 kg. Con el tope, `PlanMaterializer.materializeSet` limita la carga al 1RM de referencia y
 * deja `targetPercentageRM` en la misma base que el resto de la serie.
 *
 * H11 y H11b de `SessionCompositionPolicy` miden el porcentaje CRUDO de la receta (85 %); este tope cubre
 * el hueco en ejecución, donde se conocen el 1RM y el kilo de la semana. `ExerciseSet` no tiene un campo de
 * nota o marca, así que una serie limitada se reconoce por quedar exactamente en el 1RM.
 */
class WeeklyKgCapTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    private fun smolovRecipe(): TrainingPlanRecipe = PROTOCOL_LIBRARY.first { it.id == "smolov" }.recipe!!

    private fun smolovJrRecipe(): TrainingPlanRecipe = PROTOCOL_LIBRARY.first { it.id == "smolov-jr" }.recipe!!

    private fun materialize(recipe: TrainingPlanRecipe, profile: PowerliftingProfile?): Program =
        PlanMaterializer.materialize(
            Program(id = "wk", name = "WeeklyKg"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = profile,
            // Estas pruebas miden cargas, no la composición de la receta (la cubre ProtocolCompositionContractTest).
            strict = false,
        )

    private fun weeksOf(program: Program): List<ProgramWeek> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    /** Las series de la sentadilla principal (T1) del día [dayIndex] de [week]. */
    private fun squatSets(week: ProgramWeek, dayIndex: Int): List<ExerciseSet> =
        week.sessions[dayIndex].allExercises()
            .first { it.slotRole == SlotRole.T1_MAIN && it.catalogConfigurationId == CatalogIds.SQ_LOW }
            .sets

    /**
     * Smolov, semanas 4 (+10 kg) y 5 (+15 kg): S1 4×9 al 70 %, S2 5×7 al 75 %, S3 7×5 al 80 % y S4 10×3 al
     * 85 %. Devuelve el kilo de cada día (todas las series de un día pesan lo mismo).
     */
    private fun smolovKgByDay(program: Program, weekNumber: Int): List<Double> =
        (0..3).map { dayIndex ->
            val sets = squatSets(weeksOf(program)[weekNumber - 1], dayIndex)
            assertEquals(
                "semana $weekNumber día ${dayIndex + 1}: todas las series pesan lo mismo",
                1,
                sets.map { it.weight }.distinct().size,
            )
            sets.first().weight ?: -1.0
        }

    private fun smolovPercentByDay(program: Program, weekNumber: Int): List<Double> =
        (0..3).map { dayIndex -> squatSets(weeksOf(program)[weekNumber - 1], dayIndex).first().targetPercentageRM ?: -1.0 }

    private fun assertDoubles(label: String, expected: List<Double>, actual: List<Double>) {
        assertEquals("$label: cuántos valores", expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals("$label [${index + 1}]", expected[index], actual[index], 1e-9)
        }
    }

    @Test
    fun a_smolov_with_a_160_kg_one_rm_keeps_its_weekly_kilos_untouched() {
        val program = materialize(smolovRecipe(), PowerliftingProfile(squat1RM = 160.0))

        // Semana 5 (+15 kg): 70 % = 112 → 127; 75 % = 120 → 135; 80 % = 128 → 143; 85 % = 136 → 151.
        assertDoubles("semana 5", listOf(127.0, 135.0, 143.0, 151.0), smolovKgByDay(program, 5))
        // El % mostrado es el de la receta más el kilo sobre el 1RM: S4 queda en 85 + 15 ÷ 160 × 100 = 94,375 %.
        val s4 = squatSets(weeksOf(program)[4], 3)
        assertEquals(10, s4.size)
        s4.forEach { set ->
            assertEquals("S4 de la semana 5: 10×3 al 85 % + 15 kg", 151.0, set.weight!!, 1e-9)
            assertEquals("94,4 % del 1RM", 94.375, set.targetPercentageRM!!, 1e-9)
        }
        // Semana 4 (+10 kg).
        assertDoubles("semana 4", listOf(122.0, 130.0, 138.0, 146.0), smolovKgByDay(program, 4))
    }

    @Test
    fun a_smolov_with_a_60_kg_one_rm_never_prescribes_more_than_the_one_rm() {
        val program = materialize(smolovRecipe(), PowerliftingProfile(squat1RM = 60.0))

        // Semana 5 (+15 kg): 70 % = 42 → 57 (no se toca); 75 % = 45 → 60 (justo el 1RM, no se limita);
        // 80 % = 48 → 63 y 85 % = 51 → 66 se limitan a 60.
        assertDoubles("semana 5", listOf(57.0, 60.0, 60.0, 60.0), smolovKgByDay(program, 5))
        // Semana 4 (+10 kg): solo el 85 % pasa del 1RM (51 + 10 = 61).
        assertDoubles("semana 4", listOf(52.0, 55.0, 58.0, 60.0), smolovKgByDay(program, 4))

        // El % de una serie limitada es el 100 % del 1RM; las demás conservan receta + kilo ÷ 1RM.
        assertDoubles("% semana 5", listOf(95.0, 100.0, 100.0, 100.0), smolovPercentByDay(program, 5))
        assertDoubles(
            "% semana 4",
            listOf(70.0 + 10.0 / 60.0 * 100.0, 75.0 + 10.0 / 60.0 * 100.0, 80.0 + 10.0 / 60.0 * 100.0, 100.0),
            smolovPercentByDay(program, 4),
        )

        // Ninguna serie principal de toda la receta supera el 1RM.
        weeksOf(program).forEach { week ->
            week.sessions.flatMap { it.allExercises() }
                .filter { it.slotRole == SlotRole.T1_MAIN }
                .flatMap { it.sets }
                .forEach { set ->
                    assertTrue("${week.name}: ${set.weight} kg pasa del 1RM de 60 kg", (set.weight ?: 0.0) <= 60.0 + 1e-9)
                }
        }
    }

    @Test
    fun the_percent_shown_is_always_the_kilos_over_the_one_rm_limited_or_not() {
        val program = materialize(smolovRecipe(), PowerliftingProfile(squat1RM = 60.0))
        val week5 = weeksOf(program)[4]
        (0..3).forEach { dayIndex ->
            squatSets(week5, dayIndex).forEach { set ->
                // Con 1RM = TM, el % mostrado es kg ÷ 1RM × 100 (limitado o no).
                assertEquals("día ${dayIndex + 1}", set.weight!! / 60.0 * 100.0, set.targetPercentageRM!!, 1e-9)
            }
        }
    }

    @Test
    fun without_a_load_base_there_is_neither_a_cap_nor_kilos() {
        val program = materialize(smolovRecipe(), profile = null)

        (3..4).forEach { weekIndex ->
            (0..3).forEach { dayIndex ->
                val sets = squatSets(weeksOf(program)[weekIndex], dayIndex)
                assertTrue("sin 1RM no hay kg", sets.all { it.weight == null })
            }
        }
        // El porcentaje es el de la receta, sin kilo semanal que sumar ni tope que aplicar.
        assertDoubles("% semana 5", listOf(70.0, 75.0, 80.0, 85.0), smolovPercentByDay(program, 5))
        assertDoubles("% semana 4", listOf(70.0, 75.0, 80.0, 85.0), smolovPercentByDay(program, 4))
    }

    @Test
    fun smolov_jr_with_a_200_kg_one_rm_still_goes_140_145_150() {
        val program = materialize(smolovJrRecipe(), PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0))

        // Primera sesión (6×6 al 70 %) de las semanas 1 a 3: 140, +5 y +10 kg.
        assertDoubles(
            "primera sesión",
            listOf(140.0, 145.0, 150.0),
            (0..2).map { week -> squatSets(weeksOf(program)[week], 0).first().weight ?: -1.0 },
        )
        // Las cuatro sesiones de cada semana, sin tope alguno (el máximo, 85 % + 10 kg = 180 kg, queda bajo 200).
        val base = listOf(140.0, 150.0, 160.0, 170.0)
        listOf(0.0, 5.0, 10.0).forEachIndexed { weekIndex, offset ->
            base.indices.forEach { dayIndex ->
                squatSets(weeksOf(program)[weekIndex], dayIndex).forEach { set ->
                    assertEquals("semana ${weekIndex + 1} día ${dayIndex + 1}", base[dayIndex] + offset, set.weight!!, 1e-9)
                }
            }
        }
    }

    // ─── La referencia del tope es el 1RM, no el TM, y solo cubre el kilo semanal ──────

    /** Una semana con una sola serie de sentadilla al [percent] % del TM y `WeeklyKg` de +[offsetKg] en esa semana. */
    private fun singleSetRecipe(
        offsetKg: Double?,
        trainingMaxPercent: Double = 0.90,
        percent: Double = 100.0,
    ): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "weekly-kg-cap",
        weeks = listOf(
            weekRecipe(
                1, 0, "Semana", BlockGoal.INTENSIFICATION,
                listOf(
                    day(
                        "A",
                        weekday = 1,
                        slots = listOf(
                            slot(
                                "sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(180, 5 to percent), 180,
                                LiftSlot.SQUAT, isCompetitionLift = true,
                            ),
                        ),
                    ),
                ),
            ),
        ),
        trainingMaxPercent = trainingMaxPercent,
        liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW),
        progression = if (offsetKg != null) ProgressionRule.WeeklyKg(mapOf(1 to offsetKg)) else ProgressionRule.None,
    )

    private fun onlySet(recipe: TrainingPlanRecipe, profile: PowerliftingProfile?): ExerciseSet =
        squatSets(weeksOf(materialize(recipe, profile))[0], 0).single()

    @Test
    fun the_cap_is_the_one_rm_and_not_the_training_max() {
        // 1RM 100 con TM al 90 %: la serie al 100 % del TM son 90 kg.
        val profile = PowerliftingProfile(squat1RM = 100.0)

        // +5 kg: 95 kg pasan del TM (90) pero no del 1RM (100): no se limita.
        val within = onlySet(singleSetRecipe(offsetKg = 5.0), profile)
        assertEquals(95.0, within.weight!!, 1e-9)
        assertEquals("100 % del TM + 5 kg ÷ 90 kg", 100.0 + 5.0 / 90.0 * 100.0, within.targetPercentageRM!!, 1e-9)

        // +20 kg: 110 kg pasan del 1RM y se limitan a 100 kg; el % sigue la base de la serie (el TM): 100 ÷ 90.
        val capped = onlySet(singleSetRecipe(offsetKg = 20.0), profile)
        assertEquals(100.0, capped.weight!!, 1e-9)
        assertEquals(100.0 / 90.0 * 100.0, capped.targetPercentageRM!!, 1e-9)
    }

    @Test
    fun the_cap_only_covers_the_weekly_kilos_a_set_above_the_one_rm_without_them_is_untouched() {
        // Sin WeeklyKg, una serie al 105 % del TM (TM = 1RM = 100) sigue siendo exactamente eso: el tope no inventa nada.
        val recipe = singleSetRecipe(offsetKg = null, trainingMaxPercent = 1.0, percent = 105.0)
        val set = onlySet(recipe, PowerliftingProfile(squat1RM = 100.0))
        assertEquals(105.0, set.weight!!, 1e-9)
        assertEquals(105.0, set.targetPercentageRM!!, 1e-9)
    }

    @Test
    fun a_weekly_kilo_that_lands_exactly_on_the_one_rm_is_not_limited() {
        // TM = 1RM = 80: el 75 % son 60 kg y con +20 kg llegan justo al 1RM; no se limita ni se redondea.
        val recipe = singleSetRecipe(offsetKg = 20.0, trainingMaxPercent = 1.0, percent = 75.0)
        val set = onlySet(recipe, PowerliftingProfile(squat1RM = 80.0))
        assertEquals(80.0, set.weight!!, 1e-9)
        assertEquals(75.0 + 20.0 / 80.0 * 100.0, set.targetPercentageRM!!, 1e-9)
    }

    @Test
    fun without_a_profile_the_weight_stays_null_even_with_weekly_kg() {
        val set = onlySet(singleSetRecipe(offsetKg = 15.0), profile = null)
        assertNull(set.weight)
        assertEquals("el porcentaje es el de la receta", 100.0, set.targetPercentageRM!!, 1e-9)
    }
}
