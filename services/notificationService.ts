// services/notificationService.ts
import { Program, WorkoutLog, Settings, Session, NutritionLog, ActiveProgramState } from '../types';
import { Capacitor } from '@capacitor/core';
import { getWeekId } from '../utils/calculations';
import type { AugeAdaptiveCache } from './augeAdaptiveService';
import { getCachedAdaptiveData } from './augeAdaptiveService';

// ─── ID ranges (never cancel all; cancel by category) ─────────────────────
const ID_MEALS = { breakfast: 1, lunch: 2, dinner: 3 };
const ID_REST_END = 100;
const ID_SESSION_TODAY = 200;
const ID_AUGE_BATTERY = 300;
const ID_EVENTS_START = 400;
const ID_EVENTS_END = 499;
const ID_MISSED_START = 500;

export interface NotificationState {
  programs: Program[];
  activeProgramId: string | null;
  activeProgramState: ActiveProgramState | null;
  settings: Settings;
  history: WorkoutLog[];
  nutritionLogs: NutritionLog[];
  adaptiveCache: AugeAdaptiveCache;
}

function simpleHash(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0;
  }
  return Math.abs(hash) % 100000 + 60000; // questionnaire/other: 60k+
}

async function getLocalNotifications() {
  return await import('@capacitor/local-notifications');
}

async function ensurePermissions(): Promise<boolean> {
  if (!Capacitor.isNativePlatform()) return false;
  const { LocalNotifications } = await getLocalNotifications();
  const status = await LocalNotifications.checkPermissions();
  if (status.display === 'granted') return true;
  const result = await LocalNotifications.requestPermissions();
  return result.display === 'granted';
}

/** Cancel only the given IDs (never all pending). */
export async function cancelNotificationIds(ids: number[]): Promise<void> {
  if (!Capacitor.isNativePlatform() || ids.length === 0) return;
  try {
    const { LocalNotifications } = await getLocalNotifications();
    await LocalNotifications.cancel({ notifications: ids.map(id => ({ id })) });
  } catch (e) {
    console.error('Error canceling notification IDs', e);
  }
}

export const requestPermissions = async (): Promise<boolean> => {
  if (!Capacitor.isNativePlatform()) return false;
  try {
    const { LocalNotifications } = await getLocalNotifications();
    const result = await LocalNotifications.requestPermissions();
    return result.display === 'granted';
  } catch (e) {
    console.error('Error requesting notification permissions', e);
    return false;
  }
};

/** @deprecated Avoid: cancels ALL pending. Use cancelNotificationIds for category-specific cancel. */
export const cancelPendingNotifications = async () => {
  if (!Capacitor.isNativePlatform()) return;
  try {
    const { LocalNotifications } = await getLocalNotifications();
    const pending = await LocalNotifications.getPending();
    if (pending.notifications.length > 0) {
      await LocalNotifications.cancel(pending);
    }
  } catch (e) {
    console.error('Error canceling notifications', e);
  }
};

export const setupNotificationChannels = async () => {
  if (!Capacitor.isNativePlatform() || Capacitor.getPlatform() !== 'android') return;
  try {
    const { LocalNotifications } = await getLocalNotifications();
    await LocalNotifications.createChannel({
      id: 'yp_timers',
      name: 'Temporizadores',
      description: 'Descanso entre series: sonido y vibración',
      importance: 5,
      visibility: 1,
      sound: 'rest_beep_final.wav',
      vibration: true,
      lights: true,
    });
    await LocalNotifications.createChannel({
      id: 'yp_reminders',
      name: 'Recordatorios',
      description: 'Sesión del día y recordatorios generales',
      importance: 4,
      visibility: 1,
      sound: 'default',
      vibration: true,
    });
    await LocalNotifications.createChannel({
      id: 'yp_meals',
      name: 'Comidas',
      description: 'Recordatorios para registrar desayuno, almuerzo, cena',
      importance: 3,
      visibility: 1,
      sound: 'default',
      vibration: false,
    });
    await LocalNotifications.createChannel({
      id: 'yp_auge',
      name: 'Batería AUGE',
      description: 'Aviso cuando la batería AUGE está baja',
      importance: 4,
      visibility: 1,
      sound: 'default',
      vibration: true,
    });
    await LocalNotifications.createChannel({
      id: 'yp_events',
      name: 'Eventos y bloques',
      description: 'Inicio de bloque, día de evento o fecha clave',
      importance: 4,
      visibility: 1,
      sound: 'default',
      vibration: true,
    });
    await LocalNotifications.createChannel({
      id: 'yp_missed',
      name: 'Entrenamiento no registrado',
      description: 'Aviso si tenías sesión y no la registraste',
      importance: 4,
      visibility: 1,
      sound: 'default',
      vibration: true,
    });
  } catch (e) {
    console.error('Error creating notification channels', e);
  }
};

