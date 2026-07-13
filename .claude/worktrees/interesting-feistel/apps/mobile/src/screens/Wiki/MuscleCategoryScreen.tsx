import React, { useEffect, useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { RouteProp, useRoute } from '@react-navigation/native';
import { ScreenShell } from '@/components/ScreenShell';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { useExerciseStore } from '@/stores/exerciseStore';
import { useColors } from '@/theme';
import type { WikiStackParamList } from '@/navigation/types';
import { getMuscleDisplayId } from '@/utils/canonicalMuscles';

function normalizeMuscleGroupId(name: string) {
  return name.toLowerCase().replace(/\s+/g, '-').replace(/[()]/g, '');
}

export function MuscleCategoryScreen() {
  const colors = useColors();
  const route = useRoute<RouteProp<WikiStackParamList, 'MuscleCategory'>>();
  const status = useExerciseStore(state => state.status);
  const hydrateExercises = useExerciseStore(state => state.hydrateFromMigration);
  const muscleHierarchy = useExerciseStore(state => state.muscleHierarchy);
  const muscleGroupData = useExerciseStore(state => state.muscleGroupData);
  const exerciseList = useExerciseStore(state => state.exerciseList);

  useEffect(() => {
    if (status === 'idle') void hydrateExercises();
  }, [hydrateExercises, status]);

  const categoryName = route.params.categoryName;
  const categoryInfo = useMemo(() => {
    const categoryId = normalizeMuscleGroupId(categoryName);
    return muscleGroupData.find(item => item.id === categoryId);
  }, [categoryName, muscleGroupData]);

  const subgroups = useMemo(() => {
    return muscleHierarchy?.bodyPartHierarchy?.[categoryName] ?? [];
  }, [categoryName, muscleHierarchy]);

  const subgroupRows = useMemo(() => {
    return subgroups.map(item => {
      const subgroupName = typeof item === 'string' ? item : Object.keys(item)[0];
      const matchingExercises = exerciseList.filter(exercise =>
        (exercise.involvedMuscles ?? []).some(muscle => {
          const display = getMuscleDisplayId(String(muscle.muscle), muscle.emphasis);
          return display.toLowerCase() === subgroupName.toLowerCase();
        }),
      );
      return {
        name: subgroupName,
        count: matchingExercises.length,
        examples: matchingExercises.slice(0, 3).map(exercise => exercise.name),
      };
    });
  }, [exerciseList, subgroups]);

  return (
    <ScreenShell
      title={categoryName}
      subtitle="Jerarquia anatomica y biblioteca ligada a esta categoria corporal."
    >
      <View style={styles.container}>
        {categoryInfo ? (
          <LiquidGlassCard style={styles.infoCard} padding={20}>
            <Text style={[styles.infoText, { color: colors.onSurfaceVariant }]}>
              {categoryInfo.description}
            </Text>
            <View style={[styles.calloutBox, { backgroundColor: `${colors.primary}16` }]}>
              <Text style={[styles.calloutLabel, { color: colors.primary }]}>Biomecanica</Text>
              <Text style={[styles.calloutText, { color: colors.onSurface }]}>
                {categoryInfo.importance.movement}
              </Text>
            </View>
          </LiquidGlassCard>
        ) : null}

        {subgroupRows.length === 0 ? (
          <LiquidGlassCard style={styles.emptyCard} padding={20}>
            <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>Sin subgrupos</Text>
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>No encontramos una jerarquia migrada para esta categoria corporal.</Text>
          </LiquidGlassCard>
        ) : (
          <View style={styles.list}>
            {subgroupRows.map(row => (
              <LiquidGlassCard key={row.name} style={styles.rowCard} padding={18}>
                <View style={styles.rowHeader}>
                  <Text style={[styles.rowTitle, { color: colors.onSurface }]}>{row.name}</Text>
                  <Text style={[styles.rowCount, { color: colors.primary }]}> 
                    {row.count} ejercicios
                  </Text>
                </View>
                <Text style={[styles.rowExamples, { color: colors.onSurfaceVariant }]}>
                  {row.examples.length > 0
                    ? `Ejemplos: ${row.examples.join(', ')}`
                    : 'Todavia no hay ejemplos migrados para este subgrupo.'}
                </Text>
              </LiquidGlassCard>
            ))}
          </View>
        )}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  infoCard: {
    borderRadius: 28,
  },
  infoText: {
    fontSize: 14,
    lineHeight: 21,
    marginBottom: 14,
  },
  calloutBox: {
    borderRadius: 20,
    padding: 14,
  },
  calloutLabel: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.4,
    marginBottom: 6,
  },
  calloutText: {
    fontSize: 14,
    lineHeight: 20,
  },
  emptyCard: {
    borderRadius: 28,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '800',
    marginBottom: 6,
  },
  emptyText: {
    fontSize: 14,
    lineHeight: 20,
  },
  list: {
    gap: 12,
  },
  rowCard: {
    borderRadius: 24,
  },
  rowHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
    gap: 12,
  },
  rowTitle: {
    fontSize: 16,
    fontWeight: '800',
    flex: 1,
  },
  rowCount: {
    fontSize: 12,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  rowExamples: {
    fontSize: 13,
    lineHeight: 19,
  },
});
