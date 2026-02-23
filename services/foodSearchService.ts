// services/foodSearchService.ts
// Búsqueda unificada: foodDatabase local + Open Food Facts + USDA FoodData Central

import type { FoodItem, Settings } from '../types';
import { FOOD_DATABASE } from '../data/foodDatabase';

const STOPWORDS = new Set(['de', 'y', 'la', 'el', 'en', 'a', 'al', 'del', 'los', 'las', 'un', 'una', 'por', 'para']);
const FUZZY_THRESHOLD = 0.55;
const API_TIMEOUT_MS = 4000;

/** fetch con timeout: evita esperar indefinidamente a APIs lentas */
async function fetchWithTimeout(url: string, timeoutMs = API_TIMEOUT_MS): Promise<Response> {
    const ctrl = new AbortController();
    const id = setTimeout(() => ctrl.abort(), timeoutMs);
    try {
        const res = await fetch(url, { signal: ctrl.signal });
        clearTimeout(id);
        return res;
    } catch (e) {
        clearTimeout(id);
        throw e;
    }
}

const CACHE_KEY = 'kpkn-food-search-cache';
const CACHE_MAX_ENTRIES = 100;
const CACHE_TTL_MS = 24 * 60 * 60 * 1000;

interface CacheEntry {
    query: string;
    results: FoodItem[];
    ts: number;
}

function getCache(): Map<string, CacheEntry> {
    try {
        const raw = localStorage.getItem(CACHE_KEY);
        if (raw) {
            const arr = JSON.parse(raw) as [string, CacheEntry][];
            const map = new Map(arr);
            const now = Date.now();
            for (const [k, v] of map) {
                if (now - v.ts > CACHE_TTL_MS) map.delete(k);
            }
            return map;
        }
    } catch (_) {}
    return new Map();
}

function setCache(map: Map<string, CacheEntry>) {
    try {
        const arr = Array.from(map.entries()).slice(-CACHE_MAX_ENTRIES);
        localStorage.setItem(CACHE_KEY, JSON.stringify(arr));
    } catch (_) {}
}

const OFF_MICRO_MAP: Record<string, { name: string; unit: string }> = {
    'iron_100g': { name: 'Hierro', unit: 'mg' },
    'calcium_100g': { name: 'Calcio', unit: 'mg' },
    'sodium_100g': { name: 'Sodio', unit: 'mg' },
    'vitamin-a_100g': { name: 'Vitamina A', unit: 'µg' },
    'vitamin-c_100g': { name: 'Vitamina C', unit: 'mg' },
    'vitamin-d_100g': { name: 'Vitamina D', unit: 'µg' },
    'vitamin-b12_100g': { name: 'Vitamina B12', unit: 'µg' },
    'magnesium_100g': { name: 'Magnesio', unit: 'mg' },
    'zinc_100g': { name: 'Zinc', unit: 'mg' },
    'potassium_100g': { name: 'Potasio', unit: 'mg' },
};

function foodItemFromOFF(product: any): FoodItem {
    const nut = product.nutriments || {};
    const kcal100 = nut['energy-kcal_100g'] ?? nut['energy-kcal'] ?? (nut['energy_100g'] ? nut['energy_100g'] / 4.184 : 0);
    const servingSize = 100;
    const micronutrients: { name: string; amount: number; unit: string }[] = [];
    for (const [key, meta] of Object.entries(OFF_MICRO_MAP)) {
        const val = nut[key];
        if (val != null && typeof val === 'number' && val > 0) {
            micronutrients.push({ name: meta.name, amount: Math.round(val * 10) / 10, unit: meta.unit });
        }
    }
    return {
        id: `off-${product.code || product.id || crypto.randomUUID()}`,
        name: product.product_name || product.product_name_es || 'Sin nombre',
        brand: product.brands,
        servingSize,
        servingUnit: 'g',
        unit: 'g',
        calories: Math.round(kcal100) || 0,
        protein: Math.round((nut['proteins_100g'] ?? nut.proteins ?? 0) * 10) / 10,
        carbs: Math.round((nut['carbohydrates_100g'] ?? nut.carbohydrates ?? 0) * 10) / 10,
        fats: Math.round((nut['fat_100g'] ?? nut.fat ?? 0) * 10) / 10,
        isCustom: false,
        image: product.image_small_url || product.image_front_small_url,
        fatBreakdown: (nut['saturated-fat_100g'] != null) ? {
            saturated: nut['saturated-fat_100g'],
            monounsaturated: nut['monounsaturated-fat_100g'] ?? 0,
            polyunsaturated: nut['polyunsaturated-fat_100g'] ?? 0,
            trans: nut['trans-fat_100g'] ?? 0,
        } : undefined,
        micronutrients: micronutrients.length > 0 ? micronutrients : undefined,
    };
}

