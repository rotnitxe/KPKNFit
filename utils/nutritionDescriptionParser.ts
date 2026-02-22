// utils/nutritionDescriptionParser.ts
// Parser automático de descripciones de comida a tags y porción

import type { PortionPreset } from '../types';
import { resolveToCanonical } from '../data/foodSynonyms';
import { FOOD_DATABASE } from '../data/foodDatabase';

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

/**
 * Extrae el preset de porción de la descripción
 */
function extractPortion(description: string): PortionPreset {
    for (const { pattern, preset } of PORTION_PATTERNS) {
        if (pattern.test(description)) return preset;
    }
    return 'medium';
}

/**
 * Elimina patrones de porción del texto para extraer solo alimentos
 */
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

/**
 * Tokeniza la descripción en candidatos de alimentos
 */
function tokenize(description: string): string[] {
    const cleaned = removePortionPhrases(description);
    const parts = cleaned.split(CONNECTORS).filter(Boolean);
    const tokens: string[] = [];
    for (const p of parts) {
        const t = p.trim().toLowerCase();
        if (t && t.length > 1 && !/^(de|con|y|la|el|los|las|un|una|unos|unas)$/.test(t)) {
            tokens.push(t);
        }
    }
    if (tokens.length === 0 && cleaned.trim()) {
        tokens.push(cleaned.trim());
    }
    return tokens;
}

/**
 * Busca si un término existe en la base de datos (por nombre)
 */
function findInDatabase(term: string): string | null {
    const normalized = term.toLowerCase();
    const dbNames = FOOD_DATABASE.map(f => f.name.toLowerCase());
    const exact = FOOD_DATABASE.find(f => f.name.toLowerCase() === normalized);
    if (exact) return exact.name;
    const partial = FOOD_DATABASE.find(f => 
        f.name.toLowerCase().includes(normalized) || normalized.includes(f.name.toLowerCase())
    );
    if (partial) return partial.name;
    return null;
}

export interface ParsedDescription {
    tags: string[];
    portion: PortionPreset;
    rawDescription: string;
}

/**
 * Parsea una descripción de comida en tags (alimentos) y porción
 * Ej: "plato grande de fideos con salsa y carne molida" -> { tags: ['fideos', 'salsa de tomate', 'carne molida'], portion: 'large' }
 */
export function parseNutritionDescription(description: string): ParsedDescription {
    const trimmed = description.trim();
    if (!trimmed) return { tags: [], portion: 'medium', rawDescription: '' };

    const portion = extractPortion(trimmed);
    const tokens = tokenize(trimmed);
    const tags: string[] = [];
    const seen = new Set<string>();

    for (const token of tokens) {
        const canonical = resolveToCanonical(token);
        const inDb = findInDatabase(canonical);
        const finalName = inDb || canonical;
        const key = finalName.toLowerCase();
        if (!seen.has(key) && finalName.length > 1) {
            seen.add(key);
            tags.push(finalName);
        }
    }

    return { tags, portion, rawDescription: trimmed };
}
