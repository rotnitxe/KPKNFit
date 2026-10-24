package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionRecipeRepositoryTest {
    @get:Rule val roomRule = RoomTestRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun repository(db: KpknDatabase): NutritionRepository {
        val constructor = NutritionRepository::class.java.getDeclaredConstructor(Context::class.java,
            KpknDatabase::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        return constructor.newInstance(context, db, false)
    }
    private suspend fun close(repo: NutritionRepository, db: KpknDatabase) {
        val job = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
        (job.get(repo) as Job).cancelAndJoin(); db.close()
    }
    private fun recipe() = RecipeCalculator.asFood("recipe", "Arroz con pollo", RecipeData(
        ingredients = listOf(RecipeIngredient(LoggedFood(foodName = "Ingrediente sin catálogo", amount = 800.0, calories = 800.0))),
        servings = 4, finalWeightGrams = 600.0,
    ))

    @Test fun `guardar editar buscar y borrar la receta no cambia comidas registradas`() = runBlocking {
        val db = KpknDatabase.createInMemory(context); val repo = repository(db)
        try {
            repo.saveCustomFood(recipe())
            val stored = repo.getCustomFoodsForBackup().single()
            assertEquals(1, stored.recipe!!.version)
            assertTrue(stored.recipe.updatedAt.isNotBlank())
            assertEquals("recipe", repo.searchFood("arroz con pollo").first().id)
            val log = NutritionLog(id = "history", date = "2026-10-24T12:00:00.000Z",
                foods = listOf(RecipeCalculator.portion(stored, servings = 1.0, id = "portion")))
            repo.saveNutritionLog(log)
            repo.saveCustomFood(stored.copy(recipe = stored.recipe.copy(servings = 2)))
            val edited = repo.getCustomFoodsForBackup().single()
            assertEquals(2, edited.recipe!!.version)
            assertEquals(400.0, edited.calories, 0.0)
            assertEquals(log, db.nutritionDao().getAllLogs().single().toNutritionLog())
            repo.deleteCustomFood(edited.id)
            assertTrue(repo.getCustomFoodsForBackup().isEmpty())
            assertEquals(log, db.nutritionDao().getAllLogs().single().toNutritionLog())
        } finally { close(repo, db) }
    }

    @Test fun `un fallo Room al editar no publica ni avanza la version guardada`() = runBlocking {
        val db = KpknDatabase.createInMemory(context); val repo = repository(db)
        try {
            repo.saveCustomFood(recipe())
            val before = repo.foodDatabase.value.single()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_recipe BEFORE INSERT ON nutrition_custom_foods BEGIN SELECT RAISE(ABORT, 'fallo de prueba'); END")
            assertTrue(runCatching { repo.saveCustomFood(before.copy(recipe = before.recipe!!.copy(servings = 2))) }.isFailure)
            assertEquals(before, repo.foodDatabase.value.single())
            assertEquals(1, repo.getCustomFoodsForBackup().single().recipe!!.version)
        } finally { close(repo, db) }
    }

    @Test fun `dos guardados concurrentes serializan las versiones de la misma receta`() = runBlocking {
        val db = KpknDatabase.createInMemory(context); val repo = repository(db)
        try {
            val food = recipe()
            coroutineScope { listOf(async { repo.saveCustomFood(food) }, async { repo.saveCustomFood(food) }).awaitAll() }
            assertEquals(1, repo.foodDatabase.value.size)
            assertEquals(2, repo.getCustomFoodsForBackup().single().recipe!!.version)
            assertTrue(db.nutritionDao().getAllLogs().isEmpty())
        } finally { close(repo, db) }
    }
}
