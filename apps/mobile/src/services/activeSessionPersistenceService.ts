/**
 * services/activeSessionPersistenceService.ts
 * 
 * Servicio para persistencia y recuperación robusta de sesiones activas.
 * Garantiza que si la app se cierra (crash o sistema), el WorkoutSession
 * se recupere exactamente donde quedó, incluyendo timers de descanso activos
 * y estados parciales de AMRAP.
 */

import { OngoingWorkoutState, OngoingSetData } from '../types/workout';
import { appStorage, getJsonValue, setJsonValue } from '../storage/mmkv';
import {
  getSessionExercises,
  getSessionSetCount,
  normalizeSessionForExecution,
} from '../utils/workoutSession';

export const SESSION_PERSISTENCE_KEY = 'active_session_checkpoint';

/**
 * Checkpoint de sesión para recuperación.
 */
export interface SessionCheckpoint {
  programId: string;
  session: OngoingWorkoutState['session'];
  startTime: number;
  activeExerciseId: string | null;
  activeSetId: string | null;
  completedSets: Record<string, OngoingSetData | { left: OngoingSetData | null; right: OngoingSetData | null }>;
  dynamicWeights: Record<string, { consolidated?: number; technical?: number }>;
  sessionAdjusted1RMs: Record<string, number>;
  selectedBrands: Record<string, string>;
  setTypeOverrides: Record<string, string>;
  checkpointTime: number;
  restTimer?: {
    remainingSeconds: number;
    totalSeconds: number;
    startedAt: number;
    setId?: string;
  } | null;
  isPaused: boolean;
  pauseTime?: number;
}

