import React, { useEffect, useMemo, useState } from 'react';
import {
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { ScreenShell } from '@/components/ScreenShell';
import { CaupolicanIcon } from '@/components/CaupolicanIcon';
import { SessionTodayCard, TodaySessionItem } from '@/components/home/SessionTodayCard';
import { BatteryRingCard } from '@/components/activity/BatteryRingCard';
import { AugeStatusCard } from '@/components/auge';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import {
  ArrowDownIcon,
  ArrowUpIcon,
  BellIcon,
  IntertwinedRingsIcon,
  MoonIcon,
  SettingsIcon,
  SingleRingIcon,
  SunIcon,
  TrophyIcon,
  UserBadgeIcon,
  WikiLabIcon,
} from '@/components/icons';
import { useBootstrapStore } from '@/stores/bootstrapStore';
import { useAugeRuntimeStore } from '@/stores/augeRuntimeStore';
import { useWorkoutStore } from '@/stores/workoutStore';
import { useProgramStore } from '@/stores/programStore';
import { useBodyStore } from '@/stores/bodyStore';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useMobileNutritionStore } from '@/stores/nutritionStore';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';
import { useColors, useTheme } from '@/theme';
import type { RootTabParamList } from '@/navigation/AppNavigator';
import type { Program, Session } from '@/types/workout';

type RingsMode = 'rings' | 'individual';

function computeFfmi(weightKg?: number, bodyFatPct?: number, heightCm?: number) {
  if (!weightKg || !bodyFatPct || !heightCm) return null;
  const leanMassKg = weightKg * (1 - bodyFatPct / 100);
  const heightMeters = heightCm / 100;
  if (!heightMeters) return null;
  return leanMassKg / (heightMeters * heightMeters);
}

function getGreeting(userName?: string) {
  const hour = new Date().getHours();
  const base = hour < 12 ? '¡Buenos días' : hour < 19 ? '¡Buenas tardes' : '¡Buenas noches';
  return `${base}, ${userName && userName.trim() ? userName.trim() : 'Atleta'}!`;
}

function SectionTitle({ title, action }: { title: string; action?: React.ReactNode }) {
  return (
    <View style={styles.sectionHeaderRow}>
      <Text style={styles.sectionHeaderText}>{title}</Text>
      {action}
    </View>
  );
}

function HomeMetricCard({
  eyebrow,
  value,
  helper,
  accent,
  trend,
}: {
  eyebrow: string;
  value: string;
  helper: string;
  accent: string;
  trend?: 'up' | 'down' | 'neutral';
}) {
  const colors = useColors();
  const trendColor =
    trend === 'up' ? colors.error : trend === 'down' ? colors.batteryHigh : colors.onSurfaceVariant;
  const TrendIcon = trend === 'up' ? ArrowUpIcon : trend === 'down' ? ArrowDownIcon : null;

  return (
    <LiquidGlassCard style={styles.metricCard} padding={18}>
      <Text style={[styles.metricEyebrow, { color: colors.onSurfaceVariant }]}>{eyebrow}</Text>
      <Text style={[styles.metricValue, { color: accent }]}>{value}</Text>
      <View style={styles.metricFooter}>
        <Text style={[styles.metricHelper, { color: colors.onSurfaceVariant }]}>{helper}</Text>
        {TrendIcon ? <TrendIcon size={14} color={trendColor} /> : null}
      </View>
    </LiquidGlassCard>
  );
}

function CornerCard({
  title,
  subtitle,
  icon,
  onPress,
}: {
  title: string;
  subtitle: string;
  icon: React.ReactNode;
  onPress: () => void;
}) {
  const colors = useColors();
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.cornerPressable, pressed && styles.pressed]}>
      <LiquidGlassCard style={styles.cornerCard} padding={20}>
        <View style={[styles.cornerIconWrap, { backgroundColor: `${colors.onSurface}10` }]}>{icon}</View>
        <View style={styles.cornerTextWrap}>
          <Text style={[styles.cornerTitle, { color: colors.onSurface }]}>{title}</Text>
          <Text style={[styles.cornerSubtitle, { color: colors.onSurfaceVariant }]}>{subtitle}</Text>
        </View>
      </LiquidGlassCard>
    </Pressable>
  );
}

