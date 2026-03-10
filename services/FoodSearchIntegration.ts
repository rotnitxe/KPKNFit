/**
 * FoodSearchIntegration - Integración con foodSearchService.ts existente
 * 
 * Conecta el nuevo sistema FoodAI con la infraestructura de búsqueda
 * ya implementada en el proyecto.
 */

// Importar servicios existentes (ajustar paths según tu estructura)
// import { searchFoods, FoodItem } from './foodSearchService';
// import { normalizeFoodQuery } from './foodTaxonomy';

export interface FoodItem {
    id: string;
    name: string;
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
    fiber?: number;
    sodium?: number;
    category?: string;
    source?: 'usda' | 'off' | 'custom';
}

export interface FoodSearchResult {
    foodName: string;
    category: string;
    nutrition: {
        calories: number | null;
        protein: number | null;
        carbs: number | null;
        fats: number | null;
        fiber: number | null;
        sodium?: number | null;
    };
    confidence: number;
    matchedItemId?: string;
    source: 'database';
    warnings: string[];
}

/**
 * Buscar en la base de datos local de alimentos
 * 
 * Usa el foodSearchService.ts existente para encontrar matches
 */
export async function searchInDatabase(query: string): Promise<FoodSearchResult | null> {
    // Normalizar query (usando foodTaxonomy.ts si existe)
    const normalizedQuery = normalizeQuery(query);
    
    try {
        // Intentar con servicios existentes si están disponibles
        // const results = await searchFoods(normalizedQuery, { limit: 5 });
        
        // IMPLEMENTACIÓN TEMPORAL - Reemplazar con foodSearchService.ts real
        const results = await searchFoodsTemporal(normalizedQuery);
        
        if (!results || results.length === 0) {
            return null;
        }

        // Retornar el mejor match
        const bestMatch = results[0];
        
        return {
            foodName: bestMatch.name,
            category: bestMatch.category || 'desconocido',
            nutrition: {
                calories: bestMatch.calories || null,
                protein: bestMatch.protein || null,
                carbs: bestMatch.carbs || null,
                fats: bestMatch.fats || null,
                fiber: bestMatch.fiber || null,
                sodium: bestMatch.sodium || null,
            },
            confidence: calculateMatchConfidence(query, bestMatch),
            matchedItemId: bestMatch.id,
            source: 'database',
            warnings: [],
        };
    } catch (error) {
        console.warn('Error buscando en BD:', error);
        return null;
    }
}

/**
 * Calcular confianza del match basado en similitud de texto
 */
function calculateMatchConfidence(query: string, item: FoodItem): number {
    const queryLower = query.toLowerCase();
    const nameLower = item.name.toLowerCase();
    
    // Match exacto
    if (queryLower === nameLower) {
        return 0.95;
    }
    
    // Query está contenido en el nombre
    if (nameLower.includes(queryLower)) {
        return 0.85;
    }
    
    // Nombre está contenido en query
    if (queryLower.includes(nameLower)) {
        return 0.80;
    }
    
    // Match parcial (palabras clave)
    const queryWords = queryLower.split(/\s+/);
    const nameWords = nameLower.split(/\s+/);
    
    const matchingWords = queryWords.filter(word => 
        nameWords.some(nameWord => nameWord.includes(word))
    ).length;
    
    const matchRatio = matchingWords / Math.max(queryWords.length, nameWords.length);
    
    if (matchRatio >= 0.7) return 0.75;
    if (matchRatio >= 0.5) return 0.65;
    if (matchRatio >= 0.3) return 0.55;
    
    return 0.45;
}

/**
 * Normalizar query de búsqueda
 */
function normalizeQuery(query: string): string {
    return query
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '') // Remove accents
        .replace(/\s+/g, ' ')
        .trim();
}

/**
 * IMPLEMENTACIÓN TEMPORAL - Reemplazar con foodSearchService.ts real
 * 
 * Esta función simula la búsqueda hasta que se integre con el servicio real
 */
