package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.FOOD_ALIASES_APPROXIMATION
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.system.measureTimeMillis

/**
 * E16 — Contrato de métricas de nutrición (Iteración 1).
 *
 * Umbrales del plan (medidos en CI sobre el pipeline real, sin Room):
 *  - precision@1 de identidad ≥ 95%
 *  - sustituciones auto-confirmadas = 0 (ningún alias aproximado con status AUTO)
 *  - error mediano de gramos ≤ 15%
 *  - idempotencia 3/3 (mismo input → mismo resultado)
 *  - p95 de resolución completa < 50 ms
 *
 * El baseline se imprime siempre: cualquier regresión debe leerse en el log
 * antes de mirar el fallo.
 */
class NutritionMetricsContractTest {

    private class RealPort(
        private val resolver: SmartFoodResolver,
        private val foods: List<FoodItem>,
    ) : FoodResolutionPort {
        override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
            resolver.resolve(tag, brandHint, contextHint, stateHint)
        override suspend fun getFoodById(id: String): FoodItem? = foods.firstOrNull { it.id == id }
        override suspend fun staticFood(tag: String): FoodItem? = findFoodByNormalized(tag)
        override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
        override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
    }

    private class Env(
        val foods: List<FoodItem>,
        val resolver: SmartFoodResolver,
        val port: RealPort,
    )

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao

