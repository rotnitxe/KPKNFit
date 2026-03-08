
// components/FinishWorkoutModal.tsx
import React, { useState, useEffect, useRef, useMemo } from 'react';
import { TacticalModal } from './ui/TacticalOverlays';
import WorkoutDrawer from './workout/WorkoutDrawer';
import { DISCOMFORT_DATABASE } from '../data/discomfortList';
import { CheckCircleIcon, ZapIcon, BrainIcon, ActivityIcon, LinkIcon, ClockIcon, FlameIcon, ChevronDownIcon, ChevronRightIcon, SearchIcon } from './icons';
import { useAppDispatch, useAppState } from '../contexts/AppContext';
import { shareElementAsImage } from '../services/shareService';
import CaupolicanBackground from './social/CaupolicanBackground';
import { getLocalDateString } from '../utils/dateUtils';
import { isSetEffective } from '../services/auge';
import type { Exercise, ExerciseMuscleInfo } from '../types';

export interface InitialBatteriesFromFeedback {
  general?: number;
  spinal?: number;
  muscle?: Record<string, number>;
}

interface FinishWorkoutModalProps {
  isOpen: boolean;
  onClose: () => void;
  onFinish: (notes?: string, discomforts?: string[], fatigueLevel?: number, mentalClarity?: number, durationInMinutes?: number, logDate?: string, photo?: string, planDeviations?: any[], focus?: number, pump?: number, environmentTags?: string[], sessionDifficulty?: number, planAdherenceTags?: string[], muscleBatteries?: Record<string, number>) => void;
  mode?: 'live' | 'log';
  improvementIndex?: { percent: number; direction: 'up' | 'down' | 'neutral' } | null;
  initialDurationInSeconds?: number;
  initialNotes?: string;
  initialDiscomforts?: string[];
  initialBatteries?: InitialBatteriesFromFeedback;
  /** @deprecated Use fullPage. When true, shows full-screen page instead of drawer. */
  asDrawer?: boolean;
  /** Vista full-screen en lugar de modal/drawer. Estética "Tú" (gris claro, limpio). */
  fullPage?: boolean;
  allExercises?: Exercise[];
  completedSets?: Record<string, { left: any; right: any }>;
  exerciseList?: ExerciseMuscleInfo[];
}

const ENVIRONMENT_TAGS = ["Gimnasio Lleno", "Gimnasio Vacío", "Entrenando con Amigos", "Buena Música", "Distraído", "Con Prisa"];
const PLAN_ADHERENCE_TAGS = ["Seguí el Plan", "Más Pesado", "Más Ligero", "Más Reps", "Menos Reps", "Cambié Ejercicios", "Añadí Ejercicios"];

// --- HIDDEN WORKOUT SHARE CARD ---
export interface ShareCardData {
  date: string;
  duration: string;
  difficulty: number;
  pump: number;
  exerciseSummaries: { name: string; line: string }[];
}

const formatSetIntensity = (set: { weight?: number; completedReps?: number; completedRPE?: number; completedRIR?: number; isFailure?: boolean; isAmrap?: boolean; useBodyweight?: boolean }, weightUnit: string): string => {
  const reps = set.completedReps;
  const w = set.weight;
  const rpe = set.completedRPE;
  const rir = set.completedRIR;
  const fail = set.isFailure;
  const amrap = set.isAmrap;
  const bw = set.useBodyweight;
  const parts: string[] = [];
  if (w != null && w > 0 && !bw) parts.push(`${w}${weightUnit}`);
  else if (bw) parts.push('BW');
  if (reps != null && typeof reps === 'number') parts.push(`${reps} reps`);
  if (rpe != null && rpe > 0) parts.push(`RPE ${rpe}`);
  if (rir != null && rir >= 0) parts.push(`RIR ${rir}`);
  if (fail) parts.push('Fallo');
  if (amrap) parts.push('AMRAP');
  return parts.filter(Boolean).join(' ');
};

export const buildShareCardDataFromLog = (log: { date: string; duration?: number; completedExercises: { exerciseName: string; sets: any[]; useBodyweight?: boolean }[]; sessionDifficulty?: number; pump?: number }, weightUnit: string): ShareCardData => {
  const duration = log.duration != null ? String(Math.round(log.duration / 60)) : '--';
  const exerciseSummaries = (log.completedExercises || []).map(ex => {
    const setsWithData = ex.sets.filter((s: any) => (s.completedReps != null && s.completedReps > 0) || (s.weight != null && s.weight > 0) || ex.useBodyweight);
    const count = setsWithData.length;
    if (count === 0) return { name: ex.exerciseName, line: '' };
    const first = setsWithData[0] as any;
    const intensity = formatSetIntensity({ ...first, useBodyweight: ex.useBodyweight }, weightUnit);
    const line = count > 1 ? `${count}× ${intensity}` : intensity;
    return { name: ex.exerciseName, line: line || `${count} series` };
  }).filter(s => s.line || s.name);
  return {
    date: log.date,
    duration,
    difficulty: log.sessionDifficulty ?? 5,
    pump: log.pump ?? 5,
    exerciseSummaries,
  };
};

