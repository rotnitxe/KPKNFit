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
    fun prioritiesIsAFiveMuscleSelectionWithOnePointPerMuscle() {
        val priorities = requireNotNull(SetupStepDefinitions.of(SetupStepId.PRIORITIES))
        assertEquals(SetupControlKind.MUSCLE_SYMBOLS, priorities.control)
        assertEquals(ORDER_MUSCLE_OPTIONS, priorities.options)
        // Doce músculos con símbolo más «Erectores Espinales», que el motor conoce pero no tiene símbolo.
        assertEquals(13, priorities.options.size)
        assertEquals(5, priorities.budget)
        assertEquals(1, priorities.maxPerItem)
        // Only reorders exercises: the engine's canonical muscle names, stable.
        assertEquals(ORDER_MUSCLE_OPTIONS.map { it.value }, priorities.options.map { it.value })
        assertTrue("Antebrazo", priorities.options.any { it.value == "Antebrazo" })
        // Cada símbolo tiene su músculo canónico en la lista.
        assertTrue(MuscleSymbol.entries.all { symbol -> priorities.option(MuscleSymbols.canonical(symbol)) != null })
    }

    @Test
    fun everyNewEntrenoStepDeclaresItsSymbolControlAndANaturalCopy() {
        val expected = mapOf(
            SetupStepId.EQUIPMENT to SetupControlKind.PLACES,
            SetupStepId.AVAILABILITY to SetupControlKind.EQUIPMENT_SYMBOLS,
            SetupStepId.GOAL to SetupControlKind.GOAL_PROFILES,
            SetupStepId.FRESH_DAY to SetupControlKind.FRESH_DAY,
            SetupStepId.WEEKDAYS to SetupControlKind.WEEK_CALENDAR,
            SetupStepId.SESSION_TIME to SetupControlKind.SESSION_DIAL,
            SetupStepId.CAPABILITIES to SetupControlKind.CAPABILITIES,
            SetupStepId.PRIORITIES to SetupControlKind.MUSCLE_SYMBOLS,
            SetupStepId.TRAINING_MAX to SetupControlKind.LIFT_MARKS,
            SetupStepId.PLAN to SetupControlKind.PLAN_REVEAL,
            SetupStepId.WEEK_LAYOUT to SetupControlKind.WEEK_LAYOUT,
        )
        expected.forEach { (step, control) ->
            val definition = requireNotNull(SetupStepDefinitions.of(step))
            assertEquals("$step", control, definition.control)
            assertFalse("$step ya no es solo lectura", definition.legacyOnly)
        }
        // Los textos finales de docs/entreno-v2/COPY.md (término «programa»).
        assertEquals("¿Dónde entrenas?", SetupStepDefinitions.title(SetupStepId.EQUIPMENT))
        assertEquals("Elige uno o varios lugares.", SetupStepDefinitions.subtitle(SetupStepId.EQUIPMENT))
        assertEquals("¿Con qué material entrenas?", SetupStepDefinitions.title(SetupStepId.AVAILABILITY))
        assertEquals("¿Qué día llegas con más energía?", SetupStepDefinitions.title(SetupStepId.FRESH_DAY))
        assertEquals("¿Qué días puedes entrenar?", SetupStepDefinitions.title(SetupStepId.WEEKDAYS))
        assertEquals("¿Cuánto tiempo tienes por sesión?", SetupStepDefinitions.title(SetupStepId.SESSION_TIME))
        assertEquals("¿Qué ejercicios ya te salen?", SetupStepDefinitions.title(SetupStepId.CAPABILITIES))
        assertEquals("¿Conoces tus marcas?", SetupStepDefinitions.title(SetupStepId.TRAINING_MAX))
        assertEquals("Tu programa a medida", SetupStepDefinitions.title(SetupStepId.PLAN))
        assertEquals("Así queda tu semana", SetupStepDefinitions.title(SetupStepId.WEEK_LAYOUT))
        // El material ofrece un símbolo por implemento y «solo peso corporal» es exclusivo.
        assertEquals(
            EquipmentSymbolId.entries.map { it.name },
            SetupStepDefinitions.options(SetupStepId.AVAILABILITY).map { it.value },
        )
        assertEquals(setOf("BODYWEIGHT_ONLY"), SetupStepDefinitions.of(SetupStepId.AVAILABILITY)?.exclusiveValues)
        // Los lugares salen del contrato, en su orden.
        assertEquals(
            listOf("gym", "home", "public"),
            SetupStepDefinitions.options(SetupStepId.EQUIPMENT).map { it.value },
        )
    }

    @Test
    fun retiredTrainingStepsKeepTheirDefinitionsOnlyToReadOldDrafts() {
        val retired = listOf(
            SetupStepId.ROUTE, SetupStepId.STYLE, SetupStepId.DAYS, SetupStepId.SPLIT, SetupStepId.TRAINING_MARKS,
            SetupStepId.AUTOREGULATION, SetupStepId.AUTOREGULATION_CONFIRM, SetupStepId.WARMUPS, SetupStepId.TRAINING_REVIEW,
        )
        retired.forEach { step ->
            val definition = requireNotNull(SetupStepDefinitions.of(step))
            assertTrue("$step", definition.legacyOnly)
            assertTrue("$step no es una pregunta del alta", SetupStepDefinitions.isLegacyOnly(step))
        }
        // Sus preguntas antiguas ya no migran ni se pintan como copia.
        assertFalse(WizChatQuestionId.T_DAYS in SetupStepDefinitions.legacyMigrationTargets)
        assertFalse(WizChatQuestionId.T_STYLE in SetupStepDefinitions.legacyMigrationTargets)
        assertFalse(WizChatQuestionId.T_ROUTE in SetupStepDefinitions.legacyMigrationTargets)
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
        assertEquals(SetupControlKind.MUSCLE_SYMBOLS, SetupStepDefinitions.control(SetupStepId.PRIORITIES))
        assertEquals(SetupControlKind.MILESTONE, SetupStepDefinitions.control(SetupStepId.MILESTONE_RINGS))
        assertEquals(SetupControlKind.REVIEW, SetupStepDefinitions.control(SetupStepId.REVIEW_ACTIVATE))
    }

    @Test
    fun bodyFatIsAMandatoryRulerStepWithoutSubtitleOrSkip() {
        val bodyFat = requireNotNull(SetupStepDefinitions.of(SetupStepId.BODY_FAT))
        assertEquals(SetupControlKind.PHYSIQUE, bodyFat.control)
        assertEquals("¿Cuál es tu porcentaje de grasa corporal?", bodyFat.title)
        // Obligatorio y sin subtítulo: la figura y la regla se explican solas.
        assertFalse("la grasa corporal ya no se puede omitir", bodyFat.allowSkip)
        assertNull(bodyFat.subtitle)
        // Grasa ACTUAL: las fuentes antiguas (medida, visual, omitida) siguen en el catálogo para leer borradores viejos.
        assertEquals("measured", bodyFat.option("measured")?.value)
        assertEquals("visual", bodyFat.option("visual")?.value)
        assertEquals("unknown", bodyFat.option("unknown")?.value)
        // El rango numérico del valor declarado: la regla cubre 5–50 %, pero un dato antiguo de 3–60 % sigue valiendo.
        assertEquals(3.0, bodyFat.range?.min ?: 0.0, 0.0001)
        assertEquals(60.0, bodyFat.range?.max ?: 0.0, 0.0001)
        assertEquals("%", bodyFat.unit)
    }

    @Test
    fun questionStepsCarryNaturalQuestionTitlesExceptResultsAndMilestones() {
        for (definition in SetupStepDefinitions.definitions.values) {
            if (definition.legacyOnly) continue
            // El programa y la semana armada son pantallas de resultado: llevan etiqueta, no pregunta.
            val isLabeledScreen = definition.kind != SetupStepKind.QUESTION ||
                definition.control == SetupControlKind.RESULT_PREVIEW ||
                definition.control == SetupControlKind.PLAN_REVEAL ||
                definition.control == SetupControlKind.WEEK_LAYOUT
            if (isLabeledScreen || definition.id == SetupStepId.NAME) continue
            assertTrue(
                "${definition.id} should be a natural question, got «${definition.title}»",
                definition.title.startsWith("¿") && definition.title.endsWith("?"),
            )
        }
        // Excepciones: bases, resultados y el review siguen siendo etiquetas.
        assertEquals("Datos básicos", SetupStepDefinitions.title(SetupStepId.MILESTONE_BASICS))
        assertEquals("Tu plan de alimentación", SetupStepDefinitions.title(SetupStepId.NUTRITION_RESULT))
        assertEquals("Tus RINGS", SetupStepDefinitions.title(SetupStepId.RINGS_RESULT))
        assertEquals("Revisión del plan", SetupStepDefinitions.title(SetupStepId.TRAINING_REVIEW))
        assertEquals("Revisión y activación", SetupStepDefinitions.title(SetupStepId.REVIEW_ACTIVATE))
        // Preguntas grandes de referencia para el bloque de datos básicos.
        assertEquals("Pon tu alias", SetupStepDefinitions.title(SetupStepId.NAME))
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
                // Con los diez perfiles como opciones ya no hay valores de lectura sin opción: toda etiqueta antigua
                // apunta a una opción real del paso.
                assertTrue(
                    "${definition.id} maps to $value but that option does not exist",
                    value in optionValues,
                )
            }
        }
    }

    @Test
    fun goalOffersTheTenProfilesAndLegacyLabelsStayReadable() {
        val goal = requireNotNull(SetupStepDefinitions.of(SetupStepId.GOAL))
        // Tres perfiles generales y siete disciplinas: el valor estable es el nombre del perfil en minúsculas.
        assertEquals(TrainingGoalProfile.entries.map { it.name.lowercase() }, goal.options.map { it.value })
        assertEquals(TrainingGoalProfile.entries.map { it.label }, goal.options.map { it.label })
        assertEquals(10, goal.options.size)
        assertEquals(
            TrainingGoalProfile.entries.map { it.tagline },
            goal.options.map { it.description },
        )
        // Los valores antiguos (strength, muscle, health, mixed, complete_athlete) ya no son opciones: se leen
        // con `EntrenoStepValues.goalProfileOf`.
        listOf("strength", "muscle", "health", "mixed", "complete_athlete").forEach {
            assertFalse(it, it in goal.options.map { option -> option.value })
        }
        // Las etiquetas del chat antiguo resuelven al perfil que hoy les corresponde.
        assertEquals("functional_health", goal.migratedValue("Salud y condición"))
        assertEquals("strength_cardio", goal.migratedValue("Fuerza + cardio"))
        assertEquals("strength_cardio", goal.migratedValue("Atleta completo"))
        assertEquals("powerlifting", goal.migratedValue("Fuerza"))
        assertEquals("bodybuilding", goal.migratedValue("Músculo"))
        assertEquals("strength_muscle", goal.migratedValue("Fuerza y músculo"))
        // Los minutos por sesión son un reloj de 20 a 180 (múltiplos de 5).
        val sessionTime = requireNotNull(SetupStepDefinitions.of(SetupStepId.SESSION_TIME))
        assertEquals(20.0, sessionTime.range?.min ?: 0.0, 0.0001)
        assertEquals(180.0, sessionTime.range?.max ?: 0.0, 0.0001)
        // Cardio: tres modalidades actuales y los cuatro escalones 10/15/20/30.
        assertEquals(
            listOf("WALK", "RUN_OUTDOOR", "BIKE_OUTDOOR"),
            SetupStepDefinitions.options(SetupStepId.CARDIO_TYPE).map { it.value },
        )
        assertEquals(
            listOf("10", "15", "20", "30"),
            SetupStepDefinitions.options(SetupStepId.CARDIO_TIME).map { it.value },
        )
        // Los pasos de inventario (kg/cantidades) no vuelven a la ruta.
        val route = SetupStepGraph.stepIds(SetupStepContext())
        assertFalse(SetupStepId.INVENTORY_DUMBBELLS in route)
        assertFalse(SetupStepId.INVENTORY_MACHINES in route)
        assertTrue(SetupStepId.AVAILABILITY in route)
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
        // Los días como número ya no son una pregunta: sale de los días de la semana (T_DAYS no migra).
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.T_DAYS, "3"))
        assertEquals("1", SetupStepDefinitions.migratedValue(WizChatQuestionId.T_WEEKDAYS, "Lunes"))
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.T_WEEKDAYS, "Finde"))
    }

    @Test
    fun legacyMigrationTargetsCoverEveryRenderableBridgeAndSkipsLegacyOnlySteps() {
        val targets = SetupStepDefinitions.legacyMigrationTargets

        // Productive 1:1 copies are covered.
        assertEquals(SetupStepId.NAME, targets[WizChatQuestionId.P_NAME])
        assertEquals(SetupStepId.AGE, targets[WizChatQuestionId.P_AGE])
        assertEquals(SetupStepId.WEEKDAYS, targets[WizChatQuestionId.T_WEEKDAYS])
        assertEquals(SetupStepId.GOAL, targets[WizChatQuestionId.T_GOAL])
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
        // 14 controles de datos genéricos (algunos solo para leer pasos retirados), 2 pantallas de estructura
        // (MILESTONE/REVIEW) y los 11 controles de símbolos de Entreno v2.
        assertEquals(
            setOf(
                "TEXT", "NUMBER", "PHYSIQUE", "SINGLE_CHOICE", "MULTI_CHOICE", "ROUTE_CHOICE",
                "INVENTORY_PICKER", "SPLIT_EDITOR", "MARKS_EDITOR", "TOGGLE", "AUTO_CONFIRM", "MANUAL_MACROS",
                "EDITOR_ROWS", "RESULT_PREVIEW", "MILESTONE", "REVIEW",
                "PLACES", "EQUIPMENT_SYMBOLS", "GOAL_PROFILES", "FRESH_DAY", "WEEK_CALENDAR", "SESSION_DIAL",
                "CAPABILITIES", "MUSCLE_SYMBOLS", "LIFT_MARKS", "PLAN_REVEAL", "WEEK_LAYOUT",
            ),
            SetupControlKind.entries.mapTo(mutableSetOf()) { it.name },
        )
        assertEquals(27, SetupControlKind.entries.size)
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
        // Las cuatro opciones de la fila de género, las tres respuestas sobre el contexto hormonal
        // (el panel que abre «No lo sé») y la opción explícita de desconocimiento.
        assertEquals(
            setOf(
                "female", "male", "trans_male", "trans_female",
                "hormones_estrogen", "hormones_androgen", "hormones_mixed",
                "unknown",
            ),
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

    @Test
    fun equationSexOffersTheHormonalContextAnswersWithTheirCopy() {
        val equationSex = requireNotNull(SetupStepDefinitions.of(SetupStepId.EQUATION_SEX))

        // Orden del catálogo: los cuatro glifos, el contexto hormonal y «No lo sé» al final.
        assertEquals(
            listOf(
                "female", "male", "trans_male", "trans_female",
                "hormones_estrogen", "hormones_androgen", "hormones_mixed",
                "unknown",
            ),
            equationSex.options.map { it.value },
        )

        val estrogen = requireNotNull(equationSex.option("hormones_estrogen"))
        assertEquals("Predominan los estrógenos", estrogen.label)
        assertEquals("Por ejemplo, ciclo menstrual o terapia con estrógenos.", estrogen.description)
        val androgen = requireNotNull(equationSex.option("hormones_androgen"))
        assertEquals("Predominan los andrógenos", androgen.label)
        assertEquals("Por ejemplo, testosterona propia o terapia con testosterona.", androgen.description)
        val mixed = requireNotNull(equationSex.option("hormones_mixed"))
        assertEquals("Un equilibrio o no lo sé", mixed.label)
        assertEquals("Calculamos con el promedio de ambas ecuaciones.", mixed.description)

        // Solo las tres respuestas hormonales llevan descripción: los glifos y «No lo sé» no.
        val withDescription = equationSex.options.filter { it.description != null }.map { it.value }
        assertEquals(listOf("hormones_estrogen", "hormones_androgen", "hormones_mixed"), withDescription)
        assertNull(equationSex.option("unknown")?.description)
    }

    @Test
    fun equationSexValueConstantsMatchTheCatalog() {
        val values = SetupStepDefinitions.optionValues(SetupStepId.EQUATION_SEX)
        assertEquals("unknown", SetupEquationSexValues.UNKNOWN)
        assertEquals(
            listOf("hormones_estrogen", "hormones_androgen", "hormones_mixed"),
            SetupEquationSexValues.HORMONAL,
        )
        assertTrue(values.containsAll(SetupEquationSexValues.HORMONAL))
        assertTrue(SetupEquationSexValues.UNKNOWN in values)

        // El panel hormonal se abre con «No lo sé» y con cualquier respuesta hormonal, y con nada más.
        assertTrue(SetupEquationSexValues.opensHormonalPanel("unknown"))
        SetupEquationSexValues.HORMONAL.forEach { assertTrue(it, SetupEquationSexValues.opensHormonalPanel(it)) }
        listOf("female", "male", "trans_male", "trans_female").forEach {
            assertFalse(it, SetupEquationSexValues.opensHormonalPanel(it))
        }
        assertFalse(SetupEquationSexValues.opensHormonalPanel(null))
    }

    @Test
    fun theHormonalAnswersAreNotMigratedFromAnyLegacyQuestion() {
        // Contexto hormonal: solo nace de la respuesta de la persona en el paso nuevo.
        val equationSex = requireNotNull(SetupStepDefinitions.of(SetupStepId.EQUATION_SEX))
        assertEquals(setOf("Femenino", "Masculino"), equationSex.legacyValueMap.keys)
        assertEquals(setOf("female", "male"), equationSex.legacyValueMap.values.toSet())
    }

    @Test
    fun routeOptionsUseTheLabelsThatTheLegacyAnswerMapAlreadyRecognises() {
        val route = requireNotNull(SetupStepDefinitions.of(SetupStepId.ROUTE))
        assertEquals("Recomiéndame un plan", route.option("recommended")?.label)
        assertEquals("Elegir un protocolo", route.option("protocol")?.label)
        // Cada etiqueta visible es una clave del mapa legacy: ningún borrador antiguo deja de leerse.
        route.options.forEach { option ->
            assertEquals("etiqueta «${option.label}»", option.value, route.legacyValueMap[option.label])
        }
        // ROUTE ya no es una pregunta del alta: su pregunta antigua no migra a ninguna parte.
        assertNull(SetupStepDefinitions.migratedValue(WizChatQuestionId.T_ROUTE, "Recomiéndame un plan"))
        assertTrue(route.legacyOnly)
    }
}