package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WizChatCopyCatalogTest {
    @Test
    fun introIntroducesTheGuideAndMentionsTheNameWhenKnown() {
        val plain = WizChatCopyCatalog.intro()
        val named = WizChatCopyCatalog.intro("Rodrigo")
        assertTrue(plain.startsWith("¡Hola!"))
        assertTrue(plain.contains("guía en KPKN"))
        assertTrue(named.startsWith("¡Hola, Rodrigo!"))
        assertTrue(named.contains("preguntas sencillas"))
    }

    @Test
    fun nameAnswerAcknowledgesWithTheUsersNameAndAPromiseOfWhatComesNext() {
        val ack = WizChatCopyCatalog.acknowledgement(
            stage = WizChatStage.PROFILE,
            variantId = "abc123",
            userName = "Rodrigo",
            questionId = WizChatQuestionId.P_NAME,
            nextQuestionId = WizChatQuestionId.P_GENDER,
        )
        assertTrue(ack.startsWith("Genial, Rodrigo!"))
        assertTrue(ack.length > "Genial, Rodrigo!".length)
        assertFalse(ack.contains("tuyos, Rodrigo"))
        assertFalse(ack.contains("ecuación"))
    }

    @Test
    fun omittedAnswersGetASofterReactionWithoutForcingAHint() {
        val ack = WizChatCopyCatalog.acknowledgement(
            stage = WizChatStage.PROFILE,
            variantId = null,
            userName = null,
            questionId = WizChatQuestionId.P_GENDER,
            nextQuestionId = WizChatQuestionId.P_AGE,
            omitted = true,
        )
        assertTrue(ack.contains("no pasa nada") || ack.contains("Sin problema") || ack.contains("Tranquilo"))
    }

    @Test
    fun stageTransitionsHintAtTheNextTopic() {
        val ack = WizChatCopyCatalog.acknowledgement(
            stage = WizChatStage.PROFILE,
            variantId = null,
            userName = null,
            questionId = WizChatQuestionId.P_EXPERIENCE,
            nextQuestionId = WizChatQuestionId.T_ROUTE,
        )
        assertTrue(ack.contains("entrenamiento"))
    }

    @Test
    fun sameStageMilestonesStillPreviewWhatIsNext() {
        val hint = WizChatCopyCatalog.nextStepHint(WizChatQuestionId.T_PLAN)
        assertTrue(hint.contains("opciones de plan"))
    }

    @Test
    fun everyGraphQuestionHasAFriendlyPromptAndStage() {
        val ids = WizChatQuestionId.entries
        ids.forEach { id ->
            val question = requireNotNull(WizChatGraph.question(id)) { "missing $id" }
            assertTrue(question.prompt.isNotBlank())
            assertTrue(question.prompt.endsWith("?") || question.prompt.endsWith(".") || question.prompt.endsWith("!") || question.prompt.endsWith("…"))
            assertFalse(question.prompt.contains("ecuación"))
            assertFalse(question.prompt.contains("protocolo de"))
        }
    }

    @Test
    fun rResultBelongsToRingsStage() {
        assertEquals(WizChatStage.RINGS, WizChatGraph.stageFor(WizChatQuestionId.R_RESULT))
    }
}
