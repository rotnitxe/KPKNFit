import SwiftUI

public struct RestTimerOverlay: View {
    let state: WorkoutRestModalState
    let remainingSeconds: Int
    let pendingRestSuggestion: PendingRestSuggestion?
    let lastSetOutcome: SetOutcomeV2?
    let lastCompletedSet: CompletedSet?
    let lastCompletedSets: [(String, CompletedSet)]
    let isAdaptiveActive: Bool
    let sessionAccentColor: Color
    
    let onDecrease: () -> Void
    let onIncrease: () -> Void
    let onSkip: () -> Void
    let skipExerciseLabel: String?
    let onSkipExercise: (() -> Void)?
    let onUseAdaptive: (() -> Void)?
    let postExerciseFeedbackContent: AnyView?
    let feedbackExerciseCount: Int
    let onMinimize: (() -> Void)?
    
    public init(
        state: WorkoutRestModalState,
        remainingSeconds: Int,
        pendingRestSuggestion: PendingRestSuggestion? = nil,
        lastSetOutcome: SetOutcomeV2? = nil,
        lastCompletedSet: CompletedSet? = nil,
        lastCompletedSets: [(String, CompletedSet)] = [],
        isAdaptiveActive: Bool = false,
        sessionAccentColor: Color = .white,
        onDecrease: @escaping () -> Void,
        onIncrease: @escaping () -> Void,
        onSkip: @escaping () -> Void,
        skipExerciseLabel: String? = nil,
        onSkipExercise: (() -> Void)? = nil,
        onUseAdaptive: (() -> Void)? = nil,
        postExerciseFeedbackContent: AnyView? = nil,
        feedbackExerciseCount: Int = 0,
        onMinimize: (() -> Void)? = nil
    ) {
        self.state = state
        self.remainingSeconds = remainingSeconds
        self.pendingRestSuggestion = pendingRestSuggestion
        self.lastSetOutcome = lastSetOutcome
        self.lastCompletedSet = lastCompletedSet
        self.lastCompletedSets = lastCompletedSets
        self.isAdaptiveActive = isAdaptiveActive
        self.sessionAccentColor = sessionAccentColor
        self.onDecrease = onDecrease
        self.onIncrease = onIncrease
        self.onSkip = onSkip
        self.skipExerciseLabel = skipExerciseLabel
        self.onSkipExercise = onSkipExercise
        self.onUseAdaptive = onUseAdaptive
        self.postExerciseFeedbackContent = postExerciseFeedbackContent
        self.feedbackExerciseCount = feedbackExerciseCount
        self.onMinimize = onMinimize
    }
    
    public var body: some View {
        ZStack {
            // Glassmorphic backdrop blur
            Rectangle()
                .fill(Color.black.opacity(0.4))
                .background(.ultraThinMaterial)
                .ignoresSafeArea()
            
            VStack {
                if let postExerciseFeedbackContent = postExerciseFeedbackContent {
                    FeedbackContent(
                        state: state,
                        remainingSeconds: remainingSeconds,
                        sessionAccentColor: sessionAccentColor,
                        pendingRestSuggestion: pendingRestSuggestion,
                        isAdaptiveActive: isAdaptiveActive,
                        onDecrease: onDecrease,
                        onIncrease: onIncrease,
                        onUseAdaptive: onUseAdaptive,
                        postExerciseFeedbackContent: postExerciseFeedbackContent,
                        feedbackExerciseCount: feedbackExerciseCount
                    )
                } else {
                    NormalRestContent(
                        state: state,
                        remainingSeconds: remainingSeconds,
                        sessionAccentColor: sessionAccentColor,
                        pendingRestSuggestion: pendingRestSuggestion,
                        lastSetOutcome: lastSetOutcome,
                        lastCompletedSet: lastCompletedSet,
                        lastCompletedSets: lastCompletedSets,
                        isAdaptiveActive: isAdaptiveActive,
                        onDecrease: onDecrease,
                        onIncrease: onIncrease,
                        onSkip: onSkip,
                        skipExerciseLabel: skipExerciseLabel,
                        onSkipExercise: onSkipExercise,
                        onUseAdaptive: onUseAdaptive,
                        onMinimize: onMinimize
                    )
                }
            }
        }
        .zIndex(6)
    }
}

