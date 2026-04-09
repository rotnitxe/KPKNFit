package com.example.kpkn.data.food

import android.content.Context
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * FoodImporter — Processes USDA CSV files from assets and populates the Room database.
 * 
 * Performance design:
 * - Reads files line-by-line to avoid OOM with 50MB+ files.
 * - Uses a temporary map to join food names with their nutrients.
 * - Batches database inserts to improve speed.
 */
object FoodImporter {
    private const val TAG = "FoodImporter"
    private const val BATCH_SIZE = 1000

    // Nutrient IDs from USDA standard
    private const val NUTRIENT_CALORIES = 1008
    private const val NUTRIENT_PROTEIN = 1003
    private const val NUTRIENT_FATS = 1004
    private const val NUTRIENT_CARBS = 1005
    private const val NUTRIENT_FIBER = 1079
    private const val NUTRIENT_SUGAR = 2000

    suspend fun importIfEmpty(context: Context) = withContext(Dispatchers.IO) {
        val dao = KpknDatabase.getInstance(context).nutritionDao()
        val count = dao.getGlobalFoodCount()
        
        if (count > 0) {
            android.util.Log.d(TAG, "Database already has $count foods. Skipping import.")
            return@withContext
        }

        android.util.Log.d(TAG, "Starting massive food import...")
        
        try {
            // 1. Load Nutrients (Memory-efficient map: FoodID -> Nutrients)
            val foodNutrients = mutableMapOf<Int, MutableMap<Int, Double>>()
            
            context.assets.open("food_data/food_nutrient.csv").bufferedReader().use { reader ->
                val header = reader.readLine() // Skip header
                reader.forEachLine { line ->
                    val parts = parseCsvLine(line)
                    if (parts.size >= 4) {
                        val fdcId = parts[1].toIntOrNull() ?: return@forEachLine
                        val nutrientId = parts[2].toIntOrNull() ?: return@forEachLine
                        val amount = parts[3].toDoubleOrNull() ?: 0.0
                        
                        if (isRelevantNutrient(nutrientId)) {
                            val map = foodNutrients.getOrPut(fdcId) { mutableMapOf() }
                            map[nutrientId] = amount
                        }
                    }
                }
            }
            android.util.Log.d(TAG, "Loaded nutrients for ${foodNutrients.size} foods")

            // 2. Load Foods and Join with Nutrients
            val batch = mutableListOf<GlobalFoodEntity>()
            context.assets.open("food_data/food.csv").bufferedReader().use { reader ->
                reader.readLine() // Skip header
                reader.forEachLine { line ->
                    val parts = parseCsvLine(line)
                    if (parts.size >= 3) {
                        val fdcId = parts[0].toIntOrNull() ?: return@forEachLine
                        val description = parts[2].trim('"')
                        
                        val nutrients = foodNutrients[fdcId] ?: emptyMap()
                        
                        batch.add(GlobalFoodEntity(
                            fdcId = fdcId,
                            name = description,
                            calories = nutrients[NUTRIENT_CALORIES] ?: 0.0,
                            protein = nutrients[NUTRIENT_PROTEIN] ?: 0.0,
                            carbs = nutrients[NUTRIENT_CARBS] ?: 0.0,
                            fats = nutrients[NUTRIENT_FATS] ?: 0.0,
                            fiber = nutrients[NUTRIENT_FIBER] ?: 0.0,
                            sugar = nutrients[NUTRIENT_SUGAR] ?: 0.0
                        ))

                        if (batch.size >= BATCH_SIZE) {
                            dao.insertGlobalFoods(batch)
                            batch.clear()
                        }
                    }
                }
            }
            if (batch.isNotEmpty()) {
                dao.insertGlobalFoods(batch)
            }
            
            android.util.Log.d(TAG, "Import finished successfully!")
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error importing food data", e)
        }
    }

    private fun isRelevantNutrient(id: Int) = id in listOf(
        NUTRIENT_CALORIES, NUTRIENT_PROTEIN, NUTRIENT_FATS, 
        NUTRIENT_CARBS, NUTRIENT_FIBER, NUTRIENT_SUGAR
    )

    /**
     * Simple CSV parser that handles quotes.
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        for (char in line) {
            when {
                char == '\"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }
}
