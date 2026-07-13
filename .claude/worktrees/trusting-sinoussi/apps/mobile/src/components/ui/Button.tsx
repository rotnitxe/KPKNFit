import React from 'react';
import {
  TouchableOpacity,
  Text,
  ActivityIndicator,
  StyleSheet,
  View,
} from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useColors } from '../../theme';

interface ButtonProps {
  children: React.ReactNode;
  onPress?: () => void;
  variant?: 'primary' | 'secondary' | 'danger';
  isLoading?: boolean;
  disabled?: boolean;
  style?: any;
  testID?: string;
}

const AnimatedPressable = Animated.createAnimatedComponent(TouchableOpacity);

export function Button({
  children,
  onPress,
  variant = 'primary',
  isLoading = false,
  disabled = false,
  style,
  testID,
}: ButtonProps) {
  const colors = useColors();
  const scale = useSharedValue(1);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const handlePressIn = () => {
    scale.value = withSpring(0.95);
  };

  const handlePressOut = () => {
    scale.value = withSpring(1);
  };

  const getVariantStyles = () => {
    switch (variant) {
      case 'primary':
        return {
          container: { backgroundColor: colors.onPrimary },
          text: { color: colors.primary },
          loader: colors.primary,
        };
      case 'secondary':
        return {
          container: { backgroundColor: `${colors.onSurface}1A` },
          text: { color: colors.onSurface },
          loader: colors.onSurface,
        };
      case 'danger':
        return {
          container: { backgroundColor: colors.error },
          text: { color: colors.onError },
          loader: colors.onError,
        };
      default:
        return {
          container: { backgroundColor: colors.onPrimary },
          text: { color: colors.primary },
          loader: colors.primary,
        };
    }
  };

  const v = getVariantStyles();

  return (
    <AnimatedPressable
      testID={testID}
      onPress={onPress}
      onPressIn={handlePressIn}
      onPressOut={handlePressOut}
      disabled={disabled || isLoading}
      activeOpacity={0.8}
      style={[
        styles.button,
        v.container,
        animatedStyle,
        disabled && styles.disabled,
        style,
      ]}
    >
      {isLoading ? (
        <ActivityIndicator color={v.loader} size="small" />
      ) : typeof children === 'string' ? (
        <Text style={[styles.text, v.text]}>{children}</Text>
      ) : (
        children
      )}
    </AnimatedPressable>
  );
}

const styles = StyleSheet.create({
  button: {
    height: 48,
    paddingHorizontal: 24,
    borderRadius: 0, // Sharp corners as per spec
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 120,
  },
  text: {
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
  },
  disabled: {
    opacity: 0.5,
  },
});

export default Button;
