import { normalizeWikiText, WIKI_JOINTS, WIKI_MOVEMENT_PATTERNS, WIKI_MUSCLES } from '../data/wikiData';
import type { ExerciseCatalogEntry } from '../types/workout';

export interface MobilityRoutineStep {
  name: string;
  durationSeconds: number;
  instruction: string;
  focus: string;
}

export interface MobilityRoutine {
  targetLabel: string;
  summary: string;
  totalDurationSeconds: number;
  detailLink: {
    articleType: 'muscle' | 'joint' | 'pattern';
    articleId: string;
    label: string;
  } | null;
  steps: MobilityRoutineStep[];
}

interface MobilityTargetDefinition {
  id: string;
  label: string;
  aliases: string[];
  summary: string;
  detailLink: MobilityRoutine['detailLink'];
  steps: MobilityRoutineStep[];
}

const MOBILITY_TARGETS: MobilityTargetDefinition[] = [
  {
    id: 'hip',
    label: 'Cadera',
    aliases: ['cadera', 'hip', 'squat', 'sentadilla', 'hinge'],
    summary: 'Abrir la cadera y recuperar control para sentadillas, bisagras y zancadas.',
    detailLink: { articleType: 'joint', articleId: 'hip', label: 'Ver cadera' },
    steps: [
      {
        name: '90/90 switches',
        durationSeconds: 45,
        instruction: 'Alterna posiciones 90/90 sin despegar el tronco del suelo.',
        focus: 'Rotación interna y externa.',
      },
      {
        name: 'Adductor rocks',
        durationSeconds: 45,
        instruction: 'Mueve la pelvis atrás y adelante manteniendo la rodilla estable.',
        focus: 'Aductores y apertura.',
      },
      {
        name: 'Cossack squat hold',
        durationSeconds: 40,
        instruction: 'Baja hacia un lateral y sostiene el rango más cómodo por lado.',
        focus: 'Aductores y tobillo lateral.',
      },
      {
        name: 'Glute bridge march',
        durationSeconds: 45,
        instruction: 'Puente de glúteos con marchas cortas sin perder pelvis neutra.',
        focus: 'Extensión y control pélvico.',
      },
    ],
  },
  {
    id: 'shoulder',
    label: 'Hombro',
    aliases: ['hombro', 'shoulder', 'press', 'empuje', 'overhead'],
    summary: 'Recuperar escápula, rotación y control para empujes y tracciones del tren superior.',
    detailLink: { articleType: 'joint', articleId: 'glenohumeral', label: 'Ver hombro' },
    steps: [
      {
        name: 'Wall slides',
        durationSeconds: 45,
        instruction: 'Desliza brazos sobre la pared sin perder costillas ni cuello.',
        focus: 'Rotación superior de escápula.',
      },
      {
        name: 'Band dislocates',
        durationSeconds: 40,
        instruction: 'Pasa la banda por encima de la cabeza sin forzar el rango.',
        focus: 'Movilidad anterior y control.',
      },
      {
        name: 'Scap push-ups',
        durationSeconds: 45,
        instruction: 'Mantén codos extendidos y mueve solo la escápula.',
        focus: 'Serrato y estabilidad.',
      },
      {
        name: 'Thoracic reach',
        durationSeconds: 40,
        instruction: 'Gira la parte alta de la espalda en cuadrupedia.',
        focus: 'Extensión torácica.',
      },
    ],
  },
  {
    id: 'ankle',
    label: 'Tobillo',
    aliases: ['tobillo', 'ankle', 'lunge', 'zancada', 'calf'],
    summary: 'Ganar dorsiflexión útil para sentadilla, zancadas y aterrizajes.',
    detailLink: { articleType: 'joint', articleId: 'ankle', label: 'Ver tobillo' },
    steps: [
      {
        name: 'Knee to wall',
        durationSeconds: 45,
        instruction: 'Empuja la rodilla hacia la pared sin levantar el talón.',
        focus: 'Dorsiflexión.',
      },
      {
        name: 'Calf pulses',
        durationSeconds: 40,
        instruction: 'Eleva y baja talones con control en una postura de media sentadilla.',
        focus: 'Gemelo y sóleo.',
      },
      {
        name: 'Tibialis raises',
        durationSeconds: 40,
        instruction: 'Levanta la punta del pie manteniendo el talón apoyado.',
        focus: 'Control anterior.',
      },
      {
        name: 'Split squat iso',
        durationSeconds: 45,
        instruction: 'Sostén una zancada isométrica en el rango más limpio posible.',
        focus: 'Tolerancia a carga.',
      },
    ],
  },
  {
    id: 'thoracic',
    label: 'Columna torácica',
    aliases: ['toracica', 'torácica', 'thoracic', 'espalda alta', 'rotation'],
    summary: 'Mejorar extensión y rotación para que el tronco no robe movilidad al hombro o a la cadera.',
    detailLink: { articleType: 'pattern', articleId: 'core-rotation', label: 'Ver patrón rotacional' },
    steps: [
      {
        name: 'Open books',
        durationSeconds: 45,
        instruction: 'Abre el torso desde posición lateral sin despegar la pelvis.',
        focus: 'Rotación torácica.',
      },
      {
        name: 'Foam roller extension',
        durationSeconds: 40,
        instruction: 'Extiende la parte alta de la espalda sobre apoyo blando.',
        focus: 'Extensión torácica.',
      },
      {
        name: 'Thread the needle',
        durationSeconds: 45,
        instruction: 'Pasa el brazo por debajo y sigue con la mirada la rotación.',
        focus: 'Rotación + control escapular.',
      },
      {
        name: 'Dead bug reach',
        durationSeconds: 40,
        instruction: 'Extiende brazo y pierna contrarios sin arquear la zona lumbar.',
        focus: 'Control del core.',
      },
    ],
  },
  {
    id: 'general',
    label: 'General',
    aliases: ['general', 'global', 'full body', 'cuerpo completo', 'warmup'],
    summary: 'Rutina base cuando no hay un foco claro o necesitas entrar al entrenamiento sin rigidez.',
    detailLink: { articleType: 'pattern', articleId: 'squat', label: 'Ver patrón sentadilla' },
    steps: [
      {
        name: 'Cat-camel',
        durationSeconds: 30,
        instruction: 'Mueve la columna de forma suave sin buscar el rango máximo.',
        focus: 'Movilidad segmentaria.',
      },
      {
        name: 'Deep squat pry',
        durationSeconds: 45,
        instruction: 'Sostén una sentadilla profunda y usa los codos para abrir la cadera.',
        focus: 'Apertura global.',
      },
      {
        name: 'World’s greatest stretch',
        durationSeconds: 45,
        instruction: 'Combina cadera, torácica y hombro en un solo patrón.',
        focus: 'Cadena completa.',
      },
      {
        name: 'Breathing reset',
        durationSeconds: 40,
        instruction: 'Respira amplio, suelta costillas y prepara el brace.',
        focus: 'Tono y control.',
      },
    ],
  },
];