function ProgramPreviewCard({
  program,
  active,
  onPress,
}: {
  program: Program;
  active: boolean;
  onPress: () => void;
}) {
  const colors = useColors();

  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.programCardPressable, pressed && styles.pressed]}>
      <View style={styles.programCardWrap}>
        <View style={[styles.programCover, { backgroundColor: active ? colors.primaryContainer : colors.surfaceContainerHigh }]}>
          {program.coverImage ? (
            <Image source={{ uri: program.coverImage }} style={styles.programCoverImage} />
          ) : (
            <CaupolicanIcon size={42} color={active ? colors.primary : `${colors.onSurfaceVariant}55`} />
          )}
          {active ? (
            <View style={[styles.programBadge, { backgroundColor: `${colors.primary}22`, borderColor: `${colors.primary}44` }]}>
              <Text style={[styles.programBadgeText, { color: colors.primary }]}>ACTIVO</Text>
            </View>
          ) : null}
        </View>
        <Text style={[styles.programName, { color: colors.onSurface }]} numberOfLines={2}>
          {program.name}
        </Text>
      </View>
    </Pressable>
  );
}

export function HomeScreen() {
  const colors = useColors();
  const { isDark, toggleDark } = useTheme();
  const navigation = useNavigation<BottomTabNavigationProp<RootTabParamList>>();
  const status = useBootstrapStore(state => state.status);
  const [ringsMode, setRingsMode] = useState<RingsMode>('rings');

  const augeStoreSnapshot = useAugeRuntimeStore(state => state.snapshot);
  const augeIsRefreshing = useAugeRuntimeStore(state => state.isRefreshing);
  const recomputeAugeRuntime = useAugeRuntimeStore(state => state.recompute);

  const {
    overview: workoutOverview,
    status: workoutStatus,
    hydrateFromMigration: hydrateWorkout,
    startActiveSession,
    activeSession,
  } = useWorkoutStore();

  const {
    programs,
    activeProgramState,
    status: programStatus,
    hydrateFromMigration: hydratePrograms,
  } = useProgramStore();

  const {
    bodyProgress,
    status: bodyStatus,
    hydrateFromMigration: hydrateBody,
  } = useBodyStore();

  const {
    overview: wellbeingOverview,
    status: wellbeingStatus,
    hydrateFromMigration: hydrateWellbeing,
  } = useWellbeingStore();

  const {
    savedLogs,
    hasHydrated: hasHydratedNutrition,
    hydrateFromStorage,
  } = useMobileNutritionStore();

  const rawSettings = readStoredSettingsRaw();

  useEffect(() => {
    if (workoutStatus === 'idle') {
      void hydrateWorkout();
    }
  }, [hydrateWorkout, workoutStatus]);

  useEffect(() => {
    if (programStatus === 'idle') {
      void hydratePrograms();
    }
  }, [hydratePrograms, programStatus]);

  useEffect(() => {
    if (bodyStatus === 'idle') {
      void hydrateBody();
    }
  }, [bodyStatus, hydrateBody]);

  useEffect(() => {
    if (wellbeingStatus === 'idle') {
      void hydrateWellbeing();
    }
  }, [hydrateWellbeing, wellbeingStatus]);

  useEffect(() => {
    if (!hasHydratedNutrition) {
      void hydrateFromStorage();
    }
  }, [hasHydratedNutrition, hydrateFromStorage]);

  const greeting = useMemo(
    () => getGreeting(typeof rawSettings.userName === 'string' ? rawSettings.userName : undefined),
    [rawSettings.userName],
  );

  const latestBody = bodyProgress[0];
  const previousBody = bodyProgress[1];
  const ffmi = useMemo(
    () =>
      computeFfmi(
        latestBody?.weight,
        latestBody?.bodyFatPercentage,
        typeof rawSettings.height === 'number' ? rawSettings.height : undefined,
      ),
    [latestBody?.bodyFatPercentage, latestBody?.weight, rawSettings.height],
  );

  const todayKey = new Date().toISOString().slice(0, 10);
  const todayNutritionTotals = useMemo(
    () =>
      savedLogs
        .filter(log => log.createdAt.slice(0, 10) === todayKey)
        .reduce(
          (acc, log) => ({
            calories: acc.calories + log.totals.calories,
            protein: acc.protein + log.totals.protein,
          }),
          { calories: 0, protein: 0 },
        ),
    [savedLogs, todayKey],
  );

  const todaySessions = useMemo(() => {
    if (!workoutOverview?.todaySession) return [] as TodaySessionItem[];
    const program =
      programs.find(item => item.id === workoutOverview.activeProgramId) ||
      ({
        id: workoutOverview.activeProgramId || 'programa-activo',
        name: workoutOverview.activeProgramName || 'Programa activo',
        coverImage: undefined,
      } as Program);

    const macro = program.macrocycles?.[activeProgramState?.currentMacrocycleIndex ?? 0];
    const block = macro?.blocks?.[activeProgramState?.currentBlockIndex ?? 0];
    const mesocycle = block?.mesocycles?.[activeProgramState?.currentMesocycleIndex ?? 0];
    const week = mesocycle?.weeks.find(item => item.id === activeProgramState?.currentWeekId);
    const fullSession =
      week?.sessions.find(item => item.id === workoutOverview.todaySession?.id) ??
      week?.sessions.find(item => item.dayOfWeek === workoutOverview.todaySession?.dayOfWeek) ??
      null;

    if (!fullSession) return [] as TodaySessionItem[];

    return [
      {
        session: fullSession,
        program,
        location: {
          macroIndex: activeProgramState?.currentMacrocycleIndex ?? 0,
          mesoIndex: activeProgramState?.currentMesocycleIndex ?? 0,
          weekId: activeProgramState?.currentWeekId || workoutOverview.currentWeekId || '',
        },
        isCompleted: workoutOverview.hasWorkoutLoggedToday,
        dayOfWeek: workoutOverview.todaySession.dayOfWeek || undefined,
      },
    ];
  }, [activeProgramState?.currentBlockIndex, activeProgramState?.currentMacrocycleIndex, activeProgramState?.currentMesocycleIndex, activeProgramState?.currentWeekId, programs, workoutOverview]);

  const activeProgramsFirst = useMemo(() => {
    const activeId = activeProgramState?.status === 'active' ? activeProgramState.programId : null;
    return [...programs].sort((a, b) => {
      if (a.id === activeId) return -1;
      if (b.id === activeId) return 1;
      return a.name.localeCompare(b.name);
    });
  }, [activeProgramState?.programId, activeProgramState?.status, programs]);

  const handleStartWorkout = (session: Session, program: Program, location: unknown) => {
    void location;
    startActiveSession({
      programId: program.id,
      session,
    });

    navigation.navigate('Workout', {
      screen: 'ActiveSession',
      params: {
        programId: program.id,
        sessionId: session.id,
        sessionName: session.name,
      },
    });
  };

  const battery = workoutOverview?.battery;
  const sleepLabel = wellbeingOverview?.averageSleepHoursLast7Days
    ? `${wellbeingOverview.averageSleepHoursLast7Days}h`
    : '--';
  const weightDelta =
    latestBody?.weight && previousBody?.weight ? latestBody.weight - previousBody.weight : 0;

  const headerContent = (
    <View style={styles.heroHeader}>
      <View style={styles.heroTopRow}>
        <Pressable
          onPress={() => navigation.navigate('Profile', { screen: 'ProfileMain' })}
          style={({ pressed }) => [styles.avatarWrap, pressed && styles.pressed, { borderColor: colors.outlineVariant, backgroundColor: `${colors.onSurface}08` }]}
        >
          <CaupolicanIcon size={24} color={`${colors.onSurface}66`} />
        </Pressable>

        <View style={styles.heroActions}>
          <Pressable
            onPress={toggleDark}
            style={({ pressed }) => [styles.heroActionButton, pressed && styles.pressed, { backgroundColor: `${colors.onSurface}10` }]}
          >
            {isDark ? <SunIcon size={20} color={colors.onSurfaceVariant} /> : <MoonIcon size={20} color={colors.onSurfaceVariant} />}
          </Pressable>
          <Pressable
            onPress={() => navigation.navigate('Settings')}
            style={({ pressed }) => [styles.heroActionButton, pressed && styles.pressed, { backgroundColor: `${colors.onSurface}10` }]}
          >
            <BellIcon size={20} color={colors.onSurfaceVariant} />
          </Pressable>
          <Pressable
            onPress={() => navigation.navigate('Settings')}
            style={({ pressed }) => [styles.heroActionButton, pressed && styles.pressed, { backgroundColor: `${colors.onSurface}10` }]}
          >
            <SettingsIcon size={20} color={colors.onSurfaceVariant} />
          </Pressable>
        </View>
      </View>

      <Text style={[styles.greeting, { color: colors.onSurface }]}>{greeting}</Text>
    </View>
  );

  return (
    <ScreenShell
      title="Inicio"
      showBack={false}
      headerContent={headerContent}
      contentContainerStyle={styles.shellContent}
    >
      <View style={styles.content}>
        <View style={styles.section}>
          <SectionTitle
            title="Tus Rings"
            action={
              <View style={[styles.segmentedControl, { backgroundColor: `${colors.onSurface}10` }]}>
                <Pressable
                  onPress={() => setRingsMode('rings')}
                  style={[
                    styles.segmentedOption,
                    ringsMode === 'rings' && { backgroundColor: `${colors.onSurface}14` },
                  ]}
                >
                  <IntertwinedRingsIcon size={18} color={ringsMode === 'rings' ? colors.primary : colors.onSurfaceVariant} />
                </Pressable>
                <Pressable
                  onPress={() => setRingsMode('individual')}
                  style={[
                    styles.segmentedOption,
                    ringsMode === 'individual' && { backgroundColor: `${colors.onSurface}14` },
                  ]}
                >
                  <SingleRingIcon size={18} color={ringsMode === 'individual' ? colors.primary : colors.onSurfaceVariant} />
                </Pressable>
              </View>
            }
          />

          {ringsMode === 'rings' ? (
            <BatteryRingCard
              overallPct={battery?.overall ?? 0}
              cnsPct={battery?.cns ?? 0}
              muscularPct={battery?.muscular ?? 0}
              sourceLabel={battery?.source === 'wellbeing-derived' ? 'derivado desde wellbeing' : 'estimación RN'}
            />
          ) : (
            <View style={styles.individualGrid}>
              {[
                { label: 'Muscular', value: `${Math.round(battery?.muscular ?? 0)}%`, accent: colors.ringMuscular },
                { label: 'SNC', value: `${Math.round(battery?.cns ?? 0)}%`, accent: colors.ringCns },
                { label: 'Espinal', value: `${Math.round(battery?.spinal ?? 0)}%`, accent: colors.ringSpinal },
              ].map(item => (
                <LiquidGlassCard key={item.label} style={styles.individualCard} padding={16}>
                  <Text style={[styles.individualLabel, { color: colors.onSurfaceVariant }]}>{item.label}</Text>
                  <Text style={[styles.individualValue, { color: item.accent }]}>{item.value}</Text>
                </LiquidGlassCard>
              ))}
            </View>
          )}

          <AugeStatusCard
            snapshot={augeStoreSnapshot}
            isRefreshing={augeIsRefreshing}
            onRefresh={() => void recomputeAugeRuntime()}
          />
        </View>

        <View style={styles.section}>
          <SectionTitle title="Sesión de hoy" />
          <SessionTodayCard
            programName={workoutOverview?.activeProgramName || 'Entrenamiento'}
            sessions={todaySessions}
            currentDayOfWeek={[7, 1, 2, 3, 4, 5, 6][new Date().getDay()]}
            onStartWorkout={handleStartWorkout}
            onOpenStartWorkoutModal={() => navigation.navigate('Workout', { screen: 'ProgramsList' })}
          />
        </View>

        <View style={styles.section}>
          <SectionTitle title="Progreso físico y alimentación" />
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.metricsScroll}
          >
            <HomeMetricCard
              eyebrow="Calorías"
              value={`${Math.round(todayNutritionTotals.calories)}`}
              helper={`Meta ${typeof rawSettings.dailyCalorieGoal === 'number' ? rawSettings.dailyCalorieGoal : 2200} kcal`}
              accent={colors.primary}
            />
            <HomeMetricCard
              eyebrow="Proteína"
              value={`${Math.round(todayNutritionTotals.protein)} g`}
              helper="Hoy"
              accent={colors.secondary}
            />
            <HomeMetricCard
              eyebrow="Peso"
              value={latestBody?.weight ? `${latestBody.weight} kg` : '--'}
              helper={weightDelta === 0 ? 'Sin cambios recientes' : `${Math.abs(weightDelta).toFixed(1)} kg vs último log`}
              accent={colors.onSurface}
              trend={weightDelta > 0 ? 'up' : weightDelta < 0 ? 'down' : 'neutral'}
            />
            <HomeMetricCard
              eyebrow="FFMI / Sueño"
              value={ffmi ? ffmi.toFixed(1) : sleepLabel}
              helper={ffmi ? 'Masa libre de grasa' : 'Promedio 7 días'}
              accent={colors.tertiary}
            />
          </ScrollView>
        </View>

        <View style={styles.section}>
          <SectionTitle
            title="Tus programas"
            action={<Text style={[styles.sectionActionText, { color: colors.onSurfaceVariant }]}>{programs.length} total</Text>}
          />

          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.programsScroll}>
            {activeProgramsFirst.length === 0 ? (
              <LiquidGlassCard style={styles.emptyProgramsCard} padding={20}>
                <Text style={[styles.emptyProgramsTitle, { color: colors.onSurface }]}>Sin programas aún</Text>
                <Text style={[styles.emptyProgramsBody, { color: colors.onSurfaceVariant }]}>
                  Aquí vamos a reflejar exactamente tus programas de la PWA. Por ahora ya dejamos el carrusel operativo para que no parezca una demo vacía.
                </Text>
              </LiquidGlassCard>
            ) : (
              activeProgramsFirst.map(program => (
                <ProgramPreviewCard
                  key={program.id}
                  program={program}
                  active={program.id === activeProgramState?.programId}
                  onPress={() =>
                    navigation.navigate('Workout', {
                      screen: 'ProgramDetail',
                      params: { programId: program.id },
                    })
                  }
                />
              ))
            )}
          </ScrollView>
        </View>

        <View style={styles.section}>
          <SectionTitle title="Rincones" />
          <View style={styles.cornersColumn}>
            <CornerCard
              title="Athlete ID"
              subtitle="Tu perfil atlético, cuerpo y progreso corporal en una sola vista."
              icon={<UserBadgeIcon size={24} color={colors.primary} />}
              onPress={() => navigation.navigate('Profile', { screen: 'ProfileMain' })}
            />
            <CornerCard
              title="WikiLab"
              subtitle="Biomecánica, bases del movimiento y exploración de ejercicios."
              icon={<WikiLabIcon size={24} color={colors.secondary} />}
              onPress={() => navigation.navigate('Wiki', { screen: 'WikiHome' })}
            />
            <CornerCard
              title="Nutrición"
              subtitle="Registro libre, base de alimentos y planner desde el nuevo flujo móvil."
              icon={<TrophyIcon size={24} color={colors.tertiary} />}
              onPress={() => navigation.navigate('Nutrition', { screen: 'NutritionDashboard' })}
            />
          </View>
        </View>

        {__DEV__ ? (
          <View style={[styles.devPanel, { borderColor: colors.outlineVariant }]}>
            <Text style={[styles.devText, { color: colors.onSurfaceVariant }]}>
              DEBUG {status.toUpperCase()} · activeSession {activeSession ? 'sí' : 'no'}
            </Text>
          </View>
        ) : null}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  shellContent: {
    paddingTop: 8,
  },
  heroHeader: {
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 8,
    gap: 24,
  },
  heroTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  avatarWrap: {
    width: 48,
    height: 48,
    borderRadius: 24,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroActions: {
    flexDirection: 'row',
    gap: 8,
  },
  heroActionButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  greeting: {
    fontSize: 32,
    fontWeight: '900',
    lineHeight: 38,
    letterSpacing: -1.1,
  },
  content: {
    gap: 28,
  },
  section: {
    gap: 14,
  },
  sectionHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  sectionHeaderText: {
    fontSize: 22,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: -0.5,
  },
  sectionActionText: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.4,
  },
  segmentedControl: {
    flexDirection: 'row',
    padding: 4,
    borderRadius: 999,
    gap: 4,
  },
  segmentedOption: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
  },
  individualGrid: {
    flexDirection: 'row',
    gap: 12,
  },
  individualCard: {
    flex: 1,
    borderRadius: 24,
  },
  individualLabel: {
    fontSize: 9,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  individualValue: {
    marginTop: 8,
    fontSize: 22,
    fontWeight: '900',
  },
  metricsScroll: {
    paddingRight: 4,
    gap: 14,
  },
  metricCard: {
    width: 164,
    borderRadius: 32,
  },
  metricEyebrow: {
    fontSize: 9,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.3,
  },
  metricValue: {
    marginTop: 12,
    fontSize: 30,
    fontWeight: '900',
    letterSpacing: -0.9,
  },
  metricFooter: {
    marginTop: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  metricHelper: {
    flex: 1,
    fontSize: 11,
    lineHeight: 15,
  },
  programsScroll: {
    paddingRight: 4,
    gap: 14,
  },
  programCardPressable: {
    width: 184,
  },
  programCardWrap: {
    gap: 10,
  },
  programCover: {
    height: 118,
    borderRadius: 32,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  programCoverImage: {
    ...StyleSheet.absoluteFillObject,
  },
  programBadge: {
    position: 'absolute',
    top: 14,
    left: 14,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    borderWidth: 1,
  },
  programBadgeText: {
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1.2,
  },
  programName: {
    fontSize: 12,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    paddingHorizontal: 4,
  },
  emptyProgramsCard: {
    width: 280,
    borderRadius: 32,
  },
  emptyProgramsTitle: {
    fontSize: 18,
    fontWeight: '800',
  },
  emptyProgramsBody: {
    marginTop: 8,
    fontSize: 13,
    lineHeight: 20,
  },
  cornersColumn: {
    gap: 14,
  },
  cornerPressable: {
    width: '100%',
  },
  cornerCard: {
    borderRadius: 34,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  cornerIconWrap: {
    width: 56,
    height: 56,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cornerTextWrap: {
    flex: 1,
    gap: 4,
  },
  cornerTitle: {
    fontSize: 18,
    fontWeight: '900',
  },
  cornerSubtitle: {
    fontSize: 12,
    lineHeight: 18,
  },
  devPanel: {
    marginTop: 12,
    paddingVertical: 12,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderRadius: 18,
    borderStyle: 'dashed',
  },
  devText: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
  },
  pressed: {
    opacity: 0.8,
    transform: [{ scale: 0.98 }],
  },
});
