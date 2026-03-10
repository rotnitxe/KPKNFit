import type { FoodItem, Settings } from '../types';
import { FOOD_DATABASE } from '../data/foodDatabase';
import { buildFoodSearchText, enrichFoodItem } from '../data/foodTaxonomy';
import { getNutritionConnectivity } from './nutritionConnectivityService';

const STOPWORDS = new Set([
    'de', 'y', 'la', 'el', 'en', 'a', 'al', 'del', 'los', 'las', 'un', 'una', 'por', 'para',
]);
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
const OFF_OFFLINE_URL = '/data/openFoodFactsOffline.json';
const USDA_OFFLINE_URL = '/data/usdaFoodsOffline.json';
const USDA_OFFLINE_FALLBACK_URL = '/data/usdaFoundationFoods.json';
const OFFLINE_LIMIT = 15;
const LOCAL_FOODS = FOOD_DATABASE.map(enrichFoodItem);

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

interface CacheEntry extends SearchFoodsResult {
    query: string;
    ts: number;
}

interface LearnedResolution {
    foodId: string;
    canonicalId: string;
    ts: number;
    count: number;
}

interface IndexedEntry<T> {
    raw: T;
    food: FoodItem;
    surface: string;
    canonicalId: string;
    tokens: string[];
}

interface IndexedCatalog<T> {
    entries: IndexedEntry<T>[];
    tokenIndex: Map<string, number[]>;
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

const USDA_MICRO_MAP = [
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

let preloadStarted = false;
let localIndex: IndexedCatalog<FoodItem> | null = null;
let offOfflineCache: any[] | null = null;
let offOfflineIndex: IndexedCatalog<any> | null = null;
let usdaOfflineCache: any[] | null = null;
let usdaOfflineIndex: IndexedCatalog<any> | null = null;

const unique = (values: (string | undefined | null)[]) => [
    ...new Set(
        values
            .filter((value): value is string => Boolean(value))
            .map(value => value.trim())
            .filter(Boolean)
    ),
];

const hasLocalStorage = () => typeof localStorage !== 'undefined';

const readLocalStorage = (key: string) => {
    try {
        return hasLocalStorage() ? localStorage.getItem(key) : null;
    } catch {
        return null;
    }
};

const writeLocalStorage = (key: string, value: string) => {
    try {
        if (hasLocalStorage()) {
            localStorage.setItem(key, value);
        }
    } catch {
        // ignore storage failures
    }
};

const removeLocalStorage = (key: string) => {
    try {
        if (hasLocalStorage()) {
            localStorage.removeItem(key);
        }
    } catch {
        // ignore storage failures
    }
};

async function fetchWithTimeout(url: string, timeoutMs = API_TIMEOUT_MS): Promise<Response> {
    const ctrl = new AbortController();
    const id = setTimeout(() => ctrl.abort(), timeoutMs);
    try {
        return await fetch(url, { signal: ctrl.signal });
    } finally {
        clearTimeout(id);
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
    return (name || '').replace(/\s+con\s+/gi, ' c/ ').toLowerCase();
}

function normalizeFoodName(name: string): string {
    return (name || '')
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
    if (id.startsWith('off-')) return 1;
    return 2;
}

function cleanRemoteTag(tag: string): string {
    return tag.replace(/^[a-z]{2}:/i, '').replace(/[_-]+/g, ' ').trim();
}

function buildIndexedCatalog<T>(records: T[], mapper: (raw: T) => FoodItem): IndexedCatalog<T> {
    const entries = records.map(raw => {
        const food = enrichFoodItem(mapper(raw));
        const surface = normalizeFoodName(buildFoodSearchText(food));
        const tokens = [...getTokens(surface)];
        return {
            raw,
            food,
            surface,
            canonicalId: getCanonicalFoodId(food),
            tokens,
        };
    });

    const tokenIndex = new Map<string, number[]>();
    entries.forEach((entry, index) => {
        entry.tokens.forEach(token => {
            tokenIndex.set(token, [...(tokenIndex.get(token) || []), index]);
        });
    });

    return { entries, tokenIndex };
}

function getLocalCatalog(): IndexedCatalog<FoodItem> {
    if (!localIndex) {
        localIndex = buildIndexedCatalog(LOCAL_FOODS, food => food);
    }
    return localIndex;
}

function fuzzyScore(query: string, surface: string): number {
    const normalizedQuery = normalizeFoodName(normalizeQueryForSearch(query));
    const normalizedSurface = normalizeFoodName(surface);
    if (!normalizedQuery || !normalizedSurface) {
        return 0;
    }

    const queryTokens = getTokens(normalizedQuery);
    const surfaceTokens = getTokens(normalizedSurface);
    if (queryTokens.size === 0) {
        return normalizedSurface.includes(normalizedQuery) ? 0.9 : 0;
    }

    const overlap = [...queryTokens].filter(token => surfaceTokens.has(token)).length;
    const union = new Set([...queryTokens, ...surfaceTokens]).size;
    const coverage = overlap / queryTokens.size;
    const jaccard = union > 0 ? overlap / union : 0;

    if (normalizedSurface.includes(normalizedQuery) || normalizedQuery.includes(normalizedSurface)) {
        return Math.max((jaccard * 0.5) + (coverage * 0.5), 0.85);
    }

    return (jaccard * 0.5) + (coverage * 0.5);
}

function foodItemFromOFF(product: any): FoodItem {
    const nutriments = product.nutriments || {};
    const kcal100 =
        nutriments['energy-kcal_100g']
        ?? nutriments['energy-kcal']
        ?? (nutriments['energy_100g'] ? nutriments['energy_100g'] / 4.184 : 0);

    const micronutrients = Object.entries(OFF_MICRO_MAP).flatMap(([key, meta]) => (
        typeof nutriments[key] === 'number' && nutriments[key] > 0
            ? [{ name: meta.name, amount: Math.round(nutriments[key] * 10) / 10, unit: meta.unit }]
            : []
    ));

    return enrichFoodItem({
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
        micronutrients: micronutrients.length ? micronutrients : undefined,
        tags: unique([
            ...(product.categories_tags || []).map(cleanRemoteTag),
            ...(product.labels_tags || []).map(cleanRemoteTag),
        ]),
        searchAliases: unique([
            product.product_name_es,
            product.generic_name,
            product.generic_name_es,
            product.abbreviated_product_name,
        ]),
    });
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
    }, {
        calories: 0,
        protein: 0,
        carbs: 0,
        fats: 0,
        saturated: 0,
        trans: 0,
    });

    const micronutrients = (food.foodNutrients || []).flatMap((nutrient: any) => {
        const rawName = (nutrient.nutrientName || nutrient.nutrient?.name) || '';
        const value = nutrient.value ?? nutrient.amount ?? nutrient.median ?? 0;
        if (typeof value !== 'number' || value <= 0) {
            return [];
        }
        const match = USDA_MICRO_MAP.find(entry => entry.pattern.test(rawName));
        return match ? [{ name: match.name, amount: Math.round(value * 10) / 10, unit: match.unit }] : [];
    });

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
        searchAliases: unique([
            food.additionalDescriptions,
            food.lowercaseDescription,
            food.shortDescription,
        ]),
        tags: unique([
            typeof food.foodCategory === 'string'
                ? cleanRemoteTag(food.foodCategory)
                : cleanRemoteTag(food.foodCategory?.description || ''),
            cleanRemoteTag(food.foodClass || ''),
            cleanRemoteTag(food.dataType || ''),
        ]),
    };

