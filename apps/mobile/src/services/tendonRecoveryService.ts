// apps/mobile/src/services/tendonRecoveryService.ts
// Recuperación de baterías articulares (tendones y articulaciones) — Ported from PWA

import type { WorkoutLog, ExerciseMuscleInfo, MuscleGroupInfo } from '../types/workout';
import type { Settings } from '../types/settings';
import type { ArticularBatteryId, ArticularBatteryState } from '@kpkn/shared-types';
import { buildExerciseIndex, findExerciseWithFallback } from '../utils/exerciseIndex';
import { calculateSetTendonDrain } from './ttcService';
import { getArticularBatteriesForExercise, ARTICULAR_BATTERIES } from '../data/articularBatteryConfig';
import { calculateArticularBatteries as calculateShared, MUSCLE_TO_ARTICULAR_BATTERIES } from '@kpkn/shared-domain';

export type { ArticularBatteryState };

export function calculateArticularBatteries(
  history: WorkoutLog[],
  exerciseList: ExerciseMuscleInfo[],
  muscleGroupData: MuscleGroupInfo[],
  settings?: Settings
): Record<ArticularBatteryId, ArticularBatteryState> {
  return calculateShared(
    history as any,
    {
      buildExerciseIndex: buildExerciseIndex as any,
      findExerciseWithFallback: findExerciseWithFallback as any,
      getArticularBatteriesForExercise: getArticularBatteriesForExercise as any,
      calculateSetTendonDrain: calculateSetTendonDrain as any,
    },
    exerciseList as any,
    muscleGroupData as any,
    settings
  );
}

export { MUSCLE_TO_ARTICULAR_BATTERIES, ARTICULAR_BATTERIES };
