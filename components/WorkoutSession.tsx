// components/WorkoutSession.tsx
import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { Session, WorkoutLog, CompletedExercise, CompletedSet, Exercise, ExerciseSet, SessionBackground, OngoingSetData, SetInputState, UnilateralSetInputs, DropSetData, RestPauseData, ExerciseMuscleInfo, Program, Settings, PlanDeviation, CoverStyle, ToastData } from '../types';
import Button from './ui/Button';
import { ClockIcon, ChevronRightIcon, ChevronLeftIcon, FlameIcon, CheckCircleIcon, TrophyIcon, MinusIcon, PlusIcon, MicIcon, MicOffIcon, AlertTriangleIcon, CheckCircleIcon as CheckIcon, XCircleIcon, StarIcon, SparklesIcon, SettingsIcon, ArrowUpIcon, ArrowDownIcon, RefreshCwIcon, BrainIcon, LinkIcon, PlayIcon, PauseIcon, ActivityIcon, InfoIcon, BodyIcon, PencilIcon } from './icons'; 
import { playSound } from '../services/soundService';
import { hapticImpact as _hapticImpact, ImpactStyle, hapticNotification as _hapticNotification, NotificationType } from '../services/hapticsService';
import { calculateBrzycki1RM, getWeightSuggestionForSet, roundWeight, calculateWeightFrom1RM } from '../utils/calculations';
import { useAppDispatch, useAppState, useAppContext } from '../contexts/AppContext';

// Bypass de TypeScript: Adaptador para que los strings literales sean aceptados como Enums de Capacitor
const hapticImpact = (style?: any) => _hapticImpact(style);
const hapticNotification = (type?: any) => _hapticNotification(type);
import { calculateSpinalScore, calculatePersonalizedBatteryTanks, calculateSetBatteryDrain } from '../services/auge';
import { normalizeMuscleGroup } from '../services/volumeCalculator';
import { getCachedAdaptiveData, AugeAdaptiveCache } from '../services/augeAdaptiveService';
import { GPFatigueCurve } from './ui/AugeDeepView';
import FinishWorkoutModal from './FinishWorkoutModal';
import ExerciseHistoryModal from './ExerciseHistoryModal';
import Modal from './ui/Modal';
import WarmupDrawer from './workout/WarmupDrawer';
import PostExerciseDrawer from './workout/PostExerciseDrawer';
import WorkoutDrawer from './workout/WorkoutDrawer';
import { useKeyboardOverlayMode } from '../hooks/useKeyboardOverlayMode';

const InCardTimer: React.FC<{ initialTime: number; onSave: (duration: number) => void; }> = ({ initialTime, onSave }) => {
    const [time, setTime] = useState(initialTime * 1000);
    const [isRunning, setIsRunning] = useState(false);
    const intervalRef = useRef<number | null>(null);
    const startTimeRef = useRef<number | null>(null);

    useEffect(() => {
        if (isRunning) {
            startTimeRef.current = Date.now() - time;
            intervalRef.current = window.setInterval(() => {
                setTime(Date.now() - (startTimeRef.current || 0));
            }, 50);
        } else {
            if (intervalRef.current) clearInterval(intervalRef.current);
        }
        return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
    }, [isRunning, time]);

    const handleToggle = (e: React.MouseEvent) => { e.preventDefault(); e.stopPropagation(); setIsRunning(!isRunning); };
    const handleStop = (e: React.MouseEvent) => { e.preventDefault(); e.stopPropagation(); setIsRunning(false); onSave(Math.ceil(time / 1000)); };
    const formatMs = (ms: number) => { const totalSeconds = Math.floor(ms / 1000); const mins = Math.floor(totalSeconds / 60).toString().padStart(2, '0'); const secs = (totalSeconds % 60).toString().padStart(2, '0'); return `${mins}:${secs}`; };

    return (
        <div className="flex items-center gap-2 bg-slate-800 rounded-lg p-1 pr-2 border border-slate-700">
             <button onClick={handleToggle} className={`w-8 h-8 flex items-center justify-center rounded-md transition-colors ${isRunning ? 'bg-yellow-500/20 text-yellow-400' : 'bg-green-500/20 text-green-400'}`}>{isRunning ? <PauseIcon size={14} /> : <PlayIcon size={14} />}</button>
            <span className={`font-mono font-bold text-lg w-14 text-center ${isRunning ? 'text-white' : 'text-slate-400'}`}>{formatMs(time)}</span>
             <button onClick={handleStop} className="p-1.5 text-slate-400 hover:text-white bg-slate-700/50 rounded hover:bg-slate-600 transition-colors" title="Detener y Guardar"><CheckCircleIcon size={16} /></button>
        </div>
    );
};

const SetTimerButton: React.FC<{ onSave: (duration: number) => void; initialDuration?: number; }> = ({ onSave, initialDuration }) => {
    const [duration, setDuration] = useState(initialDuration || 0);
    const [isRunning, setIsRunning] = useState(false);
    const startTimeRef = useRef<number | null>(null);
    const intervalRef = useRef<number | null>(null);

    const toggleTimer = (e: React.MouseEvent) => {
        e.preventDefault(); e.stopPropagation();
        if (isRunning) { if (intervalRef.current) clearInterval(intervalRef.current); setIsRunning(false); onSave(duration); } 
        else { startTimeRef.current = Date.now() - (duration * 1000); intervalRef.current = window.setInterval(() => { setDuration(Math.floor((Date.now() - (startTimeRef.current || 0)) / 1000)); }, 1000); setIsRunning(true); }
    };
    useEffect(() => { return () => { if (intervalRef.current) clearInterval(intervalRef.current); }; }, []);
    const formatSeconds = (s: number) => { if (s < 60) return `${s}s`; const m = Math.floor(s/60); const sec = s%60; return `${m}:${sec.toString().padStart(2,'0')}`; }

    return (
         <button onClick={toggleTimer} className={`flex items-center justify-center gap-1 w-8 h-8 rounded-full transition-all shadow-md ${isRunning ? 'bg-red-500 text-white animate-pulse' : duration > 0 ? 'bg-sky-500 text-white' : 'bg-slate-700 text-slate-300'}`} title="Cronómetro de Serie (TUT)">
            {isRunning || duration > 0 ? <span className="text-[9px] font-black">{duration}</span> : <ClockIcon size={14} />}
        </button>
    );
};

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
        hapticNotification(NotificationType.SUCCESS);

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
            <div className="absolute inset-0 bg-black/80 backdrop-blur-md" onClick={onAnimationComplete} />
            
            <div className="relative z-10 w-full max-w-sm overflow-hidden bg-slate-900 border border-white/10 rounded-[2.5rem] shadow-[0_32px_64px_-12px_rgba(0,0,0,0.8)] p-8 text-center animate-modal-enter">
                <div className="absolute top-0 inset-x-0 h-32 bg-gradient-to-b from-yellow-500/20 to-transparent pointer-events-none rounded-t-3xl z-0" />
                
                <div className="relative inline-block mb-6">
                    <div className="absolute inset-0 bg-yellow-400 blur-2xl opacity-20 animate-pulse" />
                    <TrophyIcon size={64} className={`${isGoalMet ? 'text-yellow-400' : 'text-slate-400'} relative z-10 mx-auto drop-shadow-lg`} />
                </div>

                <div className="space-y-1 mb-8">
                    <h3 className="text-3xl font-black text-white uppercase tracking-tighter italic">
                        {isGoalMet ? '¡META ALCANZADA!' : 'SINCRO DE POTENCIAL'}
                    </h3>
                    <p className="text-[10px] text-slate-400 font-black uppercase tracking-[0.3em]">
                        {isGoalMet ? 'Has superado tus límites' : 'Progresando hacia el objetivo'}
                    </p>
                </div>

                <div className="space-y-6">
                    <div className="flex justify-between items-end px-1">
                        <div className="text-left">
                            <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Actual (1RMe)</p>
                            <p className="text-xl font-black text-white">{current1RM.toFixed(1)} <span className="text-[10px] text-slate-500 font-bold">{unit}</span></p>
                        </div>
                        <div className="text-right">
                            <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Objetivo</p>
                            <p className="text-xl font-black text-yellow-400">{goal1RM} <span className="text-[10px] text-yellow-900 font-bold">{unit}</span></p>
                        </div>
                    </div>

                    <div className="relative h-4 bg-slate-800 rounded-full overflow-hidden border border-white/5 shadow-inner">
                        <div 
                            className="absolute top-0 left-0 h-full bg-gradient-to-r from-yellow-600 via-yellow-400 to-white shadow-[0_0_20px_rgba(250,204,21,0.6)] transition-all duration-[2500ms] ease-out" 
                            style={{ width: `${renderProgress}%` }}
                        />
                    </div>

                    <div className="flex flex-col items-center">
                        <span className="text-6xl font-black text-white tracking-tighter tabular-nums">
                            {renderProgress.toFixed(1)}%
                        </span>
                    </div>
                </div>

                <p className="mt-8 text-[9px] text-slate-500 font-bold uppercase tracking-widest animate-pulse">Analizando Biomarcadores...</p>
                
                <button onClick={onAnimationComplete} className="mt-6 w-full py-3 rounded-2xl bg-white/5 text-slate-500 font-bold text-xs uppercase tracking-widest">Cerrar</button>
            </div>
        </div>
    );
};

