// components/onboarding/BatteryPrecalibrationStep.tsx
// Pre-calibración de batería: ¿ya entrenó? + 3 ejercicios + intensidad + readiness

import React, { useState } from 'react';
import { MoonIcon, ActivityIcon, FlameIcon, ZapIcon, DumbbellIcon, ChevronRightIcon, SearchIcon } from '../icons';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import {
    applyPrecalibrationToBattery,
    applyPrecalibrationReadinessOnly,
    type PrecalibrationExerciseInput,
    type PrecalibrationReadinessInput,
} from '../../services/auge';

const INTENSITY_OPTIONS: { id: PrecalibrationExerciseInput['intensity']; label: string }[] = [
    { id: 'LIGERO', label: 'Ligero' },
    { id: 'MEDIO', label: 'Medio' },
    { id: 'ALTO', label: 'Alto' },
    { id: 'MUY_ALTO', label: 'Muy alto' },
    { id: 'EXTREMO', label: 'Extremo' },
];

const CyberSlider: React.FC<{
    label: string;
    value: number;
    onChange: (v: number) => void;
    icon: React.ReactNode;
    color: string;
    accentClass: string;
    inverseScale?: boolean;
}> = ({ label, value, onChange, icon, color, accentClass, inverseScale }) => (
    <div className="bg-zinc-900/60 p-4 rounded-xl border border-white/5 mb-3">
        <div className="flex justify-between items-center mb-2">
            <label className="text-[10px] font-black uppercase tracking-widest text-slate-300 flex items-center gap-2">
                <span className={color}>{icon}</span> {label}
            </label>
            <span className={`text-base font-black font-mono ${color}`}>{value}/5</span>
        </div>
        <input
            type="range"
            min="1"
            max="5"
            value={value}
            onChange={e => onChange(parseInt(e.target.value))}
            className={`w-full h-2 rounded-lg appearance-none cursor-pointer bg-black/50 border border-white/10 ${accentClass}`}
        />
        <div className="flex justify-between text-[8px] text-slate-500 uppercase tracking-widest mt-1 font-bold">
            <span>{inverseScale ? 'Óptimo' : 'Pésimo'}</span>
            <span>{inverseScale ? 'Pésimo' : 'Óptimo'}</span>
        </div>
    </div>
);

interface BatteryPrecalibrationStepProps {
    onComplete: () => void;
    onSkip: () => void;
}

