// utils/inAppBrowser.ts
import { Capacitor } from '@capacitor/core';

/**
 * Opens a URL in the in-app browser (native) or new tab (web).
 * Use instead of window.open for better UX on mobile.
 */
export async function openInAppBrowser(url: string): Promise<void> {
  if (!url || !url.startsWith('http')) return;
  try {
    if (Capacitor.isNativePlatform()) {
      const { Browser } = await import('@capacitor/browser');
      await Browser.open({ url });
    } else {
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  } catch (e) {
    console.warn('[inAppBrowser] Failed to open:', e);
    window.open(url, '_blank', 'noopener,noreferrer');
  }
}
