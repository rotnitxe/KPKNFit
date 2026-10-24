package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Banco de solo lectura. No importa el catálogo ni modifica índices o tablas. */
class FoodSearchBenchActivity : ComponentActivity() {
    companion object { internal var testRun: (suspend () -> String)? = null }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val output = TextView(this).apply { textSize = 17f; setPadding(24, 48, 24, 24); text = "Midiendo búsqueda local…" }
        setContentView(output)
        val warm = intent.getBooleanExtra("warm", true)
        val repeat = intent.getIntExtra("repeat", 1).coerceIn(1, 3)
        lifecycleScope.launch {
            try {
                val result = testRun?.invoke() ?: withContext(Dispatchers.IO) { runBench(warm, repeat) }
                output.text = result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { output.text = "No se pudo medir: ${error.message}"; Log.e("MfpSearchBench", "BENCH ERROR ${error.javaClass.simpleName}") }
        }
    }
    private suspend fun runBench(warm: Boolean, repeat: Int): String {
        withTimeout(120_000) { ProgramRepository.getInstance().isReady.first { it } }
        val repository = NutritionRepository.getInstance()
        withTimeout(120_000) { repository.foodDatabase.first { it.isNotEmpty() } }
        check(repository.catalogImportProgress.value.let { it == null || it >= 1f }) { "El catálogo se está importando. Repite al terminar." }
        val count = KpknDatabase.getInstance(applicationContext).nutritionDao().getGlobalFoodCount()
        check(count > 0) { "El catálogo global aún no está importado." }
        val queries = FoodSearchBenchQueries.all
        if (warm) queries.take(20).forEach { repository.searchFoodCandidates(it.text, limit = FoodSearchBenchQueries.TOP_K, loggerFilter = true) }
        fun memoryKb(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss
        val before = memoryKb()
        val samples = mutableListOf<Pair<String, Double>>()
        repeat(repeat) { queries.forEach { query ->
            currentCoroutineContext().ensureActive()
            val started = SystemClock.elapsedRealtimeNanos()
            val rows = repository.searchFoodCandidates(query.text, limit = FoodSearchBenchQueries.TOP_K, loggerFilter = true)
            val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
            check(rows.size <= FoodSearchBenchQueries.TOP_K)
            samples += query.text to elapsed
        } }
        val values = samples.map { it.second }
        fun format(value: Double) = String.format(Locale.ROOT, "%.2f", value)
        val summary = "BENCH p50=${format(FoodSearchBenchQueries.percentile(values, .50))} p95=${format(FoodSearchBenchQueries.percentile(values, .95))} p99=${format(FoodSearchBenchQueries.percentile(values, .99))} n=${values.size} catalog=$count topK=${FoodSearchBenchQueries.TOP_K} warm=$warm"
        val slow = samples.sortedByDescending { it.second }.take(5).joinToString("\n") { "${it.first}: ${format(it.second)} ms" }
        val memory = "PSS antes=$before KB después=${memoryKb()} KB"
        listOf(summary, slow, memory).forEach { Log.i("MfpSearchBench", it) }
        return "$summary\n\n$slow\n\n$memory\n\nTop-20: p95 caliente ≤120 ms conserva búsqueda. Si supera 120 ms, proponer FTS; no cambiarlo automáticamente."
    }
}
