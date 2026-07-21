import SwiftUI

enum VoicePipelineStageV2: Equatable {
    case disabled
    case listening
    case processing
    case confirmWait
    case ttsSpeaking
    case errorRecovery
}

struct WorkoutVoiceSessionState {
    let stage: VoicePipelineStageV2
    let partialText: String
    let errorMessage: String?
}

struct WorkoutCommandDock: View {
    let exercise: Exercise?
    let setIndex: Int
    let activeSide: String?
    let isUnilateral: Bool
    let voiceSessionEnabled: Bool
    let voiceSessionState: WorkoutVoiceSessionState
    let onToggleVoice: () -> Void
    let onPrimaryAction: () -> Void
    var primaryActionEnabled: Bool = true
    var sessionAccentColor: Color = .blue
    var isUpdateMode: Bool = false

    @State private var pulseScale: CGFloat = 1.0
    @State private var pulseAlpha: CGFloat = 1.0

    var body: some View {
        let isListening = voiceSessionState.stage == .listening
        let isProcessing = voiceSessionState.stage == .processing || voiceSessionState.stage == .confirmWait || voiceSessionState.stage == .ttsSpeaking

        let voiceIndicatorColor: Color = {
            if isListening { return .green }
            if isProcessing { return .purple }
            if voiceSessionState.stage == .errorRecovery { return .orange }
            return voiceSessionEnabled ? .secondary : .white.opacity(0.38)
        }()

        let voiceText: String = {
            switch voiceSessionState.stage {
            case .listening:
                let partial = voiceSessionState.partialText.trimmingCharacters(in: .whitespaces)
                return partial.isEmpty ? "Escuchando comandos de voz..." : "Escuchando: \"\(partial)\""
            case .processing: return "Procesando..."
            case .confirmWait: return "¿Confirmar? Di \"Sí\" o \"No\""
            case .ttsSpeaking: return "Hablando..."
            case .errorRecovery: return "Reintentando..."
            case .disabled: return ""
            }
        }()

        let buttonIcon: String = {
            if !primaryActionEnabled { return "hourglass" }
            return isUpdateMode ? "arrow.clockwise" : "checkmark"
        }()

        return VStack(alignment: .trailing, spacing: 8) {
            if voiceSessionState.stage != .disabled && !voiceText.isEmpty {
                HStack(spacing: 8) {
                    Circle()
                        .fill(voiceIndicatorColor)
                        .frame(width: 8, height: 8)
                        .scaleEffect(isListening ? pulseScale : 1.0)
                        .opacity(isListening ? pulseAlpha : 1.0)
                    Text(voiceText)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    if let error = voiceSessionState.errorMessage, voiceSessionState.stage == .errorRecovery {
                        Text("(\(error))")
                            .font(.system(size: 12))
                            .foregroundColor(Color(red: 1, green: 0.8, blue: 0.82))
                            .lineLimit(1)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .frame(maxWidth: 292)
                .background(Color(.systemGray6).opacity(0.66))
                .overlay(RoundedRectangle(cornerRadius: 999).stroke(Color.white.opacity(0.08), lineWidth: 1))
                .clipShape(Capsule())
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            ZStack(alignment: .bottomTrailing) {
                Button(action: { if primaryActionEnabled { onPrimaryAction() } }) {
                    Image(systemName: buttonIcon)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(primaryActionEnabled ? .white : .secondary)
                        .frame(width: 45, height: 45)
                        .background(primaryActionEnabled ? sessionAccentColor : Color(.systemGray4))
                        .clipShape(Circle())
                }

                Button(action: onToggleVoice) {
                    Image(systemName: voiceSessionEnabled ? "mic.fill" : "mic.slash.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(voiceSessionEnabled ? .black : .white.opacity(0.78))
                        .frame(width: 32, height: 32)
                        .background(voiceSessionEnabled ? voiceIndicatorColor.opacity(0.92) : Color(.systemGray5).opacity(0.86))
                        .clipShape(Circle())
                }
                .offset(x: -55, y: -10)
                .scaleEffect(isListening ? pulseScale : 1.0)
                .opacity(isListening ? pulseAlpha : 1.0)
            }
            .frame(width: 96, height: 51)
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .onAppear {
            guard isListening else { return }
            withAnimation(.easeInOut(duration: 0.85).repeatForever(autoreverses: true)) {
                pulseScale = 1.15
                pulseAlpha = 0.5
            }
        }
    }
}