    private val env: Env by lazy {
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = foods, staticAliases = FOOD_ALIASES)
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        Env(foods, resolver, RealPort(resolver, foods))
    }

    private suspend fun resolve(description: String): List<ResolvedTag> {
        val parsed = parseMealDescription(description)
        val (tags, _) = TagResolver(env.port).resolveAll(parsed)
        return tags
    }

    // ─── Corpus de identidad (descripción → primer tag esperado) ─────────────

    private data class IdentityCase(
        val description: String,
        val expectedTag: String,
        val expectedId: String? = null,
        val expectedStatus: FoodResolutionStatus? = null,
        val nameContains: String? = null,
    )

    private val identityCorpus: List<IdentityCase> = listOf(
        // Casos curados de la auditoría (IT1/IT2)
        IdentityCase("papas fritas", "papa", expectedId = "gen021f", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("manjar", "manjar", expectedId = "gen109", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("torta", "torta", expectedId = "gen019", expectedStatus = FoodResolutionStatus.NEEDS_CONFIRMATION),
        IdentityCase("café con leche", "café con leche", expectedId = "gen016", expectedStatus = FoodResolutionStatus.NEEDS_CONFIRMATION),
        IdentityCase("quesadilla", "quesadilla", expectedStatus = FoodResolutionStatus.NEEDS_CONFIRMATION),
        IdentityCase("fideos", "fideos", expectedId = "gen040h", expectedStatus = FoodResolutionStatus.NEEDS_STATE),
        IdentityCase("200g pollo a la plancha", "pollo", expectedId = "gen003c", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("salmón al horno", "salmon", expectedId = "gen009h", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("atún", "atún", expectedId = "gen029", nameContains = "agua"),
        IdentityCase("atún en aceite", "atún en aceite", expectedId = "gen029e", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("atún al agua", "atún al agua", expectedId = "gen029", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("pasta", "pasta", expectedId = "gen040", expectedStatus = FoodResolutionStatus.NEEDS_STATE),
        IdentityCase("pasta cocida", "pasta", expectedId = "gen040"),
        IdentityCase("cazuela", "cazuela", expectedId = "cl004", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("cazuela de vacuno", "cazuela de vacuno", expectedId = "cl030", expectedStatus = FoodResolutionStatus.AUTO),
        // Identidad de ingredientes comunes
        IdentityCase("arroz", "arroz", expectedId = "gen005", expectedStatus = FoodResolutionStatus.NEEDS_STATE),
        IdentityCase("avena", "avena", expectedId = "gen011", expectedStatus = FoodResolutionStatus.NEEDS_STATE),
        IdentityCase("huevo", "huevo", expectedId = "gen007", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("leche", "leche", expectedId = "gen016", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("palta", "palta", expectedId = "gen014", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("pan", "pan", expectedId = "gen019", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("pechuga de pollo", "pechuga de pollo", expectedId = "gen003", expectedStatus = FoodResolutionStatus.NEEDS_STATE),
        IdentityCase("pollo", "pollo", expectedId = "gen003", expectedStatus = FoodResolutionStatus.NEEDS_STATE),
        IdentityCase("lentejas", "lenteja", expectedId = "gen012", expectedStatus = FoodResolutionStatus.NEEDS_STATE),
        IdentityCase("garbanzos", "garbanzo", expectedId = "gen013", expectedStatus = FoodResolutionStatus.NEEDS_STATE),
        IdentityCase("plátano", "platano", expectedId = "gen002", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("manzana", "manzana", expectedId = "gen001", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("tomate", "tomate", expectedId = "gen026", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("yogurt", "yogurt", expectedId = "gen017", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("té", "té", expectedId = "gen060", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("jugo de naranja", "jugo de naranja", expectedId = "gen103", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("zanahoria", "zanahoria", expectedId = "gen024", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("brócoli", "brócoli", expectedId = "gen022", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("pollo al horno", "pollo", expectedId = "gen003h", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("choclo", "choclo", expectedId = "gen071", expectedStatus = FoodResolutionStatus.AUTO),
        // Sustituciones que deben quedar en revisión
        IdentityCase("ensalada", "ensalada", expectedId = "gen066", expectedStatus = FoodResolutionStatus.NEEDS_CONFIRMATION),
        IdentityCase("milanesa", "milanesa", expectedId = "gen093", expectedStatus = FoodResolutionStatus.NEEDS_CONFIRMATION),
        IdentityCase("batido", "batido", expectedId = "gen016", expectedStatus = FoodResolutionStatus.NEEDS_CONFIRMATION),
        IdentityCase("choripán", "choripán", expectedId = "cl001", expectedStatus = FoodResolutionStatus.NEEDS_CONFIRMATION),
        IdentityCase("galletas", "galletas", expectedId = "gen019", expectedStatus = FoodResolutionStatus.NEEDS_CONFIRMATION),
        // BUGS detectados en el baseline (expectativas de identidad correctas)
        IdentityCase("pan integral", "pan integral", nameContains = "integral"),
        IdentityCase("queso fresco", "queso fresco", expectedId = "gen084", expectedStatus = FoodResolutionStatus.AUTO),
        IdentityCase("sopa", "sopa", nameContains = "sopa"),
        IdentityCase("cereal", "cereal", expectedStatus = FoodResolutionStatus.NEEDS_CONFIRMATION),
        IdentityCase("once", "once", expectedStatus = FoodResolutionStatus.NEEDS_CONFIRMATION),
        IdentityCase("porotos", "poroto", nameContains = "poroto"),
        IdentityCase("arroz integral", "arroz integral", nameContains = "integral"),
    )

    @Test
    fun `E16 baseline y contrato de precision@1 identidad`() = runBlocking {
        var hits = 0
        val misses = mutableListOf<String>()
        for (case in identityCorpus) {
            val tags = resolve(case.description)
            val first = tags.firstOrNull()
            val ok = first != null &&
                first.tag == case.expectedTag &&
                (case.expectedId == null || first.foodItem?.id == case.expectedId) &&
                (case.expectedStatus == null || first.resolutionStatus == case.expectedStatus) &&
                (case.nameContains == null || first.foodItem?.name?.contains(case.nameContains, ignoreCase = true) == true)
            if (ok) {
                hits++
            } else {
                misses += "[${case.description}] → tag=${first?.tag} id=${first?.foodItem?.id} " +
                    "status=${first?.resolutionStatus} name=${first?.foodItem?.name} " +
                    "(esperado: ${case.expectedTag} id=${case.expectedId} status=${case.expectedStatus} name≈${case.nameContains})"
            }
        }
        val precision = hits.toDouble() / identityCorpus.size
        println("BASELINE precision@1 identidad = $hits/${identityCorpus.size} = ${"%.1f".format(precision * 100)}%")
        misses.forEach { println("  MISS $it") }
        assertTrue("precision@1 ≥ 95% (fue ${"%.1f".format(precision * 100)}%, ${misses.size} misses)", precision >= 0.95)
    }

    @Test
    fun `E16 sustituciones auto-confirmadas son cero`() = runBlocking {
        val autoConfirmed = mutableListOf<String>()
        for (alias in FOOD_ALIASES_APPROXIMATION.sorted()) {
            val tags = resolve(alias)
            val first = tags.firstOrNull()
            if (first?.resolutionStatus == FoodResolutionStatus.AUTO && first.isResolved) {
                autoConfirmed += "$alias → ${first.foodItem?.id} (${first.foodItem?.name})"
            }
        }
        println("BASELINE sustituciones auto-confirmadas = ${autoConfirmed.size} de ${FOOD_ALIASES_APPROXIMATION.size} alias")
        autoConfirmed.forEach { println("  AUTO-CONFIRM $it") }
        assertTrue("ningún alias aproximado puede auto-confirmarse: $autoConfirmed", autoConfirmed.isEmpty())
    }

    // ─── Corpus de gramos (sin dependencia del dataset semántico) ─────────────

    private data class GramsCase(val description: String, val expected: Double, val tolerance: Double = 0.15)

    private val gramsCorpus: List<GramsCase> = listOf(
        GramsCase("100g arroz 50g pollo", 100.0),
        GramsCase("200g pechuga de pollo a la plancha", 200.0),
        GramsCase("medio kilo de arroz", 500.0),
        GramsCase("2 kilos de papa", 2000.0),
        GramsCase("1 cucharadita de azúcar", 5.0, tolerance = 0.5),
        GramsCase("dos cucharadas de mantequilla", 30.0, tolerance = 0.35),
        GramsCase("una marraqueta", 100.0, tolerance = 0.25),
        GramsCase("media marraqueta", 50.0, tolerance = 0.25),
        GramsCase("un vaso de leche", 250.0, tolerance = 0.25),
        GramsCase("una botella de agua", 750.0, tolerance = 0.15),
        GramsCase("un puñado de almendras", 30.0, tolerance = 0.35),
        GramsCase("una rodaja de tomate", 30.0, tolerance = 0.35),
        GramsCase("150g arroz 100g pollo", 150.0),
    )

    @Test
    fun `E16 error mediano de gramos menor o igual a 15 por ciento`() = runBlocking {
        val errors = mutableListOf<Double>()
        val fails = mutableListOf<String>()
        for (case in gramsCorpus) {
            val tags = resolve(case.description)
            val grams = tags.firstOrNull()?.amountGrams
            if (grams == null) {
                fails += "[${case.description}] → sin gramos"
                continue
            }
            val relative = abs(grams - case.expected) / case.expected
            errors += relative
            if (relative > case.tolerance) {
                fails += "[${case.description}] → ${grams}g (esperado ${case.expected}g, error ${"%.0f".format(relative * 100)}%)"
            }
        }
        val median = errors.sorted().let { if (it.isEmpty()) 1.0 else it[it.size / 2] }
        println("BASELINE error mediano gramos = ${"%.1f".format(median * 100)}% (n=${errors.size})")
        fails.forEach { println("  GRAMS $it") }
        assertTrue("error mediano ≤ 15% (fue ${"%.1f".format(median * 100)}%)", median <= 0.15)
        assertTrue("casos con gramos sin fallo de tolerancia: $fails", fails.isEmpty())
    }

    @Test
    fun `E16 idempotencia tres de tres`() = runBlocking {
        val desc = "200g pechuga de pollo a la plancha, 150g arroz blanco, ensalada grande, 2 huevos"
        suspend fun signature() = resolve(desc).joinToString("|") {
            "${it.tag}:${it.foodItem?.id}:${it.resolutionStatus}:${it.amountGrams}"
        }
        val first = signature()
        assertEquals(first, signature())
        assertEquals(first, signature())
    }

    @Test
    fun `E16 p95 de resolucion completa bajo 50 ms`() = runBlocking {
        val queries = identityCorpus.map { it.description } + listOf(
            "desayuno con 2 huevos y pan", "arroz con pollo", "cazuela de vacuno con arroz",
        )
        val timings = mutableListOf<Long>()
        repeat(3) {
            for (q in queries) {
                val ms = measureTimeMillis { resolve(q) }
                timings += ms
            }
        }
        val sorted = timings.sorted()
        val p95 = sorted[(sorted.size * 0.95).toInt().coerceIn(0, sorted.lastIndex)]
        println("BASELINE p95 resolución = ${p95}ms (n=${sorted.size}, max=${sorted.last()}ms)")
        assertTrue("p95 < 50 ms (fue ${p95}ms)", p95 < 50)
    }
}
