// apps/mobile/src/services/foodIndexService.ts
// Motor de Búsqueda y Resolución de Alimentos — Ported from PWA
import { FOOD_DATABASE } from '../data/foodDatabase';
import { LOCAL_CHILEAN_FOOD_CATALOG } from '../data/localChileanFoods';
import { enrichFoodItem, buildFoodSearchText, normalizeFoodText } from '../data/foodTaxonomy';

const HOT_FOODS = [...FOOD_DATABASE, ...LOCAL_CHILEAN_FOOD_CATALOG].map(enrichFoodItem);

export interface SearchFoodsResult {
    results: any[];
    matchType: 'exact' | 'fuzzy';
    candidates: any[];
    bestConfidence: 'high' | 'medium' | 'low';
    canAutoSelect: boolean;
}

export async function searchFoodIndex(query: string, options: any = {}): Promise<SearchFoodsResult> {
    const normalizedQuery = normalizeFoodText(query);
    if (!normalizedQuery) return { results: [], matchType: 'exact', candidates: [], bestConfidence: 'low', canAutoSelect: false };

    // Búsqueda simplificada 1:1 lógica de ranking
    const scored = HOT_FOODS.map(food => {
        const surface = normalizeFoodText(buildFoodSearchText(food));
        let score = 0;
        if (surface === normalizedQuery) score = 1.0;
        else if (surface.includes(normalizedQuery)) score = 0.8;
        else {
            const queryTokens = normalizedQuery.split(' ');
            const matchedTokens = queryTokens.filter(t => surface.includes(t)).length;
            score = matchedTokens / queryTokens.length;
        }
        return { food, score };
    })
    .filter(item => item.score > 0.2)
    .sort((a, b) => b.score - a.score);

    const candidates = scored.slice(0, options.limit || 12);

    return {
        results: candidates.map(c => c.food),
        candidates: candidates,
        matchType: candidates[0]?.score === 1.0 ? 'exact' : 'fuzzy',
        bestConfidence: candidates[0]?.score > 0.8 ? 'high' : 'medium',
        canAutoSelect: candidates[0]?.score > 0.9,
    };
}

export async function warmFoodIndex(): Promise<void> {
    // In mobile, we just ensure HOT_FOODS is evaluated
    console.log('[FoodIndex] Warmed up', HOT_FOODS.length, 'foods');
}
