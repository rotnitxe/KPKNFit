import { buildNutritionAnalysisFromFood, buildNutritionAnalysisFromTemplate, buildSavedNutritionEntryFromAnalysis } from '../../services/nutritionEntryBuilders';
import type { FoodItem } from '../../types/food';
import type { MealTemplateSummary } from '../../stores/mealTemplateStore';

describe('nutritionEntryBuilders', () => {
  const arroz: FoodItem = {
    id: 'food-arroz',
    name: 'Arroz',
    calories: 130,
    protein: 2.7,
    carbs: 28.2,
    fats: 0.3,
    servingSize: 100,
    servingUnit: 'g',
    unit: 'g',
  };

  const pollo: FoodItem = {
    id: 'food-pollo',
    name: 'Pollo',
    calories: 165,
    protein: 31,
    carbs: 0,
    fats: 3.6,
    servingSize: 100,
    servingUnit: 'g',
    unit: 'g',
  };

  const template: MealTemplateSummary = {
    id: 'tpl-1',
    name: 'Almuerzo KPKN',
    description: 'Arroz con pollo',
    foods: [
      {
        id: 'log-arroz',
        foodName: 'Arroz',
        amount: 150,
        unit: 'g',
        calories: 195,
        protein: 4,
        carbs: 42,
        fats: 0.5,
      },
      {
        id: 'log-pollo',
        foodName: 'Pollo',
        amount: 200,
        unit: 'g',
        calories: 330,
        protein: 62,
        carbs: 0,
        fats: 7.2,
      },
    ],
    foodCount: 2,
    calories: 525,
    protein: 66,
    carbs: 42,
    fats: 7.7,
    quickDescription: 'Arroz, pollo',
    createdAt: '2025-03-01T12:00:00.000Z',
  };

  it('builds a scaled analysis from a food item', () => {
    const analysis = buildNutritionAnalysisFromFood(arroz, 2, '2 porciones de arroz');

    expect(analysis.items).toHaveLength(1);
    expect(analysis.items[0].canonicalName).toBe('Arroz');
    expect(analysis.items[0].calories).toBe(260);
    expect(analysis.items[0].grams).toBe(200);
    expect(analysis.items[0].quantity).toBe(2);
  });

  it('keeps template foods in the analysis so totals survive round-trips', () => {
    const analysis = buildNutritionAnalysisFromTemplate(template);

    expect(analysis.items).toHaveLength(2);
    expect(analysis.items[0].canonicalName).toBe('Arroz');
    expect(analysis.items[1].canonicalName).toBe('Pollo');

    const totals = analysis.items.reduce((acc, item) => ({
      calories: acc.calories + item.calories,
      protein: acc.protein + item.protein,
      carbs: acc.carbs + item.carbs,
      fats: acc.fats + item.fats,
    }), { calories: 0, protein: 0, carbs: 0, fats: 0 });

    expect(totals.calories).toBe(525);
    expect(totals.protein).toBe(66);
    expect(totals.carbs).toBe(42);
    expect(totals.fats).toBeCloseTo(7.7, 1);
  });

  it('builds a persisted nutrition entry with consistent totals', () => {
    const analysis = buildNutritionAnalysisFromFood(pollo, 1, 'Pollo a la plancha');
    const entry = buildSavedNutritionEntryFromAnalysis('Pollo a la plancha', analysis, {
      mealType: 'lunch',
      logDate: '2025-03-04',
    });

    expect(entry.description).toBe('Pollo a la plancha');
    expect(entry.loggedDate).toBe('2025-03-04');
    expect(entry.mealType).toBe('lunch');
    expect(entry.totals.calories).toBe(165);
    expect(entry.totals.protein).toBe(31);
    expect(entry.analysis.items[0].canonicalName).toBe('Pollo');
  });
});
