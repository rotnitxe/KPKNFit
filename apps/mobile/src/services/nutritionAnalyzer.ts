import type { LocalAiNutritionAnalysisRequest, LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';
import { analyzeNutritionDescriptionLocally } from '@kpkn/shared-domain';
import { localAiModule } from '../modules/localAi';
import { useLocalAiDiagnosticsStore } from '../stores/localAiDiagnosticsStore';

export async function analyzeNutritionDraft(
  request: LocalAiNutritionAnalysisRequest,
): Promise<LocalAiNutritionAnalysisResult> {
  try {
    const result = await localAiModule.analyzeNutritionDescription(request);
    useLocalAiDiagnosticsStore.getState().recordRun(result, request.description);
    void useLocalAiDiagnosticsStore.getState().refreshStatus();
    return result;
  } catch (error) {
    console.warn('Fallo el runtime local de nutricion, se usa fallback.', error);
    const fallback = analyzeNutritionDescriptionLocally(request);
    useLocalAiDiagnosticsStore.getState().recordRun(
      {
        ...fallback,
        runtimeError: error instanceof Error ? error.message : 'El modulo local no pudo completar el analisis.',
      },
      request.description,
      error instanceof Error ? error.message : 'El modulo local no pudo completar el analisis.',
    );
    void useLocalAiDiagnosticsStore.getState().refreshStatus();
    return fallback;
  }
}
