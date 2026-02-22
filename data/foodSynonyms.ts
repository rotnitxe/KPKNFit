// data/foodSynonyms.ts
// Sinónimos para el parser de descripciones de comida
// Formato: canonical name -> [alias1, alias2, ...]

export const FOOD_SYNONYMS: Record<string, string[]> = {
    // Pastas y carbohidratos
    'fideos': ['pasta', 'tallarines', 'espagueti', 'espaguetis', 'fideo', 'pastas'],
    'arroz blanco': ['arroz', 'arroz cocido'],
    'arroz integral': ['arroz integral cocido'],
    'salsa de tomate': ['salsa', 'salsa napolitana', 'salsa italiana', 'salsa roja'],
    'carne molida': ['carne picada', 'ground beef', 'vacuno molido', 'carne molida 90/10'],
    'papa': ['papas', 'patata', 'patatas'],
    'quinoa': ['quinua'],
    'avena': ['avena en hojuelas', 'avena cruda'],
    'pan blanco': ['pan'],
    'pan integral': ['pan integral de molde'],
    'tortilla de trigo': ['tortilla', 'tortilla de harina'],

    // Proteínas
    'pechuga de pollo': ['pollo', 'pechuga', 'pollo cocido', 'pollo crudo'],
    'pechuga de pollo (cocida)': ['pollo cocido', 'pechuga cocida'],
    'pechuga de pollo (cruda)': ['pollo crudo', 'pechuga cruda'],
    'huevo entero': ['huevo', 'huevos', 'huevo cocido'],
    'clara de huevo': ['claras', 'clara'],
    'salmón': ['salmon', 'salmón crudo'],
    'atún en lata': ['atún', 'atun', 'atún en agua'],
    'carne molida 90/10 (cocida)': ['carne molida cocida', 'vacuno molido cocido'],
    'posta rosada': ['postas', 'postas rosadas'],
    'lomo de cerdo': ['cerdo', 'lomo'],
    'pechuga de pavo': ['pavo', 'pechuga pavo'],

    // Lácteos
    'leche entera': ['leche'],
    'leche descremada': ['leche light', 'leche baja en grasa'],
    'yogurt griego natural': ['yogurt griego', 'yogur griego', 'yogurt natural'],
    'queso cottage': ['cottage', 'cottage cheese'],
    'palta': ['aguacate', 'paltas', 'avocado'],

    // Legumbres y vegetales
    'lentejas': ['lenteja'],
    'garbanzos': ['garbanzo'],
    'porotos negros': ['porotos', 'frijoles negros', 'black beans'],
    'brócoli': ['brocoli', 'broccoli'],
    'espinaca': ['espinacas'],
    'tomate': ['tomates'],
    'cebolla': ['cebollas'],
    'zanahoria': ['zanahorias'],
    'champiñones': ['champinones', 'hongos', 'setas'],

    // Frutas
    'manzana': ['manzanas'],
    'plátano': ['platano', 'banana', 'bananas', 'plátanos'],
    'naranja': ['naranjas'],
    'frutillas': ['fresas', 'strawberries'],

    // Grasas y aceites
    'aceite de oliva': ['aceite', 'oliva', 'olive oil'],
    'mantequilla de maní': ['mantequilla mani', 'peanut butter', 'crema de maní'],
    'almendras': ['almendra'],
    'nueces': ['nuez', 'walnuts'],

    // Comidas preparadas
    'cazuela de vacuno': ['cazuela', 'cazuela vacuno'],
    'completo italiano': ['completo', 'hot dog italiano'],
    'pastel de choclo': ['pastel choclo', 'pastel de choclo'],
    'empanada de pino': ['empanada', 'empanada pino', 'empanada de carne'],
    'lasaña de carne': ['lasaña', 'lasagna', 'lasagna de carne'],
    'sopa de lentejas': ['sopa lentejas'],
    'ensalada césar c/ pollo': ['ensalada cesar', 'cesar c/ pollo', 'ensalada cesar con pollo'],
};

/**
 * Resuelve un término a su nombre canónico (si existe en sinónimos)
 */
export function resolveToCanonical(term: string): string {
    const normalized = term.trim().toLowerCase();
    for (const [canonical, aliases] of Object.entries(FOOD_SYNONYMS)) {
        if (canonical.toLowerCase() === normalized) return canonical;
        if (aliases.some(a => a.toLowerCase() === normalized)) return canonical;
    }
    return term.trim();
}
