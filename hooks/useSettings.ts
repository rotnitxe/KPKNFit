
// hooks/useSettings.ts
import { useCallback } from 'react';
import { Settings } from '../types';
import useLocalStorage from './useLocalStorage';

const SETTINGS_KEY = 'yourprime-settings';

const defaultSettings: Settings = {
  hasSeenWelcome: false,
  hasSeenHomeTour: false,
  hasSeenProgramEditorTour: false,
  hasSeenSessionEditorTour: false,
  hasSeenYourLabTour: false,
  
  username: "Atleta",
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
  userVitals: {
    workHours: 8,
    studyHours: 0,
    workIntensity: 'moderate',
    // Fixed: 'low' is not a valid IntensityLevel ('light' | 'moderate' | 'high'), changed to 'light'
    studyIntensity: 'light'
  },
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
  apiKeys: {
    gemini: '',
    deepseek: '',
    gpt: '',
  },
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
  // Default tabs active on new installation
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

function useSettings(): [Settings, (newSettings: Partial<Settings>) => void, boolean] {
  const [settings, setSettingsState, isLoading] = useLocalStorage<Settings>(SETTINGS_KEY, defaultSettings);

  const setSettings = useCallback((newSettings: Partial<Settings>) => {
    setSettingsState(prevSettings => ({
      ...prevSettings,
      ...newSettings,
    }));
  }, [setSettingsState]);

  return [settings, setSettings, isLoading];
}

export default useSettings;
