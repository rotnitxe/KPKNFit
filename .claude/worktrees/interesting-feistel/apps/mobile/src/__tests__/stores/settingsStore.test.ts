jest.mock('../../services/mobileDomainStateService', () => ({
  getStoredSettingsSource: jest.fn(),
  patchStoredSettingsRaw: jest.fn(),
  readStoredSettingsRaw: jest.fn(),
}));

jest.mock('../../services/mobileNotificationService', () => ({
  rescheduleCoreNotificationsFromStorage: jest.fn(),
}));

import {
  getStoredSettingsSource,
  patchStoredSettingsRaw,
  readStoredSettingsRaw,
} from '../../services/mobileDomainStateService';
import { rescheduleCoreNotificationsFromStorage } from '../../services/mobileNotificationService';
import { useSettingsStore } from '../../stores/settingsStore';

describe('settingsStore', () => {
  let storageState: Record<string, unknown>;

  beforeEach(() => {
    storageState = {
      userName: 'Valen',
      age: 32,
      weight: 78,
      height: 174,
      gender: 'male',
      activityLevel: 'moderate',
      defaultRestSeconds: 75,
      autoStartTimer: false,
      remindersEnabled: false,
      mealRemindersEnabled: true,
      fallbackEnabled: false,
      appTheme: 'default',
      tabBarStyle: 'default',
    };

    jest.clearAllMocks();
    (getStoredSettingsSource as jest.Mock).mockReturnValue('migration-fallback');
    (readStoredSettingsRaw as jest.Mock).mockImplementation(() => storageState);
    (patchStoredSettingsRaw as jest.Mock).mockImplementation((patch: Record<string, unknown>) => {
      storageState = { ...storageState, ...patch };
      return storageState;
    });
    useSettingsStore.setState({
      status: 'idle',
      summary: null,
      notice: null,
    } as any);
  });

  it('normalizes legacy aliases and applies safe defaults on hydration', async () => {
    await useSettingsStore.getState().hydrateFromMigration();

    const summary = useSettingsStore.getState().summary;
    expect(summary?.username).toBe('Valen');
    expect(summary?.userVitals).toMatchObject({
      age: 32,
      weight: 78,
      height: 174,
      gender: 'male',
      activityLevel: 'moderate',
    });
    expect(summary?.restTimerDefaultSeconds).toBe(75);
    expect(summary?.restTimerAutoStart).toBe(false);
    expect(summary?.appTheme).toBe('default');
    expect(summary?.tabBarStyle).toBe('default');
    expect(summary?.weightUnit).toBe('kg');
  });

  it('persists toggles immediately and reschedules notifications', async () => {
    await useSettingsStore.getState().hydrateFromMigration();

    await useSettingsStore.getState().toggleWorkoutReminders();

    expect(patchStoredSettingsRaw).toHaveBeenCalledWith({ remindersEnabled: true });
    expect(rescheduleCoreNotificationsFromStorage).toHaveBeenCalled();
    expect(useSettingsStore.getState().summary?.remindersEnabled).toBe(true);
    expect(useSettingsStore.getState().notice).toContain('activados');
  });

  it('updates shell settings through a single patch round-trip', async () => {
    await useSettingsStore.getState().updateSettings({
      appTheme: 'light',
      tabBarStyle: 'icons-only',
      weightUnit: 'lbs',
    });

    expect(patchStoredSettingsRaw).toHaveBeenCalledWith({
      appTheme: 'light',
      tabBarStyle: 'icons-only',
      weightUnit: 'lbs',
    });
    expect(useSettingsStore.getState().summary?.appTheme).toBe('light');
    expect(useSettingsStore.getState().summary?.tabBarStyle).toBe('icons-only');
    expect(useSettingsStore.getState().summary?.weightUnit).toBe('lbs');
  });
});
