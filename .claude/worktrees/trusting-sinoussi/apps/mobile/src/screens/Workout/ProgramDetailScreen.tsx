import React, { useEffect, useMemo, useState } from 'react';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  TextInput,
  Dimensions,
  Platform,
} from 'react-native';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WorkoutStackParamList } from '../../navigation/types';
import { useProgramStore } from '../../stores/programStore';
import type { Block, Program, ProgramWeek, Session } from '../../types/workout';
import { LiquidGlassCard } from '../../components/ui/LiquidGlassCard';
import { useColors } from '../../theme';
import { 
    ActivityIcon, 
    CheckCircleIcon, 
    ClockIcon, 
    PencilIcon, 
    TrashIcon, 
    SettingsIcon, 
    LayoutIcon, 
    ChevronRightIcon,
    ArrowLeftIcon,
    PlusIcon,
    InfoIcon
} from '../../components/icons';
import { AugeDashboard } from '../../components/auge/AugeDashboard';
import { CaupolicanBody } from '../../components/analytics/CaupolicanBody';
import VolumeView from '../../components/programs/VolumeView';
import ProgressView from '../../components/programs/ProgressView';
import HistoryView from '../../components/programs/HistoryView';
import { calculateAverageVolumeForWeeks } from '../../services/analysisService';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { Canvas, Rect, LinearGradient, vec, Blur, Circle } from '@shopify/react-native-skia';
import ReactNativeHapticFeedback from '@/services/hapticsService';
import { SessionDayPickerModal } from '../../components/workout/SessionDayPickerModal';
import { StartWorkoutModal } from '../../components/workout/StartWorkoutModal';
import { StartWorkoutDrawer } from '../../components/workout/StartWorkoutDrawer';
import { getSessionExercises, getSessionSetCount } from '../../utils/workoutSession';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

type ProgramMainTab = 'training' | 'analytics';
type TrainingSubTab = 'semana' | 'macrociclo' | 'split' | 'loops';
type AnalyticsSubTab = 'volumen' | 'muscles' | 'progreso' | 'historiales';

function getProgramWeeks(program: Program) {
  return program.macrocycles.flatMap(macro =>
    (macro.blocks ?? []).flatMap(block =>
      block.mesocycles.flatMap(meso => meso.weeks.map(week => ({ block, week, meso }))),
    ),
  );
}

function getProgramSessions(program: Program) {
  return getProgramWeeks(program).flatMap(({ week }) => week.sessions ?? []);
}

function getDayLabel(dayOfWeek?: number) {
  const days = ['LIBRE', 'LUNES', 'MARTES', 'MIÉRCOLES', 'JUEVES', 'VIERNES', 'SÁBADO', 'DOMINGO'];
  if (!dayOfWeek || dayOfWeek < 1 || dayOfWeek > 7) return 'FLEXIBLE';
  return days[dayOfWeek];
}

const HeroBackground = () => {
    const colors = useColors();
    return (
        <View style={StyleSheet.absoluteFill}>
            <Canvas style={StyleSheet.absoluteFill}>
                <Rect x={0} y={0} width={SCREEN_WIDTH} height={320}>
                    <LinearGradient
                        start={vec(0, 0)}
                        end={vec(SCREEN_WIDTH, 320)}
                        colors={[colors.primary, colors.secondary]}
                    />
                </Rect>
                <Circle cx={SCREEN_WIDTH * 0.8} cy={60} r={100} color="rgba(255,255,255,0.1)">
                    <Blur blur={40} />
                </Circle>
                <Circle cx={20} cy={220} r={80} color="rgba(168, 85, 247, 0.2)">
                    <Blur blur={60} />
                </Circle>
            </Canvas>
        </View>
    );
};

