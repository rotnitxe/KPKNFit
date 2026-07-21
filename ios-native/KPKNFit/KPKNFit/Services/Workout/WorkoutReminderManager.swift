import Foundation
import UserNotifications

final class WorkoutReminderManager: NSObject {
    static let shared = WorkoutReminderManager()

    static let channelWorkoutReminder = "workout_reminder"
    static let channelSleepReminder = "sleep_reminder"
    static let notifWorkout = 4001
    static let notifSleep = 4002
    static let extraReminderType = "reminder_type"
    static let typeWorkout = "workout"
    static let typeSleep = "sleep"

    private static let reqWorkout = "workout_reminder_request"
    private static let reqSleep = "sleep_reminder_request"

    private override init() {
        super.init()
    }

    func requestAuthorization() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    func createChannels() {
        let workoutCategory = UNNotificationCategory(
            identifier: Self.channelWorkoutReminder,
            actions: [],
            intentIdentifiers: [],
            options: []
        )
        let sleepCategory = UNNotificationCategory(
            identifier: Self.channelSleepReminder,
            actions: [],
            intentIdentifiers: [],
            options: []
        )
        UNUserNotificationCenter.current().setNotificationCategories([workoutCategory, sleepCategory])
    }

    func scheduleWorkoutReminder(time: String = "18:00") {
        scheduleDaily(identifier: Self.reqWorkout, hour: parseHour(time), minute: parseMin(time), type: Self.typeWorkout)
    }

    func cancelWorkoutReminder() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [Self.reqWorkout])
    }

    func scheduleSleepReminder(time: String = "22:00") {
        scheduleDaily(identifier: Self.reqSleep, hour: parseHour(time), minute: parseMin(time), type: Self.typeSleep)
    }

    func cancelSleepReminder() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [Self.reqSleep])
    }

    private func scheduleDaily(identifier: String, hour: Int, minute: Int, type: String) {
        var dateComponents = DateComponents()
        dateComponents.hour = hour
        dateComponents.minute = minute

        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: true)

        let content = UNMutableNotificationContent()
        content.categoryIdentifier = type == Self.typeWorkout ? Self.channelWorkoutReminder : Self.channelSleepReminder
        content.userInfo[Self.extraReminderType] = type

        if type == Self.typeWorkout {
            content.title = "KPKN Fit"
            content.body = "¡Hora de entrenar! Tu sesión de hoy te espera."
        } else {
            content.title = "KPKN Fit"
            content.body = "Recuerda descansar y recuperarte. Tu cuerpo lo agradecerá."
        }
        content.sound = .default

        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Failed to schedule \(type) reminder: \(error.localizedDescription)")
            }
        }
    }

    private func parseHour(_ time: String) -> Int {
        Int(time.split(separator: ":").first ?? "18") ?? 18
    }

    private func parseMin(_ time: String) -> Int {
        Int(time.split(separator: ":").dropFirst().first ?? "0") ?? 0
    }
}
