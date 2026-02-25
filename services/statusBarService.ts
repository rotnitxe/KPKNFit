// services/statusBarService.ts
import { Capacitor } from '@capacitor/core';
import type { Settings } from '../types';

/**
 * Syncs the native status bar with the app theme (light/dark).
 * Call on app init and when settings.appTheme changes.
 */
export async function syncStatusBarWithTheme(settings: Settings): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  try {
    const { StatusBar, Style } = await import('@capacitor/status-bar');
    const theme = settings?.appTheme || 'default';
    // default and deep-black are dark themes; volt or future light themes use LIGHT
    const isDark = theme === 'default' || theme === 'deep-black';
    await StatusBar.setStyle({ style: isDark ? Style.Dark : Style.Light });
    const bgColor = isDark ? '#000000' : '#ffffff';
    await StatusBar.setBackgroundColor({ color: bgColor });
    await StatusBar.setOverlaysWebView({ overlay: false });
  } catch (e) {
    console.warn('[statusBar] Failed to sync:', e);
  }
}
