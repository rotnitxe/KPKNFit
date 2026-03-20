jest.mock('../../theme', () => ({
  useColors: () => ({
    secondaryContainer: '#EDE9FE',
    secondary: '#7C3AED',
    onSecondaryContainer: '#111827',
    onSurfaceVariant: '#6B7280',
    onSurface: '#111827',
    primary: '#7C3AED',
    primaryContainer: '#DDD6FE',
    surfaceContainer: '#F3F4F6',
    surface: '#FFFFFF',
    outlineVariant: '#D1D5DB',
  }),
  useTheme: () => ({
    isDark: false,
    toggleDark: jest.fn(),
  }),
}));

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, right: 0, bottom: 0, left: 0 }),
}));

import React from 'react';
import renderer, { act } from 'react-test-renderer';
import { KpknBottomBar } from '../../components/navigation/KpknBottomBar';
import { useSettingsStore } from '../../stores/settingsStore';

const baseState = {
  index: 2,
  routes: [
    { key: 'rings', name: 'Rings' },
    { key: 'workout', name: 'Workout' },
    { key: 'home', name: 'Home' },
    { key: 'nutrition', name: 'Nutrition' },
    { key: 'profile', name: 'Profile' },
    { key: 'wiki', name: 'Wiki' },
    { key: 'settings', name: 'Settings' },
  ],
  routeNames: ['Rings', 'Workout', 'Home', 'Nutrition', 'Profile', 'Wiki', 'Settings'],
  history: undefined,
  type: 'tab',
  stale: false,
  key: 'root',
} as any;

function renderBar(overrides: Record<string, any> = {}) {
  const navigate = jest.fn();
  const emit = jest.fn(() => ({ defaultPrevented: false }));
  let tree: renderer.ReactTestRenderer;
  act(() => {
    tree = renderer.create(
      <KpknBottomBar
        state={baseState}
        descriptors={{} as any}
        insets={{ top: 0, right: 0, bottom: 0, left: 0 }}
        navigation={{ emit, navigate } as any}
      />,
    );
  });
  return { tree: tree!, navigate, emit };
}

describe('KpknBottomBar', () => {
  beforeEach(() => {
    act(() => {
      useSettingsStore.setState({
        status: 'ready',
        summary: {
          tabBarStyle: 'default',
        } as any,
      } as any);
    });
  });

  it('renders accessible tab buttons for visible routes', () => {
    const { tree } = renderBar();
    const buttons = tree.root
      .findAllByProps({ accessibilityRole: 'button' })
      .map(node => node.props.accessibilityLabel)
      .filter(Boolean);
    expect(buttons).toEqual(
      expect.arrayContaining([
        'Entrenar',
        'Nutrición',
        'Inicio',
        'Mi Perfil',
        'WikiLab',
      ]),
    );
  });

  it('renders testIDs for all nav buttons', () => {
    const { tree } = renderBar();
    const testIDs = tree.root
      .findAllByProps({ accessibilityRole: 'button' })
      .map(node => node.props.testID)
      .filter(Boolean);
    expect(testIDs).toEqual(
      expect.arrayContaining([
        'nav-workout',
        'nav-home',
        'nav-nutrition',
        'nav-profile',
        'nav-wiki',
      ]),
    );
  });

  it('marks the active route as selected', () => {
    const { tree } = renderBar();
    const homeButton = tree.root.findByProps({ testID: 'nav-home' });
    expect(homeButton.props.accessibilityState.selected).toBe(true);
  });
});
