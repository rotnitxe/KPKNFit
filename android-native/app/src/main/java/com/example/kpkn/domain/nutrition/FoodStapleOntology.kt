package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.FoodItem

/**
 * Curated household staple graph: family → cut → state → method → [foodId].
 *
 * This is the authoritative identity layer for everyday foods. It prevents alias
 * collapse (pollo≠pechuga by default, carne≠molida) and exposes a single
 * clarification when the calorie delta between cuts exceeds [MATERIAL_KCAL_DELTA].
 */
object FoodStapleOntology {

    const val MATERIAL_KCAL_DELTA = 0.15

    enum class Family {
        POLLO,
        VACUNO,
        PAVO,
        PESCADO,
        ARROZ,
        PASTA,
        PAN,
        WHEY,
        ATUN,
        MAIZ,
        HUEVO,
    }

    enum class Cut {
        PECHUGA,
        TRUTRO,
        ALA,
        ENTERO,
        MOLIDA,
        BISTEC,
        FILETE,
        AGUA,
        ACEITE,
        SCOOP,
        GENERIC,
    }

    enum class StapleState {
        RAW,
        COOKED,
        HYDRATED,
        AS_SOLD,
    }

    data class StapleCutOption(
        val label: String,
        val foodId: String,
        val kcalPer100g: Double,
    )

    data class CutClarification(
        val family: Family,
        val options: List<StapleCutOption>,
        val defaultFoodId: String,
    )

    private data class StapleNode(
        val family: Family,
        val cut: Cut,
        val foodId: String,
        val householdDefaultGrams: Double,
        val kcalPer100g: Double,
        val state: StapleState,
        val method: String? = null,
        val nutritionBasis: String = "PER_100G_COOKED",
        val fdcId: String? = null,
        val aliases: Set<String> = emptySet(),
    )

