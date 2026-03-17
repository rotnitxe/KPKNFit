import { extractCoreReminderSettings } from '@kpkn/shared-domain';
import { appStorage, getJsonValue, setJsonValue } from '../storage/mmkv';
import type { AugeRuntimeSnapshot } from '../types/augeRuntime';

export type StoredSettingsSource = 'rn-owned' | 'migration-fallback' | 'defaults';

export type StoredWellbeingSource = 'rn-owned' | 'migration-fallback' | 'empty';
export type StoredTemplateSource = 'rn-owned' | 'migration-fallback' | 'empty';
export type WidgetSyncSource = 'foreground' | 'background' | 'unknown';

const KEYS = {
  settings: 'rn.settings',
  wellbeing: 'rn.wellbeing',
  mealTemplates: 'rn.mealTemplates',
  mealPlanner: 'rn.mealPlanner',
  notificationPermission: 'rn.notifications.permission',
  widgetSyncStatus: 'rn.widgets.syncStatus',
  backgroundSyncStatus: 'rn.background.syncStatus',
  augeRuntime: 'rn.augeRuntime',
} as const;


const MIGRATION_KEYS = {
  settings: 'migration.settings',
  wellbeingSleepLogs: 'migration.wellbeing.sleepLogs',
  wellbeingWaterLogs: 'migration.wellbeing.waterLogs',
  wellbeingDailyLogs: 'migration.wellbeing.dailyLogs',
  wellbeingTasks: 'migration.wellbeing.tasks',
  mealTemplates: 'migration.mealTemplates',
} as const;

export interface StoredWellbeingPayload {
  sleepLogs: unknown[];
  waterLogs: unknown[];
  dailyWellbeingLogs: unknown[];
  tasks: unknown[];
}

export interface NotificationPermissionSnapshot {
  status: 'authorized' | 'blocked' | 'unsupported';
  granted: boolean;
  lastCheckedAt: string | null;
  lastScheduledAt: string | null;
}

export interface WidgetSyncStatus {
  stale: boolean;
  lastAttemptAt: string | null;
  lastSuccessfulSyncAt: string | null;
  lastError: string | null;
  source: WidgetSyncSource;
}

