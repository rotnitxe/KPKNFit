import Foundation
import Combine
import SwiftUI

@MainActor final class WorkoutViewModel: ObservableObject {
    private let programId: String
    private let sessionId: String
    private var repository: ProgramRepository { ProgramRepository.shared }
    private var deferredOnComplete: (() -> Void)?
    private var sessionTimerTask: Task<Void, Never>?
    private var timerTask: Task<Void, Never>?
    private var voiceTask: Task<Void, Never>?
    private let recordingGate = WorkoutRecordingGate()
    private var evaluatedContextKeysThisSession = Set<String>()
    private var deferredReplacementPrompt: PendingReplacementPersistencePrompt?
    private var restReferenceSet: CompletedSet?
    private var restReferenceAdvanced: SetAdvancedFeedback?
    private var restStartedAtMs: Int64?
    private var activeRestTimerId: String?
    private var performanceRangeCache: [String: PerformanceRangeData] = [:]
    private var performanceRangePrefetchInFlight = Set<String>()

    @Published var uiState = WorkoutUiState()
    @Published var allUserTags: [String] = []
    @Published var restTimerRemaining: Int = 0
    @Published var restRecovery: RestRecoveryStatus?
    @Published var currentAutoRegulation: SetAutoRegulation?
    @Published var currentCoachMessage: CoachMessage?

    var lastLog: WorkoutLog? { repository.getLogsForSession(sessionId).first }
    var exerciseIndex: [String: ExerciseMuscleInfo] { EXERCISE_DATABASE_BY_ID }

    init(programId: String, sessionId: String) {
        self.programId = programId
        self.sessionId = sessionId
        uiState = WorkoutUiState(programId: programId)
        loadSession()
        refreshAllUserTags()
    }

    deinit {
        sessionTimerTask?.cancel()
        timerTask?.cancel()
        voiceTask?.cancel()
        ProgramRepository.shared.clearOngoingWorkout()
    }

    private func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    private func nowIso() -> String {
        ISO8601DateFormatter().string(from: Date())
    }

    private func updateState(_ transform: (inout WorkoutUiState) -> Void) {
        transform(&uiState)
    }

    // MARK: - loadSession

    private func loadSession() {
        guard let program = repository.getProgramById(programId) else { return }
        var foundSession: Session?
        var foundWeekId = ""
        var foundMacroIdx = 0
        var foundMesoIdx = 0

        for (macroIdx, macro) in program.macrocycles.enumerated() {
            var mesoOffset = 0
            for block in macro.blocks {
                for (mesoIdx, meso) in block.mesocycles.enumerated() {
                    let flattenedMesoIdx = mesoOffset + mesoIdx
                    for week in meso.weeks {
                        if let s = week.sessions.first(where: { $0.id == sessionId }) {
                            foundSession = s
                            foundWeekId = week.id
                            foundMacroIdx = macroIdx
                            foundMesoIdx = flattenedMesoIdx
                            break
                        }
                    }
                }
                mesoOffset += block.mesocycles.count
            }
        }

        guard let session = foundSession else { return }

        let resumedState = repository.ongoingWorkout
            .flatMap { $0.programId == programId && $0.session.id == sessionId ? $0 : nil }

        let restoredSession = (resumedState?.session ?? session).normalizeSupersetsForWorkout().sanitizeSessionLoadModes()
        let restoredMode = resumedState?.activeMode ?? .A
        let restoredCompletedSets = resumedState?.completedSets ?? [:]
        let restoredSkippedExerciseIds = resumedState?.skippedExerciseIds ?? []
        let restoredWarmupCompletedExerciseIds = resumedState?.warmupCompletedExerciseIds ?? []
        let restoredMobilityCompletedExerciseIds = resumedState?.mobilityCompletedExerciseIds ?? []
        let exercisesForMode = sessionForActiveMode(restoredSession, restoredMode).allExercises()
        let hydratedProfiles = hydrateContextProfiles(exercises: exercisesForMode, resumedState: resumedState)
        var restoredActiveProfiles = hydratedProfiles.1
        var restoredTags = (resumedState?.exerciseTags ?? [:])
        for (exerciseId, profileId) in hydratedProfiles.1 {
            guard let profileTag = hydratedProfiles.0[profileId]?.tagId else { continue }
            restoredTags[exerciseId] = profileTag
        }

        var restoredActiveTags: [String: [String]] = [:]
        if let resumedState = resumedState {
            restoredActiveTags = resumedState.activeTags
        } else {
            for (exId, tagName) in restoredTags {
                restoredActiveTags[exId] = [tagName]
            }
        }
        let restoredActiveSubTags = resumedState?.activeSubTags ?? [:]
        let restoredUserCreatedTags = resumedState?.userCreatedTags ?? [:]

        if resumedState == nil {
            let historicalLogs = repository.history
            for exercise in exercisesForMode {
                let exerciseDbId = canonicalExerciseKey(exercise)
                let recurrence = WorkoutContextRecurrenceEngine.detectDayRecurrence(exerciseDbId: exerciseDbId, dayOfWeek: Calendar.current.component(.weekday, from: Date()), logs: historicalLogs)
                if recurrence.confidence >= 2 {
                    if let tagId = recurrence.tagId {
                        restoredTags[exercise.id] = tagId
                    }
                    if let profileId = recurrence.profileId, hydratedProfiles.0[profileId] != nil {
                        restoredActiveProfiles[exercise.id] = profileId
                    }
                }
            }
        }

        let restoredExerciseIdx: Int
        let restoredSetIdx: Int
        if let resumedState = resumedState {
            let directIdx = resumedState.activeExerciseIndex
            if directIdx >= 0, directIdx < exercisesForMode.count {
                restoredExerciseIdx = directIdx
                restoredSetIdx = min(resumedState.activeSetIndex, max(0, exercisesForMode[directIdx].sets.count - 1))
            } else {
                let pos = resolveResumePosition(exercises: exercisesForMode, completedSets: restoredCompletedSets, preferredExerciseId: resumedState.activeExerciseId, preferredSetId: resumedState.activeSetId)
                restoredExerciseIdx = pos.0
                restoredSetIdx = pos.1
            }
        } else {
            restoredExerciseIdx = 0
            restoredSetIdx = 0
        }

        let restoredStartTime = resumedState?.startTime ?? nowMs()
        let settings = repository.settings
        let featureFlags = settings.workoutFeatureFlags
        let headerWidgets = settings.workoutV2HeaderWidgetsBySession[workoutWidgetsSessionKey()] ?? WorkoutHeaderWidgets()

        updateState {
            $0.session = restoredSession.normalizedIdentityFields().normalizeSupersetsForWorkout()
            $0.activeMode = restoredMode
            $0.weekId = foundWeekId
            $0.macroIndex = foundMacroIdx
            $0.mesoIndex = foundMesoIdx
            $0.currentExerciseIdx = restoredExerciseIdx
            $0.currentSetIdx = restoredSetIdx
            $0.activeStepKey = resumedState?.activeStepKey
            $0.completedSets = restoredCompletedSets
            $0.skippedExerciseIds = restoredSkippedExerciseIds
            $0.warmupCompletedExerciseIds = restoredWarmupCompletedExerciseIds
            $0.mobilityCompletedExerciseIds = restoredMobilityCompletedExerciseIds
            $0.exerciseTags = restoredTags
            $0.activeTagsByExercise = restoredActiveTags
            $0.activeSubTagsByExercise = restoredActiveSubTags
            $0.userCreatedTags = restoredUserCreatedTags
            $0.startTimeMs = restoredStartTime
            $0.featureFlags = featureFlags
            $0.contextualPerformanceCache = repository.contextPerformance
            $0.globalPerformanceCache = repository.globalPerformance
            $0.contextProfilesV3 = hydratedProfiles.0
            $0.activeContextProfileByExerciseId = restoredActiveProfiles
            $0.headerWidgets = headerWidgets
            $0.readinessNeuralOverride = resumedState?.readinessNeuralOverride
            $0.readinessMuscularOverride = resumedState?.readinessMuscularOverride
            $0.readinessSpinalOverride = resumedState?.readinessSpinalOverride
            $0.readinessMuscleOverrides = resumedState?.readinessMuscleOverrides ?? [:]
            $0.setDrafts = resumedState?.setDrafts ?? [:]
            $0.manualLoadOverrides = resumedState?.manualLoadOverrides ?? [:]
            $0.persistedLoadModeBySet = resumedState?.persistedLoadModeBySet ?? [:]
            $0.customTargetDurationMinutes = resumedState?.customTargetDurationMinutes
            $0.targetDurationMinutes = resumedState?.customTargetDurationMinutes ?? restoredSession.targetDurationMinutes
        }

        // Restore rest timer state if still active
        let currentTimeMs = nowMs()
        if let restoredRestState = resumedState?.restModalState, restoredRestState.endsAtMs > UInt64(currentTimeMs) {
            let restoredSeconds = max(1, Int((Int64(restoredRestState.endsAtMs) - currentTimeMs) / 1000))
            let patchedRestState = restoredRestState
            updateState {
                $0.restTimerTotal = patchedRestState.activeSeconds
                $0.isRestTimerRunning = true
                $0.restModalState = patchedRestState
            }
            startRestTimer(seconds: restoredSeconds, preserveElapsed: true)
        }

        // Initialize persistedLoadModeByExercise from hydrated profiles if not yet set
        if resumedState?.persistedLoadModeByExercise?.isEmpty ?? true {
            for (_, profile) in hydratedProfiles.0 {
                guard let lm = profile.loadMode else { continue }
                let exKey = workoutExerciseContextKey(exerciseId: profile.exerciseKey, tagId: profile.tagId)
                updateState { $0.persistedLoadModeByExercise[exKey] = lm }
            }
        }

        // Calculate mesocycle stress EMA
        let ema = AugeFatigueEngine.calculateMesocycleStressEMA(
            logs: repository.history,
            programId: programId,
            mesoIndex: uiState.mesoIndex
        )
        updateState { $0.mesocycleStressEMA = ema }

        updateCoachMessage(setDrain: SetDrain(cnsDrainPct: 0, muscularDrainPct: 0, spinalDrainPct: 0), sessionProgress: 0)

        refreshLoadSuggestions()
        if uiState.activeStepKey == nil || uiState.activeStepKey!.isEmpty {
            updateState { $0.activeStepKey = nextIncompleteStepAfter($0, includeCurrent: true)?.stepKey }
        }

        if resumedState == nil {
            // Auto-migrate legacy context profiles to user-created tags
            for exercise in exercisesForMode {
                let exKey = canonicalExerciseKey(exercise)
                if restoredUserCreatedTags[exKey] == nil || restoredUserCreatedTags[exKey]!.isEmpty {
                    let migrated = migrateContextProfilesToTags(hydratedProfiles.0, exerciseKey: exKey)
                    if !migrated.isEmpty {
                        restoredUserCreatedTags[exKey] = migrated
                    }
                }
            }

            let initialExercise = exercisesForMode.first
            repository.startWorkout(OngoingWorkoutState(
                programId: programId,
                session: restoredSession.normalizedIdentityFields(),
                isPaused: false,
                startTime: restoredStartTime,
                activeExerciseId: initialExercise?.id,
                activeSetId: initialExercise?.sets.first?.id,
                activeSetIndex: 0,
                activeExerciseIndex: 0,
                activeStepKey: uiState.activeStepKey,
                activeMode: restoredMode,
                completedSets: [:],
                dynamicWeights: uiState.loadSuggestions.mapValues { $0.suggestedWeight },
                loadSuggestionReasons: uiState.loadSuggestions.mapValues { $0.reason },
                setDrafts: uiState.setDrafts,
                manualLoadOverrides: uiState.manualLoadOverrides,
                editingSetKey: uiState.editingState?.setKey,
                isCarpeDiem: false,
                macroIndex: foundMacroIdx,
                mesoIndex: foundMesoIdx,
                weekId: foundWeekId,
                exerciseTags: restoredTags,
                activeTags: restoredActiveTags,
                activeSubTags: restoredActiveSubTags,
                userCreatedTags: restoredUserCreatedTags,
                contextProfilesV3: hydratedProfiles.0,
                activeContextProfileByExerciseId: restoredActiveProfiles,
                skippedExerciseIds: restoredSkippedExerciseIds,
                warmupCompletedExerciseIds: restoredWarmupCompletedExerciseIds,
                mobilityCompletedExerciseIds: restoredMobilityCompletedExerciseIds,
                readinessNeuralOverride: resumedState?.readinessNeuralOverride,
                readinessMuscularOverride: resumedState?.readinessMuscularOverride,
                readinessSpinalOverride: resumedState?.readinessSpinalOverride,
                readinessMuscleOverrides: resumedState?.readinessMuscleOverrides ?? [:],
                restModalState: uiState.restModalState,
                persistedLoadModeBySet: [:],
                persistedLoadModeByExercise: [:]
            ))
        }

        updateCoachMessage(setDrain: SetDrain(cnsDrainPct: 0, muscularDrainPct: 0, spinalDrainPct: 0), sessionProgress: 0)

        let lastLogEntry = repository.history.first { $0.programId == programId && $0.id != sessionId }
        let lastDiscomforts = lastLogEntry?.postExerciseReports.flatMap { $0.discomfortIds }.filter { $0 != "none" } ?? []
        if !lastDiscomforts.isEmpty {
            updateState { $0.previousSessionDiscomforts = lastDiscomforts }
        }

        if let targetMinutes = uiState.customTargetDurationMinutes ?? restoredSession.targetDurationMinutes, targetMinutes > 0 {
            let elapsedSeconds = max(0, (nowMs() - restoredStartTime) / 1000)
            let remainingSeconds = targetMinutes * 60 - Int(elapsedSeconds)
            startSessionTimer(totalSeconds: remainingSeconds)
        }
    }

    // MARK: - Session Helpers

    private func workoutWidgetsSessionKey() -> String { "\(programId)::\(sessionId)" }

    private func sessionForActiveMode(_ base: Session, _ mode: WeekVariant) -> Session {
        switch mode {
        case .A: return base
        case .B: return base.sessionB ?? base
        case .C: return base.sessionC ?? base
        case .D: return base.sessionD ?? base
        }
    }

    func canonicalExerciseKey(_ exercise: Exercise) -> String { exercise.resolvedCanonicalExerciseId() }

    private func catalogInfoForExercise(_ exercise: Exercise) -> ExerciseMuscleInfo? {
        let canonicalId = canonicalExerciseKey(exercise)
        if let info = EXERCISE_DATABASE_BY_ID[canonicalId] { return info }
        if let dbId = exercise.exerciseDbId?.lowercased(), let info = EXERCISE_DATABASE_BY_ID[dbId] { return info }
        if let exId = exercise.exerciseId?.lowercased(), let info = EXERCISE_DATABASE_BY_ID[exId] { return info }
        return nil
    }

    private func catalogInfoForCompletedExercise(_ exercise: CompletedExercise) -> ExerciseMuscleInfo? {
        let canonicalId = exercise.resolvedCanonicalExerciseId()
        if let info = EXERCISE_DATABASE_BY_ID[canonicalId] { return info }
        if let dbId = exercise.exerciseDbId?.lowercased(), let info = EXERCISE_DATABASE_BY_ID[dbId] { return info }
        let exId = exercise.exerciseId.lowercased()
        if let info = EXERCISE_DATABASE_BY_ID[exId] { return info }
        return nil
    }

    private func visibleExercises(_ state: WorkoutUiState) -> [Exercise] {
        guard let base = state.session else { return [] }
        let byMode = sessionForActiveMode(base, state.activeMode).allExercises()
        if state.skippedExerciseIds.isEmpty { return byMode }
        return byMode.filter { !state.skippedExerciseIds.contains($0.id) }
    }

    func isSetDone(completedSets: [String: CompletedSet], exerciseId: String, setIdx: Int, isUnilateral: Bool) -> Bool {
        let key = "\(exerciseId)_\(setIdx)"
        if completedSets[key] != nil { return true }
        if isUnilateral {
            return completedSets["\(key)_L"] != nil && completedSets["\(key)_R"] != nil
        }
        return false
    }

    private func buildCompletedSetKey(_ exerciseId: String, _ setIdx: Int, _ side: String?) -> String {
        switch side {
        case "left": return "\(exerciseId)_\(setIdx)_L"
        case "right": return "\(exerciseId)_\(setIdx)_R"
        default: return "\(exerciseId)_\(setIdx)"
        }
    }

    private func counterpartSide(_ side: String) -> String { side == "left" ? "right" : "left" }

    private func withModeSession(_ base: Session, _ mode: WeekVariant, _ update: (Session) -> Session) -> Session {
        switch mode {
        case .A: return update(base)
        case .B: return base.copy(sessionB: update(base.sessionB ?? base))
        case .C: return base.copy(sessionC: update(base.sessionC ?? base))
        case .D: return base.copy(sessionD: update(base.sessionD ?? base))
        }
    }

    // MARK: - Body Weight

    func currentBodyWeight() -> Double? { repository.settings.userVitals.weight }

    func setCurrentBodyWeight(_ weight: Double) {
        repository.updateSettings { $0.copy(userVitals: $0.userVitals.copy(weight: weight)) }
    }

    // MARK: - Sanitize



    // MARK: - Context Profiles

    private func inferDefaultLoadModeFromCatalog(_ exercise: Exercise) -> LoadModeV2 {
        guard let info = catalogInfoForExercise(exercise) else { return .LOAD }
        let equipment = info.equipment?.lowercased() ?? ""
        let name = exercise.name.lowercased()
        if equipment.contains("peso corporal") || equipment.contains("bodyweight") || equipment.contains("calistenia") { return .BODYWEIGHT }
        if equipment.contains("asist") || name.contains("asist") || equipment.contains("assisted") || name.contains("assisted") { return .ASSISTED }
        return .LOAD
    }

    private func sanitizeSessionLoadModes(_ session: Session) -> Session {
        let transform: (Exercise) -> Exercise = { exercise in
            let defaultMode = self.inferDefaultLoadModeFromCatalog(exercise)
            return exercise.copy(sets: exercise.sets.map { set in
                set.loadModeV2 != nil ? set : set.copy(loadModeV2: defaultMode)
            })
        }
        return session.copy(
            exercises: session.exercises.map(transform),
            parts: session.parts.map { $0.copy(exercises: $0.exercises.map(transform)) },
            sessionB: session.sessionB?.sanitizeSessionLoadModes(),
            sessionC: session.sessionC?.sanitizeSessionLoadModes(),
            sessionD: session.sessionD?.sanitizeSessionLoadModes()
        )
    }

    private func hydrateContextProfiles(exercises: [Exercise], resumedState: OngoingWorkoutState?) -> ([String: WorkoutContextProfile], [String: String]) {
        var mergedProfiles = repository.contextProfiles
        var activeProfiles = resumedState?.activeContextProfileByExerciseId ?? [:]

        for exercise in exercises {
            let exerciseKey = canonicalExerciseKey(exercise)
            let candidatesList = exercise.contextProfilesV3
                + repository.getContextProfilesForExercise(exerciseKey)
                + (resumedState?.contextProfilesV3.values.filter { $0.exerciseKey == exerciseKey } ?? [])
            
            var uniqueCandidates: [WorkoutContextProfile] = []
            var seenIds = Set<String>()
            for candidate in candidatesList {
                if let id = candidate.id {
                    if !seenIds.contains(id) {
                        seenIds.insert(id)
                        uniqueCandidates.append(candidate)
                    }
                }
            }
            var candidates = uniqueCandidates
            if candidates.isEmpty { candidates = [defaultContextProfileForExercise(exercise)] }

            for profile in candidates {
                if let id = profile.id {
                    mergedProfiles[id] = profile
                }
                repository.upsertContextProfile(profile)
            }

            let preferredId = resumedState?.activeContextProfileByExerciseId[exercise.id]
                ?? exercise.defaultContextProfileIdV3
                ?? candidates.first?.id
            let resolvedId = candidates.first(where: { $0.id == preferredId })?.id ?? candidates[0].id
            if let rId = resolvedId {
                activeProfiles[exercise.id] = rId
            }
        }
        return (mergedProfiles, activeProfiles)
    }

    private func defaultContextProfileForExercise(_ exercise: Exercise) -> WorkoutContextProfile {
        let exerciseKey = canonicalExerciseKey(exercise)
        return WorkoutContextProfile(
            id: "\(exerciseKey)|default",
            exerciseKey: exerciseKey,
            tagId: exercise.sets.first(where: { $0.defaultTagIdV3 != nil })?.defaultTagIdV3
                ?? exercise.sets.first(where: { $0.tagId != nil })?.tagId
                ?? exercise.variantName,
            setupProfileId: exercise.sets.first(where: { $0.defaultSetupProfileIdV3 != nil })?.defaultSetupProfileIdV3
                ?? exercise.sets.first(where: { $0.setupId != nil })?.setupId,
            setupLabel: exercise.setupDetails?.seatPosition,
            machineBrand: exercise.sets.first(where: { $0.machineBrand != nil })?.machineBrand,
            setupDetails: exercise.setupDetails,
            createdAtIso: nowIso(),
            lastUsedAtIso: nowIso(),
            usageCount: 1
        )
    }

    func profilesForExercise(_ exercise: Exercise) -> [WorkoutContextProfile] {
        let key = canonicalExerciseKey(exercise)
        return uiState.contextProfilesV3.values
            .filter { $0.exerciseKey == key }
            .sorted { ($0.lastUsedAtIso ?? "") > ($1.lastUsedAtIso ?? "") }
    }

    func dominantMuscleGroupFor(_ exercise: Exercise) -> String? {
        guard let info = catalogInfoForExercise(exercise) else { return nil }
        let dominant = info.involvedMuscles
            .filter { resolveMuscleVolumeContribution($0, capAtOne: false) > 0.0 }
            .max { a, b in
                let aScore = resolveMuscleVolumeContribution(a, capAtOne: false) + {
                    switch a.role {
                    case .PRIMARY: return 1.0
                    case .SECONDARY: return 0.45
                    case .STABILIZER: return 0.20
                    case .NEUTRALIZER: return 0.10
                    }
                }()
                let bScore = resolveMuscleVolumeContribution(b, capAtOne: false) + {
                    switch b.role {
                    case .PRIMARY: return 1.0
                    case .SECONDARY: return 0.45
                    case .STABILIZER: return 0.20
                    case .NEUTRALIZER: return 0.10
                    }
                }()
                return aScore < bScore
            } ?? info.involvedMuscles.first
        guard let d = dominant else { return nil }
        return VolumeCalculator.normalizeCanonicalMuscleGroup(d.muscle, emphasis: d.emphasis)
    }

    func activeContextProfile(_ exerciseId: String) -> WorkoutContextProfile? {
        guard let profileId = uiState.activeContextProfileByExerciseId[exerciseId] else { return nil }
        return uiState.contextProfilesV3[profileId]
    }

    func setActiveContextProfile(_ exerciseId: String, profileId: String) {
        guard let profile = uiState.contextProfilesV3[profileId] else { return }
        updateState {
            if let tag = profile.tagId {
                let existingTags = self.tagsForExercise(exerciseId)
                let match = existingTags.first(where: { $0.name == tag })
                if let match = match {
                    $0.activeTagsByExercise[exerciseId] = [match.id]
                }
                $0.exerciseTags[exerciseId] = tag
            }
            $0.activeContextProfileByExerciseId[exerciseId] = profileId
        }
        persistOngoingState()
    }

    func upsertContextProfile(exercise: Exercise, profile: WorkoutContextProfile, makeActive: Bool = true) {
        let updated = profile.copy(
            exerciseKey: canonicalExerciseKey(exercise),
            lastUsedAtIso: nowIso(),
            usageCount: profile.usageCount + 1
        )
        repository.upsertContextProfile(updated)
        updateState {
            if let id = updated.id {
                $0.contextProfilesV3[id] = updated
                if makeActive {
                    $0.activeContextProfileByExerciseId[exercise.id] = id
                }
            }
        }
        persistOngoingState()
    }

    // MARK: - Tag CRUD

