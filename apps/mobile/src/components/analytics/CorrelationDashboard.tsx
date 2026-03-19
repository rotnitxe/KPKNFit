import React, { useEffect, useMemo, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, TouchableOpacity, ScrollView } from 'react-native';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { SparklesIcon } from '../icons';
import { readStoredSettingsRaw, readStoredWellbeingPayload } from '../../services/mobileDomainStateService';
import { useBodyStore } from '../../stores/bodyStore';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { getWeekId } from '../../utils/calculations';

interface WeeklyData {
  weekId: string;
  weekName: string;
  avgWeight: number | null;
  avgCalories: number | null;
  totalVolume: number;
  avgSleep: number | null;
  avgProtein: number | null;
}

function pearson(left: Array<number | null>, right: Array<number | null>) {
  const pairs = left
    .map((value, index) => [value, right[index]] as const)
    .filter((pair): pair is [number, number] => typeof pair[0] === 'number' && typeof pair[1] === 'number');

  if (pairs.length < 2) return null;

  const meanLeft = pairs.reduce((sum, [value]) => sum + value, 0) / pairs.length;
  const meanRight = pairs.reduce((sum, [, value]) => sum + value, 0) / pairs.length;
  const covariance = pairs.reduce((sum, [x, y]) => sum + ((x - meanLeft) * (y - meanRight)), 0);
  const varianceLeft = pairs.reduce((sum, [x]) => sum + Math.pow(x - meanLeft, 2), 0);
  const varianceRight = pairs.reduce((sum, [, y]) => sum + Math.pow(y - meanRight, 2), 0);
  if (varianceLeft === 0 || varianceRight === 0) return null;
  return covariance / Math.sqrt(varianceLeft * varianceRight);
}

function formatCorrelation(value: number | null) {
  if (value == null || Number.isNaN(value)) return 'n/a';
  return value.toFixed(2);
}

