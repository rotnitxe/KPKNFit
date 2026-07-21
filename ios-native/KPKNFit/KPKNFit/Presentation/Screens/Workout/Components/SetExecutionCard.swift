import SwiftUI

// MARK: - RecordActionBox

class RecordActionBox {
    var action: (() -> Void)?
}

// MARK: - Private helpers

private struct QuickLoadOption {
    let label: String
    let weight: Double
    let isAuge: Bool
}

private func quickLoadOptionsFor(currentWeightText: String, suggestedWeight: Double?, loadIncrementKg: Double) -> [QuickLoadOption] {
    let currentWeight = Double(currentWeightText) ?? suggestedWeight ?? 0.0
    let increment = loadIncrementKg > 0.0 ? loadIncrementKg : 2.5
    var baseOptions = [
        QuickLoadOption(label: "-\(increment.toTrimmedNumberString())", weight: max(currentWeight - increment, 0.0), isAuge: false),
        QuickLoadOption(label: "Actual", weight: max(currentWeight, 0.0), isAuge: false),
        QuickLoadOption(label: "+\(increment.toTrimmedNumberString())", weight: max(currentWeight + increment, 0.0), isAuge: false),
    ]
    guard let suggested = suggestedWeight else { return baseOptions }
    if baseOptions.contains(where: { abs($0.weight - suggested) < 0.01 }) {
        return baseOptions.map { opt in
            if abs(opt.weight - suggested) < 0.01 {
                return QuickLoadOption(label: "Sug.", weight: opt.weight, isAuge: true)
            }
            return opt
        }
    }
    baseOptions.append(QuickLoadOption(label: "Sug.", weight: max(suggested, 0.0), isAuge: true))
    return baseOptions
}

private func quickLoadIncrementFor(exercise: Exercise, currentSet: ExerciseSet) -> Double {
    let setupText = [
        exercise.setupDetails?.equipmentNotes,
        currentSet.machineBrand,
        exercise.contextProfilesV3.first(where: { $0.id == currentSet.contextProfileIdV3 })?.machineBrand,
        exercise.name,
    ].compactMap { $0 }.joined(separator: " ").lowercased()
    if setupText.contains("mancuerna") || setupText.contains("dumbbell") { return 1.0 }
    if setupText.contains("polea") || setupText.contains("maquina") || setupText.contains("máquina") || setupText.contains("machine") { return 5.0 }
    return 2.5
}

// MARK: - AmrapConfigSheet

private struct AmrapConfigSheet: View {
    let plannedMinReps: Int?
    let plannedTargetName: String
    let initialReachFailure: Bool
    let initialReserveReps: Int?
    let onApply: (Int?, Bool, Int?) -> Void
    let onDismiss: () -> Void

    @State private var minReps: String = ""
    @State private var reachFailure: Bool = true
    @State private var reserveReps: String = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Configurar serie AMRAP")
                    .font(.title2.bold())
                    .foregroundColor(.white)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Reps mínimas")
                        .font(.caption.bold())
                        .foregroundColor(.white.opacity(0.7))
                    TextField(plannedMinReps.map { "\($0)" } ?? "0", text: $minReps)
                        .keyboardType(.numberPad)
                        .onChange(of: minReps) { _, new in minReps = new.filter { $0.isNumber } }
                        .textFieldStyle(.roundedBorder)
                        .font(.body.bold())
                        .foregroundColor(.white)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Objetivo de la serie")
                        .font(.caption.bold())
                        .foregroundColor(.white.opacity(0.7))
                    HStack(spacing: 8) {
                        Button(action: { reachFailure = true; reserveReps = "" }) {
                            Text("Llegar al fallo")
                                .font(.caption2)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(reachFailure ? Color.blue.opacity(0.2) : Color(.darkGray))
                                .foregroundColor(reachFailure ? .blue : .white)
                                .clipShape(Capsule())
                        }
                        Button(action: { reachFailure = false }) {
                            Text("Reservar reps")
                                .font(.caption2)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(!reachFailure ? Color.blue.opacity(0.2) : Color(.darkGray))
                                .foregroundColor(!reachFailure ? .blue : .white)
                                .clipShape(Capsule())
                        }
                    }
                }
                if !reachFailure {
                    TextField("RIR (repeticiones en reserva)", text: $reserveReps)
                        .keyboardType(.numberPad)
                        .onChange(of: reserveReps) { _, new in reserveReps = new.filter { $0.isNumber } }
                        .textFieldStyle(.roundedBorder)
                }
                Button(action: {
                    onApply(Int(minReps) ?? plannedMinReps, reachFailure, Int(reserveReps))
                }) {
                    Text("Aplicar")
                        .font(.callout.bold())
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
            .onAppear {
                minReps = plannedMinReps.map { "\($0)" } ?? ""
                reachFailure = initialReachFailure
                reserveReps = initialReserveReps.map { "\($0)" } ?? ""
            }
        }
    }
}

// MARK: - WorkoutStepperField

private struct WorkoutStepperField: View {
    let value: String
    let onValueChange: (String) -> Void
    let onDecrement: () -> Void
    let onIncrement: () -> Void
    var buttonsEnabled: Bool = true
    var textInputEnabled: Bool = true
    var isError: Bool = false
    var accentColor: Color = .blue
    var roomier: Bool = false

    private var controlHeight: CGFloat { roomier ? 60 : 56 }
    private var buttonWidth: CGFloat { roomier ? 34 : 32 }

    var body: some View {
        let containerColor: Color = isError ? Color.red.opacity(0.25) : Color(.systemGray5).opacity(0.92)
        let borderColor: Color = isError ? Color.red.opacity(0.38) : Color(.systemGray3).opacity(0.28)
        let disabledColor: Color = Color(.systemGray).opacity(0.28)
        let enabledIconColor: Color = Color(.systemGray).opacity(0.74)

        HStack(spacing: roomier ? 4 : 3) {
            Button(action: onDecrement) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color(.systemGray4).opacity(buttonsEnabled ? 0.68 : 0.34))
                        .frame(width: buttonWidth, height: controlHeight - 10)
                    Image(systemName: "minus")
                        .font(.system(size: roomier ? 11 : 10, weight: .bold))
                        .foregroundColor(buttonsEnabled ? enabledIconColor : disabledColor)
                }
            }
            .disabled(!buttonsEnabled)
            .frame(width: buttonWidth)

            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color(.systemBackground).opacity(0.36))
                Text(value)
                    .font(.title2.weight(.black))
                    .foregroundColor(isError ? .red : Color(.label))
                    .lineLimit(1)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 4)
            }
            .frame(maxWidth: .infinity)
            .frame(height: controlHeight - 10)

            Button(action: onIncrement) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(buttonsEnabled ? accentColor.opacity(0.16) : Color(.systemGray4).opacity(0.34))
                        .frame(width: buttonWidth, height: controlHeight - 10)
                    Image(systemName: "plus")
                        .font(.system(size: roomier ? 11 : 10, weight: .bold))
                        .foregroundColor(buttonsEnabled ? accentColor : disabledColor)
                }
            }
            .disabled(!buttonsEnabled)
            .frame(width: buttonWidth)
        }
        .padding(.horizontal, roomier ? 4 : 3)
        .padding(.vertical, 5)
        .frame(height: controlHeight)
        .background(containerColor)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(borderColor, lineWidth: 1))
    }
}

// MARK: - IntegratedLoadInput

private struct IntegratedLoadInput: View {
    let value: String
    let onValueChange: (String) -> Void
    let label: String
    let placeholder: String?
    let options: [QuickLoadOption]
    let onWeightSelected: (String) -> Void
    let onOpenLoadMode: () -> Void
    let accentColor: Color
    var loadMode: LoadModeV2 = .LOAD

    var body: some View {
        let isBodyweightMode = loadMode == .BODYWEIGHT
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.caption.weight(.semibold))
                    .foregroundColor(Color(.label).opacity(0.6))
                    .lineLimit(1)
                ZStack(alignment: .leading) {
                    if isBodyweightMode {
                        Text("Peso corporal")
                            .font(.body.weight(.bold).italic())
                            .foregroundColor(Color(.label).opacity(0.36))
                            .lineLimit(1)
                    } else if value.isBlankOrEmpty, let ph = placeholder {
                        Text(ph)
                            .font(.body.weight(.bold).italic())
                            .foregroundColor(Color(.label).opacity(0.36))
                            .lineLimit(1)
                    }
                    TextField("", text: Binding(
                        get: { isBodyweightMode ? "" : value },
                        set: { if !isBodyweightMode { onValueChange($0) } }
                    ))
                    .keyboardType(.decimalPad)
                    .disabled(isBodyweightMode)
                    .font(.title2.weight(.black))
                    .foregroundColor(isBodyweightMode ? Color(.label).opacity(0.36) : Color(.label))
                }
            }
            .frame(maxWidth: .infinity)
            .frame(minWidth: 92)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 5) {
                    ForEach(options.indices, id: \.self) { idx in
                        let option = options[idx]
                        Button(action: { onWeightSelected(option.weight.toTrimmedNumberString()) }) {
                            VStack(spacing: 1) {
                                Text(option.label)
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(option.isAuge ? accentColor : Color(.systemGray))
                                let chipUnit: String = {
                                    switch loadMode {
                                    case .LASTRE: return " lastre"
                                    case .ASSISTED: return " asist."
                                    default: return ""
                                    }
                                }()
                                Text("\(option.weight.toTrimmedNumberString())\(chipUnit)")
                                    .font(.system(size: 10, weight: .black))
                                    .foregroundColor(Color(.label).opacity(0.82))
                            }
                            .padding(.horizontal, 7)
                            .padding(.vertical, 5)
                            .background(option.isAuge ? accentColor.opacity(0.16) : Color(.systemGray5).opacity(0.72))
                            .clipShape(RoundedRectangle(cornerRadius: 11))
                            .overlay(RoundedRectangle(cornerRadius: 11).stroke(
                                option.isAuge ? accentColor.opacity(0.48) : Color(.systemGray3).opacity(0.22),
                                lineWidth: 1
                            ))
                        }
                    }
                }
            }
            .frame(maxWidth: 152)

            Button(action: onOpenLoadMode) {
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 12))
                    .foregroundColor(Color(.label).opacity(0.7))
            }
            .frame(width: 30, height: 30)
        }
        .padding(.leading, 12)
        .padding(.trailing, 6)
        .padding(.vertical, 7)
        .frame(minHeight: 58)
        .background(WorkoutUiTokens.setInnerHighestColor())
        .clipShape(WorkoutUiTokens.innerCardShape)
        .overlay(WorkoutUiTokens.innerCardShape.stroke(Color(.systemGray3).opacity(0.36), lineWidth: 1))
    }
}

// MARK: - WorkoutMiniTextField

