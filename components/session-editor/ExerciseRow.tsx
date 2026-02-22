import React, { useState, useCallback, useMemo, useEffect } from 'react';
import { Exercise, ExerciseSet, ExerciseMuscleInfo } from '../../types';
import { StarIcon, TrashIcon, ChevronDownIcon, ClockIcon, LinkIcon, SearchIcon, PlusIcon, FlameIcon } from '../icons';
import InlineSetTable from './InlineSetTable';
import FatigueIndicators from './FatigueIndicators';
import { calculateHybrid1RM } from '../../utils/calculations';

interface ExerciseRowProps {
    exercise: Exercise;
    exerciseIndex: number;
    partIndex: number;
    exerciseList: ExerciseMuscleInfo[];
    isCulprit?: boolean;
    isExpertMode?: boolean;
    fatigue?: { msc: number; snc: number; spinal: number };
    augeSuggestion?: string | null;
    onUpdate: (partIndex: number, exerciseIndex: number, updater: (ex: Exercise) => void) => void;
    onRemove: (partIndex: number, exerciseIndex: number) => void;
    onReorder: (partIndex: number, exerciseIndex: number, direction: 'up' | 'down') => void;
    onLink: (partIndex: number, exerciseIndex: number) => void;
    onUnlink: (partIndex: number, exerciseIndex: number) => void;
    onAmrapToggle?: (partIndex: number, exerciseIndex: number, setIndex: number) => void;
    onOpenExerciseModal?: (partIndex: number, exerciseIndex: number) => void;
    scrollRef?: (el: HTMLDivElement | null) => void;
    dragHandleProps?: React.HTMLAttributes<HTMLDivElement>;
}

