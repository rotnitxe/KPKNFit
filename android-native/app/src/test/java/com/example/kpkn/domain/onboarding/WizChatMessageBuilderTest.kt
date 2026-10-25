package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WizChatMessageBuilderTest {
    private fun answer(
        id: WizChatQuestionId,
        kind: WizChatAnswerKind = WizChatAnswerKind.CHOICE,
        text: String? = null,
        number: Double? = null,
        values: List<String> = emptyList(),
        source: WizChatAnswerSource = WizChatAnswerSource.DECLARED,
        revision: Int = 1,
        variantId: String? = "abcd12",
    ) = WizChatAnswerRecord(id, kind, textValue = text, numberValue = number, values = values, source = source, revision = revision, variantId = variantId)

    @Test
    fun introNeverChangesRetroactivelyWhenTheNameIsLearned() {
        val beforeName = WizChatMessageBuilder.build(
            acceptedAnswers = emptyList(),
            currentQuestionId = WizChatQuestionId.P_NAME,
            weightUnit = "kg",
            profileName = null,
        )
        val afterName = WizChatMessageBuilder.build(
            acceptedAnswers = listOf(answer(WizChatQuestionId.P_NAME, WizChatAnswerKind.TEXT, text = "Ana")),
            currentQuestionId = WizChatQuestionId.P_GENDER,
            weightUnit = "kg",
            profileName = null,
        )
        assertEquals(beforeName.first().text, afterName.first().text)
        assertTrue(afterName.first().text.startsWith("¡Hola!"))

        val named = WizChatMessageBuilder.build(
            acceptedAnswers = emptyList(),
            currentQuestionId = WizChatQuestionId.P_NAME,
            weightUnit = "kg",
            profileName = "Luis",
        )
        assertTrue(named.first().text.startsWith("¡Hola, Luis!"))
    }

    @Test
    fun daysAnswerExplainsWhatItChanges() {
        val messages = WizChatMessageBuilder.build(
            acceptedAnswers = listOf(answer(WizChatQuestionId.T_DAYS, text = "3")),
            currentQuestionId = WizChatQuestionId.T_WEEKDAYS,
            weightUnit = "kg",
            profileName = null,
        )
        val ack = messages.first { it.id.startsWith("ack:") }.text
        assertTrue(ack.contains("tres días"))
        assertTrue(ack.contains("Buscaré planes que encajen en esos días"))
    }

    @Test
    fun goalMuscleAnswerExplainsTheTrainingFocus() {
        val messages = WizChatMessageBuilder.build(
            acceptedAnswers = listOf(answer(WizChatQuestionId.T_GOAL, text = "Músculo")),
            currentQuestionId = WizChatQuestionId.T_STYLE,
            weightUnit = "kg",
            profileName = null,
        )
        val ack = messages.first { it.id.startsWith("ack:") }.text
        assertTrue(ack.contains("ganar músculo"))
        assertTrue(ack.contains("volumen"))
        assertTrue(ack.contains("programas"))
        assertFalse(ack.startsWith("Perfecto."))
    }

    @Test
    fun homeEnvironmentAnswerPointsAtTheEquipmentQuestion() {
        val messages = WizChatMessageBuilder.build(
            acceptedAnswers = listOf(answer(WizChatQuestionId.T_EQUIPMENT, text = "Entreno en casa")),
            currentQuestionId = WizChatQuestionId.T_HOME_EQUIPMENT,
            weightUnit = "kg",
            profileName = null,
        )
        val ack = messages.first { it.id.startsWith("ack:") }.text
        assertTrue(ack.contains("lo que tienes en casa"))
        assertTrue(ack.contains("material"))
    }

    @Test
    fun genderAndEquationSexKeepTheirRolesApart() {
        val gender = WizChatMessageBuilder.build(
            acceptedAnswers = listOf(answer(WizChatQuestionId.P_GENDER, text = "Mujer")),
            currentQuestionId = WizChatQuestionId.P_AGE,
            weightUnit = "kg",
            profileName = null,
        ).first { it.id.startsWith("ack:") }.text
        assertFalse(gender.contains("ecuación"))
        assertFalse(gender.contains("calcula tu energía"))
        assertTrue(gender.contains("pregunta aparte"))

        val sex = WizChatMessageBuilder.build(
            acceptedAnswers = listOf(answer(WizChatQuestionId.N_SEX, text = "Femenino")),
            currentQuestionId = WizChatQuestionId.N_ELIGIBILITY,
            weightUnit = "kg",
            profileName = null,
        ).first { it.id.startsWith("ack:") }.text
        assertTrue(sex.contains("energía"))
        assertFalse(sex.contains("género define"))
    }

    @Test
    fun weightPresentationFollowsTheDisplayUnitButKeepsKgInside() {
        val inKg = WizChatMessageBuilder.presentation(
            answer(WizChatQuestionId.P_WEIGHT, WizChatAnswerKind.NUMBER, number = 70.0),
            weightUnit = "kg",
        )
        val inLb = WizChatMessageBuilder.presentation(
            answer(WizChatQuestionId.P_WEIGHT, WizChatAnswerKind.NUMBER, number = 70.0),
            weightUnit = "lb",
        )
        assertTrue(inKg.endsWith("kg"))
        assertTrue(inLb.endsWith("lb"))
        assertTrue(inLb.startsWith("154"))
    }

    @Test
    fun omittedAnswersKeepTheSoftReactionAndUnknownStaysHonest() {
        val omitted = WizChatCopyCatalog.acknowledgement(
            stage = WizChatStage.PROFILE,
            variantId = "abcd12",
            questionId = WizChatQuestionId.P_NAME,
            omitted = true,
        )
        assertTrue(omitted.contains("Sin problema") || omitted.contains("Tranquilo") || omitted.contains("no pasa nada"))

    }

    @Test
    fun stageTransitionAcksKeepTheirTransitionMessage() {
        val messages = WizChatMessageBuilder.build(
            acceptedAnswers = listOf(answer(WizChatQuestionId.P_EXPERIENCE, text = "Estoy empezando")),
            currentQuestionId = WizChatQuestionId.T_ROUTE,
            weightUnit = "kg",
            profileName = null,
        )
        val ack = messages.first { it.id.startsWith("ack:") }
        assertEquals(WizChatStage.PROFILE, ack.stage)
        assertTrue(ack.text.contains("entrenamiento"))
    }

    @Test
    fun transcriptIsDeterministicForTheSameInputs() {
        val answers = listOf(
            answer(WizChatQuestionId.P_NAME, WizChatAnswerKind.TEXT, text = "Ana"),
            answer(WizChatQuestionId.T_DAYS, text = "3"),
        )
        val first = WizChatMessageBuilder.build(answers, WizChatQuestionId.T_WEEKDAYS, "kg", null)
        val second = WizChatMessageBuilder.build(answers, WizChatQuestionId.T_WEEKDAYS, "kg", null)
        assertEquals(first, second)
    }

}
