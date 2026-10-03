package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1 · Paso de grasa corporal: la figura de arranque (≈25 %) no es una
 * respuesta. Continuar queda bloqueado hasta una acción explícita (mover o
 * tocar la figura, escribir una medición u omitir), salvo que Ajustes ya tenga
 * una grasa declarada. Reglas puras sobre el borrador, sin ViewModel (los
 * recorridos con el ViewModel viven en [SetupWizardBodyFatViewModelTest]).
 */
class SetupBodyFatStepTest {

    private fun blocking(draft: SetupWizardDraft) =
        SetupWizardValidation.validateStep(draft, SetupStepId.BODY_FAT).filter { it.isBlocking }

    // ─── Sin acción explícita: bloqueado ─────────────────────────────────────

    @Test
    fun aFreshDraftIsPendingAndBlocksContinuarWithAnExplicitMessage() {
        val draft = SetupWizardDraft()

        assertEquals(SetupBodyFatState.PENDING, draft.bodyFatState())
        val checks = blocking(draft)
        assertEquals(1, checks.size)
        assertEquals("bodyFat", checks.single().key)
        assertEquals(BODY_FAT_PENDING_MESSAGE, checks.single().message)

        // El CTA del Host sigue esa misma validación: Continuar no está operable.
        val onTheStep = SetupWizardDraft(stepProgress = SetupStepProgress(currentStepId = SetupStepId.BODY_FAT))
        assertFalse(SetupWizardState(onTheStep).canConfirmStep)
    }

    @Test
    fun theStartingFigurePositionAloneIsNotAnAnswer() {
        // Mover la posición guardada del slider sin confirmar nada no declara un dato.
        val draft = SetupWizardDraft(physiqueSliderPosition = 6f, physiqueModel = "female")

        assertEquals(SetupBodyFatState.PENDING, draft.bodyFatState())
        assertTrue(blocking(draft).isNotEmpty())
    }

    // ─── Cada acción explícita desbloquea ────────────────────────────────────

    @Test
    fun confirmingTheFigureUnblocksTheStep() {
        val draft = SetupWizardDraft()
            .withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name, nowEpochMs = 1L)
            .withStepNumber(SetupStepId.BODY_FAT, 22.0, nowEpochMs = 2L)

