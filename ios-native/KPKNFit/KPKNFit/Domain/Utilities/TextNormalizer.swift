import Foundation

enum TextNormalizer {
    static func normalize(_ text: String) -> String {
        let folded = text.folding(options: [.diacriticInsensitive, .caseInsensitive, .widthInsensitive], locale: .current)
        let stripped = folded.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) || CharacterSet.whitespaces.contains($0) }
        return String(stripped).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func tokens(_ text: String) -> [String] {
        normalize(text).components(separatedBy: .whitespaces).filter { !$0.isEmpty }
    }

    static func similarity(_ a: String, _ b: String) -> Double {
        let na = normalize(a)
        let nb = normalize(b)
        guard !na.isEmpty, !nb.isEmpty else { return 0 }
        if na == nb { return 1.0 }
        let distance = levenshtein(na, nb)
        let maxLen = Double(max(na.count, nb.count, 1))
        return 1.0 - (Double(distance) / maxLen)
    }

    static func contains(_ needle: String, in haystack: String) -> Bool {
        normalize(haystack).contains(normalize(needle))
    }

    static func startsWith(_ prefix: String, in text: String) -> Bool {
        normalize(text).hasPrefix(normalize(prefix))
    }

    static func levenshtein(_ a: String, _ b: String) -> Int {
        let a = Array(a)
        let b = Array(b)
        var dp = Array(repeating: Array(repeating: 0, count: b.count + 1), count: a.count + 1)
        for i in 0...a.count { dp[i][0] = i }
        for j in 0...b.count { dp[0][j] = j }
        for i in 1...a.count {
            for j in 1...b.count {
                let cost = a[i-1] == b[j-1] ? 0 : 1
                dp[i][j] = min(dp[i-1][j] + 1, dp[i][j-1] + 1, dp[i-1][j-1] + cost)
            }
        }
        return dp[a.count][b.count]
    }

    static func camelToSnake(_ text: String) -> String {
        let pattern = "([a-z0-9])([A-Z])"
        let replaced = text.replacingOccurrences(of: pattern, with: "$1_$2", options: .regularExpression)
        return replaced.lowercased()
    }

    static func snakeToCamel(_ text: String) -> String {
        let parts = text.split(separator: "_")
        guard let first = parts.first else { return text }
        return String(first).lowercased() + parts.dropFirst().map { $0.prefix(1).uppercased() + $0.dropFirst().lowercased() }.joined()
    }

    static func isNumeric(_ text: String) -> Bool {
        Double(text) != nil
    }

    static func extractNumber(_ text: String) -> Double? {
        let pattern = "[-+]?\\d*\\.?\\d+"
        guard let range = text.range(of: pattern, options: .regularExpression) else { return nil }
        return Double(text[range])
    }
}