// ─── Rest timer: schedule one-shot at now + duration; cancel when foreground ends ───
export async function scheduleRestEndNotification(durationSeconds: number): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  if (!(await ensurePermissions())) return;
  try {
    const { LocalNotifications } = await getLocalNotifications();
    await LocalNotifications.cancel({ notifications: [{ id: ID_REST_END }] });
    const at = new Date(Date.now() + durationSeconds * 1000);
    await LocalNotifications.schedule({
      notifications: [{
        id: ID_REST_END,
        title: 'Descanso terminado',
        body: '¡A por la siguiente serie!',
        schedule: { at, allowWhileIdle: true },
        channelId: 'yp_timers',
      }],
    });
  } catch (e) {
    console.error('Error scheduling rest end notification', e);
  }
}

export async function cancelRestEndNotification(): Promise<void> {
  await cancelNotificationIds([ID_REST_END]);
}

// ─── Next occurrence of time today or tomorrow ───
function getNextAtTime(hhmm: string, todayOnly?: boolean): Date {
  const [h, m] = hhmm.split(':').map(Number);
  const now = new Date();
  const at = new Date(now);
  at.setHours(h, m ?? 0, 0, 0);
  if (at.getTime() <= now.getTime()) {
    if (todayOnly) return at;
    at.setDate(at.getDate() + 1);
  }
  return at;
}

function getTodayDateString(): string {
  const d = new Date();
  return `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-${d.getDate().toString().padStart(2, '0')}`;
}

function hasMealLoggedToday(logs: NutritionLog[], mealType: 'breakfast' | 'lunch' | 'dinner'): boolean {
  const today = getTodayDateString();
  return logs.some(log => log.date === today && log.mealType === mealType);
}

// ─── Meals ───
async function scheduleMealReminders(state: NotificationState): Promise<void> {
  const { settings, nutritionLogs } = state;
  if (!settings.mealRemindersEnabled || !Capacitor.isNativePlatform()) return;
  if (!(await ensurePermissions())) return;
  try {
    const { LocalNotifications } = await getLocalNotifications();
    await cancelNotificationIds([ID_MEALS.breakfast, ID_MEALS.lunch, ID_MEALS.dinner]);
    const notifications: any[] = [];
    const today = getTodayDateString();

    if (!hasMealLoggedToday(nutritionLogs, 'breakfast')) {
      const at = getNextAtTime(settings.breakfastReminderTime || '08:00');
      if (at.getTime() > Date.now()) {
        notifications.push({
          id: ID_MEALS.breakfast,
          title: '¿Registraste tu desayuno?',
          body: 'Añade tu desayuno para llevar el seguimiento.',
          schedule: { at },
          sound: 'default',
          channelId: 'yp_meals',
        });
      }
    }
    if (!hasMealLoggedToday(nutritionLogs, 'lunch')) {
      const at = getNextAtTime(settings.lunchReminderTime || '14:00');
      if (at.getTime() > Date.now()) {
        notifications.push({
          id: ID_MEALS.lunch,
          title: '¿Registraste tu almuerzo?',
          body: 'Añade tu almuerzo para llevar el seguimiento.',
          schedule: { at },
          sound: 'default',
          channelId: 'yp_meals',
        });
      }
    }
    if (!hasMealLoggedToday(nutritionLogs, 'dinner')) {
      const at = getNextAtTime(settings.dinnerReminderTime || '21:00');
      if (at.getTime() > Date.now()) {
        notifications.push({
          id: ID_MEALS.dinner,
          title: '¿Registraste tu cena?',
          body: 'Añade tu cena para llevar el seguimiento.',
          schedule: { at },
          sound: 'default',
          channelId: 'yp_meals',
        });
      }
    }
    if (notifications.length > 0) {
      await LocalNotifications.schedule({ notifications });
    }
  } catch (e) {
    console.error('Error scheduling meal reminders', e);
  }
}

// ─── Today's session (single notification at reminderTime if today has session) ───
function getTodaysSession(state: NotificationState): { session: Session; program: Program } | null {
  const { programs, activeProgramId, activeProgramState } = state;
  if (!activeProgramId || !activeProgramState || activeProgramState.programId !== activeProgramId) return null;
  const program = programs.find(p => p.id === activeProgramId);
  if (!program) return null;
  const now = new Date();
  const todayCalendarDay = now.getDay(); // 0=Sun .. 6=Sat
  const currentWeekId = activeProgramState.currentWeekId;

  for (const macro of program.macrocycles || []) {
    for (const block of macro.blocks || []) {
      for (const meso of block.mesocycles || []) {
        const week = meso.weeks?.find(w => w.id === currentWeekId);
        if (!week) continue;
        const session = week.sessions?.find(s => s.dayOfWeek !== undefined && s.dayOfWeek === todayCalendarDay);
        if (session) return { session, program };
      }
    }
  }
  return null;
}

