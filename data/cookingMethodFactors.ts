// data/cookingMethodFactors.ts
// Factores de ajuste por método de cocción (calorías y grasas)

import type { CookingMethod } from '../types';

export interface CookingFactor {
    caloriesFactor: number;
    fatsFactor: number;
}

export const COOKING_METHOD_FACTORS: Record<CookingMethod, CookingFactor> = {
    crudo: { caloriesFactor: 1.0, fatsFactor: 1.0 },
    cocido: { caloriesFactor: 1.0, fatsFactor: 1.0 },
    plancha: { caloriesFactor: 1.05, fatsFactor: 0.95 },
    horno: { caloriesFactor: 1.02, fatsFactor: 0.98 },
    frito: { caloriesFactor: 1.35, fatsFactor: 1.4 },
    empanizado_frito: { caloriesFactor: 1.5, fatsFactor: 1.65 },
};

/** Patrones de texto para detectar método de cocción */
export const COOKING_PATTERNS: { pattern: RegExp; method: CookingMethod }[] = [
    { pattern: /\bcrudo\b/i, method: 'crudo' },
    { pattern: /\bcocido\b|\bhervido\b|\bboiled\b/i, method: 'cocido' },
    { pattern: /\ba la plancha\b|\bplancha\b|\bgrilled\b/i, method: 'plancha' },
    { pattern: /\b(?:al )?horno\b|\bhornear\b|\bbaked\b/i, method: 'horno' },
    { pattern: /\bempanizado\b|\bempanado\b|\bbreaded\b/i, method: 'empanizado_frito' },
    { pattern: /\bfrito\b|\bfreído\b|\bfried\b|\brevuelto\b/i, method: 'frito' },
];

export function getCookingFactor(method: CookingMethod): CookingFactor {
    return COOKING_METHOD_FACTORS[method] ?? COOKING_METHOD_FACTORS.cocido;
}

export function detectCookingMethod(text: string): CookingMethod | undefined {
    for (const { pattern, method } of COOKING_PATTERNS) {
        if (pattern.test(text)) return method;
    }
    return undefined;
}
