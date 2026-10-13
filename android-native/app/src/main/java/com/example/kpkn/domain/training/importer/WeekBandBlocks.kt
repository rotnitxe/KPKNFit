package com.example.kpkn.domain.training.importer

/**
 * Calculadoras por bloques bajo filas de semanas en columnas (H6c): «3 @ · 121 · x 6» en tres o cuatro celdas seguidas, con
 * la semana en la columna de su etiqueta («Week 1:», «Weeks 1 & 2», «Week 1: A»…). Puro: `ProgramImportParser` lo usa solo si
 * [plan] da un plan para la hoja; si no, la hoja se lee como siempre.
 */
internal object WeekBandBlocks {

    /** Nombre del ejercicio cuando ninguna fila de encima dice el levantamiento: no se reconoce y sale «Elegir». */
    const val DEFAULT_LIFT = "Levantamiento principal"

    /** Día de un bloque sin etiqueta de fila, de banda ni de sufijo. */
    private const val DEFAULT_DAY = "Día 1"

    /** Bloques mínimos debajo de la fila de semanas para que la hoja sea una calculadora. */
    private const val MIN_BLOCKS = 2

    /** Palabras como mucho en la frase del levantamiento («Bench Press», «Squat»…). */
    private const val MAX_LIFT_WORDS = 4

    /**
     * Plan de la hoja, o null si no es una calculadora de este tipo: hace falta una fila de semanas con dos o más etiquetas
     * y al menos [MIN_BLOCKS] bloques debajo de ella.
     */
    fun plan(rows: List<List<String>>): WeekBandPlan? {
        val cells = rows.map { row -> row.map { it.trim() } }
        val labels = cells.map { weekLabelsOf(it) }
        val blocks = cells.map { blocksOf(it) }
        // Fila de semanas: tiene etiquetas y ningún bloque. La primera con dos o más etiquetas ancla la hoja.
        val anchor = cells.indices.firstOrNull { labels[it].size >= 2 && blocks[it].isEmpty() } ?: return null
        // «Option 2» corta la hoja: lo que va debajo cuenta como etiqueta, sin bloques.
        val option = cells.indices.firstOrNull { it > anchor && OPTION_ROW.containsMatchIn(joined(cells[it])) }
        val end = option ?: cells.size
        if ((anchor + 1 until end).sumOf { blocks[it].size } < MIN_BLOCKS) return null

        val weekRows = (anchor until end).filter { labels[it].isNotEmpty() && blocks[it].isEmpty() }.toSet()
        // Encima de la ancla, las filas sin bloques son texto: título, 1RM, unidad o levantamiento.
        val above = (0 until anchor).filter { blocks[it].isEmpty() }
        val labelRows = above.toMutableSet().apply { if (option != null) addAll(option until cells.size) }

        val planned = HashMap<Int, List<PlannedBlock>>()
        var weekRow = anchor
        var inherited: String? = null
        for (index in anchor + 1 until end) {
            if (index in weekRows) {
                weekRow = index
                inherited = null
            } else if (blocks[index].isNotEmpty()) {
                // La etiqueta de una fila de bloques pasa a las de debajo, mientras sigan bajo la misma fila de semanas.
                val own = dayLabelLeftOf(cells[index], blocks[index].first().first)
                if (own != null) inherited = own
                val rowDay = own ?: inherited
                planned[index] = blocks[index].map { block -> plannedBlock(block, bandLabelOf(labels[weekRow], block), rowDay) }
            }
        }
        return WeekBandPlan(
            weekRows = weekRows,
            labelRows = labelRows,
            blocks = planned,
            lift = liftAbove(cells, above),
            unitHint = unitHintAbove(cells, above),
            oneRm = oneRmAbove(cells, above),
            optionRow = option,
        )
    }

    /** Un bloque con su día: la etiqueta de la fila; si no, la de la banda (si es día) y, si no, «Día 1». */
    private fun plannedBlock(block: RawBlock, band: WeekLabel, rowDay: String?): PlannedBlock {
        val suffixDay = dayOfSuffix(band.suffix)
        return PlannedBlock(
            weeks = band.weeks,
            day = rowDay ?: suffixDay ?: DEFAULT_DAY,
            sets = block.sets,
            setsRange = block.setsRange,
            load = block.load,
            repsMin = block.reps,
            repsMax = block.repsMax ?: block.reps,
            amrapReps = block.amrapReps,
            repsMissing = block.repsMissing,
            bandNote = band.suffix.takeIf { suffixDay == null && it.isNotEmpty() }?.let { "$it." },
        )
    }

