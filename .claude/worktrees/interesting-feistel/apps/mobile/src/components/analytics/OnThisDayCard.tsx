import React, { useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { calculateBrzycki1RM } from '../../utils/calculations';
import { getLocalDateString } from '../../utils/dateUtils';

type ComparisonSet = { weight: number; reps: number; date: string };

function formatWeight(weight: number, unit: string) {
  return `${Math.round(weight * 10) / 10}${unit}`;
}

export function OnThisDayCard() {
  const colors = useColors();
  const history = useWorkoutStore(state => state.history);
  const settings = useSettingsStore(state => state.summary ?? state.getSettings());

  const comparison = useMemo(() => {
    if (history.length < 2) return null;

    const today = new Date();
    const oneYearAgo = new Date(today);
    oneYearAgo.setFullYear(today.getFullYear() - 1);
    const oneYearAgoKey = getLocalDateString(oneYearAgo);
    const logFromLastYear = [...history].find(log => log.date.startsWith(oneYearAgoKey));
    if (!logFromLastYear) return null;

    const keyLifts = ['press de banca', 'bench', 'sentadilla', 'squat', 'peso muerto', 'deadlift'];
    const exercises = logFromLastYear.completedExercises || [];
    let keyExercise = exercises.find(ex => keyLifts.some(lift => ex.exerciseName.toLowerCase().includes(lift)));
    if (!keyExercise) keyExercise = exercises[0];
    if (!keyExercise) return null;

    const bestSetLastYear = keyExercise.sets.reduce<ComparisonSet | null>((best, current) => {
      const bestE1rm = best ? calculateBrzycki1RM(best.weight || 0, best.reps || 0) : -1;
      const currentE1rm = calculateBrzycki1RM(current.weight || 0, current.completedReps || current.reps || 0);
      return currentE1rm > bestE1rm
        ? {
            weight: current.weight || 0,
            reps: current.completedReps || current.reps || 0,
            date: logFromLastYear.date,
          }
        : best;
    }, null);

    if (!bestSetLastYear) return null;

    const baselineE1rm = calculateBrzycki1RM(bestSetLastYear.weight, bestSetLastYear.reps);
    let recentPR: { weight: number; reps: number; e1rm: number; date: string } | null = null;

    history.forEach(log => {
      const exercise = (log.completedExercises || []).find(ex =>
        ex.exerciseId === keyExercise?.exerciseId || ex.exerciseName === keyExercise?.exerciseName,
      );
      if (!exercise) return;

      exercise.sets.forEach(set => {
        const e1rm = calculateBrzycki1RM(set.weight || 0, set.completedReps || set.reps || 0);
        if (!recentPR || e1rm > recentPR.e1rm) {
          recentPR = {
            weight: set.weight || 0,
            reps: set.completedReps || set.reps || 0,
            e1rm,
            date: log.date,
          };
        }
      });
    });

    if (!recentPR || recentPR.e1rm <= baselineE1rm) return null;

    const improvement = recentPR.e1rm - baselineE1rm;
    const message =
      improvement >= 20
        ? 'Tu progreso de este año es brutal. La tendencia va clara hacia arriba.'
        : improvement >= 10
          ? 'Has mejorado con constancia. Sigue empujando ese patrón.'
          : 'Pequeñas mejoras sostenidas siguen sumando. Mantén la línea.';

    return {
      exerciseName: keyExercise.exerciseName,
      oneYearAgoSet: bestSetLastYear,
      recentPRSet: recentPR,
      message,
    };
  }, [history]);

  if (!comparison) return null;

  const weightUnit = settings?.weightUnit || 'kg';

  return (
    <LiquidGlassCard style={styles.card} padding={20}>
      <Text style={[styles.title, { color: colors.onSurface }]}>En Este Día...</Text>
      <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
        Un vistazo a tu progreso del último año en {comparison.exerciseName}.
      </Text>

      <View style={styles.grid}>
        <View style={[styles.miniCard, { backgroundColor: colors.surfaceContainer }]}>
          <Text style={[styles.miniDate, { color: colors.onSurfaceVariant }]}>
            {new Date(comparison.oneYearAgoSet.date).toLocaleDateString('es-CL')}
          </Text>
          <Text style={[styles.miniValue, { color: colors.onSurface }]}>
            {formatWeight(comparison.oneYearAgoSet.weight, weightUnit)}
          </Text>
          <Text style={[styles.miniReps, { color: colors.onSurfaceVariant }]}>
            x {comparison.oneYearAgoSet.reps} reps
          </Text>
        </View>

        <View style={[styles.miniCard, { backgroundColor: `${colors.primary}22`, borderColor: `${colors.primary}55` }]}>
          <Text style={[styles.miniDate, { color: colors.onSurfaceVariant }]}>
            {new Date(comparison.recentPRSet.date).toLocaleDateString('es-CL')}
          </Text>
          <Text style={[styles.miniValue, { color: colors.onSurface }]}>
            {formatWeight(comparison.recentPRSet.weight, weightUnit)}
          </Text>
          <Text style={[styles.miniReps, { color: colors.onSurfaceVariant }]}>
            x {comparison.recentPRSet.reps} reps
          </Text>
        </View>
      </View>

      <Text style={[styles.message, { color: colors.onSurfaceVariant }]}>
        {comparison.message}
      </Text>
    </LiquidGlassCard>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 28,
    marginBottom: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: '900',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 13,
    lineHeight: 20,
    marginBottom: 16,
  },
  grid: {
    flexDirection: 'row',
    gap: 12,
  },
  miniCard: {
    flex: 1,
    borderRadius: 18,
    padding: 14,
    borderWidth: 1,
  },
  miniDate: {
    fontSize: 10,
    fontWeight: '700',
    marginBottom: 8,
  },
  miniValue: {
    fontSize: 28,
    fontWeight: '900',
  },
  miniReps: {
    marginTop: 4,
    fontSize: 12,
    fontWeight: '600',
  },
  message: {
    marginTop: 14,
    fontSize: 13,
    fontStyle: 'italic',
    lineHeight: 20,
    textAlign: 'center',
  },
});

export default OnThisDayCard;
