import React, { useEffect, useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '@/components/ScreenShell';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { ChevronRightIcon } from '@/components/icons';
import { useExerciseStore } from '@/stores/exerciseStore';
import { useColors } from '@/theme';
import type { WikiStackParamList } from '@/navigation/types';
import { getMuscleDisplayId } from '@/utils/canonicalMuscles';

const TRAIN_INFO: Record<string, { description: string; importance: string }> = {
  'Tren Superior': {
    description:
      'Agrupa pecho, espalda, hombros y brazos. Es una de las estructuras mas usadas para manejar frecuencia alta y reparto simple de fatiga.',
    importance:
      'Clave para postura, empujes, tirones y fuerza funcional del torso.',
  },
  'Tren Inferior': {
    description:
      'Reune cuadriceps, gluteos, isquiosurales y pantorrillas. Suele ser la zona con mayor coste sistemico por sesion.',
    importance:
      'Fundamental para potencia, estabilidad, locomocion y transferencia atletica.',
  },
  'Cuerpo Completo': {
    description:
      'Incluye sesiones que estimulan los grandes grupos musculares en un solo entrenamiento.',
    importance:
      'Muy eficiente para frecuencia alta, recomposicion corporal y agendas con menos dias disponibles.',
  },
  Otro: {
    description:
      'Ejercicios hibridos o categorias que no encajan de forma estricta en una sola division corporal.',
    importance:
      'Util para movimientos accesorios, acondicionamiento y objetivos especificos.',
  },
};

function normalizeBodyPartId(value: string) {
  const normalized = value.trim().toLowerCase();
  if (normalized === 'upper' || normalized === 'tren superior') return 'Tren Superior';
  if (normalized === 'lower' || normalized === 'tren inferior') return 'Tren Inferior';
  if (normalized === 'full' || normalized === 'cuerpo completo') return 'Cuerpo Completo';
  return 'Otro';
}

function mapBodyPartToInternal(value: string) {
  if (value === 'Tren Superior') return 'upper';
  if (value === 'Tren Inferior') return 'lower';
  if (value === 'Cuerpo Completo') return 'full';
  return undefined;
}

export function BodyPartDetailScreen() {
  const colors = useColors();
  const route = useRoute<RouteProp<WikiStackParamList, 'BodyPartDetail'>>();
  const navigation = useNavigation<NativeStackNavigationProp<WikiStackParamList>>();
  const status = useExerciseStore(state => state.status);
  const hydrateExercises = useExerciseStore(state => state.hydrateFromMigration);
  const exerciseList = useExerciseStore(state => state.exerciseList);

  useEffect(() => {
    if (status === 'idle') void hydrateExercises();
  }, [hydrateExercises, status]);

  const bodyPartLabel = normalizeBodyPartId(route.params.bodyPartId);
  const internalBodyPart = mapBodyPartToInternal(bodyPartLabel);
  const info = TRAIN_INFO[bodyPartLabel] ?? TRAIN_INFO.Otro;

  const groupedExercises = useMemo(() => {
    const filtered = exerciseList.filter(exercise => exercise.bodyPart === internalBodyPart);
    const next = filtered.reduce<Record<string, typeof filtered>>((acc, exercise) => {
      const primary = exercise.involvedMuscles?.find(item => item.role === 'primary');
      const group = primary ? getMuscleDisplayId(String(primary.muscle), primary.emphasis) : 'Otros';
      if (!acc[group]) acc[group] = [];
      acc[group].push(exercise);
      return acc;
    }, {});
    return Object.entries(next).sort(([a], [b]) => a.localeCompare(b, 'es'));
  }, [exerciseList, internalBodyPart]);

  return (
    <ScreenShell
      title={bodyPartLabel}
      subtitle="Division de entrenamiento y biblioteca de ejercicios asociados."
    >
      <View style={styles.container}>
        <LiquidGlassCard style={styles.infoCard} padding={20}>
          <Text style={[styles.infoText, { color: colors.onSurfaceVariant }]}>{info.description}</Text>
          <View style={[styles.importanceBox, { backgroundColor: `${colors.primary}16` }]}>
            <Text style={[styles.importanceLabel, { color: colors.primary }]}>Importancia</Text>
            <Text style={[styles.importanceText, { color: colors.onSurface }]}>{info.importance}</Text>
          </View>
        </LiquidGlassCard>

        {groupedExercises.length === 0 ? (
          <LiquidGlassCard style={styles.emptyCard} padding={20}>
            <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>Sin ejercicios asociados</Text>
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>La migracion todavia no encontro ejercicios vinculados a esta division corporal.</Text>
          </LiquidGlassCard>
        ) : (
          groupedExercises.map(([muscle, exercises]) => (
            <LiquidGlassCard key={muscle} style={styles.groupCard} padding={18}>
              <Text style={[styles.groupTitle, { color: colors.onSurface }]}>{muscle}</Text>
              <View style={styles.groupList}>
                {exercises.map(exercise => (
                  <Pressable
                    key={exercise.id}
                    style={[styles.exerciseRow, { borderBottomColor: `${colors.outlineVariant}55` }]}
                    onPress={() => navigation.navigate('ExerciseDetail', { exerciseId: exercise.id })}
                  >
                    <View style={styles.exerciseTextWrap}>
                      <Text style={[styles.exerciseName, { color: colors.onSurface }]}>{exercise.name}</Text>
                      <Text style={[styles.exerciseMeta, { color: colors.onSurfaceVariant }]}>
                        {exercise.type} • {exercise.equipment}
                      </Text>
                    </View>
                    <ChevronRightIcon size={18} color={colors.onSurfaceVariant} />
                  </Pressable>
                ))}
              </View>
            </LiquidGlassCard>
          ))
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
  importanceBox: {
    borderRadius: 20,
    padding: 14,
  },
  importanceLabel: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.4,
    marginBottom: 6,
  },
  importanceText: {
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
  groupCard: {
    borderRadius: 24,
  },
  groupTitle: {
    fontSize: 18,
    fontWeight: '800',
    marginBottom: 12,
  },
  groupList: {
    gap: 2,
  },
  exerciseRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  exerciseTextWrap: {
    flex: 1,
    marginRight: 12,
  },
  exerciseName: {
    fontSize: 15,
    fontWeight: '700',
  },
  exerciseMeta: {
    fontSize: 12,
    marginTop: 3,
  },
});
