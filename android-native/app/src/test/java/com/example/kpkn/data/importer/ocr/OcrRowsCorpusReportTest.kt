package com.example.kpkn.data.importer.ocr

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.training.importer.ExerciseNameMatcher
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.ImportWarning
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.OcrLayout
import com.example.kpkn.domain.training.importer.ProgramImportParser
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Banco de pruebas de las filas que OCR reconstruyó en el teléfono (H4d). Solo corre con KPKN_OCR_ROWS, la carpeta con los
 * `<archivo>.rows.txt` del arnés (si no, se salta). Cada archivo es una hoja: celdas separadas por « | » y, si trae líneas
 * `--- página N ---`, páginas que se juntan como en la app. Pasa por el analizador y el emparejador, igual que el arnés, y
 * escribe el informe en KPKN_IMPORT_REPORT, fuera del repositorio.
 */
class OcrRowsCorpusReportTest {

    @Test
    fun `informe de las filas de OCR con el analizador de la app`() {
        val folder = System.getenv("KPKN_OCR_ROWS")
        assumeTrue("sin KPKN_OCR_ROWS no hay filas que leer", folder != null)
        val reportDir = File(
            requireNotNull(System.getenv("KPKN_IMPORT_REPORT")) { "KPKN_IMPORT_REPORT es obligatoria cuando hay filas" },
        )
        val catalog = ExerciseCatalogV2Loader.decodeApproved(File("src/main/assets/exercise_catalog_v2.json").readText())
        val matcher = ExerciseNameMatcher(catalog)
        val files = File(folder!!).listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".rows.txt") }
            .sortedBy { it.name }

        val results = files.map { measure(it, matcher) }
        OcrRowsReport(results).write(reportDir)

        // El informe ya está escrito; solo un FALLO (excepción no esperada) hace fallar la prueba.
        val failures = results.filter { it.verdict == FALLO }
        assertTrue(
            "Fallos del analizador (ver $reportDir/report.md):\n" + failures.joinToString("\n") { "${it.file}: ${it.message}" },
            failures.isEmpty(),
        )
    }

    /** Lee un `.rows.txt` como lo haría la app con un PDF: una hoja con su nota de OCR, analizada y emparejada. */
    private fun measure(file: File, matcher: ExerciseNameMatcher): FileResult {
        val title = file.name.removeSuffix(".rows.txt").removeSuffix(".pdf")
        return try {
            val started = System.nanoTime()
            val text = file.readText()
            val pages = pagesOf(text)
            // Igual que la app: las páginas se unen quitando los muebles. Sin marcas de página, el archivo es una sola página.
            val joined = OcrLayout.joinPages(pages)
            val rows = joined.rows
            val marked = PAGE_MARK.containsMatchIn(text)
            val sheet = ImportSheet(
                name = title,
                rows = rows,
                notes = listOf(OcrImportSource.IMAGE_NOTE),
                pageStarts = if (marked) joined.pageStarts else listOf(0),
            )
            val draft = ProgramImportParser.parseSheets(listOf(sheet), title)
            val placed = draft.weeks.flatMap { week ->
                week.days.flatMap { day -> day.exercises.map { Placed(week.index, day.name, it) } }
            }
            val names = placed.map { it.exercise.rawName }.distinct()
            val matches = matcher.matchAll(names)
            val confident = names.filter { matches[it]?.isConfident == true }
            val kinds = draft.warnings.groupingBy { it.kind }.eachCount()
            val skipped = kinds[ImportWarningKind.SKIPPED_ROW] ?: 0
            val verdict = when {
                placed.isEmpty() -> NADA
                confident.size * 2 < names.size || skipped > placed.size -> POBRE
                else -> BIEN
            }
            FileResult(
                file = title,
                pages = pages.size,
                rows = rows.size,
                weeks = draft.weeks.size,
                days = draft.weeks.sumOf { it.days.size },
                exercises = placed.size,
                names = names.size,
                recognized = confident.size,
                kinds = kinds,
                verdict = verdict,
                message = "",
                millis = (System.nanoTime() - started) / 1_000_000,
                placed = placed,
                warnings = draft.warnings,
                unknown = names - confident.toSet(),
            )
        } catch (error: Exception) {
            FileResult.failed(title, "${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private companion object {
        const val BIEN = "BIEN"
        const val POBRE = "POBRE"
        const val NADA = "NADA"
        const val FALLO = "FALLO"
        val PAGE_MARK = Regex("""--- página \d+ ---""")

        /** Páginas de un `.rows.txt`: cada línea `--- página N ---` abre una; sin ellas, todo el archivo es una página. */
        fun pagesOf(text: String): List<List<List<String>>> {
            val pages = mutableListOf<MutableList<List<String>>>()
            for (line in text.lines()) {
                if (PAGE_MARK.matches(line.trim())) {
                    pages.add(mutableListOf())
                } else if (line.isNotBlank()) {
                    if (pages.isEmpty()) pages.add(mutableListOf())
                    pages.last().add(line.split(" | "))
                }
            }
            return pages
        }
    }
}

/** Ejercicio con su semana y su día, para el informe de cada archivo. */
private class Placed(val week: Int, val day: String, val exercise: ImportedExercise)

/** Cifras de un archivo de filas: su resultado y lo que salió de él. */
private class FileResult(
    val file: String,
    val pages: Int,
    val rows: Int,
    val weeks: Int,
    val days: Int,
    val exercises: Int,
    val names: Int,
    val recognized: Int,
    val kinds: Map<ImportWarningKind, Int>,
    val verdict: String,
    val message: String,
    val millis: Long,
    val placed: List<Placed>,
    val warnings: List<ImportWarning>,
    val unknown: List<String>,
) {
    companion object {
        fun failed(file: String, message: String) = FileResult(
            file = file, pages = 0, rows = 0, weeks = 0, days = 0, exercises = 0, names = 0, recognized = 0,
            kinds = emptyMap(), verdict = "FALLO", message = message, millis = 0, placed = emptyList(),
            warnings = emptyList(), unknown = emptyList(),
        )
    }
}

/** Informe en `report.md`, `report.json` y `sheets/<archivo>.md`, en la carpeta de KPKN_IMPORT_REPORT. */
private class OcrRowsReport(private val results: List<FileResult>) {

    fun write(dir: File) {
        dir.mkdirs()
        File(dir, "report.json").writeText(json())
        File(dir, "report.md").writeText(markdown())
        val sheets = File(dir, "sheets").apply { mkdirs() }
        results.forEachIndexed { index, result ->
            val name = "%03d-%s.md".format(index + 1, result.file.replace(Regex("""[^\w.-]"""), "_").take(90))
            File(sheets, name).writeText(sheetMarkdown(result))
        }
    }

    private fun markdown(): String = buildString {
        appendLine("# Filas de OCR con el analizador de la app")
        appendLine()
        appendLine("Archivos: ${results.size}. Resultado como en el arnés del teléfono: BIEN, POBRE, NADA o FALLO.")
        appendLine()
        appendLine("## Resumen")
        for (verdict in listOf("FALLO", "NADA", "POBRE", "BIEN")) {
            appendLine("- $verdict: ${results.count { it.verdict == verdict }}")
        }
        val exercises = results.sumOf { it.exercises }
        val names = results.sumOf { it.names }
        val recognized = results.sumOf { it.recognized }
        appendLine("- Ejercicios: $exercises. Nombres por archivo: $names; reconocidos con confianza: $recognized (${percent(recognized, names)}).")
        appendLine("- Avisos por tipo: " + ImportWarningKind.entries.joinToString(", ") { kind ->
            "${kind.name} ${results.sumOf { it.kinds[kind] ?: 0 }}"
        })
        appendLine()
        appendLine("## Tabla")
        appendLine("| archivo | págs | filas | semanas | días | ejercicios | nombres | reconocidos | SKIPPED | IGNORED | EMPTY | NOTHING | NOTE | resultado |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|")
        val order = listOf("FALLO", "NADA", "POBRE", "BIEN")
        for (r in results.sortedWith(compareBy({ order.indexOf(it.verdict) }, { it.file }))) {
            appendLine(
                "| ${cell(r.file)} | ${r.pages} | ${r.rows} | ${r.weeks} | ${r.days} | ${r.exercises} | ${r.names} | " +
                    "${r.recognized} (${percent(r.recognized, r.names)}) | ${count(r, ImportWarningKind.SKIPPED_ROW)} | " +
                    "${count(r, ImportWarningKind.IGNORED_VALUE)} | ${count(r, ImportWarningKind.EMPTY_DAY)} | " +
                    "${count(r, ImportWarningKind.NOTHING_FOUND)} | ${count(r, ImportWarningKind.SHEET_NOTE)} | ${r.verdict} |",
            )
        }
        appendLine()
        appendLine("## Nombres sin reconocer (60 más frecuentes)")
        appendLine("| nombre | archivos |")
        appendLine("|---|---:|")
        results.flatMap { r -> r.unknown.distinct().map { it to r.file } }
            .groupBy({ it.first }, { it.second })
            .map { (name, files) -> name to files.distinct().size }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(60)
            .forEach { (name, files) -> appendLine("| ${cell(name)} | $files |") }
    }

    private fun sheetMarkdown(r: FileResult): String = buildString {
        appendLine("# ${r.file}")
        appendLine()
        appendLine("Resultado: ${r.verdict}. ${r.exercises} ejercicios, ${r.names} nombres (${r.recognized} reconocidos). ${r.millis} ms.")
        if (r.message.isNotEmpty()) appendLine("Mensaje: ${r.message}")
        appendLine()
        appendLine("## Ejercicios (primeros 120)")
        appendLine("| semana | día | ejercicio | series×reps | kg | notas |")
        appendLine("|---:|---|---|---|---:|---|")
        for (p in r.placed.take(120)) {
            val e = p.exercise
            val reps = if (e.repsMin == e.repsMax) "${e.repsMin}" else "${e.repsMin}–${e.repsMax}"
            appendLine("| ${p.week} | ${cell(p.day)} | ${cell(e.rawName)} | ${e.sets}×$reps | ${e.loadKg ?: ""} | ${cell(e.notes.orEmpty())} |")
        }
        appendLine()
        appendLine("## Avisos (primeros 120)")
        appendLine("| tipo | ubicación | mensaje |")
        appendLine("|---|---|---|")
        for (w in r.warnings.take(120)) appendLine("| ${w.kind.name} | ${cell(w.location)} | ${cell(w.message)} |")
    }

    private fun json(): String = buildString {
        appendLine("{")
        appendLine("  \"archivos\": [")
        results.forEachIndexed { index, r ->
            val kinds = ImportWarningKind.entries.joinToString(", ") { "${quoted(it.name)}: ${r.kinds[it] ?: 0}" }
            append(
                "    {\"archivo\": ${quoted(r.file)}, \"resultado\": ${quoted(r.verdict)}, \"paginas\": ${r.pages}, " +
                    "\"filas\": ${r.rows}, \"semanas\": ${r.weeks}, \"dias\": ${r.days}, \"ejercicios\": ${r.exercises}, " +
                    "\"nombresDistintos\": ${r.names}, \"nombresReconocidos\": ${r.recognized}, " +
                    "\"avisosPorTipo\": {$kinds}, \"ms\": ${r.millis}, \"mensaje\": ${quoted(r.message)}}",
            )
            appendLine(if (index < results.lastIndex) "," else "")
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun count(r: FileResult, kind: ImportWarningKind): Int = r.kinds[kind] ?: 0

    private fun percent(part: Int, whole: Int): String =
        if (whole == 0) "0 %" else "${Math.round(part * 1000.0 / whole) / 10.0} %"

    private fun cell(text: String): String =
        text.replace('\n', ' ').replace('|', '/').take(60)

    private fun quoted(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
}