function foodItemFromUSDA(food: any): FoodItem {
    // Soporta API (nutrientName, value) y Foundation Foods JSON (nutrient.name, amount/median)
    const nutrients = (food.foodNutrients || []).reduce((acc: Record<string, number>, n: any) => {
        const name = ((n.nutrientName || n.nutrient?.name) || '').toLowerCase();
        const unit = (n.nutrient?.unitName || '').toLowerCase();
        const val = n.value ?? n.amount ?? n.median ?? 0;
        if (name.includes('energy') && (unit.includes('kcal') || !unit.includes('kj'))) acc.calories = val;
        else if (name.includes('energy') && !acc.calories) acc.calories = val;
        if (name.includes('protein')) acc.protein = val;
        if (name.includes('carbohydrate') && !name.includes('fiber')) acc.carbs = val;
        if (name.includes('total lipid') || (name === 'fat')) acc.fats = val;
        if (name.includes('fatty acids, total saturated')) acc.saturated = val;
        if (name.includes('fatty acids, total trans')) acc.trans = val;
        return acc;
    }, { calories: 0, protein: 0, carbs: 0, fats: 0, saturated: 0, trans: 0 } as Record<string, number>);
    const serving = 100;
    const item: FoodItem = {
        id: `usda-${food.fdcId || food.id || crypto.randomUUID()}`,
        name: food.description || food.foodDescription || 'Sin nombre',
        brand: food.brandOwner,
        servingSize: serving,
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
    return item;
}

function searchLocal(query: string): FoodItem[] {
    const q = normalizeQueryForSearch(query);
    if (!q) return [];
    if (!hasSignificantQuery(q)) return [];
    const searchTerms = getSignificantQueryTokens(q).join(' ') || q;
    let results = FOOD_DATABASE.filter(f => {
        const name = normalizeFoodNameForMatch(f.name);
        const brand = (f.brand || '').toLowerCase();
        return searchTerms.split(' ').some(term => name.includes(term) || brand.includes(term));
    }).slice(0, 20);
    if (results.length === 0) {
        results = FOOD_DATABASE.map(f => ({ item: f, score: fuzzyScore(q, f.name) }))
            .filter(x => x.score >= FUZZY_THRESHOLD)
            .sort((a, b) => b.score - a.score)
            .slice(0, 20)
            .map(x => x.item);
    }
    return results;
}

const OFF_OFFLINE_URL = '/data/openFoodFactsOffline.json';
let offOfflineCache: any[] | null = null;

async function loadOFFOffline(): Promise<any[]> {
    if (offOfflineCache) return offOfflineCache;
    const base = typeof window !== 'undefined' && window.location?.origin ? window.location.origin : '';
    try {
        const res = await fetch(base + OFF_OFFLINE_URL);
        if (!res.ok) return [];
        const data = await res.json();
        const arr = Array.isArray(data) ? data : (data?.products || []);
        if (arr.length > 0) offOfflineCache = arr;
        return offOfflineCache ?? [];
    } catch (_) {
        return [];
    }
}

async function searchOFFOffline(query: string): Promise<FoodItem[]> {
    const products = await loadOFFOffline();
    const q = normalizeQueryForSearch(query);
    if (!q || products.length === 0) return [];
    if (!hasSignificantQuery(q)) return [];
    const terms = getSignificantQueryTokens(q);
    const searchTerms = terms.length > 0 ? terms : [q];
    const name = (p: any) => normalizeFoodNameForMatch(p.product_name || p.product_name_es || '');
    let matches = products.filter((p: any) =>
        searchTerms.some(term => name(p).includes(term))
    );
    if (matches.length === 0) {
        matches = products
            .map((p: any) => ({ p, score: fuzzyScore(q, name(p)) }))
            .filter(x => x.score >= FUZZY_THRESHOLD)
            .sort((a, b) => b.score - a.score)
            .slice(0, 15)
            .map(x => x.p);
    }
    return matches.slice(0, 15).map((p: any) => foodItemFromOFF(p));
}

async function searchOpenFoodFacts(query: string): Promise<FoodItem[]> {
    if (!hasSignificantQuery(query)) return [];
    const searchTerms = getSignificantQueryTokens(query).join(' ') || query.trim();
    if (!searchTerms) return [];
    try {
        const url = `https://world.openfoodfacts.org/cgi/search.pl?search_terms=${encodeURIComponent(searchTerms)}&search_simple=1&json=1&page_size=10`;
        const res = await fetchWithTimeout(url);
        const data = await res.json();
        const products = data.products || [];
        const items = products
            .filter((p: any) => p.product_name && (p.nutriments?.['energy-kcal_100g'] != null || p.nutriments?.proteins_100g != null))
            .map((p: any) => foodItemFromOFF(p))
            .filter((f: FoodItem) => fuzzyScore(query, f.name) >= FUZZY_THRESHOLD || getSignificantQueryTokens(query).some(t => normalizeFoodName(f.name).includes(t)));
        if (items.length > 0) return items.slice(0, 10);
    } catch (_) {
        /* Fallback a offline */
    }
    return searchOFFOffline(query);
}

async function searchUSDA(query: string, apiKey: string): Promise<FoodItem[]> {
    if (apiKey) {
        try {
            const url = `https://api.nal.usda.gov/fdc/v1/foods/search?api_key=${encodeURIComponent(apiKey)}&query=${encodeURIComponent(query)}&pageSize=10`;
            const res = await fetchWithTimeout(url);
            const data = await res.json();
            const foods = data.foods || [];
            if (foods.length > 0) return foods.slice(0, 10).map((f: any) => foodItemFromUSDA(f));
        } catch (_) {
            /* Fallback a offline */
        }
    }
    return searchUSDAOffline(query);
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
            const res = await fetch(base + url);
            if (!res.ok) continue;
            const data = await res.json();
            const arr = parseUSDAOfflineData(data);
            if (arr.length > 0) {
                usdaOfflineCache = arr;
                return usdaOfflineCache;
            }
        } catch (_) {
            /* intentar siguiente URL */
        }
    }
    usdaOfflineCache = [];
    return [];
}

