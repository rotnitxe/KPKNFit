package com.example.kpkn.screens.nutrition.components

import com.example.kpkn.domain.nutrition.ClarificationRequest

internal data class FoodClarificationPrompt(
    val title: String,
    val detail: String?,
    val pickOtherLabel: String?,
    val unsureLabel: String,
    val reviewAmountLabel: String?,
)

internal fun foodClarificationPrompt(
    question: ClarificationRequest,
    matchedName: String?,
    unresolvedDeclaredAmount: Boolean,
    dryVsCooked: Boolean,
): FoodClarificationPrompt {
    val title = when (question) {
        is ClarificationRequest.Identity -> when (question.requestId) {
            "composition" -> "¿Qué llevaba la ensalada?"
            "tortilla_composition" -> "¿De qué era la tortilla?"
            "cut" -> "¿Qué corte comiste?"
            else -> "¿Es este el alimento?"
        }
        is ClarificationRequest.WeightState -> if (dryVsCooked) {
            "¿La cantidad era seca o ya cocida?"
        } else {
            "¿La cantidad era en crudo o cocida?"
        }
        is ClarificationRequest.Oil -> "¿Cuánto aceite tenía?"
        is ClarificationRequest.Portion -> if (question.requestId == "package_portion") {
            "¿Consumiste todo el envase o una porción?"
        } else {
            "¿Qué porción fue?"
        }
    }
    val identityNeedsMatchHint = question is ClarificationRequest.Identity &&
        question.requestId !in IDENTITY_SPECIFIC_IDS
    val detail = when {
        unresolvedDeclaredAmount ->
            "No pudimos recuperar la medida que escribiste. Revisa la cantidad antes de guardar."
        identityNeedsMatchHint && !matchedName.isNullOrBlank() ->
            "Coincidencia aproximada: $matchedName. Cámbiala si no es lo que comiste."
        else -> null
    }
    val pickOther = when (question) {
        is ClarificationRequest.Identity -> if (question.requestId == "composition") {
            "Elegir otro tipo de ensalada"
        } else {
            "Cambiar alimento"
        }
        else -> null
    }
    return FoodClarificationPrompt(
        title = title,
        detail = detail,
        pickOtherLabel = pickOther,
        unsureLabel = "Seguir con esta estimación",
        reviewAmountLabel = if (unresolvedDeclaredAmount) "Revisar cantidad" else null,
    )
}

private val IDENTITY_SPECIFIC_IDS = setOf("composition", "tortilla_composition", "cut")
