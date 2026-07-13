// utils/sessionDayLabel.ts
// Genera etiqueta dinámica según músculos involucrados en la sesión

import { Session, Exercise, ExerciseMuscleInfo } from '../types';
import { buildExerciseIndex, findExercise } from './exerciseIndex';

function muscleToGroup(muscle: string): string | null {
    const m = muscle.toLowerCase();
    if (m.includes('pector') || m.includes('pecho')) return 'pecho';
    if (m.includes('dorsal') || m.includes('trapecio') || m.includes('romboide') || m.includes('redondo')) return 'espalda';
    if (m.includes('deltoides') || m.includes('supraespinoso') || m.includes('infraespinoso') || m.includes('manguito') || m.includes('serrato')) return 'hombros';
    if (m.includes('bíceps') || m.includes('tríceps') || m.includes('braquial') || m.includes('antebrazo')) return 'brazos';
    if (m.includes('cuádriceps') || m.includes('isquio') || m.includes('femoral') || m.includes('semitend') || m.includes('semimembr') ||
        m.includes('glúteo') || m.includes('pantorrilla') || m.includes('gastrocnemio') || m.includes('sóleo') || m.includes('aductor') ||
        m.includes('tibial anterior') || m.includes('gemelo')) return 'piernas';
    if (m.includes('abdom') || m.includes('oblicuo') || m.includes('transverso') || m.includes('core') || m.includes('erector') || m.includes('espalda baja')) return 'core';
    return null;
}

const GROUP_LABELS: Record<string, string> = {
    pecho: 'pecho',
    espalda: 'espalda',
    hombros: 'hombros',
    brazos: 'brazos',
    piernas: 'pierna',
    core: 'core',
};

export function getSessionDayLabel(session: Session, exerciseList: ExerciseMuscleInfo[]): string {
    if (!session || !exerciseList.length) return 'Día de acción';

    const exIndex = buildExerciseIndex(exerciseList);
    const exercises: Exercise[] = session.exercises ?? [];
    const fromParts = (session.parts ?? []).flatMap(p => p.exercises ?? []);
    const allExercises = [...exercises, ...fromParts];

    const groupCounts: Record<string, number> = {};

    for (const ex of allExercises) {
        const info = findExercise(exIndex, ex.exerciseDbId ?? ex.exerciseId, ex.name);
        if (!info?.involvedMuscles?.length) continue;
        for (const { muscle, role } of info.involvedMuscles) {
            if (role !== 'primary') continue;
            const group = muscleToGroup(muscle);
            if (group) groupCounts[group] = (groupCounts[group] ?? 0) + 1;
        }
    }

    const groups = Object.entries(groupCounts)
        .filter(([, c]) => c > 0)
        .sort((a, b) => b[1] - a[1])
        .map(([g]) => g);

    if (groups.length === 0) return 'Día de acción';

    // Tren superior completo: pecho + espalda + hombros (con o sin brazos)
    const hasPecho = groups.includes('pecho');
    const hasEspalda = groups.includes('espalda');
    const hasHombros = groups.includes('hombros');
    if (hasPecho && hasEspalda && hasHombros) return 'Día de tren superior completo';

    // Cuerpo completo: upper + lower
    const upper = ['pecho', 'espalda', 'hombros', 'brazos'];
    const hasUpper = groups.some(g => upper.includes(g));
    const hasLower = groups.includes('piernas');
    if (hasUpper && hasLower) return 'Día de cuerpo completo';

    // Un solo grupo principal
    if (groups.length === 1) return `Día de ${GROUP_LABELS[groups[0]] ?? groups[0]}`;

    // Dos grupos: "Día de pecho y hombros" etc
    if (groups.length === 2) {
        const a = GROUP_LABELS[groups[0]] ?? groups[0];
        const b = GROUP_LABELS[groups[1]] ?? groups[1];
        return `Día de ${a} y ${b}`;
    }

    // Tres o más: usar el principal
    return `Día de ${GROUP_LABELS[groups[0]] ?? groups[0]}`;
}
