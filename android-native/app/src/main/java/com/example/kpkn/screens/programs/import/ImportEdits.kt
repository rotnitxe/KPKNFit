package com.example.kpkn.screens.programs.importer

import com.example.kpkn.domain.training.importer.Change
import com.example.kpkn.domain.training.importer.ImportDraftEditor
import com.example.kpkn.domain.training.importer.ImportExercisePatch
import com.example.kpkn.domain.training.importer.ImportIssue
import com.example.kpkn.domain.training.importer.ImportIssueKind
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ProgramImportParser

/**
 * Lo que se edita en la vista previa: el borrador leído y las decisiones por nombre (null = ejercicio propio; sin clave =
 * con dudas). Es lo que la pila de deshacer guarda antes de cada cambio.
 */
data class ImportSnapshot(
    val imported: ImportedProgramDraft,
    val resolutions: Map<String, String?>,
)

/** Una edición de la vista previa. Cada una tiene su texto para deshacer, que devuelve [ImportEdits.apply]. */
sealed interface ImportEditOp {
    /** Cambia los campos del ejercicio; con [allWeeks], también «el mismo ejercicio» de las demás semanas. */
    data class Update(val exerciseId: String, val patch: ImportExercisePatch, val allWeeks: Boolean) : ImportEditOp

    /**
     * Cambia el nombre (con [allWeeks], también el de las demás semanas). Si el nombre nuevo no tiene decisión, hereda la
     * del anterior: un propio sigue propio y uno elegido conserva su configuración.
     */
    data class Rename(val exerciseId: String, val rawName: String, val allWeeks: Boolean) : ImportEditOp

    data class Remove(val exerciseId: String) : ImportEditOp

    data class Duplicate(val exerciseId: String) : ImportEditOp

    /** Mueve el ejercicio al día [toDayId], a la posición [toIndex] contando sin el propio ejercicio. */
    data class Move(val exerciseId: String, val toDayId: String, val toIndex: Int) : ImportEditOp

    /**
     * Decide un nombre del archivo: [configId] del catálogo, mostrado como [name]; o null, ejercicio propio (y [name] es el
     * nombre del archivo). Va en la pila porque la decisión forma parte de lo que se deshace.
     */
    data class Decide(val rawName: String, val configId: String?, val name: String) : ImportEditOp

    data class RemoveDay(val dayId: String) : ImportEditOp

    data class RemoveWeek(val weekIndex: Int) : ImportEditOp

    /** Marca como revisados todos los avisos. */
    data object IgnoreAllWarnings : ImportEditOp

    /**
     * Varias ediciones como una sola, en orden: «Guardar cambios» con valores y nombre deja un único paso para deshacer.
     */
    data class Batch(val ops: List<ImportEditOp>) : ImportEditOp
}

/**
 * Aplica las ediciones de la vista previa y dice cómo se deshacen. Puro, sin Android: cada operación devuelve un estado
 * nuevo; con ids que no existen, o sin cambios, no devuelve nada.
 */
object ImportEdits {

