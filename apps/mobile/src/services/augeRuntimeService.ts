import { computeAugeReadiness } from '@kpkn/shared-domain';
import { useWorkoutStore } from '../stores/workoutStore';
import { useWellbeingStore } from '../stores/wellbeingStore';
import { readStoredSettingsRaw } from './mobileDomainStateService';
import { AugeRuntimeSnapshot, AugeRuntimeDebug } from '../types/augeRuntime';

export function buildAugeRuntimeInputs() {
  const workoutOverview = useWorkoutStore.getState().overview;
  const wellbeingOverview = useWellbeingStore.getState().overview;
  const settings = readStoredSettingsRaw();

  const hasWorkoutOverview = !!workoutOverview;
  const hasWellbeingSnapshot = !!wellbeingOverview?.latestSnapshot;
  const hasSettings = !!settings;

  const cnsBattery = workoutOverview?.battery?.overall ?? 60;

  const adaptiveCache = {
    cnsDelta: 0,
    muscularDelta: 0,
    spinalDelta: 0,
    lastCalibrated: new Date().toISOString(),
  };

  const configForEngine = {
    settings: settings || {},
    adaptiveCache,
    wellbeing: wellbeingOverview?.latestSnapshot || null,
    history: [], // Base implementation
    cnsBattery,
  };

  const runtimeDebug: AugeRuntimeDebug = {
    hasWorkoutOverview,
    hasWellbeingSnapshot,
    hasSettings,
    wellbeingStressLevel: wellbeingOverview?.latestSnapshot?.stressLevel ?? null,
    wellbeingSleepHours: (wellbeingOverview?.latestSnapshot as any)?.sleepHours ?? null,
  };

  const summaryBase = {
    activeProgramName: workoutOverview?.activeProgramName || null,
    weeklySessionCount: workoutOverview?.weeklySessionCount || 0,
    completedSetsThisWeek: workoutOverview?.completedSetsThisWeek || 0,
    plannedSetsThisWeek: workoutOverview?.plannedSetsThisWeek || 0,
  };

  return {
    configForEngine,
    runtimeDebug,
    summaryBase,
  };
}

export async function computeAugeRuntimeSnapshot(): Promise<{
  snapshot: AugeRuntimeSnapshot;
  debug: AugeRuntimeDebug;
}> {
  const { configForEngine, runtimeDebug, summaryBase } = buildAugeRuntimeInputs();

  try {
    const result = computeAugeReadiness(configForEngine as any);

    const snapshot: AugeRuntimeSnapshot = {
      computedAt: new Date().toISOString(),
      cnsBattery: configForEngine.cnsBattery,
      readinessStatus: result.status as 'green' | 'yellow' | 'red',
      stressMultiplier: result.stressMultiplier,
      recommendation: result.recommendation,
      diagnostics: result.diagnostics || [],
      ...summaryBase,
    };

    return { snapshot, debug: runtimeDebug };
  } catch (error) {
    console.error('[AugeRuntimeService] Error computing readiness:', error);
    
    // Fallback safe snapshot
    const fallbackSnapshot: AugeRuntimeSnapshot = {
      computedAt: new Date().toISOString(),
      cnsBattery: configForEngine.cnsBattery,
      readinessStatus: 'yellow',
      stressMultiplier: 1.0,
      recommendation: 'Proceder con precaución. No se pudo calcular el estado exacto.',
      diagnostics: ['Error en motor AUGE'],
      ...summaryBase,
    };

    return { snapshot: fallbackSnapshot, debug: runtimeDebug };
  }
}
