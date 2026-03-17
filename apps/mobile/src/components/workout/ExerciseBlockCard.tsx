import React, { useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Dimensions } from 'react-native';
import type { Exercise, OngoingWorkoutState } from '../../types/workout';
import { ExerciseSetRow } from './ExerciseSetRow';
import { ExerciseSubstitutionSheet } from './ExerciseSubstitutionSheet';
import { ActivityIcon, TrophyIcon } from '../icons';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { useColors } from '../../theme';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface ExerciseBlockCardProps {
  exercise: Exercise;
  activeSession: OngoingWorkoutState | null;
  exerciseIndex: number;
  isFocused?: boolean;
  onSelect?: () => void;
}

export const ExerciseBlockCard: React.FC<ExerciseBlockCardProps> = ({
  exercise,
  activeSession,
  exerciseIndex,
  isFocused = false,
  onSelect,
}) => {
  const [subModalVisible, setSubModalVisible] = React.useState(false);
  const colors = useColors();

  const totalSets = exercise.sets?.length ?? 0;
  const doneSets = exercise.sets?.filter(s => activeSession?.completedSets[s.id]).length ?? 0;
  const completedPercent = totalSets > 0 ? (doneSets / totalSets) : 0;
  
  const metaText = [
    `${totalSets} series`,
    exercise.restTime ? `${exercise.restTime}s rest` : null,
  ].filter(Boolean).join(' · ');

  const handleSelect = () => {
    ReactNativeHapticFeedback.trigger('selection');
    onSelect?.();
  };

  const cardStyle = useMemo(() => [
    styles.container,
    isFocused && { borderColor: colors.primary, backgroundColor: `${colors.primary}05` }
  ] as any, [colors.primary, isFocused]);

  return (
    <LiquidGlassCard 
      style={cardStyle}
    >
      <TouchableOpacity activeOpacity={0.9} onPress={handleSelect} style={styles.headerPressable}>
        <View style={styles.headerRow}>
          <View style={[styles.indexBadge, { backgroundColor: isFocused ? colors.primary : `${colors.onSurface}08` }]}>
            <Text style={[styles.indexBadgeText, { color: isFocused ? colors.onPrimary : colors.onSurfaceVariant }]}>
              {exerciseIndex + 1}
            </Text>
          </View>
          
          <View style={styles.headerContent}>
            <View style={styles.titleRow}>
              <Text style={[styles.exerciseName, { color: colors.onSurface }]}>{exercise.name}</Text>
              <View style={[styles.progressBadge, { backgroundColor: `${colors.primary}10`, borderColor: `${colors.primary}20` }]}>
                <Text style={[styles.progressBadgeText, { color: colors.primary }]}>{doneSets}/{totalSets}</Text>
              </View>
            </View>
            <Text style={[styles.metaText, { color: colors.onSurfaceVariant }]}>{metaText.toUpperCase()}</Text>
            {exercise.notes ? (
              <Text style={[styles.notesText, { color: colors.onSurfaceVariant }]}>{exercise.notes}</Text>
            ) : null}
          </View>
        </View>
      </TouchableOpacity>

      <View style={styles.actionRow}>
        <TouchableOpacity 
          onPress={() => setSubModalVisible(true)} 
          style={[styles.actionChip, { backgroundColor: `${colors.onSurface}05`, borderColor: `${colors.onSurface}10` }]}
        >
          <ActivityIcon size={12} color={colors.onSurfaceVariant} />
          <Text style={[styles.actionChipText, { color: colors.onSurfaceVariant }]}>Sustituir</Text>
        </TouchableOpacity>
        
        <View style={[styles.progressTrack, { backgroundColor: `${colors.primary}08` }]}>
          <View style={[styles.progressFill, { width: `${completedPercent * 100}%`, backgroundColor: colors.primary }]} />
        </View>
      </View>

      <View style={styles.tableSection}>
        {exercise.sets && exercise.sets.length > 0 ? (
          exercise.sets.map((set, index) => {
            const setId = set.id || `${exercise.id}-set-${index}`;
            return (
              <ExerciseSetRow
                key={setId}
                exerciseId={exercise.id}
                setId={setId}
                setIndex={index}
                set={set}
                activeSession={activeSession}
              />
            );
          })
        ) : (
          <View style={styles.emptyContainer}>
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>No sets available.</Text>
          </View>
        )}
      </View>

      <ExerciseSubstitutionSheet
        visible={subModalVisible}
        onClose={() => setSubModalVisible(false)}
        oldExerciseId={exercise.id}
      />
    </LiquidGlassCard>
  );
};

const styles = StyleSheet.create({
  container: {
    marginHorizontal: 16,
    marginBottom: 20,
    paddingVertical: 16,
  },
  headerPressable: {
    paddingHorizontal: 16,
  },
  headerRow: {
    flexDirection: 'row',
    gap: 12,
    alignItems: 'center',
  },
  indexBadge: {
    width: 32,
    height: 32,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  indexBadgeText: {
    fontSize: 14,
    fontWeight: '900',
  },
  headerContent: {
    flex: 1,
    gap: 4,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
  },
  exerciseName: {
    flex: 1,
    fontSize: 20,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  progressBadge: {
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderWidth: 1,
  },
  progressBadgeText: {
    fontSize: 12,
    fontWeight: '900',
  },
  metaText: {
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1.2,
    opacity: 0.6,
  },
  notesText: {
    fontSize: 12,
    lineHeight: 16,
    opacity: 0.8,
    marginTop: 4,
  },
  actionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
    marginTop: 16,
    marginBottom: 12,
  },
  actionChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    borderWidth: 1,
  },
  actionChipText: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  progressTrack: {
    flex: 1,
    height: 4,
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 2,
  },
  tableSection: {
    marginTop: 4,
    borderTopWidth: 1,
    borderTopColor: 'rgba(0,0,0,0.05)',
  },
  emptyContainer: {
    padding: 20,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 13,
  },
});
