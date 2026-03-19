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

import React from 'react';
import { Text } from 'react-native';
import renderer, { act } from 'react-test-renderer';
import { KpknBottomBar } from '../../components/navigation/KpknBottomBar';
import { useSettingsStore } from '../../stores/settingsStore';

describe('KpknBottomBar', () => {
  beforeEach(() => {
    useSettingsStore.setState({
      status: 'ready',
      summary: {
        tabBarStyle: 'icons-only',
      } as any,
    } as any);
  });

  it('hides labels when icons-only style is enabled', () => {
    const navigate = jest.fn();
    const emit = jest.fn(() => ({ defaultPrevented: false }));
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <KpknBottomBar
          state={{
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
          } as any}
          descriptors={{} as any}
          insets={{ top: 0, right: 0, bottom: 0, left: 0 }}
          navigation={{
            emit,
            navigate,
          } as any}
        />,
      );
    });

    const labels = tree!.root
      .findAllByType(Text)
      .flatMap(node => {
        const children = node.props.children;
        if (Array.isArray(children)) {
          return children.filter((child): child is string => typeof child === 'string');
        }
        return typeof children === 'string' ? [children] : [];
      });

    expect(labels).not.toEqual(
      expect.arrayContaining(['RINGS', 'ENTRENAR', 'NUTRICION', 'PERFIL', 'WIKILAB', 'AJUSTES', 'INICIO']),
    );
  });
});