const MAX_EXERCISES_IN_SHARE = 14;

export const WorkoutShareCard: React.FC<ShareCardData & { preview?: boolean }> = ({ date, duration, exerciseSummaries, preview }) => {
    const displayed = exerciseSummaries.slice(0, MAX_EXERCISES_IN_SHARE);
    const restCount = exerciseSummaries.length - MAX_EXERCISES_IN_SHARE;

    const wrapperClass = preview
        ? 'relative w-[540px] h-[960px] flex items-center justify-center font-sans overflow-hidden'
        : 'fixed w-[540px] h-[960px] flex items-center justify-center font-sans overflow-hidden pointer-events-none';
    const captureStyle: React.CSSProperties = preview ? {} : { left: 0, top: 0, zIndex: -1 };
    return (
        <div id={preview ? undefined : 'workout-summary-share-card'} className={wrapperClass} style={preview ? undefined : captureStyle}>
            {/* Fondo del frame (gradiente sutil) */}
            <div className="absolute inset-0 bg-gradient-to-br from-zinc-950 via-zinc-900 to-black" />
            <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,rgba(6,182,212,0.08)_0%,transparent_70%)]" />

            {/* Tarjeta centrada con bordes redondeados */}
            <div className="relative w-[480px] h-[860px] rounded-[28px] overflow-hidden border border-white/[0.12] shadow-[0_0_0_1px_rgba(255,255,255,0.06),0_25px_50px_-12px_rgba(0,0,0,0.5)] flex flex-col bg-[#FEF7FF]">
                <div className="absolute inset-0 z-0 opacity-30 mix-blend-luminosity scale-110 -translate-y-8" style={{ overflow: 'hidden' }}>
                    <CaupolicanBackground />
                </div>
                <div className="absolute inset-0 bg-gradient-to-b from-black/90 via-black/50 to-zinc-950 z-0" />
                <div className="absolute inset-0 opacity-[0.12] mix-blend-overlay z-0" style={{ backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")` }} />

                <div className="relative z-10 flex flex-col h-full p-5 box-border text-white" style={{ minHeight: 0 }}>
                    {/* Header */}
                    <div className="flex justify-between items-center shrink-0 mb-2">
                        <div>
                            <span className="text-[10px] font-black text-white/40 uppercase tracking-widest block">Sesión de entrenamiento</span>
                            <span className="text-sm font-bold text-white">{new Date(date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })}</span>
                        </div>
                        <div className="w-11 h-11 rounded-xl bg-white flex items-center justify-center p-1.5 shrink-0 shadow-lg">
                            <img src="/caupolican-icon.svg" alt="" className="w-full h-full object-contain" loading="eager" decoding="sync" aria-hidden />
                        </div>
                    </div>

                    {/* Ejercicios */}
                    <div className="flex-1 min-h-0 flex flex-col py-2">
                        {displayed.length > 0 ? (
                            <div className="space-y-0.5 flex-1 min-h-0" style={{ overflow: 'hidden' }}>
                                {displayed.map((ex, i) => (
                                    <div key={i} className="flex justify-between gap-2 text-[10px] leading-tight py-0.5">
                                        <span className="font-bold text-white flex-1 min-w-0 truncate">{ex.name}</span>
                                        <span className="text-cyan-400/90 font-mono shrink-0 text-right">{ex.line}</span>
                                    </div>
                                ))}
                                {restCount > 0 && (
                                    <div className="text-[9px] text-white/50 pt-1">+{restCount} más</div>
                                )}
                            </div>
                        ) : (
                            <div className="flex items-center justify-center flex-1 min-h-[80px]">
                                <span className="text-white/40 text-xs">Ejercicios de la sesión</span>
                            </div>
                        )}
                        <div className="h-0.5 w-full mt-2 rounded-full bg-gradient-to-r from-red-500 to-cyan-500 shrink-0" />
                    </div>

                    {/* Minutos + footer */}
                    <div className="shrink-0 space-y-2 pt-2">
                        <div className="bg-white/5 border border-[#E6E0E9] rounded-xl px-4 py-2.5 flex items-center justify-center gap-2">
                            <ClockIcon size={18} className="text-white/50 shrink-0" />
                            <span className="text-xl font-black text-white">{duration}</span>
                            <span className="text-[9px] text-[#49454F] font-bold uppercase shrink-0">min</span>
                        </div>
                        <p className="text-[9px] font-black text-white/40 uppercase tracking-widest text-center">Registra tus entrenamientos con KPKN</p>
                    </div>
                </div>
            </div>
        </div>
    );
};

