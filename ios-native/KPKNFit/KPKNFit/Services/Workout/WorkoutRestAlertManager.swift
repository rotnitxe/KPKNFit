import Foundation
import AVFoundation
import UIKit
import UserNotifications

final class WorkoutRestAlertManager {

    struct RestAlertCapabilityState {
        let notificationsEnabled: Bool
        let exactAlarmGranted: Bool
        let soundReady: Bool

        var needsPersistentChip: Bool { !notificationsEnabled }
        var needsAlarmWarning: Bool { !exactAlarmGranted }
        var needsSoundBadge: Bool { !soundReady }
    }

    static let CHANNEL_REST_ONGOING = "workout_rest_ongoing"
    static let CHANNEL_REST_FINISHED = "workout_rest_finished"

    private static let PREFS = "workout_rest_alerts"
    private static let KEY_TIMER_ID = "active_timer_id"
    private static let KEY_SESSION_NAME = "active_session_name"
    private static let KEY_EXERCISE_NAME = "active_exercise_name"
    private static let KEY_END_AT = "active_end_at"
    private static let KEY_LAST_AUDIO_FAILURE_AT = "last_audio_failure_at"

    private static let NOTIF_ID_ONGOING = "rest_ongoing"
    private static let NOTIF_ID_FINISHED = "rest_finished"

    internal static let EXTRA_TIMER_ID = "timer_id"
    internal static let EXTRA_SESSION_NAME = "session_name"
    internal static let EXTRA_EXERCISE_NAME = "exercise_name"
    internal static let EXTRA_EVENT = "event"
    internal static let EXTRA_END_AT = "end_at"
    internal static let EVENT_PREALERT = "prealert"
    internal static let EVENT_FINISH = "finish"

    private let prefs = UserDefaults(suiteName: WorkoutRestAlertManager.PREFS) ?? .standard
    private let notificationCenter = UNUserNotificationCenter.current()
    private let audioSession = AVAudioSession.sharedInstance()
    private var toneEngine: AVAudioEngine?
    private var tonePlayer: AVAudioPlayerNode?
    private var playWorkItem: DispatchWorkItem?
    private let playQueue = DispatchQueue(label: "com.kpkn.workout.restAlertPlay", qos: .userInitiated)

    func ensureChannels() {
        let ongoing = UNNotificationCategory(
            identifier: Self.CHANNEL_REST_ONGOING,
            actions: [],
            intentIdentifiers: [],
            options: []
        )
        let finished = UNNotificationCategory(
            identifier: Self.CHANNEL_REST_FINISHED,
            actions: [],
            intentIdentifiers: [],
            options: []
        )
        notificationCenter.setNotificationCategories([ongoing, finished])
    }

    func capabilityState(soundsEnabled: Bool? = nil) -> RestAlertCapabilityState {
        let resolvedSounds = soundsEnabled ?? (ProgramRepository.shared.settings.soundsEnabled)
        return RestAlertCapabilityState(
            notificationsEnabled: canPostNotifications(),
            exactAlarmGranted: true,
            soundReady: !resolvedSounds || SystemAudioHelper.isNormalRinger()
        )
    }

