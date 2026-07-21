import Foundation
import AVFoundation
import Speech

struct VoiceCapabilityResult {
    let hasAudioPermission: Bool
    let speechRecognizerAvailable: Bool
    let ttsAvailable: Bool
    let canUseVoice: Bool
    let blockingReason: String?
}

enum WorkoutVoicePermissionHelper {

    static func checkVoiceCapability() -> VoiceCapabilityResult {
        let hasAudioPerm = AVAudioSession.sharedInstance().recordPermission == .granted

        let recognizerAvailable: Bool = {
            guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "es-ES")) else {
                return false
            }
            return recognizer.isAvailable
        }()

        let ttsAvailable = AVSpeechSynthesisVoice.speechVoices().contains { $0.language == "es-ES" }

        var reason = ""
        if !hasAudioPerm { reason += "Permiso de micrófono no concedido. " }
        if !recognizerAvailable { reason += "Reconocimiento de voz no disponible. " }
        if !ttsAvailable { reason += "Texto a voz no disponible. " }
        let blockingReason = reason.trimmingCharacters(in: .whitespaces).isEmpty ? nil : reason.trimmingCharacters(in: .whitespaces)

        return VoiceCapabilityResult(
            hasAudioPermission: hasAudioPerm,
            speechRecognizerAvailable: recognizerAvailable,
            ttsAvailable: ttsAvailable,
            canUseVoice: hasAudioPerm && recognizerAvailable,
            blockingReason: blockingReason
        )
    }

    static func needsPermission() -> Bool {
        AVAudioSession.sharedInstance().recordPermission != .granted
    }
}
