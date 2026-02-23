// AppContext.tsx — Thin bridge over Zustand stores (migration layer)
// Components keep using useAppState/useAppDispatch; internally state lives in Zustand stores.
import React, { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
    AppContextState, AppContextDispatch, View, Program, Session, WorkoutLog, SkippedWorkoutLog,
    BodyProgressLog, NutritionLog, Settings, ExerciseMuscleInfo, OngoingWorkoutState,
    ToastData, CompletedExercise, Exercise, CustomExerciseModalData, ExercisePlaylist,
    MuscleGroupInfo, MuscleHierarchy, PlanDeviation, SessionBackground, ProgramWeek, Achievement,
    BodyLabAnalysis, BiomechanicalData, BiomechanicalAnalysis, Task, PantryItem, CarpeDiemPlan,
    ExerciseSet, Block, Mesocycle, AINutritionPlan, ActiveProgramState, WorkoutLogSchema, FoodItem,
    SleepLog, PostSessionFeedback, PendingQuestionnaire, RecommendationTrigger, WaterLog, DailyWellbeingLog
} from '../types';
import { z } from 'zod';

// --- Zustand Stores ---
import { useSettingsStore } from '../stores/settingsStore';
import { useProgramStore } from '../stores/programStore';
import { useWorkoutStore } from '../stores/workoutStore';
import { useBodyStore } from '../stores/bodyStore';
import { useNutritionStore } from '../stores/nutritionStore';
import { useWellbeingStore } from '../stores/wellbeingStore';
import { useExerciseStore } from '../stores/exerciseStore';
import { useUIStore } from '../stores/uiStore';

// --- Legacy hooks (still needed for cross-cutting concerns) ---
import useAchievements from '../hooks/useAchievements';
import useGoogleDrive from '../hooks/useGoogleDrive';

import { playSound } from '../services/soundService';
import { hapticImpact, ImpactStyle, hapticNotification, NotificationType } from '../services/hapticsService';
import { routerNavigate, routerBack } from '../routes/navigation';
import * as aiService from '../services/aiService';
import { getWeekId, estimatePercent1RM, getRepDebtContextKey, calculateBrzycki1RM } from '../utils/calculations';
import { cacheService } from '../services/cacheService';
import { calculateCompletedSessionStress, calculateCompletedSessionDrainBreakdown } from '../services/auge';
import { queueFatigueDataPoint, queueTrainingImpulse } from '../services/augeAdaptiveService';
import { UIProvider, UIState, UIDispatch } from './UIContext';
import { JOINT_DATABASE } from '../data/jointDatabase';
import { TENDON_DATABASE } from '../data/tendonDatabase';
import { MOVEMENT_PATTERN_DATABASE } from '../data/movementPatternDatabase';

const AppStateContext = createContext<AppContextState | undefined>(undefined);
const AppDispatchContext = createContext<AppContextDispatch | undefined>(undefined);

