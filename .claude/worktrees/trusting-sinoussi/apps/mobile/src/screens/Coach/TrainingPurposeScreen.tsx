import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { ScreenShell } from '@/components/ScreenShell';
import { Button } from '@/components/ui';
import { useColors } from '@/theme';
import { useExerciseStore } from '@/stores/exerciseStore';
import { generateExercisesForPurpose } from '@/services/aiService';
import type { ExerciseMuscleInfo } from '@/types/workout';

const PREDEFINED_PURPOSES = [
  'Mejorar rendimiento en futbol',
  'Ser mas fuerte en el trabajo',
  'Ser el mejor en rugby',
  'Punos de acero en boxeo',
  'Aumentar salto vertical',
  'Correr mas rapido (sprints)',
  'Vida longeva y saludable',
  'Pretemporada de ski',
] as const;

export function TrainingPurposeScreen() {
  const colors = useColors();
  const [purpose, setPurpose] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [results, setResults] = useState<
    { name: string; justification: string; primaryMuscles: string[] }[]
  >([]);

  const exerciseStatus = useExerciseStore(state => state.status);
  const exerciseList = useExerciseStore(state => state.exerciseList);
  const hydrateExercises = useExerciseStore(state => state.hydrateFromMigration);

  useEffect(() => {
    if (exerciseStatus === 'idle') {
      void hydrateExercises();
    }
  }, [exerciseStatus, hydrateExercises]);

  const catalog = useMemo(() => exerciseList as ExerciseMuscleInfo[], [exerciseList]);

  const runGeneration = useCallback(
    async (nextPurpose?: string) => {
      const finalPurpose = (nextPurpose ?? purpose).trim();
      if (!finalPurpose) return;

      setLoading(true);
      setError(null);
      setPurpose(finalPurpose);
      try {
        const response = await generateExercisesForPurpose(finalPurpose, { exerciseCatalog: catalog });
        setResults(response.exercises);
      } catch (generationError) {
        setResults([]);
        setError(
          generationError instanceof Error
            ? generationError.message
            : 'No pudimos generar sugerencias para este objetivo.',
        );
      } finally {
        setLoading(false);
      }
    },
    [catalog, purpose],
  );

  return (
    <ScreenShell
      title="Laboratorio de Propositos"
      subtitle="Descubre ejercicios clave para tu meta."
    >
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.content}>
        <View style={[styles.section, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.label, { color: colors.onSurface }]}>Describe tu objetivo</Text>
          <TextInput
            value={purpose}
            onChangeText={setPurpose}
            placeholder="Ej: Quiero ser mas explosivo para basquetbol"
            placeholderTextColor={colors.onSurfaceVariant}
            multiline
            style={[
              styles.input,
              {
                color: colors.onSurface,
                borderColor: colors.outlineVariant,
                backgroundColor: colors.surfaceContainer,
              },
            ]}
          />

          <View style={styles.chips}>
            {PREDEFINED_PURPOSES.map(item => (
              <Pressable
                key={item}
                onPress={() => {
                  void runGeneration(item);
                }}
                style={({ pressed }) => [
                  styles.chip,
                  {
                    backgroundColor: colors.surfaceContainer,
                    borderColor: colors.outlineVariant,
                    opacity: pressed ? 0.8 : 1,
                  },
                ]}
              >
                <Text style={[styles.chipText, { color: colors.onSurface }]}>{item}</Text>
              </Pressable>
            ))}
          </View>

          <Button
            onPress={() => {
              void runGeneration();
            }}
            disabled={!purpose.trim() || loading}
          >
            Generar ejercicios clave
          </Button>
        </View>

        {loading ? (
          <View style={[styles.loaderCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <ActivityIndicator color={colors.primary} />
            <Text style={[styles.loaderText, { color: colors.onSurfaceVariant }]}>
              Armando sugerencias para tu objetivo...
            </Text>
          </View>
        ) : null}

        {error ? (
          <View style={[styles.errorCard, { borderColor: `${colors.error}55`, backgroundColor: `${colors.error}12` }]}>
            <Text style={[styles.errorText, { color: colors.error }]}>{error}</Text>
          </View>
        ) : null}

        {results.length > 0 ? (
          <View style={styles.results}>
            <Text style={[styles.resultsTitle, { color: colors.onSurface }]}>Ejercicios para: {purpose}</Text>
            {results.map((item, index) => (
              <View
                key={`${item.name}-${index}`}
                style={[styles.resultCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
              >
                <Text style={[styles.resultName, { color: colors.onSurface }]}>{item.name}</Text>
                <Text style={[styles.resultJustification, { color: colors.onSurfaceVariant }]}>
                  {item.justification}
                </Text>
                <Text style={[styles.resultMuscles, { color: colors.primary }]}>
                  Musculos clave: {item.primaryMuscles.join(', ')}
                </Text>
              </View>
            ))}
          </View>
        ) : null}
      </ScrollView>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: 16,
    paddingBottom: 40,
  },
  section: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
    gap: 12,
  },
  label: {
    fontSize: 13,
    fontWeight: '700',
  },
  input: {
    minHeight: 88,
    borderWidth: 1,
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 10,
    textAlignVertical: 'top',
    fontSize: 14,
    lineHeight: 20,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  chipText: {
    fontSize: 12,
    fontWeight: '600',
  },
  loaderCard: {
    borderRadius: 20,
    borderWidth: 1,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  loaderText: {
    fontSize: 13,
    fontWeight: '500',
  },
  errorCard: {
    borderRadius: 20,
    borderWidth: 1,
    padding: 14,
  },
  errorText: {
    fontSize: 13,
    fontWeight: '600',
    lineHeight: 18,
  },
  results: {
    gap: 10,
  },
  resultsTitle: {
    fontSize: 16,
    fontWeight: '800',
  },
  resultCard: {
    borderRadius: 18,
    borderWidth: 1,
    padding: 14,
    gap: 6,
  },
  resultName: {
    fontSize: 16,
    fontWeight: '800',
  },
  resultJustification: {
    fontSize: 13,
    lineHeight: 18,
  },
  resultMuscles: {
    fontSize: 12,
    fontWeight: '700',
  },
});
