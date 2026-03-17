import '../mocks/setupMobileDatabase';
import { resetMockDatabase, getMockDatabase, getMockTableRows } from '../mocks/setupMobileDatabase';
import { hydrateFromMigrationSnapshot } from '../../services/migrationHydrationService';
import { persistStoredMealTemplatesRaw, persistStoredSettingsRaw, persistStoredWellbeingPayload } from '../../services/mobileDomainStateService';
import { appStorage, setJsonValue } from '../../storage/mmkv';

// Mockear el módulo de domain state service
jest.mock('../../services/mobileDomainStateService', () => ({
  persistStoredMealTemplatesRaw: jest.fn(),
  persistStoredSettingsRaw: jest.fn(),
  persistStoredWellbeingPayload: jest.fn(),
  readStoredSettingsRaw: jest.fn(() => ({})),
  readStoredWellbeingPayload: jest.fn(() => ({ tasks: [], sleepLogs: [], waterLogs: [], dailyWellbeingLogs: [] })),
  readStoredMealTemplatesRaw: jest.fn(() => []),
  readWidgetSyncStatus: jest.fn(() => ({ stale: false })),
  persistWidgetSyncStatus: jest.fn(),
  readBackgroundSyncStatus: jest.fn(() => ({ lastResult: 'idle' })),
  persistBackgroundSyncStatus: jest.fn(),
  readNotificationPermissionSnapshot: jest.fn(() => ({ granted: false })),
  persistNotificationPermissionSnapshot: jest.fn(),
}));

// Mockear el módulo de MMKV storage
jest.mock('../../storage/mmkv', () => {
  const store = new Map();
  return {
    appStorage: {
      set: jest.fn((k: string, v: unknown) => store.set(k, v)),
      getString: jest.fn((k: string) => store.get(k)),
      getBoolean: jest.fn((k: string) => store.get(k)),
      delete: jest.fn((k: string) => store.delete(k)),
      clearAll: jest.fn(() => store.clear()),
    },
    setJsonValue: jest.fn((k: string, v: unknown) => store.set(k, JSON.stringify(v))),
    getJsonValue: jest.fn((k: string, fallback: unknown) => {
      const raw = store.get(k);
      if (raw === undefined) return fallback;
      try {
        return JSON.parse(raw as string);
      } catch {
        return fallback;
      }
    }),
  };
});

