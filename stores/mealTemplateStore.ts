import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { createPersistMultiKeyStorage } from './storageAdapter';
import type { MealTemplate, LoggedFood } from '../types';

type Updater<T> = T | ((prev: T) => T);

interface MealTemplateStoreState {
    mealTemplates: MealTemplate[];
    _hasHydrated: boolean;

    setMealTemplates: (updater: Updater<MealTemplate[]>) => void;
    addMealTemplate: (template: Omit<MealTemplate, 'id' | 'createdAt'>) => MealTemplate;
    updateMealTemplate: (id: string, data: Partial<MealTemplate>) => void;
    deleteMealTemplate: (id: string) => void;
}

function computeTotals(foods: LoggedFood[]) {
    return foods.reduce(
        (acc, f) => ({
            calories: acc.calories + (f.calories || 0),
            protein: acc.protein + (f.protein || 0),
            carbs: acc.carbs + (f.carbs || 0),
            fats: acc.fats + (f.fats || 0),
        }),
        { calories: 0, protein: 0, carbs: 0, fats: 0 }
    );
}

export const useMealTemplateStore = create<MealTemplateStoreState>()(
    persist(
        immer((set) => ({
            mealTemplates: [],
            _hasHydrated: false,

            setMealTemplates: (updater) => set((state) => {
                state.mealTemplates = typeof updater === 'function'
                    ? updater(state.mealTemplates)
                    : updater;
            }),

            addMealTemplate: (template) => {
                const totals = computeTotals(template.foods);
                const newTemplate: MealTemplate = {
                    ...template,
                    id: crypto.randomUUID(),
                    createdAt: new Date().toISOString(),
                    totalCalories: totals.calories,
                    totalProtein: totals.protein,
                    totalCarbs: totals.carbs,
                    totalFats: totals.fats,
                };
                set((state) => {
                    state.mealTemplates.push(newTemplate);
                });
                return newTemplate;
            },

            updateMealTemplate: (id, data) => set((state) => {
                const idx = state.mealTemplates.findIndex(t => t.id === id);
                if (idx >= 0) {
                    const updated = { ...state.mealTemplates[idx], ...data };
                    if (data.foods) {
                        const totals = computeTotals(data.foods);
                        updated.totalCalories = totals.calories;
                        updated.totalProtein = totals.protein;
                        updated.totalCarbs = totals.carbs;
                        updated.totalFats = totals.fats;
                    }
                    state.mealTemplates[idx] = updated;
                }
            }),

            deleteMealTemplate: (id) => set((state) => {
                state.mealTemplates = state.mealTemplates.filter(t => t.id !== id);
            }),
        })),
        {
            name: 'kpkn-meal-templates',
            storage: createPersistMultiKeyStorage({
                mealTemplates: 'yourprime-meal-templates',
            }),
            partialize: (state) => ({ mealTemplates: state.mealTemplates }),
            onRehydrateStorage: () => () => {
                useMealTemplateStore.setState({ _hasHydrated: true });
            },
        }
    )
);
