/**
 * Canonical date utilities shared across PWA and React Native targets.
 * Single source of truth – every other copy must import from here.
 */
/** Returns a local YYYY-MM-DD string for the given date (defaults to now). */
export function getLocalDateKey(date = new Date()): string {
  return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')}`;
}
/** Alias kept for backward-compat; identical to getLocalDateKey. */
export const getLocalDateString = getLocalDateKey;
function stringOrNull(v: unknown): string | null {
  return typeof v === 'string' && v.length > 0 ? v : null;
}
/**
 * Normalizes an unknown value to a YYYY-MM-DD key, or returns null.
 * Accepts:
 *   - already formatted "2025-01-15" → pass-through
 *   - any Date-parseable string      → format to YYYY-MM-DD
 *   - anything else                   → null
 */
export function normalizeDateKey(raw: unknown): string | null {
  const value = stringOrNull(raw);
  if (!value) return null;
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return null;
  return getLocalDateKey(parsed);
}