    /** Etiquetas de semana de una fila, con su columna, de izquierda a derecha. */
    private fun weekLabelsOf(row: List<String>): List<WeekLabel> = row.indices.mapNotNull { column ->
        val match = WEEK_LABEL.matchEntire(row[column]) ?: return@mapNotNull null
        val from = match.groupValues[1].toInt().coerceAtLeast(1)
        val to = match.groupValues[2].toIntOrNull()?.coerceAtLeast(1)
        val weeks = if (to == null) listOf(from) else (minOf(from, to)..maxOf(from, to)).toList()
        val suffix = match.groupValues[3].trim().trimStart(':', '-', '–', '—', ' ').trim()
        WeekLabel(column, weeks, suffix)
    }

    /** Bloques de una fila, de izquierda a derecha y sin solaparse. */
    private fun blocksOf(row: List<String>): List<RawBlock> {
        val found = ArrayList<RawBlock>()
        var column = 0
        while (column < row.size) {
            val block = blockAt(row, column)
            if (block == null) {
                column++
            } else {
                found += block
                column = block.last + 1
            }
        }
        return found
    }

    /**
     * Un bloque que empieza en [start]: «N @», una o dos cargas (vale la segunda) y «x R», o «x failure» sin número (ese
     * bloque se omite con su aviso, como un AMRAP sin número en una tabla). Null si no es un bloque.
     */
    private fun blockAt(row: List<String>, start: Int): RawBlock? {
        val sets = SETS_CELL.matchEntire(row[start]) ?: return null
        fun cell(offset: Int): String = row.getOrNull(start + offset).orEmpty()
        fun repsAt(offset: Int): Boolean = REPS_CELL.matches(cell(offset)) || REPS_WITHOUT_NUMBER.matches(cell(offset))
        val loads = when {
            NUMBER_CELL.matches(cell(1)) && NUMBER_CELL.matches(cell(2)) && repsAt(3) -> 2
            NUMBER_CELL.matches(cell(1)) && repsAt(2) -> 1
            else -> return null
        }
        val reps = REPS_CELL.matchEntire(cell(loads + 1))
        val load = numberOf(cell(loads)) ?: return null
        val from = sets.groupValues[1].toInt()
        val to = sets.groupValues[2].toIntOrNull()
        val repsTo = reps?.groupValues?.get(2)?.toIntOrNull()
        return RawBlock(
            first = start,
            last = start + loads + 1,
            sets = from,
            setsRange = to?.takeIf { it != from }?.let { minOf(from, it)..maxOf(from, it) },
            load = load,
            reps = reps?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            repsMax = repsTo,
            // «x 6+»: AMRAP con el número que lleva el «+» (el último de un rango).
            amrapReps = if (reps == null || reps.groupValues[3].isEmpty()) null else repsTo ?: reps.groupValues[1].toInt(),
            repsMissing = reps == null,
        )
    }

    /** La etiqueta de día de una fila: la celda con texto más cercana a la izquierda del primer bloque, sin «:». */
    private fun dayLabelLeftOf(row: List<String>, firstColumn: Int): String? {
        val text = (firstColumn - 1 downTo 0).map { row[it] }.firstOrNull { it.isNotEmpty() } ?: return null
        if (WEEK_LABEL.matchEntire(text) != null) return null
        return text.trimEnd(':', '.', '-', '–', '—', '|', ' ').trim().takeIf { it.isNotEmpty() }
    }

    /** La etiqueta de la banda: la que cae dentro de las columnas del bloque; si no, la más cercana por la izquierda; si no, la primera. */
    private fun bandLabelOf(labels: List<WeekLabel>, block: RawBlock): WeekLabel =
        labels.firstOrNull { it.column in block.first..block.last }
            ?: labels.lastOrNull { it.column < block.first }
            ?: labels.first()

    /** Un sufijo de semana que es un día: una letra («A») o una palabra con «day» o «día» («Light Day»). */
    private fun dayOfSuffix(suffix: String): String? = suffix.takeIf {
        it.isNotEmpty() && ((it.length == 1 && it.first().isLetter()) || it.contains("day", ignoreCase = true) || it.contains("día", ignoreCase = true))
    }

