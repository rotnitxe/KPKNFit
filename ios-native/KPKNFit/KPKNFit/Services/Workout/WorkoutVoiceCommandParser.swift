import Foundation

internal enum WorkoutVoiceCommandParser {

    private static let CONFIRM_KEYWORDS: Set<String> = [
        "si", "sí", "confirmar", "confirmado", "dale", "ok", "okey",
        "listo", "correcto", "eso", "exacto", "aplicar", "aceptar",
        "registrar", "guardar", "bueno", "bien", "perfecto",
    ]

    private static let CANCEL_KEYWORDS: Set<String> = [
        "no", "cancelar", "corregir", "borrar", "mal",
        "equivocado", "error", "descartar", "anular", "nulo",
        "cancelado", "niego",
    ]

    private static let SKIP_SET_KEYWORDS: Set<String> = [
        "saltar serie", "omitir serie", "saltar set", "omitir set",
        "pasar serie", "avanzar serie",
    ]

    private static let SKIP_KEYWORDS: Set<String> = [
        "saltar", "siguiente", "omitir", "pasar", "adelante",
        "siguiente ejercicio", "proximo", "próximo", "avanzar",
        "saltar ejercicio", "omitir ejercicio", "pasar ejercicio",
    ]

    private static let PREVIOUS_KEYWORDS: Set<String> = [
        "anterior", "volver", "regresar", "atras", "atrás",
        "retroceder", "antes",
    ]

    private static let SUGGEST_WEIGHT_KEYWORDS: Set<String> = [
        "cuanto peso", "cuánto peso", "carga sugerida", "que peso",
        "qué peso", "cuanto pongo", "cuánto pongo", "peso sugerido",
        "carga", "cuanto levanto", "cuánto levanto",
    ]

    private static let REST_STATUS_KEYWORDS: Set<String> = [
        "cuanto falta", "cuánto falta", "descanso", "timer", "tiempo",
        "cronometro", "cronómetro", "cuanto queda", "cuánto queda",
        "restante", "falta",
    ]

    private static let WHAT_EXERCISE_KEYWORDS: Set<String> = [
        "que toca", "qué toca", "que ejercicio", "qué ejercicio",
        "donde voy", "dónde voy", "cual sigue", "cuál sigue",
        "en que voy", "en qué voy",
    ]

    private static let NEXT_EXERCISE_KEYWORDS: Set<String> = [
        "que sigue", "qué sigue", "proximo ejercicio", "próximo ejercicio",
        "despues", "después",
    ]

    private static let TURN_OFF_VOICE_KEYWORDS: Set<String> = [
        "apagar voz", "silencio", "desactivar voz", "apagar microfono",
        "apagar micrófono", "callar",
    ]

    private static let FINISH_SESSION_KEYWORDS: Set<String> = [
        "finalizar sesion", "finalizar entrenamiento", "terminar sesion",
        "terminar entrenamiento", "acabar sesion", "finalizar", "terminar",
    ]

    private static let CANCEL_SESSION_KEYWORDS: Set<String> = [
        "cancelar sesion", "cancelar entrenamiento", "descartar entrenamiento",
        "descartar sesion", "eliminar entrenamiento",
    ]

    // MARK: - Parse

    static func parseCommand(
        transcript: String,
        isTimeMode: Bool,
        isUnilateral: Bool,
        hasPendingConfirmation: Bool,
        isRestTimerActive: Bool
    ) -> VoiceSessionCommand {
        let lower = normalizeText(transcript)

        if hasPendingConfirmation {
            if CONFIRM_KEYWORDS.contains(where: { lower.contains($0) }) {
                return .confirm
            }
            if CANCEL_KEYWORDS.contains(where: { lower.contains($0) }) {
                return .cancel
            }
        }

        if FINISH_SESSION_KEYWORDS.contains(where: { lower.contains($0) }) {
            return .finishSession
        }

        if CANCEL_SESSION_KEYWORDS.contains(where: { lower.contains($0) }) {
            return .cancelSession
        }

        if TURN_OFF_VOICE_KEYWORDS.contains(where: { lower.contains($0) }) {
            return .turnOffVoice
        }

        if SKIP_SET_KEYWORDS.contains(where: { lower.contains($0) }) {
            return .skipSet
        }

        if SKIP_KEYWORDS.contains(where: { lower.contains($0) }) {
            return .skipExercise
        }

        if PREVIOUS_KEYWORDS.contains(where: { lower.contains($0) }) {
            return .previousExercise
        }

        if SUGGEST_WEIGHT_KEYWORDS.contains(where: { lower.contains($0) }) {
            return .suggestWeight
        }

        if REST_STATUS_KEYWORDS.contains(where: { lower.contains($0) }) {
            return .restStatus
        }

        if WHAT_EXERCISE_KEYWORDS.contains(where: { lower.contains($0) }) {
            return .whatExercise
        }

        if NEXT_EXERCISE_KEYWORDS.contains(where: { lower.contains($0) }) {
            return .nextExercise
        }

        if let interpretation = parseWorkoutVoiceTranscript(transcript, isTimeMode: isTimeMode, isUnilateral: isUnilateral) {
            return .registerSet(interpretation)
        }

        return .unknown(transcript)
    }

