// AppContext.tsx
import React, { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  AppContextState, AppContextDispatch, View, Program, Session, WorkoutLog, SkippedWorkoutLog,
  BodyProgressLog, NutritionLog, Settings, ExerciseMuscleInfo, OngoingWorkoutState,
  ToastData, CompletedExercise, Exercise, CustomExerciseModalData, ExercisePlaylist,
  MuscleGroupInfo, MuscleHierarchy, PlanDeviation, SessionBackground, ProgramWeek, Achievement,
  BodyLabAnalysis, BiomechanicalData, BiomechanicalAnalysis, Task, PantryItem, CarpeDiemPlan, ExerciseSet, Block, Mesocycle, AINutritionPlan, ActiveProgramState, WorkoutLogSchema, FoodItem,
  SleepLog,
  PostSessionFeedback,
  PendingQuestionnaire,
  RecommendationTrigger,
  WaterLog,
  DailyWellbeingLog
} from '../types';
import { z } from 'zod';

import useSettings from '../hooks/useSettings';
import useLocalStorage from '../hooks/useLocalStorage';
import useAchievements from '../hooks/useAchievements';
import useGoogleDrive from '../hooks/useGoogleDrive';
import useExerciseDatabase from '../hooks/useExerciseDatabase';
import { INITIAL_MUSCLE_GROUP_DATA } from '../data/initialMuscleGroupDatabase';
import { INITIAL_MUSCLE_HIERARCHY } from '../data/initialMuscleHierarchy';
import { FOOD_DATABASE } from '../data/foodDatabase';
import { playSound } from '../services/soundService';
import { hapticImpact, ImpactStyle, hapticNotification, NotificationType } from '../services/hapticsService';
import { setupNotificationChannels, scheduleWorkoutReminders, cancelPendingNotifications } from '../services/notificationService';
import * as aiService from '../services/aiService';
import { getWeekId, estimatePercent1RM, getRepDebtContextKey, calculateBrzycki1RM } from '../utils/calculations';
import { cacheService } from '../services/cacheService';
import { calculateCompletedSessionStress } from '../services/fatigueService';
import { storageService } from '../services/storageService';

const AppStateContext = createContext<AppContextState | undefined>(undefined);
const AppDispatchContext = createContext<AppContextDispatch | undefined>(undefined);

