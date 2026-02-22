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

/** Patrones de texto para detectar método de cocción (orden: más específicos primero) */
export const COOKING_PATTERNS: { pattern: RegExp; method: CookingMethod }[] = [
    { pattern: /\bempanizado\b|\bempanado\b|\bapanado\b|\bbreaded\b/i, method: 'empanizado_frito' },
    { pattern: /\ba la plancha\b|\ba la parrilla\b|\bparrilla\b|\bplancha\b|\bgrilled\b|\basado\b|\basada\b/i, method: 'plancha' },
    { pattern: /\b(?:al )?horno\b|\bhornear\b|\bhorneado\b|\bhorneada\b|\bbaked\b/i, method: 'horno' },
    { pattern: /\bfrito\b|\bfreído\b|\bfreída\b|\bfried\b|\brevuelto\b|\brevuelta\b|\bsalteado\b|\bsalteada\b|\bsaltear\b/i, method: 'frito' },
    { pattern: /\b(?:al )?vapor\b|\bvapor\b|\bsteamed\b/i, method: 'cocido' },
    { pattern: /\bcocido\b|\bcocida\b|\bhervido\b|\bhervida\b|\bboiled\b/i, method: 'cocido' },
    { pattern: /\bcrudo\b|\bcruda\b|\bfresco\b|\bfresca\b|\braw\b/i, method: 'crudo' },
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

/** Extrae método de cocción y devuelve el texto sin esa palabra (para obtener nombre limpio del alimento) */
export function extractCookingMethodFromFragment(text: string): { method?: CookingMethod; cleaned: string } {
    let cleaned = text;
    let method: CookingMethod | undefined;
    for (const { pattern, method: m } of COOKING_PATTERNS) {
        const match = cleaned.match(pattern);
        if (match) {
            method = m;
            cleaned = cleaned.replace(pattern, ' ').replace(/\s{2,}/g, ' ').trim();
            break;
        }
    }
    return { method, cleaned };
}
