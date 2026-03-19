import { Capacitor } from '@capacitor/core';
import { Directory, Encoding, Filesystem } from '@capacitor/filesystem';
import packageJson from '../package.json';
import { validateMigrationSnapshot, type MigrationSnapshotV1 } from '@kpkn/shared-types';

export const MIGRATION_SNAPSHOT_SCHEMA_VERSION = 1;
export const MIGRATION_SNAPSHOT_PATH = 'migration/snapshot-v1.json';

const EXPORT_DEBOUNCE_MS = 1200;

let exportTimer: ReturnType<typeof setTimeout> | null = null;
/**
 * Huella de deduplicación basada en contenido real del snapshot, no solo en conteos.
 * Ignoramos createdAt porque cambia en cada export, pero sí consideramos el payload e
 * integrity completos para detectar cambios aunque el número de registros no varíe.
 */
let lastSnapshotFingerprint: string | null = null;

function hashString(value: string) {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }

  return (hash >>> 0).toString(16).padStart(8, '0');
}

function buildSnapshotFingerprint(snapshot: MigrationSnapshotV1): string {
  const normalized = JSON.stringify({
    schemaVersion: snapshot.schemaVersion,
    appVersion: snapshot.appVersion,
    integrity: snapshot.integrity,
    payload: snapshot.payload,
  });

  return hashString(normalized);
}

export interface MigrationSnapshotSource {
  hydrated: {
    settings: boolean;
    workout: boolean;
    nutrition: boolean;
    wellbeing: boolean;
    exercise: boolean;
  };
  payload: MigrationSnapshotV1['payload'];
}

function buildIntegrity(source: MigrationSnapshotSource): MigrationSnapshotV1['integrity'] {
  return {
    settingsHydrated: source.hydrated.settings,
    workoutHydrated: source.hydrated.workout,
    nutritionHydrated: source.hydrated.nutrition,
    wellbeingHydrated: source.hydrated.wellbeing,
    exerciseHydrated: source.hydrated.exercise,
    recordCounts: {
      programs: source.payload.programs.programs.length,
      workoutHistory: source.payload.workout.history.length,
      nutritionLogs: source.payload.nutrition.nutritionLogs.length,
      pantryItems: source.payload.nutrition.pantryItems.length,
      mealTemplates: source.payload.nutrition.mealTemplates.length,
      sleepLogs: source.payload.wellbeing.sleepLogs.length,
      waterLogs: source.payload.wellbeing.waterLogs.length,
      tasks: source.payload.wellbeing.tasks.length,
    },
  };
}

export function buildMigrationSnapshotV1(source: MigrationSnapshotSource): MigrationSnapshotV1 {
  return {
    schemaVersion: MIGRATION_SNAPSHOT_SCHEMA_VERSION,
    createdAt: new Date().toISOString(),
    appVersion: packageJson.version,
    integrity: buildIntegrity(source),
    payload: source.payload,
  };
}

async function ensureMigrationDirectory() {
  try {
    await Filesystem.mkdir({
      path: 'migration',
      directory: Directory.Data,
      recursive: true,
    });
  } catch (error) {
    console.warn('No se pudo asegurar el directorio de migracion.', error);
  }
}

export async function exportMigrationSnapshotV1(snapshot: MigrationSnapshotV1) {
  const fingerprint = buildSnapshotFingerprint(snapshot);
  if (fingerprint === lastSnapshotFingerprint) return;

  await ensureMigrationDirectory();
  await Filesystem.writeFile({
    path: MIGRATION_SNAPSHOT_PATH,
    directory: Directory.Data,
    data: JSON.stringify(snapshot, null, 2),
    encoding: Encoding.UTF8,
    recursive: true,
  });
  lastSnapshotFingerprint = fingerprint;
}

export function scheduleMigrationSnapshotExport(source: MigrationSnapshotSource) {
  if (Capacitor.getPlatform() !== 'android') return;

  if (exportTimer) {
    clearTimeout(exportTimer);
  }

  exportTimer = setTimeout(() => {
    void exportMigrationSnapshotV1(buildMigrationSnapshotV1(source)).catch((error) => {
      console.error('No se pudo exportar el snapshot puente para React Native.', error);
    });
  }, EXPORT_DEBOUNCE_MS);
}

export async function readMigrationSnapshotV1(): Promise<MigrationSnapshotV1 | null> {
  try {
    const result = await Filesystem.readFile({
      path: MIGRATION_SNAPSHOT_PATH,
      directory: Directory.Data,
      encoding: Encoding.UTF8,
    });
    const parsed = JSON.parse(typeof result.data === 'string' ? result.data : '');
    const validation = validateMigrationSnapshot(parsed);
    if (validation.valid === false) {
      console.warn('Snapshot puente invalido.', validation.reason);
      return null;
    }
    return validation.snapshot;
  } catch (error) {
    console.warn('Snapshot puente no disponible todavia.', error);
    return null;
  }
}
