import React from 'react';
import { ExerciseSet } from '../../types';
import { PlusIcon, XIcon, FlameIcon } from '../icons';
import { calculateWeightFrom1RMAndIntensity } from '../../utils/calculations';

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
    const showWeightColumn = isPercent;

    const getEstimatedWeight = (set: ExerciseSet): number | null => {
        if (!reference1RM) return null;
        const mode = set.intensityMode || (isPercent ? 'solo_rm' : 'rpe');
        if (mode === 'load' && set.targetPercentageRM) {
            return Math.round((reference1RM * set.targetPercentageRM / 100) * 4) / 4;
        }
        const w = calculateWeightFrom1RMAndIntensity(reference1RM, set);
        return w != null ? Math.round(w * 4) / 4 : null;
    };

    // No auto-rellenar peso: el usuario debe poder borrar y que no vuelva a aparecer.
    // La sugerencia se muestra solo como placeholder o en UI separada.

    const showPercentColumn = isPercent && sets.some(s => (s.intensityMode || 'solo_rm') === 'load' && s.targetPercentageRM != null);

    return (
        <div className="session-card-base w-full overflow-x-auto">
            <div className="session-table min-w-[380px]" data-tabular="true">
                <div className="flex items-center gap-1 px-2 py-1.5 border-b border-[#2A2D38] text-[10px] font-bold uppercase tracking-widest text-[#A0A7B8]">
                    <span className="w-8 text-center">Set</span>
                    <span className="w-12 max-w-[48px]">{isTime ? 'Seg' : 'Reps'}</span>
                    {showPercentColumn && <span className="w-14 text-right">%1RM</span>}
                    {showWeightColumn && <span className="w-16 text-right">Peso</span>}
                    <span className="w-14 text-center">Int.</span>
                    <span className="w-20 text-center">Tipo</span>
                    <span className="w-7" />
                </div>

                {sets.map((set, i) => {
                    const mode = set.intensityMode || (isPercent ? 'solo_rm' : 'rpe');
                    const isAmrap = set.isAmrap || set.isCalibrator;
                    const useIntensityWeight = isPercent && (mode === 'solo_rm' || mode === 'rpe' || mode === 'rir' || mode === 'failure' || mode === 'amrap');
                    const estimatedKg = useIntensityWeight ? getEstimatedWeight(set) : (mode === 'load' && set.targetPercentageRM ? getEstimatedWeight(set) : null);
                    const suggestedWeight = (isPercent && reference1RM && set.targetPercentageRM) ? Math.round((reference1RM * set.targetPercentageRM / 100) * 4) / 4 : estimatedKg;
                    const weightValue = set.weight != null ? String(set.weight) : '';
                    return (
                        <div key={set.id || i} className="flex items-center gap-1 px-2 py-2 border-b border-white/[0.03] hover:bg-white/[0.02] transition-colors group min-h-[48px]">
                            <span className="w-8 text-center text-xs font-mono text-[#999] font-bold tabular-nums">{i + 1}</span>

                            <div className="w-12 max-w-[48px]">
                                <input
                                    type="number"
                                    min={0}
                                    max={isTime ? 9999 : 99}
                                    maxLength={isTime ? 4 : 2}
                                    value={isTime ? (set.targetDuration ?? '') : (set.targetReps ?? '')}
                                    onChange={e => {
                                        const raw = e.target.value;
                                        if (raw === '') { onSetChange(i, isTime ? 'targetDuration' : 'targetReps', undefined); return; }
                                        const val = parseInt(raw, 10);
                                        if (Number.isNaN(val)) return;
                                        const clamped = isTime ? val : Math.min(99, Math.max(0, val));
                                        onSetChange(i, isTime ? 'targetDuration' : 'targetReps', clamped);
                                    }}
                                    className="w-full bg-transparent border-b border-transparent focus:border-[#00F0FF] text-sm font-mono text-white py-1 px-1 rounded transition-colors outline-none placeholder-[#A0A7B8]/80 tabular-nums"
                                    placeholder="—"
                                />
                            </div>

                            {showPercentColumn && mode === 'load' && (
                                <div className="w-14">
                                    <input
                                        type="number"
                                        value={set.targetPercentageRM ?? ''}
                                        onChange={e => onSetChange(i, 'targetPercentageRM', e.target.value === '' ? undefined : parseInt(e.target.value, 10))}
                                        className="w-full bg-transparent border-b border-transparent focus:border-[#00F0FF] text-sm font-mono text-white py-1 text-right rounded transition-colors outline-none placeholder-[#A0A7B8]/80 tabular-nums"
                                        placeholder="%"
                                    />
                                </div>
                            )}

                            {showWeightColumn && (
                                <div className="w-16 flex flex-col items-end">
                                    {useIntensityWeight && estimatedKg != null ? (
                                        <span className="text-[10px] font-mono text-cyber-cyan/90">{estimatedKg}kg</span>
                                    ) : (
                                        <input
                                            type="number"
                                            step="0.5"
                                            value={weightValue}
                                            onChange={e => {
                                                const raw = e.target.value;
                                                onSetChange(i, 'weight', raw === '' ? undefined : (parseFloat(raw) || 0));
                                            }}
                                            className="w-full bg-transparent border-b border-transparent focus:border-[#00F0FF] text-sm font-mono text-white py-1 text-right rounded transition-colors outline-none placeholder-[#A0A7B8]/80 tabular-nums"
                                            placeholder={suggestedWeight != null ? String(suggestedWeight) : 'kg'}
                                        />
                                    )}
                                </div>
                            )}

                            <div className="w-14 text-center">
                                {mode === 'failure' ? (
                                    <span className="text-[10px] font-bold text-red-400">FALLO</span>
                                ) : mode === 'solo_rm' ? (
                                    <span className="text-[10px] font-bold text-cyber-cyan/90">RM</span>
                                ) : mode === 'load' ? (
                                    <span className="text-[10px] font-bold text-[#999]">—</span>
                                ) : (
                                    <input
                                        type="number"
                                        step="0.5"
                                        min={0}
                                        max={10}
                                        value={mode === 'rir' ? (set.targetRIR ?? '') : (set.targetRPE ?? '')}
                                        onChange={e => {
                                            const raw = e.target.value;
                                            if (raw === '') { onSetChange(i, mode === 'rir' ? 'targetRIR' : 'targetRPE', undefined); return; }
                                            const val = parseFloat(raw);
                                            if (!isNaN(val)) onSetChange(i, mode === 'rir' ? 'targetRIR' : 'targetRPE', val);
                                        }}
                                        className="w-full bg-transparent border-b border-transparent focus:border-[#00F0FF] text-sm font-mono text-white py-1 text-center rounded transition-colors outline-none placeholder-[#A0A7B8]/80 tabular-nums"
                                        placeholder={mode === 'rir' ? 'RIR' : 'RPE'}
                                    />
                                )}
                            </div>

                            <div className="w-20 flex items-center justify-center">
                                <select
                                    value={mode}
                                    onChange={e => onSetChange(i, 'intensityMode', e.target.value)}
                                    className="bg-black/50 text-[10px] font-bold text-[#999] border border-white/10 rounded px-1 py-0.5 focus:ring-1 focus:ring-cyber-cyan/30 cursor-pointer text-center"
                                >
                                    {isPercent && <option value="solo_rm" className="bg-black text-white">SOLO RM</option>}
                                    <option value="rpe" className="bg-black text-white">RPE</option>
                                    <option value="rir" className="bg-black text-white">RIR</option>
                                    <option value="failure" className="bg-black text-white">Fallo</option>
                                    <option value="load" className="bg-black text-white">Carga</option>
                                </select>
                                {isAmrap && <FlameIcon size={10} className="text-yellow-400 ml-0.5 shrink-0" />}
                            </div>

                            <div className="w-7 flex items-center justify-center gap-0.5">
                                {onAmrapToggle && (
                                    <button
                                        onClick={() => onAmrapToggle(i)}
                                        className={`p-1.5 rounded transition-opacity ${isAmrap ? 'text-yellow-400 opacity-100' : 'text-[#555] hover:text-white opacity-0 group-hover:opacity-100'}`}
                                        title="AMRAP"
                                    >
                                        <FlameIcon size={10} />
                                    </button>
                                )}
                                <button
                                    onClick={() => onRemoveSet(i)}
                                    className="p-1.5 text-[#555] hover:text-red-400 transition-all rounded"
                                >
                                    <XIcon size={12} />
                                </button>
                            </div>
                        </div>
                    );
                })}

                <button
                    onClick={onAddSet}
                    className="flex items-center gap-1.5 px-2 py-2.5 w-full text-left text-[#00F0FF]/90 hover:text-[#00F0FF] transition-colors border-none bg-transparent"
                >
                    <PlusIcon size={12} />
                    <span className="text-xs font-medium">Añadir serie</span>
                </button>
            </div>
        </div>
    );
};

export default InlineSetTable;
