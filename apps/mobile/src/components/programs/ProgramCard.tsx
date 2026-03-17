import React, { useEffect, memo } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import Animated, { useSharedValue, withRepeat, withSequence, withTiming, useAnimatedStyle } from 'react-native-reanimated';
import { useColors } from '../../theme';
import type { Program } from '../../types/workout';

interface ProgramCardProps {
  program: Program;
  stats: { weeks: number; sessions: number };
  variant: 'active' | 'inactive';
  onPress: () => void;
}

export const ProgramCard = memo(({ program, stats, variant, onPress }: ProgramCardProps) => {
  const colors = useColors();
  const pulseOpacity = useSharedValue(1);

  useEffect(() => {
    if (variant === 'active') {
      pulseOpacity.value = withRepeat(
        withSequence(
          withTiming(0.3, { duration: 800 }),
          withTiming(1, { duration: 800 }),
        ),
        -1,
        false,
      );
    }
  }, [variant]);

  const animatedDotStyle = useAnimatedStyle(() => ({
    opacity: pulseOpacity.value,
  }));

  const isActive = variant === 'active';

  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        { 
          backgroundColor: colors.surfaceContainer, 
          borderColor: isActive ? colors.primary : colors.outlineVariant,
          borderWidth: isActive ? 2 : 1,
          opacity: pressed ? 0.7 : 1,
        }
      ]}
    >
      <View style={styles.contentRow}>
        <View style={[styles.iconContainer, { backgroundColor: isActive ? colors.primary : colors.surfaceContainerHigh }]}>
          <Text style={[styles.icon, { color: isActive ? colors.onPrimary : colors.onSurfaceVariant }]}>
            {'\u{1F3CB}'}
          </Text>
        </View>

        <View style={styles.textContainer}>
          {isActive && (
            <View style={styles.activeRow}>
              <Animated.View style={[animatedDotStyle, styles.dot, { backgroundColor: colors.cyberSuccess }]} />
              <Text style={[styles.activeLabel, { color: colors.primary }]}>
                EJECUTANDO AHORA
              </Text>
            </View>
          )}
          <Text style={[styles.name, { color: colors.onSurface }]}>{program.name}</Text>
          <Text style={[styles.stats, { color: colors.onSurfaceVariant }]}>
            {stats.weeks} semanas · {stats.sessions} sesiones
          </Text>
        </View>

        <View style={[styles.actionIcon, { backgroundColor: colors.surfaceContainerHigh }]}>
          <Text style={{ color: colors.onSurface }}>
            {isActive ? '\u{25B6}' : '>'}
          </Text>
        </View>
      </View>
    </Pressable>
  );
});

const styles = StyleSheet.create({
  card: {
    marginBottom: 16,
    borderRadius: 24,
    padding: 16,
  },
  contentRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  iconContainer: {
    height: 56,
    width: 56,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 28,
  },
  icon: {
    fontSize: 24,
  },
  textContainer: {
    flex: 1,
  },
  activeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 4,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  activeLabel: {
    fontSize: 10,
    fontWeight: 'bold',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
  },
  name: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  stats: {
    marginTop: 4,
    fontSize: 12,
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  actionIcon: {
    height: 40,
    width: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 20,
  },
});

export default ProgramCard;
