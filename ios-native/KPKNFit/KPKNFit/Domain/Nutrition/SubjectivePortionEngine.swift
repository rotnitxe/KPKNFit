import Foundation

enum SubjectivePortionEngine {

    enum FoodDensityCategory: CaseIterable {
        case liquid
        case powder
        case grain
        case vegetable
        case protein
        case fat
        case dairy
        case nuts
        case fruit
        case mixed

        var densityGPerMl: Double {
            switch self {
            case .liquid: return 1.0
            case .powder: return 0.6
            case .grain: return 0.85
            case .vegetable: return 0.7
            case .protein: return 1.0
            case .fat: return 0.9
            case .dairy: return 1.03
            case .nuts: return 0.65
            case .fruit: return 0.6
            case .mixed: return 0.8
            }
        }
    }

    struct PortionResult {
        let grams: Double
        let confidence: Double
        let source: String
        let expression: String
        let relativeFactor: Double
    }

    private struct PatternEntry {
        let regex: NSRegularExpression
        let value: Double
        let source: String
    }

    private static func entry(_ pattern: String, _ value: Double, _ source: String) -> PatternEntry {
        PatternEntry(
            regex: try! NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
            value: value,
            source: source
        )
    }

    // ─── Utensils: base volume in ml ─────────────────────────────────────

    private static let UTENSIL_PATTERNS: [PatternEntry] = [
        entry("\\b(un|una|1)\\s+cucharaditas?\\b", 5.0, "cucharadita"),
        entry("\\b(un|una|1)\\s+cucharadas?\\s+(?:de\\s+)?(?:postre)?\\b", 10.0, "cucharada_postre"),
        entry("\\b(un|una|1)\\s+cucharadas?\\s+soperas?\\b", 20.0, "cucharada_sopera"),
        entry("\\b(un|una|1)\\s+cucharadas?\\s+colmadas?\\b", 25.0, "cucharada_colmada"),
        entry("\\b(un|una|1)\\s+cucharadas?\\s+generosas?\\b", 22.0, "cucharada_generosa"),
        entry("\\b(\\d+(?:[.,]\\d+)?)\\s+cucharaditas?\\b", 5.0, "cucharadita_multi"),
        entry("\\b(\\d+(?:[.,]\\d+)?)\\s+cucharadas?\\b", 15.0, "cucharada_multi"),
        entry("\\bmedia\\s+cucharadita\\b", 2.5, "media_cucharadita"),
        entry("\\bmedia\\s+cucharada\\b", 7.5, "media_cucharada"),
        entry("\\bun\\s+cuarto\\s+de\\s+cucharadita\\b", 1.25, "cuarto_cucharadita"),

        entry("\\b(un|una|1)\\s+cuchar[oó]n\\b", 90.0, "cucharon"),
        entry("\\bmedio\\s+cuchar[oó]n\\b", 45.0, "medio_cucharon"),

        entry("\\b(un|una|1)\\s+tazas?\\s+rebosantes?\\b", 280.0, "taza_rebosante"),
        entry("\\b(un|una|1)\\s+tazas?\\s+de\\s+desayuno\\b", 200.0, "taza_desayuno"),
        entry("\\b(un|una|1)\\s+tazas?\\b", 250.0, "taza"),
        entry("\\bmedia\\s+taza\\b", 125.0, "media_taza"),
        entry("\\bun\\s+cuarto\\s+de\\s+taza\\b", 62.5, "cuarto_taza"),
        entry("\\bun\\s+tercio\\s+de\\s+taza\\b", 83.0, "tercio_taza"),
        entry("\\b(un|una|1)\\s+tacitas?\\s+de\\s+caf[ée]\\b", 60.0, "tacita_cafe"),

        entry("\\b(un|una|1)\\s+vasos?\\b", 250.0, "vaso"),
        entry("\\bmedio\\s+vaso\\b", 125.0, "medio_vaso"),
        entry("\\b(un|una|1)\\s+copas?\\b", 150.0, "copa"),
        entry("\\b(un|una|1)\\s+copitas?\\b", 50.0, "copita"),
        entry("\\b(un|una|1)\\s+caballitos?\\b", 45.0, "caballito"),
        entry("\\b(un|una|1)\\s+dedales?\\b", 5.0, "dedal"),

        entry("\\b(un|una|1)\\s+pocillos?\\b", 60.0, "pocillo"),
        entry("\\b(un|una|1)\\s+bols?\\b", 300.0, "bol"),
        entry("\\b(un|una|1)\\s+platos?\\s+hondos?\\b", 400.0, "plato_hondo"),
        entry("\\b(un|una|1)\\s+platos?\\s+grandes?\\b", 350.0, "plato_grande"),
        entry("\\b(un|una|1)\\s+platos?\\b", 250.0, "plato"),
        entry("\\b(un|una|1)\\s+tazones?\\b", 300.0, "tazon"),
        entry("\\b(un|una|1)\\s+fuente\\s+de\\b", 500.0, "fuente"),
    ]

