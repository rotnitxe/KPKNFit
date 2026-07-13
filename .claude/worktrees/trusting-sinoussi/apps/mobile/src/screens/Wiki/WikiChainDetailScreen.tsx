import React, { useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '../../components/ScreenShell';
import { useColors } from '../../theme';
import { useExerciseStore } from '../../stores/exerciseStore';
import type { ExerciseCatalogEntry } from '../../types/workout';
import type { WikiStackParamList } from '../../navigation/types';
import { getWikiChainDefinition } from '../../data/wikiExploreData';

type WikiChainRouteProp = RouteProp<WikiStackParamList, 'WikiChainDetail'>;
type WikiChainNavigationProp = NativeStackNavigationProp<WikiStackParamList>;

function matchesChain(exercise: ExerciseCatalogEntry, chainId: string) {
  const bodyPart = exercise.bodyPart ?? null;
  const chain = exercise.chain ?? null;

  switch (chainId) {
    case 'anterior':
      return chain === 'anterior';
    case 'posterior':
      return chain === 'posterior';
    case 'full':
      return chain === 'full' || bodyPart === 'full';
    case 'upper':
      return bodyPart === 'upper';
    case 'lower':
      return bodyPart === 'lower';
    case 'core':
      return /core|abdom|lumbar/i.test(exercise.name) || /core|abdom|lumbar/i.test(exercise.category);
    default:
      return chain == null;
  }
}

function groupExercises(exercises: ExerciseCatalogEntry[]) {
  const groups = new Map<string, ExerciseCatalogEntry[]>();

  for (const exercise of exercises) {
    const primaryMuscle =
      exercise.involvedMuscles.find(muscle => muscle.role === 'primary')?.muscle ??
      exercise.category ??
      'Otros';
    const key = String(primaryMuscle);
    const current = groups.get(key) ?? [];
    current.push(exercise);
    groups.set(key, current);
  }

  return Array.from(groups.entries()).sort((a, b) => a[0].localeCompare(b[0]));
}

export function WikiChainDetailScreen() {
  const route = useRoute<WikiChainRouteProp>();
  const navigation = useNavigation<WikiChainNavigationProp>();
  const colors = useColors();
  const exerciseList = useExerciseStore(state => state.exerciseList);

  const chain = useMemo(() => getWikiChainDefinition(route.params?.chainId), [route.params?.chainId]);
  const matchingExercises = useMemo(
    () => exerciseList.filter(exercise => matchesChain(exercise, chain.id)),
    [chain.id, exerciseList],
  );
  const groupedExercises = useMemo(() => groupExercises(matchingExercises), [matchingExercises]);

  return (
    <ScreenShell title={chain.title} subtitle={chain.subtitle}>
      <View style={styles.container}>
        <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Descripción</Text>
          <Text style={[styles.bodyText, { color: colors.onSurface }]}>{chain.description}</Text>
        </View>

        <View style={[styles.card, { backgroundColor: `${colors.primary}0D`, borderColor: `${colors.primary}33` }]}>
          <Text style={[styles.sectionTitle, { color: colors.primary }]}>Importancia</Text>
          <Text style={[styles.bodyText, { color: colors.onSurface }]}>{chain.importance}</Text>
          <View style={styles.chipRow}>
            {chain.focusAreas.map(focus => (
              <View
                key={focus}
                style={[styles.chip, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
              >
                <Text style={[styles.chipText, { color: colors.onSurfaceVariant }]}>{focus}</Text>
              </View>
            ))}
          </View>
        </View>

        <View>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Ejercicios de referencia</Text>
          {groupedExercises.length > 0 ? (
            groupedExercises.map(([muscle, exercises]) => (
              <View
                key={muscle}
                style={[styles.groupCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
              >
                <Text style={[styles.groupTitle, { color: colors.onSurface }]}>{muscle}</Text>
                <Text style={[styles.groupMeta, { color: colors.onSurfaceVariant }]}>
                  {exercises.length} ejercicio{exercises.length === 1 ? '' : 's'}
                </Text>
                {exercises.map(exercise => (
                  <Pressable
                    key={exercise.id}
                    onPress={() => navigation.navigate('ExerciseDetail', { exerciseId: exercise.id })}
                    style={[styles.exerciseRow, { borderColor: colors.outlineVariant }]}
                  >
                    <View style={styles.exerciseTextBlock}>
                      <Text style={[styles.exerciseName, { color: colors.onSurface }]}>{exercise.name}</Text>
                      <Text style={[styles.exerciseDetails, { color: colors.onSurfaceVariant }]}>
                        {exercise.type} · {exercise.equipment}
                      </Text>
                    </View>
                    <Text style={[styles.chevron, { color: colors.primary }]}>›</Text>
                  </Pressable>
                ))}
              </View>
            ))
          ) : (
            <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
              <Text style={[styles.bodyText, { color: colors.onSurfaceVariant }]}>
                No hay ejercicios registrados para esta cadena todavía.
              </Text>
            </View>
          )}
        </View>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  card: {
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 16,
    gap: 10,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  bodyText: {
    fontSize: 15,
    lineHeight: 22,
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 8,
  },
  chip: {
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  chipText: {
    fontSize: 11,
    fontWeight: '700',
  },
  groupCard: {
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 16,
    marginTop: 12,
  },
  groupTitle: {
    fontSize: 18,
    fontWeight: '700',
  },
  groupMeta: {
    fontSize: 12,
    marginTop: 4,
    marginBottom: 12,
  },
  exerciseRow: {
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  exerciseTextBlock: {
    flex: 1,
  },
  exerciseName: {
    fontSize: 15,
    fontWeight: '700',
  },
  exerciseDetails: {
    marginTop: 2,
    fontSize: 12,
  },
  chevron: {
    fontSize: 20,
    fontWeight: '800',
  },
});

