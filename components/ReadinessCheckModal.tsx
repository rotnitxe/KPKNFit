
// components/ReadinessCheckModal.tsx
import React, { useState } from 'react';
import Button from './ui/Button';
import { BriefcaseIcon, BookOpenIcon } from './icons';

interface ReadinessData {
  sleepQuality: number;
  stressLevel: number;
  doms: number;
  motivation: number;
  workHours?: number;
  studyHours?: number;
  moodState?: 'happy' | 'neutral' | 'sad' | 'anxious' | 'energetic';
}

interface ReadinessCheckModalProps {
  isOpen: boolean;
  onClose: () => void;
  onContinue: (data: ReadinessData) => void;
}

const Slider: React.FC<{ label: string; value: number; onChange: (value: number) => void; minLabel: string; maxLabel: string; icon?: React.ReactNode }> = ({ label, value, onChange, minLabel, maxLabel, icon }) => (
    <div>
        <label className="block text-sm font-medium text-slate-300 mb-2 flex items-center gap-2">{icon} {label}</label>
        <div className="flex items-center gap-3">
            <input type="range" min="1" max="5" value={value} onChange={(e) => onChange(parseInt(e.target.value))} className="w-full" />
            <span className="font-bold text-lg w-8 text-center">{value}</span>
        </div>
        <div className="flex justify-between text-xs text-slate-500 mt-1">
            <span>{minLabel}</span>
            <span>{maxLabel}</span>
        </div>
    </div>
);

const HoursSlider: React.FC<{ label: string; value: number; onChange: (value: number) => void; icon?: React.ReactNode }> = ({ label, value, onChange, icon }) => (
    <div>
        <label className="block text-sm font-medium text-slate-300 mb-2 flex items-center gap-2">{icon} {label}</label>
        <div className="flex items-center gap-3">
             <input type="range" min="0" max="14" step="0.5" value={value} onChange={e => onChange(parseFloat(e.target.value))} className="w-full h-2 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-sky-500"/>
             <span className="font-bold text-lg w-12 text-right">{value}h</span>
        </div>
    </div>
);

const ReadinessCheckModal: React.FC<ReadinessCheckModalProps> = ({ isOpen, onClose, onContinue }) => {
    const [sleepQuality, setSleepQuality] = useState(3);
    const [stressLevel, setStressLevel] = useState(3);
    const [doms, setDoms] = useState(3);
    const [motivation, setMotivation] = useState(3);
    
    // New Context Fields
    const [workHours, setWorkHours] = useState(0);
    const [studyHours, setStudyHours] = useState(0);
    const [mood, setMood] = useState<'happy' | 'neutral' | 'sad' | 'anxious' | 'energetic'>('neutral');

    const handleContinue = () => {
        onContinue({ sleepQuality, stressLevel, doms, motivation, workHours, studyHours, moodState: mood });
    };

    const moods: { id: any, label: string, emoji: string }[] = [
        { id: 'sad', label: 'Bajo', emoji: '😔' },
        { id: 'anxious', label: 'Ansioso', emoji: '😰' },
        { id: 'neutral', label: 'Normal', emoji: '😐' },
        { id: 'happy', label: 'Bien', emoji: '🙂' },
        { id: 'energetic', label: 'A tope', emoji: '🔥' },
    ];

    if (!isOpen) return null;

    const isPreWorkout = onContinue.name.includes('handleContinueFromReadiness');
    const buttonText = isPreWorkout ? "Continuar al Entrenamiento" : "Guardar Registro";

    return (
        <>
            <div className="bottom-sheet-backdrop" onClick={onClose} />
            <div className="bottom-sheet-content open !h-auto max-h-[90vh]">
                <div className="bottom-sheet-grabber" />
                <header className="flex-shrink-0 p-4 text-center">
                    <h2 className="text-xl font-bold text-white">¿Cómo te sientes hoy?</h2>
                    <p className="text-sm text-slate-400">Contextualiza tu entrenamiento.</p>
                </header>
                <div className="overflow-y-auto px-4 pb-4 space-y-6 custom-scrollbar">
                    <div className="space-y-6 border-b border-slate-700/50 pb-6">
                        <Slider label="Calidad del Sueño" value={sleepQuality} onChange={setSleepQuality} minLabel="Mala" maxLabel="Excelente" />
                        <Slider label="Nivel de Estrés" value={stressLevel} onChange={setStressLevel} minLabel="Bajo" maxLabel="Alto" />
                        <Slider label="Dolor Muscular (DOMS)" value={doms} onChange={setDoms} minLabel="Nulo" maxLabel="Extremo" />
                        <Slider label="Motivación" value={motivation} onChange={setMotivation} minLabel="Baja" maxLabel="Alta" />
                    </div>

                    <div className="space-y-4">
                        <h3 className="text-xs font-black text-slate-500 uppercase tracking-widest">Carga Externa</h3>
                        <HoursSlider label="Horas de Trabajo" value={workHours} onChange={setWorkHours} icon={<BriefcaseIcon size={16} className="text-blue-400"/>} />
                        <HoursSlider label="Horas de Estudio" value={studyHours} onChange={setStudyHours} icon={<BookOpenIcon size={16} className="text-purple-400"/>} />
                    </div>
                    
                    <div>
                         <h3 className="text-xs font-black text-slate-500 uppercase tracking-widest mb-3">Estado de Ánimo</h3>
                         <div className="flex justify-between bg-slate-800 p-2 rounded-2xl">
                            {moods.map(m => (
                                <button 
                                    key={m.id} 
                                    onClick={() => setMood(m.id)}
                                    className={`flex flex-col items-center gap-1 p-2 rounded-xl transition-all ${mood === m.id ? 'bg-white/10 scale-110' : 'opacity-50 hover:opacity-100'}`}
                                >
                                    <span className="text-2xl">{m.emoji}</span>
                                    <span className="text-[9px] font-bold uppercase">{m.label}</span>
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
                <footer className="p-4 bg-slate-900 border-t border-slate-700/50 flex-shrink-0">
                    <Button onClick={handleContinue} className="w-full !py-3">{buttonText}</Button>
                </footer>
            </div>
        </>
    );
};

export default ReadinessCheckModal;
