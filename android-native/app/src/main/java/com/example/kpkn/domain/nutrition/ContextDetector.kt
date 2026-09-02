package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.MealType

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
        val shape: InferredMealContext.Shape = InferredMealContext.Shape.UNKNOWN,
        val assumedLabel: String? = null,
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
            "desayuno", "desayunar", "mañana", "manana", "al despertar",
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
     * C3: precompilados con word-boundary — "am" matcheaba "jamón"/"camarón" por substring;
     * "trabajo" matcheaba "trabajosa"; "mañana" matcheaba dentro de otras palabras.
     */
    private val CONTEXT_REGEXES: Map<MealContext, List<Regex>> by lazy {
        CONTEXT_PATTERNS.mapValues { (_, keywords) ->
            keywords.map { keyword -> Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE) }
        }
    }

    /**
     * Detect meal context from user description.
     */
    fun detect(
        description: String,
        mealType: MealType? = null,
        foodTags: List<String> = emptyList(),
    ): ContextResult {
        val lower = description.lowercase()
        val detected = mutableListOf<MealContext>()

        for ((context, regexes) in CONTEXT_REGEXES) {
            if (regexes.any { it.containsMatchIn(lower) }) {
                detected.add(context)
            }
        }

        val shape = InferredMealContext.inferShape(description, foodTags)
        val decision = InferredMealContext.combine(detected, mealType, shape)
        val primary = decision.context
        val confidence = when {
            detected.isNotEmpty() -> 0.8
            decision.assumedLabel != null -> 0.62
            else -> 0.5
        }
        val profile = getContextProfile(primary)
        val stableProfiles = SemanticPortionRetriever.contextProfiles()
            .filter { it.sampleCount >= MIN_CONTEXT_SAMPLES }
        val baselineGrams = stableProfiles.map { it.medianGrams }
            .filter { it > 0.0 }
            .medianOrNull()
        val baselineProtein = stableProfiles.map { it.medianProtein }
            .filter { it > 0.0 }
            .medianOrNull()
        val datasetPortionAdjustment = if (profile != null && baselineGrams != null && profile.medianGrams > 0.0) {
            (profile.medianGrams / baselineGrams).coerceIn(MIN_PORTION_FACTOR, MAX_PORTION_FACTOR)
        } else {
            primary.portionFactor
        }
        val datasetProteinAdjustment = if (
            profile != null &&
            baselineProtein != null &&
            profile.medianProtein > 0.0
        ) {
            (profile.medianProtein / baselineProtein - 1.0)
                .coerceIn(MIN_PROTEIN_ADJUSTMENT, MAX_PROTEIN_ADJUSTMENT)
        } else {
            primary.proteinBoost
        }

        return ContextResult(
            primaryContext = primary,
            detectedContexts = detected,
            confidence = confidence,
            portionAdjustment = datasetPortionAdjustment,
            proteinAdjustment = datasetProteinAdjustment,
            shape = decision.shape,
            assumedLabel = decision.assumedLabel,
        )
    }

    /**
     * Adjust portion grams based on detected context.
     */
    fun adjustPortion(grams: Double, context: MealContext): Double {
        return grams * context.portionFactor
    }

    // Nota (plan 2026-08-16, Fase 1): el ajuste de proteína por contexto fue
    // eliminado — el contexto describe porciones probables, nunca muta la
    // densidad por 100 g de una ficha. No re agregar helpers equivalentes.

    /**
     * Get context profile from dataset knowledge.
     */
    fun getContextProfile(context: MealContext): DatasetContextProfile? =
        SemanticPortionRetriever.contextProfile(context.name)

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        return sorted[sorted.size / 2]
    }

    private const val MIN_CONTEXT_SAMPLES = 10
    private const val MIN_PORTION_FACTOR = 0.65
    private const val MAX_PORTION_FACTOR = 1.50
    private const val MIN_PROTEIN_ADJUSTMENT = -0.10
    private const val MAX_PROTEIN_ADJUSTMENT = 0.30
}
