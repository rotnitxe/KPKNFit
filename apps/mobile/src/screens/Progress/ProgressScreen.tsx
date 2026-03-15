import React, { useEffect, useMemo } from 'react';
import { Pressable, Text, View } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { PrimaryButton } from '../../components/PrimaryButton';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';

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

export function ProgressScreen() {
  const savedLogs = useMobileNutritionStore(state => state.savedLogs);
  const workoutOverview = useWorkoutStore(state => state.overview);
  const wellbeingStatus = useWellbeingStore(state => state.status);
  const wellbeingOverview = useWellbeingStore(state => state.overview);
  const wellbeingTasks = useWellbeingStore(state => state.tasks);
  const wellbeingNotice = useWellbeingStore(state => state.notice);
  const hydrateWellbeing = useWellbeingStore(state => state.hydrateFromMigration);
  const logWater = useWellbeingStore(state => state.logWater);
  const toggleTask = useWellbeingStore(state => state.toggleTask);
  const clearNotice = useWellbeingStore(state => state.clearNotice);

  useEffect(() => {
    if (wellbeingStatus === 'idle') {
      void hydrateWellbeing();
    }
  }, [hydrateWellbeing, wellbeingStatus]);

  useEffect(() => {
    if (!wellbeingNotice) return undefined;
    const timeout = setTimeout(() => {
      clearNotice();
    }, 3000);
    return () => clearTimeout(timeout);
  }, [clearNotice, wellbeingNotice]);

  const nutritionSummary = useMemo(() => {
    const today = getLocalDateKey();
    const last7Days = new Set(
      Array.from({ length: 7 }).map((_, index) => {
        const date = new Date();
        date.setDate(date.getDate() - index);
        return getLocalDateKey(date);
      }),
    );

    const todayLogs = savedLogs.filter(log => log.createdAt.slice(0, 10) === today);
    const last7Logs = savedLogs.filter(log => last7Days.has(log.createdAt.slice(0, 10)));

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

  return (
    <ScreenShell
      title="Progreso"
      subtitle="Una vista simple para revisar cómo va tu semana: comida registrada, entrenos y señales prácticas del plan."
    >
      <View className="gap-4">
        <View className="flex-row flex-wrap gap-3">
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

        <View className="flex-row flex-wrap gap-3">
          <MetricCard
            label="Sueño"
            value={wellbeingOverview?.averageSleepHoursLast7Days ? `${wellbeingOverview.averageSleepHoursLast7Days} h` : '--'}
            detail={wellbeingOverview ? `${wellbeingOverview.sleepEntriesLast7Days} registros en 7 dias` : 'Sin datos de sueño todavía'}
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

        <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
          <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Entrenamiento</Text>
          <Text className="mt-3 text-xl font-semibold text-kpkn-text">
            {workoutOverview?.activeProgramName ?? 'Sin programa activo'}
          </Text>
          <Text className="mt-2 text-base leading-6 text-kpkn-muted">
            {workoutOverview
              ? `${workoutOverview.weeklySessionCount} entreno${workoutOverview.weeklySessionCount === 1 ? '' : 's'} esta semana · ${workoutOverview.completedSetsThisWeek}/${workoutOverview.plannedSetsThisWeek} series`
              : 'Cuando tu programa migrado esté listo aquí verás tu carga semanal y tus últimas sesiones.'}
          </Text>
        </View>

        {wellbeingOverview ? (
          <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
            <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Bienestar</Text>
            <Text className="mt-3 text-xl font-semibold text-kpkn-text">
              {wellbeingOverview.latestSnapshot?.moodState ?? 'Sin mood registrado'}
            </Text>
            <Text className="mt-2 text-base leading-6 text-kpkn-muted">
              {`Readiness ${wellbeingOverview.latestSnapshot?.readiness ?? 'sin dato'} · sueño ${wellbeingOverview.latestSleepHours ?? 'sin dato'} h · agua ${wellbeingOverview.waterTodayMl} ml`}
            </Text>
            <View className="mt-4 gap-3">
              <PrimaryButton label="+ 500 ml de agua" onPress={() => void logWater(500)} tone="secondary" />
              <PrimaryButton label="+ 1 L de agua" onPress={() => void logWater(1000)} tone="secondary" />
            </View>
            {wellbeingTasks.length > 0 ? (
              <View className="mt-4 gap-3">
                <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Tareas</Text>
                {wellbeingTasks.slice(0, 3).map(task => (
                  <Pressable
                    key={task.id}
                    className="rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4"
                    onPress={() => void toggleTask(task.id)}
                  >
                    <Text className="text-base font-semibold text-kpkn-text">{task.title}</Text>
                    <Text className="mt-1 text-sm text-kpkn-muted">
                      {task.completed ? 'Marcada como lista' : 'Toca hacerla'}
                    </Text>
                  </Pressable>
                ))}
              </View>
            ) : null}
          </View>
        ) : null}

        {wellbeingNotice ? (
          <View className="rounded-card border border-emerald-400/25 bg-emerald-500/10 px-4 py-4">
            <Text className="text-base font-medium text-white">{wellbeingNotice}</Text>
          </View>
        ) : null}

        {savedLogs.length > 0 ? (
          <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
            <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Últimas comidas</Text>
            <View className="mt-4 gap-3">
              {savedLogs.slice(0, 5).map(log => (
                <View key={log.id} className="rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4">
                  <Text className="text-base font-semibold text-kpkn-text">{log.description}</Text>
                  <Text className="mt-1 text-sm text-kpkn-muted">
                    {`${Math.round(log.totals.calories)} kcal · ${Math.round(log.totals.protein)}p · ${Math.round(log.totals.carbs)}c · ${Math.round(log.totals.fats)}g`}
                  </Text>
                  <Text className="mt-2 text-sm text-kpkn-muted">{new Date(log.createdAt).toLocaleString('es-CL')}</Text>
                </View>
              ))}
            </View>
          </View>
        ) : null}

        {workoutOverview?.recentLogs?.length ? (
          <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
            <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Últimos entrenos</Text>
            <View className="mt-4 gap-3">
              {workoutOverview.recentLogs.map(log => (
                <View key={log.id} className="rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4">
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
