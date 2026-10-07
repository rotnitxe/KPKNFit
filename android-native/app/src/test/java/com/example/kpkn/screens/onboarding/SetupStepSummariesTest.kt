package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.nutrition.NutritionWizardDraft
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.RingsChannelCoverage
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsCoverageSource
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupControlKind
import com.example.kpkn.domain.onboarding.SetupInventoryGroup
import com.example.kpkn.domain.onboarding.SetupNutritionPreparationResult
import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.TrainingOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Textos de las filas-resumen de la página larga ([setupStepSummary]): etiqueta ≤ 24 y
 * valor de una línea ≤ 44 para TODOS los pasos, y lo que dicen en los casos que importan
 * (alias y edad, altura y peso en ambas unidades, elecciones únicas y múltiples, numéricos,
 * omitido / sin responder, hitos y pasos de resultado). JVM puro.
 */
class SetupStepSummariesTest {

    private companion object {
        const val NOW = 1_700_000_000_000L
    }

    // ─── Constructores de borrador / estado ──────────────────────────────────

    private fun summary(page: SetupStepId, draft: SetupWizardDraft = SetupWizardDraft()): SetupStepSummary =
        setupStepSummary(page, SetupWizardState(draft = draft))

    private fun summary(page: SetupStepId, state: SetupWizardState): SetupStepSummary =
        setupStepSummary(page, state)

    private fun value(page: SetupStepId, draft: SetupWizardDraft = SetupWizardDraft()): String =
        summary(page, draft).value

    /** El paso quedó confirmado (un registro de respuesta) sin tocar su dato. */
    private fun SetupWizardDraft.answered(step: SetupStepId): SetupWizardDraft =
        recordStepAnswer(step, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)

    /** Lo que deja `skipStep`: registro de respuesta declarada pero ausente, sin ningún dato. */
    private fun SetupWizardDraft.skipped(step: SetupStepId): SetupWizardDraft =
        recordStepAnswer(step, SetupAnswerProvenance.USER_DECLARED, SetupValueState.ABSENT)

    private fun SetupWizardDraft.choose(step: SetupStepId, value: String): SetupWizardDraft =
        withStepChoice(step, value, NOW)

    private fun SetupWizardDraft.chooseAll(step: SetupStepId, vararg values: String): SetupWizardDraft =
        withStepChoices(step, values.toSet(), NOW)

    private fun isLegacy(step: SetupStepId): Boolean = SetupStepDefinitions.isLegacyOnly(step)

    private fun assertWithinLimits(step: SetupStepId, summary: SetupStepSummary, where: String) {
        val tag = "$where · $step"
        assertTrue("$tag: etiqueta vacía", summary.label.isNotBlank())
        assertTrue("$tag: etiqueta «${summary.label}» de ${summary.label.length} > $SETUP_SUMMARY_LABEL_MAX", summary.label.length <= SETUP_SUMMARY_LABEL_MAX)
        assertTrue("$tag: valor vacío", summary.value.isNotBlank())
        assertTrue("$tag: valor «${summary.value}» de ${summary.value.length} > $SETUP_SUMMARY_VALUE_MAX", summary.value.length <= SETUP_SUMMARY_VALUE_MAX)
        assertFalse("$tag: el valor no es de una línea", summary.value.any { it == '\n' || it == '\r' || it == '\t' })
        assertFalse("$tag: la etiqueta no es de una línea", summary.label.any { it == '\n' || it == '\r' || it == '\t' })
    }

    /** Rutas con todas las ramas condicionales: cardio, capacidades, marcas, inventarios y los tres modos de nutrición. */
    private val routeContexts = listOf(
        SetupStepContext(),
        SetupStepContext(
            goalIncludesCardio = true,
            asksCapabilities = true,
            asksMarks = true,
            inventoryGroups = SetupInventoryGroup.entries.toSet(),
            nutritionStartChoice = "automatic",
            nutritionDirection = "deficit",
            recentTraining = true,
        ),
        SetupStepContext(nutritionStartChoice = "self_defined", nutritionDirection = "surplus"),
        SetupStepContext(nutritionStartChoice = "tracking_only"),
        SetupStepContext(nutritionProfessional = true),
        SetupStepContext(hasWeekLayout = false, asksTechnique = false),
    )

    // ─── Cobertura y límites ─────────────────────────────────────────────────

    @Test
    fun everyStepOfTheFullRouteHasALabelAndAValueWithinTheLimits() {
        val routeSteps = routeContexts.flatMap { SetupStepGraph.stepIds(it) }.toSet()

        // El barrido no se encoge en silencio: la ruta trae todo paso que no sea solo lectura de borradores antiguos.
        val expected = SetupStepId.entries.filterNot { isLegacy(it) }
        assertTrue("pasos fuera de las rutas probadas: ${expected - routeSteps}", routeSteps.containsAll(expected))

        routeSteps.forEach { step -> assertWithinLimits(step, summary(step), "borrador por defecto") }
    }

    @Test
    fun everyStepIncludingTheLegacyOnesSummarizesTheDefaultDraftWithoutThrowing() {
        SetupStepId.entries.forEach { step -> assertWithinLimits(step, summary(step), "borrador por defecto") }
    }

    @Test
    fun aFullyAnsweredDraftKeepsEveryRowWithinTheLimits() {
        val state = richState()
        SetupStepId.entries.forEach { step -> assertWithinLimits(step, summary(step, state), "borrador completo") }
    }

    @Test
    fun labelsAreDistinctAcrossStepsAndStartWithACapital() {
        val labels = SetupStepId.entries.associateWith { step -> summary(step).label }
        labels.forEach { (step, label) ->
            assertTrue("$step: «$label» no empieza en mayúscula", label.first().isUpperCase())
            assertFalse("$step: «$label» acaba en puntuación", label.last() in ".:;,?!")
        }
        val repeated = labels.entries.groupBy({ it.value.lowercase() }, { it.key }).filterValues { it.size > 1 }
        assertTrue("etiquetas repetidas: $repeated", repeated.isEmpty())
    }

    @Test
    fun aFreshDraftOnlyShowsHonestPlaceholdersOrTheContractDefaults() {
        // Sin respuestas: «Sin responder», «Listo» (resultados y la semana) y «Completado» (hitos).
        val allowed = setOf("Sin responder", "Listo", "Completado")
        SetupStepId.entries.forEach { step ->
            val shown = value(step)
            assertTrue("$step enseña «$shown» con el borrador por defecto", shown in allowed)
        }
    }

    // ─── Datos básicos ───────────────────────────────────────────────────────

    @Test
    fun nameShowsTheAliasAndTheAge() {
        val result = summary(SetupStepId.NAME, SetupWizardDraft(name = "Matías", ageYears = 36))
        assertEquals(SetupStepSummary(label = "Alias y edad", value = "Matías · 36 años"), result)
    }

    @Test
    fun nameWithOnlyTheAliasOrOnlyTheAgeShowsWhatExists() {
        assertEquals("Matías", value(SetupStepId.NAME, SetupWizardDraft(name = "Matías")))
        assertEquals("36 años", value(SetupStepId.NAME, SetupWizardDraft(ageYears = 36)))
        assertEquals("Sin responder", value(SetupStepId.NAME))
    }