    func createTag(_ exerciseId: String, name: String) -> WorkoutTag {
        let exercise = visibleExercises(uiState).first(where: { $0.id == exerciseId }) ?? Exercise(id: "", name: "")
        let exKey = canonicalExerciseKey(exercise)
        let tag = WorkoutTag(id: UUID().uuidString, name: name.trimmingCharacters(in: .whitespacesAndNewlines), exerciseKey: exKey, createdAtIso: nowIso(), lastUsedAtIso: nowIso(), usageCount: 0)
        updateState { $0.userCreatedTags[exKey, default: []].append(tag) }
        persistOngoingState()
        toggleMainTagActive(exerciseId, tagId: tag.id)
        return tag
    }

    func deleteTag(_ exerciseId: String, tagId: String) {
        let exercise = visibleExercises(uiState).first(where: { $0.id == exerciseId }) ?? Exercise(id: "", name: "")
        let exKey = canonicalExerciseKey(exercise)
        let tagName = uiState.userCreatedTags[exKey]?.first(where: { $0.id == tagId })?.name
        updateState {
            $0.userCreatedTags[exKey] = ($0.userCreatedTags[exKey] ?? []).filter { $0.id != tagId }
            for (exId, tagIds) in $0.activeTagsByExercise {
                if exId == exerciseId {
                    $0.activeTagsByExercise[exId] = tagIds.filter { $0 != tagId }
                }
            }
            if let t = tagName, $0.exerciseTags[exerciseId] == t {
                $0.exerciseTags.removeValue(forKey: exerciseId)
            }
        }
        persistOngoingState()
    }

    func renameTag(_ exerciseId: String, tagId: String, newName: String) {
        let exercise = visibleExercises(uiState).first(where: { $0.id == exerciseId }) ?? Exercise(id: "", name: "")
        let exKey = canonicalExerciseKey(exercise)
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let oldTag = uiState.userCreatedTags[exKey]?.first(where: { $0.id == tagId }) else { return }
        let oldName = oldTag.name
        updateState {
            $0.userCreatedTags[exKey] = ($0.userCreatedTags[exKey] ?? []).map { $0.id == tagId ? $0.copy(name: trimmed) : $0 }
            if $0.exerciseTags[exerciseId] == oldName {
                $0.exerciseTags[exerciseId] = trimmed
            }
        }
        persistOngoingState()
    }

    func toggleMainTagActive(_ exerciseId: String, tagId: String) {
        updateState { state in
            let current = state.activeTagsByExercise[exerciseId] ?? []
            let updated = current.contains(tagId) ? current.filter { $0 != tagId } : current + [tagId]
            state.activeTagsByExercise[exerciseId] = updated
            if let tag = state.userCreatedTags.values.flatMap({ $0 }).first(where: { $0.id == tagId }) {
                state.exerciseTags[exerciseId] = tag.name
            } else {
                state.exerciseTags.removeValue(forKey: exerciseId)
            }
        }
        persistOngoingState()
    }

    func addSubTag(_ exerciseId: String, tagId: String, name: String, category: SubTagCategory) {
        let exercise = visibleExercises(uiState).first(where: { $0.id == exerciseId }) ?? Exercise(id: "", name: "")
        let exKey = canonicalExerciseKey(exercise)
        let subTag = WorkoutSubTag(id: UUID().uuidString, name: name.trimmingCharacters(in: .whitespacesAndNewlines), category: category)
        updateState {
            $0.userCreatedTags[exKey] = ($0.userCreatedTags[exKey] ?? []).map { $0.id == tagId ? $0.copy(subTags: $0.subTags + [subTag]) : $0 }
        }
        persistOngoingState()
    }

    func removeSubTag(_ exerciseId: String, tagId: String, subTagId: String) {
        let exercise = visibleExercises(uiState).first(where: { $0.id == exerciseId }) ?? Exercise(id: "", name: "")
        let exKey = canonicalExerciseKey(exercise)
        updateState {
            $0.userCreatedTags[exKey] = ($0.userCreatedTags[exKey] ?? []).map { $0.id == tagId ? $0.copy(subTags: $0.subTags.filter { $0.id != subTagId }) : $0 }
            for (exId, subIds) in $0.activeSubTagsByExercise {
                if exId == exerciseId {
                    $0.activeSubTagsByExercise[exId] = subIds.filter { $0 != subTagId }
                }
            }
        }
        persistOngoingState()
    }

    func toggleSubTagActive(_ exerciseId: String, subTagId: String) {
        updateState { state in
            let current = state.activeSubTagsByExercise[exerciseId] ?? []
            state.activeSubTagsByExercise[exerciseId] = current.contains(subTagId) ? current.filter { $0 != subTagId } : current + [subTagId]
        }
        persistOngoingState()
    }

    func clearAllTags(_ exerciseId: String) {
        updateState {
            $0.activeTagsByExercise.removeValue(forKey: exerciseId)
            $0.activeSubTagsByExercise.removeValue(forKey: exerciseId)
            $0.exerciseTags.removeValue(forKey: exerciseId)
        }
        persistOngoingState()
    }

    func tagsForExercise(_ exerciseId: String) -> [WorkoutTag] {
        let exercise = visibleExercises(uiState).first(where: { $0.id == exerciseId }) ?? Exercise(id: "", name: "")
        let exKey = canonicalExerciseKey(exercise)
        return uiState.userCreatedTags[exKey] ?? []
    }

    func activeMainTags(_ exerciseId: String) -> [WorkoutTag] {
        let tagIds = uiState.activeTagsByExercise[exerciseId] ?? []
        return tagsForExercise(exerciseId).filter { tagIds.contains($0.id) }
    }

    func activeSubTags(_ exerciseId: String) -> [WorkoutSubTag] {
        let subTagIds = uiState.activeSubTagsByExercise[exerciseId] ?? []
        return tagsForExercise(exerciseId).flatMap { $0.subTags }.filter { subTagIds.contains($0.id) }
    }

    // MARK: - Load/Unit Mode Helpers

    private func inferUnitMode(_ exercise: Exercise, _ set: ExerciseSet) -> UnitModeV2 {
        set.unitModeV2 ?? {
            if exercise.trainingMode == .TIME || set.targetDuration != nil { return .TIME }
            if exercise.trainingMode == .DISTANCE { return .DISTANCE }
            if exercise.trainingMode == .CUSTOM { return .CUSTOM }
            return .REPS
        }()
    }

    private func inferLoadMode(_ set: ExerciseSet) -> LoadModeV2 { set.loadModeV2 ?? .LOAD }

    private func effectiveLoadModeForExercise(_ exercise: Exercise, setIdx: Int? = nil) -> LoadModeV2 {
        if let setIdx = setIdx {
            if let mode = resolvePersistedLoadModeForSet(exerciseId: exercise.id, setIdx: setIdx, tagId: uiState.exerciseTags[exercise.id], persistedLoadModeBySet: uiState.persistedLoadModeBySet, persistedLoadModeByExercise: uiState.persistedLoadModeByExercise) {
                return mode
            }
        } else {
            let exKey = workoutExerciseContextKey(exerciseId: exercise.id, tagId: uiState.exerciseTags[exercise.id])
            if let mode = uiState.persistedLoadModeByExercise[exKey] { return mode }
        }
        if let setIdx = setIdx, let plannedSet = exercise.sets[safe: setIdx] {
            return inferLoadMode(plannedSet)
        }
        return exercise.sets.first.map { inferLoadMode($0) } ?? .LOAD
    }

    private func inferPlannedTarget(_ set: ExerciseSet, _ unitMode: UnitModeV2) -> Double? {
        switch unitMode {
        case .TIME: return set.plannedTargetV2 ?? set.targetDuration.map { Double($0) }
        case .DISTANCE: return set.plannedTargetV2 ?? set.targetReps.map { Double($0) }
        case .REPS: return set.plannedTargetV2 ?? set.targetReps.map { Double($0) }
        case .CUSTOM: return set.plannedTargetV2 ?? set.targetReps.map { Double($0) } ?? set.targetDuration.map { Double($0) }
        }
    }

    private func inferPlannedIntensity(_ set: ExerciseSet) -> Double? {
        if set.isFailure || set.intensityMode == .FAILURE { return nil }
        if let rpe = set.targetRPE { return rpe }
        if let rir = set.targetRIR { return Double(10 - rir) }
        return nil
    }

    // MARK: - Performance Evaluation

    private func globalPerformanceKey(_ entry: SetEntryV2) -> String { entry.resolvedCanonicalExerciseId() }

    private func evaluateSetEntryV3(_ entry: SetEntryV2) -> WorkoutPerformanceHomologationEngine.EvaluationResult {
        let previousContext = uiState.contextualPerformanceCache[entry.contextKey]
            ?? repository.getContextPerformanceState(entry.contextKey)
        let previousGlobal = uiState.globalPerformanceCache[globalPerformanceKey(entry)]
            ?? repository.getGlobalPerformanceState(globalPerformanceKey(entry))
        let result = WorkoutPerformanceHomologationEngine.evaluate(entry: entry, previous: previousContext, previousGlobal: previousGlobal)
        repository.upsertContextPerformanceState(result.nextState)
        repository.upsertGlobalPerformanceState(result.nextGlobalState)

        let canonicalId = entry.resolvedCanonicalExerciseId()
        let rangeData = performanceRangeCache[canonicalId]
        let homologatedWithRange: HomologatedPerformanceResult
        if let range = rangeData, range.ermMax > range.ermMin {
            homologatedWithRange = result.homologated.copy(ermRangeMin: range.ermMin, ermRangeMax: range.ermMax)
        } else {
            homologatedWithRange = result.homologated
        }

        updateState {
            $0.contextualPerformanceCache[entry.contextKey] = result.nextState
            $0.globalPerformanceCache[result.nextGlobalState.globalKey] = result.nextGlobalState
            $0.lastHomologatedResultV3 = homologatedWithRange
        }

        if rangeData == nil, !canonicalId.isEmpty, performanceRangePrefetchInFlight.insert(canonicalId).inserted {
            Task.detached { @MainActor in
                let loaded = try? await self.repository.performanceRangeDao.getByContextKey(canonicalId)
                if let loaded = loaded { self.performanceRangeCache[canonicalId] = loaded }
                self.performanceRangePrefetchInFlight.remove(canonicalId)
            }
        }
        return result
    }

    func computeSetOutcomeV2(_ entry: SetEntryV2) -> SetOutcomeV2 { evaluateSetEntryV3(entry).outcome }

    // MARK: - Recording Gate

    func isRecording(_ key: String) -> Bool { uiState.recordingSetKey == key }

    func finishRecording(_ key: String) {
        recordingGate.finish(key: key)
        updateState { state in
            if state.recordingSetKey == key { state.recordingSetKey = nil }
        }
    }

    // MARK: - Recording

    func recordSetV2(
        weight: Double,
        value: Double,
        intensity: Double?,
        advanced: SetAdvancedFeedback = SetAdvancedFeedback(),
        loadMode: LoadModeV2? = nil,
        unitMode: UnitModeV2? = nil,
        bodyWeight: Double? = nil,
        side: String? = nil,
        tagId: String? = nil,
        setupId: String? = nil,
        machineBrand: String? = nil,
        amrapOverride: Bool = false,
        setIdxOverride: Int? = nil,
        expectedExerciseId: String? = nil,
        expectedSetIdx: Int? = nil,
        expectedSide: String? = nil
    ) {
        let state = uiState
        let allExercises = visibleExercises(state)
        guard let exercise = allExercises[safe: state.currentExerciseIdx] else { return }
        let targetSetIdx = setIdxOverride ?? state.currentSetIdx
        if let eId = expectedExerciseId, eId != exercise.id { return }
        if let eIdx = expectedSetIdx, eIdx != targetSetIdx { return }

        let initialSide: String? = exercise.isEffectivelyUnilateral() ? (side ?? expectedSide ?? "left") : nil
        if exercise.isEffectivelyUnilateral(), let eSide = expectedSide, eSide != initialSide { return }

        let recordingKey = buildCompletedSetKey(exercise.id, targetSetIdx, initialSide)
        guard recordingGate.tryStart(key: recordingKey) else { return }

        updateState { $0.recordingSetKey = recordingKey }

        let plannedSet = exercise.sets[safe: targetSetIdx]
        let activeProfile = activeContextProfile(exercise.id)

        var resolvedUnitMode = unitMode ?? (plannedSet.map { inferUnitMode(exercise, $0) } ?? .REPS)
        var resolvedLoadMode = loadMode ?? effectiveLoadModeForExercise(exercise, setIdx: targetSetIdx)
        if resolvedLoadMode == .LASTRE, weight <= 0 { resolvedLoadMode = .BODYWEIGHT }

        let resolvedBodyWeight: Double? = (bodyWeight ?? currentBodyWeight()).map { bw in
            repository.settings.weightUnit == .LBS ? bw * 0.45359237 : bw
        }
        let resolvedTagId = tagId
            ?? activeProfile?.tagId
            ?? (uiState.activeTagsByExercise[exercise.id]?.first.flatMap { id in
                uiState.userCreatedTags.values.flatMap { $0 }.first(where: { $0.id == id })?.name
            })
            ?? uiState.exerciseTags[exercise.id]
        let resolvedSubTagIds = uiState.activeSubTagsByExercise[exercise.id] ?? []
        let resolvedSetupId = setupId ?? activeProfile?.setupProfileId ?? plannedSet?.defaultSetupProfileIdV3 ?? plannedSet?.setupId
        let resolvedMachineBrand = machineBrand ?? activeProfile?.machineBrand ?? plannedSet?.machineBrand
        let isUnilateral = exercise.isEffectivelyUnilateral()
        let resolvedSide: String? = isUnilateral ? initialSide : nil

        let actualValue: Double
        switch resolvedUnitMode {
        case .TIME, .REPS, .DISTANCE, .CUSTOM: actualValue = max(0, value)
        }
        let logicalActualValue: Double
        switch resolvedUnitMode {
        case .REPS: logicalActualValue = actualValue + Double(max(0, advanced.partialReps ?? 0)) * 0.5
        default: logicalActualValue = actualValue
        }
        let plannedTarget = plannedSet.map { inferPlannedTarget($0, resolvedUnitMode) } ?? nil
        let debt: Double = (plannedTarget != nil && logicalActualValue >= 0) ? max(0, plannedTarget! - logicalActualValue) : 0

        var allSubTags: [WorkoutSubTag] = []
        for tags in uiState.userCreatedTags.values {
            for tag in tags {
                allSubTags.append(contentsOf: tag.subTags)
            }
        }
        
        let filteredSubTags = allSubTags.filter { subTag in
            resolvedSubTagIds.contains(subTag.id) && (subTag.category == .TECNICA || subTag.category == .MARCA)
        }
        
        let mappedNames = filteredSubTags.map { subTag in
            subTag.name.lowercased().replacingOccurrences(of: " ", with: "_")
        }
        
        let sortedNames = mappedNames.sorted()
        let techSubTags = sortedNames.joined(separator: "+")
        let contextKey = buildWorkoutContextKey(exerciseId: canonicalExerciseKey(exercise), machineBrand: resolvedMachineBrand, tagId: resolvedTagId, loadMode: resolvedLoadMode, unitMode: resolvedUnitMode, techSubTags: techSubTags.isEmpty ? nil : techSubTags)

        let isFirstInSession = evaluatedContextKeysThisSession.insert(contextKey).inserted

        let actualIntensityMode = advanced.actualIntensityMode ?? {
            if advanced.reachedFailure { return IntensityMode.FAILURE }
            if let _ = advanced.rir { return .RIR }
            if let _ = intensity { return .RPE }
            if amrapOverride { return .AMRAP }
            if plannedSet?.isFailure == true || plannedSet?.intensityMode == .FAILURE { return .FAILURE }
            return plannedSet?.intensityMode
        }()

        let actualIntensityValue: Double? = advanced.actualIntensityValue ?? {
            switch actualIntensityMode {
            case .RIR: return advanced.rir.map { Double($0) }
            case .FAILURE: return nil
            case .AMRAP: return intensity
            default: return intensity
            }
        }()

        let actualReps = resolvedUnitMode == .TIME ? 0 : Int(actualValue)
        let durationSeconds: Int? = resolvedUnitMode == .TIME ? max(0, advanced.timerElapsedSeconds ?? Int(actualValue)) : nil

        var techniques: [SetTechniqueV2] = []
        if !advanced.dropSets.isEmpty { techniques.append(SetTechniqueV2(technique: "DROP_SET")) }
        if !advanced.restPauses.isEmpty { techniques.append(SetTechniqueV2(technique: "REST_PAUSE")) }
        if advanced.isPartial { techniques.append(SetTechniqueV2(technique: "PARTIALS")) }
        if advanced.reachedFailure { techniques.append(SetTechniqueV2(technique: "FAILURE")) }
        if amrapOverride { techniques.append(SetTechniqueV2(technique: "AMRAP")) }

        let externalLoad: Double? = {
            switch resolvedLoadMode {
            case .LOAD, .BODYWEIGHT, .LASTRE: return weight > 0 ? weight : nil
            case .ASSISTED: return nil
            }
        }()
        let assistedLoad: Double? = resolvedLoadMode == .ASSISTED ? (weight > 0 ? weight : nil) : nil

        let resolvedBarWeightKg = activeProfile?.setupDetails?.barWeightKg ?? exercise.setupDetails?.barWeightKg

        let entry = SetEntryV2(
            exerciseId: exercise.id,
            exerciseDbId: canonicalExerciseKey(exercise),
            canonicalExerciseId: canonicalExerciseKey(exercise),
            setIndex: targetSetIdx,
            loadMode: resolvedLoadMode,
            unitMode: resolvedUnitMode,
            plannedTarget: plannedTarget,
            actualValue: logicalActualValue,
            loggedLoad: weight > 0 ? weight : nil,
            bodyWeight: resolvedBodyWeight,
            plannedIntensity: plannedSet.flatMap { inferPlannedIntensity($0) },
            actualIntensity: actualIntensityValue,
            debt: debt,
            failedSet: advanced.isFailedSet || advanced.executionError,
            reachedFailure: advanced.reachedFailure,
            amrapOverride: amrapOverride,
            techniques: techniques,
            tagId: resolvedTagId,
            setupId: resolvedSetupId,
            machineBrand: resolvedMachineBrand,
            contextKey: contextKey,
            timeProgressionStrategy: plannedSet?.timeProgressionStrategyV3 ?? .LOAD_THEN_TIME,
            barWeightKg: resolvedBarWeightKg,
            rom: advanced.rom,
            assistedReps: advanced.assistedReps,
            isFirstEvaluationInSession: isFirstInSession
        )

        let evaluation: WorkoutPerformanceHomologationEngine.EvaluationResult?
        if uiState.featureFlags.workoutV2Homologation || uiState.featureFlags.workoutV2LoadModes || uiState.featureFlags.workoutV3UnifiedFlow {
            evaluation = evaluateSetEntryV3(entry)
        } else {
            evaluation = nil
        }

        let outcome = evaluation?.outcome ?? SetOutcomeV2(
            contextKey: contextKey, loadMode: resolvedLoadMode, unitMode: resolvedUnitMode,
            plannedTarget: plannedTarget, actualValue: logicalActualValue,
            actualIntensity: actualIntensityValue, debt: debt,
            failedSet: advanced.isFailedSet || advanced.executionError,
            reachedFailure: advanced.reachedFailure, amrapOverride: amrapOverride,
            techniques: techniques,
            metricType: resolvedUnitMode == .TIME ? "TRM" : "ERM",
            metricValue: 0, estimatedRm: nil, trm: nil,
            globalPerformanceIndex: 50, contextPercentile: 50,
            contextEwma: 0, contextStdDev: 0, isContextPr: false,
            historyColor: .NEUTRAL, difficultySignal: .MATCHED,
            suggestedNextLoad: nil, suggestedTargetSeconds: nil, suggestionReason: nil,
            augeEquivalentLoad: max(0, weight), augeEquivalentReps: max(0, Int(logicalActualValue))
        )

        let currentWorkoutStep = workoutStepPositions(uiState).first(where: {
            $0.type == .WORKING_SET && $0.exerciseId == exercise.id && $0.setIndex == targetSetIdx && (resolvedSide == nil || $0.side == resolvedSide)
        })

        let payload = RecordedSetPayload(
            contextProfileId: activeProfile?.id,
            exerciseId: exercise.id,
            exerciseDbId: canonicalExerciseKey(exercise),
            side: resolvedSide,
            loadInputMode: resolvedLoadMode,
            unitMode: resolvedUnitMode,
            externalLoad: externalLoad,
            assistedLoad: assistedLoad,
            bodyWeightSnapshot: resolvedBodyWeight,
            completedReps: actualReps,
            partialReps: advanced.partialReps,
            durationSeconds: durationSeconds,
            actualIntensityMode: actualIntensityMode,
            actualIntensityValue: actualIntensityValue,
            techniques: techniques,
            failedSet: advanced.isFailedSet || advanced.executionError,
            reachedFailure: advanced.reachedFailure,
            amrapPerformed: amrapOverride,
            timerTargetSeconds: advanced.timerTargetSeconds,
            timerElapsedSeconds: advanced.timerElapsedSeconds,
            failureReason: advanced.failureReason,
            executionError: advanced.executionError,
            skipped: false
        )

        let completedSet = applyAdvancedFeedback(base: CompletedSet(
            id: UUID().uuidString,
            weight: outcome.augeEquivalentLoad,
            reps: actualReps,
            timeSeconds: durationSeconds,
            rpe: advanced.reachedFailure ? nil : (actualIntensityValue ?? intensity),
            supersetId: exercise.supersetGroupRefOrLegacyId(),
            supersetRoundIndex: currentWorkoutStep?.supersetRoundIndex,
            restAfterKind: currentWorkoutStep?.restAfterKind.rawValue,
            side: resolvedSide,
            actualIntensityMode: actualIntensityMode,
            actualIntensityValue: actualIntensityValue,
            debt: outcome.debt,
            contextProfileId: activeProfile?.id,
            tagId: resolvedTagId,
            subTagIds: resolvedSubTagIds,
            setupProfileId: resolvedSetupId,
            machineBrand: resolvedMachineBrand,
            homologatedResultV3: evaluation?.homologated,
            recordedPayloadV3: payload,
            setOutcomeV2: outcome
        ), advanced: advanced)

        let key = buildCompletedSetKey(exercise.id, targetSetIdx, resolvedSide)
        let wasExistingSet = uiState.completedSets[key] != nil
        let updatedCompletedSets = uiState.completedSets.merging([key: completedSet]) { _, new in new }

        let imbalanceNotice: String? = isUnilateral ? computeImbalanceNotice(exercise, targetSetIdx, updatedCompletedSets) : nil

        updateState {
            $0.completedSets = updatedCompletedSets
            $0.setAdvancedFeedback[key] = advanced
            $0.setJustLoggedKey = key
            $0.lastSetOutcomeV2 = outcome
            $0.lastHomologatedResultV3 = evaluation?.homologated
            $0.imbalanceNotice = imbalanceNotice
        }

        clearDraftForSet(exercise.id, setIdx: targetSetIdx, side: resolvedSide)
        updateState {
            $0.persistedLoadModeBySet[workoutSetKey(exerciseId: exercise.id, setIdx: targetSetIdx)] = resolvedLoadMode
            $0.persistedLoadModeByExercise[exercise.id] = resolvedLoadMode
        }
        persistLoadModeToProfile(exercise.id, loadMode: resolvedLoadMode)
        if weight > 0 { registerManualLoadOverride(exercise.id, setIdx: targetSetIdx, side: resolvedSide, load: weight) }
        refreshLoadSuggestions()
        persistOngoingState()

        if !wasExistingSet { nextSet(stopRest: false) }

        let wasLastSet = state.currentSetIdx == exercise.sets.count - 1
        let isExecutionError = advanced.isFailedSet || advanced.executionError

        let unilateralPendingOtherSide = isUnilateral && resolvedSide != nil && updatedCompletedSets[buildCompletedSetKey(exercise.id, targetSetIdx, counterpartSide(resolvedSide!))] == nil
        var stateAfterLoggedSet = uiState
        stateAfterLoggedSet.completedSets = updatedCompletedSets
        let nextStepForRest = nextIncompleteStepAfter(stateAfterLoggedSet)

        if !unilateralPendingOtherSide && !wasExistingSet {
            nextSet(stopRest: false)
        }

        let baseRest = exercise.restTime.map { $0 > 0 ? $0 : repository.settings.restTimerDefaultSeconds } ?? repository.settings.restTimerDefaultSeconds
        let sessionForRest = uiState.session.map { sessionForActiveMode($0, uiState.activeMode) }
        let supersetGroup = sessionForRest?.effectiveSupersetGroupFor(exercise)

        let sameSupersetRound = nextStepForRest?.supersetGroupId != nil
            && nextStepForRest!.supersetGroupId == exercise.supersetGroupRefOrLegacyId()
            && nextStepForRest!.exerciseId != exercise.id
            && nextStepForRest!.supersetRoundIndex == targetSetIdx

        let restKind: RestTimerKind
        if unilateralPendingOtherSide { restKind = .BETWEEN_SIDES }
        else if sameSupersetRound { restKind = .SUPERSET_INTRA }
        else if supersetGroup != nil { restKind = .SUPERSET_ROUND }
        else { restKind = .STANDARD }

        let completedCount = uiState.completedSets.count
        let totalSetsInSession = allExercises.reduce(0) { $0 + $1.sets.count }
        let sessionProgress = totalSetsInSession > 0 ? Double(completedCount) / Double(totalSetsInSession) : 0

        let adaptiveRest = WorkoutAdaptiveRest.compute(baseRestSeconds: baseRest, advanced: advanced)

        if !wasExistingSet {
            let plannedRest: Int
            switch restKind {
            case .SUPERSET_INTRA, .SUPERSET_ROUND, .BETWEEN_SIDES, .WARMUP:
                plannedRest = max(0, baseRest)
            case .STANDARD:
                plannedRest = max(10, baseRest)
            }
            let effectivePlanned = plannedRest

            let pendingSuggestion = PendingRestSuggestion(
                plannedSeconds: effectivePlanned,
                adaptiveSeconds: max(10, adaptiveRest),
                exerciseName: exercise.name,
                exerciseId: exercise.id,
                lastSet: completedSet,
                advancedFeedback: advanced
            )
            updateState { $0.pendingRestSuggestion = pendingSuggestion }

            if effectivePlanned > 0, !(isExecutionError && !wasLastSet) {
                startRestTimer(seconds: effectivePlanned, advanceOnFinish: false, lastSet: completedSet, advancedFeedback: advanced, kind: restKind)
            }
        }

        if isExecutionError, !wasLastSet, !uiState.showPostExerciseSheet {
            updateState {
                $0.showExecutionErrorDiscomfortSheet = true
                $0.isRestTimerRunning = false
                $0.restModalState = nil
                $0.pendingRestSuggestion = nil
            }
        }

        computeAndStoreAutoRegulation(completedSet: completedSet, advanced: advanced, setDrain: SetDrain(cnsDrainPct: 0, muscularDrainPct: 0, spinalDrainPct: 0), effectiveRpe: 0, sessionProgress: sessionProgress)
        updateCoachMessage(setDrain: SetDrain(cnsDrainPct: 0, muscularDrainPct: 0, spinalDrainPct: 0), sessionProgress: sessionProgress)
        recordingGate.finish(key: recordingKey)
        finishRecording(recordingKey)
    }

