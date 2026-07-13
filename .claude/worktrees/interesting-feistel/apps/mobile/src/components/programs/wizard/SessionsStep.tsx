import React, { useState } from 'react';
import { View, Text, StyleSheet, TextInput, TouchableOpacity, ScrollView } from 'react-native';
import { useColors } from '../../../theme';
import { Exercise, Session } from '../../../types/workout';
import { generateId } from '../../../utils/generateId';
import { AdvancedExercisePicker } from '../../exercise/AdvancedExercisePicker';

interface SessionsStepProps {
  trainingDays: number[];
  sessions: Session[];
  onSessionsChange: (sessions: Session[]) => void;
}

const SessionsStep: React.FC<SessionsStepProps> = ({
  trainingDays,
  sessions,
  onSessionsChange,
}) => {
  const colors = useColors();
  const [showPicker, setShowPicker] = useState(false);
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);

  // Initialize sessions if empty
  React.useEffect(() => {
    if (sessions.length === 0 && trainingDays.length > 0) {
      const newSessions: Session[] = trainingDays.map((day, index) => ({
        id: generateId(),
        name: `Sesión ${index + 1}`,
        description: '',
        focus: '',
        exercises: [],
        dayOfWeek: day,
      }));
      onSessionsChange(newSessions);
    }
  }, [trainingDays]);

  const updateSessionName = (sessionId: string, name: string) => {
    onSessionsChange(
      sessions.map(s => (s.id === sessionId ? { ...s, name } : s))
    );
  };

  const handleAddExercise = (session: Session, exercise: Exercise) => {
    onSessionsChange(
      sessions.map(s => (s.id === session.id ? { ...s, exercises: [...s.exercises, exercise] } : s))
    );
  };

  const handleRemoveExercise = (sessionId: string, exerciseId: string) => {
    onSessionsChange(
      sessions.map(s =>
        s.id === sessionId
          ? { ...s, exercises: s.exercises.filter(e => e.id !== exerciseId) }
          : s
      )
    );
  };

  const openPicker = (sessionId: string) => {
    setSelectedSessionId(sessionId);
    setShowPicker(true);
  };

  const closePicker = () => {
    setShowPicker(false);
    setSelectedSessionId(null);
  };

  const calculateMuscleVolume = (session: Session) => {
    const volume: Record<string, number> = {};
    session.exercises.forEach(ex => {
      const totalSets = ex.sets.length;
      const muscles = ex.targetSessionGoal?.split(',').map(m => m.trim()) || [];
      muscles.forEach(muscle => {
        volume[muscle] = (volume[muscle] || 0) + totalSets;
      });
    });
    return volume;
  };

  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      <Text style={[styles.title, { color: colors.onSurface }]}>
        Sesiones del Programa
      </Text>
      <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
        Asigna ejercicios a cada sesión
      </Text>

      {sessions.map(session => {
        const muscleVolume = calculateMuscleVolume(session);
        return (
          <View key={session.id} style={[styles.sessionCard, { backgroundColor: colors.surfaceContainer }]}>
            <View style={styles.sessionHeader}>
              <TextInput
                value={session.name}
                onChangeText={(text) => updateSessionName(session.id, text)}
                style={[styles.sessionNameInput, { color: colors.onSurface }]}
              />
              <Text style={[styles.dayLabel, { color: colors.primary }]}>
                {['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'][session.dayOfWeek || 0]}
              </Text>
            </View>

            {session.exercises.length === 0 ? (
              <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
                No hay ejercicios asignados
              </Text>
            ) : (
              <View style={styles.exercisesList}>
                {session.exercises.map(exercise => (
                  <View key={exercise.id} style={styles.exerciseItem}>
                    <View style={styles.exerciseInfo}>
                      <Text style={[styles.exerciseName, { color: colors.onSurface }]}>
                        {exercise.name}
                      </Text>
                      <Text style={[styles.exerciseDetails, { color: colors.onSurfaceVariant }]}>
                        {exercise.sets.length} series
                      </Text>
                    </View>
                    <TouchableOpacity onPress={() => handleRemoveExercise(session.id, exercise.id)}>
                      <Text style={[styles.removeText, { color: colors.error }]}>×</Text>
                    </TouchableOpacity>
                  </View>
                ))}
              </View>
            )}

            <TouchableOpacity
              style={[styles.addButton, { backgroundColor: colors.primaryContainer }]}
              onPress={() => openPicker(session.id)}
            >
              <Text style={[styles.addButtonText, { color: colors.onPrimaryContainer }]}>
                + Añadir Ejercicio
              </Text>
            </TouchableOpacity>

            {Object.keys(muscleVolume).length > 0 && (
              <View style={styles.volumeSummary}>
                <Text style={[styles.volumeLabel, { color: colors.onSurfaceVariant }]}>
                  Volumen por músculo:
                </Text>
                <View style={styles.muscleChips}>
                  {Object.entries(muscleVolume).map(([muscle, volume]) => (
                    <View key={muscle} style={[styles.muscleChip, { backgroundColor: colors.surfaceVariant }]}>
                      <Text style={[styles.muscleText, { color: colors.onSurface }]}>
                        {muscle} × {volume}
                      </Text>
                    </View>
                  ))}
                </View>
              </View>
            )}
          </View>
        );
      })}

      <AdvancedExercisePicker
        visible={showPicker}
        onClose={closePicker}
        onSelect={(exercise) => {
          const session = sessions.find(s => s.id === selectedSessionId);
          if (session) {
            handleAddExercise(session, {
              id: generateId(),
              name: exercise.name,
              exerciseDbId: exercise.id,
              sets: [
                {
                  id: generateId(),
                  targetReps: 10,
                  weight: 0,
                  targetRPE: 7,
                },
              ],
            });
          }
          closePicker();
        }}
        onCreateNew={() => {
          // Implementar creación de nuevo ejercicio si es necesario
          closePicker();
        }}
      />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  title: {
    fontSize: 22,
    fontWeight: '900',
    marginBottom: 8,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    textAlign: 'center',
    marginBottom: 24,
    opacity: 0.7,
  },
  sessionCard: {
    padding: 16,
    borderRadius: 20,
    marginBottom: 16,
  },
  sessionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  sessionNameInput: {
    fontSize: 16,
    fontWeight: '900',
    flex: 1,
  },
  dayLabel: {
    fontSize: 12,
    fontWeight: '900',
  },
  emptyText: {
    fontSize: 13,
    fontStyle: 'italic',
    marginBottom: 12,
  },
  exercisesList: {
    gap: 8,
    marginBottom: 12,
  },
  exerciseItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  exerciseInfo: {
    flex: 1,
  },
  exerciseName: {
    fontSize: 14,
    fontWeight: '700',
  },
  exerciseDetails: {
    fontSize: 12,
  },
  removeText: {
    fontSize: 20,
    fontWeight: '900',
  },
  addButton: {
    padding: 12,
    borderRadius: 12,
    alignItems: 'center',
    marginBottom: 12,
  },
  addButtonText: {
    fontSize: 12,
    fontWeight: '900',
  },
  volumeSummary: {
    marginTop: 8,
  },
  volumeLabel: {
    fontSize: 11,
    fontWeight: '700',
    marginBottom: 6,
  },
  muscleChips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  muscleChip: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  muscleText: {
    fontSize: 11,
    fontWeight: '700',
  },
});

export default SessionsStep;