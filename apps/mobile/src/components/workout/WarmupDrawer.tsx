import React, { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';
import type { Exercise, WarmupSetDefinition, WeightUnit } from '../../types/workout';
import { roundWeight } from '../../utils/calculations';
import { CheckCircleIcon } from '../icons';
import { useColors } from '../../theme';
import { WorkoutDrawer } from './WorkoutDrawer';

interface WarmupDrawerProps {
  visible: boolean;
  exercise: Exercise | null;
  baseWeight: number;
  weightUnit: WeightUnit;
  onBaseWeightChange: (weight: number) => void;
  onClose: () => void;
  onComplete: () => void;
}

function normalizeWarmupSets(rawSets: unknown[] | undefined): WarmupSetDefinition[] {
  if (!Array.isArray(rawSets)) return [];
  return rawSets
    .map((raw, index) => {
      if (!raw || typeof raw !== 'object') return null;
      const item = raw as Record<string, unknown>;
      const percentage = Number(item.percentageOfWorkingWeight);
      const reps = Number(item.targetReps);
      if (!Number.isFinite(percentage) || !Number.isFinite(reps)) return null;
      return {
        id: typeof item.id === 'string' && item.id.length > 0 ? item.id : `warmup-${index}`,
        percentageOfWorkingWeight: percentage,
        targetReps: reps,
      } satisfies WarmupSetDefinition;
    })
    .filter((item): item is WarmupSetDefinition => item !== null);
}

export function WarmupDrawer({
  visible,
  exercise,
  baseWeight,
  weightUnit,
  onBaseWeightChange,
  onClose,
  onComplete,
}: WarmupDrawerProps) {
  const colors = useColors();
  const warmupSets = useMemo(() => normalizeWarmupSets(exercise?.warmupSets), [exercise?.warmupSets]);
  const [checked, setChecked] = useState<Set<string>>(new Set());
  const [weightInput, setWeightInput] = useState(String(baseWeight > 0 ? baseWeight : ''));

  useEffect(() => {
    if (!visible) {
      setChecked(new Set());
      setWeightInput(String(baseWeight > 0 ? baseWeight : ''));
      return;
    }
    setWeightInput(String(baseWeight > 0 ? baseWeight : ''));
  }, [baseWeight, visible]);

  useEffect(() => {
    if (warmupSets.length === 0) return;
    if (checked.size === warmupSets.length) {
      const timer = setTimeout(() => {
        onComplete();
        onClose();
      }, 280);
      return () => clearTimeout(timer);
    }
    return;
  }, [checked, onClose, onComplete, warmupSets.length]);

  const parsedBaseWeight = Number(weightInput);
  const effectiveBaseWeight = Number.isFinite(parsedBaseWeight) ? parsedBaseWeight : 0;

  const toggleSet = (setId: string) => {
    ReactNativeHapticFeedback.trigger('selection');
    setChecked(prev => {
      const next = new Set(prev);
      if (next.has(setId)) {
        next.delete(setId);
      } else {
        next.add(setId);
      }
      return next;
    });
  };

  if (!exercise) return null;

  return (
    <WorkoutDrawer visible={visible} title="Series de aproximación" onClose={onClose} maxHeightPercent={0.84}>
      {warmupSets.length === 0 ? (
        <View style={[styles.emptyCard, { backgroundColor: colors.surfaceContainer }]}>
          <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>Esta sesión no trae warmups definidos</Text>
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
            Puedes registrar series directo en la sesión activa.
          </Text>
        </View>
      ) : (
        <>
          <View style={[styles.weightCard, { backgroundColor: colors.surfaceContainer, borderColor: `${colors.onSurface}1A` }]}>
            <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>Carga base</Text>
            <View style={styles.weightRow}>
              <TextInput
                value={weightInput}
                onChangeText={text => {
                  setWeightInput(text);
                  const numeric = Number(text);
                  if (Number.isFinite(numeric)) {
                    onBaseWeightChange(numeric);
                  }
                }}
                keyboardType="numeric"
                style={[styles.weightInput, { color: colors.onSurface, borderColor: `${colors.onSurface}33` }]}
                placeholder="0"
                placeholderTextColor={`${colors.onSurfaceVariant}99`}
              />
              <Text style={[styles.weightUnit, { color: colors.onSurfaceVariant }]}>{weightUnit}</Text>
            </View>
          </View>

          <View style={styles.setList}>
            {warmupSets.map(set => {
              const done = checked.has(set.id);
              const targetWeight = roundWeight(effectiveBaseWeight * (set.percentageOfWorkingWeight / 100), weightUnit);

              return (
                <Pressable
                  key={set.id}
                  onPress={() => toggleSet(set.id)}
                  style={[
                    styles.setCard,
                    {
                      backgroundColor: done ? colors.primaryContainer : colors.surface,
                      borderColor: done ? colors.primary : `${colors.onSurface}20`,
                    },
                  ]}
                >
                  <View style={styles.leftInfo}>
                    <View style={[styles.checkCircle, { borderColor: done ? colors.primary : `${colors.onSurface}33` }]}>
                      {done ? <CheckCircleIcon size={14} color={colors.primary} /> : null}
                    </View>
                    <View>
                      <Text style={[styles.weightValue, { color: done ? colors.onPrimaryContainer : colors.onSurface }]}>
                        {targetWeight} {weightUnit}
                      </Text>
                      <Text style={[styles.weightMeta, { color: done ? colors.onPrimaryContainer : colors.onSurfaceVariant }]}>
                        {set.percentageOfWorkingWeight}% de la carga base
                      </Text>
                    </View>
                  </View>
                  <View style={styles.repsInfo}>
                    <Text style={[styles.repsValue, { color: done ? colors.onPrimaryContainer : colors.onSurface }]}>
                      {set.targetReps}
                    </Text>
                    <Text style={[styles.repsLabel, { color: done ? colors.onPrimaryContainer : colors.onSurfaceVariant }]}>
                      reps
                    </Text>
                  </View>
                </Pressable>
              );
            })}
          </View>
        </>
      )}
    </WorkoutDrawer>
  );
}

const styles = StyleSheet.create({
  weightCard: {
    borderWidth: 1,
    borderRadius: 14,
    padding: 12,
    gap: 8,
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  weightRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  weightInput: {
    width: 94,
    minHeight: 40,
    borderWidth: 1,
    borderRadius: 10,
    fontSize: 16,
    fontWeight: '800',
    textAlign: 'center',
  },
  weightUnit: {
    fontSize: 13,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  setList: {
    gap: 8,
  },
  setCard: {
    borderWidth: 1,
    borderRadius: 14,
    minHeight: 68,
    paddingHorizontal: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  leftInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    flex: 1,
  },
  checkCircle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  weightValue: {
    fontSize: 16,
    fontWeight: '900',
  },
  weightMeta: {
    fontSize: 11,
  },
  repsInfo: {
    alignItems: 'flex-end',
  },
  repsValue: {
    fontSize: 20,
    fontWeight: '900',
  },
  repsLabel: {
    fontSize: 11,
    textTransform: 'uppercase',
    letterSpacing: 1,
    fontWeight: '700',
  },
  emptyCard: {
    borderRadius: 14,
    padding: 14,
    gap: 6,
  },
  emptyTitle: {
    fontSize: 15,
    fontWeight: '800',
  },
  emptyText: {
    fontSize: 13,
    lineHeight: 20,
  },
});
