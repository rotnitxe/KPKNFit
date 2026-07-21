import Foundation

public final class ProgramRepository {

    private let db = KpknDatabase.instance()

    private var _programs: [Program] = []
    private var _programQueue: [String] = []
    private var _isReady: Bool = false
    private var _activeProgramState: ActiveProgramState? = nil
    private var _history: [WorkoutLog] = []
    private var _ongoingWorkout: OngoingWorkoutState? = nil
    private var _settings: AppSettings = AppSettings()
    private var _contextPerformance: [String: ContextPerformanceStateV2] = [:]
    private var _globalPerformance: [String: GlobalPerformanceStateV3] = [:]
    private var _contextProfiles: [String: WorkoutContextProfile] = [:]
    private var _replacementDecisions: [ExerciseReplacementDecisionV2] = []

    private let programsLock = NSLock()
    private let queueLock = NSLock()
    private let activeLock = NSLock()
    private let historyLock = NSLock()
    private let ongoingLock = NSLock()
    private let settingsLock = NSLock()
    private let contextPerfLock = NSLock()
    private let globalPerfLock = NSLock()
    private let contextProfilesLock = NSLock()
    private let replacementLock = NSLock()
    private let ongoingWorkoutMutex = NSLock()

    private init() {
        loadFromDb()
    }

    public static let shared = ProgramRepository()

    public static func getInstance() -> ProgramRepository { shared }

    public static func initOnce() -> ProgramRepository { shared }

    public var programs: [Program] {
        programsLock.lock()
        defer { programsLock.unlock() }
        return _programs
    }

    public var programQueue: [String] {
        queueLock.lock()
        defer { queueLock.unlock() }
        return _programQueue
    }

    public var isReady: Bool {
        programsLock.lock()
        defer { programsLock.unlock() }
        return _isReady
    }

    public var activeProgramState: ActiveProgramState? {
        activeLock.lock()
        defer { activeLock.unlock() }
        return _activeProgramState
    }

    public var history: [WorkoutLog] {
        historyLock.lock()
        defer { historyLock.unlock() }
        return _history
    }

    public var ongoingWorkout: OngoingWorkoutState? {
        ongoingLock.lock()
        defer { ongoingLock.unlock() }
        return _ongoingWorkout
    }

    public var settings: AppSettings {
        settingsLock.lock()
        defer { settingsLock.unlock() }
        return _settings
    }

    public var contextPerformance: [String: ContextPerformanceStateV2] {
        contextPerfLock.lock()
        defer { contextPerfLock.unlock() }
        return _contextPerformance
    }

    public var globalPerformance: [String: GlobalPerformanceStateV3] {
        globalPerfLock.lock()
        defer { globalPerfLock.unlock() }
        return _globalPerformance
    }

    public var contextProfiles: [String: WorkoutContextProfile] {
        contextProfilesLock.lock()
        defer { contextProfilesLock.unlock() }
        return _contextProfiles
    }

    public var replacementDecisions: [ExerciseReplacementDecisionV2] {
        replacementLock.lock()
        defer { replacementLock.unlock() }
        return _replacementDecisions
    }

    public func getProgramById(_ id: String) -> Program? {
        programsLock.lock()
        defer { programsLock.unlock() }
        return _programs.first { $0.id == id }
    }

    public func getLogsForProgram(programId: String) -> [WorkoutLog] {
        historyLock.lock()
        defer { historyLock.unlock() }
        return _history.filter { $0.programId == programId }
    }

    public func getLogsForSession(sessionId: String) -> [WorkoutLog] {
        historyLock.lock()
        defer { historyLock.unlock() }
        return _history.filter { $0.sessionId == sessionId }
    }

    public func getContextProfilesForExercise(exerciseKey: String) -> [WorkoutContextProfile] {
        contextProfilesLock.lock()
        defer { contextProfilesLock.unlock() }
        return _contextProfiles.values.filter { $0.exerciseKey == exerciseKey }
            .sorted { ($0.lastUsedAtIso ?? "") > ($1.lastUsedAtIso ?? "") }
    }

