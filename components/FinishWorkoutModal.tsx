
// components/FinishWorkoutModal.tsx
import React, { useState, useEffect, useRef, useMemo } from 'react';
import { TacticalModal } from './ui/TacticalOverlays';
import WorkoutDrawer from './workout/WorkoutDrawer';
import { DISCOMFORT_DATABASE } from '../data/discomfortList';
import { CheckCircleIcon, ZapIcon, BrainIcon, ActivityIcon, LinkIcon, ClockIcon, FlameIcon, ChevronDownIcon, ChevronRightIcon, SearchIcon, TrophyIcon, AlertTriangleIcon } from './icons';
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

export const WorkoutShareCard: React.FC<ShareCardData & { preview?: boolean }> = ({ date, duration, difficulty, pump, exerciseSummaries, preview }) => {
  const displayed = exerciseSummaries.slice(0, MAX_EXERCISES_IN_SHARE);
  const restCount = exerciseSummaries.length - MAX_EXERCISES_IN_SHARE;

  const wrapperClass = preview
    ? 'relative w-[540px] h-[960px] flex items-center justify-center font-sans overflow-hidden'
    : 'fixed w-[540px] h-[960px] flex items-center justify-center font-sans overflow-hidden pointer-events-none';
  const captureStyle: React.CSSProperties = preview ? {} : { left: 0, top: 0, zIndex: -1 };

  return (
    <div id={preview ? undefined : 'workout-summary-share-card'} className={wrapperClass} style={preview ? undefined : captureStyle}>
      {/* Dynamic Background with depth */}
      <div className="absolute inset-0 bg-[#0f0f11]" />
      <div className="absolute inset-0 opacity-40 mix-blend-overlay" style={{ backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")` }} />

      {/* Decorative Orbs */}
      <div className="absolute top-[-10%] right-[-10%] w-[400px] h-[400px] rounded-full bg-[var(--m3-primary)]/20 blur-[120px]" />
      <div className="absolute bottom-[5%] left-[-20%] w-[500px] h-[500px] rounded-full bg-[var(--m3-tertiary)]/15 blur-[140px]" />

      {/* Main Glass Card */}
      <div className="relative w-[480px] h-[860px] rounded-[48px] overflow-hidden border border-white/10 shadow-2xl flex flex-col"
        style={{
          background: 'rgba(255, 255, 255, 0.03)',
          backdropFilter: 'blur(40px)',
          WebkitBackdropFilter: 'blur(40px)',
          boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.05), 0 25px 50px -12px rgba(0,0,0,0.5)'
        }}>

        {/* Subtle texture in glass */}
        <div className="absolute inset-0 z-0 opacity-10 mix-blend-soft-light pointer-events-none">
          <CaupolicanBackground />
        </div>

        <div className="relative z-10 flex flex-col h-full p-8 text-white">
          {/* Top Brand Header */}
          <div className="flex justify-between items-start mb-8">
            <div className="space-y-1">
              <span className="text-[10px] font-black text-[var(--m3-primary)] uppercase tracking-[0.3em] block">KPKN FIT</span>
              <h1 className="text-2xl font-black tracking-tight leading-none text-white">Workout Summary</h1>
              <p className="text-xs font-medium text-white/50">{new Date(date).toLocaleDateString('es-ES', { day: '2-digit', month: 'long', year: 'numeric' })}</p>
            </div>
            <div className="w-14 h-14 rounded-2xl bg-white/10 border border-white/10 flex items-center justify-center p-2.5 backdrop-blur-md">
              <img src="/caupolican-icon.svg" alt="logo" className="w-full h-full object-contain" />
            </div>
          </div>

          {/* Core Stats Row */}
          <div className="grid grid-cols-3 gap-3 mb-8">
            {[
              { label: 'Tiempo', value: duration, unit: 'min', icon: <ClockIcon size={14} /> },
              { label: 'Dificultad', value: difficulty, unit: '/10', icon: <FlameIcon size={14} /> },
              { label: 'Pump', value: pump, unit: '/10', icon: <ActivityIcon size={14} /> }
            ].map((s, i) => (
              <div key={i} className="bg-white/5 border border-white/10 rounded-2xl p-4 flex flex-col items-center">
                <div className="text-white/40 mb-2">{s.icon}</div>
                <div className="flex items-baseline gap-0.5">
                  <span className="text-xl font-black text-white">{s.value}</span>
                  <span className="text-[8px] font-bold text-white/30 uppercase">{s.unit}</span>
                </div>
                <span className="text-[8px] font-bold uppercase tracking-wider text-white/40 mt-1">{s.label}</span>
              </div>
            ))}
          </div>

          {/* Exercise List - More elegant typography */}
          <div className="flex-1 min-h-0 py-2">
            <div className="flex items-center gap-2 mb-4">
              <div className="h-[1px] flex-1 bg-white/10" />
              <span className="text-[9px] font-black text-white/30 uppercase tracking-[0.2em]">Ejercicios</span>
              <div className="h-[1px] flex-1 bg-white/10" />
            </div>

            <div className="space-y-2 overflow-hidden">
              {displayed.map((ex, i) => (
                <div key={i} className="flex flex-col gap-0.5 group">
                  <div className="flex justify-between items-baseline gap-4">
                    <span className="text-[11px] font-bold text-white/90 truncate">{ex.name}</span>
                    <div className="h-[1px] flex-1 border-b border-dashed border-white/10" />
                    <span className="text-[10px] font-black text-[var(--m3-primary)]/80 tracking-tight shrink-0">{ex.line}</span>
                  </div>
                </div>
              ))}
            </div>
            {restCount > 0 && (
              <div className="mt-4 flex justify-center">
                <span className="px-3 py-1 rounded-full bg-white/5 border border-white/10 text-[9px] font-bold text-white/40 uppercase tracking-widest">+{restCount} ejercicios más</span>
              </div>
            )}
          </div>

          {/* Bottom Footer */}
          <div className="mt-auto pt-8 flex flex-col items-center">
            <div className="w-12 h-1 rounded-full bg-gradient-to-r from-[var(--m3-primary)] to-[var(--m3-tertiary)] mb-4 opacity-50" />
            <p className="text-[9px] font-black text-white/40 uppercase tracking-[0.4em] mb-1">UNLEASH YOUR POTENTIAL</p>
            <p className="text-[8px] text-white/20 font-medium">kpkn-fit.app</p>
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
          className={`w-7 h-7 rounded-full border transition-colors text-[10px] font-medium ${value === v ? 'bg-[var(--md-sys-color-on-surface-variant)] border-[var(--md-sys-color-on-surface-variant)] text-white' : 'bg-white border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface)] hover:border-[var(--md-sys-color-on-surface-variant)]'
            }`}
          aria-label={`${v} de 10`}
        >
          {v}
        </button>
      ))}
    </div>
    {labels && (
      <div className="flex justify-between w-full mt-0.5 text-[9px] text-[var(--md-sys-color-on-surface-variant)]">
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
    onFinish(notes, selectedDiscomforts, fatigueLevel, mentalClarity, durationNum, logDate, undefined, [], focus, pump, environmentTags, sessionDifficulty, planAdherenceTags, muscleBatteriesToPass);
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

  const TagGroup: React.FC<{ title: string; tags: string[]; selected: string[]; onToggle: (tag: string) => void; }> = ({ title, tags, selected, onToggle }) => (
    <div className="space-y-3">
      <label className="text-[11px] font-bold uppercase tracking-wider text-[var(--md-sys-color-on-surface-variant)] px-1">{title}</label>
      <div className="flex flex-wrap gap-2">
        {tags.map(tag => (
          <button
            key={tag}
            onClick={() => onToggle(tag)}
            className={`px-4 py-2 rounded-full text-[12px] font-medium transition-all border ${selected.includes(tag)
              ? 'bg-[var(--md-sys-color-primary-container)] border-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary-container)]'
              : 'bg-white/50 border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface-variant)]'
              }`}
          >
            {tag}
          </button>
        ))}
      </div>
    </div>
  );

  const BatteryPointSelector: React.FC<{ label: string; value: number; onChange: (v: number) => void }> = ({ label, value, onChange }) => {
    const point = Math.min(10, Math.max(1, Math.round(value / 10)));
    return (
      <div className="space-y-2">
        <label className="block text-[11px] font-bold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-wider px-1">{label}</label>
        <PointSelector value={point} onChange={(v) => onChange(v * 10)} labels={['Mínimo', 'Máximo']} />
      </div>
    );
  };

  const title = showRecoverySuggestion ? "Recuperación Prioritaria" : "Finalizar Sesión";

  const content = (
    <div className="space-y-6 pb-20">
      {/* Off-screen share card */}
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
        <>
          {/* Quick Summary Card */}
          <div className="liquid-glass-panel p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-[10px] font-bold uppercase tracking-[0.2em] text-[var(--md-sys-color-primary)] block mb-1">Resumen de Sesión</span>
                <h2 className="text-xl font-bold text-[var(--md-sys-color-on-surface)]">{new Date(logDate).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })}</h2>
              </div>
              <div className="w-12 h-12 rounded-2xl bg-[var(--md-sys-color-secondary-container)] flex items-center justify-center text-[var(--md-sys-color-on-secondary-container)]">
                <TrophyIcon size={24} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="bg-white/40 border border-[var(--md-sys-color-outline-variant)]/30 rounded-2xl p-3">
                <span className="text-[9px] font-bold uppercase text-[var(--md-sys-color-on-surface-variant)] block mb-1">Duración</span>
                <div className="flex items-center gap-2">
                  <input
                    type="number"
                    value={durationInMinutes}
                    onChange={(e) => setDurationInMinutes(e.target.value)}
                    className="bg-transparent border-none p-0 text-xl font-bold w-16 focus:ring-0 outline-none text-[var(--md-sys-color-on-surface)]"
                    placeholder="60"
                  />
                  <span className="text-xs font-medium text-[var(--md-sys-color-on-surface-variant)]">min</span>
                </div>
              </div>
              <div className="bg-white/40 border border-[var(--md-sys-color-outline-variant)]/30 rounded-2xl p-3">
                <span className="text-[9px] font-bold uppercase text-[var(--md-sys-color-on-surface-variant)] block mb-1">Dificultad</span>
                <div className="flex items-center gap-2">
                  <span className="text-xl font-bold text-[var(--md-sys-color-on-surface)]">{sessionDifficulty}</span>
                  <span className="text-xs font-medium text-[var(--md-sys-color-on-surface-variant)]">/ 10</span>
                </div>
              </div>
            </div>
          </div>

          {/* AUGE Subjective metrics */}
          <div className="liquid-glass-panel p-5 space-y-6">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-1 h-4 bg-[var(--md-sys-color-primary)] rounded-full" />
              <h3 className="text-sm font-bold text-[var(--md-sys-color-on-surface)] uppercase tracking-wider">Métricas de Recuperación</h3>
            </div>

            <BatteryPointSelector label="1. Fatiga General" value={generalBattery} onChange={setGeneralBattery} />
            <BatteryPointSelector label="2. Stress Percibido" value={spinalBattery} onChange={setSpinalBattery} />

            <div className="pt-2">
              <button
                type="button"
                onClick={() => setMuscleAccordionOpen(!muscleAccordionOpen)}
                className="w-full flex items-center justify-between p-4 rounded-2xl bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)]/50 transition-all active:scale-[0.98]"
              >
                <div className="flex items-center gap-3">
                  <ActivityIcon size={20} className="text-[var(--md-sys-color-primary)]" />
                  <span className="text-sm font-bold text-[var(--md-sys-color-on-surface)]">Puntaje por Músculo</span>
                </div>
                {muscleAccordionOpen ? <ChevronDownIcon size={18} /> : <ChevronRightIcon size={18} />}
              </button>

              {muscleAccordionOpen && (
                <div className="mt-4 space-y-6 pl-4 animate-fade-in">
                  {musclesWithEffectiveSets.length > 0 ? (
                    musclesWithEffectiveSets.map(m => (
                      <BatteryPointSelector key={m} label={MUSCLE_LABEL_MAP[m] || m} value={muscleBatteries[m] ?? 50} onChange={(v) => setMuscleBatteries(prev => ({ ...prev, [m]: v }))} />
                    ))
                  ) : (
                    <p className="text-xs text-[var(--md-sys-color-on-surface-variant)] italic">No se detectaron series efectivas suficientes.</p>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Context & Tags */}
          <div className="liquid-glass-panel p-5 space-y-8">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-1 h-4 bg-[var(--md-sys-color-secondary)] rounded-full" />
              <h3 className="text-sm font-bold text-[var(--md-sys-color-on-surface)] uppercase tracking-wider">Contexto de la Sesión</h3>
            </div>

            <div className="space-y-4">
              <label className="text-[11px] font-bold uppercase tracking-wider text-[var(--md-sys-color-on-surface-variant)] px-1">Percepción subjetiva</label>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-2">
                  <span className="text-[10px] text-[var(--md-sys-color-on-surface-variant)] font-medium text-center block">Foco</span>
                  <PointSelector value={focus} onChange={setFocus} />
                </div>
                <div className="space-y-2">
                  <span className="text-[10px] text-[var(--md-sys-color-on-surface-variant)] font-medium text-center block">Pump</span>
                  <PointSelector value={pump} onChange={setPump} />
                </div>
              </div>
            </div>

            <TagGroup title="Entorno" tags={ENVIRONMENT_TAGS} selected={environmentTags} onToggle={(tag) => toggleTag(tag, environmentTags, setEnvironmentTags)} />
            <TagGroup title="Adherencia al Plan" tags={PLAN_ADHERENCE_TAGS} selected={planAdherenceTags} onToggle={(tag) => toggleTag(tag, planAdherenceTags, setPlanAdherenceTags)} />

            {/* Discomforts */}
            <div className="space-y-4 pt-2">
              <label className="text-[11px] font-bold uppercase tracking-wider text-[var(--md-sys-color-on-surface-variant)] px-1">Molestias o Dolores</label>
              <button
                type="button"
                onClick={() => setShowDiscomfortSearch(!showDiscomfortSearch)}
                className={`w-full flex items-center justify-between p-4 rounded-2xl border transition-all ${selectedDiscomforts.length > 0
                  ? 'bg-[var(--md-sys-color-error-container)] border-[var(--md-sys-color-error)] text-[var(--md-sys-color-on-error-container)]'
                  : 'bg-white/50 border-[var(--md-sys-color-outline-variant)]'
                  }`}
              >
                <span className="text-sm font-bold">{selectedDiscomforts.length > 0 ? `${selectedDiscomforts.length} Molestias registradas` : '¿Alguna molestia física?'}</span>
                {showDiscomfortSearch ? <ChevronDownIcon size={18} /> : <SearchIcon size={18} />}
              </button>

              {showDiscomfortSearch && (
                <div className="animate-fade-in space-y-4 pt-2">
                  <div className="flex items-center gap-3 px-4 py-2 border border-[var(--md-sys-color-outline-variant)] rounded-full bg-white/60">
                    <SearchIcon size={16} className="text-[var(--md-sys-color-on-surface-variant)]" />
                    <input
                      type="text"
                      value={discomfortSearchQuery}
                      onChange={(e) => setDiscomfortSearchQuery(e.target.value)}
                      placeholder="Buscar síntoma..."
                      className="bg-transparent border-none text-sm w-full outline-none"
                    />
                  </div>
                  <div className="max-h-48 overflow-y-auto space-y-2 pr-1">
                    {filteredDiscomforts.map(d => (
                      <button
                        key={d.id}
                        type="button"
                        onClick={() => toggleDiscomfort(d.name)}
                        className={`w-full text-left p-3 rounded-2xl border transition-all ${selectedDiscomforts.includes(d.name)
                          ? 'bg-[var(--md-sys-color-primary)] border-[var(--md-sys-color-primary)] text-white'
                          : 'bg-white/40 border-[var(--md-sys-color-outline-variant)] hover:border-[var(--md-sys-color-primary)]'
                          }`}
                      >
                        <span className="font-bold text-sm block">{d.name}</span>
                        <p className={`text-[10px] mt-0.5 ${selectedDiscomforts.includes(d.name) ? 'text-white/80' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}>{d.description}</p>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Journal Notes */}
            <div className="space-y-3">
              <label className="text-[11px] font-bold uppercase tracking-wider text-[var(--md-sys-color-on-surface-variant)] px-1">Notas del Diario</label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
                placeholder="Cuéntanos cómo te sentiste hoy..."
                className="w-full liquid-glass-panel p-4 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--md-sys-color-primary)]/20 border-none resize-none bg-white/60 text-[var(--md-sys-color-on-surface)]"
              />
            </div>
          </div>

          {/* Bottom Actions */}
          <div className="fixed bottom-0 left-0 right-0 z-50 flex gap-3 border-t border-white/75 liquid-glass-panel p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
            <button
              onClick={handleShare}
              disabled={isSharing}
              className="h-14 px-6 rounded-full bg-[var(--md-sys-color-surface-container-high)] text-[var(--md-sys-color-on-surface)] font-bold flex items-center justify-center gap-2 border border-[var(--md-sys-color-outline-variant)]/40 active:scale-95 transition-all disabled:opacity-50"
            >
              {isSharing ? <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" /> : <LinkIcon size={20} />}
              <span className="text-[13px] uppercase tracking-wider">Compartir</span>
            </button>
            <button
              onClick={handleFinishAttempt}
              className="flex-1 h-14 rounded-full bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] font-black text-[13px] uppercase tracking-[0.1em] flex items-center justify-center gap-2 shadow-lg shadow-[var(--md-sys-color-primary)]/20 active:scale-95 transition-all"
            >
              <CheckCircleIcon size={22} />
              Finalizar Entrenamiento
            </button>
          </div>
        </>
      ) : (
        <div className="liquid-glass-panel p-6 space-y-8 animate-fade-in text-center">
          <div className="space-y-4">
            <div className="w-20 h-20 rounded-full bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-error)] flex items-center justify-center mx-auto mb-2">
              <AlertTriangleIcon size={40} />
            </div>
            <h2 className="text-2xl font-black text-[var(--md-sys-color-on-surface)]">Fatiga Crítica Detectada</h2>
            <p className="text-sm text-[var(--md-sys-color-on-surface-variant)] leading-relaxed">
              Tu sistema indica una fatiga de <strong>{fatigueLevel}/10</strong> y una claridad mental de <strong>{mentalClarity}/10</strong>.
              Continuar con este ritmo podría llevarte al sobreentrenamiento.
            </p>
          </div>

          <div className="bg-[var(--md-sys-color-secondary-container)]/30 p-5 rounded-3xl border border-[var(--md-sys-color-secondary-container)]">
            <p className="text-sm text-[var(--md-sys-color-on-secondary-container)] italic font-medium">
              "He analizado tus señales. Sugiero programar una descarga mañana para asegurar una recuperación óptima."
            </p>
          </div>

          <div className="space-y-3">
            <button
              onClick={handleAcceptDeload}
              className="w-full h-14 rounded-full bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] font-bold text-[14px] uppercase tracking-wide flex items-center justify-center gap-2 shadow-lg active:scale-95 transition-all"
            >
              <ZapIcon size={20} /> Programar Descanso Activo
            </button>

            <button
              onClick={executeFinish}
              className="w-full h-14 rounded-full border border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface-variant)] font-bold text-[14px] uppercase tracking-wide flex items-center justify-center gap-2 hover:bg-white/60 active:scale-95 transition-all"
            >
              Ignorar sugerencia y finalizar
            </button>
          </div>
        </div>
      )}
    </div>
  );

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[10000] flex flex-col bg-[var(--md-sys-color-surface-container)] animate-fade-in">
      <div className="flex items-center justify-between px-6 py-4 shrink-0 border-b border-white/75 liquid-glass-panel">
        <span className="text-[10px] font-black uppercase tracking-[0.2em] text-[var(--md-sys-color-on-surface-variant)]">{title}</span>
        <button
          onClick={onClose}
          className="w-10 h-10 rounded-full border border-[var(--md-sys-color-outline-variant)] bg-white/65 flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] active:scale-90 transition-transform"
        >
          <span className="text-2xl leading-none">×</span>
        </button>
      </div>
      <div className="flex-1 overflow-y-auto px-4 pt-4 custom-scrollbar">
        <div className="max-w-md mx-auto">
          {content}
        </div>
      </div>
    </div>
  );
};

export default FinishWorkoutModal;