    // MARK: - Pace Coach

    func checkPaceCoachAlert() {
        let state = uiState
        let targetMin = state.customTargetDurationMinutes ?? state.session?.targetDurationMinutes
        guard let target = targetMin, target > 0, !state.isComplete else {
            if state.coachPaceAlert != nil || state.pacingAlertMessage != nil {
                updateState { $0.coachPaceAlert = nil; $0.pacingAlertMessage = nil }
            }
            return
        }

        let elapsedSeconds = max(0, Int((nowMs() - state.startTimeMs) / 1000))
        let remainingSeconds = target * 60 - elapsedSeconds
        let remainingMin = remainingSeconds / 60
        let allExercises = visibleExercises(state)
        let totalSets = allExercises.reduce(0) { $0 + $1.sets.count }
        guard totalSets > 0 else { return }

        let uniqueCompleted = Set(state.completedSets.keys.map { key -> String in
            let parts = key.split(separator: "_").map(String.init)
            return parts.count >= 2 ? "\(parts[0])_\(parts[1])" : key
        }).count
        let progress = Double(uniqueCompleted) / Double(totalSets)
        guard progress < 1.0 else { return }

        let expectedProgress = Double(elapsedSeconds) / Double(target * 60)
        let newAlert: String?
        if remainingMin <= 0 { newAlert = "excedido" }
        else if progress < expectedProgress - 0.15, elapsedSeconds > 300 { newAlert = "retrasado" }
        else if progress < expectedProgress - 0.05, elapsedSeconds > 300 { newAlert = "apurar" }
        else { newAlert = nil }

        if state.coachPaceAlert != newAlert {
            updateState { $0.coachPaceAlert = newAlert }
        }

        if progress < expectedProgress - 0.15, elapsedSeconds > 300 {
            let remainingSets = totalSets - uniqueCompleted
            let safeMin = max(0, remainingMin)
            let message = "Ritmo lento · \(remainingSets) series · \(safeMin) min"
            updateState { $0.pacingAlertMessage = message }
        } else if remainingMin <= 0 {
            updateState { $0.pacingAlertMessage = "Tiempo de sesión agotado" }
        } else {
            updateState { $0.pacingAlertMessage = nil }
        }
    }

    private func checkPacingStatus() {
        let state = uiState
        guard let totalMinutes = state.customTargetDurationMinutes ?? state.targetDurationMinutes else { return }
        guard let remainingSeconds = state.sessionTimeRemainingSeconds else { return }
        let allExercises = visibleExercises(state)
        let totalSets = allExercises.reduce(0) { $0 + $1.sets.count }
        guard totalSets > 0 else { return }

        let uniqueCompleted = Set(state.completedSets.keys.map { key -> String in
            let parts = key.split(separator: "_").map(String.init)
            return parts.count >= 2 ? "\(parts[0])_\(parts[1])" : key
        }).count

        let progress = Double(uniqueCompleted) / Double(totalSets)
        guard progress < 1.0 else { return }

        let totalSeconds = totalMinutes * 60
        let elapsedSeconds = totalSeconds - remainingSeconds
        guard elapsedSeconds >= 300 else { return }

        let expectedProgress = Double(elapsedSeconds) / Double(totalSeconds)

        if progress < expectedProgress - 0.15 {
            let remainingSets = totalSets - uniqueCompleted
            let remainingMinutes = max(0, remainingSeconds / 60)
            let message = "Ritmo lento · \(remainingSets) series · \(remainingMinutes) min"
            if message != state.pacingAlertMessage {
                updateState { $0.pacingAlertMessage = message }
            }
        } else if state.pacingAlertMessage != nil {
            updateState { $0.pacingAlertMessage = nil }
        }
    }

    private func adjustRestTimeForPace(_ baseSeconds: Int) -> Int {
        let state = uiState
        guard let targetMin = state.customTargetDurationMinutes ?? state.session?.targetDurationMinutes else { return baseSeconds }
        guard targetMin > 0 else { return baseSeconds }
        let elapsedMin = Int((nowMs() - state.startTimeMs) / 60000)
        let remainingMin = targetMin - elapsedMin
        let totalSets = visibleExercises(state).reduce(0) { $0 + $1.sets.count }
        let progress: Float = totalSets > 0 ? Float(state.completedSets.count) / Float(totalSets) : 0
        if remainingMin <= 15, progress < 0.50, baseSeconds > 60 {
            return max(60, baseSeconds - 30)
        }
        return baseSeconds
    }

    // MARK: - Navigation

    func isSetDone(_ setIdx: Int, exercise: Exercise) -> Bool {
        isSetDone(completedSets: uiState.completedSets, exerciseId: exercise.id, setIdx: setIdx, isUnilateral: exercise.isEffectivelyUnilateral())
    }

    func updateSetDraft(_ exerciseId: String, setIdx: Int, side: String? = nil, draft: WorkoutSetDraft) {
        let key = workoutSetKey(exerciseId: exerciseId, setIdx: setIdx, side: side)
        let fallbackKey = side != nil ? workoutSetKey(exerciseId: exerciseId, setIdx: setIdx) : nil
        updateState {
            if draft.isDirty {
                $0.setDrafts[key] = draft.copy(updatedAtMs: UInt64(Date().timeIntervalSince1970 * 1000))
            } else {
                $0.setDrafts.removeValue(forKey: key)
                if let fk = fallbackKey { $0.setDrafts.removeValue(forKey: fk) }
            }
        }
        if let lm = draft.loadMode, lm != (uiState.setDrafts[key]?.loadMode) {
            persistLoadModeToProfile(exerciseId, loadMode: lm)
        }
        persistOngoingState()
    }

    func getSetDraft(_ exerciseId: String, setIdx: Int, side: String? = nil) -> WorkoutSetDraft? {
        let key = workoutSetKey(exerciseId: exerciseId, setIdx: setIdx, side: side)
        if let exact = uiState.setDrafts[key] { return exact }
        if side != nil { return uiState.setDrafts[workoutSetKey(exerciseId: exerciseId, setIdx: setIdx)] }
        return [uiState.setDrafts[workoutSetKey(exerciseId: exerciseId, setIdx: setIdx)], uiState.setDrafts[workoutSetKey(exerciseId: exerciseId, setIdx: setIdx, side: "left")], uiState.setDrafts[workoutSetKey(exerciseId: exerciseId, setIdx: setIdx, side: "right")]].compactMap { $0 }.max(by: { $0.updatedAtMs < $1.updatedAtMs })
    }

    func beginEditingSet(_ exerciseId: String, setIdx: Int, side: String? = nil) {
        let state = uiState
        let exercises = visibleExercises(state)
        guard let exerciseIdx = exercises.firstIndex(where: { $0.id == exerciseId }) else { return }
        guard let editingState = buildEditingStateForPosition(completedSets: state.completedSets, exercise: exercises[exerciseIdx], setIdx: setIdx, preferredSide: side) else { return }
        updateState {
            $0.currentExerciseIdx = exerciseIdx
            $0.currentSetIdx = editingState.setIdx
            $0.pendingRestSuggestion = nil
            $0.restModalState = nil
            $0.editingState = editingState
        }
        persistOngoingState()
    }

    func endEditingSet() {
        updateState { $0.editingState = nil }
        persistOngoingState()
    }

    func discardSetDraft(_ exerciseId: String, setIdx: Int, side: String? = nil) {
        clearDraftForSet(exerciseId, setIdx: setIdx, side: side)
        persistOngoingState()
    }

    func discardAllDraftsForSet(_ exerciseId: String, setIdx: Int) {
        clearDraftForSet(exerciseId, setIdx: setIdx, side: nil)
        clearDraftForSet(exerciseId, setIdx: setIdx, side: "left")
        clearDraftForSet(exerciseId, setIdx: setIdx, side: "right")
        persistOngoingState()
    }

    func editingState() -> WorkoutEditingState? { uiState.editingState }

    func showVoiceError(_ exerciseId: String, setIdx: Int, side: String?, message: String) {
        voiceTask?.cancel()
        voiceTask = nil
        updateState { $0.voiceUiState = .error(exerciseId: exerciseId, setIdx: setIdx, side: side, message: message) }
    }

    func consumeVoiceAppliedMessage(_ exerciseId: String, setIdx: Int, side: String?) {
        if case .applied(let eid, let si, let s, _, _) = uiState.voiceUiState, eid == exerciseId, si == setIdx, s == side {
            updateState { $0.voiceUiState = .idle }
        }
    }

    func confirmVoiceInput(_ exerciseId: String, setIdx: Int, side: String?, isTimeMode: Bool, baseIntensityMode: IntensityMode?) {
        guard case .confirmation(let eid, let si, let s, let interpretation) = uiState.voiceUiState else { return }
        guard eid == exerciseId, si == setIdx, s == side else { return }

        let draft = getSetDraft(exerciseId, setIdx: setIdx, side: side) ?? WorkoutSetDraft(selectedSide: side)
        let resolvedSide = interpretation.side ?? side ?? draft.selectedSide
        var nextDraft = draft.copy(
            weightText: interpretation.weightKg?.toTrimmedNumberString() ?? draft.weightText,
            valueText: interpretation.metricValue.map { String($0) } ?? draft.valueText,
            intensityText: workoutVoiceIntensityText(interpretation, baseIntensityMode),
            selectedSide: resolvedSide,
            reachedFailure: interpretation.fields.contains(.FAILURE) ? interpretation.reachedFailure : draft.reachedFailure,
            voiceFields: interpretation.fields,
            isDirty: true
        )
        if resolvedSide != side { clearDraftForSet(exerciseId, setIdx: setIdx, side: side) }
        updateSetDraft(exerciseId, setIdx: setIdx, side: resolvedSide, draft: nextDraft)
        updateState { $0.voiceUiState = .applied(exerciseId: exerciseId, setIdx: setIdx, side: resolvedSide, interpretation: interpretation, message: workoutVoiceAppliedMessage(interpretation, isTimeMode)) }
    }

    func toggleVoiceSession() {
        if uiState.voiceSessionEnabled { disableVoice() } else { enableVoice() }
    }

    func enableVoice() {
        updateState {
            $0.voiceSessionEnabled = true
            $0.voiceSessionState = VoiceSessionState()
        }
    }

    func disableVoice() {
        updateState {
            $0.voiceSessionEnabled = false
            $0.voiceSessionState = VoiceSessionState()
        }
    }

    func cancelVoiceInput() {
        voiceTask?.cancel()
        voiceTask = nil
        updateState { $0.voiceUiState = .idle }
    }

    // MARK: - Voice Helpers

    private func buildVoiceAdvancedFeedback(_ interpretation: WorkoutVoiceInterpretation) -> SetAdvancedFeedback {
        let reachedFailure = interpretation.reachedFailure
        let intensityMode: IntensityMode? = {
            if reachedFailure { return .FAILURE }
            if interpretation.intensityKind == .RPE { return .RPE }
            if interpretation.intensityKind == .RIR { return .RIR }
            return nil
        }()
        return SetAdvancedFeedback(
            rir: intensityMode == .RIR ? interpretation.intensityValue.map { Int($0) } : nil,
            reachedFailure: reachedFailure,
            actualIntensityMode: intensityMode,
            actualIntensityValue: reachedFailure ? nil : interpretation.intensityValue
        )
    }

    private func speakCurrentStepAnnouncementIfEnabled() {
        // placeholder for TTS
    }

    // MARK: - Imbalance

    private func computeImbalanceNotice(_ exercise: Exercise, _ setIdx: Int, _ completedSets: [String: CompletedSet]) -> String? {
        guard exercise.isEffectivelyUnilateral() else { return nil }
        guard let left = completedSets[buildCompletedSetKey(exercise.id, setIdx, "left")],
              let right = completedSets[buildCompletedSetKey(exercise.id, setIdx, "right")] else { return nil }

        var reasons: [String] = []
        if let lRpe = left.rpe, let rRpe = right.rpe, abs(lRpe - rRpe) > 1.0 {
            let dominant = lRpe > rRpe ? "izquierdo" : "derecho"
            reasons.append("RPE \(dominant) mayor")
        }
        if let lRir = left.rir, let rRir = right.rir, abs(lRir - rRir) > 1 {
            reasons.append("Menos reserva lado \(lRir < rRir ? "izquierdo" : "derecho")")
        }
        if left.reps > 0, right.reps > 0, abs(left.reps - right.reps) > 2 {
            let dominant = left.reps > right.reps ? "izquierdo" : "derecho"
            reasons.append("Reps \(dominant) mayor")
        }

        let leftWork = unilateralWorkScore(left)
        let rightWork = unilateralWorkScore(right)
        if leftWork > 0, rightWork > 0 {
            let ratio = abs(leftWork - rightWork) / max(leftWork, rightWork)
            if ratio > 0.10 {
                reasons.append("Carga \(leftWork > rightWork ? "izquierdo" : "derecho") \(Int(ratio * 100))% mayor")
            }
        }

        guard !reasons.isEmpty else { return nil }
        return "Desbalance en \(exercise.name): \(reasons.joined(separator: "; ")). Considera trabajo unilateral."
    }

    private func unilateralWorkScore(_ set: CompletedSet) -> Double {
        let metric: Double = {
            if (set.timeSeconds ?? 0) > 0 { return Double(set.timeSeconds ?? 0) }
            if set.reps > 0 { return Double(set.reps) }
            return 0
        }()
        return (max(0, set.weight) + 1.0) * metric
    }

    // MARK: - Live Energy

    private func recomputeLiveEnergy(completedSets: [String: CompletedSet], allExercises: [Exercise]) -> SessionEnergySummary {
        let completedExercises = allExercises.map { exercise -> CompletedExercise in
            let sets = exercise.sets.indices.flatMap { setIdx -> [CompletedSet] in
                [completedSets["\(exercise.id)_\(setIdx)"],
                 completedSets["\(exercise.id)_\(setIdx)_L"],
                 completedSets["\(exercise.id)_\(setIdx)_R"]].compactMap { $0 }
            }
            return CompletedExercise(
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                exerciseDbId: exercise.exerciseDbId ?? exercise.exerciseId,
                canonicalExerciseId: exercise.canonicalExerciseId ?? canonicalExerciseKey(exercise),
                sets: sets,
                restTime: exercise.restTime ?? 90,
                supersetId: exercise.supersetGroupRefOrLegacyId()
            )
        }.filter { !$0.sets.isEmpty }

        return TrainingEnergyEngine.estimateLiveSession(completedExercises: completedExercises, settings: repository.settings)
    }

    // MARK: - Resume Position

    private func resolveResumePosition(exercises: [Exercise], completedSets: [String: CompletedSet], preferredExerciseId: String?, preferredSetId: String?) -> (Int, Int) {
        guard !exercises.isEmpty else { return (0, 0) }

        if let prefId = preferredExerciseId, !prefId.isEmpty {
            if let prefIdx = exercises.firstIndex(where: { $0.id == prefId }) {
                let prefEx = exercises[prefIdx]
                if let prefSetId = preferredSetId {
                    if let prefSetIdx = prefEx.sets.firstIndex(where: { $0.id == prefSetId }) {
                        if !isSetDone(completedSets: completedSets, exerciseId: prefEx.id, setIdx: prefSetIdx, isUnilateral: prefEx.isEffectivelyUnilateral()) {
                            return (prefIdx, prefSetIdx)
                        }
                    }
                }
                if let fallback = prefEx.sets.indices.first(where: { !isSetDone(completedSets: completedSets, exerciseId: prefEx.id, setIdx: $0, isUnilateral: prefEx.isEffectivelyUnilateral()) }) {
                    return (prefIdx, fallback)
                }
            }
        }

        for (idx, ex) in exercises.enumerated() {
            if let pending = ex.sets.indices.first(where: { !isSetDone(completedSets: completedSets, exerciseId: ex.id, setIdx: $0, isUnilateral: ex.isEffectivelyUnilateral()) }) {
                return (idx, pending)
            }
        }
        return (exercises.count, 0)
    }

    // MARK: - Session Mutation

    private func applySessionMutation(_ updatedSession: Session, preferredExerciseId: String? = nil, preferredSetId: String? = nil) {
        let normalizedSession = updatedSession.normalizeSupersetsForWorkout()
        let visible = visibleExercises(uiState.copy(session: normalizedSession))
        let resolvedExerciseIdx = preferredExerciseId.flatMap { id in visible.firstIndex(where: { $0.id == id }) }
            ?? min(uiState.currentExerciseIdx, max(0, visible.count - 1))
        let resolvedSetIdx = preferredSetId.flatMap { setId in
            visible[safe: resolvedExerciseIdx]?.sets.firstIndex(where: { $0.id == setId })
        } ?? min(uiState.currentSetIdx, max(0, (visible[safe: resolvedExerciseIdx]?.sets.count ?? 1) - 1))

        updateState {
            $0.session = normalizedSession
            $0.currentExerciseIdx = resolvedExerciseIdx
            $0.currentSetIdx = resolvedSetIdx
        }
        refreshLoadSuggestions()
        persistOngoingState()
        persistSessionToProgram(normalizedSession)
    }

    private func persistSessionToProgram(_ updatedSession: Session) {
        guard !uiState.weekId.isEmpty else { return }
        guard let program = repository.getProgramById(programId) else { return }
        guard WorkoutEditingRules.canPersistLiveStructuralChanges(program) else { return }
        let updated = program.updateWeekSessions(uiState.macroIndex, mesoIndex: uiState.mesoIndex, weekId: uiState.weekId) { sessions in
            sessions.map { $0.id == sessionId ? updatedSession : $0 }
        }
        if updated != program { repository.updateProgram(updated) }
    }

    // MARK: - Exercise Replacement

    func replaceExercise(_ exerciseId: String, replacement: ExerciseMuscleInfo) {
        let state = uiState
        guard let base = state.session else { return }
        let updatedSession = withModeSession(base, state.activeMode) { modeSession in
            modeSession.replaceExerciseById(exerciseId) { buildReplacementExercise($0, replacement) }
        }
        let cleanedCompleted = state.completedSets.filter { !$0.key.hasPrefix("\(exerciseId)_") }
        let cleanedAdvanced = state.setAdvancedFeedback.filter { !$0.key.hasPrefix("\(exerciseId)_") }
        let cleanedFeedback = state.postExerciseFeedbackByExerciseId.filter { $0.key != exerciseId }

        updateState {
            $0.session = updatedSession
            $0.completedSets = cleanedCompleted
            $0.setAdvancedFeedback = cleanedAdvanced
            $0.postExerciseFeedbackByExerciseId = cleanedFeedback
        }
        refreshLoadSuggestions()
        persistOngoingState()
    }

    func applyReplacementDecision(_ exerciseId: String, replacement: ExerciseMuscleInfo, scope: ReplacementPersistenceScopeV2) {
        replaceExercise(exerciseId, replacement: replacement)
    }

    func dismissPendingReplacementPersistencePrompt() {
        deferredReplacementPrompt = nil
        updateState { $0.pendingReplacementPersistencePrompt = nil }
    }

    func commitPendingReplacementPersistence(_ scope: ReplacementPersistenceScopeV2) {
        updateState { $0.pendingReplacementPersistencePrompt = nil }
    }

    func replacementScopeOptions() -> [ReplacementPersistenceScopeV2] {
        guard let program = repository.getProgramById(programId) else { return [.SESSION_ONLY] }
        return WorkoutEditingRules.replacementPersistenceOptions(program)
    }

    // MARK: - Exercise Skip

    func skipExercise(_ exerciseId: String) {
        skipExerciseAndAdvance(uiState, exerciseId: exerciseId)
    }

    func skipRemainingCurrentExercise() {
        stopRestTimer()
        guard let current = visibleExercises(uiState).first(where: { $0.id == visibleExercises(uiState)[uiState.currentExerciseIdx].id }) else {
            openFinishSheet()
            return
        }
        skipExerciseAndAdvance(uiState, exerciseId: current.id)
    }

    func skipCurrentSupersetRound() {
        stopRestTimer()
        let state = uiState
        let visible = visibleExercises(state)
        let steps = workoutStepPositions(state)
        let currentStepIdx = stepPositionIndex(steps, visible: visible, exerciseIdx: state.currentExerciseIdx, setIdx: state.currentSetIdx, activeStepKey: state.activeStepKey)
        guard let currentStep = steps[safe: currentStepIdx],
              let groupId = currentStep.supersetGroupId,
              let roundIndex = currentStep.supersetRoundIndex else { return }

        let remainingRoundSteps = steps.dropFirst(currentStepIdx + 1).prefix { $0.supersetGroupId == groupId && $0.supersetRoundIndex == roundIndex && $0.type == .WORKING_SET && $0.setIndex != nil }
        guard !remainingRoundSteps.isEmpty else { return }

        let advanced = SetAdvancedFeedback(failureReason: "skipped_round", skipped: true)
        var updatedCompleted = state.completedSets
        var updatedAdvanced = state.setAdvancedFeedback

        for step in remainingRoundSteps {
            guard let exercise = visible.first(where: { $0.id == step.exerciseId }),
                  let setIndex = step.setIndex else { continue }
            let sides: [String?] = exercise.isEffectivelyUnilateral() ? ["left", "right"] : [nil]
            for side in sides {
                let key = buildCompletedSetKey(exercise.id, setIndex, side)
                if updatedCompleted[key] == nil {
                    updatedCompleted[key] = applyAdvancedFeedback(base: CompletedSet(id: UUID().uuidString, side: side), advanced: advanced)
                    updatedAdvanced[key] = advanced
                }
            }
        }

        updateState {
            $0.completedSets = updatedCompleted
            $0.setAdvancedFeedback = updatedAdvanced
            $0.pendingRestSuggestion = nil
            $0.restModalState = nil
            $0.isRestTimerRunning = false
        }
        refreshLoadSuggestions()
        persistOngoingState()
        nextSet(stopRest: false)
    }

