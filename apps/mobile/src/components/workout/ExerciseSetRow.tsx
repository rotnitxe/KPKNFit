import React, { useMemo } from 'react';

import { View, Text, Pressable, StyleSheet } from 'react-native';
import { ModernSetEditor } from './ModernSetEditor';
import { TargetIcon, FlameIcon, XCircleIcon, CheckCircleIcon } from '../icons';
import { useWorkoutStore } from '../../stores/workoutStore';
import { ExerciseSet, OngoingWorkoutState, OngoingSetData } from '../../types/workout';
import { PWA_WORKOUT_PALETTE as PWA } from './pwaWorkoutPalette';

interface ExerciseSetRowProps {
  exerciseId: string;
  setId: string;
  setIndex: number;
  set: ExerciseSet;
  activeSession: OngoingWorkoutState | null;
}

function formatValue(value?: number, suffix?: string) {
  if (typeof value !== 'number' || Number.isNaN(value)) return '--';
  return suffix ? `${value} ${suffix}` : String(value);
}

export const ExerciseSetRow: React.FC<ExerciseSetRowProps> = ({
  exerciseId,
  setId,
  setIndex,
  set,
  activeSession,
}) => {
  const updateSetData = useWorkoutStore(s => s.updateSetData);
  const setActiveExercise = useWorkoutStore(s => s.setActiveExercise);
  const setActiveSet = useWorkoutStore(s => s.setActiveSet);
  const [editorVisible, setEditorVisible] = React.useState(false);

  const completedData = activeSession?.completedSets[setId];
  const isCompleted = !!completedData;
  const isUnilateral = !!(completedData && 'left' in completedData);

  const primaryData = React.useMemo(() => {
    if (!completedData) return null;
    if ('left' in completedData) {
      return completedData.left || completedData.right || null;
    }
    return completedData;
  }, [completedData]);

  const perfMode = primaryData?.performanceMode || 'target';
  
  const displayWeight = useMemo(() => {
    if (typeof primaryData?.weight === 'number') return primaryData.weight;
    
    // Si no está completada, revisamos si hay override por calibración en esta sesión
    const adjusted1RM = activeSession?.sessionAdjusted1RMs?.[exerciseId];
    if (adjusted1RM && set.targetPercentageRM) {
      return Math.round(adjusted1RM * (set.targetPercentageRM / 100) * 4) / 4; // Redondeo a 0.25kg
    }
    
    return set.weight;
  }, [primaryData?.weight, activeSession?.sessionAdjusted1RMs, exerciseId, set.weight, set.targetPercentageRM]);

  const displayReps = typeof primaryData?.reps === 'number' ? primaryData.reps : set.targetReps;


  const modeIcon = () => {
    if (!isCompleted) return <TargetIcon size={14} color={PWA.muted} />;
    if (perfMode === 'failure') return <FlameIcon size={14} color={PWA.warning} />;
    if (perfMode === 'failed') return <XCircleIcon size={14} color={PWA.danger} />;
    return <CheckCircleIcon size={14} color={PWA.success} />;
  };

  const handleOpen = () => {
    setActiveExercise(exerciseId);
    setActiveSet(setId);
    setEditorVisible(true);
  };

  const handleQuickComplete = () => {
    setActiveExercise(exerciseId);
    setActiveSet(setId);
    if (!isCompleted) {
      const { completeSet } = useWorkoutStore.getState();
      completeSet(exerciseId, setId, {
        weight: set.weight || 0,
        reps: set.targetReps || 0,
        rir: set.targetRIR || 0,
        performanceMode: 'target',
        isAmrap: set.isAmrap,
        isCalibrator: set.isCalibrator,
      }, set.isCalibrator);
      return;
    }
    setEditorVisible(true);
  };


  const summaryText = isCompleted
    ? `Registrada: ${formatValue(displayWeight, 'kg')} · ${formatValue(displayReps, 'reps')}`
    : 'Objetivo base para esta serie';

  return (
    <View style={[styles.container, isCompleted && styles.containerCompleted]}>
      <View style={styles.indexWrap}>
        <View style={[styles.indexBadge, isCompleted && styles.indexBadgeCompleted]}>
          <Text style={[styles.indexText, isCompleted && styles.indexTextCompleted]}>{setIndex + 1}</Text>
        </View>
      </View>

      <Pressable style={styles.mainPressable} onPress={handleOpen}>
        <View style={styles.topRow}>
          <View style={styles.labelRow}>
            {modeIcon()}
            <Text style={styles.labelText}>Serie {setIndex + 1}</Text>
            {isUnilateral ? (
              <View style={styles.uniBadge}>
                <Text style={styles.uniBadgeText}>UNI</Text>
              </View>
            ) : null}
          </View>
          {set.targetRIR !== undefined ? (
            <Text style={styles.rirText}>RIR {set.targetRIR}</Text>
          ) : null}
        </View>

        <View style={styles.metricRow}>
          <View style={styles.metricBox}>
            <Text style={styles.metricLabel}>KG</Text>
            <Text style={styles.metricValue}>{formatValue(displayWeight)}</Text>
          </View>
          <View style={styles.metricBox}>
            <Text style={styles.metricLabel}>{typeof set.targetDuration === 'number' ? 'SEG' : 'REPS'}</Text>
            <Text style={styles.metricValue}>{formatValue(displayReps)}</Text>
          </View>
        </View>

        <Text style={styles.summaryText}>{summaryText}</Text>
      </Pressable>

      <Pressable onPress={handleQuickComplete} style={[styles.completeButton, isCompleted && styles.completeButtonDone]}>
        <CheckCircleIcon size={20} color={isCompleted ? '#FFFFFF' : PWA.primary} />
      </Pressable>

      <ModernSetEditor
        visible={editorVisible}
        onClose={() => setEditorVisible(false)}
        onSave={(data) => {
          const { completeSet } = useWorkoutStore.getState();
          completeSet(exerciseId, setId, data, set.isCalibrator);
          setEditorVisible(false);
        }}
        initialData={primaryData as OngoingSetData | undefined}
        exerciseName={activeSession?.session.exercises.find(ex => ex.id === exerciseId)?.name || 'Ejercicio'}
        setIndex={setIndex}
        targetReps={set.targetReps}
        targetWeight={set.weight}
        isAmrap={set.isAmrap}
        isCalibrator={set.isCalibrator}
      />

    </View>
  );
};


