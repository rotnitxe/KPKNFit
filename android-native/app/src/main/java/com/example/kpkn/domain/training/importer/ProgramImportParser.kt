package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.ExerciseMatchLexicon
import kotlin.math.roundToInt

/**
 * Analizador puro de programas importados (hojas de cálculo o texto pegado). Convierte las filas en semanas, días
 * y ejercicios: reconoce semanas y días por nombre de hoja o por fila de encabezado («Semana 2», «Día 1 — Pierna»,
 * «Lunes:», «Session 3», «Mon Pull»), columnas por cabecera en español e inglés y notación compacta («4x8», «3×10 RPE8»,
 * «5x5 @100kg», «4 series de 6», «1x5+», «3x8-12», «5x5 Squat»). Lo que no entiende va a `warnings`, con su tipo, semana
 * y día. La prosa, las instrucciones, los calentamientos y las etiquetas no son ejercicios: se cuentan por hoja en un
 * aviso `SHEET_NOTE`. Las hojas de instrucciones y de cálculo se omiten por su nombre. Nunca lanza excepciones por
 * contenido raro. Los ids de días y ejercicios son `w<semana>-d<n>` y `w<semana>-d<n>-e<m>`, contando desde 1 sobre el
 * borrador final.
 */
object ProgramImportParser {

    private const val MAX_SETS = 20
    internal const val MAX_REPS = 100
    internal const val MAX_LOAD_KG = 1000.0
    internal const val MAX_PERCENT = 100.0
    internal const val MAX_REST_SECONDS = 1800
    internal const val MAX_NOTE_CHARS = 200

    /** Origen de un texto pegado en los avisos de nota de hoja (un texto no tiene hojas). */
    private const val PASTED_TEXT = "Texto pegado"

    /** Convierte hojas (una por día, una por semana o una sola con encabezados) en un borrador. */
    fun parseSheets(sheets: List<ImportSheet>, title: String): ImportedProgramDraft {
        val builder = DraftBuilder()
        for (sheet in sheets) {
            if (isInstructionOrCalcSheet(sheet.name)) {
                builder.noteOmittedSheet(sheet.name)
                continue
            }
            val weekInName = weekNumber(sheet.name)
            if (weekInName != null) builder.openWeek(weekInName)
            builder.beginSheet()
            // Una hoja sin nombre de semana es un día: su nombre sirve si no trae un encabezado propio.
            builder.pendingDayName = if (weekInName == null) usableSheetName(sheet.name) else null
            processRows(builder, sheet.rows) { index -> "Hoja «${sheet.name}», fila ${index + 1}" }
            builder.endSheet("Hoja «${sheet.name}»")
        }
        return builder.finish(title)
    }

    /**
     * Convierte texto pegado: líneas de semana, día y ejercicio. Si la mayoría de líneas llevan tabuladores
     * (texto copiado de una hoja de cálculo), cada línea es una fila de tabla.
     */
    fun parseText(text: String, title: String): ImportedProgramDraft {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val tabbed = lines.count { it.contains('\t') }
        val filled = lines.count { it.isNotBlank() }
        val rows = if (tabbed >= 2 && tabbed * 2 >= filled) {
            lines.map { line -> line.split('\t') }
        } else {
            lines.map { line -> listOf(line) }
        }
        val builder = DraftBuilder()
        builder.beginSheet()
        processRows(builder, rows) { index -> "Línea ${index + 1}" }
        builder.endSheet(PASTED_TEXT)
        return builder.finish(title)
    }

    // ---- Filas -------------------------------------------------------------------------------------------

    private fun processRows(builder: DraftBuilder, rows: List<List<String>>, where: (Int) -> String) {
        var columns: Columns? = null
        rows.forEachIndexed { index, row ->
            val cells = row.map { it.trim() }
            val filled = cells.filter { it.isNotEmpty() }
            if (filled.isEmpty()) return@forEachIndexed
            val context = RowContext(where(index), filled.joinToString(" · "), builder)
            val header = detectColumns(cells)
            val table = columns
            val line: Line? = when {
                header != null -> {
                    columns = header
                    null
                }
                filled.size == 1 -> classify(filled.first(), context)
                table != null -> tableRow(cells, table, context)
                else -> classify(filled.joinToString(" "), context)
            }
            val exercise = when (line) {
                is Line.Week -> {
                    builder.openWeek(line.number)
                    null
                }
                is Line.Day -> {
                    builder.startDay(line.name, line.silent, context)
                    null
                }
                is Line.Exercise -> builder.addExercise(line.exercise, context)
                null -> null
            }
            builder.endRow(exercise)
        }
    }

    /**
     * Una línea o una fila sin cabecera. Orden: notación (ejercicio), semana, prosa (se cuenta), etiqueta de día y, por
     * último, un texto corto que puede ser un día. Lo demás es un aviso «no se entiende».
     */
    private fun classify(raw: String, row: RowContext): Line? {
        val text = raw.trim()
        // Una cabecera es una lista corta de nombres de columna; una frase larga con esas palabras es prosa.
        if (text.isEmpty() || (isColumnHeaderText(text) && !isProse(text))) return null
        val notation = parseNotation(text)
        if (notation != null) {
            if (notation.name.isBlank()) {
                row.warn(ImportWarningKind.SKIPPED_ROW, "falta el nombre del ejercicio («$text»)")
                return null
            }
            return notationLine(notation, row)
        }
        weekNumber(text)?.let { return Line.Week(it) }
        if (isProse(text)) {
            row.count(Noise.TEXT)
            return null
        }
        if (isDayLabel(text)) return dayLine(text, silent = false)
        if (isShortDayCandidate(text)) return dayLine(text, silent = true)
        row.warn(ImportWarningKind.SKIPPED_ROW, "no se entiende «$text»")
        return null
    }

