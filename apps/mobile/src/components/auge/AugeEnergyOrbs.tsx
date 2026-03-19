/**
 * components/auge/AugeEnergyOrbs.tsx
 * 
 * Visualización de telemetría AUGE como orbes de energía con micro-oscilaciones:
 * - Orbes animados que pulsan según el nivel de fatiga
 * - Micro-oscilaciones que reflejan estrés sistémico en tiempo real
 * - Gradientes radiales con efecto de profundidad
 * - Partículas flotantes para feedback visual
 */

import React, { useEffect, useMemo } from 'react';
import { View, Text, StyleSheet, Dimensions, type ViewStyle } from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withRepeat,
  withTiming,
  withSpring,
  interpolate,
  Extrapolation,
  runOnJS,
} from 'react-native-reanimated';
import {
  Canvas,
  Circle,
  RadialGradient,
  vec,
  Group,
  BlurMask,
  Path,
  Skia,
  LinearGradient,
  Rect,
} from '@shopify/react-native-skia';
import { useColors } from '../../theme';
import { ZapIcon, FlameIcon, ActivityIcon } from '../icons';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface AugeEnergyOrbsProps {
  cns: number;
  muscular: number;
  spinal: number;
  liveMode?: boolean;
}

interface EnergyOrbProps {
  value: number;
  color: string;
  label: string;
  icon: React.ComponentType<{ size?: number; color?: string }>;
  index: number;
  liveMode?: boolean;
}

/**
 * EnergyOrb
 * Orbe individual con animaciones de pulsación y micro-oscilaciones
 */