    func skipSet() {
        stopRestTimer()
        let state = uiState
        guard let exercise = visibleExercises(state).first(where: { $0.id == visibleExercises(state)[safe: state.currentExerciseIdx]?.id }) else { return }

        if isSetDone(completedSets: state.completedSets, exerciseId: exercise.id, setIdx: state.currentSetIdx, isUnilateral: exercise.isEffectivelyUnilateral()) {
            nextSet(stopRest: false)
            return
        }

        let advanced = SetAdvancedFeedback(failureReason: "skipped", skipped: true)
        var updatedCompleted = state.completedSets
        var updatedAdvanced = state.setAdvancedFeedback

        let sides: [String?] = exercise.isEffectivelyUnilateral() ? ["left", "right"] : [nil]
        for side in sides {
            let key = buildCompletedSetKey(exercise.id, state.currentSetIdx, side)
            updatedCompleted[key] = applyAdvancedFeedback(base: CompletedSet(id: UUID().uuidString, side: side), advanced: advanced)
            updatedAdvanced[key] = advanced
        }

        updateState {
            $0.completedSets = updatedCompleted
            $0.setAdvancedFeedback = updatedAdvanced
            $0.pendingRestSuggestion = nil
        }
        for side in sides { clearDraftForSet(exercise.id, setIdx: state.currentSetIdx, side: side) }
        refreshLoadSuggestions()
        persistOngoingState()
        nextSet(stopRest: false)
    }

    // MARK: - Warmup/Mobility

    func markWarmupComplete(_ exerciseId: String) {
        guard let exercise = visibleExercises(uiState).first(where: { $0.id == exerciseId }) else { return }
        let keys = exercise.warmupSets.map { WorkoutStepRules.warmupStepKey(exerciseId, $0.id) }
        updateState { $0.warmupCompletedExerciseIds.formUnion([exerciseId] + keys) }
        persistOngoingState()
        nextSet(stopRest: false)
    }

    func markWarmupComplete(_ exerciseId: String, warmupSetId: String, completed: Bool = true) {
        let key = WorkoutStepRules.warmupStepKey(exerciseId, warmupSetId)
        let state = uiState
        let alreadyCompleted = state.warmupCompletedExerciseIds.contains(key) || state.warmupCompletedExerciseIds.contains(exerciseId)
        guard completed != alreadyCompleted else { return }
        let shouldAdvance = completed && state.activeStepKey == key
        updateState {
            if completed { $0.warmupCompletedExerciseIds.insert(key) }
            else { $0.warmupCompletedExerciseIds.remove(key); $0.warmupCompletedExerciseIds.remove(exerciseId) }
        }
        persistOngoingState()
        if shouldAdvance { nextSet(stopRest: false) }
    }

    func markMobilityComplete(_ exerciseId: String, mobilityId: String, completed: Bool = true) {
        let key = WorkoutStepRules.mobilityStepKey(exerciseId, mobilityId)
        let state = uiState
        let alreadyCompleted = state.mobilityCompletedExerciseIds.contains(key)
        guard completed != alreadyCompleted else { return }
        let shouldAdvance = completed && state.activeStepKey == key
        updateState {
            if completed { $0.mobilityCompletedExerciseIds.insert(key) }
            else { $0.mobilityCompletedExerciseIds.remove(key) }
        }
        persistOngoingState()
        if shouldAdvance { nextSet(stopRest: false) }
    }

    func resolvePendingRestSuggestion(useAdaptive: Bool) {
        guard let pending = uiState.pendingRestSuggestion else { return }
        updateState {
            $0.pendingRestSuggestion = nil
            $0.restModalState = $0.restModalState?.copy(activeSeconds: useAdaptive ? pending.adaptiveSeconds : pending.plannedSeconds, isManualOverride: false)
        }
        startRestTimer(seconds: useAdaptive ? pending.adaptiveSeconds : pending.plannedSeconds, advanceOnFinish: false, lastSet: pending.lastSet, advancedFeedback: pending.advancedFeedback)
    }

    // MARK: - Finish

    func finishUpToCurrentPoint() {
        stopRestTimer()
        let state = uiState
        let visible = visibleExercises(state)
        let omittedIds = visible.dropFirst(state.currentExerciseIdx + 1).map { $0.id }
        updateState { $0.skippedExerciseIds.formUnion(omittedIds) }
        openFinishSheet()
    }

    func selectExercise(_ idx: Int) {
        guard !uiState.showPostExerciseSheet else { return }
        stopRestTimer()
        let state = uiState
        let targetExercise = visibleExercises(state)[safe: idx]
        let targetStep = targetExercise.flatMap { firstIncompleteStepForExercise(state, exercise: $0) }
        let targetSetIdx = targetStep?.setIndex ?? 0
        updateState {
            $0.currentExerciseIdx = idx
            $0.currentSetIdx = targetSetIdx
            $0.activeStepKey = targetStep?.stepKey
            $0.pendingRestSuggestion = nil
            $0.restModalState = nil
        }
        persistOngoingState()
    }

    func nextSet(stopRest: Bool = true) {
        if stopRest { stopRestTimer() }
        let state = uiState
        let allExercises = visibleExercises(state)
        guard let currentEx = allExercises[safe: state.currentExerciseIdx] else { return }

        guard let nextStep = nextIncompleteStepAfter(state) else {
            let feedbackTarget = buildPostExerciseFeedbackTarget(state, exercise: currentEx)
            let shouldShow = !feedbackTarget.missingExerciseIds(state).isEmpty
            updateState {
                $0.showPostExerciseSheet = shouldShow
                $0.postExerciseTargetIdx = state.currentExerciseIdx
                $0.postExerciseFeedbackTarget = shouldShow ? feedbackTarget : nil
                $0.pendingPostExerciseIdx = shouldShow ? -2 : -1
                $0.editingState = shouldShow ? $0.editingState : nil
            }
            persistOngoingState()
            return
        }

        guard let nextPosition = nextStep.positionIn(allExercises) else { return }
        let nextExerciseIdx = nextPosition.0
        let nextSetIdx = nextPosition.1
        let nextExercise = allExercises[safe: nextExerciseIdx]
        let exerciseChanged = nextExerciseIdx != state.currentExerciseIdx

        updateState {
            $0.currentExerciseIdx = nextExerciseIdx
            $0.currentSetIdx = nextSetIdx
            $0.activeStepKey = nextStep.stepKey
            $0.showPostExerciseSheet = false
            $0.postExerciseTargetIdx = -1
            $0.postExerciseFeedbackTarget = nil
            $0.pendingPostExerciseIdx = -1
        }
        persistOngoingState()
    }

    func selectWorkoutStep(_ stepKey: String) {
        guard !uiState.showPostExerciseSheet, !stepKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        let visible = visibleExercises(uiState)
        guard let targetStep = workoutStepPositions(uiState).first(where: { $0.stepKey == stepKey }),
              let position = targetStep.positionIn(visible) else { return }
        let targetExercise = visible[safe: position.0]
        guard position.0 != uiState.currentExerciseIdx || position.1 != uiState.currentSetIdx || uiState.activeStepKey != stepKey else { return }
        stopRestTimer()
        updateState {
            $0.currentExerciseIdx = position.0
            $0.currentSetIdx = position.1
            $0.activeStepKey = targetStep.stepKey
            $0.pendingRestSuggestion = nil
            $0.restModalState = nil
        }
        persistOngoingState()
    }

    func navigateAdjacentWorkingStep(forward: Bool) {
        guard !uiState.showPostExerciseSheet else { return }
        let visible = visibleExercises(uiState)
        let steps = workoutStepPositions(uiState).filter { $0.type == .WORKING_SET }
        guard !steps.isEmpty else { return }
        let currentIdx = steps.firstIndex(where: { $0.stepKey == uiState.activeStepKey }) ?? steps.firstIndex(where: { step in
            guard let pos = step.positionIn(visible) else { return false }
            return pos.0 == uiState.currentExerciseIdx && pos.1 == uiState.currentSetIdx
        }) ?? -1
        guard currentIdx >= 0 else { return }
        let targetIdx = min(max(0, currentIdx + (forward ? 1 : -1)), steps.count - 1)
        guard targetIdx != currentIdx else { return }
        let targetStep = steps[targetIdx]
        guard let position = targetStep.positionIn(visible) else { return }
        updateState {
            $0.currentExerciseIdx = position.0
            $0.currentSetIdx = position.1
            $0.activeStepKey = targetStep.stepKey
            $0.pendingRestSuggestion = nil
            $0.restModalState = nil
        }
        persistOngoingState()
    }

    func jumpToSet(_ setIdx: Int) {
        guard !uiState.showPostExerciseSheet else { return }
        let state = uiState
        guard let currentExercise = visibleExercises(state).first(where: { $0.id == visibleExercises(state)[safe: state.currentExerciseIdx]?.id }) else { return }
        let maxIdx = max(0, currentExercise.sets.count - 1)
        let targetSetIdx = min(max(0, setIdx), maxIdx)
        guard targetSetIdx != state.currentSetIdx else { return }
        let visible = visibleExercises(state)
        let targetStep = workoutStepPositions(state).first(where: {
            $0.type == .WORKING_SET && $0.exerciseId == currentExercise.id && $0.setIndex == targetSetIdx
        })
        updateState {
            $0.currentSetIdx = targetSetIdx
            $0.activeStepKey = targetStep?.stepKey ?? WorkoutStepRules.workingStepKey(currentExercise.id, setIdx: targetSetIdx)
            $0.pendingRestSuggestion = nil
        }
        persistOngoingState()
    }

    func prevSet() {
        guard !uiState.showPostExerciseSheet else { return }
        stopRestTimer()
        guard let previousStep = previousStepBefore(uiState) else { return }
        let visible = visibleExercises(uiState)
        guard let position = previousStep.positionIn(visible) else { return }
        updateState {
            $0.currentExerciseIdx = position.0
            $0.currentSetIdx = position.1
            $0.activeStepKey = previousStep.stepKey
        }
        persistOngoingState()
    }

    func selectSupersetRound(_ roundIdx: Int) {
        guard !uiState.showPostExerciseSheet else { return }
        let visible = visibleExercises(uiState)
        guard let currentExercise = visible[safe: uiState.currentExerciseIdx],
              let groupId = currentExercise.supersetGroupRefOrLegacyId() else { return }
        if let targetStep = workoutStepPositions(uiState).first(where: { $0.type == .WORKING_SET && $0.supersetGroupId == groupId && $0.setIndex == roundIdx && $0.exerciseId == currentExercise.id }) {
            selectWorkoutStep(targetStep.stepKey)
        } else if let targetStep = workoutStepPositions(uiState).first(where: { $0.type == .WORKING_SET && $0.supersetGroupId == groupId && $0.setIndex == roundIdx }) {
            selectWorkoutStep(targetStep.stepKey)
        }
    }

    func selectExerciseInSupersetRound(_ exerciseId: String) {
        guard !uiState.showPostExerciseSheet else { return }
        let visible = visibleExercises(uiState)
        guard let currentExercise = visible[safe: uiState.currentExerciseIdx],
              let groupId = currentExercise.supersetGroupRefOrLegacyId() else { return }
        let roundIdx = uiState.currentSetIdx
        guard let targetStep = workoutStepPositions(uiState).first(where: { $0.type == .WORKING_SET && $0.supersetGroupId == groupId && $0.setIndex == roundIdx && $0.exerciseId == exerciseId }) else { return }
        selectWorkoutStep(targetStep.stepKey)
    }

    func recoverFromOrphanPostExerciseSheet() {
        guard uiState.showPostExerciseSheet else { return }
        let visible = visibleExercises(uiState)
        let hasTarget = visible[safe: uiState.postExerciseTargetIdx] != nil
        if hasTarget || uiState.postExerciseFeedbackTarget != nil { return }
        updateState {
            $0.showPostExerciseSheet = false
            $0.postExerciseTargetIdx = -1
            $0.postExerciseFeedbackTarget = nil
            $0.pendingPostExerciseIdx = -1
        }
        persistOngoingState()
    }

    // MARK: - Rest Timer

    func startRestTimer(seconds: Int, advanceOnFinish: Bool = false, lastSet: CompletedSet? = nil, advancedFeedback: SetAdvancedFeedback? = nil, preserveElapsed: Bool = false, kind: RestTimerKind = .STANDARD) {
        guard seconds > 0 else { return }
        timerTask?.cancel()

        if !preserveElapsed {
            restReferenceSet = lastSet
            restReferenceAdvanced = advancedFeedback
        }

        let now = nowMs()
        let endMs: UInt64
        if preserveElapsed, let previousEnd = uiState.restModalState?.endsAtMs, previousEnd > UInt64(now) {
            endMs = previousEnd
        } else {
            endMs = UInt64(now + Int64(seconds * 1000))
            restStartedAtMs = now
        }

        let alertCapability = repository.settings.soundsEnabled

        updateState { state in
            let activeExercise = visibleExercises(state).first(where: { $0.id == visibleExercises(state)[safe: state.currentExerciseIdx]?.id })
            let exName = activeExercise?.name ?? "Siguiente serie"
            if var rm = state.restModalState {
                rm.activeSeconds = seconds
                rm.endsAtMs = endMs
                rm.isManualOverride = preserveElapsed || rm.isManualOverride
                state.restModalState = rm
            } else {
                state.restModalState = WorkoutRestModalState(
                    exerciseId: activeExercise?.id,
                    exerciseName: exName,
                    kind: kind,
                    plannedSeconds: seconds,
                    suggestedSeconds: seconds,
                    activeSeconds: seconds,
                    endsAtMs: endMs,
                    isManualOverride: preserveElapsed
                )
            }
            state.restTimerTotal = seconds
            state.isRestTimerRunning = true
        }
        persistOngoingState()
        restTimerRemaining = seconds

        timerTask = Task { @MainActor in
            while true {
                try? await Task.sleep(nanoseconds: 500_000_000)
                guard !Task.isCancelled else { return }
                let remaining = max(0, Int((Int64(endMs) - nowMs() + 500) / 1000))
                restTimerRemaining = remaining
                if remaining <= 0 { break }
            }

            guard !Task.isCancelled else { return }
            updateState { $0.isRestTimerRunning = false; $0.restModalState = nil }
            persistOngoingState()
            restTimerRemaining = 0
            restRecovery = nil

            if advanceOnFinish {
                nextSet(stopRest: false)
            } else {
                let pending = uiState.pendingPostExerciseIdx
                let currentExIdx = uiState.currentExerciseIdx
                if pending >= 0 {
                    updateState {
                        $0.isRestTimerRunning = false
                        $0.restModalState = nil
                        $0.showPostExerciseSheet = true
                        $0.postExerciseTargetIdx = currentExIdx
                    }
                    persistOngoingState()
                } else {
                    updateState { $0.isRestTimerRunning = false; $0.restModalState = nil }
                    persistOngoingState()
                }
            }
        }
    }

    func addRestTime(_ seconds: Int) {
        timerTask?.cancel()
        let baseSeconds = restTimerRemaining > 0 ? restTimerRemaining : (uiState.restModalState?.activeSeconds ?? 0)
        let newTotal = max(0, baseSeconds + seconds)
        guard newTotal > 0 else { stopRestTimer(); return }

        let now = nowMs()
        let currentEnd = uiState.restModalState?.endsAtMs
        let newEndMs: UInt64 = {
            if let ce = currentEnd, ce > UInt64(now) { return ce + UInt64(Int64(seconds) * 1000) }
            return UInt64(now + Int64(newTotal * 1000))
        }()

        updateState {
            $0.restModalState = $0.restModalState?.copy(activeSeconds: newTotal, endsAtMs: newEndMs, isManualOverride: true)
        }
        startRestTimer(seconds: newTotal, lastSet: restReferenceSet, advancedFeedback: restReferenceAdvanced, preserveElapsed: true)
    }

    func stopRestTimer() {
        timerTask?.cancel()
        let pending = uiState.pendingPostExerciseIdx
        let currentExIdx = uiState.currentExerciseIdx
        updateState { state in
            state.isRestTimerRunning = false
            state.pendingRestSuggestion = nil
            state.restModalState = nil
            if pending >= 0 {
                state.showPostExerciseSheet = true
                state.postExerciseTargetIdx = currentExIdx
            }
        }
        persistOngoingState()
        restTimerRemaining = 0
        restRecovery = nil
    }

    func startSessionTimer(totalSeconds: Int) {
        sessionTimerTask?.cancel()
        var remaining = totalSeconds
        sessionTimerTask = Task { @MainActor in
            while remaining >= -3600 {
                updateState { $0.sessionTimeRemainingSeconds = remaining }
                if remaining == 300 { /* speak 5 min warning */ }
                if remaining == 60 { /* speak 1 min warning */ }
                if remaining == 0 { /* speak time up */ }
                self.checkPacingStatus()
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                remaining -= 1
            }
        }
    }

    func adjustSessionTimeLimit(_ minutes: Int) {
        let currentLimit = uiState.customTargetDurationMinutes ?? uiState.targetDurationMinutes ?? uiState.session?.targetDurationMinutes ?? 60
        let newLimit = max(5, currentLimit + minutes)
        let now = nowMs()
        let elapsedSeconds = max(0, (now - uiState.startTimeMs) / 1000)
        let newRemaining = newLimit * 60 - Int(elapsedSeconds)
        updateState {
            $0.customTargetDurationMinutes = newLimit
            $0.targetDurationMinutes = newLimit
            $0.sessionTimeRemainingSeconds = newRemaining
        }
        persistOngoingState()
        startSessionTimer(totalSeconds: newRemaining)
    }

    func handleTimerAction(_ action: TimerAction) {
        switch action {
        case .completeSet: nextSet(stopRest: true)
        case .skipTimer: stopRestTimer()
        case .addTime: addRestTime(15)
        case .subtractTime:
            if restTimerRemaining > 15 { addRestTime(-15) } else { stopRestTimer() }
        }
    }

    func clearContinuityTransitionTarget() {
        updateState { if $0.continuityTransitionTarget != nil { $0.continuityTransitionTarget = nil } }
    }

    func dismissContinuityFeedbackPrompt() {
        updateState { $0.continuityFeedbackExerciseId = nil }
    }

    // MARK: - Post-Exercise Sheet

    func requestPostExerciseFeedback(_ exerciseIdx: Int) {
        updateState { state in
            let exercise = visibleExercises(state).first(where: { $0.id == visibleExercises(state)[safe: exerciseIdx]?.id })
            state.showPostExerciseSheet = true
            state.postExerciseTargetIdx = exerciseIdx
            state.postExerciseFeedbackTarget = exercise.map { buildPostExerciseFeedbackTarget(state, exercise: $0) }
        }
    }

    func savePostExerciseFeedback(_ feedback: PostExerciseFeedback) {
        savePostExerciseFeedbacks([feedback])
    }

    func savePostExerciseFeedbacks(_ feedbacks: [PostExerciseFeedback]) {
        guard !feedbacks.isEmpty else { dismissPostExerciseSheet(); return }
        let feedbackIds = Set(feedbacks.map { $0.exerciseId })
        updateState {
            $0.completedSets = backfillCompletedSetIntensityFromPostExerciseFeedbacks(completedSets: $0.completedSets, feedbacks: feedbacks)
            $0.postExerciseFeedbackByExerciseId = $0.postExerciseFeedbackByExerciseId.merging(Dictionary(feedbacks.map { ($0.exerciseId, $0) }, uniquingKeysWith: { _, new in new })) { _, new in new }
            $0.showPostExerciseSheet = false
            $0.postExerciseTargetIdx = -1
            $0.postExerciseFeedbackTarget = nil
            if let cId = $0.continuityFeedbackExerciseId, feedbackIds.contains(cId) { $0.continuityFeedbackExerciseId = nil }
        }
        advanceAfterPostExerciseFeedback()
    }

    func dismissPostExerciseSheet() {
        updateState {
            $0.showPostExerciseSheet = false
            $0.postExerciseTargetIdx = -1
            $0.postExerciseFeedbackTarget = nil
        }
        advanceAfterPostExerciseFeedback()
    }

    private func advanceAfterPostExerciseFeedback() {
        let state = uiState
        let pending = state.pendingPostExerciseIdx
        if pending >= 0 {
            updateState { $0.currentExerciseIdx = pending; $0.pendingPostExerciseIdx = -1 }
        } else if pending == -2, state.isRestTimerRunning {
            updateState { $0.showPostExerciseSheet = false; $0.postExerciseTargetIdx = -1; $0.postExerciseFeedbackTarget = nil }
        } else if pending == -2 {
            updateState { $0.showFinishSheet = true; $0.pendingPostExerciseIdx = -1 }
        }
        persistOngoingState()
    }

    func dismissExecutionErrorDiscomfortSheet(_ discomfortIds: [String]) {
        let state = uiState
        guard let exercise = visibleExercises(state).first(where: { $0.id == visibleExercises(state)[safe: state.currentExerciseIdx]?.id }) else {
            updateState { $0.showExecutionErrorDiscomfortSheet = false }
            return
        }
        updateState {
            $0.postExerciseFeedbackByExerciseId[exercise.id] = PostExerciseFeedback(exerciseId: exercise.id, exerciseDbId: canonicalExerciseKey(exercise), exerciseName: exercise.name, technicalQuality: 5, discomfortIds: discomfortIds)
            $0.showExecutionErrorDiscomfortSheet = false
        }
        if let pending = state.pendingRestSuggestion {
            startRestTimer(seconds: pending.plannedSeconds, advanceOnFinish: false, lastSet: pending.lastSet, advancedFeedback: pending.advancedFeedback)
        }
    }

    // MARK: - Readiness Adjustments

    func saveReadinessAdjustments(neural: Int?, muscular: Int?, spinal: Int?, perMuscle: [String: Int], sleepQuality: Int? = nil) {
        updateState {
            $0.readinessNeuralOverride = neural
            $0.readinessMuscularOverride = muscular
            $0.readinessSpinalOverride = spinal
            $0.readinessMuscleOverrides = perMuscle
            $0.sleepQuality = sleepQuality
        }
        persistOngoingState()
    }

    func computeExerciseReadiness(batteries: GlobalBatteries, perMuscle: [String: MuscleRecoveryStatus]) {
        guard let exercises = uiState.session?.exercises else { return }
        var readinessMap: [String: ExerciseReadiness] = [:]
        for exercise in exercises {
            let canonicalId = canonicalExerciseKey(exercise)
            let avgErm: Double? = {
                let entries = getExerciseHistory(canonicalId, limit: 5)
                let valid = entries.compactMap { $0.e1rm }
                guard !valid.isEmpty else { return nil }
                return valid.reduce(0, +) / Double(valid.count)
            }()
            if let readiness = ExerciseReadinessEngine.calculatePerExerciseReadiness(exercise: exercise, augeBatteries: batteries, perMuscle: perMuscle, averageErm: avgErm) {
                readinessMap[exercise.id] = readiness
            }
        }
        let patterns = ExerciseReadinessEngine.calculatePerMovementPatternReadiness(exercises: exercises, exerciseReadinessMap: readinessMap, perMuscle: perMuscle)
        updateState {
            $0.exerciseReadinessMap = readinessMap
            $0.patternReadiness = patterns
        }
    }

