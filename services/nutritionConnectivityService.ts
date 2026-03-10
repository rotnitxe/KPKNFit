import type { Settings } from '../types';
import { getNetworkStatus } from './networkService';

const BACKEND_URL = (import.meta as any)?.env?.VITE_BACKEND_URL || 'http://localhost:8000';
const STATUS_TTL_MS = 15_000;
const REQUEST_TIMEOUT_MS = 1_500;

export interface NutritionConnectivitySnapshot {
    checkedAt: number;
    networkConnected: boolean;
    connectionType: string;
    canUseInternetApis: boolean;
    usdaApiConfigured: boolean;
    canUseUsdaApi: boolean;
    canUseOpenFoodFactsApi: boolean;
    backendReachable: boolean;
    localAiAvailable: boolean;
    localAiProvider: 'ollama' | null;
    localAiModel: string | null;
    availableLocalModels: string[];
}

let cachedSnapshot: NutritionConnectivitySnapshot | null = null;
let lastFingerprint = '';

async function fetchJsonWithTimeout(url: string, timeoutMs = REQUEST_TIMEOUT_MS): Promise<any> {
    const ctrl = new AbortController();
    const id = setTimeout(() => ctrl.abort(), timeoutMs);
    try {
        const response = await fetch(url, { signal: ctrl.signal });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    } finally {
        clearTimeout(id);
    }
}

function buildFingerprint(settings?: Settings | null): string {
    return JSON.stringify({
        usda: Boolean(settings?.apiKeys?.usda),
        mode: settings?.nutritionDescriptionMode ?? 'auto',
        useLocalAi: settings?.nutritionUseLocalAI ?? true,
        localModel: settings?.nutritionLocalModel ?? '',
        useOnlineApis: settings?.nutritionUseOnlineApis ?? true,
    });
}

export async function getNutritionConnectivity(
    settings?: Settings | null,
    forceRefresh = false
): Promise<NutritionConnectivitySnapshot> {
    const fingerprint = buildFingerprint(settings);
    const now = Date.now();
    if (!forceRefresh && cachedSnapshot && fingerprint === lastFingerprint && (now - cachedSnapshot.checkedAt) < STATUS_TTL_MS) {
        return cachedSnapshot;
    }

    const network = await getNetworkStatus().catch(() => ({ connected: false, connectionType: 'unknown' }));
    const allowOnlineApis = settings?.nutritionUseOnlineApis ?? true;
    const usdaApiConfigured = Boolean(settings?.apiKeys?.usda);

    let backendReachable = false;
    let localAiAvailable = false;
    let availableLocalModels: string[] = [];

    try {
        const status = await fetchJsonWithTimeout(`${BACKEND_URL}/api/ai/status`);
        backendReachable = Boolean(status?.backend);
        const ollama = status?.providers?.ollama;
        availableLocalModels = Array.isArray(ollama?.models) ? ollama.models.filter(Boolean) : [];
        localAiAvailable = Boolean(ollama?.available);
    } catch (_) {
        backendReachable = false;
        localAiAvailable = false;
    }

    const preferredLocalModel = settings?.nutritionLocalModel || availableLocalModels[0] || null;
    const snapshot: NutritionConnectivitySnapshot = {
        checkedAt: now,
        networkConnected: Boolean(network.connected),
        connectionType: network.connectionType || 'unknown',
        canUseInternetApis: Boolean(network.connected && allowOnlineApis),
        usdaApiConfigured,
        canUseUsdaApi: Boolean(network.connected && allowOnlineApis && usdaApiConfigured),
        canUseOpenFoodFactsApi: Boolean(network.connected && allowOnlineApis),
        backendReachable,
        localAiAvailable: Boolean(localAiAvailable && (settings?.nutritionUseLocalAI ?? true)),
        localAiProvider: localAiAvailable ? 'ollama' : null,
        localAiModel: preferredLocalModel,
        availableLocalModels,
    };

    cachedSnapshot = snapshot;
    lastFingerprint = fingerprint;
    return snapshot;
}
