import type { MigrationSnapshotV1, MigrationIntegritySnapshot } from '@kpkn/shared-types';
/**
 * Genera un recordCounts válido con valores por defecto.
 */
export function makeRecordCounts(
  overrides: Partial<MigrationIntegritySnapshot['recordCounts']> = {},
): MigrationIntegritySnapshot['recordCounts'] {
  return {
    programs: 0,
    workoutHistory: 0,
    nutritionLogs: 0,
    pantryItems: 0,
    mealTemplates: 0,
    sleepLogs: 0,
    waterLogs: 0,
    tasks: 0,
    ...overrides,
  };
}
/**
 * Genera un integrity snapshot válido con todos los flags en true.
 */
export function makeIntegrity(
  overrides: Partial<MigrationIntegritySnapshot> = {},
): MigrationIntegritySnapshot {
  return {
    settingsHydrated: true,
    workoutHydrated: true,
    nutritionHydrated: true,
    wellbeingHydrated: true,
    exerciseHydrated: true,
    recordCounts: makeRecordCounts(overrides.recordCounts),
    ...overrides,
  };
}
/**
 * Genera un MigrationSnapshotV1 mínimo y válido.
 * Todos los arrays del payload están vacíos por defecto.
 */
export function makeValidSnapshot(
  overrides: Partial<MigrationSnapshotV1> = {},
): MigrationSnapshotV1 {
  return {
    schemaVersion: 1,
    createdAt: '2025-06-01T12:00:00.000Z',
    appVersion: '1.4.0',
    integrity: makeIntegrity(),
    payload: {
      settings: { theme: 'dark', language: 'es' },
      programs: { programs: [], activeProgramState: null },
      workout: { history: [], skippedLogs: [], ongoingWorkout: null, syncQueue: [] },
      nutrition: {
        nutritionLogs: [],
        pantryItems: [],
        foodDatabase: [],
        aiNutritionPlan: null,
        nutritionPlans: [],
        activeNutritionPlanId: null,
        mealTemplates: [],
      },
      wellbeing: {
        sleepLogs: [],
        sleepStartTime: null,
        waterLogs: [],
        dailyWellbeingLogs: [],
        postSessionFeedback: [],
        pendingQuestionnaires: [],
        recommendationTriggers: [],
        tasks: [],
      },
      body: {
        bodyProgress: [],
        bodyLabAnalysis: null,
        biomechanicalData: null,
        biomechanicalAnalysis: null,
      },
      exercise: {
        exerciseList: [],
        exercisePlaylists: [],
        muscleGroupData: [],
        muscleHierarchy: {},
      },
    },
    ...overrides,
  };
}
/**
 * Genera un nutrition log "crudo" como llegaría desde la app Capacitor.
 */
export function makeRawNutritionLog(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: `log-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    description: '2 huevos revueltos con tostada',
    createdAt: '2025-06-01T08:30:00.000Z',
    totals: { calories: 320, protein: 22, carbs: 18, fats: 16 },
    ...overrides,
  };
}
/**
 * Genera un snapshot con N nutrition logs realistas.
 */
export function makeSnapshotWithNutritionLogs(count: number): MigrationSnapshotV1 {
  const logs = Array.from({ length: count }, (_, i) =>
    makeRawNutritionLog({
      id: `imported-log-${i}`,
      description: `Comida de test #${i}`,
      createdAt: new Date(2025, 5, 1, 8 + i).toISOString(),
      totals: { calories: 300 + i * 50, protein: 20 + i, carbs: 30 + i, fats: 10 + i },
    }),
  );
  return makeValidSnapshot({
    integrity: makeIntegrity({ recordCounts: makeRecordCounts({ nutritionLogs: count }) }),
    payload: {
      ...makeValidSnapshot().payload,
      nutrition: {
        ...makeValidSnapshot().payload.nutrition,
        nutritionLogs: logs,
      },
    },
  });
}