    @Test
    fun aSkippedAliasReadsOmitidoAndAnUnskippableEmptyStepNeverDoes() {
        assertEquals("Omitido", value(SetupStepId.NAME, SetupWizardDraft().skipped(SetupStepId.NAME)))
        // La edad no se puede omitir: confirmada sin dato sigue sin responder.
        assertEquals("Sin responder", value(SetupStepId.AGE, SetupWizardDraft().answered(SetupStepId.AGE)))
    }

    @Test
    fun aLongAliasIsTrimmedButTheAgeIsKept() {
        val shown = value(SetupStepId.NAME, SetupWizardDraft(name = "A".repeat(40), ageYears = 36))

        assertEquals(SETUP_SUMMARY_VALUE_MAX, shown.length)
        assertTrue(shown, shown.endsWith(" · 36 años"))
        assertEquals("A".repeat(33) + "…", shown.removeSuffix(" · 36 años"))
    }

    @Test
    fun anAliasWithLineBreaksStaysOnOneLine() {
        assertEquals("Mati Saldías · 36 años", value(SetupStepId.NAME, SetupWizardDraft(name = " Mati \n Saldías ", ageYears = 36)))
    }

    @Test
    fun heightAndWeightInCentimetersAndKilograms() {
        val result = summary(SetupStepId.HEIGHT, SetupWizardDraft(heightCm = 172.0, weightKg = 70.1))
        assertEquals(SetupStepSummary(label = "Altura y peso", value = "172 cm · 70,1 kg"), result)
        // Un peso entero no arrastra decimales.
        assertEquals("172 cm · 70 kg", value(SetupStepId.HEIGHT, SetupWizardDraft(heightCm = 172.0, weightKg = 70.0)))
    }

    @Test
    fun heightAndWeightInFeetAndPounds() {
        val draft = SetupWizardDraft(heightCm = 172.0, weightKg = 70.1, heightUnit = "ft", weightUnit = "lb")
        assertEquals("5′ 8″ · 154,5 lb", value(SetupStepId.HEIGHT, draft))
        // Cada medida lleva su propia unidad.
        assertEquals("172 cm · 154,5 lb", value(SetupStepId.HEIGHT, draft.copy(heightUnit = "cm")))
        assertEquals("5′ 8″ · 70,1 kg", value(SetupStepId.HEIGHT, draft.copy(weightUnit = "kg")))
        // La unidad persistida admite los alias que lee la UI.
        assertEquals("5′ 8″ · 70,1 kg", value(SetupStepId.HEIGHT, draft.copy(heightUnit = "imperial", weightUnit = "kg")))
    }

    @Test
    fun heightAndWeightShowOnlyWhatExists() {
        assertEquals("172 cm", value(SetupStepId.HEIGHT, SetupWizardDraft(heightCm = 172.0)))
        assertEquals("70 kg", value(SetupStepId.HEIGHT, SetupWizardDraft(weightKg = 70.0)))
        assertEquals("Sin responder", value(SetupStepId.HEIGHT))
    }

    @Test
    fun ageAndWeightHaveTheirOwnSummaryForRoutesWithoutTheMergedPages() {
        assertEquals(SetupStepSummary("Edad", "36 años"), summary(SetupStepId.AGE, SetupWizardDraft(ageYears = 36)))
        assertEquals(SetupStepSummary("Peso", "70,1 kg"), summary(SetupStepId.WEIGHT, SetupWizardDraft(weightKg = 70.1)))
        assertEquals("154,5 lb", value(SetupStepId.WEIGHT, SetupWizardDraft(weightKg = 70.1, weightUnit = "lb")))
        assertEquals("Sin responder", value(SetupStepId.WEIGHT))
    }

    @Test
    fun equationSexShowsTheCatalogLabel() {
        assertEquals(SetupStepSummary("Género", "Mujer"), summary(SetupStepId.EQUATION_SEX, SetupWizardDraft().choose(SetupStepId.EQUATION_SEX, "female")))
        assertEquals("Hombre", value(SetupStepId.EQUATION_SEX, SetupWizardDraft().choose(SetupStepId.EQUATION_SEX, "male")))
        assertEquals("Hombre trans", value(SetupStepId.EQUATION_SEX, SetupWizardDraft().choose(SetupStepId.EQUATION_SEX, "trans_male")))
        assertEquals("Mujer trans", value(SetupStepId.EQUATION_SEX, SetupWizardDraft().choose(SetupStepId.EQUATION_SEX, "trans_female")))
        assertEquals("No lo sé", value(SetupStepId.EQUATION_SEX, SetupWizardDraft().choose(SetupStepId.EQUATION_SEX, "unknown")))
        assertEquals("Sin responder", value(SetupStepId.EQUATION_SEX))
    }

    @Test
    fun equationSexReadsTheTypedValueOfARehydratedDraft() {
        val rehydrated = SetupWizardDraft(nutritionDraft = NutritionWizardDraft(equationSex = EerSex.MALE))
        assertEquals("Hombre", value(SetupStepId.EQUATION_SEX, rehydrated))
        assertEquals("Mujer", value(SetupStepId.EQUATION_SEX, SetupWizardDraft(nutritionDraft = NutritionWizardDraft(equationSex = EerSex.FEMALE))))
        // El promedio solo nace del equilibrio hormonal, así que se resume con esa respuesta.
        assertEquals(
            "Equilibrio hormonal",
            value(SetupStepId.EQUATION_SEX, SetupWizardDraft(nutritionDraft = NutritionWizardDraft(equationSex = EerSex.AVERAGE))),
        )
    }

    @Test
    fun equationSexHormonalAnswersAreSummarizedAsTheContextTheyDeclare() {
        assertEquals(
            SetupStepSummary("Género", "Estrógenos predominantes"),
            summary(SetupStepId.EQUATION_SEX, SetupWizardDraft().choose(SetupStepId.EQUATION_SEX, "hormones_estrogen")),
        )
        assertEquals("Andrógenos predominantes", value(SetupStepId.EQUATION_SEX, SetupWizardDraft().choose(SetupStepId.EQUATION_SEX, "hormones_androgen")))
        assertEquals("Equilibrio hormonal", value(SetupStepId.EQUATION_SEX, SetupWizardDraft().choose(SetupStepId.EQUATION_SEX, "hormones_mixed")))

        // Todas caben en una línea del resumen, junto a las cuatro de glifo y «No lo sé».
        SetupStepDefinitions.optionValues(SetupStepId.EQUATION_SEX).forEach { option ->
            val draft = SetupWizardDraft().choose(SetupStepId.EQUATION_SEX, option)
            assertWithinLimits(SetupStepId.EQUATION_SEX, summary(SetupStepId.EQUATION_SEX, draft), "género $option")
        }
        // La revisión final dice lo mismo que la fila-resumen para las tres respuestas hormonales.
        assertEquals("Estrógenos predominantes", equationSexHormonalSummary("hormones_estrogen"))
        assertEquals("Andrógenos predominantes", equationSexHormonalSummary("hormones_androgen"))
        assertEquals("Equilibrio hormonal", equationSexHormonalSummary("hormones_mixed"))
        // Los glifos y «No lo sé» no tienen texto hormonal: usan la etiqueta del catálogo.
        listOf("female", "male", "trans_male", "trans_female", "unknown").forEach { option ->
            assertNull(option, equationSexHormonalSummary(option))
        }
    }

