package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem

/**
 * Helpers for raw/dry vs cooked/hydrated state and prepared DB variants.
 * Keeps cooking factors moderate — does not use CookingMethodParser multipliers.
 */
object CookingStateResolver {

    enum class ClarificationKind {
        NONE,
        DRY_VS_COOKED,
        RAW_VS_COOKED,
    }

    private val HYDRATION_KEYWORDS = listOf(
        "arroz", "pasta", "fideo", "fideos", "soya texturizada", "soja texturizada",
        "pvt", "avena", "quinoa", "lenteja", "garbanzo", "poroto", "frijol", "frejol",
    )

    private val PROTEIN_KEYWORDS = listOf(
        "pollo", "pechuga", "carne", "vacuno", "cerdo", "pavo", "pescado", "salmón",
        "salmon", "atún", "atun", "lomo", "bife", "filete", "molida",
    )

    fun isDbFoodRaw(food: FoodItem): Boolean {
        val blob = (food.name + " " + food.searchAliases.joinToString(" ")).lowercase()
        return blob.contains("(crudo)") || blob.contains("cruda") || blob.contains("crudo") ||
            blob.contains("(seca)") || blob.contains("(seco)") ||
            Regex("""\bsec[oa]\b""").containsMatchIn(blob) ||
            blob.contains("deshidratad")
    }

    fun isDbFoodCooked(food: FoodItem): Boolean {
        val blob = (food.name + " " + food.searchAliases.joinToString(" ")).lowercase()
        return blob.contains("(cocido)") || blob.contains("cocida") || blob.contains("cocido") ||
            blob.contains("hidratada/cocida") || blob.contains("hidratad") ||
            blob.contains("(frita)") || blob.contains("(frito)") ||
            blob.contains("(plancha)") || blob.contains("(horno)") ||
            blob.contains("(vapor)") || blob.contains("(parrilla)")
    }

    fun isAlreadyPreparedForMethod(food: FoodItem, method: CookingMethod?): Boolean {
        if (method == null || method == CookingMethod.CRUDO) return false
        val name = food.name.lowercase()
        return when (method) {
            CookingMethod.FRITO, CookingMethod.EMPANIZADO_FRITO ->
                // B5: "revuelto" también es una fila preparada para FRITO (huevos revueltos);
                // sin esto se aplicaba factor FRITO + aceite sobre la fila cruda aunque existiera.
                name.contains("frit") || name.contains("revuelto")
            CookingMethod.PLANCHA -> name.contains("plancha")
            CookingMethod.HORNO -> name.contains("horno")
            CookingMethod.VAPOR -> name.contains("vapor")
            CookingMethod.ASADO_PARRILLA -> name.contains("parrilla") || name.contains("asad")
            CookingMethod.COCIDO, CookingMethod.OLLA, CookingMethod.GUISADO ->
                name.contains("cocid") || name.contains("hidratad")
            CookingMethod.AHUMADO -> name.contains("ahumad")
            CookingMethod.CRUDO -> false
        }
    }

    fun methodSearchSuffix(method: CookingMethod): String? = methodSearchSuffixes(method).firstOrNull()

    /** Sufijos de fila preparada por método. FRITO también prueba "revuelto" (B5). */
    fun methodSearchSuffixes(method: CookingMethod): List<String> = when (method) {
        CookingMethod.FRITO, CookingMethod.EMPANIZADO_FRITO -> listOf("frito", "revuelto", "frita", "revuelta")
        CookingMethod.PLANCHA -> listOf("plancha")
        CookingMethod.HORNO -> listOf("horno")
        CookingMethod.VAPOR -> listOf("vapor")
        CookingMethod.ASADO_PARRILLA -> listOf("parrilla", "asado")
        CookingMethod.COCIDO -> listOf("cocido")
        CookingMethod.AHUMADO -> listOf("ahumado")
        CookingMethod.OLLA, CookingMethod.GUISADO -> listOf("cocido")
        CookingMethod.CRUDO -> emptyList()
    }