    public func getContextPerformanceState(_ key: String) -> ContextPerformanceStateV2? {
        contextPerfLock.lock()
        defer { contextPerfLock.unlock() }
        return _contextPerformance[key]
    }

    public func getGlobalPerformanceState(_ key: String) -> GlobalPerformanceStateV3? {
        globalPerfLock.lock()
        defer { globalPerfLock.unlock() }
        return _globalPerformance[key]
    }

    public func addProgram(_ program: Program) {
        let normalized = program.normalizedIdentityFields()
        programsLock.lock()
        _programs.append(normalized)
        programsLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(normalized)
            let entity = ProgramEntity(id: normalized.id, name: normalized.name, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await db.programDao.upsert(entity: entity)
        }
    }

    public func updateProgram(_ program: Program) {
        let normalized = program.normalizedIdentityFields()
        programsLock.lock()
        if let idx = _programs.firstIndex(where: { $0.id == normalized.id }) {
            _programs[idx] = normalized
        }
        programsLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(normalized)
            let entity = ProgramEntity(id: normalized.id, name: normalized.name, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await db.programDao.upsert(entity: entity)
        }
    }

    public func updateProgramNow(_ program: Program) async {
        let normalized = program.normalizedIdentityFields()
        programsLock.lock()
        if let idx = _programs.firstIndex(where: { $0.id == normalized.id }) {
            _programs[idx] = normalized
        }
        programsLock.unlock()
        let data = try? JSONEncoder().encode(normalized)
        let entity = ProgramEntity(id: normalized.id, name: normalized.name, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
        try? await db.programDao.upsert(entity: entity)
    }

    public func upsertSessionInProgram(programId: String, weekId: String, macroIndex: Int, mesoIndex: Int, session: Session) -> Bool {
        guard let current = getProgramById(programId),
              let updated = current.upsertSessionInWeek(weekId: weekId, macroIndex: macroIndex, mesoIndex: mesoIndex, session: session) else {
            return false
        }
        updateProgram(updated)
        return true
    }

    public func upsertSessionInProgramNow(programId: String, weekId: String, macroIndex: Int, mesoIndex: Int, session: Session) async -> Bool {
        guard let current = getProgramById(programId),
              let updated = current.upsertSessionInWeek(weekId: weekId, macroIndex: macroIndex, mesoIndex: mesoIndex, session: session) else {
            return false
        }
        await updateProgramNow(updated)
        return true
    }

    public func deleteProgram(programId: String) {
        programsLock.lock()
        _programs = _programs.filter { $0.id != programId }
        programsLock.unlock()
        queueLock.lock()
        _programQueue = _programQueue.filter { $0 != programId }
        queueLock.unlock()
        Task {
            try? await db.programDao.delete(id: programId)
        }
    }

    public func addProgramToQueue(programId: String) {
        guard programsLock.withLock({ _programs.contains(where: { $0.id == programId }) }) else { return }
        queueLock.lock()
        if !_programQueue.contains(programId) {
            _programQueue.append(programId)
        }
        queueLock.unlock()
    }

    public func removeProgramFromQueue(programId: String) {
        queueLock.lock()
        _programQueue = _programQueue.filter { $0 != programId }
        queueLock.unlock()
    }

    public func moveQueuedProgram(programId: String, direction: Int) {
        queueLock.lock()
        guard let from = _programQueue.firstIndex(of: programId), from >= 0 else {
            queueLock.unlock()
            return
        }
        let to = max(0, min(from + direction, _programQueue.count - 1))
        if from == to {
            queueLock.unlock()
            return
        }
        var updated = _programQueue
        let item = updated.remove(at: from)
        updated.insert(item, at: to)
        _programQueue = updated
        queueLock.unlock()
    }

    public func clearPrograms() {
        programsLock.lock()
        _programs = []
        programsLock.unlock()
        Task {
            try? await db.programDao.deleteAll()
        }
    }

    public func startProgram(programId: String) {
        let program = programs.first { $0.id == programId }
        let resolved = program.flatMap { buildDefaultActiveProgramState($0, programId: programId) }
        let state = resolved ?? ActiveProgramState(programId: programId, status: .ACTIVE)
        activeLock.lock()
        _activeProgramState = state
        activeLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(state)
            let entity = ActiveProgramEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) })
            try? await db.stateDao.upsertActiveProgram(entity: entity)
        }
    }

    public func pauseProgram() {
        activeLock.lock()
        _activeProgramState = _activeProgramState?.copy(status: .PAUSED)
        let stateToSave = _activeProgramState
        activeLock.unlock()
        if let state = stateToSave {
            Task {
                let data = try? JSONEncoder().encode(state)
                let entity = ActiveProgramEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) })
                try? await db.stateDao.upsertActiveProgram(entity: entity)
            }
        }
    }

    public func resumeProgram() {
        activeLock.lock()
        _activeProgramState = _activeProgramState?.copy(status: .ACTIVE)
        let stateToSave = _activeProgramState
        activeLock.unlock()
        if let state = stateToSave {
            Task {
                let data = try? JSONEncoder().encode(state)
                let entity = ActiveProgramEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) })
                try? await db.stateDao.upsertActiveProgram(entity: entity)
            }
        }
    }

    public func advanceWeek(nextWeekId: String) {
        activeLock.lock()
        _activeProgramState = _activeProgramState?.copy(currentWeekId: nextWeekId)
        let stateToSave = _activeProgramState
        activeLock.unlock()
        if let state = stateToSave {
            Task {
                let data = try? JSONEncoder().encode(state)
                let entity = ActiveProgramEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) })
                try? await db.stateDao.upsertActiveProgram(entity: entity)
            }
        }
    }

    public func clearActiveProgram() {
        activeLock.lock()
        _activeProgramState = nil
        activeLock.unlock()
        Task {
            try? await db.stateDao.clearActiveProgram()
        }
    }

    public func addWorkoutLog(_ log: WorkoutLog) {
        let normalized = log.normalizedIdentityFields()
        historyLock.lock()
        _history.insert(normalized, at: 0)
        historyLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(normalized)
            let entity = WorkoutLogEntity(
                id: normalized.id,
                programId: normalized.programId,
                sessionId: normalized.sessionId,
                date: normalized.date,
                data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}"
            )
            try? await db.workoutLogDao.insert(entity: entity)
        }
    }

    public func startWorkout(_ state: OngoingWorkoutState) {
        let normalized = state.normalizedIdentityFields()
        ongoingLock.lock()
        _ongoingWorkout = normalized
        ongoingLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(normalized)
            let entity = OngoingWorkoutEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await db.stateDao.upsertOngoingWorkout(entity: entity)
        }
    }

    public func updateOngoingWorkout(update: (OngoingWorkoutState) -> OngoingWorkoutState) {
        ongoingLock.lock()
        guard let current = _ongoingWorkout else {
            ongoingLock.unlock()
            return
        }
        let updated = update(current).normalizedIdentityFields()
        _ongoingWorkout = updated
        ongoingLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(updated)
            let entity = OngoingWorkoutEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await db.stateDao.upsertOngoingWorkout(entity: entity)
        }
    }

    public func clearOngoingWorkout() {
        ongoingLock.lock()
        _ongoingWorkout = nil
        ongoingLock.unlock()
        Task {
            try? await db.stateDao.clearOngoingWorkout()
        }
    }

    public func flushPendingWrites() async {
        let currentWorkout = ongoingWorkout
        let currentPrograms = programs
        let currentActiveProgram = activeProgramState

        for program in currentPrograms {
            let data = try? JSONEncoder().encode(program.normalizedIdentityFields())
            let entity = ProgramEntity(id: program.id, name: program.name, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await db.programDao.upsert(entity: entity)
        }
        if let workout = currentWorkout {
            let data = try? JSONEncoder().encode(workout)
            let entity = OngoingWorkoutEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await db.stateDao.upsertOngoingWorkout(entity: entity)
        }
        if let active = currentActiveProgram {
            let data = try? JSONEncoder().encode(active)
            let entity = ActiveProgramEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await db.stateDao.upsertActiveProgram(entity: entity)
        }
    }

    public func updateSettings(update: (inout AppSettings) -> Void) {
        settingsLock.lock()
        var s = _settings
        update(&s)
        _settings = s
        settingsLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(self._settings)
            let entity = SettingsEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await self.db.settingsDao.upsert(entity: entity)
        }
    }

    public func upsertContextPerformanceState(_ state: ContextPerformanceStateV2) {
        contextPerfLock.lock()
        _contextPerformance[state.contextKey] = state
        contextPerfLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(state)
            let entity = WorkoutContextPerformanceEntity(contextKey: state.contextKey, updatedAt: state.lastUpdatedAtIso ?? "", data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await self.db.workoutV2Dao.upsertContextPerformance(entity: entity)
        }
    }

    public func upsertGlobalPerformanceState(_ state: GlobalPerformanceStateV3) {
        globalPerfLock.lock()
        _globalPerformance[state.globalKey] = state
        globalPerfLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(state)
            let entity = WorkoutGlobalPerformanceEntity(globalKey: state.globalKey, updatedAt: state.lastUpdatedAtIso ?? "", data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await self.db.workoutV2Dao.upsertGlobalPerformance(entity: entity)
        }
    }

    public func upsertContextProfile(_ profile: WorkoutContextProfile) {
        guard let id = profile.id else { return }
        contextProfilesLock.lock()
        _contextProfiles[id] = profile
        contextProfilesLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(profile)
            let entity = WorkoutContextProfileEntity(id: id, exerciseKey: profile.exerciseKey, lastUsedAt: profile.lastUsedAtIso ?? "", data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
            try? await self.db.workoutV2Dao.upsertContextProfile(entity: entity)
        }
    }

    public func getReplacementDecisions(programId: String) -> [ExerciseReplacementDecisionV2] {
        replacementLock.lock()
        defer { replacementLock.unlock() }
        return _replacementDecisions.filter { $0.programId == programId }
    }

    public func saveReplacementDecision(_ decision: ExerciseReplacementDecisionV2) {
        replacementLock.lock()
        _replacementDecisions = _replacementDecisions.filter { $0.id != decision.id } + [decision]
        replacementLock.unlock()
        Task {
            let data = try? JSONEncoder().encode(decision)
            let entity = WorkoutReplacementDecisionEntity(
                id: decision.id,
                programId: decision.programId,
                sessionId: decision.sessionId,
                createdAt: decision.createdAtIso,
                data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}"
            )
            try? await self.db.workoutV2Dao.upsertReplacementDecision(entity: entity)
        }
    }

    public func createAndSaveReplacementDecision(
        programId: String,
        sessionId: String,
        macroIndex: Int,
        mesoIndex: Int,
        weekId: String,
        sessionSlot: Int,
        exerciseSlot: Int,
        fromExerciseDbId: String,
        toExerciseDbId: String,
        scope: ReplacementPersistenceScopeV2
    ) -> ExerciseReplacementDecisionV2 {
        let decision = ExerciseReplacementDecisionV2(
            id: UUID().uuidString,
            programId: programId,
            sessionId: sessionId,
            macroIndex: macroIndex,
            mesoIndex: mesoIndex,
            weekId: weekId,
            sessionSlot: sessionSlot,
            exerciseSlot: exerciseSlot,
            fromExerciseDbId: fromExerciseDbId,
            toExerciseDbId: toExerciseDbId,
            scope: scope,
            createdAtIso: IsoDateFormatter.nowString()
        )
        saveReplacementDecision(decision)
        return decision
    }

    public func refreshData() {
        loadFromDb()
    }

    private func loadFromDb() {
        Task { [weak self] in
            guard let self = self else { return }
            do {
                let programEntities = try? await self.db.programDao.getAll()
                let programs = programEntities?.compactMap { entity -> Program? in
                    try? JSONDecoder().decode(Program.self, from: Data(entity.data.utf8))
                }.map { $0.normalizedIdentityFields() } ?? []

                let logEntities = try? await self.db.workoutLogDao.getAll()
                let logs = logEntities?.compactMap { entity -> WorkoutLog? in
                    try? JSONDecoder().decode(WorkoutLog.self, from: Data(entity.data.utf8))
                }.map { $0.normalizedIdentityFields() } ?? []

                let settingsEntity = try? await self.db.settingsDao.get()
                let settings = settingsEntity.flatMap { try? JSONDecoder().decode(AppSettings.self, from: Data($0.data.utf8)) } ?? AppSettings()

                let activeProgramEntity = try? await self.db.stateDao.getActiveProgram()
                let activeProgram = activeProgramEntity.flatMap { try? JSONDecoder().decode(ActiveProgramState.self, from: Data(($0.data ?? "{}").utf8)) }

                let ongoingEntity = try? await self.db.stateDao.getOngoingWorkout()
                let ongoingWorkout = ongoingEntity.flatMap { try? JSONDecoder().decode(OngoingWorkoutState.self, from: Data(($0.data ?? "{}").utf8)) }?.normalizedIdentityFields()

                let contextPerformanceEntities = try? await self.db.workoutV2Dao.getAllContextPerformance()
                let contextPerformance = contextPerformanceEntities?.compactMap { entity -> ContextPerformanceStateV2? in
                    try? JSONDecoder().decode(ContextPerformanceStateV2.self, from: Data(entity.data.utf8))
                }.associateBy { $0.contextKey } ?? [:]

                let globalPerformanceEntities = try? await self.db.workoutV2Dao.getAllGlobalPerformance()
                let globalPerformance = globalPerformanceEntities?.compactMap { entity -> GlobalPerformanceStateV3? in
                    try? JSONDecoder().decode(GlobalPerformanceStateV3.self, from: Data(entity.data.utf8))
                }.associateBy { $0.globalKey } ?? [:]

                let contextProfilesEntities = try? await self.db.workoutV2Dao.getAllContextProfiles()
                let contextProfiles = contextProfilesEntities?.compactMap { entity -> WorkoutContextProfile? in
                    try? JSONDecoder().decode(WorkoutContextProfile.self, from: Data(entity.data.utf8))
                }.associateBy { $0.id } ?? [:]

                let replacementEntities = try? await self.db.workoutV2Dao.getAllReplacementDecisions()
                let replacementDecisions = replacementEntities?.compactMap { entity -> ExerciseReplacementDecisionV2? in
                    try? JSONDecoder().decode(ExerciseReplacementDecisionV2.self, from: Data(entity.data.utf8))
                } ?? []

                let normalizedActive = self.normalizeActiveProgramState(programs: programs, state: activeProgram)

                if let normalizedActive = normalizedActive, normalizedActive != activeProgram {
                    let data = try? JSONEncoder().encode(normalizedActive)
                    let entity = ActiveProgramEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) })
                    try? await self.db.stateDao.upsertActiveProgram(entity: entity)
                }

                let currentOngoing = ongoingWorkout
                if let persisted = ongoingEntity.flatMap({ try? JSONDecoder().decode(OngoingWorkoutState.self, from: Data(($0.data ?? "{}").utf8)) }),
                   persisted != currentOngoing, let currentOngoing = currentOngoing {
                    let data = try? JSONEncoder().encode(currentOngoing)
                    let entity = OngoingWorkoutEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
                    try? await self.db.stateDao.upsertOngoingWorkout(entity: entity)
                }

                self.programsLock.lock()
                self._programs = programs
                self.programsLock.unlock()

                self.historyLock.lock()
                self._history = logs
                self.historyLock.unlock()

                self.settingsLock.lock()
                self._settings = settings
                self.settingsLock.unlock()

                self.activeLock.lock()
                self._activeProgramState = normalizedActive
                self.activeLock.unlock()

                self.ongoingLock.lock()
                self._ongoingWorkout = ongoingWorkout
                self.ongoingLock.unlock()

                self.contextPerfLock.lock()
                self._contextPerformance = contextPerformance
                self.contextPerfLock.unlock()

                self.globalPerfLock.lock()
                self._globalPerformance = globalPerformance
                self.globalPerfLock.unlock()

                self.contextProfilesLock.lock()
                self._contextProfiles = contextProfiles
                self.contextProfilesLock.unlock()

                self.replacementLock.lock()
                self._replacementDecisions = replacementDecisions
                self.replacementLock.unlock()

                self.programsLock.lock()
                self._isReady = true
                self.programsLock.unlock()
            } catch {
                self.programsLock.lock()
                self._isReady = true
                self.programsLock.unlock()
            }
        }
    }

    private func normalizeActiveProgramState(programs: [Program], state: ActiveProgramState?) -> ActiveProgramState? {
        guard let state = state else { return nil }
        guard let program = programs.first(where: { $0.id == state.programId }) else { return state }
        let locations = program.allWeekLocations()
        guard !locations.isEmpty else { return state }

        if ProgramCalendarEngine.isCalendarized(program) {
            let projection = ProgramCalendarEngine.project(program)
            let today = IsoDateFormatter.todayDate() ?? Date()
            let calendarWeek = projection.weekForDate(today)
            if let calendarWeek = calendarWeek,
               let resolved = locations.first(where: { $0.week.id == calendarWeek.weekId }) {
                if state.currentWeekId != resolved.week.id ||
                    state.currentMacrocycleIndex != resolved.macroIndex ||
                    state.currentBlockIndex != resolved.blockIndex ||
                    state.currentMesocycleIndex != resolved.mesoIndex {
                    return state.copy(
                        currentMacrocycleIndex: resolved.macroIndex,
                        currentBlockIndex: resolved.blockIndex,
                        currentMesocycleIndex: resolved.mesoIndex,
                        currentWeekId: resolved.week.id
                    )
                }
                return state
            }
        }

        if let exact = locations.first(where: { location in
            location.macroIndex == state.currentMacrocycleIndex &&
            location.blockIndex == state.currentBlockIndex &&
            location.mesoIndex == state.currentMesocycleIndex &&
            location.week.id == state.currentWeekId
        }) {
            return state
        }

        let sameContainer = locations.first(where: { location in
            location.macroIndex == state.currentMacrocycleIndex &&
            location.blockIndex == state.currentBlockIndex &&
            location.mesoIndex == state.currentMesocycleIndex
        }) ?? program.resolveDefaultWeekLocation() ?? locations.first!

        return state.copy(
            currentMacrocycleIndex: sameContainer.macroIndex,
            currentBlockIndex: sameContainer.blockIndex,
            currentMesocycleIndex: sameContainer.mesoIndex,
            currentWeekId: sameContainer.week.id
        )
    }

}

