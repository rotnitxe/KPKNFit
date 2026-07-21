import Foundation
import UIKit
import UserNotifications
import os.log

public final class NutritionNotificationManager {

    public init() {}

    public func scheduleMeasurementReminder(_ dateIso: String, hour: Int, minute: Int) {
        Self.scheduleMeasurementReminder(dateIso: dateIso, hour: hour, minute: minute)
    }

    public func cancelMeasurementReminder() {
        Self.cancelMeasurementReminder()
    }

    public static let channelMeals = "nutrition_meals"
    public static let channelMacros = "nutrition_macros"
    public static let channelMeasurement = "nutrition_measurement"

    private static let notifBreakfast = 5001
    private static let notifLunch = 5002
    private static let notifDinner = 5003
    private static let notifMacroDeficit = 5010
    private static let notifMeasurement = 5020

    private static let reqBreakfast = 6001
    private static let reqLunch = 6002
    private static let reqDinner = 6003
    private static let reqMacroCheck = 6010
    private static let reqMeasurement = 6020

    public static let extraNotifType = "notif_type"
    public static let typeBreakfast = "breakfast"
    public static let typeLunch = "lunch"
    public static let typeDinner = "dinner"
    public static let typeMacroCheck = "macro_check"
    public static let typeMeasurement = "measurement"

    public static func registerCategories() {
        let mealsCategory = UNNotificationCategory(
            identifier: channelMeals,
            actions: [],
            intentIdentifiers: [],
            options: []
        )
        let macrosCategory = UNNotificationCategory(
            identifier: channelMacros,
            actions: [],
            intentIdentifiers: [],
            options: []
        )
        let measurementCategory = UNNotificationCategory(
            identifier: channelMeasurement,
            actions: [],
            intentIdentifiers: [],
            options: []
        )
        UNUserNotificationCenter.current().setNotificationCategories([
            mealsCategory, macrosCategory, measurementCategory
        ])
    }

    // MARK: — Meal Reminders

    public static func scheduleMealReminders(
        breakfastTime: String = "08:00",
        lunchTime: String = "13:00",
        dinnerTime: String = "20:00"
    ) {
        scheduleDaily(requestCode: reqBreakfast, hour: parseHour(breakfastTime), minute: parseMin(breakfastTime), type: typeBreakfast)
        scheduleDaily(requestCode: reqLunch, hour: parseHour(lunchTime), minute: parseMin(lunchTime), type: typeLunch)
        scheduleDaily(requestCode: reqDinner, hour: parseHour(dinnerTime), minute: parseMin(dinnerTime), type: typeDinner)
    }

    public static func cancelMealReminders() {
        let ids = [typeBreakfast, typeLunch, typeDinner].map { "nutrition_\($0)" }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
    }

    // MARK: — Macro Check

    public static func scheduleDailyMacroCheck(hour: Int = 20, minute: Int = 30) {
        scheduleDaily(requestCode: reqMacroCheck, hour: hour, minute: minute, type: typeMacroCheck)
    }

