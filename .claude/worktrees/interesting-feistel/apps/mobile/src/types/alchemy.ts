// apps/mobile/src/types/alchemy.ts
import type { PortionPreset } from '@kpkn/shared-types';

export const PORTION_MULTIPLIERS: Record<PortionPreset, number> = {
    small: 0.5,
    medium: 1.0,
    large: 1.5,
    extra: 2.0,
};

export interface TagWithFood {
    id: string;
    foodId?: string;
    amountGrams?: number;
    portion?: PortionPreset | string;
    quantity: number;
    cookingMethod?: string;
    preparationModifiers?: string[];
    anatomicalModifiers?: string[];
    stateModifiers?: string[];
    heuristicModifiers?: string[];
    compositionModifiers?: string[];
    macroOverrides?: {
        calories?: number;
        protein?: number;
        carbs?: number;
        fats?: number;
    };
}
