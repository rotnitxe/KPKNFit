package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.settings.NutritionPreferencesBackup
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.After
import com.example.kpkn.domain.nutrition.SubjectivePortionEngine
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionFavoriteRepositoryTest {
    @get:Rule val roomRule = RoomTestRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var engineBefore: Map<String, Double> = emptyMap()
    @Before fun rememberEngine() { engineBefore = SubjectivePortionEngine.currentUtensilOverrides() }
    @After fun restoreEngine() { SubjectivePortionEngine.applyUtensilOverrides(engineBefore) }
    private fun repository(db: KpknDatabase): NutritionRepository {
        val constructor = NutritionRepository::class.java.getDeclaredConstructor(Context::class.java,
            KpknDatabase::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        return constructor.newInstance(context, db, false)
    }
    private suspend fun close(repos: List<NutritionRepository>, db: KpknDatabase) {
        val job = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
        repos.forEach { (job.get(it) as Job).cancelAndJoin() }; db.close()
    }
    @Test fun `marcar y desmarcar persisten al recrear el repositorio`() = runBlocking {
        context.getSharedPreferences("nutrition_food_catalog", Context.MODE_PRIVATE).edit().clear().commit()
        val db = KpknDatabase.createInMemory(context)
        val first = repository(db)
        var second: NutritionRepository? = null
        try {
            first.toggleFavoriteFood("food")
            first.toggleFavoriteFood("template:breakfast")
            val recreated = repository(db)
            second = recreated
            assertEquals(setOf("food", "template:breakfast"), recreated.favoriteFoodIds.value)
            recreated.toggleFavoriteFood("food")
            assertEquals(setOf("template:breakfast"), NutritionPreferencesBackup.read(context).favoriteFoodIds)
            assertTrue(db.nutritionDao().getAllLogs().isEmpty())
        } finally { close(listOfNotNull(first, second), db) }
    }
    @Test fun `restaurar favoritos actualiza el flujo sin modificar el catalogo`() = runBlocking {
        val db = KpknDatabase.createInMemory(context); val repo = repository(db)
        try {
            val before = repo.foodDatabase.value
            NutritionPreferencesBackup(utensilVolumesMl = mapOf("ml_taza" to 300f), favoriteFoodIds = setOf("recipe")).restore(context)
            repo.refreshFoodPreferencesFromStorage()
            assertEquals(setOf("recipe"), repo.favoriteFoodIds.value)
            assertEquals(300.0, SubjectivePortionEngine.currentUtensilOverrides()["taza"])
            assertEquals(before, repo.foodDatabase.value)
        } finally { close(listOf(repo), db) }
    }
}
