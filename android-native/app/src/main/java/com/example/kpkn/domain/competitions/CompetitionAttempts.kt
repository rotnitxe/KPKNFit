package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.PlannedAttempt
import kotlin.math.log10
import kotlin.math.pow

/** Un intento de la tabla con su fila: lo que pintan c-log, c-today y c-detail. */
data class AttemptCell(val block: CompetitionTechnicalBlock, val attempt: CompetitionAttempt)

/**
 * La tabla de intentos de Registros v4: un toque alterna válido ↔ nulo (c-log), cada toque del modo competición guarda (c-today) y los
 * pesos salen del plan o de tus marcas (adiós al relleno de 100 kg).
 */
object CompetitionAttempts {
    /** Un toque: pendiente, nulo o saltado → válido; válido → nulo. Ya no hay ciclo de cuatro estados (C-S9). */
    fun toggled(result: CompetitionAttemptResult): CompetitionAttemptResult =
        if (result == CompetitionAttemptResult.GOOD_LIFT) CompetitionAttemptResult.NO_LIFT else CompetitionAttemptResult.GOOD_LIFT

    fun updateAttempt(record: CompetitionRecord, blockId: String, attemptNumber: Int, transform: (CompetitionAttempt) -> CompetitionAttempt): CompetitionRecord =
        record.copy(technicalBlocks = record.technicalBlocks.map { block ->
            if (block.id != blockId) block else block.copy(attempts = block.attempts.map { if (it.attemptNumber == attemptNumber) transform(it) else it })
        })

    fun toggle(record: CompetitionRecord, blockId: String, attemptNumber: Int): CompetitionRecord =
        updateAttempt(record, blockId, attemptNumber) { it.copy(resultType = toggled(it.resultType)) }

    fun setResult(record: CompetitionRecord, blockId: String, attemptNumber: Int, result: CompetitionAttemptResult): CompetitionRecord =
        updateAttempt(record, blockId, attemptNumber) { it.copy(resultType = result) }

    fun setWeight(record: CompetitionRecord, blockId: String, attemptNumber: Int, kg: Double?): CompetitionRecord =
        updateAttempt(record, blockId, attemptNumber) { it.copy(weightKg = kg?.takeIf { w -> w.isFinite() && w > 0 }) }

    /** Las filas de la tabla en el orden de la competencia y sus intentos 1–3. */
    fun cells(record: CompetitionRecord, discipline: CompetitionDiscipline): List<AttemptCell> {
        val seeds = CompetitionDraft.loadSeeds(discipline)
        val blocks = if (seeds.isEmpty()) record.technicalBlocks else record.technicalBlocks.take(seeds.size)
        return blocks.flatMap { block -> block.attempts.sortedBy { it.attemptNumber }.map { AttemptCell(block, it) } }
    }

    /** Las filas de la tabla (bloques) de la disciplina. */
    fun rows(record: CompetitionRecord, discipline: CompetitionDiscipline): List<CompetitionTechnicalBlock> {
        val seeds = CompetitionDraft.loadSeeds(discipline)
        return if (seeds.isEmpty()) record.technicalBlocks else record.technicalBlocks.take(seeds.size)
    }

    /** El peso que propone el plan para un intento: el planificado o, sin plan, la estrategia sobre tus marcas. */
    fun proposedWeight(record: CompetitionRecord, movement: CompetitionMovementType, attemptNumber: Int, marks: Map<CompetitionMovementType, Double>): Double? {
        record.plannedAttempts.firstOrNull { it.movementType == movement && it.attemptNumber == attemptNumber }?.let { return it.weightKg }
        val max = marks[movement] ?: return null
        val strategy = AttemptStrategy.entries.firstOrNull { it.name == record.attemptStrategy } ?: AttemptStrategy.TARGET
        return AttemptPlanner.plan(movement, max, strategy).firstOrNull { it.attemptNumber == attemptNumber }?.weightKg
    }

    /** Rellena los pesos vacíos con el plan o tus marcas; lo escrito se respeta. */
    fun prefill(record: CompetitionRecord, discipline: CompetitionDiscipline, marks: Map<CompetitionMovementType, Double>): CompetitionRecord {
        val ensured = CompetitionDraft.ensureBlocks(record, discipline)
        return ensured.copy(technicalBlocks = ensured.technicalBlocks.mapIndexed { index, block ->
            if (index >= CompetitionDraft.loadSeeds(discipline).size) block else block.copy(attempts = block.attempts.map { attempt ->
                if (attempt.weightKg != null) attempt else attempt.copy(weightKg = proposedWeight(record, block.movementType, attempt.attemptNumber, marks))
            })
        })
    }

    /** El intento que toca en el modo competición: el primero sin decidir. */
    fun current(record: CompetitionRecord, discipline: CompetitionDiscipline): AttemptCell? =
        cells(record, discipline).firstOrNull { it.attempt.resultType == CompetitionAttemptResult.PENDING }

