package com.example.kpkn.domain.nutrition.nutrients

import com.example.kpkn.data.models.LoggedFood

/**
 * Cantidad de [id] en este registro (en la unidad de [NutrientId.unit]) a partir de la instantánea por 100 g.
 * Null = sin dato: no hay instantánea, la clave no está, o la cantidad no está en gramos / no es positiva.
 */
fun LoggedFood.nutrientAmount(id: NutrientId): Double? {
    val per100 = nutrientsPer100g?.get(id.key) ?: return null
    if (unit != "g" || !(amount > 0.0)) return null
    return per100 * amount / 100.0
}

/** True si el registro lleva el vector de nutrientes del paquete de catálogo. */
fun LoggedFood.hasNutrientSnapshot(): Boolean = !nutrientsPer100g.isNullOrEmpty()

/** Copia con la instantánea por 100 g y su generación; los valores no finitos se descartan (quedan como sin dato). */
fun LoggedFood.withNutrientSnapshot(per100: Map<NutrientId, Double>, generation: String): LoggedFood =
    copy(
        nutrientsPer100g = per100.entries
            .filter { it.value.isFinite() }
            .associate { it.key.key to it.value },
        catalogGeneration = generation,
    )

/** Pone la instantánea por 100 g de la ficha del paquete (convertida desde 100 ml con la densidad si hace falta); sin cambios si no se puede. */
fun LoggedFood.withCatalogNutrients(port: NutritionCatalogPort): LoggedFood {
    if (isQuickEntry || hasNutrientSnapshot()) return this
    val recordId = catalogRecordId ?: return this
    val record = port.record(recordId) ?: return this
    val values = port.nutrientsPer100(recordId)
    if (values.isEmpty()) return this
    if (!(record.basisAmount > 0.0)) return this
    val factor = when (record.basisUnit) {
        "g" -> 100.0 / record.basisAmount
        "ml" -> 100.0 / record.basisAmount / (record.densityGPerMl?.takeIf { it > 0.0 } ?: 1.0)
        else -> return this
    }
    return withNutrientSnapshot(values.mapValues { it.value * factor }, port.generation)
}
