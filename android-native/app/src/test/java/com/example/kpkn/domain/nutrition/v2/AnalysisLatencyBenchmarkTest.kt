package com.example.kpkn.domain.nutrition.v2

import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.ContextDetector
import com.example.kpkn.domain.nutrition.DatasetContextProfile
import com.example.kpkn.domain.nutrition.DatasetDocument
import com.example.kpkn.domain.nutrition.DatasetKnowledgeSnapshot
import com.example.kpkn.domain.nutrition.DatasetMacroBasis
import com.example.kpkn.domain.nutrition.DatasetMacros
import com.example.kpkn.domain.nutrition.DatasetPortion
import com.example.kpkn.domain.nutrition.DatasetPortionPrior
import com.example.kpkn.domain.nutrition.DatasetPosting
import com.example.kpkn.domain.nutrition.DatasetTokenEntry
import com.example.kpkn.domain.nutrition.SemanticPortionRetriever
import com.example.kpkn.domain.nutrition.parseMealDescription
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

/**
 * Medicion en caliente de las etapas del analisis de una descripcion (FoodLoggerDrawer.analyzeDescription / resolveTags).
 * Solo mide e imprime; el unico assert es que corrieron al menos 100 textos. Usa SOLO casos de DEV.
 * Banco de analisis: solo corre con la variable de entorno KPKN_BENCH=1; sin ella se omite (Assume) para no gastar
 * tiempo en las tandas completas de otras sesiones.
 * Etapas: retrieve, detect, parse (interprete anterior); v2parse, adapter (v2). total = suma.
 * Nota: el drawer llama a ContextDetector.detect DOS veces (antes y despues de retrieve); aqui se mide una y se informa
 * tambien el total con detect x2.
 */
@Serializable
private data class ExamCase(val id: String, val text: String, val mealType: String = "LUNCH")

class AnalysisLatencyBenchmarkTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Copia del decodificador de DatasetKnowledgeStore (excluido del carril JVM por usar android.content.Context).
    private fun DataInputStream.sized(): String {
        val n = readInt()
        val b = ByteArray(n)
        readFully(b)
        return b.toString(Charsets.UTF_8)
    }

    private fun decode(input: DataInputStream): DatasetKnowledgeSnapshot {
        val magic = ByteArray(8).also { input.readFully(it) }.toString(Charsets.US_ASCII)
        require(magic == "KPKNDS02")
        val formatVersion = input.readInt()
        val checksum = input.sized()
        val docCount = input.readInt()
        val documents = ArrayList<DatasetDocument>(docCount)
        repeat(docCount) { expectedId ->
            val id = input.readInt()
            require(id == expectedId)
            val instruction = input.sized()
            val type = input.sized()
            val contexts = buildSet { repeat(input.readUnsignedByte()) { add(input.sized()) } }
            val cookingTerms = buildSet { repeat(input.readUnsignedByte()) { add(input.sized()) } }
            val macroBasis = DatasetMacroBasis.fromCode(input.readUnsignedByte())
            val basisGrams = input.readDouble()
            val macros = if (input.readUnsignedByte() == 1) {
                DatasetMacros(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble())
            } else null
            val portions = buildList {
                repeat(input.readUnsignedShort()) { add(DatasetPortion(input.sized(), input.readDouble())) }
            }
            documents += DatasetDocument(
                id = id, instruction = instruction, type = type, contexts = contexts, cookingTerms = cookingTerms,
                macroBasis = macroBasis, basisGrams = basisGrams, macros = macros, portions = portions,
                vectorNorm = input.readFloat().toDouble(), trigramCount = input.readUnsignedShort(),
            )
        }
        val tokenCount = input.readInt()
        val tokenIndex = HashMap<String, DatasetTokenEntry>()
        repeat(tokenCount) {
            val token = input.sized()
            val idf = input.readFloat().toDouble()
            val postings = buildList {
                repeat(input.readUnsignedShort()) { add(DatasetPosting(input.readInt(), input.readFloat().toDouble())) }
            }
            tokenIndex[token] = DatasetTokenEntry(idf, postings)
        }
        val trigramCount = input.readInt()
        val trigramIndex = HashMap<String, IntArray>()
        repeat(trigramCount) {
            val tri = input.sized()
            trigramIndex[tri] = IntArray(input.readUnsignedShort()) { input.readInt() }
        }
        val priorCount = input.readInt()
        val priors = HashMap<String, DatasetPortionPrior>()
        repeat(priorCount) {
            val food = input.sized()
            priors[food] = DatasetPortionPrior(food, input.readDouble(), input.readInt())
        }
        val ctxCount = input.readInt()
        val ctx = HashMap<String, DatasetContextProfile>()
        repeat(ctxCount) {
            val c = input.sized()
            ctx[c] = DatasetContextProfile(
                c, input.readInt(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(),
            )
        }
        return DatasetKnowledgeSnapshot(formatVersion, checksum, documents, tokenIndex, trigramIndex, priors, ctx)
    }

    private class Sample(val id: String, val text: String, val stages: Map<String, Double>)

    private fun pct(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val idx = kotlin.math.ceil(p * sorted.size).toInt().coerceIn(1, sorted.size) - 1
        return sorted[idx]
    }

    @Test
    fun `measure analysis stages hot`() {
        Assume.assumeTrue("Banco de analisis: solo con KPKN_BENCH=1", System.getenv("KPKN_BENCH") == "1")
        val exportFile = File(System.getProperty("kpkn.v2.export") ?: System.getenv("KPKN_V2_EXPORT") ?: "C:/kw/nutri-build/final/concepts_export.json")
        val devFile = File("C:/kw/nutri-examen/cases/dev.jsonl")
        Assume.assumeTrue("falta ${exportFile.path}", exportFile.exists())
        Assume.assumeTrue("falta ${devFile.path}", devFile.exists())

        val catalog = exportFile.inputStream().use { ConceptCatalogLoaderV2.read(it) }
        val parser = NutritionParserV2(catalog)
        val cases = devFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { json.decodeFromString<ExamCase>(it) }

        // Dataset de porciones: el real, para que retrieve haga trabajo real (como en el telefono).
        val asset = File("src/main/assets/food_data/dataset_knowledge.bin")
        var retrieveReal = false
        if (asset.exists()) {
            runCatching {
                DataInputStream(GZIPInputStream(FileInputStream(asset).buffered())).use { SemanticPortionRetriever.install(decode(it)) }
                retrieveReal = SemanticPortionRetriever.status().ready
            }.onFailure { println("BENCH retrieve: no se pudo instalar el dataset real: $it") }
        }
        println("BENCH retrieve con dataset real instalado: $retrieveReal")
        println("BENCH textos DEV: ${cases.size}; generacion catalogo: ${catalog.generation}")

        fun mealOf(s: String): MealType? = runCatching { MealType.valueOf(s) }.getOrNull()

        fun once(case: ExamCase): Map<String, Double> {
            val meal = mealOf(case.mealType)
            var t = System.nanoTime()
            val retrieval = SemanticPortionRetriever.retrieve(case.text)
            val tRetrieve = (System.nanoTime() - t) / 1e6
            t = System.nanoTime()
            ContextDetector.detect(case.text, meal)
            val tDetect = (System.nanoTime() - t) / 1e6
            t = System.nanoTime()
            val parsed = parseMealDescription(case.text, retrieval)
            val tParse = (System.nanoTime() - t) / 1e6
            val v2Text = parsed.verbatimDescription.ifBlank { parsed.rawDescription }
            t = System.nanoTime()
            val v2 = parser.parse(v2Text)
            val tV2 = (System.nanoTime() - t) / 1e6
            t = System.nanoTime()
            val tags = ResolvedTagAdapterV2.toResolvedTags(v2, parser.catalog)
            val tAdapter = (System.nanoTime() - t) / 1e6
            if (tags.size < 0) println("imposible")
            return linkedMapOf(
                "retrieve" to tRetrieve, "detect" to tDetect, "parse(anterior)" to tParse,
                "v2parse" to tV2, "adapter" to tAdapter,
                "total" to (tRetrieve + tDetect + tParse + tV2 + tAdapter),
                "anterior(retr+det+parse)" to (tRetrieve + tDetect + tParse),
                "v2(parse+adapter)" to (tV2 + tAdapter),
            )
        }

        // 2 pasadas de calentamiento sin medir.
        repeat(2) { cases.forEach { runCatching { once(it) } } }

        val measured = HashMap<String, MutableList<Map<String, Double>>>()
        val failures = ArrayList<String>()
        repeat(3) {
            cases.forEach { c ->
                runCatching { once(c) }.onSuccess { measured.getOrPut(c.id) { ArrayList() }.add(it) }
                    .onFailure { failures += "${c.id}: $it" }
            }
        }
        failures.take(5).forEach { println("BENCH fallo $it") }
        println("BENCH fallos: ${failures.size}")

        val samples = cases.filter { measured[it.id]?.size == 3 }.map { c ->
            val runs = measured.getValue(c.id)
            val keys = runs.first().keys
            Sample(c.id, c.text, keys.associateWith { k -> runs.map { it.getValue(k) }.sorted()[1] })
        }
        val allRuns = measured.values.flatten()

        println("BENCH ===== TABLA (ms; sobre ${allRuns.size} mediciones = ${samples.size} textos x 3 pasadas) =====")
        println("BENCH %-26s %8s %8s %8s".format("etapa", "p50", "p95", "max"))
        val order = listOf("retrieve", "detect", "parse(anterior)", "v2parse", "adapter", "total", "anterior(retr+det+parse)", "v2(parse+adapter)")
        for (k in order) {
            val v = allRuns.map { it.getValue(k) }.sorted()
            println("BENCH %-26s %8.3f %8.3f %8.3f".format(k, pct(v, 0.5), pct(v, 0.95), v.last()))
        }
        val dd = allRuns.map { it.getValue("total") + it.getValue("detect") }.sorted()
        println("BENCH %-26s %8.3f %8.3f %8.3f".format("total con detect x2 (drawer)", pct(dd, 0.5), pct(dd, 0.95), dd.last()))
        println("BENCH medias: " + order.joinToString(" ") { k -> "$k=%.3f".format(allRuns.map { it.getValue(k) }.average()) })

        println("BENCH ===== 10 MAS LENTOS (mediana de 3 pasadas, por total) =====")
        samples.sortedByDescending { it.stages.getValue("total") }.take(10).forEach { s ->
            println(
                "BENCH %s total=%.2f | retrieve=%.2f detect=%.2f parse=%.2f v2parse=%.2f adapter=%.2f | %s".format(
                    s.id, s.stages.getValue("total"), s.stages.getValue("retrieve"), s.stages.getValue("detect"),
                    s.stages.getValue("parse(anterior)"), s.stages.getValue("v2parse"), s.stages.getValue("adapter"),
                    s.text.take(120),
                ),
            )
        }
        assertTrue("debia medir al menos 100 textos, midio ${samples.size}", samples.size >= 100)
    }
}
