// utils/nutritionDescriptionParser.ts
// Parser de descripciones de comida: detectores separados (gramos, alimento, cocción, porción, referencias)

import type { PortionPreset, ParsedMealItem, ParsedMealDescription, CookingMethod, PortionReference } from '../types';
import { resolveToCanonical } from '../data/foodSynonyms';
import { FOOD_DATABASE } from '../data/foodDatabase';
import { extractCookingMethodFromFragment } from '../data/cookingMethodFactors';
import { getGramsForReference, getFoodTypeForPortion } from '../data/portionReferences';

// Conectores que dividen la lista. "con" funciona igual que "y" (los alimentos con "con" en nombre usan "c/" en DB).
const COMMA_OR_PLUS = /,\s*|\s+\+\s+/g;
const CONNECTOR_Y = /\s+(?:y|e|mas|más)\s+/gi;
const CONNECTOR_CON = /\s+con\s+/gi;

// Gramos: "300g", "300 g", "200gr", "1.5 kg", "300 gramos de"
const GRAM_PATTERN = /(\d+(?:[.,]\d+)?)\s*(?:g|gr|gramos?|kg|ml|mililitros?|l|litros?|oz|onzas?|lb|libras?)(?:\s+de)?\s*/gi;

const LITERAL_QUANTITIES: Record<string, number> = {
    un: 1, una: 1, uno: 1, dos: 2, tres: 3, cuatro: 4, cinco: 5,
    seis: 6, siete: 7, ocho: 8, nueve: 9, diez: 10,
    media: 0.5, medio: 0.5, mitad: 0.5,
    'un cuarto': 0.25, cuarto: 0.25,
    'un tercio': 0.33, tercio: 0.33,
    doble: 2, triple: 3,
};

const PROTECTED_ENTITIES = [
    'arroz con leche', 'pollo con papas', 'pan con queso', 'pan con palta', 'pan con mantequilla',
    'papas con mayo', 'completo italiano', 'pastel de choclo', 'empanada de pino', 'carne al horno'
];

const KNOWN_BRANDS = [
    'soprole', 'colun', 'lider', 'optimum nutrition', 'quaker', 'nestlé', 'nestle', 'mccormick',
    'carozzi', 'lucchetti', 'costa', 'mckay', 'tucapel', 'san jorge', 'pf', 'sopraval', 'ideal',
    'gatorade', 'coca-cola', "bob's red mill", 'myprotein', 'quest', 'acuenta', 'tottus', 'jumbo'
];

const DIMENSIONAL_ADJECTIVES = {
    amplifiers: /\bgrues[oa]s?\b|\bcolmad[oa]s?\b|\bgeneros[oa]s?\b|\brebosantes?\b|\bgigantes?\b/i,
    reducers: /\bfinas?\b|\brasas?\b|\bdelgad[oa]s?\b|\bpequeñit[oa]s?\b/i
};

const ANATOMICAL_PATTERNS: { pattern: RegExp; id: 'sin_miga' | 'sin_yema' | 'solo_claras' | 'sin_piel' }[] = [
    { pattern: /\bsin\s+miga\b/i, id: 'sin_miga' },
    { pattern: /\bsin\s+yema\b/i, id: 'sin_yema' },
    { pattern: /\bsolo\s+claras\b/i, id: 'solo_claras' },
    { pattern: /\bsin\s+piel\b/i, id: 'sin_piel' },
];

const HEURISTIC_PATTERNS: { pattern: RegExp; id: 'descremado' | 'light' | 'integral' }[] = [
    { pattern: /\bdescremad[oa]s?\b|\bfat\s+free\b/i, id: 'descremado' },
    { pattern: /\blight\b|\bliger[oa]s?\b/i, id: 'light' },
    { pattern: /\bintegral(es)?\b/i, id: 'integral' },
];

