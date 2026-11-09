package com.example.kpkn.debug

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.kpkn.data.importer.readSheets
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.training.importer.ExerciseNameMatcher
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ProgramImportParser
import java.io.File
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * SOLO DEPURACIÓN (no se integra): lee con OCR cada .pdf, .jpg, .jpeg, .png, .webp y .heic de `getExternalFilesDir("ocr-corpus")`
 * por el mismo camino que la app: `readSheets`, `ProgramImportParser.parseSheets` y el emparejador con el catálogo. Escribe en
 * `getExternalFilesDir("ocr-report")` un `ocr-report.json` con las cifras de cada archivo y un `<archivo>.rows.txt` con sus
 * filas reconstruidas (celdas separadas por « | » y, en un PDF, una línea `--- página N ---` antes de las filas de cada página,
 * sin quitar los muebles). Un `<archivo>.<extensión>.expected.txt` opcional junto a la entrada añade métricas contra la
 * transcripción esperada; el texto de referencia no se copia al informe. Muestra «Listo: N archivos» y lo deja en logcat con la etiqueta
 * KpknOcrHarness. Los milisegundos por página son el tiempo entre dos avisos de progreso (dibujo y OCR de esa página); el
 * último incluye la reconstrucción de filas.
 */
class ImportOcrHarnessActivity : ComponentActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(48, 160, 48, 48)
            text = "Leyendo el corpus con OCR…"
        }
        setContentView(ScrollView(this).apply { addView(status) })
        lifecycleScope.launch {
            status.text = try {
                val count = runCorpus()
                if (intent.getStringExtra(EXTRA_ONLY_FILE).isNullOrBlank()) "Listo: $count archivos" else status.text
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                "Error: ${error.javaClass.simpleName}: ${error.message}"
            }
        }
    }

    private suspend fun runCorpus(): Int {
        val corpus = getExternalFilesDir(CORPUS_DIR) ?: error("Sin almacenamiento externo de la app")
        val onlyFile = intent.getStringExtra(EXTRA_ONLY_FILE)?.trim()?.takeIf(String::isNotEmpty)
        val reportDir = intent.getStringExtra(EXTRA_REPORT_DIR)
            ?.takeIf { it.matches(Regex("[a-z0-9_-]+")) }
            ?: REPORT_DIR
        val report = getExternalFilesDir(reportDir) ?: error("Sin almacenamiento externo de la app")
        report.mkdirs()
        val candidates = corpus.listFiles().orEmpty()
            .filter(::isOcrHarnessCorpusFile)
            .sortedBy { it.name }
        val files = onlyFile?.let { name -> candidates.filter { it.name == name } } ?: candidates
        if (onlyFile != null && files.isEmpty()) error("No se encontró $onlyFile en el corpus de evaluación")
        val matcher = withContext(Dispatchers.IO) {
            val catalog = assets.open(CATALOG).bufferedReader().use { it.readText() }
            ExerciseNameMatcher(ExerciseCatalogV2Loader.decodeApproved(catalog))
        }
        val results = JSONArray()
        for (file in files) {
            val outcome = measure(file, matcher)
            results.put(outcome.json)
            File(report, "${file.name}.rows.txt").writeText(outcome.rowsText)
            Log.i(TAG, "${file.name}: ${outcome.json.optString("resultado")} ${outcome.json.optString("mensaje")}")
        }
        val reportJson = JSONObject().put("archivos", results)
        File(report, "ocr-report.json").writeText(reportJson.toString(2))
        if (onlyFile != null) status.text = diagnosticText(onlyFile, results.optJSONObject(0), File(report, "$onlyFile.rows.txt"))
        Log.i(TAG, "Listo: ${files.size} archivos en ${report.absolutePath}")
        return files.size
    }

    /** Lee un archivo con el camino de la app y mide sus cifras. Un fallo se anota en el informe y no para el resto. */
    private suspend fun measure(file: File, matcher: ExerciseNameMatcher): Outcome {
        val isPdf = file.extension.equals("pdf", ignoreCase = true)
        val json = JSONObject()
            .put("archivo", file.name)
            .put("tipo", if (isPdf) "pdf" else "imagen")
        return try {
            val started = System.nanoTime()
            val progress = mutableListOf<Pair<Int, Long>>()
            val recognizedPages = mutableListOf<Pair<Int, com.example.kpkn.domain.training.importer.OcrPage>>()
            val read = readSheets(
                contentResolver,
                Uri.fromFile(file),
                null,
                cacheDir,
                onOcrPage = { pageNumber, page -> recognizedPages += pageNumber to page },
                onProgress = { page, _ -> progress.add(page to System.nanoTime()) },
            )
            val ended = System.nanoTime()
            val sheets = read.getOrElse { error ->
                return Outcome(json.put("resultado", "RECHAZO").put("mensaje", error.message.orEmpty()), "")
            }
            sheets.mapNotNull { it.ocrConfidence }.singleOrNull()?.let { confidence ->
                json.put("ocrPalabrasDetectadas", confidence.detectedWords)
                    .put("ocrPalabrasConConfianza", confidence.scoredWords)
                    .put("ocrPalabrasConfianzaBaja", confidence.lowConfidenceWords)
                    .put("ocrConfianzaMinima", confidence.minimumConfidence ?: JSONObject.NULL)
                    .put("ocrConfianzaMedia", confidence.meanConfidence ?: JSONObject.NULL)
            }
            val msPerPage = progress.mapIndexed { index, (_, at) ->
                val next = progress.getOrNull(index + 1)?.second ?: ended
                round((next - at) / 1_000_000.0 * 10) / 10
            }
            // Igual que en la app, el título de la hoja es el nombre del archivo sin extensión.
            val named = sheets.map { it.copy(name = file.nameWithoutExtension) }
            json.put(
                "ocrTokensPorPagina",
                JSONArray(recognizedPages.map { (pageNumber, page) ->
                    JSONObject()
                        .put("pagina", pageNumber)
                        .put("ancho", page.width)
                        .put("alto", page.height)
                        .put("tokens", JSONArray(page.words.map { word ->
                            JSONObject()
                                .put("texto", word.text)
                                .put("caja", JSONArray(listOf(word.left, word.top, word.right, word.bottom)))
                                .put("confianza", word.confidence?.toDouble() ?: JSONObject.NULL)
                        }))
                }),
            )
            json.put(
                "ocrHojas",
                JSONArray(
                    named.map { sheet ->
                        JSONObject()
                            .put("nombre", sheet.name)
                            .put("filas", sheet.rows.size)
                            .put("iniciosPagina", JSONArray(sheet.pageStarts))
                    },
                ),
            )
            val draft = withContext(Dispatchers.Default) {
                ProgramImportParser.parseSheets(named, file.nameWithoutExtension)
            }
            val exercises = draft.weeks.flatMap { week -> week.days.flatMap { it.exercises } }
            val names = exercises.map { it.rawName }.distinct()
            val matches = withContext(Dispatchers.Default) { matcher.matchAll(names) }
            val recognized = names.count { matches[it]?.isConfident == true }
            val skipped = draft.warnings.count { it.kind == ImportWarningKind.SKIPPED_ROW }
            val rows = sheets.flatMap { it.rows }
            val rowsText = rowsTextOf(sheets)
            val verdict = when {
                exercises.isEmpty() -> "NADA"
                recognized * 2 < names.size || skipped > exercises.size -> "POBRE"
                else -> "BIEN"
            }
            json.put("resultado", verdict)
                .put("paginas", if (isPdf) progress.size else 1)
                .put("msPorPagina", JSONArray(msPerPage))
                .put("msTotal", (ended - started) / 1_000_000)
                .put("filas", rows.size)
                .put("semanas", draft.weeks.size)
                .put("dias", draft.weeks.sumOf { it.days.size })
                .put("ejercicios", exercises.size)
                .put("nombresDistintos", names.size)
                .put("nombresReconocidos", recognized)
                .put("avisosPorTipo", JSONObject(draft.warnings.groupingBy { it.kind.name }.eachCount()))
                .put(
                    "nombres",
                    JSONArray(
                        names.map { name ->
                            val match = matches[name]
                            JSONObject()
                                .put("nombre", name)
                                .put("confiable", match?.isConfident == true)
                                .put("configuracion", match?.best?.configurationId.orEmpty())
                                .put("confianza", match?.best?.confidence ?: 0.0)
                        },
                    ),
                )
                .put(
                    "ejerciciosPorDia",
                    JSONArray(
                        draft.weeks.flatMap { week ->
                            week.days.map { day ->
                                JSONObject()
                                    .put("semana", week.index)
                                    .put("dia", day.name)
                                    .put("ejercicios", JSONArray(day.exercises.map { it.rawName }))
                            }
                        },
                    ),
                )
                .put(
                    "ejerciciosConOrigen",
                    JSONArray(draft.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises.map { exercise ->
                        JSONObject()
                            .put("semana", week.index)
                            .put("dia", day.name)
                            .put("nombre", exercise.rawName)
                            .put("tipoDocumento", exercise.sourceDocumentType.name)
                            .put("pagina", exercise.sourcePage ?: JSONObject.NULL)
                            .put("hoja", exercise.sourceSheet ?: JSONObject.NULL)
                            .put("fila", exercise.sourceRow ?: JSONObject.NULL)
                            .put("textoOrigen", exercise.sourceText ?: JSONObject.NULL)
                    } } }),
                )
                .put(
                    "avisosConOrigen",
                    JSONArray(draft.warnings.map { warning ->
                        JSONObject()
                            .put("id", warning.id)
                            .put("tipo", warning.kind.name)
                            .put("tipoDocumento", warning.sourceDocumentType.name)
                            .put("ubicacion", warning.location)
                            .put("pagina", warning.sourcePage ?: JSONObject.NULL)
                            .put("hoja", warning.sourceSheet ?: JSONObject.NULL)
                            .put("fila", warning.sourceRow ?: JSONObject.NULL)
                            .put("cantidad", warning.itemCount ?: JSONObject.NULL)
                            .put("textoTruncado", warning.sourceTextTruncated)
                            .put("textoOrigen", warning.sourceText ?: JSONObject.NULL)
                    }),
                )
            val expectedFile = expectedOcrTranscriptFile(file.parentFile ?: File("."), file)
            if (expectedFile.isFile) {
                val metrics = compareOcrTranscripts(expectedFile.readText(Charsets.UTF_8), rowsText)
                json.put("transcripcionEsperada", true)
                    .put("filasReferencia", metrics.expectedRows)
                    .put("filasReconocidas", metrics.recognizedRows)
                    .put("filasExactasPct", metrics.exactRowsPct ?: JSONObject.NULL)
                    .put("celdasExactasPct", metrics.exactCellsPct ?: JSONObject.NULL)
                    .put("werPct", metrics.wordErrorRatePct ?: JSONObject.NULL)
                    .put("cerPct", metrics.characterErrorRatePct ?: JSONObject.NULL)
            } else {
                json.put("transcripcionEsperada", false)
            }
            Outcome(json, rowsText)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Outcome(json.put("resultado", "FALLO").put("mensaje", "${error.javaClass.simpleName}: ${error.message}"), "")
        }
    }

    /** Filas de las hojas para el `.rows.txt`: en un PDF, una línea `--- página N ---` antes de las filas de cada página. */
    private fun rowsTextOf(sheets: List<ImportSheet>): String = sheets.joinToString("\n") { sheet ->
        if (sheet.ocrPages.isEmpty()) {
            sheet.rows.joinToString("\n") { row -> row.joinToString(" | ") }
        } else {
            sheet.ocrPages.withIndex().joinToString("\n") { (index, page) ->
                "--- página ${index + 1} ---\n" + page.joinToString("\n") { row -> row.joinToString(" | ") }
            }
        }
    }

    /** Resumen legible de una muestra para capturarla sin exportar el directorio privado de resultados. */
    private fun diagnosticText(fileName: String, result: JSONObject?, rowsFile: File): String {
        result ?: return "No se generó resultado para $fileName"
        val starts = result.optJSONArray("ocrHojas")?.optJSONObject(0)?.optJSONArray("iniciosPagina")
        val startValues = starts?.let { values ->
            (0 until values.length()).joinToString(prefix = "[", postfix = "]") { values.optInt(it).toString() }
        } ?: "no informado"
        val days = result.optJSONArray("ejerciciosPorDia")
        return buildString {
            appendLine("EVALUACIÓN OCR · $fileName")
            appendLine("Resultado: ${result.optString("resultado")}")
            appendLine("Filas: ${result.optInt("filas")} · ejercicios: ${result.optInt("ejercicios")} · nombres: ${result.optInt("nombresDistintos")} (${result.optInt("nombresReconocidos")} confiables)")
            appendLine("Semanas: ${result.optInt("semanas")} · días: ${result.optInt("dias")} · páginas: ${result.optInt("paginas")}")
            appendLine("iniciosPagina: $startValues")
            appendLine()
            appendLine("EJERCICIOS POR DÍA")
            if (days == null) {
                appendLine("Sin desglose diario")
            } else {
                for (index in 0 until days.length()) {
                    val day = days.optJSONObject(index) ?: continue
                    val exerciseNames = day.optJSONArray("ejercicios")
                    val names = exerciseNames?.let { values ->
                        (0 until values.length()).joinToString { values.optString(it) }
                    }.orEmpty().ifBlank { "sin ejercicios" }
                    appendLine("S${day.optInt("semana")} · ${day.optString("dia")}: $names")
                }
            }
            if (rowsFile.isFile) {
                appendLine()
                appendLine("FILAS OCR")
                append(rowsFile.readText(Charsets.UTF_8))
            }
        }
    }

    /** Lo que sale de medir un archivo: su entrada del informe y el texto de sus filas. */
    private class Outcome(val json: JSONObject, val rowsText: String)

    private companion object {
        const val TAG = "KpknOcrHarness"
        const val CORPUS_DIR = "ocr-corpus"
        const val REPORT_DIR = "ocr-report"
        const val EXTRA_ONLY_FILE = "onlyFile"
        const val EXTRA_REPORT_DIR = "reportDirName"
        const val CATALOG = "exercise_catalog_v2.json"
    }
}

private val OCR_HARNESS_EXTENSIONS = setOf("pdf", "jpg", "jpeg", "png", "webp", "heic")

internal fun isOcrHarnessCorpusFile(file: File): Boolean =
    file.isFile && file.extension.lowercase() in OCR_HARNESS_EXTENSIONS
