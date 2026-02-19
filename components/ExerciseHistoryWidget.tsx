import React, { useState } from 'react';
import { Program, WorkoutLog } from '../types';
import { TrendingUpIcon, ChevronRightIcon } from './icons';
import { ExerciseHistoryPickerModal } from './ExerciseHistoryPickerModal';

const ExerciseHistoryWidget: React.FC<{ program: Program; history: WorkoutLog[] }> = ({ program, history }) => {
    const [isOpen, setIsOpen] = useState(false);

    // Contar ejercicios únicos del programa
    const uniqueExercisesCount = new Set(
        program.macrocycles.flatMap(m => m.blocks?.flatMap(b => b.mesocycles.flatMap(me => me.weeks.flatMap(w => w.sessions.flatMap(s => s.exercises.map(e => e.name))))))
    ).size;

    return (
        <>
            <button 
                onClick={() => setIsOpen(true)}
                className="w-full bg-[#111] border border-white/10 rounded-3xl p-5 flex items-center justify-between hover:bg-white/5 transition-colors group"
            >
                <div className="flex items-center gap-4">
                    <div className="bg-blue-500/10 p-3 rounded-2xl text-blue-400 group-hover:bg-blue-500 group-hover:text-white transition-colors">
                        <TrendingUpIcon size={24} />
                    </div>
                    <div className="text-left">
                        <h4 className="text-sm font-black text-white uppercase">Historial por Ejercicio</h4>
                        <p className="text-[10px] text-gray-400 mt-0.5">
                            Analiza progreso en {uniqueExercisesCount} ejercicios
                        </p>
                    </div>
                </div>
                <div className="bg-black/20 p-2 rounded-full text-gray-500 group-hover:text-white transition-colors">
                    <ChevronRightIcon size={20} />
                </div>
            </button>

            <ExerciseHistoryPickerModal 
                isOpen={isOpen} 
                onClose={() => setIsOpen(false)} 
                program={program}
                history={history}
            />
        </>
    );
};

export default ExerciseHistoryWidget;