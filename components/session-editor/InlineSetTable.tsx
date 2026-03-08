import React, { useState, useRef, useCallback, useEffect } from 'react';
import { ExerciseSet } from '../../types';
import { PlusIcon, FlameIcon } from '../icons';
import { calculateWeightFrom1RMAndIntensity } from '../../utils/calculations';
import NumpadOverlay from '../workout/NumpadOverlay';

const SWIPE_THRESHOLD = 80;
const SWIPE_ACTIVATE_PX = 25;
const HINT_KEY = 'kpkn_seen_swipe_delete_hint';

interface InlineSetTableProps {
    sets: ExerciseSet[];
    trainingMode?: string;
    onSetChange: (setIndex: number, fieldOrPartial: keyof ExerciseSet | Partial<ExerciseSet>, value?: any) => void;
    onAddSet: () => void;
    onRemoveSet: (index: number) => void;
    onAmrapToggle?: (setIndex: number) => void;
    reference1RM?: number;
    onFirstAddSet?: () => void;
    weightUnit?: string;
}

const InlineSetTable: React.FC<InlineSetTableProps> = ({
    sets, trainingMode = 'reps', onSetChange, onAddSet, onRemoveSet, onAmrapToggle, reference1RM, onFirstAddSet, weightUnit = 'kg',
}) => {
    const [numpad, setNumpad] = useState<{ setIndex: number; field: 'targetReps' | 'targetDuration' | 'targetPercentageRM' | 'weight' | 'targetRIR' | 'targetRPE' } | null>(null);
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

    const showPercentColumn = isPercent && sets.some(s => (s.intensityMode || 'solo_rm') === 'load' && s.targetPercentageRM != null);

    const handleAddSetClick = useCallback(() => {
        const seen = localStorage.getItem(HINT_KEY);
        if (!seen && onFirstAddSet) {
            onFirstAddSet();
        }
        onAddSet();
    }, [onAddSet, onFirstAddSet]);

    return (
        <div className="session-card-base w-full overflow-x-auto">
            <div className="session-table min-w-[380px]" data-tabular="true">
                <div className="flex items-center gap-1 px-2 py-1.5 border-b border-[#2A2D38] text-[10px] font-bold uppercase tracking-widest text-[#A0A7B8]">
                    <span className="w-8 text-center">Set</span>
                    <span className="w-12 max-w-[48px]">{isTime ? 'Seg' : 'Reps'}</span>
                    {showPercentColumn && <span className="w-14 text-right">%1RM</span>}
                    {showWeightColumn && <span className="w-14 text-right">Peso</span>}
                    <span className="w-14 text-center">Int.</span>
                    <span className="w-20 text-center">Tipo</span>
                </div>

                {sets.map((set, i) => (
                    <SwipeableSetRow
                        key={set.id || i}
                        set={set}
                        setIndex={i}
                        isPercent={isPercent}
                        isTime={isTime}
                        showPercentColumn={showPercentColumn}
                        showWeightColumn={showWeightColumn}
                        getEstimatedWeight={getEstimatedWeight}
                        reference1RM={reference1RM}
                        onSetChange={onSetChange}
                        onRemoveSet={onRemoveSet}
                        onAmrapToggle={onAmrapToggle}
                        onOpenNumpad={setNumpad}
                    />
                ))}

                <button
                    onClick={handleAddSetClick}
                    className="flex items-center gap-1.5 px-2 py-2.5 w-full text-left text-[#00F0FF]/90 hover:text-[#00F0FF] transition-colors border-none bg-transparent"
                >
                    <PlusIcon size={12} />
                    <span className="text-xs font-medium">Añadir serie</span>
                </button>
            </div>
            {numpad && (() => {
                const set = sets[numpad.setIndex];
                if (!set) return null;
                const val = numpad.field === 'targetReps' ? (set.targetReps ?? '') : numpad.field === 'targetDuration' ? (set.targetDuration ?? '') : numpad.field === 'targetPercentageRM' ? (set.targetPercentageRM ?? '') : numpad.field === 'weight' ? (set.weight != null ? String(set.weight) : '') : numpad.field === 'targetRIR' ? (set.targetRIR ?? '') : (set.targetRPE ?? '');
                return (
                    <NumpadOverlay
                        value={String(val)}
                        onChange={(v) => {
                            if (numpad.field === 'targetReps') onSetChange(numpad.setIndex, 'targetReps', v === '' ? undefined : parseInt(v, 10));
                            else if (numpad.field === 'targetDuration') onSetChange(numpad.setIndex, 'targetDuration', v === '' ? undefined : parseInt(v, 10));
                            else if (numpad.field === 'targetPercentageRM') onSetChange(numpad.setIndex, 'targetPercentageRM', v === '' ? undefined : parseInt(v, 10));
                            else if (numpad.field === 'weight') onSetChange(numpad.setIndex, 'weight', v === '' ? undefined : parseFloat(v) || 0);
                            else if (numpad.field === 'targetRIR') onSetChange(numpad.setIndex, 'targetRIR', v === '' ? undefined : parseFloat(v));
                            else onSetChange(numpad.setIndex, 'targetRPE', v === '' ? undefined : parseFloat(v));
                        }}
                        onClose={() => setNumpad(null)}
                        mode={numpad.field === 'weight' ? 'decimal' : 'integer'}
                        label={numpad.field === 'weight' ? `Kg (${weightUnit})` : numpad.field === 'targetDuration' ? 'Seg' : numpad.field === 'targetPercentageRM' ? '%1RM' : numpad.field === 'targetRIR' ? 'RIR' : 'RPE'}
                        showNextButton={false}
                    />
                );
            })()}
        </div>
    );
};