    public static func cancelDailyMacroCheck() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["nutrition_\(typeMacroCheck)"])
    }

    // MARK: — Measurement Reminder

    public static func scheduleMeasurementReminder(dateIso: String, hour: Int = 9, minute: Int = 0) {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: dateIso) else { return }

        var components = Calendar.current.dateComponents([.year, .month, .day], from: date)
        components.hour = hour
        components.minute = minute
        components.second = 0

        guard let triggerAt = Calendar.current.date(from: components) else { return }
        guard triggerAt > Date() else { return }

        let content = UNMutableNotificationContent()
        content.title = "Recordatorio de medición"
        content.body = "Hoy es día de medición corporal."
        content.sound = .default
        content.userInfo = [extraNotifType: typeMeasurement]

        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        let request = UNNotificationRequest(
            identifier: "nutrition_\(typeMeasurement)",
            content: content,
            trigger: trigger
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                os_log("Failed to schedule measurement reminder: %{public}@",
                       log: .default, type: .error, error.localizedDescription)
            }
        }
    }

    public static func cancelMeasurementReminder() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["nutrition_\(typeMeasurement)"])
    }

    // MARK: — Immediate Notifications

    public static func sendMacroDeficitAlert(totals: DailyMacroTotals, goals: MacroGoals) {
        var hasCalorieExcess = false
        var deficitItems: [String] = []

        let protPct = goals.proteinGoal > 0 ? totals.protein / Double(goals.proteinGoal) : 1.0
        let carbPct = goals.carbGoal > 0 ? totals.carbs / Double(goals.carbGoal) : 1.0
        let fatPct = goals.fatGoal > 0 ? totals.fats / Double(goals.fatGoal) : 1.0
        let calPct = goals.calorieGoal > 0 ? totals.calories / Double(goals.calorieGoal) : 1.0

        if protPct < 0.70 {
            deficitItems.append("Proteína: \(Int(totals.protein))g de \(goals.proteinGoal)g (\(Int(protPct * 100))%)")
        }
        if carbPct < 0.60 {
            deficitItems.append("Carbohidratos: \(Int(totals.carbs))g de \(goals.carbGoal)g (\(Int(carbPct * 100))%)")
        }
        if fatPct < 0.60 {
            deficitItems.append("Grasas: \(Int(totals.fats))g de \(goals.fatGoal)g (\(Int(fatPct * 100))%)")
        }
        if calPct > 1.10 {
            hasCalorieExcess = true
            deficitItems.append("Calorías: \(Int(totals.calories)) de \(goals.calorieGoal) — excedido")
        }

        guard !deficitItems.isEmpty else { return }

        let body = deficitItems.joined(separator: "\n")
        let title = hasCalorieExcess ? "Exceso de calorías" : "Macros incompletos"

        let content = UNMutableNotificationContent()
        content.title = title
        content.body = deficitItems.first ?? body
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "nutrition_macro_\(notifMacroDeficit)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                os_log("Could not send macro deficit alert: %{public}@",
                       log: .default, type: .error, error.localizedDescription)
            }
        }
    }

    public static func sendMeasurementReminderNotification(nextDate: String) {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "es_ES")
        formatter.dateStyle = .long
        formatter.timeStyle = .none

        let label: String
        if let date = ISO8601DateFormatter().date(from: nextDate) ?? {
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = "yyyy-MM-dd"
            return f.date(from: nextDate)
        }() {
            label = formatter.string(from: date)
        } else {
            label = nextDate
        }

        let content = UNMutableNotificationContent()
        content.title = "Medición corporal"
        content.body = "Recuerda realizar tu medición corporal: \(label)"
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "nutrition_measurement_\(notifMeasurement)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                os_log("Could not send measurement reminder: %{public}@",
                       log: .default, type: .error, error.localizedDescription)
            }
        }
    }

    // MARK: — Internal

    private static func scheduleDaily(requestCode: Int, hour: Int, minute: Int, type: String) {
        var components = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        components.hour = hour
        components.minute = minute
        components.second = 0

        guard var triggerAt = Calendar.current.date(from: components) else { return }
        if triggerAt <= Date() {
            triggerAt = Calendar.current.date(byAdding: .day, value: 1, to: triggerAt) ?? triggerAt
        }

        let content = UNMutableNotificationContent()
        content.title = "KPKN Nutrición"
        content.userInfo = [extraNotifType: type]

        switch type {
        case typeBreakfast:
            content.body = "¡Hora del desayuno! Registra tus alimentos."
        case typeLunch:
            content.body = "¡Hora del almuerzo! Registra tus alimentos."
        case typeDinner:
            content.body = "¡Hora de la cena! Registra tus alimentos."
        case typeMacroCheck:
            content.body = "Revisa tus macros del día."
        default:
            content.body = "Recordatorio de nutrición."
        }

        content.sound = .default

        let trigger = UNCalendarNotificationTrigger(dateMatching: Calendar.current.dateComponents([.hour, .minute], from: triggerAt), repeats: true)
        let request = UNNotificationRequest(
            identifier: "nutrition_\(type)",
            content: content,
            trigger: trigger
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                os_log("Failed to schedule %{public}@: %{public}@",
                       log: .default, type: .error, type, error.localizedDescription)
            }
        }
    }

    private static func parseHour(_ time: String) -> Int {
        Int(time.split(separator: ":").first ?? "8") ?? 8
    }

    private static func parseMin(_ time: String) -> Int {
        Int(time.split(separator: ":").dropFirst().first ?? "0") ?? 0
    }
}

// MARK: — Alert Receiver (called from AppDelegate when notification arrives)

public enum NutritionAlertReceiver {

    public static func handle(userInfo: [AnyHashable: Any]) {
        guard let type = userInfo[NutritionNotificationManager.extraNotifType] as? String else { return }

        switch type {
        case NutritionNotificationManager.typeBreakfast:
            sendMealReminder(text: "¡Hora del desayuno!", type: type)
        case NutritionNotificationManager.typeLunch:
            sendMealReminder(text: "¡Hora del almuerzo!", type: type)
        case NutritionNotificationManager.typeDinner:
            sendMealReminder(text: "¡Hora de la cena!", type: type)
        case NutritionNotificationManager.typeMacroCheck:
            checkAndSendMacroAlert()
        case NutritionNotificationManager.typeMeasurement:
            NutritionNotificationManager.sendMeasurementReminderNotification(nextDate: todayISO())
        default:
            break
        }
    }

    private static func sendMealReminder(text: String, type: String) {
        let repo = NutritionRepository.shared
        let today = todayISO()
        let todayLogs = repo.nutritionLogs.filter { $0.date.prefix(10) == today }

        let mealType: MealType? = {
            switch type {
            case NutritionNotificationManager.typeBreakfast: return .BREAKFAST
            case NutritionNotificationManager.typeLunch: return .LUNCH
            case NutritionNotificationManager.typeDinner: return .DINNER
            default: return nil
            }
        }()
        guard let mt = mealType else { return }
        if todayLogs.contains(where: { $0.mealType == mt }) { return }

        let content = UNMutableNotificationContent()
        content.title = "KPKN Nutrición"
        content.body = text
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "nutrition_alert_\(type)_\(today)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { _ in }
    }

    private static func checkAndSendMacroAlert() {
        let nutritionRepo = NutritionRepository.shared
        let programRepo = ProgramRepository.shared
        let today = todayISO()

        let todayLogs = nutritionRepo.nutritionLogs.filter {
            $0.date.prefix(10) == today && $0.status != .PLANNED
        }
        guard !todayLogs.isEmpty else { return }

        let totals = computeDailyTotals(logs: todayLogs)
        let settings = programRepo.settings
        let goals = deriveMacroGoals(settings: settings)

        NutritionNotificationManager.sendMacroDeficitAlert(totals: totals, goals: goals)
    }

    private static func todayISO() -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }
}
