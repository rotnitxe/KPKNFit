import { useWorkoutStore } from '../../stores/workoutStore';
import { loadWorkoutRuntimeState } from '../../services/workoutStateService';
import {
  scheduleRestTimerNotification,
  cancelRestTimerNotification,
  rescheduleCoreNotificationsFromState,
} from '../../services/mobileNotificationService';
import { syncWorkoutWidgetState } from '../../services/widgetSyncService';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { loadPersistedDomainPayload, persistLocalWorkoutLog } from '../../services/mobilePersistenceService';
import type { WorkoutOverview, CoreReminderSettings } from '@kpkn/shared-types';

jest.mock('../../services/workoutStateService', () => ({
  loadWorkoutRuntimeState: jest.fn(),
}));

jest.mock('../../services/mobilePersistenceService', () => ({
  persistLocalWorkoutLog: jest.fn(),
  loadPersistedDomainPayload: jest.fn(),
  persistDomainPayload: jest.fn(),
}));

jest.mock('../../services/mobileNotificationService', () => ({
  scheduleRestTimerNotification: jest.fn(),
  cancelRestTimerNotification: jest.fn(),
  rescheduleCoreNotificationsFromState: jest.fn(),
}));

jest.mock('../../services/widgetSyncService', () => ({
  syncWorkoutWidgetState: jest.fn(),
}));

jest.mock('../../storage/mmkv', () => ({
  appStorage: { getString: jest.fn(), set: jest.fn(), delete: jest.fn() },
  setJsonValue: jest.fn(),
  getJsonValue: jest.fn(() => null),
}));

const mockReminderSettings: CoreReminderSettings = {
  remindersEnabled: false,
  reminderTime: null,
  mealRemindersEnabled: false,
  breakfastReminderTime: '08:00',
  lunchReminderTime: '13:00',
  dinnerReminderTime: '20:00',
  missedWorkoutReminderEnabled: false,
  missedWorkoutReminderTime: '21:00',
  augeBatteryReminderEnabled: false,
  augeBatteryReminderThreshold: 30,
  augeBatteryReminderTime: '09:00',
  eventRemindersEnabled: false,
  startWeekOn: 1,
};

const mockOverview: WorkoutOverview = {
  activeProgramId: 'prog-1',
  activeProgramName: 'Test Program',
  activeProgramStatus: 'active',
  currentWeekId: 'week-1',
  todaySession: {
    id: 'session-1',
    name: 'Push Day',
    dayOfWeek: 1,
    exerciseCount: 5,
    setCount: 20,
    focus: 'chest',
  },
  nextSession: null,
  nextSessionOffsetDays: null,
  upcomingEvent: null,
  recentLogs: [],
  plannedSetsThisWeek: 60,
  completedSetsThisWeek: 20,
  weeklySessionCount: 3,
  hasWorkoutLoggedToday: false,
  battery: null,
};

const mockRuntimeState = {
  overview: mockOverview,
  reminderSettings: mockReminderSettings,
  settingsPayload: {},
};

function resetStoreState() {
  useWorkoutStore.setState({
    status: 'idle',
    overview: null,
    reminderSettings: null,
    hasHydrated: false,
    errorMessage: null,
    notice: null,
    loggingState: 'idle',
  });
  useMobileNutritionStore.setState({ savedLogs: [] } as any);
}

