/**
 * @format
 */

import React from 'react';
import { AppState } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';
import App from '../App';
import { backgroundModule } from '../src/modules/background';
import {
  rescheduleCoreNotificationsFromStorage,
  syncNotificationPermissionState,
} from '../src/services/mobileNotificationService';
import { refreshWidgetSyncHealth } from '../src/services/widgetSyncService';

// Evita que este smoke test dependa de internals de React Navigation.
jest.mock('../src/navigation/AppNavigator', () => ({
  AppNavigator: () => null,
}));

jest.mock('../src/services/mobileNotificationService', () => ({
  rescheduleCoreNotificationsFromStorage: jest.fn().mockResolvedValue(undefined),
  syncNotificationPermissionState: jest.fn().mockResolvedValue({
    status: 'authorized',
    granted: true,
    lastCheckedAt: null,
    lastScheduledAt: null,
  }),
}));

jest.mock('../src/services/widgetSyncService', () => ({
  refreshWidgetSyncHealth: jest.fn().mockResolvedValue({
    stale: false,
    lastAttemptAt: null,
    lastSuccessfulSyncAt: null,
    lastError: null,
    source: 'foreground',
  }),
}));

jest.mock('../src/modules/background', () => ({
  backgroundModule: {
    schedulePeriodicSync: jest.fn().mockResolvedValue({ scheduled: true }),
  },
}));

describe('App shell', () => {
  let appStateHandler: ((state: string) => void) | null = null;
  let appStateSpy: jest.SpyInstance;

  beforeEach(() => {
    jest.clearAllMocks();
    appStateHandler = null;
    appStateSpy = jest.spyOn(AppState, 'addEventListener').mockImplementation((_, handler: any) => {
      appStateHandler = handler;
      return { remove: jest.fn() } as never;
    });
  });

  afterEach(() => {
    appStateSpy.mockRestore();
  });

  test('renders correctly and revalidates operational state on foreground', async () => {
    await ReactTestRenderer.act(async () => {
      ReactTestRenderer.create(<App />);
    });

    expect(syncNotificationPermissionState).toHaveBeenCalledTimes(1);
    expect(refreshWidgetSyncHealth).toHaveBeenCalledTimes(1);
    expect(backgroundModule.schedulePeriodicSync).toHaveBeenCalledTimes(1);

    await ReactTestRenderer.act(async () => {
      appStateHandler?.('active');
    });

    expect(syncNotificationPermissionState).toHaveBeenCalledTimes(2);
    expect(refreshWidgetSyncHealth).toHaveBeenCalledTimes(2);
    expect(rescheduleCoreNotificationsFromStorage).toHaveBeenCalledTimes(2);
  });
});
