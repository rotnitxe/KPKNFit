import React, { useEffect, useMemo, useState } from 'react';
import { Pressable, Text, View, Alert, StyleSheet, ScrollView } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { Button } from '../../components/ui';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';
import { useBodyStore } from '../../stores/bodyStore';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useAugeRuntimeStore } from '../../stores/augeRuntimeStore';
import { AugeStatusCard, AugeDiagnosticsList } from '../../components/auge';
import { BodyProgressSection } from '../../components/body/BodyProgressSection';
import { AddBodyLogModal } from '../../components/body/AddBodyLogModal';
import { BatteryRingCard, StreakCard } from '../../components/activity';
import type { BodyProgressEntry } from '../../types/workout';
import { getLocalDateKey } from '@kpkn/shared-domain';
import { calculateWeightDelta, buildLast7NutritionSeries, buildBodyWeightSeries } from '../../utils/homeHelpers';
import { ChartCard, LineTrendChart, BarTrendChart } from '../../components/charts';
import { useColors } from '../../theme';
import { CaupolicanBody } from '../../components/analytics/CaupolicanBody';
import { calculateLast7DaysMuscleVolume } from '../../services/analysisService';
import { loadPersistedDomainPayload } from '../../services/mobilePersistenceService';
import { WorkoutLog } from '../../types/workout';

interface MetricCardProps {
  label: string;
  value: string;
  detail: string;
}

