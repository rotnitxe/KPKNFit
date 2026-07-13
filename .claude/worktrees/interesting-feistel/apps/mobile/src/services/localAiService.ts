import { AppState } from 'react-native';
import type {
  LocalAiNutritionAnalysisRequest,
  LocalAiNutritionAnalysisResult,
  LocalAiStatus as ModuleLocalAiStatus,
} from '@kpkn/shared-types';
import { localAiModule, isLocalAiModuleAvailable } from '../modules/localAi';

export type LocalAiDeliveryMode =
  | 'install-time-pack'
  | 'bundled-asset'
  | 'web-fallback'
  | 'unavailable';

export type LocalAiBackend = 'cpu' | 'gpu' | 'unavailable';

export type LocalAiItemSource =
  | 'database'
  | 'user-memory'
  | 'local-ai-estimate'
  | 'local-heuristic'
  | 'fallback-estimate';

export type LocalAiExecutionEngine = 'runtime' | 'heuristics' | 'unavailable';

export interface LocalAiStatus {
  available: boolean;
  heuristicsAvailable?: boolean;
  modelReady: boolean;
  modelVersion: string | null;
  deliveryMode: ModuleLocalAiStatus['deliveryMode'] | 'web-fallback' | 'unavailable';
  backend: LocalAiBackend | string;
  engine: ModuleLocalAiStatus['engine'];
  lastError?: string | null;
}

export interface LocalAiNutritionRequest extends LocalAiNutritionAnalysisRequest {}

export interface LocalAiNutritionResult extends LocalAiNutritionAnalysisResult {}

const DEFAULT_STATUS: LocalAiStatus = {
  available: false,
  heuristicsAvailable: true,
  modelReady: false,
  modelVersion: null,
  deliveryMode: 'unavailable',
  backend: 'unavailable',
  engine: 'unavailable',
  lastError: null,
};

let cachedStatus: LocalAiStatus | null = null;
let cachedStatusAt = 0;
let lifecycleAttached = false;
let backgroundUnloadTimerId: ReturnType<typeof setTimeout> | null = null;

function mapStatus(status: ModuleLocalAiStatus): LocalAiStatus {
  const deliveryMode: LocalAiDeliveryMode =
    status.deliveryMode === 'install-time-pack' || status.deliveryMode === 'bundled-asset'
      ? status.deliveryMode
      : status.available
        ? 'bundled-asset'
        : 'unavailable';

  return {
    ...status,
    heuristicsAvailable: true,
    deliveryMode,
    backend: status.backend || 'unavailable',
  };
}

function cacheStatus(status: LocalAiStatus) {
  cachedStatus = status;
  cachedStatusAt = Date.now();
  return status;
}

export function isNativeLocalAiPlatform(): boolean {
  return isLocalAiModuleAvailable;
}

export async function getLocalAiStatus(forceRefresh = false): Promise<LocalAiStatus> {
  if (!forceRefresh && cachedStatus && Date.now() - cachedStatusAt < 10_000) {
    return cachedStatus;
  }

  try {
    const status = await localAiModule.getStatus();
    return cacheStatus(mapStatus(status));
  } catch (error) {
    return cacheStatus({
      ...DEFAULT_STATUS,
      lastError: error instanceof Error ? error.message : 'No se pudo leer el estado de IA local.',
    });
  }
}

export async function warmupLocalAi(): Promise<LocalAiStatus> {
  try {
    const status = await localAiModule.warmup();
    return cacheStatus(mapStatus(status));
  } catch (error) {
    return cacheStatus({
      ...(await getLocalAiStatus(true)),
      lastError: error instanceof Error ? error.message : 'No se pudo iniciar la IA local.',
    });
  }
}

export async function analyzeLocalNutritionDescription(
  request: LocalAiNutritionRequest,
): Promise<LocalAiNutritionResult> {
  return localAiModule.analyzeNutritionDescription(request);
}

export async function cancelCurrentLocalAiAnalysis(): Promise<void> {
  try {
    await localAiModule.cancelCurrentAnalysis();
  } catch {
    // Ignore cancellation failures; stale responses are guarded at store/service level.
  }
}

export async function unloadLocalAi(): Promise<void> {
  try {
    await localAiModule.unload();
  } finally {
    cachedStatusAt = 0;
  }
}

export async function resetLocalAiRuntime(): Promise<LocalAiStatus> {
  await unloadLocalAi();
  return getLocalAiStatus(true);
}

export async function ensureLocalAiLifecycleBridge(): Promise<void> {
  if (lifecycleAttached || !isNativeLocalAiPlatform()) return;

  AppState.addEventListener('change', nextState => {
    if (nextState === 'active') {
      if (backgroundUnloadTimerId) {
        clearTimeout(backgroundUnloadTimerId);
        backgroundUnloadTimerId = null;
      }
      return;
    }

    if (!backgroundUnloadTimerId) {
      backgroundUnloadTimerId = setTimeout(() => {
        void unloadLocalAi();
        backgroundUnloadTimerId = null;
      }, 5 * 60 * 1000);
    }
  });

  lifecycleAttached = true;
}
