// utils/sessionMusclesForBattery.ts
// Mapea músculos de la sesión a IDs del acordeón para lookup de baterías

import { Session, Exercise, ExerciseMuscleInfo } from '../types';
import { buildExerciseIndex, findExercise } from './exerciseIndex';
import { ACCORDION_MUSCLES } from '../services/recoveryService';

// Keywords por accordion id (orden: más específico primero). Evitar 'lateral' y 'medio' sueltos: mapean mal Glúteo Medio, Vasto Lateral, etc.
const MUSCLE_TO_ACCORDION: { id: string; keywords: string[] }[] = [
    { id: 'deltoides-anterior', keywords: ['deltoides anterior', 'deltoide anterior'] },
    { id: 'deltoides-lateral', keywords: ['deltoides lateral', 'deltoide lateral'] },
    { id: 'deltoides-posterior', keywords: ['deltoides posterior', 'deltoide posterior'] },
    { id: 'pectorales', keywords: ['pectoral', 'pecho'] },
    { id: 'dorsales', keywords: ['dorsal', 'dorsales', 'redondo', 'lats', 'romboides'] },
    { id: 'bíceps', keywords: ['bíceps', 'biceps', 'braquial', 'braquiorradial'] },
    { id: 'tríceps', keywords: ['tríceps', 'triceps'] },
    { id: 'cuádriceps', keywords: ['cuádriceps', 'cuadriceps', 'recto femoral', 'vasto'] },
    { id: 'isquiosurales', keywords: ['isquiosurales', 'isquiotibiales', 'bíceps femoral', 'semitendinoso', 'semimembranoso', 'femoral'] },
    { id: 'glúteos', keywords: ['glúteo', 'gluteo'] },
    { id: 'pantorrillas', keywords: ['pantorrilla', 'gemelo', 'gastrocnemio', 'sóleo'] },
    { id: 'abdomen', keywords: ['abdomen', 'abdominal', 'oblicuo', 'recto abdominal', 'transverso'] },
    { id: 'trapecio', keywords: ['trapecio'] },
    { id: 'espalda baja', keywords: ['erector', 'espinal', 'lumbar', 'espalda baja', 'cuadrado lumbar'] },
    { id: 'core', keywords: ['core'] },
    { id: 'aductores', keywords: ['aductor'] },
    { id: 'antebrazo', keywords: ['antebrazo', 'flexores', 'extensores'] },
];

function muscleToAccordionId(muscle: string): string | null {
    const m = muscle.toLowerCase();
    for (const { id, keywords } of MUSCLE_TO_ACCORDION) {
        if (keywords.some(k => m.includes(k))) return id;
    }
    return null;
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

    const seen = new Set<string>();
    const result: SessionMuscleForBattery[] = [];

    for (const ex of allExercises) {
        const info = findExercise(exIndex, ex.exerciseDbId ?? ex.exerciseId, ex.name);
        if (!info?.involvedMuscles?.length) continue;
        for (const { muscle, role } of info.involvedMuscles) {
            if (role !== 'primary') continue;
            const accordionId = muscleToAccordionId(muscle);
            if (!accordionId || seen.has(accordionId)) continue;
            seen.add(accordionId);
            const label = ACCORDION_MUSCLES.find(a => a.id === accordionId)?.label ?? accordionId;
            const battery = perMuscleBatteries[accordionId] ?? 100;
            result.push({ id: accordionId, label, battery });
        }
    }

    return result.sort((a, b) => b.battery - a.battery);
}
