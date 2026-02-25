// hooks/useExerciseDatabase.ts
import { useCallback } from 'react';
import { DETAILED_EXERCISE_LIST } from '../data/exerciseDatabase';
import { ExerciseMuscleInfo } from '../types';
import useLocalStorage from './useLocalStorage';
import { getLocalDateString } from '../utils/dateUtils';

const EXERCISE_DB_KEY = 'yourprime-exercise-database';
const STATIC_EXERCISES = DETAILED_EXERCISE_LIST; // Keep the original default list

const useExerciseDatabase = () => {
  const [exerciseList, setExerciseList, isDbLoading] = useLocalStorage<ExerciseMuscleInfo[]>(
    EXERCISE_DB_KEY,
    STATIC_EXERCISES
  );

  const addOrUpdateCustomExercise = useCallback((exerciseInfo: ExerciseMuscleInfo) => {
    setExerciseList(prev => {
      const isStatic = STATIC_EXERCISES.some(ex => ex.id === exerciseInfo.id);
      const exerciseWithFlag = { ...exerciseInfo, isCustom: !isStatic };

      const existingIndexById = prev.findIndex(ex => ex.id === exerciseWithFlag.id);

      if (existingIndexById > -1) {
          // Found by ID, update in place
          const updated = [...prev];
          updated[existingIndexById] = exerciseWithFlag;
          return updated;
      }

      // Not found by ID, look for name collision to overwrite.
      // This is still useful if a user wants to replace an existing custom exercise by creating a new one with the same name.
      const existingIndexByName = prev.findIndex(ex => ex.name.toLowerCase() === exerciseWithFlag.name.toLowerCase());
      if (existingIndexByName > -1) {
          const updated = [...prev];
          updated[existingIndexByName] = exerciseWithFlag;
          return updated;
      }

      // Doesn't exist, add as new
      return [...prev, exerciseWithFlag];
    });
  }, [setExerciseList]);

  const exportExerciseDatabase = useCallback(() => {
    if (isDbLoading) {
        alert("La base de datos todavía se está cargando. Inténtalo de nuevo en un momento.");
        return;
    }
    try {
        const jsonString = `data:text/json;charset=utf-8,${encodeURIComponent(
            JSON.stringify(exerciseList, null, 2)
        )}`;
        const link = document.createElement("a");
        link.href = jsonString;
        link.download = `yourprime_exercisedb_${getLocalDateString()}.json`;
        link.click();
    } catch (error) {
        alert('Error al exportar la base de datos de ejercicios.');
        console.error(error);
    }
  }, [exerciseList, isDbLoading]);

  const importExerciseDatabase = useCallback((jsonString: string) => {
    try {
        const importedData = JSON.parse(jsonString);
        if (!Array.isArray(importedData) || !importedData.every(item => typeof item === 'object' && item.name && item.id)) {
            throw new Error("El archivo no tiene el formato de base de datos de ejercicios correcto.");
        }
        
        if (window.confirm("¿Estás seguro de que quieres reemplazar tu base de datos de ejercicios actual con la del archivo? Esta acción no se puede deshacer.")) {
            setExerciseList(importedData as ExerciseMuscleInfo[]);
            alert('Base de datos de ejercicios importada con éxito. La aplicación se recargará para aplicar los cambios.');
            setTimeout(() => window.location.reload(), 500);
        }

    } catch (error) {
        alert(`Error al importar la base de datos: ${error instanceof Error ? error.message : 'Error desconocido'}`);
        console.error(error);
    }
  }, [setExerciseList]);

  return {
    exerciseList,
    setExerciseList,
    isDbLoading,
    addOrUpdateCustomExercise,
    exportExerciseDatabase,
    importExerciseDatabase,
  };
};

export default useExerciseDatabase;