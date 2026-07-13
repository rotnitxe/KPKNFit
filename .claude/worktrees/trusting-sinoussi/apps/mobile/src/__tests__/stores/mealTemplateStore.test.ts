jest.mock('../../services/mobileDomainStateService', () => ({
  getStoredMealTemplateSource: jest.fn(),
  readStoredMealTemplatesRaw: jest.fn(),
}));

import { getStoredMealTemplateSource, readStoredMealTemplatesRaw } from '../../services/mobileDomainStateService';
import { useMealTemplateStore } from '../../stores/mealTemplateStore';

describe('mealTemplateStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useMealTemplateStore.setState({
      status: 'idle',
      source: 'empty',
      templates: [],
      discardedCount: 0,
    });
  });

  it('preserves foods and food counts when hydrating from raw templates', async () => {
    (getStoredMealTemplateSource as jest.Mock).mockReturnValue('migration-fallback');
    (readStoredMealTemplatesRaw as jest.Mock).mockReturnValue([
      {
        id: 'tpl-1',
        name: 'Almuerzo KPKN',
        description: 'Arroz con pollo',
        createdAt: '2025-03-01T12:00:00.000Z',
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
      },
    ]);

    await useMealTemplateStore.getState().hydrateFromMigration();

    const state = useMealTemplateStore.getState();
    expect(state.source).toBe('migration-fallback');
    expect(state.status).toBe('ready');
    expect(state.templates).toHaveLength(1);
    expect(state.templates[0].foodCount).toBe(2);
    expect(state.templates[0].foods).toHaveLength(2);
    expect(state.templates[0].calories).toBe(525);
    expect(state.templates[0].protein).toBe(66);
  });

  it('falls back to stored summary totals when foods are missing', async () => {
    (getStoredMealTemplateSource as jest.Mock).mockReturnValue('migration-fallback');
    (readStoredMealTemplatesRaw as jest.Mock).mockReturnValue([
      {
        id: 'tpl-2',
        name: 'Resumen',
        description: 'Sin foods',
        totalCalories: 420,
        totalProtein: 28,
        totalCarbs: 54,
        totalFats: 12,
      },
    ]);

    await useMealTemplateStore.getState().hydrateFromMigration();

    const template = useMealTemplateStore.getState().templates[0];
    expect(template.foodCount).toBe(0);
    expect(template.foods).toHaveLength(0);
    expect(template.calories).toBe(420);
    expect(template.protein).toBe(28);
    expect(template.carbs).toBe(54);
    expect(template.fats).toBe(12);
  });
});
