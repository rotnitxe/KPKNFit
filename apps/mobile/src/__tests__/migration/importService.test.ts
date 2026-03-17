import '../mocks/setupMobileDatabase';
import { resetMockDatabase, getMockDatabase, getMockTableRows } from '../mocks/setupMobileDatabase';
import { importBridgeSnapshotIfNeeded } from '../../services/migrationImportService';
import { migrationBridge } from '../../modules/migrationBridge';
import { makeValidSnapshot, makeIntegrity, makeRecordCounts } from '../fixtures/migrationFixtures';
import { appStorage } from '../../storage/mmkv';

// Mockear el módulo de migrationBridge
jest.mock('../../modules/migrationBridge', () => ({
  migrationBridge: {
    readMigrationSnapshot: jest.fn(),
    markMigrationComplete: jest.fn(),
  },
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

describe('importBridgeSnapshotIfNeeded', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    resetMockDatabase();
    appStorage.clearAll();
  });

  it('retorna source "empty" cuando el bridge no tiene snapshot', async () => {
    (migrationBridge.readMigrationSnapshot as jest.Mock).mockResolvedValue(null);
    
    const result = await importBridgeSnapshotIfNeeded();
    
    expect(result.source).toBe('empty');
    expect(result.recordCounts).toBeNull();
    expect(migrationBridge.markMigrationComplete).not.toHaveBeenCalled();
  });

  it('retorna source "empty" con validationError cuando el JSON es inválido', async () => {
    (migrationBridge.readMigrationSnapshot as jest.Mock).mockResolvedValue('{broken json!!');
    
    const result = await importBridgeSnapshotIfNeeded();
    
    expect(result.source).toBe('empty');
    expect(result.validationError).toContain('JSON');
    expect(migrationBridge.markMigrationComplete).not.toHaveBeenCalled();
  });

  it('retorna source "empty" con validationError cuando el snapshot no pasa validación', async () => {
    (migrationBridge.readMigrationSnapshot as jest.Mock).mockResolvedValue(JSON.stringify({ schemaVersion: 99 }));
    
    const result = await importBridgeSnapshotIfNeeded();
    
    expect(result.source).toBe('empty');
    // Ajustado para coincidir con el mensaje real: "Versión de schema incompatible: esperada 1, recibida 99."
    expect(result.validationError).toContain('Versión de schema incompatible');
    expect(migrationBridge.markMigrationComplete).not.toHaveBeenCalled();
  });

  it('importa snapshot válido, persiste en SQLite, y retorna source "snapshot"', async () => {
    const snapshot = makeValidSnapshot({
      integrity: makeIntegrity({
        recordCounts: makeRecordCounts({ nutritionLogs: 3, programs: 1 }),
      }),
    });
    (migrationBridge.readMigrationSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(snapshot));
    
    const result = await importBridgeSnapshotIfNeeded();
    
    expect(result.source).toBe('snapshot');
    expect(result.recordCounts?.nutritionLogs).toBe(3);
    expect(migrationBridge.markMigrationComplete).toHaveBeenCalledTimes(1);
    
    // Verificar que se persistió en SQLite (app_meta y domain_payloads)
    const metaRows = getMockTableRows('app_meta');
    expect(metaRows.length).toBeGreaterThan(0);
    const domainRows = getMockTableRows('domain_payloads');
    expect(domainRows.length).toBeGreaterThan(0);
  });

  it('retorna snapshot cacheado en segunda llamada sin llamar al bridge', async () => {
    const snapshot = makeValidSnapshot();
    (migrationBridge.readMigrationSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(snapshot));
    
    // Primera llamada
    await importBridgeSnapshotIfNeeded();
    expect(migrationBridge.readMigrationSnapshot).toHaveBeenCalledTimes(1);
    
    // Limpiar mock del bridge para asegurar que no se llame de nuevo
    (migrationBridge.readMigrationSnapshot as jest.Mock).mockClear();
    
    // Segunda llamada
    const result = await importBridgeSnapshotIfNeeded();
    
    expect(result.source).toBe('snapshot');
    expect(migrationBridge.readMigrationSnapshot).not.toHaveBeenCalled();
  });

  it('no persiste nada cuando el snapshot es inválido', async () => {
    const badSnapshot = { schemaVersion: 1, createdAt: '2025-01-01', appVersion: '1.0' }; // Falta payload e integrity
    (migrationBridge.readMigrationSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(badSnapshot));
    
    const result = await importBridgeSnapshotIfNeeded();
    
    expect(result.source).toBe('empty');
    expect(getMockTableRows('domain_payloads')).toHaveLength(0);
    expect(migrationBridge.markMigrationComplete).not.toHaveBeenCalled();
  });
});
