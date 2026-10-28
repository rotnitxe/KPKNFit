package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cabecera, antetítulo y fila «Lo que ya sabemos» de la instancia «Nuevo programa» (Registros v4, cr-wz*, CR-F2, CR-F3, CR-D1). */
class NewProgramInstanceCopyTest {

    private val pages = SetupStepGraph.stepIds(SetupStepContext(includeNutrition = false, newProgramInstance = true))

    @Test
    fun laCabeceraCuentaLaRutaEfectivaSinDatosBasicos() {
        val total = pages.size
        assertEquals("Nuevo programa · 1/$total", wizardHeaderLabel(pages.first(), pages, instance = true))
        assertEquals("Nuevo programa · $total/$total", wizardHeaderLabel(SetupStepId.REVIEW_ACTIVATE, pages, instance = true))
        // Con el lápiz abierto, los pasos de Datos básicos no cuentan en n/m.
        val editing = SetupStepGraph.stepIds(
            SetupStepContext(includeNutrition = false, newProgramInstance = true, editsKnownBasics = true),
        )
        assertEquals("Nuevo programa · 1/$total", wizardHeaderLabel(pages.first(), editing, instance = true))
    }

    @Test
    fun elCierreDiceUltimoPasoEntreno() {
        assertEquals("Último paso · Entreno", wizardEyebrow(SetupStepId.REVIEW_ACTIVATE, pages, instance = true))
        assertEquals("Último paso", wizardEyebrow(SetupStepId.REVIEW_ACTIVATE, pages))
    }

    @Test
    fun laBarraDeProgresoLlevaDatosBasicosCompleto() {
        val progress = newProgramBlockProgress(pages, 0)
        assertEquals(SetupWizardBlock.BASICS to 1f, progress.first())
        assertTrue(progress.drop(1).none { it.first == SetupWizardBlock.BASICS })
    }

    @Test
    fun loQueYaSabemosResumeAliasEdadAlturaYPeso() {
        assertEquals("Matías · 34 años · 1,78 m · 80,4 kg", knownBasicsSummary("Matías", 34, 178.0, 80.4))
        assertEquals("Matías · 1,78 m", knownBasicsSummary(" Matías ", null, 178.0, null))
        assertEquals("177,3 lb", knownBasicsSummary(null, null, null, 80.4, weightUnit = "lb"))
    }
}
