import React, { useEffect, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { InfoIcon } from '../icons';
import { useWorkoutStore } from '../../stores/workoutStore';

interface RiskAlert {
  discomfort: string;
  exercise: string;
}

export const InjuryRiskAlerts = () => {
  const colors = useColors();
  const history = useWorkoutStore(state => state.history);
  const status = useWorkoutStore(state => state.status);
  const hydrateWorkout = useWorkoutStore(state => state.hydrateFromMigration);

  useEffect(() => {
    if (status === 'idle') void hydrateWorkout();
  }, [hydrateWorkout, status]);

  const alerts = useMemo<RiskAlert[]>(() => {
    if (history.length < 3) return [];

    const discomfortMap = new Map<string, { exercises: string[]; count: number }>();

    history.forEach(log => {
      const discomforts = Array.isArray(log.discomforts) ? log.discomforts : [];
      if (discomforts.length === 0) return;

      const exerciseNames = (log.completedExercises || []).map(ex => ex.exerciseName).filter(Boolean);
      discomforts.forEach(discomfort => {
        const entry = discomfortMap.get(discomfort) || { exercises: [], count: 0 };
        entry.count += 1;
        exerciseNames.forEach(exName => {
          if (!entry.exercises.includes(exName)) {
            entry.exercises.push(exName);
          }
        });
        discomfortMap.set(discomfort, entry);
      });
    });

    return Array.from(discomfortMap.entries())
      .filter(([, data]) => data.count > 1)
      .map(([discomfort, data]) => ({
        discomfort,
        exercise: data.exercises[0] || 'tu sesión',
      }))
      .slice(0, 5);
  }, [history]);

  return (
    <LiquidGlassCard style={styles.card}>
      <View style={styles.header}>
        <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Alertas de Riesgo</Text>
        <View style={[styles.alertBadge, { backgroundColor: `${colors.error}20` }]}>
          <Text style={[styles.alertBadgeText, { color: colors.error }]}>{alerts.length}</Text>
        </View>
      </View>

      {alerts.length > 0 ? (
        <ScrollView showsVerticalScrollIndicator={false} style={styles.alertsContainer}>
          {alerts.map((alert, index) => (
            <View key={`${alert.discomfort}-${index}`} style={[styles.alertItem, { backgroundColor: `${colors.surfaceContainer}33` }]}>
              <View style={styles.alertHeader}>
                <InfoIcon size={16} color={colors.tertiary} />
                <Text style={[styles.alertTitle, { color: colors.onSurface }]} numberOfLines={1}>
                  {alert.discomfort}
                </Text>
              </View>
              <Text style={[styles.alertDescription, { color: colors.onSurfaceVariant }]}>
                Has reportado esta molestia repetidamente. Revisa la técnica o reduce la carga en ejercicios como{' '}
                <Text style={styles.alertExercise}>{alert.exercise}</Text>.
              </Text>
            </View>
          ))}
        </ScrollView>
      ) : (
        <View style={styles.emptyState}>
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
            No se han detectado patrones de riesgo significativos en tu historial reciente.
          </Text>
          <Text style={[styles.emptySubtext, { color: colors.onSurfaceVariant }]}>
            Sigue registrando molestias y carga de entrenamiento para detectar correlaciones útiles.
          </Text>
        </View>
      )}
    </LiquidGlassCard>
  );
};

const styles = StyleSheet.create({
  card: {
    padding: 20,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  alertBadge: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 9999,
  },
  alertBadgeText: {
    fontSize: 12,
    fontWeight: '700',
  },
  alertsContainer: {
    maxHeight: 320,
  },
  alertItem: {
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
  },
  alertHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 8,
  },
  alertTitle: {
    fontSize: 14,
    fontWeight: '700',
    flex: 1,
  },
  alertDescription: {
    fontSize: 12,
    lineHeight: 18,
  },
  alertExercise: {
    fontWeight: '800',
    color: '#fff',
  },
  emptyState: {
    paddingVertical: 16,
  },
  emptyText: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 12,
    lineHeight: 18,
  },
});