const MUSCLE_LABEL_MAP: Record<string, string> = {
  'Tríceps': 'Tríceps', 'Bíceps': 'Bíceps', 'Pectorales': 'Pectorales', 'Dorsales': 'Dorsales',
  'Deltoides': 'Hombros', 'Cuádriceps': 'Cuádriceps', 'Glúteos': 'Glúteos', 'Isquiosurales': 'Isquiotibiales',
  'Pantorrillas': 'Pantorrillas', 'Abdomen': 'Abdomen', 'Espalda Baja': 'Espalda Baja', 'Trapecio': 'Trapecio',
};

/** Selector de puntos 1–10, estilo ReadinessDrawer */
const PointSelector: React.FC<{ value: number; onChange: (v: number) => void; labels?: [string, string] }> = ({ value, onChange, labels }) => (
  <div className="flex flex-col">
    <div className="flex flex-wrap gap-1">
      {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((v) => (
        <button
          key={v}
          type="button"
          onClick={() => onChange(v)}
          className={`w-7 h-7 rounded-full border transition-colors text-[10px] font-medium ${
            value === v ? 'bg-[#525252] border-[#525252] text-white' : 'bg-white border-[#a3a3a3] text-[#1a1a1a] hover:border-[#737373]'
          }`}
          aria-label={`${v} de 10`}
        >
          {v}
        </button>
      ))}
    </div>
    {labels && (
      <div className="flex justify-between w-full mt-0.5 text-[9px] text-[#737373]">
        <span>{labels[0]}</span>
        <span>{labels[1]}</span>
      </div>
    )}
  </div>
);