    /**
     * Aplica [op] sobre [snapshot]. Devuelve el estado nuevo y el texto de deshacer, o null si la operación no cambia nada
     * (id inexistente, parche sin cambios, nombre igual…): entonces no hay entrada de deshacer. [nameOf] da el nombre que se
     * muestra de un nombre del archivo (el del catálogo si se eligió uno); se evalúa sobre el estado ANTES del cambio.
     */
    fun apply(
        snapshot: ImportSnapshot,
        op: ImportEditOp,
        nameOf: (rawName: String) -> String = { it },
    ): Pair<ImportSnapshot, String>? {
        val draft = snapshot.imported
        val result: Pair<ImportSnapshot, String> = when (op) {
            is ImportEditOp.Update -> snapshot.copy(
                imported = ImportDraftEditor.update(draft, op.exerciseId, op.patch, op.allWeeks),
            ) to "Cambiaste «${shownName(draft, op.exerciseId, nameOf)}»."
            is ImportEditOp.Rename -> renamed(snapshot, op, nameOf)
            is ImportEditOp.Remove -> snapshot.copy(
                imported = ImportDraftEditor.remove(draft, op.exerciseId),
            ) to "Quitaste «${shownName(draft, op.exerciseId, nameOf)}»."
            is ImportEditOp.Duplicate -> snapshot.copy(
                imported = ImportDraftEditor.duplicate(draft, op.exerciseId),
            ) to "Duplicaste «${shownName(draft, op.exerciseId, nameOf)}»."
            is ImportEditOp.Move -> moved(snapshot, op, nameOf)
            is ImportEditOp.Decide -> snapshot.copy(
                resolutions = snapshot.resolutions + (op.rawName to op.configId),
            ) to (if (op.configId == null) "Dejaste «${op.name}» como ejercicio propio." else "Elegiste «${op.name}».")
            is ImportEditOp.RemoveDay -> snapshot.copy(
                imported = ImportDraftEditor.removeDay(draft, op.dayId),
            ) to "Quitaste «${dayOf(draft, op.dayId)?.name.orEmpty()}»."
            is ImportEditOp.RemoveWeek -> snapshot.copy(
                imported = ImportDraftEditor.removeWeek(draft, op.weekIndex),
            ) to "Quitaste la semana ${op.weekIndex}."
            ImportEditOp.IgnoreAllWarnings -> ignoredAll(snapshot)
            is ImportEditOp.Batch -> return batched(snapshot, op, nameOf)
        }
        return if (result.first == snapshot) null else result
    }

    private fun renamed(
        snapshot: ImportSnapshot,
        op: ImportEditOp.Rename,
        nameOf: (String) -> String,
    ): Pair<ImportSnapshot, String> {
        val name = op.rawName.trim()
        val oldName = exerciseOf(snapshot.imported, op.exerciseId)?.rawName
        val imported = ImportDraftEditor.rename(snapshot.imported, op.exerciseId, name, op.allWeeks)
        // El nombre nuevo sin decisión hereda la del anterior; las decisiones de otros nombres no se tocan.
        val inherited = oldName?.takeIf { name.isNotEmpty() && name !in snapshot.resolutions && it in snapshot.resolutions }
        val resolutions = if (inherited != null) snapshot.resolutions + (name to snapshot.resolutions[inherited]) else snapshot.resolutions
        return ImportSnapshot(imported, resolutions) to "Renombraste «$name»."
    }

    private fun moved(
        snapshot: ImportSnapshot,
        op: ImportEditOp.Move,
        nameOf: (String) -> String,
    ): Pair<ImportSnapshot, String> {
        val draft = snapshot.imported
        val from = dayIdOf(draft, op.exerciseId)
        val target = dayOf(draft, op.toDayId)?.name.orEmpty()
        val label = if (from == op.toDayId) {
            "Cambiaste el orden de «$target»"
        } else {
            "Moviste «${shownName(draft, op.exerciseId, nameOf)}» a «$target»"
        }
        return snapshot.copy(imported = ImportDraftEditor.move(draft, op.exerciseId, op.toDayId, op.toIndex)) to label
    }

    private fun ignoredAll(snapshot: ImportSnapshot): Pair<ImportSnapshot, String> {
        val draft = snapshot.imported
        val pending = draft.warnings.count { it.id !in draft.reviewedWarnings }
        val imported = draft.warnings.fold(draft) { current, warning -> ImportDraftEditor.ignoreWarning(current, warning.id) }
        val label = if (pending == 1) "Ignoraste 1 aviso." else "Ignoraste $pending avisos."
        return snapshot.copy(imported = imported) to label
    }

