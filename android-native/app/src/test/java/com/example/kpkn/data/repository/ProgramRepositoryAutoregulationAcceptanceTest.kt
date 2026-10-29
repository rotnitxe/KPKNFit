package com.example.kpkn.data.repository

import com.example.kpkn.testsupport.SyncRoomExecutor
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.PlanMaterializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AC-G1/AC-G4/§14.5 por la ruta de repositorio+progreso: registrar una sesión
 * real → avanzar → aceptar/rechazar propuestas. La semana futura cambia de forma
 * observable (kilos derivados del nuevo TM), la semana entrenada queda byte idéntica
 * (contraejemplo R-202) y toda resolución persiste su estado terminal con motivo.
 * POST-RINGS: la propuesta de cambio es un `ADJUST_TM`; las de escala del modelo retirado
 * (SCALE_WEEK_*) ya no se aplican (ver ProgramAutoregulationResolutionTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramRepositoryAutoregulationAcceptanceTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            CatalogCompositionTestSupport.install()
        }
    }

    @Before
    fun setUp() {
        SyncRoomExecutor.install()
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
        SyncRoomExecutor.uninstall()
    }

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "repo_${++n}"
    }

    private fun recipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "repo-recipe",
        weeks = listOf(
            weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, listOf(DayArchetypes.plSquat(80.0, t1Amrap = false, weekday = 1))),
            weekRecipe(2, 0, "Base", BlockGoal.ACCUMULATION, listOf(DayArchetypes.plSquat(82.5, t1Amrap = false, weekday = 1))),
        ),
        trainingMaxPercent = 0.90,
        liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP, LiftSlot.DEADLIFT to CatalogIds.DL),
    )

    private fun weeksOf(program: Program) =
        program.macrocycles.first().blocks.first().mesocycles.first().weeks

    private fun percentsOf(program: Program, weekId: String): List<Double?> =
        weeksOf(program).first { it.id == weekId }
            .sessions.flatMap { it.allExercises() }
            .flatMap { it.sets }
            .map { it.targetPercentageRM }

    /**
     * Propuesta vigente POST-RINGS: bajar un 5 % el TM de sentadilla. Las propuestas de escala del
     * modelo retirado (SCALE_WEEK_INTENSITY/VOLUME…) ya no se aplican: caducan sin cambiar el plan.
     */
    private fun intensityProposal() = AutoregulationProposal(
        kind = AutoregulationProposalKind.ADJUST_TM,
        liftSlot = LiftSlot.SQUAT.name,
        percentDelta = -5.0,
        explanation = "Rendimiento por debajo del objetivo: bajar un 5 % el TM de sentadilla",
    )

    /** Pesos (kg) de las series de los ejercicios de una semana cuya configuración cumple [filter]. */
    private fun weightsOf(
        program: Program,
        weekId: String,
        filter: (String?) -> Boolean = { true },
    ): List<Double?> =
        weeksOf(program).first { it.id == weekId }
            .sessions.flatMap { it.allExercises() }
            .filter { filter(it.catalogConfigurationId) }
            .flatMap { exercise -> exercise.sets.map { it.weight } }

    private fun squatWeightsOf(program: Program, weekId: String) =
        weightsOf(program, weekId) { it == CatalogIds.SQ_LOW }

    private fun nonSquatWeightsOf(program: Program, weekId: String) =
        weightsOf(program, weekId) { it != CatalogIds.SQ_LOW }

    private suspend fun seedProgram(repository: ProgramRepository): Pair<Program, ProgramWeek> {
        val program = PlanMaterializer.materialize(
            Program(
                id = "repo-r202",
                name = "R202",
                powerliftingProfile = PowerliftingProfile(
                    squat1RM = 200.0, squatTM = 180.0,
                    bench1RM = 140.0, benchTM = 126.0,
                    deadlift1RM = 240.0, deadliftTM = 216.0,
                ),
                autoregulationMode = AutoregulationMode.OFF,
            ),
            recipe(),
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            strict = false,
        )
        val weeks = weeksOf(program)
        val week1 = weeks[0]
        val macro = program.macrocycles.first()
        val block = macro.blocks.first()
        val meso = block.mesocycles.first()
        val run = ProgramRunState(
            runId = "run-repo",
            cycleNumber = 1,
            weekInstanceId = week1.id,
            weekId = week1.id,
            macrocycleId = macro.id,
            blockId = block.id,
            mesocycleId = meso.id,
        )
        val seeded = program.copy(runState = run)
        repository.addProgram(seeded)
        withTimeout(10_000) { repository.programs.first { it.any { item -> item.id == seeded.id } } }
        repository.updateActiveProgramState(
            ActiveProgramState(
                programId = seeded.id,
                status = ProgramStatus.ACTIVE,
                currentWeekId = week1.id,
                currentWeekInstanceId = week1.id,
                currentMacrocycleId = macro.id,
                currentBlockId = block.id,
                currentMesocycleId = meso.id,
                programRunId = run.runId,
            ),
        )
        repository.updateProgramNow(seeded)
        return seeded to week1
    }

    private fun log(program: Program, weekId: String, sessionId: String, suffix: String): WorkoutLog = WorkoutLog(
        id = "log-$suffix",
        programId = program.id,
        sessionId = sessionId,
        sessionName = sessionId,
        date = "2026-08-21T10:00:00Z",
        durationMinutes = 45,
        weekId = weekId,
        weekInstanceId = weekId,
        programRunId = program.runState?.runId,
    )

    @Test
    fun advance_then_accept_changes_the_future_week_and_keeps_the_trained_week_byte_identical() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val (seeded, week1) = seedProgram(repository)
        val weeks = weeksOf(seeded)
        val week2 = weeks[1]
        val sessionId = week1.sessions.first().id

        // 1) Registro real de la sesión de la semana 1 → avance de cursor.
        repository.finalizeWorkout(log(seeded, week1.id, sessionId, "a"))
        val advanced = repository.getProgramById(seeded.id) ?: error("programa ausente tras el avance")
        assertEquals(week2.id, advanced.runState?.weekId)
        assertNull("OFF no genera propuestas", advanced.runState?.pendingAction)

        // 2) Evidencia real: la semana entrenada sí, la próxima semana NO.
        val evidence = repository.executedTrainingEvidence(advanced)
        assertTrue("La semana entrenada es evidencia", week1.id in evidence.weekIds)
        assertFalse("La semana vacía jamás es evidencia (R-202)", week2.id in evidence.weekIds)
        assertTrue(sessionId in evidence.sessionIds)

        // 3) Estado real de la app: PROPOSE con la propuesta de TM pendiente de la
        //    semana a la que acababa de apuntar el avance.
        val pending = PendingProgramAction(
            type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
            message = "Propuesta: bajar un 5 % el TM de sentadilla",
            proposals = listOf(intensityProposal()),
            targetWeekId = week2.id,
        )
        repository.mutateProgramNow(seeded.id) { current ->
            current.copy(
                autoregulationMode = AutoregulationMode.PROPOSE,
                runState = current.runState?.copy(pendingAction = pending),
            )
        }
        val beforeAccept = repository.getProgramById(seeded.id) ?: error("programa ausente")
        val trainedBefore = weeksOf(beforeAccept)[0].sessions
        val percentsBefore = percentsOf(beforeAccept, week2.id)
        assertTrue(percentsBefore.any { it != null })
        val squatTmBefore = requireNotNull(beforeAccept.powerliftingProfile?.squatTM)
        val squatWeightsBefore = squatWeightsOf(beforeAccept, week2.id)
        val otherWeightsBefore = nonSquatWeightsOf(beforeAccept, week2.id)
        assertEquals("El TM de sentadilla del perfil sembrado", 180.0, squatTmBefore, 1e-9)
        assertEquals("Semana 2: 4 series de sentadilla al 82,5 % del TM", 4, squatWeightsBefore.size)
        assertEquals(180.0 * 0.825, squatWeightsBefore.first()!!, 1e-6)

        // 4) Aceptar por la ruta de repositorio (evidencia real + persistencia).
        assertTrue(repository.resolvePendingAutoregulationNow(seeded.id, accept = true))
        val accepted = repository.getProgramById(seeded.id) ?: error("programa ausente tras aceptar")

        assertNull(accepted.runState?.pendingAction)

        // ADJUST_TM (-5 %): el perfil baja el TM de sentadilla; los porcentajes de la receta no se
        // tocan (los kilos se derivan de TM x %), así que el cambio observable en la semana futura son
        // los kilos de la sentadilla. Press y peso muerto quedan igual.
        val squatTmAfter = requireNotNull(accepted.powerliftingProfile?.squatTM)
        assertEquals("El TM de sentadilla baja un 5 %", squatTmBefore * 0.95, squatTmAfter, 1e-6)
        assertEquals(
            "Press y peso muerto conservan su TM",
            beforeAccept.powerliftingProfile?.copy(squatTM = null),
            accepted.powerliftingProfile?.copy(squatTM = null),
        )
        assertEquals("Los porcentajes de la receta no cambian con un ajuste de TM", percentsBefore, percentsOf(accepted, week2.id))
        val squatWeightsAfter = squatWeightsOf(accepted, week2.id)
        assertTrue("La propuesta aceptada cambia la semana futura", squatWeightsBefore != squatWeightsAfter)
        assertEquals(squatWeightsBefore.size, squatWeightsAfter.size)
        squatWeightsAfter.forEach { assertEquals(squatTmAfter * 0.825, it!!, 1e-6) }
        assertEquals("El resto de ejercicios de la semana futura no cambia", otherWeightsBefore, nonSquatWeightsOf(accepted, week2.id))
        assertEquals("La semana entrenada queda byte idéntica", trainedBefore, weeksOf(accepted)[0].sessions)

        val applied = accepted.runState?.autoregulationAudit.orEmpty().filter {
            it.resolution == PendingActionResolutionStatus.APPLIED
        }
        assertTrue("Estado terminal APPLIED persistido", applied.isNotEmpty())
        assertTrue(applied.all { it.resolutionReason.isNotBlank() })
        assertEquals(
            "Como las series de la semana futura cambiaron de verdad, la receta efectiva viaja junto a las sesiones",
            1,
            accepted.effectiveWeekRecipes.size,
        )

        // 5) Persistencia real en Room (reinicio de lectura).
        val persisted = repository.databaseForTests().programDao().getById(seeded.id)?.toProgram()
            ?: error("lectura de Room ausente")
        assertNull(persisted.runState?.pendingAction)
        assertTrue(persisted.runState?.autoregulationAudit.orEmpty().any { it.resolution == PendingActionResolutionStatus.APPLIED })
        assertEquals(1, persisted.effectiveWeekRecipes.size)
        assertEquals(squatTmAfter, persisted.powerliftingProfile?.squatTM)
        assertEquals(squatWeightsAfter, squatWeightsOf(persisted, week2.id))
        assertEquals(trainedBefore, weeksOf(persisted)[0].sessions)
    }

    @Test
    fun reject_by_repository_records_a_terminal_state_and_keeps_the_plan_unchanged() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val (seeded, week1) = seedProgram(repository)
        val week2 = weeksOf(seeded)[1]
        repository.finalizeWorkout(log(seeded, week1.id, week1.sessions.first().id, "b"))
        repository.mutateProgramNow(seeded.id) { current ->
            current.copy(
                autoregulationMode = AutoregulationMode.PROPOSE,
                runState = current.runState?.copy(
                    pendingAction = PendingProgramAction(
                        type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                        message = "Propuesta: bajar un 5 % el TM de sentadilla",
                        proposals = listOf(intensityProposal()),
                        targetWeekId = week2.id,
                    ),
                ),
            )
        }
        val before = repository.getProgramById(seeded.id) ?: error("programa ausente")

        assertTrue(repository.resolvePendingAutoregulationNow(seeded.id, accept = false))
        val rejected = repository.getProgramById(seeded.id) ?: error("programa ausente tras rechazar")

        assertNull(rejected.runState?.pendingAction)
        assertEquals("Rechazar no muta la receta", before.macrocycles, rejected.macrocycles)
        val resolutions = rejected.runState?.autoregulationAudit.orEmpty().filter { it.resolution != null }
        assertTrue(resolutions.any { it.resolution == PendingActionResolutionStatus.REJECTED })
        assertTrue(resolutions.all { it.resolutionReason.isNotBlank() })
        assertEquals("No se registra receta efectiva al rechazar", 0, rejected.effectiveWeekRecipes.size)

        val persisted = repository.databaseForTests().programDao().getById(seeded.id)?.toProgram()
            ?: error("lectura de Room ausente")
        assertTrue(persisted.runState?.autoregulationAudit.orEmpty().any { it.resolution == PendingActionResolutionStatus.REJECTED })
    }
}
