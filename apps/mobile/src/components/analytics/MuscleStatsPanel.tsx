import React, { useState, useEffect, useMemo } from 'react';
import { 
  View, 
  Text, 
  StyleSheet, 
  TouchableOpacity, 
  Animated,
  Easing
} from 'react-native';
import { useColors } from '../../theme';
import type { DetailedMuscleVolumeAnalysis } from '../../types/workout';
import { getVolumeThresholdsForMuscle } from '../../services/volumeCalculator';
import { INITIAL_MUSCLE_GROUP_DATA } from '../../data/initialMuscleGroupDatabase';
import { XIcon } from '../icons';
import type { MuscleVolumeThresholds } from '../../types/volume';

type MuscleVolumeData = { 
  muscleGroup: string; 
  displayVolume: number; 
  directExercises?: { name: string; sets: number }[]; 
  indirectExercises?: { name: string; sets: number }[] 
};

interface MuscleStatsPanelProps {
  selectedMuscle: string | null;
  data: MuscleVolumeData[];
  program?: any;
  settings?: any;
  onClose?: () => void;
}

const getStatusFromThresholds = (sets: number, thresholds: MuscleVolumeThresholds, programMode?: string) => {
  const isPowerlifting = programMode === 'powerlifting' || programMode === 'powerbuilding' || programMode === 'strength';
  const { min, max } = thresholds;

  if (isPowerlifting) {
    if (sets === 0) return { label: 'Inactivo', color: 'text-black/40', bg: 'bg-black/[0.03]' };
    if (sets < 6) return { label: 'Bajo', color: 'text-blue-500', bg: 'bg-blue-50' };
    if (sets <= 12) return { label: 'Óptimo', color: 'text-emerald-500', bg: 'bg-emerald-50' };
    return { label: 'Alto', color: 'text-amber-500', bg: 'bg-amber-50' };
  }

  if (sets === 0) return { label: 'Inactivo', color: 'text-black/40', bg: 'bg-black/[0.03]' };
  if (sets < min) return { label: 'Subentreno', color: 'text-blue-500', bg: 'bg-blue-50' };
  if (sets <= max) return { label: 'Óptimo', color: 'text-emerald-500', bg: 'bg-emerald-50' };
  return { label: 'Sobreentreno', color: 'text-red-500', bg: 'bg-red-50' };
};

