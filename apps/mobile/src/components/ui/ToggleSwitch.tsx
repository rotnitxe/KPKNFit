import React, { useEffect, useRef } from 'react';
import { Pressable, Text, StyleSheet, Animated, Easing, View } from 'react-native';
import { useColors } from '../../theme';

interface ToggleSwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label?: string;
  size?: 'sm' | 'md';
  isBlackAndWhite?: boolean;
  testID?: string;
}

export function ToggleSwitch({
  checked,
  onChange,
  label,
  size = 'md',
  isBlackAndWhite = false,
  testID,
}: ToggleSwitchProps) {
  const colors = useColors();
  const knobPosition = useRef(new Animated.Value(checked ? 1 : 0)).current;

  const dimensions = size === 'sm'
    ? { track: { width: 36, height: 20 }, knob: { size: 16 }, translateX: 16 }
    : { track: { width: 44, height: 24 }, knob: { size: 20 }, translateX: 20 };

  useEffect(() => {
    Animated.timing(knobPosition, {
      toValue: checked ? 1 : 0,
      duration: 180,
      easing: Easing.inOut(Easing.ease),
      useNativeDriver: true,
    }).start();
  }, [checked, knobPosition]);

  const handlePress = () => {
    const newChecked = !checked;
    onChange(newChecked);

    Animated.timing(knobPosition, {
      toValue: newChecked ? 1 : 0,
      duration: 200,
      easing: Easing.inOut(Easing.ease),
      useNativeDriver: true,
    }).start();
  };

  const trackColor = checked
    ? (isBlackAndWhite ? colors.onSurface : colors.primary)
    : colors.surfaceVariant;

  const knobColor = isBlackAndWhite && checked ? colors.surface : colors.onPrimary;

  return (
    <Pressable
      style={styles.container}
      onPress={handlePress}
      accessibilityRole="switch"
      accessibilityState={{ checked }}
      testID={testID}
    >
      {label && (
        <Text
          style={[
            styles.label,
            {
              color: colors.onSurfaceVariant,
              fontSize: size === 'sm' ? 12 : 14,
            },
          ]}
        >
          {label}
        </Text>
      )}
      <View
        style={[
          styles.track,
          {
            width: dimensions.track.width,
            height: dimensions.track.height,
            backgroundColor: trackColor,
          },
        ]}
      >
        <Animated.View
          style={[
            styles.knob,
            {
              width: dimensions.knob.size,
              height: dimensions.knob.size,
              backgroundColor: knobColor,
              shadowColor: colors.onSurface,
              transform: [{
                translateX: knobPosition.interpolate({
                  inputRange: [0, 1],
                  outputRange: [0, dimensions.translateX],
                }),
              }],
            },
          ]}
        />
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  label: {
    fontWeight: '500',
  },
  track: {
    borderRadius: 9999,
    justifyContent: 'center',
    paddingHorizontal: 2,
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
  knob: {
    borderRadius: 9999,
    elevation: 2,
    shadowOpacity: 0.15,
    shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 },
  },
});

export default ToggleSwitch;
