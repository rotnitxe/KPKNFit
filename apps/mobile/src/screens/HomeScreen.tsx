import React, { useEffect, useMemo, useState, useCallback } from 'react';
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
import { AugeEnergyOrbs } from '@/components/auge/AugeEnergyOrbs';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';
import {
  BellIcon,
  IntertwinedRingsIcon,
  MoonIcon,
  SettingsIcon,
  SingleRingIcon,
  SunIcon,
  WikiLabIcon,
} from '@/components/icons';
import { useWorkoutStore } from '@/stores/workoutStore';
import { useProgramStore } from '@/stores/programStore';
import { useBodyStore } from '@/stores/bodyStore';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useMobileNutritionStore } from '@/stores/nutritionStore';
import { useSettingsStore } from '@/stores/settingsStore';
import { useColors, useTheme } from '@/theme';
import type { RootTabParamList } from '@/navigation/types';
import type { Program, Session } from '@/types/workout';

type RingsMode = 'rings' | 'individual';

function getGreeting() {
  const h = new Date().getHours();
  if (h < 12) return '¡Buenos días';
  if (h < 19) return '¡Buenas tardes';
  return '¡Buenas noches';
}

function SectionTitle({ title, action }: { title: string; action?: React.ReactNode }) {
  const colors = useColors();
  return (
    <View style={styles.sectionHeaderRow}>
      <Text style={[styles.sectionHeaderText, { color: colors.onSurface }]}>{title}</Text>
      {action}
    </View>
  );
}

