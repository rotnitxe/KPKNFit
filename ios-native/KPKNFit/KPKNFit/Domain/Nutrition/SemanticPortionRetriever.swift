import Foundation

enum SemanticPortionRetriever {

    struct RetrievalResult {
        let query: String
        let matches: [DatasetMatch]
        let contextDetected: [String]
        let portionPriors: [String: Double]
        let macroRange: MacroRangeEstimate?
        let confidence: Double
        let elapsedMs: Int
    }

    struct DatasetMatch {
        let docId: Int
        let instruction: String
        let score: Double
        let type: String
    }

    struct MacroRangeEstimate {
        let kcalMin: Double
        let kcalMax: Double
        let kcalMedian: Double
        let proteinMin: Double
        let proteinMax: Double
        let proteinMedian: Double
        let fatsMin: Double
        let fatsMax: Double
        let fatsMedian: Double
        let carbsMin: Double
        let carbsMax: Double
        let carbsMedian: Double
    }

    private static let mnPattern = try! NSRegularExpression(pattern: "\\p{Mn}+")
    private static let nonAlphanumericPattern = try! NSRegularExpression(pattern: "[^\\p{L}\\p{Nd}]+")
    private static let spacesPattern = try! NSRegularExpression(pattern: "\\s+")
    private static let gramsDePattern = try! NSRegularExpression(pattern: "(\\d+(?:[.,]\\d+)?)\\s*g\\s+de\\s+([a-záéíóúñü\\s]{2,}?)(?:\\s*,|\\s+y\\s+|\\s*\\(|\\s*$)")
    private static let parenGramsPattern = try! NSRegularExpression(pattern: "([a-záéíóúñü\\s]{2,}?)\\s*\\((\\d+(?:[.,]\\d+)?)\\s*g\\)")
    private static let kcalMatchPattern = try! NSRegularExpression(pattern: "(\\d+(?:[.,]\\d+)?)\\s*kcal")
    private static let proteinMatchPattern = try! NSRegularExpression(pattern: "(\\d+(?:[.,]\\d+)?)\\s*[pP]")
    private static let fatsMatchPattern = try! NSRegularExpression(pattern: "(\\d+(?:[.,]\\d+)?)\\s*[gG](?:rasas?)?")
    private static let carbsMatchPattern = try! NSRegularExpression(pattern: "(\\d+(?:[.,]\\d+)?)\\s*[cC](?:arbohidratos?)?")

    private static let spanishStopwords: Set<String> = [
        "de", "la", "el", "con", "sin", "a", "al", "en", "por", "y", "o", "un", "una",
        "unos", "unas", "del", "las", "los", "lo", "para", "que", "es", "su", "se",
        "no", "más", "como", "le", "me", "te", "mi", "tu", "muy", "ya", "si",
        "pero", "porque", "cuando", "donde", "cual", "quien", "este", "esta", "ese",
        "esa", "todos", "todas", "todo", "toda", "otro", "otra", "otros", "otras",
        "mismo", "misma", "cada", "sobre", "entre", "hasta", "desde", "hacia",
        "hay", "son", "fue", "era", "tiene", "puede", "debe", "calcula", "cuáles",
        "oficiales", "gramos", "calorías", "macros",
    ]

