/**
 * Servicio de integración Nutrición → Recuperación (AUGE)
 * Lógica NO LINEAL: el superávit no siempre acelera la recuperación.
 * Considera: calorías, proteína, balance de macros y estrés.
 */

import type { NutritionLog, Settings } from '../types';
import {
    computeNutritionRecoveryMultiplier,
    getMicronutrientDeficiencies,
    MICRONUTRIENT_RDA
} from '@kpkn/shared-domain';
import type { NutritionRecoveryInput, NutritionRecoveryResult } from '@kpkn/shared-types';

export type { NutritionRecoveryInput, NutritionRecoveryResult };
export { computeNutritionRecoveryMultiplier, getMicronutrientDeficiencies, MICRONUTRIENT_RDA };
