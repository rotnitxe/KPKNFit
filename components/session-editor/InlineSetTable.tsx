import React, { useEffect } from 'react';
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

    // Autocompletar peso en modo RM cuando hay reference1RM y reps
    useEffect(() => {
        if (!isPercent || !reference1RM) return;
        sets.forEach((set, i) => {
            const mode = set.intensityMode || 'solo_rm';
            const useIntensity = mode === 'solo_rm' || mode === 'rpe' || mode === 'rir' || mode === 'failure' || mode === 'amrap';
            if (!useIntensity && mode === 'load' && set.targetPercentageRM) {
                const w = Math.round((reference1RM * set.targetPercentageRM / 100) * 4) / 4;
                if (w > 0 && (set.weight == null || Math.abs(set.weight - w) > 0.01)) {
                    onSetChange(i, 'weight', w);
                }
                return;
            }
            const calculated = calculateWeightFrom1RMAndIntensity(reference1RM, set);
            if (calculated != null && calculated > 0) {
                const rounded = Math.round(calculated * 4) / 4;
                if (set.weight == null || Math.abs(set.weight - rounded) > 0.01) {
                    onSetChange(i, 'weight', rounded);
                }
            }
        });
    }, [isPercent, reference1RM, sets, onSetChange]);

    const showPercentColumn = isPercent && sets.some(s => (s.intensityMode || 'solo_rm') === 'load' && s.targetPercentageRM != null);

    return (
        <div className="w-full overflow-x-auto">
            <div className="min-w-[380px]">
                <div className="flex items-center gap-1 px-1 pb-1.5 border-b border-white/5">
                    <span className="w-8 text-[10px] font-bold text-[#555] uppercase text-center">Set</span>
                    <span className="w-12 max-w-[48px] text-[10px] font-bold text-[#555] uppercase">{isTime ? 'Seg' : 'Reps'}</span>
                    {showPercentColumn && <span className="w-14 text-[10px] font-bold text-[#555] uppercase text-right">%1RM</span>}
                    {showWeightColumn && <span className="w-16 text-[10px] font-bold text-[#555] uppercase text-right">Peso</span>}
                    <span className="w-14 text-[10px] font-bold text-[#555] uppercase text-center">Int.</span>
                    <span className="w-20 text-[10px] font-bold text-[#555] uppercase text-center">Tipo</span>
                    <span className="w-7" />
                </div>

                {sets.map((set, i) => {
                    const mode = set.intensityMode || (isPercent ? 'solo_rm' : 'rpe');
                    const isAmrap = set.isAmrap || set.isCalibrator;
                    const isSoloRm = mode === 'solo_rm';
                    const useIntensityWeight = isPercent && (mode === 'solo_rm' || mode === 'rpe' || mode === 'rir' || mode === 'failure' || mode === 'amrap');
                    const estimatedKg = useIntensityWeight ? getEstimatedWeight(set) : (mode === 'load' && set.targetPercentageRM ? getEstimatedWeight(set) : null);
                    const displayWeight = set.weight ?? (isPercent && reference1RM && set.targetPercentageRM ? Math.round((reference1RM * set.targetPercentageRM / 100) * 4) / 4 : estimatedKg);
                    return (
                        <div key={set.id || i} className="flex items-center gap-1 px-1 py-2 border-b border-white/[0.03] hover:bg-white/[0.02] transition-colors group min-h-[40px]">
                            <span className="w-8 text-center text-xs font-mono text-[#999] font-bold">{i + 1}</span>

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
                                        const val = parseInt(raw) || 0;
                                        const clamped = isTime ? val : Math.min(99, Math.max(0, val));
                                        onSetChange(i, isTime ? 'targetDuration' : 'targetReps', clamped);
                                    }}
                                    className="w-full bg-white/[0.03] border-b border-orange-500/20 focus:border-orange-500/60 text-sm font-mono text-white py-1 px-1 rounded transition-colors outline-none placeholder-orange-900/50"
                                    placeholder="—"
                                />
                            </div>

                            {showPercentColumn && mode === 'load' && (
                                <div className="w-14">
                                    <input
                                        type="number"
                                        value={set.targetPercentageRM ?? ''}
                                        onChange={e => onSetChange(i, 'targetPercentageRM', e.target.value === '' ? undefined : parseInt(e.target.value))}
                                        className="w-full bg-white/[0.03] border-b border-orange-500/20 focus:border-orange-500/60 text-sm font-mono text-white py-1 text-right rounded transition-colors outline-none placeholder-orange-900/50"
                                        placeholder="%"
                                    />
                                </div>
                            )}

                            {showWeightColumn && (
                                <div className="w-16 flex flex-col items-end">
                                    {useIntensityWeight && estimatedKg != null ? (
                                        <span className="text-[10px] font-mono text-orange-400/90">{estimatedKg}kg</span>
                                    ) : (
                                        <input
                                            type="number"
                                            step="0.5"
                                            value={displayWeight ?? ''}
                                            onChange={e => onSetChange(i, 'weight', e.target.value === '' ? undefined : (parseFloat(e.target.value) || 0))}
                                            className="w-full bg-white/[0.03] border-b border-orange-500/20 focus:border-orange-500/60 text-sm font-mono text-white py-1 text-right rounded transition-colors outline-none placeholder-orange-900/50"
                                            placeholder="kg"
                                        />
                                    )}
                                </div>
                            )}

                            <div className="w-14 text-center">
                                {mode === 'failure' ? (
                                    <span className="text-[10px] font-bold text-red-400">FALLO</span>
                                ) : mode === 'solo_rm' ? (
                                    <span className="text-[10px] font-bold text-orange-400/90">RM</span>
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
                                        className="w-full bg-white/[0.03] border-b border-orange-500/20 focus:border-orange-500/60 text-sm font-mono text-white py-1 text-center rounded transition-colors outline-none placeholder-orange-900/50"
                                        placeholder={mode === 'rir' ? 'RIR' : 'RPE'}
                                    />
                                )}
                            </div>

                            <div className="w-20 flex items-center justify-center">
                                <select
                                    value={mode}
                                    onChange={e => onSetChange(i, 'intensityMode', e.target.value)}
                                    className="bg-black/50 text-[10px] font-bold text-[#999] border border-white/10 rounded px-1 py-0.5 focus:ring-1 focus:ring-orange-500/30 cursor-pointer text-center"
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
                    className="flex items-center gap-1.5 px-1 py-2.5 w-full text-left text-[#555] hover:text-[#FC4C02] transition-colors"
                >
                    <PlusIcon size={12} />
                    <span className="text-xs font-medium">Agregar serie</span>
                </button>
            </div>
        </div>
    );
};

export default InlineSetTable;
