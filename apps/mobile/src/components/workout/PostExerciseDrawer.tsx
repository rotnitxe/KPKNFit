import React, { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { PostExerciseFeedback } from '../../types/workout';
import { useColors } from '../../theme';
import { ActivityIcon, CheckCircleIcon, StarIcon } from '../icons';
import { WorkoutDrawer } from './WorkoutDrawer';

interface PostExerciseDrawerProps {
  visible: boolean;
  exerciseName: string;
  stats: {
    sets: number;
    reps: number;
    weight: number;
    unit: string;
  };
  initialFeedback?: PostExerciseFeedback;
  onClose: () => void;
  onSave: (feedback: PostExerciseFeedback) => void;
}

const DISCOMFORT_OPTIONS = ['Hombro', 'Codo', 'Muñeca', 'Lumbar', 'Rodilla'];

export function PostExerciseDrawer({
  visible,
  exerciseName,
  stats,
  initialFeedback,
  onClose,
  onSave,
}: PostExerciseDrawerProps) {
  const colors = useColors();
  const [technicalQuality, setTechnicalQuality] = useState(initialFeedback?.technicalQuality ?? 8);
  const [perceivedFatigue, setPerceivedFatigue] = useState(initialFeedback?.perceivedFatigue ?? 6);
  const [mood, setMood] = useState(initialFeedback?.mood ?? 3);
  const [discomforts, setDiscomforts] = useState<string[]>(initialFeedback?.discomforts ?? []);

  const moodLabel = useMemo(() => {
    if (mood <= 1) return 'Drenado';
    if (mood === 2) return 'Tenso';
    if (mood === 3) return 'Estable';
    if (mood === 4) return 'Con energía';
    return 'Imparable';
  }, [mood]);

  const toggleDiscomfort = (tag: string) => {
    setDiscomforts(prev => (prev.includes(tag) ? prev.filter(value => value !== tag) : [...prev, tag]));
  };

  return (
    <WorkoutDrawer visible={visible} title="Feedback de ejercicio" onClose={onClose} maxHeightPercent={0.92}>
      <View style={[styles.headerCard, { backgroundColor: colors.surfaceContainer, borderColor: `${colors.onSurface}1A` }]}>
        <Text style={[styles.headerTitle, { color: colors.onSurface }]}>{exerciseName}</Text>
        <Text style={[styles.headerSub, { color: colors.onSurfaceVariant }]}>
          Cierra este bloque con un registro rápido de calidad y sensación.
        </Text>
        <View style={styles.statsRow}>
          {[
            { label: 'Sets', value: String(stats.sets) },
            { label: 'Reps', value: String(stats.reps) },
            { label: 'Carga', value: `${stats.weight} ${stats.unit}` },
          ].map(item => (
            <View key={item.label} style={[styles.statCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
              <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>{item.label}</Text>
              <Text style={[styles.statValue, { color: colors.onSurface }]}>{item.value}</Text>
            </View>
          ))}
        </View>
      </View>

      <View style={[styles.sectionCard, { backgroundColor: colors.surfaceContainer, borderColor: `${colors.onSurface}1A` }]}>
        <View style={styles.sectionHeader}>
          <ActivityIcon size={16} color={colors.primary} />
          <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Esfuerzo percibido</Text>
        </View>
        <View style={styles.chipsRow}>
          {Array.from({ length: 10 }, (_, index) => index + 1).map(value => (
            <Pressable
              key={value}
              onPress={() => setPerceivedFatigue(value)}
              style={[
                styles.valueChip,
                {
                  backgroundColor: perceivedFatigue === value ? colors.primary : `${colors.onSurface}0D`,
                },
              ]}
            >
              <Text style={[styles.valueChipText, { color: perceivedFatigue === value ? colors.onPrimary : colors.onSurface }]}>
                {value}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>

      <View style={[styles.sectionCard, { backgroundColor: colors.surfaceContainer, borderColor: `${colors.onSurface}1A` }]}>
        <View style={styles.sectionHeader}>
          <StarIcon size={16} color={colors.tertiary} />
          <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Calidad técnica</Text>
        </View>
        <View style={styles.chipsRow}>
          {[6, 7, 8, 9, 10].map(value => (
            <Pressable
              key={value}
              onPress={() => setTechnicalQuality(value)}
              style={[
                styles.valueChip,
                {
                  backgroundColor: technicalQuality === value ? colors.tertiaryContainer : `${colors.onSurface}0D`,
                },
              ]}
            >
              <Text
                style={[
                  styles.valueChipText,
                  { color: technicalQuality === value ? colors.onTertiaryContainer : colors.onSurface },
                ]}
              >
                {value}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>

      <View style={[styles.sectionCard, { backgroundColor: colors.surfaceContainer, borderColor: `${colors.onSurface}1A` }]}>
        <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Estado al cerrar ({moodLabel})</Text>
        <View style={styles.chipsRow}>
          {[1, 2, 3, 4, 5].map(value => (
            <Pressable
              key={value}
              onPress={() => setMood(value)}
              style={[
                styles.valueChip,
                {
                  backgroundColor: mood === value ? colors.secondaryContainer : `${colors.onSurface}0D`,
                },
              ]}
            >
              <Text style={[styles.valueChipText, { color: mood === value ? colors.onSecondaryContainer : colors.onSurface }]}>
                {value}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>

      <View style={[styles.sectionCard, { backgroundColor: colors.surfaceContainer, borderColor: `${colors.onSurface}1A` }]}>
        <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Molestias</Text>
        <View style={styles.chipsRow}>
          {DISCOMFORT_OPTIONS.map(option => {
            const selected = discomforts.includes(option);
            return (
              <Pressable
                key={option}
                onPress={() => toggleDiscomfort(option)}
                style={[
                  styles.pillChip,
                  {
                    backgroundColor: selected ? colors.errorContainer : `${colors.onSurface}0D`,
                    borderColor: selected ? colors.error : `${colors.onSurface}20`,
                  },
                ]}
              >
                <Text style={[styles.pillChipText, { color: selected ? colors.onError : colors.onSurfaceVariant }]}>
                  {option}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      <View style={styles.actions}>
        <Pressable onPress={onClose} style={[styles.secondaryBtn, { borderColor: `${colors.onSurface}30` }]}>
          <Text style={[styles.secondaryBtnText, { color: colors.onSurfaceVariant }]}>Omitir</Text>
        </Pressable>
        <Pressable
          onPress={() =>
            onSave({
              technicalQuality,
              perceivedFatigue,
              mood,
              discomforts,
              jointLoad: Math.max(1, Math.min(10, perceivedFatigue)),
            })
          }
          style={[styles.primaryBtn, { backgroundColor: colors.primary }]}
        >
          <CheckCircleIcon size={16} color={colors.onPrimary} />
          <Text style={[styles.primaryBtnText, { color: colors.onPrimary }]}>Guardar feedback</Text>
        </Pressable>
      </View>
    </WorkoutDrawer>
  );
}

const styles = StyleSheet.create({
  headerCard: {
    borderWidth: 1,
    borderRadius: 14,
    padding: 12,
    gap: 8,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '900',
  },
  headerSub: {
    fontSize: 12,
    lineHeight: 18,
  },
  statsRow: {
    flexDirection: 'row',
    gap: 8,
  },
  statCard: {
    flex: 1,
    borderWidth: 1,
    borderRadius: 12,
    minHeight: 64,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 2,
  },
  statLabel: {
    fontSize: 10,
    textTransform: 'uppercase',
    letterSpacing: 1,
    fontWeight: '700',
  },
  statValue: {
    fontSize: 17,
    fontWeight: '900',
  },
  sectionCard: {
    borderWidth: 1,
    borderRadius: 14,
    padding: 12,
    gap: 10,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '800',
  },
  chipsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  valueChip: {
    width: 40,
    height: 40,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  valueChipText: {
    fontSize: 14,
    fontWeight: '900',
  },
  pillChip: {
    minHeight: 34,
    borderRadius: 20,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
  pillChipText: {
    fontSize: 12,
    fontWeight: '700',
  },
  actions: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 2,
  },
  secondaryBtn: {
    flex: 1,
    minHeight: 46,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryBtnText: {
    fontSize: 13,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  primaryBtn: {
    flex: 1.4,
    minHeight: 46,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 8,
  },
  primaryBtnText: {
    fontSize: 13,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
});