    static func retrieve(query: String, topK: Int = 8) -> RetrievalResult {
        let startMs = Date().timeIntervalSince1970 * 1000
        let normalized = normalizeText(query)
        let tokens = tokenize(normalized)

        guard !tokens.isEmpty else {
            return RetrievalResult(query: query, matches: [], contextDetected: [], portionPriors: [:], macroRange: nil, confidence: 0.0, elapsedMs: 0)
        }

        var docScores: [Int: Double] = [:]

        for token in tokens {
            if let entry = DatasetKnowledge.TFIDF_TOKEN_INDEX[token] {
                let matchesList = entry.split(separator: ",")
                for matchStr in matchesList {
                    let parts = matchStr.split(separator: ":")
                    if parts.count == 2, let docId = Int(parts[0]), let score = Double(parts[1]) {
                        docScores[docId, default: 0.0] += score
                    }
                }
            }
        }

        for (context, keywords) in DatasetKnowledge.CONTEXT_KEYWORDS {
            for kw in keywords {
                if tokens.contains(where: { $0.contains(kw) || kw.contains($0) }) {
                    docScores[context.hashValue] = 1.0
                }
            }
        }

        var trigramHits: [Int: Int] = [:]
        for token in tokens where token.count >= 3 {
            let trigrams = generateTrigrams(token)
            for trigram in trigrams {
                if let indexEntry = DatasetKnowledge.TFIDF_TRIGRAM_INDEX[trigram] {
                    let docIds = indexEntry.split(separator: ",")
                    for docIdStr in docIds {
                        if let docId = Int(docIdStr) {
                            trigramHits[docId, default: 0] += 1
                        }
                    }
                }
            }
        }

        let maxTrigramHits = trigramHits.values.max() ?? 1
        for (docId, triCount) in trigramHits {
            let triBonus = (Double(triCount) / Double(maxTrigramHits)) * 0.5
            docScores[docId, default: 0.0] += triBonus
        }

        let sortedDocs = docScores.sorted { $0.value > $1.value }.prefix(topK)
        let maxScore = sortedDocs.first?.value ?? 1.0

        let matches = sortedDocs.compactMap { (docId, rawScore) -> DatasetMatch? in
            let normalizedScore = min(max(rawScore / maxScore, 0.0), 1.0)
            let instruction = docId < DatasetKnowledge.INSTRUCTIONS.count ? DatasetKnowledge.INSTRUCTIONS[docId] : ""
            let type = docId < DatasetKnowledge.ENTRY_TYPES.count ? DatasetKnowledge.ENTRY_TYPES[docId] : "GENERAL"
            guard !instruction.isEmpty else { return nil }
            return DatasetMatch(docId: docId, instruction: instruction, score: normalizedScore, type: type)
        }

        let contextDetected = detectContexts(query)
        let portionPriors = extractPortionPriors(matches: matches, queryTokens: tokens)
        let macroRange = estimateMacroRange(matches)
        let confidence = calculateConfidence(matches: matches, tokenCount: tokens.count)
        let elapsed = Int((Date().timeIntervalSince1970 * 1000) - startMs)

        return RetrievalResult(
            query: query, matches: matches, contextDetected: contextDetected,
            portionPriors: portionPriors, macroRange: macroRange,
            confidence: confidence, elapsedMs: elapsed
        )
    }

    static func getGramsForFood(foodName: String, retrievalResult: RetrievalResult?) -> Double? {
        let normalized = foodName.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)

        if let result = retrievalResult, !result.portionPriors.isEmpty {
            for (key, grams) in result.portionPriors {
                if key.contains(normalized) || normalized.contains(key) {
                    return grams
                }
            }
        }

        for triplet in DatasetKnowledge.PORTION_TRIPLETS {
            let tripletName = triplet.food.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
            if tripletName == normalized || tripletName.contains(normalized) || normalized.contains(tripletName) {
                return triplet.grams
            }
        }