    private fun notationLine(notation: Notation, row: RowContext): Line? {
        if (countIfNotExercise(notation.name, row)) return null
        val notes = listOfNotNull(notation.notes, amrapNote(notation.amrapReps)).joinToString(" ").takeIf { it.isNotEmpty() }
        return validate(
            name = notation.name,
            sets = notation.sets,
            repsMin = notation.repsMin,
            repsMax = notation.repsMax,
            load = notation.load,
            percent = notation.percent,
            rpe = notation.rpe,
            rir = notation.rir,
            rest = notation.rest,
            notes = notes,
            row = row,
        )?.let { Line.Exercise(it) }
    }

    private fun dayLine(text: String, silent: Boolean): Line? {
        val name = cleanHeader(text)
        return if (name.isEmpty()) null else Line.Day(name, silent)
    }

    /** Fila de tabla con cabecera: cada celda según su columna; el nombre puede traer notación («Sentadilla 4x6»). */
    private fun tableRow(cells: List<String>, columns: Columns, row: RowContext): Line? {
        fun cell(role: ColumnRole): String = columns.indexOf(role)?.let { cells.getOrNull(it) }.orEmpty()

        val nameCell = cell(ColumnRole.NAME)
        val inline = if (nameCell.isEmpty()) null else parseNotation(nameCell)
        val name = dropLinks(inline?.name ?: nameCell)
        if (name.isBlank()) {
            row.warn(ImportWarningKind.SKIPPED_ROW, "fila sin nombre de ejercicio; se omite")
            return null
        }
        if (countIfNotExercise(name, row)) return null

        val setsCell = cell(ColumnRole.SETS)
        val repsCell = cell(ColumnRole.REPS)
        if (isAmrapWithoutNumber(repsCell)) {
            row.warn(ImportWarningKind.SKIPPED_ROW, "«$name»: AMRAP sin número de repeticiones; se omite")
            return null
        }
        val setsNotation = if (setsCell.isNotEmpty() && leadingInt(setsCell) == null) parseNotation(setsCell) else null
        // «1x5+», «3x8-12», «3 x 10» en la celda de repeticiones; si no hay notación, un número o un rango.
        val scheme = if (hasScheme(repsCell)) parseNotation(repsCell) else null
        val range = if (scheme == null) parseRange(repsCell) else null
        // Si la columna de series tiene un número, manda; si no, la notación de la celda de repeticiones o la de antes.
        val sets = leadingInt(setsCell) ?: setsNotation?.sets ?: scheme?.sets ?: inline?.sets
        val repsMin = range?.first ?: scheme?.repsMin ?: setsNotation?.repsMin ?: inline?.repsMin
        val repsMax = range?.second ?: scheme?.repsMax ?: setsNotation?.repsMax ?: inline?.repsMax
        val amrap = cellAmrap(repsCell) ?: scheme?.amrapReps ?: setsNotation?.amrapReps ?: inline?.amrapReps

        val loadCell = cell(ColumnRole.LOAD)
        val percentCell = cell(ColumnRole.PERCENT)
        val loadIsPercent = loadCell.contains('%')
        val load = if (loadIsPercent) null else firstNumber(loadCell)
        val percent = firstNumber(percentCell) ?: (if (loadIsPercent) firstNumber(loadCell) else null)
        val rpeCell = cell(ColumnRole.RPE)
        val rirCell = cell(ColumnRole.RIR)
        val restCell = cell(ColumnRole.REST)
        val notesCell = cell(ColumnRole.NOTES)
        val rpe = firstNumber(rpeCell)
        val rir = leadingInt(rirCell)
        val rest = parseRest(restCell, columns.restInMinutes)

        unreadable(row, loadCell, load ?: percent, "carga")
        unreadable(row, percentCell, firstNumber(percentCell), "porcentaje")
        unreadable(row, rpeCell, rpe, "RPE")
        unreadable(row, rirCell, rir, "RIR")
        unreadable(row, restCell, rest, "descanso")

        val remainder = if (range != null) rangeRemainder(repsCell) else null
        val notes = listOfNotNull(
            notesCell.takeIf { it.isNotEmpty() },
            remainder,
            inline?.notes,
            amrapNote(amrap),
        ).joinToString(" ").takeIf { it.isNotEmpty() }
        return validate(
            name = name,
            sets = sets,
            repsMin = repsMin,
            repsMax = repsMax,
            load = load ?: inline?.load,
            percent = percent ?: inline?.percent,
            rpe = rpe ?: inline?.rpe,
            rir = rir ?: inline?.rir,
            rest = rest ?: inline?.rest,
            notes = notes,
            row = row,
        )?.let { Line.Exercise(it) }
    }

    private fun unreadable(row: RowContext, cell: String, parsed: Any?, label: String) {
        if (cell.isNotEmpty() && parsed == null) {
            row.warn(ImportWarningKind.IGNORED_VALUE, "«$cell» no se entiende como $label; se ignora")
        }
    }

