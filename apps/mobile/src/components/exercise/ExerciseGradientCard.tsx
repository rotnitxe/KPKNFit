/**
 * components/exercise/ExerciseGradientCard.tsx
 * 
 * Tarjeta de ejercicio con degradado de grupo muscular y brillo al hacer swipe:
 * - Gradiente dinámico según grupo muscular primario
 * - Edge glow que se intensifica al pasar el dedo
 * - Animaciones de entrada con spring
 * - Icono de favorito animado
 */

import React, { useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withTiming,
  withDelay,
  interpolate,
  Extrapolation,
  runOnJS,
} from 'react-native-reanimated';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { Canvas, Rect, LinearGradient, RadialGradient, vec, Circle, Path } from '@shopify/react-native-skia';
import { useColors } from '../../theme';
import type { ExerciseMuscleInfo } from '../../types/workout';
import { ActivityIcon, StarIcon } from '../icons';

interface ExerciseGradientCardProps {
  exercise: ExerciseMuscleInfo;
  onPress: (exercise: ExerciseMuscleInfo) => void;
  isFavorite?: boolean;
  onToggleFavorite?: (exercise: ExerciseMuscleInfo) => void;
  index?: number;
}

/**
 * Mapeo de grupos musculares a colores de gradiente
 */
const MUSCLE_GRADIENTS: Record<string, string[]> = {
  'Pecho': ['#FF6B6B', '#FF8E8E'],
  'Espalda': ['#4ECDC4', '#7EDDD6'],
  'Hombros': ['#FFE66D', '#FFF0A0'],
  'Cuádriceps': ['#95E1D3', '#B5EAD7'],
  'Isquios': ['#F38181', '#F7A8A8'],
  'Glúteos': ['#FF9A8B', '#FFB5A8'],
  'Bíceps': ['#A8E6CF', '#C4EED7'],
  'Tríceps': ['#FFD3B6', '#FFE0C7'],
  'Core': ['#DCEDC8', '#E8F5C8'],
  'Pantorrillas': ['#FFAAA5', '#FFBDB8'],
};

const DEFAULT_GRADIENT = ['#D4FC79', '#96E6A1'];

/**
 * ExerciseGradientCard
 * Tarjeta con gradiente muscular y edge glow interactivo
 */
