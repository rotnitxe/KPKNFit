import SwiftUI

// MARK: - Deduplicate Canonical Muscles

func deduplicateCanonicalMuscles(_ muscleIds: [String]) -> [String] {
    var result = muscleIds
    var toRemove = Set<String>()
    for id in result {
        if result.contains(where: { other in other != id && other.hasPrefix("\(id) ") }) {
            toRemove.insert(id)
        }
    }
    result.removeAll { toRemove.contains($0) }
    return result
}

// MARK: - Top-level WorkoutScreen

struct WorkoutScreen: View {
    let programId: String
    let sessionId: String
    var onBack: () -> Void = {}
    var onComplete: (() -> Void)?
    var onNavigateToWikiLab: ((String) -> Void)? = nil

    @StateObject private var viewModel: WorkoutViewModel
    @State private var showExitDialog = false
    @State private var roadmapMode: RoadmapMode = .compact
    @State private var readinessSheetDismissed = false
    @State private var lastAnnouncedSetKey: String? = nil
    @State private var exerciseContextExerciseId: String? = nil
    @State private var showReplaceExercisePicker = false
    @State private var replaceTargetExerciseId: String? = nil
    @State private var replaceSearchQuery = ""
    @State private var setupSheetExerciseId: String? = nil
    @State private var tagSheetExerciseId: String? = nil
    @State private var selectedExerciseContextTab: WorkoutExerciseContextTab? = nil
    @State private var editSheetExerciseId: String? = nil
    @State private var rmSelectedWeight: Double? = nil
    @State private var showWorkoutSupersetCreator = false
    @State private var workoutSupersetSelectedExerciseId: String? = nil
    @State private var supersetSettingsGroupId: String? = nil
    @State private var addCatalogToSupersetGroupId: String? = nil
    @State private var addCatalogSearchQuery = ""
    @State private var addExerciseAfterId: String? = nil
    @State private var addExerciseSearchQuery = ""
    @State private var showReorderSheet = false
    @State private var reorderSheetExerciseIds: [String] = []
    @State private var showReorderCrossBoundaryConfirm = false
    @State private var reorderCrossBoundaryMessages: [String] = []
    @State private var pendingGlobalReorderIds: [String] = []
    @State private var showMobilityBanner = false
    @State private var showMobilityPicker = false
    @State private var selectedUnilateralSideOverride: String? = nil

    init(programId: String, sessionId: String, onBack: @escaping () -> Void = {}, onComplete: (() -> Void)? = nil, onNavigateToWikiLab: ((String) -> Void)? = nil) {
        self.programId = programId
        self.sessionId = sessionId
        self.onBack = onBack
        self.onComplete = onComplete
        self.onNavigateToWikiLab = onNavigateToWikiLab
        _viewModel = StateObject(wrappedValue: WorkoutViewModel(programId: programId, sessionId: sessionId))
    }

    var body: some View {
        let isMeetOrComp = viewModel.uiState.session?.isMeetDay == true || viewModel.uiState.session?.isCompetitionSession == true
        let showReadinessSheet = !readinessSheetDismissed && !isMeetOrComp && viewModel.uiState.readinessNeuralOverride == nil

        ZStack {
            if viewModel.uiState.session == nil {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                mainContent
            }

            // Roadmap bar
            if let session = viewModel.uiState.session {
                let modeExercises = sessionForActiveMode(session, viewModel.uiState.activeMode, uiState: viewModel.uiState).allExercises()
                let skipped = viewModel.uiState.skippedExerciseIds
                let visibleExercises = modeExercises.filter { !skipped.contains($0.id) }
                let renderedParts = modeSessionParts(session, mode: viewModel.uiState.activeMode)

                VStack {
                    Spacer()
                    WorkoutRoadmapBar(
                        exercises: visibleExercises,
                        parts: renderedParts,
                        supersetGroups: session.allSupersetGroups(),
                        currentIdx: viewModel.uiState.currentExerciseIdx,
                        currentSetIdx: viewModel.uiState.currentSetIdx,
                        completedSets: viewModel.uiState.completedSets,
                        onSelect: { viewModel.selectExercise($0) },
                        onSelectGroup: { viewModel.selectSupersetGroup($0) },
                        onOpenContext: { exId in exerciseContextExerciseId = exId },
                        enableLongPress: true,
                        sessionAccentColor: resolvedSessionAccentColor,
                        mode: $roadmapMode
                    )
                }
            }
        }
        .onAppear { loadData() }
        .onChange(of: viewModel.uiState.isComplete) { complete in
            if complete { (onComplete ?? onBack)() }
        }
        .sheet(isPresented: $showReplaceExercisePicker) {
            // Replace exercise picker
        }
        .alert("¿Qué deseas hacer?", isPresented: $showExitDialog) {
            Button("Continuar entrenando") { showExitDialog = false }
            Button("Terminar hasta acá") {
                viewModel.finishUpToCurrentPoint()
                showExitDialog = false
            }
            Button("Pausar y salir") {
                viewModel.stopRestTimer()
                onBack()
                showExitDialog = false
            }
            Button("Abandonar sin guardar", role: .destructive) {
                viewModel.stopRestTimer()
                ProgramRepository.shared.clearOngoingWorkout()
                onBack()
                showExitDialog = false
            }
        } message: {
            Text("Tu entrenamiento en curso se perderá si abandonas sin guardar.")
        }
    }

    private func loadData() {
        // Data is loaded in ViewModel.init
    }

    private var resolvedSessionAccentColor: Color {
        resolveSessionAccentColor(viewModel.uiState.session?.background)
    }

    private var mainContent: some View {
        let session = viewModel.uiState.session!
        let modeSession = sessionForActiveMode(session, viewModel.uiState.activeMode, uiState: viewModel.uiState)
        let modeExercises = modeSession.allExercises()
        let skipped = viewModel.uiState.skippedExerciseIds
        let visibleExercises = modeExercises.filter { !skipped.contains($0.id) }
        let currentExercise = visibleExercises[safe: viewModel.uiState.currentExerciseIdx]
        let currentSet = currentExercise?.sets[safe: viewModel.uiState.currentSetIdx]
        let partName = resolveCurrentPartName(modeSession: modeSession, visibleExercises: visibleExercises)
        let headerExerciseInfo = currentExercise.flatMap { workoutCatalogInfo($0) }
        let headerGroup = resolveWorkoutHeaderGroupLabel(partName: partName, type: headerExerciseInfo?.type, category: headerExerciseInfo?.category)
        let showingPostExerciseCardDock = currentExercise != nil && viewModel.uiState.showPostExerciseSheet && viewModel.uiState.postExerciseTargetIdx == viewModel.uiState.currentExerciseIdx
        let isUnilateralDock = currentExercise?.isEffectivelyUnilateral() == true
        let activeDockSide = computeActiveDockSide(currentExercise: currentExercise, isUnilateral: isUnilateralDock)

        let currentSupersetGroupId = currentExercise?.supersetGroupRefOrLegacyId()
        let currentSupersetMembers: [Exercise] = {
            guard let gid = currentSupersetGroupId else { return [] }
            return visibleExercises.filter { $0.supersetGroupRefOrLegacyId() == gid }
        }()
        let currentSupersetMemberIndex = currentSupersetMembers.firstIndex { $0.id == currentExercise?.id } ?? -1
        let isInsideSupersetRound = currentSupersetMembers.count > 1 && currentSupersetMemberIndex >= 0
        let isLastExerciseInSupersetRound = isInsideSupersetRound && currentSupersetMemberIndex == currentSupersetMembers.count - 1
        let canSkipCurrentExerciseOnRestFinish: Bool = {
            if isInsideSupersetRound { return !isLastExerciseInSupersetRound }
            guard let ex = currentExercise else { return false }
            return viewModel.uiState.currentSetIdx < ex.sets.count - 1
        }()

        return ZStack {
            WorkoutV2Body(
                uiState: viewModel.uiState,
                viewModel: viewModel,
                currentExercise: currentExercise,
                visibleExercises: visibleExercises,
                currentSet: currentSet,
                selectedContextTab: selectedExerciseContextTab,
                onSelectedContextTabChange: { selectedExerciseContextTab = $0 },
                sessionAccentColor: resolvedSessionAccentColor,
                headerExerciseName: currentExercise?.name ?? session.name,
                headerSessionName: session.name,
                headerGroupName: headerGroup,
                headerStartTimeMs: viewModel.uiState.startTimeMs,
                headerIsComplete: viewModel.uiState.isComplete,
                headerBackground: session.background,
                headerExerciseTag: viewModel.uiState.exerciseTags[currentExercise?.id ?? ""],
                rmSelectedWeight: rmSelectedWeight,
                onRmWeightConsumed: { rmSelectedWeight = nil },
                onExpandHistory: {
                    let dbId = currentExercise?.exerciseDbId ?? currentExercise?.exerciseId
                    if let id = dbId { viewModel.showHistoryFor(id) }
                },
                onExpandTags: { tagSheetExerciseId = currentExercise?.id },
                onExpandSetup: { setupSheetExerciseId = currentExercise?.id },
                onExpandReplace: {
                    if let id = currentExercise?.id {
                        replaceTargetExerciseId = id
                        showReplaceExercisePicker = true
                    }
                },
                onExpandEdit: { editSheetExerciseId = currentExercise?.id },
                exerciseReadinessMap: viewModel.uiState.exerciseReadinessMap,
                isUnilateral: isUnilateralDock,
                selectedUnilateralSideOverride: selectedUnilateralSideOverride,
                onSelectedUnilateralSideOverride: { selectedUnilateralSideOverride = $0 },
                activeSide: activeDockSide,
                showingPostExerciseCard: showingPostExerciseCardDock
            )

            // Rest timer or feedback overlay
            let activeRestModalState = viewModel.uiState.restModalState
            let isShowingFeedback = viewModel.uiState.showPostExerciseSheet && (visibleExercises[safe: viewModel.uiState.postExerciseTargetIdx] ?? currentExercise) != nil
            if (viewModel.uiState.isRestTimerRunning && activeRestModalState != nil && !viewModel.uiState.isRestMinimized) || isShowingFeedback {
                let postExerciseTarget = visibleExercises[safe: viewModel.uiState.postExerciseTargetIdx] ?? currentExercise
                let restState = activeRestModalState ?? WorkoutRestModalState(
                    activeSeconds: postExerciseTarget?.restTime ?? 90,
                    plannedSeconds: postExerciseTarget?.restTime ?? 90,
                    kind: .standard,
                    exerciseName: postExerciseTarget?.name ?? "",
                    isManualOverride: false
                )
                RestTimerOverlay(
                    state: restState,
                    remainingSeconds: viewModel.uiState.isRestTimerRunning ? viewModel.restTimerRemaining : 0,
                    pendingRestSuggestion: viewModel.uiState.pendingRestSuggestion.map { PendingRestSuggestion(adaptiveSeconds: $0.adaptiveSeconds, lastSet: $0.lastSet) },
                    lastSetOutcome: viewModel.uiState.lastSetOutcomeV2,
                    lastCompletedSet: viewModel.uiState.setJustLoggedKey.flatMap { viewModel.uiState.completedSets[$0] },
                    lastCompletedSets: [],
                    isAdaptiveActive: false,
                    sessionAccentColor: resolvedSessionAccentColor,
                    onDecrease: { viewModel.addRestTime(-15) },
                    onIncrease: { viewModel.addRestTime(15) },
                    onSkip: { viewModel.stopRestTimer() },
                    skipExerciseLabel: canSkipCurrentExerciseOnRestFinish ? (isInsideSupersetRound ? "Saltar ronda" : currentExercise.map { "Saltar series restantes de \($0.name)" }) : nil,
                    onSkipExercise: canSkipCurrentExerciseOnRestFinish ? {
                        if isInsideSupersetRound { viewModel.skipCurrentSupersetRound() }
                        else { viewModel.deferSkipRemainingCurrentExercise() }
                    } : nil,
                    onUseAdaptive: { viewModel.resolvePendingRestSuggestion(useAdaptive: true) },
                    postExerciseFeedbackContent: isShowingFeedback ? { AnyView(postExerciseFeedbackContent(postExerciseTarget: postExerciseTarget)) } : nil,
                    feedbackExerciseCount: 1,
                    onMinimize: { viewModel.toggleRestMinimized() }
                )
            } else if viewModel.uiState.isRestTimerRunning && viewModel.uiState.isRestMinimized, let state = activeRestModalState {
                VStack {
                    RestTimerPill(
                        remainingSeconds: viewModel.restTimerRemaining,
                        totalSeconds: max(state.activeSeconds, 1),
                        exerciseName: state.exerciseName,
                        sessionAccentColor: resolvedSessionAccentColor,
                        onClick: { viewModel.toggleRestMinimized() }
                    )
                    Spacer()
                }
            }

            // Readiness sheet
            if showReadinessSheet {
                WorkoutReadinessSheet(
                    showReadinessSheet: showReadinessSheet,
                    gender: viewModel.uiState.session?.gender,
                    sessionMuscleStartingBatteries: [:],
                    readinessNeuralStart: 0,
                    readinessMuscularStart: 0,
                    readinessSpinalStart: 0,
                    onSave: { _, _, _, _, _ in readinessSheetDismissed = true },
                    patternReadiness: viewModel.uiState.patternReadiness,
                    exerciseReadinessMap: viewModel.uiState.exerciseReadinessMap,
                    sessionExercises: session.exercises,
                    onDismissWithoutVerify: { readinessSheetDismissed = true },
                    initialDiscomforts: []
                )
            }

            // Other sheets, dialogs, etc handled via .sheet and .alert
        }
    }

