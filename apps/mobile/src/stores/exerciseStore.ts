import { create } from 'zustand';
import { loadPersistedDomainPayload } from '../services/mobilePersistenceService';
import { getJsonValue, setJsonValue } from '../storage/mmkv';
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
  addOrUpdateCustomExercise: (exercise: ExerciseCatalogEntry) => Promise<void>;
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
      // 1. First, check MMKV for previously saved/migrated data
      const cached = getJsonValue<ExerciseMigrationPayload | null>('rn.exercise', null);
      
      if (cached && cached.exerciseList && cached.exerciseList.length > 0) {
        set({ 
          status: 'ready', 
          hasHydrated: true, 
          exerciseList: cached.exerciseList, 
          exercisePlaylists: cached.exercisePlaylists || [], 
          muscleGroupData: cached.muscleGroupData || [], 
          muscleHierarchy: cached.muscleHierarchy || null,
          errorMessage: null
        });
        return;
      }
      
      // 2. Fallback to SQLite (migration key) if MMKV is empty
      const payload = await loadPersistedDomainPayload<ExerciseMigrationPayload>('exercise');
      if (!payload) {
        set({ status: 'ready', hasHydrated: true });
        return;
      }
      
      const exerciseList: ExerciseCatalogEntry[] = Array.isArray(payload.exerciseList) ? payload.exerciseList : [];
      const exercisePlaylists: ExercisePlaylist[] = Array.isArray(payload.exercisePlaylists) ? payload.exercisePlaylists : [];
      const muscleGroupData: MuscleGroupInfo[] = Array.isArray(payload.muscleGroupData) ? payload.muscleGroupData : [];
      const muscleHierarchy: MuscleHierarchy | null = payload.muscleHierarchy ?? null;
      
      // 3. Save to MMKV for future instant loads
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
  addOrUpdateCustomExercise: async (exercise: ExerciseCatalogEntry) => {
    try {
      const current = get();
      
      // 1. Verificar si el ejercicio viene de la lista estática (isCustom = false) o es custom (isCustom = true)
      const isStaticExercise = current.exerciseList.some(ex => ex.id === exercise.id);
      const exerciseWithFlag = { ...exercise, isCustom: !isStaticExercise };
      
      // 2. Buscar por ID, luego por nombre (case insensitive)
      const idxById = current.exerciseList.findIndex(ex => ex.id === exerciseWithFlag.id);
      if (idxById > -1) {
        // Actualizar por ID
        const updatedExerciseList = [...current.exerciseList];
        updatedExerciseList[idxById] = exerciseWithFlag;
        
        // Persistir en MMKV
        setJsonValue('rn.exercise', { 
          exerciseList: updatedExerciseList, 
          exercisePlaylists: current.exercisePlaylists, 
          muscleGroupData: current.muscleGroupData, 
          muscleHierarchy: current.muscleHierarchy 
        });
        
        set({ exerciseList: updatedExerciseList });
        return;
      }
      
      const idxByName = current.exerciseList.findIndex(
        ex => ex.name.toLowerCase() === exerciseWithFlag.name.toLowerCase()
      );
      if (idxByName > -1) {
        // Actualizar por nombre
        const updatedExerciseList = [...current.exerciseList];
        updatedExerciseList[idxByName] = exerciseWithFlag;
        
        // Persistir en MMKV
        setJsonValue('rn.exercise', { 
          exerciseList: updatedExerciseList, 
          exercisePlaylists: current.exercisePlaylists, 
          muscleGroupData: current.muscleGroupData, 
          muscleHierarchy: current.muscleHierarchy 
        });
        
        set({ exerciseList: updatedExerciseList });
        return;
      }
      
      // 3. Si no existe, push al array
      const updatedExerciseList = [...current.exerciseList, exerciseWithFlag];
      
      // Persistir en MMKV
      setJsonValue('rn.exercise', { 
        exerciseList: updatedExerciseList, 
        exercisePlaylists: current.exercisePlaylists, 
        muscleGroupData: current.muscleGroupData, 
        muscleHierarchy: current.muscleHierarchy 
      });
      
      set({ exerciseList: updatedExerciseList });
    } catch (error) {
      set({ 
        errorMessage: error instanceof Error ? error.message : 'Error al guardar el ejercicio personalizado.' 
      });
    }
  }
}));
