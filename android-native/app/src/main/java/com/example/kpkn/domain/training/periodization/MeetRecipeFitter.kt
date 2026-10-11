package com.example.kpkn.domain.training.periodization

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.domain.onboarding.CompetitionRules
import com.example.kpkn.domain.training.AuthoredProgressionEngine
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramExecutionContract
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Resultado de ajustar un plan de autor a la fecha de su competición. */
sealed interface MeetFit {
    /** El programa termina en la semana de la competición; [notes] son las líneas que la persona lee en PLAN. */
    data class Fitted(val program: Program, val notes: List<String>) : MeetFit

    /** El plan no se puede ajustar: [reason] es el texto honesto para la persona. El plan se ofrece igual, con esa nota. */
    data class NotFittable(val reason: String) : MeetFit
}

/**
 * Ajusta un plan de autor a la fecha de su competición (puro y determinista: «hoy» llega como parámetro).
 *
 * - Solo se ajustan los planes de [APT_RECIPES]. Su cola (desde la primera semana de realización, pico, taper o descarga
 *   final) queda fija y termina en la semana de la competición.
 * - Si falta tiempo, se quitan semanas de acumulación desde la cabeza, dejando al menos una de intensificación.
 * - Si sobra, se repite la última semana de acumulación (ids propios con sufijo `-r<n>`). La n-ésima repetición sube los kilos
 *   y el % de 1RM de sus series un escalón de +2,5 % (tope +10 %; ver [repeatOf]). Cada [MAX_TRAINING_WEEKS] semanas de
 *   entrenamiento se añade una descarga, sin escalón.
 * - Lo que no cabe sale como [MeetFit.NotFittable] con su texto. El resultado pasa el contrato de ejecución del programa.
 */
object MeetRecipeFitter {

    /** Escalón de carga por cada repetición de acumulación (+2,5 %) y número máximo de escalones (tope +10 %). */
    private const val REPEAT_LOAD_STEP = 0.025
    private const val MAX_REPEAT_STEPS = 4

    /** Los % de 1RM se redondean a medio punto y no pasan del 100 %. */
    private const val PERCENT_STEP = 0.5
    private const val MAX_PERCENT = 100.0

    /** Planes de autor que se ajustan (id de receta, de protocolo o de catálogo). */
    private val APT_RECIPES = setOf("kpkn-native-sbd-4", "candito-6", "cube-method", "calgary-16", "tsa-9", "gzcl-uhf-9")

    /** Planes que se repiten en ciclos o no tienen pico: no se ajustan. */
    private val CYCLE_RECIPES = setOf(
        "madcow-5x5", "nsuns-531-lp-4d", "gzclp", "westside-conjugate", "texas-method-3d", "texas-method-4d",
        "phul-verified", "phat-verified", "original:phul-ms-2021-r1", "original:phat-biolayne-2016-r1",
        "adapted:phul-kpkn-r1", "adapted:phat-kpkn-r1",
        "smolov-jr", "kpkn-ppl-6", "kpkn-rp-style",
    )

    private const val PROTOCOL_PREFIX = "protocol:"
    private const val MAX_TRAINING_WEEKS = 6
    private const val DELOAD_SETS_CUT = 2

    private const val CYCLE_TEXT = "Este programa se repite en ciclos y no tiene pico: no se ajusta a tu fecha."
    private const val GENERIC_TEXT = "Este programa no se ajusta a tu fecha: lo ofrecemos tal como está."
    private const val NO_ACCUMULATION_TEXT = "Este programa no tiene semanas de acumulación que repetir para llegar a tu fecha."
    private const val INVALID_TEXT = "No podemos ajustar este programa a tu fecha sin romper su estructura."

    private val KNOWN_PHASES = setOf(
        BlockGoal.ACCUMULATION, BlockGoal.INTENSIFICATION, BlockGoal.REALIZATION,
        BlockGoal.PEAK, BlockGoal.TAPER, BlockGoal.DELOAD,
    )
    private val HEAD_PHASES = setOf(BlockGoal.ACCUMULATION, BlockGoal.INTENSIFICATION, BlockGoal.DELOAD)
    private val TAIL_PHASES = setOf(BlockGoal.REALIZATION, BlockGoal.PEAK, BlockGoal.TAPER, BlockGoal.DELOAD)