    /**
     * Valida un ejercicio: series y repeticiones son obligatorias; el resto, opcional y con rango. El id es provisional:
     * [DraftBuilder.finish] le asigna el definitivo.
     */
    private fun validate(
        name: String,
        sets: Int?,
        repsMin: Int?,
        repsMax: Int?,
        load: Double?,
        percent: Double?,
        rpe: Double?,
        rir: Int?,
        rest: Int?,
        notes: String?,
        row: RowContext,
    ): ImportedExercise? {
        if (sets == null) {
            row.warn(ImportWarningKind.SKIPPED_ROW, "«$name» sin series; se omite")
            return null
        }
        if (sets !in 1..MAX_SETS) {
            row.warn(ImportWarningKind.SKIPPED_ROW, "«$name» tiene $sets series, fuera de rango; se omite")
            return null
        }
        if (repsMin == null) {
            row.warn(ImportWarningKind.SKIPPED_ROW, "«$name» sin repeticiones; se omite")
            return null
        }
        val high = repsMax ?: repsMin
        val low = minOf(repsMin, high)
        val top = maxOf(repsMin, high)
        if (low < 1 || top > MAX_REPS) {
            row.warn(ImportWarningKind.SKIPPED_ROW, "«$name» tiene repeticiones fuera de rango; se omite")
            return null
        }
        return ImportedExercise(
            id = "",
            rawName = name,
            sets = sets,
            repsMin = low,
            repsMax = top,
            loadKg = checked(load, "la carga", name, row) { it in 0.5..MAX_LOAD_KG },
            percent1Rm = checked(percent, "el porcentaje", name, row) { it in 1.0..MAX_PERCENT },
            rpe = checked(rpe, "el RPE", name, row) { it in 1.0..10.0 },
            rir = checked(rir, "las RIR", name, row) { it in 0..10 },
            restSeconds = checked(rest, "el descanso", name, row) { it in 5..MAX_REST_SECONDS },
            notes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTE_CHARS),
            sourceText = row.source,
        )
    }

    private fun <T> checked(
        value: T?,
        label: String,
        name: String,
        row: RowContext,
        valid: (T) -> Boolean,
    ): T? {
        if (value == null) return null
        if (valid(value)) return value
        row.warn(ImportWarningKind.IGNORED_VALUE, "$label de «$name» está fuera de rango; se ignora")
        return null
    }

    // ---- Nombres que no son ejercicios ------------------------------------------------------------------

    /** Ruido que se cuenta por hoja en vez de avisarlo: líneas de texto, series de calentamiento o filas de etiquetas. */
    private enum class Noise { TEXT, WARMUP, LABEL }

    /** Calentamiento o etiqueta según el nombre; null si el nombre puede ser un ejercicio. */
    private fun nonExerciseKind(name: String): Noise? {
        val words = ExerciseMatchLexicon.normalize(name).split(' ').filter { it.isNotEmpty() }
        if (words.isNotEmpty() && isWarmupStart(words)) return Noise.WARMUP
        if (name.count { it.isLetter() } < 3) return Noise.LABEL
        if (words.isNotEmpty() && "sxr" in words && words.size <= 3) return Noise.LABEL
        // Las letras sueltas («A» de «Meso A») no cuentan; las cifras se quitan antes de comparar.
        val content = words.filter { it.length > 1 }.map { word -> word.filter { !it.isDigit() } }.filter { it.isNotEmpty() }
        return if (content.isNotEmpty() && content.all { it in LABEL_WORDS }) Noise.LABEL else null
    }

    private fun isWarmupStart(words: List<String>): Boolean {
        val first = words.first()
        return first == "warmup" || (first == "warm" && words.getOrNull(1) == "up") || first in WARMUP_STARTS
    }

    /** Cuenta el nombre si no es un ejercicio (calentamiento o etiqueta) y dice si lo era. */
    private fun countIfNotExercise(name: String, row: RowContext): Boolean {
        val noise = nonExerciseKind(name) ?: return false
        row.count(noise)
        return true
    }

    /** «AMRAP», «MR», «max reps», «máx.» o «al fallo» sin número: la fila no dice cuántas repeticiones son. */
    private fun isAmrapWithoutNumber(cell: String): Boolean {
        if (cell.isEmpty() || cell.any { it.isDigit() }) return false
        val text = ExerciseMatchLexicon.normalize(cell)
        return text in AMRAP_WORDS || text.contains("amrap")
    }

    private val WARMUP_STARTS = setOf("calentamiento", "aproximacion", "approach", "ramp")
    private val AMRAP_WORDS = setOf("mr", "max reps", "max", "al fallo")

    /** Palabras de una etiqueta de plantilla, sin cifras («e1rm» cuenta como «erm»). */
    private val LABEL_WORDS: Set<String> = setOf(
        "set", "sets", "serie", "series", "rep", "reps", "sxr", "week", "semana", "day", "dia", "total", "volume",
        "volumen", "tonnage", "tonelaje", "e1rm", "rm", "max", "tm", "training", "notes", "notas", "load", "weight",
        "peso", "carga", "status", "estado", "amrap", "rest", "descanso", "tempo", "rpe", "rir", "date", "fecha",
        "cycle", "ciclo", "block", "bloque", "meso", "phase", "fase", "scheme",
    ).mapTo(HashSet()) { word -> word.filter { !it.isDigit() } }

    // ---- Cabeceras de columna ----------------------------------------------------------------------------

    private enum class ColumnRole { NAME, SETS, REPS, LOAD, PERCENT, RPE, RIR, REST, NOTES }

    private class Columns(private val indexes: Map<ColumnRole, Int>, val restInMinutes: Boolean) {
        fun indexOf(role: ColumnRole): Int? = indexes[role]
    }

    /** Una fila es cabecera si trae la columna de ejercicio y al menos otra columna reconocida. */
    private fun detectColumns(cells: List<String>): Columns? {
        val indexes = HashMap<ColumnRole, Int>()
        cells.forEachIndexed { index, cell ->
            val role = columnRole(cell)
            if (role != null && !indexes.containsKey(role)) indexes[role] = index
        }
        if (!indexes.containsKey(ColumnRole.NAME) || indexes.size < 2) return null
        val restHeader = indexes[ColumnRole.REST]?.let { cells[it] }.orEmpty()
        return Columns(indexes, restInMinutes = isMinutesHeader(restHeader))
    }

    private fun columnRole(raw: String): ColumnRole? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val words = ExerciseMatchLexicon.normalize(text.replace(PARENTHESES, " "))
            .split(' ')
            .filter { it.isNotEmpty() }
            .toSet()
        return when {
            words.any { it in NAME_WORDS } -> ColumnRole.NAME
            "rpe" in words -> ColumnRole.RPE
            "rir" in words -> ColumnRole.RIR
            words.any { it in REST_WORDS } -> ColumnRole.REST
            text.contains('%') || words.any { it in PERCENT_WORDS } -> ColumnRole.PERCENT
            words.any { it in LOAD_WORDS } -> ColumnRole.LOAD
            words.any { it in SET_WORDS } -> ColumnRole.SETS
            words.any { it in REP_WORDS } -> ColumnRole.REPS
            words.any { it in NOTE_WORDS } -> ColumnRole.NOTES
            else -> null
        }
    }

    private fun isColumnHeaderText(text: String): Boolean {
        if (text.any { it.isDigit() }) return false
        return ExerciseMatchLexicon.normalize(text).split(' ').count { columnRole(it) != null } >= 2
    }

    private fun isMinutesHeader(header: String): Boolean =
        ExerciseMatchLexicon.normalize(header).split(' ').any { it == "min" || it == "minutos" || it == "minutes" }

    private val PARENTHESES = Regex("""\(.*?\)""")
    private val NAME_WORDS = setOf("ejercicio", "ejercicios", "exercise", "exercises", "movimiento", "movimientos", "movement")
    private val REST_WORDS = setOf("descanso", "descansos", "rest", "pausa")
    private val PERCENT_WORDS = setOf("porcentaje", "percent", "percentage", "1rm")
    private val LOAD_WORDS = setOf("peso", "pesos", "carga", "cargas", "kg", "kgs", "kilos", "load", "weight")
    private val SET_WORDS = setOf("series", "serie", "sets", "set")
    private val REP_WORDS = setOf("reps", "rep", "repeticiones", "repeticion")
    private val NOTE_WORDS = setOf("notas", "nota", "notes", "note", "observaciones", "comentarios")

    // ---- Notación compacta -------------------------------------------------------------------------------

    private class Notation(
        val name: String,
        val notes: String?,
        val sets: Int?,
        val repsMin: Int?,
        val repsMax: Int?,
        val load: Double?,
        val percent: Double?,
        val rpe: Double?,
        val rir: Int?,
        val rest: Int?,
        /** Repeticiones de la última serie con «+» («5+», «1x5+»): AMRAP. Null si no hay «+». */
        val amrapReps: Int?,
    )

    /**
     * Busca la notación en un texto. Cada patrón consume su tramo. Si hay texto antes del primer tramo, ese es el nombre
     * y lo que queda detrás, las notas; si no lo hay, el nombre es lo que queda detrás («5x5 Squat» → «Squat»).
     * Devuelve null si el texto no tiene ninguna notación.
     */
    private fun parseNotation(text: String): Notation? {
        val spans = mutableListOf<IntRange>()
        var sets: Int? = null
        var repsMin: Int? = null
        var repsMax: Int? = null
        var amrap: Int? = null
        var load: Double? = null
        var percent: Double? = null
        var rpe: Double? = null
        var rir: Int? = null
        var rest: Int? = null

        fun scan(pattern: Regex, onMatch: (MatchResult) -> Unit) {
            for (match in pattern.findAll(text)) {
                if (spans.any { overlaps(it, match.range) }) continue
                spans += match.range
                onMatch(match)
            }
        }

        scan(REST_MINUTES) { m -> if (rest == null) rest = minutesToSeconds(m.groupValues[1]) }
        scan(REST_SECONDS) { m -> if (rest == null) rest = m.groupValues[1].toIntOrNull() }
        scan(SETS_BY_REPS) { m ->
            if (sets == null) {
                sets = m.groupValues[1].toIntOrNull()
                repsMin = m.groupValues[2].toIntOrNull()
                repsMax = m.groupValues[4].toIntOrNull() ?: repsMin
                amrap = schemeAmrap(m)
            }
        }
        scan(SETS_OF_REPS) { m ->
            if (sets == null) {
                sets = m.groupValues[1].toIntOrNull()
                repsMin = m.groupValues[2].toIntOrNull()
                repsMax = m.groupValues[4].toIntOrNull() ?: repsMin
                amrap = schemeAmrap(m)
            }
        }
        scan(SETS_ONLY) { m -> if (sets == null) sets = m.groupValues[1].toIntOrNull() }
        scan(PERCENT) { m -> if (percent == null) percent = firstNumber(m.groupValues[1]) }
        scan(LOAD_KG) { m -> if (load == null) load = firstNumber(m.groupValues[1]) }
        scan(LOAD_AT) { m -> if (load == null) load = firstNumber(m.groupValues[1]) }
        scan(RPE) { m -> if (rpe == null) rpe = firstNumber(m.groupValues[1]) }
        scan(RIR) { m -> if (rir == null) rir = m.groupValues[1].ifEmpty { m.groupValues[2] }.toIntOrNull() }
        if (spans.isEmpty()) return null

        val first = spans.minOf { it.first }
        val before = StringBuilder()
        val behind = StringBuilder()
        text.forEachIndexed { index, char ->
            if (spans.any { index in it }) return@forEachIndexed
            if (index < first) before.append(char) else behind.append(char)
        }
        val beforeName = cleanName(before.toString())
        val name = beforeName.ifEmpty { cleanName(behind.toString()) }
        val notes = if (beforeName.isEmpty()) "" else cleanNotes(behind.toString())
        return Notation(
            name = name,
            notes = notes.takeIf { it.isNotEmpty() },
            sets = sets,
            repsMin = repsMin,
            repsMax = repsMax,
            load = load,
            percent = percent,
            rpe = rpe,
            rir = rir,
            rest = rest,
            amrapReps = amrap,
        )
    }

    private fun overlaps(a: IntRange, b: IntRange): Boolean = a.first <= b.last && b.first <= a.last

    private fun minutesToSeconds(value: String): Int? = firstNumber(value)?.let { (it * 60).roundToInt() }

    private fun cleanName(raw: String): String =
        dropLinks(raw).replace(NAME_TAIL, "").replace(NAME_HEAD, "").trim()

    /** Quita los enlaces de un texto: no son parte del nombre ni de la notación. */
    private fun dropLinks(text: String): String = text.replace(LINK, " ").trim()

    private fun cleanNotes(raw: String): String =
        raw.replace(EMPTY_PARENTHESES, " ")
            .replace(SPACES, " ")
            .trim { it.isWhitespace() || it in "-–—|·,;:" }

    /** Nota de la última serie con «+»; null si no hay AMRAP. */
    private fun amrapNote(reps: Int?): String? = reps?.let { "AMRAP: última serie, $it o más repeticiones." }

    /** Repeticiones de un tramo con «+»: «5+», «1x5+» o «10-15+» (el número que lleva el «+»). */
    private fun schemeAmrap(m: MatchResult): Int? = when {
        m.groupValues[5].isNotEmpty() -> m.groupValues[4].toIntOrNull()
        m.groupValues[3].isNotEmpty() -> m.groupValues[2].toIntOrNull()
        else -> null
    }

    /** Igual que [schemeAmrap], para una celda entera: el último número con «+» que no sigue otra cifra. */
    private fun cellAmrap(cell: String): Int? =
        AMRAP_NUMBER.findAll(cell).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()

    private fun hasScheme(cell: String): Boolean = SETS_BY_REPS.containsMatchIn(cell) || SETS_OF_REPS.containsMatchIn(cell)

    /** «4-6 / 8-10»: manda el primer rango; lo que queda con cifras pasa a las notas («8-10»). */
    private fun rangeRemainder(cell: String): String? {
        val match = RANGE_CELL.find(cell) ?: return null
        return cell.substring(match.range.last + 1)
            .trimStart(' ', '/', '|', ',', ';', ':', '-', '–', '—', '·')
            .trim()
            .takeIf { rest -> rest.any { it.isDigit() } }
    }

    private val REST_MINUTES = Regex(
        """(\d{1,2}(?:[.,]\d)?)\s*(?:'|’|´|min(?:uto)?s?\b)(?:\s*(?:de\s+)?(?:descanso|pausa)\b)?""",
        RegexOption.IGNORE_CASE,
    )
    private val REST_SECONDS = Regex(
        """(?<![\w,.])(\d{2,3})\s*(?:s|seg|segs|segundos?)\b(?:\s*(?:de\s+)?(?:descanso|pausa)\b)?""",
        RegexOption.IGNORE_CASE,
    )

    /** «4x8», «3×10», «3 x 10», «1x5+», «3x8-12», «3x8-12+»: grupos 1 series, 2 reps, 3 «+», 4 reps máx., 5 «+». */
    private val SETS_BY_REPS = Regex(
        """(?<![\w.,])(\d{1,2})\s*(?:x|×|\*|por)\s*(\d{1,3})(\+)?(?:\s*(?:-|–|—|a|hasta)\s*(\d{1,3})(\+)?)?""",
        RegexOption.IGNORE_CASE,
    )

    /** «4 series de 6-8»: los mismos grupos que [SETS_BY_REPS]. */
    private val SETS_OF_REPS = Regex(
        """(\d{1,2})\s*(?:series?|sets?)\s*(?:de|x|of)?\s*(\d{1,3})(\+)?(?:\s*(?:-|–|—|a|hasta)\s*(\d{1,3})(\+)?)?(?:\s*(?:reps?|repeticiones|rep)\b)?""",
        RegexOption.IGNORE_CASE,
    )
    private val SETS_ONLY = Regex("""(\d{1,2})\s*(?:series?|sets?)\b""", RegexOption.IGNORE_CASE)
    private val PERCENT = Regex("""@?\s*(\d{1,3}(?:[.,]\d+)?)\s*%""")
    private val LOAD_KG = Regex(
        """(?:@|\ba\b|\bcon\b)?\s*(\d{1,4}(?:[.,]\d+)?)\s*(?:kg|kgs|kilos?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val LOAD_AT = Regex("""@\s*(\d{1,4}(?:[.,]\d+)?)""")
    private val RPE = Regex("""\brpe\s*@?\s*(\d{1,2}(?:[.,]\d)?)""", RegexOption.IGNORE_CASE)
    private val RIR = Regex("""\brir\s*(\d{1,2})\b|\b(\d{1,2})\s*rir\b""", RegexOption.IGNORE_CASE)
    private val NAME_TAIL = Regex("""[\s\-–—|·,;:(\[]+$""")
    private val NAME_HEAD = Regex("""^[\s\-–—|·,;:)\]]+""")
    private val EMPTY_PARENTHESES = Regex("""\(\s*\)""")
    private val SPACES = Regex("""\s+""")
    private val LINK = Regex("""\s*https?://\S+\s*""", RegexOption.IGNORE_CASE)
    private val AMRAP_NUMBER = Regex("""(\d{1,3})\s*\+(?!\d)""")

    // ---- Celdas y cabeceras de tiempo y número -------------------------------------------------------------

    private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
    private val LEADING_INT = Regex("""^\s*(\d{1,3})\b""")
    private val RANGE_CELL = Regex("""^\s*(\d{1,3})\s*(?:-|–|—|a|hasta)\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""^\s*(\d{1,2}):([0-5]\d)\s*$""")

    private fun firstNumber(text: String): Double? =
        NUMBER.find(text)?.value?.replace(',', '.')?.toDoubleOrNull()

    private fun leadingInt(text: String): Int? = LEADING_INT.find(text)?.groupValues?.get(1)?.toIntOrNull()

    private fun parseRange(text: String): Pair<Int, Int>? {
        RANGE_CELL.find(text)?.let { m -> return m.groupValues[1].toInt() to m.groupValues[2].toInt() }
        return leadingInt(text)?.let { it to it }
    }

    /** Descanso en segundos: «2:30», «3 min», «2'», «90 s»; un número suelto usa la unidad de la cabecera. */
    private fun parseRest(cell: String, headerInMinutes: Boolean): Int? {
        if (cell.isEmpty()) return null
        CLOCK.find(cell)?.let { m -> return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt() }
        val value = firstNumber(cell) ?: return null
        val lower = cell.lowercase()
        val factor = when {
            lower.contains("min") || lower.contains('\'') || lower.contains('’') -> 60.0
            lower.contains('s') || lower.contains("seg") -> 1.0
            headerInMinutes -> 60.0
            else -> 1.0
        }
        return (value * factor).roundToInt()
    }

    // ---- Semanas, días, hojas y prosa ----------------------------------------------------------------------

    private val WEEK_NAME = Regex("""(?:semana|week|sem|s)\s?(\d{1,2})(?:\s.*)?""")

    /** «Día 1», «Dia 2 — Pierna», «Day 3», «D4» (texto normalizado), con o sin texto detrás. */
    private val DAY_NUMBERED = Regex("""(?:dia|day|d)\s?\d{1,2}(?:\s.*)?""")

    /** Un día de la semana, en inglés o en español, con o sin foco detrás («Mon Pull», «Lunes: Pierna»). */
    private val WEEKDAY_LABEL = Regex(
        """(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo|monday|tuesday|wednesday|thursday|friday|saturday|sunday""" +
            """|mon|tues|tue|wed|thurs|thur|thu|fri|sat|sun|lun|mar|mie|jue|vie|sab|dom)(?:\s.*)?""",
    )

    /** «Session 2», «Sesión 4», «Workout B», «Entreno A/B»: el número o la letra, y un texto de foco sin cifras. */
    private val SESSION_LABEL = Regex("""(?:session|sesion|workout|entreno)\s?(?:\d{1,2}|[a-c])(?:\s[^0-9]*)?""")
    private val GENERIC_SHEET = Regex("""(?:hoja|sheet|tabla)\s?\d*""")
    private val HEADER_TAIL = Regex("""[\s:.\-–—|]+$""")
    private val WHITESPACE = Regex("""\s+""")

    /** Focos de día, solos o combinados con «&», «/», «y», «+» o «and» (en el texto normalizado, sin signos). */
    private val FOCUS_WORDS = setOf(
        "upper", "lower", "push", "pull", "legs", "leg", "full", "body", "fullbody", "torso", "pierna", "piernas",
        "empuje", "tiron", "arms", "brazos", "chest", "pecho", "back", "espalda", "shoulders", "hombros", "glutes",
        "gluteos", "butt", "calves", "gemelos", "core", "abs", "abdomen",
    )
    private val FOCUS_CONNECTORS = setOf("y", "and")

    /** Hojas de instrucciones o de cálculo: su nombre contiene una de estas palabras, o es exactamente una de estas. */
    private val OMITTED_SHEET_WORDS = listOf(
        "readme", "read me", "leeme", "instructions", "instrucciones", "instruction", "faq", "setup", "configuracion",
        "calculator", "calculadora", "calculadoras", "calcs", "glossary", "glosario", "changelog", "tutorial",
    )
    private val OMITTED_SHEET_NAMES = setOf(
        "inputs", "input", "maxes", "max", "1rm", "1rm input", "notes", "notas", "info", "log", "registro",
        "summary", "resumen", "stats", "dashboard", "history", "historial",
    )

    /** Número de semana si el texto empieza por «Semana 2», «Week 2», «Sem 2» o «S2». */
    private fun weekNumber(text: String): Int? {
        val digits = WEEK_NAME.matchEntire(ExerciseMatchLexicon.normalize(text))?.groupValues?.get(1) ?: return null
        return digits.toIntOrNull()?.coerceAtLeast(1)
    }

    private fun isInstructionOrCalcSheet(name: String): Boolean {
        val text = ExerciseMatchLexicon.normalize(name)
        return OMITTED_SHEET_WORDS.any { text.contains(it) } || text in OMITTED_SHEET_NAMES
    }

    /**
     * Una etiqueta de día: «Día 1», «Day 3», «D4», «Session 2», «Workout B», «Entreno A/B», un día de la semana con o sin
     * foco, o solo focos («Pull», «Push & Butt & Calves»).
     */
    private fun isDayLabel(text: String): Boolean {
        val n = ExerciseMatchLexicon.normalize(text)
        if (n.isEmpty()) return false
        return DAY_NUMBERED.matches(n) || WEEKDAY_LABEL.matches(n) || SESSION_LABEL.matches(n) || isFocusLabel(n)
    }

    private fun isFocusLabel(normalized: String): Boolean {
        val tokens = normalized.split(' ')
        return tokens.first() in FOCUS_WORDS && tokens.last() in FOCUS_WORDS &&
            tokens.all { it in FOCUS_WORDS || it in FOCUS_CONNECTORS }
    }

    /**
     * Un texto corto que no es prosa: como mucho 5 palabras y 40 caracteres, sin cifras salvo un número final («Deadlift
     * (Day 2)»). Puede ser un día; si sale sin ejercicios, no avisa.
     */
    private fun isShortDayCandidate(text: String): Boolean {
        val words = text.split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 5 || text.length > 40) return false
        val digits = text.count { it.isDigit() }
        if (digits == 0) return true
        val last = words.last().trimEnd(')', ']', '.', ':', ',')
        return last.isNotEmpty() && last.all { it.isDigit() } && last.length == digits
    }

    /**
     * Prosa: una fila sin notación con más de 8 palabras o más de 60 caracteres, con un enlace, o que acaba en punto
     * tras 5 palabras o más. La prosa no es día ni aviso suelto: se cuenta por hoja.
     */
    private fun isProse(text: String): Boolean {
        val words = text.split(WHITESPACE).count { it.isNotEmpty() }
        return words > 8 || text.length > 60 || text.contains("http", ignoreCase = true) ||
            (text.trimEnd().endsWith(".") && words >= 5)
    }

    /** El nombre de una hoja sirve de día salvo que sea genérico («Hoja1», «Sheet 2»). */
    private fun usableSheetName(name: String): String? {
        val clean = cleanHeader(name)
        if (clean.isEmpty() || GENERIC_SHEET.matches(ExerciseMatchLexicon.normalize(clean))) return null
        return clean
    }

    private fun cleanHeader(text: String): String = text.trim().replace(HEADER_TAIL, "").trim()

    // ---- Estado del análisis --------------------------------------------------------------------------------

    private sealed interface Line {
        data class Week(val number: Int) : Line

        /** Día en curso; [silent] si es solo un texto corto que puede no ser día (si sale vacío, no avisa). */
        data class Day(val name: String, val silent: Boolean) : Line
        data class Exercise(val exercise: ImportedExercise) : Line
    }

    /** Fila o línea en curso: dónde está y su texto original, para los avisos y el ejercicio que salga de ella. */
    private class RowContext(val where: String, val source: String, private val builder: DraftBuilder) {
        fun warn(kind: ImportWarningKind, detail: String) = builder.warn(kind, where, source, "$where: $detail")

        fun count(noise: Noise) = builder.count(noise)
    }

    /** Día en curso. Su [id] se fija al cerrar el borrador, y solo si el día conserva ejercicios. */
    private class DayDraft(
        val week: Int,
        val name: String,
        val location: String,
        val sourceText: String,
        val silent: Boolean,
    ) {
        val exercises = mutableListOf<ExerciseDraft>()
        var id: String? = null
    }

    /** Ejercicio conservado; su [id] se fija al cerrar el borrador. */
    private class ExerciseDraft(val day: DayDraft, val exercise: ImportedExercise) {
        var id: String? = null
    }

    /**
     * Aviso en curso. Día y ejercicio se resuelven al cerrar el borrador, cuando ya se sabe qué se conserva.
     * [exercise] solo se fija en los avisos de valores descartados de un ejercicio que sí se conservó.
     */
    private class PendingWarning(
        val kind: ImportWarningKind,
        val location: String,
        val sourceText: String?,
        val message: String,
        var weekIndex: Int?,
        var day: DayDraft?,
        var exercise: ExerciseDraft?,
    )

    /** Acumula semanas, días y avisos mientras se recorren las filas. */
    private class DraftBuilder {
        private val warnings = mutableListOf<PendingWarning>()
        private val rowWarnings = mutableListOf<PendingWarning>()
        private val days = mutableListOf<DayDraft>()
        private val noise = mutableMapOf<Noise, Int>()
        private var week = 1
        private var openDay: DayDraft? = null
        private var sheetFirstDay = 0
        var pendingDayName: String? = null

        /** Aviso de la fila en curso: lleva la semana y el día que estaban abiertos al leerla. */
        fun warn(kind: ImportWarningKind, where: String, sourceText: String, message: String) {
            val pending = PendingWarning(kind, where, sourceText, message, weekIndex = week, day = openDay, exercise = null)
            warnings += pending
            rowWarnings += pending
        }

        fun count(kind: Noise) {
            noise[kind] = (noise[kind] ?: 0) + 1
        }

        fun openWeek(number: Int) {
            week = number
            openDay = null
        }

        /** Empieza una hoja (o el texto pegado): el día abierto de la hoja anterior no recoge sus filas. */
        fun beginSheet() {
            openDay = null
            sheetFirstDay = days.size
            noise.clear()
        }

        /** Cierra una hoja: los días cortos sin ejercicios cuentan como texto y sale una nota por tipo de ruido. */
        fun endSheet(location: String) {
            repeat(days.drop(sheetFirstDay).count { it.silent && it.exercises.isEmpty() }) { count(Noise.TEXT) }
            for (kind in Noise.entries) {
                val total = noise[kind] ?: continue
                addNote(location, noiseMessage(location, kind, total))
            }
        }

        fun noteOmittedSheet(name: String) {
            addNote("Hoja «$name»", "Se omitió la hoja «$name»: parece de instrucciones o de cálculo.")
        }

        private fun addNote(location: String, message: String) {
            warnings += PendingWarning(
                kind = ImportWarningKind.SHEET_NOTE,
                location = location,
                sourceText = null,
                message = message,
                weekIndex = null,
                day = null,
                exercise = null,
            )
        }

        private fun noiseMessage(location: String, kind: Noise, total: Int): String {
            val phrase = when (kind) {
                Noise.TEXT -> if (total == 1) "se omitió 1 línea de texto." else "se omitieron $total líneas de texto."
                Noise.WARMUP -> if (total == 1) "se omitió 1 serie de calentamiento." else "se omitieron $total series de calentamiento."
                Noise.LABEL -> if (total == 1) "se omitió 1 fila de cálculo o de etiquetas." else "se omitieron $total filas de cálculo o de etiquetas."
            }
            return "$location: $phrase"
        }

        fun startDay(name: String, silent: Boolean, row: RowContext) {
            pendingDayName = null
            openDay = DayDraft(week, name, row.where, row.source, silent).also { days += it }
        }

        fun addExercise(exercise: ImportedExercise, row: RowContext): ExerciseDraft {
            val day = openDay ?: DayDraft(week, takeDefaultDayName(), row.where, row.source, silent = false).also { days += it }
            openDay = day
            return ExerciseDraft(day, exercise).also { day.exercises += it }
        }

        /** Cierra la fila: los valores descartados pasan al ejercicio que se conservó, si lo hubo. */
        fun endRow(exercise: ExerciseDraft?) {
            if (exercise != null) {
                rowWarnings
                    .filter { it.kind == ImportWarningKind.IGNORED_VALUE }
                    .forEach { pending ->
                        pending.exercise = exercise
                        pending.day = exercise.day
                        pending.weekIndex = exercise.day.week
                    }
            }
            rowWarnings.clear()
        }

        fun finish(title: String): ImportedProgramDraft {
            val kept = days.filter { day ->
                val hasExercises = day.exercises.isNotEmpty()
                if (!hasExercises && !day.silent) {
                    warnings += PendingWarning(
                        kind = ImportWarningKind.EMPTY_DAY,
                        location = day.location,
                        sourceText = day.sourceText,
                        message = "«${day.name}» no tiene ejercicios; se omite",
                        weekIndex = day.week,
                        day = null,
                        exercise = null,
                    )
                }
                hasExercises
            }
            val weeks = kept.groupBy { it.week }.toSortedMap().map { (number, dayList) ->
                ImportedWeek(
                    index = number,
                    days = dayList.mapIndexed { dayPosition, day ->
                        val dayId = "w$number-d${dayPosition + 1}"
                        day.id = dayId
                        ImportedDay(
                            id = dayId,
                            name = day.name,
                            exercises = day.exercises.mapIndexed { exercisePosition, draft ->
                                val exerciseId = "$dayId-e${exercisePosition + 1}"
                                draft.id = exerciseId
                                draft.exercise.copy(id = exerciseId)
                            },
                        )
                    },
                )
            }
            if (weeks.isEmpty()) {
                warnings += PendingWarning(
                    kind = ImportWarningKind.NOTHING_FOUND,
                    location = "",
                    sourceText = null,
                    message = "No se ha encontrado ningún ejercicio con series y repeticiones",
                    weekIndex = null,
                    day = null,
                    exercise = null,
                )
            }
            return ImportedProgramDraft(
                title = title.trim(),
                weeks = weeks,
                warnings = warnings.mapIndexed { index, pending ->
                    ImportWarning(
                        id = "a${index + 1}",
                        kind = pending.kind,
                        location = pending.location,
                        sourceText = pending.sourceText,
                        message = pending.message,
                        weekIndex = pending.weekIndex,
                        dayId = pending.day?.id,
                        exerciseId = pending.exercise?.id,
                    )
                },
            )
        }

        private fun takeDefaultDayName(): String {
            val name = pendingDayName ?: "Día ${days.count { it.week == week } + 1}"
            pendingDayName = null
            return name
        }
    }
}
