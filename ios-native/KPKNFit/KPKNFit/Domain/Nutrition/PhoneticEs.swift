import Foundation

/// PhoneticEs — Metaphone adapted for Spanish for food name matching.
/// Collapses: b/v, c/z/s, h muda, j/g suave, ll/y, qu/k.
/// Used by SmartFoodResolver for fuzzy token matching.
enum PhoneticEs {

    static func encode(_ word: String) -> String {
        var s = word.lowercased()
            .folding(options: .diacriticInsensitive, locale: .current)
            .trimmingCharacters(in: .whitespaces)

        if s.isEmpty { return s }
        if s.count <= 2 { return s.uppercased() }

        // Leading h is silent in Spanish
        if s.hasPrefix("h") { s = String(s.dropFirst()) }
        if s.isEmpty { return s }

        var result = ""
        let chars = Array(s)
        var i = 0

        while i < chars.count {
            let c = chars[i]
            let next = (i + 1 < chars.count) ? chars[i + 1] : nil

            switch c {
            case "b", "v":
                result += "B"
            case "c" where next == "h":
                result += "CH"
                i += 1
            case "c" where next != nil && (next == "e" || next == "i"):
                result += "S"
            case "c" where next == "u" && (i + 2 < chars.count) && (chars[i + 2] == "e" || chars[i + 2] == "i"):
                result += "K"
            case "c":
                result += "K"
            case "z":
                result += "S"
            case "g" where next != nil && (next == "e" || next == "i"):
                result += "J"
            case "g" where next == "u":
                result += "G"
                if (i + 2 < chars.count) && (chars[i + 2] == "e" || chars[i + 2] == "i") {
                    i += 1
                }
            case "g":
                result += "G"
            case "h":
                break
            case "j":
                result += "J"
            case "l" where next == "l":
                result += "Y"
                i += 1
            case "ñ":
                result += "N"
            case "q" where next == "u":
                result += "K"
                i += 1
            case "r":
                result += "R"
            case "s":
                result += "S"
            case "x":
                result += "S"
            case "w":
                result += "U"
            case "y":
                result += "Y"
            default:
                if isVowel(c) {
                    if result.isEmpty || !isVowel(Character(String(result.suffix(1)))) {
                        result += "A"
                    }
                } else if c.isLetter {
                    result += c.uppercased()
                }
            }
            i += 1
        }

        return result
    }

    private static func isVowel(_ c: Character?) -> Bool {
        guard let c = c else { return false }
        return "aeiouáéíóú".contains(c.lowercased())
    }
}