    private val nodes: List<StapleNode> = listOf(
        // Pollo — FDC 171077 / 171009 / 171140 pattern (pechuga ago-2026)
        StapleNode(Family.POLLO, Cut.PECHUGA, "gen004", 150.0, 166.0, StapleState.COOKED, "cocido", "PER_100G_COOKED", "171009", setOf("pechuga", "pechuga de pollo", "poyo")),
        StapleNode(Family.POLLO, Cut.PECHUGA, "gen003", 150.0, 106.0, StapleState.RAW, null, "PER_100G_RAW", "171077", setOf("pechuga cruda", "pechuga de pollo cruda")),
        StapleNode(Family.POLLO, Cut.PECHUGA, "gen003c", 150.0, 173.0, StapleState.COOKED, "plancha", "PER_100G_PREPARED", null, setOf("pollo a la plancha", "pechuga plancha")),
        StapleNode(Family.POLLO, Cut.TRUTRO, "gen003t", 120.0, 177.0, StapleState.RAW, null, "PER_100G_RAW", "171077", setOf("trutro", "trutro de pollo", "muslo", "muslo de pollo", "pierna de pollo")),
        StapleNode(Family.POLLO, Cut.TRUTRO, "gen003tc", 120.0, 195.0, StapleState.COOKED, "cocido", "PER_100G_COOKED", "172385", setOf("trutro cocido", "muslo cocido")),
        StapleNode(Family.POLLO, Cut.ALA, "gen003a", 80.0, 203.0, StapleState.COOKED, "cocido", "PER_100G_COOKED", null, setOf("ala", "ala de pollo", "alitas", "alita")),
        StapleNode(Family.POLLO, Cut.ENTERO, "gen003e", 200.0, 165.0, StapleState.COOKED, "horno", "PER_100G_COOKED", null, setOf("pollo entero", "pollo asado entero")),
        // Vacuno
        StapleNode(Family.VACUNO, Cut.MOLIDA, "gen010", 120.0, 217.0, StapleState.COOKED, "cocido", "PER_100G_COOKED", null, setOf("carne molida", "molida")),
        StapleNode(Family.VACUNO, Cut.BISTEC, "gen093", 150.0, 187.0, StapleState.COOKED, "plancha", "PER_100G_COOKED", null, setOf("filete", "filete de vacuno", "bistec", "lomo", "bife")),
        StapleNode(Family.VACUNO, Cut.FILETE, "gen093h", 150.0, 180.0, StapleState.COOKED, "plancha", "PER_100G_COOKED", null, setOf("churrasco")),
        // Pavo / pescado
        StapleNode(Family.PAVO, Cut.PECHUGA, "gen045", 150.0, 135.0, StapleState.COOKED, "cocido", "PER_100G_COOKED", null, setOf("pavo", "pechuga de pavo")),
        StapleNode(Family.PESCADO, Cut.GENERIC, "gen009", 150.0, 208.0, StapleState.RAW, null, "PER_100G_RAW", null, setOf("salmon", "salmón")),
        StapleNode(Family.PESCADO, Cut.GENERIC, "gen044", 150.0, 94.0, StapleState.COOKED, "cocido", "PER_100G_COOKED", null, setOf("merluza", "merluza cocida")),
        StapleNode(Family.PESCADO, Cut.GENERIC, "gen029", 100.0, 116.0, StapleState.AS_SOLD, "agua", "PER_100G_AS_SOLD", null, setOf("atun", "atún", "atun en lata", "atún en lata")),
        StapleNode(Family.ATUN, Cut.AGUA, "gen029", 100.0, 116.0, StapleState.AS_SOLD, "agua", "PER_100G_AS_SOLD", null, setOf("atun al agua", "atún al agua")),
        StapleNode(Family.ATUN, Cut.ACEITE, "gen029e", 100.0, 200.0, StapleState.AS_SOLD, "aceite", "PER_100G_AS_SOLD", null, setOf("atun en aceite", "atún en aceite")),
        // Arroz / pasta / maíz — default cocido/plato
        StapleNode(Family.ARROZ, Cut.GENERIC, "gen005", 120.0, 130.0, StapleState.COOKED, "cocido", "PER_100G_COOKED", null, setOf("arroz", "arroz blanco", "arroz cocido", "arros")),
        StapleNode(Family.ARROZ, Cut.GENERIC, "gen005c", 40.0, 360.0, StapleState.RAW, null, "PER_100G_RAW", null, setOf("arroz crudo", "arroz seco")),
        StapleNode(Family.PASTA, Cut.GENERIC, "gen040", 160.0, 131.0, StapleState.COOKED, "cocido", "PER_100G_COOKED", null, setOf("pasta", "fideos", "fideos cocidos", "pasta cocida")),
        StapleNode(Family.PASTA, Cut.GENERIC, "gen040h", 160.0, 131.0, StapleState.HYDRATED, "cocido", "PER_100G_COOKED", null, setOf("pasta hidratada")),
        StapleNode(Family.PASTA, Cut.GENERIC, "gen040c", 45.0, 371.0, StapleState.RAW, null, "PER_100G_RAW", null, setOf("pasta cruda", "fideos secos")),
        StapleNode(Family.MAIZ, Cut.GENERIC, "gen071", 80.0, 86.0, StapleState.COOKED, "cocido", "PER_100G_COOKED", null, setOf("choclo", "maiz", "maíz")),
        StapleNode(Family.HUEVO, Cut.GENERIC, "gen007", 50.0, 155.0, StapleState.COOKED, "cocido", "PER_100G_COOKED", null, setOf("huevo", "huevos", "wevo")),
        // Pan contable
        StapleNode(Family.PAN, Cut.GENERIC, "cl013", 80.0, 210.0, StapleState.AS_SOLD, null, "PER_100G_AS_SOLD", null, setOf("hallulla", "hallula")),
        StapleNode(Family.PAN, Cut.GENERIC, "cl010", 100.0, 260.0, StapleState.AS_SOLD, null, "PER_100G_AS_SOLD", null, setOf("marraqueta")),
        StapleNode(Family.PAN, Cut.GENERIC, "gen019", 50.0, 265.0, StapleState.AS_SOLD, null, "PER_100G_AS_SOLD", null, setOf("pan", "pan blanco")),
        StapleNode(Family.PAN, Cut.GENERIC, "gen133", 50.0, 265.0, StapleState.AS_SOLD, null, "PER_100G_AS_SOLD", null, setOf("pan integral")),
        // Whey
        StapleNode(Family.WHEY, Cut.SCOOP, "gen105", 30.0, 400.0, StapleState.AS_SOLD, null, "PER_SERVING", null, setOf("whey", "proteina en polvo", "proteína en polvo", "proteina whey")),
    )