    // ─── Body/Gestures: base grams per category ──────────────────────────

    private static let BODY_PATTERNS: [PatternEntry] = [
        entry("\\bun\\s+puñadito\\b", 20.0, "puñadito"),
        entry("\\bun\\s+puñado\\s+gigante\\b", 50.0, "puñado_gigante"),
        entry("\\bun\\s+puñado\\s+generoso\\b", 45.0, "puñado_generoso"),
        entry("\\bun\\s+puñado\\b", 30.0, "puñado"),
        entry("\\b(lo\\s+que\\s+agarr[óo]\\s+la\\s+mano|un\\s+puñado\\s+de)\\b", 40.0, "mano"),

        entry("\\buna\\s+pizquita\\b", 0.3, "pizquita"),
        entry("\\buna\\s+pizca\\b", 0.5, "pizca"),
        entry("\\bun\\s+pellizquito\\b", 0.5, "pellizquito"),
        entry("\\bun\\s+pellizco\\b", 1.0, "pellizco"),

        entry("\\bun\\s+dedo\\b", 20.0, "dedo"),
        entry("\\bdos\\s+dedos\\b", 30.0, "dos_dedos"),
        entry("\\btres\\s+dedos\\b", 45.0, "tres_dedos"),

        entry("\\bla\\s+palma\\s+de\\s+la\\s+mano\\b", 135.0, "palma"),
        entry("\\bel\\s+puño\\s+cerrado\\b", 175.0, "puño_cerrado"),
        entry("\\bun\\s+puño\\b", 150.0, "puño"),
        entry("\\bun\\s+nudillo\\b", 10.0, "nudillo"),

        entry("\\bun\\s+diente\\s+(?:de\\s+)?ajo\\b", 5.0, "diente_ajo"),
        entry("\\bmedia\\s+cabeza\\s+de\\s+ajo\\b", 15.0, "media_cabeza_ajo"),
        entry("\\buna\\s+cabeza\\s+de\\s+ajo\\b", 30.0, "cabeza_ajo"),
        entry("\\buna\\s+rama\\b", 10.0, "rama"),
        entry("\\bun\\s+ramillete\\b", 15.0, "ramillete"),
        entry("\\bun\\s+tallo\\b", 15.0, "tallo"),
        entry("\\buna\\s+hoja\\b", 5.0, "hoja"),
        entry("\\bunas?\\s+hojitas?\\b", 3.0, "hojitas"),
        entry("\\bun\\s+cogollo\\b", 30.0, "cogollo"),
        entry("\\bun\\s+ramo\\b", 20.0, "ramo"),
        entry("\\buna\\s+vara\\b", 5.0, "vara"),
        entry("\\buna\\s+astilla\\b", 2.0, "astilla"),

        entry("\\buna\\s+rodaja\\b", 30.0, "rodaja"),
        entry("\\bmedia\\s+rodaja\\b", 15.0, "media_rodaja"),
        entry("\\buna\\s+tajada\\b", 40.0, "tajada"),
        entry("\\bun\\s+trozo\\b", 50.0, "trozo"),
        entry("\\bun\\s+trocito\\b", 20.0, "trocito"),
        entry("\\bun\\s+pedazo\\b", 50.0, "pedazo"),
        entry("\\bun\\s+pedacito\\b", 20.0, "pedacito"),
        entry("\\buna\\s+loncha\\b", 25.0, "loncha"),
        entry("\\buna\\s+lonchita\\b", 15.0, "lonchita"),
        entry("\\buna\\s+l[aá]mina\\b", 15.0, "lamina"),
        entry("\\buna\\s+tira\\b", 15.0, "tira"),
        entry("\\buna\\s+tirita\\b", 8.0, "tirita"),
        entry("\\bun\\s+gajo\\b", 40.0, "gajo"),
        entry("\\buna\\s+raja\\b", 30.0, "raja"),
        entry("\\buna\\s+cuña\\b", 40.0, "cuña"),
        entry("\\buna\\s+esquina\\b", 60.0, "esquina"),
        entry("\\buna\\s+punta\\b", 40.0, "punta"),
        entry("\\bun\\s+tri[aá]ngulo\\b", 60.0, "triangulo"),
        entry("\\bun\\s+dado\\b", 20.0, "dado"),
        entry("\\bun\\s+cubito\\b", 10.0, "cubito"),

        entry("\\buna\\s+pastilla\\b", 10.0, "pastilla"),
        entry("\\buna\\s+tableta\\b", 100.0, "tableta"),
        entry("\\buna\\s+onza\\b", 28.0, "onza"),
        entry("\\buna\\s+barra\\b", 250.0, "barra"),
        entry("\\bun\\s+cuarto\\s+de\\s+barra\\b", 62.0, "cuarto_barra"),
        entry("\\buna\\s+nuez\\b", 20.0, "nuez"),
        entry("\\buna\\s+avellana\\b", 10.0, "avellana"),
        entry("\\buna\\s+aceituna\\b", 5.0, "aceituna"),
        entry("\\bun\\s+garbanzo\\b", 1.0, "garbanzo"),
        entry("\\bun\\s+grano\\b", 0.03, "grano"),
    ]

