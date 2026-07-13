jest.mock('../../storage/mmkv', () => ({
  appStorage: { getString: jest.fn(), set: jest.fn(), delete: jest.fn() },
  setJsonValue: jest.fn(),
  getJsonValue: jest.fn(() => null),
}));

jest.mock('../../modules/background', () => ({
  isBackgroundModuleAvailable: false,
  backgroundModule: {
    schedulePeriodicSync: jest.fn(),
    runImmediateSync: jest.fn(),
    getStatus: jest.fn(async () => ({
      lastDispatchAtMs: null,
      lastCompletionAtMs: null,
      lastResult: 'idle',
      lastError: null,
      runAttemptCount: 0,
    })),
  },
}));

jest.mock('../../modules/widgets', () => ({
  isWidgetModuleAvailable: false,
  widgetModule: {
    reloadWidget: jest.fn(),
    getStatus: jest.fn(async () => ({
      lastSyncAtMs: null,
      lastReloadAtMs: null,
      lastError: 'widget-module-unavailable',
      stale: true,
      staleReason: 'widget-module-unavailable',
      source: 'unknown',
    })),
  },
}));

import { backgroundModule } from '../../modules/background';
import { widgetModule } from '../../modules/widgets';
import { useCutoverStore } from '../../stores/cutoverStore';

describe('cutoverStore.runOperationalSweep', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useCutoverStore.setState({
      stage: 'needs-work',
      systemChecklist: null,
      manualSignoff: {
        legacyUpgradeVerified: false,
        offlineColdStartVerified: false,
        widgetsVerified: false,
        backgroundVerified: false,
        notificationsVerified: false,
        nutritionFlowVerified: false,
      },
      operationalSnapshot: {
        notificationPermission: 'blocked',
        widgetModuleAvailable: false,
        backgroundModuleAvailable: false,
        migrationBridgeAvailable: false,
        localAiModuleAvailable: false,
        widgetStale: true,
        backgroundLastResult: 'idle',
        nutritionLogCount: 0,
        templateCount: 0,
        wellbeingHasData: false,
        lastSweepAt: null,
      },
      lastCheckedAt: null,
      notice: null,
      refresh: jest.fn().mockResolvedValue(undefined),
    } as any);
  });

  it('reports unavailable native modules honestly instead of claiming success', async () => {
    await useCutoverStore.getState().runOperationalSweep();

    expect(backgroundModule.schedulePeriodicSync).not.toHaveBeenCalled();
    expect(backgroundModule.runImmediateSync).not.toHaveBeenCalled();
    expect(widgetModule.reloadWidget).not.toHaveBeenCalled();
    expect(useCutoverStore.getState().notice).toContain('Módulo background no disponible.');
    expect(useCutoverStore.getState().notice).toContain('Módulo widgets no disponible.');
    expect(useCutoverStore.getState().notice).not.toContain('widgets recargados');
  });
});
