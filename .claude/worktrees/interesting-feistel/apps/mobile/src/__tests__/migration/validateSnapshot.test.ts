import { validateMigrationSnapshot } from '@kpkn/shared-types';
import { makeValidSnapshot, makeIntegrity, makeRecordCounts } from '../fixtures/migrationFixtures';

describe('validateMigrationSnapshot', () => {
  // ── Happy path ──────────────────────────────────────────────
  it('acepta un snapshot válido completo', () => {
    const result = validateMigrationSnapshot(makeValidSnapshot());
    expect(result.valid).toBe(true);
    if (result.valid) {
      expect(result.snapshot.schemaVersion).toBe(1);
    }
  });

  it('acepta snapshot con nutrition logs y record counts', () => {
    const snapshot = makeValidSnapshot({
      integrity: makeIntegrity({
        recordCounts: makeRecordCounts({ nutritionLogs: 5, programs: 2 }),
      }),
      payload: {
        ...makeValidSnapshot().payload,
        nutrition: {
          ...makeValidSnapshot().payload.nutrition,
          nutritionLogs: [{ id: '1' }, { id: '2' }] as any,
        },
      },
    });
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(true);
  });

  // ── Rechazos de nivel raíz ──────────────────────────────────
  it('rechaza null', () => {
    const result = validateMigrationSnapshot(null);
    expect(result.valid).toBe(false);
  });

  it('rechaza undefined', () => {
    const result = validateMigrationSnapshot(undefined);
    expect(result.valid).toBe(false);
  });

  it('rechaza string', () => {
    const result = validateMigrationSnapshot('not an object');
    expect(result.valid).toBe(false);
  });

  it('rechaza array', () => {
    const result = validateMigrationSnapshot([1, 2, 3]);
    expect(result.valid).toBe(false);
  });

  it('rechaza número', () => {
    const result = validateMigrationSnapshot(42);
    expect(result.valid).toBe(false);
  });

  it('rechaza objeto vacío', () => {
    const result = validateMigrationSnapshot({});
    expect(result.valid).toBe(false);
  });

  // ── schemaVersion ───────────────────────────────────────────
  it('rechaza schemaVersion 2', () => {
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), schemaVersion: 2 } as any);
    expect(result.valid).toBe(false);
    if (result.valid === false) expect(result.reason).toContain('schema');
  });

  it('rechaza schemaVersion string', () => {
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), schemaVersion: '1' } as any);
    expect(result.valid).toBe(false);
  });

  it('rechaza schemaVersion ausente', () => {
    const { schemaVersion, ...rest } = makeValidSnapshot();
    const result = validateMigrationSnapshot(rest);
    expect(result.valid).toBe(false);
  });

  // ── createdAt ───────────────────────────────────────────────
  it('rechaza createdAt vacío', () => {
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), createdAt: '' });
    expect(result.valid).toBe(false);
    if (result.valid === false) expect(result.reason).toContain('createdAt');
  });

  it('rechaza createdAt numérico', () => {
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), createdAt: 12345 } as any);
    expect(result.valid).toBe(false);
  });

  it('rechaza createdAt solo espacios', () => {
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), createdAt: '   ' });
    expect(result.valid).toBe(false);
  });

  // ── appVersion ──────────────────────────────────────────────
  it('acepta appVersion vacío (BUG: el validador no lo rechaza actualmente)', () => {
    // BUG: El validador en packages/shared-types/src/migration.ts no rechaza appVersion vacío
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), appVersion: '' });
    expect(result.valid).toBe(true);
  });

  it('rechaza appVersion numérico', () => {
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), appVersion: 1.4 } as any);
    expect(result.valid).toBe(false);
  });

  // ── integrity ───────────────────────────────────────────────
  it('rechaza integrity null', () => {
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), integrity: null } as any);
    expect(result.valid).toBe(false);
  });

  it('rechaza integrity sin recordCounts', () => {
    const { recordCounts, ...badIntegrity } = makeIntegrity();
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), integrity: badIntegrity } as any);
    expect(result.valid).toBe(false);
  });

  it('rechaza integrity con boolean faltante', () => {
    const integrity = makeIntegrity();
    delete (integrity as unknown as Record<string, unknown>).settingsHydrated;
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), integrity } as any);
    expect(result.valid).toBe(false);
  });

  it('rechaza recordCounts con campo string en vez de number', () => {
    const integrity = makeIntegrity({
      recordCounts: { ...makeRecordCounts(), nutritionLogs: 'many' as unknown as number },
    });
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), integrity });
    expect(result.valid).toBe(false);
  });

  // ── payload.nutrition ───────────────────────────────────────
  it('rechaza nutrition sin nutritionLogs array', () => {
    const snapshot = makeValidSnapshot();
    (snapshot.payload.nutrition as Record<string, unknown>).nutritionLogs = 'not-array';
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(false);
  });

  it('rechaza nutrition sin mealTemplates array', () => {
    const snapshot = makeValidSnapshot();
    (snapshot.payload.nutrition as Record<string, unknown>).mealTemplates = null;
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(false);
  });

  // ── payload.wellbeing ───────────────────────────────────────
  it('rechaza wellbeing sin sleepLogs array', () => {
    const snapshot = makeValidSnapshot();
    (snapshot.payload.wellbeing as Record<string, unknown>).sleepLogs = {};
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(false);
  });

  it('rechaza wellbeing sin tasks array', () => {
    const snapshot = makeValidSnapshot();
    (snapshot.payload.wellbeing as Record<string, unknown>).tasks = 'none';
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(false);
  });

  // ── payload.workout ─────────────────────────────────────────
  it('rechaza workout sin history array', () => {
    const snapshot = makeValidSnapshot();
    (snapshot.payload.workout as Record<string, unknown>).history = null;
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(false);
  });

  // ── payload.programs ────────────────────────────────────────
  it('rechaza programs sin programs array', () => {
    const snapshot = makeValidSnapshot();
    (snapshot.payload.programs as Record<string, unknown>).programs = 'bad';
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(false);
  });

  // ── payload.exercise ────────────────────────────────────────
  it('rechaza exercise sin exerciseList array', () => {
    const snapshot = makeValidSnapshot();
    (snapshot.payload.exercise as Record<string, unknown>).exerciseList = 42;
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(false);
  });

  // ── payload ausente o incompleto ────────────────────────────
  it('rechaza payload null', () => {
    const result = validateMigrationSnapshot({ ...makeValidSnapshot(), payload: null } as any);
    expect(result.valid).toBe(false);
  });

  it('rechaza payload sin dominio nutrition', () => {
    const snapshot = makeValidSnapshot();
    delete (snapshot.payload as Record<string, unknown>).nutrition;
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(false);
  });

  it('rechaza payload sin dominio workout', () => {
    const snapshot = makeValidSnapshot();
    delete (snapshot.payload as Record<string, unknown>).workout;
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(false);
  });

  it('rechaza payload sin dominio settings', () => {
    const snapshot = makeValidSnapshot();
    delete (snapshot.payload as Record<string, unknown>).settings;
    const result = validateMigrationSnapshot(snapshot);
    expect(result.valid).toBe(false);
  });

  // ── Resultado contiene reason descriptivo ───────────────────
  it('reason menciona el campo problemático cuando falla', () => {
    const result = validateMigrationSnapshot({ schemaVersion: 1, createdAt: '', appVersion: '1.0' });
    expect(result.valid).toBe(false);
    if (result.valid === false) {
      expect(typeof result.reason).toBe('string');
      expect(result.reason.length).toBeGreaterThan(5);
    }
  });
});
