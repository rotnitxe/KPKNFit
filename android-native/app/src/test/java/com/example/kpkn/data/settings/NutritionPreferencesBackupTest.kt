package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.dbJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionPreferencesBackupTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun `utensilios aprendizaje y favoritos hacen ida y vuelta JSON sin tocar metadatos locales`() = runBlocking {
        val utensils = context.getSharedPreferences("nutrition_utensils", Context.MODE_PRIVATE)
        val foods = context.getSharedPreferences("nutrition_food_catalog", Context.MODE_PRIVATE)
        utensils.edit().clear().putFloat("ml_taza", 240f).putFloat("ml_cucharada", 15f).commit()
        val learning = "[{\"query\":\"avena\",\"foodId\":\"food\",\"updatedAt\":\"2026-10-24T12:00:00Z\"}]"
        foods.edit().clear().putString("food_query_learning_v2", learning)
            .putStringSet("favorite_food_ids", setOf("food", "recipe", "template:t1"))
            .putString("food_catalog_meta", "solo-local").commit()
        val saved = NutritionPreferencesBackup.read(context)
        val decoded = dbJson.decodeFromString<NutritionPreferencesBackup>(dbJson.encodeToString(saved))
        utensils.edit().putFloat("ml_taza", 400f).putFloat("ml_vaso", 300f).commit()
        foods.edit().remove("food_query_learning_v2").putStringSet("favorite_food_ids", emptySet()).commit()
        decoded.restore(context)
        assertEquals(saved, NutritionPreferencesBackup.read(context))
        assertEquals(240f, utensils.getFloat("ml_taza", 0f))
        assertFalse(utensils.contains("ml_vaso"))
        assertEquals(learning, foods.getString("food_query_learning_v2", null))
        assertEquals("solo-local", foods.getString("food_catalog_meta", null))
    }
    @Test fun `una copia de preferencias invalida no cambia las preferencias locales`() = runBlocking {
        val utensils = context.getSharedPreferences("nutrition_utensils", Context.MODE_PRIVATE)
        utensils.edit().clear().putFloat("ml_taza", 240f).commit()
        val before = NutritionPreferencesBackup.read(context)
        assertTrue(runCatching { NutritionPreferencesBackup(mapOf("ml_taza" to -10f)).restore(context) }.isFailure)
        assertEquals(before, NutritionPreferencesBackup.read(context))
    }
}
