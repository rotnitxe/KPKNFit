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
import type { ExerciseCatalogEntry, ExercisePlaylist, MuscleGroupInfo, MuscleHierarchy } from '../types/workout';
import type { MuscleRole } from '@kpkn/shared-types';
import { getMobileDatabase } from '../storage/mobileDatabase';
import { persistNutritionLog } from './mobilePersistenceService';
import {
  persistStoredMealTemplatesRaw,
  persistStoredSettingsRaw,
  persistStoredWellbeingPayload,
} from './mobileDomainStateService';
import { setJsonValue } from '../storage/mmkv';

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
  bodyImported: boolean;
  exerciseImported: boolean;
  programsImported: boolean;
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
  let bodyImported = false;
  let exerciseImported = false;
  let programsImported = false;

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

  // ── 4. Body ────────────────────────────────────────────
  try {
    const bodyPayload = readDomainPayload('body');
    if (bodyPayload !== null) {
      bodyImported = true;
    }
  } catch (error) {
    const msg = `Error rehidratando body: ${error instanceof Error ? error.message : 'desconocido'}`;
    console.error('[MigrationHydration]', msg);
    errors.push(msg);
  }

      // ── 5. Exercise ────────────────────────────────────────
      try {
        const exercisePayload = readDomainPayload('exercise');
        if (exercisePayload !== null && typeof exercisePayload === 'object' && !Array.isArray(exercisePayload)) {
          const ep = exercisePayload as Record<string, unknown>;
          
          // Process exercise list
          if (Array.isArray(ep.exerciseList)) {
            // Validate and convert exercise entries
            const validExercises: ExerciseCatalogEntry[] = ep.exerciseList
              .filter((ex): ex is Record<string, unknown> => 
                ex !== null && 
                typeof ex === 'object' && 
                !Array.isArray(ex) &&
                typeof ex.id === 'string' &&
                ex.id.trim() !== '' &&
                typeof ex.name === 'string' &&
                ex.name.trim() !== ''
              )
              .map((ex: Record<string, unknown>) => {
                // Ensure required fields have proper types
                const exercise: ExerciseCatalogEntry = {
                  id: String(ex.id).trim(),
                  name: String(ex.name).trim(),
                  alias: typeof ex.alias === 'string' ? String(ex.alias).trim() : undefined,
                  description: String(ex.description ?? '').trim(),
                  category: String(ex.category ?? '').trim(),
                  type: (ex.type === 'A' || ex.type === 'Accesorio' || ex.type === 'Aislamiento') ? 
                        (ex.type as 'Básico' | 'Accesorio' | 'Aislamiento') : 'Básico',
                  tier: (ex.tier === 'T1' || ex.tier === 'T2' || ex.tier === 'T3') ? 
                        (ex.tier as 'T1' | 'T2' | 'T3') : undefined,
                  equipment: String(ex.equipment ?? '').trim(),
                  force: String(ex.force ?? '').trim(),
                  isCustom: Boolean(ex.isCustom),
                  bodyPart: (ex.bodyPart === 'upper' || ex.bodyPart === 'lower' || ex.bodyPart === 'full') ? 
                            (ex.bodyPart as 'upper' | 'lower' | 'full') : undefined,
                  isFavorite: Boolean(ex.isFavorite),
                  variantOf: typeof ex.variantOf === 'string' ? String(ex.variantOf).trim() : undefined,
                  efc: Number.isFinite(Number(ex.efc)) ? Number(ex.efc) : undefined,
                  ssc: Number.isFinite(Number(ex.ssc)) ? Number(ex.ssc) : undefined,
                  cnc: Number.isFinite(Number(ex.cnc)) ? Number(ex.cnc) : undefined,
                  ttc: Number.isFinite(Number(ex.ttc)) ? Number(ex.ttc) : undefined,
                  involvedMuscles: Array.isArray(ex.involvedMuscles) 
                    ? ex.involvedMuscles
                      .filter((mus): mus is Record<string, unknown> => 
                        mus !== null && 
                        typeof mus === 'object' && 
                        !Array.isArray(mus) &&
                        typeof mus.muscle === 'string' &&
                        typeof mus.role === 'string' &&
                        (mus.activation === undefined || typeof mus.activation === 'number')
                      )
                      .map((mus: Record<string, unknown>) => ({
                        muscle: String(mus.muscle).trim(),
                        role: (mus.role === 'primary' || mus.role === 'secondary' || mus.role === 'stabilizer') ? 
                              (mus.role as MuscleRole) : 'secondary',
                        activation: mus.activation !== undefined && Number.isFinite(Number(mus.activation)) 
                          ? Number(mus.activation) : undefined
                      }))
                    : []
                };
                
                return exercise;
              });
            
            // Process exercise playlists
            const validPlaylists: ExercisePlaylist[] = Array.isArray(ep.exercisePlaylists)
              ? ep.exercisePlaylists
                .filter((pl): pl is Record<string, unknown> => 
                  pl !== null && 
                  typeof pl === 'object' && 
                  !Array.isArray(pl) &&
                  typeof pl.id === 'string' &&
                  pl.id.trim() !== '' &&
                  typeof pl.name === 'string' &&
                  pl.name.trim() !== '' &&
                  Array.isArray(pl.exerciseIds)
                )
                .map((pl: Record<string, unknown>) => ({
                  id: String(pl.id).trim(),
                  name: String(pl.name).trim(),
                  description: typeof pl.description === 'string' ? String(pl.description).trim() : '',
                  exerciseIds: Array.isArray(pl.exerciseIds) 
                    ? pl.exerciseIds.map(id => String(id).trim()).filter(Boolean)
                    : []
                }))
              : [];
            
            // Process muscle group data
            const validMuscleGroups: MuscleGroupInfo[] = Array.isArray(ep.muscleGroupData)
              ? ep.muscleGroupData
                .filter((mg): mg is Record<string, unknown> => 
                  mg !== null && 
                  typeof mg === 'object' && 
                  !Array.isArray(mg) &&
                  typeof mg.id === 'string' &&
                  mg.id.trim() !== '' &&
                  typeof mg.name === 'string' &&
                  mg.name.trim() !== ''
                )
                .map((mg: Record<string, unknown>) => ({
                  id: String(mg.id).trim(),
                  name: String(mg.name).trim(),
                  description: typeof mg.description === 'string' ? String(mg.description).trim() : '',
                  importance: typeof mg.importance === 'object' && mg.importance !== null 
                    ? {
                        movement: String((mg.importance as Record<string, unknown>).movement ?? ''),
                        health: String((mg.importance as Record<string, unknown>).health ?? '')
                      }
                    : { movement: '', health: '' },
                  volumeRecommendations: typeof mg.volumeRecommendations === 'object' && mg.volumeRecommendations !== null
                    ? {
                        mev: String((mg.volumeRecommendations as Record<string, unknown>).mev ?? '0'),
                        mav: String((mg.volumeRecommendations as Record<string, unknown>).mav ?? '0'),
                        mrv: String((mg.volumeRecommendations as Record<string, unknown>).mrv ?? '0')
                      }
                    : { mev: '0', mav: '0', mrv: '0' }
                }))
              : [];
            
            // Process muscle hierarchy
            const validMuscleHierarchy: MuscleHierarchy | null = 
              typeof ep.muscleHierarchy === 'object' && ep.muscleHierarchy !== null && !Array.isArray(ep.muscleHierarchy)
                ? ep.muscleHierarchy as MuscleHierarchy
                : null;
            
            // Save to MMKV storage
            setJsonValue('rn.exercise', { 
              exerciseList: validExercises, 
              exercisePlaylists: validPlaylists,
              muscleGroupData: validMuscleGroups,
              muscleHierarchy: validMuscleHierarchy
            });
            
            exerciseImported = true;
          }
        }
      } catch (error) {
        const msg = `Error rehidratando exercise: ${error instanceof Error ? error.message : 'desconocido'}`;
        console.error('[MigrationHydration]', msg);
        errors.push(msg);
      }

  // ── 6. Programs ────────────────────────────────────────
  try {
    const programsPayload = readDomainPayload('programs');
    if (programsPayload !== null) {
      programsImported = true;
    }
  } catch (error) {
    const msg = `Error rehidratando programs: ${error instanceof Error ? error.message : 'desconocido'}`;
    console.error('[MigrationHydration]', msg);
    errors.push(msg);
  }

  return {
    nutritionLogsImported,
    nutritionLogsDiscarded,
    settingsImported,
    wellbeingImported,
    bodyImported,
    exerciseImported,
    programsImported,
    workoutStatus: 'available-in-overview',
    errors,
  };
}
