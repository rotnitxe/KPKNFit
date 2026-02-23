// components/ReadinessDrawer.tsx - Drawer de readiness check (migración de ReadinessCheckModal)

import React, { useState, useEffect, useMemo } from 'react';
import { MoonIcon, ActivityIcon, FlameIcon, ZapIcon, BatteryIcon, BrainIcon } from './icons';
import {
  getCachedAdaptiveData,
  queuePrediction,
  queueOutcome,
  getConfidenceLabel,
  getConfidenceColor,
} from '../services/augeAdaptiveService';
import WorkoutDrawer from './workout/WorkoutDrawer';

interface ReadinessData {
  sleepQuality: number;
  stressLevel: number;
  doms: number;
  motivation: number;
}

interface ReadinessDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onContinue: (data: ReadinessData) => void;
}

const CyberSlider: React.FC<{ label: string; value: number; onChange: (v: number) => void; icon: React.ReactNode; color: string; accentClass: string; inverseScale?: boolean }> = ({ label, value, onChange, icon, color, accentClass, inverseScale }) => (
  <div className="bg-[#0d0d0d] p-4 rounded-xl border border-cyber-cyan/20 mb-3">
    <div className="flex justify-between items-center mb-2">
      <label className="text-[10px] font-mono font-black uppercase tracking-widest text-slate-400 flex items-center gap-2">
        <span className={color}>{icon}</span> {label}
      </label>
      <span className={`text-sm font-mono font-black ${color}`}>{value}/5</span>
    </div>
    <input
      type="range" min="1" max="5"
      value={value}
      onChange={(e) => onChange(parseInt(e.target.value))}
      className={`w-full h-2 rounded-lg appearance-none cursor-pointer bg-slate-800 border border-slate-700 ${accentClass}`}
    />
    <div className="flex justify-between text-[9px] font-mono text-slate-500 uppercase tracking-widest mt-1.5">
      <span>{inverseScale ? 'Óptimo' : 'Pésimo'}</span>
      <span>{inverseScale ? 'Pésimo' : 'Óptimo'}</span>
    </div>
  </div>
);

const ReadinessDrawer: React.FC<ReadinessDrawerProps> = ({ isOpen, onClose, onContinue }) => {
  const [sleepQuality, setSleepQuality] = useState(3);
  const [stressLevel, setStressLevel] = useState(3);
  const [doms, setDoms] = useState(3);
  const [motivation, setMotivation] = useState(3);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const adaptiveCache = useMemo(() => getCachedAdaptiveData(), []);
  const confidenceLabel = getConfidenceLabel(adaptiveCache.totalObservations);
  const confidenceColor = getConfidenceColor(adaptiveCache.totalObservations);

  const augePrediction = useMemo(() => {
    const gpCurve = adaptiveCache.gpCurve;
    const banister = adaptiveCache.banister;
    let score = 3;
    if (banister?.systems?.muscular) {
      const perf = banister.systems.muscular.performance;
      if (perf.length > 0) {
        const latest = perf[perf.length - 1];
        if (latest > 0.7) score = 5;
        else if (latest > 0.4) score = 4;
        else if (latest > 0.1) score = 3;
        else if (latest > -0.2) score = 2;
        else score = 1;
      }
    }
    if (gpCurve) {
      const latestFatigue = gpCurve.mean_fatigue[gpCurve.mean_fatigue.length - 1] ?? 0.5;
      if (latestFatigue > 0.7) score = Math.max(1, score - 1);
      else if (latestFatigue < 0.3) score = Math.min(5, score + 1);
    }
    const labels: Record<number, { text: string; color: string }> = {
      1: { text: 'ROJO — Descanso recomendado', color: 'text-red-400' },
      2: { text: 'NARANJA — Sesión ligera', color: 'text-cyber-warning' },
      3: { text: 'AMARILLO — Proceder con cautela', color: 'text-yellow-400' },
      4: { text: 'VERDE — Buen día para entrenar', color: 'text-emerald-400' },
      5: { text: 'VERDE+ — Óptimo para PRs', color: 'text-emerald-300' },
    };
    return { score, ...(labels[score] || labels[3]) };
  }, [adaptiveCache]);

  useEffect(() => {
    if (isOpen) setIsSubmitting(false);
  }, [isOpen]);

  const handleStart = () => {
    if (isSubmitting) return;
    setIsSubmitting(true);

    const now = new Date().toISOString();
    const readinessAvg = (sleepQuality + (6 - stressLevel) + (6 - doms) + motivation) / 4;
    const predId = `readiness-${Date.now()}`;
    queuePrediction({
      prediction_id: predId,
      timestamp: now,
      system: 'readiness',
      predicted_value: augePrediction.score,
      context: { confidence: confidenceLabel, observations: adaptiveCache.totalObservations },
    });
    queueOutcome({
      prediction_id: predId,
      actual_value: Math.round(readinessAvg),
      feedback_source: 'readiness_modal',
    });

    setTimeout(() => {
      onContinue({ sleepQuality, stressLevel, doms, motivation });
    }, 150);
  };

  return (
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Estado Base" height="90vh">
      <div className="p-5 space-y-5">
        <div className="p-4 bg-[#0d0d0d] border border-cyber-cyan/20 rounded-xl">
          <div className="flex items-center gap-2 mb-2">
            <BrainIcon size={14} className="text-cyber-cyan/80" />
            <span className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90">Predicción AUGE</span>
          </div>
          <p className={`text-sm font-mono font-black ${augePrediction.color}`}>{augePrediction.text}</p>
          <p className={`text-[9px] mt-1 font-mono font-bold uppercase tracking-widest ${confidenceColor}`}>
            {adaptiveCache.totalObservations} observaciones — {confidenceLabel}
          </p>
        </div>

        <p className="text-[10px] font-mono text-slate-500 text-center uppercase tracking-widest">Reporta tu realidad.</p>
        <CyberSlider label="Calidad del Sueño" value={sleepQuality} onChange={setSleepQuality} icon={<MoonIcon size={16} />} color="text-indigo-400" accentClass="accent-indigo-500" />
        <CyberSlider label="Estrés del SNC" value={stressLevel} onChange={setStressLevel} icon={<ActivityIcon size={16} />} color="text-rose-400" accentClass="accent-rose-500" inverseScale={true} />
        <CyberSlider label="Daño Muscular" value={doms} onChange={setDoms} icon={<FlameIcon size={16} />} color="text-amber-400" accentClass="accent-amber-500" inverseScale={true} />
        <CyberSlider label="Motivación" value={motivation} onChange={setMotivation} icon={<ZapIcon size={16} />} color="text-sky-400" accentClass="accent-sky-500" />

        <button onClick={handleStart} disabled={isSubmitting} className="w-full py-4 rounded-xl text-[10px] font-mono font-black uppercase tracking-widest bg-cyber-cyan text-white hover:bg-cyber-cyan/90 transition-colors border border-cyber-cyan/30 disabled:opacity-50">
          {isSubmitting ? 'Sincronizando...' : 'Comenzar Batalla'}
        </button>
        <button onClick={onClose} disabled={isSubmitting} className="w-full py-3 text-slate-500 text-[10px] font-mono font-black uppercase tracking-widest hover:text-white transition-colors">
          Cancelar
        </button>
      </div>
    </WorkoutDrawer>
  );
};

export default ReadinessDrawer;
