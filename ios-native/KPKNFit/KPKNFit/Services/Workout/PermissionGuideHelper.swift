import Foundation
import UIKit
import UserNotifications

public struct PermissionState {
    public let batteryOptimizationOk: Bool
    public let exactAlarmOk: Bool

    public var needsAttention: Bool { !batteryOptimizationOk || !exactAlarmOk }
    public var allOk: Bool { batteryOptimizationOk && exactAlarmOk }
}

public enum PermissionGuideHelper {

    public static func isBatteryOptimizationIgnored() -> Bool {
        UIApplication.shared.backgroundRefreshStatus == .available
    }

    public static func isExactAlarmGranted() -> Bool {
        true
    }

    public static func getPermissionState() -> PermissionState {
        PermissionState(
            batteryOptimizationOk: isBatteryOptimizationIgnored(),
            exactAlarmOk: isExactAlarmGranted()
        )
    }

    public static func openBatteryOptimizationSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }

    public static func openExactAlarmSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }

    public static func openAllPermissionSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }
}
