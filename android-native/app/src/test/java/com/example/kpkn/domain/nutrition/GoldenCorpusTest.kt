package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Corpus dorado — la red de seguridad del parser.
 *
 * Cada caso es una descripción real (voz, typos, jerga chilena, medidas mixtas) con el
 * resultado esperado. Cualquier mejora del parser debe mantener este corpus verde.
 * Este archivo es SOLO de tests: no forma parte del APK ni del dataset de la app.
 */
class GoldenCorpusTest {

    private data class Expectation(
        val tag: String,
        val quantity: Double = 1.0,
        val grams: Double? = null,
        val gramsPositive: Boolean = false,
        val intent: AmountIntent? = null,
        val portion: PortionPreset? = null,
        val cooking: CookingMethod? = null,
        val excluded: Boolean = false,
    )

    private data class GoldenCase(
        val description: String,
        val expectations: List<Expectation>,
    )

    private val corpus: List<GoldenCase> = listOf(

        // ─── Caso canónico multi-alimento ──────────────────────────────────
        GoldenCase(
            "2 huevos y 100g de avena con leche",
            listOf(
                Expectation("huevo", quantity = 2.0, intent = AmountIntent.UNSPECIFIED),
                Expectation("avena", grams = 100.0, intent = AmountIntent.EXPLICIT_MASS),
                Expectation("leche", intent = AmountIntent.UNSPECIFIED),
            ),
        ),

        // ─── B1: referencias subjetivas con literal "un/una" ───────────────
        GoldenCase(
            "un poco de aceite",
            listOf(
                Expectation("aceite", quantity = 1.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE),
            ),
        ),
        GoldenCase(
            "una pizca de sal",
            listOf(
                Expectation("sal", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE),
            ),
        ),
        GoldenCase(
            "un chorrito de limon",
            listOf(
                Expectation("limon", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE),
            ),
        ),
        GoldenCase(
            "un puñado de almendras",
            listOf(
                Expectation("almendras", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE),
            ),
        ),

        // ─── Cantidades ────────────────────────────────────────────────────
        GoldenCase("2 huevos", listOf(Expectation("huevo", quantity = 2.0, grams = null))),
        GoldenCase("huevos x2", listOf(Expectation("huevos", quantity = 2.0))),
        GoldenCase("3 huevos x2", listOf(Expectation("huevo", quantity = 6.0))),
        GoldenCase("1-2 huevos", listOf(Expectation("huevo", quantity = 1.5))),
        GoldenCase("un par de huevos", listOf(Expectation("huevos", quantity = 2.0))),
        GoldenCase("media marraqueta", listOf(Expectation("marraqueta", quantity = 0.5))),

        // ─── G3: múltiples medidas por fragmento ───────────────────────────
        GoldenCase(
            "100g arroz 50g pollo",
            listOf(
                Expectation("arroz", grams = 100.0, intent = AmountIntent.EXPLICIT_MASS),
                Expectation("pollo", grams = 50.0, intent = AmountIntent.EXPLICIT_MASS),
            ),
        ),

        // ─── G2: separadores ; y salto de línea ────────────────────────────
        GoldenCase(
            "200g pollo; 150g arroz",
            listOf(
                Expectation("pollo", grams = 200.0, intent = AmountIntent.EXPLICIT_MASS),
                Expectation("arroz", grams = 150.0, intent = AmountIntent.EXPLICIT_MASS),
            ),
        ),
        GoldenCase(
            "200g pollo\n150g arroz",
            listOf(
                Expectation("pollo", grams = 200.0, intent = AmountIntent.EXPLICIT_MASS),
                Expectation("arroz", grams = 150.0, intent = AmountIntent.EXPLICIT_MASS),
            ),
        ),

        // ─── Negación ──────────────────────────────────────────────────────
        GoldenCase(
            "café con leche sin azúcar",
            listOf(
                Expectation("café con leche"),
                Expectation("azúcar", excluded = true),
            ),
        ),
        GoldenCase(
            "té sin azúcar",
            listOf(
                Expectation("té"),
                Expectation("azúcar", excluded = true),
            ),
        ),
        GoldenCase(
            "sin azúcar",
            listOf(Expectation("azúcar", excluded = true)),
        ),

        // ─── Voz / typos / emojis ──────────────────────────────────────────
        GoldenCase(
            "2 uebos con arros",
            listOf(
                Expectation("huevo", quantity = 2.0),
                Expectation("arroz"),
            ),
        ),
        GoldenCase(
            "🥑 y 2 huevos",
            listOf(
                Expectation("palta"),
                Expectation("huevo", quantity = 2.0),
            ),
        ),
        GoldenCase(
            "100gr de avena",
            listOf(Expectation("avena", grams = 100.0, intent = AmountIntent.EXPLICIT_MASS)),
        ),
        GoldenCase(
            "medio kilo de arroz",
            listOf(Expectation("arroz", grams = 500.0, intent = AmountIntent.EXPLICIT_MASS)),
        ),
        GoldenCase(
            "2 kilos de papa",
            listOf(Expectation("papa", grams = 2000.0, intent = AmountIntent.EXPLICIT_MASS)),
        ),

        // ─── Referencias de porción ────────────────────────────────────────
        GoldenCase(
            "media taza de avena",
            listOf(Expectation("avena", quantity = 0.5, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
        GoldenCase(
            "dos cucharadas de mantequilla",
            listOf(Expectation("mantequilla", quantity = 2.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
        GoldenCase(
            "una porción de queso",
            listOf(Expectation("queso", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),

        // ─── Porción presets ───────────────────────────────────────────────
        GoldenCase(
            "plato grande de arroz",
            listOf(Expectation("arroz", portion = PortionPreset.LARGE)),
        ),

        // ─── Cocción y entidades protegidas ────────────────────────────────
        GoldenCase(
            "3 huevos revueltos",
            listOf(Expectation("huevo", quantity = 3.0, cooking = CookingMethod.FRITO)),
        ),
        GoldenCase(
            "200g de pechuga de pollo a la plancha",
            listOf(
                Expectation(
                    "pechuga de pollo",
                    grams = 200.0,
                    intent = AmountIntent.EXPLICIT_MASS,
                    cooking = CookingMethod.PLANCHA,
                ),
            ),
        ),
        GoldenCase(
            "2 empanadas de pino",
            listOf(Expectation("empanadas de pino", quantity = 2.0)),
        ),
        GoldenCase(
            "dos panes con palta",
            listOf(
                Expectation("pan", quantity = 2.0),
                Expectation("palta"),
            ),
        ),

        // ─── F1.1: motor subjetivo completo (310+ expresiones) ─────────────
        GoldenCase(
            "un montón de arroz",
            listOf(Expectation("arroz", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
        GoldenCase(
            "una botella de agua",
            listOf(Expectation("agua", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
        GoldenCase(
            "una rodaja de tomate",
            listOf(Expectation("tomate", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
        GoldenCase(
            "un cucharón de sopa",
            listOf(Expectation("sopa", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
        GoldenCase(
            "una hallulla",
            listOf(Expectation("hallulla", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
        GoldenCase(
            "una marraqueta",
            listOf(Expectation("marraqueta", quantity = 1.0, grams = 100.0, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
        GoldenCase(
            "media marraqueta",
            listOf(Expectation("marraqueta", quantity = 0.5, grams = 50.0, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
        GoldenCase(
            "una empanada de pino",
            listOf(Expectation("empanada de pino", quantity = 1.0, grams = null)),
        ),

        // ─── G5: dedupe por clave canónica (tildes + plural) ───────────────
        GoldenCase(
            "2 huevos, 3 huevos",
            listOf(Expectation("huevo", quantity = 5.0)),
        ),
        GoldenCase(
            "una manzana y una manzana",
            listOf(Expectation("manzana", quantity = 2.0)),
        ),

        // ─── F2.1: inglés estructural ──────────────────────────────────────
        GoldenCase(
            "200g chicken with rice",
            listOf(
                Expectation("pollo", grams = 200.0, intent = AmountIntent.EXPLICIT_MASS),
                Expectation("arroz"),
            ),
        ),
        GoldenCase(
            "two eggs and 100 grams of oats with milk",
            listOf(
                Expectation("huevo", quantity = 2.0),
                Expectation("avena", grams = 100.0, intent = AmountIntent.EXPLICIT_MASS),
                Expectation("leche"),
            ),
        ),
        GoldenCase(
            "a cup of oats",
            listOf(Expectation("avena", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
        GoldenCase(
            "half a banana",
            listOf(Expectation("platano", quantity = 0.5)),
        ),
        GoldenCase(
            "coffee without sugar",
            listOf(
                Expectation("cafe"),
                Expectation("azucar", excluded = true),
            ),
        ),
        GoldenCase(
            "one apple",
            listOf(Expectation("manzana", quantity = 1.0)),
        ),
        GoldenCase(
            "a glass of milk",
            listOf(Expectation("leche", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),

        // ─── F2.2: hedges y artículos ──────────────────────────────────────
        GoldenCase(
            "casi medio kilo de arroz",
            listOf(Expectation("arroz", grams = 500.0, intent = AmountIntent.EXPLICIT_MASS)),
        ),
        GoldenCase(
            "lo que sobró de la leche",
            listOf(Expectation("leche")),
        ),

        // ─── Nombres simples ───────────────────────────────────────────────
        GoldenCase("agua", listOf(Expectation("agua"))),

        // ─── Iteración 1: regresiones de la auditoría ──────────────────────
        GoldenCase(
            "arroz 100g pollo 50g",
            listOf(
                Expectation("arroz", grams = 100.0, intent = AmountIntent.EXPLICIT_MASS),
                Expectation("pollo", grams = 50.0, intent = AmountIntent.EXPLICIT_MASS),
            ),
        ),
        GoldenCase(
            "papas fritas",
            listOf(Expectation("papa", cooking = CookingMethod.FRITO)),
        ),
        GoldenCase(
            "tres leches con crema",
            listOf(
                Expectation("leches", quantity = 3.0),
                Expectation("crema"),
            ),
        ),
        GoldenCase(
            "1 cucharadita de azúcar",
            listOf(Expectation("azúcar", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE)),
        ),
    )

    @Test
    fun `golden corpus full coverage`() {
        var failures = 0
        for ((index, case) in corpus.withIndex()) {
            val failuresForCase = StringBuilder()
            val result = parseMealDescription(case.description)

            if (result.items.size != case.expectations.size) {
                failuresForCase.appendLine(
                    "  items esperados=${case.expectations.size} obtenidos=${result.items.size}" +
                        " tags=[${result.items.joinToString(", ") { it.tag }}]",
                )
            } else {
                for ((i, expected) in case.expectations.withIndex()) {
                    val item = result.items[i]
                    val fieldFailures = mutableListOf<String>()
                    if (item.tag != expected.tag) fieldFailures += "tag=${item.tag} (esperado ${expected.tag})"
                    if (item.quantity != expected.quantity) fieldFailures += "quantity=${item.quantity} (esperado ${expected.quantity})"
                    if (expected.grams != null && item.amountGrams != expected.grams) {
                        fieldFailures += "grams=${item.amountGrams} (esperado ${expected.grams})"
                    }
                    if (expected.gramsPositive && (item.amountGrams == null || item.amountGrams <= 0.0)) {
                        fieldFailures += "grams=${item.amountGrams} (esperado > 0)"
                    }
                    if (expected.intent != null && item.amountIntent != expected.intent) {
                        fieldFailures += "intent=${item.amountIntent} (esperado ${expected.intent})"
                    }
                    if (expected.portion != null && item.portion != expected.portion) {
                        fieldFailures += "portion=${item.portion} (esperado ${expected.portion})"
                    }
                    if (expected.cooking != null && item.cookingMethod != expected.cooking) {
                        fieldFailures += "cooking=${item.cookingMethod} (esperado ${expected.cooking})"
                    }
                    if (item.isExcluded != expected.excluded) {
                        fieldFailures += "excluded=${item.isExcluded} (esperado ${expected.excluded})"
                    }
                    if (fieldFailures.isNotEmpty()) {
                        failuresForCase.appendLine("  item[$i]: ${fieldFailures.joinToString(" | ")}")
                    }
                }
            }

            if (failuresForCase.isNotEmpty()) {
                failures++
                System.err.println("CORPUS CASE ${index + 1} [${case.description}]:")
                System.err.print(failuresForCase)
            }
        }
        assertTrue("$failures/${corpus.size} casos del corpus dorado fallaron", failures == 0)
    }

    @Test
    fun `raw description preserved`() {
        val desc = "200g pollo y 150g arroz"
        val result = parseMealDescription(desc)
        assertEquals(desc, result.rawDescription)
    }

    @Test
    fun `dataset prior multiplies by quantity`() {
        val original = SemanticPortionRetriever.currentSnapshot()
        try {
            SemanticPortionRetriever.install(DatasetTestHarness.snapshotFor("huevo", 60.0, "2 huevos", queryToken = "huevos"))
            val dummy = SemanticPortionRetriever.RetrievalResult(
                query = "2 huevos", matches = emptyList(), contextDetected = emptyList(),
                portionPriors = emptyMap(), macroRange = null, confidence = 0.0, elapsedMs = 0,
            )
            val result = parseMealDescription("2 huevos", dummy)
            assertEquals(1, result.items.size)
            assertEquals(2.0, result.items[0].quantity, 0.01)
            assertEquals(120.0, result.items[0].amountGrams!!, 0.01)
        } finally {
            DatasetTestHarness.restore(original)
        }
    }

    @Test
    fun `dataset prior half quantity halves grams`() {
        val original = SemanticPortionRetriever.currentSnapshot()
        try {
            SemanticPortionRetriever.install(DatasetTestHarness.snapshotFor("manzana", 120.0, "una manzana"))
            val dummy = SemanticPortionRetriever.RetrievalResult(
                query = "media manzana", matches = emptyList(), contextDetected = emptyList(),
                portionPriors = emptyMap(), macroRange = null, confidence = 0.0, elapsedMs = 0,
            )
            val result = parseMealDescription("media manzana", dummy)
            assertEquals(1, result.items.size)
            assertEquals(0.5, result.items[0].quantity, 0.01)
            assertEquals(60.0, result.items[0].amountGrams!!, 0.01)
        } finally {
            DatasetTestHarness.restore(original)
        }
    }
}