    @ViewBuilder
    private func postExerciseFeedbackContent(postExerciseTarget: Exercise?) -> some View {
        if let target = postExerciseTarget {
            PostExerciseCompactContent(
                exerciseName: target.name,
                showPerceivedIntensity: !exerciseHasPlannedIntensity(target),
                onSave: { result in
                    viewModel.savePostExerciseFeedback(PostExerciseFeedback(
                        exerciseId: target.id,
                        exerciseName: target.name,
                        technicalQuality: result.technicalQuality,
                        discomfortIds: result.discomfortIds.isEmpty ? ["none"] : result.discomfortIds,
                        perceivedIntensityRpe: result.perceivedIntensityRpe,
                        perceivedFailure: result.perceivedFailure
                    ))
                    viewModel.dismissPostExerciseSheet()
                }
            )
        }
    }

    private func computeActiveDockSide(currentExercise: Exercise?, isUnilateral: Bool) -> String? {
        guard let ex = currentExercise, isUnilateral else { return nil }
        let expectedSides = ex.expectedSidesForSet(viewModel.uiState.currentSetIdx)
        if let override = selectedUnilateralSideOverride, expectedSides.contains(override) { return override }
        return expectedSides.first { side in
            !viewModel.uiState.completedSets.keys.contains("\(ex.id)_\(viewModel.uiState.currentSetIdx)_\(side.prefix(1).uppercased())")
        } ?? expectedSides.first
    }

    private func resolveCurrentPartName(modeSession: Session, visibleExercises: [Exercise]) -> String {
        guard let exId = visibleExercises[safe: viewModel.uiState.currentExerciseIdx]?.id else { return "Sesion" }
        return modeSession.parts.first { part in part.exercises.contains { $0.id == exId } }?.name ?? "Sesion"
    }
}

// MARK: - Helper: Resolve Session Accent Color

func resolveSessionAccentColor(_ background: SessionBackground?) -> Color {
    guard let bg = background else { return Color(hex: "#E08E45") ?? .orange }
    if bg.type == .color {
        switch bg.value {
        case "gradient://ember": return Color(hex: "#E08E45") ?? .orange
        case "gradient://lagoon": return Color(hex: "#5FA8D3") ?? .blue
        case "gradient://velvet": return Color(hex: "#E26D5A") ?? .red
        case "gradient://forest": return Color(hex: "#95D5B2") ?? .green
        case "solid://obsidian": return Color(hex: "#3B82F6") ?? .blue
        case "solid://steel": return Color(hex: "#94A3B8") ?? .gray
        case "solid://ember-red": return Color(hex: "#EF4444") ?? .red
        case "solid://ocean": return Color(hex: "#38BDF8") ?? .cyan
        case "solid://moss": return Color(hex: "#4ADE80") ?? .green
        default: return Color(hex: "#E08E45") ?? .orange
        }
    }
    return Color(hex: "#3B82F6") ?? .blue
}

// MARK: - Helper: Session for active mode

private func sessionForActiveMode(_ base: Session, _ mode: WeekVariant, uiState: WorkoutUiState? = nil) -> Session {
    if let state = uiState {
        return state.session ?? base
    }
    switch mode {
    case .A: return base
    case .B: return base.sessionB ?? base
    case .C: return base.sessionC ?? base
    case .D: return base.sessionD ?? base
    }
}

private func modeSessionParts(_ session: Session, mode: WeekVariant) -> [SessionPart] {
    let s = sessionForActiveMode(session, mode)
    if s.parts.isNotEmpty { return s.parts }
    return [SessionPart(id: "default", name: "Sesion Principal", exercises: s.exercises)]
}

// MARK: - WorkoutHeader colors

private func workoutHeaderColors(_ background: SessionBackground?) -> [Color] {
    guard let bg = background else { return [Color(hex: "#20110F")!, Color(hex: "#8D3D2E")!, Color(hex: "#E08E45")!] }
    if bg.type == .color {
        switch bg.value {
        case "gradient://ember": return [Color(hex: "#20110F")!, Color(hex: "#8D3D2E")!, Color(hex: "#E08E45")!]
        case "gradient://lagoon": return [Color(hex: "#0D1B2A")!, Color(hex: "#1B4965")!, Color(hex: "#5FA8D3")!]
        case "gradient://velvet": return [Color(hex: "#1C1024")!, Color(hex: "#5B2A86")!, Color(hex: "#E26D5A")!]
        case "gradient://forest": return [Color(hex: "#102A1F")!, Color(hex: "#2D6A4F")!, Color(hex: "#95D5B2")!]
        case "solid://obsidian": return [Color(hex: "#111318")!, Color(hex: "#111318")!]
        case "solid://steel": return [Color(hex: "#334155")!, Color(hex: "#334155")!]
        case "solid://ember-red": return [Color(hex: "#7F1D1D")!, Color(hex: "#7F1D1D")!]
        case "solid://ocean": return [Color(hex: "#0F3D5E")!, Color(hex: "#0F3D5E")!]
        case "solid://moss": return [Color(hex: "#244B3C")!, Color(hex: "#244B3C")!]
        default: return [Color(hex: "#20110F")!, Color(hex: "#8D3D2E")!, Color(hex: "#E08E45")!]
        }
    }
    return [Color(hex: "#111318")!, Color(hex: "#111318")!]
}

// MARK: - WorkoutChronometer

private struct WorkoutChronometer: View {
    let startTimeMs: Int64
    let isComplete: Bool
    let sessionTimeRemainingSeconds: Int?
    let onAdjustTimeLimit: (Int) -> Void

    @State private var elapsedSeconds = 0
    @State private var showAdjustDialog = false

    let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        let hasLimit = sessionTimeRemainingSeconds != nil
        let displayRemaining = sessionTimeRemainingSeconds ?? 0
        let isExceeded = hasLimit && displayRemaining < 0
        let absSeconds = abs(displayRemaining)
        let minutes = absSeconds / 60
        let seconds = absSeconds % 60
        let sign = isExceeded ? "-" : ""
        let text = hasLimit ? "Lim: \(sign)\(String(format: "%02d:%02d", minutes, seconds))" : formatElapsed(seconds: elapsedSeconds)
        let textColor = isExceeded ? Color(hex: "#FF5252")! : Color.white.opacity(0.85)

        Text(text)
            .font(.system(size: 11, weight: .black))
            .foregroundColor(textColor)
            .onTapGesture { showAdjustDialog = true }
            .onReceive(timer) { _ in
                guard !isComplete else { return }
                let now = Int64(Date().timeIntervalSince1970 * 1000)
                elapsedSeconds = max(0, Int((now - startTimeMs) / 1000))
            }
            .alert("Límite de Tiempo de Sesión", isPresented: $showAdjustDialog) {
                Button("-5 min") { onAdjustTimeLimit(-5); showAdjustDialog = false }
                Button("+5 min") { onAdjustTimeLimit(5); showAdjustDialog = false }
                Button("+15 min") { onAdjustTimeLimit(15); showAdjustDialog = false }
                if !hasLimit {
                    Button("Fijar 30 min") { onAdjustTimeLimit(30); showAdjustDialog = false }
                    Button("Fijar 60 min") { onAdjustTimeLimit(60); showAdjustDialog = false }
                }
                Button("Cerrar", role: .cancel) { showAdjustDialog = false }
            } message: {
                Text(hasLimit ? "Tiempo restante: \(displayRemaining / 60) min.\n¿Deseas ajustar la duración de la sesión?" : "No se ha configurado un límite de tiempo para esta sesión.\n¿Deseas fijar un límite?")
            }
    }
}

// MARK: - WorkoutHeaderBar

private struct WorkoutHeaderBar: View {
    let exerciseName: String
    let sessionName: String
    let groupName: String?
    let startTimeMs: Int64
    let isComplete: Bool
    let background: SessionBackground?
    let sessionTimeRemainingSeconds: Int?
    let onAdjustTimeLimit: (Int) -> Void
    let exerciseTag: String?
    let isSuperset: Bool
    let exerciseReadiness: ExerciseReadiness?
    let activeMainTags: [WorkoutTag]
    let activeSubTags: [WorkoutSubTag]
    let onTagClick: (String) -> Void
    let onRemoveSubTag: (String) -> Void
    let onCreateTagClick: () -> Void

    var body: some View {
        let colors = workoutHeaderColors(background)
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom)
                .frame(maxWidth: .infinity, height: 160)
            LinearGradient(colors: [.clear, .clear, Color(.systemBackground)], startPoint: .top, endPoint: .bottom)
                .frame(maxWidth: .infinity, height: 160)
            VStack(spacing: 4) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(exerciseName)
                            .font(.system(size: 20, weight: .black))
                            .foregroundColor(.white)
                            .lineLimit(2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        HStack(spacing: 0) {
                            if let g = groupName, !g.isEmpty {
                                Text("\(g) · ")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.white.opacity(0.85))
                            }
                            Text(sessionName)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white.opacity(0.85))
                        }
                        HStack(spacing: 6) {
                            HStack(spacing: 5) {
                                Image(systemName: "timer")
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.85))
                                WorkoutChronometer(
                                    startTimeMs: startTimeMs,
                                    isComplete: isComplete,
                                    sessionTimeRemainingSeconds: sessionTimeRemainingSeconds,
                                    onAdjustTimeLimit: onAdjustTimeLimit
                                )
                            }
                            .padding(.horizontal, 9)
                            .padding(.vertical, 3)
                            .background(Color.white.opacity(0.12))
                            .clipShape(Capsule())

                            if let readiness = exerciseReadiness {
                                let score = readiness.overallScore
                                let chipColor: Color = score >= 75 ? Color(hex: "#4CAF50")! : score >= 50 ? Color(hex: "#FFC107")! : Color(hex: "#FF5252")!
                                HStack(spacing: 5) {
                                    Circle()
                                        .fill(chipColor)
                                        .frame(width: 6, height: 6)
                                    Text("\(score)%")
                                        .font(.system(size: 11, weight: .black))
                                        .foregroundColor(.white.opacity(0.85))
                                }
                                .padding(.horizontal, 9)
                                .padding(.vertical, 3)
                                .background(chipColor.opacity(0.18))
                                .clipShape(Capsule())
                            }

                            if isSuperset {
                                HStack(spacing: 3) {
                                    Image(systemName: "arrow.left.arrow.right")
                                        .font(.system(size: 10))
                                    Text("Superserie")
                                        .font(.system(size: 10, weight: .black))
                                }
                                .foregroundColor(.white)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(Color(hex: "#EF4444")!.opacity(0.82))
                                .clipShape(Capsule())
                                .overlay(Capsule().stroke(Color.white.opacity(0.28), lineWidth: 1))
                            }

                            ForEach(activeMainTags) { tag in
                                Button(action: { onTagClick(tag.id) }) {
                                    HStack(spacing: 3) {
                                        Text(tag.name)
                                            .font(.system(size: 10, weight: .black))
                                            .foregroundColor(.white.opacity(0.9))
                                            .lineLimit(1)
                                        Image(systemName: "arrowtriangle.down.fill")
                                            .font(.system(size: 8))
                                            .foregroundColor(.white.opacity(0.7))
                                    }
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 3)
                                    .background(Color.white.opacity(0.18))
                                    .clipShape(Capsule())
                                    .overlay(Capsule().stroke(Color.white.opacity(0.28), lineWidth: 1))
                                }
                            }
                            ForEach(activeSubTags) { subTag in
                                Button(action: { onRemoveSubTag(subTag.id) }) {
                                    HStack(spacing: 3) {
                                        Text(subTag.name)
                                            .font(.system(size: 9))
                                            .foregroundColor(.white.opacity(0.7))
                                        Image(systemName: "xmark")
                                            .font(.system(size: 8))
                                            .foregroundColor(.white.opacity(0.5))
                                    }
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 3)
                                    .background(Color.white.opacity(0.10))
                                    .clipShape(Capsule())
                                    .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1))
                                }
                            }
                            Button(action: onCreateTagClick) {
                                Image(systemName: "plus")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.8))
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 3)
                                    .background(Color.clear)
                                    .clipShape(Capsule())
                                    .overlay(Capsule().stroke(Color.white.opacity(0.20), lineWidth: 1))
                            }
                            if activeMainTags.isEmpty, let tag = exerciseTag, !tag.isEmpty {
                                Text(tag)
                                    .font(.system(size: 10, weight: .black))
                                    .foregroundColor(.white.opacity(0.9))
                                    .lineLimit(1)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(Color.white.opacity(0.16))
                                    .clipShape(Capsule())
                                    .overlay(Capsule().stroke(Color.white.opacity(0.28), lineWidth: 1))
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
    }
}

// MARK: - WorkoutV2Body

private struct WorkoutV2Body: View {
    let uiState: WorkoutUiState
    let viewModel: WorkoutViewModel
    let currentExercise: Exercise?
    let visibleExercises: [Exercise]
    let currentSet: ExerciseSet?
    let selectedContextTab: WorkoutExerciseContextTab?
    let onSelectedContextTabChange: (WorkoutExerciseContextTab?) -> Void
    let sessionAccentColor: Color
    let headerExerciseName: String
    let headerSessionName: String
    let headerGroupName: String?
    let headerStartTimeMs: Int64
    let headerIsComplete: Bool
    let headerBackground: SessionBackground?
    let headerExerciseTag: String?
    var rmSelectedWeight: Double? = nil
    var onRmWeightConsumed: () -> Void = {}
    var onExpandHistory: () -> Void = {}
    var onExpandTags: () -> Void = {}
    var onExpandSetup: () -> Void = {}
    var onExpandReplace: () -> Void = {}
    var onExpandEdit: () -> Void = {}
    var exerciseReadinessMap: [String: ExerciseReadiness] = [:]
    var isUnilateral: Bool = false
    var selectedUnilateralSideOverride: String? = nil
    var onSelectedUnilateralSideOverride: (String?) -> Void = { _ in }
    var activeSide: String? = nil
    var showingPostExerciseCard: Bool = false

