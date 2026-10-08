package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanLabels
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.design.entreno.daysPerWeekUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C3 · Concordancia en singular de los textos del wizard y del catálogo de
 * planes: con 1 día / 1 semana / 1 serie nunca sale «1 días» ni «1 series».
 * Son funciones puras de texto (sin Robolectric); la prueba que recorre el
 * ViewModel con un día vive en [SetupWizardOneDayCopyTest].
 */
class WizardPluralCopyTest {

    /** Cantidad uno seguida de un sustantivo en plural: justo lo que C3 elimina. */
    private val oneWithPlural =
        Regex("""\b1 (días|sesiones|semanas|ejercicios|series|bloques|planes|pasos)\b""")

    // ─── Motivo «encaja con tu semana» ───────────────────────────────────────

    @Test
    fun weekFitReasonUsesTheWholeSingularPhraseForOneDay() {
        assertEquals("Encaja con tu semana de 1 día", weekFitReason(1))
    }

    @Test
    fun weekFitReasonKeepsThePluralPhraseForSeveralDays() {
        assertEquals("Encaja con tus 2 días por semana", weekFitReason(2))
        assertEquals("Encaja con tus 3 días por semana", weekFitReason(3))
        assertEquals("Encaja con tus 6 días por semana", weekFitReason(6))
    }

    // ─── Contadores y avisos del bloque Entreno ──────────────────────────────

    @Test
    fun weekdaysSummaryAgreesWithTheNumberOfDays() {
        assertEquals("1 día · mié", weekdaysSummaryText(setOf(3)))
        assertEquals("2 días · lun, jue", weekdaysSummaryText(setOf(1, 4)))
        assertEquals("3 días · lun, mié, vie", weekdaysSummaryText(setOf(1, 3, 5)))
        assertEquals("Todos los días", weekdaysSummaryText((1..7).toSet()))
    }

    /** El contador del calendario («1 día por semana», «3 días por semana») concuerda con la cifra: nunca «1 días». */
    @Test
    fun theWeekCalendarCounterAgreesWithTheNumberOfDays() {
        assertEquals("1 día por semana", "1 ${daysPerWeekUnit(1)}")
        assertEquals("3 días por semana", "3 ${daysPerWeekUnit(3)}")
        assertEquals("7 días por semana", "7 ${daysPerWeekUnit(7)}")
        assertEquals("0 días por semana", "0 ${daysPerWeekUnit(0)}")
    }

    // ─── Validaciones de la semana ───────────────────────────────────────────

    @Test
    fun weekValidationAsksForAtLeastOneDayAndSaysTheRange() {
        val none = SetupWizardDraft()
        assertEquals(
            "Elige al menos un día para entrenar.",
            SetupWizardValidation.validateStep(none, SetupStepId.WEEKDAYS).single().message,
        )
        val tooMany = SetupWizardDraft(selectedWeekdays = setOf(1, 9))
        assertEquals(
            "Elige entre 1 y 7 días.",
            SetupWizardValidation.validateStep(tooMany, SetupStepId.WEEKDAYS).single().message,
        )
        // Un solo día es válido y no pide nada más.
        assertTrue(SetupWizardValidation.validateStep(SetupWizardDraft(selectedWeekdays = setOf(2)), SetupStepId.WEEKDAYS).none { it.isBlocking })
    }

    @Test
    fun weekChapterValidationAsksForDaysMinutesAndPlaces() {
        val empty = SetupWizardValidation.validate(SetupWizardDraft(), SetupWizardChapter.WEEK)
        assertEquals("Elige los días que quieres entrenar", empty["days"])
        assertEquals("Indica el tiempo disponible", empty["minutes"])
        assertEquals("Elige al menos un lugar", empty["equipment"])
        val ready = SetupWizardDraft(selectedWeekdays = setOf(1, 3), minutesPerSession = 60).withPlaces(setOf(TrainingPlace.GYM))
        assertTrue(SetupWizardValidation.validate(ready, SetupWizardChapter.WEEK).isEmpty())
    }

    // ─── Catálogo de planes ──────────────────────────────────────────────────

    @Test
    fun nativeSubtitleDropsTheDaysAndTheFrequencyLabelKeepsTheSingularAndTheRanges() {
        // El subtítulo es «{duración} · {nivel}»: los días van en el motivo y en la línea de metadatos.
        assertEquals("Semana que se repite · Todos los niveles", entry("native:one-day").technicalSubtitle)
        assertEquals("Semana que se repite · Todos los niveles", entry("native:full-body").technicalSubtitle)
        assertEquals("1 día por semana", PlanLabels.frequencyLabel(entry("native:one-day").supportedFrequencies))
        // Rango con raya (U+2013): siempre plural, no se toca.
        assertEquals("2–3 días por semana", PlanLabels.frequencyLabel(entry("native:full-body").supportedFrequencies))
    }

    @Test
    fun templateSubtitleSaysOneRepeatingWeekForTheSingleWeekTemplate() {
        val simpleOne = entry("template:simple-1").technicalSubtitle
        assertTrue("subtítulo: $simpleOne", simpleOne.startsWith("Semana que se repite · "))
        val simpleAb = entry("template:simple-ab").technicalSubtitle
        assertTrue("subtítulo: $simpleAb", simpleAb.startsWith("2 semanas · "))
    }

    @Test
    fun noCatalogTextEverPairsTheNumberOneWithAPluralNoun() {
        PersonalizedPlanCatalog.entries().forEach { entry ->
            listOf(
                entry.title,
                entry.technicalSubtitle,
                entry.description,
                entry.disclaimer.orEmpty(),
                entry.displayName,
                PlanLabels.subtitle(entry),
                PlanLabels.metaLine(entry),
                entry.summary,
                entry.attributionLine.orEmpty(),
            ).forEach { text ->
                assertTrue(
                    "${entry.id}: «$text» mezcla 1 con un sustantivo en plural",
                    !oneWithPlural.containsMatchIn(text),
                )
            }
        }
    }

