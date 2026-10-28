package com.example.kpkn.domain.nutrition.exam

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Corredor de predicciones del examen de nutricion (docs/nutrition-v2/EXAMEN.md, secciones 2 y 6) para el sistema actual (master).
 *
 * Lee casos JSONL, ejecuta la cadena real de la app con el catalogo real de assets ([MasterExamPipeline]) y escribe un
 * JSONL de predicciones. Entradas (propiedad de sistema o variable de entorno):
 *  - `kpkn.exam.cases` / `KPKN_EXAM_CASES`   (por defecto `C:/kw/nutri-examen/cases/all.jsonl`)
 *  - `kpkn.exam.out` / `KPKN_EXAM_OUT`       (por defecto `C:/kw/nutri-examen/predictions`)
 *  - `kpkn.exam.system` / `KPKN_EXAM_SYSTEM` (por defecto `master`); el archivo es `<sistema>-<yyyyMMdd-HHmm>.jsonl`.
 *
 * Run: `./gradlew :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.exam.NutritionExamPredictionRunnerTest"`
 */
class NutritionExamPredictionRunnerTest {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    private fun setting(property: String, env: String, default: String): String =
        System.getProperty(property) ?: System.getenv(env) ?: default

    private fun readCases(file: File): List<ExamCase> =
        file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { json.decodeFromString<ExamCase>(it) }

    private fun readPredictions(file: File): List<ExamPrediction> =
        file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { json.decodeFromString<ExamPrediction>(it) }

    private fun writePredictions(file: File, predictions: List<ExamPrediction>) {
        file.parentFile?.mkdirs()
        file.writeText(predictions.joinToString("") { json.encodeToString(it) + "\n" }, Charsets.UTF_8)
    }

    private suspend fun runAll(pipeline: MasterExamPipeline, cases: List<ExamCase>): List<ExamPrediction> {
        // Calentamiento: el primer caso paga la carga perezosa de clases y no debe contar contra el limite de 5 s.
        pipeline.predict(ExamCase(id = "WARMUP", text = "2 huevos revueltos con pan", mealType = "BREAKFAST"))
        return cases.map { pipeline.predict(it) }
    }

    @Test
    fun `fixture cases produce one prediction per case`() = runBlocking {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("nutrition_exam/fixture_cases.jsonl")) {
            "falta el recurso nutrition_exam/fixture_cases.jsonl"
        }
        val cases = stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readLines().filter { it.isNotBlank() }.map { json.decodeFromString<ExamCase>(it) }
        }
        assertEquals(6, cases.size)

        val pipeline = MasterExamPipeline.create("master-fixture")
        println("EXAM-CATALOG usda=${pipeline.usdaRows} off=${pipeline.offRows} index=${pipeline.indexSize}")
        File("build/nutrition-exam").mkdirs()
        File("build/nutrition-exam/catalog-size.txt")
            .writeText("usda=${pipeline.usdaRows} off=${pipeline.offRows} index=${pipeline.indexSize}\n")

        val predictions = runAll(pipeline, cases)
        val out = File("build/nutrition-exam/fixture-predictions.jsonl")
        writePredictions(out, predictions)

        val lines = out.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        assertEquals(cases.size, lines.size)
        assertEquals(cases.map { it.id }, predictions.map { it.id })
        assertEquals(cases.map { it.id }, readPredictions(out).map { it.id })

        val withFood = predictions.count { p ->
            p.mentions.any { m ->
                !m.excluded && m.eaten.kcal.let { it != null && it.isFinite() && it >= 0.0 }
            }
        }
        assertTrue("al menos 4 casos con una mencion con kcal finitas >= 0 (hubo $withFood): $predictions", withFood >= 4)

        val agua = predictions.first { it.id == "F0004" }
        val aguaKcal = agua.mentions.filter { !it.excluded }.sumOf { it.eaten.kcal ?: 0.0 }
        assertTrue("agua debe tener < 5 kcal (tuvo $aguaKcal): $agua", aguaKcal < 5.0)

        // El archivo se vuelve a leer con las mismas data classes y conserva el contenido.
        assertEquals(predictions, readPredictions(out))
    }

    @Test
    fun `exam run writes all predictions`() = runBlocking {
        val casesPath = setting("kpkn.exam.cases", "KPKN_EXAM_CASES", "C:/kw/nutri-examen/cases/all.jsonl")
        val casesFile = File(casesPath)
        Assume.assumeTrue("no existe el archivo de casos $casesPath", casesFile.exists())
        val outDir = File(setting("kpkn.exam.out", "KPKN_EXAM_OUT", "C:/kw/nutri-examen/predictions"))
        val system = setting("kpkn.exam.system", "KPKN_EXAM_SYSTEM", "master")
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
        val outFile = File(outDir, "$system-$stamp.jsonl")

        val cases = readCases(casesFile)
        val pipeline = MasterExamPipeline.create(system)
        println("EXAM-CATALOG usda=${pipeline.usdaRows} off=${pipeline.offRows} index=${pipeline.indexSize}")
        val predictions = runAll(pipeline, cases)
        writePredictions(outFile, predictions)

        val lines = outFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        println("EXAM-OUT ${outFile.absolutePath} lines=${lines.size} errors=${predictions.count { it.error != null }}")
        assertEquals(cases.size, lines.size)
    }
}