    @State private var tagManagerTagId: String? = nil
    @State private var showCreateTagDialog = false
    @State private var newTagName = ""
    @State private var drainOverlayState: ExerciseDrainOverlayStateV2? = nil
    @State private var expandedSupersetWarmups: Set<String> = []
    @State private var pendingUpdateAction: (() -> Void)? = nil

    var body: some View {
        let currentExerciseKey = currentExercise.map { viewModel.canonicalExerciseKey($0) } ?? ""
        let currentExerciseTags: [WorkoutTag] = uiState.userCreatedTags[currentExerciseKey] ?? []
        let currentExerciseActiveMainTags: [WorkoutTag] = {
            guard let exId = currentExercise?.id else { return [] }
            let tagIds = Set(uiState.activeTagsByExercise[exId] ?? [])
            return currentExerciseTags.filter { tagIds.contains($0.id) }
        }()
        let currentExerciseActiveSubTags: [WorkoutSubTag] = {
            guard let exId = currentExercise?.id else { return [] }
            let subTagIds = Set(uiState.activeSubTagsByExercise[exId] ?? [])
            return currentExerciseTags.flatMap { $0.subTags }.filter { subTagIds.contains($0.id) }
        }()
        let currentExerciseReadiness = currentExercise.flatMap { exerciseReadinessMap[$0.id] }

        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 0) {
                WorkoutHeaderBar(
                    exerciseName: headerExerciseName,
                    sessionName: headerSessionName,
                    groupName: headerGroupName,
                    startTimeMs: headerStartTimeMs,
                    isComplete: headerIsComplete,
                    background: headerBackground,
                    sessionTimeRemainingSeconds: uiState.sessionTimeRemainingSeconds,
                    onAdjustTimeLimit: { viewModel.adjustSessionTimeLimit($0) },
                    exerciseTag: headerExerciseTag,
                    isSuperset: currentExercise?.isInSuperset() == true,
                    exerciseReadiness: currentExerciseReadiness,
                    activeMainTags: currentExerciseActiveMainTags,
                    activeSubTags: currentExerciseActiveSubTags,
                    onTagClick: { tagId in tagManagerTagId = tagId },
                    onRemoveSubTag: { subTagId in viewModel.toggleSubTagActive(currentExercise?.id ?? "", subTagId) },
                    onCreateTagClick: { showCreateTagDialog = true }
                )

                if let ex = currentExercise, let set = currentSet {
                    if !showingPostExerciseCard {
                        let currentExerciseInfo = catalogInfoForExercise(ex)
                        let currentExerciseCompleted = CompletedExercise(
                            exerciseId: ex.id,
                            exerciseName: ex.name,
                            exerciseDbId: ex.exerciseDbId ?? ex.exerciseId,
                            restTime: ex.restTime ?? 90,
                            supersetId: ex.supersetGroupRefOrLegacyId(),
                            sets: ex.sets.indices.flatMap { idx in
                                [
                                    uiState.completedSets["\(ex.id)_\(idx)"],
                                    uiState.completedSets["\(ex.id)_\(idx)_L"],
                                    uiState.completedSets["\(ex.id)_\(idx)_R"]
                                ].compactMap { $0 }
                            }
                        )
                        let currentExerciseDrain = currentExerciseCompleted.sets.isEmpty
                            ? PredictedDrain(cns: 0, muscular: 0, spinal: 0)
                            : AugeFatigueEngine.calculateCompletedSessionDrain(
                                completedExercises: [currentExerciseCompleted],
                                exerciseDb: catalogExerciseIndex()
                            )

                        WorkoutExerciseTabs(
                            currentExercise: ex,
                            currentSet: set,
                            currentExerciseInfo: currentExerciseInfo,
                            drain: currentExerciseDrain,
                            exerciseTag: uiState.exerciseTags[ex.id],
                            profiles: viewModel.profilesForExercise(ex),
                            activeProfileId: uiState.activeContextProfileByExerciseId[ex.id],
                            selectedTab: selectedContextTab,
                            onSelectedTabChange: onSelectedContextTabChange,
                            onTagSet: { tag in
                                if tag.isEmpty { viewModel.clearExerciseTag(ex.id) }
                                else { viewModel.setExerciseTag(ex.id, tag) }
                            },
                            onSelectProfile: { viewModel.setActiveContextProfile(ex.id, $0) },
                            onSaveProfile: { viewModel.upsertContextProfile(exercise: ex, profile: $0) },
                            onUpdateExercise: { transform in
                                viewModel.updateExerciseDefinition(ex.id) { transform($0) }
                            },
                            onUpdateCurrentSetPlan: { setId, transform in
                                viewModel.updateExerciseSetPlan(ex.id, setId, transform)
                            },
                            onExpandHistory: onExpandHistory,
                            onExpandTags: onExpandTags,
                            onExpandSetup: onExpandSetup,
                            onExpandReplace: onExpandReplace,
                            onExpandEdit: onExpandEdit,
                            sessionAccentColor: sessionAccentColor,
                            sessionEnergy: uiState.liveEnergySummary,
                            allowExerciseManagementActions: !ex.isInSuperset(),
                            userTags: viewModel.allUserTags,
                            exerciseReadiness: uiState.exerciseReadinessMap[ex.id],
                            userWorkoutTags: currentExerciseTags,
                            activeMainTagIds: Set(uiState.activeTagsByExercise[ex.id] ?? []),
                            activeSubTagIds: Set(currentExerciseActiveSubTags.map { $0.id }),
                            onMainTagToggle: { viewModel.toggleMainTagActive(ex.id, $0) },
                            onSubTagToggle: { viewModel.toggleSubTagActive(ex.id, $0) },
                            onCreateTag: { viewModel.createTag(ex.id, name: $0) },
                            onDeleteTag: { viewModel.deleteTag(ex.id, tagId: $0) },
                            onAddSubTag: { tagId, name, category in viewModel.addSubTag(ex.id, tagId: tagId, name: name, category: category) },
                            onRemoveSubTag: { tagId, subTagId in viewModel.removeSubTag(ex.id, tagId: tagId, subTagId: subTagId) }
                        )

                        let setPagerPages = buildSetPagerPages(exercise: ex, isUnilateral: isUnilateral)
                        let totalSetPages = max(setPagerPages.count, 1)
                        let activeSwipePageIndex = computeActiveSwipePageIndex(
                            setPagerPages: setPagerPages,
                            uiState: uiState,
                            currentExercise: ex,
                            activeSide: activeSide,
                            isUnilateral: isUnilateral
                        )

                        let currentSupersetGroupId = ex.supersetGroupRefOrLegacyId()
                        let currentSupersetMembers: [Exercise] = {
                            guard let gid = currentSupersetGroupId else { return [] }
                            return visibleExercises.filter { $0.supersetGroupRefOrLegacyId() == gid }
                        }()

                        if let gid = currentSupersetGroupId, currentSupersetMembers.count > 1 {
                            let pageSpec = setPagerPages[safe: activeSwipePageIndex]
                            if pageSpec?.type == .normal {
                                SupersetSetPager(
                                    members: currentSupersetMembers,
                                    currentExerciseId: ex.id,
                                    currentRoundIndex: uiState.currentSetIdx,
                                    completedSets: uiState.completedSets,
                                    sessionAccentColor: sessionAccentColor,
                                    onSelectRound: { viewModel.selectSupersetRound($0) }
                                )
                            }
                        } else {
                            WorkoutSetPager(
                                items: buildPagerItems(
                                    setPagerPages: setPagerPages,
                                    currentExercise: ex,
                                    uiState: uiState,
                                    activeSide: activeSide,
                                    isUnilateral: isUnilateral,
                                    activeSwipePageIndex: activeSwipePageIndex
                                ),
                                activePageIndex: activeSwipePageIndex,
                                onSelectPage: { pageIndex in
                                    let targetPage = setPagerPages[safe: pageIndex]
                                    if let tp = targetPage {
                                        let key: String = {
                                            switch tp.type {
                                            case .mobility:
                                                return ex.mobilitySeries.first { "\(ex.id)_\($0.id)" !in uiState.mobilityCompletedExerciseIds }
                                                    .map { "\(ex.id)_\($0.id)" } ?? ""
                                            case .warmup:
                                                return ex.warmupSets.first { "\(ex.id)_warmup_\($0.id)" !in uiState.warmupCompletedExerciseIds }
                                                    .map { "\(ex.id)_warmup_\($0.id)" } ?? ""
                                            case .normal:
                                                return WorkoutStepRules.workingStepKey(ex.id, tp.setIndex, tp.side)
                                            }
                                        }()
                                        if !key.isEmpty { viewModel.selectWorkoutStep(key) }
                                    }
                                },
                                sessionAccentColor: sessionAccentColor,
                                isUnilateral: isUnilateral,
                                selectedSide: activeSide,
                                sideCompleted: isUnilateral ? { setIdx, side in
                                    let safeIdx = min(setIdx, max(0, ex.sets.count - 1))
                                    return uiState.completedSets.keys.contains("\(ex.id)_\(safeIdx)_\(side.prefix(1).uppercased())")
                                } : nil,
                                onAddSet: { viewModel.addSetToCurrentExercise() }
                            )
                        }

                        // Time progress indicator
                        let currentPart = uiState.session?.parts.first { part in
                            part.exercises.contains { $0.id == ex.id }
                        }
                        let targetMin = ex.targetDurationMinutes ?? currentPart?.targetDurationMinutes
                        if let tMin = targetMin, tMin > 0 {
                            let targetSeconds = tMin * 60
                            let progress = min(CGFloat(0) / CGFloat(targetSeconds), 1.0)
                            let barColor: Color = progress >= 0.9 ? Color(hex: "#EF4444")! : progress >= 0.75 ? Color(hex: "#F59E0B")! : sessionAccentColor
                            ProgressView(value: progress)
                                .tint(barColor)
                                .frame(maxWidth: .infinity, maxHeight: 3)
                                .padding(.horizontal, 8)
                        }

                        // Set cards via pager simulation
                        if let pageSpec = setPagerPages[safe: activeSwipePageIndex] {
                            switch pageSpec.type {
                            case .mobility:
                                MobilitySetCard(exercise: ex, uiState: uiState, viewModel: viewModel, sessionAccentColor: sessionAccentColor)
                            case .warmup:
                                WarmupSetCard(exercise: ex, uiState: uiState, viewModel: viewModel, sessionAccentColor: sessionAccentColor)
                            case .normal:
                                let activeSetIndex = min(pageSpec.setIndex, max(0, ex.sets.count - 1))
                                let activeSet = ex.sets[safe: activeSetIndex] ?? set
                                let cardSide = pageSpec.side ?? activeSide
                                let isActivePage = true
                                let activeGhostSet = viewModel.getGhostForSet(
                                    exerciseId: ex.id,
                                    setIdx: activeSetIndex,
                                    exerciseDbId: ex.exerciseDbId ?? ex.exerciseId,
                                    activeTag: uiState.exerciseTags[ex.id]
                                )
                                let activeWeightSuggestion = viewModel.getWeightSuggestionWithAutoRegulation(ex, activeSetIndex, uiState.exerciseTags[ex.id])
                                let sessionCompletedSet = uiState.completedSets[isUnilateral ? {
                                    switch cardSide {
                                    case "left": return "\(ex.id)_\(activeSetIndex)_L"
                                    case "right": return "\(ex.id)_\(activeSetIndex)_R"
                                    default: return "\(ex.id)_\(activeSetIndex)"
                                    }
                                }() : "\(ex.id)_\(activeSetIndex)"]

                                SetInputCardV2(
                                    exercise: ex,
                                    setIndex: activeSetIndex,
                                    currentSet: activeSet,
                                    recordActionHolder: RecordActionHolder(),
                                    ghostSet: activeGhostSet,
                                    sessionCompletedSet: sessionCompletedSet,
                                    weightSuggestion: activeWeightSuggestion,
                                    sessionAccentColor: sessionAccentColor,
                                    persistedLoadModeBySet: uiState.persistedLoadModeBySet,
                                    persistedLoadModeByExercise: uiState.persistedLoadModeByExercise,
                                    amrapCalibrationMessage: uiState.amrapCalibrationMessage,
                                    isActivePage: isActivePage,
                                    initialDraft: viewModel.getSetDraft(ex.id, activeSetIndex, cardSide),
                                    onDraftChange: { draft, side in viewModel.updateSetDraft(ex.id, activeSetIndex, side, draft) },
                                    activeSide: cardSide,
                                    sideLocked: isUnilateral && cardSide != nil,
                                    rmSuggestedWeight: rmSelectedWeight,
                                    onRmWeightConsumed: onRmWeightConsumed,
                                    onShowHistory: {
                                        let dbId = ex.exerciseDbId ?? ex.exerciseId ?? ""
                                        viewModel.showHistoryFor(dbId)
                                    },
                                    onGoToPrevSet: { viewModel.navigateAdjacentWorkingStep(forward: false) },
                                    onGoToNextSet: { viewModel.navigateAdjacentWorkingStep(forward: true) },
                                    onSetBodyWeight: { viewModel.setCurrentBodyWeight($0) },
                                    initialBodyWeight: viewModel.currentBodyWeight(),
                                    onExecutionError: {
                                        viewModel.recordSetV2(weight: 0, value: 0, intensity: nil, advanced: SetAdvancedFeedback(executionError: true, failureReason: "execution_error", isFailedSet: true), loadMode: .LOAD, unitMode: .REPS, bodyWeight: viewModel.currentBodyWeight(), side: nil, tagId: uiState.exerciseTags[ex.id], setupId: activeSet.setupId, machineBrand: activeSet.machineBrand, amrapOverride: false, setIdxOverride: activeSetIndex)
                                    },
                                    onRecordV2: { loadMode, unitMode, weight, value, intensity, advanced, amrap, bodyWeight, side in
                                        let action = {
                                            viewModel.recordSetV2(weight: weight, value: value, intensity: intensity, advanced: advanced, loadMode: loadMode, unitMode: unitMode, bodyWeight: bodyWeight, side: side, tagId: uiState.exerciseTags[ex.id], setupId: activeSet.setupId, machineBrand: activeSet.machineBrand, amrapOverride: amrap, setIdxOverride: activeSetIndex)
                                        }
                                        let updateKey = side.map { "\(ex.id)_\(activeSetIndex)_\($0.prefix(1).uppercased())" } ?? "\(ex.id)_\(activeSetIndex)"
                                        if uiState.completedSets.keys.contains(updateKey) {
                                            pendingUpdateAction = action
                                        } else {
                                            action()
                                        }
                                    },
                                    exerciseReadiness: exerciseReadinessMap[ex.id],
                                    readinessAdjustment: uiState.readinessAdjustments["\(ex.id)_\(activeSetIndex)"],
                                    onApplyReadinessAdjustment: { viewModel.applyReadinessAdjustment(ex.id, activeSetIndex, $0) }
                                )
                            }
                        }

                        if !showingPostExerciseCard, let notice = uiState.imbalanceNotice, !notice.isEmpty {
                            HStack {
                                Text(notice)
                                    .font(.system(size: 12))
                                    .foregroundColor(Color(.systemRed))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 10)
                            }
                            .frame(maxWidth: .infinity)
                            .background(Color(.systemRed).opacity(0.72))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .padding(.horizontal, 10)
                        }
                    }
                } else {
                    // No current exercise
                }
                Spacer().frame(height: 120)
            }
        }
        .safeAreaInset(edge: .bottom) { Color.clear.frame(height: 112) }

        // Tag Manager Dialog
        if let tagId = tagManagerTagId, let ex = currentExercise {
            let tag = currentExerciseActiveMainTags.first { $0.id == tagId }
            if let t = tag {
                WorkoutTagManagerModal(
                    tag: t,
                    exerciseId: ex.id,
                    onRename: { newName in
                        viewModel.renameTag(ex.id, tagId: tagId, newName: newName)
                        tagManagerTagId = nil
                    },
                    onDelete: {
                        viewModel.deleteTag(ex.id, tagId: tagId)
                        tagManagerTagId = nil
                    },
                    onAddSubTag: { name, category in
                        viewModel.addSubTag(ex.id, tagId: tagId, name: name, category: category)
                    },
                    onRemoveSubTag: { subTagId in
                        viewModel.removeSubTag(ex.id, tagId: tagId, subTagId: subTagId)
                    },
                    onToggleSubTagActive: { subTagId in
                        viewModel.toggleSubTagActive(ex.id, subTagId)
                    },
                    activeSubTagIds: Set(uiState.activeSubTagsByExercise[ex.id] ?? []),
                    onDismiss: { tagManagerTagId = nil }
                )
            } else {
                tagManagerTagId = nil
            }
        }

        // Create Tag Dialog
        if showCreateTagDialog, let ex = currentExercise {
            Color.black.opacity(0.4).ignoresSafeArea().onTapGesture { showCreateTagDialog = false }
            VStack(spacing: 16) {
                Text("Nueva etiqueta")
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(.white)
                TextField("Nombre de la etiqueta", text: $newTagName)
                    .textFieldStyle(.plain)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                Text("Puedes agregar sub-etiquetas después de crearla.")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                HStack(spacing: 12) {
                    Button("Cancelar") { showCreateTagDialog = false }
                        .buttonStyle(.bordered)
                    Button("Crear") {
                        if !newTagName.trimmingCharacters(in: .whitespaces).isEmpty {
                            viewModel.createTag(ex.id, name: newTagName.trimmingCharacters(in: .whitespaces))
                        }
                        showCreateTagDialog = false
                        newTagName = ""
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(newTagName.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .padding(24)
            .background(Color(.systemGray5))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .padding(32)
        }

        // Pending update dialog
        if pendingUpdateAction != nil {
            Color.black.opacity(0.4).ignoresSafeArea()
            VStack(spacing: 12) {
                Text("Actualizar serie")
                    .font(.headline)
                Text("Esta serie ya estaba registrada. ¿Quieres actualizarla?")
                    .font(.subheadline)
                HStack(spacing: 12) {
                    Button("Cancelar") { pendingUpdateAction = nil }
                    Button("Actualizar") {
                        pendingUpdateAction?()
                        pendingUpdateAction = nil
                    }
                }
            }
            .padding()
            .background(Color(.systemBackground))
            .cornerRadius(16)
            .padding()
        }

        // Drain overlay
        if let overlay = drainOverlayState {
            ExerciseDrainOverlayCard(state: overlay)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .transition(.opacity)
        }
    }

    private func buildSetPagerPages(exercise: Exercise, isUnilateral: Bool) -> [WorkoutSetSwipePage] {
        var pages: [WorkoutSetSwipePage] = []
        if exercise.mobilitySeries.isNotEmpty {
            pages.append(WorkoutSetSwipePage(type: .mobility, setIndex: 0))
        }
        if exercise.warmupSets.isNotEmpty {
            pages.append(WorkoutSetSwipePage(type: .warmup, setIndex: 0))
        }
        for (i, _) in exercise.sets.enumerated() {
            if isUnilateral {
                let expectedSides = exercise.expectedSidesForSet(i)
                for side in expectedSides {
                    pages.append(WorkoutSetSwipePage(type: .normal, setIndex: i, side: side))
                }
            } else {
                pages.append(WorkoutSetSwipePage(type: .normal, setIndex: i, side: nil))
            }
        }
        if pages.isEmpty {
            pages.append(WorkoutSetSwipePage(type: .normal, setIndex: 0, side: nil))
        }
        return pages
    }

    private func computeActiveSwipePageIndex(setPagerPages: [WorkoutSetSwipePage], uiState: WorkoutUiState, currentExercise: Exercise, activeSide: String?, isUnilateral: Bool) -> Int {
        let idx = setPagerPages.firstIndex { page in
            switch page.type {
            case .mobility:
                return currentExercise.mobilitySeries.contains { uiState.activeStepKey == "\(currentExercise.id)_\($0.id)" }
            case .warmup:
                return currentExercise.warmupSets.contains { uiState.activeStepKey == "\(currentExercise.id)_warmup_\($0.id)" }
            case .normal:
                return page.setIndex == uiState.currentSetIdx && (!isUnilateral || page.side == activeSide)
            }
        }
        return idx ?? 0
    }

    private func buildPagerItems(setPagerPages: [WorkoutSetSwipePage], currentExercise: Exercise, uiState: WorkoutUiState, activeSide: String?, isUnilateral: Bool, activeSwipePageIndex: Int) -> [WorkoutSetPagerItem] {
        setPagerPages.enumerated().map { idx, page in
            let label: String = {
                switch page.type {
                case .mobility: return "M"
                case .warmup: return "A"
                case .normal: return "S\(page.setIndex + 1)"
                }
            }()
            let isDone: Bool = {
                switch page.type {
                case .mobility:
                    return currentExercise.mobilitySeries.allSatisfy { "\(currentExercise.id)_\($0.id)" in uiState.mobilityCompletedExerciseIds }
                case .warmup:
                    return currentExercise.warmupSets.allSatisfy {
                        "\(currentExercise.id)_warmup_\($0.id)" in uiState.warmupCompletedExerciseIds ||
                        currentExercise.id in uiState.warmupCompletedExerciseIds
                    }
                case .normal:
                    let bilateralDone = uiState.completedSets.keys.contains("\(currentExercise.id)_\(page.setIndex)")
                    let expectedSides = currentExercise.expectedSidesForSet(page.setIndex)
                    return bilateralDone || (isUnilateral && expectedSides.allSatisfy { s in
                        uiState.completedSets.keys.contains("\(currentExercise.id)_\(page.setIndex)_\(s.prefix(1).uppercased())")
                    })
                }
            }()
            let isActive: Bool = {
                switch page.type {
                case .mobility:
                    return currentExercise.mobilitySeries.contains { uiState.activeStepKey == "\(currentExercise.id)_\($0.id)" }
                case .warmup:
                    return currentExercise.warmupSets.contains { uiState.activeStepKey == "\(currentExercise.id)_warmup_\($0.id)" }
                case .normal:
                    return (uiState.activeStepKey == nil && page.setIndex == uiState.currentSetIdx) ||
                        uiState.activeStepKey == WorkoutStepRules.workingStepKey(currentExercise.id, page.setIndex, page.side)
                }
            }()
            let state: WorkoutSetCardVisualState = isActive ? .active : isDone ? .completed : .future
            return WorkoutSetPagerItem(
                index: idx,
                label: label,
                state: state,
                isEditing: false,
                side: page.type == .normal ? page.side : nil,
                isWarmupOrFeedback: page.type != .normal
            )
        }
    }
}

// MARK: - Mobility Set Card

private struct MobilitySetCard: View {
    let exercise: Exercise
    let uiState: WorkoutUiState
    let viewModel: WorkoutViewModel
    let sessionAccentColor: Color

    var body: some View {
        VStack(spacing: 8) {
            Text("Movilidad")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
            ForEach(Array(exercise.mobilitySeries.enumerated()), id: \.offset) { mobIdx, mob in
                let mobDone = "\(exercise.id)_\(mob.id)" in uiState.mobilityCompletedExerciseIds
                HStack(spacing: 8) {
                    Button(action: {
                        viewModel.markMobilityComplete(exerciseId: exercise.id, mobilityId: mob.id, completed: !mobDone)
                    }) {
                        Image(systemName: mobDone ? "checkmark.circle.fill" : "circle")
                            .foregroundColor(mobDone ? WorkoutUiTokens.successColor() : .secondary)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(mob.name)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.primary)
                        HStack(spacing: 8) {
                            if let reps = mob.reps, !reps.isEmpty {
                                InfoPill(label: "Reps", value: reps, color: sessionAccentColor)
                            }
                            if let dur = mob.durationSeconds, dur > 0 {
                                let m = dur / 60
                                let s = dur % 60
                                let timeStr = m > 0 ? "\(m)m \(s)s" : "\(s)s"
                                InfoPill(label: "Tiempo", value: timeStr, color: sessionAccentColor)
                            }
                        }
                        if let notes = mob.notes, !notes.isEmpty {
                            Text(notes)
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .padding(8)
                .background(mobDone ? WorkoutUiTokens.successColor().opacity(0.08) : Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding(12)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 28))
        .overlay(RoundedRectangle(cornerRadius: 28).stroke(Color.white.opacity(0.08), lineWidth: 1))
        .padding(.horizontal, 10)
    }
}

// MARK: - Warmup Set Card

private struct WarmupSetCard: View {
    let exercise: Exercise
    let uiState: WorkoutUiState
    let viewModel: WorkoutViewModel
    let sessionAccentColor: Color

    var body: some View {
        let warmupWorkingWeight = viewModel.getGhostForSet(
            exerciseId: exercise.id,
            setIdx: 0,
            exerciseDbId: exercise.exerciseDbId ?? exercise.exerciseId,
            activeTag: uiState.exerciseTags[exercise.id]
        )?.weight ?? exercise.sets.first { $0.weight != nil && $0.weight! > 0 }?.weight

        VStack(spacing: 8) {
            Text("Aproximaciones")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
            ForEach(Array(exercise.warmupSets.enumerated()), id: \.offset) { warmIdx, ws in
                let wsDone = "\(exercise.id)_warmup_\(ws.id)" in uiState.warmupCompletedExerciseIds ||
                    exercise.id in uiState.warmupCompletedExerciseIds
                let warmupKg = (warmupWorkingWeight != nil && warmupWorkingWeight! > 0)
                    ? warmupWorkingWeight! * (ws.percentageOfWorkingWeight / 100.0) : nil

                HStack(spacing: 8) {
                    Button(action: {
                        viewModel.markWarmupComplete(exerciseId: exercise.id, warmupSetId: ws.id, completed: !wsDone)
                    }) {
                        Image(systemName: wsDone ? "checkmark.circle.fill" : "circle")
                            .foregroundColor(wsDone ? WorkoutUiTokens.successColor() : .secondary)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Aproximación #\(warmIdx + 1)")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.primary)
                        HStack(spacing: 8) {
                            InfoPill(label: "Intensidad", value: "\(Int(ws.percentageOfWorkingWeight))%", color: sessionAccentColor)
                            InfoPill(label: "Reps", value: "\(ws.targetReps)", color: sessionAccentColor)
                        }
                        if let kg = warmupKg {
                            Text("Peso sugerido: \(toTrimmedNumberString(kg)) kg")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                        if let rest = ws.restBetween, rest > 0 {
                            Text("Descanso: \(rest)s")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary.opacity(0.8))
                        }
                    }
                }
                .padding(8)
                .background(wsDone ? WorkoutUiTokens.successColor().opacity(0.08) : Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding(12)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 28))
        .overlay(RoundedRectangle(cornerRadius: 28).stroke(Color.white.opacity(0.08), lineWidth: 1))
        .padding(.horizontal, 10)
    }
}

// MARK: - ExerciseDrainOverlayCard

private struct ExerciseDrainOverlayCard: View {
    let state: ExerciseDrainOverlayStateV2

    var body: some View {
        VStack(spacing: 8) {
            Text("Drenaje de \(state.exerciseName)")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.secondary)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            ForEach(Array(state.items.enumerated()), id: \.offset) { index, item in
                ExerciseDrainAnimatedRow(item: item, index: index)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Color(.systemBackground).opacity(0.97))
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .shadow(color: .black.opacity(0.15), radius: 18, y: 10)
    }
}

// MARK: - ExerciseDrainAnimatedRow

private struct ExerciseDrainAnimatedRow: View {
    let item: ExerciseDrainOverlayItemV2
    let index: Int

    @State private var shouldDrain = false
    @State private var animatedFraction: CGFloat = 1.0

    private var accent: Color {
        switch item.channel {
        case .energy: return Color(hex: "#58C4FF")!
        case .back: return Color(hex: "#FFB85C")!
        case .muscle: return Color(hex: "#FF6F7D")!
        }
    }

    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(item.label)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.primary)
                Spacer()
                Text("-\(item.delta)%")
                    .font(.system(size: 12, weight: .black))
                    .foregroundColor(accent)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 999)
                        .fill(Color(.systemGray5))
                        .frame(height: 7)
                    RoundedRectangle(cornerRadius: 999)
                        .fill(accent)
                        .frame(width: geo.size.width * animatedFraction, height: 7)
                }
            }
            .frame(height: 7)
        }
        .onAppear {
            let baseFraction = CGFloat(item.delta) / 24.0
            withAnimation(.easeInOut(duration: 0.62).delay(Double(index) * 0.045)) {
                animatedFraction = 0
            }
            shouldDrain = true
        }
    }
}

