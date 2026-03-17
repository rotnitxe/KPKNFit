// services/tendonRecoveryService.ts
// Recuperación de baterías articulares (tendones y articulaciones)
// Half-life más largo que muscular; penalización acumulativa si se entrena antes de 100%

import type { WorkoutLog, ExerciseMuscleInfo, Settings } from '../types';
import { buildExerciseIndex, findExerciseWithFallback } from '../utils/exerciseIndex';
import { calculateSetTendonDrain } from './ttcService';
import {
  getArticularBatteriesForExercise,
  ARTICULAR_BATTERIES,
} from '../data/articularBatteryConfig';
import type { MuscleGroupInfo, ArticularBatteryId, ArticularBatteryState } from '../types';
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
