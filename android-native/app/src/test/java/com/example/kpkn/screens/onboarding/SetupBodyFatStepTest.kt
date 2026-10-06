package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.nutrition.bodyFatForSliderPos
import com.example.kpkn.domain.nutrition.physiqueSliderPositionForBodyFat
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupValueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paso de grasa corporal con regla vertical: es OBLIGATORIO y la posición de arranque (≈25 %) no es una
 * respuesta. Continuar queda bloqueado hasta que se mueve la regla (arrastrar o tocar un punto), salvo que Ajustes
 * ya tenga una grasa declarada. Ya no hay «omitir» ni campo de medición: los borradores antiguos que traen una
 * omisión o una medición se siguen leyendo, pero la omisión no valida. Reglas puras sobre el borrador, sin ViewModel
 * (los recorridos con el ViewModel viven en [SetupWizardBodyFatViewModelTest]).
 */
class SetupBodyFatStepTest {

    private fun blocking(draft: SetupWizardDraft) =
        SetupWizardValidation.validateStep(draft, SetupStepId.BODY_FAT).filter { it.isBlocking }

    // ─── Sin mover la regla: bloqueado ───────────────────────────────────────

    @Test
    fun aFreshDraftIsPendingAndBlocksContinuarWithAnExplicitMessage() {
        val draft = SetupWizardDraft()

        assertEquals(SetupBodyFatState.PENDING, draft.bodyFatState())
        assertFalse(draft.bodyFatState().isDeclared)
        val checks = blocking(draft)
        assertEquals(1, checks.size)
        assertEquals("bodyFat", checks.single().key)
        assertEquals(BODY_FAT_PENDING_MESSAGE, checks.single().message)
        assertEquals("Mueve la regla hasta tu porcentaje de grasa corporal.", BODY_FAT_PENDING_MESSAGE)

        // El CTA del Host sigue esa misma validación: Continuar no está operable.
        val onTheStep = SetupWizardDraft(stepProgress = SetupStepProgress(currentStepId = SetupStepId.BODY_FAT))
        assertFalse(SetupWizardState(onTheStep).canConfirmStep)
    }

    @Test
    fun theStartingRulerPositionAloneIsNotAnAnswer() {
        // Mover la posición guardada de la figura sin tocar la regla no declara un dato.
        val draft = SetupWizardDraft(physiqueSliderPosition = 6f, physiqueModel = "female")

        assertEquals(SetupBodyFatState.PENDING, draft.bodyFatState())
        assertTrue(blocking(draft).isNotEmpty())
        // La regla se pinta en la referencia de la figura (posición 6 = 35 %), pero sin guardar ningún porcentaje.
        assertEquals(35.0, draft.bodyFatRulerPercent(), 1e-6)
        assertNull(draft.bodyFatPercent)
        assertNull(draft.bodyFatSource)
    }

    @Test
    fun theDefaultRulerStartsAtTheSoftSilhouetteOfTwentyFivePercent() {
        val draft = SetupWizardDraft()

        assertEquals("male", draft.physiqueModel)
        assertEquals(4f, draft.physiqueSliderPosition, 0f)
        assertEquals(25.0, draft.bodyFatRulerPercent(), 1e-6)
        assertEquals(bodyFatForSliderPos(4f), draft.bodyFatRulerPercent(), 0.0)
    }

    // ─── Mover la regla declara ──────────────────────────────────────────────

    @Test
    fun movingTheRulerDeclaresAVisualEstimateAndUnblocksTheStep() {
        val draft = SetupWizardDraft().withBodyFatRulerValue(22, nowEpochMs = 7L)

        assertEquals(SetupBodyFatSource.VISUAL_ESTIMATE, draft.bodyFatSource)
        assertEquals(22.0, draft.bodyFatPercent!!, 0.0)
        assertEquals(SetupBodyFatState.VISUAL, draft.bodyFatState())
        assertTrue(draft.bodyFatState().isDeclared)
        assertTrue(blocking(draft).isEmpty())
        assertEquals(7L, draft.bodyFatCapturedAtEpochMs)
        // El texto crudo es el entero, sin decimales.
        assertEquals("22", draft.inputTexts["BODY_FAT"])
        assertEquals(22.0, draft.bodyFatRulerPercent(), 0.0)
        // El check queda habilitado sin pulsar nada más.
        val onTheStep = draft.copy(stepProgress = SetupStepProgress(currentStepId = SetupStepId.BODY_FAT))
        assertTrue(SetupWizardState(onTheStep).canConfirmStep)
    }

