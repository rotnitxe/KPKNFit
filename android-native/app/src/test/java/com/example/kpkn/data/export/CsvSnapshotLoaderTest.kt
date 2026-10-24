package com.example.kpkn.data.export

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class CsvSnapshotLoaderTest {
    @get:Rule val roomRule = RoomTestRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun unaComidaIlegibleNoImpideExportarLaSanaNiSeBorra() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val log = NutritionLog(id = "good", date = "2026-10-24T12:00:00-03:00", foods = listOf(LoggedFood(foodName = "Sana", amount = 100.0, calories = 100.0)))
            db.nutritionDao().upsertLog(log.toEntity())
            db.nutritionDao().upsertLog(log.copy(id = "bad").toEntity().copy(data = "{contenido privado"))
            val snapshot = CsvSnapshotLoader(db, Settings()).load(setOf(CsvExportDomain.MEALS))
            assertEquals(listOf(log), snapshot.meals); assertEquals(listOf(CsvRowIssue("nutrition_logs", "bad")), snapshot.issues)
            assertFalse(snapshot.issues.toString().contains("contenido privado"))
            assertEquals(2, db.nutritionDao().getAllLogs().size)
        } finally { db.close() }
    }
    @Test fun bibliotecaQuarantenaCadaTablaSinPerderElResto() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val food = FoodItem(id = "food", name = "Avena")
            val exercise = ExerciseMuscleInfo("exercise", "Press propio", isCustom = true)
            db.nutritionDao().upsertCustomFood(food.toEntity())
            db.nutritionDao().upsertCustomFood(food.copy(id = "bad-food").toEntity().copy(data = "{"))
            db.customExerciseDao().upsert(exercise.toEntity())
            db.customExerciseDao().upsert(exercise.copy(id = "bad-exercise").toEntity().copy(data = "{"))
            val snapshot = CsvSnapshotLoader(db, Settings()).load(setOf(CsvExportDomain.LIBRARY))
            assertEquals(listOf(food.id), snapshot.foods.map { it.id }); assertEquals(listOf(exercise.id), snapshot.exercises.map { it.id })
            assertEquals(2, snapshot.issues.size)
            assertEquals(2, db.nutritionDao().getAllCustomFoods().size); assertEquals(2, db.customExerciseDao().getAll().size)
        } finally { db.close() }
    }
}
