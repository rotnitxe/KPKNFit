import { storageService } from '@/services/storageService';
import { createJSONStorage, type StateStorage } from 'zustand/middleware';

function createMultiKeyStorage(keyMap: Record<string, string>): StateStorage {
  return {
    getItem: async () => {
      const state: Record<string, unknown> = {};
      let hasData = false;

      await Promise.all(
        Object.entries(keyMap).map(async ([field, storageKey]) => {
          const value = await storageService.get(storageKey);
          if (value !== null) {
            state[field] = value;
            hasData = true;
          }
        }),
      );

      if (!hasData) return null;
      return JSON.stringify({ state, version: 0 });
    },

    setItem: async (_name: string, value: string) => {
      try {
        const parsed = JSON.parse(value) as { state?: Record<string, unknown> } | Record<string, unknown>;
        const state = (parsed as { state?: Record<string, unknown> }).state ?? parsed;
        if (!state || typeof state !== 'object') return;

        await Promise.all(
          Object.entries(keyMap).map(async ([field, storageKey]) => {
            if (field in state) {
              await storageService.set(storageKey, state[field] as never);
            }
          }),
        );
      } catch (error) {
        console.error('Zustand storage write error:', error);
      }
    },

    removeItem: async () => {
      await Promise.all(Object.values(keyMap).map((key) => storageService.remove(key)));
    },
  };
}

export function createPersistMultiKeyStorage(keyMap: Record<string, string>) {
  return createJSONStorage(() => createMultiKeyStorage(keyMap));
}

