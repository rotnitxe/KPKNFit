package com.example.kpkn.screens.programs.importer

import com.example.kpkn.domain.training.importer.Change
import com.example.kpkn.domain.training.importer.ImportExercisePatch
import com.example.kpkn.domain.training.importer.ImportIssue
import com.example.kpkn.domain.training.importer.ImportIssueKind
import com.example.kpkn.domain.training.importer.ImportWarning
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportEditsTest {

    /** Tres semanas: la sentadilla con cargas distintas (100, 105 y 110 kg); dos avisos sin revisar. */
    private val draft = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            week(
                1,
                day(
                    "w1-d1",
                    "Pierna",
                    ex("w1-d1-e1", "Sentadilla", sets = 4, min = 6, max = 8, loadKg = 100.0, rest = 120, notes = "Profundidad"),
                    ex("w1-d1-e2", "Peso muerto", sets = 3, min = 5, max = 5, percent = 80.0),
                ),
                day("w1-d2", "Empuje", ex("w1-d2-e1", "Press banca", sets = 4, min = 8, max = 10)),
            ),
            week(
                2,
                day("w2-d1", "Pierna", ex("w2-d1-e1", "Sentadilla", sets = 3, min = 5, max = 5, loadKg = 105.0)),
                day("w2-d2", "Empuje", ex("w2-d2-e1", "Press banca", sets = 3, min = 6, max = 8)),
            ),
            week(
                3,
                day("w3-d1", "Pierna", ex("w3-d1-e1", "Sentadilla", sets = 2, min = 5, max = 5, loadKg = 110.0)),
                day("w3-d2", "Empuje", ex("w3-d2-e1", "Fondos", sets = 3, min = 10, max = 12)),
            ),
        ),
        warnings = listOf(
            ImportWarning("a1", ImportWarningKind.SKIPPED_ROW, "Línea 7", "Hacer 3 vueltas", "Línea 7: no se entiende «Hacer 3 vueltas»", 1, "w1-d2", null),
            ImportWarning("a2", ImportWarningKind.SKIPPED_ROW, "Línea 9", "Nota 2", "Línea 9: no se entiende «Nota 2»", 3, null, null),
        ),
    )

    /** «Sentadilla» decidida con catálogo, «Peso muerto» y «Press banca» propios, «Fondos» sin decidir. */
    private val snapshot = ImportSnapshot(
        draft,
        mapOf("Sentadilla" to "cfg-squat", "Peso muerto" to null, "Press banca" to null),
    )

    private fun applied(op: ImportEditOp, state: ImportSnapshot = snapshot): Pair<ImportSnapshot, String> =
        checkNotNull(ImportEdits.apply(state, op)) { "la operación debía cambiar algo: $op" }

    @Test
    fun `cada operacion devuelve su texto para deshacer`() {
        assertEquals(
            "Cambiaste «Sentadilla».",
            applied(ImportEditOp.Update("w1-d1-e1", ImportExercisePatch(sets = Change.Set(5)), allWeeks = false)).second,
        )
        assertEquals("Renombraste «Remo con barra».", applied(ImportEditOp.Rename("w1-d1-e2", "Remo con barra", allWeeks = false)).second)
        assertEquals("Quitaste «Sentadilla».", applied(ImportEditOp.Remove("w1-d1-e1")).second)
        assertEquals("Duplicaste «Sentadilla».", applied(ImportEditOp.Duplicate("w1-d1-e1")).second)
        assertEquals("Moviste «Sentadilla» a «Empuje»", applied(ImportEditOp.Move("w1-d1-e1", "w1-d2", 1)).second)
        assertEquals("Cambiaste el orden de «Pierna»", applied(ImportEditOp.Move("w1-d1-e2", "w1-d1", 0)).second)
        assertEquals(
            "Elegiste «Press banca (barra)».",
            applied(ImportEditOp.Decide("Press banca", "cfg-bench", "Press banca (barra)")).second,
        )
        assertEquals(
            "Dejaste «Sentadilla» como ejercicio propio.",
            applied(ImportEditOp.Decide("Sentadilla", null, "Sentadilla")).second,
        )
        assertEquals("Quitaste «Empuje».", applied(ImportEditOp.RemoveDay("w1-d2")).second)
        assertEquals("Quitaste la semana 2.", applied(ImportEditOp.RemoveWeek(2)).second)
        assertEquals("Ignoraste 2 avisos.", applied(ImportEditOp.IgnoreAllWarnings).second)
    }

    @Test
    fun `el texto usa el nombre que se muestra y no el del archivo`() {
        val shown = ImportEdits.apply(snapshot, ImportEditOp.Remove("w1-d1-e1")) { "Sentadilla trasera" }
        assertEquals("Quitaste «Sentadilla trasera».", shown?.second)
    }

    @Test
    fun `un solo aviso se escribe en singular`() {
        val oneLeft = snapshot.copy(imported = draft.copy(reviewedWarnings = setOf("a1")))
        assertEquals("Ignoraste 1 aviso.", applied(ImportEditOp.IgnoreAllWarnings, oneLeft).second)
    }

    @Test
    fun `sin cambios no hay operacion ni entrada de deshacer`() {
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.Update("w1-d1-e1", ImportExercisePatch(), allWeeks = false)))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.Update("nope", ImportExercisePatch(sets = Change.Set(5)), allWeeks = false)))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.Rename("w1-d1-e1", "Sentadilla", allWeeks = false)))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.Move("w1-d1-e1", "w1-d1", 0)))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.Decide("Sentadilla", "cfg-squat", "Sentadilla")))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.RemoveDay("nope")))
        val ignored = applied(ImportEditOp.IgnoreAllWarnings).first
        assertNull(ImportEdits.apply(ignored, ImportEditOp.IgnoreAllWarnings))
    }

    @Test
    fun `renombrar hereda la decision del nombre anterior y no toca las de otros nombres`() {
        val renamed = applied(ImportEditOp.Rename("w1-d1-e1", "Sentadilla trasera", allWeeks = false)).first
        assertEquals("cfg-squat", renamed.resolutions["Sentadilla trasera"])
        assertEquals("cfg-squat", renamed.resolutions["Sentadilla"])
        assertEquals(null, renamed.resolutions["Press banca"])
    }

    @Test
    fun `un propio renombrado sigue propio y un nombre con decision propia la conserva`() {
        val own = applied(ImportEditOp.Rename("w1-d1-e2", "Peso muerto rumano", allWeeks = false)).first
        assertTrue(own.resolutions.containsKey("Peso muerto rumano"))
        assertNull(own.resolutions["Peso muerto rumano"])

        val withOwnDecision = snapshot.copy(resolutions = snapshot.resolutions + ("Sentadilla trasera" to "cfg-other"))
        val kept = applied(ImportEditOp.Rename("w1-d1-e1", "Sentadilla trasera", allWeeks = false), withOwnDecision).first
        assertEquals("cfg-other", kept.resolutions["Sentadilla trasera"])
    }

    @Test
    fun `un nombre sin decision sigue sin decidir al renombrarlo`() {
        val undecided = snapshot.copy(resolutions = emptyMap())
        val renamed = applied(ImportEditOp.Rename("w1-d1-e2", "Peso muerto rumano", allWeeks = false), undecided).first
        assertFalse(renamed.resolutions.containsKey("Peso muerto rumano"))
    }

    @Test
    fun `patchFrom pone Set o Clear solo en lo que cambio y Keep en el resto`() {
        val squat = draft.weeks[0].days[0].exercises[0]
        val form = ImportExerciseForm.of(squat)
        assertTrue(patchFrom(squat, form).isNoOp())

        val sets = patchFrom(squat, form.copy(sets = 5))
        assertEquals(Change.Set(5), sets.sets)
        assertTrue(sets.copy(sets = Change.Keep).isNoOp())

        val reps = patchFrom(squat, form.copy(repsMax = 10))
        assertEquals(Change.Set(10), reps.repsMax)
        assertEquals(Change.Keep, reps.repsMin)

        val load = patchFrom(squat, form.copy(loadKg = 102.5))
        assertEquals(Change.Set(102.5), load.loadKg)
        assertEquals(Change.Keep, load.percent1Rm)

        val noLoad = patchFrom(squat, form.copy(loadMode = ImportLoadMode.NONE, loadKg = null))
        assertEquals(Change.Clear, noLoad.loadKg)

        val percent = patchFrom(squat, form.copy(loadMode = ImportLoadMode.PERCENT, percent1Rm = 80.0))
        assertEquals(Change.Clear, percent.loadKg)
        assertEquals(Change.Set(80.0), percent.percent1Rm)

        val rpe = patchFrom(squat, form.copy(effortMode = ImportEffortMode.RPE, rpe = 8.5))
        assertEquals(Change.Set(8.5), rpe.rpe)
        assertEquals(Change.Keep, rpe.rir)

        assertEquals(Change.Clear, patchFrom(squat, form.copy(restSeconds = null)).restSeconds)
        assertEquals(Change.Clear, patchFrom(squat, form.copy(notes = "   ")).notes)
        assertEquals(Change.Set("Pausa"), patchFrom(squat, form.copy(notes = " Pausa ")).notes)
    }

    @Test
    fun `aplicar en las otras semanas cambia solo las series y las cargas siguen distintas`() {
        val squat = draft.weeks[0].days[0].exercises[0]
        val patch = patchFrom(squat, ImportExerciseForm.of(squat).copy(sets = 5, allWeeks = true))

        val after = applied(ImportEditOp.Update(squat.id, patch, allWeeks = true)).first.imported

        assertEquals(listOf(5, 5, 5), listOf(0, 1, 2).map { after.weeks[it].days[0].exercises[0].sets })
        assertEquals(listOf(100.0, 105.0, 110.0), listOf(0, 1, 2).map { after.weeks[it].days[0].exercises[0].loadKg })
    }

    @Test
    fun `el formulario valida los limites del analizador`() {
        val form = ImportExerciseForm.of(draft.weeks[0].days[0].exercises[0])
        assertTrue(form.isValid())
        assertFalse(form.copy(sets = 21).isValid())
        assertFalse(form.copy(loadMode = ImportLoadMode.KG, loadKg = 0.2).isValid())
        assertFalse(form.copy(loadMode = ImportLoadMode.KG, loadKg = null).isValid())
        assertFalse(form.copy(loadMode = ImportLoadMode.PERCENT, percent1Rm = 101.0).isValid())
        assertFalse(form.copy(repsMin = 9, repsMax = 8).isValid())
        assertFalse(form.copy(notes = "x".repeat(201)).isValid())
        assertTrue(form.copy(effortMode = ImportEffortMode.RIR, rir = 2).isValid())
    }

    @Test
    fun `la pila de deshacer guarda 30 pasos y olvida el mas viejo`() {
        val stack = ImportUndoStack()
        repeat(31) { index -> stack.push(snapshot, "Paso ${index + 1}") }

        assertEquals(30, stack.size)
        assertEquals("Paso 31", stack.label)
        repeat(29) { stack.pop() }
        assertEquals("Paso 2", stack.label)
        stack.pop()
        assertNull(stack.label)
        assertNull(stack.pop())
    }

    @Test
    fun `clear vacia la pila`() {
        val stack = ImportUndoStack()
        stack.push(snapshot, "Cambiaste «Sentadilla».")
        stack.clear()

        assertNull(stack.label)
        assertNull(stack.pop())
    }

    @Test
    fun `el descanso se lee en segundos, minutos o ambos`() {
        assertEquals("45 s", importRestText(45))
        assertEquals("2 min", importRestText(120))
        assertEquals("2 min 30 s", importRestText(150))
        assertEquals("30 min", importRestText(1800))
    }

    @Test
    fun `cada problema de la revision tiene su linea y el listo cuenta bien`() {
        assertEquals("Falta decidir «Remo».", importIssueLine(ImportIssue(ImportIssueKind.UNDECIDED_EXERCISE, rawName = "Remo"), draft))
        assertEquals(
            "El día «Empuje» de la semana 3 no tiene ejercicios.",
            importIssueLine(ImportIssue(ImportIssueKind.EMPTY_DAY, weekIndex = 3, dayId = "w3-d2"), draft),
        )
        assertEquals("La semana 2 no tiene ejercicios.", importIssueLine(ImportIssue(ImportIssueKind.EMPTY_WEEK, weekIndex = 2), draft))
        assertEquals("No quedó ninguna semana.", importIssueLine(ImportIssue(ImportIssueKind.NO_WEEKS), draft))
        assertEquals("Hay 1 aviso sin revisar.", importIssueLine(ImportIssue(ImportIssueKind.PENDING_WARNINGS, count = 1), draft))
        assertEquals("Hay 2 avisos sin revisar.", importIssueLine(ImportIssue(ImportIssueKind.PENDING_WARNINGS, count = 2), draft))
        assertEquals("Listo: 3 semanas · 6 días · 7 ejercicios.", importReadyLine(draft))

        val one = ImportedProgramDraft(
            "Uno",
            listOf(ImportedWeek(1, listOf(ImportedDay("d", "Pierna", listOf(ImportedExercise("e", "Sentadilla", 3, 5, 5)))))),
        )
        assertEquals("Listo: 1 semana · 1 día · 1 ejercicio.", importReadyLine(one))
    }

    @Test
    fun `la opcion de mover usa el nombre del dia y solo antepone Dia n si el nombre no lo trae`() {
        assertEquals("Día 2 — Pierna", importMoveLabelOf(1, "Día 2 — Pierna"))
        assertEquals("Día 2 — Pierna", importMoveLabelOf(1, "Pierna"))
        assertEquals("Día 3", importMoveLabelOf(2, "Día 3"))
    }

    @Test
    fun `un lote es un solo cambio que nombra el deshacer con el nombre nuevo, y un lote sin cambios no deja nada`() {
        val batch = ImportEditOp.Batch(
            listOf(
                ImportEditOp.Update("w1-d1-e1", ImportExercisePatch(sets = Change.Set(5)), allWeeks = false),
                ImportEditOp.Rename("w1-d1-e1", "Sentadilla trasera", allWeeks = false),
            ),
        )
        val (after, label) = applied(batch)

        assertEquals("Cambiaste «Sentadilla trasera».", label)
        val squat = after.imported.weeks[0].days[0].exercises[0]
        assertEquals(5, squat.sets)
        assertEquals("Sentadilla trasera", squat.rawName)
        assertEquals("cfg-squat", after.resolutions["Sentadilla trasera"])
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.Batch(listOf(ImportEditOp.Update("w1-d1-e1", ImportExercisePatch(), allWeeks = false)))))
    }

    @Test
    fun `las operaciones de dias, semanas y ejercicios nuevos dicen lo que cambiaron`() {
        assertEquals("Renombraste «Piernas».", applied(ImportEditOp.RenameDay("w1-d1", "  Piernas ")).second)
        assertEquals("Cambiaste el orden de la semana 1.", applied(ImportEditOp.MoveDay("w1-d2", 0)).second)
        assertEquals("Uniste «Pierna» con «Empuje».", applied(ImportEditOp.MergeWithNext("w1-d1")).second)
        assertEquals("Añadiste «Día 3».", applied(ImportEditOp.AddDay(1, "")).second)
        assertEquals("Añadiste «Tirones».", applied(ImportEditOp.AddDay(2, "Tirones")).second)
        assertEquals("Duplicaste la semana 2.", applied(ImportEditOp.DuplicateWeek(2)).second)
        assertEquals(
            "Añadiste «Remo con barra».",
            applied(ImportEditOp.AddExercise("w1-d2", "Remo con barra", 4, 8, 8, configId = null, decided = true)).second,
        )
    }

    @Test
    fun `las operaciones de dias y semanas sin cambios no dejan nada para deshacer`() {
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.RenameDay("w1-d1", "Pierna")))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.RenameDay("w1-d1", "   ")))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.RenameDay("nope", "Otro")))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.MoveDay("w1-d1", 0)))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.MoveDay("nope", 0)))
        // «Empuje» es el último día de la semana 1: no hay con quién unirlo.
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.MergeWithNext("w1-d2")))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.AddDay(9, "Extra")))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.DuplicateWeek(9)))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.AddExercise("nope", "Remo", 3, 8, 8, null, decided = true)))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.AddExercise("w1-d1", "  ", 3, 8, 8, null, decided = true)))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.AddExercise("w1-d1", "Remo", 21, 8, 8, null, decided = true)))
        assertNull(ImportEdits.apply(snapshot, ImportEditOp.AddExercise("w1-d1", "Remo", 3, 9, 8, null, decided = true)))
    }

    @Test
    fun `anadir un ejercicio fija su decision sin pisar la que el nombre ya tenia`() {
        val catalog = applied(ImportEditOp.AddExercise("w1-d2", "Press inclinado", 4, 8, 8, "cfg-incline", decided = true)).first
        assertEquals("cfg-incline", catalog.resolutions["Press inclinado"])
        assertEquals("Press inclinado", catalog.imported.weeks[0].days[1].exercises.last().rawName)
        assertEquals("x1", catalog.imported.weeks[0].days[1].exercises.last().id)
        assertEquals(2, catalog.imported.nextId)

        val own = applied(ImportEditOp.AddExercise("w1-d2", "Fondos", 3, 10, 10, null, decided = true)).first
        assertTrue(own.resolutions.containsKey("Fondos"))
        assertNull(own.resolutions["Fondos"])

        val kept = applied(ImportEditOp.AddExercise("w1-d2", "Sentadilla", 3, 8, 8, null, decided = true)).first
        assertEquals("cfg-squat", kept.resolutions["Sentadilla"])

        val undecided = applied(ImportEditOp.AddExercise("w1-d2", "Remo", 3, 8, 8, null, decided = false)).first
        assertFalse(undecided.resolutions.containsKey("Remo"))
    }

    @Test
    fun `el texto de anadir usa el nombre que se muestra si el nombre ya tenia decision`() {
        val shown = ImportEdits.apply(snapshot, ImportEditOp.AddExercise("w1-d2", "Sentadilla", 3, 8, 8, null, decided = true)) {
            "Sentadilla trasera"
        }
        assertEquals("Añadiste «Sentadilla trasera».", shown?.second)
    }

    @Test
    fun `duplicar una semana da ids unicos a su copia y renumera las siguientes`() {
        val duplicated = applied(ImportEditOp.DuplicateWeek(1)).first.imported
        val ids = duplicated.weeks.flatMap { week -> week.days.flatMap { day -> listOf(day.id) + day.exercises.map { it.id } } }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf(1, 2, 3, 4), duplicated.weeks.map { it.index })
        assertEquals("Sentadilla", duplicated.weeks[1].days[0].exercises[0].rawName)
    }

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
