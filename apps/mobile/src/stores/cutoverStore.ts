import { create } from 'zustand';
import { getJsonValue, setJsonValue } from '../storage/mmkv';
import { getNotificationPermissionSummary } from '../services/mobileNotificationService';
import { getPersistedMigrationSummary } from '../services/mobilePersistenceService';
import { isBackgroundModuleAvailable, backgroundModule } from '../modules/background';
import { isWidgetModuleAvailable, widgetModule } from '../modules/widgets';
import { isMigrationBridgeAvailable } from '../modules/migrationBridge';
import { isLocalAiModuleAvailable } from '../modules/localAi';
import { readBackgroundSyncStatus, readWidgetSyncStatus } from '../services/mobileDomainStateService';
import { refreshWidgetSyncHealth } from '../services/widgetSyncService';
import { useBootstrapStore } from './bootstrapStore';
import { useLocalAiDiagnosticsStore } from './localAiDiagnosticsStore';
import { useMealTemplateStore } from './mealTemplateStore';
import { useMobileNutritionStore } from './nutritionStore';
import { useSettingsStore } from './settingsStore';
import { useWellbeingStore } from './wellbeingStore';
import { useWorkoutStore } from './workoutStore';

type CutoverStage = 'needs-work' | 'pilot-ready' | 'ready-for-cutover';

type ManualSignoffKey =
  | 'legacyUpgradeVerified'
  | 'offlineColdStartVerified'
  | 'widgetsVerified'
  | 'backgroundVerified'
  | 'notificationsVerified'
  | 'nutritionFlowVerified';

interface CutoverSystemChecklist {
  migrationBridgeReady: boolean;
  migrationDataReady: boolean;
  nutritionReady: boolean;
  workoutReady: boolean;
  settingsReady: boolean;
  wellbeingReady: boolean;
  templatesReady: boolean;
  widgetsReady: boolean;
  backgroundReady: boolean;
  notificationsReady: boolean;
  localAiReady: boolean;
}

interface CutoverManualSignoff {
  legacyUpgradeVerified: boolean;
  offlineColdStartVerified: boolean;
  widgetsVerified: boolean;
  backgroundVerified: boolean;
  notificationsVerified: boolean;
  nutritionFlowVerified: boolean;
}

interface CutoverOperationalSnapshot {
  notificationPermission: 'authorized' | 'blocked' | 'unsupported';
  widgetModuleAvailable: boolean;
  backgroundModuleAvailable: boolean;
  migrationBridgeAvailable: boolean;
  localAiModuleAvailable: boolean;
  widgetStale: boolean;
  backgroundLastResult: 'idle' | 'running' | 'success' | 'failure' | 'dispatching';
  nutritionLogCount: number;
  templateCount: number;
  wellbeingHasData: boolean;
  lastSweepAt: string | null;
}

interface CutoverStoreState {
  stage: CutoverStage;
  systemChecklist: CutoverSystemChecklist | null;
  manualSignoff: CutoverManualSignoff;
  operationalSnapshot: CutoverOperationalSnapshot | null;
  lastCheckedAt: string | null;
  notice: string | null;
  refresh: () => Promise<void>;
  runOperationalSweep: () => Promise<void>;
  toggleManualSignoff: (key: ManualSignoffKey) => void;
  clearNotice: () => void;
}

const MANUAL_SIGNOFF_KEY = 'cutover.manualSignoff';

const DEFAULT_SIGNOFF: CutoverManualSignoff = {
  legacyUpgradeVerified: false,
  offlineColdStartVerified: false,
  widgetsVerified: false,
  backgroundVerified: false,
  notificationsVerified: false,
  nutritionFlowVerified: false,
};

function readManualSignoff() {
  return getJsonValue<CutoverManualSignoff>(MANUAL_SIGNOFF_KEY, DEFAULT_SIGNOFF);
}

function persistManualSignoff(signoff: CutoverManualSignoff) {
  setJsonValue(MANUAL_SIGNOFF_KEY, signoff);
}

function computeStage(systemChecklist: CutoverSystemChecklist, manualSignoff: CutoverManualSignoff): CutoverStage {
  const systemPassed = Object.values(systemChecklist).every(Boolean);
  const manualPassed = Object.values(manualSignoff).every(Boolean);

  if (systemPassed && manualPassed) return 'ready-for-cutover';
  if (systemPassed) return 'pilot-ready';
  return 'needs-work';
}

function hasExpectedNutritionData(expectedNutritionLogs: number | undefined, currentCount: number) {
  return (expectedNutritionLogs ?? 0) === 0 || currentCount > 0;
}

