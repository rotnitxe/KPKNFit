/**
 * services/sessionRecoveryService.ts
 * 
 * Servicio para integrar el feedback post-entreno con el motor AUGE.
 * Calcula el impacto de la sesión en las baterías de recuperación y 
 * actualiza el estado de Readiness para el día siguiente.
 */

import type { WorkoutLog, CompletedExercise } from '../types/workout';
import type { Settings } from '../types/settings';
import {
  calculateCompletedSessionDrainBreakdown,
  calculateCompletedSessionStress,
} from './fatigueService';
import { useExerciseStore } from '../stores/exerciseStore';

export interface SessionRecoveryImpact {
  totalStress: number;
  cnsDrain: number;
  muscularDrain: number;
  spinalDrain: number;
  readinessAdjustment: number;  // Cuánto restar al Readiness de mañana
  recommendedRecoveryActions: RecoveryAction[];
}

export interface RecoveryAction {
  type: 'nutrition' | 'sleep' | 'active-recovery' | 'deload';
  priority: 'high' | 'medium' | 'low';
  title: string;
  description: string;
}

/**
 * Calcula el impacto de una sesión completada en la recuperación.
 * Usa el motor AUGE para determinar el drenaje por sistema.
 */
export function calculateSessionRecoveryImpact(
  completedExercises: CompletedExercise[],
  feedback: {
    sessionRpe: number;
    energyAfter: number;
    sorenessAfter: number;
    hadPain: boolean;
  },
  settings: Settings
): SessionRecoveryImpact {
  const { exerciseList } = useExerciseStore.getState();
  
  // Calcular drenaje por sistema usando AUGE
  const breakdown = calculateCompletedSessionDrainBreakdown(
    completedExercises,
    exerciseList,
    settings
  );

  // Factor de intensidad basado en el RPE de sesión
  const rpeMultiplier = 0.5 + (feedback.sessionRpe / 10) * 0.5; // 0.5..1.0
  
  // Factor de energía residual (menos energía = más impacto)
  const energyFactor = (6 - feedback.energyAfter) / 5; // 1..5 → 1.0..0.2
  
  // Factor de dolor (más dolor = más impacto en recuperación)
  const sorenessFactor = 1 + (feedback.sorenessAfter / 5) * 0.3; // 1..5 → 1.0..1.6

  // Ajustar drenaje con los factores de feedback
  const adjustedCnsDrain = breakdown.cnsDrain * rpeMultiplier * energyFactor;
  const adjustedMuscularDrain = breakdown.muscularDrain * rpeMultiplier * sorenessFactor;
  const adjustedSpinalDrain = breakdown.spinalDrain * rpeMultiplier * (feedback.hadPain ? 1.5 : 1);

  const totalStress = adjustedCnsDrain + adjustedMuscularDrain + adjustedSpinalDrain;
  
  // Calcular ajuste al Readiness (0..100 scale)
  // Una sesión muy dura (150+ stress) puede bajar el readiness en 20-30 puntos
  const readinessAdjustment = Math.min(40, totalStress / 5);

  // Generar recomendaciones de recuperación
  const recommendedRecoveryActions: RecoveryAction[] = [];

  if (adjustedCnsDrain > 60) {
    recommendedRecoveryActions.push({
      type: 'sleep',
      priority: 'high',
      title: 'Prioriza el sueño',
      description: 'Tu SNC está fatigado. Intenta dormir 8-9 horas hoy.',
    });
  }

  if (adjustedMuscularDrain > 70) {
    recommendedRecoveryActions.push({
      type: 'nutrition',
      priority: 'high',
      title: 'Superávit calórico',
      description: 'Tus músculos necesitan combustible. Aumenta proteínas y carbohidratos hoy.',
    });
  }

  if (feedback.sorenessAfter >= 4) {
    recommendedRecoveryActions.push({
      type: 'active-recovery',
      priority: 'medium',
      title: 'Recuperación activa',
      description: 'Camina 20-30 min o haz movilidad ligera para reducir DOMS.',
    });
  }

  if (totalStress > 150) {
    recommendedRecoveryActions.push({
      type: 'deload',
      priority: 'high',
      title: 'Considera deload',
      description: 'Sesión muy demandante. Evalúa reducir volumen en el próximo entreno.',
    });
  }

  return {
    totalStress: Math.round(totalStress),
    cnsDrain: Math.round(adjustedCnsDrain),
    muscularDrain: Math.round(adjustedMuscularDrain),
    spinalDrain: Math.round(adjustedSpinalDrain),
    readinessAdjustment: Math.round(readinessAdjustment),
    recommendedRecoveryActions,
  };
}

/**
 * Aplica el ajuste de Readiness al wellbeing del día siguiente.
 * Esto se integra con el dailyWellbeingLogs del wellbeingStore.
 */
export function applyReadinessAdjustment(
  targetDate: string,
  adjustment: number,
  currentReadiness?: {
    sleep: number;
    mood: number;
    soreness: number;
  }
): { sleep: number; mood: number; soreness: number } {
  // El ajuste afecta principalmente al mood (energía/fatiga CNS)
  // y al soreness (fatiga muscular)
  
  const baseSleep = currentReadiness?.sleep ?? 3;
  const baseMood = currentReadiness?.mood ?? 3;
  const baseSoreness = currentReadiness?.soreness ?? 3;

  // Ajustar mood basado en el drenaje de CNS
  const moodAdjustment = Math.min(2, adjustment / 15); // Máximo -2 puntos
  const adjustedMood = Math.max(1, baseMood - moodAdjustment);

  // Ajustar soreness basado en drenaje muscular
  const sorenessIncrease = Math.min(2, adjustment / 12); // Máximo +2 puntos
  const adjustedSoreness = Math.min(5, baseSoreness + sorenessIncrease);

  return {
    sleep: baseSleep, // El sleep no se afecta directamente, es input del usuario
    mood: Math.round(adjustedMood),
    soreness: Math.round(adjustedSoreness),
  };
}

/**
 * Clasifica el nivel de estrés de la sesión con etiquetas semánticas.
 */
export function classifySessionStress(stress: number): {
  label: string;
  color: string;
  recoveryTime: string;
} {
  if (stress < 60) {
    return { label: 'Ligero', color: '#38BDF8', recoveryTime: '24h' };
  }
  if (stress < 100) {
    return { label: 'Moderado', color: '#00FF9D', recoveryTime: '24-48h' };
  }
  if (stress < 150) {
    return { label: 'Alto', color: '#FFD600', recoveryTime: '48-72h' };
  }
  return { label: 'Muy Alto', color: '#FF2E43', recoveryTime: '72h+' };
}