const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 14,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#F0EAF5',
    backgroundColor: 'rgba(255,255,255,0.65)',
  },
  containerCompleted: {
    backgroundColor: '#F7F3FF',
  },
  indexWrap: {
    width: 30,
    alignItems: 'center',
  },
  indexBadge: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F3EDF7',
  },
  indexBadgeCompleted: {
    backgroundColor: '#D9F0DB',
  },
  indexText: {
    color: PWA.muted,
    fontSize: 11,
    fontWeight: '900',
  },
  indexTextCompleted: {
    color: PWA.success,
  },
  mainPressable: {
    flex: 1,
    gap: 8,
  },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  labelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  labelText: {
    fontSize: 11,
    fontWeight: '900',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: PWA.text,
  },
  uniBadge: {
    borderRadius: 999,
    paddingHorizontal: 7,
    paddingVertical: 3,
    backgroundColor: '#EEE7FF',
  },
  uniBadgeText: {
    color: PWA.primaryDeep,
    fontSize: 9,
    fontWeight: '900',
  },
  rirText: {
    color: PWA.muted,
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  metricRow: {
    flexDirection: 'row',
    gap: 10,
  },
  metricBox: {
    minWidth: 84,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 16,
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#EAE1F2',
  },
  metricLabel: {
    color: PWA.muted,
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  metricValue: {
    marginTop: 3,
    color: PWA.text,
    fontSize: 16,
    fontWeight: '800',
  },
  summaryText: {
    color: PWA.muted,
    fontSize: 12,
    lineHeight: 16,
  },
  completeButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#D4C8EA',
    backgroundColor: '#FFFFFF',
  },
  completeButtonDone: {
    backgroundColor: PWA.primary,
    borderColor: PWA.primary,
  },
});
