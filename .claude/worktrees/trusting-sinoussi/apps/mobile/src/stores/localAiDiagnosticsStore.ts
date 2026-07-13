import { create } from 'zustand';
import type {
  LocalAiDebugSnapshot,
  LocalAiNutritionAnalysisResult,
  LocalAiStatus,
  NutritionAnalysisEngine,
} from '@kpkn/shared-types';
import { localAiModule } from '../modules/localAi';
import { runLocalAiSmokeTest } from '../services/localAiSmokeTestService';

type DiagnosticsRefreshState = 'idle' | 'refreshing';
type SmokeTestState = 'idle' | 'running';

export interface LocalAiRunSnapshot {
  analyzedAt: string;
  descriptionPreview: string;
  engine: NutritionAnalysisEngine;
  itemCount: number;
  elapsedMs: number;
  overallConfidence: number;
  runtimeError: string | null;
  modelVersion: string | null;
}

interface LocalAiDiagnosticsState {
  status: LocalAiStatus | null;
  nativeDiagnostics: LocalAiDebugSnapshot | null;
  refreshState: DiagnosticsRefreshState;
  smokeTestState: SmokeTestState;
  smokeTestError: string | null;
  lastCheckedAt: string | null;
  recentRuns: LocalAiRunSnapshot[];
  refreshStatus: (mode?: 'status' | 'warmup') => Promise<void>;
  refreshNativeDiagnostics: () => Promise<void>;
  runSmokeTest: (description?: string) => Promise<void>;
  recordRun: (result: LocalAiNutritionAnalysisResult, description: string, fallbackReason?: string | null) => void;
}

function truncateDescription(value: string) {
  const normalized = value.replace(/\s+/g, ' ').trim();
  if (normalized.length <= 56) return normalized;
  return `${normalized.slice(0, 53)}...`;
}

function buildUnavailableStatus(message: string): LocalAiStatus {
  return {
    available: false,
    modelReady: false,
    modelVersion: null,
    deliveryMode: 'unknown',
    backend: 'react-native-fallback',
    engine: 'unavailable',
    lastError: message,
  };
}

export const useLocalAiDiagnosticsStore = create<LocalAiDiagnosticsState>((set, get) => ({
  status: null,
  nativeDiagnostics: null,
  refreshState: 'idle',
  smokeTestState: 'idle',
  smokeTestError: null,
  lastCheckedAt: null,
  recentRuns: [],
  refreshStatus: async (mode = 'status') => {
    if (get().refreshState === 'refreshing') return;

    set({ refreshState: 'refreshing' });
    try {
      const status = mode === 'warmup'
        ? await localAiModule.warmup()
        : await localAiModule.getStatus();
      const nativeDiagnostics = await localAiModule.getDiagnostics();

      set({
        status,
        nativeDiagnostics,
        refreshState: 'idle',
        lastCheckedAt: new Date().toISOString(),
      });
    } catch (error) {
      set({
        status: buildUnavailableStatus(error instanceof Error ? error.message : 'No se pudo leer el motor local.'),
        nativeDiagnostics: null,
        refreshState: 'idle',
        lastCheckedAt: new Date().toISOString(),
      });
    }
  },
  refreshNativeDiagnostics: async () => {
    try {
      const nativeDiagnostics = await localAiModule.getDiagnostics();
      set({ nativeDiagnostics });
    } catch (error) {
      set({
        nativeDiagnostics: {
          runtimeLoaded: false,
          modelPath: null,
          deliveryMode: 'unknown',
          runtimeFailureCount: 0,
          cooldownRemainingMs: 0,
          lastError: error instanceof Error ? error.message : 'No se pudo leer el diagnostico nativo.',
          lastWarmupAtMs: null,
          lastAnalysisAtMs: null,
          lastAnalysisEngine: 'unavailable',
          lastAnalysisElapsedMs: null,
          lastDescriptionPreview: null,
          recentEvents: [],
        },
      });
    }
  },
  runSmokeTest: async (description = '2 completos italianos') => {
    if (get().smokeTestState === 'running') return;

    set({
      smokeTestState: 'running',
      smokeTestError: null,
    });

    try {
      const result = await runLocalAiSmokeTest(description);
      get().recordRun(result, description, result.runtimeError ?? null);
      set({
        smokeTestState: 'idle',
        smokeTestError: null,
      });
      await get().refreshStatus('status');
    } catch (error) {
      set({
        smokeTestState: 'idle',
        smokeTestError: error instanceof Error ? error.message : 'No pudimos completar la prueba interna.',
      });
    }
  },
  recordRun: (result, description, fallbackReason = null) => {
    const runtimeError = result.runtimeError ?? fallbackReason;
    const snapshot: LocalAiRunSnapshot = {
      analyzedAt: new Date().toISOString(),
      descriptionPreview: truncateDescription(description),
      engine: result.engine,
      itemCount: result.items.length,
      elapsedMs: result.elapsedMs,
      overallConfidence: result.overallConfidence,
      runtimeError,
      modelVersion: result.modelVersion,
    };

    set(state => ({
      recentRuns: [snapshot, ...state.recentRuns].slice(0, 5),
    }));

    void get().refreshNativeDiagnostics();
  },
}));
