import React, { useEffect, useMemo, useState } from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  View,
  ScrollView,
  Dimensions,
  TouchableOpacity,
} from 'react-native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useNavigation } from '@react-navigation/native';
import { ScreenShell } from '@/components/ScreenShell';
import { NutritionRingsContainer } from '@/components/nutrition/NutritionSkiaRings';
import { NutritionStackParamList } from '@/navigation/types';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';
import { useBodyStore } from '@/stores/bodyStore';
import { useMealTemplateStore } from '@/stores/mealTemplateStore';
import { useMobileNutritionStore } from '@/stores/nutritionStore';
import { useColors } from '@/theme';
import { calculateFFMI } from '@/utils/calculations';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import {
    ActivityIcon,
    PlusCircleIcon,
    SearchIcon,
    ClipboardListIcon,
    TrendingUpIcon,
    ChevronRightIcon,
    FlameIcon,
    ZapIcon,
    ClockIcon,
    ArrowRightIcon
} from '@/components/icons';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

type NutritionNavProp = NativeStackNavigationProp<NutritionStackParamList>;
type DashboardTab = 'alimentacion' | 'metricas';

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function formatCompactDate(dateIso: string) {
  const date = new Date(dateIso);
  if (Number.isNaN(date.getTime())) return 'Sin fecha';
  return date.toLocaleString('es-CL', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export const NutritionDashboardScreen: React.FC = () => {
  const navigation = useNavigation<NutritionNavProp>();
  const colors = useColors();
  const [activeTab, setActiveTab] = useState<DashboardTab>('alimentacion');

  const {
    savedLogs,
    hasHydrated,
    hydrateFromStorage,
  } = useMobileNutritionStore();
  const {
    bodyProgress,
    hasHydrated: bodyHydrated,
    hydrateFromMigration: hydrateBody,
  } = useBodyStore();
  const {
    templates,
    status: templateStatus,
    hydrateFromMigration: hydrateTemplates,
  } = useMealTemplateStore();

  useEffect(() => {
    if (!hasHydrated) void hydrateFromStorage();
    if (!bodyHydrated) void hydrateBody();
    if (templateStatus === 'idle') void hydrateTemplates();
  }, [hasHydrated, bodyHydrated, templateStatus]);

  const settings = readStoredSettingsRaw();
  const weightUnit = settings.weightUnit === 'lbs' ? 'lbs' : 'kg';
  const userVitals = (settings.userVitals as Record<string, unknown>) || null;
  const heightCm = isFiniteNumber(userVitals?.height) ? userVitals.height : null;
  const targetWeight = isFiniteNumber(userVitals?.targetWeight) ? userVitals.targetWeight : null;

  const getNum = (v: unknown, f: number) => (isFiniteNumber(v) && v > 0 ? v : f);
  const calorieGoal = getNum(settings.dailyCalorieGoal, 2200);
  const proteinGoal = getNum(settings.dailyProteinGoal, 150);
  const carbGoal = getNum(settings.dailyCarbGoal, 250);
  const fatGoal = getNum(settings.dailyFatGoal, 70);

  const todayKey = new Date().toISOString().slice(0, 10);
  const todayLogs = useMemo(() => savedLogs.filter(log => log.createdAt.slice(0, 10) === todayKey), [savedLogs, todayKey]);

  const dailyTotals = useMemo(() => todayLogs.reduce((acc, log) => ({
    calories: acc.calories + log.totals.calories,
    protein: acc.protein + log.totals.protein,
    carbs: acc.carbs + log.totals.carbs,
    fats: acc.fats + log.totals.fats,
  }), { calories: 0, protein: 0, carbs: 0, fats: 0 }), [todayLogs]);

  const proteinPct = Math.min(100, (dailyTotals.protein / proteinGoal) * 100);
  const carbsPct = Math.min(100, (dailyTotals.carbs / carbGoal) * 100);
  const fatsPct = Math.min(100, (dailyTotals.fats / fatGoal) * 100);

  const latestBodyLog = useMemo(() => [...bodyProgress].sort((a,b) => new Date(b.date).getTime() - new Date(a.date).getTime())[0] ?? null, [bodyProgress]);
  const ffmi = useMemo(() => {
    if (!latestBodyLog || !heightCm || !latestBodyLog.weight || !latestBodyLog.bodyFatPercentage) return null;
    return calculateFFMI(heightCm, latestBodyLog.weight, latestBodyLog.bodyFatPercentage);
  }, [heightCm, latestBodyLog]);

  const handleLogPress = () => {
    ReactNativeHapticFeedback.trigger('impactMedium');
    navigation.navigate('NutritionLog');
  };

  return (
    <ScreenShell
      title="Nutrición"
      subtitle="Fisiología y Dieta"
      showBack={false}
      contentContainerStyle={styles.shellContent}
    >
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>

        {/* HERO SUMMARY CARD */}
        <LiquidGlassCard style={styles.heroCard}>
            <View style={styles.heroInfo}>
                <View style={styles.heroGoalBadge}>
                    <Text style={[styles.heroGoalLabel, { color: colors.primary }]}>
                        {targetWeight ? 'OBJETIVO PESO' : 'OBJETIVO KCAL'}
                    </Text>
                </View>
                <Text style={[styles.heroTarget, { color: colors.onSurface }]}>
                    {targetWeight ? `${targetWeight.toFixed(1)} ${weightUnit}` : `${Math.round(calorieGoal)} kcal`}
                </Text>
                <View style={styles.heroDivider} />
                <View style={styles.heroCurrentRow}>
                    <View>
                        <Text style={[styles.heroCurrentLabel, { color: colors.onSurfaceVariant }]}>HOY</Text>
                        <Text style={[styles.heroCurrentValue, { color: colors.onSurface }]}>{Math.round(dailyTotals.calories)} <Text style={styles.heroCurrentUnit}>kcal</Text></Text>
                    </View>
                    <View style={styles.heroRingsBox}>
                        <NutritionRingsContainer
                            calories={dailyTotals.calories}
                            goal={calorieGoal}
                            proteinPct={proteinPct}
                            carbsPct={carbsPct}
                            fatsPct={fatsPct}
                        />
                    </View>
                </View>
            </View>
        </LiquidGlassCard>

        {/* QUICK ACTIONS DOCK */}
        <View style={styles.quickDock}>
             <TouchableOpacity style={[styles.dockBtn, { backgroundColor: colors.primary }]} onPress={handleLogPress}>
                <PlusCircleIcon size={20} color={colors.onPrimary} />
                <Text style={[styles.dockBtnTxt, { color: colors.onPrimary }]}>REGISTRAR</Text>
             </TouchableOpacity>
             <TouchableOpacity style={[styles.dockBtn, { backgroundColor: colors.surfaceVariant }]} onPress={() => navigation.navigate('MealPlanner')}>
                <ClipboardListIcon size={18} color={colors.onSurface} strokeWidth={2} />
                <Text style={[styles.dockBtnTxt, { color: colors.onSurface }]}>PLANNER</Text>
             </TouchableOpacity>
             <TouchableOpacity style={[styles.dockBtn, { backgroundColor: colors.surfaceVariant }]} onPress={() => navigation.navigate('FoodDatabase')}>
                <SearchIcon size={18} color={colors.onSurface} strokeWidth={2} />
                <Text style={[styles.dockBtnTxt, { color: colors.onSurface }]}>BASE</Text>
             </TouchableOpacity>
        </View>

        {/* TABS SELECTOR */}
        <View style={[styles.pillTabs, { backgroundColor: colors.surfaceVariant, borderColor: colors.outlineVariant }]}>
            <Pressable 
                onPress={() => { ReactNativeHapticFeedback.trigger('selection'); setActiveTab('alimentacion'); }}
                style={[styles.pillTab, activeTab === 'alimentacion' && { backgroundColor: colors.primary }]}
            >
                <Text style={[styles.pillTabTxt, activeTab === 'alimentacion' && { color: colors.onPrimary }]}>DIETA</Text>
            </Pressable>
            <Pressable 
                onPress={() => { ReactNativeHapticFeedback.trigger('selection'); setActiveTab('metricas'); }}
                style={[styles.pillTab, activeTab === 'metricas' && { backgroundColor: colors.primary }]}
            >
                <Text style={[styles.pillTabTxt, activeTab === 'metricas' && { color: colors.onPrimary }]}>CUERPO</Text>
            </Pressable>
        </View>

        {/* CONTENT STACK */}
        {activeTab === 'alimentacion' ? (
            <View style={styles.stack}>
                <View style={styles.sectionHeader}>
                    <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Macros del Día</Text>
                    <Text style={[styles.sectionMeta, { color: colors.onSurfaceVariant }]}>Resumen de ingesta procesada por IA</Text>
                </View>

                <View style={styles.macroGrid}>
                    <LiquidGlassCard style={styles.macroBox}>
                        <ZapIcon size={16} color="#6366F1" />
                        <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>PROTEÍNA</Text>
                        <Text style={[styles.macroVal, { color: colors.onSurface }]}>{Math.round(dailyTotals.protein)}g</Text>
                        <Text style={[styles.macroGoal, { color: colors.onSurfaceVariant }]}>Meta {proteinGoal}g</Text>
                    </LiquidGlassCard>
                    <LiquidGlassCard style={styles.macroBox}>
                        <FlameIcon size={16} color="#F59E0B" />
                        <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>CARBS</Text>
                        <Text style={[styles.macroVal, { color: colors.onSurface }]}>{Math.round(dailyTotals.carbs)}g</Text>
                        <Text style={[styles.macroGoal, { color: colors.onSurfaceVariant }]}>Meta {carbGoal}g</Text>
                    </LiquidGlassCard>
                    <LiquidGlassCard style={styles.macroBox}>
                        <ActivityIcon size={16} color="#EC4899" />
                        <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>GRASAS</Text>
                        <Text style={[styles.macroVal, { color: colors.onSurface }]}>{Math.round(dailyTotals.fats)}g</Text>
                        <Text style={[styles.macroGoal, { color: colors.onSurfaceVariant }]}>Meta {fatGoal}g</Text>
                    </LiquidGlassCard>
                </View>

                <View style={styles.sectionHeader}>
                    <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Comidas Recientes</Text>
                </View>

                {todayLogs.length === 0 ? (
                    <LiquidGlassCard style={styles.emptyCard}>
                         <ClockIcon size={32} color={colors.onSurfaceVariant} opacity={0.3} />
                         <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>No hay registros hoy.</Text>
                    </LiquidGlassCard>
                ) : (
                    todayLogs.map(log => (
                        <LiquidGlassCard key={log.id} style={styles.logCard}>
                            <View style={styles.logHeader}>
                                <Text style={[styles.logTitle, { color: colors.onSurface }]} numberOfLines={1}>{log.description}</Text>
                                <Text style={[styles.logKcal, { color: colors.primary }]}>{Math.round(log.totals.calories)} kcal</Text>
                            </View>
                            <View style={styles.logFooter}>
                                <Text style={[styles.logTime, { color: colors.onSurfaceVariant }]}>{formatCompactDate(log.createdAt)}</Text>
                                <View style={styles.logMacrosPills}>
                                   <Text style={[styles.miniPill, { color: colors.onSurfaceVariant }]}>P {Math.round(log.totals.protein)}g</Text>
                                   <Text style={[styles.miniPill, { color: colors.onSurfaceVariant }]}>C {Math.round(log.totals.carbs)}g</Text>
                                   <Text style={[styles.miniPill, { color: colors.onSurfaceVariant }]}>F {Math.round(log.totals.fats)}g</Text>
                                </View>
                            </View>
                        </LiquidGlassCard>
                    ))
                )}
            </View>
        ) : (
            <View style={styles.stack}>
                <View style={styles.sectionHeader}>
                    <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Estado Metabólico</Text>
                    <Text style={[styles.sectionMeta, { color: colors.onSurfaceVariant }]}>Composición y masa libre de grasa</Text>
                </View>

                <View style={styles.statsGrid}>
                    <LiquidGlassCard style={styles.statSquare}>
                        <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>FFMI</Text>
                        <Text style={[styles.statVal, { color: colors.primary }]}>{ffmi ? ffmi.normalizedFfmi : '--'}</Text>
                        <Text style={[styles.statMeta, { color: colors.onSurfaceVariant }]}>{ffmi ? ffmi.interpretation.toUpperCase() : 'FALTA DATA'}</Text>
                    </LiquidGlassCard>
                    <LiquidGlassCard style={styles.statSquare}>
                        <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>GRASA %</Text>
                        <Text style={[styles.statVal, { color: colors.onSurface }]}>{latestBodyLog?.bodyFatPercentage ? `${latestBodyLog.bodyFatPercentage}%` : '--'}</Text>
                        <View style={styles.statTrend}>
                           <TrendingUpIcon size={12} color="#10B981" />
                           <Text style={[styles.trendTxt, { color: '#10B981' }]}>ESTABLE</Text>
                        </View>
                    </LiquidGlassCard>
                </View>

                <View style={styles.sectionHeader}>
                    <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Historial Corporal</Text>
                </View>

                {bodyProgress.length === 0 ? (
                    <LiquidGlassCard style={styles.emptyCard}>
                         <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>No hay registros de peso aún.</Text>
                    </LiquidGlassCard>
                ) : (
                    bodyProgress.slice(0, 5).map(entry => (
                        <LiquidGlassCard key={entry.id} style={styles.historyRow}>
                             <View style={styles.historyDateBox}>
                                <Text style={[styles.historyDay, { color: colors.onSurface }]}>{new Date(entry.date).getDate()}</Text>
                                <Text style={[styles.historyMo, { color: colors.onSurfaceVariant }]}>{new Date(entry.date).toLocaleString('es', { month: 'short' }).toUpperCase()}</Text>
                             </View>
                             <View style={styles.historyInfo}>
                                <Text style={[styles.historyWeight, { color: colors.onSurface }]}>{entry.weight} {weightUnit}</Text>
                                <Text style={[styles.historyMeta, { color: colors.onSurfaceVariant }]}>{entry.bodyFatPercentage}% Grasa · {entry.muscleMassPercentage}% Músculo</Text>
                             </View>
                             <ChevronRightIcon size={20} color={colors.onSurfaceVariant} opacity={0.3} />
                        </LiquidGlassCard>
                    ))
                )}
            </View>
        )}
      </ScrollView>
    </ScreenShell>
  );
};

const styles = StyleSheet.create({
    shellContent: {
        paddingHorizontal: 0,
    },
    scrollContent: {
        paddingHorizontal: 16,
        paddingBottom: 40,
    },
    heroCard: {
        padding: 20,
        marginBottom: 20,
    },
    heroInfo: {
        gap: 8,
    },
    heroGoalBadge: {
        alignSelf: 'flex-start',
        paddingHorizontal: 8,
        paddingVertical: 4,
        borderRadius: 6,
        backgroundColor: 'rgba(0,0,0,0.05)',
    },
    heroGoalLabel: {
        fontSize: 9,
        fontWeight: '900',
        letterSpacing: 1.2,
    },
    heroTarget: {
        fontSize: 34,
        fontWeight: '900',
        letterSpacing: -1,
    },
    heroDivider: {
        height: 1,
        backgroundColor: 'rgba(0,0,0,0.05)',
        marginVertical: 12,
    },
    heroCurrentRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    heroCurrentLabel: {
        fontSize: 10,
        fontWeight: '900',
        letterSpacing: 1.5,
        marginBottom: 4,
    },
    heroCurrentValue: {
        fontSize: 24,
        fontWeight: '800',
    },
    heroCurrentUnit: {
        fontSize: 14,
        fontWeight: '500',
        opacity: 0.6,
    },
    heroRingsBox: {
        width: 140,
        height: 140,
        alignItems: 'center',
        justifyContent: 'center',
        marginTop: -30,
        marginRight: -10,
    },
    quickDock: {
        flexDirection: 'row',
        gap: 10,
        marginBottom: 24,
    },
    dockBtn: {
        flex: 1,
        height: 54,
        borderRadius: 18,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
        elevation: 2,
    },
    dockBtnTxt: {
        fontSize: 11,
        fontWeight: '900',
        letterSpacing: 0.5,
    },
    pillTabs: {
        flexDirection: 'row',
        borderRadius: 28,
        padding: 5,
        borderWidth: 1,
        marginBottom: 24,
    },
    pillTab: {
        flex: 1,
        borderRadius: 24,
        paddingVertical: 12,
        alignItems: 'center',
    },
    pillTabTxt: {
        fontSize: 11,
        fontWeight: '900',
        color: 'rgba(0,0,0,0.4)',
    },
    stack: {
        gap: 16,
    },
    sectionHeader: {
        marginBottom: 8,
    },
    sectionTitle: {
        fontSize: 22,
        fontWeight: '900',
        letterSpacing: -0.5,
    },
    sectionMeta: {
        fontSize: 13,
        fontWeight: '500',
        opacity: 0.7,
        marginTop: 2,
    },
    macroGrid: {
        flexDirection: 'row',
        gap: 10,
    },
    macroBox: {
        flex: 1,
        padding: 14,
        gap: 4,
    },
    macroLabel: {
        fontSize: 9,
        fontWeight: '900',
        letterSpacing: 0.5,
    },
    macroVal: {
        fontSize: 18,
        fontWeight: '900',
    },
    macroGoal: {
        fontSize: 9,
        fontWeight: '700',
        opacity: 0.5,
    },
    logCard: {
        padding: 16,
        gap: 8,
    },
    logHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    logTitle: {
        fontSize: 16,
        fontWeight: '800',
        flex: 1,
        marginRight: 10,
    },
    logKcal: {
        fontSize: 15,
        fontWeight: '900',
    },
    logFooter: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    logTime: {
        fontSize: 11,
        fontWeight: '600',
    },
    logMacrosPills: {
        flexDirection: 'row',
        gap: 8,
    },
    miniPill: {
        fontSize: 10,
        fontWeight: '800',
        backgroundColor: 'rgba(0,0,0,0.05)',
        paddingHorizontal: 6,
        paddingVertical: 2,
        borderRadius: 4,
    },
    emptyCard: {
        padding: 40,
        alignItems: 'center',
        gap: 12,
    },
    emptyText: {
        fontSize: 14,
        fontWeight: '600',
    },
    statsGrid: {
        flexDirection: 'row',
        gap: 12,
    },
    statSquare: {
        flex: 1,
        padding: 16,
        gap: 6,
    },
    statLabel: {
        fontSize: 9,
        fontWeight: '900',
        letterSpacing: 1.5,
    },
    statVal: {
        fontSize: 24,
        fontWeight: '900',
    },
    statMeta: {
        fontSize: 9,
        fontWeight: '800',
    },
    statTrend: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 4,
    },
    trendTxt: {
        fontSize: 9,
        fontWeight: '900',
    },
    historyRow: {
        flexDirection: 'row',
        alignItems: 'center',
        padding: 16,
        gap: 16,
    },
    historyDateBox: {
        width: 48,
        height: 48,
        borderRadius: 12,
        backgroundColor: 'rgba(0,0,0,0.05)',
        alignItems: 'center',
        justifyContent: 'center',
    },
    historyDay: {
        fontSize: 18,
        fontWeight: '900',
    },
    historyMo: {
        fontSize: 8,
        fontWeight: '900',
    },
    historyInfo: {
        flex: 1,
        gap: 2,
    },
    historyWeight: {
        fontSize: 17,
        fontWeight: '900',
    },
    historyMeta: {
        fontSize: 11,
        fontWeight: '600',
    },
});