        return nil
    }

    // MARK: - Internal

    private static func normalizeText(_ text: String) -> String {
        let folded = text.folding(options: [.diacriticInsensitive, .caseInsensitive, .widthInsensitive], locale: .current)
        let stripped = folded.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) || CharacterSet.whitespaces.contains($0) }
        return String(stripped).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func tokenize(_ normalized: String) -> [String] {
        normalized.components(separatedBy: .whitespaces).filter { $0.count >= 2 && !spanishStopwords.contains($0) }
    }

    private static func generateTrigrams(_ token: String) -> Set<String> {
        guard token.count >= 3 else { return [token] }
        let padded = "$\(token)$"
        var trigrams = Set<String>()
        let startIdx = padded.startIndex
        for i in 0..<(padded.count - 2) {
            let s = padded.index(startIdx, offsetBy: i)
            let e = padded.index(startIdx, offsetBy: i + 3)
            trigrams.insert(String(padded[s..<e]))
        }
        return trigrams
    }

    private static func detectContexts(_ query: String) -> [String] {
        let lower = query.lowercased()
        return DatasetKnowledge.CONTEXT_KEYWORDS.compactMap { (context, keywords) in
            keywords.contains { lower.contains($0) } ? context : nil
        }
    }

    private static func extractPortionPriors(matches: [DatasetMatch], queryTokens: [String]) -> [String: Double] {
        var priors: [String: [Double]] = [:]

        for match in matches where match.score >= 0.3 {
            let instruction = match.instruction.lowercased()
            for gramMatch in gramsDePattern.matches(in: instruction, range: NSRange(instruction.startIndex..., in: instruction)) {
                guard gramMatch.numberOfRanges == 3 else { continue }
                let gramsStr = (instruction as NSString).substring(with: gramMatch.range(at: 1)).replacingOccurrences(of: ",", with: ".")
                let food = (instruction as NSString).substring(with: gramMatch.range(at: 2)).trimmingCharacters(in: .whitespaces)
                guard let grams = Double(gramsStr), grams > 0, grams <= 2000, food.count >= 2 else { continue }
                priors[food, default: []].append(grams)
            }
            for parenMatch in parenGramsPattern.matches(in: instruction, range: NSRange(instruction.startIndex..., in: instruction)) {
                guard parenMatch.numberOfRanges == 3 else { continue }
                let food = (instruction as NSString).substring(with: parenMatch.range(at: 1)).trimmingCharacters(in: .whitespaces)
                let gramsStr = (instruction as NSString).substring(with: parenMatch.range(at: 2)).replacingOccurrences(of: ",", with: ".")
                guard let grams = Double(gramsStr), grams > 0, grams <= 2000, food.count >= 2 else { continue }
                priors[food, default: []].append(grams)
            }
        }

        return priors.mapValues { $0.reduce(0, +) / Double($0.count) }
    }

    private static func estimateMacroRange(_ matches: [DatasetMatch]) -> MacroRangeEstimate? {
        var kcalValues: [Double] = []
        var proteinValues: [Double] = []
        var fatsValues: [Double] = []
        var carbsValues: [Double] = []

        for match in matches where match.score >= 0.3 {
            if let output = extractMacrosFromInstruction(match.instruction) {
                if let kcal = output.kcal { kcalValues.append(kcal) }
                if let protein = output.protein { proteinValues.append(protein) }
                if let fats = output.fats { fatsValues.append(fats) }
                if let carbs = output.carbs { carbsValues.append(carbs) }
            }
        }

        guard !kcalValues.isEmpty || !proteinValues.isEmpty else { return nil }

        func median(_ arr: [Double]) -> Double {
            let sorted = arr.sorted()
            if sorted.isEmpty { return 0.0 }
            return sorted[sorted.count / 2]
        }

        return MacroRangeEstimate(
            kcalMin: kcalValues.min() ?? 0, kcalMax: kcalValues.max() ?? 0, kcalMedian: median(kcalValues),
            proteinMin: proteinValues.min() ?? 0, proteinMax: proteinValues.max() ?? 0, proteinMedian: median(proteinValues),
            fatsMin: fatsValues.min() ?? 0, fatsMax: fatsValues.max() ?? 0, fatsMedian: median(fatsValues),
            carbsMin: carbsValues.min() ?? 0, carbsMax: carbsValues.max() ?? 0, carbsMedian: median(carbsValues)
        )
    }

    private struct MacroOutput {
        let kcal: Double?
        let protein: Double?
        let fats: Double?
        let carbs: Double?
    }

    private static func extractMacrosFromInstruction(_ instruction: String) -> MacroOutput? {
        let lower = instruction.lowercased()

        func firstMatch(_ pattern: NSRegularExpression, _ text: String) -> Double? {
            guard let match = pattern.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
                  match.numberOfRanges > 1 else { return nil }
            let str = (text as NSString).substring(with: match.range(at: 1)).replacingOccurrences(of: ",", with: ".")
            return Double(str)
        }

        func lastMatch(_ pattern: NSRegularExpression, _ text: String, _ min: Double, _ max: Double) -> Double? {
            let matches = pattern.matches(in: text, range: NSRange(text.startIndex..., in: text))
            guard let match = matches.last, match.numberOfRanges > 1 else { return nil }
            let str = (text as NSString).substring(with: match.range(at: 1)).replacingOccurrences(of: ",", with: ".")
            guard let val = Double(str), val > min, val <= max else { return nil }
            return val
        }

        let kcal = firstMatch(kcalMatchPattern, lower)
        let protein = lastMatch(proteinMatchPattern, lower, 0, 500)
        let fats = lastMatch(fatsMatchPattern, lower, 0, 300)
        let carbs = lastMatch(carbsMatchPattern, lower, 0, 500)

        guard kcal != nil || protein != nil || fats != nil || carbs != nil else { return nil }
        return MacroOutput(kcal: kcal, protein: protein, fats: fats, carbs: carbs)
    }

    private static func calculateConfidence(matches: [DatasetMatch], tokenCount: Int) -> Double {
        guard !matches.isEmpty else { return 0.0 }
        let topScore = matches[0].score
        let matchCount = matches.filter { $0.score >= 0.3 }.count
        let tokenCoverage = tokenCount > 0 ? Double(matchCount) / Double(max(tokenCount, 1)) : 0.0
        return min(max(topScore * 0.5 + (Double(min(matchCount, 5)) / 5.0) * 0.3 + min(tokenCoverage, 1.0) * 0.2, 0.0), 1.0)
    }
}
