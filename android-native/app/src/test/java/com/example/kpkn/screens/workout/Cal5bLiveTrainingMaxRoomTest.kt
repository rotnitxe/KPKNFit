package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ExerciseTrainingMax
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE,sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class Cal5bLiveTrainingMaxRoomTest {
    @get:Rule val room = RoomTestRule()
    private var fixture: LiveSessionVmFixture? = null
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); LiveSessionVmFixture.resetAugeRepository() }
    @After fun cleanup() {
        try { fixture?.close(); LiveSessionVmFixture.resetAugeRepository(); ProgramRepository.closeInstance(); KpknDatabase.closeInstance() }
        finally { Dispatchers.resetMain() }
    }
    private fun exercise(id: String,config: String = CatalogIds.OHP) = Exercise(id,id,
        catalogConfigurationId = config,loadQuantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
        trainingMode = TrainingMode.RM,sets = listOf(ExerciseSet("$id-set",targetReps = 5,
            targetPercentageRM = 80.0,loadBasis = LoadBasis.PERCENT_TM,weight = null)))
    private suspend fun open(): Pair<LiveSessionVmFixture,WorkoutViewModel> {
        val session = Session("tm-session","Fuerza",exercises = listOf(exercise("first"),exercise("second"),exercise("other",CatalogIds.OHP_SEATED)))
        val f = LiveSessionVmFixture(session).prepare().also { fixture = it }
        return f to f.open()
    }
    @Test fun confirmedGlobalTmHydratesBothCompatibleOccurrencesBeforeMissingDisappears() = runBlocking {
        val (f,vm) = open()
        assertEquals(3,vm.missingTrainingMaxes(vm.uiState.value.session!!).size)
        vm.confirmTrainingMax("first",100.0)
        withTimeout(20_000) { vm.uiState.first { it.session!!.allExercises().first().referenceTrainingMax == 100.0 } }
        val exercises = vm.uiState.value.session!!.allExercises()
        assertEquals(listOf(100.0,100.0,null),exercises.map { it.referenceTrainingMax })
        assertEquals(listOf(80.0,80.0,null),exercises.map { it.sets.single().weight })
        assertEquals(listOf("other"),vm.missingTrainingMaxes(vm.uiState.value.session!!).map { it.exercise.id })
        assertNull(exercises[2].reference1RM)
        val reopened = f.recreate(vm)
        assertEquals(listOf(100.0,100.0,null),reopened.uiState.value.session!!.allExercises().map { it.referenceTrainingMax })
        assertEquals(listOf(80.0,80.0,null),f.storedOngoing().session.allExercises().map { it.sets.single().weight })
    }
    @Test fun programRoomFailureKeepsBothMissingReferencesThenRetryUpdatesBoth() = runBlocking {
        val (f,vm) = open()
        val before = f.repository.getProgramById(f.programId)!!
        val db = f.repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_tm_insert BEFORE INSERT ON programs BEGIN SELECT RAISE(ABORT, 'tm'); END")
        db.execSQL("CREATE TRIGGER fail_tm_update BEFORE UPDATE ON programs BEGIN SELECT RAISE(ABORT, 'tm'); END")
        vm.confirmTrainingMax("first",100.0)
        withTimeout(20_000) { vm.uiState.first { it.workoutToastNotice?.startsWith("No se pudo guardar el TM") == true } }
        assertEquals(before,f.repository.getProgramById(f.programId))
        assertTrue(vm.uiState.value.session!!.allExercises().all { it.referenceTrainingMax == null })
        assertEquals(3,vm.missingTrainingMaxes(vm.uiState.value.session!!).size)
        db.execSQL("DROP TRIGGER fail_tm_insert"); db.execSQL("DROP TRIGGER fail_tm_update")
        vm.confirmTrainingMax("first",100.0)
        withTimeout(20_000) { vm.uiState.first { it.session!!.allExercises()[1].referenceTrainingMax == 100.0 } }
        val second = vm.uiState.value.session!!.allExercises()[1]
        assertEquals(100.0,ExerciseTrainingMax.capturedKg(f.repository.getProgramById(f.programId)!!,second,PlanLoadReferenceKind.EXERCISE_TM)!!,0.0)
        assertEquals(80.0,second.sets.single().weight!!,0.0)
    }
}