// MARK: - WorkoutFatigueRings

private struct WorkoutFatigueRings: View {
    let cns: Int
    let muscular: Int
    let spinal: Int

    var body: some View {
        let values = [muscular, cns, spinal].map { CGFloat($0) / 100.0 }
        let colors: [Color] = [Color(hex: "#FF5252")!, Color(hex: "#448AFF")!, Color(hex: "#FFD740")!]
        let labels = ["Músc.", "Sist.", "Estr."]

        VStack(spacing: 6) {
            Text("Fatiga en tiempo real")
                .font(.system(size: 12, weight: .bold))
            HStack(spacing: 0) {
                ForEach(0..<3, id: \.self) { i in
                    VStack(spacing: 4) {
                        ZStack {
                            Circle()
                                .stroke(colors[i].opacity(0.15), lineWidth: 5)
                                .frame(width: 48, height: 48)
                            Circle()
                                .trim(from: 0, to: values[i])
                                .stroke(colors[i], style: StrokeStyle(lineWidth: 5, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                                .frame(width: 48, height: 48)
                            Text("\(Int(values[i] * 100))")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(colors[i])
                        }
                        Text(labels[i])
                            .font(.system(size: 9))
                            .foregroundColor(colors[i].opacity(0.7))
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(12)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - WorkoutRmCalcContent

private struct WorkoutRmCalcContent: View {
    @State private var rmWeightText = ""
    @State private var rmRepsText = ""

    private var rmResult: Double? {
        let w = Double(rmWeightText) ?? 0
        let r = Int(rmRepsText) ?? 0
        if w > 0, r > 0 { return calculateHybrid1RM(w, r) }
        return nil
    }

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                TextField("Peso (kg)", text: $rmWeightText)
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.plain)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                TextField("Reps", text: $rmRepsText)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.plain)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
            }
            if let result = rmResult {
                HStack {
                    Text("e1RM estimado")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                    Spacer()
                    Text("\(toTrimmedNumberString(result)) kg")
                        .font(.system(size: 16, weight: .black))
                        .foregroundColor(.blue)
                }
                .padding(12)
                .background(Color.blue.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

// MARK: - WorkoutExerciseTabs

private struct WorkoutExerciseTabs: View {
    let currentExercise: Exercise
    let currentSet: ExerciseSet
    let currentExerciseInfo: ExerciseMuscleInfo?
    let drain: PredictedDrain
    let exerciseTag: String?
    let profiles: [WorkoutContextProfile]
    let activeProfileId: String?
    let selectedTab: WorkoutExerciseContextTab?
    let onSelectedTabChange: (WorkoutExerciseContextTab?) -> Void
    let onTagSet: (String) -> Void
    let onSelectProfile: (String) -> Void
    let onSaveProfile: (WorkoutContextProfile) -> Void
    let onUpdateExercise: (@escaping (Exercise) -> Exercise) -> Void
    let onUpdateCurrentSetPlan: (String, @escaping (ExerciseSet) -> ExerciseSet) -> Void
    let onExpandHistory: () -> Void
    let onExpandTags: () -> Void
    let onExpandSetup: () -> Void
    let onExpandReplace: () -> Void
    let onExpandEdit: () -> Void
    let sessionAccentColor: Color
    let sessionEnergy: SessionEnergySummary?
    let allowExerciseManagementActions: Bool
    let userTags: [String]
    let exerciseReadiness: ExerciseReadiness?
    let userWorkoutTags: [WorkoutTag]
    let activeMainTagIds: Set<String>
    let activeSubTagIds: Set<String>
    let onMainTagToggle: (String) -> Void
    let onSubTagToggle: (String) -> Void
    let onCreateTag: (String) -> Void
    let onDeleteTag: (String) -> Void
    let onAddSubTag: (String, String, SubTagCategory) -> Void
    let onRemoveSubTag: (String, String) -> Void

    var body: some View {
        VStack(spacing: 8) {
            // Action buttons row
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ActionChip(icon: "clock.arrow.circlepath", label: "Historial", action: onExpandHistory)
                    ActionChip(icon: "tag", label: "Tags", action: onExpandTags)
                    ActionChip(icon: "gearshape", label: "Setup", action: onExpandSetup)
                    ActionChip(icon: "arrow.triangle.swap", label: "Reemplazar", action: onExpandReplace)
                    if allowExerciseManagementActions {
                        ActionChip(icon: "pencil", label: "Editar", action: onExpandEdit)
                    }
                }
                .padding(.horizontal, 10)
            }

            // Catalog info pills
            if let info = currentExerciseInfo {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        if let type = info.type {
                            InfoPill(label: "Tipo", value: type, color: sessionAccentColor)
                        }
                        if let equip = info.equipment {
                            InfoPill(label: "Equip.", value: equip, color: sessionAccentColor)
                        }
                        if let muscle = info.involvedMuscles.first?.muscle {
                            InfoPill(label: "Músculo", value: muscle, color: sessionAccentColor)
                        }
                    }
                    .padding(.horizontal, 10)
                }
            }

            // Drain / energy summary
            if drain.cns > 0 || drain.muscular > 0 || drain.spinal > 0 {
                WorkoutFatigueRings(cns: drain.cns, muscular: drain.muscular, spinal: drain.spinal)
                    .padding(.horizontal, 10)
            }

            if let energy = sessionEnergy {
                HStack(spacing: 6) {
                    ForEach(energy.entries.prefix(5), id: \.muscleId) { entry in
                        VStack(spacing: 2) {
                            Text(entry.muscleId)
                                .font(.system(size: 8))
                                .foregroundColor(.secondary)
                            Text("\(entry.percentage)%")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(sessionAccentColor)
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                        .background(sessionAccentColor.opacity(0.12))
                        .clipShape(Capsule())
                    }
                }
                .padding(.horizontal, 10)
            }

            // Suggested tag chip
            if let tag = exerciseTag, !tag.isEmpty {
                HStack {
                    Image(systemName: "tag.fill")
                        .font(.system(size: 10))
                    Text(tag)
                        .font(.system(size: 11, weight: .bold))
                }
                .foregroundColor(sessionAccentColor)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(sessionAccentColor.opacity(0.15))
                .clipShape(Capsule())
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 10)
            }

            // Readiness
            if let readiness = exerciseReadiness {
                HStack(spacing: 4) {
                    Text("Readiness: \(readiness.overallScore)%")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.secondary)
                    ForEach(readiness.muscleReadiness.prefix(3), id: \.muscleId) { mr in
                        Text("\(mr.muscleId) \(mr.percentage)%")
                            .font(.system(size: 8))
                            .foregroundColor(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 10)
            }

            // Tags section
            if userWorkoutTags.isNotEmpty {
                FlowLayout(spacing: 6) {
                    ForEach(userWorkoutTags) { tag in
                        let isActive = activeMainTagIds.contains(tag.id)
                        Button(action: { onMainTagToggle(tag.id) }) {
                            HStack(spacing: 4) {
                                Text(tag.name)
                                    .font(.system(size: 10, weight: isActive ? .black : .regular))
                                if !tag.subTags.isEmpty {
                                    Text("(\(tag.subTags.count))")
                                        .font(.system(size: 8))
                                }
                            }
                            .foregroundColor(isActive ? .white : .white.opacity(0.7))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(isActive ? sessionAccentColor : Color(.systemGray4))
                            .clipShape(Capsule())
                        }
                    }
                }
                .padding(.horizontal, 10)
            }
        }
        .padding(.vertical, 8)
    }
}

private struct ActionChip: View {
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(label, systemImage: icon)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(.primary)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color(.systemGray6))
                .clipShape(Capsule())
        }
    }
}

// MARK: - WorkoutExerciseSetupContent

private struct WorkoutExerciseSetupContent: View {
    let exercise: Exercise
    let currentSet: ExerciseSet
    let onUpdateExercise: (@escaping (Exercise) -> Exercise) -> Void
    let onUpdateSet: (String, @escaping (ExerciseSet) -> ExerciseSet) -> Void
    var maxVisibleCues: Int = Int.max

    @State private var machineBrandText: String = ""
    @State private var seatText: String = ""
    @State private var pinText: String = ""
    @State private var notesText: String = ""

    var body: some View {
        let cues = Array(Set(exercise.setupCues + exercise.executionCues))

        VStack(spacing: 10) {
            TextField("Máquina / marca", text: $machineBrandText)
                .textFieldStyle(.plain)
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(8)
                .onChange(of: machineBrandText) { newVal in
                    onUpdateSet(currentSet.id) { $0.copy(machineBrand: newVal.isEmpty ? nil : newVal) }
                }

            HStack(spacing: 8) {
                TextField("Asiento", text: $seatText)
                    .textFieldStyle(.plain)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                    .onChange(of: seatText) { newVal in
                        onUpdateExercise { current in
                            current.copy(setupDetails: (current.setupDetails ?? ExerciseSetupDetails()).copy(seatPosition: newVal.isEmpty ? nil : newVal))
                        }
                    }
                TextField("Pin", text: $pinText)
                    .textFieldStyle(.plain)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                    .onChange(of: pinText) { newVal in
                        onUpdateExercise { current in
                            current.copy(setupDetails: (current.setupDetails ?? ExerciseSetupDetails()).copy(pinPosition: newVal.isEmpty ? nil : newVal))
                        }
                    }
            }

            TextField("Notas de set-up", text: $notesText, axis: .vertical)
                .textFieldStyle(.plain)
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(8)
                .lineLimit(2...4)
                .onChange(of: notesText) { newVal in
                    onUpdateExercise { current in
                        current.copy(setupDetails: (current.setupDetails ?? ExerciseSetupDetails()).copy(equipmentNotes: newVal.isEmpty ? nil : newVal))
                    }
                }

            if cues.isNotEmpty {
                Text("Cues")
                    .font(.system(size: 12, weight: .bold))
                VStack(spacing: 4) {
                    ForEach(cues.prefix(maxVisibleCues), id: \.self) { cue in
                        Text("• \(cue)")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
        .onAppear {
            machineBrandText = currentSet.machineBrand ?? ""
            seatText = exercise.setupDetails?.seatPosition ?? ""
            pinText = exercise.setupDetails?.pinPosition ?? ""
            notesText = exercise.setupDetails?.equipmentNotes ?? ""
        }
    }
}

// MARK: - WorkoutDrawer

private struct WorkoutDrawer<Content: View>: View {
    let title: String
    let onDismiss: () -> Void
    var dismissible: Bool = true
    var showCloseButton: Bool = true
    @ViewBuilder let content: Content

    @State private var showContent = false

    var body: some View {
        ZStack {
            if showContent {
                Color.black.opacity(0.5)
                    .ignoresSafeArea()
                    .onTapGesture { if dismissible { handleDismiss() } }
                    .transition(.opacity)

                VStack(spacing: 16) {
                    HStack {
                        Text(title)
                            .font(.system(size: 18, weight: .black))
                            .foregroundColor(.white)
                        Spacer()
                        if showCloseButton {
                            Button(action: handleDismiss) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(8)
                                    .background(Color.white.opacity(0.08))
                                    .clipShape(Circle())
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)

                    content
                        .padding(.horizontal, 20)

                    Spacer().frame(height: 24)
                }
                .frame(maxWidth: .infinity)
                .background(Color(hex: "#1E1E1E")!.opacity(0.4))
                .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 0.3)) { showContent = true }
        }
    }

    private func handleDismiss() {
        withAnimation(.easeInOut(duration: 0.25)) {
            showContent = false
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
            onDismiss()
        }
    }
}

// MARK: - WarmupCompactContent

private struct WarmupCompactContent: View {
    let exercise: Exercise
    let onDismiss: () -> Void
    let onComplete: () -> Void
    let workingWeightKg: Double?

    var body: some View {
        let safeWarmupSets = exercise.warmupSets.map { set in
            let pct = sanitizeWarmupPercentage(set.percentageOfWorkingWeight)
            return SanitizedWarmupSet(percentage: pct, reps: sanitizeWarmupReps(set.targetReps, pct))
        }

        VStack(spacing: 8) {
            Text(exercise.name)
                .font(.system(size: 14, weight: .bold))
            if let ww = workingWeightKg, ww > 0 {
                Text("Peso de trabajo base: \(toTrimmedNumberString(ww)) kg")
                    .font(.system(size: 11))
                    .foregroundColor(.blue)
            }
            ForEach(Array(safeWarmupSets.enumerated()), id: \.offset) { idx, set in
                let warmupKg = (workingWeightKg != nil && workingWeightKg! > 0) ? workingWeightKg! * (Double(set.percentage) / 100.0) : nil
                HStack {
                    Text("Serie \(idx + 1)")
                        .font(.system(size: 12, weight: .bold))
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("\(set.percentage)% · \(set.reps) reps")
                            .font(.system(size: 11))
                        if let kg = warmupKg {
                            Text("\(toTrimmedNumberString(kg)) kg")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.blue)
                        }
                    }
                }
                .padding(10)
                .background(Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            HStack(spacing: 6) {
                Button("Omitir", action: onDismiss)
                    .buttonStyle(.bordered)
                Button("Comenzar", action: onComplete)
                    .buttonStyle(.borderedProminent)
            }
        }
    }
}

// MARK: - WorkoutWarmupSheet

private struct WorkoutWarmupSheet: View {
    let exercise: Exercise
    let warmupSets: [WorkoutWarmupDisplaySet]
    let workingWeight: Double?
    let isCompleted: Bool
    let onDismiss: () -> Void
    let onMarkCompleted: () -> Void

    var body: some View {
        let displaySets = warmupSets.isEmpty ? exercise.warmupSets.map { set in
            let pct = sanitizeWarmupPercentage(set.percentageOfWorkingWeight)
            return WorkoutWarmupDisplaySet(
                percentage: Double(pct),
                reps: sanitizeWarmupReps(set.targetReps, pct),
                targetWeight: (workingWeight ?? 0) > 0 ? (workingWeight! * Double(pct) / 100.0) : nil
            )
        } : warmupSets

        VStack(spacing: 10) {
            Text("Warm-up inteligente")
                .font(.system(size: 16, weight: .bold))
            Text(exercise.name)
                .font(.system(size: 13))
                .foregroundColor(.secondary)
            if let ww = workingWeight, ww > 0 {
                Text("\(toTrimmedNumberString(ww)) kg estimados para la primera serie efectiva")
                    .font(.system(size: 11))
                    .foregroundColor(.blue)
            }
            ForEach(Array(displaySets.enumerated()), id: \.offset) { index, set in
                VStack(spacing: 4) {
                    HStack {
                        Text("Aproximacion \(index + 1)")
                            .font(.system(size: 12, weight: .bold))
                        Spacer()
                        Text([set.percentage > 0 ? "\(toTrimmedNumberString(set.percentage))%" : nil,
                              "\(set.reps) reps",
                              set.targetWeight.map { "\(toTrimmedNumberString($0)) kg" }
                        ].compactMap { $0 }.joined(separator: " · "))
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                    if let rest = exercise.warmupSets[safe: index]?.restBetween, rest > 0 {
                        Text("Descanso: \(rest)s")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.blue)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(12)
                .background(Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            HStack(spacing: 8) {
                Button(isCompleted ? "Cerrar" : "Omitir", action: onDismiss)
                    .buttonStyle(.bordered)
                Button(isCompleted ? "Warm-up listo" : "Marcar warm-up listo", action: onMarkCompleted)
                    .buttonStyle(.borderedProminent)
            }
        }
    }
}

// MARK: - WorkoutExerciseQuickActionsSheet

private struct WorkoutExerciseQuickActionsSheet: View {
    let exercise: Exercise
    let canMoveUp: Bool
    let canMoveDown: Bool
    let hasWarmup: Bool
    let onDismiss: () -> Void
    let onGoToExercise: () -> Void
    let onOpenWarmup: () -> Void
    let onOpenHistory: () -> Void
    let onOpenTags: () -> Void
    let onOpenSetup: () -> Void
    let onOpenReplace: () -> Void
    let onMoveUp: () -> Void
    let onMoveDown: () -> Void
    let onSkip: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Text("Quick actions")
                .font(.system(size: 16, weight: .bold))
            Text(exercise.name)
                .font(.system(size: 13))
                .foregroundColor(.secondary)

            Button("Ir al ejercicio", action: onGoToExercise)
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
            Button("Ver historial", action: onOpenHistory)
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity)
            if hasWarmup {
                Button("Warm-up", action: onOpenWarmup)
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
            }
            Button("Reemplazar", action: onOpenReplace)
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity)
            Button("Tags", action: onOpenTags)
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity)
            Button("Setup", action: onOpenSetup)
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity)
            HStack(spacing: 8) {
                Button("Subir", action: onMoveUp)
                    .buttonStyle(.bordered)
                    .disabled(!canMoveUp)
                Button("Bajar", action: onMoveDown)
                    .buttonStyle(.bordered)
                    .disabled(!canMoveDown)
            }
            Button("Omitir ejercicio", action: onSkip)
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
            Button("Cerrar", action: onDismiss)
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - PostExerciseFeedbackSheet

private struct PostExerciseFeedbackSheet: View {
    let exercise: Exercise
    let historicalFeedback: PostExerciseFeedback?
    let showPerceivedIntensity: Bool
    let onSave: (PostExerciseQuickResult) -> Void
    let onDismiss: () -> Void

    @State private var technical: Int = 8
    @State private var perceivedIntensity: Float = 8
    @State private var perceivedFailure: Bool = false
    @State private var searchQuery: String = ""
    @State private var selectedIds: [String] = []
    @State private var infoEntry: DiscomfortCatalogEntry? = nil

    var body: some View {
        let filteredEntries = searchQuery.trimmingCharacters(in: .whitespaces).lowercased().isEmpty ? [] :
            DISCOMFORT_CATALOG.filter { $0.label.lowercased().contains(searchQuery.lowercased()) || $0.description.lowercased().contains(searchQuery.lowercased()) }
                .sorted { $0.label < $1.label }

        VStack(spacing: 16) {
            Text("Feedback post-ejercicio")
                .font(.system(size: 16, weight: .black))
                .foregroundColor(.white)
            Text(exercise.name)
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.6))

            ScrollView(.vertical) {
                VStack(spacing: 16) {
                    Text("Calidad técnica")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    HStack(spacing: 8) {
                        Slider(value: Binding(get: { Float(technical) }, set: { technical = Int($0).clamped(to: 1...10) }), in: 1...10, step: 1)
                        Text("\(technical) / 10")
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(.blue)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.blue.opacity(0.2))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }

                    if showPerceivedIntensity {
                        Text("Qué tan intenso fue")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        HStack(spacing: 8) {
                            Slider(value: $perceivedIntensity, in: 1...10, step: 1)
                                .onChange(of: perceivedIntensity) { newVal in
                                    if newVal < 10 { perceivedFailure = false }
                                }
                            Button(action: { perceivedFailure.toggle(); if perceivedFailure { perceivedIntensity = 10 } }) {
                                Text("Fallo")
                                    .font(.system(size: 11))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(perceivedFailure ? Color.red.opacity(0.3) : Color.clear)
                                    .clipShape(Capsule())
                            }
                        }
                        Text("\(Int(perceivedIntensity)) / 10")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.65))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    if let hist = historicalFeedback, hist.discomfortIds.filter({ $0 != "none" }).isNotEmpty {
                        let labels = hist.discomfortIds.compactMap { id in DISCOMFORT_CATALOG.first { $0.id == id }?.label }
                        if labels.isNotEmpty {
                            Text("Molestias frecuentes: \(labels.joined(separator: ", "))")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                                .padding(10)
                                .background(Color(.systemGray5))
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }

                    Text("Molestias")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    TextField("Buscar molestia", text: $searchQuery)
                        .textFieldStyle(.plain)
                        .padding()
                        .background(Color(.systemGray6))
                        .cornerRadius(8)
                        .overlay(Image(systemName: "magnifyingglass").foregroundColor(.secondary), alignment: .leading)

                    if filteredEntries.isNotEmpty {
                        ForEach(filteredEntries, id: \.id) { entry in
                            let selected = selectedIds.contains(entry.id)
                            Button(action: {
                                if selected { selectedIds.removeAll { $0 == entry.id } }
                                else { selectedIds.append(entry.id) }
                            }) {
                                HStack {
                                    Text(entry.label)
                                        .font(.system(size: 11))
                                        .foregroundColor(selected ? .blue : .white.opacity(0.7))
                                    Spacer()
                                    if selected { Image(systemName: "checkmark").foregroundColor(.blue) }
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 6)
                                .background(selected ? Color.blue.opacity(0.15) : Color.clear)
                                .clipShape(Capsule())
                            }
                        }
                    } else if searchQuery.isEmpty {
                        Text("Escribe para buscar molestias...")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.4))
                    } else {
                        Text("No se encontraron resultados para \"\(searchQuery)\"")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.4))
                    }

                    if selectedIds.isNotEmpty {
                        FlowLayout(spacing: 6) {
                            ForEach(selectedIds, id: \.self) { id in
                                let label = DISCOMFORT_CATALOG.first { $0.id == id }?.label ?? id
                                HStack(spacing: 4) {
                                    Text(label)
                                        .font(.system(size: 11))
                                        .foregroundColor(.blue)
                                    Image(systemName: "xmark")
                                        .font(.system(size: 10))
                                        .foregroundColor(.blue)
                                        .onTapGesture { selectedIds.removeAll { $0 == id } }
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.blue.opacity(0.2))
                                .clipShape(Capsule())
                            }
                        }
                    }
                }
            }

            Button(action: {
                onSave(PostExerciseQuickResult(
                    technicalQuality: technical,
                    discomfortIds: selectedIds,
                    perceivedIntensityRpe: showPerceivedIntensity ? Double(perceivedIntensity) : nil,
                    perceivedFailure: showPerceivedIntensity && perceivedFailure
                ))
            }) {
                Text("Guardar y continuar")
                    .font(.system(size: 14, weight: .bold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .clipShape(Capsule())
            }
        }
        .onAppear {
            technical = (historicalFeedback?.technicalQuality).map { max(1, min(10, $0)) } ?? 8
            perceivedIntensity = Float((historicalFeedback?.perceivedIntensityRpe).map { max(1, min(10, $0)) } ?? 8)
            perceivedFailure = showPerceivedIntensity && historicalFeedback?.perceivedFailure == true
        }
    }
}

// MARK: - PostExerciseCompactContent

private struct PostExerciseCompactContent: View {
    let exerciseName: String
    var showPerceivedIntensity: Bool = true
    let onSave: (PostExerciseQuickResult) -> Void

    @State private var technical: Int = 8
    @State private var perceivedIntensity: Float = 8
    @State private var perceivedFailure: Bool = false
    @State private var searchQuery: String = ""
    @State private var selectedIds: [String] = []
    @State private var infoEntry: DiscomfortCatalogEntry? = nil

    var body: some View {
        let filteredEntries = searchQuery.trimmingCharacters(in: .whitespaces).lowercased().isEmpty ? [] :
            DISCOMFORT_CATALOG.filter { $0.label.lowercased().contains(searchQuery.lowercased()) || $0.description.lowercased().contains(searchQuery.lowercased()) }
                .sorted { $0.label < $1.label }

        VStack(spacing: 8) {
            Text(exerciseName)
                .font(.system(size: 12))
                .foregroundColor(.secondary)

            Text("Calidad técnica")
                .font(.system(size: 12, weight: .bold))
                .frame(maxWidth: .infinity, alignment: .leading)
            Slider(value: Binding(get: { Float(technical) }, set: { technical = Int($0).clamped(to: 1...10) }), in: 1...10, step: 1)
            Text("\(technical) / 10")
                .font(.system(size: 11))
                .foregroundColor(.blue)
                .frame(maxWidth: .infinity, alignment: .leading)

            if showPerceivedIntensity {
                Text("Qué tan intenso fue")
                    .font(.system(size: 12, weight: .bold))
                    .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 8) {
                    Slider(value: $perceivedIntensity, in: 1...10, step: 1)
                        .onChange(of: perceivedIntensity) { newVal in
                            if newVal < 10 { perceivedFailure = false }
                        }
                    Button(action: { perceivedFailure.toggle(); if perceivedFailure { perceivedIntensity = 10 } }) {
                        Text("Fallo")
                            .font(.system(size: 11))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(perceivedFailure ? Color.red.opacity(0.3) : Color.clear)
                            .clipShape(Capsule())
                    }
                }
                Text("\(Int(perceivedIntensity)) / 10")
                    .font(.system(size: 11))
                    .foregroundColor(.blue)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Text("Molestias (opcional)")
                .font(.system(size: 12, weight: .bold))
                .frame(maxWidth: .infinity, alignment: .leading)

            TextField("Buscar molestia", text: $searchQuery)
                .textFieldStyle(.plain)
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(8)
                .overlay(Image(systemName: "magnifyingglass").foregroundColor(.secondary), alignment: .leading)

            if filteredEntries.isNotEmpty {
                ForEach(filteredEntries, id: \.id) { entry in
                    let selected = selectedIds.contains(entry.id)
                    Button(action: {
                        if selected { selectedIds.removeAll { $0 == entry.id } }
                        else { selectedIds.append(entry.id) }
                    }) {
                        HStack {
                            Text(entry.label)
                                .font(.system(size: 11))
                            Spacer()
                            if selected { Image(systemName: "checkmark") }
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                        .background(selected ? Color.blue.opacity(0.15) : Color.clear)
                        .clipShape(Capsule())
                    }
                }
            } else if searchQuery.trimmingCharacters(in: .whitespaces).isNotEmpty {
                Text("No se encontraron resultados")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }

            if selectedIds.isNotEmpty {
                FlowLayout(spacing: 6) {
                    ForEach(selectedIds, id: \.self) { id in
                        let label = DISCOMFORT_CATALOG.first { $0.id == id }?.label ?? id
                        HStack(spacing: 4) {
                            Text(label)
                                .font(.system(size: 11))
                            Image(systemName: "xmark")
                                .font(.system(size: 10))
                                .onTapGesture { selectedIds.removeAll { $0 == id } }
                        }
                        .foregroundColor(.blue)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.blue.opacity(0.2))
                        .clipShape(Capsule())
                    }
                }
            }

            Button(action: {
                onSave(PostExerciseQuickResult(
                    technicalQuality: technical,
                    discomfortIds: selectedIds,
                    perceivedIntensityRpe: showPerceivedIntensity ? Double(perceivedIntensity) : nil,
                    perceivedFailure: showPerceivedIntensity && perceivedFailure
                ))
            }) {
                Text("Guardar")
                    .font(.system(size: 11))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .clipShape(Capsule())
            }
        }
    }
}

// MARK: - PostExerciseQuickResult

private struct PostExerciseQuickResult {
    let technicalQuality: Int
    let discomfortIds: [String]
    let perceivedIntensityRpe: Double?
    let perceivedFailure: Bool
}

// MARK: - SuperSetSetPager

private struct SupersetSetPager: View {
    let members: [Exercise]
    let currentExerciseId: String
    let currentRoundIndex: Int
    let completedSets: [String: CompletedSet]
    let sessionAccentColor: Color
    let onSelectRound: (Int) -> Void

    var body: some View {
        let roundCount = max(members.map { $0.sets.count }.max() ?? 1, 1)
        HStack(spacing: 0) {
            Spacer()
            ForEach(0..<roundCount, id: \.self) { roundIdx in
                let isActiveRound = roundIdx == currentRoundIndex
                let roundKeys = members.flatMap { $0.completionKeysForSet(roundIdx) }
                let roundDone = roundKeys.isNotEmpty && roundKeys.allSatisfy { completedSets.keys.contains($0) }
                Button(action: { onSelectRound(roundIdx) }) {
                    VStack(spacing: 4) {
                        Text("R\(roundIdx + 1)")
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(isActiveRound ? sessionAccentColor : roundDone ? Color(hex: "#66BB6A")! : .secondary)
                        HStack(spacing: 4) {
                            ForEach(members, id: \.id) { member in
                                let keys = member.completionKeysForSet(roundIdx)
                                if keys.isNotEmpty {
                                    let memberDone = keys.allSatisfy { completedSets.keys.contains($0) }
                                    let memberActive = isActiveRound && member.id == currentExerciseId
                                    Circle()
                                        .fill(memberActive ? sessionAccentColor : memberDone ? Color(hex: "#66BB6A")! : Color.clear)
                                        .frame(width: memberActive ? 10 : 8, height: memberActive ? 10 : 8)
                                        .overlay(Circle().stroke(memberActive || memberDone ? Color.clear : Color.secondary.opacity(0.45), lineWidth: 1))
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(isActiveRound ? sessionAccentColor.opacity(0.18) : roundDone ? Color(hex: "#66BB6A")!.opacity(0.13) : Color.clear)
                    .clipShape(RoundedRectangle(cornerRadius: 13))
                    .overlay(RoundedRectangle(cornerRadius: 13).stroke(
                        isActiveRound ? sessionAccentColor : roundDone ? Color(hex: "#66BB6A")!.opacity(0.62) : Color.secondary.opacity(0.44),
                        lineWidth: isActiveRound ? 1.5 : 1
                    ))
                }
                .padding(.horizontal, 4)
            }
            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
    }
}

// MARK: - UnifiedExerciseCarousel

private struct UnifiedExerciseCarousel: View {
    let exercises: [Exercise]
    let parts: [SessionPart]
    let supersetGroups: [SupersetGroup]
    let currentIdx: Int
    let currentSetIdx: Int
    let completedSets: [String: CompletedSet]
    let onSelect: (Int) -> Void
    var onSelectGroup: (String) -> Void = { _ in }
    var onOpenContext: (String) -> Void = { _ in }
    var enableLongPress: Bool = true

    var body: some View {
        let roadmapGroups: [ExerciseRoadmapGroup] = {
            var emitted = Set<String>()
            return exercises.compactMap { ex in
                let gid = ex.supersetGroupRefOrLegacyId()
                if let id = gid {
                    guard emitted.insert(id).inserted else { return nil }
                    return ExerciseRoadmapGroup(groupId: id, exercises: exercises.filter { $0.supersetGroupRefOrLegacyId() == id })
                }
                return ExerciseRoadmapGroup(groupId: nil, exercises: [ex])
            }
        }()

        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 6) {
                ForEach(Array(roadmapGroups.enumerated()), id: \.offset) { groupIdx, group in
                    let exercise = group.exercises.first!
                    let idx = exercises.firstIndex { $0.id == exercise.id } ?? 0
                    let part = parts.first { $0.exercises.contains { $0.id == exercise.id } }
                    let accent = part.flatMap { $0.color.flatMap { Color(hex: $0) } } ?? Color.blue
                    let completedCount = group.exercises.reduce(0) { sum, member in
                        sum + member.sets.indices.reduce(0) { s, setIdx in
                            s + member.completionKeysForSet(setIdx).filter { completedSets.keys.contains($0) }.count
                        }
                    }
                    let totalSets = group.exercises.reduce(0) { sum, member in
                        sum + member.sets.indices.reduce(0) { s, setIdx in
                            s + member.completionKeysForSet(setIdx).count
                        }
                    }
                    let isAllDone = completedCount >= totalSets && totalSets > 0
                    let isCurrent = group.exercises.contains { $0.id == exercises[safe: currentIdx]?.id }

                    if group.groupId == nil || group.exercises.count == 1 {
                        ExerciseRoadmapCard(
                            exercise: exercise,
                            completedCount: completedCount,
                            isCurrent: isCurrent,
                            isAllDone: isAllDone,
                            accent: accent,
                            groupName: part?.name,
                            onClick: { onSelect(idx) },
                            onLongClick: enableLongPress ? { onOpenContext(exercise.id) } : nil
                        )
                    } else {
                        let supersetOrdinal = roadmapGroups.compactMap { $0.groupId }.distinct().firstIndex(of: group.groupId!).map { $0 + 1 } ?? 1
                        SupersetRoadmapCard(
                            exercises: group.exercises,
                            supersetNumber: supersetOrdinal,
                            supersetCount: roadmapGroups.compactMap { $0.groupId }.distinct().count,
                            roundCount: group.exercises.map { $0.sets.count }.max() ?? 0,
                            completedSets: completedSets,
                            isCurrent: isCurrent,
                            isAllDone: isAllDone,
                            accent: accent,
                            groupName: part?.name,
                            currentExerciseId: exercises[safe: currentIdx]?.id,
                            currentRound: isCurrent ? currentSetIdx + 1 : nil,
                            onClick: { onSelectGroup(group.groupId!) },
                            onLongClick: enableLongPress ? { onOpenContext(exercise.id) } : nil
                        )
                    }
                }
            }
            .padding(.vertical, 5)
        }
    }
}

private struct ExerciseRoadmapGroup {
    let groupId: String?
    let exercises: [Exercise]
}

// MARK: - ExerciseRoadmapCard

private struct ExerciseRoadmapCard: View {
    let exercise: Exercise
    let completedCount: Int
    let isCurrent: Bool
    let isAllDone: Bool
    let accent: Color
    let groupName: String?
    let onClick: () -> Void
    var onLongClick: (() -> Void)? = nil

    var body: some View {
        let nameLen = exercise.name.count
        let minWidth: CGFloat = nameLen > 30 ? 130 : nameLen > 22 ? 110 : 88
        let containerColor: Color = isCurrent ? accent.opacity(0.88) : isAllDone ? Color(hex: "#1A3A1A")! : accent.opacity(0.18)
        let contentColor: Color = isCurrent ? .white : .white.opacity(0.90)

        Button(action: onClick) {
            HStack(spacing: 5) {
                Text(isAllDone ? "✓" : "\(completedCount)/\(exercise.sets.count)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(contentColor)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(isCurrent ? Color.white.opacity(0.16) : accent.opacity(0.20))
                    .clipShape(Capsule())
                VStack(alignment: .leading, spacing: 0) {
                    Text(exercise.name)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(contentColor)
                        .lineLimit(2)
                    if let gn = groupName, !gn.isEmpty {
                        Text(gn)
                            .font(.system(size: 9, weight: .medium))
                            .foregroundColor(contentColor.opacity(0.7))
                            .lineLimit(1)
                    }
                }
            }
            .padding(.horizontal, 7)
            .padding(.vertical, 5)
            .frame(minWidth: minWidth, maxWidth: 170, minHeight: groupName != nil ? 60 : 46, alignment: .leading)
            .background(containerColor)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.08), lineWidth: 0.5))
        }
    }
}

// MARK: - SupersetRoadmapCard

private struct SupersetRoadmapCard: View {
    let exercises: [Exercise]
    let supersetNumber: Int
    let supersetCount: Int
    let roundCount: Int
    let completedSets: [String: CompletedSet]
    let isCurrent: Bool
    let isAllDone: Bool
    let accent: Color
    let groupName: String?
    let currentExerciseId: String?
    let currentRound: Int?
    let onClick: () -> Void
    var onLongClick: (() -> Void)? = nil

    var body: some View {
        let safeRoundCount = max(roundCount, 1)
        let title = supersetCount > 1 ? "Superserie \(supersetNumber)" : "Superserie"

        Button(action: onClick) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.system(size: 12, weight: .black))
                        .foregroundColor(.white)
                        .lineLimit(2)
                    Text(isAllDone ? "Completada" : (currentRound.map { "Ronda \($0)/\(safeRoundCount)" } ?? "\(safeRoundCount) rondas"))
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(isCurrent ? accent : .white.opacity(0.62))
                        .lineLimit(1)
                }
                .frame(minWidth: 82, maxWidth: 104, alignment: .leading)

                HStack(spacing: 7) {
                    ForEach(0..<safeRoundCount, id: \.self) { roundIdx in
                        let roundKeys = exercises.flatMap { $0.completionKeysForSet(roundIdx) }
                        let roundDone = roundKeys.isNotEmpty && roundKeys.allSatisfy { completedSets.keys.contains($0) }
                        let isRoundCurrent = isCurrent && currentRound == roundIdx + 1
                        ZStack {
                            Circle()
                                .fill(isRoundCurrent ? accent : roundDone ? Color(hex: "#66BB6A")! : Color.clear)
                                .frame(width: isRoundCurrent ? 24 : 18, height: isRoundCurrent ? 24 : 18)
                                .overlay(Circle().stroke(
                                    roundDone ? Color(hex: "#66BB6A")! : Color.white.opacity(0.42),
                                    lineWidth: isRoundCurrent ? 0 : 1.4
                                ))
                            Text("\(roundIdx + 1)")
                                .font(.system(size: isRoundCurrent ? 10 : 9, weight: .black))
                                .foregroundColor(isRoundCurrent || roundDone ? .black : .white.opacity(0.70))
                        }
                    }
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 8)
                .background(Color.white.opacity(isCurrent ? 0.13 : 0.07))
                .clipShape(RoundedRectangle(cornerRadius: 15))
                .overlay(RoundedRectangle(cornerRadius: 15).stroke(Color.white.opacity(0.10), lineWidth: 1))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .frame(minWidth: 214, maxWidth: 280, minHeight: 68, alignment: .leading)
            .background(Color(hex: "#101010")!)
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(
                isCurrent ? accent : isAllDone ? Color(hex: "#66BB6A")!.opacity(0.62) : Color.white.opacity(0.12),
                lineWidth: isCurrent ? 1.5 : 1
            ))
        }
    }
}

