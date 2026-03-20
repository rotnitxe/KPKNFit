import type { CoreReminderSettings, WorkoutOverview } from '@kpkn/shared-types';
import type { SavedNutritionEntry } from '../types/nutrition';
import {
  rescheduleCoreNotificationsFromState,
  rescheduleCoreNotificationsFromStorage,
  scheduleRestTimerNotification,
  cancelRestTimerNotification,
  syncNotificationPermissionState,
} from './mobileNotificationService';

export interface NotificationState {
  settings: CoreReminderSettings;
  nutritionLogs: SavedNutritionEntry[];
  workoutOverview: WorkoutOverview | null;
}

export async function cancelNotificationIds(_ids: number[]): Promise<void> {
  await cancelRestTimerNotification();
}

export const requestPermissions = async (): Promise<boolean> => {
  const snapshot = await syncNotificationPermissionState();
  return snapshot.granted;
};

export const cancelPendingNotifications = async () => {
  await cancelRestTimerNotification();
};

export const setupNotificationChannels = async () => {
  await syncNotificationPermissionState();
};

export async function scheduleRestEndNotification(durationSeconds: number): Promise<void> {
  await scheduleRestTimerNotification(durationSeconds);
}

export async function cancelRestEndNotification(): Promise<void> {
  await cancelRestTimerNotification();
}

export async function cancelMissedWorkoutNotificationForToday(_state: NotificationState): Promise<void> {
  await rescheduleCoreNotificationsFromStorage();
}

export async function rescheduleAllNotifications(state: NotificationState): Promise<void> {
  await rescheduleCoreNotificationsFromState(state);
}

export const scheduleWorkoutReminders = async (_programs: unknown[], _settings: unknown) => {
  await rescheduleCoreNotificationsFromStorage();
};

export const triggerRestEndNotification = async () => {
  await scheduleRestTimerNotification(1);
};

export const scheduleQuestionnaireNotification = async (_logId: string, sessionName: string) => {
  await scheduleRestTimerNotification(2, `Post-entreno ${sessionName}`);
};

export const scheduleBedtimeReminder = async (_bedTime: string) => {
  await scheduleRestTimerNotification(2, 'Hora de dormir');
};