function MetricCard({ label, value, detail }: MetricCardProps) {
  const colors = useColors();

  return (
    <View style={[styles.metricCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <Text style={[styles.metricLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <Text style={[styles.metricValue, { color: colors.onSurface }]}>{value}</Text>
      <Text style={[styles.metricDetail, { color: colors.onSurfaceVariant }]}>{detail}</Text>
    </View>
  );
}

export function ProgressScreen() {
  const colors = useColors();

  const savedLogs = useMobileNutritionStore((state) => state.savedLogs);
  const workoutOverview = useWorkoutStore((state) => state.overview);
  const wellbeingStatus = useWellbeingStore((state) => state.status);
  const wellbeingOverview = useWellbeingStore((state) => state.overview);
  const wellbeingTasks = useWellbeingStore((state) => state.tasks);
  const wellbeingNotice = useWellbeingStore((state) => state.notice);
  const hydrateWellbeing = useWellbeingStore((state) => state.hydrateFromMigration);
  const logWater = useWellbeingStore((state) => state.logWater);
  const toggleTask = useWellbeingStore((state) => state.toggleTask);
  const clearNotice = useWellbeingStore((state) => state.clearNotice);

  const bodyStatus = useBodyStore((state) => state.status);
  const bodyProgress = useBodyStore((state) => state.bodyProgress);
  const hydrateBody = useBodyStore((state) => state.hydrateFromMigration);
  const addBodyLog = useBodyStore((state) => state.addBodyLog);
  const updateBodyLog = useBodyStore((state) => state.updateBodyLog);
  const deleteBodyLog = useBodyStore((state) => state.deleteBodyLog);
  const bodyNotice = useBodyStore((state) => state.notice);
  const clearBodyNotice = useBodyStore((state) => state.clearNotice);

  const [isBodyModalVisible, setIsBodyModalVisible] = useState(false);
  const [editingEntry, setEditingEntry] = useState<BodyProgressEntry | null>(null);

  const exerciseStatus = useExerciseStore((state) => state.status);
  const exerciseList = useExerciseStore((state) => state.exerciseList);
  const muscleHierarchy = useExerciseStore((state) => state.muscleHierarchy);
  const exercisePlaylists = useExerciseStore((state) => state.exercisePlaylists);
  const hydrateExercises = useExerciseStore((state) => state.hydrateFromMigration);

  const {
    snapshot: augeSnapshot,
    isRefreshing: isAugeRefreshing,
    status: augeStatus,
    notice: augeNotice,
    errorMessage: augeErrorMessage,
    hydrateFromStorage: hydrateAuge,
    recompute: recomputeAuge,
    clearNotice: clearAugeNotice,
  } = useAugeRuntimeStore();

  useEffect(() => {
    if (bodyStatus === 'idle') void hydrateBody();
    if (exerciseStatus === 'idle') void hydrateExercises();
  }, [bodyStatus, exerciseStatus, hydrateBody, hydrateExercises]);

  const weightDelta = useMemo(() => calculateWeightDelta(bodyProgress), [bodyProgress]);
  const sortedBodyProgress = useMemo(
    () => [...bodyProgress].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()),
    [bodyProgress]
  );

  useEffect(() => {
    if (wellbeingStatus === 'idle') {
      void hydrateWellbeing();
    }
  }, [hydrateWellbeing, wellbeingStatus]);

  useEffect(() => {
    if (augeStatus === 'idle') {
      void hydrateAuge();
    }
  }, [augeStatus, hydrateAuge]);

  useEffect(() => {
    if (augeNotice) {
      const timeout = setTimeout(() => {
        clearAugeNotice();
      }, 3000);
      return () => clearTimeout(timeout);
    }
    return undefined;
  }, [augeNotice, clearAugeNotice]);

  useEffect(() => {
    if (!wellbeingNotice) return undefined;
    const timeout = setTimeout(() => {
      clearNotice();
    }, 3000);
    return () => clearTimeout(timeout);
  }, [clearNotice, wellbeingNotice]);

  useEffect(() => {
    if (!bodyNotice) return undefined;
    const timeout = setTimeout(() => {
      clearBodyNotice();
    }, 3000);
    return () => clearTimeout(timeout);
  }, [clearBodyNotice, bodyNotice]);

  const nutritionSummary = useMemo(() => {
    const today = getLocalDateKey();
    const last7Days = new Set(
      Array.from({ length: 7 }).map((_, index) => {
        const date = new Date();
        date.setDate(date.getDate() - index);
        return getLocalDateKey(date);
      })
    );

    const todayLogs = savedLogs.filter((log) => log.createdAt.slice(0, 10) === today);
    const last7Logs = savedLogs.filter((log) => last7Days.has(log.createdAt.slice(0, 10)));

    const todayCalories = todayLogs.reduce((sum, log) => sum + log.totals.calories, 0);
    const weeklyCalories = last7Logs.reduce((sum, log) => sum + log.totals.calories, 0);
    const weeklyProtein = last7Logs.reduce((sum, log) => sum + log.totals.protein, 0);

    return {
      todayCalories,
      weeklyCalories,
      weeklyProtein,
      mealCount: todayLogs.length,
      weeklyLogCount: last7Logs.length,
    };
  }, [savedLogs]);

  const nutritionSeries = useMemo(() => buildLast7NutritionSeries(savedLogs), [savedLogs]);
  const bodyWeightSeries = useMemo(() => buildBodyWeightSeries(bodyProgress), [bodyProgress]);

  const [fullHistory, setFullHistory] = useState<WorkoutLog[]>([]);

  useEffect(() => {
    const load = async () => {
      const payload = await loadPersistedDomainPayload<any>('workout');
      if (payload?.history) {
        setFullHistory(payload.history);
      }
    };
    load();
  }, [workoutOverview]);

  const muscleVolumeAnalysis = useMemo(() => {
    if (!fullHistory.length || !exerciseList.length) return [];
    return calculateLast7DaysMuscleVolume(fullHistory, exerciseList, muscleHierarchy);
  }, [fullHistory, exerciseList, muscleHierarchy]);

  const handleDeleteBodyLog = (entry: BodyProgressEntry) => {
    Alert.alert(
      'Eliminar registro',
      '¿Estás seguro de que quieres eliminar este registro corporal?',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Eliminar',
          style: 'destructive',
          onPress: () => void deleteBodyLog(entry.id),
        },
      ]
    );
  };

  return (
    <ScreenShell
      title="Progreso"
      subtitle="Una vista simple para revisar cómo va tu semana: comida registrada, entrenos y señales prácticas del plan."
    >
      <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
        <View style={styles.content}>
          {/* Nutrition Metrics */}
          <View style={styles.metricsRow}>
            <MetricCard
              label="Hoy"
              value={`${Math.round(nutritionSummary.todayCalories)} kcal`}
              detail={`${nutritionSummary.mealCount} comida${nutritionSummary.mealCount === 1 ? '' : 's'} registrada${nutritionSummary.mealCount === 1 ? '' : 's'}`}
            />
            <MetricCard
              label="7 días"
              value={`${Math.round(nutritionSummary.weeklyCalories)} kcal`}
              detail={`${nutritionSummary.weeklyLogCount} registros nutricionales`}
            />
            <MetricCard
              label="Proteína"
              value={`${Math.round(nutritionSummary.weeklyProtein)} g`}
              detail="Suma referencial de la última semana"
            />
          </View>

          {/* Heat Map Section */}
          <View style={[styles.section, styles.heatMapSection]}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Estímulo Semanal por Músculo</Text>
            <View style={[styles.heatMapContainer, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
              <CaupolicanBody data={muscleVolumeAnalysis} />
            </View>
          </View>

          {/* Rings and Streak */}
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Rings y Racha</Text>
            <View style={styles.ringsRow}>
              <View style={styles.ringCardContainer}>
                <BatteryRingCard
                  overallPct={workoutOverview?.battery?.overall ?? 0}
                  cnsPct={workoutOverview?.battery?.cns ?? 0}
                  muscularPct={workoutOverview?.battery?.muscular ?? 0}
                  sourceLabel={workoutOverview?.battery?.source ?? 'recovery-derived'}
                />
              </View>
              <View style={styles.ringCardContainer}>
                <StreakCard
                  datesWithWorkout={workoutOverview?.recentLogs?.map((l) => l.date) ?? []}
                  weekCompletionPct={
                    (workoutOverview?.completedSetsThisWeek ?? 0) /
                      (workoutOverview?.plannedSetsThisWeek || 1) *
                      100
                  }
                />
              </View>
            </View>
          </View>

          {/* Charts */}
          <ChartCard title="Calorías últimos 7 días">
            <BarTrendChart
              data={nutritionSeries.map((p) => {
                const [y, m, d] = p.dateKey.split('-');
                return {
                  key: p.dateKey,
                  label: `${d}/${m}`,
                  value: p.calories,
                  highlight: p.dateKey === getLocalDateKey(),
                };
              })}
            />
          </ChartCard>

          <ChartCard title="Proteína últimos 7 días">
            <LineTrendChart
              data={nutritionSeries.map((p) => {
                const [y, m, d] = p.dateKey.split('-');
                return {
                  key: p.dateKey,
                  label: `${d}/${m}`,
                  value: p.protein,
                };
              })}
            />
          </ChartCard>

          <ChartCard title="Tendencia de peso">
            <LineTrendChart data={bodyWeightSeries} />
          </ChartCard>

          {/* Body Progress Section */}
          <BodyProgressSection
            entries={bodyProgress}
            onPressAdd={() => {
              setEditingEntry(null);
              setIsBodyModalVisible(true);
            }}
            onPressEdit={(entry) => {
              setEditingEntry(entry);
              setIsBodyModalVisible(true);
            }}
            onPressDelete={handleDeleteBodyLog}
          />

          {/* Wellbeing Metrics */}
          <View style={styles.metricsRow}>
            <MetricCard
              label="Sueño"
              value={
                wellbeingOverview?.averageSleepHoursLast7Days
                  ? `${wellbeingOverview.averageSleepHoursLast7Days} h`
                  : '--'
              }
              detail={
                wellbeingOverview
                  ? `${wellbeingOverview.sleepEntriesLast7Days} registros en 7 dias`
                  : 'Sin datos de sueño todavía'
              }
            />
            <MetricCard
              label="Agua"
              value={`${Math.round((wellbeingOverview?.waterTodayMl ?? 0) / 100) / 10} L`}
              detail="Total acumulado hoy"
            />
            <MetricCard
              label="Tareas"
              value={`${wellbeingOverview?.completedTaskCount ?? 0}/${wellbeingOverview?.totalTaskCount ?? 0}`}
              detail="Completadas del módulo wellbeing"
            />
          </View>

          {/* Body Composition */}
          <View style={[styles.infoCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.infoCardTitle, { color: colors.onSurfaceVariant }]}>Composicion corporal</Text>
            {bodyProgress.length > 0 ? (
              <View style={styles.infoCardContent}>
                <Text style={[styles.infoCardValue, { color: colors.onSurface }]}>
                  {sortedBodyProgress[0].weight} kg
                  {weightDelta ? (
                    <Text
                      style={[
                        styles.infoCardDelta,
                        { color: weightDelta.delta > 0 ? colors.cyberWarning : colors.batteryHigh },
                      ]}
                    >
                      {` ${weightDelta.delta > 0 ? '+' : ''}${weightDelta.delta.toFixed(1)} kg`}
                    </Text>
                  ) : null}
                </Text>
                {weightDelta ? (
                  <Text style={[styles.infoCardDetail, { color: colors.onSurfaceVariant }]}>
                    Tendencia en los ultimos {weightDelta.days} dias
                  </Text>
                ) : null}
              </View>
            ) : (
              <Text style={[styles.infoCardEmpty, { color: colors.onSurfaceVariant }]}>
                Sin registros de peso todavia. Los datos de la app vieja apareceran aqui tras la migracion.
              </Text>
            )}
          </View>

          {/* Exercise Library */}
          <View style={[styles.infoCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.infoCardTitle, { color: colors.onSurfaceVariant }]}>Biblioteca de ejercicios</Text>
            <Text style={[styles.infoCardValue, { color: colors.onSurface }]}>
              {exerciseList.length} ejercicios disponibles
            </Text>
            {exercisePlaylists.length > 0 ? (
              <Text style={[styles.infoCardDetail, { color: colors.onSurfaceVariant }]}>
                Playlists: {exercisePlaylists.slice(0, 3).map((p) => p.name).join(', ')}
              </Text>
            ) : (
              <Text style={[styles.infoCardDetail, { color: colors.onSurfaceVariant }]}>
                Aun no tienes playlists de ejercicios creadas.
              </Text>
            )}
          </View>

          {/* Training Overview */}
          <View style={[styles.infoCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.infoCardTitle, { color: colors.onSurfaceVariant }]}>Entrenamiento</Text>
            <Text style={[styles.infoCardValue, { color: colors.onSurface }]}>
              {workoutOverview?.activeProgramName ?? 'Sin programa activo'}
            </Text>
            <Text style={[styles.infoCardDetail, { color: colors.onSurfaceVariant }]}>
              {workoutOverview
                ? `${workoutOverview.weeklySessionCount} entreno${workoutOverview.weeklySessionCount === 1 ? '' : 's'} esta semana · ${workoutOverview.completedSetsThisWeek}/${workoutOverview.plannedSetsThisWeek} series`
                : 'Cuando tu programa migrado esté listo aquí verás tu carga semanal y tus últimas sesiones.'}
            </Text>
          </View>

          {/* Wellbeing Card */}
          {wellbeingOverview ? (
            <View style={[styles.infoCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
              <Text style={[styles.infoCardTitle, { color: colors.onSurfaceVariant }]}>Bienestar</Text>
              <Text style={[styles.infoCardValue, { color: colors.onSurface }]}>
                {wellbeingOverview.latestSnapshot?.moodState ?? 'Sin mood registrado'}
              </Text>
              <Text style={[styles.infoCardDetail, { color: colors.onSurfaceVariant }]}>
                {`Readiness ${wellbeingOverview.latestSnapshot?.readiness ?? 'sin dato'} · sueño ${wellbeingOverview.latestSleepHours ?? 'sin dato'} h · agua ${wellbeingOverview.waterTodayMl} ml`}
              </Text>
              <View style={styles.waterButtons}>
                <Button onPress={() => void logWater(500)} variant="secondary">
                  + 500 ml de agua
                </Button>
                <Button onPress={() => void logWater(1000)} variant="secondary">
                  + 1 L de agua
                </Button>
              </View>
              {wellbeingTasks.length > 0 ? (
                <View style={styles.tasksSection}>
                  <Text style={[styles.tasksTitle, { color: colors.onSurfaceVariant }]}>Tareas</Text>
                  {wellbeingTasks.slice(0, 3).map((task) => (
                    <Pressable
                      key={task.id}
                      style={[styles.taskItem, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
                      onPress={() => void toggleTask(task.id)}
                    >
                      <Text style={[styles.taskTitle, { color: colors.onSurface }]}>{task.title}</Text>
                      <Text style={[styles.taskDetail, { color: colors.onSurfaceVariant }]}>
                        {task.completed ? 'Marcada como lista' : 'Toca hacerla'}
                      </Text>
                    </Pressable>
                  ))}
                </View>
              ) : null}
            </View>
          ) : null}

          {/* Wellbeing Notice */}
          {wellbeingNotice ? (
            <View style={[styles.noticeCard, { backgroundColor: `${colors.batteryHigh}1A`, borderColor: `${colors.batteryHigh}40` }]}>
              <Text style={[styles.noticeText, { color: colors.batteryHigh }]}>{wellbeingNotice}</Text>
            </View>
          ) : null}

          {/* AUGE Readiness */}
          <View style={[styles.infoCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.infoCardTitle, { color: colors.onSurfaceVariant }]}>AUGE Readiness</Text>
            <Text style={[styles.infoCardValue, { color: colors.onSurface }]}>Estado del Sistema Nervioso Central</Text>
            <Text style={[styles.infoCardDetail, { color: colors.onSurfaceVariant }]}>
              Indicador de fatiga y recuperación para ajustar la intensidad de tu entrenamiento.
            </Text>
            <View style={styles.augeSection}>
              <AugeStatusCard snapshot={augeSnapshot} isRefreshing={isAugeRefreshing} onRefresh={recomputeAuge} />
              {augeSnapshot && <AugeDiagnosticsList diagnostics={augeSnapshot.diagnostics.slice(0, 3)} />}
            </View>
          </View>

          {/* Recent Meals */}
          {savedLogs.length > 0 ? (
            <View style={[styles.listCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
              <Text style={[styles.listCardTitle, { color: colors.onSurfaceVariant }]}>Últimas comidas</Text>
              <View style={styles.listContent}>
                {savedLogs.slice(0, 5).map((log) => (
                  <View
                    key={log.id}
                    style={[styles.listItem, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
                  >
                    <Text style={[styles.listItemTitle, { color: colors.onSurface }]}>{log.description}</Text>
                    <Text style={[styles.listItemDetail, { color: colors.onSurfaceVariant }]}>
                      {`${Math.round(log.totals.calories)} kcal · ${Math.round(log.totals.protein)}p · ${Math.round(log.totals.carbs)}c · ${Math.round(log.totals.fats)}g`}
                    </Text>
                    <Text style={[styles.listItemDate, { color: colors.onSurfaceVariant }]}>
                      {new Date(log.createdAt).toLocaleString('es-CL')}
                    </Text>
                  </View>
                ))}
              </View>
            </View>
          ) : null}

          {/* Recent Workouts */}
          {workoutOverview?.recentLogs?.length ? (
            <View style={[styles.listCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
              <Text style={[styles.listCardTitle, { color: colors.onSurfaceVariant }]}>Últimos entrenos</Text>
              <View style={styles.listContent}>
                {workoutOverview.recentLogs.map((log) => (
                  <View
                    key={log.id}
                    style={[styles.listItem, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
                  >
                    <Text style={[styles.listItemTitle, { color: colors.onSurface }]}>{log.sessionName}</Text>
                    <Text style={[styles.listItemDetail, { color: colors.onSurfaceVariant }]}>
                      {`${log.programName} · ${log.date}`}
                    </Text>
                    <Text style={[styles.listItemDate, { color: colors.onSurfaceVariant }]}>
                      {`${log.exerciseCount} ejercicios · ${log.completedSetCount} series${log.durationMinutes ? ` · ${log.durationMinutes} min` : ''}`}
                    </Text>
                  </View>
                ))}
              </View>
            </View>
          ) : null}

          {/* Body Notice */}
          {bodyNotice ? (
            <View style={[styles.noticeCard, { backgroundColor: `${colors.primary}1A`, borderColor: `${colors.primary}40` }]}>
              <Text style={[styles.noticeText, { color: colors.primary }]}>{bodyNotice}</Text>
            </View>
          ) : null}
        </View>
      </ScrollView>

      <AddBodyLogModal
        visible={isBodyModalVisible}
        onClose={() => {
          setIsBodyModalVisible(false);
          setEditingEntry(null);
        }}
        initialLogId={editingEntry?.id}
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    gap: 16,
    paddingBottom: 24,
  },
  metricsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  metricCard: {
    flex: 1,
    minWidth: 140,
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
  },
  metricLabel: {
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.5,
    textTransform: 'uppercase',
  },
  metricValue: {
    fontSize: 22,
    fontWeight: '600',
    marginTop: 8,
  },
  metricDetail: {
    fontSize: 13,
    lineHeight: 18,
    marginTop: 8,
  },
  section: {
    gap: 12,
  },
  sectionTitle: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2,
    textTransform: 'uppercase',
    paddingHorizontal: 4,
  },
  ringsRow: {
    flexDirection: 'row',
    gap: 12,
  },
  ringCardContainer: {
    flex: 1,
  },
  infoCard: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
  },
  infoCardTitle: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  infoCardContent: {
    marginTop: 12,
  },
  infoCardValue: {
    fontSize: 20,
    fontWeight: '600',
  },
  infoCardDelta: {
    fontSize: 15,
    fontWeight: '500',
  },
  infoCardDetail: {
    fontSize: 13,
    lineHeight: 18,
    marginTop: 8,
  },
  infoCardEmpty: {
    fontSize: 14,
    lineHeight: 20,
    marginTop: 12,
  },
  waterButtons: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 16,
  },
  tasksSection: {
    marginTop: 16,
    gap: 12,
  },
  tasksTitle: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  taskItem: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
  },
  heatMapSection: {
    marginBottom: 24,
  },
  heatMapContainer: {
    borderRadius: 24,
    padding: 24,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 12,
  },
  taskTitle: {
    fontSize: 15,
    fontWeight: '600',
  },
  taskDetail: {
    fontSize: 13,
    lineHeight: 18,
    marginTop: 4,
  },
  noticeCard: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
  },
  noticeText: {
    fontSize: 15,
    fontWeight: '500',
  },
  augeSection: {
    marginTop: 16,
    gap: 12,
  },
  listCard: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
  },
  listCardTitle: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  listContent: {
    marginTop: 12,
    gap: 8,
  },
  listItem: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
  },
  listItemTitle: {
    fontSize: 15,
    fontWeight: '600',
  },
  listItemDetail: {
    fontSize: 13,
    lineHeight: 18,
    marginTop: 4,
  },
  listItemDate: {
    fontSize: 12,
    lineHeight: 16,
    marginTop: 8,
  },
});
