import type { ParsedMealDescription, ParsedMealItem, Settings } from '../types';
import { FOOD_DATABASE } from '../data/foodDatabase';
import { LOCAL_CHILEAN_FOOD_CATALOG } from '../data/localChileanFoods';
import { parseMealDescription as deterministicParse } from '../utils/nutritionDescriptionParser';
import {
    analyzeLocalNutritionDescription,
    getLocalAiStatus,
    type LocalAiNutritionItem,
    type LocalAiNutritionResult,
} from './localAiService';
import { getFoodResolutionMemoryHints } from './foodSearchService';

const DEFAULT_LOCAL_MODEL = 'kpkn-food-fg270m-v1';
const AI_TIMEOUT_FIRST_MS = 8_000;
const AI_TIMEOUT_WARM_MS = 4_500;
let hasCalledRuntimeOnce = false;
const KNOWN_FOOD_HINTS = Array.from(
    new Set(
        FOOD_DATABASE
            .concat(LOCAL_CHILEAN_FOOD_CATALOG)
            .filter(food => food.brand === 'Comida' || food.tags?.includes('preparacion') || food.tags?.includes('chileno'))
            .flatMap(food => [food.name, ...(food.searchAliases || [])]),
    ),
).slice(0, 96);

export interface FreeFormNutritionOptions {
    mode?: 'auto' | 'rules' | 'local-ai' | 'deterministic' | 'assisted';
    provider?: 'android-native';
    onStageChange?: (stage: 'interpreting' | 'estimating') => void;
}

function withTimeout<T>(promise: Promise<T>, timeoutMs = AI_TIMEOUT_WARM_MS): Promise<T> {
    return Promise.race([
        promise,
        new Promise<T>((_, reject) => setTimeout(() => reject(new Error('timeout')), timeoutMs)),
    ]);
}

function clampConfidence(value: unknown): number | undefined {
    return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.min(1, value)) : undefined;
}

function buildMacroOverrides(item: Record<string, any>, amountGrams?: number) {
    const directTotals = item?.nutritionTotals;
    if (directTotals && typeof directTotals === 'object') {
        const calories = typeof directTotals.calories === 'number' ? directTotals.calories : undefined;
        const protein = typeof directTotals.protein === 'number' ? directTotals.protein : undefined;
        const carbs = typeof directTotals.carbs === 'number' ? directTotals.carbs : undefined;
        const fats = typeof directTotals.fats === 'number' ? directTotals.fats : undefined;
        if ([calories, protein, carbs, fats].some(value => typeof value === 'number')) {
            return { calories, protein, carbs, fats };
        }
    }

    const per100g = item?.nutritionPer100g;
    if (!per100g || typeof per100g !== 'object' || typeof amountGrams !== 'number' || amountGrams <= 0) {
        return undefined;
    }

    const ratio = amountGrams / 100;
    return {
        calories: typeof per100g.calories === 'number' ? Math.round(per100g.calories * ratio) : undefined,
        protein: typeof per100g.protein === 'number' ? Math.round(per100g.protein * ratio * 10) / 10 : undefined,
        carbs: typeof per100g.carbs === 'number' ? Math.round(per100g.carbs * ratio * 10) / 10 : undefined,
        fats: typeof per100g.fats === 'number' ? Math.round(per100g.fats * ratio * 10) / 10 : undefined,
    };
}

function sanitizeItem(item: Record<string, any>): ParsedMealItem {
    const amountGrams = typeof item?.grams === 'number'
        ? item.grams
        : (typeof item?.amountGrams === 'number' ? item.amountGrams : undefined);

    return {
        tag: item?.canonicalName || item?.tag || item?.rawText || 'Desconocido',
        quantity: typeof item?.quantity === 'number' && item.quantity > 0 ? item.quantity : 1,
        amountGrams: amountGrams && amountGrams > 0 ? amountGrams : undefined,
        portion: (typeof item?.portion === 'string' ? item.portion : 'medium') as ParsedMealItem['portion'],
        cookingMethod: (item?.preparation || item?.cookingMethod || undefined) as ParsedMealItem['cookingMethod'],
        brandHint: item?.brandHint || undefined,
        macroOverrides: buildMacroOverrides(item, amountGrams),
        anatomicalModifiers: Array.isArray(item?.anatomicalModifiers) && item.anatomicalModifiers.length ? item.anatomicalModifiers : undefined,
        heuristicModifiers: Array.isArray(item?.heuristicModifiers) && item.heuristicModifiers.length ? item.heuristicModifiers : undefined,
        preparationModifiers: Array.isArray(item?.preparationModifiers) && item.preparationModifiers.length ? item.preparationModifiers : undefined,
        stateModifiers: Array.isArray(item?.stateModifiers) && item.stateModifiers.length ? item.stateModifiers : undefined,
        compositionModifiers: Array.isArray(item?.compositionModifiers) && item.compositionModifiers.length ? item.compositionModifiers : undefined,
        dimensionalMultiplier: typeof item?.dimensionalMultiplier === 'number' ? item.dimensionalMultiplier : undefined,
        isGroup: Boolean(item?.isGroup),
        subItems: Array.isArray(item?.subItems) ? item.subItems.map((subItem: Record<string, any>) => sanitizeItem(subItem)) : undefined,
        analysisSource: (['local-ai-estimate', 'local-heuristic', 'database', 'user-memory', 'rules'].includes(item?.source) ? item.source : 'local-ai-estimate') as ParsedMealItem['analysisSource'],
        analysisConfidence: clampConfidence(item?.confidence),
        reviewRequired: Boolean(item?.reviewRequired),
    };
}

