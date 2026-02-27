// components/ReadinessDrawer.tsx - Drawer de readiness check (rediseño con baterías AUGE)

import React, { useState, useEffect, useMemo } from 'react';
import { MoonIcon, ZapIcon, BatteryIcon, BrainIcon, ActivityIcon, TargetIcon } from './icons';
import {
  getCachedAdaptiveData,
  queuePrediction,
  queueOutcome,
  getConfidenceLabel,
  getConfidenceColor,
} from '../services/augeAdaptiveService';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { calculateGlobalBatteriesAsync, getPerMuscleBatteries } from '../services/auge';
import WorkoutDrawer from './workout/WorkoutDrawer';
import type { Session, Exercise } from '../types';

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
  pendingWorkout?: { session: Session; program: any; weekVariant?: 'A' | 'B' | 'C' | 'D'; location?: any } | null;
}

const SIMPLE_LABELS: Record<string, string> = {
  'Tríceps': 'Tríceps', 'Bíceps': 'Bíceps', 'Pectorales': 'Pectorales', 'Dorsales': 'Dorsales',
  'Deltoides': 'Hombros', 'Cuádriceps': 'Cuádriceps', 'Glúteos': 'Glúteos', 'Isquiosurales': 'Isquiotibiales',
  'Pantorrillas': 'Pantorrillas', 'Abdomen': 'Abdomen', 'Espalda Baja': 'Espalda Baja', 'Trapecio': 'Trapecio',
};

const getPrecisionScale = (obs: number): 'Bajo' | 'Medio' | 'Alto' => {
  if (obs >= 15) return 'Alto';
  if (obs >= 5) return 'Medio';
  return 'Bajo';
};

