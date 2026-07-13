/**
 * Hook para suscribirse a settings con useShallow.
 * Evita re-renders cuando solo cambian campos que el componente no usa.
 *
 * Ejemplo:
 *   const { hasSeenWelcome, homeCardOrder } = useSettingsStoreShallow(s => ({
 *     hasSeenWelcome: s.hasSeenWelcome,
 *     homeCardOrder: s.homeCardOrder,
 *   }));
 */
import { useShallow } from 'zustand/react/shallow';
import { useSettingsStore } from '../stores/settingsStore';
import type { Settings } from '../types';

export function useSettingsStoreShallow<T>(selector: (settings: Settings) => T): T {
    return useSettingsStore(
        useShallow((state) => selector(state.settings))
    );
}
