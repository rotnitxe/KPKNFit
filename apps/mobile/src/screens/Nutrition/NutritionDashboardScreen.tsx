import React, { useEffect, useMemo, useState } from 'react';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { ScreenShell } from '../../components/ScreenShell';
import { NutritionStackParamList } from '../../navigation/types';
import { useSettingsStore } from '../../stores/settingsStore';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { useMealTemplateStore } from '../../stores/mealTemplateStore';
import { useMealPlannerStore } from '../../stores/mealPlannerStore';
import { NutritionHeroCard, NutritionMacroProgressCard, NutritionRecentLogsCard, NutritionSevenDayTrendCard, type TrendPoint } from '../../components/nutrition';
import GoalReachedModal from '../../components/nutrition/GoalReachedModal';
import NutritionPlanEditorModal from '../../components/nutrition/NutritionPlanEditorModal';
import NutritionWizard from '../../components/nutrition/NutritionWizard';
import { RegisterFoodDrawer } from '../../components/nutrition/RegisterFoodDrawer';
import { Button } from '../../components/ui';
import { useColors } from '../../theme';
import { calculateDailyCalorieGoal } from '../../utils/calorieFormulas';

type NavigationProp = NativeStackNavigationProp<NutritionStackParamList, 'NutritionDashboard'>;

const todayKey = () => new Date().toISOString().slice(0, 10);

function logDateKey(log: { loggedDate?: string; createdAt: string }) {
  return log.loggedDate || log.createdAt.slice(0, 10);
}

function formatShortDay(dateKey: string) {
  return new Date(`${dateKey}T00:00:00`).toLocaleDateString('es-CL', { weekday: 'short' }).replace('.', '');
}

function buildTrendPoints(logs: Array<{ loggedDate?: string; createdAt: string; totals: { calories: number } }>): TrendPoint[] {
  const anchor = new Date();
  const dates = Array.from({ length: 7 }, (_, index) => {
    const day = new Date(anchor);
    day.setDate(anchor.getDate() - (6 - index));
    return day.toISOString().slice(0, 10);
  });

  const caloriesByDate = new Map<string, number>();
  for (const log of logs) {
    const key = logDateKey(log);
    caloriesByDate.set(key, (caloriesByDate.get(key) ?? 0) + (log.totals.calories ?? 0));
  }

  return dates.map(dateKey => ({
    key: dateKey,
    label: formatShortDay(dateKey),
    calories: Math.round(caloriesByDate.get(dateKey) ?? 0),
    isToday: dateKey === todayKey(),
  }));
}

function buildMealCount(logs: Array<{ createdAt: string }>) {
  return logs.length;
}