function matchesTarget(value: string, target: MobilityTargetDefinition) {
  return target.aliases.some(alias => {
    const normalizedAlias = normalizeWikiText(alias);
    return value.includes(normalizedAlias) || normalizedAlias.includes(value);
  });
}

function pickMobilityTarget(query: string, exerciseList: ExerciseCatalogEntry[]) {
  const normalizedQuery = normalizeWikiText(query.trim());
  if (!normalizedQuery) {
    return MOBILITY_TARGETS[MOBILITY_TARGETS.length - 1];
  }

  const directTarget = MOBILITY_TARGETS.find(target => matchesTarget(normalizedQuery, target));
  if (directTarget) return directTarget;

  const exerciseMatch = exerciseList.find(exercise => normalizeWikiText(exercise.name).includes(normalizedQuery));
  if (exerciseMatch) {
    const exerciseTarget = MOBILITY_TARGETS.find(target => matchesTarget(normalizeWikiText(exerciseMatch.name), target));
    if (exerciseTarget) return exerciseTarget;
  }

  const muscleMatch = WIKI_MUSCLES.find(muscle => {
    const normalizedName = normalizeWikiText(muscle.name);
    const normalizedDescription = normalizeWikiText(muscle.description);
    return normalizedName.includes(normalizedQuery) || normalizedDescription.includes(normalizedQuery);
  });
  if (muscleMatch) {
    if (muscleMatch.relatedJoints?.includes('hip')) return MOBILITY_TARGETS[0];
    if (muscleMatch.relatedJoints?.includes('glenohumeral')) return MOBILITY_TARGETS[1];
    if (muscleMatch.relatedJoints?.includes('ankle')) return MOBILITY_TARGETS[2];
  }

  const jointMatch = WIKI_JOINTS.find(joint => {
    const normalizedName = normalizeWikiText(joint.name);
    const normalizedDescription = normalizeWikiText(joint.description);
    return normalizedName.includes(normalizedQuery) || normalizedDescription.includes(normalizedQuery);
  });
  if (jointMatch) {
    if (jointMatch.id === 'hip') return MOBILITY_TARGETS[0];
    if (jointMatch.id === 'glenohumeral') return MOBILITY_TARGETS[1];
    if (jointMatch.id === 'ankle') return MOBILITY_TARGETS[2];
    if (jointMatch.id === 'lumbar-spine') return MOBILITY_TARGETS[3];
  }

  const patternMatch = WIKI_MOVEMENT_PATTERNS.find(pattern => {
    const normalizedName = normalizeWikiText(pattern.name);
    const normalizedDescription = normalizeWikiText(pattern.description);
    return normalizedName.includes(normalizedQuery) || normalizedDescription.includes(normalizedQuery);
  });
  if (patternMatch) {
    if (patternMatch.id === 'core-rotation') return MOBILITY_TARGETS[3];
    if (patternMatch.id === 'squat' || patternMatch.id === 'lunge' || patternMatch.id === 'hinge') {
      return MOBILITY_TARGETS[0];
    }
  }

  return MOBILITY_TARGETS[MOBILITY_TARGETS.length - 1];
}

