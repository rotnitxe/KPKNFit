import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useColors } from '../../../theme';
import { ProgramTemplateOption, SplitTemplate, Session } from '../../../types/workout';

interface PreviewStepProps {
  programName: string;
  programDescription: string;
  selectedTemplate: ProgramTemplateOption | null;
  selectedSplit: SplitTemplate | null;
  trainingDays: number[];
  sessions: Session[];
  onCreateProgram: () => void;
}

const PreviewStep: React.FC<PreviewStepProps> = ({
  programName,
  programDescription,
  selectedTemplate,
  selectedSplit,
  trainingDays,
  sessions,
  onCreateProgram,
}) => {
  const colors = useColors();

  const totalExercises = sessions.reduce((sum, s) => sum + s.exercises.length, 0);
  const totalSets = sessions.reduce(
    (sum, s) => sum + s.exercises.reduce((eSum, e) => eSum + e.sets.length, 0),
    0
  );

  // Calculate muscle volume
  const muscleVolume: Record<string, number> = {};
  sessions.forEach(session => {
    session.exercises.forEach(exercise => {
      const sets = exercise.sets.length;
      // For simplicity, we'll use targetSessionGoal or default to 'General'
      // In a real app, we'd parse the exercise's muscle involvement
      const muscle = exercise.targetSessionGoal || 'General';
      muscleVolume[muscle] = (muscleVolume[muscle] || 0) + sets;
    });
  });

  const daysOfWeek = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      <Text style={[styles.title, { color: colors.onSurface }]}>
        Resumen del Programa
      </Text>
      <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
        Revisa los detalles antes de crear tu programa
      </Text>

      {/* Program Info */}
      <View style={[styles.card, { backgroundColor: colors.surfaceContainer }]}>
        <Text style={[styles.cardTitle, { color: colors.onSurface }]}>Información</Text>
        <View style={styles.infoRow}>
          <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Nombre:</Text>
          <Text style={[styles.value, { color: colors.onSurface }]}>{programName}</Text>
        </View>
        <View style={styles.infoRow}>
          <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Tipo:</Text>
          <Text style={[styles.value, { color: colors.onSurface }]}>
            {selectedTemplate?.name || 'No seleccionado'}
          </Text>
        </View>
        <View style={styles.infoRow}>
          <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Semanas:</Text>
          <Text style={[styles.value, { color: colors.onSurface }]}>
            {selectedTemplate?.weeks || 0}
          </Text>
        </View>
        <View style={styles.infoRow}>
          <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>Descripción:</Text>
          <Text style={[styles.value, { color: colors.onSurface }]}>
            {programDescription || 'Sin descripción'}
          </Text>
        </View>
      </View>

      {/* Split Visual */}
      <View style={[styles.card, { backgroundColor: colors.surfaceContainer }]}>
        <Text style={[styles.cardTitle, { color: colors.onSurface }]}>Split Semanal</Text>
        {selectedSplit ? (
          <View style={styles.splitGrid}>
            {selectedSplit.pattern.map((day, index) => {
              const dayOfWeek = daysOfWeek[(trainingDays[0] || 0 + index) % 7];
              const isRest = day.toLowerCase() === 'descanso';
              return (
                <View key={index} style={styles.dayCell}>
                  <Text style={[styles.dayLabel, { color: colors.onSurfaceVariant }]}>
                    {dayOfWeek}
                  </Text>
                  <View
                    style={[
                      styles.daySession,
                      {
                        backgroundColor: isRest ? colors.surfaceVariant : colors.primaryContainer,
                      },
                    ]}
                  >
                    <Text
                      style={[
                        styles.daySessionText,
                        {
                          color: isRest ? colors.onSurfaceVariant : colors.onPrimaryContainer,
                        },
                      ]}
                    >
                      {isRest ? 'Descanso' : day}
                    </Text>
                  </View>
                </View>
              );
            })}
          </View>
        ) : (
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
            No se ha seleccionado split
          </Text>
        )}
      </View>

      {/* Stats */}
      <View style={[styles.card, { backgroundColor: colors.surfaceContainer }]}>
        <Text style={[styles.cardTitle, { color: colors.onSurface }]}>Estadísticas</Text>
        <View style={styles.statsRow}>
          <View style={styles.statItem}>
            <Text style={[styles.statValue, { color: colors.primary }]}>
              {totalExercises}
            </Text>
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>
              Ejercicios
            </Text>
          </View>
          <View style={styles.statItem}>
            <Text style={[styles.statValue, { color: colors.primary }]}>
              {totalSets}
            </Text>
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>
              Series
            </Text>
          </View>
          <View style={styles.statItem}>
            <Text style={[styles.statValue, { color: colors.primary }]}>
              {Object.keys(muscleVolume).length}
            </Text>
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>
              Músculos
            </Text>
          </View>
        </View>
      </View>

      {/* Muscle Volume */}
      <View style={[styles.card, { backgroundColor: colors.surfaceContainer }]}>
        <Text style={[styles.cardTitle, { color: colors.onSurface }]}>Volumen por Músculo</Text>
        <View style={styles.muscleBars}>
          {Object.entries(muscleVolume).map(([muscle, volume]) => (
            <View key={muscle} style={styles.muscleBarContainer}>
              <Text style={[styles.muscleLabel, { color: colors.onSurface }]}>
                {muscle}
              </Text>
              <View style={[styles.muscleBar, { backgroundColor: colors.surfaceVariant }]}>
                <View
                  style={[
                    styles.muscleBarFill,
                    {
                      width: `${Math.min((volume / totalSets) * 100, 100)}%`,
                      backgroundColor: colors.primary,
                    },
                  ]}
                />
              </View>
              <Text style={[styles.muscleVolume, { color: colors.onSurfaceVariant }]}>
                {volume} series
              </Text>
            </View>
          ))}
        </View>
      </View>

      {/* Create Button */}
      <TouchableOpacity
        style={[styles.createButton, { backgroundColor: colors.primary }]}
        onPress={onCreateProgram}
        disabled={!selectedTemplate || !selectedSplit}
      >
        <Text style={[styles.createButtonText, { color: colors.onPrimary }]}>
          CREAR PROGRAMA
        </Text>
      </TouchableOpacity>
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
  card: {
    padding: 16,
    borderRadius: 20,
    marginBottom: 16,
  },
  cardTitle: {
    fontSize: 14,
    fontWeight: '900',
    marginBottom: 12,
  },
  infoRow: {
    flexDirection: 'row',
    marginBottom: 8,
  },
  label: {
    width: 80,
    fontSize: 13,
    fontWeight: '700',
  },
  value: {
    flex: 1,
    fontSize: 13,
  },
  splitGrid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  dayCell: {
    alignItems: 'center',
    flex: 1,
  },
  dayLabel: {
    fontSize: 10,
    fontWeight: '700',
    marginBottom: 4,
  },
  daySession: {
    paddingVertical: 8,
    paddingHorizontal: 4,
    borderRadius: 8,
    alignItems: 'center',
    flex: 1,
    width: '100%',
  },
  daySessionText: {
    fontSize: 10,
    fontWeight: '700',
    textAlign: 'center',
  },
  emptyText: {
    fontSize: 13,
    fontStyle: 'italic',
    textAlign: 'center',
    paddingVertical: 20,
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  statItem: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: 24,
    fontWeight: '900',
  },
  statLabel: {
    fontSize: 12,
    fontWeight: '700',
  },
  muscleBars: {
    gap: 12,
  },
  muscleBarContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  muscleLabel: {
    width: 80,
    fontSize: 12,
    fontWeight: '700',
  },
  muscleBar: {
    flex: 1,
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
  },
  muscleBarFill: {
    height: '100%',
    borderRadius: 4,
  },
  muscleVolume: {
    width: 50,
    fontSize: 11,
    fontWeight: '700',
    textAlign: 'right',
  },
  createButton: {
    padding: 16,
    borderRadius: 16,
    alignItems: 'center',
    marginTop: 16,
  },
  createButtonText: {
    fontSize: 14,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
});

export default PreviewStep;