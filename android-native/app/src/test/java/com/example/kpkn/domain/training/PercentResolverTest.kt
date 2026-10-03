package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.protocols.definitions.TexasMethodProtocols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PercentResolverTest {
    private fun ofTop(percent: Double, reps: Int = 5) =
        SetRecipe(reps = reps, percent = percent, loadBasis = LoadBasis.PERCENT_OF_TOP_SET)

    private fun topSet(percent: Double, reps: Int = 5) =
        SetRecipe(reps = reps, percent = percent, isTopSet = true, loadBasis = LoadBasis.PERCENT_OF_TOP_SET)

    private fun slotOf(id: String, sets: List<SetRecipe>, liftSlot: LiftSlot? = LiftSlot.SQUAT) = SlotRecipe(
        id = id,
        role = SlotRole.T1_MAIN,
        lift = LiftRef(CatalogIds.SQ_LOW, liftSlot),
        sets = sets,
        restSeconds = 180,
    )

    /** Una semana con un día por lista de slots, en el orden recibido. */
    private fun weekOf(vararg days: List<SlotRecipe>) = WeekRecipe(
        weekNumber = 1,
        blockIndex = 0,
        days = days.mapIndexed { index, slots -> DayRecipe(label = "D${index + 1}", slots = slots, weekday = index + 1) },
    )

    private fun assertAllClose(expected: Double, actual: List<Double>, size: Int) {
        assertEquals("series resueltas: $actual", size, actual.size)
        actual.forEach { assertEquals(expected, it, 1e-9) }
    }

    @Test
    fun sets_without_percent_resolve_to_null() {
        val set = SetRecipe(reps = 8, rir = 2, loadBasis = LoadBasis.RPE)
        val slot = slotOf("sq", listOf(set))
        assertNull(PercentResolver.resolve(set, slot, weekOf(listOf(slot))))
    }

    @Test
    fun bases_other_than_top_set_are_returned_untouched() {
        val sets = listOf(
            SetRecipe(reps = 5, percent = 75.0, loadBasis = LoadBasis.PERCENT_TM),
            SetRecipe(reps = 3, percent = 82.5, loadBasis = LoadBasis.PERCENT_1RM),
            SetRecipe(reps = 1, percent = 100.0, loadBasis = LoadBasis.PERCENT_DESIRED_MAX),
            SetRecipe(reps = 2, percent = 90.0, loadBasis = LoadBasis.REP_MAX),
        )
        val slot = slotOf("sq", sets)
        val week = weekOf(listOf(slot))
        assertEquals(listOf(75.0, 82.5, 100.0, 90.0), sets.map { PercentResolver.resolve(it, slot, week) })
    }

    @Test
    fun the_top_set_itself_is_returned_untouched() {
        val slot = slotOf("sq", listOf(topSet(102.5, reps = 3)))
        val week = weekOf(listOf(slot))
        assertEquals(102.5, PercentResolver.resolve(slot.sets.single(), slot, week)!!, 1e-9)
    }

    @Test
    fun texas_3d_resolves_friday_100_monday_90_wednesday_72_percent_of_tm() {
        val recipe = TexasMethodProtocols.recipe3d()
        recipe.weeks.forEach { week ->
            fun slotOn(weekday: Int, liftSlot: LiftSlot) =
                week.days.first { it.weekday == weekday }.slots.first { it.lift.liftSlot == liftSlot && it.role != SlotRole.T3_ACCESSORY }
            fun resolved(weekday: Int, liftSlot: LiftSlot): List<Double> {
                val slot = slotOn(weekday, liftSlot)
                return slot.sets.map { PercentResolver.resolve(it, slot, week)!! }
            }
            assertAllClose(90.0, resolved(1, LiftSlot.SQUAT), size = 5)
            assertAllClose(72.0, resolved(3, LiftSlot.SQUAT), size = 2)
            assertAllClose(100.0, resolved(5, LiftSlot.SQUAT), size = 1)
            assertAllClose(90.0, resolved(1, LiftSlot.BENCH), size = 5)
            assertAllClose(100.0, resolved(5, LiftSlot.BENCH), size = 1)
        }
    }

    @Test
    fun volume_factor_is_the_heaviest_working_set_not_the_first() {
        // Lunes: rampa 50 → 100 (cinco series de trabajo, sin top set): slot de volumen.
        val monday = slotOf("sq-mon", listOf(ofTop(50.0), ofTop(62.5), ofTop(75.0), ofTop(87.5), ofTop(100.0)))
        val wednesday = slotOf("sq-wed", listOf(ofTop(80.0), ofTop(80.0)))
        val friday = slotOf("sq-fri", listOf(topSet(100.0)))
        val week = weekOf(listOf(monday), listOf(wednesday), listOf(friday))
        // Con el factor del PRIMER set (50) el miércoles saldría a 40; con el más pesado (100) sale a 80.
        assertEquals(80.0, PercentResolver.resolve(wednesday.sets.first(), wednesday, week)!!, 1e-9)
        assertEquals(50.0, PercentResolver.resolve(monday.sets.first(), monday, week)!!, 1e-9)
        assertEquals(100.0, PercentResolver.resolve(monday.sets.last(), monday, week)!!, 1e-9)
    }

    @Test
    fun the_anchor_is_the_own_top_set_before_the_top_set_of_other_slots() {
        // Lunes: top set de otro slot del mismo levantamiento al 95.
        val monday = slotOf("sq-mon", listOf(topSet(95.0)))
        // Miércoles: slot con top set propio al 102,5 y una serie de espalda al 75.
        val wednesday = slotOf("sq-wed", listOf(topSet(102.5, reps = 3), ofTop(75.0, reps = 8)))
        // Viernes: slot de volumen (5 × 80, sin top set): su ancla es el primer top set de OTRO slot (95).
        val friday = slotOf("sq-fri", List(5) { ofTop(80.0) })
        val week = weekOf(listOf(monday), listOf(wednesday), listOf(friday))
        // Volumen: 80 ÷ 100 × 95 = 76.
        assertEquals(76.0, PercentResolver.resolve(friday.sets.first(), friday, week)!!, 1e-9)
        // La serie de espalda del miércoles usa el ancla propia (102,5) y el factor del slot de volumen (80):
        // 75 ÷ 100 × (80 ÷ 100 × 102,5) = 61,5.
        assertEquals(61.5, PercentResolver.resolve(wednesday.sets.last(), wednesday, week)!!, 1e-9)
    }

    @Test
    fun without_any_top_set_the_anchor_is_100_and_the_volume_factor_is_90() {
        val volume = slotOf("sq-vol", List(5) { ofTop(90.0) })
        val light = slotOf("sq-light", listOf(ofTop(80.0), ofTop(80.0)))
        // Solo el slot ligero: sin slot de volumen el factor es 90 → 80 ÷ 100 × (90 ÷ 100 × 100) = 72.
        assertEquals(72.0, PercentResolver.resolve(light.sets.first(), light, weekOf(listOf(light)))!!, 1e-9)
        // Solo el slot de volumen: sin top set el ancla es 100 → 90.
        assertEquals(90.0, PercentResolver.resolve(volume.sets.first(), volume, weekOf(listOf(volume)))!!, 1e-9)
    }

    @Test
    fun slots_without_lift_slot_have_no_sibling_slots() {
        val orphan = slotOf("sq-orphan", List(5) { ofTop(90.0) }, liftSlot = null)
        val otherTop = slotOf("sq-top", listOf(topSet(110.0)))
        val week = weekOf(listOf(otherTop), listOf(orphan))
        // El top set de otro slot no cuenta porque el orfano no declara levantamiento: ancla 100.
        assertEquals(90.0, PercentResolver.resolve(orphan.sets.first(), orphan, week)!!, 1e-9)
    }

    @Test
    fun effective_1rm_percent_converts_every_basis() {
        val slot = slotOf(
            "sq",
            listOf(
                SetRecipe(reps = 3, percent = 82.5, loadBasis = LoadBasis.PERCENT_1RM),
                SetRecipe(reps = 1, percent = 95.0, loadBasis = LoadBasis.PERCENT_DESIRED_MAX),
                SetRecipe(reps = 5, percent = 100.0, loadBasis = LoadBasis.PERCENT_TM),
                SetRecipe(reps = 8, rir = 2, loadBasis = LoadBasis.RPE),
                SetRecipe(reps = 2, percent = 90.0, loadBasis = LoadBasis.REP_MAX),
                SetRecipe(reps = 5, percent = 90.0, loadBasis = LoadBasis.RPE, rpe = 8.0),
            ),
        )
        val week = weekOf(listOf(slot))
        fun effective(index: Int, tm: Double) = PercentBasis.effective1RmPercent(slot.sets[index], slot, week, tm)
        assertEquals(82.5, effective(0, 0.87)!!, 1e-9)
        assertEquals(95.0, effective(1, 0.87)!!, 1e-9)
        assertEquals(87.0, effective(2, 0.87)!!, 1e-9)
        assertNull(effective(3, 0.87))
        assertNull(effective(4, 0.87))
        assertNull(effective(5, 0.87))
        // Una receta sin TM válido cae al 90 % de siempre.
        assertEquals(90.0, effective(2, 0.0)!!, 1e-9)
        assertEquals(90.0, effective(2, -1.0)!!, 1e-9)
    }

    @Test
    fun effective_1rm_percent_is_null_for_observed_working_set_and_bodyweight_references() {
        fun referenced(kind: PlanLoadReferenceKind, basis: LoadBasis) = SetRecipe(
            reps = 5,
            percent = 100.0,
            loadBasis = basis,
            reference = PlanLoadReference(kind = kind, configurationId = CatalogIds.SQ_LOW),
        )
        fun effective(set: SetRecipe, tm: Double): Double? {
            val slot = slotOf("sq", listOf(set))
            return PercentBasis.effective1RmPercent(set, slot, weekOf(listOf(slot)), tm)
        }
        val percentBases = listOf(
            LoadBasis.PERCENT_1RM,
            LoadBasis.PERCENT_DESIRED_MAX,
            LoadBasis.PERCENT_TM,
            LoadBasis.PERCENT_OF_TOP_SET,
        )
        // Con una referencia de trabajo observado o de lastre la serie no se expresa sobre el 1RM, sea cual sea su base.
        listOf(PlanLoadReferenceKind.OBSERVED_WORKING_SET, PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL).forEach { kind ->
            percentBases.forEach { basis ->
                assertNull("$kind / $basis", effective(referenced(kind, basis), 1.0))
            }
        }
        // Las referencias 1RM y TM del mismo ejercicio sí se expresan sobre el 1RM (100 % × TM 0,87 = 87 %).
        listOf(PlanLoadReferenceKind.EXERCISE_1RM, PlanLoadReferenceKind.EXERCISE_TM).forEach { kind ->
            assertEquals("$kind", 87.0, effective(referenced(kind, LoadBasis.PERCENT_TM), 0.87)!!, 1e-9)
        }
        // Sin referencia, igual que siempre.
        assertEquals(87.0, effective(SetRecipe(reps = 5, percent = 100.0, loadBasis = LoadBasis.PERCENT_TM), 0.87)!!, 1e-9)
    }

    @Test
    fun effective_1rm_percent_of_top_set_is_the_resolved_percent_times_the_tm_fraction() {
        val recipe = TexasMethodProtocols.recipe3d()
        val week = recipe.weeks.first()
        fun squatOn(weekday: Int) = week.days.first { it.weekday == weekday }.slots.first { it.lift.liftSlot == LiftSlot.SQUAT && it.role == SlotRole.T1_MAIN }
        val monday = squatOn(1)
        val wednesday = squatOn(3)
        val friday = squatOn(5)
        val tm = 0.87
        assertEquals(90.0 * tm, PercentBasis.effective1RmPercent(monday.sets.first(), monday, week, tm)!!, 1e-9)
        assertEquals(72.0 * tm, PercentBasis.effective1RmPercent(wednesday.sets.first(), wednesday, week, tm)!!, 1e-9)
        assertEquals(100.0 * tm, PercentBasis.effective1RmPercent(friday.sets.first(), friday, week, tm)!!, 1e-9)
    }

    @Test
    fun tm_fraction_falls_back_to_90_percent_when_the_recipe_has_no_valid_tm() {
        assertEquals(0.87, PercentBasis.tmFraction(0.87), 1e-9)
        assertEquals(1.0, PercentBasis.tmFraction(1.0), 1e-9)
        assertEquals(0.90, PercentBasis.tmFraction(0.0), 1e-9)
        assertEquals(0.90, PercentBasis.tmFraction(-0.5), 1e-9)
    }

    @Test
    fun epley_max_reps_table() {
        assertEquals(1, PercentBasis.maxRepsByEpley(100.0))
        assertEquals(1, PercentBasis.maxRepsByEpley(97.5))
        assertEquals(2, PercentBasis.maxRepsByEpley(95.0))
        assertEquals(3, PercentBasis.maxRepsByEpley(92.5))
        assertEquals(4, PercentBasis.maxRepsByEpley(90.0))
        // 87 % es el TM de un 5RM: caben exactamente cinco.
        assertEquals(5, PercentBasis.maxRepsByEpley(87.0))
        assertEquals(6, PercentBasis.maxRepsByEpley(85.0))
        assertEquals(8, PercentBasis.maxRepsByEpley(80.0))
        // 75 % da 10 exactos en papel (9,999… en coma flotante): el margen evita perder una repetición.
        assertEquals(11, PercentBasis.maxRepsByEpley(75.0))
        assertEquals(13, PercentBasis.maxRepsByEpley(70.0))
        assertEquals(21, PercentBasis.maxRepsByEpley(60.0))
        // Por encima del 1RM no cabe ni una: 0, nunca un negativo.
        assertEquals(0, PercentBasis.maxRepsByEpley(102.5))
        assertEquals(0, PercentBasis.maxRepsByEpley(105.0))
        assertEquals(Int.MAX_VALUE, PercentBasis.maxRepsByEpley(0.0))
    }

    @Test
    fun epley_max_reps_is_clamped_between_zero_and_int_max_minus_one() {
        // Por encima del 100 % no cabe ni una y nunca sale negativo (antes, 110 % daba -2).
        assertEquals(0, PercentBasis.maxRepsByEpley(102.5))
        assertEquals(0, PercentBasis.maxRepsByEpley(110.0))
        assertEquals(0, PercentBasis.maxRepsByEpley(250.0))
        assertEquals(0, PercentBasis.maxRepsByEpley(Double.POSITIVE_INFINITY))
        // Con un porcentaje minúsculo la cuenta se sale del rango de Int: se acota y no desborda a negativo.
        val tiny = PercentBasis.maxRepsByEpley(1e-9)
        assertTrue("1e-9 % desbordó a $tiny", tiny > 0)
        assertEquals(Int.MAX_VALUE - 1, tiny)
        assertEquals(Int.MAX_VALUE - 1, PercentBasis.maxRepsByEpley(Double.MIN_VALUE))
        // Solo un porcentaje menor o igual que 0 (sin base) devuelve el centinela «sin límite».
        assertEquals(Int.MAX_VALUE, PercentBasis.maxRepsByEpley(0.0))
        assertEquals(Int.MAX_VALUE, PercentBasis.maxRepsByEpley(-5.0))
        // Los valores normales no cambian.
        assertEquals(1, PercentBasis.maxRepsByEpley(100.0))
        assertEquals(4, PercentBasis.maxRepsByEpley(90.0))
    }
}