private struct WorkoutMiniTextField: View {
    let value: String
    let onValueChange: (String) -> Void
    let label: String
    var accentColor: Color = .blue
    var keyboardType: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption)
                .foregroundColor(accentColor)
            TextField("", text: Binding(get: { value }, set: onValueChange))
                .keyboardType(keyboardType)
                .font(.body.bold())
                .foregroundColor(Color(.label))
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .frame(minHeight: 48)
                .background(WorkoutUiTokens.setInnerHighestColor())
                .clipShape(WorkoutUiTokens.innerCardShape)
                .overlay(WorkoutUiTokens.innerCardShape.stroke(Color(.systemGray3).opacity(0.4), lineWidth: 1))
        }
    }
}

// MARK: - SetExecutionCard

struct SetExecutionCard: View {
    let exercise: Exercise
    let setIndex: Int
    let currentSet: ExerciseSet
    let ghostSet: CompletedSet?
    let sessionCompletedSet: CompletedSet?
    let weightSuggestion: WeightSuggestion?
    var sessionAccentColor: Color = .blue
    var isJustLogged: Bool = false
    let lastOutcomeV2: SetOutcomeV2?
    let lastHomologatedResultV3: HomologatedPerformanceResult?
    var showPRsInWorkout: Bool = true
    var hapticFeedbackEnabled: Bool = true
    let onShowHistory: () -> Void
    let onSetBodyWeight: (Double) -> Void
    let initialBodyWeight: Double?
    let recordActionBox: RecordActionBox
    var isActivePage: Bool = true
    let initialDraft: WorkoutSetDraft?
    let onDraftChange: (WorkoutSetDraft, String?) -> Void
    var onExecutionError: (() -> Void)?
    var persistedLoadModeBySet: [String: LoadModeV2] = [:]
    var persistedLoadModeByExercise: [String: LoadModeV2] = [:]
    var activeTag: String? = nil
    var amrapCalibrationMessage: String? = nil
    var activeSide: String? = nil
    var sideLocked: Bool = false
    var rmSuggestedWeight: Double? = nil
    var onRmWeightConsumed: (() -> Void)? = nil
    var onGoToPrevSet: (() -> Void)? = nil
    var onGoToNextSet: (() -> Void)? = nil
    let onRecord: (
        _ loadMode: LoadModeV2,
        _ unitMode: UnitModeV2,
        _ weight: Double,
        _ value: Double,
        _ intensity: Double?,
        _ advanced: SetAdvancedFeedback,
        _ amrapOverride: Bool,
        _ bodyWeight: Double?,
        _ side: String?
    ) -> Void
    let exerciseReadiness: ExerciseReadiness?
    let readinessAdjustment: SetAdjustmentSuggestion?
    let onApplyReadinessAdjustment: ((SetAdjustmentSuggestion) -> Void)?

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    // MARK: - State

    @State private var weightText: String = ""
    @State private var lastAutoFilledWeight: String = ""
    @State private var hasManualWeightOverride: Bool = false
    @State private var valueText: String = ""
    @State private var showReadinessAdjustmentSheet: Bool = false
    @State private var leftWeightText: String = ""
    @State private var rightWeightText: String = ""
    @State private var leftValueText: String = ""
    @State private var rightValueText: String = ""
    @State private var intensityText: String = ""
    @State private var bodyWeightText: String = ""
    @State private var showBodyWeightPrompt: Bool = false
    @State private var romValue: Int? = 100
    @State private var selectedSide: String = "left"
    @State private var loadMode: LoadModeV2 = .LOAD
    @State private var reachedFailure: Bool = false
    @State private var isFailedSet: Bool = false
    @State private var isAmrap: Bool = false
    @State private var showAmrapSheet: Bool = false
    @State private var amrapReachFailure: Bool = true
    @State private var amrapReserveReps: Int? = nil
    @State private var dropSetEnabled: Bool = false
    @State private var restPauseEnabled: Bool = false
    @State private var showPartialsMode: Bool = false
    @State private var assistedRepsValue: Int = 0
    @State private var adjustmentsTab: Int = -1
    @State private var loadModeMenuExpanded: Bool = false
    @State private var dropSets: [DropSetEntry] = [DropSetEntry(weight: 0, reps: 0)]
    @State private var restPauseSets: [RestPauseData] = [RestPauseData(restTime: 20, reps: 0)]
    @State private var partialSets: [Int] = [0]
    @State private var reportedIntensityMode: IntensityMode = .RPE
    @State private var timerRunning: Bool = false
    @State private var timerRemainingSeconds: Int = 0
    @State private var timerElapsedSeconds: Int = 0

    // MARK: - Body