const FinishWorkoutModal: React.FC<FinishWorkoutModalProps> = ({ isOpen, onClose, onFinish, mode = 'live', improvementIndex, initialDurationInSeconds, initialNotes, initialDiscomforts = [], initialBatteries, asDrawer, fullPage, allExercises = [], completedSets = {}, exerciseList = [] }) => {
  const { addRecommendationTrigger, addToast } = useAppDispatch();
  const { settings } = useAppState();
  const weightUnit = settings?.weightUnit ?? 'kg';
  const [generalBattery, setGeneralBattery] = useState(50);
  const [muscleBatteries, setMuscleBatteries] = useState<Record<string, number>>({});
  const [spinalBattery, setSpinalBattery] = useState(50);
  const [muscleAccordionOpen, setMuscleAccordionOpen] = useState(false);
  const [focus, setFocus] = useState(5);
  const [pump, setPump] = useState(5);
  const [sessionDifficulty, setSessionDifficulty] = useState(5);
  const [selectedDiscomforts, setSelectedDiscomforts] = useState<string[]>([]);
  const [showDiscomfortSearch, setShowDiscomfortSearch] = useState(false);
  const [discomfortSearchQuery, setDiscomfortSearchQuery] = useState('');
  const [environmentTags, setEnvironmentTags] = useState<string[]>([]);
  const [planAdherenceTags, setPlanAdherenceTags] = useState<string[]>([]);
  const [notes, setNotes] = useState('');
  const [durationInMinutes, setDurationInMinutes] = useState('');
  const [logDate, setLogDate] = useState(getLocalDateString());
  const [showRecoverySuggestion, setShowRecoverySuggestion] = useState(false);
  const prevIsOpen = useRef(isOpen);
  const [isSharing, setIsSharing] = useState(false);

  const shareCardExerciseSummaries = useMemo(() => {
    const out: { name: string; line: string }[] = [];
    allExercises.forEach(ex => {
      const setsWithData: { reps?: number; weight?: number; rpe?: number; rir?: number; isFailure?: boolean; isAmrap?: boolean }[] = [];
      ex.sets?.forEach(set => {
        const raw = completedSets[String(set.id)] as { left?: any; right?: any } & Record<string, unknown> | undefined;
        const data = ex.isUnilateral
          ? (raw?.left || raw?.right)
          : (raw?.left ?? (raw && raw.left === undefined && raw.right === undefined ? raw : undefined));
        if (data && typeof data === 'object' && ((data.reps != null && data.reps > 0) || (data.weight != null && data.weight > 0))) {
          setsWithData.push({
            reps: data.reps,
            weight: data.weight,
            rpe: data.rpe,
            rir: data.rir,
            isFailure: data.isFailure,
            isAmrap: data.isAmrap,
          });
        }
      });
      if (setsWithData.length === 0) return;
      const first = setsWithData[0];
      const parts: string[] = [];
      if (first.weight != null && first.weight > 0) parts.push(`${first.weight}${weightUnit}`);
      if (first.reps != null) parts.push(`${first.reps} reps`);
      if (first.rpe != null && first.rpe > 0) parts.push(`RPE ${first.rpe}`);
      if (first.rir != null && first.rir >= 0) parts.push(`RIR ${first.rir}`);
      if (first.isFailure) parts.push('Fallo');
      if (first.isAmrap) parts.push('AMRAP');
      const intensity = parts.join(' ');
      const line = setsWithData.length > 1 ? `${setsWithData.length}× ${intensity}` : intensity;
      out.push({ name: ex.name, line: line || `${setsWithData.length} series` });
    });
    return out;
  }, [allExercises, completedSets, weightUnit]);

  const musclesWithEffectiveSets = useMemo(() => {
    const muscles = new Set<string>();
    allExercises.forEach(ex => {
      const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.name);
      const primaryMuscles = info?.involvedMuscles?.filter(m => m.role === 'primary').map(m => m.muscle) ?? [];
      ex.sets?.forEach(set => {
        const dataRaw = completedSets[String(set.id)];
        if (!dataRaw) return;
        const data = dataRaw as { left?: any; right?: any } & Record<string, unknown>;
        // Soporta formato { left, right } o plano { reps, weight, rpe, rir }
        const primary = ex.isUnilateral
          ? (data?.left || data?.right)
          : (data?.left ?? (data?.left === undefined && data?.right === undefined ? data : undefined));
        if (!primary || typeof primary !== 'object') return;
        const setLike = { ...primary, completedRPE: primary?.rpe, completedRIR: primary?.rir, targetRPE: set.targetRPE, targetRIR: set.targetRIR, intensityMode: set.intensityMode };
        if (isSetEffective(setLike)) primaryMuscles.forEach(m => muscles.add(m));
      });
    });
    return Array.from(muscles);
  }, [allExercises, completedSets, exerciseList]);

  useEffect(() => {
    if (isOpen && musclesWithEffectiveSets.length > 0) {
      setMuscleBatteries(prev => {
        const next = { ...prev };
        musclesWithEffectiveSets.forEach(m => { if (next[m] === undefined) next[m] = 50; });
        return next;
      });
    }
  }, [isOpen, musclesWithEffectiveSets]);

  useEffect(() => {
    if (isOpen && !prevIsOpen.current) {
      if (initialDurationInSeconds) setDurationInMinutes(Math.round(initialDurationInSeconds / 60).toString());
      if (initialNotes) setNotes(initialNotes);
      if (initialDiscomforts.length > 0) setSelectedDiscomforts(prev => [...new Set([...prev, ...initialDiscomforts])]);
      if (initialBatteries) {
        if (initialBatteries.general != null) setGeneralBattery(initialBatteries.general);
        if (initialBatteries.spinal != null) setSpinalBattery(initialBatteries.spinal);
        if (initialBatteries.muscle && Object.keys(initialBatteries.muscle).length > 0) setMuscleBatteries(prev => ({ ...prev, ...initialBatteries.muscle }));
      }
      setLogDate(getLocalDateString());
    }
    if (!isOpen && prevIsOpen.current) {
      setGeneralBattery(50);
      setMuscleBatteries({});
      setSpinalBattery(50);
      setFocus(5);
      setPump(5);
      setSessionDifficulty(5);
      setSelectedDiscomforts([]);
      setShowDiscomfortSearch(false);
      setDiscomfortSearchQuery('');
      setEnvironmentTags([]);
      setPlanAdherenceTags([]);
      setNotes('');
      setDurationInMinutes('');
      setShowRecoverySuggestion(false);
    }
    prevIsOpen.current = isOpen;
  }, [isOpen, initialDurationInSeconds, initialNotes, initialDiscomforts, initialBatteries]);

  const fatigueLevel = Math.min(10, Math.max(1, Math.round(generalBattery / 10)));
  const mentalClarity = Math.min(10, Math.max(1, Math.round((100 - spinalBattery) / 10)));
  const avgMuscle = musclesWithEffectiveSets.length > 0
    ? musclesWithEffectiveSets.reduce((a, m) => a + (muscleBatteries[m] ?? 50), 0) / musclesWithEffectiveSets.length
    : 50;
  const pumpMapped = Math.min(10, Math.max(1, Math.round(avgMuscle / 10)));

  const handleFinishAttempt = () => {
    if (fatigueLevel >= 8 && mentalClarity <= 3) {
      setShowRecoverySuggestion(true);
      addRecommendationTrigger({ type: 'high_fatigue', value: fatigueLevel, context: `Fatiga extrema (${fatigueLevel}/10) tras sesión.` });
    } else {
      executeFinish();
    }
  };

  const executeFinish = () => {
    const durationNum = durationInMinutes ? parseInt(durationInMinutes, 10) : undefined;
    if (mode === 'log' && (!durationNum || durationNum <= 0)) {
      alert("Por favor, introduce una duración válida en minutos.");
      return;
    }
    const muscleBatteriesToPass = musclesWithEffectiveSets.length > 0
      ? Object.fromEntries(musclesWithEffectiveSets.map(m => [m, muscleBatteries[m] ?? 50]))
      : undefined;
    onFinish(notes, selectedDiscomforts, fatigueLevel, mentalClarity, durationNum, logDate, undefined, [], focus, pumpMapped, environmentTags, sessionDifficulty, planAdherenceTags, muscleBatteriesToPass);
  };

  const filteredDiscomforts = useMemo(() => {
    const q = discomfortSearchQuery.toLowerCase().trim();
    if (!q) return DISCOMFORT_DATABASE;
    return DISCOMFORT_DATABASE.filter(d =>
      d.name.toLowerCase().includes(q) || d.description.toLowerCase().includes(q)
    );
  }, [discomfortSearchQuery]);

  const toggleDiscomfort = (name: string) => {
    setSelectedDiscomforts(prev => prev.includes(name) ? prev.filter(x => x !== name) : [...prev, name]);
  };
  
  const handleAcceptDeload = () => {
      addToast("Se ha programado una sesión de descanso activo para tu próximo entrenamiento.", "suggestion");
      executeFinish();
  }
  
  const handleShare = async () => {
      setIsSharing(true);
      await shareElementAsImage('workout-summary-share-card', '¡Entrenamiento Terminado!', 'Registra tus entrenamientos con KPKN. #KPKN #Fitness');
      setIsSharing(false);
  }

  const toggleTag = (tag: string, state: string[], setState: React.Dispatch<React.SetStateAction<string[]>>) => {
    setState(prev => prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag]);
  };

  const useFullPage = fullPage ?? asDrawer;

  const TagGroup: React.FC<{title:string; tags: string[]; selected: string[]; onToggle: (tag:string) => void;}> = ({title, tags, selected, onToggle}) => (
      <div>
        <label className={`block text-[10px] font-semibold uppercase tracking-wide mb-2 ${useFullPage ? 'text-[#525252]' : 'text-cyber-cyan/80 font-mono font-black tracking-widest'}`}>{title}</label>
        <div className="flex flex-wrap gap-2">
            {tags.map(tag => (
                <button key={tag} onClick={() => onToggle(tag)} className={`px-3 py-1.5 text-[10px] font-medium border transition-all ${useFullPage ? (selected.includes(tag) ? 'bg-[#525252] border-[#525252] text-white' : 'bg-white border-[#a3a3a3] text-[#1a1a1a] hover:border-[#737373]') : (selected.includes(tag) ? 'bg-cyber-cyan/20 border-cyber-cyan/50 text-cyber-cyan font-mono font-bold uppercase rounded-lg' : 'bg-slate-900/80 border-slate-700 text-slate-500 hover:border-cyber-cyan/30 hover:text-slate-400 font-mono font-bold uppercase rounded-lg')}`}>
                    {tag}
                </button>
            ))}
        </div>
    </div>
  );

  /** Selector de batería: puntos 1-10 → 10%-100% */
  const BatteryPointSelector: React.FC<{ label: string; value: number; onChange: (v: number) => void }> = ({ label, value, onChange }) => {
    const point = Math.min(10, Math.max(1, Math.round(value / 10)));
    return (
      <div>
        <label className="block text-[10px] font-semibold text-[#525252] uppercase tracking-wide mb-1">{label}</label>
        <PointSelector value={point} onChange={(v) => onChange(v * 10)} labels={['10%', '100%']} />
      </div>
    );
  };

  const title = showRecoverySuggestion ? "Recuperación Prioritaria" : "Finalizar Sesión";
  const content = (
    <>
      {/* Tarjeta para compartir: off-screen, el wrapper tiene el id para html2canvas */}
      <div id="workout-summary-share-card" className="fixed -left-[9999px] top-0 w-[540px] h-[960px] pointer-events-none overflow-hidden">
        <WorkoutShareCard 
          preview
          date={logDate} 
          duration={durationInMinutes || '--'} 
          difficulty={sessionDifficulty} 
          pump={pump}
          exerciseSummaries={shareCardExerciseSummaries}
        />
      </div>

      {!showRecoverySuggestion ? (
        <div className={`space-y-5 p-4 ${useFullPage ? 'bg-[#e5e5e5]' : 'bg-[#0a0c10]'}`}>
            {/* Resumen compacto */}
            <div className={`flex items-center justify-between gap-4 p-4 ${useFullPage ? 'bg-white border border-[#a3a3a3]' : 'rounded-xl bg-slate-950/80 border border-cyber-cyan/20'}`}>
                <div>
                    <span className={`text-[9px] font-semibold uppercase tracking-wide block ${useFullPage ? 'text-[#737373]' : 'font-mono font-black text-cyber-cyan/70 tracking-widest'}`}>Sesión Completada</span>
                    <span className={`text-lg font-semibold ${useFullPage ? 'text-[#1a1a1a]' : 'font-mono font-black text-white'}`}>{new Date(logDate).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })}</span>
                </div>
                <div className={`flex items-center gap-3 font-medium ${useFullPage ? 'text-[#525252]' : 'text-cyber-cyan font-mono font-bold'}`}>
                    <span>{durationInMinutes || '--'} min</span>
                    <span className={useFullPage ? 'text-[#a3a3a3]' : 'text-slate-600'}>|</span>
                    <span>Nivel {sessionDifficulty}</span>
                </div>
            </div>

            {/* Duración y fecha */}
            <div className="grid grid-cols-2 gap-3">
                <div className={`overflow-hidden ${useFullPage ? 'border border-[#a3a3a3] bg-white' : 'rounded-xl border border-cyber-cyan/20 bg-slate-950/80'}`}>
                    <label className={`block px-3 pt-2 text-[9px] font-semibold uppercase ${useFullPage ? 'text-[#525252]' : 'font-mono font-bold text-slate-500'}`}>Duración (min)</label>
                    <input type="number" value={durationInMinutes} onChange={(e) => setDurationInMinutes(e.target.value)} className={`w-full pb-3 px-3 text-lg font-medium focus:outline-none focus:ring-0 border-none ${useFullPage ? 'bg-white text-[#1a1a1a] [color-scheme:light]' : 'bg-slate-950/50 text-white font-mono font-bold [color-scheme:dark]'}`} placeholder="60" />
                </div>
                <div className={`overflow-hidden ${useFullPage ? 'border border-[#a3a3a3] bg-white' : 'rounded-xl border border-cyber-cyan/20 bg-slate-950/80'}`}>
                    <label className={`block px-3 pt-2 text-[9px] font-semibold uppercase ${useFullPage ? 'text-[#525252]' : 'font-mono font-bold text-slate-500'}`}>Fecha</label>
                    <input type="date" value={logDate} onChange={e => setLogDate(e.target.value)} className={`w-full pb-3 px-3 text-sm font-medium focus:outline-none focus:ring-0 border-none ${useFullPage ? 'bg-white text-[#1a1a1a] [color-scheme:light]' : 'bg-slate-950/50 text-white font-mono font-bold [color-scheme:dark]'}`} />
                </div>
            </div>
            
            {/* Baterías AUGE: selectores de puntos 1–10 */}
            <div className={`p-4 space-y-5 ${useFullPage ? 'bg-white border border-[#a3a3a3]' : 'rounded-xl border border-cyber-cyan/20 bg-slate-950/60'}`}>
                <p className={`text-[10px] font-semibold uppercase tracking-wide ${useFullPage ? 'text-[#525252]' : 'font-mono text-slate-500'}`}>Batería consumida (1–10)</p>
                <BatteryPointSelector label="1. Estado general" value={generalBattery} onChange={setGeneralBattery} />
                
                <div>
                    <button type="button" onClick={() => setMuscleAccordionOpen(!muscleAccordionOpen)} className={`w-full flex items-center justify-between text-[10px] font-semibold uppercase tracking-wide mb-2 py-1 ${useFullPage ? 'text-[#525252]' : 'font-mono font-bold text-slate-400'}`}>
                        <span>2. Músculos trabajados</span>
                        {muscleAccordionOpen ? <ChevronDownIcon size={14} className={useFullPage ? 'text-[#525252]' : 'text-cyber-cyan'} /> : <ChevronRightIcon size={14} className={useFullPage ? 'text-[#525252]' : 'text-cyber-cyan'} />}
                    </button>
                    {muscleAccordionOpen && musclesWithEffectiveSets.length > 0 && (
                        <div className={`space-y-3 mt-3 animate-fade-in pl-2 ${useFullPage ? 'border-l-2 border-[#a3a3a3]' : 'border-l-2 border-cyber-cyan/20'}`}>
                            {musclesWithEffectiveSets.map(m => (
                                <BatteryPointSelector key={m} label={MUSCLE_LABEL_MAP[m] || m} value={muscleBatteries[m] ?? 50} onChange={(v) => setMuscleBatteries(prev => ({ ...prev, [m]: v }))} />
                            ))}
                        </div>
                    )}
                    {muscleAccordionOpen && musclesWithEffectiveSets.length === 0 && (
                        <p className={`text-[10px] ${useFullPage ? 'text-[#737373]' : 'text-slate-600 font-mono'}`}>No hay músculos con series efectivas.</p>
                    )}
                </div>

                <BatteryPointSelector label="3. Columna" value={spinalBattery} onChange={setSpinalBattery} />
            </div>

            <div className="space-y-4 pt-1">
                <div className={`overflow-hidden ${useFullPage ? 'bg-white border border-[#a3a3a3]' : 'rounded-xl border border-cyber-cyan/20 bg-slate-950/60'}`}>
                    <button type="button" onClick={() => setShowDiscomfortSearch(!showDiscomfortSearch)} className={`w-full flex items-center justify-between px-4 py-3 text-[10px] font-semibold uppercase tracking-wide ${useFullPage ? 'text-[#525252] hover:bg-[#f5f5f5]' : 'font-mono font-bold text-slate-400 hover:text-cyber-cyan/90'}`}>
                        <span>¿Tuviste alguna molestia?</span>
                        {showDiscomfortSearch ? <ChevronDownIcon size={14} className={useFullPage ? 'text-[#525252] shrink-0' : 'text-cyber-cyan shrink-0'} /> : <ChevronRightIcon size={14} className={useFullPage ? 'text-[#525252] shrink-0' : 'text-cyber-cyan shrink-0'} />}
                    </button>
                    {showDiscomfortSearch && (
                        <div className={`animate-fade-in space-y-3 px-4 pb-4 pt-3 border-t ${useFullPage ? 'border-[#d4d4d4]' : 'border-cyber-cyan/10'}`}>
                            <div className={`flex items-center gap-2 p-2 ${useFullPage ? 'bg-[#f5f5f5] border border-[#a3a3a3]' : 'bg-slate-900/80 rounded-lg border border-cyber-cyan/20'}`}>
                                <SearchIcon size={14} className={useFullPage ? 'text-[#737373] shrink-0' : 'text-cyber-cyan/60 shrink-0'} />
                                <input type="text" value={discomfortSearchQuery} onChange={(e) => setDiscomfortSearchQuery(e.target.value)} placeholder="Describe tu molestia o busca..." className={`bg-transparent border-none text-sm w-full focus:ring-0 focus:outline-none ${useFullPage ? 'text-[#1a1a1a] placeholder:text-[#a3a3a3]' : 'text-white placeholder-slate-500'}`} />
                            </div>
                            <div className="max-h-40 overflow-y-auto custom-scrollbar space-y-2">
                                {filteredDiscomforts.map(d => (
                                    <button key={d.id} type="button" onClick={() => toggleDiscomfort(d.name)} className={`w-full text-left p-3 border transition-all ${useFullPage ? (selectedDiscomforts.includes(d.name) ? 'bg-[#525252] border-[#525252] text-white' : 'bg-white border-[#a3a3a3] text-[#1a1a1a] hover:border-[#737373]') : (selectedDiscomforts.includes(d.name) ? 'rounded-lg bg-cyber-cyan/20 border-cyber-cyan/50' : 'rounded-lg bg-slate-800/50 border-cyber-cyan/10 hover:border-cyber-cyan/30')}`}>
                                        <span className="font-semibold text-sm">{d.name}</span>
                                        <p className={`text-[10px] mt-1 ${useFullPage ? (selectedDiscomforts.includes(d.name) ? 'text-white/80' : 'text-[#737373]') : 'text-slate-400'}`}>{d.description}</p>
                                    </button>
                                ))}
                            </div>
                            {selectedDiscomforts.length > 0 && (
                                <div className="flex flex-wrap gap-1">
                                    {selectedDiscomforts.map(name => (
                                        <span key={name} className={`px-2 py-0.5 text-[10px] font-medium flex items-center gap-1 inline-flex ${useFullPage ? 'bg-[#525252] text-white' : 'rounded-full bg-cyber-cyan/30 text-cyber-cyan font-bold'}`}>{name} <button type="button" onClick={() => toggleDiscomfort(name)} className={useFullPage ? 'opacity-80 hover:opacity-100' : 'ml-1 opacity-70 hover:text-white'}>×</button></span>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
                <div className={`p-4 space-y-4 ${useFullPage ? 'bg-white border border-[#a3a3a3]' : 'rounded-xl border border-cyber-cyan/20 bg-slate-950/60'}`}>
                    <TagGroup title="Entorno" tags={ENVIRONMENT_TAGS} selected={environmentTags} onToggle={(tag) => toggleTag(tag, environmentTags, setEnvironmentTags)} />
                    <TagGroup title="Adherencia" tags={PLAN_ADHERENCE_TAGS} selected={planAdherenceTags} onToggle={(tag) => toggleTag(tag, planAdherenceTags, setPlanAdherenceTags)} />
                </div>
            </div>
            
            <div className={`overflow-hidden ${useFullPage ? 'bg-white border border-[#a3a3a3]' : 'rounded-xl border border-cyber-cyan/20 bg-slate-950/60'}`}>
              <label className={`block px-3 pt-3 text-[10px] font-semibold uppercase tracking-wide ${useFullPage ? 'text-[#525252]' : 'font-mono font-bold text-slate-500'}`}>Notas del Diario</label>
              <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} placeholder="¿Algo que destacar hoy?" className={`w-full px-3 pb-3 pt-1 text-sm focus:outline-none focus:ring-0 border-none resize-none ${useFullPage ? 'bg-white text-[#1a1a1a] placeholder:text-[#a3a3a3]' : 'font-mono bg-transparent text-white placeholder-slate-500'}`} />
            </div>
            <div className={`flex gap-2 pt-4 border-t ${useFullPage ? 'border-[#a3a3a3]' : 'border-cyber-cyan/20'}`}>
                <button onClick={handleShare} disabled={isSharing} className={`flex-1 py-4 font-semibold uppercase tracking-wide flex items-center justify-center gap-2 ${useFullPage ? 'bg-white text-[#1a1a1a] border border-[#a3a3a3] hover:bg-[#f5f5f5] disabled:opacity-50' : '!border-cyber-cyan/30 hover:!border-cyber-cyan/50 !bg-slate-900/80 transition-all font-mono'}`}>
                    {isSharing ? (
                        <div className="flex items-center gap-2">
                            <div className="w-4 h-4 border-2 border-[#737373] border-t-[#1a1a1a] rounded-full animate-spin"></div>
                            <span className={`text-[10px] tracking-wide ${useFullPage ? 'text-[#525252]' : 'text-cyber-cyan/90'}`}>Compartiendo...</span>
                        </div>
                    ) : (
                        <><LinkIcon size={18} className={useFullPage ? 'text-[#525252]' : 'text-cyber-cyan/80'}/> <span className={`text-[11px] tracking-wide ${useFullPage ? 'text-[#1a1a1a]' : 'text-cyber-cyan/90'}`}>Compartir</span></>
                    )}
                </button>
                <button onClick={handleFinishAttempt} className={`flex-[2] py-4 text-base font-semibold uppercase tracking-wide flex items-center justify-center gap-2 ${useFullPage ? 'bg-white text-[#1a1a1a] border border-[#a3a3a3]' : '!bg-cyber-cyan !text-black !border-cyber-cyan hover:!bg-cyber-cyan/90 font-mono font-black tracking-widest shadow-[0_0_20px_rgba(0,240,255,0.3)]'}`}>
                    <CheckCircleIcon size={20}/> Finalizar sesión
                </button>
            </div>
        </div>
      ) : (
          <div className={`space-y-6 p-4 animate-fade-in ${useFullPage ? 'bg-[#e5e5e5]' : 'bg-[#0a0c10] rounded-xl'}`}>
              <div className={`p-4 text-center ${useFullPage ? 'bg-white border border-[#a3a3a3]' : 'bg-cyber-cyan/10 border border-cyber-cyan/30 rounded-xl'}`}>
                <ActivityIcon size={40} className={`mx-auto mb-2 ${useFullPage ? 'text-[#525252]' : 'text-cyber-cyan'}`} />
                <h4 className={`text-lg font-bold uppercase tracking-wider ${useFullPage ? 'text-[#1a1a1a]' : 'text-cyber-cyan font-mono'}`}>Señales de Fatiga Acumulada</h4>
                <p className={`text-sm mt-1 ${useFullPage ? 'text-[#525252]' : 'text-slate-400 font-mono'}`}>Tu nivel de fatiga ({fatigueLevel}) y claridad mental ({mentalClarity}) sugieren que necesitas un descanso para evitar el sobreentrenamiento.</p>
              </div>

              <div className="space-y-3">
                  <p className={`text-sm text-center italic ${useFullPage ? 'text-[#525252]' : 'text-slate-300 font-mono'}`}>"He detectado que tu sistema nervioso está bajo estrés. ¿Insertamos una sesión de descarga mañana?"</p>
                  
                  <button onClick={handleAcceptDeload} className={`w-full py-4 flex items-center justify-center gap-2 font-semibold uppercase tracking-wide ${useFullPage ? 'bg-white text-[#1a1a1a] border border-[#a3a3a3]' : '!bg-cyber-cyan !text-black !border-cyber-cyan hover:!bg-cyber-cyan/90 font-mono font-black tracking-widest'}`}>
                    <ZapIcon size={20}/> Sí, programar Descarga / Descanso Activo
                  </button>
                  
                  <button onClick={executeFinish} className={`w-full py-4 flex items-center justify-center gap-2 font-semibold uppercase tracking-wide ${useFullPage ? 'bg-white text-[#1a1a1a] border border-[#a3a3a3]' : '!border-cyber-cyan/30 hover:!border-cyber-cyan/50 !bg-slate-900/80'}`}>
                    <BrainIcon size={20}/> No, seguiré con mi programa habitual
                  </button>
              </div>
          </div>
      )}
    </>
  );

  if (!isOpen) return null;

  if (useFullPage) {
    return (
      <div className="fixed inset-0 z-[100000] flex flex-col bg-[#e5e5e5] animate-fade-in">
        <div className="flex items-center justify-between px-4 py-3 shrink-0 border-b border-[#a3a3a3] bg-white">
          <h3 className="text-[10px] font-semibold uppercase tracking-wide text-[#1a1a1a]">{title}</h3>
          <button onClick={onClose} className="p-2 -mr-2 text-[#525252] hover:text-[#1a1a1a] transition-colors" aria-label="Cerrar">
            <span className="text-xl leading-none">×</span>
          </button>
        </div>
        <div className="flex-1 overflow-y-auto min-h-0 pb-8 px-4" style={{ WebkitOverflowScrolling: 'touch' }}>
          {content}
        </div>
      </div>
    );
  }

  if (asDrawer) {
    return (
      <WorkoutDrawer isOpen={isOpen} onClose={onClose} title={title} height="90vh">
        <div className="p-5 overflow-y-auto max-h-[85vh]">{content}</div>
      </WorkoutDrawer>
    );
  }
  return (
    <TacticalModal isOpen={isOpen} onClose={onClose} title={title} className="!bg-[#FEF7FF] !border-cyber-cyan/20">
      {content}
    </TacticalModal>
  );
};

export default FinishWorkoutModal;
