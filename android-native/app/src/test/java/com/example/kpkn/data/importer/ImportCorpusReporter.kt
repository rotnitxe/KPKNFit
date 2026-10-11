package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ExerciseNameMatch
import com.example.kpkn.domain.training.importer.ExerciseNameMatcher
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ProgramImportParser
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Banco de pruebas del importador sobre un corpus de hojas (solo test: el corpus no es del repositorio). Lee cada
 * .xlsx y .csv con el lector de la app, lo analiza con [ProgramImportParser] y empareja sus nombres con
 * [ExerciseNameMatcher]. Escribe `report.json`, `report.md` y `sheets/<archivo>.md` en la carpeta de salida.
 */
class ImportCorpusReporter(
    private val matcher: ExerciseNameMatcher,
    /** Análisis del borrador: el real por defecto; las pruebas lo cambian para forzar un fallo. */
    private val parse: (List<ImportSheet>, String) -> ImportedProgramDraft = ProgramImportParser::parseSheets,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /** Recorre [corpus] por ruta, escribe el informe en [reportDir] y lo devuelve. */
    fun run(corpus: File, reportDir: File): CorpusReport {
        val found = corpus.walkTopDown()
            .filter { it.isFile && (it.extension.equals("xlsx", true) || it.extension.equals("csv", true)) }
            .sortedBy { it.relativeTo(corpus).invariantSeparatorsPath }
            .toList()
        val analyses = found.map { analyze(corpus, it) }
            .sortedWith(compareBy<Analysis>({ it.report.outcome.ordinal }, { it.report.path }))
        val sheetNames = HashSet<String>()
        val files = analyses.map { it.report.copy(sheetFile = sheetFileName(it.report.path, sheetNames)) }
        val report = CorpusReport(
            corpus = corpus.absolutePath,
            generated = Instant.now().toString(),
            summary = summaryOf(files, ignored = corpus.walkTopDown().count { it.isFile } - found.size),
            unknownNames = unknownNames(analyses),
            files = files,
        )
        val sheetsDir = File(reportDir, "sheets").apply { mkdirs() }
        File(reportDir, "report.json").writeText(json.encodeToString(CorpusReport.serializer(), report))
        File(reportDir, "report.md").writeText(reportMarkdown(report))
        for (file in files) File(sheetsDir, file.sheetFile).writeText(sheetMarkdown(file))
        return report
    }

    /** Lo que sale de un archivo: su informe y los nombres emparejados, para el recuento global. */
    private class Analysis(val report: CorpusFileReport, val matches: Map<String, ExerciseNameMatch>)

    /** Ejercicio con su semana y su día, para las muestras. */
    private class Placed(val week: Int, val day: String, val exercise: ImportedExercise)

    // ---- Un archivo ----------------------------------------------------------------------------------------

    /** Lee, analiza y empareja un archivo. Un rechazo del lector no es un fallo; cualquier otra excepción sí. */
    private fun analyze(root: File, file: File): Analysis {
        val path = file.relativeTo(root).invariantSeparatorsPath
        val title = file.nameWithoutExtension
        val readStart = System.nanoTime()
        val sheets = try {
            FileInputStream(file).use { input ->
                if (file.extension.equals("csv", true)) listOf(CsvReader.read(input, title)) else XlsxReader.read(input)
            }
        } catch (rejected: ImportFileException) {
            return Analysis(
                CorpusFileReport(path, CorpusOutcome.RECHAZO, note = rejected.message, readMs = millisSince(readStart)),
                emptyMap(),
            )
        } catch (error: Throwable) {
            return Analysis(
                CorpusFileReport(path, CorpusOutcome.FALLO, note = failureNote("lectura", error), readMs = millisSince(readStart)),
                emptyMap(),
            )
        }
        val base = CorpusFileReport(
            path = path,
            outcome = CorpusOutcome.BIEN,
            readMs = millisSince(readStart),
            sheets = sheets.map { it.name },
            rows = sheets.sumOf { it.rows.size },
            sheetViews = sheets.map { sheetView(it) },
        )

        val analysisStart = System.nanoTime()
        val draft = try {
            parse(sheets, title)
        } catch (error: Throwable) {
            val failed = base.copy(
                outcome = CorpusOutcome.FALLO,
                note = failureNote("análisis", error),
                analysisMs = millisSince(analysisStart),
            )
            return Analysis(failed, emptyMap())
        }
        val analysisMs = millisSince(analysisStart)
        val placed = draft.weeks.flatMap { week ->
            week.days.flatMap { day -> day.exercises.map { Placed(week.index, day.name, it) } }
        }
        val names = placed.map { it.exercise.rawName.trim() }.distinct()
        val matches = try {
            matcher.matchAll(names)
        } catch (error: Throwable) {
            val failed = base.copy(
                outcome = CorpusOutcome.FALLO,
                note = failureNote("emparejado", error),
                analysisMs = analysisMs,
            )
            return Analysis(failed, emptyMap())
        }

        val confident = matches.values.count { it.isConfident }
        val percent = if (names.isEmpty()) null else confident * 100.0 / names.size
        val warnings = ImportWarningKind.entries.associate { kind -> kind.name to draft.warnings.count { it.kind == kind } }
        val report = base.copy(
            outcome = outcomeOf(placed.size, percent, warnings.getValue(ImportWarningKind.SKIPPED_ROW.name)),
            analysisMs = analysisMs,
            weeks = draft.weeks.size,
            days = draft.weeks.sumOf { it.days.size },
            exercises = placed.size,
            distinctNames = names.size,
            confidentNames = confident,
            confidentPct = percent,
            warnings = warnings,
            exerciseSamples = placed.take(SAMPLE_LIMIT).map { it.sample() },
            warningSamples = draft.warnings.take(SAMPLE_LIMIT).map {
                WarningSample(it.kind.name, it.location, it.message, it.sourceText)
            },
        )
        return Analysis(report, matches)
    }

    /** BIEN salvo que haya menos de la mitad de nombres reconocidos o más avisos SKIPPED_ROW que ejercicios. */
    private fun outcomeOf(exercises: Int, percent: Double?, skipped: Int): CorpusOutcome = when {
        exercises == 0 -> CorpusOutcome.NADA
        (percent != null && percent < POOR_PERCENT) || skipped > exercises -> CorpusOutcome.POBRE
        else -> CorpusOutcome.BIEN
    }

    private fun Placed.sample() = ExerciseSample(
        week = week,
        day = day,
        name = exercise.rawName,
        setsReps = setsReps(exercise),
        kg = exercise.loadKg,
        percent = exercise.percent1Rm,
        rpe = exercise.rpe,
        rir = exercise.rir,
        restSeconds = exercise.restSeconds,
        sourceText = exercise.sourceText,
    )

    private fun setsReps(exercise: ImportedExercise): String {
        val reps = if (exercise.repsMin == exercise.repsMax) "${exercise.repsMin}" else "${exercise.repsMin}-${exercise.repsMax}"
        return "${exercise.sets}×$reps"
    }

    /** Primeras filas no vacías de una hoja, con sus celdas recortadas (la hoja se muestra tal cual la lee la app). */
    private fun sheetView(sheet: ImportSheet): SheetView {
        val width = sheet.rows.maxOfOrNull { it.size } ?: 0
        val filled = sheet.rows.withIndex().filter { (_, cells) -> cells.any { it.isNotBlank() } }
        val shownWidth = minOf(width, VIEW_COLUMNS)
        return SheetView(
            sheet = sheet.name,
            filledRows = filled.size,
            columns = width,
            shown = filled.take(VIEW_ROWS).map { (index, cells) ->
                ViewRow(number = index + 1, cells = (0 until shownWidth).map { cutCell(cells.getOrElse(it) { "" }) })
            },
        )
    }

    private fun failureNote(stage: String, error: Throwable): String =
        "$stage: ${error.javaClass.name}: ${error.message ?: "sin mensaje"}"

    private fun millisSince(startNanos: Long): Double = (System.nanoTime() - startNanos) / 1_000_000.0

    // ---- Recuentos del corpus ------------------------------------------------------------------------------

    private fun summaryOf(files: List<CorpusFileReport>, ignored: Int): CorpusSummary {
        val percents = files.mapNotNull { it.confidentPct }
        return CorpusSummary(
            total = files.size,
            ignored = ignored,
            outcomes = CorpusOutcome.entries.associate { outcome -> outcome.name to files.count { it.outcome == outcome } },
            filesWithNames = percents.size,
            averageConfidentPct = if (percents.isEmpty()) null else percents.average(),
            rejections = files.filter { it.outcome == CorpusOutcome.RECHAZO }
                .groupBy { it.note.orEmpty() }
                .map { (message, group) -> RejectionGroup(message, group.size, group.map { it.path }) }
                .sortedWith(compareByDescending<RejectionGroup> { it.count }.thenBy { it.message }),
            failures = files.filter { it.outcome == CorpusOutcome.FALLO }.map { "${it.path}: ${it.note.orEmpty()}" },
        )
    }

    /** Nombres que el emparejador no reconoce con confianza, por número de archivos que los usan. */
    private fun unknownNames(analyses: List<Analysis>): List<UnknownName> {
        val files = HashMap<String, MutableSet<String>>()
        val matches = HashMap<String, ExerciseNameMatch>()
        for (analysis in analyses) {
            for ((name, match) in analysis.matches) {
                if (match.isConfident) continue
                files.getOrPut(name) { HashSet() }.add(analysis.report.path)
                matches[name] = match
            }
        }
        return files.map { (name, paths) -> Triple(name, paths.size, matches.getValue(name)) }
            .sortedWith(compareByDescending<Triple<String, Int, ExerciseNameMatch>> { it.second }.thenBy { it.first })
            .take(UNKNOWN_LIMIT)
            .map { (name, count, match) ->
                val best = match.best
                UnknownName(
                    name = name,
                    files = count,
                    bestCandidate = best?.let { "${it.displayName} (${it.configurationId})" },
                    confidence = best?.confidence,
                )
            }
    }

    /** Nombre de archivo seguro para Windows, único dentro del informe: `<ruta sin extensión>.md`. */
    private fun sheetFileName(path: String, taken: MutableSet<String>): String {
        val base = path.substringBeforeLast('.').replace(UNSAFE_FILE_CHARS, "_").take(MAX_BASE_CHARS).ifEmpty { "archivo" }
        var name = "$base.md"
        var suffix = 2
        while (!taken.add(name.lowercase())) {
            name = "${base}_$suffix.md"
            suffix++
        }
        return name
    }

    // ---- Markdown -----------------------------------------------------------------------------------------

    private fun reportMarkdown(report: CorpusReport): String = buildString {
        val summary = report.summary
        appendLine("# Informe del banco de pruebas del importador")
        appendLine()
        appendLine("Corpus: `${report.corpus}`. Generado: ${report.generated}.")
        appendLine()
        appendLine(
            "Gravedad, de más a menos: FALLO (excepción no esperada), RECHAZO (el lector rechaza el archivo), " +
                "NADA (0 ejercicios), POBRE (menos de 50 % de nombres reconocidos o más avisos SKIPPED_ROW que ejercicios) " +
                "y BIEN. Archivos de otro tipo, sin leer: ${summary.ignored}.",
        )
        appendLine()
        appendLine("## Resumen")
        appendLine()
        appendLine("| Resultado | Archivos |")
        appendLine("|---|---:|")
        for (outcome in CorpusOutcome.entries) {
            appendLine("| ${outcome.name} | ${summary.outcomes[outcome.name] ?: 0} |")
        }
        appendLine()
        val average = summary.averageConfidentPct?.let { "${rounded(it, 1)} %" } ?: "sin nombres"
        appendLine("Reconocimiento medio: $average (media por archivo; ${summary.filesWithNames} archivos tienen nombres).")
        appendLine()
        appendLine("### Rechazos agrupados")
        appendLine()
        if (summary.rejections.isEmpty()) appendLine("Ninguno.")
        for (group in summary.rejections) appendLine("- ${group.count} × «${mdCell(group.message)}»")
        appendLine()
        appendLine("### Fallos")
        appendLine()
        if (summary.failures.isEmpty()) appendLine("Ninguno.")
        for (failure in summary.failures) appendLine("- ${mdCell(failure)}")
        appendLine()
        appendLine("## Tabla por archivo")
        appendLine()
        appendLine(
            "| Resultado | Archivo | Hojas | Filas | Semanas | Días | Ejercicios | Nombres | Reconocidos " +
                "| Avisos: saltadas / ignorados / día vacío / nada / nota de hoja | Lectura ms | Análisis ms | Nota |",
        )
        appendLine("|---|---|---:|---:|---:|---:|---:|---:|---|---|---:|---:|---|")
        for (file in report.files) {
            val link = "[${mdCell(file.path)}](sheets/${file.sheetFile})"
            appendLine(
                "| ${file.outcome.name} | $link | ${file.sheets.size} | ${file.rows} | ${file.weeks} | ${file.days} " +
                    "| ${file.exercises} | ${file.distinctNames} | ${recognizedText(file)} | ${warningsText(file)} " +
                    "| ${rounded(file.readMs, 1)} | ${rounded(file.analysisMs, 1)} | ${mdCell(cutCell(file.note.orEmpty(), NOTE_CHARS))} |",
            )
        }
        appendLine()
        appendLine("## Nombres sin reconocer")
        appendLine()
        appendLine("Los $UNKNOWN_LIMIT más frecuentes del corpus, con los archivos que los usan. Es la lista para ampliar sinónimos.")
        appendLine()
        appendLine("| # | Nombre | Archivos | Mejor candidato | Confianza |")
        appendLine("|---:|---|---:|---|---:|")
        report.unknownNames.forEachIndexed { index, name ->
            val confidence = name.confidence?.let { rounded(it, 2) } ?: "-"
            appendLine("| ${index + 1} | ${mdCell(name.name)} | ${name.files} | ${mdCell(name.bestCandidate.orEmpty())} | $confidence |")
        }
    }

    private fun sheetMarkdown(file: CorpusFileReport): String = buildString {
        val noteText = file.note?.let { ". Nota: ${mdCell(it)}" } ?: ""
        appendLine("# ${mdCell(file.path)}")
        appendLine()
        appendLine("Resultado: ${file.outcome.name}$noteText")
        appendLine()
        appendLine("- Hojas (${file.sheets.size}): ${file.sheets.joinToString { "«${mdCell(it)}»" }}")
        appendLine("- Filas: ${file.rows}. Semanas: ${file.weeks}. Días: ${file.days}. Ejercicios: ${file.exercises}.")
        appendLine("- Nombres distintos: ${file.distinctNames}. Reconocidos con confianza: ${recognizedText(file)}.")
        appendLine("- Avisos: ${ImportWarningKind.entries.joinToString(", ") { "${it.name} ${file.warnings[it.name] ?: 0}" }}.")
        appendLine("- Tiempos: lectura ${rounded(file.readMs, 1)} ms, análisis ${rounded(file.analysisMs, 1)} ms.")
        appendLine()
        appendLine("## Muestras de ejercicios (primeros ${file.exerciseSamples.size})")
        appendLine()
        if (file.exerciseSamples.isEmpty()) {
            appendLine("(ninguno)")
        } else {
            appendLine("| Semana | Día | Nombre | Series×reps | Kg | % | RPE | RIR | Descanso (s) | Texto original |")
            appendLine("|---:|---|---|---|---:|---:|---:|---:|---:|---|")
            for (sample in file.exerciseSamples) {
                appendLine(
                    "| ${sample.week} | ${mdCell(sample.day)} | ${mdCell(sample.name)} | ${sample.setsReps} " +
                        "| ${number(sample.kg)} | ${number(sample.percent)} | ${number(sample.rpe)} " +
                        "| ${sample.rir ?: "-"} | ${sample.restSeconds ?: "-"} | ${mdCell(sample.sourceText.orEmpty())} |",
                )
            }
        }
        appendLine()
        appendLine("## Avisos (primeros ${file.warningSamples.size})")
        appendLine()
        if (file.warningSamples.isEmpty()) {
            appendLine("(ninguno)")
        } else {
            appendLine("| Tipo | Ubicación | Mensaje | Texto original |")
            appendLine("|---|---|---|---|")
            for (warning in file.warningSamples) {
                appendLine("| ${warning.kind} | ${mdCell(warning.location)} | ${mdCell(warning.message)} | ${mdCell(warning.sourceText.orEmpty())} |")
            }
        }
        appendLine()
        appendLine("## Vistas de hoja")
        if (file.sheetViews.isEmpty()) {
            appendLine()
            appendLine("(sin hojas)")
        }
        for (view in file.sheetViews) {
            appendLine()
            appendLine("### Hoja «${mdCell(view.sheet)}»: ${view.filledRows} filas con datos, ${view.columns} columnas")
            appendLine()
            if (view.shown.isEmpty()) {
                appendLine("(sin filas con datos)")
                continue
            }
            val width = minOf(view.columns, VIEW_COLUMNS)
            appendLine("| Fila | " + (0 until width).joinToString(" | ") { columnLetter(it) } + " |")
            appendLine("|---|" + "---|".repeat(width))
            for (row in view.shown) {
                appendLine("| ${row.number} | " + row.cells.joinToString(" | ") { mdCell(it) } + " |")
            }
            if (view.filledRows > view.shown.size) appendLine("(se muestran ${view.shown.size} de ${view.filledRows} filas con datos)")
            if (view.columns > width) appendLine("(se muestran $width de ${view.columns} columnas)")
        }
    }

    private fun recognizedText(file: CorpusFileReport): String {
        val pct = file.confidentPct ?: return "-"
        return "${file.confidentNames} (${rounded(pct, 1)} %)"
    }

    private fun warningsText(file: CorpusFileReport): String =
        ImportWarningKind.entries.joinToString(" / ") { (file.warnings[it.name] ?: 0).toString() }

    private fun number(value: Double?): String = when {
        value == null -> "-"
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> value.toString()
    }

    private fun rounded(value: Double, digits: Int): String {
        val factor = Math.pow(10.0, digits.toDouble())
        return (Math.round(value * factor) / factor).toString()
    }

    /** Letra de columna de hoja de cálculo: 0 → A, 25 → Z, 26 → AA. */
    private fun columnLetter(index: Int): String {
        var position = index + 1
        val letters = StringBuilder()
        while (position > 0) {
            letters.insert(0, 'A' + (position - 1) % 26)
            position = (position - 1) / 26
        }
        return letters.toString()
    }

    companion object {
        private const val SAMPLE_LIMIT = 15
        private const val VIEW_ROWS = 25
        private const val VIEW_COLUMNS = 16
        private const val CELL_CHARS = 24
        private const val NOTE_CHARS = 160
        private const val UNKNOWN_LIMIT = 60
        private const val POOR_PERCENT = 50.0
        private const val MAX_BASE_CHARS = 100
        private val UNSAFE_FILE_CHARS = Regex("[^\\p{L}\\p{N}_-]")

        /** Recorta [text] a [limit] caracteres; lo que sobra se cambia por «…» (la elipsis cuenta en el límite). */
        internal fun cutCell(text: String, limit: Int = CELL_CHARS): String {
            if (text.length <= limit) return text
            var end = limit - 1
            // No se corta un par de sustitutos (emoji): se deja fuera el primero.
            if (end > 0 && text[end - 1].isHighSurrogate()) end--
            return text.substring(0, end) + "…"
        }

        /** Escapa una celda para la tabla Markdown: los saltos de línea salen como «\n» y las barras como «\|». */
        internal fun mdCell(text: String): String =
            text.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\n").replace("|", "\\|")
    }
}

