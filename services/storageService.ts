import { Capacitor } from '@capacitor/core';
import { Preferences } from '@capacitor/preferences';

const DB_NAME = 'KPKNDB';
const DB_VERSION = 2;
let dbInstance: IDBDatabase | null = null;

const OBJECT_STORES: Record<string, { keyPath?: string; indexes?: { name: string; keyPath: string; unique?: boolean }[] }> = {
    keyval: {},
    programs: { keyPath: 'id' },
    history: { keyPath: 'id', indexes: [{ name: 'by_date', keyPath: 'date' }, { name: 'by_program', keyPath: 'programId' }] },
    exercises: { keyPath: 'id', indexes: [{ name: 'by_name', keyPath: 'name' }] },
    nutrition: { keyPath: 'id', indexes: [{ name: 'by_date', keyPath: 'date' }] },
    sleep: { keyPath: 'id', indexes: [{ name: 'by_date', keyPath: 'date' }] },
};

function getDB(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
        if (dbInstance) return resolve(dbInstance);
        const request = indexedDB.open(DB_NAME, DB_VERSION);

        request.onupgradeneeded = (event) => {
            const db = (event.target as IDBOpenDBRequest).result;
            for (const [storeName, config] of Object.entries(OBJECT_STORES)) {
                if (!db.objectStoreNames.contains(storeName)) {
                    const store = config.keyPath
                        ? db.createObjectStore(storeName, { keyPath: config.keyPath })
                        : db.createObjectStore(storeName);

                    if (config.indexes) {
                        for (const idx of config.indexes) {
                            store.createIndex(idx.name, idx.keyPath, { unique: idx.unique || false });
                        }
                    }
                }
            }
        };

        request.onsuccess = (event) => {
            dbInstance = (event.target as IDBOpenDBRequest).result;
            resolve(dbInstance);
        };

        request.onerror = (event) => {
            reject((event.target as IDBOpenDBRequest).error);
        };
    });
}

// ═══════════════════════════════════════════════════════════
// Generic key-value ops (backward compatible with v1)
// ═══════════════════════════════════════════════════════════

async function idbGet<T>(key: string, storeName = 'keyval'): Promise<T | undefined> {
    try {
        const db = await getDB();
        return new Promise((resolve, reject) => {
            const transaction = db.transaction(storeName, 'readonly');
            const store = transaction.objectStore(storeName);
            const request = store.get(key);
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    } catch (e) {
        console.error('IndexedDB Get Error:', e);
        return undefined;
    }
}

async function idbSet(key: string, value: any, storeName = 'keyval'): Promise<void> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readwrite');
        const store = transaction.objectStore(storeName);
        const request = store.put(value, storeName === 'keyval' ? key : undefined);
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
    });
}

async function idbDel(key: string, storeName = 'keyval'): Promise<void> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readwrite');
        const store = transaction.objectStore(storeName);
        const request = store.delete(key);
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
    });
}

async function idbKeys(storeName = 'keyval'): Promise<string[]> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readonly');
        const store = transaction.objectStore(storeName);
        const request = store.getAllKeys();
        request.onsuccess = () => resolve(request.result as string[]);
        request.onerror = () => reject(request.error);
    });
}

// ═══════════════════════════════════════════════════════════
// Indexed query helpers (new in v2)
// ═══════════════════════════════════════════════════════════

async function idbGetByIndex<T>(storeName: string, indexName: string, value: IDBValidKey): Promise<T[]> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readonly');
        const store = transaction.objectStore(storeName);
        const index = store.index(indexName);
        const request = index.getAll(value);
        request.onsuccess = () => resolve(request.result as T[]);
        request.onerror = () => reject(request.error);
    });
}

async function idbGetByRange<T>(storeName: string, indexName: string, lower: IDBValidKey, upper: IDBValidKey): Promise<T[]> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readonly');
        const store = transaction.objectStore(storeName);
        const index = store.index(indexName);
        const range = IDBKeyRange.bound(lower, upper);
        const request = index.getAll(range);
        request.onsuccess = () => resolve(request.result as T[]);
        request.onerror = () => reject(request.error);
    });
}

async function idbGetAll<T>(storeName: string): Promise<T[]> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readonly');
        const store = transaction.objectStore(storeName);
        const request = store.getAll();
        request.onsuccess = () => resolve(request.result as T[]);
        request.onerror = () => reject(request.error);
    });
}

async function idbPutMany(storeName: string, items: any[]): Promise<void> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readwrite');
        const store = transaction.objectStore(storeName);
        for (const item of items) {
            store.put(item);
        }
        transaction.oncomplete = () => resolve();
        transaction.onerror = () => reject(transaction.error);
    });
}

async function idbCount(storeName: string): Promise<number> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readonly');
        const store = transaction.objectStore(storeName);
        const request = store.count();
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function idbClearStore(storeName: string): Promise<void> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, 'readwrite');
        const store = transaction.objectStore(storeName);
        const request = store.clear();
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
    });
}