    private val ambiguousFamilies = mapOf(
        Family.POLLO to listOf("gen004", "gen003t", "gen003e"),
        Family.VACUNO to listOf("gen093", "gen010"),
    )

    private val supplementMarkers = listOf(
        "whey", "scoop", "batido de proteina", "batido de proteína", "shake", "proteina en polvo", "proteína en polvo",
    )

    private val proteinFoodMarkers = listOf(
        "pollo", "carne", "pescado", "huevo", "atun", "atún", "salmon", "salmón", "pechuga", "vacuno",
    )

    fun normalizeQuery(query: String): String = FoodIdentity.normalize(query)

    fun detectFamily(query: String): Family? {
        val blob = normalizeQuery(query)
        return when {
            supplementMarkers.any { blob.contains(it) } -> Family.WHEY
            listOf("trutro", "muslo", "pierna de pollo", "ala de pollo", "alita").any { blob.contains(it) } -> Family.POLLO
            listOf("pechuga", "pollo", "poyo").any { blob.contains(it) } -> Family.POLLO
            listOf("molida", "carne molida").any { blob.contains(it) } -> Family.VACUNO
            listOf("filete", "bistec", "lomo", "bife", "vacuno", "asado").any { blob.contains(it) } -> Family.VACUNO
            blob == "carne" -> Family.VACUNO
            listOf("pavo").any { blob.contains(it) } -> Family.PAVO
            listOf("atun en aceite", "atún en aceite").any { blob.contains(it) } -> Family.ATUN
            listOf("atun", "atún", "tuna").any { blob.contains(it) } -> Family.ATUN
            listOf("merluza").any { blob.contains(it) } -> Family.PESCADO
            listOf("salmon", "salmón", "pescado").any { blob.contains(it) } -> Family.PESCADO
            listOf("arroz").any { blob.contains(it) } -> Family.ARROZ
            listOf("pasta", "fideo", "fideos", "tallarin").any { blob.contains(it) } -> Family.PASTA
            listOf("choclo", "maiz").any { blob.contains(it) } -> Family.MAIZ
            listOf("huevo", "wevo").any { blob.contains(it) } -> Family.HUEVO
            listOf("hallulla", "hallula", "marraqueta", "pan integral", "pan ").any { blob.contains(it) } ||
                blob == "pan" || blob == "integral" -> Family.PAN
            else -> null
        }
    }

    private fun resolveNode(query: String): StapleNode? {
        val blob = normalizeQuery(query)
        return nodes
            .flatMap { node -> node.aliases.map { alias -> node to alias } }
            .filter { (_, alias) -> aliasFits(blob, alias) }
            .maxByOrNull { (_, alias) -> alias.length }
            ?.first
    }

    private fun aliasFits(blob: String, alias: String): Boolean {
        if (blob == alias) return true
        if (!blob.contains(alias)) return false
        val extra = blob.replace(alias, " ").split(" ").filter { it.isNotBlank() }
        val ignorable = setOf(
            "de", "con", "al", "a", "la", "el", "un", "una", "del",
            "cocido", "cocida", "crudo", "cruda", "seco", "seca",
        )
        return extra.all { it in ignorable }
    }

    fun resolveFoodId(query: String): String? {
        resolveNode(query)?.foodId?.let { return it }
        val blob = normalizeQuery(query)
        return when {
            blob == "pollo" || blob == "poyo" -> "gen004"
            blob == "carne" -> "gen093"
            detectFamily(query) == Family.WHEY && isProteinSupplementContext(query) -> "gen105"
            else -> null
        }
    }

    fun defaultFoodIdForFamily(query: String): String? = resolveFoodId(query)

    fun householdDefaultGrams(query: String, food: FoodItem? = null): Double? {
        resolveNode(query)?.householdDefaultGrams?.let { return it }
        food?.id?.let { id -> nodes.firstOrNull { it.foodId == id }?.householdDefaultGrams }?.let { return it }
        return null
    }

    fun hasAnchoredPortion(query: String, food: FoodItem? = null): Boolean =
        householdDefaultGrams(query, food) != null

