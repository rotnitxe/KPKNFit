// components/HallOfFameView.tsx
import React, { useMemo, useState } from 'react';
import { ExerciseMuscleInfo } from '../types';
import useExerciseDatabase from '../hooks/useExerciseDatabase';
import { useAppContext } from '../contexts/AppContext';
import { ChevronRightIcon, TrophyIcon } from './icons';

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo }> = React.memo(({ exercise }) => {
    const { navigateTo } = useAppContext();
    return (
        <div
            onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
            className="p-4 flex justify-between items-center cursor-pointer list-none hover:bg-slate-800/50 rounded-lg transition-colors bg-slate-900/50"
        >
            <div>
                <h3 className="font-semibold text-white text-lg">{exercise.name}</h3>
                <p className="text-sm text-slate-400">{exercise.involvedMuscles?.find(m => m.role === 'primary')?.muscle}</p>
            </div>
            <ChevronRightIcon className="text-slate-500" />
        </div>
    );
});


const HallOfFameView: React.FC = () => {
    const { exerciseList } = useExerciseDatabase();
    const [filter, setFilter] = useState('All');

    const hallOfFameExercises = useMemo(() => {
        const hofs = exerciseList.filter(ex => ex.isHallOfFame);
        if (filter === 'All') {
            return hofs;
        }
        return hofs.filter(ex => ex.bodyPart === filter);
    }, [exerciseList, filter]);

    const filterOptions: {label: string, value: string}[] = [
        { label: 'Todos', value: 'All' },
        { label: 'Tren Superior', value: 'upper' },
        { label: 'Tren Inferior', value: 'lower' },
        { label: 'Cuerpo Completo', value: 'full' },
    ];
    
    return (
        <div className="animate-fade-in pb-10">
            <div className="text-center mb-8">
                <TrophyIcon size={48} className="mx-auto text-yellow-400" />
                <h1 className="text-4xl font-bold uppercase tracking-wider text-yellow-300 mt-2" style={{ textShadow: '0 2px 10px rgba(250, 204, 21, 0.3)'}}>Hall of Fame</h1>
            </div>
            
            <div className="flex justify-center mb-6">
                 <div className="flex space-x-1 p-1 rounded-full bg-slate-800">
                    {filterOptions.map(opt => (
                        <button 
                            key={opt.value} 
                            onClick={() => setFilter(opt.value)}
                            className={`py-1.5 px-4 rounded-full text-sm font-semibold transition-colors ${filter === opt.value ? 'bg-primary-color text-white' : 'text-slate-300 hover:bg-slate-700'}`}
                        >
                            {opt.label}
                        </button>
                    ))}
                </div>
            </div>

            <div className="space-y-3">
                {hallOfFameExercises.map(ex => (
                    <ExerciseItem key={ex.id} exercise={ex} />
                ))}
            </div>
        </div>
    );
};

export default HallOfFameView;