export const ProgramDetailScreen: React.FC = () => {
    const route = useRoute<RouteProp<WorkoutStackParamList, 'ProgramDetail'>>();
    const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
    const { programId } = route.params;
    const colors = useColors();

    const programs = useProgramStore(state => state.programs);
    const activeProgramState = useProgramStore(state => state.activeProgramState);
    const activateProgram = useProgramStore(state => state.activateProgram);
    const pauseProgram = useProgramStore(state => state.pauseProgram);
    const updateProgram = useProgramStore(state => state.updateProgram);
    const overview = useWorkoutStore(state => state.overview);
    const history = useWorkoutStore(state => state.history);
    const startActiveSession = useWorkoutStore(state => state.startActiveSession);
    const moveSessionToDay = useProgramStore(state => state.moveSessionToDay);
    const addSession = useProgramStore(state => state.addSession);
    const deleteSession = useProgramStore(state => state.deleteSession);

    const [mainTab, setMainTab] = useState<ProgramMainTab>('training');
    const [trainingTab, setTrainingTab] = useState<TrainingSubTab>('semana');
    const [analyticsTab, setAnalyticsTab] = useState<AnalyticsSubTab>('volumen');
    const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null);
    const [selectedWeekId, setSelectedWeekId] = useState<string | null>(null);

    const [dayPickerVisible, setDayPickerVisible] = useState(false);
    const [targetSessionToAssign, setTargetSessionToAssign] = useState<string | null>(null);
    const [targetSessionDay, setTargetSessionDay] = useState<number>(0);
    const [startWorkoutVisible, setStartWorkoutVisible] = useState(false);
    const [startWorkoutDrawerVisible, setStartWorkoutDrawerVisible] = useState(false);
    const [sessionToStart, setSessionToStart] = useState<Session | null>(null);

    const { exerciseList, muscleHierarchy } = useExerciseStore();

    const program = useMemo(
        () => programs.find(item => item.id === programId) ?? null,
        [programId, programs],
    );

    const volumeAnalysis = useMemo(() => {
        if (!program || !exerciseList.length) return [];
        const allWeeks = program.macrocycles.flatMap(m => 
            (m.blocks || []).flatMap(b => b.mesocycles.flatMap(meso => meso.weeks))
        );
        return calculateAverageVolumeForWeeks(allWeeks, exerciseList, muscleHierarchy);
    }, [program, exerciseList, muscleHierarchy]);

    const [isEditingDescription, setIsEditingDescription] = useState(false);
    const [tempDescription, setTempDescription] = useState(program?.description || '');

    useEffect(() => {
        if (!program) return;
        const firstBlock = program.macrocycles?.[0]?.blocks?.[0] ?? null;
        const firstWeek = firstBlock?.mesocycles?.[0]?.weeks?.[0] ?? null;
        setSelectedBlockId(prev => prev ?? firstBlock?.id ?? null);
        setSelectedWeekId(prev => prev ?? activeProgramState?.currentWeekId ?? firstWeek?.id ?? null);
    }, [activeProgramState?.currentWeekId, program]);

    const isCurrentProgram = activeProgramState?.programId === programId;
    const status = isCurrentProgram ? activeProgramState?.status ?? 'active' : null;

    const allWeeks = useMemo(() => (program ? getProgramWeeks(program) : []), [program]);
    const allSessions = useMemo(() => (program ? getProgramSessions(program) : []), [program]);
    const totalSessions = allSessions.length;
    const totalWeeks = allWeeks.length;

    const adherence = useMemo(() => {
        if (!program) return 0;
        const programLogs = history.filter(log => log.programId === program.id);
        const completedSessionIds = new Set(programLogs.map(l => l.sessionId));
        const allProgramSessions = getProgramSessions(program);
        return allProgramSessions.length > 0 
            ? Math.round((allProgramSessions.filter(s => completedSessionIds.has(s.id)).length / allProgramSessions.length) * 100)
            : 0;
    }, [history, program]);

    const currentWeekIndex = useMemo(() => {
        if (!program || !activeProgramState || activeProgramState.programId !== program.id) return 0;
        const weekIdx = allWeeks.findIndex(entry => entry.week.id === activeProgramState.currentWeekId);
        return weekIdx >= 0 ? weekIdx : 0;
    }, [activeProgramState, allWeeks, program]);

    const selectedBlock = useMemo<Block | null>(() => {
        if (!program) return null;
        return program.macrocycles.flatMap(macro => macro.blocks ?? []).find(block => block.id === selectedBlockId) ?? null;
    }, [program, selectedBlockId]);

    const selectedWeeks = useMemo(() => {
        if (!selectedBlock) return [] as ProgramWeek[];
        return selectedBlock.mesocycles.flatMap(meso => meso.weeks);
    }, [selectedBlock]);

    const selectedWeek = selectedWeeks.find(week => week.id === selectedWeekId) ?? selectedWeeks[0] ?? null;

    if (!program) return null;

    const handleSaveDescription = async () => {
        await updateProgram({ ...program, description: tempDescription });
        setIsEditingDescription(false);
        ReactNativeHapticFeedback.trigger('notificationSuccess');
    };

    const handlePrimaryAction = async () => {
        ReactNativeHapticFeedback.trigger('impactMedium');
        if (status === 'active' || status === 'paused') {
            await pauseProgram();
            return;
        }
        await activateProgram(program.id);
    };

    const handleAssignDay = (sessionId: string, currentDay: number) => {
        setTargetSessionToAssign(sessionId);
        setTargetSessionDay(currentDay);
        setDayPickerVisible(true);
    };

    const onDaySelected = async (day: number) => {
        if (targetSessionToAssign) {
            await moveSessionToDay(program.id, targetSessionToAssign, targetSessionDay, day);
            ReactNativeHapticFeedback.trigger('notificationSuccess');
        }
    };

    const handleAddSession = async () => {
        if (!selectedWeekId) return;
        ReactNativeHapticFeedback.trigger('impactLight');
        await addSession(program.id, selectedWeekId);
    };

    const handleDeleteSession = async (sessionId: string) => {
        if (!selectedWeekId) return;
        ReactNativeHapticFeedback.trigger('impactHeavy');
        await deleteSession(program.id, selectedWeekId, sessionId);
    };

    const handleOpenStartWorkout = (session: Session) => {
        setSessionToStart(session);
        setStartWorkoutVisible(true);
    };

    const handleStartWorkout = (payload: { key: 'A' | 'B' | 'C' | 'D'; session: Session }) => {
        ReactNativeHapticFeedback.trigger('notificationSuccess');
        startActiveSession({ programId, session: payload.session });
        setStartWorkoutVisible(false);
        setSessionToStart(null);
        navigation.navigate('ActiveSession', {
            programId,
            sessionId: payload.session.id,
            sessionName: payload.session.name,
        });
    };

    const handleStartWorkoutFromDrawer = (payload: {
        key: 'A' | 'B' | 'C' | 'D';
        session: Session;
        programId: string;
    }) => {
        ReactNativeHapticFeedback.trigger('notificationSuccess');
        startActiveSession({ programId: payload.programId, session: payload.session });
        setStartWorkoutDrawerVisible(false);
        navigation.navigate('ActiveSession', {
            programId: payload.programId,
            sessionId: payload.session.id,
            sessionName: payload.session.name,
        });
    };

    return (
        <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]} edges={['top']}>
            <ScrollView 
                style={styles.scroll} 
                contentContainerStyle={styles.scrollContent} 
                showsVerticalScrollIndicator={false}
            >
                {/* PREMIUM HERO SECTION */}
                <View style={styles.heroShell}>
                    <HeroBackground />
                    
                    <View style={styles.heroHeader}>
                        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.heroIconBtn}>
                            <ArrowLeftIcon size={22} color="#FFF" />
                        </TouchableOpacity>
                        <View style={[styles.statusPill, { backgroundColor: 'rgba(255,255,255,0.15)' }]}>
                            <Text style={styles.statusPillText}>
                                {status === 'active' ? 'ACTIVO' : status === 'paused' ? 'PAUSADO' : 'BORRADOR'}
                            </Text>
                        </View>
                        <TouchableOpacity style={styles.heroIconBtn}>
                            <SettingsIcon size={22} color="#FFF" />
                        </TouchableOpacity>
                    </View>

                    <View style={styles.heroBody}>
                        <View style={styles.heroInfo}>
                            <View style={styles.programModeChip}>
                                <Text style={styles.programModeText}>
                                    {program.mode === 'hypertrophy' ? 'HIPERTROFIA' : program.mode === 'powerlifting' ? 'POWERLIFTING' : 'POWERBUILDING'}
                                </Text>
                            </View>
                            <Text style={styles.heroTitle}>{program.name}</Text>
                            
                            {isEditingDescription ? (
                                <View style={styles.editDescRow}>
                                    <TextInput
                                        style={styles.descInput}
                                        value={tempDescription}
                                        onChangeText={setTempDescription}
                                        autoFocus
                                        multiline
                                        maxLength={120}
                                    />
                                    <TouchableOpacity onPress={handleSaveDescription} style={styles.saveDescBtn}>
                                        <Text style={styles.saveDescText}>OK</Text>
                                    </TouchableOpacity>
                                </View>
                            ) : (
                                <TouchableOpacity onPress={() => { setIsEditingDescription(true); setTempDescription(program.description || ''); }} style={styles.descClickable}>
                                    <Text style={styles.heroDescription} numberOfLines={2}>
                                        {program.description || 'Toca para añadir descripción...'}
                                    </Text>
                                    <View style={{ marginLeft: 6 }}><PencilIcon size={12} color="#FFFFFF80" /></View>
                                </TouchableOpacity>
                            )}
                        </View>

                        <TouchableOpacity style={styles.playBtn} onPress={handlePrimaryAction}>
                            <Text style={[styles.playBtnText, { color: colors.primary }]}>
                                {status === 'active' ? 'II' : '▶'}
                            </Text>
                        </TouchableOpacity>
                    </View>

                    {/* LIQUID GLASS STATS BAR */}
                    <View style={styles.statsShell}>
                        <View style={styles.statsGlassBar}>
                            <View style={styles.statBox}>
                                <Text style={styles.statLabel}>SEMANA</Text>
                                <Text style={styles.statValue}>{currentWeekIndex + 1}/{totalWeeks}</Text>
                            </View>
                            <View style={[styles.statDivider, { backgroundColor: 'rgba(255,255,255,0.1)' }]} />
                            <View style={styles.statBox}>
                                <Text style={styles.statLabel}>ADHERENCIA</Text>
                                <Text style={styles.statValue}>{adherence}%</Text>
                            </View>
                            <View style={[styles.statDivider, { backgroundColor: 'rgba(255,255,255,0.1)' }]} />
                            <View style={styles.statBox}>
                                <Text style={styles.statLabel}>SESIONES</Text>
                                <Text style={styles.statValue}>{totalSessions}</Text>
                            </View>
                        </View>
                    </View>
                </View>

                {/* TAILORED TAB SELECTORS */}
                <View style={styles.tabsSection}>
                    <View style={[styles.mainTabs, { backgroundColor: colors.surfaceVariant, borderColor: colors.outlineVariant }]}>
                        <Pressable 
                            onPress={() => setMainTab('training')}
                            style={[styles.mainTabBtn, mainTab === 'training' && { backgroundColor: colors.primary }]}
                        >
                            <Text style={[styles.mainTabTxt, mainTab === 'training' && { color: colors.onPrimary }]}>ESTRUCTURA</Text>
                        </Pressable>
                        <Pressable 
                            onPress={() => setMainTab('analytics')}
                            style={[styles.mainTabBtn, mainTab === 'analytics' && { backgroundColor: colors.primary }]}
                        >
                            <Text style={[styles.mainTabTxt, mainTab === 'analytics' && { color: colors.onPrimary }]}>ANÁLISIS</Text>
                        </Pressable>
                    </View>

                    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.subTabsRow}>
                        {(mainTab === 'training' ? [
                            { id: 'semana', label: 'MI SEMANA' },
                            { id: 'split', label: 'SPLIT' },
                            { id: 'macrociclo', label: 'MACROCICLO' },
                            { id: 'loops', label: 'PROTOCOLO' }
                        ] : [
                            { id: 'volumen', label: 'VOLUMEN' },
                            { id: 'muscles', label: 'MÚSCULOS' },
                            { id: 'progreso', label: 'PROGRESO' },
                            { id: 'historiales', label: 'HISTORIAL' }
                        ]).map(tab => {
                            const isActive = mainTab === 'training' ? trainingTab === tab.id : analyticsTab === tab.id;
                            return (
                                <TouchableOpacity 
                                    key={tab.id}
                                    onPress={() => mainTab === 'training' ? setTrainingTab(tab.id as TrainingSubTab) : setAnalyticsTab(tab.id as AnalyticsSubTab)}
                                    style={[styles.subTabBtn, isActive && { borderBottomColor: colors.primary, borderBottomWidth: 3 }]}
                                >
                                    <Text style={[styles.subTabTxt, { color: isActive ? colors.primary : colors.onSurfaceVariant }]}>{tab.label}</Text>
                                </TouchableOpacity>
                            );
                        })}
                    </ScrollView>
                </View>

                {/* TRAINING CONTENT */}
                {mainTab === 'training' ? (
                    <View style={styles.trainingStack}>
                        <View style={styles.blockExplorer}>
                            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.blockChips}>
                                {program.macrocycles.flatMap(m => m.blocks ?? []).map(b => (
                                    <TouchableOpacity 
                                        key={b.id} 
                                        onPress={() => setSelectedBlockId(b.id)}
                                        style={[styles.blockChip, { backgroundColor: b.id === selectedBlockId ? colors.primaryContainer : colors.surfaceVariant }]}
                                    >
                                        <Text style={[styles.blockChipTxt, { color: b.id === selectedBlockId ? colors.primary : colors.onSurfaceVariant }]}>{b.name.toUpperCase()}</Text>
                                    </TouchableOpacity>
                                ))}
                            </ScrollView>
                        </View>

                        {trainingTab === 'semana' && (
                            <View style={styles.weekView}>
                                <TouchableOpacity
                                    onPress={() => setStartWorkoutDrawerVisible(true)}
                                    style={[styles.startDrawerBtn, { backgroundColor: colors.primaryContainer, borderColor: `${colors.primary}55` }]}
                                >
                                    <CheckCircleIcon size={16} color={colors.primary} />
                                    <Text style={[styles.startDrawerTxt, { color: colors.primary }]}>Iniciar sesión (selector completo)</Text>
                                </TouchableOpacity>

                                <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.weekChips}>
                                    {selectedWeeks.map((w, idx) => (
                                        <TouchableOpacity 
                                            key={w.id} 
                                            onPress={() => setSelectedWeekId(w.id)}
                                            style={[styles.weekChip, { borderColor: w.id === selectedWeekId ? colors.primary : colors.outlineVariant }]}
                                        >
                                            <Text style={[styles.weekChipLabel, { color: colors.onSurfaceVariant }]}>S{idx + 1}</Text>
                                            <Text style={[styles.weekChipName, { color: colors.onSurface }]}>{w.name}</Text>
                                        </TouchableOpacity>
                                    ))}
                                </ScrollView>

                                <View style={styles.sessionList}>
                                    {selectedWeek?.sessions?.map(session => (
                                        <LiquidGlassCard key={session.id} style={styles.sessionItem}>
                                            <View style={styles.sessionHeader}>
                                                <View style={[styles.dayBadge, { backgroundColor: colors.secondaryContainer }]}>
                                                    <Text style={[styles.dayText, { color: colors.secondary }]}>{getDayLabel(session.dayOfWeek)}</Text>
                                                </View>
                                                <TouchableOpacity
                                                    onPress={() => navigation.navigate('SessionDetail', { sessionId: session.id })}
                                                    style={styles.sessionInfo}
                                                    activeOpacity={0.7}
                                                >
                                                    <Text style={[styles.sessionTitle, { color: colors.onSurface }]}>{session.name}</Text>
                                                    <Text style={[styles.sessionMeta, { color: colors.onSurfaceVariant }]}>{getSessionExercises(session).length} EJERCICIOS · {getSessionSetCount(session)} SERIES</Text>
                                                </TouchableOpacity>
                                                <View style={styles.sessionActions}>
                                                    <TouchableOpacity 
                                                        onPress={() => handleAssignDay(session.id, session.dayOfWeek ?? 0)}
                                                        style={styles.sessionActionBtn}
                                                    >
                                                        <PlusIcon size={18} color={colors.onSurfaceVariant} />
                                                    </TouchableOpacity>
                                                    <TouchableOpacity 
                                                        onPress={() => navigation.navigate('SessionEditor', { programId, weekId: selectedWeek.id, sessionId: session.id })}
                                                        style={styles.sessionActionBtn}
                                                    >
                                                        <PencilIcon size={18} color={colors.onSurfaceVariant} />
                                                    </TouchableOpacity>
                                                    <TouchableOpacity 
                                                        onPress={() => handleDeleteSession(session.id)}
                                                        style={styles.sessionActionBtn}
                                                    >
                                                        <TrashIcon size={18} color={colors.error} />
                                                    </TouchableOpacity>
                                                    <TouchableOpacity 
                                                        onPress={() => handleOpenStartWorkout(session)}
                                                        style={[styles.sessionActionBtn, { backgroundColor: colors.primary }]}
                                                    >
                                                        <ChevronRightIcon size={18} color={colors.onPrimary} />
                                                    </TouchableOpacity>
                                                </View>
                                            </View>
                                        </LiquidGlassCard>
                                    ))}

                                    <TouchableOpacity 
                                        onPress={handleAddSession}
                                        style={[styles.addSessionBtn, { borderColor: colors.outlineVariant }]}
                                    >
                                        <PlusIcon size={20} color={colors.primary} />
                                        <Text style={[styles.addSessionBtnText, { color: colors.primary }]}>AÑADIR SESIÓN</Text>
                                    </TouchableOpacity>
                                </View>
                            </View>
                        )}

                        {/* STRUCTURE TOOLBOX */}
                        <View style={styles.toolboxSection}>
                            <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>STRUCTURE TOOLBOX</Text>
                            <View style={styles.toolboxGrid}>
                                <TouchableOpacity 
                                    onPress={() => navigation.navigate('SplitEditor', { programId })}
                                    style={[styles.toolBtn, { backgroundColor: colors.surfaceContainer }]}
                                >
                                    <LayoutIcon size={20} color={colors.primary} />
                                    <Text style={[styles.toolBtnTxt, { color: colors.onSurface }]}>Split Semanal</Text>
                                </TouchableOpacity>
                                <TouchableOpacity 
                                    onPress={() => navigation.navigate('MacrocycleEditor', { programId })}
                                    style={[styles.toolBtn, { backgroundColor: colors.surfaceContainer }]}
                                >
                                    <ActivityIcon size={20} color={colors.primary} />
                                    <Text style={[styles.toolBtnTxt, { color: colors.onSurface }]}>Macrociclo</Text>
                                </TouchableOpacity>
                            </View>
                        </View>
                    </View>
                ) : (
                    <View style={styles.analyticsSection}>
                        {analyticsTab === 'volumen' && (
                            <VolumeView 
                                volumeAnalysis={volumeAnalysis}
                                muscleHierarchy={muscleHierarchy}
                                currentWeeks={allWeeks}
                                selectedWeekId={selectedWeekId}
                                onSelectWeek={setSelectedWeekId}
                            />
                        )}

                        {analyticsTab === 'muscles' && (
                            <View style={{ paddingBottom: 40, marginTop: 12 }}>
                                <LiquidGlassCard style={{ padding: 20, marginBottom: 16 }}>
                                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
                                        <View>
                                            <Text style={{ fontSize: 13, fontWeight: '900', color: colors.primary, textTransform: 'uppercase' }}>MAPA DE CARGA</Text>
                                            <Text style={{ fontSize: 10, color: colors.onSurfaceVariant, fontWeight: '700' }}>Promedio semanal del programa</Text>
                                        </View>
                                        <InfoIcon size={18} color={colors.onSurfaceVariant} opacity={0.5} />
                                    </View>
                                    <View style={{ alignItems: 'center' }}>
                                        <CaupolicanBody data={volumeAnalysis} />
                                    </View>
                                </LiquidGlassCard>

                                <View style={{ gap: 10 }}>
                                    {volumeAnalysis.map(item => (
                                        <View key={item.muscleGroup} style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: 'rgba(255,255,255,0.4)', padding: 16, borderRadius: 24, borderWidth: 1, borderColor: 'rgba(255,255,255,0.6)' }}>
                                            <View>
                                                <Text style={{ fontSize: 14, fontWeight: '800', color: colors.onSurface }}>{item.muscleGroup}</Text>
                                                <Text style={{ fontSize: 10, fontWeight: '700', color: colors.onSurfaceVariant, textTransform: 'uppercase' }}>{item.totalSets} Series Totales</Text>
                                            </View>
                                            <View style={{ alignItems: 'flex-end', gap: 6 }}>
                                                <Text style={{ fontSize: 16, fontWeight: '900', color: colors.primary }}>{item.displayVolume}</Text>
                                                <View style={{ width: 60, height: 4, backgroundColor: colors.outlineVariant, borderRadius: 2, overflow: 'hidden' }}>
                                                    <View style={{ width: `${Math.min(100, (item.displayVolume / 20) * 100)}%`, height: '100%', backgroundColor: colors.primary }} />
                                                </View>
                                            </View>
                                        </View>
                                    ))}
                                </View>
                            </View>
                        )}

                        {analyticsTab === 'progreso' && (
                            <ProgressView 
                                program={program}
                                history={history}
                            />
                        )}

                        {analyticsTab === 'historiales' && (
                            <HistoryView 
                                program={program}
                                history={history}
                            />
                        )}
                        
                        <View style={styles.analyticsStats}>
                            <LiquidGlassCard style={styles.analyticRow}>
                                <Text style={[styles.analyticLabel, { color: colors.onSurfaceVariant }]}>COMPLETADO ESTA SEMANA</Text>
                                <Text style={[styles.analyticValue, { color: colors.onSurface }]}>{overview?.completedSetsThisWeek ?? 0} / {overview?.plannedSetsThisWeek ?? 60} SERIES</Text>
                            </LiquidGlassCard>
                        </View>
                    </View>
                )}
            </ScrollView>

            <SessionDayPickerModal 
                visible={dayPickerVisible}
                onClose={() => setDayPickerVisible(false)}
                onSelect={onDaySelected}
                currentDay={targetSessionDay}
            />
            <StartWorkoutModal
                visible={startWorkoutVisible}
                session={sessionToStart}
                onClose={() => {
                    setStartWorkoutVisible(false);
                    setSessionToStart(null);
                }}
                onStart={handleStartWorkout}
            />
            <StartWorkoutDrawer
                visible={startWorkoutDrawerVisible}
                programs={[program]}
                onClose={() => setStartWorkoutDrawerVisible(false)}
                onStart={handleStartWorkoutFromDrawer}
            />
        </SafeAreaView>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    scroll: {
        flex: 1,
    },
    scrollContent: {
        paddingBottom: 40,
    },
    heroShell: {
        height: 320,
        justifyContent: 'flex-end',
        paddingBottom: 30,
    },
    heroHeader: {
        position: 'absolute',
        top: 20,
        left: 20,
        right: 20,
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    heroIconBtn: {
        width: 40,
        height: 40,
        borderRadius: 20,
        backgroundColor: 'rgba(255,255,255,0.15)',
        justifyContent: 'center',
        alignItems: 'center',
    },
    statusPill: {
        paddingHorizontal: 12,
        paddingVertical: 6,
        borderRadius: 20,
    },
    statusPillText: {
        color: '#FFF',
        fontSize: 10,
        fontWeight: '900',
        letterSpacing: 1.2,
    },
    heroBody: {
        flexDirection: 'row',
        paddingHorizontal: 24,
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: 20,
    },
    heroInfo: {
        flex: 1,
        gap: 4,
    },
    programModeChip: {
        backgroundColor: 'rgba(255,255,255,0.2)',
        paddingHorizontal: 8,
        paddingVertical: 4,
        borderRadius: 6,
        alignSelf: 'flex-start',
    },
    programModeText: {
        color: '#FFF',
        fontSize: 9,
        fontWeight: '900',
    },
    heroTitle: {
        color: '#FFF',
        fontSize: 32,
        fontWeight: '900',
        letterSpacing: -1,
    },
    descClickable: {
        flexDirection: 'row',
        alignItems: 'center',
    },
    heroDescription: {
        color: 'rgba(255,255,255,0.8)',
        fontSize: 13,
        fontWeight: '500',
        maxWidth: 240,
    },
    editDescRow: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 10,
    },
    descInput: {
        flex: 1,
        backgroundColor: 'rgba(255,255,255,0.2)',
        borderRadius: 8,
        color: '#FFF',
        padding: 8,
        fontSize: 13,
    },
    saveDescBtn: {
        backgroundColor: '#FFF',
        paddingHorizontal: 10,
        paddingVertical: 6,
        borderRadius: 6,
    },
    saveDescText: {
        fontWeight: '900',
        fontSize: 10,
    },
    playBtn: {
        width: 60,
        height: 60,
        borderRadius: 30,
        backgroundColor: '#FFF',
        justifyContent: 'center',
        alignItems: 'center',
    },
    playBtnText: {
        fontSize: 24,
        fontWeight: '900',
    },
    statsShell: {
        paddingHorizontal: 20,
    },
    statsGlassBar: {
        flexDirection: 'row',
        backgroundColor: 'rgba(255,255,255,0.15)',
        borderRadius: 24,
        padding: 16,
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.2)',
    },
    statBox: {
        flex: 1,
        alignItems: 'center',
    },
    statLabel: {
        color: 'rgba(255,255,255,0.6)',
        fontSize: 9,
        fontWeight: '900',
        marginBottom: 4,
    },
    statValue: {
        color: '#FFF',
        fontSize: 18,
        fontWeight: '900',
    },
    statDivider: {
        width: 1,
        height: '80%',
        alignSelf: 'center',
    },
    tabsSection: {
        paddingHorizontal: 20,
        paddingTop: 24,
    },
    mainTabs: {
        flexDirection: 'row',
        borderRadius: 28,
        padding: 5,
        borderWidth: 1,
    },
    mainTabBtn: {
        flex: 1,
        borderRadius: 24,
        paddingVertical: 12,
        alignItems: 'center',
    },
    mainTabTxt: {
        fontSize: 11,
        fontWeight: '900',
        color: 'rgba(0,0,0,0.4)',
    },
    subTabsRow: {
        gap: 20,
        marginTop: 16,
    },
    subTabBtn: {
        paddingVertical: 10,
    },
    subTabTxt: {
        fontSize: 12,
        fontWeight: '900',
        letterSpacing: 0.5,
    },
    trainingStack: {
        paddingHorizontal: 20,
        paddingTop: 16,
        gap: 20,
    },
    blockExplorer: {
        marginBottom: 8,
    },
    blockChips: {
        gap: 10,
    },
    blockChip: {
        paddingHorizontal: 16,
        paddingVertical: 10,
        borderRadius: 14,
    },
    blockChipTxt: {
        fontSize: 11,
        fontWeight: '900',
    },
    weekView: {
        gap: 16,
    },
    startDrawerBtn: {
        borderWidth: 1,
        borderRadius: 16,
        minHeight: 46,
        paddingHorizontal: 12,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
    },
    startDrawerTxt: {
        fontSize: 12,
        fontWeight: '900',
        letterSpacing: 0.6,
        textTransform: 'uppercase',
    },
    weekChips: {
        gap: 12,
    },
    weekChip: {
        minWidth: 110,
        padding: 14,
        borderRadius: 18,
        backgroundColor: 'rgba(255,255,255,0.6)',
        borderWidth: 1.5,
    },
    weekChipLabel: {
        fontSize: 10,
        fontWeight: '900',
        marginBottom: 4,
    },
    weekChipName: {
        fontSize: 14,
        fontWeight: '800',
    },
    sessionList: {
        gap: 12,
    },
    sessionItem: {
        padding: 16,
    },
    sessionHeader: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
    },
    dayBadge: {
        width: 80,
        paddingVertical: 8,
        borderRadius: 10,
        alignItems: 'center',
    },
    dayText: {
        fontSize: 10,
        fontWeight: '900',
    },
    sessionInfo: {
        flex: 1,
    },
    sessionTitle: {
        fontSize: 17,
        fontWeight: '900',
    },
    sessionMeta: {
        fontSize: 10,
        fontWeight: '700',
        marginTop: 4,
        opacity: 0.6,
    },
    sessionActions: {
        flexDirection: 'row',
        gap: 8,
    },
    sessionActionBtn: {
        width: 36,
        height: 36,
        borderRadius: 18,
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: 'rgba(0,0,0,0.05)',
    },
    toolboxSection: {
        marginTop: 10,
    },
    sectionLabel: {
        fontSize: 10,
        fontWeight: '900',
        letterSpacing: 1.5,
        marginBottom: 12,
    },
    toolboxGrid: {
        flexDirection: 'row',
        gap: 12,
    },
    toolBtn: {
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        gap: 10,
        padding: 16,
        borderRadius: 20,
    },
    toolBtnTxt: {
        fontSize: 13,
        fontWeight: '800',
    },
    analyticsSection: {
        paddingTop: 16,
    },
    analyticsStats: {
        paddingHorizontal: 20,
        marginTop: 16,
    },
    analyticRow: {
        padding: 20,
        gap: 6,
    },
    analyticLabel: {
        fontSize: 10,
        fontWeight: '900',
        letterSpacing: 1,
    },
    analyticValue: {
        fontSize: 22,
        fontWeight: '900',
    },
    addSessionBtn: {
        marginTop: 8,
        paddingVertical: 16,
        borderWidth: 1.5,
        borderStyle: 'dashed',
        borderRadius: 20,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 10,
    },
    addSessionBtnText: {
        fontSize: 13,
        fontWeight: '900',
        letterSpacing: 1.5,
    },
});
