// data/exerciseDatabaseMerged.ts
// Fusiona DETAILED_EXERCISE_LIST con exerciseDatabaseExtended.json (extraído de BASE DE DATOS.docx)

import { ExerciseMuscleInfo } from '../types';
import { DETAILED_EXERCISE_LIST } from './exerciseDatabase';
import { inferInvolvedMuscles } from './inferMusclesFromName';
import extendedRaw from './exerciseDatabaseExtended.json';

function normalizeExtended(ex: Record<string, unknown>): ExerciseMuscleInfo {
  const name = String(ex.name || '');
  const equipment = String(ex.equipment || '');
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
    ? primary.map(m => ({ muscle: m.muscle, role: (m.role || 'primary') as 'primary' | 'secondary' | 'stabilizer', activation: m.activation ?? 1.0 }))
    : inferInvolvedMuscles(name, equipment, force, bodyPart);

  // Mostrar variante (equipment) junto al nombre para que sea visible
  const displayName = equipment && equipment.trim() && equipment !== 'Otro'
    ? `${name} (${equipment})`
    : name;

  return {
    id,
    name: displayName,
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

function enrichWithOperationalData(ex: ExerciseMuscleInfo): ExerciseMuscleInfo {
  const hasCore = ex.involvedMuscles?.some(m =>
    ['Core', 'Abdomen', 'Espalda Baja', 'Recto Abdominal', 'Transverso Abdominal'].includes(m.muscle) && (m.activation || 0) >= 0.3
  );
  const isCompound = ex.type === 'Básico' && ['Barra', 'Peso Corporal'].includes(ex.equipment || '');
  const isPull = ex.force === 'Tirón' || ex.force === 'Bisagra';
  const isHeavyPull = isPull && (ex.equipment === 'Barra' || ex.subMuscleGroup?.toLowerCase().includes('dorsal'));

  return {
    ...ex,
    averageRestSeconds: ex.averageRestSeconds ?? (ex.type === 'Básico' ? 120 : ex.type === 'Aislamiento' ? 60 : 90),
    coreInvolvement: ex.coreInvolvement ?? (hasCore ? (isCompound ? 'high' as const : 'medium' as const) : 'low' as const),
    bracingRecommended: ex.bracingRecommended ?? (isCompound && (ex.force === 'Sentadilla' || ex.force === 'Bisagra' || ex.force === 'Empuje')),
    strapsRecommended: ex.strapsRecommended ?? (isHeavyPull && (ex.name?.toLowerCase().includes('peso muerto') || ex.name?.toLowerCase().includes('remo') || ex.name?.toLowerCase().includes('dominada') || ex.name?.toLowerCase().includes('jalón'))),
    bodybuildingScore: ex.bodybuildingScore ?? (ex.category === 'Hipertrofia' ? (ex.type === 'Básico' ? 8 : ex.type === 'Aislamiento' ? 7 : 7.5) : 6),
  };
}

const existingIds = new Set(DETAILED_EXERCISE_LIST.map(e => e.id));
const merged: ExerciseMuscleInfo[] = [...DETAILED_EXERCISE_LIST];
for (const ex of extendedExercises) {
  if (!existingIds.has(ex.id)) {
    existingIds.add(ex.id);
    merged.push(ex);
  }
}

/** Elimina únicamente duplicados exactos (100% idénticos: id, name, involvedMuscles, etc.) */
function removeExactDuplicates(list: ExerciseMuscleInfo[]): ExerciseMuscleInfo[] {
  const seen = new Map<string, ExerciseMuscleInfo>();
  for (const ex of list) {
    const key = JSON.stringify({
      id: ex.id,
      name: ex.name,
      involvedMuscles: ex.involvedMuscles?.map(m => ({ muscle: m.muscle, role: m.role, activation: m.activation })).sort((a, b) => a.muscle.localeCompare(b.muscle)),
      equipment: ex.equipment,
      type: ex.type,
    });
    if (!seen.has(key)) seen.set(key, ex);
  }
  return Array.from(seen.values());
}

const deduplicated = removeExactDuplicates(merged);
export const FULL_EXERCISE_LIST: ExerciseMuscleInfo[] = deduplicated.map(enrichWithOperationalData);
