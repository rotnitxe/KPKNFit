// Profile surface test - verifies main UI blocks render correctly

jest.mock('@react-navigation/native', () => {
  const actual = jest.requireActual('@react-navigation/native');
  return {
    ...actual,
    useNavigation: jest.fn(),
  };
});

jest.mock('../../services/mobileDomainStateService', () => ({
  readStoredSettingsRaw: jest.fn(() => ({})),
}));

import React from 'react';
import renderer, { act } from 'react-test-renderer';
import { ThemeProvider } from '../../theme';
import { useNavigation } from '@react-navigation/native';
import { ProfileScreen } from '../../screens/Profile/ProfileScreen';
import { useSettingsStore } from '../../stores/settingsStore';
import { useBodyStore } from '../../stores/bodyStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';

const mockNavigate = jest.fn();

function getNavigationMock() {
  return {
    navigate: mockNavigate,
    goBack: jest.fn(),
    canGoBack: jest.fn(() => true),
    emit: jest.fn(() => ({ defaultPrevented: false })),
  } as any;
}

describe('ProfileScreen surface render', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useNavigation as jest.Mock).mockReturnValue(getNavigationMock());
    mockNavigate.mockClear();

    // Settings store – same default used in other tests
    useSettingsStore.setState({
      status: 'ready',
      summary: {
        username: 'Atleta Test',
        userVitals: { weight: 80, height: 180, bodyFatPercentage: 12 },
        athleteType: 'Powerlifter',
        weightUnit: 'kg',
      } as any,
      notice: null,
    } as any);

    // Body store – provide a single progress entry
    useBodyStore.setState({
      status: 'ready',
      bodyProgress: [{ weight: 80, bodyFatPercentage: 12 }],
      bodyLabAnalysis: { profileSummary: undefined },
    } as any);

    // Wellbeing store – simple overview
    useWellbeingStore.setState({
      status: 'ready',
      overview: { averageSleepHoursLast7Days: 7 },
    } as any);
  });

  it('renders header, quick summary and status sections', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <ProfileScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    // Main label for profile
    expect(json).toContain('Perfil de Atleta');
    // Quick stats section title
    expect(json).toContain('Resumen rápido');
    // Current status section title
    expect(json).toContain('Estado actual');
    // Navigation button label should be present
    expect(json).toContain('Records Personales');
  });
});
