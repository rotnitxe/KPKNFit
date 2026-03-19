import React, { useEffect, useState, useCallback, memo } from 'react';
import { Text, View, Pressable, StyleSheet, ScrollView } from 'react-native';
import { useShallow } from 'zustand/react/shallow';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { WorkoutStackParamList } from '../../navigation/types';
import type { Session } from '../../types/workout';
import { ScreenShell } from '../../components/ScreenShell';
import { Button } from '../../components/ui';
import { useProgramStore } from '../../stores/programStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { ReadinessModal } from '../../components/workout/ReadinessModal';
import { StartWorkoutDrawer } from '../../components/workout/StartWorkoutDrawer';
import { useColors } from '../../theme';

type WorkoutNavProp = NativeStackNavigationProp<WorkoutStackParamList>;

const MetricPill = memo(({ label, value }: { label: string; value: string }) => {
  const colors = useColors();
  return (
    <View style={[styles.metricPill, { backgroundColor: `${colors.onSurface}0D`, borderColor: `${colors.onSurface}1A` }]}>
      <Text style={[styles.metricLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <Text style={[styles.metricValue, { color: colors.onSurface }]}>{value}</Text>
    </View>
  );
});

const SessionCard = memo(({
  id,
  eyebrow,
  title,
  detail,
  meta,
}: {
  id?: string;
  eyebrow: string;
  title: string;
  detail: string;
  meta: string;
}) => {
  const colors = useColors();
  const navigation = useNavigation<WorkoutNavProp>();

  const handlePress = useCallback(() => {
    if (id) {
      navigation.navigate('SessionDetail', { sessionId: id });
    }
  }, [id, navigation]);

  return (
    <Pressable 
      onPress={handlePress}
      disabled={!id}
      style={({ pressed }) => [
        styles.sessionCard,
        { 
          backgroundColor: colors.surface, 
          borderColor: `${colors.onSurface}1A`,
          opacity: pressed ? 0.7 : 1,
        }
      ]}
    >
      <Text style={[styles.cardEyebrow, { color: colors.onSurfaceVariant }]}>{eyebrow}</Text>
      <Text style={[styles.cardTitle, { color: colors.onSurface }]}>{title}</Text>
      <Text style={[styles.cardDetail, { color: colors.onSurfaceVariant }]}>{detail}</Text>
      <Text style={[styles.cardMeta, { color: colors.onSurfaceVariant }]}>{meta}</Text>
    </Pressable>
  );
});

export function WorkoutScreen() {
  const colors = useColors();
  const [isReadinessVisible, setReadinessVisible] = useState(false);
  const [isStartDrawerVisible, setStartDrawerVisible] = useState(false);
  const programs = useProgramStore(state => state.programs);

  const {
    status,
    overview,
    notice,
    errorMessage,
    loggingState,
    readinessScore,
    hydrateFromMigration,
    refreshInfrastructure,
    logTodaySession,
    startRestTimer,
    cancelRestTimer,
    setReadinessScore,
    startActiveSession,
    activeSession,
    clearNotice,
  } = useWorkoutStore(useShallow(state => ({
    status: state.status,
    overview: state.overview,
    notice: state.notice,
    errorMessage: state.errorMessage,
    loggingState: state.loggingState,
    readinessScore: state.readinessScore,
    hydrateFromMigration: state.hydrateFromMigration,
    refreshInfrastructure: state.refreshInfrastructure,
    logTodaySession: state.logTodaySession,
    startRestTimer: state.startRestTimer,
    cancelRestTimer: state.cancelRestTimer,
    setReadinessScore: state.setReadinessScore,
    startActiveSession: state.startActiveSession,
    activeSession: state.activeSession,
    clearNotice: state.clearNotice,
  })));

  const navigation = useNavigation<WorkoutNavProp>();

  useEffect(() => {
    if (status === 'idle') {
      void hydrateFromMigration();
    }
  }, [hydrateFromMigration, status]);

  useEffect(() => {
    if (!notice) return undefined;
    const timeout = setTimeout(() => {
      clearNotice();
    }, 3000);
    return () => clearTimeout(timeout);
  }, [clearNotice, notice]);

  const handleReadinessComplete = useCallback((data: { sleep: number; mood: number; soreness: number }) => {
    setReadinessScore(data);
    setReadinessVisible(false);
  }, [setReadinessScore]);

  const handleResumeActive = useCallback(() => {
    if (activeSession) {
      navigation.navigate('ActiveSession', {
        programId: activeSession.programId,
        sessionId: activeSession.session.id,
        sessionName: activeSession.session.name,
      });
    }
  }, [activeSession, navigation]);

  const handleStartFromDrawer = useCallback((payload: {
    key: 'A' | 'B' | 'C' | 'D';
    session: Session;
    programId: string;
  }) => {
    startActiveSession({ programId: payload.programId, session: payload.session });
    setStartDrawerVisible(false);
    navigation.navigate('ActiveSession', {
      programId: payload.programId,
      sessionId: payload.session.id,
      sessionName: payload.session.name,
    });
  }, [navigation, startActiveSession]);

  const handleRefresh = useCallback(() => {
    void refreshInfrastructure();
  }, [refreshInfrastructure]);

  const handleLogToday = useCallback(() => {
    void logTodaySession();
  }, [logTodaySession]);

  return (
    <ScreenShell
      title="Entrenamiento"
      subtitle="Tu vista nativa de entrenamiento ahora también puede registrar rápido la sesión de hoy, además de mantener widgets y recordatorios al día."
    >
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        <View style={styles.content}>
          <View style={[styles.statusCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
            <Text style={[styles.sectionHeading, { color: colors.onSurfaceVariant }]}>Estado</Text>
            <Text style={[styles.statusTitle, { color: colors.onSurface }]}>
              {status === 'ready'
                ? overview?.activeProgramName ?? 'Programa cargado'
                : status === 'empty'
                  ? 'Sin programa activo'
                  : status === 'failed'
                    ? 'No pudimos abrir entrenamiento'
                    : 'Preparando entrenamiento...'}
            </Text>
            <Text style={[styles.statusDetail, { color: colors.onSurfaceVariant }]}>
              {status === 'ready'
                ? `Semana ${overview?.currentWeekId ?? 'actual'} · ${overview?.weeklySessionCount ?? 0} entrenos registrados esta semana`
                : status === 'empty'
                  ? 'La versión nativa ya debería dejarte retomar el mismo flujo de la PWA. Si todavía no hay un programa activo, entremos directo a la biblioteca.'
                  : errorMessage ?? 'Estamos revisando el estado del programa migrado.'}
            </Text>
            {notice ? (
              <Text style={[styles.noticeText, { color: colors.cyberSuccess }]}>{notice}</Text>
            ) : null}
            {status === 'empty' ? (
              <View style={styles.emptyActions}>
                <Button onPress={() => navigation.navigate('ProgramsList')} variant="primary">
                  Ir a programas
                </Button>
                {programs.slice(0, 2).map(program => (
                  <Pressable
                    key={program.id}
                    onPress={() => navigation.navigate('ProgramDetail', { programId: program.id })}
                    style={[styles.historyCard, { backgroundColor: colors.surfaceContainer, borderColor: `${colors.onSurface}14` }]}
                  >
                    <Text style={[styles.historyTitle, { color: colors.onSurface }]}>{program.name}</Text>
                    <Text style={[styles.historyMeta, { color: colors.onSurfaceVariant }]}>
                      {(program.macrocycles?.[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.[0]?.sessions?.length ?? 0)} sesiones en la primera semana
                    </Text>
                  </Pressable>
                ))}
              </View>
            ) : null}
          </View>

          {overview?.todaySession ? (
            <SessionCard
              id={overview.todaySession.id}
              eyebrow="Hoy"
              title={overview.todaySession.name}
              detail={overview.todaySession.focus ?? 'Sesión principal del día'}
              meta={`${overview.todaySession.exerciseCount} ejercicios · ${overview.todaySession.setCount} series`}
            />
          ) : null}

          {overview?.nextSession ? (
            <SessionCard
              id={overview.nextSession.id}
              eyebrow={overview.nextSessionOffsetDays === 0 ? 'Sigue hoy' : 'Próxima'}
              title={overview.nextSession.name}
              detail={overview.nextSessionOffsetDays === 0
                ? 'Aún está disponible para registrarla hoy.'
                : `Viene en ${overview.nextSessionOffsetDays} día${overview.nextSessionOffsetDays === 1 ? '' : 's'}.`}
              meta={`${overview.nextSession.exerciseCount} ejercicios · ${overview.nextSession.setCount} series`}
            />
          ) : null}

          {overview && (
            <View style={[styles.statusCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
              <Text style={[styles.sectionHeading, { color: colors.onSurfaceVariant }]}>Semana</Text>
              <View style={styles.metricsGrid}>
                <MetricPill label="Series hechas" value={String(overview.completedSetsThisWeek)} />
                <MetricPill label="Series plan" value={String(overview.plannedSetsThisWeek)} />
                <MetricPill
                  label="Sesiones"
                  value={`${overview.weeklySessionCount}${overview.hasWorkoutLoggedToday ? ' · hoy listo' : ''}`}
                />
              </View>
            </View>
          )}

          {overview?.battery && (
            <View style={[styles.statusCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
              <Text style={[styles.sectionHeading, { color: colors.onSurfaceVariant }]}>Recuperación estimada</Text>
              <View style={styles.metricsGrid}>
                <MetricPill label="Global" value={`${Math.round(overview.battery.overall)}%`} />
                <MetricPill label="CNS" value={`${Math.round(overview.battery.cns)}%`} />
                <MetricPill label="Muscular" value={`${Math.round(overview.battery.muscular)}%`} />
                <MetricPill label="Espinal" value={`${Math.round(overview.battery.spinal)}%`} />
              </View>
              <Text style={[styles.batteryNote, { color: colors.onSurfaceVariant }]}>
                Esta batería sigue siendo una estimación RN mientras el motor AUGE completo termina de migrar.
              </Text>
            </View>
          )}

          {overview?.upcomingEvent && (
            <View style={[styles.statusCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
              <Text style={[styles.sectionHeading, { color: colors.onSurfaceVariant }]}>Evento próximo</Text>
              <Text style={[styles.eventTitle, { color: colors.onSurface }]}>{overview.upcomingEvent.title}</Text>
              <Text style={[styles.eventDetail, { color: colors.onSurfaceVariant }]}>
                {overview.upcomingEvent.daysUntil === 0
                  ? 'Es hoy.'
                  : `Faltan ${overview.upcomingEvent.daysUntil} día${overview.upcomingEvent.daysUntil === 1 ? '' : 's'}.`}
              </Text>
            </View>
          )}

          <View style={[styles.statusCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
            <Text style={[styles.sectionHeading, { color: colors.onSurfaceVariant }]}>Acciones rápidas</Text>
            <View style={styles.actionsGrid}>
              {activeSession && (
                <Button
                  onPress={handleResumeActive}
                  variant="primary"
                >
                  Reanudar sesión activa
                </Button>
              )}
              <Button
                onPress={() => setStartDrawerVisible(true)}
                variant="primary"
              >
                Iniciar sesión ahora
              </Button>
              <Button
                onPress={() => setReadinessVisible(true)}
                variant={readinessScore ? "secondary" : "primary"}
              >
                {readinessScore ? 'Readiness Completado ✓' : '1. Evaluar Readiness'}
              </Button>
              <Button
                onPress={() => navigation.navigate('LogHub')}
                variant="secondary"
              >
                Abrir Log Hub
              </Button>
              <Button
                onPress={() => navigation.navigate('LogWorkout')}
                variant="secondary"
              >
                Registrar entreno manual
              </Button>
              <Button
                onPress={() => navigation.navigate('ExerciseDatabase')}
                variant="secondary"
              >
                Abrir base de ejercicios
              </Button>
              <Button
                onPress={() => navigation.navigate('WikiHome')}
                variant="secondary"
              >
                Abrir WikiLab
              </Button>
              <Button
                onPress={handleRefresh}
              >
                Actualizar widgets y recordatorios
              </Button>
              <Button
                onPress={handleLogToday}
                disabled={loggingState === 'saving' || !overview?.todaySession || overview.hasWorkoutLoggedToday}
                variant="secondary"
              >
                {loggingState === 'saving' ? 'Registrando sesión...' : overview?.hasWorkoutLoggedToday ? 'Sesión de hoy ya registrada' : 'Registrar sesión de hoy'}
              </Button>
              <Button
                onPress={() => void startRestTimer(60)}
                variant="secondary"
              >
                Descanso 60s
              </Button>
              <Button
                onPress={() => void startRestTimer(90)}
                variant="secondary"
              >
                Descanso 90s
              </Button>
              <Button
                onPress={() => void cancelRestTimer()}
                variant="secondary"
              >
                Cancelar temporizador
              </Button>
            </View>
          </View>

          {overview?.recentLogs && overview.recentLogs.length > 0 && (
            <View style={[styles.statusCard, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}>
              <Text style={[styles.sectionHeading, { color: colors.onSurfaceVariant }]}>Últimos entrenos</Text>
              <View style={styles.actionsGrid}>
                {overview.recentLogs.map(log => (
                  <View
                    key={`${log.id}-${log.date}`}
                    style={[styles.historyCard, { backgroundColor: colors.surfaceContainer, borderColor: `${colors.onSurface}14` }]}
                  >
                    <Text style={[styles.historyTitle, { color: colors.onSurface }]}>{log.sessionName}</Text>
                    <Text style={[styles.historyMeta, { color: colors.onSurfaceVariant }]}>
                      {`${log.programName} · ${log.date}`}
                    </Text>
                    <Text style={[styles.historyDetail, { color: colors.onSurfaceVariant }]}>
                      {`${log.exerciseCount} ejercicios · ${log.completedSetCount} series${log.durationMinutes ? ` · ${log.durationMinutes} min` : ''}`}
                    </Text>
                  </View>
                ))}
              </View>
            </View>
          )}
        </View>
      </ScrollView>
      <ReadinessModal 
        visible={isReadinessVisible} 
        onClose={() => setReadinessVisible(false)} 
        onComplete={handleReadinessComplete} 
      />
      <StartWorkoutDrawer
        visible={isStartDrawerVisible}
        programs={programs}
        onClose={() => setStartDrawerVisible(false)}
        onStart={handleStartFromDrawer}
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  scrollContent: {
    paddingBottom: 40,
  },
  content: {
    gap: 16,
    padding: 16,
  },
  emptyActions: {
    marginTop: 14,
    gap: 12,
  },
  statusCard: {
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 20,
  },
  sectionHeading: {
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 2,
    fontWeight: '600',
  },
  statusTitle: {
    marginTop: 12,
    fontSize: 24,
    fontWeight: 'bold',
  },
  statusDetail: {
    marginTop: 8,
    fontSize: 15,
    lineHeight: 22,
  },
  noticeText: {
    marginTop: 12,
    fontSize: 14,
    lineHeight: 20,
  },
  sessionCard: {
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 20,
  },
  cardEyebrow: {
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
  },
  cardTitle: {
    marginTop: 12,
    fontSize: 20,
    fontWeight: 'bold',
  },
  cardDetail: {
    marginTop: 8,
    fontSize: 16,
    lineHeight: 24,
  },
  cardMeta: {
    marginTop: 12,
    fontSize: 14,
  },
  metricsGrid: {
    marginTop: 16,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  metricPill: {
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  metricLabel: {
    fontSize: 11,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
  },
  metricValue: {
    marginTop: 8,
    fontSize: 18,
    fontWeight: '600',
  },
  batteryNote: {
    marginTop: 12,
    fontSize: 12,
    lineHeight: 18,
    fontStyle: 'italic',
  },
  eventTitle: {
    marginTop: 12,
    fontSize: 20,
    fontWeight: 'bold',
  },
  eventDetail: {
    marginTop: 8,
    fontSize: 16,
    lineHeight: 24,
  },
  actionsGrid: {
    marginTop: 16,
    gap: 12,
  },
  historyCard: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
  },
  historyTitle: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  historyMeta: {
    marginTop: 4,
    fontSize: 13,
  },
  historyDetail: {
    marginTop: 8,
    fontSize: 13,
  },
});