    if (nutrients.saturated > 0) {
        item.fatBreakdown = {
            saturated: nutrients.saturated,
            monounsaturated: 0,
            polyunsaturated: 0,
            trans: nutrients.trans ?? 0,
        };
    }
    if (micronutrients.length) {
        item.micronutrients = micronutrients;
    }

    return enrichFoodItem(item);
}

function searchCatalog<T>(
    query: string,
    catalog: IndexedCatalog<T>,
    limit = OFFLINE_LIMIT
): { results: FoodItem[]; matchType: SearchMatchType } {
    const normalizedQuery = normalizeQueryForSearch(query);
    if (!normalizedQuery || !hasSignificantQuery(normalizedQuery)) {
        return { results: [], matchType: 'exact' };
    }

    const searchTerms = getSignificantQueryTokens(normalizedQuery);
    const candidateIndexes = new Set<number>();
    searchTerms.forEach(term => {
        (catalog.tokenIndex.get(term) || []).forEach(index => candidateIndexes.add(index));
    });

    const pool = candidateIndexes.size
        ? [...candidateIndexes].map(index => catalog.entries[index])
        : catalog.entries
            .filter(entry => entry.surface.includes((searchTerms[0] || normalizedQuery).slice(0, 4)))
            .slice(0, 1200);

    const direct = pool
        .filter(entry => searchTerms.some(term => entry.surface.includes(term)))
        .slice(0, limit);

    if (direct.length) {
        const queryName = normalizeFoodName(normalizedQuery);
        const hasExact = direct.some(entry =>
            entry.canonicalId === queryName
            || entry.canonicalId.includes(queryName)
            || queryName.includes(entry.canonicalId)
        );

        return {
            results: direct.map(entry => entry.food),
            matchType: hasExact ? 'exact' : 'partial',
        };
    }

    return {
        results: pool
            .map(entry => ({ entry, score: fuzzyScore(normalizedQuery, entry.surface) }))
            .filter(entry => entry.score >= FUZZY_THRESHOLD)
            .sort((a, b) => b.score - a.score)
            .slice(0, limit)
            .map(entry => entry.entry.food),
        matchType: 'fuzzy',
    };
}