    /** Serialización solo para renombrar los ids de una semana copiada (todos sus niveles, sin lista a mano). */
    private val JSON = Json

    /**
     * Ajusta [program] para que su última semana sea la de [meet]. [anchor] es «hoy» y [weekStartDay] el día que empieza cada
     * semana (1 = lunes … 7 = domingo). Si ya termina allí, devuelve el mismo programa con su nota.
     */
    fun fit(program: Program, meet: LocalDate, anchor: LocalDate, weekStartDay: Int): MeetFit {
        val ids = listOfNotNull(
            program.sourceRecipe?.id,
            program.sourceProtocolId,
            program.structureTemplateId?.removePrefix(PROTOCOL_PREFIX),
            program.planProvenance?.planId,
        )
        if (ids.none { it in APT_RECIPES }) {
            return MeetFit.NotFittable(if (ids.any { it in CYCLE_RECIPES }) CYCLE_TEXT else GENERIC_TEXT)
        }
        val macrocycle = program.macrocycles.singleOrNull() ?: return MeetFit.NotFittable(GENERIC_TEXT)
        val slots = slotsOf(program, macrocycle) ?: return MeetFit.NotFittable(GENERIC_TEXT)

        var tailStart = slots.size
        while (tailStart > 0 && slots[tailStart - 1].phase in TAIL_PHASES) tailStart--
        val head = slots.subList(0, tailStart)
        val tail = slots.subList(tailStart, slots.size)
        if (head.any { it.phase !in HEAD_PHASES }) return MeetFit.NotFittable(GENERIC_TEXT)
        if (tail.none { it.phase == BlockGoal.PEAK || it.phase == BlockGoal.TAPER }) return MeetFit.NotFittable(CYCLE_TEXT)

        val minWeeks = tail.size + 1
        val weeks = weeksUntil(anchor, meet, weekStartDay)
        if (weeks < minWeeks) {
            return MeetFit.NotFittable("Este programa necesita al menos $minWeeks semanas hasta tu competición.")
        }
        val headWeeks = weeks - tail.size
        val notes = listOf(meetLine(meet))
        if (headWeeks == head.size) return MeetFit.Fitted(program, notes)

        val newHead = if (headWeeks < head.size) {
            trimmed(head, headWeeks)
        } else {
            extended(head, headWeeks - head.size) ?: return MeetFit.NotFittable(NO_ACCUMULATION_TEXT)
        }
        val result = withStartCursor(
            withBlocks(program, macrocycle, newHead + tail).copy(planProvenance = adaptedProvenance(program.planProvenance)),
        )
        if (ProgramExecutionContract.validate(result).isNotEmpty()) return MeetFit.NotFittable(INVALID_TEXT)
        return MeetFit.Fitted(result, notes)
    }

    /**
     * El cursor de la ejecución (`runState`) vuelve al inicio del plan si apunta a un bloque, mesociclo o semana que el ajuste
     * ha partido o quitado. Un plan recién armado empieza en su primera semana.
     */
    private fun withStartCursor(program: Program): Program {
        val run = program.runState ?: return program
        val blocks = program.macrocycles.flatMap { it.blocks }
        val first = blocks.firstOrNull() ?: return program
        val firstMeso = first.mesocycles.firstOrNull() ?: return program
        val blockIds = blocks.map { it.id }.toSet()
        val mesoIds = blocks.flatMap { block -> block.mesocycles.map { it.id } }.toSet()
        val weekIds = blocks.flatMap { block -> block.mesocycles.flatMap { meso -> meso.weeks.map { it.id } } }.toSet()
        val stale = run.blockId?.let { it !in blockIds } == true ||
            run.mesocycleId?.let { it !in mesoIds } == true ||
            run.weekId?.let { it !in weekIds } == true
        if (!stale) return program
        return program.copy(
            runState = run.copy(
                blockId = run.blockId?.let { first.id },
                mesocycleId = run.mesocycleId?.let { firstMeso.id },
                weekId = run.weekId?.let { firstMeso.weeks.firstOrNull()?.id },
            ),
        )
    }

    /**
     * Procedencia del plan ajustado: ya no es el original fiel, así que su clase pasa a «Adaptación» (la ficha la muestra). La
     * fuente y la autoría se conservan.
     */
    private fun adaptedProvenance(provenance: PlanProvenance?): PlanProvenance =
        (provenance ?: PlanProvenance()).copy(category = PlanProvenanceClass.ADAPTED)

