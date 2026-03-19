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
}));

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, right: 0, bottom: 0, left: 0 }),
}));

import React from 'react';
import { Text } from 'react-native';
import renderer, { act } from 'react-test-renderer';
import { KpknBottomBar } from '../../components/navigation/KpknBottomBar';
import { useSettingsStore } from '../../stores/settingsStore';

const baseState = {
  index: 0,
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

function collectTextLabels(tree: renderer.ReactTestRenderer) {
  return tree.root
    .findAllByType(Text)
    .flatMap(node => {
      const children = node.props.children;
      if (Array.isArray(children)) {
        return children.filter((child): child is string => typeof child === 'string');
      }
      return typeof children === 'string' ? [children] : [];
    });
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

  it('renders PWA-matching labels in default mode', () => {
    const { tree } = renderBar();
    const labels = collectTextLabels(tree);
    expect(labels).toEqual(
      expect.arrayContaining([
        'Mi RINGS',
        'Entrenar',
        'Nutrición',
        'Inicio',
        'Mi Perfil',
        'WikiLab',
        'Ajustes',
      ]),
    );
  });

  it('hides labels when icons-only style is enabled', () => {
    act(() => {
      useSettingsStore.setState({
        status: 'ready',
        summary: { tabBarStyle: 'icons-only' } as any,
      } as any);
    });

    const { tree } = renderBar();
    const labels = collectTextLabels(tree);
    expect(labels).not.toEqual(
      expect.arrayContaining([
        'Mi RINGS',
        'Entrenar',
        'Nutrición',
        'Mi Perfil',
        'WikiLab',
        'Ajustes',
        'Inicio',
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
        'nav-rings',
        'nav-workout',
        'nav-home',
        'nav-nutrition',
        'nav-profile',
        'nav-wiki',
        'nav-settings',
      ]),
    );
  });
});
