// components/WorkoutSession.tsx
import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import { requestPermissions, setupNotificationChannels } from '../services/notificationService';
import { Session, WorkoutLog, CompletedExercise, CompletedSet, Exercise, ExerciseSet, WarmupSetDefinition, SessionBackground, OngoingSetData, SetInputState, UnilateralSetInputs, DropSetData, RestPauseData, ExerciseMuscleInfo, Program, Settings, PlanDeviation, CoverStyle, ToastData } from '../types';
import Button from './ui/Button';
import { ClockIcon, ChevronRightIcon, ChevronLeftIcon, FlameIcon, CheckCircleIcon, TrophyIcon, MinusIcon, PlusIcon, MicIcon, MicOffIcon, AlertTriangleIcon, CheckCircleIcon as CheckIcon, XCircleIcon, StarIcon, SparklesIcon, SettingsIcon, ArrowUpIcon, ArrowDownIcon, RefreshCwIcon, BrainIcon, LinkIcon, PlayIcon, PauseIcon, ActivityIcon, InfoIcon, BodyIcon, PencilIcon } from './icons'; 
import { playSound, preloadSounds, configureAudioSession } from '../services/soundService';
import { hapticImpact, ImpactStyle, hapticNotification, NotificationType } from '../services/hapticsService';
import { calculateBrzycki1RM, getWeightSuggestionForSet, roundWeight, calculateWeightFrom1RM } from '../utils/calculations';
import { useAppDispatch, useAppState, useAppContext } from '../contexts/AppContext';
import { calculateSpinalScore, calculatePersonalizedBatteryTanks, calculateSetBatteryDrain, getDynamicAugeMetrics } from '../services/auge';
import { normalizeMuscleGroup } from '../services/volumeCalculator';
import { getCachedAdaptiveData, AugeAdaptiveCache } from '../services/augeAdaptiveService';
import { GPFatigueCurve } from './ui/AugeDeepView';
import FinishWorkoutModal from './FinishWorkoutModal';
import ExerciseHistoryModal from './ExerciseHistoryModal';
import { AdvancedExercisePickerModal } from './AdvancedExercisePickerModal';
import { TacticalModal } from './ui/TacticalOverlays';
import WarmupDrawer from './workout/WarmupDrawer';
import PostExerciseDrawer from './workout/PostExerciseDrawer';
import WorkoutDrawer from './workout/WorkoutDrawer';
import NumpadOverlay from './workout/NumpadOverlay';
import { FinishContextBottomSheet } from './workout/FinishContextBottomSheet';
import CardCarouselBar, { type CarouselItem, type CarouselItemType } from './workout/CardCarouselBar';
import ExerciseCardContextMenu from './workout/ExerciseCardContextMenu';
import { InCardTimer } from './workout/InCardTimer';
import { SetTimerButton } from './workout/SetTimerButton';
import { useKeyboardOverlayMode } from '../hooks/useKeyboardOverlayMode';

// --- ENHANCED GOAL PROGRESS OVERLAY (STABLE & ELEGANT) ---
const GoalProgressOverlay: React.FC<{
    current1RM: number;
    goal1RM: number;
    unit: string;
    onAnimationComplete: () => void;
}> = ({ current1RM, goal1RM, unit, onAnimationComplete }) => {
    const [renderProgress, setRenderProgress] = useState(0);
    const targetPercent = Math.min(100, (current1RM / goal1RM) * 100);
    const isGoalMet = current1RM >= goal1RM;

    useEffect(() => {
        playSound(isGoalMet ? 'session-complete-sound' : 'new-pr-sound');
        hapticNotification(NotificationType.Success);

        // Iniciar animación después de un breve delay para asegurar renderizado
        const animTimer = setTimeout(() => {
            setRenderProgress(targetPercent);
        }, 300);

        // Auto-cierre
        const closeTimer = setTimeout(onAnimationComplete, 5500);

        return () => {
            clearTimeout(animTimer);
            clearTimeout(closeTimer);
        };
    }, [targetPercent, onAnimationComplete, isGoalMet]);

    return (
        <div className="fixed inset-0 z-[2000] flex items-center justify-center p-6 animate-fade-in pointer-events-auto">
            <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onAnimationComplete} />
            <div className="relative z-10 w-full max-w-sm overflow-hidden bg-[#0a0a0a] border border-white/10 rounded-lg shadow-xl p-6 text-center animate-fade-in">
                <h3 className="text-lg font-bold text-white mb-1">
                    {isGoalMet ? 'Meta alcanzada' : 'Progreso hacia objetivo'}
                </h3>
                <p className="text-sm text-slate-400 mb-4">
                    {current1RM.toFixed(1)}{unit} / {goal1RM}{unit} ({renderProgress.toFixed(0)}%)
                </p>
                <div className="h-1.5 bg-slate-800 rounded-full overflow-hidden mb-6">
                    <div className="h-full bg-amber-500/70 rounded-full transition-all duration-500" style={{ width: `${renderProgress}%` }} />
                </div>
                <button onClick={onAnimationComplete} className="w-full py-2.5 rounded-lg border border-white/10 bg-white/5 text-slate-400 font-bold text-[10px] uppercase tracking-widest hover:bg-white/10 hover:text-white transition-colors">Cerrar</button>
            </div>
        </div>
    );
};

const PART_THEME_COLORS = { warmup: '#64748b', main: '#D97706', finisher: '#7c3aed', default: '#64748b' } as const;

const getPartTheme = (name: string) => {
    const n = name.toLowerCase();
    if (n.includes('calentamiento') || n.includes('warmup') || n.includes('movilidad'))
        return { color: PART_THEME_COLORS.warmup, bgColor: 'rgba(100, 116, 139, 0.08)', borderColor: PART_THEME_COLORS.warmup };
    if (n.includes('principal') || n.includes('main') || n.includes('fuerza') || n.includes('básicos'))
        return { color: PART_THEME_COLORS.main, bgColor: 'rgba(217, 119, 6, 0.08)', borderColor: PART_THEME_COLORS.main };
    if (n.includes('finisher') || n.includes('accesorio') || n.includes('aislamiento') || n.includes('bombeo'))
        return { color: PART_THEME_COLORS.finisher, bgColor: 'rgba(124, 58, 237, 0.08)', borderColor: PART_THEME_COLORS.finisher };
    return { color: PART_THEME_COLORS.default, bgColor: 'rgba(100, 116, 139, 0.08)', borderColor: PART_THEME_COLORS.default };
};

const findPrForExercise = (exerciseInfo: ExerciseMuscleInfo, history: WorkoutLog[], settings: any, currentTag?: string): { prString: string, e1rm: number, reps: number } | null => {
    let best1RM = exerciseInfo.calculated1RM || 0;
    let prString = '';
    let bestReps = Infinity;
    if (best1RM > 0) { prString = `Estimado: ${best1RM.toFixed(1)}${settings.weightUnit}`; }
    history.forEach(log => {
        const completedEx = log.completedExercises.find(ce => ce.exerciseDbId === exerciseInfo.id);
        if (completedEx) {
            const logTag = completedEx.machineBrand || 'Base';
            if (currentTag && logTag !== currentTag && logTag !== 'Base') return;
            completedEx.sets.forEach(set => {
                if (typeof set.weight === 'number' && typeof set.completedReps === 'number' && set.completedReps > 0) {
                    const e1RM = calculateBrzycki1RM(set.weight, set.completedReps);
                    if (e1RM > best1RM) { best1RM = e1RM; prString = `${set.weight}${settings.weightUnit} x ${set.completedReps} reps${set.machineBrand ? ` (${set.machineBrand})` : ''}`; bestReps = set.completedReps; }
                }
            });
        }
    });
    if (best1RM > 0) { return { prString, e1rm: Math.round(best1RM * 10) / 10, reps: bestReps }; }
    return null;
};

const getSetTypeLabel = (set: ExerciseSet): 'W' | 'T' | 'F' | 'D' => {
    if (set.isAmrap || set.intensityMode === 'failure') return 'F';
    if (set.dropSets && set.dropSets.length > 0) return 'D';
    return 'T';
};

const formatSetTarget = (set: ExerciseSet, exercise: Exercise, settings: Settings) => {
    const parts: string[] = [];
    if (exercise.trainingMode === 'percent') {
        const reps = set.targetReps || '?';
        const percent = set.targetPercentageRM || '?';
        let weightStr = '';
        if (exercise.reference1RM && set.targetPercentageRM) {
             const weight = calculateWeightFrom1RM(exercise.reference1RM, 1) * (set.targetPercentageRM / 100);
             weightStr = `(${roundWeight(weight, settings.weightUnit)}${settings.weightUnit})`;
        }
        return `${reps} Reps @ ${percent}% ${weightStr}`;
    }
    if (set.isAmrap) {
        if (set.targetReps) return `${set.targetReps}+ ${exercise.customUnit || 'Reps'}`;
        return 'AMRAP';
    }
    if (exercise.trainingMode === 'time' && set.targetDuration) { parts.push(`${set.targetDuration}s`); } 
    else if (set.targetReps) { parts.push(`${set.targetReps} ${exercise.customUnit || 'reps'}`); }
    
    if (set.intensityMode === 'failure') parts.push('Al Fallo');
    else if (set.targetRPE) parts.push(`@ RPE ${set.targetRPE}`);
    else if (set.targetRIR !== undefined) parts.push(`@ RIR ${set.targetRIR}`);
    return parts.join(' ');
};

const HeaderAccordion: React.FC<{
    exercise: Exercise;
    exerciseInfo?: ExerciseMuscleInfo;
    selectedTag: string | undefined;
    onTagChange: (tag: string) => void;
}> = ({ exercise, exerciseInfo, selectedTag, onTagChange }) => {
    const [expandedSection, setExpandedSection] = useState<'tags' | 'setup' | null>(null);
    const { addOrUpdateCustomExercise, exerciseList } = useAppContext();
    const [localDetails, setLocalDetails] = useState(exercise.setupDetails || {});
    const [newTagName, setNewTagName] = useState('');
    const [isEditingTag, setIsEditingTag] = useState(false);

    const toggleSection = (section: 'tags' | 'setup') => {
        if (expandedSection === section) setExpandedSection(null);
        else setExpandedSection(section);
    };

    const handleSaveSetup = () => {
        const exInfo = exerciseList.find(e => e.id === exercise.exerciseDbId);
        if (exInfo) {
            addOrUpdateCustomExercise({ ...exInfo, setupDetails: localDetails });
        }
        setExpandedSection(null);
    }

    const liveExerciseInfo = useMemo(() => {
        return exerciseList.find(e => e.id === exerciseInfo?.id) || exerciseInfo;
    }, [exerciseList, exerciseInfo]);

    const tags = useMemo(() => {
        const tagSet = new Set<string>();
        (exercise.brandEquivalencies || []).forEach(b => tagSet.add(b.brand));
        (liveExerciseInfo?.brandEquivalencies || []).forEach(b => tagSet.add(b.brand));
        tagSet.add('Base'); tagSet.add('Sentado'); tagSet.add('Parado'); tagSet.add('Unilateral');
        if (selectedTag) tagSet.add(selectedTag);
        return Array.from(tagSet).sort();
    }, [exercise, liveExerciseInfo, selectedTag]);

    const handleCreateTag = () => {
        if (newTagName.trim()) {
            const tagName = newTagName.trim();
            onTagChange(tagName);
            if (liveExerciseInfo) {
                const currentTags = liveExerciseInfo.brandEquivalencies || [];
                if (!currentTags.some(b => b.brand === tagName)) {
                    const updatedExercise = { ...liveExerciseInfo, brandEquivalencies: [...currentTags, { brand: tagName }] };
                    addOrUpdateCustomExercise(updatedExercise);
                }
            }
            setNewTagName('');
            setIsEditingTag(false);
            setExpandedSection(null);
        }
    };

    return (
        <div className="w-full mb-2">
            <div className="flex gap-2 mb-2 px-2">
                 <button 
                    onClick={() => toggleSection('tags')} 
                    className={`flex-1 flex items-center justify-center gap-1 text-[10px] uppercase font-medium py-1.5 transition-all ${expandedSection === 'tags' ? 'bg-[#3f3f3f] text-white' : 'bg-[#252525] text-[#a3a3a3] hover:bg-[#3f3f3f] hover:text-white'}`}
                >
                   <BodyIcon size={12}/> {selectedTag && selectedTag !== 'Base' ? selectedTag : 'Etiquetas'} {expandedSection === 'tags' ? <ChevronRightIcon className="-rotate-90" size={12}/> : <ChevronRightIcon className="rotate-90" size={12}/>}
                </button>
                <button 
                    onClick={() => toggleSection('setup')} 
                    className={`flex-1 flex items-center justify-center gap-1 text-[10px] uppercase font-medium py-1.5 transition-all ${expandedSection === 'setup' ? 'bg-[#3f3f3f] text-white' : 'bg-[#252525] text-[#a3a3a3] hover:bg-[#3f3f3f] hover:text-white'}`}
                >
                    <SettingsIcon size={12}/> Setup {expandedSection === 'setup' ? <ChevronRightIcon className="-rotate-90" size={12}/> : <ChevronRightIcon className="rotate-90" size={12}/>}
                </button>
            </div>

            {expandedSection === 'tags' && (
                <div className="animate-fade-in bg-[#252525] p-3">
                    <div className="flex flex-wrap gap-2 mb-3">
                        {tags.map(tag => (
                            <button key={tag} onClick={() => { onTagChange(tag); setExpandedSection(null); }} className={`px-3 py-1.5 text-[10px] font-medium transition-all ${selectedTag === tag ? 'bg-white text-[#1a1a1a]' : 'bg-[#1a1a1a] text-[#a3a3a3] hover:bg-[#3f3f3f] hover:text-white'}`}>{tag}</button>
                        ))}
                    </div>
                    {isEditingTag ? (
                        <div className="flex gap-2 items-center mt-2 animate-fade-in">
                            <input type="text" value={newTagName} onChange={(e) => setNewTagName(e.target.value)} placeholder="Nueva..." className="flex-1 bg-[#1a1a1a] px-2 py-1.5 text-xs text-white outline-none placeholder-[#737373]" autoFocus />
                            <button onClick={handleCreateTag} className="px-2 py-1.5 bg-white text-[#1a1a1a] text-[10px] font-medium"><CheckIcon size={14}/></button>
                            <button onClick={() => setIsEditingTag(false)} className="px-2 py-1.5 bg-[#3f3f3f] text-[#a3a3a3] text-[10px] font-medium"><XCircleIcon size={14}/></button>
                        </div>
                    ) : (
                         <button onClick={() => setIsEditingTag(true)} className="w-full py-2 text-[#a3a3a3] text-[10px] hover:text-white flex items-center justify-center gap-1"><PlusIcon size={10} /> Crear Nueva Etiqueta</button>
                    )}
                </div>
            )}

            {expandedSection === 'setup' && (
                 <div className="animate-fade-in bg-[#252525] p-3 space-y-3">
                     <div className="grid grid-cols-2 gap-3">
                         <div><label className="text-[9px] text-[#a3a3a3] font-medium uppercase block mb-1">Asiento</label><input type="text" value={localDetails.seatPosition || ''} onChange={e => setLocalDetails({...localDetails, seatPosition: e.target.value})} className="w-full bg-[#1a1a1a] px-2 py-1.5 text-xs text-white outline-none placeholder-[#737373]"/></div>
                         <div><label className="text-[9px] text-[#a3a3a3] font-medium uppercase block mb-1">Pines</label><input type="text" value={localDetails.pinPosition || ''} onChange={e => setLocalDetails({...localDetails, pinPosition: e.target.value})} className="w-full bg-[#1a1a1a] px-2 py-1.5 text-xs text-white outline-none placeholder-[#737373]"/></div>
                     </div>
                     <div><label className="text-[9px] text-[#a3a3a3] font-medium uppercase block mb-1">Notas</label><textarea value={localDetails.equipmentNotes || ''} onChange={e => setLocalDetails({...localDetails, equipmentNotes: e.target.value})} rows={2} className="w-full bg-[#1a1a1a] px-2 py-1.5 text-xs text-white outline-none placeholder-[#737373]"/></div>
                     <button onClick={handleSaveSetup} className="w-full py-2 text-[10px] uppercase font-medium bg-white text-[#1a1a1a]">Guardar Setup</button>
                 </div>
            )}
        </div>
    );
}