    /** Línea de PLAN de un programa que termina en la competición. */
    fun meetLine(meet: LocalDate): String = "Termina el ${CompetitionRules.dateLabel(meet)}, el día de tu competición."

    /** Una semana con su fase de la receta y el bloque del que sale; [inserted] marca las repetidas y las descargas. */
    private class Slot(val week: ProgramWeek, val phase: BlockGoal, val block: Block, val inserted: Boolean = false)

    /**
     * Semanas en orden con su fase. La fase sale de la receta del autor (la semana se enlaza por su `progressionIndex`) y,
     * si falta, del bloque. Un bloque puede mezclar fases (p. ej. Cube, con una sola receta de bloque): por eso no se usa solo.
     */
    private fun slotsOf(program: Program, macrocycle: Macrocycle): List<Slot>? {
        val goals = program.sourceRecipe?.weeks?.associate { it.weekNumber to it.blockGoal }.orEmpty()
        val slots = ArrayList<Slot>()
        for (block in macrocycle.blocks) {
            for (meso in block.mesocycles) {
                for (week in meso.weeks) {
                    val phase = week.progressionIndex?.let(goals::get) ?: block.goal ?: return null
                    if (phase !in KNOWN_PHASES) return null
                    slots.add(Slot(week, phase, block))
                }
            }
        }
        return slots
    }

    /** Quita semanas de la cabeza: primero de acumulación, luego descargas e intensificación; deja la última de intensificación. */
    private fun trimmed(head: List<Slot>, keep: Int): List<Slot> {
        val protectedIndex = head.indexOfLast { it.phase == BlockGoal.INTENSIFICATION }
            .takeIf { it >= 0 }
            ?: head.indexOfLast { it.phase == BlockGoal.ACCUMULATION }.takeIf { it >= 0 }
            ?: head.lastIndex
        val candidates = listOf(BlockGoal.ACCUMULATION, BlockGoal.DELOAD, BlockGoal.INTENSIFICATION)
            .flatMap { phase -> head.indices.filter { it != protectedIndex && head[it].phase == phase } }
        val removed = candidates.take(head.size - keep).toSet()
        return head.filterIndexed { index, _ -> index !in removed }
    }

    /**
     * Añade [extra] semanas a la cabeza: copias de la última semana de acumulación, con una descarga cada
     * [MAX_TRAINING_WEEKS] semanas de entrenamiento seguidas (nunca al final de lo añadido). El escalón de carga cuenta solo
     * las repeticiones: una descarga no lo reinicia. Null sin acumulación que repetir.
     */
    private fun extended(head: List<Slot>, extra: Int): List<Slot>? {
        val last = head.indexOfLast { it.phase == BlockGoal.ACCUMULATION }
        if (last < 0) return null
        val source = head[last]
        var since = 0
        for (index in last downTo 0) {
            if (head[index].phase == BlockGoal.DELOAD) break
            since++
        }
        val added = ArrayList<Slot>()
        var left = extra
        // Repeticiones hechas hasta ahora: su número marca el escalón de carga. Las descargas no lo cuentan.
        var repeats = 0
        while (left > 0) {
            val suffix = "-r${added.size + 1}"
            if (since >= MAX_TRAINING_WEEKS && left > 1) {
                added.add(Slot(deloadOf(source.week, suffix), BlockGoal.DELOAD, source.block, inserted = true))
                since = 0
            } else {
                repeats++
                added.add(Slot(repeatOf(source.week, suffix, repeats), BlockGoal.ACCUMULATION, source.block, inserted = true))
                since++
            }
            left--
        }
        return head.subList(0, last + 1) + added + head.subList(last + 1, head.size)
    }

    /** Agrupa las semanas en bloques: una racha de la misma fase y del mismo bloque de origen (o las añadidas). */
    private fun withBlocks(program: Program, macrocycle: Macrocycle, slots: List<Slot>): Program {
        val runs = ArrayList<MutableList<Slot>>()
        for (slot in slots) {
            val run = runs.lastOrNull()
            if (run != null && continues(run.last(), slot)) run.add(slot) else runs.add(mutableListOf(slot))
        }
        val blocks = runs.mapIndexed { index, run ->
            val origin = run.first().block
            val wholeBlock = run.none { it.inserted } &&
                run.map { it.week.id } == origin.mesocycles.flatMap { it.weeks }.map { it.id }
            if (wholeBlock) origin else blockOf(program, index + 1, run.first().phase, run.map { it.week }, origin)
        }
        return program.copy(macrocycles = listOf(macrocycle.copy(blocks = blocks)))
    }

