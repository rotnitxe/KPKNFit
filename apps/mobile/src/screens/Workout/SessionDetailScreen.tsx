import React from 'react';
import { Text, View, ScrollView, StyleSheet } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { useProgramStore } from '../../stores/programStore';
import type { Program, Session, ProgramExercise, ExerciseSet } from '../../types/workout';
import { useColors } from '../../theme';

/**
 * Busca una sesión por ID recorriendo la estructura anidada del programa:
 * macrocycles -> blocks -> mesocycles -> weeks -> sessions
 */
function findSessionById(programs: Program[], sessionId: string): Session | null {
  if (!programs || !Array.isArray(programs)) return null;

  for (const program of programs) {
    const macrocycles = program.macrocycles || [];
    for (const macro of macrocycles) {
      const blocks = macro.blocks || [];
      for (const block of blocks) {
        const mesos = block.mesocycles || [];
        for (const meso of mesos) {
          const weeks = meso.weeks || [];
          for (const week of weeks) {
            const sessions = week.sessions || [];
            for (const session of sessions) {
              if (session.id === sessionId) {
                return session;
              }
            }
          }
        }
      }
    }
  }
  return null;
}

export function SessionDetailScreen({ route }: { route: { params: { sessionId: string } } }) {
  const colors = useColors();
  const { sessionId } = route.params;
  const programs = useProgramStore(s => s.programs);

  const session = findSessionById(programs, sessionId);

  if (!session) {
    return (
      <ScreenShell title="Sesión no encontrada">
        <View style={styles.emptyContainer}>
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
            No pudimos encontrar los detalles de esta sesión.
          </Text>
        </View>
      </ScreenShell>
    );
  }

  const exercises: ProgramExercise[] = session.exercises || [];

  return (
    <ScreenShell title={session.name || 'Detalle de Sesión'}>
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={[styles.card, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.focusTitle, { color: colors.onSurface }]}>
            Foco: {session.focus || 'General'}
          </Text>
          {session.description ? (
            <Text style={[styles.description, { color: colors.onSurfaceVariant }]}>{session.description}</Text>
          ) : null}
        </View>

        <View style={styles.sectionHeader}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
            Ejercicios Planeados
          </Text>
          {exercises.map((ex: ProgramExercise, index: number) => (
            <View
              key={ex.id || index}
              style={[
                styles.exerciseCard,
                { 
                  backgroundColor: colors.surfaceContainer, 
                  borderColor: colors.outlineVariant 
                }
              ]}
            >
              <Text style={[styles.exerciseName, { color: colors.onSurface }]}>
                {ex.name || 'Ejercicio sin nombre'}
              </Text>
              {ex.notes ? (
                <Text style={[styles.notes, { color: colors.onSurfaceVariant }]}>
                  {ex.notes}
                </Text>
              ) : null}

              <View style={[styles.setsContainer, { borderTopColor: colors.outlineVariant }]}>
                {(ex.sets || []).map((set: ExerciseSet, sIdx: number) => (
                  <View key={set.id || sIdx} style={styles.setRow}>
                    <Text style={[styles.setText, { color: colors.onSurfaceVariant }]}>Set {sIdx + 1}:</Text>
                    <Text style={[styles.targetText, { color: colors.onSurface }]}>
                      {set.targetReps || set.completedReps || '0'} reps{' '}
                      {set.weight ? `@ ${set.weight}kg` : ''}
                      {set.targetRIR !== undefined ? ` (RIR ${set.targetRIR})` : ''}
                    </Text>
                  </View>
                ))}
              </View>
            </View>
          ))}

          {exercises.length === 0 ? (
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant, marginTop: 24 }]}>
              No hay ejercicios definidos para esta sesión.
            </Text>
          ) : null}
        </View>
      </ScrollView>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  emptyText: {
    fontSize: 16,
    textAlign: 'center',
  },
  card: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
    marginBottom: 20,
  },
  focusTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  description: {
    fontSize: 14,
    lineHeight: 20,
  },
  sectionHeader: {
    marginTop: 8,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 16,
    textTransform: 'uppercase',
    letterSpacing: 2,
  },
  exerciseCard: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
    marginBottom: 12,
  },
  exerciseName: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  notes: {
    fontSize: 12,
    fontStyle: 'italic',
    marginTop: 4,
  },
  setsContainer: {
    marginTop: 12,
    borderTopWidth: 1,
    paddingTop: 8,
  },
  setRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 4,
  },
  setText: {
    fontSize: 14,
  },
  targetText: {
    fontSize: 14,
    fontWeight: '500',
  },
});
