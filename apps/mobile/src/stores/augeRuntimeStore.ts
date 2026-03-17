import { create } from 'zustand';
import { 
  readStoredAugeRuntimePayload, 
  persistStoredAugeRuntimePayload 
} from '../services/mobileDomainStateService';
import { computeAugeRuntimeSnapshot } from '../services/augeRuntimeService';
import { AugeRuntimeSnapshot, AugeRuntimeDebug } from '../types/augeRuntime';

interface AugeRuntimeStoreState {
  status: 'idle' | 'ready' | 'failed';
  snapshot: AugeRuntimeSnapshot | null;
  debug: AugeRuntimeDebug | null;
  notice: string | null;
  errorMessage: string | null;
  isRefreshing: boolean;
  hydrateFromStorage: () => Promise<void>;
  recompute: () => Promise<void>;
  clearNotice: () => void;
}

export const useAugeRuntimeStore = create<AugeRuntimeStoreState>((set, get) => ({
  status: 'idle',
  snapshot: null,
  debug: null,
  notice: null,
  errorMessage: null,
  isRefreshing: false,

  hydrateFromStorage: async () => {
    try {
      const payload = readStoredAugeRuntimePayload();
      if (payload.snapshot) {
        set({ 
          snapshot: payload.snapshot, 
          status: 'ready',
          errorMessage: null 
        });
      } else {
        // No hay snapshot persistido, intentamos el primer cálculo
        await get().recompute();
      }
    } catch (error) {
      console.error('[AugeRuntimeStore] Hydration failed:', error);
      set({ status: 'failed', errorMessage: 'Error al hidratar estado AUGE.' });
    }
  },

  recompute: async () => {
    set({ isRefreshing: true, errorMessage: null });
    try {
      const { snapshot, debug } = await computeAugeRuntimeSnapshot();
      
      persistStoredAugeRuntimePayload({ snapshot });
      
      set({ 
        snapshot, 
        debug,
        status: 'ready', 
        isRefreshing: false,
        notice: 'AUGE actualizado.' 
      });
    } catch (error) {
      const msg = error instanceof Error ? error.message : 'Error desconocido al calcular AUGE.';
      set({ 
        status: 'failed', 
        errorMessage: msg,
        isRefreshing: false 
      });
    }
  },

  clearNotice: () => set({ notice: null }),
}));