function sanitizeParsedMeal(result: LocalAiNutritionResult | Record<string, any>, description: string): ParsedMealDescription | null {
    const engine = result?.engine === 'runtime' ? 'local-ai' : result?.engine === 'heuristics' ? 'local-heuristic' : 'local-ai';
    const items = Array.isArray(result?.items)
        ? result.items
            .map((item: LocalAiNutritionItem | Record<string, any>) => sanitizeItem(item as Record<string, any>))
            .filter(item => item.tag && item.tag.trim().length > 0)
        : [];

    if (!items.length) {
        return null;
    }

    return {
        items,
        rawDescription: description,
        overallConfidence: clampConfidence(result?.overallConfidence),
        containsEstimatedItems: Boolean(result?.containsEstimatedItems),
        requiresReview: Boolean(result?.requiresReview),
        analysisEngine: engine,
        modelVersion: engine === 'local-ai' ? (result?.modelVersion ?? DEFAULT_LOCAL_MODEL) : null,
    };
}

function fallbackWithMeta(description: string): ParsedMealDescription {
    return {
        ...deterministicParse(description),
        analysisEngine: 'deterministic',
        modelVersion: null,
    };
}

function rulesWithMeta(description: string): ParsedMealDescription {
    return {
        ...deterministicParse(description),
        analysisEngine: 'rules',
        modelVersion: null,
    };
}

function getResolutionMode(
    settings?: Settings | null,
    options?: FreeFormNutritionOptions,
): 'rules' | 'deterministic' | 'assisted' {
    const explicit = options?.mode;
    if (explicit === 'rules') return 'rules';
    if (explicit === 'local-ai' || explicit === 'assisted') return 'assisted';
    if (explicit === 'deterministic') return 'deterministic';
    if (settings?.nutritionResolutionMode === 'assisted') return 'assisted';
    if (settings?.nutritionDescriptionMode === 'local-ai' || settings?.nutritionDescriptionMode === 'assisted') return 'assisted';
    if (settings?.nutritionDescriptionMode === 'rules') return 'rules';
    return 'deterministic';
}

export async function parseFreeFormNutrition(
    description: string,
    settings?: Settings | null,
    options: FreeFormNutritionOptions = {},
): Promise<ParsedMealDescription> {
    const trimmed = description.trim();
    if (!trimmed) {
        return { items: [], rawDescription: '', analysisEngine: 'deterministic', modelVersion: null };
    }

    options.onStageChange?.('interpreting');
    const mode = getResolutionMode(settings, options);
    if (mode === 'rules') {
        return rulesWithMeta(trimmed);
    }
    if (mode !== 'assisted') {
        return fallbackWithMeta(trimmed);
    }

    const shouldUseLocalAi = settings?.nutritionUseLocalAI ?? false;
    if (!shouldUseLocalAi) {
        return fallbackWithMeta(trimmed);
    }

    const localAiStatus = await getLocalAiStatus();
    if (!localAiStatus.available && !localAiStatus.heuristicsAvailable) {
        return fallbackWithMeta(trimmed);
    }

    try {
        options.onStageChange?.('estimating');

        const timeoutMs = hasCalledRuntimeOnce ? AI_TIMEOUT_WARM_MS : AI_TIMEOUT_FIRST_MS;
        const result = await withTimeout(analyzeLocalNutritionDescription({
            description: trimmed,
            locale: 'es-CL',
            schemaVersion: 'kpkn-food-analysis-v1',
            knownFoods: KNOWN_FOOD_HINTS,
            userMemory: getFoodResolutionMemoryHints(24),
        }), timeoutMs);
        hasCalledRuntimeOnce = true;

        const parsed = sanitizeParsedMeal(result, trimmed);
        return parsed ?? fallbackWithMeta(trimmed);
    } catch (error) {
        console.warn('[aiNutritionParser] falling back to deterministic parser:', error);
        return fallbackWithMeta(trimmed);
    }
}
