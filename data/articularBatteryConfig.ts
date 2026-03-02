// data/articularBatteryConfig.ts
// Las 5 baterías articulares principales (tendones y articulaciones)
// Referencia: Manual Módulo de Batería Estructural Periférica

import type { ExerciseMuscleInfo } from '../types';
import { INITIAL_MUSCLE_GROUP_DATA } from './initialMuscleGroupDatabase';

export type ArticularBatteryId = 'shoulder' | 'elbow' | 'knee' | 'hip' | 'ankle';

export const ARTICULAR_BATTERIES: {
  id: ArticularBatteryId;
  label: string;
  shortLabel: string;
  jointIds: string[];
  tendonIds: string[];
}[] = [
  {
    id: 'shoulder',
    label: 'Hombro',
    shortLabel: 'Hombro',
    jointIds: ['glenohumeral'],
    tendonIds: ['tendon-supraespinoso', 'tendon-infraespinoso', 'tendon-bíceps-largo'],
  },
  {
    id: 'elbow',
    label: 'Codo y Antebrazo',
    shortLabel: 'Codo',
    jointIds: ['codo', 'muñeca', 'radiocubital-proximal'],
    tendonIds: ['tendon-bíceps', 'tendon-tríceps', 'tendon-flexores-muñeca', 'tendon-extensores-muñeca'],
  },
  {
    id: 'knee',
    label: 'Rodilla',
    shortLabel: 'Rodilla',
    jointIds: ['rodilla'],
    tendonIds: ['tendon-rotuliano', 'tendon-cuádriceps', 'tendon-isquiotibiales'],
  },
  {
    id: 'hip',
    label: 'Cadera',
    shortLabel: 'Cadera',
    jointIds: ['cadera', 'sacroiliaca'],
    tendonIds: ['tendon-iliopsoas'],
  },
  {
    id: 'ankle',
    label: 'Tobillo',
    shortLabel: 'Tobillo',
    jointIds: ['tobillo'],
    tendonIds: ['tendon-aquiles'],
  },
];

// Mapeo de nombre de músculo (en involvedMuscles) a id del MuscleGroupInfo
const MUSCLE_NAME_TO_ID: Record<string, string> = {};
for (const mg of INITIAL_MUSCLE_GROUP_DATA) {
  MUSCLE_NAME_TO_ID[mg.name.toLowerCase()] = mg.id;
  // Variantes comunes
  if (mg.name.includes(' ')) {
    const parts = mg.name.split(' ');
    MUSCLE_NAME_TO_ID[parts[0].toLowerCase()] = mg.id;
  }
}
// Mapeos adicionales por alias
const ALIASES: Record<string, string> = {
  'pectoral': 'pectoral-medio', 'pecho': 'pectoral-medio', 'pectorales': 'pectoral-medio',
  'dorsal': 'dorsal-ancho', 'dorsales': 'dorsal-ancho', 'lats': 'dorsal-ancho',
  'deltoides': 'deltoides-lateral', 'hombro': 'deltoides-lateral',
  'deltoides anterior': 'deltoides-anterior', 'deltoides lateral': 'deltoides-lateral', 'deltoides posterior': 'deltoides-posterior',
  'bíceps': 'bíceps', 'biceps': 'bíceps', 'braquial': 'braquial', 'braquiorradial': 'braquiorradial',
  'tríceps': 'tríceps', 'triceps': 'tríceps',
  'cuádriceps': 'cuádriceps', 'cuadriceps': 'cuádriceps', 'quads': 'cuádriceps', 'recto femoral': 'recto-femoral', 'vasto': 'vasto-lateral',
  'isquiosurales': 'isquiosurales', 'isquiotibiales': 'isquiosurales', 'hamstrings': 'isquiosurales', 'bíceps femoral': 'bíceps-femoral',
  'glúteos': 'glúteo-mayor', 'gluteos': 'glúteo-mayor', 'glúteo': 'glúteo-mayor', 'gluteo mayor': 'glúteo-mayor', 'glúteo medio': 'glúteo-medio',
  'pantorrillas': 'gastrocnemio', 'gemelos': 'gastrocnemio', 'gastrocnemio': 'gastrocnemio', 'sóleo': 'sóleo', 'soleo': 'sóleo',
  'abdomen': 'recto-abdominal', 'abdominal': 'recto-abdominal', 'oblicuos': 'oblicuos', 'core': 'recto-abdominal',
  'trapecio': 'trapecio', 'trapecios': 'trapecio',
  'erectores': 'erectores-espinales', 'espalda baja': 'erectores-espinales', 'lumbar': 'erectores-espinales',
  'antebrazo': 'antebrazo', 'flexores': 'flexores-de-antebrazo', 'extensores': 'extensores-de-antebrazo',
  'aductores': 'aductores', 'cuerpo completo': 'cuádriceps', // fallback para full-body
};

