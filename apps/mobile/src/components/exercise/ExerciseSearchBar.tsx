/**
 * components/exercise/ExerciseSearchBar.tsx
 * 
 * Barra de búsqueda con transición de expansión estilo 'Shared Element Transition':
 * - Expansión animada al enfocar con spring dynamics
 * - Gradiente líquido en el borde
 * - Icono de búsqueda animado
 * - Clear button con fade/scale transition
 */

import React, { useCallback, useRef } from 'react';
import { View, TextInput, TouchableOpacity, Text, StyleSheet, type ViewStyle } from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withTiming,
  interpolate,
  Extrapolation,
  runOnJS,
} from 'react-native-reanimated';
import { Canvas, Rect, LinearGradient, vec, Circle, Path, Skia } from '@shopify/react-native-skia';
import { useColors } from '@/theme';
import { SearchIcon, XCircleIcon } from '../icons';

interface ExerciseSearchBarProps {
  query: string;
  onChangeQuery: (value: string) => void;
  onClear?: () => void;
  onFocus?: () => void;
  onBlur?: () => void;
  placeholder?: string;
}

export const ExerciseSearchBar: React.FC<ExerciseSearchBarProps> = ({
  query,
  onChangeQuery,
  onClear,
  onFocus,
  onBlur,
  placeholder = 'Buscar ejercicio, músculo o equipo',
}) => {
  const colors = useColors();
  const inputRef = useRef<TextInput>(null);
  
  // Animation values
  const isFocused = useSharedValue(0);
  const expandAnim = useSharedValue(0);
  const borderGlowProgress = useSharedValue(0);
  const clearButtonScale = useSharedValue(0);

  // Handle focus
  const handleFocus = useCallback(() => {
    isFocused.value = withSpring(1, { damping: 15, stiffness: 200 });
    expandAnim.value = withSpring(1, { damping: 20, stiffness: 150 });
    borderGlowProgress.value = withTiming(1, { duration: 300 });
    onFocus?.();
  }, [isFocused, expandAnim, borderGlowProgress, onFocus]);

  // Handle blur
  const handleBlur = useCallback(() => {
    isFocused.value = withTiming(0, { duration: 200 });
    expandAnim.value = withTiming(0, { duration: 200 });
    borderGlowProgress.value = withTiming(0, { duration: 200 });
    onBlur?.();
  }, [isFocused, expandAnim, borderGlowProgress, onBlur]);

  // Handle clear
  const handleClear = useCallback(() => {
    clearButtonScale.value = withSpring(0.8, { damping: 10 });
    setTimeout(() => {
      clearButtonScale.value = withSpring(1);
    }, 100);
    onClear?.();
    onChangeQuery('');
  }, [onClear, onChangeQuery, clearButtonScale]);

  // Animated styles
  const containerStyle = useAnimatedStyle(() => {
    const scale = interpolate(expandAnim.value, [0, 1], [1, 1.02]);
    const borderRadius = interpolate(expandAnim.value, [0, 1], [16, 20]);
    
    return {
      transform: [{ scale }],
      borderRadius,
    };
  });

  const borderGlowStyle = useAnimatedStyle(() => ({
    opacity: interpolate(borderGlowProgress.value, [0, 1], [0.3, 0.8]),
  }));

  const clearButtonAnimStyle = useAnimatedStyle(() => ({
    opacity: interpolate(clearButtonScale.value, [0, 1], [0, 1]),
    transform: [{ scale: clearButtonScale.value }] as unknown as NonNullable<ViewStyle['transform']>,
  }));

  const searchIconStyle = useAnimatedStyle(() => {
    const rotate = interpolate(isFocused.value, [0, 1], [0, 15]);
    const scale = interpolate(isFocused.value, [0, 1], [1, 1.1]);
    const transform = [
      { rotate: `${rotate}deg` },
      { scale },
    ] as unknown as NonNullable<ViewStyle['transform']>;
    
    return {
      transform,
    };
  });

  const hasQuery = query.length > 0;

  return (
    <Animated.View
      style={[
        styles.container,
        containerStyle,
        {
          backgroundColor: `${colors.surface}E6`,
          borderColor: `${colors.outlineVariant}60`,
        },
      ]}
    >
      {/* Liquid Border Effect */}
      <View style={styles.borderContainer}>
        <Animated.View
          style={[
            styles.borderGlow,
            borderGlowStyle,
            { backgroundColor: colors.primary },
          ]}
        />
      </View>

      {/* Search Icon */}
      <Animated.View style={[styles.iconWrapper, searchIconStyle]}>
        <SearchIcon size={22} color={isFocused.value > 0.5 ? colors.primary : colors.onSurfaceVariant} />
      </Animated.View>

      {/* Input */}
      <TextInput
        ref={inputRef}
        style={[styles.input, { color: colors.onSurface }]}
        placeholder={placeholder}
        placeholderTextColor={`${colors.onSurface}60`}
        value={query}
        onChangeText={onChangeQuery}
        onFocus={handleFocus}
        onBlur={handleBlur}
        autoCorrect={false}
        autoCapitalize="none"
        returnKeyType="search"
      />

      {/* Clear Button */}
      {hasQuery && (
        <Animated.View style={[styles.clearWrapper, clearButtonAnimStyle]}>
          <TouchableOpacity onPress={handleClear} style={styles.clearButton} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
            <XCircleIcon size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>
        </Animated.View>
      )}

      {/* Gradient accent on focus */}
      {isFocused.value > 0 && (
        <View style={[styles.gradientAccent, { backgroundColor: `${colors.primary}15` }]} />
      )}
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1.5,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 16,
    position: 'relative',
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 12,
    elevation: 4,
  },
  borderContainer: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 20,
    overflow: 'hidden',
  },
  borderGlow: {
    ...StyleSheet.absoluteFillObject,
    borderWidth: 2,
    borderRadius: 20,
    opacity: 0,
  },
  iconWrapper: {
    marginRight: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  input: {
    flex: 1,
    fontSize: 16,
    fontWeight: '500',
    paddingVertical: 0,
  },
  clearWrapper: {
    marginLeft: 8,
  },
  clearButton: {
    padding: 4,
  },
  gradientAccent: {
    ...StyleSheet.absoluteFillObject,
    opacity: 0.5,
  },
});