    /** Levantamiento: en la primera fila de una sola celda de encima que lo nombre. */
    private fun liftAbove(cells: List<List<String>>, rows: List<Int>): String? = rows.firstNotNullOfOrNull { index ->
        cells[index].filter { it.isNotEmpty() }.singleOrNull()?.let { longestLift(it) }
    }

    /** La frase más larga (de 4 palabras a 1) que reconoce el importador, con el texto tal como está escrito. */
    private fun longestLift(text: String): String? {
        val words = text.split(WHITESPACE).filter { it.isNotEmpty() }
        for (size in minOf(MAX_LIFT_WORDS, words.size) downTo 1) {
            for (start in 0..words.size - size) {
                val phrase = words.subList(start, start + size).joinToString(" ").trim { !it.isLetterOrDigit() }
                if (phrase.isNotEmpty() && ImportExerciseAliases.lookup(phrase) != null) return phrase
            }
        }
        return null
    }

    /** Pista de unidad: «Rounding: 5» o «Enter 2.5 for KG or 5 for LB»: 5 son libras y 2,5 kilos. */
    private fun unitHintAbove(cells: List<List<String>>, rows: List<Int>): UnitHint? = rows.firstNotNullOfOrNull { index ->
        val row = cells[index]
        val label = row.indexOfFirst { UNIT_HINT.containsMatchIn(it) }
        if (label < 0) return@firstNotNullOfOrNull null
        val unit = when (valueAfter(row, label)) {
            5.0 -> ImportLoadUnit.LB
            2.5 -> ImportLoadUnit.KG
            else -> null
        }
        unit?.let { UnitHint(index, it) }
    }

    /** El 1RM o 2RM con el que se calcularon las cargas: «Enter your current 1RM: · 200». */
    private fun oneRmAbove(cells: List<List<String>>, rows: List<Int>): OneRm? = rows.firstNotNullOfOrNull { index ->
        val row = cells[index]
        val label = row.indexOfFirst { CURRENT_RM.containsMatchIn(it) }
        if (label < 0) return@firstNotNullOfOrNull null
        val rm = CURRENT_RM.find(row[label])?.groupValues?.get(1)?.toIntOrNull() ?: return@firstNotNullOfOrNull null
        valueAfter(row, label)?.let { OneRm(it, rm) }
    }

    /** El número al final de la celda [label] o, si no, la primera celda con cifra que va detrás en la fila. */
    private fun valueAfter(row: List<String>, label: Int): Double? {
        TRAILING_NUMBER.find(row[label])?.let { return numberOf(it.groupValues[1]) }
        return row.drop(label + 1).firstOrNull { NUMBER_CELL.matches(it) }?.let { numberOf(it) }
    }

