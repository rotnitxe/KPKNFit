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
                Expectation("huevo", quantity = 2.0, grams = 100.0, intent = AmountIntent.RESOLVED_SUBJECTIVE),
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
        GoldenCase("2 huevos", listOf(Expectation("huevo", quantity = 2.0, grams = 100.0))),
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
        GoldenCase("arroz con pollo", listOf(Expectation("arroz"), Expectation("pollo"))),
        GoldenCase("arroz con mariscos", listOf(Expectation("arroz"), Expectation("mariscos"))),
        GoldenCase("arroz integral", listOf(Expectation("arroz integral"))),
        GoldenCase("arroz con lentejas", listOf(Expectation("arroz"), Expectation("lenteja"))),
        GoldenCase("lentejas con arroz", listOf(Expectation("lenteja"), Expectation("arroz"))),
        GoldenCase("porotos granados", listOf(Expectation("porotos granados"))),
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
        GoldenCase("merluza frita", listOf(Expectation("merluza frita", cooking = CookingMethod.FRITO))),
        GoldenCase("pescado al horno", listOf(Expectation("pescado", cooking = CookingMethod.HORNO))),
        GoldenCase("pescado al vapor", listOf(Expectation("pescado", cooking = CookingMethod.VAPOR))),
        GoldenCase("camarones al ajo", listOf(Expectation("camarones al ajo"))),
        GoldenCase("mariscos al pil pil", listOf(Expectation("mariscos al pil pil"))),

        // ─── E15 · Once y colaciones ────────────────────────────────────────
        GoldenCase("once", listOf(Expectation("once"))),
        GoldenCase("pan con palta", listOf(Expectation("pan con palta"))),
        GoldenCase("marraqueta con mantequilla", listOf(Expectation("marraqueta"), Expectation("mantequilla"))),
        GoldenCase("hallulla con queso", listOf(Expectation("hallulla"), Expectation("queso"))),
        GoldenCase("galletas de avena", listOf(Expectation("galletas de avena", grams = 30.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("galletas de chocolate", listOf(Expectation("galletas de chocolate", grams = 30.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("galletas saladas", listOf(Expectation("galletas saladas", grams = 30.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
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

        // ─── E15 · Medidas y utensilios ─────────────────────────────────────
        GoldenCase("una taza de avena", listOf(Expectation("avena", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("media taza de arroz", listOf(Expectation("arroz", quantity = 0.5, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("2 tazas de leche", listOf(Expectation("leche", quantity = 2.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("tres cucharadas de azúcar", listOf(Expectation("azúcar", quantity = 3.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("dos cucharaditas de sal", listOf(Expectation("sal", quantity = 2.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un plato de arroz", listOf(Expectation("arroz", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un plato grande de sopa", listOf(Expectation("sopa", gramsPositive = true, portion = PortionPreset.LARGE, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un bol de yogurt", listOf(Expectation("yogurt", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("1/4 de taza de avena", listOf(Expectation("avena", quantity = 0.25))),
        GoldenCase("3/4 de taza de leche", listOf(Expectation("leche", quantity = 0.75))),
        GoldenCase("100 ml de leche", listOf(Expectation("leche", grams = 100.0, intent = AmountIntent.EXPLICIT_MASS))),
        GoldenCase("150 g de carne molida", listOf(Expectation("carne molida", grams = 150.0, intent = AmountIntent.EXPLICIT_MASS))),
        GoldenCase("300 ml de agua", listOf(Expectation("agua", grams = 300.0, intent = AmountIntent.EXPLICIT_MASS))),
        GoldenCase("10 g de almendras", listOf(Expectation("almendras", grams = 10.0, intent = AmountIntent.EXPLICIT_MASS))),
        GoldenCase("50 g de queso", listOf(Expectation("queso", grams = 50.0, intent = AmountIntent.EXPLICIT_MASS))),
        GoldenCase("2 litros de agua", listOf(Expectation("agua", grams = 2000.0, intent = AmountIntent.EXPLICIT_MASS))),
        GoldenCase("un litro de leche", listOf(Expectation("leche", grams = 1000.0, intent = AmountIntent.EXPLICIT_MASS))),
        GoldenCase("dos rebanadas de pan", listOf(Expectation("pan", quantity = 2.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una tajada de queque", listOf(Expectation("queque", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un puñado de almendras", listOf(Expectation("almendras", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un puñado de nueces", listOf(Expectation("nueces", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una rodaja de queso", listOf(Expectation("queso", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un trozo de salmón", listOf(Expectation("salmon", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una lata de atún", listOf(Expectation("atún", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un vaso de leche", listOf(Expectation("leche", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("dos vasos de agua", listOf(Expectation("agua", quantity = 2.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una copa de vino", listOf(Expectation("vino", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una cucharada de aceite", listOf(Expectation("aceite", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una cucharada de mantequilla", listOf(Expectation("mantequilla", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("dos cucharadas de queso rallado", listOf(Expectation("queso rallado", quantity = 2.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una pizca de sal", listOf(Expectation("sal", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una pizca de comino", listOf(Expectation("comino", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un chorrito de limón", listOf(Expectation("limón", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un chorrito de vinagre", listOf(Expectation("vinagre", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un poco de azúcar", listOf(Expectation("azúcar", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un montón de arroz", listOf(Expectation("arroz", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un plato generoso de ensalada", listOf(Expectation("ensalada", gramsPositive = true, portion = PortionPreset.EXTRA, intent = AmountIntent.RESOLVED_SUBJECTIVE))),

        // ─── E15 · Cocción y estados ────────────────────────────────────────
        GoldenCase("huevo duro", listOf(Expectation("huevo", cooking = CookingMethod.COCIDO))),
        GoldenCase("huevo pasado por agua", listOf(Expectation("huevo pasado por agua"))),
        GoldenCase("huevo poché", listOf(Expectation("huevo poché"))),
        GoldenCase("huevos a la copa", listOf(Expectation("huevos a la copa"))),
        GoldenCase("pollo sancochado", listOf(Expectation("pollo", cooking = CookingMethod.COCIDO))),
        GoldenCase("pollo hervido", listOf(Expectation("pollo", cooking = CookingMethod.COCIDO))),
        GoldenCase("papas hervidas", listOf(Expectation("papa", cooking = CookingMethod.COCIDO))),
        GoldenCase("verduras al vapor", listOf(Expectation("verduras", cooking = CookingMethod.VAPOR))),
        GoldenCase("brócoli al vapor", listOf(Expectation("brócoli", cooking = CookingMethod.VAPOR))),
        GoldenCase("choclo hervido", listOf(Expectation("choclo", cooking = CookingMethod.COCIDO))),
        GoldenCase("pescado a la parrilla", listOf(Expectation("pescado", cooking = CookingMethod.ASADO_PARRILLA))),
        GoldenCase("anticucho", listOf(Expectation("anticucho"))),
        GoldenCase("salmón ahumado", listOf(Expectation("salmon", cooking = CookingMethod.AHUMADO))),
        GoldenCase("pescado crudo", listOf(Expectation("pescado", cooking = CookingMethod.CRUDO))),
        GoldenCase("pescado fresco", listOf(Expectation("pescado fresco"))),
        GoldenCase("verduras salteadas", listOf(Expectation("verduras", cooking = CookingMethod.FRITO))),
        GoldenCase("champiñones salteados", listOf(Expectation("champiñones", cooking = CookingMethod.FRITO))),
        GoldenCase("cebolla caramelizada", listOf(Expectation("cebolla caramelizada"))),
        GoldenCase("pan tostado", listOf(Expectation("pan tostado"))),
        GoldenCase("marraqueta tostada", listOf(Expectation("marraqueta tostada"))),
        GoldenCase("empanada frita", listOf(Expectation("empanada", cooking = CookingMethod.FRITO))),
        GoldenCase("empanada al horno", listOf(Expectation("empanada", cooking = CookingMethod.HORNO))),
        GoldenCase("sopaipillas pasadas", listOf(Expectation("sopaipillas pasadas"))),
        GoldenCase("manzana al horno", listOf(Expectation("manzana", cooking = CookingMethod.HORNO))),
        GoldenCase("zapallo asado", listOf(Expectation("zapallo", cooking = CookingMethod.ASADO_PARRILLA))),
        GoldenCase("choclo asado", listOf(Expectation("choclo", cooking = CookingMethod.ASADO_PARRILLA))),
        GoldenCase("carne a las brasas", listOf(Expectation("carne a las brasas"))),
        GoldenCase("pollo asado", listOf(Expectation("pollo", cooking = CookingMethod.ASADO_PARRILLA))),
        GoldenCase("cerdo ahumado", listOf(Expectation("cerdo", cooking = CookingMethod.AHUMADO))),
        GoldenCase("fideos saltados", listOf(Expectation("fideos saltados"))),
        GoldenCase("huevo frito con pan", listOf(Expectation("huevo", cooking = CookingMethod.FRITO), Expectation("pan"))),
        GoldenCase("arroz blanco", listOf(Expectation("arroz blanco"))),
        GoldenCase("arroz graneado", listOf(Expectation("arroz graneado"))),
        GoldenCase("pollo al pil pil", listOf(Expectation("pollo al pil pil"))),
        GoldenCase("papas a la huancaína", listOf(Expectation("papa a la huancaína"))),
        GoldenCase("carne mechada", listOf(Expectation("carne mechada"))),
        GoldenCase("tocino frito", listOf(Expectation("tocino", cooking = CookingMethod.FRITO))),

        // ─── E15 · Typos y voz ──────────────────────────────────────────────
        GoldenCase("1 vaso de leche con chocolate", listOf(Expectation("leche con chocolate", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("huevo duro con pan", listOf(Expectation("huevo", cooking = CookingMethod.COCIDO), Expectation("pan"))),
        GoldenCase("pollo al horno con papas", listOf(Expectation("pollo", cooking = CookingMethod.HORNO), Expectation("papa"))),
        GoldenCase("ensaladita", listOf(Expectation("ensalada"))),
        GoldenCase("arros con pollo", listOf(Expectation("arroz"), Expectation("pollo"))),
        GoldenCase("fideos con salsa de tomate", listOf(Expectation("fideos"), Expectation("salsa de tomate"))),
        // CRI-ANALYSIS: reproducción del reporte "no se pudo analizar esta descripción".
        // Un plato común debe parsear sin lanzar: fideos + salsa de tomate + carne molida.
        GoldenCase("fideos con salsa de tomate y un poco de carne molida", listOf(Expectation("fideos"), Expectation("salsa de tomate"), Expectation("carne molida"))),
        GoldenCase("una hallulla con mantequilla", listOf(Expectation("hallulla", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE), Expectation("mantequilla"))),
        GoldenCase("cafecito", listOf(Expectation("cafe"))),
        GoldenCase("juguito de naranja", listOf(Expectation("juguito de naranja"))),
        GoldenCase("pan con queso y tomate", listOf(Expectation("pan con queso"), Expectation("tomate"))),
        GoldenCase("mil hojas", listOf(Expectation("mil hojas"))),
        GoldenCase("cuatro quesos", listOf(Expectation("quesos", quantity = 4.0))),
        GoldenCase("dos quesos", listOf(Expectation("quesos", quantity = 2.0))),

        // ─── E15 · Negaciones ───────────────────────────────────────────────
        GoldenCase("arroz sin sal", listOf(Expectation("arroz"), Expectation("sal", excluded = true))),
        GoldenCase("ensalada sin tomate", listOf(Expectation("ensalada"), Expectation("tomate", excluded = true))),
        GoldenCase("pollo sin piel", listOf(Expectation("pollo"))),
        GoldenCase("queso sin lactosa", listOf(Expectation("queso"), Expectation("lactosa", excluded = true))),
        GoldenCase("leche sin lactosa", listOf(Expectation("leche"), Expectation("lactosa", excluded = true))),
        GoldenCase("pan sin gluten", listOf(Expectation("pan"), Expectation("gluten", excluded = true))),
        GoldenCase("jugo sin azúcar", listOf(Expectation("jugo"), Expectation("azúcar", excluded = true))),

        // ─── E15 · Comidas multi-alimento ───────────────────────────────────
        GoldenCase("arroz con pollo y ensalada", listOf(Expectation("arroz"), Expectation("pollo"), Expectation("ensalada"))),
        GoldenCase("2 huevos con 3 tostadas", listOf(Expectation("huevo", quantity = 2.0), Expectation("tostada", quantity = 3.0))),
        GoldenCase("atún con palta y limón", listOf(Expectation("atún"), Expectation("palta"), Expectation("limón"))),
        GoldenCase("pollo, arroz y ensalada", listOf(Expectation("pollo"), Expectation("arroz"), Expectation("ensalada"))),
        GoldenCase("carne con puré y ensalada", listOf(Expectation("carne"), Expectation("puré"), Expectation("ensalada"))),
        GoldenCase("salmón con papas al horno", listOf(Expectation("salmon"), Expectation("papa", cooking = CookingMethod.HORNO))),
        GoldenCase("lentejas con arroz y ensalada", listOf(Expectation("lenteja"), Expectation("arroz"), Expectation("ensalada"))),
        GoldenCase("yogurt con granola y frutas", listOf(Expectation("yogurt"), Expectation("granola"), Expectation("frutas"))),
        GoldenCase("avena con leche, manzana y canela", listOf(Expectation("avena"), Expectation("leche"), Expectation("manzana"), Expectation("canela"))),
        GoldenCase("huevos revueltos con tomate y pan", listOf(Expectation("huevos", cooking = CookingMethod.FRITO), Expectation("tomate"), Expectation("pan"))),
        GoldenCase("pan con palta y huevo", listOf(Expectation("pan con palta"), Expectation("huevo"))),
        GoldenCase("sopa de pollo con arroz", listOf(Expectation("sopa de pollo"), Expectation("arroz"))),
        GoldenCase("pescado con papas y verduras", listOf(Expectation("pescado"), Expectation("papa"), Expectation("verduras"))),
        GoldenCase("tallarines con carne y salsa", listOf(Expectation("tallarines"), Expectation("carne"), Expectation("salsa"))),
        GoldenCase("ensalada de lechuga, tomate y cebolla", listOf(Expectation("ensalada de lechuga"), Expectation("tomate"), Expectation("cebolla"))),
        GoldenCase("quinoa con pollo y verduras", listOf(Expectation("quinoa"), Expectation("pollo"), Expectation("verduras"))),
        GoldenCase("pollo al horno con papas y zanahorias", listOf(Expectation("pollo", cooking = CookingMethod.HORNO), Expectation("papa"), Expectation("zanahorias"))),
        GoldenCase("hamburguesa con papas fritas", listOf(Expectation("hamburguesa"), Expectation("papa", cooking = CookingMethod.FRITO))),
        GoldenCase("completo con papas fritas", listOf(Expectation("completo"), Expectation("papa", cooking = CookingMethod.FRITO))),
        GoldenCase("arroz con mariscos y limón", listOf(Expectation("arroz"), Expectation("mariscos"), Expectation("limón"))),

        // ─── E15 · Cantidades con números-palabra y más medidas ─────────────
        GoldenCase("un huevo", listOf(Expectation("huevo", quantity = 1.0))),
        GoldenCase("dos plátanos", listOf(Expectation("platano", quantity = 2.0))),
        GoldenCase("tres tomates", listOf(Expectation("tomat", quantity = 3.0))),
        GoldenCase("cuatro zanahorias", listOf(Expectation("zanahoria", quantity = 4.0))),
        GoldenCase("cinco almendras", listOf(Expectation("almendra", quantity = 5.0))),
        GoldenCase("seis galletas", listOf(Expectation("galleta", quantity = 6.0))),
        GoldenCase("diez uvas", listOf(Expectation("uva", quantity = 10.0))),
        GoldenCase("cien gramos de arroz", listOf(Expectation("arroz", grams = 100.0, intent = AmountIntent.EXPLICIT_MASS))),
        GoldenCase("trescientos gramos de pollo", listOf(Expectation("pollo", grams = 300.0, intent = AmountIntent.EXPLICIT_MASS))),
        GoldenCase("una porción de arroz", listOf(Expectation("arroz", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("2 porciones de pasta", listOf(Expectation("pasta", quantity = 2.0, gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una botella de cerveza", listOf(Expectation("cerveza", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una taza de té", listOf(Expectation("té", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una taza de café", listOf(Expectation("café", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("un vaso de vino", listOf(Expectation("vino", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una barra de chocolate", listOf(Expectation("chocolate", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una cucharadita de miel", listOf(Expectation("miel", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("media cucharadita de sal", listOf(Expectation("sal", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),
        GoldenCase("una cucharada de azúcar", listOf(Expectation("azúcar", gramsPositive = true, intent = AmountIntent.RESOLVED_SUBJECTIVE))),

        // ─── E15 · Combinaciones sencillas ──────────────────────────────────
        GoldenCase("huevos con tocino", listOf(Expectation("huevos"), Expectation("tocino"))),
        GoldenCase("pan y huevo", listOf(Expectation("pan"), Expectation("huevo"))),
        GoldenCase("yogurt con miel y nueces", listOf(Expectation("yogurt"), Expectation("miel"), Expectation("nueces"))),
        GoldenCase("avena con leche y plátano", listOf(Expectation("avena"), Expectation("leche"), Expectation("platano"))),
        GoldenCase("ensalada con pollo", listOf(Expectation("ensalada"), Expectation("pollo"))),
        GoldenCase("arroz con huevo", listOf(Expectation("arroz"), Expectation("huevo"))),
        GoldenCase("salmón con brócoli", listOf(Expectation("salmon"), Expectation("brócoli"))),
        GoldenCase("carne con arroz", listOf(Expectation("carne"), Expectation("arroz"))),
        GoldenCase("pollo con champiñones", listOf(Expectation("pollo"), Expectation("champiñones"))),
        GoldenCase("papas con carne", listOf(Expectation("papa"), Expectation("carne"))),
        GoldenCase("queso con jamón", listOf(Expectation("queso"), Expectation("jamón"))),
        GoldenCase("manzana con canela", listOf(Expectation("manzana"), Expectation("canela"))),
        GoldenCase("plátano con avena", listOf(Expectation("platano"), Expectation("avena"))),
        GoldenCase("leche con granola", listOf(Expectation("leche"), Expectation("granola"))),
        GoldenCase("ensalada de frutas", listOf(Expectation("ensalada de frutas"))),
        GoldenCase("ensalada de atún", listOf(Expectation("ensalada de atún"))),
        GoldenCase("ensalada de pollo", listOf(Expectation("ensalada de pollo"))),
        GoldenCase("ensalada de pasta", listOf(Expectation("ensalada de pasta"))),
        GoldenCase("ensalada de papa", listOf(Expectation("ensalada de papa"))),

        // ─── E15 · Más cocción y sustituciones ──────────────────────────────
        GoldenCase("huevo frito", listOf(Expectation("huevo", cooking = CookingMethod.FRITO))),
        GoldenCase("huevo revuelto", listOf(Expectation("huevo", cooking = CookingMethod.FRITO))),
        GoldenCase("pescado frito", listOf(Expectation("pescado frito", cooking = CookingMethod.FRITO))),
        GoldenCase("chorizo a la parrilla", listOf(Expectation("chorizo", cooking = CookingMethod.ASADO_PARRILLA))),
        GoldenCase("2 marraquetas", listOf(Expectation("marraqueta", quantity = 2.0, grams = 200.0))),
        GoldenCase("un completo", listOf(Expectation("completo"))),
        GoldenCase("2 completos", listOf(Expectation("completo", quantity = 2.0))),
        GoldenCase("una empanada", listOf(Expectation("empanada"))),
        GoldenCase("2 empanadas", listOf(Expectation("empanada", quantity = 2.0))),
        GoldenCase("torta", listOf(Expectation("torta"))),
        GoldenCase("batido de frutas", listOf(Expectation("batido de frutas"))),
        GoldenCase("cereal", listOf(Expectation("cereal"))),
        GoldenCase("tequeños", listOf(Expectation("tequeños"))),
        GoldenCase("medialunas", listOf(Expectation("medialunas"))),
        GoldenCase("ceviche", listOf(Expectation("ceviche"))),
        GoldenCase("nuggets", listOf(Expectation("nuggets"))),
        GoldenCase("lomito", listOf(Expectation("lomito"))),
        GoldenCase("huevos con tocino y queso", listOf(Expectation("huevos"), Expectation("tocino"), Expectation("queso"))),
        GoldenCase("palta con huevo", listOf(Expectation("palta"), Expectation("huevo"))),
        GoldenCase("atún con arroz", listOf(Expectation("atún"), Expectation("arroz"))),
        GoldenCase("2 huevos y 100g de avena con leche", listOf(
            Expectation("huevo", quantity = 2.0),
            Expectation("avena", grams = 100.0, intent = AmountIntent.EXPLICIT_MASS),
            Expectation("leche"),
        )),
    )

    @Test
    fun `golden corpus full coverage`() {
        var failures = 0
        val allFailures = StringBuilder()
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
                allFailures.appendLine("CORPUS CASE ${index + 1} [${case.description}]:")
                allFailures.append(failuresForCase)
            }
        }
        assertTrue("$failures/${corpus.size} casos del corpus dorado fallaron\n$allFailures", failures == 0)
    }

    @Test
    fun `raw description preserved`() {
        val desc = "200g pollo y 150g arroz"
        val result = parseMealDescription(desc)
        assertEquals(desc, result.rawDescription)
    }

    @Test
    fun `household unit beats dataset prior on countable foods`() {
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
            assertEquals(100.0, result.items[0].amountGrams!!, 0.01)
        } finally {
            DatasetTestHarness.restore(original)
        }
    }

    @Test
    fun `household unit beats dataset prior on half fruit`() {
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
            assertEquals(50.0, result.items[0].amountGrams!!, 0.01)
        } finally {
            DatasetTestHarness.restore(original)
        }
    }
}