    /**
     * Aplica las ediciones en orden como un solo cambio. El texto para deshacer nombra el ejercicio: el nombre nuevo si lo
     * hay («Cambiaste «Sentadilla trasera».»); si no, el que se mostraba antes.
     */
    private fun batched(
        snapshot: ImportSnapshot,
        op: ImportEditOp.Batch,
        nameOf: (String) -> String,
    ): Pair<ImportSnapshot, String>? {
        var state = snapshot
        op.ops.forEach { step -> state = apply(state, step, nameOf)?.first ?: state }
        if (state == snapshot) return null
        val renamed = op.ops.filterIsInstance<ImportEditOp.Rename>().lastOrNull()?.rawName?.trim()?.takeIf { it.isNotEmpty() }
        val exerciseId = op.ops.firstNotNullOfOrNull { exerciseIdOf(it) }
        val name = renamed ?: exerciseId?.let { shownName(snapshot.imported, it, nameOf) }.orEmpty()
        return state to "Cambiaste «$name»."
    }

    private fun exerciseIdOf(op: ImportEditOp): String? = when (op) {
        is ImportEditOp.Update -> op.exerciseId
        is ImportEditOp.Rename -> op.exerciseId
        else -> null
    }

    private fun shownName(draft: ImportedProgramDraft, exerciseId: String, nameOf: (String) -> String): String =
        exerciseOf(draft, exerciseId)?.let { nameOf(it.rawName) }.orEmpty()
}

/** Modo de la carga en el panel de ajuste: sin carga, en kg o en % de 1RM. */
enum class ImportLoadMode { NONE, KG, PERCENT }

/** Modo del esfuerzo en el panel de ajuste: sin esfuerzo, RPE o RIR. */
enum class ImportEffortMode { NONE, RPE, RIR }

/**
 * Lo que muestra el panel de ajuste de un ejercicio: cada campo tal como lo deja la persona. Un valor en blanco mientras se
 * escribe (kg o % sin número) no es válido; [isValid] lo dice. [allWeeks] es el interruptor «aplicar en las otras semanas».
 */
data class ImportExerciseForm(
    val rawName: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val loadMode: ImportLoadMode,
    val loadKg: Double?,
    val percent1Rm: Double?,
    val effortMode: ImportEffortMode,
    val rpe: Double?,
    val rir: Int?,
    val restSeconds: Int?,
    val notes: String,
    val allWeeks: Boolean,
) {
    companion object {
        /** El formulario con los valores actuales del ejercicio; el interruptor de otras semanas empieza apagado. */
        fun of(exercise: ImportedExercise): ImportExerciseForm = ImportExerciseForm(
            rawName = exercise.rawName,
            sets = exercise.sets,
            repsMin = exercise.repsMin,
            repsMax = exercise.repsMax,
            loadMode = when {
                exercise.loadKg != null -> ImportLoadMode.KG
                exercise.percent1Rm != null -> ImportLoadMode.PERCENT
                else -> ImportLoadMode.NONE
            },
            loadKg = exercise.loadKg,
            percent1Rm = exercise.percent1Rm,
            effortMode = when {
                exercise.rpe != null -> ImportEffortMode.RPE
                exercise.rir != null -> ImportEffortMode.RIR
                else -> ImportEffortMode.NONE
            },
            rpe = exercise.rpe,
            rir = exercise.rir,
            restSeconds = exercise.restSeconds,
            notes = exercise.notes.orEmpty(),
            allWeeks = false,
        )
    }
}

/** Rangos del panel: los del analizador, para que lo que se edita sea lo que se lee. */
private val LOAD_KG_RANGE = 0.5..ProgramImportParser.MAX_LOAD_KG
private val PERCENT_RANGE = 1.0..ProgramImportParser.MAX_PERCENT
private val RPE_RANGE = 1.0..10.0
private val RIR_RANGE = 0..10

/** Carga válida para el modo elegido: kg de 0,5 a 1000; % de 1 a 100; sin carga siempre vale. */
fun ImportExerciseForm.loadIsValid(): Boolean = when (loadMode) {
    ImportLoadMode.NONE -> true
    ImportLoadMode.KG -> loadKg?.let { it in LOAD_KG_RANGE } == true
    ImportLoadMode.PERCENT -> percent1Rm?.let { it in PERCENT_RANGE } == true
}

