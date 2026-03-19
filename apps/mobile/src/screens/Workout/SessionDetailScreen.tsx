import React, { useMemo, useState } from 'react';
import { Text, View, ScrollView, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '../../components/ScreenShell';
import { Button } from '../../components/ui';
import { StartWorkoutModal } from '../../components/workout/StartWorkoutModal';
import { useProgramStore } from '../../stores/programStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import type { Program, Session, Exercise, ExerciseSet } from '../../types/workout';
import { WorkoutStackParamList } from '../../navigation/types';
import { useColors } from '../../theme';
import { getSessionExercises, getSessionSetCount } from '../../utils/workoutSession';

type WorkoutNav = NativeStackNavigationProp<WorkoutStackParamList>;

interface SessionContext {
  program: Program;
  session: Session;
  weekId: string;
}

function findSessionContext(programs: Program[], sessionId: string): SessionContext | null {
  for (const program of programs) {
    const macrocycles = program.macrocycles || [];
    for (const macro of macrocycles) {
      const blocks = macro.blocks || [];
      for (const block of blocks) {
        const mesocycles = block.mesocycles || [];
        for (const meso of mesocycles) {
          const weeks = meso.weeks || [];
          for (const week of weeks) {
            const session = (week.sessions || []).find(item => item.id === sessionId);
            if (session) {
              return { program, session, weekId: week.id };
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
  const navigation = useNavigation<WorkoutNav>();
  const programs = useProgramStore(state => state.programs);
  const startActiveSession = useWorkoutStore(state => state.startActiveSession);
  const { sessionId } = route.params;
  const [startVisible, setStartVisible] = useState(false);

  const context = useMemo(() => findSessionContext(programs, sessionId), [programs, sessionId]);

  if (!context) {
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

  const { session, program, weekId } = context;
  const exercises: Exercise[] = getSessionExercises(session);
  const sessionSetCount = getSessionSetCount(session);

  return (
    <ScreenShell title={session.name || 'Detalle de Sesión'}>
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={[styles.card, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.focusTitle, { color: colors.onSurface }]}>
            {program.name}
          </Text>
          <Text style={[styles.description, { color: colors.onSurfaceVariant }]}>
            {session.focus || 'Foco general'} · {exercises.length} ejercicios · {sessionSetCount} sets
          </Text>
          {session.description ? (
            <Text style={[styles.description, { color: colors.onSurfaceVariant }]}>{session.description}</Text>
          ) : null}
        </View>

        <View style={[styles.card, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Acciones rápidas</Text>
          <View style={styles.actions}>
            <Button variant="primary" onPress={() => setStartVisible(true)}>
              Iniciar sesión
            </Button>
            <Button
              variant="secondary"
              onPress={() =>
                navigation.navigate('SessionEditor', {
                  programId: program.id,
                  weekId,
                  sessionId: session.id,
                })
              }
            >
              Editar sesión
            </Button>
            <Button variant="secondary" onPress={() => navigation.navigate('LogWorkout')}>
              Registrar entreno manual
            </Button>
          </View>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Ejercicios planeados</Text>
          {exercises.map((ex: Exercise, index: number) => (
            <View
              key={ex.id || index}
              style={[
                styles.exerciseCard,
                {
                  backgroundColor: colors.surfaceContainer,
                  borderColor: colors.outlineVariant,
                },
              ]}
            >
              <Text style={[styles.exerciseName, { color: colors.onSurface }]}>
                {ex.name || 'Ejercicio sin nombre'}
              </Text>
              {ex.notes ? (
                <Text style={[styles.notes, { color: colors.onSurfaceVariant }]}>{ex.notes}</Text>
              ) : null}

              <View style={[styles.setsContainer, { borderTopColor: colors.outlineVariant }]}>
                {(ex.sets || []).map((set: ExerciseSet, setIndex: number) => (
                  <View key={set.id || setIndex} style={styles.setRow}>
                    <Text style={[styles.setText, { color: colors.onSurfaceVariant }]}>Set {setIndex + 1}:</Text>
                    <Text style={[styles.targetText, { color: colors.onSurface }]}>
                      {set.targetReps || set.completedReps || '0'} reps
                      {set.weight ? ` @ ${set.weight}kg` : ''}
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

      <StartWorkoutModal
        visible={startVisible}
        session={session}
        onClose={() => setStartVisible(false)}
        onStart={payload => {
          startActiveSession({ programId: program.id, session: payload.session });
          setStartVisible(false);
          navigation.navigate('ActiveSession', {
            programId: program.id,
            sessionId: payload.session.id,
            sessionName: payload.session.name,
          });
        }}
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
    gap: 12,
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
    marginTop: 4,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 12,
    textTransform: 'uppercase',
    letterSpacing: 1.6,
  },
  actions: {
    gap: 10,
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
