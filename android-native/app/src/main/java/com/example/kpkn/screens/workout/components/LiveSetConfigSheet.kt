package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.displayWorkoutExerciseName
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Pestañas de «Configurar serie» (prototipo 02, `set-config`). */
internal enum class LiveSetConfigTab(val title: String) { TARGET("Objetivo"), LOAD("Carga"), DETAIL("Detalle") }

/** «Formato» de `set-config` (prototipo 02): las siete formas de plantear el objetivo de la serie. */
internal enum class LiveSetFormat(val label: String) {
    REPS("Repeticiones"), RM("RM / TM"), SOLO_RPE("Solo RPE"), TIME("Tiempo"), DISTANCE("Distancia"), CUSTOM("Unidad propia"), AMRAP("AMRAP"),
}

/** Lo que la hoja edita del plan de una serie; el registro realizado no se toca. */
internal data class LiveSetConfigDraft(
    val format: LiveSetFormat,
    /** «% de RM» cuando el formato es RM / TM. */
    val percentRm: String,
    val min: String,
    val max: String,
    val intensity: IntensityMode,
    val effort: String,
    val rest: String,
    val loadMode: LoadModeV2,
    val reference: String,
    val purpose: SetPurpose,
)

private fun Double.plain(): String = if (this % 1.0 == 0.0) toLong().toString() else toString().replace('.', ',')

internal fun liveSetConfigDraft(exercise: Exercise, set: ExerciseSet): LiveSetConfigDraft {
    val range = set.targetRepsRange
    val intensity = if (set.intensityMode == IntensityMode.RPE) IntensityMode.RPE else IntensityMode.RIR
    val unit = com.example.kpkn.screens.workout.liveSetUnitMode(exercise, set)
    val format = when (com.example.kpkn.screens.workout.liveSetCardMode(exercise, set, unit)) {
        com.example.kpkn.screens.workout.LiveSetCardMode.RM -> LiveSetFormat.RM
        com.example.kpkn.screens.workout.LiveSetCardMode.SOLO_RPE -> LiveSetFormat.SOLO_RPE
        com.example.kpkn.screens.workout.LiveSetCardMode.TIME -> LiveSetFormat.TIME
        com.example.kpkn.screens.workout.LiveSetCardMode.DISTANCE -> LiveSetFormat.DISTANCE
        com.example.kpkn.screens.workout.LiveSetCardMode.CUSTOM -> LiveSetFormat.CUSTOM
        com.example.kpkn.screens.workout.LiveSetCardMode.AMRAP -> if (set.purpose == SetPurpose.AMRAP) LiveSetFormat.AMRAP else LiveSetFormat.REPS
        com.example.kpkn.screens.workout.LiveSetCardMode.REPS -> LiveSetFormat.REPS
    }
    return LiveSetConfigDraft(
        format = format,
        percentRm = set.targetPercentageRM?.plain().orEmpty(),
        min = (range?.min ?: set.targetReps)?.toString().orEmpty(),
        max = (range?.max ?: set.targetReps)?.toString().orEmpty(),
        intensity = intensity,
        effort = if (intensity == IntensityMode.RPE) set.targetRPE?.plain().orEmpty() else set.targetRIR?.toString().orEmpty(),
        rest = (set.restAfterSeconds ?: exercise.restTime)?.toString().orEmpty(),
        loadMode = set.loadModeV2 ?: LoadModeV2.LOAD,
        reference = set.weight?.plain().orEmpty(),
        purpose = set.purpose,
    )
}

