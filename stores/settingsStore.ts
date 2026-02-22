import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { createMultiKeyStorage } from './storageAdapter';
import type { Settings } from '../types';

const defaultSettings: Settings = {
    hasSeenWelcome: false,
    hasSeenHomeTour: false,
    hasSeenProgramEditorTour: false,
    hasSeenSessionEditorTour: false,
    hasSeenKPKNTour: false,
    username: 'Atleta',
    profilePicture: undefined,
    athleteType: 'enthusiast',
    soundsEnabled: true,
    weightUnit: 'kg',
    intensityMetric: 'rpe',
    barbellWeight: 20,
    showTimeSaverPrompt: true,
    restTimerAutoStart: true,
    restTimerDefaultSeconds: 90,
    gymName: '',
    userVitals: { workHours: 8, studyHours: 0, workIntensity: 'moderate', studyIntensity: 'light' },
    calorieGoalObjective: 'maintenance',
    startWeekOn: 1,
    remindersEnabled: false,
    reminderTime: '17:00',
    autoSyncEnabled: false,
    appBackground: undefined,
    enableParallax: true,
    hapticFeedbackEnabled: true,
    hapticIntensity: 'medium',
    showPRsInWorkout: true,
    readinessCheckEnabled: true,
    apiProvider: 'gemini',
    fallbackEnabled: true,
    apiKeys: { gemini: '', deepseek: '', gpt: '', usda: 'zM7If362VYhfVVMbb25GSo7hzbO0birHlWLh5Hyt' },
    aiTemperature: 0.7,
    aiMaxTokens: 2048,
    aiVoice: 'Puck',
    appTheme: 'default',
    themePrimaryColor: '#8B5CF6',
    themeCardBorderRadius: 1.25,
    themeBlurAmount: 40,
    themeGlowIntensity: 10,
    fontSizeScale: 1.0,
    workoutLoggerMode: 'pro',
    oneRMFormula: 'brzycki',
    enableGlassmorphism: true,
    enableAnimations: true,
    enableGlowEffects: true,
    enableZenMode: false,
    enabledTabs: ['home', 'nutrition', 'recovery', 'sleep'],
    algorithmSettings: {
        oneRMDecayRate: 0.1,
        failureFatigueFactor: 1.25,
        legVolumeMultiplier: 1.0,
        torsoVolumeMultiplier: 1.0,
        synergistFactor: 0.3,
        augeEnableNutritionTracking: true,
        augeEnableSleepTracking: true,
    },
    smartSleepEnabled: true,
    sleepTargetHours: 8,
    workDays: [1, 2, 3, 4, 5],
    wakeTimeWork: '07:00',
    wakeTimeOff: '09:00',
};

export { defaultSettings };

interface SettingsStoreState {
    settings: Settings;
    _hasHydrated: boolean;
    setSettings: (partial: Partial<Settings>) => void;
    replaceSettings: (settings: Settings) => void;
}

export const useSettingsStore = create<SettingsStoreState>()(
    persist(
        immer((set) => ({
            settings: defaultSettings,
            _hasHydrated: false,

            setSettings: (partial) => set((state) => {
                Object.assign(state.settings, partial);
            }),

            replaceSettings: (newSettings) => set((state) => {
                state.settings = newSettings;
            }),
        })),
        {
            name: 'kpkn-settings-store',
            storage: createMultiKeyStorage({
                settings: 'yourprime-settings',
            }),
            partialize: (state) => ({ settings: state.settings }),
            merge: (persisted: any, current) => ({
                ...current,
                settings: { ...current.settings, ...(persisted?.settings || {}) },
            }),
            onRehydrateStorage: () => () => {
                useSettingsStore.setState({ _hasHydrated: true });
            },
        }
    )
);
