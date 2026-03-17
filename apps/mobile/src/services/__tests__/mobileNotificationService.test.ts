import {
  rescheduleCoreNotificationsFromState,
  syncNotificationPermissionState,
  scheduleRestTimerNotification,
} from '../mobileNotificationService';
import notifee, { AuthorizationStatus } from '@notifee/react-native';
import { Platform } from 'react-native';
import {
  persistNotificationPermissionSnapshot,
  readNotificationPermissionSnapshot,
} from '../mobileDomainStateService';

jest.mock('@notifee/react-native', () => ({
  __esModule: true,
  default: {
    createChannels: jest.fn().mockResolvedValue(undefined),
    getNotificationSettings: jest.fn().mockResolvedValue({ authorizationStatus: 1 }),
    requestPermission: jest.fn().mockResolvedValue({ authorizationStatus: 1 }),
    cancelNotification: jest.fn().mockResolvedValue(undefined),
    createTriggerNotification: jest.fn().mockResolvedValue('notif-id'),
  },
  AndroidImportance: { HIGH: 4, DEFAULT: 3 },
  AuthorizationStatus: { AUTHORIZED: 1, DENIED: 0 },
  TriggerType: { TIMESTAMP: 0 },
}));

jest.mock('../mobileDomainStateService', () => ({
  persistNotificationPermissionSnapshot: jest.fn(),
  readNotificationPermissionSnapshot: jest.fn().mockReturnValue({
    status: 'authorized',
    granted: true,
    lastCheckedAt: null,
    lastScheduledAt: null,
  }),
}));

describe('mobileNotificationService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    Platform.OS = 'android';
  });

  it('should setup channels and schedule notifications if permission granted', async () => {
    const mockState: any = {
      settings: {
        mealRemindersEnabled: true,
        breakfastReminderTime: '08:00',
        lunchReminderTime: '13:00',
        dinnerReminderTime: '20:00',
      },
      nutritionLogs: [],
      workoutOverview: null,
    };

    await rescheduleCoreNotificationsFromState(mockState);

    expect(notifee.createChannels).toHaveBeenCalled();
    expect(notifee.createTriggerNotification).toHaveBeenCalledTimes(3); // Breakfast, Lunch, Dinner
    expect(persistNotificationPermissionSnapshot).toHaveBeenCalled();
  });

  it('should not schedule notifications if permission denied', async () => {
    (notifee.getNotificationSettings as jest.Mock).mockResolvedValueOnce({
      authorizationStatus: AuthorizationStatus.DENIED,
    });
    (notifee.requestPermission as jest.Mock).mockResolvedValueOnce({
      authorizationStatus: AuthorizationStatus.DENIED,
    });

    const mockState: any = {
      settings: { mealRemindersEnabled: true },
      nutritionLogs: [],
      workoutOverview: null,
    };

    await rescheduleCoreNotificationsFromState(mockState);

    expect(notifee.createTriggerNotification).not.toHaveBeenCalled();
  });

  it('should schedule rest timer notification', async () => {
    await scheduleRestTimerNotification(60, 'Descanso');

    expect(notifee.createTriggerNotification).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 'rest-end',
        title: 'Descanso terminado',
      }),
      expect.any(Object)
    );
  });

  it('should sync permission state', async () => {
    (notifee.getNotificationSettings as jest.Mock).mockResolvedValueOnce({
      authorizationStatus: AuthorizationStatus.AUTHORIZED,
    });

    const status = await syncNotificationPermissionState();

    expect(status.granted).toBe(true);
    expect(persistNotificationPermissionSnapshot).toHaveBeenCalled();
  });
});
