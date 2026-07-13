import { NativeModules } from 'react-native';
import type {
  LocalAiDebugSnapshot,
  LocalAiModuleContract,
  LocalAiNutritionAnalysisRequest,
  LocalAiNutritionAnalysisResult,
  LocalAiStatus,
} from '@kpkn/shared-types';
import { analyzeNutritionDescriptionLocally } from '@kpkn/shared-domain';

const fallbackStatus: LocalAiStatus = {
  available: false,
  modelReady: false,
  modelVersion: null,
  deliveryMode: 'unknown',
  backend: 'react-native-fallback',
  engine: 'unavailable',
  lastError: null,
};

const fallbackDiagnostics: LocalAiDebugSnapshot = {
  runtimeLoaded: false,
  modelPath: null,
  deliveryMode: 'unknown',
  runtimeFailureCount: 0,
  cooldownRemainingMs: 0,
  lastError: null,
  lastWarmupAtMs: null,
  lastAnalysisAtMs: null,
  lastAnalysisEngine: 'unavailable',
  lastAnalysisElapsedMs: null,
  lastDescriptionPreview: null,
  recentEvents: [],
};

const nativeModule = NativeModules.KPKNLocalAi as LocalAiModuleContract | undefined;
export const isLocalAiModuleAvailable = Boolean(nativeModule);

export const localAiModule: LocalAiModuleContract = nativeModule ? {
  async getStatus() {
    return nativeModule.getStatus?.() ?? fallbackStatus;
  },
  async getDiagnostics() {
    return nativeModule.getDiagnostics?.() ?? fallbackDiagnostics;
  },
  async warmup() {
    return nativeModule.warmup?.() ?? fallbackStatus;
  },
  async analyzeNutritionDescription(request: LocalAiNutritionAnalysisRequest): Promise<LocalAiNutritionAnalysisResult> {
    if (!nativeModule.analyzeNutritionDescription) {
      return analyzeNutritionDescriptionLocally(request);
    }
    return nativeModule.analyzeNutritionDescription(request);
  },
  async cancelCurrentAnalysis() {
    return nativeModule.cancelCurrentAnalysis?.() ?? { cancelled: true };
  },
  async unload() {
    return nativeModule.unload?.() ?? { unloaded: true };
  },
} : {
  async getStatus() {
    return fallbackStatus;
  },
  async getDiagnostics() {
    return fallbackDiagnostics;
  },
  async warmup() {
    return fallbackStatus;
  },
  async analyzeNutritionDescription(request: LocalAiNutritionAnalysisRequest): Promise<LocalAiNutritionAnalysisResult> {
    return analyzeNutritionDescriptionLocally(request);
  },
  async cancelCurrentAnalysis() {
    return { cancelled: true };
  },
  async unload() {
    return { unloaded: true };
  },
};
