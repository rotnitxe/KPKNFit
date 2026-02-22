// utils/nutritionDescriptionParser.ts
// Parser de descripciones de comida: detectores separados (gramos, alimento, cocción, porción)

import type { PortionPreset, ParsedMealItem, ParsedMealDescription, CookingMethod } from '../types';
import { resolveToCanonical } from '../data/foodSynonyms';
import { FOOD_DATABASE } from '../data/foodDatabase';
import { extractCookingMethodFromFragment } from '../data/cookingMethodFactors';

// Conectores que dividen la lista. "con" funciona igual que "y" (los alimentos con "con" en nombre usan "c/" en DB).
const COMMA_OR_PLUS = /,\s*|\s+\+\s+/g;
const CONNECTOR_Y = /\s+y\s+/gi;
const CONNECTOR_CON = /\s+con\s+/gi;

// Gramos: "300g", "300 g", "200gr", "1.5 kg", "300 gramos de"
const GRAM_PATTERN = /(\d+(?:[.,]\d+)?)\s*(?:g|gr|gramos?|kg)(?:\s+de)?\s*/gi;

const LITERAL_QUANTITIES: Record<string, number> = {
    un: 1, una: 1, uno: 1, dos: 2, tres: 3, cuatro: 4, cinco: 5,
    media: 0.5, medio: 0.5, doble: 2, triple: 3,
};

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

function findInDatabase(term: string): string | null {
    const normalized = term.trim().toLowerCase().replace(/\s+con\s+/gi, ' c/ ');
    if (!normalized || normalized.length < 2) return null;
    const exact = FOOD_DATABASE.find(f =>
        f.name.toLowerCase().replace(/\s+con\s+/gi, ' c/ ') === normalized
    );
    if (exact) return exact.name;
    const partial = FOOD_DATABASE.find(f => {
        const fn = f.name.toLowerCase().replace(/\s+con\s+/gi, ' c/ ');
        return fn.includes(normalized) || normalized.includes(fn);
    });
    return partial?.name ?? null;
}

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
        const numMatch = m.match(/(\d+(?:[.,]\d+)?)\s*(?:g|gr|gramos?|kg)/i);
        if (numMatch) {
            let val = parseFloat(numMatch[1].replace(',', '.'));
            if (/kg/i.test(m)) val *= 1000;
            grams = grams != null ? grams + val : val;
        }
        cleaned = cleaned.replace(m, ' ').replace(/\s{2,}/g, ' ').trim();
    }
    return { grams, cleaned };
}

/** Parsea multiplicador de cantidad: "2 panes", "3 huevos", "dos huevos" (NO "200g") */
function parseQuantityMultiplier(text: string): { quantity: number; foodPart: string } {
    const t = text.trim();
    const numMatch = t.match(/^(\d+(?:\.\d+)?)\s*(?:x\s*)?(.+)$/);
    if (numMatch) {
        const qty = parseFloat(numMatch[1]);
        const rest = numMatch[2].trim();
        if (rest.length >= 2 && qty > 0 && qty <= 50) return { quantity: qty, foodPart: rest };
    }
    const literalMatch = t.match(/^(un|una|uno|dos|tres|cuatro|cinco|media|medio|doble|triple)\s+(.+)$/i);
    if (literalMatch) {
        const qty = LITERAL_QUANTITIES[literalMatch[1].toLowerCase()];
        const rest = literalMatch[2].trim();
        if (qty != null && rest.length >= 2) return { quantity: qty, foodPart: rest };
    }
    return { quantity: 1, foodPart: t };
}

function parseFragment(frag: string): ParsedMealItem | null {
    let text = frag.trim();
    if (!text) return null;

    const defaultPortion: PortionPreset = 'medium';

    const { grams, cleaned: afterGrams } = extractGramsFromFragment(text);
    let working = afterGrams;

    const { method: cookingMethod, cleaned: afterCooking } = extractCookingMethodFromFragment(working);
    working = afterCooking;

    const { portion, cleaned: afterPortion } = extractPortionFromFragment(working);
    working = afterPortion;

    working = working.replace(/^\s*de\s+/i, '').replace(/\s+de\s+$/i, '').replace(/\s{2,}/g, ' ').trim();
    if (!working) return null;

    const { quantity, foodPart } = parseQuantityMultiplier(working);
    const foodName = foodPart || working;
    if (!foodName || foodName.length < 2) return null;

    const canonical = resolveToCanonical(foodName);
    const tag = findInDatabase(canonical) || canonical;
    if (tag.length < 2) return null;

    return {
        tag,
        quantity,
        amountGrams: grams,
        cookingMethod,
        portion: grams != null ? undefined : portion,
    };
}

function splitByListConnectors(description: string): string[] {
    const trimmed = description.trim();
    if (!trimmed) return [];
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
    const portion = items[0]?.portion ?? 'medium';
    const tags = items.map(i => i.tag);
    return { tags, portion, rawDescription };
}
