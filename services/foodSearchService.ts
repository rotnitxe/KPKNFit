// services/foodSearchService.ts
// Busqueda unificada: foodDatabase local + Open Food Facts + USDA FoodData Central.
// Incluye ranking, confianza, trazabilidad y memoria de resolucion aprendida.

import type { FoodItem, Settings } from '../types';
import { FOOD_DATABASE } from '../data/foodDatabase';

const STOPWORDS = new Set(['de', 'y', 'la', 'el', 'en', 'a', 'al', 'del', 'los', 'las', 'un', 'una', 'por', 'para']);
const FUZZY_THRESHOLD = 0.55;
const MIN_CANDIDATE_SCORE = 0.2;
const HIGH_CONFIDENCE_SCORE = 0.82;
const MEDIUM_CONFIDENCE_SCORE = 0.62;
const SAFE_AUTOSELECT_GAP = 0.14;
const API_TIMEOUT_MS = 4000;
const ONLINE_QUERY_MIN_CHARS = 4;

const CACHE_KEY = 'kpkn-food-search-cache';
const CACHE_MAX_ENTRIES = 150;
const CACHE_TTL_MS = 24 * 60 * 60 * 1000;

const LEARNED_RESOLUTIONS_KEY = 'kpkn-food-resolution-memory';
const LEARNED_MAX_ENTRIES = 250;

export type SearchMatchType = 'exact' | 'partial' | 'fuzzy';
export type SearchConfidence = 'high' | 'medium' | 'low';
export type SearchSource = 'local' | 'off' | 'usda';

export interface SearchFoodCandidate {
    food: FoodItem;
    score: number;
    confidence: SearchConfidence;
    canonicalId: string;
    source: SearchSource;
    trace: string[];
    queryCoverage: number;
    tokenPrecision: number;
    brandMatched: boolean;
    learned: boolean;
}

export interface SearchFoodsResult {
    results: FoodItem[];
    matchType: SearchMatchType;
    candidates: SearchFoodCandidate[];
    bestScore?: number;
    bestConfidence: SearchConfidence;
    canAutoSelect: boolean;
    decisionReason?: string;
}

interface CacheEntry {
    query: string;
    results: FoodItem[];
    candidates: SearchFoodCandidate[];
    matchType: SearchMatchType;
    bestScore?: number;
    bestConfidence: SearchConfidence;
    canAutoSelect: boolean;
    decisionReason?: string;
    ts: number;
}

interface LearnedResolution {
    foodId: string;
    canonicalId: string;
    ts: number;
    count: number;
}

const OFF_MICRO_MAP: Record<string, { name: string; unit: string }> = {
    'iron_100g': { name: 'Hierro', unit: 'mg' },
    'calcium_100g': { name: 'Calcio', unit: 'mg' },
    'sodium_100g': { name: 'Sodio', unit: 'mg' },
    'vitamin-a_100g': { name: 'Vitamina A', unit: 'ug' },
    'vitamin-c_100g': { name: 'Vitamina C', unit: 'mg' },
    'vitamin-d_100g': { name: 'Vitamina D', unit: 'ug' },
    'vitamin-b12_100g': { name: 'Vitamina B12', unit: 'ug' },
    'magnesium_100g': { name: 'Magnesio', unit: 'mg' },
    'zinc_100g': { name: 'Zinc', unit: 'mg' },
    'potassium_100g': { name: 'Potasio', unit: 'mg' },
};

const USDA_MICRO_MAP: { pattern: RegExp; name: string; unit: string }[] = [
    { pattern: /\biron\b/i, name: 'Hierro', unit: 'mg' },
    { pattern: /\bcalcium\b/i, name: 'Calcio', unit: 'mg' },
    { pattern: /\bsodium\b/i, name: 'Sodio', unit: 'mg' },
    { pattern: /\bvitamin\s+a\b|vitamin a,/i, name: 'Vitamina A', unit: 'ug' },
    { pattern: /\bvitamin\s+c\b|ascorbic acid/i, name: 'Vitamina C', unit: 'mg' },
    { pattern: /\bvitamin\s+d\b/i, name: 'Vitamina D', unit: 'ug' },
    { pattern: /\bvitamin\s+b-?12\b|b12\b/i, name: 'Vitamina B12', unit: 'ug' },
    { pattern: /\bmagnesium\b/i, name: 'Magnesio', unit: 'mg' },
    { pattern: /\bzinc\b/i, name: 'Zinc', unit: 'mg' },
    { pattern: /\bpotassium\b/i, name: 'Potasio', unit: 'mg' },
];

function hasLocalStorage(): boolean {
    return typeof localStorage !== 'undefined';
}