    @Test
    fun bodyFatReusesTheTextOfTheFinalReview() {
        val visual = SetupWizardDraft()
            .choose(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name)
            .withStepNumber(SetupStepId.BODY_FAT, 18.0, NOW)
        assertEquals(SetupStepSummary("Grasa corporal", "18 % · estimación visual"), summary(SetupStepId.BODY_FAT, visual))
        assertEquals(bodyFatReviewValue(visual), value(SetupStepId.BODY_FAT, visual))

        val measured = SetupWizardDraft()
            .choose(SetupStepId.BODY_FAT, SetupBodyFatSource.MEASURED.name)
            .withStepText(SetupStepId.BODY_FAT, "17,5", NOW)
        assertEquals("17,5 % · medido", value(SetupStepId.BODY_FAT, measured))
        assertEquals(bodyFatReviewValue(measured), value(SetupStepId.BODY_FAT, measured))

        // «Omitir este paso» deja la fuente en «No lo sé»; la revisión final dice lo mismo.
        val omitted = SetupWizardDraft().choose(SetupStepId.BODY_FAT, SetupBodyFatSource.UNKNOWN.name)
        assertEquals("No lo sé", value(SetupStepId.BODY_FAT, omitted))
        assertEquals(bodyFatReviewValue(omitted), value(SetupStepId.BODY_FAT, omitted))
    }

    @Test
    fun bodyFatSavedInSettingsIsNotPresentedAsAnAnswerAndNothingReadsSinResponder() {
        assertEquals("Guardada en Ajustes: 21 %", value(SetupStepId.BODY_FAT, SetupWizardDraft(importedBodyFatPercent = 21.0)))
        assertEquals("Sin responder", value(SetupStepId.BODY_FAT))
        // Una fuente real sin porcentaje no es un dato.
        assertEquals("Sin responder", value(SetupStepId.BODY_FAT, SetupWizardDraft(bodyFatSource = SetupBodyFatSource.MEASURED)))
    }

    // ─── Elecciones únicas y múltiples ───────────────────────────────────────

    @Test
    fun aSingleChoiceShowsTheLabelOfTheChosenOption() {
        assertEquals(
            SetupStepSummary("Experiencia", "Ya entreno con constancia"),
            summary(SetupStepId.EXPERIENCE, SetupWizardDraft().choose(SetupStepId.EXPERIENCE, "intermediate")),
        )
        assertEquals("Fuerza y masa muscular", value(SetupStepId.GOAL, SetupWizardDraft().choose(SetupStepId.GOAL, "strength_muscle")))
        assertEquals("Powerlifting", value(SetupStepId.GOAL, SetupWizardDraft().choose(SetupStepId.GOAL, "powerlifting")))
        assertEquals("Gimnasio", value(SetupStepId.EQUIPMENT, SetupWizardDraft().choose(SetupStepId.EQUIPMENT, "gym")))
        assertEquals("No lo sé", value(SetupStepId.RINGS_RECENT, SetupWizardDraft().choose(SetupStepId.RINGS_RECENT, "unknown")))
        assertEquals("Sin responder", value(SetupStepId.EXPERIENCE))
        assertEquals("Sin responder", value(SetupStepId.GOAL))
    }

    @Test
    fun theLegacyGoalsKeepTheirOwnLabelBecauseTheCatalogOffersNoOptionForThem() {
        assertEquals("Salud y condición", value(SetupStepId.GOAL, SetupWizardDraft(goal = SetupGoal.HEALTH)))
        assertEquals("Fuerza + cardio", value(SetupStepId.GOAL, SetupWizardDraft(goal = SetupGoal.MIXED)))
    }

    @Test
    fun aRehydratedDraftReadsItsTypedAnswers() {
        assertEquals("Casa", value(SetupStepId.EQUIPMENT, SetupWizardDraft(trainingPlaces = setOf(TrainingPlace.HOME))))
        assertEquals("Estoy volviendo", value(SetupStepId.EXPERIENCE, SetupWizardDraft(experience = SetupExperience.RETURNING)))
        assertEquals("Fuerza y masa muscular", value(SetupStepId.GOAL, SetupWizardDraft(goalProfile = TrainingGoalProfile.STRENGTH_MUSCLE)))
        // Los pasos retirados siguen leyendo lo que un borrador antiguo guardó.
        assertEquals("Músculo", value(SetupStepId.STYLE, SetupWizardDraft().choose(SetupStepId.STYLE, "bodybuilder")))
    }

