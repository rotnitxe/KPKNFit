package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanLabels
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

    /**
     * C.P5 · «12 planes revisados · 3 encajan con tus respuestas»: cada cifra concuerda con su sustantivo y con su
     * verbo (uno: «1 plan revisado · 1 encaja»; ninguno: «ninguno encaja»), y los no viables ya no se cuentan aparte.
     */
    @Test
    fun candidateCountsAgreeForEachFigure() {
        assertEquals(
            "1 plan revisado · 1 encaja con tus respuestas",
            candidateCountsText(SetupCandidateCounts(evaluated = 1, viable = 1, nonViable = 0)),
        )
        assertEquals(
            "5 planes revisados · 1 encaja con tus respuestas",
            candidateCountsText(SetupCandidateCounts(evaluated = 5, viable = 1, nonViable = 4)),
        )
        assertEquals(
            "12 planes revisados · 3 encajan con tus respuestas",
            candidateCountsText(SetupCandidateCounts(evaluated = 12, viable = 3, nonViable = 9)),
        )
        assertEquals(
            "12 planes revisados · 7 encajan con tus respuestas",
            candidateCountsText(SetupCandidateCounts(evaluated = 12, viable = 7, nonViable = 5)),
        )
    }

    @Test
    fun candidateCountsToleratesASinglePlanAndNoViablePlan() {
        // Atleta completo evalúa un solo plan (DEC-w2-06): «1 plan revisado», nunca «1 planes».
        assertEquals(
            "1 plan revisado · ninguno encaja con tus respuestas",
            candidateCountsText(SetupCandidateCounts(evaluated = 1, viable = 0, nonViable = 1)),
        )
        assertEquals(
            "12 planes revisados · ninguno encaja con tus respuestas",
            candidateCountsText(SetupCandidateCounts(evaluated = 12, viable = 0, nonViable = 12)),
        )
    }

    /**
     * C.P6 · Con el pase a peso corporal las tarjetas que se ven no son las que se contaron (el conteo es del pase
     * pedido): el texto lo dice con un solo literal, para que «ninguno encaja» no parezca un error junto a unas tarjetas.
     */
    @Test
    fun candidateCountsSaysWhenTheCardsComeFromTheBodyweightPass() {
        assertEquals("te mostramos planes de peso corporal", ADAPTED_TO_BODYWEIGHT_COUNT_SUFFIX)
        assertEquals(
            "12 planes revisados · ninguno encaja con tus respuestas; te mostramos planes de peso corporal",
            candidateCountsText(SetupCandidateCounts(evaluated = 12, viable = 0, nonViable = 12), adaptedToBodyweight = true),
        )
        assertEquals(
            "1 plan revisado · ninguno encaja con tus respuestas; te mostramos planes de peso corporal",
            candidateCountsText(SetupCandidateCounts(evaluated = 1, viable = 0, nonViable = 1), adaptedToBodyweight = true),
        )
        // Sin el pase a peso corporal el texto es el de siempre.
        assertEquals(
            "12 planes revisados · 3 encajan con tus respuestas",
            candidateCountsText(SetupCandidateCounts(evaluated = 12, viable = 3, nonViable = 9), adaptedToBodyweight = false),
        )
        assertEquals(
            candidateCountsText(SetupCandidateCounts(evaluated = 5, viable = 1, nonViable = 4)),
            candidateCountsText(SetupCandidateCounts(evaluated = 5, viable = 1, nonViable = 4), adaptedToBodyweight = false),
        )
        for (evaluated in 0..14) {
            for (viable in 0..evaluated) {
                val text = candidateCountsText(SetupCandidateCounts(evaluated, viable, evaluated - viable), adaptedToBodyweight = true)
                assertTrue("«$text» mezcla 1 con un sustantivo en plural", !oneWithPlural.containsMatchIn(text))
                assertTrue("«$text» no cierra con el aviso del pase corporal", text.endsWith("; $ADAPTED_TO_BODYWEIGHT_COUNT_SUFFIX"))
            }
        }
    }

    @Test
    fun candidateCountsNeverPairsAnAmountWithTheWrongNounOrVerb() {
        for (evaluated in 0..14) {
            for (viable in 0..evaluated) {
                val text = candidateCountsText(SetupCandidateCounts(evaluated, viable, evaluated - viable))
                assertTrue("«$text» mezcla 1 con un sustantivo en plural", !oneWithPlural.containsMatchIn(text))
                // El límite de palabra evita confundir «11 encajan» con «1 encajan» y «10 encajan» con «0 encaja».
                assertTrue("«$text» dice «1 encajan»", !Regex("""\b1 encajan""").containsMatchIn(text))
                assertTrue("«$text» dice «0 encaja»", !Regex("""\b0 encaja""").containsMatchIn(text))
                assertTrue("«$text» no es la forma de «$evaluated planes revisados»", text.startsWith("$evaluated plan"))
                assertTrue("«$text» enseña una cifra de no viables", !text.contains("viable"))
            }
        }
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

    private fun entry(id: String) =
        requireNotNull(PersonalizedPlanCatalog.find(id)) { "Falta la entrada «$id» en el catálogo de planes" }
}
