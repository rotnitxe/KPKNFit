// components/ExerciseLink.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';

export const ExerciseLink: React.FC<{ name: string }> = ({ name }) => {
    const { exerciseList } = useAppState();
    const dispatch = useAppDispatch();
    
    const foundExercise = React.useMemo(() => 
        exerciseList.find(ex => ex.name.toLowerCase() === name.toLowerCase()),
    [exerciseList, name]);

    if (foundExercise) {
        return (
            <button 
                onClick={() => dispatch.navigateTo('exercise-detail', { exerciseId: foundExercise.id })} 
                className="text-primary-color hover:underline text-left"
            >
                {name}
            </button>
        );
    }

    const handleCreate = () => {
        if (window.confirm(`El ejercicio "${name}" no existe en tu base de datos. ¿Quieres crearlo ahora?`)) {
            dispatch.openCustomExerciseEditor({ preFilledName: name });
        }
    };
    
    return (
        <button 
            onClick={handleCreate} 
            className="text-sky-400 hover:underline text-left italic"
            title={`Crear ejercicio: ${name}`}
        >
            {name} (+)
        </button>
    );
};