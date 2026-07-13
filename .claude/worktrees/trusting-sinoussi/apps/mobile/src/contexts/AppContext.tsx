import React, { createContext, useContext, useMemo } from 'react';
import { useBodyStore } from '@/stores/bodyStore';
import { useExerciseStore } from '@/stores/exerciseStore';
import { useMealTemplateStore } from '@/stores/mealTemplateStore';
import { useMobileNutritionStore } from '@/stores/nutritionStore';
import { useProgramStore } from '@/stores/programStore';
import { useSettingsStore } from '@/stores/settingsStore';
import { useUIStore } from '@/stores/uiStore';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useWorkoutStore } from '@/stores/workoutStore';
import { routerBack, routerNavigate } from '@/routes/navigation';

type AppStateShape = {
  settings: ReturnType<typeof useSettingsStore.getState>;
  program: ReturnType<typeof useProgramStore.getState>;
  workout: ReturnType<typeof useWorkoutStore.getState>;
  body: ReturnType<typeof useBodyStore.getState>;
  nutrition: ReturnType<typeof useMobileNutritionStore.getState>;
  wellbeing: ReturnType<typeof useWellbeingStore.getState>;
  exercise: ReturnType<typeof useExerciseStore.getState>;
  mealTemplates: ReturnType<typeof useMealTemplateStore.getState>;
  ui: ReturnType<typeof useUIStore.getState>;
};

type AppDispatchShape = {
  addToast: ReturnType<typeof useUIStore.getState>['addToast'];
  removeToast: ReturnType<typeof useUIStore.getState>['removeToast'];
  navigate: typeof routerNavigate;
  back: typeof routerBack;
};

const AppStateContext = createContext<AppStateShape | undefined>(undefined);
const AppDispatchContext = createContext<AppDispatchShape | undefined>(undefined);

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const settings = useSettingsStore((state) => state);
  const program = useProgramStore((state) => state);
  const workout = useWorkoutStore((state) => state);
  const body = useBodyStore((state) => state);
  const nutrition = useMobileNutritionStore((state) => state);
  const wellbeing = useWellbeingStore((state) => state);
  const exercise = useExerciseStore((state) => state);
  const mealTemplates = useMealTemplateStore((state) => state);
  const ui = useUIStore((state) => state);

  const stateValue = useMemo<AppStateShape>(
    () => ({ settings, program, workout, body, nutrition, wellbeing, exercise, mealTemplates, ui }),
    [settings, program, workout, body, nutrition, wellbeing, exercise, mealTemplates, ui],
  );

  const dispatchValue = useMemo<AppDispatchShape>(
    () => ({
      addToast: ui.addToast,
      removeToast: ui.removeToast,
      navigate: routerNavigate,
      back: routerBack,
    }),
    [ui.addToast, ui.removeToast],
  );

  return (
    <AppStateContext.Provider value={stateValue}>
      <AppDispatchContext.Provider value={dispatchValue}>{children}</AppDispatchContext.Provider>
    </AppStateContext.Provider>
  );
};

export function useAppState() {
  const context = useContext(AppStateContext);
  if (!context) {
    throw new Error('useAppState debe usarse dentro de AppProvider');
  }
  return context;
}

export function useAppDispatch() {
  const context = useContext(AppDispatchContext);
  if (!context) {
    throw new Error('useAppDispatch debe usarse dentro de AppProvider');
  }
  return context;
}

