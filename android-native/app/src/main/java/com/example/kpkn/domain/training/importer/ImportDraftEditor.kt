package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.ExerciseMatchLexicon

/** Cambio de un campo de un parche: se conserva, se pone un valor o se borra. */
sealed interface Change<out T> {
    /** No toca el campo. */
    object Keep : Change<Nothing>

    /** Pone [value]; si está fuera de rango, el campo se conserva. */
    data class Set<T>(val value: T) : Change<T>

    /** Borra el campo. En los obligatorios (series, repeticiones) no hace nada. */
    object Clear : Change<Nothing>
}

/** Parche de un ejercicio: cada campo es un [Change]; por defecto no cambia nada. */
data class ImportExercisePatch(
    val sets: Change<Int> = Change.Keep,
    val repsMin: Change<Int> = Change.Keep,
    val repsMax: Change<Int> = Change.Keep,
    val loadKg: Change<Double> = Change.Keep,
    val percent1Rm: Change<Double> = Change.Keep,
    val rpe: Change<Double> = Change.Keep,
    val rir: Change<Int> = Change.Keep,
    val restSeconds: Change<Int> = Change.Keep,
    val notes: Change<String> = Change.Keep,
)

/**
 * Edición del borrador importado antes de guardarlo. Puro, sin Android: cada operación devuelve un borrador nuevo.
 * Si un id o una semana no existen, devuelve el mismo borrador y nunca lanza. Los ids que crea son `x<n>`, con el
 * contador [ImportedProgramDraft.nextId]; los de la lectura no cambian al mover, renombrar o renumerar.
 * Los límites de los valores son los del analizador (las series, de 1 a 20, como al leer).
 */
object ImportDraftEditor {

    private val SETS_RANGE = 1..ProgramImportParser.MAX_SETS
    private val REPS_RANGE = 1..ProgramImportParser.MAX_REPS
    private val LOAD_RANGE = 0.5..ProgramImportParser.MAX_LOAD_KG
    private val PERCENT_RANGE = 1.0..ProgramImportParser.MAX_PERCENT
    private val RPE_RANGE = 1.0..10.0
    private val RIR_RANGE = 0..10
    private val REST_RANGE = 0..ProgramImportParser.MAX_REST_SECONDS

    // ---- Ejercicios ----------------------------------------------------------------------------------------

    /**
     * Aplica el parche al ejercicio [exerciseId]. Con [allWeeks], el mismo parche va a «el mismo ejercicio» de las
     * demás semanas: el del día que ocupa la misma posición dentro de su semana y con el mismo nombre normalizado
     * (el primero si hay varios). Si no hay ninguno, nada.
     */
    fun update(
        draft: ImportedProgramDraft,
        exerciseId: String,
        patch: ImportExercisePatch,
        allWeeks: Boolean = false,
    ): ImportedProgramDraft {
        val same = sameExercise(draft, exerciseId)
        if (same.isEmpty()) return draft
        return draft.withExercisesAt(same, allWeeks) { exercise -> exercise.patched(patch) }
    }

    /**
     * Cambia el nombre del ejercicio; sin espacios sobrantes, y un nombre vacío no cambia nada. Con [allWeeks], renombra
     * también «el mismo ejercicio» de las demás semanas, con la misma regla que [update] y a partir del nombre ANTERIOR.
     */
    fun rename(draft: ImportedProgramDraft, exerciseId: String, rawName: String, allWeeks: Boolean = false): ImportedProgramDraft {
        val name = rawName.trim()
        if (name.isEmpty()) return draft
        val same = sameExercise(draft, exerciseId)
        if (same.isEmpty()) return draft
        return draft.withExercisesAt(same, allWeeks) { exercise -> exercise.copy(rawName = name) }
    }

    /**
     * Cuántas OTRAS semanas tienen «el mismo ejercicio» según la regla de [update] con [allWeeks] (el mismo día de su semana
     * y el mismo nombre normalizado). 0 si el id no existe.
     */
    fun sameExerciseInOtherWeeks(draft: ImportedProgramDraft, exerciseId: String): Int =
        (sameExercise(draft, exerciseId).size - 1).coerceAtLeast(0)

    /** Quita el ejercicio de su día. */
    fun remove(draft: ImportedProgramDraft, exerciseId: String): ImportedProgramDraft {
        val place = placeOfExercise(draft, exerciseId) ?: return draft
        return draft.withWeek(place.week) { week ->
            week.withDay(place.day) { day ->
                day.copy(exercises = day.exercises.filterIndexed { index, _ -> index != place.exercise })
            }
        }
    }

