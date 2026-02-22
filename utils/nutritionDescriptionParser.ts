// utils/nutritionDescriptionParser.ts
// Parser de descripciones de comida: tags, cantidades, método de cocción, porciones

import type { PortionPreset, ParsedMealItem, ParsedMealDescription, CookingMethod } from '../types';
import { resolveToCanonical } from '../data/foodSynonyms';
import { FOOD_DATABASE } from '../data/foodDatabase';
import { detectCookingMethod } from '../data/cookingMethodFactors';

const PORTION_PATTERNS: { pattern: RegExp; preset: PortionPreset }[] = [
    { pattern: /\b(plato\s+)?(muy\s+)?grande\b/i, preset: 'extra' },
    { pattern: /\b(plato\s+)?grande\b/i, preset: 'large' },
    { pattern: /\b(plato\s+)?mediano\b/i, preset: 'medium' },
    { pattern: /\b(plato\s+)?(muy\s+)?pequeño\b/i, preset: 'small' },
    { pattern: /\bporción\s+grande\b/i, preset: 'large' },
    { pattern: /\bporción\s+pequeña\b/i, preset: 'small' },
    { pattern: /\bmedia\s+(taza|porción|ración)\b/i, preset: 'small' },
    { pattern: /\bdoble\s+(porción|ración)\b/i, preset: 'extra' },
    { pattern: /\b1\s+unidad\b/i, preset: 'medium' },
    { pattern: /\bmedio\s+plato\b/i, preset: 'small' },
];

const CONNECTORS = /\s+(de\s+|con\s+|y\s+|,\s*|\+\s*)\s*/gi;

const LITERAL_QUANTITIES: Record<string, number> = {
    'un': 1, 'una': 1, 'uno': 1, 'dos': 2, 'tres': 3, 'cuatro': 4, 'cinco': 5,
    'media': 0.5, 'medio': 0.5, 'doble': 2, 'triple': 3,
};

function extractPortion(description: string): PortionPreset {
    for (const { pattern, preset } of PORTION_PATTERNS) {
        if (pattern.test(description)) return preset;
    }
    return 'medium';
}

function removePortionPhrases(text: string): string {
    return text
        .replace(/\b(plato\s+)?(muy\s+)?(grande|mediano|pequeño)\b/gi, '')
        .replace(/\bporción\s+(grande|pequeña|mediana)\b/gi, '')
        .replace(/\bmedia\s+(taza|porción|ración)\b/gi, '')
        .replace(/\bdoble\s+(porción|ración)\b/gi, '')
        .replace(/\b1\s+unidad\b/gi, '')
        .replace(/\bmedio\s+plato\b/gi, '')
        .replace(/\bde\s+$/i, '')
        .replace(/^\s*de\s+/i, '')
        .replace(/^\s+|\s+$/g, '')
        .replace(/\s{2,}/g, ' ');
}

function findInDatabase(term: string): string | null {
    const normalized = term.toLowerCase();
    const exact = FOOD_DATABASE.find(f => f.name.toLowerCase() === normalized);
    if (exact) return exact.name;
    const partial = FOOD_DATABASE.find(f =>
        f.name.toLowerCase().includes(normalized) || normalized.includes(f.name.toLowerCase())
    );
    return partial?.name ?? null;
}

/** Parsea un fragmento "2 panes" o "pan" -> { tag, quantity } */
function parseFragment(frag: string): { tag: string; qty: number } | null {
    const t = frag.trim().toLowerCase();
    if (!t || t.length < 2) return null;
    const numMatch = t.match(/^(\d+(?:\.\d+)?)\s*(?:x\s*)?(.+)$/);
    if (numMatch) {
        const qty = parseFloat(numMatch[1]);
        const food = numMatch[2].trim();
        const canonical = resolveToCanonical(food);
        const tag = findInDatabase(canonical) || canonical;
        if (tag.length > 1 && qty > 0) return { tag, qty };
    }
    const literalQty = LITERAL_QUANTITIES[t];
    if (literalQty != null) return null;
    const canonical = resolveToCanonical(t);
    const tag = findInDatabase(canonical) || canonical;
    if (tag.length > 1) return { tag, qty: 1 };
    return null;
}

/** Extrae items con cantidades: "2 panes con huevo" -> [{tag: "pan", qty: 2}, {tag: "huevo", qty: 1}] */
function extractItemsWithQuantities(description: string): ParsedMealItem[] {
    const portion = extractPortion(description);
    const cookingMethod = detectCookingMethod(description);
    const cleaned = removePortionPhrases(description);
    const items: ParsedMealItem[] = [];
    const seen = new Set<string>();

    const parts = cleaned.split(CONNECTORS).filter(Boolean);

    for (const p of parts) {
        const parsed = parseFragment(p);
        if (parsed) {
            const key = `${parsed.tag}:${parsed.qty}`;
            if (!seen.has(parsed.tag)) {
                seen.add(parsed.tag);
                items.push({ tag: parsed.tag, quantity: parsed.qty, cookingMethod, portion });
            } else {
                const idx = items.findIndex(i => i.tag === parsed.tag);
                if (idx >= 0) items[idx].quantity += parsed.qty;
            }
        }
    }

    if (items.length === 0 && cleaned.trim()) {
        const parsed = parseFragment(cleaned.trim());
        if (parsed) items.push({ tag: parsed.tag, quantity: parsed.qty, cookingMethod, portion });
    }

    return items;
}

export interface ParsedDescription {
    tags: string[];
    portion: PortionPreset;
    rawDescription: string;
}

/**
 * Parsea descripción en items con cantidades y método de cocción
 */
export function parseMealDescription(description: string): ParsedMealDescription {
    const trimmed = description.trim();
    if (!trimmed) return { items: [], rawDescription: '' };
    const items = extractItemsWithQuantities(trimmed);
    return { items, rawDescription: trimmed };
}

/**
 * Parsea una descripción de comida en tags (alimentos) y porción (legacy)
 */
export function parseNutritionDescription(description: string): ParsedDescription {
    const { items, rawDescription } = parseMealDescription(description);
    const portion = items[0]?.portion ?? 'medium';
    const tags = items.map(i => i.tag);
    return { tags, portion, rawDescription };
}