async function searchFoodsTemporal(query: string): Promise<FoodItem[]> {
    // Base de datos local de alimentos comunes
    const foodDatabase: FoodItem[] = [
        { id: 'usda_001', name: 'huevo', calories: 155, protein: 13, carbs: 1.1, fats: 11, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_002', name: 'huevo frito', calories: 196, protein: 13, carbs: 1.1, fats: 15, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_003', name: 'huevo duro', calories: 155, protein: 13, carbs: 1.1, fats: 11, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_004', name: 'huevo revuelto', calories: 180, protein: 12, carbs: 2, fats: 14, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_005', name: 'pollo', calories: 165, protein: 31, carbs: 0, fats: 3.6, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_006', name: 'pollo a la plancha', calories: 165, protein: 31, carbs: 0, fats: 3.6, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_007', name: 'arroz blanco', calories: 130, protein: 2.7, carbs: 28, fats: 0.3, fiber: 0.4, category: 'carbohidrato' },
        { id: 'usda_008', name: 'arroz graneado', calories: 130, protein: 2.7, carbs: 28, fats: 0.3, fiber: 0.4, category: 'carbohidrato' },
        { id: 'usda_009', name: 'pan blanco', calories: 265, protein: 9, carbs: 49, fats: 3.2, fiber: 2.7, category: 'carbohidrato' },
        { id: 'usda_010', name: 'pan integral', calories: 250, protein: 10, carbs: 45, fats: 4, fiber: 6, category: 'carbohidrato' },
        { id: 'usda_011', name: 'pan marraqueta', calories: 275, protein: 9, carbs: 55, fats: 2, fiber: 2, category: 'carbohidrato' },
        { id: 'usda_012', name: 'leche entera', calories: 61, protein: 3.2, carbs: 4.8, fats: 3.3, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_013', name: 'leche descremada', calories: 34, protein: 3.4, carbs: 5, fats: 0.1, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_014', name: 'café', calories: 2, protein: 0.3, carbs: 0, fats: 0, fiber: 0, category: 'bebida' },
        { id: 'usda_015', name: 'café con leche', calories: 60, protein: 3, carbs: 5, fats: 3, fiber: 0, category: 'bebida' },
        { id: 'usda_016', name: 'palta', calories: 160, protein: 2, carbs: 8.5, fats: 14.7, fiber: 6.7, category: 'grasa_saludable' },
        { id: 'usda_017', name: 'plátano', calories: 89, protein: 1.1, carbs: 23, fats: 0.3, fiber: 2.6, category: 'fruta' },
        { id: 'usda_018', name: 'manzana', calories: 52, protein: 0.3, carbs: 14, fats: 0.2, fiber: 2.4, category: 'fruta' },
        { id: 'usda_019', name: 'papa', calories: 77, protein: 2, carbs: 17, fats: 0.1, fiber: 2.2, category: 'carbohidrato' },
        { id: 'usda_020', name: 'papa frita', calories: 312, protein: 3.4, carbs: 41, fats: 15, fiber: 3.5, category: 'grasa_no_saludable' },
        { id: 'usda_021', name: 'ensalada', calories: 50, protein: 2, carbs: 8, fats: 1, fiber: 3, category: 'verdura' },
        { id: 'usda_022', name: 'carne de vacuno', calories: 250, protein: 26, carbs: 0, fats: 15, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_023', name: 'pescado', calories: 206, protein: 22, carbs: 0, fats: 12, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_024', name: 'salmón', calories: 208, protein: 20, carbs: 0, fats: 13, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_025', name: 'atún', calories: 132, protein: 29, carbs: 0, fats: 1, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_026', name: 'queso', calories: 402, protein: 25, carbs: 1.3, fats: 33, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_027', name: 'quesillo', calories: 105, protein: 12, carbs: 2, fats: 5, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_028', name: 'yogurt', calories: 59, protein: 10, carbs: 4, fats: 0.4, fiber: 0, category: 'proteina_animal' },
        { id: 'usda_029', name: 'avena', calories: 389, protein: 17, carbs: 66, fats: 7, fiber: 11, category: 'carbohidrato' },
        { id: 'usda_030', name: 'marraqueta', calories: 275, protein: 9, carbs: 55, fats: 2, fiber: 2, category: 'carbohidrato' },
    ];
    
    const queryLower = query.toLowerCase();
    
    // Buscar matches
    const matches = foodDatabase.filter(food => {
        const nameLower = food.name.toLowerCase();
        
        // Match exacto o contenido
        if (nameLower === queryLower || nameLower.includes(queryLower) || queryLower.includes(nameLower)) {
            return true;
        }
        
        // Match por palabras clave
        const queryWords = queryLower.split(/\s+/);
        const nameWords = nameLower.split(/\s+/);
        
        const hasMatchingWord = queryWords.some(word => 
            word.length > 2 && nameWords.some(nameWord => nameWord.includes(word))
        );
        
        return hasMatchingWord;
    });
    
    // Ordenar por relevancia (matches más largos primero)
    matches.sort((a, b) => {
        const aScore = a.name.toLowerCase().includes(queryLower) ? 2 : 1;
        const bScore = b.name.toLowerCase().includes(queryLower) ? 2 : 1;
        return bScore - aScore;
    });
    
    return matches.slice(0, 5);
}

/**
 * Verificar si hay conexión a internet para búsqueda en BD remota
 */
export function isOnline(): boolean {
    return navigator.onLine;
}

/**
 * Obtener alimento por ID (para cuando la IA retorna un matchedItemId)
 */
export async function getFoodById(id: string): Promise<FoodItem | null> {
    // IMPLEMENTACIÓN TEMPORAL
    // Reemplazar con llamada real a foodSearchService.ts
    
    const foodDatabase: Record<string, FoodItem> = {
        'usda_001': { id: 'usda_001', name: 'huevo', calories: 155, protein: 13, carbs: 1.1, fats: 11, fiber: 0, category: 'proteina_animal' },
        'usda_002': { id: 'usda_002', name: 'huevo frito', calories: 196, protein: 13, carbs: 1.1, fats: 15, fiber: 0, category: 'proteina_animal' },
        'usda_005': { id: 'usda_005', name: 'pollo', calories: 165, protein: 31, carbs: 0, fats: 3.6, fiber: 0, category: 'proteina_animal' },
        'usda_007': { id: 'usda_007', name: 'arroz blanco', calories: 130, protein: 2.7, carbs: 28, fats: 0.3, fiber: 0.4, category: 'carbohidrato' },
        'usda_009': { id: 'usda_009', name: 'pan blanco', calories: 265, protein: 9, carbs: 49, fats: 3.2, fiber: 2.7, category: 'carbohidrato' },
        'usda_012': { id: 'usda_012', name: 'leche entera', calories: 61, protein: 3.2, carbs: 4.8, fats: 3.3, fiber: 0, category: 'proteina_animal' },
    };
    
    return foodDatabase[id] || null;
}
