import Foundation

func parseWorkoutVoiceTranscript(
    transcript: String,
    isTimeMode: Bool,
    isUnilateral: Bool
) -> WorkoutVoiceInterpretation? {
    let tokens = normalizeWorkoutVoiceTranscript(transcript)
    guard !tokens.isEmpty else { return nil }

    let explicitWeight = tokens.firstIndex { CONNECTOR_KEYWORDS.contains($0) }
        .flatMap { nearestVoiceNumber(tokens, index: $0, preferBackward: true) }
    let explicitReps = tokens.firstIndex { REP_KEYWORDS.contains($0) }
        .flatMap { nearestVoiceNumber(tokens, index: $0, preferBackward: true).flatMap { $0.toSafeWholeNumber() } }
    let explicitSeconds = tokens.firstIndex { SECOND_KEYWORDS.contains($0) }
        .flatMap { nearestVoiceNumber(tokens, index: $0, preferBackward: true).flatMap { $0.toSafeWholeNumber() } }
    let explicitMinutes = tokens.firstIndex { MINUTE_KEYWORDS.contains($0) }
        .flatMap { nearestVoiceNumber(tokens, index: $0, preferBackward: true).flatMap { $0.toSafeWholeNumber().map { $0 * 60 } } }
    let explicitRpe = tokens.firstIndex { RPE_KEYWORDS.contains($0) }
        .flatMap { nearestVoiceNumber(tokens, index: $0) }
    let explicitRir = tokens.firstIndex { RIR_KEYWORDS.contains($0) }
        .flatMap { nearestVoiceNumber(tokens, index: $0) }
    let explicitPercentRm = tokens.firstIndex { PERCENT_RM_KEYWORDS.contains($0) }
        .flatMap { nearestVoiceNumber(tokens, index: $0) }
    let explicitFailureMetric = tokens.firstIndex { FAILURE_DISTANCE_KEYWORDS.contains($0) }
        .flatMap { nearestVoiceNumber(tokens, index: $0, preferBackward: true) }
    let connectorPair = extractConnectedWeightAndMetric(tokens)

    let side: String? = {
        guard isUnilateral else { return nil }
        let hasLeft = tokens.contains { LEFT_SIDE_KEYWORDS.contains($0) }
        let hasRight = tokens.contains { RIGHT_SIDE_KEYWORDS.contains($0) }
        if hasLeft && !hasRight { return "left" }
        if hasRight && !hasLeft { return "right" }
        return nil
    }()

    let reachedFailure = tokens.contains { FAILURE_KEYWORDS.contains($0) }

    let weightKg = isTimeMode ? explicitWeight : (explicitWeight ?? connectorPair?.0)
    let metricValue: Int? = isTimeMode
        ? (explicitSeconds ?? explicitMinutes ?? connectorPair?.1)
        : (explicitReps ?? connectorPair?.1)
    let intensityValue = explicitRpe ?? explicitRir ?? explicitPercentRm ?? explicitFailureMetric
    let intensityKind: WorkoutVoiceIntensityKind? = {
        if explicitRpe != nil { return .RPE }
        if explicitRir != nil { return .RIR }
        if explicitPercentRm != nil { return .PERCENT_RM }
        if explicitFailureMetric != nil { return .RIR }
        return nil
    }()

    var fields = Set<WorkoutVoiceField>()
    if weightKg != nil { fields.insert(.WEIGHT) }
    if metricValue != nil { fields.insert(.VALUE) }
    if intensityValue != nil { fields.insert(.INTENSITY) }
    if side != nil { fields.insert(.SIDE) }
    if reachedFailure { fields.insert(.FAILURE) }

    guard !fields.isEmpty else { return nil }

    return WorkoutVoiceInterpretation(
        transcript: transcript.trimmingCharacters(in: .whitespaces),
        weightKg: weightKg,
        metricValue: metricValue,
        intensityValue: intensityValue,
        intensityKind: intensityKind,
        side: side,
        reachedFailure: reachedFailure,
        fields: fields
    )
}