/** Valida y aplica el borrador al plan de la serie; null si hay objetivos, intensidad o descanso inválidos. */
internal fun applyLiveSetConfig(draft: LiveSetConfigDraft, set: ExerciseSet, restFallback: Int?): ExerciseSet? {
    fun num(text: String) = text.trim().replace(',', '.').toDoubleOrNull()
    val min = num(draft.min)?.toInt()
    val max = num(draft.max)?.toInt()
    val effort = num(draft.effort)
    val rest = num(draft.rest)?.toInt()
    val reference = if (draft.reference.isBlank()) null else num(draft.reference)
    if (min == null || max == null || min < 0 || max < min) return null
    if (effort == null || effort < 0 || (draft.intensity == IntensityMode.RPE && effort > 10)) return null
    if (rest == null || rest < 0) return null
    if (draft.reference.isNotBlank() && (reference == null || reference < 0)) return null
    val percentRm = num(draft.percentRm)
    // RM / TM pide su porcentaje: sin él la serie no tendría de dónde sacar la carga.
    if (draft.format == LiveSetFormat.RM && (percentRm == null || percentRm <= 0 || percentRm > 110)) return null
    val unit = when (draft.format) {
        LiveSetFormat.TIME -> UnitModeV2.TIME
        LiveSetFormat.DISTANCE -> UnitModeV2.DISTANCE
        LiveSetFormat.CUSTOM -> UnitModeV2.CUSTOM
        else -> UnitModeV2.REPS
    }
    return set.copy(
        unitModeV2 = unit,
        targetPercentageRM = if (draft.format == LiveSetFormat.RM) percentRm else set.targetPercentageRM,
        targetReps = max,
        targetRepsRange = if (min != max) RepRange(min, max) else null,
        intensityMode = if (draft.format == LiveSetFormat.RM) IntensityMode.LOAD else draft.intensity,
        targetRIR = if (draft.intensity == IntensityMode.RIR) effort.toInt() else set.targetRIR,
        targetRPE = if (draft.intensity == IntensityMode.RPE) effort else set.targetRPE,
        restAfterSeconds = rest.takeIf { it != restFallback },
        loadModeV2 = draft.loadMode,
        // Solo RPE no lleva carga fija: la guía el esfuerzo.
        weight = if (draft.format == LiveSetFormat.SOLO_RPE) null else reference,
        purpose = draft.purpose,
        calibrationScope = if (draft.purpose == SetPurpose.CALIBRATOR) set.calibrationScope ?: CalibrationScope.SESSION else null,
    )
}

/**
 * «Configurar serie» (prototipo 02): los objetivos de la serie, separados del registro. Objetivo (rango, intensidad y descanso), Carga
 * (tipo y referencia) y Detalle (propósito, técnica y todas las series), con «Aplicar a» esta serie o las pendientes del ejercicio.
 * Cerrar con cambios sin guardar pregunta antes de descartarlos.
 */
