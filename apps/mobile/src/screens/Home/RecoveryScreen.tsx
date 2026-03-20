import React, { useEffect } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ScreenShell } from '@/components/ScreenShell';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { AugeDashboard } from '@/components/auge/AugeDashboard';
import { WeeklyFatigueCard } from '@/components/analytics/WeeklyFatigueCard';
import { MuscleRecoveryWidget } from '@/components/analytics/MuscleRecoveryWidget';
import { useColors } from '@/theme';
import { useExerciseStore } from '@/stores/exerciseStore';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useWorkoutStore } from '@/stores/workoutStore';

function SnapshotCard() {
  const colors = useColors();
  const overview = useWorkoutStore(state => state.overview);
  const latestFeedback = useWorkoutStore(state => state.latestPostSessionFeedback);
  const wellbeingOverview = useWellbeingStore(state => state.overview);
  const battery = overview?.battery;

  return (
    <LiquidGlassCard style={styles.snapshotCard} padding={20}>
      <Text style={[styles.cardEyebrow, { color: colors.onSurfaceVariant }]}>Lectura rapida</Text>
      <View style={styles.snapshotGrid}>
        <View style={styles.snapshotMetric}>
          <Text style={[styles.snapshotValue, { color: colors.onSurface }]}>
            {battery ? `${Math.round(battery.overall)}%` : '--'}
          </Text>
          <Text style={[styles.snapshotLabel, { color: colors.onSurfaceVariant }]}>Readiness</Text>
        </View>
        <View style={styles.snapshotMetric}>
          <Text style={[styles.snapshotValue, { color: colors.onSurface }]}>
            {latestFeedback ? `${latestFeedback.sessionRpe}/10` : '--'}
          </Text>
          <Text style={[styles.snapshotLabel, { color: colors.onSurfaceVariant }]}>Ultimo RPE</Text>
        </View>
        <View style={styles.snapshotMetric}>
          <Text style={[styles.snapshotValue, { color: colors.onSurface }]}>
            {wellbeingOverview?.averageSleepHoursLast7Days
              ? `${wellbeingOverview.averageSleepHoursLast7Days}h`
              : '--'}
          </Text>
          <Text style={[styles.snapshotLabel, { color: colors.onSurfaceVariant }]}>Sueno 7d</Text>
        </View>
      </View>

      <Text style={[styles.snapshotCopy, { color: colors.onSurfaceVariant }]}>
        {battery?.source
          ? `Fuente del estado actual: ${battery.source}.`
          : 'La bateria AUGE se actualizara a medida que registres entrenos, sueno y feedback post sesion.'}
      </Text>
    </LiquidGlassCard>
  );
}

export function RecoveryScreen() {
  const workoutStatus = useWorkoutStore(state => state.status);
  const hydrateWorkout = useWorkoutStore(state => state.hydrateFromMigration);
  const wellbeingStatus = useWellbeingStore(state => state.status);
  const hydrateWellbeing = useWellbeingStore(state => state.hydrateFromMigration);
  const exerciseStatus = useExerciseStore(state => state.status);
  const hydrateExercises = useExerciseStore(state => state.hydrateFromMigration);

  useEffect(() => {
    if (workoutStatus === 'idle') void hydrateWorkout();
    if (wellbeingStatus === 'idle') void hydrateWellbeing();
    if (exerciseStatus === 'idle') void hydrateExercises();
  }, [
    exerciseStatus,
    hydrateExercises,
    hydrateWellbeing,
    hydrateWorkout,
    wellbeingStatus,
    workoutStatus,
  ]);

  return (
    <ScreenShell
      title="Recuperacion"
      subtitle="Fatiga sistemica, readiness y respuesta muscular del estado actual."
    >
      <View style={styles.container}>
        <SnapshotCard />
        <AugeDashboard />
        <WeeklyFatigueCard />
        <MuscleRecoveryWidget />
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  snapshotCard: {
    borderRadius: 28,
  },
  cardEyebrow: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.6,
    marginBottom: 14,
  },
  snapshotGrid: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 14,
  },
  snapshotMetric: {
    flex: 1,
  },
  snapshotValue: {
    fontSize: 28,
    fontWeight: '900',
  },
  snapshotLabel: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  snapshotCopy: {
    fontSize: 13,
    lineHeight: 20,
  },
});
