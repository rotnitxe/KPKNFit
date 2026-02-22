import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { createMultiKeyStorage } from './storageAdapter';
import type {
    SleepLog, WaterLog, DailyWellbeingLog,
    PostSessionFeedback, PendingQuestionnaire,
    RecommendationTrigger, Task
} from '../types';

type Updater<T> = T | ((prev: T) => T);

interface WellbeingStoreState {
    sleepLogs: SleepLog[];
    sleepStartTime: number | null;
    waterLogs: WaterLog[];
    dailyWellbeingLogs: DailyWellbeingLog[];
    postSessionFeedback: PostSessionFeedback[];
    pendingQuestionnaires: PendingQuestionnaire[];
    recommendationTriggers: RecommendationTrigger[];
    tasks: Task[];
    _hasHydrated: boolean;

    setSleepLogs: (updater: Updater<SleepLog[]>) => void;
    setSleepStartTime: (updater: Updater<number | null>) => void;
    setWaterLogs: (updater: Updater<WaterLog[]>) => void;
    setDailyWellbeingLogs: (updater: Updater<DailyWellbeingLog[]>) => void;
    setPostSessionFeedback: (updater: Updater<PostSessionFeedback[]>) => void;
    setPendingQuestionnaires: (updater: Updater<PendingQuestionnaire[]>) => void;
    setRecommendationTriggers: (updater: Updater<RecommendationTrigger[]>) => void;
    setTasks: (updater: Updater<Task[]>) => void;
}

function applyUpdater<T>(current: T, updater: Updater<T>): T {
    return typeof updater === 'function'
        ? (updater as (prev: T) => T)(current)
        : updater;
}

export const useWellbeingStore = create<WellbeingStoreState>()(
    persist(
        immer((set) => ({
            sleepLogs: [],
            sleepStartTime: null,
            waterLogs: [],
            dailyWellbeingLogs: [],
            postSessionFeedback: [],
            pendingQuestionnaires: [],
            recommendationTriggers: [],
            tasks: [],
            _hasHydrated: false,

            setSleepLogs: (u) => set((s) => { s.sleepLogs = applyUpdater(s.sleepLogs, u); }),
            setSleepStartTime: (u) => set((s) => { s.sleepStartTime = applyUpdater(s.sleepStartTime, u); }),
            setWaterLogs: (u) => set((s) => { s.waterLogs = applyUpdater(s.waterLogs, u); }),
            setDailyWellbeingLogs: (u) => set((s) => { s.dailyWellbeingLogs = applyUpdater(s.dailyWellbeingLogs, u); }),
            setPostSessionFeedback: (u) => set((s) => { s.postSessionFeedback = applyUpdater(s.postSessionFeedback, u); }),
            setPendingQuestionnaires: (u) => set((s) => { s.pendingQuestionnaires = applyUpdater(s.pendingQuestionnaires, u); }),
            setRecommendationTriggers: (u) => set((s) => { s.recommendationTriggers = applyUpdater(s.recommendationTriggers, u); }),
            setTasks: (u) => set((s) => { s.tasks = applyUpdater(s.tasks, u); }),
        })),
        {
            name: 'kpkn-wellbeing-store',
            storage: createMultiKeyStorage({
                sleepLogs: 'yourprime-sleep-logs',
                sleepStartTime: 'yourprime-sleep-start-time',
                waterLogs: 'yourprime-water-logs',
                dailyWellbeingLogs: 'yourprime-daily-wellbeing',
                postSessionFeedback: 'yourprime-post-session-feedback',
                pendingQuestionnaires: 'yourprime-pending-questionnaires',
                recommendationTriggers: 'yourprime-recommendation-triggers',
                tasks: 'yourprime-tasks',
            }),
            partialize: (state) => ({
                sleepLogs: state.sleepLogs,
                sleepStartTime: state.sleepStartTime,
                waterLogs: state.waterLogs,
                dailyWellbeingLogs: state.dailyWellbeingLogs,
                postSessionFeedback: state.postSessionFeedback,
                pendingQuestionnaires: state.pendingQuestionnaires,
                recommendationTriggers: state.recommendationTriggers,
                tasks: state.tasks,
            }),
            onRehydrateStorage: () => () => {
                useWellbeingStore.setState({ _hasHydrated: true });
            },
        }
    )
);