async function scheduleSessionToday(state: NotificationState): Promise<void> {
  const { settings, history } = state;
  if (!settings.remindersEnabled || !settings.reminderTime || !Capacitor.isNativePlatform()) return;
  if (!(await ensurePermissions())) return;
  try {
    await cancelNotificationIds([ID_SESSION_TODAY]);
    const todays = getTodaysSession(state);
    if (!todays) return;
    const today = getTodayDateString();
    if (history.some(log => log.date === today)) return;
    const at = getNextAtTime(settings.reminderTime, true);
    if (at.getTime() <= Date.now()) return;
    const { LocalNotifications } = await getLocalNotifications();
    await LocalNotifications.schedule({
      notifications: [{
        id: ID_SESSION_TODAY,
        title: 'Hoy te toca entrenar',
        body: `${todays.session.name} — ${todays.program.name}`,
        schedule: { at },
        sound: 'default',
        channelId: 'yp_reminders',
      }],
    });
  } catch (e) {
    console.error('Error scheduling session today', e);
  }
}

// ─── AUGE battery low ───
function getAugeBatteryPct(cache: AugeAdaptiveCache): number | null {
  const banister = cache?.banister;
  if (!banister?.combined_performance?.length) return null;
  const arr = banister.combined_performance;
  const last = arr[arr.length - 1];
  if (typeof last !== 'number') return null;
  if (last <= 1 && last >= 0) return Math.round(last * 100);
  if (last <= 100 && last >= 0) return Math.round(last);
  return null;
}

async function scheduleAugeBattery(state: NotificationState): Promise<void> {
  const { settings, adaptiveCache } = state;
  if (!settings.augeBatteryReminderEnabled || !Capacitor.isNativePlatform()) return;
  if (!(await ensurePermissions())) return;
  try {
    await cancelNotificationIds([ID_AUGE_BATTERY]);
    const pct = getAugeBatteryPct(adaptiveCache);
    if (pct === null || pct >= (settings.augeBatteryReminderThreshold ?? 20)) return;
    const at = getNextAtTime(settings.augeBatteryReminderTime || '09:00', true);
    if (at.getTime() <= Date.now()) return;
    const { LocalNotifications } = await getLocalNotifications();
    await LocalNotifications.schedule({
      notifications: [{
        id: ID_AUGE_BATTERY,
        title: 'Batería AUGE baja',
        body: `Tu batería AUGE está al ${pct}%. Considera descanso o reducir volumen.`,
        schedule: { at },
        sound: 'default',
        channelId: 'yp_auge',
      }],
    });
  } catch (e) {
    console.error('Error scheduling AUGE battery notification', e);
  }
}

// ─── Events (next 30 days) and block change ───
async function scheduleEventReminders(state: NotificationState): Promise<void> {
  const { programs, activeProgramId, settings } = state;
  if (!settings.eventRemindersEnabled || !Capacitor.isNativePlatform()) return;
  if (!(await ensurePermissions())) return;
  try {
    const idsToCancel: number[] = [];
    for (let i = ID_EVENTS_START; i <= ID_EVENTS_END; i++) idsToCancel.push(i);
    await cancelNotificationIds(idsToCancel);

    const now = new Date();
    const notifications: any[] = [];
    let idx = 0;
    const program = activeProgramId ? programs.find(p => p.id === activeProgramId) : null;
    const events = program?.events || [];
    for (const ev of events) {
      if (idx + ID_EVENTS_START > ID_EVENTS_END) break;
      const dateStr = ev.date;
      if (!dateStr) continue;
      const evDate = new Date(dateStr);
      evDate.setHours(8, 0, 0, 0);
      if (evDate.getTime() < now.getTime()) continue;
      const daysDiff = Math.ceil((evDate.getTime() - now.getTime()) / (24 * 60 * 60 * 1000));
      if (daysDiff > 30) continue;
      const id = ID_EVENTS_START + idx++;
      notifications.push({
        id,
        title: 'Evento',
        body: ev.title || 'Fecha clave',
        schedule: { at: evDate },
        sound: 'default',
        channelId: 'yp_events',
      });
    }
    if (notifications.length > 0) {
      const { LocalNotifications } = await getLocalNotifications();
      await LocalNotifications.schedule({ notifications });
    }
  } catch (e) {
    console.error('Error scheduling event reminders', e);
  }
}

// ─── Missed workout (today had session assigned but no log) ───
function getTodaysSessionDayOfWeek(state: NotificationState): number | null {
  const todays = getTodaysSession(state);
  if (!todays) return null;
  return todays.session.dayOfWeek ?? null;
}

