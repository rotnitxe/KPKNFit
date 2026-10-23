package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.resolveUnitModeV2
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.screens.sessioneditor.components.cardioDoseLine
import com.example.kpkn.ui.design.KpknSetFormat
import java.util.Locale

/**
 * Dosis de una línea de un ejercicio (SESSION_EDITOR_V1 §2, punto 6): «4 × 6–8 · RIR 2 · 2:30». Función pura, sin Android.
 *
 * - Cuenta solo las series de trabajo: las ranuras vacías no cuentan y las series de aproximación van en `warmupSets`, fuera de [sets].
 * - Con series iguales escribe la dosis tal cual. Con series distintas resume el rango («3 × 6–10») o, si no hay rango común, «N series».
 * - La intensidad (RIR, RPE, % del 1RM o «hasta el fallo») sale si todas las series la comparten, o con su rango.
 * - El descanso sale si [includeRest] lo pide y es mayor que cero.
 *
 * Devuelve null si no hay ninguna serie de trabajo: quien la pinta escribe «Sin series».
 */
internal fun sessionExerciseDose(
    sets: List<ExerciseSet>,
    trainingMode: TrainingMode,
    restSeconds: Int?,
    includeRest: Boolean = true,
    customUnit: String? = null,
): String? {
    val working = sets.filterNot { it.isEmptySlot }
    if (working.isEmpty()) return null
    val count = working.size
    val reps = repsLabel(working, trainingMode, customUnit)
    val first = when {
        reps != null -> "$count × $reps"
        count == 1 -> "1 serie"
        else -> "$count series"
    }
    val rest = restSeconds?.takeIf { includeRest && it > 0 }?.let { KpknSetFormat.doseTime(it) }
    return listOfNotNull(first, intensityLabel(working, trainingMode), rest).joinToString(" · ")
}

/**
 * La misma dosis dicha en voz alta para TalkBack (SE8): «3 series de 6 a 8, RIR 2, descanso 2 minutos 30». Usa los mismos datos que
 * [sessionExerciseDose] y devuelve null igual que ella, sin series de trabajo.
 */
internal fun sessionExerciseSpokenDose(
    sets: List<ExerciseSet>,
    trainingMode: TrainingMode,
    restSeconds: Int?,
    includeRest: Boolean = true,
    customUnit: String? = null,
): String? {
    val working = sets.filterNot { it.isEmptySlot }
    if (working.isEmpty()) return null
    val count = working.size
    val reps = repsLabel(working, trainingMode, customUnit)?.replace("–", " a ")
    val first = when {
        count == 1 && reps != null -> "1 serie de $reps"
        count == 1 -> "1 serie"
        reps != null -> "$count series de $reps"
        else -> "$count series"
    }
    val rest = restSeconds?.takeIf { includeRest && it > 0 }?.let { "descanso ${spokenDuration(it)}" }
    return listOfNotNull(first, intensityLabel(working, trainingMode), rest).joinToString(", ")
}

/** Un tiempo hablado: «2 minutos 30», «1 minuto», «45 segundos». */
private fun spokenDuration(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    val minuteText = if (minutes == 1) "1 minuto" else "$minutes minutos"
    val secondText = if (rest == 1) "1 segundo" else "$rest segundos"
    return when {
        minutes == 0 -> secondText
        rest == 0 -> minuteText
        else -> "$minuteText ${rest}"
    }
}

/** Repeticiones de todas las series: «AMRAP», un rango («6–8»), un número («8») o una duración («45 s»). Null si no hay un patrón común. */
private fun repsLabel(sets: List<ExerciseSet>, mode: TrainingMode, customUnit: String?): String? {
    val unit = sets.map { it.resolveUnitModeV2(mode) }.distinct().singleOrNull() ?: return null
    if (unit == UnitModeV2.DISTANCE || unit == UnitModeV2.CUSTOM) {
        val values = sets.mapNotNull { it.plannedTargetV2 }
        if (values.size != sets.size) return null
        val label = if (unit == UnitModeV2.DISTANCE) "m" else customUnit?.takeIf { it.isNotBlank() } ?: "u"
        return valueRange(values, prefix = "", suffix = " $label")
    }
    val amrap = sets.count { it.purpose.isAmrap }
    if (amrap == sets.size) return "AMRAP"
    if (amrap > 0) return null
    if (unit == UnitModeV2.TIME) {
        return sets.map { it.targetDuration }.distinct().singleOrNull()?.let { KpknSetFormat.doseTime(it) }
    }
    if (unit != UnitModeV2.REPS) return null
    val ranges = sets.map { repRangeOf(it) }
    if (ranges.any { it == null }) return null
    val known = ranges.filterNotNull()
    val low = known.minOf { it.first }
    val high = known.maxOf { it.second }
    return if (low == high) low.toString() else low.toString() + "–" + high
}

