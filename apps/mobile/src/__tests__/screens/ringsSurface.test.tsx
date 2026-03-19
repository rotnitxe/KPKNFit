// Rings surface test — verifies main UI blocks render correctly

jest.mock('@react-navigation/native', () => {
  const actual = jest.requireActual('@react-navigation/native');
  return {
    ...actual,
    useNavigation: jest.fn(),
  };
});

jest.mock('../../services/mobileDomainStateService', () => ({
  readStoredWellbeingPayload: jest.fn(() => ({
    sleepLogs: [],
    dailyWellbeingLogs: [],
  })),
  readStoredSettingsRaw: jest.fn(() => ({})),
  getStoredSettingsSource: jest.fn(() => 'rn-owned'),
  patchStoredSettingsRaw: jest.fn(),
}));

jest.mock('../../services/ringsMetrics', () => ({
  calculateRingsMetrics: jest.fn(() => ({
    avgSleepHours: null,
    avgStressLevel: null,
    avgEnergyLevel: null,
  })),
}));

import React from 'react';
import renderer, { act } from 'react-test-renderer';
import { ThemeProvider } from '../../theme';
import { useNavigation } from '@react-navigation/native';
import { RingsScreen } from '../../screens/Rings/RingsScreen';
import { useAugeRuntimeStore } from '../../stores/augeRuntimeStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useMobileNutritionStore } from '../../stores/nutritionStore';

const mockNavigate = jest.fn();

function getNavigationMock() {
  return {
    navigate: mockNavigate,
    goBack: jest.fn(),
    canGoBack: jest.fn(() => true),
    emit: jest.fn(() => ({ defaultPrevented: false })),
  } as any;
}

describe('RingsScreen surface render', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useNavigation as jest.Mock).mockReturnValue(getNavigationMock());

    act(() => {
      useAugeRuntimeStore.setState({
        status: 'ready',
        snapshot: {
          readinessStatus: 'green',
          cnsBattery: 72,
          stressMultiplier: 1.05,
          recommendation: 'Buen momento para entrenar.',
          batteries: { muscular: 85, cns: 72, spinal: 90 },
          lastComputedAt: new Date().toISOString(),
        },
        isRefreshing: false,
        hydrateFromStorage: jest.fn(),
        recompute: jest.fn(),
      } as any);

      useWellbeingStore.setState({
        status: 'ready',
        overview: { averageSleepHoursLast7Days: 7.2 },
      } as any);

      useWorkoutStore.setState({
        status: 'ready',
        overview: {
          battery: { overall: 78, cns: 72, muscular: 85, source: 'wellbeing-derived' },
        },
      } as any);

      useMobileNutritionStore.setState({
        status: 'ready',
      } as any);
    });
  });

  it('renders header, subtitle, and PWA-aligned copy', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <RingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Mis Rings');
    expect(json).toContain('Biometría en tiempo real');
    expect(json).toContain('Monitoreo de recuperación y estrés');
  });

  it('renders the segmented toggle with both modes', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <RingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Combinado');
    expect(json).toContain('Individual');
  });

  it('renders stat cards with PWA-aligned labels', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <RingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Sueño prom.');
    expect(json).toContain('Estrés prom.');
    expect(json).toContain('Energía prom.');
  });

  it('renders info cards with PWA-matching titles', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <RingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Sistema Muscular');
    expect(json).toContain('Sistema Nervioso Central');
    expect(json).toContain('Columna y Articulaciones');
  });

  it('renders the Google Health placeholder with disabled label', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <RingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Google Health Connect');
    expect(json).toContain('Próximamente');
  });

  it('renders info card footers aligned with PWA', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <RingsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Se recupera en 24-72 horas pos-esfuerzo');
    expect(json).toContain('Depende directamente de la calidad del sueño');
    expect(json).toContain('Impacto acumulativo de cargas pesadas');
  });
});