async function scheduleMissedWorkout(state: NotificationState): Promise<void> {
  const { settings, history } = state;
  if (!Capacitor.isNativePlatform()) return;
  if (!(await ensurePermissions())) return;
  try {
    const dayOfWeek = getTodaysSessionDayOfWeek(state);
    if (dayOfWeek === null) return;
    const id = ID_MISSED_START + (dayOfWeek % 7);
    await cancelNotificationIds([id]);

    if (!settings.missedWorkoutReminderEnabled) return;
    const today = getTodayDateString();
    const hasLogToday = history.some(log => log.date === today);
    if (hasLogToday) return;
    const at = getNextAtTime(settings.missedWorkoutReminderTime || '21:00', true);
    if (at.getTime() <= Date.now()) return;
    const { LocalNotifications } = await getLocalNotifications();
    await LocalNotifications.schedule({
      notifications: [{
        id,
        title: '¿Registraste tu entrenamiento?',
        body: 'Tenías una sesión hoy y aún no la has registrado.',
        schedule: { at },
        sound: 'default',
        channelId: 'yp_missed',
      }],
    });
  } catch (e) {
    console.error('Error scheduling missed workout notification', e);
  }
}

/** Cancel the "missed workout" notification for today (call when user logs workout). */
export async function cancelMissedWorkoutNotificationForToday(state: NotificationState): Promise<void> {
  const dayOfWeek = getTodaysSessionDayOfWeek(state);
  if (dayOfWeek === null) return;
  await cancelNotificationIds([ID_MISSED_START + (dayOfWeek % 7)]);
}

/** Central reschedule: run on app open/foreground, after settings change, after meal/workout log. */
export async function rescheduleAllNotifications(state: NotificationState): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  try {
    await setupNotificationChannels();
    await scheduleMealReminders(state);
    await scheduleSessionToday(state);
    await scheduleAugeBattery(state);
    await scheduleEventReminders(state);
    await scheduleMissedWorkout(state);
  } catch (e) {
    console.error('Error rescheduling notifications', e);
  }
}

// ─── Legacy: session reminders (now replaced by scheduleSessionToday; keep for callers that pass programs) ───
export const scheduleWorkoutReminders = async (programs: Program[], settings: Settings) => {
  if (!Capacitor.isNativePlatform()) return;
  await rescheduleAllNotifications({
    programs,
    activeProgramId: null,
    activeProgramState: null,
    settings,
    history: [],
    nutritionLogs: [],
    adaptiveCache: getCachedAdaptiveData(),
  });
};

export const triggerRestEndNotification = async () => {
  await scheduleRestEndNotification(1);
};

export const scheduleQuestionnaireNotification = async (logId: string, sessionName: string) => {
  if (!Capacitor.isNativePlatform()) return;
  try {
    if (!(await ensurePermissions())) return;
    const { LocalNotifications } = await getLocalNotifications();
    const notificationId = simpleHash(logId);
    await LocalNotifications.schedule({
      notifications: [{
        id: notificationId,
        title: '¿Cómo te sientes hoy?',
        body: `Responde el cuestionario sobre tu sesión de "${sessionName}" de ayer.`,
        schedule: { at: new Date(Date.now() + 24 * 60 * 60 * 1000) },
        sound: 'default',
        channelId: 'yp_reminders',
      }],
    });
  } catch (e) {
    console.error('Error scheduling questionnaire notification', e);
  }
};

const BEDTIME_NOTIF_ID = 999;
export const scheduleBedtimeReminder = async (bedTime: string) => {
  if (!Capacitor.isNativePlatform()) return;
  try {
    if (!(await ensurePermissions())) return;
    const [hours, minutes] = bedTime.split(':').map(Number);
    const reminderDate = new Date();
    reminderDate.setHours(hours, minutes ?? 0, 0, 0);
    reminderDate.setMinutes(reminderDate.getMinutes() - 30);
    if (reminderDate.getTime() < Date.now()) {
      reminderDate.setDate(reminderDate.getDate() + 1);
    }
    await cancelNotificationIds([BEDTIME_NOTIF_ID]);
    const { LocalNotifications } = await getLocalNotifications();
    await LocalNotifications.schedule({
      notifications: [{
        id: BEDTIME_NOTIF_ID,
        title: 'Hora de desconectar',
        body: `Tu ventana de sueño óptima comienza en 30 minutos (${bedTime}).`,
        schedule: { at: reminderDate },
        sound: 'default',
        channelId: 'yp_reminders',
        extra: { type: 'bedtime_reminder' },
      }],
    });
  } catch (e) {
    console.error('Error scheduling bedtime reminder', e);
  }
};
