import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { 
    Alert, 
    Modal, 
    Pressable, 
    ScrollView, 
    StyleSheet, 
    Text, 
    TextInput, 
    TouchableOpacity, 
    View, 
    Dimensions, 
    Platform,
    StatusBar,
    Animated
} from 'react-native';

import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WorkoutHeader } from '../../components/workout/WorkoutHeader';
import { AugeTelemetryPanel } from '../../components/workout/AugeTelemetryPanel';
import { CardCarouselBar } from '../../components/workout/CardCarouselBar';
import { ExerciseBlockCard } from '../../components/workout/ExerciseBlockCard';
import { FinishSessionModal } from '../../components/workout/FinishSessionModal';
import { PostSessionQuestionnaireModal } from '../../components/workout/PostSessionQuestionnaireModal';
import { PostExerciseDrawer } from '../../components/workout/PostExerciseDrawer';
import { WarmupDrawer } from '../../components/workout/WarmupDrawer';
import { CheckCircleIcon, ClockIcon, PencilIcon, ActivityIcon, ArrowDownIcon, ChevronRightIcon, TrophyIcon } from '../../components/icons';
import { WorkoutStackParamList } from '../../navigation/types';
import { useProgramStore } from '../../stores/programStore';
import { PostSessionFeedbackInput, useWorkoutStore } from '../../stores/workoutStore';
import { calculateCompletedSessionDrainBreakdown } from '../../services/fatigueService';
import { useExerciseStore } from '../../stores/exerciseStore';
import { CompletedExercise, CompletedSet, OngoingSetData, Exercise, PostExerciseFeedback } from '../../types/workout';
import { useColors, useTheme } from '../../theme';
import { LiquidGlassCard } from '../../components/ui/LiquidGlassCard';
import { Button } from '../../components/ui';
import { useSettingsStore } from '../../stores/settingsStore';
import ReactNativeHapticFeedback from '@/services/hapticsService';
import { Canvas, Rect, LinearGradient, vec, Blur, Fill } from '@shopify/react-native-skia';
import { calculateSessionTonnage } from './activeSessionMetrics';
import { getSessionExercises } from '../../utils/workoutSession';

const { height: SCREEN_HEIGHT, width: SCREEN_WIDTH } = Dimensions.get('window');

type ActiveSessionRouteParams = RouteProp<WorkoutStackParamList, 'ActiveSession'>;
type NavigationProp = NativeStackNavigationProp<WorkoutStackParamList>;