    @Test
    fun theFirstTouchDeclaresEvenTheValueTheRulerStartedOn() {
        // Tocar el 25 % de arranque es una respuesta: el valor no cambia, pero ahora sí está declarado.
        val started = SetupWizardDraft()
        assertEquals(25.0, started.bodyFatRulerPercent(), 0.0)
        assertTrue(blocking(started).isNotEmpty())

        val touched = started.withBodyFatRulerValue(25)
        assertEquals(25.0, touched.bodyFatPercent!!, 0.0)
        assertEquals(SetupBodyFatState.VISUAL, touched.bodyFatState())
        assertTrue(blocking(touched).isEmpty())
    }

    @Test
    fun theFigurePositionIsDerivedFromThePercentageTheRulerDeclared() {
        for (percent in 5..50) {
            val draft = SetupWizardDraft().withBodyFatRulerValue(percent)
            assertEquals(
                "$percent %",
                physiqueSliderPositionForBodyFat(percent.toDouble()),
                draft.physiqueSliderPosition,
                0f,
            )
        }
        assertEquals(1f, SetupWizardDraft().withBodyFatRulerValue(5).physiqueSliderPosition, 0f)
        assertEquals(4f, SetupWizardDraft().withBodyFatRulerValue(25).physiqueSliderPosition, 0f)
        assertEquals(7f, SetupWizardDraft().withBodyFatRulerValue(50).physiqueSliderPosition, 0f)
    }

    @Test
    fun movingTheRulerAgainKeepsTheSourceAndOnlyRefreshesTheDateWhenTheValueChanges() {
        val first = SetupWizardDraft().withBodyFatRulerValue(22, nowEpochMs = 100L)

        // Mismo valor: ni fecha nueva ni otra escritura.
        val same = first.withBodyFatRulerValue(22, nowEpochMs = 999L)
        assertEquals(100L, same.bodyFatCapturedAtEpochMs)
        assertEquals(first, same)

        // Otro valor: sigue siendo la estimación visual, con la fecha del nuevo gesto.
        val moved = first.withBodyFatRulerValue(31, nowEpochMs = 200L)
        assertEquals(SetupBodyFatSource.VISUAL_ESTIMATE, moved.bodyFatSource)
        assertEquals(31.0, moved.bodyFatPercent!!, 0.0)
        assertEquals(200L, moved.bodyFatCapturedAtEpochMs)
        assertEquals("31", moved.inputTexts["BODY_FAT"])
        assertEquals(physiqueSliderPositionForBodyFat(31.0), moved.physiqueSliderPosition, 0f)
    }

    @Test
    fun theFigureNeverTouchesTheCalculationSexNorThePercentage() {
        val declared = SetupWizardDraft().withBodyFatRulerValue(18)

        val female = declared.copy(physiqueModel = "female")

        assertEquals("female", female.physiqueModel)
        assertEquals(declared.bodyFatPercent, female.bodyFatPercent)
        assertEquals(declared.bodyFatSource, female.bodyFatSource)
        assertEquals(declared.nutritionDraft, female.nutritionDraft)
        assertEquals(SetupBodyFatState.VISUAL, female.bodyFatState())
    }

    // ─── Borradores antiguos ─────────────────────────────────────────────────

    @Test
    fun aLegacyMeasurementStillValidatesAndTheRulerStartsOnIt() {
        val draft = SetupWizardDraft()
            .withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.MEASURED.name, nowEpochMs = 1L)
            .withStepText(SetupStepId.BODY_FAT, "17,5", nowEpochMs = 2L)