/** Si el formulario se puede guardar: nombre, series, repeticiones, carga, esfuerzo, descanso y notas dentro de rango. */
fun ImportExerciseForm.isValid(): Boolean {
    val effortOk = when (effortMode) {
        ImportEffortMode.NONE -> true
        ImportEffortMode.RPE -> rpe?.let { it in RPE_RANGE } == true
        ImportEffortMode.RIR -> rir?.let { it in RIR_RANGE } == true
    }
    return rawName.isNotBlank() &&
        sets in 1..ProgramImportParser.MAX_SETS &&
        repsMin in 1..ProgramImportParser.MAX_REPS &&
        repsMax in 1..ProgramImportParser.MAX_REPS &&
        repsMin <= repsMax &&
        loadIsValid() &&
        effortOk &&
        (restSeconds == null || restSeconds in 0..ProgramImportParser.MAX_REST_SECONDS) &&
        notes.length <= ProgramImportParser.MAX_NOTE_CHARS
}

/**
 * El parche con lo que cambió en [form] respecto a [original]: Set o Clear en los campos que la persona tocó y Keep en el
 * resto. Así, aplicado a otra semana, solo cambia lo que se tocó: la progresión propia de cada semana se conserva.
 */
fun patchFrom(original: ImportedExercise, form: ImportExerciseForm): ImportExercisePatch {
    val (load, percent) = form.loadPair()
    val (oldLoad, oldPercent) = originalLoadPair(original)
    val (rpe, rir) = form.effortPair()
    val (oldRpe, oldRir) = originalEffortPair(original)
    return ImportExercisePatch(
        sets = required(original.sets, form.sets),
        repsMin = required(original.repsMin, form.repsMin),
        repsMax = required(original.repsMax, form.repsMax),
        loadKg = optional(oldLoad, load),
        percent1Rm = optional(oldPercent, percent),
        rpe = optional(oldRpe, rpe),
        rir = optional(oldRir, rir),
        restSeconds = optional(original.restSeconds, form.restSeconds),
        notes = optional(original.notes?.trim()?.ifEmpty { null }, form.notes.trim().ifEmpty { null }),
    )
}

/** True si el parche no cambia ningún campo. */
fun ImportExercisePatch.isNoOp(): Boolean =
    listOf(sets, repsMin, repsMax, loadKg, percent1Rm, rpe, rir, restSeconds, notes).all { it == Change.Keep }

/** Texto de un descanso en segundos: «45 s», «2 min» o «2 min 30 s». */
fun importRestText(seconds: Int): String {
    if (seconds < 60) return "$seconds s"
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (rest == 0) "$minutes min" else "$minutes min $rest s"
}

/** Una línea de la revisión final, con su sitio: lo que falta y dónde. */
fun importIssueLine(issue: ImportIssue, imported: ImportedProgramDraft): String = when (issue.kind) {
    ImportIssueKind.NO_WEEKS -> "No quedó ninguna semana."
    ImportIssueKind.EMPTY_WEEK -> "La semana ${issue.weekIndex} no tiene ejercicios."
    ImportIssueKind.EMPTY_DAY ->
        "El día «${dayOf(imported, issue.dayId.orEmpty())?.name.orEmpty()}» de la semana ${issue.weekIndex} no tiene ejercicios."
    ImportIssueKind.UNDECIDED_EXERCISE -> "Falta decidir «${issue.rawName}»."
    ImportIssueKind.PENDING_WARNINGS ->
        if (issue.count == 1) "Hay 1 aviso sin revisar." else "Hay ${issue.count} avisos sin revisar."
}

