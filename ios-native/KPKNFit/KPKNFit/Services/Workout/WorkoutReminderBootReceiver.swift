import Foundation
import UIKit
import os.log

public enum WorkoutReminderBootReceiver {

    public static func restoreRemindersAfterLaunch() {
        Task {
            let db = KpknDatabase.instance()
            let settings = await db.settingsDao.get()?.toSettings() ?? AppSettings()

            let reminderManager = WorkoutReminderManager.shared
            reminderManager.createChannels()

            if settings.workoutReminderEnabled {
                reminderManager.scheduleWorkoutReminder(time: settings.workoutReminderTime)
            } else {
                reminderManager.cancelWorkoutReminder()
            }

                if settings.sleepReminderEnabled {
                    reminderManager.scheduleSleepReminder(time: settings.sleepReminderTime)
                } else {
                    reminderManager.cancelSleepReminder()
                }
        }
    }
}