async function searchUSDAOffline(query: string): Promise<FoodItem[]> {
    const foods = await loadUSDAOffline();
    const q = normalizeQueryForSearch(query);
    if (!q) return [];
    if (!hasSignificantQuery(q)) return [];
    const terms = getSignificantQueryTokens(q);
    const searchTerms = terms.length > 0 ? terms : [q];
    const descNorm = (f: any) => normalizeFoodNameForMatch(f.description || '');
    let matches = foods.filter((f: any) =>
        searchTerms.some(term => descNorm(f).includes(term))
    );
    if (matches.length === 0) {
        matches = foods
            .map((f: any) => ({ f, score: fuzzyScore(q, f.description || '') }))
            .filter((x: { f: any; score: number }) => x.score >= FUZZY_THRESHOLD)
            .sort((a: { score: number }, b: { score: number }) => b.score - a.score)
            .slice(0, 15)
            .map((x: { f: any }) => x.f);
    }
    return matches.slice(0, 15).map((f: any) => foodItemFromUSDA(f));
}

/** Normaliza query para búsqueda: "con" -> "c/" para coincidir con nombres en DB */
function normalizeQueryForSearch(query: string): string {
    return query
        .trim()
        .replace(/\s+con\s+/gi, ' c/ ')
        .replace(/\s{2,}/g, ' ')
        .trim()
        .toLowerCase();
}

/** Normaliza nombre de alimento para matching: "con" en DB se guarda como "c/" */
function normalizeFoodNameForMatch(name: string): string {
    return (name || '')
        .replace(/\s+con\s+/gi, ' c/ ')
        .toLowerCase();
}

/** Normaliza nombre para comparación: sin acentos, minúsculas, sin (cocido)/(crudo), sin marcas */
function normalizeFoodName(name: string): string {
    return name
        .normalize('NFD')
        .replace(/\p{Diacritic}/gu, '')
        .toLowerCase()
        .replace(/\s*\([^)]*\)\s*/g, ' ')
        .replace(/\b(genérico|cocido|crudo|raw|cooked)\b/gi, '')
        .replace(/\s+/g, ' ')
        .trim();
}