    // ─── Subjective/Colloquial: relative factor ──────────────────────────

    private static let SUBJECTIVE_PATTERNS: [PatternEntry] = [
        entry("\\bun\\s+poquit[ií]n\\b", 0.01, "poquitin"),
        entry("\\bun\\s+tantico\\b", 0.02, "tantico"),
        entry("\\bun\\s+chin\\b", 0.02, "chin"),
        entry("\\bun\\s+poquillo\\b", 0.03, "poquillo"),
        entry("\\bun\\s+pel[ií]n\\b", 0.03, "pelin"),
        entry("\\buna\\s+miaja\\b", 0.02, "miaja"),
        entry("\\buna\\s+mijita\\b", 0.02, "mijita"),

        entry("\\buna\\s+gotita\\b", 0.03, "gotita"),
        entry("\\buna\\s+gota\\b", 0.05, "gota"),
        entry("\\bun\\s+hilito\\b", 0.03, "hilito"),
        entry("\\bun\\s+hilo\\b", 0.05, "hilo"),
        entry("\\bun\\s+velo\\b", 0.05, "velo"),

        entry("\\bun\\s+chorrito\\b", 0.10, "chorrito"),
        entry("\\bun\\s+chorret[oó]n\\b", 0.20, "chorreton"),
        entry("\\bun\\s+chorro\\b", 0.15, "chorro"),
        entry("\\bun\\s+cul[ií]n\\b", 0.10, "culin"),
        entry("\\bun\\s+culillo\\b", 0.12, "culillo"),
        entry("\\bun\\s+fondo\\b", 0.10, "fondo"),
        entry("\\bun\\s+poquito\\b", 0.10, "poquito"),
        entry("\\bun\\s+poco\\b", 0.15, "poco"),

        entry("\\buna\\s+capita\\b", 0.5, "capita"),
        entry("\\buna\\s+capa\\b", 0.7, "capa"),
        entry("\\buna\\s+capa\\s+fina\\b", 0.4, "capa_fina"),
        entry("\\buna\\s+medida\\b", 1.0, "medida"),
        entry("\\buna\\s+medida\\s+escasa\\b", 0.7, "medida_escasa"),
        entry("\\buna\\s+raci[oó]n\\s+individual\\b", 1.0, "racion_individual"),
        entry("\\buna\\s+porci[oó]n\\b", 1.0, "porcion"),
        entry("\\buna\\s+raci[oó]n\\b", 1.0, "racion"),

        entry("\\buna\\s+medida\\s+generosa\\b", 1.3, "medida_generosa"),
        entry("\\buna\\s+raci[oó]n\\s+generosa\\b", 1.4, "racion_generosa"),
        entry("\\buna\\s+raci[oó]n\\s+doble\\b", 2.0, "racion_doble"),
        entry("\\buna\\s+media\\s+raci[oó]n\\b", 0.5, "media_racion"),

        entry("\\bun\\s+mont[oó]n\\b", 2.0, "monton"),
        entry("\\bun\\s+montoncito\\b", 1.5, "montoncito"),
        entry("\\bun\\s+cerro\\b", 3.0, "cerro"),
        entry("\\buna\\s+barbaridad\\b", 3.5, "barbaridad"),
        entry("\\buna\\s+bestialidad\\b", 4.0, "bestialidad"),
        entry("\\buna\\s+exageraci[oó]n\\b", 3.5, "exageracion"),
        entry("\\bun\\s+disparate\\b", 3.0, "disparate"),
        entry("\\bun\\s+porr[oó]n\\b", 3.0, "porron"),

        entry("\\bun\\s+scoop\\s+generoso\\b", 40.0, "scoop_generoso"),
        entry("\\b(\\d+(?:[.,]\\d+)?)\\s+scoops?\\s+generosos?\\b", 40.0, "scoops_generosos"),
        entry("\\bun\\s+scoop\\b", 30.0, "scoop"),
        entry("\\b(\\d+(?:[.,]\\d+)?)\\s+scoops?\\b", 30.0, "scoops"),
        entry("\\buna\\s+medida\\s+(?:de\\s+)?(?:prote[ií]na|suplemento)\\b", 30.0, "medida_proteina"),
    ]

