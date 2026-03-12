/**
 * heuristicFoodCatalog.ts
 *
 * FUENTE ÚNICA DE VERDAD para los alimentos heurísticos del analizador de nutrición.
 *
 * Este archivo es la referencia canónica. Java (LocalAiModule.java) debe estar
 * alineado manualmente con estos valores. Mientras exista dualidad de ejecución
 * (el módulo nativo en Java también implementa heurísticas como fallback), hay
 * riesgo de divergencia.
 *
 * ── Estado de la deuda ────────────────────────────────────────────────────────
 * La heurística Java en LocalAiModule.java NO puede consumir este archivo en
 * tiempo de build — son runtimes completamente distintos. La solución completa
 * requeriría uno de:
 *   a) Generar el JSON de este catálogo y empaquetarlo como asset APK para que
 *      Java lo lea en runtime.
 *   b) Eliminar la heurística Java por completo, dejando solo el runtime LLM.
 *   c) Generar código Java desde este TS a través de un script de build.
 *
 * Por ahora, los valores están ALINEADOS manualmente y documentados aquí.
 * Cualquier cambio a este archivo DEBE reflejarse en LocalAiModule.java:82–95.
 * Esto queda como deuda técnica activa (B1 Parcial).
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * VALORES: nutritionPer100g — los totals se calculan como (nutritionPer100g.X * defaultGrams / 100)
 * Fuente de referencia para los valores: Tabla de composición química de alimentos, INTA Chile (2010)
 * y datos propios de formación KPKN.
 */

export interface HeuristicFood {
  key: string;
  canonicalName: string;
  aliases: string[];
  quantityKeyword: string;
  /** Peso de una porción estándar en gramos */
  defaultGrams: number;
  nutritionPer100g: {
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
  };
  preparation: string;
}

export const HEURISTIC_FOOD_CATALOG: HeuristicFood[] = [
  {
    key: 'completo-italiano',
    canonicalName: 'Completo Italiano',
    aliases: ['completo italiano', 'completos italianos', 'completo', 'completos'],
    quantityKeyword: 'completo',
    defaultGrams: 250,
    // 250g total: pan 100g + vienesa 80g + palta+mayo+tomate 70g
    // kcal totales ~550 → por 100g = 220
    nutritionPer100g: { calories: 220, protein: 6.0, carbs: 18.0, fats: 14.0 },
    preparation: 'armado',
  },
  {
    key: 'pastel-de-choclo',
    canonicalName: 'Pastel de Choclo',
    aliases: ['pastel de choclo', 'pastel choclo'],
    quantityKeyword: 'plato',
    defaultGrams: 350,
    // ~600 kcal totales → por 100g = 171
    nutritionPer100g: { calories: 171, protein: 7.1, carbs: 17.1, fats: 8.6 },
    preparation: 'horno',
  },
  {
    key: 'cazuela-de-vacuno',
    canonicalName: 'Cazuela de Vacuno',
    aliases: ['cazuela de vacuno', 'cazuela vacuno', 'cazuela'],
    quantityKeyword: 'plato',
    defaultGrams: 500,
    // ~480 kcal totales → por 100g = 96
    nutritionPer100g: { calories: 96, protein: 7.0, carbs: 8.0, fats: 4.0 },
    preparation: 'cocido',
  },
  {
    key: 'porotos-granados',
    canonicalName: 'Porotos Granados',
    aliases: ['porotos granados', 'porotos verano'],
    quantityKeyword: 'plato',
    defaultGrams: 250,
    nutritionPer100g: { calories: 116, protein: 5.6, carbs: 18.8, fats: 2.0 },
    preparation: 'cocido',
  },
  {
    key: 'charquican',
    canonicalName: 'Charquicán',
    aliases: ['charquican', 'charquicán'],
    quantityKeyword: 'plato',
    defaultGrams: 350,
    nutritionPer100g: { calories: 111, protein: 5.1, carbs: 14.0, fats: 3.7 },
    preparation: 'guiso',
  },
  {
    key: 'pan-con-palta-y-jamon',
    canonicalName: 'Pan con Palta y Jamón',
    aliases: ['pan con palta y jamon', 'pan con palta y jamón', 'sandwich de palta y jamon'],
    quantityKeyword: 'unidad',
    defaultGrams: 160,
    // 160g: 228 kcal → por 100g ≈ 143 (ligeramente distinto a la versión anterior por inconsistencia corregida)
    nutritionPer100g: { calories: 143, protein: 8.8, carbs: 19.4, fats: 7.8 },
    preparation: 'armado',
  },
  {
    key: 'batido-proteina-platano-leche',
    canonicalName: 'Batido de Proteína con Plátano y Leche',
    aliases: ['batido con platano y proteina', 'batido con plátano y proteína', 'batido con proteina y leche', 'batido con proteína y leche'],
    quantityKeyword: 'vaso',
    defaultGrams: 450,
    // 450ml: 340 kcal → por 100ml ≈ 76
    nutritionPer100g: { calories: 76, protein: 6.9, carbs: 7.6, fats: 1.8 },
    preparation: 'licuado',
  },
  {
    key: 'empanada-de-pino',
    canonicalName: 'Empanada de Pino',
    aliases: ['empanada de pino', 'empanada pino', 'empanada', 'empanadas'],
    quantityKeyword: 'unidad',
    defaultGrams: 200,
    // 200g: ~465 kcal → por 100g ≈ 233
    nutritionPer100g: { calories: 233, protein: 8.5, carbs: 25.0, fats: 11.0 },
    preparation: 'horno',
  },
  {
    key: 'arroz-cocido',
    canonicalName: 'Arroz',
    aliases: ['arroz', 'arroz blanco', 'arroz cocido', 'arroz graneado'],
    quantityKeyword: 'plato',
    defaultGrams: 200,
    nutritionPer100g: { calories: 130, protein: 2.7, carbs: 28.2, fats: 0.3 },
    preparation: 'cocido',
  },
  {
    key: 'pollo-cocido',
    canonicalName: 'Pollo',
    aliases: ['pollo', 'pechuga de pollo', 'pechuga', 'pollo a la plancha'],
    quantityKeyword: 'porcion',
    defaultGrams: 150,
    nutritionPer100g: { calories: 165, protein: 31.0, carbs: 0.0, fats: 3.6 },
    preparation: 'plancha',
  },
  {
    key: 'huevo',
    canonicalName: 'Huevo',
    aliases: ['huevo', 'huevos', 'huevo frito', 'huevo revuelto', 'huevo duro'],
    quantityKeyword: 'unidad',
    defaultGrams: 50,
    nutritionPer100g: { calories: 155, protein: 13.0, carbs: 1.1, fats: 11.0 },
    preparation: 'cocido',
  },
  {
    key: 'pan-marraqueta',
    canonicalName: 'Pan (Marraqueta)',
    aliases: ['pan', 'marraqueta', 'pan frances', 'hallulla', 'pan amasado'],
    quantityKeyword: 'unidad',
    defaultGrams: 100,
    nutritionPer100g: { calories: 270, protein: 8.0, carbs: 52.0, fats: 2.5 },
    preparation: 'horneado',
  },
];
