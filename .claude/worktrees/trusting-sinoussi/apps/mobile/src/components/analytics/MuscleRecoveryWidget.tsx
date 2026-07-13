import React, { useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { getMuscleRecovery } from '../../services/recoveryService';
import { useWellbeingStore } from '../../stores/wellbeingStore';

interface MuscleRecoveryWidgetProps {
  onMusclePress?: (muscle: string) => void;
}

export const MuscleRecoveryWidget: React.FC<MuscleRecoveryWidgetProps> = ({ onMusclePress }) => {
  const colors = useColors();
  const overview = useWellbeingStore(state => state.overview);
  
  const muscleRecovery = useMemo(() => {
    return getMuscleRecovery();
  }, [overview]);

  const getRecoveryColor = (recovery: number) => {
    if (recovery >= 80) return colors.primary;
    if (recovery >= 60) return colors.tertiary;
    return colors.error;
  };

  const getRecoveryText = (recovery: number) => {
    if (recovery >= 80) return 'Óptimo';
    if (recovery >= 60) return 'Moderado';
    return 'Bajo';
  };

  return (
    <LiquidGlassCard style={styles.card}>
      <View style={styles.header}>
        <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Recuperación Muscular</Text>
        <Text style={[styles.headerSubtitle, { color: colors.onSurfaceVariant }]}>Estado actual</Text>
      </View>

      <View style={styles.musclesList}>
        {muscleRecovery.map((muscle, idx) => (
          <TouchableOpacity
            key={muscle.name}
            style={[styles.muscleRow, { borderBottomColor: `${colors.outlineVariant}33` }]}
            onPress={() => onMusclePress?.(muscle.name)}
          >
            <View style={styles.muscleInfo}>
              <Text style={[styles.muscleName, { color: colors.onSurface }]}>{muscle.name}</Text>
              <Text style={[styles.muscleStatus, { color: getRecoveryColor(muscle.recovery) }]}>
                {getRecoveryText(muscle.recovery)}
              </Text>
            </View>
            <View style={styles.recoveryBarContainer}>
              <View style={[styles.recoveryBar, { backgroundColor: colors.surfaceContainer }]}>
                <View 
                  style={[
                    styles.recoveryFill,
                    { width: `${muscle.recovery}%`, backgroundColor: getRecoveryColor(muscle.recovery) }
                  ]}
                />
              </View>
              <Text style={[styles.recoveryPercent, { color: colors.onSurface }]}>
                {muscle.recovery}%
              </Text>
            </View>
          </TouchableOpacity>
        ))}
      </View>
    </LiquidGlassCard>
  );
};

const styles = StyleSheet.create({
  card: {
    padding: 20,
  },
  header: {
    marginBottom: 20,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  headerSubtitle: {
    fontSize: 12,
    marginTop: 4,
  },
  musclesList: {
    gap: 16,
  },
  muscleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  muscleInfo: {
    flex: 1,
  },
  muscleName: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 4,
  },
  muscleStatus: {
    fontSize: 12,
    fontWeight: '500',
  },
  recoveryBarContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    width: 140,
  },
  recoveryBar: {
    flex: 1,
    height: 6,
    borderRadius: 3,
    overflow: 'hidden',
  },
  recoveryFill: {
    height: '100%',
    borderRadius: 3,
  },
  recoveryPercent: {
    fontSize: 12,
    fontWeight: '700',
    width: 40,
    textAlign: 'right',
  },
});