private extension NSLock {
    func withLock<T>(_ body: () -> T) -> T {
        lock()
        defer { unlock() }
        return body()
    }
}

private extension Dictionary where Value: Equatable {
    func associateBy<Key: Hashable>(_ transform: (Value) -> Key) -> [Key: Value] {
        var result: [Key: Value] = [:]
        for (_, value) in self {
            let key = transform(value)
            result[key] = value
        }
        return result
    }
}

private struct IsoDateFormatter {
    static func nowString() -> String {
        let formatter = ISO8601DateFormatter()
        return formatter.string(from: Date())
    }

    static func todayDate() -> Date? { Date() }
}

private struct ProgramWeekLocation: Equatable {
    let macroIndex: Int
    let blockIndex: Int
    let mesoIndex: Int
    let week: ProgramWeek

    static func == (lhs: ProgramWeekLocation, rhs: ProgramWeekLocation) -> Bool {
        return lhs.macroIndex == rhs.macroIndex &&
               lhs.blockIndex == rhs.blockIndex &&
               lhs.mesoIndex == rhs.mesoIndex &&
               lhs.week.id == rhs.week.id
    }
}

private extension Program {
    func allWeekLocations() -> [ProgramWeekLocation] {
        var locations: [ProgramWeekLocation] = []
        var mesoIndex = 0
        for (macroIndex, macro) in macrocycles.enumerated() {
            for (blockIndex, block) in macro.blocks.enumerated() {
                for meso in block.mesocycles {
                    for week in meso.weeks {
                        locations.append(ProgramWeekLocation(
                            macroIndex: macroIndex,
                            blockIndex: blockIndex,
                            mesoIndex: mesoIndex,
                            week: week
                        ))
                    }
                    mesoIndex += 1
                }
            }
        }
        return locations
    }