const safeCreateISOStringFromDateInput = (dateString?: string): string => {
    if (dateString && /^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
        return new Date(dateString + 'T12:00:00Z').toISOString();
    }
    return new Date().toISOString();
};

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    // ═══════════════════════════════════════════════════════════
    // 1. SUBSCRIBE TO ZUSTAND STORES (replaces useState/useLocalStorage)
    // ═══════════════════════════════════════════════════════════
    const { settings, setSettings, replaceSettings } = useSettingsStore();
    const { programs, activeProgramState, setPrograms, setActiveProgramState } = useProgramStore();
    const { history, skippedLogs, ongoingWorkout, syncQueue, setHistory, setSkippedLogs, setOngoingWorkout, setSyncQueue } = useWorkoutStore();
    const { bodyProgress, bodyLabAnalysis, biomechanicalData, biomechanicalAnalysis, setBodyProgress, setBodyLabAnalysis: _setBodyLabAnalysis, setBiomechanicalData: _setBiomechanicalDataDirect, setBiomechanicalAnalysis: _setBiomechanicalAnalysis } = useBodyStore();
    const { nutritionLogs, pantryItems, foodDatabase, aiNutritionPlan, setNutritionLogs, setPantryItems, setFoodDatabase, setAiNutritionPlan } = useNutritionStore();
    const { sleepLogs, sleepStartTime, waterLogs, dailyWellbeingLogs, postSessionFeedback, pendingQuestionnaires, recommendationTriggers, tasks, setSleepLogs, setSleepStartTime, setWaterLogs, setDailyWellbeingLogs, setPostSessionFeedback, setPendingQuestionnaires, setRecommendationTriggers, setTasks } = useWellbeingStore();
    const { exerciseList, exercisePlaylists, muscleGroupData, muscleHierarchy, setExerciseList, setExercisePlaylists, setMuscleGroupData, setMuscleHierarchy, addOrUpdateCustomExercise } = useExerciseStore();

    const ui = useUIStore();

    // Hydration check
    const isSettingsHydrated = useSettingsStore(s => s._hasHydrated);
    const isProgramsHydrated = useProgramStore(s => s._hasHydrated);
    const isWorkoutHydrated = useWorkoutStore(s => s._hasHydrated);
    const isBodyHydrated = useBodyStore(s => s._hasHydrated);
    const isNutritionHydrated = useNutritionStore(s => s._hasHydrated);
    const isWellbeingHydrated = useWellbeingStore(s => s._hasHydrated);
    const isExerciseHydrated = useExerciseStore(s => s._hasHydrated);

    const isAppLoading = !isSettingsHydrated || !isProgramsHydrated || !isWorkoutHydrated || !isBodyHydrated || !isNutritionHydrated || !isWellbeingHydrated || !isExerciseHydrated;

    // ═══════════════════════════════════════════════════════════
    // 2. LEGACY HOOKS (achievements, google drive)
    // ═══════════════════════════════════════════════════════════
    const { unlockedAchievements, checkAndUnlock } = useAchievements();

    const drive = useGoogleDrive({
        settings,
        addToast: (msg, type, title, duration) => ui.addToast(msg, type, title, duration),
        onLoad: (data: any) => {
            if (data.programs && data.programs.length > 0) setPrograms(data.programs);
            if (data.history && data.history.length > 0) setHistory(data.history);
            if (data.settings && Object.keys(data.settings).length > 0) setSettings(data.settings);
            if (data['body-progress'] && data['body-progress'].length > 0) setBodyProgress(data['body-progress']);
            ui.addToast('Datos sincronizados desde la nube.', 'success');
        }
    });

    // ═══════════════════════════════════════════════════════════
    // 3. EFFECTS
    // ═══════════════════════════════════════════════════════════
    useEffect(() => {
        if (window.history.state === null) {
            routerNavigate('home', undefined, true);
        }
    }, []);

    useEffect(() => {
        const handleOnline = () => ui.setIsOnline(true);
        const handleOffline = () => ui.setIsOnline(false);
        window.addEventListener('online', handleOnline);
        window.addEventListener('offline', handleOffline);
        return () => {
            window.removeEventListener('online', handleOnline);
            window.removeEventListener('offline', handleOffline);
        };
    }, []);

    // ═══════════════════════════════════════════════════════════
    // 4. CROSS-STORE CALLBACKS
    // ═══════════════════════════════════════════════════════════

    const addToast = useCallback((message: string, type: ToastData['type'] = 'success', title?: string, duration?: number) => {
        ui.addToast(message, type, title, duration);
    }, []);

    const removeToast = useCallback((id: number) => {
        ui.removeToast(id);
    }, []);

    const addRecommendationTrigger = useCallback((trigger: Omit<RecommendationTrigger, 'id' | 'date' | 'actionTaken'>) => {
        const newTrigger: RecommendationTrigger = { ...trigger, id: crypto.randomUUID(), date: new Date().toISOString(), actionTaken: false };
        setRecommendationTriggers(prev => [newTrigger, ...prev]);
    }, [setRecommendationTriggers]);

    const markRecommendationAsTaken = useCallback((id: string) => {
        setRecommendationTriggers(prev => prev.map(t => t.id === id ? { ...t, actionTaken: true } : t));
    }, [setRecommendationTriggers]);

    const handleLogSleep = useCallback((action: 'start' | 'end') => {
        if (action === 'start') {
            setSleepStartTime(Date.now());
            addToast("Dulces sueños...", 'suggestion');
        } else {
            if (sleepStartTime) {
                const endTime = Date.now();
                const durationHours = (endTime - sleepStartTime) / (1000 * 60 * 60);
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

    const handleLogWater = useCallback((amountMl: number) => {
        setWaterLogs(prev => [...prev, { id: crypto.randomUUID(), date: new Date().toISOString(), amountMl }]);
        addToast(`Añadidos ${amountMl}ml de agua.`, "success");
    }, [setWaterLogs, addToast]);

    const handleLogDailyWellbeing = useCallback((data: Omit<DailyWellbeingLog, 'id'>) => {
        const newLog: DailyWellbeingLog = { ...data, id: crypto.randomUUID() };
        setDailyWellbeingLogs(prev => {
            const filtered = prev.filter(l => l.date !== data.date);
            return [...filtered, newLog];
        });
        addToast("Bienestar diario registrado.", "success");
    }, [setDailyWellbeingLogs, addToast]);

    const addTask = useCallback((task: Omit<Task, 'id' | 'completed' | 'generatedBy'>) => {
        setTasks(prev => [{ id: crypto.randomUUID(), completed: false, generatedBy: 'user' as const, ...task }, ...prev]);
    }, [setTasks]);

    const addAITasks = useCallback((newTasks: Omit<Task, 'id' | 'completed'>[]) => {
        const tasks: Task[] = newTasks.map(t => ({ ...t, id: crypto.randomUUID(), completed: false }));
        setTasks(prev => [...tasks, ...prev]);
    }, [setTasks]);

    const toggleTask = useCallback((taskId: string) => {
        setTasks(prev => prev.map(task =>
            task.id === taskId ? { ...task, completed: !task.completed, completedDate: !task.completed ? new Date().toISOString() : undefined } : task
        ));
    }, [setTasks]);

    const deleteTask = useCallback((taskId: string) => {
        setTasks(prev => prev.filter(task => task.id !== taskId));
    }, [setTasks]);

    const createAndAddExerciseToDB = useCallback(async (exerciseName: string): Promise<ExerciseMuscleInfo | null> => {
        addToast(`Creando "${exerciseName}" en KPKN...`, "suggestion", "IA en Progreso");
        try {
            const populatedData = await aiService.createAndPopulateExercise(exerciseName, settings);
            const newExercise: ExerciseMuscleInfo = {
                id: `custom_${crypto.randomUUID()}`, name: exerciseName, description: '', involvedMuscles: [],
                category: 'Hipertrofia', type: 'Accesorio', equipment: 'Otro', force: 'Otro',
                ...populatedData, isCustom: true,
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
            const nonDuplicates = newExercises.filter(newEx => !existingNames.has(newEx.name.toLowerCase()));
            if (nonDuplicates.length > 0) addToast(`${nonDuplicates.length} nuevos ejercicios añadidos a KPKN!`, 'success');
            else if (newExercises.length > 0) addToast('Todos los ejercicios sugeridos ya existen en tu base de datos.', 'suggestion');
            return [...prev, ...nonDuplicates];
        });
    }, [setExerciseList, addToast]);

    const exportExerciseDatabase = useCallback(() => {
        try {
            const jsonString = `data:text/json;charset=utf-8,${encodeURIComponent(JSON.stringify(exerciseList, null, 2))}`;
            const link = document.createElement("a");
            link.href = jsonString;
            link.download = `yourprime_exercisedb_${new Date().toISOString().split('T')[0]}.json`;
            link.click();
        } catch (error) {
            alert('Error al exportar la base de datos de ejercicios.');
            console.error(error);
        }
    }, [exerciseList]);

    const importExerciseDatabase = useCallback((jsonString: string) => {
        try {
            const importedData = JSON.parse(jsonString);
            if (!Array.isArray(importedData) || !importedData.every(item => typeof item === 'object' && item.name && item.id)) {
                throw new Error("El archivo no tiene el formato de base de datos de ejercicios correcto.");
            }
            if (window.confirm("¿Estás seguro de que quieres reemplazar tu base de datos de ejercicios actual con la del archivo?")) {
                setExerciseList(importedData as ExerciseMuscleInfo[]);
                alert('Base de datos importada con éxito. La app se recargará.');
                setTimeout(() => window.location.reload(), 500);
            }
        } catch (error) {
            alert(`Error al importar: ${error instanceof Error ? error.message : 'Error desconocido'}`);
        }
    }, [setExerciseList]);

    // NAVIGATION
    const navigateTo = useCallback((newView: View, data?: any, options?: { replace?: boolean }) => {
        const stateToPush = { view: newView, data };
        if (options?.replace) {
            ui.setHistoryStack(prev => [...prev.slice(0, -1), stateToPush]);
        } else {
            ui.setHistoryStack(prev => [...prev, stateToPush]);
        }
        routerNavigate(newView, data, options?.replace);

        ui.setActiveProgramId(null); ui.setEditingProgramId(null); ui.setEditingSessionInfo(null);
        ui.setLoggingSessionInfo(null); ui.setViewingSessionInfo(null); ui.setViewingExerciseId(null);
        ui.setViewingMuscleGroupId(null); ui.setViewingJointId(null); ui.setViewingTendonId(null); ui.setViewingMovementPatternId(null); ui.setViewingBodyPartId(null); ui.setViewingChainId(null);
        ui.setViewingMuscleCategoryName(null); ui.setViewingFoodId(null);

        if (data) {
            switch (newView) {
                case 'program-detail': ui.setActiveProgramId(data.programId); break;
                case 'program-metric-volume':
                case 'program-metric-strength':
                case 'program-metric-density':
                case 'program-metric-frequency':
                case 'program-metric-banister':
                case 'program-metric-recovery':
                case 'program-metric-adherence':
                case 'program-metric-rpe':
                    ui.setActiveProgramId(data.programId); break;
                case 'program-editor': ui.setEditingProgramId(data.programId || null); break;
                case 'session-editor': ui.setEditingSessionInfo(data); break;
                case 'log-workout': ui.setLoggingSessionInfo(data); break;
                case 'session-detail': ui.setViewingSessionInfo(data); break;
                case 'exercise-detail': ui.setViewingExerciseId(data.exerciseId); break;
                case 'muscle-group-detail': ui.setViewingMuscleGroupId(data.muscleGroupId); break;
                case 'joint-detail': ui.setViewingJointId(data.jointId); break;
                case 'tendon-detail': ui.setViewingTendonId(data.tendonId); break;
                case 'movement-pattern-detail': ui.setViewingMovementPatternId(data.movementPatternId); break;
                case 'body-part-detail': ui.setViewingBodyPartId(data.bodyPartId); break;
                case 'chain-detail': ui.setViewingChainId(data.chainId); break;
                case 'muscle-category': ui.setViewingMuscleCategoryName(data.categoryName); break;
                case 'food-detail': ui.setViewingFoodId(data.foodId); break;
            }
        }
        ui.setView(newView);
    }, []);

    const handleBack = useCallback(() => {
        const stack = useUIStore.getState().historyStack;
        if (stack.length <= 1) return;

        const newStack = stack.slice(0, -1);
        const previousState = newStack[newStack.length - 1];
        ui.setHistoryStack(newStack);

        ui.setActiveProgramId(null); ui.setEditingProgramId(null); ui.setEditingSessionInfo(null);
        ui.setLoggingSessionInfo(null); ui.setViewingSessionInfo(null); ui.setViewingExerciseId(null);
        ui.setViewingMuscleGroupId(null); ui.setViewingJointId(null); ui.setViewingTendonId(null); ui.setViewingMovementPatternId(null); ui.setViewingBodyPartId(null); ui.setViewingChainId(null);
        ui.setViewingMuscleCategoryName(null); ui.setViewingFoodId(null);

        if (previousState.data) {
            switch (previousState.view) {
                case 'program-detail': ui.setActiveProgramId(previousState.data.programId); break;
                case 'program-metric-volume':
                case 'program-metric-strength':
                case 'program-metric-density':
                case 'program-metric-frequency':
                case 'program-metric-banister':
                case 'program-metric-recovery':
                case 'program-metric-adherence':
                case 'program-metric-rpe':
                    ui.setActiveProgramId(previousState.data.programId); break;
                case 'program-editor': ui.setEditingProgramId(previousState.data.programId || null); break;
                case 'session-editor': ui.setEditingSessionInfo(previousState.data); break;
                case 'log-workout': ui.setLoggingSessionInfo(previousState.data); break;
                case 'session-detail': ui.setViewingSessionInfo(previousState.data); break;
                case 'exercise-detail': ui.setViewingExerciseId(previousState.data.exerciseId); break;
                case 'muscle-group-detail': ui.setViewingMuscleGroupId(previousState.data.muscleGroupId); break;
                case 'joint-detail': ui.setViewingJointId(previousState.data.jointId); break;
                case 'tendon-detail': ui.setViewingTendonId(previousState.data.tendonId); break;
                case 'movement-pattern-detail': ui.setViewingMovementPatternId(previousState.data.movementPatternId); break;
                case 'body-part-detail': ui.setViewingBodyPartId(previousState.data.bodyPartId); break;
                case 'chain-detail': ui.setViewingChainId(previousState.data.chainId); break;
                case 'muscle-category': ui.setViewingMuscleCategoryName(previousState.data.categoryName); break;
                case 'food-detail': ui.setViewingFoodId(previousState.data.foodId); break;
            }
        }
        ui.setView(previousState.view);
    }, []);

    // BACK BUTTON INTERCEPTOR (hash history fires popstate on back/forward)
    useEffect(() => {
        const handlePopState = (event: PopStateEvent) => {
            event.preventDefault();
            const stack = useUIStore.getState().historyStack;
            if (stack.length > 1) {
                handleBack();
            } else {
                routerNavigate('home');
                if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.WARNING as any);
                addToast("Presiona el botón Inicio para salir.", "suggestion");
            }
        };
        window.addEventListener('popstate', handlePopState);
        return () => window.removeEventListener('popstate', handlePopState);
    }, [handleBack, settings.hapticFeedbackEnabled, addToast]);

    // CRUD: Programs
    const handleCreateProgram = useCallback(() => navigateTo('program-editor'), [navigateTo]);
    const handleEditProgram = useCallback((programId: string) => navigateTo('program-editor', { programId }), [navigateTo]);

    const handleSaveProgram = useCallback((program: Program) => {
        const newlyCreated = !programs.some(p => p.id === program.id);
        setPrograms(prev => {
            const index = prev.findIndex(p => p.id === program.id);
            if (index > -1) { const updated = [...prev]; updated[index] = program; return updated; }
            return [...prev, program];
        });
        if (newlyCreated) {
            const unlocked: Achievement[] = checkAndUnlock({ programJustCreated: true });
            if (unlocked.length > 0) addToast(unlocked[0].name, 'achievement', '¡Logro Desbloqueado!');
        }
        navigateTo('program-detail', { programId: program.id }, { replace: true });
        addToast("Programa guardado.", "success");
    }, [programs, setPrograms, checkAndUnlock, addToast, navigateTo]);

    const handleSelectProgram = useCallback((program: Program) => navigateTo('program-detail', { programId: program.id }), [navigateTo]);

    const handleDeleteProgram = useCallback((programId: string) => {
        if (window.confirm('¿Seguro que quieres eliminar este programa?')) {
            setPrograms(prev => prev.filter(p => p.id !== programId));
            setHistory(prev => prev.filter(h => h.programId !== programId));
            navigateTo('home', undefined, { replace: true });
        }
    }, [setPrograms, setHistory, navigateTo]);

    const handleStartProgram = useCallback((programId: string) => {
        const program = programs.find(p => p.id === programId);
        if (!program) { addToast("No se pudo encontrar el programa.", "danger"); return; }
        const firstWeekId = program.macrocycles[0]?.blocks?.[0]?.mesocycles[0]?.weeks[0]?.id;
        if (!firstWeekId) { addToast("Este programa no tiene semanas.", "danger"); return; }
        setActiveProgramState({
            programId, status: 'active', startDate: new Date().toISOString(),
            currentMacrocycleIndex: 0, currentBlockIndex: 0, currentMesocycleIndex: 0, currentWeekId: firstWeekId,
        });
        addToast(`¡Programa "${program.name}" iniciado!`, 'success');
    }, [programs, setActiveProgramState, addToast]);

    const handlePauseProgram = useCallback(() => {
        setActiveProgramState(prev => {
            if (prev && prev.status === 'active') { addToast("Programa pausado.", "suggestion"); return { ...prev, status: 'paused' }; }
            return prev;
        });
    }, [setActiveProgramState, addToast]);

    const handleFinishProgram = useCallback(() => {
        setActiveProgramState(prev => {
            if (prev && prev.status !== 'completed') {
                addToast("¡Programa completado!", "achievement");
                if (prev.queuedProgramId) {
                    const nextProgram = programs.find(p => p.id === prev.queuedProgramId);
                    if (nextProgram) {
                        addToast(`Iniciando programa en cola: ${nextProgram.name}`, "suggestion");
                        const firstWeekId = nextProgram.macrocycles[0]?.blocks?.[0]?.mesocycles[0]?.weeks[0]?.id;
                        return { programId: prev.queuedProgramId, status: 'active', startDate: new Date().toISOString(), currentMacrocycleIndex: 0, currentBlockIndex: 0, currentMesocycleIndex: 0, currentWeekId: firstWeekId || '', firstSessionDate: undefined, queuedProgramId: undefined };
                    }
                }
                return { ...prev, status: 'completed' };
            }
            return prev;
        });
    }, [setActiveProgramState, addToast, programs]);

    const handleRestartProgram = useCallback(() => {
        if (activeProgramState) {
            if (window.confirm("¿Seguro que quieres reiniciar el progreso?")) handleStartProgram(activeProgramState.programId);
        }
    }, [activeProgramState, handleStartProgram]);

    // CRUD: Sessions (using Immer-style via setPrograms)
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
                            if (sessionIndex > -1) week.sessions[sessionIndex] = session;
                            else week.sessions.push(session);
                        }
                        return newPrograms;
                    }
                    mesoCount += block.mesocycles.length;
                }
            }
            return prevPrograms;
        });
    }, [setPrograms]);

    const handleSaveSession = useCallback((sessionOrSessions: Session | Session[], programId: string, macroIndex: number, mesoIndex: number, weekId: string) => {
        if (Array.isArray(sessionOrSessions)) {
            sessionOrSessions.forEach(session => handleUpdateSessionInProgram(session, programId, macroIndex, mesoIndex, weekId));
        } else {
            handleUpdateSessionInProgram(sessionOrSessions, programId, macroIndex, mesoIndex, weekId);
        }
        handleBack();
    }, [handleUpdateSessionInProgram, handleBack]);

    const handleDeleteSession = useCallback((sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => {
        setPrograms(prevPrograms => {
            const newPrograms = JSON.parse(JSON.stringify(prevPrograms));
            const program = newPrograms.find((p: Program) => p.id === programId);
            if (!program) { addToast('Error: Programa no encontrado.', 'danger'); return prevPrograms; }
            const macro = program.macrocycles[macroIndex];
            if (!macro) { addToast('Error: Macrociclo no encontrado.', 'danger'); return prevPrograms; }

            for (const block of (macro.blocks || [])) {
                for (const meso of block.mesocycles) {
                    const week = meso.weeks.find((w: ProgramWeek) => w.id === weekId);
                    if (week) {
                        const before = week.sessions.length;
                        week.sessions = week.sessions.filter((s: Session) => s.id !== sessionId);
                        if (week.sessions.length < before) {
                            return newPrograms;
                        }
                    }
                }
            }
            addToast('No se pudo eliminar la sesión. Intenta de nuevo.', 'danger');
            return prevPrograms;
        });
    }, [setPrograms, addToast]);

    const handleChangeSplit = useCallback((
        programId: string,
        splitPattern: string[],
        splitId: string,
        scope: 'week' | 'block' | 'program',
        preserveExercises: boolean,
        startDay: number,
        targetBlockId?: string,
        targetWeekId?: string
    ) => {
        setPrograms(prevPrograms => {
            const newPrograms = JSON.parse(JSON.stringify(prevPrograms));
            const program = newPrograms.find((p: Program) => p.id === programId);
            if (!program) return prevPrograms;

            program.startDay = startDay;
            program.selectedSplitId = splitId;

            const regenerateWeekSessions = (week: ProgramWeek) => {
                const newSessions: Session[] = [];
                splitPattern.forEach((label: string, dayIndex: number) => {
                    if (label && label.toLowerCase() !== 'descanso' && label.trim() !== '') {
                        const assignedDay = (startDay + dayIndex) % 7;
                        let existingSession: Session | undefined;
                        if (preserveExercises) {
                            existingSession = week.sessions.find((s: Session) =>
                                s.name.toLowerCase() === label.toLowerCase()
                            );
                        }
                        if (existingSession) {
                            newSessions.push({ ...existingSession, dayOfWeek: assignedDay });
                        } else {
                            newSessions.push({
                                id: crypto.randomUUID(),
                                name: label,
                                description: '',
                                exercises: [],
                                dayOfWeek: assignedDay
                            });
                        }
                    }
                });
                week.sessions = newSessions;
            };

            for (const macro of program.macrocycles) {
                for (const block of (macro.blocks || [])) {
                    const isTargetBlock = !targetBlockId || block.id === targetBlockId;
                    if (!isTargetBlock && scope === 'block') continue;

                    for (const meso of block.mesocycles) {
                        for (const week of meso.weeks) {
                            if (scope === 'week' && week.id !== targetWeekId) continue;
                            regenerateWeekSessions(week);
                            if (scope === 'week') break;
                        }
                    }

                    if (scope === 'block') break;
                }
            }

            return newPrograms;
        });
        addToast('Split actualizado correctamente.', 'success');
    }, [setPrograms, addToast]);

    // WORKOUT LOGIC
    const onCancelWorkout = useCallback(() => {
        if (window.confirm('¿Cancelar entrenamiento? No se guardará el progreso.')) {
            setOngoingWorkout(null);
            ui.setActiveSession(null);
            ui.setView('home');
            ui.setHistoryStack([{ view: 'home' }]);
            if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.WARNING as any);
        }
    }, [setOngoingWorkout, settings.hapticFeedbackEnabled]);

    const handleStartWorkout = useCallback(async (session: Session, program: Program, weekVariant?: 'A' | 'B' | 'C' | 'D', location?: { macroIndex: number; mesoIndex: number; weekId: string }, isLowEnergyMental?: boolean) => {
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
                                if (repsToFailure) { const ep = estimatePercent1RM(repsToFailure); if (ep) set.targetPercentageRM = ep; }
                            });
                        }
                    });
                    addToast("¡Sesión adaptada por la IA lista!", "success");
                } else {
                    addToast("No se encontró modificación de IA, usando sesión base.", "suggestion");
                }
            } catch (error) {
                console.error("Fallo del Coach IA:", error);
                addToast("Coach IA no disponible. Cargando sesión original.", "suggestion");
            }
        }

        const startWorkoutFlow = (sessionToBegin: Session, readiness?: OngoingWorkoutState['readiness']) => {
            const activeMode = weekVariant || 'A';
            const exercisesForMode = (activeMode === 'A' || !(sessionToBegin as any)[`session${activeMode}`])
                ? (sessionToBegin.parts && sessionToBegin.parts.length > 0 ? sessionToBegin.parts.flatMap(p => p.exercises) : sessionToBegin.exercises)
                : ((sessionToBegin as any)[`session${activeMode}`].parts && (sessionToBegin as any)[`session${activeMode}`].parts.length > 0 ? (sessionToBegin as any)[`session${activeMode}`].parts.flatMap((p: any) => p.exercises) : (sessionToBegin as any)[`session${activeMode}`].exercises);
            const newState: OngoingWorkoutState = {
                programId: program.id, session: sessionToBegin, startTime: Date.now(),
                activeExerciseId: exercisesForMode?.[0]?.id || null, activeSetId: exercisesForMode?.[0]?.sets?.[0]?.id || null,
                activeMode, completedSets: {}, dynamicWeights: {}, exerciseFeedback: {}, unilateralImbalances: {},
                readiness, isCarpeDiem: isCarpeDiemSession,
                macroIndex: location?.macroIndex, mesoIndex: location?.mesoIndex, weekId: location?.weekId,
                isLowEnergyMental: isLowEnergyMental || false,
            };
            setOngoingWorkout(newState);
            ui.setActiveSession(sessionToBegin);
            navigateTo('workout');
            if (settings.hapticFeedbackEnabled) hapticImpact(ImpactStyle.Heavy as any);
        };

        ui.setPendingWorkoutAfterBriefing(null);

        if (settings.readinessCheckEnabled && !ongoingWorkout) {
            ui.setPendingWorkoutForReadinessCheck({ session: sessionToStart, program, weekVariant, location });
            ui.setIsReadinessModalOpen(true);
        } else {
            startWorkoutFlow(sessionToStart, ongoingWorkout?.readiness);
        }
    }, [addToast, settings, history, setOngoingWorkout, navigateTo, ongoingWorkout]);

    const handleUpdateExerciseRepDebt = useCallback((exerciseDbId: string, debtUpdate: Record<string, number>) => {
        setExerciseList(prev => prev.map(ex => ex.id === exerciseDbId ? { ...ex, repDebtHistory: { ...(ex.repDebtHistory || {}), ...debtUpdate } } : ex));
    }, [setExerciseList]);

    const handleContinueFromReadiness = useCallback((data: any) => {
        const pending = useUIStore.getState().pendingWorkoutForReadinessCheck;
        if (pending) {
            const { session, program, weekVariant, location } = pending;
            const readinessScore = Math.round(((data.sleepQuality + (6 - data.stressLevel) + (6 - data.doms) + data.motivation) / 20) * 100);
            const readinessData = { ...data, readinessScore };
            const activeMode = weekVariant || 'A';
            const exercisesForMode = (activeMode === 'A' || !(session as any)[`session${activeMode}`])
                ? (session.parts && session.parts.length > 0 ? session.parts.flatMap(p => p.exercises) : session.exercises)
                : ((session as any)[`session${activeMode}`].parts && (session as any)[`session${activeMode}`].parts.length > 0 ? (session as any)[`session${activeMode}`].parts.flatMap((p: any) => p.exercises) : (session as any)[`session${activeMode}`].exercises);
            const newState: OngoingWorkoutState = {
                programId: program.id, session, startTime: Date.now(),
                activeExerciseId: exercisesForMode?.[0]?.id || null, activeSetId: exercisesForMode?.[0]?.sets?.[0]?.id || null,
                activeMode, completedSets: {}, dynamicWeights: {}, exerciseFeedback: {}, unilateralImbalances: {},
                readiness: readinessData, isCarpeDiem: program.carpeDiemEnabled,
                macroIndex: location?.macroIndex, mesoIndex: location?.mesoIndex, weekId: location?.weekId,
            };
            setOngoingWorkout(newState);
            ui.setActiveSession(session);
            navigateTo('workout');
            if (settings.hapticFeedbackEnabled) hapticImpact(ImpactStyle.Heavy as any);
        }
        ui.setIsReadinessModalOpen(false);
        ui.setPendingWorkoutForReadinessCheck(null);
    }, [setOngoingWorkout, navigateTo, settings.hapticFeedbackEnabled]);

    const handleContinueWorkoutAfterBriefing = useCallback(() => {
        const pending = useUIStore.getState().pendingWorkoutAfterBriefing;
        if (!pending) return;
        const { session, program, weekVariant, location } = pending;
        const readinessData = ongoingWorkout?.readiness;
        if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.WARNING as any);
        const activeMode = weekVariant || 'A';
        const exercisesForMode = (activeMode === 'A' || !(session as any)[`session${activeMode}`])
            ? (session.parts && session.parts.length > 0 ? session.parts.flatMap(p => p.exercises) : session.exercises)
            : ((session as any)[`session${activeMode}`].parts && (session as any)[`session${activeMode}`].parts.length > 0 ? (session as any)[`session${activeMode}`].parts.flatMap((p: any) => p.exercises) : (session as any)[`session${activeMode}`].exercises);
        const newState: OngoingWorkoutState = {
            programId: program.id, session, startTime: Date.now(),
            activeExerciseId: exercisesForMode[0]?.id || null, activeSetId: exercisesForMode[0]?.sets[0]?.id || null,
            activeMode, completedSets: {}, dynamicWeights: {}, exerciseFeedback: {}, unilateralImbalances: {},
            readiness: readinessData, isCarpeDiem: program.carpeDiemEnabled,
            macroIndex: location?.macroIndex, mesoIndex: location?.mesoIndex, weekId: location?.weekId,
        };
        setOngoingWorkout(newState);
        ui.setActiveSession(session);
        navigateTo('workout');
        if (settings.hapticFeedbackEnabled) hapticImpact(ImpactStyle.Heavy as any);
        ui.setPendingWorkoutAfterBriefing(null);
        ui.setPendingCoachBriefing(null);
    }, [ongoingWorkout, setOngoingWorkout, navigateTo, settings.hapticFeedbackEnabled]);

    const handleFinishWorkout = useCallback((
        completedExercises: CompletedExercise[], duration: number, notes?: string, discomforts?: string[],
        fatigue?: number, clarity?: number, logDate?: string, photoUri?: string, planDeviations?: PlanDeviation[],
        focus?: number, pump?: number, environmentTags?: string[], sessionDifficulty?: number, planAdherenceTags?: string[]
    ) => {
        if (!ongoingWorkout) return;
        const logId = crypto.randomUUID();
        const sessionStressScore = calculateCompletedSessionStress(completedExercises, exerciseList);
        const drainBreakdown = calculateCompletedSessionDrainBreakdown(completedExercises, exerciseList, settings);

        queueFatigueDataPoint({
            hours_since_session: 0,
            session_stress: sessionStressScore,
            sleep_hours: 7,
            nutrition_status: 1,
            stress_level: 3,
            age: settings.userVitals?.age || 25,
            is_compound_dominant: true,
            observed_fatigue_fraction: Math.min(1, sessionStressScore / 100),
        });
        queueTrainingImpulse({
            timestamp_hours: 0,
            impulse: drainBreakdown.totalStress,
            cns_impulse: drainBreakdown.cnsDrain,
            spinal_impulse: drainBreakdown.spinalDrain,
        });

        if (sessionStressScore > 200) {
            let avgWakeHour = 7, avgWakeMinute = 0;
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
            const pceData = {
                score: Math.round(sessionStressScore),
                suggestedCalories: settings.dailyCalorieGoal ? settings.dailyCalorieGoal + 350 : null,
                suggestedSleepTime: `${pad(suggestedSleepHour)}:${pad(avgWakeMinute)}`,
                wakeTimeStr: `${pad(avgWakeHour)}:${pad(avgWakeMinute)}`,
                isExtreme: sessionStressScore > 350
            };
            setTimeout(() => window.dispatchEvent(new CustomEvent('auge-pce-triggered', { detail: pceData })), 1500);
        }

        const newLog: Omit<WorkoutLog, 'id'> & { id: string } = {
            id: logId, programId: ongoingWorkout.programId,
            programName: programs.find(p => p.id === ongoingWorkout.programId)?.name || 'Unknown',
            sessionId: ongoingWorkout.session.id, sessionName: ongoingWorkout.session.name,
            date: safeCreateISOStringFromDateInput(logDate), duration, completedExercises,
            notes, discomforts, fatigueLevel: fatigue || 5, mentalClarity: clarity || 5,
            gymName: settings.gymName, photoUri, sessionVariant: ongoingWorkout.activeMode,
            planDeviations, readiness: ongoingWorkout.readiness, focus, pump,
            environmentTags, sessionDifficulty, planAdherenceTags, sessionStressScore,
        };

        const validationResult = WorkoutLogSchema.safeParse(newLog);
        if (!validationResult.success) {
            console.error("Validation error:", validationResult.error.flatten());
            addToast("Error al guardar el entrenamiento.", "danger");
            return;
        }
        const validatedLog = validationResult.data;

        setOngoingWorkout(null);
        ui.setActiveSession(null);
        ui.setIsFinishModalOpen(false);

        const saveLog = (log: WorkoutLog) => {
            const newHistory = [...history, log];
            setHistory(newHistory);
            try {
                const unlocked = checkAndUnlock({ log: validatedLog, history: newHistory });
                if (unlocked.length > 0) unlocked.forEach(ach => addToast(ach.name, 'achievement', '¡Logro Desbloqueado!'));
            } catch (e) { console.error("Achievement check error:", e); }
        };

        saveLog(validatedLog);  // Siempre guardar en history (fuente local de verdad)
        if (!ui.isOnline) {
            setSyncQueue(prev => [...prev, validatedLog]);
            addToast('Sin conexión. Guardado en cola para sincronizar.', 'suggestion');
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
                    debtUpdate[debtContextKey] = historicalDebt + (completedReps - targetReps);
                }
            });
            if (Object.keys(debtUpdate).length > 0) handleUpdateExerciseRepDebt(ex.exerciseDbId, debtUpdate);
        });

        const finishedProgram = programs.find(p => p.id === ongoingWorkout.programId);
        if (finishedProgram?.carpeDiemEnabled) {
            cacheService.remove(`carpe_diem_${finishedProgram.id}_${getWeekId(new Date(), settings.startWeekOn)}`);
        }

        setActiveProgramState(prev => {
            if (prev && prev.programId === ongoingWorkout.programId && !prev.firstSessionDate) return { ...prev, firstSessionDate: validatedLog.date };
            return prev;
        });

        navigateTo('home', undefined, { replace: true });
        playSound('session-complete-sound');
        if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.SUCCESS as any);
    }, [ongoingWorkout, programs, settings, history, setHistory, setOngoingWorkout, checkAndUnlock, addToast, setSyncQueue, exerciseList, handleUpdateExerciseRepDebt, navigateTo, sleepLogs, setActiveProgramState]);

    const handleSaveLoggedWorkout = useCallback((log: WorkoutLog) => {
        const newHistory = [...history, log];
        setHistory(newHistory);
        try {
            const unlocked = checkAndUnlock({ log, history: newHistory });
            if (unlocked.length > 0) unlocked.forEach(ach => addToast(ach.name, 'achievement', '¡Logro Desbloqueado!'));
        } catch (e) { console.error(e); }
        handleBack();
        addToast("Entrenamiento registrado con éxito.", "success");
        if (settings.hapticFeedbackEnabled) hapticNotification(NotificationType.SUCCESS as any);
    }, [history, setHistory, checkAndUnlock, handleBack, addToast, settings.hapticFeedbackEnabled]);

    const handleUpdateExercise1RM = useCallback((exerciseDbId: string | undefined, exerciseName: string, weight: number, reps: number, testDate?: string, machineBrand?: string) => {
        if (!exerciseDbId) return;
        const new1RM = calculateBrzycki1RM(weight, reps);
        if (new1RM <= 0) return;
        setExerciseList(prevList => {
            const exerciseIndex = prevList.findIndex(ex => ex.id === exerciseDbId);
            if (exerciseIndex === -1) return prevList;
            const exercise = prevList[exerciseIndex];
            if (new1RM > (exercise.calculated1RM || 0)) {
                const newList = [...prevList];
                newList[exerciseIndex] = { ...exercise, calculated1RM: new1RM, last1RMTestDate: testDate };
                playSound('new-pr-sound');
                hapticNotification(NotificationType.SUCCESS as any);
                addToast(`¡Nuevo PR en ${exerciseName}! ${weight}${settings.weightUnit} x ${reps} reps`, 'achievement');
                return newList;
            }
            return prevList;
        });
    }, [setExerciseList, addToast, settings.weightUnit]);

    const handleUpdateExerciseBrandPR = useCallback((exerciseDbId: string, brand: string, pr: { weight: number; reps: number; e1rm: number }) => {
        setExerciseList(prev => prev.map(ex => {
            if (ex.id === exerciseDbId) {
                const newEquivalencies = [...(ex.brandEquivalencies || [])];
                const brandIndex = newEquivalencies.findIndex(b => b.brand === brand);
                if (brandIndex > -1) newEquivalencies[brandIndex] = { ...newEquivalencies[brandIndex], pr };
                else newEquivalencies.push({ brand, pr });
                return { ...ex, brandEquivalencies: newEquivalencies };
            }
            return ex;
        }));
    }, [setExerciseList]);

    const openCustomExerciseEditor = useCallback((data?: CustomExerciseModalData) => { ui.setEditingCustomExerciseData(data || null); ui.setIsCustomExerciseEditorOpen(true); }, []);
    const closeCustomExerciseEditor = useCallback(() => { ui.setIsCustomExerciseEditorOpen(false); ui.setEditingCustomExerciseData(null); }, []);
    const openFoodEditor = useCallback((data?: { food?: FoodItem; preFilledName?: string }) => { ui.setEditingFoodData(data || null); ui.setIsFoodEditorOpen(true); }, []);
    const closeFoodEditor = useCallback(() => { ui.setIsFoodEditorOpen(false); ui.setEditingFoodData(null); }, []);
    const addOrUpdateFoodItem = useCallback((food: FoodItem) => { setFoodDatabase(prev => { const i = prev.findIndex(f => f.id === food.id); if (i > -1) { const u = [...prev]; u[i] = food; return u; } return [...prev, food]; }); }, [setFoodDatabase]);
    const openMuscleListEditor = useCallback((categoryName: string, type: 'bodyPart' | 'special') => { ui.setEditingCategoryInfo({ name: categoryName, type }); ui.setIsMuscleListEditorOpen(true); }, []);
    const closeMuscleListEditor = useCallback(() => { ui.setIsMuscleListEditorOpen(false); ui.setEditingCategoryInfo(null); }, []);
    const addOrUpdatePantryItem = useCallback((item: PantryItem) => { setPantryItems(prev => { const i = prev.findIndex(p => p.id === item.id); if (i > -1) { const u = [...prev]; u[i] = item; return u; } return [...prev, item]; }); }, [setPantryItems]);
    const openAddPantryItemModal = useCallback((foodItem: FoodItem) => { ui.setFoodItemToAdd_to_pantry(foodItem); ui.setIsAddPantryItemModalOpen(true); }, []);
    const closeAddPantryItemModal = useCallback(() => { ui.setIsAddPantryItemModalOpen(false); ui.setFoodItemToAdd_to_pantry(null); }, []);

    const restTimerInterval = useRef<number | null>(null);
    const handleStartRest = useCallback((duration: number, exerciseName: string) => {
        if (restTimerInterval.current) clearInterval(restTimerInterval.current);
        const key = Date.now();
        const endTime = Date.now() + duration * 1000;
        ui.setRestTimer({ duration, remaining: duration, key, exerciseName, endTime });
        restTimerInterval.current = window.setInterval(() => {
            ui.setRestTimer(currentTimer => {
                if (currentTimer && currentTimer.key === key) {
                    const newRemaining = Math.max(0, Math.round((currentTimer.endTime - Date.now()) / 1000));
                    if (newRemaining <= 0) {
                        clearInterval(restTimerInterval.current!);
                        if (currentTimer.remaining > 0) {
                            playSound('rest-timer-sound');
                            if (settings.hapticFeedbackEnabled) { hapticNotification(NotificationType.SUCCESS as any); setTimeout(() => hapticImpact(ImpactStyle.Medium as any), 150); }
                        }
                        setTimeout(() => ui.setRestTimer(t => t?.key === key ? null : t), 3000);
                        return { ...currentTimer, remaining: 0 };
                    }
                    return { ...currentTimer, remaining: newRemaining };
                }
                if (!currentTimer) clearInterval(restTimerInterval.current!);
                return currentTimer;
            });
        }, 250);
    }, [settings.hapticFeedbackEnabled]);

    const handleAdjustRestTimer = useCallback((amountInSeconds: number) => { ui.setRestTimer(ct => ct ? { ...ct, remaining: Math.max(0, ct.remaining + amountInSeconds), endTime: ct.endTime + (amountInSeconds * 1000) } : null); }, []);
    const handleSkipRestTimer = useCallback(() => { if (restTimerInterval.current) clearInterval(restTimerInterval.current); ui.setRestTimer(null); }, []);

    const handleModifyWorkout = useCallback(() => {
        if (!ongoingWorkout) return;
        const program = programs.find(p => p.id === ongoingWorkout.programId);
        if (!program) return;
        let sessionInfo: { session: Session; programId: string; macroIndex: number; mesoIndex: number; weekId: string } | undefined;
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
        if (sessionInfo) { ui.setEditingWorkoutSessionInfo(sessionInfo); ui.setIsWorkoutEditorOpen(true); }
        else addToast("No se pudo encontrar la sesión original.", "danger");
    }, [ongoingWorkout, programs, addToast]);

    const handleSaveModifiedWorkout = useCallback((session: Session) => {
        setOngoingWorkout(prev => prev ? ({ ...prev, session }) : null);
        ui.setIsWorkoutEditorOpen(false);
        ui.setEditingWorkoutSessionInfo(null);
        addToast("Sesión en curso modificada.", "success");
    }, [setOngoingWorkout, addToast]);

    const handleSaveBodyLog = useCallback((log: BodyProgressLog) => { setBodyProgress(prev => [...prev, log]); addToast("Registro corporal guardado.", "success"); }, [setBodyProgress, addToast]);
    const handleSaveNutritionLog = useCallback((log: NutritionLog) => { setNutritionLogs(prev => [...prev, log]); addToast("Registro de nutrición guardado.", "success"); }, [setNutritionLogs, addToast]);

    const handleCopySessionsToMeso = useCallback((programId: string, macroIndex: number, mesoIndex: number) => {
        setPrograms(prev => { const newPrograms = JSON.parse(JSON.stringify(prev)); return newPrograms; });
    }, [setPrograms]);

    const handleBiomechanicalDataUpdate = useCallback(async (data: BiomechanicalData) => {
        _setBiomechanicalDataDirect(data);
        if (ui.isOnline) {
            try {
                const analysis = await aiService.generateBiomechanicalAnalysis(data, [], settings);
                _setBiomechanicalAnalysis(analysis);
            } catch (e) { console.error(e); }
        }
    }, [_setBiomechanicalDataDirect, _setBiomechanicalAnalysis, settings]);

    const setBodyLabAnalysis = useCallback((analysis: BodyLabAnalysis | null) => { _setBodyLabAnalysis(analysis); }, [_setBodyLabAnalysis]);

    // ═══════════════════════════════════════════════════════════
    // 5. BUILD CONTEXT VALUES
    // ═══════════════════════════════════════════════════════════
    const stateValue: AppContextState = {
        view: ui.view, historyStack: ui.historyStack, programs, history, skippedLogs, settings, bodyProgress,
        nutritionLogs, waterLogs, pantryItems, tasks, exercisePlaylists, muscleGroupData, muscleHierarchy,
        jointDatabase: JOINT_DATABASE, tendonDatabase: TENDON_DATABASE, movementPatternDatabase: MOVEMENT_PATTERN_DATABASE,
        exerciseList, foodDatabase, unlockedAchievements, isOnline: ui.isOnline, isAppLoading,
        installPromptEvent: ui.installPromptEvent, drive, toasts: ui.toasts, bodyLabAnalysis,
        biomechanicalData, biomechanicalAnalysis, syncQueue, aiNutritionPlan, activeProgramState,
        onExerciseCreated: ui.onExerciseCreated, pendingQuestionnaires, postSessionFeedback,
        dailyWellbeingLogs,
        isBodyLogModalOpen: ui.isBodyLogModalOpen, isNutritionLogModalOpen: ui.isNutritionLogModalOpen,
        isMeasurementsModalOpen: ui.isMeasurementsModalOpen, isStartWorkoutModalOpen: ui.isStartWorkoutModalOpen,
        isCustomExerciseEditorOpen: ui.isCustomExerciseEditorOpen, isFoodEditorOpen: ui.isFoodEditorOpen,
        isFinishModalOpen: ui.isFinishModalOpen, isTimeSaverModalOpen: ui.isTimeSaverModalOpen,
        isTimersModalOpen: ui.isTimersModalOpen, isReadinessModalOpen: ui.isReadinessModalOpen,
        isAddToPlaylistSheetOpen: ui.isAddToPlaylistSheetOpen, isWorkoutEditorOpen: ui.isWorkoutEditorOpen,
        isMuscleListEditorOpen: ui.isMuscleListEditorOpen, isLiveCoachActive: ui.isLiveCoachActive,
        isLogActionSheetOpen: ui.isLogActionSheetOpen, isWorkoutExitModalOpen: ui.isWorkoutExitModalOpen,
        isAddPantryItemModalOpen: ui.isAddPantryItemModalOpen,
        isSpecialSessionModalOpen: ui.isSpecialSessionModalOpen, specialSessionData: ui.specialSessionData,
        activeProgramId: ui.activeProgramId, editingProgramId: ui.editingProgramId,
        editingSessionInfo: ui.editingSessionInfo, activeSession: ui.activeSession,
        loggingSessionInfo: ui.loggingSessionInfo, viewingSessionInfo: ui.viewingSessionInfo,
        viewingExerciseId: ui.viewingExerciseId, viewingFoodId: ui.viewingFoodId,
        viewingMuscleGroupId: ui.viewingMuscleGroupId, viewingJointId: ui.viewingJointId, viewingTendonId: ui.viewingTendonId, viewingMovementPatternId: ui.viewingMovementPatternId, viewingBodyPartId: ui.viewingBodyPartId,
        viewingChainId: ui.viewingChainId, viewingMuscleCategoryName: ui.viewingMuscleCategoryName,
        exerciseToAddId: ui.exerciseToAddId, foodItemToAdd_to_pantry: ui.foodItemToAdd_to_pantry,
        ongoingWorkout, editingCustomExerciseData: ui.editingCustomExerciseData,
        editingFoodData: ui.editingFoodData, pendingWorkoutForReadinessCheck: ui.pendingWorkoutForReadinessCheck,
        editingWorkoutSessionInfo: ui.editingWorkoutSessionInfo, editingCategoryInfo: ui.editingCategoryInfo,
        pendingNavigation: ui.pendingNavigation, saveSessionTrigger: ui.saveSessionTrigger,
        addExerciseTrigger: ui.addExerciseTrigger, saveProgramTrigger: ui.saveProgramTrigger,
        saveLoggedWorkoutTrigger: ui.saveLoggedWorkoutTrigger, modifyWorkoutTrigger: ui.modifyWorkoutTrigger,
        searchQuery: ui.searchQuery, activeSubTabs: ui.activeSubTabs,
        currentBackgroundOverride: ui.currentBackgroundOverride, restTimer: ui.restTimer,
        isDirty: ui.isDirty, kpknAction: ui.kpknAction, pendingCoachBriefing: ui.pendingCoachBriefing,
        pendingWorkoutAfterBriefing: ui.pendingWorkoutAfterBriefing,
        sleepLogs, sleepStartTime, isGlobalVoiceActive: ui.isGlobalVoiceActive,
        recommendationTriggers, isMenuOpen: ui.isMenuOpen,
    };

    const dispatchValue: AppContextDispatch = {
        setPrograms, setHistory, setSkippedLogs, setSettings, setBodyProgress, setNutritionLogs,
        setWaterLogs, handleLogWater, setPantryItems, addOrUpdatePantryItem, setTasks,
        addTask, addAITasks, toggleTask, deleteTask, setExercisePlaylists,
        addOrUpdatePlaylist: (playlist) => setExercisePlaylists(prev => { const i = prev.findIndex(p => p.id === playlist.id); if (i > -1) { const u = [...prev]; u[i] = playlist; return u; } return [...prev, playlist]; }),
        deletePlaylist: (playlistId) => setExercisePlaylists(prev => prev.filter(p => p.id !== playlistId)),
        setMuscleGroupData,
        updateMuscleGroupInfo: (id, data) => setMuscleGroupData(prev => prev.map(m => m.id === id ? { ...m, ...data } : m)),
        setMuscleHierarchy,
        renameMuscleCategory: () => { }, renameMuscleGroup: () => { },
        updateCategoryMuscles: (categoryName, newMuscles, type) => setMuscleHierarchy(prev => {
            const newHierarchy = JSON.parse(JSON.stringify(prev));
            if (type === 'bodyPart') newHierarchy.bodyPartHierarchy[categoryName] = newMuscles;
            else newHierarchy.specialCategories[categoryName] = newMuscles;
            return newHierarchy;
        }),
        setBodyLabAnalysis, setBiomechanicalData: handleBiomechanicalDataUpdate,
        setBiomechanicalAnalysis: _setBiomechanicalAnalysis,
        setAiNutritionPlan, setActiveProgramState, setOnExerciseCreated: ui.setOnExerciseCreated,
        setInstallPromptEvent: ui.setInstallPromptEvent,
        setIsBodyLogModalOpen: ui.setIsBodyLogModalOpen, setIsNutritionLogModalOpen: ui.setIsNutritionLogModalOpen,
        setIsMeasurementsModalOpen: ui.setIsMeasurementsModalOpen, setIsStartWorkoutModalOpen: ui.setIsStartWorkoutModalOpen,
        setIsFinishModalOpen: ui.setIsFinishModalOpen, setIsTimeSaverModalOpen: ui.setIsTimeSaverModalOpen,
        setIsTimersModalOpen: ui.setIsTimersModalOpen, setIsReadinessModalOpen: ui.setIsReadinessModalOpen,
        setIsAddToPlaylistSheetOpen: ui.setIsAddToPlaylistSheetOpen, setIsLiveCoachActive: ui.setIsLiveCoachActive,
        setIsLogActionSheetOpen: ui.setIsLogActionSheetOpen,
        openCustomExerciseEditor, closeCustomExerciseEditor, openFoodEditor, closeFoodEditor,
        openAddPantryItemModal, closeAddPantryItemModal, openMuscleListEditor, closeMuscleListEditor,
        setIsWorkoutExitModalOpen: ui.setIsWorkoutExitModalOpen,
        setPendingNavigation: ui.setPendingNavigation, setExerciseToAddId: ui.setExerciseToAddId,
        setPendingWorkoutForReadinessCheck: ui.setPendingWorkoutForReadinessCheck,
        setSaveSessionTrigger: ui.setSaveSessionTrigger, setAddExerciseTrigger: ui.setAddExerciseTrigger,
        setSaveProgramTrigger: ui.setSaveProgramTrigger, setSaveLoggedWorkoutTrigger: ui.setSaveLoggedWorkoutTrigger,
        setModifyWorkoutTrigger: ui.setModifyWorkoutTrigger, setSearchQuery: ui.setSearchQuery,
        setActiveSubTabs: ui.setActiveSubTabs, setCurrentBackgroundOverride: ui.setCurrentBackgroundOverride,
        setOngoingWorkout, navigateTo, handleBack, addToast, removeToast,
        setPendingCoachBriefing: ui.setPendingCoachBriefing, setPendingWorkoutAfterBriefing: ui.setPendingWorkoutAfterBriefing,
        handleCreateProgram, handleEditProgram, handleSelectProgram: (p) => navigateTo('program-detail', { programId: p.id }),
        handleSaveProgram, handleUpdateProgram: handleSaveProgram, handleDeleteProgram,
        handleAddSession, handleEditSession, handleSaveSession, handleUpdateSessionInProgram,
        handleDeleteSession, handleChangeSplit, handleCopySessionsToMeso, handleStartProgram, handlePauseProgram,
        handleFinishProgram, handleRestartProgram, handleStartWorkout,
        handleResumeWorkout: () => navigateTo('workout'), handleContinueFromReadiness,
        handleContinueWorkoutAfterBriefing, onCancelWorkout, handlePauseWorkout: () => navigateTo('home'),
        handleFinishWorkout, handleLogWorkout: (programId, sessionId) => navigateTo('log-workout', { programId, sessionId }),
        handleSaveLoggedWorkout,
        handleSkipWorkout: (session, program, reason, notes) => setSkippedLogs(prev => [...prev, { id: crypto.randomUUID(), date: new Date().toISOString(), programId: program.id, sessionId: session.id, sessionName: session.name, programName: program.name, reason, notes }]),
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
                                                if (exIndex > -1) { session.exercises[exIndex] = updatedExercise; return newPrograms; }
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
            setOngoingWorkout(prev => prev ? { ...prev, dynamicWeights: { ...prev.dynamicWeights, [exerciseId]: { consolidated, technical } } } : null);
        },
        handleUpdateExercise1RM, handleUpdateExerciseBrandPR, handleUpdateExerciseRepDebt,
        handleStartRest, handleAdjustRestTimer, handleSkipRestTimer,
        handleLogPress: () => ui.setIsLogActionSheetOpen(true),
        addOrUpdateCustomExercise, batchAddExercises, createAndAddExerciseToDB, setExerciseList,
        exportExerciseDatabase, importExerciseDatabase, setIsDirty: ui.setIsDirty, handleModifyWorkout,
        handleSaveModifiedWorkout, setIsWorkoutEditorOpen: ui.setIsWorkoutEditorOpen, setKpknaction: ui.setKpknaction,
        handleLogSleep, setSleepLogs, handleSavePostSessionFeedback,
        setIsGlobalVoiceActive: ui.setIsGlobalVoiceActive,
        addRecommendationTrigger, markRecommendationAsTaken,
        setIsMenuOpen: ui.setIsMenuOpen, handleLogDailyWellbeing,
        setIsSpecialSessionModalOpen: ui.setIsSpecialSessionModalOpen,
        setSpecialSessionData: ui.setSpecialSessionData,
    };

    // ═══════════════════════════════════════════════════════════
    // 6. UI CONTEXT BRIDGE
    // ═══════════════════════════════════════════════════════════
    const uiStates: UIState = useMemo(() => ({
        isBodyLogModalOpen: ui.isBodyLogModalOpen, isNutritionLogModalOpen: ui.isNutritionLogModalOpen,
        isMeasurementsModalOpen: ui.isMeasurementsModalOpen, isStartWorkoutModalOpen: ui.isStartWorkoutModalOpen,
        isCustomExerciseEditorOpen: ui.isCustomExerciseEditorOpen, isFoodEditorOpen: ui.isFoodEditorOpen,
        isFinishModalOpen: ui.isFinishModalOpen, isTimeSaverModalOpen: ui.isTimeSaverModalOpen,
        isTimersModalOpen: ui.isTimersModalOpen, isReadinessModalOpen: ui.isReadinessModalOpen,
        isAddToPlaylistSheetOpen: ui.isAddToPlaylistSheetOpen, isWorkoutEditorOpen: ui.isWorkoutEditorOpen,
        isMuscleListEditorOpen: ui.isMuscleListEditorOpen, isLiveCoachActive: ui.isLiveCoachActive,
        isLogActionSheetOpen: ui.isLogActionSheetOpen, isWorkoutExitModalOpen: ui.isWorkoutExitModalOpen,
        isAddPantryItemModalOpen: ui.isAddPantryItemModalOpen,
        isSpecialSessionModalOpen: ui.isSpecialSessionModalOpen,
        isMenuOpen: ui.isMenuOpen, isGlobalVoiceActive: ui.isGlobalVoiceActive,
        searchQuery: ui.searchQuery, activeSubTabs: ui.activeSubTabs,
    }), [
        ui.isBodyLogModalOpen, ui.isNutritionLogModalOpen, ui.isMeasurementsModalOpen,
        ui.isStartWorkoutModalOpen, ui.isCustomExerciseEditorOpen, ui.isFoodEditorOpen,
        ui.isFinishModalOpen, ui.isTimeSaverModalOpen, ui.isTimersModalOpen,
        ui.isReadinessModalOpen, ui.isAddToPlaylistSheetOpen, ui.isWorkoutEditorOpen,
        ui.isMuscleListEditorOpen, ui.isLiveCoachActive, ui.isLogActionSheetOpen,
        ui.isWorkoutExitModalOpen, ui.isAddPantryItemModalOpen, ui.isSpecialSessionModalOpen,
        ui.isMenuOpen, ui.isGlobalVoiceActive, ui.searchQuery, ui.activeSubTabs,
    ]);

    const uiDispatches: UIDispatch = useMemo(() => ({
        setIsBodyLogModalOpen: ui.setIsBodyLogModalOpen, setIsNutritionLogModalOpen: ui.setIsNutritionLogModalOpen,
        setIsMeasurementsModalOpen: ui.setIsMeasurementsModalOpen, setIsStartWorkoutModalOpen: ui.setIsStartWorkoutModalOpen,
        setIsFinishModalOpen: ui.setIsFinishModalOpen, setIsTimeSaverModalOpen: ui.setIsTimeSaverModalOpen,
        setIsTimersModalOpen: ui.setIsTimersModalOpen, setIsReadinessModalOpen: ui.setIsReadinessModalOpen,
        setIsAddToPlaylistSheetOpen: ui.setIsAddToPlaylistSheetOpen, setIsLiveCoachActive: ui.setIsLiveCoachActive,
        setIsLogActionSheetOpen: ui.setIsLogActionSheetOpen, setIsWorkoutExitModalOpen: ui.setIsWorkoutExitModalOpen,
        setIsWorkoutEditorOpen: ui.setIsWorkoutEditorOpen, setIsMenuOpen: ui.setIsMenuOpen,
        setIsGlobalVoiceActive: ui.setIsGlobalVoiceActive, setIsSpecialSessionModalOpen: ui.setIsSpecialSessionModalOpen,
        setSearchQuery: ui.setSearchQuery, setActiveSubTabs: ui.setActiveSubTabs,
    }), []);

    return (
        <AppStateContext.Provider value={stateValue}>
            <AppDispatchContext.Provider value={dispatchValue}>
                <UIProvider overrides={{ states: uiStates, dispatches: uiDispatches }}>
                    {children}
                </UIProvider>
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

export { useUIState, useUIDispatch } from './UIContext';
