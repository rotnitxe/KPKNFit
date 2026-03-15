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
  widgetLastSyncAt?: string;
  widgetSyncSource?: 'foreground' | 'background' | 'unknown';
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
  getStatus(): Promise<WidgetSyncStatus>;
  markStale(reason: string): Promise<void>;
}

export interface BackgroundModuleContract {
  schedulePeriodicSync(): Promise<{ scheduled: boolean }>;
  cancelPeriodicSync(): Promise<{ cancelled: boolean }>;
  runImmediateSync(): Promise<{ started: boolean }>;
  getStatus(): Promise<BackgroundSyncStatus>;
  reportTaskResult(params: { success: boolean; error?: string | null }): Promise<void>;
}

export interface MigrationBridgeContract {
  readMigrationSnapshot(): Promise<string | null>;
  markMigrationComplete(): Promise<void>;
}

export interface WidgetSyncStatus {
  lastSyncAtMs: number | null;
  lastReloadAtMs: number | null;
  lastError: string | null;
  stale: boolean;
  staleReason: string | null;
  source: 'foreground' | 'background' | 'unknown';
}

export interface BackgroundSyncStatus {
  lastDispatchAtMs: number | null;
  lastCompletionAtMs: number | null;
  lastResult: 'idle' | 'dispatching' | 'success' | 'failure';
  lastError: string | null;
  runAttemptCount: number;
}