/** Tokens significativos (palabras > 2 chars, excluyendo stopwords) */
function getTokens(normalized: string): Set<string> {
    return new Set(
        normalized
            .split(/\s+/)
            .filter(t => t.length > 2 && !/^\d+$/.test(t) && !STOPWORDS.has(t))
    );
}

/** Query sanitizado: sin stopwords, requiere al menos un token significativo */
function getSignificantQueryTokens(query: string): string[] {
    const normalized = query
        .normalize('NFD')
        .replace(/\p{Diacritic}/gu, '')
        .toLowerCase()
        .trim();
    const tokens = normalized
        .split(/\s+/)
        .filter(t => t.length > 2 && !/^\d+$/.test(t) && !STOPWORDS.has(t));
    return tokens;
}

/** Si el query solo tiene stopwords o es muy corto, no buscar */
function hasSignificantQuery(query: string): boolean {
    const tokens = getSignificantQueryTokens(query);
    const trimmed = query.trim();
    return tokens.length > 0 || (trimmed.length >= 3 && !STOPWORDS.has(trimmed.toLowerCase()));
}

/** Score de similitud fuzzy (0-1): Jaccard + bonus por prefijo */
function fuzzyScore(query: string, foodName: string): number {
    const qNorm = normalizeFoodName(normalizeQueryForSearch(query));
    const fNorm = normalizeFoodName(normalizeFoodNameForMatch(foodName));
    if (!qNorm || !fNorm) return 0;
    const qTokens = getTokens(qNorm);
    const fTokens = getTokens(fNorm);
    if (qTokens.size === 0) return fNorm.includes(qNorm) ? 0.9 : 0;
    const overlap = [...qTokens].filter(t => fTokens.has(t)).length;
    const union = new Set([...qTokens, ...fTokens]).size;
    const jaccard = union > 0 ? overlap / union : 0;
    const coverage = overlap / qTokens.size;
    const score = (jaccard * 0.5 + coverage * 0.5);
    if (fNorm.includes(qNorm) || qNorm.includes(fNorm)) return Math.max(score, 0.85);
    return score;
}

/** Filtra resultados: descarta solo los que no comparten ningún token con el query */
function filterByRelevance(items: FoodItem[], query: string): FoodItem[] {
    const qTokens = getSignificantQueryTokens(query);
    if (qTokens.length === 0) return items;
    return items.filter(f => {
        const fNorm = normalizeFoodName(normalizeFoodNameForMatch(f.name));
        const fTokens = getTokens(fNorm);
        return qTokens.some(t => fTokens.has(t) || fNorm.includes(t));
    });
}

/** Prioridad de fuente: USDA > local > OFF */
function getSourcePriority(id: string): number {
    if (id.startsWith('usda-')) return 3;
    if (id.startsWith('gen') || id.startsWith('cl') || id.startsWith('int') || id.startsWith('meal')) return 2;
    if (id.startsWith('off-')) return 1;
    return 2;
}

/** Comprueba si dos alimentos son similares (mismo concepto) */
function areSimilarFoods(a: FoodItem, b: FoodItem): boolean {
    const na = normalizeFoodName(a.name);
    const nb = normalizeFoodName(b.name);
    if (na === nb) return true;
    const ta = getTokens(na);
    const tb = getTokens(nb);
    if (ta.size === 0 || tb.size === 0) return false;
    const overlap = [...ta].filter(t => tb.has(t)).length;
    const minSize = Math.min(ta.size, tb.size);
    return overlap >= Math.ceil(minSize * 0.7);
}

/** Fusiona nutrientes: usa base, rellena con fallback si base es 0 o undefined */
function mergeNutrients(base: FoodItem, fallback: FoodItem): FoodItem {
    const out = { ...base };
    if ((!out.calories || out.calories === 0) && fallback.calories) out.calories = fallback.calories;
    if ((!out.protein || out.protein === 0) && fallback.protein) out.protein = fallback.protein;
    if ((!out.carbs || out.carbs === 0) && fallback.carbs) out.carbs = fallback.carbs;
    if ((!out.fats || out.fats === 0) && fallback.fats) out.fats = fallback.fats;
    if (!out.fatBreakdown && fallback.fatBreakdown) out.fatBreakdown = fallback.fatBreakdown;
    if (!out.micronutrients?.length && fallback.micronutrients?.length) out.micronutrients = fallback.micronutrients;
    if (!out.carbBreakdown && fallback.carbBreakdown) out.carbBreakdown = fallback.carbBreakdown;
    return out;
}

