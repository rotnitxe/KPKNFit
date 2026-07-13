import { getNetworkStatus, readLastNetworkStatus } from './networkService';
import { useLocalAiDiagnosticsStore } from '../stores/localAiDiagnosticsStore';

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
  localAiDeliveryMode: 'native' | 'fallback' | null;
  localAiLastError?: string | null;
}

let cachedSnapshot: NutritionConnectivitySnapshot | null = null;

export async function getNutritionConnectivity(
  settings?: {
    apiKeys?: { usda?: string };
    nutritionUseOnlineApis?: boolean;
    nutritionUseLocalAI?: boolean;
    nutritionLocalModel?: string;
  } | null,
  forceRefresh = false,
): Promise<NutritionConnectivitySnapshot> {
  const now = Date.now();
  if (!forceRefresh && cachedSnapshot && now - cachedSnapshot.checkedAt < STATUS_TTL_MS) {
    return cachedSnapshot;
  }

  const latestNetwork = forceRefresh ? await getNetworkStatus() : readLastNetworkStatus();
  const diagnostics = useLocalAiDiagnosticsStore.getState();
  const localStatus = diagnostics.status;
  const onlineEnabled = settings?.nutritionUseOnlineApis ?? true;
  const localEnabled = settings?.nutritionUseLocalAI ?? true;
  const usdaApiConfigured = Boolean(settings?.apiKeys?.usda);
  const localModel = settings?.nutritionLocalModel ?? localStatus?.modelVersion ?? null;

  const snapshot: NutritionConnectivitySnapshot = {
    checkedAt: now,
    networkConnected: latestNetwork.isOnline,
    connectionType: latestNetwork.isOnline ? 'online' : 'offline',
    canUseInternetApis: latestNetwork.isOnline && onlineEnabled,
    usdaApiConfigured,
    canUseUsdaApi: latestNetwork.isOnline && onlineEnabled && usdaApiConfigured,
    canUseOpenFoodFactsApi: latestNetwork.isOnline && onlineEnabled,
    backendReachable: latestNetwork.isOnline && onlineEnabled,
    localAiAvailable: localEnabled && Boolean(localStatus?.available ?? true),
    localAiModelReady: localEnabled && Boolean(localStatus?.modelReady ?? true),
    localAiProvider: localEnabled ? 'android-native' : null,
    localAiModel: localModel,
    availableLocalModels: localModel ? [localModel] : [],
    localAiDeliveryMode: localEnabled ? 'native' : null,
    localAiLastError: localStatus?.lastError ?? null,
  };

  cachedSnapshot = snapshot;
  return snapshot;
}
