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

export type CookingBehavior = 'shrinks' | 'expands';

export interface FoodItem {
  id: string;
  name: string;
  brand?: string;
  category?: string;
  subcategory?: string;
  portionSize?: string;
  servingSize?: number;
  servingUnit?: string;
  unit?: string;
  calories: number;
  protein: number;
  carbs: number;
  fats: number;
  fat?: number;
  isCustom?: boolean;
  image?: string;
  fatBreakdown?: { saturated: number; monounsaturated: number; polyunsaturated: number; trans: number };
  carbBreakdown?: { fiber: number; sugar: number };
  proteinQuality?: { completeness: string; details: string };
  micronutrients?: { name: string; amount: number; unit: string }[];
  aiNotes?: string;
  tags?: string[];
  aliases?: string[];
  searchAliases?: string[];
  cookingBehavior?: CookingBehavior;
  cookingWeightFactor?: number;
}

export interface FoodFilterOptions {
  category?: FoodCategory;
  searchQuery?: string;
}
