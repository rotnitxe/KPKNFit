package com.example.kpkn.domain.nutrition.v2

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Mide `ConceptSearchV2.search` con el vocabulario real; se salta salvo con KPKN_BENCH=1 y vocabulario a mano. */
class ConceptSearchV2BenchmarkTest {

    private val queries = listOf(
        "p", "pa", "pal", "palt", "palta", "m", "ma", "mar", "marraq", "marraqueta", "huevos", "arroz", "arroz con pollo",
        "leche en polvo", "pan con palta", "cafe con leche", "aguacate", "avocado", "yogur", "manzana", "pollo", "atun en agua",
        "pastel de choclo", "completo", "sopaipilla", "queso", "pan", "te", "cerveza", "whey",
    )

    private fun vocabulary(): File? {
        System.getenv("KPKN_V2_VOCAB")?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.isFile }?.let { return it }
        }
        return File("C:/kw/nutri-build/final/concepts_export.json").takeIf { it.isFile }
    }

    private fun percentile(sortedMs: List<Double>, p: Double): Double =
        sortedMs[((sortedMs.size - 1) * p).let { Math.ceil(it).toInt() }.coerceIn(0, sortedMs.size - 1)]

    @Test
    fun searchIsFastWithTheRealVocabulary() {
        assumeTrue("Banco de búsqueda: solo con KPKN_BENCH=1", System.getenv("KPKN_BENCH") == "1")
        val file = vocabulary()
        assumeTrue("No hay vocabulario v2 (KPKN_V2_VOCAB o C:/kw/nutri-build/final/concepts_export.json)", file != null)
        file!!
        println("[bench] vocabulario: ${file.path}")

        val catalog = file.inputStream().buffered().use { ConceptCatalogLoaderV2.read(it, gzip = file.name.endsWith(".gz")) }

        val buildStart = System.nanoTime()
        val search = ConceptSearchV2(catalog)
        val buildMs = (System.nanoTime() - buildStart) / 1_000_000.0
        println("[bench] armado del índice: %.1f ms".format(buildMs))

        repeat(2) { for (q in queries) search.search(q) }

        val all = ArrayList<Double>()
        val perQuery = LinkedHashMap<String, List<Double>>()
        for (q in queries) {
            val times = (1..5).map {
                val start = System.nanoTime()
                search.search(q)
                (System.nanoTime() - start) / 1_000_000.0
            }.sorted()
            perQuery[q] = times
            all += times
            println("[bench] \"%s\": p50=%.2f p95=%.2f max=%.2f ms".format(q, percentile(times, 0.5), percentile(times, 0.95), times.last()))
        }
        val sortedAll = all.sorted()
        val p95 = percentile(sortedAll, 0.95)
        println("[bench] global: p50=%.2f p95=%.2f max=%.2f ms".format(percentile(sortedAll, 0.5), p95, sortedAll.last()))
        val slowest = perQuery.entries.sortedByDescending { it.value.last() }.take(3)
        println("[bench] más lentas: " + slowest.joinToString { "\"${it.key}\" %.2f ms".format(it.value.last()) })

        assertTrue("armado del índice ${buildMs} ms", buildMs < 5000.0)
        for ((q, times) in perQuery) assertTrue("p95 de \"$q\" = ${percentile(times, 0.95)} ms", percentile(times, 0.95) < 50.0)
        assertTrue("p95 global = $p95 ms", p95 < 50.0)
    }
}
