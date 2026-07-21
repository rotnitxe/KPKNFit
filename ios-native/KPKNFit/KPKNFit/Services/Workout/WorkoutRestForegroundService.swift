import Foundation
import UserNotifications
import AVFoundation
import UIKit

final class WorkoutRestForegroundService: NSObject {
    static let shared = WorkoutRestForegroundService()

    static let channelId = "rest_ongoing"
    static let notifId = 42041

    static let actionCompleteSet = "COMPLETE_SET"
    static let actionSkipTimer = "SKIP_TIMER"
    static let actionSubtractTime = "SUBTRACT_TIME"
    static let actionAddTime = "ADD_TIME"

    static let extraSessionName = "extra_session_name"
    static let extraExerciseName = "extra_exercise_name"
    static let extraExerciseImage = "extra_exercise_image"
    static let extraSetInfo = "extra_set_info"
    static let extraEndAt = "extra_end_at"

    private var endAt: Date?
    private var displayLink: CADisplayLink?
    private var sessionName: String = ""
    private var exerciseName: String = ""
    private var setInfo: String = ""

    private override init() {
        super.init()
        registerCategories()
    }

    private func registerCategories() {
        let completeAction = UNNotificationAction(identifier: Self.actionCompleteSet, title: "Completar serie", options: [])
        let skipAction = UNNotificationAction(identifier: Self.actionSkipTimer, title: "Saltar", options: [.destructive])
        let subtractAction = UNNotificationAction(identifier: Self.actionSubtractTime, title: "-15s", options: [])
        let addAction = UNNotificationAction(identifier: Self.actionAddTime, title: "+15s", options: [])

        let category = UNNotificationCategory(
            identifier: Self.channelId,
            actions: [completeAction, skipAction, subtractAction, addAction],
            intentIdentifiers: [],
            options: [.hiddenPreviewsShowTitle]
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    func start(sessionName: String, exerciseName: String, setInfo: String = "", endAt: Date) {
        self.sessionName = sessionName
        self.exerciseName = exerciseName
        self.setInfo = setInfo
        self.endAt = endAt

        activateAudioSession()
        postOngoingNotification()

        displayLink?.invalidate()
        let link = CADisplayLink(target: self, selector: #selector(tick))
        link.add(to: .current, forMode: .common)
        displayLink = link
    }

    func updateEndTime(_ newEndAt: Date) {
        endAt = newEndAt
        postOngoingNotification()
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        endAt = nil
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["\(Self.notifId)"])
        deactivateAudioSession()
    }

    private func activateAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers])
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {}
    }

    private func deactivateAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {}
    }

    @objc private func tick() {
        guard let endAt = endAt else { return }
        let remaining = max(0, endAt.timeIntervalSinceNow)
        let minutes = Int(remaining) / 60
        let seconds = Int(remaining) % 60
        let timeString = String(format: "%d:%02d", minutes, seconds)

        let content = makeNotificationContent(timeString: timeString)
        let request = UNNotificationRequest(
            identifier: "\(Self.notifId)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { _ in }
    }

    private func postOngoingNotification() {
        guard let endAt = endAt else { return }
        let remaining = max(0, endAt.timeIntervalSinceNow)
        let minutes = Int(remaining) / 60
        let seconds = Int(remaining) % 60
        let timeString = String(format: "%d:%02d", minutes, seconds)

        let content = makeNotificationContent(timeString: timeString)
        let request = UNNotificationRequest(
            identifier: "\(Self.notifId)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { _ in }
    }

    private func makeNotificationContent(timeString: String) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = "Descanso activo"
        var body = sessionName
        if !exerciseName.isEmpty {
            body += " · \(exerciseName)"
        }
        if !setInfo.isEmpty {
            body += " · \(setInfo)"
        }
        body += " · \(timeString)"
        content.body = body
        content.categoryIdentifier = Self.channelId
        content.sound = nil
        content.badge = nil
        return content
    }
}

final class TimerNotificationActionHandler: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let action = response.actionIdentifier

        switch action {
        case WorkoutRestForegroundService.actionCompleteSet:
            ActiveWorkoutHolder.shared.handleAction(.completeSet)
        case WorkoutRestForegroundService.actionSkipTimer:
            ActiveWorkoutHolder.shared.handleAction(.skipTimer)
        case WorkoutRestForegroundService.actionSubtractTime:
            ActiveWorkoutHolder.shared.handleAction(.subtractTime)
        case WorkoutRestForegroundService.actionAddTime:
            ActiveWorkoutHolder.shared.handleAction(.addTime)
        default:
            break
        }

        if action == UNNotificationDefaultActionIdentifier {
            if let rootVC = UIApplication.shared.connectedScenes
                .compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController })
                .first {
                // Navigate to training
            }
        }

        completionHandler()
    }
}