// ═══════════════════════════════════════════════════════════
// Public API
// ═══════════════════════════════════════════════════════════

export const storageService = {
    async set(key: string, value: any): Promise<void> {
        try {
            await idbSet(key, value);
        } catch (error) {
            console.error(`Error guardando en IndexedDB la clave "${key}"`, error);
        }
    },

    async get<T>(key: string): Promise<T | null> {
        try {
            const dbValue = await idbGet<T>(key);
            if (dbValue !== undefined) return dbValue;

            const { value: legacyValue } = await Preferences.get({ key });
            if (legacyValue !== null) {
                console.log(`Migrando clave "${key}" a IndexedDB...`);
                try {
                    const parsed = JSON.parse(legacyValue);
                    await idbSet(key, parsed);
                    await Preferences.remove({ key });
                    return parsed as T;
                } catch (parseError) {
                    console.error(`Error parseando valor legacy para clave "${key}"`, parseError);
                    return null;
                }
            }

            return null;
        } catch (error) {
            console.error(`Error leyendo clave "${key}"`, error);
            return null;
        }
    },

    async remove(key: string): Promise<void> {
        try {
            await idbDel(key);
            await Preferences.remove({ key });
        } catch (e) {
            console.error(`Error eliminando clave "${key}"`, e);
        }
    },

    async getAllKeys(): Promise<string[]> {
        try {
            return await idbKeys();
        } catch (e) {
            console.error('Error obteniendo claves', e);
            return [];
        }
    },

    async getAllDataForExport(): Promise<Record<string, any>> {
        const keys = await this.getAllKeys();
        const data: Record<string, any> = {};
        const relevantKeys = ['programs', 'history', 'yourprime-settings', 'body-progress', 'nutrition-logs', 'skipped-logs'];
        for (const key of keys) {
            if (relevantKeys.includes(key)) {
                data[key] = await this.get(key);
            }
        }
        if (data['yourprime-settings']) {
            data['settings'] = data['yourprime-settings'];
            delete data['yourprime-settings'];
        }
        return data;
    },
};

// ═══════════════════════════════════════════════════════════
// Collection-based API for indexed stores
// ═══════════════════════════════════════════════════════════

export const collectionService = {
    history: {
        getAll: () => idbGetAll<any>('history'),
        getByDateRange: (from: string, to: string) => idbGetByRange<any>('history', 'by_date', from, to),
        getByProgram: (programId: string) => idbGetByIndex<any>('history', 'by_program', programId),
        put: (item: any) => idbSet(item.id, item, 'history'),
        putMany: (items: any[]) => idbPutMany('history', items),
        delete: (id: string) => idbDel(id, 'history'),
        count: () => idbCount('history'),
        clear: () => idbClearStore('history'),
    },

    programs: {
        getAll: () => idbGetAll<any>('programs'),
        getById: (id: string) => idbGet<any>(id, 'programs'),
        put: (item: any) => idbSet(item.id, item, 'programs'),
        putMany: (items: any[]) => idbPutMany('programs', items),
        delete: (id: string) => idbDel(id, 'programs'),
        count: () => idbCount('programs'),
        clear: () => idbClearStore('programs'),
    },

    exercises: {
        getAll: () => idbGetAll<any>('exercises'),
        getById: (id: string) => idbGet<any>(id, 'exercises'),
        getByName: (name: string) => idbGetByIndex<any>('exercises', 'by_name', name),
        put: (item: any) => idbSet(item.id, item, 'exercises'),
        putMany: (items: any[]) => idbPutMany('exercises', items),
        delete: (id: string) => idbDel(id, 'exercises'),
        count: () => idbCount('exercises'),
        clear: () => idbClearStore('exercises'),
    },

    nutrition: {
        getAll: () => idbGetAll<any>('nutrition'),
        getByDateRange: (from: string, to: string) => idbGetByRange<any>('nutrition', 'by_date', from, to),
        put: (item: any) => idbSet(item.id, item, 'nutrition'),
        putMany: (items: any[]) => idbPutMany('nutrition', items),
        delete: (id: string) => idbDel(id, 'nutrition'),
        count: () => idbCount('nutrition'),
        clear: () => idbClearStore('nutrition'),
    },

    sleep: {
        getAll: () => idbGetAll<any>('sleep'),
        getByDateRange: (from: string, to: string) => idbGetByRange<any>('sleep', 'by_date', from, to),
        put: (item: any) => idbSet(item.id, item, 'sleep'),
        putMany: (items: any[]) => idbPutMany('sleep', items),
        delete: (id: string) => idbDel(id, 'sleep'),
        count: () => idbCount('sleep'),
        clear: () => idbClearStore('sleep'),
    },
};
