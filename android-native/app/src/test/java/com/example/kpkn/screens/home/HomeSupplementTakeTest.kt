package com.example.kpkn.screens.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.SupplementPilaItem
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El registro rápido de Inicio pasa por las MISMAS reglas que Nutrición: dosis, techo sobre el total conocido
 * (incluida la cafeína de los alimentos), confirmación explícita, persistencia antes de anunciar el éxito y
 * «Deshacer» que borra exactamente el registro creado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HomeSupplementTakeTest {

    @get:Rule
    val roomRule = RoomTestRule()

    private val date = "2026-10-29"
    private lateinit var programs: ProgramRepository
    private lateinit var repository: NutritionRepository
    private lateinit var db: KpknDatabase
    private lateinit var vm: HomeViewModel

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
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        programs = ProgramRepository.initForTests(context)
        awaitState("el repositorio de programas termina de cargar") { programs.isReady.value }
        repository = NutritionRepository.initForTests(context, noImport)
        runBlocking { repository.awaitStartupLoadForTests() }
        repository.stopCalendarForecastUpdates()
        db = repository.databaseForTests()
        runBlocking {
            programs.updateSettings { current ->
                current.copy(supplementPila = listOf(SupplementPilaItem(id = "cafeina", dose = 100.0, doses = 1, moment = 3)))
            }
        }
        vm = HomeViewModel()
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

    private suspend fun seedCaffeineFood() {
        repository.saveNutritionLog(
            NutritionLog(
                id = "coffee",
                date = date,
                mealType = MealType.SNACK,
                foods = listOf(LoggedFood(id = "food-coffee", foodName = "Café", amount = 1.0, caffeineMg = 350.0)),
            ),
        )
    }

    private suspend fun nextEvent() = withTimeout(10_000) { vm.supplementEvents.first() }

    @Test
    fun unaTomaQueSuperaElTechoConLaCafeinaDeLosAlimentosPideConfirmacionYNoGuarda() = runBlocking {
        seedCaffeineFood()

        vm.takeSupplementFromHome("cafeina", 100.0, date, "10:00")

        val event = nextEvent()
        assertTrue(event.message, event.isError)
        assertTrue(event.message.contains("máximo"))
        assertEquals("la confirmación no puede guardar de tapadillo", 1, repository.nutritionLogs.value.size)
    }

    @Test
    fun confirmarElTechoGuardaLaTomaYDeshacerBorraSoloEseRegistro() = runBlocking {
        seedCaffeineFood()

        vm.takeSupplementFromHome("cafeina", 100.0, date, "10:00", allowOverCeiling = true)

        val saved = nextEvent()
        assertFalse(saved.message, saved.isError)
        val logId = saved.logId
        assertNotNull("el éxito lleva el registro para deshacer", logId)
        awaitState("la toma se guardó de verdad") { repository.nutritionLogs.value.any { it.id == logId } }
        assertEquals(2, repository.nutritionLogs.value.size)

        vm.undoSupplementFromHome(logId!!)

        awaitState("deshacer quita la toma") { repository.nutritionLogs.value.none { it.id == logId } }
        assertEquals("el café del día sigue ahí", listOf("coffee"), repository.nutritionLogs.value.map { it.id })
    }

    @Test
    fun unFalloDePersistenciaNoAnunciaExitoYNoDejaLaToma() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_save BEFORE INSERT ON nutrition_logs BEGIN SELECT RAISE(ABORT, 'Fallo de escritura de prueba'); END",
        )

        vm.takeSupplementFromHome("cafeina", 100.0, date, "10:00", allowOverCeiling = true)

        val event = nextEvent()
        assertTrue("un fallo se informa", event.isError)
        assertFalse(event.message.contains("registrado"))
        assertTrue(repository.nutritionLogs.value.isEmpty())
    }
}
