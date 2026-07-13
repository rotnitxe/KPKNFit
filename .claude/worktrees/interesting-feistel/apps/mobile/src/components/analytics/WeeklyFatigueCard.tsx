import React, { useEffect, useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { calculateCompletedSessionStress } from '../../services/fatigueService';
import { calculateHistoricalFatigueData } from '../../services/analysisService';
import { calculateSystemicFatigue } from '../../services/recoveryService';
import { readStoredSettingsRaw, readStoredWellbeingPayload } from '../../services/mobileDomainStateService';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';
import { useWorkoutStore } from '../../stores/workoutStore';

const WEEK_DAYS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

const getDayKey = (date: Date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const getCapacityColor = (colors: ReturnType<typeof useColors>, capacity: number) => {
  if (capacity >= 80) return colors.primary;
  if (capacity >= 60) return colors.tertiary;
  return colors.error;
};

export const WeeklyFatigueCard = () => {
  const colors = useColors();
  const history = useWorkoutStore(state => state.history);
  const workoutStatus = useWorkoutStore(state => state.status);
  const hydrateWorkout = useWorkoutStore(state => state.hydrateFromMigration);

  const exerciseList = useExerciseStore(state => state.exerciseList);
  const exerciseStatus = useExerciseStore(state => state.status);
  const hydrateExercises = useExerciseStore(state => state.hydrateFromMigration);

  const wellbeingOverview = useWellbeingStore(state => state.overview);
  const wellbeingStatus = useWellbeingStore(state => state.status);
  const hydrateWellbeing = useWellbeingStore(state => state.hydrateFromMigration);

  useEffect(() => {
    if (workoutStatus === 'idle') void hydrateWorkout();
    if (exerciseStatus === 'idle') void hydrateExercises();
    if (wellbeingStatus === 'idle') void hydrateWellbeing();
  }, [exerciseStatus, hydrateExercises, hydrateWellbeing, wellbeingStatus, hydrateWorkout, workoutStatus]);

  const wellbeingPayload = readStoredWellbeingPayload();
  const settings = readStoredSettingsRaw() as any;

  const historicalData = useMemo(() => {
    return calculateHistoricalFatigueData(history, settings as any, exerciseList, {
      sleepLogs: wellbeingPayload.sleepLogs as any,
      dailyWellbeingLogs: wellbeingPayload.dailyWellbeingLogs as any,
    });
  }, [exerciseList, history, settings, wellbeingPayload.dailyWellbeingLogs, wellbeingPayload.sleepLogs]);

  const currentWeekData = historicalData[historicalData.length - 1] ?? null;

  const systemicFatigue = useMemo(() => {
    return calculateSystemicFatigue(
      history,
      wellbeingPayload.sleepLogs as any,
      wellbeingPayload.dailyWellbeingLogs as any,
      exerciseList,
      settings as any,
    );
  }, [exerciseList, history, settings, wellbeingPayload.dailyWellbeingLogs, wellbeingPayload.sleepLogs]);

  const weeklyData = useMemo(() => {
    const now = new Date();
    const startOfWeek = new Date(now);
    startOfWeek.setDate(now.getDate() - now.getDay() + 1);

    return WEEK_DAYS.map((day, idx) => {
      const dayDate = new Date(startOfWeek);
      dayDate.setDate(startOfWeek.getDate() + idx);
      const dayKey = getDayKey(dayDate);
      const dayLogs = history.filter(log => log.date.slice(0, 10) === dayKey);
      const fatigueLevel = dayLogs.reduce(
        (sum, log) => sum + calculateCompletedSessionStress(log.completedExercises, exerciseList),
        0,
      );
      const fatigue = Math.min(100, Math.round(fatigueLevel));
      const capacity = Math.max(0, 100 - fatigue);

      return {
        day,
        fatigue,
        capacity,
      };
    });
  }, [exerciseList, history]);

  const currentCapacity = systemicFatigue.total;
  const capacityPercent = Math.min(currentCapacity, 100);

  if (!currentWeekData) {
    return (
      <LiquidGlassCard style={styles.card}>
        <View style={styles.header}>
          <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Fatiga Semanal</Text>
          <Text style={[styles.headerSubtitle, { color: colors.onSurfaceVariant }]}>Datos insuficientes</Text>
        </View>
        <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
          Completa más entrenamientos para ver tu ACWR, tonelaje y capacidad real.
        </Text>
      </LiquidGlassCard>
    );
  }

  return (
    <LiquidGlassCard style={styles.card}>
      <View style={styles.header}>
        <View>
          <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Fatiga Semanal</Text>
          <Text style={[styles.headerSubtitle, { color: colors.onSurfaceVariant }]}>
            ACWR {currentWeekData.acwr.toFixed(2)} · Capacidad {capacityPercent.toFixed(0)}%
          </Text>
        </View>
        <View style={[styles.badge, { backgroundColor: `${getCapacityColor(colors, currentCapacity)}20` }]}>
          <Text style={[styles.badgeText, { color: getCapacityColor(colors, currentCapacity) }]}>
            {currentCapacity >= 80 ? 'Óptimo' : currentCapacity >= 60 ? 'Moderado' : 'Bajo'}
          </Text>
        </View>
      </View>

      <View style={styles.summaryGrid}>
        <View style={styles.summaryCard}>
          <Text style={[styles.summaryLabel, { color: colors.onSurfaceVariant }]}>Carga aguda</Text>
          <Text style={[styles.summaryValue, { color: colors.onSurface }]}>{currentWeekData.acuteLoad}</Text>
        </View>
        <View style={styles.summaryCard}>
          <Text style={[styles.summaryLabel, { color: colors.onSurfaceVariant }]}>Carga crónica</Text>
          <Text style={[styles.summaryValue, { color: colors.onSurface }]}>{currentWeekData.chronicLoad}</Text>
        </View>
        <View style={styles.summaryCard}>
          <Text style={[styles.summaryLabel, { color: colors.onSurfaceVariant }]}>Tonelaje</Text>
          <Text style={[styles.summaryValue, { color: colors.onSurface }]}>{currentWeekData.tonnage.toLocaleString('es-CL')}</Text>
        </View>
      </View>

      <View style={styles.secondaryRow}>
        <View style={styles.secondaryCard}>
          <Text style={[styles.secondaryLabel, { color: colors.onSurfaceVariant }]}>IMR media</Text>
          <Text style={[styles.secondaryValue, { color: colors.primary }]}>{currentWeekData.avgRMI}%</Text>
        </View>
        <View style={styles.secondaryCard}>
          <Text style={[styles.secondaryLabel, { color: colors.onSurfaceVariant }]}>Sueño</Text>
          <Text style={[styles.secondaryValue, { color: colors.secondary }]}>
            {currentWeekData.avgSleepQuality != null
              ? `${currentWeekData.avgSleepQuality.toFixed(1)}/5`
              : currentWeekData.avgSleepHours != null
                ? `${currentWeekData.avgSleepHours.toFixed(1)}h`
                : '--'}
          </Text>
        </View>
      </View>

      <View style={styles.weeklyBars}>
        {weeklyData.map(day => (
          <View key={day.day} style={styles.dayColumn}>
            <Text style={[styles.dayLabel, { color: colors.onSurfaceVariant }]}>{day.day}</Text>
            <View style={styles.barContainer}>
              <View
                style={[
                  styles.barFill,
                  { height: `${Math.max(day.fatigue, 6)}%`, backgroundColor: getCapacityColor(colors, day.capacity) },
                ]}
              />
              <View style={[styles.maxLine, { backgroundColor: `${colors.onSurfaceVariant}33` }]} />
            </View>
            <Text style={[styles.capacityLabel, { color: colors.onSurface }]}>{day.capacity}</Text>
          </View>
        ))}
      </View>

      {wellbeingOverview ? (
        <Text style={[styles.footerCopy, { color: colors.onSurfaceVariant }]}>
          {wellbeingOverview.latestSnapshot?.sleepQuality != null
            ? `Último check-in: ${wellbeingOverview.latestSnapshot.sleepQuality}/5 sueño, ${wellbeingOverview.latestSnapshot.stressLevel}/5 estrés.`
            : 'La capacidad se calcula con entrenamiento, sueño y check-ins reales.'}
        </Text>
      ) : null}
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
    gap: 12,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  headerSubtitle: {
    fontSize: 12,
    marginTop: 4,
  },
  badge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 9999,
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  emptyText: {
    fontSize: 14,
    lineHeight: 20,
  },
  summaryGrid: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 16,
  },
  summaryCard: {
    flex: 1,
    padding: 12,
    borderRadius: 16,
    backgroundColor: 'rgba(255,255,255,0.04)',
  },
  summaryLabel: {
    fontSize: 9,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  summaryValue: {
    fontSize: 20,
    fontWeight: '900',
    marginTop: 4,
  },
  secondaryRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 18,
  },
  secondaryCard: {
    flex: 1,
    padding: 12,
    borderRadius: 16,
    backgroundColor: 'rgba(255,255,255,0.04)',
  },
  secondaryLabel: {
    fontSize: 9,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  secondaryValue: {
    fontSize: 18,
    fontWeight: '900',
    marginTop: 4,
  },
  weeklyBars: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  dayColumn: {
    alignItems: 'center',
    flex: 1,
  },
  dayLabel: {
    fontSize: 10,
    fontWeight: '600',
    textTransform: 'uppercase',
    marginBottom: 8,
  },
  barContainer: {
    width: 20,
    height: 80,
    backgroundColor: 'rgba(255,255,255,0.05)',
    borderRadius: 4,
    position: 'relative',
    marginBottom: 8,
    overflow: 'hidden',
  },
  barFill: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    borderRadius: 4,
  },
  maxLine: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 1,
  },
  capacityLabel: {
    fontSize: 10,
    fontWeight: '600',
  },
  footerCopy: {
    marginTop: 8,
    fontSize: 12,
    lineHeight: 18,
  },
});
