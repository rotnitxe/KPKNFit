// App.tsx
import { GlobalVoiceAssistant } from './components/GlobalVoiceAssistant';
import React, { useState, useEffect, useCallback, useMemo, useRef, Suspense } from 'react';
import { useAppState, useAppDispatch } from './contexts/AppContext';
import { View, Session, OngoingWorkoutState, Program, TabBarActions, WorkoutLog, SleepLog } from './types';
import useAchievements from './hooks/useAchievements';
import { pSBC } from './utils/colorUtils';
import useLocalStorage from './hooks/useLocalStorage';
import { setupNotificationChannels } from './services/notificationService';
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
import ReadinessCheckModal from './components/ReadinessCheckModal';
import AddToPlaylistSheet from './components/AddToPlaylistSheet';
import StartWorkoutModal from './components/StartWorkoutModal';
import CoachBriefingModal from './components/CoachBriefingModal';
import AddBodyLogModal from './components/AddBodyLogModal';
import LogActionSheet from './components/LogActionSheet';
import FoodEditorModal from './components/FoodEditorModal';
import AddPantryItemModal from './components/AddPantryItemModal';
import { SleepWidgetHeader } from './components/SleepWidgetHeader';
import { PostSessionQuestionnaireWidget } from './components/PostSessionQuestionnaireWidget';
import AthleteIDDashboard from './components/AthleteIDDashboard';

// Icons for header & new menu
import { ArrowLeftIcon, IdCardIcon } from './components/icons';

// --- Static Imports for Performance ---
import Home from './components/Home';
// FIX: Cambiado a import por defecto para coincidir con el archivo ProgramDetail
import ProgramDetail from './components/ProgramDetail';
import ProgramsView from './components/ProgramsView';
import ProgramEditor from './components/ProgramEditor';
import { SessionEditor } from './components/SessionEditor';
import { WorkoutSession } from './components/WorkoutSession';
import { SettingsComponent } from './components/SettingsComponent';
import CoachView from './components/CoachView';
import PhysicalProgress from './components/PhysicalProgress';
import LogHub from './components/LogHub';
import AchievementsView from './components/AchievementsView';
import LogWorkoutView from './components/LogWorkoutView';
import YourLab from './components/YourLab';
import { ExerciseDetailView } from './components/ExerciseDetailView';
import MuscleGroupDetailView from './components/MuscleGroupDetailView';
import HallOfFameView from './components/HallOfFameView';
import BodyPartDetailView from './components/BodyPartDetailView';
import MuscleCategoryView from './components/MuscleCategoryView';
import ChainDetailView from './components/ChainDetailView';
import MuscleListEditorModal from './components/MuscleListEditorModal';
import BodyLabView from './components/BodyLabView';
import TrainingPurposeView from './components/TrainingPurposeView';
import ExerciseDatabaseView from './components/ExerciseDatabaseView';
import TasksView from './components/TasksView';
import SessionDetailView from './components/SessionDetailView';
import SmartMealPlannerView from './components/SmartMealPlannerView';
import NutritionView from './components/NutritionView';
import FoodDatabaseView from './components/FoodDatabaseView';
import EditSleepLogModal from './components/EditSleepLogModal';

// Components formerly widgets, now views
import SleepTrackerWidget from './components/SleepTrackerWidget';
import MuscleRecoveryWidget from './components/MuscleRecoveryWidget';

