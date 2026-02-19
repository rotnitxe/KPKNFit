import React, { useState } from 'react';
import Modal from './ui/Modal';
import { Program, WorkoutLog, Exercise } from '../types';
import { XIcon, ChevronRightIcon, SearchIcon } from './icons';
import { useAppContext } from '../contexts/AppContext';
import ExerciseHistoryModal from './ExerciseHistoryModal'; // Importamos el modal restaurado

interface Props {
    isOpen: boolean;
    onClose: () => void;
    program: Program;
    history: WorkoutLog[];
}

export const ExerciseHistoryPickerModal: React.FC<Props> = ({ isOpen, onClose, program, history }) => {
    const { settings } = useAppContext();
    const [selectedExercise, setSelectedExercise] = useState<Exercise | null>(null);
    const [searchTerm, setSearchTerm] = useState('');

    if (!isOpen) return null;

    // Si hay un ejercicio seleccionado, mostramos SU historial
    if (selectedExercise) {
        return (
            <ExerciseHistoryModal 
                exercise={selectedExercise}
                programId={program.id}
                history={history}
                settings={settings}
                onClose={() => setSelectedExercise(null)}
            />
        );
    }

    // Obtener lista única de ejercicios del programa
    const uniqueExercisesMap = new Map<string, Exercise>();
    
    program.macrocycles.forEach(m => 
        (m.blocks || []).forEach(b => 
            b.mesocycles.forEach(me => 
                me.weeks.forEach(w => 
                    w.sessions.forEach(s => 
                        s.exercises.forEach(e => {
                            // Usamos el nombre como clave única para agrupar variantes
                            if (!uniqueExercisesMap.has(e.name)) {
                                uniqueExercisesMap.set(e.name, e);
                            }
                        })
                    )
                )
            )
        )
    );

    const exercises = Array.from(uniqueExercisesMap.values())
        .filter(e => e.name.toLowerCase().includes(searchTerm.toLowerCase()))
        .sort((a, b) => a.name.localeCompare(b.name));

    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Historial por Ejercicio">
            <div className="p-2 h-[70vh] flex flex-col">
                <div className="relative mb-4">
                    <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" size={16} />
                    <input 
                        type="text" 
                        placeholder="Buscar ejercicio..." 
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="w-full bg-zinc-900 border border-white/10 rounded-xl py-3 pl-10 pr-4 text-sm text-white focus:outline-none focus:border-white/30"
                    />
                </div>

                <div className="flex-1 overflow-y-auto custom-scrollbar space-y-2">
                    {exercises.map((exercise) => (
                        <button 
                            key={exercise.id}
                            onClick={() => setSelectedExercise(exercise)}
                            className="w-full flex items-center justify-between p-4 bg-zinc-900/50 border border-white/5 rounded-xl hover:bg-zinc-800 transition-colors group text-left"
                        >
                            <span className="font-bold text-gray-300 text-sm group-hover:text-white">{exercise.name}</span>
                            <ChevronRightIcon size={16} className="text-gray-600 group-hover:text-white" />
                        </button>
                    ))}
                    
                    {exercises.length === 0 && (
                        <div className="text-center text-gray-500 text-xs py-8">
                            No se encontraron ejercicios.
                        </div>
                    )}
                </div>
            </div>
        </Modal>
    );
};