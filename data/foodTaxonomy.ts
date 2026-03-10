import type { FoodItem } from '../types';

interface FoodCategoryRule {
    category: string;
    subcategory: string;
    tags: string[];
    keywords: string[];
}

const STOPWORDS = new Set(['de', 'del', 'la', 'el', 'los', 'las', 'con', 'sin', 'en', 'a', 'al', 'por']);

const CATEGORY_RULES: FoodCategoryRule[] = [
    { category: 'Proteinas', subcategory: 'Aves', tags: ['proteina', 'ave'], keywords: ['pollo', 'pavo', 'pechuga', 'trutro', 'ala'] },
    { category: 'Proteinas', subcategory: 'Carnes rojas', tags: ['proteina', 'carne'], keywords: ['vacuno', 'res', 'posta', 'lomo', 'plateada', 'carne', 'hamburguesa', 'molida'] },
    { category: 'Proteinas', subcategory: 'Cerdo y embutidos', tags: ['proteina', 'cerdo', 'embutido'], keywords: ['cerdo', 'jamon', 'jamon', 'tocino', 'salame', 'salchicha', 'vienesa', 'longaniza', 'chorizo'] },
    { category: 'Proteinas', subcategory: 'Pescados y mariscos', tags: ['proteina', 'mar'], keywords: ['salmon', 'atun', 'jurel', 'reineta', 'merluza', 'trucha', 'camaron', 'marisco', 'pulpo'] },
    { category: 'Proteinas', subcategory: 'Huevos', tags: ['proteina', 'huevo'], keywords: ['huevo', 'clara', 'yema'] },
    { category: 'Proteinas', subcategory: 'Lacteos proteicos', tags: ['proteina', 'lacteo'], keywords: ['quesillo', 'cottage', 'skyr', 'griego', 'yogurt protein', 'whey', 'proteina'] },
    { category: 'Carbohidratos', subcategory: 'Arroces y cereales', tags: ['carbohidrato', 'cereal'], keywords: ['arroz', 'avena', 'quinoa', 'cuscus', 'mijo', 'granola'] },
    { category: 'Carbohidratos', subcategory: 'Pastas', tags: ['carbohidrato', 'pasta'], keywords: ['pasta', 'fideo', 'tallarin', 'espagueti', 'lasaña', 'lasana', 'ramen', 'noodle'] },
    { category: 'Carbohidratos', subcategory: 'Panes y tortillas', tags: ['carbohidrato', 'pan'], keywords: ['pan', 'hallulla', 'marraqueta', 'tortilla', 'wrap', 'pita', 'arepa'] },
    { category: 'Carbohidratos', subcategory: 'Legumbres', tags: ['carbohidrato', 'fibra', 'legumbre'], keywords: ['lenteja', 'garbanzo', 'poroto', 'frijol', 'lupino', 'haba'] },
    { category: 'Carbohidratos', subcategory: 'Tuberculos', tags: ['carbohidrato', 'tuberculo'], keywords: ['papa', 'camote', 'batata', 'yuca', 'choclo'] },
    { category: 'Grasas', subcategory: 'Aceites y semillas', tags: ['grasa', 'aceite'], keywords: ['aceite', 'chia', 'chia', 'girasol', 'linaza', 'sesamo', 'semilla'] },
    { category: 'Grasas', subcategory: 'Frutos secos y palta', tags: ['grasa', 'fruto_seco'], keywords: ['almendra', 'nuez', 'mani', 'mani', 'pistacho', 'castana', 'castaña', 'palta', 'aguacate'] },
    { category: 'Vegetales', subcategory: 'Verduras', tags: ['vegetal', 'fibra'], keywords: ['lechuga', 'tomate', 'cebolla', 'zanahoria', 'brocoli', 'brócoli', 'espinaca', 'zapallo', 'coliflor', 'pepino', 'betarraga', 'acelga', 'apio'] },
    { category: 'Frutas', subcategory: 'Fruta fresca', tags: ['fruta', 'fibra'], keywords: ['manzana', 'platano', 'plátano', 'banana', 'naranja', 'mandarina', 'pera', 'kiwi', 'uva', 'frutilla', 'fresa', 'mango', 'melon', 'melón', 'sandia', 'sandía', 'durazno'] },
    { category: 'Lacteos', subcategory: 'Leches y yogures', tags: ['lacteo'], keywords: ['leche', 'yogurt', 'yoghurt', 'queso', 'kefir', 'ricotta', 'mozzarella', 'cheddar'] },
    { category: 'Bebidas', subcategory: 'Liquidos', tags: ['bebida'], keywords: ['bebida', 'jugo', 'agua', 'cafe', 'café', 'te', 'té', 'isotonica', 'isotónica', 'cerveza', 'vino', 'piscola'] },
    { category: 'Snacks', subcategory: 'Snacks y dulces', tags: ['snack'], keywords: ['galleta', 'chocolate', 'alfajor', 'papas fritas', 'cheezels', 'super 8', 'triton', 'ramitas', 'muffin'] },
    { category: 'Preparaciones', subcategory: 'Platos preparados', tags: ['preparacion'], keywords: ['ensalada', 'cazuela', 'charquican', 'charquicán', 'porotos con rienda', 'completo', 'empanada', 'sushi', 'pizza', 'burrito', 'bowl', 'arroz con pollo', 'pastel de choclo'] },
    { category: 'Condimentos', subcategory: 'Salsas y extras', tags: ['condimento'], keywords: ['mayonesa', 'mostaza', 'ketchup', 'sriracha', 'salsa', 'miel', 'manjar'] },
    { category: 'Suplementos', subcategory: 'Suplementacion', tags: ['suplemento'], keywords: ['whey', 'creatina', 'protein', 'proteina', 'barra de proteina', 'pre workout', 'pre-entreno'] },
];

