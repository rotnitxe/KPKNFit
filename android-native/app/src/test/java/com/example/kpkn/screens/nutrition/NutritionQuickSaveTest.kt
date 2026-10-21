package com.example.kpkn.screens.nutrition

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionActiveStateEntity
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toNutritionLog
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** El atajo y addLog esperan a la transacción real antes de publicar o abrir el onboarding. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionQuickSaveTest {
    @get:Rule
    val roomRule = RoomTestRule()

    private lateinit var repository: NutritionRepository
    private lateinit var db: KpknDatabase
    private lateinit var vm: NutritionViewModel

    private val noImport = object : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase,
            context: Context,
            alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?,
            onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ) = false
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        NutritionRepository.closeInstance()
        BodyProgressRepository.closeInstance()
        val programs = ProgramRepository.initForTests(context)
        awaitState("el repositorio de programas termina de cargar") { programs.isReady.value }
        repository = NutritionRepository.initForTests(context, noImport)
        runBlocking { repository.awaitStartupLoadForTests() }
        repository.stopCalendarForecastUpdates()
        db = repository.databaseForTests()
        vm = NutritionViewModel()
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        BodyProgressRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    private fun awaitState(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000L
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(description, condition())
    }

    private fun seedActivePlan(snapshot: DailyGoalSnapshot? = null) = runBlocking {
        val dao = db.nutritionDao()
        dao.upsertPlan(NutritionPlan(id = "current-plan", name = "Plan vigente", calorieTarget = 2600, isActive = true).toEntity())
        dao.upsertActiveState(NutritionActiveStateEntity(activePlanId = "current-plan"))
        if (snapshot != null) dao.insertDailyGoalSnapshot(snapshot.toEntity())
        repository.publishNutritionPlanCommit()
    }

    private fun rejectInserts(table: String) {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_save BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'Fallo de escritura de prueba'); END",
        )
    }

    private fun assertFailedSave() {
        awaitState("el usuario recibe el aviso de guardado fallido") { vm.uiMessage.value != null }
        assertEquals("No se pudo guardar la comida. Inténtalo de nuevo.", vm.uiMessage.value)
        assertTrue(repository.nutritionLogs.value.isEmpty())
        assertFalse("el fallo no abre el onboarding", vm.showCreatineOverlay.value)
    }

    @Test
    fun `el fallo de Room en el atajo no publica creatina ni abre el onboarding`() {
        rejectInserts("nutrition_logs")

        vm.quickAddCreatine()

        assertFailedSave()
        runBlocking {
            assertTrue(db.nutritionDao().getAllLogs().isEmpty())
            assertTrue(db.nutritionDao().getAllDailyGoalSnapshots().isEmpty())
        }
    }

    @Test
    fun `una base cerrada avisa del fallo del atajo sin publicar exito`() {
        db.close()

        vm.quickAddCreatine()

        assertFailedSave()
    }

    @Test
    fun `un fallo al fijar el objetivo revierte tambien la comida del atajo`() {
        seedActivePlan()
        rejectInserts("daily_goal_snapshots")

        vm.quickAddCreatine()

        assertFailedSave()
        runBlocking {
            assertTrue("la transacción revierte el log que ya había insertado", db.nutritionDao().getAllLogs().isEmpty())
            assertTrue(db.nutritionDao().getAllDailyGoalSnapshots().isEmpty())
        }
        assertTrue(repository.dailyGoalSnapshots.value.isEmpty())
    }

    @Test
    fun `el atajo espera al guardado y conserva el objetivo historico en vez del plan vigente`() = runBlocking {
        val historical = DailyGoalSnapshot(
            date = LocalDate.now().minusDays(2).toString(),
            planId = "historical-plan",
            calorieTargetKcal = 1900,
            capturedAtEpochMs = 1L,
        )
        seedActivePlan(historical)
        vm.setSelectedDate(historical.date)
        val saveMutex = NutritionRepository::class.java.getDeclaredField("foodSaveMutex")
            .apply { isAccessible = true }.get(repository) as Mutex

        saveMutex.lock()
        try {
            vm.quickAddCreatine()
            assertTrue(repository.nutritionLogs.value.isEmpty())
            assertTrue(db.nutritionDao().getAllLogs().isEmpty())
            assertFalse(vm.showCreatineOverlay.value)
            assertNull(vm.uiMessage.value)
        } finally {
            saveMutex.unlock()
        }
        awaitState("el onboarding solo aparece tras el guardado confirmado") { vm.showCreatineOverlay.value }

        val stored = db.nutritionDao().getAllLogs().single().toNutritionLog()
        assertEquals(historical.date, stored.date)
        assertEquals(5.0, stored.foods.sumOf { it.creatineG }, 0.0)
        assertEquals(listOf(stored), repository.nutritionLogs.value)
        assertEquals(historical, repository.getDailyGoalSnapshot(historical.date))
        assertEquals(listOf(historical), repository.dailyGoalSnapshots.value)
        assertEquals("current-plan", repository.activeNutritionPlan?.id)
        assertNull(vm.uiMessage.value)
    }

    @Test
    fun `el atajo retroactivo sin evidencia no captura el objetivo del plan actual`() {
        seedActivePlan()
        val yesterday = LocalDate.now().minusDays(1).toString()
        vm.setSelectedDate(yesterday)

        vm.quickAddCreatine()
        awaitState("el guardado termina y puede abrir el onboarding") { vm.showCreatineOverlay.value }

        assertEquals(yesterday, repository.nutritionLogs.value.single().date)
        runBlocking { assertNull(repository.getDailyGoalSnapshot(yesterday)) }
        assertTrue(repository.dailyGoalSnapshots.value.isEmpty())
    }

    @Test
    fun `reintentar addLog con el mismo id tras un fallo deja una sola fila`() {
        val log = NutritionLog(
            id = "retry-creatine",
            date = LocalDate.now().toString(),
            foods = listOf(LoggedFood(foodName = "Creatina", amount = 5.0, creatineG = 5.0)),
        )
        rejectInserts("nutrition_logs")
        vm.addLog(log)
        assertFailedSave()
        runBlocking { assertTrue(db.nutritionDao().getAllLogs().isEmpty()) }

        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_save")
        vm.consumeUiMessage()
        vm.addLog(log)
        awaitState("el reintento confirmado abre el onboarding") { vm.showCreatineOverlay.value }
        vm.dismissCreatineOverlay()
        vm.addLog(log)
        awaitState("guardar otra vez el mismo id termina sin duplicarlo") { vm.showCreatineOverlay.value }

        assertEquals(listOf(log), repository.nutritionLogs.value)
        runBlocking { assertEquals(listOf(log), db.nutritionDao().getAllLogs().map { it.toNutritionLog() }) }
        assertNull(vm.uiMessage.value)
    }
}