function readLocalStorage(key: string): string | null {
    if (!hasLocalStorage()) return null;
    try {
        return localStorage.getItem(key);
    } catch (_) {
        return null;
    }
}

function writeLocalStorage(key: string, value: string): void {
    if (!hasLocalStorage()) return;
    try {
        localStorage.setItem(key, value);
    } catch (_) {
        /* ignore */
    }
}

function removeLocalStorage(key: string): void {
    if (!hasLocalStorage()) return;
    try {
        localStorage.removeItem(key);
    } catch (_) {
        /* ignore */
    }
}

async function fetchWithTimeout(url: string, timeoutMs = API_TIMEOUT_MS): Promise<Response> {
    const ctrl = new AbortController();
    const id = setTimeout(() => ctrl.abort(), timeoutMs);
    try {
        const res = await fetch(url, { signal: ctrl.signal });
        clearTimeout(id);
        return res;
    } catch (error) {
        clearTimeout(id);
        throw error;
    }
}

function normalizeQueryForSearch(query: string): string {
    return query
        .trim()
        .replace(/\s+con\s+/gi, ' c/ ')
        .replace(/\s+/g, ' ')
        .trim()
        .toLowerCase();
}

function normalizeFoodNameForMatch(name: string): string {
    return (name || '')
        .replace(/\s+con\s+/gi, ' c/ ')
        .toLowerCase();
}

