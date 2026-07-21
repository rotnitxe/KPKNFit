import Foundation

/// CookingMethodParser — Detecta 440+ expresiones de métodos de cocción y aplica multiplicadores.
enum CookingMethodParser {

    enum CookingCategory {
        case lowImpact, neutral, moderate, high, veryHigh
    }

    struct CookingMethodResult {
        let method: String
        let category: CookingCategory
        let kcalFactor: Double
        let proteinFactor: Double
        let carbsFactor: Double
        let fatsFactor: Double
        let waterChange: Double
        let confidence: Double
    }

    struct MethodPattern { let regex: NSRegularExpression; let method: String; let kcal: Double; let protein: Double; let carbs: Double; let fats: Double; let water: Double }
    struct DonenessPattern { let regex: NSRegularExpression; let method: String; let kcal: Double; let protein: Double; let carbs: Double; let fats: Double }
    struct ColloquialPattern { let regex: NSRegularExpression; let factor: Double }
    struct ModernMethod { let regex: NSRegularExpression; let method: String; let factor: Double }

    private static let METHOD_PATTERNS: [MethodPattern] = [
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(crudo|natural|al\s+natural|sin\s+cocinar|vivo)\b"#, options: .caseInsensitive), method: "crudo", kcal: 0.90, protein: 1.0, carbs: 1.0, fats: 1.0, water: 0.0),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(hervido|hervidito|cocido|cocidito|en\s+agua|en\s+agua\s+con\s+sal|sancochado|sancochadito|sancochar|medio\s+sancochado)\b"#, options: .caseInsensitive), method: "hervido", kcal: 0.90, protein: 0.95, carbs: 1.0, fats: 1.0, water: 0.15),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(al\s+vapor|al\s+vaporcito|vaporizado|en\s+vaporera|en\s+olla\s+vaporera|en\s+canasta\s+de\s+bambu|en\s+tamalera|al\s+vapor\s+suave|al\s+vapor\s+fuerte|coccion\s+al\s+vapor\s+express|en\s+papillote\s+al\s+vapor|en\s+olla\s+de\s+presion\s+con\s+rejilla|envuelto\s+en\s+hoja|en\s+hoja\s+de\s+platano|en\s+hoja\s+de\s+maiz|envuelto)\b"#, options: .caseInsensitive), method: "vapor", kcal: 0.95, protein: 1.0, carbs: 1.0, fats: 1.0, water: 0.08),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(olla\s+de\s+presion|olla\s+expres|en\s+olla\s+de\s+presion|en\s+olla\s+expres)\b"#, options: .caseInsensitive), method: "olla_presion", kcal: 0.90, protein: 0.95, carbs: 1.0, fats: 1.0, water: 0.12),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(escalfado|escalfadito|pochado|poche|huevo\s+poche|en\s+agua\s+con\s+vinagre|en\s+caldo\s+suave|a\s+fuego\s+muy\s+lento|apenas\s+un\s+temblor|sin\s+que\s+hierva|a\s+punto\s+de\s+ebullicion|con\s+agua\s+temblando|con\s+aguita\s+temblorosa)\b"#, options: .caseInsensitive), method: "escalfado", kcal: 0.95, protein: 1.0, carbs: 1.0, fats: 1.0, water: 0.05),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(blanqueado|blanqueadito|dar\s+un\s+hervor|hervir\s+un\s+minuto|pasado\s+por\s+agua|huevo\s+tibio|huevo\s+mollet)\b"#, options: .caseInsensitive), method: "blanqueado", kcal: 0.98, protein: 1.0, carbs: 1.0, fats: 1.0, water: 0.03),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(microondas|microondeado|al\s+micro)\b"#, options: .caseInsensitive), method: "microondas", kcal: 0.98, protein: 1.0, carbs: 1.0, fats: 1.0, water: -0.05),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(sous\s+vide|al\s+vacio|envasado\s+y\s+cocido|en\s+bolsa\s+sellada|a\s+baja\s+temperatura|coccion\s+controlada|en\s+termocirculador|en\s+olla\s+con\s+temperatura\s+exacta|coccion\s+precisa|coccion\s+lenta\s+al\s+vacio)\b"#, options: .caseInsensitive), method: "sous_vide", kcal: 1.02, protein: 1.0, carbs: 1.0, fats: 1.0, water: 0.0),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(ceviche|leche\s+de\s+tigre|aguachile|tiradito|carpaccio|coccion\s+acida|marinado\s+acido|macerado\s+en\s+acido)\b"#, options: .caseInsensitive), method: "ceviche", kcal: 0.98, protein: 1.0, carbs: 1.0, fats: 1.0, water: 0.05),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(fermentado|fermentacion)\b"#, options: .caseInsensitive), method: "fermentado", kcal: 0.93, protein: 1.05, carbs: 0.90, fats: 1.0, water: 0.0),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(encurtido|en\s+vinagreta|en\s+salmuera|salado\s+y\s+secado|en\s+salazon)\b"#, options: .caseInsensitive), method: "encurtido", kcal: 0.95, protein: 1.0, carbs: 1.0, fats: 1.0, water: 0.10),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(a\s+la\s+plancha|a\s+la\s+planchita|plancheado|bien\s+plancheado|a\s+la\s+chapa|a\s+la\s+chapita|a\s+la\s+chapa\s+volcanica|al\s+fierro|a\s+la\s+bifera|sellado|marcado\s+a\s+la\s+plancha)\b"#, options: .caseInsensitive), method: "plancha_sin_aceite", kcal: 1.0, protein: 1.05, carbs: 1.0, fats: 0.95, water: -0.15),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(a\s+la\s+parrilla|a\s+la\s+parrillita|parrillado|asado\s+a\s+la\s+parrilla|a\s+la\s+brasa|al\s+carbon|a\s+la\s+lena|al\s+asador|al\s+espeto|al\s+espeton|en\s+varal|en\s+cruz|al\s+espiedo|al\s+pincho|ahumado\s+en\s+parrilla|ahumadito|barbacoa\s+de\s+tambor)\b"#, options: .caseInsensitive), method: "parrilla", kcal: 1.05, protein: 1.10, carbs: 1.0, fats: 0.90, water: -0.20),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(ahumado|ahumadito|en\s+frio|en\s+caliente|con\s+lena\s+aromatica|al\s+humo|humeado|con\s+humito|en\s+ahumador|en\s+tambor\s+ahumador|en\s+caja\s+de\s+ahumar|con\s+virutas\s+de\s+madera)\b"#, options: .caseInsensitive), method: "ahumado", kcal: 1.10, protein: 1.05, carbs: 1.0, fats: 0.95, water: -0.15),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(curado|en\s+salazon|salmuera|en\s+salmuera|salado\s+y\s+secado)\b"#, options: .caseInsensitive), method: "curado", kcal: 1.30, protein: 1.10, carbs: 1.0, fats: 1.05, water: -0.30),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(asado\s+al\s+horno|al\s+horno|horneado|en\s+horno\s+de\s+lena|a\s+la\s+piedra|al\s+rescoldo|a\s+las\s+brasas|en\s+cazuela\s+de\s+barro\s+al\s+horno|dorado\s+al\s+horno|a\s+la\s+sal|envuelto\s+en\s+papillote|empapelado|en\s+olla\s+de\s+hierro\s+tapada|en\s+horno\s+holandes|gratinado|gratinadito|al\s+graten|con\s+costra|con\s+cubierta\s+crocante|al\s+golpe\s+de\s+horno|un\s+golpe\s+de\s+horno\s+fuerte\s+al\s+principio|horno\s+fuerte|horno\s+suave|horno\s+medio|a\s+fuego\s+de\s+horno)\b"#, options: .caseInsensitive), method: "horno_sin_grasa", kcal: 1.15, protein: 1.10, carbs: 1.05, fats: 0.95, water: -0.25),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(salteado|salteadito|salteado\s+al\s+wok|wok-wok|al\s+wok|revuelto\s+en\s+el\s+wok|vuelta\s+y\s+vuelta\s+en\s+la\s+sarten|rehogado|rehogadito|sofrito|sofritito|pochado\s+en\s+aceite|dorado\s+en\s+sarten|bien\s+doradito|tostado\s+en\s+sarten|refrito|refritito)\b"#, options: .caseInsensitive), method: "frito", kcal: 1.10, protein: 1.10, carbs: 1.0, fats: 1.00, water: -0.20),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(confitado|confitadito|en\s+su\s+propia\s+grasa|en\s+manteca|en\s+aceite\s+templado|confitar|en\s+aceite\s+a\s+baja\s+temperatura|sumergido\s+en\s+aceite\s+tibio|a\s+80\s+grados|textura\s+melosa\s+por\s+confitado|desmigable|confitura\s+de\s+carne)\b"#, options: .caseInsensitive), method: "confitado", kcal: 2.50, protein: 1.0, carbs: 1.0, fats: 3.0, water: -0.20),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(estofado|estofadito|guisado|guisadito|guiso|guisote|en\s+salsa|a\s+la\s+cacerola|en\s+cazuela|cazuelita|en\s+olla\s+de\s+barro|a\s+fuego\s+lento\s+tapado|coccion\s+prolongada|a\s+la\s+antigua|de\s+toda\s+la\s+vida|como\s+el\s+de\s+la\s+abuela|casero|guiso\s+de\s+puchero|cocido|puchero|olla\s+podrida|cocido\s+madrileno|sancochado|sudado|sudadito|al\s+vapor\s+de\s+su\s+propio\s+jugo|en\s+su\s+jugo|en\s+juguito|a\s+la\s+olla|en\s+paila|en\s+caldero|en\s+fondo|con\s+fondo\s+oscuro|con\s+fondo\s+claro|desglasado|reducido|concentrado|espesadito|trabado|ligado|meloso|con\s+textura\s+de\s+terciopelo)\b"#, options: .caseInsensitive), method: "estofado", kcal: 1.30, protein: 1.0, carbs: 1.0, fats: 1.20, water: 0.05),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(braseado|al\s+brasier|en\s+cocotte|en\s+horno\s+holandes\s+tapado|primera\s+sellada\s+luego\s+liquido|un\s+cuarto\s+de\s+liquido|coccion\s+mixta|primero\s+dorado\s+luego\s+guisado|fondo\s+corto)\b"#, options: .caseInsensitive), method: "braseado", kcal: 1.25, protein: 1.05, carbs: 1.0, fats: 1.15, water: 0.0),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(mantecado|enmantecado|en\s+mantequilla\s+clarificada|en\s+ghee|en\s+aceite\s+de\s+oliva\s+suave)\b"#, options: .caseInsensitive), method: "mantecado", kcal: 1.30, protein: 1.0, carbs: 1.0, fats: 1.40, water: -0.05),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(glaseado|con\s+brillo|lacado|con\s+espejo|caramelizado|acaramelado)\b"#, options: .caseInsensitive), method: "glaseado", kcal: 1.30, protein: 1.0, carbs: 1.15, fats: 1.0, water: -0.10),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(frito|fritito|fritanga|fritura|freir\s+en\s+abundante\s+aceite|freir\s+en\s+aceite\s+profundo|sumergido\s+en\s+aceite\s+caliente|por\s+inmersion|frito\s+en\s+sarten|fritura\s+superficial|fritura\s+ligera|en\s+freidora\s+de\s+aire|air\s+fryer|sin\s+aceite\s+\(air\s+fryer\)|sin\s+aceite\s+en\s+freidora)\b"#, options: .caseInsensitive), method: "fritura_superficial", kcal: 2.0, protein: 1.0, carbs: 1.0, fats: 2.5, water: -0.15),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(gratinado\s+con\s+queso|gratinado\s+con\s+bechamel|gratinado\s+burbujeante|con\s+burbujitas)\b"#, options: .caseInsensitive), method: "gratinado", kcal: 1.80, protein: 1.20, carbs: 1.15, fats: 1.60, water: -0.15),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(rebozado|empanizado|empanado|enharinado\s+y\s+frito|en\s+tempura|tempurizado|rebozado\s+en\s+panko|capeado|capeadito|milanesa|milanesita|escalope|escalopita|a\s+la\s+romana|en\s+gabardina|bunuelo|bunuelito|croqueta|croquetita|fritura\s+de\s+calle|fritura\s+casera)\b"#, options: .caseInsensitive), method: "rebozado_frito", kcal: 3.5, protein: 1.15, carbs: 1.30, fats: 3.5, water: 0.10),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(freir\s+en\s+aceite\s+profundo|fritura\s+profunda|fritura\s+en\s+inmersion)\b"#, options: .caseInsensitive), method: "fritura_profunda", kcal: 3.0, protein: 1.0, carbs: 1.0, fats: 3.5, water: -0.20),
        MethodPattern(regex: try! NSRegularExpression(pattern: #"\b(deshidratado|seco\s+al\s+sol|al\s+oreo|orear|en\s+deshidratador)\b"#, options: .caseInsensitive), method: "deshidratado", kcal: 4.0, protein: 3.0, carbs: 3.0, fats: 3.0, water: -0.85),
    ]

    private static let DONENESS_PATTERNS: [DonenessPattern] = [
        DonenessPattern(regex: try! NSRegularExpression(pattern: #"\b(al\s+dente|aldentito)\b"#, options: .caseInsensitive), method: "al_dente", kcal: 1.0, protein: 1.0, carbs: 1.0, fats: 1.0),
        DonenessPattern(regex: try! NSRegularExpression(pattern: #"\b(termino\s+medio|a\s+punto|en\s+su\s+punto\s+exacto|jugoso|rosadito)\b"#, options: .caseInsensitive), method: "termino_medio", kcal: 1.0, protein: 1.0, carbs: 1.0, fats: 1.0),
        DonenessPattern(regex: try! NSRegularExpression(pattern: #"\b(tres\s+cuartos|bien\s+cocido|bien\s+pasado)\b"#, options: .caseInsensitive), method: "bien_cocido", kcal: 0.95, protein: 1.0, carbs: 1.0, fats: 0.95),
        DonenessPattern(regex: try! NSRegularExpression(pattern: #"\b(seco|reseco|como\s+suela\s+de\s+zapato|como\s+carton)\b"#, options: .caseInsensitive), method: "reseco", kcal: 0.90, protein: 1.05, carbs: 1.05, fats: 0.90),
        DonenessPattern(regex: try! NSRegularExpression(pattern: #"\b(chicloso|gomoso|crudo\s+por\s+dentro|vivo|que\s+muge|sangrante|que\s+suelte\s+sangre)\b"#, options: .caseInsensitive), method: "crudo_interior", kcal: 1.05, protein: 1.0, carbs: 1.0, fats: 1.05),
        DonenessPattern(regex: try! NSRegularExpression(pattern: #"\b(apenas\s+cocido|pasado\s+de\s+coccion|recocido|deshecho|que\s+se\s+deshaga|a\s+punto\s+de\s+deshacerse)\b"#, options: .caseInsensitive), method: "pasado", kcal: 0.90, protein: 0.95, carbs: 1.0, fats: 0.95),
        DonenessPattern(regex: try! NSRegularExpression(pattern: #"\b(blandito|suavecito|firme\s+pero\s+tierno|que\s+se\s+deshaga\s+en\s+la\s+boca|butter-soft|mantequilloso|derretido|fundente|que\s+se\s+corre)\b"#, options: .caseInsensitive), method: "tierno", kcal: 1.0, protein: 1.0, carbs: 1.0, fats: 1.0),
        DonenessPattern(regex: try! NSRegularExpression(pattern: #"\b(ingles|muy\s+poco\s+hecho|bleu|casi\s+crudo)\b"#, options: .caseInsensitive), method: "bleu", kcal: 1.02, protein: 1.0, carbs: 1.0, fats: 1.02),
        DonenessPattern(regex: try! NSRegularExpression(pattern: #"\b(chamuscado|tatemado|tatemar|quemadito|con\s+puntitos\s+negros)\b"#, options: .caseInsensitive), method: "chamuscado", kcal: 1.05, protein: 1.05, carbs: 0.95, fats: 1.0),
    ]

    private static let COLLOQUIAL_DONENESS: [ColloquialPattern] = [
        ColloquialPattern(regex: try! NSRegularExpression(pattern: #"\b(vuelta\s+y\s+vuelta)\b"#, options: .caseInsensitive), factor: 1.02),
        ColloquialPattern(regex: try! NSRegularExpression(pattern: #"\b(bien\s+doradito|crocante|crujiente|con\s+corteza|con\s+costra)\b"#, options: .caseInsensitive), factor: 1.10),
        ColloquialPattern(regex: try! NSRegularExpression(pattern: #"\b(tostado|tostadito|bien\s+tostado|ligeramente\s+tostado|torrado|torradito|torrefacto|dorado|doradito|al\s+sarten\s+seco|a\s+fuego\s+seco|en\s+comal|comaleado|en\s+budare|en\s+callana|tatemado\s+en\s+comal)\b"#, options: .caseInsensitive), factor: 1.08),
    ]

    private static let MODERN_METHODS: [ModernMethod] = [
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+olla\s+de\s+coccion\s+lenta|en\s+slow\s+cooker|en\s+crockpot)\b"#, options: .caseInsensitive), method: "slow_cooker", factor: 0.95),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+Instant\s+Pot|olla\s+multifuncion)\b"#, options: .caseInsensitive), method: "multifuncion", factor: 0.95),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+arrocera|arrocero)\b"#, options: .caseInsensitive), method: "arrocera", factor: 0.95),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+termomix|robot|robotizado)\b"#, options: .caseInsensitive), method: "robot", factor: 0.98),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+horno\s+de\s+conveccion)\b"#, options: .caseInsensitive), method: "conveccion", factor: 1.10),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+grill\s+electrico)\b"#, options: .caseInsensitive), method: "grill_electrico", factor: 1.05),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+fondue)\b"#, options: .caseInsensitive), method: "fondue", factor: 1.40),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+raclette)\b"#, options: .caseInsensitive), method: "raclette", factor: 1.50),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+tagine|en\s+tajin)\b"#, options: .caseInsensitive), method: "tagine", factor: 1.20),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+paellera|paellero)\b"#, options: .caseInsensitive), method: "paellera", factor: 1.15),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+cazuela\s+de\s+hierro|en\s+sarten\s+de\s+hierro\s+fundido|en\s+wok\s+de\s+acero|en\s+olla\s+de\s+cobre)\b"#, options: .caseInsensitive), method: "hierro", factor: 1.05),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(bajo\s+tierra|pachamanca|curanto|en\s+hoyo)\b"#, options: .caseInsensitive), method: "bajo_tierra", factor: 1.10),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(en\s+espeto|al\s+asador|en\s+cruz)\b"#, options: .caseInsensitive), method: "asador", factor: 1.05),
        ModernMethod(regex: try! NSRegularExpression(pattern: #"\b(al\s+disco|de\s+arado)\b"#, options: .caseInsensitive), method: "disco", factor: 1.25),
    ]

    static func parse(_ text: String) -> CookingMethodResult? {
        let lower = text.lowercased()
        let range = NSRange(lower.startIndex..., in: lower)

        for entry in METHOD_PATTERNS {
            if entry.regex.firstMatch(in: lower, options: [], range: range) != nil {
                let category: CookingCategory
                if entry.kcal < 1.1 { category = .neutral }
                else if entry.kcal < 1.5 { category = .moderate }
                else if entry.kcal < 2.5 { category = .high }
                else { category = .veryHigh }

                return CookingMethodResult(
                    method: entry.method, category: category,
                    kcalFactor: entry.kcal, proteinFactor: entry.protein,
                    carbsFactor: entry.carbs, fatsFactor: entry.fats,
                    waterChange: entry.water, confidence: 0.85
                )
            }
        }
        for entry in DONENESS_PATTERNS {
            if entry.regex.firstMatch(in: lower, options: [], range: range) != nil {
                return CookingMethodResult(
                    method: entry.method, category: .neutral,
                    kcalFactor: entry.kcal, proteinFactor: entry.protein,
                    carbsFactor: entry.carbs, fatsFactor: entry.fats,
                    waterChange: 0.0, confidence: 0.70
                )
            }
        }
        for entry in COLLOQUIAL_DONENESS {
            if entry.regex.firstMatch(in: lower, options: [], range: range) != nil {
                return CookingMethodResult(
                    method: "coloquial", category: .neutral,
                    kcalFactor: entry.factor, proteinFactor: 1.0,
                    carbsFactor: 1.0, fatsFactor: 1.0,
                    waterChange: -0.10, confidence: 0.60
                )
            }
        }
        for entry in MODERN_METHODS {
            if entry.regex.firstMatch(in: lower, options: [], range: range) != nil {
                return CookingMethodResult(
                    method: entry.method, category: entry.factor < 1.1 ? .neutral : .moderate,
                    kcalFactor: entry.factor, proteinFactor: 1.0,
                    carbsFactor: 1.0, fatsFactor: 1.0,
                    waterChange: -0.10, confidence: 0.75
                )
            }
        }
        return nil
    }

    static func applyOilFactor(
        baseKcal: Double, baseProtein: Double, baseCarbs: Double, baseFats: Double,
        method: CookingMethodResult?, tablespoonsOil: Double = 0.0
    ) -> QuadMacro {
        let mKcal = method?.kcalFactor ?? 1.0
        let mProtein = method?.proteinFactor ?? 1.0
        let mCarbs = method?.carbsFactor ?? 1.0
        let mFats = method?.fatsFactor ?? 1.0

        var kcal = baseKcal * mKcal
        var protein = baseProtein * mProtein
        var carbs = baseCarbs * mCarbs
        var fats = baseFats * mFats

        if tablespoonsOil > 0 {
            kcal += tablespoonsOil * 120.0
            fats += tablespoonsOil * 13.5
        }

        return QuadMacro(
            calories: round1(kcal),
            protein: round1(protein),
            carbs: round1(carbs),
            fats: round1(fats)
        )
    }

    struct QuadMacro {
        let calories: Double
        let protein: Double
        let carbs: Double
        let fats: Double
    }
}