describe('workoutStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    resetStoreState();
    (syncWorkoutWidgetState as jest.Mock).mockResolvedValue(undefined);
    (rescheduleCoreNotificationsFromState as jest.Mock).mockResolvedValue(undefined);
    (loadPersistedDomainPayload as jest.Mock).mockResolvedValue([]);
  });

  describe('hydrateFromMigration', () => {
    it('should hydrate successfully with workout data', async () => {
      (loadWorkoutRuntimeState as jest.Mock).mockResolvedValue(mockRuntimeState);

      await useWorkoutStore.getState().hydrateFromMigration();

      const state = useWorkoutStore.getState();
      expect(state.status).toBe('ready');
      expect(state.overview).toEqual(mockOverview);
      expect(state.reminderSettings).toEqual(mockReminderSettings);
      expect(state.hasHydrated).toBe(true);
      expect(state.errorMessage).toBeNull();
    });

    it('should set empty status when no overview', async () => {
      (loadWorkoutRuntimeState as jest.Mock).mockResolvedValue({
        overview: null,
        reminderSettings: mockReminderSettings,
        settingsPayload: {},
      });

      await useWorkoutStore.getState().hydrateFromMigration();

      expect(useWorkoutStore.getState().status).toBe('empty');
      expect(useWorkoutStore.getState().hasHydrated).toBe(true);
    });

    it('should sync widget state and notifications after hydration', async () => {
      (loadWorkoutRuntimeState as jest.Mock).mockResolvedValue(mockRuntimeState);

      await useWorkoutStore.getState().hydrateFromMigration();

      expect(syncWorkoutWidgetState).toHaveBeenCalledWith(mockOverview);
      expect(rescheduleCoreNotificationsFromState).toHaveBeenCalledTimes(1);
    });

    it('should handle hydration errors', async () => {
      (loadWorkoutRuntimeState as jest.Mock).mockRejectedValue(new Error('DB read error'));

      await useWorkoutStore.getState().hydrateFromMigration();

      const state = useWorkoutStore.getState();
      expect(state.status).toBe('failed');
      expect(state.errorMessage).toBe('DB read error');
      expect(state.hasHydrated).toBe(true);
    });
  });

  describe('refreshInfrastructure', () => {
    it('should refresh overview, widgets and notifications', async () => {
      (loadWorkoutRuntimeState as jest.Mock).mockResolvedValue(mockRuntimeState);

      await useWorkoutStore.getState().refreshInfrastructure();

      const state = useWorkoutStore.getState();
      expect(state.status).toBe('ready');
      expect(state.notice).toBe('Widgets y recordatorios actualizados.');
      expect(syncWorkoutWidgetState).toHaveBeenCalledWith(mockOverview);
    });

    it('should handle refresh errors', async () => {
      (loadWorkoutRuntimeState as jest.Mock).mockRejectedValue(new Error('Network timeout'));

      await useWorkoutStore.getState().refreshInfrastructure();

      expect(useWorkoutStore.getState().status).toBe('failed');
      expect(useWorkoutStore.getState().errorMessage).toBe('Network timeout');
    });
  });

  describe('logTodaySession', () => {
    it('should show notice if no todaySession', async () => {
      useWorkoutStore.setState({
        overview: { ...mockOverview, todaySession: null } as any,
      });

      await useWorkoutStore.getState().logTodaySession();

      expect(useWorkoutStore.getState().notice).toContain('no hay una sesión programada');
      expect(persistLocalWorkoutLog).not.toHaveBeenCalled();
    });

    it('should show notice if already logged today', async () => {
      useWorkoutStore.setState({
        overview: { ...mockOverview, hasWorkoutLoggedToday: true },
      });

      await useWorkoutStore.getState().logTodaySession();

      expect(useWorkoutStore.getState().notice).toContain('ya está registrada');
      expect(persistLocalWorkoutLog).not.toHaveBeenCalled();
    });

    it('should persist workout log and refresh state', async () => {
      useWorkoutStore.setState({ overview: mockOverview });
      (persistLocalWorkoutLog as jest.Mock).mockResolvedValue(undefined);
      (loadWorkoutRuntimeState as jest.Mock).mockResolvedValue({
        ...mockRuntimeState,
        overview: { ...mockOverview, hasWorkoutLoggedToday: true },
      });

      await useWorkoutStore.getState().logTodaySession();

      expect(persistLocalWorkoutLog).toHaveBeenCalledTimes(1);
      const persistedLog = (persistLocalWorkoutLog as jest.Mock).mock.calls[0][0];
      expect(persistedLog.sessionName).toBe('Push Day');
      expect(persistedLog.exerciseCount).toBe(5);
      expect(persistedLog.completedSetCount).toBe(20);

      const state = useWorkoutStore.getState();
      expect(state.notice).toContain('registrada');
      expect(state.loggingState).toBe('idle');
    });

    it('should handle logging errors', async () => {
      useWorkoutStore.setState({ overview: mockOverview });
      (persistLocalWorkoutLog as jest.Mock).mockRejectedValue(new Error('Write failed'));

      await useWorkoutStore.getState().logTodaySession();

      const state = useWorkoutStore.getState();
      expect(state.loggingState).toBe('idle');
      expect(state.errorMessage).toBe('Write failed');
    });
  });

  describe('rest timer', () => {
    it('should schedule a rest timer notification', async () => {
      useWorkoutStore.setState({ overview: mockOverview });
      (scheduleRestTimerNotification as jest.Mock).mockResolvedValue(undefined);

      await useWorkoutStore.getState().startRestTimer(90);

      expect(scheduleRestTimerNotification).toHaveBeenCalledWith(90, 'Push Day');
      expect(useWorkoutStore.getState().notice).toContain('90s');
    });

    it('should cancel rest timer', async () => {
      (cancelRestTimerNotification as jest.Mock).mockResolvedValue(undefined);

      await useWorkoutStore.getState().cancelRestTimer();

      expect(cancelRestTimerNotification).toHaveBeenCalledTimes(1);
      expect(useWorkoutStore.getState().notice).toContain('cancelado');
    });
  });

  describe('clearNotice', () => {
    it('should clear the notice', () => {
      useWorkoutStore.setState({ notice: 'Some notice' });
      useWorkoutStore.getState().clearNotice();
      expect(useWorkoutStore.getState().notice).toBeNull();
    });
  });
});