function ProgramPreviewCard({
  program,
  onPress,
}: {
  program: Program;
  onPress: () => void;
}) {
  const colors = useColors();
  const { isDark } = useTheme();

  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.programCardPressable, pressed && styles.pressed]}>
      <View style={styles.programCardWrap}>
        <View style={[styles.programCover, { backgroundColor: isDark ? 'rgba(255,255,255,0.05)' : 'white', borderColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.03)' }]}>
          {program.coverImage ? (
            <Image source={{ uri: program.coverImage }} style={styles.programCoverImage} />
          ) : (
            <CaupolicanIcon size={40} color={isDark ? "rgba(255,255,255,0.1)" : "rgba(0,0,0,0.05)"} />
          )}
        </View>
        <Text style={[styles.programName, { color: isDark ? 'rgba(255,255,255,0.8)' : 'black' }]} numberOfLines={1}>
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
  
  const { summary: settingsSummary, hydrateFromMigration: hydrateSettings } = useSettingsStore();
  const { 
    programs, 
    activeProgramState, 
    status: programStatus,
    hydrateFromMigration: hydratePrograms,
  } = useProgramStore();
  const {
    overview,
    status: workoutStatus,
    hydrateFromMigration: hydrateWorkout,
    startActiveSession,
    activeSession,
    recoverActiveSession,
  } = useWorkoutStore();

  const rawSettings = useMemo(() => readStoredSettingsRaw(), [settingsSummary]);

  const {
    status: bodyStatus,
    hydrateFromMigration: hydrateBody,
  } = useBodyStore();

  const {
    status: wellbeingStatus,
    hydrateFromMigration: hydrateWellbeing,
  } = useWellbeingStore();

  const {
    hasHydrated: hasHydratedNutrition,
    hydrateFromStorage: hydrateNutrition,
  } = useMobileNutritionStore();

  const [ringsView, setRingsView] = useState<RingsMode>('rings');

  useEffect(() => {
    if (workoutStatus === 'idle') void hydrateWorkout();
    if (programStatus === 'idle') void hydratePrograms();
    if (bodyStatus === 'idle') void hydrateBody();
    if (wellbeingStatus === 'idle') void hydrateWellbeing();
    if (!hasHydratedNutrition) void hydrateNutrition();
    if (settingsSummary === null) void hydrateSettings();
    void recoverActiveSession();
  }, []);

  const activeProgram = useMemo(() =>
    programs.find((p: Program) => p.id === activeProgramState?.programId) || null
  , [programs, activeProgramState]);

  const sessionsWithOngoing = useMemo(() => {
    if (!activeProgram || !activeProgramState) return [] as TodaySessionItem[];

    const { currentMacrocycleIndex, currentBlockIndex, currentMesocycleIndex, currentWeekId } = activeProgramState;
    const macro = activeProgram.macrocycles?.[currentMacrocycleIndex ?? 0];
    const block = macro?.blocks?.[currentBlockIndex ?? 0];
    const meso = block?.mesocycles?.[currentMesocycleIndex ?? 0];
    const week = meso?.weeks.find(w => w.id === currentWeekId);

    if (!week) return [] as TodaySessionItem[];

    const today = new Date().getDay(); // 0-6 (Sun-Sat)
    const dayMap = [7, 1, 2, 3, 4, 5, 6]; // Map JS day to 1-7 (Mon-Sun)
    const currentDay = dayMap[today];

    return week.sessions.map(session => {
      const isToday = session.dayOfWeek === currentDay;
      const ongoing = activeSession?.session?.id === session.id;
      
      return {
        session: session,
        program: activeProgram,
        location: {
          macroIndex: currentMacrocycleIndex ?? 0,
          mesoIndex: currentMesocycleIndex ?? 0,
          weekId: currentWeekId ?? ''
        },
        isCompleted: false,
        dayOfWeek: session.dayOfWeek || 1,
        isOngoing: ongoing
      } as TodaySessionItem;
    }).sort((a, b) => {
      if (a.isOngoing) return -1;
      if (b.isOngoing) return 1;

      const aIsToday = a.session.dayOfWeek === currentDay;
      const bIsToday = b.session.dayOfWeek === currentDay;
      if (aIsToday && !bIsToday) return -1;
      if (!aIsToday && bIsToday) return 1;

      return (a.session.dayOfWeek ?? 0) - (b.session.dayOfWeek ?? 0);
    });
  }, [activeProgram, activeProgramState, activeSession]);

  const handleStartWorkout = useCallback((session: Session, program: Program) => {
    startActiveSession({ programId: program.id, session });
    navigation.navigate('Workout', {
      screen: 'ActiveSession',
      params: { 
        programId: program.id, 
        sessionId: session.id,
        sessionName: session.name 
      },
    });
  }, [startActiveSession, navigation]);

  const greeting = getGreeting();
  const userName = (rawSettings?.userName as string)?.trim() || 'Atleta';

  const headerContent = (
    <View style={styles.heroHeader}>
      <View style={styles.heroTopRow}>
        <View style={[styles.avatarWrap, { borderColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.05)', backgroundColor: isDark ? 'rgba(255,255,255,0.05)' : 'white' }]}>
          {rawSettings?.profilePicture ? (
            <Image source={{ uri: rawSettings.profilePicture as string }} style={styles.avatarImage} />
          ) : (
            <CaupolicanIcon size={24} color={isDark ? "rgba(255,255,255,0.2)" : "rgba(0,0,0,0.2)"} />
          )}
        </View>
        <View style={styles.heroActions}>
          <Pressable onPress={toggleDark} style={styles.heroActionButton}>
            {isDark ? <SunIcon size={22} color={colors.onSurfaceVariant} /> : <MoonIcon size={22} color={colors.onSurfaceVariant} />}
          </Pressable>
          <Pressable style={styles.heroActionButton}>
            <BellIcon size={24} color={colors.onSurfaceVariant} />
          </Pressable>
          <Pressable onPress={() => navigation.navigate('Settings')} style={styles.heroActionButton}>
            <SettingsIcon size={24} color={colors.onSurfaceVariant} />
          </Pressable>
        </View>
      </View>
      <Text style={[styles.greetingText, { color: isDark ? 'white' : '#1C1B1F' }]}>
        {greeting}, {'\n'}{userName}!
      </Text>
    </View>
  );

  const renderWithProgram = () => (
    <View style={styles.programView}>
      <View style={styles.section}>
        <SectionTitle 
          title="Tus RINGS" 
          action={
            <View style={[styles.segmentedControl, { backgroundColor: isDark ? 'rgba(255,255,255,0.1)' : '#ECE6F0' }]}>
              <Pressable 
                onPress={() => setRingsView('rings')} 
                style={[styles.segmentedOption, ringsView === 'rings' && { backgroundColor: isDark ? 'rgba(255,255,255,0.2)' : 'white' }]}
              >
                <IntertwinedRingsIcon size={22} color={ringsView === 'rings' ? colors.primary : colors.onSurfaceVariant} />
              </Pressable>
              <Pressable 
                onPress={() => setRingsView('individual')} 
                style={[styles.segmentedOption, ringsView === 'individual' && { backgroundColor: isDark ? 'rgba(255,255,255,0.2)' : 'white' }]}
              >
                <SingleRingIcon size={22} color={ringsView === 'individual' ? colors.primary : colors.onSurfaceVariant} />
              </Pressable>
            </View>
          }
        />
        <AugeEnergyOrbs 
          cns={overview?.battery?.cns ?? 0}
          muscular={overview?.battery?.muscular ?? 0}
          spinal={overview?.battery?.spinal ?? 0}
          liveMode={!!activeSession}
        />
      </View>

      <View style={styles.section}>
        <SectionTitle title="Sesión de hoy" />
        <SessionTodayCard
          programName={activeProgram?.name ?? 'Entrenamiento'}
          sessions={sessionsWithOngoing}
          currentDayOfWeek={[7, 1, 2, 3, 4, 5, 6][new Date().getDay()]}
          onStartWorkout={handleStartWorkout}
          onOpenStartWorkoutModal={() => navigation.navigate('Workout', { screen: 'ProgramDetail', params: { programId: activeProgram?.id } })}
        />
      </View>

      <View style={styles.section}>
        <SectionTitle title="Tus Programas" />
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.programsScroll}>
          {programs.map((prog: Program) => (
            <ProgramPreviewCard 
              key={prog.id} 
              program={prog} 
              onPress={() => navigation.navigate('Workout', { screen: 'ProgramDetail', params: { programId: prog.id } })} 
            />
          ))}
        </ScrollView>
      </View>

      <View style={styles.section}>
        <SectionTitle title="Rincones" />
        <View style={styles.cornersGrid}>
          <Pressable 
            onPress={() => navigation.navigate('Profile', { screen: 'ProfileMain' })}
            style={({ pressed }) => [styles.cornerCard, { backgroundColor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(255,255,255,0.5)', borderColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.02)' }, pressed && styles.pressed]}
          >
            <View style={[styles.cornerIconWrap, { backgroundColor: isDark ? 'rgba(255,255,255,0.1)' : '#ECE6F0' }]}>
              <CaupolicanIcon size={32} color={isDark ? "rgba(255,255,255,0.3)" : "rgba(73,69,79,0.5)"} />
            </View>
            <View style={styles.cornerText}>
              <Text style={[styles.cornerTitle, { color: isDark ? 'white' : '#1D1B20' }]}>Powerlifter Corner</Text>
              <Text style={[styles.cornerSubtitle, { color: isDark ? 'rgba(255,255,255,0.4)' : 'rgba(73,69,79,0.6)' }]}>Federaciones, historial y competiciones.</Text>
            </View>
          </Pressable>

          <Pressable 
            onPress={() => navigation.navigate('Wiki', { screen: 'WikiHome' })}
            style={({ pressed }) => [styles.cornerCard, { backgroundColor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(255,255,255,0.5)', borderColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.02)' }, pressed && styles.pressed]}
          >
            <View style={[styles.cornerIconWrap, { backgroundColor: isDark ? 'rgba(255,255,255,0.1)' : '#ECE6F0' }]}>
              <WikiLabIcon size={28} color={isDark ? "rgba(255,255,255,0.3)" : "rgba(73,69,79,0.5)"} />
            </View>
            <View style={styles.cornerText}>
              <Text style={[styles.cornerTitle, { color: isDark ? 'white' : '#1D1B20' }]}>WikiLab</Text>
              <Text style={[styles.cornerSubtitle, { color: isDark ? 'rgba(255,255,255,0.4)' : 'rgba(73,69,79,0.6)' }]}>Ciencia del entrenamiento y biomecánica.</Text>
            </View>
          </Pressable>
        </View>
      </View>
    </View>
  );

  const renderEmpty = () => (
    <View style={styles.emptyView}>
      <Text style={[styles.emptyHeader, { color: isDark ? 'white' : '#1C1B1F' }]}>
        Inicia tu próximo{'\n'}<Text style={{ color: colors.primary }}>Plan Maestro</Text>
      </Text>
      
      <AugeEnergyOrbs 
        cns={overview?.battery?.cns ?? 0}
        muscular={overview?.battery?.muscular ?? 0}
        spinal={overview?.battery?.spinal ?? 0}
        liveMode={!!activeSession}
      />

      <View style={styles.emptyArsenal}>
        <View style={styles.emptyIconWrap}>
          <CaupolicanIcon size={120} color={isDark ? "rgba(255,255,255,0.03)" : "rgba(0,0,0,0.03)"} />
        </View>
        <Text style={styles.emptyLabel}>Arsenal Vacío</Text>
        <Text style={[styles.emptyDescription, { color: isDark ? 'rgba(255,255,255,0.5)' : 'rgba(73,69,79,0.7)' }]}>
          Configura tu biometría avanzada creando tu primer programa de entrenamiento.
        </Text>
        <Pressable 
          onPress={() => navigation.navigate('Workout', { screen: 'ProgramsList' })}
          style={[styles.createButton, { backgroundColor: colors.primary }]}
        >
          <Text style={styles.createButtonText}>CREAR PROGRAMA</Text>
        </Pressable>
      </View>
    </View>
  );

  return (
    <ScreenShell
      title="Inicio"
      showBack={false}
      headerContent={headerContent}
      contentContainerStyle={styles.shellContent}
    >
      <View style={[styles.container, { backgroundColor: isDark ? '#121212' : 'transparent' }]}>
        {activeProgram ? renderWithProgram() : renderEmpty()}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingBottom: 40,
  },
  shellContent: {
    paddingTop: 8,
  },
  heroHeader: {
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 16,
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
    overflow: 'hidden',
  },
  avatarImage: {
    width: '100%',
    height: '100%',
  },
  heroActions: {
    flexDirection: 'row',
    gap: 8,
  },
  heroActionButton: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  greetingText: {
    fontSize: 32,
    fontWeight: '900',
    lineHeight: 38,
    letterSpacing: -1.1,
  },
  section: {
    marginBottom: 24,
    gap: 16,
  },
  sectionHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 24,
  },
  sectionHeaderText: {
    fontSize: 22,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: -0.5,
  },
  segmentedControl: {
    flexDirection: 'row',
    padding: 4,
    borderRadius: 24,
    gap: 4,
  },
  segmentedOption: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  programView: {
    gap: 8,
  },
  programsScroll: {
    paddingHorizontal: 24,
    gap: 16,
    paddingBottom: 8,
  },
  programCardPressable: {
    width: 176,
  },
  programCardWrap: {
    gap: 12,
  },
  programCover: {
    height: 112,
    borderRadius: 32,
    borderWidth: 1,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  programCoverImage: {
    width: '100%',
    height: '100%',
    opacity: 0.8,
  },
  programName: {
    fontSize: 11,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 0.2,
    paddingHorizontal: 8,
  },
  cornersGrid: {
    paddingHorizontal: 24,
    gap: 16,
  },
  cornerCard: {
    flexDirection: 'row',
    padding: 20,
    borderRadius: 36,
    borderWidth: 1,
    alignItems: 'center',
    gap: 24,
  },
  cornerIconWrap: {
    width: 64,
    height: 64,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cornerText: {
    flex: 1,
    gap: 2,
  },
  cornerTitle: {
    fontSize: 18,
    fontWeight: '900',
  },
  cornerSubtitle: {
    fontSize: 12,
    fontWeight: '500',
    lineHeight: 18,
  },
  emptyView: {
    paddingTop: 10,
    gap: 8,
  },
  emptyHeader: {
    fontSize: 32,
    fontWeight: '900',
    lineHeight: 38,
    letterSpacing: -1.1,
    paddingHorizontal: 24,
    marginBottom: 16,
  },
  emptyArsenal: {
    marginTop: 64,
    paddingHorizontal: 24,
    alignItems: 'center',
    gap: 16,
  },
  emptyIconWrap: {
    position: 'relative',
    marginBottom: 8,
  },
  emptyLabel: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
    color: 'rgba(73,69,79,0.4)',
  },
  emptyDescription: {
    fontSize: 16,
    fontWeight: '500',
    textAlign: 'center',
    lineHeight: 24,
    maxWidth: 280,
  },
  createButton: {
    width: '100%',
    height: 64,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.1,
    shadowRadius: 20,
  },
  createButtonText: {
    color: 'white',
    fontSize: 14,
    fontWeight: '900',
    letterSpacing: 1.5,
  },
  pressed: {
    opacity: 0.8,
  },
});
