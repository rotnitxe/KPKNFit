import Foundation

enum VoicePipelineStage: String, Codable {
    case DISABLED
    case LISTENING
    case PROCESSING
    case CONFIRM_WAIT
    case TTS_SPEAKING
    case ERROR_RECOVERY
}

struct VoiceSessionState {
    var stage: VoicePipelineStage = .DISABLED
    var partialText: String = ""
    var lastInterpretation: WorkoutVoiceInterpretation?
    var lastCommand: VoiceSessionCommand?
    var errorMessage: String?
    var duckHandle: String?
    var consecutiveErrors: Int = 0

    var isListening: Bool { stage == .LISTENING }
    var isDucking: Bool { duckHandle != nil }
    var hasPendingConfirmation: Bool { stage == .CONFIRM_WAIT }
}

enum VoiceSessionCommand {
    case registerSet(WorkoutVoiceInterpretation)
    case confirm
    case cancel
    case skipExercise
    case skipSet
    case previousExercise
    case suggestWeight
    case restStatus
    case whatExercise
    case nextExercise
    case turnOffVoice
    case finishSession
    case cancelSession
    case logFeedback(technicalQuality: Int?, discomfortId: String?, perceivedIntensity: Double?, isSaveAction: Bool, exerciseSearchName: String?)
    case logFinalFeedback(notes: String?, discomfortId: String?, additionalDiscomfortNote: String?, neuralBattery: Int?, spinalBattery: Int?, isSaveAction: Bool)
    case unknown(String)
}
