import React, { useEffect, useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ScreenShell } from '@/components/ScreenShell';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { useBodyStore } from '@/stores/bodyStore';
import { useMobileNutritionStore } from '@/stores/nutritionStore';
import { useProgramStore } from '@/stores/programStore';
import { useWorkoutStore } from '@/stores/workoutStore';
import { useColors } from '@/theme';
import { calculateBrzycki1RM } from '@/utils/calculations';

type AchievementCategory = 'Consistencia' | 'Hitos' | 'Exploracion' | 'Dedicacion';

type AchievementRecord = {
  id: string;
  name: string;
  description: string;
  category: AchievementCategory;
  unlocked: boolean;
  unlockedAt?: string;
};

function formatDate(value?: string) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString('es-CL');
}

function getSessionVolume(log: any) {
  return (log?.completedExercises ?? []).reduce((total: number, exercise: any) => {
    return (
      total +
      (exercise?.sets ?? []).reduce((sum: number, set: any) => {
        const weight = Number(set?.weight ?? 0);
        const reps = Number(set?.completedReps ?? 0);
        return sum + weight * reps;
      }, 0)
    );
  }, 0);
}

function buildAchievements(input: {
  history: ReturnType<typeof useWorkoutStore.getState>['history'];
  programs: ReturnType<typeof useProgramStore.getState>['programs'];
  savedLogs: ReturnType<typeof useMobileNutritionStore.getState>['savedLogs'];
  bodyProgress: ReturnType<typeof useBodyStore.getState>['bodyProgress'];
}): AchievementRecord[] {
  const { history, programs, savedLogs, bodyProgress } = input;

  const firstWorkout = history[0];
  const weekendWorkout = history.find(log => {
    const day = new Date(log.date).getDay();
    return day === 0 || day === 6;
  });
  const earlyWorkout = history.find(log => new Date(log.date).getHours() < 7);
  const lateWorkout = history.find(log => new Date(log.date).getHours() >= 21);
  const marathonWorkout = history.find(log => Number(log.duration ?? 0) > 90 * 60);
  const session10k = history.find(log => getSessionVolume(log) > 10000);
  const session20k = history.find(log => getSessionVolume(log) > 20000);

  const bench100 = history.find(log =>
    (log.completedExercises ?? []).some((exercise: any) => {
      const name = String(exercise.exerciseName ?? '').toLowerCase();
      if (!name.includes('press de banca') && !name.includes('bench')) return false;
      return (exercise.sets ?? []).some((set: any) => {
        const weight = Number(set?.weight ?? 0);
        const reps = Number(set?.completedReps ?? 0);
        return calculateBrzycki1RM(weight, reps) >= 100;
      });
    }),
  );

  let prBreakthroughDate: string | undefined;
  const historicalPrs = new Map<string, number>();
  for (const log of [...history].reverse()) {
    for (const exercise of log.completedExercises ?? []) {
      const key = String(exercise.exerciseName ?? '').toLowerCase();
      const currentBest = Math.max(
        0,
        ...(exercise.sets ?? []).map((set: any) =>
          calculateBrzycki1RM(Number(set?.weight ?? 0), Number(set?.completedReps ?? 0)),
        ),
      );
      const previousBest = historicalPrs.get(key) ?? 0;
      if (currentBest > previousBest && previousBest > 0) {
        prBreakthroughDate = log.date;
        break;
      }
      if (currentBest > previousBest) historicalPrs.set(key, currentBest);
    }
    if (prBreakthroughDate) break;
  }

  const photoLog = bodyProgress.find(entry => Array.isArray(entry.photos) && entry.photos.length > 0);

  return [
    {
      id: 'consistency_1',
      name: 'Primer Paso',
      description: 'Completa tu primer entrenamiento.',
      category: 'Consistencia',
      unlocked: history.length >= 1,
      unlockedAt: firstWorkout?.date,
    },
    {
      id: 'consistency_10',
      name: 'Constancia de Acero',
      description: 'Completa 10 entrenamientos.',
      category: 'Consistencia',
      unlocked: history.length >= 10,
      unlockedAt: history.length >= 10 ? history[history.length - 10]?.date : undefined,
    },
    {
      id: 'consistency_50',
      name: 'Maquina Imparable',
      description: 'Completa 50 entrenamientos.',
      category: 'Consistencia',
      unlocked: history.length >= 50,
      unlockedAt: history.length >= 50 ? history[history.length - 50]?.date : undefined,
    },
    {
      id: 'consistency_100',
      name: 'Devoto del Hierro',
      description: 'Completa 100 entrenamientos.',
      category: 'Consistencia',
      unlocked: history.length >= 100,
      unlockedAt: history.length >= 100 ? history[history.length - 100]?.date : undefined,
    },
    {
      id: 'consistency_weekend',
      name: 'Guerrero de Fin de Semana',
      description: 'Completa un entrenamiento en sabado o domingo.',
      category: 'Consistencia',
      unlocked: Boolean(weekendWorkout),
      unlockedAt: weekendWorkout?.date,
    },
    {
      id: 'volume_10k',
      name: 'Titan del Volumen',
      description: 'Supera 10.000 kg/lbs de volumen en una sesion.',
      category: 'Hitos',
      unlocked: Boolean(session10k),
      unlockedAt: session10k?.date,
    },
    {
      id: 'volume_20k',
      name: 'Coloso de Hierro',
      description: 'Supera 20.000 kg/lbs de volumen en una sesion.',
      category: 'Hitos',
      unlocked: Boolean(session20k),
      unlockedAt: session20k?.date,
    },
    {
      id: 'pr_1',
      name: 'Rompiendo Barreras',
      description: 'Supera un record personal estimado en cualquier ejercicio.',
      category: 'Hitos',
      unlocked: Boolean(prBreakthroughDate),
      unlockedAt: prBreakthroughDate,
    },
    {
      id: 'pr_bench_100kg',
      name: 'Club de los 100kg',
      description: 'Alcanza un 1RM estimado de 100kg o mas en press de banca.',
      category: 'Hitos',
      unlocked: Boolean(bench100),
      unlockedAt: bench100?.date,
    },
    {
      id: 'program_create',
      name: 'Arquitecto Fitness',
      description: 'Crea tu primer programa de entrenamiento.',
      category: 'Exploracion',
      unlocked: programs.length > 0,
      unlockedAt: programs.length > 0 ? new Date().toISOString() : undefined,
    },
    {
      id: 'log_nutrition',
      name: 'Diario Alimenticio',
      description: 'Registra tu primera comida.',
      category: 'Exploracion',
      unlocked: savedLogs.length > 0,
      unlockedAt: savedLogs[0]?.createdAt,
    },
    {
      id: 'log_photo',
      name: 'Fotografo del Progreso',
      description: 'Anade tu primera foto de progreso corporal.',
      category: 'Exploracion',
      unlocked: Boolean(photoLog),
      unlockedAt: photoLog?.date,
    },
    {
      id: 'dedication_early',
      name: 'Pajaro Madrugador',
      description: 'Completa un entrenamiento antes de las 7 AM.',
      category: 'Dedicacion',
      unlocked: Boolean(earlyWorkout),
      unlockedAt: earlyWorkout?.date,
    },
    {
      id: 'dedication_late',
      name: 'Buho Nocturno',
      description: 'Completa un entrenamiento despues de las 9 PM.',
      category: 'Dedicacion',
      unlocked: Boolean(lateWorkout),
      unlockedAt: lateWorkout?.date,
    },
    {
      id: 'dedication_duration',
      name: 'Sesion Maratonica',
      description: 'Entrena durante mas de 90 minutos en una sola sesion.',
      category: 'Dedicacion',
      unlocked: Boolean(marathonWorkout),
      unlockedAt: marathonWorkout?.date,
    },
  ];
}

