import type {
  LocalAiNutritionAnalysisRequest,
  LocalAiNutritionAnalysisResult,
  LocalAiStatus,
  NutritionAnalysisEngine,
} from './nutrition';

export interface WidgetDashboardSnapshot {
  nextSessionLabel?: string;
  nextSessionProgramName?: string;
  nutritionCaloriesToday?: number;
  nutritionProteinToday?: number;
  nutritionCarbsToday?: number;
  nutritionFatsToday?: number;
  nutritionCalorieGoal?: number;
  augeBatteryScore?: number;
  batteryCnsScore?: number;
  batteryMuscularScore?: number;
  batterySpinalScore?: number;
  effectiveVolumeToday?: number;
  effectiveVolumePlanned?: number;
}

export interface LocalAiModuleContract {
  getStatus(): Promise<LocalAiStatus>;
  getDiagnostics(): Promise<LocalAiDebugSnapshot>;
  warmup(): Promise<LocalAiStatus>;
  analyzeNutritionDescription(request: LocalAiNutritionAnalysisRequest): Promise<LocalAiNutritionAnalysisResult>;
  cancelCurrentAnalysis(): Promise<{ cancelled: boolean }>;
  unload(): Promise<{ unloaded: boolean }>;
}

export interface LocalAiDiagnosticEvent {
  timestampMs: number;
  level: 'info' | 'warn' | 'error';
  scope: 'warmup' | 'analyze' | 'runtime' | 'model' | 'status';
  message: string;
}

export interface LocalAiDebugSnapshot {
  runtimeLoaded: boolean;
  modelPath: string | null;
  deliveryMode: 'install-time-pack' | 'bundled-asset' | 'unknown';
  runtimeFailureCount: number;
  cooldownRemainingMs: number;
  lastError: string | null;
  lastWarmupAtMs: number | null;
  lastAnalysisAtMs: number | null;
  lastAnalysisEngine: NutritionAnalysisEngine | null;
  lastAnalysisElapsedMs: number | null;
  lastDescriptionPreview: string | null;
  recentEvents: LocalAiDiagnosticEvent[];
}

export interface WidgetModuleContract {
  setItem(key: string, value: string): Promise<void>;
  reloadWidget(): Promise<void>;
  syncDashboardState(snapshot: WidgetDashboardSnapshot): Promise<void>;
}

export interface BackgroundModuleContract {
  schedulePeriodicSync(): Promise<{ scheduled: boolean }>;
  cancelPeriodicSync(): Promise<{ cancelled: boolean }>;
  runImmediateSync(): Promise<{ started: boolean }>;
}

export interface MigrationBridgeContract {
  readMigrationSnapshot(): Promise<string | null>;
  markMigrationComplete(): Promise<void>;
}
