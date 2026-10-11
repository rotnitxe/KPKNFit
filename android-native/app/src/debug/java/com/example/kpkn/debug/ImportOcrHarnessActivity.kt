package com.example.kpkn.debug

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.kpkn.data.importer.readSheets
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.training.importer.ExerciseNameMatcher
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
 * SOLO DEPURACIÓN (no se integra): lee con OCR cada .pdf, .jpg, .jpeg y .png de `getExternalFilesDir("ocr-corpus")` por el
 * mismo camino que la app: `readSheets`, `ProgramImportParser.parseSheets` y el emparejador con el catálogo. Escribe en
 * `getExternalFilesDir("ocr-report")` un `ocr-report.json` con las cifras de cada archivo y un `<archivo>.rows.txt` con sus
 * filas reconstruidas (celdas separadas por « | »). Muestra «Listo: N archivos» y lo deja en logcat con la etiqueta
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
        setContentView(status)
        lifecycleScope.launch {
            status.text = try {
                "Listo: ${runCorpus()} archivos"
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                "Error: ${error.javaClass.simpleName}: ${error.message}"
            }
        }
    }

    private suspend fun runCorpus(): Int {
        val corpus = getExternalFilesDir(CORPUS_DIR) ?: error("Sin almacenamiento externo de la app")
        val report = getExternalFilesDir(REPORT_DIR) ?: error("Sin almacenamiento externo de la app")
        report.mkdirs()
        val files = corpus.listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() in SUPPORTED }
            .sortedBy { it.name }
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
        File(report, "ocr-report.json").writeText(JSONObject().put("archivos", results).toString(2))
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
            val read = readSheets(contentResolver, Uri.fromFile(file), null, cacheDir) { page, _ ->
                progress.add(page to System.nanoTime())
            }
            val ended = System.nanoTime()
            val sheets = read.getOrElse { error ->
                return Outcome(json.put("resultado", "RECHAZO").put("mensaje", error.message.orEmpty()), "")
            }
            val msPerPage = progress.mapIndexed { index, (_, at) ->
                val next = progress.getOrNull(index + 1)?.second ?: ended
                round((next - at) / 1_000_000.0 * 10) / 10
            }
            // Igual que en la app, el título de la hoja es el nombre del archivo sin extensión.
            val named = sheets.map { it.copy(name = file.nameWithoutExtension) }
            val draft = withContext(Dispatchers.Default) {
                ProgramImportParser.parseSheets(named, file.nameWithoutExtension)
            }
            val exercises = draft.weeks.flatMap { week -> week.days.flatMap { it.exercises } }
            val names = exercises.map { it.rawName }.distinct()
            val matches = withContext(Dispatchers.Default) { matcher.matchAll(names) }
            val recognized = names.count { matches[it]?.isConfident == true }
            val skipped = draft.warnings.count { it.kind == ImportWarningKind.SKIPPED_ROW }
            val rows = sheets.flatMap { it.rows }
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
            Outcome(json, rows.joinToString("\n") { row -> row.joinToString(" | ") })
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Outcome(json.put("resultado", "FALLO").put("mensaje", "${error.javaClass.simpleName}: ${error.message}"), "")
        }
    }

    /** Lo que sale de medir un archivo: su entrada del informe y el texto de sus filas. */
    private class Outcome(val json: JSONObject, val rowsText: String)

    private companion object {
        const val TAG = "KpknOcrHarness"
        const val CORPUS_DIR = "ocr-corpus"
        const val REPORT_DIR = "ocr-report"
        const val CATALOG = "exercise_catalog_v2.json"
        val SUPPORTED = setOf("pdf", "jpg", "jpeg", "png")
    }
}
