package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Test

/** El editor valida la carga con el rango de la unidad del borrador: de 1 a 2200 lb, o de 0,5 a 1000 kg (H4d). */
class ImportDraftEditorLoadUnitTest {

    private fun loadOf(draft: ImportedProgramDraft): Double? = draft.weeks.single().days.single().exercises.single().loadKg

    private fun idOf(draft: ImportedProgramDraft): String = draft.weeks.single().days.single().exercises.single().id

    @Test
    fun `en libras acepta una carga de 1100 y la guarda tal cual`() {
        val draft = ProgramImportParser.parseText("Sentadilla 3x5 135 lb", title = "Libras")
        assertEquals(ImportLoadUnit.LB, draft.loadUnit)

        val changed = ImportDraftEditor.update(draft, idOf(draft), ImportExercisePatch(loadKg = Change.Set(1100.0)))

        assertEquals(1100.0, loadOf(changed))
    }

    @Test
    fun `en libras rechaza mas de 2200 y deja la carga como estaba`() {
        val draft = ProgramImportParser.parseText("Sentadilla 3x5 135 lb", title = "Libras")

        val changed = ImportDraftEditor.update(draft, idOf(draft), ImportExercisePatch(loadKg = Change.Set(2300.0)))

        assertEquals(135.0, loadOf(changed))
    }

    @Test
    fun `en kilos rechaza 1100 y acepta 1000`() {
        val draft = ProgramImportParser.parseText("Sentadilla 3x5 60 kg", title = "Kilos")
        assertEquals(ImportLoadUnit.KG, draft.loadUnit)

        val rejected = ImportDraftEditor.update(draft, idOf(draft), ImportExercisePatch(loadKg = Change.Set(1100.0)))
        val accepted = ImportDraftEditor.update(draft, idOf(draft), ImportExercisePatch(loadKg = Change.Set(1000.0)))

        assertEquals(60.0, loadOf(rejected))
        assertEquals(1000.0, loadOf(accepted))
    }
}
