// services/alchemyEngine.ts
// Motor centralizado de Alquimia Nutricional V2: Ultra-Flexible y Fiel.

import type { FoodItem, LoggedFood, PortionPreset } from '../types';
import type { TagWithFood } from '../components/nutrition/RegisterFoodDrawer';
import { PORTION_MULTIPLIERS } from '../types';
import { getCookingFactor, getEffectiveAmountForMacros } from '../data/cookingMethodFactors';

export class AlchemyEngine {
    /**
     * Calcula el LoggedFood final aplicando todos los modificadores en orden.
     */
    static calculateLoggedFood(item: FoodItem, tag: TagWithFood): LoggedFood {
        // 1. Determinar el peso base según porción o gramos explícitos
        const portionKey = tag.portion as PortionPreset;
        const baseAmount = tag.amountGrams != null
            ? tag.amountGrams * tag.quantity
            : item.servingSize * (PORTION_MULTIPLIERS[portionKey] || 1) * tag.quantity;

        // 2. Aplicar factores de peso base (Cocción: shrinks/expands)
        let effectiveAmount = getEffectiveAmountForMacros(baseAmount, item, tag.cookingMethod);

        // 3. Factores de densidad calórica (Cocción: frito, plancha, etc)
        const cookFactor = tag.cookingMethod ? getCookingFactor(tag.cookingMethod) : { caloriesFactor: 1, fatsFactor: 1 };

        // 4. Modificadores de Preparación (Desperdicio)
        if (tag.preparationModifiers?.includes('con_hueso')) {
            effectiveAmount *= 0.7; // Factor estimado de desperdicio del 30%
        }

        // 5. Cálculo de macros base proporcionales
        const ratio = effectiveAmount / item.servingSize;
        let calories = item.calories * ratio * cookFactor.caloriesFactor;
        let protein = item.protein * ratio;
        let carbs = item.carbs * ratio;
        let fats = item.fats * ratio * cookFactor.fatsFactor;
        let finalAmount = baseAmount;

        // 6. Alquimia Anatómica (Modificadores de partes)
        if (tag.anatomicalModifiers?.includes('sin_miga')) {
            carbs *= 0.6;
            finalAmount *= 0.8;
        }
        if (tag.anatomicalModifiers?.includes('sin_yema') || tag.anatomicalModifiers?.includes('solo_claras')) {
            fats *= 0.1;
            calories = (protein * 4) + (fats * 9) + (carbs * 4);
        }
        if (tag.anatomicalModifiers?.includes('sin_piel')) {
            fats *= 0.5;
            calories = (protein * 4) + (fats * 9) + (carbs * 4);
        }

        // 7. Alquimia de Estado (En almíbar, Polvo, etc)
        if (tag.stateModifiers?.includes('en_almibar')) {
            carbs += (effectiveAmount * 0.15);
            calories = (protein * 4) + (carbs * 4) + (fats * 9);
        }

        // 8. Alquimia Heurística (Light, Descremado)
        if (tag.heuristicModifiers?.includes('descremado')) {
            fats = 0;
            calories = (protein * 4) + (carbs * 4);
        }
        if (tag.heuristicModifiers?.includes('light')) {
            calories *= 0.7;
            fats *= 0.7;
        }

        // 9. Modificadores de Composición
        if (tag.compositionModifiers?.includes('sin_grasa')) {
            fats = 0;
            calories = (protein * 4) + (carbs * 4);
        }

        // 10. Aplicar Overrides finales
        return {
            id: crypto.randomUUID(),
            foodName: item.name,
            amount: Math.round(finalAmount * 10) / 10,
            unit: item.unit,
            calories: Math.round(tag.macroOverrides?.calories ?? calories),
            protein: Math.round((tag.macroOverrides?.protein ?? protein) * 10) / 10,
            carbs: Math.round((tag.macroOverrides?.carbs ?? carbs) * 10) / 10,
            fats: Math.round((tag.macroOverrides?.fats ?? fats) * 10) / 10,
            portionPreset: tag.portion,
            quantity: tag.quantity,
            cookingMethod: tag.cookingMethod,
        };
    }
}
