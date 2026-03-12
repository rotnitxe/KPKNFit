// components/WorkoutSession.tsx
import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import { requestPermissions, setupNotificationChannels } from '../services/notificationService';
import { Session, WorkoutLog, CompletedExercise, CompletedSet, Exercise, ExerciseSet, WarmupSetDefinition, SessionBackground, OngoingSetData, SetInputState, UnilateralSetInputs, DropSetData, RestPauseData, ExerciseMuscleInfo, Program, Settings, PlanDeviation, CoverStyle, ToastData } from '../types';
import { motion } from 'framer-motion';
import Button from './ui/Button';
import { ClockIcon, ChevronRightIcon, ChevronLeftIcon, FlameIcon, CheckCircleIcon, TrophyIcon, MinusIcon, PlusIcon, MicIcon, MicOffIcon, AlertTriangleIcon, CheckCircleIcon as CheckIcon, XCircleIcon, StarIcon, SparklesIcon, SettingsIcon, ArrowUpIcon, ArrowDownIcon, RefreshCwIcon, BrainIcon, LinkIcon, PlayIcon, PauseIcon, ActivityIcon, InfoIcon, BodyIcon, PencilIcon } from './icons';
import { playSound, preloadSounds, configureAudioSession } from '../services/soundService';
import { hapticImpact, ImpactStyle, hapticNotification, NotificationType } from '../services/hapticsService';
import { calculateWeightFrom1RM, roundWeight, getWeightSuggestionForSet } from '../utils/calculations';
import { useAppDispatch, useAppState, useAppContext } from '../contexts/AppContext';
import { calculateSpinalScore, calculatePersonalizedBatteryTanks, calculateSetBatteryDrain, getDynamicAugeMetrics } from '../services/auge';
import { normalizeMuscleGroup } from '../services/volumeCalculator';
import { getCachedAdaptiveData, AugeAdaptiveCache } from '../services/augeAdaptiveService';
import { GPFatigueCurve } from './ui/AugeDeepView';
import FinishWorkoutModal from './FinishWorkoutModal';
import ExerciseHistoryModal from './ExerciseHistoryModal';
import { AdvancedExercisePickerModal } from './AdvancedExercisePickerModal';
import TacticalModal from './ui/TacticalModal';
import { SetDetails } from './workout/SetDetails';