// MARK: - SupersetWarmupRevealCard

private struct SupersetWarmupRevealCard: View {
    let exercise: Exercise
    let onClick: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "flame.fill")
                .font(.system(size: 16))
                .foregroundColor(Color(hex: "#FFD740"))
                .padding(6)
                .background(Color(hex: "#FFD740")!.opacity(0.16))
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text("Aproximaciones disponibles")
                    .font(.system(size: 12, weight: .black))
                    .foregroundColor(Color(hex: "#FFD740"))
                Text(exercise.name)
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.62))
                    .lineLimit(1)
            }
            Spacer()
            Button("Saltar", action: onDismiss)
                .font(.system(size: 11))
                .foregroundColor(.white.opacity(0.62))
            Button("Desplegar", action: onClick)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.black)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color(hex: "#FFD740"))
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(hex: "#2A2200"))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color(hex: "#FFD740")!.opacity(0.34), lineWidth: 1))
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
    }
}

// MARK: - WarmupInlineCard

private struct WarmupInlineCard: View {
    let exercise: Exercise
    let workingWeightKg: Double?
    let onToggleComplete: (Bool) -> Void
    let onDismiss: () -> Void

    @State private var checkedSets: [Bool] = []

    var body: some View {
        let safeWarmupSets = exercise.warmupSets.map { set in
            let pct = sanitizeWarmupPercentage(set.percentageOfWorkingWeight)
            return SanitizedWarmupSet(percentage: pct, reps: sanitizeWarmupReps(set.targetReps, pct))
        }

        VStack(spacing: 8) {
            HStack {
                Image(systemName: "flame.fill")
                    .foregroundColor(Color(hex: "#FFD740"))
                Text("Series de aproximación")
                    .font(.system(size: 14, weight: .black))
                    .foregroundColor(Color(hex: "#FFD740"))
                Spacer()
                if let ww = workingWeightKg, ww > 0 {
                    Text("\(toTrimmedNumberString(ww)) kg trabajo")
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: "#FFD740"))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color(hex: "#FFD740")!.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }

            ForEach(Array(safeWarmupSets.enumerated()), id: \.offset) { idx, set in
                let warmupKg = (workingWeightKg != nil && workingWeightKg! > 0) ? workingWeightKg! * (Double(set.percentage) / 100.0) : nil
                let checked = checkedSets[safe: idx] ?? false
                HStack {
                    Button(action: {
                        if idx < checkedSets.count { checkedSets[idx].toggle() }
                    }) {
                        Image(systemName: checked ? "checkmark.circle.fill" : "circle")
                            .foregroundColor(checked ? Color(hex: "#FFD740") : .white.opacity(0.3))
                    }
                    Text("Aprox. \(idx + 1)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                    Spacer()
                    Text("\(set.percentage)%")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "#FFD740"))
                    Text("\(set.reps) reps")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.7))
                    if let kg = warmupKg {
                        Text("\(toTrimmedNumberString(kg)) kg")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(hex: "#FFD740"))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color(hex: "#FFD740")!.opacity(0.15))
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .background(checked ? Color(hex: "#FFD740")!.opacity(0.12) : Color.white.opacity(0.04))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(
                    checked ? Color(hex: "#FFD740")!.opacity(0.3) : Color.white.opacity(0.08),
                    lineWidth: 1
                ))
            }

            HStack(spacing: 8) {
                Button("Saltar calentamiento", action: onDismiss)
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.6))
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity)
                    .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
                Button("Listo") {
                    onToggleComplete(true)
                    onDismiss()
                }
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.black)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
                .background(Color(hex: "#FFD740"))
                .clipShape(Capsule())
            }
        }
        .padding(12)
        .background(Color(hex: "#2A2200"))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color(hex: "#FFD740")!.opacity(0.4), lineWidth: 1))
        .onAppear {
            checkedSets = Array(repeating: false, count: safeWarmupSets.count)
        }
    }
}

