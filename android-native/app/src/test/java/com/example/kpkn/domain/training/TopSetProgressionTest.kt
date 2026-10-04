package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.data.protocols.rpeSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * B.S4: los consumidores de `TopSetPr` y `RepMaxAutoregulated`. La progresión POR RENDIMIENTO sale como
 * propuesta `ADJUST_TM` y respeta OFF (nada), PROPOSE (pendiente) y AUTO (aplicada con auditoría).
 *
 * - `TopSetPr`: top set con las repeticiones del objetivo sube el incremento del levantamiento (1,25 kg
 *   en banca y press militar, 2,5 kg en sentadilla y peso muerto), con dos más el doble, con una menos
 *   nada y con dos menos baja el TM un 2,5 %.
 * - `RepMaxAutoregulated` (mínimo y conservador): el e1RM de una serie al máximo del levantamiento de
 *   competición frente al 1RM que implica el TM; ±2,5 % fuera del margen.
 */
class TopSetProgressionTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "ts_${++n}"
    }

    private val metadata get() = CatalogCompositionTestSupport.metadata

    private val signals = WeeklyAutoregulationSignals(readinessScore = 80)

    // ─── La regla de TopSetPr ──────────────────────────────────────────────────────

    private fun change(lift: LiftSlot, target: Int, actual: Int, ratio: Double? = null, rule: ProgressionRule.TopSetPr = ProgressionRule.TopSetPr()) =
        ProgramAutoregulationEngine.topSetTmChange(rule, TopSetHit(lift, target, actual, ratio))

    @Test
    fun reaching_the_target_raises_the_tm_by_the_increment_of_the_lift() {
        assertEquals(1.25, change(LiftSlot.BENCH, 5, 5)!!.kgDelta!!, 1e-9)
        assertEquals(1.25, change(LiftSlot.OVERHEAD, 5, 5)!!.kgDelta!!, 1e-9)
        assertEquals(2.5, change(LiftSlot.SQUAT, 5, 5)!!.kgDelta!!, 1e-9)
        assertEquals(2.5, change(LiftSlot.DEADLIFT, 5, 5)!!.kgDelta!!, 1e-9)
        // Una repetición de más sigue siendo el incremento sencillo.
        assertEquals(1.25, change(LiftSlot.BENCH, 5, 6)!!.kgDelta!!, 1e-9)
        assertNull("en kilos, nunca en porcentaje", change(LiftSlot.BENCH, 5, 5)!!.percentDelta)
    }

    @Test
    fun two_extra_reps_double_the_increment() {
        assertEquals(2.5, change(LiftSlot.BENCH, 5, 7)!!.kgDelta!!, 1e-9)
        assertEquals(5.0, change(LiftSlot.SQUAT, 5, 7)!!.kgDelta!!, 1e-9)
        // Más repeticiones no multiplican más: el doble es el techo.
        assertEquals(5.0, change(LiftSlot.SQUAT, 5, 12)!!.kgDelta!!, 1e-9)
    }

    @Test
    fun one_rep_short_changes_nothing_and_two_short_lowers_the_tm_2_5_percent() {
        assertNull(change(LiftSlot.SQUAT, 5, 4))
        val down = change(LiftSlot.SQUAT, 5, 3)!!
        assertEquals(-2.5, down.percentDelta!!, 1e-9)
        assertNull(down.kgDelta)
        assertEquals(-2.5, change(LiftSlot.BENCH, 5, 0)!!.percentDelta!!, 1e-9)
    }

    @Test
    fun a_lighter_load_never_raises_the_tm_but_can_still_lower_it() {
        assertNull("con el 90 % de la carga no hay subida", change(LiftSlot.BENCH, 5, 9, ratio = 0.9))
        assertEquals(1.25, change(LiftSlot.BENCH, 5, 5, ratio = 0.98)!!.kgDelta!!, 1e-9)
        assertEquals(-2.5, change(LiftSlot.BENCH, 5, 3, ratio = 0.9)!!.percentDelta!!, 1e-9)
    }

    @Test
    fun the_increments_are_the_ones_of_the_rule() {
        val custom = ProgressionRule.TopSetPr(upperKg = 1.0, lowerKg = 3.0)
        assertEquals(1.0, change(LiftSlot.BENCH, 5, 5, rule = custom)!!.kgDelta!!, 1e-9)
        assertEquals(3.0, change(LiftSlot.DEADLIFT, 5, 5, rule = custom)!!.kgDelta!!, 1e-9)
        assertNull("un incremento cero no propone nada", change(LiftSlot.BENCH, 5, 5, rule = ProgressionRule.TopSetPr(0.0, 0.0)))
    }

    // ─── TopSetPr sobre una receta materializada ───────────────────────────────────

    /** Dos semanas iguales: top set de sentadilla (T1) y de banca (T2) a 1×5 al 100 % del TM, más un accesorio. */
    private fun topSetRecipe(rule: ProgressionRule = ProgressionRule.TopSetPr()): TrainingPlanRecipe {
        fun intensityDay() = day(
            label = "Intensidad",
            weekday = 1,
            slots = listOf(
                slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, listOf(SetRecipe(reps = 5, percent = 100.0, isTopSet = true)), 240, LiftSlot.SQUAT, isCompetitionLift = true),
                slot("bp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, listOf(SetRecipe(reps = 5, percent = 100.0, isTopSet = true)), 240, LiftSlot.BENCH, isCompetitionLift = true),
                slot("row", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, rpeSets(3, 8, 8.0), 120),
            ),
        )
        return TrainingPlanRecipe(
            id = "top-set-test",
            weeks = listOf(
                weekRecipe(1, 0, "Texas", BlockGoal.INTENSIFICATION, listOf(intensityDay())),
                weekRecipe(2, 0, "Texas", BlockGoal.INTENSIFICATION, listOf(intensityDay())),
            ),
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP, LiftSlot.DEADLIFT to CatalogIds.DL),
            progression = rule,
        )
    }

    private fun programOf(recipe: TrainingPlanRecipe, mode: AutoregulationMode = AutoregulationMode.PROPOSE): Program =
        PlanMaterializer.materialize(
            Program(
                id = "p-top",
                name = "Top set",
                structure = ProgramStructure.COMPLEX,
                powerliftingProfile = PowerliftingProfile(
                    squat1RM = 200.0, squatTM = 180.0, bench1RM = 140.0, benchTM = 126.0, deadlift1RM = 240.0, deadliftTM = 216.0,
                ),
                autoregulationMode = mode,
            ),
            recipe,
            metadata,
            SeqIds(),
            strict = false,
        ).copy(autoregulationMode = mode)

    private fun weeksOf(program: Program): List<ProgramWeek> =
        program.macrocycles.first().blocks.first().mesocycles.first().weeks

    /**
     * Un registro de la sesión de la semana 1 con una serie por ejercicio de sentadilla y banca. La carga es
     * la prescrita salvo que se indique ([squatKg], [benchKg]).
     */
    private fun topSetLog(
        program: Program,
        id: String,
        squatReps: Int,
        benchReps: Int,
        date: String = "2026-03-01T10:00:00.000Z",
        squatKg: Double? = null,
        benchKg: Double? = null,
    ): WorkoutLog {
        val week = weeksOf(program).first()
        val session = week.sessions.first()
        fun logged(configurationId: String, reps: Int, kg: Double?): CompletedExercise {
            val exercise = session.allExercises().first { it.catalogConfigurationId == configurationId }
            return CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                catalogConfigurationId = configurationId,
                sets = exercise.sets.mapIndexed { index, planned ->
                    CompletedSet(id = "${exercise.id}-$index", weight = kg ?: planned.weight ?: 100.0, reps = reps)
                },
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
            completedExercises = listOf(
                logged(CatalogIds.SQ_LOW, squatReps, squatKg),
                logged(CatalogIds.BP, benchReps, benchKg),
            ),
        )
    }

    private fun tmProposals(program: Program, recipe: TrainingPlanRecipe, vararg logs: WorkoutLog) =
        ProgramAutoregulationEngine.evaluate(program, weeksOf(program).first(), logs.toList(), recipe, signals)
            .filter { it.kind == AutoregulationProposalKind.ADJUST_TM }

    @Test
    fun the_top_sets_of_the_week_become_adjust_tm_proposals_in_kilos() {
        val recipe = topSetRecipe()
        val program = programOf(recipe)

        // Sentadilla 7 reps (objetivo 5: el doble, +5 kg) y banca 5 reps (objetivo: +1,25 kg).
        val proposals = tmProposals(program, recipe, topSetLog(program, "l1", squatReps = 7, benchReps = 5))

        val byLift = proposals.associateBy { it.liftSlot }
        assertEquals(setOf("SQUAT", "BENCH"), byLift.keys)
        assertEquals(5.0, byLift.getValue("SQUAT").kgDelta!!, 1e-9)
        assertEquals("Top set de sentadilla: 7 reps (objetivo 5): TM +5 kg", byLift.getValue("SQUAT").explanation)
        assertEquals(1.25, byLift.getValue("BENCH").kgDelta!!, 1e-9)
        assertEquals("Top set de banca: 5 reps (objetivo 5): TM +1,25 kg", byLift.getValue("BENCH").explanation)
        assertTrue(proposals.all { it.percentDelta == null })
    }

    @Test
    fun a_top_set_two_reps_short_lowers_the_tm_by_a_percent_and_a_lighter_load_never_raises_it() {
        val recipe = topSetRecipe()
        val program = programOf(recipe)

        // Sentadilla 3 reps donde se pedían 5; banca 9 reps pero con 100 kg en vez de los 126 prescritos.
        val proposals = tmProposals(program, recipe, topSetLog(program, "l1", squatReps = 3, benchReps = 9, benchKg = 100.0))

        val squat = proposals.single()
        assertEquals("SQUAT", squat.liftSlot)
        assertEquals(-2.5, squat.percentDelta!!, 1e-9)
        assertNull(squat.kgDelta)
        assertEquals("Top set de sentadilla: 3 reps (objetivo 5): TM -2,5 %", squat.explanation)
    }

    @Test
    fun only_a_top_set_pr_recipe_reads_the_top_sets() {
        listOf(ProgressionRule.None, ProgressionRule.AmrapDrivenTm(), ProgressionRule.CycleIncrement(2.5, 5.0)).forEach { rule ->
            val recipe = topSetRecipe(rule)
            val program = programOf(recipe)
            val proposals = tmProposals(program, recipe, topSetLog(program, "l1", squatReps = 9, benchReps = 9))
            assertTrue("${rule::class.simpleName}: $proposals", proposals.isEmpty())
        }
    }

    @Test
    fun the_most_recent_log_of_the_session_decides_the_top_set() {
        val recipe = topSetRecipe()
        val program = programOf(recipe)
        val older = topSetLog(program, "old", squatReps = 3, benchReps = 5, date = "2026-03-01T10:00:00.000Z")
        val newer = topSetLog(program, "new", squatReps = 5, benchReps = 5, date = "2026-03-08T10:00:00.000Z")

        val proposals = tmProposals(program, recipe, newer, older)

        // Con el registro antiguo (3 reps) bajaría el TM; el reciente (5 reps) lo sube.
        assertEquals(2.5, proposals.first { it.liftSlot == "SQUAT" }.kgDelta!!, 1e-9)
    }

    @Test
    fun a_top_set_proposal_respects_off_propose_and_auto() {
        val recipe = topSetRecipe()

        // OFF: ni propuesta ni cambio.
        val off = programOf(recipe, AutoregulationMode.OFF)
        assertTrue(
            ProgramAutoregulationEngine.evaluate(
                off, weeksOf(off).first(), listOf(topSetLog(off, "l1", 5, 5)), recipe, signals,
            ).isEmpty(),
        )

        // PROPOSE: las dos propuestas quedan pendientes y el TM no se toca hasta aceptar.
        val propose = programOf(recipe, AutoregulationMode.PROPOSE)
        val weeks = weeksOf(propose)
        val proposals = ProgramAutoregulationEngine.evaluate(
            propose, weeks.first(), listOf(topSetLog(propose, "l1", 5, 5)), recipe, signals,
        )
        val pending = ProgramAutoregulationEngine.apply(propose, weeks[1].id, proposals, recipe, setOf(weeks.first().id), metadata)
        assertFalse(pending.applied)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, pending.program.runState?.pendingAction?.type)
        assertEquals(2, pending.program.runState?.pendingAction?.proposals?.size)
        assertEquals(180.0, pending.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(126.0, pending.program.powerliftingProfile!!.benchTM!!, 1e-9)

        val accepted = ProgramAutoregulationEngine.resolvePending(pending.program, accept = true, metadata)
        assertEquals("180 + 2,5", 182.5, accepted.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals("126 + 1,25 redondea al medio kilo", 127.5, accepted.powerliftingProfile!!.benchTM!!, 1e-9)
        assertEquals(200.0, accepted.powerliftingProfile!!.squat1RM!!, 1e-9)

        // AUTO: aplica, audita y la semana siguiente (top set al 100 % del TM) toma el TM nuevo.
        val auto = programOf(recipe, AutoregulationMode.AUTO)
        val autoWeeks = weeksOf(auto)
        val applied = ProgramAutoregulationEngine.apply(auto, autoWeeks[1].id, proposals, recipe, setOf(autoWeeks.first().id), metadata)
        assertTrue(applied.applied)
        assertEquals(182.5, applied.program.powerliftingProfile!!.squatTM!!, 1e-9)
        val audit = applied.program.runState?.autoregulationAudit.orEmpty().filter { it.resolution != null }
        assertEquals(2, audit.size)
        assertTrue(audit.all { it.resolution == PendingActionResolutionStatus.APPLIED })
        assertTrue(audit.any { it.resolutionReason.startsWith("Aplicada: Top set de sentadilla") })
        val nextTopSet = weeksOf(applied.program)[1].sessions.first().allExercises().first().sets.first().weight!!
        assertEquals(182.5, nextTopSet, 1e-6)
    }

    /** Terminar la semana por el motor de progreso lee los top sets de los registros: sin señales precalculadas. */
    @Test
    fun finishing_a_week_through_the_progress_engine_reads_the_top_sets_from_the_logs() {
        val recipe = topSetRecipe()
        val program = programOf(recipe, AutoregulationMode.AUTO)
        val week1 = weeksOf(program).first()
        val session = week1.sessions.first()
        val log = topSetLog(program, "l1", squatReps = 5, benchReps = 5)

        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program.copy(runState = ProgramRunState(runId = "run-ts", weekId = week1.id)),
            activeState = null,
            completedSession = session,
            weekInstanceId = week1.id,
            logs = listOf(log),
            compositionMetadata = metadata,
        )

        assertTrue(result.advancedWeek)
        assertEquals(setOf("SQUAT", "BENCH"), result.autoregulationProposals.map { it.liftSlot }.toSet())
        // AUTO: los kilos del top set se aplican al TM al avanzar de semana.
        assertEquals(182.5, result.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(127.5, result.program.powerliftingProfile!!.benchTM!!, 1e-9)
        val nextTopSet = weeksOf(result.program)[1].sessions.first().allExercises().first().sets.first().weight!!
        assertEquals("el top set de la semana siguiente toma el TM nuevo", 182.5, nextTopSet, 1e-6)
    }

    // ─── La regla de RepMaxAutoregulated ───────────────────────────────────────────

    @Test
    fun a_rep_max_well_above_the_implied_one_rm_raises_the_tm_and_one_well_below_lowers_it() {
        val up = ProgramAutoregulationEngine.repMaxTmChange(estimatedOneRmKg = 216.0, impliedOneRmKg = 200.0)!!
        assertEquals(2.5, up.percentDelta!!, 1e-9)
        assertNull(up.kgDelta)
        val down = ProgramAutoregulationEngine.repMaxTmChange(estimatedOneRmKg = 174.9, impliedOneRmKg = 200.0)!!
        assertEquals(-2.5, down.percentDelta!!, 1e-9)
        // Entre el 95 % y el 102,5 % del 1RM implícito no cambia nada.
        listOf(196.0, 200.0, 204.0).forEach { e1rm ->
            assertNull("$e1rm", ProgramAutoregulationEngine.repMaxTmChange(e1rm, 200.0))
        }
        assertNull("sin 1RM implícito no hay referencia", ProgramAutoregulationEngine.repMaxTmChange(216.0, 0.0))
    }

    // ─── RepMaxAutoregulated sobre una receta materializada ────────────────────────

    /** Una sola semana con una serie al máximo (base REP_MAX) de [configurationId] declarada como [lift]. */
    private fun repMaxRecipe(configurationId: String = CatalogIds.SQ_LOW, lift: LiftSlot = LiftSlot.SQUAT): TrainingPlanRecipe =
        TrainingPlanRecipe(
            id = "rep-max-test",
            weeks = listOf(
                weekRecipe(
                    1, 0, "Máximo", BlockGoal.INTENSIFICATION,
                    listOf(
                        day(
                            label = "Máximo",
                            weekday = 1,
                            slots = listOf(
                                slot(
                                    "me", SlotRole.T1_MAIN, configurationId,
                                    listOf(SetRecipe(reps = 2, percent = 90.0, isTopSet = true, loadBasis = LoadBasis.REP_MAX)),
                                    240, lift, isCompetitionLift = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP, LiftSlot.DEADLIFT to CatalogIds.DL),
            progression = ProgressionRule.RepMaxAutoregulated,
        )

    /** Un registro con una sola serie del primer ejercicio de la sesión: [kg] × [reps]. */
    private fun repMaxLog(program: Program, kg: Double, reps: Int): WorkoutLog {
        val week = weeksOf(program).first()
        val session = week.sessions.first()
        val exercise = session.allExercises().first()
        return WorkoutLog(
            id = "rm-${kg.toInt()}-$reps",
            programId = program.id,
            sessionId = session.id,
            sessionName = session.name,
            date = "2026-03-01T10:00:00.000Z",
            durationMinutes = 60,
            weekId = week.id,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    catalogConfigurationId = exercise.catalogConfigurationId,
                    sets = listOf(CompletedSet(id = "rm-set", weight = kg, reps = reps)),
                ),
            ),
        )
    }

    @Test
    fun a_rep_max_set_proposes_a_percent_change_of_the_tm_against_the_one_rm_the_tm_implies() {
        val recipe = repMaxRecipe()
        val program = programOf(recipe)

        // 210 kg × 2 → e1RM 216 kg frente a los 200 kg (180 ÷ 0,9) que implica el TM: +2,5 %.
        val raise = tmProposals(program, recipe, repMaxLog(program, 210.0, 2)).single()
        assertEquals("SQUAT", raise.liftSlot)
        assertEquals(2.5, raise.percentDelta!!, 1e-9)
        assertNull(raise.kgDelta)
        assertEquals(
            "Serie al máximo de sentadilla: e1RM 216 kg frente a los 200 kg de 1RM que implica tu TM: TM +2,5 %",
            raise.explanation,
        )

        // 170 kg × 2 → e1RM 174,9 kg: muy por debajo, baja el TM un 2,5 %.
        val drop = tmProposals(program, recipe, repMaxLog(program, 170.0, 2)).single()
        assertEquals(-2.5, drop.percentDelta!!, 1e-9)
        assertEquals(
            "Serie al máximo de sentadilla: e1RM 174,9 kg frente a los 200 kg de 1RM que implica tu TM: TM -2,5 %",
            drop.explanation,
        )

        // 190 kg × 2 → e1RM 195,4 kg, dentro del margen: sin propuesta.
        assertTrue(tmProposals(program, recipe, repMaxLog(program, 190.0, 2)).isEmpty())
    }

    @Test
    fun the_reference_follows_the_tm_so_a_raise_does_not_repeat_forever() {
        val recipe = repMaxRecipe()
        val program = programOf(recipe)
        // Con el TM de partida (180 kg → 200 kg implícitos) los 210 kg × 2 (e1RM 216 kg) suben el TM.
        assertEquals(1, tmProposals(program, recipe, repMaxLog(program, 210.0, 2)).size)
        // Con el TM ya en 190 kg el 1RM implícito es 211,1 kg: el mismo e1RM queda a un 2,3 % y no vuelve a subir.
        // Con el 1RM fijo del perfil (200 kg) la misma serie subiría el TM cada semana sin parar.
        val raised = program.copy(powerliftingProfile = program.powerliftingProfile!!.copy(squatTM = 190.0))
        assertTrue(tmProposals(raised, recipe, repMaxLog(raised, 210.0, 2)).isEmpty())
    }

    @Test
    fun a_rep_max_of_a_variant_or_of_a_high_rep_set_or_a_percent_top_set_is_ignored() {
        // El máximo en cajón no se compara con el 1RM de la sentadilla de competición.
        val box = repMaxRecipe(configurationId = CatalogIds.SQ_BOX)
        val boxProgram = programOf(box)
        assertTrue(tmProposals(boxProgram, box, repMaxLog(boxProgram, 300.0, 2)).isEmpty())

        // 12 repeticiones: la estimación del 1RM ya no es fiable.
        val recipe = repMaxRecipe()
        val program = programOf(recipe)
        assertTrue(tmProposals(program, recipe, repMaxLog(program, 160.0, 12)).isEmpty())

        // Un top set con porcentaje no es una serie al máximo, aunque la receta use RepMaxAutoregulated.
        val percent = topSetRecipe(ProgressionRule.RepMaxAutoregulated)
        val percentProgram = programOf(percent)
        assertTrue(tmProposals(percentProgram, percent, topSetLog(percentProgram, "l1", squatReps = 3, benchReps = 3, squatKg = 300.0)).isEmpty())
    }

    @Test
    fun a_rep_max_proposal_respects_off_and_auto() {
        val recipe = repMaxRecipe()

        val off = programOf(recipe, AutoregulationMode.OFF)
        assertTrue(
            ProgramAutoregulationEngine.evaluate(
                off, weeksOf(off).first(), listOf(repMaxLog(off, 210.0, 2)), recipe, signals,
            ).isEmpty(),
        )

        val auto = programOf(recipe, AutoregulationMode.AUTO)
        val proposals = ProgramAutoregulationEngine.evaluate(
            auto, weeksOf(auto).first(), listOf(repMaxLog(auto, 210.0, 2)), recipe, signals,
        )
        val applied = ProgramAutoregulationEngine.apply(auto, null, proposals, recipe, emptySet(), metadata)
        assertTrue(applied.applied)
        assertEquals("180 × 1,025", 184.5, applied.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals("los demás TM no se tocan", 126.0, applied.program.powerliftingProfile!!.benchTM!!, 1e-9)
    }

    // ─── Recetas publicadas ────────────────────────────────────────────────────────

    /** Todas las semanas del programa materializado, en el orden de la receta. */
    private fun allWeeksOf(program: Program): List<ProgramWeek> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    /**
     * Para cada receta que la app publica (protocolos visibles y las autoradas con id de día), el
     * levantamiento de cada serie AMRAP y de cada top set sale del slot declarado en la receta, no del
     * texto del ejercicio: un `liftSlot` ausente (las dominadas de Texas) sigue sin levantamiento y uno
     * declarado en una variante (un rumano que declara peso muerto) se respeta. Es la prueba con datos
     * reales de la resolución por slot de `ProgramAutoregulationEngine`.
     */
    @Test
    fun published_recipes_resolve_the_lift_of_every_amrap_and_top_set_slot_from_the_recipe() {
        val recipes = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.mapNotNull { it.recipe } +
            AuthoredPhulPhatRecipes.all
        val problems = mutableListOf<String>()
        var materialized = 0
        var amrapSets = 0
        var topSets = 0
        recipes.forEach { recipe ->
            val program = runCatching {
                PlanMaterializer.materialize(
                    Program(
                        id = "pub-${recipe.id}",
                        name = recipe.id,
                        structure = ProgramStructure.COMPLEX,
                        powerliftingProfile = PowerliftingProfile(
                            squat1RM = 200.0, bench1RM = 140.0, deadlift1RM = 240.0, overhead1RM = 90.0,
                        ),
                    ),
                    recipe,
                    metadata,
                    SeqIds(),
                    strict = false,
                )
            }.getOrNull()
            if (program == null) {
                problems += "${recipe.id}: no se materializa"
                return@forEach
            }
            materialized += 1
            val weeks = allWeeksOf(program)
            recipe.weeks.forEach { recipeWeek ->
                val week = weeks.firstOrNull { it.progressionIndex == recipeWeek.weekNumber } ?: return@forEach
                recipeWeek.days.forEachIndexed { dayIndex, recipeDay ->
                    val session = week.sessions.getOrNull(dayIndex) ?: return@forEachIndexed
                    val exercises = session.allExercises()
                    if (exercises.size != recipeDay.slots.size) return@forEachIndexed
                    val marked = exercises.indices.filter { index ->
                        exercises[index].sets.any { it.isAmrap || it.isTopSet }
                    }
                    if (marked.isEmpty()) return@forEachIndexed

                    // Un registro con todas las series de cada ejercicio marcado; la AMRAP marcada como realizada.
                    val log = WorkoutLog(
                        id = "log-${recipe.id}-${recipeWeek.weekNumber}-$dayIndex",
                        programId = program.id,
                        sessionId = session.id,
                        sessionName = session.name,
                        date = "2026-03-01T10:00:00.000Z",
                        durationMinutes = 60,
                        weekId = week.id,
                        completedExercises = marked.map { index ->
                            val exercise = exercises[index]
                            CompletedExercise(
                                exerciseId = exercise.id,
                                exerciseName = exercise.name,
                                catalogConfigurationId = exercise.catalogConfigurationId,
                                sets = exercise.sets.mapIndexed { setIndex, planned ->
                                    CompletedSet(
                                        id = "${exercise.id}-$setIndex",
                                        weight = planned.weight ?: 60.0,
                                        reps = planned.targetReps ?: planned.targetRepsRange?.min ?: 5,
                                        amrapPerformed = planned.isAmrap,
                                    )
                                },
                            )
                        },
                    )

                    val expectedAmrap = marked.flatMap { index ->
                        List(exercises[index].sets.count { it.isAmrap }) { recipeDay.slots[index].lift.liftSlot }
                    }
                    val actualAmrap = ProgramAutoregulationEngine
                        .collectAmrapHits(week, listOf(log), recipe = recipe)
                        .map { it.liftSlot }
                    amrapSets += expectedAmrap.size
                    if (expectedAmrap != actualAmrap) {
                        problems += "${recipe.id} s${recipeWeek.weekNumber} d${dayIndex + 1}: AMRAP esperado $expectedAmrap, obtenido $actualAmrap"
                    }

                    val expectedTopSets = marked.flatMap { index ->
                        val lift = recipeDay.slots[index].lift.liftSlot
                        val withReps = exercises[index].sets.count { it.isTopSet && (it.targetReps != null || it.targetRepsRange != null) }
                        if (lift == null) emptyList<LiftSlot>() else List(withReps) { lift }
                    }
                    val actualTopSets = ProgramAutoregulationEngine
                        .collectTopSetHits(week, listOf(log), recipe)
                        .map { it.liftSlot }
                    topSets += expectedTopSets.size
                    if (expectedTopSets != actualTopSets) {
                        problems += "${recipe.id} s${recipeWeek.weekNumber} d${dayIndex + 1}: top set esperado $expectedTopSets, obtenido $actualTopSets"
                    }
                }
            }
        }

        assertTrue("recetas materializadas: $materialized de ${recipes.size}", materialized >= 25)
        assertTrue("series AMRAP comprobadas: $amrapSets", amrapSets >= 10)
        assertTrue("top sets comprobados: $topSets", topSets >= 10)
        assertTrue("Levantamientos mal resueltos:\n${problems.joinToString("\n")}", problems.isEmpty())
    }
}
