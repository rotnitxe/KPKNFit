import React, { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '@/components/ScreenShell';
import { useExerciseStore } from '@/stores/exerciseStore';
import type { WikiStackParamList } from '@/navigation/types';
import { useColors } from '@/theme';
import { buildMobilityRoutine, buildMobilitySuggestions, type MobilityRoutine } from '@/services/wikiMobilityService';

type WikiMobilityNavProp = NativeStackNavigationProp<WikiStackParamList>;

function openDetail(navigation: WikiMobilityNavProp, link: MobilityRoutine['detailLink']) {
  if (!link) return;
  if (link.articleType === 'muscle') {
    navigation.navigate('WikiMuscleDetail', { muscleId: link.articleId });
  } else if (link.articleType === 'joint') {
    navigation.navigate('WikiJointDetail', { jointId: link.articleId });
  } else {
    navigation.navigate('WikiPatternDetail', { patternId: link.articleId });
  }
}

export function WikiMobilityScreen() {
  const colors = useColors();
  const navigation = useNavigation<WikiMobilityNavProp>();
  const exerciseList = useExerciseStore(state => state.exerciseList);
  const [query, setQuery] = useState('');
  const [selectedTarget, setSelectedTarget] = useState<string | null>(null);
  const [routine, setRoutine] = useState<MobilityRoutine | null>(null);
  const [error, setError] = useState<string | null>(null);

  const suggestions = useMemo(() => buildMobilitySuggestions(query, exerciseList), [query, exerciseList]);

  const handleSuggestion = (suggestion: string) => {
    setQuery(suggestion);
    setSelectedTarget(suggestion);
    setRoutine(null);
    setError(null);
  };

  const handleGenerate = () => {
    const target = selectedTarget ?? query.trim();
    if (!target) {
      setError('Elige un foco antes de generar la rutina.');
      return;
    }

    const nextRoutine = buildMobilityRoutine(target, exerciseList);
    setRoutine(nextRoutine);
    setError(null);
  };

  return (
    <ScreenShell title="Movilidad" subtitle="Rangos activos y control para entrar mejor a la sesión">
      <View style={styles.container}>
        <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Elige un foco</Text>
          <Text style={[styles.bodyText, { color: colors.onSurface }]}>
            Busca una articulación, un grupo muscular o un ejercicio. El laboratorio te devuelve una
            rutina práctica de activación y movilidad.
          </Text>
          <View style={[styles.inputRow, { borderColor: colors.outlineVariant }]}>
            <TextInput
              value={query}
              onChangeText={text => {
                setQuery(text);
                setSelectedTarget(null);
                setRoutine(null);
                setError(null);
              }}
              placeholder="Ej: cadera, hombro, tobillo..."
              placeholderTextColor={colors.onSurfaceVariant}
              style={[styles.input, { color: colors.onSurface }]}
              autoCorrect={false}
            />
          </View>
          <Pressable
            onPress={handleGenerate}
            style={[styles.primaryButton, { backgroundColor: colors.primary }]}
            accessibilityRole="button"
            accessibilityLabel="Generar rutina de movilidad"
          >
            <Text style={[styles.primaryButtonText, { color: colors.onPrimary }]}>Generar rutina</Text>
          </Pressable>
        </View>

        {suggestions.length > 0 && (
          <View>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Sugerencias</Text>
            <View style={styles.suggestionRow}>
              {suggestions.map(suggestion => (
                <Pressable
                  key={suggestion}
                  onPress={() => handleSuggestion(suggestion)}
                  style={[styles.suggestionChip, { borderColor: colors.outlineVariant, backgroundColor: colors.surface }]}
                  accessibilityRole="button"
                  accessibilityLabel={`Sugerencia ${suggestion}`}
                >
                  <Text style={[styles.suggestionText, { color: colors.onSurface }]}>{suggestion}</Text>
                </Pressable>
              ))}
            </View>
          </View>
        )}

        {error ? (
          <View style={[styles.card, { backgroundColor: `${colors.error}14`, borderColor: `${colors.error}33` }]}>
            <Text style={[styles.bodyText, { color: colors.error }]}>{error}</Text>
          </View>
        ) : null}

        {routine ? (
          <View style={styles.routineBlock}>
          <View style={[styles.card, { backgroundColor: `${colors.primary}0D`, borderColor: `${colors.primary}33` }]}>
              <Text style={[styles.sectionTitle, { color: colors.primary }]}>{`Rutina para ${routine.targetLabel}`}</Text>
              <Text style={[styles.bodyText, { color: colors.onSurface }]}>{routine.summary}</Text>
              <Text style={[styles.metaText, { color: colors.onSurfaceVariant }]}>
                Duración total estimada: {Math.round(routine.totalDurationSeconds / 60)} min
              </Text>
              {routine.detailLink ? (
                <Pressable
                  onPress={() => openDetail(navigation, routine.detailLink)}
                  style={[styles.secondaryButton, { borderColor: colors.outlineVariant }]}
                  accessibilityRole="button"
                  accessibilityLabel={routine.detailLink.label}
                >
                  <Text style={[styles.secondaryButtonText, { color: colors.onSurface }]}>
                    {routine.detailLink.label}
                  </Text>
                </Pressable>
              ) : null}
            </View>

            {routine.steps.map(step => (
              <View
                key={step.name}
                style={[styles.stepCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}
              >
                <View style={styles.stepHeader}>
                  <Text style={[styles.stepTitle, { color: colors.onSurface }]}>{step.name}</Text>
                  <Text style={[styles.stepDuration, { color: colors.onSurfaceVariant }]}>
                    {step.durationSeconds}s
                  </Text>
                </View>
                <Text style={[styles.stepFocus, { color: colors.primary }]}>{step.focus}</Text>
                <Text style={[styles.stepInstruction, { color: colors.onSurfaceVariant }]}>
                  {step.instruction}
                </Text>
              </View>
            ))}
          </View>
        ) : (
          <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.bodyText, { color: colors.onSurfaceVariant }]}>
              Toca una sugerencia o escribe tu foco para generar una rutina útil.
            </Text>
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
  card: {
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 16,
    gap: 12,
  },
  sectionTitle: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  bodyText: {
    fontSize: 15,
    lineHeight: 22,
  },
  inputRow: {
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginTop: 4,
  },
  input: {
    fontSize: 16,
    minHeight: 24,
  },
  primaryButton: {
    alignItems: 'center',
    borderRadius: 999,
    paddingVertical: 14,
  },
  primaryButtonText: {
    fontSize: 14,
    fontWeight: '800',
  },
  suggestionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  suggestionChip: {
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  suggestionText: {
    fontSize: 12,
    fontWeight: '700',
  },
  routineBlock: {
    gap: 12,
  },
  metaText: {
    fontSize: 12,
    fontWeight: '600',
  },
  secondaryButton: {
    alignSelf: 'flex-start',
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  secondaryButtonText: {
    fontSize: 12,
    fontWeight: '800',
  },
  stepCard: {
    borderRadius: 18,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  stepHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  stepTitle: {
    flex: 1,
    fontSize: 15,
    fontWeight: '800',
  },
  stepDuration: {
    fontSize: 12,
    fontWeight: '700',
  },
  stepFocus: {
    fontSize: 12,
    fontWeight: '800',
    marginTop: 6,
  },
  stepInstruction: {
    fontSize: 13,
    lineHeight: 20,
    marginTop: 6,
  },
});
