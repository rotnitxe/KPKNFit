package com.example.kpkn.domain.sessionassistant

/**
 * Curated protected families for Modo Ultrarrápido.
 * Matches on normalized name + equipment. Catalog ids are preferred when available
 * but name fallback keeps it robust across legacy/custom exercises.
 */
object UltraFastConfig {

    // Equipment keys that are considered "barra libre" (free bar)
    val BAR_EQUIPMENT_TOKENS = setOf("barra", "barbell", "zercher_barra", "zercher")

    // Machine keys allowed for superset pairing (polea + smith only)
    val SUPERSET_MACHINE_TOKENS = setOf("polea", "cable", "polea_alta", "polea_baja", "smith", "maquina_smith", "máquina smith", "v_squat")

    // Dangerous / complex tokens (olympic, niche)
    val DANGEROUS_NAME_TOKENS = setOf(
        "snatch", "arrancada",
        "clean", "cargada",
        "jerk", "envion", "envión",
        "good morning", "buenos días", "buenos dias",
        "behind the neck", "tras nuca",
        "sissy", "jefferson", "zercher", // zercher is already protected but keep
        "guillotine",
        "landmine 180", "meadows",
        "z press", "z-press",
        "hiperextension 45", "hiperextensiones 45",
    )

    // Catalog DB ids for protected basics (best-effort, name fallback covers rest)
    val PROTECTED_CATALOG_IDS = setOf(
        // sentadillas
        "quads_sentadilla_barra_alta",
        "quads_sentadilla_barra_baja",
        "quads_sentadilla_frontal_barra",
        "quads_sentadilla_zercher_barra",
        "quads_sentadilla_bulgara_barra",
        "quads_sentadilla_bulgara_mancuernas", // keep but will be filtered by bar check
        // peso muerto
        "hams_peso_muerto_barra",
        "hams_peso_muerto_rumano_barra",
        "hams_peso_muerto_sumo_barra",
        "hams_peso_muerto_zercher_barra",
        "back_peso_muerto_convencional_barra",
        // press banca plano barra
        "pecs_press_banca_plano_barra",
        "tren_superior_press_banca_plano_barra",
    )

    // Name kernels for protected families (lowercase, accent-insensitive handled by contains)
    val SQUAT_KERNELS = setOf("sentadilla")
    val DEADLIFT_KERNELS = setOf("peso muerto", "deadlift")
    val BENCH_KERNELS = setOf("press banca", "bench press")

    fun isSquatFamily(nameLower: String): Boolean = SQUAT_KERNELS.any { it in nameLower }
    fun isDeadliftFamily(nameLower: String): Boolean = DEADLIFT_KERNELS.any { it in nameLower }
    fun isBenchFamily(nameLower: String): Boolean = BENCH_KERNELS.any { it in nameLower }

    // Heuristic: is barbell variant? equipment or name contains "barra" / "barbell"
    fun isBarbellEquipment(equipmentLower: String, nameLower: String): Boolean {
        val eq = equipmentLower.lowercase()
        val nm = nameLower.lowercase()
        return BAR_EQUIPMENT_TOKENS.any { it in eq || it in nm } || "barra" in nm
    }

    fun isSmithEquipment(equipmentLower: String): Boolean {
        val eq = equipmentLower.lowercase()
        return eq.contains("smith") || eq.contains("v_squat")
    }

    fun isPoleaEquipment(equipmentLower: String): Boolean {
        val eq = equipmentLower.lowercase()
        return eq.contains("polea") || eq.contains("cable")
    }

    fun machineKeyForSuperset(equipmentRaw: String?, brandRaw: String?): String? {
        val eq = equipmentRaw?.trim()?.lowercase().orEmpty()
        val brand = brandRaw?.trim()?.lowercase().orEmpty()
        // Only polea/smith generate a key; others return null (no superset)
        val isPolea = isPoleaEquipment(eq) || isPoleaEquipment(brand)
        val isSmith = isSmithEquipment(eq) || isSmithEquipment(brand)
        if (!isPolea && !isSmith) return null
        // Normalize: polea vs smith distinct buckets, brand disambiguates but polea pools together
        val base = when {
            isSmith -> "smith"
            else -> "polea"
        }
        // If brand specified and smith, keep brand to avoid cross-brand smith pairing (optional)
        // For polea, ignore brand (same cable tower usable for many angles)
        return if (base == "smith" && brand.isNotBlank()) "smith:$brand" else base
    }
}