const ALIAS_MAP: Record<string, string[]> = {
    palta: ['aguacate', 'avocado'],
    porotos: ['frijoles', 'beans'],
    atun: ['atún', 'tuna'],
    mani: ['maní', 'peanut'],
    yogurt: ['yoghurt', 'yogur'],
    proteina: ['protein'],
    pollo: ['chicken'],
    carne: ['beef'],
    arroz: ['rice'],
    avena: ['oatmeal', 'oats'],
    tortilla: ['wrap'],
    queso: ['cheese'],
};

function unique(values: (string | undefined | null)[]): string[] {
    return [...new Set(values.filter((value): value is string => Boolean(value)).map(value => value.trim()).filter(Boolean))];
}

export function normalizeFoodText(text: string): string {
    return (text || '')
        .normalize('NFD')
        .replace(/\p{Diacritic}/gu, '')
        .toLowerCase()
        .replace(/\([^)]*\)/g, ' ')
        .replace(/[^\p{Letter}\p{Number}\s/+-]+/gu, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

function tokenize(text: string): string[] {
    return normalizeFoodText(text)
        .split(/\s+/)
        .filter(token => token.length > 1 && !STOPWORDS.has(token));
}

function inferFromRules(surface: string): FoodCategoryRule | null {
    for (const rule of CATEGORY_RULES) {
        if (rule.keywords.some(keyword => surface.includes(normalizeFoodText(keyword)))) {
            return rule;
        }
    }
    return null;
}

function inferMacroTags(food: FoodItem): string[] {
    const total = (food.protein * 4) + (food.carbs * 4) + (food.fats * 9);
    if (!total) return [];

    const proteinShare = (food.protein * 4) / total;
    const carbShare = (food.carbs * 4) / total;
    const fatShare = (food.fats * 9) / total;
    const tags: string[] = [];

    if (proteinShare >= 0.35) tags.push('alto_proteina');
    if (carbShare >= 0.45) tags.push('alto_carbohidrato');
    if (fatShare >= 0.45) tags.push('alto_grasa');
    if (food.micronutrients?.length) tags.push('micronutrientes');

    return tags;
}

function buildAliasVariants(tokens: string[]): string[] {
    const aliases: string[] = [];
    for (const token of tokens) {
        aliases.push(...(ALIAS_MAP[token] || []));
    }
    return aliases;
}

export function deriveFoodSemantics(food: FoodItem): Pick<FoodItem, 'category' | 'subcategory' | 'tags' | 'searchAliases'> {
    const surface = normalizeFoodText([food.name, food.brand, ...(food.tags || []), ...(food.searchAliases || [])].filter(Boolean).join(' '));
    const tokens = tokenize(surface);
    const matchedRule = inferFromRules(surface);

    const tags = unique([
        ...(food.tags || []),
        ...(matchedRule?.tags || []),
        ...inferMacroTags(food),
        ...(food.brand ? [`marca:${normalizeFoodText(food.brand).replace(/\s+/g, '_')}`] : []),
    ]);

    const searchAliases = unique([
        ...(food.searchAliases || []),
        ...buildAliasVariants(tokens),
        ...tokens,
    ]);

    return {
        category: food.category || matchedRule?.category || 'Otros',
        subcategory: food.subcategory || matchedRule?.subcategory || 'General',
        tags,
        searchAliases,
    };
}

export function enrichFoodItem(food: FoodItem): FoodItem {
    const semantics = deriveFoodSemantics(food);
    return {
        ...food,
        category: semantics.category,
        subcategory: semantics.subcategory,
        tags: semantics.tags,
        searchAliases: semantics.searchAliases,
    };
}

export function enrichFoodCatalog(foods: FoodItem[]): FoodItem[] {
    return foods.map(enrichFoodItem);
}

export function buildFoodSearchText(food: FoodItem): string {
    return [
        food.name,
        food.brand,
        food.category,
        food.subcategory,
        ...(food.tags || []),
        ...(food.searchAliases || []),
    ]
        .filter(Boolean)
        .join(' ');
}
