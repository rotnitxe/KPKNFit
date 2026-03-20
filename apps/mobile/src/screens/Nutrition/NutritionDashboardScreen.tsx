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
import { CalendarIcon, ChevronLeftIcon, ChevronRightIcon } from '../../components/icons';
import { useColors } from '../../theme';
import { calculateDailyCalorieGoal, getBMRAndTDEE } from '../../utils/calorieFormulas';
import type { NutritionMealType, SavedNutritionEntry } from '../../types/nutrition';

type NavigationProp = NativeStackNavigationProp<NutritionStackParamList, 'NutritionDashboard'>;

const todayKey = () => new Date().toISOString().slice(0, 10);
const MEAL_ORDER: NutritionMealType[] = ['breakfast', 'lunch', 'dinner', 'snack'];
const MEAL_LABELS: Record<NutritionMealType, string> = {
  breakfast: 'Desayuno',
  lunch: 'Almuerzo',
  dinner: 'Cena',
  snack: 'Snack',
};

interface MealSummary {
  mealType: NutritionMealType;
  logs: number;
  totals: {
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
  };
  examples: string[];
}

function shiftDateKey(dateKey: string, days: number) {
  const date = new Date(`${dateKey}T12:00:00`);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

function buildMealSummaries(logs: SavedNutritionEntry[]): MealSummary[] {
  const seed: Record<NutritionMealType, MealSummary> = {
    breakfast: {
      mealType: 'breakfast',
      logs: 0,
      totals: { calories: 0, protein: 0, carbs: 0, fats: 0 },
      examples: [],
    },
    lunch: {
      mealType: 'lunch',
      logs: 0,
      totals: { calories: 0, protein: 0, carbs: 0, fats: 0 },
      examples: [],
    },
    dinner: {
      mealType: 'dinner',
      logs: 0,
      totals: { calories: 0, protein: 0, carbs: 0, fats: 0 },
      examples: [],
    },
    snack: {
      mealType: 'snack',
      logs: 0,
      totals: { calories: 0, protein: 0, carbs: 0, fats: 0 },
      examples: [],
    },
  };

  for (const log of logs) {
    const mealType: NutritionMealType = log.mealType ?? 'lunch';
    const summary = seed[mealType];
    summary.logs += 1;
    summary.totals.calories += log.totals.calories ?? 0;
    summary.totals.protein += log.totals.protein ?? 0;
    summary.totals.carbs += log.totals.carbs ?? 0;
    summary.totals.fats += log.totals.fats ?? 0;
    const description = log.description?.trim();
    if (description && summary.examples.length < 2) {
      summary.examples.push(description);
    }
  }

  return MEAL_ORDER.map(mealType => seed[mealType]);
}

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
  const [registerMealType, setRegisterMealType] = useState<NutritionMealType>('lunch');
  const [planEditorVisible, setPlanEditorVisible] = useState(false);
  const [goalReachedVisible, setGoalReachedVisible] = useState(false);
  const [wizardDismissed, setWizardDismissed] = useState(false);
  const [shownGoalKey, setShownGoalKey] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState(todayKey());

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

  const selectedDateLogs = useMemo(() => getLogsForDate(selectedDate), [getLogsForDate, savedLogs, selectedDate]);
  const todayTotals = useMemo(() => selectedDateLogs.reduce(
    (acc, log) => ({
      calories: acc.calories + (log.totals.calories ?? 0),
      protein: acc.protein + (log.totals.protein ?? 0),
      carbs: acc.carbs + (log.totals.carbs ?? 0),
      fats: acc.fats + (log.totals.fats ?? 0),
    }),
    { calories: 0, protein: 0, carbs: 0, fats: 0 },
  ), [selectedDateLogs]);

  const trendPoints = useMemo(() => buildTrendPoints(savedLogs), [savedLogs]);
  const mealCount = useMemo(() => buildMealCount(selectedDateLogs), [selectedDateLogs]);
  const mealSummaries = useMemo(() => buildMealSummaries(selectedDateLogs), [selectedDateLogs]);
  const selectedDateLabel = useMemo(
    () =>
      new Date(`${selectedDate}T12:00:00`).toLocaleDateString('es-CL', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      }),
    [selectedDate],
  );
  const selectedDateIsToday = selectedDate === todayKey();

  useEffect(() => {
    if (showWizard) {
      setGoalReachedVisible(false);
      return;
    }

    if (selectedDateIsToday && calorieGoal > 0 && todayTotals.calories >= calorieGoal) {
      const goalKey = todayKey();
      if (shownGoalKey !== goalKey) {
        setGoalReachedVisible(true);
      }
    }
  }, [calorieGoal, selectedDateIsToday, shownGoalKey, showWizard, todayTotals.calories]);

  const openRegisterDrawer = (mealType: NutritionMealType) => {
    setRegisterMealType(mealType);
    setRegisterVisible(true);
  };

  if (showWizard) {
    return <NutritionWizard onClose={() => setWizardDismissed(true)} />;
  }

  return (
    <ScreenShell
      title="Nutrición"
      subtitle="Registra, planifica y revisa tu alimentación"
      contentContainerStyle={styles.content}
    >
      <View style={[styles.dateCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
        <View style={styles.dateCardHeader}>
          <View style={styles.dateCardTitleRow}>
            <CalendarIcon size={16} color={colors.onSurfaceVariant} />
            <Text style={[styles.dateCardTitle, { color: colors.onSurfaceVariant }]}>Fecha de registro</Text>
          </View>
          {!selectedDateIsToday ? (
            <Pressable
              onPress={() => setSelectedDate(todayKey())}
              style={[styles.todayButton, { backgroundColor: `${colors.primary}1A` }]}
            >
              <Text style={[styles.todayButtonText, { color: colors.primary }]}>Ir a hoy</Text>
            </Pressable>
          ) : null}
        </View>

        <View style={styles.dateNavigator}>
          <Pressable
            onPress={() => setSelectedDate(previous => shiftDateKey(previous, -1))}
            style={[styles.dateNavButton, { backgroundColor: colors.surfaceContainer }]}
          >
            <ChevronLeftIcon size={18} color={colors.onSurface} />
          </Pressable>
          <Text style={[styles.selectedDateText, { color: colors.onSurface }]}>{selectedDateLabel}</Text>
          <Pressable
            onPress={() => setSelectedDate(previous => shiftDateKey(previous, 1))}
            disabled={selectedDateIsToday}
            style={[
              styles.dateNavButton,
              { backgroundColor: colors.surfaceContainer, opacity: selectedDateIsToday ? 0.45 : 1 },
            ]}
          >
            <ChevronRightIcon size={18} color={colors.onSurface} />
          </Pressable>
        </View>
      </View>

      <NutritionHeroCard
        dateKey={selectedDate}
        caloriesToday={Math.round(todayTotals.calories)}
        calorieGoal={Math.round(calorieGoal)}
        mealCount={mealCount}
        protein={Math.round(todayTotals.protein)}
        proteinGoal={Math.round(proteinGoal)}
        carbs={Math.round(todayTotals.carbs)}
        carbGoal={Math.round(carbGoal)}
        fats={Math.round(todayTotals.fats)}
        fatGoal={Math.round(fatGoal)}
        onPressPrimary={() => openRegisterDrawer('lunch')}
        onPressSettings={() => setPlanEditorVisible(true)}
        primaryLabel="Registrar comida"
      />

      {/* Metabolism Metrics — aligned with PWA NutritionDashboard */}
      {(() => {
        const { bmr, tdee } = getBMRAndTDEE(currentSettings as any, (currentSettings as any).calorieGoalConfig);
        const deficitSurplus = Math.round(todayTotals.calories) - Math.round(calorieGoal);
        if (bmr == null && tdee == null) return null;
        return (
          <View style={[styles.metabolismCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <Text style={[styles.metabolismEyebrow, { color: colors.onSurfaceVariant }]}>Metabolismo</Text>
            <View style={styles.metabolismGrid}>
              <View style={styles.metabolismCell}>
                <Text style={[styles.metabolismLabel, { color: colors.onSurfaceVariant }]}>BMR</Text>
                <Text style={[styles.metabolismValue, { color: colors.tertiary }]}>
                  {bmr != null ? `${Math.round(bmr)} kcal` : '—'}
                </Text>
              </View>
              <View style={styles.metabolismCell}>
                <Text style={[styles.metabolismLabel, { color: colors.onSurfaceVariant }]}>TDEE</Text>
                <Text style={[styles.metabolismValue, { color: colors.primary }]}>
                  {tdee != null ? `${tdee} kcal` : '—'}
                </Text>
              </View>
              <View style={styles.metabolismCell}>
                <Text style={[styles.metabolismLabel, { color: colors.onSurfaceVariant }]}>Balance</Text>
                <Text style={[styles.metabolismValue, { color: deficitSurplus >= 0 ? colors.primary : colors.error }]}>
                  {deficitSurplus >= 0 ? '+' : ''}{deficitSurplus} kcal
                </Text>
              </View>
            </View>
          </View>
        );
      })()}

      <View style={[styles.registerCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
        <Text style={[styles.registerCardTitle, { color: colors.onSurfaceVariant }]}>Registro rápido</Text>
        <View style={styles.registerActions}>
          {MEAL_ORDER.map(mealType => (
            <Pressable
              key={mealType}
              onPress={() => openRegisterDrawer(mealType)}
              style={[styles.registerActionButton, { backgroundColor: colors.surfaceContainer }]}
            >
              <Text style={[styles.registerActionLabel, { color: colors.onSurface }]}>
                {MEAL_LABELS[mealType]}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>

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

      <View style={[styles.mealCard, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
        <View style={[styles.mealCardHeader, { borderBottomColor: colors.outlineVariant }]}>
          <Text style={[styles.mealCardEyebrow, { color: colors.onSurfaceVariant }]}>Registro de alimentos</Text>
          <Text style={[styles.mealCardTitle, { color: colors.onSurface }]}>Comidas del día por bloque</Text>
        </View>

        <View style={styles.mealRows}>
          {mealSummaries.map((mealSummary, index) => {
            const hasLogs = mealSummary.logs > 0;
            const isLast = index === mealSummaries.length - 1;
            return (
              <Pressable
                key={mealSummary.mealType}
                onPress={() => openRegisterDrawer(mealSummary.mealType)}
                style={[
                  styles.mealRow,
                  !isLast && { borderBottomWidth: 1, borderBottomColor: colors.outlineVariant },
                ]}
              >
                <View style={styles.mealRowTop}>
                  <View style={styles.mealRowContent}>
                    <Text style={[styles.mealLabel, { color: colors.onSurfaceVariant }]}>
                      {MEAL_LABELS[mealSummary.mealType]}
                    </Text>
                    <Text style={[styles.mealMeta, { color: colors.onSurface }]}>
                      {hasLogs
                        ? `${Math.round(mealSummary.totals.calories)} kcal · P ${Math.round(mealSummary.totals.protein)}g · C ${Math.round(mealSummary.totals.carbs)}g · G ${Math.round(mealSummary.totals.fats)}g`
                        : 'Sin registros por ahora'}
                    </Text>
                  </View>
                  {hasLogs ? (
                    <View style={[styles.mealBadge, { backgroundColor: colors.surfaceContainer }]}>
                      <Text style={[styles.mealBadgeText, { color: colors.onSurfaceVariant }]}>{mealSummary.logs}</Text>
                    </View>
                  ) : null}
                </View>

                {mealSummary.examples.length > 0 ? (
                  <Text style={[styles.mealExamples, { color: colors.onSurfaceVariant }]}>
                    {mealSummary.examples.join(' · ')}
                  </Text>
                ) : null}
              </Pressable>
            );
          })}
        </View>
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
        visible={selectedDateIsToday && goalReachedVisible}
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
        selectedDate={selectedDate}
        mealType={registerMealType}
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
  dateCard: {
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    gap: 10,
  },
  dateCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  dateCardTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  dateCardTitle: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
  },
  todayButton: {
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  todayButtonText: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  dateNavigator: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  dateNavButton: {
    width: 34,
    height: 34,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  selectedDateText: {
    flex: 1,
    fontSize: 13,
    fontWeight: '700',
    textTransform: 'capitalize',
  },
  mealCard: {
    borderRadius: 22,
    borderWidth: 1,
    overflow: 'hidden',
  },
  metabolismCard: {
    borderRadius: 18,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    gap: 10,
  },
  metabolismEyebrow: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.6,
  },
  metabolismGrid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  metabolismCell: {
    flex: 1,
    alignItems: 'center',
    gap: 3,
  },
  metabolismLabel: {
    fontSize: 11,
    fontWeight: '700',
  },
  metabolismValue: {
    fontSize: 16,
    fontWeight: '900',
    letterSpacing: -0.3,
  },
  registerCard: {
    borderRadius: 18,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    gap: 10,
  },
  registerCardTitle: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  registerActions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  registerActionButton: {
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  registerActionLabel: {
    fontSize: 11,
    fontWeight: '700',
  },
  mealCardHeader: {
    paddingHorizontal: 16,
    paddingTop: 14,
    paddingBottom: 10,
    borderBottomWidth: 1,
    gap: 4,
  },
  mealCardEyebrow: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
  },
  mealCardTitle: {
    fontSize: 14,
    fontWeight: '800',
  },
  mealRows: {
    paddingHorizontal: 16,
  },
  mealRow: {
    paddingVertical: 12,
    gap: 4,
  },
  mealRowTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 10,
  },
  mealRowContent: {
    flex: 1,
    gap: 3,
  },
  mealLabel: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  mealMeta: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
  },
  mealBadge: {
    minWidth: 26,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 8,
  },
  mealBadgeText: {
    fontSize: 10,
    fontWeight: '800',
  },
  mealExamples: {
    fontSize: 11,
    lineHeight: 15,
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
