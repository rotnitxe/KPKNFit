import { LOCAL_CHILEAN_FOOD_CATALOG } from '@kpkn/shared-domain';
import type { NutritionAnalysisEngine } from '@kpkn/shared-types';
import { widgetModule } from '../modules/widgets';
import { analyzeNutritionDraft } from './nutritionAnalyzer';
import { loadSmokeTestLogs, persistSmokeLog } from './mobilePersistenceService';
import type { SavedNutritionEntry } from '../types/nutrition';
import { generateId } from '../utils/generateId';

export interface NutritionFlowSmokeResult {
  description: string;
  engine: NutritionAnalysisEngine;
  elapsedMs: number;
  itemCount: number;
  calories: number;
  /**
   * true si el log fue correctamente escrito Y releído desde smoke_test_logs.
   * NOTA: smoke_test_logs es una tabla separada de nutrition_logs.
   * Correr este smoke test NO ensucia los datos reales del usuario.
   */
  persisted: boolean;
  savedEntryId: string;
  runtimeError: string | null;
}

function computeTotals(entry: SavedNutritionEntry['analysis']) {
  return entry.items.reduce(
    (totals, item) => ({
      calories: totals.calories + item.calories,
      protein: totals.protein + item.protein,
      carbs: totals.carbs + item.carbs,
      fats: totals.fats + item.fats,
    }),
    { calories: 0, protein: 0, carbs: 0, fats: 0 },
  );
}

export async function runNutritionFlowSmokeTest(
  description = '2 completos italianos',
): Promise<NutritionFlowSmokeResult> {
  const historicalLogs = await loadSmokeTestLogs(20);
  const result = await analyzeNutritionDraft({
    description,
    locale: 'es-CL',
    schemaVersion: 'rn-v1-smoke',
    knownFoods: LOCAL_CHILEAN_FOOD_CATALOG,
    userMemory: historicalLogs
      .slice(0, 20)
      .map(log => log.description)
      .filter(Boolean),
  });

  if (result.items.length === 0) {
    throw new Error('El analisis no devolvio items guardables.');
  }

  const totals = computeTotals(result);
  const entry: SavedNutritionEntry = {
    id: `smoke-${generateId()}`,
    description,
    createdAt: new Date().toISOString(),
    totals,
    analysis: result,
  };

  // Escribe en smoke_test_logs, NO en nutrition_logs.
  // Los datos de test nunca van a la base de datos real del usuario.
  await persistSmokeLog(entry);

  // Verificamos que el dato fue realmente escrito y es relible.
  const reloadedLogs = await loadSmokeTestLogs(10);
  const persisted = reloadedLogs.some(log => log.id === entry.id);

  try {
    await widgetModule.syncDashboardState({
      nutritionCaloriesToday: totals.calories,
      nutritionProteinToday: totals.protein,
      nutritionCarbsToday: totals.carbs,
      nutritionFatsToday: totals.fats,
    });
  } catch (error) {
    console.warn('El smoke flow de nutricion no pudo sincronizar widgets.', error);
  }

  return {
    description,
    engine: result.engine,
    elapsedMs: result.elapsedMs,
    itemCount: result.items.length,
    calories: totals.calories,
    persisted,
    savedEntryId: entry.id,
    runtimeError: result.runtimeError ?? null,
  };
}
