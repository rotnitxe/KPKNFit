package com.example.kpkn.screens.home

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.TrainingVolumeFixtures
import com.example.kpkn.domain.training.TrainingWindow
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.screens.programdetail.ProgramDetailViewModel
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Las fuentes reales de Home y detalle comparten el historial y sus conteos de series. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class VolumeConsumersTest {
    @get:Rule val roomRule = RoomTestRule()
    private val store = ViewModelStore()
    private lateinit var repository: ProgramRepository

    private object NoCatalogImport : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase, context: Context, alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?, onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ): Boolean = false
    }

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        NutritionRepository.initForTests(context, NoCatalogImport)
        repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
    }

    @After
    fun tearDown() {
        store.clear()
        CompetitionRepository.closeInstance()
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun `las fuentes de Home y detalle conservan el conteo semanal al cambiar el historial`() = runBlocking {
        val f = TrainingVolumeFixtures
        val today = LocalDate.now()
        val weeks = listOf(f.week("w", today, sessions = 1))
        val program = f.program(weeks).copy(volumeRecommendations = listOf(VolumeRecommendation("Pectorales", 1, 1, 2)))
        repository.addProgramNow(program).getOrThrow()
        repository.startProgram(program.id)
        fun completed(sets: Int) = f.log("volumen", today.toString(), sets).let { log ->
            log.copy(
                fatigueLevel = 9, discomforts = listOf("Pectorales"),
                completedExercises = log.completedExercises.map { it.copy(effectiveMuscles = f.chest) },
            )
        }
        repository.addWorkoutLog(completed(12))
        val home = HomeViewModel().also { store.put("home", it) }
        val detail = ProgramDetailViewModel(program.id).also { store.put("detail", it) }
        // HomeScreen observes repository.history directly; detail exposes that same live source.
        assertEquals(program.id, withTimeout(10_000) { home.activeProgram.first { it != null } }!!.id)
        suspend fun assertCounts(expectedSets: Int, expectedWeekly: Double) = withTimeout(10_000) {
            for (flow in listOf(repository.history, detail.history)) {
                val logs = flow.first { history -> history.singleOrNull()?.completedExercises?.singleOrNull()?.sets?.size == expectedSets }
                val volume = VolumeCalculator.calculateCompletedVolume(logs, f.catalog, TrainingWindow.last28Days(f.zone, today))
                assertEquals(expectedWeekly, volume.muscles.getValue("Pectorales").sets, 0.0)
            }
        }
        assertCounts(12, 3.0)
        repository.addWorkoutLog(completed(8))
        assertCounts(8, 2.0)
    }
}