function resolveCheckpointSelection(
  session: OngoingWorkoutState['session'],
  activeExerciseId: string | null,
  activeSetId: string | null,
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

/**
 * Persiste el estado actual de la sesión activa en MMKV.
 * Se llama después de cada acción importante (completar serie, cambiar peso, etc.)
 */
export async function persistActiveSessionCheckpoint(state: OngoingWorkoutState): Promise<void> {
  const {
    session,
    activeExerciseId,
    activeSetId,
  } = resolveCheckpointSelection(state.session, state.activeExerciseId, state.activeSetId);

  const checkpoint: SessionCheckpoint = {
    programId: state.programId,
    session,
    startTime: state.startTime,
    activeExerciseId,
    activeSetId,
    completedSets: state.completedSets,
    dynamicWeights: state.dynamicWeights,
    sessionAdjusted1RMs: state.sessionAdjusted1RMs || {},
    selectedBrands: state.selectedBrands || {},
    setTypeOverrides: state.setTypeOverrides || {},
    checkpointTime: Date.now(),
    restTimer: null,
    isPaused: state.isPaused || false,
    pauseTime: state.isPaused ? Date.now() : undefined,
  };

  // Persistencia ultra-rápida vía MMKV
  setJsonValue(SESSION_PERSISTENCE_KEY, checkpoint);
}

/**
 * Recupera una sesión activa desde el checkpoint persistido.
 * Retorna null si no hay sesión activa o si expiró (12h TTL).
 */
export async function recoverActiveSession(): Promise<OngoingWorkoutState | null> {
  try {
    const checkpoint = getJsonValue<SessionCheckpoint | null>(SESSION_PERSISTENCE_KEY, null);
    if (!checkpoint) return null;
    
    // Validar que el checkpoint no sea muy antiguo (12 horas TTL per Prompt Maestro)
    const hoursSinceCheckpoint = (Date.now() - checkpoint.checkpointTime) / (1000 * 60 * 60);
    if (hoursSinceCheckpoint > 12) {
      console.log(`[Checkpoint] Sesión de hace ${hoursSinceCheckpoint.toFixed(1)}h ignorada por TTL (12h).`);
      await clearActiveSessionCheckpoint();
      return null;
    }

    const {
      session,
      exercises,
      activeExerciseId,
      activeSetId,
    } = resolveCheckpointSelection(checkpoint.session, checkpoint.activeExerciseId, checkpoint.activeSetId);

    if (exercises.length === 0) {
      await clearActiveSessionCheckpoint();
      return null;
    }
    
    // Recuperar timer de descanso si existe
    let restTimerState = checkpoint.restTimer;
    if (restTimerState && restTimerState.remainingSeconds > 0) {
      const elapsedSeconds = Math.floor((Date.now() - restTimerState.startedAt) / 1000);
      const newRemaining = restTimerState.totalSeconds - elapsedSeconds;
      
      if (newRemaining <= 0) {
        restTimerState = null;
      } else {
        restTimerState = {
          ...restTimerState,
          remainingSeconds: newRemaining,
        };
      }
    }
    
    const recoveredState: OngoingWorkoutState = {
      programId: checkpoint.programId,
      session,
      startTime: checkpoint.startTime,
      activeExerciseId,
      activeSetId,
      completedSets: checkpoint.completedSets,
      dynamicWeights: checkpoint.dynamicWeights,
      sessionAdjusted1RMs: checkpoint.sessionAdjusted1RMs || {},
      selectedBrands: checkpoint.selectedBrands || {},
      setTypeOverrides: (checkpoint.setTypeOverrides as any) || {},
      isPaused: checkpoint.isPaused,
    };
    
    return recoveredState;
  } catch (error) {
    console.error('Error recuperando sesión activa:', error);
    return null;
  }
}

/**
 * Limpia el checkpoint de sesión activa.
 */
export async function clearActiveSessionCheckpoint(): Promise<void> {
  appStorage.delete(SESSION_PERSISTENCE_KEY);
}


/**
 * Persiste el estado de un timer de descanso activo.
 */
export async function persistRestTimer(params: {
  remainingSeconds: number;
  totalSeconds: number;
  setId?: string;
}): Promise<void> {
  const checkpoint = getJsonValue<SessionCheckpoint | null>(SESSION_PERSISTENCE_KEY, null);
  if (!checkpoint) return;

  checkpoint.restTimer = {
    remainingSeconds: params.remainingSeconds,
    totalSeconds: params.totalSeconds,
    startedAt: Date.now(),
    setId: params.setId,
  };
  checkpoint.checkpointTime = Date.now();
  
  setJsonValue(SESSION_PERSISTENCE_KEY, checkpoint);
}

/**
 * Recupera el estado del timer de descanso.
 */
export async function recoverRestTimer(): Promise<{
  remainingSeconds: number;
  totalSeconds: number;
  setId?: string;
} | null> {
  try {
    const checkpoint = getJsonValue<SessionCheckpoint | null>(SESSION_PERSISTENCE_KEY, null);
    if (!checkpoint?.restTimer || checkpoint.restTimer.remainingSeconds <= 0) return null;
    
    // Calcular tiempo restante real
    const elapsedSeconds = Math.floor((Date.now() - checkpoint.restTimer.startedAt) / 1000);
    const newRemaining = checkpoint.restTimer.totalSeconds - elapsedSeconds;
    
    if (newRemaining <= 0) return null;
    
    return {
      remainingSeconds: newRemaining,
      totalSeconds: checkpoint.restTimer.totalSeconds,
      setId: checkpoint.restTimer.setId,
    };
  } catch (error) {
    console.error('Error recuperando timer:', error);
    return null;
  }
}

/**
 * Marca una serie como completada en el checkpoint persistido.
 */
export async function persistSetCompletion(
  setId: string,
  data: OngoingSetData
): Promise<void> {
  const checkpoint = getJsonValue<SessionCheckpoint | null>(SESSION_PERSISTENCE_KEY, null);
  if (!checkpoint) return;
  
  checkpoint.completedSets[setId] = data;
  checkpoint.checkpointTime = Date.now();
  
  setJsonValue(SESSION_PERSISTENCE_KEY, checkpoint);
}

/**
 * Verifica si hay una sesión activa persistida (12h TTL).
 */
export async function hasActiveSession(): Promise<boolean> {
  const checkpoint = getJsonValue<SessionCheckpoint | null>(SESSION_PERSISTENCE_KEY, null);
  if (!checkpoint) return false;

  const { exercises } = resolveCheckpointSelection(
    checkpoint.session,
    checkpoint.activeExerciseId,
    checkpoint.activeSetId,
  );
  if (exercises.length === 0) return false;
  
  const hoursSinceCheckpoint = (Date.now() - checkpoint.checkpointTime) / (1000 * 60 * 60);
  return hoursSinceCheckpoint <= 12;
}

/**
 * Obtiene información resumida de la sesión activa.
 */
export async function getActiveSessionSummary(): Promise<{
  sessionName: string;
  completedSets: number;
  totalSets: number;
  elapsedMinutes: number;
} | null> {
  try {
    const checkpoint = getJsonValue<SessionCheckpoint | null>(SESSION_PERSISTENCE_KEY, null);
    if (!checkpoint) return null;

    const { session, exercises } = resolveCheckpointSelection(
      checkpoint.session,
      checkpoint.activeExerciseId,
      checkpoint.activeSetId,
    );
    if (exercises.length === 0) return null;
    
    const completedSetsCount = Object.keys(checkpoint.completedSets).length;
    const totalSetsCount = getSessionSetCount(session);
    const elapsedMinutes = Math.floor((Date.now() - checkpoint.startTime) / (1000 * 60));
    
    return {
      sessionName: session.name,
      completedSets: completedSetsCount,
      totalSets: totalSetsCount,
      elapsedMinutes,
    };
  } catch {
    return null;
  }
}
