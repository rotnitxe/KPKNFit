// apps/mobile/src/data/articularBatteryConfig.ts
// Las 5 baterías articulares principales (tendones y articulaciones) — Ported from PWA
import type { ExerciseMuscleInfo } from '../types/workout';
import { INITIAL_MUSCLE_GROUP_DATA } from './initialMuscleGroupDatabase';

export type ArticularBatteryId = 'shoulder' | 'elbow' | 'knee' | 'hip' | 'ankle' | 'cervical';

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
  {
    id: 'cervical',
    label: 'Cuello y Cervical',
    shortLabel: 'Cuello',
    jointIds: ['columna-cervical'],
    tendonIds: [],
  },
];

const ALIASES: Record<string, string> = {
  'pectoral': 'pectoral', 'pecho': 'pectoral', 'pectorales': 'pectoral',
  'dorsal': 'espalda', 'dorsales': 'espalda', 'lats': 'espalda',
  'deltoides': 'deltoides', 'hombro': 'deltoides',
  'bíceps': 'bíceps', 'biceps': 'bíceps',
  'tríceps': 'tríceps', 'triceps': 'tríceps',
  'cuádriceps': 'cuadriceps', 'cuadriceps': 'cuadriceps', 'quads': 'cuadriceps',
  'isquiosurales': 'isquiosurales', 'isquiotibiales': 'isquiosurales', 'hamstrings': 'isquiosurales',
  'glúteos': 'gluteos', 'gluteos': 'gluteos', 'glúteo': 'gluteos',
  'pantorrillas': 'pantorrillas', 'gemelos': 'pantorrillas',
  'abdomen': 'abdomen', 'abdominal': 'abdomen', 'core': 'abdomen',
  'erectores': 'espalda', 'espalda baja': 'espalda',
  'antebrazo': 'antebrazo',
  'aductores': 'aductores',
  'cuello': 'cuello', 'cervical': 'cuello',
};

function normalizeText(value: string | undefined | null): string {
  return (value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function resolveMuscleId(muscleName: string): string | null {
  const lower = normalizeText(muscleName);
  if (ALIASES[lower]) return ALIASES[lower];
  for (const [alias, id] of Object.entries(ALIASES)) {
    if (lower.includes(alias) || alias.includes(lower)) return id;
  }
  for (const mg of INITIAL_MUSCLE_GROUP_DATA) {
    if (lower.includes(normalizeText(mg.name)) || normalizeText(mg.name).includes(lower)) return mg.id;
  }
  return null;
}

export function getArticularBatteriesForExercise(
  info: ExerciseMuscleInfo | undefined,
  muscleGroupData: { id: string; relatedJoints?: string[]; relatedTendons?: string[] }[] = INITIAL_MUSCLE_GROUP_DATA as any
): Record<ArticularBatteryId, number> {
  const result: Record<ArticularBatteryId, number> = {
    shoulder: 0,
    elbow: 0,
    knee: 0,
    hip: 0,
    ankle: 0,
    cervical: 0,
  };

  if (!info) return result;

  const muscleMap = new Map<string, { id: string; relatedJoints: string[]; relatedTendons: string[] }>();
  for (const mg of muscleGroupData) {
    muscleMap.set(mg.id, {
      id: mg.id,
      relatedJoints: mg.relatedJoints ?? [],
      relatedTendons: mg.relatedTendons ?? [],
    });
  }

  for (const { muscle, role, activation = 1 } of info.involvedMuscles ?? []) {
    const weight = role === 'primary' ? 1 : role === 'secondary' ? 0.6 : 0.3;
    const muscleId = resolveMuscleId(typeof muscle === 'string' ? muscle : (muscle as any).name);
    if (!muscleId) continue;
    const data = muscleMap.get(muscleId);
    if (!data) continue;
    for (const j of data.relatedJoints) {
      for (const ab of ARTICULAR_BATTERIES) {
        if (ab.jointIds.includes(j)) {
          result[ab.id] = Math.max(result[ab.id], weight * activation);
          break;
        }
      }
    }
    for (const t of data.relatedTendons) {
      for (const ab of ARTICULAR_BATTERIES) {
        if (ab.tendonIds.includes(t)) {
          result[ab.id] = Math.max(result[ab.id], weight * activation * 1.2);
          break;
        }
      }
    }
  }

  // Fallback simple por bodyPart/force si no hay mapeo fino
  if (Object.values(result).every(v => v === 0)) {
     if (info.bodyPart === 'upper') {
         result.shoulder = 0.7;
         result.elbow = 0.5;
     } else if (info.bodyPart === 'lower') {
         result.knee = 0.8;
         result.hip = 0.6;
     }
  }

  for (const k of Object.keys(result) as ArticularBatteryId[]) {
    result[k] = Math.min(1, result[k]);
  }
  return result;
}
