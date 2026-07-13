import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { XIcon, CheckIcon, InfoIcon, ActivityIcon, BrainIcon, MoonIcon, ZapIcon, AlertTriangleIcon } from '../icons';

interface ReadinessSheetProps {
    isOpen: boolean;
    onClose: () => void;
    onStartWorkout: (data: any) => void;
}

const SYMPTOM_CHIPS = [
    { id: 'fatigue', label: 'Fatiga', icon: ZapIcon },
    { id: 'stress', label: 'Estress', icon: BrainIcon },
    { id: 'pain', label: 'Dolor', icon: ActivityIcon },
    { id: 'injury', label: 'Lesión', icon: AlertTriangleIcon },
    { id: 'period', label: 'Periodo', icon: ActivityIcon },
    { id: 'nutrition', label: 'Nutrición', icon: ActivityIcon },
    { id: 'sleep', label: 'Sueño', icon: MoonIcon },
];

const SLIDERS = [
    { id: 'fatigue', label: 'Fatiga General', min: 1, max: 10 },
    { id: 'soreness', label: 'Soreness (Agujetas)', min: 1, max: 10 },
    { id: 'stress', label: 'Estrés Psicológico', min: 1, max: 10 },
    { id: 'sleep', label: 'Calidad del Sueño', min: 1, max: 10 },
    { id: 'motivation', label: 'Motivación', min: 1, max: 10 },
];

const ReadinessSheet: React.FC<ReadinessSheetProps> = ({ isOpen, onClose, onStartWorkout }) => {
    const [selectedSymptoms, setSelectedSymptoms] = useState<string[]>([]);
    const [sliderValues, setSliderValues] = useState<Record<string, number>>({
        fatigue: 5,
        soreness: 2,
        stress: 4,
        sleep: 7,
        motivation: 8,
    });
    const [mood, setMood] = useState<number>(3); // 1-5

    const toggleSymptom = (id: string) => {
        setSelectedSymptoms(prev =>
            prev.includes(id) ? prev.filter(s => s !== id) : [...prev, id]
        );
    };

    const handleSliderChange = (id: string, value: number) => {
        setSliderValues(prev => ({ ...prev, [id]: value }));
    };

    if (!isOpen) return null;

    return (
        <AnimatePresence>
            <div className="fixed inset-0 z-[100] flex items-end justify-center">
                <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    onClick={onClose}
                    className="absolute inset-0 bg-black/60 backdrop-blur-sm"
                />

                <motion.div
                    initial={{ translateY: '100%' }}
                    animate={{ translateY: 0 }}
                    exit={{ translateY: '100%' }}
                    transition={{ type: 'spring', damping: 25, stiffness: 200 }}
                    className="relative w-full max-w-lg liquid-glass rounded-t-[32px] overflow-hidden"
                >
                    <div className="flex flex-col p-6 pb-10">
                        {/* Drag Handle */}
                        <div className="self-center w-12 h-1.5 bg-white/20 rounded-full mb-6" />

                        <div className="flex justify-between items-start mb-6">
                            <div>
                                <h2 className="text-m3-title text-white">Readiness Check</h2>
                                <p className="text-m3-on-surface-variant text-xs mt-1">Evalúa tu estado antes de comenzar</p>
                            </div>
                            <button onClick={onClose} className="p-2 rounded-full bg-white/5 text-white/60">
                                <XIcon size={20} />
                            </button>
                        </div>

                        {/* General Mood */}
                        <div className="mb-8">
                            <label className="text-m3-label text-m3-primary/80 mb-3 block">¿Cómo te sientes hoy?</label>
                            <div className="flex justify-between items-center px-2">
                                {[1, 2, 3, 4, 5].map(val => (
                                    <button
                                        key={val}
                                        onClick={() => setMood(val)}
                                        className={`w-12 h-12 rounded-2xl flex items-center justify-center text-2xl transition-all ${mood === val ? 'bg-m3-primary/20 scale-110 shadow-[0_0_15px_rgba(208,188,255,0.3)]' : 'bg-white/5'}`}
                                    >
                                        {val === 1 ? '😫' : val === 2 ? '😕' : val === 3 ? '😐' : val === 4 ? '🙂' : '🔥'}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Symptom Chips */}
                        <div className="mb-8">
                            <label className="text-m3-label text-m3-primary/80 mb-3 block">Sintomas detectados</label>
                            <div className="flex flex-wrap gap-2">
                                {SYMPTOM_CHIPS.map(chip => (
                                    <button
                                        key={chip.id}
                                        onClick={() => toggleSymptom(chip.id)}
                                        className={`m3-chip ${selectedSymptoms.includes(chip.id) ? 'm3-chip-filled' : 'bg-white/5 text-white/50 border-white/10'}`}
                                    >
                                        <chip.icon size={14} className="mr-2" />
                                        {chip.label}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Sliders */}
                        <div className="space-y-6 mb-10">
                            {SLIDERS.map(slider => (
                                <div key={slider.id}>
                                    <div className="flex justify-between items-center mb-2">
                                        <label className="text-xs font-medium text-white/80">{slider.label}</label>
                                        <span className="text-[10px] font-black text-m3-primary bg-m3-primary/10 px-2 rounded-md">{sliderValues[slider.id]}</span>
                                    </div>
                                    <input
                                        type="range"
                                        min={slider.min}
                                        max={slider.max}
                                        value={sliderValues[slider.id]}
                                        onChange={(e) => handleSliderChange(slider.id, parseInt(e.target.value))}
                                        className="w-full h-1.5 bg-white/10 rounded-full appearance-none cursor-pointer accent-m3-primary"
                                    />
                                </div>
                            ))}
                        </div>

                        <button
                            onClick={() => onStartWorkout({ mood, symptoms: selectedSymptoms, values: sliderValues })}
                            className="w-full py-4 rounded-full bg-m3-primary text-m3-on-primary font-black uppercase tracking-widest shadow-lg shadow-m3-primary/20 active:scale-95 transition-transform"
                        >
                            Comenzar Entrenamiento
                        </button>
                    </div>
                </motion.div>
            </div>
        </AnimatePresence>
    );
};

export default ReadinessSheet;