// MARK: - QuickExecutionErrorDiscomfortSheet

private struct QuickExecutionErrorDiscomfortSheet: View {
    let exerciseName: String
    let onSave: ([String]) -> Void
    let onDismiss: () -> Void

    @State private var searchQuery: String = ""
    @State private var selectedIds: [String] = []
    @State private var infoEntry: DiscomfortCatalogEntry? = nil

    var body: some View {
        let filteredEntries = searchQuery.trimmingCharacters(in: .whitespaces).lowercased().isEmpty ? [] :
            DISCOMFORT_CATALOG.filter { $0.label.lowercased().contains(searchQuery.lowercased()) || $0.description.lowercased().contains(searchQuery.lowercased()) }
                .sorted { $0.label < $1.label }

        VStack(spacing: 16) {
            Text("Reportar molestias")
                .font(.system(size: 16, weight: .black))
                .foregroundColor(.white)
            Text(exerciseName)
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.6))

            ScrollView(.vertical) {
                VStack(spacing: 12) {
                    Text("¿Tuviste alguna molestia al realizar este ejercicio?")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    TextField("Buscar molestia", text: $searchQuery)
                        .textFieldStyle(.plain)
                        .padding()
                        .background(Color(.systemGray5))
                        .cornerRadius(8)
                        .overlay(Image(systemName: "magnifyingglass").foregroundColor(.secondary), alignment: .leading)

                    if filteredEntries.isNotEmpty {
                        ForEach(filteredEntries, id: \.id) { entry in
                            let selected = selectedIds.contains(entry.id)
                            Button(action: {
                                if selected { selectedIds.removeAll { $0 == entry.id } }
                                else { selectedIds.append(entry.id) }
                            }) {
                                HStack {
                                    Text(entry.label)
                                        .font(.system(size: 11))
                                    Spacer()
                                    if selected { Image(systemName: "checkmark") }
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 6)
                                .background(selected ? Color.blue.opacity(0.15) : Color.clear)
                                .clipShape(Capsule())
                            }
                        }
                    } else if searchQuery.isEmpty {
                        Text("Escribe para buscar molestias...")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.4))
                    } else {
                        Text("No se encontraron resultados para \"\(searchQuery)\"")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.4))
                    }

                    if selectedIds.isNotEmpty {
                        FlowLayout(spacing: 6) {
                            ForEach(selectedIds, id: \.self) { id in
                                let label = DISCOMFORT_CATALOG.first { $0.id == id }?.label ?? id
                                HStack(spacing: 4) {
                                    Text(label)
                                        .font(.system(size: 11))
                                    Image(systemName: "xmark")
                                        .font(.system(size: 10))
                                        .foregroundColor(.blue)
                                        .onTapGesture { selectedIds.removeAll { $0 == id } }
                                }
                                .foregroundColor(.blue)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.blue.opacity(0.2))
                                .clipShape(Capsule())
                            }
                        }
                    }
                }
            }
            .frame(maxHeight: 400)

            HStack(spacing: 8) {
                Button("Sin molestias") { onSave([]) }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                Button("Guardar") { onSave(selectedIds) }
                    .buttonStyle(.borderedProminent)
                    .disabled(selectedIds.isEmpty)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
        .background(Color(.systemGray5))
    }
}