    func applyReadinessAdjustment(_ exerciseId: String, setIndex: Int, suggestion: SetAdjustmentSuggestion) {
        let key = "\(exerciseId)_\(setIndex)"
        updateState { $0.readinessAdjustments[key] = suggestion }
    }

    // MARK: - Tags/Setup

    func setExerciseTag(_ exerciseId: String, tag: String) {
        let existingTags = tagsForExercise(exerciseId)
        if let match = existingTags.first(where: { $0.name == tag }) {
            toggleMainTagActive(exerciseId, tagId: match.id)
        } else {
            let created = createTag(exerciseId, name: tag)
            toggleMainTagActive(exerciseId, tagId: created.id)
        }
        updateState { $0.exerciseTags[exerciseId] = tag }
        persistOngoingState()
    }

    func clearExerciseTag(_ exerciseId: String) {
        clearAllTags(exerciseId)
        persistOngoingState()
    }

    // MARK: - History

    func showHistoryFor(_ exerciseDbId: String) {
        updateState { $0.showHistorySheet = true; $0.historySheetExerciseDbId = exerciseDbId }
    }

    func hideHistorySheet() {
        updateState { $0.showHistorySheet = false; $0.historySheetExerciseDbId = nil }
    }

    // MARK: - Finish Sheet

    func showFinish() {
        stopRestTimer()
        updateState { $0.showFinishSheet = true; $0.pendingRestSuggestion = nil; $0.editingState = nil }
    }

    func hideFinish() {
        updateState { $0.showFinishSheet = false }
        persistOngoingState()
    }

    func recoverFinishSheet() {
        if uiState.showFinishSheet {
            updateState { $0.showFinishSheet = false }
            updateState { $0.showFinishSheet = true }
        }
    }

    // MARK: - Finish Workout

    func finishWorkout(notes: String, fatigueLevel: Int, closingFeedback: SessionClosingFeedback, onPendingQuestionnaire: ((PendingQuestionnaire) -> Void)? = nil, onComplete: @escaping () -> Void = {}) {
        let state = uiState
        guard let session = state.session else { return }
        let durationMs = nowMs() - state.startTimeMs
        let durationMinutes = max(1, Int(durationMs / 60000))
        let activeSession = sessionForActiveMode(session, state.activeMode)
        let allExercises = activeSession.allExercises()

        let completedExercises: [CompletedExercise] = allExercises.compactMap { exercise -> CompletedExercise? in
            let sets = exercise.sets.indices.flatMap { setIdx -> [CompletedSet] in
                [state.completedSets["\(exercise.id)_\(setIdx)"],
                 state.completedSets["\(exercise.id)_\(setIdx)_L"],
                 state.completedSets["\(exercise.id)_\(setIdx)_R"]].compactMap { $0 }
            }
            guard !sets.isEmpty else { return nil }
            return CompletedExercise(
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                exerciseDbId: canonicalExerciseKey(exercise),
                canonicalExerciseId: exercise.canonicalExerciseId ?? canonicalExerciseKey(exercise),
                relativeToCanonicalExerciseId: exercise.relativeToCanonicalExerciseId,
                sets: sets,
                restTime: exercise.restTime ?? 90,
                supersetId: exercise.supersetGroupRefOrLegacyId(),
                supersetExerciseCount: exercise.supersetGroupRefOrLegacyId().map { SupersetRules.orderedMembers(activeSession, $0).count } ?? 1,
                supersetRounds: exercise.supersetGroupRefOrLegacyId().map { SupersetRules.roundCount(activeSession, $0) },
                supersetRestBetween: exercise.supersetRestBetween,
                supersetRestAfter: exercise.supersetRestAfter
            )
        }

        var totalVolume: Double = 0.0
        for ce in completedExercises {
            for set in ce.sets {
                totalVolume += set.weight * set.effectiveRepEquivalent()
            }
        }
        let logId = UUID().uuidString
        let drain = AugeFatigueEngine.calculateCompletedSessionDrain(completedExercises: completedExercises, exerciseDb: EXERCISE_DATABASE_BY_ID, settings: repository.settings)
        let base = AugeFatigueEngine.calculateCompletedSessionStress(completedExercises: completedExercises, exerciseDb: EXERCISE_DATABASE_BY_ID, settings: repository.settings)

        let predictedOverall = max(1.0, drain.cns * 0.45 + drain.muscular * 0.25 + drain.spinal * 0.30)
        let adjSystem = (drain.cns + Double(closingFeedback.systemAdjustment)).clamped(0, 100)
        let adjMuscular = (drain.muscular + Double(closingFeedback.muscularAdjustment)).clamped(0, 100)
        let adjStructure = (drain.spinal + Double(closingFeedback.structureAdjustment)).clamped(0, 100)
        let adjOverall = max(1.0, adjSystem * 0.45 + adjMuscular * 0.25 + adjStructure * 0.30)
        let impactFactor = adjOverall / predictedOverall

        let avgSetEffortSignal = calculateUnifiedSessionEffortSignal(completedExercises.flatMap { $0.sets })
        let avgTechValues = state.postExerciseFeedbackByExerciseId.values.map { Double($0.technicalQuality) }
        let avgTech = avgTechValues.isEmpty ? 8.0 : avgTechValues.reduce(0, +) / Double(avgTechValues.count)
        let techniqueQuality5 = max(1, min(5, Int(avgTech - 5.0)))
        let techniquePenalty = AugeFatigueEngine.calculateTechniquePenalty(technicalQuality: techniqueQuality5, effortSignal: avgSetEffortSignal).clamped(1.0, 1.5)
        let clarityFactor: Double = {
            if closingFeedback.clarityRating >= 8 { return 0.96 }
            if closingFeedback.clarityRating <= 4 { return 1.10 }
            return 1.0
        }()
        let stressScore = max(1.0, base * impactFactor * techniquePenalty * clarityFactor)

        let muscleGroups = completedExercises.compactMap { ex -> String? in
            let info = catalogInfoForCompletedExercise(ex)
            if let primary = info?.involvedMuscles.first(where: { $0.role == .PRIMARY }) {
                let canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, emphasis: primary.emphasis)
                return getAugeMuscleDisplayId(canonical, emphasis: primary.emphasis)
            }
            return ex.exerciseName
        }.distinct().prefix(6).map { $0 }

        let finalEnergySummary = TrainingEnergyEngine.estimateCompletedSession(completedExercises: completedExercises, settings: repository.settings, postExerciseFeedback: state.postExerciseFeedbackByExerciseId)

        let actualDate = ISO8601DateFormatter().string(from: Date()).prefix(10).description
        let scheduledDate = scheduledDateForSession(state.weekId, session: session)
        let scheduleDeltaDays: Int? = nil // Would compute from dates

        let log = WorkoutLog(
            id: logId,
            programId: programId,
            sessionId: sessionId,
            sessionName: session.name,
            date: nowIso(),
            scheduledDate: scheduledDate,
            actualDate: String(actualDate),
            scheduleDeltaDays: scheduleDeltaDays,
            durationMinutes: durationMinutes,
            completedExercises: completedExercises,
            fatigueLevel: fatigueLevel,
            discomforts: (closingFeedback.discomforts + state.postExerciseFeedbackByExerciseId.values.flatMap { $0.discomfortIds }.filter { $0 != "none" }).distinct(),
            notes: notes.isEmpty ? nil : notes,
            totalVolume: totalVolume,
            sessionStressScore: stressScore,
            weekId: state.weekId,
            macroIndex: state.macroIndex,
            mesoIndex: state.mesoIndex,
            clarityRating: closingFeedback.clarityRating,
            environmentTags: closingFeedback.environmentTags,
            planDeviations: state.planDeviations,
            exerciseTags: state.exerciseTags,
            contextualPerformanceStateV2: state.contextualPerformanceCache,
            globalPerformanceStateV3: state.globalPerformanceCache,
            contextProfilesV3: state.contextProfilesV3,
            postExerciseReports: state.postExerciseFeedbackByExerciseId.values.map { fb in
                ExerciseDiscomfortReport(exerciseId: fb.exerciseId, exerciseDbId: fb.exerciseDbId, canonicalExerciseId: fb.canonicalExerciseId, exerciseName: fb.exerciseName, technicalQuality: fb.technicalQuality, discomfortIds: fb.discomfortIds.filter { $0 != "none" }, notes: fb.notes, perceivedIntensityRpe: fb.perceivedIntensityRpe, perceivedFailure: fb.perceivedFailure)
            },
            omittedExercises: [],
            energySummary: finalEnergySummary,
            stillPresentDiscomfortIds: closingFeedback.stillPresentDiscomfortIds
        )

        repository.addWorkoutLog(log)
        updatePredictionBiasFromClosingFeedback(closingFeedback)
        repository.clearOngoingWorkout()

        onPendingQuestionnaire?(PendingQuestionnaire(logId: logId, sessionName: session.name, muscleGroups: muscleGroups, stillPresentDiscomfortIds: closingFeedback.stillPresentDiscomfortIds, scheduledTimeMs: nowMs() + 86400000))

        let deltas = computeWorkoutVolumeDelta(state.session ?? session, completedSets: state.completedSets)
        if !deltas.isEmpty {
            deferredOnComplete = onComplete
            updateState {
                $0.pendingVolumeAdvances = deltas
                $0.showVolumeAdvanceModal = true
                $0.showFinishSheet = false
            }
            return
        }

