package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.training.NativeProfileSplitWitness
import com.example.kpkn.domain.training.SplitApplicationEngine
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
 *
 * A.E2 (D6) · La lista de repartos del paso SPLIT recibe el objetivo: los repartos de powerlifting solo se ofrecen en
 * Fuerza, y el reparto destacado es el equivalente del calendario propio del objetivo (el único que su plan propio
 * acepta) cuando está en la lista. Al final de la clase.
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

    // ─── A.E2 (D6): la lista de repartos recibe el objetivo ────────────────────────────────────────────

    private val visiblePowerlifting = SPLIT_TEMPLATES.filter { it.isVisibleForApplication && SplitTag.POWERLIFTING in it.tags }

    private fun trainingDaysOf(split: com.example.kpkn.data.splits.SplitTemplate): Int =
        SplitApplicationEngine.patternToTrainingDays(split.pattern, startDay = 1).size

    private fun goalOf(profile: NativeProfileKind): SetupGoal = when (profile) {
        NativeProfileKind.STRENGTH -> SetupGoal.STRENGTH
        NativeProfileKind.MUSCLE -> SetupGoal.MUSCLE
        NativeProfileKind.POWERBUILDING -> SetupGoal.STRENGTH_MUSCLE
        NativeProfileKind.COMPLETE_ATHLETE -> SetupGoal.COMPLETE_ATHLETE
    }

    private fun offeredIds(days: Int?, goal: SetupGoal?): List<String> =
        compatibleSplitTemplates(days, startDay = 1, goal = goal).map { it.id }

    @Test
    fun powerliftingSplitsAreOfferedOnlyInStrength() {
        assertTrue("el catálogo publica repartos de powerlifting", visiblePowerlifting.size >= 2)
        (1..7).forEach { days ->
            SetupGoal.entries.forEach { goal ->
                val powerliftingOffered = compatibleSplitTemplates(days, startDay = 1, goal = goal)
                    .filter { SplitTag.POWERLIFTING in it.tags }
                    .map { it.id }
                if (goal == SetupGoal.STRENGTH) {
                    assertEquals(
                        "Fuerza con $days días los ofrece todos",
                        visiblePowerlifting.filter { trainingDaysOf(it) == days }.map { it.id },
                        powerliftingOffered,
                    )
                } else {
                    assertTrue("$goal con $days días ofrece $powerliftingOffered", powerliftingOffered.isEmpty())
                }
            }
        }
    }

    @Test
    fun theGoalOnlyTakesAwayPowerliftingSplitsAndNothingElse() {
        (listOf<Int?>(null) + (1..7)).forEach { days ->
            val unfiltered = compatibleSplitTemplates(days, startDay = 1)
            assertEquals("sin objetivo no se filtra por él", unfiltered, compatibleSplitTemplates(days, startDay = 1, goal = null))
            SetupGoal.entries.forEach { goal ->
                val expected = if (goal == SetupGoal.STRENGTH) unfiltered else unfiltered.filter { SplitTag.POWERLIFTING !in it.tags }
                assertEquals("$goal con $days días", expected.map { it.id }, offeredIds(days, goal))
            }
        }
    }

    @Test
    fun aMuscleListWithThreeDaysKeepsTheGeneralSplitsAndDropsEveryPowerliftingOne() {
        val muscle = offeredIds(3, SetupGoal.MUSCLE)
        listOf("fullbody_x3", "heavy_light", "ppl_x3", "ul_fb_x3").forEach { assertTrue("Músculo ofrece $it", it in muscle) }
        listOf("pl_sbd_x3", "texas_method", "madcow_5x5", "sheiko_3day", "korte_3x3").forEach {
            assertFalse("Músculo no debe ofrecer $it", it in muscle)
        }
        val strength = offeredIds(3, SetupGoal.STRENGTH)
        listOf("fullbody_x3", "pl_sbd_x3", "texas_method", "madcow_5x5", "sheiko_3day", "korte_3x3").forEach {
            assertTrue("Fuerza ofrece $it", it in strength)
        }
    }

    @Test
    fun theGoalFilterNeverHidesTheSplitTheOwnPlanOfThatGoalAccepts() {
        NativeProfileSplitWitness.allWitnesses().forEach { witness ->
            val goal = goalOf(witness.profile)
            assertTrue(
                "$goal con ${witness.days} días debe ofrecer ${witness.splitId}",
                witness.splitId in offeredIds(witness.days, goal),
            )
        }
    }

    @Test
    fun theFeaturedSplitIsTheOneTheOwnPlanAcceptsWhenTheListHasIt() {
        NativeProfileSplitWitness.allWitnesses().filter { it.pull != false }.forEach { witness ->
            val goal = goalOf(witness.profile)
            val featured = featuredSplitTemplate(compatibleSplitTemplates(witness.days, 1, goal), goal, witness.days)
            assertEquals("$goal con ${witness.days} días destaca su reparto equivalente", witness.splitId, featured?.id)
        }
        // El caso que antes engañaba: en Fuerza con 3 días el destacado era «Cuerpo completo, 3 días», que el plan
        // propio de Fuerza rechaza; ahora es el reparto de sentadilla, banca y peso muerto.
        val strengthThree = featuredSplitTemplate(compatibleSplitTemplates(3, 1, SetupGoal.STRENGTH), SetupGoal.STRENGTH, 3)
        assertEquals("pl_sbd_x3", strengthThree?.id)
        assertEquals("SBD Full Body x3", strengthThree?.let { splitDisplayName(it) })
    }

    @Test
    fun withoutAnEquivalentSplitTheFeaturedOneIsTheRecommendedOrTheFirst() {
        // Atleta y Fuerza con 4 días no tienen reparto equivalente: se conserva el criterio de siempre.
        listOf(SetupGoal.COMPLETE_ATHLETE, SetupGoal.STRENGTH).forEach { goal ->
            val compatible = compatibleSplitTemplates(4, 1, goal)
            val expected = compatible.firstOrNull { SplitTag.RECOMENDADO_KPKN in it.tags } ?: compatible.firstOrNull()
            assertEquals("$goal con 4 días", expected?.id, featuredSplitTemplate(compatible, goal, 4)?.id)
        }
        // Sin objetivo, sin días o sin lista tampoco se inventa nada.
        val compatible = compatibleSplitTemplates(4, 1, null)
        val recommended = compatible.firstOrNull { SplitTag.RECOMENDADO_KPKN in it.tags }
        assertEquals(recommended?.id, featuredSplitTemplate(compatible, null, 4)?.id)
        assertEquals(recommended?.id, featuredSplitTemplate(compatible, SetupGoal.MUSCLE, null)?.id)
        assertNull(featuredSplitTemplate(emptyList(), SetupGoal.MUSCLE, 4))
    }
}
