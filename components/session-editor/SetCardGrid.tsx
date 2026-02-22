import React, { useState } from 'react';
import { ExerciseSet } from '../../types';
import { PlusIcon, XIcon, FlameIcon } from '../icons';

interface SetCardGridProps {
    sets: ExerciseSet[];
    trainingMode?: string;
    onSetChange: (setIndex: number, fieldOrPartial: keyof ExerciseSet | Partial<ExerciseSet>, value?: any) => void;
    onAddSet: () => void;
    onRemoveSet: (index: number) => void;
    onAmrapToggle?: (setIndex: number) => void;
    reference1RM?: number;
    isExpanded?: boolean;
}

const SetCardGrid: React.FC<SetCardGridProps> = ({
    sets, trainingMode = 'reps', onSetChange, onAddSet, onRemoveSet, onAmrapToggle, reference1RM, isExpanded = false
}) => {
    const [editingSet, setEditingSet] = useState<number | null>(null);

    const getSetSummary = (set: ExerciseSet, index: number) => {
        const mode = set.intensityMode || 'rpe';
        let repsStr = '';
        if (trainingMode === 'time') {
            repsStr = `${set.targetDuration || 0}s`;
        } else {
            repsStr = `${set.targetReps || '?'}r`;
        }

        let intensityStr = '';
        if (mode === 'rpe') intensityStr = set.targetRPE ? `@${set.targetRPE}` : '';
        else if (mode === 'rir') intensityStr = set.targetRIR !== undefined ? `RIR ${set.targetRIR}` : '';
        else if (mode === 'failure') intensityStr = 'Fallo';
        else if (mode === 'load' && set.weight) intensityStr = `${set.weight}kg`;

        let percentStr = '';
        if (trainingMode === 'percent' && set.targetPercentageRM) {
            percentStr = `${set.targetPercentageRM}%`;
        }

        return { repsStr, intensityStr, percentStr, isAmrap: set.isAmrap || set.isCalibrator };
    };

    if (!isExpanded) {
        return (
            <div className="flex flex-wrap gap-1.5">
                {sets.map((set, i) => {
                    const { repsStr, intensityStr, isAmrap } = getSetSummary(set, i);
                    return (
                        <button
                            key={set.id || i}
                            onClick={() => setEditingSet(editingSet === i ? null : i)}
                            className={`px-2.5 py-1 rounded-lg text-[10px] font-bold transition-all border ${
                                editingSet === i
                                    ? 'bg-white text-black border-white'
                                    : isAmrap
                                        ? 'bg-yellow-500/10 border-yellow-500/30 text-yellow-400'
                                        : 'bg-zinc-900 border-white/5 text-zinc-300 hover:border-white/20'
                            }`}
                        >
                            <span className="font-black">S{i + 1}</span>
                            {' '}{repsStr}{intensityStr ? ` ${intensityStr}` : ''}
                            {isAmrap && <FlameIcon size={9} className="inline ml-0.5" />}
                        </button>
                    );
                })}
                <button
                    onClick={onAddSet}
                    className="px-2 py-1 rounded-lg border border-dashed border-white/10 text-zinc-600 hover:text-white hover:border-white/30 transition-all"
                >
                    <PlusIcon size={12} />
                </button>
            </div>
        );
    }

    return (
        <div className="space-y-2">
            <div className="flex flex-wrap gap-1.5 mb-2">
                {sets.map((set, i) => {
                    const { repsStr, intensityStr, isAmrap } = getSetSummary(set, i);
                    return (
                        <button
                            key={set.id || i}
                            onClick={() => setEditingSet(editingSet === i ? null : i)}
                            className={`px-2.5 py-1 rounded-lg text-[10px] font-bold transition-all border ${
                                editingSet === i
                                    ? 'bg-white text-black border-white'
                                    : isAmrap
                                        ? 'bg-yellow-500/10 border-yellow-500/30 text-yellow-400'
                                        : 'bg-zinc-900 border-white/5 text-zinc-300 hover:border-white/20'
                            }`}
                        >
                            S{i + 1} {repsStr} {intensityStr}
                        </button>
                    );
                })}
                <button onClick={onAddSet} className="px-2 py-1 rounded-lg border border-dashed border-white/10 text-zinc-600 hover:text-white hover:border-white/30 transition-all">
                    <PlusIcon size={12} />
                </button>
            </div>

            {editingSet !== null && sets[editingSet] && (
                <div className="bg-zinc-900/80 border border-white/10 rounded-xl p-3 space-y-3 animate-fade-in">
                    <div className="flex items-center justify-between">
                        <span className="text-[10px] font-black text-zinc-400 uppercase tracking-widest">Serie {editingSet + 1}</span>
                        <div className="flex gap-2">
                            {onAmrapToggle && (
                                <button
                                    onClick={() => onAmrapToggle(editingSet)}
                                    className={`text-[9px] font-bold px-2 py-1 rounded-md border transition-all ${
                                        sets[editingSet].isAmrap ? 'bg-yellow-500/20 border-yellow-500/30 text-yellow-400' : 'border-white/10 text-zinc-500 hover:text-white'
                                    }`}
                                >
                                    <FlameIcon size={10} className="inline mr-0.5" /> AMRAP
                                </button>
                            )}
                            <button
                                onClick={() => { onRemoveSet(editingSet); setEditingSet(null); }}
                                className="p-1 rounded-md border border-white/10 text-zinc-500 hover:text-red-400 hover:border-red-400/30 transition-all"
                            >
                                <XIcon size={12} />
                            </button>
                        </div>
                    </div>

                    <div className="grid grid-cols-3 gap-2">
                        <div>
                            <label className="text-[8px] font-bold text-zinc-600 uppercase block mb-1">
                                {trainingMode === 'time' ? 'Segundos' : 'Reps'}
                            </label>
                            <input
                                type="number"
                                value={trainingMode === 'time' ? (sets[editingSet].targetDuration || '') : (sets[editingSet].targetReps || '')}
                                onChange={e => {
                                    const val = parseInt(e.target.value) || 0;
                                    if (trainingMode === 'time') {
                                        onSetChange(editingSet, 'targetDuration', val);
                                    } else {
                                        onSetChange(editingSet, 'targetReps', val);
                                    }
                                }}
                                className="w-full bg-black border border-white/10 rounded-lg px-2 py-1.5 text-center text-xs font-bold text-white focus:ring-1 focus:ring-white/30"
                            />
                        </div>
                        <div>
                            <label className="text-[8px] font-bold text-zinc-600 uppercase block mb-1">Intensidad</label>
                            <select
                                value={sets[editingSet].intensityMode || 'rpe'}
                                onChange={e => onSetChange(editingSet, 'intensityMode', e.target.value)}
                                className="w-full bg-black border border-white/10 rounded-lg px-1 py-1.5 text-center text-[10px] font-bold text-white focus:ring-1 focus:ring-white/30"
                            >
                                <option value="rpe">RPE</option>
                                <option value="rir">RIR</option>
                                <option value="failure">Fallo</option>
                                <option value="load">Carga</option>
                            </select>
                        </div>
                        <div>
                            <label className="text-[8px] font-bold text-zinc-600 uppercase block mb-1">Valor</label>
                            {(sets[editingSet].intensityMode || 'rpe') === 'failure' ? (
                                <div className="w-full bg-red-900/20 border border-red-500/30 rounded-lg py-1.5 text-center text-[10px] font-black text-red-400">FALLO</div>
                            ) : (sets[editingSet].intensityMode || 'rpe') === 'load' ? (
                                <input
                                    type="number"
                                    value={sets[editingSet].weight || ''}
                                    onChange={e => onSetChange(editingSet, 'weight', parseFloat(e.target.value) || 0)}
                                    placeholder="kg"
                                    className="w-full bg-black border border-white/10 rounded-lg px-2 py-1.5 text-center text-xs font-bold text-white focus:ring-1 focus:ring-white/30"
                                />
                            ) : (
                                <input
                                    type="number"
                                    step="0.5"
                                    max={10}
                                    value={(sets[editingSet].intensityMode || 'rpe') === 'rir' ? (sets[editingSet].targetRIR ?? '') : (sets[editingSet].targetRPE ?? '')}
                                    onChange={e => {
                                        const val = parseFloat(e.target.value);
                                        if ((sets[editingSet].intensityMode || 'rpe') === 'rir') {
                                            onSetChange(editingSet, 'targetRIR', val);
                                        } else {
                                            onSetChange(editingSet, 'targetRPE', val);
                                        }
                                    }}
                                    className="w-full bg-black border border-white/10 rounded-lg px-2 py-1.5 text-center text-xs font-bold text-white focus:ring-1 focus:ring-white/30"
                                />
                            )}
                        </div>
                    </div>

                    {trainingMode === 'percent' && (
                        <div className="grid grid-cols-2 gap-2">
                            <div>
                                <label className="text-[8px] font-bold text-zinc-600 uppercase block mb-1">% 1RM</label>
                                <input
                                    type="number"
                                    value={sets[editingSet].targetPercentageRM || ''}
                                    onChange={e => onSetChange(editingSet, 'targetPercentageRM', parseInt(e.target.value) || 0)}
                                    className="w-full bg-black border border-white/10 rounded-lg px-2 py-1.5 text-center text-xs font-bold text-white focus:ring-1 focus:ring-white/30"
                                />
                            </div>
                            {reference1RM ? (
                                <div>
                                    <label className="text-[8px] font-bold text-zinc-600 uppercase block mb-1">Peso Est.</label>
                                    <div className="w-full bg-zinc-900 border border-white/5 rounded-lg py-1.5 text-center text-xs font-bold text-blue-400">
                                        {Math.round((reference1RM * (sets[editingSet].targetPercentageRM || 0) / 100) * 4) / 4} kg
                                    </div>
                                </div>
                            ) : null}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default SetCardGrid;
