import {
  readStoredSettingsRaw,
  getStoredSettingsSource,
  persistStoredSettingsRaw,
  patchStoredSettingsRaw,
  readStoredWellbeingPayload,
  getStoredWellbeingSource,
  persistStoredWellbeingPayload,
  patchStoredWellbeingPayload,
  readStoredMealTemplatesRaw,
  getStoredMealTemplateSource,
  persistStoredMealTemplatesRaw,
  readNotificationPermissionSnapshot,
  persistNotificationPermissionSnapshot,
  readWidgetSyncStatus,
  persistWidgetSyncStatus,
  readBackgroundSyncStatus,
  persistBackgroundSyncStatus,
  type NotificationPermissionSnapshot,
  type WidgetSyncStatus,
  type BackgroundSyncStatus,
} from '../../services/mobileDomainStateService';
import { appStorage, getJsonValue, setJsonValue } from '../../storage/mmkv';

// The global jest.setup.js already mocks react-native-mmkv with an in-memory Map.
// For these tests we need finer control, so we use the real mock's Map-backed implementation.

// Helper to clear all MMKV storage between tests
function clearStorage() {
  (appStorage.clearAll as jest.Mock)?.();
  // Also clear the mock's call history
  jest.clearAllMocks();
}

describe('mobileDomainStateService', () => {
  beforeEach(() => {
    clearStorage();
  });

  describe('settings', () => {
    it('should return "defaults" source when no settings stored', () => {
      expect(getStoredSettingsSource()).toBe('defaults');
    });

    it('should return default settings when nothing stored', () => {
      const settings = readStoredSettingsRaw();
      expect(settings).toBeDefined();
      expect(settings).toHaveProperty('apiProvider', 'gemini');
      expect(settings).toHaveProperty('fallbackEnabled', true);
      expect(settings).toHaveProperty('workoutLoggerMode', 'pro');
      expect(settings).toHaveProperty('appTheme', 'default');
      expect(settings).toHaveProperty('tabBarStyle', 'default');
    });

    it('should persist and read back settings', () => {
      const custom = { apiProvider: 'gemini', fallbackEnabled: true, customKey: 'value' };
      persistStoredSettingsRaw(custom);

      expect(getStoredSettingsSource()).toBe('rn-owned');
      const read = readStoredSettingsRaw();
      expect(read.apiProvider).toBe('gemini');
      expect(read.customKey).toBe('value');
    });

    it('should patch settings by merging with existing', () => {
      persistStoredSettingsRaw({ apiProvider: 'gemini', fallbackEnabled: false });
      const patched = patchStoredSettingsRaw({ fallbackEnabled: true, newKey: 42 });

      expect(patched.apiProvider).toBe('gemini');
      expect(patched.fallbackEnabled).toBe(true);
      expect(patched.newKey).toBe(42);

      // Verify persisted
      const read = readStoredSettingsRaw();
      expect(read.fallbackEnabled).toBe(true);
    });

    it('should fall back to migration key when rn-owned not present', () => {
      // Simulate migration data: write directly to the migration key
      appStorage.set('migration.settings', JSON.stringify({ apiProvider: 'gpt', migrated: true }));

      expect(getStoredSettingsSource()).toBe('migration-fallback');
      const settings = readStoredSettingsRaw();
      expect(settings.apiProvider).toBe('gpt');
      expect(settings.migrated).toBe(true);
    });
  });

  describe('wellbeing', () => {
    it('should return "empty" source when nothing stored', () => {
      expect(getStoredWellbeingSource()).toBe('empty');
    });

    it('should return empty arrays when nothing stored', () => {
      const payload = readStoredWellbeingPayload();
      expect(payload.sleepLogs).toEqual([]);
      expect(payload.waterLogs).toEqual([]);
      expect(payload.dailyWellbeingLogs).toEqual([]);
      expect(payload.tasks).toEqual([]);
    });

    it('should persist and read back wellbeing payload', () => {
      const payload = {
        sleepLogs: [{ date: '2025-01-01', hours: 8 }],
        waterLogs: [{ date: '2025-01-01', ml: 2000 }],
        dailyWellbeingLogs: [],
        tasks: ['stretch', 'meditate'],
      };
      persistStoredWellbeingPayload(payload);

      expect(getStoredWellbeingSource()).toBe('rn-owned');
      const read = readStoredWellbeingPayload();
      expect(read.sleepLogs).toHaveLength(1);
      expect(read.waterLogs).toHaveLength(1);
      expect(read.tasks).toHaveLength(2);
    });

    it('should patch wellbeing by merging specific fields', () => {
      persistStoredWellbeingPayload({
        sleepLogs: [{ hours: 7 }],
        waterLogs: [{ ml: 1500 }],
        dailyWellbeingLogs: [],
        tasks: [],
      });

      const patched = patchStoredWellbeingPayload({
        waterLogs: [{ ml: 2500 }],
      });

      expect(patched.sleepLogs).toHaveLength(1); // preserved
      expect(patched.waterLogs).toEqual([{ ml: 2500 }]); // replaced
    });

    it('should fall back to migration keys when rn-owned not present', () => {
      appStorage.set('migration.wellbeing.sleepLogs', JSON.stringify([{ hours: 6 }]));

      expect(getStoredWellbeingSource()).toBe('migration-fallback');
      const payload = readStoredWellbeingPayload();
      expect(payload.sleepLogs).toHaveLength(1);
    });
  });

  describe('meal templates', () => {
    it('should return "empty" source when nothing stored', () => {
      expect(getStoredMealTemplateSource()).toBe('empty');
    });

    it('should return empty array when nothing stored', () => {
      expect(readStoredMealTemplatesRaw()).toEqual([]);
    });

    it('should persist and read back templates', () => {
      const templates = [{ id: 't1', name: 'Desayuno' }, { id: 't2', name: 'Almuerzo' }];
      persistStoredMealTemplatesRaw(templates);

      expect(getStoredMealTemplateSource()).toBe('rn-owned');
      expect(readStoredMealTemplatesRaw()).toHaveLength(2);
    });

    it('should fall back to migration key', () => {
      appStorage.set('migration.mealTemplates', JSON.stringify([{ id: 'm1' }]));

      expect(getStoredMealTemplateSource()).toBe('migration-fallback');
      expect(readStoredMealTemplatesRaw()).toHaveLength(1);
    });
  });

  describe('notification permission', () => {
    it('should return defaults when nothing stored', () => {
      const snapshot = readNotificationPermissionSnapshot();
      expect(snapshot.status).toBe('unsupported');
      expect(snapshot.granted).toBe(false);
      expect(snapshot.lastCheckedAt).toBeNull();
    });

    it('should persist and read back permission snapshot', () => {
      const snapshot: NotificationPermissionSnapshot = {
        status: 'authorized',
        granted: true,
        lastCheckedAt: '2025-01-01T12:00:00Z',
        lastScheduledAt: '2025-01-01T12:05:00Z',
      };
      persistNotificationPermissionSnapshot(snapshot);

      const read = readNotificationPermissionSnapshot();
      expect(read.status).toBe('authorized');
      expect(read.granted).toBe(true);
    });
  });

  describe('widget sync status', () => {
    it('should return stale defaults when nothing stored', () => {
      const status = readWidgetSyncStatus();
      expect(status.stale).toBe(true);
      expect(status.lastError).toBeNull();
      expect(status.source).toBe('unknown');
    });

    it('should persist and read back widget sync status', () => {
      const status: WidgetSyncStatus = {
        stale: false,
        lastAttemptAt: '2025-01-01T12:00:00Z',
        lastSuccessfulSyncAt: '2025-01-01T12:00:00Z',
        lastError: null,
        source: 'foreground',
      };
      persistWidgetSyncStatus(status);

      const read = readWidgetSyncStatus();
      expect(read.stale).toBe(false);
      expect(read.source).toBe('foreground');
    });
  });

  describe('background sync status', () => {
    it('should return idle defaults when nothing stored', () => {
      const status = readBackgroundSyncStatus();
      expect(status.lastResult).toBe('idle');
      expect(status.lastError).toBeNull();
    });

    it('should persist and read back background sync status', () => {
      const status: BackgroundSyncStatus = {
        lastAttemptAt: '2025-01-01T10:00:00Z',
        lastCompletedAt: '2025-01-01T10:01:00Z',
        lastResult: 'success',
        lastError: null,
      };
      persistBackgroundSyncStatus(status);

      const read = readBackgroundSyncStatus();
      expect(read.lastResult).toBe('success');
      expect(read.lastCompletedAt).toBe('2025-01-01T10:01:00Z');
    });
  });
});
