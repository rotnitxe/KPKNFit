import Foundation
import Combine

// ─── AugeViewModel — Minimal version for Phase 1.1 ───────────────────────────

final class AugeViewModel: ObservableObject {
    @Published var snapshot: AugeSnapshot = AugeSnapshot(
        batteries: GlobalBatteries(muscular: 100, cnc: 100, spinal: 100),
        perMuscle: [:],
        readiness: nil,
        dashboard: RecoveryDashboard(),
        isLoading: true
    )
    @Published var todayWellbeing: DailyWellbeingLog?
    @Published var pendingQuestionnaire: PendingQuestionnaire?

    static let shared = AugeViewModel()
    private init() {}

    func saveWellbeing(_ log: DailyWellbeingLog) {
        todayWellbeing = log
        let values = Array(log.manualMuscleBatteries.values)
        let derivedMuscular: Int? = values.isEmpty ? nil : values.reduce(0, +) / values.count
        snapshot = AugeSnapshot(
            batteries: GlobalBatteries(
                muscular: derivedMuscular ?? snapshot.batteries.muscular,
                cnc: log.manualNeuralBattery ?? snapshot.batteries.cnc,
                spinal: log.manualSpinalBattery ?? snapshot.batteries.spinal
            ),
            perMuscle: snapshot.perMuscle,
            readiness: snapshot.readiness,
            dashboard: snapshot.dashboard,
            isLoading: false
        )
    }

    func savePostSessionFeedback(_ fb: PostSessionFeedback) {
        pendingQuestionnaire = nil
    }

    func refresh() {
        // Placeholder for full recompute
    }
}
