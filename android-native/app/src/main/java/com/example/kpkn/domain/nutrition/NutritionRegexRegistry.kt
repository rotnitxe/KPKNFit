package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod

/**
 * Forces every regex holder of the description pipeline to compile (WP-N4).
 *
 * The pipeline keeps its patterns in objects and in file-level vals, many of them `by lazy`, so a
 * pattern that the Android engine (ICU) rejects would only throw `PatternSyntaxException` the first
 * time a person typed the matching word. [warmUp] touches each holder with a representative input,
 * so a unit test, an instrumented test or an app start can surface the failure at once instead.
 * It has no side effects and needs neither a database nor a Context.
 *
 * Pure Kotlin / JVM: no Android dependency.
 */
object NutritionRegexRegistry {

    private class Holder(val name: String, val touch: () -> Unit)

    private val HOLDERS: List<Holder> = listOf(
        Holder("TextNormalizer") {
            TextNormalizer.normalize("hoy me comí dos huevos poché, medio kilo de pollo a la plancha, 1,5 tazas de arroz y un vaso de agua \uD83E\uDD51")
            TextNormalizer.normalize("two eggs and a cup of oats with honey, no sugar")
            TextNormalizer.canonicalizeDiminutives("huevitos")
            TextNormalizer.normalizeFoodName("pollote")
            TextNormalizer.startsWithNumberWordFoodName("tres leches")
        },
        // File-level patterns of FoodParser.kt, plus everything the parser calls on the way.
        Holder("FoodParser") {
            parseMealDescription("Desayuno: 2 huevos poché. Almorcé 150 g de salmón ahumado con arroz, sin sal, 3 tomates y una porción de pastel de choclo")
        },
        Holder("ContextDetector") { ContextDetector.detect("desayuno en la oficina, un tentempié") },
        Holder("FoodCombinationParser") { FoodCombinationParser.parse("pan con palta y huevo") },
        Holder("LastResortSplitter") { LastResortSplitter.split("arroz y pollo sin sal") },
        Holder("MealLanguageGrammar") { MealLanguageGrammar.detectContainer("una taza de arroz con pollo") },
        Holder("InferredMealContext") { InferredMealContext.inferShape("arroz con pollo y ensalada", listOf("arroz", "pollo")) },
        Holder("SubjectivePortionEngine") {
            SubjectivePortionEngine.resolve("un puñado de almendras")
            SubjectivePortionEngine.detectDensityCategory("jugo de naranja")
        },
        Holder("SubjectivePortionLexicon") { SubjectivePortionLexicon.resolve("dos rebanadas de queso") },
        Holder("HouseholdPortions") {
            HouseholdPortions.isCountable(null, "huevo")
            HouseholdPortions.looksLikeCountExpression("2 huevos")
            HouseholdPortions.isExplicitKilogram("1 kg de papas")
            HouseholdPortions.looksLikePackName("pack 1kg")
        },
        Holder("FoodIdentity") {
            FoodIdentity.familyFor("pechuga de pollo frita")
            FoodIdentity.stateFor("arroz cocido")
            FoodIdentity.declaredAttributes("leche sin lactosa")
        },
        Holder("CookingStateResolver") {
            CookingStateResolver.stateForMethod(CookingMethod.COCIDO)
            CookingStateResolver.methodSearchSuffixes(CookingMethod.FRITO)
        },
        Holder("NutritionHeuristicEstimator") { estimateNutritionByKeyword("pollo frito empanizado") },
        Holder("CookingFactors") { isLikelyLiquid("jugo de naranja") },
        Holder("SemanticPortionRetriever") { SemanticPortionRetriever.repairQuery("polllo con arros") },
        Holder("FoodIndex") {
            FoodIndex.normalizeSearch("Pechuga de Pollo")
            FoodIndex.tokenize("pechuga de pollo")
        },
        Holder("SpanishSingularizer") { SpanishSingularizer.singularize("tomates") },
        Holder("TextKeys") { TextKeys.normalize("Ñoquis") },
    )

    /** Names of the holders [warmUp] touches, in order. */
    fun holderNames(): List<String> = HOLDERS.map { it.name }

    /**
     * Compiles every pattern holder. A pattern the platform engine rejects throws its
     * `PatternSyntaxException` from here. Returns how many holders were touched.
     */
    fun warmUp(): Int {
        HOLDERS.forEach { it.touch() }
        return HOLDERS.size
    }
}
