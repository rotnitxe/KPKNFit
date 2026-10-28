package com.example.kpkn.domain.nutrition.v2

import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.SearchConfidence
import com.example.kpkn.data.models.SearchSource
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.FoodState
import com.example.kpkn.domain.nutrition.NutrientBasis
import com.example.kpkn.domain.nutrition.TextKeys
import com.example.kpkn.domain.nutrition.nutrients.CatalogPortion
import com.example.kpkn.domain.nutrition.nutrients.CatalogRecord
import com.example.kpkn.domain.nutrition.nutrients.NutrientId
import com.example.kpkn.domain.nutrition.nutrients.NutritionCatalogPort

/**
 * «Buscar» del registro con v2 como sistema: primero lo aprendido y lo propio, luego los conceptos del vocabulario v2, después el
 * resto del buscador anterior y al final productos del paquete que ese no tenga. Toda fila pasa el filtro del registro (nutrientes
 * verificables e identidad declarada) para que tocarla nunca dé «Esa ficha no coincide».
 */
object LoggerSearchV2 {
    const val CONCEPT_LIMIT = 10
    const val PACK_LIMIT = 8

    /** El FTS del paquete no ordena: se piden más ids y se ordenan aquí por cómo coincide el nombre. */
    const val PACK_FETCH = 150

    /** Fila del paquete con el nombre que se mostraría y cómo coincide con la consulta. */
    private class PackRow(val record: CatalogRecord, val name: String, val level: Int)

    private fun accepted(query: String, food: FoodItem): Boolean =
        NutrientBasis.isVerified(food) && FoodIdentity.matchesDeclaredIdentity(query, food)

    /** Agua, café o bebida zero con todo en 0 pasan [NutrientBasis.isVerified] solo con esta etiqueta. */
    private fun zeroEnergyTags(per100g: Map<String, Double>): List<String> {
        val energy = per100g["energy"] ?: return emptyList()
        return if (energy <= NutrientBasis.ZERO_ENERGY_MAX_KCAL) listOf(NutrientBasis.ZERO_ENERGY_TAG) else emptyList()
    }

    /** Conceptos del vocabulario v2 que coinciden con [query], como filas del registro (las que el registro aceptaría). */
    fun conceptCandidates(search: ConceptSearchV2, query: String, limit: Int = CONCEPT_LIMIT): List<FoodCandidate> {
        if (limit <= 0) return emptyList()
        val generation = search.catalog.generation
        val out = ArrayList<FoodCandidate>()
        for (hit in search.search(query, limit * 2)) {
            val food = conceptFood(hit, generation)
            if (!accepted(query, food)) continue
            out.add(
                FoodCandidate(
                    foodId = food.id,
                    displayName = food.name,
                    score = 1.0 - 0.1 * hit.level,
                    confidence = if (hit.level <= 1) SearchConfidence.HIGH else SearchConfidence.MEDIUM,
                    source = SearchSource.LOCAL,
                    food = food,
                    trace = listOf("v2", "alias:${hit.matchedAlias}", "nivel:${hit.level}"),
                ),
            )
            if (out.size >= limit) break
        }
        return out
    }

    /** Productos del paquete de catálogo que coinciden con [query], mejor nombre primero, como filas del registro. */
    fun packCandidates(port: NutritionCatalogPort, query: String, limit: Int = PACK_LIMIT): List<FoodCandidate> {
        if (limit <= 0) return emptyList()
        val q = ConceptSearchV2.searchTokens(query)
        // Minúsculas sin tildes: el lector del paquete tokeniza con [a-z0-9]+.
        val norm = TextKeys.normalize(query)
        if (q.isEmpty() || norm.isEmpty()) return emptyList()

        val rows = port.search(norm, PACK_FETCH)
            .mapNotNull { record ->
                val name = displayNameOf(record) ?: return@mapNotNull null
                PackRow(record, name, ConceptSearchV2.matchLevel(q, ConceptSearchV2.searchTokens(name)) ?: 3)
            }
            .sortedWith(compareBy<PackRow>({ it.level }, { it.name.length }, { it.name }))

        val out = ArrayList<FoodCandidate>()
        for (row in rows) {
            val record = row.record
            val food = packFood(record, row.name, port.nutrientsPer100(record.id), port.portions(record.id), port.generation) ?: continue
            if (!accepted(query, food)) continue
            out.add(
                FoodCandidate(
                    foodId = food.id,
                    displayName = food.name,
                    score = 0.9 - 0.1 * row.level,
                    confidence = when (row.level) {
                        0, 1 -> SearchConfidence.HIGH
                        2 -> SearchConfidence.MEDIUM
                        else -> SearchConfidence.LOW
                    },
                    source = when {
                        record.id.startsWith("off_") -> SearchSource.OFF
                        record.id.startsWith("usda_") -> SearchSource.USDA
                        else -> SearchSource.LOCAL
                    },
                    food = food,
                    trace = listOf("paquete", "nivel:${row.level}"),
                ),
            )
            if (out.size >= limit) break
        }
        return out
    }