function normalizeFoodName(name: string): string {
    return name
        .normalize('NFD')
        .replace(/\p{Diacritic}/gu, '')
        .toLowerCase()
        .replace(/\s*\([^)]*\)\s*/g, ' ')
        .replace(/\b(generico|cocido|crudo|raw|cooked)\b/gi, ' ')
        .replace(/[^\p{Letter}\p{Number}\s/+-]+/gu, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

function getTokens(normalized: string): Set<string> {
    return new Set(
        normalized
            .split(/\s+/)
            .map(token => token.trim())
            .filter(token => token.length > 2 && !/^\d+$/.test(token) && !STOPWORDS.has(token))
    );
}

function getSignificantQueryTokens(query: string): string[] {
    return [...getTokens(normalizeFoodName(normalizeQueryForSearch(query)))];
}

function hasSignificantQuery(query: string): boolean {
    const tokens = getSignificantQueryTokens(query);
    const trimmed = query.trim();
    return tokens.length > 0 || (trimmed.length >= 3 && !STOPWORDS.has(trimmed.toLowerCase()));
}

export function getCanonicalFoodId(food: FoodItem): string {
    return normalizeFoodName(normalizeFoodNameForMatch(food.name));
}

function getSourceKind(id: string): SearchSource {
    if (id.startsWith('usda-')) return 'usda';
    if (id.startsWith('off-')) return 'off';
    return 'local';
}

function getSourcePriority(id: string): number {
    if (id.startsWith('usda-')) return 3;
    if (id.startsWith('gen') || id.startsWith('cl') || id.startsWith('int') || id.startsWith('meal')) return 2;
    if (id.startsWith('off-')) return 1;
    return 2;
}

let FOOD_TOKEN_INDEX: Map<string, FoodItem[]> | null = null;

function buildFoodTokenIndex(): Map<string, FoodItem[]> {
    if (FOOD_TOKEN_INDEX) return FOOD_TOKEN_INDEX;

    const index = new Map<string, FoodItem[]>();
    for (const food of FOOD_DATABASE) {
        const tokens = getTokens(getCanonicalFoodId(food));
        for (const token of tokens) {
            const existing = index.get(token) ?? [];
            if (!existing.includes(food)) existing.push(food);
            index.set(token, existing);
        }
    }

    FOOD_TOKEN_INDEX = index;
    return index;
}

function foodItemFromOFF(product: any): FoodItem {
    const nutriments = product.nutriments || {};
    const kcal100 = nutriments['energy-kcal_100g']
        ?? nutriments['energy-kcal']
        ?? (nutriments['energy_100g'] ? nutriments['energy_100g'] / 4.184 : 0);
    const micronutrients: { name: string; amount: number; unit: string }[] = [];

    for (const [key, meta] of Object.entries(OFF_MICRO_MAP)) {
        const value = nutriments[key];
        if (typeof value === 'number' && value > 0) {
            micronutrients.push({
                name: meta.name,
                amount: Math.round(value * 10) / 10,
                unit: meta.unit,
            });
        }
    }

    return {
        id: `off-${product.code || product.id || crypto.randomUUID()}`,
        name: product.product_name || product.product_name_es || 'Sin nombre',
        brand: product.brands,
        servingSize: 100,
        servingUnit: 'g',
        unit: 'g',
        calories: Math.round(kcal100) || 0,
        protein: Math.round((nutriments['proteins_100g'] ?? nutriments.proteins ?? 0) * 10) / 10,
        carbs: Math.round((nutriments['carbohydrates_100g'] ?? nutriments.carbohydrates ?? 0) * 10) / 10,
        fats: Math.round((nutriments['fat_100g'] ?? nutriments.fat ?? 0) * 10) / 10,
        isCustom: false,
        image: product.image_small_url || product.image_front_small_url,
        fatBreakdown: nutriments['saturated-fat_100g'] != null ? {
            saturated: nutriments['saturated-fat_100g'],
            monounsaturated: nutriments['monounsaturated-fat_100g'] ?? 0,
            polyunsaturated: nutriments['polyunsaturated-fat_100g'] ?? 0,
            trans: nutriments['trans-fat_100g'] ?? 0,
        } : undefined,
        micronutrients: micronutrients.length > 0 ? micronutrients : undefined,
    };
}

function foodItemFromUSDA(food: any): FoodItem {
    const nutrients = (food.foodNutrients || []).reduce((acc: Record<string, number>, nutrient: any) => {
        const name = ((nutrient.nutrientName || nutrient.nutrient?.name) || '').toLowerCase();
        const unit = (nutrient.nutrient?.unitName || '').toLowerCase();
        const value = nutrient.value ?? nutrient.amount ?? nutrient.median ?? 0;

        if (name.includes('energy') && (unit.includes('kcal') || !unit.includes('kj'))) acc.calories = value;
        else if (name.includes('energy') && !acc.calories) acc.calories = value;
        if (name.includes('protein')) acc.protein = value;
        if (name.includes('carbohydrate') && !name.includes('fiber')) acc.carbs = value;
        if (name.includes('total lipid') || name === 'fat') acc.fats = value;
        if (name.includes('fatty acids, total saturated')) acc.saturated = value;
        if (name.includes('fatty acids, total trans')) acc.trans = value;

        return acc;
    }, { calories: 0, protein: 0, carbs: 0, fats: 0, saturated: 0, trans: 0 } as Record<string, number>);

    const micronutrients: { name: string; amount: number; unit: string }[] = [];
    for (const nutrient of food.foodNutrients || []) {
        const rawName = (nutrient.nutrientName || nutrient.nutrient?.name) || '';
        const value = nutrient.value ?? nutrient.amount ?? nutrient.median ?? 0;
        if (typeof value !== 'number' || value <= 0) continue;

        for (const entry of USDA_MICRO_MAP) {
            if (entry.pattern.test(rawName)) {
                micronutrients.push({
                    name: entry.name,
                    amount: Math.round(value * 10) / 10,
                    unit: entry.unit,
                });
                break;
            }
        }
    }

    const item: FoodItem = {
        id: `usda-${food.fdcId || food.id || crypto.randomUUID()}`,
        name: food.description || food.foodDescription || 'Sin nombre',
        brand: food.brandOwner,
        servingSize: 100,
        servingUnit: 'g',
        unit: 'g',
        calories: Math.round(nutrients.calories) || 0,
        protein: Math.round(nutrients.protein * 10) / 10 || 0,
        carbs: Math.round(nutrients.carbs * 10) / 10 || 0,
        fats: Math.round(nutrients.fats * 10) / 10 || 0,
        isCustom: false,
    };

    if (nutrients.saturated != null && nutrients.saturated > 0) {
        item.fatBreakdown = {
            saturated: nutrients.saturated,
            monounsaturated: 0,
            polyunsaturated: 0,
            trans: nutrients.trans ?? 0,
        };
    }

    if (micronutrients.length > 0) item.micronutrients = micronutrients;
    return item;
}

function fuzzyScore(query: string, foodName: string): number {
    const queryNormalized = normalizeFoodName(normalizeQueryForSearch(query));
    const foodNormalized = normalizeFoodName(normalizeFoodNameForMatch(foodName));
    if (!queryNormalized || !foodNormalized) return 0;

    const queryTokens = getTokens(queryNormalized);
    const foodTokens = getTokens(foodNormalized);
    if (queryTokens.size === 0) return foodNormalized.includes(queryNormalized) ? 0.9 : 0;

    const overlap = [...queryTokens].filter(token => foodTokens.has(token)).length;
    const union = new Set([...queryTokens, ...foodTokens]).size;
    const jaccard = union > 0 ? overlap / union : 0;
    const coverage = overlap / queryTokens.size;
    const score = jaccard * 0.5 + coverage * 0.5;

    if (foodNormalized.includes(queryNormalized) || queryNormalized.includes(foodNormalized)) {
        return Math.max(score, 0.85);
    }

    return score;
}

function searchLocalWithMatch(query: string): { results: FoodItem[]; matchType: SearchMatchType } {
    const normalizedQuery = normalizeQueryForSearch(query);
    if (!normalizedQuery || !hasSignificantQuery(normalizedQuery)) {
        return { results: [], matchType: 'exact' };
    }

    const searchTerms = getSignificantQueryTokens(normalizedQuery);
    const index = buildFoodTokenIndex();
    const candidateSet = new Set<FoodItem>();

    for (const term of searchTerms) {
        const matches = index.get(term);
        if (!matches) continue;
        for (const food of matches) candidateSet.add(food);
    }

    const pool = candidateSet.size > 0 ? [...candidateSet] : FOOD_DATABASE;
    const directMatches = pool.filter(food => {
        const name = normalizeFoodNameForMatch(food.name);
        const brand = (food.brand || '').toLowerCase();
        return searchTerms.some(term => name.includes(term) || brand.includes(term));
    }).slice(0, 30);

    if (directMatches.length > 0) {
        const queryName = normalizeFoodName(normalizedQuery);
        const hasExact = directMatches.some(food => {
            const foodName = getCanonicalFoodId(food);
            return foodName === queryName || foodName.includes(queryName) || queryName.includes(foodName);
        });

        return { results: directMatches, matchType: hasExact ? 'exact' : 'partial' };
    }

    const fuzzyResults = FOOD_DATABASE
        .map(food => ({ food, score: fuzzyScore(normalizedQuery, food.name) }))
        .filter(entry => entry.score >= FUZZY_THRESHOLD)
        .sort((a, b) => b.score - a.score)
        .slice(0, 20)
        .map(entry => entry.food);

    return { results: fuzzyResults, matchType: 'fuzzy' };
}

const OFF_OFFLINE_URL = '/data/openFoodFactsOffline.json';
let offOfflineCache: any[] | null = null;

async function loadOFFOffline(): Promise<any[]> {
    if (offOfflineCache) return offOfflineCache;

    const base = typeof window !== 'undefined' && window.location?.origin ? window.location.origin : '';
    try {
        const response = await fetch(base + OFF_OFFLINE_URL);
        if (!response.ok) return [];

        const data = await response.json();
        const products = Array.isArray(data) ? data : (data?.products || []);
        if (products.length > 0) offOfflineCache = products;
        return offOfflineCache ?? [];
    } catch (_) {
        return [];
    }
}

async function searchOFFOffline(query: string): Promise<FoodItem[]> {
    const products = await loadOFFOffline();
    const normalizedQuery = normalizeQueryForSearch(query);
    if (!normalizedQuery || products.length === 0 || !hasSignificantQuery(normalizedQuery)) return [];

    const searchTerms = getSignificantQueryTokens(normalizedQuery);
    const normalizedName = (product: any) => normalizeFoodNameForMatch(product.product_name || product.product_name_es || '');

    let matches = products.filter(product =>
        searchTerms.some(term => normalizedName(product).includes(term))
    );

    if (matches.length === 0) {
        matches = products
            .map(product => ({ product, score: fuzzyScore(normalizedQuery, normalizedName(product)) }))
            .filter(entry => entry.score >= FUZZY_THRESHOLD)
            .sort((a, b) => b.score - a.score)
            .slice(0, 15)
            .map(entry => entry.product);
    }

    return matches.slice(0, 15).map(product => foodItemFromOFF(product));
}

async function searchOpenFoodFacts(query: string): Promise<FoodItem[]> {
    if (!hasSignificantQuery(query) || query.trim().length < ONLINE_QUERY_MIN_CHARS) return [];

    const searchTerms = getSignificantQueryTokens(query).join(' ') || query.trim();
    if (!searchTerms) return [];

    try {
        const url = `https://world.openfoodfacts.org/cgi/search.pl?search_terms=${encodeURIComponent(searchTerms)}&search_simple=1&json=1&page_size=10`;
        const response = await fetchWithTimeout(url);
        const data = await response.json();
        const products = data.products || [];

        return products
            .filter((product: any) => product.product_name && (product.nutriments?.['energy-kcal_100g'] != null || product.nutriments?.proteins_100g != null))
            .map((product: any) => foodItemFromOFF(product))
            .filter((food: FoodItem) => {
                const tokens = getSignificantQueryTokens(query);
                return fuzzyScore(query, food.name) >= FUZZY_THRESHOLD
                    || tokens.some(token => normalizeFoodName(food.name).includes(token));
            })
            .slice(0, 10);
    } catch (_) {
        return searchOFFOffline(query);
    }
}

const USDA_OFFLINE_URL = '/data/usdaFoodsOffline.json';
const USDA_OFFLINE_FALLBACK_URL = '/data/usdaFoundationFoods.json';
let usdaOfflineCache: any[] | null = null;

function parseUSDAOfflineData(data: unknown): any[] {
    if (Array.isArray(data)) return data;
    if (data && typeof data === 'object' && 'FoundationFoods' in data) {
        return (data as { FoundationFoods?: any[] }).FoundationFoods || [];
    }
    if (data && typeof data === 'object' && 'foods' in data) {
        return (data as { foods?: any[] }).foods || [];
    }
    return [];
}

async function loadUSDAOffline(): Promise<any[]> {
    if (usdaOfflineCache) return usdaOfflineCache;

    const base = typeof window !== 'undefined' && window.location?.origin ? window.location.origin : '';
    for (const url of [USDA_OFFLINE_URL, USDA_OFFLINE_FALLBACK_URL]) {
        try {
            const response = await fetch(base + url);
            if (!response.ok) continue;

            const data = await response.json();
            const foods = parseUSDAOfflineData(data);
            if (foods.length > 0) {
                usdaOfflineCache = foods;
                return usdaOfflineCache;
            }
        } catch (_) {
            /* try next url */
        }
    }

    usdaOfflineCache = [];
    return [];
}

async function searchUSDAOffline(query: string): Promise<FoodItem[]> {
    const foods = await loadUSDAOffline();
    const normalizedQuery = normalizeQueryForSearch(query);
    if (!normalizedQuery || !hasSignificantQuery(normalizedQuery) || foods.length === 0) return [];

    const searchTerms = getSignificantQueryTokens(normalizedQuery);
    const normalizedDescription = (food: any) => normalizeFoodNameForMatch(food.description || '');

    let matches = foods.filter(food =>
        searchTerms.some(term => normalizedDescription(food).includes(term))
    );

    if (matches.length === 0) {
        matches = foods
            .map(food => ({ food, score: fuzzyScore(normalizedQuery, food.description || '') }))
            .filter(entry => entry.score >= FUZZY_THRESHOLD)
            .sort((a, b) => b.score - a.score)
            .slice(0, 15)
            .map(entry => entry.food);
    }

    return matches.slice(0, 15).map(food => foodItemFromUSDA(food));
}

async function searchUSDA(query: string, apiKey: string): Promise<FoodItem[]> {
    if (apiKey && query.trim().length >= ONLINE_QUERY_MIN_CHARS) {
        try {
            const url = `https://api.nal.usda.gov/fdc/v1/foods/search?api_key=${encodeURIComponent(apiKey)}&query=${encodeURIComponent(query)}&pageSize=10`;
            const response = await fetchWithTimeout(url);
            const data = await response.json();
            const foods = data.foods || [];
            if (foods.length > 0) return foods.slice(0, 10).map((food: any) => foodItemFromUSDA(food));
        } catch (_) {
            /* offline fallback */
        }
    }

    return searchUSDAOffline(query);
}

function mergeNutrients(base: FoodItem, fallback: FoodItem): FoodItem {
    const merged = { ...base };

    if ((!merged.calories || merged.calories === 0) && fallback.calories) merged.calories = fallback.calories;
    if ((!merged.protein || merged.protein === 0) && fallback.protein) merged.protein = fallback.protein;
    if ((!merged.carbs || merged.carbs === 0) && fallback.carbs) merged.carbs = fallback.carbs;
    if ((!merged.fats || merged.fats === 0) && fallback.fats) merged.fats = fallback.fats;
    if (!merged.fatBreakdown && fallback.fatBreakdown) merged.fatBreakdown = fallback.fatBreakdown;
    if (!merged.micronutrients?.length && fallback.micronutrients?.length) merged.micronutrients = fallback.micronutrients;
    if (!merged.carbBreakdown && fallback.carbBreakdown) merged.carbBreakdown = fallback.carbBreakdown;

    return merged;
}

function areSimilarFoods(a: FoodItem, b: FoodItem): boolean {
    const canonicalA = getCanonicalFoodId(a);
    const canonicalB = getCanonicalFoodId(b);
    if (canonicalA === canonicalB) return true;

    const tokensA = getTokens(canonicalA);
    const tokensB = getTokens(canonicalB);
    if (tokensA.size === 0 || tokensB.size === 0) return false;

    const overlap = [...tokensA].filter(token => tokensB.has(token)).length;
    const minSize = Math.min(tokensA.size, tokensB.size);
    return overlap >= Math.ceil(minSize * 0.7);
}

function mergeAndDeduplicate(local: FoodItem[], off: FoodItem[], usda: FoodItem[]): FoodItem[] {
    const all = [...usda, ...local, ...off];
    const result: FoodItem[] = [];
    const used = new Set<string>();

    for (const item of all) {
        if (used.has(item.id)) continue;

        const group = all.filter(candidate => !used.has(candidate.id) && areSimilarFoods(item, candidate));
        const best = [...group].sort((a, b) => getSourcePriority(b.id) - getSourcePriority(a.id))[0];
        let merged = { ...best };

        for (const candidate of group) {
            if (candidate.id !== best.id) merged = mergeNutrients(merged, candidate);
        }

        result.push(merged);
        for (const candidate of group) used.add(candidate.id);
    }

    return result.slice(0, 30);
}

function getCache(): Map<string, CacheEntry> {
    const raw = readLocalStorage(CACHE_KEY);
    if (!raw) return new Map();

    try {
        const entries = JSON.parse(raw) as [string, CacheEntry][];
        const map = new Map(entries);
        const now = Date.now();
        for (const [key, value] of map) {
            if (now - value.ts > CACHE_TTL_MS) map.delete(key);
        }
        return map;
    } catch (_) {
        return new Map();
    }
}

function setCache(map: Map<string, CacheEntry>): void {
    const entries = Array.from(map.entries()).slice(-CACHE_MAX_ENTRIES);
    writeLocalStorage(CACHE_KEY, JSON.stringify(entries));
}

function clearSearchCache(): void {
    removeLocalStorage(CACHE_KEY);
}

function getLearnedResolutions(): Map<string, LearnedResolution> {
    const raw = readLocalStorage(LEARNED_RESOLUTIONS_KEY);
    if (!raw) return new Map();

    try {
        const entries = JSON.parse(raw) as [string, LearnedResolution][];
        return new Map(entries);
    } catch (_) {
        return new Map();
    }
}

function setLearnedResolutions(map: Map<string, LearnedResolution>): void {
    const entries = Array.from(map.entries())
        .sort((a, b) => b[1].ts - a[1].ts)
        .slice(0, LEARNED_MAX_ENTRIES);
    writeLocalStorage(LEARNED_RESOLUTIONS_KEY, JSON.stringify(entries));
}

function buildMemoryKey(query: string, brandHint?: string): string {
    const normalizedQuery = normalizeFoodName(normalizeQueryForSearch(query));
    const normalizedBrand = brandHint ? normalizeFoodName(brandHint) : '';
    return normalizedBrand ? `${normalizedQuery}|${normalizedBrand}` : normalizedQuery;
}

function getLearnedResolution(query: string, brandHint?: string): LearnedResolution | null {
    const key = buildMemoryKey(query, brandHint);
    return getLearnedResolutions().get(key) ?? null;
}

export function rememberFoodResolution(query: string, food: FoodItem, brandHint?: string): void {
    const key = buildMemoryKey(query, brandHint);
    if (!key) return;

    const resolutions = getLearnedResolutions();
    const current = resolutions.get(key);
    resolutions.set(key, {
        foodId: food.id,
        canonicalId: getCanonicalFoodId(food),
        ts: Date.now(),
        count: (current?.count ?? 0) + 1,
    });
    setLearnedResolutions(resolutions);
    clearSearchCache();
}

function classifyCandidateConfidence(
    score: number,
    queryCoverage: number,
    exactNormalized: boolean,
    directPhraseMatch: boolean,
    queryTokenCount: number
): SearchConfidence {
    if (exactNormalized) return 'high';
    if (score >= HIGH_CONFIDENCE_SCORE && (queryCoverage >= 1 || (queryTokenCount <= 1 && directPhraseMatch))) return 'high';
    if (score >= MEDIUM_CONFIDENCE_SCORE && queryCoverage >= 0.5) return 'medium';
    return 'low';
}

function scoreFoodCandidate(
    query: string,
    food: FoodItem,
    brandHint?: string,
    learnedResolution?: LearnedResolution | null
): SearchFoodCandidate {
    const queryNormalized = normalizeFoodName(normalizeQueryForSearch(query));
    const canonicalId = getCanonicalFoodId(food);
    const foodTokens = getTokens(canonicalId);
    const queryTokens = [...getTokens(queryNormalized)];
    const overlap = queryTokens.filter(token => foodTokens.has(token) || canonicalId.includes(token));
    const uniqueOverlap = [...new Set(overlap)];
    const exactNormalized = queryNormalized.length > 0 && canonicalId === queryNormalized;
    const directPhraseMatch = !exactNormalized && queryNormalized.length > 0 && (canonicalId.includes(queryNormalized) || queryNormalized.includes(canonicalId));
    const queryCoverage = queryTokens.length > 0 ? uniqueOverlap.length / queryTokens.length : (directPhraseMatch ? 1 : 0);
    const tokenPrecision = foodTokens.size > 0 ? uniqueOverlap.length / foodTokens.size : 0;
    const source = getSourceKind(food.id);
    const trace: string[] = [];

    let score = 0;
    if (exactNormalized) {
        score += 0.54;
        trace.push('exact_name');
    } else if (directPhraseMatch) {
        score += 0.28;
        trace.push('phrase_match');
    }

    if (queryCoverage > 0) {
        score += queryCoverage * 0.28;
        trace.push(`coverage:${queryCoverage.toFixed(2)}`);
    }

    if (tokenPrecision > 0) {
        score += tokenPrecision * 0.14;
        trace.push(`precision:${tokenPrecision.toFixed(2)}`);
    }

    if (queryTokens.length > 0 && uniqueOverlap.length === queryTokens.length) {
        score += 0.08;
        trace.push('all_query_tokens_present');
    }

    if (queryTokens.length > 1 && uniqueOverlap.length < queryTokens.length) {
        const missingTokens = queryTokens.length - uniqueOverlap.length;
        score -= missingTokens * 0.14;
        trace.push(`missing_tokens:${missingTokens}`);
    }

    let brandMatched = false;
    if (brandHint) {
        const normalizedBrand = normalizeFoodName(brandHint);
        const normalizedFoodBrand = normalizeFoodName(food.brand || '');
        brandMatched = normalizedFoodBrand.includes(normalizedBrand) || normalizeFoodName(food.name).includes(normalizedBrand);
        if (brandMatched) {
            score += 0.18;
            trace.push('brand_match');
        } else {
            score -= 0.05;
            trace.push('brand_missing');
        }
    } else if (food.brand && queryNormalized.includes(normalizeFoodName(food.brand))) {
        brandMatched = true;
        score += 0.08;
        trace.push('brand_in_query');
    }

    const learned = Boolean(
        learnedResolution
        && (food.id === learnedResolution.foodId || canonicalId === learnedResolution.canonicalId)
    );
    if (learned && learnedResolution) {
        score += 0.32 + Math.min(learnedResolution.count, 3) * 0.02;
        trace.push('learned_resolution');
    }

    score += getSourcePriority(food.id) * 0.03;
    trace.push(`source:${source}`);

    if (food.micronutrients?.length) score += 0.01;

    score = Math.max(0, Math.min(1, score));
    const confidence = classifyCandidateConfidence(score, queryCoverage, exactNormalized, directPhraseMatch, queryTokens.length);

    return {
        food,
        score: Math.round(score * 1000) / 1000,
        confidence,
        canonicalId,
        source,
        trace,
        queryCoverage: Math.round(queryCoverage * 1000) / 1000,
        tokenPrecision: Math.round(tokenPrecision * 1000) / 1000,
        brandMatched,
        learned,
    };
}

function rankCandidates(
    query: string,
    items: FoodItem[],
    brandHint?: string,
    learnedResolution?: LearnedResolution | null,
    allowLowScores = false
): SearchFoodCandidate[] {
    const candidates = items
        .map(item => scoreFoodCandidate(query, item, brandHint, learnedResolution))
        .filter(candidate => allowLowScores || candidate.score >= MIN_CANDIDATE_SCORE)
        .sort((a, b) => {
            if (b.score !== a.score) return b.score - a.score;
            if (b.learned !== a.learned) return Number(b.learned) - Number(a.learned);
            return getSourcePriority(b.food.id) - getSourcePriority(a.food.id);
        });

    return candidates.slice(0, 20);
}

function deriveMatchType(candidates: SearchFoodCandidate[]): SearchMatchType {
    const best = candidates[0];
    if (!best) return 'exact';
    if (best.trace.includes('exact_name')) return 'exact';
    if (best.score >= 0.7 && best.queryCoverage >= 0.5) return 'partial';
    return 'fuzzy';
}

function summarizeCandidates(candidates: SearchFoodCandidate[]): {
    bestConfidence: SearchConfidence;
    canAutoSelect: boolean;
    decisionReason?: string;
} {
    const best = candidates[0];
    const second = candidates[1];
    if (!best) {
        return {
            bestConfidence: 'low',
            canAutoSelect: false,
            decisionReason: 'No se encontraron coincidencias.',
        };
    }

    const gap = second ? best.score - second.score : best.score;

    if (best.learned && best.score >= 0.68) {
        return {
            bestConfidence: best.confidence,
            canAutoSelect: true,
            decisionReason: 'Coincidencia reforzada por una resolucion aprendida.',
        };
    }

    if (best.confidence === 'high' && gap >= 0.08) {
        return {
            bestConfidence: best.confidence,
            canAutoSelect: true,
            decisionReason: 'Coincidencia de alta confianza.',
        };
    }

    if (best.confidence === 'medium' && best.queryCoverage >= 1 && gap >= SAFE_AUTOSELECT_GAP) {
        return {
            bestConfidence: best.confidence,
            canAutoSelect: true,
            decisionReason: 'Coincidencia suficientemente diferenciada para autoseleccion.',
        };
    }

    if (best.confidence === 'medium') {
        return {
            bestConfidence: best.confidence,
            canAutoSelect: false,
            decisionReason: 'Hay coincidencias plausibles, pero requieren confirmacion.',
        };
    }

    return {
        bestConfidence: best.confidence,
        canAutoSelect: false,
        decisionReason: 'La coincidencia es ambigua o de baja confianza.',
    };
}

let preloadStarted = false;

function ensurePreload(): void {
    if (preloadStarted || typeof window === 'undefined') return;
    preloadStarted = true;
    loadOFFOffline().catch(() => { /* ignore */ });
    loadUSDAOffline().catch(() => { /* ignore */ });
}

export async function searchFoods(
    query: string,
    settings?: Settings | null,
    brandHint?: string
): Promise<SearchFoodsResult> {
    ensurePreload();

    const normalizedQuery = normalizeQueryForSearch(query);
    if (!normalizedQuery || !hasSignificantQuery(normalizedQuery)) {
        return {
            results: [],
            matchType: 'exact',
            candidates: [],
            bestConfidence: 'low',
            canAutoSelect: false,
            decisionReason: 'La consulta no tiene suficientes tokens utiles.',
        };
    }

    const cacheKey = brandHint ? `${normalizedQuery}|${brandHint}` : normalizedQuery;
    const cache = getCache();
    const cached = cache.get(cacheKey);
    if (cached && Date.now() - cached.ts < CACHE_TTL_MS) {
        return {
            results: cached.results,
            matchType: cached.matchType,
            candidates: cached.candidates,
            bestScore: cached.bestScore,
            bestConfidence: cached.bestConfidence,
            canAutoSelect: cached.canAutoSelect,
            decisionReason: cached.decisionReason,
        };
    }

    const learnedResolution = getLearnedResolution(normalizedQuery, brandHint);
    const usdaKey = settings?.apiKeys?.usda || '';
    const { results: local } = searchLocalWithMatch(normalizedQuery);
    const [offOffline, usdaOffline] = await Promise.all([
        searchOFFOffline(normalizedQuery),
        searchUSDAOffline(normalizedQuery),
    ]);

    let merged = mergeAndDeduplicate(local, offOffline, usdaOffline);
    let candidates = rankCandidates(normalizedQuery, merged, brandHint, learnedResolution);

    const MIN_OFFLINE_RESULTS = 5;
    const canQueryOnline = normalizedQuery.length >= ONLINE_QUERY_MIN_CHARS
        && typeof navigator !== 'undefined'
        && navigator.onLine;

    if (candidates.length < MIN_OFFLINE_RESULTS && canQueryOnline) {
        try {
            const [offOnline, usdaOnline] = await Promise.all([
                searchOpenFoodFacts(normalizedQuery),
                searchUSDA(normalizedQuery, usdaKey),
            ]);
            merged = mergeAndDeduplicate(local, [...offOffline, ...offOnline], [...usdaOffline, ...usdaOnline]);
            candidates = rankCandidates(normalizedQuery, merged, brandHint, learnedResolution);
        } catch (_) {
            /* keep offline candidates */
        }
    }

    if (candidates.length === 0) {
        const fallbackPool = mergeAndDeduplicate(local, offOffline, usdaOffline);
        candidates = rankCandidates(normalizedQuery, fallbackPool, brandHint, learnedResolution, true);
    }

    const summary = summarizeCandidates(candidates);
    const result: SearchFoodsResult = {
        results: candidates.map(candidate => candidate.food),
        candidates,
        matchType: deriveMatchType(candidates),
        bestScore: candidates[0]?.score,
        bestConfidence: summary.bestConfidence,
        canAutoSelect: summary.canAutoSelect,
        decisionReason: summary.decisionReason,
    };

    cache.set(cacheKey, {
        query: query.trim(),
        results: result.results,
        candidates: result.candidates,
        matchType: result.matchType,
        bestScore: result.bestScore,
        bestConfidence: result.bestConfidence,
        canAutoSelect: result.canAutoSelect,
        decisionReason: result.decisionReason,
        ts: Date.now(),
    });
    setCache(cache);

    return result;
}

export function preloadFoodDatabases(): void {
    ensurePreload();
}
