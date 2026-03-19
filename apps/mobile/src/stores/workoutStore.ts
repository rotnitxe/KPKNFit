import { create } from 'zustand';
import type { CoreReminderSettings, WorkoutLogSummary, WorkoutOverview } from '@kpkn/shared-types';
import {
  cancelRestTimerNotification,
  rescheduleCoreNotificationsFromState,
  scheduleRestTimerNotification,
} from '../services/mobileNotificationService';
import {
  persistLocalWorkoutLog,
  persistDomainPayload,
  loadPersistedDomainPayload
} from '../services/mobilePersistenceService';
import { loadWorkoutRuntimeState } from '../services/workoutStateService';
import { syncWorkoutWidgetState } from '../services/widgetSyncService';
import { useMobileNutritionStore } from './nutritionStore';
import { useExerciseStore } from './exerciseStore';
import { generateId } from '../utils/generateId';

import { getMobileDatabase } from '../storage/mobileDatabase';
import {
  calculateCompletedSessionDrainBreakdown,
  calculateCompletedSessionStress,
} from '../services/fatigueService';
import { getLocalDateString } from '../utils/dateUtils';
import { propagate1RM } from '../services/oneRMPropagationService';
import { getSessionExercises, normalizeSessionForExecution } from '../utils/workoutSession';
import {
  persistActiveSessionCheckpoint,
  clearActiveSessionCheckpoint,
  persistRestTimer,
  persistSetCompletion,
  recoverActiveSession,
  SESSION_PERSISTENCE_KEY,
} from '../services/activeSessionPersistenceService';

import {
  OngoingWorkoutState,
  OngoingSetData,
  Session,
  WorkoutLog,
  SkippedWorkoutLog,
  CompletedExercise,
  CompletedSet,
  SetTypeLabel,
  Exercise,
  PostExerciseFeedback,
} from '../types/workout';
import { Settings } from '../types/settings';

type WorkoutScreenStatus = 'idle' | 'empty' | 'ready' | 'failed';
type WorkoutLoggingState = 'idle' | 'saving';

export type SessionFinishState = 'idle' | 'saving';

export type PostSessionFeedbackInput = {
  sessionRpe: number;      // 1..10
  energyAfter: number;     // 1..5
  sorenessAfter: number;   // 1..5
  hadPain: boolean;
  notes: string;
};

export type PostSessionFeedbackRecord = PostSessionFeedbackInput & {
  id: string;
  createdAt: string;
  programId: string;
  sessionId: string;
  sessionName: string;
};

interface WorkoutStoreState {
  status: WorkoutScreenStatus;
  overview: WorkoutOverview | null;
  reminderSettings: CoreReminderSettings | null;
  hasHydrated: boolean;
  errorMessage: string | null;
  notice: string | null;
  loggingState: WorkoutLoggingState;
  readinessScore: { sleep: number; mood: number; soreness: number } | null;
  activeSession: OngoingWorkoutState | null;
  sessionFinishState: SessionFinishState;
  postSessionFeedbackHistory: PostSessionFeedbackRecord[];
  latestPostSessionFeedback: PostSessionFeedbackRecord | null;
  history: WorkoutLog[];
  skippedLogs: SkippedWorkoutLog[];
  syncQueue: WorkoutLog[];
  hydrateFromMigration: () => Promise<void>;
  refreshInfrastructure: () => Promise<void>;
  logTodaySession: () => Promise<void>;
  startRestTimer: (seconds: number, setId?: string) => Promise<void>;
  cancelRestTimer: () => Promise<void>;
  setReadinessScore: (score: { sleep: number; mood: number; soreness: number }) => void;
  startActiveSession: (payload: { programId: string; session: Session }) => void;
  recoverActiveSession: () => Promise<void>;
  updateSetData: (setId: string, data: Partial<OngoingSetData>) => void;
  setSetTypeOverride: (setId: string, type: SetTypeLabel) => void;
  setExerciseBrand: (exerciseId: string, brand: string) => void;
  setDynamicWeight: (exerciseId: string, weight: number, isTechnical?: boolean) => void;
  setActiveExercise: (exerciseId: string | null) => void;
  setActiveSet: (setId: string | null) => void;
  discardActiveSession: () => void;

