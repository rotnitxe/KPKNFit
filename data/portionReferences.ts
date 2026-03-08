// data/portionReferences.ts
// Referencias visuales para porciones: cucharada, palma, taza, toque, etc.
// Valores en gramos por tipo de alimento (100g = referencia base para macros).

import type { PortionReference, FoodItem } from '../types';

/** Tipos de alimento para estimar gramos por referencia (densidad distinta) */
export type FoodTypeForPortion =
    | 'protein'
    | 'carb'
    | 'fat'
    | 'sugar'
    | 'puree'
    | 'liquid'
    | 'rehydratable'
    | 'mixed';

export interface PortionRefEntry {
    key: PortionReference;
    label: string;
    description?: string;
    gramsByFoodType: Record<FoodTypeForPortion, number>;
}

export const PORTION_REFERENCES: PortionRefEntry[] = [
    { key: 'palm', label: 'Una palma', description: 'Tamano de la palma de tu mano, sin dedos', gramsByFoodType: { protein: 85, carb: 75, fat: 20, sugar: 25, puree: 80, liquid: 0, rehydratable: 20, mixed: 90 } },
    { key: 'fist', label: 'Un puno', description: 'Tamano de un puno cerrado', gramsByFoodType: { protein: 85, carb: 75, fat: 20, sugar: 30, puree: 100, liquid: 0, rehydratable: 25, mixed: 100 } },
    { key: 'tablespoon', label: 'Una cucharada', description: 'Cucharada sopera colmada', gramsByFoodType: { protein: 15, carb: 12, fat: 14, sugar: 12, puree: 18, liquid: 15, rehydratable: 5, mixed: 15 } },
    { key: 'cup', label: 'Una taza', description: 'Taza estandar de cafe (~240 ml)', gramsByFoodType: { protein: 140, carb: 180, fat: 220, sugar: 200, puree: 240, liquid: 240, rehydratable: 35, mixed: 180 } },
    { key: 'handful', label: 'Un punado', description: 'Lo que cabe en la mano abierta', gramsByFoodType: { protein: 30, carb: 25, fat: 20, sugar: 15, puree: 35, liquid: 0, rehydratable: 15, mixed: 35 } },
    { key: 'pinch', label: 'Un toque de', description: 'Pellizco entre dos dedos (sal, especias)', gramsByFoodType: { protein: 2, carb: 2, fat: 2, sugar: 2, puree: 2, liquid: 1, rehydratable: 2, mixed: 2 } },
    { key: 'teaspoon', label: 'Una cucharadita', description: 'Cucharita de postre o te', gramsByFoodType: { protein: 5, carb: 4, fat: 5, sugar: 4, puree: 6, liquid: 5, rehydratable: 2, mixed: 5 } },
    { key: 'glass', label: 'Un vaso', description: 'Vaso estandar (~200-250 ml)', gramsByFoodType: { protein: 200, carb: 200, fat: 200, sugar: 200, puree: 250, liquid: 250, rehydratable: 40, mixed: 220 } },
    { key: 'slice', label: 'Una rebanada', description: 'Tajada, lamina o rebanada', gramsByFoodType: { protein: 30, carb: 35, fat: 15, sugar: 20, puree: 15, liquid: 0, rehydratable: 0, mixed: 30 } },
    { key: 'can', label: 'Una lata', description: 'Lata estandar / conservas', gramsByFoodType: { protein: 120, carb: 150, fat: 150, sugar: 330, puree: 150, liquid: 330, rehydratable: 100, mixed: 150 } },
    { key: 'portion', label: 'Una porcion', description: 'Equivalente estandar o porcion comercial', gramsByFoodType: { protein: 100, carb: 100, fat: 30, sugar: 30, puree: 50, liquid: 200, rehydratable: 30, mixed: 100 } },
    { key: 'scoop', label: 'Un scoop', description: 'Medida dosificadora estandar (proteina/suplementos)', gramsByFoodType: { protein: 30, carb: 30, fat: 20, sugar: 30, puree: 30, liquid: 20, rehydratable: 15, mixed: 30 } },
];

