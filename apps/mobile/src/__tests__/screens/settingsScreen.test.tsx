jest.mock('@react-navigation/native', () => {
  const actual = jest.requireActual('@react-navigation/native');
  return {
    ...actual,
    useNavigation: jest.fn(),
  };
});

jest.mock('../../services/mobileDomainStateService', () => ({
  getStoredSettingsSource: jest.fn(() => 'rn-owned'),
  patchStoredSettingsRaw: jest.fn((patch: Record<string, unknown>) => patch),
  readStoredSettingsRaw: jest.fn(() => ({})),
}));

jest.mock('../../services/mobileNotificationService', () => ({
  rescheduleCoreNotificationsFromStorage: jest.fn(() => Promise.resolve()),
}));

import React from 'react';
import { Text } from 'react-native';
import renderer, { act } from 'react-test-renderer';
import { ThemeProvider } from '../../theme';
import { useNavigation } from '@react-navigation/native';
import { SettingsScreen } from '../../screens/Settings/SettingsScreen';
import { useSettingsStore } from '../../stores/settingsStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';
import { useCutoverStore } from '../../stores/cutoverStore';

const mockNavigate = jest.fn();

function getNavigationMock() {
  return {
    navigate: mockNavigate,
    goBack: jest.fn(),
    canGoBack: jest.fn(() => true),
    emit: jest.fn(() => ({ defaultPrevented: false })),
  } as any;
}

const defaultSettingsSummary = {
  source: 'rn-owned' as const,
  hasSeenWelcome: true,
  hasSeenHomeTour: true,
  hasSeenProgramEditorTour: true,
  hasSeenSessionEditorTour: true,
  hasSeenKPKNTour: true,
  athleteType: 'enthusiast' as const,
  soundsEnabled: true,
  weightUnit: 'kg' as const,
  intensityMetric: 'rpe' as const,
  barbellWeight: 20,
  showTimeSaverPrompt: true,
  restTimerAutoStart: true,
  restTimerDefaultSeconds: 90,
  sessionCompactView: false,
  showPRsInWorkout: true,
  readinessCheckEnabled: true,
  workoutLoggerMode: 'pro' as const,
  oneRMFormula: 'brzycki' as const,
  apiProvider: 'gemini' as const,
  fallbackEnabled: true,
  aiTemperature: 0.7,
  aiMaxTokens: 2048,
  aiVoice: 'Puck',
  appTheme: 'default' as const,
  themePrimaryColor: '#8B5CF6',
  enableGlassmorphism: true,
  enableAnimations: true,
  enableGlowEffects: true,
  enableZenMode: false,
  enableParallax: true,
  themeCardBorderRadius: 1.25,
  themeBlurAmount: 40,
  themeGlowIntensity: 10,
  fontSizeScale: 1,
  hapticFeedbackEnabled: true,
  hapticIntensity: 'medium' as const,
  calorieGoalObjective: 'maintenance' as const,
  smartSleepEnabled: true,
  sleepTargetHours: 8,
  workDays: [1, 2, 3, 4, 5],
  wakeTimeWork: '07:00',
  wakeTimeOff: '09:00',
  startWeekOn: 1,
  remindersEnabled: false,
  reminderTime: '17:00',
  mealRemindersEnabled: false,
  breakfastReminderTime: '08:00',
  lunchReminderTime: '14:00',
  dinnerReminderTime: '21:00',
  missedWorkoutReminderEnabled: true,
  missedWorkoutReminderTime: '21:00',
  augeBatteryReminderEnabled: false,
  augeBatteryReminderThreshold: 20,
  augeBatteryReminderTime: '09:00',
  eventRemindersEnabled: true,
  autoSyncEnabled: false,
  tabBarStyle: 'default' as const,
  username: 'Atleta Test',
  userVitals: {
    age: 28,
    weight: 75,
    height: 175,
    gender: 'male' as const,
    activityLevel: 'moderate' as const,
  },
  homeWidgetOrder: [],
  homeCardOrder: [],
} as any;

describe('SettingsScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useNavigation as jest.Mock).mockReturnValue(getNavigationMock());
    mockNavigate.mockClear();

    act(() => {
      useSettingsStore.setState({
        status: 'ready',
        summary: defaultSettingsSummary,
        notice: null,
      });
      useWellbeingStore.setState({
        status: 'idle',
        overview: null,
        source: null,
        droppedDailyLogs: 0,
      } as any);
      useCutoverStore.setState({
        stage: 'not-started',
        systemChecklist: null,
        manualSignoff: {},
        operationalSnapshot: null,
        lastCheckedAt: null,
        notice: null,
        refresh: jest.fn(),
        runOperationalSweep: jest.fn(),
        toggleManualSignoff: jest.fn(),
        clearNotice: jest.fn(),
      } as any);
    });
  });

  it('renders the screen title and subtitle', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <SettingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Ajustes');
    expect(json).toContain('Personaliza tu experiencia');
  });

  it('renders all public sections in order', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <SettingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Perfil');
    expect(json).toContain('Apariencia');
    expect(json).toContain('Navegación');
    expect(json).toContain('Recordatorios');
    expect(json).toContain('Entreno');
    expect(json).toContain('Preferencias');
    expect(json).toContain('Acciones');
  });

  it('shows profile data from settings summary', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <SettingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Atleta Test');
    expect(json).toContain('28 años');
    expect(json).toContain('75 kg');
    expect(json).toContain('175 cm');
  });

  it('renders toggle switches for reminders', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <SettingsScreen />
        </ThemeProvider>,
      );
    });

    expect(tree!.root.findByProps({ testID: 'toggle-workout-reminders' })).toBeTruthy();
    expect(tree!.root.findByProps({ testID: 'toggle-meal-reminders' })).toBeTruthy();
    expect(tree!.root.findByProps({ testID: 'toggle-fallback-ai' })).toBeTruthy();
  });

  it('shows Desactivado for disabled reminders', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <SettingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Desactivado');
  });

  it('renders internal sections when __DEV__ is true', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <SettingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Estado interno');
    expect(json).toContain('Cutover Android (interno)');
  });

  it('renders subtitle from PWA reference copy', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <SettingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Personaliza tu experiencia');
  });

  it('renders theme choice chips', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <SettingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Predeterminado');
    expect(json).toContain('Oscuro');
    expect(json).toContain('Claro');
    expect(json).toContain('Deep Black');
    expect(json).toContain('Volt');
  });
});
