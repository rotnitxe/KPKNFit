package com.example.kpkn.screens.sessioneditor.design

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.training.NativeProgressionText
import com.example.kpkn.domain.training.RepRangeParser
import com.example.kpkn.screens.sessioneditor.components.DropSetPlanDefaults
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.roundToMax2Decimals
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknSetCell
import com.example.kpkn.ui.design.KpknSetColumn
import com.example.kpkn.ui.design.KpknSetColumns
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSetKind
import com.example.kpkn.ui.design.KpknSetRow

/**
 * Lógica pura de la tabla de series del ejercicio desplegado (SE3b). Sin Android ni estado: traduce las series a filas de
 * [com.example.kpkn.ui.design.KpknSetTable] y escribe cada celda con las mismas reglas que la fila de hoy (InlineSetRow y su menú de
 * intensidad, y el chip Drop-set de InlineSetRowTechniqueChips).
 */

/** Intensidad que enseña la tabla: RIR, RPE o % del 1RM (modo RM). */
internal enum class SessionSetIntensity { RIR, RPE, PERCENT }

/** Columna «Descanso» (la última de [KpknSetColumns.time]): su cabecera y su peso son los de la tabla. */
private val REST_COLUMN: KpknSetColumn = KpknSetColumns.time.last()

/**
 * Intensidad de la tabla. El modo RM es «%». En los demás, RIR solo si todas las series de trabajo lo llevan; si no, RPE, que es lo
 * que escriben las series nuevas.
 */
internal fun sessionSetIntensity(exercise: Exercise): SessionSetIntensity {
    if (exercise.trainingMode == TrainingMode.RM) return SessionSetIntensity.PERCENT
    val working = exercise.sets.filterNot { it.isEmptySlot }
    val allRir = working.isNotEmpty() && working.all { it.intensityMode == IntensityMode.RIR }
    return if (allRir) SessionSetIntensity.RIR else SessionSetIntensity.RPE
}

/**
 * Columnas de la tabla para el modo del ejercicio: RIR, RPE, % (RM), tiempo, distancia o la unidad personalizada. Con [withRest] va el
 * descanso al final; sin él (miembros de una superserie, que descansan por la superserie) se quita.
 */
internal fun sessionSetColumns(exercise: Exercise, withRest: Boolean): List<KpknSetColumn> {
    val core: List<KpknSetColumn> = when (exercise.trainingMode) {
        TrainingMode.TIME -> KpknSetColumns.time
        TrainingMode.DISTANCE -> KpknSetColumns.distance
        TrainingMode.CUSTOM -> {
            val unit = exercise.customUnit?.trim()?.takeIf { it.isNotEmpty() } ?: "Unidad"
            listOf(KpknSetColumn(key = "value", label = unit, weight = 1.2f, spoken = unit))
        }
        TrainingMode.RM -> KpknSetColumns.percent
        else -> when (sessionSetIntensity(exercise)) {
            SessionSetIntensity.RIR -> KpknSetColumns.rir
            else -> KpknSetColumns.rpe
        }
    }
    val withoutRest = core.filterNot { it.key == "rest" }
    val columns = if (withRest) withoutRest + REST_COLUMN else withoutRest
    return columns.map { column -> column.withTableWeight() }
}

/**
 * Peso de cada columna en esta tabla (SE3b). La cifra de kg va algo más ancha y el descanso tiene sitio para su cabecera «Descanso» al
 * 130 % de letra; reps cabe con «6–8», y RIR y RPE, que solo llevan una cifra, son las más estrechas.
 */
private fun KpknSetColumn.withTableWeight(): KpknSetColumn = when (key) {
    "kg" -> copy(weight = 1.3f)
    "reps" -> copy(weight = 0.9f)
    "rir", "rpe" -> copy(weight = 0.8f)
    "rest" -> copy(weight = 1.9f)
    else -> this
}

/**
 * Carga sugerida como texto (SE3b): al medio kilo y pegada al «≈», para que una cifra de tres dígitos quepa al 130 % de letra. Es una
 * estimación, y por eso va con el símbolo.
 */
private fun estimateKgText(kg: Double): String {
    val halfKilos = kotlin.math.round(kg * 2.0) / 2.0
    return "≈" + NativeProgressionText.formatKg(halfKilos)
}

/**
 * Etiqueta de cada serie: «1», «2», «3»… las de trabajo (las ranuras vacías también ocupan su número, porque son rondas de una
 * superserie) y «D» las de drop. Las aproximaciones no están aquí: van en `warmupSets` y llevan «A».
 */
internal fun sessionSetLabels(sets: List<ExerciseSet>): Map<String, String> {
    var work = 0
    val labels = LinkedHashMap<String, String>()
    sets.forEach { set ->
        labels[set.id] = if (set.isDropSet) {
            "D"
        } else {
            work += 1
            work.toString()
        }
    }
    return labels
}

