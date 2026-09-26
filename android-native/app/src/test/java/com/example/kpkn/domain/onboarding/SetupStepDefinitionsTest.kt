package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStepDefinitionsTest {

    @Test
    fun defaultRouteMetadataHasCleanBlockTitlesForTheFourBlocksPlusReview() {
        assertEquals("Datos básicos", SetupStepDefinitions.blockTitle(SetupWizardBlock.BASICS))
        assertEquals("Entreno", SetupStepDefinitions.blockTitle(SetupWizardBlock.TRAINING))
        assertEquals("Nutrición", SetupStepDefinitions.blockTitle(SetupWizardBlock.NUTRITION))
        assertEquals("Rings", SetupStepDefinitions.blockTitle(SetupWizardBlock.RINGS))
        assertEquals("Revisión", SetupStepDefinitions.blockTitle(SetupWizardBlock.REVIEW))
    }

    @Test
    fun prioritiesIsAFivePointBagWithTwoPointsPerMuscle() {
        val priorities = requireNotNull(SetupStepDefinitions.of(SetupStepId.PRIORITIES))
        assertEquals(SetupControlKind.POINT_BUDGET, priorities.control)
        assertEquals(ORDER_MUSCLE_OPTIONS, priorities.options)
        assertEquals(12, priorities.options.size)
        assertEquals(5, priorities.budget)
        assertEquals(2, priorities.maxPerItem)
        // Only reorders exercises: the engine's canonical muscle names, stable.
        assertEquals(ORDER_MUSCLE_OPTIONS.map { it.value }, priorities.options.map { it.value })
    }

    @Test
    fun stableOptionValuesAreClosedAndMatchTheirControls() {
        // ROUTE only offers recommended/protocol: no later/manual route option.
        assertEquals(setOf("recommended", "protocol"), SetupStepDefinitions.optionValues(SetupStepId.ROUTE))
        // TRACKING_ONLY is an explicit inside the nutrition block, never a skip.
        assertEquals(
            setOf("automatic", "self_defined", "tracking_only"),
            SetupStepDefinitions.optionValues(SetupStepId.NUTRITION_START),
        )
        assertEquals(setOf("on", "off"), SetupStepDefinitions.optionValues(SetupStepId.AUTOREGULATION))
        assertEquals(SetupControlKind.ROUTE_CHOICE, SetupStepDefinitions.control(SetupStepId.ROUTE))
        assertEquals(SetupControlKind.POINT_BUDGET, SetupStepDefinitions.control(SetupStepId.PRIORITIES))
        assertEquals(SetupControlKind.MILESTONE, SetupStepDefinitions.control(SetupStepId.MILESTONE_RINGS))
        assertEquals(SetupControlKind.REVIEW, SetupStepDefinitions.control(SetupStepId.REVIEW_ACTIVATE))
    }

    @Test
    fun bodyFatUsesADedicatedPhysiqueControlWithVisualEstimationAndOmit() {
        val bodyFat = requireNotNull(SetupStepDefinitions.of(SetupStepId.BODY_FAT))
        assertEquals(SetupControlKind.PHYSIQUE, bodyFat.control)
        assertTrue(bodyFat.allowSkip)
        // Grasa ACTUAL: medición o estimación visual explícita; desconocido permite omitir.
        assertEquals("measured", bodyFat.option("measured")?.value)
        assertEquals("visual", bodyFat.option("visual")?.value)
        assertEquals("unknown", bodyFat.option("unknown")?.value)
        assertFalse("no se estima" in bodyFat.subtitle.orEmpty().lowercase())
        assertTrue("estimación visual" in bodyFat.subtitle.orEmpty())
        // El rango numérico cualitativo sigue disponible para el valor declarado.
        assertEquals(3.0, bodyFat.range?.min ?: 0.0, 0.0001)
        assertEquals(60.0, bodyFat.range?.max ?: 0.0, 0.0001)
    }

    @Test
    fun questionStepsCarryNaturalQuestionTitlesExceptResultsAndMilestones() {
        for (definition in SetupStepDefinitions.definitions.values) {
            if (definition.legacyOnly) continue
            val isLabeledScreen = definition.kind != SetupStepKind.QUESTION ||
                definition.control == SetupControlKind.RESULT_PREVIEW
            if (isLabeledScreen) continue
            assertTrue(
                "${definition.id} should be a natural question, got «${definition.title}»",
                definition.title.startsWith("¿") && definition.title.endsWith("?"),
            )
        }
        // Excepciones: bases, resultados y el review siguen siendo etiquetas.
        assertEquals("Datos básicos", SetupStepDefinitions.title(SetupStepId.MILESTONE_BASICS))
        assertEquals("Tus referencias", SetupStepDefinitions.title(SetupStepId.NUTRITION_RESULT))
        assertEquals("Tus RINGS", SetupStepDefinitions.title(SetupStepId.RINGS_RESULT))
        assertEquals("Revisión del plan", SetupStepDefinitions.title(SetupStepId.TRAINING_REVIEW))
        assertEquals("Revisión y activación", SetupStepDefinitions.title(SetupStepId.REVIEW_ACTIVATE))
        // Preguntas grandes de referencia para el bloque de datos básicos.
        assertEquals("¿Cómo te llamas?", SetupStepDefinitions.title(SetupStepId.NAME))
        assertEquals("¿Cuál es tu altura?", SetupStepDefinitions.title(SetupStepId.HEIGHT))
        assertEquals("¿Cuál es tu peso?", SetupStepDefinitions.title(SetupStepId.WEIGHT))
    }

    @Test
    fun inventorySubtitlesGiveUsefulInformationNotUiInstructions() {
        for (group in SetupInventoryGroup.entries) {
            val step = SetupStepDefinitions.stepOf(group)
            val subtitle = SetupStepDefinitions.subtitle(step).orEmpty()
            assertFalse("$step subtitle leaks UI instructions", "Máximo dos entradas" in subtitle)
            assertFalse("$step subtitle leaks UI instructions", "añadir filas" in subtitle)
            assertTrue("$step subtitle should inform about the material", subtitle.isNotBlank())
        }
    }

    @Test
    fun noStepAllowsMoreThanTwoRelatedInputsPerScreen() {
        for (definition in SetupStepDefinitions.definitions.values) {
            assertTrue(
                "${definition.id} maxRelatedInputs=${definition.maxRelatedInputs} exceeds the cap of 2",
                definition.maxRelatedInputs <= 2,
            )
        }
    }

    @Test
    fun nutritionTargetAndHistoryContextAreOptionalAndPlayNoLegacyRole() {
        val target = requireNotNull(SetupStepDefinitions.of(SetupStepId.NUTRITION_TARGET))
        assertEquals(SetupControlKind.NUMBER, target.control)
        assertEquals("kg", target.unit)
        assertTrue(target.allowSkip)
        assertNull(target.legacyQuestion)

        val history = requireNotNull(SetupStepDefinitions.of(SetupStepId.NUTRITION_HISTORY_CONTEXT))
        assertEquals(SetupControlKind.EDITOR_ROWS, history.control)
        assertTrue(history.allowSkip)
        assertNull(history.legacyQuestion)
        // Tendencia + máximo anterior como contexto declarado, no registros.
        assertEquals(setOf("trend", "max_previous"), SetupStepDefinitions.optionValues(SetupStepId.NUTRITION_HISTORY_CONTEXT))
    }

    @Test
    fun exclusiveValuesAreNeverCombinableWithOtherOptions() {
        assertEquals(
            setOf("none", "unknown"),
            SetupStepDefinitions.of(SetupStepId.NUTRITION_ELIGIBILITY)?.exclusiveValues,
        )
        assertEquals(
            setOf("none", "omit"),
            SetupStepDefinitions.of(SetupStepId.RINGS_DISCOMFORT)?.exclusiveValues,
        )
        assertEquals(
            setOf("none"),
            SetupStepDefinitions.of(SetupStepId.HOME_EQUIPMENT)?.exclusiveValues,
        )
    }

    @Test
    fun everyProductiveStepDeclaresATitleAndAnIdentifiableControl() {
        for (definition in SetupStepDefinitions.definitions.values) {
            if (definition.legacyOnly) continue
            assertTrue("${definition.id} needs a title", definition.title.isNotBlank())
            // Every step must be renderable without guessing the control.
            assertTrue("${definition.id} must carry a control", SetupStepDefinitions.control(definition.id).name.isNotBlank())
        }
    }

    @Test
    fun everyMappedLegacyLabelPointsAtARealOptionValue() {
        for (definition in SetupStepDefinitions.definitions.values) {
            if (definition.legacyValueMap.isEmpty()) continue
            val optionValues = SetupStepDefinitions.optionValues(definition.id)
            for (value in definition.legacyValueMap.values) {
                assertTrue(
                    "${definition.id} maps to $value but that option does not exist",
                    value in optionValues,
                )
            }
        }
    }

    @Test
    fun migratedValueIsNullWhenTheLegacyLabelIsNotMapped() {
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.N_SEX, "Prefiero no responder"))
        assertEquals("female", SetupStepDefinitions.migratedValue(WizChatQuestionId.N_SEX, "Femenino"))
        assertEquals("male", SetupStepDefinitions.migratedValue(WizChatQuestionId.N_SEX, "Masculino"))
        // Gender identity is never a calculation sex; no mapping exists.
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.P_GENDER, "Mujer"))
        // The WizChat "decidiré después" route answers stay pending.
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.T_ROUTE, "Lo decidiré después"))
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.T_ROUTE, "Crear desde cero"))
        // Only the explicit "preparar referencias" answer migrates from N_START.
        assertEquals(
            "automatic",
            SetupStepDefinitions.migratedValue(WizChatQuestionId.N_START, "Sí, preparar mis referencias"),
        )
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.N_START, "Lo haré después"))
        // Los días y semanas legacy usan el mismo valor estable: el label ES el id.
        assertEquals("3", SetupStepDefinitions.migratedValue(WizChatQuestionId.T_DAYS, "3"))
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.T_DAYS, "7"))
        assertEquals("1", SetupStepDefinitions.migratedValue(WizChatQuestionId.T_WEEKDAYS, "Lunes"))
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.T_WEEKDAYS, "Finde"))
    }

    @Test
    fun legacyMigrationTargetsCoverEveryRenderableBridgeAndSkipsLegacyOnlySteps() {
        val targets = SetupStepDefinitions.legacyMigrationTargets

        // Productive 1:1 copies are covered.
        assertEquals(SetupStepId.NAME, targets[WizChatQuestionId.P_NAME])
        assertEquals(SetupStepId.AGE, targets[WizChatQuestionId.P_AGE])
        assertEquals(SetupStepId.DAYS, targets[WizChatQuestionId.T_DAYS])
        assertEquals(SetupStepId.PLAN, targets[WizChatQuestionId.T_PLAN])
        assertEquals(SetupStepId.RINGS_RECENT, targets[WizChatQuestionId.R_RECENT])

        // N_SEX goes to the basics block, never to the legacy nutrition step.
        assertEquals(SetupStepId.EQUATION_SEX, targets[WizChatQuestionId.N_SEX])
        assertFalse(SetupStepDefinitions.legacyRenderable.containsKey(SetupStepId.EQUATION_SEX))
        assertFalse(SetupStepDefinitions.legacyRenderable.containsKey(SetupStepId.NUTRITION_SEX))

        // Legacy-only questions are never a migration target.
        assertFalse(WizChatQuestionId.P_GENDER in targets)
        assertFalse(WizChatQuestionId.T_HOME_EQUIPMENT in targets)
        assertFalse(WizChatQuestionId.R_START in targets)

        // Every renderable step maps: legacyQuestion + renders and not legacyOnly.
        // El puente es question -> STEP: comparar question con question ocultaba
        // un desajuste de tipos en vez de fallar sobre el mapa real.
        assertTrue(
            "legacyRenderable must not be empty",
            SetupStepDefinitions.legacyRenderable.isNotEmpty(),
        )
        for ((step, question) in SetupStepDefinitions.legacyRenderable) {
            val definition = requireNotNull(SetupStepDefinitions.of(step))
            assertEquals(step, targets[question])
            assertEquals(question, definition.legacyQuestion)
            assertFalse(definition.legacyOnly)
        }
    }

    @Test
    fun nonRenderableLegacyQuestionsAreMigrationOnlyAndCarryNoCopy() {
        val renderable = SetupStepDefinitions.legacyRenderable
        // Migrated values must never render the old conversational copy.
        assertFalse(SetupStepId.EQUATION_SEX in renderable)
        assertFalse(SetupStepId.NUTRITION_RESULT in renderable)
        assertFalse(SetupStepId.RINGS_RESULT in renderable)
        assertFalse(SetupStepId.REVIEW_ACTIVATE in renderable)
        // New steps without a legacy source are never renderable copies either.
        assertFalse(SetupStepId.PRIORITIES in renderable)
        assertFalse(SetupStepId.NUTRITION_RHYTHM in renderable)
        assertFalse(SetupStepId.NUTRITION_TARGET in renderable)

        // RINGS_DISCOMFORT SÍ es copia 1:1: su pregunta legacy R_DISCOMFORT
        // existe en WizChatGraph y declara exactamente las mismas opciones.
        assertEquals(WizChatQuestionId.R_DISCOMFORT, renderable[SetupStepId.RINGS_DISCOMFORT])
        assertEquals(WizChatQuestionId.R_DISCOMFORT, SetupStepGraph.questionForStep(SetupStepId.RINGS_DISCOMFORT))
        val legacyQuestion = requireNotNull(WizChatGraph.question(WizChatQuestionId.R_DISCOMFORT))
        val copy = requireNotNull(SetupStepDefinitions.of(SetupStepId.RINGS_DISCOMFORT))
        assertEquals(legacyQuestion.options, copy.options.map { it.label })
    }

    @Test
    fun inventoryAndManualMacroStepsLimitRelatedInputsPerScreen() {
        assertEquals(
            2,
            SetupStepDefinitions.of(SetupStepId.INVENTORY_BARBELL)?.maxRelatedInputs,
        )
        assertEquals(
            2,
            SetupStepDefinitions.of(SetupStepId.NUTRITION_MANUAL_CALORIES)?.maxRelatedInputs,
        )
        assertEquals(
            SetupStepId.INVENTORY_BARBELL,
            SetupStepDefinitions.stepOf(SetupInventoryGroup.BARBELL),
        )
        assertEquals(
            SetupStepId.INVENTORY_MACHINES,
            SetupStepDefinitions.stepOf(SetupInventoryGroup.MACHINES),
        )
    }

    @Test
    fun controlKindCatalogIsStableSoModelsCanBindEveryStep() {
        // 17 controles de datos + 2 pantallas de estructura (MILESTONE/REVIEW).
        assertEquals(
            setOf(
                "TEXT", "NUMBER", "PHYSIQUE", "SINGLE_CHOICE", "MULTI_CHOICE", "ROUTE_CHOICE",
                "ENVIRONMENT_CHOICE", "INVENTORY_PICKER", "POINT_BUDGET", "SPLIT_EDITOR",
                "PLAN_PICKER", "MARKS_EDITOR", "TOGGLE", "AUTO_CONFIRM", "MANUAL_MACROS",
                "EDITOR_ROWS", "RESULT_PREVIEW", "MILESTONE", "REVIEW",
            ),
            SetupControlKind.entries.mapTo(mutableSetOf()) { it.name },
        )
        assertEquals(17, SetupControlKind.entries.size - 2)
        // Ningún paso usa un control desconocido: la capa de modelos no adivina.
        for (definition in SetupStepDefinitions.definitions.values) {
            assertEquals(definition.control, SetupStepDefinitions.control(definition.id))
        }
    }

    @Test
    fun everyStepIdIsCoveredByMetadataThatMatchesTheGraph() {
        for (step in SetupStepId.entries) {
            val definition = SetupStepDefinitions.of(step)
            assertNotNull("$step must declare metadata for the models layer", definition)
            assertEquals(step, definition!!.id)
            assertEquals(SetupStepGraph.blockOf(step), definition.block)
            val expectedKind = when {
                SetupStepGraph.isMilestone(step) -> SetupStepKind.MILESTONE
                step == SetupStepId.REVIEW_ACTIVATE -> SetupStepKind.REVIEW
                else -> SetupStepKind.QUESTION
            }
            assertEquals(expectedKind, definition.kind)
            // Cada paso es renderizable sin adivinar el control.
            assertTrue(definition.control.name.isNotBlank())
        }
    }

    @Test
    fun subtitlesExplainContextNeverUiMechanicsOrRouting() {
        val banned = listOf(
            "se pregunta", "se muestra", "a continuación", "filas editables",
            "nivel 0", "nunca un salto", "modo automático", "cadena de ecuaciones",
            "después de elegirla", "máximo dos", "inputs",
        )
        for (definition in SetupStepDefinitions.definitions.values) {
            if (definition.legacyOnly) continue
            val subtitle = definition.subtitle.orEmpty().lowercase()
            for (phrase in banned) {
                assertFalse(
                    "${definition.id} subtitle leaks UI/routing «$phrase»: ${definition.subtitle}",
                    subtitle.contains(phrase),
                )
            }
        }
    }

    @Test
    fun equationSexKeepsAnExplicitUnknownOptionThatLegacyNeutralAnswersDoNotMigrateInto() {
        val equationSex = requireNotNull(SetupStepDefinitions.of(SetupStepId.EQUATION_SEX))
        // Opción explícita de desconocimiento: permitida en nutrición manual.
        assertEquals(
            setOf("female", "male", "unknown"),
            SetupStepDefinitions.optionValues(SetupStepId.EQUATION_SEX),
        )
        assertEquals("No lo sé", equationSex.option("unknown")?.label)
        // La neutralidad legacy sigue PENDIENTE: solo macho/hembra migran.
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.N_SEX, "Prefiero no responder"))
        assertEquals("female", SetupStepDefinitions.migratedValue(WizChatQuestionId.N_SEX, "Femenino"))
        assertEquals("male", SetupStepDefinitions.migratedValue(WizChatQuestionId.N_SEX, "Masculino"))
        // La identidad de género nunca tiene mapa hacia el sexo de cálculo.
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.P_GENDER, "Mujer"))
    }
}