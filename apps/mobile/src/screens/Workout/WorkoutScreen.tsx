import React, { useEffect } from 'react';
import { Text, View } from 'react-native';
import { useShallow } from 'zustand/react/shallow';
import { ScreenShell } from '../../components/ScreenShell';
import { PrimaryButton } from '../../components/PrimaryButton';
import { useWorkoutStore } from '../../stores/workoutStore';

function MetricPill({ label, value }: { label: string; value: string }) {
  return (
    <View className="rounded-3xl border border-white/10 bg-white/5 px-4 py-3">
      <Text className="text-xs uppercase tracking-[1.5px] text-kpkn-muted">{label}</Text>
      <Text className="mt-2 text-lg font-semibold text-kpkn-text">{value}</Text>
    </View>
  );
}

function SessionCard({
  eyebrow,
  title,
  detail,
  meta,
}: {
  eyebrow: string;
  title: string;
  detail: string;
  meta: string;
}) {
  return (
    <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
      <Text className="text-xs uppercase tracking-[1.5px] text-kpkn-muted">{eyebrow}</Text>
      <Text className="mt-3 text-xl font-semibold text-kpkn-text">{title}</Text>
      <Text className="mt-2 text-base leading-6 text-kpkn-muted">{detail}</Text>
      <Text className="mt-3 text-sm text-kpkn-muted">{meta}</Text>
    </View>
  );
}

