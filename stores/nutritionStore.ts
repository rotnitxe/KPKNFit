import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { createMultiKeyStorage } from './storageAdapter';
import { FOOD_DATABASE } from '../data/foodDatabase';
import type { NutritionLog, PantryItem, FoodItem, AINutritionPlan } from '../types';

type Updater<T> = T | ((prev: T) => T);

interface NutritionStoreState {
    nutritionLogs: NutritionLog[];
    pantryItems: PantryItem[];
    foodDatabase: FoodItem[];
    aiNutritionPlan: AINutritionPlan | null;
    _hasHydrated: boolean;

    setNutritionLogs: (updater: Updater<NutritionLog[]>) => void;
    setPantryItems: (updater: Updater<PantryItem[]>) => void;
    setFoodDatabase: (updater: Updater<FoodItem[]>) => void;
    setAiNutritionPlan: (updater: Updater<AINutritionPlan | null>) => void;
}

export const useNutritionStore = create<NutritionStoreState>()(
    persist(
        immer((set) => ({
            nutritionLogs: [],
            pantryItems: [],
            foodDatabase: FOOD_DATABASE,
            aiNutritionPlan: null,
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
        })),
        {
            name: 'kpkn-nutrition-store',
            storage: createMultiKeyStorage({
                nutritionLogs: 'nutrition-logs',
                pantryItems: 'yourprime-pantry-items',
                foodDatabase: 'yourprime-food-database',
                aiNutritionPlan: 'yourprime-ai-nutrition-plan',
            }),
            partialize: (state) => ({
                nutritionLogs: state.nutritionLogs,
                pantryItems: state.pantryItems,
                foodDatabase: state.foodDatabase,
                aiNutritionPlan: state.aiNutritionPlan,
            }),
            onRehydrateStorage: () => () => {
                useNutritionStore.setState({ _hasHydrated: true });
            },
        }
    )
);