export const useCutoverStore = create<CutoverStoreState>((set, get) => ({
  stage: 'needs-work',
  systemChecklist: null,
  manualSignoff: readManualSignoff(),
  operationalSnapshot: null,
  lastCheckedAt: null,
  notice: null,

  refresh: async () => {
    const bootstrap = useBootstrapStore.getState();
    const nutrition = useMobileNutritionStore.getState();
    const workout = useWorkoutStore.getState();
    const settings = useSettingsStore.getState();
    const wellbeing = useWellbeingStore.getState();
    const templates = useMealTemplateStore.getState();
    const localAi = useLocalAiDiagnosticsStore.getState();

    const [
      notificationSummary,
      persistedSummary,
      widgetNativeStatus,
      backgroundNativeStatus,
    ] = await Promise.all([
      getNotificationPermissionSummary(),
      getPersistedMigrationSummary(),
      refreshWidgetSyncHealth(),
      backgroundModule.getStatus(),
    ]);

    const manualSignoff = readManualSignoff();
    const widgetSyncStatus = readWidgetSyncStatus();
    const backgroundSyncStatus = readBackgroundSyncStatus();
    const recordCounts = persistedSummary?.integrity.recordCounts;
    const expectedWellbeingData = Boolean(
      recordCounts && (recordCounts.sleepLogs > 0 || recordCounts.waterLogs > 0 || recordCounts.tasks > 0),
    );
    const hasWellbeingData = Boolean(wellbeing.overview || wellbeing.tasks.length > 0);
    const nutritionReady = nutrition.hasHydrated && hasExpectedNutritionData(recordCounts?.nutritionLogs, nutrition.savedLogs.length);
    const workoutReady =
      workout.hasHydrated && (Boolean(workout.overview) || ((recordCounts?.programs ?? 0) === 0 && workout.status === 'empty'));
    const settingsReady = settings.status === 'ready' && settings.summary !== null;
    const wellbeingReady = wellbeing.status === 'ready' || (!expectedWellbeingData && wellbeing.status === 'empty');
    const templatesReady = templates.status === 'ready' || ((recordCounts?.mealTemplates ?? 0) === 0 && templates.status === 'empty');
    const widgetsReady = isWidgetModuleAvailable && !widgetSyncStatus.stale && widgetNativeStatus.stale === false;
    const backgroundReady =
      isBackgroundModuleAvailable &&
      (backgroundNativeStatus.lastResult === 'success' || backgroundSyncStatus.lastResult === 'success');
    const notificationsReady = notificationSummary.granted && Boolean(notificationSummary.lastScheduledAt);

    const systemChecklist: CutoverSystemChecklist = {
      migrationBridgeReady: isMigrationBridgeAvailable,
      migrationDataReady:
        bootstrap.status === 'ready' &&
        !bootstrap.error &&
        Boolean(persistedSummary) &&
        nutritionReady &&
        settingsReady,
      nutritionReady,
      workoutReady,
      settingsReady,
      wellbeingReady,
      templatesReady,
      widgetsReady,
      backgroundReady,
      notificationsReady,
      localAiReady:
        isLocalAiModuleAvailable &&
        (localAi.status?.engine === 'runtime' || Boolean(localAi.status?.modelVersion)),
    };

    set({
      manualSignoff,
      systemChecklist,
      operationalSnapshot: {
        notificationPermission: notificationSummary.status,
        widgetModuleAvailable: isWidgetModuleAvailable,
        backgroundModuleAvailable: isBackgroundModuleAvailable,
        migrationBridgeAvailable: isMigrationBridgeAvailable,
        localAiModuleAvailable: isLocalAiModuleAvailable,
        widgetStale: widgetSyncStatus.stale,
        backgroundLastResult:
          (backgroundSyncStatus.lastResult === 'running' ? 'running' : backgroundNativeStatus.lastResult) ?? 'idle',
        nutritionLogCount: nutrition.savedLogs.length,
        templateCount: templates.templates.length,
        wellbeingHasData: hasWellbeingData,
        lastSweepAt: get().operationalSnapshot?.lastSweepAt ?? null,
      },
      stage: computeStage(systemChecklist, manualSignoff),
      lastCheckedAt: new Date().toISOString(),
    });
  },

  runOperationalSweep: async () => {
    try {
      const [scheduleResult, immediateResult] = await Promise.all([
        backgroundModule.schedulePeriodicSync(),
        backgroundModule.runImmediateSync(),
      ]);
      await widgetModule.reloadWidget();
      await get().refresh();

      set(state => ({
        operationalSnapshot: state.operationalSnapshot
          ? {
              ...state.operationalSnapshot,
              lastSweepAt: new Date().toISOString(),
            }
          : state.operationalSnapshot,
        notice: `Sweep ok · background ${scheduleResult.scheduled && immediateResult.started ? 'programado' : 'parcial'} · widgets recargados.`,
      }));
    } catch (error) {
      set({
        notice: error instanceof Error ? error.message : 'No se pudo ejecutar el sweep operativo.',
      });
    }
  },

  toggleManualSignoff: key => {
    const current = readManualSignoff();
    const next = {
      ...current,
      [key]: !current[key],
    };
    persistManualSignoff(next);

    const systemChecklist = get().systemChecklist;
    set({
      manualSignoff: next,
      stage: systemChecklist ? computeStage(systemChecklist, next) : get().stage,
      notice: next[key] ? `${key} marcado como verificado.` : `${key} marcado como pendiente.`,
    });
  },

  clearNotice: () => set({ notice: null }),
}));
