import { syncNutritionWidgetState, syncWorkoutWidgetState, markWidgetStateStale } from '../widgetSyncService';
import { widgetModule } from '../../modules/widgets';
import { persistWidgetSyncStatus, readWidgetSyncStatus } from '../mobileDomainStateService';

jest.mock('../../modules/widgets', () => ({
  widgetModule: {
    syncDashboardState: jest.fn().mockResolvedValue(undefined),
    markStale: jest.fn().mockResolvedValue(undefined),
    getStatus: jest.fn().mockResolvedValue({
      lastSyncAtMs: Date.now(),
      lastReloadAtMs: Date.now(),
      lastError: null,
      stale: false,
      source: 'foreground',
    }),
  },
}));

jest.mock('../mobileDomainStateService', () => ({
  persistWidgetSyncStatus: jest.fn(),
  readWidgetSyncStatus: jest.fn().mockReturnValue({
    stale: false,
    lastAttemptAt: null,
    lastSuccessfulSyncAt: null,
    lastError: null,
    source: 'unknown',
  }),
}));

describe('widgetSyncService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should sync nutrition snapshot correctly', async () => {
    const today = new Date();
    const todayStr = `${today.getFullYear()}-${(today.getMonth() + 1).toString().padStart(2, '0')}-${today.getDate().toString().padStart(2, '0')}`;
    const logs: any[] = [
      {
        createdAt: `${todayStr}T12:00:00Z`,
        totals: { calories: 2000, protein: 150, carbs: 200, fats: 60 },
      },
    ];

    await syncNutritionWidgetState(logs, 'foreground');

    expect(widgetModule.syncDashboardState).toHaveBeenCalledWith(
      expect.objectContaining({
        nutritionCaloriesToday: 2000,
        nutritionProteinToday: 150,
        nutritionCarbsToday: 200,
        nutritionFatsToday: 60,
        widgetSyncSource: 'foreground',
      })
    );
    expect(persistWidgetSyncStatus).toHaveBeenCalled();
  });

  it('should sync workout snapshot correctly', async () => {
    const overview: any = {
      activeProgramName: 'Push Pull Leg',
      nextSession: { name: 'Push A' },
      completedSetsThisWeek: 12,
      plannedSetsThisWeek: 20,
      battery: { overall: 85, cns: 80, muscular: 90, spinal: 85 },
    };

    await syncWorkoutWidgetState(overview, 'foreground');

    expect(widgetModule.syncDashboardState).toHaveBeenCalledWith(
      expect.objectContaining({
        nextSessionLabel: 'Push A',
        nextSessionProgramName: 'Push Pull Leg',
        effectiveVolumeToday: 12,
        effectiveVolumePlanned: 20,
        augeBatteryScore: 85,
        widgetSyncSource: 'foreground',
      })
    );
  });

  it('should mark widget stale on failure', async () => {
    (widgetModule.syncDashboardState as jest.Mock).mockRejectedValueOnce(new Error('Sync failed'));

    await syncWorkoutWidgetState(null, 'background');

    expect(persistWidgetSyncStatus).toHaveBeenCalledWith(
      expect.objectContaining({
        stale: true,
        lastError: 'Sync failed',
        source: 'background',
      })
    );
    expect(widgetModule.markStale).toHaveBeenCalledWith('Sync failed');
  });

  it('should manually mark stale', async () => {
    await markWidgetStateStale('user-request', 'foreground');

    expect(widgetModule.markStale).toHaveBeenCalledWith('user-request');
    expect(persistWidgetSyncStatus).toHaveBeenCalledWith(
      expect.objectContaining({
        stale: true,
        lastError: 'user-request',
      })
    );
  });
});