@Composable
internal fun LiveSetConfigSheet(
    exercise: Exercise,
    setIndex: Int,
    pendingIndices: Set<Int>,
    onSave: (Map<Int, ExerciseSet>) -> Unit,
    onOpenTechnique: () -> Unit,
    onOpenAllSeries: () -> Unit,
    onDismiss: () -> Unit,
    /** «Parciales, ayuda y ROM» del Detalle: abre el detalle de ejecución de la serie (con su propio borrador). */
    onOpenDetails: (() -> Unit)? = null,
) {
    val set = exercise.sets.getOrNull(setIndex) ?: return
    val initial = remember(exercise.id, set.id) { liveSetConfigDraft(exercise, set) }
    var draft by remember(exercise.id, set.id) { mutableStateOf(initial) }
    var tab by rememberSaveable(exercise.id, set.id) { mutableStateOf(LiveSetConfigTab.TARGET) }
    var pendingScope by rememberSaveable(exercise.id, set.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = draft != initial
    fun requestClose() { if (dirty) confirmDiscard = true else onDismiss() }
    fun save() {
        val targets = if (pendingScope) (pendingIndices + setIndex) else setOf(setIndex)
        val updated = targets.mapNotNull { index ->
            exercise.sets.getOrNull(index)?.let { target -> applyLiveSetConfig(draft, target, exercise.restTime)?.let { index to it } }
        }.toMap()
        if (updated.size != targets.count { exercise.sets.getOrNull(it) != null }) {
            error = "Revisa objetivos, intensidad y descanso."
            return
        }
        onSave(updated)
        onDismiss()
    }
    LiveSheet(
        title = "Configurar serie",
        onDismiss = ::requestClose,
        tag = "live-set-config-sheet",
        footer = {
            LiveSheetPrimary("Guardar objetivos", ::save, tag = "live-set-config-save")
            LiveSheetTextAction("Cancelar", ::requestClose)
        },
    ) {
        LiveSheetSubtitle("${displayWorkoutExerciseName(exercise)} · serie ${setIndex + 1}\nLos objetivos son independientes de lo registrado.")
        LiveSheetSegmented(LiveSetConfigTab.entries.map { it to it.title }, tab, { tab = it })
        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            when (tab) {
                LiveSetConfigTab.TARGET -> {
                    LiveSheetSelectField("Formato", LiveSetFormat.entries.map { it to it.label }, draft.format, { format ->
                        draft = draft.copy(
                            format = format,
                            purpose = when {
                                format == LiveSetFormat.AMRAP -> SetPurpose.AMRAP
                                draft.purpose == SetPurpose.AMRAP -> SetPurpose.NONE
                                else -> draft.purpose
                            },
                        )
                        error = null
                    }, Modifier.padding(bottom = 16.dp), tag = "live-set-config-format")
                    if (draft.format == LiveSetFormat.RM) {
                        LiveSheetLabeledField("% de RM", draft.percentRm, { draft = draft.copy(percentRm = it.filter { c -> c.isDigit() || c == ',' || c == '.' }); error = null },
                            Modifier.padding(bottom = 16.dp), keyboardType = KeyboardType.Decimal, tag = "live-set-config-percent")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LiveSheetLabeledField("Objetivo mínimo", draft.min, { draft = draft.copy(min = it.filter(Char::isDigit)); error = null },
                            Modifier.weight(1f), keyboardType = KeyboardType.Number, tag = "live-set-config-min")
                        LiveSheetLabeledField("Objetivo máximo", draft.max, { draft = draft.copy(max = it.filter(Char::isDigit)); error = null },
                            Modifier.weight(1f), keyboardType = KeyboardType.Number, tag = "live-set-config-max")
                    }
                    // `.two-fields`: «Intensidad» (RIR / RPE) y «Valor objetivo» en la misma fila.
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        LiveSheetSelectField("Intensidad", listOf(IntensityMode.RIR to "RIR", IntensityMode.RPE to "RPE"), draft.intensity,
                            { draft = draft.copy(intensity = it); error = null }, Modifier.weight(1f), tag = "live-set-config-intensity")
                        LiveSheetLabeledField("Valor objetivo", draft.effort, { draft = draft.copy(effort = it.filter { c -> c.isDigit() || c == ',' || c == '.' }); error = null },
                            Modifier.weight(1f), keyboardType = KeyboardType.Decimal, tag = "live-set-config-effort")
                    }
                    LiveSheetLabeledField("Descanso después · segundos", draft.rest, { draft = draft.copy(rest = it.filter(Char::isDigit)); error = null },
                        Modifier.padding(top = 16.dp), keyboardType = KeyboardType.Number, tag = "live-set-config-rest")
                }
                LiveSetConfigTab.LOAD -> {
                    LiveSheetChoiceRow(listOf(LoadModeV2.LOAD, LoadModeV2.BODYWEIGHT, LoadModeV2.LASTRE, LoadModeV2.ASSISTED).map { it to liveLoadModeLabel(it) },
                        { it == draft.loadMode }, { draft = draft.copy(loadMode = it) }, tagPrefix = "live-set-config-load")
                    LiveSheetLabeledField("Referencia · kg", draft.reference, { draft = draft.copy(reference = it.filter { c -> c.isDigit() || c == ',' || c == '.' }); error = null },
                        keyboardType = KeyboardType.Decimal, placeholder = "—", tag = "live-set-config-reference")
                    LiveSheetShortCopy("La carga realmente utilizada se edita al registrar la serie.")
                }
                LiveSetConfigTab.DETAIL -> {
                    LiveSheetSelectField("Propósito", listOf(SetPurpose.NONE to "Efectiva", SetPurpose.AMRAP to "AMRAP", SetPurpose.CALIBRATOR to "Calibrador"),
                        draft.purpose, { purpose ->
                            draft = draft.copy(
                                purpose = purpose,
                                format = when {
                                    purpose == SetPurpose.AMRAP -> LiveSetFormat.AMRAP
                                    draft.format == LiveSetFormat.AMRAP -> LiveSetFormat.REPS
                                    else -> draft.format
                                },
                            )
                        }, Modifier.padding(bottom = 8.dp), tag = "live-set-config-purpose")
                    LiveSheetMenuRow("Parciales, ayuda y ROM", { onOpenDetails?.invoke() }, icon = null, enabled = onOpenDetails != null)
                    LiveSheetMenuRow("Técnica avanzada", onOpenTechnique, icon = null)
                    LiveSheetMenuRow("Todas las series del ejercicio", onOpenAllSeries, icon = null,
                        small = exercise.sets.size.let { "$it series" })
                    LiveSheetShortCopy("El detalle de ejecución conserva su propio borrador.")
                }
            }
        }
        LiveSheetSectionLabel("Aplicar a")
        LiveSheetChoiceRow(listOf(false to "Esta serie", true to "Pendientes del ejercicio"), { it == pendingScope }, { pendingScope = it },
            Modifier.testTag("live-set-config-scope"), tagPrefix = "live-set-config-scope")
        error?.let { LiveSheetShortCopy(it, error = true) }
    }
    if (confirmDiscard) {
        LiveAlert(
            title = "Tienes cambios sin guardar",
            text = "Puedes volver a la configuración o descartar esta edición.",
            primaryLabel = "Seguir editando",
            onPrimary = { confirmDiscard = false },
            secondaryLabel = "Descartar cambios",
            onSecondary = { confirmDiscard = false; onDismiss() },
            onDismiss = { confirmDiscard = false },
            tag = "live-set-config-dirty",
        )
    }
}
