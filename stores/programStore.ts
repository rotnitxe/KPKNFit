import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { createMultiKeyStorage } from './storageAdapter';
import type { Program, ActiveProgramState } from '../types';

type Updater<T> = T | ((prev: T) => T);

interface ProgramStoreState {
    programs: Program[];
    activeProgramState: ActiveProgramState | null;
    _hasHydrated: boolean;

    setPrograms: (updater: Updater<Program[]>) => void;
    setActiveProgramState: (updater: Updater<ActiveProgramState | null>) => void;
}

export const useProgramStore = create<ProgramStoreState>()(
    persist(
        immer((set) => ({
            programs: [],
            activeProgramState: null,
            _hasHydrated: false,

            setPrograms: (updater) => set((state) => {
                state.programs = typeof updater === 'function'
                    ? (updater as (prev: Program[]) => Program[])(state.programs)
                    : updater;
            }),

            setActiveProgramState: (updater) => set((state) => {
                state.activeProgramState = typeof updater === 'function'
                    ? (updater as (prev: ActiveProgramState | null) => ActiveProgramState | null)(state.activeProgramState)
                    : updater;
            }),
        })),
        {
            name: 'kpkn-program-store',
            storage: createMultiKeyStorage({
                programs: 'programs',
                activeProgramState: 'yourprime-active-program-state',
            }),
            partialize: (state) => ({
                programs: state.programs,
                activeProgramState: state.activeProgramState,
            }),
            onRehydrateStorage: () => () => {
                useProgramStore.setState({ _hasHydrated: true });
            },
        }
    )
);
