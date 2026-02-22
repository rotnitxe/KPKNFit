import React, { useState, useCallback, useMemo } from 'react';
import { Exercise, ExerciseSet, ExerciseMuscleInfo } from '../../types';
import { StarIcon, TrashIcon, ChevronRightIcon, ClockIcon, LinkIcon, ArrowUpIcon, ArrowDownIcon, SearchIcon, PlusIcon } from '../icons';
import SetCardGrid from './SetCardGrid';

interface ExerciseCardCompactProps {
    exercise: Exercise;
    exerciseIndex: number;
    partIndex: number;
    exerciseList: ExerciseMuscleInfo[];
    isCulprit?: boolean;
    isExpertMode?: boolean;
    onUpdate: (partIndex: number, exerciseIndex: number, updater: (ex: Exercise) => void) => void;
    onRemove: (partIndex: number, exerciseIndex: number) => void;
    onReorder: (partIndex: number, exerciseIndex: number, direction: 'up' | 'down') => void;
    onLink: (partIndex: number, exerciseIndex: number) => void;
    onUnlink: (partIndex: number, exerciseIndex: number) => void;
    onAmrapToggle?: (partIndex: number, exerciseIndex: number, setIndex: number) => void;
}

const ExerciseCardCompact: React.FC<ExerciseCardCompactProps> = ({
    exercise, exerciseIndex, partIndex, exerciseList, isCulprit, isExpertMode,
    onUpdate, onRemove, onReorder, onLink, onUnlink, onAmrapToggle
}) => {
    const [isExpanded, setIsExpanded] = useState(false);
    const [isSearching, setIsSearching] = useState(!exercise.name);
    const [searchQuery, setSearchQuery] = useState('');

    const exInfo = useMemo(() =>
        exerciseList.find(e => e.id === exercise.exerciseDbId || e.name === exercise.name),
        [exercise, exerciseList]
    );

    const setsSummary = useMemo(() => {
        const sets = exercise.sets || [];
        if (sets.length === 0) return 'Sin series';
        const reps = sets.map(s => s.targetReps || 0);
        const allSame = reps.every(r => r === reps[0]);
        const rpeVals = sets.map(s => s.targetRPE).filter(Boolean);
        const avgRpe = rpeVals.length ? (rpeVals.reduce((a, b) => a! + b!, 0)! / rpeVals.length).toFixed(1) : null;

        if (allSame && reps[0]) {
            return `${sets.length}x${reps[0]}${avgRpe ? ` @${avgRpe}` : ''}`;
        }
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
            draft.sets.push({
                id: crypto.randomUUID(),
                targetReps: last?.targetReps || 10,
                targetRPE: last?.targetRPE || 8,
                intensityMode: last?.intensityMode || 'rpe'
            });
        });
    }, [partIndex, exerciseIndex, onUpdate]);

    const handleRemoveSet = useCallback((setIndex: number) => {
        onUpdate(partIndex, exerciseIndex, (draft) => {
            draft.sets.splice(setIndex, 1);
        });
    }, [partIndex, exerciseIndex, onUpdate]);

    const formatRest = (seconds: number) => {
        const m = Math.floor(seconds / 60);
        const s = seconds % 60;
        return `${m}:${s.toString().padStart(2, '0')}`;
    };

    if (isSearching) {
        return (
            <div className="bg-zinc-900/60 border border-white/10 rounded-2xl p-3 animate-fade-in">
                <div className="relative mb-2">
                    <SearchIcon size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={e => setSearchQuery(e.target.value)}
                        placeholder="Buscar ejercicio..."
                        autoFocus
                        className="w-full bg-black border border-white/10 rounded-xl pl-9 pr-3 py-2.5 text-xs text-white placeholder-zinc-600 focus:ring-1 focus:ring-white/30"
                    />
                </div>
                <div className="max-h-48 overflow-y-auto custom-scrollbar space-y-0.5">
                    {filteredExercises.map(ex => (
                        <button
                            key={ex.id || ex.name}
                            onClick={() => handleSelectExercise(ex)}
                            className="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 transition-colors"
                        >
                            <span className="text-xs font-bold text-white">{ex.name}</span>
                            <span className="text-[9px] text-zinc-500 ml-2">
                                {ex.involvedMuscles?.filter(m => m.role === 'primary').map(m => m.muscle).join(', ')}
                            </span>
                        </button>
                    ))}
                </div>
                {exercise.name && (
                    <button onClick={() => setIsSearching(false)} className="w-full mt-2 py-1.5 text-[10px] font-bold text-zinc-500 hover:text-white transition-colors">
                        Cancelar
                    </button>
                )}
            </div>
        );
    }

    return (
        <div className={`rounded-2xl border transition-all ${
            isCulprit
                ? 'bg-red-950/20 border-red-500/20'
                : isExpanded
                    ? 'bg-zinc-900/80 border-white/10'
                    : 'bg-zinc-900/40 border-white/5 hover:border-white/10'
        }`}>
            {/* Collapsed view */}
            <button
                onClick={() => setIsExpanded(!isExpanded)}
                className="w-full flex items-center gap-3 p-3 text-left"
            >
                <button
                    onClick={(e) => { e.stopPropagation(); onUpdate(partIndex, exerciseIndex, d => { d.isStarTarget = !d.isStarTarget; }); }}
                    className={`shrink-0 ${exercise.isStarTarget ? 'text-yellow-400' : 'text-zinc-700'}`}
                >
                    <StarIcon size={14} filled={exercise.isStarTarget} />
                </button>

                <div className="flex-1 min-w-0">
                    <button
                        onClick={(e) => { e.stopPropagation(); setIsSearching(true); }}
                        className="text-xs font-black text-white uppercase tracking-tight truncate block text-left w-full hover:text-blue-400 transition-colors"
                    >
                        {exercise.name || 'Seleccionar ejercicio'}
                    </button>
                    <span className="text-[10px] text-zinc-500 font-bold">{setsSummary}</span>
                </div>

                {exercise.restTime && (
                    <span className="text-[9px] text-zinc-600 font-bold flex items-center gap-0.5 shrink-0">
                        <ClockIcon size={10} />
                        {formatRest(exercise.restTime)}
                    </span>
                )}

                <ChevronRightIcon
                    size={14}
                    className={`text-zinc-600 transition-transform shrink-0 ${isExpanded ? 'rotate-90' : ''}`}
                />
            </button>

            {/* Expanded view */}
            {isExpanded && (
                <div className="px-3 pb-3 space-y-3 border-t border-white/5 pt-3 animate-fade-in">
                    {/* Actions bar */}
                    <div className="flex items-center gap-1.5 flex-wrap">
                        <button
                            onClick={() => onLink(partIndex, exerciseIndex)}
                            className="px-2 py-1 rounded-md border border-white/10 text-[9px] font-bold text-zinc-500 hover:text-white hover:bg-white/5 transition-all flex items-center gap-1"
                        >
                            <LinkIcon size={10} /> Biserie
                        </button>
                        <button
                            onClick={() => onReorder(partIndex, exerciseIndex, 'up')}
                            className="p-1 rounded-md border border-white/10 text-zinc-500 hover:text-white transition-all"
                        >
                            <ArrowUpIcon size={12} />
                        </button>
                        <button
                            onClick={() => onReorder(partIndex, exerciseIndex, 'down')}
                            className="p-1 rounded-md border border-white/10 text-zinc-500 hover:text-white transition-all"
                        >
                            <ArrowDownIcon size={12} />
                        </button>
                        <div className="flex-1" />
                        <button
                            onClick={() => onRemove(partIndex, exerciseIndex)}
                            className="p-1 rounded-md border border-white/10 text-zinc-500 hover:text-red-400 hover:border-red-400/30 transition-all"
                        >
                            <TrashIcon size={12} />
                        </button>
                    </div>

                    {/* Rest time + training mode */}
                    <div className="grid grid-cols-2 gap-2">
                        <div>
                            <label className="text-[8px] font-bold text-zinc-600 uppercase block mb-1">Descanso</label>
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
                                className="w-full bg-black border border-white/10 rounded-lg px-2 py-1.5 text-center text-xs font-bold text-white focus:ring-1 focus:ring-white/30"
                            />
                        </div>
                        <div>
                            <label className="text-[8px] font-bold text-zinc-600 uppercase block mb-1">Modo</label>
                            <select
                                value={exercise.trainingMode || 'reps'}
                                onChange={e => onUpdate(partIndex, exerciseIndex, d => { d.trainingMode = e.target.value as any; })}
                                className="w-full bg-black border border-white/10 rounded-lg px-2 py-1.5 text-center text-[10px] font-bold text-white focus:ring-1 focus:ring-white/30"
                            >
                                <option value="reps">Repeticiones</option>
                                <option value="percent">% 1RM</option>
                                <option value="time">Tiempo</option>
                                <option value="custom">Libre</option>
                            </select>
                        </div>
                    </div>

                    {/* 1RM calculator (only in percent mode) */}
                    {exercise.trainingMode === 'percent' && (
                        <div className="bg-black/50 border border-white/5 rounded-xl p-2.5">
                            <div className="flex items-center justify-between mb-2">
                                <label className="text-[8px] font-bold text-zinc-500 uppercase">1RM Referencia</label>
                                <span className="text-xs font-black text-blue-400">
                                    {exercise.reference1RM ? `${exercise.reference1RM} kg` : 'No definido'}
                                </span>
                            </div>
                            <input
                                type="number"
                                value={exercise.reference1RM || ''}
                                onChange={e => onUpdate(partIndex, exerciseIndex, d => { d.reference1RM = parseFloat(e.target.value) || 0; })}
                                placeholder="1RM en kg"
                                className="w-full bg-black border border-white/10 rounded-lg px-2 py-1.5 text-center text-xs font-bold text-white focus:ring-1 focus:ring-white/30"
                            />
                        </div>
                    )}

                    {/* Sets */}
                    <div>
                        <label className="text-[8px] font-bold text-zinc-600 uppercase block mb-2">Series</label>
                        <SetCardGrid
                            sets={exercise.sets || []}
                            trainingMode={exercise.trainingMode}
                            onSetChange={handleSetChange}
                            onAddSet={handleAddSet}
                            onRemoveSet={handleRemoveSet}
                            onAmrapToggle={onAmrapToggle ? (setIdx) => onAmrapToggle(partIndex, exerciseIndex, setIdx) : undefined}
                            reference1RM={exercise.reference1RM}
                            isExpanded={true}
                        />
                    </div>
                </div>
            )}
        </div>
    );
};

export default ExerciseCardCompact;
