import Foundation
import UIKit
import UserNotifications

public enum TimerAction {
    case completeSet
    case skipTimer
    case addTime
    case subtractTime
}

public enum TimerNotificationActionReceiver {

    public static let actionCompleteSet = "TIMER_COMPLETE_SET"
    public static let actionSkipTimer = "TIMER_SKIP"
    public static let actionAddTime = "TIMER_ADD_TIME"
    public static let actionSubtractTime = "TIMER_SUBTRACT_TIME"

    public static let categoryRestTimer = "REST_TIMER"

    public static func registerActions() {
        let complete = UNNotificationAction(identifier: actionCompleteSet, title: "Completar serie", options: [])
        let skip = UNNotificationAction(identifier: actionSkipTimer, title: "Saltar", options: [])
        let add = UNNotificationAction(identifier: actionAddTime, title: "+15s", options: [])
        let subtract = UNNotificationAction(identifier: actionSubtractTime, title: "-15s", options: [])

        let category = UNNotificationCategory(
            identifier: categoryRestTimer,
            actions: [complete, skip, add, subtract],
            intentIdentifiers: [],
            options: []
        )

        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    public static func handle(response: UNNotificationResponse) {
        guard ActiveWorkoutHolder.shared.isActive() else { return }
 
        switch response.actionIdentifier {
        case actionCompleteSet:
            ActiveWorkoutHolder.shared.handleAction(.completeSet)
        case actionSkipTimer:
            ActiveWorkoutHolder.shared.handleAction(.skipTimer)
        case actionAddTime:
            ActiveWorkoutHolder.shared.handleAction(.addTime)
        case actionSubtractTime:
            ActiveWorkoutHolder.shared.handleAction(.subtractTime)
        default:
            break
        }
    }
}
