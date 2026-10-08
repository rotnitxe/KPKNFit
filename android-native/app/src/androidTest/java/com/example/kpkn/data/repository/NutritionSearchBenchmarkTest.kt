package com.example.kpkn.data.repository

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android search, imported catalog and FTS; fixtures contain no personal meal text. */
@RunWith(AndroidJUnit4::class)
class NutritionSearchBenchmarkTest {
    @Test fun searchBudget() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.kpkn.stability", context.packageName)
        val repository = withContext(Dispatchers.IO) { NutritionRepository.init(context) }
        run {
            withTimeout(120_000) { repository.awaitStartupLoadForTests() }
            withContext(Dispatchers.IO) {
                val sql = repository.databaseForTests().openHelper.readableDatabase
                for (table in listOf("global_foods", "global_foods_fts")) {
                    sql.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertTrue("The real imported catalog and FTS must be populated", cursor.getLong(0) > 0)
                    }
                }
            }
            val queries = listOf("manzana", "arroz cocido", "pollo", "pan integral", "leche", "yogur",
                "porotos", "avena", "huevo", "lentejas", "papa", "plátano")
            repeat(3) { queries.forEach { assertTrue(repository.searchFood(it).isNotEmpty()) } }
            val timings = mutableListOf<Double>()
            repeat(10) {
                for (query in queries) {
                    val start = SystemClock.elapsedRealtimeNanos()
                    val results = repository.searchFood(query)
                    timings += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                    assertTrue("Search must return catalog candidates", results.isNotEmpty())
                    assertEquals(results.size, results.map { it.id }.distinct().size)
                }
            }
            val sorted = timings.sorted()
            val p95 = sorted[kotlin.math.ceil(sorted.size * 0.95).toInt() - 1]
            println("KPKN_SEARCH_ANDROID samples=${timings.size} medianMs=${sorted[sorted.size / 2]} p95Ms=$p95 maxMs=${sorted.last()} budgetMs=150.0")
            val evidence = org.json.JSONObject().put("samples", timings.size).put("medianMs", sorted[sorted.size / 2])
                .put("p95Ms", p95).put("maxMs", sorted.last()).put("budgetMs", 150.0).put("storage", "PROFILE_ROOM_FILE")
            InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                putString("kpkn_search_result", evidence.toString())
            })
            assertTrue("Android warm search p95 must be below 150 ms; measured $p95", p95 < 150.0)
        }
    }
}
