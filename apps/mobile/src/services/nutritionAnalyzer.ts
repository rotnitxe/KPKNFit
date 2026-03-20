import type { LocalAiNutritionAnalysisRequest, LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';
import { analyzeNutritionDescriptionLocally } from '@kpkn/shared-domain';
import { localAiModule } from '../modules/localAi';
import { useLocalAiDiagnosticsStore } from '../stores/localAiDiagnosticsStore';
import { recordNutritionAiTelemetry } from './nutritionAiTelemetryService';

export async function analyzeNutritionDraft(
  request: LocalAiNutritionAnalysisRequest,
): Promise<LocalAiNutritionAnalysisResult> {
  const startedAt = Date.now();
  try {
    const result = await localAiModule.analyzeNutritionDescription(request);
    useLocalAiDiagnosticsStore.getState().recordRun(result, request.description);
    void useLocalAiDiagnosticsStore.getState().refreshStatus();
    recordNutritionAiTelemetry({
      usedFallback: false,
      durationMs: Date.now() - startedAt,
      requestLength: request.description.length,
      itemCount: result.items?.length ?? 0,
      runtimeError: result.runtimeError ?? null,
    });
    return result;
  } catch (error) {
    console.warn('Fallo el runtime local de nutricion, se usa fallback.', error);
    const fallback = analyzeNutritionDescriptionLocally(request);
    const runtimeError = error instanceof Error ? error.message : 'El modulo local no pudo completar el analisis.';
    useLocalAiDiagnosticsStore.getState().recordRun(
      {
        ...fallback,
        runtimeError,
      },
      request.description,
      runtimeError,
    );
    void useLocalAiDiagnosticsStore.getState().refreshStatus();
    recordNutritionAiTelemetry({
      usedFallback: true,
      durationMs: Date.now() - startedAt,
      requestLength: request.description.length,
      itemCount: fallback.items?.length ?? 0,
      runtimeError,
    });
    return fallback;
  }
}
