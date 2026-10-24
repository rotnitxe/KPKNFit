package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.MealTemplate
import com.example.kpkn.data.models.PantryItem
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionBackupRowsTest {
    @get:Rule val roomRule = RoomTestRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun repository(db: KpknDatabase): NutritionRepository {
        val constructor = NutritionRepository::class.java.getDeclaredConstructor(
            Context::class.java, KpknDatabase::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return constructor.newInstance(context, db, false)
    }

    private suspend fun close(repo: NutritionRepository, db: KpknDatabase) {
        val job = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
        (job.get(repo) as Job).cancelAndJoin()
        db.close()
    }

    @Test
    fun `una fila ilegible por tabla no impide exportar las sanas y ninguna se borra`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        val repo = repository(db)
        try {
            val dao = db.nutritionDao()
            val template = MealTemplate(id = "template-ok", name = "Almuerzo, con acentos y ñ")
            val pantry = PantryItem(id = "pantry-ok", name = "Arroz", currentQuantity = 150.5)
            val food = FoodItem(id = "food-ok", name = "Avena", calories = 370.0)
            dao.upsertTemplate(template.toEntity())
            dao.upsertTemplate(template.copy(id = "template-bad").toEntity().copy(data = "{contenido privado ilegible"))
            dao.upsertPantryItem(pantry.toEntity())
            dao.upsertPantryItem(pantry.copy(id = "pantry-bad").toEntity().copy(data = "{"))
            dao.upsertCustomFood(food.toEntity())
            dao.upsertCustomFood(food.copy(id = "food-bad").toEntity().copy(data = "{"))

            assertEquals(listOf(template), repo.getMealTemplatesForBackup())
            assertEquals(listOf(pantry), repo.getPantryItemsForBackup())
            assertEquals(listOf(food.id), repo.getCustomFoodsForBackup().map { it.id })
            assertEquals(setOf(
                UnreadableNutritionRow("template-bad", "nutrition_templates"),
                UnreadableNutritionRow("pantry-bad", "nutrition_pantry"),
                UnreadableNutritionRow("food-bad", "nutrition_custom_foods"),
            ), repo.unreadableRows.value.toSet())
            val json = dbJson.encodeToString(repo.getMealTemplatesForBackup())
            assertTrue(json.contains(template.name))
            assertFalse(json.contains("contenido privado"))
            assertEquals(2, dao.getAllTemplates().size)
            assertEquals(2, dao.getAllPantryItems().size)
            assertEquals(2, dao.getAllCustomFoods().size)
            assertEquals("{contenido privado ilegible", dao.getAllTemplates().single { it.id == "template-bad" }.data)
        } finally { close(repo, db) }
    }

    @Test
    fun `reparar una fila recupera su exportacion sin perder la cuarentena de otra tabla`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        val repo = repository(db)
        try {
            val dao = db.nutritionDao()
            val template = MealTemplate(id = "template", name = "Desayuno")
            val pantry = PantryItem(id = "pantry", name = "Leche")
            dao.upsertTemplate(template.toEntity().copy(data = "{"))
            dao.upsertPantryItem(pantry.toEntity().copy(data = "{"))
            assertTrue(repo.getMealTemplatesForBackup().isEmpty())
            assertTrue(repo.getPantryItemsForBackup().isEmpty())
            dao.upsertTemplate(template.toEntity())
            assertEquals(listOf(template), repo.getMealTemplatesForBackup())
            assertEquals(listOf(UnreadableNutritionRow("pantry", "nutrition_pantry")), repo.unreadableRows.value)
        } finally { close(repo, db) }
    }
}
