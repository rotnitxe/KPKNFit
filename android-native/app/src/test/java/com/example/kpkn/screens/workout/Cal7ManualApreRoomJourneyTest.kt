package com.example.kpkn.screens.workout

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.calibration.CalibrationStatus
import com.example.kpkn.domain.training.Apre
import com.example.kpkn.domain.training.ApreVariant
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ExerciseLoadReferenceStore
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.screens.programdetail.ProgramDetailViewModel
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.testsupport.awaitRepositoryScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** Tres modos APRE manuales, ciclo simple de una semana y recorrido real de la variante B. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class Cal7ManualApreRoomJourneyTest {
    @get:Rule val room = RoomTestRule()
    private val fixtures = mutableListOf<Cal7ManualApreRoomFixture>()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        LiveSessionVmFixture.resetAugeRepository()
    }

    @After fun cleanup() {
        try {
            runBlocking {
                try { fixtures.forEach { it.close() } }
                finally {
                    try { fixtures.firstOrNull()?.preparedRepository?.let { awaitRepositoryScope(it) } }
                    finally {
                        try { LiveSessionVmFixture.resetAugeRepository() }
                        finally {
                            try { ProgramRepository.closeInstance() }
                            finally { KpknDatabase.closeInstance() }
                        }
                    }
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun journey(mode: AutoregulationMode) {
        val f = Cal7ManualApreRoomFixture(mode).prepare().also { fixtures += it }
        assertNull("El programa del atleta no depende de una receta", f.program.sourceRecipe)
        assertNull(f.program.sourceProtocolId)
        val sourceSession = f.week1.sessions.single()
        val vm = f.open(sourceSession)
        val log = f.completeApreAndFinish(vm, Instant.now().minusSeconds(3_600))
        val capture = captureOf(log)
        assertEquals(f.programId, capture.reference.sourceProgramId)
        assertEquals(f.apreExerciseId, capture.sourceExerciseId)
        assertEquals("cal7-apre-week1-s4", capture.sourcePlannedSetId)
        assertEquals(125.0, capture.reference.capturedLoadKg!!, 0.0)
        assertEquals(log.completedExercises.flatMap { it.sets }.single { it.apreXrm != null }.id,
            capture.reference.metadata!!.sourceId)
        assertEquals(capture.sourcePlannedSetId, capture.reference.metadata!!.sourceSetId)
        assertEquals("El cierre conserva la captura S4 en historial durable", log,
            f.repository.history.value.single { it.id == log.id })

        var current = f.program
        val pending = current.runState?.pendingAction
        val targetWeekId = f.week2.id
        when (mode) {
            AutoregulationMode.OFF -> {
                assertNull(pending)
                assertTrue(current.runState!!.autoregulationAudit.isEmpty())
                assertTrue(ExerciseLoadReferenceStore.history(current, f.apreExerciseId, capture.reference).isEmpty())
            }
            AutoregulationMode.PROPOSE -> {
                assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, pending!!.type)
                val proposal = assertSingleApreProposal(pending.proposals, capture)
                assertApreLoads(f, current, targetWeekId, f.apreSessionWeek2Id, 120.0)
                assertTrue(ExerciseLoadReferenceStore.history(current, f.apreExerciseId, capture.reference).isEmpty())
                f.accept(proposal)
                current = f.program
                assertNull(current.runState!!.pendingAction)
                assertAppliedAudit(current)
            }
            AutoregulationMode.AUTO -> {
                assertNull(pending)
                assertAppliedAudit(current)
            }
        }

        val expectedXrm = if (mode == AutoregulationMode.OFF) 120.0 else 125.0
        assertApreLoads(f, current, targetWeekId, f.apreSessionWeek2Id, expectedXrm)
        assertEquals("La sesión manual de la semana siguiente conserva su contenido",
            f.manualSessionWeek2, f.weeks(current).single { it.id == targetWeekId }.sessions.single {
                it.id == f.manualSessionWeek2.id
            })
        assertEquals(f.foreignReferences, current.exerciseLoadReferences.single {
            it.exerciseId == f.foreignExerciseId
        })
        assertNull(current.powerliftingProfile)
        if (mode != AutoregulationMode.OFF) {
            val history = ExerciseLoadReferenceStore.history(current, f.apreExerciseId, capture.reference)
            assertEquals(1, history.size)
            assertEquals(125.0, history.single().capturedLoadKg!!, 0.0)
            assertEquals(PlanLoadReferenceKind.OBSERVED_WORKING_SET, history.single().kind)
        }

        val nextVm = f.open(f.week2.sessions.single { it.id == f.apreSessionWeek2Id })
        val nextExercise = nextVm.uiState.value.session!!.allExercises().single { it.id == f.apreExerciseId }
        assertEquals(Apre.block(expectedXrm, ApreVariant.APRE_6).map { it.loadKg }, nextExercise.sets.map { it.weight })
        assertEquals("La ejecución real siguiente refleja el XRM del modo", expectedXrm,
            nextExercise.sets[2].weight!!, 0.0)
        assertEquals(log, f.repository.history.value.single { it.id == log.id })
    }

    @Test fun manualApreOffCompletesTheWeekWithoutApplyingAnXrm() = runBlocking {
        journey(AutoregulationMode.OFF)
    }

    @Test fun manualApreProposePersistsTheTypedCaptureUntilTheAthleteAcceptsIt() = runBlocking {
        journey(AutoregulationMode.PROPOSE)
    }

    @Test fun manualApreAutoAppliesTheS4CaptureToTheNextWeek() = runBlocking {
        journey(AutoregulationMode.AUTO)
    }

    @Test fun oneWeekSimpleCycleEvaluatesItsClosedApreRunBeforeReusingTheWeek() = runBlocking {
        val f = Cal7ManualApreRoomFixture(AutoregulationMode.PROPOSE, simpleCycle = true)
            .prepare().also { fixtures += it }
        assertNull(f.program.sourceRecipe)
        assertEquals(1, f.weeks(f.program).size)
        val closedRun = f.program.runState!!
        val log = f.completeApreAndFinish(f.open(f.week1.sessions.single()), Instant.now().minusSeconds(3_600))
        assertEquals(1, log.cycleNumber)
        assertEquals(closedRun.weekInstanceId, log.weekInstanceId)
        val after = f.program
        assertEquals(2, after.runState!!.cycleNumber)
        assertEquals(ProgramProgressEngine.instanceIdFor(2, f.week1.id), after.runState!!.weekInstanceId)
        val proposal = assertSingleApreProposal(after.runState!!.pendingAction!!.proposals, captureOf(log))
        assertEquals(AutoregulationProposalKind.ADJUST_XRM, proposal.kind)
        assertEquals(125.0, proposal.apreXrm!!.reference.capturedLoadKg!!, 0.0)
        assertTrue("La propuesta debe salir del log del ciclo recién cerrado",
            proposal.apreXrm!!.reference.metadata!!.sourceId == log.completedExercises.flatMap { it.sets }
                .single { it.apreXrm != null }.id)
    }

    @Test fun activeVariantBFinishesUnderBaseSessionIdentityAndChangesOnlyFutureB() = runBlocking {
        val f = Cal7ManualApreRoomFixture(AutoregulationMode.PROPOSE, activeVariantB = true)
            .prepare().also { fixtures += it }
        val baseWeek1 = f.week1.sessions.single()
        val activeBWeek1 = baseWeek1.sessionB!!
        assertNotEquals("La variante persistida tiene su propio ID", baseWeek1.id, activeBWeek1.id)
        val baseWeek2Before = f.week2.sessions.single { it.id == f.apreSessionWeek2Id }
        val unrelatedAExercisesBefore = baseWeek2Before.exercises
        val variantBWeek2Before = baseWeek2Before.sessionB!!
        val manualBExerciseBefore = variantBWeek2Before.allExercises().single {
            it.id == f.manualVariantBExerciseId
        }
        assertEquals(f.manualVariantBAspects, manualBExerciseBefore.selectedAspects)

        val log = f.completeApreAndFinish(
            f.open(baseWeek1),
            Instant.now().minusSeconds(3_600),
            activeMode = WeekVariant.B,
        )
        assertEquals("El ViewModel abre la sesión base A y guarda esa identidad", baseWeek1.id, log.sessionId)
        assertNotEquals(activeBWeek1.id, log.sessionId)
        val completedIds = log.completedExercises.map { it.exerciseId }.toSet()
        assertEquals(setOf(f.apreExerciseId, f.manualVariantBExerciseId), completedIds)
        val completedBManual = log.completedExercises.single { it.exerciseId == f.manualVariantBExerciseId }
        assertEquals(41.25, completedBManual.sets.single().weight, 0.0)
        assertEquals(f.manualVariantBAspects, completedBManual.selectedAspects)

        val capture = captureOf(log)
        assertEquals(f.programId, capture.reference.sourceProgramId)
        assertEquals(f.apreExerciseId, capture.sourceExerciseId)
        assertEquals("cal7-apre-week1-b-s4", capture.sourcePlannedSetId)
        assertEquals(125.0, capture.reference.capturedLoadKg!!, 0.0)
        val pending = f.program.runState!!.pendingAction!!
        val proposal = assertSingleApreProposal(pending.proposals, capture)
        assertApreLoads(f, f.program, f.week2.id, f.apreSessionWeek2Id, 120.0, WeekVariant.B)
        f.accept(proposal)

        val after = f.program
        val baseWeek2After = f.weeks(after).single { it.id == f.week2.id }.sessions.single {
            it.id == f.apreSessionWeek2Id
        }
        assertEquals("Solo se autorregula el contenido APRE de B", unrelatedAExercisesBefore,
            baseWeek2After.exercises)
        assertApreLoads(f, after, f.week2.id, f.apreSessionWeek2Id, 125.0, WeekVariant.B)
        val variantBWeek2After = baseWeek2After.sessionB!!
        assertEquals(manualBExerciseBefore, variantBWeek2After.allExercises().single {
            it.id == f.manualVariantBExerciseId
        })
        assertEquals(f.manualVariantBAspects, variantBWeek2After.allExercises().single {
            it.id == f.manualVariantBExerciseId
        }.selectedAspects)
        assertEquals(f.foreignReferences, after.exerciseLoadReferences.single {
            it.exerciseId == f.foreignExerciseId
        })
        assertEquals(f.manualSessionWeek2, f.weeks(after).single { it.id == f.week2.id }.sessions.single {
            it.id == f.manualSessionWeek2.id
        })
    }
    private fun assertSingleApreProposal(
        proposals: List<AutoregulationProposal>,
        capture: ApreXrmCapture,
    ): AutoregulationProposal {
        val proposal = proposals.single()
        assertEquals(AutoregulationProposalKind.ADJUST_XRM, proposal.kind)
        assertEquals(capture, proposal.apreXrm)
        assertEquals(125.0, proposal.apreXrm!!.reference.capturedLoadKg!!, 0.0)
        return proposal
    }

    private fun assertAppliedAudit(program: Program) {
        assertTrue(program.runState!!.autoregulationAudit.any {
            it.resolution == PendingActionResolutionStatus.APPLIED &&
                AutoregulationProposalKind.ADJUST_XRM in it.kinds
        })
    }

    private fun assertApreLoads(
        f: Cal7ManualApreRoomFixture,
        program: Program,
        weekId: String,
        sessionId: String,
        xrm: Double,
        variant: WeekVariant = WeekVariant.A,
    ) {
        val week = f.weeks(program).single { it.id == weekId }
        val baseSession = week.sessions.single { it.id == sessionId }
        val session = when (variant) {
            WeekVariant.A -> baseSession
            WeekVariant.B -> baseSession.sessionB!!
            WeekVariant.C -> baseSession.sessionC!!
            WeekVariant.D -> baseSession.sessionD!!
        }
        val exercise = session.allExercises().single { it.id == f.apreExerciseId }
        assertEquals(Apre.block(xrm, ApreVariant.APRE_6).map { it.loadKg }, exercise.sets.map { it.weight })
        assertNull("El XRM no se convierte en 1RM", exercise.reference1RM)
        assertNull("El XRM no se convierte en TM", exercise.referenceTrainingMax)
    }

    private fun captureOf(log: WorkoutLog): ApreXrmCapture =
        log.completedExercises.flatMap { it.sets }.single { it.apreXrm != null }.apreXrm!!

    private class Cal7ManualApreRoomFixture(
        private val mode: AutoregulationMode,
        private val simpleCycle: Boolean = false,
        private val activeVariantB: Boolean = false,
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val programId = "cal7-manual-apre-" + mode.name.lowercase() + if (simpleCycle) "-simple" else if (activeVariantB) "-variant-b" else ""
        val apreExerciseId = "cal7-manual-apre-squat"
        val foreignExerciseId = "cal7-apre-unrelated-exercise"
        private val week1Id = "cal7-apre-week1"
        private val week2Id = "cal7-apre-week2"
        val apreSessionWeek2Id = "cal7-apre-session-week2"
        val manualVariantBExerciseId = "cal7-apre-manual-press-b"
        val manualVariantBAspects = mapOf("agarre" to "neutro", "variante" to "B")
        val week1: ProgramWeek
        val week2: ProgramWeek
        val manualSessionWeek2: Session
        lateinit var repository: ProgramRepository
            private set
        val preparedRepository: ProgramRepository? get() = if (::repository.isInitialized) repository else null
        private val opened = mutableListOf<WorkoutViewModel>()
        private val details = mutableListOf<ProgramDetailViewModel>()
        lateinit var foreignReferences: ExerciseLoadReference
            private set

        init {
            fun sessionWithVariantB(baseId: String, baseName: String, setPrefix: String, weekSuffix: String) =
                Session(baseId, baseName, exercises = listOf(unrelatedAExercise(weekSuffix)),
                    sessionB = Session("$baseId-b", "$baseName · variante B", exercises = listOf(
                        apreExercise(setPrefix), manualVariantBExercise(weekSuffix))))
            val firstSession = if (activeVariantB) {
                sessionWithVariantB("cal7-apre-session-week1", "Sesión A · semana 1",
                    "cal7-apre-week1-b", "week1")
            } else Session("cal7-apre-session-week1", "APRE 6 · semana 1",
                exercises = listOf(apreExercise("cal7-apre-week1")))
            val nextSession = if (activeVariantB) {
                sessionWithVariantB(apreSessionWeek2Id, "Sesión A · semana 2",
                    "cal7-apre-week2-b", "week2")
            } else Session(apreSessionWeek2Id, "APRE 6 · semana 2",
                exercises = listOf(apreExercise("cal7-apre-week2")))
            manualSessionWeek2 = Session("cal7-apre-manual-session-week2", "Sesión manual protegida",
                exercises = listOf(Exercise("cal7-apre-manual-row", "Remo fijado por la atleta", sets = listOf(
                    ExerciseSet("cal7-apre-manual-row-s1", targetReps = 8, weight = 41.25,
                        loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)))))
            week1 = ProgramWeek(week1Id, "Semana 1", sessions = listOf(firstSession), progressionIndex = 1)
            week2 = ProgramWeek(week2Id, "Semana 2", sessions = if (simpleCycle) listOf(firstSession) else
                listOf(nextSession, manualSessionWeek2), progressionIndex = 2)
        }

        private fun apreExercise(setPrefix: String): Exercise {
            val rows = Apre.block(120.0, ApreVariant.APRE_6)
            val sets = rows.mapIndexed { index, row ->
                ExerciseSet(
                    id = setPrefix + "-s" + (index + 1),
                    targetReps = row.targetReps,
                    weight = row.loadKg,
                    purpose = row.purpose,
                    amrapMinimumReps = row.minimumReps,
                    loadModeV2 = LoadModeV2.LOAD,
                    unitModeV2 = UnitModeV2.REPS,
                )
            }
            return Exercise(
                id = apreExerciseId,
                name = "Sentadilla APRE 6",
                exerciseDbId = "barbell-squat",
                catalogConfigurationId = CatalogIds.SQ_LOW,
                slotRole = SlotRole.T1_MAIN,
                loadQuantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                sets = sets,
                apreBlock = ApreBlockReference(ApreVariant.APRE_6.xrmReps, sets.map { it.id }),
            )
        }


        private fun unrelatedAExercise(prefix: String) = Exercise(
            id = "cal7-apre-unrelated-a-bench",
            name = "Banca A ajena al APRE",
            sets = listOf(ExerciseSet("$prefix-a-bench-s1", targetReps = 8, weight = 62.5,
                loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)),
            selectedAspects = mapOf("agarre" to "cerrado", "variante" to "A"),
        )

        private fun manualVariantBExercise(prefix: String) = Exercise(
            id = manualVariantBExerciseId,
            name = "Press manual B con contexto",
            sets = listOf(ExerciseSet("cal7-apre-manual-b-$prefix-s1", targetReps = 8, weight = 41.25,
                loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)),
            selectedAspects = manualVariantBAspects,
            selectedExecutionOption = "agarre neutro",
        )

        suspend fun prepare(): Cal7ManualApreRoomFixture {
            CatalogCompositionTestSupport.install()
            repository = ProgramRepository.initForTests(context)
            withTimeout(10_000) { repository.isReady.first { it } }
            repository.resetAllStateSync()

            val macro = Macrocycle("cal7-apre-macro", "Programa manual", blocks = listOf(
                Block("cal7-apre-block", "Bloque APRE 6", mesocycles = listOf(
                    Mesocycle("cal7-apre-meso", "Semanas APRE",
                        weeks = if (simpleCycle) listOf(week1) else listOf(week1, week2))))))

            val run = ProgramRunState(
                runId = programId + "-run",
                cycleNumber = 1,
                weekId = week1.id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, week1.id),
                macrocycleId = macro.id,
                blockId = macro.blocks.single().id,
                mesocycleId = macro.blocks.single().mesocycles.single().id,
            )
            val manualProgram = Program(
                id = programId,
                name = "APRE 6 manual",
                structure = if (simpleCycle) ProgramStructure.SIMPLE else ProgramStructure.COMPLEX,
                macrocycles = listOf(macro),
                runState = run,
                autoregulationMode = mode,
                sourceRecipe = null,
                sourceProtocolId = null,
                manualSessionOverrides = if (simpleCycle) emptyList() else listOf(ManualSessionOverride(
                    manualSessionWeek2.id, weekId = week2.id, reason = "Carga elegida por la atleta CAL7")),
            )

            val foreign = PlanLoadReference(
                kind = PlanLoadReferenceKind.EXERCISE_TM,
                configurationId = CatalogIds.OHP_SEATED,
                quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                state = PlanLoadReferenceState.CAPTURED,
                capturedLoadKg = 45.0,
                capturedAtMs = 1L,
                metadata = PlanLoadReferenceMetadata(origin = ExerciseLoadReferenceStore.MANUAL,
                    sourceId = "cal7-apre-foreign", reason = "Referencia de una variante independiente"),
            )
            foreignReferences = ExerciseLoadReferenceStore.appendCapture(
                manualProgram, setOf(foreignExerciseId), foreign)
                .exerciseLoadReferences.single { it.exerciseId == foreignExerciseId }

            repository.addProgram(ExerciseLoadReferenceStore.appendCapture(manualProgram,
                setOf(foreignExerciseId), foreign))
            repository.flushPendingWrites()
            val weekLocation = ProgramHierarchyIndex(manualProgram).locateWeek(week1.id)!!
            repository.updateActiveProgramState(ActiveProgramState(
                programId = programId,
                status = ProgramStatus.ACTIVE,
                currentWeekId = week1.id,
                currentWeekInstanceId = run.weekInstanceId,
                currentMacrocycleId = macro.id,
                currentBlockId = weekLocation.blockId,
                currentMesocycleId = weekLocation.mesocycleId,
                currentCycleNumber = 1,
                programRunId = run.runId,
            ))
            return this
        }

        val program: Program get() = checkNotNull(repository.getProgramById(programId))
        fun weeks(program: Program = this.program): List<ProgramWeek> =
            program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

        suspend fun open(session: Session): WorkoutViewModel {
            val vm = WorkoutViewModel(context, programId, session.id, WorkoutRestAlertManager(context))
            opened += vm
            withTimeout(60_000) { vm.uiState.first { it.session != null && !it.isStartingWorkout } }
            withTimeout(30_000) { repository.ongoingWorkout.first { it != null } }
            assertEquals(session.id, vm.uiState.value.session!!.id)
            return vm
        }

        suspend fun completeApreAndFinish(
            vm: WorkoutViewModel,
            completionInstant: Instant,
            activeMode: WeekVariant = WeekVariant.A,
        ): WorkoutLog {
            if (activeMode != WeekVariant.A) {
                vm.setActiveMode(activeMode)
                withTimeout(20_000) { vm.uiState.first { it.activeMode == activeMode } }
            }
            fun activeSession(): Session {
                val base = vm.uiState.value.session!!
                return when (activeMode) {
                    WeekVariant.A -> base
                    WeekVariant.B -> base.sessionB ?: error("No hay variante B materializada")
                    WeekVariant.C -> base.sessionC ?: error("No hay variante C materializada")
                    WeekVariant.D -> base.sessionD ?: error("No hay variante D materializada")
                }
            }

            val exerciseIndex = activeSession().allExercises().indexOfFirst { it.id == apreExerciseId }
            assertTrue(exerciseIndex >= 0)
            val repetitions = listOf(10, 6, 9, 9)
            for (setIndex in repetitions.indices) {
                val exercise = activeSession().allExercises()[exerciseIndex]
                val set = exercise.sets[setIndex]
                vm.selectExercise(exerciseIndex)
                vm.jumpToSet(setIndex)
                val result = vm.recordSetV2(set.weight!!, repetitions[setIndex].toDouble(), 1.0,
                    SetAdvancedFeedback(rir = 1), LoadModeV2.LOAD, UnitModeV2.REPS, 75.0,
                    setIdxOverride = setIndex, expectedExerciseId = exercise.id, expectedSetIdx = setIndex)
                assertTrue("APRE S" + (setIndex + 1) + " debe entrar al grabador real: " + result,
                    result is RecordSetResult.Created)
                if (setIndex == 2) {
                    val completed = vm.uiState.value.completedSets.getValue(workoutSetKey(exercise.id, setIndex))
                    val decision = vm.uiState.value.calibrationDecisions.single {
                        it.sourceSetId == completed.id
                    }
                    assertEquals(122.5, decision.apre!!.set4LoadKg, 0.0)
                    vm.resolveCalibrationDecision(decision.id, CalibrationStatus.APPLIED)
                    withTimeout(20_000) { vm.uiState.first { it.calibrationActionId == null } }
                    assertNull(vm.uiState.value.calibrationError)
                    assertEquals(CalibrationStatus.APPLIED,
                        vm.uiState.value.calibrationDecisions.single { it.id == decision.id }.status)
                    val adjusted = activeSession().allExercises()[exerciseIndex].sets[3].weight!!
                    assertEquals("S3 120 kg × 9 debe ajustar S4 con Mann", 122.5, adjusted, 0.0)
                }
            }

            if (activeMode == WeekVariant.B) {
                val session = activeSession()
                val manualIndex = session.allExercises().indexOfFirst { it.id == manualVariantBExerciseId }
                assertTrue("La variante B contiene su accesorio manual", manualIndex >= 0)
                val exercise = session.allExercises()[manualIndex]
                exercise.sets.forEachIndexed { setIndex, set ->
                    vm.selectExercise(manualIndex)
                    vm.jumpToSet(setIndex)
                    val result = vm.recordSetV2(set.weight!!, (set.targetReps ?: 8).toDouble(), 1.0,
                        SetAdvancedFeedback(rir = 1), LoadModeV2.LOAD, UnitModeV2.REPS, 75.0,
                        setIdxOverride = setIndex, expectedExerciseId = exercise.id, expectedSetIdx = setIndex)
                    assertTrue("El accesorio manual de B también se registra por el ViewModel: $result",
                        result is RecordSetResult.Created)
                }
            }

            val completedS4 = vm.uiState.value.completedSets.getValue(workoutSetKey(apreExerciseId, 3))
            assertEquals("El S4 ejecutado usa la carga recalculada", 122.5, completedS4.weight, 0.0)
            assertEquals(125.0, completedS4.apreXrm!!.reference.capturedLoadKg!!, 0.0)
            val readinessDate = completionInstant.atZone(ZoneId.systemDefault()).toLocalDate()
            AugeRepository.getInstance(context).saveWellbeingLog(DailyWellbeingLog(
                "cal7-manual-apre-ready-" + completionInstant.toEpochMilli(), readinessDate.toString(),
                sleepQuality = 5, sleepHours = 8.0, stressLevel = 1, doms = 1, motivation = 5,
                manualNeuralBattery = 100, manualSpinalBattery = 100, manualMuscularBattery = 100,
                manualBatteryAnchorMs = completionInstant.toEpochMilli()))

            val baseSession = vm.uiState.value.session!!
            vm.finishWorkout("Recorrido CAL7 APRE manual", 5, SessionClosingFeedback(5, 0, 0, 0, emptyList(),
                completionInstantIso = completionInstant.toString()))
            val log = withTimeout(60_000) {
                repository.history.first { logs -> logs.any {
                    it.programId == programId && it.date == completionInstant.toString()
                } }.single {
                    it.programId == programId && it.date == completionInstant.toString()
                }
            }
            withTimeout(20_000) {
                vm.uiState.first { it.logAlreadyWrittenId != null || it.isComplete || it.showVolumeAdvanceModal }
            }
            if (vm.uiState.value.showVolumeAdvanceModal) vm.dismissVolumeAdvance()
            withTimeout(60_000) { repository.ongoingWorkout.first { it == null } }
            assertEquals(baseSession.id, log.sessionId)
            assertEquals(completionInstant.toString(), log.date)
            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            opened.remove(vm)
            return log
        }
        suspend fun accept(proposal: AutoregulationProposal) {
            val vm = ProgramDetailViewModel(programId).also { details += it }
            withTimeout(20_000) { vm.program.first { it != null } }
            vm.acceptAutoregulation(proposal)
            withTimeout(20_000) {
                repository.programs.first { programs ->
                    programs.single { it.id == programId }.runState?.pendingAction?.proposals?.contains(proposal) != true
                }
            }
            repository.flushPendingWrites()
        }

        suspend fun close() {
            val workoutViewModels = opened.toList()
            val detailViewModels = details.toList()
            opened.clear()
            details.clear()
            workoutViewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
            detailViewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
            preparedRepository?.flushPendingWrites()
        }
    }
}
