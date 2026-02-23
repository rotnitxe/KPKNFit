import { storageService } from '../services/storageService';
import { createJSONStorage, type StateStorage } from 'zustand/middleware';

/**
 * Maps each Zustand store field to its own IndexedDB key,
 * preserving backward compatibility with the legacy per-key storage layout.
 * Returns StateStorage (string-based) for use with createJSONStorage.
 */
function createMultiKeyStorage(keyMap: Record<string, string>): StateStorage {
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
                const parsed = JSON.parse(value);
                // Zustand persist pasa { state, version }; legacy puede ser { settings } directo
                const state = parsed?.state ?? parsed;
                if (!state || typeof state !== 'object') return;

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

/**
 * PersistStorage-compatible wrapper for createMultiKeyStorage.
 * Zustand persist expects object-based storage; createJSONStorage adapts our
 * string-based storage to that interface so settings (hasSeenWelcome, etc.)
 * persist correctly across app restarts.
 */
export function createPersistMultiKeyStorage(keyMap: Record<string, string>) {
    return createJSONStorage(() => createMultiKeyStorage(keyMap));
}