    /** Prefer a DB row that already encodes the preparation (e.g. pechuga frita). */
    fun findPreparedVariant(tag: String, method: CookingMethod?): FoodItem? {
        if (method == null || method == CookingMethod.CRUDO) return null
        val suffixes = methodSearchSuffixes(method)
        if (suffixes.isEmpty()) return null
        for (suffix in suffixes) {
            val queries = listOf(
                "$tag $suffix",
                "${tag.trim()} ($suffix)",
            )
            for (q in queries) {
                findFoodByNormalized(q)?.let { return it }
            }
        }
        return null
    }

    fun findRawVariant(food: FoodItem): FoodItem? {
        val foodName = food.name.lowercase()
        val rawName = foodName
            .replace(Regex("""\s*\((?:cocid[oa]|frit[oa]|plancha|horno|asad[oa]|vapor|parrilla|hidratad[oa])\)"""), "")
            .replace(Regex("""\s+(?:cocid[oa]|frit[oa]|plancha|horno|asad[oa]|hidratad[oa])"""), "")
            .trim()
        if (rawName.isBlank() || rawName == foodName) return null
        return findFoodByNormalized(rawName)
    }

    fun findDryOrCookedVariant(tag: String, wantCooked: Boolean): FoodItem? {
        val lower = tag.lowercase()
        return if (wantCooked) {
            findFoodByNormalized("$lower cocido")
                ?: findFoodByNormalized("$lower hidratada")
                ?: findFoodByNormalized("$lower (cocido)")
                ?: findFoodByNormalized("$lower (hidratada)")
        } else {
            findFoodByNormalized("$lower crudo")
                ?: findFoodByNormalized("$lower seco")
                ?: findFoodByNormalized("$lower (crudo)")
                ?: findFoodByNormalized("$lower (seca)")
                ?: findFoodByNormalized(lower)
        }
    }

    fun clarificationKind(
        tag: String,
        food: FoodItem?,
        cookingMethod: CookingMethod?,
    ): ClarificationKind {
        if (cookingMethod != null) return ClarificationKind.NONE
        val blob = (tag + " " + (food?.name ?: "")).lowercase()

        if (HYDRATION_KEYWORDS.any { blob.contains(it) }) {
            // Ambiguous if we don't know dry vs cooked from name alone
            val known = food != null && (isDbFoodRaw(food) || isDbFoodCooked(food)) &&
                (food.name.lowercase().contains("seca") || food.name.lowercase().contains("seco") ||
                    food.name.lowercase().contains("crudo") || food.name.lowercase().contains("cocid") ||
                    food.name.lowercase().contains("hidratad"))
            // Still ask when user said only "arroz" / "soya" without state words in the tag
            val tagHasState = Regex("""\b(sec[oa]|crudo|cruda|cocid[oa]|hidratad[oa])\b""")
                .containsMatchIn(tag.lowercase())
            if (!tagHasState) return ClarificationKind.DRY_VS_COOKED
            if (!known) return ClarificationKind.DRY_VS_COOKED
            return ClarificationKind.NONE
        }

        if (PROTEIN_KEYWORDS.any { blob.contains(it) }) {
            val tagHasState = Regex("""\b(crudo|cruda|cocid[oa]|frit[oa]|plancha|horno|vapor|parrilla)\b""")
                .containsMatchIn(tag.lowercase())
            if (!tagHasState && (food == null || isDbFoodRaw(food))) {
                return ClarificationKind.RAW_VS_COOKED
            }
        }
        return ClarificationKind.NONE
    }

    fun assumedStateStatus(tag: String, food: FoodItem?): String? {
        val kind = clarificationKind(tag, food, cookingMethod = null)
        return when (kind) {
            ClarificationKind.DRY_VS_COOKED -> {
                val assumed = when {
                    food != null && isDbFoodRaw(food) -> "seco"
                    else -> "cocido"
                }
                "Asumí ${tag.trim()} $assumed — cambia si era lo contrario."
            }
            ClarificationKind.RAW_VS_COOKED ->
                "Asumí peso según la ficha (${food?.name ?: tag}) — aclara crudo o cocido."
            ClarificationKind.NONE -> null
        }
    }

    fun shouldApplyOil(food: FoodItem?, method: CookingMethod?): Boolean {
        if (method != CookingMethod.FRITO && method != CookingMethod.EMPANIZADO_FRITO) return false
        if (food == null) return true
        return !isAlreadyPreparedForMethod(food, method)
    }
}
