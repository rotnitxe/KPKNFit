
// components/FinishWorkoutModal.tsx
import React, { useState, useEffect, useRef } from 'react';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { DISCOMFORT_LIST } from '../data/discomfortList';
import { CheckCircleIcon, ArrowUpIcon, ArrowDownIcon, ZapIcon, BrainIcon, ActivityIcon, LinkIcon, DumbbellIcon, ClockIcon } from './icons';
import { useAppDispatch } from '../contexts/AppContext';
import { shareElementAsImage } from '../services/shareService';

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

// --- HIDDEN WORKOUT SHARE CARD (Spotify Wrapped Style) ---
const WorkoutShareCard: React.FC<{
    date: string;
    duration: string;
    difficulty: number;
    pump: number;
}> = ({ date, duration, difficulty, pump }) => {
    return (
        <div id="workout-summary-share-card" className="fixed top-0 left-[-2000px] w-[500px] h-[888px] bg-black text-white overflow-hidden pointer-events-none z-[-10]">
             {/* Background */}
            <div className="absolute inset-0 bg-gradient-to-b from-[#0f172a] via-[#1e1b4b] to-black z-0" />
            <div className="absolute inset-0 opacity-20" style={{ backgroundImage: 'linear-gradient(45deg, rgba(255,255,255,0.05) 25%, transparent 25%, transparent 50%, rgba(255,255,255,0.05) 50%, rgba(255,255,255,0.05) 75%, transparent 75%, transparent)', backgroundSize: '40px 40px' }}></div>
            
            <div className="relative z-10 flex flex-col h-full p-10 justify-between">
                <div className="text-center pt-10">
                     <p className="text-xl font-black text-white/50 uppercase tracking-[0.5em] mb-2">Entrenamiento</p>
                     <h1 className="text-6xl font-black text-white leading-tight uppercase">Completado</h1>
                     <div className="w-24 h-1 bg-gradient-to-r from-cyan-400 to-blue-600 mx-auto mt-6"></div>
                </div>

                <div className="space-y-8">
                     <div className="bg-white/5 border border-white/10 rounded-3xl p-6 backdrop-blur-xl">
                        <div className="flex items-center gap-4 mb-2">
                             <ClockIcon size={32} className="text-cyan-400" />
                             <div>
                                 <p className="text-xs text-slate-400 font-bold uppercase tracking-widest">Duración</p>
                                 <p className="text-4xl font-black text-white">{duration}<span className="text-xl ml-1">min</span></p>
                             </div>
                        </div>
                     </div>
                     
                     <div className="grid grid-cols-2 gap-4">
                        <div className="bg-white/5 border border-white/10 rounded-3xl p-6 backdrop-blur-xl text-center">
                             <p className="text-4xl font-black text-yellow-400">{difficulty}/10</p>
                             <p className="text-xs text-slate-400 font-bold uppercase tracking-widest mt-1">Dificultad</p>
                        </div>
                        <div className="bg-white/5 border border-white/10 rounded-3xl p-6 backdrop-blur-xl text-center">
                             <p className="text-4xl font-black text-purple-400">{pump}/10</p>
                             <p className="text-xs text-slate-400 font-bold uppercase tracking-widest mt-1">Pump</p>
                        </div>
                     </div>
                     
                     <div className="bg-gradient-to-r from-blue-600 to-cyan-500 rounded-3xl p-6 text-center shadow-lg shadow-cyan-500/20">
                          <p className="text-lg font-bold text-white italic">"El dolor de hoy es la fuerza de mañana."</p>
                     </div>
                </div>

                 <div className="text-center pb-4">
                     <p className="text-sm font-bold text-slate-500 uppercase">{new Date(date).toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</p>
                     <p className="text-xs font-black text-white/20 mt-2 uppercase tracking-widest">#YOURPRIME</p>
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
            
            <div className="space-y-4">
                <SliderInput label="Dificultad de la Sesión" value={sessionDifficulty} onChange={setSessionDifficulty} />
                <SliderInput label="Claridad Mental" value={mentalClarity} onChange={setMentalClarity} />
                <SliderInput label="Bombeo Muscular (Pump)" value={pump} onChange={setPump} />
                <SliderInput label="Fatiga General" value={fatigueLevel} onChange={setFatigueLevel} />
            </div>
            
            <TagGroup title="Entorno" tags={ENVIRONMENT_TAGS} selected={environmentTags} onToggle={(tag) => toggleTag(tag, environmentTags, setEnvironmentTags)} />
            <TagGroup title="Adherencia" tags={PLAN_ADHERENCE_TAGS} selected={planAdherenceTags} onToggle={(tag) => toggleTag(tag, planAdherenceTags, setPlanAdherenceTags)} />
            <TagGroup title="Molestias Articulares" tags={DISCOMFORT_LIST} selected={selectedDiscomforts} onToggle={(tag) => toggleTag(tag, selectedDiscomforts, setSelectedDiscomforts)} />
            
            <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">Notas del Diario</label>
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} className="w-full" placeholder="¿Algo que destacar hoy?"/>
            </div>
            <div className="flex gap-2 pt-4 border-t border-border-color">
                <Button onClick={handleShare} variant="secondary" className="flex-1 !py-4" disabled={isSharing}>
                    <LinkIcon size={20}/> {isSharing ? '...' : 'Compartir'}
                </Button>
                <Button onClick={handleFinishAttempt} variant="primary" className="flex-[2] !py-4 !text-base shadow-xl">
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