// MARK: - PostExerciseSetCard

private struct PostExerciseSetCard: View {
    let exercise: Exercise
    let onSave: (PostExerciseQuickResult) -> Void

    var body: some View {
        VStack(spacing: 10) {
            VStack(spacing: 4) {
                Text("Feedback post-ejercicio")
                    .font(.system(size: 12, weight: .bold))
                Text("Cierra \(exercise.name) antes de avanzar.")
                    .font(.system(size: 11))
            }
            .foregroundColor(.primary)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemGray5))
            .clipShape(RoundedRectangle(cornerRadius: 12))

            PostExerciseCompactContent(
                exerciseName: exercise.name,
                showPerceivedIntensity: !exerciseHasPlannedIntensity(exercise),
                onSave: onSave
            )
        }
        .padding(12)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .shadow(color: .black.opacity(0.1), radius: 6, y: 3)
    }
}

// MARK: - Helper functions

private func exerciseHasPlannedIntensity(_ exercise: Exercise) -> Bool {
    exercise.sets.contains { set in
        set.targetRPE != nil || set.targetRIR != nil || set.targetPercentageRM != nil ||
        set.isFailure || set.intensityMode == .rpe || set.intensityMode == .rir ||
        set.intensityMode == .failure || set.intensityMode == .soloRm
    }
}

