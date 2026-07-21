import SwiftUI

enum VoicePipelineStage: Equatable {
    case disabled
    case listening
    case processing
    case confirmWait
    case ttsSpeaking
    case errorRecovery
}

struct WorkoutVoiceFab: View {
    let isEnabled: Bool
    let voiceStage: VoicePipelineStage
    let onToggle: () -> Void

    @State private var pulseScale: CGFloat = 1.0
    @State private var pulseAlpha: CGFloat = 1.0

    var body: some View {
        let isListening = voiceStage == .listening
        let isProcessing = voiceStage == .processing || voiceStage == .confirmWait || voiceStage == .ttsSpeaking

        let containerColor: Color = {
            if isListening { return .green }
            if isProcessing { return .purple }
            if voiceStage == .errorRecovery { return .orange }
            if isEnabled { return Color(.systemGray2) }
            return Color(.systemGray5)
        }()

        Button(action: onToggle) {
            Image(systemName: isEnabled ? "mic.fill" : "mic.slash.fill")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 40, height: 40)
                .background(containerColor)
                .clipShape(Circle())
        }
        .scaleEffect(isListening ? pulseScale : 1.0)
        .opacity(isListening ? pulseAlpha : 1.0)
        .onAppear {
            guard isListening else { return }
            withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
                pulseScale = 1.12
                pulseAlpha = 0.7
            }
        }
    }
}

struct WorkoutVoiceStatusBar: View {
    let voiceStage: VoicePipelineStage
    let voicePartialText: String
    let voiceErrorMessage: String?

    var body: some View {
        if voiceStage != .disabled {
            let status = statusInfo
            if !status.text.isEmpty {
                VStack(spacing: 0) {
                    HStack {
                        Text(status.text)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(status.color.opacity(0.92))

                    if let err = voiceErrorMessage, voiceStage == .errorRecovery {
                        HStack {
                            Text(err)
                                .font(.system(size: 11))
                                .foregroundColor(Color(red: 1, green: 0.8, blue: 0.84))
                            Spacer()
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 4)
                    }
                }
            }
        }
    }

    private var statusInfo: (text: String, color: Color) {
        switch voiceStage {
        case .listening:
            let partial = voicePartialText.trimmingCharacters(in: .whitespaces)
            return (partial.isEmpty ? "Escuchando comandos..." : "Escuchando: \"\(partial)\"", .green)
        case .processing: return ("Procesando...", Color(red: 0.47, green: 0.33, blue: 0.28))
        case .confirmWait: return ("\"Sí\" para confirmar, \"No\" para cancelar", Color(red: 0.49, green: 0.70, blue: 0.26))
        case .ttsSpeaking: return ("Hablando...", Color(red: 0.08, green: 0.40, blue: 0.75))
        case .errorRecovery: return ("Error, reintentando...", .orange)
        case .disabled: return ("", .clear)
        }
    }
}