        updateState { $0.isComplete = true; $0.showFinishSheet = false; $0.sessionStressScore = stressScore }
        onComplete()
    }

    func cancelWorkout() {
        sessionTimerTask?.cancel()
        timerTask?.cancel()
        repository.clearOngoingWorkout()
        uiState = WorkoutUiState()
        restTimerRemaining = 0
        restRecovery = nil
    }

    func acceptVolumeAdvance() {
        updateState { $0.pendingVolumeAdvances = []; $0.showVolumeAdvanceModal = false; $0.isComplete = true }
        repository.clearOngoingWorkout()
        deferredOnComplete?()
        deferredOnComplete = nil
    }

    func dismissVolumeAdvance() {
        let cb = deferredOnComplete
        deferredOnComplete = nil
        updateState { $0.pendingVolumeAdvances = []; $0.showVolumeAdvanceModal = false; $0.isComplete = true }
        repository.clearOngoingWorkout()
        cb?()
    }

    func toggleRestMinimized() {
        updateState { $0.isRestMinimized = !$0.isRestMinimized }
    }

    func commitStructuralPersistence(_ scope: ReplacementPersistenceScopeV2) {
        updateState { $0.pendingStructuralPersistence = nil }
    }

    func persistExerciseChangesToPlan(_ exerciseId: String) {}

    func persistExerciseChangesToBlock(_ exerciseId: String) {}

    func clearPendingEditSheetExerciseId() { updateState { $0.pendingEditSheetExerciseId = nil } }

    func clearPendingStructuralPersistence() { updateState { $0.pendingStructuralPersistence = nil } }

    // MARK: - Superset

    func createLiveSuperset(_ exerciseIds: [String], partId: String? = nil, restBetween: Int = 60, restAfter: Int = 120) {
        let state = uiState
        guard let base = state.session else { return }
        let targetIds = Array(Set(exerciseIds))
        guard targetIds.count >= 2 else { return }
        let groupId = UUID().uuidString
        let updatedSession = withModeSession(base, state.activeMode) { modeSession in
            SupersetRules.createSuperset(session: modeSession, groupId: groupId, exerciseIds: targetIds, restBetweenExercises: restBetween, restAfterSuperset: restAfter, rounds: nil, anchorPartId: partId, anchorExerciseId: targetIds.first)
        }
        guard updatedSession != base else { return }
        applySessionMutation(updatedSession, preferredExerciseId: targetIds.first)
    }

    func dissolveLiveSuperset(_ groupId: String, preferredExerciseId: String? = nil) {
        let state = uiState
        guard let base = state.session else { return }
        let updatedSession = withModeSession(base, state.activeMode) { SupersetRules.dissolve($0, groupId) }
        guard updatedSession != base else { return }
        applySessionMutation(updatedSession, preferredExerciseId: preferredExerciseId)
    }

    func updateLiveSupersetRest(_ groupId: String, restBetween: Int?, restAfter: Int?, rounds: Int?) {
        let state = uiState
        guard let base = state.session else { return }
        let currentExerciseId = visibleExercises(state)[safe: state.currentExerciseIdx]?.id
        let updatedSession = withModeSession(base, state.activeMode) { modeSession in
            SupersetRules.updateRest(session: modeSession, groupId: groupId, restBetweenExercises: restBetween, restAfterSuperset: restAfter, rounds: rounds)
        }
        guard updatedSession != base else { return }
        applySessionMutation(updatedSession, preferredExerciseId: currentExerciseId)
    }

    func moveExercise(_ exerciseId: String, direction: Int) {
        let state = uiState
        guard let base = state.session else { return }
        let updatedSession = withModeSession(base, state.activeMode) { $0.moveExerciseById(exerciseId, direction: direction) }
        guard updatedSession != base else { return }
        updateState {
            $0.session = updatedSession
            let newIdx = visibleExercises($0.copy(session: updatedSession)).firstIndex(where: { $0.id == exerciseId })
            if let idx = newIdx { $0.currentExerciseIdx = idx }
        }
        persistOngoingState()
    }

    func reorderExercises(_ partId: String?, orderedExerciseIds: [String]) {
        let state = uiState
        guard let base = state.session else { return }
        let currentExerciseId = visibleExercises(state)[safe: state.currentExerciseIdx]?.id
        let updatedSession = withModeSession(base, state.activeMode) { $0.reorderExercisesByIds(partId, orderedExerciseIds: Array(Set(orderedExerciseIds))) }
        guard updatedSession != base else { return }
        applySessionMutation(updatedSession, preferredExerciseId: currentExerciseId)
    }

    func reorderExercisesPreservingParts(_ orderedExerciseIds: [String]) {
        let state = uiState
        guard let base = state.session else { return }
        let currentExerciseId = visibleExercises(state)[safe: state.currentExerciseIdx]?.id
        let updatedSession = withModeSession(base, state.activeMode) { modeSession in
            if modeSession.parts.isEmpty {
                let lookup = Dictionary(uniqueKeysWithValues: modeSession.exercises.map { ($0.id, $0) })
                let reordered = orderedExerciseIds.compactMap { lookup[$0] }
                return reordered == modeSession.exercises ? modeSession : modeSession.copy(exercises: reordered)
            } else {
                var changed = false
                let newParts = modeSession.parts.map { part -> SessionPart in
                    let partOrdered = orderedExerciseIds.filter { id in part.exercises.contains(where: { $0.id == id }) }
                    guard partOrdered.count == part.exercises.count else { return part }
                    let lookup = Dictionary(uniqueKeysWithValues: part.exercises.map { ($0.id, $0) })
                    let reordered = partOrdered.compactMap { lookup[$0] }
                    if reordered != part.exercises { changed = true; return part.copy(exercises: reordered) }
                    return part
                }
                return changed ? modeSession.copy(parts: newParts) : modeSession
            }
        }
        guard updatedSession != base else { return }
        applySessionMutation(updatedSession, preferredExerciseId: currentExerciseId)
    }

    func reorderExercisesGlobally(_ orderedExerciseIds: [String], originalPartMap: [String: String]) {
        let state = uiState
        guard let base = state.session else { return }
        let currentExerciseId = visibleExercises(state)[safe: state.currentExerciseIdx]?.id
        let updatedSession = withModeSession(base, state.activeMode) { $0.globalReorder(Array(Set(orderedExerciseIds)), originalPartMap) }
        guard updatedSession != base else { return }
        applySessionMutation(updatedSession, preferredExerciseId: currentExerciseId)
    }

    func applyReorderAndPromptPersistence(_ orderedExerciseIds: [String], originalPartMap: [String: String], isGlobal: Bool) {
        if isGlobal { reorderExercisesGlobally(orderedExerciseIds, originalPartMap: originalPartMap) }
        else { reorderExercisesPreservingParts(orderedExerciseIds) }
        updateState { $0.pendingStructuralPersistence = .reorderExercises(orderedExerciseIds: Array(Set(orderedExerciseIds)), originalPartMap: originalPartMap, isGlobal: isGlobal) }
    }

    func updateExerciseDefinition(_ exerciseId: String, transform: (Exercise) -> Exercise) {
        let state = uiState
        guard let base = state.session else { return }
        let updatedSession = withModeSession(base, state.activeMode) { modeSession in
            modeSession.replaceExerciseById(exerciseId) { WorkoutEditingRules.normalizeLiveEditedExercise(transform($0)) }
        }
        guard updatedSession != base else { return }
        applySessionMutation(updatedSession, preferredExerciseId: exerciseId)
    }

    func addSetToCurrentExercise() {
        guard let currentExerciseId = visibleExercises(uiState)[safe: uiState.currentExerciseIdx]?.id else { return }
        let exerciseName = visibleExercises(uiState)[safe: uiState.currentExerciseIdx]?.name ?? ""
        updateExerciseDefinition(currentExerciseId) { exercise in
            let lastSet = exercise.sets.last
            let lastSetIdx = exercise.sets.count - 1
            let effectiveMode = effectiveLoadModeForExercise(exercise, setIdx: lastSetIdx)
            let newSet = ExerciseSet(
                id: UUID().uuidString,
                targetReps: lastSet?.targetReps,
                targetDuration: lastSet?.targetDuration,
                targetRPE: lastSet?.targetRPE,
                targetRIR: lastSet?.targetRIR,
                intensityMode: lastSet?.intensityMode,
                targetPercentageRM: lastSet?.targetPercentageRM,
                weight: lastSet?.weight,
                isAmrap: false,
                loadModeV2: effectiveMode,
                unitModeV2: lastSet?.unitModeV2
            )
            return exercise.copy(sets: exercise.sets + [newSet])
        }
        updateState { $0.pendingStructuralPersistence = .addSet(exerciseId: currentExerciseId, exerciseName: exerciseName) }
    }

    func addExerciseAfter(_ exerciseId: String, info: ExerciseMuscleInfo) {
        let state = uiState
        guard let base = state.session else { return }
        let newId = UUID().uuidString
        let updatedSession = withModeSession(base, state.activeMode) { modeSession in
            let template = modeSession.allExercises().first(where: { $0.id == exerciseId }) ?? modeSession.allExercises().last
            guard let template = template else { return modeSession }
            let newExercise = buildReplacementExercise(template.copy(id: newId), info).copy(id: newId, sets: [ExerciseSet(id: UUID().uuidString)])
            return insertExerciseAfter(modeSession, currentExerciseId: exerciseId, newExercise: newExercise)
        }
        guard updatedSession != base else { return }
        applySessionMutation(updatedSession, preferredExerciseId: newId)
        updateState {
            $0.pendingEditSheetExerciseId = newId
            $0.pendingStructuralPersistence = .addExercise(afterExerciseId: exerciseId, newExerciseId: newId, newExerciseName: info.name)
        }
    }

    func addMobilityExerciseToSession(_ name: String, durationSeconds: Int = 60) {
        let mobilityExercise = Exercise(
            id: UUID().uuidString,
            name: name,
            exerciseDbId: "mobility_custom_\(UUID().uuidString)",
            sets: [ExerciseSet(id: UUID().uuidString, targetDuration: durationSeconds, unitModeV2: .TIME)],
            restTime: 30,
            trainingMode: .TIME
        )
        updateState { state in
            guard var session = state.session else { return }
            if session.parts.isEmpty {
                session = session.copy(exercises: [mobilityExercise] + session.exercises)
            } else {
                session = session.copy(parts: session.parts.enumerated().map { idx, part in
                    idx == 0 ? part.copy(exercises: [mobilityExercise] + part.exercises) : part
                })
            }
            state.session = session
        }
        persistOngoingState()
    }

    func setActiveMode(_ mode: WeekVariant) {
        let state = uiState
        guard state.activeMode != mode else { return }
        let resolved = resolveResumePosition(exercises: visibleExercises(state.copy(activeMode: mode)), completedSets: state.completedSets, preferredExerciseId: nil, preferredSetId: nil)
        updateState {
            $0.activeMode = mode
            $0.currentExerciseIdx = resolved.0
            $0.currentSetIdx = resolved.1
        }
        persistOngoingState()
    }

    // MARK: - Ghost Performance

    func getGhostForSet(_ exerciseId: String, setIdx: Int, exerciseDbId: String? = nil, activeTag: String? = nil) -> CompletedSet? {
        if let dbId = exerciseDbId, !dbId.isEmpty {
            let candidates = repository.history.filter { log in
                log.completedExercises.contains { $0.resolvedCanonicalExerciseId() == dbId }
            }.sorted { $0.date > $1.date }
            let preferred = activeTag.flatMap { tag in
                candidates.first { log in
                    log.completedExercises.contains { $0.resolvedCanonicalExerciseId() == dbId } && log.exerciseTags.values.contains(tag)
                }
            }
            let ghost = (preferred ?? candidates.first)?.completedExercises.first(where: { $0.resolvedCanonicalExerciseId() == dbId })?.sets[safe: setIdx]
            if let g = ghost { return g }
        }
        return lastLog?.completedExercises.first(where: { $0.exerciseId == exerciseId })?.sets[safe: setIdx]
    }

    // MARK: - Exercise History

    func getExerciseHistory(_ exerciseDbId: String, limit: Int = 10, preferredTag: String? = nil) -> [ExerciseHistoryEntry] {
        let all = repository.history.filter { log in
            log.completedExercises.contains { $0.resolvedCanonicalExerciseId() == exerciseDbId }
        }.sorted { $0.date > $1.date }

        let tagged = preferredTag.map { tag in
            all.filter { log in
                log.completedExercises.contains { $0.resolvedCanonicalExerciseId() == exerciseDbId } && log.exerciseTags.values.contains(tag)
            }
        } ?? []

        let ordered = Array(Set(tagged + all.filter { !tagged.contains($0) })).prefix(limit)

        return ordered.compactMap { log -> ExerciseHistoryEntry? in
            guard let ex = log.completedExercises.first(where: { $0.resolvedCanonicalExerciseId() == exerciseDbId }) else { return nil }
            let best1rm = ex.sets.filter { !$0.isWarmup && $0.weight > 0 && $0.reps > 0 }.map { calculateHybrid1RM($0.weight, reps: $0.reps) }.max()
            let latestOutcome = ex.sets.reversed().first(where: { $0.setOutcomeV2 != nil })?.setOutcomeV2
            return ExerciseHistoryEntry(
                date: log.date,
                sets: ex.sets,
                e1rm: best1rm,
                tag: log.exerciseTags[ex.exerciseId],
                latestHistoryColor: latestOutcome?.historyColor,
                latestMetricType: latestOutcome?.metricType,
                latestMetricValue: latestOutcome?.metricValue
            )
        }
    }

    func latestCompletedSessionSnapshot() -> WorkoutShareSnapshot? {
        guard let last = repository.getLogsForSession(sessionId).max(by: { $0.date < $1.date }) else { return nil }
        let allSets = last.completedExercises.flatMap { $0.sets }
        let best = allSets.filter { $0.weight > 0 && $0.reps > 0 }.map { calculateHybrid1RM($0.weight, reps: $0.reps) }.max()
        return WorkoutShareSnapshot(totalVolume: last.totalVolume, totalSets: allSets.count, durationMinutes: last.durationMinutes, bestEstimated1RM: best)
    }

    // MARK: - Weight Suggestion

    func getWeightSuggestion(_ exercise: Exercise, setIdx: Int, activeTag: String? = nil) -> WeightSuggestion? {
        let dbId = canonicalExerciseKey(exercise)
        let loadMode = effectiveLoadModeForExercise(exercise, setIdx: setIdx)
        guard loadMode != .BODYWEIGHT else { return WeightSuggestion(suggestedWeight: 0, reason: "Peso corporal", suggestedLoadMode: .BODYWEIGHT) }

        let history = getExerciseHistory(dbId, limit: 5, preferredTag: activeTag)
        guard !history.isEmpty else {
            if let refWeight = exercise.consolidatedWeight?.weight ?? exercise.sets[safe: setIdx]?.weight ?? exercise.sets[safe: setIdx]?.consolidatedWeight, refWeight > 0 {
                return WeightSuggestion(suggestedWeight: refWeight, reason: "Del programa")
            }
            return nil
        }

        let baseEntry = history.first(where: { $0.tag == activeTag }) ?? history[0]
        let lastSet = baseEntry.sets.filter { !$0.isWarmup }[safe: setIdx] ?? baseEntry.sets.filter { !$0.isWarmup }.last

        if let ls = lastSet, ls.weight > 0 {
            let lastSetWeight = inputLoadForSuggestion(ls, loadMode: loadMode)
            let suggestedWeight: Double
            if ls.isFailedSet || ls.isFailure { suggestedWeight = lastSetWeight * 0.95 }
            else if ls.reps > 0, let target = exercise.sets[safe: setIdx]?.targetReps, target <= ls.reps { suggestedWeight = lastSetWeight * 1.025 }
            else { suggestedWeight = lastSetWeight }
            return WeightSuggestion(suggestedWeight: (suggestedWeight * 2).rounded() / 2, reason: "Última sesión")
        }
        return nil
    }

    func getWeightSuggestionWithAutoRegulation(_ exercise: Exercise, setIdx: Int, activeTag: String? = nil, side: String? = nil) -> WeightSuggestion? {
        let currentLoadMode = effectiveLoadModeForExercise(exercise, setIdx: setIdx)
        guard currentLoadMode != .BODYWEIGHT else { return WeightSuggestion(suggestedWeight: 0, reason: "Peso corporal · progresa por reps o tiempo", suggestedLoadMode: .BODYWEIGHT) }

        let baseSuggestion = getWeightSuggestion(exercise, setIdx: setIdx, activeTag: activeTag)
        let plannedFallback = plannedWorkingWeightForSet(exercise, setIdx: setIdx)
        let baseWorkingWeight = [plannedFallback, baseSuggestion?.suggestedWeight].compactMap { $0 }.first(where: { $0 > 0 }) ?? 0
        guard baseWorkingWeight > 0 else { return nil }

        let adjustedWeight = roundWorkoutLoadSuggestion(baseWorkingWeight)
        return WeightSuggestion(suggestedWeight: adjustedWeight, reason: baseSuggestion?.reason ?? "Del programa", suggestedLoadMode: currentLoadMode)
    }

    func getWarmupWorkingWeightAnchor(_ exercise: Exercise, activeTag: String? = nil) -> Double? {
        let suggested = getWeightSuggestionWithAutoRegulation(exercise, setIdx: 0, activeTag: activeTag, side: nil)?.suggestedWeight
        if let s = suggested, s > 0 { return s }
        if let pw = plannedWorkingWeightForSet(exercise, setIdx: 0), pw > 0 { return pw }
        let referenceRm = exercise.reference1RM ?? exercise.prFor1RM.flatMap { $0.weight > 0 && $0.reps > 0 ? calculateHybrid1RM($0.weight, reps: $0.reps) : nil }
        if let ref = referenceRm, let pct = exercise.sets.first?.targetPercentageRM, pct > 0 {
            return coerceLoadStep(ref * pct / 100)
        }
        return nil
    }

    // MARK: - Readiness

    private func computeAndStoreAutoRegulation(completedSet: CompletedSet, advanced: SetAdvancedFeedback, setDrain: SetDrain, effectiveRpe: Double, sessionProgress: Double) {
        guard !uiState.showFinishSheet else { return }
        let allExercises = visibleExercises(uiState)
        guard let nextExercise = allExercises[safe: uiState.currentExerciseIdx] else { return }
        let nextSetIdx = uiState.currentSetIdx

        let weightedDrain = setDrain.cnsDrainPct * 0.45 + setDrain.muscularDrainPct * 0.25 + setDrain.spinalDrainPct * 0.30
        let adjustmentFactor = WorkoutAutoRegulation.computeAdjustmentFactor(weightedDrainPct: weightedDrain, effectiveRpe: effectiveRpe, reachedFailure: advanced.reachedFailure, isFailedSet: advanced.isFailedSet, isPartial: advanced.isPartial, sessionProgress: sessionProgress)

        let nextLoadMode = effectiveLoadModeForExercise(nextExercise, setIdx: nextSetIdx)
        let rawWeight = nextExercise.sets[safe: nextSetIdx]?.weight ?? completedSet.weight
        let adjustedWeight: Double
        if nextLoadMode == .ASSISTED { adjustedWeight = rawWeight / max(0.70, adjustmentFactor) }
        else { adjustedWeight = rawWeight * adjustmentFactor }

        let regulation = SetAutoRegulation(exerciseId: nextExercise.id, nextSetIdx: nextSetIdx, adjustmentFactor: adjustmentFactor, adjustedWeight: roundWorkoutLoadSuggestion(max(0, adjustedWeight)), reason: WorkoutAutoRegulation.buildReason(factor: adjustmentFactor, weightedDrainPct: weightedDrain, effectiveRpe: effectiveRpe, reachedFailure: advanced.reachedFailure))
        currentAutoRegulation = regulation
        updateState { $0.currentAutoRegulation = regulation }
    }

    private func updateCoachMessage(setDrain: SetDrain, sessionProgress: Double) {
        let state = uiState
        let weightedDrain = setDrain.cnsDrainPct * 0.45 + setDrain.muscularDrainPct * 0.25 + setDrain.spinalDrainPct * 0.30
        let readinessScore = WorkoutCoachMessages.getReadinessScore(neural: state.readinessNeuralOverride, spinal: state.readinessSpinalOverride, muscular: state.readinessMuscularOverride)
        let message = WorkoutCoachMessages.getMessage(weightedDrainPct: weightedDrain, readinessScore: readinessScore, sessionProgress: sessionProgress)
        currentCoachMessage = message
        updateState { $0.currentCoachMessage = message }
    }

    func shouldShowRealtimeRingsWidget() -> Bool { uiState.featureFlags.workoutV2HeaderWidgets && uiState.headerWidgets.showRealtimeRings }
    func shouldShowRmCalculatorWidget() -> Bool { uiState.featureFlags.workoutV2HeaderWidgets && uiState.headerWidgets.showRmCalculator }

    func estimateBrzycki1RM(_ weight: Double, reps: Int) -> Double? {
        guard reps > 0, reps < 37, weight > 0 else { return nil }
        return weight * (36.0 / (37.0 - Double(reps)))
    }

    // MARK: - Private Helpers

    private func persistOngoingState(_ state: WorkoutUiState? = nil) {
        let s = state ?? uiState
        guard let session = s.session else { return }
        let visible = visibleExercises(s)
        let activeExercise = visible[safe: s.currentExerciseIdx]
        repository.updateOngoingWorkout { ongoing in
            guard var current = ongoing, current.programId == programId, current.session.id == sessionId else { return }
            current = current.copy(
                session: session,
                activeExerciseId: activeExercise?.id,
                activeSetId: activeExercise?.sets[safe: s.currentSetIdx]?.id,
                activeSetIndex: s.currentSetIdx,
                activeExerciseIndex: s.currentExerciseIdx,
                activeStepKey: s.activeStepKey,
                activeMode: s.activeMode,
                completedSets: s.completedSets,
                dynamicWeights: s.loadSuggestions.mapValues { $0.suggestedWeight },
                loadSuggestionReasons: s.loadSuggestions.mapValues { $0.reason },
                exerciseTags: s.exerciseTags,
                activeTags: s.activeTagsByExercise,
                activeSubTags: s.activeSubTagsByExercise,
                userCreatedTags: s.userCreatedTags,
                contextProfilesV3: s.contextProfilesV3,
                activeContextProfileByExerciseId: s.activeContextProfileByExerciseId,
                skippedExerciseIds: s.skippedExerciseIds,
                warmupCompletedExerciseIds: s.warmupCompletedExerciseIds,
                mobilityCompletedExerciseIds: s.mobilityCompletedExerciseIds,
                readinessNeuralOverride: s.readinessNeuralOverride,
                readinessMuscularOverride: s.readinessMuscularOverride,
                readinessSpinalOverride: s.readinessSpinalOverride,
                readinessMuscleOverrides: s.readinessMuscleOverrides,
                restModalState: s.restModalState,
                persistedLoadModeBySet: s.persistedLoadModeBySet,
                persistedLoadModeByExercise: s.persistedLoadModeByExercise,
                customTargetDurationMinutes: s.customTargetDurationMinutes
            )
            ongoing = current
        }
    }

    private func refreshAllUserTags() {
        var tags = Set<String>()
        for tagList in uiState.userCreatedTags.values {
            for tag in tagList {
                if !tag.name.isEmpty { tags.insert(tag.name) }
            }
        }
        for log in repository.history {
            for ex in log.completedExercises {
                for set in ex.sets { if let t = set.tagId, !t.isEmpty { tags.insert(t) } }
            }
            for tag in log.exerciseTags.values { if !tag.isEmpty { tags.insert(tag) } }
        }
        allUserTags = tags.filter { !$0.isEmpty }.sorted()
    }

    private func buildPostExerciseFeedbackTarget(_ state: WorkoutUiState, exercise: Exercise) -> PostExerciseFeedbackTarget {
        let visible = visibleExercises(state)
        if let groupId = exercise.supersetGroupRefOrLegacyId() {
            let members = visible.filter { $0.supersetGroupRefOrLegacyId() == groupId }
            if members.count > 1, members.allSatisfy({ isExerciseCompleteInSteps(state, exercise: $0) }) {
                return .supersetGroup(groupId: groupId, exerciseIds: members.map { $0.id })
            }
        }
        return .single(exerciseId: exercise.id)
    }

    private func isExerciseCompleteInSteps(_ state: WorkoutUiState, exercise: Exercise) -> Bool {
        let visible = visibleExercises(state)
        let steps = workoutStepPositions(state).filter { $0.exerciseId == exercise.id }
        guard !steps.isEmpty else {
            return exercise.sets.indices.allSatisfy { isSetDone(completedSets: state.completedSets, exerciseId: exercise.id, setIdx: $0, isUnilateral: exercise.isEffectivelyUnilateral()) }
        }
        return steps.allSatisfy { isWorkoutStepDone($0, visible: visible, completedSets: state.completedSets) }
    }

    private func isWorkoutStepDone(_ step: WorkoutStep, visible: [Exercise], completedSets: [String: CompletedSet]) -> Bool {
        switch step.type {
        case .MOBILITY:
            guard let mobilityId = step.mobilitySeriesId else { return true }
            return uiState.mobilityCompletedExerciseIds.contains(WorkoutStepRules.mobilityStepKey(step.exerciseId, mobilityId))
        case .WARMUP:
            guard let warmupId = step.warmupSetId else { return true }
            return uiState.warmupCompletedExerciseIds.contains(step.exerciseId) || uiState.warmupCompletedExerciseIds.contains(WorkoutStepRules.warmupStepKey(step.exerciseId, warmupId))
        case .WORKING_SET:
            guard let setIdx = step.setIndex, let exercise = visible.first(where: { $0.id == step.exerciseId }) else { return true }
            if exercise.isEffectivelyUnilateral(), let side = step.side {
                return completedSets[buildCompletedSetKey(exercise.id, setIdx, side)] != nil
            }
            return isSetDone(completedSets: completedSets, exerciseId: exercise.id, setIdx: setIdx, isUnilateral: exercise.isEffectivelyUnilateral())
        }
    }

    private func firstIncompleteStepForExercise(_ state: WorkoutUiState, exercise: Exercise) -> WorkoutStep? {
        let visible = visibleExercises(state)
        return workoutStepPositions(state).first(where: { step in
            step.exerciseId == exercise.id && !isWorkoutStepDone(step, visible: visible, completedSets: state.completedSets)
        })
    }

    private func nextIncompleteStepAfter(_ state: WorkoutUiState, includeCurrent: Bool = false) -> WorkoutStep? {
        let visible = visibleExercises(state)
        let steps = workoutStepPositions(state)
        guard !steps.isEmpty else { return nil }
        let currentStepIdx = stepPositionIndex(steps, visible: visible, exerciseIdx: state.currentExerciseIdx, setIdx: state.currentSetIdx, activeStepKey: state.activeStepKey)
        let start: Int
        if currentStepIdx < 0 { start = 0 }
        else if includeCurrent { start = currentStepIdx }
        else { start = currentStepIdx + 1 }
        return Array(steps.dropFirst(start)).first(where: { !isWorkoutStepDone($0, visible: visible, completedSets: state.completedSets) })
    }

    private func previousStepBefore(_ state: WorkoutUiState) -> WorkoutStep? {
        let visible = visibleExercises(state)
        let steps = workoutStepPositions(state)
        let idx = stepPositionIndex(steps, visible: visible, exerciseIdx: state.currentExerciseIdx, setIdx: state.currentSetIdx, activeStepKey: state.activeStepKey)
        guard idx > 0 else { return nil }
        return Array(steps.prefix(idx)).last
    }

    private func workoutStepPositions(_ state: WorkoutUiState) -> [WorkoutStep] {
        guard let baseSession = state.session else { return [] }
        let modeSession = sessionForActiveMode(baseSession, state.activeMode)
        return WorkoutStepRules.buildSteps(session: modeSession, visibleExercises: visibleExercises(state))
    }

    private func stepPositionIndex(_ steps: [WorkoutStep], visible: [Exercise], exerciseIdx: Int, setIdx: Int, activeStepKey: String?) -> Int {
        if let key = activeStepKey, !key.isEmpty {
            if let idx = steps.firstIndex(where: { $0.stepKey == key }) { return idx }
        }
        guard let exerciseId = visible[safe: exerciseIdx]?.id else { return -1 }
        return steps.firstIndex(where: { $0.type == .WORKING_SET && $0.exerciseId == exerciseId && $0.setIndex == setIdx }) ?? -1
    }

    private func openFinishSheet() {
        updateState {
            $0.showFinishSheet = true
            $0.postExerciseTargetIdx = -1
            $0.postExerciseFeedbackTarget = nil
            $0.pendingPostExerciseIdx = -1
            $0.showPostExerciseSheet = false
            $0.pendingRestSuggestion = nil
            $0.restModalState = nil
            $0.editingState = nil
            $0.continuityTransitionTarget = nil
            $0.continuityFeedbackExerciseId = nil
            $0.isRestTimerRunning = false
        }
        persistOngoingState()
    }

    private func skipExerciseAndAdvance(_ state: WorkoutUiState, exerciseId: String) {
        let updatedSkips = state.skippedExerciseIds.union([exerciseId])
        let visible = visibleExercises(state.copy(skippedExerciseIds: updatedSkips))
        let resolvedIdx = min(state.currentExerciseIdx, max(0, visible.count - 1))
        let resolvedSetIdx = 0
        updateState {
            $0.skippedExerciseIds = updatedSkips
            $0.currentExerciseIdx = resolvedIdx
            $0.currentSetIdx = resolvedSetIdx
        }
        persistOngoingState()
        if nextIncompleteStepAfter(uiState) == nil { openFinishSheet() }
    }

    private func clearDraftForSet(_ exerciseId: String, setIdx: Int, side: String?) {
        let exactKey = workoutSetKey(exerciseId: exerciseId, setIdx: setIdx, side: side)
        let fallbackKey = side != nil ? workoutSetKey(exerciseId: exerciseId, setIdx: setIdx) : nil
        updateState {
            $0.setDrafts.removeValue(forKey: exactKey)
            if let fk = fallbackKey { $0.setDrafts.removeValue(forKey: fk) }
        }
    }

    private func persistLoadModeToProfile(_ exerciseId: String, loadMode: LoadModeV2) {
        let state = uiState
        guard let profileId = state.activeContextProfileByExerciseId[exerciseId],
              var profile = state.contextProfilesV3[profileId],
              profile.loadMode != loadMode else { return }
        profile = profile.copy(loadMode: loadMode, lastUsedAtIso: nowIso())
        repository.upsertContextProfile(profile)
        updateState {
            if let id = profile.id {
                $0.contextProfilesV3[id] = profile
            }
        }
    }

    private func registerManualLoadOverride(_ exerciseId: String, setIdx: Int, side: String?, load: Double) {
        let key = workoutSetKey(exerciseId: exerciseId, setIdx: setIdx, side: side)
        updateState { $0.manualLoadOverrides[key] = max(0, load) }
    }

    private func manualOverrideForSet(_ exerciseId: String, setIdx: Int, side: String? = nil) -> Double? {
        let key = workoutSetKey(exerciseId: exerciseId, setIdx: setIdx, side: side)
        if let v = uiState.manualLoadOverrides[key] { return v }
        if side != nil { return uiState.manualLoadOverrides[workoutSetKey(exerciseId: exerciseId, setIdx: setIdx)] }
        return nil
    }

    private func buildEditingStateForPosition(completedSets: [String: CompletedSet], exercise: Exercise?, setIdx: Int, preferredSide: String? = nil) -> WorkoutEditingState? {
        guard let exercise = exercise else { return nil }
        return WorkoutEditingRules.buildEditingState(completedSets: completedSets, exercise: exercise, setIdx: setIdx, preferredSide: preferredSide)
    }

    private func applyAssistedAdjustment(_ baseAssistance: Double, factor: Double) -> Double {
        let clampedFactor = factor.clamped(0.60, 1.50)
        let adjusted = clampedFactor > 0.0 ? baseAssistance / clampedFactor : baseAssistance
        return adjusted.clamped(0.5, baseAssistance * 1.50)
    }

    private func coerceLoadStep(_ weight: Double) -> Double {
        guard weight > 0 else { return 0 }
        return max(0.5, (weight / 0.5).rounded() * 0.5)
    }

    private func roundWorkoutLoadSuggestion(_ weight: Double) -> Double { coerceLoadStep(weight) }

    private func inputLoadForSuggestion(_ set: CompletedSet, loadMode: LoadModeV2) -> Double {
        let payload = set.recordedPayloadV3
        switch loadMode {
        case .ASSISTED: return payload?.assistedLoad ?? set.weight
        default: return payload?.externalLoad ?? set.weight
        }
    }

    private func plannedWorkingWeightForSet(_ exercise: Exercise, setIdx: Int) -> Double? {
        guard let set = exercise.sets[safe: setIdx] else { return nil }
        return calculateSuggestedLoad(exercise, set: set) ?? set.weight ?? set.consolidatedWeight ?? exercise.consolidatedWeight?.weight
    }

    private func refreshLoadSuggestions(_ trackPulses: Bool = true) {
        // placeholder - would rebuild all load suggestions
    }

    private func plannedRestForKind(_ restKind: RestTimerKind, exercise: Exercise, supersetGroup: SupersetGroup?, targetSetIdx: Int, baseRest: Int) -> Int {
        switch restKind {
        case .BETWEEN_SIDES: return exercise.restBetweenSidesSeconds ?? 0
        case .SUPERSET_INTRA: return supersetGroup?.roundRestBetweenExercises[targetSetIdx] ?? supersetGroup?.restBetweenExercises ?? exercise.supersetRestBetween ?? baseRest
        case .SUPERSET_ROUND: return supersetGroup?.roundRestAfterSuperset[targetSetIdx] ?? supersetGroup?.restAfterSuperset ?? exercise.supersetRestAfter ?? baseRest
        case .WARMUP, .STANDARD: return baseRest
        }
    }

    private func shouldConfirmAdaptiveRestChange(_ baseRest: Int, _ adaptiveRest: Int) -> Bool {
        guard adaptiveRest > 0, baseRest > 0, adaptiveRest != baseRest else { return false }
        return abs(adaptiveRest - baseRest) >= 15
    }

    private func computeWorkoutVolumeDelta(_ plannedSession: Session, completedSets: [String: CompletedSet]) -> [MuscleAdvance] {
        guard !programId.isEmpty else { return [] }
        var plannedPerMuscle: [String: Double] = [:]
        for ex in plannedSession.allExercises() {
            guard let dbId = ex.exerciseDbId ?? ex.exerciseId else { continue }
            guard let info = exerciseIndex[dbId] else { continue }
            for muscle in info.involvedMuscles where muscle.role == .PRIMARY {
                let muscleId = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis)
                plannedPerMuscle[muscleId] = (plannedPerMuscle[muscleId] ?? 0.0) + Double(ex.sets.count)
            }
        }

        var actualPerMuscle: [String: Double] = [:]
        var completedByExercise: [String: Int] = [:]
        for (key, _) in completedSets {
            var exerciseId = String(key.split(separator: "_").dropLast().joined(separator: "_"))
            if exerciseId.hasSuffix("_L") || exerciseId.hasSuffix("_R") {
                exerciseId = String(exerciseId.split(separator: "_").dropLast().joined(separator: "_"))
            }
            completedByExercise[exerciseId] = (completedByExercise[exerciseId] ?? 0) + 1
        }
        for ex in plannedSession.allExercises() {
            let sets = completedByExercise[ex.id] ?? 0
            if sets == 0 { continue }
            guard let dbId = ex.exerciseDbId ?? ex.exerciseId else { continue }
            guard let info = exerciseIndex[dbId] else { continue }
            for muscle in info.involvedMuscles where muscle.role == .PRIMARY {
                let muscleId = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis)
                actualPerMuscle[muscleId] = (actualPerMuscle[muscleId] ?? 0.0) + Double(sets)
            }
        }

        var surplusMuscles: [String] = []
        for (muscle, planned) in plannedPerMuscle {
            let actual = actualPerMuscle[muscle] ?? 0.0
            let delta = actual - planned
            if delta > 0 { surplusMuscles.append(muscle) }
        }
        if surplusMuscles.isEmpty { return [] }

        guard let program = repository.getProgramById(programId) else { return [] }
        let state = uiState
        guard state.macroIndex >= 0, state.macroIndex < program.macrocycles.count else { return [] }
        let macro = program.macrocycles[state.macroIndex]
        let allMeso = macro.blocks.flatMap { $0.mesocycles }
        guard state.mesoIndex >= 0, state.mesoIndex < allMeso.count else { return [] }
        let week = allMeso[state.mesoIndex].weeks.first { $0.id == state.weekId }
        guard let week = week else { return [] }
        let weekSessions = week.sessions

        guard let nextSession = SessionAssistantEngine.findNextSessionWithMuscles(
            currentSessionId: plannedSession.id,
            weekSessions: weekSessions,
            muscleIds: surplusMuscles,
            exerciseIndex: exerciseIndex
        ) else { return [] }

        return SessionAssistantEngine.computeProposedDiscounts(
            currentSession: plannedSession,
            nextSession: nextSession,
            targetMuscles: surplusMuscles,
            completedSets: completedSets,
            exerciseIndex: exerciseIndex
        )
    }

    private func latestTechniqueSignal(_ exerciseId: String, _ exerciseDbId: String) -> Int {
        let reports = repository.history.sorted(by: { $0.date > $1.date }).flatMap { $0.postExerciseReports ?? [] }.filter { $0.exerciseId == exerciseId || $0.canonicalExerciseId == exerciseDbId }.prefix(2)
        guard let first = reports.first else { return 0 }
        let latest = first.technicalQuality.clamped(to: 1...10)
        let previous = reports.dropFirst().first?.technicalQuality.clamped(to: 1...10)
        if let prev = previous {
            if latest >= prev + 1 { return 1 }
            if latest <= prev - 1 { return -1 }
        }
        if latest >= 9 { return 1 }
        if latest <= 6 { return -1 }
        return 0
    }

    private func completedSessionSetsForExercise(_ exercise: Exercise, activeTag: String?) -> [SessionExerciseSetSnapshot] {
        let state = uiState
        let allSessionSets = exercise.sets.indices.flatMap { setIndex -> [SessionExerciseSetSnapshot] in
            [state.completedSets[buildCompletedSetKey(exercise.id, setIndex, nil)],
             state.completedSets[buildCompletedSetKey(exercise.id, setIndex, "left")],
             state.completedSets[buildCompletedSetKey(exercise.id, setIndex, "right")]].compactMap { $0 }.map { SessionExerciseSetSnapshot(setIndex: setIndex, completedSet: $0) }
        }.filter { !$0.completedSet.isWarmup }
        let tagged = activeTag.map { tag in allSessionSets.filter { $0.completedSet.tagId == tag } } ?? allSessionSets
        return tagged.isEmpty ? allSessionSets : tagged
    }

    private func estimatedSessionCapacity(_ set: CompletedSet) -> Double? {
        if let erm = set.homologatedResultV3?.estimatedRm, erm > 0 { return erm }
        guard set.weight > 0, set.reps >= 1, set.reps <= 36 else { return nil }
        return calculateHybrid1RM(set.weight, reps: set.reps)
    }

    private func performanceRatioForSet(_ set: CompletedSet) -> Double? {
        guard let eRM = estimatedSessionCapacity(set), eRM > 0 else { return nil }
        let payload = set.recordedPayloadV3
        let actualLoad: Double
        if payload?.loadInputMode == LoadModeV2.ASSISTED { actualLoad = payload?.assistedLoad ?? set.weight }
        else { actualLoad = payload?.externalLoad ?? set.weight }
        guard actualLoad > 0 else { return nil }
        return actualLoad / eRM
    }

    private func determineSessionBaseWeight(_ exercise: Exercise, setIdx: Int, activeTag: String?, side: String?) -> (Double?, WorkoutLoadSuggestionSource) {
        let manualOverride = manualOverrideForSet(exercise.id, setIdx: setIdx, side: side)
        if let m = manualOverride { return (m, .MANUAL_BASE) }
        if let pw = plannedWorkingWeightForSet(exercise, setIdx: setIdx), pw > 0 { return (pw, .PROGRAM) }
        let historySuggestion = getWeightSuggestion(exercise, setIdx: setIdx, activeTag: activeTag)
        if let hw = historySuggestion?.suggestedWeight, hw > 0 { return (hw, .HISTORY) }
        return (nil, .PROGRAM)
    }

    private func shouldUsePlanAsDominantBase(_ exercise: Exercise, setIdx: Int) -> Bool {
        guard let set = exercise.sets[safe: setIdx] else { return false }
        return set.weight != nil || set.consolidatedWeight != nil || calculateSuggestedLoad(exercise, set: set) != nil
    }

    private func determineFatigueFactor(_ exercise: Exercise, setIdx: Int, activeTag: String?, side: String?) -> Double {
        let priorCount = completedSessionSetsForExercise(exercise, activeTag: activeTag).filter { $0.setIndex < setIdx && !$0.completedSet.skipped && (side == nil || $0.completedSet.side == side) }.count
        return WorkoutLoadSuggestionRules.fatigueFactorForPriorCompletedSets(priorCompletedCount: priorCount)
    }

    private func computeSessionImprovementAdjustment(_ exercise: Exercise, activeTag: String?, side: String?) -> Double {
        let sessionSets = completedSessionSetsForExercise(exercise, activeTag: activeTag).filter { $0.completedSet.side == side || side == nil }.filter { !$0.completedSet.skipped }
        guard !sessionSets.isEmpty else { return 1.0 }
        let sessionBest = sessionSets.compactMap { estimatedSessionCapacity($0.completedSet) }.max() ?? 0
        let dbId = canonicalExerciseKey(exercise)
        let historyBaselineRm = getExerciseHistory(dbId, limit: 1, preferredTag: activeTag).first?.e1rm ?? exercise.reference1RM ?? exercise.goal1RM
        guard let baseline = historyBaselineRm, baseline > 0, sessionBest > 0 else { return 1.0 }
        let ratio = sessionBest / baseline
        if ratio >= 1.025 { return min(1.05, ratio) }
        if ratio <= 0.97 { return max(0.92, ratio) }
        return 1.0
    }

    // MARK: - Prediction bias

    private func updatePredictionBiasFromClosingFeedback(_ closingFeedback: SessionClosingFeedback) {
        repository.updateSettings { settings in
            let prev = settings.augePredictionBias
            let alpha = 0.30
            let newSamples = min(prev.sampleCount + 1, 500)
            var updated = prev
            updated.cnsBias = (prev.cnsBias * (1.0 - alpha) + Double(closingFeedback.systemAdjustment) * alpha).clamped(-20.0, 20.0)
            updated.muscularBias = (prev.muscularBias * (1.0 - alpha) + Double(closingFeedback.muscularAdjustment) * alpha).clamped(-20.0, 20.0)
            updated.spinalBias = (prev.spinalBias * (1.0 - alpha) + Double(closingFeedback.structureAdjustment) * alpha).clamped(-20.0, 20.0)
            updated.sampleCount = newSamples
            updated.lastUpdatedMs = self.nowMs()
            settings.augePredictionBias = updated
        }
    }

    // MARK: - Header widget visibility

    func setHeaderWidgetVisibility(showRmCalculator: Bool? = nil, showRealtimeRings: Bool? = nil) {
        var current = uiState.headerWidgets
        if let v = showRmCalculator { current.showRmCalculator = v }
        if let v = showRealtimeRings { current.showRealtimeRings = v }
        updateState { $0.headerWidgets = current }
        let key = workoutWidgetsSessionKey()
        repository.updateSettings { settings in
            settings.workoutV2HeaderWidgetsBySession[key] = current
        }
    }

    // MARK: - Deferred replacement prompt

    private func showDeferredReplacementPromptIfNeeded(_ exerciseId: String) {
        guard let prompt = deferredReplacementPrompt, prompt.exerciseId == exerciseId else { return }
        deferredReplacementPrompt = nil
        updateState { $0.pendingReplacementPersistencePrompt = prompt }
    }

    // MARK: - Apply replacement to session/program

    private func applyReplacementToSession(session: Session, sourceExerciseDbId: String?, sourceExerciseId: String, sourceExerciseSlot: Int?, replacement: ExerciseMuscleInfo, slotStrict: Bool) -> Session {
        if slotStrict, let slot = sourceExerciseSlot {
            let target = session.exerciseAtSlot(slot)
            guard let target = target, matchesSourceExercise(target, sourceExerciseDbId: sourceExerciseDbId, sourceExerciseId: sourceExerciseId) else { return session }
            return session.replaceExerciseAtSlot(slot) { buildReplacementExercise($0, replacement) }
        }
        if !session.parts.isEmpty {
            var changed = false
            let newParts = session.parts.map { part -> SessionPart in
                let mapped = part.exercises.map { candidate -> Exercise in
                    guard matchesSourceExercise(candidate, sourceExerciseDbId: sourceExerciseDbId, sourceExerciseId: sourceExerciseId) else { return candidate }
                    changed = true
                    return buildReplacementExercise(candidate, replacement)
                }
                return mapped == part.exercises ? part : part.withExercises(mapped)
            }
            return changed ? session.withParts(newParts) : session
        }
        let mapped = session.exercises.map { candidate -> Exercise in
            guard matchesSourceExercise(candidate, sourceExerciseDbId: sourceExerciseDbId, sourceExerciseId: sourceExerciseId) else { return candidate }
            return buildReplacementExercise(candidate, replacement)
        }
        return mapped == session.exercises ? session : session.withExercises(mapped)
    }

    private func applyReplacementToProgram(program: Program, currentLocation: SessionLocationCursor?, sourceExerciseDbId: String?, sourceExerciseId: String, sourceExerciseSlot: Int?, replacement: ExerciseMuscleInfo, scope: ReplacementPersistenceScopeV2) -> Program {
        guard scope != .SESSION_ONLY else { return program }
        var changed = false
        
        let newMacros = program.macrocycles.enumerated().map { macroIndex, macro -> Macrocycle in
            let newBlocks = macro.blocks.enumerated().map { blockIndex, block -> Block in
                var mesoOffset = 0
                let newMesos = block.mesocycles.enumerated().map { mesoLocalIdx, meso -> Mesocycle in
                    let flattenedMeso = mesoOffset + mesoLocalIdx
                    let newWeeks = meso.weeks.enumerated().map { weekIndex, week -> ProgramWeek in
                        var newSessions = week.sessions
                        for sessionSlot in newSessions.indices {
                            let session = newSessions[sessionSlot]
                            let applyNow: Bool
                            switch scope {
                            case .SESSION_ONLY: applyNow = false
                            case .PERMANENT: applyNow = true
                            case .MESOCYCLE_MATCHING:
                                if let loc = currentLocation {
                                    applyNow = macroIndex == loc.macroIndex && flattenedMeso == loc.mesoIndex && weekIndex == loc.weekIndex && sessionSlot == loc.sessionSlot
                                } else { applyNow = false }
                            }
                            if applyNow {
                                let updated = applyReplacementToSession(session: session, sourceExerciseDbId: sourceExerciseDbId, sourceExerciseId: sourceExerciseId, sourceExerciseSlot: sourceExerciseSlot, replacement: replacement, slotStrict: scope == .MESOCYCLE_MATCHING)
                                if updated != session { changed = true }
                                newSessions[sessionSlot] = updated
                            }
                        }
                        return ProgramWeek(id: week.id, name: week.name, description: week.description, sessions: newSessions, variant: week.variant)
                    }
                    mesoOffset += 1
                    return Mesocycle(id: meso.id, name: meso.name, goal: meso.goal, customGoal: meso.customGoal, weeks: newWeeks)
                }
                return Block(id: block.id, name: block.name, description: block.description, mesocycles: newMesos)
            }
            return Macrocycle(id: macro.id, name: macro.name, blocks: newBlocks)
        }
        
        return changed ? program.withMacrocycles(newMacros) : program
    }

    private func sanitizeLiveEditPersistenceScope(_ program: Program?, _ requested: ReplacementPersistenceScopeV2) -> ReplacementPersistenceScopeV2 {
        guard let program = program else { return .SESSION_ONLY }
        guard WorkoutEditingRules.canPersistLiveStructuralChanges(program) else { return .SESSION_ONLY }
        switch requested {
        case .PERMANENT: return .PERMANENT
        case .SESSION_ONLY: return .SESSION_ONLY
        case .MESOCYCLE_MATCHING: return .SESSION_ONLY
        }
    }

    private func matchesSourceExercise(_ candidate: Exercise, sourceExerciseDbId: String?, sourceExerciseId: String) -> Bool {
        let sourceDb = sourceExerciseDbId?.trimmingCharacters(in: .whitespaces) ?? ""
        let candidateDb = candidate.resolvedCanonicalExerciseId()
        return candidate.id == sourceExerciseId || (!sourceDb.isEmpty && candidateDb.caseInsensitiveCompare(sourceDb) == .orderedSame)
    }

    private func buildReplacementExercise(_ old: Exercise, _ replacement: ExerciseMuscleInfo) -> Exercise {
        let replaced = old.replacedWithCatalogExercise(replacement)
        let defaultLoadMode = replaced.sets.first?.loadModeV2 ?? .LOAD
        return replaced.copy(
            sets: [ExerciseSet(id: UUID().uuidString, loadModeV2: defaultLoadMode, unitModeV2: .REPS)],
            trainingMode: .REPS,
            reference1RM: nil,
            prFor1RM: nil,
            consolidatedWeight: nil,
            isUnilateral: false,
            unilateralMode: .BILATERAL,
            restBetweenSidesSeconds: nil
        )
    }

    // MARK: - Insert / reorder exercises

    private func insertExerciseAfter(_ session: Session, currentExerciseId: String, newExercise: Exercise) -> Session {
        if !session.parts.isEmpty {
            var result = session.parts
            for i in result.indices {
                if let idx = result[i].exercises.firstIndex(where: { $0.id == currentExerciseId }) {
                    var exercises = result[i].exercises
                    exercises.insert(newExercise, at: idx + 1)
                    result[i] = result[i].withExercises(exercises)
                    return session.withParts(result)
                }
            }
        }
        if let idx = session.exercises.firstIndex(where: { $0.id == currentExerciseId }) {
            var exercises = session.exercises
            exercises.insert(newExercise, at: idx + 1)
            return session.withExercises(exercises)
        }
        return insertExerciseAtEnd(session, newExercise: newExercise)
    }

    private func insertExerciseAtEnd(_ session: Session, newExercise: Exercise) -> Session {
        if !session.parts.isEmpty {
            var result = session.parts
            let lastIdx = result.count - 1
            if lastIdx >= 0 {
                result[lastIdx] = result[lastIdx].withExercises(result[lastIdx].exercises + [newExercise])
            }
            return session.withParts(result)
        }
        return session.withExercises(session.exercises + [newExercise])
    }

    private func insertExerciseAfterSupersetMembers(_ session: Session, memberIds: [String], exercise: Exercise) -> Session {
        let memberIdSet = Set(memberIds)
        if !session.parts.isEmpty {
            if let partIdx = session.parts.firstIndex(where: { part in part.exercises.contains(where: { $0.id.isIn(memberIdSet) }) }) {
                var result = session.parts
                let lastMemberIndex = result[partIdx].exercises.lastIndex(where: { $0.id.isIn(memberIdSet) }) ?? -1
                let insertionIndex = min(lastMemberIndex + 1, result[partIdx].exercises.count)
                var exercises = result[partIdx].exercises
                exercises.insert(exercise, at: insertionIndex)
                result[partIdx] = result[partIdx].withExercises(exercises)
                return session.withParts(result)
            }
        }
        let lastMemberIndex = session.exercises.lastIndex(where: { $0.id.isIn(memberIdSet) }) ?? -1
        let insertionIndex = min(lastMemberIndex + 1, session.exercises.count)
        var exercises = session.exercises
        exercises.insert(exercise, at: insertionIndex)
        return session.withExercises(exercises)
    }

    func addCatalogExerciseToLiveSuperset(_ groupId: String, catalogExercise: ExerciseMuscleInfo) {
        let state = uiState
        guard let base = state.session else { return }
        var newExerciseId: String?
        let updatedSession = withModeSession(base, state.activeMode) { modeSession in
            guard let group = modeSession.allSupersetGroups().first(where: { $0.id == groupId }) else { return modeSession }
            let members = SupersetRules.orderedMembers(modeSession, groupId)
            guard let template = members.first, members.count < 4 else { return modeSession }
            let generatedId = UUID().uuidString
            newExerciseId = generatedId
            let newExercise = template.copy(
                id: generatedId,
                sets: template.sets.isEmpty ? [ExerciseSet(id: UUID().uuidString)] : template.sets.map { $0.copy(id: UUID().uuidString) },
                warmupSets: [],
                supersetId: groupId,
                supersetRestBetween: group.restBetweenExercises,
                supersetRestAfter: group.restAfterSuperset,
                supersetGroupRef: groupId,
                mobilitySeries: []
            ).replacedWithCatalogExercise(catalogExercise)
            let memberIds = members.map { $0.id }
            let inserted = insertExerciseAfterSupersetMembers(modeSession, memberIds: memberIds, exercise: newExercise)
            return SupersetRules.createSuperset(session: inserted, groupId: groupId, exerciseIds: memberIds + [generatedId], restBetweenExercises: group.restBetweenExercises, restAfterSuperset: group.restAfterSuperset, rounds: group.rounds, anchorPartId: group.visualPlacement?.partId, anchorExerciseId: group.visualPlacement?.anchorExerciseId ?? memberIds.first)
        }
        guard updatedSession != base else { return }
        applySessionMutation(updatedSession, preferredExerciseId: newExerciseId)
    }

    func deferSkipRemainingCurrentExercise() {
        guard let restState = uiState.restModalState else { return }
        guard !restState.skipCurrentExerciseOnFinish else { return }
        updateState { $0.restModalState = restState.withSkipCurrentExerciseOnFinish(true) }
        persistOngoingState()
    }

    // MARK: - Session schedule

    private func scheduledDateForSession(_ weekId: String?, session: Session) -> String? {
        guard let weekId = weekId, !weekId.isEmpty else { return nil }
        guard let program = repository.getProgramById(programId) else { return nil }
        let projected = ProgramCalendarEngine.project(program).scheduledDateFor(session, weekId: weekId)
        if let projected = projected { return projected }
        let week = program.macrocycles
            .flatMap { $0.blocks }
            .flatMap { $0.mesocycles }
            .flatMap { $0.weeks }
            .first { $0.id == weekId }
            ?? { () -> ProgramWeek? in nil }()
        guard let w = week else { return nil }
        if let day = session.dayOfWeek, day >= 1, day <= 7 {
            if let explicit = w.trainingDayDates[day], !explicit.isEmpty { return explicit }
        }
        return w.startDate
    }

    // MARK: - Format helpers

    func formatWorkoutWeight(_ weight: Double) -> String {
        if weight == 0 { return "0" }
        if weight.truncatingRemainder(dividingBy: 1.0) == 0.0 {
            return "\(Int(weight))"
        }
        let formatted = String(format: "%.1f", weight)
        return formatted.hasSuffix(".0") ? "\(Int(weight))" : formatted
    }



    // MARK: - Tag multiplier

    private func getTagMultiplier(_ tag: String?) -> Double {
        guard let tag = tag, !tag.trimmingCharacters(in: .whitespaces).isEmpty else { return 1.0 }
        switch tag.trimmingCharacters(in: .whitespaces).lowercased() {
        case "base": return 1.0
        case "top set": return 1.08
        case "pr": return 1.15
        case "pesado": return 1.05
        case "back-off": return 0.90
        case "tecnica", "control": return 0.85
        case "volumen": return 0.90
        case "ligero", "pump": return 0.80
        case "máquina": return 1.10
        case "sentado": return 0.95
        case "de pie": return 1.00
        case "cable": return 0.90
        case "unilateral": return 0.85
        case "inclinado": return 0.85
        case "declinado": return 1.05
        default: return 1.0
        }
    }

    // MARK: - Suggested weight for voice after rest

    private func suggestedWeightForVoiceAfterRest() -> Double? {
        let state = uiState
        guard let exercise = visibleExercises(state).first(where: { $0.id == visibleExercises(state)[safe: state.currentExerciseIdx]?.id }) else { return nil }
        let suggested = getWeightSuggestionWithAutoRegulation(exercise, setIdx: state.currentSetIdx, activeTag: state.exerciseTags[exercise.id])?.suggestedWeight
        return (suggested ?? 0.0) > 0.0 ? suggested : nil
    }

    // MARK: - Contextual load suggestion

    func getContextualLoadSuggestion(_ exercise: Exercise, setIdx: Int, activeTag: String? = nil, side: String? = nil) -> WorkoutLoadSuggestionUi? {
        let key = workoutSetKey(exerciseId: exercise.id, setIdx: setIdx, side: side)
        if let existing = uiState.loadSuggestions[key] { return existing }
        if side != nil, let fallback = uiState.loadSuggestions[workoutSetKey(exerciseId: exercise.id, setIdx: setIdx)] { return fallback }
        return buildLoadSuggestionForSet(exercise, setIdx: setIdx, activeTag: activeTag, side: side)
    }

    // MARK: - Build load suggestion for set

    private func buildLoadSuggestionForSet(_ exercise: Exercise, setIdx: Int, activeTag: String?, side: String?) -> WorkoutLoadSuggestionUi? {
        let currentLoadMode = effectiveLoadModeForExercise(exercise, setIdx: setIdx)
        guard currentLoadMode != .BODYWEIGHT else { return nil }

        let manualOverride = manualOverrideForSet(exercise.id, setIdx: setIdx, side: side)
        let (resolvedBase, resolvedSource) = determineSessionBaseWeight(exercise, setIdx: setIdx, activeTag: activeTag, side: side)
        let historySuggestion = getWeightSuggestion(exercise, setIdx: setIdx, activeTag: activeTag)
        guard let baseWeight = (manualOverride ?? resolvedBase), baseWeight > 0.0 else { return nil }
        let originalWeight = plannedWorkingWeightForSet(exercise, setIdx: setIdx) ?? historySuggestion?.suggestedWeight ?? baseWeight
        let shouldRespectPlan = shouldUsePlanAsDominantBase(exercise, setIdx: setIdx)
        let improvementFactor = shouldRespectPlan ? 1.0 : computeSessionImprovementAdjustment(exercise, activeTag: activeTag, side: side)
        let fatigueFactor = determineFatigueFactor(exercise, setIdx: setIdx, activeTag: activeTag, side: side)
        var computedWeight = baseWeight * fatigueFactor
        if manualOverride == nil {
            computedWeight *= shouldRespectPlan ? improvementFactor.clamped(0.95, 1.05) : improvementFactor
        }

        let exerciseHistory = completedSessionSetsForExercise(exercise, activeTag: activeTag).filter { side == nil || $0.completedSet.side == side }.filter { !$0.completedSet.skipped }
        let bestRatio = exerciseHistory.compactMap { performanceRatioForSet($0.completedSet) }.max()
        let worstRatio = exerciseHistory.compactMap { performanceRatioForSet($0.completedSet) }.min()
        let latestRatio = exerciseHistory.last.flatMap { performanceRatioForSet($0.completedSet) }
        let severePerformanceDrop = exerciseHistory.last != nil && (
            exerciseHistory.last?.completedSet.failureReason == "execution_error" ||
            (exerciseHistory.last?.completedSet.isPartial ?? false && (latestRatio ?? 1.0) <= 0.85) ||
            (latestRatio != nil && latestRatio! <= 0.70)
        )

        var reason: String
        if let mo = manualOverride {
            reason = "Override manual"
        } else {
            switch resolvedSource {
            case .MANUAL_BASE: reason = "Base manual de la sesión"
            case .HISTORY: reason = "Historial del usuario"
            case .SESSION_ERM: reason = "eRM de la sesión"
            case .PROGRAM: reason = "Plan de la sesión"
            }
        }

        if let best = bestRatio, best >= 1.025 {
            let capped = max(originalWeight * 1.05, originalWeight)
            computedWeight = min(computedWeight, capped)
            if computedWeight < baseWeight * fatigueFactor { computedWeight = min(baseWeight * fatigueFactor, capped) }
            reason += " · eRM +\(Int((best - 1.0) * 100))%"
        }
        if let worst = worstRatio, worst <= 0.97 {
            computedWeight *= 0.97
            reason += " · Fatiga detectada"
        }

        if !severePerformanceDrop {
            computedWeight = max(computedWeight, baseWeight * 0.80)
        } else {
            computedWeight = max(computedWeight, baseWeight * 0.70)
            reason += " · Rendimiento muy bajo"
        }

        let finalWeight: Double
        if currentLoadMode == .ASSISTED {
            let adjustmentRatio = baseWeight > 0.0 ? (computedWeight / baseWeight).clamped(0.60, 1.50) : 1.0
            finalWeight = coerceLoadStep(applyAssistedAdjustment(baseWeight, factor: adjustmentRatio)).clamped(min: 0.5)
        } else {
            finalWeight = coerceLoadStep(computedWeight)
        }

        return WorkoutLoadSuggestionUi(
            suggestedWeight: finalWeight,
            originalWeight: coerceLoadStep(originalWeight),
            isRecalculated: abs(finalWeight - originalWeight) >= 0.25,
            reason: reason,
            source: manualOverride != nil ? .MANUAL_BASE : resolvedSource,
            suggestedLoadMode: currentLoadMode == .ASSISTED ? .ASSISTED : historySuggestion?.suggestedLoadMode
        )
    }

    // MARK: - Suggest next load V2

    func suggestNextLoadV2(_ entry: SetEntryV2) -> WeightSuggestion? {
        guard let exercise = uiState.session?.allExercises().first(where: { $0.id == entry.exerciseId }) else { return nil }
        return getWeightSuggestion(exercise, setIdx: entry.setIndex, activeTag: uiState.exerciseTags[entry.exerciseId])
    }

    // MARK: - Migrate context profiles to tags

    private func migrateContextProfilesToTags(_ profiles: [String: WorkoutContextProfile], exerciseKey: String) -> [WorkoutTag] {
        let filtered = profiles.values.filter { $0.exerciseKey == exerciseKey && ($0.tagId != nil || $0.setupLabel != nil) }
        var unique: [WorkoutContextProfile] = []
        var seenKeys = Set<String>()
        for p in filtered {
            let key = p.tagId ?? p.setupLabel ?? p.id ?? ""
            if !seenKeys.contains(key) {
                seenKeys.insert(key)
                unique.append(p)
            }
        }
        return unique.map { profile in
            var subTags: [WorkoutSubTag] = []
            if let brand = profile.machineBrand { subTags.append(WorkoutSubTag(name: brand, category: .MARCA)) }
            if let seat = profile.setupDetails?.seatPosition { subTags.append(WorkoutSubTag(name: "Asiento: \(seat)", category: .SETUP)) }
            if let pin = profile.setupDetails?.pinPosition { subTags.append(WorkoutSubTag(name: "Pin: \(pin)", category: .SETUP)) }
            if let bar = profile.setupDetails?.barWeightKg { subTags.append(WorkoutSubTag(name: "Barra: \(bar)kg", category: .SETUP)) }
            if let notes = profile.setupDetails?.equipmentNotes { subTags.append(WorkoutSubTag(name: notes, category: .SETUP)) }
            return WorkoutTag(
                id: profile.id ?? UUID().uuidString,
                name: profile.tagId ?? profile.setupLabel ?? "Migrado",
                exerciseKey: profile.exerciseKey ?? "",
                subTags: subTags,
                createdAtIso: profile.createdAtIso ?? "",
                lastUsedAtIso: profile.lastUsedAtIso ?? "",
                usageCount: profile.usageCount
            )
        }
    }

    // MARK: - Warmup / Mobility completion keys

    private func warmupCompletionKey(_ exerciseId: String, warmupSetId: String) -> String {
        WorkoutStepRules.warmupStepKey(exerciseId, warmupSetId)
    }

    private func mobilityCompletionKey(_ exerciseId: String, mobilityId: String) -> String {
        WorkoutStepRules.mobilityStepKey(exerciseId, mobilityId)
    }

    // MARK: - Assist mode helpers

    private func isAssistedExercise(_ exercise: Exercise, setIdx: Int) -> Bool {
        effectiveLoadModeForExercise(exercise, setIdx: setIdx) == .ASSISTED
    }

    // MARK: - Update exercise set plan

    func updateExerciseSetPlan(_ exerciseId: String, setId: String, transform: (ExerciseSet) -> ExerciseSet) {
        updateExerciseDefinition(exerciseId) { exercise in
            exercise.copy(sets: exercise.sets.map { set in
                if set.id == setId { return transform(set).normalizeWorkoutSet(exercise) }
                return set
            })
        }
    }

    // MARK: - Sync active profile tag

    private func syncActiveProfileTag(_ exerciseId: String, tag: String?) {
        let state = uiState
        let profileId = state.activeContextProfileByExerciseId[exerciseId]
        guard let exercise = visibleExercises(state).first(where: { $0.id == exerciseId }) else { return }
        let now = nowIso()
        let updatedProfile: WorkoutContextProfile
        if let pid = profileId, let existing = state.contextProfilesV3[pid] {
            updatedProfile = existing.copy(
                tagId: tag,
                lastUsedAtIso: now,
                usageCount: existing.usageCount + 1
            )
        } else {
            let base = defaultContextProfileForExercise(exercise)
            updatedProfile = base.copy(
                id: "\(canonicalExerciseKey(exercise))|\(UUID().uuidString)",
                tagId: tag,
                createdAtIso: now,
                lastUsedAtIso: now,
                usageCount: 1
            )
        }
        repository.upsertContextProfile(updatedProfile)
        updateState {
            if let id = updatedProfile.id {
                $0.contextProfilesV3[id] = updatedProfile
                $0.activeContextProfileByExerciseId[exerciseId] = id
            }
        }
    }

    // MARK: - Voice input

    func startVoiceInput(_ exerciseId: String, setIdx: Int, side: String?, isTimeMode: Bool, isUnilateral: Bool) {
        voiceTask?.cancel()
        updateState { $0.voiceUiState = .listening(exerciseId: exerciseId, setIdx: setIdx, side: side, partialText: "", isReady: false) }
        voiceTask = Task { @MainActor in
            // Voice recognition would be connected here
            // For now, update voice session state
        }
    }

    private func handleVoiceCommand(_ command: VoiceSessionCommand) {
        updateState { $0.voiceSessionState = uiState.voiceSessionState }
        switch command {
        case .registerSet(let interpretation): handleVoiceRegisterSet(interpretation)
        case .confirm: handleVoiceConfirmSet()
        case .cancel: handleVoiceCancelSet()
        case .skipExercise: handleVoiceSkipExercise()
        case .skipSet: skipSet()
        case .previousExercise: handleVoicePreviousExercise()
        case .suggestWeight: handleVoiceSuggestWeight()
        case .restStatus: handleVoiceRestStatus()
        case .whatExercise: handleVoiceWhatExercise()
        case .nextExercise: handleVoiceNextExercise()
        case .turnOffVoice: disableVoice()
        case .finishSession: finishUpToCurrentPoint()
        case .cancelSession: cancelWorkout()
        case .logFeedback(let tech, let discomfort, let intensity, let save, let search):
            handleVoiceLogFeedback(technicalQuality: tech, discomfortId: discomfort, perceivedIntensity: intensity, isSaveAction: save, exerciseSearchName: search)
        case .logFinalFeedback(let notes, let discomfort, let additional, let neural, let spinal, let save):
            handleVoiceLogFinalFeedback(notes: notes, discomfortId: discomfort, additionalDiscomfortNote: additional, neuralBattery: neural, spinalBattery: spinal, isSaveAction: save)
        case .unknown: break
        }
    }

    private func handleVoiceLogFeedback(technicalQuality: Int?, discomfortId: String?, perceivedIntensity: Double?, isSaveAction: Bool, exerciseSearchName: String?) {
        let state = uiState
        let allExercises = visibleExercises(state)
        guard let exercise = allExercises[safe: state.postExerciseTargetIdx] else { return }
        let target = state.postExerciseFeedbackTarget
        let targetExercise: Exercise
        if let target = target, case .supersetGroup(_, let targetIds) = target {
            let matchedId = targetIds.first(where: { id in
                guard let memberEx = allExercises.first(where: { $0.id == id }) else { return false }
                let normalizedName = memberEx.name.lowercased().folding(options: .diacriticInsensitive, locale: .current)
                return !normalizedName.isEmpty && (exerciseSearchName ?? "").contains(normalizedName)
            }) ?? target.missingFeedbackExerciseIds(state).first ?? targetIds.first ?? exercise.id
            targetExercise = allExercises.first(where: { $0.id == matchedId }) ?? exercise
        } else {
            targetExercise = exercise
        }

        var feedback = state.postExerciseFeedbackByExerciseId[targetExercise.id] ?? PostExerciseFeedback(
            exerciseId: targetExercise.id, exerciseDbId: canonicalExerciseKey(targetExercise),
            canonicalExerciseId: targetExercise.canonicalExerciseId ?? canonicalExerciseKey(targetExercise),
            exerciseName: targetExercise.name, technicalQuality: 8, discomfortIds: [], perceivedIntensityRpe: nil
        )

        if isSaveAction {
            if case .supersetGroup(_, let ids) = target {
                let feedbacks = ids.map { id -> PostExerciseFeedback in
                    if let existing = state.postExerciseFeedbackByExerciseId[id] { return existing }
                    let memberEx = allExercises.first(where: { $0.id == id })
                    return PostExerciseFeedback(exerciseId: id, exerciseDbId: memberEx.map { canonicalExerciseKey($0) } ?? "", canonicalExerciseId: memberEx?.canonicalExerciseId ?? memberEx.map { canonicalExerciseKey($0) } ?? "", exerciseName: memberEx?.name ?? "", technicalQuality: 8, discomfortIds: [], perceivedIntensityRpe: nil)
                }
                savePostExerciseFeedbacks(feedbacks)
            } else {
                savePostExerciseFeedback(feedback)
            }
            return
        }

        var updates: [String] = []
        if let quality = technicalQuality {
            feedback.technicalQuality = quality
            updates.append("Calidad técnica fijada en \(quality)")
        }
        if let intensity = perceivedIntensity {
            feedback.perceivedIntensityRpe = intensity
            updates.append("Intensidad en RPE \(intensity)")
        }
        if let id = discomfortId {
            feedback.discomfortIds = id == "none" ? [] : (feedback.discomfortIds + [id]).distinct()
            updates.append(id == "none" ? "Sin molestias" : "Molestia agregada")
        }

        if !updates.isEmpty {
            updateState { $0.postExerciseFeedbackByExerciseId[targetExercise.id] = feedback }
        }
    }

    private func handleVoiceLogFinalFeedback(notes: String?, discomfortId: String?, additionalDiscomfortNote: String?, neuralBattery: Int?, spinalBattery: Int?, isSaveAction: Bool) {
        if isSaveAction {
            updateState { $0.voiceFinalConfirmTriggered = true }
            return
        }
        var updates: [String] = []
        if let notes = notes {
            updateState { $0.voiceFinalNotes = notes }
            updates.append("Nota de sesión actualizada")
        }
        if let additional = additionalDiscomfortNote {
            updateState { $0.voiceFinalAdditionalDiscomfortNote = additional }
            updates.append("Detalles de molestia actualizados")
        }
        if let neural = neuralBattery {
            updateState { $0.voiceFinalNeural = neural }
            updates.append("Batería nerviosa en \(neural)")
        }
        if let spinal = spinalBattery {
            updateState { $0.voiceFinalSpinal = spinal }
            updates.append("Batería espinal en \(spinal)")
        }
        if let discomfortId = discomfortId {
            let nextDiscomforts = discomfortId == "none" ? [] : (uiState.voiceFinalDiscomforts + [discomfortId]).distinct()
            updateState { $0.voiceFinalDiscomforts = nextDiscomforts }
            updates.append(discomfortId == "none" ? "Sin molestias finales" : "Molestia agregada")
        }
    }

    private func handleVoiceRegisterSet(_ interpretation: WorkoutVoiceInterpretation) {
        let state = uiState
        let allExercises = visibleExercises(state)
        guard let exercise = allExercises[safe: state.currentExerciseIdx] else { return }
        let setIdx = state.currentSetIdx
        let side = exercise.isEffectivelyUnilateral() ? interpretation.side : nil
        let isTimeMode = exercise.trainingMode == .TIME
        let baseIntensityMode = exercise.sets[safe: setIdx]?.intensityMode

        var draft = getSetDraft(exercise.id, setIdx: setIdx, side: side) ?? WorkoutSetDraft(selectedSide: side)
        let resolvedSide = interpretation.side ?? side ?? draft.selectedSide
        draft.weightText = interpretation.weightKg.map { String($0) } ?? draft.weightText
        draft.valueText = interpretation.metricValue.map { String($0) } ?? draft.valueText
        draft.selectedSide = resolvedSide
        draft.reachedFailure = interpretation.reachedFailure
        draft.voiceFields = interpretation.fields
        draft.isDirty = true
        updateSetDraft(exercise.id, setIdx: setIdx, side: resolvedSide, draft: draft)
        updateState { $0.voiceUiState = .applied(exerciseId: exercise.id, setIdx: setIdx, side: resolvedSide, interpretation: interpretation, message: workoutVoiceAppliedMessage(interpretation, isTimeMode)) }

        Task { @MainActor in
            await recordSetV2(
                weight: interpretation.weightKg ?? 0.0,
                value: Double(interpretation.metricValue ?? 0),
                intensity: interpretation.intensityValue,
                advanced: buildVoiceAdvancedFeedback(interpretation),
                side: resolvedSide
            )
        }
    }

    private func handleVoiceConfirmSet() {
        let state = uiState
        let allExercises = visibleExercises(state)
        guard allExercises[safe: state.currentExerciseIdx] != nil else { return }
        guard let interpretation = uiState.voiceSessionState.lastInterpretation else { return }
        Task { @MainActor in
            await recordSetV2(
                weight: interpretation.weightKg ?? 0.0,
                value: Double(interpretation.metricValue ?? 0),
                intensity: interpretation.intensityValue,
                advanced: buildVoiceAdvancedFeedback(interpretation),
                side: interpretation.side
            )
        }
    }

    private func handleVoiceCancelSet() {
        updateState { $0.voiceSessionState = uiState.voiceSessionState }
    }

    private func handleVoiceSkipExercise() {
        skipRemainingCurrentExercise()
    }

    private func handleVoicePreviousExercise() {
        prevSet()
    }

    private func handleVoiceSuggestWeight() {
        let state = uiState
        let allExercises = visibleExercises(state)
        guard let exercise = allExercises[safe: state.currentExerciseIdx] else { return }
        let suggestion = getWeightSuggestionWithAutoRegulation(exercise, setIdx: state.currentSetIdx, activeTag: state.exerciseTags[exercise.id])
        if let weight = suggestion?.suggestedWeight, weight > 0.0 {
            // voiceController.speakSuggestedWeight would be called here
        }
    }

    private func handleVoiceRestStatus() {
        if restTimerRemaining > 0 {
            // voiceController.speakRestRemaining would be called here
        }
    }

    private func handleVoiceWhatExercise() {
        let state = uiState
        let allExercises = visibleExercises(state)
        guard let exercise = allExercises[safe: state.currentExerciseIdx] else { return }
        // voiceController.speakCurrentExercise would be called here
    }

    private func handleVoiceNextExercise() {
        let state = uiState
        let allExercises = visibleExercises(state)
        if let nextEx = allExercises[safe: state.currentExerciseIdx + 1] {
            // voiceController.speakNextExercise would be called here
        }
    }

    // MARK: - Select superset group

    func selectSupersetGroup(_ groupId: String) {
        guard !uiState.showPostExerciseSheet else { return }
        let state = uiState
        let visible = visibleExercises(state)
        let steps = workoutStepPositions(state)
        let targetStep = steps.first(where: { step in
            step.supersetGroupId == groupId && !isWorkoutStepDone(step, visible: visible, completedSets: state.completedSets)
        }) ?? steps.first(where: { $0.supersetGroupId == groupId })
        guard let targetStep = targetStep, let position = targetStep.positionIn(visible) else { return }
        guard position.0 != state.currentExerciseIdx || position.1 != state.currentSetIdx else { return }
        stopRestTimer()
        let targetExercise = visible[safe: position.0]
        updateState {
            $0.currentExerciseIdx = position.0
            $0.currentSetIdx = position.1
            $0.activeStepKey = targetStep.stepKey
            $0.currentAutoRegulation = nil
            $0.pendingRestSuggestion = nil
            $0.restModalState = nil
            $0.editingState = targetExercise.flatMap { buildEditingStateForPosition(completedSets: state.completedSets, exercise: $0, setIdx: position.1) }
            $0.continuityTransitionTarget = nil
            $0.continuityFeedbackExerciseId = nil
        }
        persistOngoingState()
    }
}

