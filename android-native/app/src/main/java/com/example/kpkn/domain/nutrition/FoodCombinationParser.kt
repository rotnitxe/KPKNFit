package com.example.kpkn.domain.nutrition

/**
 * FoodCombinationParser — Detecta y parsea combinaciones de comida.
 *
 * Estructuras lingüísticas principales:
 * - [A] con [B] → pan con palta, arroz con pollo
 * - [A] de [B] → ensalada de lechuga, tortilla de patatas
 * - [A] y [B] → jamón y queso, arroz y frijoles
 * - [A] a la/el [B] → pollo a la plancha, pescado al horno
 */
object FoodCombinationParser {

    private val CON_Y_COMMA_LEADING_PATTERN = Regex("""^\s*(?:con|y|,)\s*""")
    private val COMBO_SPLIT_PATTERN = Regex("""\s+(?:con|y|,)\s+""")
    private val SANDWICH_DE_Y = Regex(
        """(?:s[aá]ndwich|sandwich)\s+de\s+(.+?)\s+y\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val dishRegexCache = mutableMapOf<String, Regex>()

    data class ParsedCombination(
        val baseFood: String,
        val baseProportion: Double,
        val accompaniments: List<Accompaniment>,
        val cookingMethod: String?,
        val isKnownDish: Boolean,
        val dishName: String?,
        val confidence: Double,
    )

    data class Accompaniment(
        val food: String,
        val proportion: Double,
        val role: Role,
    )

    enum class Role {
        SIDE, STARCH, SAUCE, TOPPING, FILLING, GARNISH
    }

    data class DishComponent(val food: String, val proportion: Double, val role: Role)

    // ─── Platos conocidos con descomposición ───────────────────────────────

    private val KNOWN_DISHES: Map<String, List<DishComponent>> = mapOf(
        "pan con palta" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("palta", 0.6, Role.SIDE)),
        "pan con mantequilla" to listOf(DishComponent("pan", 0.7, Role.STARCH), DishComponent("mantequilla", 0.3, Role.SAUCE)),
        "pan con tomate" to listOf(DishComponent("pan", 0.6, Role.STARCH), DishComponent("tomate", 0.4, Role.SIDE)),
        "pan con queso" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("queso", 0.5, Role.TOPPING)),
        "pan con jamon" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING)),
        "pan con jamón" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING)),
        "pan con huevo" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("huevo", 0.5, Role.TOPPING)),
        "pan con atun" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        "pan con atún" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        "pan con pollo" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING)),
        "pan con salmon" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("salmón", 0.5, Role.TOPPING)),
        "pan con palta y huevo" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("palta", 0.35, Role.SIDE), DishComponent("huevo", 0.3, Role.TOPPING)),
        "pan con palta y jamon" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("palta", 0.35, Role.SIDE), DishComponent("jamón", 0.25, Role.TOPPING)),
        "pan con palta y jamón" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("palta", 0.35, Role.SIDE), DishComponent("jamón", 0.25, Role.TOPPING)),
        "pan con tomate y aceite" to listOf(DishComponent("pan", 0.6, Role.STARCH), DishComponent("tomate", 0.3, Role.SIDE), DishComponent("aceite", 0.1, Role.SAUCE)),
        "pan con tomate y jamon" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("tomate", 0.3, Role.SIDE), DishComponent("jamón", 0.3, Role.TOPPING)),
        "pan con tomate y jamón" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("tomate", 0.3, Role.SIDE), DishComponent("jamón", 0.3, Role.TOPPING)),
        "pan con mantequilla y mermelada" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("mantequilla", 0.25, Role.SAUCE), DishComponent("mermelada", 0.25, Role.SAUCE)),
        "pan con palta y huevo duro" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("palta", 0.35, Role.SIDE), DishComponent("huevo", 0.3, Role.TOPPING)),

        "arroz con pollo" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING)),
        "arroz con huevo" to listOf(DishComponent("arroz", 0.6, Role.STARCH), DishComponent("huevo", 0.4, Role.TOPPING)),
        "arroz con huevo frito" to listOf(DishComponent("arroz", 0.55, Role.STARCH), DishComponent("huevo", 0.45, Role.TOPPING)),
        "arroz con atun" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        "arroz con atún" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        "arroz con verduras" to listOf(DishComponent("arroz", 0.6, Role.STARCH), DishComponent("verduras", 0.4, Role.SIDE)),
        "arroz con frijoles" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("frijoles", 0.5, Role.SIDE)),
        "arroz con porotos" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("porotos", 0.5, Role.SIDE)),
        "arroz con lentejas" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("lentejas", 0.5, Role.SIDE)),
        "arroz con garbanzos" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("garbanzos", 0.5, Role.SIDE)),
        "arroz con carne" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("carne", 0.5, Role.TOPPING)),
        "arroz con pescado" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pescado", 0.5, Role.TOPPING)),
        "arroz con camaron" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("camarón", 0.5, Role.TOPPING)),
        "arroz con camarones" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("camarón", 0.5, Role.TOPPING)),
        "arroz con mariscos" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("mariscos", 0.5, Role.TOPPING)),
        "arroz con chorizo" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("chorizo", 0.5, Role.TOPPING)),
        "arroz con tocino" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("tocino", 0.5, Role.TOPPING)),
        "arroz con leche" to listOf(DishComponent("arroz", 0.4, Role.STARCH), DishComponent("leche", 0.6, Role.SIDE)),
        "arroz con coco" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("coco", 0.5, Role.SIDE)),
        "arroz con curry" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("curry", 0.5, Role.SAUCE)),
        "arroz chaufa" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("verduras", 0.2, Role.SIDE)),
        "arroz chaufa de pollo" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("verduras", 0.2, Role.SIDE)),
        "arroz a la cubana" to listOf(DishComponent("arroz", 0.4, Role.STARCH), DishComponent("huevo frito", 0.3, Role.TOPPING), DishComponent("platano frito", 0.3, Role.SIDE)),
        "arroz tres delicias" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pollo", 0.2, Role.TOPPING), DishComponent("camarón", 0.15, Role.TOPPING), DishComponent("verduras", 0.15, Role.SIDE)),
        "arroz frito con verduras" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("verduras", 0.3, Role.SIDE), DishComponent("huevo", 0.2, Role.TOPPING)),
        "arroz frito con pollo" to listOf(DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("verduras", 0.2, Role.SIDE)),

        "pasta con salsa de tomate" to listOf(DishComponent("pasta", 0.6, Role.STARCH), DishComponent("salsa de tomate", 0.4, Role.SAUCE)),
        "pasta con boloñesa" to listOf(DishComponent("pasta", 0.5, Role.STARCH), DishComponent("carne molida", 0.3, Role.TOPPING), DishComponent("salsa de tomate", 0.2, Role.SAUCE)),
        "pasta con salsa bolonesa" to listOf(DishComponent("pasta", 0.5, Role.STARCH), DishComponent("carne molida", 0.3, Role.TOPPING), DishComponent("salsa de tomate", 0.2, Role.SAUCE)),
        "pasta con pesto" to listOf(DishComponent("pasta", 0.6, Role.STARCH), DishComponent("pesto", 0.4, Role.SAUCE)),
        "pasta con carbonara" to listOf(DishComponent("pasta", 0.5, Role.STARCH), DishComponent("huevo", 0.2, Role.SAUCE), DishComponent("tocino", 0.2, Role.TOPPING), DishComponent("queso", 0.1, Role.TOPPING)),
        "pasta con atun" to listOf(DishComponent("pasta", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        "pasta con atún" to listOf(DishComponent("pasta", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        "pasta con pollo" to listOf(DishComponent("pasta", 0.5, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING)),
        "pasta con carne" to listOf(DishComponent("pasta", 0.5, Role.STARCH), DishComponent("carne", 0.5, Role.TOPPING)),
        "pasta con verduras" to listOf(DishComponent("pasta", 0.6, Role.STARCH), DishComponent("verduras", 0.4, Role.SIDE)),
        "pasta con salmon" to listOf(DishComponent("pasta", 0.5, Role.STARCH), DishComponent("salmón", 0.5, Role.TOPPING)),
        "pasta con camaron" to listOf(DishComponent("pasta", 0.5, Role.STARCH), DishComponent("camarón", 0.5, Role.TOPPING)),
        "pasta con champinones" to listOf(DishComponent("pasta", 0.6, Role.STARCH), DishComponent("champiñones", 0.4, Role.SIDE)),
        "pasta con champiñones" to listOf(DishComponent("pasta", 0.6, Role.STARCH), DishComponent("champiñones", 0.4, Role.SIDE)),
        "pasta con crema" to listOf(DishComponent("pasta", 0.6, Role.STARCH), DishComponent("crema", 0.4, Role.SAUCE)),
        "pasta con queso" to listOf(DishComponent("pasta", 0.6, Role.STARCH), DishComponent("queso", 0.4, Role.TOPPING)),
        "pasta con mantequilla" to listOf(DishComponent("pasta", 0.7, Role.STARCH), DishComponent("mantequilla", 0.3, Role.SAUCE)),

        "huevos fritos con papas" to listOf(DishComponent("huevo", 0.4, Role.TOPPING), DishComponent("papa", 0.6, Role.STARCH)),
        "huevos fritos con patatas" to listOf(DishComponent("huevo", 0.4, Role.TOPPING), DishComponent("papa", 0.6, Role.STARCH)),
        "huevos fritos con chorizo" to listOf(DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("chorizo", 0.5, Role.TOPPING)),
        "huevos fritos con jamon" to listOf(DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("jamón", 0.5, Role.TOPPING)),
        "huevos fritos con jamón" to listOf(DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("jamón", 0.5, Role.TOPPING)),
        "huevos fritos con tocino" to listOf(DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("tocino", 0.5, Role.TOPPING)),
        "huevos fritos con arroz" to listOf(DishComponent("huevo", 0.4, Role.TOPPING), DishComponent("arroz", 0.6, Role.STARCH)),
        "huevos revueltos con jamon" to listOf(DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("jamón", 0.4, Role.TOPPING)),
        "huevos revueltos con jamón" to listOf(DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("jamón", 0.4, Role.TOPPING)),
        "huevos revueltos con queso" to listOf(DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("queso", 0.4, Role.TOPPING)),
        "huevos revueltos con chorizo" to listOf(DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("chorizo", 0.5, Role.TOPPING)),
        "huevos revueltos con tocino" to listOf(DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("tocino", 0.5, Role.TOPPING)),
        "huevos revueltos con verduras" to listOf(DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("verduras", 0.4, Role.SIDE)),
        "huevos revueltos con tomate" to listOf(DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("tomate", 0.4, Role.SIDE)),
        "huevos revueltos con champinones" to listOf(DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("champiñones", 0.4, Role.SIDE)),
        "huevos revueltos con champiñones" to listOf(DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("champiñones", 0.4, Role.SIDE)),
        "huevos revueltos con espinaca" to listOf(DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("espinaca", 0.4, Role.SIDE)),
        "huevos revueltos con espinacas" to listOf(DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("espinacas", 0.4, Role.SIDE)),

        "pollo con papas" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        "pollo con patatas" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        "pollo con arroz" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        "pollo con ensalada" to listOf(DishComponent("pollo", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        "pollo con verduras" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        "pollo con pure" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("puré", 0.5, Role.STARCH)),
        "pollo con pasta" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("pasta", 0.5, Role.STARCH)),
        "pollo con frijoles" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("frijoles", 0.5, Role.SIDE)),
        "pollo con lentejas" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("lentejas", 0.5, Role.SIDE)),
        "pollo con champinones" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("champiñones", 0.5, Role.SIDE)),
        "pollo con champiñones" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("champiñones", 0.5, Role.SIDE)),
        "pollo con curry" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("curry", 0.3, Role.SAUCE), DishComponent("arroz", 0.2, Role.STARCH)),
        "pollo con arroz y ensalada" to listOf(DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("arroz", 0.35, Role.STARCH), DishComponent("ensalada", 0.25, Role.SIDE)),
        "pollo a la plancha con verduras" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        "pollo a la plancha con ensalada" to listOf(DishComponent("pollo", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        "pollo a la plancha con arroz" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        "pollo al horno con papas" to listOf(DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        "pollo guisado con arroz" to listOf(DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("arroz", 0.4, Role.STARCH), DishComponent("salsa", 0.2, Role.SAUCE)),
        "pollo al curry con arroz" to listOf(DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("arroz", 0.4, Role.STARCH), DishComponent("curry", 0.2, Role.SAUCE)),
        "milanesa de pollo con papas fritas" to listOf(DishComponent("pollo empanizado", 0.4, Role.TOPPING), DishComponent("papa frita", 0.6, Role.STARCH)),
        "milanesa de pollo con pure" to listOf(DishComponent("pollo empanizado", 0.5, Role.TOPPING), DishComponent("puré", 0.5, Role.STARCH)),
        "milanesa de pollo con arroz" to listOf(DishComponent("pollo empanizado", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        "milanesa de pollo con ensalada" to listOf(DishComponent("pollo empanizado", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        "milanesa napolitana" to listOf(DishComponent("pollo empanizado", 0.4, Role.TOPPING), DishComponent("salsa de tomate", 0.2, Role.SAUCE), DishComponent("queso", 0.2, Role.TOPPING), DishComponent("jamon", 0.2, Role.TOPPING)),
        "milanesa napolitana con papas" to listOf(DishComponent("pollo empanizado", 0.3, Role.TOPPING), DishComponent("salsa de tomate", 0.15, Role.SAUCE), DishComponent("queso", 0.15, Role.TOPPING), DishComponent("papa", 0.4, Role.STARCH)),

        "bistec con papas" to listOf(DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        "bistec con patatas" to listOf(DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        "bistec con arroz" to listOf(DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        "bistec con ensalada" to listOf(DishComponent("bistec", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        "bistec con pure" to listOf(DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("puré", 0.5, Role.STARCH)),
        "bistec con verduras" to listOf(DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        "bistec con frijoles" to listOf(DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("frijoles", 0.5, Role.SIDE)),
        "bistec a la plancha con ensalada" to listOf(DishComponent("bistec", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        "hamburguesa con papas fritas" to listOf(DishComponent("hamburguesa", 0.5, Role.TOPPING), DishComponent("papa frita", 0.5, Role.STARCH)),
        "hamburguesa con queso" to listOf(DishComponent("hamburguesa", 0.7, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        "hamburguesa con tocino" to listOf(DishComponent("hamburguesa", 0.7, Role.TOPPING), DishComponent("tocino", 0.3, Role.TOPPING)),
        "hamburguesa con huevo" to listOf(DishComponent("hamburguesa", 0.7, Role.TOPPING), DishComponent("huevo", 0.3, Role.TOPPING)),
        "hamburguesa completa" to listOf(DishComponent("hamburguesa", 0.5, Role.TOPPING), DishComponent("papa frita", 0.3, Role.STARCH), DishComponent("bebida", 0.2, Role.SIDE)),

        "pescado con arroz" to listOf(DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        "pescado con papas" to listOf(DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        "pescado con patatas" to listOf(DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        "pescado con pure" to listOf(DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("puré", 0.5, Role.STARCH)),
        "pescado con ensalada" to listOf(DishComponent("pescado", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        "pescado con verduras" to listOf(DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        "pescado a la plancha con ensalada" to listOf(DishComponent("pescado", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        "pescado al horno con papas" to listOf(DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        "pescado frito con arroz" to listOf(DishComponent("pescado frito", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        "salmon con verduras" to listOf(DishComponent("salmón", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        "salmon con arroz" to listOf(DishComponent("salmón", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        "salmon con pure" to listOf(DishComponent("salmón", 0.5, Role.TOPPING), DishComponent("puré", 0.5, Role.STARCH)),
        "salmon con ensalada" to listOf(DishComponent("salmón", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        "salmon a la plancha con verduras" to listOf(DishComponent("salmón", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        "camarones con arroz" to listOf(DishComponent("camarón", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        "camarones con pasta" to listOf(DishComponent("camarón", 0.5, Role.TOPPING), DishComponent("pasta", 0.5, Role.STARCH)),
        "camarones con ensalada" to listOf(DishComponent("camarón", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        "camarones al ajillo con arroz" to listOf(DishComponent("camarón", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),

        "ensalada de lechuga y tomate" to listOf(DishComponent("lechuga", 0.5, Role.SIDE), DishComponent("tomate", 0.5, Role.SIDE)),
        "ensalada de lechuga, tomate y cebolla" to listOf(DishComponent("lechuga", 0.4, Role.SIDE), DishComponent("tomate", 0.35, Role.SIDE), DishComponent("cebolla", 0.25, Role.SIDE)),
        "ensalada cesar con pollo" to listOf(DishComponent("lechuga", 0.4, Role.SIDE), DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("aderezo cesar", 0.2, Role.SAUCE)),
        "ensalada griega con queso feta" to listOf(DishComponent("lechuga", 0.3, Role.SIDE), DishComponent("tomate", 0.2, Role.SIDE), DishComponent("pepino", 0.2, Role.SIDE), DishComponent("queso feta", 0.3, Role.TOPPING)),
        "ensalada de atun con aceitunas" to listOf(DishComponent("atún", 0.4, Role.TOPPING), DishComponent("lechuga", 0.3, Role.SIDE), DishComponent("aceitunas", 0.3, Role.SIDE)),
        "ensalada de papa con mayonesa" to listOf(DishComponent("papa", 0.7, Role.STARCH), DishComponent("mayonesa", 0.3, Role.SAUCE)),
        "ensalada rusa" to listOf(DishComponent("papa", 0.3, Role.STARCH), DishComponent("zanahoria", 0.2, Role.SIDE), DishComponent("arveja", 0.2, Role.SIDE), DishComponent("mayonesa", 0.3, Role.SAUCE)),
        "ensaladilla rusa" to listOf(DishComponent("papa", 0.3, Role.STARCH), DishComponent("zanahoria", 0.2, Role.SIDE), DishComponent("arveja", 0.2, Role.SIDE), DishComponent("mayonesa", 0.3, Role.SAUCE)),

        "sopa de pollo con fideos" to listOf(DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("fideos", 0.3, Role.STARCH), DishComponent("caldo", 0.4, Role.SAUCE)),
        "sopa de pollo con verduras" to listOf(DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("verduras", 0.3, Role.SIDE), DishComponent("caldo", 0.4, Role.SAUCE)),
        "sopa de pollo con arroz" to listOf(DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("arroz", 0.3, Role.STARCH), DishComponent("caldo", 0.4, Role.SAUCE)),
        "caldo de pollo con verduras" to listOf(DishComponent("pollo", 0.25, Role.TOPPING), DishComponent("verduras", 0.35, Role.SIDE), DishComponent("caldo", 0.4, Role.SAUCE)),
        "sopa de lentejas con verduras" to listOf(DishComponent("lentejas", 0.5, Role.SIDE), DishComponent("verduras", 0.3, Role.SIDE), DishComponent("caldo", 0.2, Role.SAUCE)),
        "sopa de lentejas con chorizo" to listOf(DishComponent("lentejas", 0.5, Role.SIDE), DishComponent("chorizo", 0.3, Role.TOPPING), DishComponent("caldo", 0.2, Role.SAUCE)),
        "cazuela de vacuno" to listOf(DishComponent("carne", 0.25, Role.TOPPING), DishComponent("papa", 0.2, Role.STARCH), DishComponent("zapallo", 0.15, Role.SIDE), DishComponent("choclo", 0.1, Role.SIDE), DishComponent("caldo", 0.3, Role.SAUCE)),
        "cazuela de pollo" to listOf(DishComponent("pollo", 0.25, Role.TOPPING), DishComponent("papa", 0.2, Role.STARCH), DishComponent("zapallo", 0.15, Role.SIDE), DishComponent("choclo", 0.1, Role.SIDE), DishComponent("caldo", 0.3, Role.SAUCE)),

        "tortilla de patatas" to listOf(DishComponent("papa", 0.6, Role.STARCH), DishComponent("huevo", 0.4, Role.TOPPING)),
        "tortilla de papas" to listOf(DishComponent("papa", 0.6, Role.STARCH), DishComponent("huevo", 0.4, Role.TOPPING)),
        "tortilla de patatas con cebolla" to listOf(DishComponent("papa", 0.5, Role.STARCH), DishComponent("huevo", 0.35, Role.TOPPING), DishComponent("cebolla", 0.15, Role.SIDE)),
        "tortilla de papas con cebolla" to listOf(DishComponent("papa", 0.5, Role.STARCH), DishComponent("huevo", 0.35, Role.TOPPING), DishComponent("cebolla", 0.15, Role.SIDE)),
        "tortilla de espinaca" to listOf(DishComponent("espinaca", 0.5, Role.SIDE), DishComponent("huevo", 0.5, Role.TOPPING)),
        "tortilla de espinacas" to listOf(DishComponent("espinaca", 0.5, Role.SIDE), DishComponent("huevo", 0.5, Role.TOPPING)),
        "tortilla de atun" to listOf(DishComponent("atún", 0.5, Role.TOPPING), DishComponent("huevo", 0.5, Role.TOPPING)),
        "tortilla de atún" to listOf(DishComponent("atún", 0.5, Role.TOPPING), DishComponent("huevo", 0.5, Role.TOPPING)),
        "tortilla de jamon y queso" to listOf(DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING), DishComponent("huevo", 0.4, Role.TOPPING)),
        "tortilla de jamón y queso" to listOf(DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING), DishComponent("huevo", 0.4, Role.TOPPING)),

        "porotos con riendas" to listOf(DishComponent("porotos", 0.5, Role.SIDE), DishComponent("fideos", 0.3, Role.STARCH), DishComponent("caldo", 0.2, Role.SAUCE)),
        "porotos granados" to listOf(DishComponent("porotos", 0.4, Role.SIDE), DishComponent("choclo", 0.3, Role.SIDE), DishComponent("zapallo", 0.2, Role.SIDE), DishComponent("caldo", 0.1, Role.SAUCE)),
        "charquican" to listOf(DishComponent("carne", 0.2, Role.TOPPING), DishComponent("papa", 0.2, Role.STARCH), DishComponent("zapallo", 0.15, Role.SIDE), DishComponent("choclo", 0.15, Role.SIDE), DishComponent("caldo", 0.3, Role.SAUCE)),
        "pastel de choclo" to listOf(DishComponent("choclo", 0.5, Role.STARCH), DishComponent("carne", 0.3, Role.TOPPING), DishComponent("huevo", 0.1, Role.TOPPING), DishComponent("aceituna", 0.1, Role.SIDE)),
        "humitas" to listOf(DishComponent("choclo", 0.8, Role.STARCH), DishComponent("cebolla", 0.1, Role.SIDE), DishComponent("albahaca", 0.1, Role.GARNISH)),
        "empanada de pino" to listOf(DishComponent("masa", 0.4, Role.STARCH), DishComponent("carne molida", 0.3, Role.TOPPING), DishComponent("cebolla", 0.15, Role.SIDE), DishComponent("huevo", 0.1, Role.TOPPING), DishComponent("aceituna", 0.05, Role.SIDE)),

        "cafe con leche" to listOf(DishComponent("café", 0.3, Role.SIDE), DishComponent("leche", 0.7, Role.SIDE)),
        "café con leche" to listOf(DishComponent("café", 0.3, Role.SIDE), DishComponent("leche", 0.7, Role.SIDE)),
        "cafe con azucar" to listOf(DishComponent("café", 0.8, Role.SIDE), DishComponent("azúcar", 0.2, Role.SAUCE)),
        "café con azúcar" to listOf(DishComponent("café", 0.8, Role.SIDE), DishComponent("azúcar", 0.2, Role.SAUCE)),
        "leche con chocolate" to listOf(DishComponent("leche", 0.7, Role.SIDE), DishComponent("chocolate", 0.3, Role.SAUCE)),
        "yogurt con fruta" to listOf(DishComponent("yogurt", 0.6, Role.SIDE), DishComponent("fruta", 0.4, Role.SIDE)),
        "yogurt con granola" to listOf(DishComponent("yogurt", 0.5, Role.SIDE), DishComponent("granola", 0.5, Role.TOPPING)),
        "avena con leche" to listOf(DishComponent("avena", 0.4, Role.STARCH), DishComponent("leche", 0.6, Role.SIDE)),
        "avena con frutas" to listOf(DishComponent("avena", 0.5, Role.STARCH), DishComponent("fruta", 0.5, Role.SIDE)),
        "avena con platano" to listOf(DishComponent("avena", 0.5, Role.STARCH), DishComponent("plátano", 0.5, Role.SIDE)),
        "avena con banana" to listOf(DishComponent("avena", 0.5, Role.STARCH), DishComponent("plátano", 0.5, Role.SIDE)),
        "avena con manzana" to listOf(DishComponent("avena", 0.5, Role.STARCH), DishComponent("manzana", 0.5, Role.SIDE)),
        "avena con miel" to listOf(DishComponent("avena", 0.7, Role.STARCH), DishComponent("miel", 0.3, Role.SAUCE)),
        "avena con canela" to listOf(DishComponent("avena", 0.9, Role.STARCH), DishComponent("canela", 0.1, Role.GARNISH)),
        "avena con nueces" to listOf(DishComponent("avena", 0.6, Role.STARCH), DishComponent("nueces", 0.4, Role.TOPPING)),
        "avena con almendras" to listOf(DishComponent("avena", 0.6, Role.STARCH), DishComponent("almendras", 0.4, Role.TOPPING)),
        "avena con chia" to listOf(DishComponent("avena", 0.7, Role.STARCH), DishComponent("chía", 0.3, Role.TOPPING)),
        "avena con chía" to listOf(DishComponent("avena", 0.7, Role.STARCH), DishComponent("chía", 0.3, Role.TOPPING)),
        "avena con yogurt" to listOf(DishComponent("avena", 0.5, Role.STARCH), DishComponent("yogurt", 0.5, Role.SIDE)),
        "avena con chocolate" to listOf(DishComponent("avena", 0.6, Role.STARCH), DishComponent("chocolate", 0.4, Role.SAUCE)),
        "granola con yogurt" to listOf(DishComponent("granola", 0.4, Role.TOPPING), DishComponent("yogurt", 0.6, Role.SIDE)),
        "granola con leche" to listOf(DishComponent("granola", 0.4, Role.TOPPING), DishComponent("leche", 0.6, Role.SIDE)),
        "cereal con leche" to listOf(DishComponent("cereal", 0.3, Role.TOPPING), DishComponent("leche", 0.7, Role.SIDE)),

        "fruta con yogurt" to listOf(DishComponent("fruta", 0.5, Role.SIDE), DishComponent("yogurt", 0.5, Role.SIDE)),
        "fruta con crema" to listOf(DishComponent("fruta", 0.6, Role.SIDE), DishComponent("crema", 0.4, Role.SAUCE)),
        "fruta con miel" to listOf(DishComponent("fruta", 0.8, Role.SIDE), DishComponent("miel", 0.2, Role.SAUCE)),
        "fruta con chocolate" to listOf(DishComponent("fruta", 0.6, Role.SIDE), DishComponent("chocolate", 0.4, Role.SAUCE)),
        "fruta con granola" to listOf(DishComponent("fruta", 0.5, Role.SIDE), DishComponent("granola", 0.5, Role.TOPPING)),
        "fresas con crema" to listOf(DishComponent("fresa", 0.6, Role.SIDE), DishComponent("crema", 0.4, Role.SAUCE)),
        "frutillas con crema" to listOf(DishComponent("frutilla", 0.6, Role.SIDE), DishComponent("crema", 0.4, Role.SAUCE)),
        "platano con dulce de leche" to listOf(DishComponent("plátano", 0.6, Role.SIDE), DishComponent("dulce de leche", 0.4, Role.SAUCE)),
        "banana con dulce de leche" to listOf(DishComponent("plátano", 0.6, Role.SIDE), DishComponent("dulce de leche", 0.4, Role.SAUCE)),
        "manzana con canela" to listOf(DishComponent("manzana", 0.9, Role.SIDE), DishComponent("canela", 0.1, Role.GARNISH)),
        "manzana con miel" to listOf(DishComponent("manzana", 0.8, Role.SIDE), DishComponent("miel", 0.2, Role.SAUCE)),
        "melon con jamon" to listOf(DishComponent("melón", 0.6, Role.SIDE), DishComponent("jamón", 0.4, Role.TOPPING)),
        "melon con jamón" to listOf(DishComponent("melón", 0.6, Role.SIDE), DishComponent("jamón", 0.4, Role.TOPPING)),
        "sandia con queso" to listOf(DishComponent("sandía", 0.7, Role.SIDE), DishComponent("queso", 0.3, Role.TOPPING)),
        "sandía con queso" to listOf(DishComponent("sandía", 0.7, Role.SIDE), DishComponent("queso", 0.3, Role.TOPPING)),
        "mango con yogurt" to listOf(DishComponent("mango", 0.5, Role.SIDE), DishComponent("yogurt", 0.5, Role.SIDE)),

        "arroz con leche y canela" to listOf(DishComponent("arroz", 0.4, Role.STARCH), DishComponent("leche", 0.5, Role.SIDE), DishComponent("canela", 0.1, Role.GARNISH)),
        "arroz con leche con pasas" to listOf(DishComponent("arroz", 0.4, Role.STARCH), DishComponent("leche", 0.45, Role.SIDE), DishComponent("pasas", 0.15, Role.TOPPING)),
        "flan con dulce de leche" to listOf(DishComponent("flan", 0.6, Role.SIDE), DishComponent("dulce de leche", 0.4, Role.SAUCE)),
        "flan con crema" to listOf(DishComponent("flan", 0.6, Role.SIDE), DishComponent("crema", 0.4, Role.SAUCE)),
        "helado con chocolate" to listOf(DishComponent("helado", 0.6, Role.SIDE), DishComponent("chocolate", 0.4, Role.SAUCE)),
        "helado con frutas" to listOf(DishComponent("helado", 0.5, Role.SIDE), DishComponent("fruta", 0.5, Role.TOPPING)),
        "helado con nueces" to listOf(DishComponent("helado", 0.6, Role.SIDE), DishComponent("nueces", 0.4, Role.TOPPING)),
        "brownie con helado" to listOf(DishComponent("brownie", 0.5, Role.SIDE), DishComponent("helado", 0.5, Role.TOPPING)),
        "churros con chocolate" to listOf(DishComponent("churros", 0.5, Role.SIDE), DishComponent("chocolate", 0.5, Role.SAUCE)),
        "churros con dulce de leche" to listOf(DishComponent("churros", 0.5, Role.SIDE), DishComponent("dulce de leche", 0.5, Role.SAUCE)),
        "tres leches con crema" to listOf(DishComponent("tres leches", 0.6, Role.SIDE), DishComponent("crema", 0.4, Role.SAUCE)),
        "cheesecake con mermelada" to listOf(DishComponent("cheesecake", 0.7, Role.SIDE), DishComponent("mermelada", 0.3, Role.SAUCE)),
        "cheesecake con frutos rojos" to listOf(DishComponent("cheesecake", 0.6, Role.SIDE), DishComponent("frutos rojos", 0.4, Role.TOPPING)),

        "arepa con queso" to listOf(DishComponent("arepa", 0.5, Role.STARCH), DishComponent("queso", 0.5, Role.TOPPING)),
        "arepa con jamon y queso" to listOf(DishComponent("arepa", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        "arepa con jamón y queso" to listOf(DishComponent("arepa", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        "arepa con carne mechada" to listOf(DishComponent("arepa", 0.4, Role.STARCH), DishComponent("carne mechada", 0.6, Role.TOPPING)),
        "arepa reina pepiada" to listOf(DishComponent("arepa", 0.3, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("palta", 0.35, Role.TOPPING)),
        "arepa con pollo y aguacate" to listOf(DishComponent("arepa", 0.3, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("palta", 0.35, Role.TOPPING)),
        "arepa con huevo" to listOf(DishComponent("arepa", 0.5, Role.STARCH), DishComponent("huevo", 0.5, Role.TOPPING)),
        "arepa con mantequilla" to listOf(DishComponent("arepa", 0.7, Role.STARCH), DishComponent("mantequilla", 0.3, Role.SAUCE)),
        "arepa con pernil" to listOf(DishComponent("arepa", 0.4, Role.STARCH), DishComponent("pernil", 0.6, Role.TOPPING)),
        "arepa con chorizo" to listOf(DishComponent("arepa", 0.4, Role.STARCH), DishComponent("chorizo", 0.6, Role.TOPPING)),
        "arepa con chicharron" to listOf(DishComponent("arepa", 0.4, Role.STARCH), DishComponent("chicharrón", 0.6, Role.TOPPING)),
        "arepa con chicharrón" to listOf(DishComponent("arepa", 0.4, Role.STARCH), DishComponent("chicharrón", 0.6, Role.TOPPING)),

        "taco de carne asada" to listOf(DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("carne", 0.5, Role.TOPPING), DishComponent("salsa", 0.2, Role.SAUCE)),
        "taco de pastor" to listOf(DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("cerdo", 0.5, Role.TOPPING), DishComponent("pina", 0.2, Role.SIDE)),
        "taco de carnitas" to listOf(DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("cerdo", 0.5, Role.TOPPING), DishComponent("salsa", 0.2, Role.SAUCE)),
        "taco de pollo" to listOf(DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("salsa", 0.2, Role.SAUCE)),
        "taco de pescado" to listOf(DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("salsa", 0.2, Role.SAUCE)),
        "taco de camaron" to listOf(DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("camarón", 0.5, Role.TOPPING), DishComponent("salsa", 0.2, Role.SAUCE)),
        "taco de frijoles" to listOf(DishComponent("tortilla", 0.4, Role.STARCH), DishComponent("frijoles", 0.6, Role.TOPPING)),
        "quesadilla con queso" to listOf(DishComponent("tortilla", 0.4, Role.STARCH), DishComponent("queso", 0.6, Role.TOPPING)),
        "quesadilla con pollo" to listOf(DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("queso", 0.2, Role.TOPPING)),
        "burrito de carne" to listOf(DishComponent("tortilla", 0.2, Role.STARCH), DishComponent("carne", 0.3, Role.TOPPING), DishComponent("arroz", 0.2, Role.STARCH), DishComponent("frijoles", 0.2, Role.SIDE), DishComponent("salsa", 0.1, Role.SAUCE)),
        "burrito de pollo" to listOf(DishComponent("tortilla", 0.2, Role.STARCH), DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("arroz", 0.2, Role.STARCH), DishComponent("frijoles", 0.2, Role.SIDE), DishComponent("salsa", 0.1, Role.SAUCE)),
        "enchiladas con pollo" to listOf(DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("salsa", 0.3, Role.SAUCE)),
        "tamal de pollo" to listOf(DishComponent("masa", 0.5, Role.STARCH), DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("salsa", 0.1, Role.SAUCE)),
        "tamal de cerdo" to listOf(DishComponent("masa", 0.5, Role.STARCH), DishComponent("cerdo", 0.4, Role.TOPPING), DishComponent("salsa", 0.1, Role.SAUCE)),

        "sándwich de jamon y queso" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        "sándwich de jamón y queso" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        "sandwich de jamon y queso" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        "sandwich de jamón y queso" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        "sándwich de pavo" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("pavo", 0.5, Role.TOPPING)),
        "sándwich de pollo" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.6, Role.TOPPING)),
        "sándwich de atun" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("atún", 0.6, Role.TOPPING)),
        "sándwich de atún" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("atún", 0.6, Role.TOPPING)),
        "sándwich de vegetales" to listOf(DishComponent("pan", 0.5, Role.STARCH), DishComponent("verduras", 0.5, Role.SIDE)),

        "sándwich de pollo con mayonesa" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.1, Role.SAUCE)),
        "sándwich de pollo con mayo" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.1, Role.SAUCE)),
        "sándwich de pollo con palta" to listOf(DishComponent("pan", 0.3, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("palta", 0.25, Role.SIDE)),
        "sándwich de pollo con lechuga" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("lechuga", 0.15, Role.SIDE)),
        "sándwich de pollo con lechuga y tomate" to listOf(DishComponent("pan", 0.3, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("lechuga", 0.1, Role.SIDE), DishComponent("tomate", 0.15, Role.SIDE)),
        "sándwich de pollo con ketchup" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("ketchup", 0.1, Role.SAUCE)),
        "sándwich de pollo con mostaza" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mostaza", 0.1, Role.SAUCE)),
        "sándwich de pollo con queso" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("queso", 0.2, Role.TOPPING)),
        "sándwich de pollo con jamon" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("jamón", 0.3, Role.TOPPING)),
        "sándwich de pollo con jamón" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("jamón", 0.3, Role.TOPPING)),

        "sándwich de jamon con mayonesa" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        "sándwich de jamón con mayonesa" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        "sándwich de atun con mayonesa" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        "sándwich de atún con mayonesa" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        "sándwich de palta con mayonesa" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("palta", 0.5, Role.SIDE), DishComponent("mayonesa", 0.15, Role.SAUCE)),

        "sandwich de pollo con mayonesa" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.1, Role.SAUCE)),
        "sandwich de pollo con mayo" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.1, Role.SAUCE)),
        "sandwich de pollo con palta" to listOf(DishComponent("pan", 0.3, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("palta", 0.25, Role.SIDE)),
        "sandwich de pollo con lechuga" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("lechuga", 0.15, Role.SIDE)),
        "sandwich de pollo con lechuga y tomate" to listOf(DishComponent("pan", 0.3, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("lechuga", 0.1, Role.SIDE), DishComponent("tomate", 0.15, Role.SIDE)),
        "sandwich de pollo con ketchup" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("ketchup", 0.1, Role.SAUCE)),
        "sandwich de pollo con mostaza" to listOf(DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mostaza", 0.1, Role.SAUCE)),
        "sandwich de pollo con queso" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("queso", 0.2, Role.TOPPING)),
        "sandwich de pollo con jamon" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("jamón", 0.3, Role.TOPPING)),
        "sandwich de pollo con jamón" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("jamón", 0.3, Role.TOPPING)),

        "sandwich de jamon con mayonesa" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        "sandwich de jamón con mayonesa" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        "sandwich de atun con mayonesa" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        "sandwich de atún con mayonesa" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        "sandwich de palta con mayonesa" to listOf(DishComponent("pan", 0.35, Role.STARCH), DishComponent("palta", 0.5, Role.SIDE), DishComponent("mayonesa", 0.15, Role.SAUCE)),

        "hamburguesa con mayonesa" to listOf(DishComponent("hamburguesa", 0.75, Role.TOPPING), DishComponent("mayonesa", 0.25, Role.SAUCE)),
        "hamburguesa con ketchup" to listOf(DishComponent("hamburguesa", 0.75, Role.TOPPING), DishComponent("ketchup", 0.25, Role.SAUCE)),
        "hamburguesa con mostaza" to listOf(DishComponent("hamburguesa", 0.75, Role.TOPPING), DishComponent("mostaza", 0.25, Role.SAUCE)),
        "hamburguesa con queso y tocino" to listOf(DishComponent("hamburguesa", 0.5, Role.TOPPING), DishComponent("queso", 0.25, Role.TOPPING), DishComponent("tocino", 0.25, Role.TOPPING)),
        "hamburguesa con lechuga y tomate" to listOf(DishComponent("hamburguesa", 0.65, Role.TOPPING), DishComponent("lechuga", 0.15, Role.SIDE), DishComponent("tomate", 0.2, Role.SIDE)),

        "papas fritas con mayonesa" to listOf(DishComponent("papa frita", 0.75, Role.STARCH), DishComponent("mayonesa", 0.25, Role.SAUCE)),
        "papas fritas con ketchup" to listOf(DishComponent("papa frita", 0.75, Role.STARCH), DishComponent("ketchup", 0.25, Role.SAUCE)),
        "papas fritas con salsa" to listOf(DishComponent("papa frita", 0.7, Role.STARCH), DishComponent("salsa", 0.3, Role.SAUCE)),
        "papas con mayonesa" to listOf(DishComponent("papa", 0.7, Role.STARCH), DishComponent("mayonesa", 0.3, Role.SAUCE)),
        "papas con salsa" to listOf(DishComponent("papa", 0.7, Role.STARCH), DishComponent("salsa", 0.3, Role.SAUCE)),

        "ceviche con maiz" to listOf(DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("maiz", 0.3, Role.SIDE), DishComponent("limon", 0.2, Role.SAUCE)),
        "ceviche con maiz tostado" to listOf(DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("maiz tostado", 0.3, Role.SIDE), DishComponent("limon", 0.2, Role.SAUCE)),
        "ceviche con camote" to listOf(DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("camote", 0.3, Role.SIDE), DishComponent("limon", 0.2, Role.SAUCE)),
        "ceviche con palta" to listOf(DishComponent("pescado", 0.4, Role.TOPPING), DishComponent("palta", 0.3, Role.SIDE), DishComponent("limon", 0.2, Role.SAUCE), DishComponent("cebolla", 0.1, Role.SIDE)),
        "ceviche con chifles" to listOf(DishComponent("pescado", 0.4, Role.TOPPING), DishComponent("platano frito", 0.4, Role.STARCH), DishComponent("limon", 0.2, Role.SAUCE)),

        "lomo saltado con arroz" to listOf(DishComponent("lomo", 0.35, Role.TOPPING), DishComponent("arroz", 0.35, Role.STARCH), DishComponent("papa frita", 0.3, Role.STARCH)),
        "lomo saltado con papas fritas" to listOf(DishComponent("lomo", 0.4, Role.TOPPING), DishComponent("papa frita", 0.6, Role.STARCH)),
    )

    // ─── Patrones lingüísticos genéricos ───────────────────────────────────

    data class PatternEntry(val regex: Regex, val type: String)

    private val COMBINATION_PATTERNS = listOf(
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+con\s+([a-záéíóúñü\s]+?)(?:\s+y\s+([a-záéíóúñü\s]+?))?$""", RegexOption.IGNORE_CASE), "con_triple"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+con\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "con_doble"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+de\s+([a-záéíóúñü\s]+?)(?:\s+con\s+([a-záéíóúñü\s]+?))?$""", RegexOption.IGNORE_CASE), "de_con"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+de\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "de_doble"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+y\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "y_doble"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+a\s+la\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "a_la"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+al\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "al"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+en\s+salsa\s+de\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "en_salsa_de"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+con\s+salsa\s+de\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "con_salsa_de"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+rellen[oa]\s+de\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "relleno_de"),
    )

    /**
     * Parse a food combination string.
     */
    fun parse(text: String): ParsedCombination {
        val lower = text.lowercase().trim()

        // 1. Check known dishes first (highest confidence).
        // Match por palabras completas: "arroz con leche condensada" NO debe matchear
        // "arroz con leche". El plato solo se acepta al final del texto o seguido de un
        // conector ("con/y/e/mas/a/al/de"). Si varios platos matchean, gana el más largo.
        var bestDishName: String? = null
        var bestDishRegex: Regex? = null
        for ((dishName, _) in KNOWN_DISHES) {
            val regex = dishRegexCache.getOrPut(dishName) {
                Regex(
                    """\b${Regex.escape(dishName)}\b(?=$|\s+(?:con|y|e|mas|más|sin|a|al|de|,)\b)""",
                    RegexOption.IGNORE_CASE,
                )
            }
            if (regex.containsMatchIn(lower) && (bestDishName == null || dishName.length > bestDishName.length)) {
                bestDishName = dishName
                bestDishRegex = regex
            }
        }

        if (bestDishName != null) {
            val components = KNOWN_DISHES.getValue(bestDishName)
            val base = components.first()
            val baseFood = base.food
            val baseProportion = base.proportion
            val accompaniments = components.drop(1).map { comp ->
                Accompaniment(food = comp.food, proportion = comp.proportion, role = comp.role)
            }

            return ParsedCombination(
                baseFood = baseFood,
                baseProportion = baseProportion,
                accompaniments = accompaniments,
                cookingMethod = null,
                isKnownDish = true,
                dishName = bestDishName,
                confidence = 0.95,
            )
        }

        val sandwichGeneric = SANDWICH_DE_Y.find(lower)
        if (sandwichGeneric != null) {
            val fillingA = sandwichGeneric.groupValues[1].trim()
            val fillingB = sandwichGeneric.groupValues[2].trim()
            if (fillingA.isNotBlank() && fillingB.isNotBlank() &&
                !fillingA.contains(" con ") && !fillingB.contains(" con ")
            ) {
                return ParsedCombination(
                    baseFood = "pan",
                    baseProportion = 0.4,
                    accompaniments = listOf(
                        Accompaniment(fillingA, 0.3, inferRole(fillingA)),
                        Accompaniment(fillingB, 0.3, inferRole(fillingB)),
                    ),
                    cookingMethod = null,
                    isKnownDish = true,
                    dishName = "sandwich de $fillingA y $fillingB",
                    confidence = 0.85,
                )
            }
        }

        // 2. Try generic patterns
        for (entry in COMBINATION_PATTERNS) {
            val match = entry.regex.find(lower) ?: continue

            when (entry.type) {
                "con_triple" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    val c = match.groupValues[3].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 0.5,
                        accompaniments = listOf(
                            Accompaniment(b, 0.3, inferRole(b)),
                            Accompaniment(c, 0.2, inferRole(c)),
                        ),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.70,
                    )
                }
                "con_doble" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 0.6,
                        accompaniments = listOf(
                            Accompaniment(b, 0.4, inferRole(b)),
                        ),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.75,
                    )
                }
                "de_con" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    val c = match.groupValues[3].trim()
                    val accs = mutableListOf<Accompaniment>(Accompaniment(a, 0.2, Role.SIDE))
                    if (c.isNotBlank()) accs.add(Accompaniment(c, 0.2, inferRole(c)))
                    return ParsedCombination(
                        baseFood = b,
                        baseProportion = 0.6,
                        accompaniments = accs,
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.70,
                    )
                }
                "de_doble" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = b,
                        baseProportion = 0.7,
                        accompaniments = listOf(Accompaniment(a, 0.3, Role.SIDE)),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.75,
                    )
                }
                "y_doble" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 0.5,
                        accompaniments = listOf(Accompaniment(b, 0.5, inferRole(b))),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.70,
                    )
                }
                "a_la", "al" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 1.0,
                        accompaniments = emptyList(),
                        cookingMethod = b,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.65,
                    )
                }
                "en_salsa_de", "con_salsa_de" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 0.7,
                        accompaniments = listOf(Accompaniment("salsa de $b", 0.3, Role.SAUCE)),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.65,
                    )
                }
                "relleno_de" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 0.5,
                        accompaniments = listOf(Accompaniment(b, 0.5, Role.FILLING)),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.70,
                    )
                }
            }
        }

        // 3. Fallback: single food
        return ParsedCombination(
            baseFood = lower,
            baseProportion = 1.0,
            accompaniments = emptyList(),
            cookingMethod = null,
            isKnownDish = false,
            dishName = null,
            confidence = 0.50,
        )
    }

    private fun inferRole(food: String): Role {
        val lower = food.lowercase()
        return when {
            lower.contains("arroz") || lower.contains("papa") || lower.contains("patata") || lower.contains("pasta") || lower.contains("fideo") || lower.contains("pure") || lower.contains("pan") || lower.contains("tortilla") -> Role.STARCH
            lower.contains("salsa") || lower.contains("mayonesa") || lower.contains("mayo") || lower.contains("ketchup") || lower.contains("catsup") || lower.contains("mostaza") || lower.contains("crema") || lower.contains("aderezo") || lower.contains("vinagreta") || lower.contains("pesto") || lower.contains("chimichurri") || lower.contains("aceite") || lower.contains("oil") || lower.contains("mantequilla") || lower.contains("margarina") || lower.contains("manteca") || lower.contains("ghee") -> Role.SAUCE
            lower.contains("queso") || lower.contains("huevo") || lower.contains("jamon") || lower.contains("tocino") || lower.contains("salchicha") -> Role.TOPPING
            lower.contains("ensalada") || lower.contains("verdura") || lower.contains("lechuga") || lower.contains("tomate") || lower.contains("cebolla") || lower.contains("zanahoria") || lower.contains("brocoli") || lower.contains("espinaca") -> Role.SIDE
            else -> Role.SIDE
        }
    }

    fun splitFoods(text: String): List<String> {
        val lower = text.lowercase().trim()
        for (dishName in KNOWN_DISHES.keys) {
            if (lower == dishName) return listOf(dishName)
        }
        val foods = mutableListOf<String>()
        var remaining = lower
        for (dishName in KNOWN_DISHES.keys) {
            if (remaining.contains(dishName)) {
                foods.add(dishName)
                remaining = remaining.replace(dishName, "").trim()
                remaining = CON_Y_COMMA_LEADING_PATTERN.replace(remaining, "").trim()
            }
        }
        if (remaining.isNotBlank()) {
            val parts = remaining.split(COMBO_SPLIT_PATTERN).map { it.trim() }.filter { it.isNotBlank() }
            foods.addAll(parts)
        }
        return foods.ifEmpty { listOf(lower) }
    }
}
