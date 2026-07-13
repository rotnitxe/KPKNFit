// services/networkService.ts
import { Capacitor } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

export interface NetworkStatus {
  connected: boolean;
  connectionType: string;
}

let cachedStatus: NetworkStatus | null = null;
let listenerHandle: PluginListenerHandle | null = null;

/**
 * Get current network status. Returns { connected: false } on web or if plugin unavailable.
 */
export async function getNetworkStatus(): Promise<NetworkStatus> {
  if (!Capacitor.isNativePlatform()) {
    const online = typeof navigator !== 'undefined' ? navigator.onLine : false;
    return { connected: online, connectionType: online ? 'wifi' : 'none' };
  }
  try {
    const { Network } = await import('@capacitor/network');
    const status = await Network.getStatus();
    cachedStatus = { connected: status.connected, connectionType: status.connectionType };
    return cachedStatus;
  } catch (e) {
    console.warn('[networkService] getStatus failed:', e);
    return { connected: true, connectionType: 'unknown' };
  }
}

export type NetworkStatusCallback = (status: NetworkStatus) => void;

/**
 * Listen for network status changes. Returns remove function.
 */
export async function addNetworkStatusListener(callback: NetworkStatusCallback): Promise<() => void> {
  if (!Capacitor.isNativePlatform()) {
    const handler = () => callback({ connected: navigator.onLine, connectionType: navigator.onLine ? 'wifi' : 'none' });
    window.addEventListener('online', handler);
    window.addEventListener('offline', handler);
    return () => {
      window.removeEventListener('online', handler);
      window.removeEventListener('offline', handler);
    };
  }
  try {
    const { Network } = await import('@capacitor/network');
    if (listenerHandle) await listenerHandle.remove();
    listenerHandle = await Network.addListener('networkStatusChange', (status) => {
      cachedStatus = { connected: status.connected, connectionType: status.connectionType };
      callback(cachedStatus);
    });
    const status = await Network.getStatus();
    cachedStatus = { connected: status.connected, connectionType: status.connectionType };
    callback(cachedStatus);
    return async () => {
      if (listenerHandle) await listenerHandle.remove();
      listenerHandle = null;
    };
  } catch (e) {
    console.warn('[networkService] addListener failed:', e);
    return () => {};
  }
}
