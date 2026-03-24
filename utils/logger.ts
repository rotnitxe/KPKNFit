/**
 * Secure logging utility.
 * In production, all logs are suppressed to prevent data leakage.
 * In development, logs are shown with optional data sanitization.
 */

const IS_DEV = typeof process !== 'undefined' && process.env.NODE_ENV === 'development';

const SENSITIVE_KEYS = [
  'password', 'passwd', 'pwd',
  'token', 'access_token', 'refresh_token', 'auth_token',
  'secret', 'api_key', 'apikey', 'apiSecret',
  'key', 'private', 'credential',
  'authorization', 'bearer',
  'session', 'cookie',
  'email', 'phone',
  'ssn', 'social_security',
  'credit_card', 'card_number',
  'dni', 'rut', 'cpf',
];

function sanitizeValue(value: unknown, depth: number = 0): unknown {
  if (depth > 5) return '[MAX_DEPTH]';
  if (value === null || value === undefined) return value;
  if (typeof value === 'string') {
    if (value.match(/^[\w-]{20,}$/)) return '[REDACTED]';
    if (value.includes('@') && value.includes('.')) {
      const [local, domain] = value.split('@');
      return `${local.substring(0, 2)}***@${domain}`;
    }
    return value;
  }
  if (typeof value === 'number' || typeof value === 'boolean') return value;
  if (Array.isArray(value)) {
    return value.map(v => sanitizeValue(v, depth + 1));
  }
  if (typeof value === 'object') {
    const sanitized: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (SENSITIVE_KEYS.some(sk => k.toLowerCase().includes(sk))) {
        sanitized[k] = '[REDACTED]';
      } else {
        sanitized[k] = sanitizeValue(v, depth + 1);
      }
    }
    return sanitized;
  }
  return value;
}

function formatArgs(args: unknown[], sanitize: boolean = true): unknown[] {
  if (!sanitize) return args;
  return args.map(arg => sanitizeValue(arg));
}

export const logger = {
  log: (...args: unknown[]) => {
    if (IS_DEV) {
      console.log(...formatArgs(args));
    }
  },
  
  info: (...args: unknown[]) => {
    if (IS_DEV) {
      console.info(...formatArgs(args));
    }
  },
  
  warn: (...args: unknown[]) => {
    if (IS_DEV) {
      console.warn(...formatArgs(args));
    }
  },
  
  error: (...args: unknown[]) => {
    if (IS_DEV) {
      console.error(...formatArgs(args, false));
    }
  },
  
  debug: (...args: unknown[]) => {
    if (IS_DEV) {
      console.debug(...formatArgs(args));
    }
  },
  
  secure: (...args: unknown[]) => {
    if (IS_DEV) {
      const [prefix, ...rest] = args;
      const safeArgs = rest.map(arg => sanitizeValue(arg));
      console.log(`[${prefix}]`, ...safeArgs);
    }
  },
};

export function reportError(error: Error, context?: Record<string, unknown>): void {
  if (IS_DEV) {
    console.error('[ERROR]', error.message, context ? sanitizeValue(context) : '');
  }
  try {
    const { captureException } = require('../services/sentryService');
    captureException(error, context);
  } catch {
    // Sentry not initialized
  }
}

export { sanitizeValue };