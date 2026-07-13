import { useState, useMemo } from 'react';
import { useExerciseStore } from '../stores/exerciseStore';
import type { ExerciseCatalogEntry } from '../types/workout';

/**
 * Normalizes text for accent-insensitive search.
 * Removes diacritics and converts to lowercase.
 */
function normalizeText(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

/**
 * Hook for exercise search functionality.
 * Connects to useExerciseStore with atomic selection and provides memoized filtering.
 */
export function useExerciseSearch() {
  // Atomic selection - only subscribe to exerciseList
  const exerciseList = useExerciseStore((state) => state.exerciseList);

  const [searchQuery, setSearchQuery] = useState('');

  /**
   * Filtered exercises based on search query.
   * Searches in name and target muscle (first involved muscle) ignoring case and accents.
   */
  const filteredExercises = useMemo<ExerciseCatalogEntry[]>(() => {
    if (!searchQuery.trim()) {
      return exerciseList;
    }

    const normalizedQuery = normalizeText(searchQuery);

    return exerciseList.filter((exercise) => {
      const normalizedName = normalizeText(exercise.name);
      const normalizedAlias = exercise.alias ? normalizeText(exercise.alias) : '';
      const normalizedTargetMuscle =
        exercise.involvedMuscles.length > 0
          ? normalizeText(String(exercise.involvedMuscles[0].muscle))
          : '';

      return (
        normalizedName.includes(normalizedQuery) ||
        normalizedAlias.includes(normalizedQuery) ||
        normalizedTargetMuscle.includes(normalizedQuery)
      );
    });
  }, [exerciseList, searchQuery]);

  /**
   * Unique target muscles from the entire catalog.
   * Extracts the primary muscle (first involvedMuscle) from each exercise.
   */
  const uniqueTargetMuscles = useMemo<string[]>(() => {
    const muscleSet = new Set<string>();

    exerciseList.forEach((exercise) => {
      if (exercise.involvedMuscles.length > 0) {
        const muscle = String(exercise.involvedMuscles[0].muscle);
        if (muscle.trim()) {
          muscleSet.add(muscle);
        }
      }
    });

    return Array.from(muscleSet).sort((a, b) => a.localeCompare(b, 'es'));
  }, [exerciseList]);

  return {
    // State
    searchQuery,
    setSearchQuery,
    // Data
    filteredExercises,
    uniqueTargetMuscles,
    // Metadata
    totalExercises: exerciseList.length,
    hasResults: filteredExercises.length > 0,
  };
}
