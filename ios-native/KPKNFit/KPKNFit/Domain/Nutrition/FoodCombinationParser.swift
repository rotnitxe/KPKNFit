import Foundation

/// FoodCombinationParser — Detecta y parsea combinaciones de comida.
///
/// Estructuras lingüísticas principales:
/// - [A] con [B] → pan con palta, arroz con pollo
/// - [A] de [B] → ensalada de lechuga, tortilla de patatas
/// - [A] y [B] → jamón y queso, arroz y frijoles
/// - [A] a la/el [B] → pollo a la plancha, pescado al horno
enum FoodCombinationParser {

    private static let CON_Y_COMMA_LEADING_PATTERN = try! NSRegularExpression(pattern: #"^\s*(?:con|y|,)\s*"#, options: .caseInsensitive)
    private static let COMBO_SPLIT_PATTERN = try! NSRegularExpression(pattern: #"\s+(?:con|y|,)\s+"#, options: .caseInsensitive)

    struct ParsedCombination {
        let baseFood: String
        let baseProportion: Double
        let accompaniments: [Accompaniment]
        let cookingMethod: String?
        let isKnownDish: Bool
        let dishName: String?
        let confidence: Double
    }

    struct Accompaniment {
        let food: String
        let proportion: Double
        let role: Role
    }

    enum Role {
        case side, starch, sauce, topping, filling, garnish
    }

    struct DishComponent {
        let food: String
        let proportion: Double
        let role: Role
    }

    // MARK: - Platos conocidos con descomposición

    private static let KNOWN_DISHES: [(String, [DishComponent])] = [
        ("pan con palta", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "palta", proportion: 0.6, role: .side)]),
        ("pan con mantequilla", [DishComponent(food: "pan", proportion: 0.7, role: .starch), DishComponent(food: "mantequilla", proportion: 0.3, role: .sauce)]),
        ("pan con tomate", [DishComponent(food: "pan", proportion: 0.6, role: .starch), DishComponent(food: "tomate", proportion: 0.4, role: .side)]),
        ("pan con queso", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "queso", proportion: 0.5, role: .topping)]),
        ("pan con jamon", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "jamón", proportion: 0.5, role: .topping)]),
        ("pan con jamón", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "jamón", proportion: 0.5, role: .topping)]),
        ("pan con huevo", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "huevo", proportion: 0.5, role: .topping)]),
        ("pan con atun", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "atún", proportion: 0.5, role: .topping)]),
        ("pan con atún", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "atún", proportion: 0.5, role: .topping)]),
        ("pan con pollo", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping)]),
        ("pan con salmon", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "salmón", proportion: 0.5, role: .topping)]),
        ("pan con palta y huevo", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "palta", proportion: 0.35, role: .side), DishComponent(food: "huevo", proportion: 0.3, role: .topping)]),
        ("pan con tomate y aceite", [DishComponent(food: "pan", proportion: 0.6, role: .starch), DishComponent(food: "tomate", proportion: 0.3, role: .side), DishComponent(food: "aceite", proportion: 0.1, role: .sauce)]),
        ("pan con tomate y jamon", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "tomate", proportion: 0.3, role: .side), DishComponent(food: "jamón", proportion: 0.3, role: .topping)]),
        ("pan con tomate y jamón", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "tomate", proportion: 0.3, role: .side), DishComponent(food: "jamón", proportion: 0.3, role: .topping)]),
        ("pan con mantequilla y mermelada", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "mantequilla", proportion: 0.25, role: .sauce), DishComponent(food: "mermelada", proportion: 0.25, role: .sauce)]),
        ("pan con palta y huevo duro", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "palta", proportion: 0.35, role: .side), DishComponent(food: "huevo", proportion: 0.3, role: .topping)]),

        ("arroz con pollo", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping)]),
        ("arroz con huevo", [DishComponent(food: "arroz", proportion: 0.6, role: .starch), DishComponent(food: "huevo", proportion: 0.4, role: .topping)]),
        ("arroz con huevo frito", [DishComponent(food: "arroz", proportion: 0.55, role: .starch), DishComponent(food: "huevo", proportion: 0.45, role: .topping)]),
        ("arroz con atun", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "atún", proportion: 0.5, role: .topping)]),
        ("arroz con atún", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "atún", proportion: 0.5, role: .topping)]),
        ("arroz con verduras", [DishComponent(food: "arroz", proportion: 0.6, role: .starch), DishComponent(food: "verduras", proportion: 0.4, role: .side)]),
        ("arroz con frijoles", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "frijoles", proportion: 0.5, role: .side)]),
        ("arroz con porotos", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "porotos", proportion: 0.5, role: .side)]),
        ("arroz con lentejas", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "lentejas", proportion: 0.5, role: .side)]),
        ("arroz con garbanzos", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "garbanzos", proportion: 0.5, role: .side)]),
        ("arroz con carne", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "carne", proportion: 0.5, role: .topping)]),
        ("arroz con pescado", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "pescado", proportion: 0.5, role: .topping)]),
        ("arroz con camaron", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "camarón", proportion: 0.5, role: .topping)]),
        ("arroz con camarones", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "camarón", proportion: 0.5, role: .topping)]),
        ("arroz con mariscos", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "mariscos", proportion: 0.5, role: .topping)]),
        ("arroz con chorizo", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "chorizo", proportion: 0.5, role: .topping)]),
        ("arroz con tocino", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "tocino", proportion: 0.5, role: .topping)]),
        ("arroz con leche", [DishComponent(food: "arroz", proportion: 0.4, role: .starch), DishComponent(food: "leche", proportion: 0.6, role: .side)]),
        ("arroz con coco", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "coco", proportion: 0.5, role: .side)]),
        ("arroz con curry", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "curry", proportion: 0.5, role: .sauce)]),
        ("arroz chaufa", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "pollo", proportion: 0.3, role: .topping), DishComponent(food: "verduras", proportion: 0.2, role: .side)]),
        ("arroz chaufa de pollo", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "pollo", proportion: 0.3, role: .topping), DishComponent(food: "verduras", proportion: 0.2, role: .side)]),
        ("arroz a la cubana", [DishComponent(food: "arroz", proportion: 0.4, role: .starch), DishComponent(food: "huevo frito", proportion: 0.3, role: .topping), DishComponent(food: "platano frito", proportion: 0.3, role: .side)]),
        ("arroz tres delicias", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "pollo", proportion: 0.2, role: .topping), DishComponent(food: "camarón", proportion: 0.15, role: .topping), DishComponent(food: "verduras", proportion: 0.15, role: .side)]),
        ("arroz frito con verduras", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "verduras", proportion: 0.3, role: .side), DishComponent(food: "huevo", proportion: 0.2, role: .topping)]),
        ("arroz frito con pollo", [DishComponent(food: "arroz", proportion: 0.5, role: .starch), DishComponent(food: "pollo", proportion: 0.3, role: .topping), DishComponent(food: "verduras", proportion: 0.2, role: .side)]),

        ("pasta con salsa de tomate", [DishComponent(food: "pasta", proportion: 0.6, role: .starch), DishComponent(food: "salsa de tomate", proportion: 0.4, role: .sauce)]),
        ("pasta con boloñesa", [DishComponent(food: "pasta", proportion: 0.5, role: .starch), DishComponent(food: "carne molida", proportion: 0.3, role: .topping), DishComponent(food: "salsa de tomate", proportion: 0.2, role: .sauce)]),
        ("pasta con salsa bolonesa", [DishComponent(food: "pasta", proportion: 0.5, role: .starch), DishComponent(food: "carne molida", proportion: 0.3, role: .topping), DishComponent(food: "salsa de tomate", proportion: 0.2, role: .sauce)]),
        ("pasta con pesto", [DishComponent(food: "pasta", proportion: 0.6, role: .starch), DishComponent(food: "pesto", proportion: 0.4, role: .sauce)]),
        ("pasta con carbonara", [DishComponent(food: "pasta", proportion: 0.5, role: .starch), DishComponent(food: "huevo", proportion: 0.2, role: .sauce), DishComponent(food: "tocino", proportion: 0.2, role: .topping), DishComponent(food: "queso", proportion: 0.1, role: .topping)]),
        ("pasta con atun", [DishComponent(food: "pasta", proportion: 0.5, role: .starch), DishComponent(food: "atún", proportion: 0.5, role: .topping)]),
        ("pasta con atún", [DishComponent(food: "pasta", proportion: 0.5, role: .starch), DishComponent(food: "atún", proportion: 0.5, role: .topping)]),
        ("pasta con pollo", [DishComponent(food: "pasta", proportion: 0.5, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping)]),
        ("pasta con carne", [DishComponent(food: "pasta", proportion: 0.5, role: .starch), DishComponent(food: "carne", proportion: 0.5, role: .topping)]),
        ("pasta con verduras", [DishComponent(food: "pasta", proportion: 0.6, role: .starch), DishComponent(food: "verduras", proportion: 0.4, role: .side)]),
        ("pasta con salmon", [DishComponent(food: "pasta", proportion: 0.5, role: .starch), DishComponent(food: "salmón", proportion: 0.5, role: .topping)]),
        ("pasta con camaron", [DishComponent(food: "pasta", proportion: 0.5, role: .starch), DishComponent(food: "camarón", proportion: 0.5, role: .topping)]),
        ("pasta con champinones", [DishComponent(food: "pasta", proportion: 0.6, role: .starch), DishComponent(food: "champiñones", proportion: 0.4, role: .side)]),
        ("pasta con champiñones", [DishComponent(food: "pasta", proportion: 0.6, role: .starch), DishComponent(food: "champiñones", proportion: 0.4, role: .side)]),
        ("pasta con crema", [DishComponent(food: "pasta", proportion: 0.6, role: .starch), DishComponent(food: "crema", proportion: 0.4, role: .sauce)]),
        ("pasta con queso", [DishComponent(food: "pasta", proportion: 0.6, role: .starch), DishComponent(food: "queso", proportion: 0.4, role: .topping)]),
        ("pasta con mantequilla", [DishComponent(food: "pasta", proportion: 0.7, role: .starch), DishComponent(food: "mantequilla", proportion: 0.3, role: .sauce)]),

        ("huevos fritos con papas", [DishComponent(food: "huevo", proportion: 0.4, role: .topping), DishComponent(food: "papa", proportion: 0.6, role: .starch)]),
        ("huevos fritos con patatas", [DishComponent(food: "huevo", proportion: 0.4, role: .topping), DishComponent(food: "papa", proportion: 0.6, role: .starch)]),
        ("huevos fritos con chorizo", [DishComponent(food: "huevo", proportion: 0.5, role: .topping), DishComponent(food: "chorizo", proportion: 0.5, role: .topping)]),
        ("huevos fritos con jamon", [DishComponent(food: "huevo", proportion: 0.5, role: .topping), DishComponent(food: "jamón", proportion: 0.5, role: .topping)]),
        ("huevos fritos con jamón", [DishComponent(food: "huevo", proportion: 0.5, role: .topping), DishComponent(food: "jamón", proportion: 0.5, role: .topping)]),
        ("huevos fritos con tocino", [DishComponent(food: "huevo", proportion: 0.5, role: .topping), DishComponent(food: "tocino", proportion: 0.5, role: .topping)]),
        ("huevos fritos con arroz", [DishComponent(food: "huevo", proportion: 0.4, role: .topping), DishComponent(food: "arroz", proportion: 0.6, role: .starch)]),
        ("huevos revueltos con jamon", [DishComponent(food: "huevo", proportion: 0.6, role: .topping), DishComponent(food: "jamón", proportion: 0.4, role: .topping)]),
        ("huevos revueltos con jamón", [DishComponent(food: "huevo", proportion: 0.6, role: .topping), DishComponent(food: "jamón", proportion: 0.4, role: .topping)]),
        ("huevos revueltos con queso", [DishComponent(food: "huevo", proportion: 0.6, role: .topping), DishComponent(food: "queso", proportion: 0.4, role: .topping)]),
        ("huevos revueltos con chorizo", [DishComponent(food: "huevo", proportion: 0.5, role: .topping), DishComponent(food: "chorizo", proportion: 0.5, role: .topping)]),
        ("huevos revueltos con tocino", [DishComponent(food: "huevo", proportion: 0.5, role: .topping), DishComponent(food: "tocino", proportion: 0.5, role: .topping)]),
        ("huevos revueltos con verduras", [DishComponent(food: "huevo", proportion: 0.6, role: .topping), DishComponent(food: "verduras", proportion: 0.4, role: .side)]),
        ("huevos revueltos con tomate", [DishComponent(food: "huevo", proportion: 0.6, role: .topping), DishComponent(food: "tomate", proportion: 0.4, role: .side)]),
        ("huevos revueltos con champinones", [DishComponent(food: "huevo", proportion: 0.6, role: .topping), DishComponent(food: "champiñones", proportion: 0.4, role: .side)]),
        ("huevos revueltos con champiñones", [DishComponent(food: "huevo", proportion: 0.6, role: .topping), DishComponent(food: "champiñones", proportion: 0.4, role: .side)]),
        ("huevos revueltos con espinaca", [DishComponent(food: "huevo", proportion: 0.6, role: .topping), DishComponent(food: "espinaca", proportion: 0.4, role: .side)]),
        ("huevos revueltos con espinacas", [DishComponent(food: "huevo", proportion: 0.6, role: .topping), DishComponent(food: "espinacas", proportion: 0.4, role: .side)]),

        ("pollo con papas", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "papa", proportion: 0.5, role: .starch)]),
        ("pollo con patatas", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "papa", proportion: 0.5, role: .starch)]),
        ("pollo con arroz", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "arroz", proportion: 0.5, role: .starch)]),
        ("pollo con ensalada", [DishComponent(food: "pollo", proportion: 0.6, role: .topping), DishComponent(food: "ensalada", proportion: 0.4, role: .side)]),
        ("pollo con verduras", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "verduras", proportion: 0.5, role: .side)]),
        ("pollo con pure", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "puré", proportion: 0.5, role: .starch)]),
        ("pollo con pasta", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "pasta", proportion: 0.5, role: .starch)]),
        ("pollo con frijoles", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "frijoles", proportion: 0.5, role: .side)]),
        ("pollo con lentejas", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "lentejas", proportion: 0.5, role: .side)]),
        ("pollo con champinones", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "champiñones", proportion: 0.5, role: .side)]),
        ("pollo con champiñones", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "champiñones", proportion: 0.5, role: .side)]),
        ("pollo con curry", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "curry", proportion: 0.3, role: .sauce), DishComponent(food: "arroz", proportion: 0.2, role: .starch)]),
        ("pollo con arroz y ensalada", [DishComponent(food: "pollo", proportion: 0.4, role: .topping), DishComponent(food: "arroz", proportion: 0.35, role: .starch), DishComponent(food: "ensalada", proportion: 0.25, role: .side)]),
        ("pollo a la plancha con verduras", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "verduras", proportion: 0.5, role: .side)]),
        ("pollo a la plancha con ensalada", [DishComponent(food: "pollo", proportion: 0.6, role: .topping), DishComponent(food: "ensalada", proportion: 0.4, role: .side)]),
        ("pollo a la plancha con arroz", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "arroz", proportion: 0.5, role: .starch)]),
        ("pollo al horno con papas", [DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "papa", proportion: 0.5, role: .starch)]),
        ("pollo guisado con arroz", [DishComponent(food: "pollo", proportion: 0.4, role: .topping), DishComponent(food: "arroz", proportion: 0.4, role: .starch), DishComponent(food: "salsa", proportion: 0.2, role: .sauce)]),
        ("pollo al curry con arroz", [DishComponent(food: "pollo", proportion: 0.4, role: .topping), DishComponent(food: "arroz", proportion: 0.4, role: .starch), DishComponent(food: "curry", proportion: 0.2, role: .sauce)]),
        ("milanesa de pollo con papas fritas", [DishComponent(food: "pollo empanizado", proportion: 0.4, role: .topping), DishComponent(food: "papa frita", proportion: 0.6, role: .starch)]),
        ("milanesa de pollo con pure", [DishComponent(food: "pollo empanizado", proportion: 0.5, role: .topping), DishComponent(food: "puré", proportion: 0.5, role: .starch)]),
        ("milanesa de pollo con arroz", [DishComponent(food: "pollo empanizado", proportion: 0.5, role: .topping), DishComponent(food: "arroz", proportion: 0.5, role: .starch)]),
        ("milanesa de pollo con ensalada", [DishComponent(food: "pollo empanizado", proportion: 0.6, role: .topping), DishComponent(food: "ensalada", proportion: 0.4, role: .side)]),
        ("milanesa napolitana", [DishComponent(food: "pollo empanizado", proportion: 0.4, role: .topping), DishComponent(food: "salsa de tomate", proportion: 0.2, role: .sauce), DishComponent(food: "queso", proportion: 0.2, role: .topping), DishComponent(food: "jamon", proportion: 0.2, role: .topping)]),
        ("milanesa napolitana con papas", [DishComponent(food: "pollo empanizado", proportion: 0.3, role: .topping), DishComponent(food: "salsa de tomate", proportion: 0.15, role: .sauce), DishComponent(food: "queso", proportion: 0.15, role: .topping), DishComponent(food: "papa", proportion: 0.4, role: .starch)]),

        ("bistec con papas", [DishComponent(food: "bistec", proportion: 0.5, role: .topping), DishComponent(food: "papa", proportion: 0.5, role: .starch)]),
        ("bistec con patatas", [DishComponent(food: "bistec", proportion: 0.5, role: .topping), DishComponent(food: "papa", proportion: 0.5, role: .starch)]),
        ("bistec con arroz", [DishComponent(food: "bistec", proportion: 0.5, role: .topping), DishComponent(food: "arroz", proportion: 0.5, role: .starch)]),
        ("bistec con ensalada", [DishComponent(food: "bistec", proportion: 0.6, role: .topping), DishComponent(food: "ensalada", proportion: 0.4, role: .side)]),
        ("bistec con pure", [DishComponent(food: "bistec", proportion: 0.5, role: .topping), DishComponent(food: "puré", proportion: 0.5, role: .starch)]),
        ("bistec con verduras", [DishComponent(food: "bistec", proportion: 0.5, role: .topping), DishComponent(food: "verduras", proportion: 0.5, role: .side)]),
        ("bistec con frijoles", [DishComponent(food: "bistec", proportion: 0.5, role: .topping), DishComponent(food: "frijoles", proportion: 0.5, role: .side)]),
        ("bistec a la plancha con ensalada", [DishComponent(food: "bistec", proportion: 0.6, role: .topping), DishComponent(food: "ensalada", proportion: 0.4, role: .side)]),
        ("hamburguesa con papas fritas", [DishComponent(food: "hamburguesa", proportion: 0.5, role: .topping), DishComponent(food: "papa frita", proportion: 0.5, role: .starch)]),
        ("hamburguesa con queso", [DishComponent(food: "hamburguesa", proportion: 0.7, role: .topping), DishComponent(food: "queso", proportion: 0.3, role: .topping)]),
        ("hamburguesa con tocino", [DishComponent(food: "hamburguesa", proportion: 0.7, role: .topping), DishComponent(food: "tocino", proportion: 0.3, role: .topping)]),
        ("hamburguesa con huevo", [DishComponent(food: "hamburguesa", proportion: 0.7, role: .topping), DishComponent(food: "huevo", proportion: 0.3, role: .topping)]),
        ("hamburguesa completa", [DishComponent(food: "hamburguesa", proportion: 0.5, role: .topping), DishComponent(food: "papa frita", proportion: 0.3, role: .starch), DishComponent(food: "bebida", proportion: 0.2, role: .side)]),

        ("pescado con arroz", [DishComponent(food: "pescado", proportion: 0.5, role: .topping), DishComponent(food: "arroz", proportion: 0.5, role: .starch)]),
        ("pescado con papas", [DishComponent(food: "pescado", proportion: 0.5, role: .topping), DishComponent(food: "papa", proportion: 0.5, role: .starch)]),
        ("pescado con patatas", [DishComponent(food: "pescado", proportion: 0.5, role: .topping), DishComponent(food: "papa", proportion: 0.5, role: .starch)]),
        ("pescado con pure", [DishComponent(food: "pescado", proportion: 0.5, role: .topping), DishComponent(food: "puré", proportion: 0.5, role: .starch)]),
        ("pescado con ensalada", [DishComponent(food: "pescado", proportion: 0.6, role: .topping), DishComponent(food: "ensalada", proportion: 0.4, role: .side)]),
        ("pescado con verduras", [DishComponent(food: "pescado", proportion: 0.5, role: .topping), DishComponent(food: "verduras", proportion: 0.5, role: .side)]),
        ("pescado a la plancha con ensalada", [DishComponent(food: "pescado", proportion: 0.6, role: .topping), DishComponent(food: "ensalada", proportion: 0.4, role: .side)]),
        ("pescado al horno con papas", [DishComponent(food: "pescado", proportion: 0.5, role: .topping), DishComponent(food: "papa", proportion: 0.5, role: .starch)]),
        ("pescado frito con arroz", [DishComponent(food: "pescado frito", proportion: 0.5, role: .topping), DishComponent(food: "arroz", proportion: 0.5, role: .starch)]),
        ("salmon con verduras", [DishComponent(food: "salmón", proportion: 0.5, role: .topping), DishComponent(food: "verduras", proportion: 0.5, role: .side)]),
        ("salmon con arroz", [DishComponent(food: "salmón", proportion: 0.5, role: .topping), DishComponent(food: "arroz", proportion: 0.5, role: .starch)]),
        ("salmon con pure", [DishComponent(food: "salmón", proportion: 0.5, role: .topping), DishComponent(food: "puré", proportion: 0.5, role: .starch)]),
        ("salmon con ensalada", [DishComponent(food: "salmón", proportion: 0.6, role: .topping), DishComponent(food: "ensalada", proportion: 0.4, role: .side)]),
        ("salmon a la plancha con verduras", [DishComponent(food: "salmón", proportion: 0.5, role: .topping), DishComponent(food: "verduras", proportion: 0.5, role: .side)]),
        ("camarones con arroz", [DishComponent(food: "camarón", proportion: 0.5, role: .topping), DishComponent(food: "arroz", proportion: 0.5, role: .starch)]),
        ("camarones con pasta", [DishComponent(food: "camarón", proportion: 0.5, role: .topping), DishComponent(food: "pasta", proportion: 0.5, role: .starch)]),
        ("camarones con ensalada", [DishComponent(food: "camarón", proportion: 0.6, role: .topping), DishComponent(food: "ensalada", proportion: 0.4, role: .side)]),
        ("camarones al ajillo con arroz", [DishComponent(food: "camarón", proportion: 0.5, role: .topping), DishComponent(food: "arroz", proportion: 0.5, role: .starch)]),

        ("ensalada de lechuga y tomate", [DishComponent(food: "lechuga", proportion: 0.5, role: .side), DishComponent(food: "tomate", proportion: 0.5, role: .side)]),
        ("ensalada de lechuga, tomate y cebolla", [DishComponent(food: "lechuga", proportion: 0.4, role: .side), DishComponent(food: "tomate", proportion: 0.35, role: .side), DishComponent(food: "cebolla", proportion: 0.25, role: .side)]),
        ("ensalada cesar con pollo", [DishComponent(food: "lechuga", proportion: 0.4, role: .side), DishComponent(food: "pollo", proportion: 0.4, role: .topping), DishComponent(food: "aderezo cesar", proportion: 0.2, role: .sauce)]),
        ("ensalada griega con queso feta", [DishComponent(food: "lechuga", proportion: 0.3, role: .side), DishComponent(food: "tomate", proportion: 0.2, role: .side), DishComponent(food: "pepino", proportion: 0.2, role: .side), DishComponent(food: "queso feta", proportion: 0.3, role: .topping)]),
        ("ensalada de atun con aceitunas", [DishComponent(food: "atún", proportion: 0.4, role: .topping), DishComponent(food: "lechuga", proportion: 0.3, role: .side), DishComponent(food: "aceitunas", proportion: 0.3, role: .side)]),
        ("ensalada de papa con mayonesa", [DishComponent(food: "papa", proportion: 0.7, role: .starch), DishComponent(food: "mayonesa", proportion: 0.3, role: .sauce)]),
        ("ensalada rusa", [DishComponent(food: "papa", proportion: 0.3, role: .starch), DishComponent(food: "zanahoria", proportion: 0.2, role: .side), DishComponent(food: "arveja", proportion: 0.2, role: .side), DishComponent(food: "mayonesa", proportion: 0.3, role: .sauce)]),
        ("ensaladilla rusa", [DishComponent(food: "papa", proportion: 0.3, role: .starch), DishComponent(food: "zanahoria", proportion: 0.2, role: .side), DishComponent(food: "arveja", proportion: 0.2, role: .side), DishComponent(food: "mayonesa", proportion: 0.3, role: .sauce)]),

        ("sopa de pollo con fideos", [DishComponent(food: "pollo", proportion: 0.3, role: .topping), DishComponent(food: "fideos", proportion: 0.3, role: .starch), DishComponent(food: "caldo", proportion: 0.4, role: .sauce)]),
        ("sopa de pollo con verduras", [DishComponent(food: "pollo", proportion: 0.3, role: .topping), DishComponent(food: "verduras", proportion: 0.3, role: .side), DishComponent(food: "caldo", proportion: 0.4, role: .sauce)]),
        ("sopa de pollo con arroz", [DishComponent(food: "pollo", proportion: 0.3, role: .topping), DishComponent(food: "arroz", proportion: 0.3, role: .starch), DishComponent(food: "caldo", proportion: 0.4, role: .sauce)]),
        ("caldo de pollo con verduras", [DishComponent(food: "pollo", proportion: 0.25, role: .topping), DishComponent(food: "verduras", proportion: 0.35, role: .side), DishComponent(food: "caldo", proportion: 0.4, role: .sauce)]),
        ("sopa de lentejas con verduras", [DishComponent(food: "lentejas", proportion: 0.5, role: .side), DishComponent(food: "verduras", proportion: 0.3, role: .side), DishComponent(food: "caldo", proportion: 0.2, role: .sauce)]),
        ("sopa de lentejas con chorizo", [DishComponent(food: "lentejas", proportion: 0.5, role: .side), DishComponent(food: "chorizo", proportion: 0.3, role: .topping), DishComponent(food: "caldo", proportion: 0.2, role: .sauce)]),
        ("cazuela de vacuno", [DishComponent(food: "carne", proportion: 0.25, role: .topping), DishComponent(food: "papa", proportion: 0.2, role: .starch), DishComponent(food: "zapallo", proportion: 0.15, role: .side), DishComponent(food: "choclo", proportion: 0.1, role: .side), DishComponent(food: "caldo", proportion: 0.3, role: .sauce)]),
        ("cazuela de pollo", [DishComponent(food: "pollo", proportion: 0.25, role: .topping), DishComponent(food: "papa", proportion: 0.2, role: .starch), DishComponent(food: "zapallo", proportion: 0.15, role: .side), DishComponent(food: "choclo", proportion: 0.1, role: .side), DishComponent(food: "caldo", proportion: 0.3, role: .sauce)]),

        ("tortilla de patatas", [DishComponent(food: "papa", proportion: 0.6, role: .starch), DishComponent(food: "huevo", proportion: 0.4, role: .topping)]),
        ("tortilla de papas", [DishComponent(food: "papa", proportion: 0.6, role: .starch), DishComponent(food: "huevo", proportion: 0.4, role: .topping)]),
        ("tortilla de patatas con cebolla", [DishComponent(food: "papa", proportion: 0.5, role: .starch), DishComponent(food: "huevo", proportion: 0.35, role: .topping), DishComponent(food: "cebolla", proportion: 0.15, role: .side)]),
        ("tortilla de papas con cebolla", [DishComponent(food: "papa", proportion: 0.5, role: .starch), DishComponent(food: "huevo", proportion: 0.35, role: .topping), DishComponent(food: "cebolla", proportion: 0.15, role: .side)]),
        ("tortilla de espinaca", [DishComponent(food: "espinaca", proportion: 0.5, role: .side), DishComponent(food: "huevo", proportion: 0.5, role: .topping)]),
        ("tortilla de espinacas", [DishComponent(food: "espinaca", proportion: 0.5, role: .side), DishComponent(food: "huevo", proportion: 0.5, role: .topping)]),
        ("tortilla de atun", [DishComponent(food: "atún", proportion: 0.5, role: .topping), DishComponent(food: "huevo", proportion: 0.5, role: .topping)]),
        ("tortilla de atún", [DishComponent(food: "atún", proportion: 0.5, role: .topping), DishComponent(food: "huevo", proportion: 0.5, role: .topping)]),
        ("tortilla de jamon y queso", [DishComponent(food: "jamón", proportion: 0.3, role: .topping), DishComponent(food: "queso", proportion: 0.3, role: .topping), DishComponent(food: "huevo", proportion: 0.4, role: .topping)]),
        ("tortilla de jamón y queso", [DishComponent(food: "jamón", proportion: 0.3, role: .topping), DishComponent(food: "queso", proportion: 0.3, role: .topping), DishComponent(food: "huevo", proportion: 0.4, role: .topping)]),

        ("porotos con riendas", [DishComponent(food: "porotos", proportion: 0.5, role: .side), DishComponent(food: "fideos", proportion: 0.3, role: .starch), DishComponent(food: "caldo", proportion: 0.2, role: .sauce)]),
        ("porotos granados", [DishComponent(food: "porotos", proportion: 0.4, role: .side), DishComponent(food: "choclo", proportion: 0.3, role: .side), DishComponent(food: "zapallo", proportion: 0.2, role: .side), DishComponent(food: "caldo", proportion: 0.1, role: .sauce)]),
        ("charquican", [DishComponent(food: "carne", proportion: 0.2, role: .topping), DishComponent(food: "papa", proportion: 0.2, role: .starch), DishComponent(food: "zapallo", proportion: 0.15, role: .side), DishComponent(food: "choclo", proportion: 0.15, role: .side), DishComponent(food: "caldo", proportion: 0.3, role: .sauce)]),
        ("pastel de choclo", [DishComponent(food: "choclo", proportion: 0.5, role: .starch), DishComponent(food: "carne", proportion: 0.3, role: .topping), DishComponent(food: "huevo", proportion: 0.1, role: .topping), DishComponent(food: "aceituna", proportion: 0.1, role: .side)]),
        ("humitas", [DishComponent(food: "choclo", proportion: 0.8, role: .starch), DishComponent(food: "cebolla", proportion: 0.1, role: .side), DishComponent(food: "albahaca", proportion: 0.1, role: .garnish)]),
        ("empanada de pino", [DishComponent(food: "masa", proportion: 0.4, role: .starch), DishComponent(food: "carne molida", proportion: 0.3, role: .topping), DishComponent(food: "cebolla", proportion: 0.15, role: .side), DishComponent(food: "huevo", proportion: 0.1, role: .topping), DishComponent(food: "aceituna", proportion: 0.05, role: .side)]),

        ("cafe con leche", [DishComponent(food: "café", proportion: 0.3, role: .side), DishComponent(food: "leche", proportion: 0.7, role: .side)]),
        ("café con leche", [DishComponent(food: "café", proportion: 0.3, role: .side), DishComponent(food: "leche", proportion: 0.7, role: .side)]),
        ("cafe con azucar", [DishComponent(food: "café", proportion: 0.8, role: .side), DishComponent(food: "azúcar", proportion: 0.2, role: .sauce)]),
        ("café con azúcar", [DishComponent(food: "café", proportion: 0.8, role: .side), DishComponent(food: "azúcar", proportion: 0.2, role: .sauce)]),
        ("leche con chocolate", [DishComponent(food: "leche", proportion: 0.7, role: .side), DishComponent(food: "chocolate", proportion: 0.3, role: .sauce)]),
        ("yogurt con fruta", [DishComponent(food: "yogurt", proportion: 0.6, role: .side), DishComponent(food: "fruta", proportion: 0.4, role: .side)]),
        ("yogurt con granola", [DishComponent(food: "yogurt", proportion: 0.5, role: .side), DishComponent(food: "granola", proportion: 0.5, role: .topping)]),
        ("avena con leche", [DishComponent(food: "avena", proportion: 0.4, role: .starch), DishComponent(food: "leche", proportion: 0.6, role: .side)]),
        ("avena con frutas", [DishComponent(food: "avena", proportion: 0.5, role: .starch), DishComponent(food: "fruta", proportion: 0.5, role: .side)]),
        ("avena con platano", [DishComponent(food: "avena", proportion: 0.5, role: .starch), DishComponent(food: "plátano", proportion: 0.5, role: .side)]),
        ("avena con banana", [DishComponent(food: "avena", proportion: 0.5, role: .starch), DishComponent(food: "plátano", proportion: 0.5, role: .side)]),
        ("avena con manzana", [DishComponent(food: "avena", proportion: 0.5, role: .starch), DishComponent(food: "manzana", proportion: 0.5, role: .side)]),
        ("avena con miel", [DishComponent(food: "avena", proportion: 0.7, role: .starch), DishComponent(food: "miel", proportion: 0.3, role: .sauce)]),
        ("avena con canela", [DishComponent(food: "avena", proportion: 0.9, role: .starch), DishComponent(food: "canela", proportion: 0.1, role: .garnish)]),
        ("avena con nueces", [DishComponent(food: "avena", proportion: 0.6, role: .starch), DishComponent(food: "nueces", proportion: 0.4, role: .topping)]),
        ("avena con almendras", [DishComponent(food: "avena", proportion: 0.6, role: .starch), DishComponent(food: "almendras", proportion: 0.4, role: .topping)]),
        ("avena con chia", [DishComponent(food: "avena", proportion: 0.7, role: .starch), DishComponent(food: "chía", proportion: 0.3, role: .topping)]),
        ("avena con chía", [DishComponent(food: "avena", proportion: 0.7, role: .starch), DishComponent(food: "chía", proportion: 0.3, role: .topping)]),
        ("avena con yogurt", [DishComponent(food: "avena", proportion: 0.5, role: .starch), DishComponent(food: "yogurt", proportion: 0.5, role: .side)]),
        ("avena con chocolate", [DishComponent(food: "avena", proportion: 0.6, role: .starch), DishComponent(food: "chocolate", proportion: 0.4, role: .sauce)]),
        ("granola con yogurt", [DishComponent(food: "granola", proportion: 0.4, role: .topping), DishComponent(food: "yogurt", proportion: 0.6, role: .side)]),
        ("granola con leche", [DishComponent(food: "granola", proportion: 0.4, role: .topping), DishComponent(food: "leche", proportion: 0.6, role: .side)]),
        ("cereal con leche", [DishComponent(food: "cereal", proportion: 0.3, role: .topping), DishComponent(food: "leche", proportion: 0.7, role: .side)]),

        ("fruta con yogurt", [DishComponent(food: "fruta", proportion: 0.5, role: .side), DishComponent(food: "yogurt", proportion: 0.5, role: .side)]),
        ("fruta con crema", [DishComponent(food: "fruta", proportion: 0.6, role: .side), DishComponent(food: "crema", proportion: 0.4, role: .sauce)]),
        ("fruta con miel", [DishComponent(food: "fruta", proportion: 0.8, role: .side), DishComponent(food: "miel", proportion: 0.2, role: .sauce)]),
        ("fruta con chocolate", [DishComponent(food: "fruta", proportion: 0.6, role: .side), DishComponent(food: "chocolate", proportion: 0.4, role: .sauce)]),
        ("fruta con granola", [DishComponent(food: "fruta", proportion: 0.5, role: .side), DishComponent(food: "granola", proportion: 0.5, role: .topping)]),
        ("fresas con crema", [DishComponent(food: "fresa", proportion: 0.6, role: .side), DishComponent(food: "crema", proportion: 0.4, role: .sauce)]),
        ("frutillas con crema", [DishComponent(food: "frutilla", proportion: 0.6, role: .side), DishComponent(food: "crema", proportion: 0.4, role: .sauce)]),
        ("platano con dulce de leche", [DishComponent(food: "plátano", proportion: 0.6, role: .side), DishComponent(food: "dulce de leche", proportion: 0.4, role: .sauce)]),
        ("banana con dulce de leche", [DishComponent(food: "plátano", proportion: 0.6, role: .side), DishComponent(food: "dulce de leche", proportion: 0.4, role: .sauce)]),
        ("manzana con canela", [DishComponent(food: "manzana", proportion: 0.9, role: .side), DishComponent(food: "canela", proportion: 0.1, role: .garnish)]),
        ("manzana con miel", [DishComponent(food: "manzana", proportion: 0.8, role: .side), DishComponent(food: "miel", proportion: 0.2, role: .sauce)]),
        ("melon con jamon", [DishComponent(food: "melón", proportion: 0.6, role: .side), DishComponent(food: "jamón", proportion: 0.4, role: .topping)]),
        ("melon con jamón", [DishComponent(food: "melón", proportion: 0.6, role: .side), DishComponent(food: "jamón", proportion: 0.4, role: .topping)]),
        ("sandia con queso", [DishComponent(food: "sandía", proportion: 0.7, role: .side), DishComponent(food: "queso", proportion: 0.3, role: .topping)]),
        ("sandía con queso", [DishComponent(food: "sandía", proportion: 0.7, role: .side), DishComponent(food: "queso", proportion: 0.3, role: .topping)]),
        ("mango con yogurt", [DishComponent(food: "mango", proportion: 0.5, role: .side), DishComponent(food: "yogurt", proportion: 0.5, role: .side)]),

        ("arroz con leche y canela", [DishComponent(food: "arroz", proportion: 0.4, role: .starch), DishComponent(food: "leche", proportion: 0.5, role: .side), DishComponent(food: "canela", proportion: 0.1, role: .garnish)]),
        ("arroz con leche con pasas", [DishComponent(food: "arroz", proportion: 0.4, role: .starch), DishComponent(food: "leche", proportion: 0.45, role: .side), DishComponent(food: "pasas", proportion: 0.15, role: .topping)]),
        ("flan con dulce de leche", [DishComponent(food: "flan", proportion: 0.6, role: .side), DishComponent(food: "dulce de leche", proportion: 0.4, role: .sauce)]),
        ("flan con crema", [DishComponent(food: "flan", proportion: 0.6, role: .side), DishComponent(food: "crema", proportion: 0.4, role: .sauce)]),
        ("helado con chocolate", [DishComponent(food: "helado", proportion: 0.6, role: .side), DishComponent(food: "chocolate", proportion: 0.4, role: .sauce)]),
        ("helado con frutas", [DishComponent(food: "helado", proportion: 0.5, role: .side), DishComponent(food: "fruta", proportion: 0.5, role: .topping)]),
        ("helado con nueces", [DishComponent(food: "helado", proportion: 0.6, role: .side), DishComponent(food: "nueces", proportion: 0.4, role: .topping)]),
        ("brownie con helado", [DishComponent(food: "brownie", proportion: 0.5, role: .side), DishComponent(food: "helado", proportion: 0.5, role: .topping)]),
        ("churros con chocolate", [DishComponent(food: "churros", proportion: 0.5, role: .side), DishComponent(food: "chocolate", proportion: 0.5, role: .sauce)]),
        ("churros con dulce de leche", [DishComponent(food: "churros", proportion: 0.5, role: .side), DishComponent(food: "dulce de leche", proportion: 0.5, role: .sauce)]),
        ("tres leches con crema", [DishComponent(food: "tres leches", proportion: 0.6, role: .side), DishComponent(food: "crema", proportion: 0.4, role: .sauce)]),
        ("cheesecake con mermelada", [DishComponent(food: "cheesecake", proportion: 0.7, role: .side), DishComponent(food: "mermelada", proportion: 0.3, role: .sauce)]),
        ("cheesecake con frutos rojos", [DishComponent(food: "cheesecake", proportion: 0.6, role: .side), DishComponent(food: "frutos rojos", proportion: 0.4, role: .topping)]),

        ("arepa con queso", [DishComponent(food: "arepa", proportion: 0.5, role: .starch), DishComponent(food: "queso", proportion: 0.5, role: .topping)]),
        ("arepa con jamon y queso", [DishComponent(food: "arepa", proportion: 0.4, role: .starch), DishComponent(food: "jamón", proportion: 0.3, role: .topping), DishComponent(food: "queso", proportion: 0.3, role: .topping)]),
        ("arepa con jamón y queso", [DishComponent(food: "arepa", proportion: 0.4, role: .starch), DishComponent(food: "jamón", proportion: 0.3, role: .topping), DishComponent(food: "queso", proportion: 0.3, role: .topping)]),
        ("arepa con carne mechada", [DishComponent(food: "arepa", proportion: 0.4, role: .starch), DishComponent(food: "carne mechada", proportion: 0.6, role: .topping)]),
        ("arepa reina pepiada", [DishComponent(food: "arepa", proportion: 0.3, role: .starch), DishComponent(food: "pollo", proportion: 0.35, role: .topping), DishComponent(food: "palta", proportion: 0.35, role: .topping)]),
        ("arepa con pollo y aguacate", [DishComponent(food: "arepa", proportion: 0.3, role: .starch), DishComponent(food: "pollo", proportion: 0.35, role: .topping), DishComponent(food: "palta", proportion: 0.35, role: .topping)]),
        ("arepa con huevo", [DishComponent(food: "arepa", proportion: 0.5, role: .starch), DishComponent(food: "huevo", proportion: 0.5, role: .topping)]),
        ("arepa con mantequilla", [DishComponent(food: "arepa", proportion: 0.7, role: .starch), DishComponent(food: "mantequilla", proportion: 0.3, role: .sauce)]),
        ("arepa con pernil", [DishComponent(food: "arepa", proportion: 0.4, role: .starch), DishComponent(food: "pernil", proportion: 0.6, role: .topping)]),
        ("arepa con chorizo", [DishComponent(food: "arepa", proportion: 0.4, role: .starch), DishComponent(food: "chorizo", proportion: 0.6, role: .topping)]),
        ("arepa con chicharron", [DishComponent(food: "arepa", proportion: 0.4, role: .starch), DishComponent(food: "chicharrón", proportion: 0.6, role: .topping)]),
        ("arepa con chicharrón", [DishComponent(food: "arepa", proportion: 0.4, role: .starch), DishComponent(food: "chicharrón", proportion: 0.6, role: .topping)]),

        ("taco de carne asada", [DishComponent(food: "tortilla", proportion: 0.3, role: .starch), DishComponent(food: "carne", proportion: 0.5, role: .topping), DishComponent(food: "salsa", proportion: 0.2, role: .sauce)]),
        ("taco de pastor", [DishComponent(food: "tortilla", proportion: 0.3, role: .starch), DishComponent(food: "cerdo", proportion: 0.5, role: .topping), DishComponent(food: "pina", proportion: 0.2, role: .side)]),
        ("taco de carnitas", [DishComponent(food: "tortilla", proportion: 0.3, role: .starch), DishComponent(food: "cerdo", proportion: 0.5, role: .topping), DishComponent(food: "salsa", proportion: 0.2, role: .sauce)]),
        ("taco de pollo", [DishComponent(food: "tortilla", proportion: 0.3, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "salsa", proportion: 0.2, role: .sauce)]),
        ("taco de pescado", [DishComponent(food: "tortilla", proportion: 0.3, role: .starch), DishComponent(food: "pescado", proportion: 0.5, role: .topping), DishComponent(food: "salsa", proportion: 0.2, role: .sauce)]),
        ("taco de camaron", [DishComponent(food: "tortilla", proportion: 0.3, role: .starch), DishComponent(food: "camarón", proportion: 0.5, role: .topping), DishComponent(food: "salsa", proportion: 0.2, role: .sauce)]),
        ("taco de frijoles", [DishComponent(food: "tortilla", proportion: 0.4, role: .starch), DishComponent(food: "frijoles", proportion: 0.6, role: .topping)]),
        ("quesadilla con queso", [DishComponent(food: "tortilla", proportion: 0.4, role: .starch), DishComponent(food: "queso", proportion: 0.6, role: .topping)]),
        ("quesadilla con pollo", [DishComponent(food: "tortilla", proportion: 0.3, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "queso", proportion: 0.2, role: .topping)]),
        ("burrito de carne", [DishComponent(food: "tortilla", proportion: 0.2, role: .starch), DishComponent(food: "carne", proportion: 0.3, role: .topping), DishComponent(food: "arroz", proportion: 0.2, role: .starch), DishComponent(food: "frijoles", proportion: 0.2, role: .side), DishComponent(food: "salsa", proportion: 0.1, role: .sauce)]),
        ("burrito de pollo", [DishComponent(food: "tortilla", proportion: 0.2, role: .starch), DishComponent(food: "pollo", proportion: 0.3, role: .topping), DishComponent(food: "arroz", proportion: 0.2, role: .starch), DishComponent(food: "frijoles", proportion: 0.2, role: .side), DishComponent(food: "salsa", proportion: 0.1, role: .sauce)]),
        ("enchiladas con pollo", [DishComponent(food: "tortilla", proportion: 0.3, role: .starch), DishComponent(food: "pollo", proportion: 0.4, role: .topping), DishComponent(food: "salsa", proportion: 0.3, role: .sauce)]),
        ("tamal de pollo", [DishComponent(food: "masa", proportion: 0.5, role: .starch), DishComponent(food: "pollo", proportion: 0.4, role: .topping), DishComponent(food: "salsa", proportion: 0.1, role: .sauce)]),
        ("tamal de cerdo", [DishComponent(food: "masa", proportion: 0.5, role: .starch), DishComponent(food: "cerdo", proportion: 0.4, role: .topping), DishComponent(food: "salsa", proportion: 0.1, role: .sauce)]),

        ("sándwich de jamon y queso", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "jamón", proportion: 0.3, role: .topping), DishComponent(food: "queso", proportion: 0.3, role: .topping)]),
        ("sándwich de jamón y queso", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "jamón", proportion: 0.3, role: .topping), DishComponent(food: "queso", proportion: 0.3, role: .topping)]),
        ("sándwich de pavo", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "pavo", proportion: 0.5, role: .topping)]),
        ("sándwich de pollo", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "pollo", proportion: 0.6, role: .topping)]),
        ("sándwich de atun", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "atún", proportion: 0.6, role: .topping)]),
        ("sándwich de atún", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "atún", proportion: 0.6, role: .topping)]),
        ("sándwich de vegetales", [DishComponent(food: "pan", proportion: 0.5, role: .starch), DishComponent(food: "verduras", proportion: 0.5, role: .side)]),

        ("sándwich de pollo con mayonesa", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.1, role: .sauce)]),
        ("sándwich de pollo con mayo", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.1, role: .sauce)]),
        ("sándwich de pollo con palta", [DishComponent(food: "pan", proportion: 0.3, role: .starch), DishComponent(food: "pollo", proportion: 0.45, role: .topping), DishComponent(food: "palta", proportion: 0.25, role: .side)]),
        ("sándwich de pollo con lechuga", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "lechuga", proportion: 0.15, role: .side)]),
        ("sándwich de pollo con lechuga y tomate", [DishComponent(food: "pan", proportion: 0.3, role: .starch), DishComponent(food: "pollo", proportion: 0.45, role: .topping), DishComponent(food: "lechuga", proportion: 0.1, role: .side), DishComponent(food: "tomate", proportion: 0.15, role: .side)]),
        ("sándwich de pollo con ketchup", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "ketchup", proportion: 0.1, role: .sauce)]),
        ("sándwich de pollo con mostaza", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "mostaza", proportion: 0.1, role: .sauce)]),
        ("sándwich de pollo con queso", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "pollo", proportion: 0.45, role: .topping), DishComponent(food: "queso", proportion: 0.2, role: .topping)]),
        ("sándwich de pollo con jamon", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "pollo", proportion: 0.35, role: .topping), DishComponent(food: "jamón", proportion: 0.3, role: .topping)]),
        ("sándwich de pollo con jamón", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "pollo", proportion: 0.35, role: .topping), DishComponent(food: "jamón", proportion: 0.3, role: .topping)]),

        ("sándwich de jamon con mayonesa", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "jamón", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.15, role: .sauce)]),
        ("sándwich de jamón con mayonesa", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "jamón", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.15, role: .sauce)]),
        ("sándwich de atun con mayonesa", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "atún", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.15, role: .sauce)]),
        ("sándwich de atún con mayonesa", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "atún", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.15, role: .sauce)]),
        ("sándwich de palta con mayonesa", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "palta", proportion: 0.5, role: .side), DishComponent(food: "mayonesa", proportion: 0.15, role: .sauce)]),

        ("sandwich de pollo con mayonesa", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.1, role: .sauce)]),
        ("sandwich de pollo con mayo", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.1, role: .sauce)]),
        ("sandwich de pollo con palta", [DishComponent(food: "pan", proportion: 0.3, role: .starch), DishComponent(food: "pollo", proportion: 0.45, role: .topping), DishComponent(food: "palta", proportion: 0.25, role: .side)]),
        ("sandwich de pollo con lechuga", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "lechuga", proportion: 0.15, role: .side)]),
        ("sandwich de pollo con lechuga y tomate", [DishComponent(food: "pan", proportion: 0.3, role: .starch), DishComponent(food: "pollo", proportion: 0.45, role: .topping), DishComponent(food: "lechuga", proportion: 0.1, role: .side), DishComponent(food: "tomate", proportion: 0.15, role: .side)]),
        ("sandwich de pollo con ketchup", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "ketchup", proportion: 0.1, role: .sauce)]),
        ("sandwich de pollo con mostaza", [DishComponent(food: "pan", proportion: 0.4, role: .starch), DishComponent(food: "pollo", proportion: 0.5, role: .topping), DishComponent(food: "mostaza", proportion: 0.1, role: .sauce)]),
        ("sandwich de pollo con queso", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "pollo", proportion: 0.45, role: .topping), DishComponent(food: "queso", proportion: 0.2, role: .topping)]),
        ("sandwich de pollo con jamon", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "pollo", proportion: 0.35, role: .topping), DishComponent(food: "jamón", proportion: 0.3, role: .topping)]),
        ("sandwich de pollo con jamón", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "pollo", proportion: 0.35, role: .topping), DishComponent(food: "jamón", proportion: 0.3, role: .topping)]),

        ("sandwich de jamon con mayonesa", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "jamón", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.15, role: .sauce)]),
        ("sandwich de jamón con mayonesa", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "jamón", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.15, role: .sauce)]),
        ("sandwich de atun con mayonesa", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "atún", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.15, role: .sauce)]),
        ("sandwich de atún con mayonesa", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "atún", proportion: 0.5, role: .topping), DishComponent(food: "mayonesa", proportion: 0.15, role: .sauce)]),
        ("sandwich de palta con mayonesa", [DishComponent(food: "pan", proportion: 0.35, role: .starch), DishComponent(food: "palta", proportion: 0.5, role: .side), DishComponent(food: "mayonesa", proportion: 0.15, role: .sauce)]),

        ("hamburguesa con mayonesa", [DishComponent(food: "hamburguesa", proportion: 0.75, role: .topping), DishComponent(food: "mayonesa", proportion: 0.25, role: .sauce)]),
        ("hamburguesa con ketchup", [DishComponent(food: "hamburguesa", proportion: 0.75, role: .topping), DishComponent(food: "ketchup", proportion: 0.25, role: .sauce)]),
        ("hamburguesa con mostaza", [DishComponent(food: "hamburguesa", proportion: 0.75, role: .topping), DishComponent(food: "mostaza", proportion: 0.25, role: .sauce)]),
        ("hamburguesa con queso y tocino", [DishComponent(food: "hamburguesa", proportion: 0.5, role: .topping), DishComponent(food: "queso", proportion: 0.25, role: .topping), DishComponent(food: "tocino", proportion: 0.25, role: .topping)]),
        ("hamburguesa con lechuga y tomate", [DishComponent(food: "hamburguesa", proportion: 0.65, role: .topping), DishComponent(food: "lechuga", proportion: 0.15, role: .side), DishComponent(food: "tomate", proportion: 0.2, role: .side)]),

        ("papas fritas con mayonesa", [DishComponent(food: "papa frita", proportion: 0.75, role: .starch), DishComponent(food: "mayonesa", proportion: 0.25, role: .sauce)]),
        ("papas fritas con ketchup", [DishComponent(food: "papa frita", proportion: 0.75, role: .starch), DishComponent(food: "ketchup", proportion: 0.25, role: .sauce)]),
        ("papas fritas con salsa", [DishComponent(food: "papa frita", proportion: 0.7, role: .starch), DishComponent(food: "salsa", proportion: 0.3, role: .sauce)]),
        ("papas con mayonesa", [DishComponent(food: "papa", proportion: 0.7, role: .starch), DishComponent(food: "mayonesa", proportion: 0.3, role: .sauce)]),
        ("papas con salsa", [DishComponent(food: "papa", proportion: 0.7, role: .starch), DishComponent(food: "salsa", proportion: 0.3, role: .sauce)]),

        ("ceviche con maiz", [DishComponent(food: "pescado", proportion: 0.5, role: .topping), DishComponent(food: "maiz", proportion: 0.3, role: .side), DishComponent(food: "limon", proportion: 0.2, role: .sauce)]),
        ("ceviche con maiz tostado", [DishComponent(food: "pescado", proportion: 0.5, role: .topping), DishComponent(food: "maiz tostado", proportion: 0.3, role: .side), DishComponent(food: "limon", proportion: 0.2, role: .sauce)]),
        ("ceviche con camote", [DishComponent(food: "pescado", proportion: 0.5, role: .topping), DishComponent(food: "camote", proportion: 0.3, role: .side), DishComponent(food: "limon", proportion: 0.2, role: .sauce)]),
        ("ceviche con palta", [DishComponent(food: "pescado", proportion: 0.4, role: .topping), DishComponent(food: "palta", proportion: 0.3, role: .side), DishComponent(food: "limon", proportion: 0.2, role: .sauce), DishComponent(food: "cebolla", proportion: 0.1, role: .side)]),
        ("ceviche con chifles", [DishComponent(food: "pescado", proportion: 0.4, role: .topping), DishComponent(food: "platano frito", proportion: 0.4, role: .starch), DishComponent(food: "limon", proportion: 0.2, role: .sauce)]),

        ("lomo saltado con arroz", [DishComponent(food: "lomo", proportion: 0.35, role: .topping), DishComponent(food: "arroz", proportion: 0.35, role: .starch), DishComponent(food: "papa frita", proportion: 0.3, role: .starch)]),
        ("lomo saltado con papas fritas", [DishComponent(food: "lomo", proportion: 0.4, role: .topping), DishComponent(food: "papa frita", proportion: 0.6, role: .starch)]),
    ]

    // MARK: - Patrones lingüísticos genéricos

    struct PatternEntry {
        let regex: NSRegularExpression
        let type: String
    }

    private static let COMBINATION_PATTERNS: [PatternEntry] = [
        PatternEntry(regex: try! NSRegularExpression(pattern: #"^([a-záéíóúñü\s]+?)\s+con\s+([a-záéíóúñü\s]+?)(?:\s+y\s+([a-záéíóúñü\s]+?))?$"#, options: .caseInsensitive), type: "con_triple"),
        PatternEntry(regex: try! NSRegularExpression(pattern: #"^([a-záéíóúñü\s]+?)\s+con\s+([a-záéíóúñü\s]+?)$"#, options: .caseInsensitive), type: "con_doble"),
        PatternEntry(regex: try! NSRegularExpression(pattern: #"^([a-záéíóúñü\s]+?)\s+de\s+([a-záéíóúñü\s]+?)(?:\s+con\s+([a-záéíóúñü\s]+?))?$"#, options: .caseInsensitive), type: "de_con"),
        PatternEntry(regex: try! NSRegularExpression(pattern: #"^([a-záéíóúñü\s]+?)\s+de\s+([a-záéíóúñü\s]+?)$"#, options: .caseInsensitive), type: "de_doble"),
        PatternEntry(regex: try! NSRegularExpression(pattern: #"^([a-záéíóúñü\s]+?)\s+y\s+([a-záéíóúñü\s]+?)$"#, options: .caseInsensitive), type: "y_doble"),
        PatternEntry(regex: try! NSRegularExpression(pattern: #"^([a-záéíóúñü\s]+?)\s+a\s+la\s+([a-záéíóúñü\s]+?)$"#, options: .caseInsensitive), type: "a_la"),
        PatternEntry(regex: try! NSRegularExpression(pattern: #"^([a-záéíóúñü\s]+?)\s+al\s+([a-záéíóúñü\s]+?)$"#, options: .caseInsensitive), type: "al"),
        PatternEntry(regex: try! NSRegularExpression(pattern: #"^([a-záéíóúñü\s]+?)\s+en\s+salsa\s+de\s+([a-záéíóúñü\s]+?)$"#, options: .caseInsensitive), type: "en_salsa_de"),
        PatternEntry(regex: try! NSRegularExpression(pattern: #"^([a-záéíóúñü\s]+?)\s+con\s+salsa\s+de\s+([a-záéíóúñü\s]+?)$"#, options: .caseInsensitive), type: "con_salsa_de"),
        PatternEntry(regex: try! NSRegularExpression(pattern: #"^([a-záéíóúñü\s]+?)\s+rellen[oa]\s+de\s+([a-záéíóúñü\s]+?)$"#, options: .caseInsensitive), type: "relleno_de"),
    ]

    // MARK: - Helpers

    private static func group(_ text: String, _ result: NSTextCheckingResult, _ index: Int) -> String {
        let range = result.range(at: index)
        if range.location == NSNotFound { return "" }
        return (text as NSString).substring(with: range).trimmingCharacters(in: .whitespaces)
    }

    private static func splitByRegex(_ text: String, regex: NSRegularExpression) -> [String] {
        let nsText = text as NSString
        let range = NSRange(location: 0, length: nsText.length)
        let matches = regex.matches(in: text, options: [], range: range)
        var parts: [String] = []
        var current = 0
        for match in matches {
            let matchRange = match.range
            if current < matchRange.location {
                let part = nsText.substring(with: NSRange(location: current, length: matchRange.location - current))
                parts.append(part.trimmingCharacters(in: .whitespaces))
            }
            current = matchRange.location + matchRange.length
        }
        if current < nsText.length {
            let part = nsText.substring(with: NSRange(location: current, length: nsText.length - current))
            parts.append(part.trimmingCharacters(in: .whitespaces))
        }
        return parts.filter { !$0.isEmpty }
    }

    // MARK: - Parse

    /// Parse a food combination string.
    static func parse(text: String) -> ParsedCombination {
        let lower = text.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)

        // 1. Check known dishes first (highest confidence)
        for (dishName, components) in KNOWN_DISHES {
            if lower == dishName || lower.contains(dishName) {
                let base = components.first!
                let baseFood = base.food
                let baseProportion = base.proportion
                let accompaniments = components.dropFirst().map { comp in
                    Accompaniment(food: comp.food, proportion: comp.proportion, role: comp.role)
                }

                return ParsedCombination(
                    baseFood: baseFood,
                    baseProportion: baseProportion,
                    accompaniments: accompaniments,
                    cookingMethod: nil,
                    isKnownDish: true,
                    dishName: dishName,
                    confidence: 0.95
                )
            }
        }

        // 2. Try generic patterns
        let nsRange = NSRange(lower.startIndex..., in: lower)
        for entry in COMBINATION_PATTERNS {
            guard let match = entry.regex.firstMatch(in: lower, options: [], range: nsRange) else { continue }

            switch entry.type {
            case "con_triple":
                let a = group(lower, match, 1)
                let b = group(lower, match, 2)
                let c = group(lower, match, 3)
                return ParsedCombination(
                    baseFood: a,
                    baseProportion: 0.5,
                    accompaniments: [
                        Accompaniment(food: b, proportion: 0.3, role: inferRole(b)),
                        Accompaniment(food: c, proportion: 0.2, role: inferRole(c)),
                    ],
                    cookingMethod: nil,
                    isKnownDish: false,
                    dishName: nil,
                    confidence: 0.70
                )
            case "con_doble":
                let a = group(lower, match, 1)
                let b = group(lower, match, 2)
                return ParsedCombination(
                    baseFood: a,
                    baseProportion: 0.6,
                    accompaniments: [
                        Accompaniment(food: b, proportion: 0.4, role: inferRole(b)),
                    ],
                    cookingMethod: nil,
                    isKnownDish: false,
                    dishName: nil,
                    confidence: 0.75
                )
            case "de_con":
                let a = group(lower, match, 1)
                let b = group(lower, match, 2)
                let c = group(lower, match, 3)
                var accs: [Accompaniment] = [Accompaniment(food: a, proportion: 0.2, role: .side)]
                if !c.isEmpty { accs.append(Accompaniment(food: c, proportion: 0.2, role: inferRole(c))) }
                return ParsedCombination(
                    baseFood: b,
                    baseProportion: 0.6,
                    accompaniments: accs,
                    cookingMethod: nil,
                    isKnownDish: false,
                    dishName: nil,
                    confidence: 0.70
                )
            case "de_doble":
                let a = group(lower, match, 1)
                let b = group(lower, match, 2)
                return ParsedCombination(
                    baseFood: b,
                    baseProportion: 0.7,
                    accompaniments: [Accompaniment(food: a, proportion: 0.3, role: .side)],
                    cookingMethod: nil,
                    isKnownDish: false,
                    dishName: nil,
                    confidence: 0.75
                )
            case "y_doble":
                let a = group(lower, match, 1)
                let b = group(lower, match, 2)
                return ParsedCombination(
                    baseFood: a,
                    baseProportion: 0.5,
                    accompaniments: [Accompaniment(food: b, proportion: 0.5, role: inferRole(b))],
                    cookingMethod: nil,
                    isKnownDish: false,
                    dishName: nil,
                    confidence: 0.70
                )
            case "a_la", "al":
                let a = group(lower, match, 1)
                let b = group(lower, match, 2)
                return ParsedCombination(
                    baseFood: a,
                    baseProportion: 1.0,
                    accompaniments: [],
                    cookingMethod: b,
                    isKnownDish: false,
                    dishName: nil,
                    confidence: 0.65
                )
            case "en_salsa_de", "con_salsa_de":
                let a = group(lower, match, 1)
                let b = group(lower, match, 2)
                return ParsedCombination(
                    baseFood: a,
                    baseProportion: 0.7,
                    accompaniments: [Accompaniment(food: "salsa de \(b)", proportion: 0.3, role: .sauce)],
                    cookingMethod: nil,
                    isKnownDish: false,
                    dishName: nil,
                    confidence: 0.65
                )
            case "relleno_de":
                let a = group(lower, match, 1)
                let b = group(lower, match, 2)
                return ParsedCombination(
                    baseFood: a,
                    baseProportion: 0.5,
                    accompaniments: [Accompaniment(food: b, proportion: 0.5, role: .filling)],
                    cookingMethod: nil,
                    isKnownDish: false,
                    dishName: nil,
                    confidence: 0.70
                )
            default:
                break
            }
        }

        // 3. Fallback: single food
        return ParsedCombination(
            baseFood: lower,
            baseProportion: 1.0,
            accompaniments: [],
            cookingMethod: nil,
            isKnownDish: false,
            dishName: nil,
            confidence: 0.50
        )
    }

    private static func inferRole(_ food: String) -> Role {
        let lower = food.lowercased()
        if lower.contains("arroz") || lower.contains("papa") || lower.contains("patata") || lower.contains("pasta") || lower.contains("fideo") || lower.contains("pure") || lower.contains("pan") || lower.contains("tortilla") {
            return .starch
        }
        if lower.contains("salsa") || lower.contains("mayonesa") || lower.contains("mayo") || lower.contains("ketchup") || lower.contains("catsup") || lower.contains("mostaza") || lower.contains("crema") || lower.contains("aderezo") || lower.contains("vinagreta") || lower.contains("pesto") || lower.contains("chimichurri") || lower.contains("aceite") || lower.contains("oil") || lower.contains("mantequilla") || lower.contains("margarina") || lower.contains("manteca") || lower.contains("ghee") {
            return .sauce
        }
        if lower.contains("queso") || lower.contains("huevo") || lower.contains("jamon") || lower.contains("tocino") || lower.contains("salchicha") {
            return .topping
        }
        if lower.contains("ensalada") || lower.contains("verdura") || lower.contains("lechuga") || lower.contains("tomate") || lower.contains("cebolla") || lower.contains("zanahoria") || lower.contains("brocoli") || lower.contains("espinaca") {
            return .side
        }
        return .side
    }

    static func splitFoods(text: String) -> [String] {
        let lower = text.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        for (dishName, _) in KNOWN_DISHES {
            if lower == dishName { return [dishName] }
        }
        var foods: [String] = []
        var remaining = lower
        for (dishName, _) in KNOWN_DISHES {
            if remaining.contains(dishName) {
                foods.append(dishName)
                remaining = remaining.replacingOccurrences(of: dishName, with: "").trimmingCharacters(in: .whitespaces)
                let nsRemaining = NSRange(remaining.startIndex..., in: remaining)
                remaining = CON_Y_COMMA_LEADING_PATTERN.stringByReplacingMatches(in: remaining, options: [], range: nsRemaining, withTemplate: "").trimmingCharacters(in: .whitespaces)
            }
        }
        if !remaining.trimmingCharacters(in: .whitespaces).isEmpty {
            let parts = splitByRegex(remaining, regex: COMBO_SPLIT_PATTERN)
            foods.append(contentsOf: parts)
        }
        return foods.isEmpty ? [lower] : foods
    }
}