const PREPARATION_PATTERNS: { pattern: RegExp; id: 'pelado' | 'picado' | 'deshuesado' | 'con_hueso' | 'rayado' }[] = [
    { pattern: /\bpelad[oa]s?\b/i, id: 'pelado' },
    { pattern: /\bpicad[oa]s?\b/i, id: 'picado' },
    { pattern: /\bdeshuesad[oa]s?\b|\bsin\s+hueso\b/i, id: 'deshuesado' },
    { pattern: /\bcon\s+hueso\b/i, id: 'con_hueso' },
    { pattern: /\brayad[oa]s?\b|\brallad[oa]s?\b/i, id: 'rayado' },
];

const STATE_PATTERNS: { pattern: RegExp; id: 'en_almibar' | 'al_agua' | 'en_polvo' | 'concentrado' | 'deshidratado' }[] = [
    { pattern: /\ben\s+alm[ií]bar\b/i, id: 'en_almibar' },
    { pattern: /\bal\s+agua\b/i, id: 'al_agua' },
    { pattern: /\ben\s+polvo\b/i, id: 'en_polvo' },
    { pattern: /\bconcentrad[oa]s?\b/i, id: 'concentrado' },
    { pattern: /\bdeshidratad[oa]s?\b/i, id: 'deshidratado' },
];

const COMPOSITION_PATTERNS: { pattern: RegExp; id: 'extra_tierno' | 'con_grasa' | 'sin_grasa' | 'bajo_sodio' }[] = [
    { pattern: /\bextra\s+tierno\b/i, id: 'extra_tierno' },
    { pattern: /\bcon\s+grasa\b/i, id: 'con_grasa' },
    { pattern: /\bsin\s+grasa\b/i, id: 'sin_grasa' },
    { pattern: /\bbajo\s+en\s+sodio\b/i, id: 'bajo_sodio' },
];

