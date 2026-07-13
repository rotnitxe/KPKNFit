import { runLocalAiSmokeTest } from '../../services/localAiSmokeTestService';
import { localAiModule } from '../../modules/localAi';
import { LOCAL_CHILEAN_FOOD_CATALOG, analyzeNutritionDescriptionLocally } from '@kpkn/shared-domain';

jest.mock('../../modules/localAi', () => ({
  localAiModule: {
    analyzeNutritionDescription: jest.fn(),
  },
}));

jest.mock('@kpkn/shared-domain', () => ({
  analyzeNutritionDescriptionLocally: jest.fn(),
  LOCAL_CHILEAN_FOOD_CATALOG: [
    { id: '1', name: 'completo italiano', calories: 340, protein: 11, carbs: 36, fats: 17 }
  ],
}));

describe('localAiSmokeTest service', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should use default description when none is provided', async () => {
    (localAiModule.analyzeNutritionDescription as jest.Mock).mockResolvedValue({ items: [] });
    
    await runLocalAiSmokeTest();
    
    expect(localAiModule.analyzeNutritionDescription).toHaveBeenCalledWith(expect.objectContaining({
      description: '2 completos italianos',
      knownFoods: LOCAL_CHILEAN_FOOD_CATALOG,
    }));
  });

  it('should pass argument to analyzeNutritionDescription', async () => {
    (localAiModule.analyzeNutritionDescription as jest.Mock).mockResolvedValue({ items: [] });
    
    await runLocalAiSmokeTest('1 cazuela');
    
    expect(localAiModule.analyzeNutritionDescription).toHaveBeenCalledWith(expect.objectContaining({
      description: '1 cazuela',
    }));
  });

  it('should return fallback result when module fails', async () => {
    const error = new Error('Smoke fail');
    const fallbackResult: any = { items: [], engine: 'heuristics' };
    
    (localAiModule.analyzeNutritionDescription as jest.Mock).mockRejectedValue(error);
    (analyzeNutritionDescriptionLocally as jest.Mock).mockReturnValue(fallbackResult);
    
    const result = await runLocalAiSmokeTest();
    
    expect(result).toEqual({
      ...fallbackResult,
      runtimeError: 'Smoke fail',
    });
    expect(analyzeNutritionDescriptionLocally).toHaveBeenCalled();
  });

  it('should handle non-Error catch values in smoke test', async () => {
    const fallbackResult: any = { items: [], engine: 'heuristics' };
    
    (localAiModule.analyzeNutritionDescription as jest.Mock).mockRejectedValue('String Error');
    (analyzeNutritionDescriptionLocally as jest.Mock).mockReturnValue(fallbackResult);
    
    const result = await runLocalAiSmokeTest();
    
    expect(result.runtimeError).toBe('El motor local no pudo completar la prueba.');
  });
});