function parseUSDAOfflineData(data: unknown): any[] {
    if (Array.isArray(data)) return data;
    if (data && typeof data === 'object' && 'FoundationFoods' in data) return (data as any).FoundationFoods || [];
    if (data && typeof data === 'object' && 'foods' in data) return (data as any).foods || [];
    return [];
}

async function loadOFFOffline(): Promise<any[]> {
    if (offOfflineCache) return offOfflineCache;

    const base = typeof window !== 'undefined' && window.location?.origin ? window.location.origin : '';
    try {
        const response = await fetch(base + OFF_OFFLINE_URL);
        const data = response.ok ? await response.json() : [];
        const products: any[] = Array.isArray(data)
            ? data
            : Array.isArray(data?.products)
                ? data.products
                : [];
        offOfflineCache = products;
        return products;
    } catch {
        return [];
    }
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
            if (foods.length) {
                usdaOfflineCache = foods;
                return foods;
            }
        } catch {
            // ignore offline source failures
        }
    }

    usdaOfflineCache = [];
    return [];
}

async function getOFFOfflineCatalog(): Promise<IndexedCatalog<any>> {
    if (!offOfflineIndex) {
        offOfflineIndex = buildIndexedCatalog(await loadOFFOffline(), foodItemFromOFF);
    }
    return offOfflineIndex;
}

async function getUSDAOfflineCatalog(): Promise<IndexedCatalog<any>> {
    if (!usdaOfflineIndex) {
        usdaOfflineIndex = buildIndexedCatalog(await loadUSDAOffline(), foodItemFromUSDA);
    }
    return usdaOfflineIndex;
}

async function searchOFFOffline(query: string): Promise<FoodItem[]> {
    return searchCatalog(query, await getOFFOfflineCatalog()).results;
}

async function searchUSDAOffline(query: string): Promise<FoodItem[]> {
    return searchCatalog(query, await getUSDAOfflineCatalog()).results;
}

async function searchOpenFoodFacts(query: string): Promise<FoodItem[]> {
    if (!hasSignificantQuery(query) || query.trim().length < ONLINE_QUERY_MIN_CHARS) {
        return [];
    }

    const searchTerms = getSignificantQueryTokens(query).join(' ') || query.trim();
    try {
        const fields = [
            'code',
            'product_name',
            'product_name_es',
            'generic_name',
            'generic_name_es',
            'brands',
            'nutriments',
            'image_small_url',
            'image_front_small_url',
            'categories_tags',
            'labels_tags',
        ].join(',');
        const url = `https://world.openfoodfacts.org/cgi/search.pl?search_terms=${encodeURIComponent(searchTerms)}&search_simple=1&json=1&page_size=10&fields=${encodeURIComponent(fields)}`;
        const response = await fetchWithTimeout(url);
        const data = await response.json();
        const products = data.products || [];

        return products
            .filter((product: any) =>
                product.product_name
                && (product.nutriments?.['energy-kcal_100g'] != null || product.nutriments?.proteins_100g != null)
            )
            .map(foodItemFromOFF)
            .slice(0, 10);
    } catch {
        return searchOFFOffline(query);
    }
}