    private fun continues(previous: Slot, slot: Slot): Boolean =
        previous.phase == slot.phase && previous.inserted == slot.inserted && (slot.inserted || previous.block === slot.block)

    /** Bloque nuevo para una racha; conserva la procedencia del bloque de origen. */
    private fun blockOf(program: Program, number: Int, phase: BlockGoal, weeks: List<ProgramWeek>, origin: Block): Block {
        val id = "${program.id}-fit-$number"
        return origin.copy(
            id = id,
            name = labelOf(phase),
            goal = phase,
            mesocycles = listOf(
                Mesocycle(id = "$id-m", name = labelOf(phase), goal = mesocycleGoalOf(phase), weeks = weeks),
            ),
        )
    }

    /**
     * Repetición de una semana de acumulación, con ids propios. La [n]-ésima repetición sube los kilos y el % de 1RM de sus
     * series un escalón de [REPEAT_LOAD_STEP] (hasta [MAX_REPEAT_STEPS] escalones), y el nombre lo dice: «repetida, +2,5 %».
     */
    private fun repeatOf(week: ProgramWeek, suffix: String, n: Int): ProgramWeek {
        val steps = n.coerceAtMost(MAX_REPEAT_STEPS)
        val factor = 1.0 + REPEAT_LOAD_STEP * steps
        return scaledLoads(renamedWeek(week, suffix), factor)
            .copy(name = "${week.name} (repetida, +${percentLabel(REPEAT_LOAD_STEP * steps)} %)")
    }

    /** Porcentaje para el nombre de una repetición, con coma decimal: «2,5», «5», «7,5» o «10». */
    private fun percentLabel(fraction: Double): String {
        val tenths = (fraction * 1000.0).roundToInt()
        val decimal = tenths % 10
        return if (decimal == 0) "${tenths / 10}" else "${tenths / 10},$decimal"
    }

    /** Copia de [week] con el escalón de carga en sus series, en `sessions` y en `parts` (como [deloadOf]). */
    private fun scaledLoads(week: ProgramWeek, factor: Double): ProgramWeek = week.copy(
        sessions = week.sessions.map { session ->
            session.copy(
                exercises = session.exercises.map { scaledExercise(it, factor) },
                parts = session.parts.map { part -> part.copy(exercises = part.exercises.map { scaledExercise(it, factor) }) },
            )
        },
    )

    private fun scaledExercise(exercise: Exercise, factor: Double): Exercise =
        exercise.copy(sets = exercise.sets.map { scaledSet(it, factor) })

    /**
     * Escala los kilos ([ExerciseSet.weight]) y el % de 1RM ([ExerciseSet.targetPercentageRM]) de una serie de carga externa
     * ([LoadModeV2.LOAD], que es el valor por defecto). Nunca baja del valor de origen.
     * - Asistido, lastre y peso corporal no se tocan: en asistido el peso es la ayuda, y subirla facilitaría la serie.
     * - [ExerciseSet.plannedTargetV2] no se toca: es la meta de la unidad de la serie (reps, segundos, metros o unidad
     *   propia), no kilos. Escalarla alteraría repeticiones o tiempos.
     * - RPE, RIR, reps, series y los kilos técnicos o consolidados no cambian.
     */
    private fun scaledSet(set: ExerciseSet, factor: Double): ExerciseSet {
        if ((set.loadModeV2 ?: LoadModeV2.LOAD) != LoadModeV2.LOAD) return set
        val weight = set.weight?.takeIf { it > 0.0 }?.let { kg -> maxOf(roundLoadKg(kg * factor), kg) } ?: set.weight
        val percent = set.targetPercentageRM?.takeIf { it > 0.0 }?.let { pct ->
            minOf(maxOf(roundToStep(pct * factor, PERCENT_STEP), pct), MAX_PERCENT)
        } ?: set.targetPercentageRM
        return set.copy(weight = weight, targetPercentageRM = percent)
    }

