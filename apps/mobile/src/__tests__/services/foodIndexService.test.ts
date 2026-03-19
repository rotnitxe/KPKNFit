import { getFoodById, searchFoodIndex, searchFoods } from '../../services/foodIndexService';

describe('foodIndexService', () => {
  it('resolves canonical aliases with exact matching and high confidence', async () => {
    const result = await searchFoodIndex('completo italiano');

    expect(result.results[0]?.name).toContain('Completo Italiano');
    expect(result.matchType).toBe('exact');
    expect(result.bestConfidence).toBe('high');
    expect(result.canAutoSelect).toBe(true);
  });

  it('finds branded/local foods through alias search', async () => {
    const result = await searchFoodIndex('pan con palta y jamon');

    expect(result.results[0]?.name).toBe('Pan con Palta y Jamón');
    expect(result.decisionReason).toBeDefined();
    expect(result.bestScore).toBeGreaterThan(0.9);
  });

  it('supports the thin search wrapper and canonical lookup by id', () => {
    const foods = searchFoods('cazuela vacuno');

    expect(foods[0]?.name).toBe('Cazuela de Vacuno');
    expect(getFoodById('cl001')?.name).toBe('Pan con Palta y Jamón');
  });
});
