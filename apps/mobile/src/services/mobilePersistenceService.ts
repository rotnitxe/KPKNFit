import type { MigrationSnapshotV1, WorkoutLogSummary } from '@kpkn/shared-types';
import type { SavedNutritionEntry } from '../types/nutrition';
import { getMobileDatabase } from '../storage/mobileDatabase';

const META_KEYS = {
  schemaVersion: 'migration.schemaVersion',
  createdAt: 'migration.createdAt',
  appVersion: 'migration.appVersion',
  integrity: 'migration.integrity',
  importedAt: 'migration.importedAt',
} as const;

function parseJson<T>(value: string, fallback: T): T {
  try {
    return JSON.parse(value) as T;
  } catch (error) {
    console.warn('No se pudo leer un valor persistido en SQLite.', error);
    return fallback;
  }
}

function upsertMeta(key: string, value: unknown, updatedAt: string) {
  const db = getMobileDatabase();
  db.execute(
    'INSERT OR REPLACE INTO app_meta (key, value, updated_at) VALUES (?, ?, ?)',
    [key, JSON.stringify(value), updatedAt],
  );
}

function upsertDomainPayload(domain: string, payload: unknown, updatedAt: string) {
  const db = getMobileDatabase();
  db.execute(
    'INSERT OR REPLACE INTO domain_payloads (domain, payload_json, updated_at) VALUES (?, ?, ?)',
    [domain, JSON.stringify(payload), updatedAt],
  );
}

export async function persistMigrationSnapshot(snapshot: MigrationSnapshotV1) {
  const db = getMobileDatabase();
  const importedAt = new Date().toISOString();

  await db.transaction(async tx => {
    tx.execute(
      'INSERT OR REPLACE INTO app_meta (key, value, updated_at) VALUES (?, ?, ?)',
      [META_KEYS.schemaVersion, JSON.stringify(snapshot.schemaVersion), importedAt],
    );
    tx.execute(
      'INSERT OR REPLACE INTO app_meta (key, value, updated_at) VALUES (?, ?, ?)',
      [META_KEYS.createdAt, JSON.stringify(snapshot.createdAt), importedAt],
    );
    tx.execute(
      'INSERT OR REPLACE INTO app_meta (key, value, updated_at) VALUES (?, ?, ?)',
      [META_KEYS.appVersion, JSON.stringify(snapshot.appVersion), importedAt],
    );
    tx.execute(
      'INSERT OR REPLACE INTO app_meta (key, value, updated_at) VALUES (?, ?, ?)',
      [META_KEYS.integrity, JSON.stringify(snapshot.integrity), importedAt],
    );
    tx.execute(
      'INSERT OR REPLACE INTO app_meta (key, value, updated_at) VALUES (?, ?, ?)',
      [META_KEYS.importedAt, JSON.stringify(importedAt), importedAt],
    );

    for (const [domain, payload] of Object.entries(snapshot.payload)) {
      tx.execute(
        'INSERT OR REPLACE INTO domain_payloads (domain, payload_json, updated_at) VALUES (?, ?, ?)',
        [domain, JSON.stringify(payload), importedAt],
      );
    }
  });
}

export async function getPersistedMigrationSummary() {
  const db = getMobileDatabase();
  const result = db.execute('SELECT key, value FROM app_meta WHERE key IN (?, ?, ?, ?, ?)', [
    META_KEYS.schemaVersion,
    META_KEYS.createdAt,
    META_KEYS.appVersion,
    META_KEYS.integrity,
    META_KEYS.importedAt,
  ]);

  const rows = result.rows?._array ?? [];
  if (rows.length === 0) return null;

  const meta = new Map<string, string>();
  for (const row of rows) {
    meta.set(String(row.key), String(row.value));
  }

  const integrityRaw = meta.get(META_KEYS.integrity);
  if (!integrityRaw) return null;

  return {
    schemaVersion: parseJson<number>(meta.get(META_KEYS.schemaVersion) ?? '1', 1),
    createdAt: parseJson<string>(meta.get(META_KEYS.createdAt) ?? '""', ''),
    appVersion: parseJson<string>(meta.get(META_KEYS.appVersion) ?? '""', ''),
    importedAt: parseJson<string>(meta.get(META_KEYS.importedAt) ?? '""', ''),
    integrity: parseJson<MigrationSnapshotV1['integrity']>(integrityRaw, {
      settingsHydrated: false,
      workoutHydrated: false,
      nutritionHydrated: false,
      wellbeingHydrated: false,
      exerciseHydrated: false,
      recordCounts: {
        programs: 0,
        workoutHistory: 0,
        nutritionLogs: 0,
        pantryItems: 0,
        mealTemplates: 0,
        sleepLogs: 0,
        waterLogs: 0,
        tasks: 0,
      },
    }),
  };
}

export async function persistNutritionLog(entry: SavedNutritionEntry) {
  const db = getMobileDatabase();
  db.execute(
    `
      INSERT OR REPLACE INTO nutrition_logs (
        id,
        description,
        created_at,
        totals_json,
        analysis_json
      ) VALUES (?, ?, ?, ?, ?)
    `,
    [
      entry.id,
      entry.description,
      entry.createdAt,
      JSON.stringify(entry.totals),
      JSON.stringify(entry.analysis),
    ],
  );
}