const MobilityLabView = React.lazy(() => import('./components/MobilityLabView'));
const AIArtStudioView = React.lazy(() => import('./components/AIArtStudioView'));

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
        viewingBodyPartId,
        viewingChainId,
        viewingMuscleCategoryName,
        settings,
        programs,
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
        pendingWorkoutForReadinessCheck
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
    const [isVideoAnalysisModalOpen, setIsVideoAnalysisModalOpen] = useState(false);
    const [editingSleepLog, setEditingSleepLog] = useState<any | null>(null);

    const handleSaveSleepLog = useCallback((log: SleepLog) => {
        setSleepLogs(prev => prev.map(l => l.id === log.id ? log : l));
        setEditingSleepLog(null);
    }, [setSleepLogs]);

    const isTopLevelView = useMemo(() => ['home', 'nutrition', 'recovery', 'sleep', 'your-lab', 'progress', 'tasks'].includes(view), [view]);
    
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
        if (['your-lab', 'exercise-detail', 'muscle-group-detail', 'body-part-detail', 'chain-detail', 'muscle-category', 'hall-of-fame'].includes(view)) return 'your-lab';
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
                <div className="pt-[20px] px-4 h-full overflow-y-auto hide-scrollbar pb-32">
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
                const programToEdit = editingProgramId ? programs.find(p => p.id === editingProgramId) : null;
                return <ProgramEditor onSave={handleSaveProgram} onCancel={handleBack} existingProgram={programToEdit} isOnline={isOnline} saveTrigger={saveProgramTrigger} />;
            }
            case 'session-editor': {
                if (editingSessionInfo) {
                    const { programId, macroIndex, mesoIndex, weekId, sessionId } = editingSessionInfo;
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

                    const sessionInfoForEditor = {
                        session: session || { id: sessionId || crypto.randomUUID(), name: 'Nueva Sesión', description: '', exercises: [], warmup: [] },
                        programId, macroIndex, mesoIndex, weekId, sessionId
                    };
                    return <SessionEditor onSave={handleSaveSession} onCancel={handleBack} existingSessionInfo={sessionInfoForEditor} isOnline={isOnline} settings={settings} saveTrigger={saveSessionTrigger} addExerciseTrigger={addExerciseTrigger} exerciseList={exerciseList} />;
                }
                return null;
            }
            case 'workout': {
                const programToRender = ongoingWorkout ? programs.find(p => p.id === ongoingWorkout.programId) : null;
                const sessionToRender = activeSession || ongoingWorkout?.session;
                if (sessionToRender && programToRender && ongoingWorkout) {
                    return <WorkoutSession session={sessionToRender} program={programToRender} programId={programToRender.id} settings={settings} history={history} onFinish={handleFinishWorkout} onCancel={onCancelWorkout} onUpdateExercise1RM={handleUpdateExercise1RM} isFinishModalOpen={isFinishModalOpen} setIsFinishModalOpen={setIsFinishModalOpen} onUpdateExerciseInProgram={handleUpdateExerciseInProgram} onUpdateSessionInProgram={handleUpdateSessionInProgram} exerciseList={exerciseList} isTimeSaverModalOpen={isTimeSaverModalOpen} setIsTimeSaverModalOpen={setIsTimeSaverModalOpen} isTimersModalOpen={isTimersModalOpen} setIsTimersModalOpen={setIsTimersModalOpen} />;
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
            case 'your-lab': return <YourLab />;
            case 'ai-art-studio': return <Suspense fallback={<div>Cargando...</div>}><AIArtStudioView /></Suspense>;
            case 'body-lab': return <BodyLabView />;
            case 'mobility-lab': return <Suspense fallback={<div>Cargando...</div>}><MobilityLabView /></Suspense>;
            case 'training-purpose': return <TrainingPurposeView />;
            case 'exercise-database': return <ExerciseDatabaseView />;
            case 'food-database': return <FoodDatabaseView />;
            case 'smart-meal-planner': return <SmartMealPlannerView />;
            case 'exercise-detail': return viewingExerciseId && <ExerciseDetailView exerciseId={viewingExerciseId} isOnline={isOnline} muscleHierarchy={muscleHierarchy} />;
            case 'muscle-group-detail': return viewingMuscleGroupId && <MuscleGroupDetailView muscleGroupId={viewingMuscleGroupId} isOnline={isOnline} />;
            case 'body-part-detail': return viewingBodyPartId && <BodyPartDetailView bodyPartId={viewingBodyPartId as any} />;
            case 'muscle-category': return viewingMuscleCategoryName && <MuscleCategoryView categoryName={viewingMuscleCategoryName} />;
            case 'chain-detail': return viewingChainId && <ChainDetailView chainId={viewingChainId as any} />;
            case 'hall-of-fame': return <HallOfFameView />;
            
            default: return <Home onNavigate={handleHomeNavigation} onResumeWorkout={handleResumeWorkout} onEditSleepLog={setEditingSleepLog}/>;
        }
    }, [view, programs, history, settings, isOnline, activeProgramId, editingProgramId, editingSessionInfo, loggingSessionInfo, viewingSessionInfo, activeSession, viewingExerciseId, viewingMuscleGroupId, viewingBodyPartId, viewingChainId, viewingMuscleCategoryName, ongoingWorkout, isFinishModalOpen, isTimeSaverModalOpen, isTimersModalOpen, isLogFinishModalOpen, exerciseList, muscleHierarchy, saveProgramTrigger, saveSessionTrigger, saveLoggedWorkoutTrigger, addExerciseTrigger]);
    
    
    const tabBarContainerHeight = 'h-[75px]'; // Standard height for anchored nav + safe area

    return (
        <div className="app-container fixed inset-0 w-full h-[100dvh] flex flex-col overflow-hidden bg-black">
            
            <AppBackground />
            
            <GlobalVoiceAssistant />
            
            <AthleteIDDashboard 
                isOpen={isMenuOpen} 
                onClose={() => setIsMenuOpen(false)} 
                onNavigate={(v) => navigateTo(v)}
                onSettingsClick={() => navigateTo('settings')}
            />
            
            <main className={`app-main-content flex-1 w-full relative overflow-y-auto overflow-x-hidden custom-scrollbar z-10 ${view === 'home' ? 'p-0' : 'pt-4'} pb-[100px]`}>
                <div className={`${view === 'home' || view === 'sleep' ? 'w-full min-h-full' : 'max-w-4xl mx-auto px-4'} animate-fade-in`}>
                    {renderView()}
                </div>
            </main>
            
            {/* UPDATED TAB BAR CONTAINER: Hides on Program Editor to allow full screen Wizard */}
            {view !== 'program-editor' && (
                <div className={`tab-bar-card-container fixed bottom-0 left-0 w-full z-[60] border-t border-white/10 rounded-t-none backdrop-blur-xl ${tabBarContainerHeight}`}>
                     <div className="relative w-full h-full">
                        {/* SubTabBar is absolutely positioned inside here, pushed up */}
                        <SubTabBar context={subTabBarContext} isActive={!!subTabBarContext} viewingExerciseId={viewingExerciseId} onEditExercisePress={tabBarActions.onEditExercisePress} />
                        <TabBar activeView={view} navigate={(v) => navigateTo(v)} context={tabBarContext} actions={tabBarActions} isSubTabBarActive={!!subTabBarContext} />
                    </div>
                </div>
            )}
            
            <div className="fixed top-16 left-1/2 -translate-x-1/2 z-[300] flex flex-col gap-2 items-center pointer-events-none">
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
            {isReadinessModalOpen && <ReadinessCheckModal isOpen={isReadinessModalOpen} onClose={() => { setIsReadinessModalOpen(false); setPendingWorkoutForReadinessCheck(null); }} onContinue={handleContinueFromReadiness} />}
            {state.isAddToPlaylistSheetOpen && <AddToPlaylistSheet />}
            {isStartWorkoutModalOpen && <StartWorkoutModal isOpen={isStartWorkoutModalOpen} onClose={() => setIsStartWorkoutModalOpen(false)} />}
            {pendingCoachBriefing && <CoachBriefingModal isOpen={!!pendingCoachBriefing} onClose={handleContinueWorkoutAfterBriefing} briefing={pendingCoachBriefing} />}
            {state.isLogActionSheetOpen && <LogActionSheet />}
            <SpecialSessionLoggerModal />
        </div>
    );
};