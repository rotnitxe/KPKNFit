import type { LocalAiNutritionAnalysisRequest } from '@kpkn/shared-types';
import type { CoachChatMessage, CoachContextSnapshot } from '../types/coach';
import type { WorkoutOverview, WellbeingOverview } from '@kpkn/shared-types';
import type { BodyProgressEntry } from '../types/workout';
import type { SavedNutritionEntry } from '../types/nutrition';
import type { ExerciseMuscleInfo } from '../types/workout';
import { analyzeNutritionDraft as analyzeNutritionDraftWithLocalRuntime } from './nutritionAnalyzer';
import {
  buildCoachContextSnapshot as buildCoachContextSnapshotFromState,
  generateCoachReply as generateCoachReplyFromRules,
  generateCoachBriefing as generateCoachBriefingFromRules,
  summarizeConversationTitle as summarizeConversationTitleFromRules,
} from './coachChatService';

// Punto de entrada central para capacidades IA dentro de RN.
// Mantiene API estable aunque internamente combinemos runtime local, reglas o proveedor remoto.

export function analyzeNutritionDraft(request: LocalAiNutritionAnalysisRequest) {
  return analyzeNutritionDraftWithLocalRuntime(request);
}

export function buildCoachContextSnapshot(
  workoutOverview: WorkoutOverview | null,
  bodyProgress: BodyProgressEntry[],
  savedNutritionLogs: SavedNutritionEntry[],
  wellbeingOverview: WellbeingOverview | null,
) {
  return buildCoachContextSnapshotFromState(
    workoutOverview,
    bodyProgress,
    savedNutritionLogs,
    wellbeingOverview,
  );
}

export function generateCoachReply(input: {
  userText: string;
  context: CoachContextSnapshot;
  recentMessages: CoachChatMessage[];
}) {
  return generateCoachReplyFromRules(input);
}

export function summarizeConversationTitle(firstUserText: string) {
  return summarizeConversationTitleFromRules(firstUserText);
}

export function generateCoachBriefing(context: CoachContextSnapshot) {
  return generateCoachBriefingFromRules(context);
}

type WeightHistoryEntry = {
  date: string;
  weight?: number;
};

type TrainingPurposeSuggestion = {
  name: string;
  justification: string;
  primaryMuscles: string[];
};

const PURPOSE_PROFILES: Array<{
  keywords: string[];
  muscles: string[];
  focus: string;
}> = [
  {
    keywords: ['futbol', 'rugby', 'sprint', 'aceleracion', 'explosivo', 'salto', 'vertical'],
    muscles: ['quadriceps', 'hamstrings', 'glutes', 'calves', 'core'],
    focus: 'potencia, aceleracion y transferencia atletica',
  },
  {
    keywords: ['boxeo', 'golpe', 'puno', 'pegada'],
    muscles: ['shoulders', 'triceps', 'chest', 'core', 'lats'],
    focus: 'potencia de tren superior y estabilidad del core',
  },
  {
    keywords: ['fuerte', 'fuerza', 'powerlifting', 'sentadilla', 'peso muerto', 'bench'],
    muscles: ['glutes', 'hamstrings', 'quadriceps', 'chest', 'lats', 'core'],
    focus: 'fuerza maxima en patrones principales',
  },
  {
    keywords: ['salud', 'longeva', 'longevidad', 'vida'],
    muscles: ['glutes', 'core', 'back', 'hamstrings', 'shoulders'],
    focus: 'base fuerte, salud articular y sostenibilidad',
  },
  {
    keywords: ['ski', 'pretemporada'],
    muscles: ['quadriceps', 'glutes', 'hamstrings', 'core', 'calves'],
    focus: 'resistencia de piernas y estabilidad para giros',
  },
];

function isValidWeightHistoryEntry(entry: WeightHistoryEntry): entry is Required<Pick<WeightHistoryEntry, 'date'>> & { weight: number } {
  return typeof entry.date === 'string' && Number.isFinite(entry.weight);
}

function normalizeText(value: string) {
  return value
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}

function getPrimaryMuscles(exercise: ExerciseMuscleInfo) {
  const primary = (exercise.involvedMuscles ?? [])
    .filter(item => item.role === 'primary')
    .map(item => String(item.muscle).toLowerCase());
  if (primary.length > 0) {
    return Array.from(new Set(primary)).slice(0, 3);
  }
  const fallback = (exercise.involvedMuscles ?? [])
    .map(item => String(item.muscle).toLowerCase())
    .filter(Boolean);
  return Array.from(new Set(fallback)).slice(0, 3);
}

function pickPurposeProfile(purpose: string) {
  const normalized = normalizeText(purpose);
  let best = PURPOSE_PROFILES[0];
  let bestScore = -1;
  PURPOSE_PROFILES.forEach(profile => {
    const score = profile.keywords.reduce(
      (acc, keyword) => (normalized.includes(keyword) ? acc + 1 : acc),
      0,
    );
    if (score > bestScore) {
      best = profile;
      bestScore = score;
    }
  });
  return best;
}