    func resolveDefaultWeekLocation(dayOfWeek: Int = currentDayOfWeek()) -> ProgramWeekLocation? {
        let locations = allWeekLocations()
        if locations.isEmpty { return nil }

        if ProgramCalendarEngine.isCalendarized(self) {
            let projection = ProgramCalendarEngine.project(self)
            let today = IsoDateFormatter.todayDate() ?? Date()
            let calendarWeek = projection.weekForDate(today)
            if let calendarWeek = calendarWeek,
               let resolved = locations.first(where: { $0.week.id == calendarWeek.weekId }) {
                return resolved
            }
        }

        return locations.first { location in
            location.week.sessions.contains { $0.matchesDay(dayOfWeek) }
        } ?? locations.first
    }
}

// Duplicate normalizedIdentityFields extensions removed (defined in ExerciseIdentity.swift)

private extension ActiveProgramState {
    func copy(
        programId: String? = nil,
        status: ProgramStatus? = nil,
        currentMacrocycleIndex: Int? = nil,
        currentBlockIndex: Int? = nil,
        currentMesocycleIndex: Int? = nil,
        currentWeekId: String? = nil
    ) -> ActiveProgramState {
        ActiveProgramState(
            programId: programId ?? self.programId,
            status: status ?? self.status,
            currentMacrocycleIndex: currentMacrocycleIndex ?? self.currentMacrocycleIndex,
            currentBlockIndex: currentBlockIndex ?? self.currentBlockIndex,
            currentMesocycleIndex: currentMesocycleIndex ?? self.currentMesocycleIndex,
            currentWeekId: currentWeekId ?? self.currentWeekId
        )
    }
}

