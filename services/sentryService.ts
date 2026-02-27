/**
 * Sentry error reporting service.
 * Reports errors to Sentry when DSN is configured.
 */
import * as Sentry from '@sentry/capacitor';
import * as SentryReact from '@sentry/react';

const SENTRY_DSN =
  (globalThis as any).__KPKN_SENTRY_DSN__ as string | undefined ||
  'https://66085ee6661f7b934968c583b4b009f0@o4510952852422656.ingest.us.sentry.io/4510952892727296';

let isInitialized = false;

export function initSentry(): void {
  if (isInitialized || !SENTRY_DSN || SENTRY_DSN === '') return;

  try {
    Sentry.init(
      {
        dsn: SENTRY_DSN,
        enableAutoSessionTracking: true,
        sendDefaultPii: true,
        environment: (typeof __DEV__ !== 'undefined' && __DEV__) ? 'development' : 'production',
        tracesSampleRate: 0.1,
      },
      SentryReact.init
    );
    isInitialized = true;
  } catch (e) {
    console.warn('[Sentry] Init failed:', e);
  }
}

export function captureException(error: Error, context?: Record<string, unknown>): void {
  if (!isInitialized) return;
  try {
    Sentry.withScope((scope) => {
      if (context) {
        Object.entries(context).forEach(([k, v]) => scope.setContext(k, { value: v }));
      }
      Sentry.captureException(error);
    });
  } catch (_) {
    // Silently ignore Sentry errors
  }
}

export function captureMessage(message: string, level: 'info' | 'warning' | 'error' = 'info'): void {
  if (!isInitialized) return;
  try {
    Sentry.captureMessage(message, level);
  } catch (_) {}
}

export function isSentryEnabled(): boolean {
  return isInitialized;
}

