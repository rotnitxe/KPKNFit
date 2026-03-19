import { create } from 'zustand';
import { loadPersistedDomainPayload } from '../services/mobilePersistenceService';
import { getJsonValue, setJsonValue } from '../storage/mmkv';
import type {
  ExerciseCatalogEntry,
  ExercisePlaylist,
  MuscleGroupInfo,
  MuscleHierarchy,
  WorkoutLog,
} from '../types/workout';

interface ExerciseMigrationPayload {
  exerciseList?: ExerciseCatalogEntry[];
  exercisePlaylists?: ExercisePlaylist[];
  muscleGroupData?: MuscleGroupInfo[];
  muscleHierarchy?: MuscleHierarchy | null;
}

interface ExerciseHistoryEntry {
  date: string;
  sessionName: string;
  sets: { weight?: number; completedReps?: number; targetReps?: number; completedRPE?: number }[];
}

interface ExercisePR {
  weight: number;
  reps: number;
  e1RM: number;
  date: string;
}

interface ExerciseStoreState {
  status: 'idle' | 'ready' | 'failed';
  exerciseList: ExerciseCatalogEntry[];
  exercisePlaylists: ExercisePlaylist[];
  muscleGroupData: MuscleGroupInfo[];
  muscleHierarchy: MuscleHierarchy | null;
  hasHydrated: boolean;
  errorMessage: string | null;
  getExerciseById: (id: string) => ExerciseCatalogEntry | undefined;
  hydrateFromMigration: () => Promise<void>;
  addOrUpdateCustomExercise: (exercise: ExerciseCatalogEntry) => Promise<void>;
  updateExerciseNotes: (exerciseId: string, notes: string) => void;
  addPlaylist: (name: string, description?: string) => void;
  removePlaylist: (playlistId: string) => void;
  updatePlaylist: (playlistId: string, updates: Partial<ExercisePlaylist>) => void;
  addExerciseToPlaylist: (playlistId: string, exerciseId: string) => void;
  removeExerciseFromPlaylist: (playlistId: string, exerciseId: string) => void;
  getExerciseHistory: (exerciseId: string, history: WorkoutLog[]) => ExerciseHistoryEntry[];
  getExercisePRs: (exerciseId: string, history: WorkoutLog[]) => ExercisePR[];
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
      const cached = getJsonValue<ExerciseMigrationPayload | null>('rn.exercise', null);
      if (cached && cached.exerciseList && cached.exerciseList.length > 0) {
        set({
          status: 'ready',
          hasHydrated: true,
          exerciseList: cached.exerciseList,
          exercisePlaylists: cached.exercisePlaylists || [],
          muscleGroupData: cached.muscleGroupData || [],
          muscleHierarchy: cached.muscleHierarchy || null,
          errorMessage: null,
        });
        return;
      }
      const payload = await loadPersistedDomainPayload<ExerciseMigrationPayload>('exercise');
      if (!payload) {
        set({ status: 'ready', hasHydrated: true });
        return;
      }
      const exerciseList = Array.isArray(payload.exerciseList) ? payload.exerciseList : [];
      const exercisePlaylists = Array.isArray(payload.exercisePlaylists) ? payload.exercisePlaylists : [];
      const muscleGroupData = Array.isArray(payload.muscleGroupData) ? payload.muscleGroupData : [];
      const muscleHierarchy = payload.muscleHierarchy ?? null;
      setJsonValue('rn.exercise', { exerciseList, exercisePlaylists, muscleGroupData, muscleHierarchy });
      set({
        status: 'ready',
        hasHydrated: true,
        exerciseList,
        exercisePlaylists,
        muscleGroupData,
        muscleHierarchy,
        errorMessage: null,
      });
    } catch (error) {
      set({
        status: 'failed',
        hasHydrated: true,
        errorMessage: error instanceof Error ? error.message : 'Error cargando ejercicios.',
      });
    }
  },
  addOrUpdateCustomExercise: async (exercise: ExerciseCatalogEntry) => {
    try {
      const current = get();
      const isStatic = current.exerciseList.some(ex => ex.id === exercise.id);
      const exerciseWithFlag = { ...exercise, isCustom: !isStatic };
      const idxById = current.exerciseList.findIndex(ex => ex.id === exerciseWithFlag.id);
      if (idxById > -1) {
        const updated = [...current.exerciseList];
        updated[idxById] = exerciseWithFlag;
        setJsonValue('rn.exercise', {
          exerciseList: updated,
          exercisePlaylists: current.exercisePlaylists,
          muscleGroupData: current.muscleGroupData,
          muscleHierarchy: current.muscleHierarchy,
        });
        set({ exerciseList: updated });
        return;
      }
      const idxByName = current.exerciseList.findIndex(
        ex => ex.name.toLowerCase() === exerciseWithFlag.name.toLowerCase()
      );
      if (idxByName > -1) {
        const updated = [...current.exerciseList];
        updated[idxByName] = exerciseWithFlag;
        setJsonValue('rn.exercise', {
          exerciseList: updated,
          exercisePlaylists: current.exercisePlaylists,
          muscleGroupData: current.muscleGroupData,
          muscleHierarchy: current.muscleHierarchy,
        });
        set({ exerciseList: updated });
        return;
      }
      const updated = [...current.exerciseList, exerciseWithFlag];
      setJsonValue('rn.exercise', {
        exerciseList: updated,
        exercisePlaylists: current.exercisePlaylists,
        muscleGroupData: current.muscleGroupData,
        muscleHierarchy: current.muscleHierarchy,
      });
      set({ exerciseList: updated });
    } catch (error) {
      set({
        errorMessage: error instanceof Error ? error.message : 'Error al guardar el ejercicio personalizado.',
      });
    }
  },

  updateExerciseNotes: (_exerciseId: string, _notes: string) => {
    // Notes stored in executionCues or description in ExerciseMuscleInfo
  },

  addPlaylist: (name: string, description?: string) => {
    const current = get();
    const newPlaylist: ExercisePlaylist = {
      id: `playlist_${Date.now()}`,
      name,
      exerciseIds: [],
    };
    const updated = [...current.exercisePlaylists, newPlaylist];
    setJsonValue('rn.exercise', {
      exerciseList: current.exerciseList,
      exercisePlaylists: updated,
      muscleGroupData: current.muscleGroupData,
      muscleHierarchy: current.muscleHierarchy,
    });
    set({ exercisePlaylists: updated });
  },

  removePlaylist: (playlistId: string) => {
    const current = get();
    const updated = current.exercisePlaylists.filter(p => p.id !== playlistId);
    setJsonValue('rn.exercise', {
      exerciseList: current.exerciseList,
      exercisePlaylists: updated,
      muscleGroupData: current.muscleGroupData,
      muscleHierarchy: current.muscleHierarchy,
    });
    set({ exercisePlaylists: updated });
  },

  updatePlaylist: (playlistId: string, updates: Partial<ExercisePlaylist>) => {
    const current = get();
    const idx = current.exercisePlaylists.findIndex(p => p.id === playlistId);
    if (idx === -1) return;
    const updated = [...current.exercisePlaylists];
    updated[idx] = { ...updated[idx], ...updates };
    setJsonValue('rn.exercise', {
      exerciseList: current.exerciseList,
      exercisePlaylists: updated,
      muscleGroupData: current.muscleGroupData,
      muscleHierarchy: current.muscleHierarchy,
    });
    set({ exercisePlaylists: updated });
  },

  addExerciseToPlaylist: (playlistId: string, exerciseId: string) => {
    const current = get();
    const idx = current.exercisePlaylists.findIndex(p => p.id === playlistId);
    if (idx === -1) return;
    const playlist = current.exercisePlaylists[idx];
    if (playlist.exerciseIds.includes(exerciseId)) return;
    const updated = [...current.exercisePlaylists];
    updated[idx] = { ...playlist, exerciseIds: [...playlist.exerciseIds, exerciseId] };
    setJsonValue('rn.exercise', {
      exerciseList: current.exerciseList,
      exercisePlaylists: updated,
      muscleGroupData: current.muscleGroupData,
      muscleHierarchy: current.muscleHierarchy,
    });
    set({ exercisePlaylists: updated });
  },

  removeExerciseFromPlaylist: (playlistId: string, exerciseId: string) => {
    const current = get();
    const idx = current.exercisePlaylists.findIndex(p => p.id === playlistId);
    if (idx === -1) return;
    const playlist = current.exercisePlaylists[idx];
    const updated = [...current.exercisePlaylists];
    updated[idx] = { ...playlist, exerciseIds: playlist.exerciseIds.filter(id => id !== exerciseId) };
    setJsonValue('rn.exercise', {
      exerciseList: current.exerciseList,
      exercisePlaylists: updated,
      muscleGroupData: current.muscleGroupData,
      muscleHierarchy: current.muscleHierarchy,
    });
    set({ exercisePlaylists: updated });
  },

  getExerciseHistory: (exerciseId: string, history: WorkoutLog[]) => {
    const exercise = get().exerciseList.find(e => e.id === exerciseId);
    const searchName = exercise?.name.toLowerCase() || '';
    return history
      .map(log => {
        const completedEx = log.completedExercises.find(ce => {
          if (ce.exerciseId === exerciseId) return true;
          if (ce.exerciseDbId === exerciseId) return true;
          const ceName = (ce.exerciseName || '').toLowerCase();
          return ceName === searchName;
        });
        if (completedEx) {
          return { date: log.date, sessionName: log.sessionName, sets: completedEx.sets };
        }
        return null;
      })
      .filter((log): log is NonNullable<typeof log> => log !== null)
      .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
  },

  getExercisePRs: (exerciseId: string, history: WorkoutLog[]) => {
    const logs = get().getExerciseHistory(exerciseId, history);
    const prs: { weight: number; reps: number; e1RM: number; date: string }[] = [];
    logs.forEach(log => {
      log.sets.forEach(set => {
        if (set.weight && set.completedReps) {
          const e1RM = set.weight * (1 + set.completedReps / 30);
          prs.push({ weight: set.weight, reps: set.completedReps, e1RM, date: log.date });
        }
      });
    });
    return prs.sort((a, b) => b.e1RM - a.e1RM);
  },
}));