export function AchievementsScreen() {
  const colors = useColors();
  const workoutStatus = useWorkoutStore(state => state.status);
  const hydrateWorkout = useWorkoutStore(state => state.hydrateFromMigration);
  const history = useWorkoutStore(state => state.history);
  const programStatus = useProgramStore(state => state.status);
  const hydratePrograms = useProgramStore(state => state.hydrateFromMigration);
  const programs = useProgramStore(state => state.programs);
  const bodyStatus = useBodyStore(state => state.status);
  const hydrateBody = useBodyStore(state => state.hydrateFromMigration);
  const bodyProgress = useBodyStore(state => state.bodyProgress);
  const hasHydratedNutrition = useMobileNutritionStore(state => state.hasHydrated);
  const hydrateNutrition = useMobileNutritionStore(state => state.hydrateFromStorage);
  const savedLogs = useMobileNutritionStore(state => state.savedLogs);

  useEffect(() => {
    if (workoutStatus === 'idle') void hydrateWorkout();
    if (programStatus === 'idle') void hydratePrograms();
    if (bodyStatus === 'idle') void hydrateBody();
    if (!hasHydratedNutrition) void hydrateNutrition();
  }, [
    bodyStatus,
    hasHydratedNutrition,
    hydrateBody,
    hydrateNutrition,
    hydratePrograms,
    hydrateWorkout,
    programStatus,
    workoutStatus,
  ]);

  const achievements = useMemo(
    () => buildAchievements({ history, programs, savedLogs, bodyProgress }),
    [bodyProgress, history, programs, savedLogs],
  );

  const unlockedCount = achievements.filter(item => item.unlocked).length;
  const grouped = useMemo(() => {
    return achievements.reduce<Record<AchievementCategory, AchievementRecord[]>>(
      (acc, item) => {
        acc[item.category].push(item);
        return acc;
      },
      {
        Consistencia: [],
        Hitos: [],
        Exploracion: [],
        Dedicacion: [],
      },
    );
  }, [achievements]);

  const progress = achievements.length > 0 ? (unlockedCount / achievements.length) * 100 : 0;

  return (
    <ScreenShell
      title="Logros"
      subtitle="Muro de trofeos derivado de tus datos migrados y de la actividad registrada en RN."
    >
      <View style={styles.container}>
        <LiquidGlassCard style={styles.heroCard} padding={20}>
          <Text style={[styles.heroValue, { color: colors.onSurface }]}>
            {unlockedCount}/{achievements.length}
          </Text>
          <Text style={[styles.heroLabel, { color: colors.onSurfaceVariant }]}>logros desbloqueados</Text>
          <View style={[styles.progressTrack, { backgroundColor: `${colors.onSurface}12` }]}>
            <View
              style={[
                styles.progressFill,
                { backgroundColor: colors.primary, width: `${progress}%` },
              ]}
            />
          </View>
        </LiquidGlassCard>

        {Object.entries(grouped).map(([category, items]) => (
          <View key={category} style={styles.categoryBlock}>
            <Text style={[styles.categoryTitle, { color: colors.primary }]}>{category}</Text>
            <View style={styles.categoryList}>
              {items.map(item => (
                <LiquidGlassCard
                  key={item.id}
                  style={[
                    styles.achievementCard,
                    !item.unlocked && { opacity: 0.6 },
                  ]}
                  padding={18}
                >
                  <View style={styles.achievementHeader}>
                    <Text style={[styles.achievementName, { color: colors.onSurface }]}>
                      {item.name}
                    </Text>
                    <Text
                      style={[
                        styles.badge,
                        {
                          color: item.unlocked ? colors.primary : colors.onSurfaceVariant,
                        },
                      ]}
                    >
                      {item.unlocked ? 'Desbloqueado' : 'Bloqueado'}
                    </Text>
                  </View>
                  <Text style={[styles.achievementDescription, { color: colors.onSurfaceVariant }]}>
                    {item.description}
                  </Text>
                  {item.unlocked ? (
                    <Text style={[styles.unlockDate, { color: colors.onSurfaceVariant }]}>
                      {formatDate(item.unlockedAt) ? `Fecha estimada: ${formatDate(item.unlockedAt)}` : 'Ya disponible'}
                    </Text>
                  ) : null}
                </LiquidGlassCard>
              ))}
            </View>
          </View>
        ))}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  heroCard: {
    borderRadius: 28,
  },
  heroValue: {
    fontSize: 42,
    fontWeight: '900',
  },
  heroLabel: {
    fontSize: 13,
    marginBottom: 14,
  },
  progressTrack: {
    width: '100%',
    height: 10,
    borderRadius: 999,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 999,
  },
  categoryBlock: {
    gap: 10,
  },
  categoryTitle: {
    fontSize: 18,
    fontWeight: '900',
  },
  categoryList: {
    gap: 10,
  },
  achievementCard: {
    borderRadius: 24,
  },
  achievementHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
    marginBottom: 6,
  },
  achievementName: {
    fontSize: 16,
    fontWeight: '800',
    flex: 1,
  },
  badge: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  achievementDescription: {
    fontSize: 13,
    lineHeight: 19,
  },
  unlockDate: {
    marginTop: 8,
    fontSize: 11,
    fontWeight: '700',
  },
});
