package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.SetupStepId
import org.junit.Assert.assertEquals
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
    fun weekdaysCounterAgreesWithTheTargetDays() {
        assertEquals("Elige 1 día · 0 de 1 elegido", weekdaysCounterText(target = 1, selectedCount = 0))
        assertEquals("Elige 1 día · 1 de 1 elegido", weekdaysCounterText(target = 1, selectedCount = 1))
        assertEquals("Elige 3 días · 2 de 3 elegidos", weekdaysCounterText(target = 3, selectedCount = 2))
        assertEquals("Días elegidos: 2", weekdaysCounterText(target = null, selectedCount = 2))
    }

    @Test
    fun customSplitNoticeDoesNotSayYourOneDays() {
        assertEquals("Define el foco de tu día (0 de 1).", customSplitPendingText(target = 1, defined = 0))
        assertEquals("Define el foco de tus 4 días (2 de 4).", customSplitPendingText(target = 4, defined = 2))
    }

    @Test
    fun candidateCountsAgreeForEachFigure() {
        assertEquals(
            "1 plan evaluado · 1 viable · 0 no viables",
            candidateCountsText(SetupCandidateCounts(evaluated = 1, viable = 1, nonViable = 0)),
        )
        assertEquals(
            "5 planes evaluados · 1 viable · 1 no viable",
            candidateCountsText(SetupCandidateCounts(evaluated = 5, viable = 1, nonViable = 1)),
        )
        assertEquals(
            "12 planes evaluados · 7 viables · 5 no viables",
            candidateCountsText(SetupCandidateCounts(evaluated = 12, viable = 7, nonViable = 5)),
        )
    }

    // ─── Series y repeticiones ───────────────────────────────────────────────

    @Test
    fun scratchExerciseLineUsesSingularForOneSetAndOneRep() {
        assertEquals("Peso muerto · 1 serie × 1 rep", scratchExerciseLine("Peso muerto", sets = 1, reps = 1))
        assertEquals("Press · 3 series × 8 reps", scratchExerciseLine("Press", sets = 3, reps = 8))
        assertEquals("Press · — series × — reps", scratchExerciseLine("Press", sets = null, reps = null))
    }

    @Test
    fun exerciseSummaryUsesSingularForOneSet() {
        val single = Exercise(id = "e1", name = "Peso muerto", sets = listOf(ExerciseSet(id = "s1", targetReps = 1)))
        assertEquals("1 serie × 1 rep", exerciseSummary(single))

        val several = Exercise(id = "e2", name = "Press", sets = (1..3).map { ExerciseSet(id = "s$it", targetReps = 8) })
        assertEquals("3 series × 8 rep", exerciseSummary(several))

        val timed = Exercise(id = "e3", name = "Plancha", sets = listOf(ExerciseSet(id = "s1", targetDuration = 30)))
        assertEquals("1 serie × 30 s", exerciseSummary(timed))
    }

    // ─── Validaciones de la semana ───────────────────────────────────────────

    @Test
    fun weekValidationMessageAgreesWithTheRequestedDays() {
        val one = SetupWizardDraft(daysPerWeek = 1, selectedWeekdays = setOf(1, 2))
        val oneMessage = SetupWizardValidation.validateStep(one, SetupStepId.WEEKDAYS).single().message
        assertEquals("Selecciona 1 día en tu semana", oneMessage)

        val three = SetupWizardDraft(daysPerWeek = 3, selectedWeekdays = setOf(1))
        val threeMessage = SetupWizardValidation.validateStep(three, SetupStepId.WEEKDAYS).single().message
        assertEquals("Selecciona 3 días en tu semana", threeMessage)
    }

    @Test
    fun weekChapterValidationAgreesWithTheRequestedDays() {
        val one = SetupWizardDraft(daysPerWeek = 1, selectedWeekdays = setOf(1, 2))
        assertEquals(
            "Selecciona 1 día en tu semana",
            SetupWizardValidation.validate(one, SetupWizardChapter.WEEK)["week"],
        )
        val five = SetupWizardDraft(daysPerWeek = 5, selectedWeekdays = setOf(1))
        assertEquals(
            "Selecciona 5 días en tu semana",
            SetupWizardValidation.validate(five, SetupWizardChapter.WEEK)["week"],
        )
    }

    // ─── Catálogo de planes ──────────────────────────────────────────────────

    @Test
    fun nativeSubtitleSaysOneDayForTheOneDayPlanAndKeepsRanges() {
        assertEquals("Semana cíclica · 1 día", entry("native:one-day").technicalSubtitle)
        // Rango con raya (U+2013): siempre plural, no se toca.
        assertEquals("Semana cíclica · 2–3 días", entry("native:full-body").technicalSubtitle)
    }

    @Test
    fun templateSubtitleSaysOneWeekForTheSingleWeekTemplate() {
        val simpleOne = entry("template:simple-1").technicalSubtitle
        assertTrue("subtítulo: $simpleOne", simpleOne.startsWith("1 semana · "))
        val simpleAb = entry("template:simple-ab").technicalSubtitle
        assertTrue("subtítulo: $simpleAb", simpleAb.startsWith("2 semanas · "))
    }

    @Test
    fun noCatalogTextEverPairsTheNumberOneWithAPluralNoun() {
        PersonalizedPlanCatalog.entries().forEach { entry ->
            listOf(entry.title, entry.technicalSubtitle, entry.description, entry.disclaimer.orEmpty())
                .forEach { text ->
                    assertTrue(
                        "${entry.id}: «$text» mezcla 1 con un sustantivo en plural",
                        !oneWithPlural.containsMatchIn(text),
                    )
                }
        }
    }

    private fun entry(id: String) =
        requireNotNull(PersonalizedPlanCatalog.find(id)) { "Falta la entrada «$id» en el catálogo de planes" }
}