    var body: some View {
        let isNarrowScreen = horizontalSizeClass == .compact
        let suggestedWeightText: String? = weightSuggestion?.suggestedWeight?.toTrimmedNumberString()
        let completedWeightText: String? = sessionCompletedSet?.weight
            .flatMap { $0 > 0.0 ? $0.toTrimmedNumberString() : nil }

        let defaultWeight: String = {
            if let cwt = completedWeightText { return cwt }
            if currentSet.targetPercentageRM != nil {
                if let ghost = ghostSet {
                    if ghost.weight > 0 && ghost.REPS > 0 && ghost.REPS < 37 {
                        let ghost1RM = ghost.weight / (1.0278 - 0.0278 * Double(ghost.REPS))
                        let calc = ((currentSet.targetPercentageRM! / 100.0) * ghost1RM * 2)
                        let rounded = (calc * 2).rounded() / 2
                        return rounded.toTrimmedNumberString()
                    }
                    if ghost.weight > 0 { return ghost.weight.toTrimmedNumberString() }
                }
                return currentSet.weight?.toTrimmedNumberString() ?? ""
            }
            if let ghost = ghostSet, ghost.weight > 0 { return ghost.weight.toTrimmedNumberString() }
            return currentSet.weight?.toTrimmedNumberString() ?? ""
        }()

        let resolvedPlannedUnitMode: UnitModeV2 = currentSet.unitModeV2 ?? {
            if exercise.trainingMode == .TIME || currentSet.targetDuration != nil { return .TIME }
            if exercise.trainingMode == .DISTANCE { return .DISTANCE }
            if exercise.trainingMode == .CUSTOM { return .CUSTOM }
            return .REPS
        }()

        let defaultValue: String = {
            switch resolvedPlannedUnitMode {
            case .TIME:
                return (sessionCompletedSet?.TIMESeconds ?? currentSet.targetDuration ?? currentSet.plannedTargetV2.map(Int.init) ?? ghostSet?.TIMESeconds)
                    .map { "\($0)" } ?? ""
            case .DISTANCE, .CUSTOM:
                if let v = sessionCompletedSet?.REPS, v > 0 { return "\(v)" }
                if let v = currentSet.plannedTargetV2 { return v.toTrimmedNumberString() }
                if let v = currentSet.targetReps { return "\(v)" }
                return ghostSet?.REPS.map { "\($0)" } ?? ""
            case .REPS:
                if let v = sessionCompletedSet?.REPS, v > 0 { return "\(v)" }
                if let v = currentSet.targetReps { return "\(v)" }
                if let v = currentSet.plannedTargetV2 { return "\(Int(v))" }
                return ghostSet?.REPS.map { "\($0)" } ?? ""
            }
        }()

        let isTimeMode = resolvedPlannedUnitMode == .TIME

        let basePlannedTarget: Int? = {
            switch resolvedPlannedUnitMode {
            case .TIME: return currentSet.targetDuration ?? currentSet.plannedTargetV2.map(Int.init)
            case .DISTANCE, .CUSTOM: return currentSet.plannedTargetV2.map(Int.init) ?? currentSet.targetReps
            case .REPS: return currentSet.targetReps ?? currentSet.plannedTargetV2.map(Int.init)
            }
        }()

        let plannedIntensityMode: IntensityMode = {
            if let im = currentSet.intensityMode { return im }
            if currentSet.targetRIR != nil { return .RIR }
            return .RPE
        }()

        let supportsIndependentSides = exercise.isEffectivelyUnilateral()
        let lockedSide = activeSide?.takeIf(supportsIndependentSides && sideLocked)
        let initialSelectedSide: String = initialDraft?.selectedSide ?? lockedSide ?? "left"

        let draftWeightText: String? = initialDraft?.weightText?.nilIfEmpty
        let draftValueText: String? = initialDraft?.valueText?.nilIfEmpty

        let targetLeftWeight: String = currentSet.leftTarget?.weight?.toTrimmedNumberString() ?? defaultWeight
        let targetRightWeight: String = currentSet.rightTarget?.weight?.toTrimmedNumberString() ?? defaultWeight

        // LaunchedEffect equivalent setup
        let exId = exercise.id
        let setIdx = setIndex
        let completedSetId = sessionCompletedSet?.id

        // Side management helpers
        func valueTextForSide(_ side: String) -> String { side == "left" ? leftValueText : rightValueText }
        func weightTextForSide(_ side: String) -> String { side == "left" ? leftWeightText : rightWeightText }
        func updateActiveValueText(_ newValue: String) {
            valueText = newValue
            if supportsIndependentSides {
                if selectedSide == "left" { leftValueText = newValue }
                else { rightValueText = newValue }
            }
        }
        func updateActiveWeightText(_ newWeight: String, markManual: Bool = true) {
            if markManual { hasManualWeightOverride = true }
            weightText = newWeight
            if supportsIndependentSides {
                if selectedSide == "left" { leftWeightText = newWeight }
                else { rightWeightText = newWeight }
            }
        }
        func selectSide(_ side: String) {
            guard selectedSide != side else { return }
            selectedSide = side
            if supportsIndependentSides {
                valueText = valueTextForSide(side)
                weightText = weightTextForSide(side)
            }
        }

        let persistedLoadMode: LoadModeV2? = resolvePersistedLoadModeForSet(
            exerciseId: exId, setIdx: setIdx, tagId: activeTag,
            persistedLoadModeBySet: persistedLoadModeBySet,
            persistedLoadModeByExercise: persistedLoadModeByExercise
        )

        let ghostSuggestedWeightText: String? = suggestedWeightText?.takeIf(weightText.isBlankOrEmpty)

        func intensityStep() -> Double {
            reportedIntensityMode == .RIR ? 1.0 : (plannedIntensityMode == .FAILURE ? 1.0 : 0.5)
        }

        func decreaseIntensityInput() {
            if isFailedSet { return }
            if reachedFailure {
                reachedFailure = false
                reportedIntensityMode = .RPE
                intensityText = "10"
                return
            }
            let current = Double(intensityText) ?? (reportedIntensityMode == .RIR ? 0.0 : 10.0)
            if reportedIntensityMode == .RIR {
                let next = current - 1.0
                if next < 0.0 {
                    reachedFailure = true
                    intensityText = ""
                } else {
                    intensityText = "\(Int(next))"
                }
            } else {
                intensityText = (current - intensityStep()).clamped(to: 0.0...100.0).toTrimmedNumberString()
            }
        }

        func increaseIntensityInput() {
            if isFailedSet { return }
            if reachedFailure {
                reachedFailure = false
                reportedIntensityMode = .RIR
                intensityText = "0"
                return
            }
            let current = Double(intensityText) ?? 0.0
            if reportedIntensityMode == .RIR {
                intensityText = "\(Int(current + 1.0))"
            } else {
                let next = current + intensityStep()
                if next > 10.0 {
                    reachedFailure = true
                    intensityText = ""
                } else {
                    intensityText = next.toTrimmedNumberString()
                }
            }
        }

        func fallbackIntensityForMode(_ mode: IntensityMode) -> String {
            switch mode {
            case .RIR: return "\(currentSet.targetRIR ?? 1)"
            default: return (currentSet.targetRPE ?? 9.0).toTrimmedNumberString()
            }
        }

        func ensureReportedIntensityText() {
            if intensityText.isBlankOrEmpty {
                intensityText = fallbackIntensityForMode(reportedIntensityMode)
            }
        }

        let achievedValue = Double(valueText) ?? 0.0
        let targetDelta: Double? = basePlannedTarget.map { achievedValue - Double($0) }
        let debt: Double = max((basePlannedTarget.map(Double.init) ?? 0.0) - achievedValue, 0.0)

        let activePlannedRpe: Double? = currentSet.targetRPE
        let activePlannedRir: Int? = currentSet.targetRIR
        let registeredIntensity: Double? = Double(intensityText)
        let expectedIntensity: Double? = {
            switch reportedIntensityMode {
            case .RIR:
                switch plannedIntensityMode {
                case .FAILURE: return nil
                case .RIR: return activePlannedRir.map(Double.init)
                default: return activePlannedRpe.map { (10.0 - $0).clamped(to: 0.0...10.0) }
                }
            default:
                switch plannedIntensityMode {
                case .FAILURE: return nil
                case .RIR: return activePlannedRir.map { (10.0 - Double($0)).clamped(to: 0.0...10.0) }
                default: return activePlannedRpe
                }
            }
        }()
        let intensityDelta: Double? = {
            guard let e = expectedIntensity, let r = registeredIntensity else { return nil }
            if reportedIntensityMode == .RIR { return e - r }
            return r - e
        }()

        let isNoFalloCase = !reachedFailure && plannedIntensityMode == .FAILURE

        let difficultyLabel: String? = {
            if reachedFailure { return "Fallo alcanzado" }
            if isFailedSet { return "Serie fallida" }
            guard let d = intensityDelta else { return nil }
            if d <= -0.5 { return "Más fácil" }
            if d >= 0.5 { return "Más difícil" }
            return "Igual"
        }()

        let plannedValueLabel = isTimeMode ? "Tiempo" : "Reps"

        let expectedIntensityLabel: String = {
            if currentSet.targetPercentageRM != nil { return "%RM a trabajar" }
            if currentSet.isAmrap { return "AMRAP" }
            if plannedIntensityMode == .FAILURE { return "FALLO" }
            if plannedIntensityMode == .RIR { return "RIR" }
            return "RPE"
        }()

        let expectedIntensityValue: String = {
            if let pct = currentSet.targetPercentageRM { return "\(Int(pct))%" }
            if currentSet.isAmrap { return "AMRAP" }
            if plannedIntensityMode == .FAILURE { return "F" }
            if plannedIntensityMode == .RIR { return activePlannedRir.map { "\($0)" } ?? "-" }
            return activePlannedRpe?.toTrimmedNumberString() ?? "-"
        }()

        let plannedIntensityDisplayLabel: String = {
            if plannedIntensityMode == .FAILURE && !reachedFailure {
                return reportedIntensityMode == .RIR ? "RIR" : "RPE"
            }
            return expectedIntensityLabel
        }()

        let plannedIntensityDisplayValue: String = {
            if plannedIntensityMode == .FAILURE && !reachedFailure {
                return intensityText.nilIfEmpty ?? "-"
            }
            return expectedIntensityValue
        }()

        let isExecutionError = isFailedSet

        let intensityFieldLabel: String = {
            if isExecutionError { return "ERROR" }
            if reachedFailure { return "F" }
            if reportedIntensityMode == .RIR { return "RIR" }
            return "RPE"
        }()

        let loadFieldLabel: String = {
            switch loadMode {
            case .LOAD: return "Carga (kg)"
            case .BODYWEIGHT: return "Peso corporal"
            case .LASTRE: return "Lastre (kg)"
            case .ASSISTED: return "Asistencia (kg)"
            }
        }()

        let timerTargetSeconds: Int = basePlannedTarget ?? Int(valueText) ?? 0
        let isPrGlobal = lastHomologatedResultV3?.isGlobalPr == true
        let isPrContext = lastHomologatedResultV3?.isContextPr == true

        let reportWeightText: String = supportsIndependentSides ? weightTextForSide(selectedSide) : weightText
        let reportValueText: String = supportsIndependentSides ? valueTextForSide(selectedSide) : valueText

        let activeInitialWeight: String = {
            if supportsIndependentSides {
                return selectedSide == "left" ? targetLeftWeight : targetRightWeight
            }
            return defaultWeight
        }()

        let activeInitialValue: String = {
            if supportsIndependentSides {
                return selectedSide == "left"
                    ? (currentSet.leftTarget.map { sideTargetValueText($0, isTimeMode: isTimeMode, defaultValue: defaultValue, unitMode: currentSet.unitModeV2) } ?? defaultValue)
                    : (currentSet.rightTarget.map { sideTargetValueText($0, isTimeMode: isTimeMode, defaultValue: defaultValue, unitMode: currentSet.unitModeV2) } ?? defaultValue)
            }
            return defaultValue
        }()

        let initialIntensityForDraft: String = {
            currentSet.targetRPE?.toTrimmedNumberString()
                ?? currentSet.targetRIR.map { "\($0)" }
                ?? ""
        }()

        let partialRepsTotal: Int = showPartialsMode ? max(partialSets.reduce(0, +), 0) : 0

        let roomyStepper = supportsIndependentSides || exercise.isInSuperset()

        ZStack {
            VStack(spacing: 0) {
                // Main card surface
                VStack(spacing: 8) {
                    // Side selector
                    if supportsIndependentSides && !sideLocked {
                        sideSelectorView
                    }

                    // History row
                    historyRow(ghostSet: ghostSet, supportsIndependentSides: supportsIndependentSides, onShowHistory: onShowHistory)

                    // Readiness chip
                    if let readiness = exerciseReadiness {
                        readinessChipView(readiness: readiness)
                    }

                    // Planned info section
                    plannedInfoSection(
                        isFailedSet: isFailedSet,
                        sessionAccentColor: sessionAccentColor,
                        onGoToPrevSet: onGoToPrevSet,
                        onGoToNextSet: onGoToNextSet,
                        plannedValueLabel: plannedValueLabel,
                        basePlannedTarget: basePlannedTarget,
                        isAmrap: isAmrap,
                        isTimeMode: isTimeMode,
                        targetDelta: targetDelta,
                        plannedIntensityDisplayLabel: plannedIntensityDisplayLabel,
                        plannedIntensityDisplayValue: plannedIntensityDisplayValue,
                        plannedIntensityMode: plannedIntensityMode,
                        reachedFailure: reachedFailure,
                        difficultyLabel: difficultyLabel,
                        intensityDelta: intensityDelta,
                        isNarrowScreen: isNarrowScreen,
                        currentSet: currentSet,
                        lastHomologatedResultV3: lastHomologatedResultV3,
                        ghostSet: ghostSet,
                        exerciseReadiness: exerciseReadiness,
                        readinessAdjustment: readinessAdjustment,
                        onApplyReadinessAdjustment: onApplyReadinessAdjustment
                    )

                    Divider()
                        .padding(.horizontal, 4)
                        .background(Color(white: 0.2))

                    // Report section
                    reportSection(
                        isFailedSet: isFailedSet,
                        sessionAccentColor: sessionAccentColor,
                        supportsIndependentSides: supportsIndependentSides,
                        selectedSide: selectedSide,
                        reportWeightText: reportWeightText,
                        reportValueText: reportValueText,
                        loadFieldLabel: loadFieldLabel,
                        loadMode: loadMode,
                        ghostSuggestedWeightText: ghostSuggestedWeightText,
                        weightSuggestion: weightSuggestion,
                        exercise: exercise,
                        currentSet: currentSet,
                        isTimeMode: isTimeMode,
                        isExecutionError: isExecutionError,
                        reachedFailure: reachedFailure,
                        roomyStepper: roomyStepper,
                        isNarrowScreen: isNarrowScreen,
                        intensityFieldLabel: intensityFieldLabel,
                        intensityText: intensityText,
                        exerciseReadiness: exerciseReadiness,
                        readinessAdjustment: readinessAdjustment,
                        showBodyWeightPrompt: showBodyWeightPrompt,
                        bodyWeightText: bodyWeightText,
                        timerRunning: timerRunning,
                        timerRemainingSeconds: timerRemainingSeconds,
                        timerElapsedSeconds: timerElapsedSeconds,
                        timerTargetSeconds: timerTargetSeconds,
                        plannedTarget: basePlannedTarget,
                        amrapCalibrationMessage: amrapCalibrationMessage
                    )

                    // Body weight prompt
                    if showBodyWeightPrompt || (loadMode != .LOAD && bodyWeightText.isBlankOrEmpty) {
                        bodyWeightPromptView
                    }

                    // Timer support text
                    if isTimeMode {
                        timerSupportView(
                            timerRunning: timerRunning,
                            timerRemainingSeconds: timerRemainingSeconds,
                            timerElapsedSeconds: timerElapsedSeconds,
                            plannedTarget: basePlannedTarget,
                            sessionAccentColor: sessionAccentColor
                        )
                    }

                    // AMRAP calibration message
                    if let msg = amrapCalibrationMessage {
                        amrapCalibrationView(msg: msg)
                    }

                    // Adjustments tabs
                    adjustmentsTabView(
                        adjustmentsTab: $adjustmentsTab,
                        sessionAccentColor: sessionAccentColor,
                        isFailedSet: isFailedSet,
                        isAmrap: isAmrap,
                        plannedTarget: basePlannedTarget,
                        isTimeMode: isTimeMode,
                        showPartialsMode: showPartialsMode,
                        dropSetEnabled: dropSetEnabled,
                        restPauseEnabled: restPauseEnabled,
                        partialSets: $partialSets,
                        dropSets: $dropSets,
                        restPauseSets: $restPauseSets,
                        sessionAccentColor: sessionAccentColor
                    )
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
            }
            .background(
                isFailedSet
                    ? WorkoutUiTokens.dangerContainerColor().opacity(0.15)
                    : WorkoutUiTokens.setCardColor()
            )
            .clipShape(WorkoutUiTokens.cardShape)
            .overlay(
                WorkoutUiTokens.cardShape.stroke(
                    isFailedSet ? Color.red : Color(.systemGray3).opacity(0.15),
                    lineWidth: 1
                )
            )
        }
        .onAppear {
            // Initialize states from parameters
            weightText = draftWeightText ?? completedWeightText ?? ""
            lastAutoFilledWeight = defaultWeight
            hasManualWeightOverride = !(draftWeightText?.isBlankOrEmpty ?? true) || completedWeightText != nil
            valueText = draftValueText ?? defaultValue
            let initialLeftWeightVal = initialSelectedSide == "left" ? (draftWeightText ?? targetLeftWeight) : targetLeftWeight
            let initialRightWeightVal = initialSelectedSide == "right" ? (draftWeightText ?? targetRightWeight) : targetRightWeight
            leftWeightText = initialLeftWeightVal
            rightWeightText = initialRightWeightVal

            let initialLeftValueVal: String = {
                guard let t = currentSet.leftTarget else { return defaultValue }
                return sideTargetValueText(t, isTimeMode: isTimeMode, defaultValue: defaultValue, unitMode: currentSet.unitModeV2)
            }()
            let initialRightValueVal: String = {
                guard let t = currentSet.rightTarget else { return defaultValue }
                return sideTargetValueText(t, isTimeMode: isTimeMode, defaultValue: defaultValue, unitMode: currentSet.unitModeV2)
            }()
            leftValueText = initialSelectedSide == "left" ? (draftValueText ?? initialLeftValueVal) : initialLeftValueVal
            rightValueText = initialSelectedSide == "right" ? (draftValueText ?? initialRightValueVal) : initialRightValueVal

            selectedSide = initialSelectedSide

            intensityText = initialDraft?.intensityText
                ?? sessionCompletedSet?.actualIntensityValue?.toTrimmedNumberString()
                ?? sessionCompletedSet?.RPE?.toTrimmedNumberString()
                ?? currentSet.targetRPE?.toTrimmedNumberString()
                ?? currentSet.targetRIR.map { "\($0)" }
                ?? ""

            bodyWeightText = initialBodyWeight?.toTrimmedNumberString() ?? ""
            showBodyWeightPrompt = false
            romValue = initialDraft?.rom ?? sessionCompletedSet?.rom ?? 100

            loadMode = initialDraft?.LOADMode ?? currentSet.LOADModeV2 ?? persistedLoadMode ?? .LOAD

            reachedFailure = initialDraft?.reachedFailure ?? (sessionCompletedSet?.isFailure == true || currentSet.isFailure || currentSet.intensityMode == .FAILURE)
            isFailedSet = false
            isAmrap = currentSet.isAmrap
            amrapReachFailure = true
            amrapReserveReps = nil
            dropSetEnabled = currentSet.isDropSet || !currentSet.dropSets.isEmpty
            restPauseEnabled = currentSet.isRestPause || !currentSet.restPauses.isEmpty
            assistedRepsValue = initialDraft?.ASSISTEDReps ?? sessionCompletedSet?.ASSISTEDReps ?? 0
            adjustmentsTab = -1
            loadModeMenuExpanded = false

            if dropSetEnabled {
                dropSets = currentSet.dropSets.isEmpty
                    ? [DropSetEntry(weight: 0, reps: 0)]
                    : currentSet.dropSets.map { DropSetEntry(weight: $0.weight, reps: $0.REPS) }
            } else {
                dropSets = [DropSetEntry(weight: 0, reps: 0)]
            }
            if restPauseEnabled {
                restPauseSets = currentSet.restPauses.isEmpty
                    ? [RestPauseData(restTime: 20, reps: 0)]
                    : currentSet.restPauses
            } else {
                restPauseSets = [RestPauseData(restTime: 20, reps: 0)]
            }

            reportedIntensityMode = {
                if sessionCompletedSet?.actualIntensityMode == .RIR { return .RIR }
                if currentSet.targetRIR != nil || plannedIntensityMode == .RIR { return .RIR }
                return .RPE
            }()

            timerRunning = false
            timerElapsedSeconds = 0
            timerRemainingSeconds = basePlannedTarget ?? 0

            updateRecordClosure()
        }
        .onDisappear {
            recordActionBox.action = nil
        }
        .onChange(of: lockedSide) { _, newLocked in
            if let s = newLocked { selectSide(s) }
        }
        .onChange(of: currentSet.id) { _, _ in
            dropSetEnabled = currentSet.isDropSet || !currentSet.dropSets.isEmpty
            restPauseEnabled = currentSet.isRestPause || !currentSet.restPauses.isEmpty
            if dropSetEnabled {
                dropSets = currentSet.dropSets.isEmpty
                    ? [DropSetEntry(weight: 0, reps: 0)]
                    : currentSet.dropSets.map { DropSetEntry(weight: $0.weight, reps: $0.REPS) }
            }
            if restPauseEnabled {
                restPauseSets = currentSet.restPauses.isEmpty
                    ? [RestPauseData(restTime: 20, reps: 0)]
                    : currentSet.restPauses
            }
            updateRecordClosure()
        }
        .onChange(of: exId) { _, _ in
            timerRunning = false
            timerElapsedSeconds = 0
            timerRemainingSeconds = basePlannedTarget ?? 0
            updateRecordClosure()
        }
        .onChange(of: setIdx) { _, _ in
            timerRunning = false
            timerElapsedSeconds = 0
            timerRemainingSeconds = basePlannedTarget ?? 0
            updateRecordClosure()
        }
        .onChange(of: basePlannedTarget) { _, newTarget in
            timerRunning = false
            timerElapsedSeconds = 0
            timerRemainingSeconds = newTarget ?? 0
            updateRecordClosure()
        }
        .onChange(of: isJustLogged) { _, newVal in
            if newVal && showPRsInWorkout && hapticFeedbackEnabled && (isPrGlobal || isPrContext) {
                triggerPRCelebrationHaptic()
            }
        }
        .onChange(of: rmSuggestedWeight) { _, newWeight in
            if let w = newWeight {
                updateActiveWeightText(w.toTrimmedNumberString())
                onRmWeightConsumed?()
            }
        }
        .onChange(of: isNoFalloCase) { _, noFallo in
            if noFallo { ensureReportedIntensityText() }
        }
        .onChange(of: reportedIntensityMode) { _, _ in
            if isNoFalloCase { ensureReportedIntensityText() }
        }
        .onChange(of: timerRunning) { _, running in
            if running && timerRemainingSeconds > 0 {
                startTimer()
            }
        }
        .onChange(of: isActivePage) { _, _ in
            updateRecordClosure()
        }
        .onChange(of: reportWeightText) { _, _ in emitDraftChange() }
        .onChange(of: reportValueText) { _, _ in emitDraftChange() }
        .onChange(of: intensityText) { _, _ in emitDraftChange() }
        .onChange(of: loadMode) { _, _ in emitDraftChange() }
        .onChange(of: selectedSide) { _, _ in emitDraftChange() }
        .onChange(of: reachedFailure) { _, _ in emitDraftChange() }
        .onChange(of: partialRepsTotal) { _, _ in emitDraftChange() }
        .onChange(of: romValue) { _, _ in emitDraftChange() }
        .onChange(of: assistedRepsValue) { _, _ in emitDraftChange() }
        .sheet(isPresented: $showAmrapSheet) {
            AmrapConfigSheet(
                plannedMinReps: basePlannedTarget,
                plannedTargetName: isTimeMode ? "s" : "reps",
                initialReachFailure: amrapReachFailure,
                initialReserveReps: amrapReserveReps,
                onApply: { minReps, reachFailure, reserveReps in
                    isAmrap = true
                    amrapReachFailure = reachFailure
                    amrapReserveReps = reserveReps
                    if let m = minReps { updateActiveValueText("\(m)") }
                    showAmrapSheet = false
                },
                onDismiss: { showAmrapSheet = false }
            )
        }
        .sheet(isPresented: $showReadinessAdjustmentSheet) {
            if let readiness = exerciseReadiness {
                let rm1: Double? = lastHomologatedResultV3?.estimatedRm ?? ghostSet.flatMap { ghost in
                    if ghost.weight > 0 && ghost.REPS > 0 && ghost.REPS < 37 {
                        return ghost.weight / (1.0278 - 0.0278 * Double(ghost.REPS))
                    }
                    return nil
                }
                SetAdjustmentOverlay(
                    exercise: exercise,
                    currentSet: currentSet,
                    setIndex: setIndex,
                    exerciseReadiness: readiness,
                    weightSuggestion: weightSuggestion,
                    averageErm: rm1,
                    bodyWeight: initialBodyWeight ?? Double(bodyWeightText),
                    loadMode: loadMode,
                    onDismiss: { showReadinessAdjustmentSheet = false },
                    onApply: { suggestion in
                        onApplyReadinessAdjustment?(suggestion)
                        showReadinessAdjustmentSheet = false
                    }
                )
            }
        }
    }

    // MARK: - Timer

    private func startTimer() {
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: false) { _ in
            if timerRunning && timerRemainingSeconds > 0 {
                timerRemainingSeconds -= 1
                timerElapsedSeconds += 1
                if timerRemainingSeconds <= 0 {
                    timerRunning = false
                    if timerElapsedSeconds > 0 {
                        updateActiveValueText("\(timerElapsedSeconds)")
                    }
                } else {
                    startTimer()
                }
            }
        }
    }