/** Referencias de porción: "1 cucharada de X", "una taza de arroz", "un toque de sal" */
const REFERENCE_PATTERNS: { pattern: RegExp; ref: PortionReference; getQty?: (m: RegExpMatchArray) => number }[] = [
    { pattern: /\b(un|una|1)\s+(toque|pellizco)s?\s+de\s+(.+)/i, ref: 'pinch', getQty: () => 1 },
    { pattern: /\b(dos|tres|cuatro|cinco)\s+(toques?|pellizcos?)\s+de\s+(.+)/i, ref: 'pinch', getQty: m => ({ dos: 2, tres: 3, cuatro: 4, cinco: 5 }[m[1].toLowerCase()] ?? 2) },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(cucharadas?)\s+de\s+(.+)/i, ref: 'tablespoon', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(un|una|media|1)\s+(cucharada)\s+de\s+(.+)/i, ref: 'tablespoon', getQty: m => m[1].toLowerCase() === 'media' ? 0.5 : 1 },
    { pattern: /\b(dos|tres|cuatro|cinco)\s+(cucharadas?)\s+de\s+(.+)/i, ref: 'tablespoon', getQty: m => ({ dos: 2, tres: 3, cuatro: 4, cinco: 5 }[m[1].toLowerCase()] ?? 2) },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(cucharaditas?)\s+de\s+(.+)/i, ref: 'teaspoon', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(un|una|media|1)\s+(cucharaditas?)\s+de\s+(.+)/i, ref: 'teaspoon', getQty: m => m[1].toLowerCase() === 'media' ? 0.5 : 1 },
    { pattern: /\b(dos|tres|cuatro|cinco)\s+(cucharaditas?)\s+de\s+(.+)/i, ref: 'teaspoon', getQty: m => ({ dos: 2, tres: 3, cuatro: 4, cinco: 5 }[m[1].toLowerCase()] ?? 2) },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(tazas?)\s+de\s+(.+)/i, ref: 'cup', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(un|una|1)\s+(taza)\s+de\s+(.+)/i, ref: 'cup', getQty: () => 1 },
    { pattern: /\b(media|medio|0\.5)\s+(taza)\s+de\s+(.+)/i, ref: 'cup', getQty: () => 0.5 },
    { pattern: /\b(un|una|1)\s+(puñado)\s+de\s+(.+)/i, ref: 'handful', getQty: () => 1 },
    { pattern: /\b(un|una|1)\s+(palma)s?\s+(?:de\s+)?(.+)/i, ref: 'palm', getQty: () => 1 },
    { pattern: /\b(un|1)\s+(puño)\s+de\s+(.+)/i, ref: 'fist', getQty: () => 1 },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(vasos?)\s+de\s+(.+)/i, ref: 'glass', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(un|una|1)\s+(vaso)\s+de\s+(.+)/i, ref: 'glass', getQty: () => 1 },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(rebanadas?|tajadas?)\s+de\s+(.+)/i, ref: 'slice', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(un|una|1|dos|tres|cuatro)\s+(rebanadas?|tajadas?)\s+de\s+(.+)/i, ref: 'slice', getQty: m => ({ un: 1, una: 1, dos: 2, tres: 3, cuatro: 4 }[m[1].toLowerCase()] ?? 1) },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(latas?)\s+de\s+(.+)/i, ref: 'can', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(un|una|1|media)\s+(latas?)\s+de\s+(.+)/i, ref: 'can', getQty: m => m[1].toLowerCase() === 'media' ? 0.5 : 1 },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(porciones?)\s+de\s+(.+)/i, ref: 'portion', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(un|una|1|media)\s+(porciones?)\s+de\s+(.+)/i, ref: 'portion', getQty: m => m[1].toLowerCase() === 'media' ? 0.5 : 1 },
    { pattern: /\b(\d+(?:[.,]\d+)?)\s+(scoops?|medidas?)\s+de\s+(.+)/i, ref: 'scoop', getQty: m => parseFloat(m[1].replace(',', '.')) || 1 },
    { pattern: /\b(un|una|1|medio|media|0\.5)\s+(scoops?|medidas?)\s+de\s+(.+)/i, ref: 'scoop', getQty: m => (/medio|media|0\.5/.test(m[1].toLowerCase())) ? 0.5 : 1 },
];

const PORTION_PATTERNS: { pattern: RegExp; preset: PortionPreset }[] = [
    { pattern: /\b(plato\s+)?(muy\s+)?grande\b|\bgeneroso\b/i, preset: 'extra' },
    { pattern: /\b(plato\s+)?grande\b/i, preset: 'large' },
    { pattern: /\b(plato\s+)?mediano\b|\bmediana\b/i, preset: 'medium' },
    { pattern: /\b(plato\s+)?(muy\s+)?(pequeño|chico)\b|\bchica\b/i, preset: 'small' },
    { pattern: /\bporción\s+grande\b/i, preset: 'large' },
    { pattern: /\bporción\s+pequeña\b/i, preset: 'small' },
    { pattern: /\bmedia\s+(taza|porción|ración)\b/i, preset: 'small' },
    { pattern: /\bdoble\s+(porción|ración)\b/i, preset: 'extra' },
    { pattern: /\b1\s+unidad\b/i, preset: 'medium' },
    { pattern: /\bmedio\s+plato\b/i, preset: 'small' },
];


function extractPortionFromFragment(text: string): { portion: PortionPreset; cleaned: string } {
    let cleaned = text;
    let portion: PortionPreset = 'medium';
    for (const { pattern, preset } of PORTION_PATTERNS) {
        if (cleaned.match(pattern)) {
            portion = preset;
            cleaned = cleaned.replace(pattern, ' ').replace(/\s{2,}/g, ' ').trim();
            break;
        }
    }
    return { portion, cleaned };
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
            else if (/ml|mililitros?/i.test(unit)) { /* 1ml ~= 1g roughly for most entries */ }

            grams = grams != null ? grams + val : val;
        }
        cleaned = cleaned.replace(m, ' ').replace(/\s{2,}/g, ' ').trim();
    }
    return { grams, cleaned };
}

