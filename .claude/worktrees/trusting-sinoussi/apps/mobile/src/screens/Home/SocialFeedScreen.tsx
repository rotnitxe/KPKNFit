import React, { useEffect, useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ScreenShell } from '@/components/ScreenShell';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { useBodyStore } from '@/stores/bodyStore';
import { useMobileNutritionStore } from '@/stores/nutritionStore';
import { useWorkoutStore } from '@/stores/workoutStore';
import { useColors } from '@/theme';

type FeedItem = {
  id: string;
  type: 'workout' | 'nutrition' | 'body';
  title: string;
  detail: string;
  date: string;
};

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('es-CL', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function buildFeedItems(input: {
  workoutHistory: ReturnType<typeof useWorkoutStore.getState>['history'];
  nutritionLogs: ReturnType<typeof useMobileNutritionStore.getState>['savedLogs'];
  bodyProgress: ReturnType<typeof useBodyStore.getState>['bodyProgress'];
}): FeedItem[] {
  const workoutItems: FeedItem[] = input.workoutHistory.map(log => ({
    id: `workout-${log.id}`,
    type: 'workout',
    title: log.sessionName || 'Sesion registrada',
    detail: `${log.completedExercises.length} ejercicios completados`,
    date: log.date,
  }));

  const nutritionItems: FeedItem[] = input.nutritionLogs.map(log => ({
    id: `nutrition-${log.id}`,
    type: 'nutrition',
    title: log.description || 'Registro nutricional',
    detail: `${Math.round(log.totals.calories)} kcal • ${Math.round(log.totals.protein)} g proteina`,
    date: log.createdAt,
  }));

  const bodyItems: FeedItem[] = input.bodyProgress.map(entry => ({
    id: `body-${entry.id}`,
    type: 'body',
    title: 'Check corporal',
    detail: `Peso ${entry.weight ?? '--'} kg${typeof entry.bodyFatPercentage === 'number' ? ` • ${entry.bodyFatPercentage}% grasa` : ''}`,
    date: entry.date,
  }));

  return [...workoutItems, ...nutritionItems, ...bodyItems]
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
    .slice(0, 24);
}

export function SocialFeedScreen() {
  const colors = useColors();
  const workoutStatus = useWorkoutStore(state => state.status);
  const hydrateWorkout = useWorkoutStore(state => state.hydrateFromMigration);
  const workoutHistory = useWorkoutStore(state => state.history);
  const hasHydratedNutrition = useMobileNutritionStore(state => state.hasHydrated);
  const hydrateNutrition = useMobileNutritionStore(state => state.hydrateFromStorage);
  const nutritionLogs = useMobileNutritionStore(state => state.savedLogs);
  const bodyStatus = useBodyStore(state => state.status);
  const hydrateBody = useBodyStore(state => state.hydrateFromMigration);
  const bodyProgress = useBodyStore(state => state.bodyProgress);

  useEffect(() => {
    if (workoutStatus === 'idle') void hydrateWorkout();
    if (!hasHydratedNutrition) void hydrateNutrition();
    if (bodyStatus === 'idle') void hydrateBody();
  }, [bodyStatus, hasHydratedNutrition, hydrateBody, hydrateNutrition, hydrateWorkout, workoutStatus]);

  const items = useMemo(
    () => buildFeedItems({ workoutHistory, nutritionLogs, bodyProgress }),
    [bodyProgress, nutritionLogs, workoutHistory],
  );

  const badgeColor = (type: FeedItem['type']) => {
    if (type === 'workout') return colors.primary;
    if (type === 'nutrition') return colors.tertiary;
    return colors.secondary;
  };

  const badgeLabel = (type: FeedItem['type']) => {
    if (type === 'workout') return 'Entrenamiento';
    if (type === 'nutrition') return 'Nutricion';
    return 'Cuerpo';
  };

  return (
    <ScreenShell
      title="Feed"
      subtitle="Actividad reciente consolidada desde entrenamiento, nutricion y progreso corporal."
    >
      <View style={styles.container}>
        <LiquidGlassCard style={styles.heroCard} padding={20}>
          <Text style={[styles.heroValue, { color: colors.onSurface }]}>{items.length}</Text>
          <Text style={[styles.heroLabel, { color: colors.onSurfaceVariant }]}>eventos recientes visibles en el baseline RN</Text>
        </LiquidGlassCard>

        {items.length === 0 ? (
          <LiquidGlassCard style={styles.emptyCard} padding={20}>
            <Text style={[styles.emptyTitle, { color: colors.onSurface }]}>Sin actividad todavia</Text>
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>El feed empezara a poblarse en cuanto registres entrenos, comidas o progreso corporal.</Text>
          </LiquidGlassCard>
        ) : (
          <View style={styles.feedList}>
            {items.map(item => (
              <LiquidGlassCard key={item.id} style={styles.feedCard} padding={18}>
                <View style={styles.feedHeader}>
                  <View
                    style={[
                      styles.badge,
                      { backgroundColor: `${badgeColor(item.type)}18`, borderColor: `${badgeColor(item.type)}55` },
                    ]}
                  >
                    <Text style={[styles.badgeText, { color: badgeColor(item.type) }]}>
                      {badgeLabel(item.type)}
                    </Text>
                  </View>
                  <Text style={[styles.feedDate, { color: colors.onSurfaceVariant }]}> 
                    {formatDate(item.date)}
                  </Text>
                </View>
                <Text style={[styles.feedTitle, { color: colors.onSurface }]}>{item.title}</Text>
                <Text style={[styles.feedDetail, { color: colors.onSurfaceVariant }]}>{item.detail}</Text>
              </LiquidGlassCard>
            ))}
          </View>
        )}
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
    fontSize: 46,
    fontWeight: '900',
  },
  heroLabel: {
    fontSize: 13,
    lineHeight: 20,
  },
  emptyCard: {
    borderRadius: 28,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: '800',
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    lineHeight: 20,
  },
  feedList: {
    gap: 12,
  },
  feedCard: {
    borderRadius: 24,
  },
  feedHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
    gap: 12,
  },
  badge: {
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  feedDate: {
    fontSize: 11,
    fontWeight: '700',
  },
  feedTitle: {
    fontSize: 17,
    fontWeight: '800',
    marginBottom: 4,
  },
  feedDetail: {
    fontSize: 13,
    lineHeight: 19,
  },
});
