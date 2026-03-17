import type { CoreReminderSettings } from '@kpkn/shared-types';

export interface Settings extends CoreReminderSettings {
  apiProvider: string | null;
  fallbackEnabled: boolean;
  workoutLoggerMode: 'pro' | 'simple' | null;
  sessionCompactView: boolean;
  homeWidgetOrder: string[];
  weightUnit: 'kg' | 'lbs';
  barbellWeight: number;
  startWeekOn: number;
}

export type SettingsStatus = 'idle' | 'ready';
export type ReminderPreset = 'light' | 'full';

export interface MobileSettingsSummary extends Settings {
  // Mobile specific summary flags or extra fields
}
