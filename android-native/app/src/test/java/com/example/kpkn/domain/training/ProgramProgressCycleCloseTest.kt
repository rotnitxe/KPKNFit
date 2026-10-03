package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationAuditEntry
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NativeProgressionIdentity
import com.example.kpkn.data.models.NativeProgressionProposal
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.NativeProgressionSpec
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §12.1 + AC-G3/AC-G4: al cerrar ciclo la progresión propia se procesa UNA sola
 * vez (sin duplicar ocurrencias ni propuestas en cierres repetidos) y ninguna
 * acción pendiente se descarta en silencio: queda expirada con motivo y el audit
 * append-only sobrevive al cierre.
 */
class ProgramProgressCycleCloseTest {

    private fun recipe(nativeProgression: Boolean): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "cycle-recipe",
        weeks = listOf(
            weekRecipe(1, 0, "Base", com.example.kpkn.data.models.BlockGoal.ACCUMULATION, emptyList()),
            weekRecipe(2, 0, "Base", com.example.kpkn.data.models.BlockGoal.ACCUMULATION, emptyList()),
        ),
        repeats = true,
        nativeProgression = if (nativeProgression) NativeProgressionSpec() else null,
    )

    private fun baseProgram(nativeProgression: Boolean = true): Program = Program(
        id = "cycle-prog",
        name = "Ciclo",
        structure = ProgramStructure.SIMPLE,
        sourceRecipe = recipe(nativeProgression),
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Meso",
                                weeks = listOf(
                                    com.example.kpkn.data.models.ProgramWeek(
                                        id = "w1", name = "Semana 1",
                                        sessions = listOf(Session(id = "s1", name = "Día 1", dayOfWeek = 1, isMainSession = true)),
                                    ),
                                    com.example.kpkn.data.models.ProgramWeek(
                                        id = "w2", name = "Semana 2",
                                        sessions = listOf(Session(id = "s2", name = "Día 2", dayOfWeek = 3, isMainSession = true)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    ).copy(
        runState = ProgramRunState(
            runId = "run-cycle",
            cycleNumber = 1,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
            weekId = "w1",
        ),
    )

    private fun cycle1Logs(): List<WorkoutLog> = listOf(
        WorkoutLog(
            id = "log-1", programId = "cycle-prog", sessionId = "s1", sessionName = "Día 1",
            date = "2026-01-01T10:00:00.000Z", durationMinutes = 45, weekId = "w1",
            cycleNumber = 1, weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
        ),
        WorkoutLog(
            id = "log-2", programId = "cycle-prog", sessionId = "s2", sessionName = "Día 2",
            date = "2026-01-03T10:00:00.000Z", durationMinutes = 45, weekId = "w2",
            cycleNumber = 1, weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2"),
        ),
    )

    @Test
    fun cycle_close_registers_native_progression_exactly_once() {
        val result = ProgramProgressEngine.completeCycle(baseProgram(), null, 1, cycle1Logs())

        assertTrue(result.advancedCycle)
        assertEquals(2, result.program.runState?.cycleNumber)
        assertEquals(ProgramProgressEngine.instanceIdFor(2, "w1"), result.program.runState?.weekInstanceId)

        val continuations = result.program.effectiveWeekRecipes.filter { entry ->
            entry.appliedProposals.any { it.kind == "NATIVE_PROGRESSION" }
        }
        assertEquals("La continuación se registra una sola vez", 1, continuations.size)
        assertEquals(1, continuations.single().appliedProposals.size)
        assertEquals(2, continuations.single().cycleNumber)
        // La continuación se ancla en la primera semana del ciclo nuevo (antes comparaba un valor consigo mismo).
        assertEquals(1, continuations.single().weekOccurrence)
        assertEquals(
            ProgramProgressEngine.instanceIdFor(2, "w1"),
            result.program.runState?.weekInstanceId,
        )
        val occurrenceKeys = result.program.effectiveWeekRecipes.map { it.weekOccurrence to it.cycleNumber }
        assertEquals(occurrenceKeys, occurrenceKeys.distinct())
    }

    @Test
    fun recipe_without_native_progression_registers_nothing_at_cycle_close() {
        val result = ProgramProgressEngine.completeCycle(baseProgram(nativeProgression = false), null, 1, cycle1Logs())
        assertTrue(result.advancedCycle)
        assertTrue(result.program.effectiveWeekRecipes.isEmpty())
    }

    @Test
    fun repeated_cycle_close_does_not_duplicate_occurrence_ids_or_proposals() {
        val first = ProgramProgressEngine.completeCycle(baseProgram(), null, 1, cycle1Logs())
        val second = ProgramProgressEngine.completeCycle(first.program, null, 1, cycle1Logs())

        assertEquals(
            "La receta efectiva no se duplica",
            first.program.effectiveWeekRecipes,
            second.program.effectiveWeekRecipes,
        )
        assertEquals(
            "Las ocurrencias son deterministas",
            first.program.runState?.weekInstanceId,
            second.program.runState?.weekInstanceId,
        )
        assertEquals(
            "El audit no crece con cierres repetidos",
            first.program.runState?.autoregulationAudit,
            second.program.runState?.autoregulationAudit,
        )
        assertEquals(2, second.program.runState?.cycleNumber)
    }

    @Test
    fun cycle_close_expires_pending_autoregulation_with_a_reason_and_keeps_the_audit() {
        val proposal = AutoregulationProposal(
            kind = AutoregulationProposalKind.SCALE_WEEK_INTENSITY,
            percentDelta = -2.5,
            explanation = "Readiness 40",
        )
        val generation = AutoregulationAuditEntry(
            atMs = 1_000L,
            mode = AutoregulationMode.PROPOSE,
            kinds = listOf(AutoregulationProposalKind.SCALE_WEEK_INTENSITY),
            weekId = "w2",
        )
        val program = baseProgram(nativeProgression = false).copy(
            runState = ProgramRunState(
                runId = "run-cycle",
                cycleNumber = 1,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
                weekId = "w1",
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = "AUGE propone bajar intensidad",
                    proposals = listOf(proposal),
                    targetWeekId = "w2",
                ),
                autoregulationAudit = listOf(generation),
            ),
        )

        val result = ProgramProgressEngine.completeCycle(program, null, 1, cycle1Logs())

        assertNull("Nada queda pendiente en silencio", result.program.runState?.pendingAction)
        val audit = result.program.runState?.autoregulationAudit.orEmpty()
        assertEquals("La entrada de generación se conserva", 1, audit.count { it.resolution == null })
        val expired = audit.filter { it.resolution == PendingActionResolutionStatus.EXPIRED }
        assertEquals(1, expired.size)
        assertTrue(expired.single().resolutionReason.contains("Caducada"))
        assertEquals(listOf(AutoregulationProposalKind.SCALE_WEEK_INTENSITY), expired.single().kinds)
    }

    private fun pendingNativeProposal(): NativeProgressionProposal = NativeProgressionProposal(
        proposalId = "native-pending-1",
        kind = NativeProgressionProposalKind.INCREASE_LOAD,
        identity = NativeProgressionIdentity(
            recipeId = "cycle-recipe",
            recipeContentVersion = 1,
            recipeDayId = "d1",
            recipeSlotId = "s1",
            configurationId = "bench_press__barbell",
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            side = "bilateral",
            execution = "",
            slotPurpose = "H",
        ),
        sourceSessionId = "s1",
        sourceLogIds = listOf("log-1", "log-2"),
        explanation = "pendiente",
        createdAtMs = 1L,
    )

    @Test
    fun cycle_close_expires_pending_native_proposals_with_a_reason_and_consumes_their_logs() {
        val program = baseProgram().copy(nativeProgressionProposals = listOf(pendingNativeProposal()))

        val result = ProgramProgressEngine.completeCycle(program, null, 1, cycle1Logs())

        assertTrue("nada queda pendiente en silencio", result.program.nativeProgressionProposals.isEmpty())
        val resolution = result.program.nativeProgressionAudit.single { it.proposalId == "native-pending-1" }
        assertEquals(NativeProgressionResolutionStatus.EXPIRED, resolution.status)
        assertTrue(resolution.reason.contains("Caducada al cerrar el ciclo 1"))
        assertEquals(listOf("log-1", "log-2"), resolution.sourceLogIds)
        // H-CICLO: la caducidad queda en el registro pero NO se muestra; el atleta recibe UN solo
        // aviso al entrar en el ciclo siguiente, con la duración real del programa.
        assertFalse("la caducidad no se suma al aviso de nuevo bloque", resolution.userFacingNotice)
        val visible = result.program.nativeProgressionAudit.filter { it.userFacingNotice }
        assertEquals(1, visible.size)
        assertEquals(NativeProgressionResolutionStatus.NOTICE, visible.single().status)
        assertEquals("native-cycle-c2", visible.single().proposalId)
        assertTrue(visible.single().reason, visible.single().reason.startsWith("Empiezas un nuevo bloque de 2 semanas con tus últimas cargas."))
        assertTrue("avisa que la propuesta sin responder caducó", visible.single().reason.contains("caducaron"))

        // Cierre repetido: la guarda `native-progression-c2` evita duplicar avisos o reabrir propuestas.
        val again = ProgramProgressEngine.completeCycle(result.program, null, 1, cycle1Logs())
        assertEquals(result.program.nativeProgressionAudit, again.program.nativeProgressionAudit)
        assertTrue(again.program.nativeProgressionProposals.isEmpty())
    }

    @Test
    fun recipe_without_native_progression_keeps_its_native_proposals_untouched_at_cycle_close() {
        // Defensa: sin `nativeProgression` el motor de ciclo no interpreta datos nativos ajenos.
        val program = baseProgram(nativeProgression = false).copy(nativeProgressionProposals = listOf(pendingNativeProposal()))

        val result = ProgramProgressEngine.completeCycle(program, null, 1, cycle1Logs())

        assertTrue(result.advancedCycle)
        assertEquals(program.nativeProgressionProposals, result.program.nativeProgressionProposals)
        assertTrue(result.program.nativeProgressionAudit.isEmpty())
    }
}
