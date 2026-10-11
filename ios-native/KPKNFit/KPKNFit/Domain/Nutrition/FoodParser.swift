import Foundation

// Shared models removed to resolve redeclaration with Data/Models/NutritionModels.swift

// ─── Data-layer stubs (to be replaced when FoodDatabase.kt is ported) ─────────

/// Look up a food by its normalized name.
public func findFoodByNormalized(_ text: String) -> FoodItem? { nil }

/// Get grams for a given reference type (e.g. "cup", "tablespoon").
public func getGramsForReference(_ refType: String, _ foodItem: FoodItem? = nil) -> Double { 0.0 }

// ─── Regex Patterns ──────────────────────────────────────────────────────────

/// FoodParser — Deterministic Spanish NLP parser for free-form food descriptions.
/// Mirrors domain/nutrition/FoodParser.kt from Android.
enum FoodParser {

    private static let gramPattern = try! NSRegularExpression(pattern: #"(\d+(?:[.,]\d+)?)\s*(?:g|gr|gramos?|kg|ml|mililitros?|l|litros?|oz|onzas?|lb|libras?)\b(?:\s+de)?\s*"#, options: .caseInsensitive)

    private static let commaOrPlus = try! NSRegularExpression(pattern: #"(,\s*|\s+\+\s+)"#)
    private static let connectorY = try! NSRegularExpression(pattern: #"\s+(?:y|e|mas|más)\s+"#, options: .caseInsensitive)
    private static let connectorCon = try! NSRegularExpression(pattern: #"\s+con\s+"#, options: .caseInsensitive)

    private static let protectedEntities: [String] = [
        "arroz con leche", "arroz con pollo", "pollo con papas", "pan con queso",
        "pan con palta y jamon", "pan con palta y jamón", "pan con palta",
        "pan con mantequilla", "papas con mayo", "pastel de choclo", "pasteles de choclo",
        "empanada de pino", "empanadas de pino", "empanada de queso", "empanadas de queso",
        "sandwich de pollo con mayonesa", "sandwich de jamon con mayonesa",
        "sándwich de pollo con mayonesa", "sándwich de jamón con mayonesa",
        "hamburguesa con queso", "hamburguesas con queso", "papas fritas con mayonesa", "papa fritas con mayonesa",
        "cafe con leche", "café con leche", "te con leche", "té con leche",
        "leche con chocolate", "leche con platano", "leche con plátano", "porotos con riendas",
    ]

    private static let literalQuantities: [String: Double] = [
        "un": 1.0, "una": 1.0, "uno": 1.0, "dos": 2.0, "tres": 3.0,
        "cuatro": 4.0, "cinco": 5.0, "seis": 6.0, "siete": 7.0,
        "ocho": 8.0, "nueve": 9.0, "diez": 10.0,
        "once": 11.0, "doce": 12.0, "trece": 13.0, "catorce": 14.0,
        "quince": 15.0, "dieciséis": 16.0, "dieciseis": 16.0,
        "diecisiete": 17.0, "dieciocho": 18.0, "diecinueve": 19.0,
        "veinte": 20.0, "veintiuno": 21.0, "veintidós": 22.0, "veintidos": 22.0,
        "veintitrés": 23.0, "veintitres": 23.0, "veinticuatro": 24.0,
        "veinticinco": 25.0, "treinta": 30.0,
        "media": 0.5, "medio": 0.5, "mitad": 0.5,
        "cuarto": 0.25, "tercio": 0.33, "doble": 2.0, "triple": 3.0,
    ]

    private static let portionPatterns: [(NSRegularExpression, PortionPreset, String)] = [
        (try! NSRegularExpression(pattern: #"\b(grande|generoso|generosa)\b"#, options: .caseInsensitive), .EXTRA, "extra"),
        (try! NSRegularExpression(pattern: #"\bplato\s+grande\b"#, options: .caseInsensitive), .LARGE, "large"),
        (try! NSRegularExpression(pattern: #"\bplato\s+mediano\b"#, options: .caseInsensitive), .MEDIUM, "medium"),
        (try! NSRegularExpression(pattern: #"\bplato\s+(?:chico|pequeño|pequeña)\b"#, options: .caseInsensitive), .SMALL, "small"),
        (try! NSRegularExpression(pattern: #"\b(mediano|mediana)\b"#, options: .caseInsensitive), .MEDIUM, "medium"),
        (try! NSRegularExpression(pattern: #"\b(pequeño|pequeña|chico|chica)\b"#, options: .caseInsensitive), .SMALL, "small"),
    ]

    private static let cookingPatterns: [(NSRegularExpression, CookingMethod)] = [
        (try! NSRegularExpression(pattern: #"\b(?:empanizad[oa]s?|empanad[o]s?|apanad[oa]s?|breaded)\b"#, options: .caseInsensitive), .EMPANIZADO_FRITO),
        (try! NSRegularExpression(pattern: #"\b(?:a\s+la\s+)?(?:plancha|planchad[oa]s?)\b"#, options: .caseInsensitive), .PLANCHA),
        (try! NSRegularExpression(pattern: #"\b(?:al\s+)?horno\b|\bhorn(?:ead[oa]s?|er[oa]?)\b|\b(?:baked|airfryer|air\s*fryer|frito\s+al\s+aire)\b"#, options: .caseInsensitive), .HORNO),
        (try! NSRegularExpression(pattern: #"\b(?:frit[oa]s?|fre[ií]d[oa]s?|revuelt[oa]s?|saltead[oa]s?|saltear|sofrit[oa]s?|soffrit[oa]s?|fried)\b"#, options: .caseInsensitive), .FRITO),
        (try! NSRegularExpression(pattern: #"\b(?:cocid[oa]s?|hervid[oa]s?|sancochad[oa]s?|boiled|estofad[oa]s?)\b"#, options: .caseInsensitive), .COCIDO),
        (try! NSRegularExpression(pattern: #"\b(?:crud[oa]s?|fresc[oa]s?|raw)\b"#, options: .caseInsensitive), .CRUDO),
        (try! NSRegularExpression(pattern: #"\b(?:al\s+)?vapor\b|\bvaporizad[oa]s?\b|\bsteamed\b"#, options: .caseInsensitive), .VAPOR),
        (try! NSRegularExpression(pattern: #"\b(?:a\s+la\s+)?olla\b"#, options: .caseInsensitive), .OLLA),
        (try! NSRegularExpression(pattern: #"\b(?:a\s+la\s+)?parrilla\b|\bparrill[ae]r[oa]s?\b|\b(?:grilled|asad[oa]s?|al\s+carb[oó]n)\b"#, options: .caseInsensitive), .ASADO_PARRILLA),
        (try! NSRegularExpression(pattern: #"\bguisad[oa]s?\b|\bcazuel[ae]d[oa]s?\b"#, options: .caseInsensitive), .GUISADO),
        (try! NSRegularExpression(pattern: #"\bahumad[oa]s?\b|\bhumad[oa]s?\b|\bsmoked\b"#, options: .caseInsensitive), .AHUMADO),
    ]

    private static let referencePatterns: [(NSRegularExpression, String)] = [
        (try! NSRegularExpression(pattern: #"\b(\d+(?:[.,]\d+)?)\s+(cucharadas?)\s+de\s+(.+)"#, options: .caseInsensitive), "tablespoon"),
        (try! NSRegularExpression(pattern: #"\b(un|una|media|1)\s+(cucharada)\s+de\s+(.+)"#, options: .caseInsensitive), "tablespoon"),
        (try! NSRegularExpression(pattern: #"\b(dos|tres)\s+(cucharadas?)\s+de\s+(.+)"#, options: .caseInsensitive), "tablespoon"),
        (try! NSRegularExpression(pattern: #"\b(\d+(?:[.,]\d+)?)\s+(cucharaditas?)\s+de\s+(.+)"#, options: .caseInsensitive), "teaspoon"),
        (try! NSRegularExpression(pattern: #"\b(\d+(?:[.,]\d+)?)\s+(tazas?)\s+de\s+(.+)"#, options: .caseInsensitive), "cup"),
        (try! NSRegularExpression(pattern: #"\b(un|una|1)\s+(taza)\s+de\s+(.+)"#, options: .caseInsensitive), "cup"),
        (try! NSRegularExpression(pattern: #"\b(media|medio|1/2)\s+(taza)\s+de\s+(.+)"#, options: .caseInsensitive), "cup"),
        (try! NSRegularExpression(pattern: #"\b(un|una|1)\s+(puñado)\s+de\s+(.+)"#, options: .caseInsensitive), "handful"),
        (try! NSRegularExpression(pattern: #"\b(un|1)\s+(puño)\s+de\s+(.+)"#, options: .caseInsensitive), "fist"),
        (try! NSRegularExpression(pattern: #"\b(\d+(?:[.,]\d+)?)\s+(vasos?)\s+de\s+(.+)"#, options: .caseInsensitive), "glass"),
        (try! NSRegularExpression(pattern: #"\b(\d+(?:[.,]\d+)?)\s+(rebanadas?|tajadas?)\s+de\s+(.+)"#, options: .caseInsensitive), "slice"),
        (try! NSRegularExpression(pattern: #"\b(\d+(?:[.,]\d+)?)\s+(latas?)\s+de\s+(.+)"#, options: .caseInsensitive), "can"),
        (try! NSRegularExpression(pattern: #"\b(\d+(?:[.,]\d+)?)\s+(scoops?|medidas?)\s+de\s+(.+)"#, options: .caseInsensitive), "scoop"),
        (try! NSRegularExpression(pattern: #"\b(\d+(?:[.,]\d+)?)\s+(porciones?)\s+de\s+(.+)"#, options: .caseInsensitive), "portion"),
        (try! NSRegularExpression(pattern: #"\b(un|una|1)\s+(trozo)\s+de\s+(.+)"#, options: .caseInsensitive), "piece"),
        (try! NSRegularExpression(pattern: #"\b(un|una|1)\s+(pedazo)\s+de\s+(.+)"#, options: .caseInsensitive), "piece"),
        (try! NSRegularExpression(pattern: #"\b(\d+(?:[.,]\d+)?)\s+(trozos?|pedazos?)\s+de\s+(.+)"#, options: .caseInsensitive), "piece"),
        (try! NSRegularExpression(pattern: #"\b(un\s+poco)\s+de\s+(.+)"#, options: .caseInsensitive), "little"),
        (try! NSRegularExpression(pattern: #"\b(poquito|poquita)\s+(?:de\s+)?(.+)"#, options: .caseInsensitive), "little"),
        (try! NSRegularExpression(pattern: #"\b(una?\s+pizca)\s+de\s+(.+)"#, options: .caseInsensitive), "pinch"),
        (try! NSRegularExpression(pattern: #"\b(un\s+chorrito)\s+de\s+(.+)"#, options: .caseInsensitive), "splash"),
    ]

    private static let groupPattern = try! NSRegularExpression(pattern: "^(.+?)\\s*\\((.+)\\)\\s*$")
    private static let startsWithDigit = try! NSRegularExpression(pattern: "^\\d")
    private static let negationPattern = try! NSRegularExpression(pattern: #"\b(?:sin|menos|no)\b"#, options: .caseInsensitive)
    private static let gramUnitPattern = try! NSRegularExpression(pattern: #"(\d+(?:[.,]\d+)?)\s*(g|gr|gramos?|kg|ml|mililitros?|l|litros?|oz|onzas?|lb|libras?)\b"#, options: .caseInsensitive)
    private static let kgLiterPattern = try! NSRegularExpression(pattern: "kg|l$|litros?")
    private static let ozPattern = try! NSRegularExpression(pattern: "oz|onzas?")
    private static let lbPattern = try! NSRegularExpression(pattern: "lb|libras?")
    private static let halfPattern = try! NSRegularExpression(pattern: #"\b1/2\b"#)
    private static let quarterPattern = try! NSRegularExpression(pattern: #"\b1/4\b"#)
    private static let threeQuartersPattern = try! NSRegularExpression(pattern: #"\b3/4\b"#)
    private static let rangeQuantityPattern = try! NSRegularExpression(pattern: #"^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s+(.+)$"#)
    private static let numberQuantityPattern = try! NSRegularExpression(pattern: #"^(\d+(?:\.\d+)?)\s*(?:x\s*)?(.+)$"#)
    private static let literalQuantityPattern = try! NSRegularExpression(pattern: #"^(un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieciséis|dieciseis|diecisiete|dieciocho|diecinueve|veinte|veintiuno|veintidós|veintidos|veintitrés|veintitres|veinticuatro|veinticinco|treinta|media|medio|mitad|cuarto|tercio|doble|triple)\s+(.+)$"#, options: .caseInsensitive)

    private static let spacesPattern = try! NSRegularExpression(pattern: "\\s+")
    private static let leadingDePattern = try! NSRegularExpression(pattern: "^de\\s+")
    private static let portionPrefixPattern = try! NSRegularExpression(pattern: #"^(?:platos?|porciones?|porción|tazas?|vasos?|boles?|bowls?|fuentes?)\s+de\s+"#)
    private static let articlePortionPrefixPattern = try! NSRegularExpression(pattern: #"^(?:un|una|unos|unas)\s+(?:platos?|porciones?|porción|tazas?|vasos?|boles?|bowls?|fuentes?)\s+de\s+"#)
    private static let trailingDePattern = try! NSRegularExpression(pattern: "\\s+de\\s+$")
    private static let diminutivePattern = try! NSRegularExpression(pattern: #"(\w+?)(cito|cita|ito|ita|illo|illa|ecito|ecita)$"#)

    private static let protectedEntitiesRegex: NSRegularExpression = {
        let escaped = protectedEntities.map { NSRegularExpression.escapedPattern(for: $0) }
        let joined = escaped.map { #"\b"# + $0 + #"\b"# }.joined(separator: "|")
        return try! NSRegularExpression(pattern: joined, options: .caseInsensitive)
    }()

    private static let referenceKeywordsFast: [String] = [
        "cucharad", "taza", "puñ", "vaso", "rebanad", "tajad", "lata",
        "scoop", "medida", "porcion", "porción", "trozo", "pedazo",
        "poco", "poquit", "pizca", "chorrit",
    ]

    private static let cookingKeywordsFast: [String] = [
        "empaniz", "apanad", "breaded", "empanad", "plancha", "horno", "horn",
        "baked", "airfryer", "air fryer", "frito", "freid", "freíd", "revuelt",
        "saltea", "sofrit", "soffrit", "fried", "cocid", "hervid", "sancoch",
        "boiled", "estofad", "crud", "fresc", "raw", "vapor", "steamed", "olla",
        "parrill", "grilled", "asado", "carbón", "carbon", "guisad", "cazuel",
        "ahumad", "humad", "smoked",
    ]

    private static let portionKeywordsFast: [String] = [
        "grand", "generos", "plato", "median", "pequeñ", "chico", "chica",
    ]

    private static let modifierKeywordsFast: [String] = [
        "piel", "grasa", "miga", "clara", "descremad", "light", "0%", "almibar",
        "almíbar", "azucar", "azúcar", "integral", "colmad", "generos", "rasa", "fina", "pequeñ",
    ]

    // ─── Main Parser ─────────────────────────────────────────────────────────

    static func parseMealDescription(_ description: String) -> ParsedMealDescription {
        let normalized = TextNormalizer.normalize(description)
        let trimmed = normalized.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return ParsedMealDescription(rawDescription: description) }

        let fragments = splitByListConnectors(trimmed)
        var items: [ParsedMealItem] = []
        var seen: Set<String> = []
        let globalPortion = extractGlobalPortion(trimmed)

        for frag in fragments {
            guard let parsed = parseFragment(frag) else { continue }
            if !seen.contains(parsed.tag) {
                seen.insert(parsed.tag)
                let portion = (parsed.portion == .MEDIUM && globalPortion != .MEDIUM) ? globalPortion : parsed.portion
                items.append(ParsedMealItem(
                    tag: parsed.tag, quantity: parsed.quantity,
                    amountGrams: parsed.amountGrams, cookingMethod: parsed.cookingMethod,
                    portion: portion, isFuzzyMatch: parsed.isFuzzyMatch,
                    brandHint: parsed.brandHint, macroOverrides: parsed.macroOverrides,
                    basePer100g: parsed.basePer100g, analysisSource: parsed.analysisSource,
                    analysisConfidence: parsed.analysisConfidence, reviewRequired: parsed.reviewRequired,
                    subItems: parsed.subItems, isGroup: parsed.isGroup,
                    resolvedFoodId: parsed.resolvedFoodId, appliedCookingFactor: parsed.appliedCookingFactor,
                    modifierScale: parsed.modifierScale, isExcluded: parsed.isExcluded
                ))
            } else {
                if let idx = items.firstIndex(where: { $0.tag == parsed.tag }) {
                    let existing = items[idx]
                    let combinedGrams = existing.amountGrams.map { $0 + (parsed.amountGrams ?? 0.0) }
                    items[idx] = ParsedMealItem(
                        tag: existing.tag, quantity: existing.quantity + parsed.quantity,
                        amountGrams: combinedGrams, cookingMethod: existing.cookingMethod,
                        portion: existing.portion, isFuzzyMatch: existing.isFuzzyMatch,
                        brandHint: existing.brandHint, macroOverrides: existing.macroOverrides,
                        basePer100g: existing.basePer100g, analysisSource: existing.analysisSource,
                        analysisConfidence: existing.analysisConfidence, reviewRequired: existing.reviewRequired,
                        subItems: existing.subItems, isGroup: existing.isGroup,
                        resolvedFoodId: existing.resolvedFoodId, appliedCookingFactor: existing.appliedCookingFactor,
                        modifierScale: existing.modifierScale, isExcluded: existing.isExcluded
                    )
                }
            }
        }

        if items.isEmpty && !trimmed.isEmpty {
            if let parsed = parseFragment(trimmed) { items.append(parsed) }
        }

        return ParsedMealDescription(items: items, rawDescription: trimmed)
    }

    private static func isKnownNegationModifier(_ text: String, negMatch: NSTextCheckingResult) -> Bool {
        let nsText = text as NSString
        let afterNeg = nsText.substring(from: negMatch.range.location + negMatch.range.length)
            .trimmingCharacters(in: .whitespaces).lowercased()
        let firstWord = afterNeg.components(separatedBy: .whitespaces).first ?? ""
        return ["piel", "grasa", "miga", "pieles", "grasas"].contains(firstWord)
    }

    // ─── Fragment Parser ─────────────────────────────────────────────────────

    private static func parseFragment(_ frag: String) -> ParsedMealItem? {
        var text = frag.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return nil }

        var isExcluded = false
        let sinPrefix = try! NSRegularExpression(pattern: "^sin\\s+", options: .caseInsensitive)
        if let sinMatch = sinPrefix.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) {
            isExcluded = true
            text = (text as NSString).substring(from: sinMatch.range.location + sinMatch.range.length)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if text.isEmpty { return nil }
        }

        if let groupMatch = groupPattern.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) {
            let nsText = text as NSString
            let groupName = nsText.substring(with: groupMatch.range(at: 1)).trimmingCharacters(in: .whitespaces)
            let content = nsText.substring(with: groupMatch.range(at: 2)).trimmingCharacters(in: .whitespaces)
            let subItems = splitByListConnectors(content).compactMap { parseFragment($0) }
            if !subItems.isEmpty {
                return ParsedMealItem(tag: groupName, subItems: subItems, isGroup: true)
            }
        }

        let gramsResult = extractGramsFromFragment(text)
        var grams = gramsResult.0
        var working = gramsResult.1
        var refQuantity: Double? = nil

        if grams == nil {
            let refResult = extractReferenceFromFragment(working)
            if let refGrams = refResult.grams {
                grams = refGrams
                working = refResult.foodPart
                refQuantity = refResult.quantity
            }
        }

        let cookingMethod = extractCookingMethod(working)
        working = cookingMethod.1

        let modifierResult = extractModifiers(working, currentGrams: grams)
        working = modifierResult.2
        if let g = modifierResult.1 { grams = g }

        let portionResult = extractPortionFromFragment(working)
        working = portionResult.1

        let quantityResult = parseQuantityMultiplier(working)
        let quantity = refQuantity.map { $0 * quantityResult.0 } ?? quantityResult.0
        let foodName = quantityResult.1

        if foodName.count < 2 { return nil }

        let shouldSingularize = startsWithDigit.firstMatch(
            in: working.trimmingCharacters(in: .whitespaces),
            range: NSRange(working.startIndex..., in: working)
        ) != nil
        let canonical = normalizeFoodName(foodName, singularize: shouldSingularize)

        return ParsedMealItem(
            tag: canonical, quantity: quantity, amountGrams: grams,
            cookingMethod: cookingMethod.0, portion: portionResult.0,
            isFuzzyMatch: false,
            appliedCookingFactor: cookingMethod.0.flatMap { cookingFactors[$0] }?.kcal ?? 1.0,
            modifierScale: modifierResult.0.map { MacroOverrides(calories: $0.kcal, protein: $0.protein, carbs: $0.carbs, fats: $0.fats) },
            isExcluded: isExcluded
        )
    }

    // ─── Split Connectors ────────────────────────────────────────────────────

    private static func splitByListConnectors(_ description: String) -> [String] {
        let trimmed = description.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return [] }

        var masks: [(token: String, original: String)] = []
        let nsTrimmed = trimmed as NSString
        let protoMatches = protectedEntitiesRegex.matches(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed))
        var masked = trimmed
        for m in protoMatches.reversed() {
            let token = "__PROTECTED_\(masks.count)__"
            masks.append((token, nsTrimmed.substring(with: m.range)))
            masked = (masked as NSString).replacingCharacters(in: m.range, with: token)
        }

        var parts: [String] = [masked]
        for pattern in [commaOrPlus, connectorY, connectorCon] {
            parts = parts.flatMap { splitByRegex($0, pattern: pattern) }
        }

        var result: [String] = []
        for part in parts {
            var unmasked = part
            for (token, original) in masks {
                unmasked = unmasked.replacingOccurrences(of: token, with: original)
            }

            if let negMatch = negationPattern.firstMatch(in: unmasked, range: NSRange(unmasked.startIndex..., in: unmasked)),
               !isKnownNegationModifier(unmasked, negMatch: negMatch) {
                let ns = unmasked as NSString
                let beforeNeg = ns.substring(to: negMatch.range.location).trimmingCharacters(in: .whitespaces)
                let afterNeg = ns.substring(from: negMatch.range.location + negMatch.range.length).trimmingCharacters(in: .whitespaces)
                let sinFragment = "sin \(afterNeg)"
                if !beforeNeg.isEmpty { result.append(beforeNeg) }
                if !sinFragment.isEmpty { result.append(sinFragment) }
            } else {
                result.append(unmasked.trimmingCharacters(in: .whitespaces))
            }
        }

        return result.filter { !$0.isEmpty }
    }

    private static func splitByRegex(_ text: String, pattern: NSRegularExpression) -> [String] {
        let nsText = text as NSString
        let range = NSRange(location: 0, length: nsText.length)
        var results: [String] = []
        var lastEnd = 0

        for match in pattern.matches(in: text, range: range) {
            if match.range.location > lastEnd {
                let part = nsText.substring(with: NSRange(location: lastEnd, length: match.range.location - lastEnd))
                let trimmed = part.trimmingCharacters(in: .whitespaces)
                if !trimmed.isEmpty { results.append(trimmed) }
            }
            lastEnd = match.range.location + match.range.length
        }
        if lastEnd < nsText.length {
            let trimmed = nsText.substring(from: lastEnd).trimmingCharacters(in: .whitespaces)
            if !trimmed.isEmpty { results.append(trimmed) }
        }

        return results
    }

    // ─── Extract Grams ───────────────────────────────────────────────────────

    private static func extractGramsFromFragment(_ text: String) -> (Double?, String) {
        guard let match = gramPattern.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) else {
            return (nil, text)
        }
        let nsText = text as NSString
        let matchValue = nsText.substring(with: match.range)
        guard let numMatch = gramUnitPattern.firstMatch(in: matchValue, range: NSRange(matchValue.startIndex..., in: matchValue)) else {
            return (nil, text)
        }

        let nsMatchValue = matchValue as NSString
        let numStr = nsMatchValue.substring(with: numMatch.range(at: 1)).replacingOccurrences(of: ",", with: ".")
        guard var value = Double(numStr) else { return (nil, text) }
        let unit = nsMatchValue.substring(with: numMatch.range(at: 2)).lowercased()

        let unitRange = NSRange(unit.startIndex..., in: unit)
        if kgLiterPattern.firstMatch(in: unit, range: unitRange) != nil {
            value *= 1000
        } else if ozPattern.firstMatch(in: unit, range: unitRange) != nil {
            value *= 28.3495
        } else if lbPattern.firstMatch(in: unit, range: unitRange) != nil {
            value *= 453.592
        }

        let cleaned = text
            .replacingOccurrences(of: matchValue, with: " ")
            .replacingOccurrences(of: "\\s{2,}", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (value, cleaned)
    }

    // ─── Extract Portion Reference ───────────────────────────────────────────

    private struct ReferenceResult {
        let grams: Double?
        let quantity: Double
        let foodPart: String
    }

    private static func extractReferenceFromFragment(_ text: String) -> ReferenceResult {
        let lower = text.lowercased()
        if !referenceKeywordsFast.contains(where: { lower.contains($0) }) {
            return ReferenceResult(grams: nil, quantity: 1.0, foodPart: text)
        }

        for (pattern, refType) in referencePatterns {
            guard let match = pattern.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) else { continue }
            let nsText = text as NSString

            var foodPart = ""
            for i in (1...match.numberOfRanges - 1).reversed() {
                let r = match.range(at: i)
                if r.location != NSNotFound && r.length > 0 {
                    foodPart = nsText.substring(with: r).trimmingCharacters(in: .whitespaces)
                    break
                }
            }
            if foodPart.count < 2 { continue }

            let qtyStr = nsText.substring(with: match.range(at: 1)).lowercased()
            let qty = literalQuantities[qtyStr] ?? Double(qtyStr.replacingOccurrences(of: ",", with: ".")) ?? 1.0

            let food = findFoodByNormalized(foodPart)
            let densityCategory = SubjectivePortionEngine.detectDensityCategory(foodName: foodPart)
            let subjectiveResult = SubjectivePortionEngine.resolve(
                expression: nsText.substring(with: match.range),
                foodCategory: densityCategory,
                standardPortion: food?.servingSize
            )

            let grams: Double
            if let result = subjectiveResult {
                grams = (result.grams * 10).rounded() / 10.0
            } else {
                let gramsPerUnit = getGramsForReference(refType, food)
                grams = (gramsPerUnit * qty * 10).rounded() / 10.0
            }

            return ReferenceResult(grams: grams, quantity: qty, foodPart: foodPart)
        }

        return ReferenceResult(grams: nil, quantity: 1.0, foodPart: text)
    }

    // ─── Extract Cooking Method ──────────────────────────────────────────────

    private static func extractCookingMethod(_ text: String) -> (CookingMethod?, String) {
        let lower = text.lowercased()
        if !cookingKeywordsFast.contains(where: { lower.contains($0) }) {
            return (nil, text)
        }
        for (pattern, method) in cookingPatterns {
            guard let match = pattern.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) else { continue }
            let nsText = text as NSString
            let cleaned = text
                .replacingOccurrences(of: nsText.substring(with: match.range), with: " ")
                .replacingOccurrences(of: "\\s{2,}", with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            return (method, cleaned)
        }
        return (nil, text)
    }

    // ─── Extract Portion ─────────────────────────────────────────────────────

    private static func extractPortionFromFragment(_ text: String) -> (PortionPreset, String) {
        let lower = text.lowercased()
        if !portionKeywordsFast.contains(where: { lower.contains($0) }) {
            return (.MEDIUM, text)
        }
        for (pattern, preset, _) in portionPatterns {
            guard let match = pattern.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) else { continue }
            let nsText = text as NSString
            let cleaned = text
                .replacingOccurrences(of: nsText.substring(with: match.range), with: " ")
                .replacingOccurrences(of: "\\s{2,}", with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            return (preset, cleaned)
        }
        return (.MEDIUM, text)
    }

    // ─── Quantity Multiplier ─────────────────────────────────────────────────

    private static func parseQuantityMultiplier(_ text: String) -> (Double, String) {
        var trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        func fullRange(_ s: String) -> NSRange { NSRange(s.startIndex..., in: s) }
        trimmed = halfPattern.stringByReplacingMatches(in: trimmed, range: fullRange(trimmed), withTemplate: "0.5")
        trimmed = quarterPattern.stringByReplacingMatches(in: trimmed, range: fullRange(trimmed), withTemplate: "0.25")
        trimmed = threeQuartersPattern.stringByReplacingMatches(in: trimmed, range: fullRange(trimmed), withTemplate: "0.75")

        if let rangeMatch = rangeQuantityPattern.firstMatch(in: trimmed, range: fullRange(trimmed)) {
            let ns = trimmed as NSString
            let qty1 = Double(ns.substring(with: rangeMatch.range(at: 1))) ?? 1.0
            let qty2 = Double(ns.substring(with: rangeMatch.range(at: 2))) ?? 1.0
            let rest = ns.substring(with: rangeMatch.range(at: 3)).trimmingCharacters(in: .whitespaces)
            if rest.count >= 2 { return ((qty1 + qty2) / 2.0, rest) }
        }

        if let numMatch = numberQuantityPattern.firstMatch(in: trimmed, range: fullRange(trimmed)) {
            let ns = trimmed as NSString
            let qty = Double(ns.substring(with: numMatch.range(at: 1))) ?? 1.0
            let rest = ns.substring(with: numMatch.range(at: 2)).trimmingCharacters(in: .whitespaces)
            if rest.count >= 2 { return (max(qty, 0.0), rest) }
        }

        if let literalMatch = literalQuantityPattern.firstMatch(in: trimmed, range: fullRange(trimmed)) {
            let ns = trimmed as NSString
            let word = ns.substring(with: literalMatch.range(at: 1)).lowercased()
            let rest = ns.substring(with: literalMatch.range(at: 2)).trimmingCharacters(in: .whitespaces)
            if let qty = literalQuantities[word], rest.count >= 2 { return (qty, rest) }
        }

        return (1.0, trimmed)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static func extractGlobalPortion(_ description: String) -> PortionPreset {
        for (pattern, preset, _) in portionPatterns {
            if pattern.firstMatch(in: description, range: NSRange(description.startIndex..., in: description)) != nil {
                return preset
            }
        }
        return .MEDIUM
    }

    private static func normalizeFoodName(_ name: String, singularize: Bool = false) -> String {
        var normalized = name
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)

        func fullRange(_ s: String) -> NSRange { NSRange(s.startIndex..., in: s) }
        normalized = leadingDePattern.stringByReplacingMatches(in: normalized, range: fullRange(normalized), withTemplate: "")
        normalized = portionPrefixPattern.stringByReplacingMatches(in: normalized, range: fullRange(normalized), withTemplate: "")
        normalized = articlePortionPrefixPattern.stringByReplacingMatches(in: normalized, range: fullRange(normalized), withTemplate: "")
        normalized = trailingDePattern.stringByReplacingMatches(in: normalized, range: fullRange(normalized), withTemplate: "")
        normalized = diminutivePattern.stringByReplacingMatches(in: normalized, range: fullRange(normalized), withTemplate: "$1")

        if singularize {
            if normalized.hasSuffix("ces") && normalized.count > 4 {
                normalized = String(normalized.dropLast(3)) + "z"
            } else if normalized.hasSuffix("es") && normalized.count > 4 {
                normalized = String(normalized.dropLast(2))
            } else if normalized.hasSuffix("s") && normalized.count > 3 {
                normalized = String(normalized.dropLast(1))
            }
        }

        return normalized
    }

    // ─── Anatomical / Preparation Modifiers ──────────────────────────────────

    private struct MacroScale {
        let kcal: Double
        let protein: Double
        let carbs: Double
        let fats: Double
        init(kcal: Double = 1.0, protein: Double = 1.0, carbs: Double = 1.0, fats: Double = 1.0) {
            self.kcal = kcal; self.protein = protein; self.carbs = carbs; self.fats = fats
        }
    }

    private static let modifierPatterns: [(NSRegularExpression, MacroScale)] = [
        (try! NSRegularExpression(pattern: #"\bsin\s+(piel|grasa)\b"#, options: .caseInsensitive), MacroScale(fats: 0.6)),
        (try! NSRegularExpression(pattern: #"\bsin\s+miga\b"#, options: .caseInsensitive), MacroScale(kcal: 0.65, carbs: 0.6)),
        (try! NSRegularExpression(pattern: #"\bsolo?\s+claras?\b"#, options: .caseInsensitive), MacroScale(kcal: 0.55, protein: 0.85, carbs: 1.0, fats: 0.05)),
        (try! NSRegularExpression(pattern: #"\b(descremad[oa]|light|0\s*%)\b"#, options: .caseInsensitive), MacroScale(kcal: 0.7, fats: 0.15)),
        (try! NSRegularExpression(pattern: #"\ben\s+alm[ií]bar\b|\bcon\s+az[uú]car\b"#, options: .caseInsensitive), MacroScale(kcal: 1.3, carbs: 1.4)),
        (try! NSRegularExpression(pattern: #"\bintegral(es)?\b"#, options: .caseInsensitive), MacroScale(kcal: 0.95, carbs: 0.9)),
        (try! NSRegularExpression(pattern: #"\b(colmad[oa]|generos[oa])\b"#, options: .caseInsensitive), MacroScale()),
        (try! NSRegularExpression(pattern: #"\b(ras[oa]|fin[oa]|pequeñ[oa])\b"#, options: .caseInsensitive), MacroScale()),
    ]

    private static func extractModifiers(_ text: String, currentGrams: Double?) -> (MacroScale?, Double?, String) {
        let lower = text.lowercased()
        if !modifierKeywordsFast.contains(where: { lower.contains($0) }) {
            return (nil, currentGrams, text)
        }
        var working = text
        var resultScale: MacroScale? = nil
        var gramsOverride = currentGrams

        for (pattern, scale) in modifierPatterns {
            guard let match = pattern.firstMatch(in: working, range: NSRange(working.startIndex..., in: working)) else { continue }
            let ns = working as NSString
            let matchText = ns.substring(with: match.range)
            working = working
                .replacingOccurrences(of: matchText, with: " ")
                .replacingOccurrences(of: "\\s{2,}", with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)

            let lowerMatch = matchText.lowercased()
            if lowerMatch.contains("colmad") || lowerMatch.contains("generos") {
                if currentGrams == nil {
                    gramsOverride = (gramsOverride ?? 100.0) * 1.25
                }
            } else if lowerMatch.contains("rasa") || lowerMatch.contains("fina") || lowerMatch.contains("pequeñ") {
                if currentGrams == nil {
                    gramsOverride = (gramsOverride ?? 100.0) * 0.75
                }
            } else {
                resultScale = resultScale.map {
                    MacroScale(kcal: $0.kcal * scale.kcal, protein: $0.protein * scale.protein,
                               carbs: $0.carbs * scale.carbs, fats: $0.fats * scale.fats)
                } ?? scale
            }
        }

        return (resultScale, gramsOverride, working)
    }
}
