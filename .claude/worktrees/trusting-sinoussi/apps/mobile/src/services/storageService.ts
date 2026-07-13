import { appStorage, getJsonValue, setJsonValue } from '../storage/mmkv';

type JsonValue = Record<string, unknown> | unknown[] | string | number | boolean | null;

function collectionPrefix(collection: string) {
  return `collection.${collection}.`;
}

function listCollectionKeys(collection: string) {
  const prefix = collectionPrefix(collection);
  return appStorage.getAllKeys().filter(key => key.startsWith(prefix));
}

async function getCollectionItems<T>(collection: string): Promise<T[]> {
  const keys = listCollectionKeys(collection);
  return keys
    .map(key => getJsonValue<T | null>(key, null))
    .filter((item): item is T => item !== null);
}

async function putMany<T extends { id?: string }>(collection: string, items: T[]) {
  items.forEach((item, index) => {
    const itemId = typeof item.id === 'string' && item.id.length > 0 ? item.id : String(index);
    setJsonValue(`${collectionPrefix(collection)}${itemId}`, item);
  });
}

export const storageService = {
  async set(key: string, value: JsonValue): Promise<void> {
    setJsonValue(key, value);
  },

  async get<T>(key: string): Promise<T | null> {
    return getJsonValue<T | null>(key, null);
  },

  async remove(key: string): Promise<void> {
    appStorage.delete(key);
  },

  async getAllKeys(): Promise<string[]> {
    return appStorage.getAllKeys();
  },

  async getAllDataForExport(): Promise<Record<string, unknown>> {
    const keys = appStorage.getAllKeys();
    const data: Record<string, unknown> = {};
    keys.forEach(key => {
      const value = getJsonValue<unknown | null>(key, null);
      if (value !== null) {
        data[key] = value;
      }
    });
    return data;
  },
};

export const collectionService = {
  history: {
    getAll: <T = unknown>() => getCollectionItems<T>('history'),
    getByDateRange: async <T = { date?: string }>(from: string, to: string) => {
      const items = await getCollectionItems<T>('history');
      return items.filter(item => {
        const date = (item as { date?: string }).date;
        return typeof date === 'string' && date >= from && date <= to;
      });
    },
    getByProgram: async <T = { programId?: string }>(programId: string) => {
      const items = await getCollectionItems<T>('history');
      return items.filter(item => (item as { programId?: string }).programId === programId);
    },
    putMany: <T extends { id?: string }>(items: T[]) => putMany('history', items),
    count: async () => listCollectionKeys('history').length,
    clear: async () => {
      listCollectionKeys('history').forEach(key => appStorage.delete(key));
    },
  },
  programs: {
    getAll: <T = unknown>() => getCollectionItems<T>('programs'),
    putMany: <T extends { id?: string }>(items: T[]) => putMany('programs', items),
    count: async () => listCollectionKeys('programs').length,
    clear: async () => {
      listCollectionKeys('programs').forEach(key => appStorage.delete(key));
    },
  },
  exercises: {
    getAll: <T = unknown>() => getCollectionItems<T>('exercises'),
    putMany: <T extends { id?: string }>(items: T[]) => putMany('exercises', items),
    count: async () => listCollectionKeys('exercises').length,
    clear: async () => {
      listCollectionKeys('exercises').forEach(key => appStorage.delete(key));
    },
  },
  nutrition: {
    getAll: <T = unknown>() => getCollectionItems<T>('nutrition'),
    putMany: <T extends { id?: string }>(items: T[]) => putMany('nutrition', items),
    count: async () => listCollectionKeys('nutrition').length,
    clear: async () => {
      listCollectionKeys('nutrition').forEach(key => appStorage.delete(key));
    },
  },
  sleep: {
    getAll: <T = unknown>() => getCollectionItems<T>('sleep'),
    putMany: <T extends { id?: string }>(items: T[]) => putMany('sleep', items),
    count: async () => listCollectionKeys('sleep').length,
    clear: async () => {
      listCollectionKeys('sleep').forEach(key => appStorage.delete(key));
    },
  },
};
