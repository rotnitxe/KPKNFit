import { LOCAL_CHILEAN_FOOD_CATALOG, analyzeNutritionDescriptionLocally } from '@kpkn/shared-domain';
import { localAiModule } from '../modules/localAi';

const DEFAULT_SMOKE_DESCRIPTION = '2 completos italianos';

export async function runLocalAiSmokeTest(description = DEFAULT_SMOKE_DESCRIPTION) {
  const request = {
    description,
    locale: 'es-CL' as const,
    schemaVersion: 'rn-smoke-v1',
    knownFoods: LOCAL_CHILEAN_FOOD_CATALOG,
    userMemory: [description],
  };

  try {
    return await localAiModule.analyzeNutritionDescription(request);
  } catch (error) {
    const fallback = analyzeNutritionDescriptionLocally(request);
    return {
      ...fallback,
      runtimeError: error instanceof Error ? error.message : 'El motor local no pudo completar la prueba.',
    };
  }
}