export const CorrelationDashboard: React.FC = () => {
  const colors = useColors();
  const [analysis, setAnalysis] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [showFullCharts, setShowFullCharts] = useState(false);

  const bodyProgress = useBodyStore(state => state.bodyProgress);
  const bodyStatus = useBodyStore(state => state.status);
  const hydrateBody = useBodyStore(state => state.hydrateFromMigration);

  const workoutHistory = useWorkoutStore(state => state.history);
  const workoutStatus = useWorkoutStore(state => state.status);
  const hydrateWorkout = useWorkoutStore(state => state.hydrateFromMigration);

  const savedLogs = useMobileNutritionStore(state => state.savedLogs);
  const nutritionStatus = useMobileNutritionStore(state => state.status);
  const hydrateNutrition = useMobileNutritionStore(state => state.hydrateFromStorage);

  const exerciseList = useExerciseStore(state => state.exerciseList);
  const exerciseStatus = useExerciseStore(state => state.status);
  const hydrateExercises = useExerciseStore(state => state.hydrateFromMigration);

  const wellbeingOverview = useWellbeingStore(state => state.overview);
  const wellbeingStatus = useWellbeingStore(state => state.status);
  const hydrateWellbeing = useWellbeingStore(state => state.hydrateFromMigration);

  const settingsSummary = useSettingsStore(state => state.summary);
  const settingsStatus = useSettingsStore(state => state.status);
  const hydrateSettings = useSettingsStore(state => state.hydrateFromMigration);

  useEffect(() => {
    if (bodyStatus === 'idle') void hydrateBody();
    if (workoutStatus === 'idle') void hydrateWorkout();
    if (nutritionStatus === 'idle') void hydrateNutrition();
    if (exerciseStatus === 'idle') void hydrateExercises();
    if (wellbeingStatus === 'idle') void hydrateWellbeing();
    if (settingsStatus === 'idle') void hydrateSettings();
  }, [
    bodyStatus,
    exerciseStatus,
    hydrateBody,
    hydrateExercises,
    hydrateNutrition,
    hydrateSettings,
    hydrateWellbeing,
    settingsStatus,
    nutritionStatus,
    wellbeingStatus,
    workoutStatus,
    hydrateWorkout,
  ]);

  const settings = settingsSummary ?? (readStoredSettingsRaw() as any);
  const wellbeingPayload = readStoredWellbeingPayload();

  const weeklyData = useMemo<WeeklyData[]>(() => {
    const dataMap = new Map<string, {
      weights: number[];
      calories: number[];
      proteins: number[];
      volumes: number[];
      sleeps: number[];
      calorieDays: Set<string>;
      proteinDays: Set<string>;
    }>();

    const processLogs = <T extends { date: string }>(logs: T[], processor: (log: T, weekData: any) => void) => {
      logs.forEach(log => {
        const weekId = getWeekId(new Date(log.date), settings.startWeekOn || 1);
        if (!dataMap.has(weekId)) {
          dataMap.set(weekId, {
            weights: [],
            calories: [],
            proteins: [],
            volumes: [],
            sleeps: [],
            calorieDays: new Set(),
            proteinDays: new Set(),
          });
        }
        processor(log, dataMap.get(weekId)!);
      });
    };

    processLogs(bodyProgress, (log, weekData) => {
      if (typeof log.weight === 'number') {
        weekData.weights.push(log.weight);
      }
    });

    processLogs(savedLogs, (log, weekData) => {
      const calories = Number(log.totals?.calories ?? 0);
      const protein = Number(log.totals?.protein ?? 0);

      if (calories > 0) {
        weekData.calories.push(calories);
        weekData.calorieDays.add(log.createdAt.slice(0, 10));
      }
      if (protein > 0) {
        weekData.proteins.push(protein);
        weekData.proteinDays.add(log.createdAt.slice(0, 10));
      }
    });

    processLogs(workoutHistory, (log, weekData) => {
      const volume = log.completedExercises.reduce(
        (total, ex) => total + ex.sets.reduce((setTotal, set) => setTotal + (set.weight || 0) * (set.completedReps || 0), 0),
        0,
      );
      weekData.volumes.push(volume);
      if (log.readiness?.sleepQuality) {
        weekData.sleeps.push(log.readiness.sleepQuality);
      }
    });

    const sleepLogs = Array.isArray(wellbeingPayload.sleepLogs) ? wellbeingPayload.sleepLogs : [];
    const sleepMap = new Map<string, number[]>();
    sleepLogs.forEach((log: any) => {
      const weekId = getWeekId(new Date(log.endTime || log.startTime || log.date || Date.now()), settings.startWeekOn || 1);
      if (!sleepMap.has(weekId)) sleepMap.set(weekId, []);
      const quality = typeof log.quality === 'number'
        ? log.quality
        : typeof log.duration === 'number'
          ? Math.max(1, Math.min(5, (log.duration / 8) * 5))
          : null;
      if (quality != null) sleepMap.get(weekId)!.push(quality);
    });

    const sortedWeeks = Array.from(dataMap.keys()).sort((left, right) => new Date(left).getTime() - new Date(right).getTime());

    return sortedWeeks.slice(-12).map(weekId => {
      const data = dataMap.get(weekId)!;
      const avgWeight = data.weights.length > 0 ? data.weights.reduce((a, b) => a + b, 0) / data.weights.length : null;
      const avgCalories = data.calorieDays.size > 0 ? data.calories.reduce((a, b) => a + b, 0) / data.calorieDays.size : null;
      const avgProtein = data.proteinDays.size > 0 ? data.proteins.reduce((a, b) => a + b, 0) / data.proteinDays.size : null;
      const totalVolume = data.volumes.reduce((a, b) => a + b, 0);
      const sleepSamples = sleepMap.get(weekId) || [];
      const avgSleep = sleepSamples.length > 0
        ? sleepSamples.reduce((a, b) => a + b, 0) / sleepSamples.length
        : (data.sleeps.length > 0 ? data.sleeps.reduce((a, b) => a + b, 0) / data.sleeps.length : null);

      return {
        weekId,
        weekName: new Date(weekId).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' }),
        avgWeight,
        avgCalories,
        totalVolume,
        avgSleep,
        avgProtein,
      };
    });
  }, [bodyProgress, savedLogs, settings.startWeekOn, workoutHistory, wellbeingPayload.sleepLogs]);

  const correlations = useMemo(() => {
    const weights = weeklyData.map(week => week.avgWeight);
    const calories = weeklyData.map(week => week.avgCalories);
    const volume = weeklyData.map(week => week.totalVolume);
    const sleep = weeklyData.map(week => week.avgSleep);
    const protein = weeklyData.map(week => week.avgProtein);

    return [
      {
        factor: 'Peso vs. Calorías',
        r: pearson(weights, calories),
        description: 'Relaciona el peso medio semanal con la ingesta calórica media.',
      },
      {
        factor: 'Volumen vs. Sueño',
        r: pearson(volume, sleep),
        description: 'Compara el volumen de entrenamiento con el promedio de sueño semanal.',
      },
      {
        factor: 'Peso vs. Proteína',
        r: pearson(weights, protein),
        description: 'Cruza peso corporal medio con la proteína media consumida.',
      },
    ];
  }, [weeklyData]);
  const wellbeingSnapshot = wellbeingOverview?.latestSnapshot;

  const handleGenerateAnalysis = async () => {
    if (weeklyData.length < 2) return;
    setIsLoading(true);
    try {
      const latest = weeklyData[weeklyData.length - 1];
      const previous = weeklyData[weeklyData.length - 2];
      const weightDelta = latest.avgWeight != null && previous.avgWeight != null ? latest.avgWeight - previous.avgWeight : null;
      const caloriesDelta = latest.avgCalories != null && previous.avgCalories != null ? latest.avgCalories - previous.avgCalories : null;
      const volumeDelta = latest.totalVolume - previous.totalVolume;

      const summary = [
        `Semana ${latest.weekName}:`,
        weightDelta != null ? `peso ${weightDelta >= 0 ? 'subió' : 'bajó'} ${Math.abs(weightDelta).toFixed(1)}${settings.weightUnit}` : 'sin suficiente dato de peso',
        caloriesDelta != null ? `calorías ${caloriesDelta >= 0 ? 'subieron' : 'bajaron'} ${Math.abs(caloriesDelta).toFixed(0)} kcal` : 'sin suficiente dato calórico',
        `volumen ${volumeDelta >= 0 ? 'subió' : 'bajó'} ${Math.abs(volumeDelta).toFixed(0)} series-equivalentes`,
      ].join(' · ');

      setAnalysis(summary);
      setShowFullCharts(true);
    } finally {
      setIsLoading(false);
    }
  };

  if (weeklyData.length < 2) {
    return (
      <LiquidGlassCard style={styles.emptyCard}>
        <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Correlaciones de Datos</Text>
        <Text style={[styles.alertText, { color: colors.onSurfaceVariant }]}>
          Se necesitan más datos (al menos 2 semanas de registros de peso, nutrición y entrenamiento) para mostrar correlaciones.
        </Text>
      </LiquidGlassCard>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Correlaciones de Datos</Text>
        <TouchableOpacity
          style={[styles.toggleButton, { backgroundColor: showFullCharts ? colors.surface : colors.primaryContainer }]}
          onPress={() => setShowFullCharts(prev => !prev)}
        >
          <Text style={[styles.toggleText, { color: showFullCharts ? colors.onPrimaryContainer : colors.onSurface }]}>
            {showFullCharts ? 'Simplificado' : 'Completo'}
          </Text>
        </TouchableOpacity>
      </View>

      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="small" color={colors.primary} style={styles.loading} />
        </View>
      ) : (
        <>
          {!showFullCharts ? (
            <LiquidGlassCard style={styles.alertBanner}>
              <Text style={[styles.alertText, { color: colors.onSurfaceVariant }]}>
                {analysis ??
                  'Se necesitan más datos (al menos 2 semanas de registros de peso, nutrición y entrenamiento) para mostrar correlaciones.'}
              </Text>
            </LiquidGlassCard>
          ) : (
            <ScrollView showsVerticalScrollIndicator={false} style={styles.content}>
              {correlations.map((corr, idx) => (
                <LiquidGlassCard key={idx} style={styles.card}>
                  <View style={styles.cardHeader}>
                    <Text style={[styles.cardTitle, { color: colors.onSurface }]}>{corr.factor}</Text>
                    <Text style={[styles.correlation, { color: colors.onSurfaceVariant }]}>r = {formatCorrelation(corr.r)}</Text>
                  </View>
                  <Text style={[styles.cardDescription, { color: colors.onSurfaceVariant }]}>{corr.description}</Text>
                  <View style={styles.chartPlaceholder}>
                    <Text style={[styles.chartHeader, { color: colors.onSurface }]}>Datos semanales</Text>
                    {weeklyData.map(week => (
                      <View key={week.weekId} style={styles.weekRow}>
                        <Text style={[styles.weekLabel, { color: colors.onSurfaceVariant }]}>{week.weekName}</Text>
                        <Text style={[styles.weekValue, { color: colors.onSurface }]} numberOfLines={1}>
                          {week.avgWeight != null ? `${week.avgWeight.toFixed(1)}${settings.weightUnit}` : '--'} ·{' '}
                          {week.avgCalories != null ? `${week.avgCalories.toFixed(0)} kcal` : '--'} ·{' '}
                          {week.totalVolume.toFixed(0)} vol
                        </Text>
                      </View>
                    ))}
                  </View>
                </LiquidGlassCard>
              ))}

              <LiquidGlassCard style={styles.card}>
                <View style={styles.cardHeader}>
                  <Text style={[styles.cardTitle, { color: colors.onSurface }]}>Análisis del Coach</Text>
                  <SparklesIcon size={16} color={colors.primary} />
                </View>
                {analysis ? (
                  <Text style={[styles.analysisText, { color: colors.onSurfaceVariant }]}>{analysis}</Text>
                ) : (
                  <Text style={[styles.analysisText, { color: colors.onSurfaceVariant }]}>
                    Pulsa el botón para resumir las tendencias reales del historial.
                  </Text>
                )}
                {wellbeingSnapshot ? (
                  <Text style={[styles.analysisText, { color: colors.onSurfaceVariant }]}>
                    Último check-in: sueño {wellbeingSnapshot.sleepQuality}/5, estrés {wellbeingSnapshot.stressLevel}/5, motivación {wellbeingSnapshot.motivation}/5.
                  </Text>
                ) : null}
                <TouchableOpacity
                  style={[styles.generateButton, { backgroundColor: colors.primary }]}
                  onPress={() => void handleGenerateAnalysis()}
                  disabled={isLoading}
                >
                  <Text style={[styles.generateButtonText, { color: colors.onPrimary }]}>
                    {analysis ? 'Regenerar Análisis' : 'Generar Análisis'}
                  </Text>
                </TouchableOpacity>
              </LiquidGlassCard>
            </ScrollView>
          )}
        </>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 16,
    gap: 24,
  },
  emptyCard: {
    padding: 20,
    borderRadius: 16,
    gap: 12,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  toggleButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 9999,
  },
  toggleText: {
    fontSize: 12,
    fontWeight: '600',
  },
  alertBanner: {
    padding: 24,
    borderRadius: 12,
    alignItems: 'center',
  },
  alertText: {
    fontSize: 14,
    textAlign: 'center',
  },
  content: {
    paddingBottom: 16,
  },
  card: {
    padding: 20,
    borderRadius: 16,
    marginBottom: 16,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '700',
  },
  correlation: {
    fontSize: 14,
    fontWeight: '600',
  },
  cardDescription: {
    fontSize: 12,
    lineHeight: 18,
    marginBottom: 12,
  },
  chartPlaceholder: {
    padding: 16,
    borderRadius: 12,
    gap: 8,
    backgroundColor: 'rgba(255,255,255,0.04)',
  },
  chartHeader: {
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 4,
  },
  weekRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  weekLabel: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    width: 70,
  },
  weekValue: {
    flex: 1,
    fontSize: 11,
    fontWeight: '500',
    textAlign: 'right',
  },
  analysisText: {
    fontSize: 14,
    lineHeight: 20,
    marginBottom: 12,
  },
  generateButton: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 12,
    alignItems: 'center',
  },
  generateButtonText: {
    fontSize: 14,
    fontWeight: '700',
  },
  loadingContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 24,
  },
  loading: {
    marginTop: 24,
  },
  tooltipContainer: {
    padding: 8,
    borderRadius: 8,
    backgroundColor: 'rgba(0,0,0,0.7)',
  },
  tooltipLabel: {
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 4,
  },
  tooltipValue: {
    fontSize: 14,
    fontWeight: '700',
  },
});
