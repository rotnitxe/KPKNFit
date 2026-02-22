// data/exerciseDatabaseMerged.ts
// Fusiona DETAILED_EXERCISE_LIST con exerciseDatabaseExtended.json (extraído de BASE DE DATOS.docx)

import { ExerciseMuscleInfo } from '../types';
import { DETAILED_EXERCISE_LIST } from './exerciseDatabase';
import extendedRaw from './exerciseDatabaseExtended.json';

function normalizeExtended(ex: Record<string, unknown>): ExerciseMuscleInfo {
  const name = String(ex.name || '');
  const id = String(ex.id || `ext_${name.toLowerCase().replace(/\s+/g, '-').replace(/[^\w-]/g, '')}`);

  // Mapear type "C" -> "Básico", "A" -> "Accesorio", etc.
  let type = String(ex.type || 'Accesorio');
  if (type === 'C' || type === 'c') type = 'Básico';
  else if (type === 'A' || type === 'a') type = 'Accesorio';
  else if (type === 'I' || type === 'i') type = 'Aislamiento';
  else if (!['Básico', 'Accesorio', 'Aislamiento'].includes(type)) type = 'Accesorio';

  // Inferir bodyPart y force desde el nombre
  const n = name.toLowerCase();
  let bodyPart = String(ex.bodyPart || 'upper');
  let force = String(ex.force || 'Otro');
  if (n.includes('sentadilla') || n.includes('prensa') || n.includes('zancada') || n.includes('pierna') || n.includes('cuádriceps') || n.includes('glúteo') || n.includes('gemelo') || n.includes('femoral') || n.includes('isquio')) {
    bodyPart = 'lower';
    if (n.includes('sentadilla') || n.includes('prensa')) force = 'Sentadilla';
  }
  if (n.includes('peso muerto') || n.includes('remo') || n.includes('hip thrust') || n.includes('buenos días') || n.includes('rumanian') || n.includes('rdl')) {
    bodyPart = 'lower';
    force = 'Bisagra';
  }
  if (n.includes('press') || n.includes('empuje') || n.includes('flexión') || n.includes('fondo')) force = 'Empuje';
  if (n.includes('dominada') || n.includes('remo') || n.includes('jalón') || n.includes('tirón') || n.includes('curl')) force = 'Tirón';

  const primary = (ex.involvedMuscles as { muscle: string; role: string; activation: number }[]) || [];
  const involvedMuscles = primary.length > 0
    ? primary
    : [{ muscle: 'General', role: 'primary' as const, activation: 1.0 }];

  return {
    id,
    name,
    description: String(ex.description || ''),
    involvedMuscles,
    subMuscleGroup: String(ex.subMuscleGroup || ''),
    category: String(ex.category || 'Hipertrofia'),
    type: type as 'Básico' | 'Accesorio' | 'Aislamiento',
    equipment: String(ex.equipment || 'Otro'),
    force: force as ExerciseMuscleInfo['force'],
    bodyPart: bodyPart as 'upper' | 'lower' | 'core',
    chain: String(ex.chain || 'anterior'),
    setupTime: typeof ex.setupTime === 'number' ? ex.setupTime : 3,
    technicalDifficulty: typeof ex.technicalDifficulty === 'number' ? ex.technicalDifficulty : 5,
    transferability: typeof ex.transferability === 'number' ? ex.transferability : 6,
    injuryRisk: typeof ex.injuryRisk === 'object' && ex.injuryRisk !== null
      ? ex.injuryRisk as { level: number; details?: string }
      : { level: 5, details: '' },
    efc: typeof ex.efc === 'number' ? ex.efc : 2.5,
    cnc: typeof ex.cnc === 'number' ? ex.cnc : 2.5,
    ssc: typeof ex.ssc === 'number' ? ex.ssc : 0.2,
  };
}

const extendedExercises: ExerciseMuscleInfo[] = Array.isArray(extendedRaw)
  ? (extendedRaw as Record<string, unknown>[]).map(normalizeExtended)
  : [];

const existingIds = new Set(DETAILED_EXERCISE_LIST.map(e => e.id));
const merged: ExerciseMuscleInfo[] = [...DETAILED_EXERCISE_LIST];
for (const ex of extendedExercises) {
  if (!existingIds.has(ex.id)) {
    existingIds.add(ex.id);
    merged.push(ex);
  }
}

export const FULL_EXERCISE_LIST: ExerciseMuscleInfo[] = merged;