    // MARK: - Draft change emission

    private func emitDraftChange() {
        guard isActivePage else { return }
        let initialLoadMode = currentSet.LOADModeV2 ?? persistedLoadMode ?? .LOAD
        let initialFailure = currentSet.isFailure || currentSet.intensityMode == .FAILURE
        let initialSide = lockedSide ?? "left"
        let partialRepsTotal = showPartialsMode ? max(partialSets.reduce(0, +), 0) : 0
        let activeInitialWeight: String = {
            if supportsIndependentSides {
                return selectedSide == "left"
                    ? (currentSet.leftTarget?.weight?.toTrimmedNumberString() ?? defaultWeight)
                    : (currentSet.rightTarget?.weight?.toTrimmedNumberString() ?? defaultWeight)
            }
            return defaultWeight
        }()
        let activeInitialValue: String = {
            if supportsIndependentSides {
                let target = selectedSide == "left" ? currentSet.leftTarget : currentSet.rightTarget
                return sideTargetValueText(target, isTimeMode: isTimeMode, defaultValue: defaultValue, unitMode: currentSet.unitModeV2)
            }
            return defaultValue
        }()
        let initialIntensityForDraft: String = currentSet.targetRPE?.toTrimmedNumberString() ?? currentSet.targetRIR.map { "\($0)" } ?? ""

        let isDirty = reportWeightText != activeInitialWeight ||
            reportValueText != activeInitialValue ||
            intensityText != initialIntensityForDraft ||
            loadMode != initialLoadMode ||
            reachedFailure != initialFailure ||
            partialRepsTotal != (initialDraft?.partialReps ?? 0) ||
            (supportsIndependentSides && selectedSide != initialSide) ||
            romValue != (initialDraft?.rom ?? sessionCompletedSet?.rom) ||
            assistedRepsValue != (initialDraft?.ASSISTEDReps ?? 0)

        onDraftChange(
            WorkoutSetDraft(
                weightText: reportWeightText,
                valueText: reportValueText,
                intensityText: intensityText,
                loadMode: loadMode,
                selectedSide: supportsIndependentSides ? selectedSide : nil,
                partialReps: partialRepsTotal > 0 ? partialRepsTotal : nil,
                reachedFailure: reachedFailure,
                isDirty: isDirty,
                rom: romValue,
                assistedReps: assistedRepsValue > 0 ? assistedRepsValue : nil
            ),
            supportsIndependentSides ? selectedSide : nil
        )
    }

