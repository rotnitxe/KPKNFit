// App.tsx
import { GlobalVoiceAssistant } from './components/GlobalVoiceAssistant';
import React, { useState, useEffect, useCallback, useMemo, useRef, Suspense } from 'react';
import { useAppState, useAppDispatch } from './contexts/AppContext';
import { View, Session, OngoingWorkoutState, Program, TabBarActions, WorkoutLog, SleepLog } from './types';
import useAchievements from './hooks/useAchievements';
import { pSBC } from './utils/colorUtils';
import useLocalStorage from './hooks/useLocalStorage';
import { setupNotificationChannels, rescheduleAllNotifications } from './services/notificationService';
import { syncStatusBarWithTheme } from './services/statusBarService';
import { Capacitor } from '@capacitor/core';
import { App as CapApp } from '@capacitor/app';
import SpecialSessionLoggerModal from './components/SpecialSessionLoggerModal';
import { PCEActionModal } from './components/PCEActionModal';

// Import UI Components
import TabBar from './components/TabBar';
import SubTabBar from './components/SubTabBar';
import AddNutritionLogModal from './components/AddNutritionLogModal';
import CoachChatModal from './components/CoachChatModal';
import AppBackground from './components/AppBackground';
import TimersModal from './components/TimersModal';
import Toast from './components/ui/Toast';
import CustomExerciseEditorModal from './components/CustomExerciseEditorModal';
import VideoAnalysisModal from './components/VideoAnalysisModal';
import ReadinessDrawer from './components/ReadinessDrawer';
import AddToPlaylistSheet from './components/AddToPlaylistSheet';
import StartWorkoutDrawer from './components/StartWorkoutDrawer';
import CoachBriefingDrawer from './components/CoachBriefingDrawer';
import AddBodyLogModal from './components/AddBodyLogModal';
import LogActionSheet from './components/LogActionSheet';
import FoodEditorModal from './components/FoodEditorModal';
import AddPantryItemModal from './components/AddPantryItemModal';
import { SleepWidgetHeader } from './components/SleepWidgetHeader';
import { PostSessionQuestionnaireWidget } from './components/PostSessionQuestionnaireWidget';
import AthleteIDDashboard from './components/AthleteIDDashboard';
import { UpdateNoveltiesModal } from './components/UpdateNoveltiesModal';
import { GeneralOnboardingWizard } from './components/onboarding/GeneralOnboardingWizard';
import { WelcomeWizard } from './components/onboarding/WelcomeWizard';

// Icons for header & new menu
import { ArrowLeftIcon, IdCardIcon } from './components/icons';

// --- Static Imports (vistas de arranque frecuente) ---
import ErrorBoundary from './components/ui/ErrorBoundary';
import Home from './components/Home';
import ProgramsView from './components/ProgramsView';
import MuscleListEditorModal from './components/MuscleListEditorModal';
import EditSleepLogModal from './components/EditSleepLogModal';

// --- Lazy imports (code splitting) ---
const ProgramDetail = React.lazy(() => import('./components/ProgramDetail'));
const ProgramEditor = React.lazy(() => import('./components/ProgramEditor'));
const SessionEditor = React.lazy(() => import('./components/SessionEditor').then(m => ({ default: m.SessionEditor })));
const WorkoutSession = React.lazy(() => import('./components/WorkoutSession').then(m => ({ default: m.WorkoutSession })));
const SettingsComponent = React.lazy(() => import('./components/SettingsComponent'));
const CoachView = React.lazy(() => import('./components/CoachView'));
const PhysicalProgress = React.lazy(() => import('./components/PhysicalProgress'));
const LogHub = React.lazy(() => import('./components/LogHub'));
const AchievementsView = React.lazy(() => import('./components/AchievementsView'));
const LogWorkoutView = React.lazy(() => import('./components/LogWorkoutView'));
const KPKNView = React.lazy(() => import('./components/KPKNView'));
const ExerciseDetailView = React.lazy(() => import('./components/ExerciseDetailView').then(m => ({ default: m.ExerciseDetailView })));
const MuscleGroupDetailView = React.lazy(() => import('./components/MuscleGroupDetailView'));
const BodyPartDetailView = React.lazy(() => import('./components/BodyPartDetailView'));
const MuscleCategoryView = React.lazy(() => import('./components/MuscleCategoryView'));
const ChainDetailView = React.lazy(() => import('./components/ChainDetailView'));
const JointDetailView = React.lazy(() => import('./components/JointDetailView'));
const TendonDetailView = React.lazy(() => import('./components/TendonDetailView'));
const MovementPatternDetailView = React.lazy(() => import('./components/MovementPatternDetailView'));
const BodyLabView = React.lazy(() => import('./components/BodyLabView'));
const TrainingPurposeView = React.lazy(() => import('./components/TrainingPurposeView'));
const ExerciseDatabaseView = React.lazy(() => import('./components/ExerciseDatabaseView'));
const TasksView = React.lazy(() => import('./components/TasksView'));
const SessionDetailView = React.lazy(() => import('./components/SessionDetailView'));
const SmartMealPlannerView = React.lazy(() => import('./components/SmartMealPlannerView'));
const NutritionView = React.lazy(() => import('./components/NutritionView'));
const FoodDatabaseView = React.lazy(() => import('./components/FoodDatabaseView'));
const BodyProgressView = React.lazy(() => import('./components/BodyProgressView'));
const ProgramMetricVolumeDetail = React.lazy(() => import('./components/program-detail/metrics/ProgramMetricVolumeDetail'));
const ProgramMetricStrengthDetail = React.lazy(() => import('./components/program-detail/metrics/ProgramMetricStrengthDetail'));
const ProgramMetricDensityDetail = React.lazy(() => import('./components/program-detail/metrics/ProgramMetricDensityDetail'));
const ProgramMetricFrequencyDetail = React.lazy(() => import('./components/program-detail/metrics/ProgramMetricFrequencyDetail'));
const ProgramMetricBanisterDetail = React.lazy(() => import('./components/program-detail/metrics/ProgramMetricBanisterDetail'));
const ProgramMetricRecoveryDetail = React.lazy(() => import('./components/program-detail/metrics/ProgramMetricRecoveryDetail'));
const ProgramMetricAdherenceDetail = React.lazy(() => import('./components/program-detail/metrics/ProgramMetricAdherenceDetail'));
const ProgramMetricRPEDetail = React.lazy(() => import('./components/program-detail/metrics/ProgramMetricRPEDetail'));
import { getCachedAdaptiveData } from './services/augeAdaptiveService';
import { useProgramStore } from './stores/programStore';
import { useSettingsStore } from './stores/settingsStore';
import { useWorkoutStore } from './stores/workoutStore';
import { useNutritionStore } from './stores/nutritionStore';

