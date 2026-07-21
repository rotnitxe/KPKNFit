import Foundation

public enum TextNormalizer {

    private static let EMOJI_MAP: [String: String] = [
        "\u{1F951}": "palta",
        "\u{1F357}": "pollo",
        "\u{1F95A}": "huevo",
        "\u{1F35E}": "pan",
        "\u{1F35A}": "arroz",
        "\u{1F34C}": "platano",
        "\u{1F95B}": "leche",
        "\u{1F34E}": "manzana",
        "\u{1F354}": "hamburguesa",
        "\u{1F355}": "pizza",
        "\u{1F363}": "sushi",
        "\u{1F32E}": "taco",
        "\u{1F32F}": "burrito",
        "\u{1F950}": "pan",
        "\u{1F955}": "zanahoria",
        "\u{1F952}": "pepino",
        "\u{1F345}": "tomate",
        "\u{1F353}": "frutilla",
        "\u{1F95E}": "queso",
        "\u{1F347}": "uva",
        "\u{1F34A}": "naranja",
        "\u{1F34B}": "limon",
        "\u{1F349}": "sandia",
        "\u{1F351}": "durazno",
        "\u{1F352}": "cereza",
        "\u{1F95D}": "kiwi",
        "\u{1F34D}": "pina",
        "\u{1F965}": "coco",
        "\u{1F95C}": "mani",
        "\u{1F33D}": "choclo",
        "\u{1F966}": "brocoli",
        "\u{1F346}": "berenjena",
        "\u{1F957}": "ensalada",
        "\u{1F35D}": "pasta",
        "\u{1F35C}": "sopa",
        "\u{1F372}": "guiso",
        "\u{1F35F}": "papas fritas",
        "\u{1F969}": "carne",
        "\u{1F356}": "carne",
        "\u{1F364}": "camaron",
        "\u{1F41F}": "pescado",
        "\u{1F990}": "camaron",
        "\u{1F370}": "pastel",
        "\u{1F36B}": "chocolate",
        "\u{1F369}": "donut",
        "\u{1F36A}": "galleta",
        "\u{1F36F}": "miel",
        "\u{1F37A}": "cerveza",
        "\u{1F377}": "vino",
        "\u{1F943}": "cafe",
        "\u{1F375}": "te",
        "\u{1F964}": "jugo",
        "\u{1F9CA}": "hielo",
        "\u{1F9C2}": "sal",
    ]

    private static let FILLER_PATTERN: NSRegularExpression = {
        try! NSRegularExpression(
            pattern: #"\b(eh{1,}|este|osea|o\s+sea|como\s+que|m{3,}|aj[aá]|a\s+ver|por\s+a[hí]|no\s+s[eé]|ps|pe)\b"#,
            options: .caseInsensitive
        )
    }()

    private static let HEDGE_PATTERN: NSRegularExpression = {
        try! NSRegularExpression(
            pattern: #"\b(creo\s+que\s+(?:fue|era)|me\s+parece|m[aá]s\s+o\s+menos|como\s+unos|aprox(?:imadamente)?|tipo)\s*"#,
            options: .caseInsensitive
        )
    }()

