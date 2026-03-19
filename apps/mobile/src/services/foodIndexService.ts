// apps/mobile/src/services/foodIndexService.ts
// Motor de Búsqueda y Resolución de Alimentos — RN parity layer
import { FOOD_DATABASE } from '../data/foodDatabase';
import { LOCAL_CHILEAN_FOOD_CATALOG } from '../data/localChileanFoods';
import { enrichFoodItem, buildFoodSearchText, normalizeFoodText } from '../data/foodTaxonomy';
import type { FoodItem } from '../types/food';

const HOT_FOODS = [...FOOD_DATABASE, ...LOCAL_CHILEAN_FOOD_CATALOG].map(enrichFoodItem);
const STOPWORDS = new Set(['de', 'del', 'la', 'el', 'los', 'las', 'y', 'en', 'con', 'sin', 'a', 'al', 'por', 'para']);
const HOT_LIMIT = 12;
const EXTENDED_LIMIT = 20;
const MIN_SCORE = 0.18;
const HIGH_CONFIDENCE_SCORE = 0.86;
const MEDIUM_CONFIDENCE_SCORE = 0.6;
const AUTO_SELECT_GAP = 0.08;

let favoriteFoodIds = new Set<string>();
let recentFoodIds: string[] = [];

export type SearchMatchType = 'exact' | 'partial' | 'fuzzy';
export type SearchConfidence = 'high' | 'medium' | 'low';

export interface SearchFoodCandidate {
    food: FoodItem;
    score: number;
    reason: string;
    matchedAlias?: string;
}

export interface SearchFoodsResult {
    results: FoodItem[];
    matchType: SearchMatchType;
    candidates: SearchFoodCandidate[];
    bestConfidence: SearchConfidence;
    canAutoSelect: boolean;
    bestScore?: number;
    decisionReason?: string;
}

function normalizeQuery(query: string): string {
    return normalizeFoodText(query)
        .replace(/\s+con\s+/gi, ' c/ ')
        .replace(/\s+/g, ' ')
        .trim();
}

function getTokens(value: string): string[] {
    return [...new Set(
        normalizeQuery(value)
            .split(/\s+/)
            .map(token => token.trim())
            .filter(token => token.length > 1 && !STOPWORDS.has(token) && !/^\d+$/.test(token)),
    )];
}

function getSearchSurface(food: FoodItem): string {
    return normalizeQuery([
        buildFoodSearchText(food),
        food.brand || '',
        food.category || '',
        ...(food.aliases || []),
        ...(food.searchAliases || []),
    ].join(' '));
}

function getMatchedAlias(food: FoodItem, query: string): string | undefined {
    const aliases = [...(food.aliases || []), ...(food.searchAliases || [])];
    const normalizedQuery = normalizeQuery(query);
    return aliases.find(alias => normalizeQuery(alias) === normalizedQuery)
        || aliases.find(alias => normalizeQuery(alias).includes(normalizedQuery))
        || undefined;
}

function classifyConfidence(score: number): SearchConfidence {
    if (score >= HIGH_CONFIDENCE_SCORE) return 'high';
    if (score >= MEDIUM_CONFIDENCE_SCORE) return 'medium';
    return 'low';
}

function scoreFood(query: string, food: FoodItem, brandHint?: string): SearchFoodCandidate | null {
    const normalizedQuery = normalizeQuery(query);
    if (!normalizedQuery) return null;

    const normalizedName = normalizeQuery(food.name);
    const surface = getSearchSurface(food);
    const queryTokens = getTokens(normalizedQuery);
    const surfaceTokens = new Set(getTokens(surface));
    const matchedAlias = getMatchedAlias(food, normalizedQuery);

    let score = 0;
    const reason: string[] = [];

    if (normalizedName === normalizedQuery) {
        score = 1;
        reason.push('exact_name');
    } else if (surface === normalizedQuery) {
        score = 1;
        reason.push('exact_surface');
    } else if (matchedAlias && normalizeQuery(matchedAlias) === normalizedQuery) {
        score = 0.98;
        reason.push('exact_alias');
    } else if (surface.includes(normalizedQuery) || normalizedQuery.includes(surface)) {
        score = 0.88;
        reason.push('substring');
    } else if (queryTokens.length > 0) {
        const overlap = queryTokens.filter(token => surfaceTokens.has(token)).length;
        const coverage = overlap / queryTokens.length;
        const union = new Set([...queryTokens, ...surfaceTokens]).size;
        const jaccard = union > 0 ? overlap / union : 0;
        score = (coverage * 0.7) + (jaccard * 0.3);
        reason.push(`token_${Math.round(coverage * 100)}pct`);
    }

    if (brandHint) {
        const normalizedBrand = normalizeQuery(brandHint);
        const foodBrand = normalizeQuery(food.brand || '');
        if (normalizedBrand && (foodBrand.includes(normalizedBrand) || surface.includes(normalizedBrand))) {
            score += 0.1;
            reason.push('brand_match');
        }
    }

    if (food.category && normalizedQuery.includes(normalizeQuery(food.category))) {
        score += 0.04;
        reason.push('category_match');
    }

    if (!score || score <= 0) return null;

    return {
        food,
        score: Math.min(1, Math.round(score * 1000) / 1000),
        reason: reason.join('|') || 'match',
        matchedAlias,
    };
}