    // ─── Bread/Doughs ────────────────────────────────────────────────────

    private static let BREAD_PATTERNS: [PatternEntry] = [
        entry("\\bmedia\\s+marraqueta\\b", 50.0, "media_marraqueta"),
        entry("\\buna\\s+marraqueta\\b", 100.0, "marraqueta"),
        entry("\\bdiente\\s+de\\s+marraqueta\\b", 25.0, "diente_marraqueta"),
        entry("\\buna\\s+hallulla\\b", 80.0, "hallulla"),
        entry("\\bpan\\s+amasado\\b", 100.0, "pan_amasado"),
        entry("\\buna\\s+rebanada\\b", 30.0, "rebanada"),
        entry("\\buna\\s+rebanadita\\b", 15.0, "rebanadita"),
        entry("\\buna\\s+hogaza\\b", 500.0, "hogaza"),
        entry("\\buna\\s+barra\\s+de\\s+pan\\b", 250.0, "barra_pan"),
        entry("\\bun\\s+bollo\\b", 50.0, "bollo"),
        entry("\\bun\\s+panecillo\\b", 40.0, "panecillo"),
        entry("\\bun\\s+mollete\\b", 60.0, "mollete"),
        entry("\\buna\\s+arepa\\b", 80.0, "arepa"),
        entry("\\buna\\s+tortilla\\b", 40.0, "tortilla"),
        entry("\\buna\\s+empanada\\b", 120.0, "empanada"),
        entry("\\bun\\s+tamal\\b", 150.0, "tamal"),
        entry("\\bun\\s+pastel\\b", 120.0, "pastel"),
        entry("\\bun\\s+trozo\\s+de\\s+pastel\\b", 100.0, "trozo_pastel"),
        entry("\\bun\\s+bizcocho\\b", 30.0, "bizcocho"),
        entry("\\buna\\s+galleta\\b", 10.0, "galleta"),
        entry("\\bun\\s+puñado\\s+de\\s+galletas?\\b", 30.0, "puñado_galletas"),
        entry("\\buna\\s+bolsita\\s+de\\s+snacks?\\b", 45.0, "bolsita_snacks"),
        entry("\\bun\\s+paquete\\b", 150.0, "paquete"),
        entry("\\bun\\s+sobre\\b", 10.0, "sobre"),
    ]

    // ─── Commercial containers ───────────────────────────────────────────

    private static let CONTAINER_PATTERNS: [PatternEntry] = [
        entry("\\buna\\s+lata\\b", 180.0, "lata"),
        entry("\\bun\\s+bote\\b", 400.0, "bote"),
        entry("\\bun\\s+frasco\\b", 250.0, "frasco"),
        entry("\\buna\\s+botella\\b", 750.0, "botella"),
        entry("\\bmedia\\s+botella\\b", 375.0, "media_botella"),
        entry("\\bun\\s+cuarto\\s+de\\s+botella\\b", 187.0, "cuarto_botella"),
        entry("\\buna\\s+c[aá]psula\\b", 6.0, "capsula"),
        entry("\\bun\\s+cart[oó]n\\b", 1000.0, "carton"),
        entry("\\buna\\s+caja\\b", 500.0, "caja"),
        entry("\\buna\\s+bolsa\\b", 200.0, "bolsa"),
    ]

    // ─── Object comparisons ──────────────────────────────────────────────

