package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ProgramAutoregulationEngineTest {
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

    private fun recipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "auto-test",
        weeks = listOf(
            com.example.kpkn.data.protocols.weekRecipe(
                1, 0, "Fuerza", com.example.kpkn.data.models.BlockGoal.INTENSIFICATION,
                listOf(DayArchetypes.plSquat(80.0, t1Amrap = true, weekday = 1), DayArchetypes.plBenchHeavy(75.0, weekday = 3)),
            ),
            com.example.kpkn.data.protocols.weekRecipe(
                2, 0, "Fuerza", com.example.kpkn.data.models.BlockGoal.INTENSIFICATION,
                listOf(DayArchetypes.plSquat(82.5, weekday = 1), DayArchetypes.plBenchHeavy(77.5, weekday = 3)),
            ),
        ),
        trainingMaxPercent = 0.90,
        liftSlots = mapOf(
            LiftSlot.SQUAT to CatalogIds.SQ_LOW,
            LiftSlot.BENCH to CatalogIds.BP,
            LiftSlot.DEADLIFT to CatalogIds.DL,
        ),
        progression = ProgressionRule.AmrapDrivenTm(),
    )

    private fun materialized(mode: AutoregulationMode = AutoregulationMode.PROPOSE): Program {
        val base = Program(
            id = "p",
            name = "Auto",
            structure = ProgramStructure.COMPLEX,
            powerliftingProfile = PowerliftingProfile(squat1RM = 200.0, squatTM = 180.0, bench1RM = 140.0, benchTM = 126.0, deadlift1RM = 240.0, deadliftTM = 216.0),
            autoregulationMode = mode,
        )
        return PlanMaterializer.materialize(base, recipe(), CatalogCompositionTestSupport.metadata, SeqIds())
            .copy(autoregulationMode = mode)
    }

    @Test
    fun shortAmrap_proposes_adjust_tm_down() {
        val program = materialized()
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val proposals = ProgramAutoregulationEngine.evaluate(
            program = program,
            completedWeek = week,
            logs = emptyList(),
            recipe = recipe(),
            signals = WeeklyAutoregulationSignals(
                amrapHits = listOf(AmrapHit(LiftSlot.SQUAT, 95.0, prescribedReps = 5, actualReps = 1, meanRpe = 9.4)),
                readinessScore = 38,
            ),
        )
        assertTrue(proposals.any { it.kind == AutoregulationProposalKind.ADJUST_TM && (it.percentDelta ?: 0.0) < 0 })
        assertTrue(proposals.first { it.kind == AutoregulationProposalKind.ADJUST_TM }.explanation.contains("AMRAP"))
    }

    @Test
    fun lowReadiness_and_unload_proposes_deload() {
        val program = materialized()
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val proposals = ProgramAutoregulationEngine.evaluate(
            program, week, emptyList(), recipe(),
            WeeklyAutoregulationSignals(readinessScore = 32, cumulativeFatigue = 90.0, loadAdvisoryLevel = LoadAdvisoryLevel.UNLOAD),
        )
        assertTrue(proposals.any { it.kind == AutoregulationProposalKind.INSERT_DELOAD })
    }

    @Test
    fun highE1rm_two_weeks_promotes_tm() {
        val program = materialized()
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val proposals = ProgramAutoregulationEngine.evaluate(
            program, week, emptyList(), recipe(),
            WeeklyAutoregulationSignals(
                e1rmByLift = mapOf(LiftSlot.SQUAT to 220.0),
                consecutiveHighE1rmWeeks = 2,
                readinessScore = 80,
            ),
        )
        assertTrue(proposals.any { it.kind == AutoregulationProposalKind.PROMOTE_TM && it.liftSlot == "SQUAT" })
    }

    @Test
    fun executed_week_is_never_rewritten() {
        val program = materialized(AutoregulationMode.AUTO)
        val weeks = program.macrocycles.first().blocks.first().mesocycles.first().weeks
        val firstId = weeks.first().id
        val secondId = weeks[1].id
        val applied = ProgramAutoregulationEngine.apply(
            program = program,
            nextWeekId = firstId,
            proposals = listOf(
                com.example.kpkn.data.models.AutoregulationProposal(
                    kind = AutoregulationProposalKind.SCALE_WEEK_INTENSITY,
                    percentDelta = -5.0,
                    explanation = "test",
                ),
            ),
            recipe = recipe(),
            executedWeekIds = setOf(firstId),
            metadata = CatalogCompositionTestSupport.metadata,
        )
        assertEquals(program.macrocycles.first().blocks.first().mesocycles.first().weeks.first(), applied.program.macrocycles.first().blocks.first().mesocycles.first().weeks.first())
        val nextApplied = ProgramAutoregulationEngine.apply(
            program = program,
            nextWeekId = secondId,
            proposals = listOf(
                com.example.kpkn.data.models.AutoregulationProposal(
                    kind = AutoregulationProposalKind.SCALE_WEEK_INTENSITY,
                    percentDelta = -5.0,
                    explanation = "test",
                ),
            ),
            recipe = recipe(),
            executedWeekIds = setOf(firstId),
            metadata = CatalogCompositionTestSupport.metadata,
        )
        assertNotEquals(
            program.macrocycles.first().blocks.first().mesocycles.first().weeks[1].sessions.first().allExercises().first().sets.first().targetPercentageRM,
            nextApplied.program.macrocycles.first().blocks.first().mesocycles.first().weeks[1].sessions.first().allExercises().first().sets.first().targetPercentageRM,
        )
    }

    @Test
    fun propose_mode_persists_pending_action_without_rewriting() {
        val program = materialized(AutoregulationMode.PROPOSE).copy(
            runState = ProgramRunState(runId = "run1", weekId = "w2"),
        )
        val weeks = program.macrocycles.first().blocks.first().mesocycles.first().weeks
        val result = ProgramAutoregulationEngine.apply(
            program = program,
            nextWeekId = weeks[1].id,
            proposals = listOf(
                com.example.kpkn.data.models.AutoregulationProposal(
                    kind = AutoregulationProposalKind.ADJUST_TM,
                    liftSlot = "SQUAT",
                    percentDelta = -2.5,
                    explanation = "AMRAP corto",
                ),
            ),
            recipe = recipe(),
            executedWeekIds = setOf(weeks.first().id),
            metadata = CatalogCompositionTestSupport.metadata,
        )
        assertFalse(result.applied)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, result.program.runState?.pendingAction?.type)
        assertEquals(program.macrocycles, result.program.macrocycles)
        val accepted = ProgramAutoregulationEngine.resolvePending(result.program, accept = true, CatalogCompositionTestSupport.metadata)
        assertEquals(null, accepted.runState?.pendingAction)
        assertTrue((accepted.powerliftingProfile?.squatTM ?: 180.0) < 180.0)
    }

    @Test
    fun off_mode_never_proposes() {
        val program = materialized(AutoregulationMode.OFF)
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val proposals = ProgramAutoregulationEngine.evaluate(
            program, week, emptyList(), recipe(),
            WeeklyAutoregulationSignals(readinessScore = 10, loadAdvisoryLevel = LoadAdvisoryLevel.UNLOAD),
        )
        assertTrue(proposals.isEmpty())
    }

    // ─── B.S4: progresión por rendimiento (AMRAP) como propuesta de TM ────────────────

    private fun firstWeek(program: Program): ProgramWeek =
        program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()

    /** Propuestas `ADJUST_TM` de [hits] con la regla [rule]: la semana 1 de `recipe()` y readiness alto. */
    private fun amrapProposals(rule: ProgressionRule, vararg hits: AmrapHit): List<AutoregulationProposal> {
        val program = materialized()
        return ProgramAutoregulationEngine.evaluate(
            program = program,
            completedWeek = firstWeek(program),
            logs = emptyList(),
            recipe = recipe().copy(progression = rule),
            signals = WeeklyAutoregulationSignals(amrapHits = hits.toList(), readinessScore = 80),
        ).filter { it.kind == AutoregulationProposalKind.ADJUST_TM }
    }

    @Test
    fun amrap_driven_tm_proposes_the_kilos_of_the_recipe_table_not_a_percent() {
        val proposal = amrapProposals(
            ProgressionRule.AmrapDrivenTm(),
            AmrapHit(LiftSlot.SQUAT, 95.0, prescribedReps = 1, actualReps = 5),
        ).single()

        assertEquals("SQUAT", proposal.liftSlot)
        assertEquals("la tabla da 5 kg para 4 o 5 reps", 5.0, proposal.kgDelta!!, 1e-9)
        assertNull("kilos, no porcentaje", proposal.percentDelta)
        assertEquals("AMRAP 95 % de sentadilla: 5 reps (objetivo 1+): TM +5 kg", proposal.explanation)
    }

    @Test
    fun the_amrap_table_gives_nothing_2_5_5_and_7_5_kg_by_reps() {
        val rule = ProgressionRule.AmrapDrivenTm()
        // prescribedReps = 0: sin mínimo declarado el AMRAP nunca es corto y solo decide la tabla.
        fun kgFor(reps: Int): Double? =
            ProgramAutoregulationEngine.amrapTmChange(rule, AmrapHit(LiftSlot.BENCH, 90.0, prescribedReps = 0, actualReps = reps))?.kgDelta

        assertNull("0 reps: sin propuesta", kgFor(0))
        assertNull("1 rep: sin propuesta", kgFor(1))
        assertEquals(2.5, kgFor(2)!!, 1e-9)
        assertEquals(2.5, kgFor(3)!!, 1e-9)
        assertEquals(5.0, kgFor(4)!!, 1e-9)
        assertEquals(5.0, kgFor(5)!!, 1e-9)
        assertEquals(7.5, kgFor(6)!!, 1e-9)
        assertEquals(7.5, kgFor(12)!!, 1e-9)

        // La tabla es la de la receta, no unos kilos fijos del motor.
        val custom = ProgressionRule.AmrapDrivenTm(zeroToOneKg = 1.0, twoToThreeKg = 3.0, fourToFiveKg = 6.0, sixPlusKg = 9.0)
        fun customKg(reps: Int): Double? =
            ProgramAutoregulationEngine.amrapTmChange(custom, AmrapHit(LiftSlot.BENCH, 90.0, prescribedReps = 0, actualReps = reps))?.kgDelta
        assertEquals(1.0, customKg(1)!!, 1e-9)
        assertEquals(3.0, customKg(2)!!, 1e-9)
        assertEquals(6.0, customKg(5)!!, 1e-9)
        assertEquals(9.0, customKg(6)!!, 1e-9)
    }

    @Test
    fun a_light_amrap_never_moves_an_amrap_driven_tm_up() {
        val rule = ProgressionRule.AmrapDrivenTm()
        // 70 % del TM con 12 reps sobre 5: es un AMRAP largo, pero no mide el TM.
        assertTrue(amrapProposals(rule, AmrapHit(LiftSlot.SQUAT, 70.0, prescribedReps = 5, actualReps = 12)).isEmpty())
        // El umbral es el 85 % del TM, incluido.
        assertNull(ProgramAutoregulationEngine.amrapTmChange(rule, AmrapHit(LiftSlot.SQUAT, 84.9, 5, 9)))
        assertEquals(7.5, ProgramAutoregulationEngine.amrapTmChange(rule, AmrapHit(LiftSlot.SQUAT, 85.0, 5, 9))!!.kgDelta!!, 1e-9)
        // Sin porcentaje conocido no se puede afirmar que sea pesado.
        assertNull(ProgramAutoregulationEngine.amrapTmChange(rule, AmrapHit(LiftSlot.SQUAT, null, 5, 9)))
    }

    @Test
    fun a_short_amrap_never_proposes_a_positive_change_whatever_the_rule() {
        val rules: List<ProgressionRule> = listOf(
            ProgressionRule.AmrapDrivenTm(),
            ProgressionRule.RepTargetDrivenTm(),
            ProgressionRule.CycleIncrement(2.5, 5.0),
            ProgressionRule.TopSetPr(),
            ProgressionRule.RepMaxAutoregulated,
            ProgressionRule.None,
        )
        rules.forEach { rule ->
            (0..4).forEach { reps ->
                val hit = AmrapHit(LiftSlot.SQUAT, 95.0, prescribedReps = 5, actualReps = reps)
                assertTrue("el hit de $reps reps sobre 5 es corto", ProgramAutoregulationEngine.isShortAmrap(hit))
                val change = ProgramAutoregulationEngine.amrapTmChange(rule, hit)
                assertTrue(
                    "${rule::class.simpleName} con $reps reps",
                    (change?.kgDelta ?: 0.0) <= 0.0 && (change?.percentDelta ?: 0.0) <= 0.0,
                )
            }
        }
        // Y la bajada de AmrapDrivenTm es el 2,5 % (su tabla en kilos no define bajadas).
        val down = ProgramAutoregulationEngine.amrapTmChange(
            ProgressionRule.AmrapDrivenTm(),
            AmrapHit(LiftSlot.SQUAT, 95.0, prescribedReps = 5, actualReps = 1),
        )!!
        assertEquals(-2.5, down.percentDelta!!, 1e-9)
        assertNull(down.kgDelta)
    }

    @Test
    fun a_single_at_95_percent_is_short_and_lowers_the_tm_even_when_the_target_was_1_plus() {
        val hit = AmrapHit(LiftSlot.DEADLIFT, 95.0, prescribedReps = 1, actualReps = 1)
        assertTrue(ProgramAutoregulationEngine.isShortAmrap(hit))
        assertEquals(-2.5, ProgramAutoregulationEngine.amrapTmChange(ProgressionRule.AmrapDrivenTm(), hit)!!.percentDelta!!, 1e-9)
        // Por debajo del 90 % del TM una sola repetición con objetivo 1+ cumple y la tabla no sube nada.
        val lighter = AmrapHit(LiftSlot.DEADLIFT, 87.5, prescribedReps = 1, actualReps = 1)
        assertFalse(ProgramAutoregulationEngine.isShortAmrap(lighter))
        assertNull(ProgramAutoregulationEngine.amrapTmChange(ProgressionRule.AmrapDrivenTm(), lighter))
    }

    @Test
    fun rep_target_driven_tm_raises_the_tm_half_a_percent_per_extra_rep() {
        val rule = ProgressionRule.RepTargetDrivenTm()
        val proposal = amrapProposals(rule, AmrapHit(LiftSlot.SQUAT, 75.0, prescribedReps = 6, actualReps = 9)).single()
        assertEquals("el camino positivo de SBS ya es alcanzable", 1.5, proposal.percentDelta!!, 1e-9)
        assertNull(proposal.kgDelta)
        assertEquals("AMRAP 75 % de sentadilla: 9 reps (objetivo 6+): TM +1,5 %", proposal.explanation)
        assertEquals(0.5, amrapProposals(rule, AmrapHit(LiftSlot.SQUAT, 75.0, 6, 7)).single().percentDelta!!, 1e-9)
        // En el objetivo no hay extra y con una repetición de menos no llega al umbral de fallo.
        assertTrue(amrapProposals(rule, AmrapHit(LiftSlot.SQUAT, 75.0, 6, 6)).isEmpty())
        assertTrue(amrapProposals(rule, AmrapHit(LiftSlot.SQUAT, 75.0, 6, 5)).isEmpty())
        // Con dos o más de menos baja una vez por cada repetición que falta (nunca hay propuesta de 0 %).
        assertEquals(-2.0, amrapProposals(rule, AmrapHit(LiftSlot.SQUAT, 75.0, 6, 4)).single().percentDelta!!, 1e-9)
        assertEquals(-6.0, amrapProposals(rule, AmrapHit(LiftSlot.SQUAT, 75.0, 6, 0)).single().percentDelta!!, 1e-9)
        // El porcentaje no tiene umbral de intensidad: SBS usa AMRAP al 71-82 % del TM.
        assertEquals(1.0, amrapProposals(rule, AmrapHit(LiftSlot.BENCH, 71.0, 6, 8)).single().percentDelta!!, 1e-9)
    }

    @Test
    fun a_hit_without_a_lift_is_recorded_but_proposes_nothing_and_lighter_loads_do_not_raise_the_tm() {
        val rule = ProgressionRule.AmrapDrivenTm()
        assertTrue(amrapProposals(rule, AmrapHit(null, 95.0, prescribedReps = 1, actualReps = 5)).isEmpty())
        // Hasta con un AMRAP corto: sin levantamiento no hay TM que bajar.
        assertTrue(amrapProposals(rule, AmrapHit(null, 95.0, prescribedReps = 5, actualReps = 1)).isEmpty())

        // Una serie hecha con menos del 97,5 % de la carga prescrita no sube el TM, pero sí puede bajarlo.
        assertNull(ProgramAutoregulationEngine.amrapTmChange(rule, AmrapHit(LiftSlot.SQUAT, 95.0, 1, 5, weightRatio = 0.9)))
        assertEquals(5.0, ProgramAutoregulationEngine.amrapTmChange(rule, AmrapHit(LiftSlot.SQUAT, 95.0, 1, 5, weightRatio = 0.98))!!.kgDelta!!, 1e-9)
        assertEquals(-2.5, ProgramAutoregulationEngine.amrapTmChange(rule, AmrapHit(LiftSlot.SQUAT, 95.0, 5, 1, weightRatio = 0.9))!!.percentDelta!!, 1e-9)
    }

    @Test
    fun a_kilo_proposal_respects_off_propose_and_auto() {
        val hit = AmrapHit(LiftSlot.SQUAT, 95.0, prescribedReps = 1, actualReps = 5)
        val signals = WeeklyAutoregulationSignals(amrapHits = listOf(hit), readinessScore = 80)
        val rule = recipe()

        // OFF: ni propuesta ni cambio.
        val off = materialized(AutoregulationMode.OFF)
        assertTrue(ProgramAutoregulationEngine.evaluate(off, firstWeek(off), emptyList(), rule, signals).isEmpty())

        // PROPOSE: queda pendiente con sus kilos y el TM no se toca hasta aceptar.
        val propose = materialized(AutoregulationMode.PROPOSE)
        val proposeWeeks = propose.macrocycles.first().blocks.first().mesocycles.first().weeks
        val proposals = ProgramAutoregulationEngine.evaluate(propose, firstWeek(propose), emptyList(), rule, signals)
        val pending = ProgramAutoregulationEngine.apply(
            propose, proposeWeeks[1].id, proposals, rule, setOf(proposeWeeks.first().id), CatalogCompositionTestSupport.metadata,
        )
        assertFalse(pending.applied)
        assertEquals(180.0, pending.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, pending.program.runState?.pendingAction?.type)
        assertEquals(5.0, pending.program.runState?.pendingAction?.proposals?.single()?.kgDelta!!, 1e-9)
        val accepted = ProgramAutoregulationEngine.resolvePending(pending.program, accept = true, CatalogCompositionTestSupport.metadata)
        assertEquals(185.0, accepted.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals("los 1RM no se tocan", 200.0, accepted.powerliftingProfile!!.squat1RM!!, 1e-9)

        // AUTO: aplica los kilos, audita y la semana siguiente toma el TM nuevo.
        val auto = materialized(AutoregulationMode.AUTO)
        val autoWeeks = auto.macrocycles.first().blocks.first().mesocycles.first().weeks
        val applied = ProgramAutoregulationEngine.apply(
            auto, autoWeeks[1].id, proposals, rule, setOf(autoWeeks.first().id), CatalogCompositionTestSupport.metadata,
        )
        assertTrue(applied.applied)
        assertEquals(185.0, applied.program.powerliftingProfile!!.squatTM!!, 1e-9)
        val audit = applied.program.runState?.autoregulationAudit.orEmpty()
        assertTrue(
            audit.any {
                it.resolution == PendingActionResolutionStatus.APPLIED &&
                    it.resolutionReason.startsWith("Aplicada: AMRAP 95 % de sentadilla")
            },
        )
        val nextSquat = applied.program.macrocycles.first().blocks.first().mesocycles.first().weeks[1]
            .sessions.first().allExercises().first().sets.first().weight!!
        assertEquals("82,5 % del TM nuevo", 0.825 * 185.0, nextSquat, 1e-6)
    }

    // ─── B.S4: lectura de los registros (ciclo, log más reciente, levantamiento por slot) ────

    /** La sesión de sentadilla de la semana 1 y su T1 (4 series, la última AMRAP al 80 %). */
    private fun squatT1(program: Program): Pair<Session, Exercise> {
        val session = firstWeek(program).sessions.first()
        val squat = session.allExercises().first { it.slotRole == SlotRole.T1_MAIN && it.catalogConfigurationId == CatalogIds.SQ_LOW }
        return session to squat
    }

    /** Un registro del T1 de sentadilla: 4 reps en todas las series y [amrapReps] en la AMRAP (marcada). */
    private fun squatLog(
        program: Program,
        id: String,
        date: String,
        cycle: Int,
        amrapReps: Int,
        runId: String? = "run-a",
        flagAmrap: Boolean = true,
        sets: Int? = null,
    ): WorkoutLog {
        val week = firstWeek(program)
        val (session, squat) = squatT1(program)
        val logged = squat.sets.mapIndexed { index, planned ->
            CompletedSet(
                id = "$id-$index",
                weight = planned.weight ?: 100.0,
                reps = if (planned.isAmrap) amrapReps else 4,
                amrapPerformed = planned.isAmrap && flagAmrap,
            )
        }
        return WorkoutLog(
            id = id,
            programId = program.id,
            sessionId = session.id,
            sessionName = session.name,
            date = date,
            durationMinutes = 60,
            weekId = week.id,
            cycleNumber = cycle,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(cycle, week.id),
            programRunId = runId,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = squat.id,
                    exerciseName = squat.name,
                    catalogConfigurationId = squat.catalogConfigurationId,
                    sets = if (sets != null) logged.take(sets) else logged,
                ),
            ),
        )
    }

    @Test
    fun collect_amrap_hits_reads_only_the_logs_of_the_cycle_being_evaluated() {
        val program = materialized()
        val week = firstWeek(program)
        val logs = listOf(
            squatLog(program, "c1", "2026-01-05T10:00:00.000Z", cycle = 1, amrapReps = 2),
            squatLog(program, "c2", "2026-02-02T10:00:00.000Z", cycle = 2, amrapReps = 7),
        )
        fun repsOf(cycle: Int) = ProgramAutoregulationEngine
            .collectAmrapHits(week, logs, cycle, program.id, "run-a", recipe())
            .map { it.actualReps }

        assertEquals(listOf(2), repsOf(1))
        assertEquals(listOf(7), repsOf(2))
        assertTrue("un ciclo sin registros no hereda los de otro", repsOf(3).isEmpty())
        // Otro run del mismo programa no cuenta.
        assertTrue(
            ProgramAutoregulationEngine.collectAmrapHits(week, logs, 1, program.id, "run-b", recipe()).isEmpty(),
        )
        // Sin ciclo (llamadores antiguos) se leen los dos, pero manda el más reciente.
        assertEquals(listOf(7), ProgramAutoregulationEngine.collectAmrapHits(week, logs).map { it.actualReps })

        val hit = ProgramAutoregulationEngine.collectAmrapHits(week, logs, 2, program.id, "run-a", recipe()).single()
        assertEquals(LiftSlot.SQUAT, hit.liftSlot)
        assertEquals(80.0, hit.percent!!, 1e-9)
        assertEquals(4, hit.prescribedReps)
    }

    @Test
    fun evaluate_takes_the_amrap_of_the_current_cycle_from_the_logs() {
        val withRun = materialized().let { it.copy(runState = ProgramRunState(runId = "run-a", cycleNumber = 2)) }
        val week = firstWeek(withRun)
        val logs = listOf(
            squatLog(withRun, "c1", "2026-01-05T10:00:00.000Z", cycle = 1, amrapReps = 1),
            squatLog(withRun, "c2", "2026-02-02T10:00:00.000Z", cycle = 2, amrapReps = 4),
        )

        val proposals = ProgramAutoregulationEngine.evaluate(
            program = withRun,
            completedWeek = week,
            logs = logs,
            recipe = recipe().copy(progression = ProgressionRule.RepTargetDrivenTm()),
            signals = WeeklyAutoregulationSignals(readinessScore = 80),
        ).filter { it.kind == AutoregulationProposalKind.ADJUST_TM }

        // El ciclo 1 (1 rep, corto) ya pasó: el 4+ del ciclo 2 cumple su objetivo y no propone nada.
        assertTrue(proposals.toString(), proposals.isEmpty())
    }

    @Test
    fun the_most_recent_log_of_a_session_decides_not_the_oldest() {
        val program = materialized()
        val week = firstWeek(program)
        val older = squatLog(program, "old", "2026-01-05T10:00:00.000Z", cycle = 1, amrapReps = 2)
        val newer = squatLog(program, "new", "2026-01-12T10:00:00.000Z", cycle = 1, amrapReps = 6)

        // El orden de la lista no manda (el historial puede venir en cualquier orden): manda la fecha.
        listOf(listOf(older, newer), listOf(newer, older)).forEach { logs ->
            val hit = ProgramAutoregulationEngine.collectAmrapHits(week, logs, 1, program.id, "run-a", recipe()).single()
            assertEquals(6, hit.actualReps)
        }
    }

    @Test
    fun an_unflagged_log_pairs_by_position_only_when_it_has_every_planned_set() {
        val program = materialized()
        val week = firstWeek(program)
        val full = squatLog(program, "full", "2026-01-05T10:00:00.000Z", cycle = 1, amrapReps = 3, flagAmrap = false)
        val missing = squatLog(program, "missing", "2026-01-05T10:00:00.000Z", cycle = 1, amrapReps = 3, flagAmrap = false, sets = 3)

        assertEquals(
            listOf(3),
            ProgramAutoregulationEngine.collectAmrapHits(week, listOf(full), 1, program.id, "run-a", recipe()).map { it.actualReps },
        )
        // Con una serie menos no hay forma segura de saber cuál era la AMRAP: sin dato antes que un dato falso.
        assertTrue(
            ProgramAutoregulationEngine.collectAmrapHits(week, listOf(missing), 1, program.id, "run-a", recipe()).isEmpty(),
        )
    }

    @Test
    fun the_text_fallback_no_longer_counts_variants_as_the_main_lifts() {
        fun lift(id: String?) = ProgramAutoregulationEngine.liftSlotFromConfigurationId(id)
        assertEquals(LiftSlot.DEADLIFT, lift(CatalogIds.DL))
        assertEquals(LiftSlot.DEADLIFT, lift(CatalogIds.DL_SUMO))
        assertEquals(LiftSlot.SQUAT, lift(CatalogIds.SQ_LOW))
        assertEquals(LiftSlot.BENCH, lift(CatalogIds.BP))
        assertEquals(LiftSlot.OVERHEAD, lift(CatalogIds.OHP))
        listOf(
            CatalogIds.RDL, CatalogIds.SLDL, CatalogIds.SQ_HACK, CatalogIds.SQ_FRONT, CatalogIds.SQ_SISSY,
            CatalogIds.BP_INC, CatalogIds.OH_TRI, CatalogIds.CHIN, null,
        ).forEach { id -> assertNull("$id", lift(id)) }
    }

    @Test
    fun the_e1rm_estimated_from_history_ignores_the_variants_of_the_main_lifts() {
        fun log(configurationId: String, kg: Double, reps: Int) = WorkoutLog(
            id = "log-$configurationId",
            programId = "p",
            sessionId = "s",
            sessionName = "Sesión",
            date = "2026-01-01T10:00:00.000Z",
            durationMinutes = 30,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "e-$configurationId",
                    exerciseName = configurationId,
                    catalogConfigurationId = configurationId,
                    sets = listOf(CompletedSet(id = "set-$configurationId", weight = kg, reps = reps)),
                ),
            ),
        )

        val history = listOf(
            log(CatalogIds.DL, 200.0, 1),
            log(CatalogIds.RDL, 220.0, 5),
            log(CatalogIds.SQ_LOW, 180.0, 1),
            log(CatalogIds.SQ_HACK, 300.0, 5),
            log(CatalogIds.SQ_FRONT, 250.0, 3),
        )

        val e1rm = ProgramAutoregulationEngine.collectE1rmFromHistory(history)

        // El rumano de 220 × 5 daría un e1RM de 247,5 kg: ya no cuenta como peso muerto, ni la hack ni la frontal como sentadilla.
        assertEquals(200.0, e1rm.getValue(LiftSlot.DEADLIFT), 1e-9)
        assertEquals(180.0, e1rm.getValue(LiftSlot.SQUAT), 1e-9)
    }

    /** Una recetilla con los tres casos: el principal, un rumano que DECLARA su levantamiento y un peso muerto que no lo declara. */
    private fun declaredLiftsRecipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "slot-lifts",
        weeks = listOf(
            com.example.kpkn.data.protocols.weekRecipe(
                1, 0, "Base", BlockGoal.ACCUMULATION,
                listOf(
                    day(
                        label = "Día",
                        weekday = 1,
                        slots = listOf(
                            slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(180, 5 to 80.0, 5 to 80.0, amrapLast = true), 180, LiftSlot.SQUAT),
                            slot("rdl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.RDL, percentSets(150, 8 to 60.0, 8 to 60.0, amrapLast = true), 150, LiftSlot.DEADLIFT),
                            slot("dl-acc", SlotRole.T3_ACCESSORY, CatalogIds.DL, percentSets(150, 8 to 50.0, 8 to 50.0, amrapLast = true), 150),
                        ),
                    ),
                ),
            ),
        ),
        trainingMaxPercent = 0.90,
        liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP, LiftSlot.DEADLIFT to CatalogIds.DL),
        progression = ProgressionRule.AmrapDrivenTm(),
    )

    private fun programOf(recipe: TrainingPlanRecipe): Program = PlanMaterializer.materialize(
        Program(
            id = "p-slots",
            name = "Slots",
            structure = ProgramStructure.COMPLEX,
            powerliftingProfile = PowerliftingProfile(
                squat1RM = 200.0, squatTM = 180.0, bench1RM = 140.0, benchTM = 126.0, deadlift1RM = 240.0, deadliftTM = 216.0,
            ),
            autoregulationMode = AutoregulationMode.PROPOSE,
        ),
        recipe,
        CatalogCompositionTestSupport.metadata,
        SeqIds(),
        strict = false,
    ).copy(autoregulationMode = AutoregulationMode.PROPOSE)

    /** Un registro con todas las series de cada ejercicio de la primera sesión y la última marcada como AMRAP. */
    private fun amrapLogOf(program: Program, id: String, amrapReps: Int, onlyConfigurations: Set<String>? = null): WorkoutLog {
        val week = firstWeek(program)
        val session = week.sessions.first()
        val logged = session.allExercises()
            .filter { exercise -> exercise.sets.any { it.isAmrap } }
            .filter { exercise -> onlyConfigurations == null || exercise.catalogConfigurationId in onlyConfigurations }
            .map { exercise ->
                CompletedExercise(
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    catalogConfigurationId = exercise.catalogConfigurationId,
                    sets = exercise.sets.mapIndexed { index, planned ->
                        CompletedSet(
                            id = "$id-${exercise.id}-$index",
                            weight = planned.weight ?: 80.0,
                            reps = if (planned.isAmrap) amrapReps else planned.targetReps ?: 5,
                            amrapPerformed = planned.isAmrap,
                        )
                    },
                )
            }
        return WorkoutLog(
            id = id,
            programId = program.id,
            sessionId = session.id,
            sessionName = session.name,
            date = "2026-03-01T10:00:00.000Z",
            durationMinutes = 60,
            weekId = week.id,
            completedExercises = logged,
        )
    }

    @Test
    fun the_lift_of_a_hit_comes_from_the_recipe_slot_not_from_the_exercise_name() {
        val recipe = declaredLiftsRecipe()
        val program = programOf(recipe)
        val hits = ProgramAutoregulationEngine.collectAmrapHits(
            firstWeek(program), listOf(amrapLogOf(program, "slots", amrapReps = 9)), recipe = recipe,
        )

        // Orden de la sesión: sentadilla, rumano (declara el peso muerto) y peso muerto sin levantamiento declarado.
        assertEquals(listOf(LiftSlot.SQUAT, LiftSlot.DEADLIFT, null), hits.map { it.liftSlot })
        // Sin la receta solo queda el texto: el rumano ya no cuenta como peso muerto.
        val byText = ProgramAutoregulationEngine.collectAmrapHits(firstWeek(program), listOf(amrapLogOf(program, "text", amrapReps = 9)))
        assertEquals(listOf(LiftSlot.SQUAT, null, LiftSlot.DEADLIFT), byText.map { it.liftSlot })
    }

    @Test
    fun the_chin_of_texas_never_moves_a_training_max() {
        val recipe = TrainingPlanRecipe(
            id = "texas-like",
            weeks = listOf(
                com.example.kpkn.data.protocols.weekRecipe(
                    1, 0, "Texas", BlockGoal.INTENSIFICATION,
                    listOf(
                        day(
                            label = "Recuperación",
                            weekday = 1,
                            slots = listOf(
                                slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(180, 5 to 80.0, 5 to 80.0), 180, LiftSlot.SQUAT),
                                slot(
                                    "chin", SlotRole.T3_ACCESSORY, CatalogIds.CHIN,
                                    List(3) { SetRecipe(reps = 8, amrap = true, rpe = 8.0, loadBasis = LoadBasis.RPE) }, 120,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP, LiftSlot.DEADLIFT to CatalogIds.DL),
            progression = ProgressionRule.AmrapDrivenTm(),
        )
        val program = programOf(recipe)
        val week = firstWeek(program)
        // 7 dominadas en vez de 8 en las tres series AMRAP: corto, pero sin levantamiento.
        val log = amrapLogOf(program, "chin", amrapReps = 7)

        val hits = ProgramAutoregulationEngine.collectAmrapHits(week, listOf(log), recipe = recipe)
        assertEquals(3, hits.size)
        assertTrue("las dominadas no declaran levantamiento", hits.all { it.liftSlot == null })
        assertTrue(hits.all { ProgramAutoregulationEngine.isShortAmrap(it) })

        val proposals = ProgramAutoregulationEngine.evaluate(
            program = program,
            completedWeek = week,
            logs = listOf(log),
            recipe = recipe,
            signals = WeeklyAutoregulationSignals(readinessScore = 80),
        )
        assertTrue("antes escalaba los cuatro TM", proposals.none { it.kind == AutoregulationProposalKind.ADJUST_TM })
    }

    @Test
    fun an_old_tm_proposal_without_a_lift_changes_no_tm_and_says_why() {
        val auto = programOf(declaredLiftsRecipe()).copy(autoregulationMode = AutoregulationMode.AUTO)
        val legacy = AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_TM,
            percentDelta = -2.5,
            explanation = "AMRAP 7 reps (objetivo 8)",
        )

        val result = ProgramAutoregulationEngine.apply(
            program = auto,
            nextWeekId = null,
            proposals = listOf(legacy),
            recipe = declaredLiftsRecipe(),
            executedWeekIds = emptySet(),
            metadata = CatalogCompositionTestSupport.metadata,
        )

        assertEquals("ningún TM cambia", auto.powerliftingProfile, result.program.powerliftingProfile)
        assertFalse(result.applied)
        val entry = result.program.runState?.autoregulationAudit.orEmpty().single { it.resolution != null }
        assertEquals(PendingActionResolutionStatus.EXPIRED, entry.resolution)
        assertTrue(entry.resolutionReason, entry.resolutionReason.contains("no indica a qué levantamiento"))
    }
}

class ProgramProgressEngineAutoregulationTest {
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

    @Test
    fun completing_week_attaches_confirm_autoregulation_in_propose_mode() {
        val recipe = TrainingPlanRecipe(
            id = "hook",
            weeks = listOf(
                com.example.kpkn.data.protocols.weekRecipe(
                    1, 0, "A", com.example.kpkn.data.models.BlockGoal.ACCUMULATION,
                    listOf(DayArchetypes.plBenchHeavy(70.0, weekday = 1)),
                ),
                com.example.kpkn.data.protocols.weekRecipe(
                    2, 0, "A", com.example.kpkn.data.models.BlockGoal.ACCUMULATION,
                    listOf(DayArchetypes.plBenchHeavy(72.5, weekday = 1)),
                ),
            ),
            liftSlots = mapOf(LiftSlot.BENCH to CatalogIds.BP, LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.DEADLIFT to CatalogIds.DL),
        )
        val program = PlanMaterializer.materialize(
            Program(id = "hook-p", name = "Hook", structure = ProgramStructure.COMPLEX, autoregulationMode = AutoregulationMode.PROPOSE),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        ).copy(autoregulationMode = AutoregulationMode.PROPOSE)
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val session = week.sessions.first()
        val log = WorkoutLog(
            id = "log1",
            programId = program.id,
            sessionId = session.id,
            sessionName = session.name,
            date = "2026-09-01T10:00:00.000Z",
            durationMinutes = 60,
            weekId = week.id,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = session.allExercises().first().id,
                    exerciseName = "Bench",
                    catalogConfigurationId = CatalogIds.BP,
                    sets = listOf(CompletedSet(id = "s", weight = 100.0, reps = 1, amrapPerformed = true, rpe = 9.5)),
                ),
            ),
        )
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program.copy(runState = ProgramRunState(runId = "r", weekId = week.id)),
            activeState = null,
            completedSession = session,
            weekInstanceId = week.id,
            logs = listOf(log),
            weeklySignals = WeeklyAutoregulationSignals(
                amrapHits = listOf(AmrapHit(LiftSlot.BENCH, 95.0, 5, 1, 9.5)),
                readinessScore = 70,
            ),
            compositionMetadata = CatalogCompositionTestSupport.metadata,
        )
        assertTrue(result.advancedWeek)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, result.program.runState?.pendingAction?.type)
        assertTrue(result.autoregulationProposals.isNotEmpty())
    }
}
