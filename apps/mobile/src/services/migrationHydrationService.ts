/**
 * migrationHydrationService.ts
 *
 * Lee los domain_payloads guardados en SQLite por el import del snapshot
 * y convierte los datos al formato que usan los stores RN.
 *
 * Dominios rehidratados en esta versión:
 *   ✅ nutrition   — nutritionLogs → SavedNutritionEntry[], mealTemplates a storage RN propio
 *   ✅ settings    — storage RN propio (sin escribir sobre migration.*)
 *   ✅ wellbeing   — storage RN propio (sin escribir sobre migration.*)
 *   ✅ workout     — queda persistido en SQLite/domain_payloads y su overview se arma en el store RN
 *
 * La estrategia de conversión usa "adaptadores defensivos": tomamos lo que podemos
 * de cada objeto desconocido y generamos entradas válidas para los stores RN.
 * No se propagan datos malformados.
 */

import type { SavedNutritionEntry } from '../types/nutrition';
import type { LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';
import { getMobileDatabase } from '../storage/mobileDatabase';
import { persistNutritionLog } from './mobilePersistenceService';
import {
  persistStoredMealTemplatesRaw,
  persistStoredSettingsRaw,
  persistStoredWellbeingPayload,
} from './mobileDomainStateService';

// ────────────────────────────────────────────────────────────
// Lectura de domain_payloads desde SQLite
// ────────────────────────────────────────────────────────────

function readDomainPayload(domain: string): unknown | null {
  const db = getMobileDatabase();
  const result = db.execute(
    'SELECT payload_json FROM domain_payloads WHERE domain = ?',
    [domain],
  );
  const row = result.rows?._array?.[0];
  if (!row) return null;
  try {
    return JSON.parse(String(row.payload_json));
  } catch {
    console.warn(`[MigrationHydration] No se pudo parsear domain '${domain}'.`);
    return null;
  }
}

// ────────────────────────────────────────────────────────────
// Adaptador: NutritionLog antiguo → SavedNutritionEntry
// ────────────────────────────────────────────────────────────
// La app Capacitor guarda NutritionLog con campos variados según la versión.
// Intentamos extraer los campos más comunes y construir una entrada válida.

const EMPTY_NUTRITION_TOTALS = { calories: 0, protein: 0, carbs: 0, fats: 0 };

function buildEmptyAnalysis(): LocalAiNutritionAnalysisResult {
  return {
    items: [],
    overallConfidence: 0,
    containsEstimatedItems: false,
    requiresReview: false,
    elapsedMs: 0,
    modelVersion: null,
    engine: 'unavailable',
    runtimeError: 'Entrada importada desde app anterior (análisis no disponible).',
  };
}

function isNumeric(value: unknown): value is number {
  return typeof value === 'number' && isFinite(value) && value >= 0;
}

function extractString(obj: Record<string, unknown>, ...keys: string[]): string | null {
  for (const key of keys) {
    const val = obj[key];
    if (typeof val === 'string' && val.trim() !== '') return val.trim();
  }
  return null;
}

function extractNumber(obj: Record<string, unknown>, ...keys: string[]): number {
  for (const key of keys) {
    const val = obj[key];
    if (isNumeric(val)) return val;
  }
  return 0;
}

/**
 * Convierte un objeto unknown (NutritionLog de la app Capacitor) a SavedNutritionEntry.
 * Si el objeto no tiene los campos mínimos necesarios, devuelve null (se descarta).
 */
function adaptNutritionLog(raw: unknown): SavedNutritionEntry | null {
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return null;
  const obj = raw as Record<string, unknown>;

  const id = extractString(obj, 'id');
  if (!id) return null;

  // La descripción puede venir en distintos campos según la versión
  const description = extractString(obj, 'description', 'text', 'input', 'query', 'name') ?? 'Comida importada';

  // Fecha: intentamos ISO string, fallback a now
  let createdAt: string;
  const rawDate = extractString(obj, 'createdAt', 'date', 'timestamp', 'loggedAt');
  if (rawDate) {
    try {
      createdAt = new Date(rawDate).toISOString();
    } catch {
      createdAt = new Date().toISOString();
    }
  } else {
    createdAt = new Date().toISOString();
  }

  // Macros: pueden venir directamente o anidadas bajo 'totals', 'macros', 'nutrition'
  let calories = 0;
  let protein = 0;
  let carbs = 0;
  let fats = 0;

  const totalsRaw = obj.totals ?? obj.macros ?? obj.nutrition ?? obj;
  if (typeof totalsRaw === 'object' && totalsRaw !== null) {
    const t = totalsRaw as Record<string, unknown>;
    calories = extractNumber(t, 'calories', 'kcal', 'energy');
    protein = extractNumber(t, 'protein', 'proteins', 'prot', 'p');
    carbs = extractNumber(t, 'carbs', 'carbohydrates', 'carbohidratos', 'hc', 'c');
    fats = extractNumber(t, 'fats', 'fat', 'grasas', 'lipids', 'f');
  } else {
    // Campos directos en el objeto raíz
    calories = extractNumber(obj, 'calories', 'kcal', 'energy');
    protein = extractNumber(obj, 'protein', 'proteins', 'prot');
    carbs = extractNumber(obj, 'carbs', 'carbohydrates', 'carbohidratos', 'hc');
    fats = extractNumber(obj, 'fats', 'fat', 'grasas');
  }

  const totals = { calories, protein, carbs, fats };

  // Si todos los macros son 0 y la descripción es genérica, el log es inútil → descartamos
  const allZeroMacros = calories === 0 && protein === 0 && carbs === 0 && fats === 0;
  if (allZeroMacros && description === 'Comida importada') return null;

  // Intentar recuperar análisis guardado si existe
  let analysis: LocalAiNutritionAnalysisResult;
  const rawAnalysis = obj.analysis ?? obj.result ?? obj.nutritionAnalysis;
  if (
    rawAnalysis !== null &&
    typeof rawAnalysis === 'object' &&
    !Array.isArray(rawAnalysis) &&
    Array.isArray((rawAnalysis as Record<string, unknown>).items)
  ) {
    analysis = rawAnalysis as LocalAiNutritionAnalysisResult;
  } else {
    // Construimos un análisis sintético con los macros que conocemos
    analysis = {
      items: calories > 0 ? [{
        rawText: description,
        canonicalName: description,
        quantity: 1,
        source: 'local-ai-estimate',
        confidence: 0.5,
        reviewRequired: true,
        calories,
        protein,
        carbs,
        fats,
      }] : [],
      overallConfidence: 0.5,
      containsEstimatedItems: true,
      requiresReview: true,
      elapsedMs: 0,
      modelVersion: null,
      engine: 'unavailable',
      runtimeError: 'Análisis reconstruido desde datos migrados.',
    };
  }

  return { id, description, createdAt, totals, analysis };
}

// ────────────────────────────────────────────────────────────
// Rehidratación por dominio
// ────────────────────────────────────────────────────────────

export interface HydrationResult {
  nutritionLogsImported: number;
  nutritionLogsDiscarded: number;
  settingsImported: boolean;
  wellbeingImported: boolean;
  workoutStatus: 'available-in-overview';
  errors: string[];
}

/**
 * Rehidrata los dominios prioritarios desde domain_payloads.
 * Debe llamarse tras importBridgeSnapshotIfNeeded() si source === 'snapshot'.
 */
export async function hydrateFromMigrationSnapshot(): Promise<HydrationResult> {
  const errors: string[] = [];
  let nutritionLogsImported = 0;
  let nutritionLogsDiscarded = 0;
  let settingsImported = false;
  let wellbeingImported = false;

  // ── 1. Nutrición ──────────────────────────────────────────────────────────
  try {
    const nutritionPayload = readDomainPayload('nutrition');
    if (nutritionPayload !== null && typeof nutritionPayload === 'object' && !Array.isArray(nutritionPayload)) {
      const np = nutritionPayload as Record<string, unknown>;
      const rawLogs = Array.isArray(np.nutritionLogs) ? np.nutritionLogs : [];

      for (const raw of rawLogs) {
        const entry = adaptNutritionLog(raw);
        if (entry) {
          // Verificamos si ya existe para ser idempotentes
          const db = getMobileDatabase();
          const existing = db.execute('SELECT id FROM nutrition_logs WHERE id = ?', [entry.id]);
          if ((existing.rows?._array?.length ?? 0) === 0) {
            await persistNutritionLog(entry);
            nutritionLogsImported++;
          }
        } else {
          nutritionLogsDiscarded++;
        }
      }

      // Meal templates: guardamos en storage RN propio para no depender del namespace migration.*
      if (Array.isArray(np.mealTemplates) && np.mealTemplates.length > 0) {
        persistStoredMealTemplatesRaw(np.mealTemplates);
      }
    }
  } catch (error) {
    const msg = `Error rehidratando nutrición: ${error instanceof Error ? error.message : 'desconocido'}`;
    console.error('[MigrationHydration]', msg);
    errors.push(msg);
  }

  // ── 2. Settings ───────────────────────────────────────────────────────────
  try {
    const settingsPayload = readDomainPayload('settings');
    if (settingsPayload !== null && typeof settingsPayload === 'object' && !Array.isArray(settingsPayload)) {
      persistStoredSettingsRaw(settingsPayload as Record<string, unknown>);
      settingsImported = true;
    }
  } catch (error) {
    const msg = `Error rehidratando settings: ${error instanceof Error ? error.message : 'desconocido'}`;
    console.error('[MigrationHydration]', msg);
    errors.push(msg);
  }

  // ── 3. Wellbeing ──────────────────────────────────────────────────────────
  try {
    const wellbeingPayload = readDomainPayload('wellbeing');
    if (wellbeingPayload !== null && typeof wellbeingPayload === 'object' && !Array.isArray(wellbeingPayload)) {
      const wp = wellbeingPayload as Record<string, unknown>;

      persistStoredWellbeingPayload({
        tasks: Array.isArray(wp.tasks) ? wp.tasks : [],
        sleepLogs: Array.isArray(wp.sleepLogs) ? wp.sleepLogs : [],
        waterLogs: Array.isArray(wp.waterLogs) ? wp.waterLogs : [],
        dailyWellbeingLogs: Array.isArray(wp.dailyWellbeingLogs) ? wp.dailyWellbeingLogs : [],
      });
      wellbeingImported = true;
    }
  } catch (error) {
    const msg = `Error rehidratando wellbeing: ${error instanceof Error ? error.message : 'desconocido'}`;
    console.error('[MigrationHydration]', msg);
    errors.push(msg);
  }

  return {
    nutritionLogsImported,
    nutritionLogsDiscarded,
    settingsImported,
    wellbeingImported,
    workoutStatus: 'available-in-overview',
    errors,
  };
}
