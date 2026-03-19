import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import type { Exercise } from '../../types/workout';
import { CheckCircleIcon, ChevronRightIcon } from '../icons';
import { useColors } from '../../theme';

interface CardCarouselBarProps {
  exercises: Exercise[];
  activeExerciseId: string | null;
  skippedIds?: Set<string>;
  completedExerciseIds?: Set<string>;
  durationMinutes?: number;
  completedSetsCount?: number;
  totalSetsCount?: number;
  totalTonnage?: number;
  onSelectExercise: (exerciseId: string) => void;
  onFinish: () => void;
}

export function CardCarouselBar({
  exercises,
  activeExerciseId,
  skippedIds,
  completedExerciseIds,
  durationMinutes = 0,
  completedSetsCount = 0,
  totalSetsCount = 0,
  totalTonnage = 0,
  onSelectExercise,
  onFinish,
}: CardCarouselBarProps) {
  const colors = useColors();

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.container}
    >
      {exercises.map((exercise, index) => {
        const isActive = exercise.id === activeExerciseId;
        const isSkipped = skippedIds?.has(exercise.id) ?? false;
        const isDone = completedExerciseIds?.has(exercise.id) ?? false;
        return (
          <Pressable
            key={exercise.id}
            onPress={() => onSelectExercise(exercise.id)}
            style={[
              styles.card,
              {
                backgroundColor: isActive ? colors.primaryContainer : colors.surface,
                borderColor: isActive ? colors.primary : `${colors.onSurface}1A`,
                opacity: isSkipped ? 0.55 : 1,
              },
            ]}
          >
            <View style={styles.cardTop}>
              <Text
                style={[
                  styles.eyebrow,
                  { color: isActive ? colors.primary : colors.onSurfaceVariant },
                ]}
              >
                Ejercicio {index + 1}
              </Text>
              {isDone ? <CheckCircleIcon size={12} color={colors.cyberSuccess} /> : null}
            </View>
            <Text
              numberOfLines={2}
              style={[
                styles.title,
                { color: isActive ? colors.onPrimaryContainer : colors.onSurface },
              ]}
            >
              {exercise.name}
            </Text>
          </Pressable>
        );
      })}

      <Pressable
        onPress={onFinish}
        style={[
          styles.finishCard,
          {
            backgroundColor: colors.secondaryContainer,
            borderColor: `${colors.secondary}66`,
          },
        ]}
      >
        <Text style={[styles.finishEyebrow, { color: colors.onSecondaryContainer }]}>Cerrar</Text>
        <Text style={[styles.finishTitle, { color: colors.onSecondaryContainer }]}>Finalizar sesión</Text>
        <Text style={[styles.finishMeta, { color: colors.onSecondaryContainer }]}>
          {durationMinutes} min · {completedSetsCount}/{totalSetsCount} sets
        </Text>
        <Text style={[styles.finishMeta, { color: colors.onSecondaryContainer }]}>
          {Math.round(totalTonnage)} kg movidos
        </Text>
        <ChevronRightIcon size={14} color={colors.onSecondaryContainer} />
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 10,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  card: {
    width: 170,
    minHeight: 84,
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 10,
    gap: 6,
  },
  cardTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  eyebrow: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  title: {
    fontSize: 14,
    fontWeight: '800',
    lineHeight: 18,
  },
  finishCard: {
    width: 150,
    minHeight: 84,
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 10,
    alignItems: 'flex-start',
    justifyContent: 'space-between',
  },
  finishEyebrow: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  finishTitle: {
    fontSize: 14,
    fontWeight: '900',
  },
  finishMeta: {
    fontSize: 10,
    fontWeight: '700',
    opacity: 0.9,
  },
});
