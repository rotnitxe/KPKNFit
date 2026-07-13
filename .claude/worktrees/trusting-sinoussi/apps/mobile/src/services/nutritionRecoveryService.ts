import {
  computeNutritionRecoveryMultiplier,
  getMicronutrientDeficiencies,
  MICRONUTRIENT_RDA,
} from '@kpkn/shared-domain';

export type NutritionRecoveryInput = Parameters<typeof computeNutritionRecoveryMultiplier>[0];
export type NutritionRecoveryResult = ReturnType<typeof computeNutritionRecoveryMultiplier>;

export {
  computeNutritionRecoveryMultiplier,
  getMicronutrientDeficiencies,
  MICRONUTRIENT_RDA,
};