    func scheduleRestEnd(
        durationSeconds: Int,
        sessionName: String,
        exerciseName: String,
        setInfo: String = "",
        exerciseImage: Data? = nil,
        endAtOverrideMs: Int64? = nil,
        isAdjustment: Bool = false
    ) -> String {
        ensureChannels()
        if isAdjustment {
            cancelAlarm(event: Self.EVENT_FINISH)
            cancelAlarm(event: Self.EVENT_PREALERT)
        } else {
            cancelRestAlerts()
        }

        let timerId = UUID().uuidString
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let endAt = endAtOverrideMs.flatMap { $0 > now ? $0 : nil } ?? (now + Int64(durationSeconds) * 1000)
        let remainingSeconds = max(1, Int((endAt - now + 999) / 1000))

        prefs.set(timerId, forKey: Self.KEY_TIMER_ID)
        prefs.set(sessionName, forKey: Self.KEY_SESSION_NAME)
        prefs.set(exerciseName, forKey: Self.KEY_EXERCISE_NAME)
        prefs.set(NSNumber(value: endAt), forKey: Self.KEY_END_AT)

        scheduleAlarm(
            timerId: timerId,
            sessionName: sessionName,
            exerciseName: exerciseName,
            event: Self.EVENT_FINISH,
            triggerAtMs: endAt,
            endAtMs: endAt
        )
        if shouldScheduleWorkoutRestPrealert(durationSeconds: remainingSeconds) {
            scheduleAlarm(
                timerId: timerId,
                sessionName: sessionName,
                exerciseName: exerciseName,
                event: Self.EVENT_PREALERT,
                triggerAtMs: workoutRestPrealertTriggerAt(endAtMs: endAt),
                endAtMs: endAt
            )
        }

        WorkoutRestForegroundService.shared.start(
            sessionName: sessionName,
            exerciseName: exerciseName,
            setInfo: setInfo,
            endAt: Date(timeIntervalSince1970: Double(endAt) / 1000.0)
        )

        postOngoingNotification(sessionName: sessionName, exerciseName: exerciseName, endAt: endAt)
        return timerId
    }

    func onTimerFinishedInApp(expectedTimerId: String?) {
        guard let activeId = prefs.string(forKey: Self.KEY_TIMER_ID),
              let expectedTimerId = expectedTimerId,
              activeId == expectedTimerId else { return }
        let sessionName = prefs.string(forKey: Self.KEY_SESSION_NAME) ?? "Entrenamiento"
        let exerciseName = prefs.string(forKey: Self.KEY_EXERCISE_NAME) ?? "Siguiente serie"
        deliverCompletionAlert(sessionName: sessionName, exerciseName: exerciseName)
    }

    func cancelRestAlerts() {
        WorkoutRestForegroundService.shared.stop()
        cancelAlarm(event: Self.EVENT_FINISH)
        cancelAlarm(event: Self.EVENT_PREALERT)
        notificationCenter.removeDeliveredNotifications(withIdentifiers: [Self.NOTIF_ID_ONGOING, Self.NOTIF_ID_FINISHED])
        prefs.removeObject(forKey: Self.KEY_TIMER_ID)
        prefs.removeObject(forKey: Self.KEY_SESSION_NAME)
        prefs.removeObject(forKey: Self.KEY_EXERCISE_NAME)
        prefs.removeObject(forKey: Self.KEY_END_AT)
    }

    internal func onAlarmFired(timerId: String?, sessionName: String?, exerciseName: String?, event: String?, endAtMs: Int64) {
        guard let activeId = prefs.string(forKey: Self.KEY_TIMER_ID),
              let timerId = timerId,
              activeId == timerId else { return }

        let safeSession = sessionName ?? (prefs.string(forKey: Self.KEY_SESSION_NAME) ?? "Entrenamiento")
        let safeExercise = exerciseName ?? (prefs.string(forKey: Self.KEY_EXERCISE_NAME) ?? "Siguiente serie")

        if event == Self.EVENT_PREALERT {
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            if endAtMs > now {
                deliverPrealertCue()
            }
        } else {
            deliverCompletionAlert(sessionName: safeSession, exerciseName: safeExercise)
        }
    }

    // MARK: - Private