/** Extrae referencias: "1 cucharada de aceite", "una taza de arroz", "un toque de sal" -> grams según tipo de alimento */
function extractReferenceFromFragment(text: string): { grams?: number; quantity: number; cleaned: string } {
    const t = text.trim();
    for (const { pattern, ref, getQty } of REFERENCE_PATTERNS) {
        const m = t.match(pattern);
        if (!m) continue;
        const foodPart = (m[3] || m[2] || '').trim().replace(/^\s*de\s+/i, '');
        if (!foodPart || foodPart.length < 2) continue;
        const qty = getQty ? getQty(m) : 1;

        const { multiplier, cleaned: afterDim } = extractDimensionalMultiplier(foodPart);

        const canonical = resolveToCanonical(afterDim);
        const food = FOOD_DATABASE.find(f =>
            f.name.toLowerCase().replace(/\s+con\s+/gi, ' c/ ') === canonical.toLowerCase() ||
            f.name.toLowerCase().includes(canonical.toLowerCase()) ||
            canonical.toLowerCase().includes(f.name.toLowerCase().replace(/\s+con\s+/gi, ' c/ '))
        );
        const foodType = food ? getFoodTypeForPortion(food) : 'mixed';
        const gramsPerUnit = getGramsForReference(ref, foodType);
        const grams = Math.round(gramsPerUnit * qty * multiplier * 10) / 10;
        return { grams, quantity: qty, cleaned: afterDim };
    }
    return { quantity: 1, cleaned: t };
}

function extractDimensionalMultiplier(text: string): { multiplier: number; cleaned: string } {
    let multiplier = 1;
    let cleaned = text;

    if (DIMENSIONAL_ADJECTIVES.amplifiers.test(cleaned)) {
        multiplier = 1.5;
        cleaned = cleaned.replace(DIMENSIONAL_ADJECTIVES.amplifiers, ' ');
    } else if (DIMENSIONAL_ADJECTIVES.reducers.test(cleaned)) {
        multiplier = 0.6;
        cleaned = cleaned.replace(DIMENSIONAL_ADJECTIVES.reducers, ' ');
    }

    return { multiplier, cleaned: cleaned.replace(/\s{2,}/g, ' ').trim() };
}

function extractAnatomicalModifiers(text: string): { modifiers?: ParsedMealItem['anatomicalModifiers']; cleaned: string } {
    const modifiers: ParsedMealItem['anatomicalModifiers'] = [];
    let cleaned = text;

    for (const { pattern, id } of ANATOMICAL_PATTERNS) {
        if (pattern.test(cleaned)) {
            modifiers.push(id);
            cleaned = cleaned.replace(pattern, ' ');
        }
    }

    return { modifiers: modifiers.length > 0 ? modifiers : undefined, cleaned: cleaned.replace(/\s{2,}/g, ' ').trim() };
}

function extractHeuristicModifiers(text: string): { modifiers?: ParsedMealItem['heuristicModifiers']; cleaned: string } {
    const modifiers: ParsedMealItem['heuristicModifiers'] = [];
    let cleaned = text;

    for (const { pattern, id } of HEURISTIC_PATTERNS) {
        if (pattern.test(cleaned)) {
            modifiers.push(id);
            cleaned = cleaned.replace(pattern, ' ');
        }
    }

    return { modifiers: modifiers.length > 0 ? modifiers : undefined, cleaned: cleaned.replace(/\s{2,}/g, ' ').trim() };
}

function extractPreparationModifiers(text: string): { modifiers?: ParsedMealItem['preparationModifiers']; cleaned: string } {
    const modifiers: ParsedMealItem['preparationModifiers'] = [];
    let cleaned = text;
    for (const { pattern, id } of PREPARATION_PATTERNS) {
        if (pattern.test(cleaned)) {
            modifiers.push(id);
            cleaned = cleaned.replace(pattern, ' ');
        }
    }
    return { modifiers: modifiers.length > 0 ? modifiers : undefined, cleaned: cleaned.replace(/\s{2,}/g, ' ').trim() };
}