    private static let REPEATED_VOWELS: NSRegularExpression = {
        try! NSRegularExpression(pattern: #"([aeiouáéíóúü])\1+"#, options: .caseInsensitive)
    }()

    private static let REPEATED_LETTERS_3_PLUS: NSRegularExpression = {
        try! NSRegularExpression(pattern: #"([a-záéíóúüñ])\1{2,}"#, options: .caseInsensitive)
    }()

    private static let REPEATED_PUNCT: NSRegularExpression = {
        try! NSRegularExpression(pattern: #"[!?]{2,}|\.{3,}"#)
    }()

    private static let TYPO_MAP: [String: String] = [
        "poyo": "pollo", "polllo": "pollo", "pyo": "pollo",
        "arros": "arroz", "arro": "arroz", "aros": "arroz",
        "uebo": "huevo", "wevo": "huevo", "guevo": "huevo", "güevo": "huevo",
        "gueso": "queso", "keso": "queso",
        "panna": "pana",
        "papa": "papa", "papas": "papa",
        "sanahoria": "zanahoria",
        "plátano": "platano",
        "naraja": "naranja",
        "mansana": "manzana",
        "lechua": "lechuga",
        "brocolí": "brocoli",
        "espina": "espinaca",
        "aguacate": "palta",
        "maiz": "choclo", "maíz": "choclo",
        "porotos": "poroto",
        "lentejas": "lenteja",
        "garbanzos": "garbanzo",
        "abena": "avena",
        "salmón": "salmon",
        "camarón": "camaron",
        "pimentón": "pimenton",
        "remolacha": "betarraga",
        "calabaza": "zapallo",
    ]

    private static let EN_ES_MAP: [String: String] = [
        "chicken": "pollo", "rice": "arroz", "egg": "huevo",
        "bread": "pan", "milk": "leche", "cheese": "queso",
        "fish": "pescado", "shrimp": "camaron", "beef": "vacuno",
        "pork": "cerdo", "oats": "avena", "grilled": "plancha",
        "fried": "frito", "baked": "horno", "steamed": "vapor",
        "boiled": "cocido", "smoked": "ahumado", "raw": "crudo",
        "banana": "platano", "apple": "manzana", "avocado": "palta",
        "potato": "papa", "tomato": "tomate", "onion": "cebolla",
        "garlic": "ajo", "pepper": "pimenton", "carrot": "zanahoria",
        "pasta": "pasta", "salad": "ensalada",
        "soup": "sopa", "juice": "jugo", "coffee": "cafe",
        "tea": "te", "water": "agua", "beer": "cerveza",
        "wine": "vino", "yogurt": "yogurt", "honey": "miel",
        "sugar": "azucar", "salt": "sal", "oil": "aceite",
        "butter": "mantequilla", "cream": "crema",
        "al dente": "al dente",
        "golden": "dorado", "crispy": "crocante", "juicy": "jugoso",
        "overcooked": "pasado", "undercooked": "poco cocido",
        "well done": "bien cocido", "medium": "termino medio",
        "rare": "poco cocido", "medium rare": "medio crudo",
    ]

    private static let SHORTHAND_PATTERN: NSRegularExpression = {
        try! NSRegularExpression(
            pattern: #"\b(xq|pq|porq|q|ke|tmb|tb|grs?|gramit[oa]s|gramines?|gramos?|gr|kilit[oa]s|kls|kgs|mililitr[oa]s|mlts|cdas?|cdita|cucharadita)\b"#,
            options: .caseInsensitive
        )
    }()

    private static let NUMBER_WORDS: [String: Int] = [
        "un": 1, "uno": 1, "una": 1,
        "dos": 2, "tres": 3, "cuatro": 4, "cinco": 5,
        "seis": 6, "siete": 7, "ocho": 8, "nueve": 9, "diez": 10,
        "once": 11, "doce": 12, "trece": 13, "catorce": 14, "quince": 15,
        "dieciseis": 16, "diecisiete": 17, "dieciocho": 18, "diecinueve": 19,
        "veinte": 20, "treinta": 30, "cuarenta": 40, "cincuenta": 50,
        "sesenta": 60, "setenta": 70, "ochenta": 80, "noventa": 90,
        "cien": 100, "ciento": 100, "doscientos": 200, "trescientos": 300,
        "cuatrocientos": 400, "quinientos": 500,
        "seiscientos": 600, "setecientos": 700, "ochocientos": 800, "novecientos": 900,
        "mil": 1000,
    ]

    private static let COMMON_FOOD_ROOTS: Set<String> = [
        "pan", "carne", "pollo", "pescado", "huevo", "arroz", "papa", "pasta",
        "leche", "agua", "jugo", "cafe", "te", "vino", "cerveza", "queso",
        "yogurt", "crema", "mantequilla", "azucar", "sal", "aceite", "ajo",
        "cebolla", "tomate", "palta", "platano", "manzana", "naranja", "limon",
        "zanahoria", "pepino", "lechuga", "espinaca", "brócoli", "coliflor",
        "poroto", "lenteja", "garbanzo", "avena", "trigo", "maiz", "platano",
        "sopa", "ensalada", "guiso", "asado", "ají", "pimenton", "choclo",
        "churrasco", "bistec", "hamburguesa", "pizza", "taco", "burrito", "sushi",
    ]

    private static let FRACTION_PATTERN: NSRegularExpression = {
        try! NSRegularExpression(
            pattern: #"\b(medio\s+kilo|cuarto\s+kilo|un\s+kilo\s+y\s+medio)\b"#,
            options: .caseInsensitive
        )
    }()

    private static let SPACES_PATTERN: NSRegularExpression = {
        try! NSRegularExpression(pattern: #"\s+"#)
    }()

    private static let MULTISPACE_PATTERN: NSRegularExpression = {
        try! NSRegularExpression(pattern: #"\s{2,}"#)
    }()

    private static var typoRegexList: [(NSRegularExpression, String)] = {
        TYPO_MAP.map { typo, correction in
            (try! NSRegularExpression(pattern: #"\b"# + NSRegularExpression.escapedPattern(for: typo) + #"\b"#, options: .caseInsensitive), correction)
        }
    }()

    private static var enEsRegexList: [(NSRegularExpression, String)] = {
        EN_ES_MAP.map { en, es in
            (try! NSRegularExpression(pattern: #"\b"# + NSRegularExpression.escapedPattern(for: en) + #"\b"#, options: .caseInsensitive), es)
        }
    }()

    private static var numberWordRegexList: [(String, NSRegularExpression, String)] = {
        NUMBER_WORDS.map { word, num in
            (word, try! NSRegularExpression(pattern: #"\b"# + NSRegularExpression.escapedPattern(for: word) + #"\b"#, options: .caseInsensitive), String(num))
        }
    }()

    public static func normalize(_ input: String) -> String {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return text }

        text = replaceEmojis(text)
        text = regexReplace(text, pattern: REPEATED_PUNCT, template: "")
        text = regexReplace(text, pattern: REPEATED_VOWELS, template: "$1")
        text = regexReplace(text, pattern: FILLER_PATTERN, template: "")
        text = regexReplace(text, pattern: HEDGE_PATTERN, template: "")
        text = applyShorthand(text)
        text = applyTypos(text)
        text = regexReplace(text, pattern: REPEATED_LETTERS_3_PLUS, template: "$1")
        text = applyEnglishMapping(text)
        text = expandFractions(text)
        text = convertNumberWords(text)
        text = regexReplace(text, pattern: MULTISPACE_PATTERN, template: " ").trimmingCharacters(in: .whitespacesAndNewlines)

        return text
    }

    public static func normalizeFoodName(_ name: String) -> String {
        var normalized = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let nsRange = NSRange(normalized.startIndex..., in: normalized)
        normalized = SPACES_PATTERN.stringByReplacingMatches(in: normalized, range: nsRange, withTemplate: " ")

        let diminutiveSuffixes = ["ecito", "ecita", "cito", "cita", "illo", "illa", "ito", "ita"]
        for suffix in diminutiveSuffixes {
            if normalized.hasSuffix(suffix), normalized.count > suffix.count + 2 {
                let root = String(normalized.dropLast(suffix.count))
                if root.count >= 3 {
                    let candidates = [root, root + "o", root + "a", root + "e"]
                    normalized = candidates.first { COMMON_FOOD_ROOTS.contains($0) } ?? root
                    break
                }
            }
        }

        let augmentativeSuffixes = ["azo", "aza", "ote", "ota"]
        for suffix in augmentativeSuffixes {
            if normalized.hasSuffix(suffix), normalized.count > suffix.count + 2 {
                let root = String(normalized.dropLast(suffix.count))
                let candidates = [root, root + "o", root + "a", root + "e"]
                if let matched = candidates.first(where: { COMMON_FOOD_ROOTS.contains($0) }) {
                    normalized = matched
                    break
                }
            }
        }

        return normalized
    }

    // MARK: - Internal helpers

    private static func replaceEmojis(_ text: String) -> String {
        var result = text
        for (emoji, word) in EMOJI_MAP {
            result = result.replacingOccurrences(of: emoji, with: " \(word) ")
        }
        return stripRemainingEmojis(result)
    }

    private static func stripRemainingEmojis(_ text: String) -> String {
        return String(text.unicodeScalars.filter { scalar in
            let cat = scalar.properties.generalCategory
            return cat != .surrogate && cat != .privateUse
        })
    }

    private static func applyShorthand(_ text: String) -> String {
        return regexReplace(text, pattern: SHORTHAND_PATTERN) { match in
            let word = match.lowercased()
            switch word {
            case "xq", "pq", "porq": return "porque"
            case "q", "ke": return "que"
            case "tmb", "tb": return "tambien"
            case "gr", "g", "grs", "gramos":
                return "g"
            case let w where w.starts(with: "gramit") || w.starts(with: "gramin"):
                return "g"
            case "kls", "kgs":
                return "kg"
            case let w where w.starts(with: "kilit"):
                return "kg"
            case "ml", "mlts":
                return "ml"
            case let w where w.starts(with: "mililitr"):
                return "ml"
            case "cdita", "cucharadita":
                return "cucharada"
            case let w where w.hasPrefix("cda"):
                return "cucharada"
            default:
                return word
            }
        }
    }

    private static func applyTypos(_ text: String) -> String {
        var result = text
        for (regex, correction) in typoRegexList {
            let nsRange = NSRange(result.startIndex..., in: result)
            result = regex.stringByReplacingMatches(in: result, range: nsRange, withTemplate: correction)
        }
        return result
    }

    private static func applyEnglishMapping(_ text: String) -> String {
        var result = text
        for (regex, correction) in enEsRegexList {
            let nsRange = NSRange(result.startIndex..., in: result)
            result = regex.stringByReplacingMatches(in: result, range: nsRange, withTemplate: correction)
        }
        return result
    }

    private static func expandFractions(_ text: String) -> String {
        return regexReplace(text, pattern: FRACTION_PATTERN) { match in
            let trimmed = match.lowercased().trimmingCharacters(in: .whitespaces)
            switch trimmed {
            case "medio kilo": return "500 g"
            case "cuarto kilo": return "250 g"
            case "un kilo y medio": return "1500 g"
            default: return match
            }
        }
    }

    private static func convertNumberWords(_ text: String) -> String {
        var result = text
        let words = text.lowercased().components(separatedBy: CharacterSet.whitespaces)
        for (word, regex, numStr) in numberWordRegexList {
            if words.contains(word) {
                let nsRange = NSRange(result.startIndex..., in: result)
                result = regex.stringByReplacingMatches(in: result, range: nsRange, withTemplate: numStr)
            }
        }
        return result
    }

    // MARK: - Regex block-replace helper

    private static func regexReplace(_ text: String, pattern: NSRegularExpression, template: String) -> String {
        let nsRange = NSRange(text.startIndex..., in: text)
        return pattern.stringByReplacingMatches(in: text, range: nsRange, withTemplate: template)
    }

    private static func regexReplace(_ text: String, pattern: NSRegularExpression, using block: @escaping (String) -> String) -> String {
        let nsString = text as NSString
        let range = NSRange(location: 0, length: nsString.length)
        var ranges: [(NSRange, String)] = []

        pattern.enumerateMatches(in: text, range: range) { match, _, _ in
            guard let match = match else { return }
            let matchedStr = nsString.substring(with: match.range)
            ranges.append((match.range, block(matchedStr)))
        }

        var result = text
        for (r, replacement) in ranges.reversed() {
            result = (result as NSString).replacingCharacters(in: r, with: replacement)
        }
        return result
    }
}