    @Test
    fun noPlanRepeatsTheSameNumberOfDaysInItsNameSubtitleAndSummary() {
        val numberOfDays = Regex("""\b(\d+) días\b""")
        PersonalizedPlanCatalog.entries().forEach { entry ->
            // Los dos Texas llevan los días en el nombre («Texas Method (3 días)») para distinguir
            // las dos variantes, y su resumen los vuelve a citar: es la única repetición aprobada.
            if (numberOfDays.containsMatchIn(entry.displayName)) return@forEach
            val text = entry.displayName + " " + PlanLabels.subtitle(entry) + " " + entry.summary
            val cited = numberOfDays.findAll(text).map { it.value }.toList()
            assertEquals("${entry.id}: repite el mismo «N días»: $cited", cited.size, cited.toSet().size)
        }
    }

    // ─── Hitos entre bloques: las etapas salen de la ruta del borrador (H8/H15) ────────────────

    private fun routeOf(draft: SetupWizardDraft): List<SetupStepId> = SetupStepGraph.stepIds(draft.stepContext())

    @Test
    fun theFullRouteListsEveryBlockInOrder() {
        assertEquals(
            listOf(
                SetupWizardBlock.BASICS,
                SetupWizardBlock.TRAINING,
                SetupWizardBlock.NUTRITION,
                SetupWizardBlock.RINGS,
                SetupWizardBlock.REVIEW,
            ),
            milestoneBlocks(routeOf(SetupWizardDraft())),
        )
    }

    @Test
    fun theTrainingOnlyRouteNeverTalksAboutNutritionOrRings() {
        // El asistente de solo entreno que abre la biblioteca con el alta ya completa.
        val route = routeOf(SetupWizardDraft(draftScope = "training_only", includeNutrition = false))
        assertEquals(
            listOf(SetupWizardBlock.BASICS, SetupWizardBlock.TRAINING, SetupWizardBlock.REVIEW),
            milestoneBlocks(route),
        )
        val labels = milestoneBlocks(route).map(::milestoneStageLabel)
        assertEquals(listOf("Básicos", "Entreno", "Revisión"), labels)
        assertFalse(labels.any { it.contains("Nutrición") || it.contains("Rings") })
    }

    @Test
    fun aFullRouteWithoutNutritionSkipsItInTheStages() {
        val route = routeOf(SetupWizardDraft(includeNutrition = false))
        assertEquals(listOf("Básicos", "Entreno", "Rings", "Revisión"), milestoneBlocks(route).map(::milestoneStageLabel))
    }

    // ─── Resumen del hito de Entreno: lugares, material, objetivo, días y programa ──────────

    private fun milestoneState(draft: SetupWizardDraft) = SetupWizardState(draft = draft)

    @Test
    fun theMilestoneSummaryUsesTheNamesOfTheFinalReview() {
        val draft = SetupWizardDraft(selectedWeekdays = setOf(1, 3, 5), minutesPerSession = 60)
            .withPlaces(setOf(TrainingPlace.GYM, TrainingPlace.HOME))
            .withGoalProfile(TrainingGoalProfile.POWERLIFTING)
            .withStepChoice(SetupStepId.EXPERIENCE, "intermediate")
        val rows = trainingMilestoneRows(milestoneState(draft))

        assertEquals("Experiencia" to "Ya entreno con constancia", rows.first())
        assertTrue("$rows", rows.contains("Lugares" to "Gimnasio y casa"))
        assertTrue("$rows", rows.contains("Objetivo" to "Powerlifting"))
        assertTrue("$rows", rows.contains("Días y tiempo" to "3 días · lun, mié, vie · 60 min"))
        // Ya no hay fila de reparto: la semana la arma el programa.
        assertTrue("$rows", rows.none { (label, _) -> label == "Reparto" || label == "Split" })
        // Sin plan elegido tampoco hay fila de programa y nunca se pinta un id.
        assertTrue("$rows", rows.none { (label, _) -> label == "Programa" })
    }

    @Test
    fun theMilestoneProgramRowNamesTheChosenPlanAndNeverShowsAnUnknownId() {
        fun programRows(draft: SetupWizardDraft) =
            trainingMilestoneRows(milestoneState(draft)).filter { (label, _) -> label == "Programa" }

        val entry = PersonalizedPlanCatalog.listedEntries().first()
        assertEquals(listOf("Programa" to entry.displayName), programRows(SetupWizardDraft(selectedCatalogId = entry.id)))
        assertEquals(listOf("Programa" to "Tu programa elegido"), programRows(SetupWizardDraft(selectedCatalogId = "id_que_no_existe")))
        assertEquals(listOf("Programa" to DEFER_PROGRAM_REVIEW_VALUE), programRows(SetupWizardDraft(programRoute = SetupProgramRoute.LATER)))
        // Las marcas, solo si se declararon.
        val marks = trainingMilestoneRows(milestoneState(SetupWizardDraft().withLiftMark(LiftMark.SQUAT, 140.0)))
        assertTrue("$marks", marks.contains("Marcas" to "140 kg"))
        assertTrue(trainingMilestoneRows(milestoneState(SetupWizardDraft())).none { (label, _) -> label == "Marcas" })
    }

    private fun entry(id: String) =
        requireNotNull(PersonalizedPlanCatalog.find(id)) { "Falta la entrada «$id» en el catálogo de planes" }
}