/** La línea de «listo» de la revisión: «Listo: 2 semanas · 4 días · 9 ejercicios.». */
fun importReadyLine(imported: ImportedProgramDraft): String {
    val days = imported.weeks.sumOf { it.days.size }
    val exercises = imported.weeks.sumOf { week -> week.days.sumOf { it.exercises.size } }
    return "Listo: ${countText(imported.weeks.size, "semana", "semanas")} · " +
        "${countText(days, "día", "días")} · ${countText(exercises, "ejercicio", "ejercicios")}."
}

private fun countText(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"

/**
 * Pila de deshacer de la vista previa: en memoria, con [UNDO_CAPACITY] pasos como máximo; al pasarse, se olvida el más viejo.
 * Cada entrada guarda el estado ANTES del cambio y el texto de ese cambio.
 */
class ImportUndoStack {
    private val entries = ArrayDeque<Entry>()

    /** Cuántos cambios se pueden deshacer. */
    val size: Int get() = entries.size

    /** Texto del último cambio (lo que deshace el siguiente toque); null si no hay nada que deshacer. */
    val label: String? get() = entries.lastOrNull()?.label

    fun push(before: ImportSnapshot, label: String) {
        entries.addLast(Entry(before, label))
        while (entries.size > UNDO_CAPACITY) entries.removeFirst()
    }

    /** Saca el último cambio y devuelve el estado anterior; null si la pila está vacía. */
    fun pop(): ImportSnapshot? = entries.removeLastOrNull()?.snapshot

    fun clear() {
        entries.clear()
    }

    private class Entry(val snapshot: ImportSnapshot, val label: String)

    companion object {
        const val UNDO_CAPACITY = 30
    }
}

// ---- Auxiliares --------------------------------------------------------------------------------------------

private fun ImportExerciseForm.loadPair(): Pair<Double?, Double?> = when (loadMode) {
    ImportLoadMode.NONE -> null to null
    ImportLoadMode.KG -> loadKg to null
    ImportLoadMode.PERCENT -> null to percent1Rm
}

private fun ImportExerciseForm.effortPair(): Pair<Double?, Int?> = when (effortMode) {
    ImportEffortMode.NONE -> null to null
    ImportEffortMode.RPE -> rpe to null
    ImportEffortMode.RIR -> null to rir
}

/** La carga efectiva del ejercicio leído: kg si lo hay (el panel muestra un solo modo). */
private fun originalLoadPair(exercise: ImportedExercise): Pair<Double?, Double?> = when {
    exercise.loadKg != null -> exercise.loadKg to null
    exercise.percent1Rm != null -> null to exercise.percent1Rm
    else -> null to null
}

/** El esfuerzo efectivo del ejercicio leído: RPE si lo hay (el panel muestra un solo modo). */
private fun originalEffortPair(exercise: ImportedExercise): Pair<Double?, Int?> = when {
    exercise.rpe != null -> exercise.rpe to null
    exercise.rir != null -> null to exercise.rir
    else -> null to null
}

private fun <T> required(old: T, new: T): Change<T> = if (old == new) Change.Keep else Change.Set(new)

private fun <T : Any> optional(old: T?, new: T?): Change<T> = when {
    old == new -> Change.Keep
    new == null -> Change.Clear
    else -> Change.Set(new)
}

private fun exerciseOf(draft: ImportedProgramDraft, exerciseId: String): ImportedExercise? =
    draft.weeks.firstNotNullOfOrNull { week -> week.days.firstNotNullOfOrNull { day -> day.exercises.firstOrNull { it.id == exerciseId } } }

private fun dayOf(draft: ImportedProgramDraft, dayId: String): ImportedDay? =
    draft.weeks.firstNotNullOfOrNull { week -> week.days.firstOrNull { it.id == dayId } }

private fun dayIdOf(draft: ImportedProgramDraft, exerciseId: String): String? =
    draft.weeks.firstNotNullOfOrNull { week -> week.days.firstOrNull { day -> day.exercises.any { it.id == exerciseId } }?.id }
