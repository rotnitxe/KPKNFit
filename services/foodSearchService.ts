import type { FoodItem, Settings } from '../types';
import {
    getCanonicalFoodId,
    getFoodPackStatus,
    getFoodResolutionMemoryHints,
    rememberFoodResolution,
    searchFoodIndex,
    searchFoodIndexExtended,
    warmFoodIndex,
    type FoodPackStatus,
    type SearchConfidence,
    type SearchFoodCandidate,
    type SearchFoodsResult,
    type SearchMatchType,
    type SearchSource,
} from './foodIndexService';

export type {
    FoodPackStatus,
    SearchConfidence,
    SearchFoodCandidate,
    SearchFoodsResult,
    SearchMatchType,
    SearchSource,
};

export { getCanonicalFoodId, getFoodPackStatus, getFoodResolutionMemoryHints, rememberFoodResolution };

export async function searchFoods(
    query: string,
    settings?: Settings | null,
    brandHint?: string,
): Promise<SearchFoodsResult> {
    return searchFoodIndex(query, { scope: 'hot', settings, brandHint });
}

export async function searchFoodsExtended(
    query: string,
    settings?: Settings | null,
    brandHint?: string,
): Promise<SearchFoodsResult> {
    return searchFoodIndexExtended(query, { settings, brandHint });
}

export function preloadFoodDatabases(scope: 'hot' | 'extended' = 'extended'): void {
    void warmFoodIndex(scope);
}