/** Agrupa por similitud, prioriza USDA, rellena macros faltantes */
function mergeAndDeduplicate(local: FoodItem[], off: FoodItem[], usda: FoodItem[]): FoodItem[] {
    const all = [...usda, ...local, ...off];
    const result: FoodItem[] = [];
    const used = new Set<string>();

    for (const item of all) {
        if (used.has(item.id)) continue;
        const group = all.filter(o => !used.has(o.id) && areSimilarFoods(item, o));
        const best = group.sort((a, b) => getSourcePriority(b.id) - getSourcePriority(a.id))[0];
        let merged = { ...best };
        for (const other of group) {
            if (other.id !== best.id) merged = mergeNutrients(merged, other);
        }
        result.push(merged);
        for (const g of group) used.add(g.id);
    }

    return result.slice(0, 20);
}

/**
 * Búsqueda unificada de alimentos
 * PRIORIDAD OFFLINE: usa bases locales primero (instantáneo). Solo si no hay resultados y hay conexión, consulta APIs online.
 */
export async function searchFoods(
    query: string,
    settings?: Settings | null
): Promise<FoodItem[]> {
    const q = normalizeQueryForSearch(query);
    if (!q) return [];
    if (!hasSignificantQuery(q)) return [];

    const cache = getCache();
    const cached = cache.get(q);
    if (cached && Date.now() - cached.ts < CACHE_TTL_MS) {
        return cached.results;
    }

    const usdaKey = settings?.apiKeys?.usda || '';
    const local = searchLocal(q);

    if (q.length < 4) {
        const merged = filterByRelevance(mergeAndDeduplicate(local, [], []), q);
        if (merged.length > 0) {
            cache.set(q, { query: query.trim(), results: merged.slice(0, 15), ts: Date.now() });
            setCache(cache);
            return merged.slice(0, 15);
        }
    }

    // 1. OFFLINE PRIMERO: bases integradas (instantáneo, sin red)
    const [offOffline, usdaOffline] = await Promise.all([
        searchOFFOffline(q),
        searchUSDAOffline(q),
    ]);

    merged = filterByRelevance(merged, q);

    // 2. Solo si hay pocos resultados Y hay conexión: consultar APIs online (más lento)
    const MIN_OFFLINE_RESULTS = 5;
    if (merged.length < MIN_OFFLINE_RESULTS && typeof navigator !== 'undefined' && navigator.onLine) {
        try {
            const [offOnline, usdaOnline] = await Promise.all([
                searchOpenFoodFacts(q),
                searchUSDA(q, usdaKey),
            ]);
            merged = mergeAndDeduplicate(local, [...offOffline, ...offOnline], [...usdaOffline, ...usdaOnline]);
            merged = filterByRelevance(merged, q);
        } catch (_) {
            /* Mantener resultados offline */
        }
    }

    if (merged.length === 0) {
        const allSources = [...local, ...offOffline, ...usdaOffline];
        const fuzzyCandidates = allSources
            .map(f => ({ item: f, score: fuzzyScore(q, f.name) }))
            .filter(x => x.score >= FUZZY_THRESHOLD)
            .sort((a, b) => b.score - a.score)
            .slice(0, 20)
            .map(x => x.item);
        merged = mergeAndDeduplicate(fuzzyCandidates, [], []);
    }
    if (merged.length === 0 && (local.length > 0 || offOffline.length > 0 || usdaOffline.length > 0)) {
        const allSources = [...local, ...offOffline, ...usdaOffline];
        const best = allSources
            .map(f => ({ item: f, score: fuzzyScore(q, f.name) }))
            .sort((a, b) => b.score - a.score)[0];
        if (best && best.score > 0.3) merged = [best.item];
    }

    cache.set(q, { query: query.trim(), results: merged, ts: Date.now() });
    setCache(cache);

    return merged;
}

/** Preload de bases offline en segundo plano para primera búsqueda instantánea */
if (typeof window !== 'undefined') {
    loadOFFOffline().catch(() => {});
    loadUSDAOffline().catch(() => {});
}
