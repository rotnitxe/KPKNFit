import React from 'react';
import { ExerciseSet } from '../../types';
import { PlusIcon, XIcon, FlameIcon } from '../icons';

interface InlineSetTableProps {
    sets: ExerciseSet[];
    trainingMode?: string;
    onSetChange: (setIndex: number, fieldOrPartial: keyof ExerciseSet | Partial<ExerciseSet>, value?: any) => void;
    onAddSet: () => void;
    onRemoveSet: (index: number) => void;
    onAmrapToggle?: (setIndex: number) => void;
    reference1RM?: number;
}

const InlineSetTable: React.FC<InlineSetTableProps> = ({
    sets, trainingMode = 'reps', onSetChange, onAddSet, onRemoveSet, onAmrapToggle, reference1RM,
}) => {
    const isPercent = trainingMode === 'percent';
    const isTime = trainingMode === 'time';

    return (
        <div className="w-full">
            {/* Column headers */}
            <div className="flex items-center gap-1 px-1 pb-1.5 border-b border-white/5">
                <span className="w-8 text-[10px] font-bold text-[#555] uppercase text-center">Set</span>
                <span className="flex-1 text-[10px] font-bold text-[#555] uppercase">{isTime ? 'Seg' : 'Reps'}</span>
                {isPercent && <span className="w-14 text-[10px] font-bold text-[#555] uppercase text-right">%1RM</span>}
                <span className="w-16 text-[10px] font-bold text-[#555] uppercase text-right">Peso</span>
                <span className="w-14 text-[10px] font-bold text-[#555] uppercase text-center">RPE</span>
                <span className="w-16 text-[10px] font-bold text-[#555] uppercase text-center">Tipo</span>
                <span className="w-7" />
            </div>

            {/* Set rows */}
            {sets.map((set, i) => {
                const mode = set.intensityMode || 'rpe';
                const isAmrap = set.isAmrap || set.isCalibrator;
                return (
                    <div key={set.id || i} className="flex items-center gap-1 px-1 py-1.5 border-b border-white/[0.03] hover:bg-white/[0.02] transition-colors group">
                        {/* Set number */}
                        <span className="w-8 text-center text-xs font-mono text-[#999] font-bold">
                            {i + 1}
                        </span>

                        {/* Reps / Duration */}
                        <div className="flex-1">
                            <input
                                type="number"
                                value={isTime ? (set.targetDuration || '') : (set.targetReps || '')}
                                onChange={e => {
                                    const val = parseInt(e.target.value) || 0;
                                    onSetChange(i, isTime ? 'targetDuration' : 'targetReps', val);
                                }}
                                className="w-full bg-transparent border-b border-transparent focus:border-[#FC4C02] text-sm font-mono text-white py-0.5 transition-colors outline-none"
                                placeholder="—"
                            />
                        </div>

                        {/* %1RM */}
                        {isPercent && (
                            <div className="w-14">
                                <input
                                    type="number"
                                    value={set.targetPercentageRM || ''}
                                    onChange={e => onSetChange(i, 'targetPercentageRM', parseInt(e.target.value) || 0)}
                                    className="w-full bg-transparent border-b border-transparent focus:border-[#FC4C02] text-sm font-mono text-white py-0.5 text-right transition-colors outline-none"
                                    placeholder="%"
                                />
                            </div>
                        )}

                        {/* Weight */}
                        <div className="w-16">
                            <input
                                type="number"
                                step="0.5"
                                value={set.weight || (isPercent && reference1RM && set.targetPercentageRM ? Math.round((reference1RM * set.targetPercentageRM / 100) * 4) / 4 : '')}
                                onChange={e => onSetChange(i, 'weight', parseFloat(e.target.value) || 0)}
                                className="w-full bg-transparent border-b border-transparent focus:border-[#FC4C02] text-sm font-mono text-white py-0.5 text-right transition-colors outline-none"
                                placeholder="kg"
                            />
                        </div>

                        {/* Intensity */}
                        <div className="w-14 text-center">
                            {mode === 'failure' ? (
                                <span className="text-[10px] font-bold text-red-400">FALLO</span>
                            ) : mode === 'load' ? (
                                <span className="text-[10px] font-bold text-[#999]">—</span>
                            ) : (
                                <input
                                    type="number"
                                    step="0.5"
                                    max={10}
                                    value={mode === 'rir' ? (set.targetRIR ?? '') : (set.targetRPE ?? '')}
                                    onChange={e => {
                                        const val = parseFloat(e.target.value);
                                        onSetChange(i, mode === 'rir' ? 'targetRIR' : 'targetRPE', val);
                                    }}
                                    className="w-full bg-transparent border-b border-transparent focus:border-[#FC4C02] text-sm font-mono text-white py-0.5 text-center transition-colors outline-none"
                                    placeholder={mode === 'rir' ? 'RIR' : 'RPE'}
                                />
                            )}
                        </div>

                        {/* Type badge */}
                        <div className="w-16 flex items-center justify-center">
                            <select
                                value={mode}
                                onChange={e => onSetChange(i, 'intensityMode', e.target.value)}
                                className="bg-transparent text-[10px] font-bold text-[#999] border-none p-0 focus:ring-0 cursor-pointer text-center"
                            >
                                <option value="rpe" className="bg-black text-white">RPE</option>
                                <option value="rir" className="bg-black text-white">RIR</option>
                                <option value="failure" className="bg-black text-white">Fallo</option>
                                <option value="load" className="bg-black text-white">Carga</option>
                            </select>
                            {isAmrap && <FlameIcon size={10} className="text-yellow-400 ml-0.5 shrink-0" />}
                        </div>

                        {/* Actions */}
                        <div className="w-7 flex items-center justify-center">
                            {onAmrapToggle && (
                                <button
                                    onClick={() => onAmrapToggle(i)}
                                    className={`mr-0.5 opacity-0 group-hover:opacity-100 transition-opacity ${isAmrap ? 'text-yellow-400 opacity-100' : 'text-[#555]'}`}
                                    title="AMRAP"
                                >
                                    <FlameIcon size={10} />
                                </button>
                            )}
                            <button
                                onClick={() => onRemoveSet(i)}
                                className="text-[#555] hover:text-red-400 opacity-0 group-hover:opacity-100 transition-all"
                            >
                                <XIcon size={12} />
                            </button>
                        </div>
                    </div>
                );
            })}

            {/* Add row */}
            <button
                onClick={onAddSet}
                className="flex items-center gap-1.5 px-1 py-2 w-full text-left text-[#555] hover:text-[#FC4C02] transition-colors"
            >
                <PlusIcon size={12} />
                <span className="text-xs font-medium">Agregar serie</span>
            </button>
        </div>
    );
};

export default InlineSetTable;
