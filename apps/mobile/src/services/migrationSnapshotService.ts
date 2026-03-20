import type { MigrationSnapshotV1 } from '@kpkn/shared-types';
import {
  persistMigrationSnapshot,
  getPersistedMigrationSummary,
  loadPersistedDomainPayload,
} from './mobilePersistenceService';

export const MIGRATION_SNAPSHOT_SCHEMA_VERSION = 1;
export const MIGRATION_SNAPSHOT_PATH = 'mobile://migration/snapshot-v1';

export interface MigrationSnapshotSource {
  snapshot: MigrationSnapshotV1;
}

export function buildMigrationSnapshotV1(source: MigrationSnapshotSource): MigrationSnapshotV1 {
  return source.snapshot;
}

export async function exportMigrationSnapshotV1(snapshot: MigrationSnapshotV1) {
  await persistMigrationSnapshot(snapshot);
  return {
    ok: true,
    path: MIGRATION_SNAPSHOT_PATH,
    exportedAt: new Date().toISOString(),
    schemaVersion: snapshot.schemaVersion,
  };
}

export function scheduleMigrationSnapshotExport(source: MigrationSnapshotSource) {
  return exportMigrationSnapshotV1(buildMigrationSnapshotV1(source));
}

export async function readMigrationSnapshotV1(): Promise<MigrationSnapshotV1 | null> {
  const summary = await getPersistedMigrationSummary();
  if (!summary) return null;

  const payload: MigrationSnapshotV1['payload'] = {
    settings: (await loadPersistedDomainPayload('settings')) ?? {},
    programs: {
      programs: (await loadPersistedDomainPayload<unknown[]>('program.programs')) ?? [],
      activeProgramState:
        (await loadPersistedDomainPayload('program.activeProgramState')) ?? null,
    },
    workout: {
      history: (await loadPersistedDomainPayload<unknown[]>('workout.history')) ?? [],
      skippedLogs: (await loadPersistedDomainPayload<unknown[]>('workout.skippedLogs')) ?? [],
      ongoingWorkout: (await loadPersistedDomainPayload('workout.ongoingWorkout')) ?? null,
      syncQueue: (await loadPersistedDomainPayload<unknown[]>('workout.syncQueue')) ?? [],
    },
    nutrition: {
      nutritionLogs: (await loadPersistedDomainPayload<unknown[]>('nutrition.logs')) ?? [],
      pantryItems: (await loadPersistedDomainPayload<unknown[]>('nutrition.pantryItems')) ?? [],
      foodDatabase: (await loadPersistedDomainPayload<unknown[]>('nutrition.foodDatabase')) ?? [],
      aiNutritionPlan: (await loadPersistedDomainPayload('nutrition.aiPlan')) ?? null,
      nutritionPlans: (await loadPersistedDomainPayload<unknown[]>('nutrition.plans')) ?? [],
      activeNutritionPlanId:
        (await loadPersistedDomainPayload<string | null>('nutrition.activePlanId')) ?? null,
      mealTemplates: (await loadPersistedDomainPayload<unknown[]>('mealTemplate.templates')) ?? [],
    },
    wellbeing: {
      sleepLogs: (await loadPersistedDomainPayload<unknown[]>('wellbeing.sleepLogs')) ?? [],
      sleepStartTime:
        (await loadPersistedDomainPayload<number | null>('wellbeing.sleepStartTime')) ?? null,
      waterLogs: (await loadPersistedDomainPayload<unknown[]>('wellbeing.waterLogs')) ?? [],
      dailyWellbeingLogs:
        (await loadPersistedDomainPayload<unknown[]>('wellbeing.dailyWellbeingLogs')) ?? [],
      postSessionFeedback:
        (await loadPersistedDomainPayload<unknown[]>('wellbeing.postSessionFeedback')) ?? [],
      pendingQuestionnaires:
        (await loadPersistedDomainPayload<unknown[]>('wellbeing.pendingQuestionnaires')) ?? [],
      recommendationTriggers:
        (await loadPersistedDomainPayload<unknown[]>('wellbeing.recommendationTriggers')) ?? [],
      tasks: (await loadPersistedDomainPayload<unknown[]>('wellbeing.tasks')) ?? [],
    },
    body: {
      bodyProgress: (await loadPersistedDomainPayload<unknown[]>('body.progress')) ?? [],
      bodyLabAnalysis: (await loadPersistedDomainPayload('body.labAnalysis')) ?? null,
      biomechanicalData: (await loadPersistedDomainPayload('body.biomechanicalData')) ?? null,
      biomechanicalAnalysis:
        (await loadPersistedDomainPayload('body.biomechanicalAnalysis')) ?? null,
    },
    exercise: {
      exerciseList: (await loadPersistedDomainPayload<unknown[]>('exercise.list')) ?? [],
      exercisePlaylists:
        (await loadPersistedDomainPayload<unknown[]>('exercise.playlists')) ?? [],
      muscleGroupData:
        (await loadPersistedDomainPayload<unknown[]>('exercise.muscleGroupData')) ?? [],
      muscleHierarchy: (await loadPersistedDomainPayload('exercise.muscleHierarchy')) ?? {},
    },
  };

  return {
    schemaVersion: MIGRATION_SNAPSHOT_SCHEMA_VERSION,
    appVersion: summary.appVersion,
    createdAt: summary.createdAt,
    payload,
    integrity: summary.integrity,
  };
}
