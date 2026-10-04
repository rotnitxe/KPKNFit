package com.example.kpkn.domain.training

import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.rirSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * §12.1/§14.4/§14.5 + AC-G1/AC-G4: la propuesta aceptada tiene efecto observable
 * en la semana objetivo y el entrenamiento ya realizado nunca se reescribe.
 *
 * Reproduce el contraejemplo estático R-202: `advance` deja el run apuntando a
 * la semana SIGUIENTE y `resolvePending` consideraba ejecutada exactamente esa
 * semana (`executed = setOf(runState.weekId)`), con lo que el guard de
 * `rematerializeWeek` devolvía el programa intacto y las propuestas aceptadas
 * nunca se materializaban.
 */
class ProgramAutoregulationResolutionTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "res_${++n}"
    }

    /** Día de receta sin NINGÚN porcentaje: toda la prescripción es RIR. */
    private fun rirSquatDay(weekday: Int): DayRecipe = day(
        label = "Sentadilla RIR",
        weekday = weekday,
        slots = listOf(
            slot(
                "sq",
                SlotRole.T1_MAIN,
                CatalogIds.SQ_LOW,
                rirSets(4, 5, 2, rest = 240),
                restSeconds = 240,
                liftSlot = LiftSlot.SQUAT,
                isCompetitionLift = true,
            ),
            slot(
                "dl-def",
                SlotRole.T2_SUPPLEMENTAL,
                CatalogIds.DL_DEF,
                rirSets(3, 6, 2),
                restSeconds = 180,
                liftSlot = LiftSlot.DEADLIFT,
            ),
        ),
    )

    /** Receta SIN series AMRAP: los hits no generan propuestas de TM. */
    private fun recipe(week2UsesRir: Boolean = false): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "resolution-recipe",
        weeks = listOf(
            weekRecipe(
                1, 0, "Base", com.example.kpkn.data.models.BlockGoal.ACCUMULATION,
                listOf(DayArchetypes.plSquat(80.0, t1Amrap = false, weekday = 1)),
            ),
            weekRecipe(
                2, 0, "Base", com.example.kpkn.data.models.BlockGoal.ACCUMULATION,
                listOf(
                    if (week2UsesRir) {
                        rirSquatDay(weekday = 1)
                    } else {
                        DayArchetypes.plSquat(82.5, t1Amrap = false, weekday = 1)
                    },
                ),
            ),
            weekRecipe(
                3, 0, "Base", com.example.kpkn.data.models.BlockGoal.ACCUMULATION,
                listOf(DayArchetypes.plSquat(84.0, t1Amrap = false, weekday = 1)),
            ),
        ),
        trainingMaxPercent = 0.90,
        liftSlots = mapOf(
            LiftSlot.SQUAT to CatalogIds.SQ_LOW,
            LiftSlot.BENCH to CatalogIds.BP,
            LiftSlot.DEADLIFT to CatalogIds.DL,
        ),
    )

    private fun materialized(recipe: TrainingPlanRecipe = recipe()): Program {
        val base = Program(
            id = "p-res",
            name = "Resolución",
            structure = ProgramStructure.COMPLEX,
            powerliftingProfile = PowerliftingProfile(
                squat1RM = 200.0, squatTM = 180.0,
                bench1RM = 140.0, benchTM = 126.0,
                deadlift1RM = 240.0, deadliftTM = 216.0,
            ),
            autoregulationMode = AutoregulationMode.PROPOSE,
        )
        return PlanMaterializer.materialize(
            base,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            strict = false,
        ).copy(autoregulationMode = AutoregulationMode.PROPOSE)
    }

    private fun weeksOf(program: Program) =
        program.macrocycles.first().blocks.first().mesocycles.first().weeks

    private fun log(program: Program, weekId: String, session: Session, id: String): WorkoutLog = WorkoutLog(
        id = id,
        programId = program.id,
        sessionId = session.id,
        sessionName = session.name,
        date = "2026-09-01T10:00:00.000Z",
        durationMinutes = 45,
        weekId = weekId,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = session.allExercises().first().id,
                exerciseName = session.allExercises().first().name,
                catalogConfigurationId = session.allExercises().first().catalogConfigurationId,
                sets = listOf(CompletedSet(id = "set-1", weight = 100.0, reps = 5)),
            ),
        ),
    )

    private fun percentsOf(weekId: String, program: Program): List<Double?> =
        weeksOf(program).first { it.id == weekId }
            .sessions.flatMap { it.allExercises() }
            .flatMap { it.sets }
            .map { it.targetPercentageRM }

    private fun intensityProposal() = AutoregulationProposal(
        kind = AutoregulationProposalKind.SCALE_WEEK_INTENSITY,
        percentDelta = -5.0,
        explanation = "Readiness 38 — bajar intensidad de la próxima semana",
    )

    private fun resolutions(program: Program) =
        program.runState?.autoregulationAudit.orEmpty().filter { it.resolution != null }

    /**
     * Avance real de semana con trabajo registrado → la semana 1 entrenada queda
     * intacta y la semana 2 (objetivo) sí se reconstruye al aceptar. Este test
     * FALLABA con la colisión R-202 (`executed = setOf(runState.weekId)`).
     */
    @Test
    fun r202_accepted_intensity_changes_the_next_week_and_keeps_the_trained_week_byte_identical() {
        val program = materialized()
        val week1 = weeksOf(program)[0]
        val week2 = weeksOf(program)[1]

        val advanced = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program.copy(
                runState = ProgramRunState(
                    runId = "run-r202",
                    cycleNumber = 1,
                    weekInstanceId = week1.id,
                    weekId = week1.id,
                    completedSessionIds = setOf(week1.sessions.first().id),
                ),
            ),
            activeState = null,
            completedSession = week1.sessions.first(),
            weekInstanceId = week1.id,
            logs = listOf(log(program, week1.id, week1.sessions.first(), "log-w1")),
            weeklySignals = WeeklyAutoregulationSignals(readinessScore = 38),
            compositionMetadata = CatalogCompositionTestSupport.metadata,
        )
        assertTrue("La semana 2 debe ser el nuevo cursor", advanced.advancedWeek)
        val pendingAction = advanced.program.runState?.pendingAction
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, pendingAction?.type)
        assertEquals(week2.id, pendingAction?.targetWeekId)
        assertEquals(
            "La semana vacía NO puede leerse como ejecutada (R-202)",
            emptySet<String>(),
            ProgramAutoregulationEngine.derivedExecutedWeekIds(advanced.program),
        )

        val trainedBefore = weeksOf(advanced.program)[0].sessions
        val percentsBefore = percentsOf(week2.id, advanced.program)
        assertTrue("La receta usa porcentajes", percentsBefore.any { it != null })

        val accepted = ProgramAutoregulationEngine.resolvePending(
            advanced.program,
            accept = true,
            metadata = CatalogCompositionTestSupport.metadata,
        )

        assertNull(accepted.runState?.pendingAction)
        val percentsAfter = percentsOf(week2.id, accepted)
        assertTrue(
            "La propuesta aceptada debe observarse en la semana objetivo",
            percentsBefore != percentsAfter,
        )
        assertTrue(
            "Semana 1 entrenada byte a byte intacta",
            weeksOf(accepted)[0].sessions == trainedBefore,
        )
        val applied = resolutions(accepted).filter {
            it.resolution == PendingActionResolutionStatus.APPLIED &&
                AutoregulationProposalKind.SCALE_WEEK_INTENSITY in it.kinds
        }
        assertTrue("Estado terminal APPLIED con motivo", applied.isNotEmpty())
        assertTrue(applied.all { it.resolutionReason.isNotBlank() })
    }

    /** AC-G4: rechazar deja estado terminal REJECTED con motivo; nada cambia. */
    @Test
    fun rejected_proposal_records_a_terminal_state_and_keeps_the_plan_unchanged() {
        val program = materialized()
        val week2 = weeksOf(program)[1]
        val seeded = program.copy(
            runState = ProgramRunState(
                runId = "run-reject",
                weekInstanceId = week2.id,
                weekId = week2.id,
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = "AUGE propone bajar intensidad",
                    proposals = listOf(intensityProposal()),
                    targetWeekId = week2.id,
                ),
            ),
        )
        val before = seeded.macrocycles

        val rejected = ProgramAutoregulationEngine.resolvePending(
            seeded,
            accept = false,
            metadata = CatalogCompositionTestSupport.metadata,
        )

        assertNull(rejected.runState?.pendingAction)
        assertEquals("Rechazar no muta la receta", before, rejected.macrocycles)
        val resolutions = resolutions(rejected)
        assertTrue(resolutions.any { it.resolution == PendingActionResolutionStatus.REJECTED })
        assertTrue(resolutions.first { it.resolution == PendingActionResolutionStatus.REJECTED }.resolutionReason.isNotBlank())
    }

    /** §14.5: la semana con sesiones entrenadas no se reescribe; expira con motivo. */
    @Test
    fun accepted_proposal_on_a_trained_target_week_expires_with_reason_instead_of_faking_apply() {
        val program = materialized()
        val week2 = weeksOf(program)[1]
        val seeded = program.copy(
            runState = ProgramRunState(
                runId = "run-trained",
                weekInstanceId = week2.id,
                weekId = week2.id,
                completedSessionIds = setOf(week2.sessions.first().id),
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = "AUGE propone bajar intensidad",
                    proposals = listOf(intensityProposal()),
                    targetWeekId = week2.id,
                ),
            ),
        )
        val percentsBefore = percentsOf(week2.id, seeded)
        val trainedSessions = week2.sessions

        val resolved = ProgramAutoregulationEngine.resolvePending(
            seeded,
            accept = true,
            metadata = CatalogCompositionTestSupport.metadata,
        )

        assertNull(resolved.runState?.pendingAction)
        assertEquals("La semana entrenada no cambia", percentsBefore, percentsOf(week2.id, resolved))
        assertEquals(trainedSessions, weeksOf(resolved)[1].sessions)
        val resolution = resolutions(resolved).single()
        assertEquals(PendingActionResolutionStatus.EXPIRED, resolution.resolution)
        assertTrue(resolution.resolutionReason.contains("entrenadas"))
    }

    /** §14.4/§12.4: RIR sin porcentaje nunca se declara «aplicada». */
    @Test
    fun rir_only_week_returns_not_applicable_instead_of_claiming_a_no_op() {
        val program = materialized(recipe(week2UsesRir = true))
        val week2 = weeksOf(program)[1]
        val seeded = program.copy(
            runState = ProgramRunState(
                runId = "run-rir",
                weekInstanceId = week2.id,
                weekId = week2.id,
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = "AUGE propone bajar intensidad",
                    proposals = listOf(intensityProposal()),
                    targetWeekId = week2.id,
                ),
            ),
        )
        val before = seeded.macrocycles

        val resolved = ProgramAutoregulationEngine.resolvePending(
            seeded,
            accept = true,
            metadata = CatalogCompositionTestSupport.metadata,
        )

        assertEquals("Sin porcentajes no hay mutación observable", before, resolved.macrocycles)
        assertNull(resolved.runState?.pendingAction)
        val resolution = resolutions(resolved).single()
        assertEquals(PendingActionResolutionStatus.EXPIRED, resolution.resolution)
        assertTrue(
            "Debe explicar que no aplica (§12.4)",
            resolution.resolutionReason.contains("No aplicable"),
        )
        assertFalse(
            resolutions(resolved).any { it.resolution == PendingActionResolutionStatus.APPLIED },
        )
    }

    // ─── B.S4: propuestas de TM en kilos ───────────────────────────────────────────

    private fun pendingWith(program: Program, week2Id: String, vararg proposals: AutoregulationProposal): Program =
        program.copy(
            runState = ProgramRunState(
                runId = "run-kg",
                weekInstanceId = week2Id,
                weekId = week2Id,
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = "AUGE propone ajustar el TM",
                    proposals = proposals.toList(),
                    targetWeekId = week2Id,
                ),
            ),
        )

    private fun firstSquatKg(program: Program, weekIndex: Int): Double =
        weeksOf(program)[weekIndex].sessions.first().allExercises().first().sets.first().weight!!

    /** Una propuesta en kilos deja el TM cambiado en kilos, estado terminal APPLIED y la semana siguiente con el TM nuevo. */
    @Test
    fun an_accepted_kilo_proposal_raises_the_tm_by_the_kilos_and_the_next_week_follows() {
        val program = materialized()
        val week2 = weeksOf(program)[1]
        val proposal = AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_TM,
            liftSlot = "SQUAT",
            kgDelta = 5.0,
            explanation = "AMRAP 95 % de sentadilla: 5 reps (objetivo 1+): TM +5 kg",
        )
        val seeded = pendingWith(program, week2.id, proposal)
        assertEquals("antes: 82,5 % de 180", 0.825 * 180.0, firstSquatKg(seeded, 1), 1e-6)

        val accepted = ProgramAutoregulationEngine.resolvePending(
            seeded,
            accept = true,
            metadata = CatalogCompositionTestSupport.metadata,
        )

        val profile = accepted.powerliftingProfile!!
        assertEquals("el TM sube los kilos de la propuesta", 185.0, profile.squatTM!!, 1e-9)
        assertEquals("los otros TM no se tocan", 126.0, profile.benchTM!!, 1e-9)
        assertEquals("el 1RM no se toca", 200.0, profile.squat1RM!!, 1e-9)
        assertEquals("después: 82,5 % de 185", 0.825 * 185.0, firstSquatKg(accepted, 1), 1e-6)
        assertNull(accepted.runState?.pendingAction)
        val entry = resolutions(accepted).single()
        assertEquals(PendingActionResolutionStatus.APPLIED, entry.resolution)
        assertTrue(entry.resolutionReason, entry.resolutionReason.startsWith("Aplicada: AMRAP 95 % de sentadilla"))
    }

    @Test
    fun the_kilos_win_over_the_percent_when_a_proposal_carries_both() {
        val program = materialized()
        val week2 = weeksOf(program)[1]
        val both = AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_TM,
            liftSlot = "SQUAT",
            percentDelta = -2.5,
            kgDelta = 2.5,
            explanation = "mixta",
        )

        val accepted = ProgramAutoregulationEngine.resolvePending(
            pendingWith(program, week2.id, both),
            accept = true,
            metadata = CatalogCompositionTestSupport.metadata,
        )

        assertEquals(182.5, accepted.powerliftingProfile!!.squatTM!!, 1e-9)
    }

    @Test
    fun a_percent_proposal_still_scales_the_tm_as_before() {
        val program = materialized()
        val week2 = weeksOf(program)[1]
        val percent = AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_TM,
            liftSlot = "SQUAT",
            percentDelta = -2.5,
            explanation = "AMRAP corto",
        )

        val accepted = ProgramAutoregulationEngine.resolvePending(
            pendingWith(program, week2.id, percent),
            accept = true,
            metadata = CatalogCompositionTestSupport.metadata,
        )

        assertEquals(180.0 * 0.975, accepted.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(PendingActionResolutionStatus.APPLIED, resolutions(accepted).single().resolution)
    }

    /** `outcomeEntries` marca APPLIED solo el TM que de verdad cambió: una propuesta sin efecto no se declara aplicada. */
    @Test
    fun outcome_entries_mark_applied_only_the_tm_that_really_changed() {
        val before = materialized()
        val raise = AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_TM, liftSlot = "SQUAT", kgDelta = 5.0, explanation = "sube sentadilla",
        )
        // El perfil no tiene press militar: esa propuesta no puede cambiar nada.
        val noTm = AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_TM, liftSlot = "OVERHEAD", kgDelta = 2.5, explanation = "sube press militar",
        )
        val after = before.copy(powerliftingProfile = before.powerliftingProfile!!.copy(squatTM = 185.0))

        val entries = ProgramAutoregulationEngine.outcomeEntries(
            before = before,
            after = after,
            proposals = listOf(raise, noTm),
            targetWeekId = null,
            protectedWeekIds = emptySet(),
            mode = AutoregulationMode.AUTO,
            nowMs = 1L,
        )

        assertEquals(PendingActionResolutionStatus.APPLIED, entries[0].resolution)
        assertEquals(PendingActionResolutionStatus.EXPIRED, entries[1].resolution)
        assertTrue(entries[1].resolutionReason, entries[1].resolutionReason.contains("perfil de cargas"))
    }

    @Test
    fun a_kilo_change_rounds_to_half_a_kilo_and_derives_the_tm_from_the_one_rm_when_none_is_stored() {
        fun withKg(profile: PowerliftingProfile, lift: LiftSlot, kg: Double): PowerliftingProfile =
            ProgramAutoregulationEngine.applyTmKgDelta(profile, lift, kg, 0.9)

        // 1,25 kg de TopSetPr en banca: 126 + 1,25 = 127,25 → 127,5.
        assertEquals(127.5, withKg(PowerliftingProfile(benchTM = 126.0), LiftSlot.BENCH, 1.25).benchTM!!, 1e-9)
        // Sin TM guardado sale del 1RM por el porcentaje de la receta: 200 × 0,9 + 5 = 185.
        assertEquals(185.0, withKg(PowerliftingProfile(squat1RM = 200.0), LiftSlot.SQUAT, 5.0).squatTM!!, 1e-9)
        // Bajar kilos tampoco cruza el TM actual al redondear.
        assertEquals(123.5, withKg(PowerliftingProfile(benchTM = 126.0), LiftSlot.BENCH, -2.5).benchTM!!, 1e-9)
        // Sin TM ni 1RM del levantamiento no hay nada que ajustar.
        assertEquals(PowerliftingProfile(), withKg(PowerliftingProfile(), LiftSlot.SQUAT, 5.0))
        // El 1RM y los demás TM no se tocan.
        val changed = withKg(PowerliftingProfile(squat1RM = 200.0, squatTM = 180.0, benchTM = 126.0), LiftSlot.SQUAT, 2.5)
        assertEquals(PowerliftingProfile(squat1RM = 200.0, squatTM = 182.5, benchTM = 126.0), changed)
    }

    @Test
    fun kilo_proposals_survive_json_and_the_old_json_without_the_field_still_decodes() {
        val withKg = AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_TM,
            liftSlot = "BENCH",
            kgDelta = 1.25,
            explanation = "Top set de banca: 5 reps (objetivo 5): TM +1,25 kg",
        )
        val pending = PendingProgramAction(
            type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
            message = "AUGE propone ajustar el TM",
            proposals = listOf(withKg),
            targetWeekId = "w2",
        )
        val roundTrip = dbJson.decodeFromString(
            PendingProgramAction.serializer(),
            dbJson.encodeToString(PendingProgramAction.serializer(), pending),
        )
        assertEquals(pending, roundTrip)
        assertEquals(1.25, roundTrip.proposals.single().kgDelta!!, 1e-9)

        // Una propuesta pendiente guardada antes de B.S4 (en porcentaje, sin `kgDelta`) se lee igual.
        val legacy = dbJson.decodeFromString(
            AutoregulationProposal.serializer(),
            """{"kind":"ADJUST_TM","liftSlot":"SQUAT","percentDelta":-2.5,"explanation":"AMRAP corto"}""",
        )
        assertNull(legacy.kgDelta)
        assertEquals(-2.5, legacy.percentDelta!!, 1e-9)
    }

    /** AC-G4: avanzar sin resolver deja la propuesta EXPIRADA con motivo, nunca fuera. */
    @Test
    fun advancing_without_resolving_expires_the_pending_action_with_a_reason() {
        val program = materialized()
        val weeks = weeksOf(program)
        val week1 = weeks[0]
        val seeded = program.copy(
            runState = ProgramRunState(
                runId = "run-expire",
                cycleNumber = 1,
                weekInstanceId = week1.id,
                weekId = week1.id,
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = "AUGE propone bajar intensidad",
                    proposals = listOf(intensityProposal()),
                    targetWeekId = weeks[1].id,
                ),
            ),
        )

        val advanced = ProgramProgressEngine.advanceAfterSessionComplete(
            program = seeded,
            activeState = null,
            completedSession = week1.sessions.first(),
            weekInstanceId = week1.id,
            logs = listOf(log(seeded, week1.id, week1.sessions.first(), "log-expire")),
            weeklySignals = WeeklyAutoregulationSignals(readinessScore = 80),
            compositionMetadata = CatalogCompositionTestSupport.metadata,
        )

        assertNull(advanced.program.runState?.pendingAction)
        val expired = resolutions(advanced.program).filter { it.resolution == PendingActionResolutionStatus.EXPIRED }
        assertTrue("La propuesta pendiente expira con motivo", expired.isNotEmpty())
        assertTrue(expired.all { it.resolutionReason.contains("Caducada") })
        // Y la nueva semana genera su propia propuesta pendiente (si AUGE la emite),
        // sin perder las entradas anteriores.
        assertTrue(advanced.program.runState?.autoregulationAudit.orEmpty().isNotEmpty())
    }
}
