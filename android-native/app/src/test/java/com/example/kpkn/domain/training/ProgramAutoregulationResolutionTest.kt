package com.example.kpkn.domain.training

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
