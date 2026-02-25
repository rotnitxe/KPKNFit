// services/appUpdateService.ts
import { Capacitor } from '@capacitor/core';

export interface AppUpdateInfo {
  currentVersion: string;
  availableVersion?: string;
  updateAvailable: boolean;
}

const UPDATE_AVAILABLE = 2; // AppUpdateAvailability.UPDATE_AVAILABLE

/**
 * Check for app updates (Google Play). Only works when app is published.
 * Returns null if not native or plugin unavailable.
 */
export async function checkForAppUpdate(): Promise<AppUpdateInfo | null> {
  if (!Capacitor.isNativePlatform() || Capacitor.getPlatform() !== 'android') return null;
  try {
    const { AppUpdate } = await import('@capawesome/capacitor-app-update');
    const info = await AppUpdate.getAppUpdateInfo();
    return {
      currentVersion: info.currentVersionName || '1.0.0',
      availableVersion: info.availableVersionName,
      updateAvailable: info.updateAvailability === UPDATE_AVAILABLE,
    };
  } catch (e) {
    console.warn('[appUpdate] getAppUpdateInfo failed:', e);
    return null;
  }
}

/**
 * Perform immediate update (full-screen, blocks app until updated).
 */
export async function performImmediateUpdate(): Promise<boolean> {
  if (!Capacitor.isNativePlatform() || Capacitor.getPlatform() !== 'android') return false;
  try {
    const { AppUpdate } = await import('@capawesome/capacitor-app-update');
    await AppUpdate.performImmediateUpdate();
    return true;
  } catch (e) {
    console.warn('[appUpdate] performImmediateUpdate failed:', e);
    return false;
  }
}

/**
 * Start flexible update (download in background, user continues using app).
 */
export async function startFlexibleUpdate(): Promise<boolean> {
  if (!Capacitor.isNativePlatform() || Capacitor.getPlatform() !== 'android') return false;
  try {
    const { AppUpdate } = await import('@capawesome/capacitor-app-update');
    await AppUpdate.startFlexibleUpdate();
    return true;
  } catch (e) {
    console.warn('[appUpdate] startFlexibleUpdate failed:', e);
    return false;
  }
}