  completeSet: (exerciseId: string, setId: string, data: OngoingSetData, isCalibrator?: boolean) => void;
  substituteExercise: (oldExerciseId: string, replacement: any) => void;
  updateSessionAdjusted1RM: (exerciseId: string, e1RM: number, isSource?: boolean) => void;
  savePostExerciseFeedback: (exerciseId: string, feedback: PostExerciseFeedback) => void;

   finishActiveSession: (data: {
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
        sessionRpe?: number;
        energyAfter?: number;
        sorenessAfter?: number;
        hadPain?: boolean;
   }) => Promise<void>;
  clearLastPR: () => void;
  clearNotice: () => void;
  
  setHistory: (history: WorkoutLog[] | ((prev: WorkoutLog[]) => WorkoutLog[])) => void;
  addWorkoutLog: (log: WorkoutLog) => void;
  setSkippedLogs: (logs: SkippedWorkoutLog[] | ((prev: SkippedWorkoutLog[]) => SkippedWorkoutLog[])) => void;
  setSyncQueue: (queue: WorkoutLog[] | ((prev: WorkoutLog[]) => WorkoutLog[])) => void;
}


async function syncWorkoutInfra(
  overview: WorkoutOverview | null,
  reminderSettings: CoreReminderSettings,
  options: { syncEmptyOverviewToWidget?: boolean } = {},
) {
  if (overview || options.syncEmptyOverviewToWidget) {
    await syncWorkoutWidgetState(overview);
  }
  await rescheduleCoreNotificationsFromState({
    settings: reminderSettings,
    nutritionLogs: useMobileNutritionStore.getState().savedLogs,
    workoutOverview: overview,
  });
}

function buildQuickWorkoutLog(overview: WorkoutOverview): WorkoutLogSummary | null {
  if (!overview.todaySession) return null;
  const today = new Date().toISOString().slice(0, 10);
  return {
    id: generateId(),
    date: today,
    programName: overview.activeProgramName ?? 'KPKN',
    sessionName: overview.todaySession.name,
    exerciseCount: overview.todaySession.exerciseCount,
    completedSetCount: overview.todaySession.setCount,
    durationMinutes: null,
  };
}

function resolveSessionSelection(
  session: Session,
  activeExerciseId: string | null = null,
  activeSetId: string | null = null,
) {
  const normalizedSession = normalizeSessionForExecution(session);
  const exercises = getSessionExercises(normalizedSession);

  if (exercises.length === 0) {
    return {
      session: normalizedSession,
      exercises,
      activeExerciseId: null,
      activeSetId: null,
    };
  }

  const resolvedExerciseId = exercises.some(ex => ex.id === activeExerciseId)
    ? activeExerciseId
    : exercises[0]?.id ?? null;
  const resolvedExercise = exercises.find(ex => ex.id === resolvedExerciseId) ?? null;
  const resolvedSetId = resolvedExercise?.sets?.some(set => set.id === activeSetId)
    ? activeSetId
    : resolvedExercise?.sets?.[0]?.id ?? null;

  return {
    session: normalizedSession,
    exercises,
    activeExerciseId: resolvedExerciseId,
    activeSetId: resolvedSetId,
  };
}

