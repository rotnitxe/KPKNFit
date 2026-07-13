// apps/mobile/src/services/ttcService.ts
// Motor TTC (Tendon & Tissue Cost) — Ported from PWA
import { calculateTTC as calculateTTCShared, calculateSetTendonDrain as calculateSetTendonDrainShared } from '@kpkn/shared-domain';
import { getArticularBatteriesForExercise, type ArticularBatteryId } from '../data/articularBatteryConfig';
import { getEffectiveRPE } from './fatigueService';
import type { ExerciseMuscleInfo, CompletedSet } from '../types/workout';

/**
 * Calcula el TTC (Tendon & Tissue Cost) para un ejercicio.
 * Proxy al shared-domain.
 */
export function calculateTTC(
  info: ExerciseMuscleInfo | undefined,
  setName?: string
): number {
  return calculateTTCShared(info as any, setName);
}

/**
 * Calcula el drenaje tendinoso por set, distribuido entre las baterías articulares.
 */
export function calculateSetTendonDrain(
  set: CompletedSet | { completedReps?: number; targetReps?: number; weight?: number; exerciseName?: string } & Record<string, unknown>,
  info: ExerciseMuscleInfo | undefined,
  articularWeights: Record<ArticularBatteryId, number>
): Record<ArticularBatteryId, number> {
  return calculateSetTendonDrainShared(set as any, info as any, articularWeights, getEffectiveRPE) as Record<ArticularBatteryId, number>;
}

/**
 * Obtiene TTC y baterías articulares para un ejercicio.
 */
export function getTendonDrainInput(
  info: ExerciseMuscleInfo | undefined
) {
  const articularWeights = getArticularBatteriesForExercise(info);
  const ttc = calculateTTC(info);
  return { ttc, articularWeights };
}