const SwipeableSetRow: React.FC<{
    set: ExerciseSet;
    setIndex: number;
    isPercent: boolean;
    isTime: boolean;
    showPercentColumn: boolean;
    showWeightColumn: boolean;
    getEstimatedWeight: (s: ExerciseSet) => number | null;
    reference1RM?: number;
    onSetChange: (i: number, f: keyof ExerciseSet | Partial<ExerciseSet>, v?: any) => void;
    onRemoveSet: (i: number) => void;
    onAmrapToggle?: (i: number) => void;
    onOpenNumpad: (opts: { setIndex: number; field: 'targetReps' | 'targetDuration' | 'targetPercentageRM' | 'weight' | 'targetRIR' | 'targetRPE' }) => void;
}> = ({ set, setIndex, isPercent, isTime, showPercentColumn, showWeightColumn, getEstimatedWeight, reference1RM, onSetChange, onRemoveSet, onAmrapToggle, onOpenNumpad }) => {
    const rowRef = useRef<HTMLDivElement>(null);
    const contentRef = useRef<HTMLDivElement>(null);
    const [swipeOffset, setSwipeOffset] = useState(0);
    const swipeOffsetRef = useRef(0);
    const startX = useRef(0);
    const startY = useRef(0);
    const isHorizontalSwipe = useRef<boolean | null>(null);
    const activePointerId = useRef<number | null>(null);

    useEffect(() => { swipeOffsetRef.current = swipeOffset; }, [swipeOffset]);

    const mode = set.intensityMode || (isPercent ? 'solo_rm' : 'rpe');
    const isAmrap = set.isAmrap || set.isCalibrator;
    const useIntensityWeight = isPercent && (mode === 'solo_rm' || mode === 'rpe' || mode === 'rir' || mode === 'failure' || mode === 'amrap');
    const estimatedKg = useIntensityWeight ? getEstimatedWeight(set) : (mode === 'load' && set.targetPercentageRM ? getEstimatedWeight(set) : null);
    const suggestedWeight = (isPercent && reference1RM && set.targetPercentageRM) ? Math.round((reference1RM * set.targetPercentageRM / 100) * 4) / 4 : estimatedKg;
    const weightValue = set.weight != null ? String(set.weight) : '';

    const applySwipe = useCallback((x: number) => {
        swipeOffsetRef.current = x;
        setSwipeOffset(x);
    }, []);
    const handleEnd = useCallback(() => {
        if (swipeOffsetRef.current >= SWIPE_THRESHOLD) onRemoveSet(setIndex);
        setSwipeOffset(0);
        swipeOffsetRef.current = 0;
        isHorizontalSwipe.current = null;
    }, [onRemoveSet, setIndex]);

    const onPointerDown = useCallback((e: React.PointerEvent) => {
        if (activePointerId.current != null) return;
        activePointerId.current = e.pointerId;
        startX.current = e.clientX;
        startY.current = e.clientY;
        isHorizontalSwipe.current = null;
        const onMove = (ev: PointerEvent) => {
            if (ev.pointerId !== e.pointerId) return;
            const dx = ev.clientX - startX.current;
            const dy = ev.clientY - startY.current;
            if (isHorizontalSwipe.current === null) {
                if (Math.abs(dx) > SWIPE_ACTIVATE_PX || Math.abs(dy) > SWIPE_ACTIVATE_PX) {
                    isHorizontalSwipe.current = Math.abs(dx) > Math.abs(dy);
                }
            }
            if (isHorizontalSwipe.current === true && dx > 0) applySwipe(Math.min(dx, 120));
        };
        const onUp = (ev: PointerEvent) => {
            if (ev.pointerId !== e.pointerId) return;
            activePointerId.current = null;
            document.removeEventListener('pointermove', onMove);
            document.removeEventListener('pointerup', onUp);
            document.removeEventListener('pointercancel', onUp);
            handleEnd();
        };
        document.addEventListener('pointermove', onMove, { passive: true });
        document.addEventListener('pointerup', onUp);
        document.addEventListener('pointercancel', onUp);
    }, [applySwipe, handleEnd]);

    return (
        <div
            ref={rowRef}
            className="relative overflow-hidden border-b border-white/[0.03] min-h-[48px] touch-pan-y"
            onPointerDown={onPointerDown}
        >
            {swipeOffset > 10 && (
                <div
                    className="absolute inset-y-0 left-0 w-16 bg-red-500/20 flex items-center justify-center text-red-400 text-[10px] font-black uppercase z-0"
                >
                    Eliminar
                </div>
            )}
            <div
                ref={contentRef}
                className="flex items-center gap-1 px-2 py-2 hover:bg-white/[0.02] transition-colors group min-h-[48px] bg-[#FEF7FF] relative z-10"
                style={{ transform: `translate3d(${swipeOffset}px,0,0)` }}
            >
                <span className="w-8 text-center text-xs font-mono text-[#999] font-bold tabular-nums shrink-0">{setIndex + 1}</span>

                <div className="w-12 max-w-[48px] shrink-0">
                    <button type="button" onClick={() => onOpenNumpad({ setIndex, field: isTime ? 'targetDuration' : 'targetReps' })} className="w-full bg-transparent border-b border-transparent hover:border-[#00F0FF] text-sm font-mono text-white py-1 px-1 rounded transition-colors outline-none placeholder-[#A0A7B8]/80 tabular-nums text-left">
                        {isTime ? (set.targetDuration ?? '—') : (set.targetReps ?? '—')}
                    </button>
                </div>

                {showPercentColumn && mode === 'load' && (
                    <div className="w-14 shrink-0">
                        <button type="button" onClick={() => onOpenNumpad({ setIndex, field: 'targetPercentageRM' })} className="w-full bg-transparent border-b border-transparent hover:border-[#00F0FF] text-sm font-mono text-white py-1 text-right rounded transition-colors outline-none tabular-nums">
                            {set.targetPercentageRM ?? '%'}
                        </button>
                    </div>
                )}

                {showWeightColumn && (
                    <div className="w-14 flex flex-col items-end shrink-0">
                        {useIntensityWeight && estimatedKg != null ? (
                            <span className="text-[10px] font-mono text-cyber-cyan/90">{estimatedKg}kg</span>
                        ) : (
                            <button type="button" onClick={() => onOpenNumpad({ setIndex, field: 'weight' })} className="w-full bg-transparent border-b border-transparent hover:border-[#00F0FF] text-sm font-mono text-white py-1 text-right rounded transition-colors outline-none tabular-nums">
                                {weightValue || (suggestedWeight != null ? String(suggestedWeight) : 'kg')}
                            </button>
                        )}
                    </div>
                )}

                <div className="w-14 text-center shrink-0">
                    {mode === 'failure' ? (
                        <span className="text-[10px] font-bold text-red-400">FALLO</span>
                    ) : mode === 'solo_rm' ? (
                        <span className="text-[10px] font-bold text-cyber-cyan/90">RM</span>
                    ) : mode === 'load' ? (
                        <span className="text-[10px] font-bold text-[#999]">—</span>
                    ) : (
                        <button type="button" onClick={() => onOpenNumpad({ setIndex, field: mode === 'rir' ? 'targetRIR' : 'targetRPE' })} className="w-full bg-transparent border-b border-transparent hover:border-[#00F0FF] text-sm font-mono text-white py-1 text-center rounded transition-colors outline-none tabular-nums">
                            {mode === 'rir' ? (set.targetRIR ?? '—') : (set.targetRPE ?? '—')}
                        </button>
                    )}
                </div>

                <div className="w-20 flex items-center justify-center gap-0.5 shrink-0">
                    <select
                        value={mode}
                        onChange={e => onSetChange(setIndex, 'intensityMode', e.target.value)}
                        className="bg-black/50 text-[10px] font-bold text-[#999] border border-[#E6E0E9] rounded px-1 py-0.5 focus:ring-1 focus:ring-cyber-cyan/30 cursor-pointer text-center"
                    >
                        {isPercent && <option value="solo_rm" className="bg-black text-white">SOLO RM</option>}
                        <option value="rpe" className="bg-black text-white">RPE</option>
                        <option value="rir" className="bg-black text-white">RIR</option>
                        <option value="failure" className="bg-black text-white">Fallo</option>
                        <option value="load" className="bg-black text-white">Carga</option>
                    </select>
                    {isAmrap && <FlameIcon size={10} className="text-yellow-400 shrink-0" />}
                    {onAmrapToggle && !isAmrap && (
                        <button
                            onClick={() => onAmrapToggle(setIndex)}
                            className="p-1.5 rounded text-[#555] hover:text-white opacity-0 group-hover:opacity-100 transition-opacity"
                            title="AMRAP"
                        >
                            <FlameIcon size={10} />
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
};

export default InlineSetTable;
