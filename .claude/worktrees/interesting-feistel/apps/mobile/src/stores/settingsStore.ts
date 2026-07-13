import { create } from 'zustand';
import type { Settings } from '../types/settings';
import {
  getStoredSettingsSource,
  patchStoredSettingsRaw,
  readStoredSettingsRaw,
  type StoredSettingsSource,
} from '../services/mobileDomainStateService';
import { rescheduleCoreNotificationsFromStorage } from '../services/mobileNotificationService';

type SettingsStatus = 'idle' | 'ready';
type ReminderPreset = 'light' | 'full';

export interface MobileSettingsSummary extends Settings {
  source: StoredSettingsSource;
}

interface SettingsStoreState {
  status: SettingsStatus;
  summary: MobileSettingsSummary | null;
  notice: string | null;
  hydrateFromMigration: () => Promise<void>;
  updateSettings: (partial: Partial<Settings>) => Promise<void>;
  getSettings: () => Settings | null;
  toggleWorkoutReminders: () => Promise<void>;
  toggleMealReminders: () => Promise<void>;
  toggleFallbackEnabled: () => Promise<void>;
  applyReminderPreset: (preset: ReminderPreset) => Promise<void>;
  clearNotice: () => void;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function normalizeSettingsSnapshot(raw: Record<string, unknown>): Record<string, unknown> {
  const normalized: Record<string, unknown> = { ...raw };

  if (typeof normalized.username !== 'string' || normalized.username.trim().length === 0) {
    const legacyUsername = normalized.userName;
    if (typeof legacyUsername === 'string' && legacyUsername.trim().length > 0) {
      normalized.username = legacyUsername;
    }
  }

  const userVitals = isRecord(normalized.userVitals) ? { ...normalized.userVitals } : {};
  if (typeof userVitals.age !== 'number' && typeof normalized.age === 'number') userVitals.age = normalized.age;
  if (typeof userVitals.weight !== 'number' && typeof normalized.weight === 'number') userVitals.weight = normalized.weight;
  if (typeof userVitals.height !== 'number' && typeof normalized.height === 'number') userVitals.height = normalized.height;
  if (typeof userVitals.gender !== 'string' && typeof normalized.gender === 'string') userVitals.gender = normalized.gender;
  if (typeof userVitals.activityLevel !== 'string' && typeof normalized.activityLevel === 'string') {
    userVitals.activityLevel = normalized.activityLevel;
  }
  normalized.userVitals = userVitals;

  if (normalized.hasSeenWelcome == null) normalized.hasSeenWelcome = false;
  if (normalized.hasSeenHomeTour == null) normalized.hasSeenHomeTour = false;
  if (normalized.hasSeenProgramEditorTour == null) normalized.hasSeenProgramEditorTour = false;
  if (normalized.hasSeenSessionEditorTour == null) normalized.hasSeenSessionEditorTour = false;
  if (normalized.hasSeenKPKNTour == null) normalized.hasSeenKPKNTour = false;
  if (normalized.hasSeenNutritionWizard == null) normalized.hasSeenNutritionWizard = false;
  if (normalized.hasDismissedNutritionSetup == null) normalized.hasDismissedNutritionSetup = false;
  if (normalized.hasSeenGeneralWizard == null) normalized.hasSeenGeneralWizard = false;
  if (normalized.hasPrecalibratedBattery == null) normalized.hasPrecalibratedBattery = false;
  if (normalized.precalibrationDismissed == null) normalized.precalibrationDismissed = false;
  if (normalized.hasSeenMuscleFatigueTip == null) normalized.hasSeenMuscleFatigueTip = false;
  if (normalized.athleteType == null) normalized.athleteType = 'enthusiast';

  if (normalized.restTimerDefaultSeconds == null) {
    const legacyDefaultRest = normalized.defaultRestSeconds;
    if (typeof legacyDefaultRest === 'number') {
      normalized.restTimerDefaultSeconds = legacyDefaultRest;
    }
  }
  if (normalized.restTimerAutoStart == null) {
    const legacyAutoStart = normalized.autoStartTimer;
    if (typeof legacyAutoStart === 'boolean') {
      normalized.restTimerAutoStart = legacyAutoStart;
    }
  }

  if (normalized.weightUnit == null) normalized.weightUnit = 'kg';
  if (normalized.soundsEnabled == null) normalized.soundsEnabled = true;
  if (normalized.intensityMetric == null) normalized.intensityMetric = 'rpe';
  if (normalized.barbellWeight == null) normalized.barbellWeight = 20;
  if (normalized.showTimeSaverPrompt == null) normalized.showTimeSaverPrompt = true;
  if (normalized.restTimerAutoStart == null) normalized.restTimerAutoStart = true;
  if (normalized.restTimerDefaultSeconds == null) normalized.restTimerDefaultSeconds = 90;
  if (normalized.sessionCompactView == null) normalized.sessionCompactView = false;
  if (normalized.sessionAutoAdvanceFields == null) normalized.sessionAutoAdvanceFields = true;
  if (normalized.showPRsInWorkout == null) normalized.showPRsInWorkout = true;
  if (normalized.readinessCheckEnabled == null) normalized.readinessCheckEnabled = true;
  if (normalized.workoutLoggerMode == null) normalized.workoutLoggerMode = 'pro';
  if (normalized.oneRMFormula == null) normalized.oneRMFormula = 'brzycki';
  if (normalized.apiProvider == null) normalized.apiProvider = 'gemini';
  if (normalized.fallbackEnabled == null) normalized.fallbackEnabled = true;
  if (normalized.aiTemperature == null) normalized.aiTemperature = 0.7;
  if (normalized.aiMaxTokens == null) normalized.aiMaxTokens = 2048;
  if (normalized.aiVoice == null) normalized.aiVoice = 'Puck';
  if (normalized.appTheme == null) normalized.appTheme = 'default';
  if (normalized.themePrimaryColor == null) normalized.themePrimaryColor = '#8B5CF6';
  if (normalized.enableGlassmorphism == null) normalized.enableGlassmorphism = true;
  if (normalized.enableAnimations == null) normalized.enableAnimations = true;
  if (normalized.enableGlowEffects == null) normalized.enableGlowEffects = true;
  if (normalized.enableZenMode == null) normalized.enableZenMode = false;
  if (normalized.enableParallax == null) normalized.enableParallax = true;
  if (normalized.themeCardBorderRadius == null) normalized.themeCardBorderRadius = 1.25;
  if (normalized.themeBlurAmount == null) normalized.themeBlurAmount = 40;
  if (normalized.themeGlowIntensity == null) normalized.themeGlowIntensity = 10;
  if (normalized.fontSizeScale == null) normalized.fontSizeScale = 1;
  if (normalized.hapticFeedbackEnabled == null) normalized.hapticFeedbackEnabled = true;
  if (normalized.hapticIntensity == null) normalized.hapticIntensity = 'medium';
  if (normalized.calorieGoalObjective == null) normalized.calorieGoalObjective = 'maintenance';
  if (normalized.smartSleepEnabled == null) normalized.smartSleepEnabled = true;
  if (normalized.sleepTargetHours == null) normalized.sleepTargetHours = 8;
  if (normalized.workDays == null) normalized.workDays = [1, 2, 3, 4, 5];
  if (normalized.wakeTimeWork == null) normalized.wakeTimeWork = '07:00';
  if (normalized.wakeTimeOff == null) normalized.wakeTimeOff = '09:00';
  if (normalized.startWeekOn == null) normalized.startWeekOn = 1;
  if (normalized.remindersEnabled == null) normalized.remindersEnabled = false;
  if (normalized.reminderTime == null) normalized.reminderTime = '17:00';
  if (normalized.mealRemindersEnabled == null) normalized.mealRemindersEnabled = false;
  if (normalized.breakfastReminderTime == null) normalized.breakfastReminderTime = '08:00';
  if (normalized.lunchReminderTime == null) normalized.lunchReminderTime = '14:00';
  if (normalized.dinnerReminderTime == null) normalized.dinnerReminderTime = '21:00';
  if (normalized.missedWorkoutReminderEnabled == null) normalized.missedWorkoutReminderEnabled = true;
  if (normalized.missedWorkoutReminderTime == null) normalized.missedWorkoutReminderTime = '21:00';
  if (normalized.augeBatteryReminderEnabled == null) normalized.augeBatteryReminderEnabled = false;
  if (normalized.augeBatteryReminderThreshold == null) normalized.augeBatteryReminderThreshold = 20;
  if (normalized.augeBatteryReminderTime == null) normalized.augeBatteryReminderTime = '09:00';
  if (normalized.eventRemindersEnabled == null) normalized.eventRemindersEnabled = true;
  if (normalized.autoSyncEnabled == null) normalized.autoSyncEnabled = false;
  if (normalized.tabBarStyle == null) normalized.tabBarStyle = 'default';
  if (normalized.enabledTabs == null) normalized.enabledTabs = ['home', 'nutrition', 'recovery', 'sleep'];
  if (!isRecord(normalized.apiKeys)) {
    normalized.apiKeys = {
      gemini: '',
      gpt: '',
      deepseek: '',
      usda: '',
    };
  }
  if (!isRecord(normalized.algorithmSettings)) {
    normalized.algorithmSettings = {
      oneRMDecayRate: 0.1,
      failureFatigueFactor: 1.25,
      legVolumeMultiplier: 1,
      torsoVolumeMultiplier: 1,
      synergistFactor: 0.3,
      augeEnableNutritionTracking: true,
      augeEnableSleepTracking: true,
      augeEnableWellbeingTracking: true,
    };
  }
  if (normalized.homeWidgetOrder == null) normalized.homeWidgetOrder = [];
  if (normalized.homeCardOrder == null) normalized.homeCardOrder = [];

  return normalized;
}

function buildSettingsSummary(raw: Record<string, unknown>, source: StoredSettingsSource): MobileSettingsSummary {
  return {
    ...normalizeSettingsSnapshot(raw),
    source,
  } as MobileSettingsSummary;
}

async function persistSummary(
  summary: MobileSettingsSummary,
  patch: Record<string, unknown>,
  notice: string,
) {
  const nextRaw = patchStoredSettingsRaw(patch);
  await rescheduleCoreNotificationsFromStorage();
  return {
    summary: buildSettingsSummary(nextRaw, 'rn-owned'),
    notice,
  } satisfies { summary: MobileSettingsSummary; notice: string };
}

export const useSettingsStore = create<SettingsStoreState>(set => ({
  status: 'idle',
  summary: null,
  notice: null,

  hydrateFromMigration: async () => {
    const source = getStoredSettingsSource();
    const rawSettings = readStoredSettingsRaw();
    set({
      status: 'ready',
      summary: buildSettingsSummary(rawSettings, source),
    });
  },

  updateSettings: async (partial: Partial<Settings>) => {
    patchStoredSettingsRaw(partial as Record<string, unknown>);
    await rescheduleCoreNotificationsFromStorage();
    const nextRaw = readStoredSettingsRaw();
    set({
      status: 'ready',
      summary: buildSettingsSummary(nextRaw, getStoredSettingsSource()),
      notice: 'Ajustes actualizados.',
    });
  },

  getSettings: () => {
    const rawSettings = readStoredSettingsRaw();
    return buildSettingsSummary(rawSettings, getStoredSettingsSource()) as Settings;
  },

  toggleWorkoutReminders: async () => {
    const currentRaw = readStoredSettingsRaw();
    const current = buildSettingsSummary(currentRaw, getStoredSettingsSource());
    const nextValue = !current.remindersEnabled;
    const persisted = await persistSummary(
      current,
      { remindersEnabled: nextValue },
      nextValue ? 'Recordatorios de entreno activados.' : 'Recordatorios de entreno apagados.',
    );
    set({ status: 'ready', ...persisted });
  },

  toggleMealReminders: async () => {
    const currentRaw = readStoredSettingsRaw();
    const current = buildSettingsSummary(currentRaw, getStoredSettingsSource());
    const nextValue = !current.mealRemindersEnabled;
    const persisted = await persistSummary(
      current,
      { mealRemindersEnabled: nextValue },
      nextValue ? 'Recordatorios de comida activados.' : 'Recordatorios de comida apagados.',
    );
    set({ status: 'ready', ...persisted });
  },

  toggleFallbackEnabled: async () => {
    const currentRaw = readStoredSettingsRaw();
    const current = buildSettingsSummary(currentRaw, getStoredSettingsSource());
    const nextValue = !current.fallbackEnabled;
    const persisted = await persistSummary(
      current,
      { fallbackEnabled: nextValue },
      nextValue ? 'Fallback IA activado.' : 'Fallback IA apagado.',
    );
    set({ status: 'ready', ...persisted });
  },

  applyReminderPreset: async preset => {
    const currentRaw = readStoredSettingsRaw();
    const current = buildSettingsSummary(currentRaw, getStoredSettingsSource());

    const patch =
      preset === 'light'
        ? {
            remindersEnabled: true,
            reminderTime: '19:00',
            mealRemindersEnabled: false,
            missedWorkoutReminderEnabled: false,
          }
        : {
            remindersEnabled: true,
            reminderTime: '18:00',
            mealRemindersEnabled: true,
            breakfastReminderTime: '08:00',
            lunchReminderTime: '14:00',
            dinnerReminderTime: '21:00',
            missedWorkoutReminderEnabled: true,
            missedWorkoutReminderTime: '21:00',
          };

    const persisted = await persistSummary(
      current,
      patch,
      preset === 'light' ? 'Perfil suave aplicado.' : 'Perfil de seguimiento aplicado.',
    );
    set({ status: 'ready', ...persisted });
  },

  clearNotice: () => set({ notice: null }),
}));
