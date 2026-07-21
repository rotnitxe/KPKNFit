import Foundation
import UIKit
import UserNotifications
import os.log

public enum CompetitionReminderManager {

    public static let channelCompetition = "competition_reminders"
    public static let extraRecordId = "competition_record_id"
    public static let extraType = "competition_reminder_type"
    public static let extraTitle = "competition_title"
    public static let extraDate = "competition_date"

    public static let typeWeek = "week"
    public static let type48h = "48h"
    public static let typeStart = "start"
    public static let typePostResult = "post_result"

    public static func registerCategories() {
        let category = UNNotificationCategory(
            identifier: channelCompetition,
            actions: [],
            intentIdentifiers: [],
            options: []
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    public static func schedule(record: CompetitionRecord) {
        cancel(recordId: record.id)
        guard let eventAt = record.eventDate?.toEventDateTime(startTime: record.startTime) else { return }

        scheduleIfEnabled(record: record, type: typeWeek, triggerAt: eventAt.addingTimeInterval(-7 * 24 * 3600), enabled: record.reminderOneWeekEnabled)
        scheduleIfEnabled(record: record, type: type48h, triggerAt: eventAt.addingTimeInterval(-48 * 3600), enabled: record.reminder48hEnabled)
        scheduleIfEnabled(record: record, type: typeStart, triggerAt: eventAt, enabled: record.reminderStartEnabled)

        if !(record.startTime ?? "").isEmpty {
            scheduleIfEnabled(record: record, type: typePostResult, triggerAt: eventAt.addingTimeInterval(5 * 3600), enabled: true)
        }
    }

    public static func cancel(recordId: String) {
        let identifiers = [typeWeek, type48h, typeStart, typePostResult].map { "competition_\(recordId)_\($0)" }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: identifiers)
    }

    private static func scheduleIfEnabled(record: CompetitionRecord, type: String, triggerAt: Date, enabled: Bool) {
        guard enabled else { return }
        guard triggerAt > Date() else { return }

        let content = UNMutableNotificationContent()
        content.title = "KPKN · Competición"
        content.sound = .default
        content.userInfo = [
            extraRecordId: record.id,
            extraType: type,
            extraTitle: record.title,
            extraDate: record.eventDate ?? ""
        ]

        let body: String = {
            switch type {
            case typeWeek: return "Queda una semana para \(record.title)."
            case type48h: return "Quedan 48 horas para \(record.title)."
            case typeStart: return "\(record.title) comienza ahora."
            case typePostResult: return "¿Cómo te fue en la competición?"
            default: return "Revisa tu registro de competición."
            }
        }()
        if let date = record.eventDate, !date.isEmpty {
            content.body = body + " Fecha: \(date)."
        } else {
            content.body = body
        }

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: triggerAt.timeIntervalSinceNow, repeats: false)
        let request = UNNotificationRequest(
            identifier: "competition_\(record.id)_\(type)",
            content: content,
            trigger: trigger
        )

        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                os_log("Failed to schedule competition reminder: %{public}@",
                       log: .default, type: .error, error.localizedDescription)
            }
        }
    }

    public static func handleNotification(userInfo: [AnyHashable: Any]) {
        let recordId = userInfo[extraRecordId] as? String ?? ""
        let title = (userInfo[extraTitle] as? String)?.nilIfBlank ?? "Competición"
        let date = userInfo[extraDate] as? String ?? ""
        let type = userInfo[extraType] as? String ?? ""

        if type == typePostResult && !recordId.isEmpty {
            Task {
                let entity = await KpknDatabase.instance().competitionRecordDao.getById(id: recordId)
                if entity?.status != CompetitionRecordStatus.PLANNED.rawValue { return }
                showNotification(title: title, body: makeBody(type: type, title: title, date: date), type: type, recordId: recordId)
            }
        } else {
            showNotification(title: title, body: makeBody(type: type, title: title, date: date), type: type, recordId: recordId)
        }
    }

    private static func makeBody(type: String, title: String, date: String) -> String {
        let base: String = {
            switch type {
            case typeWeek: return "Queda una semana para \(title)."
            case type48h: return "Quedan 48 horas para \(title)."
            case typeStart: return "\(title) comienza ahora."
            case typePostResult: return "¿Cómo te fue en la competición?"
            default: return "Revisa tu registro de competición."
            }
        }()
        return date.isEmpty ? base : base + " Fecha: \(date)."
    }

    private static func showNotification(title: String, body: String, type: String, recordId: String) {
        let content = UNMutableNotificationContent()
        content.title = "KPKN · Competición"
        content.body = body
        content.sound = .default
        content.userInfo = [extraRecordId: recordId, extraType: type]

        let request = UNNotificationRequest(
            identifier: "competition_delivered_\(recordId)_\(type)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { _ in }
    }
}

private extension String {
    var nilIfBlank: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }

    func toEventDateTime(startTime: String?) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: self) else { return nil }

        let time: Date
        if let st = startTime {
            let tf = DateFormatter()
            tf.locale = Locale(identifier: "en_US_POSIX")
            tf.dateFormat = "HH:mm"
            if let parsed = tf.date(from: st) {
                time = parsed
            } else {
                time = Calendar.current.date(from: DateComponents(hour: 9, minute: 0))!
            }
        } else {
            time = Calendar.current.date(from: DateComponents(hour: 9, minute: 0))!
        }

        let calendar = Calendar.current
        let dateComponents = calendar.dateComponents([.year, .month, .day], from: date)
        let timeComponents = calendar.dateComponents([.hour, .minute], from: time)
        return calendar.date(from: DateComponents(
            year: dateComponents.year,
            month: dateComponents.month,
            day: dateComponents.day,
            hour: timeComponents.hour,
            minute: timeComponents.minute
        ))
    }
}
