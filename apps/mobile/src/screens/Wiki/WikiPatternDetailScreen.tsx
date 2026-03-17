import React, { useMemo } from 'react';
import { View, ScrollView, Text, Pressable, StyleSheet } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import {
  WIKI_MUSCLES,
  WIKI_JOINTS,
  WIKI_MOVEMENT_PATTERNS
} from '../../data/wikiData';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { WorkoutStackParamList } from '../../navigation/AppNavigator';
import { useColors } from '@/theme';

export function WikiPatternDetailScreen() {
  const route = useRoute<RouteProp<WorkoutStackParamList, 'WikiPatternDetail'>>();
  const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
  const colors = useColors();
  const { patternId } = route.params;

  const exerciseList = useExerciseStore(state => state.exerciseList);
  const pattern = useMemo(() => WIKI_MOVEMENT_PATTERNS.find(p => p.id === patternId), [patternId]);

  if (!pattern) {
    return (
      <ScreenShell title="Error" subtitle="Patrón no encontrado.">
        <Text style={[styles.errorText, { color: colors.onSurface }]}>
          El patrón solicitado no existe.
        </Text>
      </ScreenShell>
    );
  }

  const muscles = useMemo(() => WIKI_MUSCLES.filter(m => pattern.primaryMuscles?.includes(m.id)), [pattern]);
  const joints = useMemo(() => WIKI_JOINTS.filter(j => pattern.primaryJoints?.includes(j.id)), [pattern]);

  const RelatedItem = ({ item, onPress }: { item: { id: string; name: string }; onPress: () => void }) => (
    <Pressable
      onPress={onPress}
      style={[styles.relatedItem, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
    >
      <Text style={[styles.relatedItemText, { color: colors.onSurface }]}>{item.name}</Text>
    </Pressable>
  );

  return (
    <ScreenShell title={pattern.name} subtitle="Patrón de movimiento">
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={[styles.section, styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Descripción</Text>
          <Text style={[styles.bodyText, { color: colors.onSurface }]}>{pattern.description}</Text>

          <View style={styles.metaRow}>
            <Text style={[styles.metaLabel, { color: colors.onSurfaceVariant }]}>Fuerza:</Text>
            <Text style={[styles.metaValue, { color: colors.onSurface }]}>{pattern.forceTypes.join(', ')}</Text>
          </View>
          <View style={styles.metaRowSmall}>
            <Text style={[styles.metaLabel, { color: colors.onSurfaceVariant }]}>Cadena:</Text>
            <Text style={[styles.metaValue, { color: colors.onSurface }]}>{pattern.chainTypes.join(', ')}</Text>
          </View>
        </View>

        {muscles.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Músculos primarios
            </Text>
            {muscles.map(m => (
              <RelatedItem
                key={m.id}
                item={m}
                onPress={() => navigation.navigate('WikiMuscleDetail', { muscleId: m.id })}
              />
            ))}
          </View>
        )}

        {joints.length > 0 && (
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
              Articulaciones clave
            </Text>
            {joints.map(j => (
              <RelatedItem
                key={j.id}
                item={j}
                onPress={() => navigation.navigate('WikiJointDetail', { jointId: j.id })}
              />
            ))}
          </View>
        )}

        <View
          style={[
            styles.section,
            styles.card,
            { backgroundColor: `${colors.primary}0D`, borderColor: `${colors.primary}33` },
          ]}
        >
          <Text style={[styles.sectionTitle, { color: colors.primary }]}>Ejercicios de ejemplo</Text>
          {pattern.exampleExercises.map((exName, idx) => {
            const exercise = exerciseList.find(e => e.name.toLowerCase() === exName.toLowerCase());
            return (
              <Pressable
                key={idx}
                onPress={() => {
                  if (exercise) {
                    navigation.navigate('ExerciseDetail', { exerciseId: exercise.id });
                  }
                }}
                style={[styles.exerciseItem, { opacity: exercise ? 1 : 0.6 }]}
              >
                <Text style={[styles.exerciseText, { color: colors.onSurface, fontWeight: exercise ? '700' : '400' }]}>
                  • {exName} {exercise ? '(Ver detalle)' : ''}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </ScrollView>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  section: {
    marginBottom: 24,
    paddingHorizontal: 16,
  },
  card: {
    borderRadius: 16,
    paddingVertical: 16,
    paddingHorizontal: 16,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 2,
    textTransform: 'uppercase',
    marginBottom: 12,
  },
  bodyText: {
    fontSize: 15,
    lineHeight: 22,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 16,
  },
  metaRowSmall: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  metaLabel: {
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  metaValue: {
    fontSize: 12,
    fontWeight: '700',
  },
  relatedItem: {
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginBottom: 8,
  },
  relatedItemText: {
    fontSize: 16,
    fontWeight: '600',
  },
  exerciseItem: {
    marginBottom: 8,
    paddingVertical: 8,
  },
  exerciseText: {
    fontSize: 15,
  },
  errorText: {
    fontSize: 16,
    textAlign: 'center',
    padding: 40,
  },
});
