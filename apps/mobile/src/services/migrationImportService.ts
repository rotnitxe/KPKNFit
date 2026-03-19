import type { MigrationSnapshotV1 } from '@kpkn/shared-types';
import { validateMigrationSnapshot } from '@kpkn/shared-types';
import { appStorage, setJsonValue } from '../storage/mmkv';
import { migrationBridge } from '../modules/migrationBridge';
import { getPersistedMigrationSummary, persistMigrationSnapshot } from './mobilePersistenceService';

const SNAPSHOT_COMPLETE_KEY = 'migration.complete';

export interface MigrationImportSummary {
  source: 'snapshot' | 'empty';
  recordCounts: MigrationSnapshotV1['integrity']['recordCounts'] | null;
  /**
   * Presente si el snapshot fue encontrado pero era inválido o corrupto.
   * Cuando este campo existe, source será 'empty' y NO se habrán persistido datos.
   */
  validationError?: string;
}

export async function importBridgeSnapshotIfNeeded(): Promise<MigrationImportSummary> {
  // Si ya importamos en sesiones previas, usamos el resumen persistido.
  const persistedSummary = await getPersistedMigrationSummary();
  if (persistedSummary) {
    appStorage.set(SNAPSHOT_COMPLETE_KEY, true);
    return {
      source: 'snapshot',
      recordCounts: persistedSummary.integrity.recordCounts,
    };
  }

  // Leemos el snapshot crudo del bridge nativo.
  const rawSnapshot = await migrationBridge.readMigrationSnapshot();
  if (!rawSnapshot) {
    return {
      source: 'empty',
      recordCounts: null,
    };
  }

  // ── Validación estructural ────────────────────────────────────────────────
  // Antes de persistir NADA, validamos que el snapshot tiene la forma esperada.
  // Si falla, abortamos completamente: no contaminar la base con basura.
  let parsed: unknown;
  try {
    parsed = JSON.parse(rawSnapshot);
  } catch (parseError) {
    const reason = `El snapshot no es JSON válido: ${parseError instanceof Error ? parseError.message : 'error de parseo'}`;
    console.error('[MigrationImport] Snapshot no parseable:', reason);
    return {
      source: 'empty',
      recordCounts: null,
      validationError: reason,
    };
  }

  const validation = validateMigrationSnapshot(parsed);
  if (validation.valid === false) {
    console.error('[MigrationImport] Snapshot inválido, importación abortada:', validation.reason);
    return {
      source: 'empty',
      recordCounts: null,
      validationError: validation.reason,
    };
  }

  // ── Persistencia ──────────────────────────────────────────────────────────
  // Solo llegamos aquí si el snapshot es estructuralmente válido.
  const snapshot = validation.snapshot;
  await persistMigrationSnapshot(snapshot);
  setJsonValue('migration.summary', {
    importedAt: new Date().toISOString(),
    integrity: snapshot.integrity,
  });
  appStorage.set(SNAPSHOT_COMPLETE_KEY, true);
  await migrationBridge.markMigrationComplete();

  return {
    source: 'snapshot',
    recordCounts: snapshot.integrity.recordCounts,
  };
}
