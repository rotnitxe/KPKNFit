package com.example.kpkn.screens.nutrition

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
}
