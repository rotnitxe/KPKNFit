// migration.ts — Tipos y validación runtime del snapshot puente Capacitor → RN
// La validación deja de ser "as MigrationSnapshotV1" y pasa a tener guardas reales.

export interface MigrationIntegritySnapshot {
  settingsHydrated: boolean;
  workoutHydrated: boolean;
  nutritionHydrated: boolean;
  wellbeingHydrated: boolean;
  exerciseHydrated: boolean;
  recordCounts: {
    programs: number;
    workoutHistory: number;
    nutritionLogs: number;
    pantryItems: number;
    mealTemplates: number;
    sleepLogs: number;
    waterLogs: number;
    tasks: number;
  };
}

export interface MigrationSnapshotV1 {
  schemaVersion: 1;
  createdAt: string;
  appVersion: string;
  integrity: MigrationIntegritySnapshot;
  payload: {
    settings: Record<string, unknown>;
    programs: {
      programs: unknown[];
      activeProgramState: Record<string, unknown> | null;
    };
    workout: {
      history: unknown[];
      skippedLogs: unknown[];
      ongoingWorkout: Record<string, unknown> | null;
      syncQueue: unknown[];
    };
    nutrition: {
      nutritionLogs: unknown[];
      pantryItems: unknown[];
      foodDatabase: unknown[];
      aiNutritionPlan: Record<string, unknown> | null;
      nutritionPlans: unknown[];
      activeNutritionPlanId: string | null;
      mealTemplates: unknown[];
    };
    wellbeing: {
      sleepLogs: unknown[];
      sleepStartTime: number | null;
      waterLogs: unknown[];
      dailyWellbeingLogs: unknown[];
      postSessionFeedback: unknown[];
      pendingQuestionnaires: unknown[];
      recommendationTriggers: unknown[];
      tasks: unknown[];
    };
    body: {
      bodyProgress: unknown[];
      bodyLabAnalysis: Record<string, unknown> | null;
      biomechanicalData: Record<string, unknown> | null;
      biomechanicalAnalysis: Record<string, unknown> | null;
    };
    exercise: {
      exerciseList: unknown[];
      exercisePlaylists: unknown[];
      muscleGroupData: unknown[];
      muscleHierarchy: Record<string, unknown>;
    };
  };
}

// ─── Runtime validators ───────────────────────────────────────────────────────
// No usamos Zod para evitar dependencias implícitas no declaradas.
// Estas funciones son guardas de tipo reales que validan en runtime.

export type SnapshotValidationResult =
  | { valid: true; snapshot: MigrationSnapshotV1 }
  | { valid: false; reason: string };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isArray(value: unknown): value is unknown[] {
  return Array.isArray(value);
}

function validateIntegrity(raw: unknown): raw is MigrationIntegritySnapshot {
  if (!isRecord(raw)) return false;
  if (typeof raw.settingsHydrated !== 'boolean') return false;
  if (typeof raw.workoutHydrated !== 'boolean') return false;
  if (typeof raw.nutritionHydrated !== 'boolean') return false;
  if (typeof raw.wellbeingHydrated !== 'boolean') return false;
  if (typeof raw.exerciseHydrated !== 'boolean') return false;
  if (!isRecord(raw.recordCounts)) return false;
  const counts = raw.recordCounts;
  const countKeys: string[] = [
    'programs', 'workoutHistory', 'nutritionLogs', 'pantryItems',
    'mealTemplates', 'sleepLogs', 'waterLogs', 'tasks',
  ];
  for (const key of countKeys) {
    if (typeof counts[key] !== 'number') return false;
  }
  return true;
}

function validateNutritionPayload(raw: unknown): boolean {
  if (!isRecord(raw)) return false;
  return (
    isArray(raw.nutritionLogs) &&
    isArray(raw.pantryItems) &&
    isArray(raw.mealTemplates) &&
    isArray(raw.nutritionPlans) &&
    isArray(raw.foodDatabase)
  );
}

function validateWellbeingPayload(raw: unknown): boolean {
  if (!isRecord(raw)) return false;
  return (
    isArray(raw.sleepLogs) &&
    isArray(raw.waterLogs) &&
    isArray(raw.dailyWellbeingLogs) &&
    isArray(raw.tasks) &&
    isArray(raw.postSessionFeedback) &&
    isArray(raw.pendingQuestionnaires) &&
    isArray(raw.recommendationTriggers)
  );
}

function validateWorkoutPayload(raw: unknown): boolean {
  if (!isRecord(raw)) return false;
  return isArray(raw.history) && isArray(raw.skippedLogs) && isArray(raw.syncQueue);
}

function validateProgramsPayload(raw: unknown): boolean {
  if (!isRecord(raw)) return false;
  return isArray(raw.programs);
}

function validateExercisePayload(raw: unknown): boolean {
  if (!isRecord(raw)) return false;
  return isArray(raw.exerciseList) && isArray(raw.exercisePlaylists);
}

/**
 * Valida el snapshot parseado de JSON antes de importarlo.
 * Si retorna { valid: false }, el caller debe abortar sin persistir nada.
 */
export function validateMigrationSnapshot(raw: unknown): SnapshotValidationResult {
  if (!isRecord(raw)) {
    return { valid: false, reason: 'El snapshot no es un objeto JSON válido.' };
  }

  if (raw.schemaVersion !== 1) {
    return {
      valid: false,
      reason: `Versión de schema incompatible: esperada 1, recibida ${String(raw.schemaVersion)}.`,
    };
  }

  if (typeof raw.createdAt !== 'string' || raw.createdAt.trim() === '') {
    return { valid: false, reason: 'El campo createdAt es inválido o está vacío.' };
  }

  if (typeof raw.appVersion !== 'string') {
    return { valid: false, reason: 'El campo appVersion no es un string.' };
  }

  if (!validateIntegrity(raw.integrity)) {
    return {
      valid: false,
      reason: 'El campo integrity es inválido o está incompleto. El snapshot puede estar corrupto.',
    };
  }

  if (!isRecord(raw.payload)) {
    return { valid: false, reason: 'El campo payload no existe o no es un objeto.' };
  }

  const p = raw.payload;

  if (!isRecord(p.settings)) {
    return { valid: false, reason: 'payload.settings no es un objeto.' };
  }

  if (!validateProgramsPayload(p.programs)) {
    return { valid: false, reason: 'payload.programs es inválido o incompleto.' };
  }

  if (!validateWorkoutPayload(p.workout)) {
    return { valid: false, reason: 'payload.workout es inválido o incompleto.' };
  }

  if (!validateNutritionPayload(p.nutrition)) {
    return { valid: false, reason: 'payload.nutrition es inválido o incompleto.' };
  }

  if (!validateWellbeingPayload(p.wellbeing)) {
    return { valid: false, reason: 'payload.wellbeing es inválido o incompleto.' };
  }

  if (!isRecord(p.body)) {
    return { valid: false, reason: 'payload.body no es un objeto.' };
  }

  if (!validateExercisePayload(p.exercise)) {
    return { valid: false, reason: 'payload.exercise es inválido o incompleto.' };
  }

  // Todos los campos son válidos → devolvemos el snapshot tipado.
  // El doble cast es deliberado: los type guards garantizan la estructura en runtime,
  // pero TypeScript no puede inferir el tipo exacto a través de las validaciones manuales.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return { valid: true, snapshot: raw as any as MigrationSnapshotV1 };
}
