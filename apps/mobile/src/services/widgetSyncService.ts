import type { WidgetDashboardSnapshot, WorkoutOverview } from '@kpkn/shared-types';
import type { SavedNutritionEntry } from '../types/nutrition';
import { widgetModule } from '../modules/widgets';
import { persistWidgetSyncStatus, readWidgetSyncStatus, type WidgetSyncSource } from './mobileDomainStateService';

function getLocalDateString(date = new Date()) {
  return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')}`;
}

function withSyncMeta(snapshot: WidgetDashboardSnapshot, source: WidgetSyncSource): WidgetDashboardSnapshot {
  return {
    ...snapshot,
    widgetLastSyncAt: new Date().toISOString(),
    widgetSyncSource: source,
  };
}

function buildNutritionSnapshot(savedLogs: SavedNutritionEntry[]): WidgetDashboardSnapshot {
  const today = getLocalDateString();
  const totals = savedLogs
    .filter(log => log.createdAt.slice(0, 10) === today)
    .reduce(
      (acc, log) => ({
        calories: acc.calories + log.totals.calories,
        protein: acc.protein + log.totals.protein,
        carbs: acc.carbs + log.totals.carbs,
        fats: acc.fats + log.totals.fats,
      }),
      { calories: 0, protein: 0, carbs: 0, fats: 0 },
    );

  return {
    nutritionCaloriesToday: totals.calories,
    nutritionProteinToday: totals.protein,
    nutritionCarbsToday: totals.carbs,
    nutritionFatsToday: totals.fats,
  };
}

function buildWorkoutSnapshot(overview: WorkoutOverview | null): WidgetDashboardSnapshot {
  if (!overview) {
    return {
      nextSessionLabel: 'Sin programa activo',
      nextSessionProgramName: 'KPKN',
      effectiveVolumeToday: 0,
      effectiveVolumePlanned: 0,
      augeBatteryScore: 0,
      batteryCnsScore: 0,
      batteryMuscularScore: 0,
      batterySpinalScore: 0,
    };
  }

  const nextSession = overview.nextSession ?? overview.todaySession;

  return {
    nextSessionLabel: nextSession?.name ?? 'Sin sesión próxima',
    nextSessionProgramName: overview.activeProgramName ?? 'KPKN',
    effectiveVolumeToday: overview.completedSetsThisWeek,
    effectiveVolumePlanned: overview.plannedSetsThisWeek,
    augeBatteryScore: overview.battery?.overall,
    batteryCnsScore: overview.battery?.cns,
    batteryMuscularScore: overview.battery?.muscular,
    batterySpinalScore: overview.battery?.spinal,
  };
}

async function persistFreshSync(source: WidgetSyncSource) {
  const now = new Date().toISOString();
  persistWidgetSyncStatus({
    stale: false,
    lastAttemptAt: now,
    lastSuccessfulSyncAt: now,
    lastError: null,
    source,
  });
}

export async function markWidgetStateStale(reason: string, source: WidgetSyncSource = 'unknown') {
  const previous = readWidgetSyncStatus();
  const now = new Date().toISOString();
  persistWidgetSyncStatus({
    stale: true,
    lastAttemptAt: now,
    lastSuccessfulSyncAt: previous.lastSuccessfulSyncAt,
    lastError: reason,
    source,
  });

  try {
    await widgetModule.markStale(reason);
  } catch (error) {
    console.warn('[widgets] No se pudo marcar el estado stale del widget.', error);
  }
}

async function syncSnapshot(snapshot: WidgetDashboardSnapshot, source: WidgetSyncSource) {
  try {
    await widgetModule.syncDashboardState(withSyncMeta(snapshot, source));
    await persistFreshSync(source);
  } catch (error) {
    const reason = error instanceof Error ? error.message : 'widget-sync-failed';
    await markWidgetStateStale(reason, source);
    console.warn('[widgets] No se pudo sincronizar el widget.', error);
  }
}

export async function syncNutritionWidgetState(savedLogs: SavedNutritionEntry[], source: WidgetSyncSource = 'foreground') {
  await syncSnapshot(buildNutritionSnapshot(savedLogs), source);
}

export async function syncWorkoutWidgetState(overview: WorkoutOverview | null, source: WidgetSyncSource = 'foreground') {
  await syncSnapshot(buildWorkoutSnapshot(overview), source);
}

export async function refreshWidgetSyncHealth() {
  try {
    const native = await widgetModule.getStatus();
    const stale = native.stale || (native.lastSyncAtMs !== null && Date.now() - native.lastSyncAtMs > 24 * 60 * 60 * 1000);
    const next = {
      stale,
      lastAttemptAt: native.lastReloadAtMs ? new Date(native.lastReloadAtMs).toISOString() : null,
      lastSuccessfulSyncAt: native.lastSyncAtMs ? new Date(native.lastSyncAtMs).toISOString() : null,
      lastError: native.lastError,
      source: native.source,
    };
    persistWidgetSyncStatus(next);
    return next;
  } catch (error) {
    await markWidgetStateStale(error instanceof Error ? error.message : 'widget-status-failed');
    return readWidgetSyncStatus();
  }
}
