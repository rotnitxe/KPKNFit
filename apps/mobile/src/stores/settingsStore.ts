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
  toggleWorkoutReminders: () => Promise<void>;
  toggleMealReminders: () => Promise<void>;
  toggleFallbackEnabled: () => Promise<void>;
  applyReminderPreset: (preset: ReminderPreset) => Promise<void>;
  clearNotice: () => void;
}

function buildSettingsSummary(raw: Record<string, unknown>, source: StoredSettingsSource): MobileSettingsSummary {
  return {
    ...raw,
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