export const useWorkoutStore = create<WorkoutStoreState>((set, get) => ({
  status: 'idle',
  overview: null,
  reminderSettings: null,
  hasHydrated: false,
  errorMessage: null,
  notice: null,
  loggingState: 'idle',
  readinessScore: null,
  activeSession: null,
  sessionFinishState: 'idle',
  postSessionFeedbackHistory: [],
  latestPostSessionFeedback: null,
  history: [],
  skippedLogs: [],
  syncQueue: [],

  hydrateFromMigration: async () => {
    set({ errorMessage: null, notice: null });
    try {
      const { overview, reminderSettings } = await loadWorkoutRuntimeState();
      
      // Cargar feedback persistido
      const feedbackHistory = await loadPersistedDomainPayload<PostSessionFeedbackRecord[]>('workout.post-session-feedback.v1') || [];
      
      // Cargar history, skippedLogs y syncQueue desde persistencia
      const history = await loadPersistedDomainPayload<WorkoutLog[]>('workout.history') || [];
      const skippedLogs = await loadPersistedDomainPayload<SkippedWorkoutLog[]>('workout.skippedLogs') || [];
      const syncQueue = await loadPersistedDomainPayload<WorkoutLog[]>('workout.syncQueue') || [];

      set({
        status: overview ? 'ready' : 'empty',
        overview,
        reminderSettings,
        postSessionFeedbackHistory: feedbackHistory,
        latestPostSessionFeedback: feedbackHistory[0] || null,
        history,
        skippedLogs,
        syncQueue,
        hasHydrated: true,
        errorMessage: null,
      });
      await syncWorkoutInfra(overview, reminderSettings);
    } catch (error) {
      set({
        status: 'failed',
        hasHydrated: true,
        errorMessage: error instanceof Error ? error.message : 'No pudimos abrir el módulo de entrenamiento.',
      });
    }
  },

  refreshInfrastructure: async () => {
    try {
      const { overview, reminderSettings } = await loadWorkoutRuntimeState();
      set({
        status: overview ? 'ready' : 'empty',
        overview,
        reminderSettings,
        errorMessage: null,
      });
      await syncWorkoutInfra(overview, reminderSettings, {
        syncEmptyOverviewToWidget: true,
      });
      set({ notice: 'Widgets y recordatorios actualizados.' });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'No pudimos refrescar entrenamiento.',
      });
    }
  },

  logTodaySession: async () => {
    const overview = get().overview;
    if (!overview?.todaySession) {
      set({ notice: 'Hoy no hay una sesión programada para registrar desde RN.' });
      return;
    }
    if (overview.hasWorkoutLoggedToday) {
      set({ notice: 'La sesión de hoy ya está registrada.' });
      return;
    }

    const quickLog = buildQuickWorkoutLog(overview);
    if (!quickLog) {
      set({ notice: 'No pudimos construir el registro rápido de la sesión.' });
      return;
    }

    set({ loggingState: 'saving', notice: null });
    try {
      await persistLocalWorkoutLog(quickLog);
      const { overview: nextOverview, reminderSettings } = await loadWorkoutRuntimeState();
console.log('Setting state after finishActiveSession');
console.log('Updating state with', {
           status: nextOverview ? 'ready' : 'empty',
        overview: nextOverview,
        reminderSettings,
        loggingState: 'idle',
        errorMessage: null,
      });
      await syncWorkoutInfra(nextOverview, reminderSettings);
      set({ notice: 'Sesión de hoy registrada en esta app RN.' });
} catch (error) {
        set({
          loggingState: 'idle',
          errorMessage: error instanceof Error ? error.message : 'No pudimos registrar la sesión de hoy.',
        });
      }
      // Ensure loggingState reset to idle regardless of outcome
      set({ loggingState: 'idle' });
    },

  startRestTimer: async (seconds: number, setId?: string) => {
    const label = get().overview?.todaySession?.name ?? 'Descanso';
    await scheduleRestTimerNotification(seconds, label);
    
    // Persistir timer para recuperación
    await persistRestTimer({
      remainingSeconds: seconds,
      totalSeconds: seconds,
      setId,
    });
    
    set({
      notice: `Temporizador listo para ${seconds}s.`,
    });
  },

  cancelRestTimer: async () => {
    await cancelRestTimerNotification();
    
    // Limpiar timer de checkpoint si existe sin borrar toda la sesión
    const { activeSession } = get();
    if (activeSession) {
      const newState = {
        ...activeSession,
        restTimer: null
      };
      persistActiveSessionCheckpoint(newState);
      set((state) => ({
        ...state,
        activeSession: newState,
        notice: 'Temporizador cancelado.',
      }));
      return;
    }

    set((state) => ({
      ...state,
      notice: 'Temporizador cancelado.',
    }));
  },

  setReadinessScore: (score) => set((state) => ({
    ...state,
    readinessScore: score,
  })),

  startActiveSession: (programIdOrPayload, maybeSession) => {
    // Support both old and new signatures for startActiveSession
    const payload = typeof programIdOrPayload === 'string'
      ? { programId: programIdOrPayload, session: maybeSession as Session }
      : programIdOrPayload;

    set((state) => {
      const { session, exercises, activeExerciseId, activeSetId } = resolveSessionSelection(payload.session);

      if (state.activeSession?.session.id === session.id) {
        return state;
      }

      if (exercises.length === 0) {
        return {
          ...state,
          notice: 'La sesión no tiene ejercicios para iniciar.',
        };
      }

      const newSession: OngoingWorkoutState = {
        programId: payload.programId,
        session,
        startTime: Date.now(),
        activeExerciseId,
        activeSetId,
        completedSets: {},
        dynamicWeights: {},
        sessionAdjusted1RMs: {},
        selectedBrands: {},
        setTypeOverrides: {},
        postExerciseFeedback: {},
      };

      // Persistir checkpoint para recuperación (Silent Checkpoint initial)
      persistActiveSessionCheckpoint(newSession);

      return {
        ...state,
        activeSession: newSession,
      };
    });
  },

  recoverActiveSession: async () => {
    try {
      const recovered = await recoverActiveSession();
      
      if (recovered) {
        const { session, exercises, activeExerciseId, activeSetId } = resolveSessionSelection(
          recovered.session,
          recovered.activeExerciseId,
          recovered.activeSetId,
        );

        if (exercises.length === 0) {
          await clearActiveSessionCheckpoint();
          set({
            activeSession: null,
            notice: 'No pudimos recuperar una sesión válida.',
          });
          return;
        }

        const normalized = {
          ...recovered,
          session,
          activeExerciseId,
          activeSetId,
        };
        persistActiveSessionCheckpoint(normalized);
        set((state) => ({
          ...state,
          activeSession: normalized,
          notice: `Sesión recuperada: ${normalized.session.name}`,
        }));
      }
    } catch (error) {
      console.error('Error recuperando sesión:', error);
      set({
        errorMessage: 'No pudimos recuperar la sesión previa.',
      });
    }
  },

  updateSetData: (setId, data) => {
    set((state) => {
      if (!state.activeSession) return state;
      const current = state.activeSession.completedSets[setId] || { weight: 0 };
      
      const newState = {
        ...state,
        activeSession: {
          ...state.activeSession,
          completedSets: {
            ...state.activeSession.completedSets,
            [setId]: { ...current, ...data }
          }
        }
      };
      
      // Persistir checkpoint silencioso cada vez que cambian datos (reps, peso, RPE)
      persistActiveSessionCheckpoint(newState.activeSession);
      
      return newState;
    });
  },

  setSetTypeOverride: (setId, type) => {
    set((state) => {
      if (!state.activeSession) return state;
      
      const newState = {
        ...state,
        activeSession: {
          ...state.activeSession,
          setTypeOverrides: {
            ...state.activeSession.setTypeOverrides,
            [setId]: type
          }
        }
      };
      
      persistActiveSessionCheckpoint(newState.activeSession);
      
      return newState;
    });
  },

  setExerciseBrand: (exerciseId, brand) => {
    set((state) => {
      if (!state.activeSession) return state;
      
      const newState = {
        ...state,
        activeSession: {
          ...state.activeSession,
          selectedBrands: {
            ...state.activeSession.selectedBrands,
            [exerciseId]: brand
          }
        }
      };
      
      persistActiveSessionCheckpoint(newState.activeSession);
      
      return newState;
    });
  },

  setDynamicWeight: (exerciseId, weight, isTechnical) => {
    set((state) => {
      if (!state.activeSession) return state;
      const current = state.activeSession.dynamicWeights[exerciseId] || {};
      
      const newState = {
        ...state,
        activeSession: {
          ...state.activeSession,
          dynamicWeights: {
            ...state.activeSession.dynamicWeights,
            [exerciseId]: {
              ...current,
              [isTechnical ? 'technical' : 'consolidated']: weight
            }
          }
        }
      };
      
      persistActiveSessionCheckpoint(newState.activeSession);
      
      return newState;
    });
  },

  setActiveExercise: (exerciseId) => {
    set((state) => {
      if (!state.activeSession) return state;
      
      const newState = {
        ...state,
        activeSession: { ...state.activeSession, activeExerciseId: exerciseId }
      };
      
      persistActiveSessionCheckpoint(newState.activeSession);
      
      return newState;
    });
  },

  setActiveSet: (setId) => {
    set((state) => {
      if (!state.activeSession) return state;
      
      const newState = {
        ...state,
        activeSession: { ...state.activeSession, activeSetId: setId }
      };
      
      persistActiveSessionCheckpoint(newState.activeSession);
      
      return newState;
    });
  },

  discardActiveSession: () => {
    clearActiveSessionCheckpoint();
    set((state) => ({
      ...state,
      activeSession: null,
    }));
  },

  substituteExercise: (oldExerciseId, replacement) => set((state) => {
    if (!state.activeSession) return state;

    const sessionExercises = getSessionExercises(state.activeSession.session);
    if (sessionExercises.length === 0) return state;

    const exercises = sessionExercises.map(ex => {
      if (ex.id === oldExerciseId) {
        // Create new exercise instance based on catalog info
        // Keeping the same ID to preserve its position in the list if preferred,
        // but typically a substitution creates a "new" instance in the session.
        // For 1:1, we should probably keep the ID or update it carefully.
        return {
          ...ex,
          exerciseDbId: replacement.id,
          name: replacement.name,
          // We clear the sets if they are not compatible? 
          // PWA usually keeps the same number of sets but resets weights/reps to match new exercise's target if available.
          // For now, let's just swap name and DB id.
        };
      }
      return ex;
    });

    return {
      ...state,
      activeSession: {
        ...state.activeSession,
        session: {
          ...state.activeSession.session,
          exercises
        }
      }
    };
  }),

  completeSet: (exerciseId: string, setId: string, data: OngoingSetData, isCalibrator?: boolean) => {
    const { updateSetData, updateSessionAdjusted1RM, activeSession } = get();
    const { exerciseList } = useExerciseStore.getState();
    const sessionExercises = getSessionExercises(activeSession?.session);

    // Check for PR before updating
    const currentEx = sessionExercises.find(ex => ex.id === exerciseId);
    if (currentEx && data.weight && data.reps) {
      const { calculateBrzycki1RM } = require('../utils/calculations');
      const newE1RM = calculateBrzycki1RM(data.weight, data.reps);
      const oldBest1RM = currentEx.reference1RM || 0;

      if (newE1RM > oldBest1RM * 1.01 && oldBest1RM > 0) {
        // It's a PR! (1% improvement to avoid noise)
        set((state) => ({
          ...state,
          activeSession: state.activeSession ? {
            ...state.activeSession,
            lastPR: {
              exerciseName: currentEx.name,
              weight: data.weight || 0,
              reps: data.reps || 0,
              e1RM: newE1RM
            }
          } : null
        }));
      }
    }

    updateSetData(setId, data);
    
    // Persistir completion inmediatamente para recuperación ante crash
    persistSetCompletion(setId, data);

    if (isCalibrator && data.weight && data.reps && data.reps > 0) {
      const { calculateBrzycki1RM } = require('../utils/calculations');
      const newE1RM = calculateBrzycki1RM(data.weight, data.reps);
      
      if (newE1RM > 0) {
        // En dispositivo móvil, el factor conservador ayuda a evitar saltos bruscos
        const conservative1RM = newE1RM * 0.95;
        
        // Actualizar el ejercicio calibrador (esto ya persistirá el checkpoint vía updateSessionAdjusted1RM)
        updateSessionAdjusted1RM(exerciseId, conservative1RM, true);

        // Propagación inteligente usando el servicio de 1RM y el catálogo
        if (currentEx && sessionExercises.length > 0) {
          const propagationResults = propagate1RM(
            currentEx,
            conservative1RM,
            sessionExercises,
            activeSession.sessionAdjusted1RMs || {},
            exerciseList
          );
          
          // Aplicar propagación a ejercicios relacionados
          propagationResults.forEach(result => {
            if (result.exerciseId !== exerciseId) {
              updateSessionAdjusted1RM(result.exerciseId, result.new1RM, false);
            }
          });
        }
      }
    }
  },

  updateSessionAdjusted1RM: (exerciseId, e1RM, isSource = false) => set((state) => {
    if (!state.activeSession) return state;
    
    // Si es el ejercicio fuente, loguear el cambio para debug
    if (isSource) {
      const old1RM = state.activeSession.sessionAdjusted1RMs?.[exerciseId];
      console.log(`[1RM] ${exerciseId}: ${old1RM || 'N/A'} → ${e1RM} kg (fuente)`);
    }
    
    const newState = {
      ...state,
      activeSession: {
        ...state.activeSession,
        sessionAdjusted1RMs: {
          ...(state.activeSession.sessionAdjusted1RMs || {}),
          [exerciseId]: e1RM
        }
      }
    };

    // Silent Checkpoint de intensidad
    persistActiveSessionCheckpoint(newState.activeSession);
    
    return newState;
  }),

  savePostExerciseFeedback: (exerciseId, feedback) => {
    set((state) => {
      if (!state.activeSession) return state;
      const nextFeedback = {
        ...(state.activeSession.postExerciseFeedback ?? {}),
        [exerciseId]: feedback,
      };
      const nextState = {
        ...state,
        activeSession: {
          ...state.activeSession,
          postExerciseFeedback: nextFeedback,
        },
        notice: 'Feedback de ejercicio guardado.',
      };
      persistActiveSessionCheckpoint(nextState.activeSession);
      return nextState;
    });
  },


   finishActiveSession: async (data) => {

     const { activeSession, postSessionFeedbackHistory } = get();
     if (!activeSession) {
       set({ notice: 'No hay una sesión activa para finalizar.' });
       return;
     }

     const sessionExercises = getSessionExercises(activeSession.session);
     if (sessionExercises.length === 0) {
       set({
         sessionFinishState: 'idle',
         notice: 'La sesión activa no tiene ejercicios válidos.',
       });
       return;
     }

     set({ sessionFinishState: 'saving' });
     try {
       const durationMinutes = data.durationInMinutes ?? Math.round((Date.now() - activeSession.startTime) / 60000);
       
       const completedExercises: CompletedExercise[] = sessionExercises.map(ex => {
           const sets: CompletedSet[] = ex.sets
               .filter(s => activeSession.completedSets[s.id])
               .map(s => {
                   const workoutData = activeSession.completedSets[s.id] as OngoingSetData;
                   return {
                       ...s,
                       weight: workoutData.weight,
                       completedReps: workoutData.reps,
                       completedRPE: workoutData.rpe,
                       completedRIR: workoutData.rir,
                       isFailure: workoutData.isFailure,
                   };
               });
           
           return {
               exerciseId: ex.id,
               exerciseDbId: ex.exerciseDbId,
               exerciseName: ex.name,
               sets,
               machineBrand: activeSession.selectedBrands?.[ex.id],
           };
       }).filter(ex => ex.sets.length > 0);

       const log: WorkoutLog = {
         id: generateId(),
         programId: activeSession.programId,
         programName: get().overview?.activeProgramName ?? 'KPKN',
         sessionId: activeSession.session.id,
         sessionName: activeSession.session.name,
         date: data.logDate ?? new Date().toISOString().slice(0, 10),
         duration: durationMinutes,
         completedExercises,
         fatigueLevel: data.fatigueLevel ?? 5,
         mentalClarity: data.mentalClarity ?? 5,
         focus: data.focus ?? 5,
         pump: data.pump ?? 5,
         environmentTags: data.environmentTags ?? [],
         planAdherenceTags: data.planAdherenceTags ?? [],
         muscleBatteries: data.muscleBatteries ?? {},
         discomforts: data.discomforts ?? [],
         notes: data.notes ?? '',
       };

       // Transform to summary for local history persistence if needed
       const summary: WorkoutLogSummary = {
           id: log.id,
           date: log.date,
           programName: log.programName,
           sessionName: log.sessionName,
           exerciseCount: log.completedExercises.length,
           completedSetCount: log.completedExercises.reduce((acc, ex) => acc + ex.sets.length, 0),
           durationMinutes: log.duration || 0,
       };

       await persistLocalWorkoutLog(log);
       // Also persist full log if SQLite supports it
       await persistDomainPayload(`workout.log.${log.id}`, log);

       // Build the feedback record for the new structure
       const feedbackRecord: PostSessionFeedbackInput = {
         sessionRpe: data.sessionRpe ?? 5,
         energyAfter: data.energyAfter ?? 3,
         sorenessAfter: data.sorenessAfter ?? 3,
         hadPain: data.hadPain ?? false,
         notes: data.notes ?? '',
       };

       let nextFeedbackHistory = postSessionFeedbackHistory;
       if (feedbackRecord) {
         try {
           const record: PostSessionFeedbackRecord = {
             ...feedbackRecord,
             id: generateId(),
             createdAt: new Date().toISOString(),
             programId: activeSession.programId,
             sessionId: activeSession.session.id,
             sessionName: activeSession.session.name,
           };

           nextFeedbackHistory = [record, ...postSessionFeedbackHistory].slice(0, 100);
           await persistDomainPayload('workout.post-session-feedback.v1', nextFeedbackHistory);
         } catch (feedbackError) {
           console.warn('Error guardando feedback:', feedbackError);
         }
       }
       
       const result = await loadWorkoutRuntimeState() || {};
        const nextOverview = result.overview;
        const nextReminders = result.reminderSettings;
       
       // Limpiar checkpoint de sesión activa
       await clearActiveSessionCheckpoint();
       
set({
          status: nextOverview ? 'ready' : 'empty',
          overview: nextOverview,
          reminderSettings: nextReminders,
          activeSession: null,
          sessionFinishState: 'success',
          postSessionFeedbackHistory: nextFeedbackHistory,
          latestPostSessionFeedback: nextFeedbackHistory[0] || null,
          notice: '¡Sesión finalizada y guardada!',
});
console.log('State after clearing activeSession', get().activeSession);

        if (nextReminders) {
         await syncWorkoutInfra(nextOverview, nextReminders);
       }
     } catch (error) {
       set({
         sessionFinishState: 'idle',
         errorMessage: error instanceof Error ? error.message : 'Error al finalizar la sesión.',
       });
     }
   },

  clearLastPR: () => {
    set((state) => ({
      ...state,
      activeSession: state.activeSession ? { ...state.activeSession, lastPR: null } : null
    }));
  },
  clearNotice: () => set({ notice: null }),

  setHistory: (updater) => {
    const current = get().history;
    const next = typeof updater === 'function' ? (updater as (prev: WorkoutLog[]) => WorkoutLog[])(current) : updater;
    persistDomainPayload('workout.history', next);
    set({ history: next });
  },

  addWorkoutLog: (log) => {
    const current = get().history;
    const next = [log, ...current];
    persistDomainPayload('workout.history', next);
    set({ history: next });
  },

  setSkippedLogs: (updater) => {
    const current = get().skippedLogs;
    const next = typeof updater === 'function' ? (updater as (prev: SkippedWorkoutLog[]) => SkippedWorkoutLog[])(current) : updater;
    persistDomainPayload('workout.skippedLogs', next);
    set({ skippedLogs: next });
  },

  setSyncQueue: (updater) => {
    const current = get().syncQueue;
    const next = typeof updater === 'function' ? (updater as (prev: WorkoutLog[]) => WorkoutLog[])(current) : updater;
    persistDomainPayload('workout.syncQueue', next);
    set({ syncQueue: next });
  },
}));
