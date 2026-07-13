import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { createPersistMultiKeyStorage } from './storageAdapter';
import type { BodyProgressLog, BodyLabAnalysis, BiomechanicalData, BiomechanicalAnalysis } from '../types';

type Updater<T> = T | ((prev: T) => T);

interface BodyStoreState {
    bodyProgress: BodyProgressLog[];
    bodyLabAnalysis: BodyLabAnalysis | null;
    biomechanicalData: BiomechanicalData | null;
    biomechanicalAnalysis: BiomechanicalAnalysis | null;
    _hasHydrated: boolean;

    setBodyProgress: (updater: Updater<BodyProgressLog[]>) => void;
    setBodyLabAnalysis: (analysis: BodyLabAnalysis | null) => void;
    setBiomechanicalData: (data: BiomechanicalData | null) => void;
    setBiomechanicalAnalysis: (updater: Updater<BiomechanicalAnalysis | null>) => void;
}

export const useBodyStore = create<BodyStoreState>()(
    persist(
        immer((set) => ({
            bodyProgress: [],
            bodyLabAnalysis: null,
            biomechanicalData: null,
            biomechanicalAnalysis: null,
            _hasHydrated: false,

            setBodyProgress: (updater) => set((state) => {
                state.bodyProgress = typeof updater === 'function'
                    ? (updater as (prev: BodyProgressLog[]) => BodyProgressLog[])(state.bodyProgress)
                    : updater;
            }),

            setBodyLabAnalysis: (analysis) => set((state) => {
                state.bodyLabAnalysis = analysis;
            }),

            setBiomechanicalData: (data) => set((state) => {
                state.biomechanicalData = data;
            }),

            setBiomechanicalAnalysis: (updater) => set((state) => {
                state.biomechanicalAnalysis = typeof updater === 'function'
                    ? (updater as (prev: BiomechanicalAnalysis | null) => BiomechanicalAnalysis | null)(state.biomechanicalAnalysis)
                    : updater;
            }),
        })),
        {
            name: 'kpkn-body-store',
            storage: createPersistMultiKeyStorage({
                bodyProgress: 'body-progress',
                bodyLabAnalysis: 'yourprime-bodylab-analysis',
                biomechanicalData: 'yourprime-biomechanical-data',
                biomechanicalAnalysis: 'yourprime-biomechanical-analysis',
            }),
            partialize: (state) => ({
                bodyProgress: state.bodyProgress,
                bodyLabAnalysis: state.bodyLabAnalysis,
                biomechanicalData: state.biomechanicalData,
                biomechanicalAnalysis: state.biomechanicalAnalysis,
            }),
            onRehydrateStorage: () => () => {
                useBodyStore.setState({ _hasHydrated: true });
            },
        }
    )
);
