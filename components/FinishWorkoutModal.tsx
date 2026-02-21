
// components/FinishWorkoutModal.tsx
import React, { useState, useEffect, useRef } from 'react';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { DISCOMFORT_LIST } from '../data/discomfortList';
import { CheckCircleIcon, ArrowUpIcon, ArrowDownIcon, ZapIcon, BrainIcon, ActivityIcon, LinkIcon, DumbbellIcon, ClockIcon, FlameIcon } from './icons';
import { useAppDispatch } from '../contexts/AppContext';
import { shareElementAsImage } from '../services/shareService';
import CaupolicanBackground from './social/CaupolicanBackground';

interface FinishWorkoutModalProps {
  isOpen: boolean;
  onClose: () => void;
  onFinish: (notes?: string, discomforts?: string[], fatigueLevel?: number, mentalClarity?: number, durationInMinutes?: number, logDate?: string, photo?: string, planDeviations?: any[], focus?: number, pump?: number, environmentTags?: string[], sessionDifficulty?: number, planAdherenceTags?: string[]) => void;
  mode?: 'live' | 'log';
  improvementIndex?: { percent: number; direction: 'up' | 'down' | 'neutral' } | null;
  initialDurationInSeconds?: number;
}

const ENVIRONMENT_TAGS = ["Gimnasio Lleno", "Gimnasio Vacío", "Entrenando con Amigos", "Buena Música", "Distraído", "Con Prisa"];
const PLAN_ADHERENCE_TAGS = ["Seguí el Plan", "Más Pesado", "Más Ligero", "Más Reps", "Menos Reps", "Cambié Ejercicios", "Añadí Ejercicios"];

