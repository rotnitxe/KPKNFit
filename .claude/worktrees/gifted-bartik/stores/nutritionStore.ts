import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { createPersistMultiKeyStorage } from './storageAdapter';
import { FOOD_DATABASE } from '../data/foodDatabase';
import type { NutritionLog, PantryItem, FoodItem, AINutritionPlan, NutritionPlan } from '../types';

type Updater<T> = T | ((prev: T) => T);

interface NutritionStoreState {
    nutritionLogs: NutritionLog[];
    pantryItems: PantryItem[];
    foodDatabase: FoodItem[];
    aiNutritionPlan: AINutritionPlan | null;
    nutritionPlans: NutritionPlan[];
    activeNutritionPlanId: string | null;
    _hasHydrated: boolean;

    setNutritionLogs: (updater: Updater<NutritionLog[]>) => void;
    setPantryItems: (updater: Updater<PantryItem[]>) => void;
    setFoodDatabase: (updater: Updater<FoodItem[]>) => void;
    setAiNutritionPlan: (updater: Updater<AINutritionPlan | null>) => void;
    setNutritionPlans: (updater: Updater<NutritionPlan[]>) => void;
    setActiveNutritionPlanId: (updater: Updater<string | null>) => void;
}

export const useNutritionStore = create<NutritionStoreState>()(
    persist(
        immer((set) => ({
            nutritionLogs: [],
            pantryItems: [],
            foodDatabase: FOOD_DATABASE,
            aiNutritionPlan: null,
            nutritionPlans: [],
            activeNutritionPlanId: null,
            _hasHydrated: false,

            setNutritionLogs: (updater) => set((state) => {
                state.nutritionLogs = typeof updater === 'function'
                    ? (updater as (prev: NutritionLog[]) => NutritionLog[])(state.nutritionLogs)
                    : updater;
            }),

            setPantryItems: (updater) => set((state) => {
                state.pantryItems = typeof updater === 'function'
                    ? (updater as (prev: PantryItem[]) => PantryItem[])(state.pantryItems)
                    : updater;
            }),

            setFoodDatabase: (updater) => set((state) => {
                state.foodDatabase = typeof updater === 'function'
                    ? (updater as (prev: FoodItem[]) => FoodItem[])(state.foodDatabase)
                    : updater;
            }),

            setAiNutritionPlan: (updater) => set((state) => {
                state.aiNutritionPlan = typeof updater === 'function'
                    ? (updater as (prev: AINutritionPlan | null) => AINutritionPlan | null)(state.aiNutritionPlan)
                    : updater;
            }),

            setNutritionPlans: (updater) => set((state) => {
                state.nutritionPlans = typeof updater === 'function'
                    ? (updater as (prev: NutritionPlan[]) => NutritionPlan[])(state.nutritionPlans)
                    : updater;
            }),

            setActiveNutritionPlanId: (updater) => set((state) => {
                state.activeNutritionPlanId = typeof updater === 'function'
                    ? (updater as (prev: string | null) => string | null)(state.activeNutritionPlanId)
                    : updater;
            }),
        })),
        {
            name: 'kpkn-nutrition-store',
            storage: createPersistMultiKeyStorage({
                nutritionLogs: 'nutrition-logs',
                pantryItems: 'yourprime-pantry-items',
                foodDatabase: 'yourprime-food-database',
                aiNutritionPlan: 'yourprime-ai-nutrition-plan',
                nutritionPlans: 'nutrition-plans',
                activeNutritionPlanId: 'active-nutrition-plan-id',
            }),
            partialize: (state) => ({
                nutritionLogs: state.nutritionLogs,
                pantryItems: state.pantryItems,
                foodDatabase: state.foodDatabase,
                aiNutritionPlan: state.aiNutritionPlan,
                nutritionPlans: state.nutritionPlans,
                activeNutritionPlanId: state.activeNutritionPlanId,
            }),
            onRehydrateStorage: () => () => {
                useNutritionStore.setState({ _hasHydrated: true });
            },
        }
    )
);
