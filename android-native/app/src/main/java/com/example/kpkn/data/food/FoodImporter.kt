package com.example.kpkn.data.food

import android.content.Context
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * FoodImporter — Soporta USDA y OpenFoodFacts Chile.
 */
object FoodImporter {
    private const val TAG = "FoodImporter"
    private const val BATCH_SIZE = 2000

    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress: StateFlow<Float?> = _importProgress.asStateFlow()

    suspend fun importIfEmpty(alreadyImported: Boolean, context: Context) = withContext(Dispatchers.IO) {
        if (alreadyImported) return@withContext

        _importProgress.value = 0.01f
        val dao = KpknDatabase.getInstance(context).nutritionDao()
        
        try {
            // --- FASE 1: USDA (Básicos) ---
            android.util.Log.d(TAG, "Importando USDA...")
            // Compact storage: FloatArray[6] = [kcal, protein, fat, carbs, fiber, sugar]
            // Avoids boxing overhead of Map<Int, Map<Int, Double>> which can cost 50-100 MB RAM
            val nutIdxMap = mapOf(1008 to 0, 1003 to 1, 1004 to 2, 1005 to 3, 1079 to 4, 2000 to 5)
            val foodNutrients = HashMap<Int, FloatArray>(60_000)
            context.assets.open("food_data/food_nutrient.csv").bufferedReader().use { r ->
                r.readLine() // Header
                for (line in r.lineSequence()) {
                    val p = parseCsvLine(line)
                    if (p.size >= 4) {
                        val fdcId = p[1].toIntOrNull() ?: continue
                        val nutId = p[2].toIntOrNull() ?: continue
                        val idx = nutIdxMap[nutId] ?: continue
                        val amt = p[3].toFloatOrNull() ?: 0f
                        foodNutrients.getOrPut(fdcId) { FloatArray(6) }[idx] = amt
                    }
                }
            }
            _importProgress.value = 0.15f

            val usdaBatch = mutableListOf<GlobalFoodEntity>()
            context.assets.open("food_data/food.csv").bufferedReader().use { r ->
                r.readLine()
                for (line in r.lineSequence()) {
                    val p = parseCsvLine(line)
                    if (p.size >= 3) {
                        val fdcId = p[0].toIntOrNull() ?: continue
                        val name = p[2].trim('"')
                        val nuts = foodNutrients[fdcId]
                        usdaBatch.add(GlobalFoodEntity(
                            foodId = "usda_$fdcId",
                            name = name,
                            calories = nuts?.get(0)?.toDouble() ?: 0.0,
                            protein = nuts?.get(1)?.toDouble() ?: 0.0,
                            fats = nuts?.get(2)?.toDouble() ?: 0.0,
                            carbs = nuts?.get(3)?.toDouble() ?: 0.0,
                            fiber = nuts?.get(4)?.toDouble() ?: 0.0,
                            sugar = nuts?.get(5)?.toDouble() ?: 0.0,
                            source = "USDA"
                        ))
                        if (usdaBatch.size >= BATCH_SIZE) {
                            dao.insertGlobalFoods(usdaBatch)
                            usdaBatch.clear()
                        }
                    }
                }
            }
            foodNutrients.clear() // liberar RAM antes de la fase OFF Chile
            if (usdaBatch.isNotEmpty()) dao.insertGlobalFoods(usdaBatch)
            _importProgress.value = 0.4f

            // --- FASE 2: OpenFoodFacts Chile ---
            android.util.Log.d(TAG, "Importando OpenFoodFacts Chile...")
            runCatching {
                val offBatch = mutableListOf<GlobalFoodEntity>()
                context.assets.open("food_data/off_chile.csv").bufferedReader().use { r ->
                    val header = r.readLine()?.split(",") ?: emptyList()
                    val idxCode = header.indexOf("code").takeIf { it >= 0 } ?: 0
                    val idxName = header.indexOf("product_name").takeIf { it >= 0 } ?: 7
                    val idxBrand = header.indexOf("brands").takeIf { it >= 0 } ?: 12
                    val idxKcal = header.indexOf("energy-kcal_100g").takeIf { it >= 0 } ?: 64
                    val idxProt = header.indexOf("proteins_100g").takeIf { it >= 0 } ?: 116
                    val idxFat = header.indexOf("fat_100g").takeIf { it >= 0 } ?: 66
                    val idxCarb = header.indexOf("carbohydrates_100g").takeIf { it >= 0 } ?: 102

                    for (line in r.lineSequence()) {
                        val p = parseCsvLine(line)
                        if (p.size > idxKcal) {
                            offBatch.add(GlobalFoodEntity(
                                foodId = "off_${p.getOrNull(idxCode) ?: ""}",
                                name = p.getOrNull(idxName)?.trim('"') ?: "Producto OFF",
                                brand = p.getOrNull(idxBrand)?.trim('"'),
                                calories = p.getOrNull(idxKcal)?.toDoubleOrNull() ?: 0.0,
                                protein = p.getOrNull(idxProt)?.toDoubleOrNull() ?: 0.0,
                                fats = p.getOrNull(idxFat)?.toDoubleOrNull() ?: 0.0,
                                carbs = p.getOrNull(idxCarb)?.toDoubleOrNull() ?: 0.0,
                                source = "OFF Chile"
                            ))
                            if (offBatch.size >= BATCH_SIZE) {
                                dao.insertGlobalFoods(offBatch)
                                offBatch.clear()
                                _importProgress.value = 0.4f + (0.55f * (System.currentTimeMillis() % 1000 / 1000f)) // Animación simulada
                            }
                        }
                    }
                }
                if (offBatch.isNotEmpty()) dao.insertGlobalFoods(offBatch)
            }

            _importProgress.value = 1.0f
            delay(1000)
            _importProgress.value = null
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Error importando (${t.javaClass.simpleName})", t)
            _importProgress.value = null
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val res = mutableListOf<String>()
        var cur = StringBuilder()
        var q = false
        for (c in line) {
            if (c == '\"') q = !q
            else if (c == ',' && !q) { res.add(cur.toString()); cur = StringBuilder() }
            else cur.append(c)
        }
        res.add(cur.toString())
        return res
    }
}