// MARK: - Subviews

struct FeedbackContent: View {
    let state: WorkoutRestModalState
    let remainingSeconds: Int
    let sessionAccentColor: Color
    let pendingRestSuggestion: PendingRestSuggestion?
    let isAdaptiveActive: Bool
    let onDecrease: () -> Void
    let onIncrease: () -> Void
    let onUseAdaptive: (() -> Void)?
    let postExerciseFeedbackContent: AnyView
    let feedbackExerciseCount: Int
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Adaptive suggestion
                if let suggestion = pendingRestSuggestion, !isAdaptiveActive, let onUseAdaptive = onUseAdaptive {
                    Button(action: onUseAdaptive) {
                        HStack {
                            Text("Descanso sugerido")
                                .font(.headline)
                                .fontWeight(.black)
                                .foregroundColor(sessionAccentColor)
                            Spacer()
                            Text(formatTime(seconds: suggestion.adaptiveSeconds))
                                .font(.subheadline)
                                .fontWeight(.bold)
                                .foregroundColor(.white.opacity(0.72))
                        }
                        .padding()
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(sessionAccentColor.opacity(0.4), lineWidth: 1)
                        )
                    }
                }
                
                Spacer().frame(height: 8)
                
                let timerSize: CGFloat = feedbackExerciseCount <= 1 ? 152 : 132
                let strokeWidth: CGFloat = feedbackExerciseCount <= 1 ? 7 : 6
                
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.08), lineWidth: strokeWidth)
                    
                    Circle()
                        .trim(from: 0.0, to: CGFloat(min(max(Double(remainingSeconds) / Double(max(1, state.activeSeconds)), 0.0), 1.0)))
                        .stroke(sessionAccentColor, style: StrokeStyle(lineWidth: strokeWidth, lineCap: .round))
                        .rotationEffect(Angle(degrees: -90))
                    
                    VStack {
                        Text(formatTime(seconds: max(0, remainingSeconds)))
                            .font(.system(size: feedbackExerciseCount <= 1 ? 34 : 30, weight: .black, design: .default))
                            .foregroundColor(.white)
                        
                        Text(state.restKindText().uppercased())
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(.white.opacity(0.45))
                            .tracking(2)
                    }
                }
                .frame(width: timerSize, height: timerSize)
                
                Spacer().frame(height: 16)
                
                postExerciseFeedbackContent
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 24)
        }
    }
}

struct NormalRestContent: View {
    let state: WorkoutRestModalState
    let remainingSeconds: Int
    let sessionAccentColor: Color
    let pendingRestSuggestion: PendingRestSuggestion?
    let lastSetOutcome: SetOutcomeV2?
    let lastCompletedSet: CompletedSet?
    let lastCompletedSets: [(String, CompletedSet)]
    let isAdaptiveActive: Bool
    let onDecrease: () -> Void
    let onIncrease: () -> Void
    let onSkip: () -> Void
    let skipExerciseLabel: String?
    let onSkipExercise: (() -> Void)?
    let onUseAdaptive: (() -> Void)?
    let onMinimize: (() -> Void)?
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Spacer().frame(height: 10)
                
