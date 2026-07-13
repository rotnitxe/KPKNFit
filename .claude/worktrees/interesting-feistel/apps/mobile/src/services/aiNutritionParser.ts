import type { Settings } from '../types/settings';
import { parseMealDescription as deterministicParse } from '../utils/nutritionDescriptionParser';
import type { ParsedMealItem as DeterministicMealItem } from '../utils/nutritionDescriptionParser';
import {
  analyzeLocalNutritionDescription,
  getLocalAiStatus,
  type LocalAiNutritionResult,
} from './localAiService';

export interface ParsedMealItem extends DeterministicMealItem {
  analysisSource?: 'local-ai-estimate' | 'local-heuristic' | 'database' | 'user-memory' | 'rules';
  analysisConfidence?: number;
  reviewRequired?: boolean;
}

export interface ParsedMealDescription {
  items: ParsedMealItem[];
  rawDescription: string;
  overallConfidence?: number;
  containsEstimatedItems?: boolean;
  requiresReview?: boolean;
  analysisEngine?: 'local-ai' | 'local-heuristic' | 'rules' | 'deterministic';
  modelVersion?: string | null;
}

export interface FreeFormNutritionOptions {
  mode?: 'auto' | 'rules' | 'local-ai' | 'deterministic' | 'assisted';
  provider?: 'android-native';
  onStageChange?: (stage: 'interpreting' | 'estimating') => void;
}

const DEFAULT_SCHEMA_VERSION = 'kpkn-food-analysis-v1';
const FIRST_CALL_TIMEOUT_MS = 8000;
const WARM_CALL_TIMEOUT_MS = 4500;
let hasRuntimeCall = false;

function withTimeout<T>(promise: Promise<T>, timeoutMs: number) {
  return Promise.race<T>([
    promise,
    new Promise<T>((_, reject) => setTimeout(() => reject(new Error('timeout')), timeoutMs)),
  ]);
}

function clampConfidence(value: unknown): number | undefined {
  if (typeof value !== 'number' || !Number.isFinite(value)) return undefined;
  return Math.max(0, Math.min(1, value));
}

function mapLocalResult(description: string, result: LocalAiNutritionResult): ParsedMealDescription {
  const items: ParsedMealItem[] = (result.items ?? [])
    .map(item => {
      const analysisSource: ParsedMealItem['analysisSource'] =
        item.source === 'database' ||
        item.source === 'user-memory' ||
        item.source === 'local-heuristic'
          ? item.source
          : 'local-ai-estimate';

      return {
        tag: item.canonicalName || item.rawText || 'Desconocido',
        quantity: typeof item.quantity === 'number' && item.quantity > 0 ? item.quantity : 1,
        amountGrams: item.grams,
        cookingMethod: item.preparation,
        portion: 'medium' as const,
        macroOverrides: {
          calories: item.calories,
          protein: item.protein,
          carbs: item.carbs,
          fats: item.fats,
        },
        analysisSource,
        analysisConfidence: clampConfidence(item.confidence),
        reviewRequired: item.reviewRequired,
      };
    })
    .filter(item => item.tag.trim().length > 0);

  if (items.length === 0) {
    return {
      ...deterministicParse(description),
      analysisEngine: 'deterministic',
      modelVersion: null,
    };
  }

  return {
    items,
    rawDescription: description,
    overallConfidence: clampConfidence(result.overallConfidence),
    containsEstimatedItems: result.containsEstimatedItems,
    requiresReview: result.requiresReview,
    analysisEngine: result.engine === 'heuristics' ? 'local-heuristic' : 'local-ai',
    modelVersion: result.modelVersion,
  };
}

function resolveMode(
  settings?: Settings | null,
  options?: FreeFormNutritionOptions,
): 'rules' | 'deterministic' | 'assisted' {
  const explicitMode = options?.mode;
  if (explicitMode === 'rules') return 'rules';
  if (explicitMode === 'deterministic') return 'deterministic';
  if (explicitMode === 'assisted' || explicitMode === 'local-ai') return 'assisted';

  if (settings?.nutritionResolutionMode === 'assisted') return 'assisted';
  if (settings?.nutritionDescriptionMode === 'rules') return 'rules';
  if (settings?.nutritionDescriptionMode === 'local-ai' || settings?.nutritionDescriptionMode === 'assisted') {
    return 'assisted';
  }

  return 'deterministic';
}

function deterministicWithMeta(description: string, engine: 'rules' | 'deterministic'): ParsedMealDescription {
  return {
    ...deterministicParse(description),
    analysisEngine: engine,
    modelVersion: null,
  };
}

export async function parseFreeFormNutrition(
  description: string,
  settings?: Settings | null,
  options: FreeFormNutritionOptions = {},
): Promise<ParsedMealDescription> {
  const trimmed = description.trim();
  if (!trimmed) {
    return {
      items: [],
      rawDescription: '',
      analysisEngine: 'deterministic',
      modelVersion: null,
    };
  }

  options.onStageChange?.('interpreting');
  const mode = resolveMode(settings, options);

  if (mode === 'rules') {
    return deterministicWithMeta(trimmed, 'rules');
  }
  if (mode !== 'assisted') {
    return deterministicWithMeta(trimmed, 'deterministic');
  }

  if (!(settings?.nutritionUseLocalAI ?? true)) {
    return deterministicWithMeta(trimmed, 'deterministic');
  }

  const status = await getLocalAiStatus();
  if (!status.available && !status.heuristicsAvailable) {
    return deterministicWithMeta(trimmed, 'deterministic');
  }

  try {
    options.onStageChange?.('estimating');
    const timeoutMs = hasRuntimeCall ? WARM_CALL_TIMEOUT_MS : FIRST_CALL_TIMEOUT_MS;
    const analysis = await withTimeout(
      analyzeLocalNutritionDescription({
        description: trimmed,
        locale: 'es-CL',
        schemaVersion: DEFAULT_SCHEMA_VERSION,
      }),
      timeoutMs,
    );
    hasRuntimeCall = true;
    return mapLocalResult(trimmed, analysis);
  } catch (error) {
    console.warn('[aiNutritionParser] fallback deterministic parser', error);
    return deterministicWithMeta(trimmed, 'deterministic');
  }
}