/**
 * Traduce las series del ejercicio a filas de la tabla (SE3b). Primero van las aproximaciones (`warmupSets`), con «A» y solo lectura;
 * luego las series en su orden. Sin dato, la celda queda vacía (la pieza pinta «–»). [predictedKg] es la carga sugerida de cada serie
 * (null si no hay referencia). [input] dice cómo se edita cada celda de una serie; por defecto, solo lectura.
 */
internal fun toKpknSetRows(
    exercise: Exercise,
    columns: List<KpknSetColumn>,
    predictedKg: Map<String, Double?>,
    input: (set: ExerciseSet, columnKey: String, current: String) -> KpknCellInput = { _, _, _ -> KpknCellInput.ReadOnly },
): List<KpknSetRow> {
    val restText = exercise.restTime?.let { KpknSetFormat.rest(it) }.orEmpty()
    val labels = sessionSetLabels(exercise.sets)
    // La aproximación se sugiere sobre la primera serie de trabajo con carga conocida.
    val referenceKg = exercise.sets.asSequence().filterNot { it.isEmptySlot }.mapNotNull { predictedKg[it.id] }.firstOrNull()

    val approachRows = exercise.warmupSets.map { warmup ->
        val approachKg = referenceKg?.let { it * fractionOf(warmup.percentageOfWorkingWeight) }
        KpknSetRow(
            id = warmup.id,
            label = "A",
            kind = KpknSetKind.APPROACH,
            cells = columns.associate { column ->
                val value = when (column.key) {
                    "kg" -> approachKg?.let { estimateKgText(it) }.orEmpty()
                    "reps" -> warmup.targetReps.toString()
                    "rpe" -> formatEditableNumber(warmup.matchRPE)
                    "rest" -> warmup.restBetween?.let { KpknSetFormat.rest(it) }.orEmpty()
                    else -> ""
                }
                column.key to KpknSetCell(
                    value = value,
                    input = KpknCellInput.ReadOnly,
                    contentDescription = "${column.spoken}, serie de aproximación",
                )
            },
        )
    }

    val workRows = exercise.sets.map { set ->
        val label = labels[set.id].orEmpty()
        val kind = if (set.isDropSet) KpknSetKind.DROP else KpknSetKind.WORK
        val kindSpoken = if (kind == KpknSetKind.DROP) "drop set" else "serie $label"
        KpknSetRow(
            id = set.id,
            label = label,
            kind = kind,
            cells = columns.associate { column ->
                val value = if (column.key == "rest") restText else workValue(exercise, set, column.key, predictedKg[set.id])
                column.key to KpknSetCell(
                    value = value,
                    input = input(set, column.key, value),
                    contentDescription = "${column.spoken}, $kindSpoken",
                )
            },
        )
    }
    return approachRows + workRows
}

/** Lo que se ve en una celda de una serie de trabajo. La carga en kg es explícita solo en modo LOAD; si no, es una estimación con «≈». */
private fun workValue(exercise: Exercise, set: ExerciseSet, key: String, predicted: Double?): String = when (key) {
    "kg" -> {
        val weight = set.weight
        when {
            set.intensityMode == IntensityMode.LOAD && weight != null -> NativeProgressionText.formatKg(weight)
            predicted != null -> estimateKgText(predicted)
            else -> ""
        }
    }
    "reps" -> set.effectiveRepRange()?.let { range ->
        // En AMRAP la serie guarda un mínimo: se muestra el mínimo, como hoy.
        if (set.isAmrap || exercise.trainingMode == TrainingMode.AMRAP) range.min.toString() else range.format()
    }.orEmpty()
    "rir" -> set.targetRIR?.toString().orEmpty()
    "rpe" -> formatEditableNumber(set.targetRPE)
    "percent" -> formatEditableNumber(set.targetPercentageRM)
    "time" -> set.targetDuration?.let { KpknSetFormat.rest(it) }.orEmpty()
    "distance", "value" -> formatEditableNumber(set.plannedTargetV2)
    else -> ""
}

/** Fracción de la carga de trabajo de una aproximación: 0,5 o «50» dan lo mismo. */
private fun fractionOf(percentage: Double): Double = if (percentage > 1.0) percentage / 100.0 else percentage

/**
 * Escribe lo que el usuario escribe en una celda de una serie de trabajo, con las reglas de hoy: kg pasa a modo LOAD; reps acepta un
 * rango («6–8»); RIR y RPE fijan su intensidad; % limpia RPE y RIR; el tiempo acepta «m:ss» o segundos.
 */
