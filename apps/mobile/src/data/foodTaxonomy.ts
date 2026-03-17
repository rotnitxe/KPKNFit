// apps/mobile/src/data/foodTaxonomy.ts
// Taxonomía y Enriquecimiento de Alimentos — Ported from PWA
import type { FoodItem } from '../types/workout'; // Reusing workout types for basic Food shape

interface FoodCategoryRule {
    category: string;
    subcategory: string;
    tags: string[];
    keywords: string[];
}

const STOPWORDS = new Set(['de', 'del', 'la', 'el', 'los', 'las', 'con', 'sin', 'en', 'a', 'al', 'por']);

const CATEGORY_RULES: FoodCategoryRule[] = [
    { category: 'Proteinas', subcategory: 'Aves', tags: ['proteina', 'ave'], keywords: ['pollo', 'pavo', 'pechuga'] },
    { category: 'Proteinas', subcategory: 'Carnes rojas', tags: ['proteina', 'carne'], keywords: ['vacuno', 'res', 'posta', 'lomo', 'carne'] },
    { category: 'Proteinas', subcategory: 'Cerdo', tags: ['proteina', 'cerdo'], keywords: ['cerdo', 'jamon', 'tocino'] },
    { category: 'Proteinas', subcategory: 'Mar', tags: ['proteina', 'mar'], keywords: ['salmon', 'atun', 'jurel', 'marisco'] },
    { category: 'Proteinas', subcategory: 'Huevos', tags: ['proteina', 'huevo'], keywords: ['huevo', 'clara', 'yema'] },
    { category: 'Carbohidratos', subcategory: 'Cereales', tags: ['carbohidrato', 'cereal'], keywords: ['arroz', 'avena', 'quinoa'] },
    { category: 'Carbohidratos', subcategory: 'Pastas', tags: ['carbohidrato', 'pasta'], keywords: ['pasta', 'fideo', 'tallarin'] },
    { category: 'Carbohidratos', subcategory: 'Legumbres', tags: ['carbohidrato', 'legumbre'], keywords: ['lenteja', 'garbanzo', 'poroto'] },
    { category: 'Grasas', subcategory: 'Aceites', tags: ['grasa', 'aceite'], keywords: ['aceite', 'chia', 'linaza'] },
    { category: 'Vegetales', subcategory: 'Verduras', tags: ['vegetal', 'fibra'], keywords: ['lechuga', 'tomate', 'brocoli', 'espinaca'] },
    { category: 'Frutas', subcategory: 'Fruta fresca', tags: ['fruta', 'fibra'], keywords: ['manzana', 'platano', 'pera', 'kiwi'] },
];

export function normalizeFoodText(text: string): string {
    return (text || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^\p{Letter}\p{Number}\s/+-]+/gu, ' ').replace(/\s+/g, ' ').trim();
}

export function enrichFoodItem(food: any): any {
    const surface = normalizeFoodText(`${food.name} ${food.brand || ''}`).toLowerCase();
    const matchedRule = CATEGORY_RULES.find(rule => rule.keywords.some(k => surface.includes(k)));

    return {
        ...food,
        category: food.category || matchedRule?.category || 'Otros',
        subcategory: food.subcategory || matchedRule?.subcategory || 'General',
        tags: [...new Set([...(food.tags || []), ...(matchedRule?.tags || [])])],
    };
}

export function buildFoodSearchText(food: any): string {
    return `${food.name} ${food.brand || ''} ${food.category || ''} ${food.subcategory || ''} ${(food.tags || []).join(' ')} ${(food.searchAliases || []).join(' ')}`;
}