function extractStateModifiers(text: string): { modifiers?: ParsedMealItem['stateModifiers']; cleaned: string } {
    const modifiers: ParsedMealItem['stateModifiers'] = [];
    let cleaned = text;
    for (const { pattern, id } of STATE_PATTERNS) {
        if (pattern.test(cleaned)) {
            modifiers.push(id);
            cleaned = cleaned.replace(pattern, ' ');
        }
    }
    return { modifiers: modifiers.length > 0 ? modifiers : undefined, cleaned: cleaned.replace(/\s{2,}/g, ' ').trim() };
}

function extractCompositionModifiers(text: string): { modifiers?: ParsedMealItem['compositionModifiers']; cleaned: string } {
    const modifiers: ParsedMealItem['compositionModifiers'] = [];
    let cleaned = text;
    for (const { pattern, id } of COMPOSITION_PATTERNS) {
        if (pattern.test(cleaned)) {
            modifiers.push(id);
            cleaned = cleaned.replace(pattern, ' ');
        }
    }
    return { modifiers: modifiers.length > 0 ? modifiers : undefined, cleaned: cleaned.replace(/\s{2,}/g, ' ').trim() };
}

function matchesKnownFoodName(term: string): boolean {
    const normalizedTerm = term
        .normalize('NFD')
        .replace(/\p{Diacritic}/gu, '')
        .toLowerCase()
        .replace(/\s+/g, ' ')
        .trim();

    return FOOD_DATABASE.some(food => {
        const normalizedName = food.name
            .normalize('NFD')
            .replace(/\p{Diacritic}/gu, '')
            .toLowerCase()
            .replace(/\s*\([^)]*\)\s*/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
        return normalizedName === normalizedTerm;
    });
}

function resolveCanonicalFoodName(rawCandidate: string, cleanedCandidate: string): string {
    const candidates = [rawCandidate, cleanedCandidate]
        .map(value => value.trim())
        .filter(Boolean);

    for (const candidate of candidates) {
        const resolved = resolveToCanonical(candidate);
        if (resolved !== candidate.trim()) return resolved;
        if (matchesKnownFoodName(candidate)) return candidate.trim();
    }

    return resolveToCanonical(cleanedCandidate.trim());
}

function parseStructuralFractions(text: string): string {
    return text
        .replace(/\b1\/2\b/g, '0.5')
        .replace(/\b1\/4\b/g, '0.25')
        .replace(/\b3\/4\b/g, '0.75')
        .replace(/\b1\/3\b/g, '0.33')
        .replace(/\b2\/3\b/g, '0.66')
        .replace(/\b1\s+1\/2\b/g, '1.5');
}

/** Parsea multiplicador de cantidad: "2 panes", "3 huevos", "dos huevos" (NO "200g") */
function parseQuantityMultiplier(text: string): { quantity: number; foodPart: string } {
    let t = text.trim();
    t = parseStructuralFractions(t);

    // Rango ej: 100-150g -> No matcheará aquí si es "g" porque extractGramsFromFragment lo limpia,
    // pero si es "1-2 manzanas" promedia
    const rangeMatch = t.match(/^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s+(.+)$/);
    if (rangeMatch) {
        const qty1 = parseFloat(rangeMatch[1]);
        const qty2 = parseFloat(rangeMatch[2]);
        const rest = rangeMatch[3].trim();
        const avg = (qty1 + qty2) / 2;
        if (rest.length >= 2 && avg > 0 && avg <= 50) return { quantity: avg, foodPart: rest };
    }

    const numMatch = t.match(/^(\d+(?:\.\d+)?)\s*(?:x\s*)?(.+)$/);
    if (numMatch) {
        const qty = parseFloat(numMatch[1]);
        const rest = numMatch[2].trim();
        if (rest.length >= 2 && qty > 0 && qty <= 50) return { quantity: qty, foodPart: rest };
    }
    const literalMatch = t.match(/^(un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|media|medio|mitad|un cuarto|cuarto|un tercio|tercio|doble|triple)\s+(.+)$/i);
    if (literalMatch) {
        const qty = LITERAL_QUANTITIES[literalMatch[1].toLowerCase()];
        const rest = literalMatch[2].trim();
        if (qty != null && rest.length >= 2) return { quantity: qty, foodPart: rest };
    }
    return { quantity: 1, foodPart: t };
}

