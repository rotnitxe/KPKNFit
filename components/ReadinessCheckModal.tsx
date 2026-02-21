import React, { useState, useEffect } from 'react';
import { MoonIcon, ActivityIcon, FlameIcon, ZapIcon, BatteryIcon } from './icons';

interface ReadinessData {
  sleepQuality: number;
  stressLevel: number;
  doms: number;
  motivation: number;
}

interface ReadinessCheckModalProps {
  isOpen: boolean;
  onClose: () => void;
  onContinue: (data: ReadinessData) => void;
}

const CyberSlider: React.FC<{ label: string; value: number; onChange: (v: number) => void; icon: React.ReactNode; color: string; accentClass: string; inverseScale?: boolean }> = ({ label, value, onChange, icon, color, accentClass, inverseScale }) => (
    <div className="bg-zinc-900/60 p-5 rounded-2xl border border-white/5 mb-3 shadow-inner">
        <div className="flex justify-between items-center mb-3">
            <label className="text-[11px] font-black uppercase tracking-widest text-slate-300 flex items-center gap-2">
                <span className={color}>{icon}</span> {label}
            </label>
            <span className={`text-lg font-black ${color}`}>{value}/5</span>
        </div>
        <input 
            type="range" min="1" max="5" 
            value={value} 
            onChange={(e) => onChange(parseInt(e.target.value))} 
            className={`w-full h-2 rounded-lg appearance-none cursor-pointer bg-black/50 border border-white/10 ${accentClass}`}
        />
        <div className="flex justify-between text-[9px] text-slate-500 uppercase tracking-widest mt-2 font-bold">
            <span>{inverseScale ? 'Óptimo' : 'Pésimo'}</span>
            <span>{inverseScale ? 'Pésimo' : 'Óptimo'}</span>
        </div>
    </div>
);

const ReadinessCheckModal: React.FC<ReadinessCheckModalProps> = ({ isOpen, onClose, onContinue }) => {
    const [sleepQuality, setSleepQuality] = useState(3);
    const [stressLevel, setStressLevel] = useState(3); 
    const [doms, setDoms] = useState(3); 
    const [motivation, setMotivation] = useState(3);
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        if (isOpen) setIsSubmitting(false); 
    }, [isOpen]);

    if (!isOpen) return null;

    const handleStart = () => {
        if (isSubmitting) return;
        setIsSubmitting(true);
        // Pequeño delay estético, luego enviamos la data exacta que espera AppContext
        setTimeout(() => {
            onContinue({ sleepQuality, stressLevel, doms, motivation });
        }, 150);
    };

    return (
        <div className="fixed inset-0 z-[9999] flex items-end sm:items-center justify-center p-0 sm:p-4 animate-fade-in pointer-events-auto">
            <div className="absolute inset-0 bg-black/90 backdrop-blur-xl" onClick={onClose} />
            <div className="relative z-10 w-full max-w-md bg-[#0a0a0a] sm:rounded-[2.5rem] rounded-t-[2.5rem] border border-white/10 flex flex-col max-h-[90vh] shadow-[0_0_50px_rgba(0,0,0,0.8)] animate-slide-up overflow-hidden">
                
                <div className="p-6 pb-4 border-b border-white/5 shrink-0 relative bg-[#0a0a0a]">
                    <div className="absolute top-0 inset-x-0 h-24 bg-gradient-to-b from-sky-500/10 to-transparent pointer-events-none z-0" />
                    <div className="relative z-10 text-center">
                        <div className="w-12 h-12 bg-sky-500/10 rounded-full flex items-center justify-center text-sky-400 mx-auto mb-3 border border-sky-500/20 shadow-[0_0_15px_rgba(56,189,248,0.2)]">
                            <BatteryIcon size={24} />
                        </div>
                        <h2 className="text-xl font-black text-white uppercase tracking-tight">Estado Base</h2>
                        <p className="text-[9px] text-sky-400 font-bold uppercase tracking-widest mt-1">Sincronización de Baterías AUGE</p>
                    </div>
                </div>

                {/* El overflow-y-auto aquí soluciona el bloqueo de pantalla */}
                <div className="p-5 overflow-y-auto hide-scrollbar flex-1 relative">
                    <p className="text-[11px] text-slate-400 text-center mb-5 px-2 leading-relaxed">Calibra tus biomarcadores antes de la batalla. Estos datos ajustarán la matemática de fatiga en vivo.</p>
                    <CyberSlider label="Calidad del Sueño" value={sleepQuality} onChange={setSleepQuality} icon={<MoonIcon size={16}/>} color="text-indigo-400" accentClass="accent-indigo-500" />
                    <CyberSlider label="Estrés del SNC" value={stressLevel} onChange={setStressLevel} icon={<ActivityIcon size={16}/>} color="text-rose-400" accentClass="accent-rose-500" inverseScale={true} />
                    <CyberSlider label="Daño Muscular Prev" value={doms} onChange={setDoms} icon={<FlameIcon size={16}/>} color="text-amber-400" accentClass="accent-amber-500" inverseScale={true} />
                    <CyberSlider label="Foco / Motivación" value={motivation} onChange={setMotivation} icon={<ZapIcon size={16}/>} color="text-sky-400" accentClass="accent-sky-500" />
                </div>

                <div className="p-5 border-t border-white/5 shrink-0 bg-[#0a0a0a]">
                    <button onClick={handleStart} disabled={isSubmitting} className="w-full py-4 rounded-2xl text-xs font-black uppercase tracking-widest transition-all bg-white text-black hover:bg-slate-200 active:scale-95">
                        {isSubmitting ? 'Sincronizando...' : 'Comenzar Batalla'}
                    </button>
                    <button onClick={onClose} disabled={isSubmitting} className="w-full py-3 mt-2 rounded-2xl text-slate-500 text-[10px] font-black uppercase tracking-widest hover:text-white transition-colors">
                        Cancelar
                    </button>
                </div>
            </div>
        </div>
    );
};

export default ReadinessCheckModal;