    private static let COMPARISON_PATTERNS: [PatternEntry] = [
        entry("\\bdel\\s+ta[mañ]o\\s+de\\s+una\\s+nuez\\b", 22.0, "tamano_nuez"),
        entry("\\bdel\\s+ta[mañ]o\\s+de\\s+una\\s+aceituna\\b", 10.0, "tamano_aceituna"),
        entry("\\bdel\\s+ta[mañ]o\\s+de\\s+una\\s+pelota\\s+de\\s+golf\\b", 90.0, "tamano_golf"),
        entry("\\bdel\\s+ta[mañ]o\\s+de\\s+un\\s+pu[ñn]o\\b", 175.0, "tamano_puño"),
        entry("\\bdel\\s+ta[mañ]o\\s+de\\s+la\\s+palma\\b", 135.0, "tamano_palma"),
        entry("\\bdel\\s+ta[mañ]o\\s+de\\s+un\\s+dedo\\s+pulgar\\b", 25.0, "tamano_pulgar"),
        entry("\\bdel\\s+ta[mañ]o\\s+de\\s+una\\s+baraja\\b", 110.0, "tamano_baraja"),
        entry("\\bcomo\\s+un\\s+dado\\b", 22.0, "como_dado"),
        entry("\\bcomo\\s+un\\s+cubo\\s+de\\s+hielo\\b", 30.0, "como_hielo"),
        entry("\\bcomo\\s+una\\s+moneda\\b", 12.0, "como_moneda"),
    ]

    // ─── Intensifiers ────────────────────────────────────────────────────

    private static let INTENSIFIER_FACTORS: [String: Double] = [
        "gigante": 1.8,
        "generoso": 1.4,
        "colmado": 1.5,
        "rebosante": 1.6,
        "grande": 1.3,
        "pequeño": 0.7,
        "chico": 0.7,
        "fino": 0.6,
        "delgado": 0.6,
        "grueso": 1.4,
        "gordo": 1.5,
    ]

    // ─── Standard portions per food category ─────────────────────────────

    private static let STANDARD_PORTIONS: [FoodDensityCategory: Double] = [
        .protein: 150.0,
        .grain: 100.0,
        .vegetable: 200.0,
        .fruit: 150.0,
        .dairy: 200.0,
        .nuts: 30.0,
        .fat: 15.0,
        .liquid: 250.0,
        .powder: 30.0,
        .mixed: 200.0,
    ]

    // ─── Internal ────────────────────────────────────────────────────────

    private static let DE_FOOD_PATTERN = try! NSRegularExpression(pattern: "de\\s+([a-záéíóúñü\\s]{2,})", options: [.caseInsensitive])

    private static func extractFoodName(expression: String) -> String? {
        guard let match = DE_FOOD_PATTERN.firstMatch(in: expression, range: NSRange(expression.startIndex..., in: expression)),
              match.numberOfRanges > 1 else { return nil }
        return (expression as NSString).substring(with: match.range(at: 1)).trimmingCharacters(in: .whitespaces)
    }

    // ─── Public API ──────────────────────────────────────────────────────

    static func resolve(
        expression: String,
        foodCategory: FoodDensityCategory? = nil,
        standardPortion: Double? = nil,
        retrievalResult: SemanticPortionRetriever.RetrievalResult? = nil
    ) -> PortionResult? {
        let lower = expression.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)

        // Check retrieval priors first
        if let result = retrievalResult, !result.portionPriors.isEmpty {
            if let foodName = extractFoodName(expression: expression) {
                let priorGrams = SemanticPortionRetriever.getGramsForFood(foodName: foodName, retrievalResult: result)
                if let grams = priorGrams, grams > 0 {
                    return PortionResult(
                        grams: grams,
                        confidence: 0.85,
                        source: "dataset-prior",
                        expression: expression,
                        relativeFactor: 1.0
                    )
                }
            }
        }

        // Check utensil patterns
        for entry in UTENSIL_PATTERNS {
            guard let match = entry.regex.firstMatch(in: lower, range: NSRange(lower.startIndex..., in: lower)) else { continue }
            let qty: Double
            if match.numberOfRanges > 1 {
                let qtyStr = (lower as NSString).substring(with: match.range(at: 1)).replacingOccurrences(of: ",", with: ".")
                qty = Double(qtyStr) ?? 1.0
            } else {
                qty = 1.0
            }
            let category = foodCategory ?? .mixed
            let grams = entry.value * qty * category.densityGPerMl
            return PortionResult(
                grams: grams,
                confidence: 0.75,
                source: "utensil:\(entry.source)",
                expression: expression,
                relativeFactor: qty
            )
        }