private extension Session {
    func matchesDay(dayOfWeek: Int) -> Bool {
        self.dayOfWeek == dayOfWeek || assignedDays.contains(dayOfWeek)
    }
}

private func currentDayOfWeek() -> Int {
    let today = Calendar.current.component(.weekday, from: Date())
    return today == 1 ? 7 : today - 1
}

// ─── Program Extensions scoped to repo ────────────────────────────────────────

private extension Program {
    func upsertSessionInWeek(
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        session: Session
    ) -> Program? {
        var changed = false
        var globalMesoIndex = 0
        let updatedMacrocycles = macrocycles.enumerated().map { currentMacroIndex, macro in
            let updatedBlocks = macro.blocks.enumerated().map { _, block in
                let updatedMesocycles = block.mesocycles.enumerated().map { currentMesoIndex, meso in
                    if currentMacroIndex != macroIndex || currentMesoIndex != mesoIndex {
                        return Mesocycle(
                            id: meso.id,
                            name: meso.name,
                            goal: meso.goal,
                            customGoal: meso.customGoal,
                            weeks: meso.weeks
                        )
                    }
                    let updatedWeeks = meso.weeks.map { week in
                        if week.id != weekId {
                            return ProgramWeek(
                                id: week.id,
                                name: week.name,
                                description: week.description,
                                sessions: week.sessions,
                                variant: week.variant,
                                isLoopWeek: week.isLoopWeek,
                                loopId: week.loopId,
                                startDate: week.startDate,
                                endDate: week.endDate,
                                trainingDayDates: week.trainingDayDates
                            )
                        }
                        changed = true
                        let replaced = week.sessions.map { existing in
                            if existing.id == session.id { return session }
                            return existing
                        }
                        let nextSessions: [Session]
                        if replaced.contains(where: { $0.id == session.id }) {
                            nextSessions = replaced
                        } else {
                            nextSessions = replaced + [session]
                        }
                        return ProgramWeek(
                            id: week.id,
                            name: week.name,
                            description: week.description,
                            sessions: self.normalizeMainSessions(nextSessions),
                            variant: week.variant,
                            isLoopWeek: week.isLoopWeek,
                            loopId: week.loopId,
                            startDate: week.startDate,
                            endDate: week.endDate,
                            trainingDayDates: week.trainingDayDates
                        )
                    }
                    globalMesoIndex += 1
                    return Mesocycle(
                        id: meso.id,
                        name: meso.name,
                        goal: meso.goal,
                        customGoal: meso.customGoal,
                        weeks: updatedWeeks
                    )
                }
                return Block(
                    id: block.id,
                    name: block.name,
                    description: block.description,
                    mesocycles: updatedMesocycles
                )
            }
            return Macrocycle(
                id: macro.id,
                name: macro.name,
                blocks: updatedBlocks
            )
        }
        return changed ? Program(
            id: self.id,
            name: self.name,
            description: self.description,
            coverImage: self.coverImage,
            mode: self.mode,
            structure: self.structure,
            blockLabel: self.blockLabel,
            macrocycles: updatedMacrocycles,
            author: self.author,
            isPublic: self.isPublic,
            tags: self.tags,
            events: self.events,
            loops: self.loops,
            loopState: self.loopState,
            exerciseGoals: self.exerciseGoals,
            goals: self.goals,
            trainingPhase: self.trainingPhase,
            volumeSystem: self.volumeSystem,
            autoVolumeEnabled: self.autoVolumeEnabled,
            startDay: self.startDay,
            weekDays: self.weekDays,
            selectedSplitId: self.selectedSplitId,
            customSplitPattern: self.customSplitPattern,
            customSplitName: self.customSplitName,
            customSplitDescription: self.customSplitDescription,
            blockSplitSelections: self.blockSplitSelections,
            structureTemplateId: self.structureTemplateId,
            timelineStartDate: self.timelineStartDate,
            calendarization: self.calendarization,
            simpleProgramKind: self.simpleProgramKind,
            pausedCyclicSnapshot: self.pausedCyclicSnapshot,
            keyDates: self.keyDates,
            volumeRecommendations: self.volumeRecommendations,
            athleteProfileScore: self.athleteProfileScore,
            volumeAlertsEnabled: self.volumeAlertsEnabled,
            volumeSetupPromptSeen: self.volumeSetupPromptSeen,
            splitTrialSeen: self.splitTrialSeen,
            isDraft: self.isDraft
        ) : nil
    }