/** Resultado de un archivo; el orden de declaración es la gravedad, de más a menos. */
@Serializable
enum class CorpusOutcome { FALLO, RECHAZO, NADA, POBRE, BIEN }

@Serializable
data class CorpusReport(
    val corpus: String,
    val generated: String,
    val summary: CorpusSummary,
    val unknownNames: List<UnknownName>,
    val files: List<CorpusFileReport>,
)

@Serializable
data class CorpusSummary(
    val total: Int,
    val ignored: Int,
    val outcomes: Map<String, Int>,
    val filesWithNames: Int,
    val averageConfidentPct: Double?,
    val rejections: List<RejectionGroup>,
    val failures: List<String>,
)

@Serializable
data class RejectionGroup(val message: String, val count: Int, val files: List<String>)

@Serializable
data class UnknownName(val name: String, val files: Int, val bestCandidate: String?, val confidence: Double?)

@Serializable
data class CorpusFileReport(
    val path: String,
    val outcome: CorpusOutcome,
    /** Motivo del rechazo, o etapa y excepción del fallo. */
    val note: String? = null,
    /** Nombre del .md de este archivo dentro de `sheets/`. */
    val sheetFile: String = "",
    val readMs: Double = 0.0,
    val analysisMs: Double = 0.0,
    val sheets: List<String> = emptyList(),
    val rows: Int = 0,
    val weeks: Int = 0,
    val days: Int = 0,
    val exercises: Int = 0,
    val distinctNames: Int = 0,
    val confidentNames: Int = 0,
    val confidentPct: Double? = null,
    /** Avisos por tipo (siempre los cinco, aunque sean cero). */
    val warnings: Map<String, Int> = emptyMap(),
    val exerciseSamples: List<ExerciseSample> = emptyList(),
    val warningSamples: List<WarningSample> = emptyList(),
    val sheetViews: List<SheetView> = emptyList(),
)

@Serializable
data class ExerciseSample(
    val week: Int,
    val day: String,
    val name: String,
    val setsReps: String,
    val kg: Double?,
    val percent: Double?,
    val rpe: Double?,
    val rir: Int?,
    val restSeconds: Int?,
    val sourceText: String?,
)

@Serializable
data class WarningSample(val kind: String, val location: String, val message: String, val sourceText: String?)

@Serializable
data class SheetView(val sheet: String, val filledRows: Int, val columns: Int, val shown: List<ViewRow>)

@Serializable
data class ViewRow(val number: Int, val cells: List<String>)
