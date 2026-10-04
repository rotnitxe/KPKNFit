package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.isVisibleForApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C.P5 · La fila «Reparto semanal» de la revisión dice el nombre del reparto, nunca su id técnico («ul_x4»):
 * el mismo nombre que la persona vio al elegirlo en la lista de repartos (`splitDisplayName`). JVM puro.
 */
class WizardSplitLabelTest {

    private fun state(draft: SetupWizardDraft = SetupWizardDraft(), preview: Program? = null) =
        SetupWizardState(draft = draft, programPreview = preview)

    private fun preview(splitId: String?, customName: String? = null) =
        Program(id = "p", name = "Plan", selectedSplitId = splitId, customSplitName = customName)

    @Test
    fun aChosenSplitReadsWithItsPlainNameAndNeverItsId() {
        assertEquals("Torso y pierna, 4 días", draftSplitLabel(state(SetupWizardDraft(selectedSplitId = "ul_x4"))))
        assertEquals("Cuerpo completo, 3 días", draftSplitLabel(state(SetupWizardDraft(selectedSplitId = "fullbody_x3"))))
        assertEquals("Empuje, tirón y pierna, 6 días", draftSplitLabel(state(SetupWizardDraft(selectedSplitId = "ppl_x6"))))
    }

    @Test
    fun aSplitWithoutAPlainNameOfItsOwnKeepsTheNameOfItsTemplate() {
        val template = SPLIT_TEMPLATES.first { it.id == "texas_method" }
        val label = requireNotNull(draftSplitLabel(state(SetupWizardDraft(selectedSplitId = template.id))))
        assertEquals(template.name, label)
        assertNotEquals(template.id, label)
        assertFalse("«$label» lleva un guion bajo", label.contains('_'))
    }

    @Test
    fun withoutAChoiceTheSplitTheEngineAppliedToThePreparedProgramIsNamed() {
        assertEquals(
            "Empuje, tirón, pierna y torso",
            draftSplitLabel(state(SetupWizardDraft(selectedSplitId = null), preview("ppl_ul"))),
        )
        // La elección de la persona manda sobre la del programa preparado.
        assertEquals(
            "Torso y pierna, 4 días",
            draftSplitLabel(state(SetupWizardDraft(selectedSplitId = "ul_x4"), preview("ppl_ul"))),
        )
    }

    @Test
    fun aCustomSplitShowsTheNameThePersonGaveIt() {
        assertEquals(
            "Mi semana",
            draftSplitLabel(state(SetupWizardDraft(selectedSplitId = "custom", customSplitName = "Mi semana"))),
        )
        // Sin nombre propio no sale «Crear desde Cero» (el nombre de la plantilla) ni «custom».
        assertEquals("Reparto personalizado", draftSplitLabel(state(SetupWizardDraft(selectedSplitId = "custom"))))
        assertEquals(
            "De la vista previa",
            draftSplitLabel(state(SetupWizardDraft(selectedSplitId = "custom"), preview("custom", customName = "De la vista previa"))),
        )
        assertEquals("un nombre en blanco no cuenta", "Reparto personalizado", draftSplitLabel(state(SetupWizardDraft(selectedSplitId = "custom", customSplitName = "  "))))
    }

    @Test
    fun anIdTheCatalogDoesNotKnowIsNeverShown() {
        assertNull(draftSplitLabel(state(SetupWizardDraft(selectedSplitId = "id_que_no_existe"))))
        assertNull(draftSplitLabel(state(SetupWizardDraft(), preview("otro_id_que_no_existe"))))
    }

    @Test
    fun withNoChoiceAndNothingPreparedThereIsNothingToSay() {
        assertNull(draftSplitLabel(state()))
        assertNull(draftSplitLabel(state(preview = preview(null))))
    }

    @Test
    fun everySplitThePersonCanChooseHasANameThatIsNotItsId() {
        val visible = SPLIT_TEMPLATES.filter { it.isVisibleForApplication && it.id != "custom" }
        assertTrue("debe haber repartos que elegir", visible.isNotEmpty())
        visible.forEach { template ->
            val name = splitDisplayName(template.id)
            assertNotNull("${template.id}: sin nombre", name)
            assertTrue("${template.id}: nombre vacío", !name.isNullOrBlank())
            assertNotEquals("${template.id}: el nombre es el id", template.id, name)
            assertFalse("${template.id}: «$name» lleva un guion bajo", name.orEmpty().contains('_'))
            assertEquals("el nombre por id es el de la lista de repartos", splitDisplayName(template), name)
        }
    }
}
