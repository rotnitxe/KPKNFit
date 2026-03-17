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
  CompletedExercise,
  CompletedSet,
  SetTypeLabel,
  Exercise,
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

  finishActiveSession: (feedback?: PostSessionFeedbackInput) => Promise<void>;
  clearNotice: () => void;
}


async function syncWorkoutInfra(overview: WorkoutOverview | null, reminderSettings: CoreReminderSettings) {
  await syncWorkoutWidgetState(overview);
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

  hydrateFromMigration: async () => {
    set({ errorMessage: null, notice: null });
    try {
      const { overview, reminderSettings } = await loadWorkoutRuntimeState();
      
      // Cargar feedback persistido
      const feedbackHistory = await loadPersistedDomainPayload<PostSessionFeedbackRecord[]>('workout.post-session-feedback.v1') || [];

      set({
        status: overview ? 'ready' : 'empty',
        overview,
        reminderSettings,
        postSessionFeedbackHistory: feedbackHistory,
        latestPostSessionFeedback: feedbackHistory[0] || null,
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
      await syncWorkoutInfra(overview, reminderSettings);
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
      set({
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
    }
  },

  setReadinessScore: (score) => set((state) => ({
    ...state,
    readinessScore: score,
  })),

  startActiveSession: (payload) => {
    set((state) => {
      if (state.activeSession?.session.id === payload.session.id) {
        return state;
      }
      
      const newSession: OngoingWorkoutState = {
        programId: payload.programId,
        session: payload.session,
        startTime: Date.now(),
        activeExerciseId: payload.session.exercises[0]?.id || null,
        activeSetId: payload.session.exercises[0]?.sets[0]?.id || null,
        completedSets: {},
        dynamicWeights: {},
        sessionAdjusted1RMs: {},
        selectedBrands: {},
        setTypeOverrides: {},
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
        set((state) => ({
          ...state,
          activeSession: recovered,
          notice: `Sesión recuperada: ${recovered.session.name}`,
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

    const exercises = state.activeSession.session.exercises.map(ex => {
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
        const currentEx = activeSession?.session.exercises.find(ex => ex.id === exerciseId);
        if (currentEx && activeSession?.session.exercises) {
          const propagationResults = propagate1RM(
            currentEx,
            conservative1RM,
            activeSession.session.exercises,
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


  finishActiveSession: async (feedback) => {

    const { activeSession, postSessionFeedbackHistory } = get();
    if (!activeSession) {
      set({ notice: 'No hay una sesión activa para finalizar.' });
      return;
    }

    set({ sessionFinishState: 'saving' });
    try {
      const durationMinutes = Math.round((Date.now() - activeSession.startTime) / 60000);
      
      const completedExercises: CompletedExercise[] = activeSession.session.exercises.map(ex => {
          const sets: CompletedSet[] = ex.sets
              .filter(s => activeSession.completedSets[s.id])
              .map(s => {
                  const data = activeSession.completedSets[s.id] as OngoingSetData;
                  return {
                      ...s,
                      weight: data.weight,
                      completedReps: data.reps,
                      completedRPE: data.rpe,
                      completedRIR: data.rir,
                      isFailure: data.isFailure,
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
        date: new Date().toISOString().slice(0, 10),
        duration: durationMinutes,
        completedExercises,
        fatigueLevel: feedback?.energyAfter ?? 3,
        mentalClarity: 3, // placeholder
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

      await persistLocalWorkoutLog(summary);
      // Also persist full log if SQLite supports it
      await persistDomainPayload(`workout.log.${log.id}`, log);

      let nextFeedbackHistory = postSessionFeedbackHistory;
      if (feedback) {
        try {
          const record: PostSessionFeedbackRecord = {
            ...feedback,
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
      
      const { overview: nextOverview, reminderSettings: nextReminders } = await loadWorkoutRuntimeState();
      
      // Limpiar checkpoint de sesión activa
      await clearActiveSessionCheckpoint();
      
      set({
        status: nextOverview ? 'ready' : 'empty',
        overview: nextOverview,
        reminderSettings: nextReminders,
        activeSession: null,
        sessionFinishState: 'idle',
        postSessionFeedbackHistory: nextFeedbackHistory,
        latestPostSessionFeedback: nextFeedbackHistory[0] || null,
        notice: '¡Sesión finalizada y guardada!',
      });

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

  clearNotice: () => set({ notice: null }),
}));
