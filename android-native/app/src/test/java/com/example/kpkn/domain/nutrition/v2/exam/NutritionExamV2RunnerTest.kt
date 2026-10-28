package com.example.kpkn.domain.nutrition.v2.exam

import com.example.kpkn.domain.nutrition.exam.ExamCase
import com.example.kpkn.domain.nutrition.exam.ExamMacros
import com.example.kpkn.domain.nutrition.exam.ExamMention
import com.example.kpkn.domain.nutrition.exam.ExamPrediction
import com.example.kpkn.domain.nutrition.v2.ConceptCatalogLoaderV2
import com.example.kpkn.domain.nutrition.v2.ConceptCatalogV2
import com.example.kpkn.domain.nutrition.v2.MentionV2
import com.example.kpkn.domain.nutrition.v2.NutritionParserV2
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Corredor de predicciones del examen de nutricion (docs/nutrition-v2/EXAMEN.md, secciones 2 y 6) para el parser v2.
 *
 * Lee casos JSONL y `concepts_export.json`, corre [NutritionParserV2] y escribe un JSONL de predicciones. Ajustes
 * (propiedad de sistema, luego variable de entorno, luego valor por defecto):
 *  - `kpkn.exam.cases` / `KPKN_EXAM_CASES`   casos; sin valor solo corre el test del fixture (`exam_fixture_cases.jsonl`)
 *  - `kpkn.v2.export` / `KPKN_V2_EXPORT`     (por defecto `C:/kw/nutri-build/concepts_export.json`)
 *  - `kpkn.exam.out` / `KPKN_EXAM_OUT`       (por defecto `C:/kw/nutri-examen/predictions`)
 *  - `kpkn.exam.system` / `KPKN_EXAM_SYSTEM` (por defecto `v2@<generation>+<sha256[0..8] del export>`)
 * Si existe `C:/kw/nutri-examen/v2-runner.properties` (o la ruta de `kpkn.v2.props`), sus claves (los mismos nombres de
 * propiedad) rellenan lo que no vino por propiedad de sistema ni por entorno.
 * El archivo de salida es `<sistema>-<yyyyMMdd-HHmm>.jsonl`.
 */
class NutritionExamV2RunnerTest {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    private val fileProps: java.util.Properties by lazy {
        java.util.Properties().also { p ->
            val f = File(System.getProperty("kpkn.v2.props") ?: System.getenv("KPKN_V2_PROPS") ?: "C:/kw/nutri-examen/v2-runner.properties")
            if (f.exists()) f.reader(Charsets.UTF_8).use { p.load(it) }
        }
    }

    private fun setting(property: String, env: String, default: String): String =
        System.getProperty(property) ?: System.getenv(env) ?: fileProps.getProperty(property) ?: default

    private fun parseCases(lines: List<String>): List<ExamCase> =
        lines.filter { it.isNotBlank() }.map { json.decodeFromString<ExamCase>(it) }

    private fun readPredictions(file: File): List<ExamPrediction> =
        file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { json.decodeFromString<ExamPrediction>(it) }

    private fun writePredictions(file: File, predictions: List<ExamPrediction>) {
        file.parentFile?.mkdirs()
        file.writeText(predictions.joinToString("") { json.encodeToString(it) + "\n" }, Charsets.UTF_8)
    }

    private fun sha8(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }.take(8)

    private fun macros(map: Map<String, Double>) = ExamMacros(map["energy"], map["protein"], map["fat"], map["carbs"])

    private fun sourceOf(recordId: String?): String? = when {
        recordId == null -> null
        recordId.startsWith("usda_") -> "USDA"
        recordId.startsWith("ciqual_") -> "CIQUAL"
        recordId.startsWith("off_") -> "OFF"
        recordId.startsWith("kpkn_") -> "KPKN"
        else -> null
    }

    private val renamedCore = mapOf("fiber" to "fiber", "sugars" to "sugar", "sodium" to "sodium", "potassium" to "potassium")
    private val macroKeys = setOf("energy", "protein", "fat", "carbs")

    private fun nutrientsPresent(per100g: Map<String, Double>): List<String> =
        renamedCore.filterKeys { it in per100g }.values.toList() +
            per100g.keys.filter { it !in macroKeys && it !in renamedCore }

    private fun toExam(m: MentionV2): ExamMention = ExamMention(
        text = m.text,
        foodName = m.conceptName,
        foodId = m.recordId,
        source = sourceOf(m.recordId),
        heuristic = m.recordId == null,
        excluded = m.excluded,
        grams = m.grams,
        unit = "g",
        eaten = ExamMacros(m.eatenKcal, m.eatenProtein, m.eatenFat, m.eatenCarbs),
        per100g = macros(m.per100g),
        state = m.state?.name ?: "UNKNOWN",
        autoAccepted = m.autoAccepted,
        questions = m.questions.map { it.dimension.name },
        nutrientsPresent = nutrientsPresent(m.per100g),
    )

