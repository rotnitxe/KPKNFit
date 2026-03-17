// apps/mobile/src/services/foodSearchService.ts
// Fachada de búsqueda de alimentos — Ported from PWA
import { searchFoodIndex, warmFoodIndex } from './foodIndexService';

export async function searchFoods(query: string, settings?: any, brandHint?: string) {
    return searchFoodIndex(query, { settings, brandHint });
}

export async function preloadFoodDatabases() {
    return warmFoodIndex();
}