// Components formerly widgets, now views
import SleepTrackerWidget from './components/SleepTrackerWidget';
import MuscleRecoveryWidget from './components/MuscleRecoveryWidget';

const MobilityLabView = React.lazy(() => import('./components/MobilityLabView'));
const AIArtStudioView = React.lazy(() => import('./components/AIArtStudioView'));

const ViewFallback: React.FC = () => (
    <div className="flex flex-col items-center justify-center min-h-[40vh] p-8 text-center">
        <div className="w-10 h-10 border-2 border-slate-500 border-t-white rounded-full animate-spin mb-4" />
        <p className="text-zinc-400 text-sm">Cargando...</p>
    </div>
);

const WorkoutViewFallback: React.FC<{ onFallback: () => void }> = ({ onFallback }) => {
    const hasRun = React.useRef(false);
    React.useEffect(() => {
        if (!hasRun.current) {
            hasRun.current = true;
            onFallback();
        }
    }, [onFallback]);
    return (
        <div className="flex flex-col items-center justify-center min-h-[40vh] p-8 text-center">
            <p className="text-zinc-400 text-sm mb-2">No se pudo cargar la sesión.</p>
            <p className="text-zinc-500 text-xs">Redirigiendo al inicio...</p>
        </div>
    );
};

export const App: React.FC = () => {
    const state = useAppState();
    const dispatch = useAppDispatch();
    
    const {
        view,
        activeProgramId,
        editingProgramId,
        editingSessionInfo,
        activeSession,
        loggingSessionInfo,
        viewingSessionInfo,
        viewingExerciseId,
        viewingMuscleGroupId,
        viewingJointId,
        viewingTendonId,
        viewingMovementPatternId,
        viewingBodyPartId,
        viewingChainId,
        viewingMuscleCategoryName,
        settings,
        programs,
        activeProgramState,
        history,
        unlockedAchievements,
        skippedLogs,
        bodyProgress,
        nutritionLogs,
        exerciseList,
        isOnline,
        isBodyLogModalOpen,
        isNutritionLogModalOpen,
        isFinishModalOpen,
        isTimeSaverModalOpen,
        isTimersModalOpen,
        isReadinessModalOpen,
        isAppLoading,
        installPromptEvent,
        toasts,
        saveSessionTrigger,
        addExerciseTrigger,
        saveProgramTrigger,
        saveLoggedWorkoutTrigger,
        drive,
        ongoingWorkout,
        isCustomExerciseEditorOpen,
        editingCustomExerciseData,
        isFoodEditorOpen,
        isStartWorkoutModalOpen,
        editingWorkoutSessionInfo,
        isMuscleListEditorOpen,
        muscleHierarchy,
        pendingCoachBriefing,
        sleepLogs,
        isMenuOpen,
        pendingWorkoutForReadinessCheck,
        historyStack
    } = state;
    
    const {
        navigateTo,
        setIsBodyLogModalOpen,
        setIsNutritionLogModalOpen,
        setIsFinishModalOpen,
        setIsTimeSaverModalOpen,
        setIsTimersModalOpen,
        setIsReadinessModalOpen,
        setIsStartWorkoutModalOpen,
        handleLogPress,
        handleSelectProgram,
        handleSaveProgram,
        handleDeleteProgram,
        handleAddSession,
        handleEditSession,
        handleSaveSession,
        handleDeleteSession,
        handleResumeWorkout,
        handleLogWorkout,
        handleSkipWorkout,
        handleFinishWorkout,
        handlePauseWorkout,
        handleSaveLoggedWorkout,
        handleUpdateExerciseInProgram,
        onCancelWorkout,
        handleUpdateExercise1RM,
        handleBack,
        setPrograms,
        setHistory,
        setSkippedLogs,
        setBodyProgress,
        setNutritionLogs,
        setInstallPromptEvent,
        setSettings,
        handleSaveNutritionLog,
        handleSaveBodyLog,
        handleCreateProgram,
        handleEditProgram,
        handleUpdateProgram,
        removeToast,
        addToast,
        setSaveSessionTrigger,
        setAddExerciseTrigger,
        setSaveProgramTrigger,
        setSaveLoggedWorkoutTrigger,
        setOngoingWorkout,
        addOrUpdateCustomExercise,
        setIsAddToPlaylistSheetOpen,
        setExerciseToAddId,
        openCustomExerciseEditor,
        closeCustomExerciseEditor,
        handleModifyWorkout,
        setIsWorkoutEditorOpen,
        handleUpdateSessionInProgram,
        setPendingCoachBriefing,
        handleContinueWorkoutAfterBriefing,
        setPendingWorkoutForReadinessCheck,
        handleLogSleep,
        setSleepLogs,
        setIsMenuOpen,
        handleContinueFromReadiness,
        handleStartProgram
    } = dispatch;

    const [isCoachChatOpen, setIsCoachChatOpen] = useState(false);
    const [coachChatContext, setCoachChatContext] = useState<Session | OngoingWorkoutState | undefined>(undefined);
    const [isLogFinishModalOpen, setIsLogFinishModalOpen] = useState(false);
    const [workoutViewMode, setWorkoutViewMode] = useState<'carousel' | 'list' | undefined>(undefined);
    useEffect(() => { if (view !== 'workout') setWorkoutViewMode(undefined); }, [view]);
    const [isVideoAnalysisModalOpen, setIsVideoAnalysisModalOpen] = useState(false);
    const [editingSleepLog, setEditingSleepLog] = useState<any | null>(null);

    const handleSaveSleepLog = useCallback((log: SleepLog) => {
        setSleepLogs(prev => prev.map(l => l.id === log.id ? log : l));
        setEditingSleepLog(null);
    }, [setSleepLogs]);

    // --- LÓGICA DE PANTALLA SIEMPRE ENCENDIDA (WAKE LOCK) ---
    const wakeLockRef = useRef<any>(null);

    const requestWakeLock = useCallback(async () => {
        if ('wakeLock' in navigator) {
            try {
                // Solicita el bloqueo de pantalla
                wakeLockRef.current = await (navigator as any).wakeLock.request('screen');
                console.log('Wake Lock activado: La pantalla no se apagará.');

                // Si por alguna razón el sistema libera el bloqueo (ej. batería baja)
                wakeLockRef.current.addEventListener('release', () => {
                    console.log('Wake Lock liberado por el sistema.');
                    wakeLockRef.current = null;
                });
            } catch (err: any) {
                console.error(`Error al solicitar Wake Lock: ${err.name}, ${err.message}`);
            }
        } else {
             console.warn('La API de Wake Lock no es compatible con este navegador.');
        }
    }, []);

    useEffect(() => {
        // Solicitar el bloqueo cuando la app inicia
        requestWakeLock();

        // Reactivar el bloqueo si el usuario sale y vuelve a entrar a la app (visibilidad)
        const handleVisibilityChange = () => {
            if (document.visibilityState === 'visible' && wakeLockRef.current === null) {
                requestWakeLock();
            }
        };

        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            document.removeEventListener('visibilitychange', handleVisibilityChange);
            // Liberar el bloqueo si el componente se desmonta
            if (wakeLockRef.current) {
                wakeLockRef.current.release()
                    .then(() => { wakeLockRef.current = null; });
            }
        };
    }, [requestWakeLock]);
    // --- FIN LÓGICA WAKE LOCK ---

    // --- VIP PASS PARA IOS (PROMPT DE INSTALACIÓN NATIVO) ---
    const [showIOSPrompt, setShowIOSPrompt] = useState(false);
    
    useEffect(() => {
        const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !(window as any).MSStream;
        // Detecta Safari en iOS estrictamente
        const isSafari = navigator.vendor && navigator.vendor.indexOf('Apple') > -1 && navigator.userAgent && navigator.userAgent.indexOf('CriOS') === -1 && navigator.userAgent.indexOf('FxiOS') === -1;
        // Detecta si ya está instalada (Standalone mode)
        const isStandalone = window.matchMedia('(display-mode: standalone)').matches || (navigator as any).standalone;
        
        // Si es iPhone, usa Safari, no está instalada y no la hemos ocultado previamente
        if (isIOS && isSafari && !isStandalone && !localStorage.getItem('kpkn_ios_prompt_hidden')) {
            setShowIOSPrompt(true);
        }
    }, []);
    // --- FIN VIP PASS ---

    // --- SISTEMA DE VERSIONES Y ESCUDO ANTI-CACHÉ ---
    const APP_VERSION = "1.4.0";
    const [showUpdateModal, setShowUpdateModal] = useState(false);

    useEffect(() => {
        const storedVersion = localStorage.getItem('kpkn_version');
        if (storedVersion && storedVersion !== APP_VERSION) {
            if ('caches' in window) {
                caches.keys().then(names => {
                    for (let name of names) caches.delete(name);
                });
            }
            setShowUpdateModal(true);
        }
        localStorage.setItem('kpkn_version', APP_VERSION);
    }, []);
    // --- FIN SISTEMA DE VERSIONES ---

    // --- CAJA NEGRA: Captura errores globales que ErrorBoundary no atrapa (async, event handlers) ---
    useEffect(() => {
        const handleGlobalError = (event: ErrorEvent) => {
            const msg = `Error: ${event.message} (${event.filename}:${event.lineno})`;
            console.error('KPKN Global Error:', msg);
            addToast(msg, 'danger', 'Error del Sistema', 8000);
        };
        const handleUnhandledRejection = (event: PromiseRejectionEvent) => {
            const reason = event.reason?.message || event.reason?.toString() || 'Error desconocido';
            console.error('KPKN Unhandled Rejection:', reason);
            addToast(`Async: ${reason}`, 'danger', 'Error del Sistema', 8000);
        };
        window.addEventListener('error', handleGlobalError);
        window.addEventListener('unhandledrejection', handleUnhandledRejection);
        return () => {
            window.removeEventListener('error', handleGlobalError);
            window.removeEventListener('unhandledrejection', handleUnhandledRejection);
        };
    }, [addToast]);
    // --- FIN CAJA NEGRA ---

    // --- Notificaciones Android: reprogramar al cargar y al volver a primer plano ---
    useEffect(() => {
        if (!Capacitor.isNativePlatform()) return;
        setupNotificationChannels().then(() => {
            rescheduleAllNotifications({
                programs,
                activeProgramId: activeProgramId ?? null,
                activeProgramState: activeProgramState ?? null,
                settings,
                history,
                nutritionLogs,
                adaptiveCache: getCachedAdaptiveData(),
            });
        });
    }, [programs, activeProgramId, activeProgramState, settings, history, nutritionLogs]);

    useEffect(() => {
        syncStatusBarWithTheme(settings);
    }, [settings.appTheme]);

    useEffect(() => {
        if (!Capacitor.isNativePlatform()) return;
        const listener = CapApp.addListener('appStateChange', ({ isActive }) => {
            if (!isActive) return;
            rescheduleAllNotifications({
                programs: useProgramStore.getState().programs,
                activeProgramId: useProgramStore.getState().activeProgramState?.programId ?? null,
                activeProgramState: useProgramStore.getState().activeProgramState ?? null,
                settings: useSettingsStore.getState().settings,
                history: useWorkoutStore.getState().history,
                nutritionLogs: useNutritionStore.getState().nutritionLogs,
                adaptiveCache: getCachedAdaptiveData(),
            });
        });
        return () => { listener.then(l => l.remove()); };
    }, []);

    const isTopLevelView = useMemo(() => ['home', 'nutrition', 'recovery', 'sleep', 'kpkn', 'progress', 'tasks'].includes(view), [view]);
    
    const onMenuClick = useCallback(() => setIsMenuOpen(prev => !prev), [setIsMenuOpen]);
    const viewingExercise = useMemo(() => viewingExerciseId ? exerciseList.find(e => e.id === viewingExerciseId) : null, [exerciseList, viewingExerciseId]);
    const onFinishWorkoutPress = useCallback(() => setIsFinishModalOpen(true), [setIsFinishModalOpen]);
    const onTimeSaverPress = useCallback(() => setIsTimeSaverModalOpen(true), [setIsTimeSaverModalOpen]);
    const onSaveSessionPress = useCallback(() => setSaveSessionTrigger(c => c + 1), [setSaveSessionTrigger]);
    const onAddExercisePress = useCallback(() => setAddExerciseTrigger(c => c + 1), [setAddExerciseTrigger]);
    const onSaveProgramPress = useCallback(() => setSaveProgramTrigger(c => c + 1), [setSaveProgramTrigger]);
    const onSaveLoggedWorkoutPress = useCallback(() => setSaveLoggedWorkoutTrigger(c => c + 1), [setSaveLoggedWorkoutTrigger]);
    const onTimersPress = useCallback(() => setIsTimersModalOpen(true), [setIsTimersModalOpen]);
    const onModifyPress = useCallback(() => handleModifyWorkout(), [handleModifyWorkout]);
    const onAddCustomExercisePress = useCallback(() => openCustomExerciseEditor(), [openCustomExerciseEditor]);
    
    const onCoachPress = useCallback(() => {
        if (view === 'workout' && ongoingWorkout) setCoachChatContext(ongoingWorkout);
        else setCoachChatContext(undefined);
        setIsCoachChatOpen(true);
    }, [view, ongoingWorkout]);

    const onPauseWorkoutPress = useCallback(() => handlePauseWorkout(), [handlePauseWorkout]);
    const onEditExercisePress = useCallback(() => { if (viewingExercise) openCustomExerciseEditor({ exercise: viewingExercise }); }, [viewingExercise, openCustomExerciseEditor]);
    const onAnalyzeTechniquePress = useCallback(() => setIsVideoAnalysisModalOpen(true), []);
    const onAddToPlaylistPress = useCallback(() => { if (viewingExerciseId) { setExerciseToAddId(viewingExerciseId); setIsAddToPlaylistSheetOpen(true); } }, [viewingExerciseId, setExerciseToAddId, setIsAddToPlaylistSheetOpen]);

    const handleHomeNavigation = (view: View, program?: Program) => {
        if (view === 'program-detail' && program) navigateTo(view, { programId: program.id });
        else navigateTo(view);
    };

    const handleStartWorkout = useCallback((session: Session, program: Program, weekVariant?: 'A' | 'B' | 'C' | 'D', location?: { macroIndex: number, mesoIndex: number, weekId: string }) => {
        dispatch.handleStartWorkout(session, program, weekVariant, location);
    }, [dispatch]);

    const tabBarActions: TabBarActions = useMemo(() => ({
        onLogPress: handleLogPress,
        onFinishWorkoutPress,
        onTimeSaverPress,
        onModifyPress,
        onTimersPress,
        onCancelWorkoutPress: onCancelWorkout,
        onPauseWorkoutPress,
        onSaveSessionPress,
        onAddExercisePress,
        onCancelEditPress: handleBack,
        onSaveProgramPress,
        onSaveLoggedWorkoutPress,
        onAddCustomExercisePress,
        onCoachPress,
        onEditExercisePress,
        onAnalyzeTechniquePress,
        onAnalyzePosturePress: () => addToast("Función disponible en BodyLab", "suggestion"),
        onAddToPlaylistPress,
        onAddToSessionPress: () => addToast("No implementado.", "suggestion"),
        onCreatePostPress: () => addToast("No implementado.", "suggestion"),
        onCustomizeFeedPress: () => addToast("No implementado.", "suggestion"),
    }), [handleLogPress, onFinishWorkoutPress, onTimeSaverPress, onModifyPress, onTimersPress, onCancelWorkout, onPauseWorkoutPress, onSaveSessionPress, onAddExercisePress, handleBack, onSaveProgramPress, onSaveLoggedWorkoutPress, onAddCustomExercisePress, onCoachPress, onEditExercisePress, onAnalyzeTechniquePress, onAddToPlaylistPress, addToast]);

     const subTabBarContext = useMemo(() => {
        if (['kpkn', 'exercise-detail', 'muscle-group-detail', 'body-part-detail', 'chain-detail', 'muscle-category', 'joint-detail', 'tendon-detail', 'movement-pattern-detail'].includes(view)) return 'kpkn';
        if (['food-database', 'food-detail'].includes(view)) return 'food-database';
        if (['progress'].includes(view)) return 'progress';
        return null;
    }, [view]);

    const tabBarContext = useMemo(() => {
        if (view === 'workout') return 'workout';
        if (view === 'session-editor') return 'session-editor';
        if (view === 'log-workout') return 'log-workout';
        if (view === 'program-editor') return 'program-editor';
        return 'default';
    }, [view]);

    const renderView = useCallback(() => {
        switch (view) {
            case 'home': return <Home onNavigate={handleHomeNavigation} onResumeWorkout={handleResumeWorkout} onEditSleepLog={setEditingSleepLog}/>;
            case 'nutrition': return <NutritionView />;
            case 'recovery': return (
                <div className="pt-[20px] px-4 h-full overflow-y-auto hide-scrollbar tab-bar-safe-area">
                    <h1 className="text-3xl font-black uppercase tracking-tighter text-white mb-6">Recuperación</h1>
                    <MuscleRecoveryWidget />
                </div>
            );
            case 'sleep': return <SleepTrackerWidget onEditLog={setEditingSleepLog} />;
            
            case 'tasks': return <TasksView />;
            case 'programs': return <ProgramsView programs={programs} onSelectProgram={handleSelectProgram} onCreateProgram={handleCreateProgram} isOnline={isOnline} />;
            case 'program-detail': {
                const program = programs.find(p => p.id === activeProgramId);
                if (!program) return <div className="text-center pt-24 text-slate-400">Programa no encontrado.</div>;
                return <ProgramDetail program={program} history={history} settings={settings} isOnline={isOnline} onLogWorkout={handleLogWorkout} onEditProgram={handleEditProgram} onEditSession={handleEditSession} onDeleteSession={handleDeleteSession} onAddSession={handleAddSession} onDeleteProgram={handleDeleteProgram} onUpdateProgram={handleUpdateProgram} />;
            }
            case 'program-editor': {
                const programToEdit = editingProgramId ? (programs.find(p => p.id === editingProgramId) || null) : null;
                return <ProgramEditor onSave={handleSaveProgram} onCancel={handleBack} existingProgram={programToEdit} isOnline={isOnline} saveTrigger={saveProgramTrigger} />;
            }
            case 'session-editor': {
                if (editingSessionInfo) {
                    const { programId, macroIndex, mesoIndex, weekId, sessionId, dayOfWeek } = editingSessionInfo;
                    const program = programs.find(p => p.id === programId);
                    if (!program) return <div className="text-center text-red-400 pt-12">Programa no encontrado.</div>;

                    let session: Session | undefined;
                    const macro = program.macrocycles?.[macroIndex];
                    if (macro) {
                        let mesoCount = 0;
                        for (const block of (macro.blocks || [])) {
                            const blockMesoLen = block.mesocycles?.length || 0;
                            if (mesoIndex < mesoCount + blockMesoLen) {
                                const relativeMesoIndex = mesoIndex - mesoCount;
                                const meso = block.mesocycles?.[relativeMesoIndex];
                                if (meso?.weeks) {
                                    const week = meso.weeks.find(w => w.id === weekId);
                                    if (week?.sessions) session = week.sessions.find(s => s.id === sessionId);
                                }
                                break;
                            }
                            mesoCount += blockMesoLen;
                        }
                    }

                    const newSession = session || { id: sessionId || crypto.randomUUID(), name: 'Nueva Sesión', description: '', exercises: [], warmup: [], ...(dayOfWeek !== undefined ? { dayOfWeek } : {}) };
                    const sessionInfoForEditor = {
                        session: newSession,
                        programId, macroIndex, mesoIndex, weekId, sessionId
                    };
                    return <SessionEditor onSave={(sessions, pId, mac, mes, wId) => handleSaveSession(sessions, pId || programId, mac ?? macroIndex, mes ?? mesoIndex, wId || weekId)} onCancel={handleBack} existingSessionInfo={sessionInfoForEditor} isOnline={isOnline} settings={settings} saveTrigger={saveSessionTrigger} addExerciseTrigger={addExerciseTrigger} exerciseList={exerciseList} />;
                }
                return null;
            }
            case 'workout': {
                const programToRender = ongoingWorkout ? programs.find(p => p.id === ongoingWorkout.programId) : null;
                const sessionToRender = activeSession || ongoingWorkout?.session;
                if (sessionToRender && programToRender && ongoingWorkout) {
                    return <WorkoutSession session={sessionToRender} program={programToRender} programId={programToRender.id} settings={settings} history={history} onFinish={handleFinishWorkout} onCancel={onCancelWorkout} onUpdateExercise1RM={handleUpdateExercise1RM} isFinishModalOpen={isFinishModalOpen} setIsFinishModalOpen={setIsFinishModalOpen} onUpdateExerciseInProgram={handleUpdateExerciseInProgram} onUpdateSessionInProgram={handleUpdateSessionInProgram} exerciseList={exerciseList} isTimeSaverModalOpen={isTimeSaverModalOpen} setIsTimeSaverModalOpen={setIsTimeSaverModalOpen} isTimersModalOpen={isTimersModalOpen} setIsTimersModalOpen={setIsTimersModalOpen} onWorkoutViewModeChange={setWorkoutViewMode} />;
                }
                return (
                    <WorkoutViewFallback
                        onFallback={() => {
                            addToast('No se pudo cargar la sesión. Intenta de nuevo.', 'danger');
                            navigateTo('home');
                        }}
                    />
                );
            }
            case 'session-detail': return viewingSessionInfo && <SessionDetailView sessionInfo={viewingSessionInfo} />;
            case 'progress': return <PhysicalProgress />;
            case 'body-progress': return <Suspense fallback={<ViewFallback />}><BodyProgressView /></Suspense>;
            case 'settings': return <SettingsComponent settings={settings} onSettingsChange={setSettings} setPrograms={setPrograms} setHistory={setHistory} setSkippedLogs={setSkippedLogs} setBodyProgress={setBodyProgress} setNutritionLogs={setNutritionLogs} drive={drive} installPromptEvent={installPromptEvent} setInstallPromptEvent={setInstallPromptEvent} isOnline={isOnline} />;
            case 'coach': return <CoachView programs={programs} history={history} skippedLogs={skippedLogs} settings={settings} bodyProgress={bodyProgress} nutritionLogs={nutritionLogs} isOnline={isOnline} />;
            case 'log-hub': return <LogHub onNavigate={navigateTo} setIsBodyLogModalOpen={setIsBodyLogModalOpen} setIsNutritionLogModalOpen={setIsNutritionLogModalOpen} />;
            case 'achievements': return <AchievementsView unlocked={unlockedAchievements} />;
            case 'log-workout': {
                if (loggingSessionInfo) {
                    const program = programs.find(p => p.id === loggingSessionInfo.programId);
                    const session = program?.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles.flatMap(me => me.weeks.flatMap(w => w.sessions)))).find(s => s.id === loggingSessionInfo.sessionId);
                    if (program && session) return <LogWorkoutView sessionInfo={{ session, program }} settings={settings} history={history} onSave={handleSaveLoggedWorkout} onCancel={handleBack} isFinishModalOpen={isLogFinishModalOpen} setIsFinishModalOpen={setIsFinishModalOpen} onUpdateExercise1RM={handleUpdateExercise1RM} onUpdateExerciseInProgram={handleUpdateExerciseInProgram} exerciseList={exerciseList} />;
                }
                return null;
            }
            case 'kpkn': return <KPKNView />;
            case 'ai-art-studio': return <Suspense fallback={<ViewFallback />}><AIArtStudioView /></Suspense>;
            case 'body-lab': return <Suspense fallback={<ViewFallback />}><BodyLabView /></Suspense>;
            case 'mobility-lab': return <Suspense fallback={<ViewFallback />}><MobilityLabView /></Suspense>;
            case 'training-purpose': return <TrainingPurposeView />;
            case 'exercise-database': return <ExerciseDatabaseView />;
            case 'food-database': return <FoodDatabaseView />;
            case 'smart-meal-planner': return <SmartMealPlannerView />;
            case 'exercise-detail': return viewingExerciseId && <ExerciseDetailView exerciseId={viewingExerciseId} isOnline={isOnline} muscleHierarchy={muscleHierarchy} />;
            case 'muscle-group-detail': return viewingMuscleGroupId && <MuscleGroupDetailView muscleGroupId={viewingMuscleGroupId} isOnline={isOnline} />;
            case 'joint-detail': return viewingJointId && <JointDetailView jointId={viewingJointId} isOnline={isOnline} />;
            case 'tendon-detail': return viewingTendonId && <TendonDetailView tendonId={viewingTendonId} isOnline={isOnline} />;
            case 'movement-pattern-detail': return viewingMovementPatternId && <MovementPatternDetailView movementPatternId={viewingMovementPatternId} isOnline={isOnline} />;
            case 'body-part-detail': return viewingBodyPartId && <BodyPartDetailView bodyPartId={viewingBodyPartId as any} />;
            case 'muscle-category': return viewingMuscleCategoryName && <MuscleCategoryView categoryName={viewingMuscleCategoryName} />;
            case 'chain-detail': return viewingChainId && <ChainDetailView chainId={viewingChainId as any} />;

            case 'program-metric-volume':
            case 'program-metric-strength':
            case 'program-metric-density':
            case 'program-metric-frequency':
            case 'program-metric-banister':
            case 'program-metric-recovery':
            case 'program-metric-adherence':
            case 'program-metric-rpe': {
                const program = programs.find(p => p.id === activeProgramId);
                const metricData = historyStack[historyStack.length - 1]?.data;
                if (!program || !metricData) return <div className="text-center pt-24 text-slate-400">Programa no encontrado.</div>;
                const { selectedWeekId, currentWeeks = [], weeklyAdherence = [], metricId } = metricData;
                const displayedSessions = currentWeeks.find((w: any) => w.id === selectedWeekId)?.sessions ?? [];
                const adaptiveCache = metricData.adaptiveCache ?? getCachedAdaptiveData();
                const commonProps = { program, displayedSessions, history };
                if (view === 'program-metric-volume') return <ProgramMetricVolumeDetail {...commonProps} settings={settings} isOnline={isOnline} />;
                if (view === 'program-metric-strength') return <ProgramMetricStrengthDetail program={program} displayedSessions={displayedSessions} />;
                if (view === 'program-metric-density') return <ProgramMetricDensityDetail program={program} displayedSessions={displayedSessions} />;
                if (view === 'program-metric-frequency') return <ProgramMetricFrequencyDetail program={program} displayedSessions={displayedSessions} />;
                if (view === 'program-metric-banister') return <ProgramMetricBanisterDetail program={program} adaptiveCache={adaptiveCache} />;
                if (view === 'program-metric-recovery') return <ProgramMetricRecoveryDetail program={program} adaptiveCache={adaptiveCache} />;
                if (view === 'program-metric-adherence') return <ProgramMetricAdherenceDetail program={program} totalAdherence={metricData.totalAdherence ?? 0} weeklyAdherence={weeklyAdherence} />;
                if (view === 'program-metric-rpe') return <ProgramMetricRPEDetail program={program} displayedSessions={displayedSessions} />;
                return null;
            }
            
            default: return <Home onNavigate={handleHomeNavigation} onResumeWorkout={handleResumeWorkout} onEditSleepLog={setEditingSleepLog}/>;
        }
    }, [view, programs, history, settings, isOnline, activeProgramId, editingProgramId, editingSessionInfo, loggingSessionInfo, viewingSessionInfo, activeSession, viewingExerciseId, viewingMuscleGroupId, viewingJointId, viewingTendonId, viewingMovementPatternId, viewingBodyPartId, viewingChainId, viewingMuscleCategoryName, ongoingWorkout, isFinishModalOpen, isTimeSaverModalOpen, isTimersModalOpen, isLogFinishModalOpen, exerciseList, muscleHierarchy, saveProgramTrigger, saveSessionTrigger, saveLoggedWorkoutTrigger, addExerciseTrigger]);
    
    
    const tabBarContainerHeight = 'h-[88px]'; // Barra más ancha, diseño plano

    return (
        <div className="app-container fixed inset-0 w-full h-[100dvh] flex flex-col overflow-hidden bg-black" data-view={view === 'nutrition' ? 'nutrition' : undefined}>
            
            <AppBackground />

            {!settings.hasSeenWelcome && (
                <WelcomeWizard
                    onComplete={() => setSettings({ hasSeenWelcome: true })}
                    onConfigurar={(slideId) => {
                        setSettings({ hasSeenWelcome: true });
                        try { sessionStorage.setItem('kpkn_welcome_configurar', slideId); } catch (_) {}
                    }}
                />
            )}
            {settings.hasSeenWelcome && !settings.hasSeenGeneralWizard && (
                <GeneralOnboardingWizard
                    onComplete={() => setSettings({ hasSeenGeneralWizard: true })}
                />
            )}
            
            <GlobalVoiceAssistant />
            
            <AthleteIDDashboard 
                isOpen={isMenuOpen} 
                onClose={() => setIsMenuOpen(false)} 
                onNavigate={(v) => navigateTo(v)}
                onSettingsClick={() => navigateTo('settings')}
            />
            
            <main className={`app-main-content scroll-flexible flex-1 w-full relative overflow-y-auto overflow-x-hidden custom-scrollbar ${view === 'session-editor' || view === 'program-editor' ? 'z-[10000]' : 'z-10'} ${view === 'home' ? 'p-0' : view === 'workout' ? 'pt-0 pb-0' : 'pt-4 pb-[max(150px,calc(var(--tab-bar-safe-bottom,140px)+24px))]'}`}>
                <div className={`${view === 'home' || view === 'sleep' || view === 'workout' ? 'w-full min-h-full' : 'max-w-4xl mx-auto px-4'} animate-fade-in`}>
                    <ErrorBoundary key={view} fallbackLabel={view} onRecover={() => navigateTo('home')}>
                        <Suspense fallback={<ViewFallback />}>
                            {renderView()}
                        </Suspense>
                    </ErrorBoundary>
                </div>
            </main>
            
            {/* UPDATED TAB BAR CONTAINER: Hides on Program Editor, Session Editor, Workout, and Nutrition Wizard/Landing */}
            {view !== 'program-editor' && view !== 'session-editor' && view !== 'workout' && (view !== 'nutrition' || settings.hasSeenNutritionWizard) && (
                <div className={`tab-bar-card-container fixed bottom-0 left-0 w-full z-[60] rounded-t-none min-h-[88px] flex flex-col`}>
                     <div className="flex flex-col w-full flex-1 min-h-0">
                        <SubTabBar context={subTabBarContext} isActive={!!subTabBarContext} viewingExerciseId={viewingExerciseId} onEditExercisePress={tabBarActions.onEditExercisePress} />
                        <div className="h-[88px] shrink-0">
                            <TabBar activeView={view} navigate={(v) => navigateTo(v)} context={tabBarContext} actions={tabBarActions} isSubTabBarActive={!!subTabBarContext} workoutViewMode={workoutViewMode} />
                        </div>
                    </div>
                </div>
            )}
            
            <div className="fixed left-1/2 -translate-x-1/2 z-[300] flex flex-col gap-2 items-center pointer-events-none safe-area-toast-top">
                {toasts.map(toast => (
                    <Toast key={toast.id} toast={toast} onDismiss={removeToast} />
                ))}
            </div>
            
            <EditSleepLogModal isOpen={!!editingSleepLog} onClose={() => setEditingSleepLog(null)} onSave={handleSaveSleepLog} log={editingSleepLog} />
            {isCoachChatOpen && <CoachChatModal isOpen={isCoachChatOpen} onClose={() => setIsCoachChatOpen(false)} sessionContext={coachChatContext} programs={programs} history={history} isOnline={isOnline} settings={settings} />}
            {isNutritionLogModalOpen && <AddNutritionLogModal isOpen={isNutritionLogModalOpen} onClose={() => setIsNutritionLogModalOpen(false)} onSave={handleSaveNutritionLog} isOnline={isOnline} settings={settings} />}
            {isBodyLogModalOpen && <AddBodyLogModal isOpen={isBodyLogModalOpen} onClose={() => setIsBodyLogModalOpen(false)} onSave={handleSaveBodyLog} settings={settings} isOnline={isOnline} />}
            {isTimersModalOpen && <TimersModal isOpen={isTimersModalOpen} onClose={() => setIsTimersModalOpen(false)} />}
            {isCustomExerciseEditorOpen && <CustomExerciseEditorModal isOpen={isCustomExerciseEditorOpen} onClose={closeCustomExerciseEditor} onSave={addOrUpdateCustomExercise} isOnline={isOnline} existingExercise={editingCustomExerciseData?.exercise} preFilledName={editingCustomExerciseData?.preFilledName} />}
            {isFoodEditorOpen && <FoodEditorModal />}
            {state.isAddPantryItemModalOpen && <AddPantryItemModal />}
            {isVideoAnalysisModalOpen && <VideoAnalysisModal isOpen={isVideoAnalysisModalOpen} onClose={() => setIsVideoAnalysisModalOpen(false)} exerciseName={viewingExercise?.name || ''} isOnline={isOnline} settings={settings} />}
            {isReadinessModalOpen && <ReadinessDrawer isOpen={isReadinessModalOpen} onClose={() => { setIsReadinessModalOpen(false); setPendingWorkoutForReadinessCheck(null); }} onContinue={handleContinueFromReadiness} pendingWorkout={pendingWorkoutForReadinessCheck} />}
            {state.isAddToPlaylistSheetOpen && <AddToPlaylistSheet />}
            {isStartWorkoutModalOpen && <StartWorkoutDrawer isOpen={isStartWorkoutModalOpen} onClose={() => setIsStartWorkoutModalOpen(false)} />}
            {pendingCoachBriefing && <CoachBriefingDrawer isOpen={!!pendingCoachBriefing} onClose={handleContinueWorkoutAfterBriefing} briefing={pendingCoachBriefing} />}
            {state.isLogActionSheetOpen && <LogActionSheet />}
            <SpecialSessionLoggerModal />

            {/* --- MODAL DE NOVEDADES (SEMI-WIZARD) --- */}
            <UpdateNoveltiesModal
                isOpen={showUpdateModal}
                version={APP_VERSION}
                onClose={() => setShowUpdateModal(false)}
            />

            {/* --- VIP PASS IOS BANNER --- */}
            {showIOSPrompt && (
                <div className="fixed bottom-4 left-4 right-4 z-[9999] animate-slide-up pointer-events-auto">
                    <div className="bg-black/90 backdrop-blur-2xl border border-white/20 p-4 rounded-3xl shadow-2xl flex items-center gap-4">
                        <div className="w-12 h-12 bg-white rounded-xl flex-shrink-0 flex items-center justify-center p-1 shadow-inner">
                            <img src="/icon-192.png" alt="KPKN" className="w-full h-full object-contain rounded-lg" />
                        </div>
                        <div className="flex-1">
                            <h3 className="text-white font-black text-sm uppercase tracking-tight leading-none mb-1">Instala KPKN</h3>
                            <p className="text-slate-400 text-[10px] font-bold leading-tight">
                                Toca <svg className="inline w-4 h-4 text-sky-500 mb-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/><polyline points="16 6 12 2 8 6"/><line x1="12" y1="2" x2="12" y2="15"/></svg> y selecciona <br/><span className="text-white font-black">"Añadir a inicio"</span>
                            </p>
                        </div>
                        <button 
                            onClick={() => { setShowIOSPrompt(false); localStorage.setItem('kpkn_ios_prompt_hidden', 'true'); }} 
                            className="text-slate-500 hover:text-white bg-white/5 border border-white/10 p-2.5 rounded-full transition-colors"
                        >
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};