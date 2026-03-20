import { appStorage } from '../storage/mmkv';

function buildKey(key: string) {
  return `cache.${key}`;
}

export async function setCacheValue<T>(key: string, value: T, ttlMs?: number) {
  const expiresAt = ttlMs ? Date.now() + ttlMs : null;
  const payload = JSON.stringify({ value, expiresAt });
  appStorage.set(buildKey(key), payload);
}

export async function getCacheValue<T>(key: string): Promise<T | null> {
  const raw = appStorage.getString(buildKey(key));
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as { value: T; expiresAt: number | null };
    if (parsed.expiresAt && Date.now() > parsed.expiresAt) {
      appStorage.delete(buildKey(key));
      return null;
    }
    return parsed.value;
  } catch {
    appStorage.delete(buildKey(key));
    return null;
  }
}

export async function clearCacheValue(key: string) {
  appStorage.delete(buildKey(key));
}