func workoutVoiceSummary(interpretation: WorkoutVoiceInterpretation, isTimeMode: Bool) -> String {
    var parts: [String] = []
    if let w = interpretation.weightKg { parts.append("\(toTrimmedNumberString(w)) kg") }
    if let v = interpretation.metricValue { parts.append(isTimeMode ? "\(v) s" : "\(v) reps") }
    if let iv = interpretation.intensityValue {
        let label: String = {
            switch interpretation.intensityKind {
            case .RIR: return "RIR \(toTrimmedNumberString(iv))"
            case .PERCENT_RM: return "\(toTrimmedNumberString(iv))%RM"
            default: return "RPE \(toTrimmedNumberString(iv))"
            }
        }()
        parts.append(label)
    }
    if let s = interpretation.side { parts.append(s == "left" ? "Izquierda" : "Derecha") }
    if interpretation.reachedFailure { parts.append("Fallo") }
    return parts.joined(separator: " · ")
}

func workoutVoiceAppliedMessage(interpretation: WorkoutVoiceInterpretation, isTimeMode: Bool) -> String {
    let summary = workoutVoiceSummary(interpretation: interpretation, isTimeMode: isTimeMode)
    return summary.isEmpty ? "Voz aplicada al borrador." : "Voz aplicada: \(summary)"
}

func workoutVoiceIntensityText(
    interpretation: WorkoutVoiceInterpretation,
    baseIntensityMode: IntensityMode?
) -> String {
    guard let raw = interpretation.intensityValue else { return "" }
    if interpretation.reachedFailure { return "" }
    let normalized: Double = {
        switch baseIntensityMode {
        case .RIR:
            switch interpretation.intensityKind {
            case .RIR: return raw
            default: return max(0, 10.0 - raw)
            }
        case .soloRm: return raw
        default:
            switch interpretation.intensityKind {
            case .RIR: return max(0, 10.0 - raw)
            default: return raw
            }
        }
    }()
    return toTrimmedNumberString(normalized)
}

// MARK: - Private helpers

private func normalizeWorkoutVoiceTranscript(_ transcript: String) -> [String] {
    let normalized = transcript.lowercased()
        .folding(options: .diacriticInsensitive, locale: .current)
        .replacingOccurrences(of: "×", with: " x ")
        .replacingOccurrences(of: "[^a-z0-9.,% ]", with: " ", options: .regularExpression)
        .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        .trimmingCharacters(in: .whitespaces)
    return normalized.split(separator: " ").map(String.init).filter { !$0.isEmpty }
}

private func extractConnectedWeightAndMetric(_ tokens: [String]) -> (Double, Int)? {
    for (index, token) in tokens.enumerated() {
        guard CONNECTOR_KEYWORDS.contains(token) else { continue }
        if index == 0 || index >= tokens.count - 1 { continue }
        let left = readVoiceNumberBackward(tokens, startIndex: index - 1)?.0
        let right = readVoiceNumberForward(tokens, startIndex: index + 1)?.0.flatMap { $0.toSafeWholeNumber() }
        if let l = left, let r = right { return (l, r) }
    }
    return nil
}

private func nearestVoiceNumber(_ tokens: [String], index: Int, preferBackward: Bool = false) -> Double? {
    if preferBackward {
        return readVoiceNumberBackward(tokens, startIndex: index - 1)?.0
            ?? readVoiceNumberForward(tokens, startIndex: index + 1)?.0
    }
    return readVoiceNumberForward(tokens, startIndex: index + 1)?.0
        ?? readVoiceNumberBackward(tokens, startIndex: index - 1)?.0
}

private func readVoiceNumberForward(_ tokens: [String], startIndex: Int) -> (Double, Int)? {
    guard tokens.indices.contains(startIndex) else { return nil }
    var collected: [String] = []
    var idx = startIndex
    while idx < tokens.count && tokens[idx].isVoiceNumberToken {
        collected.append(tokens[idx])
        idx += 1
    }
    guard let value = parseVoiceNumberTokens(collected) else { return nil }
    return (value, idx)
}

private func readVoiceNumberBackward(_ tokens: [String], startIndex: Int) -> (Double, Int)? {
    guard tokens.indices.contains(startIndex) else { return nil }
    var collected: [String] = []
    var idx = startIndex
    while idx >= 0 && tokens[idx].isVoiceNumberToken {
        collected.insert(tokens[idx], at: 0)
        idx -= 1
    }
    guard let value = parseVoiceNumberTokens(collected) else { return nil }
    return (value, idx + 1)
}

