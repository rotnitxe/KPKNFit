package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toNutritionLog
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.supplements.SupplementEngine
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionRepositoryRemoveFoodTest {

    @get:Rule
    val roomRule = RoomTestRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase
    private lateinit var repo: NutritionRepository

    private val date = "2026-10-29"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ProgramRepository.closeInstance()
        BodyProgressRepository.closeInstance()
        KpknDatabase.closeInstance()
        db = KpknDatabase.createInMemory(context)
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(
            Context::class.java,
            KpknDatabase::class.java,
            Boolean::class.javaPrimitiveType,
            FoodCatalogImporter::class.java,
        )
        ctor.isAccessible = true
        repo = ctor.newInstance(context, db, false, object : FoodCatalogImporter {
            override suspend fun importIfNeeded(
                db: KpknDatabase,
                context: Context,
                alreadyImported: Boolean,
                existingMeta: FoodImporter.ImportMetadata?,
                onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
            ): Boolean = false
        })
    }

    @After
    fun tearDown() = runBlocking {
        val repositoryJob = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
        (repositoryJob.get(repo) as Job).cancelAndJoin()
        db.close()
        BodyProgressRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    private fun sharedMeal() = NutritionLog(
        id = "shared",
        date = date,
        foods = listOf(
            LoggedFood(id = "sup:creatina", foodName = "Creatina", amount = 5.0, unit = "g", creatineG = 5.0),
            LoggedFood(id = "food", foodName = "Avena", amount = 80.0, calories = 300.0),
        ),
    )

    @Test
    fun quitaSoloElAlimentoIndicadoYConservaElResto() = runBlocking {
        repo.saveNutritionLog(sharedMeal())
        assertTrue(repo.removeFoodFromLogAndAwait("shared", "sup:creatina", 0, 5.0))

        val stored = db.nutritionDao().getLogsForDate(date).single().toNutritionLog()
        assertEquals(listOf("food"), stored.foods.map { it.id })
        assertEquals(300.0, stored.foods.single().calories, 1e-9)
    }

    @Test
    fun siEraElUnicoAlimentoSeEliminaLaComida() = runBlocking {
        val only = NutritionLog(
            id = "only",
            date = date,
            foods = listOf(LoggedFood(id = "sup:creatina", foodName = "Creatina", amount = 5.0, unit = "g", creatineG = 5.0)),
        )
        repo.saveNutritionLog(only)
        assertTrue(repo.removeFoodFromLogAndAwait("only", "sup:creatina", 0, 5.0))
        assertTrue(db.nutritionDao().getLogsForDate(date).none { it.id == "only" })
    }

    @Test
    fun siElIndiceCambioBuscaPorIdYCantidadSinBorrarOtroAlimento() = runBlocking {
        repo.saveNutritionLog(sharedMeal())
        // La comida se editó y la creatina ya no está en el índice 0.
        val edited = sharedMeal().copy(
            foods = listOf(
                LoggedFood(id = "food", foodName = "Avena", amount = 80.0, calories = 300.0),
                LoggedFood(id = "sup:creatina", foodName = "Creatina", amount = 5.0, unit = "g", creatineG = 5.0),
            ),
        )
        repo.updateNutritionLog(edited)
        assertTrue(repo.removeFoodFromLogAndAwait("shared", "sup:creatina", 0, 5.0))
        val stored = db.nutritionDao().getLogsForDate(date).single().toNutritionLog()
        assertEquals(listOf("food"), stored.foods.map { it.id })
    }

    @Test
    fun unAlimentoQueYaNoEstaNoSeBorraPorParecido() = runBlocking {
        repo.saveNutritionLog(sharedMeal())
        assertFalse(repo.removeFoodFromLogAndAwait("shared", "sup:creatina", 0, 10.0))
        val stored = db.nutritionDao().getLogsForDate(date).single().toNutritionLog()
        assertEquals(2, stored.foods.size)
    }

    @Test
    fun siLaDosisCambioNoSeBorraLaTomaQueElUsuarioVio() = runBlocking {
        repo.saveNutritionLog(sharedMeal())
        repo.updateNutritionLog(
            sharedMeal().copy(
                foods = listOf(
                    LoggedFood(id = "sup:creatina", foodName = "Creatina", amount = 4.0, unit = "g", creatineG = 4.0),
                    LoggedFood(id = "food", foodName = "Avena", amount = 80.0, calories = 300.0),
                ),
            ),
        )
        // La toma vista era de 5 g; la comida ya tiene 4 g en esa posición: no es la misma toma.
        assertFalse(repo.removeFoodFromLogAndAwait("shared", "sup:creatina", 0, 5.0))
        assertEquals(2, db.nutritionDao().getLogsForDate(date).single().toNutritionLog().foods.size)
    }

    @Test
    fun laCreatinaLegacySeQuitaPorSuIdAunqueElAmountGuardadoSeaUno() = runBlocking {
        repo.saveNutritionLog(
            NutritionLog(
                id = "legacy",
                date = date,
                foods = listOf(LoggedFood(id = "gen106", foodName = "Creatina", amount = 1.0, unit = "g", creatineG = 5.0)),
            ),
        )
        assertTrue(repo.removeFoodFromLogAndAwait("legacy", "gen106", 0, 5.0))
        assertTrue(db.nutritionDao().getLogsForDate(date).none { it.id == "legacy" })
    }

    @Test
    fun unaComidaPlanificadaNoCuentaComoToma() {
        val planned = sharedMeal().copy(id = "planned", status = NutritionStatus.PLANNED)
        assertTrue(SupplementEngine.takesFromLogs(listOf(planned), date).isEmpty())
    }
}
