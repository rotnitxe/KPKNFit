package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionDistributionStatus
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.domain.nutrition.NutritionWizardDraft
import com.example.kpkn.domain.nutrition.PlanValues
import com.example.kpkn.domain.nutrition.PlanWarning
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.nutrition.automaticPlanValues
import com.example.kpkn.domain.nutrition.recommendedPlanValues
import com.example.kpkn.domain.onboarding.SetupNutritionPreparation
import com.example.kpkn.domain.onboarding.SetupNutritionPreparationInput
import com.example.kpkn.domain.onboarding.SetupNutritionPreparationResult
import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * El resultado de nutrición con la preparación REAL ([SetupNutritionPreparation]): lo que el panel escribe en
 * el borrador es exactamente lo que el plan preparado (y, por tanto, el que se activa) lleva. Mover un macro,
 * mover el ritmo, restablecer, sembrar el ritmo del paso anterior y los modos objetivos propios, solo registro
 * y ecuación bloqueada. JVM puro: sin ViewModel ni Compose.
 */
class SetupNutritionResultTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 21)

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private fun automaticDraft(
        direction: PlanDirection? = PlanDirection.DEFICIT,
        preset: WizardPacePreset = WizardPacePreset.MEDIUM,
        weekly: NutritionWeeklyDistributionMode = NutritionWeeklyDistributionMode.UNIFORM,
        sex: EerSex? = EerSex.MALE,
    ) = NutritionWizardDraft(
        direction = direction,
        ageText = "30",
        heightText = "178",
        weightText = "80",
        equationSex = sex,
        activity = EerActivity.LOW_ACTIVE,
        pacePreset = preset,
        weeklyDistribution = weekly,
        configurationMode = NutritionConfigurationMode.AUTOMATIC,
    )

    private fun selfDefinedDraft(
        sex: EerSex? = EerSex.MALE,
        kcal: String = "2100",
        protein: String = "165",
        carbs: String = "180",
        fat: String = "65",
    ) = NutritionWizardDraft(
        direction = PlanDirection.DEFICIT,
        ageText = "30",
        heightText = "178",
        weightText = "80",
        equationSex = sex,
        configurationMode = NutritionConfigurationMode.SELF_DEFINED,
        manualCalorieTargetText = kcal,
        manualProteinText = protein,
        manualCarbsText = carbs,
        manualFatText = fat,
    )

    private fun prepare(nutrition: NutritionWizardDraft): SetupNutritionPreparationResult =
        SetupNutritionPreparation.prepare(
            SetupNutritionPreparationInput(
                draft = nutrition,
                program = null,
                settings = Settings(),
                today = monday,
            ),
        )

    private fun stateOf(nutrition: NutritionWizardDraft): SetupWizardState {
        val preparation = prepare(nutrition)
        return SetupWizardState(
            draft = SetupWizardDraft(weightKg = 80.0, heightCm = 178.0, ageYears = 30, nutritionDraft = nutrition),
            nutritionPreparation = preparation,
            nutritionPlanPreview = preparation.plan,
            nutritionErrors = preparation.errors,
        )
    }

    // ─── Escritura del borrador ──────────────────────────────────────────────

    @Test
    fun `escribir un plan fija los cuatro campos y vaciar los deja en blanco`() {
        val written = NutritionWizardDraft().withPlanValues(PlanValues(2300, 160, 250, 70), clearManual = false)
        assertEquals("2300", written.manualCalorieTargetText)
        assertEquals("160", written.manualProteinText)
        assertEquals("250", written.manualCarbsText)
        assertEquals("70", written.manualFatText)

        val cleared = written.withPlanValues(PlanValues(2300, 160, 250, 70), clearManual = true)
        assertEquals("", cleared.manualCalorieTargetText)
        assertEquals("", cleared.manualProteinText)
        assertEquals("", cleared.manualCarbsText)
        assertEquals("", cleared.manualFatText)
    }

    @Test
    fun `sin ediciones el plan es la recomendacion automatica y no esta editado`() {
        val state = stateOf(automaticDraft())
        val tuning = requireNotNull(nutritionPlanTuningOf(state))
        val plan = requireNotNull(state.nutritionPreparation?.plan)

        assertEquals(PlanValues(plan.calorieTarget, plan.proteinGoal, plan.carbGoal, plan.fatGoal), tuning.values)
        assertEquals(automaticPlanValues(tuning.context), tuning.values)
        assertFalse(tuning.isEdited)
        assertEquals(CalculationOrigin.PLAN, plan.calculationOrigin)
        // El contexto sale de la foto de entradas del motor: mismo peso, mismo EER, sexo de la ecuación.
        assertEquals(80.0, tuning.context.weightKg!!, 1e-9)
        assertEquals(state.nutritionPreparation?.recommendation?.eerKcal, tuning.context.eerKcal)
        assertEquals(EerSex.MALE, tuning.context.sex)
        assertEquals(PlanDirection.DEFICIT, tuning.context.direction)
    }

    @Test
    fun `mover un macro deja en el plan preparado esos gramos y las kcal Atwater`() {
        val base = automaticDraft()
        val tuning = requireNotNull(nutritionPlanTuningOf(stateOf(base)))
        val moved = tuning.withProtein(tuning.proteinG + 30).withFat(tuning.fatG - 8)
        assertTrue(moved.isEdited)

        val edited = base.withPlanValues(moved.values, clearManual = false)
        val plan = requireNotNull(prepare(edited).plan)

        assertEquals(moved.proteinG, plan.proteinGoal)
        assertEquals(moved.carbsG, plan.carbGoal)
        assertEquals(moved.fatG, plan.fatGoal)
        assertEquals(moved.kcal, plan.calorieTarget)
        // Las kcal son la suma Atwater de los gramos que se ven.
        assertEquals(4 * plan.proteinGoal + 4 * plan.carbGoal + 9 * plan.fatGoal, plan.calorieTarget)
        assertEquals(CalculationOrigin.MANUAL, plan.calculationOrigin)

        // Y al leer el plan de nuevo es exactamente lo mismo: nada se corrige en silencio.
        val reread = requireNotNull(nutritionPlanTuningOf(stateOf(edited)))
        assertEquals(moved.values, reread.values)
    }

    @Test
    fun `mover el ritmo deja en el plan las kcal y los macros de la tuning`() {
        val base = automaticDraft()
        val tuning = requireNotNull(nutritionPlanTuningOf(stateOf(base)))
        val moved = tuning.withPaceRate(0.62)

        val edited = base.withPlanValues(moved.values, clearManual = false)
        val plan = requireNotNull(prepare(edited).plan)

        assertEquals(moved.kcal, plan.calorieTarget)
        assertEquals(moved.proteinG, plan.proteinGoal)
        assertEquals(moved.carbsG, plan.carbGoal)
        assertEquals(moved.fatG, plan.fatGoal)
        // El ritmo que se vería al releerlo es el elegido (kcal reales frente al EER).
        val reread = requireNotNull(nutritionPlanTuningOf(stateOf(edited)))
        assertEquals(-0.62, reread.weeklyChangeKg!!, 0.001)
    }

    @Test
    fun `restablecer vacia los campos y devuelve la recomendacion con su procedencia automatica`() {
        val base = automaticDraft()
        val tuning = requireNotNull(nutritionPlanTuningOf(stateOf(base)))
        val edited = base.withPlanValues(tuning.withCarbs(tuning.carbsG - 60).values, clearManual = false)
        val editedState = stateOf(edited)
        val editedTuning = requireNotNull(nutritionPlanTuningOf(editedState))
        assertTrue(editedTuning.isEdited)

        val reset = editedTuning.reset()
        assertFalse(reset.isEdited)
        // Lo restablecido coincide con lo que el motor propone solo: se vacían los campos manuales.
        val clear = reset.values == automaticPlanValues(reset.context)
        assertTrue(clear)
        val back = edited.withPlanValues(reset.values, clearManual = clear)
        assertEquals("", back.manualCalorieTargetText)
        val plan = requireNotNull(prepare(back).plan)
        assertEquals(tuning.values, PlanValues(plan.calorieTarget, plan.proteinGoal, plan.carbGoal, plan.fatGoal))
        assertEquals(CalculationOrigin.PLAN, plan.calculationOrigin)
    }

    @Test
    fun `volver a mano a los numeros de la recomendacion tambien los deja automaticos`() {
        val base = automaticDraft()
        val tuning = requireNotNull(nutritionPlanTuningOf(stateOf(base)))
        val roundTrip = tuning.withProtein(tuning.proteinG + 10).withProtein(tuning.proteinG)
        assertEquals(tuning.values, roundTrip.values)
        assertEquals(automaticPlanValues(roundTrip.context), roundTrip.values)
    }

    // ─── Preset del paso anterior ────────────────────────────────────────────

    @Test
    fun `el motor no lee el preset y el plan automatico es el del ritmo medio`() {
        val medium = requireNotNull(prepare(automaticDraft(preset = WizardPacePreset.MEDIUM)).plan)
        val fast = requireNotNull(prepare(automaticDraft(preset = WizardPacePreset.FAST)).plan)
        assertEquals(medium.calorieTarget, fast.calorieTarget)
    }

    @Test
    fun `con otro ritmo la base es la del preset y la semilla escribe lo que se ve`() {
        val draft = automaticDraft(preset = WizardPacePreset.FAST)
        val state = stateOf(draft)
        val tuning = requireNotNull(nutritionPlanTuningOf(state))
        // Antes de sembrar: el plan es el automático y la base la del preset → «editado».
        assertTrue(tuning.isEdited)
        assertEquals(recommendedPlanValues(tuning.context, WizardPacePreset.FAST), tuning.baseline)

        val seed = requireNotNull(nutritionPresetSeed(draft, tuning))
        assertEquals(tuning.baseline, seed)

        val seeded = draft.withPlanValues(seed, clearManual = false)
        val seededState = stateOf(seeded)
        val seededTuning = requireNotNull(nutritionPlanTuningOf(seededState))
        assertEquals(seed, seededTuning.values)
        assertFalse("tras sembrar ya no está editado", seededTuning.isEdited)
        assertNull("y no vuelve a sembrar", nutritionPresetSeed(seeded, seededTuning))
        // Lo que se activa es el ritmo elegido: más déficit que el medio.
        assertTrue(seededTuning.kcal < requireNotNull(automaticPlanValues(seededTuning.context)).kcal)
    }

    @Test
    fun `con ritmo medio o con ediciones no se siembra nada`() {
        val medium = automaticDraft(preset = WizardPacePreset.MEDIUM)
        assertNull(nutritionPresetSeed(medium, requireNotNull(nutritionPlanTuningOf(stateOf(medium)))))

        val fastTouched = automaticDraft(preset = WizardPacePreset.FAST)
            .withPlanValues(PlanValues(2100, 160, 230, 60), clearManual = false)
        assertNull(nutritionPresetSeed(fastTouched, requireNotNull(nutritionPlanTuningOf(stateOf(fastTouched)))))

        val selfDefined = selfDefinedDraft().copy(pacePreset = WizardPacePreset.FAST)
        assertNull(nutritionPresetSeed(selfDefined, requireNotNull(nutritionPlanTuningOf(stateOf(selfDefined)))))
    }

    // ─── Objetivos propios ───────────────────────────────────────────────────

    @Test
    fun `en objetivos propios los anillos muestran los valores propios y restablecer vuelve a lo que habia`() {
        val draft = selfDefinedDraft()
        val state = stateOf(draft)
        val arrival = PlanValues(2100, 165, 180, 65)
        val tuning = requireNotNull(nutritionPlanTuningOf(state, arrival))

        assertEquals(arrival, tuning.values)
        assertEquals(arrival, tuning.baseline) // no hay recomendación a la que volver: lo que había al abrir
        assertFalse(tuning.isEdited)
        // Con EER (hay sexo de ecuación) el ritmo sí se puede mostrar y mover.
        assertNotNull(tuning.paceControl)
        assertNotNull(tuning.weeklyChangeKg)

        val moved = tuning.withProtein(190)
        val plan = requireNotNull(prepare(draft.withPlanValues(moved.values, clearManual = false)).plan)
        assertEquals(190, plan.proteinGoal)
        assertEquals(moved.kcal, plan.calorieTarget)
        assertEquals(CalculationOrigin.MANUAL, plan.calculationOrigin)

        assertEquals(arrival, moved.reset().values)
    }

    @Test
    fun `sin EER los objetivos propios no tienen ritmo pero si macros`() {
        val state = stateOf(selfDefinedDraft(sex = null))
        assertEquals(NutritionPlanPreparationStatus.READY, state.nutritionPreparation?.status)
        val tuning = requireNotNull(nutritionPlanTuningOf(state, PlanValues(2100, 165, 180, 65)))
        assertNull(tuning.context.eerKcal)
        assertNull(tuning.paceControl)
        assertNull(tuning.weeklyChangeKg)
        assertEquals(PlanValues(2100, 165, 180, 65), tuning.values)
        val moved = tuning.withCarbs(150)
        assertEquals(4 * 165 + 4 * 150 + 9 * 65, moved.kcal)
    }

    @Test
    fun `en objetivos propios las kcal escritas se conservan hasta que se toca un macro`() {
        // 2100 kcal con macros que suman 1965: el valor propio se muestra tal cual.
        val state = stateOf(selfDefinedDraft())
        val tuning = requireNotNull(nutritionPlanTuningOf(state, PlanValues(2100, 165, 180, 65)))
        assertEquals(2100, tuning.kcal)
        assertEquals(1965, tuning.values.atwaterKcal)
        assertEquals(1965 + 4, tuning.withProtein(166).kcal)
    }

    // ─── Solo registro, ecuación bloqueada, pauta profesional ───────────────

    @Test
    fun `solo registro no tiene plan afinable ni puerta`() {
        val tracking = NutritionWizardDraft(configurationMode = NutritionConfigurationMode.TRACKING_ONLY)
        val state = stateOf(tracking)
        assertEquals(NutritionPlanPreparationStatus.TRACKING_ONLY, state.nutritionPreparation?.status)
        assertNull(nutritionPlanTuningOf(state))
        assertTrue(nutritionResultGate(state, SetupStepId.NUTRITION_RESULT).isEmpty())
    }

    @Test
    fun `con la ecuacion bloqueada no hay plan afinable y los errores siguen ahi`() {
        val state = stateOf(automaticDraft(sex = null))
        assertEquals(NutritionPlanPreparationStatus.BLOCKED_EQUATION, state.nutritionPreparation?.status)
        assertTrue(state.nutritionErrors.isNotEmpty())
        assertNull(nutritionPlanTuningOf(state))
        assertTrue(nutritionResultGate(state, SetupStepId.NUTRITION_RESULT).isEmpty())
    }

    @Test
    fun `una pauta profesional no se afina aqui`() {
        val professional = automaticDraft(direction = PlanDirection.PROFESSIONAL)
        assertNull(nutritionPlanTuningOf(stateOf(professional)))
    }

    @Test
    fun `sin preparacion todavia no hay nada que afinar`() {
        val state = SetupWizardState(draft = SetupWizardDraft(nutritionDraft = automaticDraft()))
        assertNull(nutritionPlanTuningOf(state))
        assertTrue(nutritionResultGate(state, SetupStepId.NUTRITION_RESULT).isEmpty())
    }

    // ─── Puerta de Continuar (hardStop) ──────────────────────────────────────

    @Test
    fun `calorias por debajo del umbral duro cierran Continuar con su mensaje`() {
        val draft = automaticDraft().withPlanValues(PlanValues(1100, 130, 120, 40), clearManual = false)
        val state = stateOf(draft)
        val tuning = requireNotNull(nutritionPlanTuningOf(state))
        assertTrue(tuning.hardStop)
        assertTrue(PlanWarning.LOW_CALORIES_HARD in tuning.warnings)
        assertEquals(
            mapOf("nutritionPlan" to NUTRITION_LOW_CALORIES_MESSAGE),
            nutritionResultGate(state, SetupStepId.NUTRITION_RESULT),
        )
    }

    @Test
    fun `una perdida extrema con calorias normales tambien cierra Continuar`() {
        // 80 kg, EER ≈ 2,6 mil: 1500 kcal es > 1,5 kg/sem para un hombre de este perfil solo si el EER es alto.
        val base = automaticDraft().copy(activity = EerActivity.VERY_ACTIVE)
        val eer = requireNotNull(prepare(base).recommendation?.eerKcal)
        val kcal = (eer - 1.6 * 1100.0).toInt() // −1,6 kg/sem
        assertTrue("kcal de la prueba por encima del umbral duro", kcal >= 1200)
        val extreme = base.withPlanValues(PlanValues(kcal, 150, 140, 45), clearManual = false)
        val state = stateOf(extreme)
        val tuning = requireNotNull(nutritionPlanTuningOf(state))
        assertTrue(PlanWarning.PACE_EXTREME in tuning.warnings)
        assertEquals(
            mapOf("nutritionPlan" to NUTRITION_EXTREME_PACE_MESSAGE),
            nutritionResultGate(state, SetupStepId.NUTRITION_RESULT),
        )
    }

    @Test
    fun `un plan normal o con solo avisos no cierra Continuar y la puerta solo vale en el resultado`() {
        val normal = stateOf(automaticDraft())
        assertTrue(nutritionResultGate(normal, SetupStepId.NUTRITION_RESULT).isEmpty())

        // Proteína y grasas bajas avisan pero no detienen.
        val warned = stateOf(automaticDraft().withPlanValues(PlanValues(2200, 70, 360, 40), clearManual = false))
        val tuning = requireNotNull(nutritionPlanTuningOf(warned))
        assertTrue(tuning.warnings.isNotEmpty())
        assertFalse(tuning.hardStop)
        assertTrue(nutritionResultGate(warned, SetupStepId.NUTRITION_RESULT).isEmpty())

        val extreme = stateOf(automaticDraft().withPlanValues(PlanValues(1000, 90, 40, 53), clearManual = false))
        assertTrue(nutritionResultGate(extreme, SetupStepId.NUTRITION_DISTRIBUTION).isEmpty())
        assertTrue(nutritionResultGate(extreme, SetupStepId.NUTRITION_RESULT).isNotEmpty())
    }

    // ─── Textos del paso ─────────────────────────────────────────────────────

    @Test
    fun `el paso se llama Tu plan de alimentacion y no lleva subtitulo`() {
        val route = SetupStepGraph.stepIds(SetupStepContext(nutritionStartChoice = "automatic", nutritionDirection = "deficit"))
        assertTrue(SetupStepId.NUTRITION_RESULT in route)
        val copy = wizardPageCopy(SetupStepId.NUTRITION_RESULT, route)
        assertEquals("Tu plan de alimentación", copy.title)
        assertNull(copy.subtitle)
        assertTrue(copy.title.length <= 40)
    }

    // ─── Franja de días ──────────────────────────────────────────────────────

    private fun day(offset: Long, kcal: Int) = NutritionEditorDayTarget(monday.plusDays(offset), kcal, 150, kcal / 9, 60)

    private fun preparationWith(days: List<NutritionEditorDayTarget>) = SetupNutritionPreparationResult(
        plan = null,
        status = NutritionPlanPreparationStatus.READY,
        days = days,
        distributionStatus = NutritionDistributionStatus.VARIABLE,
    )

    @Test
    fun `la franja de dias solo existe con reparto variable y objetivos distintos`() {
        val varied = preparationWith(listOf(2400, 2100, 2400, 2100, 2400, 2000, 2000).mapIndexed { i, kcal -> day(i.toLong(), kcal) })
        val strip = nutritionStripDays(varied, NutritionWeeklyDistributionMode.VARIABLE, monday.plusDays(2))
        assertEquals(7, strip.size)
        assertEquals(listOf(2400, 2100, 2400, 2100, 2400, 2000, 2000), strip.map { it.kcal })
        assertEquals(listOf(false, false, true, false, false, false, false), strip.map { it.isToday })

        assertTrue(nutritionStripDays(varied, NutritionWeeklyDistributionMode.UNIFORM, monday).isEmpty())
        assertTrue(nutritionStripDays(varied, null, monday).isEmpty())

        val flat = preparationWith((0L until 7L).map { day(it, 2200) })
        assertTrue(nutritionStripDays(flat, NutritionWeeklyDistributionMode.VARIABLE, monday).isEmpty())

        val single = preparationWith(listOf(day(0, 2200)))
        assertTrue(nutritionStripDays(single, NutritionWeeklyDistributionMode.VARIABLE, monday).isEmpty())
        assertTrue(nutritionStripDays(preparationWith(emptyList()), NutritionWeeklyDistributionMode.VARIABLE, monday).isEmpty())
    }

    @Test
    fun `la franja se ordena por fecha y toma como mucho siete dias`() {
        val shuffled = (0L until 9L).map { day(it, 2000 + (it.toInt() % 3) * 100) }.reversed()
        val strip = nutritionStripDays(
            preparationWith(shuffled),
            NutritionWeeklyDistributionMode.VARIABLE,
            monday,
        )
        assertEquals(7, strip.size)
        assertEquals((0L until 7L).map { monday.plusDays(it) }, strip.map { it.date })
    }

    @Test
    fun `con la preparacion real y reparto variable la franja sale de los dias del plan`() {
        val draft = automaticDraft(weekly = NutritionWeeklyDistributionMode.VARIABLE)
        val preparation = prepare(draft)
        // Sin programa no hay sesiones: los siete días valen lo mismo y no hay franja que enseñar.
        assertEquals(7, preparation.days.size)
        assertTrue(
            nutritionStripDays(preparation, NutritionWeeklyDistributionMode.VARIABLE, monday).isEmpty(),
        )
    }
}
