// components/BodyPartDetailView.tsx
import React, { useMemo, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExerciseMuscleInfo } from '../types';
import { ChevronRightIcon } from './icons';

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo }> = React.memo(({ exercise }) => {
    const { navigateTo } = useAppDispatch();
    return (
        <div
            onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
            className="p-3 flex justify-between items-center cursor-pointer list-none hover:bg-slate-800/50 rounded-lg transition-colors"
        >
            <div>
                <h3 className="font-semibold text-white text-md">{exercise.name}</h3>
                <p className="text-xs text-slate-400">{exercise.type} • {exercise.equipment}</p>
            </div>
            <ChevronRightIcon className="text-slate-500" />
        </div>
    );
});

interface BodyPartDetailViewProps {
    bodyPartId: 'Tren Superior' | 'Tren Inferior' | 'Cuerpo Completo' | 'Otro';
}

const TRAIN_INFO = {
    'Tren Superior': {
        description: 'La división de "Tren Superior" se enfoca en todos los músculos de la cintura para arriba: pecho, espalda, hombros y brazos. Es una de las formas más populares y efectivas de organizar el entrenamiento, permitiendo una alta frecuencia por grupo muscular.',
        importance: 'Fortalecer el tren superior es crucial para la postura, la fuerza funcional en tareas diarias (levantar, empujar, tirar) y para crear un físico equilibrado y estético.'
    },
    'Tren Inferior': {
        description: 'La división de "Tren Inferior" se concentra en los músculos de las piernas y glúteos: cuádriceps, isquiotibiales, glúteos y gemelos. Estos entrenamientos suelen ser los más demandantes metabólicamente.',
        importance: 'Un tren inferior fuerte es la base de la potencia atlética (correr, saltar), mejora la estabilidad general, aumenta el gasto calórico y tiene grandes beneficios hormonales.'
    },
    'Cuerpo Completo': {
        description: 'El entrenamiento de "Cuerpo Completo" (Full Body) trabaja todos los principales grupos musculares en una sola sesión. Es ideal para principiantes o para quienes tienen una disponibilidad limitada para entrenar.',
        importance: 'Maximiza la frecuencia de estímulo para cada músculo, es muy eficiente en tiempo y promueve una gran respuesta hormonal y metabólica. Excelente para la recomposición corporal y la fuerza general.'
    },
    'Otro': {
        description: 'Esta categoría incluye ejercicios que no se clasifican claramente o son específicos para ciertos objetivos.',
        importance: '...'
    }
}

const BodyPartDetailView: React.FC<BodyPartDetailViewProps> = ({ bodyPartId }) => {
    const { exerciseList } = useAppState();
    const { setCurrentBackgroundOverride } = useAppDispatch();

    useEffect(() => {
        setCurrentBackgroundOverride(undefined);
        return () => setCurrentBackgroundOverride(undefined);
    }, [setCurrentBackgroundOverride]);

    const exercisesByMuscle = useMemo(() => {
        const partMap = {
            'Tren Superior': 'upper',
            'Tren Inferior': 'lower',
            'Cuerpo Completo': 'full',
            'Otro': undefined
        };
        const targetPart = partMap[bodyPartId];

        const filtered = exerciseList.filter(ex => ex.bodyPart === targetPart);
        
        return filtered.reduce((acc, ex) => {
            const primaryMuscle = ex.involvedMuscles.find(m => m.role === 'primary')?.muscle || 'Otros';
            if (!acc[primaryMuscle]) {
                acc[primaryMuscle] = [];
            }
            acc[primaryMuscle].push(ex);
            return acc;
        }, {} as Record<string, ExerciseMuscleInfo[]>);
    }, [bodyPartId, exerciseList]);

    const sortedMuscleGroups = Object.keys(exercisesByMuscle).sort((a, b) => a.localeCompare(b));
    const info = TRAIN_INFO[bodyPartId];

    return (
        <div className="pb-20 animate-fade-in">
            <header className="mb-6">
                <h1 className="text-4xl font-bold text-white">{bodyPartId}</h1>
            </header>
            
            <div className="space-y-4">
                <div className="glass-card-nested p-4 mb-6">
                    <p className="text-sm text-slate-300 mb-2">{info.description}</p>
                    <p className="text-sm text-slate-400 italic">{info.importance}</p>
                </div>
            
                {sortedMuscleGroups.map(muscle => (
                    <details key={muscle} className="glass-card !p-0" open>
                        <summary className="p-4 cursor-pointer list-none flex justify-between items-center">
                            <h2 className="text-xl font-bold text-primary-color">{muscle}</h2>
                            <ChevronRightIcon className="details-arrow transition-transform" />
                        </summary>
                        <div className="border-t border-slate-700/50 p-2 space-y-1">
                            {exercisesByMuscle[muscle].map(ex => (
                                <ExerciseItem key={ex.id} exercise={ex} />
                            ))}
                        </div>
                    </details>
                ))}
            </div>
        </div>
    );
};

export default BodyPartDetailView;