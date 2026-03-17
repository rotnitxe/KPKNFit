import { create } from 'zustand';
import { loadPersistedDomainPayload, persistDomainPayload } from '../services/mobilePersistenceService';
import { setJsonValue } from '../storage/mmkv';
import type {
  BodyProgressEntry,
  BodyLabAnalysis,
  BiomechanicalData,
  BiomechanicalAnalysis,
} from '../types/workout';

interface BodyMigrationPayload {
  bodyProgress?: BodyProgressEntry[];
  bodyLabAnalysis?: BodyLabAnalysis | null;
  biomechanicalData?: BiomechanicalData | null;
  biomechanicalAnalysis?: BiomechanicalAnalysis | null;
}

interface BodyStoreState {
  status: 'idle' | 'ready' | 'failed';
  bodyProgress: BodyProgressEntry[];
  bodyLabAnalysis: BodyLabAnalysis | null;
  biomechanicalData: BiomechanicalData | null;
  biomechanicalAnalysis: BiomechanicalAnalysis | null;
  hasHydrated: boolean;
  errorMessage: string | null;
  notice: string | null;
  hydrateFromMigration: () => Promise<void>;
  addBodyLog: (entry: Omit<BodyProgressEntry, 'id' | 'date'> & { date?: string }) => Promise<void>;
  updateBodyLog: (id: string, patch: Partial<Omit<BodyProgressEntry, 'id'>>) => Promise<void>;
  deleteBodyLog: (id: string) => Promise<void>;
  clearNotice: () => void;
}

async function persistBodySnapshot(payload: {
  bodyProgress: BodyProgressEntry[];
  bodyLabAnalysis: BodyLabAnalysis | null;
  biomechanicalData: BiomechanicalData | null;
  biomechanicalAnalysis: BiomechanicalAnalysis | null;
}) {
  await persistDomainPayload('body', payload);
  setJsonValue('rn.body', payload);
}

export const useBodyStore = create<BodyStoreState>((set, get) => ({
  status: 'idle',
  bodyProgress: [],
  bodyLabAnalysis: null,
  biomechanicalData: null,
  biomechanicalAnalysis: null,
  hasHydrated: false,
  errorMessage: null,
  notice: null,

  hydrateFromMigration: async () => {
    try {
      const payload = await loadPersistedDomainPayload<BodyMigrationPayload>('body');
      if (!payload) {
        set({ status: 'ready', hasHydrated: true });
        return;
      }

      const bodyProgress: BodyProgressEntry[] = Array.isArray(payload.bodyProgress) ? payload.bodyProgress : [];
      const bodyLabAnalysis: BodyLabAnalysis | null = payload.bodyLabAnalysis ?? null;
      const biomechanicalData: BiomechanicalData | null = payload.biomechanicalData ?? null;
      const biomechanicalAnalysis: BiomechanicalAnalysis | null = payload.biomechanicalAnalysis ?? null;

      setJsonValue('rn.body', { 
        bodyProgress, 
        bodyLabAnalysis, 
        biomechanicalData, 
        biomechanicalAnalysis 
      });

      set({ 
        status: 'ready', 
        hasHydrated: true, 
        bodyProgress, 
        bodyLabAnalysis, 
        biomechanicalData, 
        biomechanicalAnalysis,
        errorMessage: null 
      });
    } catch (error) {
      set({ 
        status: 'failed', 
        hasHydrated: true, 
        errorMessage: error instanceof Error ? error.message : 'Error cargando datos corporales.' 
      });
    }
  },

  addBodyLog: async (input) => {
    const { bodyProgress, bodyLabAnalysis, biomechanicalData, biomechanicalAnalysis } = get();
    
    // Generar ID robusto
    const id = `body_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
    const date = input.date ?? new Date().toISOString();
    
    const newEntry: BodyProgressEntry = {
      ...input,
      id,
      date,
    };

    const nextProgress = [newEntry, ...bodyProgress].sort((a, b) => 
      new Date(b.date).getTime() - new Date(a.date).getTime()
    );

    try {
      await persistBodySnapshot({
        bodyProgress: nextProgress,
        bodyLabAnalysis,
        biomechanicalData,
        biomechanicalAnalysis,
      });

      set({
        bodyProgress: nextProgress,
        status: 'ready',
        notice: 'Registro corporal guardado.',
        errorMessage: null,
      });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'Error al guardar el registro corporal.',
      });
    }
  },

  updateBodyLog: async (id, patch) => {
    const { bodyProgress, bodyLabAnalysis, biomechanicalData, biomechanicalAnalysis } = get();
    const index = bodyProgress.findIndex(e => e.id === id);

    if (index === -1) {
      set({ notice: 'No encontramos el registro para editar.' });
      return;
    }

    const updatedEntry = { ...bodyProgress[index], ...patch };
    const nextProgress = [...bodyProgress];
    nextProgress[index] = updatedEntry;
    
    // Re-ordenar por si cambió la fecha en el patch
    nextProgress.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

    try {
      await persistBodySnapshot({
        bodyProgress: nextProgress,
        bodyLabAnalysis,
        biomechanicalData,
        biomechanicalAnalysis,
      });

      set({
        bodyProgress: nextProgress,
        notice: 'Registro corporal actualizado.',
        errorMessage: null,
      });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'Error al actualizar el registro corporal.',
      });
    }
  },

  deleteBodyLog: async (id) => {
    const { bodyProgress, bodyLabAnalysis, biomechanicalData, biomechanicalAnalysis } = get();
    const exists = bodyProgress.some(e => e.id === id);

    if (!exists) {
      set({ notice: 'No encontramos el registro para eliminar.' });
      return;
    }

    const nextProgress = bodyProgress.filter(e => e.id !== id);

    try {
      await persistBodySnapshot({
        bodyProgress: nextProgress,
        bodyLabAnalysis,
        biomechanicalData,
        biomechanicalAnalysis,
      });

      set({
        bodyProgress: nextProgress,
        notice: 'Registro corporal eliminado.',
        errorMessage: null,
      });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'Error al eliminar el registro corporal.',
      });
    }
  },

  clearNotice: () => set({ notice: null }),
}));
