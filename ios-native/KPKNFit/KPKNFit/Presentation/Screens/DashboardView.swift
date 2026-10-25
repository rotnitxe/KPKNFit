import SwiftUI

/// The former simulated batteries are replaced by the existing recorded history.
struct DashboardView: View {
    var body: some View {
        ScrollView { RecordedTrainingHistoryView(logs: ProgramRepository.shared.history) }
    }
}