    private func scheduleAlarm(
        timerId: String,
        sessionName: String,
        exerciseName: String,
        event: String,
        triggerAtMs: Int64,
        endAtMs: Int64
    ) {
        let fireDate = Date(timeIntervalSince1970: Double(triggerAtMs) / 1000)
        let content = UNMutableNotificationContent()
        content.userInfo = [
            Self.EXTRA_TIMER_ID: timerId,
            Self.EXTRA_SESSION_NAME: sessionName,
            Self.EXTRA_EXERCISE_NAME: exerciseName,
            Self.EXTRA_EVENT: event,
            Self.EXTRA_END_AT: NSNumber(value: endAtMs),
        ]

        if event == Self.EVENT_FINISH {
            content.categoryIdentifier = Self.CHANNEL_REST_FINISHED
            content.sound = UNNotificationSound.default
        } else {
            content.categoryIdentifier = Self.CHANNEL_REST_ONGOING
            content.sound = nil
        }

        let dateComponents = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fireDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: false)
        let request = UNNotificationRequest(
            identifier: "rest_\(event)_\(timerId)",
            content: content,
            trigger: trigger
        )
        notificationCenter.add(request)
    }

    private func cancelAlarm(event: String) {
        guard let timerId = prefs.string(forKey: Self.KEY_TIMER_ID) else { return }
        notificationCenter.removePendingNotificationRequests(withIdentifiers: ["rest_\(event)_\(timerId)"])
    }

    private func playToneSequenceAsync(steps: [WorkoutRestToneStep], onComplete: @escaping () -> Void = {}) {
        playQueue.async { [weak self] in
            guard let self = self else { return }
            let result = self.playToneSequence(steps: steps)
            DispatchQueue.main.async {
                onComplete()
            }
        }
    }

    private func deliverPrealertCue() {
        let settings = ProgramRepository.shared.settings
        let soundsEnabled = settings.soundsEnabled
        let hapticEnabled = settings.hapticFeedbackEnabled

        if SystemAudioHelper.shouldVibrate(hapticEnabled: hapticEnabled) {
            vibratePattern(pattern: workoutPrealertVibrationPattern())
        }
        if soundsEnabled && SystemAudioHelper.isNormalRinger() {
            playToneSequenceAsync(steps: workoutPrealertTonePlan())
        }
    }

    private func deliverCompletionAlert(sessionName: String, exerciseName: String) {
        let settings = ProgramRepository.shared.settings
        let soundsEnabled = settings.soundsEnabled
        let hapticEnabled = settings.hapticFeedbackEnabled
        let hapticIntensity = settings.hapticIntensity

        if SystemAudioHelper.shouldVibrate(hapticEnabled: hapticEnabled) {
            vibratePattern(
                pattern: workoutCompletionVibrationPattern(intensity: hapticIntensity),
                amplitudes: workoutCompletionVibrationAmplitudes(intensity: hapticIntensity)
            )
        }

        let shouldAttemptManualSound = soundsEnabled && SystemAudioHelper.isNormalRinger()

        let onFinishAlert = { [weak self] in
            guard let self = self else { return }
            WorkoutRestForegroundService.shared.stop()
            self.notificationCenter.removeDeliveredNotifications(withIdentifiers: [Self.NOTIF_ID_ONGOING])

            let preferAudible = soundsEnabled && SystemAudioHelper.isNormalRinger()
            self.postFinishedNotification(
                sessionName: sessionName,
                exerciseName: exerciseName,
                preferAudibleFallback: preferAudible
            )

            self.prefs.removeObject(forKey: Self.KEY_TIMER_ID)
            self.prefs.removeObject(forKey: Self.KEY_SESSION_NAME)
            self.prefs.removeObject(forKey: Self.KEY_EXERCISE_NAME)
            self.prefs.removeObject(forKey: Self.KEY_END_AT)
        }

        if shouldAttemptManualSound {
            playToneSequenceAsync(steps: workoutCompletionTonePlan(), onComplete: onFinishAlert)
        } else {
            onFinishAlert()
        }
    }

    private func postOngoingNotification(sessionName: String, exerciseName: String, endAt: Int64) {
        guard canPostNotifications() else { return }

        let content = UNMutableNotificationContent()
        content.title = "Descanso"
        content.body = "\(sessionName) · \(exerciseName)"
        content.categoryIdentifier = Self.CHANNEL_REST_ONGOING
        content.sound = nil
        content.badge = nil

        let endDate = Date(timeIntervalSince1970: Double(endAt) / 1000)
        let dateComponents = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: endDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: false)

        let request = UNNotificationRequest(
            identifier: Self.NOTIF_ID_ONGOING,
            content: content,
            trigger: trigger
        )
        notificationCenter.add(request)
    }

    private func postFinishedNotification(
        sessionName: String,
        exerciseName: String,
        preferAudibleFallback: Bool
    ) {
        guard canPostNotifications() else { return }

        let content = UNMutableNotificationContent()
        content.title = "Descanso completado"
        content.body = "\(sessionName) · \(exerciseName)"
        content.categoryIdentifier = Self.CHANNEL_REST_FINISHED
        if preferAudibleFallback {
            content.sound = UNNotificationSound.default
        } else {
            content.sound = nil
        }

        let request = UNNotificationRequest(
            identifier: Self.NOTIF_ID_FINISHED,
            content: content,
            trigger: nil
        )
        notificationCenter.add(request)
    }

    private func canPostNotifications() -> Bool {
        var authorized = false
        let semaphore = DispatchSemaphore(value: 0)
        notificationCenter.getNotificationSettings { settings in
            authorized = settings.authorizationStatus == .authorized
            semaphore.signal()
        }
        semaphore.wait()
        return authorized
    }

    private func playToneSequence(steps: [WorkoutRestToneStep]) -> Bool {
        try? audioSession.setCategory(.playback, mode: .default)
        try? audioSession.setActive(true)

        let engine = AVAudioEngine()
        let player = AVAudioPlayerNode()
        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: nil)
        try? engine.start()

        self.toneEngine = engine
        self.tonePlayer = player

        var success = true
        for step in steps {
            guard let buffer = generateToneBuffer(step: step) else {
                success = false
                break
            }
            player.scheduleBuffer(buffer, at: nil)
            player.play()
            Thread.sleep(forTimeInterval: Double(Int64(step.durationMs) + step.pauseAfterMs) / 1000.0)
            player.stop()
        }

        engine.stop()
        self.toneEngine = nil
        self.tonePlayer = nil

        try? audioSession.setActive(false)

        if success {
            clearAudioFailure()
        } else {
            markAudioFailure()
        }
        return success
    }

    private func generateToneBuffer(step: WorkoutRestToneStep) -> AVAudioPCMBuffer? {
        let sampleRate: Double = 44100
        let totalSamples = Int((sampleRate * Double(step.durationMs) / 1000.0).rounded())
        let attackSamples = min(Int(sampleRate * 0.018), totalSamples / 3)
        let releaseSamples = min(Int(sampleRate * 0.045), totalSamples / 2)

        guard totalSamples > 0 else { return nil }
        guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1) else { return nil }
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(totalSamples)) else { return nil }
        buffer.frameLength = buffer.frameCapacity

        guard let floatData = buffer.floatChannelData else { return nil }

        for i in 0..<totalSamples {
            let t = Double(i) / sampleRate
            let envelope: Double
            if attackSamples > 0 && i < attackSamples {
                envelope = Double(i) / Double(attackSamples)
            } else if releaseSamples > 0 && i > totalSamples - releaseSamples {
                envelope = Double(totalSamples - i) / Double(releaseSamples)
            } else {
                envelope = 1.0
            }
            let fundamental = sin(2.0 * .pi * step.frequencyHz * t)
            let softHarmonic = sin(2.0 * .pi * (step.frequencyHz * 2.0) * t) * 0.18
            let value = (fundamental + softHarmonic) * envelope * step.volume
            let clamped = max(-1.0, min(1.0, value))
            floatData[0][i] = Float(clamped)
        }

        return buffer
    }

    private func vibratePattern(pattern: [Int64], amplitudes: [Int]? = nil) {
        let timerId = prefs.string(forKey: Self.KEY_TIMER_ID) ?? ""
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        for (index, duration) in pattern.enumerated() {
            if duration > 0 {
                generator.impactOccurred(intensity: 0.7)
            }
            if index < pattern.count - 1 {
                let sleepMs = pattern[index + 1]
                usleep(useconds_t(sleepMs * 1000))
            }
        }
    }

    private func lastAudioFailureAt() -> Int64 {
        prefs.object(forKey: Self.KEY_LAST_AUDIO_FAILURE_AT) as? Int64 ?? 0
    }

    private func markAudioFailure() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        prefs.set(NSNumber(value: now), forKey: Self.KEY_LAST_AUDIO_FAILURE_AT)
    }

    private func clearAudioFailure() {
        prefs.removeObject(forKey: Self.KEY_LAST_AUDIO_FAILURE_AT)
    }
}