internal fun ExerciseSet.withTypedValue(columnKey: String, text: String): ExerciseSet = when (columnKey) {
    "kg" -> copy(weight = text.safeDoubleOrNull(), intensityMode = IntensityMode.LOAD)
    "reps" -> {
        val parsed = RepRangeParser.parse(text)
        copy(
            targetReps = parsed?.max ?: text.safeIntOrNull(),
            targetRepsRange = parsed?.takeIf { range -> range.min != range.max },
        )
    }
    "rir" -> copy(targetRIR = text.safeIntOrNull(), intensityMode = IntensityMode.RIR)
    "rpe" -> copy(targetRPE = text.safeDoubleOrNull(), intensityMode = IntensityMode.RPE)
    "percent" -> copy(
        targetPercentageRM = text.safeDoubleOrNull()?.let { roundToMax2Decimals(it) },
        intensityMode = null,
        targetRPE = null,
        targetRIR = null,
        isFailure = false,
    )
    "time" -> copy(targetDuration = parseDurationSeconds(text))
    "distance", "value" -> copy(plannedTargetV2 = text.safeDoubleOrNull())
    else -> this
}

/** Suma o resta 1 con el arrastre corto de una celda numérica o con TalkBack: repeticiones, RIR o RPE. */
internal fun ExerciseSet.steppedValue(columnKey: String, delta: Int): ExerciseSet = when (columnKey) {
    "reps" -> withTypedValue("reps", ((effectiveRepRange()?.max ?: 0) + delta).coerceAtLeast(1).toString())
    "rir" -> withTypedValue("rir", ((targetRIR ?: 0) + delta).coerceIn(0, 10).toString())
    "rpe" -> withTypedValue("rpe", ((targetRPE ?: DEFAULT_RPE) + delta).coerceIn(1.0, 10.0).toString())
    else -> this
}

/** Duración en segundos desde «m:ss» («1:30» son 90) o desde un número de segundos («45»). Null si no se entiende. */
internal fun parseDurationSeconds(text: String): Int? {
    val trimmed = text.trim()
    if (!trimmed.contains(':')) return trimmed.toIntOrNull()
    val parts = trimmed.split(':')
    if (parts.size != 2) return null
    val minutes = parts[0].trim().toIntOrNull() ?: return null
    val seconds = parts[1].trim().toIntOrNull() ?: return null
    return minutes * 60 + seconds
}

/** Valor por defecto de RPE al pasar una serie a RPE, el mismo de la fila de hoy. */
private const val DEFAULT_RPE = 8.0

/** Valor por defecto de RIR al pasar una serie a RIR, el mismo de la fila de hoy. */
private const val DEFAULT_RIR = 2

/**
 * Tipo «Drop» de una serie: la misma escritura que el chip Drop-set de InlineSetRowTechniqueChips al encenderse. [techniqueId] es el id
 * de la técnica nueva.
 */
internal fun ExerciseSet.asDropSet(techniqueId: String): ExerciseSet {
    val drops = DropSetPlanDefaults.DefaultDrops
    val technique = PlannedTechnique(
        id = techniqueId,
        type = TechniqueType.DROP_SET,
        params = mapOf(
            "weightPcts" to DropSetPlanDefaults.weightPctsFor(drops),
            "count" to drops.toString(),
            "betweenMarked" to "true",
        ),
    )
    return copy(
        plannedIntensityTechniques = plannedIntensityTechniques.filter { it.type != TechniqueType.REST_PAUSE } + technique,
        isDropSet = true,
        isRestPause = false,
        dropSets = emptyList(),
        restPauses = emptyList(),
        restAfterSeconds = null,
    )
}

/** Tipo «Trabajo» de una serie que era drop: quita la técnica, con la misma escritura que el chip Drop-set al apagarse. */
internal fun ExerciseSet.asWorkSet(): ExerciseSet = copy(
    plannedIntensityTechniques = plannedIntensityTechniques.filter { it.type != TechniqueType.DROP_SET },
    isDropSet = false,
    dropSets = emptyList(),
    restAfterSeconds = null,
)

/** Una serie tiene técnica si lleva drop set o rest-pause (el chip «Técnica» de la fila la marca). */
internal fun ExerciseSet.hasTechnique(): Boolean =
    isDropSet || isRestPause || plannedIntensityTechniques.any { it.type == TechniqueType.DROP_SET || it.type == TechniqueType.REST_PAUSE }

/**
 * «Duplicar»: copia la serie justo debajo con [newId]. No duplica ranuras vacías ni ejercicios de una superserie, porque sus rondas
 * van por posición y una ronda nueva desalinearía a los demás miembros.
 */
