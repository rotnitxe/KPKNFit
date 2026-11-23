package com.example.kpkn.domain.training

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopApreVariant
import com.example.kpkn.data.models.LoopMode
import com.example.kpkn.data.models.LoopModulationScope
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.LoopWaveStep
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutHeaderWidgets
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.programdetail.design.structure.programCycleTemplates
import com.example.kpkn.screens.workout.WorkoutEditingState
import com.example.kpkn.screens.workout.WorkoutSessionHydrator
import com.example.kpkn.screens.workout.WorkoutStep
import com.example.kpkn.screens.workout.WorkoutUiState
import com.example.kpkn.testsupport.SyncRoomExecutor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgressiveLoopEngineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        SyncRoomExecutor.install()
        context = ApplicationProvider.getApplicationContext()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        SyncRoomExecutor.uninstall()
    }

    @Test
    fun wavePresetsExposeFourDistinctWavesIncludingOndulacionRepsAndTemplatesMatch() {
        val presets = LoopWaveModulator.wavePresets()
        assertEquals(4, presets.size)
        val ids = presets.map { it.id }.toSet()
        assertTrue("onda-apre-10-6-3" in ids)
        assertTrue("progresion-rir-3-0" in ids)
        assertTrue("rampa-volumen-descarga" in ids)
        assertTrue("ondulacion-reps-12-6" in ids)

        val templateIds = programCycleTemplates.map { it.id }
        assertTrue("wave:ondulacion-reps-12-6" in templateIds)
        val rirTemplate = programCycleTemplates.first { it.id == "wave:progresion-rir-3-0" }
        assertTrue(!rirTemplate.description.contains("2,5"))
    }

    @Test
    fun resolveOneRmOrdersLogsByDateDescAndRejectsBlankExerciseNames() {
        val squatSets = Apre.block(100.0, ApreVariant.APRE_6).mapIndexed { idx, s ->
            ExerciseSet(
                id = "s${idx + 1}",
                weight = 80.0,
                targetReps = s.targetReps,
                amrapMinimumReps = s.minimumReps,
                purpose = s.purpose,
            )
        }
        val squat = Exercise(
            id = "sq",
            name = "Sentadilla",
            apreBlock = ApreBlockReference(xrmReps = 6, setIds = listOf("s1", "s2", "s3", "s4")),
            sets = squatSets,
        )
        val wave = Loop(
            id = "w1",
            title = "APRE",
            type = LoopType.CUSTOM,
            repeatEveryXLoops = 1,
            mode = LoopMode.PROGRESSIVE_WAVE,
            modulationScope = LoopModulationScope.APRE_EXERCISES,
            waveSteps = listOf(LoopWaveStep(weekNumber = 1, label = "S1", apreVariant = LoopApreVariant.APRE_6)),
        )
        val xrmCapture = { loadKg: Double ->
            com.example.kpkn.data.models.ApreXrmCapture(
                sourceExerciseId = "other-id",
                sourcePlannedSetId = "s4",
                context = com.example.kpkn.domain.calibration.CalibrationContext(
                    configurationId = "squat__barbell",
                    equipmentKey = "barbell",
                ),
                reference = com.example.kpkn.data.protocols.PlanLoadReference(
                    kind = com.example.kpkn.data.protocols.PlanLoadReferenceKind.OBSERVED_WORKING_SET,
                    configurationId = "squat__barbell",
                    state = com.example.kpkn.data.protocols.PlanLoadReferenceState.CAPTURED,
                    capturedLoadKg = loadKg,
                    capturedTargetReps = 6,
                ),
                incrementKg = 2.5,
            )
        }
        val olderLog = WorkoutLog(
            id = "log-old",
            programId = "p1",
            sessionId = "sess1",
            sessionName = "Pierna",
            date = "2026-10-01",
            durationMinutes = 45,
            completedExercises = listOf(
                com.example.kpkn.data.models.CompletedExercise(
                    exerciseId = "other-id",
                    exerciseName = "Sentadilla",
                    sets = listOf(CompletedSet(id = "cs1", weight = 100.0, reps = 6, apreXrm = xrmCapture(100.0))),
                ),
            ),
        )
        val newerLog = WorkoutLog(
            id = "log-new",
            programId = "p1",
            sessionId = "sess1",
            sessionName = "Pierna",
            date = "2026-10-15",
            durationMinutes = 45,
            completedExercises = listOf(
                com.example.kpkn.data.models.CompletedExercise(
                    exerciseId = "other-id",
                    exerciseName = "Sentadilla",
                    sets = listOf(CompletedSet(id = "cs2", weight = 150.0, reps = 6, apreXrm = xrmCapture(150.0))),
                ),
            ),
        )
        val program = Program(
            id = "p1",
            name = "Prog",
            loops = listOf(wave),
        )
        val baseSession = Session(id = "sess1", name = "Pierna", exercises = listOf(squat))

        val forward = LoopWaveModulator.applyActiveWavesToSession(
            program = program,
            session = baseSession,
            cycleNumber = 1,
            workoutLogs = listOf(olderLog, newerLog),
        )
        val reversed = LoopWaveModulator.applyActiveWavesToSession(
            program = program,
            session = baseSession,
            cycleNumber = 1,
            workoutLogs = listOf(newerLog, olderLog),
        )
        // Must pick newerLog (150kg captured XRM) regardless of log list order
        val s4Forward = forward.exercises.single().sets[3].weight ?: 0.0
        val s4Reversed = reversed.exercises.single().sets[3].weight ?: 0.0
        assertEquals(s4Forward, s4Reversed, 0.001)
        assertTrue("Expected wave S4 load derived from 150kg newer log (>120kg), got $s4Forward", s4Forward > 120.0)

        // Blank-name exercise must not match an unrelated blank-name log by name
        val blankNamed = squat.copy(id = "blank-ex", name = "   ", sets = squatSets.map { it.copy(weight = null) })
        val blankLog = WorkoutLog(
            id = "log-blank",
            programId = "p1",
            sessionId = "sess1",
            sessionName = "Pierna",
            date = "2026-10-20",
            durationMinutes = 45,
            completedExercises = listOf(
                com.example.kpkn.data.models.CompletedExercise(
                    exerciseId = "unrelated",
                    exerciseName = "",
                    sets = listOf(CompletedSet(id = "cs-b", weight = 200.0, reps = 6, apreXrm = xrmCapture(200.0))),
                ),
            ),
        )
        val blankApplied = LoopWaveModulator.applyActiveWavesToSession(
            program = program,
            session = Session(id = "sess-b", name = "Pierna", exercises = listOf(blankNamed)),
            cycleNumber = 1,
            workoutLogs = listOf(blankLog),
        )
        assertNull("Blank exercise name must not falsely match blank-name log", blankApplied.exercises.single().sets[3].weight)
    }

    @Test
    fun hydratorSkipsWaveModulationOnLoopWeeksAndAppliesOnRegularWeeks() = runBlocking {
        val repo = ProgramRepository.initForTests(context)
        val wave = Loop(
            id = "w-rir",
            title = "RIR wave",
            type = LoopType.CUSTOM,
            repeatEveryXLoops = 1,
            mode = LoopMode.PROGRESSIVE_WAVE,
            modulationScope = LoopModulationScope.ALL_EXERCISES,
            waveSteps = listOf(LoopWaveStep(weekNumber = 1, label = "S1 · RIR 1", targetRirOverride = 1)),
        )
        val regularSession = Session(
            id = "s-reg",
            name = "Regular",
            exercises = listOf(Exercise(id = "ex-r", name = "Press", sets = listOf(ExerciseSet(id = "sr1", targetRIR = 4)))),
        )
        val loopSession = Session(
            id = "s-loop",
            name = "Loop",
            exercises = listOf(Exercise(id = "ex-l", name = "Press", sets = listOf(ExerciseSet(id = "sl1", targetRIR = 4)))),
        )
        val program = Program(
            id = "prog-wave",
            name = "Ciclo",
            structure = ProgramStructure.SIMPLE,
            simpleProgramKind = SimpleProgramKind.CYCLIC,
            loops = listOf(wave),
            macrocycles = listOf(
                Macrocycle(
                    id = "m1",
                    name = "M1",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "B1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "mes1",
                                    name = "Mes1",
                                    weeks = listOf(
                                        ProgramWeek(id = "w-reg", name = "Normal", isLoopWeek = false, sessions = listOf(regularSession)),
                                        ProgramWeek(id = "w-loop", name = "Loop", isLoopWeek = true, sessions = listOf(loopSession)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        repo.addProgram(program)

        suspend fun runHydrator(sessionId: String): WorkoutUiState {
            var state = WorkoutUiState()
            val ports = object : WorkoutSessionHydrator.Ports {
                override fun sessionForActiveMode(base: Session, mode: WeekVariant): Session = base
                override fun sanitizeSessionLoadModes(session: Session): Session = session
                override fun normalizeSupersetsForWorkout(session: Session): Session = session
                override fun canonicalExerciseKey(exercise: Exercise): String = exercise.name.lowercase()
                override fun hydrateContextProfiles(exercises: List<Exercise>, resumedState: com.example.kpkn.data.models.OngoingWorkoutState?): Pair<Map<String, WorkoutContextProfile>, Map<String, String>> = emptyMap<String, WorkoutContextProfile>() to emptyMap()
                override fun migrateContextProfilesToTags(profiles: Map<String, WorkoutContextProfile>, exerciseKey: String): List<WorkoutTag> = emptyList()
                override fun mergeDurableTags(exerciseKey: String, resumed: List<WorkoutTag>, profiles: Map<String, WorkoutContextProfile>): List<WorkoutTag> = emptyList()
                override fun resolveResumePosition(exercises: List<Exercise>, completedSets: Map<String, CompletedSet>, preferredExerciseId: String?, preferredSetId: String?): Pair<Int, Int> = 0 to 0
                override fun parseWorkoutSetKey(key: String, exercises: List<Exercise>?): WorkoutSessionHydrator.ParsedWorkoutSetKey? = null
                override fun buildEditingStateForPosition(completedSets: Map<String, CompletedSet>, exercise: Exercise?, setIdx: Int, preferredSide: String?): WorkoutEditingState? = null
                override fun refreshLoadSuggestions(state: WorkoutUiState) = Unit
                override fun nextIncompleteStepAfter(state: WorkoutUiState, includeCurrent: Boolean): WorkoutStep? = null
                override fun firstIncompleteStep(state: WorkoutUiState): WorkoutStep? = null
                override fun startSessionTimer(remainingSeconds: Int) = Unit
                override fun workoutWidgetsSessionKey(): String = ""
                override fun bindActiveWorkoutHolder() = Unit
                override fun restAlertCapability(soundsEnabled: Boolean) = WorkoutSessionHydrator.RestAlertCapability(false, false, false)
                override fun openFinishSheet() = Unit
            }
            val hydrator = WorkoutSessionHydrator(
                repository = repo,
                programId = program.id,
                sessionId = sessionId,
                getState = { state },
                updateState = { state = it(state) },
                ports = ports,
            )
            hydrator.loadSession()
            return state
        }

        val regularHydrated = runHydrator("s-reg")
        assertEquals(1, regularHydrated.session!!.exercises.single().sets.single().targetRIR)

        val loopHydrated = runHydrator("s-loop")
        assertEquals(4, loopHydrated.session!!.exercises.single().sets.single().targetRIR)
    }
}
