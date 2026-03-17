import notifee, {
  AndroidImportance,
  AuthorizationStatus,
  TriggerType,
  type TimestampTrigger,
} from '@notifee/react-native';
import { Platform } from 'react-native';
import type { CoreReminderSettings, WorkoutOverview } from '@kpkn/shared-types';
import type { SavedNutritionEntry } from '../types/nutrition';
import {
  persistNotificationPermissionSnapshot,
  readNotificationPermissionSnapshot,
} from './mobileDomainStateService';
import { loadSavedNutritionLogs } from './mobilePersistenceService';
import { loadWorkoutRuntimeState } from './workoutStateService';
import { getLocalDateKey } from '@kpkn/shared-domain';

const CHANNELS = {
  timers: 'kpkn-timers',
  reminders: 'kpkn-reminders',
  meals: 'kpkn-meals',
  recovery: 'kpkn-recovery',
  events: 'kpkn-events',
} as const;

const NOTIFICATION_IDS = {
  breakfast: 'meal-breakfast',
  lunch: 'meal-lunch',
  dinner: 'meal-dinner',
  sessionToday: 'session-today',
  missedWorkout: 'missed-workout',
  batteryLow: 'battery-low',
  restEnd: 'rest-end',
  events: ['event-0', 'event-1', 'event-2'],
} as const;

type MealWindow = 'breakfast' | 'lunch' | 'dinner';

interface NotificationSyncState {
  settings: CoreReminderSettings;
  nutritionLogs: SavedNutritionEntry[];
  workoutOverview: WorkoutOverview | null;
}

function parseLocalDate(raw: string) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    return new Date(`${raw}T12:00:00`);
  }
  return new Date(raw);
}

function getNextAtTime(hhmm: string, todayOnly = false) {
  const [hours, minutes] = hhmm.split(':').map(Number);
  const now = new Date();
  const at = new Date(now);
  at.setHours(hours || 0, minutes || 0, 0, 0);
  if (at.getTime() <= now.getTime() && !todayOnly) {
    at.setDate(at.getDate() + 1);
  }
  return at;
}

function getMealWindow(log: SavedNutritionEntry): MealWindow | null {
  const timestamp = parseLocalDate(log.createdAt);
  if (Number.isNaN(timestamp.getTime())) return null;
  const hour = timestamp.getHours();
  if (hour < 11) return 'breakfast';
  if (hour < 17) return 'lunch';
  return 'dinner';
}

function hasMealWindowLoggedToday(logs: SavedNutritionEntry[], window: MealWindow) {
  const today = getLocalDateKey();
  return logs.some(log => log.createdAt.slice(0, 10) === today && getMealWindow(log) === window);
}

async function setupNotificationChannels() {
  if (Platform.OS !== 'android') return;

  await notifee.createChannels([
    {
      id: CHANNELS.timers,
      name: 'Temporizadores',
      description: 'Avisos breves para descansos y bloques rápidos.',
      importance: AndroidImportance.HIGH,
    },
    {
      id: CHANNELS.reminders,
      name: 'Recordatorios',
      description: 'Recordatorios para tus sesiones del día.',
      importance: AndroidImportance.DEFAULT,
    },
    {
      id: CHANNELS.meals,
      name: 'Nutrición',
      description: 'Recordatorios suaves para registrar comidas.',
      importance: AndroidImportance.DEFAULT,
    },
    {
      id: CHANNELS.recovery,
      name: 'Recuperación',
      description: 'Avisos cuando convenga revisar tu recuperación.',
      importance: AndroidImportance.DEFAULT,
    },
    {
      id: CHANNELS.events,
      name: 'Eventos',
      description: 'Fechas relevantes de tu programa actual.',
      importance: AndroidImportance.DEFAULT,
    },
  ]);
}

async function readSystemNotificationPermission() {
  if (Platform.OS !== 'android') {
    return {
      status: 'unsupported' as const,
      granted: false,
    };
  }

  const settings = await notifee.getNotificationSettings();
  const granted = settings.authorizationStatus >= AuthorizationStatus.AUTHORIZED;
  return {
    status: granted ? ('authorized' as const) : ('blocked' as const),
    granted,
  };
}