const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60).toString().padStart(2, '0');
    const secs = (seconds % 60).toString().padStart(2, '0');
    return `${mins}:${secs}`;
};

const WorkoutHeader: React.FC<{
    sessionName: string;
    activePartName?: string;
    activePartColor?: string;
    isResting?: boolean;
    onFinishPress?: () => void;
    onFinishLongPress?: () => void;
    restTimerRemaining?: number;
    elapsedSeconds?: number;
    completedSetsCount?: number;
    totalSetsCount?: number;
}> = React.memo(({ sessionName, activePartName, activePartColor, isResting, onFinishPress, onFinishLongPress, restTimerRemaining, elapsedSeconds, completedSetsCount, totalSetsCount }) => {
    const longPressTimerRef = React.useRef<ReturnType<typeof setTimeout> | null>(null);
    const longPressFiredRef = React.useRef(false);
    const handlePointerDown = () => {
        longPressFiredRef.current = false;
        if (!onFinishLongPress) return;
        longPressTimerRef.current = setTimeout(() => {
            longPressTimerRef.current = null;
            longPressFiredRef.current = true;
            onFinishLongPress();
        }, 500);
    };
    const handlePointerUp = () => {
        if (longPressTimerRef.current) {
            clearTimeout(longPressTimerRef.current);
            longPressTimerRef.current = null;
        }
    };
    const handleClick = (e: React.MouseEvent) => {
        if (longPressFiredRef.current) {
            e.preventDefault();
            longPressFiredRef.current = false;
            return;
        }
        onFinishPress?.();
    };

    const progressPercent = (completedSetsCount != null && totalSetsCount != null && totalSetsCount > 0)
        ? Math.round((completedSetsCount / totalSetsCount) * 100) : 0;

    return (
        <div className="sticky top-0 z-30 bg-[#2a2a2a] h-auto min-h-[90px]">
            <div className="flex flex-col px-4 py-3 gap-3">
                <div className="w-full">
                    <h2 className="font-semibold text-white text-lg leading-tight break-words">{sessionName}</h2>
                    {activePartName && (
                        <span className="text-[9px] font-medium uppercase tracking-wide mt-0.5 block text-[#a3a3a3]">{isResting ? 'DESCANSO' : activePartName}</span>
                    )}
                </div>
                <div className="flex items-center justify-between gap-3 flex-wrap">
                    <div className="flex items-center gap-3 flex-1 min-w-0">
                        {restTimerRemaining != null && restTimerRemaining > 0 && (
                            <span className="text-xs font-medium text-[#1a1a1a] bg-white px-2 py-1 tabular-nums">{formatTime(restTimerRemaining)}</span>
                        )}
                        {elapsedSeconds != null && (
                            <span className="text-[10px] text-[#a3a3a3] tabular-nums">{formatTime(elapsedSeconds)}</span>
                        )}
                        {completedSetsCount != null && totalSetsCount != null && totalSetsCount > 0 && (
                            <div className="flex-1 min-w-[80px] max-w-[120px]">
                                <div className="h-1 bg-[#404040] overflow-hidden">
                                    <div className="h-full bg-[#737373] transition-all duration-300" style={{ width: `${progressPercent}%` }} />
                                </div>
                                <span className="text-[9px] text-[#a3a3a3]">{completedSetsCount}/{totalSetsCount}</span>
                            </div>
                        )}
                    </div>
                    {onFinishPress && (
                        <button
                            onPointerDown={handlePointerDown}
                            onPointerUp={handlePointerUp}
                            onPointerLeave={handlePointerUp}
                            onClick={handleClick}
                            className="min-w-[44px] min-h-[44px] p-2.5 bg-white text-[#1a1a1a] flex items-center justify-center"
                            title="Finalizar sesión (mantener para más opciones)"
                        >
                            <CheckCircleIcon size={20} strokeWidth={2.5} />
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
});

/** Returns last completed weight/reps for this exercise and set index (ghost = "última vez"). */
function getGhostForSet(exerciseId: string, setIndex: number, history: WorkoutLog[]): { weight: number; reps: number; rpe?: number } | null {
    for (let i = history.length - 1; i >= 0; i--) {
        const log = history[i];
        const completedEx = log.completedExercises.find(ex => ex.exerciseDbId === exerciseId || ex.exerciseId === exerciseId);
        if (completedEx && completedEx.sets[setIndex]) {
            const s = completedEx.sets[setIndex];
            if ((s.completedReps != null && s.completedReps > 0) || (s.weight != null && s.weight > 0)) {
                return { weight: Number(s.weight) || 0, reps: Number(s.completedReps) || 0, rpe: s.completedRPE };
            }
        }
    }
    return null;
}

const GhostSetInfo: React.FC<{ exerciseId: string; setIndex: number; history: WorkoutLog[]; settings: any; }> = ({ exerciseId, setIndex, history, settings }) => {
    const lastLog = useMemo(() => { for (let i = history.length - 1; i >= 0; i--) { const log = history[i]; const completedEx = log.completedExercises.find(ex => ex.exerciseDbId === exerciseId || ex.exerciseId === exerciseId); if (completedEx && completedEx.sets[setIndex]) { if (completedEx.sets[setIndex].completedReps || completedEx.sets[setIndex].weight) { return { date: log.date, set: completedEx.sets[setIndex] }; } } } return null; }, [history, exerciseId, setIndex]);
    if (!lastLog) return null;
    const { date, set } = lastLog;
    const dateStr = new Date(date).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' });
    return ( <div className="text-[10px] text-[#a3a3a3] flex items-center justify-center gap-1.5 mb-2 py-1 border-b border-[#252525] font-medium"><span>{dateStr}: {set.weight}{settings.weightUnit} x {set.completedReps}{set.completedRPE && <span className="ml-1">(@{set.completedRPE})</span>}</span></div>);
};

const SetDetails: React.FC<{
    exercise: Exercise;
    exerciseInfo?: ExerciseMuscleInfo;
    set: ExerciseSet;
    setIndex: number;
    settings: any;
    inputs: UnilateralSetInputs | SetInputState;
    onInputChange: (field: keyof SetInputState, value: any, side?: 'left' | 'right') => void;
    onLogSet: (isCalibrator?: boolean) => void;
    isLogged: boolean;
    history: WorkoutLog[];
    currentSession1RM?: number;
    base1RM?: number;
    isCalibrated?: boolean;
    cardAnimation?: string | null;
    addToast: (message: string, type?: ToastData['type'], title?: string, duration?: number) => void;
    suggestedWeight?: number;
    selectedTag?: string;
    tableRowMode?: boolean;
    setId: string;
    onOpenNumpad: (opts: { field: 'weight' | 'reps' | 'partialReps' | 'duration' | 'rpe' | 'rir' | 'dropSetWeight' | 'dropSetReps' | 'restPauseRestTime' | 'restPauseReps'; dropSetIndex?: number; restPauseIndex?: number; side?: 'left' | 'right' }) => void;
}> = React.memo(({ exercise, exerciseInfo, set, setIndex, settings, inputs, onInputChange, onLogSet, isLogged, history, currentSession1RM, base1RM, isCalibrated, cardAnimation, addToast, suggestedWeight, selectedTag, tableRowMode, setId, onOpenNumpad }) => {
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
    const [isSubmitting, setIsSubmitting] = useState(false); // CANDADO ANTI-DOBLE TOQUE

    const currentInputs: SetInputState = isUnilateral ? (inputs as UnilateralSetInputs)[activeSide] : (inputs as SetInputState);
    const safeInputs: SetInputState = currentInputs || { reps: '', weight: '', rpe: '', rir: '', isFailure: false, isIneffective: false, isPartial: false, duration: '', notes: '', advancedTechnique: '', dropSets: [], restPauses: [], performanceMode: 'target', partialReps: '', technicalQuality: '8', discomfortLevel: '0', discomfortNotes: '', tempo: '' };
    
    const targetReps = set.targetReps || 0;
    const completedReps = parseInt(safeInputs.reps, 10) || 0;
    const loggedDuration = parseInt(safeInputs.duration || '0', 10);
    const targetDuration = set.targetDuration || 0;

    const debt = isTimeMode ? (loggedDuration - targetDuration) : (completedReps - targetReps);
    
    const targetRPE = set.targetRPE || 8;
    const targetRIR = set.targetRIR;
    const currentRPE = safeInputs.rpe ? parseFloat(safeInputs.rpe) : (safeInputs.rir ? 10 - parseFloat(safeInputs.rir) : 0);
    const intensityDiff = currentRPE > 0 ? (currentRPE - targetRPE) : 0;
    
    const perfColorClass = debt > 0 ? 'text-green-400 border-green-500/50 bg-green-900/10' : debt === 0 ? 'text-blue-400 border-blue-500/50 bg-blue-900/10' : 'text-red-400 border-red-500/50 bg-red-900/10';
    const inputActiveColor = (repInputMode === 'standard' || isTimeMode) ? perfColorClass : 'text-white border-slate-700 bg-slate-800';

    let intensityContainerClass = 'bg-slate-800 border-slate-700';
    if (safeInputs.performanceMode === 'target' && currentRPE > 0) {
        if (intensityDiff > 0.5) intensityContainerClass = 'bg-red-900/20 border-red-500/50 shadow-[0_0_10px_rgba(239,68,68,0.2)]';
        else if (intensityDiff < -0.5) intensityContainerClass = 'bg-green-900/20 border-green-500/50 shadow-[0_0_10px_rgba(34,197,94,0.2)]';
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
                // CORRECCIÓN: Usar e.exerciseId en lugar de e.id
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
        if (isSubmitting) return; // Si el candado está cerrado, ignorar toque accidental
        setIsSubmitting(true);

        if (set.isAmrap) {
            const reps = parseInt(safeInputs.reps, 10) || 0;
            const target = set.targetReps || 0;
            if (reps < target) {
                 setShowFailureWarning(true); 
                 setIsSubmitting(false); // Liberar candado si falla validación
                 return; 
            }
        }
        setShowFailureWarning(false);
        onLogSet(!!set.isCalibrator);
        
        // Liberar el candado después de medio segundo
        setTimeout(() => setIsSubmitting(false), 500);
    }
    
    const handleSetDurationSave = (tut: number) => { onInputChange('duration', tut.toString(), isUnilateral ? activeSide : undefined); };
    
    const handleFailedSet = (reason: string) => {
        hapticNotification(NotificationType.Error); // Vibración pesada de error
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
    
    let containerClass = "set-card-content flex flex-col bg-[#252525] p-3 transition-all duration-500";
    if (cardAnimation === 'amrap') containerClass += " bg-[#2a2a2a]";
    else if (cardAnimation === 'success') containerClass += " bg-[#2a2a2a]";
    else if (cardAnimation === 'failure') containerClass += " bg-[#2a2a2a]";

    if (tableRowMode) {
        return (
            <div ref={cardRef} className="space-y-3 px-2 py-2 bg-[#0A0B0E]/80 rounded border border-[#2A2D38] animate-fade-in">
                {isUnilateral && (
                    <div className="flex bg-slate-800/60 p-1 rounded-lg relative z-10 shrink-0">
                        <button onClick={() => setActiveSide('left')} className={`flex-1 py-1.5 text-xs font-bold rounded-md transition-all min-h-[36px] ${activeSide === 'left' ? 'bg-amber-600 text-white' : 'text-slate-400 hover:text-white'}`}>IZQ</button>
                        <button onClick={() => setActiveSide('right')} className={`flex-1 py-1.5 text-xs font-bold rounded-md transition-all min-h-[36px] ${activeSide === 'right' ? 'bg-amber-600 text-white' : 'text-slate-400 hover:text-white'}`}>DER</button>
                    </div>
                )}
                {set.isAmrap ? (
                    <div className={`flex justify-center items-center py-2 rounded-lg border w-full ${set.isCalibrator ? 'bg-amber-950/30 border-amber-500/40 text-amber-400' : 'bg-amber-950/20 border-amber-500/30 text-amber-400'}`}>
                        <span className="text-[10px] font-bold uppercase tracking-wider flex items-center gap-1"><FlameIcon size={12} />{set.isCalibrator ? 'AMRAP Calibrador' : 'AMRAP'}</span>
                    </div>
                ) : (
                    <div className="flex justify-center items-center gap-1 p-1 rounded-lg bg-slate-800/40">
                        <button onClick={() => handlePerformanceModeChange('target')} className={`flex-1 py-1.5 rounded-md border text-[10px] font-bold uppercase transition-all min-h-[36px] ${safeInputs.performanceMode === 'target' ? 'bg-slate-700 border-white/20 text-white' : 'border-transparent text-slate-500 hover:text-white'}`}>{(set.intensityMode === 'rir' || settings.intensityMetric === 'rir') ? 'RIR' : 'RPE'}</button>
                        <button onClick={() => handlePerformanceModeChange('failure')} className={`flex-1 py-1.5 rounded-md border text-[10px] font-bold uppercase transition-all flex items-center justify-center gap-1 min-h-[36px] ${safeInputs.performanceMode === 'failure' ? 'bg-slate-700 border-white/20 text-white' : 'border-transparent text-slate-500 hover:text-white'}`}><FlameIcon size={10} /> Fallo</button>
                        <button onClick={() => handlePerformanceModeChange('failed')} className={`flex-1 py-1.5 rounded-md border text-[10px] font-bold uppercase transition-all flex items-center justify-center gap-1 min-h-[36px] ${safeInputs.performanceMode === 'failed' ? 'bg-slate-700 border-white/20 text-white' : 'border-transparent text-slate-500 hover:text-white'}`}><AlertTriangleIcon size={10} /> Fallido</button>
                    </div>
                )}
                {safeInputs.performanceMode === 'target' && (
                    <div className="flex justify-center">
                        {(set.intensityMode === 'rir' || settings.intensityMetric === 'rir') ? (
                            <div className={`flex items-center rounded-lg p-1.5 border w-28 justify-between transition-colors ${intensityContainerClass}`}>
                                <span className="text-slate-500 font-bold text-xs uppercase px-2">RIR</span>
                                <input type="number" step="1" value={safeInputs.rir} onChange={e => onInputChange('rir', e.target.value, isUnilateral ? activeSide : undefined)} className="w-12 bg-transparent border-none text-center font-bold text-white focus:ring-0 p-0 text-sm tabular-nums" placeholder="-"/>
                            </div>
                        ) : (
                            <div className={`flex items-center rounded-lg p-1.5 border w-28 justify-between transition-colors ${intensityContainerClass}`}>
                                <span className="text-slate-500 font-bold text-xs uppercase px-2">RPE</span>
                                <input type="number" step="0.5" value={safeInputs.rpe} onChange={e => onInputChange('rpe', e.target.value, isUnilateral ? activeSide : undefined)} className="w-12 bg-transparent border-none text-center font-bold text-white focus:ring-0 p-0 text-sm tabular-nums" placeholder="-"/>
                            </div>
                        )}
                    </div>
                )}
                <div className="flex gap-3 text-[10px] font-bold">
                    <button type="button" onClick={() => onInputChange('dropSets', [...(safeInputs.dropSets || []), { weight: 0, reps: 0 }], isUnilateral ? activeSide : undefined)} className="text-amber-400 hover:underline hover:text-amber-300">+ Dropset</button>
                    <button type="button" onClick={() => onInputChange('restPauses', [...(safeInputs.restPauses || []), { restTime: 15, reps: 0 }], isUnilateral ? activeSide : undefined)} className="text-amber-400 hover:underline hover:text-amber-300">+ Rest-Pause</button>
                </div>
                {(safeInputs.dropSets?.length || 0) > 0 || (safeInputs.restPauses?.length || 0) > 0 ? (
                    <div className="p-2 bg-slate-900/50 border border-white/5 rounded-lg space-y-2 text-xs">
                        {(safeInputs.dropSets || []).map((ds, i) => (
                            <div key={`ds-${i}`} className="flex gap-1.5 items-center">
                                <span className="text-[9px] text-slate-400 w-10 shrink-0">Dropset</span>
                                <button type="button" onClick={() => onOpenNumpad({ field: 'dropSetWeight', dropSetIndex: i, side: isUnilateral ? activeSide : undefined })} className="w-12 bg-slate-800 border border-slate-600 rounded px-2 py-1 text-[10px] text-white font-mono shrink-0">{ds.weight === 0 ? 'Kg' : ds.weight}</button>
                                <button type="button" onClick={() => onOpenNumpad({ field: 'dropSetReps', dropSetIndex: i, side: isUnilateral ? activeSide : undefined })} className="w-10 bg-slate-800 border border-slate-600 rounded px-2 py-1 text-[10px] text-white font-mono shrink-0">{ds.reps === 0 ? 'Reps' : ds.reps}</button>
                                <button type="button" onClick={() => onInputChange('dropSets', (safeInputs.dropSets || []).filter((_, j) => j !== i), isUnilateral ? activeSide : undefined)} className="p-1.5 rounded bg-red-900/50 text-red-400 shrink-0" title="Eliminar dropset"><MinusIcon size={12}/></button>
                            </div>
                        ))}
                        {(safeInputs.restPauses || []).map((rp, i) => (
                            <div key={`rp-${i}`} className="flex gap-1.5 items-center">
                                <span className="text-[9px] text-slate-400 w-10 shrink-0">Rest-Pause</span>
                                <button type="button" onClick={() => onOpenNumpad({ field: 'restPauseRestTime', restPauseIndex: i, side: isUnilateral ? activeSide : undefined })} className="w-10 bg-slate-800 border border-slate-600 rounded px-2 py-1 text-[10px] text-white font-mono shrink-0">{rp.restTime === 0 ? 's' : rp.restTime}</button>
                                <button type="button" onClick={() => onOpenNumpad({ field: 'restPauseReps', restPauseIndex: i, side: isUnilateral ? activeSide : undefined })} className="w-10 bg-slate-800 border border-slate-600 rounded px-2 py-1 text-[10px] text-white font-mono shrink-0">{rp.reps === 0 ? 'Reps' : rp.reps}</button>
                                <button type="button" onClick={() => onInputChange('restPauses', (safeInputs.restPauses || []).filter((_, j) => j !== i), isUnilateral ? activeSide : undefined)} className="p-1.5 rounded bg-red-900/50 text-red-400 shrink-0" title="Eliminar rest-pause"><MinusIcon size={12}/></button>
                            </div>
                        ))}
                    </div>
                ) : null}
                <Button onClick={() => handleLogAttempt()} className="w-full !py-2.5 !text-xs !font-black uppercase tracking-widest">
                    {isLogged ? 'ACTUALIZAR SERIE' : set.isAmrap ? 'GUARDAR AMRAP' : 'GUARDAR SERIE'}
                </Button>
            </div>
        );
    }

    return (
        <div ref={cardRef} className={`${containerClass} scroll-mb-48`}>
            {set.isAmrap && !cardAnimation && <div className="absolute inset-0 bg-[#2a2a2a]/50 z-0 pointer-events-none"></div>}
            {isStagnant && <div className="bg-slate-800/60 border border-slate-600/40 p-2 mb-2 rounded-lg text-center text-xs text-slate-400 font-bold flex items-center justify-center gap-2 mx-2 mt-2"><AlertTriangleIcon size={14}/> Mismo peso/reps en 3 sesiones</div>}

            {isUnilateral && (
                <div className="flex bg-[#1a1a1a] p-1 mb-2 relative z-10 shrink-0 mx-2">
                    <button onClick={() => setActiveSide('left')} className={`flex-1 py-1.5 text-xs font-medium transition-all min-h-[40px] ${activeSide === 'left' ? 'bg-white text-[#1a1a1a]' : 'text-[#a3a3a3] hover:text-white'}`}>Izquierda</button>
                    <button onClick={() => setActiveSide('right')} className={`flex-1 py-1.5 text-xs font-medium transition-all min-h-[40px] ${activeSide === 'right' ? 'bg-white text-[#1a1a1a]' : 'text-[#a3a3a3] hover:text-white'}`}>Derecha</button>
                </div>
            )}
            
            <GhostSetInfo exerciseId={(exercise.exerciseDbId || exercise.id) as string} setIndex={setIndex} history={history} settings={settings} />
            
            {showFailureWarning && <div className="mx-2 mb-2 bg-cyber-danger/20 border border-cyber-danger/50 p-3 rounded-xl animate-fade-in relative z-10 text-center"><h4 className="text-cyber-danger font-bold text-sm mb-1">¿Fallo Anticipado?</h4><div className="flex gap-2"><button onClick={() => setShowFailureWarning(false)} className="flex-1 py-2 bg-slate-800 rounded text-xs font-bold">Corregir</button><button onClick={() => onLogSet()} className="flex-1 py-2 bg-cyber-danger rounded text-xs font-bold text-white">Sí, Guardar</button></div></div>}

            <div className="space-y-4 px-2">
                <div className="flex justify-between items-center text-xs px-1 mb-1">
                    <div className="text-slate-400 font-bold uppercase tracking-wide">
                        Meta: <span className="text-white">{isTimeMode ? `${targetDuration}s` : `${targetReps} reps`}</span> <span className="text-slate-600">|</span> <span className="text-white">{set.intensityMode === 'rir' ? `RIR ${targetRIR}` : `RPE ${targetRPE}`}</span>
                    </div>
                    
                    <div className="flex gap-2 items-center flex-wrap">
                        {suggestedLoad && (
                            <div className="flex items-center gap-1.5">
                                <span className="text-[10px] text-sky-400 font-mono font-bold uppercase border border-sky-500/30 bg-sky-900/20 px-2 py-0.5 rounded">Sugerido: {suggestedLoad.toFixed(1)}{settings.weightUnit}</span>
                                <button onClick={() => { hapticImpact(ImpactStyle.Light); onInputChange('weight', suggestedLoad.toFixed(1), isUnilateral ? activeSide : undefined); }} className="text-[9px] font-mono font-black uppercase bg-sky-500/30 hover:bg-sky-500/50 text-sky-300 px-2 py-0.5 rounded border border-sky-500/40 transition-colors">Usar</button>
                            </div>
                        )}
                        
                        {/* NUEVO: INDICADOR ESPINAL EN VIVO MIENTRAS ENTRENA */}
                        {(exerciseInfo?.axialLoadFactor ?? 0) > 0 && (
                            <div className="flex items-center gap-1 bg-red-950/40 border border-red-900/50 px-2 py-0.5 rounded" title="Estrés Espinal de esta Serie">
                                <FlameIcon size={10} className="text-red-500" />
                                <span className="text-[10px] text-red-500 font-bold uppercase">Espinal:</span>
                                <span className="text-[10px] font-mono font-black text-red-400 tabular-nums">
                                    {Math.round(calculateSpinalScore({ 
                                        weight: parseFloat(safeInputs.weight) || suggestedLoad || 0, 
                                        reps: parseInt(safeInputs.reps) || targetReps || 0, 
                                        rpe: parseFloat(safeInputs.rpe) || targetRPE || 0 
                                    }, exerciseInfo))}
                                </span>
                            </div>
                        )}
                    </div>
                </div>

                <div className="grid gap-2 items-center" style={{ gridTemplateColumns: 'minmax(64px,90px) auto minmax(64px,90px)' }}>
                    <div className={`flex flex-col rounded-xl overflow-hidden border shadow-sm relative transition-colors duration-300 ${inputActiveColor}`}>
                         <button 
                            onClick={() => !isTimeMode && setRepInputMode(prev => prev === 'standard' ? 'partial' : 'standard')} 
                            className="text-[9px] uppercase font-bold tracking-widest py-1.5 text-center bg-black/20 hover:bg-black/40 transition-colors"
                        >
                            {isTimeMode ? 'Segundos' : (repInputMode === 'standard' ? 'Reps Reales' : 'Parciales')}
                        </button>
                        <div className="relative flex-1 py-1 flex items-center justify-center min-w-0">
                             <button type="button" onClick={() => onOpenNumpad({ field: isTimeMode ? 'duration' : (repInputMode === 'standard' ? 'reps' : 'partialReps'), side: isUnilateral ? activeSide : undefined })} className="w-full text-center bg-transparent border-none text-2xl font-black focus:ring-0 p-0 text-inherit placeholder-white/20 min-w-0 truncate" style={{ fontFamily: 'ui-monospace, monospace' }}>
                                {isTimeMode ? safeInputs.duration || '0' : (repInputMode === 'standard' ? safeInputs.reps : safeInputs.partialReps) || '0'}
                             </button>
                             {debt !== 0 && !isTimeMode && repInputMode === 'standard' && <span className={`absolute top-1 right-2 text-[10px] font-black ${debt > 0 ? 'text-green-400' : 'text-red-400'}`}>{debt > 0 ? '+' : ''}{debt}</span>}
                             {debt !== 0 && isTimeMode && <span className={`absolute top-1 right-2 text-[10px] font-black ${debt > 0 ? 'text-green-400' : 'text-red-400'}`}>{debt > 0 ? '+' : ''}{debt}s</span>}
                        </div>
                        <div className="grid grid-cols-2 border-t border-black/20 divide-x divide-black/20 bg-black/10">
                            <button onClick={() => handleAdjust(isTimeMode ? 'duration' : 'reps', isTimeMode ? -5 : -1)} className="py-3 hover:bg-white/10 active:bg-white/20 flex justify-center"><MinusIcon size={14}/></button>
                            <button onClick={() => handleAdjust(isTimeMode ? 'duration' : 'reps', isTimeMode ? 5 : 1)} className="py-3 hover:bg-white/10 active:bg-white/20 flex justify-center"><PlusIcon size={14}/></button>
                        </div>
                    </div>

                    <div className="flex flex-col justify-center items-center gap-1">
                         <SetTimerButton onSave={handleSetDurationSave} initialDuration={loggedDuration}/>
                         <span className="text-[9px] text-slate-600 font-mono">TUT</span>
                    </div>

                    <div className={`flex flex-col rounded-xl overflow-hidden border shadow-sm relative transition-colors duration-300 ${isWeightWarning ? 'border-red-500 shadow-[0_0_15px_rgba(239,68,68,0.2)] bg-red-900/10' : 'bg-slate-800 border-slate-700'}`}>
                        <button onClick={() => setIsBodyweight(!isBodyweight)} className={`text-[9px] uppercase font-bold tracking-widest py-1.5 text-center transition-colors ${isBodyweight ? 'bg-emerald-900/50 text-emerald-300' : 'bg-slate-700 text-slate-400 hover:text-white'}`}>{isBodyweight ? 'Peso Corporal' : `Carga (${settings.weightUnit})`}</button>
                         <div className="relative flex-1 py-1 flex items-center justify-center">
                            {isBodyweight ? (
                                <div className="flex items-center justify-center gap-1 text-emerald-400"><BodyIcon size={24} /><span className="text-xl font-bold">BW</span></div>
                            ) : (
                                <div className="relative w-full min-w-0">
                                     <button type="button" onClick={() => onOpenNumpad({ field: 'weight', side: isUnilateral ? activeSide : undefined })} className={`w-full text-center bg-transparent border-none text-2xl font-black focus:ring-0 p-0 min-w-0 truncate ${isWeightWarning ? 'text-red-400' : 'text-white'}`} style={{ fontFamily: 'ui-monospace, monospace' }}>
                                        {safeInputs.weight || '0'}
                                     </button>
                                     {isWeightWarning && <AlertTriangleIcon size={12} className="absolute top-1 right-2 text-red-500 animate-pulse" />}
                                </div>
                            )}
                        </div>
                         <div className="grid grid-cols-2 border-t border-slate-700/50 divide-x border-slate-700/50 bg-black/20">
                            <button onClick={() => handleAdjust('weight', -1)} disabled={isBodyweight} className="py-3 hover:bg-white/5 active:bg-white/10 text-slate-400 hover:text-white flex justify-center disabled:opacity-30"><MinusIcon size={14}/></button>
                            <button onClick={() => handleAdjust('weight', 1)} disabled={isBodyweight} className="py-3 hover:bg-white/5 active:bg-white/10 text-slate-400 hover:text-white flex justify-center disabled:opacity-30"><PlusIcon size={14}/></button>
                        </div>
                    </div>
                </div>

                {set.isAmrap ? (
                    <div className="flex justify-center items-center py-3 bg-[#3f3f3f] w-full">
                        <span className="text-xs font-medium uppercase tracking-wide flex items-center gap-2 text-white">
                            <FlameIcon size={14} />
                            {set.isCalibrator ? "AMRAP Calibrador" : "AMRAP Aislado"}
                        </span>
                    </div>
                ) : (
                    <div className="flex justify-center items-center gap-2">
                        <button onClick={() => handlePerformanceModeChange('target')} className={`flex-1 py-2 text-[10px] font-medium uppercase transition-all ${safeInputs.performanceMode === 'target' ? 'bg-white text-[#1a1a1a]' : 'bg-[#1a1a1a] text-[#a3a3a3] hover:text-white'}`}>
                            {(set.intensityMode === 'rir' || settings.intensityMetric === 'rir') ? 'RIR' : 'RPE'}
                        </button>
                        <button onClick={() => handlePerformanceModeChange('failure')} className={`flex-1 py-2 text-[10px] font-medium uppercase transition-all flex items-center justify-center gap-1 ${safeInputs.performanceMode === 'failure' ? 'bg-white text-[#1a1a1a]' : 'bg-[#1a1a1a] text-[#a3a3a3] hover:text-white'}`}>
                            <FlameIcon size={10} /> Fallo
                        </button>
                        <button onClick={() => handlePerformanceModeChange('failed')} className={`flex-1 py-2 text-[10px] font-medium uppercase transition-all flex items-center justify-center gap-1 ${safeInputs.performanceMode === 'failed' ? 'bg-[#525252] text-white' : 'bg-[#1a1a1a] text-[#a3a3a3] hover:text-white'}`}>
                            <AlertTriangleIcon size={10} /> Fallido
                        </button>
                    </div>
                )}

                {safeInputs.performanceMode === 'target' && (
                    <div className="flex justify-center animate-fade-in relative">
                         {(set.intensityMode === 'rir' || settings.intensityMetric === 'rir') ? (
                             <div className={`flex items-center rounded-lg p-1.5 border w-24 justify-between transition-colors shrink-0 ${intensityContainerClass}`}>
                                 <span className="text-slate-500 font-bold text-[10px] uppercase px-1.5">RIR</span>
                                 <button type="button" onClick={() => onOpenNumpad({ field: 'rir', side: isUnilateral ? activeSide : undefined })} className="w-10 bg-transparent border-none text-center font-bold text-white focus:ring-0 p-0 text-base font-mono">{safeInputs.rir || '—'}</button>
                             </div>
                         ) : (
                             <div className={`flex items-center rounded-lg p-1.5 border w-24 justify-between transition-colors shrink-0 ${intensityContainerClass}`}>
                                 <span className="text-slate-500 font-bold text-[10px] uppercase px-1.5">RPE</span>
                                 <button type="button" onClick={() => onOpenNumpad({ field: 'rpe', side: isUnilateral ? activeSide : undefined })} className="w-10 bg-transparent border-none text-center font-bold text-white focus:ring-0 p-0 text-base font-mono">{safeInputs.rpe || '—'}</button>
                             </div>
                         )}
                    </div>
                )}

                {/* Dropset / Rest-Pause: enlaces de texto */}
                <div className="mx-2 mt-2 flex gap-4 text-[10px] font-bold">
                    <button type="button" onClick={() => onInputChange('dropSets', [...(safeInputs.dropSets || []), { weight: 0, reps: 0 }], isUnilateral ? activeSide : undefined)} className="text-[#a3a3a3] hover:text-white">+ Dropset {(safeInputs.dropSets?.length || 0) > 0 && <span className="text-[#737373]">({(safeInputs.dropSets?.length || 0)})</span>}</button>
                    <button type="button" onClick={() => onInputChange('restPauses', [...(safeInputs.restPauses || []), { restTime: 15, reps: 0 }], isUnilateral ? activeSide : undefined)} className="text-[#a3a3a3] hover:text-white">+ Rest-Pause {(safeInputs.restPauses?.length || 0) > 0 && <span className="text-[#737373]">({(safeInputs.restPauses?.length || 0)})</span>}</button>
                </div>
                {(safeInputs.dropSets?.length || 0) > 0 || (safeInputs.restPauses?.length || 0) > 0 ? (
                    <div className="mx-2 mt-2 p-3 bg-slate-900/50 border border-white/5 rounded-xl space-y-3 animate-fade-in">
                        {(safeInputs.dropSets || []).map((ds, i) => (
                            <div key={`ds-${i}`} className="flex gap-1.5 items-center">
                                <span className="text-[9px] font-mono text-slate-400 w-12 shrink-0">Dropset</span>
                                <button type="button" onClick={() => onOpenNumpad({ field: 'dropSetWeight', dropSetIndex: i, side: isUnilateral ? activeSide : undefined })} className="w-14 bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-xs text-white font-mono text-center shrink-0">{ds.weight === 0 ? 'Peso' : ds.weight}</button>
                                <button type="button" onClick={() => onOpenNumpad({ field: 'dropSetReps', dropSetIndex: i, side: isUnilateral ? activeSide : undefined })} className="w-12 bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-xs text-white font-mono text-center shrink-0">{ds.reps === 0 ? 'Reps' : ds.reps}</button>
                                <button onClick={() => onInputChange('dropSets', (safeInputs.dropSets || []).filter((_, j) => j !== i), isUnilateral ? activeSide : undefined)} className="p-1.5 rounded bg-red-900/50 text-red-400 hover:bg-red-800/50 shrink-0"><MinusIcon size={12}/></button>
                            </div>
                        ))}
                        {(safeInputs.restPauses || []).map((rp, i) => (
                            <div key={`rp-${i}`} className="flex gap-1.5 items-center">
                                <span className="text-[9px] font-mono text-slate-400 w-12 shrink-0">Rest-Pause</span>
                                <button type="button" onClick={() => onOpenNumpad({ field: 'restPauseRestTime', restPauseIndex: i, side: isUnilateral ? activeSide : undefined })} className="w-12 bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-xs text-white font-mono text-center shrink-0">{rp.restTime === 0 ? 's' : rp.restTime}</button>
                                <button type="button" onClick={() => onOpenNumpad({ field: 'restPauseReps', restPauseIndex: i, side: isUnilateral ? activeSide : undefined })} className="w-12 bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-xs text-white font-mono text-center shrink-0">{rp.reps === 0 ? 'Reps' : rp.reps}</button>
                                <button onClick={() => onInputChange('restPauses', (safeInputs.restPauses || []).filter((_, j) => j !== i), isUnilateral ? activeSide : undefined)} className="p-1.5 rounded bg-red-900/50 text-red-400 hover:bg-red-800/50 shrink-0"><MinusIcon size={12}/></button>
                            </div>
                        ))}
                    </div>
                ) : null}
            </div>

            {exercise.isCompetitionLift && (
                <div className="mx-2 mt-3 bg-yellow-950/30 border border-yellow-500/30 p-4 rounded-xl animate-fade-in">
                    <div className="flex items-center justify-between mb-3">
                        <span className="text-[9px] font-black text-yellow-500 uppercase tracking-widest">Luces de Jueceo</span>
                        {safeInputs.attemptResult === 'good' && <span className="text-[9px] font-black text-white bg-green-500/20 px-2 py-0.5 rounded">VÁLIDO</span>}
                        {safeInputs.attemptResult === 'no-lift' && <span className="text-[9px] font-black text-white bg-red-500/20 px-2 py-0.5 rounded">NO LIFT</span>}
                    </div>
                    <div className="flex justify-center gap-4">
                        {[0, 1, 2].map((judgeIdx) => {
                            const lights = safeInputs.judgingLights || [null, null, null];
                            const currentLight = lights[judgeIdx];
                            const lightColor = currentLight === true ? 'bg-white shadow-[0_0_12px_white] scale-110' : currentLight === false ? 'bg-red-500 shadow-[0_0_12px_red] scale-110' : 'bg-zinc-800 border-2 border-zinc-600';
                            return (
                                <button key={judgeIdx} type="button" onClick={() => {
                                    const newLights = [...(safeInputs.judgingLights || [null, null, null])] as [boolean|null, boolean|null, boolean|null];
                                    newLights[judgeIdx] = newLights[judgeIdx] === null ? true : newLights[judgeIdx] === true ? false : null;
                                    onInputChange('judgingLights' as any, newLights, isUnilateral ? activeSide : undefined);
                                    const whites = newLights.filter(l => l === true).length;
                                    const reds = newLights.filter(l => l === false).length;
                                    const result = (whites + reds === 3) ? (whites >= 2 ? 'good' : 'no-lift') : 'pending';
                                    onInputChange('attemptResult' as any, result, isUnilateral ? activeSide : undefined);
                                }} className={`w-12 h-12 rounded-full transition-all duration-300 ${lightColor}`}></button>
                            );
                        })}
                    </div>
                    <p className="text-[8px] text-zinc-500 text-center mt-2">Toca: Gris → Blanco → Rojo → Gris</p>
                </div>
            )}

            {showFailedModal && (
                <TacticalModal isOpen={showFailedModal} onClose={() => setShowFailedModal(false)} title="Serie Fallida">
                    <div className="space-y-4 p-2">
                        <p className="text-sm text-slate-300 text-center mb-4">No se pudo completar ninguna repetición. ¿Cuál fue la causa?</p>
                        <div className="space-y-2">
                             <Button onClick={() => handleFailedSet('Dolor / Lesión')} variant="danger" className="w-full">Dolor / Molestia</Button>
                             <Button onClick={() => handleFailedSet('Peso Excesivo')} variant="secondary" className="w-full text-cyber-danger">Carga Excesiva</Button>
                             <Button onClick={() => handleFailedSet('Fallo Técnico')} variant="secondary" className="w-full">Fallo Técnico</Button>
                        </div>
                    </div>
                </TacticalModal>
            )}

            <div className="mt-4 px-2">
                <button onClick={handleLogAttempt} className={`w-full py-4 text-sm font-medium uppercase tracking-wide transition-all active:scale-[0.98] bg-white text-[#1a1a1a]`}>
                    {isLogged ? 'Actualizar serie' : set.isAmrap ? 'Guardar AMRAP' : 'Guardar serie'}
                </button>
            </div>
        </div>
    );
});

interface WorkoutSessionProps {
    session: Session;
    program: Program;
    programId: string;
    settings: Settings;
    history: WorkoutLog[];
    onFinish: (completedExercises: CompletedExercise[], duration: number, notes?: string, discomforts?: string[], fatigue?: number, clarity?: number, logDate?: string, photoUri?: string, planDeviations?: PlanDeviation[], focus?: number, pump?: number, environmentTags?: string[], sessionDifficulty?: number, planAdherenceTags?: string[]) => void;
    onCancel: () => void;
    onPause?: () => void;
    onUpdateExercise1RM: (exerciseDbId: string | undefined, exerciseName: string, weight: number, reps: number, testDate?: string, machineBrand?: string) => void;
    isFinishModalOpen: boolean;
    setIsFinishModalOpen: (isOpen: boolean) => void;
    isTimeSaverModalOpen: boolean;
    setIsTimeSaverModalOpen: (isOpen: boolean) => void;
    onUpdateExerciseInProgram: (programId: string, sessionId: string, exerciseId: string, updatedExercise: Exercise) => void;
    onUpdateSessionInProgram: (session: Session, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    isTimersModalOpen: boolean;
    setIsTimersModalOpen: (isOpen: boolean) => void;
    exerciseList: ExerciseMuscleInfo[];
}

export const WorkoutSession: React.FC<WorkoutSessionProps> = ({ session, program, programId, settings, history, onFinish, onCancel, onPause, onUpdateExercise1RM, isFinishModalOpen, setIsFinishModalOpen, exerciseList, onUpdateSessionInProgram, isTimeSaverModalOpen, setIsTimeSaverModalOpen, onUpdateExerciseInProgram: updateExInProg, isTimersModalOpen, setIsTimersModalOpen }) => {
    const { ongoingWorkout, isOnline, muscleHierarchy, restTimer } = useAppState();
    const dispatch = useAppDispatch();
    useKeyboardOverlayMode(true);
    const { setOngoingWorkout, handleStartRest, handleSkipRestTimer, addToast, addOrUpdateCustomExercise, openCustomExerciseEditor } = dispatch;

    useEffect(() => {
        configureAudioSession();
        preloadSounds();
    }, []);

    useEffect(() => {
        if (Capacitor.isNativePlatform()) {
            setupNotificationChannels().then(() => requestPermissions());
        }
    }, []);

    useEffect(() => {
        if (!Capacitor.isNativePlatform()) return;
        const lockLandscape = async () => {
            try {
                const { ScreenOrientation } = await import('@capacitor/screen-orientation');
                await ScreenOrientation.lock({ orientation: 'portrait' });
            } catch (e) {
                console.warn('[WorkoutSession] ScreenOrientation lock failed:', e);
            }
        };
        lockLandscape();
        return () => {
            import('@capacitor/screen-orientation').then(({ ScreenOrientation }) =>
                ScreenOrientation.unlock().catch(() => {})
            );
        };
    }, []);
    
    const [isSkippingRest, setIsSkippingRest] = useState(false); // CANDADO DE SEGURIDAD
    const [currentSession, setCurrentSession] = useState<Session>(ongoingWorkout?.session || session);
    const [startTime] = useState(ongoingWorkout?.startTime || Date.now());
    const [duration, setDuration] = useState(0);
    const [activeMode] = useState<'A' | 'B' | 'C' | 'D'>(ongoingWorkout?.activeMode || 'A');
    const [sessionAdjusted1RMs, setSessionAdjusted1RMs] = useState<Record<string, number>>({});
    const [collapsedParts, setCollapsedParts] = useState<Record<string, boolean>>({});
    const [exerciseHeartRates, setExerciseHeartRates] = useState<Record<string, { initial?: number, peak?: number }>>(ongoingWorkout?.exerciseHeartRates || {});
    const [consolidatedWeights, setConsolidatedWeights] = useState<Record<string, number>>((ongoingWorkout?.consolidatedWeights as any) || {});
    const [exerciseFeedback, setExerciseFeedback] = useState<Record<string, any>>(ongoingWorkout?.exerciseFeedback || {});
    const [selectedTags, setSelectedTags] = useState<Record<string, string>>((ongoingWorkout?.selectedBrands as any) || {});
    const [setCardAnimations, setSetCardAnimations] = useState<Record<string, string | null>>({});
    
    const [starGoalProgress, setStarGoalProgress] = useState<{ exerciseId: string; current: number; goal: number; unit: string } | null>(null);
    const [focusExerciseId, setFocusExerciseId] = useState<string | null>(null);
    const [sessionNotes, setSessionNotes] = useState<string>(ongoingWorkout?.sessionNotes || '');
    const [showNotesDrawer, setShowNotesDrawer] = useState(false);
    const [numpadState, setNumpadState] = useState<{ setId: string; field: 'weight' | 'reps' | 'partialReps' | 'duration' | 'rpe' | 'rir' | 'dropSetWeight' | 'dropSetReps' | 'restPauseRestTime' | 'restPauseReps'; exerciseId: string; dropSetIndex?: number; restPauseIndex?: number; side?: 'left' | 'right' } | null>(null);
    const [setTypeOverrides, setSetTypeOverrides] = useState<Record<string, 'W' | 'T' | 'F' | 'D'>>((ongoingWorkout?.setTypeOverrides as any) || {});
    const isFinishingRef = useRef(false);

    const cycleSetType = useCallback((setId: string, currentSet: ExerciseSet) => {
        const order: ('W' | 'T' | 'F' | 'D')[] = ['W', 'T', 'F', 'D'];
        const current = setTypeOverrides[setId] ?? getSetTypeLabel(currentSet);
        const next = order[(order.indexOf(current) + 1) % 4];
        setSetTypeOverrides(prev => ({ ...prev, [setId]: next }));
    }, [setTypeOverrides]);

    const setsContainerRefs = useRef<Record<string, HTMLDivElement | null>>({});

    const sessionForMode = useMemo(() => {
        if (ongoingWorkout?.topSetAmrapState?.status !== 'completed' && currentSession.exercises[0]?.isCalibratorAmrap) return { ...currentSession, exercises: [currentSession.exercises[0]] };
        const modeKey = `session${activeMode}` as 'sessionB' | 'sessionC' | 'sessionD';
        return (activeMode === 'A' || !currentSession[modeKey]) ? currentSession : (currentSession as any)[modeKey];
    }, [activeMode, currentSession, ongoingWorkout?.topSetAmrapState?.status]);
    
    const renderExercises = useMemo(() => {
        if (sessionForMode.parts && sessionForMode.parts.length > 0) return sessionForMode.parts;
        return [{ id: 'default', name: 'Sesión Principal', exercises: sessionForMode.exercises || [] }];
    }, [sessionForMode]);

    const allExercises = useMemo<Exercise[]>(() => {
        if (sessionForMode.parts && sessionForMode.parts.length > 0) {
            return sessionForMode.parts.flatMap((p: any) => (p.exercises || []).map((e: any) => e as Exercise));
        }
        return (sessionForMode.exercises as Exercise[]) || [];
    }, [sessionForMode]);

    const carouselItems = useMemo<CarouselItemType[]>(() => {
        const items: CarouselItemType[] = [];
        (renderExercises as { id: string; name?: string; exercises?: Exercise[]; color?: string }[]).forEach((part: any) => {
            const color = part.color || '#64748b';
            const exercises = part.exercises || [];
            let i = 0;
            while (i < exercises.length) {
                const ex = exercises[i] as Exercise;
                if (ex.supersetId) {
                    const group: Exercise[] = [ex];
                    while (i + 1 < exercises.length && (exercises[i + 1] as Exercise).supersetId === ex.supersetId) {
                        i++;
                        group.push(exercises[i] as Exercise);
                    }
                    items.push({ type: 'exercise', exercises: group, color, firstExerciseId: group[0].id });
                } else {
                    items.push({ type: 'exercise', exercises: [ex], color, firstExerciseId: ex.id });
                }
                i++;
            }
        });
        items.push({ type: 'finish' });
        return items;
    }, [renderExercises]);

    const handleCarouselReorder = useCallback((newItems: CarouselItemType[]) => {
        const exerciseItems = newItems.filter((it): it is CarouselItem => it.type === 'exercise');
        const reorderedExercises = exerciseItems.flatMap(it => it.exercises);
        if (reorderedExercises.length === 0) return;
        const modeKey = activeMode === 'A' ? null : (`session${activeMode}` as 'sessionB' | 'sessionC' | 'sessionD');
        const applyReorder = (session: Session): Session => {
            const updated = { ...session };
            if (session.parts && session.parts.length > 0) {
                updated.parts = [{ ...session.parts[0], exercises: reorderedExercises }];
            } else {
                updated.exercises = reorderedExercises;
            }
            return updated;
        };
        setCurrentSession(prev => {
            if (modeKey && (prev as any)[modeKey]) {
                return { ...prev, [modeKey]: applyReorder((prev as any)[modeKey]) };
            }
            return applyReorder(prev);
        });
        setOngoingWorkout(prev => {
            if (!prev) return null;
            const sessionToUpdate = modeKey && (prev.session as any)[modeKey] ? (prev.session as any)[modeKey] : prev.session;
            const updatedSession = modeKey && (prev.session as any)[modeKey]
                ? { ...prev.session, [modeKey]: applyReorder((prev.session as any)[modeKey]) }
                : applyReorder(prev.session);
            return { ...prev, session: updatedSession };
        });
    }, [activeMode]);

    const [skippedExerciseIds, setSkippedExerciseIds] = useState<Set<string>>(new Set());
    const [finishContextOpen, setFinishContextOpen] = useState(false);
    const [contextMenuItem, setContextMenuItem] = useState<{ item: CarouselItem } | null>(null);
    const [replaceModalExercise, setReplaceModalExercise] = useState<Exercise | null>(null);
    const [finishCardExpanded, setFinishCardExpanded] = useState(false);
    const [activeExerciseId, setActiveExerciseId] = useState<string | null>((ongoingWorkout?.activeExerciseId as any) || allExercises[0]?.id || null);

    const displayParts = useMemo(() => {
        const skipped = skippedExerciseIds;
        if (activeExerciseId) {
            const item = carouselItems.find((it): it is CarouselItem => it.type === 'exercise' && it.exercises.some(e => e.id === activeExerciseId));
            if (!item || item.type !== 'exercise') return renderExercises;
            const filtered = item.exercises.filter(e => !skipped.has(e.id));
            if (filtered.length === 0) return [{ id: 'omitted', name: 'Omitido', exercises: [] }];
            return [{ id: 'focused', name: '', exercises: filtered }];
        }
        return renderExercises;
    }, [activeExerciseId, carouselItems, renderExercises, skippedExerciseIds]);

    const [activeSetId, setActiveSetId] = useState<string | null>(null);
    const [setInputs, setSetInputs] = useState<Record<string, SetInputState | UnilateralSetInputs>>((ongoingWorkout?.unilateralSetInputs as any) || {}); 

    useEffect(() => {
         if (!activeSetId && allExercises.length > 0) {
             const firstEx = allExercises[0];
             if (firstEx && firstEx.sets && firstEx.sets.length > 0) {
                 setActiveSetId(firstEx.sets[0].id);
             }
         }
    }, [allExercises, activeSetId]);

    const handleSetInputChange = useCallback((setId: string, field: keyof SetInputState, value: any, side?: 'left' | 'right') => {
        setSetInputs(prev => {
            const currentEntry = prev[setId];
            if (side && currentEntry && 'left' in currentEntry) return { ...prev, [setId]: { ...currentEntry, [side]: { ...(currentEntry as UnilateralSetInputs)[side], [field]: value } } };
            else if (!side && currentEntry && !('left' in currentEntry)) return { ...prev, [setId]: { ...(currentEntry as SetInputState), [field]: value } };
            return prev;
        });
    }, []);

    const [completedSets, setCompletedSets] = useState<Record<string, { left: OngoingSetData | null, right: OngoingSetData | null }>>((ongoingWorkout?.completedSets as any) || {});
    const [historyModalExercise, setHistoryModalExercise] = useState<Exercise | null>(null);

    // --- KPKN LIVE TELEMETRY ENGINE ---
    const liveBatteryDrain = useMemo(() => {
        const tanks = calculatePersonalizedBatteryTanks(settings);
        let cnsPct = 0, muscPct = 0, spinalPct = 0;
        const muscleVolumeMap: Record<string, number> = {};

        allExercises.forEach(ex => {
            const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.name);
            const rawMuscle = info?.involvedMuscles.find(m => m.role === 'primary')?.muscle || 'General';
            const primaryMuscle = normalizeMuscleGroup ? normalizeMuscleGroup(rawMuscle) : rawMuscle;
            
            let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

            (ex.sets || []).forEach(set => {
                const isSetCompleted = !!completedSets[String(set.id)];
                if (isSetCompleted && (set as any).type !== 'warmup') {
                    accumulatedSets += 1;
                    
                    const completedDataRaw = completedSets[String(set.id)] as { left: OngoingSetData | null, right: OngoingSetData | null };
                    const completedData = ex.isUnilateral ? (completedDataRaw?.left || completedDataRaw?.right) : completedDataRaw?.left;
                    
                    // Mezclamos la data de la serie base con lo que el usuario realmente hizo.
                    // Incluimos partialReps, dropSets, restPauses para que AUGE calcule correctamente
                    // el drenaje de batería (parciales = más fatiga/volumen basura, menos estímulo).
                    const setToCalculate = completedData ? {
                        ...set,
                        completedReps: completedData.reps,
                        completedRPE: completedData.rpe,
                        weight: completedData.weight,
                        isFailure: completedData.isFailure,
                        intensityMode: completedData.performanceMode === 'failure' ? 'failure' : set.intensityMode,
                        partialReps: completedData.partialReps,
                        dropSets: completedData.dropSets,
                        restPauses: completedData.restPauses,
                        performanceMode: completedData.performanceMode,
                        isAmrap: completedData.isAmrap
                    } : set;

                    const drain = calculateSetBatteryDrain(setToCalculate, info, tanks, accumulatedSets, ex.restTime || 90);
                    cnsPct += drain.cnsDrainPct;
                    muscPct += drain.muscularDrainPct;
                    spinalPct += drain.spinalDrainPct;
                }
            });
            muscleVolumeMap[primaryMuscle] = accumulatedSets;
        });

        return {
            cns: Math.min(100, cnsPct),
            muscular: Math.min(100, muscPct),
            spinal: Math.min(100, spinalPct)
        };
    }, [allExercises, completedSets, settings, exerciseList]);

    const [adaptiveCache] = useState<AugeAdaptiveCache | null>(() => getCachedAdaptiveData());

    const activePartInfo = useMemo(() => {
        if (!activeExerciseId || !renderExercises || renderExercises.length === 0) return null;
        const part = renderExercises.find((p: any) => p.exercises?.some((e: any) => e.id === activeExerciseId));
        return part || null; 
    }, [activeExerciseId, renderExercises]);

    // Blindaje Maestro: Previene colapsos si un ejercicio desaparece de la lista en tiempo real
    useEffect(() => {
        if (renderExercises && renderExercises.length > 0 && activeExerciseId) {
            const exists = renderExercises.some((p: any) => p.exercises?.some((e: any) => e.id === activeExerciseId));
            if (!exists) {
                const firstAvailableId = renderExercises[0]?.exercises?.[0]?.id || null;
                setActiveExerciseId(firstAvailableId);
            }
        }
    }, [renderExercises, activeExerciseId]);

    useEffect(() => {
        if (!activeExerciseId) return;
        const currentExercise = allExercises.find(e => e.id === activeExerciseId);
        if (currentExercise) {
            setSetInputs(prev => {
                const newInputs = { ...prev };
                let hasChanges = false;
                const completedSetsForExercise: { reps?: number; weight: number; machineBrand?: string }[] = currentExercise.sets.map(s => {
                    const dataRaw = completedSets[String(s.id)];
                    if (!dataRaw) return { weight: 0 };
                    const data = dataRaw as { left: OngoingSetData | null; right: OngoingSetData | null };
                    const primary = currentExercise.isUnilateral ? (data.left || data.right) : data.left;
                    if (!primary || (primary.weight === 0 && !primary.reps)) return { weight: 0 };
                    return { weight: primary.weight || 0, reps: primary.reps, machineBrand: primary.machineBrand };
                });

                currentExercise.sets.forEach((set, index) => {
                     if (!newInputs[set.id]) {
                         hasChanges = true;
                         const initialRPE = set.targetRPE ? set.targetRPE.toString() : '';
                         const initialRIR = set.targetRIR !== undefined ? set.targetRIR.toString() : '';
                         const isPlannedFailure = set.intensityMode === 'failure' || set.isAmrap;

                         const defaultInput: SetInputState = { 
                             reps: set.targetReps?.toString() || '', 
                             weight: '', 
                             rpe: initialRPE, 
                             rir: initialRIR, 
                             isFailure: isPlannedFailure, 
                             performanceMode: isPlannedFailure ? 'failure' : 'target',
                             isIneffective: false, isPartial: false, 
                             duration: set.targetDuration?.toString() || '', notes: '', advancedTechnique: '',
                             dropSets: [], restPauses: [],
                             partialReps: '', technicalQuality: '8', discomfortLevel: '0', discomfortNotes: '', tempo: ''
                         };
                         const exerciseInfo = exerciseList.find(e => e.id === currentExercise.exerciseDbId);
                         const suggestion = getWeightSuggestionForSet(currentExercise, exerciseInfo, index, completedSetsForExercise, settings, history, selectedTags[currentExercise.id], sessionAdjusted1RMs[currentExercise.id]);
                         if (suggestion) defaultInput.weight = suggestion.toString();
                         newInputs[set.id] = currentExercise.isUnilateral ? { left: { ...defaultInput }, right: { ...defaultInput } } : defaultInput;
                     }
                });
                return hasChanges ? newInputs : prev;
            });
             if (!consolidatedWeights[currentExercise.id]) {
                 const exerciseInfo = exerciseList.find(e => e.id === currentExercise.exerciseDbId);
                 const suggestion = getWeightSuggestionForSet(currentExercise, exerciseInfo, 0, [], settings, history, selectedTags[currentExercise.id], sessionAdjusted1RMs[currentExercise.id]);
                 if (suggestion) setConsolidatedWeights(prev => ({...prev, [currentExercise.id]: suggestion}));
             }
        }
    }, [activeExerciseId, allExercises, completedSets, settings, history, selectedTags, exerciseList, sessionAdjusted1RMs, consolidatedWeights]);
    
    useEffect(() => {
        const timer = setInterval(() => { setDuration(Math.floor((Date.now() - startTime) / 1000)); }, 1000);
        return () => clearInterval(timer);
    }, [startTime]);

    useEffect(() => {
        // PREVENT STATE CLOBBERING DURING FINISH
        if (!isFinishingRef.current) {
            setOngoingWorkout(prev => {
                if (!prev) return prev;
                return {
                    ...prev,
                    completedSets: completedSets as any,
                    exerciseFeedback,
                    unilateralSetInputs: setInputs as any,
                    selectedBrands: selectedTags,
                    activeExerciseId,
                    activeSetId,
                    exerciseHeartRates,
                    consolidatedWeights,
                    setTypeOverrides: setTypeOverrides as any
                };
            });
        }
    }, [completedSets, exerciseFeedback, setInputs, selectedTags, activeExerciseId, activeSetId, setOngoingWorkout, exerciseHeartRates, consolidatedWeights, setTypeOverrides]);


    const scrollToId = useCallback((exerciseId: string, elementId: string) => {
        setTimeout(() => {
            const container = setsContainerRefs.current[exerciseId];
            const el = document.getElementById(elementId);
            if (container && el) {
                const targetScroll = el.offsetLeft - (container.clientWidth / 2) + (el.clientWidth / 2);
                container.scrollTo({ left: targetScroll, behavior: 'smooth' });
            }
        }, 100);
    }, []);

    const moveToNextSet = useCallback(() => {
        const currentEx = allExercises.find(e => e.id === activeExerciseId);
        if (!currentEx) return;
        
        // If current active is a warmup
        if (activeSetId?.startsWith('warmup-')) {
             const firstSetId = currentEx.sets[0]?.id;
             if (firstSetId) {
                setActiveSetId(firstSetId);
                scrollToId(currentEx.id, `set-card-${firstSetId}`);
             } else {
                 // Feedback if no sets?
                setActiveSetId(`feedback-${currentEx.id}`);
                scrollToId(currentEx.id, `feedback-card-${currentEx.id}`);
             }
             return;
        }

        const currentSetIndex = currentEx.sets.findIndex(s => s.id === activeSetId);
        
        // If not the last set
        if (currentSetIndex !== -1 && currentSetIndex < currentEx.sets.length - 1) {
            const nextSetId = currentEx.sets[currentSetIndex + 1].id;
            setActiveSetId(nextSetId);
            scrollToId(currentEx.id, `set-card-${nextSetId}`);
        } else {
            // Last Set Completed -> Move to Feedback Card
            setActiveSetId(`feedback-${currentEx.id}`);
            scrollToId(currentEx.id, `feedback-card-${currentEx.id}`);
        }
    }, [activeExerciseId, activeSetId, allExercises, scrollToId]);

    const handleLogSet = useCallback((exercise: Exercise, set: ExerciseSet, isCalibrator: boolean = false) => {
        const inputData = setInputs[String(set.id)];
        if (!inputData) return;
        const isUnilateral = 'left' in inputData;
        const setIndex = exercise.sets.findIndex(s => s.id === set.id);
        let setDataToSave: { left: OngoingSetData | null, right: OngoingSetData | null } = { left: null, right: null };
        const processInput = (inp: SetInputState): OngoingSetData | null => {
            const weight = parseFloat(String(inp.weight));
            const repsVal = parseInt(inp.reps, 10);
            const isEmpty = (inp.weight === '' || isNaN(weight)) && (inp.reps === '' || !repsVal) && inp.performanceMode !== 'failed' && !inp.duration;
            if (isEmpty) {
                const ghost = getGhostForSet((exercise.exerciseDbId || exercise.id) as string, setIndex, history);
                if (ghost && (ghost.weight > 0 || ghost.reps > 0)) {
                    return { weight: ghost.weight, reps: ghost.reps, rpe: ghost.rpe ?? set.targetRPE, rir: undefined, isFailure: inp.isFailure || inp.performanceMode === 'failure' || set.isAmrap, isIneffective: inp.isIneffective || inp.performanceMode === 'failed', duration: undefined, machineBrand: selectedTags[exercise.id], isPartial: false, performanceMode: inp.performanceMode || 'target', partialReps: 0, dropSets: inp.dropSets, restPauses: inp.restPauses, discomfortNotes: inp.discomfortNotes, isAmrap: set.isAmrap };
                }
                return null;
            }
            return { weight: isNaN(weight) ? 0 : weight, reps: repsVal || undefined, rpe: inp.rpe ? parseFloat(inp.rpe) : undefined, rir: inp.rir ? parseInt(inp.rir, 10) : undefined, isFailure: inp.isFailure || inp.performanceMode === 'failure' || set.isAmrap, isIneffective: inp.isIneffective || inp.performanceMode === 'failed', duration: parseInt(inp.duration || '0', 10) || undefined, machineBrand: selectedTags[exercise.id], isPartial: inp.isPartial || (parseInt(inp.partialReps, 10) > 0), performanceMode: inp.performanceMode, partialReps: parseInt(inp.partialReps, 10) || 0, dropSets: inp.dropSets, restPauses: inp.restPauses, discomfortNotes: inp.discomfortNotes, isAmrap: set.isAmrap };
        };
        if (isUnilateral) { const ui = inputData as UnilateralSetInputs; setDataToSave.left = processInput(ui.left); setDataToSave.right = processInput(ui.right); }
        else setDataToSave.left = processInput(inputData as SetInputState);
        
        if (setDataToSave.left || setDataToSave.right) {
            const primaryData = isUnilateral ? (setDataToSave.left || setDataToSave.right)! : setDataToSave.left!;
            
            let adaptiveRestTime = exercise.restTime || 90;
            let addedRest = 0;

            const targetRPE = set.targetRPE || 8;
            const targetReps = (exercise.trainingMode === 'time') ? (set.targetDuration || 0) : (set.targetReps || 0);
            const actualReps = (exercise.trainingMode === 'time') ? (primaryData.duration || 0) : (primaryData.reps || 0);
            const exInfo = exerciseList.find(e => e.id === exercise.exerciseDbId || e.name === exercise.name);
            const auge = getDynamicAugeMetrics(exInfo, exercise.name);
            const fatigueFactor = Math.min(1.5, Math.max(0.4, (auge.cnc + auge.ssc * 0.5) / 3.5));

            const wasProgrammedAsFailure = set.intensityMode === 'failure' || set.isAmrap;
            const wasProgrammedNearFailure = (set.targetRPE ?? 8) >= 9;
            const hadProgrammedDropsets = (set.dropSets?.length ?? 0) > 0;
            const hadProgrammedRestPause = (set.restPauses?.length ?? 0) > 0;
            const userDidDropsets = (primaryData.dropSets?.length ?? 0) > 0;
            const userDidRestPause = (primaryData.restPauses?.length ?? 0) > 0;
            const userWentToFailure = primaryData.isFailure || primaryData.performanceMode === 'failure';

            if (primaryData.performanceMode === 'failed') {
                setSetCardAnimations(prev => ({...prev, [set.id]: 'failure'}));
                hapticNotification(NotificationType.Error);
            } else if (actualReps >= targetReps) {
                setSetCardAnimations(prev => ({...prev, [set.id]: set.isAmrap ? 'amrap' : 'success'}));
            } else {
                setSetCardAnimations(prev => ({...prev, [set.id]: null}));
            }

            const completedSetsForExercise = exercise.sets.slice(0, setIndex).map(s => {
                const d = completedSets[String(s.id)] as { left?: { weight?: number; reps?: number; machineBrand?: string }; right?: { weight?: number; reps?: number } } | undefined;
                if (!d) return { weight: 0 };
                const p = exercise.isUnilateral ? (d.left || d.right) : d.left;
                return p ? { weight: p.weight || 0, reps: p.reps, machineBrand: p.machineBrand } : { weight: 0 };
            });
            const suggestedWeight = getWeightSuggestionForSet(exercise, exInfo, setIndex, completedSetsForExercise, settings, history, selectedTags[exercise.id], sessionAdjusted1RMs[exercise.id]);
            const actualWeight = primaryData.weight ?? 0;
            const targetRepsNum = typeof targetReps === 'number' ? targetReps : (typeof targetReps === 'string' ? parseInt(String(targetReps), 10) : 0) || 0;
            const actualRepsNum = typeof actualReps === 'number' ? actualReps : (typeof actualReps === 'string' ? parseInt(String(actualReps), 10) : 0) || 0;
            const isSignificantlyHeavier = suggestedWeight != null && actualWeight > 0 && actualWeight > suggestedWeight * 1.05;
            const didHalfOrFewerReps = targetRepsNum > 0 && actualRepsNum < targetRepsNum * 0.5;
            const deviatedByWeight = isSignificantlyHeavier && didHalfOrFewerReps;
            const deviatedByDropsets = userDidDropsets && !hadProgrammedDropsets;
            const deviatedByRestPause = userDidRestPause && !hadProgrammedRestPause;
            const deviatedByFailure = userWentToFailure && !wasProgrammedAsFailure && !wasProgrammedNearFailure && (primaryData.rpe ?? 10) >= 10;

            if (primaryData.performanceMode === 'failed' && !wasProgrammedAsFailure) {
                addedRest = Math.round(30 * fatigueFactor);
                addedRest = Math.min(addedRest, 60);
                if (addedRest > 0) addToast("He extendido en " + addedRest + " segundos el descanso porque noté que tu serie fue más exigente de lo previsto.", "suggestion");
            } else if (wasProgrammedAsFailure && userWentToFailure) {
                addedRest = 0;
            } else if (deviatedByWeight || deviatedByDropsets || deviatedByRestPause || deviatedByFailure) {
                let baseAdded = 0;
                if (deviatedByWeight) baseAdded = Math.min(90, 20 + Math.round((actualWeight / (suggestedWeight || 1) - 1) * 60));
                else if (deviatedByDropsets || deviatedByRestPause) baseAdded = 15 * ((primaryData.dropSets?.length ?? 0) + (primaryData.restPauses?.length ?? 0));
                else if (deviatedByFailure) baseAdded = 25;
                addedRest = Math.round(baseAdded * fatigueFactor);
                addedRest = Math.min(addedRest, 120);
                if (addedRest > 0) addToast("He extendido en " + addedRest + " segundos el descanso porque noté que tu serie fue más exigente de lo previsto.", "suggestion");
            }

            adaptiveRestTime += addedRest;

            setCompletedSets(prev => ({ ...prev, [String(set.id)]: setDataToSave }));
            hapticImpact(ImpactStyle.Light);
            
            // --- AMRAP CALIBRADOR: Actualizar sessionAdjusted1RMs para ejercicios del mismo músculo ---
            if (isCalibrator && primaryData.weight && primaryData.reps && primaryData.reps > 0) {
                const newE1RM = calculateBrzycki1RM(primaryData.weight, primaryData.reps, true);
                if (newE1RM > 0) {
                    const conservative1RM = newE1RM * 0.95; // Factor conservador para siguientes ejercicios
                    const exInfo = exerciseList.find(e => e.id === exercise.exerciseDbId || e.name === exercise.name);
                    const rawMuscle = exInfo?.involvedMuscles?.find(m => m.role === 'primary')?.muscle || 'General';
                    const calibMuscle = normalizeMuscleGroup(rawMuscle);
                    
                    setSessionAdjusted1RMs(prev => {
                        const next = { ...prev };
                        next[exercise.id] = newE1RM; // Mismo ejercicio: 1RM completo para sets siguientes
                        const currentIdx = allExercises.findIndex(e => e.id === exercise.id);
                        allExercises.forEach((ex, idx) => {
                            if (idx <= currentIdx) return;
                            const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.name);
                            const exMuscle = normalizeMuscleGroup(info?.involvedMuscles?.find(m => m.role === 'primary')?.muscle || 'General');
                            if (exMuscle === calibMuscle) next[ex.id] = conservative1RM;
                        });
                        return next;
                    });
                    addToast(`AMRAP Calibrador: 1RMe ${newE1RM.toFixed(1)}${settings.weightUnit}. Cargas ajustadas para ejercicios de ${calibMuscle}.`, "suggestion");
                }
            }
            
            playSound('set-logged-sound');
            moveToNextSet(); 
            
            const hasProgrammedRest = (exercise.restTime ?? 0) > 0;
            if (adaptiveRestTime > 0 && !isUnilateral && hasProgrammedRest) handleStartRest(adaptiveRestTime, exercise.name);
        }
    }, [setInputs, selectedTags, handleStartRest, moveToNextSet, addToast, settings.algorithmSettings, settings.weightUnit, completedSets, exerciseFeedback, allExercises, program.mode, (program as any).goals, exerciseList, history, sessionAdjusted1RMs]);

    const handleGoalAnimationComplete = () => {
        setStarGoalProgress(null);
        setFocusExerciseId(null);
    };

    const handleFinishFeedback = (exerciseId: string, feedbackData: any) => {
        setExerciseFeedback(prev => ({...prev, [exerciseId]: feedbackData}));
        
        // --- STAR GOAL PROGRESS TRIGGER (ELEGANT & ACCURATE) ---
        const ex = allExercises.find(e => e.id === exerciseId);
        if (ex) {
            const isBasic = program.mode === 'powerlifting' && 
                (ex.name.toLowerCase().includes('sentadilla') || ex.name.toLowerCase().includes('bench press') || ex.name.toLowerCase().includes('peso muerto') || ex.name.toLowerCase().includes('press de banca'));
            
            const isStar = ex.isStarTarget || isBasic;
            let targetGoal = ex.goal1RM;
            
            if (!targetGoal && isBasic && (program as any).goals) {
                const n = ex.name.toLowerCase();
                if (n.includes('sentadilla') || n.includes('squat')) targetGoal = (program as any).goals.squat1RM;
                else if (n.includes('bench')) targetGoal = (program as any).goals.bench1RM;
                else if (n.includes('muerto') || n.includes('deadlift')) targetGoal = (program as any).goals.deadlift1RM;
            }

            if (isStar && targetGoal && targetGoal > 0) {
                 let sessionBestE1RM = 0;
                 ex.sets.forEach(s => {
                     const dataRaw = completedSets[String(s.id)];
                     if (dataRaw) {
                         const data = dataRaw as { left: OngoingSetData | null, right: OngoingSetData | null };
                         const primary = ex.isUnilateral ? (data.left || data.right) : data.left;
                         if (primary && primary.weight && primary.reps) {
                             const e1RM = calculateBrzycki1RM(primary.weight, primary.reps);
                             if (e1RM > sessionBestE1RM) sessionBestE1RM = e1RM;
                         }
                     }
                 });

                 let historicalBestE1RM = 0;
                 const exInfo = exerciseList.find(e => e.id === ex.exerciseDbId);
                 if (exInfo?.calculated1RM) historicalBestE1RM = exInfo.calculated1RM;
                 
                 history.forEach(log => {
                     const compEx = log.completedExercises.find(ce => ce.exerciseDbId === ex.exerciseDbId);
                     compEx?.sets.forEach(s => {
                         if(s.weight && s.completedReps) {
                             const e1rm = calculateBrzycki1RM(s.weight, s.completedReps);
                             if(e1rm > historicalBestE1RM) historicalBestE1RM = e1rm;
                         }
                     });
                 });

                 if (sessionBestE1RM > historicalBestE1RM && sessionBestE1RM > 0) {
                     setStarGoalProgress({
                         exerciseId: ex.id,
                         current: sessionBestE1RM,
                         goal: targetGoal,
                         unit: settings.weightUnit
                     });
                     setFocusExerciseId(ex.id);
                 }
            }
        }

        const nonSkipped = allExercises.filter(e => !skippedExerciseIds.has(e.id));
        const currentExIndex = nonSkipped.findIndex(e => e.id === exerciseId);
        const isLast = currentExIndex === -1 || currentExIndex === nonSkipped.length - 1;

        if (isLast) {
            setIsFinishModalOpen(true);
        } else {
            const nextEx = nonSkipped[currentExIndex + 1];
            setActiveExerciseId(nextEx.id);
            if (nextEx.warmupSets && nextEx.warmupSets.length > 0) {
                setActiveSetId(`warmup-${nextEx.id}`);
                scrollToId(nextEx.id, `warmup-card-${nextEx.id}`);
            } else {
                setActiveSetId(nextEx.sets[0].id);
                scrollToId(nextEx.id, `set-card-${nextEx.sets[0].id}`);
            }
        }
    };

    const handleWarmupComplete = (exercise: Exercise) => {
        if (exercise.sets && exercise.sets.length > 0) {
            const firstSetId = exercise.sets[0].id;
            setActiveSetId(firstSetId);
            scrollToId(exercise.id, `set-card-${firstSetId}`);
        }
    };

    const handleFinishSession = (notes?: string, discomforts?: string[], fatigueLevel?: number, mentalClarity?: number, durationInMinutes?: number, logDate?: string, photoUri?: string, planDeviations?: PlanDeviation[], focus?: number, pump?: number, environmentTags?: string[], sessionDifficulty?: number, planAdherenceTags?: string[], muscleBatteries?: Record<string, number>) => {
        isFinishingRef.current = true; // Block ongoing updates to prevent ghost session
        
        const exercisesToInclude = (allExercises as Exercise[]).filter(ex => !skippedExerciseIds.has(ex.id));
        const completedPayload: CompletedExercise[] = exercisesToInclude.map((ex: Exercise): CompletedExercise | null => {
            const sets: CompletedSet[] = [];
            ex.sets.forEach(set => {
                const setId = String(set.id);
                const dataRaw = completedSets[setId];
                if (!dataRaw) return;
                const data = dataRaw as { left: OngoingSetData | null, right: OngoingSetData | null };
                const mapSetData = (sData: OngoingSetData, side?: 'left' | 'right'): CompletedSet => ({ 
                    id: String(set.id) + (side ? `-${side}` : ''), 
                    targetReps: set.targetReps, targetRPE: set.targetRPE, weight: Number(sData.weight), 
                    completedReps: Number(sData.reps || 0), completedDuration: Number(sData.duration), 
                    completedRPE: sData.rpe, completedRIR: sData.rir, isFailure: sData.isFailure, 
                    isIneffective: sData.isIneffective, machineBrand: sData.machineBrand, 
                    partialReps: Number(sData.partialReps || 0), isPartial: sData.isPartial, 
                    side, performanceMode: sData.performanceMode, isAmrap: sData.isAmrap, 
                    dropSets: sData.dropSets?.map(ds => ({ weight: Number(ds.weight), reps: Number(ds.reps) })), 
                    restPauses: sData.restPauses?.map(rp => ({ restTime: Number(rp.restTime), reps: Number(rp.reps) })) 
                });
                if (ex.isUnilateral) { 
                    if (data.left) sets.push(mapSetData(data.left, 'left')); 
                    if (data.right) sets.push(mapSetData(data.right, 'right')); 
                }
                else if (data.left) sets.push(mapSetData(data.left));
            });
            if (sets.length === 0) return null;
            return { 
                exerciseId: ex.id, exerciseDbId: ex.exerciseDbId, exerciseName: ex.name, sets, 
                initialHeartRate: exerciseHeartRates[ex.id]?.initial, peakHeartRate: exerciseHeartRates[ex.id]?.peak, 
                ...((exerciseFeedback[ex.id] || {}) as any) 
            };
        }).filter((e): e is CompletedExercise => e !== null);
        onFinish(completedPayload, durationInMinutes ? durationInMinutes * 60 : duration, notes, discomforts, fatigueLevel, mentalClarity, logDate, undefined, planDeviations, focus, pump, environmentTags, sessionDifficulty, planAdherenceTags, muscleBatteries);
    };

    const handleHeaderClick = (exId: string) => {
        hapticImpact(ImpactStyle.Light);
        setActiveExerciseId(exId);
    };

    return (
        <div className="tab-bar-safe-area flex flex-col min-h-[calc(100vh-1rem)] bg-[#1a1a1a]">
             <FinishWorkoutModal isOpen={isFinishModalOpen} onClose={() => setIsFinishModalOpen(false)} onFinish={handleFinishSession} initialDurationInSeconds={duration} initialNotes={sessionNotes} initialDiscomforts={[...new Set(Object.values(exerciseFeedback).flatMap((f: any) => f.discomforts || []))]} initialBatteries={(() => { const arr = Object.values(exerciseFeedback).map((f: any) => f.perceivedFatigue).filter((v): v is number => typeof v === 'number'); if (arr.length === 0) return undefined; const avg = arr.reduce((a, b) => a + b, 0) / arr.length; return { general: Math.round(avg * 10) }; })()} fullPage allExercises={allExercises} completedSets={completedSets} exerciseList={exerciseList} />
            {activeSetId?.startsWith('warmup-') && (() => {
                const exId = activeSetId.replace('warmup-', '');
                const ex = allExercises.find((e: Exercise) => e.id === exId);
                return ex ? (
                    <WarmupDrawer
                        isOpen={true}
                        onClose={() => { const firstSetId = ex.sets[0]?.id; if (firstSetId) { setActiveSetId(firstSetId); scrollToId(ex.id, `set-card-${firstSetId}`); } }}
                        exercise={ex}
                        baseWeight={consolidatedWeights[ex.id] || 0}
                        onBaseWeightChange={(w) => setConsolidatedWeights(prev => ({ ...prev, [ex.id]: w }))}
                        settings={settings}
                        isConsolidated={!findPrForExercise(exerciseList.find(e => e.id === ex.exerciseDbId) || {} as any, history, settings, selectedTags[ex.id])}
                        onComplete={() => handleWarmupComplete(ex)}
                    />
                ) : null;
            })()}
            {activeSetId?.startsWith('feedback-') && (() => {
                const exId = activeSetId.replace('feedback-', '');
                const ex = allExercises.find((e: Exercise) => e.id === exId);
                return ex ? (
                    <PostExerciseDrawer
                        isOpen={true}
                        onClose={() => handleFinishFeedback(ex.id, { technicalQuality: 8, discomforts: [], perceivedFatigue: 5 })}
                        exerciseName={ex.name}
                        onSave={(feedback) => handleFinishFeedback(ex.id, feedback)}
                    />
                ) : null;
            })()}
            {historyModalExercise && <ExerciseHistoryModal exercise={historyModalExercise} programId={programId} history={history} settings={settings} onClose={() => setHistoryModalExercise(null)} />}

            <AdvancedExercisePickerModal
                isOpen={!!replaceModalExercise}
                onClose={() => setReplaceModalExercise(null)}
                exerciseList={exerciseList}
                initialSearch={replaceModalExercise?.name}
                onSelect={(sugg) => {
                    if (!replaceModalExercise) return;
                    const newExercise: Exercise = {
                        ...replaceModalExercise,
                        id: crypto.randomUUID(),
                        name: sugg.name,
                        exerciseDbId: sugg.id,
                    };
                    const updatedSession: Session = { ...currentSession };
                    if (updatedSession.parts) {
                        updatedSession.parts = updatedSession.parts.map(p => ({
                            ...p,
                            exercises: p.exercises.map(ex => ex.id === replaceModalExercise.id ? newExercise : ex),
                        }));
                    } else if (updatedSession.exercises) {
                        updatedSession.exercises = updatedSession.exercises.map(ex => ex.id === replaceModalExercise.id ? newExercise : ex);
                    }
                    setCurrentSession(updatedSession);
                    setOngoingWorkout(prev => prev ? { ...prev, session: updatedSession } : prev);
                    addToast(`'${replaceModalExercise.name}' sustituido por '${sugg.name}' (solo hoy).`, 'suggestion');
                    setReplaceModalExercise(null);
                }}
                onCreateNew={() => {
                    if (replaceModalExercise) {
                        openCustomExerciseEditor({ preFilledName: replaceModalExercise.name || 'Nuevo ejercicio' });
                    }
                    setReplaceModalExercise(null);
                }}
            />

            <ExerciseCardContextMenu
                isOpen={!!contextMenuItem}
                onClose={() => setContextMenuItem(null)}
                onReplace={() => { if (contextMenuItem) { setReplaceModalExercise(contextMenuItem.item.exercises[0]); setContextMenuItem(null); } }}
                onSkip={() => {
                    if (contextMenuItem) {
                        const idsToSkip = contextMenuItem.item.exercises.map(e => e.id);
                        const newSkipped = new Set([...skippedExerciseIds, ...idsToSkip]);
                        setSkippedExerciseIds(newSkipped);
                        setContextMenuItem(null);
                        const idx = carouselItems.findIndex((it): it is CarouselItem => it.type === 'exercise' && it.firstExerciseId === contextMenuItem.item.firstExerciseId);
                        const nextItem = idx >= 0 ? carouselItems.slice(idx + 1).find((it): it is CarouselItem => it.type === 'exercise' && !it.exercises.some(e => newSkipped.has(e.id))) : null;
                        if (nextItem && nextItem.type === 'exercise') setActiveExerciseId(nextItem.firstExerciseId);
                        else if (idx >= 0 && carouselItems[idx + 1]?.type === 'finish') setIsFinishModalOpen(true);
                    }
                }}
            />
            
            {starGoalProgress && (
                <GoalProgressOverlay 
                    current1RM={starGoalProgress.current}
                    goal1RM={starGoalProgress.goal}
                    unit={starGoalProgress.unit}
                    onAnimationComplete={handleGoalAnimationComplete}
                />
            )}

<WorkoutHeader
                sessionName={currentSession.name}
                activePartName={activePartInfo?.name}
                activePartColor={undefined}
                isResting={!!(restTimer && restTimer.remaining > 0)}
                onFinishPress={() => setIsFinishModalOpen(true)}
                onFinishLongPress={() => setFinishContextOpen(true)}
                restTimerRemaining={restTimer?.remaining}
                elapsedSeconds={duration}
                completedSetsCount={Object.keys(completedSets).length}
                totalSetsCount={allExercises.reduce((acc, ex) => acc + (ex.sets?.filter((s: any) => (s as any).type !== 'warmup').length ?? 0), 0)}
            />
            <FinishContextBottomSheet
                isOpen={finishContextOpen}
                onClose={() => setFinishContextOpen(false)}
                onPause={() => { if (onPause) onPause(); }}
                onCancel={() => { if (window.confirm('¿Cancelar sesión? Se perderán los datos no guardados.')) onCancel(); }}
            />
            
            <div className="mt-2 flex-1 min-h-0 overflow-y-auto pb-36 px-2 w-full max-w-none relative">
            {displayParts.map((part: any, partIndex: number) => (
                    <details key={part.id || partIndex} open={!collapsedParts[part.id]} className="group [&>summary]:hidden">
                        <summary onClick={(e) => { e.preventDefault(); setCollapsedParts(prev => ({...prev, [part.id]: !prev[part.id]})); }} className="flex items-center justify-between mb-4 px-3 py-2 cursor-pointer list-none bg-[#252525]">
                            <div className="flex items-center gap-3"><h3 className="text-sm font-medium uppercase tracking-wide text-[#a3a3a3]">{part.name || 'Sesión'}</h3></div><ChevronRightIcon className={`text-[#737373] transition-transform ${collapsedParts[part.id] ? '' : 'rotate-90'}`} size={16} />
                        </summary>
                        <div className="space-y-4 relative pl-0 pr-0 w-full">
                            {part.exercises.map((ex: Exercise) => {
                                const exInfo = exerciseList.find(e => e.id === ex.exerciseDbId);
                                const pr = findPrForExercise(exInfo || ({} as any), history, settings, selectedTags[ex.id]);
                                const isActive = activeExerciseId === ex.id;
                                const isFocused = focusExerciseId === ex.id;
                                const hasWarmup = ex.warmupSets && ex.warmupSets.length > 0;

                                return (
                                    <div key={ex.id} className="relative transition-all duration-500 w-full max-w-none" id={`exercise-card-${ex.id}`}>
                                        <details 
                                            open={isActive || true} 
                                            className={`set-card-details w-full overflow-visible transition-all duration-500 !border-0 !shadow-none !bg-transparent ${isFocused ? 'z-40 ring-2 ring-white/30' : ''}`}
                                        >
                                            <summary className="set-card-summary p-3 flex flex-col items-stretch hidden" onClick={() => handleHeaderClick(ex.id)}>
                                                <div className="flex items-center justify-between gap-3 w-full">
                                                    <div className="flex items-center gap-3 min-w-0 flex-1">
                                                        <div className={`w-8 h-8 flex items-center justify-center font-medium text-lg flex-shrink-0 bg-[#252525] text-[#a3a3a3]`}>
                                                            {allExercises.findIndex(e => e.id === ex.id) + 1}
                                                        </div>
                                                        <p className="font-bold text-lg text-white truncate">{ex.name}</p>
                                                    </div>
                                                    <div className="flex items-center gap-2">
                                                        <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); setHistoryModalExercise(ex); }} className="p-1 text-slate-500 hover:text-primary-color">
                                                            <ClockIcon size={20} />
                                                        </button>
                                                        <ChevronRightIcon className="details-arrow text-slate-400" />
                                                    </div>
                                                </div>
                                            </summary>
                                            <div className="set-card-content !border-none !p-3 space-y-2 relative !p-3 sm:!p-4 !max-w-none w-full">
                                                <div className="mb-4 flex items-center justify-between">
                                                    <h3 className="text-lg font-bold text-white truncate">{ex.name}</h3>
                                                    <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); setHistoryModalExercise(ex); }} className="p-1.5 text-[#737373] hover:text-white shrink-0 min-w-[44px] min-h-[44px] flex items-center justify-center" title="Historial"><ClockIcon size={18} /></button>
                                                </div>
                                                {pr && <div className="p-2 text-center text-sm bg-[#252525] text-[#a3a3a3]"><p className="font-medium flex items-center justify-center gap-2"><TrophyIcon size={16}/> {pr.prString}</p></div>}
                                                
                                                <HeaderAccordion 
                                                    exercise={ex} 
                                                    exerciseInfo={exInfo} 
                                                    selectedTag={selectedTags[ex.id]} 
                                                    onTagChange={(tag) => setSelectedTags(prev => ({...prev, [ex.id]: tag}))} 
                                                />

                                                {/* Series de aproximación (vista separada) + Series efectivas */}
                                                <div className="space-y-4" ref={(el) => { setsContainerRefs.current[ex.id] = el; }}>
                                                    {hasWarmup && (
                                                        <div id={`warmup-card-${ex.id}`} className="overflow-hidden rounded-none border-0 border-t border-slate-600/30 bg-slate-800/40">
                                                            <div className="px-3 py-2 border-b border-slate-600/30 flex items-center justify-between">
                                                                <div className="flex items-center gap-2">
                                                                    <FlameIcon size={16} className="text-slate-400" />
                                                                    <span className="text-[10px] font-mono font-bold uppercase tracking-widest text-slate-400">Series de aproximación</span>
                                                                </div>
                                                                <span className="text-[9px] font-mono text-slate-500">{ex.warmupSets!.length} series</span>
                                                            </div>
                                                            <div className="p-3 space-y-2">
                                                                {(ex.warmupSets as WarmupSetDefinition[]).map((wSet, wi) => {
                                                                    const suggested = (ex.sets?.length ?? 0) > 0 ? getWeightSuggestionForSet(ex, exInfo, 0, [], settings, history, selectedTags[ex.id], sessionAdjusted1RMs[ex.id]) : undefined;
                                                                    const baseW = consolidatedWeights[ex.id] ?? suggested ?? 0;
                                                                    const calcKg = baseW > 0 ? roundWeight((baseW * wSet.percentageOfWorkingWeight) / 100, settings.weightUnit) : '—';
                                                                    return (
                                                                        <div key={wSet.id} className="flex items-center justify-between py-2 px-3 rounded-lg bg-black/20 border border-slate-600/20 text-left">
                                                                            <span className="text-xs font-mono font-bold text-slate-400 w-6">{wi + 1}</span>
                                                                            <span className="text-[10px] font-mono text-slate-400">{wSet.percentageOfWorkingWeight}%</span>
                                                                            <span className="text-sm font-mono font-bold text-white tabular-nums">{calcKg}{typeof calcKg === 'number' ? settings.weightUnit : ''}</span>
                                                                            <span className="text-sm font-mono font-bold text-slate-300 tabular-nums">{wSet.targetReps} reps</span>
                                                                        </div>
                                                                    );
                                                                })}
                                                                <button onClick={() => { setActiveExerciseId(ex.id); setActiveSetId(`warmup-${ex.id}`); }} className="w-full py-2.5 mt-1 rounded-lg border border-slate-600/40 text-slate-400 hover:bg-slate-700/30 text-[10px] font-mono font-bold uppercase tracking-wider transition-colors min-h-[44px]">
                                                                    Iniciar aproximación
                                                                </button>
                                                            </div>
                                                        </div>
                                                    )}
                                                    <div className="overflow-hidden rounded-none border-0 border-t border-white/5 bg-transparent">
                                                        <div className="px-2 py-1 border-b border-white/5 flex items-center gap-2">
                                                            <span className="text-[10px] font-mono font-bold uppercase tracking-widest text-slate-400">Series efectivas</span>
                                                        </div>
                                                        <div className="session-table w-full" data-tabular="true">
                                                            <div className="flex items-center gap-2 px-2 py-1.5 border-b border-white/5 text-[10px] font-mono font-bold uppercase tracking-widest text-slate-500">
                                                                <span className="w-8 text-center">Set</span>
                                                                <span className="flex-1 min-w-[60px] text-center">Kg</span>
                                                                <span className="flex-1 min-w-[56px] text-center">Reps</span>
                                                                <span className="w-10" />
                                                            </div>
                                                            {ex.sets.map((set: ExerciseSet, setIndex) => {
                                                                const setId = set.id;
                                                                const isCompleted = !!completedSets[String(setId)];
                                                                const isActiveRow = activeExerciseId === ex.id && activeSetId === setId;
                                                                const inputsRaw = setInputs[String(setId)];
                                                                const safeInputsRow: SetInputState = inputsRaw && !('left' in inputsRaw) ? (inputsRaw as SetInputState) : (inputsRaw as UnilateralSetInputs)?.left || { reps: '', weight: '', rpe: '', rir: '', isFailure: false, duration: '', partialReps: '' };
                                                                const rowClass = isCompleted ? 'session-row-completed' : isActiveRow ? 'session-row-active' : 'session-row-pending';
                                                                const rowMinH = settings.sessionCompactView ? 40 : 48;
                                                                const ghost = getGhostForSet((ex.exerciseDbId || ex.id) as string, setIndex, history);
                                                                const suggestedKg = getWeightSuggestionForSet(ex, exInfo, setIndex, ex.sets.slice(0, setIndex).map(s => { const d = completedSets[String(s.id)] as { left?: { weight?: number; reps?: number; machineBrand?: string }; right?: { weight?: number; reps?: number } } | undefined; if (!d) return { weight: 0 }; const p = ex.isUnilateral ? (d.left || d.right) : d.left; return p ? { weight: p.weight || 0, reps: p.reps, machineBrand: p.machineBrand } : { weight: 0 }; }), settings, history, selectedTags[ex.id], sessionAdjusted1RMs[ex.id]);
                                                                const placeholderKg = suggestedKg != null ? String(suggestedKg) : (ghost?.weight ? String(ghost.weight) : '');
                                                                const placeholderReps = ghost?.reps ? String(ghost.reps) : (set.targetReps ? String(set.targetReps) : '');
                                                                return (
                                                                        <div key={setId} className="border-b border-white/5">
                                                                        <div className={`flex items-center gap-2 px-2 py-2 ${rowClass} transition-colors`} style={{ minHeight: rowMinH }} onClick={() => { if (isCompleted || !isActiveRow) { setActiveExerciseId(ex.id); setActiveSetId(setId); } }}>
                                                                            <span className="w-8 text-center text-xs font-mono font-bold text-slate-500 tabular-nums">{setIndex + 1}</span>
                                                                            <div className="flex-1 min-w-[60px] flex justify-center" onClick={e => { e.stopPropagation(); setActiveExerciseId(ex.id); setActiveSetId(setId); setNumpadState({ setId: String(setId), field: 'weight', exerciseId: ex.id }); }} role="button" tabIndex={0}>
                                                                                <span className={`w-full max-w-[72px] text-center text-sm font-mono py-1 tabular-nums block ${safeInputsRow.weight ? 'text-white' : 'text-slate-500'}`}>{safeInputsRow.weight || placeholderKg || '—'}</span>
                                                                            </div>
                                                                            <div className="flex-1 min-w-[56px] flex justify-center" onClick={e => { e.stopPropagation(); setActiveExerciseId(ex.id); setActiveSetId(setId); setNumpadState({ setId: String(setId), field: 'reps', exerciseId: ex.id }); }} role="button" tabIndex={0}>
                                                                                <span className={`w-full max-w-[56px] text-center text-sm font-mono py-1 tabular-nums block ${(ex.trainingMode === 'time' ? safeInputsRow.duration : safeInputsRow.reps) ? 'text-white' : 'text-slate-500'}`}>{ex.trainingMode === 'time' ? (safeInputsRow.duration || placeholderReps || '—') : (safeInputsRow.reps || placeholderReps || '—')}</span>
                                                                            </div>
                                                                            <div className="w-10 flex justify-center" onClick={e => e.stopPropagation()}>
                                                                                <button type="button" onClick={() => handleLogSet(ex, set, false)} className={`min-w-[44px] min-h-[44px] w-10 h-10 rounded-lg border flex items-center justify-center transition-all ${isCompleted ? 'border-amber-500/40 bg-amber-950/30 text-amber-400' : 'border-white/10 bg-transparent text-slate-500 hover:border-amber-500/30 hover:text-amber-400'}`}>
                                                                                    <CheckCircleIcon size={18} />
                                                                                </button>
                                                                            </div>
                                                                        </div>
                                                                        {isActiveRow && (
                                                                            <div id={`set-card-${setId}`} className="px-2 pb-2">
                                                                                <SetDetails exercise={ex} exerciseInfo={exInfo} set={set} setIndex={setIndex} settings={settings} inputs={(setInputs[String(setId)] as SetInputState) || safeInputsRow} onInputChange={(field, value, side) => handleSetInputChange(String(setId), field as keyof SetInputState, value, side)} onLogSet={(isCal) => handleLogSet(ex, set, isCal)} isLogged={!!isCompleted} history={history} currentSession1RM={sessionAdjusted1RMs[ex.id]} base1RM={exInfo?.calculated1RM || ex.reference1RM} isCalibrated={!!sessionAdjusted1RMs[ex.id]} cardAnimation={setCardAnimations[String(setId)]} addToast={addToast} suggestedWeight={getWeightSuggestionForSet(ex, exInfo, setIndex, ex.sets.slice(0, setIndex).map(s => { const d = completedSets[String(s.id)] as { left?: { weight?: number; reps?: number; machineBrand?: string }; right?: { weight?: number; reps?: number } } | undefined; if (!d) return { weight: 0 }; const p = ex.isUnilateral ? (d.left || d.right) : d.left; return p ? { weight: p.weight || 0, reps: p.reps, machineBrand: p.machineBrand } : { weight: 0 }; }), settings, history, selectedTags[ex.id], sessionAdjusted1RMs[ex.id])} selectedTag={selectedTags[ex.id]} tableRowMode setId={String(setId)} onOpenNumpad={(opts) => { setActiveExerciseId(ex.id); setActiveSetId(setId); setNumpadState({ setId: String(setId), exerciseId: ex.id, ...opts }); }} />
                                                                            </div>
                                                                        )}
                                                                    </div>
                                                                );
                                                            })}
                                                        </div>
                                                    </div>
                                                    <div id={`feedback-card-${ex.id}`} className="overflow-hidden border-t border-[#252525] bg-[#252525]">
                                                        <button onClick={() => { setActiveExerciseId(ex.id); setActiveSetId(`feedback-${ex.id}`); }} className="w-full p-4 flex flex-col items-center justify-center gap-2 hover:bg-[#2a2a2a] transition-colors min-h-[48px]">
                                                            <ActivityIcon size={24} className="text-[#a3a3a3]" />
                                                            <span className="text-[10px] font-medium uppercase tracking-wide text-[#a3a3a3]">Feedback Post-Ejercicio</span>
                                                        </button>
                                                    </div>
                                                </div>
                                            </div>
                                        </details>
                                    </div>
                                )
                            })}
                        </div>
                    </details>
                ))}
            </div>

            {/* Carrusel de tarjetas + acciones */}
            <div className="fixed left-0 right-0 bottom-0 z-20 bg-[#1a1a1a] pb-[max(1rem, env(safe-area-inset-bottom))] flex flex-col">
                    {/* Acciones rápidas: 90s y Notas */}
                    <div className="flex justify-center gap-2 px-4 py-2 shrink-0">
                        <button onClick={() => handleStartRest(90, 'Descanso')} className="flex items-center gap-1.5 px-4 py-2.5 bg-white text-[#1a1a1a] text-[10px] font-medium uppercase tracking-wide min-h-[44px]">
                            <ClockIcon size={14} /> 90s
                        </button>
                        <button onClick={() => setShowNotesDrawer(true)} className="flex items-center gap-1.5 px-4 py-2.5 bg-white text-[#1a1a1a] text-[10px] font-medium uppercase tracking-wide min-h-[44px]">
                            <PencilIcon size={14} /> Notas
                        </button>
                    </div>
                    <div className="shrink-0">
                    <CardCarouselBar
                        items={carouselItems}
                        activeExerciseId={activeExerciseId}
                        skippedIds={skippedExerciseIds}
                        onSelectExercise={(id) => setActiveExerciseId(id)}
                        onLongPressExercise={(item) => setContextMenuItem({ item })}
                        onFinishCardExpand={() => setFinishCardExpanded(true)}
                        onFinish={() => { setFinishCardExpanded(false); setIsFinishModalOpen(true); }}
                        onReorder={handleCarouselReorder}
                        durationMinutes={Math.floor(duration / 60)}
                        completedSetsCount={Object.keys(completedSets).length}
                        totalSetsCount={allExercises.reduce((acc, ex) => acc + (ex.sets?.filter((s: any) => s.type !== 'warmup').length ?? 0), 0)}
                        totalTonnage={Object.entries(completedSets).reduce((acc, [, data]) => {
                            const d = data as { left: OngoingSetData | null; right: OngoingSetData | null };
                            let t = 0;
                            if (d?.left) t += (d.left.weight || 0) * (d.left.reps || 0);
                            if (d?.right) t += (d.right?.weight || 0) * (d.right?.reps || 0);
                            return acc + t;
                        }, 0)}
                        finishCardExpanded={finishCardExpanded}
                    />
                    </div>
                </div>

            {showNotesDrawer && (
                <WorkoutDrawer isOpen={true} onClose={() => setShowNotesDrawer(false)} title="Notas de Sesión" height="50vh">
                    <div className="p-5">
                        <textarea value={sessionNotes} onChange={(e) => setSessionNotes(e.target.value)} placeholder="Notas rápidas durante el entrenamiento..." rows={6} className="w-full bg-white p-4 text-[#1a1a1a] text-sm placeholder-[#737373] outline-none" />
                        <p className="text-[9px] text-[#525252] mt-2">Se incluirán al finalizar la sesión.</p>
                    </div>
                </WorkoutDrawer>
            )}

            {numpadState && (() => {
                const raw = setInputs[numpadState.setId];
                const ex = allExercises.find(e => e.id === numpadState.exerciseId);
                const isTimeMode = ex?.trainingMode === 'time';
                const side = numpadState.side ?? (ex?.isUnilateral ? 'left' as const : undefined);
                const base = raw && side && 'left' in raw ? (raw as UnilateralSetInputs)[side] : (raw as SetInputState);

                let currentValue = '';
                if (base) {
                    if (numpadState.field === 'dropSetWeight' && numpadState.dropSetIndex != null && base.dropSets?.[numpadState.dropSetIndex])
                        currentValue = base.dropSets[numpadState.dropSetIndex].weight === 0 ? '' : String(base.dropSets[numpadState.dropSetIndex].weight);
                    else if (numpadState.field === 'dropSetReps' && numpadState.dropSetIndex != null && base.dropSets?.[numpadState.dropSetIndex])
                        currentValue = base.dropSets[numpadState.dropSetIndex].reps === 0 ? '' : String(base.dropSets[numpadState.dropSetIndex].reps);
                    else if (numpadState.field === 'restPauseRestTime' && numpadState.restPauseIndex != null && base.restPauses?.[numpadState.restPauseIndex])
                        currentValue = base.restPauses[numpadState.restPauseIndex].restTime === 0 ? '' : String(base.restPauses[numpadState.restPauseIndex].restTime);
                    else if (numpadState.field === 'restPauseReps' && numpadState.restPauseIndex != null && base.restPauses?.[numpadState.restPauseIndex])
                        currentValue = base.restPauses[numpadState.restPauseIndex].reps === 0 ? '' : String(base.restPauses[numpadState.restPauseIndex].reps);
                    else {
                        const ef = numpadState.field === 'reps' && isTimeMode ? 'duration' : numpadState.field;
                        if (['weight','reps','duration','partialReps','rpe','rir'].includes(ef))
                            currentValue = String(base[ef as keyof SetInputState] ?? '');
                    }
                }

                const handleNumpadChange = (v: string) => {
                    if (numpadState.field === 'dropSetWeight' && numpadState.dropSetIndex != null) {
                        const arr = [...(base?.dropSets || [])];
                        if (!arr[numpadState.dropSetIndex]) arr[numpadState.dropSetIndex] = { weight: 0, reps: 0 };
                        arr[numpadState.dropSetIndex] = { ...arr[numpadState.dropSetIndex], weight: v === '' ? 0 : parseFloat(v) || 0 };
                        handleSetInputChange(numpadState.setId, 'dropSets', arr, side);
                    } else if (numpadState.field === 'dropSetReps' && numpadState.dropSetIndex != null) {
                        const arr = [...(base?.dropSets || [])];
                        if (!arr[numpadState.dropSetIndex]) arr[numpadState.dropSetIndex] = { weight: 0, reps: 0 };
                        arr[numpadState.dropSetIndex] = { ...arr[numpadState.dropSetIndex], reps: v === '' ? 0 : parseInt(v, 10) || 0 };
                        handleSetInputChange(numpadState.setId, 'dropSets', arr, side);
                    } else if (numpadState.field === 'restPauseRestTime' && numpadState.restPauseIndex != null) {
                        const arr = [...(base?.restPauses || [])];
                        if (!arr[numpadState.restPauseIndex]) arr[numpadState.restPauseIndex] = { restTime: 15, reps: 0 };
                        arr[numpadState.restPauseIndex] = { ...arr[numpadState.restPauseIndex], restTime: v === '' ? 0 : parseInt(v, 10) || 0 };
                        handleSetInputChange(numpadState.setId, 'restPauses', arr, side);
                    } else if (numpadState.field === 'restPauseReps' && numpadState.restPauseIndex != null) {
                        const arr = [...(base?.restPauses || [])];
                        if (!arr[numpadState.restPauseIndex]) arr[numpadState.restPauseIndex] = { restTime: 15, reps: 0 };
                        arr[numpadState.restPauseIndex] = { ...arr[numpadState.restPauseIndex], reps: v === '' ? 0 : parseInt(v, 10) || 0 };
                        handleSetInputChange(numpadState.setId, 'restPauses', arr, side);
                    } else {
                        const ef = (numpadState.field === 'reps' && isTimeMode ? 'duration' : numpadState.field) as keyof SetInputState;
                        handleSetInputChange(numpadState.setId, ef, v, side);
                        if (numpadState.field === 'partialReps' && parseFloat(v) > 0)
                            handleSetInputChange(numpadState.setId, 'isPartial', true, side);
                    }
                };

                const isDecimal = ['weight','rpe','dropSetWeight'].includes(numpadState.field);
                const labels: Record<string, string> = {
                    weight: `Kg (${settings.weightUnit})`,
                    reps: 'Reps',
                    partialReps: 'Parciales',
                    duration: 'Seg',
                    rpe: 'RPE',
                    rir: 'RIR',
                    dropSetWeight: `Kg`,
                    dropSetReps: 'Reps',
                    restPauseRestTime: 's',
                    restPauseReps: 'Reps',
                };
                return (
                    <NumpadOverlay
                        value={currentValue}
                        onChange={handleNumpadChange}
                        onClose={() => setNumpadState(null)}
                        onNext={settings.sessionAutoAdvanceFields !== false && numpadState.field === 'weight'
                            ? () => setNumpadState({ ...numpadState, field: 'reps' })
                            : undefined}
                        mode={isDecimal ? 'decimal' : 'integer'}
                        label={labels[numpadState.field] ?? numpadState.field}
                        showNextButton={numpadState.field === 'weight'}
                    />
                );
            })()}
        </div>
    );
};