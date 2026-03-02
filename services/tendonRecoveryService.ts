// services/tendonRecoveryService.ts
// Recuperación de baterías articulares (tendones y articulaciones)
// Half-life más largo que muscular; penalización acumulativa si se entrena antes de 100%

import type { WorkoutLog, ExerciseMuscleInfo, Settings } from '../types';
import { buildExerciseIndex, findExerciseWithFallback } from '../utils/exerciseIndex';
import { calculateSetTendonDrain } from './ttcService';
import {
  getArticularBatteriesForExercise,
  ARTICULAR_BATTERIES,
  type ArticularBatteryId,
} from '../data/articularBatteryConfig';
import type { MuscleGroupInfo } from '../types';
import { INITIAL_MUSCLE_GROUP_DATA } from '../data/initialMuscleGroupDatabase';

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));
const safeExp = (v: number) => {
  const r = Math.exp(v);
  return isNaN(r) || !isFinite(r) ? 0 : r;
};

/** Capacidad de referencia por batería articular (puntos de TTC-stress) */
const TENDON_CAPACITY_BASE = 80;

/** Tiempo base de recuperación (horas) para sesión con TTC medio > 3.0 */
const TENDON_RECOVERY_HIGH_TTC = 60;
/** Tiempo base estándar */
const TENDON_RECOVERY_STD = 48;

/** Penalización por fatiga acumulativa (entrenar antes de 100%) */
const CUMULATIVE_PENALTY = 0.1;

export interface ArticularBatteryState {
  recoveryScore: number;
  estimatedHoursToRecovery: number;
  status: 'optimal' | 'recovering' | 'exhausted';
  accumulatedStress: number;
}

export function calculateArticularBatteries(
  history: WorkoutLog[],
  exerciseList: ExerciseMuscleInfo[],
  muscleGroupData: MuscleGroupInfo[] = INITIAL_MUSCLE_GROUP_DATA,
  _settings?: Settings
): Record<ArticularBatteryId, ArticularBatteryState> {
  const now = Date.now();
  const exIndex = buildExerciseIndex(exerciseList);
  const tenDaysMs = 10 * 24 * 3600 * 1000;
  const relevantLogs = history
    .filter((l) => now - new Date(l.date).getTime() < tenDaysMs)
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());

  const capacity = TENDON_CAPACITY_BASE;

  const result: Record<ArticularBatteryId, ArticularBatteryState> = {
    shoulder: makeInitialState(),
    elbow: makeInitialState(),
    knee: makeInitialState(),
    hip: makeInitialState(),
    ankle: makeInitialState(),
  };

  for (const id of Object.keys(result) as ArticularBatteryId[]) {
    result[id] = { ...result[id], accumulatedStress: 0 };
  }

  // Para penalización: última vez que cada batería fue drenada (timestamp)
  const lastDrainTime: Record<ArticularBatteryId, number> = {
    shoulder: 0,
    elbow: 0,
    knee: 0,
    hip: 0,
    ankle: 0,
  };

  for (const log of relevantLogs) {
    const logTime = new Date(log.date).getTime();
    const hoursSince = Math.max(0, (now - logTime) / 3600000);

    const sessionDrain: Record<ArticularBatteryId, number> = {
      shoulder: 0,
      elbow: 0,
      knee: 0,
      hip: 0,
      ankle: 0,
    };

    for (const ex of log.completedExercises) {
      const info = findExerciseWithFallback(
        exIndex,
        ex.exerciseDbId,
        ex.exerciseName
      );
      const articularWeights = getArticularBatteriesForExercise(
        info,
        muscleGroupData
      );

      if (Object.values(articularWeights).every((w) => w === 0)) continue;

      for (const s of ex.sets) {
        if ((s as any).type === 'warmup' || (s as any).isIneffective) continue;
        const drain = calculateSetTendonDrain(s, info, articularWeights);
        for (const id of Object.keys(drain) as ArticularBatteryId[]) {
          sessionDrain[id] += drain[id];
        }
      }
    }

    // Penalización acumulativa: si se drenó esta batería en los últimos 72h, +10%
    const recoveryWindowMs = 72 * 3600 * 1000;
    for (const id of Object.keys(sessionDrain) as ArticularBatteryId[]) {
      if (sessionDrain[id] > 0 && lastDrainTime[id] > 0 && logTime - lastDrainTime[id] < recoveryWindowMs) {
        sessionDrain[id] *= 1 + CUMULATIVE_PENALTY;
      }
      if (sessionDrain[id] > 0) lastDrainTime[id] = logTime;
    }

    const totalTTC = Object.values(sessionDrain).reduce((a, b) => a + b, 0);
    const drainedCount = Object.values(sessionDrain).filter((v) => v > 0).length;
    const avgTTC = drainedCount > 0 ? totalTTC / drainedCount : 0;
    const recoveryHours = avgTTC > 3 ? TENDON_RECOVERY_HIGH_TTC : TENDON_RECOVERY_STD;
    const k = 2.0 / recoveryHours;

    for (const id of Object.keys(sessionDrain) as ArticularBatteryId[]) {
      const stress = sessionDrain[id];
      if (stress <= 0) continue;
      const remaining = stress * safeExp(-k * hoursSince);
      result[id].accumulatedStress += remaining;
    }
  }

  for (const id of Object.keys(result) as ArticularBatteryId[]) {
    const acc = result[id].accumulatedStress;
    const battery = clamp(100 - (acc / capacity) * 100, 0, 100);
    result[id].recoveryScore = Math.round(battery);
    result[id].status = battery < 40 ? 'exhausted' : battery < 85 ? 'recovering' : 'optimal';

    const target = 90;
    if (battery < target && acc > 0) {
      const targetStress = ((100 - target) / 100) * capacity;
      const k = 2.0 / TENDON_RECOVERY_STD;
      result[id].estimatedHoursToRecovery = Math.round(
        Math.max(0, -Math.log(targetStress / acc) / k)
      );
    } else {
      result[id].estimatedHoursToRecovery = 0;
    }
  }

  return result;
}

function makeInitialState(): ArticularBatteryState {
  return {
    recoveryScore: 100,
    estimatedHoursToRecovery: 0,
    status: 'optimal',
    accumulatedStress: 0,
  };
}

/** Mapeo accordion muscle id → baterías articulares relacionadas */
export const MUSCLE_TO_ARTICULAR_BATTERIES: Record<string, ArticularBatteryId[]> = {
  'deltoides-anterior': ['shoulder'],
  'deltoides-lateral': ['shoulder'],
  'deltoides-posterior': ['shoulder'],
  'pectorales': ['shoulder'],
  'dorsales': ['shoulder'],
  'bíceps': ['shoulder', 'elbow'],
  'tríceps': ['elbow'],
  'cuádriceps': ['knee', 'hip'],
  'isquiosurales': ['knee', 'hip'],
  'glúteos': ['hip'],
  'pantorrillas': ['ankle'],
  'antebrazo': ['elbow'],
  'trapecio': ['shoulder'],
  'espalda baja': ['hip'],
};

export { ARTICULAR_BATTERIES };
