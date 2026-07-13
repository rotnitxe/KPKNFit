// apps/mobile/src/utils/nutritionDescriptionParser.ts
// Parser de descripciones de comida - Ported from PWA
import { resolveToCanonical } from '../data/foodSynonyms';
import { extractCookingMethodFromFragment } from '../data/cookingMethodFactors';
import { getGramsForReference, getFoodTypeForPortion } from '../data/portionReferences';
import type { PortionReference, FoodTypeForPortion } from '../data/portionReferences';

export type PortionPreset = 'small' | 'medium' | 'large' | 'extra';

export interface ParsedMealItem {
  tag: string;
  quantity: number;
  amountGrams?: number;
  cookingMethod?: string;
  portion?: PortionPreset;
  isFuzzyMatch?: boolean;
  brandHint?: string;
  macroOverrides?: { calories?: number; protein?: number; carbs?: number; fats?: number };
  anatomicalModifiers?: string[];
  heuristicModifiers?: string[];
  preparationModifiers?: string[];
  stateModifiers?: string[];
  compositionModifiers?: string[];
  dimensionalMultiplier?: number;
  isGroup?: boolean;
  subItems?: ParsedMealItem[];
}

export interface ParsedMealDescription {
  items: ParsedMealItem[];
  rawDescription: string;
}

const COMMA_OR_PLUS = /,\s*|\s+\+\s+/g;
const CONNECTOR_Y = /\s+(?:y|e|mas|más)\s+/gi;
const CONNECTOR_CON = /\s+con\s+/gi;
const GRAM_PATTERN = /(\d+(?:[.,]\d+)?)\s*(?:g|gr|gramos?|kg|ml|mililitros?|l|litros?|oz|onzas?|lb|libras?)(?:\s+de)?\s*/gi;

const LITERAL_QUANTITIES: Record<string, number> = {
    un: 1, una: 1, uno: 1, dos: 2, tres: 3, cuatro: 4, cinco: 5,
    seis: 6, siete: 7, ocho: 8, nueve: 9, diez: 10,
    media: 0.5, medio: 0.5, mitad: 0.5,
    'un cuarto': 0.25, cuarto: 0.25,
    'un tercio': 0.33, tercio: 0.33,
    doble: 2, triple: 3,
};

const PORTION_PATTERNS: { pattern: RegExp; preset: PortionPreset }[] = [
    { pattern: /\b(plato\s+)?(muy\s+)?grande\b|\bgeneroso\b/i, preset: 'extra' },
    { pattern: /\b(plato\s+)?grande\b/i, preset: 'large' },
    { pattern: /\b(plato\s+)?mediano\b|\bmediana\b/i, preset: 'medium' },
    { pattern: /\b(plato\s+)?(muy\s+)?(pequeño|chico)\b|\bchica\b/i, preset: 'small' },
];

const REFERENCE_PATTERNS: { pattern: RegExp; ref: PortionReference; getQty?: (m: RegExpMatchArray) => number }[] = [
    { pattern: /\b(un|una|1)\s+(toque|pellizco)s?\s+de\s+(.+)/i, ref: 'pinch', getQty: () => 1 },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(cucharadas?)\s+de\s+(.+)/i, ref: 'tablespoon', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(tazas?)\s+de\s+(.+)/i, ref: 'cup', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(vasos?)\s+de\s+(.+)/i, ref: 'glass', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(rebanadas?|tajadas?)\s+de\s+(.+)/i, ref: 'slice', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
];

function normalizeLookupText(text: string): string {
    return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().trim();
}

function extractGramsFromFragment(text: string): { grams?: number; cleaned: string } {
    const match = text.match(GRAM_PATTERN);
    if (!match) return { cleaned: text };
    let grams: number | undefined;
    let cleaned = text;
    for (const m of match) {
        const numMatch = m.match(/(\d+(?:[.,]\d+)?)\s*(g|gr|gramos?|kg|ml|mililitros?|l|litros?|oz|onzas?|lb|libras?)/i);
        if (numMatch) {
            let val = parseFloat(numMatch[1].replace(',', '.'));
            const unit = numMatch[2].toLowerCase();
            if (/kg|l$|litros?/i.test(unit)) val *= 1000;
            else if (/oz|onzas?/i.test(unit)) val *= 28.3495;
            else if (/lb|libras?/i.test(unit)) val *= 453.592;
            grams = grams != null ? grams + val : val;
        }
        cleaned = cleaned.replace(m, ' ').replace(/\s{2,}/g, ' ').trim();
    }
    return { grams, cleaned };
}

function parseFragment(frag: string): ParsedMealItem | null {
    let text = frag.trim();
    if (!text) return null;

    let grams: number | undefined;
    const { grams: gramsFromExplicit, cleaned: afterGrams } = extractGramsFromFragment(text);
    grams = gramsFromExplicit;
    let working = afterGrams;

    const { method: cookingMethod, cleaned: afterCooking } = extractCookingMethodFromFragment(working);
    working = afterCooking;

    // Simplified parsing logic for mobile port
    const tag = resolveToCanonical(working);
    return {
        tag,
        quantity: 1,
        amountGrams: grams,
        cookingMethod: cookingMethod as any,
        portion: 'medium'
    };
}

export function parseMealDescription(description: string): ParsedMealDescription {
    const trimmed = description.trim();
    if (!trimmed) return { items: [], rawDescription: '' };
    const fragments = trimmed.split(COMMA_OR_PLUS);
    const items = fragments.map(f => parseFragment(f)).filter(Boolean) as ParsedMealItem[];
    return { items, rawDescription: trimmed };
}