    private fun numberOf(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

    private fun joined(row: List<String>): String = row.filter { it.isNotEmpty() }.joinToString(" ")

    /** Un bloque en su fila, con sus columnas y sus cifras; su semana se decide después. */
    private class RawBlock(
        val first: Int,
        val last: Int,
        val sets: Int,
        val setsRange: IntRange?,
        val load: Double,
        val reps: Int,
        val repsMax: Int?,
        val amrapReps: Int?,
        /** «x failure» y parecidos: el bloque no dice cuántas repeticiones son. */
        val repsMissing: Boolean,
    )

    /** Una etiqueta de semana: su columna, la semana o semanas que cubre y lo que va detrás («A», «Light Day», «unload»). */
    private class WeekLabel(val column: Int, val weeks: List<Int>, val suffix: String)

    /** «Week 1:», «Week 1: A», «Weeks 1 & 2», «Weeks 1-2», «Semana 4: unload»: semana o semanas y el sufijo. */
    private val WEEK_LABEL = Regex(
        """(?:week|weeks|semana|semanas)\s*(\d{1,2})(?:\s*(?:&|y|and|-|–|—)\s*(\d{1,2}))?\s*:?\s*(.*)""",
        RegexOption.IGNORE_CASE,
    )

    /** «3 @», «1-4 @»: series o rango de series. */
    private val SETS_CELL = Regex("""(\d{1,2})(?:\s*[-–—]\s*(\d{1,2}))?\s*@""")

    /** «x 6», «x 5-6», «x 6+», «×8»: repeticiones, rango o AMRAP. */
    private val REPS_CELL = Regex("""[x×]\s*(\d{1,3})(?:\s*[-–—]\s*(\d{1,3}))?\s*(\+)?""", RegexOption.IGNORE_CASE)

    /** «x failure», «x AMRAP», «x max», «x al fallo»: repeticiones sin número. Las mismas palabras que el AMRAP de una tabla. */
    private val REPS_WITHOUT_NUMBER = Regex(
        """[x×]\s*(?:failure|fallo|al\s+fallo|amrap|max\s+reps|max|mr)""",
        RegexOption.IGNORE_CASE,
    )

    /** Una cifra de carga o de pista: «121», «230.445», «2,5». */
    private val NUMBER_CELL = Regex("""\d{1,4}(?:[.,]\d+)?""")

    /** Un número al final de un texto: «Round to: 5». */
    private val TRAILING_NUMBER = Regex("""(\d{1,4}(?:[.,]\d+)?)\s*$""")

    /** Una fila que empieza por «Option 2», «Opción 3»… (la segunda opción o las siguientes). */
    private val OPTION_ROW = Regex("""^(?:option|opci[oó]n)\s*([2-9])\b""", RegexOption.IGNORE_CASE)

    /** «Enter your current 1RM», «Enter your 2RM», «current 1RM»; «Projected new 1RM» no cuenta. */
    private val CURRENT_RM = Regex("""\b(?:current|enter your)\s+(?:current\s+)?([12])\s*rm\b""", RegexOption.IGNORE_CASE)

    /** Una fila que habla de redondeo o de la unidad de las cargas. */
    private val UNIT_HINT = Regex("""\brounding\b|\bround to\b|\bfor kg or\b.*\bfor lb\b""", RegexOption.IGNORE_CASE)

    private val WHITESPACE = Regex("""\s+""")
}

/**
 * Plan de una hoja de calculadora: qué filas decide, con qué bloques y qué datos de la hoja. Las filas de [weekRows] se
 * consumen sin aviso; las de [labelRows] cuentan como etiqueta; las de [blocks] dan ejercicios.
 */
internal class WeekBandPlan(
    /** Filas de semanas, desde la ancla hasta la segunda opción. */
    val weekRows: Set<Int>,
    /** Filas que cuentan como etiqueta sin aviso: las de encima de la ancla sin bloques y las de la segunda opción en adelante. */
    val labelRows: Set<Int>,
    /** Bloques de cada fila de bloques, cada uno con su semana o semanas, su día y sus valores. */
    val blocks: Map<Int, List<PlannedBlock>>,
    /** Levantamiento del título; null si ninguna fila de encima lo nombra (sale [WeekBandBlocks.DEFAULT_LIFT]). */
    val lift: String?,
    /** Pista de unidad («Rounding: 5»), si la hay. */
    val unitHint: UnitHint?,
    /** 1RM o 2RM de la hoja («Enter your current 1RM: · 200»), si la trae. */
    val oneRm: OneRm?,
    /** Fila de la segunda opción («Option 2»), que corta el plan; null si la hoja tiene una sola. */
    val optionRow: Int?,
) {
    /** Las filas que decide el plan; el resto de la hoja se lee como siempre. */
    val ownedRows: Set<Int> = weekRows + labelRows + blocks.keys
}

/** Un bloque con su semana o semanas, su día y sus valores. [bandNote] es el sufijo de la semana que no es un día («unload.»). */
internal class PlannedBlock(
    val weeks: List<Int>,
    val day: String,
    val sets: Int,
    /** Rango de series («1-4 @»): [sets] es el mínimo. Null si la celda trae una sola cifra. */
    val setsRange: IntRange?,
    val load: Double,
    val repsMin: Int,
    val repsMax: Int,
    /** Repeticiones de la última serie con «+» (AMRAP); null si no lo tiene. */
    val amrapReps: Int?,
    /** «x failure» sin número: el bloque se omite con el aviso de AMRAP sin número, como en una tabla. */
    val repsMissing: Boolean,
    val bandNote: String?,
)

/** Pista de unidad de la hoja: la fila donde está y la unidad que dice («Rounding: 5» son libras). */
internal class UnitHint(val row: Int, val unit: ImportLoadUnit)

/** 1RM o 2RM de la hoja, con el que se calcularon las cargas. */
internal class OneRm(val value: Double, val rm: Int)