    @Test
    fun placesAreNamedInTheContractOrderAndTheFirstOneStartsTheSentence() {
        assertEquals(SetupStepSummary("Dónde entrenas", "Gimnasio"), summary(SetupStepId.EQUIPMENT, SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM))))
        assertEquals("Gimnasio y casa", value(SetupStepId.EQUIPMENT, SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME, TrainingPlace.GYM))))
        assertEquals(
            "Gimnasio, casa y espacios públicos",
            value(SetupStepId.EQUIPMENT, SetupWizardDraft().withPlaces(TrainingPlace.entries.toSet())),
        )
        assertEquals("Espacios públicos", value(SetupStepId.EQUIPMENT, SetupWizardDraft().withPlaces(setOf(TrainingPlace.PUBLIC))))
        assertEquals("Sin responder", value(SetupStepId.EQUIPMENT))
        // La revisión final dice lo mismo que la fila-resumen.
        assertEquals("Gimnasio y casa", placesSummaryText(setOf(TrainingPlace.GYM, TrainingPlace.HOME)))
        assertNull(placesSummaryText(emptySet()))
    }

    @Test
    fun theFreshDayAndTheSessionTimeReadWhatWasDeclared() {
        assertEquals(SetupStepSummary("Día con más energía", "Jueves"), summary(SetupStepId.FRESH_DAY, SetupWizardDraft().withFreshestDay(4)))
        assertEquals("Sin responder", value(SetupStepId.FRESH_DAY))
        assertEquals(SetupStepSummary("Tiempo por sesión", "75 min"), summary(SetupStepId.SESSION_TIME, SetupWizardDraft().withSessionMinutes(75)))
    }

    @Test
    fun capabilitiesNameTheExercisesThatAlreadyWorkAndSayAunNingunoOtherwise() {
        val draft = SetupWizardDraft()
            .withCapability(CapabilitySkill.PULL_UP, CapabilityLevel.SOME)
            .withCapability(CapabilitySkill.PUSH_UP, CapabilityLevel.MANY)
            .withCapability(CapabilitySkill.DIP, CapabilityLevel.NONE)
        assertEquals(SetupStepSummary("Ejercicios que te salen", "Dominadas, Flexiones"), summary(SetupStepId.CAPABILITIES, draft))
        assertEquals(
            "Aún ninguno",
            value(SetupStepId.CAPABILITIES, SetupWizardDraft().withCapability(CapabilitySkill.DIP, CapabilityLevel.NONE)),
        )
        assertEquals("Sin responder", value(SetupStepId.CAPABILITIES))
    }

    @Test
    fun aMultipleChoiceShowsTwoLabelsAndCountsTheRest() {
        // Los días de la semana dicen cuántos son y cuáles (en orden de semana).
        val three = SetupWizardDraft().chooseAll(SetupStepId.WEEKDAYS, "5", "1", "3")
        assertEquals(SetupStepSummary("Días de entreno", "3 días · lun, mié, vie"), summary(SetupStepId.WEEKDAYS, three))

        val seven = SetupWizardDraft().chooseAll(SetupStepId.WEEKDAYS, "1", "2", "3", "4", "5", "6", "7")
        assertEquals("Todos los días", value(SetupStepId.WEEKDAYS, seven))

        assertEquals("2 días · mar, jue", value(SetupStepId.WEEKDAYS, SetupWizardDraft().chooseAll(SetupStepId.WEEKDAYS, "4", "2")))
        assertEquals("1 día · dom", value(SetupStepId.WEEKDAYS, SetupWizardDraft().chooseAll(SetupStepId.WEEKDAYS, "7")))
        assertEquals("Sin responder", value(SetupStepId.WEEKDAYS))
        // La revisión final dice lo mismo que la fila-resumen.
        assertEquals("3 días · lun, mié, vie", weekdaysSummaryText(setOf(5, 1, 3)))
        assertNull(weekdaysSummaryText(emptySet()))
    }

    @Test
    fun aMultipleChoiceFollowsTheCatalogOrderAndExclusiveValuesStandAlone() {
        val gym = SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM))
        val symbols = gym.chooseAll(SetupStepId.AVAILABILITY, "CABLE", "BARBELL", "DUMBBELLS", "KETTLEBELL")
            .answered(SetupStepId.AVAILABILITY)
        assertEquals(SetupStepSummary("Material", "Barra y discos, Mancuernas +2"), summary(SetupStepId.AVAILABILITY, symbols))

        assertEquals(
            "Solo peso corporal",
            value(SetupStepId.AVAILABILITY, gym.chooseAll(SetupStepId.AVAILABILITY, "BODYWEIGHT_ONLY").answered(SetupStepId.AVAILABILITY)),
        )

        assertEquals("Ninguna de estas", value(SetupStepId.NUTRITION_ELIGIBILITY, SetupWizardDraft().chooseAll(SetupStepId.NUTRITION_ELIGIBILITY, "none")))
        assertEquals("Embarazo, Lactancia", value(SetupStepId.NUTRITION_ELIGIBILITY, SetupWizardDraft().chooseAll(SetupStepId.NUTRITION_ELIGIBILITY, "lactation", "pregnancy")))
        assertEquals(
            "No lo sé / prefiero no responder",
            value(SetupStepId.NUTRITION_ELIGIBILITY, SetupWizardDraft().chooseAll(SetupStepId.NUTRITION_ELIGIBILITY, "unknown")),
        )
    }

    @Test
    fun theMaterialAnEnvironmentSeedsIsNotAnAnswerUntilTheStepIsConfirmed() {
        // «Gimnasio» siembra el material habitual; la persona todavía no lo ha confirmado.
        val seeded = SetupWizardDraft().choose(SetupStepId.EQUIPMENT, "gym")
        assertEquals("Sin responder", value(SetupStepId.AVAILABILITY, seeded))

        // Confirmado el paso, la selección vigente es lo que declaró: los símbolos sembrados, en el orden del catálogo.
        val seed = EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM))
        val labels = EquipmentSymbolId.entries.filter { it in seed }.map { it.label }
        assertTrue("el gimnasio siembra varios implementos", labels.size > 2)
        assertEquals(
            "${labels[0]}, ${labels[1]} +${labels.size - 2}",
            value(SetupStepId.AVAILABILITY, seeded.answered(SetupStepId.AVAILABILITY)),
        )
    }

    @Test
    fun aListThatDoesNotFitIsCutWithAnEllipsisOnOneLine() {
        val catalog = SetupStepDefinitions.options(SetupStepId.RINGS_DISCOMFORT)
        val longest = catalog.filter { it.value != "none" && it.value != "omit" }.sortedByDescending { it.label.length }.take(3)
        val draft = SetupWizardDraft().withStepChoices(SetupStepId.RINGS_DISCOMFORT, longest.map { it.value }.toSet(), NOW)

        // Las dos primeras etiquetas en el orden del catálogo, más « +1», no caben en 44.
        val inCatalogOrder = catalog.filter { option -> option in longest }.map { it.label }
        val complete = inCatalogOrder.take(2).joinToString(", ") + " +1"
        assertTrue("el caso no estresa el tope: «$complete»", complete.length > SETUP_SUMMARY_VALUE_MAX)

        val shown = value(SetupStepId.RINGS_DISCOMFORT, draft)
        assertEquals(complete.take(SETUP_SUMMARY_VALUE_MAX - 1) + "…", shown)
        assertEquals(SETUP_SUMMARY_VALUE_MAX, shown.length)
    }

    @Test
    fun aValueTheCatalogDoesNotKnowReadsHumanlyInsteadOfAsARawId() {
        // El catálogo solo ofrece tres cardios, pero un borrador antiguo puede traer otro.
        assertEquals("Bike stationary", value(SetupStepId.CARDIO_TYPE, SetupWizardDraft(cardioType = CardioType.BIKE_STATIONARY)))
        assertEquals("Correr al aire libre", value(SetupStepId.CARDIO_TYPE, SetupWizardDraft().choose(SetupStepId.CARDIO_TYPE, "RUN_OUTDOOR")))
    }

    // ─── Numéricos ───────────────────────────────────────────────────────────

    @Test
    fun numericStepsShowTheNumberWithTheUnitOfTheCatalog() {
        assertEquals(
            SetupStepSummary("Tiempo por sesión", "60 min"),
            summary(SetupStepId.SESSION_TIME, SetupWizardDraft().withStepNumber(SetupStepId.SESSION_TIME, 60.0, NOW)),
        )
        assertEquals("Sin responder", value(SetupStepId.SESSION_TIME))
        // Un texto que no parsea no es un dato.
        assertEquals("Sin responder", value(SetupStepId.SESSION_TIME, SetupWizardDraft().withStepText(SetupStepId.SESSION_TIME, "abc", NOW)))
    }

    @Test
    fun countedChoicesAgreeInNumber() {
        assertEquals("1 día", value(SetupStepId.DAYS, SetupWizardDraft().choose(SetupStepId.DAYS, "1")))
        assertEquals("3 días", value(SetupStepId.DAYS, SetupWizardDraft().choose(SetupStepId.DAYS, "3")))
        // Un número fuera del catálogo también concuerda.
        assertEquals("1 sesión", value(SetupStepId.RINGS_SESSIONS, SetupWizardDraft().choose(SetupStepId.RINGS_SESSIONS, "1")))
        assertEquals("3 sesiones", value(SetupStepId.RINGS_SESSIONS, SetupWizardDraft().choose(SetupStepId.RINGS_SESSIONS, "3")))
        assertEquals("Hoy", value(SetupStepId.RINGS_RECENCY, SetupWizardDraft().choose(SetupStepId.RINGS_RECENCY, "0")))
        assertEquals("Ayer", value(SetupStepId.RINGS_RECENCY, SetupWizardDraft().choose(SetupStepId.RINGS_RECENCY, "1")))
        assertEquals("Hace 4 días", value(SetupStepId.RINGS_RECENCY, SetupWizardDraft().choose(SetupStepId.RINGS_RECENCY, "4")))
        assertEquals("Hace 9 días", value(SetupStepId.RINGS_RECENCY, SetupWizardDraft(ringsAnswers = SetupRingsAnswers(lastSessionRecencyDays = 9))))
        assertEquals("20 min", value(SetupStepId.CARDIO_TIME, SetupWizardDraft().choose(SetupStepId.CARDIO_TIME, "20")))
        assertEquals("45 min", value(SetupStepId.CARDIO_TIME, SetupWizardDraft(cardioMinutes = 45)))
    }

    // ─── Hitos ───────────────────────────────────────────────────────────────

    @Test
    fun milestonesUseTheBlockTitleAndCompletado() {
        val milestones = mapOf(
            SetupStepId.MILESTONE_BASICS to SetupWizardBlock.BASICS,
            SetupStepId.MILESTONE_TRAINING to SetupWizardBlock.TRAINING,
            SetupStepId.MILESTONE_NUTRITION to SetupWizardBlock.NUTRITION,
            SetupStepId.MILESTONE_RINGS to SetupWizardBlock.RINGS,
        )
        milestones.forEach { (step, block) ->
            assertTrue(SetupStepGraph.isMilestone(step))
            assertEquals(SetupStepSummary(SetupStepDefinitions.blockTitle(block), "Completado"), summary(step))
        }
        assertEquals(SetupStepSummary("Entreno", "Completado"), summary(SetupStepId.MILESTONE_TRAINING))
        assertEquals(SetupStepSummary("Datos básicos", "Completado"), summary(SetupStepId.MILESTONE_BASICS))
    }

    // ─── Omitido, No lo sé y Sin responder ───────────────────────────────────

    @Test
    fun skippedStepsReadOmitidoAndUnansweredOnesReadSinResponder() {
        val skippable = SetupStepId.entries.filter { SetupStepDefinitions.of(it)?.allowSkip == true && !isLegacy(it) }
        assertTrue(skippable.containsAll(listOf(SetupStepId.NUTRITION_TARGET, SetupStepId.NUTRITION_DIRECTION, SetupStepId.NUTRITION_WEIGH_INS, SetupStepId.RINGS_DISCOMFORT)))

        assertEquals("Sin responder", value(SetupStepId.NUTRITION_TARGET))
        assertEquals("Omitido", value(SetupStepId.NUTRITION_TARGET, SetupWizardDraft().skipped(SetupStepId.NUTRITION_TARGET)))
        assertEquals("Omitido", value(SetupStepId.NUTRITION_DIRECTION, SetupWizardDraft().skipped(SetupStepId.NUTRITION_DIRECTION)))
        assertEquals("Omitido", value(SetupStepId.NUTRITION_WEIGH_INS, SetupWizardDraft().skipped(SetupStepId.NUTRITION_WEIGH_INS)))
        assertEquals("Omitido", value(SetupStepId.NUTRITION_HISTORY_CONTEXT, SetupWizardDraft().skipped(SetupStepId.NUTRITION_HISTORY_CONTEXT)))
        assertEquals("Omitido", value(SetupStepId.RINGS_DISCOMFORT, SetupWizardDraft().skipped(SetupStepId.RINGS_DISCOMFORT)))
        // Un paso con dato nunca se lee como omitido aunque tenga registro.
        val withData = SetupWizardDraft().choose(SetupStepId.NUTRITION_DIRECTION, "deficit").answered(SetupStepId.NUTRITION_DIRECTION)
        assertEquals("Definir", value(SetupStepId.NUTRITION_DIRECTION, withData))
    }

    @Test
    fun ringsFeelingsTellNoLoSeApartFromOmitido() {
        val levelLabels = mapOf(
            SetupStepId.RINGS_MUSCLE_FEELING to ("3" to "Moderadamente cargados"),
            SetupStepId.RINGS_ENERGY_FEELING to ("5" to "Agotado"),
            SetupStepId.RINGS_STRUCTURE_FEELING to ("1" to "Descansada"),
        )
        levelLabels.forEach { (step, pair) ->
            val (stable, label) = pair
            assertEquals(label, value(step, SetupWizardDraft().choose(step, stable)))
            // «No lo sé» es una respuesta; omitir la sensación es otra.
            assertEquals("No lo sé", value(step, SetupWizardDraft().choose(step, "unknown")))
            assertEquals("Omitido", value(step, SetupWizardDraft().skipped(step)))
            assertEquals("Sin responder", value(step))
        }
        assertEquals("Sensación muscular", summary(SetupStepId.RINGS_MUSCLE_FEELING).label)
        assertEquals("Energía", summary(SetupStepId.RINGS_ENERGY_FEELING).label)
        assertEquals("Columna", summary(SetupStepId.RINGS_STRUCTURE_FEELING).label)
    }

    @Test
    fun discomfortsReadOmitidoWhenThePersonPrefersToSkipThem() {
        assertEquals("Omitido", value(SetupStepId.RINGS_DISCOMFORT, SetupWizardDraft().chooseAll(SetupStepId.RINGS_DISCOMFORT, "omit")))
        assertEquals("Sin molestias", value(SetupStepId.RINGS_DISCOMFORT, SetupWizardDraft().chooseAll(SetupStepId.RINGS_DISCOMFORT, "none")))
        val ids = SetupStepDefinitions.options(SetupStepId.RINGS_DISCOMFORT).map { it.value }.filter { it != "none" && it != "omit" }.take(3)
        val labels = SetupStepDefinitions.options(SetupStepId.RINGS_DISCOMFORT).filter { it.value in ids }.map { it.label }
        assertEquals("${labels[0]}, ${labels[1]} +1", value(SetupStepId.RINGS_DISCOMFORT, SetupWizardDraft().withStepChoices(SetupStepId.RINGS_DISCOMFORT, ids.toSet(), NOW)))
    }

    @Test
    fun nutritionDefaultsAreNotAnAnswerUntilThePersonTouchedOrConfirmedTheStep() {
        // El borrador nutricional nace con valores por defecto (automático, ritmo medio, inactivo, reparto variable).
        val withDefaults = SetupWizardDraft(nutritionDraft = NutritionWizardDraft())
        listOf(
            SetupStepId.NUTRITION_START,
            SetupStepId.NUTRITION_RHYTHM,
            SetupStepId.NUTRITION_ACTIVITY,
            SetupStepId.NUTRITION_DISTRIBUTION,
        ).forEach { step -> assertEquals("$step", "Sin responder", value(step, withDefaults)) }

        // Confirmados, el campo tipado de un borrador rehidratado sí cuenta.
        assertEquals("Calcula mis referencias", value(SetupStepId.NUTRITION_START, withDefaults.answered(SetupStepId.NUTRITION_START)))
        assertEquals("Medio", value(SetupStepId.NUTRITION_RHYTHM, withDefaults.answered(SetupStepId.NUTRITION_RHYTHM)))
        assertEquals("Tranquilo", value(SetupStepId.NUTRITION_ACTIVITY, withDefaults.answered(SetupStepId.NUTRITION_ACTIVITY)))
        assertEquals("Variable según tu calendario", value(SetupStepId.NUTRITION_DISTRIBUTION, withDefaults.answered(SetupStepId.NUTRITION_DISTRIBUTION)))
    }

    @Test
    fun nutritionChoicesTheUserMadeShowTheirLabels() {
        assertEquals("Solo registrar comidas", value(SetupStepId.NUTRITION_START, SetupWizardDraft().choose(SetupStepId.NUTRITION_START, "tracking_only")))
        assertEquals("Yo traigo mis números", value(SetupStepId.NUTRITION_START, SetupWizardDraft().choose(SetupStepId.NUTRITION_START, "self_defined")))
        assertEquals("Rápido", value(SetupStepId.NUTRITION_RHYTHM, SetupWizardDraft().choose(SetupStepId.NUTRITION_RHYTHM, "fast")))
        assertEquals("Activo", value(SetupStepId.NUTRITION_ACTIVITY, SetupWizardDraft().choose(SetupStepId.NUTRITION_ACTIVITY, "ACTIVE")))
        assertEquals("Uniforme todos los días", value(SetupStepId.NUTRITION_DISTRIBUTION, SetupWizardDraft().choose(SetupStepId.NUTRITION_DISTRIBUTION, "uniform")))
        assertEquals("Pauta indicada por un profesional", value(SetupStepId.NUTRITION_START, SetupWizardDraft(nutritionDraft = NutritionWizardDraft(mode = "professional"))))
    }

    // ─── Entreno ─────────────────────────────────────────────────────────────

    @Test
    fun prioritiesListTheMusclesWithTheirPlainNames() {
        val draft = SetupWizardDraft()
            .withMuscleToggled(MuscleSymbol.HAMSTRINGS)
            .withMuscleToggled(MuscleSymbol.GLUTES)
            .withMuscleToggled(MuscleSymbol.BACK)
        // Con un punto cada uno, por orden alfabético del músculo canónico del motor.
        assertEquals(SetupStepSummary("Músculos a mejorar", "Espalda, Glúteos +1"), summary(SetupStepId.PRIORITIES, draft))
        assertEquals("Bíceps", value(SetupStepId.PRIORITIES, SetupWizardDraft().withMuscleToggled(MuscleSymbol.BICEPS)))
        // Un punto a cero no cuenta y un borrador antiguo con dos puntos sigue leyéndose.
        assertEquals("Bíceps", value(SetupStepId.PRIORITIES, SetupWizardDraft(trainingOptions = TrainingOptions(orderPriorities = mapOf("Bíceps" to 2, "Dorsales" to 0)))))
        assertEquals(
            "Glúteos, Isquios +1",
            value(
                SetupStepId.PRIORITIES,
                SetupWizardDraft(trainingOptions = TrainingOptions(orderPriorities = mapOf("Dorsales" to 1, "Isquiosurales" to 2, "Glúteos" to 2))),
            ),
        )
    }

    @Test
    fun anEmptyPrioritiesBagIsSinPreferenciaOnlyOnceTheStepIsConfirmed() {
        assertEquals("Sin responder", value(SetupStepId.PRIORITIES))
        assertEquals("Sin preferencia", value(SetupStepId.PRIORITIES, SetupWizardDraft().answered(SetupStepId.PRIORITIES)))
    }

    @Test
    fun thePlanReadsWithItsEditorialNameNeverItsId() {
        val entry = PersonalizedPlanCatalog.listedEntries().first { it.displayName.length <= SETUP_SUMMARY_VALUE_MAX }
        assertEquals(
            SetupStepSummary("Programa", entry.displayName),
            summary(SetupStepId.PLAN, SetupWizardDraft(selectedCatalogId = entry.id)),
        )

        // Un id que el catálogo ya no conoce: el programa preparado o, sin él, una frase llana; nunca el id.
        val gone = SetupWizardDraft(selectedCatalogId = "plan-que-ya-no-existe")
        assertEquals("Tu programa elegido", value(SetupStepId.PLAN, gone))
        assertEquals(
            "Mi programa",
            summary(SetupStepId.PLAN, SetupWizardState(draft = gone, programPreview = Program(id = "p", name = "Mi programa"))).value,
        )

        assertEquals(DEFER_PROGRAM_REVIEW_VALUE, value(SetupStepId.PLAN, SetupWizardDraft(programRoute = SetupProgramRoute.LATER)))
        assertEquals("Crea desde cero", value(SetupStepId.PLAN, SetupWizardDraft(trainingPath = SetupTrainingPath.FROM_SCRATCH)))
        assertEquals("Sin responder", value(SetupStepId.PLAN))
    }

    @Test
    fun marksShowOnlyTheDeclaredLifts() {
        val three = SetupWizardDraft()
            .withLiftMark(LiftMark.SQUAT, 100.0)
            .withLiftMark(LiftMark.BENCH, 82.5)
            .withLiftMark(LiftMark.DEADLIFT, 120.0)
        assertEquals(SetupStepSummary("Marcas", "Sentadilla 100 kg, Press banca 82,5 kg +1"), summary(SetupStepId.TRAINING_MAX, three))
        assertEquals("Press banca 80 kg", value(SetupStepId.TRAINING_MAX, SetupWizardDraft().withLiftMark(LiftMark.BENCH, 80.0)))
        // Con otra unidad de visualización se dice en ella (el dato siempre vive en kg).
        assertEquals("Press banca 176,4 lb", value(SetupStepId.TRAINING_MAX, SetupWizardDraft().withLiftMark(LiftMark.BENCH, 80.0).withMarksUnit("lb")))
        assertEquals("Sin responder", value(SetupStepId.TRAINING_MAX))
        // «No la sé» a todo, ya confirmado: sin marcas, y así se dice.
        assertEquals("Sin marcas", value(SetupStepId.TRAINING_MAX, SetupWizardDraft().answered(SetupStepId.TRAINING_MAX)))
    }

    @Test
    fun theWeekLayoutSummarizesTheFirstWeekOfThePreparedProgram() {
        fun session(id: String, exercises: Int) =
            Session(id = id, name = id, exercises = (1..exercises).map { Exercise(id = "$id-$it", name = "Ejercicio $it") })

        val program = Program(
            id = "p",
            name = "Plan",
            macrocycles = listOf(
                Macrocycle(
                    id = "m", name = "M",
                    blocks = listOf(
                        Block(
                            id = "b", name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "me", name = "Me",
                                    weeks = listOf(
                                        ProgramWeek(id = "w1", name = "S1", sessions = listOf(session("s1", 2), session("s2", 1))),
                                        // Solo cuenta la primera semana.
                                        ProgramWeek(id = "w2", name = "S2", sessions = listOf(session("s3", 5))),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val draft = SetupWizardDraft()
        assertEquals(
            SetupStepSummary("Tu semana", "2 sesiones por semana"),
            summary(SetupStepId.WEEK_LAYOUT, SetupWizardState(draft = draft, programPreview = program)),
        )
        // Sin programa preparado, o sin sesiones, no se inventa ninguna cifra.
        assertEquals("Listo", value(SetupStepId.WEEK_LAYOUT))
        assertEquals("Listo", summary(SetupStepId.WEEK_LAYOUT, SetupWizardState(draft = draft, programPreview = Program(id = "p", name = "Plan"))).value)
    }

    @Test
    fun retiredTrainingStepsOnlyReadWhatAnOldDraftStoredAndNeverThrow() {
        // Sus filas ya no se pintan en el alta, pero un borrador antiguo con selecciones guardadas se lee sin romper.
        assertEquals("Sin responder", value(SetupStepId.SPLIT))
        assertEquals("Sin responder", value(SetupStepId.AUTOREGULATION))
        assertEquals("Sin responder", value(SetupStepId.WARMUPS))
        assertEquals("Sin responder", value(SetupStepId.TRAINING_REVIEW))
        assertEquals("Sin responder", value(SetupStepId.TRAINING_MARKS))
        assertEquals("3 días", value(SetupStepId.DAYS, SetupWizardDraft().choose(SetupStepId.DAYS, "3")))
        SetupStepId.entries.filter { isLegacy(it) }.forEach { step ->
            assertWithinLimits(step, summary(step, SetupWizardDraft().choose(step, "valor_desconocido")), "borrador antiguo")
        }
    }

    // ─── Nutrición ───────────────────────────────────────────────────────────

    @Test
    fun theTargetWeightCarriesTheUnitTheEngineReadsItIn() {
        assertEquals(
            SetupStepSummary("Peso objetivo", "72,5 kg"),
            summary(SetupStepId.NUTRITION_TARGET, SetupWizardDraft(nutritionDraft = NutritionWizardDraft(targetWeightText = "72,5"))),
        )
        assertEquals("160 lb", value(SetupStepId.NUTRITION_TARGET, SetupWizardDraft(nutritionDraft = NutritionWizardDraft(targetWeightText = "160", weightUnit = "lb"))))
        // El texto crudo del paso también vale, con la unidad del borrador.
        assertEquals("70 kg", value(SetupStepId.NUTRITION_TARGET, SetupWizardDraft(inputTexts = mapOf("NUTRITION_TARGET" to "70"))))
        assertEquals("154,5 lb", value(SetupStepId.NUTRITION_TARGET, SetupWizardDraft(weightUnit = "lb", inputTexts = mapOf("NUTRITION_TARGET" to "154.5"))))
        assertEquals("Sin responder", value(SetupStepId.NUTRITION_TARGET, SetupWizardDraft(inputTexts = mapOf("NUTRITION_TARGET" to "abc"))))
    }

    @Test
    fun theWeightHistoryJoinsTheDeclaredTrendAndMaximum() {
        assertEquals(
            SetupStepSummary("Evolución del peso", "Estable · máximo 82 kg"),
            summary(SetupStepId.NUTRITION_HISTORY_CONTEXT, SetupWizardDraft(weightTrend = "stable", previousMaximumWeightKg = 82.0)),
        )
        assertEquals("Subiendo", value(SetupStepId.NUTRITION_HISTORY_CONTEXT, SetupWizardDraft(weightTrend = "rising")))
        assertEquals("máximo 90,5 kg", value(SetupStepId.NUTRITION_HISTORY_CONTEXT, SetupWizardDraft(previousMaximumWeightKg = 90.5)))
        assertEquals("Sin responder", value(SetupStepId.NUTRITION_HISTORY_CONTEXT))
    }

    @Test
    fun manualMacrosShowOnlyWhatThePersonWrote() {
        val both = SetupWizardDraft(nutritionDraft = NutritionWizardDraft(manualCalorieTargetText = "2400", manualProteinText = "160"))
        assertEquals(SetupStepSummary("Calorías y proteína", "2400 kcal · Proteína 160 g"), summary(SetupStepId.NUTRITION_MANUAL_CALORIES, both))
        assertEquals("2400 kcal", value(SetupStepId.NUTRITION_MANUAL_CALORIES, SetupWizardDraft(nutritionDraft = NutritionWizardDraft(manualCalorieTargetText = "2400"))))

        val carbsFat = SetupWizardDraft(nutritionDraft = NutritionWizardDraft(manualCarbsText = "250", manualFatText = "70,5"))
        assertEquals(SetupStepSummary("Hidratos y grasas", "Hidratos 250 g · Grasas 70,5 g"), summary(SetupStepId.NUTRITION_MANUAL_CARBS_FAT, carbsFat))

        // «Dejar un campo vacío es válido»: no se muestra ninguna cifra inventada.
        assertEquals("Sin responder", value(SetupStepId.NUTRITION_MANUAL_CALORIES))
        assertEquals("Sin responder", value(SetupStepId.NUTRITION_MANUAL_CARBS_FAT, SetupWizardDraft(nutritionDraft = NutritionWizardDraft(manualCarbsText = "abc"))))
    }

    @Test
    fun weighInsAreCountedWithoutInventingDates() {
        val rows = listOf(SetupWeighIn("a", "2026-09-01", 71.0), SetupWeighIn("b", "2026-09-08", 70.5))
        assertEquals(SetupStepSummary("Pesajes", "2 pesajes"), summary(SetupStepId.NUTRITION_WEIGH_INS, SetupWizardDraft(historicalWeighIns = rows)))
        assertEquals("1 pesaje", value(SetupStepId.NUTRITION_WEIGH_INS, SetupWizardDraft(historicalWeighIns = rows.take(1))))
    }

    @Test
    fun theNutritionResultSummarizesWhatTheStateAlreadyCalculated() {
        assertEquals(SetupStepSummary("Plan de alimentación", "Listo"), summary(SetupStepId.NUTRITION_RESULT))

        val draft = SetupWizardDraft()
        // Un plan sin desglose de macros dice solo las kcal (con punto de millar).
        val withPlan = SetupWizardState(draft = draft, nutritionPlanPreview = NutritionPlan(calorieTarget = 2450))
        assertEquals("2.450 kcal", summary(SetupStepId.NUTRITION_RESULT, withPlan).value)

        // La preparación manda sobre la vista previa antigua y dice kcal y macros en una línea corta.
        val prepared = withPlan.copy(
            nutritionPreparation = SetupNutritionPreparationResult(
                plan = NutritionPlan(calorieTarget = 2300, proteinGoal = 160, carbGoal = 250, fatGoal = 70),
                status = NutritionPlanPreparationStatus.READY,
            ),
        )
        assertEquals(
            SetupStepSummary("Plan de alimentación", "2.300 kcal · P 160 · H 250 · G 70"),
            summary(SetupStepId.NUTRITION_RESULT, prepared),
        )
        assertEquals(
            "950 kcal · P 80 · H 40 · G 30",
            summary(
                SetupStepId.NUTRITION_RESULT,
                prepared.copy(
                    nutritionPreparation = SetupNutritionPreparationResult(
                        plan = NutritionPlan(calorieTarget = 950, proteinGoal = 80, carbGoal = 40, fatGoal = 30),
                        status = NutritionPlanPreparationStatus.READY,
                    ),
                ),
            ).value,
        )

        val blocked = SetupWizardState(
            draft = draft,
            nutritionPreparation = SetupNutritionPreparationResult(status = NutritionPlanPreparationStatus.BLOCKED_EQUATION),
        )
        assertEquals("Faltan datos de la ecuación", summary(SetupStepId.NUTRITION_RESULT, blocked).value)

        assertEquals("Solo registrar comidas", value(SetupStepId.NUTRITION_RESULT, draft.choose(SetupStepId.NUTRITION_START, "tracking_only")))
        assertEquals("Pauta indicada por un profesional", value(SetupStepId.NUTRITION_RESULT, SetupWizardDraft(nutritionDraft = NutritionWizardDraft(mode = "professional"))))
    }

    @Test
    fun theNutritionResultLineFitsTheRowEvenWithHugeNumbers() {
        val huge = SetupWizardState(
            draft = SetupWizardDraft(),
            nutritionPreparation = SetupNutritionPreparationResult(
                plan = NutritionPlan(calorieTarget = 12345, proteinGoal = 999, carbGoal = 999, fatGoal = 999),
                status = NutritionPlanPreparationStatus.READY,
            ),
        )
        val line = summary(SetupStepId.NUTRITION_RESULT, huge)
        assertEquals("12.345 kcal · P 999 · H 999 · G 999", line.value)
        assertTrue(line.value.length <= SETUP_SUMMARY_VALUE_MAX)
    }

    // ─── RINGS y revisión ────────────────────────────────────────────────────

    @Test
    fun theRingsResultNamesOnlyTheChannelsThatHaveData() {
        fun channel(id: RecoveryChannelId, score: Int?) = RingsChannelCoverage(
            channel = id,
            source = if (score == null) RingsCoverageSource.NO_DATA else RingsCoverageSource.SUBJECTIVE_SENSATION,
            uncertainty = if (score == null) 100 else 40,
            score = score,
        )
        val draft = SetupWizardDraft()

        // Sin vista previa todavía: nada que afirmar.
        assertEquals(SetupStepSummary("Tus RINGS", "Listo"), summary(SetupStepId.RINGS_RESULT))
        // Vista previa sin ningún canal con dato: «Sin calibrar», nunca un porcentaje.
        assertEquals("Sin calibrar", summary(SetupStepId.RINGS_RESULT, SetupWizardState(draft = draft, ringsCoveragePreview = RingsCoverage.NO_DATA)).value)

        val partial = RingsCoverage(
            muscular = channel(RecoveryChannelId.MUSCULAR, 82),
            system = channel(RecoveryChannelId.SYSTEM, 75),
            structure = channel(RecoveryChannelId.STRUCTURE, null),
        )
        assertEquals("Músculos 82 · Energía 75", summary(SetupStepId.RINGS_RESULT, SetupWizardState(draft = draft, ringsCoveragePreview = partial)).value)

        val full = RingsCoverage(
            muscular = channel(RecoveryChannelId.MUSCULAR, 100),
            system = channel(RecoveryChannelId.SYSTEM, 100),
            structure = channel(RecoveryChannelId.STRUCTURE, 100),
        )
        assertEquals("Músculos 100 · Energía 100 · Columna 100", summary(SetupStepId.RINGS_RESULT, SetupWizardState(draft = draft, ringsCoveragePreview = full)).value)
    }

    @Test
    fun theReviewIsListoUntilTheProgramIsActivated() {
        assertEquals(SetupStepSummary("Revisión y activación", "Listo"), summary(SetupStepId.REVIEW_ACTIVATE))
        assertEquals("Activado", summary(SetupStepId.REVIEW_ACTIVATE, SetupWizardState(draft = SetupWizardDraft(), receiptId = "commit-1")).value)
    }

    // ─── Legacy ──────────────────────────────────────────────────────────────

    @Test
    fun legacyStepsStillReadTheirOldAnswersWithoutBreaking() {
        assertEquals(SetupStepSummary("Identidad de género", "Otro"), summary(SetupStepId.GENDER, SetupWizardDraft().choose(SetupStepId.GENDER, "other")))
        assertEquals("Femenino", value(SetupStepId.NUTRITION_SEX, SetupWizardDraft().choose(SetupStepId.NUTRITION_SEX, "female")))
        assertEquals("Bandas, Mancuernas", value(SetupStepId.HOME_EQUIPMENT, SetupWizardDraft().chooseAll(SetupStepId.HOME_EQUIPMENT, "dumbbells", "bands")))
        assertEquals("Sin responder", value(SetupStepId.RINGS_START))
        assertEquals("conservar", value(SetupStepId.RINGS_START, SetupWizardDraft(ringsAnswers = SetupRingsAnswers(startAction = "conservar"))))
        assertEquals("Sin responder", value(SetupStepId.INVENTORY_BARBELL))
    }

    // ─── Borrador completo ───────────────────────────────────────────────────

    /**
     * Borrador y estado con TODO respondido de la forma más larga posible (la etiqueta más larga de cada
     * paso de opciones, tres elecciones en los múltiples, marcas de tres cifras, programa preparado, plan y
     * RINGS con tres cifras): ningún resumen puede salirse de los topes.
     */
    private fun richState(): SetupWizardState {
        var draft = SetupWizardDraft(name = "N".repeat(32), heightUnit = "ft", weightUnit = "lb")
            .withStepNumber(SetupStepId.AGE, 36.0, NOW)
            .withStepNumber(SetupStepId.HEIGHT, 172.0, NOW)
            .withStepNumber(SetupStepId.WEIGHT, 70.1, NOW)
            .withStepNumber(SetupStepId.SESSION_TIME, 180.0, NOW)
            .withPlaces(TrainingPlace.entries.toSet())
            .withGoalProfile(TrainingGoalProfile.STRENGTH_MUSCLE)
            .withWeekdays(setOf(1, 2, 3, 4, 5, 6))
            .withFreshestDay(3)
            .withCapability(CapabilitySkill.PISTOL_SQUAT, CapabilityLevel.MANY)
            .withCapability(CapabilitySkill.PULL_UP, CapabilityLevel.SOME)
            .withCapability(CapabilitySkill.PUSH_UP, CapabilityLevel.SOME)
            .withLiftMark(LiftMark.SQUAT, 1000.0)
            .withLiftMark(LiftMark.BENCH, 999.5)
            .withLiftMark(LiftMark.DEADLIFT, 1000.0)
            .choose(SetupStepId.BODY_FAT, SetupBodyFatSource.MEASURED.name)
            .withStepText(SetupStepId.BODY_FAT, "17,5", NOW)

        SetupStepDefinitions.definitions.values.forEach { definition ->
            val options = definition.options
            when (definition.control) {
                SetupControlKind.SINGLE_CHOICE,
                SetupControlKind.ROUTE_CHOICE,
                SetupControlKind.TOGGLE,
                -> options.maxByOrNull { it.label.length }?.let { longest ->
                    draft = draft.withStepChoice(definition.id, longest.value, NOW)
                }
                SetupControlKind.MULTI_CHOICE -> {
                    val longest = options.filter { it.value !in definition.exclusiveValues }
                        .sortedByDescending { it.label.length }
                        .take(3)
                        .map { it.value }
                        .toSet()
                    if (longest.isNotEmpty()) draft = draft.withStepChoices(definition.id, longest, NOW)
                }
                else -> Unit
            }
        }

        val longestPlan = PersonalizedPlanCatalog.listedEntries().maxByOrNull { it.displayName.length }
        draft = draft.copy(
            selectedCatalogId = longestPlan?.id,
            trainingOptions = draft.trainingOptions.copy(
                orderPriorities = mapOf("Erectores Espinales" to 1, "Isquiosurales" to 1, "Cuádriceps" to 1),
            ),
            weightTrend = "falling",
            previousMaximumWeightKg = 500.0,
            historicalWeighIns = (1..12).map { SetupWeighIn("w$it", "2026-09-%02d".format(it), 70.0 + it) },
            ringsAnswers = SetupRingsAnswers(muscleFeeling = 5, energy = 5, structureFeeling = 5),
            nutritionDraft = (draft.nutritionDraft ?: NutritionWizardDraft()).copy(
                targetWeightText = "499,9",
                manualCalorieTargetText = "99999",
                manualProteinText = "9999",
                manualCarbsText = "9999",
                manualFatText = "9999",
            ),
        )

        fun channel(id: RecoveryChannelId) =
            RingsChannelCoverage(id, RingsCoverageSource.REAL_HISTORY, uncertainty = 10, score = 100)
        return SetupWizardState(
            draft = draft,
            programPreview = Program(id = "p", name = "N".repeat(80)),
            nutritionPlanPreview = NutritionPlan(calorieTarget = 99999),
            ringsCoveragePreview = RingsCoverage(
                muscular = channel(RecoveryChannelId.MUSCULAR),
                system = channel(RecoveryChannelId.SYSTEM),
                structure = channel(RecoveryChannelId.STRUCTURE),
            ),
            receiptId = "commit-1",
        )
    }
}