internal fun Exercise.withDuplicatedSet(setId: String, newId: String): Exercise {
    if (supersetGroupRefOrLegacyId() != null) return this
    val index = sets.indexOfFirst { it.id == setId }
    if (index < 0 || sets[index].isEmptySlot) return this
    val duplicated = sets[index].copy(id = newId)
    return copy(sets = sets.subList(0, index + 1) + duplicated + sets.subList(index + 1, sets.size))
}

/**
 * Intensidad elegida en el segmentado «RIR · RPE · %». RIR y RPE escriben cada serie como el menú de intensidad de hoy y ajustan el modo
 * (solo se conserva REPS, AMRAP y, para RPE, Solo RPE). % pone el modo RM; sus valores se escriben en cada serie.
 */
internal fun Exercise.withIntensity(choice: SessionSetIntensity): Exercise = when (choice) {
    SessionSetIntensity.PERCENT -> copy(trainingMode = TrainingMode.RM)
    SessionSetIntensity.RIR -> copy(
        trainingMode = keepOrReps(trainingMode, setOf(TrainingMode.REPS, TrainingMode.AMRAP)),
        sets = sets.map { set -> if (set.isEmptySlot) set else set.asIntensity(SessionSetIntensity.RIR) },
    )
    SessionSetIntensity.RPE -> copy(
        trainingMode = keepOrReps(trainingMode, setOf(TrainingMode.REPS, TrainingMode.AMRAP, TrainingMode.SOLO_RPE)),
        sets = sets.map { set -> if (set.isEmptySlot) set else set.asIntensity(SessionSetIntensity.RPE) },
    )
}

private fun keepOrReps(current: TrainingMode, keep: Set<TrainingMode>): TrainingMode =
    if (current in keep) current else TrainingMode.REPS

/** Escritura de intensidad de una serie: la del menú de intensidad de InlineSetRow, también en los lados de un unilateral. */
private fun ExerciseSet.asIntensity(choice: SessionSetIntensity): ExerciseSet = when (choice) {
    SessionSetIntensity.RIR -> copy(
        intensityMode = IntensityMode.RIR,
        isFailure = false,
        isAmrap = false,
        targetRIR = targetRIR ?: DEFAULT_RIR,
        targetRPE = null,
        targetPercentageRM = null,
        leftTarget = leftTarget?.let { target -> target.asIntensity(choice) },
        rightTarget = rightTarget?.let { target -> target.asIntensity(choice) },
    )
    SessionSetIntensity.RPE -> copy(
        intensityMode = IntensityMode.RPE,
        isFailure = false,
        isAmrap = false,
        targetRPE = targetRPE ?: DEFAULT_RPE,
        targetRIR = null,
        targetPercentageRM = null,
        leftTarget = leftTarget?.let { target -> target.asIntensity(choice) },
        rightTarget = rightTarget?.let { target -> target.asIntensity(choice) },
    )
    SessionSetIntensity.PERCENT -> this
}

private fun UnilateralTarget.asIntensity(choice: SessionSetIntensity): UnilateralTarget = when (choice) {
    SessionSetIntensity.RIR -> copy(intensityMode = IntensityMode.RIR, targetRIR = targetRIR ?: DEFAULT_RIR, targetRPE = null)
    SessionSetIntensity.RPE -> copy(intensityMode = IntensityMode.RPE, targetRPE = targetRPE ?: DEFAULT_RPE, targetRIR = null)
    SessionSetIntensity.PERCENT -> this
}

/** Índice del segmentado «Unilateral»: 0 ambos lados, 1 alterno (lados iguales), 2 por lados (lados aparte). */
internal fun Exercise.unilateralChoiceIndex(): Int = when {
    !isEffectivelyUnilateral() -> 0
    unilateralIntensityMode == UnilateralIntensityMode.INDEPENDENT -> 2
    else -> 1
}

/**
 * Elige «Ambos lados» (0), «Alterno» (1) o «Por lados» (2). Alterno y Por lados dejan el ejercicio unilateral con lados iguales o
 * aparte; bilateral apaga la unilateralidad con la misma función de siempre.
 */
internal fun Exercise.withUnilateralChoice(index: Int): Exercise = when (index) {
    0 -> if (isEffectivelyUnilateral()) toggledBilateralUnilateral() else this
    else -> {
        val unilateral = if (isEffectivelyUnilateral()) this else toggledBilateralUnilateral()
        unilateral.copy(
            unilateralIntensityMode = if (index == 2) UnilateralIntensityMode.INDEPENDENT else UnilateralIntensityMode.SHARED,
        )
    }
}

/** Orden de los lados: 0 izquierda primero (L/R), 1 derecha primero (R/L). */
internal fun Exercise.withSideOrder(index: Int): Exercise = copy(
    unilateralSideOrder = if (index == 1) UnilateralSideOrder.RIGHT_LEFT else UnilateralSideOrder.LEFT_RIGHT,
)