    func normalizeMainSessions(_ sessions: [Session]) -> [Session] {
        var seenIds = Set<String>()
        let distinct = sessions.filter { seenIds.insert($0.id).inserted }
        var mainByDay = [Int: String]()
        var fallbackByDay = [Int: String]()

        for session in distinct {
            let day = session.dayOfWeek ?? 1
            if fallbackByDay[day] == nil {
                fallbackByDay[day] = session.id
            }
            if session.isMainSession && mainByDay[day] == nil {
                mainByDay[day] = session.id
            }
        }

        for (day, sessionId) in fallbackByDay {
            if mainByDay[day] == nil {
                mainByDay[day] = sessionId
            }
        }

        return distinct.map { session in
            let day = session.dayOfWeek ?? 1
            return Session(
                id: session.id,
                name: session.name,
                description: session.description,
                exercises: session.exercises,
                warmup: session.warmup,
                parts: session.parts,
                background: session.background,
                coverStyle: session.coverStyle,
                dayOfWeek: session.dayOfWeek,
                scheduleLabel: session.scheduleLabel,
                assignedDays: session.assignedDays,
                sessionB: session.sessionB,
                sessionC: session.sessionC,
                sessionD: session.sessionD,
                isMeetDay: session.isMeetDay,
                isCompetitionSession: session.isCompetitionSession,
                isMainSession: mainByDay[day] == session.id,
                focus: session.focus,
                microProgram: session.microProgram,
                meetBodyweight: session.meetBodyweight,
                meetResults: session.meetResults,
                competitionDetails: session.competitionDetails,
                competitionRecordId: session.competitionRecordId,
                competitionKeyDateId: session.competitionKeyDateId,
                competitionSportType: session.competitionSportType,
                competitionRecordMode: session.competitionRecordMode,
                trainingBackup: session.trainingBackup,
                supersetGroups: session.supersetGroups,
                lastModifiedAtMs: session.lastModifiedAtMs,
                targetDurationMinutes: session.targetDurationMinutes,
                volumeAdvances: session.volumeAdvances
            )
        }
    }
}
