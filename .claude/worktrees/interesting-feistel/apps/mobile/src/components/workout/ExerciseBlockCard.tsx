import React, { useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Dimensions } from 'react-native';
import type { Exercise, OngoingWorkoutState } from '../../types/workout';
import { ExerciseSetRow } from './ExerciseSetRow';
import { ExerciseSubstitutionSheet } from './ExerciseSubstitutionSheet';
import { ExerciseCardContextMenu } from './ExerciseCardContextMenu';
import { ActivityIcon, TrophyIcon } from '../icons';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { useColors } from '../../theme';
import ReactNativeHapticFeedback from '@/services/hapticsService';
import { useWorkoutStore } from '../../stores/workoutStore';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface ExerciseBlockCardProps {
  exercises: Exercise[]; // Changed to array of exercises
  activeSession: OngoingWorkoutState | null;
  exerciseIndex: number; // Index of the group in the session
  isFocused?: boolean;
  onSelect?: () => void;
  onOpenPostExercise?: (exercise: Exercise) => void;
}

export const ExerciseBlockCard: React.FC<ExerciseBlockCardProps> = ({
  exercises,
  activeSession,
  exerciseIndex,
  isFocused = false,
  onSelect,
  onOpenPostExercise,
}) => {
  const [subModalVisible, setSubModalVisible] = React.useState(false);
  const [contextMenuVisible, setContextMenuVisible] = React.useState(false);
  const colors = useColors();

  // Calculate total sets and done sets across all exercises in the group
  const totalSets = exercises.reduce((sum, exercise) => sum + (exercise.sets?.length ?? 0), 0);
  const doneSets = exercises.reduce((sum, exercise) => {
    const exerciseDone = exercise.sets?.filter(set => activeSession?.completedSets[set.id]).length ?? 0;
    return sum + exerciseDone;
  }, 0);
  const completedPercent = totalSets > 0 ? (doneSets / totalSets) : 0;

  // Use the first exercise for metaText (rest time, notes) and name
  const firstExercise = exercises[0];
  const metaText = [
    `${totalSets} series`,
    firstExercise.restTime ? `${firstExercise.restTime}s rest` : null,
  ].filter(Boolean).join(' · ');

  const handleSelect = () => {
    ReactNativeHapticFeedback.trigger('selection');
    onSelect?.();
  };

  const handleSkipBlock = React.useCallback(() => {
    if (!activeSession) return;
    const { completeSet } = useWorkoutStore.getState();
    for (const exercise of exercises) {
      for (const set of exercise.sets ?? []) {
        if (activeSession.completedSets[set.id]) continue;
        completeSet(
          exercise.id,
          set.id,
          {
            weight: set.weight ?? 0,
            reps: set.targetReps ?? 0,
            performanceMode: 'failed',
            isIneffective: true,
          },
          set.isCalibrator,
        );
      }
    }
  }, [activeSession, exercises]);

  const cardStyle = useMemo(() => [
    styles.container,
    isFocused && { borderColor: colors.primary, backgroundColor: `${colors.primary}05` }
  ] as any, [colors.primary, isFocused]);

  return (
    <LiquidGlassCard 
      style={cardStyle}
    >
      <TouchableOpacity
        activeOpacity={0.9}
        onPress={handleSelect}
        onLongPress={() => setContextMenuVisible(true)}
        delayLongPress={220}
        style={styles.headerPressable}
      >
        <View style={styles.headerRow}>
          <View style={[styles.indexBadge, { backgroundColor: isFocused ? colors.primary : `${colors.onSurface}08` }]}>
            <Text style={[styles.indexBadgeText, { color: isFocused ? colors.onPrimary : colors.onSurfaceVariant }]}>
              {exerciseIndex + 1}
            </Text>
          </View>

          <View style={styles.headerContent}>
            <View style={styles.titleRow}>
              {/* Show first exercise name and if there are more, show a count */}
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <Text style={[styles.exerciseName, { color: colors.onSurface }]}>{firstExercise.name}</Text>
                {exercises.length > 1 && (
                  <Text style={{ marginLeft: 4, fontSize: 12, color: colors.onSurfaceVariant }}>
                    (+{exercises.length - 1} más)
                  </Text>
                )}
                {/* SUPERSET badge if more than one exercise */}
                {exercises.length > 1 && (
                  <View style={styles.supersetBadge}>
                    <Text style={styles.supersetBadgeText}>SUPERSET</Text>
                  </View>
                )}
              </View>
              <View style={[styles.progressBadge, { backgroundColor: `${colors.primary}10`, borderColor: `${colors.primary}20` }]}>
                <Text style={[styles.progressBadgeText, { color: colors.primary }]}>{doneSets}/{totalSets}</Text>
              </View>
            </View>
            <Text style={[styles.metaText, { color: colors.onSurfaceVariant }]}>{metaText.toUpperCase()}</Text>
            {firstExercise.notes ? (
              <Text style={[styles.notesText, { color: colors.onSurfaceVariant }]}>{firstExercise.notes}</Text>
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

        {onOpenPostExercise ? (
          <TouchableOpacity
            onPress={() => onOpenPostExercise(firstExercise)}
            style={[styles.actionChip, { backgroundColor: `${colors.primary}16`, borderColor: `${colors.primary}30` }]}
          >
            <TrophyIcon size={12} color={colors.primary} />
            <Text style={[styles.actionChipText, { color: colors.primary }]}>Feedback</Text>
          </TouchableOpacity>
        ) : null}

        <View style={[styles.progressTrack, { backgroundColor: `${colors.primary}08` }]}>
          <View style={[styles.progressFill, { width: `${completedPercent * 100}%`, backgroundColor: colors.primary }]} />
        </View>
      </View>

      <View style={styles.tableSection}>
        {/* Render sets for each exercise in the group */}
        {totalSets > 0 ? (
          exercises.map((exercise, exerciseIdx) => (
            <View key={exercise.id} style={styles.exerciseGroupContainer}>
              <Text style={styles.exerciseGroupLabel}>{exercise.name}</Text>
              {exercise.sets && exercise.sets.length > 0 ? (
                exercise.sets.map((set, setIdx) => {
                  const setId = set.id || `${exercise.id}-set-${setIdx}`;
                  return (
                    <ExerciseSetRow
                      key={setId}
                      exerciseId={exercise.id}
                      setId={setId}
                      setIndex={setIdx}
                      set={set}
                      activeSession={activeSession}
                    />
                  );
                })
              ) : (
                <Text style={styles.emptyText}>No sets for this exercise.</Text>
              )}
            </View>
          ))
        ) : (
          <View style={styles.emptyContainer}>
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>No sets available.</Text>
          </View>
        )}
      </View>

      <ExerciseSubstitutionSheet
        visible={subModalVisible}
        onClose={() => setSubModalVisible(false)}
        oldExerciseId={firstExercise.id} // Use the first exercise's ID for substitution sheet
      />

      <ExerciseCardContextMenu
        visible={contextMenuVisible}
        onClose={() => setContextMenuVisible(false)}
        onReplace={() => setSubModalVisible(true)}
        onSkip={handleSkipBlock}
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
  supersetBadge: {
    backgroundColor: '#D9F0DB',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 10,
    marginLeft: 8,
  },
  supersetBadgeText: {
    fontSize: 10,
    fontWeight: '600',
    color: '#5D3FD3',
    textTransform: 'uppercase',
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
  exerciseGroupContainer: {
    marginVertical: 8,
  },
  exerciseGroupLabel: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 4,
  },
  emptyContainer: {
    padding: 20,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 13,
  },
});