function rankFoods(query: string, options: { brandHint?: string; limit?: number; category?: string } = {}) {
    const normalizedQuery = normalizeQuery(query);
    const pool = options.category
        ? HOT_FOODS.filter(food => food.category === options.category)
        : HOT_FOODS;

    const candidates = pool
        .map(food => scoreFood(normalizedQuery, food, options.brandHint))
        .filter((value): value is SearchFoodCandidate => value !== null && value.score >= MIN_SCORE)
        .sort((left, right) => {
            if (right.score !== left.score) return right.score - left.score;
            return left.food.name.localeCompare(right.food.name, 'es');
        });

    return candidates.slice(0, options.limit ?? HOT_LIMIT);
}

export async function searchFoodIndex(
    query: string,
    options: { brandHint?: string; limit?: number; scope?: 'hot' | 'extended'; category?: string } = {},
): Promise<SearchFoodsResult> {
    const normalizedQuery = normalizeQuery(query);
    if (!normalizedQuery) {
        return {
            results: [],
            matchType: 'fuzzy',
            candidates: [],
            bestConfidence: 'low',
            canAutoSelect: false,
            bestScore: 0,
            decisionReason: 'empty_query',
        };
    }

    const candidates = rankFoods(normalizedQuery, {
        brandHint: options.brandHint,
        limit: options.limit ?? (options.scope === 'extended' ? EXTENDED_LIMIT : HOT_LIMIT),
        category: options.category,
    });

    const best = candidates[0];
    const second = candidates[1];
    const bestScore = best?.score ?? 0;
    const gap = best && second ? best.score - second.score : 1;

    return {
        results: candidates.map(candidate => candidate.food),
        matchType: bestScore >= 0.98 ? 'exact' : bestScore >= 0.72 ? 'partial' : 'fuzzy',
        candidates,
        bestConfidence: classifyConfidence(bestScore),
        canAutoSelect: bestScore >= 0.9 && gap >= AUTO_SELECT_GAP,
        bestScore,
        decisionReason: best?.reason,
    };
}

export async function warmFoodIndex(): Promise<void> {
    // En RN sólo mantenemos el catálogo ya cargado en memoria.
    void HOT_FOODS.length;
}

export function searchFoods(query: string, filters?: { category?: string }): FoodItem[] {
    return rankFoods(query, { category: filters?.category, limit: EXTENDED_LIMIT }).map(candidate => candidate.food);
}

export function getFoodById(foodId: string): FoodItem | null {
    return HOT_FOODS.find(food => food.id === foodId) ?? null;
}

export function getFavorites(): FoodItem[] {
    return HOT_FOODS.filter(food => favoriteFoodIds.has(food.id));
}

export function getRecent(): FoodItem[] {
    return recentFoodIds
        .map(id => HOT_FOODS.find(food => food.id === id))
        .filter((value): value is FoodItem => Boolean(value));
}

export function addToFavorites(foodId: string): void {
    favoriteFoodIds.add(foodId);
}

export function removeFromFavorites(foodId: string): void {
    favoriteFoodIds.delete(foodId);
}

export function getFoodsByCategory(category: string): FoodItem[] {
    return HOT_FOODS.filter(food => food.category === category);
}

export function addToRecent(foodId: string): void {
    const index = recentFoodIds.indexOf(foodId);
    if (index !== -1) {
        recentFoodIds.splice(index, 1);
    }
    recentFoodIds.unshift(foodId);
    if (recentFoodIds.length > 20) {
        recentFoodIds.pop();
    }
}
