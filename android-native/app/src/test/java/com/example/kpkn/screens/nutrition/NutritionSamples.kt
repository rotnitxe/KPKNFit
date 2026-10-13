package com.example.kpkn.screens.nutrition

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.ResolvedTag

/**
 * Datos de ejemplo de las hojas de render de Nutrición. No son datos de la app: solo sirven para dibujar las pantallas sin base de
 * datos ni repositorios.
 */
internal object NutritionSamples {

    /** Lo que una persona escribiría en «¿Qué comiste?». */
    const val DESCRIBE_TEXT = "Dos huevos con tostadas y un café"

    /** Una fila de resultado de búsqueda, con lo que pinta la fila. */
    data class SearchRow(val name: String, val caption: String?, val kcal: Int, val basisGrams: Int)

    /** Cinco resultados para la búsqueda «pan», con su marca o su nota de coincidencia cuando la tienen. */
    val panResults: List<SearchRow> = listOf(
        SearchRow("Pan integral de molde", "Bimbo", 247, 100),
        SearchRow("Pan blanco de molde", "Coincidencia media", 265, 100),
        SearchRow("Pan árabe", "Coincidencia baja", 275, 100),
        SearchRow("Pan de hamburguesa", null, 258, 100),
        SearchRow("Pan de centeno", "Marca blanca", 259, 100),
    )

    /** Alimento interpretado y resuelto, sin dudas: la fila normal. */
    val chickenTag: ResolvedTag = ResolvedTag(
        tag = "pollo a la plancha",
        portion = PortionPreset.MEDIUM,
        amountGrams = 150.0,
        baseAmountGrams = 150.0,
        isResolved = true,
        loggedFood = LoggedFood(
            id = "pollo",
            foodName = "Pollo a la plancha",
            amount = 150.0,
            calories = 248.0,
            protein = 46.0,
            carbs = 0.0,
            fats = 5.4,
        ),
    )

    /** Tortilla con la composición por aclarar: su fila lleva la pregunta pendiente ([tortillaQuestion]). */
    val tortillaTag: ResolvedTag = ResolvedTag(
        tag = "tortilla",
        portion = PortionPreset.LARGE,
        amountGrams = 180.0,
        baseAmountGrams = 180.0,
        isResolved = false,
        loggedFood = LoggedFood(
            id = "tortilla",
            foodName = "Tortilla de papas",
            amount = 180.0,
            calories = 320.0,
            protein = 11.0,
            carbs = 28.0,
            fats = 18.0,
        ),
    )

    /** La pregunta de la tortilla: qué llevaba, con dos candidatos y las composiciones fijas de la hoja. */
    val tortillaQuestion: ClarificationRequest = ClarificationRequest.Identity(
        requestId = "tortilla_composition",
        candidateIds = listOf("tortilla_maiz", "tortilla_papa"),
        candidateLabels = listOf("Tortilla de maíz", "Tortilla de papas"),
    )

    /** Arroz estimado con rango: la cifra lleva «≈» y su rango de kcal. */
    val riceTag: ResolvedTag = ResolvedTag(
        tag = "arroz blanco",
        portion = PortionPreset.MEDIUM,
        amountGrams = 200.0,
        baseAmountGrams = 200.0,
        isResolved = true,
        isUncertain = true,
        loggedFood = LoggedFood(
            id = "arroz",
            foodName = "Arroz blanco cocido",
            amount = 200.0,
            calories = 260.0,
            protein = 5.0,
            carbs = 57.0,
            fats = 0.6,
            caloriesMin = 220.0,
            caloriesMax = 300.0,
            isUncertain = true,
        ),
    )
}