        assertEquals(SetupBodyFatState.VISUAL, draft.bodyFatState())
        assertTrue(blocking(draft).isEmpty())
        assertEquals(22.0, draft.bodyFatPercent!!, 0.001)
    }

    @Test
    fun writingAMeasurementUnblocksTheStep() {
        val draft = SetupWizardDraft()
            .withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.MEASURED.name, nowEpochMs = 1L)
            .withStepText(SetupStepId.BODY_FAT, "17,5", nowEpochMs = 2L)

        assertEquals(SetupBodyFatState.MEASURED, draft.bodyFatState())
        assertTrue(blocking(draft).isEmpty())
        assertEquals(17.5, draft.bodyFatPercent!!, 0.001)
    }

    @Test
    fun skippingUnblocksTheStepWithoutInventingAPercentage() {
        val draft = SetupWizardDraft()
            .withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.UNKNOWN.name, nowEpochMs = 1L)

        assertEquals(SetupBodyFatState.SKIPPED, draft.bodyFatState())
        assertTrue(blocking(draft).isEmpty())
        assertNull(draft.bodyFatPercent)
    }

    @Test
    fun skippingClearsAnAlreadyChosenFigureMeasurementDateAndRawText() {
        val chosen = SetupWizardDraft()
            .withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name, nowEpochMs = 1L)
            .withStepNumber(SetupStepId.BODY_FAT, 22.0, nowEpochMs = 2L)
        assertEquals(22.0, chosen.bodyFatPercent!!, 0.001)
        assertTrue(chosen.bodyFatCapturedAtEpochMs != null)

        val skipped = chosen.withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.UNKNOWN.name, nowEpochMs = 3L)

        assertEquals(SetupBodyFatState.SKIPPED, skipped.bodyFatState())
        assertNull(skipped.bodyFatPercent)
        assertNull(skipped.bodyFatCapturedAtEpochMs)
        assertFalse("BODY_FAT" in skipped.inputTexts)
        assertTrue(blocking(skipped).isEmpty())
    }

    // ─── Quien vuelve: dato previo en Ajustes ────────────────────────────────

    @Test
    fun aBodyFatAlreadyInSettingsDoesNotBlockReturningUsers() {
        val draft = SetupWizardDraft(importedBodyFatPercent = 21.0)

        assertEquals(SetupBodyFatState.ON_FILE, draft.bodyFatState())
        assertTrue(blocking(draft).isEmpty())
        // No es una respuesta de este alta: nada declarado ni fuente fabricada.
        assertNull(draft.bodyFatSource)
        assertNull(draft.bodyFatPercent)
    }

    @Test
    fun anImplausiblePreviousBodyFatStillAsksForAnExplicitAction() {
        for (previous in listOf(0.0, 2.9, 60.1, 80.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val draft = SetupWizardDraft(importedBodyFatPercent = previous)
            assertEquals("previo=$previous", SetupBodyFatState.PENDING, draft.bodyFatState())
            assertTrue("previo=$previous", blocking(draft).isNotEmpty())
        }
        // Los extremos creíbles (3 % y 60 %) sí cuentan como dato previo.
        for (previous in listOf(3.0, 60.0)) {
            assertEquals(SetupBodyFatState.ON_FILE, SetupWizardDraft(importedBodyFatPercent = previous).bodyFatState())
        }
    }

    @Test
    fun aReturningUserCanStillSkipOrReplaceThePreviousValue() {
        val base = SetupWizardDraft(importedBodyFatPercent = 21.0)

        val skipped = base.withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.UNKNOWN.name, nowEpochMs = 1L)
        assertEquals(SetupBodyFatState.SKIPPED, skipped.bodyFatState())

        val replaced = base
            .withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name, nowEpochMs = 1L)
            .withStepNumber(SetupStepId.BODY_FAT, 18.0, nowEpochMs = 2L)
        assertEquals(SetupBodyFatState.VISUAL, replaced.bodyFatState())
        assertEquals(18.0, replaced.bodyFatPercent!!, 0.001)
        // El dato previo sigue siendo el de Ajustes; la respuesta nueva no lo pisa.
        assertEquals(21.0, replaced.importedBodyFatPercent!!, 0.001)
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

    // ─── Estado visible ──────────────────────────────────────────────────────

    @Test
    fun theStatusLineAlwaysSaysWhetherThereIsDataAndWhichOne() {
        assertEquals("Sin dato todavía", bodyFatStatusText(SetupWizardDraft()))
        assertEquals("Omitido", bodyFatStatusText(SetupWizardDraft(bodyFatSource = SetupBodyFatSource.UNKNOWN)))
        assertEquals(
            "Estimación visual guardada: ≈ 22 %",
            bodyFatStatusText(SetupWizardDraft(bodyFatSource = SetupBodyFatSource.VISUAL_ESTIMATE, bodyFatPercent = 21.6)),
        )
        assertEquals(
            "Medición guardada: 17,5 %",
            bodyFatStatusText(SetupWizardDraft(bodyFatSource = SetupBodyFatSource.MEASURED, bodyFatPercent = 17.5)),
        )
        assertEquals(
            "Dato guardado antes: ≈ 21 %",
            bodyFatStatusText(SetupWizardDraft(importedBodyFatPercent = 21.0)),
        )
    }

    @Test
    fun onlyThePendingSkippedAndOnFileStatesOfferTheFigureValue() {
        assertTrue(SetupBodyFatState.PENDING.offersFigureValue)
        assertTrue(SetupBodyFatState.ON_FILE.offersFigureValue)
        assertTrue(SetupBodyFatState.SKIPPED.offersFigureValue)
        assertFalse(SetupBodyFatState.VISUAL.offersFigureValue)
        assertFalse(SetupBodyFatState.MEASURED.offersFigureValue)
    }

    @Test
    fun theFigureSelectorIsDimmedOnlyWhileTheStepIsSkipped() {
        // Omitido: el «≈ N %» del selector compartido no debe parecer un dato declarado.
        val dimmed = bodyFatSelectorAlpha(SetupBodyFatState.SKIPPED)
        assertTrue("atenuado pero visible", dimmed > 0f && dimmed < 1f)
        listOf(
            SetupBodyFatState.PENDING, SetupBodyFatState.ON_FILE,
            SetupBodyFatState.VISUAL, SetupBodyFatState.MEASURED,
        ).forEach { assertEquals("$it", 1f, bodyFatSelectorAlpha(it), 0f) }

        // Y se recupera al declarar de nuevo.
        val skipped = SetupWizardDraft().withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.UNKNOWN.name, nowEpochMs = 1L)
        assertTrue(bodyFatSelectorAlpha(skipped.bodyFatState()) < 1f)
        val redeclared = skipped
            .withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name, nowEpochMs = 2L)
            .withStepNumber(SetupStepId.BODY_FAT, 20.0, nowEpochMs = 3L)
        assertEquals(1f, bodyFatSelectorAlpha(redeclared.bodyFatState()), 0f)
    }

    @Test
    fun theHintExplainsWhyContinuarIsLockedOnlyWhileThereIsNoData() {
        assertEquals(BODY_FAT_PENDING_MESSAGE, bodyFatStatusHint(SetupBodyFatState.PENDING))
        assertTrue(bodyFatStatusHint(SetupBodyFatState.SKIPPED)!!.contains("No se guardará"))
        assertTrue(bodyFatStatusHint(SetupBodyFatState.ON_FILE)!!.contains("se conserva"))
        assertNull(bodyFatStatusHint(SetupBodyFatState.VISUAL))
        assertNull(bodyFatStatusHint(SetupBodyFatState.MEASURED))
    }
}
