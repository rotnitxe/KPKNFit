import runBackgroundSyncTask from '../backgroundSyncTask';
import { backgroundModule } from '../../modules/background';
import { loadSavedNutritionLogs } from '../mobilePersistenceService';
import { loadWorkoutRuntimeState } from '../workoutStateService';
import { syncNutritionWidgetState, syncWorkoutWidgetState } from '../widgetSyncService';
import { rescheduleCoreNotificationsFromState } from '../mobileNotificationService';
import { persistBackgroundSyncStatus } from '../mobileDomainStateService';

jest.mock('../../modules/background', () => ({
  backgroundModule: {
    reportTaskResult: jest.fn().mockResolvedValue(undefined),
  },
}));

jest.mock('../mobilePersistenceService', () => ({
  loadSavedNutritionLogs: jest.fn().mockResolvedValue([]),
}));

jest.mock('../workoutStateService', () => ({
  loadWorkoutRuntimeState: jest.fn().mockResolvedValue({
    overview: null,
    reminderSettings: {},
  }),
}));

jest.mock('../widgetSyncService', () => ({
  syncNutritionWidgetState: jest.fn().mockResolvedValue(undefined),
  syncWorkoutWidgetState: jest.fn().mockResolvedValue(undefined),
  markWidgetStateStale: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../mobileNotificationService', () => ({
  rescheduleCoreNotificationsFromState: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../mobileDomainStateService', () => ({
  persistBackgroundSyncStatus: jest.fn(),
}));

describe('backgroundSyncTask', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should run all sync steps and report success', async () => {
    const mockLogs = [{ id: 1 }];
    const mockWorkoutState = { overview: { id: 1 }, reminderSettings: { enabled: true } };
    (loadSavedNutritionLogs as jest.Mock).mockResolvedValue(mockLogs);
    (loadWorkoutRuntimeState as jest.Mock).mockResolvedValue(mockWorkoutState);

    await runBackgroundSyncTask();

    expect(loadSavedNutritionLogs).toHaveBeenCalled();
    expect(loadWorkoutRuntimeState).toHaveBeenCalled();
    expect(syncNutritionWidgetState).toHaveBeenCalledWith(mockLogs, 'background');
    expect(syncWorkoutWidgetState).toHaveBeenCalledWith(mockWorkoutState.overview, 'background');
    expect(rescheduleCoreNotificationsFromState).toHaveBeenCalled();
    expect(persistBackgroundSyncStatus).toHaveBeenCalledWith(
      expect.objectContaining({ lastResult: 'success' })
    );
    expect(backgroundModule.reportTaskResult).toHaveBeenCalledWith({ success: true });
  });

  it('should report failure if any step fails', async () => {
    (loadSavedNutritionLogs as jest.Mock).mockRejectedValue(new Error('DB Error'));

    await expect(runBackgroundSyncTask()).rejects.toThrow('DB Error');

    expect(persistBackgroundSyncStatus).toHaveBeenCalledWith(
      expect.objectContaining({
        lastResult: 'failure',
        lastError: 'DB Error',
      })
    );
    expect(backgroundModule.reportTaskResult).toHaveBeenCalledWith({
      success: false,
      error: 'DB Error',
    });
  });
});
