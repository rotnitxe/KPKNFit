// components/LogWorkoutView.tsx
import React, { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { Session, Program, Settings, WorkoutLog, CompletedExercise, CompletedSet, ExerciseSet, Exercise, OngoingWorkoutState, ExerciseMuscleInfo, BrandEquivalency, OngoingSetData } from '../types';
import Button from './ui/Button';
import { CheckCircleIcon, TrophyIcon, MinusIcon, PlusIcon, ChevronRightIcon, ClockIcon, XCircleIcon, StarIcon, SwapIcon, BrainIcon, FlameIcon, AlertTriangleIcon, RefreshCwIcon } from './icons';
import FinishWorkoutModal from './FinishWorkoutModal';
import ExerciseHistoryModal from './ExerciseHistoryModal';
import { calculatePlates } from '../utils/plateCalculator';
import { roundWeight, calculateBrzycki1RM, getWeightSuggestionForSet, isMachineOrCableExercise } from '../utils/calculations';
import { useAppDispatch, useAppState } from '../contexts/AppContext';
import SubstituteExerciseSheet from './SubstituteExerciseSheet';
import { hapticImpact as _hapticImpact, ImpactStyle } from '../services/hapticsService';
import { dateStringToISOString } from '../utils/dateUtils';

// Bypass de TypeScript: Adaptador para que los strings literales sean aceptados como Enums
const hapticImpact = (style?: any) => _hapticImpact(style);

const safeCreateISOStringFromDateInput = (dateString?: string): string => {
    if (dateString && /^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
        return dateStringToISOString(dateString);
    }
    return new Date().toISOString();
};


// Helper function from WorkoutSession
const findPrForExercise = (exercise: Exercise, history: WorkoutLog[], settings: Settings, currentTag?: string, brandEquivalencies?: BrandEquivalency[]): { prString: string, e1rm: number, reps: number } | null => {
    let best1RM = 0;
    let prString = '';
    let bestReps = Infinity;
    const exerciseDbId = exercise.exerciseDbId;
    const exerciseNameLower = exercise.name.toLowerCase();

    history.forEach(log => {
        const completedEx = log.completedExercises.find(ce => {
            if (exerciseDbId && ce.exerciseDbId) {
                return ce.exerciseDbId === exerciseDbId;
            }
            return ce.exerciseName.toLowerCase() === exerciseNameLower;
        });

        if (completedEx) {
            // Filter by Tag if specified
            const logTag = completedEx.machineBrand || 'Base';
            if (currentTag && logTag !== currentTag && logTag !== 'Base') return;

            completedEx.sets.forEach(set => {
                if (typeof set.weight === 'number' && typeof set.completedReps === 'number' && set.completedReps > 0) {
                    const e1RM = calculateBrzycki1RM(set.weight, set.completedReps);
                    const roundedE1RM = Math.round(e1RM * 10) / 10;
                    const roundedBest1RM = Math.round(best1RM * 10) / 10;

                    if (roundedE1RM > roundedBest1RM) {
                        best1RM = e1RM;
                        prString = `${set.weight}${settings.weightUnit} x ${set.completedReps} reps${set.machineBrand ? ` (${set.machineBrand})` : ''}`;
                        bestReps = set.completedReps;
                    } else if (roundedE1RM === roundedBest1RM && set.completedReps < bestReps) {
                        prString = `${set.weight}${settings.weightUnit} x ${set.completedReps} reps${set.machineBrand ? ` (${set.machineBrand})` : ''}`;
                        bestReps = set.completedReps;
                    }
                }
            });
        }
    });

    if (best1RM > 0) {
        return { prString, e1rm: Math.round(best1RM * 10) / 10, reps: bestReps };
    }
    return null;
};

const formatSetTarget = (set: ExerciseSet) => {
    const parts: string[] = [];
    if (set.targetReps) parts.push(`${set.targetReps} reps`);
    else if (set.targetDuration) parts.push(`${set.targetDuration}s`);
    
    if (set.intensityMode === 'failure') {
        parts.push('Al Fallo');
    } else if (set.targetRPE) {
        parts.push(`@ RPE ${set.targetRPE}`);
    } else if (set.targetRIR) {
        parts.push(`@ RIR ${set.targetRIR}`);
    }
    
    return parts.join(' ');
};

const getTargetIntensityDisplay = (set: ExerciseSet) => {
    switch (set.intensityMode) {
        case 'rpe':
            return { value: set.targetRPE || '-', label: 'RPE Objetivo' };
        case 'rir':
            return { value: set.targetRIR || '-', label: 'RIR Objetivo' };
        case 'failure':
            return { value: <FlameIcon className="text-danger-color"/>, label: 'Al Fallo' };
        default: // approx or undefined
             if (set.targetRPE) {
                return { value: set.targetRPE, label: 'RPE Aprox.' };
            }
            if (set.targetRIR) {
                return { value: set.targetRIR, label: 'RIR Aprox.' };
            }
            return { value: 'APROX', label: 'APROX' };
    }
};

const WorkoutHeader: React.FC<{
    sessionName: string;
    totalSets: number;
    completedSetsCount: number;
    sessionVariant?: 'A' | 'B' | 'C' | 'D';
}> = React.memo(({ sessionName, totalSets, completedSetsCount, sessionVariant }) => (
    <div className="sticky top-0 z-30 bg-[var(--bg-color)] pb-3 animate-fade-in-down">
        <div className="workout-header-card !p-0 relative overflow-hidden">
            <div className="relative z-10 p-4 space-y-4">
                <div className="flex justify-between items-center">
                    <div className="flex items-center gap-2">
                        {sessionVariant && <div className="flex-shrink-0 w-10 h-10 rounded-full bg-slate-800/80 border border-slate-700 flex items-center justify-center font-bold text-xl text-white">{sessionVariant}</div>}
                        <div>
                           <h2 className="text-2xl font-bold text-white truncate pr-2">{sessionName}</h2>
                           <p className="text-sm text-slate-400 mt-1">Registrando Manualmente</p>
                        </div>
                    </div>
                </div>
                <div className="grid grid-cols-3 gap-3 text-center">
                    <div className="bg-slate-900/50 rounded-xl p-2">
                        <p className="text-2xl font-bold font-mono text-white">--:--</p>
                        <p className="text-[10px] uppercase text-slate-400 font-semibold tracking-wider">Tiempo</p>
                    </div>
                    <div className="bg-slate-900/50 rounded-xl p-2">
                        <p className="text-2xl font-bold font-mono text-white">{completedSetsCount}/{totalSets}</p>
                        <p className="text-[10px] uppercase text-slate-400 font-semibold tracking-wider">Series</p>
                    </div>
                    <div className="bg-slate-900/50 rounded-xl p-2">
                        <p className="text-2xl font-bold font-mono text-white">--</p>
                        <p className="text-[10px] uppercase text-slate-400 font-semibold tracking-wider">Volumen</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
));

const SetDetails: React.FC<{
    exercise: Exercise;
    set: ExerciseSet;
    settings: Settings;
    inputs: { reps: string; weight: string; rpe: string; rir: string; isFailure?: boolean, isIneffective?: boolean, duration?: string, advancedTechnique?: string, isPartial?: boolean, partialReps?: string };
    onInputChange: (field: 'reps' | 'weight' | 'rpe' | 'rir' | 'isFailure' | 'isIneffective' | 'duration' | 'advancedTechnique' | 'isPartial' | 'partialReps', value: string | boolean) => void;
    dynamicWeights: { consolidated?: number, technical?: number };
    onToggleChangeOfPlans: () => void;
    isChangeOfPlans: boolean;
    onLogSet: () => void;
    isLogged: boolean;
}> = React.memo(({ exercise, set, settings, inputs, onInputChange, dynamicWeights, onToggleChangeOfPlans, isChangeOfPlans, onLogSet, isLogged }) => {
    
    // UI state for intensity type selector
    const [intensityType, setIntensityType] = useState<'RPE' | 'RIR' | 'FALLO' | 'AMRAP'>('RPE');
    const [repInputMode, setRepInputMode] = useState<'standard' | 'partial'>('standard');
    const repInputRef = useRef<HTMLInputElement>(null);

    // FIX: Define missing derived variables for UI display
    const targetReps = set.targetReps || 0;
    const completedReps = parseInt(inputs.reps, 10) || 0;
    const debt = completedReps - targetReps;
    const intensityDisplay = getTargetIntensityDisplay(set);

    useEffect(() => {
        if (inputs.advancedTechnique === 'amrap') setIntensityType('AMRAP');
        else if (inputs.isFailure) setIntensityType('FALLO');
        else if (inputs.rir) setIntensityType('RIR');
        else setIntensityType('RPE');
    }, [inputs.isFailure, inputs.rir, inputs.rpe, inputs.advancedTechnique]);

    const handleIntensityTypeChange = (type: 'RPE' | 'RIR' | 'FALLO' | 'AMRAP') => {
        setIntensityType(type);
        if (type === 'FALLO') {
            onInputChange('isFailure', true);
            onInputChange('rpe', '');
            onInputChange('rir', '');
            onInputChange('advancedTechnique', '');
        } else if (type === 'AMRAP') {
            onInputChange('isFailure', true);
            onInputChange('rpe', '10');
            onInputChange('rir', '0');
            onInputChange('advancedTechnique', 'amrap');
        } else {
            onInputChange('isFailure', false);
            onInputChange('advancedTechnique', '');
            if (type === 'RPE') onInputChange('rir', '');
            if (type === 'RIR') onInputChange('rpe', '');
        }
    };
    
    const plateCombination = useMemo(() => {
        const weight = parseFloat(inputs.weight);
        if (isNaN(weight) || weight <= 0) return null;
        return calculatePlates(weight, settings);
    }, [inputs.weight, settings]);

    const handleAdjust = useCallback((field: 'reps' | 'weight' | 'duration', amount: number) => {
        let targetField: any = field;
        if (field === 'reps' && repInputMode === 'partial') {
            targetField = 'partialReps';
        }

        const currentValue = parseFloat(inputs[targetField as keyof typeof inputs] as string || '0') || 0;
        let newValue: number;
        if (field === 'weight') {
            const step = settings.weightUnit === 'kg' ? (currentValue < 20 ? 1.25 : 2.5) : 2.5;
            newValue = currentValue + (amount * step); 
        } else {
            newValue = Math.max(0, currentValue + amount);
        }
        onInputChange(targetField, newValue.toString());
        if (targetField === 'partialReps' && newValue > 0) {
             onInputChange('isPartial', true);
        }
    }, [inputs, onInputChange, settings.weightUnit, repInputMode]);

    const toggleRepMode = () => {
        setRepInputMode(prev => prev === 'standard' ? 'partial' : 'standard');
        setTimeout(() => repInputRef.current?.focus(), 10);
    };

    const handleRepChange = (val: string) => {
        const field = repInputMode === 'standard' ? 'reps' : 'partialReps';
        onInputChange(field, val);
        if (field === 'partialReps' && parseFloat(val) > 0) {
            onInputChange('isPartial', true);
        }
    };

    const renderEffortInput = () => {
        return (
             <div className="flex flex-col gap-2 items-center justify-center w-full">
                {/* Selector Toggles */}
                <div className="flex bg-slate-800 rounded-lg p-1 text-xs mb-1">
                    <button onClick={() => handleIntensityTypeChange('RPE')} className={`px-3 py-1 rounded ${intensityType === 'RPE' ? 'bg-primary-color text-white' : 'text-slate-400'}`}>RPE</button>
                    <button onClick={() => handleIntensityTypeChange('RIR')} className={`px-3 py-1 rounded ${intensityType === 'RIR' ? 'bg-primary-color text-white' : 'text-slate-400'}`}>RIR</button>
                    <button onClick={() => handleIntensityTypeChange('FALLO')} className={`px-3 py-1 rounded ${intensityType === 'FALLO' ? 'bg-red-500 text-white' : 'text-slate-400'}`}>Fallo</button>
                    <button onClick={() => handleIntensityTypeChange('AMRAP')} className={`px-3 py-1 rounded ${intensityType === 'AMRAP' ? 'bg-cyber-cyan text-white' : 'text-slate-400'}`}>AMRAP</button>
                </div>

                <div className="flex items-center gap-3">
                    {intensityType === 'RPE' && (
                        <div className="flex flex-col items-center">
                            <input type="number" step="0.5" value={inputs.rpe} onChange={e => onInputChange('rpe', e.target.value)} placeholder="-" className="effort-input !w-16 !h-10 !text-lg" autoComplete="off"/>
                        </div>
                    )}
                    {intensityType === 'RIR' && (
                        <div className="flex flex-col items-center">
                            <input type="number" step="1" value={inputs.rir} onChange={e => onInputChange('rir', e.target.value)} placeholder="-" className="effort-input !w-16 !h-10 !text-lg" autoComplete="off"/>
                        </div>
                    )}
                    {(intensityType === 'FALLO' || intensityType === 'AMRAP') && (
                        <div className="h-10 flex items-center justify-center px-4 bg-red-500/20 border border-red-500 rounded text-red-500 font-bold text-sm">
                            <FlameIcon size={16} className="mr-1"/> AL FALLO
                        </div>
                    )}
                    
                     <button 
                        onClick={() => onInputChange('isIneffective', !inputs.isIneffective)} 
                        className={`p-2 rounded-lg border transition-all ${inputs.isIneffective ? 'bg-yellow-500/20 border-yellow-500 text-yellow-500' : 'bg-slate-800 border-slate-700 text-slate-400'}`}
                        title="Serie Fallida / No Efectiva"
                    >
                        <AlertTriangleIcon size={20} />
                    </button>
                </div>
            </div>
        );
    };

    return (
        <div className="set-card-content space-y-6 p-4">
             <div className="grid grid-cols-3 gap-3">
                {isChangeOfPlans ? (
                    <div className="target-stat-card col-span-2"><span className="value text-yellow-400"><BrainIcon/></span><span className="label">Modo Libre</span></div>
                ) : (
                    <>
                        <div className="target-stat-card"><span className="value">{set.targetReps || `${set.targetDuration}s` || '-'}</span><span className="label">Objetivo</span></div>
                        {(exercise.trainingMode || 'reps') === 'reps' ? (
                             <div className="target-stat-card">
                                 <span className={`value ${debt >= 0 ? 'text-green-400' : 'text-red-400'}`}>{debt > 0 ? '+' : ''}{debt}</span>
                                 <span className="label">Deuda</span>
                             </div>
                        ) : (
                            <div className="target-stat-card"><span className="value">{set.targetPercentageRM || '-'}<span>{set.targetPercentageRM ? '%' : ''}</span></span><span className="label">% 1RM</span></div>
                        )}
                    </>
                )}
                 <div className="target-stat-card">
                    <span className={`value ${typeof intensityDisplay.value === 'string' ? '!text-2xl' : ''}`}>{intensityDisplay.value}</span>
                    <span className="label">{intensityDisplay.label}</span>
                </div>
            </div>
            
            <div className="flex flex-col items-center gap-6">
                {exercise.trainingMode === 'time' ? (
                    <div className="w-full max-w-xs text-center">
                        <label className="text-sm font-semibold text-slate-300 mb-2 block">Duración (s)</label>
                        <div className="flex items-center justify-center gap-3">
                            <button type="button" onClick={() => handleAdjust('duration', -5)} className="workout-adjust-btn"><MinusIcon/></button>
                            <input type="number" inputMode="numeric" value={inputs.duration} onChange={e => onInputChange('duration', e.target.value)} className="workout-input-v2 !h-16 !w-24 !text-4xl"/>
                            <button type="button" onClick={() => handleAdjust('duration', 5)} className="workout-adjust-btn"><PlusIcon/></button>
                        </div>
                    </div>
                ) : (
                    <div className="w-full max-w-xs text-center">
                        <label className={`text-sm font-semibold mb-2 block ${repInputMode === 'partial' ? 'text-blue-400' : 'text-slate-300'}`}>
                            {repInputMode === 'standard' ? 'Reps Completadas' : 'Reps Parciales'}
                        </label>
                        <div className="flex items-center justify-center gap-3">
                            <button type="button" onClick={() => handleAdjust('reps', -1)} className="workout-adjust-btn"><MinusIcon/></button>
                            <div className="relative">
                                <input 
                                    ref={repInputRef}
                                    type="number" 
                                    inputMode="numeric" 
                                    value={repInputMode === 'standard' ? inputs.reps : inputs.partialReps} 
                                    onChange={e => handleRepChange(e.target.value)} 
                                    className={`workout-input-v2 !h-16 !w-24 !text-4xl rounded-xl transition-colors ${repInputMode === 'partial' ? '!bg-blue-900/30 text-blue-300' : ''}`}
                                />
                                <button 
                                    type="button" 
                                    onClick={toggleRepMode} 
                                    className={`absolute top-1 right-1 w-7 h-7 rounded-lg flex items-center justify-center border transition-all ${repInputMode === 'partial' ? 'bg-blue-500 border-blue-400 text-white' : 'bg-slate-700 border-slate-600 text-slate-400'}`}
                                >
                                    <RefreshCwIcon size={14} className={repInputMode === 'partial' ? 'animate-spin-once' : ''}/>
                                </button>
                                {/* Mini summary indicators */}
                                <div className="absolute -bottom-1.5 left-0 right-0 flex justify-center gap-1">
                                    {repInputMode === 'partial' && inputs.reps && inputs.reps !== '0' && (
                                        <span className="bg-slate-800 text-slate-400 text-[9px] px-1.5 py-0.5 rounded leading-none">{inputs.reps} F</span>
                                    )}
                                    {repInputMode === 'standard' && inputs.partialReps && inputs.partialReps !== '0' && (
                                        <span className="bg-blue-900/40 text-blue-400 text-[9px] px-1.5 py-0.5 rounded leading-none">{inputs.partialReps} P</span>
                                    )}
                                </div>
                            </div>
                            <button type="button" onClick={() => handleAdjust('reps', 1)} className="workout-adjust-btn"><PlusIcon/></button>
                        </div>
                    </div>
                )}
                 <div className="w-full max-w-xs text-center">
                    <label className="text-sm font-semibold text-slate-300 mb-2 block">Peso ({settings.weightUnit})</label>
                    <div className="flex items-center justify-center gap-3">
                        <button type="button" onClick={() => handleAdjust('weight', -1)} className="workout-adjust-btn"><MinusIcon/></button>
                        <input type="number" inputMode="decimal" step="0.5" value={inputs.weight} onChange={e => onInputChange('weight', e.target.value)} className="workout-input-v2"/>
                        <button type="button" onClick={() => handleAdjust('weight', 1)} className="workout-adjust-btn"><PlusIcon/></button>
                    </div>
                </div>

                <div className="w-full max-w-xs text-center">
                    <label className="text-sm font-semibold text-slate-300 mb-2 block">Intensidad</label>
                    {renderEffortInput()}
                </div>
                
                 {/* Advanced Tech Toggles */}
                <div>
                     <p className="text-[10px] text-center text-slate-500 uppercase font-bold mb-2 tracking-widest">Técnicas Avanzadas</p>
                     <div className="flex flex-wrap justify-center gap-2">
                        {['Drop Set', 'Rest Pause', 'Cluster'].map(tech => {
                            const dbValue = tech.toLowerCase().replace(' ', '-');
                            const isActive = inputs.advancedTechnique === dbValue;
                            return (
                                <button
                                    key={tech}
                                    onClick={() => onInputChange('advancedTechnique', isActive ? '' : dbValue)}
                                    className={`px-3 py-1 rounded-full text-xs font-semibold border transition-all ${isActive ? 'bg-purple-600/30 border-purple-500 text-purple-300' : 'bg-slate-800 border-slate-700 text-slate-400 hover:border-slate-500'}`}
                                >
                                    {tech}
                                </button>
                            )
                        })}
                     </div>
                </div>

            </div>
             <div className="flex items-center justify-center gap-4">
                 <Button onClick={onLogSet} className="w-full max-w-xs !py-3 !text-lg shadow-xl shadow-primary-color/10">{isLogged ? 'Actualizar Serie' : 'Guardar Serie'}</Button>
                 <button onClick={onToggleChangeOfPlans} title="Modo Libre / Cambio de Planes" className={`p-3 rounded-xl transition-all duration-200 ${isChangeOfPlans ? 'bg-yellow-500/20 text-yellow-400 ring-2 ring-yellow-500/80' : 'bg-slate-800 text-slate-400 hover:bg-slate-700'}`}>
                    <BrainIcon size={24}/>
                </button>
             </div>
            {plateCombination && plateCombination.platesPerSide.length > 0 && (
                <div className="text-center pt-4 border-t border-slate-700/50">
                    <label className="text-sm font-semibold text-slate-300 mb-2 block">Discos (por lado)</label>
                    <div className="flex flex-wrap justify-center gap-2">
                        {plateCombination.platesPerSide.map(({plate, count}) => (
                            <span key={plate} className="plate-chip">{count} &times; {plate}{settings.weightUnit}</span>
                        ))}
                         {plateCombination.remainder > 0 && <span className="plate-chip !bg-slate-800 !text-slate-500">Resto: {plateCombination.remainder}{settings.weightUnit}</span>}
                    </div>
                </div>
            )}
        </div>
    );
});

// Re-use TagSelector here if possible, but for simplicity let's define it inline or re-export
const TagSelector: React.FC<{
    exercise: Exercise;
    exerciseInfo: ExerciseMuscleInfo | undefined;
    selectedTag: string | undefined;
    onTagChange: (tag: string) => void;
}> = ({ exercise, exerciseInfo, selectedTag, onTagChange }) => {
    const [inputValue, setInputValue] = useState(selectedTag || '');

    useEffect(() => {
        setInputValue(selectedTag || '');
    }, [selectedTag]);

    const handleBlur = () => {
        const trimmedValue = inputValue.trim();
        if (trimmedValue && trimmedValue !== (selectedTag || 'Base')) {
            onTagChange(trimmedValue);
        }
    };

    const tags = useMemo(() => {
        const tagSet = new Set<string>();
        (exercise.brandEquivalencies || []).forEach(b => tagSet.add(b.brand));
        (exerciseInfo?.brandEquivalencies || []).forEach(b => tagSet.add(b.brand));
        // Add defaults
        tagSet.add('Base');
        tagSet.add('Sentado');
        tagSet.add('Parado');
        tagSet.add('Unilateral');
        return Array.from(tagSet);
    }, [exercise, exerciseInfo]);

    return (
        <div className="px-2 pb-2">
            <label className="text-xs font-semibold text-slate-400 mb-1 block">Etiqueta / Variante:</label>
            <div className="flex flex-wrap gap-2 items-center">
                {tags.slice(0, 5).map(tag => (
                    <button
                        key={tag}
                        onClick={() => onTagChange(tag)}
                        className={`weight-preset-chip !text-xs ${selectedTag === tag ? '!bg-primary-color text-white' : ''}`}
                    >
                        {tag}
                    </button>
                ))}
                 <input
                    type="text"
                    value={inputValue}
                    onChange={(e) => setInputValue(e.target.value)}
                    onBlur={handleBlur}
                    onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur()}}
                    placeholder="Escribe..."
                    className="!text-xs !py-1 !px-2 bg-slate-800 border-slate-700 rounded-md w-24"
                />
            </div>
        </div>
    );
};

