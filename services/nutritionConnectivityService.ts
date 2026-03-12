import type { Settings } from '../types';
import type { LocalAiDeliveryMode } from './localAiService';
import { getLocalAiStatus } from './localAiService';
import { getNetworkStatus } from './networkService';

const STATUS_TTL_MS = 15_000;

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
    localAiModelReady: boolean;
    localAiProvider: 'android-native' | 'web-fallback' | null;
    localAiModel: string | null;
    availableLocalModels: string[];
    localAiDeliveryMode: LocalAiDeliveryMode | null;
    localAiLastError?: string | null;
}

let cachedSnapshot: NutritionConnectivitySnapshot | null = null;
let lastFingerprint = '';

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
    forceRefresh = false,
): Promise<NutritionConnectivitySnapshot> {
    const fingerprint = buildFingerprint(settings);
    const now = Date.now();

    if (!forceRefresh && cachedSnapshot && fingerprint === lastFingerprint && (now - cachedSnapshot.checkedAt) < STATUS_TTL_MS) {
        return cachedSnapshot;
    }

    const network = await getNetworkStatus().catch(() => ({ connected: false, connectionType: 'unknown' }));
    const allowOnlineApis = settings?.nutritionUseOnlineApis ?? true;
    const usdaApiConfigured = Boolean(settings?.apiKeys?.usda);
    const localAiEnabled = settings?.nutritionUseLocalAI ?? true;
    const localAiStatus = await getLocalAiStatus(forceRefresh).catch(() => null);

    const modelVersion = settings?.nutritionLocalModel || localAiStatus?.modelVersion || null;
    const localAiProvider = localAiStatus?.available
        ? (localAiStatus.deliveryMode === 'web-fallback' ? 'web-fallback' : 'android-native')
        : null;

    const snapshot: NutritionConnectivitySnapshot = {
        checkedAt: now,
        networkConnected: Boolean(network.connected),
        connectionType: network.connectionType || 'unknown',
        canUseInternetApis: Boolean(network.connected && allowOnlineApis),
        usdaApiConfigured,
        canUseUsdaApi: Boolean(network.connected && allowOnlineApis && usdaApiConfigured),
        canUseOpenFoodFactsApi: Boolean(network.connected && allowOnlineApis),
        backendReachable: false,
        localAiAvailable: Boolean(localAiEnabled && localAiStatus?.available),
        localAiModelReady: Boolean(localAiEnabled && localAiStatus?.modelReady),
        localAiProvider,
        localAiModel: modelVersion,
        availableLocalModels: modelVersion ? [modelVersion] : [],
        localAiDeliveryMode: localAiStatus?.deliveryMode ?? null,
        localAiLastError: localAiStatus?.lastError ?? null,
    };

    cachedSnapshot = snapshot;
    lastFingerprint = fingerprint;
    return snapshot;
}
