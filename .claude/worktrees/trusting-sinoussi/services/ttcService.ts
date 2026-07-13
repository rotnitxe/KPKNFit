// services/ttcService.ts
// Motor TTC (Tendon & Tissue Cost) - Batería Estructural Periférica
// Fórmula: TTC = Base × EquipmentMod × ContractionMod (topado a 5.0)

import type { ExerciseMuscleInfo } from '../types';
import { getEffectiveRPE } from './fatigueService';
import { calculateTTC as calculateTTCShared, calculateSetTendonDrain as calculateSetTendonDrainShared } from '@kpkn/shared-domain';
import {
  getArticularBatteriesForExercise,
  type ArticularBatteryId,
} from '../data/articularBatteryConfig';
import type { CompletedSet } from '../types';

const TTC_MAX = 5.0;

/**
 * Calcula el TTC (Tendon & Tissue Cost) para un ejercicio.
 * Proxy al shared-domain.
 */
export function calculateTTC(
  info: ExerciseMuscleInfo | undefined,
  setName?: string
): number {
  return calculateTTCShared(info, setName);
}

/**
 * Calcula el drenaje tendinoso por set, distribuido entre las baterías articulares.
 * Proxy al shared-domain.
 */
export function calculateSetTendonDrain(
  set: CompletedSet | { completedReps?: number; targetReps?: number; weight?: number; exerciseName?: string } & Record<string, unknown>,
  info: ExerciseMuscleInfo | undefined,
  articularWeights: Record<ArticularBatteryId, number>
): Record<ArticularBatteryId, number> {
  return calculateSetTendonDrainShared(set, info, articularWeights, getEffectiveRPE) as Record<ArticularBatteryId, number>;
}

/**
 * Obtiene TTC y baterías articulares para un ejercicio.
 * Wrapper que combina getArticularBatteriesForExercise y calculateTTC.
 */
export function getTendonDrainInput(
  info: ExerciseMuscleInfo | undefined,
  muscleGroupData?: { id: string; relatedJoints?: string[]; relatedTendons?: string[] }[]
) {
  const articularWeights = getArticularBatteriesForExercise(info, muscleGroupData);
  const ttc = calculateTTC(info);
  return { ttc, articularWeights };
}
