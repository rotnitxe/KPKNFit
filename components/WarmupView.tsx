// components/WarmupView.tsx
import React, { useState, useMemo } from 'react';
import { WarmupExercise, ExerciseMuscleInfo } from '../types';
import Card from './ui/Card';
import Button from './ui/Button';
import { PlusIcon, TrashIcon, ClockIcon, SaveIcon } from './icons';

interface WarmupViewProps {
  routine: WarmupExercise[];
  onSave: (newRoutine: WarmupExercise[]) => void;
  exerciseList: ExerciseMuscleInfo[];
}

const WarmupView: React.FC<WarmupViewProps> = ({ routine, onSave, exerciseList }) => {
  const [currentRoutine, setCurrentRoutine] = useState<WarmupExercise[]>(routine);
  const [searchQuery, setSearchQuery] = useState('');

  const filteredExercises = useMemo(() => {
    if (!exerciseList) return [];
    return exerciseList.filter(ex =>
      ex.category === 'Movilidad' &&
      (ex.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (ex.bodyPart && ex.bodyPart.toLowerCase().includes(searchQuery.toLowerCase())))
    );
  }, [searchQuery, exerciseList]);

  const addExerciseToRoutine = (exercise: ExerciseMuscleInfo) => {
    // Check if exercise is already in the routine to avoid duplicates
    if (currentRoutine.some(ex => ex.id === exercise.id)) return;
    
    const newExercise: WarmupExercise = {
      id: exercise.id,
      name: exercise.name,
      description: exercise.description,
      category: (exercise.bodyPart === 'upper' ? 'Upper Body' : exercise.bodyPart === 'lower' ? 'Lower Body' : 'Full Body'),
      duration: 30, // Default duration
    };
    setCurrentRoutine(prev => [...prev, newExercise]);
  };

  const removeExerciseFromRoutine = (exerciseId: string) => {
    setCurrentRoutine(prev => prev.filter(ex => ex.id !== exerciseId));
  };
  
  const updateDuration = (exerciseId: string, duration: number) => {
      setCurrentRoutine(prev => prev.map(ex => ex.id === exerciseId ? {...ex, duration: isNaN(duration) ? 0 : duration} : ex));
  };
  
  const handleSave = () => {
      onSave(currentRoutine);
      alert('Rutina de calentamiento guardada!');
  };

  return (
    <div className="animate-fade-in space-y-8">
      <div>
        <h1 className="text-4xl font-bold uppercase tracking-wider">Creador de Calentamiento</h1>
        <p className="text-slate-400 mt-2">Crea tu rutina de calentamiento dinámico perfecta para preparar tu cuerpo antes de entrenar.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
        {/* Available Exercises Column */}
        <Card className="h-full">
            <h2 className="text-2xl font-bold mb-4">Ejercicios Disponibles</h2>
            <input 
                type="text"
                placeholder="Buscar ejercicios por nombre o categoría..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full bg-slate-800 border border-slate-700 rounded-md px-4 py-2 mb-4 focus:outline-none focus:ring-2 focus:ring-[var(--primary-color-500)]"
            />
            <div className="max-h-96 overflow-y-auto space-y-2 pr-2">
                {filteredExercises.map(ex => (
                    <div key={ex.id} className="bg-slate-900 p-3 rounded-md flex justify-between items-center">
                        <div>
                            <h3 className="font-semibold text-white">{ex.name}</h3>
                            <p className="text-sm text-slate-400">{ex.bodyPart}</p>
                        </div>
                        <button 
                            onClick={() => addExerciseToRoutine(ex)}
                            disabled={currentRoutine.some(routineEx => routineEx.id === ex.id)}
                            className="p-2 rounded-full bg-slate-700 hover:bg-[var(--primary-color-500)] text-white disabled:bg-slate-800 disabled:text-slate-600 disabled:cursor-not-allowed transition"
                        >
                            <PlusIcon size={16}/>
                        </button>
                    </div>
                ))}
            </div>
        </Card>

        {/* Current Routine Column */}
        <Card>
            <h2 className="text-2xl font-bold mb-4">Tu Rutina</h2>
            <div className="space-y-3">
                {currentRoutine.length > 0 ? currentRoutine.map(ex => (
                    <div key={ex.id} className="bg-slate-950 p-3 rounded-md flex items-center justify-between gap-4">
                        <div className="flex-grow">
                             <h3 className="font-semibold text-[var(--primary-color-400)]">{ex.name}</h3>
                             <p className="text-xs text-slate-500">{ex.category}</p>
                        </div>
                        <div className="flex items-center gap-2">
                            <ClockIcon size={16} className="text-slate-400" />
                            <input 
                                type="number"
                                value={ex.duration}
                                onChange={(e) => updateDuration(ex.id, parseInt(e.target.value, 10))}
                                className="w-16 bg-slate-800 border border-slate-700 rounded text-center py-1 focus:outline-none focus:ring-1 focus:ring-[var(--primary-color-500)]"
                            />
                            <span className="text-sm text-slate-400">seg</span>
                        </div>
                        <button onClick={() => removeExerciseFromRoutine(ex.id)} className="p-2 rounded-full text-slate-400 hover:bg-red-900/50 hover:text-red-400 transition">
                            <TrashIcon size={16} />
                        </button>
                    </div>
                )) : (
                    <div className="text-center py-8 text-slate-500">
                        <p>Añade ejercicios desde la lista de la izquierda para construir tu rutina de calentamiento.</p>
                    </div>
                )}
            </div>
            {currentRoutine.length > 0 && (
                <div className="mt-6 pt-4 border-t border-slate-700">
                     <Button onClick={handleSave} className="w-full">
                        <SaveIcon /> Guardar Rutina
                     </Button>
                </div>
            )}
        </Card>

      </div>
    </div>
  );
};

export default WarmupView;
