package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDraftEditorTest {

    /** Tres semanas: Pierna y Empuje en las dos primeras; en la tercera, Fondos en lugar de Press banca. */
    private val draft = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            week(
                1,
                day(
                    "w1-d1",
                    "Pierna",
                    ex("w1-d1-e1", "Sentadilla", sets = 4, min = 6, max = 8, loadKg = 120.0, rpe = 8.0, rest = 180, notes = "Profundidad"),
                    ex("w1-d1-e2", "Peso muerto", sets = 3, min = 5, max = 5, percent = 80.0, rir = 2),
                ),
                day("w1-d2", "Empuje", ex("w1-d2-e1", "Press banca", sets = 4, min = 8, max = 10)),
            ),
            week(
                2,
                day("w2-d1", "Pierna", ex("w2-d1-e1", "Sentadilla", sets = 3, min = 5, max = 5, loadKg = 125.0)),
                day("w2-d2", "Empuje", ex("w2-d2-e1", "Press banca", sets = 3, min = 6, max = 8)),
            ),
            week(
                3,
                day("w3-d1", "Pierna", ex("w3-d1-e1", "Sentadilla", sets = 2, min = 5, max = 5)),
                day("w3-d2", "Empuje", ex("w3-d2-e1", "Fondos", sets = 3, min = 10, max = 12)),
            ),
        ),
        warnings = listOf(
            ImportWarning(
                id = "a1",
                kind = ImportWarningKind.SKIPPED_ROW,
                location = "Línea 7",
                sourceText = "Hacer 3 vueltas",
                message = "Línea 7: no se entiende «Hacer 3 vueltas»",
                weekIndex = 1,
                dayId = "w1-d2",
                exerciseId = null,
            ),
            ImportWarning(
                id = "a2",
                kind = ImportWarningKind.SKIPPED_ROW,
                location = "Línea 9",
                sourceText = "Nota 2",
                message = "Línea 9: no se entiende «Nota 2»",
                weekIndex = 3,
                dayId = null,
                exerciseId = null,
            ),
        ),
    )

    private val squat = draft.weeks[0].days[0].exercises[0]

    // ---- Parche de ejercicio -----------------------------------------------------------------------------

    @Test
    fun `update cambia series, repeticiones y notas, y solo en su semana`() {
        val updated = ImportDraftEditor.update(
            draft,
            "w1-d1-e1",
            ImportExercisePatch(
                sets = Change.Set(5),
                repsMin = Change.Set(5),
                repsMax = Change.Set(6),
                notes = Change.Set("  Pausa  "),
            ),
        )

        val changed = updated.weeks[0].days[0].exercises[0]
        assertEquals(5, changed.sets)
        assertEquals(5, changed.repsMin)
        assertEquals(6, changed.repsMax)
        assertEquals("Pausa", changed.notes)
        assertEquals(120.0, changed.loadKg)
        assertEquals(draft.weeks[1], updated.weeks[1])
        assertEquals(4, squat.sets)
    }

    @Test
    fun `allWeeks aplica el mismo parche a la misma posicion y nombre en las tres semanas`() {
        val updated = ImportDraftEditor.update(draft, "w1-d1-e1", ImportExercisePatch(sets = Change.Set(5)), allWeeks = true)

        assertEquals(5, updated.weeks[0].days[0].exercises[0].sets)
        assertEquals(5, updated.weeks[1].days[0].exercises[0].sets)
        assertEquals(5, updated.weeks[2].days[0].exercises[0].sets)
        assertEquals(draft.weeks[0].days[1], updated.weeks[0].days[1])
    }

    @Test
    fun `allWeeks no toca un nombre que no esta en otra semana ni otro dia`() {
        val updated = ImportDraftEditor.update(
            draft,
            "w1-d2-e1",
            ImportExercisePatch(repsMin = Change.Set(12), repsMax = Change.Set(14)),
            allWeeks = true,
        )

        assertEquals(12, updated.weeks[0].days[1].exercises[0].repsMin)
        assertEquals(14, updated.weeks[0].days[1].exercises[0].repsMax)
        assertEquals(12, updated.weeks[1].days[1].exercises[0].repsMin)
        assertEquals(14, updated.weeks[1].days[1].exercises[0].repsMax)
        assertEquals(draft.weeks[2], updated.weeks[2])
        assertEquals(draft.weeks[1].days[0], updated.weeks[1].days[0])
    }

    @Test
    fun `poner carga borra el porcentaje y poner porcentaje borra la carga`() {
        val byPercent = ImportDraftEditor.update(draft, "w1-d1-e1", ImportExercisePatch(percent1Rm = Change.Set(75.0)))
        val percentSquat = byPercent.weeks[0].days[0].exercises[0]
        assertEquals(75.0, percentSquat.percent1Rm)
        assertNull(percentSquat.loadKg)

        val byLoad = ImportDraftEditor.update(draft, "w1-d1-e2", ImportExercisePatch(loadKg = Change.Set(100.0)))
        val loadDeadlift = byLoad.weeks[0].days[0].exercises[1]
        assertEquals(100.0, loadDeadlift.loadKg)
        assertNull(loadDeadlift.percent1Rm)
    }

    @Test
    fun `poner RPE borra el RIR y poner RIR borra el RPE`() {
        val byRpe = ImportDraftEditor.update(draft, "w1-d1-e2", ImportExercisePatch(rpe = Change.Set(7.5)))
        val deadlift = byRpe.weeks[0].days[0].exercises[1]
        assertEquals(7.5, deadlift.rpe)
        assertNull(deadlift.rir)

        val byRir = ImportDraftEditor.update(draft, "w1-d1-e1", ImportExercisePatch(rir = Change.Set(1)))
        val rirSquat = byRir.weeks[0].days[0].exercises[0]
        assertEquals(1, rirSquat.rir)
        assertNull(rirSquat.rpe)
    }

    @Test
    fun `un valor fuera de rango se descarta y el ejercicio queda igual`() {
        val patch = ImportExercisePatch(
            sets = Change.Set(13),
            repsMin = Change.Set(0),
            loadKg = Change.Set(0.1),
            percent1Rm = Change.Set(101.0),
            rpe = Change.Set(11.0),
            rir = Change.Set(11),
            restSeconds = Change.Set(1801),
        )

        val updated = ImportDraftEditor.update(draft, "w1-d1-e1", patch)

        assertEquals(squat, updated.weeks[0].days[0].exercises[0])
    }

    @Test
    fun `repeticiones con minimo mayor que maximo se descartan`() {
        val updated = ImportDraftEditor.update(draft, "w1-d1-e1", ImportExercisePatch(repsMin = Change.Set(10)))

        assertEquals(6, updated.weeks[0].days[0].exercises[0].repsMin)
        assertEquals(8, updated.weeks[0].days[0].exercises[0].repsMax)
    }

    @Test
    fun `clear borra los opcionales y en las series no hace nada`() {
        val cleared = ImportDraftEditor.update(
            draft,
            "w1-d1-e1",
            ImportExercisePatch(
                sets = Change.Clear,
                loadKg = Change.Clear,
                rpe = Change.Clear,
                restSeconds = Change.Clear,
                notes = Change.Clear,
            ),
        )

        val result = cleared.weeks[0].days[0].exercises[0]
        assertEquals(4, result.sets)
        assertNull(result.loadKg)
        assertNull(result.rpe)
        assertNull(result.restSeconds)
        assertNull(result.notes)
    }

    // ---- Ejercicios ----------------------------------------------------------------------------------------

    @Test
    fun `rename recorta el nombre y un nombre vacio no cambia nada`() {
        val renamed = ImportDraftEditor.rename(draft, "w1-d1-e2", "  Peso muerto rumano  ")

        assertEquals("Peso muerto rumano", renamed.weeks[0].days[0].exercises[1].rawName)
        assertSame(draft, ImportDraftEditor.rename(draft, "w1-d1-e2", "   "))
    }

    @Test
    fun `remove quita el ejercicio y deja el resto del dia`() {
        val removed = ImportDraftEditor.remove(draft, "w1-d1-e1")

        assertEquals(listOf("w1-d1-e2"), removed.weeks[0].days[0].exercises.map { it.id })
    }

    @Test
    fun `duplicate copia el ejercicio justo despues, con id nuevo del contador`() {
        val duplicated = ImportDraftEditor.duplicate(draft, "w1-d1-e1")

        val exercises = duplicated.weeks[0].days[0].exercises
        assertEquals(listOf("w1-d1-e1", "x1", "w1-d1-e2"), exercises.map { it.id })
        assertEquals("Sentadilla", exercises[1].rawName)
        assertEquals(120.0, exercises[1].loadKg)
        assertEquals(2, duplicated.nextId)
    }

    @Test
    fun `move lleva el ejercicio a otro dia, o a otra posicion del mismo`() {
        val toOtherDay = ImportDraftEditor.move(draft, "w1-d1-e2", "w1-d2", 0)
        assertEquals(listOf("w1-d1-e1"), toOtherDay.weeks[0].days[0].exercises.map { it.id })
        assertEquals(listOf("w1-d1-e2", "w1-d2-e1"), toOtherDay.weeks[0].days[1].exercises.map { it.id })

        val sameDay = ImportDraftEditor.move(draft, "w1-d1-e1", "w1-d1", 1)
        assertEquals(listOf("w1-d1-e2", "w1-d1-e1"), sameDay.weeks[0].days[0].exercises.map { it.id })

        val clamped = ImportDraftEditor.move(draft, "w1-d1-e1", "w1-d2", 99)
        assertEquals(listOf("w1-d2-e1", "w1-d1-e1"), clamped.weeks[0].days[1].exercises.map { it.id })
    }

    @Test
    fun `add crea el ejercicio con id del contador y valida sus valores`() {
        val added = ImportDraftEditor.add(draft, "w1-d2", 0, "Fondos", 3, 8, 10)

        val exercises = added.weeks[0].days[1].exercises
        assertEquals(listOf("x1", "w1-d2-e1"), exercises.map { it.id })
        assertEquals("Fondos", exercises[0].rawName)
        assertEquals(3, exercises[0].sets)
        assertEquals(8, exercises[0].repsMin)
        assertEquals(10, exercises[0].repsMax)
        assertEquals(2, added.nextId)

        assertSame(draft, ImportDraftEditor.add(draft, "w1-d2", 0, "Fondos", 13, 8, 10))
        assertSame(draft, ImportDraftEditor.add(draft, "w1-d2", 0, "Fondos", 3, 10, 8))
        assertSame(draft, ImportDraftEditor.add(draft, "w1-d2", 0, "  ", 3, 8, 10))
    }

    // ---- Días ----------------------------------------------------------------------------------------------

    @Test
    fun `renameDay cambia el nombre y un nombre vacio no cambia nada`() {
        assertEquals("Empuje B", ImportDraftEditor.renameDay(draft, "w1-d2", "  Empuje B ").weeks[0].days[1].name)
        assertSame(draft, ImportDraftEditor.renameDay(draft, "w1-d2", " "))
    }

    @Test
    fun `removeDay quita el dia con sus ejercicios`() {
        val removed = ImportDraftEditor.removeDay(draft, "w1-d2")

        assertEquals(listOf("w1-d1"), removed.weeks[0].days.map { it.id })
    }

    @Test
    fun `moveDay reordena los dias dentro de su semana`() {
        val moved = ImportDraftEditor.moveDay(draft, "w1-d1", 1)

        assertEquals(listOf("w1-d2", "w1-d1"), moved.weeks[0].days.map { it.id })
        assertEquals(draft.weeks[1], moved.weeks[1])
    }

    @Test
    fun `mergeWithNext pasa los ejercicios del siguiente al final y el siguiente desaparece`() {
        val merged = ImportDraftEditor.mergeWithNext(draft, "w1-d1")

        val days = merged.weeks[0].days
        assertEquals(listOf("w1-d1"), days.map { it.id })
        assertEquals(listOf("w1-d1-e1", "w1-d1-e2", "w1-d2-e1"), days[0].exercises.map { it.id })
        assertSame(draft, ImportDraftEditor.mergeWithNext(draft, "w1-d2"))
    }

    @Test
    fun `addDay agrega un dia vacio al final y sin nombre usa Dia n`() {
        val added = ImportDraftEditor.addDay(draft, 2, "Descanso activo")

        val last = added.weeks[1].days.last()
        assertEquals("Descanso activo", last.name)
        assertEquals("x1", last.id)
        assertTrue(last.exercises.isEmpty())
        assertEquals("Día 3", ImportDraftEditor.addDay(draft, 2, "  ").weeks[1].days.last().name)
    }

    // ---- Semanas -------------------------------------------------------------------------------------------

    @Test
    fun `removeWeek renumera las semanas siguientes`() {
        val removed = ImportDraftEditor.removeWeek(draft, 2)

        assertEquals(listOf(1, 2), removed.weeks.map { it.index })
        assertEquals(listOf("w3-d1", "w3-d2"), removed.weeks[1].days.map { it.id })
    }

    @Test
    fun `duplicateWeek copia la semana justo despues con ids nuevos y renumera`() {
        val duplicated = ImportDraftEditor.duplicateWeek(draft, 2)

        assertEquals(listOf(1, 2, 3, 4), duplicated.weeks.map { it.index })
        val copy = duplicated.weeks[2]
        assertEquals(listOf("x1", "x3"), copy.days.map { it.id })
        assertEquals(listOf("x2"), copy.days[0].exercises.map { it.id })
        assertEquals(listOf("x4"), copy.days[1].exercises.map { it.id })
        assertEquals("Sentadilla", copy.days[0].exercises[0].rawName)
        assertEquals(listOf("w2-d1", "w2-d2"), duplicated.weeks[1].days.map { it.id })
        assertEquals(listOf("w3-d1", "w3-d2"), duplicated.weeks[3].days.map { it.id })
        assertEquals(5, duplicated.nextId)
    }

    @Test
    fun `los ids siguen siendo unicos tras duplicar una semana y un ejercicio`() {
        val duplicatedWeek = ImportDraftEditor.duplicateWeek(draft, 1)
        val duplicatedExercise = ImportDraftEditor.duplicate(duplicatedWeek, "w1-d1-e1")

        val ids = allIds(duplicatedExercise)
        assertEquals(ids.size, ids.toSet().size)
    }

    // ---- Avisos --------------------------------------------------------------------------------------------

    @Test
    fun `ignoreWarning marca el aviso como revisado y no lo repite`() {
        val once = ImportDraftEditor.ignoreWarning(draft, "a1")

        assertEquals(setOf("a1"), once.reviewedWarnings)
        assertSame(once, ImportDraftEditor.ignoreWarning(once, "a1"))
    }

    @Test
    fun `addFromWarning con dia lo añade al final de ese dia y marca el aviso`() {
        val added = ImportDraftEditor.addFromWarning(draft, "a1", "Remo con barra", 3, 8, 10)

        assertEquals(listOf("w1-d2-e1", "x1"), added.weeks[0].days[1].exercises.map { it.id })
        assertEquals("Remo con barra", added.weeks[0].days[1].exercises[1].rawName)
        assertEquals(setOf("a1"), added.reviewedWarnings)
        assertEquals(2, added.nextId)
    }

    @Test
    fun `addFromWarning sin dia crea Dia 1 al final de la semana del aviso`() {
        val added = ImportDraftEditor.addFromWarning(draft, "a2", "Hacer 3 vueltas", 3, 10, 12)

        val days = added.weeks[2].days
        assertEquals(listOf("w3-d1", "w3-d2", "x1"), days.map { it.id })
        assertEquals("Día 1", days[2].name)
        assertEquals(listOf("x2"), days[2].exercises.map { it.id })
        assertEquals("Hacer 3 vueltas", days[2].exercises[0].rawName)
        assertEquals(setOf("a2"), added.reviewedWarnings)
        assertEquals(3, added.nextId)
    }

    @Test
    fun `addFromWarning sin semana la crea y deja las semanas ordenadas`() {
        val withExtra = draft.copy(
            warnings = draft.warnings + ImportWarning(
                id = "a3",
                kind = ImportWarningKind.SKIPPED_ROW,
                location = "Línea 12",
                sourceText = "Algo 2",
                message = "Línea 12: no se entiende «Algo 2»",
                weekIndex = 5,
                dayId = null,
                exerciseId = null,
            ),
        )

        val added = ImportDraftEditor.addFromWarning(withExtra, "a3", "Algo", 2, 5, 5)

        assertEquals(listOf(1, 2, 3, 5), added.weeks.map { it.index })
        assertEquals("Algo", added.weeks.last().days.single().exercises.single().rawName)
    }

    @Test
    fun `addFromWarning con valores fuera de rango no hace nada`() {
        assertSame(draft, ImportDraftEditor.addFromWarning(draft, "a1", "Remo", 13, 8, 10))
    }

    // ---- Ids inexistentes ----------------------------------------------------------------------------------

    @Test
    fun `con ids inexistentes cada operacion devuelve el mismo borrador`() {
        val unknown = "w9-d9-e9"
        assertSame(draft, ImportDraftEditor.update(draft, unknown, ImportExercisePatch(sets = Change.Set(5))))
        assertSame(draft, ImportDraftEditor.rename(draft, unknown, "X"))
        assertSame(draft, ImportDraftEditor.remove(draft, unknown))
        assertSame(draft, ImportDraftEditor.duplicate(draft, unknown))
        assertSame(draft, ImportDraftEditor.move(draft, unknown, "w1-d1", 0))
        assertSame(draft, ImportDraftEditor.move(draft, "w1-d1-e1", "nope", 0))
        assertSame(draft, ImportDraftEditor.add(draft, "nope", 0, "X", 3, 8, 8))
        assertSame(draft, ImportDraftEditor.renameDay(draft, "nope", "X"))
        assertSame(draft, ImportDraftEditor.removeDay(draft, "nope"))
        assertSame(draft, ImportDraftEditor.moveDay(draft, "nope", 0))
        assertSame(draft, ImportDraftEditor.mergeWithNext(draft, "nope"))
        assertSame(draft, ImportDraftEditor.addDay(draft, 99, "X"))
        assertSame(draft, ImportDraftEditor.removeWeek(draft, 99))
        assertSame(draft, ImportDraftEditor.duplicateWeek(draft, 99))
        assertSame(draft, ImportDraftEditor.ignoreWarning(draft, "zz"))
        assertSame(draft, ImportDraftEditor.addFromWarning(draft, "zz", "X", 3, 8, 8))
    }

    private fun allIds(draft: ImportedProgramDraft): List<String> =
        draft.weeks.flatMap { week -> week.days.flatMap { day -> listOf(day.id) + day.exercises.map { it.id } } }

    private fun week(index: Int, vararg days: ImportedDay): ImportedWeek = ImportedWeek(index = index, days = days.toList())

    private fun day(id: String, name: String, vararg exercises: ImportedExercise): ImportedDay =
        ImportedDay(id = id, name = name, exercises = exercises.toList())

    private fun ex(
        id: String,
        name: String,
        sets: Int = 3,
        min: Int = 8,
        max: Int = 8,
        loadKg: Double? = null,
        percent: Double? = null,
        rpe: Double? = null,
        rir: Int? = null,
        rest: Int? = null,
        notes: String? = null,
    ): ImportedExercise = ImportedExercise(
        id = id,
        rawName = name,
        sets = sets,
        repsMin = min,
        repsMax = max,
        loadKg = loadKg,
        percent1Rm = percent,
        rpe = rpe,
        rir = rir,
        restSeconds = rest,
        notes = notes,
    )
}