function resolveMuscleId(muscleName: string): string | null {
  const lower = muscleName.toLowerCase().trim();
  if (ALIASES[lower]) return ALIASES[lower];
  if (MUSCLE_NAME_TO_ID[lower]) return MUSCLE_NAME_TO_ID[lower];
  for (const [alias, id] of Object.entries(ALIASES)) {
    if (lower.includes(alias) || alias.includes(lower)) return id;
  }
  for (const mg of INITIAL_MUSCLE_GROUP_DATA) {
    if (lower.includes(mg.name.toLowerCase()) || mg.name.toLowerCase().includes(lower)) return mg.id;
  }
  return null;
}

/**
 * Obtiene las baterías articulares afectadas por un ejercicio con sus pesos (0–1).
 * Usa involvedMuscles → relatedJoints/relatedTendons y force/bodyPart como fallback.
 */
export function getArticularBatteriesForExercise(
  info: ExerciseMuscleInfo | undefined,
  muscleGroupData: { id: string; relatedJoints?: string[]; relatedTendons?: string[] }[] = INITIAL_MUSCLE_GROUP_DATA
): Record<ArticularBatteryId, number> {
  const result: Record<ArticularBatteryId, number> = {
    shoulder: 0,
    elbow: 0,
    knee: 0,
    hip: 0,
    ankle: 0,
  };

  if (!info) return result;

  const muscleMap = new Map<string, { id: string; relatedJoints: string[]; relatedTendons: string[] }>();
  for (const mg of muscleGroupData as { id: string; relatedJoints?: string[]; relatedTendons?: string[] }[]) {
    muscleMap.set(mg.id, {
      id: mg.id,
      relatedJoints: mg.relatedJoints ?? [],
      relatedTendons: mg.relatedTendons ?? [],
    });
  }

  // 1. Por involvedMuscles → relatedJoints/relatedTendons
  const seenJoints = new Set<string>();
  const seenTendons = new Set<string>();
  for (const { muscle, role, activation = 1 } of info.involvedMuscles ?? []) {
    const weight = role === 'primary' ? 1 : role === 'secondary' ? 0.6 : 0.3;
    const muscleId = resolveMuscleId(muscle);
    if (!muscleId) continue;
    const data = muscleMap.get(muscleId);
    if (!data) continue;
    for (const j of data.relatedJoints) {
      if (seenJoints.has(j)) continue;
      seenJoints.add(j);
      for (const ab of ARTICULAR_BATTERIES) {
        if (ab.jointIds.includes(j)) {
          result[ab.id] = Math.max(result[ab.id], weight * (activation ?? 1));
          break;
        }
      }
    }
    for (const t of data.relatedTendons) {
      if (seenTendons.has(t)) continue;
      seenTendons.add(t);
      for (const ab of ARTICULAR_BATTERIES) {
        if (ab.tendonIds.includes(t)) {
          result[ab.id] = Math.max(result[ab.id], weight * (activation ?? 1) * 1.2); // tendones = más peso
          break;
        }
      }
    }
  }

  // 2. Fallback por force + bodyPart (cuando no hay joints/tendons explícitos)
  if (Object.values(result).every(v => v === 0)) {
    const bodyPart = info.bodyPart ?? 'full';
    const force = info.force ?? 'Otro';
    if (bodyPart === 'upper' || bodyPart === 'full') {
      if (force === 'Empuje' || force === 'Tirón') {
        result.shoulder = 0.8;
        result.elbow = 0.6;
      }
      if (force === 'Flexión' || force === 'Extensión') result.elbow = 0.9;
    }
    if (bodyPart === 'lower' || bodyPart === 'full') {
      if (force === 'Sentadilla') {
        result.knee = 0.8;
        result.hip = 0.6;
      }
      if (force === 'Bisagra') {
        result.hip = 0.8;
        result.knee = 0.4;
      }
      if (force === 'Salto') {
        result.knee = 0.9;
        result.ankle = 0.8;
      }
    }
  }

  // Normalizar a 0-1
  for (const k of Object.keys(result) as ArticularBatteryId[]) {
    result[k] = Math.min(1, result[k]);
  }
  return result;
}