const ExerciseRow: React.FC<ExerciseRowProps> = ({
    exercise, exerciseIndex, partIndex, exerciseList, isCulprit,
    isExpertMode, fatigue, augeSuggestion, onUpdate, onRemove, onReorder,
    onLink, onUnlink, onAmrapToggle, onOpenExerciseModal, scrollRef, dragHandleProps,
}) => {
    const [isExpanded, setIsExpanded] = useState(false);
    const [isSearching, setIsSearching] = useState(!exercise.name);
    const [searchQuery, setSearchQuery] = useState('');

    const setsSummary = useMemo(() => {
        const sets = exercise.sets || [];
        if (sets.length === 0) return 'Sin series';
        const reps = sets.map(s => s.targetReps || 0);
        const allSame = reps.every(r => r === reps[0]);
        const rpeVals = sets.map(s => s.targetRPE).filter(Boolean);
        const avgRpe = rpeVals.length ? (rpeVals.reduce((a, b) => a! + b!, 0)! / rpeVals.length).toFixed(1) : null;
        if (allSame && reps[0]) return `${sets.length}x${reps[0]}${avgRpe ? ` @${avgRpe}` : ''}`;
        const minR = Math.min(...reps.filter(r => r > 0));
        const maxR = Math.max(...reps);
        return `${sets.length}x${minR}-${maxR}${avgRpe ? ` @${avgRpe}` : ''}`;
    }, [exercise.sets]);

    const filteredExercises = useMemo(() => {
        if (!searchQuery) return exerciseList.slice(0, 20);
        const q = searchQuery.toLowerCase();
        return exerciseList.filter(e =>
            e.name.toLowerCase().includes(q) ||
            (e.equipment && e.equipment.toLowerCase().includes(q)) ||
            (e.alias && e.alias.toLowerCase().includes(q)) ||
            e.involvedMuscles?.some(m => m.muscle.toLowerCase().includes(q))
        ).slice(0, 15);
    }, [searchQuery, exerciseList]);

    const handleSelectExercise = useCallback((ex: ExerciseMuscleInfo) => {
        onUpdate(partIndex, exerciseIndex, (draft) => {
            draft.name = ex.name;
            draft.exerciseDbId = ex.id;
        });
        setIsSearching(false);
        setSearchQuery('');
    }, [partIndex, exerciseIndex, onUpdate]);

    const handleSetChange = useCallback((setIndex: number, field: keyof ExerciseSet | Partial<ExerciseSet>, value?: any) => {
        onUpdate(partIndex, exerciseIndex, (draft) => {
            if (!draft.sets[setIndex]) return;
            if (typeof field === 'string') {
                (draft.sets[setIndex] as any)[field] = value;
            } else {
                Object.assign(draft.sets[setIndex], field);
            }
        });
    }, [partIndex, exerciseIndex, onUpdate]);

    const handleAddSet = useCallback(() => {
        onUpdate(partIndex, exerciseIndex, (draft) => {
            const last = draft.sets[draft.sets.length - 1];
            const defaultMode = draft.trainingMode === 'percent' ? 'solo_rm' : (last?.intensityMode || 'rpe');
            draft.sets.push({
                id: crypto.randomUUID(),
                targetReps: last?.targetReps || 10,
                targetRPE: last?.targetRPE || 8,
                intensityMode: last?.intensityMode || defaultMode,
            });
        });
    }, [partIndex, exerciseIndex, onUpdate]);

    const handleRemoveSet = useCallback((setIndex: number) => {
        onUpdate(partIndex, exerciseIndex, (draft) => {
            draft.sets.splice(setIndex, 1);
        });
    }, [partIndex, exerciseIndex, onUpdate]);

    // Calcular reference1RM desde PR cuando prFor1RM está definido (modo percent)
    useEffect(() => {
        if (exercise.trainingMode !== 'percent' || !exercise.prFor1RM?.weight || !exercise.prFor1RM?.reps) return;
        const e1rm = calculateHybrid1RM(exercise.prFor1RM.weight, exercise.prFor1RM.reps);
        if (e1rm > 0 && Math.abs((exercise.reference1RM || 0) - e1rm) > 0.1) {
            onUpdate(partIndex, exerciseIndex, (draft) => {
                draft.reference1RM = e1rm;
            });
        }
    }, [exercise.trainingMode, exercise.prFor1RM?.weight, exercise.prFor1RM?.reps, exercise.reference1RM, partIndex, exerciseIndex, onUpdate]);

    const formatRest = (seconds: number) => {
        const m = Math.floor(seconds / 60);
        const s = seconds % 60;
        return `${m}:${s.toString().padStart(2, '0')}`;
    };

    if (isSearching) {
        return (
            <div ref={scrollRef} className="px-4 py-3 border-b border-white/5 animate-fade-in">
                <div className="relative mb-2">
                    <SearchIcon size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#555]" />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={e => setSearchQuery(e.target.value)}
                        placeholder="Buscar ejercicio..."
                        autoFocus
                        className="w-full bg-[#0d0d0d] border-b border-white/10 focus:border-[#FC4C02] pl-9 pr-3 py-2.5 text-sm text-white placeholder-[#555] outline-none transition-colors"
                    />
                </div>
                <div className="max-h-48 overflow-y-auto custom-scrollbar space-y-0.5">
                    {filteredExercises.map(ex => (
                        <button
                            key={ex.id || ex.name}
                            onClick={() => handleSelectExercise(ex)}
                            className="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 transition-colors"
                        >
                            <span className="text-sm font-medium text-white">{ex.name}</span>
                            <span className="text-[10px] text-[#555] ml-2">
                                {ex.involvedMuscles?.filter(m => m.role === 'primary').map(m => m.muscle).join(', ')}
                            </span>
                        </button>
                    ))}
                </div>
                {exercise.name && (
                    <button onClick={() => setIsSearching(false)} className="w-full mt-2 py-1.5 text-xs font-medium text-[#555] hover:text-white transition-colors">
                        Cancelar
                    </button>
                )}
            </div>
        );
    }

    return (
        <div
            ref={scrollRef}
            className={`border-b transition-colors ${isCulprit ? 'border-red-500/20 bg-red-950/5' : 'border-white/5'}`}
        >
            {/* Collapsed row */}
            <button
                onClick={() => setIsExpanded(!isExpanded)}
                className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-white/[0.02] transition-colors"
            >
                {/* Grip handle (drag) */}
                <div {...(dragHandleProps || {})} className={`w-6 flex flex-col items-center justify-center gap-0.5 shrink-0 cursor-grab active:cursor-grabbing ${dragHandleProps ? 'opacity-60 hover:opacity-100' : 'opacity-30 hover:opacity-60'} transition-opacity touch-none`}>
                    <div className="w-3 h-0.5 bg-[#555] rounded-full" />
                    <div className="w-3 h-0.5 bg-[#555] rounded-full" />
                </div>

                {/* Exercise name */}
                <button
                    onClick={e => {
                        e.stopPropagation();
                        if (onOpenExerciseModal) onOpenExerciseModal(partIndex, exerciseIndex);
                        else setIsSearching(true);
                    }}
                    className="text-sm font-medium text-white truncate hover:text-[#FC4C02] transition-colors text-left min-w-0 flex-1"
                >
                    {exercise.name || 'Seleccionar ejercicio'}
                </button>

                {/* Sets summary */}
                <span className="text-xs font-mono text-[#999] shrink-0">{setsSummary}</span>

                {/* Fatigue indicators */}
                {fatigue && <FatigueIndicators msc={fatigue.msc} snc={fatigue.snc} spinal={fatigue.spinal} />}

                {/* Star */}
                {exercise.isStarTarget && <StarIcon size={12} filled className="text-yellow-400 shrink-0" />}

                {/* Expand chevron */}
                <ChevronDownIcon
                    size={14}
                    className={`text-[#555] transition-transform shrink-0 ${isExpanded ? 'rotate-180' : ''}`}
                />
            </button>

            {/* Expanded content */}
            {isExpanded && (
                <div className="px-4 pb-4 space-y-3 animate-fade-in">
                    {/* AUGE suggestion */}
                    {augeSuggestion && (
                        <div className="flex items-start gap-2 px-3 py-2 rounded-lg bg-[#FC4C02]/5 border border-[#FC4C02]/10">
                            <span className="text-[10px] text-[#FC4C02] leading-relaxed">{augeSuggestion}</span>
                        </div>
                    )}

                    {/* Config row */}
                    <div className="flex items-center gap-3 flex-wrap">
                        <div className="flex items-center gap-1.5">
                            <ClockIcon size={12} className="text-[#555]" />
                            <input
                                type="text"
                                value={formatRest(exercise.restTime || 90)}
                                onChange={e => {
                                    const val = e.target.value;
                                    let seconds = 90;
                                    if (val.includes(':')) {
                                        const [m, s] = val.split(':').map(Number);
                                        seconds = (m * 60) + (s || 0);
                                    } else {
                                        seconds = parseInt(val) || 0;
                                    }
                                    onUpdate(partIndex, exerciseIndex, d => { d.restTime = seconds; });
                                }}
                                className="w-12 bg-transparent border-b border-white/10 focus:border-[#FC4C02] text-xs font-mono text-white text-center py-0.5 outline-none transition-colors"
                            />
                        </div>

                        <select
                            value={exercise.trainingMode || 'reps'}
                            onChange={e => onUpdate(partIndex, exerciseIndex, d => { d.trainingMode = e.target.value as any; })}
                            className="bg-transparent text-[10px] font-bold text-[#999] border-b border-white/10 focus:border-[#FC4C02] py-0.5 px-1 outline-none cursor-pointer"
                        >
                            <option value="reps" className="bg-black">Reps</option>
                            <option value="percent" className="bg-black">% 1RM</option>
                            <option value="time" className="bg-black">Tiempo</option>
                            <option value="custom" className="bg-black">Libre</option>
                        </select>

                        {exercise.trainingMode === 'percent' && (
                            <div className="flex items-center gap-2 flex-wrap">
                                <div className="flex items-center gap-1">
                                    <span className="text-[10px] text-[#555]">1RM:</span>
                                    <input
                                        type="number"
                                        step="0.5"
                                        value={exercise.reference1RM ?? ''}
                                        onChange={e => onUpdate(partIndex, exerciseIndex, d => { d.reference1RM = parseFloat(e.target.value) || 0; })}
                                        placeholder="kg"
                                        className="w-14 bg-white/[0.03] border-b border-orange-500/20 focus:border-orange-500/60 text-xs font-mono text-white text-center py-0.5 rounded outline-none"
                                    />
                                </div>
                                <div className="flex items-center gap-1">
                                    <span className="text-[10px] text-[#555]">o PR:</span>
                                    <input
                                        type="number"
                                        step="0.5"
                                        value={exercise.prFor1RM?.weight ?? ''}
                                        onChange={e => {
                                            const w = parseFloat(e.target.value) || 0;
                                            onUpdate(partIndex, exerciseIndex, d => {
                                                d.prFor1RM = { weight: w, reps: d.prFor1RM?.reps || 1 };
                                            });
                                        }}
                                        placeholder="kg"
                                        className="w-12 bg-white/[0.03] border-b border-orange-500/20 focus:border-orange-500/60 text-xs font-mono text-white text-center py-0.5 rounded outline-none"
                                    />
                                    <span className="text-[10px] text-[#555]">×</span>
                                    <input
                                        type="number"
                                        min={1}
                                        max={30}
                                        value={exercise.prFor1RM?.reps ?? ''}
                                        onChange={e => {
                                            const r = Math.min(30, Math.max(1, parseInt(e.target.value) || 1));
                                            onUpdate(partIndex, exerciseIndex, d => {
                                                d.prFor1RM = { weight: d.prFor1RM?.weight || 0, reps: r };
                                            });
                                        }}
                                        placeholder="r"
                                        className="w-10 bg-white/[0.03] border-b border-orange-500/20 focus:border-orange-500/60 text-xs font-mono text-white text-center py-0.5 rounded outline-none"
                                    />
                                </div>
                            </div>
                        )}

                        {exercise.trainingMode === 'reps' && (
                            <div className="flex items-center gap-1">
                                <span className="text-[10px] text-[#555]">Peso ref:</span>
                                <input
                                    type="number"
                                    step="0.5"
                                    value={exercise.consolidatedWeight?.weightKg ?? ''}
                                    onChange={e => {
                                        const w = parseFloat(e.target.value) || 0;
                                        onUpdate(partIndex, exerciseIndex, d => {
                                            d.consolidatedWeight = { weightKg: w, reps: d.consolidatedWeight?.reps || 10 };
                                        });
                                    }}
                                    placeholder="kg"
                                    className="w-12 bg-white/[0.03] border-b border-orange-500/20 focus:border-orange-500/60 text-xs font-mono text-white text-center py-0.5 rounded outline-none"
                                />
                                <span className="text-[10px] text-[#555]">×</span>
                                <input
                                    type="number"
                                    min={1}
                                    max={99}
                                    value={exercise.consolidatedWeight?.reps ?? ''}
                                    onChange={e => {
                                        const r = Math.min(99, Math.max(1, parseInt(e.target.value) || 10));
                                        onUpdate(partIndex, exerciseIndex, d => {
                                            d.consolidatedWeight = { weightKg: d.consolidatedWeight?.weightKg || 0, reps: r };
                                        });
                                    }}
                                    placeholder="r"
                                    className="w-10 bg-white/[0.03] border-b border-orange-500/20 focus:border-orange-500/60 text-xs font-mono text-white text-center py-0.5 rounded outline-none"
                                />
                            </div>
                        )}

                        <div className="flex-1" />

                        {/* Actions */}
                        <button onClick={() => onUpdate(partIndex, exerciseIndex, d => { d.isStarTarget = !d.isStarTarget; })} className={`p-1 ${exercise.isStarTarget ? 'text-yellow-400' : 'text-[#555] hover:text-yellow-400'} transition-colors`}>
                            <StarIcon size={12} filled={exercise.isStarTarget} />
                        </button>
                        <button onClick={() => onLink(partIndex, exerciseIndex)} className="p-1 text-[#555] hover:text-white transition-colors" title="Biserie">
                            <LinkIcon size={12} />
                        </button>
                        <button onClick={() => onRemove(partIndex, exerciseIndex)} className="p-1 text-[#555] hover:text-red-400 transition-colors">
                            <TrashIcon size={12} />
                        </button>
                    </div>

                    {/* Sets table */}
                    <InlineSetTable
                        sets={exercise.sets || []}
                        trainingMode={exercise.trainingMode}
                        onSetChange={handleSetChange}
                        onAddSet={handleAddSet}
                        onRemoveSet={handleRemoveSet}
                        onAmrapToggle={onAmrapToggle ? (setIdx) => onAmrapToggle(partIndex, exerciseIndex, setIdx) : undefined}
                        reference1RM={exercise.reference1RM}
                    />
                </div>
            )}
        </div>
    );
};

export default ExerciseRow;
