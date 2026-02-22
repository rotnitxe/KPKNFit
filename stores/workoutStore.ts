import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { createMultiKeyStorage } from './storageAdapter';
import type { WorkoutLog, SkippedWorkoutLog, OngoingWorkoutState } from '../types';

type Updater<T> = T | ((prev: T) => T);

interface WorkoutStoreState {
    history: WorkoutLog[];
    skippedLogs: SkippedWorkoutLog[];
    ongoingWorkout: OngoingWorkoutState | null;
    syncQueue: WorkoutLog[];
    _hasHydrated: boolean;

    setHistory: (updater: Updater<WorkoutLog[]>) => void;
    setSkippedLogs: (updater: Updater<SkippedWorkoutLog[]>) => void;
    setOngoingWorkout: (updater: Updater<OngoingWorkoutState | null>) => void;
    setSyncQueue: (updater: Updater<WorkoutLog[]>) => void;
}

export const useWorkoutStore = create<WorkoutStoreState>()(
    persist(
        immer((set) => ({
            history: [],
            skippedLogs: [],
            ongoingWorkout: null,
            syncQueue: [],
            _hasHydrated: false,

            setHistory: (updater) => set((state) => {
                state.history = typeof updater === 'function'
                    ? (updater as (prev: WorkoutLog[]) => WorkoutLog[])(state.history)
                    : updater;
            }),

            setSkippedLogs: (updater) => set((state) => {
                state.skippedLogs = typeof updater === 'function'
                    ? (updater as (prev: SkippedWorkoutLog[]) => SkippedWorkoutLog[])(state.skippedLogs)
                    : updater;
            }),

            setOngoingWorkout: (updater) => set((state) => {
                state.ongoingWorkout = typeof updater === 'function'
                    ? (updater as (prev: OngoingWorkoutState | null) => OngoingWorkoutState | null)(state.ongoingWorkout)
                    : updater;
            }),

            setSyncQueue: (updater) => set((state) => {
                state.syncQueue = typeof updater === 'function'
                    ? (updater as (prev: WorkoutLog[]) => WorkoutLog[])(state.syncQueue)
                    : updater;
            }),
        })),
        {
            name: 'kpkn-workout-store',
            storage: createMultiKeyStorage({
                history: 'history',
                skippedLogs: 'skipped-logs',
                ongoingWorkout: 'ongoing-workout-session',
                syncQueue: 'yourprime-sync-queue',
            }),
            partialize: (state) => ({
                history: state.history,
                skippedLogs: state.skippedLogs,
                ongoingWorkout: state.ongoingWorkout,
                syncQueue: state.syncQueue,
            }),
            onRehydrateStorage: () => () => {
                useWorkoutStore.setState({ _hasHydrated: true });
            },
        }
    )
);
