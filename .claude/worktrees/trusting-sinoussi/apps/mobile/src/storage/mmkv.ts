import { MMKV } from 'react-native-mmkv';

export const appStorage = new MMKV({
  id: 'kpkn-mobile',
});

export function getJsonValue<T>(key: string, fallback: T): T {
  const rawValue = appStorage.getString(key);
  if (!rawValue) return fallback;

  try {
    return JSON.parse(rawValue) as T;
  } catch (error) {
    console.warn(`No se pudo parsear la clave ${key} desde MMKV.`, error);
    return fallback;
  }
}

export function setJsonValue(key: string, value: unknown) {
  appStorage.set(key, JSON.stringify(value));
}