async function searchUSDA(query: string, apiKey: string): Promise<FoodItem[]> {
    if (apiKey && query.trim().length >= ONLINE_QUERY_MIN_CHARS) {
        try {
            const url = `https://api.nal.usda.gov/fdc/v1/foods/search?api_key=${encodeURIComponent(apiKey)}&query=${encodeURIComponent(query)}&pageSize=10`;
            const response = await fetchWithTimeout(url);
            const data = await response.json();
            const foods = data.foods || [];
            if (foods.length) {
                return foods.slice(0, 10).map(foodItemFromUSDA);
            }
        } catch {
            // fallback to offline USDA
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
    merged.tags = unique([...(merged.tags || []), ...(fallback.tags || [])]);
    merged.searchAliases = unique([...(merged.searchAliases || []), ...(fallback.searchAliases || [])]);
    if (!merged.category && fallback.category) merged.category = fallback.category;
    if (!merged.subcategory && fallback.subcategory) merged.subcategory = fallback.subcategory;
    return merged;
}

function areSimilarFoods(a: FoodItem, b: FoodItem): boolean {
    const canonicalA = getCanonicalFoodId(a);
    const canonicalB = getCanonicalFoodId(b);
    if (canonicalA === canonicalB) {
        return true;
    }

    const tokensA = getTokens(canonicalA);
    const tokensB = getTokens(canonicalB);
    if (!tokensA.size || !tokensB.size) {
        return false;
    }

    const overlap = [...tokensA].filter(token => tokensB.has(token)).length;
    return overlap >= Math.ceil(Math.min(tokensA.size, tokensB.size) * 0.7);
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

        group.forEach(candidate => {
            if (candidate.id !== best.id) {
                merged = mergeNutrients(merged, candidate);
            }
        });

        result.push(enrichFoodItem(merged));
        group.forEach(candidate => used.add(candidate.id));
    }

    return result.slice(0, 30);
}

function getCache(): Map<string, CacheEntry> {
    try {
        const raw = readLocalStorage(CACHE_KEY);
        if (!raw) return new Map();

        const map = new Map(JSON.parse(raw) as [string, CacheEntry][]);
        const now = Date.now();
        for (const [key, value] of map) {
            if (now - value.ts > CACHE_TTL_MS) {
                map.delete(key);
            }
        }
        return map;
    } catch {
        return new Map();
    }
}

function setCache(map: Map<string, CacheEntry>): void {
    writeLocalStorage(CACHE_KEY, JSON.stringify(Array.from(map.entries()).slice(-CACHE_MAX_ENTRIES)));
}

function clearSearchCache(): void {
    removeLocalStorage(CACHE_KEY);
}

function getLearnedResolutions(): Map<string, LearnedResolution> {
    try {
        const raw = readLocalStorage(LEARNED_RESOLUTIONS_KEY);
        return raw ? new Map(JSON.parse(raw) as [string, LearnedResolution][]) : new Map();
    } catch {
        return new Map();
    }
}

function setLearnedResolutions(map: Map<string, LearnedResolution>): void {
    writeLocalStorage(
        LEARNED_RESOLUTIONS_KEY,
        JSON.stringify(
            Array.from(map.entries())
                .sort((a, b) => b[1].ts - a[1].ts)
                .slice(0, LEARNED_MAX_ENTRIES)
        )
    );
}

function buildMemoryKey(query: string, brandHint?: string): string {
    const normalizedQuery = normalizeFoodName(normalizeQueryForSearch(query));
    const normalizedBrand = brandHint ? normalizeFoodName(brandHint) : '';
    return normalizedBrand ? `${normalizedQuery}|${normalizedBrand}` : normalizedQuery;
}

function getLearnedResolution(query: string, brandHint?: string): LearnedResolution | null {
    return getLearnedResolutions().get(buildMemoryKey(query, brandHint)) ?? null;
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
    const matchSurface = normalizeFoodName(buildFoodSearchText(food));
    const foodTokens = getTokens(matchSurface);
    const queryTokens = [...getTokens(queryNormalized)];
    const overlap = queryTokens.filter(token => foodTokens.has(token) || matchSurface.includes(token));
    const uniqueOverlap = [...new Set(overlap)];
    const exactNormalized = queryNormalized.length > 0 && canonicalId === queryNormalized;
    const directPhraseMatch = !exactNormalized
        && queryNormalized.length > 0
        && (canonicalId.includes(queryNormalized) || queryNormalized.includes(canonicalId) || matchSurface.includes(queryNormalized));
    const queryCoverage = queryTokens.length > 0 ? uniqueOverlap.length / queryTokens.length : (directPhraseMatch ? 1 : 0);
    const tokenPrecision = foodTokens.size > 0 ? uniqueOverlap.length / foodTokens.size : 0;
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
        const missing = queryTokens.length - uniqueOverlap.length;
        score -= missing * 0.14;
        trace.push(`missing_tokens:${missing}`);
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

    if (food.tags?.length) {
        score += Math.min(food.tags.length, 4) * 0.01;
        trace.push('semantic_tags');
    }
    score += getSourcePriority(food.id) * 0.03;
    trace.push(`source:${getSourceKind(food.id)}`);
    if (food.micronutrients?.length) {
        score += 0.01;
    }

    score = Math.max(0, Math.min(1, score));
    const confidence = classifyCandidateConfidence(
        score,
        queryCoverage,
        exactNormalized,
        directPhraseMatch,
        queryTokens.length
    );

    return {
        food,
        score: Math.round(score * 1000) / 1000,
        confidence,
        canonicalId,
        source: getSourceKind(food.id),
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
    return items
        .map(item => scoreFoodCandidate(query, item, brandHint, learnedResolution))
        .filter(candidate => allowLowScores || candidate.score >= MIN_CANDIDATE_SCORE)
        .sort((a, b) => (
            b.score !== a.score
                ? b.score - a.score
                : getSourcePriority(b.food.id) - getSourcePriority(a.food.id)
        ))
        .slice(0, 20);
}

function deriveMatchType(candidates: SearchFoodCandidate[]): SearchMatchType {
    const best = candidates[0];
    if (!best) return 'exact';
    if (best.trace.includes('exact_name')) return 'exact';
    if (best.score >= 0.7 && best.queryCoverage >= 0.5) return 'partial';
    return 'fuzzy';
}

function summarizeCandidates(candidates: SearchFoodCandidate[]) {
    const best = candidates[0];
    const second = candidates[1];

    if (!best) {
        return {
            bestConfidence: 'low' as SearchConfidence,
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

function ensurePreload(): void {
    if (preloadStarted || typeof window === 'undefined') return;

    preloadStarted = true;
    getLocalCatalog();
    loadOFFOffline().then(() => getOFFOfflineCatalog()).catch(() => {});
    loadUSDAOffline().then(() => getUSDAOfflineCatalog()).catch(() => {});
}

function searchLocalWithMatch(query: string) {
    return searchCatalog(query, getLocalCatalog(), 30);
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

    const connectivity = await getNutritionConnectivity(settings);
    const cacheKey = `${brandHint ? `${normalizedQuery}|${brandHint}` : normalizedQuery}|online:${Number(connectivity.canUseInternetApis)}|usda:${Number(connectivity.canUseUsdaApi)}`;
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
    const { results: local } = searchLocalWithMatch(normalizedQuery);
    const [offOffline, usdaOffline] = await Promise.all([
        searchOFFOffline(normalizedQuery),
        searchUSDAOffline(normalizedQuery),
    ]);

    let merged = mergeAndDeduplicate(local, offOffline, usdaOffline);
    let candidates = rankCandidates(normalizedQuery, merged, brandHint, learnedResolution);

    if (
        candidates.length < 5
        && normalizedQuery.length >= ONLINE_QUERY_MIN_CHARS
        && connectivity.canUseInternetApis
    ) {
        const [offOnline, usdaOnline] = await Promise.all([
            connectivity.canUseOpenFoodFactsApi
                ? searchOpenFoodFacts(normalizedQuery)
                : Promise.resolve([]),
            connectivity.canUseUsdaApi
                ? searchUSDA(normalizedQuery, settings?.apiKeys?.usda || '')
                : Promise.resolve([]),
        ]);
        merged = mergeAndDeduplicate(local, [...offOffline, ...offOnline], [...usdaOffline, ...usdaOnline]);
        candidates = rankCandidates(normalizedQuery, merged, brandHint, learnedResolution);
    }

    if (!candidates.length) {
        candidates = rankCandidates(
            normalizedQuery,
            mergeAndDeduplicate(local, offOffline, usdaOffline),
            brandHint,
            learnedResolution,
            true
        );
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

    cache.set(cacheKey, { query: query.trim(), ...result, ts: Date.now() });
    setCache(cache);
    return result;
}

export function preloadFoodDatabases(): void {
    ensurePreload();
}