export function buildMobilitySuggestions(query: string, exerciseList: ExerciseCatalogEntry[]): string[] {
  const normalizedQuery = normalizeWikiText(query.trim());
  if (normalizedQuery.length < 2) return [];

  const targetSuggestions = MOBILITY_TARGETS.filter(target => matchesTarget(normalizedQuery, target)).map(target => target.label);
  const exerciseSuggestions = exerciseList
    .filter(exercise => normalizeWikiText(exercise.name).includes(normalizedQuery))
    .map(exercise => exercise.name);
  const muscleSuggestions = WIKI_MUSCLES
    .filter(muscle => {
      const normalizedName = normalizeWikiText(muscle.name);
      const normalizedDescription = normalizeWikiText(muscle.description);
      return normalizedName.includes(normalizedQuery) || normalizedDescription.includes(normalizedQuery);
    })
    .map(muscle => muscle.name);
  const jointSuggestions = WIKI_JOINTS
    .filter(joint => {
      const normalizedName = normalizeWikiText(joint.name);
      const normalizedDescription = normalizeWikiText(joint.description);
      return normalizedName.includes(normalizedQuery) || normalizedDescription.includes(normalizedQuery);
    })
    .map(joint => joint.name);

  return Array.from(new Set([...targetSuggestions, ...exerciseSuggestions, ...muscleSuggestions, ...jointSuggestions])).slice(0, 8);
}

export function buildMobilityRoutine(query: string, exerciseList: ExerciseCatalogEntry[]): MobilityRoutine {
  const target = pickMobilityTarget(query, exerciseList);
  return {
    targetLabel: target.label,
    summary: target.summary,
    totalDurationSeconds: target.steps.reduce((sum, step) => sum + step.durationSeconds, 0),
    detailLink: target.detailLink,
    steps: target.steps,
  };
}

