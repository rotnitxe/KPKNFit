// Mapea mÇ§sculos principales de la sesiÇün a las baterÇðas reales del acordeÇün.

import { Session, Exercise, ExerciseMuscleInfo } from '../types';
import { buildExerciseIndex, findExercise } from './exerciseIndex';
import { ACCORDION_MUSCLES } from '../services/recoveryService';
import { getExercisePrimaryDisplayMuscles } from './canonicalMuscles';

export interface SessionMuscleForBattery {
    id: string;
    label: string;
    battery: number;
}

export function getSessionMusclesWithBatteries(
    session: Session,
    exerciseList: ExerciseMuscleInfo[],
    perMuscleBatteries: Record<string, number>
): SessionMuscleForBattery[] {
    if (!session || !exerciseList.length) return [];

    const exIndex = buildExerciseIndex(exerciseList);
    const exercises: Exercise[] = session.exercises ?? [];
    const fromParts = (session.parts ?? []).flatMap(p => p.exercises ?? []);
    const allExercises = [...exercises, ...fromParts];

    const seen = new Set<string>();
    const result: SessionMuscleForBattery[] = [];

    for (const ex of allExercises) {
        const info = findExercise(exIndex, ex.exerciseDbId ?? ex.exerciseId, ex.name);
        if (!info?.involvedMuscles?.length) continue;

        for (const muscleId of getExercisePrimaryDisplayMuscles(info)) {
            if (seen.has(muscleId)) continue;
            seen.add(muscleId);

            const label = ACCORDION_MUSCLES.find(a => a.id === muscleId)?.label ?? muscleId;
            const battery = perMuscleBatteries[muscleId] ?? 100;
            result.push({ id: muscleId, label, battery });
        }
    }

    return result.sort((a, b) => b.battery - a.battery);
}
