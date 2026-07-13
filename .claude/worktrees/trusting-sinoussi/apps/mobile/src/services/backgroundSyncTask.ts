import { backgroundModule } from '../modules/background';
import { loadSavedNutritionLogs } from './mobilePersistenceService';
import { rescheduleCoreNotificationsFromState } from './mobileNotificationService';
import { markWidgetStateStale, syncNutritionWidgetState, syncWorkoutWidgetState } from './widgetSyncService';
import { persistBackgroundSyncStatus } from './mobileDomainStateService';
import { loadWorkoutRuntimeState } from './workoutStateService';

export default async function runBackgroundSyncTask() {
  const startedAt = new Date().toISOString();
  persistBackgroundSyncStatus({
    lastAttemptAt: startedAt,
    lastCompletedAt: null,
    lastResult: 'running',
    lastError: null,
  });

  try {
    const [nutritionLogs, workoutState] = await Promise.all([
      loadSavedNutritionLogs(),
      loadWorkoutRuntimeState(),
    ]);

    await syncNutritionWidgetState(nutritionLogs, 'background');
    await syncWorkoutWidgetState(workoutState.overview, 'background');
    await rescheduleCoreNotificationsFromState({
      settings: workoutState.reminderSettings,
      nutritionLogs,
      workoutOverview: workoutState.overview,
    });

    const finishedAt = new Date().toISOString();
    persistBackgroundSyncStatus({
      lastAttemptAt: startedAt,
      lastCompletedAt: finishedAt,
      lastResult: 'success',
      lastError: null,
    });
    await backgroundModule.reportTaskResult({ success: true });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'background-sync-failed';
    const finishedAt = new Date().toISOString();
    persistBackgroundSyncStatus({
      lastAttemptAt: startedAt,
      lastCompletedAt: finishedAt,
      lastResult: 'failure',
      lastError: message,
    });
    await markWidgetStateStale(message, 'background');
    await backgroundModule.reportTaskResult({ success: false, error: message });
    throw error;
  }
}
