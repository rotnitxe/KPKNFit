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

async function searchOpenFoodFacts(query: string): Promise<FoodItem[]> {
    try {
        const url = `https://world.openfoodfacts.org/cgi/search.pl?search_terms=${encodeURIComponent(query)}&search_simple=1&json=1&page_size=10`;
        const res = await fetch(url);
        const data = await res.json();
        const products = data.products || [];
        return products
            .filter((p: any) => p.product_name && (p.nutriments?.['energy-kcal_100g'] != null || p.nutriments?.proteins_100g != null))
            .slice(0, 10)
            .map((p: any) => foodItemFromOFF(p));
    } catch (_) {
        return [];
    }
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

const USDA_OFFLINE_URL = '/data/usdaFoundationFoods.json';
let usdaOfflineCache: any[] | null = null;

async function loadUSDAOffline(): Promise<any[]> {
    if (usdaOfflineCache) return usdaOfflineCache;
    try {
        const base = typeof window !== 'undefined' && window.location?.origin ? window.location.origin : '';
        const res = await fetch(base + USDA_OFFLINE_URL);
        const data = await res.json();
        usdaOfflineCache = data.FoundationFoods || [];
        return usdaOfflineCache;
    } catch (_) {
        usdaOfflineCache = [];
        return [];
    }
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

function deduplicateBySimilarName(items: FoodItem[]): FoodItem[] {
    const seen = new Set<string>();
    return items.filter(f => {
        const key = f.name.toLowerCase().replace(/\s+/g, ' ').trim();
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}

/**
 * Búsqueda unificada de alimentos
 * Consulta en paralelo: local, Open Food Facts, USDA (si hay API key)
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

    const merged = [...local, ...off, ...usda];
    const deduped = deduplicateBySimilarName(merged);

    cache.set(q.toLowerCase(), { query: q, results: deduped, ts: Date.now() });
    setCache(cache);

    return deduped;
}