    /** La ficha [recordId] del paquete como fila de «Buscar» (favoritos y lo aprendido); null si no está o no se puede convertir. */
    fun packFoodById(port: NutritionCatalogPort, recordId: String): FoodItem? {
        val record = port.record(recordId) ?: return null
        val name = displayNameOf(record) ?: record.name.trim().takeIf { it.isNotEmpty() } ?: return null
        return packFood(record, name, port.nutrientsPer100(recordId), port.portions(recordId), port.generation)
    }

    /**
     * Une las tres fuentes sin repetir. Orden: lo aprendido, lo propio, los conceptos v2, el buscador anterior y el paquete.
     * Una fila se omite si su id o su nombre (con marca) ya salió.
     */
    fun merge(
        legacy: List<FoodCandidate>,
        concepts: List<FoodCandidate>,
        pack: List<FoodCandidate>,
        limit: Int,
        learnedFoodId: String? = null,
    ): List<FoodCandidate> {
        if (limit <= 0) return emptyList()
        fun isLearned(candidate: FoodCandidate): Boolean =
            candidate.learned || (learnedFoodId != null && candidate.foodId == learnedFoodId)

        val all = legacy + concepts + pack
        val ordered = all.filter { isLearned(it) } + all.filter { it.food.isCustom } + concepts + legacy + pack

        val seenIds = HashSet<String>()
        val seenNames = HashSet<String>()
        val out = ArrayList<FoodCandidate>()
        for (candidate in ordered) {
            val idKey = candidate.foodId.ifBlank { candidate.food.id }
            val nameKey = FoodIdentity.normalize(candidate.food.name) + "|" + FoodIdentity.normalize(candidate.food.brand.orEmpty())
            if ((idKey.isNotBlank() && idKey in seenIds) || nameKey in seenNames) continue
            if (idKey.isNotBlank()) seenIds.add(idKey)
            seenNames.add(nameKey)
            out.add(if (isLearned(candidate) && !candidate.learned) candidate.copy(learned = true) else candidate)
            if (out.size >= limit) break
        }
        return out
    }

    /** La ficha de [hit] como [FoodItem] del registro: nombre con su estado o forma, alias para la identidad y porción comida. */
    internal fun conceptFood(hit: ConceptHitV2, generation: String): FoodItem =
        ResolvedTagAdapterV2.buildFood(
            hit.record.id,
            hit.label,
            hit.concept.category,
            hit.record.per100g,
            ResolvedTagAdapterV2.foodStateOf(hit.variant.state),
            generation,
        ).copy(
            searchAliases = (listOf(hit.concept.name) + hit.concept.aliasesEs + hit.concept.aliasesEn + hit.concept.typos).distinct(),
            portionGrams = portionGramsFor(hit.concept, hit.variant),
            tags = zeroEnergyTags(hit.record.per100g),
        )

    /** Gramos de la porción que «Buscar» usa como cantidad comida: los del concepto o, si la variante es de otro estado, los de su porción de ese estado. */
    private fun portionGramsFor(concept: ConceptV2, variant: ConceptVariantV2): Double? {
        val grams = if (variant.state == concept.defaultState) {
            concept.defaultGrams
        } else {
            concept.portions
                .firstOrNull { it.state != null && it.state == variant.state && it.grams > 0.0 }
                ?.grams
                ?: concept.defaultGrams
        }
        return grams.takeIf { it.isFinite() && it > 0.0 }
    }

    /** Nombre que se muestra de una ficha del paquete; las de USDA y CIQUAL sin nombre en español no se muestran (ya salen por los conceptos). */
    private fun displayNameOf(record: CatalogRecord): String? =
        record.nameEs?.trim()?.takeIf { it.isNotEmpty() }
            ?: record.name.trim().takeIf { it.isNotEmpty() && record.id.startsWith("off_") }

    /**
     * La ficha [record] del paquete como [FoodItem] por 100 g (convertida desde 100 ml con la densidad si hace falta, igual que
     * `withCatalogNutrients`); null si no tiene nutrientes o su base no se puede convertir.
     */
    internal fun packFood(
        record: CatalogRecord,
        displayName: String,
        per100: Map<NutrientId, Double>,
        portions: List<CatalogPortion>,
        generation: String,
    ): FoodItem? {
        if (per100.isEmpty() || !(record.basisAmount > 0.0)) return null
        val factor = when (record.basisUnit) {
            "g" -> 100.0 / record.basisAmount
            "ml" -> 100.0 / record.basisAmount / (record.densityGPerMl?.takeIf { it > 0.0 } ?: 1.0)
            else -> return null
        }
        val per100g = per100.entries
            .filter { it.value.isFinite() }
            .associate { it.key.key to it.value * factor }
        return ResolvedTagAdapterV2.buildFood(record.id, displayName, record.category, per100g, FoodState.UNKNOWN, generation).copy(
            brand = record.brand?.trim()?.takeIf { it.isNotEmpty() },
            searchAliases = listOfNotNull(record.nameEs, record.name).map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            portionGrams = portions.firstOrNull { it.grams.isFinite() && it.grams > 0.0 }?.grams,
            tags = zeroEnergyTags(per100g),
        )
    }
}