export const BatteryPrecalibrationStep: React.FC<BatteryPrecalibrationStepProps> = ({ onComplete, onSkip }) => {
    const { exerciseList, settings } = useAppState();
    const { setSettings, addToast } = useAppDispatch();

    const [step, setStep] = useState(0);
    const [trainedBefore, setTrainedBefore] = useState<boolean | null>(null);
    const [yearsExperience, setYearsExperience] = useState<string>('');
    const [injuryHistory, setInjuryHistory] = useState<'none' | 'recovered' | 'current'>('none');
    const [trainingDaysPerWeek, setTrainingDaysPerWeek] = useState(3);
    const [exercises, setExercises] = useState<PrecalibrationExerciseInput[]>([
        { exerciseName: '', intensity: 'ALTO' },
        { exerciseName: '', intensity: 'ALTO' },
        { exerciseName: '', intensity: 'ALTO' },
    ]);
    const [searchOpen, setSearchOpen] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [sleepQuality, setSleepQuality] = useState(3);
    const [stressLevel, setStressLevel] = useState(3);
    const [doms, setDoms] = useState(3);
    const [motivation, setMotivation] = useState(3);

    const filteredExercises = searchQuery.trim()
        ? exerciseList.filter(
              ex =>
                  ex.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                  (ex.id && String(ex.id).toLowerCase().includes(searchQuery.toLowerCase()))
          ).slice(0, 12)
        : [];

    const handleSelectExercise = (idx: number, name: string, dbId?: string) => {
        setExercises(prev => {
            const next = [...prev];
            next[idx] = { ...next[idx], exerciseName: name, exerciseDbId: dbId };
            return next;
        });
        setSearchOpen(null);
        setSearchQuery('');
    };

    const handleFinish = () => {
        const readiness: PrecalibrationReadinessInput = { sleepQuality, stressLevel, doms, motivation };

        if (trainedBefore === false) {
            const deltas = applyPrecalibrationReadinessOnly(readiness);
            setSettings({
                batteryCalibration: {
                    ...deltas,
                    lastCalibrated: new Date().toISOString(),
                    precalibrationContext: { yearsExperience, injuryHistory, trainingDaysPerWeek },
                },
                hasPrecalibratedBattery: true,
                precalibrationDismissed: false,
            });
        } else {
            const validExercises = exercises.filter(e => e.exerciseName.trim());
            if (validExercises.length > 0) {
                const deltas = applyPrecalibrationToBattery(validExercises, readiness, exerciseList, settings);
                setSettings({
                    batteryCalibration: {
                        cnsDelta: deltas.cnsDelta,
                        muscularDelta: deltas.muscularDelta,
                        spinalDelta: deltas.spinalDelta,
                        lastCalibrated: new Date().toISOString(),
                        precalibrationContext: { yearsExperience, injuryHistory, trainingDaysPerWeek },
                    },
                    hasPrecalibratedBattery: true,
                    precalibrationDismissed: false,
                });
            } else {
                const deltas = applyPrecalibrationReadinessOnly(readiness);
                setSettings({
                    batteryCalibration: {
                        cnsDelta: deltas.cnsDelta,
                        muscularDelta: deltas.muscularDelta,
                        spinalDelta: deltas.spinalDelta,
                        lastCalibrated: new Date().toISOString(),
                        precalibrationContext: { yearsExperience, injuryHistory, trainingDaysPerWeek },
                    },
                    hasPrecalibratedBattery: true,
                    precalibrationDismissed: false,
                });
            }
        }
        addToast('Batería pre-calibrada', 'success');
        onComplete();
    };

    const handleSkip = () => {
        setSettings({
            precalibrationDismissed: true,
        });
        onSkip();
    };

    const showReadinessStep = trainedBefore === false || (trainedBefore === true && step === 2);

    if (step === 0) {
        return (
            <div className="p-6 space-y-6">
                <h2 className="text-lg font-black text-white uppercase tracking-tight">Contexto atlético</h2>
                <p className="text-[11px] text-zinc-400">Estas preguntas ayudan a calibrar la batería y reducir lecturas falsas.</p>
                <div className="space-y-4">
                    <div>
                        <label className="block text-[10px] font-bold text-zinc-500 uppercase mb-2">Años de experiencia continua</label>
                        <select
                            value={yearsExperience}
                            onChange={e => setYearsExperience(e.target.value)}
                            className="w-full bg-black/50 border border-white/10 rounded-lg px-3 py-2 text-sm text-white"
                        >
                            <option value="">Selecciona</option>
                            <option value="0-1">0-1 año</option>
                            <option value="1-3">1-3 años</option>
                            <option value="3+">3+ años</option>
                        </select>
                    </div>
                    <div>
                        <label className="block text-[10px] font-bold text-zinc-500 uppercase mb-2">Historial de lesiones</label>
                        <div className="flex flex-wrap gap-2">
                            {[
                                { id: 'none' as const, label: 'Ninguna' },
                                { id: 'recovered' as const, label: 'Alguna recuperada' },
                                { id: 'current' as const, label: 'Lesión actual' },
                            ].map(opt => (
                                <button
                                    key={opt.id}
                                    onClick={() => setInjuryHistory(opt.id)}
                                    className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                                        injuryHistory === opt.id ? 'bg-cyan-500/20 border border-cyan-500/50 text-white' : 'bg-white/5 border border-white/10 text-zinc-400'
                                    }`}
                                >
                                    {opt.label}
                                </button>
                            ))}
                        </div>
                    </div>
                    <div>
                        <label className="block text-[10px] font-bold text-zinc-500 uppercase mb-2">Días de entrenamiento por semana</label>
                        <input
                            type="number"
                            min={1}
                            max={7}
                            value={trainingDaysPerWeek}
                            onChange={e => setTrainingDaysPerWeek(Math.min(7, Math.max(1, parseInt(e.target.value) || 3)))}
                            className="w-full bg-black/50 border border-white/10 rounded-lg px-3 py-2 text-sm text-white"
                        />
                    </div>
                </div>
                <button
                    onClick={() => setStep(1)}
                    className="w-full py-3 rounded-xl bg-white text-black font-black text-xs uppercase tracking-widest flex items-center justify-center gap-2"
                >
                    Siguiente
                    <ChevronRightIcon size={14} />
                </button>
            </div>
        );
    }

    if (trainedBefore === null) {
        return (
            <div className="p-6 space-y-6">
                <h2 className="text-lg font-black text-white uppercase tracking-tight">Pre-calibrar batería</h2>
                <p className="text-[11px] text-zinc-400">Para no mostrar 100% falsamente, indica si ya entrenaste antes de usar la app.</p>
                <div className="grid grid-cols-2 gap-3">
                    <button
                        onClick={() => setTrainedBefore(true)}
                        className="p-5 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 transition-colors text-left"
                    >
                        <DumbbellIcon size={24} className="text-amber-400 mb-2" />
                        <span className="font-bold text-white block">Sí, ya entrené</span>
                        <span className="text-[9px] text-zinc-500">Indica los 3 ejercicios más pesados</span>
                    </button>
                    <button
                        onClick={() => setTrainedBefore(false)}
                        className="p-5 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 transition-colors text-left"
                    >
                        <ZapIcon size={24} className="text-sky-400 mb-2" />
                        <span className="font-bold text-white block">No, empiezo de cero</span>
                        <span className="text-[9px] text-zinc-500">Solo readiness</span>
                    </button>
                </div>
            </div>
        );
    }

    if (trainedBefore === true && step === 1) {
        return (
            <div className="p-6 space-y-4">
                <h2 className="text-lg font-black text-white uppercase tracking-tight">3 ejercicios más pesados</h2>
                <p className="text-[11px] text-zinc-400">Los que más te dejaron antes de usar la app.</p>
                <div className="space-y-3">
                    {exercises.map((ex, idx) => (
                        <div key={idx} className="space-y-2">
                            <div className="flex gap-2">
                                <div className="flex-1 relative">
                                    <input
                                        type="text"
                                        value={searchOpen === idx ? searchQuery : ex.exerciseName}
                                        onChange={e => {
                                            if (searchOpen === idx) setSearchQuery(e.target.value);
                                            else {
                                                setSearchOpen(idx);
                                                setSearchQuery(e.target.value);
                                            }
                                        }}
                                        onFocus={() => setSearchOpen(idx)}
                                        placeholder="Buscar ejercicio..."
                                        className="w-full bg-black/50 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-zinc-500"
                                    />
                                    {searchOpen === idx && filteredExercises.length > 0 && (
                                        <div className="absolute top-full left-0 right-0 mt-1 max-h-40 overflow-y-auto bg-[#111] border border-white/10 rounded-lg z-20">
                                            {filteredExercises.map(e => (
                                                <button
                                                    key={e.id || e.name}
                                                    onClick={() => handleSelectExercise(idx, e.name, e.id)}
                                                    className="w-full px-3 py-2 text-left text-sm text-white hover:bg-white/10"
                                                >
                                                    {e.name}
                                                </button>
                                            ))}
                                        </div>
                                    )}
                                </div>
                                <select
                                    value={ex.intensity}
                                    onChange={e =>
                                        setExercises(prev => {
                                            const next = [...prev];
                                            next[idx] = { ...next[idx], intensity: e.target.value as PrecalibrationExerciseInput['intensity'] };
                                            return next;
                                        })
                                    }
                                    className="bg-black/50 border border-white/10 rounded-lg px-2 py-2 text-[10px] font-mono text-white"
                                >
                                    {INTENSITY_OPTIONS.map(o => (
                                        <option key={o.id} value={o.id}>
                                            {o.label}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    ))}
                </div>
                <button
                    onClick={() => setStep(2)}
                    className="w-full py-3 rounded-xl bg-white text-black font-black text-xs uppercase tracking-widest flex items-center justify-center gap-2"
                >
                    Siguiente: Readiness
                    <ChevronRightIcon size={14} />
                </button>
            </div>
        );
    }

    if (!showReadinessStep) return null;

    return (
        <div className="p-6 space-y-4">
            <h2 className="text-lg font-black text-white uppercase tracking-tight">Estado base (Readiness)</h2>
            <p className="text-[11px] text-zinc-400">Reporta cómo te sientes ahora para afinar la batería inicial.</p>
            <CyberSlider label="Calidad del Sueño" value={sleepQuality} onChange={setSleepQuality} icon={<MoonIcon size={14} />} color="text-indigo-400" accentClass="accent-indigo-500" />
            <CyberSlider label="Estrés del SNC" value={stressLevel} onChange={setStressLevel} icon={<ActivityIcon size={14} />} color="text-rose-400" accentClass="accent-rose-500" inverseScale />
            <CyberSlider label="Daño Muscular" value={doms} onChange={setDoms} icon={<FlameIcon size={14} />} color="text-amber-400" accentClass="accent-amber-500" inverseScale />
            <CyberSlider label="Motivación" value={motivation} onChange={setMotivation} icon={<ZapIcon size={14} />} color="text-sky-400" accentClass="accent-sky-500" />
            <div className="flex gap-2 pt-2">
                <button onClick={handleSkip} className="flex-1 py-3 rounded-xl text-[10px] font-black uppercase bg-zinc-800 text-zinc-400 hover:text-white">
                    Completar después
                </button>
                <button onClick={handleFinish} className="flex-1 py-3 rounded-xl text-[10px] font-black uppercase bg-white text-black">
                    Aplicar
                </button>
            </div>
        </div>
    );
};