    /** Copia el ejercicio justo después, con un id nuevo. */
    fun duplicate(draft: ImportedProgramDraft, exerciseId: String): ImportedProgramDraft {
        val place = placeOfExercise(draft, exerciseId) ?: return draft
        val duplicated = draft.weeks[place.week].days[place.day].exercises[place.exercise].copy(id = "x${draft.nextId}")
        return draft.copy(nextId = draft.nextId + 1).withWeek(place.week) { week ->
            week.withDay(place.day) { day ->
                day.copy(exercises = day.exercises.insertAt(place.exercise + 1, duplicated))
            }
        }
    }

    /** Mueve el ejercicio a [toDayId] en la posición [toIndex], contando sin el propio ejercicio. */
    fun move(draft: ImportedProgramDraft, exerciseId: String, toDayId: String, toIndex: Int): ImportedProgramDraft {
        val place = placeOfExercise(draft, exerciseId) ?: return draft
        if (placeOfDay(draft, toDayId) == null) return draft
        val exercise = draft.weeks[place.week].days[place.day].exercises[place.exercise]
        return remove(draft, exerciseId).withDayById(toDayId) { day ->
            day.copy(exercises = day.exercises.insertAt(toIndex, exercise))
        } ?: draft
    }

    /**
     * Añade un ejercicio nuevo en [dayId], en la posición [index]. Con valores fuera de rango o un nombre vacío,
     * no añade nada.
     */
    fun add(
        draft: ImportedProgramDraft,
        dayId: String,
        index: Int,
        rawName: String,
        sets: Int,
        repsMin: Int,
        repsMax: Int,
    ): ImportedProgramDraft {
        val name = rawName.trim()
        if (name.isEmpty() || !isValidValues(sets, repsMin, repsMax) || placeOfDay(draft, dayId) == null) return draft
        val exercise = newExercise("x${draft.nextId}", name, sets, repsMin, repsMax)
        return draft.copy(nextId = draft.nextId + 1).withDayById(dayId) { day ->
            day.copy(exercises = day.exercises.insertAt(index, exercise))
        } ?: draft
    }

    // ---- Días ----------------------------------------------------------------------------------------------

    /** Cambia el nombre del día; un nombre vacío no cambia nada. */
    fun renameDay(draft: ImportedProgramDraft, dayId: String, name: String): ImportedProgramDraft {
        val clean = name.trim()
        if (clean.isEmpty()) return draft
        return draft.withDayById(dayId) { day -> day.copy(name = clean) } ?: draft
    }

    /** Quita el día con sus ejercicios. La semana queda aunque se vacíe. */
    fun removeDay(draft: ImportedProgramDraft, dayId: String): ImportedProgramDraft {
        val place = placeOfDay(draft, dayId) ?: return draft
        return draft.withWeek(place.week) { week ->
            week.copy(days = week.days.filterIndexed { index, _ -> index != place.day })
        }
    }

    /** Mueve el día dentro de su semana, a la posición [toIndex]. */
    fun moveDay(draft: ImportedProgramDraft, dayId: String, toIndex: Int): ImportedProgramDraft {
        val place = placeOfDay(draft, dayId) ?: return draft
        return draft.withWeek(place.week) { week ->
            val day = week.days[place.day]
            val others = week.days.filterIndexed { index, _ -> index != place.day }
            week.copy(days = others.insertAt(toIndex, day))
        }
    }

    /** Pasa los ejercicios del día siguiente de la misma semana al final de este, y el siguiente desaparece. */
    fun mergeWithNext(draft: ImportedProgramDraft, dayId: String): ImportedProgramDraft {
        val place = placeOfDay(draft, dayId) ?: return draft
        val next = draft.weeks[place.week].days.getOrNull(place.day + 1) ?: return draft
        return draft.withWeek(place.week) { week ->
            val merged = week.days[place.day].let { day -> day.copy(exercises = day.exercises + next.exercises) }
            week.copy(
                days = week.days.mapIndexedNotNull { index, day ->
                    when (index) {
                        place.day -> merged
                        place.day + 1 -> null
                        else -> day
                    }
                },
            )
        }
    }

