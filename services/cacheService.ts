// services/cacheService.ts
import { storageService } from './storageService';

const CACHE_PREFIX = 'yp-cache-';
const TTL = 7 * 24 * 60 * 60 * 1000; // 7 days in milliseconds

interface CacheItem<T> {
  timestamp: number;
  data: T;
}

export const cacheService = {
  /**
   * Sets a value in the cache with a timestamp.
   * @param key The cache key (without prefix).
   * @param data The data to store.
   */
  async set<T>(key: string, data: T): Promise<void> {
    const item: CacheItem<T> = {
      timestamp: Date.now(),
      data,
    };
    await storageService.set(`${CACHE_PREFIX}${key}`, item);
  },

  /**
   * Gets a value from the cache. Returns null if the item is expired or not found.
   * @param key The cache key (without prefix).
   * @returns The cached data or null.
   */
  async get<T>(key: string): Promise<T | null> {
    const item = await storageService.get<CacheItem<T>>(`${CACHE_PREFIX}${key}`);

    if (!item) {
      return null;
    }

    const isExpired = Date.now() - item.timestamp > TTL;

    if (isExpired) {
      // Asynchronously remove the expired item, but return null immediately.
      this.remove(key).catch(e => console.error(`Failed to remove expired cache item for key: ${key}`, e));
      return null;
    }

    return item.data;
  },

  /**
   * Removes an item from the cache.
   * @param key The cache key (without prefix).
   */
  async remove(key: string): Promise<void> {
    await storageService.remove(`${CACHE_PREFIX}${key}`);
  },
};