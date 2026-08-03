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

        // ─── E15 · Desayunos y bebidas ──────────────────────────────────────
        GoldenCase("té con leche", listOf(Expectation("té con leche"))),
        GoldenCase("café negro", listOf(Expectation("café negro"))),
        GoldenCase("café con leche y azúcar", listOf(Expectation("café con leche"), Expectation("azúcar"))),
        GoldenCase("jugo de naranja natural", listOf(Expectation("jugo de naranja natural"))),
        GoldenCase("un vaso de jugo", listOf(Expectation("jugo", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("yogurt con granola", listOf(Expectation("yogurt"), Expectation("granola"))),
        GoldenCase("yogurt griego con miel", listOf(Expectation("yogurt griego"), Expectation("miel"))),
        GoldenCase("avena con leche y canela", listOf(Expectation("avena"), Expectation("leche"), Expectation("canela"))),
        GoldenCase("avena con frutillas", listOf(Expectation("avena"), Expectation("frutillas"))),
        GoldenCase("2 tostadas con palta", listOf(Expectation("tostada", quantity = 2.0), Expectation("palta"))),
        GoldenCase("pan tostado con mantequilla", listOf(Expectation("pan tostado"), Expectation("mantequilla"))),
        GoldenCase("pan integral con queso fresco", listOf(Expectation("pan integral"), Expectation("queso fresco"))),
        GoldenCase("2 huevos revueltos", listOf(Expectation("huevo", quantity = 2.0, cooking = CookingMethod.FRITO))),
        GoldenCase("huevos fritos", listOf(Expectation("huevos", cooking = CookingMethod.FRITO))),
        GoldenCase("claras de huevo", listOf(Expectation("claras de huevo"))),
        GoldenCase("omelette de queso", listOf(Expectation("omelette de queso"))),
        GoldenCase("queso fresco con tomate", listOf(Expectation("queso fresco"), Expectation("tomate"))),
        GoldenCase("palta con limón", listOf(Expectation("palta"), Expectation("limón"))),
        GoldenCase("frutas con yogurt", listOf(Expectation("frutas"), Expectation("yogurt"))),
        GoldenCase("granola con leche", listOf(Expectation("granola"), Expectation("leche"))),
        GoldenCase("leche con chocolate", listOf(Expectation("leche con chocolate"))),
        GoldenCase("té de hierbas", listOf(Expectation("té de hierbas"))),
        GoldenCase("té verde", listOf(Expectation("té verde"))),
        GoldenCase("jugo de manzana", listOf(Expectation("jugo de manzana"))),
        GoldenCase("jugo de piña", listOf(Expectation("jugo de piña"))),
        GoldenCase("licuado de plátano", listOf(Expectation("licuado de platano"))),
        GoldenCase("batido de proteína", listOf(Expectation("batido de proteína"))),
        GoldenCase("leche de almendras", listOf(Expectation("leche de almendras"))),
        GoldenCase("leche descremada", listOf(Expectation("leche descremada"))),
        GoldenCase("capuchino", listOf(Expectation("capuchino"))),

        // ─── E15 · Almuerzos y cenas ────────────────────────────────────────
        GoldenCase("arroz con pollo", listOf(Expectation("arroz con pollo"))),
        GoldenCase("arroz con mariscos", listOf(Expectation("arroz"), Expectation("mariscos"))),
        GoldenCase("arroz integral", listOf(Expectation("arroz integral"))),
        GoldenCase("arroz con lentejas", listOf(Expectation("arroz"), Expectation("lenteja"))),
        GoldenCase("lentejas con arroz", listOf(Expectation("lenteja"), Expectation("arroz"))),
        GoldenCase("porotos granados", listOf(Expectation("poroto granados"))),
        GoldenCase("cazuela de ave", listOf(Expectation("cazuela de ave"))),
        GoldenCase("pastel de choclo", listOf(Expectation("pastel de choclo"))),
        GoldenCase("pastel de papa", listOf(Expectation("pastel de papa"))),
        GoldenCase("sopa de pollo", listOf(Expectation("sopa de pollo"))),
        GoldenCase("sopa de verduras", listOf(Expectation("sopa de verduras"))),
        GoldenCase("crema de zapallo", listOf(Expectation("crema de zapallo"))),
        GoldenCase("ensalada mixta", listOf(Expectation("ensalada mixta"))),
        GoldenCase("ensalada de palta", listOf(Expectation("ensalada de palta"))),
        GoldenCase("ensalada chilena", listOf(Expectation("ensalada chilena"))),
        GoldenCase("pollo al horno", listOf(Expectation("pollo", cooking = CookingMethod.HORNO))),
        GoldenCase("pollo frito", listOf(Expectation("pollo", cooking = CookingMethod.FRITO))),
        GoldenCase("pollo al curry", listOf(Expectation("pollo al curry"))),
        GoldenCase("pollo con papas", listOf(Expectation("pollo"), Expectation("papa"))),
        GoldenCase("carne a la parrilla", listOf(Expectation("carne", cooking = CookingMethod.ASADO_PARRILLA))),
        GoldenCase("lomo saltado", listOf(Expectation("lomo saltado"))),
        GoldenCase("bistec con papas", listOf(Expectation("bistec"), Expectation("papa"))),
        GoldenCase("estofado de vacuno", listOf(Expectation("vacuno", cooking = CookingMethod.COCIDO))),
        GoldenCase("guiso de pollo", listOf(Expectation("pollo", cooking = CookingMethod.GUISADO))),
        GoldenCase("cerdo a la plancha", listOf(Expectation("cerdo", cooking = CookingMethod.PLANCHA))),
        GoldenCase("hamburguesa con queso", listOf(Expectation("hamburguesa con queso"))),
        GoldenCase("pizza napolitana", listOf(Expectation("pizza napolitana"))),
        GoldenCase("tallarines con salsa", listOf(Expectation("tallarines"), Expectation("salsa"))),
        GoldenCase("fideos con atún", listOf(Expectation("fideos"), Expectation("atún"))),
        GoldenCase("espaguetis con albóndigas", listOf(Expectation("espaguetis"), Expectation("albóndigas"))),
        GoldenCase("lasaña", listOf(Expectation("lasaña"))),
        GoldenCase("ravioles con salsa", listOf(Expectation("ravioles"), Expectation("salsa"))),
        GoldenCase("puré de papas", listOf(Expectation("puré de papa"))),
        GoldenCase("puré de zapallo", listOf(Expectation("puré de zapallo"))),
        GoldenCase("papas al horno", listOf(Expectation("papa", cooking = CookingMethod.HORNO))),
        GoldenCase("papas cocidas", listOf(Expectation("papa", cooking = CookingMethod.COCIDO))),
        GoldenCase("papas rústicas", listOf(Expectation("papa rústicas"))),
        GoldenCase("quinua con verduras", listOf(Expectation("quinua"), Expectation("verduras"))),
        GoldenCase("salmón a la plancha", listOf(Expectation("salmon", cooking = CookingMethod.PLANCHA))),
        GoldenCase("atún con palta", listOf(Expectation("atún"), Expectation("palta"))),
        GoldenCase("merluza frita", listOf(Expectation("merluza", cooking = CookingMethod.FRITO))),
        GoldenCase("pescado al horno", listOf(Expectation("pescado", cooking = CookingMethod.HORNO))),
        GoldenCase("pescado al vapor", listOf(Expectation("pescado", cooking = CookingMethod.VAPOR))),
        GoldenCase("camarones al ajo", listOf(Expectation("camarones al ajo"))),
        GoldenCase("mariscos al pil pil", listOf(Expectation("mariscos al pil pil"))),

        // ─── E15 · Once y colaciones ────────────────────────────────────────
        GoldenCase("once", listOf(Expectation("once"))),
        GoldenCase("pan con palta", listOf(Expectation("pan con palta"))),
        GoldenCase("marraqueta con mantequilla", listOf(Expectation("marraqueta"), Expectation("mantequilla"))),
        GoldenCase("hallulla con queso", listOf(Expectation("hallulla"), Expectation("queso"))),
        GoldenCase("galletas de avena", listOf(Expectation("galletas de avena"))),
        GoldenCase("galletas de chocolate", listOf(Expectation("galletas de chocolate"))),
        GoldenCase("galletas saladas", listOf(Expectation("galletas saladas"))),
        GoldenCase("queque de plátano", listOf(Expectation("queque de platano"))),
        GoldenCase("helado de vainilla", listOf(Expectation("helado de vainilla"))),
        GoldenCase("maní", listOf(Expectation("maní"))),
        GoldenCase("almendras", listOf(Expectation("almendras"))),
        GoldenCase("nueces", listOf(Expectation("nueces"))),
        GoldenCase("frutos secos", listOf(Expectation("frutos secos"))),
        GoldenCase("pasas", listOf(Expectation("pasas"))),
        GoldenCase("mantequilla de maní", listOf(Expectation("mantequilla de maní"))),
        GoldenCase("manjar con pan", listOf(Expectation("manjar"), Expectation("pan"))),
        GoldenCase("un trozo de queque", listOf(Expectation("queque", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
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
