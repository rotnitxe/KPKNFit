// apps/mobile/src/data/portionReferences.ts
// Referencias visuales para porciones - Ported from PWA

export type PortionReference = 'palm' | 'fist' | 'tablespoon' | 'cup' | 'handful' | 'pinch' | 'teaspoon' | 'glass' | 'slice' | 'can' | 'portion' | 'scoop';

export type FoodTypeForPortion = 'protein' | 'carb' | 'fat' | 'sugar' | 'puree' | 'liquid' | 'rehydratable' | 'mixed';

export interface PortionRefEntry {
    key: PortionReference;
    label: string;
    description?: string;
    gramsByFoodType: Record<FoodTypeForPortion, number>;
}

export const PORTION_REFERENCES: PortionRefEntry[] = [
    { key: 'palm', label: 'Una palma', gramsByFoodType: { protein: 85, carb: 75, fat: 20, sugar: 25, puree: 80, liquid: 0, rehydratable: 20, mixed: 90 } },
    { key: 'fist', label: 'Un puño', gramsByFoodType: { protein: 85, carb: 75, fat: 20, sugar: 30, puree: 100, liquid: 0, rehydratable: 25, mixed: 100 } },
    { key: 'tablespoon', label: 'Una cucharada', gramsByFoodType: { protein: 15, carb: 12, fat: 14, sugar: 12, puree: 18, liquid: 15, rehydratable: 5, mixed: 15 } },
    { key: 'cup', label: 'Una taza', gramsByFoodType: { protein: 140, carb: 180, fat: 220, sugar: 200, puree: 240, liquid: 240, rehydratable: 35, mixed: 180 } },
    { key: 'handful', label: 'Un puñado', gramsByFoodType: { protein: 30, carb: 25, fat: 20, sugar: 15, puree: 35, liquid: 0, rehydratable: 15, mixed: 35 } },
    { key: 'pinch', label: 'Un toque de', gramsByFoodType: { protein: 2, carb: 2, fat: 2, sugar: 2, puree: 2, liquid: 1, rehydratable: 2, mixed: 2 } },
    { key: 'teaspoon', label: 'Una cucharadita', gramsByFoodType: { protein: 5, carb: 4, fat: 5, sugar: 4, puree: 6, liquid: 5, rehydratable: 2, mixed: 5 } },
    { key: 'glass', label: 'Un vaso', gramsByFoodType: { protein: 200, carb: 200, fat: 200, sugar: 200, puree: 250, liquid: 250, rehydratable: 40, mixed: 220 } },
    { key: 'slice', label: 'Una rebanada', gramsByFoodType: { protein: 30, carb: 35, fat: 15, sugar: 20, puree: 15, liquid: 0, rehydratable: 0, mixed: 30 } },
    { key: 'can', label: 'Una lata', gramsByFoodType: { protein: 120, carb: 150, fat: 150, sugar: 330, puree: 150, liquid: 330, rehydratable: 100, mixed: 150 } },
    { key: 'portion', label: 'Una porción', gramsByFoodType: { protein: 100, carb: 100, fat: 30, sugar: 30, puree: 50, liquid: 200, rehydratable: 30, mixed: 100 } },
    { key: 'scoop', label: 'Un scoop', gramsByFoodType: { protein: 30, carb: 30, fat: 20, sugar: 30, puree: 30, liquid: 20, rehydratable: 15, mixed: 30 } },
];

export function getGramsForReference(ref: PortionReference, foodType: FoodTypeForPortion = 'mixed'): number {
    const entry = PORTION_REFERENCES.find(item => item.key === ref);
    if (!entry) return 100;
    return entry.gramsByFoodType[foodType] || entry.gramsByFoodType.mixed || 100;
}

export function getFoodTypeForPortion(food: any): FoodTypeForPortion {
    // Simplified version for mobile context if full FoodDatabase is not available yet
    return 'mixed';
}
