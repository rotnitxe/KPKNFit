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
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
  portionSize: string; // e.g. "100g", "1 unidad (50g)"
  category: FoodCategory;
  tags: string[];
  aliases: string[]; // For better search (e.g. "palta" for "aguacate")
}

export interface FoodFilterOptions {
  category?: FoodCategory;
  searchQuery?: string;
}
