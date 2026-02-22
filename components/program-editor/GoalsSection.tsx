import React, { useState } from 'react';
import { Program, ExerciseMuscleInfo } from '../../types';
import { ActivityIcon, PlusIcon, TrashIcon } from '../icons';

interface GoalsSectionProps {
    program: Program;
    exerciseList: ExerciseMuscleInfo[];
    onUpdateProgram: (program: Program) => void;
}

interface ExerciseGoal {
    exerciseId: string;
    exerciseName: string;
    target1RM: number;
}

const GoalsSection: React.FC<GoalsSectionProps> = ({ program, exerciseList, onUpdateProgram }) => {
    const [showAddGoal, setShowAddGoal] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');

    const exerciseGoals: ExerciseGoal[] = (program as any).exerciseGoals || [];

    const updateGoals = (goals: ExerciseGoal[]) => {
        const updated = JSON.parse(JSON.stringify(program));
        updated.exerciseGoals = goals;
        onUpdateProgram(updated);
    };

    const addGoal = (exercise: ExerciseMuscleInfo) => {
        const newGoals = [...exerciseGoals, { exerciseId: exercise.id, exerciseName: exercise.name, target1RM: 0 }];
        updateGoals(newGoals);
        setShowAddGoal(false);
        setSearchQuery('');
    };

    const removeGoal = (idx: number) => {
        const newGoals = exerciseGoals.filter((_, i) => i !== idx);
        updateGoals(newGoals);
    };

    const updateGoalTarget = (idx: number, value: number) => {
        const newGoals = [...exerciseGoals];
        newGoals[idx].target1RM = value;
        updateGoals(newGoals);
    };

    const filtered = searchQuery.trim()
        ? exerciseList.filter(e => e.name.toLowerCase().includes(searchQuery.toLowerCase())).slice(0, 8)
        : [];

    return (
        <div className="space-y-4">
            <h3 className="text-xs font-black text-white uppercase tracking-widest flex items-center gap-2">
                <ActivityIcon size={14} className="text-zinc-400" /> Metas
            </h3>

            {/* 1RM Goals */}
            <div className="bg-zinc-950 border border-white/5 rounded-xl p-4 space-y-3">
                <div className="flex items-center justify-between">
                    <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest">Metas de 1RM</span>
                    <button
                        onClick={() => setShowAddGoal(!showAddGoal)}
                        className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-zinc-900 border border-white/10 text-[8px] font-black text-zinc-400 uppercase tracking-widest hover:text-white transition-colors"
                    >
                        <PlusIcon size={10} /> Agregar
                    </button>
                </div>

                {showAddGoal && (
                    <div className="bg-black border border-white/10 rounded-xl p-3 space-y-2 animate-fade-in">
                        <input
                            type="text"
                            value={searchQuery}
                            onChange={e => setSearchQuery(e.target.value)}
                            placeholder="Buscar ejercicio..."
                            className="w-full bg-zinc-950 border border-white/10 rounded-lg px-3 py-2 text-xs text-white placeholder-zinc-600 focus:ring-1 focus:ring-white/30"
                            autoFocus
                        />
                        {filtered.map(ex => (
                            <button
                                key={ex.id}
                                onClick={() => addGoal(ex)}
                                className="w-full text-left px-3 py-2 rounded-lg text-[10px] font-bold text-zinc-300 hover:bg-zinc-900 transition-colors"
                            >
                                {ex.name}
                            </button>
                        ))}
                    </div>
                )}

                {exerciseGoals.length > 0 ? (
                    <div className="space-y-2">
                        {exerciseGoals.map((goal, idx) => (
                            <div key={idx} className="flex items-center gap-3 bg-black p-3 rounded-xl border border-white/5 group">
                                <div className="flex-1 min-w-0">
                                    <span className="text-[10px] font-bold text-white block truncate">{goal.exerciseName}</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <input
                                        type="number"
                                        value={goal.target1RM || ''}
                                        onChange={e => updateGoalTarget(idx, parseFloat(e.target.value) || 0)}
                                        placeholder="kg"
                                        className="w-16 bg-zinc-950 border border-white/10 rounded-lg px-2 py-1 text-xs text-white text-center font-bold focus:ring-1 focus:ring-white/30"
                                    />
                                    <span className="text-[8px] text-zinc-500 font-bold">kg</span>
                                    <button
                                        onClick={() => removeGoal(idx)}
                                        className="p-1 text-zinc-700 hover:text-red-500 transition-colors opacity-0 group-hover:opacity-100"
                                    >
                                        <TrashIcon size={10} />
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className="text-center py-6 border border-dashed border-white/10 rounded-xl">
                        <p className="text-[9px] text-zinc-500 font-bold">Sin metas configuradas</p>
                    </div>
                )}
            </div>

            {/* Volume goals placeholder */}
            <div className="bg-zinc-950 border border-white/5 rounded-xl p-4">
                <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest block mb-2">Metas de Volumen</span>
                <p className="text-[9px] text-zinc-600 italic">Configura volumen objetivo por grupo muscular (proximamente).</p>
            </div>

            {/* Frequency goals placeholder */}
            <div className="bg-zinc-950 border border-white/5 rounded-xl p-4">
                <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest block mb-2">Metas de Frecuencia</span>
                <p className="text-[9px] text-zinc-600 italic">Define frecuencia objetivo por patrón de movimiento (proximamente).</p>
            </div>
        </div>
    );
};

export default GoalsSection;
