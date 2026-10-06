package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.screens.onboarding.design.KpknModule
import com.example.kpkn.screens.onboarding.design.OverlayStageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `los hitos no son paginas`() {
        assertTrue(route.any(SetupStepGraph::isMilestone))
        assertTrue(pages.none(SetupStepGraph::isMilestone))
    }

    @Test
    fun `durante un hito la pagina sigue siendo la ultima pregunta del bloque`() {
        assertEquals(SetupStepId.BODY_FAT, wizardCurrentPage(SetupStepId.MILESTONE_BASICS, route))
        // Una pregunta normal y las páginas fusionadas se resuelven como siempre.
        assertEquals(SetupStepId.EXPERIENCE, wizardCurrentPage(SetupStepId.EXPERIENCE, route))
        assertEquals(SetupStepId.NAME, wizardCurrentPage(SetupStepId.AGE, route))
        // Cada hito cae en una página real de la presentación.
        route.filter(SetupStepGraph::isMilestone).forEach { milestone ->
            val page = wizardCurrentPage(milestone, route)
            assertTrue("$milestone → $page", page in pages)
            assertEquals(SetupStepGraph.blockOf(milestone), SetupStepGraph.blockOf(page))
        }
    }

    @Test
    fun `la etiqueta de la seccion cuenta las preguntas del bloque`() {
        val questions = pages.filter { SetupStepGraph.blockOf(it) == SetupWizardBlock.BASICS }
        assertEquals("Paso 1 de ${questions.size} · Datos básicos", wizardEyebrow(pages.first(), pages))
        assertEquals("Paso ${questions.size} de ${questions.size} · Datos básicos", wizardEyebrow(questions.last(), pages))
    }

    @Test
    fun `la revision tiene etiqueta propia`() {
        assertEquals("Último paso", wizardEyebrow(SetupStepId.REVIEW_ACTIVATE, pages))
    }

    @Test
    fun `la cabecera dice el bloque y cuanto llevas sin repetir la pregunta`() {
        val total = pages.count { SetupStepGraph.blockOf(it) == SetupWizardBlock.BASICS }
        assertEquals("Datos básicos · 1/$total", wizardHeaderLabel(pages.first(), pages))
        assertEquals("Revisión", wizardHeaderLabel(SetupStepId.REVIEW_ACTIVATE, pages))
    }

    @Test
    fun `el progreso por bloque va de 0 a 1 y un bloque cerrado cuenta completo`() {
        val blocks = pages.map(SetupStepGraph::blockOf).distinct()
        val atStart = wizardBlockProgress(pages, confirmedCount = 0)
        assertEquals(blocks, atStart.map { it.first })
        assertTrue(atStart.all { it.second == 0f })

        // Con la primera pregunta de Entreno por delante, todo Datos básicos está confirmado.
        val firstTraining = pages.indexOfFirst { SetupStepGraph.blockOf(it) == SetupWizardBlock.TRAINING }
        val atTraining = wizardBlockProgress(pages, confirmedCount = firstTraining)
        assertEquals(1f, atTraining.first { it.first == SetupWizardBlock.BASICS }.second)
        assertEquals(0f, atTraining.first { it.first == SetupWizardBlock.TRAINING }.second)

        val atEnd = wizardBlockProgress(pages, confirmedCount = pages.size)
        assertTrue(atEnd.all { it.second == 1f })
    }

    @Test
    fun `mientras el overlay del hito esta abierto la ultima pregunta cuenta como confirmada`() {
        val last = pages.indexOf(SetupStepId.BODY_FAT)
        val onQuestion = wizardBlockProgress(pages, confirmedCount = last)
        val onMilestone = wizardBlockProgress(pages, confirmedCount = last + 1)
        assertTrue(onQuestion.first { it.first == SetupWizardBlock.BASICS }.second < 1f)
        assertEquals(1f, onMilestone.first { it.first == SetupWizardBlock.BASICS }.second)
    }

    @Test
    fun `las paginas fusionadas tienen su propio texto`() {
        assertEquals("Empecemos por ti", wizardPageCopy(SetupStepId.NAME).title)
        assertEquals("¿Cuánto mides y pesas?", wizardPageCopy(SetupStepId.HEIGHT).title)
    }

    @Test
    fun `una pregunta normal usa el titulo y el subtitulo del catalogo`() {
        val copy = wizardPageCopy(SetupStepId.EXPERIENCE)
        assertTrue(copy.title.startsWith("¿"))
    }

    // ─── Overlay del hito: animación por bloque y fila de etapas ──────────────────────────────────

    @Test
    fun `cada bloque con hito tiene su animacion y la revision no`() {
        assertEquals(KpknModule.BASICOS, SetupWizardBlock.BASICS.toKpknModule())
        assertEquals(KpknModule.ENTRENO, SetupWizardBlock.TRAINING.toKpknModule())
        assertEquals(KpknModule.NUTRICION, SetupWizardBlock.NUTRITION.toKpknModule())
        assertEquals(KpknModule.RINGS, SetupWizardBlock.RINGS.toKpknModule())
        assertNull(SetupWizardBlock.REVIEW.toKpknModule())
        // Todo hito de la ruta completa tiene animación.
        route.filter(SetupStepGraph::isMilestone).forEach { milestone ->
            assertTrue("$milestone sin animación", SetupStepGraph.blockOf(milestone).toKpknModule() != null)
        }
    }

    @Test
    fun `las etapas del primer hito dejan basicos celebrado y entreno como siguiente`() {
        val stages = milestoneStages(SetupStepId.MILESTONE_BASICS, route, completed = emptySet())
        assertEquals(listOf("Básicos", "Entreno", "Nutrición", "Rings", "Revisión"), stages.map { it.label })
        assertEquals(
            listOf(
                OverlayStageState.JUST_DONE,
                OverlayStageState.NEXT,
                OverlayStageState.PENDING,
                OverlayStageState.PENDING,
                OverlayStageState.PENDING,
            ),
            stages.map { it.state },
        )
    }

    @Test
    fun `las etapas de un hito intermedio marcan lo anterior como completo`() {
        val stages = milestoneStages(
            SetupStepId.MILESTONE_NUTRITION,
            route,
            completed = setOf(SetupWizardBlock.BASICS, SetupWizardBlock.TRAINING),
        )
        assertEquals(
            listOf(
                OverlayStageState.DONE,
                OverlayStageState.DONE,
                OverlayStageState.JUST_DONE,
                OverlayStageState.NEXT,
                OverlayStageState.PENDING,
            ),
            stages.map { it.state },
        )
    }

    @Test
    fun `tras el ultimo hito el siguiente es la revision`() {
        val stages = milestoneStages(
            SetupStepId.MILESTONE_RINGS,
            route,
            completed = setOf(SetupWizardBlock.BASICS, SetupWizardBlock.TRAINING, SetupWizardBlock.NUTRITION),
        )
        assertEquals(OverlayStageState.JUST_DONE, stages[3].state)
        assertEquals(OverlayStageState.NEXT, stages.last().state)
        assertEquals("Revisión", stages.last().label)
    }

    @Test
    fun `la ruta de solo entreno no nombra nutricion ni rings en las etapas`() {
        val trainingOnly = SetupStepGraph.stepIds(SetupWizardDraft(draftScope = "training_only", includeNutrition = false).stepContext())
        val stages = milestoneStages(SetupStepId.MILESTONE_BASICS, trainingOnly, completed = emptySet())
        assertEquals(listOf("Básicos", "Entreno", "Revisión"), stages.map { it.label })
        assertTrue(stages.none { it.label == "Nutrición" || it.label == "Rings" })
    }

    @Test
    fun `la pantalla de arranque no tiene nada completado`() {
        val stages = introStages(route)
        assertEquals(milestoneBlocks(route).size, stages.size)
        assertTrue(stages.none { it.state == OverlayStageState.DONE || it.state == OverlayStageState.JUST_DONE })
        assertEquals(OverlayStageState.NEXT, stages.first().state)
        assertTrue(stages.drop(1).all { it.state == OverlayStageState.PENDING })
    }
}
