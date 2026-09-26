package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupValueState
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
 * - La validación numérica prioriza el crudo, exige enteros en edad/tiempo y
 *   usa los rangos del catálogo `SetupStepDefinitions`.
 * - BODY_FAT sin percentil no vale ni respondido; «No lo sé» limpia número,
 *   fecha y texto. AUTO sin confirmación no se salta con un registro previo.
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
            .withStepText(SetupStepId.SESSION_TIME, "abc", nowEpochMs = 1L)
            .withStepNumber(SetupStepId.SESSION_TIME, null, nowEpochMs = 2L)

        // El texto inválido no se silencia con el retiro del dato.
        assertEquals("abc", draft.inputTexts["SESSION_TIME"])
        val checks = SetupWizardValidation.validateStep(draft, SetupStepId.SESSION_TIME)
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
    fun fractionalAgeAndSessionTimeAreRejected() {
        val age = SetupWizardDraft().withStepText(SetupStepId.AGE, "19.5", nowEpochMs = 1L)
        assertTrue(
            SetupWizardValidation.validateStep(age, SetupStepId.AGE)
                .any { it.state == SetupValueState.INVALID },
        )

        val minutes = SetupWizardDraft().withStepText(SetupStepId.SESSION_TIME, "45.5", nowEpochMs = 1L)
        assertTrue(
            SetupWizardValidation.validateStep(minutes, SetupStepId.SESSION_TIME)
                .any { it.state == SetupValueState.INVALID },
        )
    }

    // ─── BODY_FAT: percentil obligatorio con fuente real; UNKNOWN limpia ─────

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
        // Avance bloqueado solo si hay fuente real sin percentil; UNKNOWN es omisión válida.
        assertTrue(SetupWizardValidation.validateStep(draft, SetupStepId.BODY_FAT).none { it.isBlocking })
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
    fun autoWithoutConfirmationBlocksDespiteRecordedAnswer() {
        val auto = TrainingOptions(autoregulationMode = AutoregulationMode.AUTO, automaticConfirmed = false)
        val draft = SetupWizardDraft(trainingOptions = auto).let { base ->
            base.copy(
                stepProgress = base.stepProgress.recordAnswer(
                    SetupStepId.AUTOREGULATION_CONFIRM,
                    SetupAnswerProvenance.USER_DECLARED,
                    SetupValueState.DECLARED,
                ),
            )
        }
        assertTrue(
            SetupWizardValidation.validateStep(draft, SetupStepId.AUTOREGULATION_CONFIRM).any { it.isBlocking },
        )

        val confirmed = draft.copy(trainingOptions = auto.copy(automaticConfirmed = true))
        assertTrue(
            SetupWizardValidation.validateStep(confirmed, SetupStepId.AUTOREGULATION_CONFIRM).none { it.isBlocking },
        )
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

    // ─── Inventario: contrato real de TrainingOptions, solo sus razones ──────

    @Test
    fun inventoryDeclarationMustSatisfyTrainingOptionsContract() {
        // Disco declarado sin cantidad por lado: el contrato real lo rechaza.
        val dishonest = SetupWizardDraft(
            trainingOptions = TrainingOptions(
                inventory = EquipmentInventory(plates = listOf(PlateStock(20.0, null))),
            ),
        )
        assertTrue(
            SetupWizardValidation.validateStep(dishonest, SetupStepId.INVENTORY_PLATES)
                .any { it.state == SetupValueState.INVALID },
        )
    }

    @Test
    fun noneVsUnsetInventoryIsExplicit() {
        // Sin declaración y sin elección explícita: desconocido NO cuenta como
        // material disponible ilimitado → bloquea (ABSENT).
        val unset = SetupWizardValidation.validateStep(SetupWizardDraft(), SetupStepId.INVENTORY_PLATES)
        assertTrue(unset.any { it.state == SetupValueState.ABSENT })

        // Elección explícita «none» del paso: válido y no bloquea.
        val explicitNone = SetupWizardDraft(
            stepSelections = mapOf(SetupStepId.INVENTORY_PLATES to listOf("none")),
        )
        assertTrue(
            SetupWizardValidation.validateStep(explicitNone, SetupStepId.INVENTORY_PLATES).none { it.isBlocking },
        )

        // Dato propio finito y explícito: también válido. La barra acompaña a
        // los discos porque una declaración nueva con discos y sin peso de
        // barra es inválida de verdad (el motor nunca asume 20 kg).
        val declared = SetupWizardDraft(
            trainingOptions = TrainingOptions(
                inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(20.0, 2))),
            ),
        )
        assertTrue(
            SetupWizardValidation.validateStep(declared, SetupStepId.INVENTORY_PLATES).none { it.isBlocking },
        )
    }

    @Test
    fun openRowEditorBlocksStepUntilClosed() {
        val editing = SetupWizardDraft(
            stepSelections = mapOf(SetupStepId.INVENTORY_PLATES to listOf("none")),
            stepEditors = mapOf(SetupStepId.INVENTORY_PLATES to SetupStepEditorState(editing = true, itemIndex = 1)),
        )
        val checks = SetupWizardValidation.validateStep(editing, SetupStepId.INVENTORY_PLATES)
        assertTrue(checks.any { it.isBlocking })

        val closed = editing.copy(stepEditors = mapOf(SetupStepId.INVENTORY_PLATES to SetupStepEditorState(editing = false)))
        assertTrue(
            SetupWizardValidation.validateStep(closed, SetupStepId.INVENTORY_PLATES).none { it.isBlocking },
        )
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

    // ─── Entorno: categorías, no inventario de kilos ─────────────────────────

    @Test
    fun homeEnvironmentAsksAllInventoryGroups() {
        assertTrue(SetupWizardDraft(trainingEnvironment = "home").asksEquipmentCategories())
        assertTrue(SetupWizardDraft(trainingEnvironment = "gym").asksEquipmentCategories())
        assertTrue(SetupWizardDraft(trainingEnvironment = "home").stepContext().asksAvailability)
        assertTrue(SetupWizardDraft(trainingEnvironment = "gym").inventoryGroups().isEmpty())
        assertTrue(SetupWizardDraft(trainingEnvironment = "none").stepContext().inventoryGroups.isEmpty())
        assertFalse(SetupWizardDraft(trainingEnvironment = "none").asksEquipmentCategories())
        assertTrue(SetupWizardDraft().stepContext().inventoryGroups.isEmpty())
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
        val home = SetupWizardDraft(trainingEnvironment = "home", equipment = emptySet())
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
    fun environmentChangeKeepsInventoryAndMarksGroupsPending() {
        val gym = SetupWizardDraft(
            trainingEnvironment = "gym",
            equipment = setOf(SetupEquipment.GYM),
            trainingOptions = TrainingOptions(
                inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(20.0, 2))),
            ),
            stepSelections = mapOf(SetupStepId.INVENTORY_PLATES to listOf("small_plates")),
            stepEditors = mapOf(SetupStepId.INVENTORY_PLATES to SetupStepEditorState(editing = true, itemIndex = 0)),
        )

        val home = gym.withStepChoice(SetupStepId.EQUIPMENT, "home", nowEpochMs = 1L)
        assertEquals("home", home.trainingEnvironment)
        // Un SOLO inventario principal: el cambio de entorno NO borra lo declarado
        // ni las filas a medias; solo retira el perfil legacy ajeno.
        assertEquals(gym.trainingOptions.inventory, home.trainingOptions.inventory)
        assertEquals(listOf("small_plates"), home.stepSelections[SetupStepId.INVENTORY_PLATES])
        assertTrue(home.stepEditors.getValue(SetupStepId.INVENTORY_PLATES).editing)
        assertTrue(home.equipment.isEmpty())
        // El alta ya no abre el inventario de kilos: cambiar de entorno no marca esos pasos.
        assertTrue(home.stepProgress.pendingReview.isEmpty())
        assertNull(home.trainingOptions.availability)

        // Re-pulsar el MISMO entorno: conserva todo y no marca nada.
        val same = gym.withStepChoice(SetupStepId.EQUIPMENT, "gym", nowEpochMs = 2L)
        assertEquals(gym.trainingOptions.inventory, same.trainingOptions.inventory)
        assertEquals(setOf(SetupEquipment.GYM), same.equipment)
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
        // Defaults compat: un payload sin los campos nuevos los rehidrata en null/vacío.
        val legacy = json.decodeFromString<SetupWizardDraft>(json.encodeToString(SetupWizardDraft()))
        assertNull(legacy.reviewReturnStep)
        assertTrue(legacy.stepEditors.isEmpty())
    }
}
