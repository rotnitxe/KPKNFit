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

    internal const val MAX_SETS = 20
    internal const val MAX_REPS = 100
    internal const val MAX_LOAD_KG = 1000.0
    /** Rango de la carga en libras: de 1 a 2200 lb (unos 0,5–1000 kg). Lo mismo que el panel de edición. */
    internal const val MIN_LOAD_LB = 1.0
    internal const val MAX_LOAD_LB = 2200.0
    internal const val MIN_LOAD_KG = 0.5
    internal const val MAX_PERCENT = 100.0
    internal const val MAX_REST_SECONDS = 1800
    internal const val MAX_NOTE_CHARS = 200

    /** Origen de un texto pegado en los avisos de nota de hoja (un texto no tiene hojas). */
    private const val PASTED_TEXT = "Texto pegado"

    /** Convierte hojas (una por día, una por semana o una sola con encabezados) en un borrador. */
    fun parseSheets(sheets: List<ImportSheet>, title: String): ImportedProgramDraft {
        val builder = DraftBuilder()
        for (sheet in sheets) {
            // Lo que el lector recortó de la hoja (columnas, filas o el total de hojas) va como aviso de hoja.
            for (note in sheet.notes) builder.noteReaderTrim(sheet.name, note)
            if (isInstructionOrCalcSheet(sheet.name)) {
                builder.noteOmittedSheet(sheet.name)
                continue
            }
            val weekInName = weekNumber(sheet.name)
            if (weekInName != null) builder.openWeek(weekInName)
            builder.beginSheet()
            // Una hoja sin nombre de semana es un día: su nombre sirve si no trae un encabezado propio.
            builder.pendingDayName = if (weekInName == null) usableSheetName(sheet.name) else null
            builder.sheetLift = titleLift(sheet.rows) ?: builder.pendingDayName
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
        var grid: Grid? = null
        val sessions = planSessions(rows)
        rows.forEachIndexed { index, row ->
            val cells = row.map { it.trim() }
            val filled = cells.filter { it.isNotEmpty() }
            if (filled.isEmpty()) return@forEachIndexed
            val context = RowContext(where(index), filled.joinToString(" · "), builder)

            // Sesiones en columnas (subcabecera, sus etiquetas de encima y sus datos): van por su propio camino.
            val part = sessions[index]
            if (part != null) {
                grid?.let { closeGrid(builder, it) }
                grid = null
                when (part) {
                    SessionPart.Header -> {
                        columns = null
                        cells.filter { groupColOf(it) == GroupCol.LOAD }.forEach { builder.noteLoadHeader(it) }
                    }
                    SessionPart.Labels -> if (filled.any { isProse(it) }) builder.count(Noise.TEXT)
                    is SessionPart.Data -> sessionRow(builder, part.block, cells, context)
                }
                builder.endRow(null)
                return@forEachIndexed
            }

            // Una fila con tres o más días puede ser la cabecera de una cuadrícula: cada columna con día es un día.
            // Solo abre cuadrícula si más abajo hay filas de series o de peso; si no, es una fila normal.
            val gridDays = gridDayColumns(cells)
            if (gridDays.size >= GRID_MIN_DAYS) {
                grid?.let { closeGrid(builder, it) }
                grid = null
                if (gridConfirmed(rows, index, gridDays.first().first)) {
                    grid = Grid(builder.openGridDays(gridDays, context))
                    columns = null
                    builder.endRow(null)
                    return@forEachIndexed
                }
            }
            val activeGrid = grid
            if (activeGrid != null) {
                if (gridRow(builder, activeGrid, cells, context)) {
                    builder.endRow(null)
                    return@forEachIndexed
                }
                closeGrid(builder, activeGrid)
                grid = null
            }

            val header = detectColumns(cells)
            val table = columns
            // La celda de día de una tabla abre un día (o sigue el abierto); la fila sigue si trae más datos.
            val tableDay = if (table != null && header == null) {
                cells.getOrNull(table.indexOf(ColumnRole.DAY) ?: -1)?.takeIf { isTableDayCandidate(it) }
            } else {
                null
            }
            tableDay?.let { builder.openTableDay(cleanHeader(it), context) }
            val line: Line? = when {
                header != null -> {
                    columns = header
                    header.loadHeader?.let { builder.noteLoadHeader(it) }
                    null
                }
                tableDay != null && (filled.size == 1 || (table != null && isBareTableRow(cells, table))) -> null
                filled.size == 1 -> classify(filled.first(), context)
                table != null -> tableRow(cells, table, context)
                else -> classify(filled.joinToString(" "), context)
            }
            when (line) {
                is Line.Week -> {
                    builder.openWeek(line.number)
                    builder.endRow(null)
                }
                is Line.Day -> {
                    builder.startDay(line.name, line.silent, context)
                    builder.endRow(null)
                }
                is Line.Exercise -> builder.endRow(builder.addExercise(line.exercise, context))
                is Line.Groups -> line.groups.forEach { addGroup(builder, it, context) }
                null -> builder.endRow(null)
            }
        }
        grid?.let { closeGrid(builder, it) }
    }

    /**
     * Una línea o una fila sin cabecera. Orden: notación (ejercicio), semana, prosa (se cuenta), etiqueta de día y, por
     * último, un texto corto que puede ser un día. Lo demás es un aviso «no se entiende».
     */
    private fun classify(raw: String, row: RowContext): Line? {
        val text = raw.trim()
        // Una cabecera es una lista corta de nombres de columna; una frase larga con esas palabras es prosa.
        if (text.isEmpty() || (isColumnHeaderText(text) && !isProse(text))) return null
        // Varios grupos con porcentaje («50%x5, 60%x4»): un ejercicio por grupo. Antes que la notación normal.
        percentGroups(text)?.let { return groupLine(text, it, row) }
        val notation = parseNotation(text)
        if (notation != null) {
            // Prosa con notación: lo que hay antes de la notación es una frase, no un nombre.
            if (isLongLeadingText(notation.leading)) {
                row.count(Noise.TEXT)
                return null
            }
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
        val named = numbered(notation.name)
        if (countIfNotExercise(named.name, row)) return null
        if (notation.load != null) row.noteLoad(notation.loadUnit)
        val notes = listOfNotNull(blockNote(named.block), notation.notes, amrapNote(notation.amrapReps))
            .joinToString(" ").takeIf { it.isNotEmpty() }
        return validate(
            name = named.name,
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

        // Fila de día dentro de la tabla («Día 1: | Pecho», «Miércoles | Descanso»): abre el día y no es un ejercicio.
        tableDayName(cells, columns)?.let { return Line.Day(it, silent = false) }
        val nameCell = cell(ColumnRole.NAME)
        val inline = if (nameCell.isEmpty()) null else parseNotation(nameCell)
        val named = numbered(dropLinks(inline?.name ?: nameCell))
        val name = named.name
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
        // Sin otra nota, la columna de grupo muscular va a notas.
        val groupCell = cell(ColumnRole.GROUP)
        val notes = listOfNotNull(
            blockNote(named.block),
            notesCell.takeIf { it.isNotEmpty() },
            remainder,
            inline?.notes,
            amrapNote(amrap),
        ).joinToString(" ").takeIf { it.isNotEmpty() } ?: groupCell.takeIf { it.isNotEmpty() }
        // Carga con número: cuenta para saber si el archivo va en libras (manda la celda; si no, la notación).
        if (load != null) {
            row.noteLoad(unitMarkOf(loadCell))
        } else if (inline != null && inline.load != null) {
            row.noteLoad(inline.loadUnit)
        }
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
            loadKg = loadValue(load, name, row),
            percent1Rm = checked(percent, "el porcentaje", name, row) { it in 1.0..MAX_PERCENT },
            rpe = checked(rpe, "el RPE", name, row) { it in 1.0..10.0 },
            rir = checked(rir, "las RIR", name, row) { it in 0..10 },
            restSeconds = checked(rest, "el descanso", name, row) { it in 5..MAX_REST_SECONDS },
            notes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTE_CHARS),
            sourceText = row.source,
        )
    }

    /**
     * Carga de un ejercicio en la unidad del archivo. Fuera de 0,5–2200 se descarta ya. Entre 0,5 y 1, o entre 1000 y 2200,
     * solo vale en una unidad: el aviso queda pendiente y se decide al cerrar el borrador, con la unidad del archivo.
     */
    private fun loadValue(load: Double?, name: String, row: RowContext): Double? {
        // «0», «0lb» o «0kg» es peso corporal: sin carga y sin aviso.
        if (load == null || load == 0.0) return null
        val message = "la carga de «$name» está fuera de rango; se ignora"
        if (load < MIN_LOAD_KG || load > MAX_LOAD_LB) {
            row.warn(ImportWarningKind.IGNORED_VALUE, message)
            return null
        }
        if (!loadInRange(load, ImportLoadUnit.KG) || !loadInRange(load, ImportLoadUnit.LB)) {
            row.warnUnitLoad(load, message)
        }
        return load
    }

    /** Si una carga cabe en el rango de su unidad: de 0,5 a 1000 kg, o de 1 a 2200 lb. */
    internal fun loadInRange(value: Double, unit: ImportLoadUnit): Boolean = when (unit) {
        ImportLoadUnit.KG -> value in MIN_LOAD_KG..MAX_LOAD_KG
        ImportLoadUnit.LB -> value in MIN_LOAD_LB..MAX_LOAD_LB
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
        // Una abreviatura conocida («DL», «BP», «OHP») es un ejercicio aunque tenga menos de 3 letras.
        if (name.count { it.isLetter() } < 3 && ImportExerciseAliases.lookup(name) == null) return Noise.LABEL
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

    private enum class ColumnRole { NAME, SETS, REPS, LOAD, PERCENT, RPE, RIR, REST, NOTES, DAY, GROUP }

    /** Columnas de una tabla; [loadHeader] es la cabecera de carga, si la hay («Peso (kg)», «Weight (lb)»). */
    private class Columns(
        private val indexes: Map<ColumnRole, Int>,
        val restInMinutes: Boolean,
        val loadHeader: String?,
    ) {
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
        val loadHeader = indexes[ColumnRole.LOAD]?.let { cells[it] }
        return Columns(indexes, restInMinutes = isMinutesHeader(restHeader), loadHeader = loadHeader)
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
            words.any { it in DAY_WORDS } -> ColumnRole.DAY
            words.any { it in GROUP_WORDS } -> ColumnRole.GROUP
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
    private val LOAD_WORDS = setOf(
        "peso", "pesos", "carga", "cargas", "kg", "kgs", "kilos", "load", "weight", "lb", "lbs", "libra", "libras", "pounds",
    )
    private val SET_WORDS = setOf("series", "serie", "sets", "set")
    /** «SxR» (series por repeticiones) es la columna de la notación «5x3+», como «Reps». */
    private val REP_WORDS = setOf("reps", "rep", "repeticiones", "repeticion", "sxr")
    private val NOTE_WORDS = setOf("notas", "nota", "notes", "note", "observaciones", "comentarios")

    /** Columna de día: cada celda con texto abre un día; una vacía sigue el día abierto. */
    private val DAY_WORDS = setOf("dia", "day", "sesion", "session", "jornada")

    /** Columna de grupo muscular: no es nombre; va a notas solo si la fila no tiene otra nota. */
    private val GROUP_WORDS = setOf("grupo", "muscle", "group")

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
        /** Texto antes del primer tramo de notación, sin limpiar (para la prosa con notación). */
        val leading: String,
        /** Unidad que lleva la carga («135 lb», «60kg»); null si la carga no lleva unidad. */
        val loadUnit: ImportLoadUnit?,
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
        var loadUnit: ImportLoadUnit? = null

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
        scan(LOAD_KG) { m ->
            if (load == null) {
                load = firstNumber(m.groupValues[1])
                loadUnit = unitOfToken(m.groupValues[2])
            }
        }
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
            leading = before.toString(),
            loadUnit = loadUnit,
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
        """(?<![\w.,])(\d{1,2})\s*(?:x|×|\*|por)\s*(\d{1,3})(?!\d|\s*%)(\+)?(?:\s*(?:-|–|—|a|hasta)\s*(\d{1,3})(\+)?)?""",
        RegexOption.IGNORE_CASE,
    )

    /** «4 series de 6-8»: los mismos grupos que [SETS_BY_REPS]. */
    private val SETS_OF_REPS = Regex(
        """(\d{1,2})\s*(?:series?|sets?)\s*(?:de|x|of)?\s*(\d{1,3})(?!\d|\s*%)(\+)?(?:\s*(?:-|–|—|a|hasta)\s*(\d{1,3})(\+)?)?(?:\s*(?:reps?|repeticiones|rep)\b)?""",
        RegexOption.IGNORE_CASE,
    )
    private val SETS_ONLY = Regex("""(\d{1,2})\s*(?:series?|sets?)\b""", RegexOption.IGNORE_CASE)
    private val PERCENT = Regex("""@?\s*(\d{1,3}(?:[.,]\d+)?)\s*%""")
    private val LOAD_KG = Regex(
        """(?:@|\ba\b|\bcon\b)?\s*(\d{1,4}(?:[.,]\d+)?)\s*(kgs?|kilos?|lbs?|pounds?|libras?)\b""",
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

    /** Un día de la semana, en inglés o en español, con o sin foco detrás pero sin cifras («Mon Pull», «Lunes: Pierna»). */
    private val WEEKDAY_LABEL = Regex(
        """(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo|monday|tuesday|wednesday|thursday|friday|saturday|sunday""" +
            """|mon|tues|tue|wed|thurs|thur|thu|fri|sat|sun|lun|mar|mie|jue|vie|sab|dom)(?:\s[^0-9]*)?""",
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

    /** Levantamiento del título de la hoja («Press – 5/3/1 BBB» → «Press»); null si no hay título o es de un ciclo. */
    private fun titleLift(rows: List<List<String>>): String? {
        val first = rows.map { row -> row.map { it.trim() }.filter { it.isNotEmpty() } }.firstOrNull { it.isNotEmpty() }
        if (first == null || first.size != 1) return null
        val head = first.first().split(TITLE_SEPARATOR).first().trim()
        return head.takeIf { head.count { it.isLetter() } >= 3 && cycleNumber(head) == null }
    }

    private val TITLE_SEPARATOR = Regex("""\s+[-–—]\s+|:""")

    /** Número de ciclo de una etiqueta («Cycle 2 – 5/3/1», «Ciclo 1»); null si no es una etiqueta de ciclo. */
    private fun cycleNumber(label: String): Int? =
        CYCLE_LABEL.find(ExerciseMatchLexicon.normalize(label))?.groupValues?.get(1)?.toIntOrNull()

    private val CYCLE_LABEL = Regex("""^(?:cycle|ciclo) (\d{1,2})\b""")

    private fun cleanHeader(text: String): String = text.trim().replace(HEADER_TAIL, "").trim()

    // ---- Cuadrículas: días en columnas ------------------------------------------------------------------------

    /** Una cuadrícula empieza con una fila de tres o más columnas con día. */
    private const val GRID_MIN_DAYS = 3

    /** Hasta cuántas filas más abajo se busca la etiqueta que confirma una cuadrícula. */
    private const val GRID_LOOKAHEAD_ROWS = 30

    private val GRID_EXERCISE_WORDS = setOf("ejercicio", "ejercicios", "exercise", "exercises")
    private val GRID_SETS_KEYS = setOf("series", "serie", "sets", "set")
    private val GRID_REPS_KEYS = setOf("reps", "rep", "repeticiones", "repeticion")
    private val GRID_SCHEME_KEYS = GRID_SETS_KEYS + GRID_REPS_KEYS + "sxr"
    private val GRID_SCHEME_WORDS = GRID_SCHEME_KEYS + "x"
    private val GRID_LOAD_WORDS = setOf(
        "peso", "pesos", "carga", "cargas", "weight", "load", "kg", "kgs", "kilos", "lb", "lbs", "pounds", "libras",
    )
    private val GRID_REST_KEYS = setOf("descanso", "descansos", "rest", "pausa")
    private val GRID_REST_WORDS = GRID_REST_KEYS + setOf("min", "minutos", "minutes", "seg", "segundos", "s")
    private val GRID_NOTES_WORDS = setOf("notas", "nota", "notes", "note", "observaciones", "comentarios")

    /** Clase de una fila de cuadrícula, según su etiqueta (la primera celda con texto antes de la primera columna de día). */
    private enum class GridKind { EXERCISE, SCHEME, LOAD, REST, NOTES }

    /** Cuadrícula abierta: sus columnas con día y los ejercicios que van llegando (se validan al cerrarla). */
    private class Grid(val days: List<Pair<Int, DayDraft>>) {
        val entries = mutableListOf<GridEntry>()

        val firstDayColumn: Int
            get() = days.first().first

        fun lastEntryOf(day: DayDraft): GridEntry? = entries.lastOrNull { it.day === day }
    }

    /** Ejercicio de una cuadrícula, con los valores que le llegan de sus filas. */
    private class GridEntry(val day: DayDraft, val where: String, val name: String) {
        /** Celdas del bloque, para el texto original. */
        val cells = mutableListOf<String>()
        val notes = mutableListOf<String>()

        /** Valores que no se entendieron: qué son y el texto de la celda. */
        val unreadable = mutableListOf<Pair<String, String>>()
        var sets: Int? = null
        var repsMin: Int? = null
        var repsMax: Int? = null
        var amrap: Int? = null
        var load: Double? = null
        var percent: Double? = null
        var rpe: Double? = null
        var rir: Int? = null
        var rest: Int? = null
    }

    /** Columnas de una fila con un día (Lunes, Lun, Monday, Día 1…), con el nombre de su cabecera. */
    private fun gridDayColumns(cells: List<String>): List<Pair<Int, String>> =
        cells.mapIndexedNotNull { index, cell ->
            val text = ExerciseMatchLexicon.normalize(cell)
            if (text.isNotEmpty() && (DAY_NUMBERED.matches(text) || WEEKDAY_LABEL.matches(text))) {
                index to cleanHeader(cell)
            } else {
                null
            }
        }

    /**
     * Una cabecera de días abre cuadrícula solo si, unas filas más abajo, hay una etiqueta de series («Series x Reps») o de
     * peso en su columna, antes de la primera columna de día. Otra cabecera de días antes decide por sí misma.
     */
    private fun gridConfirmed(rows: List<List<String>>, headerIndex: Int, firstDayColumn: Int): Boolean {
        val end = minOf(rows.size, headerIndex + GRID_LOOKAHEAD_ROWS)
        for (j in headerIndex + 1 until end) {
            val cells = rows[j].map { it.trim() }
            if (gridDayColumns(cells).size >= GRID_MIN_DAYS) return false
            val label = cells.indices.firstOrNull { it < firstDayColumn && cells[it].isNotEmpty() }?.let { cells[it] } ?: continue
            val kind = gridLabelKind(label)
            if (kind == GridKind.SCHEME || kind == GridKind.LOAD) return true
        }
        return false
    }

    /** Clase de una etiqueta de cuadrícula; la etiqueta vacía es un ejercicio. Null si la etiqueta no es ninguna conocida. */
    private fun gridLabelKind(label: String): GridKind? {
        val words = ExerciseMatchLexicon.normalize(label.replace(PARENTHESES, " "))
            .split(' ')
            .filter { word -> word.any { it.isLetter() } }
        if (words.isEmpty()) return if (label.isBlank()) GridKind.EXERCISE else null
        return when {
            words.all { it in GRID_EXERCISE_WORDS } -> GridKind.EXERCISE
            words.all { it in GRID_SCHEME_WORDS } && words.any { it in GRID_SCHEME_KEYS } -> GridKind.SCHEME
            words.all { it in GRID_LOAD_WORDS } -> GridKind.LOAD
            words.all { it in GRID_REST_WORDS } && words.any { it in GRID_REST_KEYS } -> GridKind.REST
            words.all { it in GRID_NOTES_WORDS } -> GridKind.NOTES
            else -> null
        }
    }

    /**
     * Una fila dentro de la cuadrícula abierta. Devuelve false si no es de la cuadrícula: entonces se cierra y la fila se
     * lee como cualquier otra (cabecera de tabla, semana o texto).
     */
    private fun gridRow(builder: DraftBuilder, grid: Grid, cells: List<String>, row: RowContext): Boolean {
        val labelIndex = cells.indices.firstOrNull { it < grid.firstDayColumn && cells[it].isNotEmpty() }
        val label = labelIndex?.let { cells[it] }.orEmpty()
        // Una etiqueta que no es de la cuadrícula (semana, cabecera, nombre de ejercicio sin etiqueta…) la cierra:
        // esa fila se lee como cualquier otra, para no perder ejercicios en silencio.
        val kind = gridLabelKind(label) ?: return false
        if (kind == GridKind.LOAD) builder.noteLoadHeader(label)
        for ((column, day) in grid.days) {
            val cell = cells.getOrNull(column).orEmpty()
            if (cell.isEmpty()) continue
            when (kind) {
                GridKind.EXERCISE -> addGridEntry(builder, grid, day, cell, row)
                GridKind.SCHEME -> applyGridScheme(builder, grid, day, label, cell, row)
                GridKind.LOAD -> applyGridLoad(builder, grid, day, cell, row)
                GridKind.REST -> applyGridRest(builder, grid, day, label, cell, row)
                GridKind.NOTES -> applyGridNotes(builder, grid, day, cell, row)
            }
        }
        return true
    }

    /** Ejercicio nuevo de un día de la cuadrícula; la celda puede traer notación («Sentadilla 4x8»). */
    private fun addGridEntry(builder: DraftBuilder, grid: Grid, day: DayDraft, cell: String, row: RowContext) {
        val notation = parseNotation(cell)
        val named = numbered(notation?.name ?: dropLinks(cell))
        val name = named.name
        if (name.isBlank()) {
            builder.inDay(day) { row.warn(ImportWarningKind.SKIPPED_ROW, "falta el nombre del ejercicio («$cell»)") }
            return
        }
        if (countIfNotExercise(name, row)) return
        val entry = GridEntry(day, row.where, name)
        entry.cells += cell
        blockNote(named.block)?.let { entry.notes += it }
        if (notation != null) {
            entry.sets = notation.sets
            entry.repsMin = notation.repsMin
            entry.repsMax = notation.repsMax
            entry.amrap = notation.amrapReps
            entry.load = notation.load
            entry.percent = notation.percent
            entry.rpe = notation.rpe
            entry.rir = notation.rir
            entry.rest = notation.rest
            notation.notes?.let { entry.notes += it }
            if (notation.load != null) row.noteLoad(notation.loadUnit)
        }
        grid.entries += entry
    }

    /** Fila «Series x Reps» (o «Series», «Reps»): notación o números para el último ejercicio del día. */
    private fun applyGridScheme(
        builder: DraftBuilder,
        grid: Grid,
        day: DayDraft,
        label: String,
        cell: String,
        row: RowContext,
    ) {
        val entry = grid.lastEntryOf(day)
        if (entry == null) {
            missingGridName(builder, day, row)
            return
        }
        entry.cells += cell
        val words = ExerciseMatchLexicon.normalize(label).split(' ').toSet()
        val hasSets = words.any { it in GRID_SETS_KEYS }
        val hasReps = words.any { it in GRID_REPS_KEYS }
        val notation = if (hasScheme(cell)) parseNotation(cell) else null
        if (notation != null) {
            notation.sets?.let { entry.sets = it }
            notation.repsMin?.let { entry.repsMin = it }
            notation.repsMax?.let { entry.repsMax = it }
            notation.amrapReps?.let { entry.amrap = it }
        } else if (hasSets && !hasReps) {
            val sets = leadingInt(cell)
            if (sets != null) entry.sets = sets else entry.unreadable += ("series" to cell)
        } else if (hasReps && !hasSets) {
            val reps = parseRange(cell)
            if (reps != null) {
                entry.repsMin = reps.first
                entry.repsMax = reps.second
            } else {
                entry.unreadable += ("repeticiones" to cell)
            }
        } else {
            entry.unreadable += ("series y repeticiones" to cell)
        }
    }

    /** Fila «Peso» («Peso (kg)», «Carga», «Weight»): carga o porcentaje del último ejercicio del día. */
    private fun applyGridLoad(builder: DraftBuilder, grid: Grid, day: DayDraft, cell: String, row: RowContext) {
        val entry = grid.lastEntryOf(day)
        if (entry == null) {
            missingGridName(builder, day, row)
            return
        }
        entry.cells += cell
        if (cell.contains('%')) {
            entry.percent = firstNumber(cell)
            if (entry.percent == null) entry.unreadable += ("porcentaje" to cell)
            return
        }
        val load = firstNumber(cell)
        if (load == null) {
            entry.unreadable += ("carga" to cell)
        } else {
            entry.load = load
            row.noteLoad(unitMarkOf(cell))
        }
    }

    /** Fila «Descanso»: el descanso del último ejercicio del día. */
    private fun applyGridRest(
        builder: DraftBuilder,
        grid: Grid,
        day: DayDraft,
        label: String,
        cell: String,
        row: RowContext,
    ) {
        val entry = grid.lastEntryOf(day)
        if (entry == null) {
            missingGridName(builder, day, row)
            return
        }
        entry.cells += cell
        val rest = parseRest(cell, headerInMinutes = isMinutesHeader(label))
        if (rest == null) entry.unreadable += ("descanso" to cell) else entry.rest = rest
    }

    /** Fila «Notas»: notas del último ejercicio del día. */
    private fun applyGridNotes(builder: DraftBuilder, grid: Grid, day: DayDraft, cell: String, row: RowContext) {
        val entry = grid.lastEntryOf(day)
        if (entry == null) {
            missingGridName(builder, day, row)
            return
        }
        entry.cells += cell
        entry.notes += cell
    }

    /** Valores de un día sin ejercicio al que pertenecer: se omiten con el aviso de fila sin nombre. */
    private fun missingGridName(builder: DraftBuilder, day: DayDraft, row: RowContext) {
        builder.inDay(day) { row.warn(ImportWarningKind.SKIPPED_ROW, "fila sin nombre de ejercicio; se omite") }
    }

    /**
     * Cierra la cuadrícula: valida cada ejercicio con sus valores y lo añade a su día. Los valores que no se entendieron
     * avisan con el ejercicio al que pertenecen; los ejercicios sin series ni repeticiones se omiten como siempre.
     */
    private fun closeGrid(builder: DraftBuilder, grid: Grid) {
        for (entry in grid.entries) {
            builder.inDay(entry.day) {
                val row = RowContext(entry.where, entry.cells.joinToString(" · "), builder)
                val notes = (entry.notes + listOfNotNull(amrapNote(entry.amrap))).joinToString(" ").takeIf { it.isNotEmpty() }
                val exercise = validate(
                    name = entry.name,
                    sets = entry.sets,
                    repsMin = entry.repsMin,
                    repsMax = entry.repsMax,
                    load = entry.load,
                    percent = entry.percent,
                    rpe = entry.rpe,
                    rir = entry.rir,
                    rest = entry.rest,
                    notes = notes,
                    row = row,
                )
                for ((label, cell) in entry.unreadable) {
                    row.warn(ImportWarningKind.IGNORED_VALUE, "«$cell» no se entiende como $label; se ignora")
                }
                builder.endRow(exercise?.let { builder.addToDay(entry.day, it) })
            }
        }
    }

    // ---- Sesiones en columnas, nombres numerados y filas de día en tablas (H3g) ----------------------------

    /** Filas de encima de una subcabecera de sesiones que se miran en busca de semana, día o sesión. */
    private const val SESSION_LABEL_ROWS = 4

    /** Ancho máximo de un grupo de sesiones, en columnas. */
    private const val MAX_GROUP_WIDTH = 8

    /** Papel de una columna de un grupo de sesiones. «Series x reps» y «Scheme» son notación. */
    private enum class GroupCol { SCHEME, SETS, REPS, LOAD, PERCENT, RPE, RIR, NAME, OTHER }

    /** Papeles que se leen a la izquierda del primer grupo: nombre y prescripción base («Sets», «Reps / Scheme»). */
    private val BASE_COLS = setOf(GroupCol.NAME, GroupCol.SETS, GroupCol.REPS, GroupCol.SCHEME)

    private enum class LabelKind { WEEK, DAY, SESSION }

    /** Subcabecera de sesiones: primera columna del primer grupo, papeles de un grupo y cuántos grupos hay. */
    private class SessionLayout(val start: Int, val pattern: List<GroupCol>, val repeats: Int)

    /**
     * Etiquetas de un grupo: semana (y su texto, con las repeticiones de la 5/3/1), día y sesión si las hay; [ordinal]
     * numera las sesiones distintas (1, 2…).
     */
    private class GroupLabels(
        val week: Int?,
        val weekLabel: String?,
        val day: String?,
        val session: String?,
        val ordinal: Int?,
    )

    /**
     * Bloque de sesiones: subcabecera, primera columna del primer grupo, papeles, etiquetas y columnas base. Con
     * [oneSet] cada fila es una serie (familia 5/3/1); [groupNames] son los nombres que la subcabecera pone sobre cada grupo.
     */
    private class SessionBlock(
        val header: Int,
        val firstGroup: Int,
        val pattern: List<GroupCol>,
        val groups: List<GroupLabels>,
        /** Columna de cada papel base (nombre, series, notación o repeticiones) a la izquierda de los grupos. */
        val base: Map<GroupCol, Int>,
        val oneSet: Boolean = false,
        val groupNames: List<String?> = emptyList(),
    ) {
        /** Semana y día de la última fila que los trae en sus columnas base; las filas siguientes los heredan. */
        val cursor = RowCursor()

        /** Familia 5/3/1: levantamiento en curso (el de la fila con nombre, o el de la hoja) y series leídas de él. */
        var lift: String? = null
        var setRun = 0
    }

    /** Semana o día que trae la última fila del bloque con etiqueta propia («Week 2», «Monday» en su primera columna). */
    private class RowCursor {
        var week: Int? = null
        var day: String? = null
    }

    /** Qué es cada fila de un bloque de sesiones: subcabecera, etiqueta de encima o dato. */
    private sealed interface SessionPart {
        object Header : SessionPart
        object Labels : SessionPart
        class Data(val block: SessionBlock) : SessionPart
    }

    /** Un nombre sin enumerador y, si lo tenía, su etiqueta de bloque («A1. Remo» → «Remo», bloque A1). */
    private class Numbered(val name: String, val block: String?)

    /** Enumerador inicial («1.», «1)», «12.», «1 -»); el punto no va seguido de cifra («1.5 kg» no cuenta). */
    private val ENUMERATOR = Regex("""^\s*\d{1,2}(?:\s*[.)](?!\d)\s*|\s+[-–—]\s*)""")

    /** Etiqueta de bloque de superserie: «A1.», «A1)», «B2 -». */
    private val BLOCK_TAG = Regex("""^\s*([A-Za-z]\d{1,2})(?:\s*[.)](?!\d)\s*|\s+[-–—]\s*)""")

    /** Palabras de una fila de descanso dentro de un día («Descanso»): no son el foco del día. */
    private val REST_DAY_WORDS = setOf("descanso", "descansa", "rest", "off")

    /** Papeles de una fila de ejercicio: si ninguno tiene texto, la fila solo puede ser un día. */
    private val VALUE_ROLES = listOf(
        ColumnRole.NAME, ColumnRole.SETS, ColumnRole.REPS, ColumnRole.LOAD, ColumnRole.PERCENT,
        ColumnRole.RPE, ColumnRole.RIR, ColumnRole.REST,
    )

    /** Quita el enumerador del nombre y separa la etiqueta de bloque, si la hay. */
    private fun numbered(text: String): Numbered {
        BLOCK_TAG.find(text)?.let { match ->
            return Numbered(text.substring(match.range.last + 1).trim(), match.groupValues[1].uppercase())
        }
        val rest = ENUMERATOR.find(text)?.let { text.substring(it.range.last + 1) } ?: text
        return Numbered(rest.trim(), null)
    }

    /** Nota de superserie para una etiqueta de bloque («Bloque A1»); null si el nombre no la tenía. */
    private fun blockNote(block: String?): String? = block?.let { "Bloque $it" }

    /** Celda vacía a efectos de notación: vacía o un guion. */
    private fun isEmptyMark(text: String): Boolean = text.isBlank() || text == "-" || text == "–" || text == "—"

    /**
     * Nombre del día si la fila de tabla es una fila de día: su celda de nombre (o la primera con texto) es una etiqueta de
     * día y la fila no trae series ni repeticiones (la etiqueta puede caer en esa columna, como en Scraler). El foco es otra
     * celda de texto: «Día 1 — Pecho».
     */
    private fun tableDayName(cells: List<String>, columns: Columns): String? {
        val nameIndex = columns.indexOf(ColumnRole.NAME)
        val labelIndex = if (nameIndex != null && cells.getOrNull(nameIndex).orEmpty().isNotEmpty()) {
            nameIndex
        } else {
            cells.indexOfFirst { it.isNotEmpty() }
        }
        if (labelIndex < 0) return null
        val label = cells[labelIndex]
        if (parseNotation(label) != null || !isDayLabel(label)) return null
        val valued = listOf(ColumnRole.SETS, ColumnRole.REPS).mapNotNull { columns.indexOf(it) }.filter { it != labelIndex }
        if (valued.any { cells.getOrNull(it).orEmpty().isNotEmpty() }) return null
        val focus = cells.filterIndexed { index, text -> index != labelIndex && isFocusText(text) }.firstOrNull()
        val name = cleanHeader(label)
        return if (focus == null) name else "$name — $focus"
    }

    /** Texto que puede ser el foco de un día: letras suficientes, sin notación y sin ser una palabra de descanso. */
    private fun isFocusText(text: String): Boolean {
        val words = ExerciseMatchLexicon.normalize(text).split(' ').filter { it.isNotEmpty() }
        return text.count { it.isLetter() } >= 3 && parseNotation(text) == null &&
            !(words.isNotEmpty() && words.all { it in REST_DAY_WORDS })
    }

    /** Fila sin valores de ejercicio (ni nombre, ni series, ni carga…): solo sirve de día, como «Miércoles | Descanso». */
    private fun isBareTableRow(cells: List<String>, columns: Columns): Boolean =
        VALUE_ROLES.none { role -> columns.indexOf(role)?.let { cells.getOrNull(it) }.orEmpty().isNotEmpty() }

    /** Clase de una etiqueta de grupo: semana («Semana 2»), sesión («Sesión 3») o día («Lunes», «Día 1»); null si no es ninguna. */
    private fun labelKindOf(text: String): LabelKind? {
        if (weekNumber(text) != null) return LabelKind.WEEK
        val n = ExerciseMatchLexicon.normalize(text)
        if (n.isEmpty()) return null
        return when {
            SESSION_LABEL.matches(n) -> LabelKind.SESSION
            DAY_NUMBERED.matches(n) || WEEKDAY_LABEL.matches(n) -> LabelKind.DAY
            else -> null
        }
    }

    private fun isFechaLabel(text: String): Boolean {
        val n = ExerciseMatchLexicon.normalize(text)
        return n == "fecha" || n.startsWith("fecha ") || n == "date"
    }

    /** Fila de etiquetas de un bloque: alguna celda con semana, día, sesión o «Fecha». */
    private fun isLabelRow(cells: List<String>): Boolean =
        cells.any { it.isNotEmpty() && (labelKindOf(it) != null || isFechaLabel(it)) }

    /** Papel de una celda de subcabecera; OTHER si no tiene papel o está vacía. */
    private fun groupColOf(raw: String): GroupCol {
        val text = raw.trim()
        if (text.isEmpty()) return GroupCol.OTHER
        val words = ExerciseMatchLexicon.normalize(text.replace(PARENTHESES, " ")).split(' ').filter { it.isNotEmpty() }.toSet()
        // «Lift» es el nombre del ejercicio en las hojas de fuerza (5/3/1, Starscream): solo cuenta en subcabeceras de sesiones.
        if (words == setOf("lift") || words == setOf("lifts")) return GroupCol.NAME
        if ("scheme" in words || "esquema" in words || ("x" in words && words.any { it in GRID_SCHEME_KEYS })) {
            return GroupCol.SCHEME
        }
        // «Est 1RM» es una columna calculada, no un porcentaje: no abre grupo.
        if ("est" in words || "estimado" in words) return GroupCol.OTHER
        return when (columnRole(text)) {
            ColumnRole.SETS -> GroupCol.SETS
            ColumnRole.REPS -> GroupCol.REPS
            ColumnRole.LOAD -> GroupCol.LOAD
            ColumnRole.PERCENT -> GroupCol.PERCENT
            ColumnRole.RPE -> GroupCol.RPE
            ColumnRole.RIR -> GroupCol.RIR
            ColumnRole.NAME -> GroupCol.NAME
            else -> GroupCol.OTHER
        }
    }

    /**
     * Subcabecera de sesiones: desde alguna columna, un patrón de papeles (dos o más con papel) que se repite dos veces o más
     * hacia la derecha. Si hay varios, gana el que tiene más grupos bajo una etiqueta de semana, día o sesión ([anchors]);
     * sin etiquetas, el primero (más a la izquierda y de menor ancho). Null si la fila no es una subcabecera.
     */
    private fun sessionLayout(cells: List<String>, anchors: Set<Int> = emptySet()): SessionLayout? {
        val roles = cells.map { groupColOf(it) }
        var best: SessionLayout? = null
        var bestAnchors = 0
        for (start in roles.indices) {
            if (roles[start] == GroupCol.OTHER) continue
            for (width in 2..MAX_GROUP_WIDTH) {
                if (start + width > roles.size) break
                val pattern = roles.subList(start, start + width)
                // Dos papeles distintos como mínimo: «Reps | Reps | Reps» (una cabecera de semanas) no es un grupo.
                if (pattern.filter { it != GroupCol.OTHER }.toSet().size < 2) continue
                // La última repetición puede ser parcial (una hoja que corta el grupo al final de la fila), pero tiene
                // que coincidir en al menos dos celdas: una sola celda «Reps» al final no abre un bloque.
                var repeats = 1
                while (start + repeats * width < roles.size) {
                    val from = start + repeats * width
                    val to = minOf(from + width, roles.size)
                    if (to - from < 2) break
                    if (roles.subList(from, to) != pattern.subList(0, to - from)) break
                    repeats++
                }
                if (repeats < 2) continue
                // Gana la forma con más grupos bajo una etiqueta de semana, día o sesión. Con igual número (o sin etiquetas)
                // se queda la primera en el orden de búsqueda: la más a la izquierda y la de menor ancho, como en H3g.
                val hits = (0 until repeats).count { start + it * width in anchors }
                if (best == null || hits > bestAnchors) {
                    best = SessionLayout(start, pattern.toList(), repeats)
                    bestAnchors = hits
                }
            }
        }
        return best
    }

    /** Etiqueta de la clase pedida más cercana a [column] o a su izquierda, buscando desde la fila [to] hacia la [from]. */
    private fun nearestLabel(cells: List<List<String>>, from: Int, to: Int, column: Int, kind: LabelKind): String? {
        for (r in to downTo from) {
            val row = cells[r]
            for (c in minOf(column, row.size - 1) downTo 0) {
                val text = row[c]
                if (text.isNotEmpty() && labelKindOf(text) == kind) return cleanHeader(text)
            }
        }
        return null
    }

    /** Etiquetas de cada grupo del bloque (una por repetición del patrón), buscadas en las filas [from]..[to]. */
    private fun groupLabelsOf(cells: List<List<String>>, from: Int, to: Int, layout: SessionLayout): List<GroupLabels> {
        val keys = ArrayList<String>()
        return (0 until layout.repeats).map { group ->
            val column = layout.start + group * layout.pattern.size
            val weekLabel = nearestLabel(cells, from, to, column, LabelKind.WEEK)
            val week = weekLabel?.let { weekNumber(it) }
            val day = nearestLabel(cells, from, to, column, LabelKind.DAY)
            val session = nearestLabel(cells, from, to, column, LabelKind.SESSION)
            val ordinal = session?.let { label ->
                val key = ExerciseMatchLexicon.normalize(label)
                if (key !in keys) keys += key
                keys.indexOf(key) + 1
            }
            GroupLabels(week, weekLabel, day, session, ordinal)
        }
    }

    /**
     * Columnas base (nombre, series, notación) a la izquierda del primer grupo. Se buscan en la subcabecera y, si no
     * están allí, en las filas de encima (la cabecera «Exercise | Sets | Reps» suele ir sobre las semanas).
     */
    private fun baseColumnsOf(cells: List<List<String>>, from: Int, header: Int, firstGroup: Int): Map<GroupCol, Int> {
        val base = LinkedHashMap<GroupCol, Int>()
        for (row in header downTo from) {
            for (column in 0 until firstGroup) {
                val role = groupColOf(cells[row].getOrNull(column).orEmpty())
                if (role in BASE_COLS && role !in base) base[role] = column
            }
        }
        return base
    }

    /** Una fila corta el bloque si es otra cabecera, otra subcabecera o trae etiquetas de grupo (semana, día, sesión, fecha). */
    private fun endsSessionData(cells: List<String>, firstGroup: Int): Boolean {
        if (detectColumns(cells) != null || sessionLayout(cells) != null) return true
        val groupCells = cells.drop(firstGroup)
        // Una semana, sesión o fecha en los grupos abre otro bloque, tenga la fila valores o no (como en H3g).
        val weekOrSession = groupCells.any {
            it.isNotEmpty() && (labelKindOf(it).let { kind -> kind == LabelKind.WEEK || kind == LabelKind.SESSION } || isFechaLabel(it))
        }
        if (weekOrSession) return true
        // Un día en los grupos abre bloque solo en una fila sin valores. Con valores es un dato: el «Monday» que cada semana
        // repite en Nuckols no corta el bloque.
        val day = groupCells.any { it.isNotEmpty() && labelKindOf(it) == LabelKind.DAY }
        val values = groupCells.any { it.isNotEmpty() && labelKindOf(it) == null && !isFechaLabel(it) }
        return day && !values
    }

    /** Columnas con una etiqueta de semana, día o sesión en las filas de encima de [index]: ahí caen los grupos. */
    private fun labelColumnsAbove(cells: List<List<String>>, index: Int): Set<Int> {
        val columns = HashSet<Int>()
        for (k in maxOf(0, index - SESSION_LABEL_ROWS) until index) {
            cells[k].forEachIndexed { column, text ->
                if (text.isNotEmpty() && labelKindOf(text) != null) columns += column
            }
        }
        return columns
    }

    /**
     * Subcabecera de una serie por fila (familia 5/3/1): una celda que solo dice «Set» o «Set %» (no «Sets») y una columna
     * de porcentaje en la misma subcabecera («% of TM»). Sin porcentaje, «Set» es solo una columna de series o de etiquetas.
     */
    private fun isOneSetHeader(cells: List<String>): Boolean =
        cells.any { ExerciseMatchLexicon.normalize(it) == "set" } && cells.any { groupColOf(it) == GroupCol.PERCENT }

    /** Nombre de cada grupo puesto en la subcabecera, en la columna de su izquierda («SQUAT» sobre «Reps | Weight»). */
    private fun groupNamesOf(header: List<String>, layout: SessionLayout): List<String?> =
        (0 until layout.repeats).map { group ->
            val text = header.getOrNull(layout.start - 1 + group * layout.pattern.size).orEmpty().trim()
            text.takeIf {
                it.count { c -> c.isLetter() } >= 3 && columnRole(it) == null && groupColOf(it) == GroupCol.OTHER &&
                    labelKindOf(it) == null && !isFechaLabel(it) && parseNotation(it) == null
            }
        }

    /**
     * Reparte las filas con sesiones en columnas. Cada subcabecera abre un bloque: sus etiquetas son las filas de encima
     * (hasta [SESSION_LABEL_ROWS], sin pisar el bloque anterior) y sus datos llegan hasta la siguiente etiqueta, cabecera o
     * fin de hoja.
     */
    private fun planSessions(rows: List<List<String>>): Map<Int, SessionPart> {
        val cells = rows.map { row -> row.map { it.trim() } }
        // Una subcabecera solo abre bloque si sus grupos tienen alguna etiqueta (semana, día o sesión) encima, o si hereda
        // las del bloque anterior con la misma forma (bloques apilados: la misma tabla repetida por debajo).
        class Candidate(
            val header: Int,
            val layout: SessionLayout,
            val top: Int,
            val groups: List<GroupLabels>,
            val oneSet: Boolean,
            val groupNames: List<String?>,
        )
        val candidates = ArrayList<Candidate>()
        var previous = -1
        for (index in cells.indices) {
            val layout = sessionLayout(cells[index], labelColumnsAbove(cells, index)) ?: continue
            val lowest = maxOf(previous + 1, index - SESSION_LABEL_ROWS)
            val top = (index - 1 downTo lowest).lastOrNull { k -> isLabelRow(cells[k]) } ?: index
            val labelled = groupLabelsOf(cells, top, index - 1, layout)
            val ownLabels = labelled.any { it.week != null || it.day != null || it.session != null }
            val before = candidates.lastOrNull()
            val sameShape = before != null && before.layout.start == layout.start &&
                before.layout.pattern == layout.pattern && before.layout.repeats == layout.repeats
            val groups = when {
                ownLabels -> labelled
                sameShape -> before!!.groups
                else -> continue
            }
            candidates += Candidate(index, layout, top, groups, isOneSetHeader(cells[index]), groupNamesOf(cells[index], layout))
            previous = index
        }
        val parts = HashMap<Int, SessionPart>()
        candidates.forEachIndexed { j, candidate ->
            val end = candidates.getOrNull(j + 1)?.top ?: cells.size
            var stop = candidate.header + 1
            while (stop < end && !endsSessionData(cells[stop], candidate.layout.start)) stop++
            val block = SessionBlock(
                header = candidate.header,
                firstGroup = candidate.layout.start,
                pattern = candidate.layout.pattern,
                groups = candidate.groups,
                base = baseColumnsOf(cells, candidate.top, candidate.header, candidate.layout.start),
                oneSet = candidate.oneSet,
                groupNames = candidate.groupNames,
            )
            for (k in candidate.top until candidate.header) parts[k] = SessionPart.Labels
            parts[candidate.header] = SessionPart.Header
            for (k in candidate.header + 1 until stop) parts[k] = SessionPart.Data(block)
        }
        return parts
    }

    /** Semana y día de un grupo: los de sus etiquetas; con sesiones y un día abierto, la sesión numera la semana. */
    private fun sessionTarget(
        builder: DraftBuilder,
        labels: GroupLabels,
        group: Int,
        cursor: RowCursor,
    ): Pair<Int, String> {
        val context = builder.pendingDayName ?: builder.openDayName
        val session = labels.session
        val week = labels.week ?: cursor.week ?: if (session != null && context != null) {
            labels.ordinal ?: builder.currentWeek
        } else {
            builder.currentWeek
        }
        val day = labels.day ?: cursor.day ?: when {
            session != null && context != null -> context
            session != null -> session
            else -> context ?: "Día ${group + 1}"
        }
        return week to day
    }

    /** Fila de datos de un bloque: un ejercicio por grupo, con su notación (o la base), su carga, su RPE o su RIR. */
    private fun sessionRow(builder: DraftBuilder, block: SessionBlock, cells: List<String>, row: RowContext) {
        if (block.oneSet) {
            setRow(builder, block, cells, row)
            return
        }
        fun own(group: Int, role: GroupCol): String = ownCell(block, cells, group, role)
        fun baseText(role: GroupCol): String {
            val text = block.base[role]?.let { cells.getOrNull(it) }.orEmpty()
            return if (isEmptyMark(text)) "" else text
        }

        // Notación de la fila: la de «Series x reps», o la de la columna de series o de repeticiones si trae «5x10» o «1*2-5».
        fun schemeOf(scheme: String, sets: String, reps: String): String =
            listOf(scheme, sets, reps).firstOrNull { hasScheme(it) }.orEmpty()

        val baseScheme = schemeOf(baseText(GroupCol.SCHEME), baseText(GroupCol.SETS), baseText(GroupCol.REPS))
            .ifEmpty { headerlessScheme(cells, block) }
        val baseNotation = baseScheme.takeIf { it.isNotEmpty() }?.let { parseNotation(it) }
        val baseRange = if (baseNotation == null) {
            baseText(GroupCol.REPS).takeIf { it.isNotEmpty() }?.let { parseRange(it) }
        } else {
            null
        }
        val baseSets = leadingInt(baseText(GroupCol.SETS)) ?: baseNotation?.sets
        val baseRepsMin = baseNotation?.repsMin ?: baseRange?.first
        val baseRepsMax = baseNotation?.repsMax ?: baseRange?.second
        val baseAmrap = baseNotation?.amrapReps
        val hasBase = baseSets != null || baseRepsMin != null

        // Nombre de la fila: la columna base o, si no la hay, la primera celda con letras que no sea una etiqueta de día.
        val baseName = baseText(GroupCol.NAME).ifEmpty {
            cells.take(block.firstGroup).firstOrNull { text ->
                text.count { it.isLetter() } >= 3 && labelKindOf(text) == null && !isFechaLabel(text)
            }.orEmpty()
        }
        val groupCount = block.groups.size
        val anyValue = (0 until groupCount).any { group ->
            listOf(GroupCol.SCHEME, GroupCol.SETS, GroupCol.LOAD, GroupCol.PERCENT, GroupCol.RPE, GroupCol.RIR)
                .any { own(group, it).isNotEmpty() } || hasScheme(own(group, GroupCol.REPS))
        }
        // Etiquetas de la fila en sus columnas base («Week 2», «Monday»): cambian el cursor del bloque para las filas siguientes.
        var rowWeek: Int? = null
        var rowDay: String? = null
        for (column in 0 until block.firstGroup) {
            val text = cells.getOrNull(column).orEmpty()
            when (labelKindOf(text)) {
                LabelKind.WEEK -> rowWeek = weekNumber(text)
                LabelKind.DAY -> rowDay = cleanHeader(text)
                else -> Unit
            }
        }
        rowWeek?.let { block.cursor.week = it }
        rowDay?.let { block.cursor.day = it }
        if ((rowWeek != null || rowDay != null) && !anyValue) {
            builder.endRow(null)
            return
        }
        if (!hasBase && !anyValue) {
            if (isProse(baseName)) builder.count(Noise.TEXT)
            builder.endRow(null)
            return
        }
        var warnedNoName = false
        val anyLabel = block.groups.any { it.week != null || it.day != null || it.session != null }
        for (group in 0 until groupCount) {
            // Un grupo sin ninguna etiqueta, en un bloque que sí las tiene, no es una sesión (columnas de sobra).
            val labels = block.groups[group]
            if (anyLabel && labels.week == null && labels.day == null && labels.session == null) continue
            val scheme = schemeOf(own(group, GroupCol.SCHEME), own(group, GroupCol.SETS), own(group, GroupCol.REPS))
            val setsCell = own(group, GroupCol.SETS)
            val repsCell = own(group, GroupCol.REPS)
            val loadCell = own(group, GroupCol.LOAD)
            val percentCell = own(group, GroupCol.PERCENT)
            val rpeCell = own(group, GroupCol.RPE)
            val rirCell = own(group, GroupCol.RIR)
            val notation = scheme.takeIf { it.isNotEmpty() }?.let { parseNotation(it) }
            // Series y repeticiones propias del grupo (si tiene sus columnas); si no, la notación o la base.
            val groupSets = if (block.pattern.contains(GroupCol.SETS)) leadingInt(setsCell) else null
            val groupRange = if (block.pattern.contains(GroupCol.SETS) && block.pattern.contains(GroupCol.REPS)) {
                parseRange(repsCell)
            } else {
                null
            }
            val sets = notation?.sets ?: groupSets ?: baseSets
            val repsMin = notation?.repsMin ?: groupRange?.first ?: baseRepsMin
            val repsMax = notation?.repsMax ?: groupRange?.second ?: baseRepsMax
            val amrap = notation?.amrapReps ?: baseAmrap
            val untouched = scheme.isEmpty() && setsCell.isEmpty() && loadCell.isEmpty() && percentCell.isEmpty() &&
                rpeCell.isEmpty() && rirCell.isEmpty()
            if (untouched && sets == null) continue
            // Nombre del grupo: el de su propia columna, el de la fila o el que la subcabecera pone sobre el grupo.
            val named = numbered(dropLinks(own(group, GroupCol.NAME).ifEmpty { baseName.ifEmpty { block.groupNames.getOrNull(group).orEmpty() } }))
            if (named.name.isBlank()) {
                if (!warnedNoName) row.warn(ImportWarningKind.SKIPPED_ROW, "fila sin nombre de ejercicio; se omite")
                warnedNoName = true
                continue
            }
            if (countIfNotExercise(named.name, row)) continue
            val percent = fractionPercent(percentCell) ?: if (loadCell.contains('%')) firstNumber(loadCell) else null
            val load = if (loadCell.contains('%')) null else firstNumber(loadCell)
            val rpe = firstNumber(rpeCell)
            val rir = leadingInt(rirCell)
            if (load != null) row.noteLoad(unitMarkOf(loadCell))
            val (week, dayName) = sessionTarget(builder, block.groups[group], group, block.cursor)
            val day = builder.sessionDay(week, dayName, row)
            val exercise = builder.inDay(day) {
                unreadable(row, percentCell, percent, "porcentaje")
                unreadable(row, loadCell, load ?: percent, "carga")
                unreadable(row, rpeCell, rpe, "RPE")
                unreadable(row, rirCell, rir, "RIR")
                val notes = listOfNotNull(blockNote(named.block), notation?.notes, amrapNote(amrap))
                    .joinToString(" ").takeIf { it.isNotEmpty() }
                validate(
                    name = named.name,
                    sets = sets,
                    repsMin = repsMin,
                    repsMax = repsMax,
                    load = load,
                    percent = percent,
                    rpe = rpe,
                    rir = rir,
                    rest = null,
                    notes = notes,
                    row = row,
                )
            }
            if (exercise == null) builder.discardIfEmpty(day)
            builder.endRow(exercise?.let { builder.addToDay(day, it) })
        }
        builder.endRow(null)
    }

    /** Celda de un grupo para un papel de la subcabecera; vacía si el grupo no tiene ese papel o la celda es un guion. */
    private fun ownCell(block: SessionBlock, cells: List<String>, group: Int, role: GroupCol): String {
        val offset = block.pattern.indexOf(role)
        if (offset < 0) return ""
        val text = cells.getOrNull(block.firstGroup + group * block.pattern.size + offset).orEmpty()
        return if (isEmptyMark(text)) "" else text
    }

    /** Notación en una columna de la izquierda sin cabecera («8x8» junto al nombre): es la prescripción base de la fila. */
    private fun headerlessScheme(cells: List<String>, block: SessionBlock): String =
        cells.take(block.firstGroup).filterIndexed { index, _ -> index !in block.base.values }
            .firstOrNull { hasScheme(it) }.orEmpty()

    /** Porcentaje de una celda de columna de %: un valor de hasta 1,5 es una fracción («0,5» → 50 %). */
    private fun fractionPercent(cell: String): Double? {
        val value = firstNumber(cell) ?: return null
        return if (value <= 1.5) Math.round(value * 1000) / 10.0 else value
    }

    /**
     * Fila de una serie por fila (familia 5/3/1): cada fila con porcentaje o carga es una serie del levantamiento, y un
     * grupo es una semana. Las series seguidas del mismo levantamiento se funden después (H3e). Solo se lee el primer ciclo.
     */
    private fun setRow(builder: DraftBuilder, block: SessionBlock, cells: List<String>, row: RowContext) {
        val label = cells.take(block.firstGroup).firstOrNull { it.isNotEmpty() && labelKindOf(it) == null && !isFechaLabel(it) }
            .orEmpty()
        // Desde el «Cycle 2» la hoja deja de leerse en las series: el resto de la hoja se omite sin avisos.
        cycleNumber(label)?.let { cycle ->
            if (cycle >= 2) builder.stopCycles()
            block.setRun = 0
            builder.endRow(null)
            return
        }
        if (builder.cyclesStopped) {
            builder.endRow(null)
            return
        }
        val valued = (0 until block.groups.size).any { group ->
            ownCell(block, cells, group, GroupCol.PERCENT).isNotEmpty() || ownCell(block, cells, group, GroupCol.LOAD).isNotEmpty()
        }
        if (!valued) {
            // Sin valores: un nombre de levantamiento («Military») o una sección («Boring But Big») reinicia la cuenta de series.
            if (label.isNotEmpty() && !isSetLabel(label) && !isSectionLabel(label)) block.lift = cleanHeader(label)
            block.setRun = 0
            builder.endRow(null)
            return
        }
        if (isWarmupLabel(label)) {
            row.count(Noise.WARMUP)
            builder.endRow(null)
            return
        }
        // Un nombre que no es de serie cambia el levantamiento; «Set 1» o «BBB Set 1» no lo cambian.
        if (label.isNotEmpty() && !isSetLabel(label) && !isSectionLabel(label)) {
            block.lift = cleanHeader(label)
            block.setRun = 0
        }
        val lift = block.lift ?: builder.sheetLift
        if (lift == null) {
            row.warn(ImportWarningKind.SKIPPED_ROW, "fila sin nombre de ejercicio; se omite")
            builder.endRow(null)
            return
        }
        val normalized = ExerciseMatchLexicon.normalize(label)
        val bbb = normalized.split(' ').contains("bbb")
        val name = if (bbb) "$lift BBB" else lift
        val index = SET_NUMBER.find(normalized)?.groupValues?.get(1)?.toIntOrNull()?.minus(1) ?: block.setRun
        block.setRun++
        val repsInLabel = REPS_IN_LABEL.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        val amrap = normalized.split(' ').contains("amrap") || label.contains('+')
        for (group in 0 until block.groups.size) {
            val labels = block.groups[group]
            val week = labels.week ?: continue
            val percentCell = ownCell(block, cells, group, GroupCol.PERCENT)
            val loadCell = ownCell(block, cells, group, GroupCol.LOAD)
            val percent = fractionPercent(percentCell) ?: if (loadCell.contains('%')) firstNumber(loadCell) else null
            val load = if (loadCell.contains('%')) null else firstNumber(loadCell)
            if (percent == null && load == null) continue
            if (load != null) row.noteLoad(unitMarkOf(loadCell))
            val reps = repsInLabel ?: fiveThreeOneReps(labels.weekLabel, index)
            val day = builder.sessionDay(week, lift, row)
            val exercise = builder.inDay(day) {
                unreadable(row, percentCell, percent, "porcentaje")
                unreadable(row, loadCell, load ?: percent, "carga")
                validate(
                    name = name,
                    sets = 1,
                    repsMin = reps,
                    repsMax = reps,
                    load = load,
                    percent = percent,
                    rpe = null,
                    rir = null,
                    rest = null,
                    notes = if (amrap) amrapNote(reps) else null,
                    row = row,
                )
            }
            if (exercise == null) builder.discardIfEmpty(day)
            builder.endRow(exercise?.let { builder.addToDay(day, it) })
        }
        builder.endRow(null)
    }

    /** Repeticiones de una serie según la etiqueta de su semana: «(3x5)» → 5; «(5/3/1)» → 5, 3 y 1 por orden; «Deload» → 5. */
    private fun fiveThreeOneReps(weekLabel: String?, index: Int): Int? {
        val text = weekLabel.orEmpty()
        FIVE_THREE_ONE.find(text)?.let { match ->
            val reps = listOf(match.groupValues[1], match.groupValues[2], match.groupValues[3]).map { it.toInt() }
            return reps[index.coerceIn(0, reps.lastIndex)]
        }
        SCHEME_REPS.find(text)?.let { match -> return match.groupValues[2].toIntOrNull() }
        val normalized = ExerciseMatchLexicon.normalize(text)
        return if ("deload" in normalized || "descarga" in normalized) 5 else null
    }

    /** Serie de un levantamiento: «Set 1», «Serie 2», «Set 3 (AMRAP)», «BBB Set 1 (10 reps)», «Top set», «Back-off set». */
    private fun isSetLabel(text: String): Boolean {
        val words = ExerciseMatchLexicon.normalize(text).split(' ')
        return "set" in words || "serie" in words
    }

    /** Etiqueta de sección de una hoja de series, que no nombra un levantamiento: BBB, calentamiento. */
    private fun isSectionLabel(text: String): Boolean {
        val words = ExerciseMatchLexicon.normalize(text).split(' ')
        return "bbb" in words || "boring" in words || isWarmupLabel(text)
    }

    private fun isWarmupLabel(text: String): Boolean {
        val words = ExerciseMatchLexicon.normalize(text).split(' ').filter { it.isNotEmpty() }
        return words.isNotEmpty() && isWarmupStart(words)
    }

    /** Número de serie en el texto normalizado: «set 2», «serie 3». */
    private val SET_NUMBER = Regex("""\b(?:set|serie) (\d{1,2})\b""")

    /** Repeticiones de la etiqueta de una serie en el texto normalizado: «bbb set 1 10 reps». */
    private val REPS_IN_LABEL = Regex("""\b(\d{1,2}) reps?\b""")

    /** «5/3/1» de una semana de la 5/3/1: una repetición por serie. */
    private val FIVE_THREE_ONE = Regex("""(\d{1,2})\s*/\s*(\d{1,2})\s*/\s*(\d{1,2})""")

    /** «3x5» de una semana de la 5/3/1: tres series de cinco; manda la segunda cifra (las repeticiones). */
    private val SCHEME_REPS = Regex("""(\d{1,2})\s*[x×]\s*(\d{1,2})(?!\d)""", RegexOption.IGNORE_CASE)

    // ---- Grupos con porcentaje (Sheiko, Smolov, 5/3/1) ------------------------------------------------------

    /** Un grupo de series de una fila: «70%x3x2» son 2 series de 3 repeticiones al 70 %. */
    private class PercentGroup(val name: String, val notes: String?, val percent: Double, val reps: Int, val sets: Int)

    /** Un grupo encontrado en el texto: su posición y sus cifras. */
    private class GroupSpan(val range: IntRange, val percent: Double, val reps: Int, val sets: Int)

    /** «70%x3», «70% x 3 x 2», «70%×3×2»: porcentaje, repeticiones y, si hay, series. */
    private val PERCENT_GROUP = Regex(
        """(?<![\d.,])(\d{1,3}(?:[.,]\d+)?)\s*%\s*[x×]\s*(\d{1,2})(?!\d)(?:\s*[x×]\s*(\d{1,2})(?!\d))?""",
        RegexOption.IGNORE_CASE,
    )

    /** «70/3» o «70/3/2» (porcentaje, repeticiones y series): solo cuenta si hay dos o más en la fila. */
    private val SLASH_GROUP = Regex("""(?<![\d.,/])(\d{1,3})\s*/\s*(\d{1,2})(?!\d)(?:\s*/\s*(\d{1,2})(?!\d))?""")

    /** Un grupo con cifras dentro de rango (porcentaje 30–110, repeticiones y series 1–12), o null. */
    private fun groupOf(match: MatchResult): GroupSpan? {
        val percent = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val reps = match.groupValues[2].toIntOrNull() ?: return null
        val sets = match.groupValues[3].ifEmpty { "1" }.toIntOrNull() ?: return null
        if (percent !in 30.0..110.0 || reps !in 1..12 || sets !in 1..12) return null
        return GroupSpan(match.range, percent, reps, sets)
    }

    /**
     * Grupos de una fila («Sentadilla 50%x5, 60%x4, 70%x3x2» o «50/5 60/4 70/3/2»), o null si no los hay. Con «%» basta
     * un grupo; con barras, dos. Si la fila trae una notación normal («4x8» sin %), gana esa y no hay grupos.
     */
    private fun percentGroups(text: String): List<GroupSpan>? {
        val withPercent = PERCENT_GROUP.findAll(text).mapNotNull { groupOf(it) }.toList()
        val withSlash = SLASH_GROUP.findAll(text).mapNotNull { groupOf(it) }
            .filter { span -> withPercent.none { overlaps(it.range, span.range) } }
            .toList()
        if (withPercent.isEmpty() && withSlash.size < 2) return null
        val groups = (withPercent + withSlash).sortedBy { it.range.first }
        return if (hasPlainScheme(text, groups)) null else groups
    }

    /** Una notación normal («4x8», «4 series de 6») fuera de los grupos: gana sobre ellos. */
    private fun hasPlainScheme(text: String, groups: List<GroupSpan>): Boolean =
        (SETS_BY_REPS.findAll(text) + SETS_OF_REPS.findAll(text))
            .any { match -> groups.none { overlaps(it.range, match.range) } }

    /** Un ejercicio por grupo: el nombre es el texto antes del primer grupo; lo de detrás, notas (como en la notación). */
    private fun groupLine(text: String, spans: List<GroupSpan>, row: RowContext): Line? {
        val first = spans.first().range.first
        val before = StringBuilder()
        val behind = StringBuilder()
        text.forEachIndexed { index, char ->
            if (spans.any { index in it.range }) return@forEachIndexed
            if (index < first) before.append(char) else behind.append(char)
        }
        val beforeName = cleanName(before.toString())
        val named = numbered(beforeName.ifEmpty { cleanName(behind.toString()) })
        val name = named.name
        if (name.isBlank()) {
            row.warn(ImportWarningKind.SKIPPED_ROW, "falta el nombre del ejercicio («$text»)")
            return null
        }
        if (countIfNotExercise(name, row)) return null
        val behindNotes = if (beforeName.isEmpty()) null else cleanNotes(behind.toString()).takeIf { it.isNotEmpty() }
        val notes = listOfNotNull(blockNote(named.block), behindNotes).joinToString(" ").takeIf { it.isNotEmpty() }
        return Line.Groups(spans.map { PercentGroup(name, notes, it.percent, it.reps, it.sets) })
    }

    /** Un grupo de una fila con varios: un ejercicio con sus series, repeticiones y porcentaje. */
    private fun addGroup(builder: DraftBuilder, group: PercentGroup, row: RowContext) {
        val exercise = validate(
            name = group.name,
            sets = group.sets,
            repsMin = group.reps,
            repsMax = group.reps,
            load = null,
            percent = group.percent,
            rpe = null,
            rir = null,
            rest = null,
            notes = group.notes,
            row = row,
        )
        builder.endRow(exercise?.let { builder.addExercise(it, row) })
    }

    // ---- Unidad de carga (kg o libras) y prosa con notación -------------------------------------------------

    /** Kilos o libras: marca de unidad en un texto de carga («60kg», «135 lb», «Peso (kg)», «Weight (lb)»). */
    private val KG_MARK = Regex("""(?<![a-z])(kgs?|kilos?|kilogramos?)(?![a-z])""")
    private val LB_MARK = Regex("""(?<![a-z])(lbs?|libras?|pounds?)(?![a-z])""")

    /** Unidad que dice un texto de carga; null si no dice ninguna. */
    private fun unitMarkOf(text: String): ImportLoadUnit? {
        val lower = ExerciseMatchLexicon.normalize(text)
        return when {
            KG_MARK.containsMatchIn(lower) -> ImportLoadUnit.KG
            LB_MARK.containsMatchIn(lower) -> ImportLoadUnit.LB
            else -> null
        }
    }

    /** Unidad de un tramo de la notación de carga («kg», «kilos», «lb», «libras»…). */
    private fun unitOfToken(token: String): ImportLoadUnit =
        if (token.lowercase().startsWith("k")) ImportLoadUnit.KG else ImportLoadUnit.LB

    /** Prosa con notación: lo que va antes de la notación tiene más de 6 palabras o más de 45 caracteres. */
    private fun isLongLeadingText(leading: String): Boolean {
        val text = leading.trim()
        return text.split(WHITESPACE).count { it.isNotEmpty() } > 6 || text.length > 45
    }

    /** Celda de día de una tabla: un día o un texto corto que puede serlo; la prosa no abre día. */
    private fun isTableDayCandidate(text: String): Boolean =
        !isProse(text) && (isDayLabel(text) || isShortDayCandidate(text))

    // ---- Estado del análisis --------------------------------------------------------------------------------

    private sealed interface Line {
        data class Week(val number: Int) : Line

        /** Día en curso; [silent] si es solo un texto corto que puede no ser día (si sale vacío, no avisa). */
        data class Day(val name: String, val silent: Boolean) : Line
        data class Exercise(val exercise: ImportedExercise) : Line

        /** Varios grupos con porcentaje de una misma fila: un ejercicio por grupo. */
        data class Groups(val groups: List<PercentGroup>) : Line
    }

    /** Fila o línea en curso: dónde está y su texto original, para los avisos y el ejercicio que salga de ella. */
    private class RowContext(val where: String, val source: String, private val builder: DraftBuilder) {
        fun warn(kind: ImportWarningKind, detail: String) = builder.warn(kind, where, source, "$where: $detail")

        fun count(noise: Noise) = builder.count(noise)

        /** Carga con número de esta fila: [mark] es la unidad que lleva la celda, si la lleva. */
        fun noteLoad(mark: ImportLoadUnit?) = builder.noteLoadValue(mark)

        /** Aviso de una carga que solo cabe en una unidad: se decide al cerrar el borrador. */
        fun warnUnitLoad(load: Double, detail: String) = builder.warnUnitLoad(load, where, source, "$where: $detail")
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
        /** Carga que solo cabe en una unidad: el aviso sobrevive solo si el archivo va en la otra. */
        val unitLoad: Double? = null,
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

        /** Levantamiento de la hoja (título «Press – 5/3/1» o nombre de la pestaña): día de una familia 5/3/1 sin nombre propio. */
        var sheetLift: String? = null

        /** Desde el ciclo 2 de la hoja no se leen más series de una familia 5/3/1 («solo se lee el primer ciclo»). */
        var cyclesStopped = false
            private set

        fun stopCycles() {
            cyclesStopped = true
        }

        /** Semana en curso: la última que abrió una fila de semana, o la 1. */
        val currentWeek: Int get() = week

        /** Nombre del día abierto, si lo hay. */
        val openDayName: String? get() = openDay?.name

        // Marcas de unidad de carga: cabeceras y valores con número, y cuántos llevan libras.
        private var kgMarks = 0
        private var lbHeaderMarks = 0
        private var numericLoads = 0
        private var lbLoads = 0

        /** Cabecera de carga con su unidad («Peso (kg)», «Weight (lb)»). */
        fun noteLoadHeader(text: String) {
            when (unitMarkOf(text)) {
                ImportLoadUnit.KG -> kgMarks++
                ImportLoadUnit.LB -> lbHeaderMarks++
                null -> Unit
            }
        }

        /** Un valor de carga con número; [mark] es la unidad que lleva su celda, si la lleva. */
        fun noteLoadValue(mark: ImportLoadUnit?) {
            numericLoads++
            when (mark) {
                ImportLoadUnit.KG -> kgMarks++
                ImportLoadUnit.LB -> lbLoads++
                null -> Unit
            }
        }

        /** Unidad del archivo: kilos si alguna marca dice kg; si no, libras con cabecera en lb o con la mitad de cargas en lb. */
        fun decideLoadUnit(): ImportLoadUnit = when {
            kgMarks > 0 -> ImportLoadUnit.KG
            lbHeaderMarks > 0 -> ImportLoadUnit.LB
            numericLoads > 0 && lbLoads * 2 >= numericLoads -> ImportLoadUnit.LB
            else -> ImportLoadUnit.KG
        }

        /** Ejecuta [block] como si la fila estuviera en [day]: sus avisos llevan esa semana y ese día. */
        fun <T> inDay(day: DayDraft, block: () -> T): T {
            val savedDay = openDay
            val savedWeek = week
            openDay = day
            week = day.week
            try {
                return block()
            } finally {
                openDay = savedDay
                week = savedWeek
            }
        }

        /** Días de una cuadrícula: uno por columna con día, con el nombre de su cabecera. */
        fun openGridDays(columns: List<Pair<Int, String>>, row: RowContext): List<Pair<Int, DayDraft>> =
            columns.map { (column, name) ->
                column to DayDraft(week, name, row.where, row.source, silent = false).also { days += it }
            }

        fun addToDay(day: DayDraft, exercise: ImportedExercise): ExerciseDraft =
            ExerciseDraft(day, exercise).also { day.exercises += it }

        /** Día de una semana con ese nombre en la hoja en curso; lo crea si no existe. */
        fun sessionDay(week: Int, name: String, row: RowContext): DayDraft =
            days.drop(sheetFirstDay).firstOrNull { it.week == week && it.name == name }
                ?: DayDraft(week, name, row.where, row.source, silent = false).also { days += it }

        /** Quita el día si se quedó sin ejercicios: un grupo que no da ejercicio no deja un día vacío. */
        fun discardIfEmpty(day: DayDraft) {
            if (day.exercises.isEmpty()) days.remove(day)
        }

        /** Día de la columna de día de una tabla: si ya está abierto con ese nombre, la fila sigue en él. */
        fun openTableDay(name: String, row: RowContext) {
            val open = openDay
            if (open != null && ExerciseMatchLexicon.normalize(open.name) == ExerciseMatchLexicon.normalize(name)) return
            startDay(name, silent = false, row)
        }

        /** Aviso de la fila en curso: lleva la semana y el día que estaban abiertos al leerla. */
        fun warn(kind: ImportWarningKind, where: String, sourceText: String, message: String) {
            val pending = PendingWarning(kind, where, sourceText, message, weekIndex = week, day = openDay, exercise = null)
            warnings += pending
            rowWarnings += pending
        }

        /** Aviso de una carga que solo cabe en una unidad: se resuelve en [finish], con la unidad del archivo. */
        fun warnUnitLoad(load: Double, where: String, sourceText: String, message: String) {
            val pending = PendingWarning(
                ImportWarningKind.IGNORED_VALUE, where, sourceText, message,
                weekIndex = week, day = openDay, exercise = null, unitLoad = load,
            )
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
            cyclesStopped = false
        }

        /** Cierra una hoja: los días cortos sin ejercicios cuentan como texto y sale una nota por tipo de ruido. */
        fun endSheet(location: String) {
            repeat(days.drop(sheetFirstDay).count { it.silent && it.exercises.isEmpty() }) { count(Noise.TEXT) }
            if (cyclesStopped) addNote(location, "$location: se leyó el primer ciclo.")
            for (kind in Noise.entries) {
                val total = noise[kind] ?: continue
                addNote(location, noiseMessage(location, kind, total))
            }
        }

        fun noteOmittedSheet(name: String) {
            addNote("Hoja «$name»", "Se omitió la hoja «$name»: parece de instrucciones o de cálculo.")
        }

        /** Nota que el lector dejó sobre una hoja: un recorte de columnas o de filas, o el total de hojas del libro. */
        fun noteReaderTrim(name: String, note: String) {
            addNote("Hoja «$name»", "Hoja «$name»: $note")
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
            // La unidad del archivo decide qué cargas (las que solo caben en una unidad) se conservan y qué avisos salen.
            val unit = decideLoadUnit()
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
                                draft.exercise.copy(
                                    id = exerciseId,
                                    loadKg = draft.exercise.loadKg?.takeIf { loadInRange(it, unit) },
                                )
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
                loadUnit = unit,
                // Un aviso de carga sobrevive solo si la carga no cabe en la unidad del archivo.
                warnings = warnings.filter { it.unitLoad == null || !loadInRange(it.unitLoad, unit) }.mapIndexed { index, pending ->
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