    /** Añade un día vacío al final de la semana [weekIndex]. Sin nombre, usa «Día <n>». */
    fun addDay(draft: ImportedProgramDraft, weekIndex: Int, name: String): ImportedProgramDraft {
        val weekPosition = draft.weeks.indexOfFirst { it.index == weekIndex }
        if (weekPosition < 0) return draft
        val week = draft.weeks[weekPosition]
        val day = ImportedDay(
            id = "x${draft.nextId}",
            name = name.trim().ifEmpty { "Día ${week.days.size + 1}" },
            exercises = emptyList(),
        )
        return draft.copy(nextId = draft.nextId + 1).withWeek(weekPosition) { current ->
            current.copy(days = current.days + day)
        }
    }

    // ---- Semanas -------------------------------------------------------------------------------------------

    /** Quita la semana [index]; las siguientes bajan un número para seguir seguidas. */
    fun removeWeek(draft: ImportedProgramDraft, index: Int): ImportedProgramDraft {
        if (draft.weeks.none { it.index == index }) return draft
        val weeks = draft.weeks
            .filter { it.index != index }
            .map { week -> if (week.index > index) week.copy(index = week.index - 1) else week }
        return draft.copy(weeks = weeks)
    }

    /**
     * Copia la semana [index] justo después, con ids nuevos; las siguientes suben un número. Los ids de la copia
     * salen del contador, así que siguen siendo únicos.
     */
    fun duplicateWeek(draft: ImportedProgramDraft, index: Int): ImportedProgramDraft {
        val position = draft.weeks.indexOfFirst { it.index == index }
        if (position < 0) return draft
        val source = draft.weeks[position]
        var next = draft.nextId
        val copiedDays = source.days.map { day ->
            val dayId = "x${next++}"
            val exercises = day.exercises.map { exercise -> exercise.copy(id = "x${next++}") }
            day.copy(id = dayId, exercises = exercises)
        }
        val renumbered = draft.weeks.map { week -> if (week.index > index) week.copy(index = week.index + 1) else week }
        val weeks = renumbered.toMutableList()
        weeks.add(position + 1, source.copy(index = index + 1, days = copiedDays))
        return draft.copy(weeks = weeks, nextId = next)
    }

    // ---- Avisos --------------------------------------------------------------------------------------------

    /** Marca el aviso como revisado (ignorado). Un id que no existe, o ya revisado, no cambia nada. */
    fun ignoreWarning(draft: ImportedProgramDraft, warningId: String): ImportedProgramDraft {
        if (draft.warnings.none { it.id == warningId } || warningId in draft.reviewedWarnings) return draft
        return draft.copy(reviewedWarnings = draft.reviewedWarnings + warningId)
    }

    /**
     * Añade como ejercicio lo que el aviso no supo leer: al final de su día; si no había día, crea «Día 1» al final de
     * su semana (o de la semana 1, creándola si no existe). Después marca el aviso como revisado.
     */
    fun addFromWarning(
        draft: ImportedProgramDraft,
        warningId: String,
        rawName: String,
        sets: Int,
        repsMin: Int,
        repsMax: Int,
    ): ImportedProgramDraft {
        val warning = draft.warnings.firstOrNull { it.id == warningId } ?: return draft
        val name = rawName.trim()
        if (name.isEmpty() || !isValidValues(sets, repsMin, repsMax)) return draft
        val reviewed = draft.reviewedWarnings + warningId
        val dayId = warning.dayId?.takeIf { placeOfDay(draft, it) != null }
        if (dayId != null) {
            val exercise = newExercise("x${draft.nextId}", name, sets, repsMin, repsMax)
            return draft.copy(nextId = draft.nextId + 1, reviewedWarnings = reviewed)
                .withDayById(dayId) { day -> day.copy(exercises = day.exercises + exercise) } ?: draft
        }
        val weekIndex = warning.weekIndex ?: 1
        val day = ImportedDay(
            id = "x${draft.nextId}",
            name = "Día 1",
            exercises = listOf(newExercise("x${draft.nextId + 1}", name, sets, repsMin, repsMax)),
        )
        val weekPosition = draft.weeks.indexOfFirst { it.index == weekIndex }
        val weeks = if (weekPosition >= 0) {
            draft.weeks.mapIndexed { position, week ->
                if (position == weekPosition) week.copy(days = week.days + day) else week
            }
        } else {
            (draft.weeks + ImportedWeek(index = weekIndex, days = listOf(day))).sortedBy { it.index }
        }
        return draft.copy(weeks = weeks, nextId = draft.nextId + 2, reviewedWarnings = reviewed)
    }

    // ---- Borradores guardados antes de los ids ---------------------------------------------------------------