                if let onMinimize = onMinimize {
                    HStack {
                        Spacer()
                        Button(action: onMinimize) {
                            Image(systemName: "chevron.down")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.white.opacity(0.7))
                                .frame(width: 40, height: 40)
                                .background(Color.white.opacity(0.08))
                                .clipShape(Circle())
                                .overlay(Circle().stroke(Color.white.opacity(0.06), lineWidth: 1))
                        }
                    }
                }
                
                // Big Timer Circle
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.08), lineWidth: 8)
                    
                    Circle()
                        .trim(from: 0.0, to: CGFloat(min(max(Double(remainingSeconds) / Double(max(1, state.activeSeconds)), 0.0), 1.0)))
                        .stroke(sessionAccentColor, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                        .rotationEffect(Angle(degrees: -90))
                    
                    VStack {
                        Text(formatTime(seconds: max(0, remainingSeconds)))
                            .font(.system(size: 46, weight: .black))
                            .foregroundColor(.white)
                        
                        Text(state.restKindText().uppercased())
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(.white.opacity(0.45))
                            .tracking(2)
                        
                        if !state.exerciseName.isEmpty {
                            Text(state.exerciseName)
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(sessionAccentColor.opacity(0.85))
                                .lineLimit(1)
                                .frame(maxWidth: 140)
                        }
                    }
                    .padding(24)
                }
                .frame(width: 204, height: 204)
                
                // Adjust Buttons
                HStack(spacing: 16) {
                    Button(action: onDecrease) {
                        Image(systemName: "minus")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                            .frame(width: 48, height: 48)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.white.opacity(0.06), lineWidth: 1))
                    }
                    
                    Text("Ajustar tiempo")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.white.opacity(0.7))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(20)
                        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.08), lineWidth: 1))
                    
                    Button(action: onIncrease) {
                        Image(systemName: "plus")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                            .frame(width: 48, height: 48)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.white.opacity(0.06), lineWidth: 1))
                    }
                }
                
                // Last Completed Sets History card list
                if !lastCompletedSets.isEmpty {
                    ForEach(0..<lastCompletedSets.count, id: \.self) { idx in
                        let pair = lastCompletedSets[idx]
                        GlassyLastSetCard(exerciseName: pair.0, set: pair.1, outcome: lastSetOutcome ?? pair.1.setOutcomeV2, sessionAccentColor: sessionAccentColor)
                    }
                } else if let referenceSet = lastCompletedSet ?? pendingRestSuggestion?.lastSet {
                    let outcome = lastSetOutcome ?? referenceSet.setOutcomeV2
                    GlassyLastSetCard(exerciseName: "Último set", set: referenceSet, outcome: outcome, sessionAccentColor: sessionAccentColor)
                }
                
                // Adaptive Recommendation
                if let suggestion = pendingRestSuggestion {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(isAdaptiveActive ? "DESCANSO DINÁMICO" : "PLAN DE SESIÓN")
                                .font(.system(size: 9, weight: .black))
                                .foregroundColor(isAdaptiveActive ? .white.opacity(0.5) : sessionAccentColor)
                            
                            if state.isManualOverride {
                                Text("Manual")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.white.opacity(0.62))
                            }
                            
                            Text(formatTime(seconds: state.plannedSeconds))
                                .font(.title3)
                                .fontWeight(.black)
                                .foregroundColor(.white)
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white.opacity(0.05))
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(isAdaptiveActive ? Color.white.opacity(0.15) : sessionAccentColor.opacity(0.25), lineWidth: 1))
                        
                        if !isAdaptiveActive, let onUseAdaptive = onUseAdaptive {
                            Button(action: onUseAdaptive) {
                                VStack {
                                    HStack(spacing: 4) {
                                        Image(systemName: "sparkles")
                                            .font(.system(size: 12))
                                        Text("Usar Dinámico")
                                            .font(.system(size: 10, weight: .black))
                                    }
                                    Spacer().frame(height: 1)
                                    Text(formatTime(seconds: suggestion.adaptiveSeconds))
                                        .font(.title3)
                                        .fontWeight(.black)
                                }
                                .foregroundColor(sessionAccentColor)
                                .frame(maxWidth: .infinity)
                                .frame(height: 52)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(sessionAccentColor.opacity(0.4), lineWidth: 1)
                                )
                            }
                        }
                    }
                }
                
                Spacer().frame(height: 8)
                
                // Primary skip buttons
                VStack(spacing: 8) {
                    Button(action: onSkip) {
                        HStack(spacing: 8) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 20))
                                .foregroundColor(.black)
                            Text("Saltar descanso")
                                .font(.headline)
                                .fontWeight(.bold)
                                .foregroundColor(.black)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.white)
                        .cornerRadius(24)
                    }
                    
                    if let onSkipExercise = onSkipExercise {
                        Button(action: onSkipExercise) {
                            HStack(spacing: 8) {
                                Image(systemName: "forward.end.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white.opacity(0.7))
                                Text(skipExerciseLabel ?? "Saltar series restantes e ir al siguiente ejercicio")
                                    .font(.subheadline)
                                    .fontWeight(.bold)
                                    .foregroundColor(.white.opacity(0.7))
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 16)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Color.white.opacity(0.06))
                            .cornerRadius(24)
                            .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.16), lineWidth: 1.5))
                        }
                    }
                }
                
                Spacer().frame(height: 16)
            }
            .padding(.horizontal, 24)
        }
    }
}

