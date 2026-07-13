export interface ErrorContext {
  tags?: Record<string, string>;
  extras?: Record<string, unknown>;
}

// Wrapper de compatibilidad. Si Sentry no esta inicializado en esta build,
// la app no revienta y solo registra en consola.
export function captureException(error: unknown, context?: ErrorContext) {
  console.error('[sentry] exception', error, context);
}

export function captureMessage(message: string, context?: ErrorContext) {
  console.warn('[sentry] message', message, context);
}