export const NutritionDashboardScreen: React.FC = () => {
  const colors = useColors();
  const navigation = useNavigation<NavigationProp>();

  const settingsStatus = useSettingsStore(state => state.status);
  const settingsSummary = useSettingsStore(state => state.summary);
  const hydrateSettings = useSettingsStore(state => state.hydrateFromMigration);
  const getSettings = useSettingsStore(state => state.getSettings);
  const updateSettings = useSettingsStore(state => state.updateSettings);

  const nutritionStatus = useMobileNutritionStore(state => state.status);
  const savedLogs = useMobileNutritionStore(state => state.savedLogs);
  const nutritionPlan = useMobileNutritionStore(state => state.nutritionPlan);
  const hydrateNutrition = useMobileNutritionStore(state => state.hydrateFromStorage);
  const getLogsForDate = useMobileNutritionStore(state => state.getLogsForDate);
  const updateNutritionPlan = useMobileNutritionStore(state => state.updateNutritionPlan);

  const templateStatus = useMealTemplateStore(state => state.status);
  const hydrateTemplates = useMealTemplateStore(state => state.hydrateFromMigration);
  const templateCount = useMealTemplateStore(state => state.templates.length);

  const plannerStatus = useMealPlannerStore(state => state.status);
  const hydratePlanner = useMealPlannerStore(state => state.hydrateFromStorage);
  const plannerSummary = useMealPlannerStore(state => state.summary);

  const [registerVisible, setRegisterVisible] = useState(false);
  const [planEditorVisible, setPlanEditorVisible] = useState(false);
  const [goalReachedVisible, setGoalReachedVisible] = useState(false);
  const [wizardDismissed, setWizardDismissed] = useState(false);
  const [shownGoalKey, setShownGoalKey] = useState<string | null>(null);

  useEffect(() => {
    if (settingsStatus === 'idle') {
      void hydrateSettings();
    }
  }, [hydrateSettings, settingsStatus]);

  useEffect(() => {
    if (nutritionStatus === 'idle') {
      void hydrateNutrition();
    }
  }, [hydrateNutrition, nutritionStatus]);

  useEffect(() => {
    if (templateStatus === 'idle') {
      void hydrateTemplates();
    }
  }, [hydrateTemplates, templateStatus]);

  useEffect(() => {
    if (plannerStatus === 'idle') {
      void hydratePlanner();
    }
  }, [hydratePlanner, plannerStatus]);

  const currentSettings = settingsSummary ?? getSettings();
  const wizardRequired = currentSettings?.hasSeenNutritionWizard !== true || currentSettings?.nutritionWizardVersion !== 2;
  const showWizard = wizardRequired && !wizardDismissed;

  const calorieGoal = useMemo(() => {
    if (!currentSettings) return nutritionPlan.calories;
    return currentSettings.dailyCalorieGoal ?? calculateDailyCalorieGoal(currentSettings, currentSettings.calorieGoalConfig);
  }, [currentSettings, nutritionPlan.calories]);

  const proteinGoal = currentSettings?.dailyProteinGoal ?? nutritionPlan.protein;
  const carbGoal = currentSettings?.dailyCarbGoal ?? nutritionPlan.carbs;
  const fatGoal = currentSettings?.dailyFatGoal ?? nutritionPlan.fats;

  const todayLogs = useMemo(() => getLogsForDate(todayKey()), [getLogsForDate, savedLogs]);
  const todayTotals = useMemo(() => todayLogs.reduce(
    (acc, log) => ({
      calories: acc.calories + (log.totals.calories ?? 0),
      protein: acc.protein + (log.totals.protein ?? 0),
      carbs: acc.carbs + (log.totals.carbs ?? 0),
      fats: acc.fats + (log.totals.fats ?? 0),
    }),
    { calories: 0, protein: 0, carbs: 0, fats: 0 },
  ), [todayLogs]);

  const trendPoints = useMemo(() => buildTrendPoints(savedLogs), [savedLogs]);
  const mealCount = useMemo(() => buildMealCount(todayLogs), [todayLogs]);

  useEffect(() => {
    if (showWizard) {
      setGoalReachedVisible(false);
      return;
    }

    if (calorieGoal > 0 && todayTotals.calories >= calorieGoal) {
      const goalKey = todayKey();
      if (shownGoalKey !== goalKey) {
        setGoalReachedVisible(true);
      }
    }
  }, [calorieGoal, shownGoalKey, showWizard, todayTotals.calories]);

  if (showWizard) {
    return <NutritionWizard onClose={() => setWizardDismissed(true)} />;
  }

  return (
    <ScreenShell
      title="Nutrición"
      subtitle="Tu panel nutricional integral"
      contentContainerStyle={styles.content}
    >
      <NutritionHeroCard
        dateLabel={new Date().toLocaleDateString('es-CL', {
          weekday: 'long',
          day: 'numeric',
          month: 'long',
        })}
        caloriesToday={Math.round(todayTotals.calories)}
        calorieGoal={Math.round(calorieGoal)}
        mealCount={mealCount}
        protein={Math.round(todayTotals.protein)}
        proteinGoal={Math.round(proteinGoal)}
        carbs={Math.round(todayTotals.carbs)}
        carbGoal={Math.round(carbGoal)}
        fats={Math.round(todayTotals.fats)}
        fatGoal={Math.round(fatGoal)}
        onPressPrimary={() => setRegisterVisible(true)}
        onPressSettings={() => setPlanEditorVisible(true)}
        primaryLabel="Registrar comida"
      />

      <View style={styles.quickGrid}>
        <QuickAction
          title="Planificador"
          description="Plantillas, despensa y sugerencias"
          onPress={() => navigation.navigate('MealPlanner')}
          colors={colors}
        />
        <QuickAction
          title="Catálogo"
          description="Busca alimentos por alias y marca"
          onPress={() => navigation.navigate('FoodDatabase')}
          colors={colors}
        />
        <QuickAction
          title="Historial"
          description="Revisa registros y edita comidas"
          onPress={() => navigation.navigate('NutritionLog')}
          colors={colors}
        />
        <QuickAction
          title="Wizard"
          description="Reabrir configuración nutricional"
          onPress={() => {
            setWizardDismissed(false);
            void updateSettings({
              hasSeenNutritionWizard: false,
              hasDismissedNutritionSetup: false,
              nutritionWizardVersion: 1,
            });
          }}
          colors={colors}
        />
      </View>

      <NutritionMacroProgressCard
        protein={Math.round(todayTotals.protein)}
        carbs={Math.round(todayTotals.carbs)}
        fats={Math.round(todayTotals.fats)}
        proteinGoal={Math.round(proteinGoal)}
        carbGoal={Math.round(carbGoal)}
        fatGoal={Math.round(fatGoal)}
      />

      <NutritionSevenDayTrendCard
        points={trendPoints}
        calorieGoal={Math.round(calorieGoal)}
      />

      <NutritionRecentLogsCard
        logs={savedLogs}
        maxItems={5}
        onPressLog={() => navigation.navigate('NutritionLog')}
      />

      {plannerSummary ? (
        <View style={[styles.summaryCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Text style={[styles.summaryLabel, { color: colors.onSurfaceVariant }]}>PLAN DE COMIDAS</Text>
          <Text style={[styles.summaryValue, { color: colors.onSurface }]}>
            {Math.round(plannerSummary.dayCaloriesPlanned)} / {Math.round(plannerSummary.dayCaloriesTarget)} kcal
          </Text>
          <Text style={[styles.summaryBody, { color: colors.onSurfaceVariant }]}>
            {plannerSummary.selectedTemplateCount} plantillas activas · {templateCount} plantillas migradas
          </Text>
          <Button variant="secondary" onPress={() => navigation.navigate('MealPlanner')}>
            Abrir planificador
          </Button>
        </View>
      ) : null}

      <GoalReachedModal
        visible={goalReachedVisible}
        onClose={() => {
          setGoalReachedVisible(false);
          setShownGoalKey(todayKey());
        }}
        calories={todayTotals.calories}
        target={calorieGoal}
        protein={todayTotals.protein}
        carbs={todayTotals.carbs}
        fats={todayTotals.fats}
      />

      <NutritionPlanEditorModal
        visible={planEditorVisible}
        onClose={() => setPlanEditorVisible(false)}
        initialPlan={nutritionPlan}
        onSave={(plan) => {
          void updateNutritionPlan(plan);
        }}
      />

      <RegisterFoodDrawer
        visible={registerVisible}
        onClose={() => setRegisterVisible(false)}
        selectedDate={todayKey()}
        mealType="lunch"
      />
    </ScreenShell>
  );
};

interface QuickActionProps {
  title: string;
  description: string;
  onPress: () => void;
  colors: ReturnType<typeof useColors>;
}

const QuickAction: React.FC<QuickActionProps> = ({ title, description, onPress, colors }) => {
  return (
    <Pressable
      onPress={onPress}
      style={[
        styles.quickAction,
        { backgroundColor: colors.surface, borderColor: colors.outlineVariant },
      ]}
    >
      <Text style={[styles.quickActionTitle, { color: colors.onSurface }]}>{title}</Text>
      <Text style={[styles.quickActionBody, { color: colors.onSurfaceVariant }]}>{description}</Text>
    </Pressable>
  );
};

const styles = StyleSheet.create({
  content: {
    gap: 14,
    paddingBottom: 40,
  },
  quickGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  quickAction: {
    width: '48.5%',
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 14,
    gap: 6,
  },
  quickActionTitle: {
    fontSize: 14,
    fontWeight: '900',
  },
  quickActionBody: {
    fontSize: 11,
    lineHeight: 16,
  },
  summaryCard: {
    borderRadius: 22,
    borderWidth: 1,
    padding: 16,
    gap: 6,
  },
  summaryLabel: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.4,
    textTransform: 'uppercase',
  },
  summaryValue: {
    fontSize: 20,
    fontWeight: '900',
  },
  summaryBody: {
    fontSize: 12,
    lineHeight: 16,
    marginBottom: 6,
  },
});

export default NutritionDashboardScreen;
