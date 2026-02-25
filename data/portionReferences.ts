// data/portionReferences.ts
// Referencias visuales para porciones: cucharada, palma, taza, toque, etc.
// Valores en gramos por tipo de alimento (100g = referencia base para macros).

import type { PortionReference } from '../types';
import type { FoodItem } from '../types';

/** Tipos de alimento para estimar gramos por referencia (densidad distinta) */
export type FoodTypeForPortion =
    | 'protein'   // carnes, pescado, huevos
    | 'carb'     // arroz, pasta, pan
    | 'fat'      // aceites, mantequilla, nueces
    | 'sugar'    // azúcar, miel (más denso en cucharada)
    | 'puree'    // puré, cremas, yogur espeso
    | 'liquid'   // líquidos (leche, jugo)
    | 'rehydratable'  // soya texturizada, legumbres secas (gramos en seco; 1 palma cocida ≈ 20g seco)
    | 'mixed';   // mixto o desconocido

export interface PortionRefEntry {
    key: PortionReference;
    label: string;
    /** Descripción corta para contexto visual */
    description?: string;
    /** Gramos por tipo de alimento (referencia respecto a 100g). rehydratable = gramos en seco. */
    gramsByFoodType: Record<FoodTypeForPortion, number>;
}

export const PORTION_REFERENCES: PortionRefEntry[] = [
    { key: 'palm', label: 'Una palma', description: 'Tamaño de la palma de tu mano, sin dedos', gramsByFoodType: { protein: 85, carb: 75, fat: 20, sugar: 25, puree: 80, liquid: 0, rehydratable: 20, mixed: 90 } },
    { key: 'fist', label: 'Un puño', description: 'Tamaño de un puño cerrado', gramsByFoodType: { protein: 85, carb: 75, fat: 20, sugar: 30, puree: 100, liquid: 0, rehydratable: 25, mixed: 100 } },
    { key: 'tablespoon', label: 'Una cucharada', description: 'Cucharada sopera colmada', gramsByFoodType: { protein: 15, carb: 12, fat: 14, sugar: 12, puree: 18, liquid: 15, rehydratable: 5, mixed: 15 } },
    { key: 'cup', label: 'Una taza', description: 'Taza estándar de café (~240 ml)', gramsByFoodType: { protein: 140, carb: 180, fat: 220, sugar: 200, puree: 240, liquid: 240, rehydratable: 35, mixed: 180 } },
    { key: 'handful', label: 'Un puñado', description: 'Lo que cabe en la mano abierta', gramsByFoodType: { protein: 30, carb: 25, fat: 20, sugar: 15, puree: 35, liquid: 0, rehydratable: 15, mixed: 35 } },
    { key: 'pinch', label: 'Un toque de', description: 'Pellizco entre dos dedos (sal, especias)', gramsByFoodType: { protein: 2, carb: 2, fat: 2, sugar: 2, puree: 2, liquid: 1, rehydratable: 2, mixed: 2 } },
];

export const OZ_TO_GRAMS = 28.35;

/** Gramos para una referencia según tipo de alimento */
export function getGramsForReference(ref: PortionReference, foodType: FoodTypeForPortion = 'mixed'): number {
    const entry = PORTION_REFERENCES.find(e => e.key === ref);
    return entry?.gramsByFoodType[foodType] ?? entry?.gramsByFoodType.mixed ?? 100;
}

/** Infiere tipo de alimento desde macros (proteína, carbos, grasas por 100g) */
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

/** Palabras clave para inferir tipo desde nombre del alimento */
const SUGAR_KEYWORDS = ['azúcar', 'azucar', 'miel', 'edulcorante', 'stevia', 'sacarosa', 'glucosa'];
const PUREE_KEYWORDS = ['puré', 'pure', 'crema', 'salsa', 'humus', 'guacamole', 'yogur', 'yogurt', 'mantequilla de', 'dip'];
const LIQUID_KEYWORDS = ['leche', 'jugo', 'agua', 'café', 'cafe', 'té', 'te', 'bebida', 'smoothie', 'batido', 'caldo', 'sopa líquida'];
const FAT_KEYWORDS = ['aceite', 'mantequilla', 'manteca', 'margarina', 'mayonesa', 'crema de leche', 'nata'];
const REHYDRATABLE_KEYWORDS = ['soya texturizada', 'soja texturizada', 'proteína de soya', 'carne de soya', 'tvp', 'textured vegetable'];

/** Infiere tipo de alimento desde nombre + macros (prioridad: nombre si hay match) */
export function getFoodTypeForPortion(food: FoodItem): FoodTypeForPortion {
    const name = (food.name || '').toLowerCase();
    if (REHYDRATABLE_KEYWORDS.some(k => name.includes(k))) return 'rehydratable';
    if (SUGAR_KEYWORDS.some(k => name.includes(k))) return 'sugar';
    if (PUREE_KEYWORDS.some(k => name.includes(k))) return 'puree';
    if (LIQUID_KEYWORDS.some(k => name.includes(k))) return 'liquid';
    if (FAT_KEYWORDS.some(k => name.includes(k))) return 'fat';
    return getFoodTypeFromMacros(food.protein, food.carbs, food.fats);
}