    private fun predict(parser: NutritionParserV2, system: String, case: ExamCase): ExamPrediction {
        val start = System.nanoTime()
        return try {
            val parsed = parser.parse(case.text)
            val ms = (System.nanoTime() - start) / 1e6
            ExamPrediction(id = case.id, system = system, latencyMs = ms, mentions = parsed.mentions.map { toExam(it) })
        } catch (e: Throwable) {
            val ms = (System.nanoTime() - start) / 1e6
            ExamPrediction(id = case.id, system = system, latencyMs = ms, error = "${e.javaClass.name}: ${e.message}")
        }
    }

    private fun runAll(parser: NutritionParserV2, system: String, cases: List<ExamCase>): List<ExamPrediction> {
        // Calentamiento: el primer caso paga la carga perezosa de clases.
        runCatching { parser.parse("2 huevos revueltos con pan") }
        return cases.map { predict(parser, system, it) }
    }

    @Test
    fun `fixture cases produce one prediction per case`() {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("nutrition_v2/exam_fixture_cases.jsonl")) {
            "falta el recurso nutrition_v2/exam_fixture_cases.jsonl"
        }
        val cases = stream.bufferedReader(Charsets.UTF_8).use { parseCases(it.readLines()) }
        assertEquals(5, cases.size)

        // Catalogo: el export real si existe; si no, el fixture del repo (el test es siempre rapido).
        val export = File(setting("kpkn.v2.export", "KPKN_V2_EXPORT", "C:/kw/nutri-build/concepts_export.json"))
        val catalog: ConceptCatalogV2 =
            if (export.exists()) export.inputStream().use { ConceptCatalogLoaderV2.read(it) }
            else checkNotNull(javaClass.classLoader.getResourceAsStream("nutrition_v2/concepts_fixture.json")) {
                "falta nutrition_v2/concepts_fixture.json"
            }.use { ConceptCatalogLoaderV2.read(it) }
        val parser = NutritionParserV2(catalog)

        val predictions = runAll(parser, "v2-fixture", cases)
        val out = File("build/nutrition-exam/v2-fixture-predictions.jsonl")
        writePredictions(out, predictions)

        val lines = out.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        assertEquals(cases.size, lines.size)
        assertEquals(cases.map { it.id }, predictions.map { it.id })
        assertEquals(cases.map { it.id }, readPredictions(out).map { it.id })
        assertEquals(predictions, readPredictions(out))
        predictions.forEach { assertNull("error en ${it.id}: ${it.error}", it.error) }
        // Texto vacio: cero menciones. Al menos un caso con comida real.
        assertTrue(predictions.first { it.id == "X0005" }.mentions.isEmpty())
        assertTrue(predictions.any { p -> p.mentions.any { (it.eaten.kcal ?: 0.0) > 0.0 } })
    }

    @Test
    fun `exam run writes all predictions`() {
        val casesPath = setting("kpkn.exam.cases", "KPKN_EXAM_CASES", "")
        Assume.assumeTrue("sin KPKN_EXAM_CASES solo corre el fixture", casesPath.isNotBlank())
        val casesFile = File(casesPath)
        Assume.assumeTrue("no existe el archivo de casos $casesPath", casesFile.exists())
        val exportFile = File(setting("kpkn.v2.export", "KPKN_V2_EXPORT", "C:/kw/nutri-build/concepts_export.json"))
        Assume.assumeTrue("no existe el export ${exportFile.path}", exportFile.exists())

        val catalog = exportFile.inputStream().use { ConceptCatalogLoaderV2.read(it) }
        val system = setting("kpkn.exam.system", "KPKN_EXAM_SYSTEM", "v2@${catalog.generation}+${sha8(exportFile)}")
        val outDir = File(setting("kpkn.exam.out", "KPKN_EXAM_OUT", "C:/kw/nutri-examen/predictions"))
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
        val outFile = File(outDir, "$system-$stamp.jsonl")

        val cases = parseCases(casesFile.readLines(Charsets.UTF_8))
        val parser = NutritionParserV2(catalog)
        val predictions = runAll(parser, system, cases)
        writePredictions(outFile, predictions)

        val lines = outFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        println("EXAM-V2-OUT ${outFile.absolutePath} lines=${lines.size} errors=${predictions.count { it.error != null }}")
        assertEquals(cases.size, lines.size)
    }
}
