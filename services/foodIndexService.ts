import type { FoodItem, ParsedMealDescription, ParsedMealItem, Settings } from '../types';
import { FOOD_DATABASE } from '../data/foodDatabase';
import { buildFoodSearchText, enrichFoodItem } from '../data/foodTaxonomy';
import { LOCAL_CHILEAN_FOOD_CATALOG } from '../data/localChileanFoods';
import { parseMealDescription } from '../utils/nutritionDescriptionParser';
import { storageService } from './storageService';

const STOPWORDS = new Set([
    'de', 'y', 'la', 'el', 'en', 'a', 'al', 'del', 'los', 'las', 'un', 'una', 'por', 'para',
]);
const HOT_QUERY_LIMIT = 12;
const EXTENDED_QUERY_LIMIT = 20;
const MIN_CANDIDATE_SCORE = 0.2;
const HIGH_CONFIDENCE_SCORE = 0.86;
const MEDIUM_CONFIDENCE_SCORE = 0.6;
const SAFE_AUTOSELECT_GAP = 0.16;
const EXTENDED_QUERY_MIN_CHARS = 3;
const SEARCH_CACHE_TTL_MS = 10 * 60 * 1000;
const LEARNED_RESOLUTIONS_KEY = 'kpkn-food-resolution-memory-v2';
const LEGACY_LEARNED_RESOLUTIONS_KEY = 'kpkn-food-resolution-memory';
const LEARNED_MAX_ENTRIES = 250;
const HOT_PACK_VERSION = 'food-hot-v1';
const EXTENDED_PACK_VERSION = 'food-extended-v1';
const OFF_OFFLINE_URL = '/data/openFoodFactsOffline.json';
const USDA_OFFLINE_URL = '/data/usdaFoodsOffline.json';
const USDA_OFFLINE_FALLBACK_URL = '/data/usdaFoundationFoods.json';
const HOT_FOODS = [...FOOD_DATABASE, ...LOCAL_CHILEAN_FOOD_CATALOG].map(enrichFoodItem);

export type FoodPackScope = 'hot' | 'extended';
export type SearchMatchType = 'exact' | 'partial' | 'fuzzy';
export type SearchConfidence = 'high' | 'medium' | 'low';
export type SearchSource = 'local' | 'off' | 'usda';

export interface FoodCandidate {
    foodId: string;
    displayName: string;
    brand?: string;
    score: number;
    confidence: SearchConfidence;
    source: SearchSource;
    matchedAlias?: string;
    why: string;
    food: FoodItem;
    canonicalId: string;
    trace: string[];
    queryCoverage: number;
    tokenPrecision: number;
    brandMatched: boolean;
    learned: boolean;
}

export type SearchFoodCandidate = FoodCandidate;

export interface SearchFoodsResult {
    results: FoodItem[];
    matchType: SearchMatchType;
    candidates: SearchFoodCandidate[];
    bestScore?: number;
    bestConfidence: SearchConfidence;
    canAutoSelect: boolean;
    decisionReason?: string;
}

export interface FoodPackStatus {
    hotReady: boolean;
    extendedReady: boolean;
    hotVersion: string;
    extendedVersion: string | null;
    bytesOnDisk: number;
    lastUpdatedAt: string | null;
}

export interface FoodResolutionResult {
    items: Array<{
        parsedItem: ParsedMealItem;
        candidate: SearchFoodCandidate | null;
        candidates: SearchFoodCandidate[];
        decisionSource: 'database' | 'user-memory' | 'review';
        confidence: SearchConfidence;
        reviewRequired: boolean;
    }>;
    confidence: SearchConfidence;
    decisionSource: 'deterministic';
    localLookupMs: number;
    remoteLookupMs: number;
    packVersion: string;
    reviewRequired: boolean;
    parsed: ParsedMealDescription;
}

