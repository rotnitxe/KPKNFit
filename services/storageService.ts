

import { Capacitor } from '@capacitor/core';
import { Preferences } from '@capacitor/preferences';

const DB_NAME = 'KPKNDB';
const STORE_NAME = 'keyval';
let dbInstance: IDBDatabase | null = null;

function getDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (dbInstance) return resolve(dbInstance);
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
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

async function idbGet<T>(key: string): Promise<T | undefined> {
  try {
    const db = await getDB();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, 'readonly');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.get(key);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  } catch (e) {
    console.error('IndexedDB Get Error:', e);
    return undefined;
  }
}

async function idbSet(key: string, value: any): Promise<void> {
  const db = await getDB();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, 'readwrite');
    const store = transaction.objectStore(STORE_NAME);
    const request = store.put(value, key);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

async function idbDel(key: string): Promise<void> {
  const db = await getDB();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, 'readwrite');
    const store = transaction.objectStore(STORE_NAME);
    const request = store.delete(key);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

async function idbKeys(): Promise<string[]> {
  const db = await getDB();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, 'readonly');
    const store = transaction.objectStore(STORE_NAME);
    const request = store.getAllKeys();
    request.onsuccess = () => resolve(request.result as string[]);
    request.onerror = () => reject(request.error);
  });
}

export const storageService = {
  async set(key: string, value: any): Promise<void> {
    try {
      // IndexedDB soporta clonación estructurada, no necesitamos stringify
      await idbSet(key, value);
    } catch (error) {
      console.error(`Error guardando en IndexedDB la clave "${key}"`, error);
    }
  },

  async get<T>(key: string): Promise<T | null> {
    try {
      // 1. Intentar obtener de IndexedDB primero
      const dbValue = await idbGet<T>(key);
      if (dbValue !== undefined) {
        return dbValue;
      }

      // 2. MIGRACIÓN: Si no existe, intentar obtener del almacenamiento antiguo (Legacy)
      // Preferences usa localStorage en web y SharedPreferences/UserDefaults en nativo
      const { value: legacyValue } = await Preferences.get({ key });
      
      if (legacyValue !== null) {
        console.log(`Migrando clave "${key}" a IndexedDB...`);
        try {
          const parsed = JSON.parse(legacyValue);
          
          // Guardar en la nueva DB
          await idbSet(key, parsed);
          
          // Limpiar del almacenamiento antiguo para no duplicar y confirmar migración
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
      // Intentar limpiar también del legacy por si acaso quedó algo
      await Preferences.remove({ key });
    } catch (e) {
      console.error(`Error eliminando clave "${key}"`, e);
    }
  },
  
  async getAllKeys(): Promise<string[]> {
    try {
        return await idbKeys();
    } catch(e) {
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
    // Normalizar nombre de settings para exportación
    if(data['yourprime-settings']) {
      data['settings'] = data['yourprime-settings'];
      delete data['yourprime-settings'];
    }
    return data;
  }
};