export const ExerciseGradientCard: React.FC<ExerciseGradientCardProps> = ({
  exercise,
  onPress,
  isFavorite = false,
  onToggleFavorite,
  index = 0,
}) => {
  const colors = useColors();
  
  // Animation values
  const scaleAnim = useSharedValue(1);
  const glowOpacity = useSharedValue(0);
  const glowPosition = useSharedValue(0.5);
  const favoriteScale = useSharedValue(isFavorite ? 1 : 0);
  const enterAnim = useSharedValue(0);

  // Get primary muscle for gradient
  const primaryMuscle = useMemo(() => {
    const primary = exercise.involvedMuscles.find(m => m.role === 'primary');
    return primary?.muscle?.toString() || exercise.name;
  }, [exercise]);

  // Get gradient colors for this muscle
  const gradientColors = useMemo(() => {
    for (const [muscle, gradient] of Object.entries(MUSCLE_GRADIENTS)) {
      if (primaryMuscle.includes(muscle)) {
        return gradient;
      }
    }
    return DEFAULT_GRADIENT;
  }, [primaryMuscle]);

  // Entry animation
  React.useEffect(() => {
    enterAnim.value = withDelay(
      index * 50,
      withSpring(1, {
        damping: 20,
        stiffness: 150,
      })
    );
  }, [index, enterAnim]);

  // Favorite animation
  React.useEffect(() => {
    favoriteScale.value = withSpring(isFavorite ? 1 : 0, {
      damping: 15,
      stiffness: 300,
    });
  }, [isFavorite, favoriteScale]);

  // Gesture for swipe glow
  const gesture = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .onStart(() => {
      glowOpacity.value = withSpring(1, { damping: 15 });
    })
    .onUpdate((event) => {
      glowPosition.value = interpolate(
        event.translationX,
        [-100, 0, 100],
        [0, 0.5, 1],
        Extrapolation.CLAMP
      );
    })
    .onEnd(() => {
      glowOpacity.value = withTiming(0, { duration: 300 });
      glowPosition.value = withTiming(0.5, { duration: 300 });
    });

  // Tap gesture
  const tapGesture = Gesture.Tap()
    .onStart(() => {
      scaleAnim.value = withSpring(0.96, { damping: 20 });
    })
    .onEnd(() => {
      scaleAnim.value = withSpring(1, { damping: 15 });
      runOnJS(onPress)(exercise);
    });

  const composedGesture = Gesture.Simultaneous(gesture, tapGesture);

  // Animated styles
  const cardStyle = useAnimatedStyle(() => ({
    transform: [
      { scale: interpolate(enterAnim.value, [0, 1], [0.9, 1]) },
      { scale: scaleAnim.value },
    ],
    opacity: enterAnim.value,
  }));

  const glowStyle = useAnimatedStyle(() => ({
    opacity: glowOpacity.value * 0.6,
  }));

  const favoriteIconStyle = useAnimatedStyle(() => ({
    transform: [{ scale: favoriteScale.value }],
    opacity: favoriteScale.value,
  }));

  const handleFavoritePress = useCallback(() => {
    onToggleFavorite?.(exercise);
  }, [onToggleFavorite, exercise]);

  return (
    <GestureDetector gesture={composedGesture}>
      <Animated.View style={[styles.container, cardStyle]}>
        {/* Gradient Background */}
        <View style={styles.gradientBackground}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Rect x={0} y={0} width={1000} height={1000}>
              <LinearGradient
                start={vec(0, 0)}
                end={vec(300, 300)}
                colors={gradientColors}
              />
            </Rect>
            
            {/* Subtle pattern overlay */}
            <Rect x={0} y={0} width={1000} height={1000}>
              <LinearGradient
                start={vec(0, 0)}
                end={vec(0, 1000)}
                colors={['rgba(255,255,255,0.1)', 'transparent']}
              />
            </Rect>
          </Canvas>
        </View>

        {/* Edge Glow Effect */}
        <Animated.View
          style={[
            styles.edgeGlow,
            glowStyle,
            {
              left: `${glowPosition.value * 100}%`,
              backgroundColor: gradientColors[0],
            },
          ]}
        />

        {/* Content */}
        <View style={styles.content}>
          {/* Icon */}
          <View style={[styles.iconContainer, { backgroundColor: `${colors.surface}80` }]}>
            <ActivityIcon size={24} color={colors.primary} />
          </View>

          {/* Info */}
          <View style={styles.info}>
            <Text style={[styles.name, { color: colors.onSurface }]} numberOfLines={1}>
              {exercise.name}
            </Text>
            <View style={styles.metaRow}>
              <Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>
                {exercise.type}
              </Text>
              <Text style={[styles.metaDot, { color: colors.onSurfaceVariant }]}>
                ·
              </Text>
              <Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>
                {exercise.equipment}
              </Text>
            </View>
          </View>

          {/* Favorite Button */}
          <Pressable onPress={handleFavoritePress} style={styles.favoriteButton}>
            <Animated.View style={favoriteIconStyle}>
              <StarIcon size={20} color="#FFD700" fill="#FFD700" />
            </Animated.View>
          </Pressable>
        </View>

        {/* Muscle indicator bar */}
        <View
          style={[
            styles.muscleBar,
            { backgroundColor: gradientColors[0] },
          ]}
        />
      </Animated.View>
    </GestureDetector>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 20,
    overflow: 'hidden',
    marginHorizontal: 20,
    marginVertical: 6,
    height: 80,
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 12,
    elevation: 5,
  },
  gradientBackground: {
    ...StyleSheet.absoluteFillObject,
    opacity: 0.15,
  },
  edgeGlow: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: 60,
    marginLeft: -30,
    borderRadius: 30,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    opacity: 0,
  },
  content: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  iconContainer: {
    width: 44,
    height: 44,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  info: {
    flex: 1,
  },
  name: {
    fontSize: 16,
    fontWeight: '700',
    letterSpacing: -0.3,
    marginBottom: 4,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  meta: {
    fontSize: 12,
    fontWeight: '500',
  },
  metaDot: {
    fontSize: 12,
    fontWeight: '500',
  },
  favoriteButton: {
    padding: 8,
    marginRight: -8,
  },
  muscleBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 3,
    opacity: 0.8,
  },
});