/** Rango de repeticiones de una serie: el [com.example.kpkn.data.models.RepRange] o el número exacto. */
private fun repRangeOf(set: ExerciseSet): Pair<Int, Int>? {
    set.targetRepsRange?.let { return it.min to it.max }
    return set.targetReps?.let { it to it }
}

/** Intensidad común: «hasta el fallo», o RIR, RPE o % del 1RM si todas las series lo tienen (con rango si difieren). */
private fun intensityLabel(sets: List<ExerciseSet>, mode: TrainingMode): String? {
    if (sets.all { it.isFailure }) return "hasta el fallo"
    if (mode == TrainingMode.RM) return percentAndEffortLabel(sets)
    val rir = sets.mapNotNull { it.targetRIR?.toDouble() }
    if (rir.size == sets.size) return valueRange(rir, prefix = "RIR", suffix = "")
    val rpe = sets.mapNotNull { it.targetRPE }
    if (rpe.size == sets.size) return valueRange(rpe, prefix = "RPE", suffix = "")
    val pct = sets.mapNotNull { it.targetPercentageRM }
    if (pct.size == sets.size) return valueRange(pct, prefix = "", suffix = " % 1RM")
    return null
}

/**
 * Modo %: el % 1RM y, detrás, el RIR (o el RPE) de la prescripción híbrida, si las series lo llevan (SE11): «80 % 1RM · RIR 2».
 * Cada parte sale con la regla de siempre, sin el otro dato.
 */
private fun percentAndEffortLabel(sets: List<ExerciseSet>): String? {
    val percent = intensityLabel(sets.map { it.copy(targetRIR = null, targetRPE = null) }, TrainingMode.REPS)
    val effort = intensityLabel(sets.map { it.copy(targetPercentageRM = null) }, TrainingMode.REPS)
    return listOfNotNull(percent, effort).joinToString(" · ").takeIf { it.isNotEmpty() }
}

private fun valueRange(values: List<Double>, prefix: String, suffix: String): String? {
    val low = values.minOrNull() ?: return null
    val high = values.maxOrNull() ?: return null
    val text = if (low == high) formatValue(low) else "${formatValue(low)}–${formatValue(high)}"
    // «RIR 2» / «RPE 8» con espacio inseparable (retoque de ED-A): la cifra no queda sola al partir la línea.
    val head = if (prefix.isEmpty()) text else "$prefix\u00A0$text"
    return head + suffix
}

/** Un número sin decimales si es entero («8»), y con coma si no («7,5»). */
private fun formatValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.forLanguageTag("es"), "%.1f", value)

/**
 * Dosis de la fila plegada (E8, SESSION_EDITOR_R1 §3.3): la de fuerza o la del cardio, con los espacios inseparables antes de «%» y de las
 * unidades (LR1: «80 %», «45 s», «20 min»). Sin series ni objetivo, escribe «Sin series» o «Sin objetivo». En unilateral, «por lado».
 */
internal fun sessionExerciseListDose(exercise: com.example.kpkn.data.models.Exercise, includeRest: Boolean): String {
    val details = exercise.cardioDetails
    if (details != null) return cardioDoseLine(details)
    val base = sessionExerciseDose(exercise.sets, exercise.trainingMode, exercise.restTime, includeRest, exercise.customUnit) ?: "Sin series"
    val side = if (exercise.isEffectivelyUnilateral() && exercise.sets.any { !it.isEmptySlot }) " · por\u00A0lado" else ""
    return hardUnitSpaces(base + side)
}

/** Espacio inseparable entre una cifra y su unidad o su «%»; el resto del texto no cambia. */
internal fun hardUnitSpaces(text: String): String = text.replace(UNIT_SPACE, "$1 $2")

private val UNIT_SPACE = Regex("([0-9]) (%|s|min|km|kg|h)(?![a-zA-Záéíóúñ])")