struct GlassyLastSetCard: View {
    let exerciseName: String
    let set: CompletedSet
    let outcome: SetOutcomeV2?
    let sessionAccentColor: Color
    
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "clock.arrow.circlepath")
                .font(.system(size: 16))
                .foregroundColor(sessionAccentColor.opacity(0.82))
            
            VStack(alignment: .leading, spacing: 2) {
                let loadStr = set.weight > 0.0 ? "\(formatWeight(set.weight)) kg" : "Peso corporal"
                let valueStr = set.timeSeconds != nil ? "\(set.timeSeconds!)s" : "\(set.reps) reps"
                
                Text(exerciseName)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(.white.opacity(0.48))
                
                Text("\(loadStr) x \(valueStr)")
                    .font(.body)
                    .fontWeight(.bold)
                    .foregroundColor(.white.opacity(0.86))
                    .lineLimit(1)
            }
            
            Spacer()
            
            HStack(spacing: 4) {
                if outcome?.isContextPr == true || outcome?.isGlobalPr == true {
                    RestTinyBadge(text: "Récord", color: Color(red: 1.0, green: 0.84, blue: 0.25))
                }
                if set.isFailure || set.isFailedSet {
                    RestTinyBadge(text: "Fallo", color: Color(red: 1.0, green: 0.32, blue: 0.32))
                }
                if !set.dropSets.isEmpty {
                    RestTinyBadge(text: "Drop", color: Color(red: 0.25, green: 0.77, blue: 1.0))
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .background(Color.white.opacity(0.06))
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }
    
    private func formatWeight(_ weight: Double) -> String {
        if weight.truncatingRemainder(dividingBy: 1.0) == 0.0 {
            return String(Int(weight))
        } else {
            return String(format: "%.1f", weight)
        }
    }
}

struct RestTinyBadge: View {
    let text: String
    let color: Color
    
    var body: some View {
        Text(text)
            .font(.system(size: 9, weight: .bold))
            .foregroundColor(color)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(color.opacity(0.13))
            .cornerRadius(4)
    }
}

public struct RestTimerPill: View {
    let remainingSeconds: Int
    let totalSeconds: Int
    let exerciseName: String
    let sessionAccentColor: Color
    let onClick: () -> Void
    
    public init(
        remainingSeconds: Int,
        totalSeconds: Int,
        exerciseName: String,
        sessionAccentColor: Color = .white,
        onClick: @escaping () -> Void
    ) {
        self.remainingSeconds = remainingSeconds
        self.totalSeconds = totalSeconds
        self.exerciseName = exerciseName
        self.sessionAccentColor = sessionAccentColor
        self.onClick = onClick
    }
    
    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 10) {
                let progress = Double(remainingSeconds) / Double(max(1, totalSeconds))
                
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.1), lineWidth: 2.5)
                    Circle()
                        .trim(from: 0.0, to: CGFloat(min(max(progress, 0.0), 1.0)))
                        .stroke(sessionAccentColor, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                        .rotationEffect(Angle(degrees: -90))
                    
                    Text(formatTime(seconds: max(0, remainingSeconds)))
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(.white)
                }
                .frame(width: 28, height: 28)
                
                if !exerciseName.isEmpty {
                    Text(exerciseName)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white.opacity(0.7))
                        .lineLimit(1)
                        .frame(maxWidth: 100)
                }
                
                Image(systemName: "chevron.up")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.5))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Color(red: 0.1, green: 0.1, blue: 0.18).opacity(0.92))
            .cornerRadius(20)
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(sessionAccentColor.opacity(0.35), lineWidth: 1))
            .shadow(color: Color.black.opacity(0.3), radius: 8, x: 0, y: 4)
        }
        .zIndex(7)
    }
}

// MARK: - Extension helpers

extension WorkoutRestModalState {
    func restKindText() -> String {
        switch kind {
        case .SUPERSET_INTRA: return "superserie"
        case .SUPERSET_ROUND: return "siguiente ronda"
        case .WARMUP: return "aproximación"
        case .BETWEEN_SIDES: return "entre lados"
        case .STANDARD: return "descanso"
        }
    }
}