function buildFallbackSuggestions(profile: { focus: string }) {
  const fallback: TrainingPurposeSuggestion[] = [
    {
      name: 'Sentadilla trasera',
      justification: `Prioriza control y progresion de carga para mejorar ${profile.focus}.`,
      primaryMuscles: ['quadriceps', 'glutes', 'core'],
    },
    {
      name: 'Peso muerto rumano',
      justification: `Aporta fuerza de cadena posterior para mejorar ${profile.focus}.`,
      primaryMuscles: ['hamstrings', 'glutes', 'erectors'],
    },
    {
      name: 'Press militar',
      justification: `Desarrolla estabilidad de hombro y transferencia global de fuerza.`,
      primaryMuscles: ['shoulders', 'triceps', 'upper_back'],
    },
    {
      name: 'Remo con barra',
      justification: `Refuerza espalda alta para sostener tecnica y postura bajo fatiga.`,
      primaryMuscles: ['lats', 'upper_back', 'biceps'],
    },
    {
      name: 'Plancha cargada',
      justification: `Mejora rigidez del core para transmitir fuerza de forma eficiente.`,
      primaryMuscles: ['core', 'obliques'],
    },
  ];
  return fallback;
}

export function generateWeightProjection(
  avgIntake: number,
  tdee: number,
  weightHistory: WeightHistoryEntry[],
  targetWeight: number,
  _settings?: unknown,
) {
  const logsWithWeight = [...weightHistory]
    .filter(isValidWeightHistoryEntry)
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());

  if (logsWithWeight.length === 0) {
    return Promise.resolve({
      projection: 'No estimable todavía',
      summary: 'Aún no hay suficientes registros para estimar tu meta.',
    });
  }

  const startWeight = logsWithWeight[0].weight;
  const currentWeight = logsWithWeight[logsWithWeight.length - 1].weight;
  const weeklyChange =
    logsWithWeight.length > 1
      ? (currentWeight - startWeight) / Math.max(1, logsWithWeight.length - 1)
      : 0;
  const weeksToGoal =
    weeklyChange !== 0
      ? Math.ceil(Math.abs((currentWeight - targetWeight) / weeklyChange))
      : null;

  return Promise.resolve({
    projection: weeksToGoal ? `${Math.abs(weeksToGoal)} semanas` : 'No estimable todavía',
    summary:
      avgIntake > tdee
        ? 'Tu consumo calórico actual es mayor que tu TDEE. Considera reducir un 10-15% para acelerar el progreso.'
        : 'Tu consumo calórico es adecuado. Mantén el ritmo actual.',
  });
}

export function generateExercisesForPurpose(
  purpose: string,
  options?: { exerciseCatalog?: ExerciseMuscleInfo[] },
): Promise<{ exercises: TrainingPurposeSuggestion[] }> {
  const finalPurpose = purpose.trim();
  if (!finalPurpose) {
    return Promise.resolve({ exercises: [] });
  }

  const profile = pickPurposeProfile(finalPurpose);
  const catalog = options?.exerciseCatalog ?? [];
  if (catalog.length === 0) {
    return Promise.resolve({ exercises: buildFallbackSuggestions(profile) });
  }

  const normalizedPurpose = normalizeText(finalPurpose);

  const ranked = catalog
    .map(exercise => {
      const normalizedName = normalizeText(exercise.name);
      const primaryMuscles = getPrimaryMuscles(exercise);
      const keywordScore = profile.keywords.reduce(
        (acc, keyword) => (normalizedName.includes(keyword) ? acc + 1 : acc),
        0,
      );
      const muscleScore = primaryMuscles.reduce(
        (acc, muscle) => (profile.muscles.includes(muscle) ? acc + 2 : acc),
        0,
      );
      const purposeBoost = profile.keywords.some(keyword => normalizedPurpose.includes(keyword)) ? 1 : 0;
      const score = keywordScore + muscleScore + purposeBoost;
      return { exercise, score, primaryMuscles };
    })
    .filter(item => item.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 8);

  const suggestions =
    ranked.length > 0
      ? ranked.map(({ exercise, primaryMuscles }) => ({
          name: exercise.name,
          justification: `Encaja con tu objetivo porque prioriza ${profile.focus} y estimula ${primaryMuscles.join(', ') || 'musculos clave'}.`,
          primaryMuscles: primaryMuscles.length > 0 ? primaryMuscles : ['general'],
        }))
      : buildFallbackSuggestions(profile);

  return Promise.resolve({
    exercises: suggestions.slice(0, 5),
  });
}

// ─── Image / Vision Functions ───────────────────────────────────────────────
// These match the PWA aiService.ts API surface.
// Currently blocked: the backend proxy has no image endpoints.
// The PWA calls @google/generative-ai directly; RN routes through backend.
// To unblock: add /api/ai/image/generate and /api/ai/image/edit endpoints to backend.

const IMAGE_BLOCKED_MESSAGE =
  'Generación de imagen no disponible en móvil. El backend no expone endpoints de imagen. ' +
  'Ver docs/parity/pwa-rn-master-matrix.md bloque B3.';

export function generateImage(_prompt: string, _aspectRatio: string): Promise<string> {
  throw new Error(IMAGE_BLOCKED_MESSAGE);
}

export function generateImages(
  _prompt: string,
  _aspectRatio: string,
): Promise<{ imageUrls: string[] }> {
  throw new Error(IMAGE_BLOCKED_MESSAGE);
}

export function editImageWithText(_base64Image: string, _prompt: string): Promise<string> {
  throw new Error(IMAGE_BLOCKED_MESSAGE);
}

export function analyzePosturePhoto(_base64Image: string): Promise<string> {
  throw new Error(IMAGE_BLOCKED_MESSAGE);
}

export function analyzeMealPhoto(_base64Image: string): Promise<Record<string, unknown>> {
  throw new Error(IMAGE_BLOCKED_MESSAGE);
}