    // MARK: - Build advanced feedback

    private func buildAdvancedFeedback() -> SetAdvancedFeedback {
        let partialRepsTotal = showPartialsMode ? max(partialSets.reduce(0, +), 0) : 0
        return SetAdvancedFeedback(
            rir: isAmrap && !amrapReachFailure ? amrapReserveReps
                : reportedIntensityMode == .RIR ? Int(intensityText)
                : nil,
            reachedFailure: reachedFailure || (isAmrap && amrapReachFailure),
            isFailedSet: isFailedSet,
            failureReason: isFailedSet ? "Serie marcada como fallida" : nil,
            isPartial: partialRepsTotal > 0,
            partialReps: partialRepsTotal > 0 ? partialRepsTotal : nil,
            dropSets: dropSetEnabled
                ? dropSets.filter { $0.weight > 0 && $0.REPS > 0 }.map { DropSetData(weight: $0.weight, reps: $0.REPS) }
                : [],
            restPauses: restPauseEnabled
                ? restPauseSets.filter { $0.REPS > 0 }.map { RestPauseData(restTime: max($0.restTime, 0), reps: $0.REPS) }
                : [],
            isWarmup: false,
            actualIntensityMode: isAmrap && amrapReachFailure ? .FAILURE
                : isAmrap ? .AMRAP
                : reachedFailure ? .FAILURE
                : reportedIntensityMode,
            actualIntensityValue: isExecutionError ? nil
                : isAmrap && amrapReachFailure ? 10.0
                : isAmrap && !amrapReachFailure ? amrapReserveReps.map(Double.init)
                : reachedFailure ? 10.0
                : Double(intensityText),
            timerElapsedSeconds: isTimeMode && timerElapsedSeconds > 0 ? timerElapsedSeconds : Int(valueText),
            timerTargetSeconds: isTimeMode ? basePlannedTarget : nil,
            rom: romValue,
            assistedReps: assistedRepsValue > 0 ? assistedRepsValue : nil
        )
    }

    // MARK: - Record action

    private func updateRecordClosure() {
        guard isActivePage else { recordActionBox.action = nil; return }
        recordActionBox.action = { [self] in
            guard loadMode != .ASSISTED || !bodyWeightText.isBlankOrEmpty else { return }
            let reportingSide = supportsIndependentSides ? selectedSide : nil
            let reportedWeightText = reportingSide.map { $0 == "left" ? leftWeightText : rightWeightText } ?? weightText
            let reportedValueText = reportingSide.map { $0 == "left" ? leftValueText : rightValueText } ?? valueText
            let weight = loadMode == .BODYWEIGHT ? 0.0 : (Double(reportedWeightText) ?? 0.0)
            let typedValue = isFailedSet ? 0.0 : (Double(reportedValueText) ?? 0.0)

            let intensity: Double? = {
                if isFailedSet { return nil }
                if isAmrap && amrapReachFailure { return 10.0 }
                if isAmrap && !amrapReachFailure { return amrapReserveReps.map(Double.init) }
                if reachedFailure { return 10.0 }
                return Double(intensityText)
            }()

            let resolvedUnitMode: UnitModeV2 = {
                if let u = currentSet.unitModeV2 { return u }
                if exercise.trainingMode == .DISTANCE { return .DISTANCE }
                if currentSet.targetDuration != nil { return .TIME }
                return .REPS
            }()

            let resolvedBodyWeight = Double(bodyWeightText)
            let minimumValue = isAmrap ? (basePlannedTarget.map(Double.init) ?? 0.0) : 0.0
            let value = max(typedValue, minimumValue)

            onRecord(
                loadMode,
                resolvedUnitMode,
                weight,
                value,
                intensity,
                buildAdvancedFeedback(),
                isAmrap,
                resolvedBodyWeight,
                reportingSide
            )
            if supportsIndependentSides && !sideLocked {
                selectSide(selectedSide == "left" ? "right" : "left")
            }
        }
    }

    // MARK: - View Components

    private var sideSelectorView: some View {
        HStack(spacing: 6) {
            ForEach([("left", "Izquierda (L)"), ("right", "Derecha (R)")], id: \.0) { side, label in
                let isSel = selectedSide == side
                Button(action: { selectSide(side) }) {
                    HStack(spacing: 4) {
                        Image(systemName: side == "left" ? "chevron.left" : "chevron.right")
                            .font(.system(size: 12))
                        Text(label)
                            .font(.caption.weight(.bold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(isSel ? sessionAccentColor.opacity(0.15) : Color.clear)
                    .foregroundColor(isSel ? sessionAccentColor : Color(.label).opacity(0.6))
                    .clipShape(WorkoutUiTokens.innerCardShape)
                    .overlay(
                        WorkoutUiTokens.innerCardShape.stroke(
                            isSel ? sessionAccentColor : Color(.systemGray3).opacity(0.2),
                            lineWidth: 1
                        )
                    )
                }
            }
        }
        .padding(6)
        .background(WorkoutUiTokens.setInnerColor())
        .clipShape(WorkoutUiTokens.innerCardShape)
    }

    @ViewBuilder
    private func historyRow(ghostSet: CompletedSet?, supportsIndependentSides: Bool, onShowHistory: @escaping () -> Void) -> some View {
        if !supportsIndependentSides, let ghost = ghostSet, (ghost.weight > 0 || ghost.REPS > 0) {
            Button(action: onShowHistory) {
                HStack(spacing: 6) {
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 10))
                        .foregroundColor(Color(red: 0.27, green: 0.53, blue: 1.0))
                    var text = "Última "
                    if ghost.weight > 0 { text += "\(ghost.weight.toTrimmedNumberString())kg" }
                    if ghost.weight > 0 && ghost.REPS > 0 { text += " · " }
                    if ghost.REPS > 0 { text += "\(ghost.REPS)" }
                    Text(text)
                        .font(.caption.weight(.semibold))
                        .foregroundColor(Color(red: 0.27, green: 0.53, blue: 1.0))
                }
            }
        } else {
            Spacer().frame(width: 1)
        }
    }

    @ViewBuilder
    private func readinessChipView(readiness: ExerciseReadiness) -> some View {
        let score = readiness.overallScore
        let chipColor: Color = {
            if score >= 75 { return Color(red: 0.30, green: 0.78, blue: 0.31) }
            if score >= 50 { return Color(red: 1.0, green: 0.76, blue: 0.03) }
            return Color(red: 1.0, green: 0.32, blue: 0.32)
        }()
        HStack(spacing: 5) {
            Circle()
                .fill(chipColor)
                .frame(width: 6, height: 6)
            Text("Mi estado: \(score)%")
                .font(.system(size: 11, weight: .black))
                .foregroundColor(.white.opacity(0.9))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(chipColor.opacity(0.15))
        .clipShape(Capsule())
    }

    @ViewBuilder
    private func plannedInfoSection(
        isFailedSet: Bool,
        sessionAccentColor: Color,
        onGoToPrevSet: (() -> Void)?,
        onGoToNextSet: (() -> Void)?,
        plannedValueLabel: String,
        basePlannedTarget: Int?,
        isAmrap: Bool,
        isTimeMode: Bool,
        targetDelta: Double?,
        plannedIntensityDisplayLabel: String,
        plannedIntensityDisplayValue: String,
        plannedIntensityMode: IntensityMode,
        reachedFailure: Bool,
        difficultyLabel: String?,
        intensityDelta: Double?,
        isNarrowScreen: Bool,
        currentSet: ExerciseSet,
        lastHomologatedResultV3: HomologatedPerformanceResult?,
        ghostSet: CompletedSet?,
        exerciseReadiness: ExerciseReadiness?,
        readinessAdjustment: SetAdjustmentSuggestion?,
        onApplyReadinessAdjustment: ((SetAdjustmentSuggestion) -> Void)?
    ) -> some View {
        VStack(spacing: 8) {
            // "Planificado" header and navigation
            HStack {
                Text("Planificado")
                    .font(.caption.weight(.bold))
                    .foregroundColor(isFailedSet ? .red : sessionAccentColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(isFailedSet ? Color.red.opacity(0.15) : sessionAccentColor.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 4))

                Spacer()

                if onGoToPrevSet != nil || onGoToNextSet != nil {
                    HStack(spacing: 6) {
                        if let prev = onGoToPrevSet {
                            Button(action: prev) {
                                Image(systemName: "arrow.left")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.78))
                            }
                            .frame(width: 28, height: 28)
                        }
                        if let next = onGoToNextSet {
                            Button(action: next) {
                                Image(systemName: "arrow.right")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.78))
                            }
                            .frame(width: 28, height: 28)
                        }
                    }
                }
            }

            // Metric chips
            HStack(spacing: 8) {
                let significantDelta = targetDelta.flatMap { $0 != 0.0 ? $0 : nil }
                let badgeText = significantDelta.map { isTimeMode ? formatSignedDelta($0, suffix: "s") : formatSignedDelta($0) }
                let badgeColor = (significantDelta.map { $0 < 0.0 } ?? false) ? Color(red: 1.0, green: 0.32, blue: 0.32) : Color(.label).opacity(0.6)

                WorkoutMetricChip(
                    label: plannedValueLabel,
                    value: {
                        if basePlannedTarget == nil { return isAmrap ? "Libre" : "-" }
                        if isTimeMode { return "\(basePlannedTarget!)s" }
                        return "\(basePlannedTarget!)"
                    }(),
                    badgeText: badgeText,
                    badgeColor: badgeColor,
                    containerColor: WorkoutUiTokens.setInnerHighestColor()
                )

                let intensityContainerColor: Color = {
                    if plannedIntensityMode == .FAILURE && reachedFailure { return Color(red: 0.29, green: 0.0, blue: 0.0) }
                    if isAmrap { return Color(red: 0.23, green: 0.0, blue: 0.23) }
                    if difficultyLabel == "Más difícil" || difficultyLabel == "Serie fallida" { return Color(red: 0.29, green: 0.0, blue: 0.0) }
                    if difficultyLabel == "Más fácil" { return Color(red: 0.0, green: 0.23, blue: 0.0) }
                    if difficultyLabel == "Fallo alcanzado" { return Color(red: 0.29, green: 0.23, blue: 0.0) }
                    return WorkoutUiTokens.setInnerHighestColor()
                }()

                let intensityBadgeText: String? = {
                    if plannedIntensityMode == .FAILURE && reachedFailure { return nil }
                    return intensityDelta.flatMap { $0 != 0.0 ? formatSignedDelta($0) : nil }
                }()
                let intensityBadgeColor: Color = (intensityDelta.map { $0 > 0.0 } ?? false) ? Color(red: 1.0, green: 0.32, blue: 0.32) : Color(.label).opacity(0.6)

                WorkoutMetricChip(
                    label: plannedIntensityDisplayLabel,
                    value: (plannedIntensityMode == .FAILURE && reachedFailure) ? (isNarrowScreen ? "F" : "FALLO") : plannedIntensityDisplayValue,
                    badgeText: intensityBadgeText,
                    badgeColor: intensityBadgeColor,
                    containerColor: intensityContainerColor
                )
            }

