import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
    Exercise,
    ExerciseSet,
    ExerciseMuscleInfo,
    Settings,
    UnilateralSetInputs,
    SetInputState,
    WorkoutLog,
    OngoingSetData
} from '../../../types';
import { calculateWeightFrom1RM, roundWeight } from '../../../utils/calculations';
import { calculateSpinalScore } from '../../../services/auge';
import { hapticImpact, hapticNotification, ImpactStyle, NotificationType } from '../../../services/hapticsService';
import Button from '../../ui/Button';
import {
    FlameIcon,
    AlertTriangleIcon,
    TrophyIcon,
    ClockIcon,
    CheckCircleIcon,
    MinusIcon,
    PlusIcon,
    BodyIcon
} from '../../icons';
import { SetTimerButton } from '../SetTimerButton';
import TacticalModal from '../../ui/TacticalModal';
import { GhostSetInfo } from './GhostSetInfo';

interface SetDetailsProps {
    exercise: Exercise;
    exerciseInfo?: ExerciseMuscleInfo;
    set: ExerciseSet;
    setIndex: number;
    settings: Settings;
    inputs: UnilateralSetInputs | SetInputState;
    onInputChange: (field: keyof SetInputState, value: any, side?: 'left' | 'right') => void;
    onLogSet: (isCalibrator?: boolean) => void;
    isLogged: boolean;
    history: WorkoutLog[];
    currentSession1RM?: number;
    base1RM?: number;
    isCalibrated?: boolean;
    cardAnimation?: string | null;
    addToast: (message: string, type?: any, title?: string, duration?: number) => void;
    suggestedWeight?: number;
    selectedTag?: string;
    tableRowMode?: boolean;
    setId: string;
}

