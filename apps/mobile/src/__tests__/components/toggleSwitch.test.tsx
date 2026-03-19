jest.mock('../../theme', () => ({
  useColors: () => ({
    primary: '#7C3AED',
    onPrimary: '#FFFFFF',
    surfaceVariant: '#E5E7EB',
    surface: '#FFFFFF',
    onSurface: '#111827',
    onSurfaceVariant: '#6B7280',
  }),
}));

import React from 'react';
import renderer, { act } from 'react-test-renderer';
import { ToggleSwitch } from '../../components/ui';

describe('ToggleSwitch', () => {
  it('renders a single pressable surface and toggles once per tap', () => {
    const onChange = jest.fn();
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ToggleSwitch checked={false} onChange={onChange} label="Notificación" />,
      );
    });

    const pressables = tree!.root.findAllByProps({ accessibilityRole: 'switch' });
    expect(pressables.length).toBeGreaterThan(0);

    act(() => {
      pressables[0].props.onPress();
    });

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(true);
  });
});