// MARK: - Double clamp helper
extension Double {
    func clamped(_ min: Double, _ max: Double) -> Double {
        Swift.min(Swift.max(self, min), max)
    }
    func clamped(min: Double) -> Double {
        Swift.max(self, min)
    }
}

// MARK: - String helper
extension String {
    func isIn(_ set: Set<String>) -> Bool { set.contains(self) }
}

// MARK: - Array safe subscript
extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

// MARK: - Session extensions
extension Session {
    func normalizeSupersetsForWorkout() -> Session {
        let normalized = SupersetRules.normalizeSession(self)
        return normalized.copy(
            sessionB: normalized.sessionB.map(SupersetRules.normalizeSession),
            sessionC: normalized.sessionC.map(SupersetRules.normalizeSession),
            sessionD: normalized.sessionD.map(SupersetRules.normalizeSession)
        )
    }

    func exerciseAtSlot(_ slot: Int) -> Exercise? {
        if !parts.isEmpty {
            return parts.flatMap { $0.exercises }[safe: slot]
        }
        return exercises[safe: slot]
    }

    func replaceExerciseAtSlot(_ slot: Int, update: (Exercise) -> Exercise) -> Session {
        guard slot >= 0 else { return self }
        if !parts.isEmpty {
            var cursor = 0
            var changed = false
            let newParts = parts.map { part -> SessionPart in
                let size = part.exercises.count
                guard slot >= cursor, slot < cursor + size else { cursor += size; return part }
                let localIdx = slot - cursor
                cursor += size
                changed = true
                var mutable = part.exercises
                mutable[localIdx] = update(mutable[localIdx])
                return part.withExercises(mutable)
            }
            return changed ? withParts(newParts) : self
        }
        guard exercises.indices.contains(slot) else { return self }
        var mutable = exercises
        mutable[slot] = update(mutable[slot])
        return withExercises(mutable)
    }