const EnergyOrb: React.FC<EnergyOrbProps> = React.memo(({ value, color, label, icon: Icon, index, liveMode }) => {
  const colors = useColors();
  
  // Animation values
  const pulseAnim = useSharedValue(0);
  const oscillationX = useSharedValue(0);
  const oscillationY = useSharedValue(0);
  const glowIntensity = useSharedValue(0);
  const particlesRotation = useSharedValue(0);

  // Safe value clamping
  const safeValue = useMemo(() => {
    const v = Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 50;
    return v;
  }, [value]);

  // Pulse animation based on value
  useEffect(() => {
    // Base pulse - slower for higher values (more fatigued = slower recovery)
    const pulseDuration = interpolate(safeValue, [0, 100], [2000, 4000]);
    pulseAnim.value = withRepeat(
      withTiming(1, { duration: pulseDuration }),
      -1,
      true
    );

    // Glow intensity based on value
    glowIntensity.value = withTiming(safeValue / 100, { duration: 500 });
  }, [safeValue, pulseAnim, glowIntensity]);

  // Micro-oscillations (simulating energy fluctuations)
  useEffect(() => {
    if (!liveMode) return;

    // Subtle X oscillation
    oscillationX.value = withRepeat(
      withTiming(Math.sin(index) * 3, { duration: 1500 + index * 200 }),
      -1,
      true
    );

    // Subtle Y oscillation  
    oscillationY.value = withRepeat(
      withTiming(Math.cos(index) * 2, { duration: 1800 + index * 300 }),
      -1,
      true
    );

    // Particle rotation
    particlesRotation.value = withRepeat(
      withTiming(360, { duration: 8000 + index * 1000 }),
      -1,
      false
    );
  }, [liveMode, index, oscillationX, oscillationY, particlesRotation]);

  const orbStyle = useAnimatedStyle(() => {
    const scale = interpolate(pulseAnim.value, [0, 1], [0.95, 1.05]);
    const transform = [
      { scale },
      { translateX: oscillationX.value },
      { translateY: oscillationY.value },
    ] as unknown as NonNullable<ViewStyle['transform']>;
    return {
      transform,
    };
  });

  const glowStyle = useAnimatedStyle(() => ({
    opacity: interpolate(glowIntensity.value, [0, 1], [0.2, 0.6]),
    transform: [{ scale: interpolate(glowIntensity.value, [0, 1], [0.8, 1.2]) }],
  }));

  const particlesStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${particlesRotation.value}deg` }],
  }));

  const orbSize = 80;
  const orbCenter = orbSize / 2;

  return (
    <View style={styles.orbContainer}>
      {/* Label */}
      <View style={styles.orbLabelContainer}>
        <Icon size={14} color={color} />
        <Text style={[styles.orbLabel, { color: colors.onSurfaceVariant }]}>
          {label}
        </Text>
        <Text style={[styles.orbValue, { color }]}>
          {Math.round(safeValue)}%
        </Text>
      </View>

      {/* Orb Canvas */}
      <View style={styles.orbCanvasContainer}>
        <Canvas style={styles.orbCanvas}>
          {/* Outer glow ring */}
          <Circle cx={orbCenter} cy={orbCenter} r={orbSize * 0.45}>
            <RadialGradient
              r={orbSize * 0.45}
              c={vec(orbCenter, orbCenter)}
              colors={[
                `${color}60`,
                `${color}20`,
                'transparent',
              ]}
            />
          </Circle>

          {/* Main orb with depth */}
          <Group>
            <Circle cx={orbCenter} cy={orbCenter} r={orbSize * 0.35}>
              <RadialGradient
                r={orbSize * 0.35}
                c={vec(orbCenter + 5, orbCenter - 5)}
                colors={[
                  color,
                  `${color}CC`,
                  `${color}40`,
                ]}
              />
            </Circle>
            
            {/* Inner highlight */}
            <Circle cx={orbCenter - 8} cy={orbCenter - 8} r={orbSize * 0.08}>
              <RadialGradient
                r={orbSize * 0.08}
                c={vec(orbCenter - 8, orbCenter - 8)}
                colors={['#FFFFFF60', 'transparent']}
              />
            </Circle>
          </Group>

          {/* Orbiting particles */}
          <Group transform={[{ rotate: (particlesRotation.value * Math.PI) / 180 }]} origin={vec(orbCenter, orbCenter)}>
            {[0, 120, 240].map((angle, i) => {
              const orbitRadius = orbSize * 0.55;
              const rad = (angle * Math.PI) / 180;
              const px = orbCenter + Math.cos(rad) * orbitRadius;
              const py = orbCenter + Math.sin(rad) * orbitRadius;
              return (
                <Circle key={i} cx={px} cy={py} r={3}>
                  <RadialGradient
                    r={3}
                    c={vec(px, py)}
                    colors={[color, 'transparent']}
                  />
                </Circle>
              );
            })}
          </Group>
        </Canvas>

        {/* Animated glow overlay */}
        <Animated.View
          style={[
            styles.orbGlowOverlay,
            glowStyle,
            { backgroundColor: color },
          ]}
        />
      </View>

      {/* Status indicator */}
      <View
        style={[
          styles.statusIndicator,
          {
            backgroundColor: safeValue > 70 ? colors.batteryHigh : safeValue > 40 ? colors.batteryMid : colors.batteryLow,
          },
        ]}
      />
    </View>
  );
});

/**
 * AugeEnergyOrbs
 * Componente principal con tres orbes de energía
 */
export const AugeEnergyOrbs: React.FC<AugeEnergyOrbsProps> = ({
  cns,
  muscular,
  spinal,
  liveMode = false,
}) => {
  const colors = useColors();

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <View style={styles.headerBadge}>
          <View style={[styles.headerDot, { backgroundColor: colors.primary }]} />
          <Text style={[styles.headerTitle, { color: colors.onSurfaceVariant }]}>
            AUGE EN VIVO
          </Text>
        </View>
        {liveMode && (
          <View style={[styles.liveBadge, { backgroundColor: `${colors.error}20` }]}>
            <View style={[styles.liveDot, { backgroundColor: colors.error }]} />
            <Text style={[styles.liveText, { color: colors.error }]}>LIVE</Text>
          </View>
        )}
      </View>

      {/* Orbs Row */}
      <View style={styles.orbsRow}>
        <EnergyOrb
          value={cns}
          color={colors.ringCns}
          label="CNS"
          icon={ZapIcon}
          index={0}
          liveMode={liveMode}
        />
        <EnergyOrb
          value={muscular}
          color={colors.ringMuscular}
          label="MUSC"
          icon={FlameIcon}
          index={1}
          liveMode={liveMode}
        />
        <EnergyOrb
          value={spinal}
          color={colors.ringSpinal}
          label="SPIN"
          icon={ActivityIcon}
          index={2}
          liveMode={liveMode}
        />
      </View>

      {/* System stress summary */}
      <View style={[styles.summaryCard, { backgroundColor: `${colors.surfaceVariant}40` }]}>
        <Text style={[styles.summaryLabel, { color: colors.onSurfaceVariant }]}>
          Estrés Sistémico
        </Text>
        <View style={styles.summaryBars}>
          <View style={styles.summaryBarTrack}>
            <View
              style={[
                styles.summaryBarFill,
                {
                  width: `${Math.min(100, (cns + muscular + spinal) / 3)}%`,
                  backgroundColor: (cns + muscular + spinal) / 3 > 70 ? colors.error : (cns + muscular + spinal) / 3 > 40 ? colors.batteryMid : colors.batteryHigh,
                },
              ]}
            />
          </View>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginHorizontal: 20,
    marginTop: 6,
    marginBottom: 18,
    paddingHorizontal: 20,
    paddingVertical: 20,
    borderRadius: 28,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.05)',
    backgroundColor: 'rgba(28, 27, 31, 0.6)',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.2,
    shadowRadius: 20,
    elevation: 8,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  headerBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  headerDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  headerTitle: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  liveBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  liveDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  liveText: {
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1,
  },
  orbsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    marginBottom: 16,
  },
  orbContainer: {
    alignItems: 'center',
    gap: 8,
  },
  orbLabelContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginBottom: 4,
  },
  orbLabel: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  orbValue: {
    fontSize: 12,
    fontWeight: '900',
  },
  orbCanvasContainer: {
    width: 100,
    height: 100,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  orbCanvas: {
    width: 100,
    height: 100,
  },
  orbGlowOverlay: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 50,
    opacity: 0.3,
  },
  statusIndicator: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginTop: 4,
  },
  summaryCard: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 16,
    gap: 8,
  },
  summaryLabel: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  summaryBars: {
    flexDirection: 'row',
    gap: 8,
  },
  summaryBarTrack: {
    flex: 1,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    overflow: 'hidden',
  },
  summaryBarFill: {
    height: '100%',
    borderRadius: 3,
  },
});
