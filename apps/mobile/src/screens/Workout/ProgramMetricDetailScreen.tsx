import React, { useEffect, useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { RouteProp, useRoute } from '@react-navigation/native';
import { ScreenShell } from '@/components/ScreenShell';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { WorkoutVolumeAnalysisWidget } from '@/components/analytics/WorkoutVolumeAnalysisWidget';
import { RelativeStrengthWidget } from '@/components/analytics/RelativeStrengthWidget';
import { WeeklyFatigueCard } from '@/components/analytics/WeeklyFatigueCard';
import { calculateAverageVolumeForWeeks, calculatePowerliftingMetrics, calculateProgramAdherence } from '@/services/analysisService';
import { getCachedAdaptiveData, getConfidenceLabel } from '@/services/augeAdaptiveService';
import { readStoredSettingsRaw, readStoredWellbeingPayload } from '@/services/mobileDomainStateService';
import { useBodyStore } from '@/stores/bodyStore';
import { useExerciseStore } from '@/stores/exerciseStore';
import { useProgramStore } from '@/stores/programStore';
import { useWorkoutStore } from '@/stores/workoutStore';
import { useColors } from '@/theme';
import type { WorkoutStackParamList, ProgramMetricRoute } from '@/navigation/types';
import { calculateBrzycki1RM } from '@/utils/calculations';

type SleepLogEntry = { duration?: number };

function isSleepLogEntry(value: unknown): value is SleepLogEntry {
  return typeof value === 'object' && value !== null;
}

function SimpleMetricCard({
  label,
  value,
  detail,
}: {
  label: string;
  value: string;
  detail: string;
}) {
  const colors = useColors();
  return (
    <View style={[styles.metricCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <Text style={[styles.metricLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <Text style={[styles.metricValue, { color: colors.onSurface }]}>{value}</Text>
      <Text style={[styles.metricDetail, { color: colors.onSurfaceVariant }]}>{detail}</Text>
    </View>
  );
}

function buildTopLifts(history: ReturnType<typeof useWorkoutStore.getState>['history']) {
  const lifts = new Map<string, number>();

  history.forEach(log => {
    (log.completedExercises ?? []).forEach(exercise => {
      const bestSet = Math.max(
        0,
        ...(exercise.sets ?? []).map(set =>
          calculateBrzycki1RM(Number(set?.weight ?? 0), Number(set?.completedReps ?? 0)),
        ),
      );
      if (bestSet <= 0) return;
      const name = String(exercise.exerciseName ?? '');
      const current = lifts.get(name) ?? 0;
      if (bestSet > current) lifts.set(name, bestSet);
    });
  });

  return Array.from(lifts.entries())
    .map(([name, e1rm]) => ({ name, e1rm }))
    .sort((a, b) => b.e1rm - a.e1rm)
    .slice(0, 6);
}

function formatMetricTitle(metric: ProgramMetricRoute) {
  switch (metric) {
    case 'volume':
      return 'Volumen';
    case 'strength':
      return 'Fuerza';
    case 'density':
      return 'Densidad';
    case 'frequency':
      return 'Frecuencia';
    case 'banister':
      return 'Banister';
    case 'recovery':
      return 'Recuperacion';
    case 'adherence':
      return 'Adherencia';
    case 'rpe':
      return 'RPE';
    default:
      return metric;
  }
}

export function ProgramMetricDetailScreen() {
  const colors = useColors();
  const route = useRoute<RouteProp<WorkoutStackParamList, 'ProgramMetricDetail'>>();
  const { programId, metric } = route.params;

  const settings = readStoredSettingsRaw() as any;
  const wellbeingPayload = readStoredWellbeingPayload();
  const typedSleepLogs = wellbeingPayload.sleepLogs.filter(isSleepLogEntry);

  const workoutStatus = useWorkoutStore(state => state.status);
  const hydrateWorkout = useWorkoutStore(state => state.hydrateFromMigration);
  const history = useWorkoutStore(state => state.history);
  const overview = useWorkoutStore(state => state.overview);
  const latestFeedback = useWorkoutStore(state => state.latestPostSessionFeedback);
  const feedbackHistory = useWorkoutStore(state => state.postSessionFeedbackHistory);

  const programStatus = useProgramStore(state => state.status);
  const hydratePrograms = useProgramStore(state => state.hydrateFromMigration);
  const programs = useProgramStore(state => state.programs);

  const exerciseStatus = useExerciseStore(state => state.status);
  const hydrateExercises = useExerciseStore(state => state.hydrateFromMigration);
  const exerciseList = useExerciseStore(state => state.exerciseList);
  const muscleHierarchy = useExerciseStore(state => state.muscleHierarchy);

  const bodyStatus = useBodyStore(state => state.status);
  const hydrateBody = useBodyStore(state => state.hydrateFromMigration);
  const bodyProgress = useBodyStore(state => state.bodyProgress);

  useEffect(() => {
    if (workoutStatus === 'idle') void hydrateWorkout();
    if (programStatus === 'idle') void hydratePrograms();
    if (exerciseStatus === 'idle') void hydrateExercises();
    if (bodyStatus === 'idle') void hydrateBody();
  }, [
    bodyStatus,
    exerciseStatus,
    hydrateBody,
    hydrateExercises,
    hydratePrograms,
    hydrateWorkout,
    programStatus,
    workoutStatus,
  ]);

  const program = useMemo(
    () => programs.find(item => item.id === programId) ?? null,
    [programId, programs],
  );

  const programWeeks = useMemo(() => {
    return (
      program?.macrocycles.flatMap(macro =>
        (macro.blocks ?? []).flatMap(block => block.mesocycles.flatMap(meso => meso.weeks)),
      ) ?? []
    );
  }, [program]);

  const volumeData = useMemo(() => {
    if (!program || exerciseList.length === 0) return [];
    return calculateAverageVolumeForWeeks(programWeeks, exerciseList, muscleHierarchy, 'complex');
  }, [exerciseList, muscleHierarchy, program, programWeeks]);

  const totalWeeklyVolume = useMemo(
    () => volumeData.reduce((sum, item) => sum + item.displayVolume, 0),
    [volumeData],
  );

  const programHistory = useMemo(
    () => history.filter(log => log.programId === programId),
    [history, programId],
  );

  const strengthLifts = useMemo(() => buildTopLifts(programHistory), [programHistory]);
  const bodyweight = bodyProgress[0]?.weight ?? settings?.userVitals?.weight ?? 0;
  const adherence = useMemo(() => {
    if (!program) return null;
    return calculateProgramAdherence(program, history, settings, exerciseList);
  }, [exerciseList, history, program, settings]);

  const powerliftingMetrics = useMemo(
    () => calculatePowerliftingMetrics(programHistory, settings, exerciseList),
    [exerciseList, programHistory, settings],
  );

  const sessionsPerWeek = useMemo(() => {
    if (!programWeeks.length) return 0;
    const totalSessions = programWeeks.reduce((sum, week) => sum + (week.sessions?.length ?? 0), 0);
    return totalSessions / programWeeks.length;
  }, [programWeeks]);

  const averageSetsPerSession = useMemo(() => {
    if (!programWeeks.length) return 0;
    const sessionCount = programWeeks.reduce((sum, week) => sum + (week.sessions?.length ?? 0), 0);
    if (sessionCount === 0) return 0;
    const totalSets = programWeeks.reduce((sum, week) => {
      return (
        sum +
        week.sessions.reduce((sessionSum, session) => {
          return (
            sessionSum +
            session.exercises.reduce((exerciseSum, exercise) => exerciseSum + exercise.sets.length, 0)
          );
        }, 0)
      );
    }, 0);
    return totalSets / sessionCount;
  }, [programWeeks]);

  const averageFeedbackRpe = useMemo(() => {
    const scoped = feedbackHistory.filter(item => item.programId === programId);
    if (scoped.length === 0) return null;
    return scoped.reduce((sum, item) => sum + item.sessionRpe, 0) / scoped.length;
  }, [feedbackHistory, programId]);

  const adaptiveCache = useMemo(() => getCachedAdaptiveData(), []);

  if (!program) {
    return (
      <ScreenShell title="Metrica no disponible" subtitle="Programa no encontrado">
        <LiquidGlassCard style={styles.emptyCard} padding={20}>
          <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>No encontramos este programa</Text>
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>La ruta ya existe, pero el identificador recibido no corresponde a un programa migrado.</Text>
        </LiquidGlassCard>
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      title={`${formatMetricTitle(metric)} del programa`}
      subtitle={program.name}
    >
      <View style={styles.container}>
        <LiquidGlassCard style={styles.summaryCard} padding={20}>
          <Text style={[styles.summaryTitle, { color: colors.onSurface }]}>{formatMetricTitle(metric)}</Text>
          <Text style={[styles.summaryCopy, { color: colors.onSurfaceVariant }]}>Vista dedicada para la metrica seleccionada desde el contrato legacy del programa.</Text>
        </LiquidGlassCard>

        {metric === 'volume' ? (
          <>
            <WorkoutVolumeAnalysisWidget volumeData={volumeData} totalWeeklyVolume={totalWeeklyVolume} />
            <View style={styles.metricGrid}>
              <SimpleMetricCard
                label="Volumen total"
                value={totalWeeklyVolume.toFixed(1)}
                detail="Suma promedio de series-equivalentes por semana"
              />
              <SimpleMetricCard
                label="Musculos activos"
                value={`${volumeData.length}`}
                detail="Grupos con estimulo real en el plan"
              />
            </View>
          </>
        ) : null}

        {metric === 'strength' ? (
          <>
            <RelativeStrengthWidget lifts={strengthLifts} bodyweight={bodyweight} />
            <View style={styles.metricGrid}>
              <SimpleMetricCard
                label="Peso corporal"
                value={bodyweight ? `${bodyweight} kg` : '--'}
                detail="Base usada para fuerza relativa"
              />
              <SimpleMetricCard
                label="Top lifts"
                value={`${strengthLifts.length}`}
                detail="Levantamientos con e1RM valido en este programa"
              />
            </View>
          </>
        ) : null}

        {metric === 'density' ? (
          <View style={styles.metricGrid}>
            <SimpleMetricCard
              label="Sets por sesion"
              value={averageSetsPerSession ? averageSetsPerSession.toFixed(1) : '--'}
              detail="Promedio del arbol actual del programa"
            />
            <SimpleMetricCard
              label="Sesiones/semana"
              value={sessionsPerWeek ? sessionsPerWeek.toFixed(1) : '--'}
              detail="Distribucion media del bloque"
            />
            <SimpleMetricCard
              label="Reps sentadilla"
              value={powerliftingMetrics ? `${powerliftingMetrics.weeklyVolume.squat.totalReps}` : '--'}
              detail="Repeticiones registradas esta semana"
            />
            <SimpleMetricCard
              label="Reps banca"
              value={powerliftingMetrics ? `${powerliftingMetrics.weeklyVolume.bench.totalReps}` : '--'}
              detail="Referencia de densidad en basicos"
            />
          </View>
        ) : null}

        {metric === 'frequency' ? (
          <View style={styles.metricGrid}>
            <SimpleMetricCard
              label="Sesiones/semana"
              value={sessionsPerWeek ? sessionsPerWeek.toFixed(1) : '--'}
              detail="Frecuencia media del programa"
            />
            <SimpleMetricCard
              label="Frecuencia top"
              value={volumeData[0] ? `${volumeData[0].frequency.toFixed(1)}` : '--'}
              detail={volumeData[0] ? volumeData[0].muscleGroup : 'Sin grupos con volumen'}
            />
            <SimpleMetricCard
              label="Sesiones planificadas"
              value={`${programWeeks.reduce((sum, week) => sum + week.sessions.length, 0)}`}
              detail="Total del arbol periodizado actual"
            />
          </View>
        ) : null}

        {metric === 'banister' ? (
          <>
            <WeeklyFatigueCard />
            <View style={styles.metricGrid}>
              <SimpleMetricCard
                label="Observaciones"
                value={`${adaptiveCache.totalObservations}`}
                detail="Muestras acumuladas en cache adaptativa"
              />
              <SimpleMetricCard
                label="Confianza"
                value={getConfidenceLabel(adaptiveCache.totalObservations)}
                detail="Nivel heuristico de personalizacion actual"
              />
            </View>
          </>
        ) : null}

        {metric === 'recovery' ? (
          <View style={styles.metricGrid}>
            <SimpleMetricCard
              label="Bateria global"
              value={overview?.battery ? `${Math.round(overview.battery.overall)}%` : '--'}
              detail="Estado actual del readiness"
            />
            <SimpleMetricCard
              label="Bateria muscular"
              value={overview?.battery ? `${Math.round(overview.battery.muscular)}%` : '--'}
              detail="Lectura local del sistema muscular"
            />
            <SimpleMetricCard
              label="Sueno 7d"
              value={
                typedSleepLogs.length
                  ? `${Math.round(
                      (typedSleepLogs.reduce(
                        (sum, log) => sum + Number(log?.duration ?? 0),
                        0,
                      ) / typedSleepLogs.length) * 10,
                    ) / 10}h`
                  : '--'
              }
              detail="Promedio tomado desde wellbeing"
            />
          </View>
        ) : null}

        {metric === 'adherence' ? (
          <View style={styles.metricGrid}>
            <SimpleMetricCard
              label="Adherencia"
              value={adherence ? `${Math.round(adherence.adherencePercentage)}%` : '--'}
              detail="Sesiones completadas versus sesiones planificadas"
            />
            <SimpleMetricCard
              label="Completadas"
              value={adherence ? `${adherence.completedSessions}` : '--'}
              detail="Sesiones unicas ya registradas"
            />
            <SimpleMetricCard
              label="Planificadas"
              value={adherence ? `${adherence.plannedSessions}` : '--'}
              detail="Sesiones unicas esperadas por el arbol"
            />
          </View>
        ) : null}

        {metric === 'rpe' ? (
          <View style={styles.metricGrid}>
            <SimpleMetricCard
              label="RPE medio"
              value={averageFeedbackRpe ? averageFeedbackRpe.toFixed(1) : '--'}
              detail="Promedio del feedback post sesion en este programa"
            />
            <SimpleMetricCard
              label="Ultimo feedback"
              value={latestFeedback ? `${latestFeedback.sessionRpe}/10` : '--'}
              detail={latestFeedback ? latestFeedback.sessionName : 'Sin feedback reciente'}
            />
            <SimpleMetricCard
              label="Encuestas"
              value={`${feedbackHistory.filter(item => item.programId === programId).length}`}
              detail="Respuestas guardadas para este programa"
            />
          </View>
        ) : null}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  summaryCard: {
    borderRadius: 28,
  },
  summaryTitle: {
    fontSize: 22,
    fontWeight: '900',
    marginBottom: 8,
  },
  summaryCopy: {
    fontSize: 14,
    lineHeight: 21,
  },
  metricGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  metricCard: {
    width: '48%',
    borderWidth: 1,
    borderRadius: 20,
    padding: 16,
  },
  metricLabel: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  metricValue: {
    fontSize: 24,
    fontWeight: '900',
    marginTop: 4,
    marginBottom: 6,
  },
  metricDetail: {
    fontSize: 12,
    lineHeight: 17,
  },
  emptyCard: {
    borderRadius: 28,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '800',
    marginBottom: 6,
  },
  emptyText: {
    fontSize: 14,
    lineHeight: 20,
  },
});
