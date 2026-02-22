import { storageService } from '../services/storageService';
import type { StateStorage } from 'zustand/middleware';

/**
 * Maps each Zustand store field to its own IndexedDB key,
 * preserving backward compatibility with the legacy per-key storage layout.
 */
export function createMultiKeyStorage(keyMap: Record<string, string>): StateStorage {
    return {
        getItem: async (_name: string): Promise<string | null> => {
            const state: Record<string, any> = {};
            let hasData = false;

            await Promise.all(
                Object.entries(keyMap).map(async ([field, storageKey]) => {
                    const value = await storageService.get(storageKey);
                    if (value !== null) {
                        state[field] = value;
                        hasData = true;
                    }
                })
            );

            if (!hasData) return null;
            return JSON.stringify({ state, version: 0 });
        },

        setItem: async (_name: string, value: string): Promise<void> => {
            try {
                const { state } = JSON.parse(value);
                await Promise.all(
                    Object.entries(keyMap).map(async ([field, storageKey]) => {
                        if (field in state) {
                            await storageService.set(storageKey, state[field]);
                        }
                    })
                );
            } catch (e) {
                console.error('Zustand storage write error:', e);
            }
        },

        removeItem: async (_name: string): Promise<void> => {
            await Promise.all(
                Object.values(keyMap).map(key => storageService.remove(key))
            );
        },
    };
}
