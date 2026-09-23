package com.example.kpkn.domain.onboarding

object WizChatValidation {
    /** Edad, estatura y peso son obligatorios: nunca se aceptan omitidos ni ausentes. */
    val mandatoryNumericQuestions = setOf(WizChatQuestionId.P_AGE, WizChatQuestionId.P_HEIGHT, WizChatQuestionId.P_WEIGHT)

    fun stripControls(value: String): String = value.filterNot(Char::isISOControl)
    fun cleanText(value: String): String = stripControls(value).trim()

    fun validate(question: WizChatQuestion, text: String? = null, number: Double? = null, values: List<String> = emptyList()): String? = when (question.id) {
        WizChatQuestionId.P_NAME -> {
            val clean = cleanText(text.orEmpty())
            when {
                clean.isBlank() -> "Escribe un nombre o usa omitir"
                clean.length > 32 -> "Usa hasta 32 caracteres"
                else -> null
            }
        }
        WizChatQuestionId.P_AGE -> range(number, 13.0, 100.0, "La edad debe estar entre 13 y 100 años", false)
        WizChatQuestionId.P_HEIGHT -> range(number, 100.0, 250.0, "La estatura debe estar entre 100 y 250 cm", false)
        WizChatQuestionId.P_WEIGHT -> range(number, 20.0, 500.0, "El peso debe estar entre 20 y 500 kg", false)
        WizChatQuestionId.T_TIME -> range(number, 20.0, 100.0, "El tiempo debe estar entre 20 y 100 minutos", false)
        WizChatQuestionId.N_ELIGIBILITY, WizChatQuestionId.R_DISCOMFORT -> {
            if (values.isEmpty() && !question.allowSkip) "Elige al menos una opción" else if (values.contains("Sin material") && values.size > 1) "Sin material es una opción exclusiva" else if (values.contains("Ninguna de estas") && values.size > 1) "Ninguna de estas es exclusiva" else null
        }
        else -> if (question.kind == WizChatAnswerKind.CHOICE) when {
            text.isNullOrBlank() && !question.allowSkip -> "Elige una opción"
            question.id !in setOf(WizChatQuestionId.T_PLAN, WizChatQuestionId.N_START) &&
                !text.isNullOrBlank() && text !in question.options -> "Elige una opción disponible"
            else -> null
        } else null
    }

    private fun range(value: Double?, min: Double, max: Double, message: String, allowSkip: Boolean): String? = when {
        value == null && allowSkip -> null
        value == null -> message
        !value.isFinite() || value !in min..max -> message
        else -> null
    }

    fun answerIsAccepted(question: WizChatQuestion, text: String? = null, number: Double? = null, values: List<String> = emptyList()): Boolean = validate(question, text, number, values) == null

    fun exclusiveMultiChoice(values: List<String>, exclusiveValue: String, label: String): String? = when {
        values.isEmpty() -> "Elige al menos una opción"
        values.contains(exclusiveValue) && values.size > 1 -> "$exclusiveValue es una opción exclusiva"
        else -> null
    }
}