    /** Kilos al paso de redondeo del motor de planes de autor sin inventario: medio kilo (`AuthoredProgressionEngine`). */
    private fun roundLoadKg(kg: Double): Double = roundToStep(kg, AuthoredProgressionEngine.roundingStepKg(inventory = null))

    /** Múltiplo de [step] más cercano a [value]; la mitad sube (igual que `AuthoredProgressionEngine.incrementedTm`). */
    private fun roundToStep(value: Double, step: Double): Double = floor(value / step + 0.5) * step

    /** Descarga: la semana de acumulación con dos series menos por ejercicio (como mínimo, una). */
    private fun deloadOf(week: ProgramWeek, suffix: String): ProgramWeek {
        val renamed = renamedWeek(week, suffix)
        return renamed.copy(
            name = "Descarga",
            executionKind = WeekExecutionKind.DELOAD,
            sessions = renamed.sessions.map { session ->
                session.copy(
                    exercises = session.exercises.map(::withFewerSets),
                    parts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::withFewerSets)) },
                )
            },
        )
    }

    private fun withFewerSets(exercise: Exercise): Exercise =
        if (exercise.sets.isEmpty()) exercise
        else exercise.copy(sets = exercise.sets.take((exercise.sets.size - DELOAD_SETS_CUT).coerceAtLeast(1)))

    /**
     * Copia de [week] con todos sus ids (la semana, sesiones, ejercicios, series, partes, supersets…) sufijados con [suffix].
     * Se renombra cada cadena que coincide con un id de la propia semana, así las referencias internas siguen apuntando bien.
     */
    private fun renamedWeek(week: ProgramWeek, suffix: String): ProgramWeek {
        val tree = JSON.encodeToJsonElement(ProgramWeek.serializer(), week)
        val ids = HashSet<String>()
        collectIds(tree, ids)
        return JSON.decodeFromJsonElement(ProgramWeek.serializer(), suffixed(tree, ids, suffix))
    }

    private fun collectIds(element: JsonElement, out: MutableSet<String>) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                // Un id vacío (valor por defecto de algunos modelos) no es un id: renombrarlo tocaría otros textos vacíos.
                if (key == "id" && value is JsonPrimitive && value.isString && value.content.isNotEmpty()) out.add(value.content)
                collectIds(value, out)
            }
            is JsonArray -> element.forEach { collectIds(it, out) }
            is JsonPrimitive -> Unit
        }
    }

    private fun suffixed(element: JsonElement, ids: Set<String>, suffix: String): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (_, value) -> suffixed(value, ids, suffix) })
        is JsonArray -> JsonArray(element.map { suffixed(it, ids, suffix) })
        is JsonPrimitive ->
            if (element.isString && element.content in ids) JsonPrimitive(element.content + suffix) else element
    }

    private fun labelOf(phase: BlockGoal): String = when (phase) {
        BlockGoal.ACCUMULATION -> "Acumulación"
        BlockGoal.INTENSIFICATION -> "Intensificación"
        BlockGoal.REALIZATION -> "Realización"
        BlockGoal.PEAK -> "Pico"
        BlockGoal.TAPER -> "Puesta a punto"
        BlockGoal.DELOAD -> "Descarga"
        else -> "Bloque"
    }

    private fun mesocycleGoalOf(phase: BlockGoal): MesocycleGoal = when (phase) {
        BlockGoal.ACCUMULATION -> MesocycleGoal.ACCUMULATION
        BlockGoal.INTENSIFICATION -> MesocycleGoal.INTENSIFICATION
        BlockGoal.REALIZATION, BlockGoal.PEAK -> MesocycleGoal.REALIZATION
        else -> MesocycleGoal.DELOAD
    }

    /** Semanas desde la semana del ancla hasta la de la competición, ambas incluidas (la misma cuenta que [PeriodLayout]). */
    private fun weeksUntil(anchor: LocalDate, meet: LocalDate, weekStartDay: Int): Int {
        val first = ProgramCalendarEngine.alignToWeekStart(anchor, weekStartDay)
        val last = ProgramCalendarEngine.alignToWeekStart(meet, weekStartDay)
        return (ChronoUnit.DAYS.between(first, last) / 7).toInt() + 1
    }
}
