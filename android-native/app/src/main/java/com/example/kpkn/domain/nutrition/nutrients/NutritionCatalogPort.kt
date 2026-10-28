package com.example.kpkn.domain.nutrition.nutrients

/** Ficha del paquete de catálogo (F1a §2). [basisAmount] y [basisUnit] describen la base de la ficha. */
data class CatalogRecord(
    val id: String,
    val source: String,
    val sourceRecordId: String,
    val name: String,
    val nameEs: String?,
    val brand: String?,
    val category: String?,
    val preparation: String,
    val form: String,
    val basisAmount: Double,
    val basisUnit: String,
    val densityGPerMl: Double?,
)

/** Porción casera de una ficha: [grams] siempre presente, [ml] solo si la porción es de volumen. */
data class CatalogPortion(
    val seq: Int,
    val label: String,
    val labelEs: String?,
    val grams: Double,
    val ml: Double?,
)

/**
 * Puerto de dominio hacia el paquete de catálogo publicado (F1a §4). Sin implementación aquí: la capa de datos la aporta.
 * En los mapas de nutrientes, ausente = clave que no está (sin dato); 0 = cero declarado por la fuente.
 */
interface NutritionCatalogPort {
    /** Identificador de la generación publicada; se copia a `LoggedFood.catalogGeneration`. */
    val generation: String

    fun record(id: String): CatalogRecord?

    /** Nutrientes por 100 g (o 100 ml según la base de la ficha) de la ficha [id]; vacío si no existe. */
    fun nutrientsPer100(id: String): Map<NutrientId, Double>

    fun portions(id: String): List<CatalogPortion>

    fun byGtin(gtin: String): CatalogRecord?

    fun search(queryNorm: String, limit: Int): List<CatalogRecord>

    fun recordFor(source: String, sourceRecordId: String): CatalogRecord?
}