const getPartTheme = (name: string) => {
    const n = name.toLowerCase();
    if (n.includes('calentamiento') || n.includes('warmup') || n.includes('movilidad')) 
        return { color: '#fbbf24', icon: '🔥', bgColor: 'rgba(251, 191, 36, 0.05)', borderColor: '#fbbf24' };
    if (n.includes('principal') || n.includes('main') || n.includes('fuerza') || n.includes('básicos')) 
        return { color: '#3b82f6', icon: '💪', bgColor: 'rgba(59, 130, 246, 0.05)', borderColor: '#3b82f6' };
    if (n.includes('finisher') || n.includes('accesorio') || n.includes('aislamiento') || n.includes('bombeo')) 
        return { color: '#a855f7', icon: '⚡', bgColor: 'rgba(168, 85, 247, 0.05)', borderColor: '#a855f7' };
    return { color: '#64748b', icon: '📋', bgColor: 'rgba(100, 116, 139, 0.05)', borderColor: '#64748b' };
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
                    className={`flex-1 flex items-center justify-center gap-1 text-[10px] uppercase font-black tracking-wider py-1.5 rounded-lg border transition-all ${expandedSection === 'tags' ? 'bg-cyan-900/40 border-cyan-500 text-cyan-400' : 'bg-slate-800/50 border-slate-700 text-slate-400 hover:text-cyan-400'}`}
                >
                   <BodyIcon size={12}/> {selectedTag && selectedTag !== 'Base' ? selectedTag : 'Etiquetas'} {expandedSection === 'tags' ? <ChevronRightIcon className="-rotate-90" size={12}/> : <ChevronRightIcon className="rotate-90" size={12}/>}
                </button>
                <button 
                    onClick={() => toggleSection('setup')} 
                    className={`flex-1 flex items-center justify-center gap-1 text-[10px] uppercase font-black tracking-wider py-1.5 rounded-lg border transition-all ${expandedSection === 'setup' ? 'bg-amber-900/40 border-amber-500 text-amber-400' : 'bg-slate-800/50 border-slate-700 text-slate-400 hover:text-amber-400'}`}
                >
                    <SettingsIcon size={12}/> Setup Info {expandedSection === 'setup' ? <ChevronRightIcon className="-rotate-90" size={12}/> : <ChevronRightIcon className="rotate-90" size={12}/>}
                </button>
            </div>

            {expandedSection === 'tags' && (
                <div className="animate-fade-in bg-slate-900 border-y border-cyan-500/30 p-3 shadow-inner">
                    <div className="flex flex-wrap gap-2 mb-3">
                        {tags.map(tag => (
                            <button key={tag} onClick={() => { onTagChange(tag); setExpandedSection(null); }} className={`px-3 py-1.5 rounded-lg text-[10px] font-bold transition-all border ${selectedTag === tag ? 'bg-cyan-600 border-cyan-400 text-white shadow-lg' : 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'}`}>{tag}</button>
                        ))}
                    </div>
                    {isEditingTag ? (
                        <div className="flex gap-2 items-center mt-2 animate-fade-in">
                            <input type="text" value={newTagName} onChange={(e) => setNewTagName(e.target.value)} placeholder="Nueva..." className="flex-1 bg-slate-950 border border-slate-700 rounded px-2 py-1.5 text-xs text-white outline-none focus:border-cyan-500" autoFocus />
                            <button onClick={handleCreateTag} className="p-1 bg-green-600 rounded text-white"><CheckIcon size={14}/></button>
                            <button onClick={() => setIsEditingTag(false)} className="p-1 bg-red-600 rounded text-white"><XCircleIcon size={14}/></button>
                        </div>
                    ) : (
                         <button onClick={() => setIsEditingTag(true)} className="w-full py-2 rounded-lg border border-dashed border-slate-600 text-slate-400 text-[10px] hover:text-white hover:border-slate-400 flex items-center justify-center gap-1"><PlusIcon size={10} /> Crear Nueva Etiqueta</button>
                    )}
                </div>
            )}

            {expandedSection === 'setup' && (
                 <div className="animate-fade-in bg-slate-900 border-y border-amber-500/30 p-3 shadow-inner space-y-3">
                     <div className="grid grid-cols-2 gap-3">
                         <div><label className="text-[9px] text-amber-500/80 font-bold uppercase block mb-1">Asiento</label><input type="text" value={localDetails.seatPosition || ''} onChange={e => setLocalDetails({...localDetails, seatPosition: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded px-2 py-1.5 text-xs text-white"/></div>
                         <div><label className="text-[9px] text-amber-500/80 font-bold uppercase block mb-1">Pines</label><input type="text" value={localDetails.pinPosition || ''} onChange={e => setLocalDetails({...localDetails, pinPosition: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded px-2 py-1.5 text-xs text-white"/></div>
                     </div>
                     <div><label className="text-[9px] text-amber-500/80 font-bold uppercase block mb-1">Notas</label><textarea value={localDetails.equipmentNotes || ''} onChange={e => setLocalDetails({...localDetails, equipmentNotes: e.target.value})} rows={2} className="w-full bg-slate-950 border border-slate-800 rounded px-2 py-1.5 text-xs text-white"/></div>
                     <Button onClick={handleSaveSetup} className="w-full !py-2 !text-[10px] uppercase font-black bg-amber-700 hover:bg-amber-600 border-none">Guardar Setup</Button>
                 </div>
            )}
        </div>
    );
}

const WorkoutHeader: React.FC<{
    sessionName: string;
    activePartName?: string;
    activePartColor?: string;
    background?: SessionBackground;
    coverStyle?: CoverStyle;
    isFocusMode: boolean;
    onToggleFocusMode: () => void;
    isLiveCoachActive: boolean;
    onToggleLiveCoach: () => void;
    isCompact?: boolean;
    isResting?: boolean;
    zenMode?: boolean;
    liveBatteryDrain?: { cns: number; muscular: number; spinal: number };
    adaptiveCache?: AugeAdaptiveCache | null;
}> = React.memo(({ sessionName, activePartName, activePartColor, background, coverStyle, isFocusMode, onToggleFocusMode, isLiveCoachActive, onToggleLiveCoach, isCompact, isResting, zenMode, liveBatteryDrain, adaptiveCache }) => {
    const [showLiveAuge, setShowLiveAuge] = useState(false);
    const bgImage = background?.type === 'image' ? `url(${background.value})` : undefined;
    const bgColor = background?.type === 'color' ? background.value : undefined;
    const tintClass = zenMode ? '' : (isResting ? 'bg-sky-900/30' : 'bg-red-900/10');
    const pulseClass = (isResting && !zenMode) ? 'animate-pulse-slow' : '';

    const getFilterString = () => {
        if (!coverStyle?.filters) return 'none';
        const f = coverStyle.filters;
        return `contrast(${f.contrast}%) saturate(${f.saturation}%) brightness(${f.brightness}%) grayscale(${f.grayscale}%) sepia(${f.sepia}%)`;
    };

    return (
        <div className={`sticky top-0 z-30 bg-black transition-all duration-700 ease-in-out ${isCompact ? 'h-16 shadow-2xl' : 'h-32'} ${tintClass} ${pulseClass}`}>
            <div className={`relative w-full h-full overflow-hidden transition-all duration-500`}>
                 <div className="absolute inset-0 w-full h-full bg-cover bg-center transition-opacity duration-500 z-0" style={{ 
                     backgroundImage: bgImage, 
                     backgroundColor: bgColor, 
                     opacity: isCompact ? 0.4 : 0.8, 
                     filter: `${getFilterString()} ${isCompact ? 'blur(10px)' : (background?.style?.blur ? `blur(${background.style.blur}px)` : 'none')}` 
                 }} />
                <div className={`absolute inset-0 z-0 transition-colors duration-1000 ${isResting ? 'bg-sky-950/40' : 'bg-black/50'}`} />
                <div className={`relative z-10 flex h-full items-center justify-between px-4 transition-all duration-500 ${isCompact ? 'pt-0' : 'pt-4'}`}>
                    <div className="flex-1 min-w-0 flex flex-col justify-center">
                        {activePartName && !isCompact && (
                             <span className="text-[10px] font-black uppercase tracking-widest mb-0.5 transition-colors duration-300" style={{ color: isResting ? '#38bdf8' : (activePartColor || 'var(--primary-color)') }}>{isResting ? 'PERIODO DE DESCANSO' : activePartName}</span>
                        )}
                        <h2 className={`font-black text-white truncate transition-all duration-500 drop-shadow-lg leading-tight ${isCompact ? 'text-xl' : 'text-3xl'}`}>{sessionName}</h2>
                        {isCompact && activePartName && (
                            <span className="text-[9px] font-bold uppercase tracking-widest text-slate-400 mt-0.5 block truncate" style={{ color: isResting ? '#38bdf8' : activePartColor }}>{isResting ? 'Descansando...' : activePartName}</span>
                        )}
                        
                        {/* --- BATERÍA AUGE PROMINENTE --- */}
                        {liveBatteryDrain && (
                            <div onClick={() => setShowLiveAuge(!showLiveAuge)} className={`flex flex-col gap-1 mt-1.5 transition-opacity duration-300 cursor-pointer bg-[#0a0a0a]/60 backdrop-blur-sm rounded-lg px-2 py-1.5 border border-orange-500/20 ${showLiveAuge ? 'border-violet-500/40' : ''}`}>
                                <div className="flex items-center gap-2">
                                    <span className="text-[9px] font-mono font-black uppercase tracking-widest text-orange-500/90">AUGE</span>
                                    {(() => {
                                        const total = (liveBatteryDrain.cns + liveBatteryDrain.muscular + liveBatteryDrain.spinal) / 3;
                                        const pct = Math.min(100, total);
                                        const color = pct > 70 ? 'bg-red-500' : pct > 40 ? 'bg-amber-500' : 'bg-emerald-500';
                                        return (
                                            <div className="flex-1 h-2 bg-slate-800 rounded-full overflow-hidden min-w-[60px] max-w-[120px]">
                                                <div className={`h-full rounded-full transition-all duration-500 ${color}`} style={{ width: `${pct}%` }} />
                                            </div>
                                        );
                                    })()}
                                    <span className="text-[9px] font-mono font-black text-slate-400">
                                        {((liveBatteryDrain.cns + liveBatteryDrain.muscular + liveBatteryDrain.spinal) / 3).toFixed(0)}%
                                    </span>
                                </div>
                                {!isCompact && (
                                    <div className="flex gap-3 items-center">
                                        <div className="flex items-center gap-1" title="SNC Drenado">
                                            <BrainIcon size={10} className={liveBatteryDrain.cns > 70 ? 'text-red-500' : 'text-sky-400'}/>
                                            <span className="text-[9px] font-mono text-slate-500">{liveBatteryDrain.cns.toFixed(0)}%</span>
                                        </div>
                                        <div className="flex items-center gap-1" title="Muscular Drenado">
                                            <ActivityIcon size={10} className={liveBatteryDrain.muscular > 70 ? 'text-red-500' : 'text-rose-400'}/>
                                            <span className="text-[9px] font-mono text-slate-500">{liveBatteryDrain.muscular.toFixed(0)}%</span>
                                        </div>
                                        <div className="flex items-center gap-1" title="Carga Espinal">
                                            <StarIcon size={10} className={liveBatteryDrain.spinal > 70 ? 'text-red-500' : 'text-amber-400'}/>
                                            <span className="text-[9px] font-mono text-slate-500">{liveBatteryDrain.spinal.toFixed(0)}%</span>
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}
                        {/* --- LIVE AUGE PANEL --- */}
                        {showLiveAuge && liveBatteryDrain && (
                            <div className="w-full mt-2 bg-[#0a0a0a]/95 backdrop-blur-sm border border-violet-500/20 rounded-2xl p-3 animate-fade-in shadow-lg" onClick={e => e.stopPropagation()}>
                                <div className="flex items-center gap-2 mb-2">
                                    <BrainIcon size={12} className="text-violet-400" />
                                    <span className="text-[9px] font-black uppercase tracking-widest text-violet-400">Live AUGE</span>
                                </div>
                                <GPFatigueCurve
                                    data={adaptiveCache?.gpCurve || null}
                                    compact={true}
                                    actualLine={{
                                        hours: [0, 1],
                                        values: [0, (liveBatteryDrain.cns + liveBatteryDrain.muscular + liveBatteryDrain.spinal) / 300]
                                    }}
                                />
                                {(() => {
                                    const totalDrain = (liveBatteryDrain.cns + liveBatteryDrain.muscular + liveBatteryDrain.spinal) / 3;
                                    const predicted = adaptiveCache?.gpCurve?.mean_fatigue?.[1] ?? 0.3;
                                    const predictedPct = predicted * 100;
                                    const diff = totalDrain - predictedPct;
                                    if (diff > 20) {
                                        return (
                                            <div className="mt-2 px-2 py-1.5 bg-red-500/10 border border-red-500/20 rounded-lg">
                                                <p className="text-[9px] text-red-400 font-bold">⚠ Acumulando {diff.toFixed(0)}% más fatiga de lo esperado</p>
                                            </div>
                                        );
                                    }
                                    return null;
                                })()}
                            </div>
                        )}
                    </div>
                    <div className="flex items-center gap-2 flex-shrink-0 ml-2">
                        <button onClick={onToggleFocusMode} className={`p-2 rounded-full backdrop-blur-md border transition-all active:scale-90 ${isFocusMode ? 'bg-primary-color border-primary-color text-white shadow-[0_0_15px_rgba(var(--primary-color-rgb),0.5)]' : 'bg-white/10 border-white/20 text-white'}`}>{isFocusMode ? <MinusIcon size={20}/> : <PlusIcon size={20}/>}</button>
                        <button onClick={onToggleLiveCoach} className={`p-2 rounded-full backdrop-blur-md border transition-all active:scale-90 ${isLiveCoachActive ? 'bg-red-500 border-red-400 text-white shadow-[0_0_15px_rgba(239,68,68,0.5)] animate-pulse' : 'bg-white/10 border-white/20 text-white'}`}>{isLiveCoachActive ? <MicOffIcon size={20}/> : <MicIcon size={20}/>}</button>
                    </div>
                </div>
            </div>
            <style>{`@keyframes pulse-slow { 0%, 100% { opacity: 0.8; } 50% { opacity: 0.4; } } .animate-pulse-slow { animation: pulse-slow 4s ease-in-out infinite; }`}</style>
        </div>
    );
});

const GhostSetInfo: React.FC<{ exerciseId: string; setIndex: number; history: WorkoutLog[]; settings: any; }> = ({ exerciseId, setIndex, history, settings }) => {
    const lastLog = useMemo(() => { for (let i = history.length - 1; i >= 0; i--) { const log = history[i]; const completedEx = log.completedExercises.find(ex => ex.exerciseDbId === exerciseId || ex.exerciseId === exerciseId); if (completedEx && completedEx.sets[setIndex]) { if (completedEx.sets[setIndex].completedReps || completedEx.sets[setIndex].weight) { return { date: log.date, set: completedEx.sets[setIndex] }; } } } return null; }, [history, exerciseId, setIndex]);
    if (!lastLog) return null;
    const { date, set } = lastLog;
    const dateStr = new Date(date).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' });
    return ( <div className="text-[10px] text-slate-500 flex items-center justify-center gap-1.5 mb-2 py-1 border-b border-slate-800/50 uppercase font-black tracking-widest"><span>{dateStr}: {set.weight}{settings.weightUnit} x {set.completedReps}{set.completedRPE && <span className="ml-1 text-primary-color">(@{set.completedRPE})</span>}</span></div>);
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
}> = React.memo(({ exercise, exerciseInfo, set, setIndex, settings, inputs, onInputChange, onLogSet, isLogged, history, currentSession1RM, base1RM, isCalibrated, cardAnimation, addToast, suggestedWeight, selectedTag }) => {
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
        hapticNotification(NotificationType.ERROR); // Vibración pesada de error
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
    
    let containerClass = "set-card-content flex flex-col bg-slate-900/30 p-2 transition-all duration-700";
    if (cardAnimation === 'amrap') containerClass += " shadow-[0_0_50px_rgba(234,179,8,0.3)] border-2 border-yellow-400 bg-yellow-900/20";
    else if (cardAnimation === 'success') containerClass += " shadow-[0_0_40px_rgba(59,130,246,0.3)] border border-blue-400 bg-blue-900/10";
    else if (cardAnimation === 'failure') containerClass += " shadow-[0_0_40px_rgba(239,68,68,0.3)] border border-red-500 bg-red-900/20";

    return (
        <div ref={cardRef} className={`${containerClass} scroll-mb-48`}>
            {set.isAmrap && !cardAnimation && <div className="absolute inset-0 bg-yellow-500/5 z-0 animate-pulse pointer-events-none"></div>}
            {isStagnant && <div className="bg-red-900/20 border border-red-500/30 p-2 mb-2 rounded text-center text-xs text-red-300 font-bold flex items-center justify-center gap-2 mx-2 mt-2"><AlertTriangleIcon size={14}/> Estancamiento Detectado (3 sesiones)</div>}

            {isUnilateral && (
                <div className="flex bg-slate-800 p-1 rounded-lg mb-2 relative z-10 shrink-0 mx-2">
                    <button onClick={() => setActiveSide('left')} className={`flex-1 py-1.5 text-xs font-bold rounded-md transition-all ${activeSide === 'left' ? 'bg-primary-color text-white' : 'text-slate-400'}`}>IZQUIERDA</button>
                    <button onClick={() => setActiveSide('right')} className={`flex-1 py-1.5 text-xs font-bold rounded-md transition-all ${activeSide === 'right' ? 'bg-primary-color text-white' : 'text-slate-400'}`}>DERECHA</button>
                </div>
            )}
            
            <GhostSetInfo exerciseId={(exercise.exerciseDbId || exercise.id) as string} setIndex={setIndex} history={history} settings={settings} />
            
            {showFailureWarning && <div className="mx-2 mb-2 bg-orange-900/30 border border-orange-500/50 p-3 rounded-xl animate-fade-in relative z-10 text-center"><h4 className="text-orange-400 font-bold text-sm mb-1">¿Fallo Anticipado?</h4><div className="flex gap-2"><button onClick={() => setShowFailureWarning(false)} className="flex-1 py-2 bg-slate-800 rounded text-xs font-bold">Corregir</button><button onClick={() => onLogSet()} className="flex-1 py-2 bg-orange-600 rounded text-xs font-bold text-white">Sí, Guardar</button></div></div>}

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
                                <span className="text-[10px] font-mono font-black text-red-400">
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

                <div className="grid grid-cols-[1fr_auto_1fr] gap-3 items-center">
                    <div className={`flex flex-col rounded-xl overflow-hidden border shadow-sm relative transition-colors duration-300 ${inputActiveColor}`}>
                         <button 
                            onClick={() => !isTimeMode && setRepInputMode(prev => prev === 'standard' ? 'partial' : 'standard')} 
                            className="text-[9px] uppercase font-bold tracking-widest py-1.5 text-center bg-black/20 hover:bg-black/40 transition-colors"
                        >
                            {isTimeMode ? 'Segundos' : (repInputMode === 'standard' ? 'Reps Reales' : 'Parciales')}
                        </button>
                        <div className="relative flex-1 py-1 flex items-center justify-center">
                             <input 
                                type="number" 
                                inputMode="numeric" 
                                value={isTimeMode ? safeInputs.duration : (repInputMode === 'standard' ? safeInputs.reps : safeInputs.partialReps)} 
                                onChange={e => { 
                                    const val = e.target.value; 
                                    if (isTimeMode) {
                                        onInputChange('duration', val, isUnilateral ? activeSide : undefined);
                                    } else {
                                        onInputChange(repInputMode === 'standard' ? 'reps' : 'partialReps', val, isUnilateral ? activeSide : undefined); 
                                        if (repInputMode === 'partial' && parseFloat(val) > 0) { onInputChange('isPartial', true, isUnilateral ? activeSide : undefined); } 
                                    }
                                }} 
                                className="w-full text-center bg-transparent border-none text-3xl font-black focus:ring-0 p-0 text-inherit placeholder-white/20" 
                                placeholder="0" 
                             />
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
                                <div className="relative w-full">
                                     <input type="number" inputMode="decimal" step="0.5" value={safeInputs.weight} onChange={e => onInputChange('weight', e.target.value, isUnilateral ? activeSide : undefined)} className={`w-full text-center bg-transparent border-none text-3xl font-black focus:ring-0 p-0 placeholder-slate-600 ${isWeightWarning ? 'text-red-400' : 'text-white'}`} placeholder="0" />
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
                    <div className={`flex justify-center items-center py-3 rounded-lg border w-full ${set.isCalibrator ? 'bg-yellow-900/30 border-yellow-500 text-yellow-400' : 'bg-orange-900/20 border-orange-500 text-orange-400'}`}>
                        <span className="text-xs font-black uppercase tracking-[0.2em] flex items-center gap-2">
                            <FlameIcon size={14} />
                            {set.isCalibrator ? "AMRAP CALIBRADOR" : "AMRAP AISLADO"}
                        </span>
                    </div>
                ) : (
                    <div className="flex justify-center items-center gap-2">
                        <button onClick={() => handlePerformanceModeChange('target')} className={`flex-1 py-2 rounded-lg border text-[10px] font-black uppercase transition-all ${safeInputs.performanceMode === 'target' ? 'bg-primary-color text-white border-primary-color shadow-lg' : 'bg-slate-800 border-slate-700 text-slate-500 hover:text-white'}`}>
                            {(set.intensityMode === 'rir' || settings.intensityMetric === 'rir') ? 'RIR' : 'RPE'}
                        </button>
                        <button onClick={() => handlePerformanceModeChange('failure')} className={`flex-1 py-2 rounded-lg border text-[10px] font-black uppercase transition-all flex items-center justify-center gap-1 ${safeInputs.performanceMode === 'failure' ? 'bg-red-600 text-white border-red-500 shadow-lg shadow-red-900/50' : 'bg-slate-800 border-slate-700 text-slate-500 hover:text-red-400'}`}>
                            <FlameIcon size={10} /> Fallo
                        </button>
                        <button onClick={() => handlePerformanceModeChange('failed')} className={`flex-1 py-2 rounded-lg border text-[10px] font-black uppercase transition-all flex items-center justify-center gap-1 ${safeInputs.performanceMode === 'failed' ? 'bg-yellow-600 text-white border-yellow-500' : 'bg-slate-800 border-slate-700 text-slate-500 hover:text-yellow-400'}`}>
                            <AlertTriangleIcon size={10} /> Fallido
                        </button>
                    </div>
                )}

                {safeInputs.performanceMode === 'target' && (
                    <div className="flex justify-center animate-fade-in relative">
                         {(set.intensityMode === 'rir' || settings.intensityMetric === 'rir') ? (
                             <div className={`flex items-center rounded-lg p-1.5 border w-32 justify-between transition-colors ${intensityContainerClass}`}>
                                 <span className="text-slate-500 font-bold text-xs uppercase px-2">RIR</span>
                                 <input type="number" step="1" value={safeInputs.rir} onChange={e => onInputChange('rir', e.target.value, isUnilateral ? activeSide : undefined)} className="w-12 bg-transparent border-none text-center font-bold text-white focus:ring-0 p-0 text-lg" placeholder="-"/>
                             </div>
                         ) : (
                             <div className={`flex items-center rounded-lg p-1.5 border w-32 justify-between transition-colors ${intensityContainerClass}`}>
                                 <span className="text-slate-500 font-bold text-xs uppercase px-2">RPE</span>
                                 <input type="number" step="0.5" value={safeInputs.rpe} onChange={e => onInputChange('rpe', e.target.value, isUnilateral ? activeSide : undefined)} className="w-12 bg-transparent border-none text-center font-bold text-white focus:ring-0 p-0 text-lg" placeholder="-"/>
                             </div>
                         )}
                    </div>
                )}

                {/* Dropset / Rest-Pause: botones visibles + filas expandibles */}
                <div className="mx-2 mt-2 flex gap-2">
                    <button onClick={() => onInputChange('dropSets', [...(safeInputs.dropSets || []), { weight: 0, reps: 0 }], isUnilateral ? activeSide : undefined)} className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg border border-orange-500/30 text-[10px] font-black uppercase bg-orange-500/10 text-orange-400 hover:bg-orange-500/20 hover:border-orange-500/50 transition-colors">
                        <PlusIcon size={12} /> Dropset {(safeInputs.dropSets?.length || 0) > 0 && <span className="bg-orange-500/30 px-1.5 py-0.5 rounded text-[9px]">{(safeInputs.dropSets?.length || 0)}</span>}
                    </button>
                    <button onClick={() => onInputChange('restPauses', [...(safeInputs.restPauses || []), { restTime: 15, reps: 0 }], isUnilateral ? activeSide : undefined)} className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg border border-sky-500/30 text-[10px] font-black uppercase bg-sky-500/10 text-sky-400 hover:bg-sky-500/20 hover:border-sky-500/50 transition-colors">
                        <PlusIcon size={12} /> Rest-Pause {(safeInputs.restPauses?.length || 0) > 0 && <span className="bg-sky-500/30 px-1.5 py-0.5 rounded text-[9px]">{(safeInputs.restPauses?.length || 0)}</span>}
                    </button>
                </div>
                {(safeInputs.dropSets?.length || 0) > 0 || (safeInputs.restPauses?.length || 0) > 0 ? (
                    <div className="mx-2 mt-2 p-3 bg-slate-900/50 border border-orange-500/20 rounded-xl space-y-3 animate-fade-in">
                        {(safeInputs.dropSets || []).map((ds, i) => (
                            <div key={`ds-${i}`} className="flex gap-2 items-center">
                                <span className="text-[9px] font-mono text-orange-500/80 w-14">Dropset</span>
                                <input type="number" step="0.5" placeholder="Peso" value={ds.weight || ''} onChange={e => { const arr = [...(safeInputs.dropSets || [])]; arr[i] = { ...arr[i], weight: parseFloat(e.target.value) || 0, reps: arr[i].reps }; onInputChange('dropSets', arr, isUnilateral ? activeSide : undefined); }} className="flex-1 bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-xs text-white" />
                                <input type="number" placeholder="Reps" value={ds.reps || ''} onChange={e => { const arr = [...(safeInputs.dropSets || [])]; arr[i] = { ...arr[i], weight: arr[i].weight, reps: parseInt(e.target.value, 10) || 0 }; onInputChange('dropSets', arr, isUnilateral ? activeSide : undefined); }} className="w-14 bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-xs text-white" />
                                <button onClick={() => onInputChange('dropSets', (safeInputs.dropSets || []).filter((_, j) => j !== i), isUnilateral ? activeSide : undefined)} className="p-1.5 rounded bg-red-900/50 text-red-400 hover:bg-red-800/50"><MinusIcon size={12}/></button>
                            </div>
                        ))}
                        {(safeInputs.restPauses || []).map((rp, i) => (
                            <div key={`rp-${i}`} className="flex gap-2 items-center">
                                <span className="text-[9px] font-mono text-sky-500/80 w-14">Rest-Pause</span>
                                <input type="number" placeholder="Descanso (s)" value={rp.restTime || ''} onChange={e => { const arr = [...(safeInputs.restPauses || [])]; arr[i] = { ...arr[i], restTime: parseInt(e.target.value, 10) || 0, reps: arr[i].reps }; onInputChange('restPauses', arr, isUnilateral ? activeSide : undefined); }} className="flex-1 bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-xs text-white" />
                                <input type="number" placeholder="Reps" value={rp.reps || ''} onChange={e => { const arr = [...(safeInputs.restPauses || [])]; arr[i] = { ...arr[i], restTime: arr[i].restTime, reps: parseInt(e.target.value, 10) || 0 }; onInputChange('restPauses', arr, isUnilateral ? activeSide : undefined); }} className="w-14 bg-slate-800 border border-slate-600 rounded px-2 py-1.5 text-xs text-white" />
                                <button onClick={() => onInputChange('restPauses', (safeInputs.restPauses || []).filter((_, j) => j !== i), isUnilateral ? activeSide : undefined)} className="p-1.5 rounded bg-red-900/50 text-red-400 hover:bg-red-800/50"><MinusIcon size={12}/></button>
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
                <Modal isOpen={showFailedModal} onClose={() => setShowFailedModal(false)} title="Serie Fallida">
                    <div className="space-y-4 p-2">
                        <p className="text-sm text-slate-300 text-center mb-4">No se pudo completar ninguna repetición. ¿Cuál fue la causa?</p>
                        <div className="space-y-2">
                             <Button onClick={() => handleFailedSet('Dolor / Lesión')} variant="danger" className="w-full">Dolor / Molestia</Button>
                             <Button onClick={() => handleFailedSet('Peso Excesivo')} variant="secondary" className="w-full text-orange-400">Carga Excesiva</Button>
                             <Button onClick={() => handleFailedSet('Fallo Técnico')} variant="secondary" className="w-full">Fallo Técnico</Button>
                        </div>
                    </div>
                </Modal>
            )}

            <div className="mt-4 px-2">
                <Button onClick={handleLogAttempt} className={`w-full !py-4 !text-sm !font-black uppercase tracking-widest shadow-xl transition-all active:scale-[0.98] ${isLogged ? 'bg-green-600 hover:bg-green-500' : set.isAmrap ? 'bg-yellow-600 hover:bg-yellow-500 text-black' : ''}`}>
                    {isLogged ? 'ACTUALIZAR SERIE' : set.isAmrap ? 'GUARDAR AMRAP' : 'GUARDAR SERIE'}
                </Button>
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

export const WorkoutSession: React.FC<WorkoutSessionProps> = ({ session, program, programId, settings, history, onFinish, onCancel, onUpdateExercise1RM, isFinishModalOpen, setIsFinishModalOpen, exerciseList, onUpdateSessionInProgram, isTimeSaverModalOpen, setIsTimeSaverModalOpen, onUpdateExerciseInProgram: updateExInProg, isTimersModalOpen, setIsTimersModalOpen }) => {
    const { ongoingWorkout, isLiveCoachActive, isOnline, muscleHierarchy, restTimer } = useAppState();
    const dispatch = useAppDispatch();
    useKeyboardOverlayMode(true);
    const { setOngoingWorkout, handleStartRest, handleSkipRestTimer, addToast, setIsLiveCoachActive, addOrUpdateCustomExercise } = dispatch;
    
    const [isFocusMode, setIsFocusMode] = useState(true); 
    const [isSkippingRest, setIsSkippingRest] = useState(false); // CANDADO DE SEGURIDAD
    const [currentSession, setCurrentSession] = useState<Session>(ongoingWorkout?.session || session);
    const [startTime] = useState(ongoingWorkout?.startTime || Date.now());
    const [duration, setDuration] = useState(0);
    const [activeMode] = useState<'A' | 'B' | 'C' | 'D'>(ongoingWorkout?.activeMode || 'A');
    const [isCompact, setIsCompact] = useState(false);
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
    const isFinishingRef = useRef(false);

    const setsContainerRefs = useRef<Record<string, HTMLDivElement | null>>({});

    useEffect(() => {
        const main = document.querySelector('.app-main-content');
        if (!main) return;
        const handleScroll = () => { setIsCompact(main.scrollTop > 40); };
        main.addEventListener('scroll', handleScroll);
        return () => main.removeEventListener('scroll', handleScroll);
    }, []);

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
            return sessionForMode.parts.flatMap((p: any) => p.exercises.map((e: any) => e as Exercise[]));
        }
        return (sessionForMode.exercises as Exercise[]) || [];
    }, [sessionForMode]);

    const [activeExerciseId, setActiveExerciseId] = useState<string | null>((ongoingWorkout?.activeExerciseId as any) || allExercises[0]?.id || null);
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
                    consolidatedWeights
                };
            });
        }
    }, [completedSets, exerciseFeedback, setInputs, selectedTags, activeExerciseId, activeSetId, setOngoingWorkout, exerciseHeartRates, consolidatedWeights]);


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
        let setDataToSave: { left: OngoingSetData | null, right: OngoingSetData | null } = { left: null, right: null };
        const processInput = (inp: SetInputState): OngoingSetData | null => {
            const weight = parseFloat(String(inp.weight));
            if (isNaN(weight) && inp.performanceMode !== 'failed' && !inp.duration && !inp.reps) return null; 
            return { weight: isNaN(weight) ? 0 : weight, reps: parseInt(inp.reps, 10) || undefined, rpe: inp.rpe ? parseFloat(inp.rpe) : undefined, rir: inp.rir ? parseInt(inp.rir, 10) : undefined, isFailure: inp.isFailure || inp.performanceMode === 'failure' || set.isAmrap, isIneffective: inp.isIneffective || inp.performanceMode === 'failed', duration: parseInt(inp.duration || '0', 10) || undefined, machineBrand: selectedTags[exercise.id], isPartial: inp.isPartial || (parseInt(inp.partialReps, 10) > 0), performanceMode: inp.performanceMode, partialReps: parseInt(inp.partialReps, 10) || 0, dropSets: inp.dropSets, restPauses: inp.restPauses, discomfortNotes: inp.discomfortNotes, isAmrap: set.isAmrap };
        };
        if (isUnilateral) { const ui = inputData as UnilateralSetInputs; setDataToSave.left = processInput(ui.left); setDataToSave.right = processInput(ui.right); }
        else setDataToSave.left = processInput(inputData as SetInputState);
        
        if (setDataToSave.left || setDataToSave.right) {
            const primaryData = isUnilateral ? (setDataToSave.left || setDataToSave.right)! : setDataToSave.left!;
            
            let adaptiveRestTime = exercise.restTime || 90;
            let addedRest = 0;
            
            // Calculamos RPE efectivo en la UI usando la misma lógica que AUGE para escalar el descanso
            let uiEffectiveRpe = primaryData.rpe || set.targetRPE || 7;
            if (primaryData.isFailure || primaryData.performanceMode === 'failure' || set.isAmrap) uiEffectiveRpe = Math.max(uiEffectiveRpe, 11);
            if (primaryData.dropSets && primaryData.dropSets.length > 0) uiEffectiveRpe += primaryData.dropSets.length * 1.5;
            if (primaryData.restPauses && primaryData.restPauses.length > 0) uiEffectiveRpe += primaryData.restPauses.length * 1.0;
            if (primaryData.partialReps && primaryData.partialReps > 0) uiEffectiveRpe += 0.5;

            const targetRPE = set.targetRPE || 8;

            if (primaryData.performanceMode === 'failed') {
                addedRest = 60; // Fallo total requiere tiempo substancial para purgar lactato
                addToast("Fallo técnico o muscular total. Descanso de emergencia +60s.", "suggestion");
                setSetCardAnimations(prev => ({...prev, [set.id]: 'failure'}));
                hapticNotification(NotificationType.ERROR);
            } else if (uiEffectiveRpe >= 10 || uiEffectiveRpe > targetRPE) {
                // Exceso de intensidad sobre zona óptima (Target 8, Exceso empieza en 9)
                const rpeExcess = Math.max(0, uiEffectiveRpe - Math.min(targetRPE, 9)); 
                const fatigueFactor = settings.algorithmSettings?.failureFatigueFactor ?? 1.25;
                
                // Crecimiento proporcional: 25s base por cada punto de exceso * factor de fatiga
                addedRest = Math.round(rpeExcess * 25 * fatigueFactor); 
                addedRest = Math.min(addedRest, 120); // Hard Cap de 2 minutos máximos adicionales

                if (addedRest > 0) {
                    addToast(`Intensidad Extrema detectada. Compensando SNC: +${addedRest}s de descanso.`, "suggestion");
                }
                
                const target = (exercise.trainingMode === 'time') ? (set.targetDuration || 0) : (set.targetReps || 0);
                const actual = (exercise.trainingMode === 'time') ? (primaryData.duration || 0) : (primaryData.reps || 0);
                if (actual >= target) setSetCardAnimations(prev => ({...prev, [set.id]: set.isAmrap ? 'amrap' : 'success'}));
                else setSetCardAnimations(prev => ({...prev, [set.id]: null}));

            } else {
                const target = (exercise.trainingMode === 'time') ? (set.targetDuration || 0) : (set.targetReps || 0);
                const actual = (exercise.trainingMode === 'time') ? (primaryData.duration || 0) : (primaryData.reps || 0);
                if (actual >= target) setSetCardAnimations(prev => ({...prev, [set.id]: set.isAmrap ? 'amrap' : 'success'}));
                else setSetCardAnimations(prev => ({...prev, [set.id]: null}));
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
            
            if (adaptiveRestTime > 0 && !isUnilateral) handleStartRest(adaptiveRestTime, exercise.name);
        }
    }, [setInputs, selectedTags, handleStartRest, moveToNextSet, addToast, settings.algorithmSettings, settings.weightUnit, completedSets, exerciseFeedback, allExercises, program.mode, (program as any).goals, exerciseList]);

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

        const currentExIndex = allExercises.findIndex(e => e.id === activeExerciseId);
        const isLast = currentExIndex === allExercises.length - 1;

        if (isLast) {
            setIsFinishModalOpen(true);
        } else {
            const nextEx = allExercises[currentExIndex + 1];
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

    const handleFinishSession = (notes?: string, discomforts?: string[], fatigueLevel?: number, mentalClarity?: number, durationInMinutes?: number, logDate?: string, photoUri?: string, planDeviations?: PlanDeviation[], focus?: number, pump?: number, environmentTags?: string[], sessionDifficulty?: number, planAdherenceTags?: string[]) => {
        isFinishingRef.current = true; // Block ongoing updates to prevent ghost session
        
        const completedPayload: CompletedExercise[] = (allExercises as Exercise[]).map((ex: Exercise): CompletedExercise | null => {
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
        onFinish(completedPayload, durationInMinutes ? durationInMinutes * 60 : duration, notes, discomforts, fatigueLevel, mentalClarity, logDate, undefined, planDeviations, focus, pump, environmentTags, sessionDifficulty, planAdherenceTags);
    };

    const handleHeaderClick = (exId: string) => {
        hapticImpact(ImpactStyle.Light);
        setActiveExerciseId(exId);
    };

    return (
        <div className={`pb-32 ${isFocusMode ? 'focus-mode-active' : ''} transition-colors duration-700`}>
             <FinishWorkoutModal isOpen={isFinishModalOpen} onClose={() => setIsFinishModalOpen(false)} onFinish={handleFinishSession} initialDurationInSeconds={duration} initialNotes={sessionNotes} asDrawer />
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
                        onClose={() => handleFinishFeedback(ex.id, { jointLoad: 5, technicalQuality: 8, perceivedFatigue: 5 })}
                        exerciseName={ex.name}
                        onSave={(feedback) => handleFinishFeedback(ex.id, feedback)}
                    />
                ) : null;
            })()}
            {historyModalExercise && <ExerciseHistoryModal exercise={historyModalExercise} programId={programId} history={history} settings={settings} onClose={() => setHistoryModalExercise(null)} />}
            
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
                background={currentSession.background} 
                coverStyle={currentSession.coverStyle}
                isFocusMode={isFocusMode} 
                onToggleFocusMode={() => setIsFocusMode(!isFocusMode)} 
                isLiveCoachActive={isLiveCoachActive} 
                onToggleLiveCoach={() => setIsLiveCoachActive(!isLiveCoachActive)} 
                isCompact={isCompact} 
                activePartName={activePartInfo?.name} 
                isResting={!!(restTimer && restTimer.remaining > 0)} 
                zenMode={settings.enableZenMode} 
                liveBatteryDrain={liveBatteryDrain}
                adaptiveCache={adaptiveCache}
            />
            
            <div className="mt-4 px-2 sm:px-4 space-y-10 relative">
            {renderExercises.map((part: any, partIndex: number) => {
                    const theme = getPartTheme(part.name || '');
                    return (
                    <details key={part.id || partIndex} open={!collapsedParts[part.id]} className="group">
                        <summary onClick={(e) => { e.preventDefault(); setCollapsedParts(prev => ({...prev, [part.id]: !prev[part.id]})); }} className="flex items-center justify-between mb-4 px-3 py-2 cursor-pointer list-none rounded-xl border border-white/5 shadow-lg" style={{ borderLeft: `4px solid ${theme.color}`, background: `linear-gradient(90deg, ${theme.bgColor} 0%, transparent 100%)` }}>
                            <div className="flex items-center gap-3"><span className="text-xl" role="img">{theme.icon}</span><h3 className="text-sm font-black uppercase tracking-widest" style={{ color: theme.color }}>{part.name || 'Sesión'}</h3></div><ChevronRightIcon className={`text-slate-500 transition-transform ${collapsedParts[part.id] ? '' : 'rotate-90'}`} size={16} />
                        </summary>
                        <div className="space-y-4 pl-1 relative">
                            {part.exercises.map((ex: Exercise) => {
                                const exInfo = exerciseList.find(e => e.id === ex.exerciseDbId);
                                const pr = findPrForExercise(exInfo || ({} as any), history, settings, selectedTags[ex.id]);
                                const isActive = activeExerciseId === ex.id;
                                const isFocused = focusExerciseId === ex.id;
                                
                                // Pagination Dots Calculation
                                const hasWarmup = ex.warmupSets && ex.warmupSets.length > 0;
                                const totalSlides = (hasWarmup ? 1 : 0) + ex.sets.length + 1; // +1 for Feedback
                                let activeSlideIndex = 0;
                                
                                if (activeSetId) {
                                    if (activeSetId === `feedback-${ex.id}`) {
                                        activeSlideIndex = totalSlides - 1;
                                    } else {
                                        const setIndex = ex.sets.findIndex(s => s.id === activeSetId);
                                        if (setIndex !== -1) {
                                            activeSlideIndex = (hasWarmup ? 1 : 0) + setIndex;
                                        } else if (hasWarmup && activeSetId.startsWith('warmup-')) { 
                                            activeSlideIndex = 0;
                                        }
                                    }
                                }

                                return (
                                    <div key={ex.id} className="relative transition-all duration-500" id={`exercise-card-${ex.id}`}>
                                        <details 
                                            open={isActive} 
                                            className={`set-card-details w-full overflow-visible transition-all duration-500 ${ex.isStarTarget ? 'border-amber-400/50' : ''} ${isFocused ? 'z-40 ring-2 ring-yellow-500/50 shadow-[0_0_40px_rgba(234,179,8,0.2)]' : ''}`} 
                                            onToggle={(e) => { if ((e.target as HTMLDetailsElement).open) setActiveExerciseId(ex.id)}}
                                            style={{ boxShadow: isActive && !isFocused ? `0 0 30px ${theme.color}20` : undefined, borderColor: isActive && !isFocused ? theme.borderColor : undefined }}
                                        >
                                            <summary className="set-card-summary p-3 flex flex-col items-stretch" onClick={() => handleHeaderClick(ex.id)}>
                                                <div className="flex items-center justify-between gap-3 w-full">
                                                    <div className="flex items-center gap-3 min-w-0 flex-1">
                                                        <div className={`w-8 h-8 rounded-lg flex items-center justify-center font-black text-lg flex-shrink-0 ${ex.isStarTarget ? 'bg-amber-400 text-black' : 'bg-slate-800 text-slate-400'}`}>
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
                                            <div className={`set-card-content !border-none !p-2 space-y-2 relative`}>
                                                {pr && <div className="p-2 text-center text-sm bg-yellow-900/30 text-yellow-300 rounded-lg font-bold"><p className="font-semibold flex items-center justify-center gap-2"><TrophyIcon size={16}/> {pr.prString}</p></div>}
                                                
                                                <HeaderAccordion 
                                                    exercise={ex} 
                                                    exerciseInfo={exInfo} 
                                                    selectedTag={selectedTags[ex.id]} 
                                                    onTagChange={(tag) => setSelectedTags(prev => ({...prev, [ex.id]: tag}))} 
                                                />

                                                {/* Wizard: un set a la vez con flechas */}
                                                <div className="space-y-3" ref={(el) => { setsContainerRefs.current[ex.id] = el; }}>
                                                    <div className="flex items-center justify-between px-1">
                                                        <button 
                                                            onClick={() => {
                                                                if (activeSlideIndex > 0) {
                                                                    const prevIdx = activeSlideIndex - 1;
                                                                    if (hasWarmup && prevIdx === 0) setActiveSetId(`warmup-${ex.id}`);
                                                                    else if (prevIdx === totalSlides - 1) setActiveSetId(`feedback-${ex.id}`);
                                                                    else setActiveSetId(ex.sets[prevIdx - (hasWarmup ? 1 : 0)].id);
                                                                    scrollToId(ex.id, prevIdx === 0 && hasWarmup ? `warmup-card-${ex.id}` : prevIdx === totalSlides - 1 ? `feedback-card-${ex.id}` : `set-card-${ex.sets[prevIdx - (hasWarmup ? 1 : 0)].id}`);
                                                                }
                                                            }}
                                                            disabled={activeSlideIndex === 0}
                                                            className="p-2 rounded-lg border border-orange-500/20 text-slate-400 hover:text-white hover:border-orange-500/40 disabled:opacity-30 disabled:pointer-events-none transition-colors"
                                                        >
                                                            <ChevronLeftIcon size={20} />
                                                        </button>
                                                        <span className="text-[10px] font-mono font-black uppercase tracking-widest text-slate-500">
                                                            {activeSlideIndex === 0 && hasWarmup ? 'Calentamiento' : activeSlideIndex === totalSlides - 1 ? 'Feedback' : `Set ${activeSlideIndex - (hasWarmup ? 1 : 0) + 1}/${ex.sets.length}`}
                                                        </span>
                                                        <button 
                                                            onClick={() => {
                                                                if (activeSlideIndex < totalSlides - 1) {
                                                                    const nextIdx = activeSlideIndex + 1;
                                                                    if (hasWarmup && nextIdx === 0) setActiveSetId(`warmup-${ex.id}`);
                                                                    else if (nextIdx === totalSlides - 1) setActiveSetId(`feedback-${ex.id}`);
                                                                    else setActiveSetId(ex.sets[nextIdx - (hasWarmup ? 1 : 0)].id);
                                                                    scrollToId(ex.id, nextIdx === totalSlides - 1 ? `feedback-card-${ex.id}` : `set-card-${ex.sets[nextIdx - (hasWarmup ? 1 : 0)].id}`);
                                                                }
                                                            }}
                                                            disabled={activeSlideIndex === totalSlides - 1}
                                                            className="p-2 rounded-lg border border-orange-500/20 text-slate-400 hover:text-white hover:border-orange-500/40 disabled:opacity-30 disabled:pointer-events-none transition-colors"
                                                        >
                                                            <ChevronRightIcon size={20} />
                                                        </button>
                                                    </div>

                                                    <div className="min-h-[280px] overflow-hidden">
                                                        {activeSlideIndex === 0 && hasWarmup && (
                                                            <div id={`warmup-card-${ex.id}`} className="animate-fade-in">
                                                                <button onClick={() => setActiveSetId(`warmup-${ex.id}`)} className="w-full p-6 bg-slate-950/80 border border-sky-500/30 rounded-xl flex flex-col items-center justify-center gap-3 hover:border-sky-500/50 transition-colors min-h-[260px]">
                                                                    <FlameIcon size={40} className="text-sky-400" />
                                                                    <span className="text-[10px] font-mono font-black uppercase tracking-widest text-sky-400">Calentamiento</span>
                                                                    <span className="text-xs text-slate-500">Toca para abrir</span>
                                                                </button>
                                                            </div>
                                                        )}
                                                        {ex.sets.map((set: ExerciseSet, setIndex) => {
                                                            const slideIdx = (hasWarmup ? 1 : 0) + setIndex;
                                                            if (activeSlideIndex !== slideIdx) return null;
                                                            return (
                                                                <div key={set.id} id={`set-card-${set.id}`} className="animate-fade-in">
                                                                    <div className={`set-card-details !bg-panel-color overflow-hidden rounded-xl border border-slate-700 ${activeSetId === set.id ? 'ring-1 ring-primary-color' : ''}`}>
                                                                        <div className="p-3 bg-slate-900/80 border-b border-slate-800 flex justify-between items-center">
                                                                            <div className="flex items-center gap-2"><p className="font-bold text-slate-400 text-xs uppercase">Serie {setIndex + 1}</p>{completedSets[set.id as string] && <CheckCircleIcon size={18} className="text-green-400" />}</div>
                                                                            <p className="text-xs text-slate-500 font-mono">{formatSetTarget(set, ex, settings)}</p>
                                                                        </div>
                                                                        <SetDetails 
                                                                            exercise={ex} 
                                                                            exerciseInfo={exInfo} 
                                                                            set={set} 
                                                                            setIndex={setIndex} 
                                                                            settings={settings} 
                                                                            inputs={(setInputs[String(set.id)] as SetInputState) || { reps: '', weight: '', rpe: '', rir: '', isFailure: false, duration: '', partialReps: '' }} 
                                                                            onInputChange={(field, value, side) => handleSetInputChange(String(set.id), field as keyof SetInputState, value, side)} 
                                                                            onLogSet={(isCalibrator) => handleLogSet(ex, set, isCalibrator)} 
                                                                            isLogged={!!completedSets[set.id as string]} 
                                                                            history={history} 
                                                                            currentSession1RM={sessionAdjusted1RMs[ex.id]} 
                                                                            base1RM={exInfo?.calculated1RM || ex.reference1RM} 
                                                                            isCalibrated={!!sessionAdjusted1RMs[ex.id]} 
                                                                            cardAnimation={setCardAnimations[String(set.id)]} 
                                                                            addToast={addToast}
                                                                            suggestedWeight={getWeightSuggestionForSet(ex, exInfo, setIndex, ex.sets.slice(0, setIndex).map(s => { const d = completedSets[String(s.id)] as { left?: { weight?: number; reps?: number; machineBrand?: string }; right?: { weight?: number; reps?: number } } | undefined; if (!d) return { weight: 0 }; const p = ex.isUnilateral ? (d.left || d.right) : d.left; return p ? { weight: p.weight || 0, reps: p.reps, machineBrand: p.machineBrand } : { weight: 0 }; }), settings, history, selectedTags[ex.id], sessionAdjusted1RMs[ex.id])}
                                                                            selectedTag={selectedTags[ex.id]}
                                                                        />
                                                                    </div>
                                                                </div>
                                                            );
                                                        })}
                                                        {activeSlideIndex === totalSlides - 1 && (
                                                            <div id={`feedback-card-${ex.id}`} className="animate-fade-in">
                                                                <button onClick={() => setActiveSetId(`feedback-${ex.id}`)} className="w-full p-6 bg-slate-950/80 border border-orange-500/20 rounded-xl flex flex-col items-center justify-center gap-3 hover:border-orange-500/40 transition-colors min-h-[260px]">
                                                                    <ActivityIcon size={40} className="text-orange-500/80" />
                                                                    <span className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90">Feedback Post-Ejercicio</span>
                                                                    <span className="text-xs text-slate-500">Toca para abrir</span>
                                                                </button>
                                                            </div>
                                                        )}
                                                    </div>
                                                
                                                    <div className="flex justify-center gap-1.5 mt-2 mb-1">
                                                        {Array.from({ length: totalSlides }).map((_, i) => (
                                                            <button 
                                                                key={i} 
                                                                onClick={() => {
                                                                    if (i === 0 && hasWarmup) setActiveSetId(`warmup-${ex.id}`);
                                                                    else if (i === totalSlides - 1) setActiveSetId(`feedback-${ex.id}`);
                                                                    else setActiveSetId(ex.sets[i - (hasWarmup ? 1 : 0)].id);
                                                                    scrollToId(ex.id, i === 0 && hasWarmup ? `warmup-card-${ex.id}` : i === totalSlides - 1 ? `feedback-card-${ex.id}` : `set-card-${ex.sets[i - (hasWarmup ? 1 : 0)].id}`);
                                                                }}
                                                                className={`h-1.5 rounded-full transition-all duration-300 ${i === activeSlideIndex ? 'w-4 bg-primary-color' : 'w-1.5 bg-slate-700 hover:bg-slate-600'}`}
                                                            />
                                                        ))}
                                                    </div>
                                                </div>
                                            </div>
                                        </details>
                                    </div>
                                )
                            })}
                        </div>
                    </details>
                )})}
            </div>

            {/* Barra de acciones rápidas */}
            <div className="fixed left-0 right-0 bottom-[max(75px,calc(75px+env(safe-area-inset-bottom)))] z-20 px-4 py-2 bg-[#0a0a0a]/95 backdrop-blur-md border-t border-orange-500/20 flex justify-center gap-3">
                <button onClick={() => { const ex = allExercises.find(e => e.id === activeExerciseId); if (ex) handleFinishFeedback(ex.id, { jointLoad: 5, technicalQuality: 8, perceivedFatigue: 5 }); addToast('Ejercicio saltado', 'info'); }} className="flex items-center gap-2 px-4 py-2 rounded-xl border border-orange-500/30 text-[10px] font-mono font-black uppercase tracking-widest text-orange-400 hover:bg-orange-500/10 transition-colors">
                    <ChevronRightIcon size={14} /> Saltar
                </button>
                <button onClick={() => handleStartRest(90, 'Descanso')} className="flex items-center gap-2 px-4 py-2 rounded-xl border border-sky-500/30 text-[10px] font-mono font-black uppercase tracking-widest text-sky-400 hover:bg-sky-500/10 transition-colors">
                    <ClockIcon size={14} /> 90s
                </button>
                <button onClick={() => setShowNotesDrawer(true)} className="flex items-center gap-2 px-4 py-2 rounded-xl border border-slate-500/30 text-[10px] font-mono font-black uppercase tracking-widest text-slate-400 hover:bg-slate-500/10 transition-colors">
                    <PencilIcon size={14} /> Notas
                </button>
            </div>

            {showNotesDrawer && (
                <WorkoutDrawer isOpen={true} onClose={() => setShowNotesDrawer(false)} title="Notas de Sesión" height="50vh">
                    <div className="p-5">
                        <textarea value={sessionNotes} onChange={(e) => setSessionNotes(e.target.value)} placeholder="Notas rápidas durante el entrenamiento..." rows={6} className="w-full bg-[#0d0d0d] border border-orange-500/20 rounded-xl p-4 text-white text-sm font-mono placeholder-slate-500 focus:border-orange-500/50 outline-none" />
                        <p className="text-[9px] text-slate-500 mt-2 font-mono">Se incluirán al finalizar la sesión.</p>
                    </div>
                </WorkoutDrawer>
            )}
        </div>
    );
};