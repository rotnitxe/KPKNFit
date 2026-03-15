import React, { useMemo } from 'react';
import { Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { PrimaryButton } from '../components/PrimaryButton';
import { ScreenShell } from '../components/ScreenShell';
import { useLocalAiDiagnosticsStore } from '../stores/localAiDiagnosticsStore';
import { useNutritionFlowDiagnosticsStore } from '../stores/nutritionFlowDiagnosticsStore';
import { useBootstrapStore } from '../stores/bootstrapStore';
import { useMobileNutritionStore } from '../stores/nutritionStore';
import { useWorkoutStore } from '../stores/workoutStore';
import { useSettingsStore } from '../stores/settingsStore';
import { useWellbeingStore } from '../stores/wellbeingStore';
import type { RootTabParamList } from '../navigation/AppNavigator';

function getLocalDateKey(date = new Date()) {
  return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')}`;
}

function MetricCard({ label, value, detail }: { label: string; value: string; detail: string }) {
  return (
    <View className="min-w-[140px] flex-1 rounded-card border border-white/10 bg-kpkn-surface px-4 py-4">
      <Text className="text-xs uppercase tracking-[1.5px] text-kpkn-muted">{label}</Text>
      <Text className="mt-3 text-2xl font-semibold text-kpkn-text">{value}</Text>
      <Text className="mt-2 text-sm leading-5 text-kpkn-muted">{detail}</Text>
    </View>
  );
}

function DashboardCard({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  children?: React.ReactNode;
}) {
  return (
    <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
      <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">{eyebrow}</Text>
      <Text className="mt-3 text-2xl font-semibold text-kpkn-text">{title}</Text>
      <Text className="mt-2 text-base leading-6 text-kpkn-muted">{description}</Text>
      {children}
    </View>
  );
}

export function HomeScreen() {
  const navigation = useNavigation<BottomTabNavigationProp<RootTabParamList>>();
  const status = useBootstrapStore(state => state.status);
  const summary = useBootstrapStore(state => state.summary);
  const hydrationResult = useBootstrapStore(state => state.hydrationResult);
  const error = useBootstrapStore(state => state.error);
  const savedLogs = useMobileNutritionStore(state => state.savedLogs);
  const nutritionHydrated = useMobileNutritionStore(state => state.hasHydrated);
  const workoutOverview = useWorkoutStore(state => state.overview);
  const workoutStatus = useWorkoutStore(state => state.status);
  const settingsSummary = useSettingsStore(state => state.summary);
  const wellbeingOverview = useWellbeingStore(state => state.overview);
  const aiStatus = useLocalAiDiagnosticsStore(state => state.status);
  const nativeDiagnostics = useLocalAiDiagnosticsStore(state => state.nativeDiagnostics);
  const aiRefreshState = useLocalAiDiagnosticsStore(state => state.refreshState);
  const smokeTestState = useLocalAiDiagnosticsStore(state => state.smokeTestState);
  const smokeTestError = useLocalAiDiagnosticsStore(state => state.smokeTestError);
  const lastCheckedAt = useLocalAiDiagnosticsStore(state => state.lastCheckedAt);
  const recentRuns = useLocalAiDiagnosticsStore(state => state.recentRuns);
  const refreshAiStatus = useLocalAiDiagnosticsStore(state => state.refreshStatus);
  const runSmokeTest = useLocalAiDiagnosticsStore(state => state.runSmokeTest);
  const nutritionSmokeState = useNutritionFlowDiagnosticsStore(state => state.smokeState);
  const nutritionSmokeError = useNutritionFlowDiagnosticsStore(state => state.smokeError);
  const nutritionSmokeResult = useNutritionFlowDiagnosticsStore(state => state.lastResult);
  const runNutritionSmokeFlow = useNutritionFlowDiagnosticsStore(state => state.runSmokeFlow);

  const aiHeadline = aiStatus?.engine === 'runtime'
    ? 'Runtime listo'
    : aiStatus?.modelVersion
      ? 'Modelo detectado, pero aun en heuristicas'
      : 'Sin modelo confirmado todavia';

  const nativeCooldownSeconds = nativeDiagnostics ? Math.ceil(nativeDiagnostics.cooldownRemainingMs / 1000) : 0;
  const todaySummary = useMemo(() => {
    const today = getLocalDateKey();
    const todayLogs = savedLogs.filter(log => log.createdAt.slice(0, 10) === today);

    return todayLogs.reduce(
      (acc, log) => ({
        calories: acc.calories + log.totals.calories,
        protein: acc.protein + log.totals.protein,
        meals: acc.meals + 1,
      }),
      { calories: 0, protein: 0, meals: 0 },
    );
  }, [savedLogs]);

  const dashboardHeadline = status === 'ready'
    ? 'Todo listo para registrar y entrenar'
    : status === 'booting'
      ? 'Estamos preparando tu base'
      : 'Necesitamos revisar el arranque';

  const dashboardDescription = status === 'ready'
    ? 'La app nueva ya puede acompañarte con comida, recordatorios y la vista base de entrenamiento sin depender de la app vieja para el dia a dia.'
    : error ?? 'Estamos levantando la app y comprobando que tu informacion local entre bien en esta base React Native.';

  const nextSessionLabel = workoutOverview?.nextSession?.name ?? workoutOverview?.todaySession?.name ?? 'Sin sesion proxima';
  const nextSessionDetail = workoutOverview?.activeProgramName
    ? `${workoutOverview.activeProgramName}${workoutOverview.nextSessionOffsetDays !== null ? ` · en ${workoutOverview.nextSessionOffsetDays} dia${workoutOverview.nextSessionOffsetDays === 1 ? '' : 's'}` : ''}`
    : 'Cuando migremos o actives un programa, aqui vas a ver tu proxima sesion.';

  const reminderSummary = !settingsSummary
    ? 'Ajustes todavia en preparacion'
    : settingsSummary.remindersEnabled
      ? `Entreno ${settingsSummary.reminderTime ?? 'activo'}`
      : 'Recordatorios de entreno apagados';
  const mealReminderSummary = !settingsSummary
    ? 'Cuando la migracion termine, aqui veras los horarios que ya traigamos desde la app anterior.'
    : settingsSummary.mealRemindersEnabled
      ? `Comidas ${settingsSummary.breakfastReminderTime} / ${settingsSummary.lunchReminderTime} / ${settingsSummary.dinnerReminderTime}`
      : 'Recordatorios de comida apagados';

  return (
    <ScreenShell
      title="Inicio"
      subtitle="La nueva app movil ya junta nutricion, entrenamiento y tus ajustes base en una sola experiencia nativa."
    >
      <View className="gap-4">
        <DashboardCard
          eyebrow="Resumen"
          title={dashboardHeadline}
          description={dashboardDescription}
        >
          <View className="mt-4 gap-3">
            <PrimaryButton label="Registrar comida" onPress={() => navigation.navigate('Nutrition')} />
            <PrimaryButton label="Abrir entreno" onPress={() => navigation.navigate('Workout')} tone="secondary" />
          </View>
        </DashboardCard>

        <View className="flex-row flex-wrap gap-3">
          <MetricCard
            label="Hoy"
            value={`${Math.round(todaySummary.calories)} kcal`}
            detail={`${todaySummary.meals} comida${todaySummary.meals === 1 ? '' : 's'} · ${Math.round(todaySummary.protein)} g proteína`}
          />
          <MetricCard
            label="Entreno"
            value={`${workoutOverview?.completedSetsThisWeek ?? 0}/${workoutOverview?.plannedSetsThisWeek ?? 0}`}
            detail={`${workoutOverview?.weeklySessionCount ?? 0} sesion${workoutOverview?.weeklySessionCount === 1 ? '' : 'es'} esta semana`}
          />
          <MetricCard
            label="Recuperacion"
            value={`${workoutOverview?.battery?.overall ?? 0}%`}
            detail={workoutOverview?.battery ? `CNS ${workoutOverview.battery.cns}% · muscular ${workoutOverview.battery.muscular}%` : 'Sin lectura suficiente todavia'}
          />
        </View>

        <DashboardCard
          eyebrow="Proxima sesion"
          title={nextSessionLabel}
          description={nextSessionDetail}
        >
          <View className="mt-4 gap-3">
            <PrimaryButton label="Ver progreso" onPress={() => navigation.navigate('Progress')} tone="secondary" />
          </View>
        </DashboardCard>

        <DashboardCard
          eyebrow="Recordatorios"
          title={reminderSummary}
          description={mealReminderSummary}
        >
          <View className="mt-4 gap-3">
            <PrimaryButton label="Abrir ajustes" onPress={() => navigation.navigate('Settings')} tone="secondary" />
          </View>
        </DashboardCard>

        <DashboardCard
          eyebrow="Bienestar"
          title={wellbeingOverview?.latestSleepHours ? `${wellbeingOverview.latestSleepHours} h de sueno` : 'Sin sueno importado todavia'}
          description={
            wellbeingOverview
              ? `Agua hoy ${wellbeingOverview.waterTodayMl} ml · tareas pendientes ${wellbeingOverview.pendingTaskCount} · readiness ${wellbeingOverview.latestSnapshot?.readiness ?? 'sin registro'}`
              : 'Cuando wellbeing tenga datos útiles en RN, aquí resumiremos descanso, agua y tareas pendientes.'
          }
        >
          <View className="mt-4 gap-3">
            <PrimaryButton label="Ver progreso" onPress={() => navigation.navigate('Progress')} tone="secondary" />
          </View>
        </DashboardCard>

        <DashboardCard
          eyebrow="Tu base"
          title={summary?.source === 'snapshot' ? 'Datos locales integrados' : 'Base nueva preparada'}
          description={
            summary?.validationError
              ? summary.validationError
              : summary?.recordCounts
                ? `Programas ${summary.recordCounts.programs} · historial ${summary.recordCounts.workoutHistory} · nutricion ${summary.recordCounts.nutritionLogs}`
                : 'Seguimos listos para recibir tus datos locales cuando hagamos el relevo completo desde la app anterior.'
          }
        >
          {hydrationResult ? (
            <Text className="mt-3 text-sm leading-6 text-kpkn-muted">
                {`Nutrición importada ${hydrationResult.nutritionLogsImported} · settings ${hydrationResult.settingsImported ? 'listos para usar' : 'pendientes'} · wellbeing ${hydrationResult.wellbeingImported ? 'listo para RN' : 'pendiente'} · workout ${hydrationResult.workoutStatus}`}
            </Text>
          ) : null}
          {nutritionHydrated && savedLogs.length > 0 ? (
            <Text className="mt-2 text-sm leading-6 text-kpkn-muted">
              {`Ya tienes ${savedLogs.length} comida${savedLogs.length === 1 ? '' : 's'} disponible${savedLogs.length === 1 ? '' : 's'} en esta app nueva.`}
            </Text>
          ) : null}
          {workoutStatus === 'ready' && workoutOverview?.recentLogs.length ? (
            <Text className="mt-2 text-sm leading-6 text-kpkn-muted">
              {`Tambien trajimos ${workoutOverview.recentLogs.length} registro${workoutOverview.recentLogs.length === 1 ? '' : 's'} reciente${workoutOverview.recentLogs.length === 1 ? '' : 's'} de entreno.`}
            </Text>
          ) : null}
        </DashboardCard>

        {__DEV__ ? (
          <DashboardCard
            eyebrow="Motor local (interno)"
            title={aiHeadline}
            description={
              aiStatus
                ? `Engine ${aiStatus.engine} · modelo ${aiStatus.modelVersion ?? 'sin confirmar'} · ultima revision ${lastCheckedAt ? new Date(lastCheckedAt).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' }) : 'pendiente'}`
                : 'Todavia no consultamos el estado del motor local en esta sesion.'
            }
          >
            {nativeDiagnostics ? (
              <Text className="mt-2 text-sm leading-6 text-kpkn-muted">
                {`Ruta ${nativeDiagnostics.modelPath ?? 'sin resolver'} · fallos runtime ${nativeDiagnostics.runtimeFailureCount} · cooldown ${nativeCooldownSeconds > 0 ? `${nativeCooldownSeconds}s` : 'sin bloqueo'}`}
              </Text>
            ) : null}
            {aiStatus?.lastError ? (
              <Text className="mt-3 text-sm leading-6 text-amber-200">{aiStatus.lastError}</Text>
            ) : null}
            {smokeTestError ? (
              <Text className="mt-3 text-sm leading-6 text-amber-200">{smokeTestError}</Text>
            ) : null}
            <View className="mt-4 gap-3">
              <PrimaryButton
                testID="local-ai-refresh-status"
                label={aiRefreshState === 'refreshing' ? 'Revisando motor...' : 'Actualizar estado'}
                onPress={() => void refreshAiStatus('status')}
                tone="secondary"
                disabled={aiRefreshState === 'refreshing' || smokeTestState === 'running'}
              />
              <PrimaryButton
                testID="local-ai-warmup"
                label={aiRefreshState === 'refreshing' ? 'Preparando...' : 'Intentar warmup'}
                onPress={() => void refreshAiStatus('warmup')}
                tone="secondary"
                disabled={aiRefreshState === 'refreshing' || smokeTestState === 'running'}
              />
              <PrimaryButton
                testID="local-ai-smoke-test"
                label={smokeTestState === 'running' ? 'Probando motor...' : 'Probar analisis local'}
                onPress={() => void runSmokeTest()}
                tone="secondary"
                disabled={smokeTestState === 'running' || aiRefreshState === 'refreshing'}
              />
            </View>
            {recentRuns.length > 0 ? (
              <View className="mt-5 gap-3">
                <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Ultimos analisis</Text>
                {recentRuns.slice(0, 3).map(run => (
                  <View
                    key={`${run.analyzedAt}-${run.descriptionPreview}`}
                    className="rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4"
                  >
                    <Text className="text-base font-semibold text-kpkn-text">{run.descriptionPreview}</Text>
                    <Text className="mt-1 text-sm text-kpkn-muted">
                      {run.engine} · {run.itemCount} items · {Math.round(run.elapsedMs)} ms · confianza {Math.round(run.overallConfidence * 100)}%
                    </Text>
                    {run.runtimeError ? (
                      <Text className="mt-2 text-sm leading-5 text-amber-200">{run.runtimeError}</Text>
                    ) : null}
                  </View>
                ))}
              </View>
            ) : null}
            {nativeDiagnostics?.recentEvents?.length ? (
              <View className="mt-5 gap-3">
                <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Eventos nativos</Text>
                {nativeDiagnostics.recentEvents.slice().reverse().slice(0, 4).map((event, index) => (
                  <View
                    key={`${event.timestampMs}-${event.scope}-${index}`}
                    className="rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4"
                  >
                    <Text className="text-sm font-semibold text-kpkn-text">
                      {event.scope} · {event.level}
                    </Text>
                    <Text className="mt-1 text-sm leading-5 text-kpkn-muted">
                      {new Date(event.timestampMs).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', second: '2-digit' })} · {event.message}
                    </Text>
                  </View>
                ))}
              </View>
            ) : null}
          </DashboardCard>
        ) : null}

        {__DEV__ ? (
          <DashboardCard
            eyebrow="Migracion (interno)"
            title={summary?.validationError ? 'Snapshot invalido' : summary?.source === 'snapshot' ? 'Snapshot encontrado' : 'Sin snapshot todavia'}
            description={
              summary?.validationError
                ? summary.validationError
                : summary?.recordCounts
                  ? `Programas ${summary.recordCounts.programs} · historial ${summary.recordCounts.workoutHistory} · nutricion ${summary.recordCounts.nutritionLogs}`
                  : 'Este panel sirve para revisar importación, fallback y errores sin contaminar la experiencia del usuario.'
            }
          >
            {hydrationResult ? (
              <Text className="mt-3 text-sm leading-6 text-kpkn-muted">
                {`Nutrición importada ${hydrationResult.nutritionLogsImported} · descartada ${hydrationResult.nutritionLogsDiscarded} · settings ${hydrationResult.settingsImported ? 'RN-owned' : 'pendiente'} · wellbeing ${hydrationResult.wellbeingImported ? 'RN-owned' : 'pendiente'} · workout ${hydrationResult.workoutStatus}`}
              </Text>
            ) : null}
            {hydrationResult?.errors.length ? (
              <Text className="mt-2 text-sm leading-6 text-amber-200">
                {hydrationResult.errors.join(' | ')}
              </Text>
            ) : null}
          </DashboardCard>
        ) : null}

        {__DEV__ ? (
          <DashboardCard
            eyebrow="Flujo nutricion (interno)"
            title="Smoke test de comida"
            description="Esta prueba ejecuta analisis, guardado aislado y verificacion de persistencia con una comida de ejemplo."
          >
            <View className="mt-4 gap-3">
              <PrimaryButton
                testID="nutrition-flow-smoke-test"
                label={nutritionSmokeState === 'running' ? 'Probando flujo...' : 'Probar flujo nutricion'}
                onPress={() => void runNutritionSmokeFlow()}
                tone="secondary"
                disabled={nutritionSmokeState === 'running'}
              />
            </View>
            {nutritionSmokeError ? (
              <Text className="mt-3 text-sm leading-6 text-amber-200">{nutritionSmokeError}</Text>
            ) : null}
            {nutritionSmokeResult ? (
              <View className="mt-5 rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4">
                <Text className="text-base font-semibold text-kpkn-text">{nutritionSmokeResult.description}</Text>
                <Text className="mt-1 text-sm leading-6 text-kpkn-muted">
                  {`${nutritionSmokeResult.engine} · ${Math.round(nutritionSmokeResult.elapsedMs)} ms · ${nutritionSmokeResult.itemCount} items · ${Math.round(nutritionSmokeResult.calories)} kcal`}
                </Text>
                <Text className="mt-1 text-sm leading-6 text-kpkn-muted">
                  {nutritionSmokeResult.persisted
                    ? `Guardado validado (${nutritionSmokeResult.savedEntryId})`
                    : 'El analisis corrio, pero no encontramos el guardado en SQLite.'}
                </Text>
                {nutritionSmokeResult.runtimeError ? (
                  <Text className="mt-2 text-sm leading-6 text-amber-200">{nutritionSmokeResult.runtimeError}</Text>
                ) : null}
              </View>
            ) : null}
          </DashboardCard>
        ) : null}
      </View>
    </ScreenShell>
  );
}
