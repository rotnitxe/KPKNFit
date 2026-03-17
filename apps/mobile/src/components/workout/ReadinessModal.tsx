/**
 * components/workout/ReadinessModal.tsx
 * 
 * Modal de Readiness con diseño "Material Liquid Glass":
 * - Glassmorphism con blur y bordes iridiscentes
 * - Rating pills animados con spring
 * - Micro-interacciones de feedback táctil
 * - Transiciones suaves de entrada/salida
 */

import React, { useState, useCallback } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withTiming,
  interpolate,
  Extrapolation,
} from 'react-native-reanimated';
import { LiquidGlassModal } from '../ui/LiquidGlassModal';
import { Button } from '../ui/Button';
import { useColors } from '../../theme';
import { BedIcon as SleepIcon, MoonIcon as MoodIcon, BrainIcon as PainIcon } from '../icons';

interface ReadinessModalProps {
  visible: boolean;
  onClose: () => void;
  onComplete: (data: { sleep: number; mood: number; soreness: number }) => void;
}

interface RatingPillProps {
  value: number;
  isSelected: boolean;
  onSelect: (value: number) => void;
  index: number;
}

/**
 * RatingPill
 * Pill individual animado con spring para selección de rating
 */
const RatingPill: React.FC<RatingPillProps> = React.memo(({ value, isSelected, onSelect, index }) => {
  const colors = useColors();
  const scale = useSharedValue(isSelected ? 1.1 : 1);
  const opacity = useSharedValue(isSelected ? 1 : 0.7);

  React.useEffect(() => {
    scale.value = withSpring(isSelected ? 1.1 : 1, {
      damping: 15,
      stiffness: 300,
    });
    opacity.value = withTiming(isSelected ? 1 : 0.7, { duration: 150 });
  }, [isSelected, scale, opacity]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
    opacity: opacity.value,
  }));

  return (
    <Pressable onPress={() => onSelect(value)} style={styles.pillWrapper}>
      <Animated.View
        style={[
          styles.pill,
          animatedStyle,
          {
            backgroundColor: isSelected ? colors.primary : `${colors.onSurface}0D`,
            borderColor: isSelected ? colors.primary : `${colors.onSurface}1A`,
          },
        ]}
      >
        <Text
          style={[
            styles.pillNumber,
            { color: isSelected ? colors.onPrimary : colors.onSurface },
          ]}
        >
          {value}
        </Text>
        
        {/* Glow effect when selected */}
        {isSelected && (
          <View
            style={[
              styles.pillGlow,
              { backgroundColor: `${colors.primary}40` },
            ]}
          />
        )}
      </Animated.View>
    </Pressable>
  );
});

/**
 * RatingRow
 * Fila de rating con icono y pills animados
 */
interface RatingRowProps {
  label: string;
  value: number;
  onChange: (value: number) => void;
  icon: React.ComponentType<{ size?: number; color?: string }>;
  color: string;
}

const RatingRow: React.FC<RatingRowProps> = React.memo(({ label, value, onChange, icon: Icon, color }) => {
  const colors = useColors();
  const expandAnim = useSharedValue(0);

  React.useEffect(() => {
    expandAnim.value = withSpring(value / 5, {
      damping: 20,
      stiffness: 150,
    });
  }, [value, expandAnim]);

  const indicatorStyle = useAnimatedStyle(() => ({
    width: interpolate(expandAnim.value, [0, 1], [0, 100], Extrapolation.CLAMP),
  }));

  return (
    <View style={styles.ratingRow}>
      <View style={styles.ratingHeader}>
        <View style={[styles.iconContainer, { backgroundColor: `${color}20` }]}>
          <Icon size={18} color={color} />
          <Text style={[styles.ratingLabel, { color: colors.onSurfaceVariant }]}>
            {label}
          </Text>
        </View>
        <View style={[styles.valueBadge, { backgroundColor: `${color}20` }]}>
          <Text style={[styles.valueText, { color }]}>{value}</Text>
          <Text style={[styles.valueMax, { color: colors.onSurfaceVariant }]}>/5</Text>
        </View>
      </View>

      <View style={styles.pillsContainer}>
        {[1, 2, 3, 4, 5].map((num, idx) => (
          <RatingPill
            key={num}
            value={num}
            index={idx}
            isSelected={value === num}
            onSelect={onChange}
          />
        ))}
      </View>

      {/* Progress indicator */}
      <View style={[styles.progressTrack, { backgroundColor: `${color}15` }]}>
        <Animated.View
          style={[
            styles.progressFill,
            indicatorStyle,
            { backgroundColor: color },
          ]}
        />
      </View>
    </View>
  );
});

