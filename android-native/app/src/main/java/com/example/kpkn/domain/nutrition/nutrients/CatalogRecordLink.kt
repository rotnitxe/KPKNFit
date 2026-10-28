package com.example.kpkn.domain.nutrition.nutrients

import com.example.kpkn.data.models.FoodItem

/** Enlace alimento → ficha del paquete de catálogo (F4 §3). */
object CatalogRecordLink {
    private val DIGITS = Regex("^[0-9]+$")
    private val GTIN_LIKE = Regex("^[0-9]{8,14}$")

    /** Fuente de las fichas del vocabulario y del paquete v2 (`ResolvedTagAdapterV2.buildFood`): su id ES el id del paquete. */
    const val V2_SOURCE = "KPKN catálogo v2"
    private val V2_PACK_PREFIXES = listOf("usda_", "off_", "ciqual_", "kpkn_")

    /** Id de la ficha del paquete a la que apunta [food], o null si no se puede enlazar (compuestos, recetas, vacíos). */
    fun recordIdFor(food: FoodItem): String? {
        if (food.recipe != null) return null
        val id = food.id.trim()
        if (id.startsWith("usda_") || id.startsWith("off_")) {
            return id.takeIf { it.length > id.indexOf('_') + 1 }
        }
        // Ficha v2 (vocabulario o paquete): CIQUAL y los platos kpkn_ también están en el paquete (N39, N44).
        if (food.source == V2_SOURCE && V2_PACK_PREFIXES.any { id.startsWith(it) }) {
            return id.takeIf { it.length > id.indexOf('_') + 1 }
        }
        val sourceRecordId = food.sourceRecordId?.trim().orEmpty()
        val source = food.source?.uppercase().orEmpty()
        if (sourceRecordId.isEmpty() || source.isEmpty()) return null
        if (source.contains("USDA") && DIGITS.matches(sourceRecordId)) return "usda_$sourceRecordId"
        if (source.contains("OFF") && GTIN_LIKE.matches(sourceRecordId)) return "off_$sourceRecordId"
        return null
    }
}