async function persistPermissionSnapshot(granted: boolean, status: 'authorized' | 'blocked' | 'unsupported') {
  const previous = readNotificationPermissionSnapshot();
  const snapshot = {
    status,
    granted,
    lastCheckedAt: new Date().toISOString(),
    lastScheduledAt: previous.lastScheduledAt,
  };
  persistNotificationPermissionSnapshot(snapshot);
  return snapshot;
}

async function ensureNotificationPermission() {
  if (Platform.OS !== 'android') return false;

  const current = await readSystemNotificationPermission();
  if (current.granted) {
    await persistPermissionSnapshot(true, 'authorized');
    return true;
  }

  const next = await notifee.requestPermission();
  const granted = next.authorizationStatus >= AuthorizationStatus.AUTHORIZED;
  await persistPermissionSnapshot(granted, granted ? 'authorized' : 'blocked');
  return granted;
}

function buildTrigger(date: Date): TimestampTrigger {
  return {
    type: TriggerType.TIMESTAMP,
    timestamp: date.getTime(),
  };
}

async function cancelCoreNotifications() {
  await Promise.all([
    notifee.cancelNotification(NOTIFICATION_IDS.breakfast),
    notifee.cancelNotification(NOTIFICATION_IDS.lunch),
    notifee.cancelNotification(NOTIFICATION_IDS.dinner),
    notifee.cancelNotification(NOTIFICATION_IDS.sessionToday),
    notifee.cancelNotification(NOTIFICATION_IDS.missedWorkout),
    notifee.cancelNotification(NOTIFICATION_IDS.batteryLow),
    ...NOTIFICATION_IDS.events.map(id => notifee.cancelNotification(id)),
  ]);
}

async function scheduleTimedNotification(params: {
  id: string;
  title: string;
  body: string;
  channelId: string;
  at: Date;
}) {
  if (params.at.getTime() <= Date.now()) return;

  await notifee.createTriggerNotification(
    {
      id: params.id,
      title: params.title,
      body: params.body,
      data: {
        screen:
          params.channelId === CHANNELS.meals
            ? 'Nutrition'
            : params.channelId === CHANNELS.reminders || params.channelId === CHANNELS.events
              ? 'Workout'
              : params.channelId === CHANNELS.recovery
                ? 'Progress'
                : 'Home',
      },
      android: {
        channelId: params.channelId,
        pressAction: { id: 'default' },
        smallIcon: 'ic_launcher',
      },
    },
    buildTrigger(params.at),
  );
}

async function scheduleMealNotifications(state: NotificationSyncState) {
  if (!state.settings.mealRemindersEnabled) return;

  if (!hasMealWindowLoggedToday(state.nutritionLogs, 'breakfast')) {
    await scheduleTimedNotification({
      id: NOTIFICATION_IDS.breakfast,
      title: 'No olvides tu desayuno',
      body: 'Registra tu primera comida para que el día quede completo.',
      channelId: CHANNELS.meals,
      at: getNextAtTime(state.settings.breakfastReminderTime),
    });
  }

  if (!hasMealWindowLoggedToday(state.nutritionLogs, 'lunch')) {
    await scheduleTimedNotification({
      id: NOTIFICATION_IDS.lunch,
      title: 'Hora de registrar tu almuerzo',
      body: 'Mantén el seguimiento simple: almuerzo y seguimos.',
      channelId: CHANNELS.meals,
      at: getNextAtTime(state.settings.lunchReminderTime),
    });
  }

  if (!hasMealWindowLoggedToday(state.nutritionLogs, 'dinner')) {
    await scheduleTimedNotification({
      id: NOTIFICATION_IDS.dinner,
      title: 'Cierra el día con tu cena',
      body: 'Registra tu comida y deja el seguimiento al día.',
      channelId: CHANNELS.meals,
      at: getNextAtTime(state.settings.dinnerReminderTime),
    });
  }
}

