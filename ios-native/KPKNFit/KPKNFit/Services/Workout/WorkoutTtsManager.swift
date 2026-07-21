import Foundation
import AVFoundation

final class WorkoutTtsManager: NSObject {

    private let synthesizer = AVSpeechSynthesizer()
    private var _isInitialized = false
    private var _initError: String?
    private var _onUtteranceComplete: (() -> Void)?

    var isInitialized: Bool { _isInitialized }
    var initError: String? { _initError }

    func initialize(onReady: (() -> Void)? = nil, onError: ((String) -> Void)? = nil) {
        synthesizer.stopSpeaking(at: .immediate)

        let voices = AVSpeechSynthesisVoice.speechVoices()
        let preferredLang = "es-CL"
        let fallbackLang = "es-ES"
        let voice = voices.first(where: { $0.language == preferredLang })
            ?? voices.first(where: { $0.language == fallbackLang })
            ?? AVSpeechSynthesisVoice(language: fallbackLang)

        guard voice != nil else {
            _isInitialized = false
            _initError = "TTS init error: no voice found"
            onError?("TTS init error: no voice found")
            return
        }

        synthesizer.delegate = self
        _isInitialized = true
        _initError = nil
        onReady?()
    }

    func setOnUtteranceComplete(_ callback: (() -> Void)?) {
        _onUtteranceComplete = callback
    }

    func speakSuggestedWeight(weightKg: Double, exerciseName: String) {
        let rounded = formatWeight(kg: weightKg)
        speak("Carga sugerida para \(exerciseName): \(rounded).", queueFlush: true)
    }

    func speakSetConfirmation(weightKg: Double?, reps: Int?, rpe: Double?, rir: Int?, isTimeMode: Bool) {
        var parts: [String] = []
        if let w = weightKg { parts.append("\(formatWeight(kg: w))") }
        if let r = reps { parts.append(isTimeMode ? "\(r) segundos" : "\(r) repeticiones") }
        if let rpe = rpe { parts.append("RPE \(formatDecimal(value: rpe))") }
        if let rir = rir { parts.append("RIR \(rir)") }
        let summary = parts.joined(separator: ", ")
        speak("\(summary). ¿Confirmar?", queueFlush: true)
    }

    func speakSetRegistered(weightKg: Double?, reps: Int?, isTimeMode: Bool) {
        var summary = ""
        if let w = weightKg { summary += "\(formatWeight(kg: w)), " }
        if let r = reps { summary += isTimeMode ? "\(r) segundos. " : "\(r) repeticiones. " }
        speak("Serie registrada\(summary.isEmpty ? "." : ": \(summary)")", queueFlush: true)
    }

    func speakAutoConfirmed() {
        speak("Serie confirmada automáticamente.", queueFlush: false)
    }

    func speakRestRemaining(minutes: Int, seconds: Int) {
        let text: String
        if minutes > 0 && seconds > 0 {
            text = "Descansas \(minutes) minutos \(seconds) segundos."
        } else if minutes > 0 {
            text = "Descansas \(minutes) minutos."
        } else {
            text = "Descansas \(seconds) segundos."
        }
        speak(text, queueFlush: true)
    }

    func speakNextExercise(name: String, restSeconds: Int?) {
        let restText = restSeconds.map { formatRestTime(totalSeconds: $0) } ?? ""
        let prefix = restText.isEmpty ? "" : ", \(restText)"
        speak("Siguiente: \(name)\(prefix).", queueFlush: false)
    }

    func speakRestComplete(exerciseName: String, suggestedWeight: Double?) {
        let weightText = suggestedWeight.map { w in " Carga sugerida: \(formatWeight(kg: w))." } ?? ""
        speak("Descanso completo. \(exerciseName).\(weightText)", queueFlush: true)
    }

    func speakCurrentExercise(name: String, setNumber: Int, totalSets: Int, round: Int? = nil) {
        let prefix = round.map { "Superserie ronda \($0). " } ?? ""
        speak("\(prefix)\(name), serie \(setNumber) de \(totalSets).", queueFlush: true)
    }

    func speakRestStarted(totalSeconds: Int) {
        let restText = formatRestTime(totalSeconds: totalSeconds)
        speak("Descanso iniciado por \(restText).", queueFlush: false)
    }

    func speakRestStartedContextual(seconds: Int, isTransition: Bool) {
        let restText = formatRestTime(totalSeconds: seconds)
        let text: String
        if isTransition {
            text = "Descanso de transición por \(restText)."
        } else {
            text = "Ronda completada. Descanso de ronda por \(restText)."
        }
        speak(text, queueFlush: false)
    }

    func speakUnilateralSideRegistered(completedSide: String, pendingSide: String) {
        let comp = completedSide == "left" ? "izquierdo" : "derecho"
        let pend = pendingSide == "left" ? "izquierdo" : "derecho"
        speak("Lado \(comp) registrado. Siguiente: Lado \(pend).", queueFlush: true)
    }

    func speakSessionSaved() {
        speak("Entrenamiento guardado con éxito. ¡Felicitaciones por completar tu sesión!", queueFlush: true)
    }

    func speakError(_ message: String) {
        speak(message, queueFlush: true)
    }

    func speakVoiceOn() {
        speak("Voz activada. Di la palabra de activación para comandos.", queueFlush: true)
    }

    func speakVoiceOff() {
        speak("Voz desactivada.", queueFlush: true)
    }

    func speak(_ text: String, queueFlush: Bool = false) {
        guard _isInitialized else { return }

        if queueFlush {
            synthesizer.stopSpeaking(at: .immediate)
        }

        let utterance = AVSpeechUtterance(string: text)
        let voices = AVSpeechSynthesisVoice.speechVoices()
        utterance.voice = voices.first(where: { $0.language == "es-CL" })
            ?? voices.first(where: { $0.language == "es-ES" })
            ?? AVSpeechSynthesisVoice(language: "es-ES")
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        utterance.volume = 1.0

        synthesizer.speak(utterance)
    }

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
    }

    func shutdown() {
        _onUtteranceComplete = nil
        synthesizer.stopSpeaking(at: .immediate)
        _isInitialized = false
    }

    // MARK: - Private

    private func formatWeight(kg: Double) -> String {
        if kg == Double(Int(kg)) {
            return "\(Int(kg)) kilos"
        }
        return "\(String(format: "%.1f", kg).replacingOccurrences(of: ",", with: ".")) kilos"
    }

    private func formatDecimal(value: Double) -> String {
        if value == Double(Int(value)) {
            return "\(Int(value))"
        }
        return String(format: "%.1f", value).replacingOccurrences(of: ",", with: ".")
    }

    private func formatRestTime(totalSeconds: Int) -> String {
        let mins = totalSeconds / 60
        let secs = totalSeconds % 60
        if mins > 0 && secs > 0 {
            return "descansas \(mins) minutos \(secs) segundos"
        }
        if mins > 0 {
            return "descansas \(mins) minutos"
        }
        return "descansas \(secs) segundos"
    }
}

// MARK: - AVSpeechSynthesizerDelegate

extension WorkoutTtsManager: AVSpeechSynthesizerDelegate {
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        _onUtteranceComplete?()
        _onUtteranceComplete = nil
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        _onUtteranceComplete?()
        _onUtteranceComplete = nil
    }
}
