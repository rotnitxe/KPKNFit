import React, { useMemo } from 'react';
import { ScrollView, StyleSheet, Text, View, Pressable, Dimensions } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '@/components/ScreenShell';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { 
  PlusIcon, 
  ChevronRightIcon, 
  UtensilsIcon, 
  ClockIcon,
  ChevronLeftIcon
} from '@/components/icons';
import { useColors } from '@/theme';
import { useMobileNutritionStore } from '@/stores/nutritionStore';
import { useSettingsStore } from '@/stores/settingsStore';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';
import type { NutritionStackParamList } from '@/navigation/types';
import { Canvas, Path } from '@shopify/react-native-skia';

const { width } = Dimensions.get('window');

type Nav = NativeStackNavigationProp<NutritionStackParamList, 'NutritionDashboard'>;

const RING_RADIUS = 28;
const STROKE_WIDTH = 6;
const CENTER = 40;

const createCircularPath = (cx: number, cy: number, radius: number, progress: number) => {
  const startAngle = -Math.PI / 2;
  const endAngle = startAngle + (Math.PI * 2 * Math.min(1, progress));
  const x = cx + radius * Math.cos(endAngle);
  const y = cy + radius * Math.sin(endAngle);
  const largeArcFlag = progress > 0.5 ? 1 : 0;
  if (progress >= 1) {
    return `M ${cx} ${cy - radius} A ${radius} ${radius} 0 1 1 ${cx} ${cy + radius} A ${radius} ${radius} 0 1 1 ${cx} ${cy - radius} Z`;
  }
  if (progress <= 0) return '';
  const startX = cx + radius * Math.cos(startAngle);
  const startY = cy + radius * Math.sin(startAngle);
  return `M ${startX} ${startY} A ${radius} ${radius} 0 ${largeArcFlag} 1 ${x} ${y}`;
};

function MacroRing({ label, value, goal, color, colors }: { label: string; value: number; goal: number; color: string; colors: any }) {
  const pct = goal > 0 ? value / goal : 0;
  
  return (
    <View style={styles.macroRingItem}>
      <View style={styles.ringGraphic}>
        <Canvas style={{ width: 80, height: 80 }}>
          <Path
            path={createCircularPath(CENTER, CENTER, RING_RADIUS, 1)}
            color={colors.surfaceContainerHighest}
            style="stroke"
            strokeWidth={STROKE_WIDTH}
            strokeCap="round"
          />
          <Path
            path={createCircularPath(CENTER, CENTER, RING_RADIUS, pct)}
            color={color}
            style="stroke"
            strokeWidth={STROKE_WIDTH}
            strokeCap="round"
          />
        </Canvas>
        <View style={styles.ringLabelOverlay}>
          <Text style={[styles.ringValueText, { color }]}>{Math.round(value)}</Text>
        </View>
      </View>
      <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <Text style={[styles.macroGoal, { color: colors.onSurfaceVariant, opacity: 0.5 }]}>{Math.round(goal)}</Text>
    </View>
  );
}

