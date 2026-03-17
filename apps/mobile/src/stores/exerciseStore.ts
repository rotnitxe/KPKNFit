import { create } from 'zustand';
import { loadPersistedDomainPayload } from '../services/mobilePersistenceService';
import { setJsonValue } from '../storage/mmkv';
import type {
  ExerciseCatalogEntry,
  ExercisePlaylist,
  MuscleGroupInfo,
  MuscleHierarchy,
} from '../types/workout';

interface ExerciseMigrationPayload {
  exerciseList?: ExerciseCatalogEntry[];
  exercisePlaylists?: ExercisePlaylist[];
  muscleGroupData?: MuscleGroupInfo[];
  muscleHierarchy?: MuscleHierarchy | null;
}

interface ExerciseStoreState {
  status: 'idle' | 'ready' | 'failed';
  exerciseList: ExerciseCatalogEntry[];
  exercisePlaylists: ExercisePlaylist[];
  muscleGroupData: MuscleGroupInfo[];
  muscleHierarchy: MuscleHierarchy | null;
  hasHydrated: boolean;
  errorMessage: string | null;
  hydrateFromMigration: () => Promise<void>;
  getExerciseById: (id: string) => ExerciseCatalogEntry | undefined;
}

export const useExerciseStore = create<ExerciseStoreState>((set, get) => ({
  status: 'idle',
  exerciseList: [],
  exercisePlaylists: [],
  muscleGroupData: [],
  muscleHierarchy: null,
  hasHydrated: false,
  errorMessage: null,
  getExerciseById: (id: string) => {
    return get().exerciseList.find((e) => e.id === id);
  },
  hydrateFromMigration: async () => {
    try {
      const payload = await loadPersistedDomainPayload<ExerciseMigrationPayload>('exercise');
      if (!payload) {
        set({ status: 'ready', hasHydrated: true });
        return;
      }

      const exerciseList: ExerciseCatalogEntry[] = Array.isArray(payload.exerciseList) ? payload.exerciseList : [];
      const exercisePlaylists: ExercisePlaylist[] = Array.isArray(payload.exercisePlaylists) ? payload.exercisePlaylists : [];
      const muscleGroupData: MuscleGroupInfo[] = Array.isArray(payload.muscleGroupData) ? payload.muscleGroupData : [];
      const muscleHierarchy: MuscleHierarchy | null = payload.muscleHierarchy ?? null;

      setJsonValue('rn.exercise', { 
        exerciseList, 
        exercisePlaylists, 
        muscleGroupData, 
        muscleHierarchy 
      });

      set({ 
        status: 'ready', 
        hasHydrated: true, 
        exerciseList, 
        exercisePlaylists, 
        muscleGroupData, 
        muscleHierarchy,
        errorMessage: null
      });
    } catch (error) {
      set({ 
        status: 'failed', 
        hasHydrated: true, 
        errorMessage: error instanceof Error ? error.message : 'Error cargando ejercicios.' 
      });
    }
  },
}));
