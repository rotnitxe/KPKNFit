import { Capacitor, WebPlugin, registerPlugin } from '@capacitor/core';

export type LocalAiDeliveryMode = 'install-time-pack' | 'bundled-asset' | 'web-fallback' | 'unavailable';
export type LocalAiBackend = 'cpu' | 'gpu' | 'unavailable';
export type LocalAiItemSource = 'database' | 'user-memory' | 'local-ai-estimate' | 'local-heuristic';
export type LocalAiExecutionEngine = 'runtime' | 'heuristics';

export interface LocalAiStatus {
    available: boolean;
    heuristicsAvailable?: boolean;
    modelReady: boolean;
    modelVersion: string | null;
    deliveryMode: LocalAiDeliveryMode;
    backend: LocalAiBackend;
    lastError?: string | null;
}

export interface LocalAiNutritionRequest {
    description: string;
    locale: 'es-CL';
    schemaVersion: string;
    knownFoods?: string[];
    userMemory?: string[];
}

export interface LocalAiNutritionItem {
    rawText: string;
    canonicalName: string;
    grams?: number;
    quantity?: number;
    preparation?: string | null;
    source: LocalAiItemSource;
    confidence: number;
    nutritionPer100g?: {
        calories: number;
        protein: number;
        carbs: number;
        fats: number;
    };
    reviewRequired?: boolean;
}

export interface LocalAiNutritionResult {
    items: LocalAiNutritionItem[];
    overallConfidence: number;
    containsEstimatedItems: boolean;
    requiresReview: boolean;
    elapsedMs: number;
    modelVersion: string | null;
    engine?: LocalAiExecutionEngine;
}

interface LocalAiPlugin {
    getStatus(): Promise<LocalAiStatus>;
    warmup(): Promise<LocalAiStatus>;
    analyzeNutritionDescription(request: LocalAiNutritionRequest): Promise<LocalAiNutritionResult>;
    cancelCurrentAnalysis(): Promise<void>;
    unload(): Promise<void>;
    resetRuntime(): Promise<LocalAiStatus>;
}

const DEFAULT_STATUS: LocalAiStatus = {
    available: false,
    modelReady: false,
    modelVersion: null,
    deliveryMode: 'unavailable',
    backend: 'unavailable',
    lastError: null,
};

class LocalAiWeb extends WebPlugin implements LocalAiPlugin {
    async getStatus(): Promise<LocalAiStatus> {
        return {
            ...DEFAULT_STATUS,
            deliveryMode: 'web-fallback',
            lastError: 'IA local nativa solo disponible en Android.',
        };
    }

    async warmup(): Promise<LocalAiStatus> {
        return this.getStatus();
    }

    async analyzeNutritionDescription(): Promise<LocalAiNutritionResult> {
        throw new Error('IA local nativa no disponible en esta plataforma.');
    }

    async cancelCurrentAnalysis(): Promise<void> { }

    async unload(): Promise<void> { }

    async resetRuntime(): Promise<LocalAiStatus> {
        return this.getStatus();
    }
}

let nativePlugin: LocalAiPlugin | null = null;
let cachedStatus: LocalAiStatus | null = null;
let cachedStatusAt = 0;
let lifecycleAttached = false;

function createPlugin(): LocalAiPlugin {
    if (typeof window === 'undefined') {
        return new LocalAiWeb();
    }

    return registerPlugin<LocalAiPlugin>('LocalAi', {
        web: () => Promise.resolve(new LocalAiWeb()),
    });
}

function getPlugin(): LocalAiPlugin {
    if (!nativePlugin) {
        nativePlugin = createPlugin();
    }
    return nativePlugin;
}

function cacheStatus(status: LocalAiStatus): LocalAiStatus {
    cachedStatus = status;
    cachedStatusAt = Date.now();
    return status;
}

export function isNativeLocalAiPlatform(): boolean {
    return typeof window !== 'undefined' && Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
}

export async function getLocalAiStatus(forceRefresh = false): Promise<LocalAiStatus> {
    if (!forceRefresh && cachedStatus && (Date.now() - cachedStatusAt) < 10_000) {
        return cachedStatus;
    }

    try {
        const status = await getPlugin().getStatus();
        return cacheStatus(status);
    } catch (error) {
        return cacheStatus({
            ...DEFAULT_STATUS,
            deliveryMode: isNativeLocalAiPlatform() ? 'bundled-asset' : 'unavailable',
            backend: isNativeLocalAiPlatform() ? 'cpu' : 'unavailable',
            lastError: error instanceof Error ? error.message : 'No se pudo leer el estado de IA local.',
        });
    }
}

export async function warmupLocalAi(): Promise<LocalAiStatus> {
    try {
        const status = await getPlugin().warmup();
        return cacheStatus(status);
    } catch (error) {
        return cacheStatus({
            ...(await getLocalAiStatus(true)),
            lastError: error instanceof Error ? error.message : 'No se pudo iniciar la IA local.',
        });
    }
}

export async function analyzeLocalNutritionDescription(request: LocalAiNutritionRequest): Promise<LocalAiNutritionResult> {
    return getPlugin().analyzeNutritionDescription(request);
}

export async function cancelCurrentLocalAiAnalysis(): Promise<void> {
    try {
        await getPlugin().cancelCurrentAnalysis();
    } catch {
        // Ignore cancellation failures; the JS request id gate still prevents stale UI updates.
    }
}

export async function unloadLocalAi(): Promise<void> {
    try {
        await getPlugin().unload();
    } finally {
        cachedStatusAt = 0;
    }
}

export async function resetLocalAiRuntime(): Promise<LocalAiStatus> {
    try {
        const status = await getPlugin().resetRuntime();
        return cacheStatus(status);
    } catch {
        return getLocalAiStatus(true);
    }
}

let backgroundUnloadTimerId: ReturnType<typeof setTimeout> | null = null;

export async function ensureLocalAiLifecycleBridge(): Promise<void> {
    if (lifecycleAttached || !isNativeLocalAiPlatform()) {
        return;
    }

    try {
        const { App } = await import('@capacitor/app');
        App.addListener('appStateChange', ({ isActive }) => {
            // Auto-unload is handled by the native plugin's 5-minute timer.
            // Only force-unload after a prolonged background period.
            if (!isActive) {
                backgroundUnloadTimerId = setTimeout(() => {
                    unloadLocalAi().catch(() => { });
                    backgroundUnloadTimerId = null;
                }, 5 * 60 * 1000);
            } else {
                if (backgroundUnloadTimerId) {
                    clearTimeout(backgroundUnloadTimerId);
                    backgroundUnloadTimerId = null;
                }
            }
        });
        lifecycleAttached = true;
    } catch {
        lifecycleAttached = false;
    }
}