    /**
     * Da id `x<n>` a cada día o ejercicio que no lo tiene (borradores guardados antes de los ids), con el contador
     * [ImportedProgramDraft.nextId]. El contador nunca reutiliza un número `x` ya usado. Si ninguno está vacío,
     * devuelve el mismo borrador.
     */
    fun withIds(draft: ImportedProgramDraft): ImportedProgramDraft {
        val ids = draft.weeks.flatMap { week -> week.days.flatMap { day -> listOf(day.id) + day.exercises.map { it.id } } }
        if (ids.none { it.isEmpty() }) return draft
        val highest = ids.filter { it.startsWith("x") }.mapNotNull { it.substring(1).toIntOrNull() }.maxOrNull() ?: 0
        var next = maxOf(draft.nextId, highest + 1)
        val weeks = draft.weeks.map { week ->
            week.copy(
                days = week.days.map { day ->
                    val dayId = if (day.id.isEmpty()) "x${next++}" else day.id
                    day.copy(
                        id = dayId,
                        exercises = day.exercises.map { exercise ->
                            if (exercise.id.isEmpty()) exercise.copy(id = "x${next++}") else exercise
                        },
                    )
                },
            )
        }
        return draft.copy(weeks = weeks, nextId = next)
    }

    // ---- Parche ----------------------------------------------------------------------------------------------

    /** Aplica el parche a un ejercicio; los valores fuera de rango se descartan. */
    private fun ImportedExercise.patched(patch: ImportExercisePatch): ImportedExercise {
        val newLoad = patch.loadKg.validSetValue { it in LOAD_RANGE }
        val newPercent = patch.percent1Rm.validSetValue { it in PERCENT_RANGE }
        val newRpe = patch.rpe.validSetValue { it in RPE_RANGE }
        val newRir = patch.rir.validSetValue { it in RIR_RANGE }
        // Carga y % se excluyen, igual que RPE y RIR: si el parche pone uno válido, el otro se borra.
        val loadPair: Pair<Double?, Double?> = when {
            newLoad != null -> newLoad to null
            newPercent != null -> null to newPercent
            else -> patch.loadKg.optional(loadKg) { it in LOAD_RANGE } to
                patch.percent1Rm.optional(percent1Rm) { it in PERCENT_RANGE }
        }
        val load = loadPair.first
        val percent = loadPair.second
        val rpeRirPair: Pair<Double?, Int?> = when {
            newRpe != null -> newRpe to null
            newRir != null -> null to newRir
            else -> patch.rpe.optional(rpe) { it in RPE_RANGE } to
                patch.rir.optional(rir) { it in RIR_RANGE }
        }
        val rpeValue = rpeRirPair.first
        val rirValue = rpeRirPair.second
        val (low, high) = patchedReps(patch)
        val newNotes = when (val change = patch.notes) {
            is Change.Set<String> -> change.value.trim().takeIf { it.isNotEmpty() }?.take(ProgramImportParser.MAX_NOTE_CHARS)
            Change.Clear -> null
            Change.Keep -> notes
        }
        return copy(
            sets = patch.sets.required(sets) { it in SETS_RANGE },
            repsMin = low,
            repsMax = high,
            loadKg = load,
            percent1Rm = percent,
            rpe = rpeValue,
            rir = rirValue,
            restSeconds = patch.restSeconds.optional(restSeconds) { it in REST_RANGE },
            notes = newNotes,
        )
    }

    /** Repeticiones: cada extremo dentro de rango; si min queda por encima de max, se conservan ambos. */
    private fun ImportedExercise.patchedReps(patch: ImportExercisePatch): Pair<Int, Int> {
        val low = patch.repsMin.required(repsMin) { it in REPS_RANGE }
        val high = patch.repsMax.required(repsMax) { it in REPS_RANGE }
        return if (low <= high) low to high else repsMin to repsMax
    }

    /** Valor de un cambio que pone un valor válido; null si el cambio no pone nada válido. */
    private fun <T> Change<T>.validSetValue(valid: (T) -> Boolean): T? = when (this) {
        is Change.Set<T> -> value.takeIf(valid)
        else -> null
    }

    /** Campo opcional: un valor válido lo pone, Clear lo borra y Keep (o un valor inválido) lo conserva. */
    private fun <T> Change<T>.optional(current: T?, valid: (T) -> Boolean): T? = when (this) {
        is Change.Set<T> -> if (valid(value)) value else current
        Change.Clear -> null
        Change.Keep -> current
    }

    /** Campo obligatorio: un valor válido lo pone; Clear y Keep lo conservan. */
    private fun <T> Change<T>.required(current: T, valid: (T) -> Boolean): T = when (this) {
        is Change.Set<T> -> if (valid(value)) value else current
        else -> current
    }

