import type { LocalAiNutritionAnalysisRequest } from '@kpkn/shared-types';
import type { CoachChatMessage, CoachContextSnapshot } from '../types/coach';
import type { WorkoutOverview, WellbeingOverview } from '@kpkn/shared-types';
import type { BodyProgressEntry } from '../types/workout';
import type { SavedNutritionEntry } from '../types/nutrition';
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

function isValidWeightHistoryEntry(entry: WeightHistoryEntry): entry is Required<Pick<WeightHistoryEntry, 'date'>> & { weight: number } {
  return typeof entry.date === 'string' && Number.isFinite(entry.weight);
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