export const MuscleStatsPanel: React.FC<MuscleStatsPanelProps> = ({
  selectedMuscle, data, program, settings, onClose,
}) => {
  const colors = useColors();
  const [animatedValue] = useState(new Animated.Value(0));
  const [slideValue] = useState(new Animated.Value(20));

  const styles = useMemo(() => StyleSheet.create({
    panelContainer: {
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      backgroundColor: 'rgba(28, 27, 31, 0.95)',
      justifyContent: 'center',
      alignItems: 'center',
      padding: 20,
      zIndex: 1000,
    },
    panelContent: {
      backgroundColor: colors.surface,
      borderRadius: 24,
      padding: 24,
      width: '90%',
      maxWidth: 400,
      maxHeight: '80%',
    },
    panelHeader: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginBottom: 24,
    },
    panelTitle: {
      fontSize: 18,
      fontWeight: '700',
      textTransform: 'uppercase',
      letterSpacing: 1,
      color: colors.onSurface,
    },
    closeButton: {
      padding: 8,
    },
    volumeSection: {
      marginBottom: 24,
    },
    volumeInfo: {
      alignItems: 'center',
    },
    volumeLabel: {
      fontSize: 14,
      color: colors.onSurfaceVariant,
      textTransform: 'uppercase',
      marginBottom: 4,
    },
    volumeValue: {
      fontSize: 36,
      fontWeight: '900',
      color: colors.onSurface,
    },
    progressBarContainer: {
      position: 'relative',
      height: 8,
      backgroundColor: 'rgba(255,255,255,0.05)',
      borderRadius: 4,
      overflow: 'hidden',
      marginVertical: 12,
    },
    progressBarBackground: {
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
    },
    progressBarFill: {
      position: 'absolute',
      top: 0,
      left: 0,
      bottom: 0,
      borderRadius: 4,
    },
    statsGrid: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      marginBottom: 24,
    },
    statCard: {
      padding: 16,
      borderRadius: 16,
      alignItems: 'center',
      minWidth: 120,
    },
    statLabel: {
      fontSize: 12,
      color: colors.onSurfaceVariant,
      textTransform: 'uppercase',
      marginBottom: 4,
    },
    statValue: {
      fontSize: 16,
      fontWeight: '600',
      textTransform: 'uppercase',
    },
    exerciseSection: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      paddingVertical: 12,
      borderTopWidth: 1,
      borderColor: 'rgba(255,255,255,0.1)',
    },
    exerciseInfo: {
      alignItems: 'center',
    },
    exerciseLabel: {
      fontSize: 12,
      color: colors.onSurfaceVariant,
      textTransform: 'uppercase',
    },
    exerciseValue: {
      fontSize: 16,
      fontWeight: '600',
      color: colors.onSurface,
    },
    divider: {
      width: 1,
      height: 24,
      backgroundColor: 'rgba(255,255,255,0.1)',
    },
    descriptionText: {
      fontSize: 14,
      color: colors.onSurfaceVariant,
      lineHeight: 20,
      fontStyle: 'italic',
    },
  }), [colors]);
  
  useEffect(() => {
    if (selectedMuscle) {
      Animated.parallel([
        Animated.timing(animatedValue, {
          toValue: 1,
          duration: 300,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: false,
        }),
        Animated.timing(slideValue, {
          toValue: 0,
          duration: 300,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: false,
        }),
      ]).start();
    }
  }, [selectedMuscle, animatedValue, slideValue]);

  useEffect(() => {
    if (!selectedMuscle) {
      Animated.parallel([
        Animated.timing(animatedValue, {
          toValue: 0,
          duration: 200,
          easing: Easing.in(Easing.cubic),
          useNativeDriver: false,
        }),
        Animated.timing(slideValue, {
          toValue: -20,
          duration: 200,
          easing: Easing.in(Easing.cubic),
          useNativeDriver: false,
        }),
      ]).start();
    }
  }, [selectedMuscle, animatedValue, slideValue]);

  if (!selectedMuscle) {
    return null;
  }

  const item = data.find(d =>
    d.muscleGroup.toLowerCase().includes(selectedMuscle.toLowerCase()) ||
    (selectedMuscle === 'Abdomen' && d.muscleGroup.toLowerCase().includes('abdom'))
  ) || {
    muscleGroup: selectedMuscle,
    displayVolume: 0,
    directExercises: [],
    indirectExercises: [],
  };

  const athleteScore = settings?.athleteScore ?? (program as any)?.athleteProfile ?? null;
  const thresholds = getVolumeThresholdsForMuscle(item.muscleGroup, { program, settings, athleteScore });
  const status = getStatusFromThresholds(item.displayVolume, thresholds, program?.mode);

  const dbInfo = INITIAL_MUSCLE_GROUP_DATA.find((m: { id: string; name: string }) =>
    m.id.toLowerCase().includes(selectedMuscle.toLowerCase()) ||
    m.name.toLowerCase().includes(selectedMuscle.toLowerCase())
  );

  const directSets = item.directExercises?.reduce((s, e) => s + e.sets, 0) ?? 0;
  const indirectSets = item.indirectExercises?.reduce((s, e) => s + e.sets, 0) ?? 0;

  const progress = Math.min(100, (item.displayVolume / (thresholds.max || 1)) * 100);
  const statusColor = 
    item.displayVolume < thresholds.min ? colors.batteryMid :
    item.displayVolume > thresholds.max ? colors.error :
    colors.batteryHigh;
  const statusBgColor = 
    item.displayVolume < thresholds.min ? colors.batteryMid + '20' :
    item.displayVolume > thresholds.max ? colors.error + '20' :
    colors.batteryHigh + '20';

  return (
    <Animated.View 
      style={[
        styles.panelContainer,
        {
          opacity: animatedValue,
          transform: [{ translateX: slideValue }]
        }
      ]}
    >
      <View style={styles.panelHeader}>
        <Text style={styles.panelTitle}>{item.muscleGroup}</Text>
        {onClose && (
          <TouchableOpacity
            onPress={onClose}
            style={styles.closeButton}
          >
            <XIcon size={14} color={colors.onSurface} />
          </TouchableOpacity>
        )}
      </View>

      <View style={styles.panelContent}>
        <View style={styles.volumeSection}>
          <View style={styles.volumeInfo}>
            <Text style={styles.volumeLabel}>Sets Semanales</Text>
            <Text style={styles.volumeValue}>{item.displayVolume}</Text>
          </View>
          
          <View style={styles.progressBarContainer}>
            <View style={styles.progressBarBackground} />
            <View style={[
              styles.progressBarFill,
              { 
                width: `${progress}%`,
                backgroundColor: statusColor
              }
            ]} />
          </View>
        </View>

        <View style={styles.statsGrid}>
          <View style={[
            styles.statCard,
            { backgroundColor: statusBgColor }
          ]}>
            <Text style={styles.statLabel}>Estado</Text>
            <Text style={[
              styles.statValue,
              { color: statusColor }
            ]}>
              {status.label}
            </Text>
          </View>
          
          <View style={styles.statCard}>
            <Text style={styles.statLabel}>Objetivo</Text>
            <Text style={styles.statValue}>{thresholds.rangeLabel}</Text>
          </View>
        </View>

        {(directSets > 0 || indirectSets > 0) && (
          <View style={styles.exerciseSection}>
            <View style={styles.exerciseInfo}>
              <Text style={styles.exerciseLabel}>Directos</Text>
              <Text style={styles.exerciseValue}>{directSets}</Text>
            </View>
            <View style={styles.divider} />
            <View style={styles.exerciseInfo}>
              <Text style={styles.exerciseLabel}>Indirectos</Text>
              <Text style={styles.exerciseValue}>{indirectSets}</Text>
            </View>
          </View>
        )}

        {dbInfo?.description && (
          <Text style={styles.descriptionText}>
            {dbInfo.description}
          </Text>
        )}
      </View>
    </Animated.View>
  );
};

export default MuscleStatsPanel;