private func catalogInfoForExercise(_ exercise: Exercise) -> ExerciseMuscleInfo? {
    let canonicalId = exercise.resolvedCanonicalExerciseId()
    if let info = catalogExerciseIndex()[canonicalId] { return info }
    if let dbId = exercise.exerciseDbId?.lowercased(), let info = catalogExerciseIndex()[dbId] { return info }
    if let exId = exercise.exerciseId?.lowercased(), let info = catalogExerciseIndex()[exId] { return info }
    return nil
}

private func resolveWorkoutHeaderGroupLabel(partName: String?, type: String?, category: String?) -> String? {
    partName
}

private func workoutCatalogInfo(_ exercise: Exercise) -> (type: String?, category: String?)? {
    guard let info = catalogInfoForExercise(exercise) else { return nil }
    return (info.type, info.category)
}

private struct SanitizedWarmupSet {
    let percentage: Int
    let reps: Int
}

private func sanitizeWarmupPercentage(_ raw: Double) -> Int {
    Int(raw.rounded()).clamped(to: 20...95)
}

private func sanitizeWarmupReps(_ raw: Int, _ percentage: Int) -> Int {
    guard (1...20).contains(raw) else { return suggestedWarmupRepsForPercentage(percentage) }
    return raw
}

private func suggestedWarmupRepsForPercentage(_ percentage: Int) -> Int {
    switch percentage {
    case 90...: return 1
    case 85..<90: return 2
    case 80..<85: return 3
    case 75..<80: return 4
    case 70..<75: return 5
    case 65..<70: return 6
    case 60..<65: return 8
    case 50..<60: return 10
    default: return 12
    }
}

// MARK: - WorkoutSetSwipePage

private struct WorkoutSetSwipePage {
    enum PageType { case mobility, warmup, normal }
    let type: PageType
    let setIndex: Int
    var side: String? = nil
}

// MARK: - Exercise extension helpers

private extension Exercise {
    func expectedSidesForSet(_ setIndex: Int) -> [String] {
        guard sets.indices.contains(setIndex) else {
            return unilateralSideOrder == .rightLeft ? ["right", "left"] : ["left", "right"]
        }
        let set = sets[setIndex]
        let hasLeftOnly = set.leftTarget != nil && set.rightTarget == nil
        let hasRightOnly = set.rightTarget != nil && set.leftTarget == nil
        if hasLeftOnly { return ["left"] }
        if hasRightOnly { return ["right"] }
        return unilateralSideOrder == .rightLeft ? ["right", "left"] : ["left", "right"]
    }

    func completionKeysForSet(_ setIndex: Int) -> [String] {
        guard sets.indices.contains(setIndex) else { return [] }
        guard isEffectivelyUnilateral() else { return ["\(id)_\(setIndex)"] }
        let set = sets[setIndex]
        let hasLeftOnly = set.leftTarget != nil && set.rightTarget == nil
        let hasRightOnly = set.rightTarget != nil && set.leftTarget == nil
        if hasLeftOnly { return ["\(id)_\(setIndex)_L"] }
        if hasRightOnly { return ["\(id)_\(setIndex)_R"] }
        return ["\(id)_\(setIndex)_L", "\(id)_\(setIndex)_R"]
    }
}

// MARK: - InfoPill

private struct InfoPill: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        HStack(spacing: 4) {
            Text(label)
                .font(.system(size: 11))
                .foregroundColor(color.opacity(0.7))
            Text(value)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(color)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(color.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(color.opacity(0.25), lineWidth: 1))
    }
}

// MARK: - RestTimerPill

private struct RestTimerPill: View {
    let remainingSeconds: Int
    let totalSeconds: Int
    let exerciseName: String
    let sessionAccentColor: Color
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 6) {
                Image(systemName: "timer")
                    .font(.system(size: 12))
                Text(formatTime(seconds: remainingSeconds))
                    .font(.system(size: 14, weight: .black))
                Text(exerciseName)
                    .font(.system(size: 11))
                    .lineLimit(1)
            }
            .foregroundColor(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(sessionAccentColor.opacity(0.85))
            .clipShape(Capsule())
            .shadow(radius: 4)
            .padding(.top, 8)
        }
    }
}

// MARK: - ExerciseDrainOverlayItemV2

struct ExerciseDrainOverlayItemV2 {
    let label: String
    let delta: Int
    let channel: ExerciseDrainOverlayChannelV2
}

struct ExerciseDrainOverlayStateV2 {
    let key: Int64
    let exerciseName: String
    let items: [ExerciseDrainOverlayItemV2]
}

enum ExerciseDrainOverlayChannelV2 {
    case energy, back, muscle
}

// MARK: - WorkoutWarmupDisplaySet

struct WorkoutWarmupDisplaySet {
    let percentage: Double
    let reps: Int
    let targetWeight: Double?
}

// MARK: - FlowLayout helper

struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        var width: CGFloat = 0
        var height: CGFloat = 0
        var x: CGFloat = 0
        var y: CGFloat = 0
        var maxHeight: CGFloat = 0
        for size in sizes {
            if x + size.width > (proposal.width ?? .infinity) {
                y += maxHeight + spacing
                x = 0
                maxHeight = 0
            }
            x += size.width + spacing
            maxHeight = max(maxHeight, size.height)
            width = max(width, x)
            height = y + maxHeight
        }
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        var x = bounds.minX
        var y = bounds.minY
        var maxHeight: CGFloat = 0
        for (index, subview) in subviews.enumerated() {
            let size = sizes[index]
            if x + size.width > bounds.maxX {
                y += maxHeight + spacing
                x = bounds.minX
                maxHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            maxHeight = max(maxHeight, size.height)
        }
    }
}

// MARK: - Clamping helper

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        return min(max(self, range.lowerBound), range.upperBound)
    }
}

// MARK: - Safe array access

private extension Array {
    subscript(safe index: Int) -> Element? {
        guard indices.contains(index) else { return nil }
        return self[index]
    }
}