const ReadinessDrawer: React.FC<ReadinessDrawerProps> = ({ isOpen, onClose, onContinue, pendingWorkout }) => {
  const { history, exerciseList, settings, sleepLogs, dailyWellbeingLogs, nutritionLogs, muscleHierarchy, postSessionFeedback, waterLogs } = useAppState();
  const { setSettings } = useAppDispatch();
  const [sleepQuality, setSleepQuality] = useState(3);
  const [moodMotivation, setMoodMotivation] = useState(3);
  const [stressLevel, setStressLevel] = useState(3);
  const [doms, setDoms] = useState(3);
  const [calibCns, setCalibCns] = useState(0);
  const [calibMusc, setCalibMusc] = useState(0);
  const [calibSpinal, setCalibSpinal] = useState(0);
  const [isCalibrating, setIsCalibrating] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [batteries, setBatteries] = useState<Awaited<ReturnType<typeof calculateGlobalBatteriesAsync>> | null>(null);
  const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});

  const adaptiveCache = useMemo(() => getCachedAdaptiveData(), []);
  const precisionScale = getPrecisionScale(adaptiveCache.totalObservations);

  const musclesForToday = useMemo(() => {
    if (!pendingWorkout?.session) return [];
    const mode = pendingWorkout.weekVariant || 'A';
    const session = pendingWorkout.session as any;
    const exercises = (session.parts && session.parts.length > 0 ? session.parts.flatMap((p: any) => p.exercises) : session.exercises)
      ?? (session[`session${mode}`]?.parts ? session[`session${mode}`].parts.flatMap((p: any) => p.exercises) : session[`session${mode}`]?.exercises ?? []);
    const muscleCount: Record<string, number> = {};
    exercises.forEach((ex: Exercise) => {
      const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.name);
      info?.involvedMuscles?.forEach(m => {
        const name = m.muscle;
        if (m.role === 'primary') muscleCount[name] = (muscleCount[name] || 0) + (ex.sets?.length ?? 0);
        else if (m.role === 'secondary') muscleCount[name] = (muscleCount[name] || 0) + (ex.sets?.length ?? 0) * 0.5;
        else if (m.role === 'stabilizer') muscleCount[name] = (muscleCount[name] || 0) + (ex.sets?.length ?? 0) * 0.25;
      });
    });
    const primary: string[] = Object.entries(muscleCount).filter(([, v]) => v >= 1).map(([k]) => k);
    const secondary: string[] = Object.entries(muscleCount).filter(([, v]) => v > 3).map(([k]) => k);
    const stabilizers: string[] = Object.entries(muscleCount).filter(([, v]) => v > 4).map(([k]) => k);
    const combined = [...new Set([...(primary ?? []), ...(secondary ?? []).filter(s => !(primary ?? []).includes(s)), ...(stabilizers ?? []).filter(s => !(primary ?? []).includes(s) && !(secondary ?? []).includes(s))])];
    const simple = combined.filter(m => !['Romboides', 'Transverso abdominal'].some(k => m.includes(k)) && !['cabeza-larga', 'cabeza-lateral', 'cabeza-medial'].some(k => m.toLowerCase().includes(k)));
    return simple.slice(0, 6);
  }, [pendingWorkout, exerciseList]);

  useEffect(() => {
    if (!isOpen || !history) return;
    calculateGlobalBatteriesAsync(history, sleepLogs || [], dailyWellbeingLogs || [], nutritionLogs || [], settings, exerciseList)
      .then(setBatteries)
      .catch(() => {});
  }, [isOpen, history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList]);

  useEffect(() => {
    if (!isOpen || !history) return;
    try {
      const hierarchy = muscleHierarchy || { bodyPartHierarchy: {}, specialCategories: {}, muscleToBodyPart: {} };
      const pm = getPerMuscleBatteries(history, exerciseList, sleepLogs || [], settings, hierarchy, postSessionFeedback || [], waterLogs || [], dailyWellbeingLogs || [], nutritionLogs || []);
      setPerMuscle(pm);
    } catch {
      setPerMuscle({});
    }
  }, [isOpen, history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs, dailyWellbeingLogs, nutritionLogs]);

  useEffect(() => {
    if (isOpen && batteries) {
      setCalibCns(batteries.cns);
      setCalibMusc(batteries.muscular);
      setCalibSpinal(batteries.spinal);
    }
  }, [isOpen, batteries]);

  useEffect(() => {
    if (isOpen) setIsSubmitting(false);
  }, [isOpen]);

  const handleStart = () => {
    if (isSubmitting) return;
    setIsSubmitting(true);

    const now = new Date().toISOString();
    const readinessAvg = (sleepQuality + (6 - stressLevel) + (6 - doms) + moodMotivation) / 4;
    const predId = `readiness-${Date.now()}`;
    queuePrediction({
      prediction_id: predId,
      timestamp: now,
      system: 'readiness',
      predicted_value: 3,
      context: { precision: precisionScale, observations: adaptiveCache.totalObservations },
    });
    queueOutcome({
      prediction_id: predId,
      actual_value: Math.round(readinessAvg),
      feedback_source: 'readiness_modal',
    });

    if (isCalibrating && batteries) {
      const calib = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0 };
      setSettings({
        batteryCalibration: {
          cnsDelta: calibCns - (batteries.cns - (calib.cnsDelta ?? 0)),
          muscularDelta: calibMusc - (batteries.muscular - (calib.muscularDelta ?? 0)),
          spinalDelta: calibSpinal - (batteries.spinal - (calib.spinalDelta ?? 0)),
          lastCalibrated: now,
        },
      });
    }

    setTimeout(() => {
      onContinue({
        sleepQuality,
        stressLevel,
        doms,
        motivation: moodMotivation,
      });
    }, 150);
  };

  const displayCns = isCalibrating ? calibCns : (batteries?.cns ?? 0);
  const displayMusc = isCalibrating ? calibMusc : (batteries?.muscular ?? 0);
  const displaySpinal = isCalibrating ? calibSpinal : (batteries?.spinal ?? 0);

  return (
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Estado Base" height="90vh">
      <div className="p-5 space-y-5">
        <p className="text-[10px] text-slate-500 italic">Precisión: <span className="font-black text-amber-400">{precisionScale}</span></p>
        <p className="text-[9px] text-slate-600">Estos números mejoran conforme entrenes y reportes pre y post entreno.</p>

        <div className="space-y-4">
          <div>
            <label className="block text-[10px] font-black text-slate-400 uppercase tracking-widest mb-2">¿Cómo dormiste anoche?</label>
            <div className="flex items-center gap-3">
              <input type="range" min="1" max="5" value={sleepQuality} onChange={(e) => setSleepQuality(parseInt(e.target.value))} className="w-full h-2 rounded-lg appearance-none cursor-pointer bg-slate-800 accent-indigo-500" />
              <span className="text-lg font-black text-white w-8 text-center">{sleepQuality}/5</span>
            </div>
            <div className="flex justify-between text-[8px] text-slate-500 mt-1"><span>Pésimo</span><span>Óptimo</span></div>
          </div>
          <div>
            <label className="block text-[10px] font-black text-slate-400 uppercase tracking-widest mb-2">¿Cómo está tu ánimo y motivación para esta sesión?</label>
            <div className="flex items-center gap-3">
              <input type="range" min="1" max="5" value={moodMotivation} onChange={(e) => setMoodMotivation(parseInt(e.target.value))} className="w-full h-2 rounded-lg appearance-none cursor-pointer bg-slate-800 accent-sky-500" />
              <span className="text-lg font-black text-white w-8 text-center">{moodMotivation}/5</span>
            </div>
            <div className="flex justify-between text-[8px] text-slate-500 mt-1"><span>Bajo</span><span>Alto</span></div>
          </div>
          <div>
            <label className="block text-[10px] font-black text-slate-400 uppercase tracking-widest mb-2">¿Nivel de estrés general?</label>
            <div className="flex items-center gap-3">
              <input type="range" min="1" max="5" value={stressLevel} onChange={(e) => setStressLevel(parseInt(e.target.value))} className="w-full h-2 rounded-lg appearance-none cursor-pointer bg-slate-800 accent-orange-500" />
              <span className="text-lg font-black text-white w-8 text-center">{stressLevel}/5</span>
            </div>
            <div className="flex justify-between text-[8px] text-slate-500 mt-1"><span>Bajo</span><span>Alto</span></div>
          </div>
          <div>
            <label className="block text-[10px] font-black text-slate-400 uppercase tracking-widest mb-2">¿Agujetas o dolor muscular residual?</label>
            <div className="flex items-center gap-3">
              <input type="range" min="1" max="5" value={doms} onChange={(e) => setDoms(parseInt(e.target.value))} className="w-full h-2 rounded-lg appearance-none cursor-pointer bg-slate-800 accent-rose-500" />
              <span className="text-lg font-black text-white w-8 text-center">{doms}/5</span>
            </div>
            <div className="flex justify-between text-[8px] text-slate-500 mt-1"><span>Nada</span><span>Mucho</span></div>
          </div>
        </div>

        {batteries && (
          <div className="space-y-4 pt-4 border-t border-white/5">
            <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Baterías AUGE</p>
            <div className="grid grid-cols-3 gap-2">
              <div className="bg-[#111] border border-rose-500/20 p-3 rounded-xl text-center">
                <ActivityIcon size={16} className="text-rose-400 mx-auto mb-1" />
                <p className="text-lg font-black text-rose-400">{displayMusc}%</p>
                <p className="text-[8px] text-slate-500">Muscular</p>
                <p className="text-[7px] text-slate-600 mt-1">Así de listos están los músculos que trabajarás hoy.</p>
              </div>
              <div className="bg-[#111] border border-sky-500/20 p-3 rounded-xl text-center">
                <BrainIcon size={16} className="text-sky-400 mx-auto mb-1" />
                <p className="text-lg font-black text-sky-400">{displayCns}%</p>
                <p className="text-[8px] text-slate-500">SNC</p>
                <p className="text-[7px] text-slate-600 mt-1">Así estimamos tu energía para hoy.</p>
              </div>
              <div className="bg-[#111] border border-amber-500/20 p-3 rounded-xl text-center">
                <TargetIcon size={16} className="text-amber-400 mx-auto mb-1" />
                <p className="text-lg font-black text-amber-400">{displaySpinal}%</p>
                <p className="text-[8px] text-slate-500">Columna</p>
                <p className="text-[7px] text-slate-600 mt-1">Así de lista está tu columna vertebral.</p>
              </div>
            </div>
            <p className="text-[9px] text-slate-500 italic">¿Estás de acuerdo con esos valores? Actualízalos si quieres corregirlos.</p>
            <button type="button" onClick={() => setIsCalibrating(!isCalibrating)} className={`w-full py-2 rounded-lg text-[10px] font-black uppercase ${isCalibrating ? 'bg-amber-500/20 text-amber-400 border border-amber-500/40' : 'bg-slate-800 text-slate-400 border border-slate-700'}`}>
              {isCalibrating ? 'Ajustando... (usa los sliders arriba)' : 'Actualizar valores'}
            </button>
            {isCalibrating && (
              <div className="space-y-2 animate-fade-in">
                <div className="flex items-center gap-2">
                  <span className="text-[9px] text-rose-400 w-16">Muscular</span>
                  <input type="range" min="0" max="100" value={calibMusc} onChange={(e) => setCalibMusc(parseInt(e.target.value))} className="flex-1 accent-rose-500" />
                  <span className="text-[9px] font-mono w-8">{calibMusc}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-[9px] text-sky-400 w-16">SNC</span>
                  <input type="range" min="0" max="100" value={calibCns} onChange={(e) => setCalibCns(parseInt(e.target.value))} className="flex-1 accent-sky-500" />
                  <span className="text-[9px] font-mono w-8">{calibCns}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-[9px] text-amber-400 w-16">Columna</span>
                  <input type="range" min="0" max="100" value={calibSpinal} onChange={(e) => setCalibSpinal(parseInt(e.target.value))} className="flex-1 accent-amber-500" />
                  <span className="text-[9px] font-mono w-8">{calibSpinal}</span>
                </div>
              </div>
            )}
          </div>
        )}

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