export function WorkoutScreen() {
  const {
    status,
    overview,
    notice,
    errorMessage,
    loggingState,
    hydrateFromMigration,
    refreshInfrastructure,
    logTodaySession,
    startRestTimer,
    cancelRestTimer,
    clearNotice,
  } = useWorkoutStore(useShallow(state => ({
    status: state.status,
    overview: state.overview,
    notice: state.notice,
    errorMessage: state.errorMessage,
    loggingState: state.loggingState,
    hydrateFromMigration: state.hydrateFromMigration,
    refreshInfrastructure: state.refreshInfrastructure,
    logTodaySession: state.logTodaySession,
    startRestTimer: state.startRestTimer,
    cancelRestTimer: state.cancelRestTimer,
    clearNotice: state.clearNotice,
  })));

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

  return (
      <ScreenShell
        title="Entrenamiento"
        subtitle="Tu vista nativa de entrenamiento ahora también puede registrar rápido la sesión de hoy, además de mantener widgets y recordatorios al día."
      >
      <View className="gap-4">
        <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
          <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Estado</Text>
          <Text className="mt-3 text-2xl font-semibold text-kpkn-text">
            {status === 'ready'
              ? overview?.activeProgramName ?? 'Programa cargado'
              : status === 'empty'
                ? 'Sin programa activo'
                : status === 'failed'
                  ? 'No pudimos abrir entrenamiento'
                  : 'Preparando entrenamiento...'}
          </Text>
          <Text className="mt-2 text-base leading-6 text-kpkn-muted">
            {status === 'ready'
              ? `Semana ${overview?.currentWeekId ?? 'actual'} · ${overview?.weeklySessionCount ?? 0} entrenos registrados esta semana`
              : status === 'empty'
                ? 'Cuando el snapshot de programas llegue a RN, aquí verás tu semana, tus últimas sesiones y los recordatorios clave.'
                : errorMessage ?? 'Estamos revisando el estado del programa migrado.'}
          </Text>
          {notice ? (
            <Text className="mt-3 text-sm leading-6 text-emerald-200">{notice}</Text>
          ) : null}
        </View>

        {overview?.todaySession ? (
          <SessionCard
            eyebrow="Hoy"
            title={overview.todaySession.name}
            detail={overview.todaySession.focus ?? 'Sesión principal del día'}
            meta={`${overview.todaySession.exerciseCount} ejercicios · ${overview.todaySession.setCount} series`}
          />
        ) : null}

        {overview?.nextSession ? (
          <SessionCard
            eyebrow={overview.nextSessionOffsetDays === 0 ? 'Sigue hoy' : 'Próxima'}
            title={overview.nextSession.name}
            detail={overview.nextSessionOffsetDays === 0
              ? 'Aún está disponible para registrarla hoy.'
              : `Viene en ${overview.nextSessionOffsetDays} día${overview.nextSessionOffsetDays === 1 ? '' : 's'}.`}
            meta={`${overview.nextSession.exerciseCount} ejercicios · ${overview.nextSession.setCount} series`}
          />
        ) : null}

        {overview ? (
          <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
            <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Semana</Text>
            <View className="mt-4 flex-row flex-wrap gap-3">
              <MetricPill label="Series hechas" value={String(overview.completedSetsThisWeek)} />
              <MetricPill label="Series plan" value={String(overview.plannedSetsThisWeek)} />
              <MetricPill
                label="Sesiones"
                value={`${overview.weeklySessionCount}${overview.hasWorkoutLoggedToday ? ' · hoy listo' : ''}`}
              />
            </View>
          </View>
        ) : null}

        {overview?.battery ? (
          <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
            <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Recuperación estimada</Text>
            <View className="mt-4 flex-row flex-wrap gap-3">
              <MetricPill label="Global" value={`${Math.round(overview.battery.overall)}%`} />
              <MetricPill label="CNS" value={`${Math.round(overview.battery.cns)}%`} />
              <MetricPill label="Muscular" value={`${Math.round(overview.battery.muscular)}%`} />
              <MetricPill label="Espinal" value={`${Math.round(overview.battery.spinal)}%`} />
            </View>
            <Text className="mt-3 text-sm leading-6 text-kpkn-muted">
              Esta batería sigue siendo una estimación RN mientras el motor AUGE completo termina de migrar.
            </Text>
          </View>
        ) : null}

        {overview?.upcomingEvent ? (
          <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
            <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Evento próximo</Text>
            <Text className="mt-3 text-xl font-semibold text-kpkn-text">{overview.upcomingEvent.title}</Text>
            <Text className="mt-2 text-base leading-6 text-kpkn-muted">
              {overview.upcomingEvent.daysUntil === 0
                ? 'Es hoy.'
                : `Faltan ${overview.upcomingEvent.daysUntil} día${overview.upcomingEvent.daysUntil === 1 ? '' : 's'}.`}
            </Text>
          </View>
        ) : null}

        <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
          <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Acciones rápidas</Text>
          <View className="mt-4 gap-3">
            <PrimaryButton
              label="Actualizar widgets y recordatorios"
              onPress={() => void refreshInfrastructure()}
            />
            <PrimaryButton
              label={loggingState === 'saving' ? 'Registrando sesión...' : overview?.hasWorkoutLoggedToday ? 'Sesión de hoy ya registrada' : 'Registrar sesión de hoy'}
              onPress={() => void logTodaySession()}
              disabled={loggingState === 'saving' || !overview?.todaySession || overview.hasWorkoutLoggedToday}
              tone="secondary"
            />
            <PrimaryButton
              label="Descanso 60s"
              onPress={() => void startRestTimer(60)}
              tone="secondary"
            />
            <PrimaryButton
              label="Descanso 90s"
              onPress={() => void startRestTimer(90)}
              tone="secondary"
            />
            <PrimaryButton
              label="Cancelar temporizador"
              onPress={() => void cancelRestTimer()}
              tone="secondary"
            />
          </View>
        </View>

        {overview?.recentLogs?.length ? (
          <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
            <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Últimos entrenos</Text>
            <View className="mt-4 gap-3">
              {overview.recentLogs.map(log => (
                <View
                  key={`${log.id}-${log.date}`}
                  className="rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4"
                >
                  <Text className="text-base font-semibold text-kpkn-text">{log.sessionName}</Text>
                  <Text className="mt-1 text-sm text-kpkn-muted">
                    {`${log.programName} · ${log.date}`}
                  </Text>
                  <Text className="mt-2 text-sm text-kpkn-muted">
                    {`${log.exerciseCount} ejercicios · ${log.completedSetCount} series${log.durationMinutes ? ` · ${log.durationMinutes} min` : ''}`}
                  </Text>
                </View>
              ))}
            </View>
          </View>
        ) : null}
      </View>
    </ScreenShell>
  );
}
