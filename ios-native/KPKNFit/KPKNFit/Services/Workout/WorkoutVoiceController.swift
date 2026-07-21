import Foundation
import Combine

final class WorkoutVoiceController {

    private let continuousEngine = WorkoutContinuousVoiceEngine()
    private let ttsManager = WorkoutTtsManager()

    @Published private(set) var state = VoiceSessionState()

    var onCommandDetected: ((VoiceSessionCommand) -> Void)?
    var onError: ((String) -> Void)?
    var onStageChanged: ((VoicePipelineStage) -> Void)?
    var exerciseInfoProvider: (() -> ExerciseInfo?)?

    struct ExerciseInfo {
        let exercise: Exercise
        let setIndex: Int
        let totalSets: Int
        let isTimeMode: Bool
        let isUnilateral: Bool
        let baseIntensityMode: IntensityMode?
        let setDraft: WorkoutSetDraft?
        let suggestedWeight: Double?
        let restSecondsRemaining: Int?
        let nextExerciseName: String?
        var showPostExerciseSheet: Bool = false
        var showFinishSheet: Bool = false
        var supersetRound: Int? = nil
        var isUnilateralSidePending: Bool = false
        var completedSidesCount: Int = 0
    }

    private var cancellables = Set<AnyCancellable>()
    private var confirmationWorkItem: DispatchWorkItem?
    private var confirmedOrCancelled = false

    // MARK: - Public API

    func initialize() {
        ttsManager.initialize(
            onReady: { [weak self] in
                self?.updateStage(.DISABLED)
            },
            onError: { [weak self] _ in
                self?.updateStage(.ERROR_RECOVERY)
            }
        )
    }

    func enable() {
        guard state.stage == .DISABLED else { return }
        startListening()
        updateStage(.LISTENING)
        state.consecutiveErrors = 0
    }

    func disable() {
        cancelAllSubscriptions()
        continuousEngine.stop()
        ttsManager.stop()
        updateStage(.DISABLED)
        resetState()
    }

    func isEnabled() -> Bool {
        state.stage != .DISABLED
    }

    func getStage() -> VoicePipelineStage {
        state.stage
    }