            // 1RM estimation
            if currentSet.targetPercentageRM != nil {
                let rm1: Double? = lastHomologatedResultV3?.estimatedRm ?? ghostSet.flatMap { ghost in
                    if ghost.weight > 0 && ghost.REPS > 0 && ghost.REPS < 37 {
                        return ghost.weight / (1.0278 - 0.0278 * Double(ghost.REPS))
                    }
                    return nil
                }
                if let rm = rm1 {
                    Text("1RM estimado: ~\(rm.toTrimmedNumberString())kg")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(Color(.label).opacity(0.6))
                        .frame(maxWidth: .infinity)
                        .multilineTextAlignment(.center)
                }
            }

            // Readiness adjustment
            if let readiness = exerciseReadiness, onApplyReadinessAdjustment != nil {
                if readinessAdjustment == nil {
                    let isRecommended = readiness.overallScore < ExerciseReadinessEngine.adjustmentThreshold
                    Button(action: { showReadinessAdjustmentSheet = true }) {
                        HStack(spacing: 6) {
                            Image(systemName: "slider.horizontal.3")
                                .font(.system(size: 10))
                            Text(isRecommended ? "Adaptar según cómo me siento (Recomendado)" : "Adaptar según cómo me siento")
                                .font(.caption.weight(.bold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.bordered)
                    .tint(isRecommended ? Color(red: 1.0, green: 0.32, blue: 0.32) : sessionAccentColor)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(
                                isRecommended ? Color(red: 1.0, green: 0.32, blue: 0.32).opacity(0.5) : Color(.systemGray3).opacity(0.5),
                                lineWidth: 1
                            )
                    )
                } else {
                    HStack(spacing: 8) {
                        Button(action: { showReadinessAdjustmentSheet = true }) {
                            HStack(spacing: 4) {
                                Image(systemName: "pencil")
                                    .font(.system(size: 9))
                                Text("Ajustar de nuevo")
                                    .font(.caption.weight(.bold))
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.plain)
                        .foregroundColor(sessionAccentColor)

                        Button(action: {
                            let emptySuggestion = SetAdjustmentSuggestion(
                                exerciseId: exercise.id,
                                setIndex: setIndex,
                                currentPlannedWeight: currentSet.weight ?? 0.0,
                                readinessScore: readiness.overallScore,
                                severityFactor: 0.0,
                                reductionPercent: 0.0,
                                suggestedWeight: currentSet.weight ?? 0.0,
                                averageErm: nil,
                                reason: "Reset manual",
                                suggestedLoadMode: currentSet.LOADModeV2 ?? .LOAD
                            )
                            onApplyReadinessAdjustment?(emptySuggestion)
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "trash")
                                    .font(.system(size: 9))
                                Text("Restablecer original")
                                    .font(.caption.weight(.bold))
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.plain)
                        .foregroundColor(.red)
                    }
                }
            }

            // Adjustment indicator
            if let adj = readinessAdjustment, (adj.reductionPercent > 0.0 || adj.suggestedLoadMode != (currentSet.LOADModeV2 ?? .LOAD)) {
                let plannedMode = currentSet.LOADModeV2 ?? .LOAD
                let adjustmentText: String = {
                    if adj.suggestedLoadMode == .ASSISTED && plannedMode != .ASSISTED {
                        return "Adaptado: +\(Int(adj.suggestedWeight))kg Asistencia"
                    }
                    if adj.suggestedLoadMode == .BODYWEIGHT && plannedMode != .BODYWEIGHT {
                        return "Adaptado: Usar Peso Corporal"
                    }
                    if plannedMode == .ASSISTED {
                        return "Adaptado: +\(Int(adj.suggestedWeight))kg Asistencia"
                    }
                    return "Adaptado −\(Int(adj.reductionPercent * 100))%"
                }()
                HStack(spacing: 6) {
                    Image(systemName: "info.circle.fill")
                        .font(.system(size: 10))
                        .foregroundColor(Color(red: 0.30, green: 0.78, blue: 0.31))
                    Text(adjustmentText)
                        .font(.caption.weight(.semibold))
                        .foregroundColor(Color(red: 0.30, green: 0.78, blue: 0.31))
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(red: 0.106, green: 0.227, blue: 0.106))
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .background(isFailedSet ? WorkoutUiTokens.dangerContainerColor().opacity(0.15) : WorkoutUiTokens.setInnerColor())
        .clipShape(WorkoutUiTokens.innerCardShape)
    }

    @ViewBuilder
    private func reportSection(
        isFailedSet: Bool,
        sessionAccentColor: Color,
        supportsIndependentSides: Bool,
        selectedSide: String,
        reportWeightText: String,
        reportValueText: String,
        loadFieldLabel: String,
        loadMode: LoadModeV2,
        ghostSuggestedWeightText: String?,
        weightSuggestion: WeightSuggestion?,
        exercise: Exercise,
        currentSet: ExerciseSet,
        isTimeMode: Bool,
        isExecutionError: Bool,
        reachedFailure: Bool,
        roomyStepper: Bool,
        isNarrowScreen: Bool,
        intensityFieldLabel: String,
        intensityText: String,
        exerciseReadiness: ExerciseReadiness?,
        readinessAdjustment: SetAdjustmentSuggestion?,
        showBodyWeightPrompt: Bool,
        bodyWeightText: String,
        timerRunning: Bool,
        timerRemainingSeconds: Int,
        timerElapsedSeconds: Int,
        timerTargetSeconds: Int,
        plannedTarget: Int?,
        amrapCalibrationMessage: String?
    ) -> some View {
        VStack(spacing: 10) {
            // "Reportar" header
            Text(supportsIndependentSides
                 ? "Reportar \(selectedSide == "left" ? "L lado izq." : "R lado der.")"
                 : "Reportar serie")
                .font(.caption.weight(.bold))
                .foregroundColor(sessionAccentColor)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(sessionAccentColor.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .frame(maxWidth: .infinity, alignment: .leading)

            // Integrated load input + dropdown
            VStack(spacing: 0) {
                IntegratedLoadInput(
                    value: reportWeightText,
                    onValueChange: { updateActiveWeightText($0) },
                    label: loadFieldLabel,
                    placeholder: {
                        if !reportWeightText.isBlankOrEmpty { return nil }
                        switch loadMode {
                        case .BODYWEIGHT: return "Sin carga externa"
                        case .LASTRE: return ghostSuggestedWeightText.map { "\($0) sugerido" } ?? "Ej: 10"
                        case .ASSISTED: return ghostSuggestedWeightText.map { "\($0) sugerido" } ?? "Ej: 20"
                        default: return ghostSuggestedWeightText.map { "\($0) sugerido" }
                        }
                    }(),
                    options: quickLoadOptionsFor(
                        currentWeightText: reportWeightText,
                        suggestedWeight: weightSuggestion?.suggestedWeight,
                        loadIncrementKg: quickLoadIncrementFor(exercise: exercise, currentSet: currentSet)
                    ),
                    onWeightSelected: { updateActiveWeightText($0) },
                    onOpenLoadMode: { loadModeMenuExpanded = true },
                    accentColor: sessionAccentColor,
                    loadMode: loadMode
                )

                if loadModeMenuExpanded {
                    VStack(spacing: 0) {
                        ForEach([
                            (LoadModeV2.LOAD, "Carga"),
                            (.BODYWEIGHT, "Peso corporal"),
                            (.LASTRE, "Lastre"),
                            (.ASSISTED, "Asistido")
                        ], id: \.0.rawValue) { mode, label in
                            Button(action: {
                                loadMode = mode
                                if mode == .BODYWEIGHT {
                                    updateActiveWeightText("")
                                    if bodyWeightText.isBlankOrEmpty { showBodyWeightPrompt = true }
                                } else if mode == .LASTRE || mode == .ASSISTED {
                                    if bodyWeightText.isBlankOrEmpty { showBodyWeightPrompt = true }
                                }
                                loadModeMenuExpanded = false
                            }) {
                                Text(label)
                                    .font(.body)
                                    .padding(.vertical, 8)
                                    .padding(.horizontal, 16)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            Divider()
                        }
                    }
                    .background(Color(.systemGray6))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }

            // Lastre warning
            if loadMode == .LASTRE && (reportWeightText.isBlankOrEmpty || (Double(reportWeightText) ?? 0.0) == 0.0) {
                Text("Lastre = 0 → se registra como peso corporal")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(Color(red: 1.0, green: 0.73, blue: 0.30))
                    .padding(.leading, 4)
            }
            if loadMode == .ASSISTED && bodyWeightText.isBlankOrEmpty {
                Text("Ingresa tu peso corporal para calcular la asistencia")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.red)
                    .padding(.leading, 4)
            }

            // Value + Intensity steppers
            HStack(spacing: roomyStepper ? 18 : 12) {
                valueStepperBlock(
                    isFailedSet: isFailedSet,
                    isTimeMode: isTimeMode,
                    reportValueText: reportValueText,
                    roomyStepper: roomyStepper,
                    sessionAccentColor: sessionAccentColor,
                    debt: debt,
                    assistedRepsValue: assistedRepsValue,
                    timerRunning: timerRunning,
                    timerTargetSeconds: timerTargetSeconds,
                    timerRemainingSeconds: timerRemainingSeconds,
                    timerElapsedSeconds: timerElapsedSeconds
                )

                intensityStepperBlock(
                    isExecutionError: isExecutionError,
                    reachedFailure: reachedFailure,
                    isNarrowScreen: isNarrowScreen,
                    intensityFieldLabel: intensityFieldLabel,
                    intensityText: intensityText,
                    roomyStepper: roomyStepper,
                    sessionAccentColor: sessionAccentColor
                )
            }

            // ROM slider
            if exercise.trackRom {
                romSliderView
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .background(WorkoutUiTokens.setInnerColor())
        .clipShape(WorkoutUiTokens.innerCardShape)
    }

    // MARK: - Value Stepper Block

    @ViewBuilder
    private func valueStepperBlock(
        isFailedSet: Bool,
        isTimeMode: Bool,
        reportValueText: String,
        roomyStepper: Bool,
        sessionAccentColor: Color,
        debt: Double,
        assistedRepsValue: Int,
        timerRunning: Bool,
        timerTargetSeconds: Int,
        timerRemainingSeconds: Int,
        timerElapsedSeconds: Int
    ) -> some View {
        VStack(spacing: 0) {
            Text((isTimeMode ? "Tiempo" : "Reps").uppercased())
                .font(.caption.weight(.bold))
                .foregroundColor(Color(.label).opacity(0.6))
            Spacer().frame(height: roomyStepper ? 8 : 6)

            WorkoutStepperField(
                value: isFailedSet ? "0" : reportValueText,
                onValueChange: { if !isFailedSet { updateActiveValueText($0.filter { $0.isNumber }) } },
                onDecrement: {
                    let current = Int(reportValueText) ?? 0
                    updateActiveValueText("\(max(current - 1, 0))")
                },
                onIncrement: {
                    let current = Int(reportValueText) ?? 0
                    updateActiveValueText("\(current + 1)")
                },
                buttonsEnabled: !isFailedSet,
                textInputEnabled: !isFailedSet,
                isError: isFailedSet || (debt > 0 && !isTimeMode),
                accentColor: sessionAccentColor,
                roomier: roomyStepper
            )

            if !isTimeMode {
                Spacer().frame(height: roomyStepper ? 6 : 4)
                HStack(spacing: 0) {
                    Button(action: { assistedRepsValue = max(assistedRepsValue - 1, 0) }) {
                        Image(systemName: "minus")
                            .font(.system(size: 8))
                            .foregroundColor(Color(.label).opacity(0.5))
                    }
                    .frame(width: 24, height: 24)

                    let maxReps = Int(reportValueText) ?? 0
                    if assistedRepsValue > maxReps { assistedRepsValue = maxReps }

                    Text(assistedRepsValue > 0 ? "\(assistedRepsValue) con ayuda" : "Sin ayuda")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(assistedRepsValue > 0 ? sessionAccentColor : Color(.label).opacity(0.5))
                        .padding(.horizontal, 2)

                    Button(action: {
                        let limit = Int(reportValueText) ?? 0
                        assistedRepsValue = min(assistedRepsValue + 1, limit)
                    }) {
                        Image(systemName: "plus")
                            .font(.system(size: 8))
                            .foregroundColor(sessionAccentColor)
                    }
                    .frame(width: 24, height: 24)
                }
            }

            if isTimeMode {
                Spacer().frame(height: roomyStepper ? 8 : 6)
                Button(action: {
                    if timerRunning {
                        timerRunning = false
                        if timerElapsedSeconds > 0 {
                            updateActiveValueText("\(timerElapsedSeconds)")
                        }
                    } else if timerTargetSeconds > 0 {
                        timerElapsedSeconds = 0
                        timerRemainingSeconds = timerTargetSeconds
                        timerRunning = true
                    }
                }) {
                    Image(systemName: timerRunning ? "stop.fill" : "play.fill")
                        .font(.system(size: 14))
                        .foregroundColor(timerRunning ? sessionAccentColor : Color(.label).opacity(0.5))
                }
                .frame(width: 32, height: 32)
            }
        }
    }

    // MARK: - Intensity Stepper Block

    @ViewBuilder
    private func intensityStepperBlock(
        isExecutionError: Bool,
        reachedFailure: Bool,
        isNarrowScreen: Bool,
        intensityFieldLabel: String,
        intensityText: String,
        roomyStepper: Bool,
        sessionAccentColor: Color
    ) -> some View {
        VStack(spacing: 0) {
            Text(intensityFieldLabel.uppercased())
                .font(.caption.weight(.bold))
                .foregroundColor(
                    isExecutionError || reachedFailure
                        ? Color(.label).opacity(0.4)
                        : Color(.label).opacity(0.6)
                )
            Spacer().frame(height: roomyStepper ? 8 : 6)

            let intensityDisabled = isExecutionError
            WorkoutStepperField(
                value: isExecutionError ? (isNarrowScreen ? "ERR" : "ERROR")
                    : reachedFailure ? (isNarrowScreen ? "F" : "FALLO")
                    : intensityText,
                onValueChange: { if !intensityDisabled && !reachedFailure { intensityText = $0 } },
                onDecrement: { decreaseIntensityInput() },
                onIncrement: { increaseIntensityInput() },
                buttonsEnabled: !intensityDisabled,
                textInputEnabled: !intensityDisabled && !reachedFailure,
                isError: isExecutionError || reachedFailure,
                accentColor: sessionAccentColor,
                roomier: roomyStepper
            )
        }
    }

    // MARK: - ROM Slider View

    private var romSliderView: some View {
        VStack(spacing: 4) {
            HStack {
                Text("Rango de Movimiento (ROM)")
                    .font(.caption.weight(.bold))
                    .foregroundColor(.white.opacity(0.6))
                Spacer()
                Text("\(romValue ?? 100)%")
                    .font(.caption.weight(.bold))
                    .foregroundColor(sessionAccentColor)
            }
            Slider(value: Binding(
                get: { Float(romValue ?? 100) },
                set: { romValue = Int($0.rounded()) }
            ), in: 10...100, step: 5)
                .tint(sessionAccentColor)

            let romText: String = {
                switch romValue ?? 100 {
                case 100: return "ROM Completo (máximo estímulo y estiramiento)"
                case 80...99: return "ROM Casi Completo (buen estímulo mecánico)"
                case 50...79: return "ROM Parcial (estímulo reducido o específico)"
                default: return "ROM Muy Corto (parciales acotadas)"
                }
            }()
            Text(romText)
                .font(.system(size: 11).italic())
                .foregroundColor(.white.opacity(0.5))
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color.white.opacity(0.02))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.06), lineWidth: 1))
    }

    // MARK: - Body Weight Prompt

    private var bodyWeightPromptView: some View {
        HStack(spacing: 10) {
            TextField("Peso corporal (kg)", text: $bodyWeightText)
                .keyboardType(.decimalPad)
                .font(.body.weight(.bold))
                .foregroundColor(.white)
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .background(Color(red: 0.165, green: 0.165, blue: 0.165))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(white: 0.33), lineWidth: 1))

            Button(action: {
                if let w = Double(bodyWeightText) {
                    onSetBodyWeight(w)
                    showBodyWeightPrompt = false
                }
            }) {
                Text("Guardar")
                    .fontWeight(.bold)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(sessionAccentColor)
                    .foregroundColor(.black)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .background(Color(red: 0.133, green: 0.133, blue: 0.133))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - Timer Support

    @ViewBuilder
    private func timerSupportView(
        timerRunning: Bool,
        timerRemainingSeconds: Int,
        timerElapsedSeconds: Int,
        plannedTarget: Int?,
        sessionAccentColor: Color
    ) -> some View {
        let timerSupport: String? = {
            if timerRunning { return "Restan \(formatTime(seconds: timerRemainingSeconds))" }
            if timerElapsedSeconds > 0 { return "Registrado \(timerElapsedSeconds)s" }
            if let target = plannedTarget { return "Objetivo \(target)s" }
            return nil
        }()
        if let text = timerSupport {
            Text(text)
                .font(.caption.weight(.semibold))
                .foregroundColor(timerRunning ? sessionAccentColor : .white.opacity(0.6))
        }
    }

    // MARK: - AMRAP Calibration

    @ViewBuilder
    private func amrapCalibrationView(msg: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "chart.bar.fill")
                .font(.system(size: 14))
                .foregroundColor(Color(red: 0.30, green: 0.78, blue: 0.31))
            Text(msg)
                .font(.caption.weight(.bold))
                .foregroundColor(Color(red: 0.30, green: 0.78, blue: 0.31))
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(red: 0.102, green: 0.227, blue: 0.102))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - Adjustments Tab

    @ViewBuilder
    private func adjustmentsTabView(
        adjustmentsTab: Binding<Int>,
        sessionAccentColor: Color,
        isFailedSet: Bool,
        isAmrap: Bool,
        plannedTarget: Int?,
        isTimeMode: Bool,
        showPartialsMode: Bool,
        dropSetEnabled: Bool,
        restPauseEnabled: Bool,
        partialSets: Binding<[Int]>,
        dropSets: Binding<[DropSetEntry]>,
        restPauseSets: Binding<[RestPauseData]>,
        sessionAccentColor: Color
    ) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 4) {
                ForEach(["Cambio de planes", "Técnicas de intensidad"].indices, id: \.self) { index in
                    let title = ["Cambio de planes", "Técnicas de intensidad"][index]
                    Button(action: {
                        adjustmentsTab.wrappedValue = adjustmentsTab.wrappedValue == index ? -1 : index
                    }) {
                        Text(title)
                            .font(.caption)
                            .fontWeight(adjustmentsTab.wrappedValue == index ? .semibold : .regular)
                            .foregroundColor(adjustmentsTab.wrappedValue == index ? sessionAccentColor : Color(.label).opacity(0.55))
                            .lineLimit(2)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(adjustmentsTab.wrappedValue == index ? sessionAccentColor.opacity(0.15) : Color.clear)
                            .clipShape(WorkoutUiTokens.innerCardShape)
                            .overlay(
                                WorkoutUiTokens.innerCardShape.stroke(
                                    adjustmentsTab.wrappedValue == index ? sessionAccentColor : Color(.systemGray3).opacity(0.3),
                                    lineWidth: 1
                                )
                            )
                    }
                }
            }

            if adjustmentsTab.wrappedValue >= 0 {
                VStack(spacing: 8) {
                    if adjustmentsTab.wrappedValue == 0 {
                        // Cambio de planes
                        planChangesTab(
                            isFailedSet: isFailedSet,
                            isAmrap: isAmrap,
                            plannedTarget: plannedTarget,
                            isTimeMode: isTimeMode,
                            sessionAccentColor: sessionAccentColor
                        )
                    } else if adjustmentsTab.wrappedValue == 1 {
                        // Técnicas de intensidad
                        intensityTechniquesTab(
                            showPartialsMode: showPartialsMode,
                            dropSetEnabled: dropSetEnabled,
                            restPauseEnabled: restPauseEnabled,
                            partialSets: partialSets,
                            dropSets: dropSets,
                            restPauseSets: restPauseSets,
                            sessionAccentColor: sessionAccentColor
                        )
                    }
                }
                .padding(12)
                .background(WorkoutUiTokens.setInnerColor())
                .clipShape(WorkoutUiTokens.innerCardShape)
                .overlay(WorkoutUiTokens.innerCardShape.stroke(Color(.systemGray3).opacity(0.15), lineWidth: 1))
            }
        }
    }

    @ViewBuilder
    private func planChangesTab(
        isFailedSet: Bool,
        isAmrap: Bool,
        plannedTarget: Int?,
        isTimeMode: Bool,
        sessionAccentColor: Color
    ) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Button(action: { self.isFailedSet.toggle(); if self.isFailedSet { reachedFailure = false } }) {
                    Text("Error de ejecución")
                        .font(.caption2)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(self.isFailedSet ? Color.red.opacity(0.2) : WorkoutUiTokens.setInnerHighestColor())
                        .foregroundColor(self.isFailedSet ? .red : Color(.label))
                        .clipShape(Capsule())
                }

                Button(action: { if self.isAmrap { self.isAmrap = false } else { showAmrapSheet = true } }) {
                    Text("AMRAP")
                        .font(.caption2)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(self.isAmrap ? sessionAccentColor.opacity(0.2) : WorkoutUiTokens.setInnerHighestColor())
                        .foregroundColor(self.isAmrap ? sessionAccentColor : Color(.label))
                        .clipShape(Capsule())
                }
            }

