// Mapea mÇ§sculos principales de la sesiÇün a las baterÇðas reales del acordeÇün.

import { Session, Exercise, ExerciseMuscleInfo } from '../types';
import { buildExerciseIndex, findExercise } from './exerciseIndex';
import { ACCORDION_MUSCLES } from '../services/recoveryService';
import { getExercisePrimaryDisplayMuscles } from './canonicalMuscles';

const LOWER_SESSION_MUSCLE_KEYS = new Set([
    'cuadriceps',
    'isquiosurales',
    'gluteos',
    'aductores',
    'pantorrillas',
]);

function normalizeKey(value: string): string {
    return value
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .trim();
}

function isUpperOnlySession(
    session: Session,
    allExercises: Exercise[],
    exIndex: ReturnType<typeof buildExerciseIndex>
): boolean {
    let upperCount = 0;
    let lowerCount = 0;
    let fullCount = 0;

    for (const ex of allExercises) {
        const info = findExercise(exIndex, ex.exerciseDbId ?? ex.exerciseId, ex.name);
        if (!info?.bodyPart) continue;
        if (info.bodyPart === 'upper') upperCount += 1;
        else if (info.bodyPart === 'lower') lowerCount += 1;
        else if (info.bodyPart === 'full') fullCount += 1;
    }

    if (upperCount > 0 && lowerCount === 0 && fullCount === 0) return true;

    const normalizedSessionLabel = normalizeKey(`${session.name || ''} ${session.focus || ''}`);
    const looksUpper =
        normalizedSessionLabel.includes('tren superior') ||
        normalizedSessionLabel.includes('upper') ||
        normalizedSessionLabel.includes('torso');
    const looksLower =
        normalizedSessionLabel.includes('tren inferior') ||
        normalizedSessionLabel.includes('lower') ||
        normalizedSessionLabel.includes('pierna');

    return upperCount === 0 && lowerCount === 0 && fullCount === 0 && looksUpper && !looksLower;
}

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
    const upperOnly = isUpperOnlySession(session, allExercises, exIndex);

    const seen = new Set<string>();
    const result: SessionMuscleForBattery[] = [];

    for (const ex of allExercises) {
        const info = findExercise(exIndex, ex.exerciseDbId ?? ex.exerciseId, ex.name);
        if (!info?.involvedMuscles?.length) continue;

        for (const muscleId of getExercisePrimaryDisplayMuscles(info)) {
            if (upperOnly && LOWER_SESSION_MUSCLE_KEYS.has(normalizeKey(muscleId))) continue;
            if (seen.has(muscleId)) continue;
            seen.add(muscleId);

            const label = ACCORDION_MUSCLES.find(a => a.id === muscleId)?.label ?? muscleId;
            const battery = perMuscleBatteries[muscleId] ?? 100;
            result.push({ id: muscleId, label, battery });
        }
    }

    return result.sort((a, b) => b.battery - a.battery);
}