export const ActiveSessionScreen: React.FC = () => {
    const route = useRoute<ActiveSessionRouteParams>();
    const navigation = useNavigation<NavigationProp>();
    const { programId, sessionId, sessionName } = route.params;
    const colors = useColors();
    const { isDark } = useTheme();

    const programs = useProgramStore(state => state.programs);
    const {
        activeSession,
        startActiveSession,
        finishActiveSession,
        discardActiveSession,
        startRestTimer,
        cancelRestTimer,
        sessionFinishState,
        postSessionFeedbackHistory,
        loggingState,
        overview,
        reminderSettings,
        savePostExerciseFeedback,
    } = useWorkoutStore();

    const { exerciseList } = useExerciseStore.getState();

    // State for workout log data (from FinishSessionModal) and feedback data (from questionnaire)
    const [workoutLogData, setWorkoutLogData] = useState<{
        notes?: string;
        discomforts?: string[];
        fatigueLevel?: number;
        mentalClarity?: number;
        durationInMinutes?: number;
        logDate?: string;
        focus?: number;
        pump?: number;
        environmentTags?: string[];
        sessionDifficulty?: number;
        planAdherenceTags?: string[];
        muscleBatteries?: Record<string, number>;
    } | null>(null);
    const [feedbackData, setFeedbackData] = useState<{
        sessionRpe?: number;
        energyAfter?: number;
        sorenessAfter?: number;
        hadPain?: boolean;
        notes?: string;
    } | null>(null);
    const [isFinishModalVisible, setFinishModalVisible] = useState(false);
    const [isQuestionnaireVisible, setIsQuestionnaireVisible] = useState(false);
    const [sessionNotes, setSessionNotes] = useState(''); // This is no longer used, but we keep for now? We'll remove.
    const [warmupVisible, setWarmupVisible] = useState(false);
    const [warmupBaseWeight, setWarmupBaseWeight] = useState(0);
    const [postExerciseVisible, setPostExerciseVisible] = useState(false);
    const [postExerciseId, setPostExerciseId] = useState<string | null>(null);
    const settingsSummary = useSettingsStore(state => state.summary);

    // Handle when the user finishes the session (workout log data)
    const handleFinishSession = useCallback((data: {
        notes?: string;
        discomforts?: string[];
        fatigueLevel?: number;
        mentalClarity?: number;
        durationInMinutes?: number;
        logDate?: string;
        focus?: number;
        pump?: number;
        environmentTags?: string[];
        sessionDifficulty?: number;
        planAdherenceTags?: string[];
        muscleBatteries?: Record<string, number>;
    }) => {
        setWorkoutLogData(data);
        setIsQuestionnaireVisible(true);
    }, []);

    // Handle when the user submits the feedback (feedback data)
    const handleSubmitFeedback = useCallback(async (feedback: {
        sessionRpe?: number;
        energyAfter?: number;
        sorenessAfter?: number;
        hadPain?: boolean;
        notes?: string;
    }) => {
        if (!workoutLogData) {
            Alert.alert('Error', 'No workout log data found.');
            return;
        }

        // Combine the data
        const combinedData = {
            ...workoutLogData,
            ...feedback,
        };

        // Call the workout store's finishActiveSession with the combined data
        await finishActiveSession(combinedData as any); // We'll update the workoutStore to accept this

        // Reset the state
        setWorkoutLogData(null);
        setFeedbackData(null);
        setFinishModalVisible(false);
        setIsQuestionnaireVisible(false);
    }, [workoutLogData, finishActiveSession]);

    // Handle when the user wants to discard the session
    const handleDiscard = useCallback(() => {
        Alert.alert(
            'Descartar sesión',
            '¿Estás seguro de que deseas descartar esta sesión? Todos los datos se perderán.',
            [
                {
                    text: 'Cancelar',
                    style: 'cancel',
                },
                {
                    text: 'Descartar',
                    style: 'destructive',
                    onPress: () => {
                        discardActiveSession();
                        setFinishModalVisible(false);
                        setIsQuestionnaireVisible(false);
                        setWorkoutLogData(null);
                        setFeedbackData(null);
                    },
                },
            ]
        );
    }, [discardActiveSession]);

    // Handle when the user presses the finish button (to show the FinishSessionModal)
    const handleFinishPress = useCallback(() => {
        setFinishModalVisible(true);
    }, []);

    // Rest timer logic (unchanged)
    const [restTimerRemaining, setRestTimerRemaining] = useState(0);
    const [isRestTimerActive, setIsRestTimerActive] = useState(false);
    const restTimerIntervalRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        if (isRestTimerActive && restTimerRemaining > 0) {
            restTimerIntervalRef.current = setInterval(() => {
                setRestTimerRemaining(prev => Math.max(prev - 1, 0));
            }, 1000);
        } else if (restTimerIntervalRef.current) {
            clearInterval(restTimerIntervalRef.current);
            restTimerIntervalRef.current = null;
        }
        return () => {
            if (restTimerIntervalRef.current) {
                clearInterval(restTimerIntervalRef.current);
            }
        };
    }, [isRestTimerActive, restTimerRemaining]);

    const startRestCountdown = useCallback((seconds: number) => {
        setRestTimerRemaining(seconds);
        setIsRestTimerActive(true);
        startRestTimer(seconds);
    }, [startRestTimer]);

    const handleCancelRest = useCallback(() => {
        setIsRestTimerActive(false);
        setRestTimerRemaining(0);
        cancelRestTimer();
    }, [cancelRestTimer]);

    // Get the current exercise and set for highlighting
    const currentExerciseId = activeSession?.activeExerciseId;
    const currentSetId = activeSession?.activeSetId;
    const sessionExercises = useMemo(() => getSessionExercises(activeSession?.session), [activeSession?.session]);
    const currentExercise = sessionExercises.find(ex => ex.id === currentExerciseId);
    const postExercise = sessionExercises.find(ex => ex.id === postExerciseId) ?? null;
    const postExerciseFeedback = postExerciseId ? activeSession?.postExerciseFeedback?.[postExerciseId] : undefined;
    const hasWarmupForCurrentExercise = Array.isArray(currentExercise?.warmupSets) && currentExercise.warmupSets.length > 0;

    // Calculate completed sets count for the summary
    const completedSetsCount = activeSession ? 
        Object.values(activeSession.completedSets || {}).length : 
        0;

    // Calculate total sets in the session
    const totalSets = sessionExercises.reduce((sum, ex) => 
        sum + (ex.sets?.length ?? 0), 0) || 0;
    const totalTonnage = useMemo(() => {
        if (!activeSession) return 0;
        return calculateSessionTonnage(activeSession.completedSets);
    }, [activeSession]);
    const durationMinutes = activeSession ? Math.max(0, Math.floor((Date.now() - activeSession.startTime) / 60000)) : 0;

    // Build the program overview for the header
    const programOverview = programs.find(p => p.id === activeSession?.programId);

    const styles = useMemo(() => createStyles(colors), [colors]);
    const weightUnit = settingsSummary?.weightUnit ?? 'kg';
    const completedExerciseIds = useMemo(() => {
        const session = activeSession?.session;
        if (!sessionExercises.length) return new Set<string>();
        const result = new Set<string>();
        for (const exercise of sessionExercises) {
            const sets = exercise.sets ?? [];
            if (sets.length === 0) continue;
            const allDone = sets.every(set => Boolean(activeSession.completedSets[set.id]));
            if (allDone) result.add(exercise.id);
        }
        return result;
    }, [activeSession, sessionExercises]);

    const skippedExerciseIds = useMemo(() => {
        const session = activeSession?.session;
        if (!sessionExercises.length) return new Set<string>();
        const result = new Set<string>();
        for (const exercise of sessionExercises) {
            const sets = exercise.sets ?? [];
            if (sets.length === 0) continue;
            const allSkipped = sets.every(set => {
                const raw = activeSession.completedSets[set.id];
                if (!raw || typeof raw !== 'object' || 'left' in raw) return false;
                return raw.performanceMode === 'failed' || Boolean(raw.isIneffective);
            });
            if (allSkipped) result.add(exercise.id);
        }
        return result;
    }, [activeSession, sessionExercises]);

    const handleOpenWarmup = useCallback(() => {
        if (!currentExercise) {
            Alert.alert('Sin ejercicio activo', 'Selecciona un ejercicio para abrir sus series de aproximación.');
            return;
        }
        const dynamicWeight = activeSession?.dynamicWeights?.[currentExercise.id]?.consolidated;
        const firstSetWeight = currentExercise.sets.find(set => typeof set.weight === 'number')?.weight ?? 0;
        const initialWeight = typeof dynamicWeight === 'number' && dynamicWeight > 0 ? dynamicWeight : firstSetWeight;
        setWarmupBaseWeight(initialWeight);
        setWarmupVisible(true);
    }, [activeSession?.dynamicWeights, currentExercise]);

    const handleOpenPostExercise = useCallback((exercise: Exercise) => {
        setPostExerciseId(exercise.id);
        setPostExerciseVisible(true);
    }, []);

    const postExerciseStats = useMemo(() => {
        if (!activeSession || !postExercise) {
            return { sets: 0, reps: 0, weight: 0 };
        }
        const completedSets = (postExercise.sets ?? [])
            .map(set => activeSession.completedSets[set.id] as OngoingSetData | undefined)
            .filter((item): item is OngoingSetData => Boolean(item));
        return {
            sets: completedSets.length,
            reps: completedSets.reduce((sum, set) => sum + (set.reps ?? 0), 0),
            weight: Math.round(completedSets.reduce((sum, set) => sum + (set.weight ?? 0), 0)),
        };
    }, [activeSession, postExercise]);

    return (
        <SafeAreaView style={styles.safeArea}>
            <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} />
            <WorkoutHeader 
                sessionName={activeSession?.session.name ?? ''}
                startTime={activeSession?.startTime ?? Date.now()}
                completedSetsCount={completedSetsCount}
                totalSetsCount={totalSets}
                onFinishPress={handleFinishPress}
            />
            <CardCarouselBar
                exercises={sessionExercises}
                activeExerciseId={currentExerciseId ?? null}
                skippedIds={skippedExerciseIds}
                completedExerciseIds={completedExerciseIds}
                durationMinutes={durationMinutes}
                completedSetsCount={completedSetsCount}
                totalSetsCount={totalSets}
                totalTonnage={totalTonnage}
                onSelectExercise={(exerciseId) => {
                    const { setActiveExercise, setActiveSet } = useWorkoutStore.getState();
                    setActiveExercise(exerciseId);
                    const exercise = sessionExercises.find(item => item.id === exerciseId);
                    const firstSetId = exercise?.sets?.[0]?.id ?? null;
                    setActiveSet(firstSetId);
                }}
                onFinish={handleFinishPress}
            />
            <AugeTelemetryPanel 
                cns={0}
                muscular={0}
                spinal={0}
            />
            <ScrollView 
                contentContainerStyle={styles.scrollContent}
                showsVerticalScrollIndicator={false}
                contentInsetAdjustmentBehavior="automatic"
            >
                {/* Exercise blocks */}
                {sessionExercises.map((exercise, exIdx) => {
                    const globalIndex = sessionExercises.slice(0, exIdx).reduce((acc, ex) => acc + (ex.sets?.length ?? 0), 0);
                    return (
                        <View key={exercise.id} style={styles.exerciseBlock}>
                            <ExerciseBlockCard
                                exercises={[exercise]} // Changed to array of exercises
                                activeSession={activeSession}
                                exerciseIndex={globalIndex}
                                isFocused={currentExercise?.id === exercise.id}
                                onOpenPostExercise={handleOpenPostExercise}
                                onSelect={() => {
                                    // Set active exercise
                                    const { setActiveExercise } = useWorkoutStore.getState();
                                    setActiveExercise(exercise.id);
                                }}
                            />
                        </View>
                    );
                })}

                {/* Empty state if no exercises */}
                {!sessionExercises.length && (
                    <View style={styles.emptyState}>
                        <Text style={styles.emptyText}>No hay ejercicios en esta sesión</Text>
                    </View>
                )}
            </ScrollView>

            {/* Floating Bottom Dock */}
            <View style={styles.dockContainer}>
                <View style={StyleSheet.absoluteFill}>
                    <Canvas style={StyleSheet.absoluteFill}>
                        <Fill color="rgba(255,255,255,0.4)">
                            <Blur blur={20} />
                        </Fill>
                    </Canvas>
                </View>
                <View style={[styles.dockContent, { backgroundColor: Platform.OS === 'android' ? `${colors.surface}F2` : 'transparent' }]}>
                    <View style={styles.quickBar}>
                        {restTimerRemaining > 0 ? (
                            <TouchableOpacity style={[styles.dockPill, { backgroundColor: colors.tertiaryContainer }]} onPress={handleCancelRest}>
                                <ClockIcon size={16} color={colors.tertiary} />
                                <Text style={[styles.dockPillText, { color: colors.tertiary }]}>Cancelar Rest</Text>
                            </TouchableOpacity>
                        ) : (
                            <View style={styles.restOptions}>
                                <TouchableOpacity style={[styles.dockPill, { backgroundColor: `${colors.onSurface}08` }]} onPress={() => startRestCountdown(60)}>
                                    <ClockIcon size={14} color={colors.onSurfaceVariant} />
                                    <Text style={[styles.dockPillText, { color: colors.onSurfaceVariant }]}>60s</Text>
                                </TouchableOpacity>
                                <TouchableOpacity style={[styles.dockPill, { backgroundColor: `${colors.onSurface}08` }]} onPress={() => startRestCountdown(90)}>
                                    <ClockIcon size={14} color={colors.onSurfaceVariant} />
                                    <Text style={[styles.dockPillText, { color: colors.onSurfaceVariant }]}>90s</Text>
                                </TouchableOpacity>
                                <TouchableOpacity style={[styles.dockPill, { backgroundColor: `${colors.onSurface}08` }]} onPress={() => startRestCountdown(120)}>
                                    <ClockIcon size={14} color={colors.onSurfaceVariant} />
                                    <Text style={[styles.dockPillText, { color: colors.onSurfaceVariant }]}>2m</Text>
                                </TouchableOpacity>
                            </View>
                        )}
                        {hasWarmupForCurrentExercise ? (
                            <TouchableOpacity style={[styles.dockPill, { backgroundColor: `${colors.primary}20` }]} onPress={handleOpenWarmup}>
                                <ActivityIcon size={14} color={colors.primary} />
                                <Text style={[styles.dockPillText, { color: colors.primary }]}>Warmup</Text>
                            </TouchableOpacity>
                        ) : null}
                    </View>
                    <View style={styles.actionButtons}>
                        <Button 
                            variant="secondary" 
                            onPress={handleDiscard}
                        >
                            Descartar
                        </Button>
                        <Button 
                            variant="primary" 
                            onPress={handleFinishPress}
                        >
                            Finalizar Sesión
                        </Button>
                    </View>
                </View>
            </View>

            {/* Finish Session Modal */}
            <FinishSessionModal
                visible={isFinishModalVisible}
                onClose={() => setFinishModalVisible(false)}
                onContinue={handleFinishSession}
                summary={{
                    completedSets: completedSetsCount,
                    totalSets,
                    durationMinutes: activeSession ? Math.floor((Date.now() - activeSession.startTime) / 60000) : 0,
                    exerciseCount: sessionExercises.length,
                }}
                 initialValues={workoutLogData ?? undefined} // Pass the current workout log data as initial values
            />

            {/* Post Session Questionnaire Modal */}
            <PostSessionQuestionnaireModal
                visible={isQuestionnaireVisible}
                onClose={() => setIsQuestionnaireVisible(false)}
                onSubmit={handleSubmitFeedback}
                isSaving={loggingState === 'saving'}
            />

            <WarmupDrawer
                visible={warmupVisible}
                exercise={currentExercise ?? null}
                baseWeight={warmupBaseWeight}
                weightUnit={weightUnit}
                onBaseWeightChange={setWarmupBaseWeight}
                onClose={() => setWarmupVisible(false)}
                onComplete={() => {
                    setIsRestTimerActive(true);
                    setRestTimerRemaining(45);
                    void startRestTimer(45);
                }}
            />

            <PostExerciseDrawer
                visible={postExerciseVisible}
                exerciseName={postExercise?.name ?? 'Ejercicio'}
                stats={{
                    sets: postExerciseStats.sets,
                    reps: postExerciseStats.reps,
                    weight: postExerciseStats.weight,
                    unit: weightUnit,
                }}
                initialFeedback={postExerciseFeedback as PostExerciseFeedback | undefined}
                onClose={() => setPostExerciseVisible(false)}
                onSave={(feedback) => {
                    if (!postExercise) return;
                    savePostExerciseFeedback(postExercise.id, feedback);
                    setPostExerciseVisible(false);
                }}
            />
        </SafeAreaView>
    );
};

const createStyles = (colors: any) => StyleSheet.create({
    safeArea: {
        flex: 1,
        backgroundColor: colors.background,
    },
    scrollContent: {
        paddingBottom: 80, // Space for the dock
    },
    exerciseBlock: {
        marginHorizontal: 16,
        marginVertical: 8,
    },
    emptyState: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        padding: 24,
    },
    emptyText: {
        fontSize: 18,
        color: colors.onSurfaceVariant,
    },
    dockContainer: {
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        height: 80,
    },
    dockContent: {
        flexDirection: 'column',
        justifyContent: 'space-between',
        alignItems: 'stretch',
        padding: 12,
    },
    quickBar: {
        flexDirection: 'row',
        justifyContent: 'space-around',
        alignItems: 'center',
        paddingVertical: 6,
    },
    restOptions: {
        flexDirection: 'row',
        justifyContent: 'space-around',
    },
    dockPill: {
        paddingHorizontal: 16,
        paddingVertical: 8,
        borderRadius: 20,
        alignItems: 'center',
    },
    dockPillText: {
        fontSize: 12,
        fontWeight: '600',
    },
    actionButtons: {
        flexDirection: 'row',
        justifyContent: 'space-around',
    },
    // Modal styles are now defined in the respective modal components
});