export const SetDetails: React.FC<SetDetailsProps> = React.memo(({
    exercise,
    exerciseInfo,
    set,
    setIndex,
    settings,
    inputs,
    onInputChange,
    onLogSet,
    isLogged,
    history,
    currentSession1RM,
    cardAnimation,
    addToast,
    suggestedWeight,
    tableRowMode
}) => {
    const cardRef = useRef<HTMLDivElement>(null);
    useEffect(() => {
        const el = cardRef.current;
        if (!el) return;
        const handler = () => {
            requestAnimationFrame(() => {
                el.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
            });
        };
        el.addEventListener('focusin', handler);
        return () => el.removeEventListener('focusin', handler);
    }, []);

    const isUnilateral = exercise.isUnilateral || false;
    const isTimeMode = exercise.trainingMode === 'time';
    const [activeSide, setActiveSide] = useState<'left' | 'right'>('left');
    const [repInputMode, setRepInputMode] = useState<'standard' | 'partial'>('standard');
    const [isBodyweight, setIsBodyweight] = useState(false);
    const [showFailureWarning, setShowFailureWarning] = useState(false);
    const [showFailedModal, setShowFailedModal] = useState(false);
    const [isStagnant, setIsStagnant] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const currentInputs: SetInputState = isUnilateral ? (inputs as UnilateralSetInputs)[activeSide] : (inputs as SetInputState);
    const safeInputs: SetInputState = currentInputs || {
        reps: '', weight: '', rpe: '', rir: '', isFailure: false,
        isIneffective: false, isPartial: false, duration: '', notes: '',
        advancedTechnique: '', dropSets: [], restPauses: [], performanceMode: 'target',
        partialReps: '', technicalQuality: '8', discomfortLevel: '0',
        discomfortNotes: '', tempo: ''
    };

    const targetReps = set.targetReps || 0;
    const completedReps = parseInt(safeInputs.reps, 10) || 0;
    const loggedDuration = parseInt(safeInputs.duration || '0', 10);
    const targetDuration = set.targetDuration || 0;

    const debt = isTimeMode ? (loggedDuration - targetDuration) : (completedReps - targetReps);

    const targetRPE = set.targetRPE || 8;
    const targetRIR = set.targetRIR;
    const currentRPE = safeInputs.rpe ? parseFloat(safeInputs.rpe) : (safeInputs.rir ? 10 - parseFloat(safeInputs.rir) : 0);
    const intensityDiff = currentRPE > 0 ? (currentRPE - targetRPE) : 0;

    const perfColorClass = debt > 0
        ? 'text-emerald-700 border-emerald-300/80 bg-emerald-100/70'
        : debt === 0
            ? 'text-[var(--md-sys-color-primary)] border-[var(--md-sys-color-primary)]/25 bg-[var(--md-sys-color-primary-container)]/55'
            : 'text-[var(--md-sys-color-error)] border-[var(--md-sys-color-error)]/25 bg-[var(--md-sys-color-error-container)]/70';

    const inputActiveColor = (repInputMode === 'standard' || isTimeMode)
        ? perfColorClass
        : 'text-[var(--md-sys-color-on-surface)] border-[var(--md-sys-color-outline-variant)] bg-white/60';

    let intensityContainerClass = 'bg-white/70 border-[var(--md-sys-color-outline-variant)]';
    if (safeInputs.performanceMode === 'target' && currentRPE > 0) {
        if (intensityDiff > 0.5) intensityContainerClass = 'bg-[var(--md-sys-color-error-container)] border-[var(--md-sys-color-error)]/40 shadow-[0_0_10px_rgba(186,26,26,0.08)]';
        else if (intensityDiff < -0.5) intensityContainerClass = 'bg-emerald-100/80 border-emerald-300/80 shadow-[0_0_10px_rgba(16,185,129,0.08)]';
    }

    const suggestedLoad = useMemo(() => {
        if (suggestedWeight != null && suggestedWeight > 0) return suggestedWeight;
        const oneRM = currentSession1RM || exerciseInfo?.calculated1RM || exercise.reference1RM || 0;
        if (oneRM <= 0 || (targetReps <= 0 && targetDuration <= 0)) return null;

        const effectiveReps = isTimeMode ? Math.max(1, Math.round(targetDuration / 3)) : targetReps;
        const weight = calculateWeightFrom1RM(oneRM, effectiveReps + (targetRIR !== undefined ? targetRIR : (10 - targetRPE)));
        return roundWeight(weight, settings.weightUnit);
    }, [suggestedWeight, exercise, exerciseInfo, set, currentSession1RM, settings.weightUnit, targetReps, targetDuration, isTimeMode]);

    const inputWeight = parseFloat(safeInputs.weight);
    const isWeightWarning = useMemo(() => {
        if (!suggestedLoad || isNaN(inputWeight)) return false;
        return inputWeight > (suggestedLoad * 1.05);
    }, [inputWeight, suggestedLoad]);

    useEffect(() => {
        if (!history || history.length < 3) return;
        const exerciseHistory = history.filter(h => h.completedExercises.some(e => e.exerciseId === exercise.id || e.exerciseDbId === exercise.exerciseDbId)).slice(-3);
        if (exerciseHistory.length === 3) {
            const last3Sets = exerciseHistory.map(h => {
                const ex = h.completedExercises.find(e => e.exerciseId === exercise.id || e.exerciseDbId === exercise.exerciseDbId);
                return ex?.sets[setIndex];
            });
            if (last3Sets.every(s => s && s.weight === parseFloat(safeInputs.weight) && s.completedReps === completedReps)) {
                setIsStagnant(true);
            } else {
                setIsStagnant(false);
            }
        }
    }, [history, exercise.id, exercise.exerciseDbId, setIndex, safeInputs.weight, completedReps]);

    const handleAdjust = useCallback((field: 'reps' | 'weight' | 'duration', amount: number) => {
        let targetField: keyof SetInputState = field;
        if (field === 'reps' && repInputMode === 'partial') { targetField = 'partialReps'; }

        const val = parseFloat(String(safeInputs[targetField]) || '0') || 0;
        let newValue: number;
        if (field === 'weight') {
            const step = settings.weightUnit === 'kg' ? (val < 20 ? 1.25 : 2.5) : 2.5;
            newValue = val + (amount * step);
        } else {
            newValue = Math.max(0, val + amount);
        }
        onInputChange(targetField, newValue.toString(), isUnilateral ? activeSide : undefined);
        if (targetField === 'partialReps' && newValue > 0) { onInputChange('isPartial', true, isUnilateral ? activeSide : undefined); }
    }, [safeInputs, onInputChange, settings.weightUnit, isUnilateral, activeSide, repInputMode]);

    const handleLogAttempt = () => {
        if (isSubmitting) return;
        setIsSubmitting(true);

        if (set.isAmrap) {
            const reps = parseInt(safeInputs.reps, 10) || 0;
            const target = set.targetReps || 0;
            if (reps < target) {
                setShowFailureWarning(true);
                setIsSubmitting(false);
                return;
            }
        }
        setShowFailureWarning(false);
        onLogSet(!!set.isCalibrator);
        setTimeout(() => setIsSubmitting(false), 500);
    }

    const handleSetDurationSave = (tut: number) => {
        onInputChange('duration', tut.toString(), isUnilateral ? activeSide : undefined);
    };

    const handleFailedSet = (reason: string) => {
        hapticNotification(NotificationType.Error);
        onInputChange('isIneffective', true, isUnilateral ? activeSide : undefined);
        onInputChange('performanceMode', 'failed', isUnilateral ? activeSide : undefined);
        onInputChange('discomfortNotes', `🚨 FALLO CRÍTICO: ${reason}`, isUnilateral ? activeSide : undefined);
        addToast(`Serie anulada por: ${reason}`, "danger");
        onLogSet();
        setShowFailedModal(false);
    };

    const handlePerformanceModeChange = (mode: 'target' | 'failure' | 'failed') => {
        hapticImpact(ImpactStyle.Light);
        onInputChange('performanceMode', mode, isUnilateral ? activeSide : undefined);
        if (mode === 'failure') {
            onInputChange('isFailure', true, isUnilateral ? activeSide : undefined);
        } else if (mode === 'target') {
            onInputChange('isFailure', false, isUnilateral ? activeSide : undefined);
        } else if (mode === 'failed') {
            setShowFailedModal(true);
        }
    }

    const containerClass = "set-card-content flex flex-col bg-white/50 backdrop-blur-xl p-3 transition-all duration-500 rounded-[24px] border border-white/20 shadow-xl shadow-black/5";

    if (tableRowMode) {
        return (
            <div ref={cardRef} className="workout-inline-panel space-y-3 px-3 py-3 animate-fade-in bg-white/40 backdrop-blur-lg rounded-[28px] border border-white/30">
                {isUnilateral && (
                    <div className="workout-segmented flex relative z-10 shrink-0 bg-black/5 p-1 rounded-full">
                        <button
                            data-active={activeSide === 'left'}
                            onClick={() => setActiveSide('left')}
                            className={`flex-1 py-1.5 text-[10px] font-black uppercase tracking-widest rounded-full transition-all min-h-[36px] ${activeSide === 'left' ? 'bg-white shadow-sm text-primary' : 'text-black/40'}`}
                        >
                            IZQUIERDA
                        </button>
                        <button
                            data-active={activeSide === 'right'}
                            onClick={() => setActiveSide('right')}
                            className={`flex-1 py-1.5 text-[10px] font-black uppercase tracking-widest rounded-full transition-all min-h-[36px] ${activeSide === 'right' ? 'bg-white shadow-sm text-primary' : 'text-black/40'}`}
                        >
                            DERECHA
                        </button>
                    </div>
                )}
                {set.isAmrap ? (
                    <div className={`flex justify-center items-center py-2 rounded-[20px] border w-full ${set.isCalibrator ? 'bg-primary/10 border-primary/25 text-primary' : 'bg-white/65 border-white/40 text-primary shadow-sm'}`}>
                        <span className="text-[10px] font-black uppercase tracking-widest flex items-center gap-1">
                            <FlameIcon size={12} />
                            {set.isCalibrator ? 'AMRAP Calibrador' : 'AMRAP'}
                        </span>
                    </div>
                ) : (
                    <div className="workout-segmented flex justify-center items-center gap-1.5 bg-black/5 p-1 rounded-full">
                        <button
                            data-active={safeInputs.performanceMode === 'target'}
                            onClick={() => handlePerformanceModeChange('target')}
                            className={`flex-1 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest transition-all min-h-[36px] ${safeInputs.performanceMode === 'target' ? 'bg-white shadow-sm text-primary' : 'text-black/40'}`}
                        >
                            {(set.intensityMode === 'rir' || settings.intensityMetric === 'rir') ? 'RIR' : 'RPE'}
                        </button>
                        <button
                            data-active={safeInputs.performanceMode === 'failure'}
                            onClick={() => handlePerformanceModeChange('failure')}
                            className={`flex-1 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest transition-all flex items-center justify-center gap-1 min-h-[36px] ${safeInputs.performanceMode === 'failure' ? 'bg-[#FF5252] text-white shadow-lg shadow-red-500/20' : 'text-black/40'}`}
                        >
                            <FlameIcon size={10} /> FALLO
                        </button>
                        <button
                            data-active={safeInputs.performanceMode === 'failed'}
                            onClick={() => handlePerformanceModeChange('failed')}
                            className={`flex-1 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest transition-all flex items-center justify-center gap-1 min-h-[36px] ${safeInputs.performanceMode === 'failed' ? 'bg-black text-white shadow-lg' : 'text-black/40'}`}
                        >
                            <AlertTriangleIcon size={10} /> FALLIDO
                        </button>
                    </div>
                )}
                <div className="grid grid-cols-2 gap-3 mb-2">
                    {!isBodyweight ? (
                        <div className="flex flex-col gap-1.5">
                            <label className="text-[9px] font-black uppercase tracking-[0.2em] text-black/30 px-2">Carga ({settings.weightUnit})</label>
                            <div className="flex items-center rounded-[20px] bg-white/70 border border-white/50 px-3 py-2 min-h-[56px] shadow-sm">
                                <input
                                    type="text"
                                    inputMode="decimal"
                                    value={safeInputs.weight || ''}
                                    onChange={e => onInputChange('weight', e.target.value.replace(',', '.').replace(/[^0-9.]/g, ''), isUnilateral ? activeSide : undefined)}
                                    className="w-full bg-transparent border-none text-center font-black text-2xl text-black focus:ring-0 p-0 tabular-nums"
                                    placeholder="0"
                                />
                            </div>
                        </div>
                    ) : (
                        <div className="flex flex-col gap-1.5">
                            <label className="text-[9px] font-black uppercase tracking-[0.2em] text-black/30 px-2">Carga</label>
                            <div className="flex items-center justify-center rounded-[20px] bg-black/5 border border-black/5 px-3 py-2 min-h-[56px] text-[10px] font-black uppercase text-black/20">AUTOCARGA</div>
                        </div>
                    )}
                    <div className="flex flex-col gap-1.5">
                        <label className="text-[9px] font-black uppercase tracking-[0.2em] text-black/30 px-2">{isTimeMode ? 'Segundos' : 'Repes'}</label>
                        <div className="flex items-center rounded-[20px] bg-white/70 border border-white/50 px-3 py-2 min-h-[56px] shadow-sm">
                            <input
                                type="text"
                                inputMode="numeric"
                                value={isTimeMode ? safeInputs.duration || '' : (repInputMode === 'standard' ? safeInputs.reps : safeInputs.partialReps) || ''}
                                onChange={e => onInputChange(isTimeMode ? 'duration' : (repInputMode === 'standard' ? 'reps' : 'partialReps'), e.target.value.replace(/[^0-9]/g, ''), isUnilateral ? activeSide : undefined)}
                                className="w-full bg-transparent border-none text-center font-black text-2xl text-black focus:ring-0 p-0 tabular-nums"
                                placeholder="0"
                            />
                        </div>
                    </div>
                </div>

                {safeInputs.performanceMode === 'target' && (
                    <div className="flex justify-center">
                        {(set.intensityMode === 'rir' || settings.intensityMetric === 'rir') ? (
                            <div className={`flex items-center rounded-[16px] p-2 border w-32 justify-between transition-all duration-300 shadow-sm ${intensityContainerClass}`}>
                                <span className="text-black/30 font-black text-[10px] uppercase px-2 tracking-widest">RIR</span>
                                <input
                                    type="text"
                                    inputMode="numeric"
                                    value={safeInputs.rir || ''}
                                    onChange={e => onInputChange('rir', e.target.value.replace(/[^0-9]/g, ''), isUnilateral ? activeSide : undefined)}
                                    className="w-12 bg-transparent border-none text-center font-black text-black focus:ring-0 p-0 text-lg tabular-nums"
                                    placeholder="-"
                                />
                            </div>
                        ) : (
                            <div className={`flex items-center rounded-[16px] p-2 border w-32 justify-between transition-all duration-300 shadow-sm ${intensityContainerClass}`}>
                                <span className="text-black/30 font-black text-[10px] uppercase px-2 tracking-widest">RPE</span>
                                <input
                                    type="text"
                                    inputMode="decimal"
                                    value={safeInputs.rpe || ''}
                                    onChange={e => onInputChange('rpe', e.target.value.replace(',', '.').replace(/[^0-9.]/g, ''), isUnilateral ? activeSide : undefined)}
                                    className="w-12 bg-transparent border-none text-center font-black text-black focus:ring-0 p-0 text-lg tabular-nums"
                                    placeholder="-"
                                />
                            </div>
                        )}
                    </div>
                )}
                <div className="flex gap-4 justify-center text-[9px] font-black uppercase tracking-[0.14em]">
                    <button type="button" onClick={() => onInputChange('dropSets', [...(safeInputs.dropSets || []), { weight: 0, reps: 0 }], isUnilateral ? activeSide : undefined)} className="text-primary hover:opacity-70 transition-opacity">+ Dropset</button>
                    <button type="button" onClick={() => onInputChange('restPauses', [...(safeInputs.restPauses || []), { restTime: 15, reps: 0 }], isUnilateral ? activeSide : undefined)} className="text-primary hover:opacity-70 transition-opacity">+ Rest-Pause</button>
                </div>

                {/* Advanced techniques UI */}
                {(safeInputs.dropSets?.length || 0) > 0 || (safeInputs.restPauses?.length || 0) > 0 ? (
                    <div className="p-4 bg-white/40 border border-white/50 rounded-[24px] space-y-3 shadow-inner">
                        {(safeInputs.dropSets || []).map((ds, i) => (
                            <div key={`ds-${i}`} className="flex gap-2 items-center">
                                <span className="text-[8px] font-black uppercase tracking-widest text-black/30 w-12">DROIPSET</span>
                                <input
                                    type="number"
                                    inputMode="decimal"
                                    value={ds.weight === 0 ? '' : ds.weight}
                                    onChange={(e) => {
                                        const arr = [...(safeInputs.dropSets || [])];
                                        if (!arr[i]) arr[i] = { weight: 0, reps: 0 };
                                        arr[i] = { ...arr[i], weight: parseFloat(e.target.value) || 0 };
                                        onInputChange('dropSets', arr, isUnilateral ? activeSide : undefined);
                                    }}
                                    placeholder="KG"
                                    className="w-20 bg-white/80 border border-white/50 rounded-lg px-2 py-1.5 text-xs text-black font-black text-center shadow-sm"
                                />
                                <input
                                    type="number"
                                    inputMode="numeric"
                                    value={ds.reps === 0 ? '' : ds.reps}
                                    onChange={(e) => {
                                        const arr = [...(safeInputs.dropSets || [])];
                                        if (!arr[i]) arr[i] = { weight: 0, reps: 0 };
                                        arr[i] = { ...arr[i], reps: parseInt(e.target.value) || 0 };
                                        onInputChange('dropSets', arr, isUnilateral ? activeSide : undefined);
                                    }}
                                    placeholder="REPS"
                                    className="w-16 bg-white/80 border border-white/50 rounded-lg px-2 py-1.5 text-xs text-black font-black text-center shadow-sm"
                                />
                                <button onClick={() => onInputChange('dropSets', (safeInputs.dropSets || []).filter((_, j) => j !== i), isUnilateral ? activeSide : undefined)} className="w-8 h-8 rounded-full bg-red-50 text-red-500 flex items-center justify-center border border-red-100"><MinusIcon size={12} /></button>
                            </div>
                        ))}
                    </div>
                ) : null}

                <Button onClick={() => handleLogAttempt()} className="w-full !py-4 !text-[10px] !font-black uppercase tracking-[0.2em] !rounded-[24px] !bg-primary !text-white !border-none !shadow-xl shadow-primary/20 active:scale-[0.96] transition-all">
                    {isLogged ? 'ACTUALIZAR SERIE' : 'GUARDAR SERIE'}
                </Button>
            </div>
        );
    }

    return (
        <div ref={cardRef} className={`${containerClass} scroll-mb-48 relative overflow-hidden`}>
            {isUnilateral && (
                <div className="flex bg-black/5 p-1 mb-4 rounded-full relative z-10 shrink-0">
                    <button onClick={() => setActiveSide('left')} className={`flex-1 py-1.5 text-[10px] font-black uppercase tracking-widest transition-all min-h-[40px] rounded-full ${activeSide === 'left' ? 'bg-white shadow-sm text-primary' : 'text-black/30'}`}>Izquierda</button>
                    <button onClick={() => setActiveSide('right')} className={`flex-1 py-1.5 text-[10px] font-black uppercase tracking-widest transition-all min-h-[40px] rounded-full ${activeSide === 'right' ? 'bg-white shadow-sm text-primary' : 'text-black/30'}`}>Derecha</button>
                </div>
            )}

            <GhostSetInfo exerciseId={(exercise.exerciseDbId || exercise.id) as string} setIndex={setIndex} history={history} settings={settings} />

            <div className="space-y-6 px-2">
                <div className="flex justify-between items-center px-1">
                    <div className="text-[10px] font-black uppercase tracking-[0.18em] text-black/20">
                        META: <span className="text-black/60">{isTimeMode ? `${targetDuration}s` : `${targetReps} REPS`}</span> <span className="mx-1">/</span> <span className="text-black/60">{set.intensityMode === 'rir' ? `RIR ${targetRIR}` : `RPE ${targetRPE}`}</span>
                    </div>

                    <div className="flex gap-2 items-center">
                        {suggestedLoad && (
                            <button onClick={() => { hapticImpact(ImpactStyle.Light); onInputChange('weight', suggestedLoad.toFixed(1), isUnilateral ? activeSide : undefined); }} className="text-[9px] font-black uppercase tracking-widest bg-primary/5 text-primary px-3 py-1.5 rounded-full border border-primary/10 hover:bg-primary/10 transition-colors">
                                SUGERIDO: {suggestedLoad.toFixed(1)}{settings.weightUnit}
                            </button>
                        )}
                    </div>
                </div>

                <div className="grid gap-4 items-center" style={{ gridTemplateColumns: '1fr auto 1fr' }}>
                    {/* Reps/Duration Input */}
                    <div className={`flex flex-col rounded-[24px] overflow-hidden border transition-all duration-300 shadow-sm ${inputActiveColor}`}>
                        <button
                            onClick={() => !isTimeMode && setRepInputMode(prev => prev === 'standard' ? 'partial' : 'standard')}
                            className="text-[9px] uppercase font-black tracking-[0.2em] py-2 text-center bg-black/5 text-black/40"
                        >
                            {isTimeMode ? 'SEGUNDOS' : (repInputMode === 'standard' ? 'REPETICIONES' : 'PARCIALES')}
                        </button>
                        <div className="relative flex-1 py-2 flex items-center justify-center">
                            <input
                                type="text"
                                inputMode="numeric"
                                value={isTimeMode ? safeInputs.duration || '' : (repInputMode === 'standard' ? safeInputs.reps : safeInputs.partialReps) || ''}
                                onChange={(e) => {
                                    const val = e.target.value.replace(/[^0-9]/g, '');
                                    onInputChange(isTimeMode ? 'duration' : (repInputMode === 'standard' ? 'reps' : 'partialReps'), val, isUnilateral ? activeSide : undefined);
                                }}
                                className="w-full text-center bg-transparent border-none text-3xl font-black focus:ring-0 p-0 text-inherit tabular-nums"
                                placeholder="0"
                            />
                        </div>
                        <div className="grid grid-cols-2 border-t border-black/5 divide-x divide-black/5 bg-black/[0.02]">
                            <button onClick={() => handleAdjust(isTimeMode ? 'duration' : 'reps', isTimeMode ? -5 : -1)} className="py-4 hover:bg-black/5 active:bg-black/10 flex justify-center text-black/30"><MinusIcon size={16} /></button>
                            <button onClick={() => handleAdjust(isTimeMode ? 'duration' : 'reps', isTimeMode ? 5 : 1)} className="py-4 hover:bg-black/5 active:bg-black/10 flex justify-center text-black/30"><PlusIcon size={16} /></button>
                        </div>
                    </div>

                    <div className="flex flex-col justify-center items-center gap-1.5">
                        <SetTimerButton onSave={handleSetDurationSave} initialDuration={loggedDuration} />
                        <span className="text-[9px] font-black text-black/20 tracking-widest">TUT</span>
                    </div>

                    {/* Weight Input */}
                    <div className={`flex flex-col rounded-[24px] overflow-hidden border transition-all duration-300 shadow-sm ${isWeightWarning ? 'border-red-500 bg-red-50' : 'bg-white border-white/50'}`}>
                        <button onClick={() => setIsBodyweight(!isBodyweight)} className={`text-[9px] uppercase font-black tracking-[0.2em] py-2 text-center transition-colors ${isBodyweight ? 'bg-primary/10 text-primary' : 'bg-black/5 text-black/40'}`}>
                            {isBodyweight ? 'AUTOCARGA' : `CARGA (${settings.weightUnit})`}
                        </button>
                        <div className="relative flex-1 py-2 flex items-center justify-center">
                            {isBodyweight ? (
                                <div className="flex items-center justify-center gap-1.5 text-primary opacity-40"><BodyIcon size={24} /><span className="text-xl font-black">BW</span></div>
                            ) : (
                                <div className="relative w-full">
                                    <input
                                        type="text"
                                        inputMode="decimal"
                                        value={safeInputs.weight || ''}
                                        onChange={(e) => {
                                            const val = e.target.value.replace(',', '.').replace(/[^0-9.]/g, '');
                                            onInputChange('weight', val, isUnilateral ? activeSide : undefined);
                                        }}
                                        className={`w-full text-center bg-transparent border-none text-3xl font-black focus:ring-0 p-0 tabular-nums ${isWeightWarning ? 'text-red-500' : 'text-black'}`}
                                        placeholder="0"
                                    />
                                    {isWeightWarning && <AlertTriangleIcon size={14} className="absolute top-1 right-2 text-red-500 animate-pulse" />}
                                </div>
                            )}
                        </div>
                        <div className="grid grid-cols-2 border-t border-black/5 divide-x divide-black/5 bg-black/[0.02]">
                            <button onClick={() => handleAdjust('weight', -1)} disabled={isBodyweight} className="py-4 hover:bg-black/5 active:bg-black/10 flex justify-center text-black/30 disabled:opacity-30"><MinusIcon size={16} /></button>
                            <button onClick={() => handleAdjust('weight', 1)} disabled={isBodyweight} className="py-4 hover:bg-black/5 active:bg-black/10 flex justify-center text-black/30 disabled:opacity-30"><PlusIcon size={16} /></button>
                        </div>
                    </div>
                </div>

                {/* Intensity & Technical Mode Selectors */}
                <div className="flex justify-between items-center gap-3">
                    <div className="flex-1 flex bg-black/5 p-1 rounded-full">
                        <button onClick={() => handlePerformanceModeChange('target')} className={`flex-1 py-2 text-[9px] font-black uppercase tracking-widest min-h-[36px] rounded-full transition-all ${safeInputs.performanceMode === 'target' ? 'bg-white shadow-sm text-primary' : 'text-black/30'}`}>
                            METRIC
                        </button>
                        <button onClick={() => handlePerformanceModeChange('failure')} className={`flex-1 py-2 text-[9px] font-black uppercase tracking-widest min-h-[36px] rounded-full transition-all flex items-center justify-center gap-1.5 ${safeInputs.performanceMode === 'failure' ? 'bg-[#FF5252] text-white shadow-lg' : 'text-black/30'}`}>
                            <FlameIcon size={11} /> FALLO
                        </button>
                    </div>

                    {safeInputs.performanceMode === 'target' && (
                        <div className="animate-fade-in">
                            {(set.intensityMode === 'rir' || settings.intensityMetric === 'rir') ? (
                                <div className={`flex items-center rounded-2xl p-2 border w-24 justify-between shadow-sm ${intensityContainerClass}`}>
                                    <span className="text-black/20 font-black text-[9px] uppercase px-1 tracking-widest">RIR</span>
                                    <input
                                        type="number"
                                        inputMode="numeric"
                                        value={safeInputs.rir || ''}
                                        onChange={(e) => onInputChange('rir', e.target.value, isUnilateral ? activeSide : undefined)}
                                        className="w-10 bg-transparent border-none text-center font-black text-black focus:ring-0 p-0 text-base"
                                        placeholder="-"
                                    />
                                </div>
                            ) : (
                                <div className={`flex items-center rounded-2xl p-2 border w-24 justify-between shadow-sm ${intensityContainerClass}`}>
                                    <span className="text-black/20 font-black text-[9px] uppercase px-1 tracking-widest">RPE</span>
                                    <input
                                        type="number"
                                        inputMode="decimal"
                                        step="0.5"
                                        value={safeInputs.rpe || ''}
                                        onChange={(e) => onInputChange('rpe', e.target.value, isUnilateral ? activeSide : undefined)}
                                        className="w-10 bg-transparent border-none text-center font-black text-black focus:ring-0 p-0 text-base"
                                        placeholder="-"
                                    />
                                </div>
                            )}
                        </div>
                    )}
                </div>

                <div className="mt-4">
                    <button onClick={handleLogAttempt} className="w-full py-5 rounded-[32px] bg-primary text-white text-[11px] font-black uppercase tracking-[0.25em] shadow-2xl shadow-primary/20 hover:scale-[1.02] active:scale-[0.98] transition-all">
                        {isLogged ? 'ACTUALIZAR SERIE' : 'GUARDAR RESULTADO'}
                    </button>
                </div>
            </div>

            {showFailedModal && (
                <TacticalModal isOpen={showFailedModal} onClose={() => setShowFailedModal(false)} title="SERIE FALLIDA">
                    <div className="space-y-4 p-4 text-center">
                        <p className="text-xs font-medium text-black/50 leading-relaxed uppercase tracking-wider">Indica la causa del fallo crítico para ajustar tu AUGE.</p>
                        <div className="grid gap-3 mt-6">
                            <button onClick={() => handleFailedSet('Dolor / Lesión')} className="w-full py-4 rounded-3xl bg-red-50 text-red-500 text-[10px] font-black uppercase tracking-widest border border-red-100">DOLOR / MOLESTIA</button>
                            <button onClick={() => handleFailedSet('Peso Excesivo')} className="w-full py-4 rounded-3xl bg-black text-white text-[10px] font-black uppercase tracking-widest">CARGA EXCESIVA</button>
                            <button onClick={() => handleFailedSet('Fallo Técnico')} className="w-full py-4 rounded-3xl bg-black/5 text-black/40 text-[10px] font-black uppercase tracking-widest">FALLO TÉCNICO</button>
                        </div>
                    </div>
                </TacticalModal>
            )}
        </div>
    );
});
