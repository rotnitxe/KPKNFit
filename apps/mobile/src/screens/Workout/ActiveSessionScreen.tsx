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
    StatusBar
} from 'react-native';

import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WorkoutHeader } from '../../components/workout/WorkoutHeader';
import { AugeTelemetryPanel } from '../../components/workout/AugeTelemetryPanel';
import { ExerciseBlockCard } from '../../components/workout/ExerciseBlockCard';
import { FinishSessionModal } from '../../components/workout/FinishSessionModal';
import { PostSessionQuestionnaireModal } from '../../components/workout/PostSessionQuestionnaireModal';
import { CheckCircleIcon, ClockIcon, PencilIcon, ActivityIcon, ArrowDownIcon, ChevronRightIcon } from '../../components/icons';
import { WorkoutStackParamList } from '../../navigation/types';
import { useProgramStore } from '../../stores/programStore';
import { PostSessionFeedbackInput, useWorkoutStore } from '../../stores/workoutStore';
import { calculateCompletedSessionDrainBreakdown } from '../../services/fatigueService';
import { useExerciseStore } from '../../stores/exerciseStore';
import { CompletedExercise, CompletedSet, OngoingSetData } from '../../types/workout';
import { useColors, useTheme } from '../../theme';
import { LiquidGlassCard } from '../../components/ui/LiquidGlassCard';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';
import { Canvas, Rect, LinearGradient, vec, Blur, Fill } from '@shopify/react-native-skia';

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
    setActiveExercise,
    setActiveSet,
  } = useWorkoutStore();

  const [isFinishModalVisible, setFinishModalVisible] = useState(false);
  const [isQuestionnaireVisible, setQuestionnaireVisible] = useState(false);
  const [isNotesVisible, setNotesVisible] = useState(false);
  const [sessionNotes, setSessionNotes] = useState('');
  const [restTimerRemaining, setRestTimerRemaining] = useState(0);
  const restIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const scrollRef = useRef<ScrollView | null>(null);
  const cardOffsetsRef = useRef<Record<string, number>>({});

  const sessionData = useMemo(() => {
    const program = programs.find(p => p.id === programId);
    if (!program) return null;

    for (const macro of program.macrocycles || []) {
      for (const block of macro.blocks || []) {
        for (const meso of block.mesocycles || []) {
          for (const week of meso.weeks || []) {
            const session = week.sessions?.find(s => s.id === sessionId);
            if (session) return session;
          }
        }
      }
    }
    return null;
  }, [programId, programs, sessionId]);

  useEffect(() => {
    if (sessionData && !activeSession) {
      startActiveSession({ programId, session: sessionData });
    }
  }, [activeSession, programId, sessionData, startActiveSession]);

  useEffect(() => () => {
    if (restIntervalRef.current) {
      clearInterval(restIntervalRef.current);
      restIntervalRef.current = null;
    }
  }, []);

  const currentSession = activeSession?.session;

  const totalSets = useMemo(
    () => currentSession?.exercises.reduce((acc, ex) => acc + (ex.sets?.length || 0), 0) || 0,
    [currentSession],
  );

  const completedSetsCount = useMemo(() => {
    if (!activeSession) return 0;
    return Object.keys(activeSession.completedSets).length;
  }, [activeSession]);

  const currentExercise = useMemo(() => {
    if (!currentSession) return null;
    return currentSession.exercises.find(ex => ex.id === activeSession?.activeExerciseId) || currentSession.exercises[0] || null;
  }, [activeSession?.activeExerciseId, currentSession]);

  const exerciseList = useExerciseStore(state => state.exerciseList);

  const telemetry = useMemo(() => {
    if (!activeSession) return { cns: 0, muscular: 0, spinal: 0 };

    const completedExercises: CompletedExercise[] = activeSession.session.exercises.map(ex => {
      const sets: CompletedSet[] = ex.sets
        .filter(s => activeSession.completedSets[s.id])
        .map(s => {
          const data = activeSession.completedSets[s.id];
          if (data && 'left' in data) {
            const primary = data.left || data.right;
            return {
              ...s,
              weight: primary?.weight || 0,
              completedReps: primary?.reps || 0,
              completedRPE: primary?.rpe,
              completedRIR: primary?.rir,
              isFailure: primary?.isFailure,
            };
          }
          const d = data as OngoingSetData;
          return {
            ...s,
            weight: d?.weight || 0,
            completedReps: d?.reps || 0,
            completedRPE: d?.rpe,
            completedRIR: d?.rir,
            isFailure: d?.isFailure,
          };
        });
      return {
        exerciseId: ex.id,
        exerciseDbId: ex.exerciseDbId,
        exerciseName: ex.name,
        sets,
      };
    }).filter(ex => ex.sets.length > 0);

    const breakdown = calculateCompletedSessionDrainBreakdown(completedExercises, exerciseList);

    return {
      cns: Math.min(100, breakdown.cnsDrain),
      muscular: Math.min(100, breakdown.muscularDrain),
      spinal: Math.min(100, breakdown.spinalDrain),
    };
  }, [activeSession, exerciseList]);

  const clearRestInterval = useCallback(() => {
    if (restIntervalRef.current) {
      clearInterval(restIntervalRef.current);
      restIntervalRef.current = null;
    }
  }, []);

  const startRestCountdown = useCallback(async (seconds: number) => {
    clearRestInterval();
    setRestTimerRemaining(seconds);
    ReactNativeHapticFeedback.trigger('impactLight');
    restIntervalRef.current = setInterval(() => {
      setRestTimerRemaining(prev => {
        if (prev <= 1) {
          clearRestInterval();
          ReactNativeHapticFeedback.trigger('notificationSuccess');
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    try {
      await startRestTimer(seconds);
    } catch (error) {
      console.warn('Rest timer error:', error);
    }
  }, [clearRestInterval, startRestTimer]);

  const handleCancelRest = useCallback(async () => {
    clearRestInterval();
    setRestTimerRemaining(0);
    ReactNativeHapticFeedback.trigger('impactMedium');
    try {
      await cancelRestTimer();
    } catch (error) {
      console.warn('Cancel rest error:', error);
    }
  }, [cancelRestTimer, clearRestInterval]);

  const focusExercise = useCallback((exerciseId: string) => {
    if (!currentSession) return;
    const exercise = currentSession.exercises.find(item => item.id === exerciseId);
    setActiveExercise(exerciseId);
    setActiveSet(exercise?.sets?.[0]?.id ?? null);
    ReactNativeHapticFeedback.trigger('selection');
    const y = cardOffsetsRef.current[exerciseId];
    if (typeof y === 'number') {
      scrollRef.current?.scrollTo({ y: Math.max(0, y - 100), animated: true });
    }
  }, [currentSession, setActiveExercise, setActiveSet]);

  const handleFinishAttempt = useCallback(() => {
    ReactNativeHapticFeedback.trigger('impactHeavy');
    setFinishModalVisible(true);
  }, []);

  const handleContinueToQuestionnaire = useCallback(() => {
    setFinishModalVisible(false);
    setQuestionnaireVisible(true);
  }, []);

  const handleFinalSave = useCallback(async (feedback: PostSessionFeedbackInput) => {
    try {
      const mergedNotes = [sessionNotes.trim(), feedback.notes.trim()].filter(Boolean).join('\n\n');
      await finishActiveSession({ ...feedback, notes: mergedNotes });
      setQuestionnaireVisible(false);
      navigation.goBack();
    } catch (error) {
      Alert.alert('Error', 'Failed to save session.');
    }
  }, [finishActiveSession, navigation, sessionNotes]);

  const handleDiscard = useCallback(() => {
    Alert.alert(
      'Abandonar sesión',
      '¿Estás seguro? Todo el progreso no guardado se perderá.',
      [
        { text: 'Continuar entrenando', style: 'cancel' },
        {
          text: 'Descartar todo',
          style: 'destructive',
          onPress: () => {
            discardActiveSession();
            navigation.goBack();
          },
        },
      ],
    );
  }, [discardActiveSession, navigation]);

  if (!currentSession || !activeSession) {
    return <View style={[styles.loadingRoot, { backgroundColor: colors.background }]} />;
  }

  return (
    <View style={[styles.root, { backgroundColor: colors.background }]}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} />
      
      <SafeAreaView style={styles.safeArea} edges={['top', 'left', 'right']}>
        <WorkoutHeader
          sessionName={sessionName}
          startTime={activeSession.startTime}
          completedSetsCount={completedSetsCount}
          totalSetsCount={totalSets}
          onFinishPress={handleFinishAttempt}
          activePartName={currentExercise?.name}
          sessionFocus={currentSession.focus ?? currentSession.description}
          isResting={restTimerRemaining > 0}
          restTimerRemaining={restTimerRemaining}
        />

        <AugeTelemetryPanel 
            cns={telemetry.cns} 
            muscular={telemetry.muscular} 
            spinal={telemetry.spinal} 
        />

        <ScrollView
          ref={scrollRef}
          style={styles.scroll}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.sessionMeta}>
            <LiquidGlassCard style={styles.summaryCard}>
               <View style={styles.summaryContent}>
                 <View style={styles.summaryBadge}>
                    <Text style={[styles.summaryBadgeText, { color: colors.primary }]}>SESSION ROADMAP</Text>
                 </View>
                 <Text style={[styles.summaryDescription, { color: colors.onSurfaceVariant }]}>
                   {currentSession.description || 'Registra cada serie. La intensidad y recovery se calculan en tiempo real.'}
                 </Text>
               </View>
            </LiquidGlassCard>
          </View>

          {currentSession.exercises.map((exercise, index) => (
            <View
              key={exercise.id}
              onLayout={(event) => {
                cardOffsetsRef.current[exercise.id] = event.nativeEvent.layout.y;
              }}
            >
              <ExerciseBlockCard
                exercise={exercise}
                activeSession={activeSession}
                exerciseIndex={index}
                isFocused={currentExercise?.id === exercise.id}
                onSelect={() => focusExercise(exercise.id)}
              />
            </View>
          ))}

          <TouchableOpacity style={styles.discardBtn} onPress={handleDiscard}>
            <Text style={[styles.discardBtnText, { color: colors.error }]}>Descartar Sesión</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>

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
                    <Text style={[styles.dockPillText, { color: colors.tertiary }]}>Cancel Rest</Text>
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
                 </View>
               )}
               
               <TouchableOpacity 
                 style={[styles.dockPill, { backgroundColor: `${colors.primary}10` }]} 
                 onPress={() => setNotesVisible(true)}
               >
                 <PencilIcon size={14} color={colors.primary} />
                 <Text style={[styles.dockPillText, { color: colors.primary }]}>Notas</Text>
               </TouchableOpacity>
            </View>

            <ScrollView 
                horizontal 
                showsHorizontalScrollIndicator={false} 
                contentContainerStyle={styles.exerciseNavRow}
            >
                {currentSession.exercises.map((ex, idx) => {
                    const isActive = currentExercise?.id === ex.id;
                    const completed = ex.sets.filter(s => activeSession.completedSets[s.id]).length;
                    return (
                        <TouchableOpacity 
                            key={ex.id} 
                            onPress={() => focusExercise(ex.id)}
                            style={[
                                styles.navPill, 
                                isActive && { backgroundColor: colors.primary, borderColor: colors.primary }
                            ]}
                        >
                            <Text style={[styles.navIndex, isActive && { color: colors.onPrimary }]}>{idx + 1}</Text>
                            <View>
                                <Text style={[styles.navName, isActive && { color: colors.onPrimary }]} numberOfLines={1}>
                                    {ex.name}
                                </Text>
                                <Text style={[styles.navMeta, isActive && { color: `${colors.onPrimary}A0` }]}>
                                    {completed}/{ex.sets.length}
                                </Text>
                            </View>
                        </TouchableOpacity>
                    );
                })}
                <TouchableOpacity style={[styles.navFinishPill, { backgroundColor: colors.primary }]} onPress={handleFinishAttempt}>
                    <CheckCircleIcon size={18} color="#FFF" />
                    <Text style={styles.navFinishText}>Finalizar</Text>
                </TouchableOpacity>
            </ScrollView>
        </View>
      </View>

      <FinishSessionModal
        visible={isFinishModalVisible}
        onClose={() => setFinishModalVisible(false)}
        onContinue={handleContinueToQuestionnaire}
        summary={{
          completedSets: completedSetsCount,
          totalSets,
          durationMinutes: Math.floor((Date.now() - activeSession.startTime) / 60000),
          exerciseCount: currentSession.exercises.length,
        }}
      />

      <PostSessionQuestionnaireModal
        visible={isQuestionnaireVisible}
        onClose={() => setQuestionnaireVisible(false)}
        onSubmit={handleFinalSave}
        isSaving={sessionFinishState === 'saving'}
      />

      <Modal transparent visible={isNotesVisible} animationType="fade" onRequestClose={() => setNotesVisible(false)}>
        <View style={styles.notesOverlay}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setNotesVisible(false)} />
          <LiquidGlassCard style={styles.notesCard}>
            <Text style={[styles.notesTitle, { color: colors.onSurface }]}>Notas de Sesión</Text>
            <Text style={[styles.notesSubtitle, { color: colors.onSurfaceVariant }]}>Se adjuntarán al feedback final.</Text>
            <TextInput
              value={sessionNotes}
              onChangeText={setSessionNotes}
              multiline
              placeholder="Sensaciones, ajustes o imprevistos..."
              placeholderTextColor={colors.onSurfaceVariant}
              style={[styles.notesInput, { color: colors.onSurface, borderColor: colors.outlineVariant, backgroundColor: `${colors.onSurface}05` }]}
            />
            <View style={styles.notesActions}>
              <TouchableOpacity style={[styles.notesBtnSec, { borderColor: colors.outlineVariant }]} onPress={() => setNotesVisible(false)}>
                <Text style={[styles.notesBtnTextSec, { color: colors.onSurfaceVariant }]}>Cerrar</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.notesBtnPri, { backgroundColor: colors.primary }]} onPress={() => setNotesVisible(false)}>
                <Text style={[styles.notesBtnTextPri, { color: colors.onPrimary }]}>Guardar</Text>
              </TouchableOpacity>
            </View>
          </LiquidGlassCard>
        </View>
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  loadingRoot: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    paddingBottom: 220,
  },
  sessionMeta: {
    paddingHorizontal: 16,
    marginBottom: 20,
  },
  summaryCard: {
    padding: 16,
  },
  summaryContent: {
    gap: 8,
  },
  summaryBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    backgroundColor: 'rgba(0,0,0,0.05)',
  },
  summaryBadgeText: {
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1.2,
  },
  summaryDescription: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
    opacity: 0.8,
  },
  discardBtn: {
    marginTop: 12,
    marginHorizontal: 16,
    paddingVertical: 14,
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: 'rgba(255,0,0,0.1)',
    alignItems: 'center',
    backgroundColor: 'rgba(255,0,0,0.03)',
  },
  discardBtnText: {
    fontSize: 12,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
  },
  dockContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    overflow: 'hidden',
    borderTopWidth: 1,
    borderTopColor: 'rgba(255,255,255,0.2)',
    elevation: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -10 },
    shadowOpacity: 0.1,
    shadowRadius: 20,
  },
  dockContent: {
    paddingTop: 16,
    paddingBottom: Platform.OS === 'ios' ? 40 : 16,
    paddingHorizontal: 16,
  },
  quickBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
    gap: 12,
  },
  restOptions: {
    flexDirection: 'row',
    gap: 8,
  },
  dockPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 20,
    minWidth: 80,
    justifyContent: 'center',
  },
  dockPillText: {
    fontSize: 12,
    fontWeight: '800',
  },
  exerciseNavRow: {
    gap: 10,
    alignItems: 'center',
    paddingRight: 20,
  },
  navPill: {
    minWidth: 120,
    maxWidth: 180,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 22,
    backgroundColor: 'rgba(0,0,0,0.04)',
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.05)',
  },
  navName: {
    fontSize: 13,
    fontWeight: '800',
    maxWidth: 110,
  },
  navMeta: {
    fontSize: 10,
    fontWeight: '700',
    opacity: 0.6,
  },
  navIndex: {
    fontSize: 12,
    fontWeight: '900',
    opacity: 0.5,
  },
  navFinishPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderRadius: 24,
  },
  navFinishText: {
    color: '#FFF',
    fontSize: 13,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  notesOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.2)',
    justifyContent: 'center',
    padding: 24,
  },
  notesCard: {
    padding: 24,
  },
  notesTitle: {
    fontSize: 22,
    fontWeight: '900',
    marginBottom: 4,
  },
  notesSubtitle: {
    fontSize: 13,
    fontWeight: '500',
    marginBottom: 20,
    opacity: 0.7,
  },
  notesInput: {
    height: 140,
    borderRadius: 20,
    borderWidth: 1.5,
    padding: 16,
    fontSize: 15,
    textAlignVertical: 'top',
    fontWeight: '500',
  },
  notesActions: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 20,
  },
  notesBtnSec: {
    flex: 1,
    height: 52,
    borderRadius: 16,
    borderWidth: 1.5,
    justifyContent: 'center',
    alignItems: 'center',
  },
  notesBtnTextSec: {
    fontWeight: '800',
    fontSize: 14,
  },
  notesBtnPri: {
    flex: 1,
    height: 52,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
  },
  notesBtnTextPri: {
    fontWeight: '800',
    fontSize: 14,
  },
});
