package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
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
 * CAL5 · nSuns LP (RES1 §4): la serie 1+ al 95 % del TM de cada T1 decide el TM de la semana siguiente: 0-1 repeticiones no suben,
 * 2-3 suben 2,5 kg, 4-5 suben 5 kg y 6 o más suben 7,5 kg (la tabla de la fuente en kilos; sin bajadas). El press militar ya lleva su 1+.
 */
class Cal5NSunsTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val nsuns = recipeOf("nsuns-531-lp-4d")

    private fun hit(lift: LiftSlot, reps: Int, percent: Double = 95.0, prescribed: Int = 1) =
        AmrapHit(lift, percent, prescribedReps = prescribed, actualReps = reps)

    @Test
    fun the_table_gives_nothing_2_5_5_and_7_5_kg_by_reps_with_no_drop() {
        assertEquals(ProgressionRule.AmrapDrivenTm(), nsuns.progression)
        fun kgFor(reps: Int): Double? = ProgramAutoregulationEngine.amrapTmChange(nsuns.progression, hit(LiftSlot.SQUAT, reps))?.kgDelta
        assertNull("0 repeticiones: sin subida y sin bajada", kgFor(0))
        assertNull("1 repetición: sin subida", kgFor(1))
        assertEquals(2.5, kgFor(2)!!, 1e-9)
        assertEquals(2.5, kgFor(3)!!, 1e-9)
        assertEquals(5.0, kgFor(4)!!, 1e-9)
        assertEquals(5.0, kgFor(5)!!, 1e-9)
        assertEquals(7.5, kgFor(6)!!, 1e-9)
        assertEquals(7.5, kgFor(15)!!, 1e-9)
    }

    @Test
    fun a_short_one_plus_never_lowers_the_tm() {
        // Antes un AMRAP corto de nSuns bajaba el TM un 2,5 %; la fuente solo dice «sin subida».
        listOf(0, 1, 2).forEach { reps ->
            val change = ProgramAutoregulationEngine.amrapTmChange(nsuns.progression, hit(LiftSlot.BENCH, reps, prescribed = 3))
            assertNull("3+ con $reps repeticiones", change)
        }
    }

    @Test
    fun the_four_t1_carry_a_one_plus_at_95_percent_including_the_overhead_press() {
        val lifts = mutableSetOf<LiftSlot>()
        nsuns.weeks.forEach { week ->
            val t1s = week.days.map { day -> day.slots.single { it.id == "t1" } }
            t1s.forEach { t1 ->
                val amrap = t1.sets.filter { it.amrap }.single()
                assertEquals("${week.weekNumber}/${t1.lift.configurationId}: la serie 1+", 1, amrap.reps)
                assertEquals(95.0, amrap.percent!!, 1e-9)
                lifts += t1.lift.liftSlot!!
            }
            assertEquals(
                "cada levantamiento tiene su 1+ una vez por semana",
                setOf(LiftSlot.OVERHEAD, LiftSlot.SQUAT, LiftSlot.BENCH, LiftSlot.DEADLIFT),
                t1s.map { it.lift.liftSlot }.toSet(),
            )
        }
        assertEquals(4, lifts.size)
        // El press militar del primer día es el T1 y la banca de volumen (9 series) su complementario.
        val day1 = nsuns.weeks.first().days.first()
        assertEquals(CatalogIds.OHP, day1.slots.single { it.role == SlotRole.T1_MAIN }.lift.configurationId)
        assertEquals(9, day1.slots.single { it.role == SlotRole.T2_SUPPLEMENTAL }.sets.size)
    }

    @Test
    fun the_overhead_press_progresses_with_its_own_one_plus() {
        val program = Cal5TestSupport.materialize(nsuns)
        val weeks = weeksOf(program)
        val week1 = weeks.first()
        val ohpDay = week1.sessions.first()
        val ohp = exerciseOf(ohpDay, CatalogIds.OHP)
        // 5 repeticiones en el 1+ al 95 % del TM del press (72 kg): +5 kg.
        val log = exerciseLog(program, week1, ohpDay, ohp, repsOf = { _, planned -> if (planned.purpose.isAmrap) 5 else planned.targetReps ?: 5 })

        val proposals = ProgramAutoregulationEngine.evaluate(
            program, week1, listOf(log), nsuns, WeeklyAutoregulationSignals(),
        ).filter { it.kind == AutoregulationProposalKind.ADJUST_TM }

        val proposal = proposals.single()
        assertEquals("OVERHEAD", proposal.liftSlot)
        assertEquals(5.0, proposal.kgDelta!!, 1e-9)
        assertEquals("AMRAP 95 % de press militar: 5 reps (objetivo 1+): TM +5 kg", proposal.explanation)
    }

    @Test
    fun the_modes_off_propose_and_auto_are_respected() {
        val hits = WeeklyAutoregulationSignals(amrapHits = listOf(hit(LiftSlot.OVERHEAD, 5)), )

        // OFF: ni propuesta ni cambio.
        val off = Cal5TestSupport.materialize(nsuns, AutoregulationMode.OFF)
        assertTrue(ProgramAutoregulationEngine.evaluate(off, weeksOf(off).first(), emptyList(), nsuns, hits).isEmpty())

        // PROPONER: queda pendiente con sus kilos y el TM no se toca hasta aceptar.
        val propose = Cal5TestSupport.materialize(nsuns, AutoregulationMode.PROPOSE)
        val proposeWeeks = weeksOf(propose)
        val proposals = ProgramAutoregulationEngine.evaluate(propose, proposeWeeks.first(), emptyList(), nsuns, hits)
        val pending = ProgramAutoregulationEngine.apply(
            propose, proposeWeeks[1].id, proposals, nsuns, setOf(proposeWeeks.first().id), CatalogCompositionTestSupport.metadata,
        )
        assertFalse(pending.applied)
        assertEquals(72.0, pending.program.powerliftingProfile!!.overheadTM!!, 1e-9)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, pending.program.runState?.pendingAction?.type)
        val accepted = ProgramAutoregulationEngine.resolvePending(pending.program, accept = true, CatalogCompositionTestSupport.metadata)
        assertEquals(77.0, accepted.powerliftingProfile!!.overheadTM!!, 1e-9)

        // AUTO: aplica los kilos y la semana siguiente toma el TM nuevo (75 % del press: 57,75 kg con el TM de 77).
        val auto = Cal5TestSupport.materialize(nsuns, AutoregulationMode.AUTO)
        val autoWeeks = weeksOf(auto)
        val applied = ProgramAutoregulationEngine.apply(
            auto, autoWeeks[1].id, proposals, nsuns, setOf(autoWeeks.first().id), CatalogCompositionTestSupport.metadata,
        )
        assertTrue(applied.applied)
        assertEquals(77.0, applied.program.powerliftingProfile!!.overheadTM!!, 1e-9)
        val nextWeekOhp = exerciseOf(weeksOf(applied.program)[1].sessions.first(), CatalogIds.OHP)
        assertEquals(0.75 * 77.0, nextWeekOhp.sets.first().weight!!, 1e-6)
    }
}