    // MARK: - Private Voice Transcript Parsing

    private static func parseWorkoutVoiceTranscript(
        _ transcript: String,
        isTimeMode: Bool,
        isUnilateral: Bool
    ) -> WorkoutVoiceInterpretation? {
        let tokens = normalizeWorkoutVoiceTranscript(transcript)
        guard !tokens.isEmpty else { return nil }

        let explicitWeight = tokens.firstIndex(where: { WEIGHT_WORDS.contains($0) })
            .flatMap { nearestVoiceNumber(tokens, index: $0, preferBackward: true) }
        let explicitReps = tokens.firstIndex(where: { REP_WORDS.contains($0) })
            .flatMap { nearestVoiceNumber(tokens, index: $0, preferBackward: true)?.toSafeWholeNumber() }
        let explicitSeconds = tokens.firstIndex(where: { SECOND_WORDS.contains($0) })
            .flatMap { nearestVoiceNumber(tokens, index: $0, preferBackward: true)?.toSafeWholeNumber() }
        let explicitMinutes = tokens.firstIndex(where: { MINUTE_WORDS.contains($0) })
            .flatMap { nearestVoiceNumber(tokens, index: $0, preferBackward: true)?.toSafeWholeNumber().map { $0 * 60 } }
        let explicitRpe = tokens.firstIndex(where: { RPE_WORDS.contains($0) })
            .flatMap { nearestVoiceNumber(tokens, index: $0) }
        let explicitRir = tokens.firstIndex(where: { RIR_WORDS.contains($0) })
            .flatMap { nearestVoiceNumber(tokens, index: $0) }
        let explicitPercentRm = tokens.firstIndex(where: { PERCENT_RM_WORDS.contains($0) })
            .flatMap { nearestVoiceNumber(tokens, index: $0) }
        let connectorPair = extractConnectedWeightAndMetric(tokens)

        let side: String?
        if isUnilateral {
            let hasLeft = tokens.contains(where: { LEFT_SIDE_WORDS.contains($0) })
            let hasRight = tokens.contains(where: { RIGHT_SIDE_WORDS.contains($0) })
            if hasLeft && !hasRight {
                side = "left"
            } else if hasRight && !hasLeft {
                side = "right"
            } else {
                side = nil
            }
        } else {
            side = nil
        }

        let reachedFailure = tokens.contains(where: { FAILURE_WORDS.contains($0) })

        let weightKg: Double? = isTimeMode ? explicitWeight : (explicitWeight ?? connectorPair?.0)
        let metricValue: Int? = isTimeMode
            ? (explicitSeconds ?? explicitMinutes ?? connectorPair?.1)
            : (explicitReps ?? connectorPair?.1)

        let intensityValue: Double? = explicitRpe ?? explicitRir ?? explicitPercentRm
        let intensityKind: WorkoutVoiceIntensityKind? = explicitRpe != nil ? .RPE : explicitRir != nil ? .RIR : explicitPercentRm != nil ? .PERCENT_RM : nil

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

    // MARK: - Normalization

    private static func normalizeText(_ text: String) -> String {
        let decomposed = text.lowercased()
            .decomposedStringWithCanonicalMapping
            .replacingOccurrences(of: "\\p{Mn}+", with: "", options: .regularExpression)
        let allowed = decomposed.replacingOccurrences(
            of: "[^a-záéíóúüñ0-9.,% ]",
            with: " ",
            options: .regularExpression
        )
        return allowed
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
    }

    private static func normalizeWorkoutVoiceTranscript(_ transcript: String) -> [String] {
        let decomposed = transcript.lowercased()
            .decomposedStringWithCanonicalMapping
            .replacingOccurrences(of: "\\p{Mn}+", with: "", options: .regularExpression)
            .replacingOccurrences(of: "×", with: " x ")
        let allowed = decomposed.replacingOccurrences(
            of: "[^a-z0-9.,% ]",
            with: " ",
            options: .regularExpression
        )
        return allowed
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
            .components(separatedBy: " ")
            .filter { !$0.isEmpty }
    }

    // MARK: - Connected Pair

    private static func extractConnectedWeightAndMetric(_ tokens: [String]) -> (Double, Int)? {
        for (index, token) in tokens.enumerated() {
            guard CONNECTOR_WORDS.contains(token) else { continue }
            let left = readVoiceNumberBackward(tokens, startIndex: index - 1)?.0
            let right = readVoiceNumberForward(tokens, startIndex: index + 1)?.0.toSafeWholeNumber()
            if let left = left, let right = right {
                return (left, right)
            }
        }
        return nil
    }

    // MARK: - Number Parsing

    private static func nearestVoiceNumber(
        _ tokens: [String],
        index: Int,
        preferBackward: Bool = false
    ) -> Double? {
        if preferBackward {
            return readVoiceNumberBackward(tokens, startIndex: index - 1)?.0
                ?? readVoiceNumberForward(tokens, startIndex: index + 1)?.0
        }
        return readVoiceNumberForward(tokens, startIndex: index + 1)?.0
            ?? readVoiceNumberBackward(tokens, startIndex: index - 1)?.0
    }

    private static func readVoiceNumberForward(
        _ tokens: [String],
        startIndex: Int
    ) -> (Double, Int)? {
        guard tokens.indices.contains(startIndex) else { return nil }
        var collected: [String] = []
        var idx = startIndex
        while idx < tokens.count && tokens[idx].isVoiceNumberToken() {
            collected.append(tokens[idx])
            idx += 1
        }
        guard let value = parseVoiceNumberTokens(collected) else { return nil }
        return (value, idx)
    }

    private static func readVoiceNumberBackward(
        _ tokens: [String],
        startIndex: Int
    ) -> (Double, Int)? {
        guard tokens.indices.contains(startIndex) else { return nil }
        var collected: [String] = []
        var idx = startIndex
        while idx >= 0 && tokens[idx].isVoiceNumberToken() {
            collected.insert(tokens[idx], at: 0)
            idx -= 1
        }
        guard let value = parseVoiceNumberTokens(collected) else { return nil }
        return (value, idx + 1)
    }

    private static func parseVoiceNumberTokens(_ tokens: [String]) -> Double? {
        guard !tokens.isEmpty else { return nil }
        if tokens.count == 1, tokens[0].range(of: #"^\d+(?:[.,]\d+)?$"#, options: .regularExpression) != nil {
            return Double(tokens[0].replacingOccurrences(of: ",", with: "."))
        }
        if let decimalIdx = tokens.firstIndex(where: { $0 == "punto" || $0 == "coma" }) {
            guard let whole = parseVoiceInteger(Array(tokens[..<decimalIdx])) else { return nil }
            var decimals = ""
            for token in tokens[(decimalIdx + 1)...] {
                guard let digit = decimalDigitForVoiceToken(token) else { return nil }
                decimals.append(digit)
            }
            return Double("\(whole).\(decimals)")
        }
        return parseVoiceInteger(tokens)
    }

    private static func parseVoiceInteger(_ tokens: [String]) -> Double? {
        guard !tokens.isEmpty else { return nil }
        var total = 0.0
        var consumed = false
        for token in tokens {
            if token == "y" { continue }
            if token == "medio" || token == "media" {
                total += 0.5
                consumed = true
            } else if token.range(of: #"^\d+(?:[.,]\d+)?$"#, options: .regularExpression) != nil {
                guard let num = Double(token.replacingOccurrences(of: ",", with: ".")) else { return nil }
                total += num
                consumed = true
            } else if let value = VOICE_INTEGER_WORDS[token] {
                total += Double(value)
                consumed = true
            } else {
                return nil
            }
        }
        return consumed ? total : nil
    }

    private static func decimalDigitForVoiceToken(_ token: String) -> Character? {
        if token.range(of: #"^\d+$"#, options: .regularExpression) != nil, token.count == 1 {
            return token.first
        }
        return VOICE_DECIMAL_DIGITS[token]
    }

    // MARK: - Keywords & Dictionaries

    private static let DIGIT_TOKEN = try! NSRegularExpression(pattern: #"^\d+(?:[.,]\d+)?$"#)

    private static let CONNECTOR_WORDS: Set<String> = ["x", "por"]
    private static let WEIGHT_WORDS: Set<String> = ["kg", "kilo", "kilos", "peso", "carga", "lastre", "asistencia"]
    private static let REP_WORDS: Set<String> = ["rep", "reps", "repeticion", "repeticiones"]
    private static let SECOND_WORDS: Set<String> = ["seg", "segundo", "segundos"]
    private static let MINUTE_WORDS: Set<String> = ["min", "minuto", "minutos"]
    private static let RPE_WORDS: Set<String> = ["rpe"]
    private static let RIR_WORDS: Set<String> = ["rir"]
    private static let PERCENT_RM_WORDS: Set<String> = ["porcentaje", "%", "rm"]
    private static let FAILURE_WORDS: Set<String> = ["fallo", "falla"]
    private static let LEFT_SIDE_WORDS: Set<String> = ["izquierda", "izquierdo", "izq"]
    private static let RIGHT_SIDE_WORDS: Set<String> = ["derecha", "derecho", "der"]

    private static let VOICE_INTEGER_WORDS: [String: Int] = [
        "cero": 0, "un": 1, "uno": 1, "una": 1, "dos": 2, "tres": 3,
        "cuatro": 4, "cinco": 5, "seis": 6, "siete": 7, "ocho": 8, "nueve": 9,
        "diez": 10, "once": 11, "doce": 12, "trece": 13, "catorce": 14,
        "quince": 15, "dieciseis": 16, "diecisiete": 17, "dieciocho": 18,
        "diecinueve": 19, "veinte": 20, "veintiuno": 21, "veintidos": 22,
        "veintitres": 23, "veinticuatro": 24, "veinticinco": 25,
        "veintiseis": 26, "veintisiete": 27, "veintiocho": 28, "veintinueve": 29,
        "treinta": 30, "cuarenta": 40, "cincuenta": 50, "sesenta": 60,
        "setenta": 70, "ochenta": 80, "noventa": 90, "cien": 100, "ciento": 100,
    ]

    private static let VOICE_DECIMAL_DIGITS: [String: Character] = [
        "cero": "0", "un": "1", "uno": "1", "una": "1",
        "dos": "2", "tres": "3", "cuatro": "4", "cinco": "5",
        "seis": "6", "siete": "7", "ocho": "8", "nueve": "9",
    ]

    // MARK: - Feedback Parsing

    static func parseFeedbackCommand(transcript: String) -> VoiceSessionCommand {
        let lower = normalizeText(transcript)

        let saveKeywords: Set<String> = ["guardar", "listo", "ok", "guardar feedback", "terminar feedback", "completar"]
        let isSaveAction = saveKeywords.contains(where: { lower.contains($0) })

        var technicalQuality: Int?
        if lower.contains("calidad") || lower.contains("tecnica") || lower.contains("ejecucion") {
            if lower.contains("excelente") || lower.contains("perfecta") {
                technicalQuality = 10
            } else if lower.contains("muy buena") {
                technicalQuality = 9
            } else if lower.contains("buena") {
                technicalQuality = 8
            } else if lower.contains("regular") || lower.contains("mas o menos") {
                technicalQuality = 6
            } else if lower.contains("mala") || lower.contains("pesima") {
                technicalQuality = 3
            } else {
                technicalQuality = extractNumberFromText(lower).flatMap { Int($0) }.map { max(1, min(10, $0)) }
            }
        }

        var perceivedIntensity: Double?
        if lower.contains("intensidad") || lower.contains("rpe") || lower.contains("esfuerzo") || lower.contains("fatiga") {
            perceivedIntensity = extractNumberFromText(lower).map { max(1.0, min(10.0, $0)) }
        }

        var discomfortId: String?
        if lower.contains("molestia") || lower.contains("dolor") || lower.contains("tiron") {
            discomfortId = matchDiscomfortJointId(lower)
        }

        return .logFeedback(
            technicalQuality: technicalQuality,
            discomfortId: discomfortId,
            perceivedIntensity: perceivedIntensity,
            isSaveAction: isSaveAction,
            exerciseSearchName: lower
        )
    }

    static func parseFinalFeedbackCommand(transcript: String) -> VoiceSessionCommand {
        let lower = normalizeText(transcript)

        let saveKeywords: Set<String> = [
            "guardar y terminar", "guardar entrenamiento", "guardar sesion",
            "terminar entrenamiento", "finalizar entrenamiento", "finalizar sesion"
        ]
        let isSaveAction = saveKeywords.contains(where: { lower.contains($0) })

        var neural: Int?
        var spinal: Int?
        if lower.contains("nerviosa") || lower.contains("neural") || lower.contains("cns") || lower.contains("sistema") {
            neural = extractNumberFromText(lower).flatMap { Int($0) }.map { max(0, min(100, $0)) }
        }
        if lower.contains("espinal") || lower.contains("columna") || lower.contains("espalda") {
            spinal = extractNumberFromText(lower).flatMap { Int($0) }.map { max(0, min(100, $0)) }
        }

        var discomfortId: String?
        if lower.contains("molestia") || lower.contains("dolor") || lower.contains("tiron") {
            discomfortId = matchDiscomfortJointId(lower)
        }

        var discomfortNote: String?
        let discomfortNoteKeywords = [
            "nota de molestia", "notas de molestia", "detalles de molestia",
            "detalle de molestia", "detalles de la molestia"
        ]
        for keyword in discomfortNoteKeywords {
            if let range = lower.range(of: keyword) {
                let afterIdx = lower.index(range.upperBound, offsetBy: 0)
                if afterIdx < lower.endIndex {
                    discomfortNote = String(lower[afterIdx...])
                        .trimmingCharacters(in: .whitespaces)
                        .replacingOccurrences(of: "^:", with: "", options: .regularExpression)
                        .trimmingCharacters(in: .whitespaces)
                    break
                }
            }
        }

        var sessionNote: String?
        let noteKeywords = [
            "nota de sesion", "notas de sesion", "comentario de sesion",
            "comentarios de sesion", "comentario", "comentarios", "nota", "notas",
            "observacion", "observaciones"
        ]
        if discomfortNote == nil {
            for keyword in noteKeywords {
                if let range = lower.range(of: keyword) {
                    let afterIdx = lower.index(range.upperBound, offsetBy: 0)
                    if afterIdx < lower.endIndex {
                        sessionNote = String(lower[afterIdx...])
                            .trimmingCharacters(in: .whitespaces)
                            .replacingOccurrences(of: "^:", with: "", options: .regularExpression)
                            .trimmingCharacters(in: .whitespaces)
                        break
                    }
                }
            }
        }

        return .logFinalFeedback(
            notes: sessionNote,
            discomfortId: discomfortId,
            additionalDiscomfortNote: discomfortNote,
            neuralBattery: neural,
            spinalBattery: spinal,
            isSaveAction: isSaveAction
        )
    }

    // MARK: - Helpers

    private static func matchDiscomfortJointId(_ text: String) -> String? {
        if text.contains("hombro") { return "shoulder_anterior" }
        if text.contains("rodilla") { return "knee_patellar" }
        if text.contains("codo") { return "elbow_lateral" }
        if text.contains("lumbar") || text.contains("espalda baja") { return "lower_back" }
        if text.contains("muneca") || text.contains("muñeca") { return "wrist" }
        if text.contains("cadera") { return "hip" }
        if text.contains("tobillo") { return "ankle" }
        if text.contains("ninguna") || text.contains("sin molestia") || text.contains("todo bien") { return "none" }
        return nil
    }

    private static func extractNumberFromText(_ text: String) -> Double? {
        if let match = text.range(of: #"\d+(?:[.,]\d+)?"#, options: .regularExpression) {
            return Double(text[match].replacingOccurrences(of: ",", with: "."))
        }
        let tokens = text.components(separatedBy: " ")
        for token in tokens {
            let normalized = token.trimmingCharacters(in: .whitespaces)
            if let value = VOICE_INTEGER_WORDS[normalized] {
                return Double(value)
            }
        }
        return nil
    }
}

// MARK: - String Token Helper

private extension String {
    func isVoiceNumberToken() -> Bool {
        self.range(of: #"^\d+(?:[.,]\d+)?$"#, options: .regularExpression) != nil
            || WorkoutVoiceCommandParser.isVoiceWordToken(self)
    }
}

internal extension WorkoutVoiceCommandParser {
    static func isVoiceWordToken(_ token: String) -> Bool {
        VOICE_INTEGER_WORDS.keys.contains(token)
            || VOICE_DECIMAL_DIGITS.keys.contains(token)
            || token == "punto"
            || token == "coma"
            || token == "y"
            || token == "medio"
            || token == "media"
    }
}

// MARK: - Double Helper

private extension Double {
    func toSafeWholeNumber() -> Int? {
        abs(self - Double(Int(self))) < 0.001 ? Int(self) : nil
    }
}