export interface BackgroundSyncStatus {
  lastAttemptAt: string | null;
  lastCompletedAt: string | null;
  lastResult: 'idle' | 'running' | 'dispatching' | 'success' | 'failure';
  lastError: string | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function hasStoredKey(key: string) {
  return appStorage.getString(key) !== undefined;
}

function buildDefaultSettingsRaw() {
  const defaults = extractCoreReminderSettings(null);
  return {
    ...defaults,
    apiProvider: null,
    fallbackEnabled: false,
    workoutLoggerMode: 'simple',
    sessionCompactView: false,
    homeWidgetOrder: [],
    weightUnit: 'kg',
  } satisfies Record<string, unknown>;
}

export function getStoredSettingsSource(): StoredSettingsSource {
  if (hasStoredKey(KEYS.settings)) return 'rn-owned';
  if (hasStoredKey(MIGRATION_KEYS.settings)) return 'migration-fallback';
  return 'defaults';
}

export function readStoredSettingsRaw(): Record<string, unknown> {
  const own = getJsonValue<Record<string, unknown> | null>(KEYS.settings, null);
  if (isRecord(own)) return own;

  const migrated = getJsonValue<Record<string, unknown> | null>(MIGRATION_KEYS.settings, null);
  if (isRecord(migrated)) return migrated;

  return buildDefaultSettingsRaw();
}

export function persistStoredSettingsRaw(raw: Record<string, unknown>) {
  setJsonValue(KEYS.settings, raw);
}

export function patchStoredSettingsRaw(patch: Record<string, unknown>) {
  const next = {
    ...readStoredSettingsRaw(),
    ...patch,
  };
  persistStoredSettingsRaw(next);
  return next;
}

export function readStoredWellbeingPayload(): StoredWellbeingPayload {
  const own = getJsonValue<StoredWellbeingPayload | null>(KEYS.wellbeing, null);
  if (own && typeof own === 'object' && !Array.isArray(own)) {
    return {
      sleepLogs: asArray(own.sleepLogs),
      waterLogs: asArray(own.waterLogs),
      dailyWellbeingLogs: asArray(own.dailyWellbeingLogs),
      tasks: asArray(own.tasks),
    };
  }

  return {
    sleepLogs: asArray(getJsonValue<unknown[]>(MIGRATION_KEYS.wellbeingSleepLogs, [])),
    waterLogs: asArray(getJsonValue<unknown[]>(MIGRATION_KEYS.wellbeingWaterLogs, [])),
    dailyWellbeingLogs: asArray(getJsonValue<unknown[]>(MIGRATION_KEYS.wellbeingDailyLogs, [])),
    tasks: asArray(getJsonValue<unknown[]>(MIGRATION_KEYS.wellbeingTasks, [])),
  };
}

export function getStoredWellbeingSource(): StoredWellbeingSource {
  if (hasStoredKey(KEYS.wellbeing)) return 'rn-owned';
  if (
    hasStoredKey(MIGRATION_KEYS.wellbeingSleepLogs) ||
    hasStoredKey(MIGRATION_KEYS.wellbeingWaterLogs) ||
    hasStoredKey(MIGRATION_KEYS.wellbeingDailyLogs) ||
    hasStoredKey(MIGRATION_KEYS.wellbeingTasks)
  ) {
    return 'migration-fallback';
  }
  return 'empty';
}

export function persistStoredWellbeingPayload(payload: StoredWellbeingPayload) {
  setJsonValue(KEYS.wellbeing, {
    sleepLogs: asArray(payload.sleepLogs),
    waterLogs: asArray(payload.waterLogs),
    dailyWellbeingLogs: asArray(payload.dailyWellbeingLogs),
    tasks: asArray(payload.tasks),
  });
}

export function patchStoredWellbeingPayload(patch: Partial<StoredWellbeingPayload>) {
  const current = readStoredWellbeingPayload();
  const next: StoredWellbeingPayload = {
    sleepLogs: patch.sleepLogs ? asArray(patch.sleepLogs) : current.sleepLogs,
    waterLogs: patch.waterLogs ? asArray(patch.waterLogs) : current.waterLogs,
    dailyWellbeingLogs: patch.dailyWellbeingLogs ? asArray(patch.dailyWellbeingLogs) : current.dailyWellbeingLogs,
    tasks: patch.tasks ? asArray(patch.tasks) : current.tasks,
  };
  persistStoredWellbeingPayload(next);
  return next;
}

export function readStoredMealTemplatesRaw(): unknown[] {
  const own = getJsonValue<unknown[]>(KEYS.mealTemplates, []);
  if (own.length > 0 || hasStoredKey(KEYS.mealTemplates)) return asArray(own);
  return asArray(getJsonValue<unknown[]>(MIGRATION_KEYS.mealTemplates, []));
}

export function getStoredMealTemplateSource(): StoredTemplateSource {
  if (hasStoredKey(KEYS.mealTemplates)) return 'rn-owned';
  if (hasStoredKey(MIGRATION_KEYS.mealTemplates)) return 'migration-fallback';
  return 'empty';
}

export function persistStoredMealTemplatesRaw(templates: unknown[]) {
  setJsonValue(KEYS.mealTemplates, asArray(templates));
}

const DEFAULT_NOTIFICATION_PERMISSION: NotificationPermissionSnapshot = {
  status: 'unsupported',
  granted: false,
  lastCheckedAt: null,
  lastScheduledAt: null,
};

export function readNotificationPermissionSnapshot() {
  return getJsonValue<NotificationPermissionSnapshot>(KEYS.notificationPermission, DEFAULT_NOTIFICATION_PERMISSION);
}

export function persistNotificationPermissionSnapshot(snapshot: NotificationPermissionSnapshot) {
  setJsonValue(KEYS.notificationPermission, snapshot);
}

const DEFAULT_WIDGET_SYNC_STATUS: WidgetSyncStatus = {
  stale: true,
  lastAttemptAt: null,
  lastSuccessfulSyncAt: null,
  lastError: null,
  source: 'unknown',
};

export function readWidgetSyncStatus() {
  return getJsonValue<WidgetSyncStatus>(KEYS.widgetSyncStatus, DEFAULT_WIDGET_SYNC_STATUS);
}

export function persistWidgetSyncStatus(status: WidgetSyncStatus) {
  setJsonValue(KEYS.widgetSyncStatus, status);
}

const DEFAULT_BACKGROUND_SYNC_STATUS: BackgroundSyncStatus = {
  lastAttemptAt: null,
  lastCompletedAt: null,
  lastResult: 'idle',
  lastError: null,
};

export function readBackgroundSyncStatus() {
  return getJsonValue<BackgroundSyncStatus>(KEYS.backgroundSyncStatus, DEFAULT_BACKGROUND_SYNC_STATUS);
}

export function persistBackgroundSyncStatus(status: BackgroundSyncStatus) {
  setJsonValue(KEYS.backgroundSyncStatus, status);
}

export interface StoredMealPlannerPayload {
  activeWeekPlan: import('../types/mealPlanner').WeeklyMealPlan | null;
}

export function readStoredMealPlannerPayload(): StoredMealPlannerPayload {
  return getJsonValue<StoredMealPlannerPayload>(KEYS.mealPlanner, { activeWeekPlan: null });
}

export function persistStoredMealPlannerPayload(payload: StoredMealPlannerPayload) {
  setJsonValue(KEYS.mealPlanner, payload);
}

export interface StoredAugeRuntimePayload {
  snapshot: AugeRuntimeSnapshot | null;
}

export function readStoredAugeRuntimePayload(): StoredAugeRuntimePayload {
  return getJsonValue<StoredAugeRuntimePayload>(KEYS.augeRuntime, { snapshot: null });
}

export function persistStoredAugeRuntimePayload(payload: StoredAugeRuntimePayload) {
  setJsonValue(KEYS.augeRuntime, payload);
}

