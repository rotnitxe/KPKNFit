import Foundation
import Combine

// ─── ProgramStore — Minimal store for program data ────────────────────────────

final class ProgramStore: ObservableObject {
    @Published var programs: [Program] = []
    @Published var settings: AppSettings = AppSettings()

    static let shared = ProgramStore()
    private init() {}
}
