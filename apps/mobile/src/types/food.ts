export type FoodCategory = 
  | 'Proteínas' 
  | 'Carbohidratos' 
  | 'Grasas' 
  | 'Vegetales' 
  | 'Frutas' 
  | 'Lácteos' 
  | 'Snacks' 
  | 'Bebidas' 
  | 'Otros';

export interface FoodItem {
  id: string;
  name: string;
  brand?: string;
  category?: string;
  subcategory?: string;
  servingSize: number;
  servingUnit?: string;
  unit: string;
  calories: number;
  protein: number;
  carbs: number;
  fats: number;
  isCustom?: boolean;
  image?: string;
  fatBreakdown?: { saturated: number; monounsaturated: number; polyunsaturated: number; trans: number };
  carbBreakdown?: { fiber: number; sugar: number };
  proteinQuality?: { completeness: string; details: string };
  micronutrients?: { name: string; amount: number; unit: string }[];
  aiNotes?: string;
  tags?: string[];
  searchAliases?: string[];
  /** Si el alimento crudo/seco cambia de peso al cocinar. Factor: peso_cocido/peso_crudo (shrinks ~0.75) o peso_seco/peso_cocido (expands ~0.25) */
  cookingBehavior?: 'shrinks' | 'expands';
  cookingWeightFactor?: number;
}

export interface FoodFilterOptions {
  category?: FoodCategory;
  searchQuery?: string;
}