            if isAmrap, let target = plannedTarget {
                Text("AMRAP mínimo: \(target) \(isTimeMode ? "s" : "reps")")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(sessionAccentColor)
            }
        }
    }

    @ViewBuilder
    private func intensityTechniquesTab(
        showPartialsMode: Bool,
        dropSetEnabled: Bool,
        restPauseEnabled: Bool,
        partialSets: Binding<[Int]>,
        dropSets: Binding<[DropSetEntry]>,
        restPauseSets: Binding<[RestPauseData]>,
        sessionAccentColor: Color
    ) -> some View {
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                Button(action: { self.showPartialsMode.toggle(); if self.showPartialsMode && self.partialSets.isEmpty { self.partialSets = [0] } }) {
                    Text("Parciales")
                        .font(.caption2)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(self.showPartialsMode ? sessionAccentColor.opacity(0.2) : WorkoutUiTokens.setInnerHighestColor())
                        .foregroundColor(self.showPartialsMode ? sessionAccentColor : Color(.label))
                        .clipShape(Capsule())
                }

                Button(action: { dropSetEnabled.toggle() }) {
                    Text("Drop-set")
                        .font(.caption2)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(dropSetEnabled ? sessionAccentColor.opacity(0.2) : WorkoutUiTokens.setInnerHighestColor())
                        .foregroundColor(dropSetEnabled ? sessionAccentColor : Color(.label))
                        .clipShape(Capsule())
                }

                Button(action: { restPauseEnabled.toggle() }) {
                    Text("Rest-Pause")
                        .font(.caption2)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(restPauseEnabled ? sessionAccentColor.opacity(0.2) : WorkoutUiTokens.setInnerHighestColor())
                        .foregroundColor(restPauseEnabled ? sessionAccentColor : Color(.label))
                        .clipShape(Capsule())
                }
            }

            if showPartialsMode {
                partialsEditor(partialSets: partialSets, sessionAccentColor: sessionAccentColor)
            }

            if dropSetEnabled {
                dropSetEditor(dropSets: dropSets, sessionAccentColor: sessionAccentColor)
            }

            if restPauseEnabled {
                restPauseEditor(restPauseSets: restPauseSets, sessionAccentColor: sessionAccentColor)
            }
        }
    }

    @ViewBuilder
    private func partialsEditor(partialSets: Binding<[Int]>, sessionAccentColor: Color) -> some View {
        VStack(spacing: 4) {
            ForEach(partialSets.wrappedValue.indices, id: \.self) { idx in
                HStack(spacing: 6) {
                    Text("Parcial \(idx + 1)")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(Color(.label).opacity(0.7))
                        .frame(minWidth: 56, alignment: .leading)

                    Button(action: {
                        var arr = partialSets.wrappedValue
                        arr[idx] = max(arr[idx] - 1, 0)
                        partialSets.wrappedValue = arr
                    }) {
                        Image(systemName: "minus")
                            .font(.system(size: 10))
                            .foregroundColor(Color(.label).opacity(0.7))
                    }
                    .frame(width: 36, height: 36)

                    Text("\(partialSets.wrappedValue[idx]) reps")
                        .font(.caption.weight(.bold))
                        .frame(minWidth: 48)
                        .multilineTextAlignment(.center)

                    Button(action: {
                        var arr = partialSets.wrappedValue
                        arr[idx] = min(arr[idx] + 1, 20)
                        partialSets.wrappedValue = arr
                    }) {
                        Image(systemName: "plus")
                            .font(.system(size: 10))
                            .foregroundColor(sessionAccentColor)
                    }
                    .frame(width: 36, height: 36)

                    if partialSets.wrappedValue.count > 1 {
                        Button(action: {
                            var arr = partialSets.wrappedValue
                            arr.remove(at: idx)
                            partialSets.wrappedValue = arr
                        }) {
                            Image(systemName: "trash")
                                .font(.system(size: 10))
                                .foregroundColor(.red)
                        }
                        .frame(width: 36, height: 36)
                    }
                }
            }

            Button(action: { partialSets.wrappedValue.append(0) }) {
                HStack(spacing: 4) {
                    Image(systemName: "plus")
                        .font(.system(size: 10))
                    Text("Agregar parcial")
                        .font(.caption.weight(.bold))
                }
            }
            .buttonStyle(.plain)
            .foregroundColor(sessionAccentColor)
        }
    }

    @ViewBuilder
    private func dropSetEditor(dropSets: Binding<[DropSetEntry]>, sessionAccentColor: Color) -> some View {
        VStack(spacing: 4) {
            ForEach(dropSets.wrappedValue.indices, id: \.self) { idx in
                HStack(spacing: 6) {
                    WorkoutMiniTextField(
                        value: dropSets.wrappedValue[idx].weight == 0.0 ? "" : dropSets.wrappedValue[idx].weight.toTrimmedNumberString(),
                        onValueChange: { v in
                            var arr = dropSets.wrappedValue
                            arr[idx] = DropSetEntry(weight: Double(v) ?? 0.0, reps: arr[idx].REPS)
                            dropSets.wrappedValue = arr
                        },
                        label: "Peso",
                        accentColor: sessionAccentColor,
                        keyboardType: .decimalPad
                    )

                    WorkoutMiniTextField(
                        value: dropSets.wrappedValue[idx].REPS == 0 ? "" : "\(dropSets.wrappedValue[idx].REPS)",
                        onValueChange: { v in
                            var arr = dropSets.wrappedValue
                            arr[idx] = DropSetEntry(weight: arr[idx].weight, reps: Int(v) ?? 0)
                            dropSets.wrappedValue = arr
                        },
                        label: "Reps",
                        accentColor: sessionAccentColor,
                        keyboardType: .numberPad
                    )

                    if dropSets.wrappedValue.count > 1 {
                        Button(action: {
                            var arr = dropSets.wrappedValue
                            arr.remove(at: idx)
                            dropSets.wrappedValue = arr
                        }) {
                            Image(systemName: "trash")
                                .font(.system(size: 12))
                                .foregroundColor(.red)
                        }
                        .frame(width: 36, height: 36)
                    }
                }
            }

            Button(action: { dropSets.wrappedValue.append(DropSetEntry(weight: 0, reps: 0)) }) {
                HStack(spacing: 4) {
                    Image(systemName: "plus")
                        .font(.system(size: 10))
                    Text("Agregar drop-set")
                        .font(.caption.weight(.bold))
                }
            }
            .buttonStyle(.plain)
            .foregroundColor(sessionAccentColor)
        }
    }

    @ViewBuilder
    private func restPauseEditor(restPauseSets: Binding<[RestPauseData]>, sessionAccentColor: Color) -> some View {
        VStack(spacing: 4) {
            ForEach(restPauseSets.wrappedValue.indices, id: \.self) { idx in
                HStack(spacing: 6) {
                    WorkoutMiniTextField(
                        value: restPauseSets.wrappedValue[idx].REPS == 0 ? "" : "\(restPauseSets.wrappedValue[idx].REPS)",
                        onValueChange: { v in
                            var arr = restPauseSets.wrappedValue
                            arr[idx] = RestPauseData(restTime: arr[idx].restTime, reps: Int(v) ?? 0)
                            restPauseSets.wrappedValue = arr
                        },
                        label: "Reps/mini-set",
                        accentColor: sessionAccentColor,
                        keyboardType: .numberPad
                    )

                    WorkoutMiniTextField(
                        value: restPauseSets.wrappedValue[idx].restTime == 0 ? "" : "\(restPauseSets.wrappedValue[idx].restTime)",
                        onValueChange: { v in
                            var arr = restPauseSets.wrappedValue
                            arr[idx] = RestPauseData(restTime: Int(v) ?? 0, reps: arr[idx].REPS)
                            restPauseSets.wrappedValue = arr
                        },
                        label: "Descanso (s)",
                        accentColor: sessionAccentColor,
                        keyboardType: .numberPad
                    )

                    if restPauseSets.wrappedValue.count > 1 {
                        Button(action: {
                            var arr = restPauseSets.wrappedValue
                            arr.remove(at: idx)
                            restPauseSets.wrappedValue = arr
                        }) {
                            Image(systemName: "trash")
                                .font(.system(size: 12))
                                .foregroundColor(.red)
                        }
                        .frame(width: 36, height: 36)
                    }
                }
            }

            Button(action: { restPauseSets.wrappedValue.append(RestPauseData(restTime: 20, reps: 0)) }) {
                HStack(spacing: 4) {
                    Image(systemName: "plus")
                        .font(.system(size: 10))
                    Text("Agregar rest-pause")
                        .font(.caption.weight(.bold))
                }
            }
            .buttonStyle(.plain)
            .foregroundColor(sessionAccentColor)
        }
    }
}

// MARK: - Helper: DropSetEntry

private struct DropSetEntry {
    var weight: Double
    var reps: Int
}

// MARK: - Helper: sideTargetValueText

private func sideTargetValueText(_ target: UnilateralTarget?, isTimeMode: Bool, defaultValue: String, unitMode: UnitModeV2?) -> String {
    guard let t = target else { return defaultValue }
    if isTimeMode { return t.targetDuration.map { "\($0)" } ?? defaultValue }
    if unitMode == .DISTANCE || unitMode == .CUSTOM {
        return t.targetValue?.toTrimmedNumberString() ?? t.targetReps.map { "\($0)" } ?? defaultValue
    }
    return t.targetReps.map { "\($0)" } ?? defaultValue
}

// MARK: - String helpers

private extension String {
    var isBlankOrEmpty: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

private extension Optional where Wrapped: Collection {
    var isBlankOrEmpty: Bool { self?.isEmpty ?? true }
}

private extension String {
    func takeIf(_ condition: Bool) -> String? {
        guard condition else { return nil }
        return self
    }
}

private extension Bool {
    func takeIf<T>(_ value: @autoclosure () -> T) -> T? { self ? value() : nil }
}
