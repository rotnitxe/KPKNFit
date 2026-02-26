import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { createPersistMultiKeyStorage } from './storageAdapter';
import { FULL_EXERCISE_LIST } from '../data/exerciseDatabaseMerged';
import { INITIAL_MUSCLE_GROUP_DATA } from '../data/initialMuscleGroupDatabase';
import { INITIAL_MUSCLE_HIERARCHY } from '../data/initialMuscleHierarchy';
import type { ExerciseMuscleInfo, ExercisePlaylist, MuscleGroupInfo, MuscleHierarchy } from '../types';

type Updater<T> = T | ((prev: T) => T);

function applyUpdater<T>(current: T, updater: Updater<T>): T {
    return typeof updater === 'function'
        ? (updater as (prev: T) => T)(current)
        : updater;
}

interface ExerciseStoreState {
    exerciseList: ExerciseMuscleInfo[];
    exercisePlaylists: ExercisePlaylist[];
    muscleGroupData: MuscleGroupInfo[];
    muscleHierarchy: MuscleHierarchy;
    _hasHydrated: boolean;

    setExerciseList: (updater: Updater<ExerciseMuscleInfo[]>) => void;
    setExercisePlaylists: (updater: Updater<ExercisePlaylist[]>) => void;
    setMuscleGroupData: (updater: Updater<MuscleGroupInfo[]>) => void;
    setMuscleHierarchy: (updater: Updater<MuscleHierarchy>) => void;

    addOrUpdateCustomExercise: (exercise: ExerciseMuscleInfo) => void;
}

export const useExerciseStore = create<ExerciseStoreState>()(
    persist(
        immer((set) => ({
            exerciseList: FULL_EXERCISE_LIST,
            exercisePlaylists: [],
            muscleGroupData: INITIAL_MUSCLE_GROUP_DATA,
            muscleHierarchy: INITIAL_MUSCLE_HIERARCHY,
            _hasHydrated: false,

            setExerciseList: (u) => set((s) => { s.exerciseList = applyUpdater(s.exerciseList, u); }),
            setExercisePlaylists: (u) => set((s) => { s.exercisePlaylists = applyUpdater(s.exercisePlaylists, u); }),
            setMuscleGroupData: (u) => set((s) => { s.muscleGroupData = applyUpdater(s.muscleGroupData, u); }),
            setMuscleHierarchy: (u) => set((s) => { s.muscleHierarchy = applyUpdater(s.muscleHierarchy, u); }),

            addOrUpdateCustomExercise: (exercise) => set((state) => {
                const isStatic = FULL_EXERCISE_LIST.some(ex => ex.id === exercise.id);
                const withFlag = { ...exercise, isCustom: !isStatic };

                const idxById = state.exerciseList.findIndex(ex => ex.id === withFlag.id);
                if (idxById > -1) {
                    state.exerciseList[idxById] = withFlag;
                    return;
                }

                const idxByName = state.exerciseList.findIndex(
                    ex => ex.name.toLowerCase() === withFlag.name.toLowerCase()
                );
                if (idxByName > -1) {
                    state.exerciseList[idxByName] = withFlag;
                    return;
                }

                state.exerciseList.push(withFlag);
            }),
        })),
        {
            name: 'kpkn-exercise-store',
            storage: createPersistMultiKeyStorage({
                exerciseList: 'yourprime-exercise-database',
                exercisePlaylists: 'yourprime-playlists',
                muscleGroupData: 'yourprime-muscle-group-data',
                muscleHierarchy: 'yourprime-muscle-hierarchy',
            }),
            partialize: (state) => ({
                exerciseList: state.exerciseList,
                exercisePlaylists: state.exercisePlaylists,
                muscleGroupData: state.muscleGroupData,
                muscleHierarchy: state.muscleHierarchy,
            }),
            onRehydrateStorage: () => (state, _err) => {
                useExerciseStore.setState({ _hasHydrated: true });
                // Migración: si se restauró una lista antigua (<400), usar la base ampliada
                const restored = state?.exerciseList;
                if (Array.isArray(restored) && restored.length < 400) {
                    useExerciseStore.setState({ exerciseList: FULL_EXERCISE_LIST });
                }
            },
        }
    )
);