export const OZ_TO_GRAMS = 28.35;

/** Gramos para una referencia segun tipo de alimento */
export function getGramsForReference(ref: PortionReference, foodType: FoodTypeForPortion = 'mixed'): number {
    const entry = PORTION_REFERENCES.find(item => item.key === ref);
    if (!entry) return 100;

    const direct = entry.gramsByFoodType[foodType];
    if (typeof direct === 'number' && direct > 0) return direct;

    const mixed = entry.gramsByFoodType.mixed;
    return typeof mixed === 'number' && mixed > 0 ? mixed : 100;
}

/** Infiere tipo de alimento desde macros (proteina, carbos, grasas por 100g) */
export function getFoodTypeFromMacros(protein: number, carbs: number, fats: number): FoodTypeForPortion {
    const total = protein * 4 + carbs * 4 + fats * 9;
    if (total === 0) return 'mixed';

    const proteinShare = (protein * 4) / total;
    const carbShare = (carbs * 4) / total;
    const fatShare = (fats * 9) / total;

    if (proteinShare > 0.4) return 'protein';
    if (carbShare > 0.4) return 'carb';
    if (fatShare > 0.4) return 'fat';
    return 'mixed';
}

const SUGAR_KEYWORDS = ['azucar', 'miel', 'edulcorante', 'stevia', 'sacarosa', 'glucosa'];
const CARB_KEYWORDS = ['pan', 'arroz', 'pasta', 'fideo', 'fideos', 'tallarines', 'tortilla', 'avena', 'granola', 'cereal', 'quinoa', 'cuscus', 'papas', 'papa', 'batata', 'camote'];
const PUREE_KEYWORDS = ['pure', 'crema', 'salsa', 'humus', 'guacamole', 'yogur', 'yogurt', 'mantequilla de', 'dip'];
const LIQUID_KEYWORDS = ['leche', 'jugo', 'agua', 'cafe', 'te', 'bebida', 'smoothie', 'batido', 'caldo', 'sopa liquida'];
const FAT_KEYWORDS = ['aceite', 'mantequilla', 'manteca', 'margarina', 'mayonesa', 'crema de leche', 'nata'];
const REHYDRATABLE_KEYWORDS = ['soya texturizada', 'soja texturizada', 'proteina de soya', 'carne de soya', 'tvp', 'textured vegetable'];

function normalizeFoodNameForKeywordMatch(name: string): string {
    return name
        .normalize('NFD')
        .replace(/\p{Diacritic}/gu, '')
        .toLowerCase()
        .replace(/[()/,.-]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

function escapeRegex(text: string): string {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function hasKeyword(name: string, keywords: string[]): boolean {
    return keywords.some(keyword => {
        const normalizedKeyword = normalizeFoodNameForKeywordMatch(keyword);
        const pattern = new RegExp(`(?:^|\\s)${escapeRegex(normalizedKeyword)}(?:$|\\s)`, 'i');
        return pattern.test(name);
    });
}

/** Infiere tipo de alimento desde nombre + macros (prioridad: nombre si hay match) */
export function getFoodTypeForPortion(food: FoodItem): FoodTypeForPortion {
    const name = normalizeFoodNameForKeywordMatch(food.name || '');

    if (hasKeyword(name, REHYDRATABLE_KEYWORDS)) return 'rehydratable';
    if (hasKeyword(name, SUGAR_KEYWORDS)) return 'sugar';
    if (hasKeyword(name, FAT_KEYWORDS)) return 'fat';
    if (hasKeyword(name, LIQUID_KEYWORDS)) return 'liquid';
    if (hasKeyword(name, PUREE_KEYWORDS)) return 'puree';
    if (hasKeyword(name, CARB_KEYWORDS)) return 'carb';

    return getFoodTypeFromMacros(food.protein, food.carbs, food.fats);
}
