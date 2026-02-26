
// components/FinishWorkoutModal.tsx
import React, { useState, useEffect, useRef, useMemo } from 'react';
import { TacticalModal } from './ui/TacticalOverlays';
import WorkoutDrawer from './workout/WorkoutDrawer';
import Button from './ui/Button';
import { DISCOMFORT_DATABASE } from '../data/discomfortList';
import { CheckCircleIcon, ZapIcon, BrainIcon, ActivityIcon, LinkIcon, ClockIcon, FlameIcon, ChevronDownIcon, ChevronRightIcon, SearchIcon } from './icons';
import { useAppDispatch, useAppState } from '../contexts/AppContext';
import { shareElementAsImage } from '../services/shareService';
import CaupolicanBackground from './social/CaupolicanBackground';
import { getLocalDateString } from '../utils/dateUtils';
import { isSetEffective } from '../services/auge';
import type { Exercise, ExerciseMuscleInfo } from '../types';

interface FinishWorkoutModalProps {
  isOpen: boolean;
  onClose: () => void;
  onFinish: (notes?: string, discomforts?: string[], fatigueLevel?: number, mentalClarity?: number, durationInMinutes?: number, logDate?: string, photo?: string, planDeviations?: any[], focus?: number, pump?: number, environmentTags?: string[], sessionDifficulty?: number, planAdherenceTags?: string[]) => void;
  mode?: 'live' | 'log';
  improvementIndex?: { percent: number; direction: 'up' | 'down' | 'neutral' } | null;
  initialDurationInSeconds?: number;
  initialNotes?: string;
  asDrawer?: boolean;
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

export const WorkoutShareCard: React.FC<ShareCardData & { preview?: boolean }> = ({ date, duration, difficulty, pump, exerciseSummaries, preview }) => {
    const wrapperClass = preview
        ? 'relative w-[540px] h-[960px] bg-black text-white overflow-hidden flex flex-col font-sans rounded-xl'
        : 'fixed top-0 left-[-2000px] w-[540px] h-[960px] bg-black text-white overflow-hidden pointer-events-none z-[-10] flex flex-col font-sans';
    return (
        <div id={preview ? undefined : 'workout-summary-share-card'} className={wrapperClass}>
             <div className="absolute inset-0 z-0 opacity-40 mix-blend-luminosity scale-110 -translate-y-10">
                <CaupolicanBackground />
            </div>
            <div className="absolute inset-0 bg-gradient-to-b from-black/80 via-black/40 to-[#0a0a0a] z-0" />
            <div className="absolute inset-0 opacity-[0.15] mix-blend-overlay z-0" style={{ backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")` }}></div>

            <div className="relative z-10 flex flex-col h-full p-12 justify-between">
                <div className="flex justify-between items-start">
                    <div className="flex flex-col">
                        <span className="text-[14px] font-black text-white/40 uppercase tracking-[0.4em] mb-1">Sesión de entrenamiento</span>
                        <span className="text-xl font-bold text-white uppercase tracking-widest">
                            {new Date(date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })}
                        </span>
                    </div>
                    <div className="w-16 h-16 rounded-2xl shadow-[0_0_40px_rgba(255,255,255,0.3)] overflow-hidden bg-white flex items-center justify-center">
                        <img src="/caupolican-icon.svg" alt="" className="w-full h-full object-contain p-1" aria-hidden />
                    </div>
                </div>

                {/* Ejercicios + barra de gradiente */}
                <div className="flex flex-col flex-1 min-h-0 mt-6 mb-6">
                    <div className="flex-1 overflow-hidden">
                        {exerciseSummaries.length > 0 ? (
                            <div className="space-y-2 max-h-[280px] overflow-y-auto custom-scrollbar pr-1">
                                {exerciseSummaries.map((ex, i) => (
                                    <div key={i} className="flex flex-col gap-0.5">
                                        <span className="text-sm font-bold text-white">{ex.name}</span>
                                        <span className="text-xs text-cyan-400/90 font-mono">{ex.line}</span>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className="h-24 flex items-center justify-center">
                                <span className="text-white/40 text-sm">Ejercicios de la sesión</span>
                            </div>
                        )}
                    </div>
                    <div className="h-1 w-full mt-4 rounded-full bg-gradient-to-r from-red-500 to-cyan-500" />
                </div>

                <div className="grid grid-cols-3 gap-4 mb-6">
                     <div className="bg-zinc-900/80 backdrop-blur-xl border border-white/10 rounded-3xl p-6 flex flex-col items-center justify-center shadow-2xl">
                         <ClockIcon size={32} className="text-white/50 mb-3" />
                         <p className="text-[3rem] font-black text-white leading-none">{duration}</p>
                         <p className="text-[11px] text-zinc-400 font-bold uppercase tracking-widest mt-2">Minutos</p>
                     </div>
                     <div className="bg-zinc-900/80 backdrop-blur-xl border border-white/10 rounded-3xl p-6 flex flex-col items-center justify-center shadow-2xl">
                         <ZapIcon size={32} className="text-yellow-500/80 mb-3" />
                         <p className="text-[3rem] font-black text-yellow-400 leading-none">{difficulty}</p>
                         <p className="text-[11px] text-zinc-400 font-bold uppercase tracking-widest mt-2">Intensidad</p>
                     </div>
                     <div className="bg-zinc-900/80 backdrop-blur-xl border border-white/10 rounded-3xl p-6 flex flex-col items-center justify-center shadow-2xl">
                         <ActivityIcon size={32} className="text-red-500/80 mb-3" />
                         <p className="text-[3rem] font-black text-red-400 leading-none">{pump}</p>
                         <p className="text-[11px] text-zinc-400 font-bold uppercase tracking-widest mt-2">Pump</p>
                     </div>
                </div>

                <div className="text-center pt-6 border-t border-white/10">
                     <p className="text-sm font-black text-white/50 uppercase tracking-[0.3em]">Registra tus entrenamientos con KPKN</p>
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

const FinishWorkoutModal: React.FC<FinishWorkoutModalProps> = ({ isOpen, onClose, onFinish, mode = 'live', improvementIndex, initialDurationInSeconds, initialNotes, asDrawer, allExercises = [], completedSets = {}, exerciseList = [] }) => {
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
        const raw = completedSets[String(set.id)] as { left?: any; right?: any } | undefined;
        const data = ex.isUnilateral ? (raw?.left || raw?.right) : raw?.left;
        if (data && ((data.reps != null && data.reps > 0) || (data.weight != null && data.weight > 0))) {
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
        const data = dataRaw as { left: any; right: any };
        const primary = ex.isUnilateral ? (data.left || data.right) : data.left;
        if (!primary) return;
        const setLike = { ...primary, completedRPE: primary.rpe, completedRIR: primary.rir, targetRPE: set.targetRPE, targetRIR: set.targetRIR, intensityMode: set.intensityMode };
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
  }, [isOpen, initialDurationInSeconds, initialNotes]);

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
    onFinish(notes, selectedDiscomforts, fatigueLevel, mentalClarity, durationNum, logDate, undefined, [], focus, pumpMapped, environmentTags, sessionDifficulty, planAdherenceTags);
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

  const TagGroup: React.FC<{title:string; tags: string[]; selected: string[]; onToggle: (tag:string) => void;}> = ({title, tags, selected, onToggle}) => (
      <div>
        <label className="block text-sm font-medium text-slate-300 mb-2">{title}</label>
        <div className="flex flex-wrap gap-2">
            {tags.map(tag => (
                <button key={tag} onClick={() => onToggle(tag)} className={`px-2 py-1 text-[10px] uppercase font-black rounded-full border transition-all ${selected.includes(tag) ? 'bg-primary-color border-primary-color text-white' : 'bg-slate-800 border-slate-700 text-slate-400'}`}>
                    {tag}
                </button>
            ))}
        </div>
    </div>
  );

  const BatterySlider: React.FC<{ label: string; value: number; onChange: (v: number) => void; color?: string }> = ({ label, value, onChange, color = 'accent-sky-500' }) => (
    <div>
      <label className="block text-[10px] font-black text-slate-400 uppercase tracking-widest mb-2">{label}</label>
      <div className="flex items-center gap-3">
        <input type="range" min="0" max="100" value={value} onChange={(e) => onChange(parseInt(e.target.value))} className={`w-full h-2 rounded-lg appearance-none cursor-pointer bg-black border border-white/10 ${color}`} />
        <span className="text-xl font-black text-white w-12 text-right tabular-nums">{value}%</span>
      </div>
    </div>
  );

  const title = showRecoverySuggestion ? "Recuperación Prioritaria" : "Finalizar Sesión";
  const content = (
    <>
      <WorkoutShareCard 
          date={logDate} 
          duration={durationInMinutes || '--'} 
          difficulty={sessionDifficulty} 
          pump={pump}
          exerciseSummaries={shareCardExerciseSummaries}
      />

      {!showRecoverySuggestion ? (
        <div className="space-y-6 p-2">
            {/* Shareable visual summary header (In-app view) */}
            <div className="bg-gradient-to-br from-slate-900 to-black p-4 rounded-xl border border-white/10 text-center mb-2">
                <p className="text-xs text-slate-400 uppercase tracking-widest font-bold">Sesión Completada</p>
                <h3 className="text-2xl font-black text-white my-2">{new Date(logDate).toLocaleDateString()}</h3>
                <div className="flex justify-center gap-4 text-sm font-mono text-primary-color">
                    <span>{durationInMinutes} min</span>
                    <span>•</span>
                    <span>Nivel {sessionDifficulty}</span>
                </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div className="relative">
                    <span className="absolute left-2 top-1 text-[8px] font-black text-slate-500 uppercase z-10">Duración (min)</span>
                    <input type="number" value={durationInMinutes} onChange={(e) => setDurationInMinutes(e.target.value)} className="w-full pt-4 pb-1 px-2 !text-lg font-bold" placeholder="Ej: 60" />
                </div>
                <div className="relative">
                    <span className="absolute left-2 top-1 text-[8px] font-black text-slate-500 uppercase z-10">Fecha</span>
                    <input type="date" value={logDate} onChange={e => setLogDate(e.target.value)} className="w-full pt-4 pb-1 px-2 !text-sm font-bold"/>
                </div>
            </div>
            
            <p className="text-xs text-slate-400 italic mb-4">Si tu cuerpo tuviese tres baterías — una para tu estado general, otra para tus músculos y una última para tu columna — del 0 al 100%:</p>
            
            <div className="space-y-6 bg-zinc-950/50 p-5 rounded-3xl border border-white/5 shadow-inner">
                <BatterySlider label="1. ¿Cuánto % de batería de tu estado general te consumió esta sesión?" value={generalBattery} onChange={setGeneralBattery} color="accent-sky-500" />
                
                <div>
                    <button type="button" onClick={() => setMuscleAccordionOpen(!muscleAccordionOpen)} className="w-full flex items-center justify-between text-[10px] font-black text-slate-300 uppercase tracking-widest mb-2">
                        <span>2. ¿Cuánto % de batería de los músculos que trabajaste te consumió esta sesión?</span>
                        {muscleAccordionOpen ? <ChevronDownIcon size={14} /> : <ChevronRightIcon size={14} />}
                    </button>
                    {muscleAccordionOpen && musclesWithEffectiveSets.length > 0 && (
                        <div className="space-y-3 mt-3 animate-fade-in">
                            {musclesWithEffectiveSets.map(m => (
                                <BatterySlider key={m} label={MUSCLE_LABEL_MAP[m] || m} value={muscleBatteries[m] ?? 50} onChange={(v) => setMuscleBatteries(prev => ({ ...prev, [m]: v }))} color="accent-rose-500" />
                            ))}
                        </div>
                    )}
                    {muscleAccordionOpen && musclesWithEffectiveSets.length === 0 && (
                        <p className="text-[10px] text-slate-500 italic">No hay músculos con series efectivas registradas.</p>
                    )}
                </div>

                <BatterySlider label="3. ¿Cuánto % de batería de tu columna te consumió esta sesión?" value={spinalBattery} onChange={setSpinalBattery} color="accent-amber-500" />
            </div>

            <div className="space-y-3 pt-2">
                <div>
                    <button type="button" onClick={() => setShowDiscomfortSearch(!showDiscomfortSearch)} className="flex items-center gap-2 text-sm font-medium text-slate-300 mb-2">
                        ¿Tuviste alguna molestia?
                        {showDiscomfortSearch ? <ChevronDownIcon size={14} /> : <ChevronRightIcon size={14} />}
                    </button>
                    {showDiscomfortSearch && (
                        <div className="animate-fade-in space-y-3">
                            <div className="flex items-center gap-2 bg-slate-800/50 p-2 rounded-lg border border-white/5">
                                <SearchIcon size={14} className="text-slate-500" />
                                <input type="text" value={discomfortSearchQuery} onChange={(e) => setDiscomfortSearchQuery(e.target.value)} placeholder="Describe tu molestia o busca..." className="bg-transparent border-none text-sm w-full focus:ring-0" />
                            </div>
                            <div className="max-h-40 overflow-y-auto custom-scrollbar space-y-2">
                                {filteredDiscomforts.map(d => (
                                    <button key={d.id} type="button" onClick={() => toggleDiscomfort(d.name)} className={`w-full text-left p-3 rounded-lg border transition-all ${selectedDiscomforts.includes(d.name) ? 'bg-primary-color/20 border-primary-color/50' : 'bg-slate-800/50 border-white/5 hover:border-white/10'}`}>
                                        <span className="font-bold text-sm text-white">{d.name}</span>
                                        <p className="text-[10px] text-slate-400 mt-1">{d.description}</p>
                                    </button>
                                ))}
                            </div>
                            {selectedDiscomforts.length > 0 && (
                                <div className="flex flex-wrap gap-1">
                                    {selectedDiscomforts.map(name => (
                                        <span key={name} className="px-2 py-0.5 rounded-full bg-primary-color/30 text-primary-color text-[10px] font-bold">{name} <button type="button" onClick={() => toggleDiscomfort(name)} className="ml-1 opacity-70">×</button></span>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
                <TagGroup title="Entorno" tags={ENVIRONMENT_TAGS} selected={environmentTags} onToggle={(tag) => toggleTag(tag, environmentTags, setEnvironmentTags)} />
                <TagGroup title="Adherencia" tags={PLAN_ADHERENCE_TAGS} selected={planAdherenceTags} onToggle={(tag) => toggleTag(tag, planAdherenceTags, setPlanAdherenceTags)} />
            </div>
            
            <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">Notas del Diario</label>
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} className="w-full" placeholder="¿Algo que destacar hoy?"/>
            </div>
            <div className="flex gap-2 pt-4 border-t border-border-color">
                <Button onClick={handleShare} variant="secondary" className="flex-1 !py-4 border-white/10 hover:border-white/30 hover:bg-white/5 transition-all" disabled={isSharing}>
                    {isSharing ? (
                        <div className="flex items-center gap-2">
                            <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                            <span className="text-[10px] tracking-widest">FORJANDO...</span>
                        </div>
                    ) : (
                        <><LinkIcon size={18} className="text-white/70"/> <span className="text-[11px] tracking-widest">HISTORIA</span></>
                    )}
                </Button>
                <Button onClick={handleFinishAttempt} variant="primary" className="flex-[2] !py-4 !text-base shadow-[0_0_20px_rgba(255,255,255,0.1)]">
                    <CheckCircleIcon size={20}/> FINALIZAR
                </Button>
            </div>
        </div>
      ) : (
          <div className="space-y-6 p-2 animate-fade-in">
              <div className="bg-yellow-900/20 border border-yellow-500/50 p-4 rounded-xl text-center">
                <ActivityIcon size={40} className="mx-auto text-yellow-400 mb-2" />
                <h4 className="text-lg font-bold text-yellow-100">Señales de Fatiga Acumulada</h4>
                <p className="text-sm text-yellow-200/70 mt-1">Tu nivel de fatiga ({fatigueLevel}) y claridad mental ({mentalClarity}) sugieren que necesitas un descanso para evitar el sobreentrenamiento.</p>
              </div>

              <div className="space-y-3">
                  <p className="text-sm text-slate-300 font-semibold text-center italic">"He detectado que tu sistema nervioso está bajo estrés. ¿Insertamos una sesión de descarga mañana?"</p>
                  
                  <Button onClick={handleAcceptDeload} className="w-full !py-4 !justify-start !bg-green-600 !border-green-500">
                    <ZapIcon size={20}/> Sí, programar Descarga / Descanso Activo
                  </Button>
                  
                  <Button onClick={executeFinish} variant="secondary" className="w-full !py-4 !justify-start">
                    <BrainIcon size={20}/> No, seguiré con mi programa habitual
                  </Button>
              </div>
          </div>
      )}
    </>
  );

  if (asDrawer) {
    return (
      <WorkoutDrawer isOpen={isOpen} onClose={onClose} title={title} height="90vh">
        <div className="p-5 overflow-y-auto max-h-[85vh]">{content}</div>
      </WorkoutDrawer>
    );
  }
  return (
    <TacticalModal isOpen={isOpen} onClose={onClose} title={title}>
      {content}
    </TacticalModal>
  );
};

export default FinishWorkoutModal;
