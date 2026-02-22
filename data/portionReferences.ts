// data/portionReferences.ts
// Referencias visuales para porciones (palma de mano, puño, etc.)

import type { PortionReference } from '../types';

export type FoodTypeForPortion = 'protein' | 'carb' | 'fat' | 'mixed';

export interface PortionRefEntry {
    key: PortionReference;
    label: string;
    gramsByFoodType: Record<FoodTypeForPortion, number>;
}

export const PORTION_REFERENCES: PortionRefEntry[] = [
    { key: 'palm', label: 'Una palma de mano', gramsByFoodType: { protein: 85, carb: 75, fat: 20, mixed: 90 } },
    { key: 'fist', label: 'Un puño', gramsByFoodType: { protein: 85, carb: 75, fat: 20, mixed: 100 } },
    { key: 'tablespoon', label: 'Una cucharada', gramsByFoodType: { protein: 15, carb: 12, fat: 15, mixed: 15 } },
    { key: 'cup', label: 'Una taza', gramsByFoodType: { protein: 85, carb: 150, fat: 50, mixed: 120 } },
    { key: 'handful', label: 'Un puñado', gramsByFoodType: { protein: 30, carb: 25, fat: 20, mixed: 35 } },
];

export const OZ_TO_GRAMS = 28.35;

export function getGramsForReference(ref: PortionReference, foodType: FoodTypeForPortion = 'mixed'): number {
    const entry = PORTION_REFERENCES.find(e => e.key === ref);
    return entry?.gramsByFoodType[foodType] ?? 100;
}

export function getFoodTypeFromMacros(protein: number, carbs: number, fats: number): FoodTypeForPortion {
    const total = protein * 4 + carbs * 4 + fats * 9;
    if (total === 0) return 'mixed';
    const pCal = protein * 4 / total;
    const cCal = carbs * 4 / total;
    const fCal = fats * 9 / total;
    if (pCal > 0.4) return 'protein';
    if (cCal > 0.4) return 'carb';
    if (fCal > 0.4) return 'fat';
    return 'mixed';
}