interface LogWorkoutViewProps {
  sessionInfo: { session: Session, program: Program };
  settings: Settings;
  history: WorkoutLog[];
  onSave: (log: WorkoutLog) => void;
  onCancel: () => void;
  isFinishModalOpen: boolean;
  setIsFinishModalOpen: (isOpen: boolean) => void;
  onUpdateExercise1RM: (exerciseDbId: string | undefined, exerciseName: string, weight: number, reps: number, testDate?: string, machineBrand?: string) => void;
  onUpdateExerciseInProgram: (programId: string, sessionId: string, exerciseId: string, updatedExercise: Exercise) => void;
  exerciseList: ExerciseMuscleInfo[];
}

const LogWorkoutView: React.FC<LogWorkoutViewProps> = ({ sessionInfo, settings, history, onSave, onCancel, isFinishModalOpen, setIsFinishModalOpen, onUpdateExercise1RM, onUpdateExerciseInProgram, exerciseList }) => {
  const { program } = sessionInfo;
  const { addToast, addOrUpdateCustomExercise } = useAppDispatch();
  
  const [currentSession, setCurrentSession] = useState<Session>(sessionInfo.session);
  const [activeExerciseId, setActiveExerciseId] = useState<string | null>(null);
  const [activeSetId, setActiveSetId] = useState<string | null>(null);
  
  const [setInputs, setSetInputs] = useState<Record<string, { reps: string; weight: string; rpe: string; rir: string; isFailure?: boolean, isIneffective?: boolean, duration?: string, advancedTechnique?: string, isPartial?: boolean, partialReps?: string }>>({});
  const [completedSets, setCompletedSets] = useState<Record<string, OngoingSetData>>({});
  const [dynamicWeights, setDynamicWeights] = useState<Record<string, { consolidated?: number, technical?: number }>>({});
  const [historyModalExercise, setHistoryModalExercise] = useState<Exercise | null>(null);
  const [substituteModalExercise, setSubstituteModalExercise] = useState<Exercise | null>(null);
  const [selectedTags, setSelectedTags] = useState<Record<string, string>>({}); // Replaced selectedBrands
  const [changeOfPlansSets, setChangeOfPlansSets] = useState<Set<string>>(new Set());
  
  const exercisesToLog = useMemo(() => {
    const sanitizedSession = { ...currentSession };
    if (!Array.isArray(sanitizedSession.parts)) sanitizedSession.parts = [];
    if (!Array.isArray(sanitizedSession.exercises)) sanitizedSession.exercises = [];

    return (sanitizedSession.parts && sanitizedSession.parts.length > 0)
        ? sanitizedSession.parts.flatMap(p => p.exercises)
        : sanitizedSession.exercises;
    }, [currentSession]);

    useEffect(() => {
        if (!activeExerciseId && exercisesToLog.length > 0) {
            setActiveExerciseId(exercisesToLog[0].id);
            setActiveSetId(exercisesToLog[0].sets[0].id);
        }
    }, [exercisesToLog, activeExerciseId]);

  useEffect(() => {
    const initialWeights: Record<string, { consolidated?: number; technical?: number }> = {};
    exercisesToLog.forEach(ex => {
        if ((ex.trainingMode || 'reps') === 'reps') {
            const lastUsedWeight = history.slice().reverse().flatMap(log => log.completedExercises).find(ce => ce.exerciseId === ex.id)?.sets.slice(-1)[0]?.weight;
            initialWeights[ex.id] = { consolidated: ex.sets[0]?.consolidatedWeight || lastUsedWeight, technical: ex.sets[0]?.technicalWeight };
        }
    });
    setDynamicWeights(initialWeights);
  }, [exercisesToLog, history]);

  useEffect(() => {
    const currentExercise = exercisesToLog.find(ex => ex.id === activeExerciseId);
    if (currentExercise) {
        const newInputs = { ...setInputs };
        currentExercise.sets.forEach((set, setIndex) => {
            if (!newInputs[set.id]) {
                if (completedSets[set.id]) {
                    const completed = completedSets[set.id];
                    newInputs[set.id] = { 
                        reps: completed.reps?.toString() || '',
                        duration: completed.duration?.toString() || '',
                        weight: completed.weight.toString(),
                        rpe: completed.rpe?.toString() || '',
                        rir: completed.rir?.toString() || '',
                        isFailure: completed.isFailure,
                        isIneffective: completed.isIneffective,
                        advancedTechnique: completed.amrapReps ? 'amrap' : '',
                        isPartial: completed.isPartial,
                        partialReps: completed.partialReps?.toString() || ''
                    };
                } else {
                    const completedSetsForThisExercise = currentExercise.sets
                        .slice(0, setIndex)
                        .map(s => completedSets[s.id])
                        .filter((d): d is OngoingSetData => !!d)
                        .map(d => ({ reps: d.reps || 0, weight: d.weight }));
                    
                    const exerciseInfo = exerciseList.find(e => e.id === currentExercise.exerciseDbId);
                    const suggestedWeight = getWeightSuggestionForSet(
                        currentExercise, exerciseInfo, setIndex, completedSetsForThisExercise, 
                        settings, history, selectedTags[currentExercise.id]
                    );
                    newInputs[set.id] = { reps: set.targetReps?.toString() || '', duration: set.targetDuration?.toString() || '', weight: suggestedWeight?.toString() || '', rpe: '', rir: '', isFailure: false, isIneffective: false, advancedTechnique: '', isPartial: false, partialReps: '' };
                }
            }
        });
        setSetInputs(newInputs);
    }
  }, [activeExerciseId, activeSetId, exercisesToLog, dynamicWeights, settings, history, completedSets, selectedTags, exerciseList]);

  const handleSetInputChange = useCallback((setId: string, field: keyof typeof setInputs[string], value: string | boolean) => {
      setSetInputs(prev => ({
          ...prev,
          [setId]: { ...(prev[setId] || { reps: '', weight: '', rpe: '', rir: '', duration: '', partialReps: '' }), [field]: value }
      }));
  }, []);

  const handleLogSet = useCallback((exercise: Exercise, set: ExerciseSet) => {
    const inputs = setInputs[set.id] || { reps: '', weight: '', rpe: '', rir: '', duration: '', partialReps: '' };
    const repsRaw = inputs.reps ? parseInt(inputs.reps, 10) : undefined;
    const reps = (repsRaw !== undefined && !isNaN(repsRaw)) ? repsRaw : undefined;
    const duration = inputs.duration ? parseInt(inputs.duration, 10) : undefined;
    const weight = parseFloat(inputs.weight);

    if (isNaN(weight) || (reps === undefined && duration === undefined && !inputs.isIneffective)) { return; }
    
    const rpe = inputs.rpe ? parseFloat(inputs.rpe) : undefined;
    const rir = inputs.rir ? parseInt(inputs.rir, 10) : undefined;
    const machineBrand = selectedTags[exercise.id];
    const isChangeOfPlans = changeOfPlansSets.has(set.id);
    
    let partialReps = 0;
    if (inputs.partialReps) {
        partialReps = parseInt(inputs.partialReps, 10);
        if (isNaN(partialReps)) partialReps = 0;
    }

    setCompletedSets(prev => ({ ...prev, [set.id]: { 
        reps, duration, weight, rpe, rir, machineBrand, isChangeOfPlans, 
        isFailure: inputs.isFailure, isIneffective: inputs.isIneffective,
        isPartial: inputs.isPartial || partialReps > 0,
        partialReps,
        amrapReps: inputs.advancedTechnique === 'amrap' ? reps : undefined 
    }}));
    hapticImpact(ImpactStyle.Light);
    
    if (typeof reps === 'number' && !inputs.isIneffective) {
        onUpdateExercise1RM(exercise.exerciseDbId, exercise.name, weight, reps, new Date().toISOString(), machineBrand);
    }
  }, [addToast, changeOfPlansSets, onUpdateExercise1RM, selectedTags, setInputs, settings.weightUnit]);
  
    const handleToggleChangeOfPlans = useCallback((setId: string) => {
        setChangeOfPlansSets(prev => {
            const newSet = new Set(prev);
            if (newSet.has(setId)) {
                newSet.delete(setId);
            } else {
                newSet.add(setId);
            }
            return newSet;
        });
        hapticImpact(ImpactStyle.Light);
    }, []);

  const handleTagChange = useCallback((exerciseId: string, tag: string) => {
        setSelectedTags(prev => ({ ...prev, [exerciseId]: tag }));
         const exerciseInfo = exerciseList.find(e => e.id === exercisesToLog.find(ex => ex.id === exerciseId)?.exerciseDbId);
        if (exerciseInfo) {
            const tagExists = exerciseInfo.brandEquivalencies?.some(b => b.brand === tag);
            if (!tagExists) {
                const updatedExerciseInfo = {
                    ...exerciseInfo,
                    brandEquivalencies: [...(exerciseInfo.brandEquivalencies || []), { brand: tag }]
                };
                addOrUpdateCustomExercise(updatedExerciseInfo);
            }
        }
  }, [exerciseList, exercisesToLog, addOrUpdateCustomExercise]);

  const handleSave = useCallback((
    notes?: string, 
    discomforts?: string[], 
    fatigueLevel?: number, 
    mentalClarity?: number, 
    durationInMinutes?: number, 
    logDate?: string, 
    photoUri?: string, 
    planDeviations?: any[], 
    focus?: number,
    pump?: number, 
    environmentTags?: string[],
    sessionDifficulty?: number,
    planAdherenceTags?: string[]
) => {
    const completedExercises: CompletedExercise[] = exercisesToLog.map(ex => ({
      exerciseId: ex.id, exerciseDbId: ex.exerciseDbId, exerciseName: ex.name,
      sets: ex.sets.map(set => ({ 
          id: set.id, targetReps: set.targetReps, targetRPE: set.targetRPE, 
          completedReps: completedSets[set.id]?.reps,
          completedDuration: completedSets[set.id]?.duration,
          weight: completedSets[set.id]?.weight,
          completedRPE: completedSets[set.id]?.rpe,
          completedRIR: completedSets[set.id]?.rir,
          machineBrand: completedSets[set.id]?.machineBrand,
          isChangeOfPlans: completedSets[set.id]?.isChangeOfPlans,
          isFailure: completedSets[set.id]?.isFailure,
          isIneffective: completedSets[set.id]?.isIneffective,
          isPartial: completedSets[set.id]?.isPartial,
          partialReps: completedSets[set.id]?.partialReps || 0,
          isAmrap: !!completedSets[set.id]?.amrapReps
      })).filter(s => (s.completedReps !== undefined || s.completedDuration !== undefined || s.isIneffective) && s.weight !== undefined) as CompletedSet[],
    })).filter(ex => ex.sets.length > 0);

    if (completedExercises.length === 0) {
        alert("Debes registrar al menos una serie para guardar el entrenamiento.");
        setIsFinishModalOpen(false);
        return;
    }

    const newLog: WorkoutLog = {
      id: crypto.randomUUID(), programId: program.id, programName: program.name,
      sessionId: currentSession.id, sessionName: currentSession.name, 
      date: safeCreateISOStringFromDateInput(logDate),
      duration: (typeof durationInMinutes === 'number' && !isNaN(durationInMinutes)) ? durationInMinutes * 60 : undefined,
      completedExercises, notes, discomforts,
      fatigueLevel: fatigueLevel || 5, mentalClarity: mentalClarity || 5,
      gymName: settings.gymName,
      focus, pump, environmentTags, sessionDifficulty, planAdherenceTags,
    };
    onSave(newLog);
  }, [completedSets, onSave, program, currentSession, settings.gymName, setIsFinishModalOpen, exercisesToLog]);

    const handleSelectAlternative = useCallback((newExerciseName: string) => {
        if (!substituteModalExercise) return;
        const newExerciseData = exerciseList.find(ex => ex.name.toLowerCase() === newExerciseName.toLowerCase());
        const newExercise: Exercise = {
            ...substituteModalExercise,
            id: crypto.randomUUID(), name: newExerciseName,
            exerciseDbId: newExerciseData?.id,
        };
        setCurrentSession(prev => ({
            ...prev,
            exercises: prev.exercises.map(ex => ex.id === substituteModalExercise.id ? newExercise : ex),
            ...(prev.parts && { parts: prev.parts.map(p => ({ ...p, exercises: p.exercises.map(ex => ex.id === substituteModalExercise.id ? newExercise : ex) })) })
        }));
        addToast(`'${substituteModalExercise.name}' sustituido por '${newExerciseName}'.`, 'suggestion');
        setSubstituteModalExercise(null);
    }, [substituteModalExercise, addToast, exerciseList]);

    const completedSetsCount = useMemo(() => Object.keys(completedSets).length, [completedSets]);
    const totalSetsInSession = useMemo(() => {
        const setIds = new Set<string>();
        exercisesToLog.forEach(ex => ex.sets.forEach(s => setIds.add(s.id)));
        return setIds.size;
    }, [exercisesToLog]);

  return (
    <div className="pb-28">
        <SubstituteExerciseSheet
            isOpen={!!substituteModalExercise}
            onClose={() => setSubstituteModalExercise(null)}
            exercise={substituteModalExercise}
            onSelectAlternative={handleSelectAlternative}
        />
        <FinishWorkoutModal
            isOpen={isFinishModalOpen}
            onClose={() => setIsFinishModalOpen(false)}
            onFinish={handleSave}
            mode="log"
            initialDurationInSeconds={3600}
        />
        {historyModalExercise && (
            <ExerciseHistoryModal
                exercise={historyModalExercise} programId={program.id} history={history}
                settings={settings} onClose={() => setHistoryModalExercise(null)}
            />
        )}
        <WorkoutHeader
            sessionName={currentSession.name}
            totalSets={totalSetsInSession}
            completedSetsCount={completedSetsCount}
        />
        <div className="space-y-2 mt-4">
            {exercisesToLog.map((ex, exIndex) => {
                const pr = settings.showPRsInWorkout ? findPrForExercise(ex, history, settings, selectedTags[ex.id], ex.brandEquivalencies) : null;
                const isExerciseComplete = ex.sets.every(s => completedSets[s.id]);
                const exerciseSetsDone = ex.sets.filter(s => completedSets[s.id]).length;
                const currentDynamicWeights: { consolidated?: number; technical?: number } = dynamicWeights[ex.id] || {};
                return (
                    <details key={ex.id} open={activeExerciseId === ex.id} className={`set-card-details ${ex.isFavorite ? 'border-yellow-500/30 bg-yellow-900/10 open:border-yellow-500/50' : ''}`} onToggle={(e) => { if ((e.target as HTMLDetailsElement).open) setActiveExerciseId(ex.id)}}>
                        <summary className="set-card-summary p-4 !items-start">
                             <div className="flex items-center gap-3 min-w-0">
                                <div className={`w-8 h-8 rounded-full flex items-center justify-center font-bold text-lg flex-shrink-0 ${isExerciseComplete ? 'bg-success-color text-white' : 'bg-slate-700 text-slate-300'}`}>
                                    {isExerciseComplete ? <CheckCircleIcon size={20}/> : exIndex + 1}
                                </div>
                                <div className="min-w-0">
                                    <div className="flex items-center gap-3 flex-wrap">
                                        <p className="font-bold text-white text-lg truncate">{ex.name}</p>
                                        <span className="text-sm text-slate-400 flex-shrink-0">{exerciseSetsDone} / {ex.sets.length} series</span>
                                    </div>
                                    {ex.isFavorite && <StarIcon filled size={16} className="text-yellow-400 mt-1"/>}
                                </div>
                            </div>
                            <div className="flex items-center gap-2 flex-shrink-0">
                                <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); setSubstituteModalExercise(ex); }} className="p-1 text-slate-500 hover:text-primary-color" aria-label={`Sustituir ${ex.name}`}><SwapIcon size={20} /></button>
                                <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); setHistoryModalExercise(ex); }} className="p-1 text-slate-500 hover:text-primary-color" aria-label={`Historial de ${ex.name}`}><ClockIcon size={20} /></button>
                                <ChevronRightIcon className="details-arrow text-slate-400" />
                            </div>
                        </summary>
                        <div className="set-card-content !border-none !p-2 space-y-2">
                            {pr && <div className="p-2 text-center text-sm bg-cyber-copper/20 text-cyber-copper rounded-lg"><p className="font-semibold flex items-center justify-center gap-2"><TrophyIcon size={16}/> Tu PR: {pr.prString} (1RMe: {pr.e1rm}{settings.weightUnit})</p></div>}
                            
                            <TagSelector
                                exercise={ex}
                                exerciseInfo={exerciseList.find(e => e.id === ex.exerciseDbId)}
                                selectedTag={selectedTags[ex.id]}
                                onTagChange={(tag) => {
                                    setSelectedTags(prev => ({...prev, [ex.id]: tag}));
                                }}
                            />

                            {ex.sets.map((set, setIndex) => {
                                const isSetLogged = !!completedSets[set.id];
                                const isChangeOfPlans = changeOfPlansSets.has(set.id);
                                return (
                                    <details key={set.id} open={activeSetId === set.id} className={`set-card-details !bg-panel-color ${isChangeOfPlans ? 'border-yellow-500/50' : ''}`} onToggle={(e) => { if ((e.target as HTMLDetailsElement).open) setActiveSetId(set.id)}}>
                                        <summary className="set-card-summary p-3">
                                            <div className="flex items-center gap-2">
                                                <p className="font-semibold text-slate-300">Serie {setIndex + 1}</p>
                                                {set.advancedTechnique && <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-sky-800 text-sky-300 uppercase">{set.advancedTechnique.replace('-', ' ')}</span>}
                                            </div>
                                            <div className="flex items-center gap-3">
                                                <p className="text-sm text-slate-400 font-mono">{formatSetTarget(set)}</p>
                                                {isSetLogged && <CheckCircleIcon size={20} className="text-green-400"/>}
                                                <ChevronRightIcon className="details-arrow text-slate-400" />
                                            </div>
                                        </summary>
                                        <SetDetails
                                            exercise={ex} set={set}
                                            settings={settings}
                                            inputs={setInputs[set.id] || { reps: '', weight: '', rpe: '', rir: '', isFailure: false, duration: '', partialReps: ''}}
                                            onInputChange={(field, value) => handleSetInputChange(set.id, field, value)}
                                            dynamicWeights={currentDynamicWeights}
                                            onToggleChangeOfPlans={() => handleToggleChangeOfPlans(set.id)}
                                            isChangeOfPlans={isChangeOfPlans}
                                            onLogSet={() => handleLogSet(ex, set)}
                                            isLogged={isSetLogged}
                                        />
                                    </details>
                                )
                            })}
                        </div>
                    </details>
                );
            })}
        </div>
    </div>
  );
};

export default LogWorkoutView;