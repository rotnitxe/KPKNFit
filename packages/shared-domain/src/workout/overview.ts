import type {
  CoreReminderSettings,
  RecoveryBatterySnapshot,
  WorkoutEventSummary,
  WorkoutLogSummary,
  WorkoutOverview,
  WorkoutSessionSummary,
} from '@kpkn/shared-types';

const DEFAULT_REMINDER_SETTINGS: CoreReminderSettings = {
  remindersEnabled: true,
  reminderTime: '18:00',
  mealRemindersEnabled: false,
  breakfastReminderTime: '08:00',
  lunchReminderTime: '14:00',
  dinnerReminderTime: '21:00',
  missedWorkoutReminderEnabled: false,
  missedWorkoutReminderTime: '21:00',
  augeBatteryReminderEnabled: false,
  augeBatteryReminderThreshold: 20,
  augeBatteryReminderTime: '09:00',
  eventRemindersEnabled: false,
  startWeekOn: 1,
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function stringOrNull(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null;
}

function numberOrNull(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function booleanOrDefault(value: unknown, fallback: boolean) {
  return typeof value === 'boolean' ? value : fallback;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function round(value: number) {
  return Math.round(value * 10) / 10;
}

function getLocalDateKey(date = new Date()) {
  return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')}`;
}

function normalizeDateKey(raw: unknown): string | null {
  const value = stringOrNull(raw);
  if (!value) return null;
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return null;
  return getLocalDateKey(parsed);
}

function getWeekKey(dateKey: string, startWeekOn: number) {
  const parsed = new Date(`${dateKey}T12:00:00`);
  if (Number.isNaN(parsed.getTime())) return dateKey;
  const day = parsed.getDay();
  let diff = day - startWeekOn;
  if (diff < 0) diff += 7;
  parsed.setDate(parsed.getDate() - diff);
  return getLocalDateKey(parsed);
}

function countSessionSets(rawSession: Record<string, unknown>) {
  return asArray(rawSession.exercises).reduce<number>((total, exercise) => {
    if (!isRecord(exercise)) return total;
    return total + asArray(exercise.sets).length;
  }, 0);
}

function adaptSession(rawSession: unknown): WorkoutSessionSummary | null {
  if (!isRecord(rawSession)) return null;
  const id = stringOrNull(rawSession.id);
  const name = stringOrNull(rawSession.name);
  if (!id || !name) return null;

  const dayOfWeek = numberOrNull(rawSession.dayOfWeek);
  const assignedDays = asArray(rawSession.assignedDays);
  const assignedDay = assignedDays.find(value => typeof value === 'number');

  return {
    id,
    name,
    dayOfWeek: dayOfWeek ?? (typeof assignedDay === 'number' ? assignedDay : null),
    exerciseCount: asArray(rawSession.exercises).length,
    setCount: countSessionSets(rawSession),
    focus: stringOrNull(rawSession.focus),
  };
}

function adaptWorkoutLog(rawLog: unknown): WorkoutLogSummary | null {
  if (!isRecord(rawLog)) return null;

  const id = stringOrNull(rawLog.id);
  const date = normalizeDateKey(rawLog.date);
  const programName = stringOrNull(rawLog.programName);
  const sessionName = stringOrNull(rawLog.sessionName);
  if (!id || !date || !programName || !sessionName) return null;

  const completedExercises = asArray(rawLog.completedExercises);
  const directExerciseCount = numberOrNull(rawLog.exerciseCount);
  const directCompletedSetCount = numberOrNull(rawLog.completedSetCount);
  const completedSetCount = directCompletedSetCount ?? completedExercises.reduce<number>((total, exercise) => {
    if (!isRecord(exercise)) return total;
    return total + asArray(exercise.sets).length;
  }, 0);

  const durationMinutes = numberOrNull(rawLog.duration) ?? numberOrNull(rawLog.durationMinutes);

  return {
    id,
    date,
    programName,
    sessionName,
    exerciseCount: directExerciseCount ?? completedExercises.length,
    completedSetCount,
    durationMinutes,
  };
}

function findActiveProgram(programsPayload: unknown) {
  if (!isRecord(programsPayload)) {
    return { activeProgram: null as Record<string, unknown> | null, activeState: null as Record<string, unknown> | null };
  }

  const activeState = isRecord(programsPayload.activeProgramState)
    ? programsPayload.activeProgramState
    : null;
  const activeProgramId = activeState ? stringOrNull(activeState.programId) : null;
  const programs = asArray(programsPayload.programs);

  const activeProgram = programs.find(program => isRecord(program) && stringOrNull(program.id) === activeProgramId);
  return {
    activeProgram: isRecord(activeProgram) ? activeProgram : null,
    activeState,
  };
}

function collectCurrentWeekSessions(activeProgram: Record<string, unknown>, currentWeekId: string | null) {
  const collected: WorkoutSessionSummary[] = [];

  for (const macro of asArray(activeProgram.macrocycles)) {
    if (!isRecord(macro)) continue;
    for (const block of asArray(macro.blocks)) {
      if (!isRecord(block)) continue;
      for (const mesocycle of asArray(block.mesocycles)) {
        if (!isRecord(mesocycle)) continue;
        for (const week of asArray(mesocycle.weeks)) {
          if (!isRecord(week)) continue;
          if (currentWeekId && stringOrNull(week.id) !== currentWeekId) continue;
          for (const rawSession of asArray(week.sessions)) {
            const session = adaptSession(rawSession);
            if (session) collected.push(session);
          }
        }
      }
    }
  }

  return collected;
}

function pickUpcomingEvent(activeProgram: Record<string, unknown>): WorkoutEventSummary | null {
  const today = new Date(`${getLocalDateKey()}T00:00:00`);
  let winner: WorkoutEventSummary | null = null;

  for (const rawEvent of asArray(activeProgram.events)) {
    if (!isRecord(rawEvent)) continue;
    const title = stringOrNull(rawEvent.title);
    const dateKey = normalizeDateKey(rawEvent.date);
    if (!title || !dateKey) continue;

    const date = new Date(`${dateKey}T00:00:00`);
    const diffDays = Math.ceil((date.getTime() - today.getTime()) / (24 * 60 * 60 * 1000));
    if (diffDays < 0) continue;

    const event: WorkoutEventSummary = {
      id: stringOrNull(rawEvent.id) ?? `${title}-${dateKey}`,
      title,
      date: dateKey,
      type: stringOrNull(rawEvent.type),
      daysUntil: diffDays,
    };

    if (!winner || event.daysUntil < winner.daysUntil) {
      winner = event;
    }
  }

  return winner;
}

function pickNextSession(
  sessions: WorkoutSessionSummary[],
  hasWorkoutLoggedToday: boolean,
) {
  if (sessions.length === 0) {
    return { session: null as WorkoutSessionSummary | null, offsetDays: null as number | null };
  }

  const todayDow = new Date().getDay();
  const ranked = sessions
    .filter(session => session.dayOfWeek !== null)
    .map(session => {
      const offset = ((session.dayOfWeek ?? todayDow) - todayDow + 7) % 7;
      return { session, offset };
    })
    .sort((left, right) => left.offset - right.offset);

  const preferred = ranked.find(candidate => candidate.offset > 0 || !hasWorkoutLoggedToday) ?? ranked[0];
  return {
    session: preferred?.session ?? null,
    offsetDays: preferred?.offset ?? null,
  };
}

function buildBatterySnapshot(wellbeingPayload: unknown, weeklySessionCount: number): RecoveryBatterySnapshot | null {
  if (!isRecord(wellbeingPayload)) return null;

  const latestLog = asArray(wellbeingPayload.dailyWellbeingLogs)
    .filter(isRecord)
    .map(log => ({
      log,
      dateKey: normalizeDateKey(log.date),
    }))
    .filter(entry => entry.dateKey !== null)
    .sort((left, right) => String(right.dateKey).localeCompare(String(left.dateKey)))[0];

  if (!latestLog) return null;

  const readiness = numberOrNull(latestLog.log.readiness);
  const sleepQuality = numberOrNull(latestLog.log.sleepQuality) ?? 5;
  const stressLevel = numberOrNull(latestLog.log.stressLevel) ?? 5;
  const doms = numberOrNull(latestLog.log.doms) ?? 5;
  const motivation = numberOrNull(latestLog.log.motivation) ?? 5;

  // Temporary RN estimate until the full AUGE engine is ported. The weights are intentionally
  // conservative so widgets/reminders remain directionally useful without pretending parity.
  const base = readiness !== null
    ? clamp(readiness * 10, 15, 100)
    : clamp((((sleepQuality + motivation + (11 - stressLevel) + (11 - doms)) / 4) * 10), 15, 95);

  const cns = clamp(base + (motivation - 5) * 4 - Math.max(stressLevel - 5, 0) * 4, 0, 100);
  const muscular = clamp(base - Math.max(doms - 5, 0) * 7 - weeklySessionCount * 2, 0, 100);
  const spinal = clamp(base + (sleepQuality - 5) * 4 - weeklySessionCount * 3, 0, 100);

  return {
    overall: round((cns + muscular + spinal) / 3),
    cns: round(cns),
    muscular: round(muscular),
    spinal: round(spinal),
    source: 'wellbeing-derived',
  };
}

export function extractCoreReminderSettings(rawSettings: unknown): CoreReminderSettings {
  if (!isRecord(rawSettings)) return DEFAULT_REMINDER_SETTINGS;

  return {
    remindersEnabled: booleanOrDefault(rawSettings.remindersEnabled, DEFAULT_REMINDER_SETTINGS.remindersEnabled),
    reminderTime: stringOrNull(rawSettings.reminderTime) ?? DEFAULT_REMINDER_SETTINGS.reminderTime,
    mealRemindersEnabled: booleanOrDefault(rawSettings.mealRemindersEnabled, DEFAULT_REMINDER_SETTINGS.mealRemindersEnabled),
    breakfastReminderTime: stringOrNull(rawSettings.breakfastReminderTime) ?? DEFAULT_REMINDER_SETTINGS.breakfastReminderTime,
    lunchReminderTime: stringOrNull(rawSettings.lunchReminderTime) ?? DEFAULT_REMINDER_SETTINGS.lunchReminderTime,
    dinnerReminderTime: stringOrNull(rawSettings.dinnerReminderTime) ?? DEFAULT_REMINDER_SETTINGS.dinnerReminderTime,
    missedWorkoutReminderEnabled: booleanOrDefault(rawSettings.missedWorkoutReminderEnabled, DEFAULT_REMINDER_SETTINGS.missedWorkoutReminderEnabled),
    missedWorkoutReminderTime: stringOrNull(rawSettings.missedWorkoutReminderTime) ?? DEFAULT_REMINDER_SETTINGS.missedWorkoutReminderTime,
    augeBatteryReminderEnabled: booleanOrDefault(rawSettings.augeBatteryReminderEnabled, DEFAULT_REMINDER_SETTINGS.augeBatteryReminderEnabled),
    augeBatteryReminderThreshold: numberOrNull(rawSettings.augeBatteryReminderThreshold) ?? DEFAULT_REMINDER_SETTINGS.augeBatteryReminderThreshold,
    augeBatteryReminderTime: stringOrNull(rawSettings.augeBatteryReminderTime) ?? DEFAULT_REMINDER_SETTINGS.augeBatteryReminderTime,
    eventRemindersEnabled: booleanOrDefault(rawSettings.eventRemindersEnabled, DEFAULT_REMINDER_SETTINGS.eventRemindersEnabled),
    startWeekOn: numberOrNull(rawSettings.startWeekOn) ?? DEFAULT_REMINDER_SETTINGS.startWeekOn,
  };
}

export function buildWorkoutOverview(input: {
  programsPayload: unknown;
  workoutPayload: unknown;
  wellbeingPayload?: unknown;
  settingsPayload?: unknown;
}): WorkoutOverview | null {
  const { activeProgram, activeState } = findActiveProgram(input.programsPayload);
  if (!activeProgram) return null;

  const reminderSettings = extractCoreReminderSettings(input.settingsPayload);
  const currentWeekId = activeState ? stringOrNull(activeState.currentWeekId) : null;
  const sessions = collectCurrentWeekSessions(activeProgram, currentWeekId);

  const logs = isRecord(input.workoutPayload)
    ? asArray(input.workoutPayload.history).map(adaptWorkoutLog).filter((value): value is WorkoutLogSummary => value !== null)
    : [];

  const todayKey = getLocalDateKey();
  const currentWeekKey = currentWeekId ?? getWeekKey(todayKey, reminderSettings.startWeekOn);
  const weeklyLogs = logs.filter(log => getWeekKey(log.date, reminderSettings.startWeekOn) === currentWeekKey);
  const hasWorkoutLoggedToday = logs.some(log => log.date === todayKey);
  const plannedSetsThisWeek = sessions.reduce((total, session) => total + session.setCount, 0);
  const completedSetsThisWeek = weeklyLogs.reduce((total, log) => total + log.completedSetCount, 0);
  const todaySession = sessions.find(session => session.dayOfWeek === new Date().getDay()) ?? null;
  const nextSelection = pickNextSession(sessions, hasWorkoutLoggedToday);
  const battery = buildBatterySnapshot(input.wellbeingPayload, weeklyLogs.length);

  return {
    activeProgramId: stringOrNull(activeProgram.id),
    activeProgramName: stringOrNull(activeProgram.name),
    activeProgramStatus: activeState && stringOrNull(activeState.status)
      ? (activeState.status as WorkoutOverview['activeProgramStatus'])
      : 'none',
    currentWeekId,
    todaySession,
    nextSession: nextSelection.session,
    nextSessionOffsetDays: nextSelection.offsetDays,
    upcomingEvent: pickUpcomingEvent(activeProgram),
    recentLogs: logs
      .sort((left, right) => right.date.localeCompare(left.date))
      .slice(0, 6),
    plannedSetsThisWeek,
    completedSetsThisWeek,
    weeklySessionCount: weeklyLogs.length,
    hasWorkoutLoggedToday,
    battery,
  };
}
