import React, { useState, useEffect, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

// --- NÚCLEO DE ESCRITURA BLINDADO ---
const safeGet = async (key: string): Promise<any> => {
    try {
        if (Capacitor.isNativePlatform()) {
            const { Preferences } = await import('@capacitor/preferences');
            const { value } = await Preferences.get({ key });
            return value ? JSON.parse(value) : null;
        } else {
            const value = window.localStorage.getItem(key);
            return value ? JSON.parse(value) : null;
        }
    } catch (e) {
        console.warn(`Error al leer ${key}, usando null`, e);
        return null;
    }
};

const safeSet = async (key: string, value: any): Promise<void> => {
    try {
        const strValue = JSON.stringify(value);
        if (Capacitor.isNativePlatform()) {
            const { Preferences } = await import('@capacitor/preferences');
            await Preferences.set({ key, value: strValue });
        } else {
            window.localStorage.setItem(key, strValue);
        }
    } catch (e) {
        console.error(`Error crítico al guardar ${key}`, e);
    }
};

function useLocalStorage<T>(key: string, initialValue: T): [T, React.Dispatch<React.SetStateAction<T>>, boolean] {
  const [storedValue, setStoredValue] = useState<T>(initialValue);
  const [isLoading, setIsLoading] = useState(true);
  const isInitialMount = useRef(true);
  const valueRef = useRef(storedValue);

  // Mantiene la referencia siempre actualizada para cierres repentinos
  useEffect(() => {
    valueRef.current = storedValue;
  }, [storedValue]);

  // Carga Segura con Anti-Congelamiento
  useEffect(() => {
    let isMounted = true;
    const loadValue = async () => {
      try {
        // Obliga a que la app nunca se quede bloqueada intentando leer
        const item = await Promise.race([
            safeGet(key),
            new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout de lectura')), 1500))
        ]);

        if (isMounted && item !== null && item !== undefined) {
            if (typeof initialValue === 'object' && !Array.isArray(initialValue) && initialValue !== null && typeof item === 'object' && !Array.isArray(item) && item !== null) {
                setStoredValue({ ...initialValue, ...item });
            } else {
                setStoredValue(item);
            }
        }
      } catch (error) {
        console.warn(`Fallback a estado inicial para "${key}" debido a lectura vacía.`);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    };
    loadValue();
    return () => { isMounted = false; };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  // Guardado Automático Agresivo
  useEffect(() => {
    if (isInitialMount.current) {
        isInitialMount.current = false;
        return;
    }
    if (isLoading) return;
    safeSet(key, storedValue);
  }, [key, storedValue, isLoading]);
  
  // Guardado de Emergencia al cerrar la app
  useEffect(() => {
    const handleAppHidden = () => {
        if (!isLoading) safeSet(key, valueRef.current);
    };

    const handleVisibilityChange = () => {
        if (document.visibilityState === 'hidden') handleAppHidden();
    };
    
    let appStateListenerPromise: Promise<PluginListenerHandle> | null = null;
    if (Capacitor.isNativePlatform()) {
        const addListener = async () => {
            const { App } = await import('@capacitor/app');
            return App.addListener('appStateChange', ({ isActive }) => {
                if (!isActive) handleAppHidden();
            });
        };
        appStateListenerPromise = addListener();
    }

    window.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('beforeunload', handleAppHidden);

    return () => {
        if (appStateListenerPromise) appStateListenerPromise.then(listener => listener.remove());
        window.removeEventListener('visibilitychange', handleVisibilityChange);
        window.removeEventListener('beforeunload', handleAppHidden);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, isLoading]);

  return [storedValue, setStoredValue, isLoading];
}

export default useLocalStorage;