describe('hydrateFromMigrationSnapshot', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    resetMockDatabase();
    appStorage.clearAll();
  });

  const setupDomainPayload = (domain: string, payload: any) => {
    getMockDatabase().execute(
      'INSERT OR REPLACE INTO domain_payloads (domain, payload_json, updated_at) VALUES (?, ?, ?)',
      [domain, JSON.stringify(payload), new Date().toISOString()]
    );
  };

  it('importa nutrition logs válidos y reporta conteos correctos', async () => {
    setupDomainPayload('nutrition', {
      nutritionLogs: [
        { id: '1', description: 'Log 1', createdAt: '2025-01-01T10:00:00Z', totals: { calories: 500, protein: 30, carbs: 60, fats: 10 } },
        { id: '2', description: 'Log 2', createdAt: '2025-01-01T14:00:00Z', totals: { calories: 400, protein: 25, carbs: 50, fats: 12 } },
        { id: '3', description: 'Log 3', createdAt: '2025-01-01T19:00:00Z', totals: { calories: 600, protein: 40, carbs: 70, fats: 15 } }
      ]
    });

    const result = await hydrateFromMigrationSnapshot();

    expect(result.nutritionLogsImported).toBe(3);
    expect(result.nutritionLogsDiscarded).toBe(0);
    expect(getMockTableRows('nutrition_logs')).toHaveLength(3);
  });

  it('descarta nutrition logs sin id', async () => {
    setupDomainPayload('nutrition', {
      nutritionLogs: [
        { id: '1', description: 'Con ID' },
        { description: 'Sin ID' }
      ]
    });

    const result = await hydrateFromMigrationSnapshot();

    expect(result.nutritionLogsImported).toBe(1);
    expect(result.nutritionLogsDiscarded).toBe(1);
  });

  it('descarta nutrition logs con macros cero y descripción genérica', async () => {
    setupDomainPayload('nutrition', {
      nutritionLogs: [
        { id: 'bad-1', totals: { calories: 0, protein: 0, carbs: 0, fats: 0 } } // fallback description is "Comida importada"
      ]
    });

    const result = await hydrateFromMigrationSnapshot();

    expect(result.nutritionLogsDiscarded).toBe(1);
    expect(result.nutritionLogsImported).toBe(0);
  });

  it('acepta nutrition log con macros en campos alternativos (kcal, proteins, etc)', async () => {
    setupDomainPayload('nutrition', {
      nutritionLogs: [
        { id: 'alt-1', kcal: 500, proteins: 30, carbohydrates: 60, grasas: 15, text: 'pollo con arroz', date: '2025-01-15T12:00:00Z' }
      ]
    });

    const result = await hydrateFromMigrationSnapshot();

    expect(result.nutritionLogsImported).toBe(1);
    const rows = getMockTableRows('nutrition_logs');
    const totals = JSON.parse(rows[0].totals_json as string);
    expect(totals.calories).toBe(500);
    expect(totals.protein).toBe(30);
  });

  it('acepta nutrition log con macros anidados bajo "totals"', async () => {
    setupDomainPayload('nutrition', {
      nutritionLogs: [
        { id: 'nest-1', totals: { calories: 400, protein: 25, carbs: 50, fats: 10 }, description: 'ensalada', createdAt: '2025-01-15T12:00:00Z' }
      ]
    });

    const result = await hydrateFromMigrationSnapshot();

    expect(result.nutritionLogsImported).toBe(1);
  });

  it('es idempotente — no duplica logs ya existentes', async () => {
    // Insertamos log directamente en nutrition_logs
    // Usamos INSERT OR REPLACE para que el mock gestione la lógica de reemplazo/existencia
    getMockDatabase().execute(
      'INSERT OR REPLACE INTO nutrition_logs (id, description, created_at, totals_json, analysis_json) VALUES (?, ?, ?, ?, ?)',
      ['existing-1', 'Old', '2025-01-01', '{}', '{}']
    );

    setupDomainPayload('nutrition', {
      nutritionLogs: [
        { id: 'existing-1', description: 'New but same ID' }
      ]
    });

    const result = await hydrateFromMigrationSnapshot();

    // El mock actual devuelve length 1 en el select WHERE id = ? si ya existe el log,
    // por lo que hydrateFromMigrationSnapshot debería saltarlo.
    expect(result.nutritionLogsImported).toBe(0);
  });

  it('persiste settings cuando el dominio existe', async () => {
    setupDomainPayload('settings', { theme: 'dark' });

    const result = await hydrateFromMigrationSnapshot();

    expect(result.settingsImported).toBe(true);
    expect(persistStoredSettingsRaw).toHaveBeenCalledWith({ theme: 'dark' });
  });

  it('persiste wellbeing cuando el dominio existe', async () => {
    const payload = { tasks: ['task1'], sleepLogs: [], waterLogs: [], dailyWellbeingLogs: [] };
    setupDomainPayload('wellbeing', payload);

    const result = await hydrateFromMigrationSnapshot();

    expect(result.wellbeingImported).toBe(true);
    expect(persistStoredWellbeingPayload).toHaveBeenCalledWith(payload);
  });

  it('reporta errors sin crashear cuando un dominio falla', async () => {
    // Forzamos un error en la base de datos para el dominio nutrition
    const db = getMockDatabase();
    const originalExecute = db.execute;
    
    // Mock temporal para fallar en el SELECT de domain_payloads o similar
    (db.execute as jest.Mock).mockImplementationOnce(() => {
        throw new Error('Database connection failed');
    });

    const result = await hydrateFromMigrationSnapshot();

    expect(result.errors.length).toBeGreaterThan(0);
    expect(result.errors[0]).toContain('Error rehidratando nutrición');
    
    // Restaurar el mock
    (db.execute as jest.Mock).mockImplementation(originalExecute);
  });

  it('retorna workoutStatus "available-in-overview" siempre', async () => {
    const result = await hydrateFromMigrationSnapshot();
    expect(result.workoutStatus).toBe('available-in-overview');
  });
});
