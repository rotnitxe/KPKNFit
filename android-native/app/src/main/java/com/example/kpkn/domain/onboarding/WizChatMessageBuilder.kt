package com.example.kpkn.domain.onboarding

/**
 * Pure builder for the whole WIZCHAT transcript. The introduction only depends
 * on the profile name known before the wizard started, so it never changes
 * retroactively when the assistant learns the name mid-conversation.
 */
object WizChatMessageBuilder {
    fun build(
        acceptedAnswers: List<WizChatAnswerRecord>,
        currentQuestionId: WizChatQuestionId,
        weightUnit: String,
        profileName: String?,
    ): List<WizChatMessage> = buildList {
        val orderedAnswers = acceptedAnswers.sortedBy { it.questionId.ordinal }
        val firstStage = orderedAnswers.firstOrNull()?.questionId?.let(WizChatGraph::stageFor)
            ?: WizChatGraph.stageFor(currentQuestionId)
        add(WizChatMessage("intro:0", firstStage, false, WizChatCopyCatalog.intro(profileName)))
        orderedAnswers.forEachIndexed { index, answer ->
            val question = WizChatGraph.question(answer.questionId) ?: return@forEachIndexed
            val presentation = presentation(answer, weightUnit)
            WizChatCopyCatalog.prefaces(question.id).forEachIndexed { preIndex, text ->
                add(WizChatMessage("pre:${question.id.name}:$preIndex", question.stage, false, text, question.id))
            }
            add(WizChatMessage("question:${answer.questionId.name}:${answer.revision}", question.stage, false, question.prompt, question.id))
            add(WizChatMessage("answer:${answer.questionId.name}:${answer.revision}", question.stage, true, presentation, question.id, answer.variantId))
            val nextAnswer = orderedAnswers.getOrNull(index + 1)
            val nextQuestionId = nextAnswer?.questionId
                ?: currentQuestionId.takeIf { it != answer.questionId }
            val nameForAck = answer.textValue?.takeIf { it.isNotBlank() && answer.questionId == WizChatQuestionId.P_NAME }
                ?: profileName
            add(WizChatMessage("ack:${answer.questionId.name}:${answer.revision}", question.stage, false,
                WizChatCopyCatalog.acknowledgement(
                    stage = question.stage,
                    variantId = answer.variantId,
                    userName = nameForAck,
                    questionId = answer.questionId,
                    answerText = presentation,
                    nextQuestionId = nextQuestionId,
                    omitted = answer.source == WizChatAnswerSource.OMITTED,
                    imported = answer.source == WizChatAnswerSource.IMPORTED,
                ),
                question.id, answer.variantId))
        }
        if (orderedAnswers.none { it.questionId == currentQuestionId }) {
            val current = WizChatGraph.question(currentQuestionId)
            if (current != null) {
                WizChatCopyCatalog.prefaces(current.id).forEachIndexed { preIndex, text ->
                    add(WizChatMessage("pre:${current.id.name}:$preIndex", current.stage, false, text, current.id))
                }
            }
        }
    }

    fun presentation(answer: WizChatAnswerRecord, weightUnit: String): String = when {
        answer.source == WizChatAnswerSource.OMITTED ->
            if (answer.questionId == WizChatQuestionId.P_NAME) "Prefiero no ponerlo" else "Lo haré después"
        answer.values.isNotEmpty() -> if (answer.questionId == WizChatQuestionId.T_MARKS) {
            listOf("Sentadilla", "Banca", "Peso muerto").zip(answer.values)
                .filter { it.second.isNotBlank() }.joinToString(" · ") { "${it.first} ${it.second} kg" }
        } else answer.values.joinToString(" · ")
        answer.numberValue != null -> {
            val unit = WizChatGraph.question(answer.questionId)?.unit.orEmpty()
            val isWeight = answer.questionId == WizChatQuestionId.P_WEIGHT
            val display = if (isWeight) screensWeightToDisplay(answer.numberValue, weightUnit) else answer.numberValue
            val displayedUnit = if (isWeight && weightUnit == "lb") "lb" else unit
            val formatted = java.text.DecimalFormat("0.#").format(display)
            "$formatted $displayedUnit".trim()
        }
        !answer.textValue.isNullOrBlank() -> answer.textValue.orEmpty()
        else -> "Confirmado"
    }

    private fun screensWeightToDisplay(kg: Double, unit: String): Double =
        if (unit == "lb") kg / 0.45359237 else kg
}