    /**
     * Decide el intento actual del modo competición y prepara el siguiente de la misma fila con la regla de hoy (MeetDayPlan): si
     * entra, el planificado; si no, se repite el peso.
     */
    fun decide(record: CompetitionRecord, discipline: CompetitionDiscipline, result: CompetitionAttemptResult): CompetitionRecord {
        val cell = current(record, discipline) ?: return record
        val decided = setResult(record, cell.block.id, cell.attempt.attemptNumber, result)
        val n = cell.attempt.attemptNumber
        if (n >= 3) return decided
        val next = cell.block.attempts.firstOrNull { it.attemptNumber == n + 1 } ?: return decided
        if (next.resultType != CompetitionAttemptResult.PENDING) return decided
        val planned = record.plannedAttempts.firstOrNull { it.movementType == cell.block.movementType && it.attemptNumber == n + 1 }?.weightKg
        val weight = when (result) {
            CompetitionAttemptResult.NO_LIFT -> cell.attempt.weightKg ?: next.weightKg
            CompetitionAttemptResult.GOOD_LIFT -> planned ?: next.weightKg
            else -> next.weightKg
        }
        return setWeight(decided, cell.block.id, n + 1, weight)
    }

    /** «Si entra: 3.º a 137,5 · si no: repites 132,5»; en el último intento de una fila, «Último intento de press banca». */
    fun nextCaption(record: CompetitionRecord, cell: AttemptCell): String {
        val n = cell.attempt.attemptNumber
        if (n >= 3) return "Último intento de ${cell.block.movementType.liftLabel(cell.block.title).lowercase()}"
        val planned = record.plannedAttempts.firstOrNull { it.movementType == cell.block.movementType && it.attemptNumber == n + 1 }?.weightKg
        val nextWeight = planned ?: cell.block.attempts.firstOrNull { it.attemptNumber == n + 1 }?.weightKg
        val current = cell.attempt.weightKg
        val yes = nextWeight?.let { "Si entra: ${CompetitionFormat.ordinal(n + 1)} a ${CompetitionFormat.kg(it)}" }
        val no = current?.let { "si no: repites ${CompetitionFormat.kg(it)}" }
        return listOfNotNull(yes, no).joinToString(" · ").replaceFirstChar { it.uppercase() }
    }

    /**
     * El calentamiento del siguiente levantamiento (c-today): la rampa de hoy hasta el 92 % de su primer intento. Null si no queda otro
     * levantamiento o no tiene primer intento.
     */
    fun nextLiftWarmup(record: CompetitionRecord, discipline: CompetitionDiscipline, cell: AttemptCell): Pair<String, String>? {
        val rows = rows(record, discipline)
        val index = rows.indexOfFirst { it.id == cell.block.id }
        val nextRow = rows.getOrNull(index + 1) ?: return null
        val attempts = nextRow.attempts.sortedBy { it.attemptNumber }
        val weights = attempts.map { it.weightKg ?: return null }
        if (weights.size != 3 || weights.zipWithNext().any { it.first > it.second }) return null
        val movement = if (nextRow.movementType in AttemptPlanner.lifts) nextRow.movementType else CompetitionMovementType.SQUAT
        val plan = MeetDayPlan.build(weights.mapIndexed { i, kg -> PlannedAttempt(movement, i + 1, kg) }).firstOrNull() ?: return null
        if (plan.warmups.isEmpty()) return null
        val label = nextRow.movementType.liftLabel(nextRow.title).lowercase()
        val article = if (nextRow.movementType == CompetitionMovementType.SQUAT || nextRow.movementType == CompetitionMovementType.SNATCH ||
            nextRow.title.startsWith("Dominada")) "la" else "el"
        val line = plan.warmups.joinToString(" · ") { "${CompetitionFormat.kg(it.weightKg)} × ${it.reps}" }
        return "Calentamiento para $article $label" to line
    }

    /** El mejor válido de una fila, recalculado siempre (C-F6). */
    fun best(block: CompetitionTechnicalBlock): Double? = CompetitionScoring.bestValidKg(block)

    /** El intento más pesado que se probó en una fila (para «135 → 132,5»). */
    fun heaviestTried(block: CompetitionTechnicalBlock): Double? =
        block.attempts.filter { it.resultType == CompetitionAttemptResult.GOOD_LIFT || it.resultType == CompetitionAttemptResult.NO_LIFT }
            .mapNotNull { it.weightKg }.maxOrNull()

    /** Total de la tabla: suma de los mejores válidos de cada fila de la disciplina. Null si no entró ninguno. */
    fun total(record: CompetitionRecord, discipline: CompetitionDiscipline): Double? {
        val sum = rows(record, discipline).sumOf { best(it) ?: 0.0 }
        return sum.takeIf { it > 0.0 }
    }

    /** «Total si entran»: la suma de los terceros intentos del plan (c-plan). */
    fun totalIfAllMake(planned: List<PlannedAttempt>): Double? =
        planned.groupBy { it.movementType }.values.mapNotNull { list -> list.maxByOrNull { it.attemptNumber }?.weightKg }.sum().takeIf { it > 0 }
}

/** Coeficientes Sinclair IWF (ciclo 2021–2024). Total × 10^(A·log10(peso/b)²) por debajo de b; el total tal cual por encima. */
object SinclairPoints {
    private const val MEN_A = 0.722762521
    private const val MEN_B = 193.609
    private const val WOMEN_A = 0.787004341
    private const val WOMEN_B = 153.757

    fun of(totalKg: Double?, bodyweightKg: Double?, female: Boolean): Double? {
        if (totalKg == null || totalKg <= 0 || bodyweightKg == null || bodyweightKg <= 0) return null
        val a = if (female) WOMEN_A else MEN_A
        val b = if (female) WOMEN_B else MEN_B
        if (bodyweightKg >= b) return totalKg
        val x = log10(bodyweightKg / b)
        return totalKg * 10.0.pow(a * x * x)
    }
}