    fun isFamilyDefault(query: String): Boolean {
        val blob = normalizeQuery(query)
        return blob == "pollo" || blob == "carne" || blob == "poyo"
    }

    /**
     * Returns a cut clarification when the query names a family without a cut and
     * the kcal spread between options exceeds [MATERIAL_KCAL_DELTA].
     *
     * @param rawHint original user fragment before typo normalization (e.g. poyo→pollo).
     * @param learnedFoodId user habit for this query; suppresses the chip.
     */
    fun cutClarification(
        query: String,
        rawHint: String? = null,
        learnedFoodId: String? = null,
    ): CutClarification? {
        if (!learnedFoodId.isNullOrBlank()) return null
        val blob = normalizeQuery(query)
        val raw = normalizeQuery(rawHint ?: query)
        if (raw == "poyo" || raw.contains("pechuga") || raw.contains("poyo")) return null
        if (resolveNode(query) != null || resolveNode(raw) != null) return null
        if (blob.split(" ").size > 3) return null
        if (blob != "pollo" && blob != "carne") return null
        val family = detectFamily(query) ?: return null
        val foodIds = ambiguousFamilies[family] ?: return null
        if (family == Family.POLLO && listOf("pechuga", "trutro", "muslo", "ala", "entero").any { blob.contains(it) }) {
            return null
        }
        if (family == Family.VACUNO && listOf("molida", "filete", "bistec", "lomo", "bife", "asado").any { blob.contains(it) }) {
            return null
        }
        val options = foodIds.mapNotNull { id ->
            nodes.firstOrNull { it.foodId == id }?.let { node ->
                StapleCutOption(
                    label = cutLabel(node.cut),
                    foodId = node.foodId,
                    kcalPer100g = node.kcalPer100g,
                )
            }
        }
        if (options.size < 2) return null
        val per100Min = options.minOf { it.kcalPer100g }
        val per100Max = options.maxOf { it.kcalPer100g }
        val householdKcals = options.map { option ->
            val grams = nodes.firstOrNull { it.foodId == option.foodId }?.householdDefaultGrams ?: 100.0
            option.kcalPer100g * grams / 100.0
        }
        val householdMin = householdKcals.min()
        val householdMax = householdKcals.max()
        val per100Delta = if (per100Min > 0.0) (per100Max - per100Min) / per100Min else 0.0
        val householdDelta = if (householdMin > 0.0) (householdMax - householdMin) / householdMin else 0.0
        if (per100Delta < MATERIAL_KCAL_DELTA && householdDelta < MATERIAL_KCAL_DELTA) return null
        return CutClarification(
            family = family,
            options = options,
            defaultFoodId = options.first().foodId,
        )
    }

    fun isKnownCutForFamily(query: String, foodId: String): Boolean {
        val family = detectFamily(query) ?: return false
        return nodes.any { it.family == family && it.foodId == foodId }
    }

    fun cutOf(foodId: String): Cut? = nodes.firstOrNull { it.foodId == foodId }?.cut

    fun isProteinSupplementContext(query: String): Boolean {
        val blob = normalizeQuery(query)
        return supplementMarkers.any { blob.contains(it) }
    }

    fun isProteinFoodContext(query: String): Boolean {
        val blob = normalizeQuery(query)
        return proteinFoodMarkers.any { blob.contains(it) } && !isProteinSupplementContext(query)
    }

    fun shouldMapProteinToWhey(query: String): Boolean = isProteinSupplementContext(query)

    fun shouldAvoidAliasCollapse(aliasKey: String): Boolean =
        aliasKey in setOf("pollo", "carne", "proteina", "proteína", "proteina")

    private fun cutLabel(cut: Cut): String = when (cut) {
        Cut.PECHUGA -> "Pechuga"
        Cut.TRUTRO -> "Trutro"
        Cut.ALA -> "Ala"
        Cut.ENTERO -> "Entero"
        Cut.MOLIDA -> "Molida"
        Cut.BISTEC -> "Bistec"
        Cut.FILETE -> "Filete"
        Cut.AGUA -> "Al agua"
        Cut.ACEITE -> "En aceite"
        Cut.SCOOP -> "Scoop"
        Cut.GENERIC -> "Habitual"
    }
}