/** Simple Brzycki 1RM estimation */
function calculateBrzycki1RM(weight: number, reps: number): number {
    if (reps <= 0) return 0;
    if (reps === 1) return weight;
    return weight / (1.0278 - (0.0278 * reps));
}

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
import WarmupDrawer from './workout/WarmupDrawer';
import PostExerciseDrawer from './workout/PostExerciseDrawer';
import WorkoutDrawer from './workout/WorkoutDrawer';
import { FinishContextBottomSheet } from './workout/FinishContextBottomSheet';
import CardCarouselBar, { type CarouselItem, type CarouselItemType } from './workout/CardCarouselBar';
import ExerciseCardContextMenu from './workout/ExerciseCardContextMenu';
import { InCardTimer } from './workout/InCardTimer';
import { SetTimerButton } from './workout/SetTimerButton';
import { useKeyboardOverlayMode } from '../hooks/useKeyboardOverlayMode';
import ReadinessSheet from './workout/ReadinessSheet';

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
            <div className="absolute inset-0 workout-modal-backdrop" onClick={onAnimationComplete} />
            <div className="relative z-10 w-full max-w-sm overflow-hidden rounded-[32px] border border-white/75 liquid-glass-panel p-6 text-center animate-fade-in shadow-[0_24px_48px_rgba(78,56,24,0.18)]">
                <h3 className="mb-1 text-[24px] font-medium tracking-[-0.03em] text-[var(--md-sys-color-on-surface)]">
                    {isGoalMet ? 'Meta alcanzada' : 'Progreso hacia objetivo'}
                </h3>
                <p className="mb-4 text-sm text-[var(--md-sys-color-on-surface-variant)]">
                    {current1RM.toFixed(1)}{unit} / {goal1RM}{unit} ({renderProgress.toFixed(0)}%)
                </p>
                <div className="mb-6 h-2 overflow-hidden rounded-full bg-[var(--md-sys-color-secondary-container)]">
                    <div className="h-full rounded-full bg-[var(--md-sys-color-primary)] transition-all duration-500" style={{ width: `${renderProgress}%` }} />
                </div>
                <button onClick={onAnimationComplete} className="w-full rounded-full border border-[var(--md-sys-color-outline-variant)] bg-white/60 py-3 text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)] transition-colors hover:bg-white/80">
                    Cerrar
                </button>
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
    };

    const liveExerciseInfo = useMemo(() => {
        return exerciseList.find(e => e.id === exerciseInfo?.id) || exerciseInfo;
    }, [exerciseList, exerciseInfo]);

    const tags = useMemo(() => {
        const tagSet = new Set<string>();
        (exercise.brandEquivalencies || []).forEach(b => tagSet.add(b.brand));
        (liveExerciseInfo?.brandEquivalencies || []).forEach(b => tagSet.add(b.brand));
        tagSet.add('Base');
        tagSet.add('Sentado');
        tagSet.add('Parado');
        tagSet.add('Unilateral');
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

    const controlBase = 'h-[40px] px-4 rounded-full border text-[14px] font-medium transition-all inline-flex items-center justify-center gap-2';

    return (
        <div className="w-full mb-2">
            <div className="flex gap-2 mb-2 px-1">
                <button
                    onClick={() => toggleSection('tags')}
                    className={`${controlBase} flex-1 ${expandedSection === 'tags'
                        ? 'bg-[var(--md-sys-color-secondary-container)] border-[var(--md-sys-color-outline)] text-[var(--md-sys-color-on-secondary-container)]'
                        : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface-variant)] hover:border-[var(--md-sys-color-outline)]'}`}
                >
                    <BodyIcon size={14} /> {selectedTag && selectedTag !== 'Base' ? selectedTag : 'Etiquetas'} {expandedSection === 'tags' ? <ChevronRightIcon className="-rotate-90" size={12} /> : <ChevronRightIcon className="rotate-90" size={12} />}
                </button>
                <button
                    onClick={() => toggleSection('setup')}
                    className={`${controlBase} flex-1 ${expandedSection === 'setup'
                        ? 'bg-[var(--md-sys-color-secondary-container)] border-[var(--md-sys-color-outline)] text-[var(--md-sys-color-on-secondary-container)]'
                        : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface-variant)] hover:border-[var(--md-sys-color-outline)]'}`}
                >
                    <SettingsIcon size={14} /> Setup {expandedSection === 'setup' ? <ChevronRightIcon className="-rotate-90" size={12} /> : <ChevronRightIcon className="rotate-90" size={12} />}
                </button>
            </div>

            {expandedSection === 'tags' && (
                <div className="animate-fade-in bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-[16px] p-3">
                    <div className="flex flex-wrap gap-2 mb-3">
                        {tags.map(tag => (
                            <button
                                key={tag}
                                onClick={() => { onTagChange(tag); setExpandedSection(null); }}
                                className={`h-[32px] px-3 rounded-full border text-[12px] font-medium transition-all ${selectedTag === tag
                                    ? 'bg-[var(--md-sys-color-primary-container)] border-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary-container)]'
                                    : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface-variant)] hover:border-[var(--md-sys-color-outline)]'}`}
                            >
                                {tag}
                            </button>
                        ))}
                    </div>
                    {isEditingTag ? (
                        <div className="flex gap-2 items-center mt-2 animate-fade-in">
                            <input
                                type="text"
                                value={newTagName}
                                onChange={(e) => setNewTagName(e.target.value)}
                                placeholder="Nueva etiqueta"
                                className="flex-1 bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-[12px] px-3 py-2 text-sm text-[var(--md-sys-color-on-surface)] outline-none"
                                autoFocus
                            />
                            <button onClick={handleCreateTag} className="h-[40px] px-3 rounded-full bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)]"><CheckIcon size={14} /></button>
                            <button onClick={() => setIsEditingTag(false)} className="h-[40px] px-3 rounded-full bg-[var(--md-sys-color-surface-container)] text-[var(--md-sys-color-on-surface-variant)] border border-[var(--md-sys-color-outline-variant)]"><XCircleIcon size={14} /></button>
                        </div>
                    ) : (
                        <button onClick={() => setIsEditingTag(true)} className="w-full h-[40px] rounded-full border border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface-variant)] text-[12px] hover:border-[var(--md-sys-color-outline)] flex items-center justify-center gap-1">
                            <PlusIcon size={12} /> Crear nueva etiqueta
                        </button>
                    )}
                </div>
            )}

            {expandedSection === 'setup' && (
                <div className="animate-fade-in bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-[16px] p-3 space-y-3">
                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className="text-[10px] text-[var(--md-sys-color-on-surface-variant)] font-medium uppercase block mb-1">Asiento</label>
                            <input type="text" value={localDetails.seatPosition || ''} onChange={e => setLocalDetails({ ...localDetails, seatPosition: e.target.value })} className="w-full bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-[12px] px-3 py-2 text-sm text-[var(--md-sys-color-on-surface)] outline-none" />
                        </div>
                        <div>
                            <label className="text-[10px] text-[var(--md-sys-color-on-surface-variant)] font-medium uppercase block mb-1">Pines</label>
                            <input type="text" value={localDetails.pinPosition || ''} onChange={e => setLocalDetails({ ...localDetails, pinPosition: e.target.value })} className="w-full bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-[12px] px-3 py-2 text-sm text-[var(--md-sys-color-on-surface)] outline-none" />
                        </div>
                    </div>
                    <div>
                        <label className="text-[10px] text-[var(--md-sys-color-on-surface-variant)] font-medium uppercase block mb-1">Notas</label>
                        <textarea value={localDetails.equipmentNotes || ''} onChange={e => setLocalDetails({ ...localDetails, equipmentNotes: e.target.value })} rows={2} className="w-full bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-[12px] px-3 py-2 text-sm text-[var(--md-sys-color-on-surface)] outline-none" />
                    </div>
                    <button onClick={handleSaveSetup} className="w-full h-[40px] rounded-full text-[12px] uppercase font-semibold bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)]">Guardar setup</button>
                </div>
            )}
        </div>
    );
};

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
        <div className="workout-session-header sticky top-0 z-40 w-full pb-2">
            <div className="flex flex-col px-6 pt-8 pb-4">
                {/* Status Bar Space (Mock) */}
                <div className="h-6 w-full flex justify-between items-center mb-2">
                    <div className="flex items-center gap-1.5">
                        <span className="text-[12px] font-bold text-[var(--md-sys-color-on-surface)] tabular-nums">{elapsedSeconds != null ? formatTime(elapsedSeconds) : '00:00'}</span>
                        {restTimerRemaining != null && restTimerRemaining > 0 && (
                            <div className="flex items-center gap-1 px-2 py-0.5 rounded-full border border-[var(--md-sys-color-tertiary)]/20 bg-[var(--md-sys-color-tertiary-container)] text-[var(--md-sys-color-on-tertiary-container)]">
                                <ClockIcon size={10} />
                                <span className="text-[10px] font-black tabular-nums">{formatTime(restTimerRemaining)}</span>
                            </div>
                        )}
                    </div>
                    <div className="flex items-center gap-2 text-[var(--md-sys-color-on-surface-variant)]">
                        <ActivityIcon size={14} />
                        <div className="w-[18px] h-[9px] border border-[var(--md-sys-color-outline)] rounded-[2px] relative">
                            <div className="absolute left-0.5 top-0.5 bottom-0.5 bg-[var(--md-sys-color-primary)] rounded-[1px]" style={{ width: '12px' }} />
                        </div>
                    </div>
                </div>

                <div className="flex justify-between items-end gap-4 min-h-[48px]">
                    <div className="flex-1 min-w-0">
                        <h1 className="text-[32px] leading-[38px] font-medium tracking-[-0.03em] text-[var(--md-sys-color-on-surface)] truncate">{sessionName}</h1>
                        <div className="flex items-center gap-2 mt-1">
                            <span className="text-[11px] font-black uppercase tracking-[0.2em] text-[var(--md-sys-color-primary)]">
                                {isResting ? 'Descanso Activo' : (activePartName || 'Sesión en curso')}
                            </span>
                            <div className="h-1 flex-1 bg-[var(--md-sys-color-secondary-container)] rounded-full overflow-hidden max-w-[120px]">
                                <div className="h-full bg-[var(--md-sys-color-primary)] transition-all duration-500 shadow-[0_0_8px_rgba(122,93,32,0.24)]" style={{ width: `${progressPercent}%` }} />
                            </div>
                        </div>
                    </div>

                    <button
                        onPointerDown={handlePointerDown}
                        onPointerUp={handlePointerUp}
                        onPointerLeave={handlePointerUp}
                        onClick={handleClick}
                        className="w-14 h-14 rounded-full bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] border border-white/60 flex items-center justify-center shadow-[0_14px_32px_rgba(122,93,32,0.18)] active:scale-95 transition-transform"
                    >
                        <CheckCircleIcon size={28} strokeWidth={2.5} />
                    </button>
                </div>
            </div>
        </div>
    );
});