    func onRestTimerFinished(exerciseName: String, suggestedWeight: Double?) {
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakRestComplete(exerciseName: exerciseName, suggestedWeight: suggestedWeight)
        }
    }

    func onRestTimerStarted(durationSeconds: Int) {
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakRestStarted(totalSeconds: durationSeconds)
        }
    }

    func onRestTimerStartedContextual(durationSeconds: Int, isTransition: Bool) {
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakRestStartedContextual(seconds: durationSeconds, isTransition: isTransition)
        }
    }

    func speakUnilateralSideRegistered(completedSide: String, pendingSide: String) {
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakUnilateralSideRegistered(completedSide: completedSide, pendingSide: pendingSide)
        }
    }

    func speakSuggestedWeight(exerciseName: String, suggestedWeight: Double) {
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakSuggestedWeight(weightKg: suggestedWeight, exerciseName: exerciseName)
        }
    }

    func speakRestRemaining(totalSeconds: Int) {
        let safeSeconds = max(0, totalSeconds)
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakRestRemaining(minutes: safeSeconds / 60, seconds: safeSeconds % 60)
        }
    }

    func speakCurrentExercise(exerciseName: String, setNumber: Int, totalSets: Int, round: Int? = nil) {
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakCurrentExercise(name: exerciseName, setNumber: setNumber, totalSets: totalSets, round: round)
        }
    }

    func speakNextExercise(exerciseName: String, restSeconds: Int? = nil) {
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakNextExercise(name: exerciseName, restSeconds: restSeconds)
        }
    }

    func speakFeedbackUpdated(message: String) {
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakError(message)
        }
    }

    func speakFeedbackSaved() {
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakError("Feedback registrado.")
        }
    }

    func speakSessionSaved() {
        speakWhilePaused { [weak self] in
            self?.ttsManager.speakSessionSaved()
        }
    }

    func shutdown() {
        cancelAllSubscriptions()
        continuousEngine.stop()
        ttsManager.stop()
        ttsManager.shutdown()
        state = VoiceSessionState(
            stage: .DISABLED,
            partialText: "",
            lastInterpretation: nil,
            lastCommand: nil,
            errorMessage: nil,
            consecutiveErrors: 0
        )
    }

    // MARK: - Private

    private func speakWhilePaused(_ block: @escaping () -> Void) {
        guard state.stage != .DISABLED else { return }

        continuousEngine.pause()
        updateStage(.TTS_SPEAKING)
        ttsManager.setOnUtteranceComplete { [weak self] in
            guard let self = self else { return }
            self.resumeListening()
        }
        block()
    }

    private func startListening() {
        continuousEngine.start()

        continuousEngine.finalResults
            .sink { [weak self] text in
                self?.handleFinalResult(text)
            }
            .store(in: &cancellables)

        continuousEngine.partialResults
            .sink { [weak self] text in
                guard let self = self else { return }
                self.state.partialText = text
            }
            .store(in: &cancellables)

        continuousEngine.errors
            .sink { [weak self] error in
                self?.onError?(error)
            }
            .store(in: &cancellables)
    }

    private func handleFinalResult(_ text: String) {
        let s = state
        guard s.stage != .DISABLED, s.stage != .TTS_SPEAKING else { return }

        if s.stage == .CONFIRM_WAIT {
            handleConfirmInput(text)
            return
        }

        if s.stage == .LISTENING || s.stage == .ERROR_RECOVERY {
            continuousEngine.pause()
            let info = exerciseInfoProvider?()
            processCommand(transcript: text, exerciseInfo: info)
        }
    }

    private func processCommand(transcript: String, exerciseInfo: ExerciseInfo?) {
        updateStage(.PROCESSING)

        if exerciseInfo?.showFinishSheet == true {
            let finalCmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand(transcript: transcript)
            state.lastCommand = finalCmd
            onCommandDetected?(finalCmd)
            resumeListening()
            return
        }

        if exerciseInfo?.showPostExerciseSheet == true {
            let feedbackCmd = WorkoutVoiceCommandParser.parseFeedbackCommand(transcript: transcript)
            state.lastCommand = feedbackCmd
            onCommandDetected?(feedbackCmd)
            resumeListening()
            return
        }

        let isTimeMode = exerciseInfo?.isTimeMode ?? false
        let isUnilateral = exerciseInfo?.isUnilateral ?? false

        let command = WorkoutVoiceCommandParser.parseCommand(
            transcript: transcript,
            isTimeMode: isTimeMode,
            isUnilateral: isUnilateral,
            hasPendingConfirmation: false,
            isRestTimerActive: exerciseInfo?.restSecondsRemaining != nil
        )

        switch command {
        case .registerSet(let interpretation):
            handleRegisterSet(interpretation, exerciseInfo: exerciseInfo)

        case .turnOffVoice:
            disable()
            return

        case .confirm, .cancel:
            resumeListening()
            return

        case .unknown:
            resumeListening()
            return

        default:
            state.lastCommand = command
            onCommandDetected?(command)
            if state.stage != .TTS_SPEAKING {
                resumeListening()
            }
        }
    }

    private func handleRegisterSet(_ interpretation: WorkoutVoiceInterpretation, exerciseInfo: ExerciseInfo?) {
        confirmedOrCancelled = false

        state.lastInterpretation = interpretation
        state.lastCommand = .registerSet(interpretation)

        let isTimeMode = exerciseInfo?.isTimeMode ?? false
        updateStage(.TTS_SPEAKING)

        ttsManager.setOnUtteranceComplete { [weak self] in
            guard let self = self else { return }
            self.updateStage(.CONFIRM_WAIT)
            self.continuousEngine.start()
            self.startConfirmationTimeout(interpretation)
        }

        ttsManager.speakSetConfirmation(
            weightKg: interpretation.weightKg,
            reps: interpretation.metricValue,
            rpe: interpretation.intensityKind == .RPE ? interpretation.intensityValue : nil,
            rir: interpretation.intensityKind == .RIR ? interpretation.intensityValue.flatMap { Int($0) } : nil,
            isTimeMode: isTimeMode
        )
    }

    private func handleConfirmInput(_ text: String) {
        let confirmCommand = WorkoutVoiceCommandParser.parseCommand(
            transcript: text,
            isTimeMode: false,
            isUnilateral: false,
            hasPendingConfirmation: true,
            isRestTimerActive: false
        )

        switch confirmCommand {
        case .confirm: doConfirm()
        case .cancel: doCancel()
        default: doConfirm()
        }
    }

    private func doConfirm() {
        guard !confirmedOrCancelled else { return }
        confirmedOrCancelled = true
        confirmationWorkItem?.cancel()

        guard let interpretation = state.lastInterpretation else {
            resumeListening()
            return
        }

        let info = exerciseInfoProvider?()
        let isUnilateral = info?.isUnilateral == true
        let completedSidesBefore = info?.completedSidesCount ?? 0

        onCommandDetected?(.registerSet(interpretation))

        updateStage(.TTS_SPEAKING)
        ttsManager.setOnUtteranceComplete { [weak self] in
            guard let self = self else { return }
            self.resumeListening()
        }

        if isUnilateral {
            let completedSide = interpretation.side ?? "left"
            let counterpart = completedSide == "left" ? "right" : "left"
            if completedSidesBefore == 0 {
                ttsManager.speakUnilateralSideRegistered(completedSide: completedSide, pendingSide: counterpart)
            } else {
                ttsManager.speakSetRegistered(
                    weightKg: interpretation.weightKg,
                    reps: interpretation.metricValue,
                    isTimeMode: info?.isTimeMode ?? false
                )
            }
        } else {
            ttsManager.speakSetRegistered(
                weightKg: interpretation.weightKg,
                reps: interpretation.metricValue,
                isTimeMode: info?.isTimeMode ?? false
            )
        }
    }

    private func doCancel() {
        guard !confirmedOrCancelled else { return }
        confirmedOrCancelled = true
        confirmationWorkItem?.cancel()

        state.lastInterpretation = nil
        state.lastCommand = .cancel

        updateStage(.TTS_SPEAKING)
        ttsManager.setOnUtteranceComplete { [weak self] in
            guard let self = self else { return }
            self.resumeListening()
        }
        ttsManager.speakError("Cancelado.")
    }

    private func startConfirmationTimeout(_ interpretation: WorkoutVoiceInterpretation) {
        confirmationWorkItem?.cancel()
        let hasData = interpretation.weightKg != nil && interpretation.metricValue != nil

        let item = DispatchWorkItem { [weak self] in
            guard let self = self else { return }
            if !self.confirmedOrCancelled && self.state.hasPendingConfirmation && hasData {
                self.doConfirm()
                self.ttsManager.speakAutoConfirmed()
            } else if !self.confirmedOrCancelled {
                self.doCancel()
            }
        }
        confirmationWorkItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + 5.0, execute: item)
    }

    private func resumeListening() {
        guard state.stage != .DISABLED else { return }

        state.partialText = ""
        state.lastInterpretation = nil
        state.lastCommand = nil
        state.errorMessage = nil
        continuousEngine.start()
        updateStage(.LISTENING)
    }

    private func cancelAllSubscriptions() {
        cancellables.removeAll()
        confirmationWorkItem?.cancel()
        confirmationWorkItem = nil
    }

    private func resetState() {
        state.partialText = ""
        state.lastInterpretation = nil
        state.lastCommand = nil
        state.errorMessage = nil
        state.consecutiveErrors = 0
    }

    private func updateStage(_ stage: VoicePipelineStage) {
        state.stage = stage
        onStageChanged?(stage)
    }
}
