import Foundation
import UIKit
import UserNotifications

public final class LoopNotificationManager {

    public static let channelLoop = "program_loop_events"

    public init() {}

    public func registerCategories() {
        UNUserNotificationCenter.current().setNotificationCategories([])
    }

    public func notifyLoopActive(programName: String, loopTitle: String) {
        let content = UNMutableNotificationContent()
        content.title = "Loop activo: \(loopTitle)"
        content.body = "\(programName) tiene una semana de loop activa. Revisa el roadmap y programa sus sesiones."
        content.sound = .default
        content.userInfo = ["deepLink": "programs"]

        let request = UNNotificationRequest(
            identifier: "loop_\(loopTitle)",
            content: content,
            trigger: nil
        )

        UNUserNotificationCenter.current().add(request) { _ in }
    }
}
