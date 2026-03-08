import React, { useState } from 'react';
import { Program, ExerciseMuscleInfo } from '../../types';
import { ActivityIcon, PlusIcon, TrashIcon, CalendarIcon } from '../icons';

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
        <div className="space-y-6 p-4 rounded-3xl">
            <h3 className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-[0.2em] flex items-center gap-2">
                <ActivityIcon size={16} className="text-[var(--md-sys-color-primary)]" /> Metas del Programa
            </h3>

            {/* 1RM Goals */}
            <div className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-6 space-y-4 shadow-sm">
                <div className="flex items-center justify-between">
                    <div>
                        <span className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest block mb-1">Rendimiento</span>
                        <h4 className="text-title-small font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tight">Metas de 1RM Fuerza</h4>
                    </div>
                    <button
                        onClick={() => setShowAddGoal(!showAddGoal)}
                        className={`flex items-center gap-2 px-4 py-2 rounded-xl text-label-small font-black uppercase tracking-widest transition-all ${showAddGoal
                            ? 'bg-[var(--md-sys-color-on-surface)] text-[var(--md-sys-color-surface)]'
                            : 'bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] hover:brightness-110'
                            }`}
                    >
                        <PlusIcon size={14} /> {showAddGoal ? 'Cerrar' : 'Agregar'}
                    </button>
                </div>

                {showAddGoal && (
                    <div className="bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-4 space-y-3 animate-in fade-in slide-in-from-top-4 duration-300">
                        <div className="relative">
                            <input
                                type="text"
                                value={searchQuery}
                                onChange={e => setSearchQuery(e.target.value)}
                                placeholder="Buscar ejercicio..."
                                className="w-full bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-xl px-4 py-3 text-body-medium font-medium text-[var(--md-sys-color-on-surface)] placeholder-[var(--md-sys-color-outline-variant)] focus:ring-2 focus:ring-[var(--md-sys-color-primary)]/20 focus:border-[var(--md-sys-color-primary)] outline-none transition-all"
                                autoFocus
                            />
                        </div>
                        <div className="grid grid-cols-1 gap-1 max-h-48 overflow-y-auto custom-scrollbar">
                            {filtered.length > 0 ? filtered.map(ex => (
                                <button
                                    key={ex.id}
                                    onClick={() => addGoal(ex)}
                                    className="w-full text-left px-4 py-3 rounded-xl text-label-large font-bold text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface)] hover:text-[var(--md-sys-color-primary)] hover:shadow-sm transition-all flex items-center justify-between group"
                                >
                                    {ex.name}
                                    <PlusIcon size={14} className="opacity-0 group-hover:opacity-100 transition-opacity" />
                                </button>
                            )) : searchQuery && (
                                <div className="py-4 text-center">
                                    <span className="text-label-small font-bold text-[var(--md-sys-color-outline)] uppercase tracking-widest">No se hallaron resultados</span>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {exerciseGoals.length > 0 ? (
                    <div className="grid grid-cols-1 gap-2">
                        {exerciseGoals.map((goal, idx) => (
                            <div key={idx} className="flex items-center gap-4 bg-[var(--md-sys-color-surface-container-high)] p-4 rounded-2xl border border-[var(--md-sys-color-outline-variant)] group hover:bg-[var(--md-sys-color-surface)] hover:border-[var(--md-sys-color-primary)]/30 hover:shadow-md transition-all">
                                <div className="w-10 h-10 rounded-full bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] flex items-center justify-center text-[var(--md-sys-color-primary)] shrink-0">
                                    <ActivityIcon size={18} />
                                </div>
                                <div className="flex-1 min-w-0">
                                    <span className="text-label-small font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tight block truncate mb-1">{goal.exerciseName}</span>
                                    <span className="text-[9px] font-bold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest">Meta Objetivo 1RM</span>
                                </div>
                                <div className="flex items-center gap-3">
                                    <div className="flex flex-col items-end">
                                        <div className="flex items-center gap-2">
                                            <input
                                                type="number"
                                                value={goal.target1RM || ''}
                                                onChange={e => updateGoalTarget(idx, parseFloat(e.target.value) || 0)}
                                                placeholder="0.0"
                                                className="w-20 bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-xl px-3 py-2 text-label-large text-[var(--md-sys-color-on-surface)] text-center font-black focus:ring-2 focus:ring-[var(--md-sys-color-primary)]/20 focus:border-[var(--md-sys-color-primary)] outline-none transition-all"
                                            />
                                            <span className="text-label-small text-[var(--md-sys-color-on-surface-variant)] font-black uppercase">KG</span>
                                        </div>
                                    </div>
                                    <button
                                        onClick={() => removeGoal(idx)}
                                        className="w-8 h-8 rounded-lg flex items-center justify-center text-[var(--md-sys-color-outline-variant)] hover:text-[var(--md-sys-color-error)] hover:bg-[var(--md-sys-color-error-container)]/30 transition-all active:scale-90"
                                    >
                                        <TrashIcon size={16} />
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className="text-center py-10 border-2 border-dashed border-[var(--md-sys-color-outline-variant)] rounded-2xl bg-[var(--md-sys-color-surface-container-lowest)]">
                        <div className="w-12 h-12 bg-[var(--md-sys-color-surface-container-high)] rounded-full flex items-center justify-center mx-auto mb-3 text-[var(--md-sys-color-outline-variant)]">
                            <ActivityIcon size={24} />
                        </div>
                        <p className="text-label-small text-[var(--md-sys-color-on-surface-variant)] font-black uppercase tracking-widest">Sin metas de fuerza configuradas</p>
                    </div>
                )}
            </div>
        </div>
    );
};

export default GoalsSection;
