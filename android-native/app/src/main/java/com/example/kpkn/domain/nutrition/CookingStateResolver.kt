package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
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

    private val PRODUCE_KEYWORDS = listOf(
        "tomate", "lechuga", "zanahoria", "brócoli", "brocoli", "palta", "aguacate",
        "manzana", "plátano", "platano", "naranja", "pepino", "apio", "espinaca",
    )

    fun isDbFoodRaw(food: FoodItem): Boolean {
        val blob = (food.name + " " + food.searchAliases.joinToString(" ")).lowercase()
        return blob.contains("(crudo)") || blob.contains("cruda") || blob.contains("crudo") ||
            blob.contains("(seca)") || blob.contains("(seco)") ||
            Regex("""\bsec(?:o|a|os|as)\b""").containsMatchIn(blob) ||
            blob.contains("deshidratad")
    }

    fun isDbFoodCooked(food: FoodItem): Boolean {
        val blob = (food.name + " " + food.searchAliases.joinToString(" ")).lowercase()
        return blob.contains("(cocido)") || blob.contains("cocida") || blob.contains("cocido") ||
            blob.contains("cocinad") ||
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
                name.contains("cocid") || name.contains("cocinad") || name.contains("hidratad")
            CookingMethod.AHUMADO -> name.contains("ahumad")
            CookingMethod.CRUDO -> false
        }
    }

    fun methodSearchSuffix(method: CookingMethod): String? = methodSearchSuffixes(method).firstOrNull()

    /**
     * Sufijos de fila preparada por método, con género y plural: "Pechuga de
     * Pollo (cocida)" nunca se encontraba con el sufijo masculino "cocido" y el
     * resolver caía a la ficha cruda con doble conversión. "cocinado/a" son
     * sinónimos cotidianos de cocido.
     */
    fun methodSearchSuffixes(method: CookingMethod): List<String> = when (method) {
        CookingMethod.FRITO, CookingMethod.EMPANIZADO_FRITO -> listOf("frita", "frito", "revuelto", "revuelta", "fritas", "fritos")
        CookingMethod.PLANCHA -> listOf("plancha")
        CookingMethod.HORNO -> listOf("horno")
        CookingMethod.VAPOR -> listOf("vapor")
        CookingMethod.ASADO_PARRILLA -> listOf("parrilla", "asado", "asada")
        CookingMethod.COCIDO, CookingMethod.OLLA, CookingMethod.GUISADO ->
            listOf("cocida", "cocido", "cocidas", "cocidos", "cocinada", "cocinado", "cocinadas", "cocinados")
        CookingMethod.AHUMADO -> listOf("ahumado", "ahumada")
        CookingMethod.CRUDO -> emptyList()
    }

    /**
     * Estado estructurado que declara el usuario vía método de cocción:
     * CRUDO → RAW; cualquier otro método produce un alimento cocido. El método
     * llega separado del tag (el parser extrae la palabra), así que este hint
     * es la única forma de que el ranking compare estado contra las filas.
     */
    fun stateForMethod(method: CookingMethod?): FoodState? = when (method) {
        null -> null
        CookingMethod.CRUDO -> FoodState.RAW
        else -> FoodState.COOKED
    }

    /** Prefer a DB row that already encodes the preparation (e.g. pechuga frita). */
    fun findPreparedVariant(tag: String, method: CookingMethod?): FoodItem? {
        if (method == null || method == CookingMethod.CRUDO) return null
        val suffixes = methodSearchSuffixes(method)
        if (suffixes.isEmpty()) return null
        for (suffix in suffixes) {
            // La forma entre paréntesis es la clave de nombre exacto de la fila
            // ("pasta (cocida)" → gen040) y debe ganar sobre la forma de alias
            // ("pasta cocida" → alias de la fila hidratada).
            val queries = listOf(
                "${tag.trim()} ($suffix)",
                "$tag $suffix",
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
        if (FoodIdentity.familyFor(tag) == "pasta") {
            val pastaQueries = if (wantCooked) {
                listOf(
                    "pasta cocida",
                    "pasta (hidratada/cocida)",
                    "fideos cocidos",
                    "tallarines cocidos",
                )
            } else {
                listOf(
                    "pasta cruda",
                    "pasta (cruda)",
                    "fideos secos",
                    "tallarines secos",
                )
            }
            pastaQueries.forEach { query ->
                findFoodExactByNormalized(query)?.let { return it }
            }
        }
        return if (wantCooked) {
            findFoodByNormalized("$lower cocida")
                ?: findFoodByNormalized("$lower cocido")
                ?: findFoodByNormalized("$lower cocinada")
                ?: findFoodByNormalized("$lower cocinado")
                ?: findFoodByNormalized("$lower hidratada")
                ?: findFoodByNormalized("$lower (cocida)")
                ?: findFoodByNormalized("$lower (cocido)")
                ?: findFoodByNormalized("$lower (hidratada)")
        } else {
            findFoodByNormalized("$lower cruda")
                ?: findFoodByNormalized("$lower crudo")
                ?: findFoodByNormalized("$lower seca")
                ?: findFoodByNormalized("$lower seco")
                ?: findFoodByNormalized("$lower (cruda)")
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
            val tagHasState = Regex("""\b(sec(?:o|a|os|as)|crudo[s]?|cocid(?:o|a|os|as)|hidratad(?:o|a|os|as))\b""")
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
                    food != null && isDbFoodRaw(food) && assumedDefault(tag, food) == FoodState.RAW -> "seco"
                    else -> "cocido"
                }
                "Asumí ${tag.trim()} $assumed — cambia si era lo contrario."
            }
            ClarificationKind.RAW_VS_COOKED ->
                "Asumí ${tag.trim()} cocido — cambia si era crudo."
            ClarificationKind.NONE -> null
        }
    }

    /**
     * Default eaten-as state when the user omits crudo/seco/cocido.
     * Grains, legumes and proteins → cooked; raw produce → raw.
     */
    fun assumedDefault(tag: String, food: FoodItem?): FoodState? {
        if (FoodIdentity.stateFor(tag) != FoodState.UNKNOWN) return null
        val family = FoodIdentity.familyFor(tag)
        if (family in setOf("salsa_de_tomate", "ketchup", "pizza", "sopa", "jugo")) return null
        val blob = FoodIdentity.normalize(tag + " " + (food?.name ?: ""))
        val avenaIsDryVessel = blob.contains("avena") &&
            !blob.contains("cocid") &&
            !blob.contains("hidrat") &&
            !blob.contains("cocinado")
        if (avenaIsDryVessel) {
            return FoodState.RAW
        }
        if ((blob.contains("bowl") || blob.contains("bol") || blob.contains("tazon") ||
                blob.contains("plato") || blob.contains("taza")) && blob.contains("avena")
        ) {
            return FoodState.RAW
        }
        val produceFamily = family in setOf(
            "tomate", "lechuga", "zanahoria", "brocoli", "palta", "manzana", "platano",
        ) || PRODUCE_KEYWORDS.any { FoodIdentity.normalize(it) == FoodIdentity.normalize(tag) }
        if (produceFamily && !FoodIdentity.isCompoundProduct(tag)) return FoodState.RAW
        if (HYDRATION_KEYWORDS.any { blob.contains(it) }) return FoodState.COOKED
        if (PROTEIN_KEYWORDS.any { blob.contains(it) }) return FoodState.COOKED
        if (blob.contains("huevo")) return FoodState.COOKED
        if (FoodIdentity.isStateSensitive(tag)) return FoodState.COOKED
        return null
    }

    fun resolveAssumedVariant(tag: String, food: FoodItem?, assumed: FoodState): FoodItem? {
        val wantCooked = assumed == FoodState.COOKED || assumed == FoodState.HYDRATED
        if (food != null) {
            if (wantCooked && isDbFoodCooked(food) && !isDbFoodRaw(food)) return food
            if (!wantCooked && isDbFoodRaw(food) && !isDbFoodCooked(food)) return food
        }
        val fromTag = if (wantCooked) {
            findPreparedVariant(tag, CookingMethod.COCIDO) ?: findDryOrCookedVariant(tag, true)
        } else {
            findDryOrCookedVariant(tag, false)
        }
        if (fromTag != null) return fromTag
        val baseName = food?.name?.replace(Regex("""\s*\([^)]*\)"""), "")?.trim().orEmpty()
        if (baseName.isNotBlank()) {
            val fromName = if (wantCooked) {
                findPreparedVariant(baseName, CookingMethod.COCIDO) ?: findDryOrCookedVariant(baseName, true)
            } else {
                findDryOrCookedVariant(baseName, false) ?: food?.let(::findRawVariant)
            }
            if (fromName != null) return fromName
        }
        return food
    }

    fun shouldApplyOil(food: FoodItem?, method: CookingMethod?): Boolean {
        if (method != CookingMethod.FRITO && method != CookingMethod.EMPANIZADO_FRITO) return false
        if (food == null) return true
        return !isAlreadyPreparedForMethod(food, method)
    }
}