function extractBrand(text: string): { brand?: string; cleaned: string } {
    let cleaned = text;
    let brandFound: string | undefined;

    // Sort brands by length descending so "Optimum Nutrition" matches before "Optimum"
    const sortedBrands = [...KNOWN_BRANDS].sort((a, b) => b.length - a.length);
    for (const brand of sortedBrands) {
        const regex = new RegExp(`\\b${brand}\\b`, 'i');
        if (regex.test(cleaned)) {
            brandFound = brand;
            cleaned = cleaned.replace(regex, ' ').replace(/\s{2,}/g, ' ').trim();
            break; // take first match
        }
    }
    return { brand: brandFound, cleaned };
}

function extractInlineOverrides(text: string): {
    overrides?: { calories?: number; protein?: number; carbs?: number; fats?: number };
    cleaned: string
} {
    let cleaned = text;
    let overrides: { calories?: number; protein?: number; carbs?: number; fats?: number } | undefined;

    // Kcal
    const kcalMatch = cleaned.match(/\[?\b(\d+)\s*(?:kcal|calorias?|cal)\b\]?/i);
    if (kcalMatch) {
        if (!overrides) overrides = {};
        overrides.calories = parseFloat(kcalMatch[1]);
        cleaned = cleaned.replace(kcalMatch[0], ' ');
    }

    // Prot
    const pMatch = cleaned.match(/\(?\b(\d+)\s*(?:g\s*prot|p\b)/i);
    if (pMatch) {
        if (!overrides) overrides = {};
        overrides.protein = parseFloat(pMatch[1]);
        cleaned = cleaned.replace(pMatch[0], ' ');
    }
    // Carb
    const cMatch = cleaned.match(/\(?\b(\d+)\s*(?:g\s*carb|c\b)/i);
    if (cMatch) {
        if (!overrides) overrides = {};
        overrides.carbs = parseFloat(cMatch[1]);
        cleaned = cleaned.replace(cMatch[0], ' ');
    }
    // Fat
    const fMatch = cleaned.match(/\(?\b(\d+)\s*(?:g\s*fat|g\s*grasas?|g\s*lipidos?|f\b)/i);
    if (fMatch) {
        if (!overrides) overrides = {};
        overrides.fats = parseFloat(fMatch[1]);
        cleaned = cleaned.replace(fMatch[0], ' ');
    }

    // Clean up empty parentheses
    cleaned = cleaned.replace(/\(\s*[,]*\s*\)/g, ' ').replace(/\[\s*\]/g, ' ').replace(/\s{2,}/g, ' ').trim();

    return { overrides, cleaned };
}

function parseFragment(frag: string): ParsedMealItem | null {
    let text = frag.trim();
    if (!text) return null;

    // Detect Additive Grouping: "Recipe (item1, item2)"
    const groupMatch = text.match(/^(.+?)\s*\((.+)\)\s*$/);
    if (groupMatch) {
        const groupName = groupMatch[1].trim();
        const content = groupMatch[2].trim();
        const subFragments = splitByListConnectors(content);
        const subItems = subFragments.map(f => parseFragment(f)).filter(Boolean) as ParsedMealItem[];

        if (subItems.length > 0) {
            return {
                tag: groupName,
                quantity: 1,
                isGroup: true,
                subItems
            };
        }
    }

    const defaultPortion: PortionPreset = 'medium';

    let grams: number | undefined;
    let working: string;

    const { grams: gramsFromExplicit, cleaned: afterGrams } = extractGramsFromFragment(text);
    grams = gramsFromExplicit;
    working = afterGrams;

    let dimMultiplier = 1;
    if (grams == null) {
        const refResult = extractReferenceFromFragment(working);
        if (refResult.grams != null) {
            grams = refResult.grams;
            working = refResult.cleaned;
        }
    } else {
        const { multiplier, cleaned: afterDim } = extractDimensionalMultiplier(working);
        dimMultiplier = multiplier;
        working = afterDim;
        if (grams != null) grams *= dimMultiplier;
    }

    const { method: cookingMethod, cleaned: afterCooking } = extractCookingMethodFromFragment(working);
    working = afterCooking;

    const { portion, cleaned: afterPortion } = extractPortionFromFragment(working);
    working = afterPortion;

    const { brand, cleaned: afterBrand } = extractBrand(working);
    working = afterBrand;

    const { overrides, cleaned: afterOverrides } = extractInlineOverrides(working);
    working = afterOverrides;

    const canonicalContext = working;

    const { modifiers: anatomical, cleaned: afterAnatomical } = extractAnatomicalModifiers(working);
    working = afterAnatomical;

    const { modifiers: heuristic, cleaned: afterHeuristic } = extractHeuristicModifiers(working);
    working = afterHeuristic;

    const { modifiers: prep, cleaned: afterPrep } = extractPreparationModifiers(working);
    working = afterPrep;

    const { modifiers: state, cleaned: afterState } = extractStateModifiers(working);
    working = afterState;

    const { modifiers: comp, cleaned: afterComp } = extractCompositionModifiers(working);
    working = afterComp;

    working = working.replace(/^\s*de\s+/i, '').replace(/\s+de\s+$/i, '').replace(/\s{2,}/g, ' ').trim();
    if (!working) return null;

    const { quantity, foodPart } = parseQuantityMultiplier(working);
    const foodName = foodPart || working;
    if (!foodName || foodName.length < 2) return null;

    const canonical = resolveCanonicalFoodName(canonicalContext, foodName);
    const tag = canonical;
    if (tag.length < 2) return null;

    return {
        tag,
        quantity,
        amountGrams: grams,
        cookingMethod,
        portion: grams != null ? undefined : portion,
        isFuzzyMatch: false,
        brandHint: brand,
        macroOverrides: overrides,
        anatomicalModifiers: anatomical,
        heuristicModifiers: heuristic,
        preparationModifiers: prep,
        stateModifiers: state,
        compositionModifiers: comp,
        dimensionalMultiplier: dimMultiplier !== 1 ? dimMultiplier : undefined
    };
}

function splitByListConnectors(description: string): string[] {
    let trimmed = description.trim();
    if (!trimmed) return [];

    // 0. Mask Parenthesized content to avoid splitting inside them
    const pMasks: { token: string, original: string }[] = [];
    let pIter = 0;
    while (trimmed.includes('(') && trimmed.includes(')')) {
        const start = trimmed.lastIndexOf('(');
        const end = trimmed.indexOf(')', start);
        if (start === -1 || end === -1) break;
        const original = trimmed.substring(start, end + 1);
        const token = `__PAREN_${pIter++}__`;
        pMasks.push({ token, original });
        trimmed = trimmed.substring(0, start) + token + trimmed.substring(end + 1);
    }

    // 1. Mask protected entities
    const masks: { token: string, original: string }[] = [];
    PROTECTED_ENTITIES.forEach((entity, index) => {
        const regex = new RegExp(`\\b${entity}\\b`, 'gi');
        trimmed = trimmed.replace(regex, (match) => {
            const token = `__PROTECTED_${index}__`;
            masks.push({ token, original: match });
            return token;
        });
    });

    // 2. Split by connectors
    let parts: string[] = [trimmed];
    const splitBy = (regex: RegExp) => {
        const next: string[] = [];
        for (const p of parts) {
            const sub = p.split(regex).map(s => s.trim()).filter(Boolean);
            next.push(...sub);
        }
        parts = next;
    };
    splitBy(COMMA_OR_PLUS);
    splitBy(CONNECTOR_Y);
    splitBy(CONNECTOR_CON);

    // 3. Unmask protected entities and Handle Negative Modifiers
    parts = parts.map(p => {
        // Unmask
        let unmasked = p;
        for (const { token, original } of masks) {
            unmasked = unmasked.replace(token, original);
        }
        for (const { token, original } of pMasks) {
            unmasked = unmasked.replace(token, original);
        }

        // Remove everything after "sin", "menos", "no" in this part
        // BUT ONLY IF it's not a recognized modifier keyword
        // Example: "cafe sin azucar" -> "cafe "
        // Example: "pan sin miga" -> Preserve "sin miga" if it matches ANATOMICAL_PATTERNS
        const negMatch = unmasked.match(/\b(?:sin|menos|no)\b/i);
        if (negMatch && negMatch.index !== undefined) {
            const afterNeg = unmasked.substring(negMatch.index).toLowerCase();
            const isModifier = ANATOMICAL_PATTERNS.some(p => p.pattern.test(afterNeg)) ||
                COMPOSITION_PATTERNS.some(p => p.pattern.test(afterNeg)) ||
                PREPARATION_PATTERNS.some(p => p.pattern.test(afterNeg));

            if (!isModifier) {
                unmasked = unmasked.substring(0, negMatch.index);
            }
        }

        return unmasked.trim();
    }).filter(Boolean);

    return parts;
}

/** Porción global para "plato grande de X y Y" */
function extractGlobalPortion(description: string): PortionPreset {
    for (const { pattern, preset } of PORTION_PATTERNS) {
        if (description.match(pattern)) return preset;
    }
    return 'medium';
}

/** Extrae items con cantidades, gramos y cocción por fragmento */
function extractItemsWithQuantities(description: string): ParsedMealItem[] {
    const globalPortion = extractGlobalPortion(description);
    const fragments = splitByListConnectors(description);
    const items: ParsedMealItem[] = [];
    const seen = new Set<string>();

    for (const frag of fragments) {
        const parsed = parseFragment(frag);
        if (parsed) {
            if (!seen.has(parsed.tag)) {
                seen.add(parsed.tag);
                const portion = parsed.portion ?? globalPortion;
                items.push({ ...parsed, portion });
            } else {
                const idx = items.findIndex(i => i.tag === parsed.tag);
                if (idx >= 0) {
                    items[idx].quantity += parsed.quantity;
                    if (parsed.amountGrams != null) {
                        items[idx].amountGrams = (items[idx].amountGrams ?? 0) + parsed.amountGrams;
                    }
                }
            }
        }
    }

    if (items.length === 0 && description.trim()) {
        const parsed = parseFragment(description.trim());
        if (parsed) {
            items.push({ ...parsed, portion: parsed.portion ?? globalPortion });
        }
    }

    return items;
}

export interface ParsedDescription {
    tags: string[];
    portion: PortionPreset;
    rawDescription: string;
}

export function parseMealDescription(description: string): ParsedMealDescription {
    const trimmed = description.trim();
    if (!trimmed) return { items: [], rawDescription: '' };
    const items = extractItemsWithQuantities(trimmed);
    return { items, rawDescription: trimmed };
}

export function parseNutritionDescription(description: string): ParsedDescription {
    const { items, rawDescription } = parseMealDescription(description);
    const portion = (items[0]?.portion as PortionPreset) ?? 'medium';
    const tags = items.map(i => i.tag);
    return { tags, portion, rawDescription };
}
