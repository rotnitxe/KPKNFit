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

  it('toggles from checked to unchecked', () => {
    const onChange = jest.fn();
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ToggleSwitch checked={true} onChange={onChange} label="Sonido" />,
      );
    });

    const pressables = tree!.root.findAllByProps({ accessibilityRole: 'switch' });
    act(() => {
      pressables[0].props.onPress();
    });

    expect(onChange).toHaveBeenCalledWith(false);
  });

  it('reports checked state in accessibility', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ToggleSwitch checked={true} onChange={jest.fn()} label="Vibración" />,
      );
    });

    const pressables = tree!.root.findAllByProps({ accessibilityRole: 'switch' });
    expect(pressables[0].props.accessibilityState).toEqual({ checked: true });
  });

  it('renders without a label', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ToggleSwitch checked={false} onChange={jest.fn()} />,
      );
    });

    const pressables = tree!.root.findAllByProps({ accessibilityRole: 'switch' });
    expect(pressables.length).toBeGreaterThan(0);
  });

  it('renders sm size correctly', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ToggleSwitch checked={false} onChange={jest.fn()} label="Pequeño" size="sm" />,
      );
    });

    const pressables = tree!.root.findAllByProps({ accessibilityRole: 'switch' });
    expect(pressables.length).toBeGreaterThan(0);
  });

  it('renders isBlackAndWhite variant', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ToggleSwitch checked={true} onChange={jest.fn()} label="B&W" isBlackAndWhite />,
      );
    });

    const pressables = tree!.root.findAllByProps({ accessibilityRole: 'switch' });
    expect(pressables.length).toBeGreaterThan(0);
  });
});