// GhostSetInfo and SetDetails have been moved to components/workout/SetDetails

interface WorkoutSessionProps {
    session: Session;
    program: Program;
    programId: string;
    settings: Settings;
    history: WorkoutLog[];
    onFinish: (completedExercises: CompletedExercise[], duration: number, notes?: string, discomforts?: string[], fatigue?: number, clarity?: number, logDate?: string, photoUri?: string, planDeviations?: PlanDeviation[], focus?: number, pump?: number, environmentTags?: string[], sessionDifficulty?: number, planAdherenceTags?: string[], muscleBatteries?: Record<string, number>) => void;
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
                ScreenOrientation.unlock().catch(() => { })
            );
        };
    }, []);

    useEffect(() => {
        document.body.classList.add('workout-theme-active');
        return () => {
            document.body.classList.remove('workout-theme-active');
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
    const [showReadiness, setShowReadiness] = useState(false);
    const [readinessData, setReadinessData] = useState<any>(null);
    const [focusExerciseId, setFocusExerciseId] = useState<string | null>(null);
    const [sessionNotes, setSessionNotes] = useState<string>((ongoingWorkout as any)?.sessionNotes || '');
    const [showNotesDrawer, setShowNotesDrawer] = useState(false);
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
        const firstExercise = Array.isArray(currentSession.exercises) ? currentSession.exercises[0] : undefined;
        if (ongoingWorkout?.topSetAmrapState?.status !== 'completed' && firstExercise?.isCalibratorAmrap) return { ...currentSession, exercises: [firstExercise] };
        const modeKey = `session${activeMode}` as 'sessionB' | 'sessionC' | 'sessionD';
        return (activeMode === 'A' || !currentSession[modeKey]) ? currentSession : (currentSession as any)[modeKey];
    }, [activeMode, currentSession, ongoingWorkout?.topSetAmrapState?.status]);

    const renderExercises = useMemo(() => {
        const normalizeExercise = (exercise: any, exerciseIndex: number, partIndex: number): Exercise => ({
            ...(exercise || {}),
            id: exercise?.id || `exercise-${partIndex}-${exerciseIndex}`,
            name: exercise?.name || `Ejercicio ${exerciseIndex + 1}`,
            sets: Array.isArray(exercise?.sets) ? exercise.sets : []
        });

        const normalizePart = (part: any, partIndex: number) => ({
            ...(part || {}),
            id: part?.id || `part-${partIndex}`,
            name: part?.name || `Parte ${partIndex + 1}`,
            exercises: Array.isArray(part?.exercises)
                ? part.exercises.map((exercise: any, exerciseIndex: number) => normalizeExercise(exercise, exerciseIndex, partIndex))
                : []
        });

        if (Array.isArray(sessionForMode.parts) && sessionForMode.parts.length > 0) return sessionForMode.parts.map(normalizePart);
        const baseExercises = Array.isArray(sessionForMode.exercises)
            ? sessionForMode.exercises.map((exercise: any, exerciseIndex: number) => normalizeExercise(exercise, exerciseIndex, 0))
            : [];
        return [{ id: 'default', name: 'Sesion Principal', exercises: baseExercises }];
    }, [sessionForMode]);

    const allExercises = useMemo<Exercise[]>(() => {
        return (renderExercises as { exercises?: Exercise[] }[]).flatMap((part: { exercises?: Exercise[] }) => part.exercises || []);
    }, [renderExercises]);

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

    const visibleExerciseCount = useMemo(() => {
        return displayParts.reduce((total: number, part: any) => total + ((part?.exercises || []).length), 0);
    }, [displayParts]);

    const showEmptySessionState = visibleExerciseCount === 0;

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
            const primaryEntry = info?.involvedMuscles.find(m => m.role === 'primary');
            const rawMuscle = primaryEntry?.muscle || info?.subMuscleGroup || 'Core';
            const primaryMuscle = normalizeMuscleGroup ? normalizeMuscleGroup(rawMuscle, primaryEntry?.emphasis) : rawMuscle;

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

    const renderExerciseCard = (exercise: Exercise, index: number) => {
        const info = exerciseList.find(e => e.id === exercise.exerciseDbId);
        const isActive = activeExerciseId === exercise.id;

        return (
            <div
                key={exercise.id}
                className={`mb-6 m3-card liquid-glass transition-all duration-300 ${isActive ? 'ring-2 ring-m3-primary/40' : 'opacity-90'}`}
                onClick={() => setActiveExerciseId(exercise.id)}
            >
                <div className="flex justify-between items-start mb-4">
                    <div className="flex-1">
                        <h3 className="text-m3-title text-white flex items-center gap-2">
                            {exercise.name}
                            {exercise.isUnilateral && <span className="text-[10px] bg-m3-tertiary/20 text-m3-tertiary px-1.5 py-0.5 rounded font-black">UNI</span>}
                        </h3>
                        <p className="text-[10px] text-m3-on-surface-variant uppercase tracking-widest font-bold mt-1">
                            {info?.category || 'Ejercicio'} • {exercise.trainingMode === 'time' ? 'Tiempo' : 'Repeticiones'}
                        </p>
                    </div>
                </div>

                {/* Action Chips */}
                <div className="flex flex-wrap gap-2 mb-6">
                    <button
                        onClick={(e) => { e.stopPropagation(); setHistoryModalExercise(exercise); }}
                        className="m3-chip border border-[var(--md-sys-color-outline-variant)] bg-white/60 text-[var(--md-sys-color-on-surface-variant)] hover:bg-white/85"
                    >
                        <TrophyIcon size={14} className="mr-2" /> Historial
                    </button>
                    {info?.videos && info.videos.length > 0 && (
                        <button className="m3-chip border border-[var(--md-sys-color-outline-variant)] bg-white/60 text-[var(--md-sys-color-on-surface-variant)] hover:bg-white/85">
                            <PlayIcon size={14} className="mr-2" /> Video
                        </button>
                    )}
                </div>

                {/* Sets Section */}
                <div className="space-y-4">
                    {exercise.sets.map((set, sIdx) => {
                        const setId = set.id;
                        const isLogged = !!completedSets[setId];

                        return (
                            <div key={setId} className="relative">
                                <SetDetails
                                    exercise={exercise}
                                    exerciseInfo={info}
                                    set={set}
                                    setIndex={sIdx}
                                    setId={setId}
                                    settings={settings}
                                    inputs={setInputs[setId] || { reps: '', weight: '', rpe: '', rir: '', partialReps: '' }}
                                    onInputChange={(f, v, s) => handleSetInputChange(setId, f as keyof SetInputState, v, s)}
                                    onLogSet={() => handleLogSet(exercise, set)}
                                    isLogged={isLogged}
                                    history={history}
                                    addToast={addToast}
                                />
                            </div>
                        );
                    })}
                </div>
            </div>
        );
    };

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
                if (suggestion) setConsolidatedWeights(prev => ({ ...prev, [currentExercise.id]: suggestion }));
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
                setSetCardAnimations(prev => ({ ...prev, [set.id]: 'failure' }));
                hapticNotification(NotificationType.Error);
            } else if (actualReps >= targetReps) {
                setSetCardAnimations(prev => ({ ...prev, [set.id]: set.isAmrap ? 'amrap' : 'success' }));
            } else {
                setSetCardAnimations(prev => ({ ...prev, [set.id]: null }));
            }

            const completedSetsForExercise = exercise.sets.slice(0, setIndex).map(s => {
                const d = completedSets[String(s.id)] as { left?: { weight?: number; reps?: number; machineBrand?: string }; right?: { weight?: number; reps?: number; machineBrand?: string } } | undefined;
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
                const newE1RM = calculateBrzycki1RM(primaryData.weight, primaryData.reps);
                if (newE1RM > 0) {
                    const conservative1RM = newE1RM * 0.95; // Factor conservador para siguientes ejercicios
                    const exInfo = exerciseList.find(e => e.id === exercise.exerciseDbId || e.name === exercise.name);
                    const primaryEntry = exInfo?.involvedMuscles?.find(m => m.role === 'primary');
                    const rawMuscle = primaryEntry?.muscle || exInfo?.subMuscleGroup || 'Core';
                    const calibMuscle = normalizeMuscleGroup(rawMuscle, primaryEntry?.emphasis);

                    setSessionAdjusted1RMs(prev => {
                        const next = { ...prev };
                        next[exercise.id] = newE1RM; // Mismo ejercicio: 1RM completo para sets siguientes
                        const currentIdx = allExercises.findIndex(e => e.id === exercise.id);
                        allExercises.forEach((ex, idx) => {
                            if (idx <= currentIdx) return;
                            const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.name);
                            const exPrimary = info?.involvedMuscles?.find(m => m.role === 'primary');
                            const exMuscle = normalizeMuscleGroup(exPrimary?.muscle || info?.subMuscleGroup || 'Core', exPrimary?.emphasis);
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
        setExerciseFeedback(prev => ({ ...prev, [exerciseId]: feedbackData }));

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
                        if (s.weight && s.completedReps) {
                            const e1rm = calculateBrzycki1RM(s.weight, s.completedReps);
                            if (e1rm > historicalBestE1RM) historicalBestE1RM = e1rm;
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
        <div className="workout-session-shell flex flex-col min-h-[calc(100vh-1rem)]">
            <FinishWorkoutModal isOpen={isFinishModalOpen} onClose={() => setIsFinishModalOpen(false)} onFinish={handleFinishSession} initialDurationInSeconds={duration} initialNotes={sessionNotes} initialDiscomforts={[...new Set(Object.values(exerciseFeedback).flatMap((f: any) => f.discomforts || []))]} initialBatteries={(() => { const arr = Object.values(exerciseFeedback).map((f: any) => f.perceivedFatigue).filter((v): v is number => typeof v === 'number'); if (arr.length === 0) return undefined; const avg = arr.reduce((a, b) => a + b, 0) / arr.length; return { general: Math.round(avg * 10) }; })()} fullPage allExercises={allExercises} completedSets={completedSets} exerciseList={exerciseList} sessionName={currentSession.name} />
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
            {historyModalExercise && <ExerciseHistoryModal exerciseName={historyModalExercise.name} history={history} settings={settings} onClose={() => setHistoryModalExercise(null)} />}

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

            <div className="workout-session-scroll mt-2 flex-1 min-h-0 overflow-y-auto pb-36 px-2 w-full max-w-none relative">
                {showEmptySessionState && (
                    <div className="mx-2 mt-4 rounded-xl border border-[var(--md-sys-color-outline-variant)]/40 bg-[var(--md-sys-color-surface)] p-5 text-center">
                        <AlertTriangleIcon size={22} className="mx-auto mb-3 text-[var(--md-sys-color-on-surface-variant)]" />
                        <h3 className="text-[15px] font-semibold text-[var(--md-sys-color-on-surface)]">No hay ejercicios disponibles</h3>
                        <p className="mt-2 text-[13px] text-[var(--md-sys-color-on-surface-variant)]">
                            Esta sesion se abrio sin ejercicios validos. Puedes reintentar la carga o salir.
                        </p>
                        <div className="mt-4 flex items-center justify-center gap-2">
                            <button
                                type="button"
                                onClick={() => window.location.reload()}
                                className="min-h-[44px] rounded-full border border-[var(--md-sys-color-outline)] bg-white/60 px-4 text-[12px] font-semibold text-[var(--md-sys-color-on-surface)] transition-colors hover:bg-white/80"
                            >
                                Reintentar
                            </button>
                            <button
                                type="button"
                                onClick={() => { if (window.confirm('Cancelar sesion y salir?')) onCancel(); }}
                                className="min-h-[44px] rounded-full bg-[var(--md-sys-color-primary)] px-4 text-[12px] font-semibold text-[var(--md-sys-color-on-primary)]"
                            >
                                Salir
                            </button>
                        </div>
                    </div>
                )}
                {!showEmptySessionState && displayParts.map((part: any, partIndex: number) => (
                    <details key={part.id || partIndex} open={!collapsedParts[part.id]} className="group">
                        <summary onClick={(e) => { e.preventDefault(); setCollapsedParts(prev => ({ ...prev, [part.id]: !prev[part.id] })); }} className="flex items-center justify-between mb-4 px-3 py-2 cursor-pointer list-none rounded-xl border border-[var(--md-sys-color-outline-variant)]/30 shadow-sm" style={{ backgroundColor: 'var(--md-sys-color-surface-container-high, #1F212A)' }}>
                            <div className="flex items-center gap-3"><h3 className="text-[11px] font-bold uppercase tracking-widest text-[var(--md-sys-color-primary)]">{part.name || 'Sesión'}</h3></div><ChevronRightIcon className={`text-[var(--md-sys-color-on-surface-variant)] transition-transform ${collapsedParts[part.id] ? '' : 'rotate-90'}`} size={16} />
                        </summary>
                        <div className="space-y-4 relative pl-0 pr-0 w-full">
                            {(part.exercises || []).map((ex: Exercise) => {
                                const exInfo = exerciseList.find(e => e.id === ex.exerciseDbId);
                                const pr = findPrForExercise(exInfo || ({} as any), history, settings, selectedTags[ex.id]);
                                const isActive = activeExerciseId === ex.id;
                                const isFocused = focusExerciseId === ex.id;
                                const hasWarmup = ex.warmupSets && ex.warmupSets.length > 0;

                                return (
                                    <div key={ex.id} className="relative transition-all duration-700 w-full max-w-none" id={`exercise-card-${ex.id}`}>
                                        <details
                                            open={isActive || true}
                                            className={`set-card-details w-full overflow-visible transition-all duration-700 !border-0 !shadow-none !bg-transparent ${isFocused ? 'z-40 ring-4 ring-primary/20 scale-[1.02]' : ''}`}
                                        >
                                            <summary className="set-card-summary p-4 flex flex-col items-stretch bg-white/40 backdrop-blur-xl rounded-[32px] border border-white/50 shadow-xl shadow-black/5 hover:bg-white/60 active:scale-[0.98] transition-all mb-4" onClick={() => handleHeaderClick(ex.id)}>
                                                <div className="flex items-center justify-between gap-4 w-full">
                                                    <div className="flex items-center gap-4 min-w-0 flex-1">
                                                        <div className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-[18px] border border-white/80 bg-white/90 text-sm font-black text-primary shadow-sm">
                                                            {allExercises.findIndex(e => e.id === ex.id) + 1}
                                                        </div>
                                                        <div className="min-w-0">
                                                            <p className="truncate text-xl font-black tracking-tight text-black">{ex.name}</p>
                                                            <p className="mt-0.5 text-[10px] font-black uppercase tracking-[0.2em] text-black/30">
                                                                {(ex.sets?.length ?? 0)} SERIES{ex.restTime ? ` • ${ex.restTime}S DESC` : ''}
                                                            </p>
                                                        </div>
                                                    </div>
                                                    <div className="flex items-center gap-2">
                                                        <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); setHistoryModalExercise(ex); }} className="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-full p-1 text-[var(--md-sys-color-on-surface-variant)] transition-colors hover:bg-white/60 hover:text-[var(--md-sys-color-on-surface)]">
                                                            <ClockIcon size={20} />
                                                        </button>
                                                        <ChevronRightIcon className="details-arrow" />
                                                    </div>
                                                </div>
                                            </summary>
                                            <div className="set-card-content !border-none !p-0 space-y-4 relative w-full mb-8">
                                                {pr && <div className="mx-4 p-3 text-center text-[10px] font-black uppercase tracking-[0.2em] rounded-2xl bg-primary/5 text-primary border border-primary/10 flex items-center justify-center gap-2"><TrophyIcon size={14} /> {pr.prString}</div>}

                                                <div className="px-4">
                                                    <HeaderAccordion
                                                        exercise={ex}
                                                        exerciseInfo={exInfo}
                                                        selectedTag={selectedTags[ex.id]}
                                                        onTagChange={(tag) => setSelectedTags(prev => ({ ...prev, [ex.id]: tag }))}
                                                    />
                                                </div>

                                                <div className="space-y-6" ref={(el) => { setsContainerRefs.current[ex.id] = el; }}>
                                                    {hasWarmup && (
                                                        <div id={`warmup-card-${ex.id}`} className="mx-4 overflow-hidden rounded-[28px] border border-white/50 bg-white/40 backdrop-blur-md shadow-xl shadow-black/5">
                                                            <div className="flex items-center justify-between border-b border-black/5 px-4 py-4">
                                                                <div className="flex items-center gap-2">
                                                                    <FlameIcon size={14} className="text-primary" />
                                                                    <span className="text-[10px] font-black uppercase tracking-[0.2em] text-black/40">Series de aproximación</span>
                                                                </div>
                                                                <span className="text-[9px] font-black uppercase tracking-[0.15em] text-primary/40">{ex.warmupSets!.length} SERIES</span>
                                                            </div>
                                                            <div className="p-4 space-y-2">
                                                                {(ex.warmupSets as WarmupSetDefinition[]).map((wSet, wi) => {
                                                                    const suggested = (ex.sets?.length ?? 0) > 0 ? getWeightSuggestionForSet(ex, exInfo, 0, [], settings, history, selectedTags[ex.id], sessionAdjusted1RMs[ex.id]) : undefined;
                                                                    const baseW = consolidatedWeights[ex.id] ?? suggested ?? 0;
                                                                    const calcKg = baseW > 0 ? roundWeight((baseW * wSet.percentageOfWorkingWeight) / 100, settings.weightUnit) : '—';
                                                                    return (
                                                                        <div key={wSet.id} className="flex items-center justify-between rounded-[20px] border border-white/50 bg-white/60 px-4 py-3 shadow-sm">
                                                                            <span className="w-8 text-xs font-black text-primary">{wi + 1}</span>
                                                                            <span className="text-[10px] font-black uppercase tracking-widest text-black/30">{wSet.percentageOfWorkingWeight}%</span>
                                                                            <span className="text-sm font-black tabular-nums text-black">{calcKg}{typeof calcKg === 'number' ? settings.weightUnit : ''}</span>
                                                                            <span className="text-[10px] font-black uppercase tracking-widest text-black/60">{wSet.targetReps} REPS</span>
                                                                        </div>
                                                                    );
                                                                })}
                                                                <button onClick={() => { setActiveExerciseId(ex.id); setActiveSetId(`warmup-${ex.id}`); }} className="mt-2 min-h-[52px] w-full rounded-[24px] bg-primary/10 text-primary border border-primary/5 py-3 text-[10px] font-black uppercase tracking-[0.2em] shadow-lg shadow-primary/5 transition-all active:scale-[0.98]">
                                                                    INICIAR CALENTAMIENTO
                                                                </button>
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* Series efectivas CAROUSEL SECTION */}
                                                    <div className="relative group/carousel">
                                                        <div className="mx-4 overflow-hidden rounded-[28px] border border-white/50 bg-white/40 backdrop-blur-md shadow-lg shadow-black/5">
                                                            <div className="session-table w-full" data-tabular="true">
                                                                <div className="flex items-center gap-2 px-4 py-3 border-b border-black/5 text-[9px] font-black uppercase tracking-[0.2em] text-black/30">
                                                                    <span className="w-8 text-center text-primary/40">SET</span>
                                                                    <span className="flex-1 min-w-[60px] text-center">KG</span>
                                                                    <span className="flex-1 min-w-[56px] text-center">REPS</span>
                                                                    <span className="w-10" />
                                                                </div>
                                                                {ex.sets.map((set: ExerciseSet, setIndex) => {
                                                                    const setId = set.id;
                                                                    const isCompleted = !!completedSets[String(setId)];
                                                                    const isActiveRow = activeExerciseId === ex.id && activeSetId === setId;
                                                                    const inputsRaw = setInputs[String(setId)];
                                                                    const safeInputsRow = (ex.isUnilateral ? (inputsRaw as UnilateralSetInputs)?.left : (inputsRaw as SetInputState)) || { weight: '', reps: '', duration: '' };
                                                                    const placeholder = getGhostForSet(ex.exerciseDbId || ex.id, setIndex, history);
                                                                    const placeholderKg = placeholder?.weight;
                                                                    const placeholderReps = placeholder?.reps;
                                                                    return (
                                                                        <div key={setId} className={`session-table-row transition-all duration-300 ${isActiveRow ? 'bg-white/40' : 'hover:bg-white/20'}`}>
                                                                            <div className={`flex items-center gap-2 px-4 py-3 border-b border-black/[0.03] last:border-0 ${isCompleted ? 'opacity-40' : ''}`}>
                                                                                <span className="w-8 text-center text-[10px] font-black text-black/20 tabular-nums">{setIndex + 1}</span>
                                                                                <div className="flex-1 min-w-[60px] flex justify-center" onClick={e => { e.stopPropagation(); setActiveExerciseId(ex.id); setActiveSetId(setId); }} role="button" tabIndex={0}>
                                                                                    <input
                                                                                        type="text"
                                                                                        inputMode="decimal"
                                                                                        value={safeInputsRow.weight || ''}
                                                                                        placeholder={placeholderKg?.toString() || '—'}
                                                                                        onChange={e => handleSetInputChange(String(setId), 'weight', e.target.value.replace(',', '.').replace(/[^0-9.]/g, ''), ex.isUnilateral ? 'left' : undefined)}
                                                                                        onClick={e => e.stopPropagation()}
                                                                                        className="w-full max-w-[72px] bg-transparent border-none text-center text-sm font-black py-1 tabular-nums block text-black focus:ring-0 p-0"
                                                                                    />
                                                                                </div>
                                                                                <div className="flex-1 min-w-[56px] flex justify-center" onClick={e => { e.stopPropagation(); setActiveExerciseId(ex.id); setActiveSetId(setId); }} role="button" tabIndex={0}>
                                                                                    <input
                                                                                        type="text"
                                                                                        inputMode="numeric"
                                                                                        value={ex.trainingMode === 'time' ? (safeInputsRow.duration || '') : (safeInputsRow.reps || '')}
                                                                                        placeholder={placeholderReps?.toString() || '—'}
                                                                                        onChange={e => handleSetInputChange(String(setId), ex.trainingMode === 'time' ? 'duration' : 'reps', e.target.value.replace(/[^0-9]/g, ''), ex.isUnilateral ? 'left' : undefined)}
                                                                                        onClick={e => e.stopPropagation()}
                                                                                        className="w-full max-w-[56px] bg-transparent border-none text-center text-sm font-black py-1 tabular-nums block text-black focus:ring-0 p-0"
                                                                                    />
                                                                                </div>
                                                                                <div className="w-10 flex justify-center" onClick={e => e.stopPropagation()}>
                                                                                    <button type="button" onClick={() => handleLogSet(ex, set, false)} className={`w-9 h-9 rounded-full border-2 flex items-center justify-center transition-all ${isCompleted ? 'border-primary bg-primary text-white scale-90' : 'border-black/5 bg-white shadow-sm text-black/10 hover:border-black/10'}`}>
                                                                                        <CheckCircleIcon size={16} />
                                                                                    </button>
                                                                                </div>
                                                                            </div>
                                                                            {isActiveRow && (
                                                                                <div id={`set-card-${setId}`} className="px-2 pb-3 animate-fade-in">
                                                                                    <SetDetails exercise={ex} exerciseInfo={exInfo} set={set} setIndex={setIndex} settings={settings} inputs={(setInputs[String(setId)] as SetInputState) || safeInputsRow} onInputChange={(field, value, side) => handleSetInputChange(String(setId), field as keyof SetInputState, value, side)} onLogSet={(isCal) => handleLogSet(ex, set, isCal)} isLogged={!!isCompleted} history={history} currentSession1RM={sessionAdjusted1RMs[ex.id]} base1RM={exInfo?.calculated1RM || ex.reference1RM} isCalibrated={!!sessionAdjusted1RMs[ex.id]} cardAnimation={setCardAnimations[String(setId)]} addToast={addToast} suggestedWeight={getWeightSuggestionForSet(ex, exInfo, setIndex, ex.sets.slice(0, setIndex).map(s => { const d = completedSets[String(s.id)] as any; if (!d) return { weight: 0 }; const p = ex.isUnilateral ? (d.left || d.right) : d; return p ? { weight: p.weight || 0, reps: p.reps, machineBrand: p.machineBrand } : { weight: 0 }; }), settings, history, selectedTags[ex.id], sessionAdjusted1RMs[ex.id])} selectedTag={selectedTags[ex.id]} tableRowMode setId={String(setId)} />
                                                                                </div>
                                                                            )}
                                                                        </div>
                                                                    );
                                                                })}
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div id={`feedback-card-${ex.id}`} className="mx-4 overflow-hidden bg-white/40 border border-white/50 rounded-[24px] shadow-lg shadow-black/5">
                                                        <button onClick={() => { setActiveExerciseId(ex.id); setActiveSetId(`feedback-${ex.id}`); }} className="flex min-h-[60px] w-full items-center justify-between px-6 py-4 transition-all active:scale-[0.98]">
                                                            <div className="flex items-center gap-3">
                                                                <ActivityIcon size={18} className="text-primary" />
                                                                <span className="text-[10px] font-black uppercase tracking-[0.2em] text-black">Feedback Post-Ejercicio</span>
                                                            </div>
                                                            <div className="w-8 h-8 rounded-full bg-black/5 flex items-center justify-center">
                                                                <div className="w-1.5 h-1.5 rounded-full bg-black/20"></div>
                                                            </div>
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
            <div className="workout-bottom-dock fixed left-0 right-0 bottom-0 z-20 pb-[max(1rem, env(safe-area-inset-bottom))] flex flex-col pointer-events-none">
                <div className="flex justify-center gap-3 px-6 py-4 shrink-0 pointer-events-auto">
                    <button onClick={() => handleStartRest(90, 'Descanso')} className="flex items-center gap-2 px-6 py-3 rounded-full text-[10px] font-black uppercase tracking-[0.2em] min-h-[48px] border border-white/80 bg-white/60 backdrop-blur-xl text-black shadow-xl shadow-black/5 transition-all active:scale-[0.95]">
                        <ClockIcon size={14} className="text-primary" /> 90S
                    </button>
                    <button onClick={() => setShowNotesDrawer(true)} className="flex items-center gap-2 px-6 py-3 rounded-full text-[10px] font-black uppercase tracking-[0.2em] min-h-[48px] border border-white/80 bg-white/60 backdrop-blur-xl text-black shadow-xl shadow-black/5 transition-all active:scale-[0.95]">
                        <PencilIcon size={14} className="text-primary" /> NOTAS
                    </button>
                </div>
                <div className="shrink-0 pointer-events-auto bg-white/80 backdrop-blur-2xl border-t border-white/50 shadow-[0_-10px_40px_rgba(0,0,0,0.05)] rounded-t-[40px]">
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
                        <textarea value={sessionNotes} onChange={(e) => setSessionNotes(e.target.value)} placeholder="Notas rápidas durante el entrenamiento..." rows={6} className="w-full rounded-[12px] bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] p-4 text-[var(--md-sys-color-on-surface)] text-sm placeholder:text-[var(--md-sys-color-on-surface-variant)] outline-none" />
                        <p className="text-[10px] text-[var(--md-sys-color-on-surface-variant)] mt-2">Se incluirán al finalizar la sesión.</p>
                    </div>
                </WorkoutDrawer>
            )}

            {showReadiness && (
                <ReadinessSheet
                    isOpen={showReadiness}
                    onClose={() => setShowReadiness(false)}
                    onStartWorkout={(data) => {
                        setReadinessData(data);
                        setShowReadiness(false);
                        hapticImpact(ImpactStyle.Heavy);
                    }}
                />
            )}
        </div>
    );
};