        assertEquals(SetupBodyFatState.MEASURED, draft.bodyFatState())
        assertTrue(draft.bodyFatState().isDeclared)
        assertTrue(blocking(draft).isEmpty())
        assertEquals(17.5, draft.bodyFatPercent!!, 0.001)
        // La regla arranca en el dato real (con su decimal), no en un redondeo.
        assertEquals(17.5, draft.bodyFatRulerPercent(), 0.001)
    }

    @Test
    fun aLegacySkippedDraftNoLongerValidatesAndAsksToDeclareAPercentage() {
        val skipped = SetupWizardDraft()
            .withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.UNKNOWN.name, nowEpochMs = 1L)

        assertEquals(SetupBodyFatState.SKIPPED, skipped.bodyFatState())
        assertFalse(skipped.bodyFatState().isDeclared)
        assertNull(skipped.bodyFatPercent)
        val checks = blocking(skipped)
        assertEquals(1, checks.size)
        assertEquals("bodyFat", checks.single().key)
        assertEquals(BODY_FAT_PENDING_MESSAGE, checks.single().message)
        // Aun registrada como respuesta de un alta anterior, la omisión no sirve.
        val answered = skipped.copy(
            stepProgress = skipped.stepProgress.recordAnswer(
                SetupStepId.BODY_FAT,
                SetupAnswerProvenance.USER_DECLARED,
                SetupValueState.ABSENT,
            ),
        )
        assertTrue(blocking(answered).isNotEmpty())

        // Moviendo la regla se declara de nuevo y la omisión desaparece.
        val declared = skipped.withBodyFatRulerValue(18)
        assertEquals(SetupBodyFatSource.VISUAL_ESTIMATE, declared.bodyFatSource)
        assertEquals(18.0, declared.bodyFatPercent!!, 0.0)
        assertEquals(SetupBodyFatState.VISUAL, declared.bodyFatState())
        assertTrue(blocking(declared).isEmpty())
    }

    @Test
    fun theStepIsMandatoryInTheCatalog() {
        val definition = requireNotNull(SetupStepDefinitions.of(SetupStepId.BODY_FAT))

        assertFalse("la grasa corporal ya no se puede omitir", definition.allowSkip)
        assertNull("sin subtítulo: la figura y la regla se explican solas", definition.subtitle)
    }

    @Test
    fun everyDeclaredStateCountsAsAPercentageAndTheOthersDoNot() {
        assertTrue(SetupBodyFatState.VISUAL.isDeclared)
        assertTrue(SetupBodyFatState.MEASURED.isDeclared)
        assertTrue(SetupBodyFatState.ON_FILE.isDeclared)
        assertFalse(SetupBodyFatState.PENDING.isDeclared)
        assertFalse(SetupBodyFatState.SKIPPED.isDeclared)
    }

    // ─── Quien vuelve: dato previo en Ajustes ────────────────────────────────

    @Test
    fun aBodyFatAlreadyInSettingsDoesNotBlockReturningUsersAndTheRulerStartsOnIt() {
        val draft = SetupWizardDraft(importedBodyFatPercent = 21.0)

        assertEquals(SetupBodyFatState.ON_FILE, draft.bodyFatState())
        assertTrue("cuenta como declarado", draft.bodyFatState().isDeclared)
        assertTrue(blocking(draft).isEmpty())
        assertEquals(21.0, draft.bodyFatRulerPercent(), 0.0)
        // No es una respuesta de este alta: nada declarado ni fuente fabricada.
        assertNull(draft.bodyFatSource)
        assertNull(draft.bodyFatPercent)
    }

    @Test
    fun aDecimalFromSettingsKeepsItsDecimalInTheRulerReading() {
        assertEquals(18.7, SetupWizardDraft(importedBodyFatPercent = 18.7).bodyFatRulerPercent(), 0.0)
    }

    @Test
    fun anImplausiblePreviousBodyFatStillAsksToMoveTheRuler() {
        for (previous in listOf(0.0, 2.9, 60.1, 80.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val draft = SetupWizardDraft(importedBodyFatPercent = previous)
            assertEquals("previo=$previous", SetupBodyFatState.PENDING, draft.bodyFatState())
            assertTrue("previo=$previous", blocking(draft).isNotEmpty())
            // Un dato que no se cree no se pinta: la regla arranca en la figura de arranque.
            assertEquals("previo=$previous", 25.0, draft.bodyFatRulerPercent(), 1e-6)
        }
        // Los extremos creíbles (3 % y 60 %) sí cuentan como dato previo.
        for (previous in listOf(3.0, 60.0)) {
            val draft = SetupWizardDraft(importedBodyFatPercent = previous)
            assertEquals(SetupBodyFatState.ON_FILE, draft.bodyFatState())
            assertEquals(previous, draft.bodyFatRulerPercent(), 0.0)
        }
    }

    @Test
    fun aReturningUserCanReplaceThePreviousValueWithTheRuler() {
        val base = SetupWizardDraft(importedBodyFatPercent = 21.0)

        val replaced = base.withBodyFatRulerValue(18, nowEpochMs = 2L)

        assertEquals(SetupBodyFatState.VISUAL, replaced.bodyFatState())
        assertEquals(18.0, replaced.bodyFatPercent!!, 0.001)
        assertEquals(18.0, replaced.bodyFatRulerPercent(), 0.0)
        // El dato previo sigue siendo el de Ajustes; la respuesta nueva no lo pisa.
        assertEquals(21.0, replaced.importedBodyFatPercent!!, 0.001)
    }

    @Test
    fun theDeclaredPercentageWinsOverTheSettingsValueInTheRuler() {
        val draft = SetupWizardDraft(importedBodyFatPercent = 21.0).withBodyFatRulerValue(30)

        assertEquals(30.0, draft.bodyFatRulerPercent(), 0.0)
        assertNotEquals(21.0, draft.bodyFatRulerPercent(), 0.0)
    }

    // ─── Lo que ya bloqueaba sigue bloqueando ────────────────────────────────

    @Test
    fun aChosenSourceWithoutPercentageStillBlocksWithItsOwnMessage() {
        for (source in listOf(SetupBodyFatSource.MEASURED, SetupBodyFatSource.VISUAL_ESTIMATE)) {
            val draft = SetupWizardDraft(bodyFatSource = source)
            assertEquals("fuente=$source", SetupBodyFatState.PENDING, draft.bodyFatState())
            assertEquals("Indica tu grasa corporal", blocking(draft).single().message)
        }
    }

    @Test
    fun anOutOfRangeOrUnreadablePercentageStillBlocks() {
        val tooLow = SetupWizardDraft(bodyFatSource = SetupBodyFatSource.MEASURED, bodyFatPercent = 2.0)
        assertEquals("Usa un porcentaje entre 3 y 60 %", blocking(tooLow).single().message)

        val unreadable = SetupWizardDraft(
            bodyFatSource = SetupBodyFatSource.MEASURED,
            inputTexts = mapOf("BODY_FAT" to "abc"),
        )
        assertEquals("Escribe un porcentaje válido", blocking(unreadable).single().message)
    }

    @Test
    fun aValueOutsideTheRulerButInsideTheCatalogRangeStillValidates() {
        // La regla va de 5 % a 50 %, pero un dato antiguo de 3 % o de 55 % es válido (3–60 %) y se lee tal cual.
        for (legacy in listOf(3.0, 4.0, 55.0, 60.0)) {
            val draft = SetupWizardDraft(bodyFatSource = SetupBodyFatSource.VISUAL_ESTIMATE, bodyFatPercent = legacy)
            assertTrue("dato=$legacy", blocking(draft).isEmpty())
            assertEquals("dato=$legacy", legacy, draft.bodyFatRulerPercent(), 0.0)
        }
    }

    // ─── Texto corto ─────────────────────────────────────────────────────────

    @Test
    fun anOldFigureValueKeepsFullPrecisionButWritesAShortText() {
        // Lo que emitía la figura antigua al mover el slider: un Double crudo, no un porcentaje redondo.
        val raw = 33.184518814086914
        val draft = SetupWizardDraft()
            .withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name, nowEpochMs = 1L)
            .withStepNumber(SetupStepId.BODY_FAT, raw, nowEpochMs = 2L)

        // `inputTexts` lleva un decimal como máximo, con punto.
        assertEquals("33.2", draft.inputTexts["BODY_FAT"])
        // El valor tipado conserva toda su precisión: solo se acorta el texto.
        assertEquals(raw, draft.bodyFatPercent!!, 0.0)
        // Un entero no arrastra decimales y un decimal ya corto queda tal cual.
        assertEquals("22", draft.withStepNumber(SetupStepId.BODY_FAT, 22.0, nowEpochMs = 3L).inputTexts["BODY_FAT"])
        assertEquals("18.5", draft.withStepNumber(SetupStepId.BODY_FAT, 18.5, nowEpochMs = 3L).inputTexts["BODY_FAT"])
        // El texto corto sigue siendo un porcentaje válido para el parser y la validación.
        assertTrue(blocking(draft).isEmpty())
    }
}
