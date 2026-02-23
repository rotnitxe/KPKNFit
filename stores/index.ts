/**
 * Índice central de stores Zustand.
 * Importar desde aquí para mantener referencias consistentes.
 */
export { useSettingsStore } from './settingsStore';
export { useProgramStore } from './programStore';
export { useWorkoutStore } from './workoutStore';
export { useBodyStore } from './bodyStore';
export { useNutritionStore } from './nutritionStore';
export { useWellbeingStore } from './wellbeingStore';
export { useExerciseStore } from './exerciseStore';
export { useMealTemplateStore } from './mealTemplateStore';
export { useUIStore } from './uiStore';
export { useAuthStore } from './authStore';
export { createPersistMultiKeyStorage } from './storageAdapter';