// --- HIDDEN WORKOUT SHARE CARD (AURA ÉPICA) ---
const WorkoutShareCard: React.FC<{
    date: string;
    duration: string;
    difficulty: number;
    pump: number;
}> = ({ date, duration, difficulty, pump }) => {
    return (
        <div id="workout-summary-share-card" className="fixed top-0 left-[-2000px] w-[540px] h-[960px] bg-black text-white overflow-hidden pointer-events-none z-[-10] flex flex-col font-sans">
             {/* Fondo Épico */}
            <div className="absolute inset-0 z-0 opacity-40 mix-blend-luminosity scale-110 -translate-y-10">
                <CaupolicanBackground />
            </div>
            
            {/* Degradados para legibilidad */}
            <div className="absolute inset-0 bg-gradient-to-b from-black/80 via-black/40 to-[#0a0a0a] z-0" />
            
            {/* Ruido Texturizado (Estilo Gritty) */}
            <div className="absolute inset-0 opacity-[0.15] mix-blend-overlay z-0" style={{ backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")` }}></div>

            <div className="relative z-10 flex flex-col h-full p-12 justify-between">
                {/* Header (Logo + Fecha) */}
                <div className="flex justify-between items-start">
                    <div className="flex flex-col">
                        <span className="text-[14px] font-black text-white/40 uppercase tracking-[0.4em] mb-1">Batalla Terminada</span>
                        <span className="text-xl font-bold text-white uppercase tracking-widest">
                            {new Date(date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })}
                        </span>
                    </div>
                    <div className="w-16 h-16 bg-white text-black flex items-center justify-center rounded-2xl shadow-[0_0_40px_rgba(255,255,255,0.3)]">
                        <span className="font-black text-2xl tracking-tighter">KP</span>
                    </div>
                </div>

                {/* Main Impact Title */}
                <div className="flex flex-col mt-auto mb-16">
                     <h1 className="text-[5.5rem] font-black text-white leading-[0.85] uppercase tracking-tighter drop-shadow-2xl">
                         DOMINA <br/>
                         <span className="text-transparent bg-clip-text bg-gradient-to-r from-red-500 to-orange-500">LA DEBILIDAD</span>
                     </h1>
                </div>

                {/* Stats Grid */}
                <div className="grid grid-cols-3 gap-4 mb-8">
                     <div className="bg-zinc-900/80 backdrop-blur-xl border border-white/10 rounded-3xl p-6 flex flex-col items-center justify-center shadow-2xl">
                         <ClockIcon size={32} className="text-white/50 mb-3" />
                         <p className="text-[3rem] font-black text-white leading-none">{duration}</p>
                         <p className="text-[11px] text-zinc-400 font-bold uppercase tracking-widest mt-2">Minutos</p>
                     </div>
                     
                     <div className="bg-zinc-900/80 backdrop-blur-xl border border-white/10 rounded-3xl p-6 flex flex-col items-center justify-center shadow-2xl">
                         <ZapIcon size={32} className="text-yellow-500/80 mb-3" />
                         <p className="text-[3rem] font-black text-yellow-400 leading-none">{difficulty}</p>
                         <p className="text-[11px] text-zinc-400 font-bold uppercase tracking-widest mt-2">Dificultad</p>
                     </div>

                     <div className="bg-zinc-900/80 backdrop-blur-xl border border-white/10 rounded-3xl p-6 flex flex-col items-center justify-center shadow-2xl">
                         <ActivityIcon size={32} className="text-red-500/80 mb-3" />
                         <p className="text-[3rem] font-black text-red-400 leading-none">{pump}</p>
                         <p className="text-[11px] text-zinc-400 font-bold uppercase tracking-widest mt-2">Pump</p>
                     </div>
                </div>

                {/* Footer Quote */}
                <div className="text-center pt-8 border-t border-white/10">
                     <p className="text-2xl font-bold text-white italic tracking-wide">"El dolor de hoy es la fuerza de mañana."</p>
                     <p className="text-sm font-black text-white/30 mt-4 uppercase tracking-[0.5em]">@YOURPRIME.APP</p>
                </div>
            </div>
        </div>
    );
};

const FinishWorkoutModal: React.FC<FinishWorkoutModalProps> = ({ isOpen, onClose, onFinish, mode = 'live', improvementIndex, initialDurationInSeconds }) => {
  const { addRecommendationTrigger, addToast } = useAppDispatch();
  const [fatigueLevel, setFatigueLevel] = useState(5);
  const [mentalClarity, setMentalClarity] = useState(5);
  const [focus, setFocus] = useState(5);
  const [pump, setPump] = useState(5);
  const [sessionDifficulty, setSessionDifficulty] = useState(5);
  const [selectedDiscomforts, setSelectedDiscomforts] = useState<string[]>([]);
  const [environmentTags, setEnvironmentTags] = useState<string[]>([]);
  const [planAdherenceTags, setPlanAdherenceTags] = useState<string[]>([]);
  const [notes, setNotes] = useState('');
  const [durationInMinutes, setDurationInMinutes] = useState('');
  const [logDate, setLogDate] = useState(new Date().toISOString().split('T')[0]);
  const [showRecoverySuggestion, setShowRecoverySuggestion] = useState(false);
  const prevIsOpen = useRef(isOpen);
  const [isSharing, setIsSharing] = useState(false);

  useEffect(() => {
    if (isOpen && !prevIsOpen.current) {
      if (initialDurationInSeconds) {
        setDurationInMinutes(Math.round(initialDurationInSeconds / 60).toString());
      }
      setLogDate(new Date().toISOString().split('T')[0]);
    }
    
    if (!isOpen && prevIsOpen.current) {
      setFatigueLevel(5);
      setMentalClarity(5);
      setFocus(5);
      setPump(5);
      setSessionDifficulty(5);
      setSelectedDiscomforts([]);
      setEnvironmentTags([]);
      setPlanAdherenceTags([]);
      setNotes('');
      setDurationInMinutes('');
      setShowRecoverySuggestion(false);
    }
    
    prevIsOpen.current = isOpen;
  }, [isOpen, initialDurationInSeconds]);

  const handleFinishAttempt = () => {
      // Consecuencia: Sugerir descarga si fatiga > 7 y claridad < 4
      if (fatigueLevel > 7 && mentalClarity < 4) {
          setShowRecoverySuggestion(true);
          addRecommendationTrigger({
              type: 'high_fatigue',
              value: fatigueLevel,
              context: `Fatiga extrema (${fatigueLevel}/10) y claridad baja (${mentalClarity}/10) tras sesión.`
          });
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

    onFinish(notes, selectedDiscomforts, fatigueLevel, mentalClarity, durationNum, logDate, undefined, [], focus, pump, environmentTags, sessionDifficulty, planAdherenceTags);
  };
  
  const handleAcceptDeload = () => {
      addToast("Se ha programado una sesión de descanso activo para tu próximo entrenamiento.", "suggestion");
      executeFinish();
  }
  
  const handleShare = async () => {
      setIsSharing(true);
      await shareElementAsImage('workout-summary-share-card', '¡Entrenamiento Terminado!', 'He completado una sesión en YourPrime. #YourPrime #Fitness');
      setIsSharing(false);
  }

  const toggleTag = (tag: string, state: string[], setState: React.Dispatch<React.SetStateAction<string[]>>) => {
    setState(prev => prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag]);
  };

  const SliderInput: React.FC<{label: string; value: number; onChange: (v:number) => void; color?: string}> = ({label, value, onChange, color = "accent-primary-color"}) => (
     <div>
        <label className="block text-sm font-medium text-slate-300 mb-2">{label} (1-10)</label>
        <div className="flex items-center gap-3">
            <input type="range" min="1" max="10" value={value} onChange={(e) => onChange(parseInt(e.target.value))} className={`w-full h-1.5 rounded-lg appearance-none cursor-pointer bg-slate-800 ${color}`} />
            <span className="font-bold text-lg w-8 text-center">{value}</span>
        </div>
    </div>
  )

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
  )

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={showRecoverySuggestion ? "🛡️ Recuperación Prioritaria" : "Finalizar Sesión"}>
      {/* Hidden Share Card */}
      <WorkoutShareCard 
          date={logDate} 
          duration={durationInMinutes || '--'} 
          difficulty={sessionDifficulty} 
          pump={pump} 
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
            
            <div className="space-y-6 bg-zinc-950/50 p-5 rounded-3xl border border-white/5 shadow-inner">
                <div>
                    <label className="block text-[10px] font-black text-sky-400 uppercase tracking-widest mb-3 flex items-center gap-2">
                        <ZapIcon size={14}/> RPE Global de la Sesión
                    </label>
                    <div className="flex items-center gap-4">
                        <input type="range" min="1" max="10" value={sessionDifficulty} onChange={(e) => setSessionDifficulty(parseInt(e.target.value))} className="w-full h-2 rounded-lg appearance-none cursor-pointer bg-black border border-white/10 accent-sky-500" />
                        <span className="text-xl font-black text-white w-8 text-right tabular-nums">{sessionDifficulty}</span>
                    </div>
                </div>
                
                <div>
                    <label className="block text-[10px] font-black text-rose-400 uppercase tracking-widest mb-3 flex items-center gap-2">
                        <ActivityIcon size={14}/> Drenaje del SNC (Estrés Central)
                    </label>
                    <div className="flex items-center gap-4">
                        <input type="range" min="1" max="10" value={fatigueLevel} onChange={(e) => setFatigueLevel(parseInt(e.target.value))} className="w-full h-2 rounded-lg appearance-none cursor-pointer bg-black border border-white/10 accent-rose-500" />
                        <span className="text-xl font-black text-white w-8 text-right tabular-nums">{fatigueLevel}</span>
                    </div>
                </div>

                <div>
                    <label className="block text-[10px] font-black text-amber-400 uppercase tracking-widest mb-3 flex items-center gap-2">
                        <FlameIcon size={14}/> Daño Muscular (Pump)
                    </label>
                    <div className="flex items-center gap-4">
                        <input type="range" min="1" max="10" value={pump} onChange={(e) => setPump(parseInt(e.target.value))} className="w-full h-2 rounded-lg appearance-none cursor-pointer bg-black border border-white/10 accent-amber-500" />
                        <span className="text-xl font-black text-white w-8 text-right tabular-nums">{pump}</span>
                    </div>
                </div>

                <div>
                    <label className="block text-[10px] font-black text-indigo-400 uppercase tracking-widest mb-3 flex items-center gap-2">
                        <BrainIcon size={14}/> Claridad Mental
                    </label>
                    <div className="flex items-center gap-4">
                        <input type="range" min="1" max="10" value={mentalClarity} onChange={(e) => setMentalClarity(parseInt(e.target.value))} className="w-full h-2 rounded-lg appearance-none cursor-pointer bg-black border border-white/10 accent-indigo-500" />
                        <span className="text-xl font-black text-white w-8 text-right tabular-nums">{mentalClarity}</span>
                    </div>
                </div>
            </div>
            
            <div className="space-y-4 pt-2">
                <TagGroup title="Entorno" tags={ENVIRONMENT_TAGS} selected={environmentTags} onToggle={(tag) => toggleTag(tag, environmentTags, setEnvironmentTags)} />
                <TagGroup title="Adherencia" tags={PLAN_ADHERENCE_TAGS} selected={planAdherenceTags} onToggle={(tag) => toggleTag(tag, planAdherenceTags, setPlanAdherenceTags)} />
                <TagGroup title="Alertas de Caupolicán Body (Articulaciones)" tags={DISCOMFORT_LIST} selected={selectedDiscomforts} onToggle={(tag) => toggleTag(tag, selectedDiscomforts, setSelectedDiscomforts)} />
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
    </Modal>
  );
};

export default FinishWorkoutModal;
