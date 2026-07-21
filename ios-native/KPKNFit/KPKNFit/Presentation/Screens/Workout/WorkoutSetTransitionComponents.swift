import SwiftUI

struct WorkoutStageTransitionTarget {
    let exerciseId: String
    let order: Int
    let label: String
}

func isWorkoutPulseActive(_ pulseToken: Int64?, _ nowMs: Int64) -> Bool {
    guard let token = pulseToken, token > 0 else { return false }
    return (nowMs - token) < 2000
}

struct WorkoutSetTransitionBanner: View {
    let transitionTarget: WorkoutStageTransitionTarget?
    let pulseToken: Int64?
    @State private var nowMs: Int64 = Date().timeIntervalSince1970Milliseconds
    @State private var visible: Bool = true
    @State private var scale: CGFloat = 1.0

    var body: some View {
        let isRecent = isWorkoutPulseActive(pulseToken, nowMs)

        Group {
            if let target = transitionTarget, visible {
                let accent: Color = isRecent ? Color(red: 1, green: 0.84, blue: 0.31) : Color.blue

                HStack(spacing: 10) {
                    Image(systemName: isRecent ? "chart.line.uptrend.xyaxis" : "sparkle")
                        .foregroundColor(accent)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(target.label)
                            .font(.system(size: 14, weight: .bold))
                            .lineLimit(2)
                        Text(isRecent
                             ? "Carga futura recalculada y lista para registrar."
                             : "Cambio de serie dentro del ejercicio actual.")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Text(isRecent ? "Autoajuste" : "Flujo activo")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(accent)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(accent.opacity(0.14))
                        .clipShape(Capsule())
                }
                .padding()
                .background(accent.opacity(0.14))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(accent.opacity(0.4), lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .scaleEffect(scale)
                .onAppear {
                    visible = true
                    nowMs = Date().timeIntervalSince1970Milliseconds
                    scheduleDismiss()
                }
            }
        }
    }

    private func scheduleDismiss() {
        Timer.scheduledTimer(withTimeInterval: 1.8, repeats: false) { _ in
            withAnimation { visible = false }
        }
    }
}

fileprivate extension Date {
    var timeIntervalSince1970Milliseconds: Int64 {
        Int64(self.timeIntervalSince1970 * 1000)
    }
}
