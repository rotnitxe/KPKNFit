package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionWizardDraft
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.TrainingOptions
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regresiones del paso semántico M1 sobre texto crudo y validación:
 * - `inputTexts` SIEMPRE con clave `step.name` (nunca `SetupStepId`).
 * - El crudo inválido se conserva y bloquea; el mismo número en otro formato
 *   no pisa lo que el usuario está escribiendo; un número realmente distinto
 *   sí reemplaza el crudo obsoleto.
 * - La validación numérica prioriza el crudo, exige enteros en la edad y usa los
 *   rangos del catálogo `SetupStepDefinitions`. El tiempo por sesión es un reloj
 *   (20..180, de 5 en 5): sin texto crudo.
 * - BODY_FAT sin percentil no vale ni respondido; «No lo sé» (omisión de un
 *   borrador antiguo) limpia número, fecha y texto y ya no valida: el paso es
 *   obligatorio. La autorregulación ya no es una pregunta: ningún estado suyo bloquea.
 * - Pesajes con fecha futura inválidos; inventario contra el contrato real.
 */
class SetupStepAnswersTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun draftWithStep(step: SetupStepId): SetupWizardDraft = SetupWizardDraft(
        draftId = "setup-wizard:full",
        commitId = "commit-1",
        stepProgress = SetupStepProgress(currentStepId = step),
    )

    // ─── Texto crudo: teclado intermedio y formatos parciales ────────────────

    @Test
    fun ageKeepsRawIntermediateWhileTypingNineteen() {
        val one = SetupWizardDraft().withStepNumber(SetupStepId.AGE, 1.0, nowEpochMs = 1L)
        // El intermedio «1» vive en `inputTexts["AGE"]` y todavía no valida.
        assertEquals("1", one.inputTexts["AGE"])
        val blocking = SetupWizardValidation.validateStep(one, SetupStepId.AGE)
        assertTrue(blocking.any { it.isBlocking })

        val nineteen = one.withStepNumber(SetupStepId.AGE, 19.0, nowEpochMs = 2L)
        assertEquals("19", nineteen.inputTexts["AGE"])
        assertEquals(19, nineteen.ageYears)
        assertTrue(SetupWizardValidation.validateStep(nineteen, SetupStepId.AGE).none { it.isBlocking })
    }

    @Test
    fun sameNumberKeepsPartialRawInsteadOfCanonicalizing() {
        // UI de texto+número: «70.» sigue escribiéndose cuando el valor es 70.
        val typed = SetupWizardDraft().withStepText(SetupStepId.WEIGHT, "70.", nowEpochMs = 1L)
        val paired = typed.withStepNumber(SetupStepId.WEIGHT, 70.0, nowEpochMs = 2L)

        assertEquals("70.", paired.inputTexts["WEIGHT"])
        assertEquals(70.0, paired.weightKg!!, 0.001)
    }

    @Test
    fun wheelChangeReplacesObsoleteRawWithDifferentNumber() {
        val draft = SetupWizardDraft()
            .withStepText(SetupStepId.WEIGHT, "70.", nowEpochMs = 1L)
            .withStepNumber(SetupStepId.WEIGHT, 72.0, nowEpochMs = 2L)

        // La rueda/regla cambió el valor de verdad: el crudo viejo no arrastra.
        assertEquals("72", draft.inputTexts["WEIGHT"])
        assertEquals(72.0, draft.weightKg!!, 0.001)
    }

    @Test
    fun nullNumberPreservesInvalidRawAndValidationFlagsIt() {
        val draft = SetupWizardDraft()
            .withStepText(SetupStepId.WEIGHT, "abc", nowEpochMs = 1L)
            .withStepNumber(SetupStepId.WEIGHT, null, nowEpochMs = 2L)

        // El texto inválido no se silencia con el retiro del dato.
        assertEquals("abc", draft.inputTexts["WEIGHT"])
        val checks = SetupWizardValidation.validateStep(draft, SetupStepId.WEIGHT)
        assertTrue(checks.any { it.state == SetupValueState.INVALID })
        assertTrue(checks.any { it.isBlocking })
    }

    @Test
    fun canConfirmRejectsInvalidNumberAndAcceptsValidOne() {
        val invalid = draftWithStep(SetupStepId.AGE).withStepNumber(SetupStepId.AGE, 1.0, nowEpochMs = 1L)
        assertFalse(SetupWizardState(invalid).canConfirmStep)

        val valid = draftWithStep(SetupStepId.AGE).withStepNumber(SetupStepId.AGE, 19.0, nowEpochMs = 1L)
        assertTrue(SetupWizardState(valid).canConfirmStep)
    }

    // ─── Validación numérica: crudo manda, enteros y catálogo ────────────────

    @Test
    fun rawNumberWinsOverOlderTypedValue() {
        // Tipado viejo en rango (175) con crudo nuevo fuera de rango (999):
        // manda lo que el usuario está escribiendo ahora.
        val draft = SetupWizardDraft(heightCm = 175.0, inputTexts = mapOf("HEIGHT" to "999"))

        val checks = SetupWizardValidation.validateStep(draft, SetupStepId.HEIGHT)
        assertTrue(checks.any { it.state == SetupValueState.INVALID })
    }

    @Test
    fun fractionalAgeIsRejectedAndSessionTimeIsADialWithoutRawText() {
        val age = SetupWizardDraft().withStepText(SetupStepId.AGE, "19.5", nowEpochMs = 1L)
        assertTrue(
            SetupWizardValidation.validateStep(age, SetupStepId.AGE)
                .any { it.state == SetupValueState.INVALID },
        )

        // El tiempo por sesión es un reloj: el texto libre no escribe nada y el número se redondea a múltiplos de 5.
        val typed = SetupWizardDraft().withStepText(SetupStepId.SESSION_TIME, "45.5", nowEpochMs = 1L)
        assertNull(typed.minutesPerSession)
        assertNull(typed.inputTexts["SESSION_TIME"])
        assertTrue(SetupWizardValidation.validateStep(typed, SetupStepId.SESSION_TIME).any { it.isBlocking })
        val dial = SetupWizardDraft().withStepNumber(SetupStepId.SESSION_TIME, 47.0, nowEpochMs = 1L)
        assertEquals(45, dial.minutesPerSession)
        assertTrue(SetupWizardValidation.validateStep(dial, SetupStepId.SESSION_TIME).none { it.isBlocking })
    }

    // ─── BODY_FAT: percentil obligatorio con fuente real; UNKNOWN limpia y no valida ─────

    @Test
    fun bodyFatWithoutPercentBlocksEvenWithOldRecordedAnswer() {
        for (source in listOf(SetupBodyFatSource.MEASURED, SetupBodyFatSource.VISUAL_ESTIMATE)) {
            val draft = SetupWizardDraft(bodyFatSource = source).let { base ->
                base.copy(
                    stepProgress = base.stepProgress.recordAnswer(
                        SetupStepId.BODY_FAT,
                        SetupAnswerProvenance.USER_DECLARED,
                        SetupValueState.DECLARED,
                    ),
                )
            }
            val checks = SetupWizardValidation.validateStep(draft, SetupStepId.BODY_FAT)
            assertTrue("source=$source", checks.any { it.isBlocking })
        }
    }

    @Test
    fun bodyFatUnknownClearsNumberTimestampAndStaleRaw() {
        val draft = SetupWizardDraft(
            bodyFatPercent = 15.0,
            bodyFatSource = SetupBodyFatSource.MEASURED,
            bodyFatCapturedAtEpochMs = 123L,
            inputTexts = mapOf("BODY_FAT" to "15"),
        ).withStepChoice(SetupStepId.BODY_FAT, "UNKNOWN", nowEpochMs = 999L)

        assertEquals(SetupBodyFatSource.UNKNOWN, draft.bodyFatSource)
        assertNull(draft.bodyFatPercent)
        assertNull(draft.bodyFatCapturedAtEpochMs)
        assertFalse("BODY_FAT" in draft.inputTexts)
        assertEquals(setOf("UNKNOWN"), draft.selectedValues(SetupStepId.BODY_FAT))
        // El paso es obligatorio: una omisión de un borrador antiguo ya no valida, hay que declarar un porcentaje.
        val checks = SetupWizardValidation.validateStep(draft, SetupStepId.BODY_FAT)
        assertTrue(checks.any { it.isBlocking })
        assertEquals(BODY_FAT_PENDING_MESSAGE, checks.single { it.isBlocking }.message)
    }

    @Test
    fun bodyFatSourceSwitchDoesNotReuseActiveValueOfOtherSource() {
        val measured = SetupWizardDraft(
            bodyFatPercent = 20.0,
            bodyFatSource = SetupBodyFatSource.MEASURED,
            bodyFatCapturedAtEpochMs = 111L,
            inputTexts = mapOf("BODY_FAT" to "20"),
        )

        // MEASURED → VISUAL: la medición NO se etiqueta como estimación activa;
        // se retira percentil, fecha y crudo hasta que la figura declare.
        val visual = measured.withStepChoice(SetupStepId.BODY_FAT, "VISUAL_ESTIMATE", nowEpochMs = 999L)
        assertEquals(SetupBodyFatSource.VISUAL_ESTIMATE, visual.bodyFatSource)
        assertNull(visual.bodyFatPercent)
        assertNull(visual.bodyFatCapturedAtEpochMs)
        assertFalse("BODY_FAT" in visual.inputTexts)
        assertTrue(
            SetupWizardValidation.validateStep(visual, SetupStepId.BODY_FAT).any { it.isBlocking },
        )
        // Figura/slider independientes intactos y sin avanzar nada.
        assertEquals(measured.physiqueModel, visual.physiqueModel)
        assertEquals(measured.physiqueSliderPosition, visual.physiqueSliderPosition)
        assertEquals(measured.stepProgress, visual.stepProgress)

        // Inverso: VISUAL → MEASURED tampoco fabrica una medición desde la estimación.
        val back = visual.copy(
            bodyFatPercent = 17.5,
            bodyFatSource = SetupBodyFatSource.VISUAL_ESTIMATE,
            bodyFatCapturedAtEpochMs = 222L,
            inputTexts = mapOf("BODY_FAT" to "17,5"),
        ).withStepChoice(SetupStepId.BODY_FAT, "MEASURED", nowEpochMs = 1000L)
        assertEquals(SetupBodyFatSource.MEASURED, back.bodyFatSource)
        assertNull(back.bodyFatPercent)
        assertNull(back.bodyFatCapturedAtEpochMs)
        assertFalse("BODY_FAT" in back.inputTexts)
    }

    @Test
    fun bodyFatSameSourceKeepsValueAndExactTimestamp() {
        // Flujo M3: primero el número, después la elección de la MISMA fuente.
        val firstSource = SetupWizardDraft()
            .withStepChoice(SetupStepId.BODY_FAT, "VISUAL_ESTIMATE", nowEpochMs = 100L)
        val declared = firstSource.withStepNumber(SetupStepId.BODY_FAT, 18.5, nowEpochMs = 555L)

        // Re-seleccionar la misma fuente conserva dato, fecha EXACTA y crudo.
        val reselect = declared.withStepChoice(SetupStepId.BODY_FAT, "VISUAL_ESTIMATE", nowEpochMs = 999L)
        assertEquals(18.5, reselect.bodyFatPercent!!, 0.001)
        assertEquals(555L, reselect.bodyFatCapturedAtEpochMs)
        assertEquals("18.5", reselect.inputTexts["BODY_FAT"])
        assertTrue(SetupWizardValidation.validateStep(reselect, SetupStepId.BODY_FAT).none { it.isBlocking })
    }

    // ─── AUTO: isAnswered no sustituye la confirmación explícita ─────────────

    @Test
    fun retiredAutoregulationStepsNeverBlockWhateverTheSavedDraftHolds() {
        // La autorregulación ya no es una pregunta del alta («sugerir y confirmar» es la única modalidad): un borrador
        // viejo con AUTO sin confirmar no bloquea ningún paso (la compatibilidad lo devuelve a PROPOSE).
        val auto = TrainingOptions(autoregulationMode = AutoregulationMode.AUTO, automaticConfirmed = false)
        val draft = SetupWizardDraft(trainingOptions = auto)
        listOf(
            SetupStepId.AUTOREGULATION, SetupStepId.AUTOREGULATION_CONFIRM, SetupStepId.WARMUPS,
            SetupStepId.TRAINING_REVIEW, SetupStepId.SPLIT, SetupStepId.DAYS, SetupStepId.STYLE,
            SetupStepId.ROUTE, SetupStepId.TRAINING_MARKS,
        ).forEach { step ->
            assertTrue("$step", SetupWizardValidation.validateStep(draft, step).none { it.isBlocking })
        }
    }

    // ─── Pesajes: fecha futura inválida; pasada válida ───────────────────────

    @Test
    fun futureWeighInIsRejectedAndPastOnePasses() {
        val future = SetupWizardDraft(
            historicalWeighIns = listOf(SetupWeighIn("w-1", "2999-01-01", 80.0)),
        )
        assertTrue(
            SetupWizardValidation.validateStep(future, SetupStepId.NUTRITION_WEIGH_INS)
                .any { it.state == SetupValueState.INVALID },
        )

        val past = SetupWizardDraft(
            historicalWeighIns = listOf(SetupWeighIn("w-2", LocalDate.now().minusDays(3).toString(), 80.0)),
        )
        assertTrue(
            SetupWizardValidation.validateStep(past, SetupStepId.NUTRITION_WEIGH_INS).none { it.isBlocking },
        )
    }

    // ─── Inventario con pesos: retirado del asistente (D2.5) ─────────────────

    @Test
    fun retiredInventoryStepsNeverBlockWhateverTheSavedDraftHolds() {
        // Los pasos INVENTORY_* ya no están en la ruta ni tienen pantalla: un borrador
        // viejo con inventario incompleto, sin declarar o con una fila abierta no puede
        // bloquear nada (y el inventario guardado no se toca).
        val dishonest = SetupWizardDraft(
            trainingOptions = TrainingOptions(
                inventory = EquipmentInventory(plates = listOf(PlateStock(20.0, null))),
            ),
            stepEditors = mapOf(SetupStepId.INVENTORY_PLATES to SetupStepEditorState(editing = true, itemIndex = 1)),
        )
        listOf(
            SetupStepId.INVENTORY_BARBELL, SetupStepId.INVENTORY_PLATES, SetupStepId.INVENTORY_DUMBBELLS,
            SetupStepId.INVENTORY_KETTLEBELLS, SetupStepId.INVENTORY_MACHINES,
        ).forEach { step ->
            assertTrue("$step", SetupWizardValidation.validateStep(dishonest, step).none { it.isBlocking })
            assertTrue("$step vacío", SetupWizardValidation.validateStep(SetupWizardDraft(), step).none { it.isBlocking })
        }
        assertEquals(20.0, dishonest.trainingOptions.inventory!!.plates.single().weightKg, 0.001)
    }

    @Test
    fun unconfirmedAutoregulationDoesNotBlockInventoryStep() {
        // Dato de barra válido + una pendiente ajena (AUTO sin confirmar): el
        // paso de material solo mira SU material, nunca razones de otros bloques.
        val draft = SetupWizardDraft(
            trainingOptions = TrainingOptions(
                inventory = EquipmentInventory(barbellWeightKg = 20.0),
                autoregulationMode = AutoregulationMode.AUTO,
                automaticConfirmed = false,
            ),
        )
        assertTrue(
            SetupWizardValidation.validateStep(draft, SetupStepId.INVENTORY_BARBELL).none { it.isBlocking },
        )
    }

    // ─── Lugares y material: símbolos, no inventario de kilos ────────────────

    @Test
    fun theRouteNeverAsksStockWhateverThePlaces() {
        listOf(
            emptySet(), setOf(TrainingPlace.GYM), setOf(TrainingPlace.HOME), setOf(TrainingPlace.PUBLIC),
            setOf(TrainingPlace.GYM, TrainingPlace.HOME),
        ).forEach { places ->
            val draft = SetupWizardDraft().withPlaces(places)
            assertTrue("$places", draft.inventoryGroups().isEmpty())
            assertTrue("$places", draft.stepContext().inventoryGroups.isEmpty())
            val route = SetupStepGraph.stepIds(draft.stepContext())
            assertTrue("$places", SetupStepId.AVAILABILITY in route)
            assertFalse("$places", SetupStepId.HOME_EQUIPMENT in route)
        }
    }

    /**
     * Camino productivo (validateStep/validateAll) con perfil legacy vacío: en
     * casa `equipment` queda vacío (el material vive en `trainingOptions`),
     * HOME_EQUIPMENT no está en la ruta y cada grupo se resuelve con elección
     * explícita «none» o dato propio. La validación por capas legacy
     * (`validate(chapter)`, con `equipment.isEmpty()` en WEEK) no la consume
     * ningún camino productivo ni review: se conserva solo por compatibilidad.
     */
    @Test
    fun homeRouteNeverBlocksOnEmptyLegacyProfile() {
        val home = SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME))
        assertTrue(home.equipment.isEmpty())
        assertTrue(SetupWizardValidation.validateStep(home, SetupStepId.EQUIPMENT).none { it.isBlocking })
        assertFalse(SetupStepId.HOME_EQUIPMENT in SetupStepGraph.stepIds(home.stepContext()))

        val withNones = SetupWizardDraft(
            trainingEnvironment = "home",
            equipment = emptySet(),
            stepSelections = listOf(
                SetupStepId.INVENTORY_BARBELL,
                SetupStepId.INVENTORY_PLATES,
                SetupStepId.INVENTORY_DUMBBELLS,
                SetupStepId.INVENTORY_KETTLEBELLS,
                SetupStepId.INVENTORY_MACHINES,
            ).associateWith { listOf("none") },
        )
        listOf(
            SetupStepId.INVENTORY_BARBELL, SetupStepId.INVENTORY_PLATES,
            SetupStepId.INVENTORY_DUMBBELLS, SetupStepId.INVENTORY_KETTLEBELLS,
            SetupStepId.INVENTORY_MACHINES,
        ).forEach { group ->
            assertTrue(group.name, SetupWizardValidation.validateStep(withNones, group).none { it.isBlocking })
        }
    }

    @Test
    fun placeChangeKeepsTheStoredInventoryAndReseedsOnlyTheAvailability() {
        val gym = SetupWizardDraft(
            trainingOptions = TrainingOptions(
                inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(20.0, 2))),
            ),
            stepSelections = mapOf(SetupStepId.INVENTORY_PLATES to listOf("small_plates")),
            stepEditors = mapOf(SetupStepId.INVENTORY_PLATES to SetupStepEditorState(editing = true, itemIndex = 0)),
        ).withPlaces(setOf(TrainingPlace.GYM))
        assertEquals("gym", gym.trainingEnvironment)
        assertEquals(setOf(SetupEquipment.GYM), gym.equipment)

        val home = gym.withStepChoice(SetupStepId.EQUIPMENT, "home", nowEpochMs = 1L)
        assertEquals(setOf(TrainingPlace.HOME), home.trainingPlaces)
        assertEquals("home", home.trainingEnvironment)
        // Un SOLO inventario principal: el cambio de lugar NO borra lo declarado
        // ni las filas a medias; solo retira el perfil legacy ajeno.
        assertEquals(gym.trainingOptions.inventory, home.trainingOptions.inventory)
        assertEquals(listOf("small_plates"), home.stepSelections[SetupStepId.INVENTORY_PLATES])
        assertTrue(home.stepEditors.getValue(SetupStepId.INVENTORY_PLATES).editing)
        assertTrue(home.equipment.isEmpty())
        // El alta ya no abre el inventario de kilos: cambiar de lugar no marca esos pasos.
        assertTrue(home.stepProgress.pendingReview.isEmpty())
        // En casa se ofrece el mismo material que en el gimnasio: lo marcado se conserva (la persona puede apagarlo).
        assertEquals(gym.selectedEquipmentSymbols(), home.selectedEquipmentSymbols())
        assertTrue(EquipmentSymbolId.BARBELL in home.selectedEquipmentSymbols())

        // Re-pulsar el MISMO lugar: conserva todo y no marca nada.
        val same = gym.withStepChoice(SetupStepId.EQUIPMENT, "gym", nowEpochMs = 2L)
        assertEquals(gym.trainingOptions.inventory, same.trainingOptions.inventory)
        assertEquals(setOf(SetupEquipment.GYM), same.equipment)
        assertEquals(gym.trainingOptions.availability, same.trainingOptions.availability)
        assertTrue(same.stepProgress.pendingReview.isEmpty())
    }

    // ─── Rings parcial: «No lo sé» explícito nunca bloquea ───────────────────

    @Test
    fun ringsUnknownHistoryAndUnknownFeelingsKeepPartialCheckInUsable() {
        val draft = SetupWizardDraft()
            .withStepChoice(SetupStepId.RINGS_RECENT, "unknown", nowEpochMs = 1L)
            .withStepChoice(SetupStepId.RINGS_MUSCLE_FEELING, "unknown", nowEpochMs = 1L)

        assertTrue(SetupWizardValidation.validateStep(draft, SetupStepId.RINGS_RECENT).none { it.isBlocking })
        assertTrue(SetupWizardValidation.validateStep(draft, SetupStepId.RINGS_MUSCLE_FEELING).none { it.isBlocking })
    }

    // ─── Género: base de la ecuación y contexto hormonal ─────────────────────

    private fun SetupWizardDraft.chooseSex(value: String): SetupWizardDraft =
        withStepChoice(SetupStepId.EQUATION_SEX, value, nowEpochMs = 1L)

    private fun sexChecks(draft: SetupWizardDraft) =
        SetupWizardValidation.validateStep(draft, SetupStepId.EQUATION_SEX)

    private fun typedSex(sex: EerSex?) = SetupWizardDraft(nutritionDraft = NutritionWizardDraft(equationSex = sex))

    @Test
    fun everyGenderOptionProjectsToItsEquationBase() {
        val expected = mapOf(
            "female" to EerSex.FEMALE,
            "trans_female" to EerSex.FEMALE,
            "hormones_estrogen" to EerSex.FEMALE,
            "male" to EerSex.MALE,
            "trans_male" to EerSex.MALE,
            "hormones_androgen" to EerSex.MALE,
            "hormones_mixed" to EerSex.AVERAGE,
            "unknown" to null,
        )
        // El mapa cubre TODAS las opciones del catálogo: una opción nueva obliga a decidir aquí su base.
        assertEquals(SetupStepDefinitions.optionValues(SetupStepId.EQUATION_SEX), expected.keys)

        expected.forEach { (value, sex) ->
            val draft = SetupWizardDraft().chooseSex(value)
            assertEquals("base de $value", sex, draft.nutritionDraft?.equationSex)
            assertEquals("selección de $value", setOf(value), draft.selectedValues(SetupStepId.EQUATION_SEX))
        }
    }

    @Test
    fun hormonalAnswersReplaceUnknownInTheSingleSelectionAndGlyphsReplaceThem() {
        val unknown = SetupWizardDraft().chooseSex("unknown")
        assertEquals(setOf("unknown"), unknown.selectedValues(SetupStepId.EQUATION_SEX))
        assertNull(unknown.nutritionDraft?.equationSex)

        // Elegir una respuesta hormonal reemplaza «unknown»: la selección sigue siendo UNA.
        val estrogen = unknown.chooseSex("hormones_estrogen")
        assertEquals(setOf("hormones_estrogen"), estrogen.selectedValues(SetupStepId.EQUATION_SEX))
        assertEquals(EerSex.FEMALE, estrogen.nutritionDraft?.equationSex)

        val androgen = estrogen.chooseSex("hormones_androgen")
        assertEquals(setOf("hormones_androgen"), androgen.selectedValues(SetupStepId.EQUATION_SEX))
        assertEquals(EerSex.MALE, androgen.nutritionDraft?.equationSex)

        val mixed = androgen.chooseSex("hormones_mixed")
        assertEquals(setOf("hormones_mixed"), mixed.selectedValues(SetupStepId.EQUATION_SEX))
        assertEquals(EerSex.AVERAGE, mixed.nutritionDraft?.equationSex)

        // Elegir un glifo reemplaza el contexto hormonal.
        val glyph = mixed.chooseSex("trans_female")
        assertEquals(setOf("trans_female"), glyph.selectedValues(SetupStepId.EQUATION_SEX))
        assertEquals(EerSex.FEMALE, glyph.nutritionDraft?.equationSex)

        // Volver a «No lo sé» deja otra vez la base sin determinar.
        val again = glyph.chooseSex("unknown")
        assertEquals(setOf("unknown"), again.selectedValues(SetupStepId.EQUATION_SEX))
        assertNull(again.nutritionDraft?.equationSex)
    }

    @Test
    fun choosingTheEquationBaseNeverTouchesTheProfileGender() {
        val base = SetupWizardDraft(profileGender = Gender.MALE)
        SetupStepDefinitions.optionValues(SetupStepId.EQUATION_SEX).forEach { value ->
            assertEquals("género de perfil tras $value", Gender.MALE, base.chooseSex(value).profileGender)
        }
    }

    @Test
    fun theReferenceFigureStartsFromWhatWasChosenAndStaysChangeable() {
        val fresh = SetupWizardDraft() // figura de arranque: «male»
        mapOf(
            "female" to "female", "trans_female" to "female", "hormones_estrogen" to "female",
            "male" to "male", "trans_male" to "male", "hormones_androgen" to "male",
        ).forEach { (value, figure) ->
            // Parte de la figura contraria para comprobar que de verdad se mueve.
            val start = fresh.copy(physiqueModel = if (figure == "female") "male" else "female")
            assertEquals("figura tras $value", figure, start.chooseSex(value).physiqueModel)
        }
        // «Equilibrio» y «No lo sé» no eligen figura: se queda la que había.
        listOf("hormones_mixed", "unknown").forEach { value ->
            assertEquals("figura tras $value", "female", fresh.copy(physiqueModel = "female").chooseSex(value).physiqueModel)
            assertEquals("figura tras $value", "male", fresh.chooseSex(value).physiqueModel)
        }
        // Cambiar la figura después es solo una referencia visual: la base de la ecuación no se mueve.
        val chosen = fresh.chooseSex("female")
        assertEquals("female", chosen.physiqueModel)
        assertEquals(EerSex.FEMALE, chosen.copy(physiqueModel = "male").nutritionDraft?.equationSex)
    }

    @Test
    fun theFigureStopsFollowingTheGenderOnceTheBodyFatIsDeclared() {
        val declared = SetupWizardDraft(physiqueModel = "male").withBodyFatRulerValue(20)
        assertEquals("male", declared.chooseSex("female").physiqueModel)
        assertEquals("male", declared.chooseSex("hormones_estrogen").physiqueModel)
    }

    @Test
    fun rehydratedDraftsWithoutASavedSelectionKeepProducingTheirSelection() {
        assertEquals(setOf("female"), typedSex(EerSex.FEMALE).selectedValues(SetupStepId.EQUATION_SEX))
        assertEquals(setOf("male"), typedSex(EerSex.MALE).selectedValues(SetupStepId.EQUATION_SEX))
        // El promedio solo nace del equilibrio hormonal: la base tipada se ve como la opción que la produce.
        assertEquals(setOf("hormones_mixed"), typedSex(EerSex.AVERAGE).selectedValues(SetupStepId.EQUATION_SEX))
        // Sin base no se inventa ningún valor.
        assertTrue(typedSex(null).selectedValues(SetupStepId.EQUATION_SEX).isEmpty())
        assertTrue(SetupWizardDraft().selectedValues(SetupStepId.EQUATION_SEX).isEmpty())

        // Con selección guardada manda la guardada: ni los glifos trans ni las respuestas hormonales se pierden.
        val stored = typedSex(EerSex.FEMALE).copy(
            stepSelections = mapOf(SetupStepId.EQUATION_SEX to listOf("hormones_estrogen")),
        )
        assertEquals(setOf("hormones_estrogen"), stored.selectedValues(SetupStepId.EQUATION_SEX))
    }

    @Test
    fun theGenderStepOnlyValidatesWithADeterminedEquationBase() {
        // Sin respuesta: bloquea.
        val empty = sexChecks(SetupWizardDraft()).single()
        assertTrue(empty.isBlocking)
        assertEquals(SetupValueState.ABSENT, empty.state)

        // «No lo sé» a solas: bloquea y pide contar qué hormonas predominan.
        val unknown = sexChecks(SetupWizardDraft().chooseSex("unknown")).single()
        assertTrue(unknown.isBlocking)
        assertEquals(SetupValueState.ABSENT, unknown.state)
        assertEquals("equationSex", unknown.key)
        assertEquals("Cuéntanos qué hormonas predominan en tu cuerpo", unknown.message)

        // Cada opción con una base determinada valida: los cuatro glifos y las tres respuestas hormonales.
        listOf(
            "female", "male", "trans_male", "trans_female",
            "hormones_estrogen", "hormones_androgen", "hormones_mixed",
        ).forEach { value ->
            assertTrue(value, sexChecks(SetupWizardDraft().chooseSex(value)).none { it.isBlocking })
        }

        // Un borrador rehidratado con la base tipada (cualquiera de las tres) también vale.
        EerSex.entries.forEach { sex -> assertTrue(sex.name, sexChecks(typedSex(sex)).none { it.isBlocking }) }

        // Volver a «No lo sé» desde una respuesta hormonal vuelve a bloquear.
        assertTrue(sexChecks(SetupWizardDraft().chooseSex("hormones_mixed").chooseSex("unknown")).single().isBlocking)
    }

    @Test
    fun anOldConfirmedUnknownAnswerNoLongerOpensTheGenderStep() {
        // Un borrador antiguo confirmado con «No lo sé» (válido entonces con nutrición a mano) ahora debe contestar la
        // consulta hormonal: el registro de la respuesta no habilita el paso por sí solo.
        val old = SetupWizardDraft()
            .chooseSex("unknown")
            .recordStepAnswer(SetupStepId.EQUATION_SEX, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)

        assertTrue(sexChecks(old).single().isBlocking)
        assertFalse(SetupWizardState(old.copy(stepProgress = old.stepProgress.at(SetupStepId.EQUATION_SEX, old.stepContext()))).canConfirmStep)
    }

    // ─── Serialización de los campos nuevos ──────────────────────────────────

    @Test
    fun newDraftContractSurvivesSerializationRoundTrip() {
        val original = SetupWizardDraft(
            draftId = "setup-wizard:full",
            commitId = "commit-1",
            trainingOptions = TrainingOptions(orderPriorities = mapOf("Pecho" to 2, "Dorsales" to 1)),
            heightUnit = "ft",
            bodyFatPercent = 15.5,
            bodyFatSource = SetupBodyFatSource.VISUAL_ESTIMATE,
            physiqueModel = "female",
            physiqueSliderPosition = 6f,
            weightTrend = "falling",
            previousMaximumWeightKg = 88.0,
            historicalWeighIns = listOf(SetupWeighIn("w-1", "2026-09-01", 81.5)),
            currentWeightMeasuredAtEpochMs = 111L,
            bodyFatCapturedAtEpochMs = 222L,
            stepSelections = mapOf(SetupStepId.GOAL to listOf("strength"), SetupStepId.WEEKDAYS to listOf("1", "3")),
            inputTexts = mapOf("AGE" to "19", "WEIGHT" to "70,5"),
            stepEditors = mapOf(
                SetupStepId.INVENTORY_PLATES to SetupStepEditorState(
                    editing = true,
                    itemIndex = 1,
                    phase = 2,
                    values = mapOf("weightKg" to "20"),
                ),
            ),
            reviewReturnStep = SetupStepId.NUTRITION_RESULT,
            // Entreno v2
            trainingPlaces = setOf(TrainingPlace.GYM, TrainingPlace.PUBLIC),
            goalProfile = TrainingGoalProfile.POWERBUILDING,
            weekStartDay = 2,
            freshestDay = 3,
            dayPlaces = mapOf(1 to TrainingPlace.GYM, 4 to TrainingPlace.PUBLIC),
            capabilities = mapOf(CapabilitySkill.PULL_UP to CapabilityLevel.SOME),
            liftMarks = mapOf(LiftMark.SQUAT to 140.0, LiftMark.OVERHEAD_PRESS to 60.0),
            marksUnit = "lb",
            weekLayoutOverrides = mapOf("session-a" to 5),
            adaptedSplitId = "split-1",
            planVariantSeed = 7,
        )

        val restored = json.decodeFromString<SetupWizardDraft>(json.encodeToString(original))

        assertEquals(original, restored)
        assertEquals("19", restored.inputTexts["AGE"])
        assertEquals(listOf("strength"), restored.stepSelections[SetupStepId.GOAL])
        assertEquals(SetupWeighIn("w-1", "2026-09-01", 81.5), restored.historicalWeighIns.single())
        assertEquals(111L, restored.currentWeightMeasuredAtEpochMs)
        // La fila editorial abierta sobrevive a la rehidratación (defaults compat).
        val editor = restored.stepEditors[SetupStepId.INVENTORY_PLATES]
        assertTrue(editor?.editing == true)
        assertEquals(SetupStepEditorState(editing = true, itemIndex = 1, phase = 2, values = mapOf("weightKg" to "20")), editor)
        // La intención de volver a la revisión también viaja en el borrador.
        assertEquals(SetupStepId.NUTRITION_RESULT, restored.reviewReturnStep)
        assertEquals(setOf(TrainingPlace.GYM, TrainingPlace.PUBLIC), restored.trainingPlaces)
        assertEquals(TrainingGoalProfile.POWERBUILDING, restored.goalProfile)
        assertEquals(mapOf(LiftMark.SQUAT to 140.0, LiftMark.OVERHEAD_PRESS to 60.0), restored.liftMarks)
        assertEquals(mapOf(1 to TrainingPlace.GYM, 4 to TrainingPlace.PUBLIC), restored.dayPlaces)
        assertEquals(mapOf(CapabilitySkill.PULL_UP to CapabilityLevel.SOME), restored.capabilities)
        assertEquals(7, restored.planVariantSeed)
        // Defaults compat: un payload sin los campos nuevos los rehidrata en null/vacío.
        val legacy = json.decodeFromString<SetupWizardDraft>(json.encodeToString(SetupWizardDraft()))
        assertNull(legacy.reviewReturnStep)
        assertTrue(legacy.stepEditors.isEmpty())
        assertTrue(legacy.trainingPlaces.isEmpty())
        assertNull(legacy.goalProfile)
        assertTrue(legacy.liftMarks.isEmpty())
        assertEquals("kg", legacy.marksUnit)
        assertEquals(0, legacy.planVariantSeed)
        // Un JSON viejo sin ninguno de los campos nuevos tampoco rompe la lectura.
        val oldJson = """{"draftId":"setup-wizard:full","commitId":"commit-1","name":"Ana"}"""
        val old = json.decodeFromString<SetupWizardDraft>(oldJson)
        assertEquals("Ana", old.name)
        assertTrue(old.trainingPlaces.isEmpty())
        assertNull(old.freshestDay)
    }
}
