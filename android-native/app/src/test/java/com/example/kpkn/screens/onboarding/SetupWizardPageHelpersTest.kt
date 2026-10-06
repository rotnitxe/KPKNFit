package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textos y posiciones de la página larga: son puros y no dependen de Compose. */
class SetupWizardPageHelpersTest {

    private val route = SetupStepGraph.stepIds(SetupStepContext())
    private val pages = wizardPresentationSteps(route)

    @Test
    fun `la edad y el peso se muestran en la pagina del alias y de la altura`() {
        assertEquals(SetupStepId.NAME, wizardPageOf(SetupStepId.AGE))
        assertEquals(SetupStepId.HEIGHT, wizardPageOf(SetupStepId.WEIGHT))
        assertEquals(SetupStepId.BODY_FAT, wizardPageOf(SetupStepId.BODY_FAT))
    }

    @Test
    fun `las paginas fusionadas no repiten edad ni peso`() {
        assertTrue(SetupStepId.AGE !in pages)
        assertTrue(SetupStepId.WEIGHT !in pages)
        assertEquals(SetupStepId.NAME, pages.first())
    }

    @Test
    fun `la etiqueta de la seccion cuenta solo preguntas del bloque`() {
        val questions = pages.filter { SetupStepGraph.blockOf(it) == SetupWizardBlock.BASICS && !SetupStepGraph.isMilestone(it) }
        assertEquals("Paso 1 de ${questions.size} · Datos básicos", wizardEyebrow(pages.first(), pages))
        assertEquals("Paso ${questions.size} de ${questions.size} · Datos básicos", wizardEyebrow(questions.last(), pages))
    }

    @Test
    fun `los hitos y la revision tienen etiqueta propia`() {
        assertEquals("Bloque completado", wizardEyebrow(SetupStepId.MILESTONE_BASICS, pages))
        assertEquals("Último paso", wizardEyebrow(SetupStepId.REVIEW_ACTIVATE, pages))
    }

    @Test
    fun `la cabecera dice el bloque y cuanto llevas sin repetir la pregunta`() {
        val total = pages.count { SetupStepGraph.blockOf(it) == SetupWizardBlock.BASICS && !SetupStepGraph.isMilestone(it) }
        assertEquals("Datos básicos · 1/$total", wizardHeaderLabel(pages.first(), pages))
        assertEquals("Datos básicos · listo", wizardHeaderLabel(SetupStepId.MILESTONE_BASICS, pages))
        assertEquals("Revisión", wizardHeaderLabel(SetupStepId.REVIEW_ACTIVATE, pages))
    }

    @Test
    fun `el progreso por bloque va de 0 a 1 y un bloque cerrado cuenta completo`() {
        val blocks = pages.map(SetupStepGraph::blockOf).distinct()
        val atStart = wizardBlockProgress(pages, currentIndex = 0)
        assertEquals(blocks, atStart.map { it.first })
        assertTrue(atStart.all { it.second == 0f })

        val milestoneIndex = pages.indexOf(SetupStepId.MILESTONE_BASICS)
        val atMilestone = wizardBlockProgress(pages, currentIndex = milestoneIndex)
        assertEquals(1f, atMilestone.first { it.first == SetupWizardBlock.BASICS }.second)
        assertEquals(0f, atMilestone.first { it.first == SetupWizardBlock.TRAINING }.second)

        val atEnd = wizardBlockProgress(pages, currentIndex = pages.lastIndex)
        assertTrue(atEnd.all { it.second in 0f..1f })
    }

    @Test
    fun `las paginas fusionadas y los hitos tienen su propio texto`() {
        assertEquals("Empecemos por ti", wizardPageCopy(SetupStepId.NAME, route).title)
        assertEquals("¿Cuánto mides y pesas?", wizardPageCopy(SetupStepId.HEIGHT, route).title)
        val milestone = wizardPageCopy(SetupStepId.MILESTONE_BASICS, route)
        assertEquals("Datos básicos listos", milestone.title)
        assertTrue(milestone.subtitle!!.startsWith("Faltan"))
    }

    @Test
    fun `una pregunta normal usa el titulo y el subtitulo del catalogo`() {
        val copy = wizardPageCopy(SetupStepId.EXPERIENCE, route)
        assertTrue(copy.title.startsWith("¿"))
    }
}