export async function loadSavedNutritionLogs(limit = 100): Promise<SavedNutritionEntry[]> {
  const db = getMobileDatabase();
  const result = db.execute(
    `
      SELECT id, description, created_at, totals_json, analysis_json
      FROM nutrition_logs
      ORDER BY created_at DESC
      LIMIT ?
    `,
    [limit],
  );

  return (result.rows?._array ?? []).map(row => ({
    id: String(row.id),
    description: String(row.description),
    createdAt: String(row.created_at),
    totals: parseJson<SavedNutritionEntry['totals']>(String(row.totals_json), {
      calories: 0,
      protein: 0,
      carbs: 0,
      fats: 0,
    }),
    analysis: parseJson<SavedNutritionEntry['analysis']>(String(row.analysis_json), {
      items: [],
      overallConfidence: 0,
      containsEstimatedItems: false,
      requiresReview: false,
      elapsedMs: 0,
      modelVersion: null,
      engine: 'unavailable',
      runtimeError: null,
    }),
  }));
}

export async function persistDomainPayload(domain: string, payload: unknown) {
  upsertDomainPayload(domain, payload, new Date().toISOString());
}

export async function persistMetaValue(key: string, value: unknown) {
  upsertMeta(key, value, new Date().toISOString());
}

export async function loadPersistedDomainPayload<T = unknown>(domain: string): Promise<T | null> {
  const db = getMobileDatabase();
  const result = db.execute(
    'SELECT payload_json FROM domain_payloads WHERE domain = ? LIMIT 1',
    [domain],
  );
  const row = result.rows?._array?.[0];
  if (!row?.payload_json) return null;
  return parseJson<T | null>(String(row.payload_json), null);
}

export async function persistLocalWorkoutLog(entry: WorkoutLogSummary) {
  const db = getMobileDatabase();
  db.execute(
    `
      INSERT OR REPLACE INTO workout_logs_local (
        id,
        date,
        program_name,
        session_name,
        exercise_count,
        completed_set_count,
        duration_minutes
      ) VALUES (?, ?, ?, ?, ?, ?, ?)
    `,
    [
      entry.id,
      entry.date,
      entry.programName,
      entry.sessionName,
      entry.exerciseCount,
      entry.completedSetCount,
      entry.durationMinutes,
    ],
  );
}

export async function loadLocalWorkoutLogs(limit = 60): Promise<WorkoutLogSummary[]> {
  const db = getMobileDatabase();
  const result = db.execute(
    `
      SELECT id, date, program_name, session_name, exercise_count, completed_set_count, duration_minutes
      FROM workout_logs_local
      ORDER BY date DESC
      LIMIT ?
    `,
    [limit],
  );

  return (result.rows?._array ?? []).map(row => ({
    id: String(row.id),
    date: String(row.date),
    programName: String(row.program_name),
    sessionName: String(row.session_name),
    exerciseCount: Number(row.exercise_count ?? 0),
    completedSetCount: Number(row.completed_set_count ?? 0),
    durationMinutes:
      row.duration_minutes === null || row.duration_minutes === undefined
        ? null
        : Number(row.duration_minutes),
  }));
}

// ────────────────────────────────────────────────────────────
// Smoke test isolation — escribe en smoke_test_logs, NUNCA en nutrition_logs.
// Los datos de smoke no son datos reales del usuario.
// ────────────────────────────────────────────────────────────

export async function persistSmokeLog(entry: SavedNutritionEntry) {
  const db = getMobileDatabase();
  db.execute(
    `INSERT OR REPLACE INTO smoke_test_logs (id, description, created_at, totals_json, analysis_json)
     VALUES (?, ?, ?, ?, ?)`,
    [
      entry.id,
      entry.description,
      entry.createdAt,
      JSON.stringify(entry.totals),
      JSON.stringify(entry.analysis),
    ],
  );
}

export async function loadSmokeTestLogs(limit = 10): Promise<SavedNutritionEntry[]> {
  const db = getMobileDatabase();
  const result = db.execute(
    `SELECT id, description, created_at, totals_json, analysis_json
     FROM smoke_test_logs
     ORDER BY created_at DESC
     LIMIT ?`,
    [limit],
  );

  return (result.rows?._array ?? []).map(row => ({
    id: String(row.id),
    description: String(row.description),
    createdAt: String(row.created_at),
    totals: parseJson<SavedNutritionEntry['totals']>(String(row.totals_json), {
      calories: 0,
      protein: 0,
      carbs: 0,
      fats: 0,
    }),
    analysis: parseJson<SavedNutritionEntry['analysis']>(String(row.analysis_json), {
      items: [],
      overallConfidence: 0,
      containsEstimatedItems: false,
      requiresReview: false,
      elapsedMs: 0,
      modelVersion: null,
      engine: 'unavailable',
      runtimeError: null,
    }),
  }));
}
