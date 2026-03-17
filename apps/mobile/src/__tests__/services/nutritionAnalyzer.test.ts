import { analyzeNutritionDraft } from '../../services/nutritionAnalyzer';
import { localAiModule } from '../../modules/localAi';
import { analyzeNutritionDescriptionLocally } from '@kpkn/shared-domain';
import { useLocalAiDiagnosticsStore } from '../../stores/localAiDiagnosticsStore';
import type { LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';

jest.mock('../../modules/localAi', () => ({
  localAiModule: {
    analyzeNutritionDescription: jest.fn(),
  },
}));

jest.mock('@kpkn/shared-domain', () => ({
  analyzeNutritionDescriptionLocally: jest.fn(),
}));

jest.mock('../../stores/localAiDiagnosticsStore', () => ({
  useLocalAiDiagnosticsStore: {
    getState: jest.fn(),
  },
}));

const VALID_RESULT: LocalAiNutritionAnalysisResult = {
  items: [{
    rawText: '2 completos italianos',
    canonicalName: 'completo italiano',
    grams: 400,
    quantity: 2,
    calories: 680,
    protein: 22,
    carbs: 72,
    fats: 34,
    source: 'local-ai-estimate',
    confidence: 0.85,
    reviewRequired: false,
  }],
  overallConfidence: 0.85,
  containsEstimatedItems: true,
  requiresReview: false,
  elapsedMs: 1200,
  modelVersion: 'kpkn-food-fg270m-v1',
  engine: 'runtime',
};

describe('nutritionAnalyzer service', () => {
  const mockRecordRun = jest.fn();
  const mockRefreshStatus = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    (useLocalAiDiagnosticsStore.getState as jest.Mock).mockReturnValue({
      recordRun: mockRecordRun,
      refreshStatus: mockRefreshStatus,
    });
  });

  it('should return result from native module and record it', async () => {
    const request: any = { description: '2 completos' };
    (localAiModule.analyzeNutritionDescription as jest.Mock).mockResolvedValue(VALID_RESULT);

    const result = await analyzeNutritionDraft(request);

    expect(result).toEqual(VALID_RESULT);
    expect(mockRecordRun).toHaveBeenCalledWith(VALID_RESULT, request.description);
    expect(mockRefreshStatus).toHaveBeenCalled();
  });

  it('should use fallback when native module fails and record the error', async () => {
    const request: any = { description: '2 completos' };
    const error = new Error('Native failure');
    const fallbackResult: any = { items: [], engine: 'heuristics' };

    (localAiModule.analyzeNutritionDescription as jest.Mock).mockRejectedValue(error);
    (analyzeNutritionDescriptionLocally as jest.Mock).mockReturnValue(fallbackResult);

    const result = await analyzeNutritionDraft(request);

    expect(result).toEqual(fallbackResult);
    expect(analyzeNutritionDescriptionLocally).toHaveBeenCalledWith(request);
    expect(mockRecordRun).toHaveBeenCalledWith(
      expect.objectContaining({
        runtimeError: 'Native failure',
      }),
      request.description,
      'Native failure'
    );
  });

  it('should handle non-Error catch values gracefully', async () => {
    const request: any = { description: '2 completos' };
    const fallbackResult: any = { items: [], engine: 'heuristics' };

    (localAiModule.analyzeNutritionDescription as jest.Mock).mockRejectedValue('String error');
    (analyzeNutritionDescriptionLocally as jest.Mock).mockReturnValue(fallbackResult);

    await analyzeNutritionDraft(request);

    expect(mockRecordRun).toHaveBeenCalledWith(
      expect.objectContaining({
        runtimeError: 'El modulo local no pudo completar el analisis.',
      }),
      request.description,
      'El modulo local no pudo completar el analisis.'
    );
  });
});