private func parseVoiceNumberTokens(_ tokens: [String]) -> Double? {
    guard !tokens.isEmpty else { return nil }
    if tokens.count == 1, let d = Double(tokens[0].replacingOccurrences(of: ",", with: ".")) { return d }

    if let sepIdx = tokens.firstIndex(where: { $0 == "punto" || $0 == "coma" }) {
        guard let whole = parseVoiceInteger(Array(tokens[..<sepIdx])) else { return nil }
        let decimals = tokens[(sepIdx + 1)...].compactMap { t -> Character? in
            if t.count == 1, t.first!.isNumber { return t.first! }
            return VOICE_DECIMAL_DIGITS[t]
        }
        guard decimals.count == tokens.count - sepIdx - 1 else { return nil }
        return Double("\(Int(whole)).\(String(decimals))")
    }
    return parseVoiceInteger(tokens)
}

private func parseVoiceInteger(_ tokens: [String]) -> Double? {
    guard !tokens.isEmpty else { return nil }
    var total = 0.0
    var consumed = false
    for token in tokens {
        switch token {
        case "y": break
        case "medio", "media":
            total += 0.5
            consumed = true
        default:
            if let d = Double(token.replacingOccurrences(of: ",", with: ".")) {
                total += d
                consumed = true
            } else if let word = VOICE_INTEGER_WORDS[token] {
                total += Double(word)
                consumed = true
            } else { return nil }
        }
    }
    return consumed ? total : nil
}

private extension String {
    var isVoiceNumberToken: Bool {
        if Double(self.replacingOccurrences(of: ",", with: ".")) != nil { return true }
        if VOICE_INTEGER_WORDS.keys.contains(self) { return true }
        if VOICE_DECIMAL_DIGITS.keys.contains(self) { return true }
        return self == "punto" || self == "coma" || self == "y" || self == "medio" || self == "media"
    }
}

private extension Double {
    func toSafeWholeNumber() -> Int? {
        guard abs(self - Double(Int(self))) < 0.001 else { return nil }
        return Int(self)
    }
}

// MARK: - Keyword sets

private let CONNECTOR_KEYWORDS: Set<String> = ["x", "por"]
private let REP_KEYWORDS: Set<String> = ["rep", "reps", "repeticion", "repeticiones"]
private let SECOND_KEYWORDS: Set<String> = ["seg", "segundo", "segundos"]
private let MINUTE_KEYWORDS: Set<String> = ["min", "minuto", "minutos"]
private let RPE_KEYWORDS: Set<String> = ["rpe"]
private let RIR_KEYWORDS: Set<String> = ["rir"]
private let PERCENT_RM_KEYWORDS: Set<String> = ["porcentaje", "%", "rm"]
private let FAILURE_KEYWORDS: Set<String> = ["fallo", "falla"]
private let FAILURE_DISTANCE_KEYWORDS: Set<String> = ["recamara", "recamaras", "reserva", "reservas"]
private let LEFT_SIDE_KEYWORDS: Set<String> = ["izquierda", "izquierdo", "izq"]
private let RIGHT_SIDE_KEYWORDS: Set<String> = ["derecha", "derecho", "der"]

private let VOICE_INTEGER_WORDS: [String: Int] = [
    "cero": 0, "un": 1, "uno": 1, "una": 1, "dos": 2, "tres": 3, "cuatro": 4, "cinco": 5,
    "seis": 6, "siete": 7, "ocho": 8, "nueve": 9, "diez": 10, "once": 11, "doce": 12,
    "trece": 13, "catorce": 14, "quince": 15, "dieciseis": 16, "diecisiete": 17, "dieciocho": 18,
    "diecinueve": 19, "veinte": 20, "veintiuno": 21, "veintidos": 22, "veintitres": 23,
    "veinticuatro": 24, "veinticinco": 25, "veintiseis": 26, "veintisiete": 27, "veintiocho": 28,
    "veintinueve": 29, "treinta": 30, "cuarenta": 40, "cincuenta": 50, "sesenta": 60,
    "setenta": 70, "ochenta": 80, "noventa": 90, "cien": 100, "ciento": 100,
]

private let VOICE_DECIMAL_DIGITS: [String: Character] = [
    "cero": "0", "un": "1", "uno": "1", "una": "1", "dos": "2", "tres": "3",
    "cuatro": "4", "cinco": "5", "seis": "6", "siete": "7", "ocho": "8", "nueve": "9",
]