    func moveExerciseById(_ exerciseId: String, direction: Int) -> Session {
        let flat = allExercises()
        guard let idx = flat.firstIndex(where: { $0.id == exerciseId }) else { return self }
        let target = min(max(0, idx + direction), flat.count - 1)
        guard target != idx else { return self }
        var mutable = flat
        let moved = mutable.remove(at: idx)
        mutable.insert(moved, at: target)
        return withExercises(mutable)
    }

    func reorderExercisesByIds(_ partId: String?, orderedExerciseIds: [String]) -> Session {
        func reorderList(_ exercises: [Exercise]) -> [Exercise] {
            guard exercises.count > 1, !orderedExerciseIds.isEmpty else { return exercises }
            let lookup = Dictionary(uniqueKeysWithValues: exercises.map { ($0.id, $0) })
            var ordered = orderedExerciseIds.compactMap { lookup[$0] }
            if ordered.count != exercises.count {
                for exercise in exercises where !orderedExerciseIds.contains(exercise.id) { ordered.append(exercise) }
            }
            return ordered
        }
        if let partId = partId {
            var changed = false
            let updatedParts = parts.map { part -> SessionPart in
                guard part.id == partId else { return part }
                changed = true
                return part.withExercises(reorderList(part.exercises))
            }
            return changed ? withParts(updatedParts) : self
        }
        let reordered = reorderList(exercises)
        return reordered == exercises ? self : withExercises(reordered)
    }

    func globalReorder(_ orderedExerciseIds: [String], _ originalPartMap: [String: String]) -> Session {
        guard !orderedExerciseIds.isEmpty else { return self }
        if parts.isEmpty {
            let lookup = Dictionary(uniqueKeysWithValues: exercises.map { ($0.id, $0) })
            return withExercises(orderedExerciseIds.compactMap { lookup[$0] })
        }
        struct ExBlock { let partId: String?; let ids: [String] }
        var blocks: [ExBlock] = []
        var curPart = originalPartMap[orderedExerciseIds[0]]
        var curIds: [String] = []
        for id in orderedExerciseIds {
            let p = originalPartMap[id]
            if p != curPart && !curIds.isEmpty {
                blocks.append(ExBlock(partId: curPart, ids: curIds))
                curIds = []
                curPart = p
            }
            curIds.append(id)
        }
        if !curIds.isEmpty { blocks.append(ExBlock(partId: curPart, ids: curIds)) }

        var newPartOf: [String: String?] = [:]
        for i in blocks.indices {
            let block = blocks[i]
            if block.ids.count == 1 {
                let prevPart = i > 0 ? blocks[i - 1].partId : nil
                let nextPart = i < blocks.count - 1 ? blocks[i + 1].partId : nil
                if let prev = prevPart, let next = nextPart, prev == next, prev != block.partId {
                    newPartOf[block.ids[0]] = prev
                    continue
                }
            }
            for id in block.ids { newPartOf[id] = block.partId }
        }

        let allEx = allExercises()
        let lookup = Dictionary(uniqueKeysWithValues: allEx.map { ($0.id, $0) })
        var partGroups: [String?: [Exercise]] = [:]
        for id in orderedExerciseIds {
            guard let ex = lookup[id] else { continue }
            partGroups[newPartOf[id] ?? nil, default: []].append(ex)
        }

        let newParts = parts.map { part -> SessionPart in
            let list = partGroups.removeValue(forKey: part.name) ?? []
            return part.withExercises(list)
        }
        let topLevel = partGroups.removeValue(forKey: nil) ?? []
        return withParts(newParts).withExercises(topLevel + partGroups.values.flatMap { $0 })
    }
}

// MARK: - Program extension
extension Program {
    func withMacrocycles(_ macros: [Macrocycle]) -> Program {
        Program(id: id, name: name, description: description, coverImage: coverImage, mode: mode, structure: structure, totalProgramWeeks: totalProgramWeeks, macrocycles: macros, author: author, isPublic: isPublic, tags: tags)
    }

    func updateWeekSessions(_ macroIndex: Int, mesoIndex: Int, weekId: String, transform: ([Session]) -> [Session]) -> Program {
        let newMacros = macrocycles.enumerated().map { macroIdx, macro -> Macrocycle in
            guard macroIdx == macroIndex else { return macro }
            let newBlocks = macro.blocks.map { block -> Block in
                let newMesos = block.mesocycles.enumerated().map { mesoIdx, meso -> Mesocycle in
                    guard mesoIdx == mesoIndex else { return meso }
                    let newWeeks = meso.weeks.map { week -> ProgramWeek in
                        guard week.id == weekId else { return week }
                        return ProgramWeek(id: week.id, name: week.name, description: week.description, sessions: transform(week.sessions), variant: week.variant)
                    }
                    return Mesocycle(id: meso.id, name: meso.name, goal: meso.goal, customGoal: meso.customGoal, weeks: newWeeks)
                }
                return Block(id: block.id, name: block.name, description: block.description, mesocycles: newMesos)
            }
            return Macrocycle(id: macro.id, name: macro.name, blocks: newBlocks)
        }
        return Program(id: id, name: name, description: description, coverImage: coverImage, mode: mode, structure: structure, totalProgramWeeks: totalProgramWeeks, macrocycles: newMacros, author: author, isPublic: isPublic, tags: tags)
    }
}

// MARK: - ExerciseSet extension
extension ExerciseSet {
    func normalizeWorkoutSet(_ exercise: Exercise) -> ExerciseSet {
        let normalized = WorkoutEditingRules.normalizeLiveEditedSet(exercise.trainingMode, self)
        let autoWeight = calculateSuggestedLoad(exercise, set: normalized) ?? normalized.weight
        return normalized.copy(weight: autoWeight)
    }
}

// MARK: - WorkoutStep extension
extension WorkoutStep {
    func positionIn(_ visible: [Exercise]) -> (Int, Int)? {
        guard let exerciseIdx = visible.firstIndex(where: { $0.id == exerciseId }) else { return nil }
        return (exerciseIdx, setIndex ?? 0)
    }
}

// MARK: - PostExerciseFeedbackTarget extension
extension PostExerciseFeedbackTarget {
    func missingFeedbackExerciseIds(_ state: WorkoutUiState) -> [String] {
        let targetIds: [String]
        switch self {
        case .single(let id): targetIds = [id]
        case .supersetGroup(_, let ids): targetIds = ids
        }
        return targetIds.filter { !state.postExerciseFeedbackByExerciseId.keys.contains($0) }
    }
}

// MARK: - Session copy helpers (immutable)
extension Session {
    func withExercises(_ exercises: [Exercise]) -> Session {
        Session(id: id, name: name, description: description, exercises: exercises, parts: parts, targetDurationMinutes: targetDurationMinutes, sessionB: sessionB, sessionC: sessionC, sessionD: sessionD, trainingBackup: trainingBackup, supersetGroups: supersetGroups)
    }
    func withParts(_ parts: [SessionPart]) -> Session {
        Session(id: id, name: name, description: description, exercises: exercises, parts: parts, targetDurationMinutes: targetDurationMinutes, sessionB: sessionB, sessionC: sessionC, sessionD: sessionD, trainingBackup: trainingBackup, supersetGroups: supersetGroups)
    }
}

extension SessionPart {
    func withExercises(_ exercises: [Exercise]) -> SessionPart {
        SessionPart(id: id, name: name, exercises: exercises, color: color, targetDurationMinutes: targetDurationMinutes)
    }
}

extension PostExerciseFeedback {
    mutating func copy(exerciseId: String? = nil, exerciseDbId: String? = nil, canonicalExerciseId: String? = nil, exerciseName: String? = nil, technicalQuality: Int? = nil, discomfortIds: [String]? = nil, perceivedIntensityRpe: Double? = nil) -> PostExerciseFeedback {
        var copy = self
        if let v = exerciseId { copy.exerciseId = v }
        if let v = exerciseDbId { copy.exerciseDbId = v }
        if let v = canonicalExerciseId { copy.canonicalExerciseId = v }
        if let v = exerciseName { copy.exerciseName = v }
        if let v = technicalQuality { copy.technicalQuality = v }
        if let v = discomfortIds { copy.discomfortIds = v }
        if let v = perceivedIntensityRpe { copy.perceivedIntensityRpe = v }
        return copy
    }
}