/**
 * ReadinessModal
 * Modal principal de evaluación de readiness
 */
export const ReadinessModal: React.FC<ReadinessModalProps> = ({
  visible,
  onClose,
  onComplete,
}) => {
  const colors = useColors();
  const [sleep, setSleep] = useState<number>(3);
  const [mood, setMood] = useState<number>(3);
  const [soreness, setSoreness] = useState<number>(1);

  const handleSave = useCallback(() => {
    onComplete({ sleep, mood, soreness });
  }, [sleep, mood, soreness, onComplete]);

  return (
    <LiquidGlassModal
      visible={visible}
      onClose={onClose}
      title="Readiness Check"
      subtitle="Evalúa tu estado antes de entrenar"
      height={580}
    >
      <View style={styles.formContent}>
        {/* Sleep Quality */}
        <RatingRow
          label="Calidad de Sueño"
          value={sleep}
          onChange={setSleep}
          icon={SleepIcon}
          color={colors.tertiary}
        />

        {/* Mood State */}
        <RatingRow
          label="Estado de Ánimo"
          value={mood}
          onChange={setMood}
          icon={MoodIcon}
          color={colors.secondary}
        />

        {/* Fatigue/Soreness */}
        <RatingRow
          label="Fatiga / Dolor"
          value={soreness}
          onChange={setSoreness}
          icon={PainIcon}
          color={colors.error}
        />
      </View>

      {/* Action Buttons */}
      <View style={styles.actions}>
        <View style={styles.actionButton}>
          <Button variant="secondary" onPress={onClose}>
            Cancelar
          </Button>
        </View>
        <View style={styles.actionButton}>
          <Button variant="primary" onPress={handleSave}>
            Comenzar Sesión
          </Button>
        </View>
      </View>

      {/* Readiness Score Preview */}
      <View style={[styles.scorePreview, { backgroundColor: `${colors.primary}10` }]}>
        <Text style={[styles.scoreLabel, { color: colors.onSurfaceVariant }]}>
          Readiness Estimado
        </Text>
        <View style={styles.scoreValueRow}>
          <Text style={[styles.scoreValue, { color: colors.primary }]}>
            {Math.round(((sleep + mood + (5 - soreness)) / 15) * 100)}%
          </Text>
          <View style={[styles.scoreDot, { backgroundColor: colors.primary }]} />
        </View>
      </View>
    </LiquidGlassModal>
  );
};

const styles = StyleSheet.create({
  formContent: {
    gap: 24,
    marginBottom: 24,
  },
  ratingRow: {
    gap: 12,
  },
  ratingHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  iconContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  ratingLabel: {
    fontSize: 14,
    fontWeight: '600',
    letterSpacing: 0.3,
  },
  valueBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  valueText: {
    fontSize: 16,
    fontWeight: '800',
  },
  valueMax: {
    fontSize: 12,
    fontWeight: '500',
  },
  pillsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 4,
  },
  pillWrapper: {
    flex: 1,
    marginHorizontal: 2,
  },
  pill: {
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
    position: 'relative',
    overflow: 'hidden',
  },
  pillNumber: {
    fontSize: 18,
    fontWeight: '800',
    zIndex: 1,
  },
  pillGlow: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 24,
  },
  progressTrack: {
    height: 4,
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 2,
  },
  actions: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 16,
  },
  actionButton: {
    flex: 1,
  },
  scorePreview: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.05)',
  },
  scoreLabel: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  scoreValueRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  scoreValue: {
    fontSize: 28,
    fontWeight: '900',
    letterSpacing: -1,
  },
  scoreDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
});
