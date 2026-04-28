package com.example.kpkn.domain.nutrition

/**
 * ContextDetector — Detecta contexto implícito en la descripción del usuario.
 *
 * Contextos del dataset:
 * - CASINO (510 ejemplos): porciones estándar de casino
 * - POST_ENTRENO (116): porciones de recuperación (más proteína)
 * - POWERBUILDER (46): porciones masivas
 * - ABUELA_CHILENA (11): porciones generosas caseras
 * - OFICINA (9): snacks rápidos de oficina
 * - ESTUDIANTE (4): comidas baratas y llenadoras
 * - SNACK/COLACIÓN: porciones pequeñas entre comidas
 * - DESAYUNO/ALMUERZO/CENA: porciones por tipo de comida
 *
 * Ajusta factores de porción según contexto detectado.
 */
object ContextDetector {

    enum class MealContext(
        val portionFactor: Double,
        val proteinBoost: Double,
        val label: String,
    ) {
        CASINO(1.0, 0.0, "Casino"),
        POST_ENTRENO(1.1, 0.2, "Post-entreno"),
        POWERBUILDER(1.5, 0.3, "Powerbuilder"),
        ABUELA_CHILENA(1.3, 0.0, "Abuela chilena"),
        OFICINA(0.8, 0.0, "Oficina"),
        ESTUDIANTE(1.1, 0.0, "Estudiante"),
        SNACK(0.5, 0.0, "Snack"),
        DESAYUNO(0.9, 0.1, "Desayuno"),
        ALMUERZO(1.1, 0.0, "Almuerzo"),
        CENA(0.9, 0.0, "Cena"),
        GENERAL(1.0, 0.0, "General"),
    }

    data class ContextResult(
        val primaryContext: MealContext,
        val detectedContexts: List<MealContext>,
        val confidence: Double,
        val portionAdjustment: Double,
        val proteinAdjustment: Double,
    )

    private val CONTEXT_PATTERNS = mapOf(
        MealContext.CASINO to listOf(
            "casino", "cafetería", "cafeteria", "comedor", "buffet", "menú del día", "menu del dia",
            "del casino", "de la cafetería", "del comedor",
        ),
        MealContext.POST_ENTRENO to listOf(
            "post-entreno", "post entreno", "post-entrenamiento", "post entrenamiento",
            "recuperación", "recuperacion", "post-sentadillas", "post-pecho",
            "post-espalda", "post-pierna", "post-brazo", "después de entrenar",
            "despues de entrenar", "post workout", "post-workout",
        ),
        MealContext.POWERBUILDER to listOf(
            "powerbuilder", "power builder", "volumen extremo", "masa extrema",
            "desayuno de powerbuilder", "bulking", "volumen sucio",
        ),
        MealContext.ABUELA_CHILENA to listOf(
            "abuela chilena", "abuela", "contundente", "plato hondo", "tazón grande",
            "plato rebosante", "plato colmado", "plato lleno", "hasta el borde",
            "almuerzo de abuela", "comida de abuela",
        ),
        MealContext.OFICINA to listOf(
            "oficina", "escritorio", "trabajo", "reunión", "reunion", "break de oficina",
            "del trabajo", "en la oficina",
        ),
        MealContext.ESTUDIANTE to listOf(
            "estudiante", "universidad", "facultad", "campus", "barato", "corto de lucas",
            "sobrevivencia", "económico", "economico",
        ),
        MealContext.SNACK to listOf(
            "snack", "colación", "colacion", "merendola", "merienda", "tentempié",
            "tentempie", "piscolabis", "refrigerio", "entre comida", "entre horas",
        ),
        MealContext.DESAYUNO to listOf(
            "desayuno", "desayunar", "am", "mañana", "manana", "al despertar",
            "temprano", "de mañana",
        ),
        MealContext.ALMUERZO to listOf(
            "almuerzo", "almorzar", "mediodía", "mediodia", "del mediodía",
            "comida", "de tarde",
        ),
        MealContext.CENA to listOf(
            "cena", "cenar", "noche", "nocturno", "antes de dormir", "de noche",
            "liviano", "ligero",
        ),
    )

    /**
     * Detect meal context from user description.
     */
    fun detect(description: String): ContextResult {
        val lower = description.lowercase()
        val detected = mutableListOf<MealContext>()

        for ((context, keywords) in CONTEXT_PATTERNS) {
            if (keywords.any { lower.contains(it) }) {
                detected.add(context)
            }
        }

        val primary = detected.firstOrNull() ?: MealContext.GENERAL
        val confidence = if (detected.isEmpty()) 0.5 else 0.8

        return ContextResult(
            primaryContext = primary,
            detectedContexts = detected,
            confidence = confidence,
            portionAdjustment = primary.portionFactor,
            proteinAdjustment = primary.proteinBoost,
        )
    }

    /**
     * Adjust portion grams based on detected context.
     */
    fun adjustPortion(grams: Double, context: MealContext): Double {
        return grams * context.portionFactor
    }

    /**
     * Adjust protein based on context (post-entreno needs more protein).
     */
    fun adjustProtein(protein: Double, context: MealContext): Double {
        return protein * (1.0 + context.proteinBoost)
    }

    /**
     * Get context profile from dataset knowledge.
     */
    fun getContextProfile(context: MealContext): DatasetKnowledgeContext.ContextProfile? {
        val key = context.name
        return DatasetKnowledgeContext.CONTEXT_PROFILES[key]
    }
}