const safeCreateISOStringFromDateInput = (dateString?: string): string => {
    if (dateString && /^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
        return new Date(dateString + 'T12:00:00Z').toISOString();
    }
    return new Date().toISOString();
};

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    // --- 1. TODOS LOS ESTADOS (useState, useLocalStorage, hooks personalizados) ---
    const [view, setView] = useState<View>('home');
    const [historyStack, setHistoryStack] = useState<{ view: View; data?: any }[]>([{ view: 'home' }]);

    const [settings, setSettings, isSettingsLoading] = useSettings();
    const [programs, setPrograms, isProgramsLoading] = useLocalStorage<Program[]>('programs', []);
    const [history, setHistory, isHistoryLoading] = useLocalStorage<WorkoutLog[]>('history', []);
    const [skippedLogs, setSkippedLogs, isSkippedLoading] = useLocalStorage<SkippedWorkoutLog[]>('skipped-logs', []);
    const [bodyProgress, setBodyProgress, isBodyProgressLoading] = useLocalStorage<BodyProgressLog[]>('body-progress', []);
    const [nutritionLogs, setNutritionLogs, isNutritionLogsLoading] = useLocalStorage<NutritionLog[]>('nutrition-logs', []);
    const [pantryItems, setPantryItems, isPantryLoading] = useLocalStorage<PantryItem[]>('yourprime-pantry-items', []);
    const [tasks, setTasks] = useLocalStorage<Task[]>('yourprime-tasks', []);
    const [ongoingWorkout, setOngoingWorkout, isOngoingLoading] = useLocalStorage<OngoingWorkoutState | null>('ongoing-workout-session', null);
    const [exercisePlaylists, setExercisePlaylists] = useLocalStorage<ExercisePlaylist[]>('yourprime-playlists', []);
    const [muscleGroupData, setMuscleGroupData] = useLocalStorage<MuscleGroupInfo[]>('yourprime-muscle-group-data', INITIAL_MUSCLE_GROUP_DATA);
    const [muscleHierarchy, setMuscleHierarchy] = useLocalStorage<MuscleHierarchy>('yourprime-muscle-hierarchy', INITIAL_MUSCLE_HIERARCHY);
    const [foodDatabase, setFoodDatabase] = useLocalStorage<FoodItem[]>('yourprime-food-database', FOOD_DATABASE);
    const [bodyLabAnalysis, _setBodyLabAnalysis] = useLocalStorage<BodyLabAnalysis | null>('yourprime-bodylab-analysis', null);
    const [biomechanicalData, _setBiomechanicalData] = useLocalStorage<BiomechanicalData | null>('yourprime-biomechanical-data', null);
    const [biomechanicalAnalysis, _setBiomechanicalAnalysis] = useLocalStorage<BiomechanicalAnalysis | null>('yourprime-biomechanical-analysis', null);
    const [syncQueue, setSyncQueue] = useLocalStorage<WorkoutLog[]>('yourprime-sync-queue', []);
    const [aiNutritionPlan, setAiNutritionPlan] = useLocalStorage<AINutritionPlan | null>('yourprime-ai-nutrition-plan', null);
    const [activeProgramState, setActiveProgramState] = useLocalStorage<ActiveProgramState | null>('yourprime-active-program-state', null);
    const [onExerciseCreated, setOnExerciseCreated] = useState<((exercise: ExerciseMuscleInfo) => void) | null>(null);
    const [pendingCoachBriefing, setPendingCoachBriefing] = useState<string | null>(null);
    const [pendingWorkoutAfterBriefing, setPendingWorkoutAfterBriefing] = useState<AppContextState['pendingWorkoutAfterBriefing']>(null);
    const [pendingQuestionnaires, setPendingQuestionnaires] = useLocalStorage<PendingQuestionnaire[]>('yourprime-pending-questionnaires', []);
    const [postSessionFeedback, setPostSessionFeedback] = useLocalStorage<PostSessionFeedback[]>('yourprime-post-session-feedback', []);
    const [recommendationTriggers, setRecommendationTriggers] = useLocalStorage<RecommendationTrigger[]>('yourprime-recommendation-triggers', []);

    // Sleep Tracking State
    const [sleepLogs, setSleepLogs] = useLocalStorage<SleepLog[]>('yourprime-sleep-logs', []);
    const [sleepStartTime, setSleepStartTime] = useLocalStorage<number | null>('yourprime-sleep-start-time', null);

    // Water Tracking State
    const [waterLogs, setWaterLogs, isWaterLogsLoading] = useLocalStorage<WaterLog[]>('yourprime-water-logs', []);

    // Daily Wellbeing Logs (Non-workout days)
    const [dailyWellbeingLogs, setDailyWellbeingLogs] = useLocalStorage<DailyWellbeingLog[]>('yourprime-daily-wellbeing', []);

    const { unlockedAchievements, checkAndUnlock } = useAchievements();
    const { exerciseList, setExerciseList, isDbLoading, addOrUpdateCustomExercise, exportExerciseDatabase, importExerciseDatabase } = useExerciseDatabase();

    const [isOnline, setIsOnline] = useState(navigator.onLine);
    const [installPromptEvent, setInstallPromptEvent] = useState<any>(null);
    const [toasts, setToasts] = useState<ToastData[]>([]);

    // --- MODAL & SHEET STATES ---
    const [isBodyLogModalOpen, setIsBodyLogModalOpen] = useState(false);
    const [isNutritionLogModalOpen, setIsNutritionLogModalOpen] = useState(false);
    const [isMeasurementsModalOpen, setIsMeasurementsModalOpen] = useState(false);
    const [isStartWorkoutModalOpen, setIsStartWorkoutModalOpen] = useState(false);
    const [isCustomExerciseEditorOpen, setIsCustomExerciseEditorOpen] = useState(false);
    const [isFoodEditorOpen, setIsFoodEditorOpen] = useState(false);
    const [isFinishModalOpen, setIsFinishModalOpen] = useState(false);
    const [isTimeSaverModalOpen, setIsTimeSaverModalOpen] = useState(false);
    const [isTimersModalOpen, setIsTimersModalOpen] = useState(false);
    const [isReadinessModalOpen, setIsReadinessModalOpen] = useState(false);
    const [isAddToPlaylistSheetOpen, setIsAddToPlaylistSheetOpen] = useState(false);
    const [isWorkoutEditorOpen, setIsWorkoutEditorOpen] = useState(false);
    const [isMuscleListEditorOpen, setIsMuscleListEditorOpen] = useState(false);
    const [isLiveCoachActive, setIsLiveCoachActive] = useState(false);
    const [isLogActionSheetOpen, setIsLogActionSheetOpen] = useState(false);
    const [isWorkoutExitModalOpen, setIsWorkoutExitModalOpen] = useState(false);
    const [isAddPantryItemModalOpen, setIsAddPantryItemModalOpen] = useState(false);
    const [isSpecialSessionModalOpen, setIsSpecialSessionModalOpen] = useState(false);
    const [specialSessionData, setSpecialSessionData] = useState<any | null>(null);
    const [pendingNavigation, setPendingNavigation] = useState<AppContextState['pendingNavigation']>(null);
    const [foodItemToAdd_to_pantry, setFoodItemToAdd_to_pantry] = useState<FoodItem | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [activeSubTabs, setActiveSubTabs] = useState<AppContextState['activeSubTabs']>({});
    const [kpknAction, setKpknaction] = useState<any>(null);
    const [isMenuOpen, setIsMenuOpen] = useState(false);

    // Global Voice State
    const [isGlobalVoiceActive, setIsGlobalVoiceActive] = useState(false);

    // --- EDITING & VIEWING STATES (ID-BASED) ---
    const [activeProgramId, setActiveProgramId] = useState<string | null>(null);
    const [editingProgramId, setEditingProgramId] = useState<string | null>(null);
    const [editingSessionInfo, setEditingSessionInfo] = useState<{ programId: string; macroIndex: number; mesoIndex: number; weekId: string; sessionId?: string; dayOfWeek?: number; } | null>(null);
    const [loggingSessionInfo, setLoggingSessionInfo] = useState<{ programId: string; sessionId: string } | null>(null);
    const [viewingSessionInfo, setViewingSessionInfo] = useState<{ programId: string; sessionId: string; } | null>(null);
    const [activeSession, setActiveSession] = useState<Session | null>(null);
    const [viewingExerciseId, setViewingExerciseId] = useState<string | null>(null);
    const [viewingFoodId, setViewingFoodId] = useState<string | null>(null);
    const [viewingMuscleGroupId, setViewingMuscleGroupId] = useState<string | null>(null);
    const [viewingBodyPartId, setViewingBodyPartId] = useState<string | null>(null);
    const [viewingChainId, setViewingChainId] = useState<string | null>(null);
    const [viewingMuscleCategoryName, setViewingMuscleCategoryName] = useState<string | null>(null);
    const [exerciseToAddId, setExerciseToAddId] = useState<string | null>(null);
    const [editingCustomExerciseData, setEditingCustomExerciseData] = useState<CustomExerciseModalData | null>(null);
    const [editingFoodData, setEditingFoodData] = useState<{ food?: FoodItem, preFilledName?: string } | null>(null);
    const [pendingWorkoutForReadinessCheck, setPendingWorkoutForReadinessCheck] = useState<{ session: Session; program: Program; weekVariant?: 'A' | 'B' | 'C' | 'D', location?: { macroIndex: number, mesoIndex: number, weekId: string } } | null>(null);
    const [editingWorkoutSessionInfo, setEditingWorkoutSessionInfo] = useState<{ session: Session; programId: string; macroIndex: number; mesoIndex: number; weekId: string; } | null>(null);
    const [editingCategoryInfo, setEditingCategoryInfo] = useState<{ name: string, type: 'bodyPart' | 'special' } | null>(null);

    // --- TRIGGERS ---
    const [saveSessionTrigger, setSaveSessionTrigger] = useState(0);
    const [addExerciseTrigger, setAddExerciseTrigger] = useState(0);
    const [saveProgramTrigger, setSaveProgramTrigger] = useState(0);
    const [saveLoggedWorkoutTrigger, setSaveLoggedWorkoutTrigger] = useState(0);
    const [modifyWorkoutTrigger, setModifyWorkoutTrigger] = useState(0);

    const [currentBackgroundOverride, setCurrentBackgroundOverride] = useState<SessionBackground | undefined>();
    const [restTimer, setRestTimer] = useState<{ duration: number; remaining: number; key: number; exerciseName: string; endTime: number; } | null>(null);
    const restTimerInterval = useRef<number | null>(null);
    const [isDirty, setIsDirty] = useState(false);

    // Google Drive hook (debe estar después de addToast, que se define más abajo)
    const drive = useGoogleDrive({
        settings, addToast: (msg, type, title, duration) => addToast(msg, type, title, duration), // addToast se definirá después, pero pasamos la función que todavía no existe. Para evitarlo, definimos un placeholder o movemos el drive después de addToast. Lo mejor es mover la definición de drive después de addToast.
        onLoad: (data: any) => {
            if (data.programs && data.programs.length > 0) setPrograms(data.programs);
            if (data.history && data.history.length > 0) setHistory(data.history);
            if (data.settings && Object.keys(data.settings).length > 0) setSettings(data.settings);
            if (data['body-progress'] && data['body-progress'].length > 0) setBodyProgress(data['body-progress']);
            addToast('Datos sincronizados desde la nube.', 'success');
        }
    });

    const isAppLoading = isSettingsLoading || isProgramsLoading || isHistoryLoading || isSkippedLoading || isBodyProgressLoading || isNutritionLogsLoading || isDbLoading || isPantryLoading || isWaterLogsLoading || isOngoingLoading;

    // --- 1.5. INICIALIZAR HISTORIAL NATIVO ---
    useEffect(() => {
        // Solo en el primer renderizado, aseguramos que el estado inicial esté en history nativo
        if (window.history.state === null) {
            window.history.replaceState({ view: 'home' }, '');
        }
    }, []);

    // --- 2. DETECTAR CAMBIOS DE CONEXIÓN (useEffect) ---
    useEffect(() => {
        const handleOnline = () => setIsOnline(true);
        const handleOffline = () => setIsOnline(false);
        window.addEventListener('online', handleOnline);
        window.addEventListener('offline', handleOffline);
        return () => {
            window.removeEventListener('online', handleOnline);
            window.removeEventListener('offline', handleOffline);
        };
    }, []);

    // --- 3. TODOS LOS CALLBACKS (useCallback) en orden de dependencia ---

    // TOASTS
    const addToast = useCallback((message: string, type: ToastData['type'] = 'success', title?: string, duration?: number) => {
        const id = Date.now();
        setToasts(prev => [...prev, { id, message, type, title, duration }]);
    }, [setToasts]);

    const removeToast = useCallback((id: number) => {
        setToasts(prev => prev.filter(t => t.id !== id));
    }, [setToasts]);

    // RECOMENDACIONES
    const addRecommendationTrigger = useCallback((trigger: Omit<RecommendationTrigger, 'id' | 'date' | 'actionTaken'>) => {
        const newTrigger: RecommendationTrigger = {
            ...trigger,
            id: crypto.randomUUID(),
            date: new Date().toISOString(),
            actionTaken: false
        };
        setRecommendationTriggers(prev => [newTrigger, ...prev]);
    }, [setRecommendationTriggers]);

    const markRecommendationAsTaken = useCallback((id: string) => {
        setRecommendationTriggers(prev => prev.map(t => t.id === id ? { ...t, actionTaken: true } : t));
    }, [setRecommendationTriggers]);

    // SLEEP TRACKING
    const handleLogSleep = useCallback((action: 'start' | 'end') => {
        if (action === 'start') {
            setSleepStartTime(Date.now());
            addToast("Dulces sueños...", 'suggestion');
        } else {
            if (sleepStartTime) {
                const endTime = Date.now();
                const durationMs = endTime - sleepStartTime;
                const durationHours = durationMs / (1000 * 60 * 60);

                if (durationHours < 1) {
                    addToast("El sueño debe durar al menos 1 hora para ser registrado.", "danger");
                    setSleepStartTime(null);
                    return;
                }

                const newLog: SleepLog = {
                    id: crypto.randomUUID(),
                    startTime: new Date(sleepStartTime).toISOString(),
                    endTime: new Date(endTime).toISOString(),
                    duration: durationHours,
                    date: new Date(endTime).toISOString().split('T')[0],
                };

                setSleepLogs(prev => [...prev, newLog].slice(-100));
                setSleepStartTime(null);
                addToast(`Dormiste ${durationHours.toFixed(1)} horas. ¡A por el día!`, 'success');
            }
        }
    }, [sleepStartTime, setSleepStartTime, setSleepLogs, addToast]);

    const handleSavePostSessionFeedback = useCallback((feedback: PostSessionFeedback) => {
        setPostSessionFeedback(prev => [...prev, feedback]);
        setPendingQuestionnaires(prev => prev.filter(q => q.logId !== feedback.logId));
        addToast("Gracias por tu feedback.", "success");
    }, [setPostSessionFeedback, setPendingQuestionnaires, addToast]);

    // WATER LOGGING
    const handleLogWater = useCallback((amountMl: number) => {
        setWaterLogs(prev => [...prev, { id: crypto.randomUUID(), date: new Date().toISOString(), amountMl }]);
        addToast(`Añadidos ${amountMl}ml de agua.`, "success");
    }, [setWaterLogs, addToast]);

    // DAILY WELLBEING
    const handleLogDailyWellbeing = useCallback((data: Omit<DailyWellbeingLog, 'id'>) => {
        const newLog: DailyWellbeingLog = { ...data, id: crypto.randomUUID() };
        setDailyWellbeingLogs(prev => {
            const filtered = prev.filter(l => l.date !== data.date);
            return [...filtered, newLog];
        });
        addToast("Bienestar diario registrado.", "success");
    }, [setDailyWellbeingLogs, addToast]);

    // TASKS
    const addTask = useCallback((task: Omit<Task, 'id' | 'completed' | 'generatedBy'>) => {
        const newTask: Task = {
            id: crypto.randomUUID(),
            completed: false,
            generatedBy: 'user',
            ...task
        };
        setTasks(prev => [newTask, ...prev]);
    }, [setTasks]);

    const addAITasks = useCallback((tasks: Omit<Task, 'id' | 'completed'>[]) => {
        const newTasks: Task[] = tasks.map(t => ({ ...t, id: crypto.randomUUID(), completed: false }));
        setTasks(prev => [...newTasks, ...prev]);
    }, [setTasks]);

    const toggleTask = useCallback((taskId: string) => {
        setTasks(prev => prev.map(task =>
            task.id === taskId ? { ...task, completed: !task.completed, completedDate: !task.completed ? new Date().toISOString() : undefined } : task
        ));
    }, [setTasks]);

    const deleteTask = useCallback((taskId: string) => {
        setTasks(prev => prev.filter(task => task.id !== taskId));
    }, [setTasks]);

    // EXERCISE CREATION
    const createAndAddExerciseToDB = useCallback(async (exerciseName: string): Promise<ExerciseMuscleInfo | null> => {
        addToast(`Creando "${exerciseName}" en KPKN...`, "suggestion", "IA en Progreso");
        try {
            const populatedData = await aiService.createAndPopulateExercise(exerciseName, settings);
            const newExercise: ExerciseMuscleInfo = {
                id: `custom_${crypto.randomUUID()}`,
                name: exerciseName,
                description: '',
                involvedMuscles: [],
                category: 'Hipertrofia',
                type: 'Accesorio',
                equipment: 'Otro',
                force: 'Otro',
                ...populatedData,
                isCustom: true,
            };
            addOrUpdateCustomExercise(newExercise);
            addToast(`"${exerciseName}" añadido a KPKN!`, 'success');
            return newExercise;
        } catch (error: any) {
            addToast(error.message || `Error al crear "${exerciseName}"`, 'danger');
            return null;
        }
    }, [settings, addOrUpdateCustomExercise, addToast]);

    const batchAddExercises = useCallback((newExercises: ExerciseMuscleInfo[]) => {
        setExerciseList(prev => {
            const existingNames = new Set(prev.map(ex => ex.name.toLowerCase()));
            const nonDuplicates = newExercises.filter(
                newEx => !existingNames.has(newEx.name.toLowerCase())
            );

            if (nonDuplicates.length > 0) {
                addToast(`${nonDuplicates.length} nuevos ejercicios añadidos a KPKN!`, 'success');
            } else if (newExercises.length > 0) {
                addToast('Todos los ejercicios sugeridos ya existen en tu base de datos.', 'suggestion');
            }
            return [...prev, ...nonDuplicates];
        });
    }, [setExerciseList, addToast]);

    // NAVIGATION
    const navigateTo = useCallback((newView: View, data?: any, options?: { replace?: boolean }) => {
        const stateToPush = { view: newView, data };
        if (options?.replace) {
            setHistoryStack(prev => [...prev.slice(0, -1), stateToPush]);
            // Sincronizar con el historial nativo (reemplazo)
            window.history.replaceState(stateToPush, '');
        } else {
            setHistoryStack(prev => [...prev, stateToPush]);
            // Sincronizar con el historial nativo (nuevo estado)
            window.history.pushState(stateToPush, '');
        }

        setActiveProgramId(null); setEditingProgramId(null); setEditingSessionInfo(null);
        setLoggingSessionInfo(null); setViewingSessionInfo(null); setViewingExerciseId(null); setViewingMuscleGroupId(null);
        setViewingBodyPartId(null); setViewingChainId(null); setViewingMuscleCategoryName(null);
        setViewingFoodId(null);

        if (data) {
            switch (newView) {
                case 'program-detail': setActiveProgramId(data.programId); break;
                case 'program-editor': setEditingProgramId(data.programId || null); break;
                case 'session-editor': setEditingSessionInfo(data); break;
                case 'log-workout': setLoggingSessionInfo(data); break;
                case 'session-detail': setViewingSessionInfo(data); break;
                case 'exercise-detail': setViewingExerciseId(data.exerciseId); break;
                case 'muscle-group-detail': setViewingMuscleGroupId(data.muscleGroupId); break;
                case 'body-part-detail': setViewingBodyPartId(data.bodyPartId); break;
                case 'chain-detail': setViewingChainId(data.chainId); break;
                case 'muscle-category': setViewingMuscleCategoryName(data.categoryName); break;
                case 'food-detail': setViewingFoodId(data.foodId); break;
            }
        }
        setView(newView);
    }, [setHistoryStack, setView, setActiveProgramId, setEditingProgramId, setEditingSessionInfo, setLoggingSessionInfo, setViewingSessionInfo, setViewingExerciseId, setViewingMuscleGroupId, setViewingBodyPartId, setViewingChainId, setViewingMuscleCategoryName, setViewingFoodId]);

    const handleBack = useCallback(() => {
        if (historyStack.length <= 1) return;

        const newStack = historyStack.slice(0, -1);
        const previousState = newStack[newStack.length - 1];
        setHistoryStack(newStack);

        setActiveProgramId(null); setEditingProgramId(null); setEditingSessionInfo(null);
        setLoggingSessionInfo(null); setViewingSessionInfo(null); setViewingExerciseId(null); setViewingMuscleGroupId(null);
        setViewingBodyPartId(null); setViewingChainId(null); setViewingMuscleCategoryName(null);
        setViewingFoodId(null);

        if (previousState.data) {
            switch (previousState.view) {
                case 'program-detail': setActiveProgramId(previousState.data.programId); break;
                case 'program-editor': setEditingProgramId(previousState.data.programId || null); break;
                case 'session-editor': setEditingSessionInfo(previousState.data); break;
                case 'log-workout': setLoggingSessionInfo(previousState.data); break;
                case 'session-detail': setViewingSessionInfo(previousState.data); break;
                case 'exercise-detail': setViewingExerciseId(previousState.data.exerciseId); break;
                case 'muscle-group-detail': setViewingMuscleGroupId(previousState.data.muscleGroupId); break;
                case 'body-part-detail': setViewingBodyPartId(previousState.data.bodyPartId); break;
                case 'chain-detail': setViewingChainId(previousState.data.chainId); break;
                case 'muscle-category': setViewingMuscleCategoryName(previousState.data.categoryName); break;
                case 'food-detail': setViewingFoodId(previousState.data.foodId); break;
            }
        }

        setView(previousState.view);
    }, [historyStack, setHistoryStack, setView, setActiveProgramId, setEditingProgramId, setEditingSessionInfo, setLoggingSessionInfo, setViewingSessionInfo, setViewingExerciseId, setViewingMuscleGroupId, setViewingBodyPartId, setViewingChainId, setViewingMuscleCategoryName, setViewingFoodId]);

    // --- INTERCEPTOR DEL BOTÓN NATIVO ATRÁS (ANDROID/IOS) ---
    useEffect(() => {
        const handlePopState = (event: PopStateEvent) => {
            // Prevenimos el comportamiento por defecto de salida
            event.preventDefault();
            
            // Si hay historial en nuestra app, manejamos la navegación interna
            if (historyStack.length > 1) {
                handleBack();
            } else {
                // Si estamos en la raíz (ej. home), podríamos querer preguntar si quiere salir
                // o dejar que Android maneje la salida. Para que no se cierre accidentalmente, 
                // podemos volver a empujar el estado para "atrapar" al usuario en la app.
                window.history.pushState({ view: 'home' }, '');
                if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.WARNING as any);
                addToast("Presiona el botón Inicio para salir.", "suggestion");
            }
        };

        window.addEventListener('popstate', handlePopState);
        return () => window.removeEventListener('popstate', handlePopState);
    }, [handleBack, historyStack.length, settings.hapticFeedbackEnabled, addToast]);

    // CRUD: Programs
    const handleCreateProgram = useCallback(() => navigateTo('program-editor'), [navigateTo]);
    const handleEditProgram = useCallback((programId: string) => navigateTo('program-editor', { programId }), [navigateTo]);

    const handleSaveProgram = useCallback((program: Program) => {
        const newlyCreated = !programs.some(p => p.id === program.id);
        setPrograms(prev => {
            const index = prev.findIndex(p => p.id === program.id);
            if (index > -1) {
                const updated = [...prev];
                updated[index] = program;
                return updated;
            }
            return [...prev, program];
        });
        if (newlyCreated) {
            const unlocked: Achievement[] = checkAndUnlock({ programJustCreated: true });
            if (unlocked.length > 0) addToast(unlocked[0].name, 'achievement', '¡Logro Desbloqueado!');
        }
        navigateTo('program-detail', { programId: program.id }, { replace: true });
        addToast("Programa guardado.", "success");
    }, [programs, setPrograms, checkAndUnlock, addToast, navigateTo]);

    const handleSelectProgram = useCallback((program: Program) => {
        navigateTo('program-detail', { programId: program.id });
    }, [navigateTo]);

    const handleDeleteProgram = useCallback((programId: string) => {
        if (window.confirm('¿Seguro que quieres eliminar este programa? Se perderán todos los datos asociados.')) {
            setPrograms(prev => prev.filter(p => p.id !== programId));
            setHistory(prev => prev.filter(h => h.programId !== programId));
            navigateTo('home', undefined, { replace: true });
        }
    }, [setPrograms, setHistory, navigateTo]);

    // Active Program Management
    const handleStartProgram = useCallback((programId: string) => {
        const program = programs.find(p => p.id === programId);
        if (!program) {
            addToast("No se pudo encontrar el programa para iniciar.", "danger");
            return;
        }
        const firstWeekId = program.macrocycles[0]?.blocks?.[0]?.mesocycles[0]?.weeks[0]?.id;
        if (!firstWeekId) {
            addToast("Este programa no tiene semanas. Añade una para poder iniciarlo.", "danger");
            return;
        }
        setActiveProgramState({
            programId,
            status: 'active',
            startDate: new Date().toISOString(),
            currentMacrocycleIndex: 0,
            currentBlockIndex: 0,
            currentMesocycleIndex: 0,
            currentWeekId: firstWeekId,
        });
        addToast(`¡Programa "${program.name}" iniciado!`, 'success');
    }, [programs, setActiveProgramState, addToast]);

    const handlePauseProgram = useCallback(() => {
        setActiveProgramState(prev => {
            if (prev && prev.status === 'active') {
                addToast("Programa pausado.", "suggestion");
                return { ...prev, status: 'paused' };
            }
            return prev;
        });
    }, [setActiveProgramState, addToast]);

    const handleFinishProgram = useCallback(() => {
        setActiveProgramState(prev => {
            if (prev && prev.status !== 'completed') {
                addToast("¡Programa completado!", "achievement");
                
                // --- AUTO-TRANSICIÓN DE COLA ---
                if (prev.queuedProgramId) {
                    const nextProgram = programs.find(p => p.id === prev.queuedProgramId);
                    if (nextProgram) {
                        addToast(`Iniciando programa en cola: ${nextProgram.name}`, "suggestion");
                        const firstWeekId = nextProgram.macrocycles[0]?.blocks?.[0]?.mesocycles[0]?.weeks[0]?.id;
                        return {
                            programId: prev.queuedProgramId,
                            status: 'active',
                            startDate: new Date().toISOString(),
                            currentMacrocycleIndex: 0,
                            currentBlockIndex: 0,
                            currentMesocycleIndex: 0,
                            currentWeekId: firstWeekId || '',
                            firstSessionDate: undefined,
                            queuedProgramId: undefined
                        };
                    }
                }
                return { ...prev, status: 'completed' };
            }
            return prev;
        });
    }, [setActiveProgramState, addToast, programs]);

    const handleRestartProgram = useCallback(() => {
        if (activeProgramState) {
            if (window.confirm("¿Seguro que quieres reiniciar el progreso de este programa? Se perderá tu posición actual.")) {
                handleStartProgram(activeProgramState.programId);
            }
        }
    }, [activeProgramState, handleStartProgram]);

    // CRUD: Sessions
    const handleAddSession = useCallback((programId: string, macroIndex: number, mesoIndex: number, weekId: string, dayOfWeek?: number) => {
        navigateTo('session-editor', { programId, macroIndex, mesoIndex, weekId, dayOfWeek });
    }, [navigateTo]);

    const handleEditSession = useCallback((programId: string, macroIndex: number, mesoIndex: number, weekId: string, sessionId: string) => {
        navigateTo('session-editor', { programId, macroIndex, mesoIndex, weekId, sessionId });
    }, [navigateTo]);

    const handleUpdateSessionInProgram = useCallback((session: Session, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => {
        setPrograms(prevPrograms => {
            const newPrograms = JSON.parse(JSON.stringify(prevPrograms));
            const program = newPrograms.find((p: Program) => p.id === programId);
            if (!program) return prevPrograms;

            const macro = program.macrocycles[macroIndex];
            if (macro) {
                let mesoCount = 0;
                for (const block of (macro.blocks || [])) {
                    if (mesoIndex < mesoCount + block.mesocycles.length) {
                        const relativeMesoIndex = mesoIndex - mesoCount;
                        const meso = block.mesocycles[relativeMesoIndex];
                        const week = meso.weeks.find((w: ProgramWeek) => w.id === weekId);
                        if (week) {
                            const sessionIndex = week.sessions.findIndex((s: Session) => s.id === session.id);
                            if (sessionIndex > -1) {
                                week.sessions[sessionIndex] = session;
                            } else {
                                week.sessions.push(session);
                            }
                        }
                        return newPrograms;
                    }
                    mesoCount += block.mesocycles.length;
                }
            }
            console.error("Critical error: Path to session not found during update.", { programId, macroIndex, mesoIndex, weekId });
            return prevPrograms;
        });
    }, [setPrograms]);

    const handleSaveSession = useCallback((sessionOrSessions: Session | Session[], programId: string, macroIndex: number, mesoIndex: number, weekId: string) => {
        if (Array.isArray(sessionOrSessions)) {
            sessionOrSessions.forEach(session => {
                handleUpdateSessionInProgram(session, programId, macroIndex, mesoIndex, weekId);
            });
        } else {
            handleUpdateSessionInProgram(sessionOrSessions, programId, macroIndex, mesoIndex, weekId);
        }
        handleBack();
    }, [handleUpdateSessionInProgram, handleBack]);

    const handleDeleteSession = useCallback((sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => {
            setPrograms(prevPrograms => {
                const newPrograms = JSON.parse(JSON.stringify(prevPrograms));
                const program = newPrograms.find((p: Program) => p.id === programId);
                if (!program) return prevPrograms;

                const macro = program.macrocycles[macroIndex];
                if (macro) {
                    let mesoCount = 0;
                    for (const block of (macro.blocks || [])) {
                        if (mesoIndex < mesoCount + block.mesocycles.length) {
                            const relativeMesoIndex = mesoIndex - mesoCount;
                            const meso = block.mesocycles[relativeMesoIndex];
                            const week = meso.weeks.find((w: ProgramWeek) => w.id === weekId);
                            if (week) {
                                week.sessions = week.sessions.filter((s: Session) => s.id !== sessionId);
                            }
                            return newPrograms;
                        }
                        mesoCount += block.mesocycles.length;
                    }
                }
                return newPrograms;
            });
    }, [setPrograms]);

    // WORKOUT LOGIC
    const onCancelWorkout = useCallback(() => {
        if (window.confirm('¿Estás seguro de que quieres cancelar el entrenamiento? No se guardará el progreso.')) {
            setOngoingWorkout(null);
            setActiveSession(null);
            setView('home');
            setHistoryStack([{ view: 'home' }]);
            if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.WARNING as any);
        }
    }, [setOngoingWorkout, setActiveSession, setView, setHistoryStack, settings.hapticFeedbackEnabled]);

    const handleStartWorkout = useCallback(async (session: Session, program: Program, weekVariant?: 'A' | 'B' | 'C' | 'D', location?: { macroIndex: number, mesoIndex: number, weekId: string }, isLowEnergyMental?: boolean) => {
        let sessionToStart = session;
        const isCarpeDiemSession = program.carpeDiemEnabled;

        if (isCarpeDiemSession) {
            addToast("Preparando sesión con tu Coach IA...", "suggestion", "Carpe Diem");
            try {
                const currentWeekId = getWeekId(new Date(), settings.startWeekOn);
                const cacheKey = `carpe_diem_${program.id}_${currentWeekId}`;
                let plan = await cacheService.get<CarpeDiemPlan>(cacheKey);

                if (!plan) {
                    plan = await aiService.generateCarpeDiemWeeklyPlan(program, history, settings, settings.calorieGoalObjective);
                    await cacheService.set(cacheKey, plan);
                }

                const modifiedSessionData = plan.modifiedSessions.find(s => s.id === session.id);
                if (modifiedSessionData) {
                    const originalSessionClone = JSON.parse(JSON.stringify(session));

                    originalSessionClone.exercises = originalSessionClone.exercises.map((originalEx: Exercise) => {
                        const modifiedEx = modifiedSessionData.exercises.find((ex: any) => ex.id === originalEx.id);
                        if (!modifiedEx) return originalEx;

                        const updatedSets = originalEx.sets.map((originalSet: ExerciseSet) => {
                            const modifiedSet = modifiedEx.sets.find((s: any) => s.id === originalSet.id);
                            return modifiedSet ? { ...originalSet, ...modifiedSet } : originalSet;
                        });

                        const newSets = modifiedEx.sets.filter((ms: any) => !originalEx.sets.some((os: ExerciseSet) => os.id === ms.id));

                        return { ...originalEx, ...modifiedEx, sets: [...updatedSets, ...newSets] };
                    });

                    sessionToStart = { ...originalSessionClone, ...modifiedSessionData, exercises: originalSessionClone.exercises };

                    sessionToStart.exercises.forEach((ex: Exercise) => {
                        if (ex.trainingMode === 'percent' && ex.calculated1RM) {
                            ex.sets.forEach((set: ExerciseSet) => {
                                let repsToFailure: number | undefined;
                                const targetReps = set.targetReps;
                                if (targetReps) {
                                    if (set.intensityMode === 'failure') repsToFailure = targetReps;
                                    else if (set.targetRIR !== undefined) repsToFailure = targetReps + set.targetRIR;
                                    else if (set.targetRPE !== undefined) repsToFailure = targetReps + (10 - set.targetRPE);
                                }
                                if (repsToFailure) {
                                    const estimatedPercent = estimatePercent1RM(repsToFailure);
                                    if (estimatedPercent) {
                                        set.targetPercentageRM = estimatedPercent;
                                    }
                                }
                            });
                        }
                    });

                    addToast("¡Sesión adaptada por la IA lista!", "success");
                } else {
                    addToast("No se encontró modificación de IA, usando sesión base.", "suggestion");
                }
            } catch (error) {
                console.error("Fallo del Coach IA (Carpe Diem):", error);
                addToast("Coach IA no disponible. Cargando sesión original.", "suggestion");
            }
        }

        const startWorkoutFlow = (sessionToBegin: Session, readiness?: OngoingWorkoutState['readiness']) => {
            const activeMode = weekVariant || 'A';
            const exercisesForMode = (activeMode === 'A' || !(sessionToBegin as any)[`session${activeMode}`])
                ? (sessionToBegin.parts && sessionToBegin.parts.length > 0 ? sessionToBegin.parts.flatMap(p => p.exercises) : sessionToBegin.exercises)
                : ((sessionToBegin as any)[`session${activeMode}`].parts && (sessionToBegin as any)[`session${activeMode}`].parts.length > 0 ? (sessionToBegin as any)[`session${activeMode}`].parts.flatMap((p: any) => p.exercises) : (sessionToBegin as any)[`session${activeMode}`].exercises);

            const newState: OngoingWorkoutState = {
                programId: program.id,
                session: sessionToBegin,
                startTime: Date.now(),
                activeExerciseId: exercisesForMode?.[0]?.id || null,
                activeSetId: exercisesForMode?.[0]?.sets?.[0]?.id || null,
                activeMode: activeMode,
                completedSets: {},
                dynamicWeights: {},
                exerciseFeedback: {},
                unilateralImbalances: {},
                readiness,
                isCarpeDiem: isCarpeDiemSession,
                macroIndex: location?.macroIndex,
                mesoIndex: location?.mesoIndex,
                weekId: location?.weekId,
                isLowEnergyMental: isLowEnergyMental || false,
            };
            setOngoingWorkout(newState);
            setActiveSession(sessionToBegin);
            navigateTo('workout');
            if (settings.hapticFeedbackEnabled) hapticImpact(ImpactStyle.Heavy as any);
        };

        setPendingWorkoutAfterBriefing(null);

        if (settings.readinessCheckEnabled && !ongoingWorkout) {
            setPendingWorkoutForReadinessCheck({ session: sessionToStart, program, weekVariant, location });
            setIsReadinessModalOpen(true);
        } else {
            startWorkoutFlow(sessionToStart, ongoingWorkout?.readiness);
        }
    }, [addToast, settings, history, setOngoingWorkout, setActiveSession, navigateTo, ongoingWorkout, setPendingWorkoutForReadinessCheck, setIsReadinessModalOpen]);

    const handleUpdateExerciseRepDebt = useCallback((exerciseDbId: string, debtUpdate: Record<string, number>) => {
        setExerciseList(prev => prev.map(ex => {
            if (ex.id === exerciseDbId) {
                return {
                    ...ex,
                    repDebtHistory: {
                        ...(ex.repDebtHistory || {}),
                        ...debtUpdate
                    }
                };
            }
            return ex;
        }));
    }, [setExerciseList]);

    const handleContinueFromReadiness = useCallback((data: any) => {
        if (pendingWorkoutForReadinessCheck) {
            const { session, program, weekVariant, location } = pendingWorkoutForReadinessCheck;
            const readinessScore = Math.round(((data.sleepQuality + (6 - data.stressLevel) + (6 - data.doms) + data.motivation) / 20) * 100);
            const readinessData = { ...data, readinessScore };

            const start = () => {
                const activeMode = weekVariant || 'A';
                const exercisesForMode = (activeMode === 'A' || !(session as any)[`session${activeMode}`])
                    ? (session.parts && session.parts.length > 0 ? session.parts.flatMap(p => p.exercises) : session.exercises)
                    : ((session as any)[`session${activeMode}`].parts && (session as any)[`session${activeMode}`].parts.length > 0 ? (session as any)[`session${activeMode}`].parts.flatMap((p: any) => p.exercises) : (session as any)[`session${activeMode}`].exercises);

                const newState: OngoingWorkoutState = {
                    programId: program.id,
                    session,
                    startTime: Date.now(),
                    activeExerciseId: exercisesForMode?.[0]?.id || null,
                    activeSetId: exercisesForMode?.[0]?.sets?.[0]?.id || null,
                    activeMode: activeMode,
                    completedSets: {},
                    dynamicWeights: {},
                    exerciseFeedback: {},
                    unilateralImbalances: {},
                    readiness: readinessData,
                    isCarpeDiem: program.carpeDiemEnabled,
                    macroIndex: location?.macroIndex,
                    mesoIndex: location?.mesoIndex,
                    weekId: location?.weekId,
                };
                setOngoingWorkout(newState);
                setActiveSession(session);
                navigateTo('workout');
                if (settings.hapticFeedbackEnabled) hapticImpact(ImpactStyle.Heavy as any);
            };
            start();
        }
        setIsReadinessModalOpen(false);
        setPendingWorkoutForReadinessCheck(null);
    }, [pendingWorkoutForReadinessCheck, setOngoingWorkout, setActiveSession, navigateTo, settings.hapticFeedbackEnabled]);

    const handleContinueWorkoutAfterBriefing = useCallback(() => {
        if (!pendingWorkoutAfterBriefing) return;

        const { session, program, weekVariant, location } = pendingWorkoutAfterBriefing;
        const readinessData = ongoingWorkout?.readiness;
        if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.WARNING as any);
        const activeMode = weekVariant || 'A';
        const exercisesForMode = (activeMode === 'A' || !(session as any)[`session${activeMode}`])
            ? (session.parts && session.parts.length > 0 ? session.parts.flatMap(p => p.exercises) : session.exercises)
            : ((session as any)[`session${activeMode}`].parts && (session as any)[`session${activeMode}`].parts.length > 0 ? (session as any)[`session${activeMode}`].parts.flatMap((p: any) => p.exercises) : (session as any)[`session${activeMode}`].exercises);

        const newState: OngoingWorkoutState = {
            programId: program.id,
            session: session,
            startTime: Date.now(),
            activeExerciseId: exercisesForMode[0]?.id || null,
            activeSetId: exercisesForMode[0]?.sets[0]?.id || null,
            activeMode: activeMode,
            completedSets: {},
            dynamicWeights: {},
            exerciseFeedback: {},
            unilateralImbalances: {},
            readiness: readinessData,
            isCarpeDiem: program.carpeDiemEnabled,
            macroIndex: location?.macroIndex,
            mesoIndex: location?.mesoIndex,
            weekId: location?.weekId,
        };

        setOngoingWorkout(newState);
        setActiveSession(session);
        navigateTo('workout');
        if (settings.hapticFeedbackEnabled) hapticImpact(ImpactStyle.Heavy as any);

        setPendingWorkoutAfterBriefing(null);
        setPendingCoachBriefing(null);
    }, [pendingWorkoutAfterBriefing, ongoingWorkout, setOngoingWorkout, setActiveSession, navigateTo, settings.hapticFeedbackEnabled]);

    const handleFinishWorkout = useCallback((
        completedExercises: CompletedExercise[],
        duration: number,
        notes?: string,
        discomforts?: string[],
        fatigue?: number,
        clarity?: number,
        logDate?: string,
        photoUri?: string,
        planDeviations?: PlanDeviation[],
        focus?: number,
        pump?: number,
        environmentTags?: string[],
        sessionDifficulty?: number,
        planAdherenceTags?: string[]
    ) => {
        if (!ongoingWorkout) return;

        const logId = crypto.randomUUID();
        const sessionStressScore = calculateCompletedSessionStress(completedExercises, exerciseList);

        // --- SISTEMA AUGE: TRIGGER DE PROTOCOLO DE COMPENSACIÓN (PCE) INTERACTIVO ---
        if (sessionStressScore > 200) {
            let avgWakeHour = 7;
            let avgWakeMinute = 0;
            if (sleepLogs && sleepLogs.length > 0) {
                const recentLogs = sleepLogs.slice(0, 5);
                const wakeTimes = recentLogs.map(log => new Date(log.endTime));
                const totalMinutes = wakeTimes.reduce((acc, d) => acc + (d.getHours() * 60 + d.getMinutes()), 0);
                const avgMinutesTotal = Math.round(totalMinutes / wakeTimes.length);
                avgWakeHour = Math.floor(avgMinutesTotal / 60);
                avgWakeMinute = avgMinutesTotal % 60;
            }

            let suggestedSleepHour = (avgWakeHour - 9 + 24) % 24;
            const pad = (n: number) => n.toString().padStart(2, '0');
            const suggestedSleepTime = `${pad(suggestedSleepHour)}:${pad(avgWakeMinute)}`;
            const wakeTimeStr = `${pad(avgWakeHour)}:${pad(avgWakeMinute)}`;

            const pceData = {
                score: Math.round(sessionStressScore),
                suggestedCalories: settings.dailyCalorieGoal ? settings.dailyCalorieGoal + 350 : null,
                suggestedSleepTime,
                wakeTimeStr,
                isExtreme: sessionStressScore > 350
            };

            setTimeout(() => {
                window.dispatchEvent(new CustomEvent('auge-pce-triggered', { detail: pceData }));
            }, 1500);
        }

        const newLog: Omit<WorkoutLog, 'id'> & { id: string } = {
            id: logId,
            programId: ongoingWorkout.programId,
            programName: programs.find(p => p.id === ongoingWorkout.programId)?.name || 'Unknown',
            sessionId: ongoingWorkout.session.id,
            sessionName: ongoingWorkout.session.name,
            date: safeCreateISOStringFromDateInput(logDate),
            duration,
            completedExercises,
            notes,
            discomforts,
            fatigueLevel: fatigue || 5,
            mentalClarity: clarity || 5,
            gymName: settings.gymName,
            photoUri: photoUri,
            sessionVariant: ongoingWorkout.activeMode,
            planDeviations: planDeviations,
            readiness: ongoingWorkout.readiness,
            focus,
            pump,
            environmentTags,
            sessionDifficulty,
            planAdherenceTags,
            sessionStressScore,
        };

        const validationResult = WorkoutLogSchema.safeParse(newLog);

        if (!validationResult.success) {
            console.error("Error de validación al guardar WorkoutLog:", validationResult.error.flatten());
            addToast("Error al guardar el entrenamiento. Datos inválidos detectados.", "danger");
            return;
        }

        const validatedLog = validationResult.data;

        setOngoingWorkout(null);
        setActiveSession(null);
        setIsFinishModalOpen(false);

        const saveLog = (log: WorkoutLog) => {
            const newHistory = [...history, log];
            setHistory(newHistory);
            try {
                const unlocked = checkAndUnlock({ log: validatedLog, history: newHistory });
                if (unlocked.length > 0) {
                    unlocked.forEach(ach => addToast(ach.name, 'achievement', '¡Logro Desbloqueado!'));
                }
            } catch (e) {
                console.error("Error checking achievements, but log was saved:", e);
            }
        };

        if (!isOnline) {
            setSyncQueue(prev => [...prev, validatedLog]);
            addToast('Estás sin conexión. El entrenamiento se guardará en la cola de sincronización.', 'suggestion');
        } else {
            saveLog(validatedLog);
        }

        completedExercises.forEach(ex => {
            if (!ex.exerciseDbId) return;
            const exerciseInfo = exerciseList.find(e => e.id === ex.exerciseDbId);
            if (!exerciseInfo) return;

            const debtUpdate: Record<string, number> = {};
            const originalExercise = ongoingWorkout?.session.exercises.find(e => e.id === ex.exerciseId);
            if (!originalExercise) return;

            originalExercise.sets.forEach(originalSet => {
                const debtContextKey = getRepDebtContextKey(originalSet);
                const historicalDebt = (exerciseInfo.repDebtHistory || {})[debtContextKey] || 0;

                const completedSet = ex.sets.find(cs => cs.id.startsWith(originalSet.id));
                const completedReps = completedSet?.completedReps;
                const targetReps = originalSet.targetReps;

                if (typeof completedReps === 'number' && typeof targetReps === 'number') {
                    const debtChange = completedReps - targetReps;
                    const newDebt = historicalDebt + debtChange;
                    debtUpdate[debtContextKey] = newDebt;
                }
            });

            if (Object.keys(debtUpdate).length > 0) {
                handleUpdateExerciseRepDebt(ex.exerciseDbId, debtUpdate);
            }
        });

        const finishedProgram = programs.find(p => p.id === ongoingWorkout.programId);
        if (finishedProgram?.carpeDiemEnabled) {
            const weekId = getWeekId(new Date(), settings.startWeekOn);
            cacheService.remove(`carpe_diem_${finishedProgram.id}_${weekId}`);
        }

        // --- ESTAMPADO DE CALENDARIO (Motor Avanzado) ---
        // Sella la fecha de la primera sesión si aún no se ha hecho
        setActiveProgramState(prev => {
            if (prev && prev.programId === ongoingWorkout.programId && !prev.firstSessionDate) {
                return { ...prev, firstSessionDate: validatedLog.date };
            }
            return prev;
        });

        navigateTo('home', undefined, { replace: true });
        playSound('session-complete-sound');
        if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.SUCCESS as any);

        navigateTo('home', undefined, { replace: true });
        playSound('session-complete-sound');
        if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.SUCCESS as any);
    }, [ongoingWorkout, programs, settings, history, setHistory, setOngoingWorkout, setActiveSession, setIsFinishModalOpen, checkAndUnlock, addToast, isOnline, setSyncQueue, exerciseList, handleUpdateExerciseRepDebt, navigateTo, sleepLogs]);

    const handleSaveLoggedWorkout = useCallback((log: WorkoutLog) => {
        const newHistory = [...history, log];
        setHistory(newHistory);
        try {
            const unlocked = checkAndUnlock({ log, history: newHistory });
            if (unlocked.length > 0) {
                unlocked.forEach(ach => addToast(ach.name, 'achievement', '¡Logro Desbloqueado!'));
            }
        } catch (e) {
            console.error("Error checking achievements, but log was saved:", e);
        }
        handleBack();
        addToast("Entrenamiento registrado con éxito.", "success");
        if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.SUCCESS as any);
    }, [history, setHistory, checkAndUnlock, handleBack, addToast, settings.hapticFeedbackEnabled]);

    // Other handlers
    const handleUpdateExercise1RM = useCallback((exerciseDbId: string | undefined, exerciseName: string, weight: number, reps: number, testDate?: string, machineBrand?: string) => {
        if (!exerciseDbId) return;
        const new1RM = calculateBrzycki1RM(weight, reps);
        if (new1RM <= 0) return;

        setExerciseList(prevList => {
            const exerciseIndex = prevList.findIndex(ex => ex.id === exerciseDbId);
            if (exerciseIndex === -1) return prevList;

            const exercise = prevList[exerciseIndex];
            const current1RM = exercise.calculated1RM || 0;

            if (new1RM > current1RM) {
                const newList = [...prevList];
                newList[exerciseIndex] = {
                    ...exercise,
                    calculated1RM: new1RM,
                    last1RMTestDate: testDate
                };
                playSound('new-pr-sound');
                hapticNotification(NotificationType.SUCCESS as any);
                addToast(`¡Nuevo PR en ${exerciseName}! ${weight}${settings.weightUnit} x ${reps} reps`, 'achievement');
                return newList;
            }
            return prevList;
        });
    }, [setExerciseList, addToast, settings.weightUnit]);

    const handleUpdateExerciseBrandPR = useCallback((exerciseDbId: string, brand: string, pr: { weight: number, reps: number, e1rm: number }) => {
        setExerciseList(prev => prev.map(ex => {
            if (ex.id === exerciseDbId) {
                const newEquivalencies = [...(ex.brandEquivalencies || [])];
                const brandIndex = newEquivalencies.findIndex(b => b.brand === brand);
                if (brandIndex > -1) {
                    newEquivalencies[brandIndex] = { ...newEquivalencies[brandIndex], pr };
                } else {
                    newEquivalencies.push({ brand, pr });
                }
                return { ...ex, brandEquivalencies: newEquivalencies };
            }
            return ex;
        }));
    }, [setExerciseList]);

    const openCustomExerciseEditor = useCallback((data?: CustomExerciseModalData) => {
        setEditingCustomExerciseData(data || null);
        setIsCustomExerciseEditorOpen(true);
    }, [setEditingCustomExerciseData, setIsCustomExerciseEditorOpen]);

    const closeCustomExerciseEditor = useCallback(() => {
        setIsCustomExerciseEditorOpen(false);
        setEditingCustomExerciseData(null);
    }, [setIsCustomExerciseEditorOpen, setEditingCustomExerciseData]);

    const openFoodEditor = useCallback((data?: { food?: FoodItem; preFilledName?: string }) => {
        setEditingFoodData(data || null);
        setIsFoodEditorOpen(true);
    }, [setEditingFoodData, setIsFoodEditorOpen]);

    const closeFoodEditor = useCallback(() => {
        setIsFoodEditorOpen(false);
        setEditingFoodData(null);
    }, [setIsFoodEditorOpen, setEditingFoodData]);

    const addOrUpdateFoodItem = useCallback((food: FoodItem) => {
        setFoodDatabase(prev => {
            const index = prev.findIndex(f => f.id === food.id);
            if (index > -1) {
                const updated = [...prev];
                updated[index] = food;
                return updated;
            }
            return [...prev, food];
        });
    }, [setFoodDatabase]);

    const openMuscleListEditor = useCallback((categoryName: string, type: 'bodyPart' | 'special') => {
        setEditingCategoryInfo({ name: categoryName, type });
        setIsMuscleListEditorOpen(true);
    }, [setEditingCategoryInfo, setIsMuscleListEditorOpen]);

    const closeMuscleListEditor = useCallback(() => {
        setIsMuscleListEditorOpen(false);
        setEditingCategoryInfo(null);
    }, [setIsMuscleListEditorOpen, setEditingCategoryInfo]);

    const handleStartRest = useCallback((duration: number, exerciseName: string) => {
        if (restTimerInterval.current) clearInterval(restTimerInterval.current);

        const key = Date.now();
        const endTime = Date.now() + duration * 1000;

        setRestTimer({ duration, remaining: duration, key, exerciseName, endTime });

        restTimerInterval.current = window.setInterval(() => {
            setRestTimer(currentTimer => {
                if (currentTimer && currentTimer.key === key) {
                    const newRemaining = Math.max(0, Math.round((currentTimer.endTime - Date.now()) / 1000));

                    if (newRemaining <= 0) {
                        clearInterval(restTimerInterval.current!);
                        if (currentTimer.remaining > 0) {
                            playSound('rest-timer-sound');
                            if (settings.hapticFeedbackEnabled) {
                                hapticNotification(NotificationType.SUCCESS as any);
                                setTimeout(() => hapticImpact(ImpactStyle.Medium as any), 150);
                            }
                        }
                        setTimeout(() => setRestTimer(t => t?.key === key ? null : t), 3000);
                        return { ...currentTimer, remaining: 0 };
                    }
                    return { ...currentTimer, remaining: newRemaining };
                }
                if (!currentTimer) {
                    clearInterval(restTimerInterval.current!);
                }
                return currentTimer;
            });
        }, 250);
    }, [settings.hapticFeedbackEnabled]);

    const handleAdjustRestTimer = useCallback((amountInSeconds: number) => {
        setRestTimer(currentTimer => {
            if (!currentTimer) return null;
            const newEndTime = currentTimer.endTime + (amountInSeconds * 1000);
            const newRemaining = Math.max(0, currentTimer.remaining + amountInSeconds);
            return { ...currentTimer, remaining: newRemaining, endTime: newEndTime };
        });
    }, [setRestTimer]);

    const handleSkipRestTimer = useCallback(() => {
        if (restTimerInterval.current) clearInterval(restTimerInterval.current);
        setRestTimer(null);
    }, [setRestTimer]);

    const handleModifyWorkout = useCallback(() => {
        if (!ongoingWorkout) return;
        const program = programs.find(p => p.id === ongoingWorkout.programId);
        if (!program) return;
        
        // AÑADIDO: Tipado explícito para evitar el error 'any' implícito
        let sessionInfo: { 
            session: Session; 
            programId: string; 
            macroIndex: number; 
            mesoIndex: number; 
            weekId: string; 
        } | undefined;

        program.macrocycles.forEach((macro, macroIndex) => {
            if (sessionInfo) return;
            let mesoCount = 0;
            (macro.blocks || []).forEach(block => {
                if (sessionInfo) return;
                block.mesocycles.forEach((meso, mesoIndex) => {
                    if (sessionInfo) return;
                    meso.weeks.forEach(week => {
                        if (sessionInfo) return;
                        if (week.sessions.some(s => s.id === ongoingWorkout.session.id)) {
                            sessionInfo = { session: ongoingWorkout.session, programId: program.id, macroIndex, mesoIndex: mesoCount + mesoIndex, weekId: week.id };
                        }
                    });
                });
                mesoCount += block.mesocycles.length;
            });
        });
        
        if (sessionInfo) {
            setEditingWorkoutSessionInfo(sessionInfo);
            setIsWorkoutEditorOpen(true);
        } else {
            addToast("No se pudo encontrar la sesión original para modificar.", "danger");
        }
    }, [ongoingWorkout, programs, addToast]);

    const handleSaveModifiedWorkout = useCallback((session: Session) => {
        setOngoingWorkout(prev => prev ? ({ ...prev, session }) : null);
        setIsWorkoutEditorOpen(false);
        setEditingWorkoutSessionInfo(null);
        addToast("Sesión en curso modificada.", "success");
    }, [setOngoingWorkout, setIsWorkoutEditorOpen, setEditingWorkoutSessionInfo, addToast]);

    const handleSaveBodyLog = useCallback((log: BodyProgressLog) => {
        setBodyProgress(prev => [...prev, log]);
        addToast("Registro corporal guardado.", "success");
    }, [setBodyProgress, addToast]);

    const handleSaveNutritionLog = useCallback((log: NutritionLog) => {
        setNutritionLogs(prev => [...prev, log]);
        addToast("Registro de nutrición guardado.", "success");
    }, [setNutritionLogs, addToast]);

    const addOrUpdatePantryItem = useCallback((item: PantryItem) => {
        setPantryItems(prev => {
            const index = prev.findIndex(p => p.id === item.id);
            if (index > -1) {
                const updated = [...prev];
                updated[index] = item;
                return updated;
            }
            return [...prev, item];
        });
    }, [setPantryItems]);

    const openAddPantryItemModal = useCallback((foodItem: FoodItem) => {
        setFoodItemToAdd_to_pantry(foodItem);
        setIsAddPantryItemModalOpen(true);
    }, [setFoodItemToAdd_to_pantry, setIsAddPantryItemModalOpen]);

    const closeAddPantryItemModal = useCallback(() => {
        setIsAddPantryItemModalOpen(false);
        setFoodItemToAdd_to_pantry(null);
    }, [setIsAddPantryItemModalOpen, setFoodItemToAdd_to_pantry]);

    const handleCopySessionsToMeso = useCallback((programId: string, macroIndex: number, mesoIndex: number) => {
        setPrograms(prev => {
            const newPrograms = JSON.parse(JSON.stringify(prev));
            const program = newPrograms.find((p: Program) => p.id === programId);
            if (!program) return prev;
            // Implementation of copying sessions logic
            return newPrograms;
        });
    }, [setPrograms]);

    const handleBiomechanicalDataUpdate = useCallback(async (data: BiomechanicalData) => {
        _setBiomechanicalData(data);
        if (isOnline) {
            try {
                const analysis = await aiService.generateBiomechanicalAnalysis(data, [], settings);
                _setBiomechanicalAnalysis(analysis);
            } catch (e) {
                console.error(e);
            }
        }
    }, [_setBiomechanicalData, _setBiomechanicalAnalysis, isOnline, settings]);

    const setBodyLabAnalysis = useCallback((analysis: BodyLabAnalysis | null) => {
        _setBodyLabAnalysis(analysis);
    }, [_setBodyLabAnalysis]);

    // --- 4. VALORES DEL CONTEXTO ---
    const stateValue: AppContextState = {
        view, historyStack, programs, history, skippedLogs, settings, bodyProgress, nutritionLogs, pantryItems, tasks,
        exercisePlaylists, muscleGroupData, muscleHierarchy, exerciseList, foodDatabase, unlockedAchievements,
        isOnline, isAppLoading, installPromptEvent, drive, toasts, bodyLabAnalysis, biomechanicalData, biomechanicalAnalysis,
        syncQueue, aiNutritionPlan, activeProgramState, onExerciseCreated,
        pendingQuestionnaires,
        postSessionFeedback,
        dailyWellbeingLogs,
        isBodyLogModalOpen, isNutritionLogModalOpen, isMeasurementsModalOpen, isStartWorkoutModalOpen,
        isCustomExerciseEditorOpen, isFoodEditorOpen, isFinishModalOpen, isTimeSaverModalOpen,
        isTimersModalOpen, isReadinessModalOpen, isAddToPlaylistSheetOpen, isWorkoutEditorOpen,
        isMuscleListEditorOpen, isLiveCoachActive, isLogActionSheetOpen, isWorkoutExitModalOpen, isAddPantryItemModalOpen,
        isSpecialSessionModalOpen, specialSessionData,
        activeProgramId, editingProgramId, editingSessionInfo, activeSession,
        loggingSessionInfo, viewingSessionInfo, viewingExerciseId, viewingFoodId,
        viewingMuscleGroupId, viewingBodyPartId, viewingChainId, viewingMuscleCategoryName,
        exerciseToAddId, foodItemToAdd_to_pantry, ongoingWorkout, editingCustomExerciseData,
        editingFoodData, pendingWorkoutForReadinessCheck, editingWorkoutSessionInfo,
        editingCategoryInfo, pendingNavigation, saveSessionTrigger, addExerciseTrigger,
        saveProgramTrigger, saveLoggedWorkoutTrigger, modifyWorkoutTrigger, searchQuery,
        activeSubTabs, currentBackgroundOverride, restTimer, isDirty, kpknAction,
        pendingCoachBriefing, pendingWorkoutAfterBriefing,
        sleepLogs,
        sleepStartTime,
        isGlobalVoiceActive,
        recommendationTriggers,
        waterLogs,
        isMenuOpen,
    };

    const dispatchValue: AppContextDispatch = {
        setPrograms, setHistory, setSkippedLogs, setSettings, setBodyProgress, setNutritionLogs, setPantryItems,
        addOrUpdatePantryItem, setTasks, addTask, addAITasks, toggleTask, deleteTask,
        setExercisePlaylists, addOrUpdatePlaylist: (playlist) => setExercisePlaylists(prev => {
            const index = prev.findIndex(p => p.id === playlist.id);
            if (index > -1) {
                const updated = [...prev];
                updated[index] = playlist;
                return updated;
            }
            return [...prev, playlist];
        }),
        deletePlaylist: (playlistId) => setExercisePlaylists(prev => prev.filter(p => p.id !== playlistId)),
        setMuscleGroupData, updateMuscleGroupInfo: (id, data) => setMuscleGroupData(prev => prev.map(m => m.id === id ? { ...m, ...data } : m)),
        setMuscleHierarchy,
        renameMuscleCategory: () => { }, renameMuscleGroup: () => { },
        updateCategoryMuscles: (categoryName, newMuscles, type) => setMuscleHierarchy(prev => {
            const newHierarchy = JSON.parse(JSON.stringify(prev));
            if (type === 'bodyPart') newHierarchy.bodyPartHierarchy[categoryName] = newMuscles;
            else newHierarchy.specialCategories[categoryName] = newMuscles;
            return newHierarchy;
        }),
        setBodyLabAnalysis, setBiomechanicalData: handleBiomechanicalDataUpdate, setBiomechanicalAnalysis: _setBiomechanicalAnalysis,
        setAiNutritionPlan, setActiveProgramState, setOnExerciseCreated, setInstallPromptEvent,
        setIsBodyLogModalOpen, setIsNutritionLogModalOpen, setIsMeasurementsModalOpen,
        setIsStartWorkoutModalOpen, setIsFinishModalOpen, setIsTimeSaverModalOpen,
        setIsTimersModalOpen, setIsReadinessModalOpen, setIsAddToPlaylistSheetOpen,
        setIsLiveCoachActive, setIsLogActionSheetOpen, openCustomExerciseEditor,
        closeCustomExerciseEditor, openFoodEditor, closeFoodEditor, openAddPantryItemModal,
        closeAddPantryItemModal, openMuscleListEditor, closeMuscleListEditor,
        setIsWorkoutExitModalOpen, setPendingNavigation, setExerciseToAddId,
        setPendingWorkoutForReadinessCheck, setSaveSessionTrigger, setAddExerciseTrigger,
        setSaveProgramTrigger, setSaveLoggedWorkoutTrigger, setModifyWorkoutTrigger,
        setSearchQuery, setActiveSubTabs, setCurrentBackgroundOverride, setOngoingWorkout,
        navigateTo, handleBack, addToast, removeToast, setPendingCoachBriefing,
        setPendingWorkoutAfterBriefing, handleCreateProgram, handleEditProgram,
        handleSelectProgram: (p) => navigateTo('program-detail', { programId: p.id }),
        handleSaveProgram, handleUpdateProgram: handleSaveProgram, handleDeleteProgram,
        handleAddSession, handleEditSession, handleSaveSession, handleUpdateSessionInProgram,
        handleDeleteSession, handleCopySessionsToMeso, handleStartProgram, handlePauseProgram,
        handleFinishProgram, handleRestartProgram, handleStartWorkout,
        handleResumeWorkout: () => navigateTo('workout'), handleContinueFromReadiness,
        handleContinueWorkoutAfterBriefing, onCancelWorkout, handlePauseWorkout: () => navigateTo('home'),
        handleFinishWorkout, handleLogWorkout: (programId, sessionId) => navigateTo('log-workout', { programId, sessionId }),
        handleSaveLoggedWorkout, handleSkipWorkout: (session, program, reason, notes) => setSkippedLogs(prev => [...prev, { id: crypto.randomUUID(), date: new Date().toISOString(), programId: program.id, sessionId: session.id, sessionName: session.name, programName: program.name, reason, notes }]),
        handleSaveBodyLog, handleSaveNutritionLog, addOrUpdateFoodItem,
        handleUpdateExerciseInProgram: (programId, sessionId, exerciseId, updatedExercise) => {
            setPrograms(prev => {
                const newPrograms = JSON.parse(JSON.stringify(prev));
                for (const prog of newPrograms) {
                    if (prog.id === programId) {
                        for (const macro of prog.macrocycles) {
                            for (const block of (macro.blocks || [])) {
                                for (const meso of block.mesocycles) {
                                    for (const week of meso.weeks) {
                                        for (const session of week.sessions) {
                                            if (session.id === sessionId) {
                                                const exIndex = session.exercises.findIndex((ex: Exercise) => ex.id === exerciseId);
                                                if (exIndex > -1) {
                                                    session.exercises[exIndex] = updatedExercise;
                                                    return newPrograms;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return prev;
            });
        },
        handleUpdateProgressionWeights: (exerciseId, consolidated, technical) => {
            setOngoingWorkout(prev => {
                if (!prev) return null;
                return { ...prev, dynamicWeights: { ...prev.dynamicWeights, [exerciseId]: { consolidated, technical } } };
            });
        },
        handleUpdateExercise1RM, handleUpdateExerciseBrandPR, handleUpdateExerciseRepDebt,
        handleStartRest, handleAdjustRestTimer, handleSkipRestTimer,
        handleLogPress: () => setIsLogActionSheetOpen(true),
        addOrUpdateCustomExercise, batchAddExercises, createAndAddExerciseToDB, setExerciseList,
        exportExerciseDatabase, importExerciseDatabase, setIsDirty, handleModifyWorkout,
        handleSaveModifiedWorkout, setIsWorkoutEditorOpen, setKpknaction,
        handleLogSleep,
        setSleepLogs,
        handleSavePostSessionFeedback,
        setIsGlobalVoiceActive,
        addRecommendationTrigger,
        markRecommendationAsTaken,
        setWaterLogs,
        handleLogWater,
        setIsMenuOpen,
        handleLogDailyWellbeing,
        setIsSpecialSessionModalOpen,
        setSpecialSessionData
    };

    return (
        <AppStateContext.Provider value={stateValue}>
            <AppDispatchContext.Provider value={dispatchValue}>
                {children}
            </AppDispatchContext.Provider>
        </AppStateContext.Provider>
    );
};

export const useAppState = (): AppContextState => {
    const context = useContext(AppStateContext);
    if (context === undefined) throw new Error('useAppState must be used within an AppProvider');
    return context;
};

export const useAppDispatch = (): AppContextDispatch => {
    const context = useContext(AppDispatchContext);
    if (context === undefined) throw new Error('useAppDispatch must be used within an AppProvider');
    return context;
};

export const useAppContext = (): AppContextState & AppContextDispatch => {
    return { ...useAppState(), ...useAppDispatch() };
};