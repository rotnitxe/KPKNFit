// utils/sessionArticularBatteries.ts
// Baterías articulares afectadas por una sesión planificada

import type { Session, Exercise, ExerciseMuscleInfo } from '../types';
import { buildExerciseIndex, findExercise } from '../utils/exerciseIndex';
import { getArticularBatteriesForExercise, ARTICULAR_BATTERIES, type ArticularBatteryId } from '../data/articularBatteryConfig';

export interface SessionArticularBattery {
  id: ArticularBatteryId;
  label: string;
  shortLabel: string;
  battery: number;
}

/**
 * Devuelve las baterías articulares afectadas por la sesión con sus valores actuales.
 */
export function getSessionArticularBatteries(
  session: Session,
  exerciseList: ExerciseMuscleInfo[],
  articularBatteries: Record<ArticularBatteryId, { recoveryScore: number }>
): SessionArticularBattery[] {
  if (!session || !exerciseList.length) return [];

  const exIndex = buildExerciseIndex(exerciseList);
  const exercises: Exercise[] = session.exercises ?? [];
  const fromParts = (session.parts ?? []).flatMap((p) => p.exercises ?? []);
  const allExercises = [...exercises, ...fromParts];

  const affected = new Set<ArticularBatteryId>();
  for (const ex of allExercises) {
    const info = findExercise(exIndex, ex.exerciseDbId ?? ex.exerciseId, ex.name);
    const weights = getArticularBatteriesForExercise(info);
    for (const [id, w] of Object.entries(weights)) {
      if (w > 0) affected.add(id as ArticularBatteryId);
    }
  }

  return Array.from(affected).map((id) => {
    const config = ARTICULAR_BATTERIES.find((a) => a.id === id);
    return {
      id,
      label: config?.label ?? id,
      shortLabel: config?.shortLabel ?? id,
      battery: articularBatteries[id]?.recoveryScore ?? 100,
    };
  });
}