export function NutritionDashboardScreen() {
  const colors = useColors();
  const navigation = useNavigation<Nav>();
  const { savedLogs } = useMobileNutritionStore();
  const { summary: settingsSummary } = useSettingsStore();

  const rawSettings = useMemo(() => readStoredSettingsRaw(), [settingsSummary]);

  const selectedDate = new Date().toISOString().slice(0, 10);
  
  const dailyTotals = useMemo(() => {
    const todayLogs = savedLogs.filter(log => (log.loggedDate || log.createdAt.split('T')[0]) === selectedDate);
    return todayLogs.reduce((acc, log) => ({
      calories: acc.calories + (log.totals?.calories || 0),
      protein: acc.protein + (log.totals?.protein || 0),
      carbs: acc.carbs + (log.totals?.carbs || 0),
      fats: acc.fats + (log.totals?.fats || 0),
    }), { calories: 0, protein: 0, carbs: 0, fats: 0 });
  }, [savedLogs, selectedDate]);

  const calorieGoal = (rawSettings?.calorieGoal as number) || 2000;
  const proteinGoal = (rawSettings?.proteinGoal as number) || 150;
  const carbsGoal = (rawSettings?.carbsGoal as number) || 200;
  const fatsGoal = (rawSettings?.fatsGoal as number) || 60;

  const calPct = Math.min(100, (dailyTotals.calories / calorieGoal) * 100);

  const mealHistory = useMemo(() => {
    return savedLogs
      .filter(log => (log.loggedDate || log.createdAt.split('T')[0]) === selectedDate)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [savedLogs, selectedDate]);

  const header = (
    <View style={styles.header}>
      <Pressable onPress={() => navigation.goBack()} style={styles.backBtn}>
        <ChevronLeftIcon size={24} color={colors.onSurface} />
      </Pressable>
      <View style={{ flex: 1 }}>
        <Text style={[styles.headerKicker, { color: colors.onSurfaceVariant }]}>Nutrición</Text>
        <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Dashboard</Text>
      </View>
      <Pressable 
        onPress={() => navigation.navigate('NutritionLog')}
        style={[styles.addBtn, { backgroundColor: colors.primary }]}
      >
        <PlusIcon size={24} color={colors.onPrimary} />
      </Pressable>
    </View>
  );

  return (
    <ScreenShell 
      title="Nutrición" 
      headerContent={header}
      contentContainerStyle={{ paddingBottom: 100 }}
    >
      <ScrollView showsVerticalScrollIndicator={false} style={styles.scroll}>
        {/* Hero Card - Calorie Summary */}
        <LiquidGlassCard style={styles.heroCard}>
          <Text style={[styles.heroKicker, { color: colors.onSurfaceVariant }]}>CALORÍAS CONSUMIDAS</Text>
          <View style={styles.calorieRow}>
            <Text style={[styles.bigCalorie, { color: colors.onSurface }]}>{Math.round(dailyTotals.calories)}</Text>
            <Text style={[styles.kcalUnit, { color: colors.onSurfaceVariant }]}>kcal</Text>
          </View>
          
          <View style={styles.progressBarContainer}>
            <View style={styles.progressHeader}>
              <Text style={[styles.goalText, { color: colors.onSurfaceVariant }]}>OBJETIVO {calorieGoal} kcal</Text>
              <Text style={[styles.pctText, { color: colors.onSurface }]}>{Math.round(calPct)}%</Text>
            </View>
            <View style={[styles.progressTrack, { backgroundColor: colors.surfaceContainer }]}>
              <View 
                style={[
                  styles.progressFill, 
                  { width: `${calPct}%`, backgroundColor: colors.primary }
                ]} 
              />
            </View>
          </View>
        </LiquidGlassCard>

        {/* Macro Rings Section */}
        <View style={styles.macroSection}>
          <View style={styles.sectionHeader}>
            <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Macros</Text>
          </View>
          <LiquidGlassCard style={styles.macroGrid}>
            <MacroRing label="P" value={dailyTotals.protein} goal={proteinGoal} color="#10b981" colors={colors} />
            <MacroRing label="C" value={dailyTotals.carbs} goal={carbsGoal} color="#f59e0b" colors={colors} />
            <MacroRing label="G" value={dailyTotals.fats} goal={fatsGoal} color="#f43f5e" colors={colors} />
          </LiquidGlassCard>
        </View>

        {/* Meal History Section */}
        <View style={styles.historySection}>
          <View style={styles.sectionHeader}>
            <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Historial de Hoy</Text>
            <Pressable onPress={() => navigation.navigate('NutritionLog')}>
              <Text style={[styles.seeAll, { color: colors.primary }]}>Añadir</Text>
            </Pressable>
          </View>
          
          {mealHistory.length === 0 ? (
            <View style={styles.emptyState}>
              <UtensilsIcon size={40} color={colors.onSurfaceVariant} style={{ opacity: 0.3 }} />
              <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>No has registrado comidas hoy</Text>
            </View>
          ) : (
            <View style={styles.mealList}>
              {mealHistory.map((log) => (
                <Pressable key={log.id} style={styles.mealItem}>
                   <LiquidGlassCard style={styles.mealCard}>
                    <View style={styles.mealIconWrap}>
                      <UtensilsIcon size={18} color={colors.primary} />
                    </View>
                    <View style={styles.mealInfo}>
                      <Text style={[styles.mealTime, { color: colors.onSurfaceVariant }]}>
                        {new Date(log.createdAt).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' })}
                      </Text>
                      <Text style={[styles.mealName, { color: colors.onSurface }]} numberOfLines={1}>{log.description}</Text>
                      <Text style={[styles.mealMacros, { color: colors.onSurfaceVariant }]}>
                        {Math.round(log.totals.calories)} kcal · {Math.round(log.totals.protein)}p · {Math.round(log.totals.carbs)}c · {Math.round(log.totals.fats)}g
                      </Text>
                    </View>
                    <ChevronRightIcon size={20} color={colors.onSurfaceVariant} />
                  </LiquidGlassCard>
                </Pressable>
              ))}
            </View>
          )}
        </View>
      </ScrollView>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 24,
    paddingTop: 8,
    gap: 16,
  },
  backBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerKicker: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  addBtn: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
  },
  scroll: {
    flex: 1,
    paddingHorizontal: 24,
    paddingTop: 16,
  },
  heroCard: {
    padding: 24,
    borderRadius: 32,
    marginBottom: 24,
  },
  heroKicker: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 2,
    marginBottom: 8,
  },
  calorieRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    marginBottom: 20,
  },
  bigCalorie: {
    fontSize: 48,
    fontWeight: '900',
    letterSpacing: -1.5,
  },
  kcalUnit: {
    fontSize: 18,
    fontWeight: '700',
    marginLeft: 8,
    opacity: 0.6,
  },
  progressBarContainer: {
    width: '100%',
  },
  progressHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  goalText: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.5,
  },
  pctText: {
    fontSize: 12,
    fontWeight: '900',
  },
  progressTrack: {
    height: 10,
    borderRadius: 5,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 5,
  },
  macroSection: {
    marginBottom: 24,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  macroGrid: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: 20,
    borderRadius: 32,
  },
  macroRingItem: {
    alignItems: 'center',
  },
  ringGraphic: {
    width: 80,
    height: 80,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ringLabelOverlay: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  ringValueText: {
    fontSize: 14,
    fontWeight: '900',
  },
  macroLabel: {
    fontSize: 10,
    fontWeight: '900',
    marginTop: 8,
  },
  macroGoal: {
    fontSize: 9,
    fontWeight: '700',
    marginTop: 2,
  },
  historySection: {
    marginBottom: 24,
  },
  seeAll: {
    fontSize: 14,
    fontWeight: '900',
  },
  emptyState: {
    padding: 40,
    alignItems: 'center',
    gap: 12,
  },
  emptyText: {
    fontSize: 14,
    fontWeight: '600',
  },
  mealList: {
    gap: 12,
  },
  mealItem: {
    width: '100%',
  },
  mealCard: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 24,
    gap: 16,
  },
  mealIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 16,
    backgroundColor: 'rgba(255,255,255,0.05)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  mealInfo: {
    flex: 1,
  },
  mealTime: {
    fontSize: 10,
    fontWeight: '800',
    marginBottom: 2,
  },
  mealName: {
    fontSize: 15,
    fontWeight: '800',
    marginBottom: 2,
  },
  mealMacros: {
    fontSize: 11,
    fontWeight: '600',
  },
});