async function scheduleWorkoutNotifications(state: NotificationSyncState) {
  if (!state.workoutOverview) return;

  const todaySession = state.workoutOverview.todaySession;
  if (
    state.settings.remindersEnabled &&
    state.settings.reminderTime &&
    todaySession &&
    !state.workoutOverview.hasWorkoutLoggedToday
  ) {
    await scheduleTimedNotification({
      id: NOTIFICATION_IDS.sessionToday,
      title: 'Hoy te toca entrenar',
      body: `${todaySession.name} · ${state.workoutOverview.activeProgramName ?? 'KPKN'}`,
      channelId: CHANNELS.reminders,
      at: getNextAtTime(state.settings.reminderTime, true),
    });
  }

  if (
    state.settings.missedWorkoutReminderEnabled &&
    todaySession &&
    !state.workoutOverview.hasWorkoutLoggedToday
  ) {
    await scheduleTimedNotification({
      id: NOTIFICATION_IDS.missedWorkout,
      title: '¿Registraste tu entrenamiento?',
      body: 'Si ya entrenaste, déjalo anotado para que el plan siga limpio.',
      channelId: CHANNELS.reminders,
      at: getNextAtTime(state.settings.missedWorkoutReminderTime, true),
    });
  }

  if (
    state.settings.augeBatteryReminderEnabled &&
    state.workoutOverview.battery &&
    state.workoutOverview.battery.overall < state.settings.augeBatteryReminderThreshold
  ) {
    await scheduleTimedNotification({
      id: NOTIFICATION_IDS.batteryLow,
      title: 'Recuperación baja',
      body: `Tu batería RN estimada va en ${Math.round(state.workoutOverview.battery.overall)}%. Vale la pena revisar descanso o volumen.`,
      channelId: CHANNELS.recovery,
      at: getNextAtTime(state.settings.augeBatteryReminderTime, true),
    });
  }

  if (state.settings.eventRemindersEnabled && state.workoutOverview.upcomingEvent) {
    const eventDate = parseLocalDate(state.workoutOverview.upcomingEvent.date);
    if (!Number.isNaN(eventDate.getTime())) {
      await scheduleTimedNotification({
        id: NOTIFICATION_IDS.events[0],
        title: 'Evento próximo',
        body: state.workoutOverview.upcomingEvent.title,
        channelId: CHANNELS.events,
        at: eventDate,
      });
    }
  }
}

export async function syncNotificationPermissionState() {
  const current = await readSystemNotificationPermission();
  const snapshot = await persistPermissionSnapshot(current.granted, current.status);
  if (!snapshot.granted) {
    await cancelCoreNotifications();
  }
  return snapshot;
}

function markNotificationsScheduled() {
  const previous = readNotificationPermissionSnapshot();
  persistNotificationPermissionSnapshot({
    ...previous,
    lastScheduledAt: new Date().toISOString(),
  });
}

export async function rescheduleCoreNotificationsFromState(state: NotificationSyncState) {
  if (Platform.OS !== 'android') return;

  await setupNotificationChannels();
  await cancelCoreNotifications();

  if (!(await ensureNotificationPermission())) {
    return;
  }

  await scheduleMealNotifications(state);
  await scheduleWorkoutNotifications(state);
  markNotificationsScheduled();
}

export async function rescheduleCoreNotificationsFromStorage() {
  const [nutritionLogs, workoutState] = await Promise.all([
    loadSavedNutritionLogs(),
    loadWorkoutRuntimeState(),
  ]);

  await rescheduleCoreNotificationsFromState({
    settings: workoutState.reminderSettings,
    nutritionLogs,
    workoutOverview: workoutState.overview,
  });
}

export async function scheduleRestTimerNotification(durationSeconds: number, label = 'Descanso') {
  if (Platform.OS !== 'android') return;

  await setupNotificationChannels();
  if (!(await ensureNotificationPermission())) {
    return;
  }

  await notifee.cancelNotification(NOTIFICATION_IDS.restEnd);

  await scheduleTimedNotification({
    id: NOTIFICATION_IDS.restEnd,
    title: 'Descanso terminado',
    body: label === 'Descanso' ? 'Ya puedes volver a la siguiente serie.' : `${label} listo. Puedes seguir.`,
    channelId: CHANNELS.timers,
    at: new Date(Date.now() + durationSeconds * 1000),
  });

  markNotificationsScheduled();
}

export async function cancelRestTimerNotification() {
  if (Platform.OS !== 'android') return;
  await notifee.cancelNotification(NOTIFICATION_IDS.restEnd);
}

export async function getNotificationPermissionSummary() {
  return syncNotificationPermissionState();
}
