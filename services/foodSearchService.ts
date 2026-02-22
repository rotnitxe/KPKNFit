// services/foodSearchService.ts
// Búsqueda unificada: foodDatabase local + Open Food Facts + USDA FoodData Central

import type { FoodItem, Settings } from '../types';
import { FOOD_DATABASE } from '../data/foodDatabase';

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

function foodItemFromOFF(product: any): FoodItem {
    const nut = product.nutriments || {};
    const kcal100 = nut['energy-kcal_100g'] ?? nut['energy-kcal'] ?? (nut['energy_100g'] ? nut['energy_100g'] / 4.184 : 0);
    const servingSize = 100;
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
    const q = query.toLowerCase().trim();
    if (!q) return [];
    return FOOD_DATABASE.filter(f =>
        f.name.toLowerCase().includes(q) ||
        (f.brand || '').toLowerCase().includes(q)
    ).slice(0, 15);
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
    const q = query.toLowerCase().trim();
    if (!q || products.length === 0) return [];
    const name = (p: any) => (p.product_name || p.product_name_es || '').toLowerCase();
    const matches = products.filter((p: any) => name(p).includes(q));
    return matches.slice(0, 15).map((p: any) => foodItemFromOFF(p));
}

async function searchOpenFoodFacts(query: string): Promise<FoodItem[]> {
    try {
        const url = `https://world.openfoodfacts.org/cgi/search.pl?search_terms=${encodeURIComponent(query)}&search_simple=1&json=1&page_size=10`;
        const res = await fetch(url);
        const data = await res.json();
        const products = data.products || [];
        const items = products
            .filter((p: any) => p.product_name && (p.nutriments?.['energy-kcal_100g'] != null || p.nutriments?.proteins_100g != null))
            .slice(0, 10)
            .map((p: any) => foodItemFromOFF(p));
        if (items.length > 0) return items;
    } catch (_) {
        /* Fallback a offline */
    }
    return searchOFFOffline(query);
}

async function searchUSDA(query: string, apiKey: string): Promise<FoodItem[]> {
    if (apiKey) {
        try {
            const url = `https://api.nal.usda.gov/fdc/v1/foods/search?api_key=${encodeURIComponent(apiKey)}&query=${encodeURIComponent(query)}&pageSize=10`;
            const res = await fetch(url);
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
    const q = query.toLowerCase().trim();
    if (!q) return [];
    const matches = foods.filter((f: any) =>
        (f.description || '').toLowerCase().includes(q)
    );
    return matches.slice(0, 15).map((f: any) => foodItemFromUSDA(f));
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

/** Tokens significativos (palabras > 2 chars) */
function getTokens(normalized: string): Set<string> {
    return new Set(
        normalized
            .split(/\s+/)
            .filter(t => t.length > 2 && !/^\d+$/.test(t))
    );
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
 * Consulta en paralelo: local, Open Food Facts, USDA (prioridad USDA, rellena macros faltantes)
 */
export async function searchFoods(
    query: string,
    settings?: Settings | null
): Promise<FoodItem[]> {
    const q = query.trim();
    if (!q) return [];

    const cache = getCache();
    const cached = cache.get(q.toLowerCase());
    if (cached && Date.now() - cached.ts < CACHE_TTL_MS) {
        return cached.results;
    }

    const usdaKey = settings?.apiKeys?.usda || '';

    const [local, off, usda] = await Promise.all([
        Promise.resolve(searchLocal(q)),
        searchOpenFoodFacts(q),
        searchUSDA(q, usdaKey),
    ]);

    const merged = mergeAndDeduplicate(local, off, usda);

    cache.set(q.toLowerCase(), { query: q, results: merged, ts: Date.now() });
    setCache(cache);

    return merged;
}
