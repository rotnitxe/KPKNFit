import Foundation

internal final class ProgramRepository {
    static let shared = ProgramRepository()

    var programs: [Program] = []
    var programQueue: [String] = []
    var isReady: Bool = false
    var activeProgramState: ActiveProgramState?

    var ongoingWorkout: OngoingWorkoutState?
    var history: [WorkoutLog] = []
    var contextProfiles: [String: WorkoutContextProfile] = [:]
    var contextPerformance: [String: ContextPerformanceStateV2] = [:]
    var globalPerformance: [String: GlobalPerformanceStateV3] = [:]
    var settings: AppSettings = AppSettings()

    let performanceRangeDao = PerformanceRangeDao()

    func getProgramById(_ id: String) -> Program? {
        programs.first { $0.id == id }
    }

    func getLogsForSession(_ sessionId: String) -> [WorkoutLog] {
        history.filter { $0.id == sessionId }
    }

    func getContextProfilesForExercise(_ exerciseKey: String) -> [WorkoutContextProfile] {
        []
    }

    func getContextPerformanceState(_ key: String) -> ContextPerformanceStateV2? {
        contextPerformance[key]
    }

    func getGlobalPerformanceState(_ key: String) -> GlobalPerformanceStateV3? {
        globalPerformance[key]
    }

    func upsertContextPerformanceState(_ state: ContextPerformanceStateV2) {
        contextPerformance[state.contextKey] = state
    }

    func upsertGlobalPerformanceState(_ state: GlobalPerformanceStateV3) {
        globalPerformance[state.globalKey] = state
    }

    func upsertContextProfile(_ profile: WorkoutContextProfile) {
        if let id = profile.id {
            contextProfiles[id] = profile
        }
    }

    func startWorkout(_ state: OngoingWorkoutState) {
        ongoingWorkout = state
    }

    func updateOngoingWorkout(_ mutate: (inout OngoingWorkoutState?) -> Void) {
        mutate(&ongoingWorkout)
    }

    func clearOngoingWorkout() {
        ongoingWorkout = nil
    }

    func addWorkoutLog(_ log: WorkoutLog) {
        history.insert(log, at: 0)
    }

    func updateProgram(_ program: Program) {
        if let idx = programs.firstIndex(where: { $0.id == program.id }) {
            programs[idx] = program
        }
    }

    func updateSettings(_ mutate: (inout AppSettings) -> Void) {
        mutate(&settings)
    }
}

internal final class PerformanceRangeDao {
    func getByContextKey(_ key: String) async -> PerformanceRangeData? {
        nil
    }
}