    // ---- Posiciones y ayudas ---------------------------------------------------------------------------------

    /** Posición de un día o de un ejercicio dentro de la lista de semanas (índices de lista). */
    private class Place(val week: Int, val day: Int, val exercise: Int = -1)

    private fun placeOfExercise(draft: ImportedProgramDraft, exerciseId: String): Place? {
        draft.weeks.forEachIndexed { weekPosition, week ->
            week.days.forEachIndexed { dayPosition, day ->
                val index = day.exercises.indexOfFirst { it.id == exerciseId }
                if (index >= 0) return Place(weekPosition, dayPosition, index)
            }
        }
        return null
    }

    private fun placeOfDay(draft: ImportedProgramDraft, dayId: String): Place? {
        draft.weeks.forEachIndexed { weekPosition, week ->
            val dayPosition = week.days.indexOfFirst { it.id == dayId }
            if (dayPosition >= 0) return Place(weekPosition, dayPosition)
        }
        return null
    }

    /**
     * El ejercicio [exerciseId] primero y, después, «el mismo» de las demás semanas: el del día que ocupa la misma posición
     * dentro de su semana y con el mismo nombre normalizado (uno por semana). Vacío si el id no existe.
     */
    private fun sameExercise(draft: ImportedProgramDraft, exerciseId: String): List<Place> {
        val own = placeOfExercise(draft, exerciseId) ?: return emptyList()
        val key = ExerciseMatchLexicon.normalize(draft.weeks[own.week].days[own.day].exercises[own.exercise].rawName)
        val others = draft.weeks.mapIndexedNotNull { weekPosition, week ->
            if (weekPosition == own.week) return@mapIndexedNotNull null
            val day = week.days.getOrNull(own.day) ?: return@mapIndexedNotNull null
            val index = day.exercises.indexOfFirst { ExerciseMatchLexicon.normalize(it.rawName) == key }
            if (index < 0) null else Place(weekPosition, own.day, index)
        }
        return listOf(own) + others
    }

    /**
     * Aplica [change] a los ejercicios de [same] (el propio y, con [allWeeks], los de las demás semanas). Todos ocupan el
     * mismo día de posición: el del propio ejercicio.
     */
    private fun ImportedProgramDraft.withExercisesAt(
        same: List<Place>,
        allWeeks: Boolean,
        change: (ImportedExercise) -> ImportedExercise,
    ): ImportedProgramDraft {
        val targets = if (allWeeks) same else same.take(1)
        val day = same.first().day
        return copy(
            weeks = weeks.mapIndexed { weekPosition, week ->
                val indexes = targets.filter { it.week == weekPosition }.map { it.exercise }.toSet()
                if (indexes.isEmpty()) {
                    week
                } else {
                    week.withDay(day) { current ->
                        current.copy(
                            exercises = current.exercises.mapIndexed { index, exercise ->
                                if (index in indexes) change(exercise) else exercise
                            },
                        )
                    }
                }
            },
        )
    }

    private fun ImportedProgramDraft.withWeek(
        position: Int,
        transform: (ImportedWeek) -> ImportedWeek,
    ): ImportedProgramDraft = copy(weeks = weeks.mapIndexed { index, week -> if (index == position) transform(week) else week })

    private fun ImportedWeek.withDay(position: Int, transform: (ImportedDay) -> ImportedDay): ImportedWeek =
        copy(days = days.mapIndexed { index, day -> if (index == position) transform(day) else day })

    private fun ImportedProgramDraft.withDayById(
        dayId: String,
        transform: (ImportedDay) -> ImportedDay,
    ): ImportedProgramDraft? {
        val place = placeOfDay(this, dayId) ?: return null
        return withWeek(place.week) { week -> week.withDay(place.day, transform) }
    }

    private fun newExercise(id: String, name: String, sets: Int, repsMin: Int, repsMax: Int): ImportedExercise =
        ImportedExercise(id = id, rawName = name, sets = sets, repsMin = repsMin, repsMax = repsMax)

    private fun isValidValues(sets: Int, repsMin: Int, repsMax: Int): Boolean =
        sets in SETS_RANGE && repsMin in REPS_RANGE && repsMax in REPS_RANGE && repsMin <= repsMax

    private fun <T> List<T>.insertAt(index: Int, element: T): List<T> {
        val result = toMutableList()
        result.add(index.coerceIn(0, result.size), element)
        return result
    }
}