        // Check body/gesture patterns
        for entry in BODY_PATTERNS {
            guard let _ = entry.regex.firstMatch(in: lower, range: NSRange(lower.startIndex..., in: lower)) else { continue }
            let category = foodCategory ?? .mixed
            let grams = entry.value * category.densityGPerMl / FoodDensityCategory.mixed.densityGPerMl
            return PortionResult(
                grams: grams,
                confidence: 0.70,
                source: "body:\(entry.source)",
                expression: expression,
                relativeFactor: 1.0
            )
        }

        // Check subjective patterns (relative factors)
        for entry in SUBJECTIVE_PATTERNS {
            guard let _ = entry.regex.firstMatch(in: lower, range: NSRange(lower.startIndex..., in: lower)) else { continue }
            let category = foodCategory ?? .mixed
            let stdPortion = standardPortion ?? STANDARD_PORTIONS[category] ?? 100.0
            let grams = stdPortion * entry.value
            return PortionResult(
                grams: grams,
                confidence: 0.60,
                source: "subjective:\(entry.source)",
                expression: expression,
                relativeFactor: entry.value
            )
        }

        // Check bread patterns
        for entry in BREAD_PATTERNS {
            guard let _ = entry.regex.firstMatch(in: lower, range: NSRange(lower.startIndex..., in: lower)) else { continue }
            return PortionResult(
                grams: entry.value,
                confidence: 0.80,
                source: "bread:\(entry.source)",
                expression: expression,
                relativeFactor: 1.0
            )
        }

        // Check container patterns
        for entry in CONTAINER_PATTERNS {
            guard let _ = entry.regex.firstMatch(in: lower, range: NSRange(lower.startIndex..., in: lower)) else { continue }
            return PortionResult(
                grams: entry.value,
                confidence: 0.65,
                source: "container:\(entry.source)",
                expression: expression,
                relativeFactor: 1.0
            )
        }

        // Check comparison patterns
        for entry in COMPARISON_PATTERNS {
            guard let _ = entry.regex.firstMatch(in: lower, range: NSRange(lower.startIndex..., in: lower)) else { continue }
            return PortionResult(
                grams: entry.value,
                confidence: 0.55,
                source: "comparison:\(entry.source)",
                expression: expression,
                relativeFactor: 1.0
            )
        }

        return nil
    }

    static func detectIntensifier(expression: String) -> Double {
        let lower = expression.lowercased()
        for (keyword, factor) in INTENSIFIER_FACTORS {
            if lower.contains(keyword) {
                return factor
            }
        }
        return 1.0
    }

    static func detectDensityCategory(foodName: String) -> FoodDensityCategory {
        let lower = foodName.lowercased()

        if lower.contains("aceite") || lower.contains("mantequilla") || lower.contains("manteca") || lower.contains("ghee") || lower.contains("margarina") || lower.contains("mayonesa") || lower.contains("mayo") {
            return .fat
        }
        if lower.contains("azúcar") || lower.contains("azucar") || lower.contains("harina") || lower.contains("cacao") || lower.contains("canela") {
            return .powder
        }
        if lower.contains("arroz") || lower.contains("pasta") || lower.contains("quinoa") || lower.contains("avena") || lower.contains("lenteja") || lower.contains("garbanzo") || lower.contains("poroto") {
            return .grain
        }
        if lower.contains("pollo") || lower.contains("carne") || lower.contains("pescado") || lower.contains("cerdo") || lower.contains("vacuno") || lower.contains("pavo") || lower.contains("huevo") || lower.contains("merluza") || lower.contains("salmón") || lower.contains("camarón") {
            return .protein
        }
        if lower.contains("lechuga") || lower.contains("tomate") || lower.contains("cebolla") || lower.contains("zanahoria") || lower.contains("espinaca") || lower.contains("brócoli") || lower.contains("pepino") {
            return .vegetable
        }
        if lower.contains("manzana") || lower.contains("plátano") || lower.contains("naranja") || lower.contains("uva") || lower.contains("frutilla") || lower.contains("pera") {
            return .fruit
        }
        if lower.contains("leche") || lower.contains("yogurt") || lower.contains("yogur") || lower.contains("queso") || lower.contains("crema") {
            return .dairy
        }
        if lower.contains("almendra") || lower.contains("nuez") || lower.contains("maní") || lower.contains("cashew") || lower.contains("chía") {
            return .nuts
        }
        if lower.contains("agua") || lower.contains("jugo") || lower.contains("zumo") || lower.contains("leche") || lower.contains("vino") || lower.contains("cerveza") {
            return .liquid
        }
        return .mixed
    }
}