export interface FoodIndexSearchOptions {
    scope?: FoodPackScope;
    settings?: Settings | null;
    brandHint?: string;
    limit?: number;
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

let hotIndexPromise: Promise<IndexedCatalog<FoodItem>> | null = null;
let offLoadPromise: Promise<any[]> | null = null;
let offIndexPromise: Promise<IndexedCatalog<any>> | null = null;
let usdaLoadPromise: Promise<any[]> | null = null;
let usdaIndexPromise: Promise<IndexedCatalog<any>> | null = null;
let learnedLoadPromise: Promise<Map<string, LearnedResolution>> | null = null;
let learnedSnapshot = new Map<string, LearnedResolution>();
let learnedSnapshotLoaded = false;
let lastExtendedPackReadyAt: string | null = null;
const searchCache = new Map<string, SearchFoodsResult & { ts: number }>();

const unique = (values: (string | undefined | null)[]) => [
    ...new Set(
        values
            .filter((value): value is string => Boolean(value))
            .map(value => value.trim())
            .filter(Boolean),
    ),
];

const hasWindow = () => typeof window !== 'undefined';

const hasLocalStorage = () => typeof localStorage !== 'undefined';

const canUsePersistentStorage = () => hasWindow() && typeof indexedDB !== 'undefined';

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
        // ignore
    }
};

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
        .replace(/\b(generico|genérico|cocido|crudo|raw|cooked)\b/gi, ' ')
        .replace(/[^\p{Letter}\p{Number}\s/+-]+/gu, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

function getTokens(normalized: string): Set<string> {
    return new Set(
        normalized
            .split(/\s+/)
            .map(token => token.trim())
            .filter(token => token.length > 2 && !/^\d+$/.test(token) && !STOPWORDS.has(token)),
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

function buildMemoryKey(query: string, brandHint?: string): string {
    const normalizedQuery = normalizeFoodName(normalizeQueryForSearch(query));
    const normalizedBrand = brandHint ? normalizeFoodName(brandHint) : '';
    return normalizedBrand ? `${normalizedQuery}|${normalizedBrand}` : normalizedQuery;
}

function getSourceKind(id: string): SearchSource {
    if (id.startsWith('usda-')) return 'usda';
    if (id.startsWith('off-')) return 'off';
    return 'local';
}

function getSourcePriority(id: string): number {
    if (id.startsWith('off-')) return 1;
    if (id.startsWith('usda-')) return 2;
    return 3;
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

async function getHotCatalog(): Promise<IndexedCatalog<FoodItem>> {
    if (!hotIndexPromise) {
        hotIndexPromise = Promise.resolve(buildIndexedCatalog(HOT_FOODS, food => food));
    }
    return hotIndexPromise;
}

async function loadOFFOffline(): Promise<any[]> {
    if (!offLoadPromise) {
        offLoadPromise = (async () => {
            const base = hasWindow() && window.location?.origin ? window.location.origin : '';
            try {
                const response = await fetch(base + OFF_OFFLINE_URL);
                if (!response.ok) return [];
                const data = await response.json();
                return Array.isArray(data)
                    ? data
                    : Array.isArray(data?.products)
                        ? data.products
                        : [];
            } catch {
                return [];
            }
        })();
    }
    return offLoadPromise;
}

function parseUSDAOfflineData(data: unknown): any[] {
    if (Array.isArray(data)) return data;
    if (data && typeof data === 'object' && 'FoundationFoods' in data) return (data as any).FoundationFoods || [];
    if (data && typeof data === 'object' && 'foods' in data) return (data as any).foods || [];
    return [];
}

async function loadUSDAOffline(): Promise<any[]> {
    if (!usdaLoadPromise) {
        usdaLoadPromise = (async () => {
            const base = hasWindow() && window.location?.origin ? window.location.origin : '';
            for (const url of [USDA_OFFLINE_URL, USDA_OFFLINE_FALLBACK_URL]) {
                try {
                    const response = await fetch(base + url);
                    if (!response.ok) continue;
                    const data = await response.json();
                    const foods = parseUSDAOfflineData(data);
                    if (foods.length) {
                        return foods;
                    }
                } catch {
                    // ignore source failures
                }
            }
            return [];
        })();
    }
    return usdaLoadPromise;
}

async function getOFFCatalog(): Promise<IndexedCatalog<any>> {
    if (!offIndexPromise) {
        offIndexPromise = loadOFFOffline().then(products => {
            if (products.length) {
                lastExtendedPackReadyAt = new Date().toISOString();
            }
            return buildIndexedCatalog(products, foodItemFromOFF);
        });
    }
    return offIndexPromise;
}

async function getUSDACatalog(): Promise<IndexedCatalog<any>> {
    if (!usdaIndexPromise) {
        usdaIndexPromise = loadUSDAOffline().then(foods => {
            if (foods.length) {
                lastExtendedPackReadyAt = new Date().toISOString();
            }
            return buildIndexedCatalog(foods, foodItemFromUSDA);
        });
    }
    return usdaIndexPromise;
}

export async function warmFoodIndex(scope: FoodPackScope = 'hot'): Promise<void> {
    ensureLearnedSnapshotSync();
    await getHotCatalog();

    if (scope === 'extended') {
        await Promise.all([getOFFCatalog(), getUSDACatalog(), loadLearnedSnapshot()]);
    } else {
        void loadLearnedSnapshot();
    }
}

export async function getFoodPackStatus(): Promise<FoodPackStatus> {
    ensureLearnedSnapshotSync();
    const latestLearnedEntry = [...learnedSnapshot.values()].sort((a, b) => b.ts - a.ts)[0];

    return {
        hotReady: Boolean(hotIndexPromise),
        extendedReady: Boolean(offIndexPromise || usdaIndexPromise),
        hotVersion: HOT_PACK_VERSION,
        extendedVersion: lastExtendedPackReadyAt ? EXTENDED_PACK_VERSION : null,
        bytesOnDisk: getPackBytesEstimate(),
        lastUpdatedAt: lastExtendedPackReadyAt || (latestLearnedEntry ? new Date(latestLearnedEntry.ts).toISOString() : null),
    };
}

async function runCatalogSearches(query: string, scope: FoodPackScope): Promise<FoodItem[][]> {
    const hotResults = searchCatalog(query, await getHotCatalog(), HOT_QUERY_LIMIT);

    if (scope !== 'extended' || query.trim().length < EXTENDED_QUERY_MIN_CHARS) {
        return [hotResults];
    }

    const [offCatalog, usdaCatalog] = await Promise.all([getOFFCatalog(), getUSDACatalog()]);
    const offResults = searchCatalog(query, offCatalog, HOT_QUERY_LIMIT);
    const usdaResults = searchCatalog(query, usdaCatalog, HOT_QUERY_LIMIT);
    return [hotResults, offResults, usdaResults];
}

export async function searchFoodIndex(
    query: string,
    options: FoodIndexSearchOptions = {},
): Promise<SearchFoodsResult> {
    const scope = options.scope ?? 'hot';
    const brandHint = options.brandHint;
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

    const cacheKey = getCacheKey(normalizedQuery, scope, brandHint);
    const cached = searchCache.get(cacheKey);
    if (cached && typeof (cached as any).ts === 'number' && Date.now() - (cached as any).ts < SEARCH_CACHE_TTL_MS) {
        return cached;
    }

    await warmFoodIndex(scope);
    const learnedResolution = getLearnedResolution(normalizedQuery, brandHint);
    const merged = mergeAndDeduplicate(...(await runCatalogSearches(normalizedQuery, scope)));
    let candidates = rankCandidates(normalizedQuery, merged, brandHint, learnedResolution);

    if (!candidates.length) {
        candidates = rankCandidates(normalizedQuery, merged, brandHint, learnedResolution, true);
    }

    const summary = summarizeCandidates(candidates);
    const result: SearchFoodsResult = {
        results: candidates
            .slice(0, options.limit ?? (scope === 'extended' ? EXTENDED_QUERY_LIMIT : HOT_QUERY_LIMIT))
            .map(candidate => candidate.food),
        candidates: candidates.slice(0, options.limit ?? EXTENDED_QUERY_LIMIT),
        matchType: deriveMatchType(candidates),
        bestScore: candidates[0]?.score,
        bestConfidence: summary.bestConfidence,
        canAutoSelect: summary.canAutoSelect,
        decisionReason: summary.decisionReason,
    };

    searchCache.set(cacheKey, Object.assign({ ts: Date.now() }, result));
    return result;
}

export function searchFoodIndexExtended(
    query: string,
    options: Omit<FoodIndexSearchOptions, 'scope'> = {},
): Promise<SearchFoodsResult> {
    return searchFoodIndex(query, { ...options, scope: 'extended' });
}

export function rememberFoodResolution(query: string, food: FoodItem, brandHint?: string): void {
    if (!query.trim()) return;

    const snapshot = new Map(ensureLearnedSnapshotSync());
    const key = buildMemoryKey(query, brandHint);
    const current = snapshot.get(key);
    snapshot.set(key, {
        foodId: food.id,
        canonicalId: getCanonicalFoodId(food),
        ts: Date.now(),
        count: (current?.count ?? 0) + 1,
    });

    learnedSnapshot = trimLearnedSnapshot(snapshot);
    learnedSnapshotLoaded = true;
    searchCache.clear();
    void persistLearnedSnapshot();
}

export function getFoodResolutionMemoryHints(limit = 24): string[] {
    const snapshot = ensureLearnedSnapshotSync();
    const foodsById = new Map(HOT_FOODS.map(food => [food.id, food] as const));

    return [...snapshot.entries()]
        .sort((a, b) => b[1].ts - a[1].ts)
        .slice(0, limit)
        .map(([query, resolution]) => {
            const matchedFood = foodsById.get(resolution.foodId);
            const canonical = matchedFood?.name || resolution.canonicalId;
            return `${query} => ${canonical}`;
        });
}

function applyResolutionToParsedItem(item: ParsedMealItem, result: SearchFoodsResult) {
    const candidate = result.candidates[0] ?? null;
    const reviewRequired = !candidate || !result.canAutoSelect;

    return {
        parsedItem: {
            ...item,
            isFuzzyMatch: result.matchType === 'fuzzy',
            analysisSource: candidate ? (candidate.learned ? 'user-memory' : 'database') : item.analysisSource,
            analysisConfidence: candidate?.score ?? item.analysisConfidence,
            reviewRequired,
        },
        candidate,
        candidates: result.candidates.slice(0, 4),
        decisionSource: (reviewRequired
            ? 'review'
            : (candidate?.learned ? 'user-memory' : 'database')) as 'review' | 'user-memory' | 'database',
        confidence: candidate?.confidence ?? result.bestConfidence,
        reviewRequired,
    };
}

export async function resolveFoodDescription(
    text: string,
    settings?: Settings | null,
): Promise<FoodResolutionResult> {
    const startedAt = performance.now();
    const parsed = parseMealDescription(text);
    const resolutionItems: FoodResolutionResult['items'] = [];

    for (const item of parsed.items) {
        const result = await searchFoodIndex(item.tag, {
            scope: 'hot',
            settings,
            brandHint: item.brandHint,
        });
        resolutionItems.push(applyResolutionToParsedItem(item, result));
    }

    const localLookupMs = Math.round(performance.now() - startedAt);
    const reviewRequired = resolutionItems.some(item => item.reviewRequired);
    const confidence: SearchConfidence = resolutionItems.every(item => item.confidence === 'high' && !item.reviewRequired)
        ? 'high'
        : resolutionItems.some(item => item.confidence === 'medium')
            ? 'medium'
            : 'low';

    return {
        items: resolutionItems,
        confidence,
        decisionSource: 'deterministic',
        localLookupMs,
        remoteLookupMs: 0,
        packVersion: HOT_PACK_VERSION,
        reviewRequired,
        parsed: {
            ...parsed,
            analysisEngine: 'deterministic',
            modelVersion: null,
        },
    };
}

function searchCatalog(query: string, catalog: IndexedCatalog<any>, limit = HOT_QUERY_LIMIT): FoodItem[] {
    const normalizedQuery = normalizeFoodName(normalizeQueryForSearch(query));
    const queryTokens = [...getTokens(normalizedQuery)];
    const candidateIndices = new Set<number>();

    queryTokens.forEach(token => {
        (catalog.tokenIndex.get(token) || []).forEach(index => candidateIndices.add(index));
    });

    if (normalizedQuery.length >= 3) {
        catalog.entries.forEach((entry, index) => {
            if (
                entry.canonicalId === normalizedQuery
                || entry.surface.includes(normalizedQuery)
                || normalizedQuery.includes(entry.canonicalId)
            ) {
                candidateIndices.add(index);
            }
        });
    }

    const sourceEntries = candidateIndices.size > 0
        ? [...candidateIndices].map(index => catalog.entries[index])
        : catalog.entries;

    return sourceEntries
        .map(entry => {
            const queryCoverage = queryTokens.length > 0
                ? queryTokens.filter(token => entry.tokens.includes(token) || entry.surface.includes(token)).length / queryTokens.length
                : 0;
            const score = Math.max(fuzzyScore(normalizedQuery, entry.surface), queryCoverage);
            return { entry, score };
        })
        .filter(item => item.score >= 0.18)
        .sort((a, b) => (
            b.score !== a.score
                ? b.score - a.score
                : getSourcePriority(b.entry.food.id) - getSourcePriority(a.entry.food.id)
        ))
        .slice(0, limit)
        .map(item => item.entry.food);
}

function mergeNutrients(base: FoodItem, candidate: FoodItem): FoodItem {
    const micronutrients = unique([
        ...(base.micronutrients || []).map(micro => JSON.stringify(micro)),
        ...(candidate.micronutrients || []).map(micro => JSON.stringify(micro)),
    ]).map(value => JSON.parse(value));

    return {
        ...candidate,
        ...base,
        image: base.image || candidate.image,
        brand: base.brand || candidate.brand,
        searchAliases: unique([...(base.searchAliases || []), ...(candidate.searchAliases || [])]),
        tags: unique([...(base.tags || []), ...(candidate.tags || [])]),
        micronutrients: micronutrients.length ? micronutrients : (base.micronutrients || candidate.micronutrients),
        fatBreakdown: base.fatBreakdown || candidate.fatBreakdown,
    };
}

function areSimilarFoods(left: FoodItem, right: FoodItem): boolean {
    const leftCanonical = getCanonicalFoodId(left);
    const rightCanonical = getCanonicalFoodId(right);
    if (!leftCanonical || !rightCanonical) {
        return false;
    }
    if (leftCanonical === rightCanonical) {
        return true;
    }

    const leftBrand = normalizeFoodName(left.brand || '');
    const rightBrand = normalizeFoodName(right.brand || '');
    if (leftBrand && rightBrand && leftBrand !== rightBrand) {
        return false;
    }

    return fuzzyScore(leftCanonical, rightCanonical) >= 0.92;
}

function mergeAndDeduplicate(...groups: FoodItem[][]): FoodItem[] {
    const merged: FoodItem[] = [];

    groups.flat().forEach(food => {
        const existingIndex = merged.findIndex(current => areSimilarFoods(current, food));
        if (existingIndex < 0) {
            merged.push(food);
            return;
        }

        const current = merged[existingIndex];
        const preferred = getSourcePriority(food.id) >= getSourcePriority(current.id)
            ? mergeNutrients(food, current)
            : mergeNutrients(current, food);
        merged[existingIndex] = enrichFoodItem(preferred);
    });

    return merged;
}

function hydrateLearnedSnapshot(value: unknown): Map<string, LearnedResolution> {
    if (!value || typeof value !== 'object') {
        return new Map();
    }

    return new Map(
        Object.entries(value as Record<string, LearnedResolution>)
            .filter(([, resolution]) => Boolean(resolution?.foodId))
            .map(([query, resolution]) => [
                query,
                {
                    foodId: resolution.foodId,
                    canonicalId: resolution.canonicalId,
                    ts: typeof resolution.ts === 'number' ? resolution.ts : Date.now(),
                    count: typeof resolution.count === 'number' ? resolution.count : 1,
                },
            ]),
    );
}

function snapshotToObject(snapshot: Map<string, LearnedResolution>): Record<string, LearnedResolution> {
    return Object.fromEntries(snapshot.entries());
}

function trimLearnedSnapshot(snapshot: Map<string, LearnedResolution>): Map<string, LearnedResolution> {
    const sorted = [...snapshot.entries()].sort((a, b) => b[1].ts - a[1].ts);
    return new Map(sorted.slice(0, LEARNED_MAX_ENTRIES));
}

function ensureLearnedSnapshotSync(): Map<string, LearnedResolution> {
    if (learnedSnapshotLoaded) {
        return learnedSnapshot;
    }

    const stored = readLocalStorage(LEARNED_RESOLUTIONS_KEY) || readLocalStorage(LEGACY_LEARNED_RESOLUTIONS_KEY);
    if (stored) {
        try {
            learnedSnapshot = trimLearnedSnapshot(hydrateLearnedSnapshot(JSON.parse(stored)));
        } catch {
            learnedSnapshot = new Map();
        }
    }

    learnedSnapshotLoaded = true;
    void loadLearnedSnapshot();
    return learnedSnapshot;
}

async function loadLearnedSnapshot(): Promise<Map<string, LearnedResolution>> {
    if (learnedLoadPromise) {
        return learnedLoadPromise;
    }

    learnedLoadPromise = (async () => {
        let persisted = new Map<string, LearnedResolution>();

        if (canUsePersistentStorage()) {
            const stored = await storageService.get<Record<string, LearnedResolution>>(LEARNED_RESOLUTIONS_KEY);
            persisted = hydrateLearnedSnapshot(stored);
        }

        if (!persisted.size) {
            const fromLocal = readLocalStorage(LEARNED_RESOLUTIONS_KEY) || readLocalStorage(LEGACY_LEARNED_RESOLUTIONS_KEY);
            if (fromLocal) {
                try {
                    persisted = hydrateLearnedSnapshot(JSON.parse(fromLocal));
                } catch {
                    persisted = new Map();
                }
            }
        }

        learnedSnapshot = trimLearnedSnapshot(persisted);
        learnedSnapshotLoaded = true;

        if (learnedSnapshot.size) {
            const serialized = JSON.stringify(snapshotToObject(learnedSnapshot));
            writeLocalStorage(LEARNED_RESOLUTIONS_KEY, serialized);
            removeLocalStorage(LEGACY_LEARNED_RESOLUTIONS_KEY);
            if (canUsePersistentStorage()) {
                await storageService.set(LEARNED_RESOLUTIONS_KEY, snapshotToObject(learnedSnapshot));
            }
        }

        return learnedSnapshot;
    })().finally(() => {
        learnedLoadPromise = null;
    });

    return learnedLoadPromise;
}

async function persistLearnedSnapshot(): Promise<void> {
    const serialized = JSON.stringify(snapshotToObject(learnedSnapshot));
    writeLocalStorage(LEARNED_RESOLUTIONS_KEY, serialized);
    removeLocalStorage(LEGACY_LEARNED_RESOLUTIONS_KEY);

    if (canUsePersistentStorage()) {
        await storageService.set(LEARNED_RESOLUTIONS_KEY, snapshotToObject(learnedSnapshot));
    }
}

function getLearnedResolution(query: string, brandHint?: string): LearnedResolution | null {
    const snapshot = ensureLearnedSnapshotSync();
    return snapshot.get(buildMemoryKey(query, brandHint)) || null;
}

function classifyCandidateConfidence(
    score: number,
    queryCoverage: number,
    exactNormalized: boolean,
    directPhraseMatch: boolean,
    queryTokenCount: number,
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
    learnedResolution?: LearnedResolution | null,
): SearchFoodCandidate {
    const queryNormalized = normalizeFoodName(normalizeQueryForSearch(query));
    const canonicalId = getCanonicalFoodId(food);
    const matchSurface = normalizeFoodName(buildFoodSearchText(food));
    const foodTokens = getTokens(matchSurface);
    const queryTokens = [...getTokens(queryNormalized)];
    const matchedAlias = (food.searchAliases || []).find(alias => normalizeFoodName(alias) === queryNormalized)
        || (food.searchAliases || []).find(alias => normalizeFoodName(alias).includes(queryNormalized))
        || undefined;
    const overlap = queryTokens.filter(token => foodTokens.has(token) || matchSurface.includes(token));
    const uniqueOverlap = [...new Set(overlap)];
    const exactNormalized = queryNormalized.length > 0
        && (canonicalId === queryNormalized || Boolean(matchedAlias && normalizeFoodName(matchedAlias) === queryNormalized));
    const directPhraseMatch = !exactNormalized
        && queryNormalized.length > 0
        && (
            canonicalId.includes(queryNormalized)
            || queryNormalized.includes(canonicalId)
            || matchSurface.includes(queryNormalized)
            || Boolean(matchedAlias && normalizeFoodName(matchedAlias).includes(queryNormalized))
        );
    const queryCoverage = queryTokens.length > 0 ? uniqueOverlap.length / queryTokens.length : (directPhraseMatch ? 1 : 0);
    const tokenPrecision = foodTokens.size > 0 ? uniqueOverlap.length / foodTokens.size : 0;
    const trace: string[] = [];
    let score = 0;

    if (exactNormalized) {
        score += matchedAlias ? 0.5 : 0.54;
        trace.push(matchedAlias ? 'exact_alias' : 'exact_name');
    } else if (directPhraseMatch) {
        score += matchedAlias ? 0.34 : 0.28;
        trace.push(matchedAlias ? 'alias_phrase_match' : 'phrase_match');
    }

    if (queryCoverage > 0) {
        score += queryCoverage * 0.24;
        trace.push(`coverage:${queryCoverage.toFixed(2)}`);
    }
    if (tokenPrecision > 0) {
        score += tokenPrecision * 0.12;
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
        && (food.id === learnedResolution.foodId || canonicalId === learnedResolution.canonicalId),
    );
    if (learned && learnedResolution) {
        score += 0.32 + Math.min(learnedResolution.count, 3) * 0.02;
        trace.push('learned_resolution');
    }

    if (food.tags?.some(tag => /prepar|chileno|plato|comida/i.test(tag)) && queryTokens.length > 1) {
        score += 0.03;
        trace.push('prepared_food_bias');
    }
    if (food.tags?.length) {
        score += Math.min(food.tags.length, 4) * 0.01;
        trace.push('semantic_tags');
    }
    if (food.micronutrients?.length) {
        score += 0.01;
        trace.push('micronutrients');
    }
    score += getSourcePriority(food.id) * 0.02;
    trace.push(`source:${getSourceKind(food.id)}`);

    score = Math.max(0, Math.min(1, score));
    const confidence = classifyCandidateConfidence(
        score,
        queryCoverage,
        exactNormalized,
        directPhraseMatch,
        queryTokens.length,
    );

    return {
        foodId: food.id,
        displayName: food.name,
        brand: food.brand,
        score: Math.round(score * 1000) / 1000,
        confidence,
        source: getSourceKind(food.id),
        matchedAlias,
        why: trace.join(', '),
        food,
        canonicalId,
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
    allowLowScores = false,
): SearchFoodCandidate[] {
    return items
        .map(item => scoreFoodCandidate(query, item, brandHint, learnedResolution))
        .filter(candidate => allowLowScores || candidate.score >= MIN_CANDIDATE_SCORE)
        .sort((a, b) => (
            b.score !== a.score
                ? b.score - a.score
                : getSourcePriority(b.food.id) - getSourcePriority(a.food.id)
        ))
        .slice(0, EXTENDED_QUERY_LIMIT);
}

function deriveMatchType(candidates: SearchFoodCandidate[]): SearchMatchType {
    const best = candidates[0];
    if (!best) return 'exact';
    if (best.trace.includes('exact_name') || best.trace.includes('exact_alias')) return 'exact';
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
            decisionReason: 'No se encontraron coincidencias locales confiables.',
        };
    }

    const gap = second ? best.score - second.score : best.score;
    if (best.learned && best.score >= 0.74) {
        return {
            bestConfidence: best.confidence,
            canAutoSelect: true,
            decisionReason: 'Coincidencia reforzada por una resolucion aprendida.',
        };
    }
    if (best.score >= HIGH_CONFIDENCE_SCORE && gap >= 0.08) {
        return {
            bestConfidence: best.confidence,
            canAutoSelect: true,
            decisionReason: 'Coincidencia deterministica de alta confianza.',
        };
    }
    if (best.confidence === 'medium') {
        return {
            bestConfidence: best.confidence,
            canAutoSelect: false,
            decisionReason: 'Hay coincidencias plausibles y conviene confirmar una de las sugerencias.',
        };
    }
    return {
        bestConfidence: best.confidence,
        canAutoSelect: false,
        decisionReason: 'La coincidencia es ambigua o de baja confianza.',
    };
}

function getCacheKey(query: string, scope: FoodPackScope, brandHint?: string): string {
    const normalizedQuery = normalizeQueryForSearch(query);
    return `${scope}|${brandHint ? `${normalizedQuery}|${normalizeFoodName(brandHint)}` : normalizedQuery}`;
}

function getPackBytesEstimate(): number {
    const hotBytes = HOT_FOODS.length * 280;
    const extendedBytes = lastExtendedPackReadyAt ? ((offLoadPromise ? 60_000 * 220 : 0) + (usdaLoadPromise ? 18_000 * 180 : 0)) : 0;
    return hotBytes + extendedBytes;
}
