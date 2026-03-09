// components/ReadinessDrawer.tsx - Antes de entrenar, coméntanos cómo te sientes
// Diseño unificado: gris medio-claro, anillos SNC/Columna, preguntas compactas, músculos con ¿Coincide?

import React, { useState, useEffect, useMemo } from 'react';
import {
  getCachedAdaptiveData,
  queuePrediction,
  queueOutcome,
  getConfidenceLabel,
  getConfidenceColor,
} from '../services/augeAdaptiveService';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { calculateGlobalBatteriesAsync, getPerMuscleBatteries } from '../services/auge';
import { getSessionMusclesWithBatteries } from '../utils/sessionMusclesForBattery';
import { getSessionArticularBatteries } from '../utils/sessionArticularBatteries';
import { getTendonImbalanceAlerts, getTendonCompensationSuggestions } from '../services/tendonAlertsService';
import WorkoutDrawer from './workout/WorkoutDrawer';
import type { Session } from '../types';

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

const RING_R = 24;
const RING_CIRC = 2 * Math.PI * RING_R;
const RING_STROKE = '#737373'; // gris
const RING_TRACK = '#d4d4d4'; // gris claro

const getPrecisionScale = (obs: number): 'Bajo' | 'Medio' | 'Alto' => {
  if (obs >= 15) return 'Alto';
  if (obs >= 5) return 'Medio';
  return 'Bajo';
};

/** Selector compacto 1-5: puntos en fila */
const DotSelector: React.FC<{
  value: number;
  onChange: (v: number) => void;
  labels?: [string, string];
}> = ({ value, onChange, labels }) => (
  <div className="flex flex-col">
    <div className="flex items-center gap-1">
      {[1, 2, 3, 4, 5].map((v) => (
        <button
          key={v}
          type="button"
          onClick={() => onChange(v)}
          className={`w-7 h-7 rounded-full border-2 transition-colors ${value === v ? 'bg-[#525252] border-[#525252]' : 'bg-white border-[#a3a3a3] hover:border-[#737373]'
            }`}
          aria-label={`${v} de 5`}
        />
      ))}
    </div>
    {labels && (
      <div className="flex justify-between w-full mt-0.5 text-[9px] text-[#737373]">
        <span>{labels[0]}</span>
        <span>{labels[1]}</span>
      </div>
    )}
  </div>
);

const ReadinessDrawer: React.FC<ReadinessDrawerProps> = ({ isOpen, onClose, onContinue, pendingWorkout }) => {
  const { history, exerciseList, settings, sleepLogs, dailyWellbeingLogs, nutritionLogs, muscleHierarchy, postSessionFeedback, waterLogs } = useAppState();
  const { setSettings } = useAppDispatch();
  const [sleepQuality, setSleepQuality] = useState(3);
  const [moodMotivation, setMoodMotivation] = useState(3);
  const [stressLevel, setStressLevel] = useState(3);
  const [doms, setDoms] = useState(3);
  const [calibCns, setCalibCns] = useState(0);
  const [calibSpinal, setCalibSpinal] = useState(0);
  const [isCalibrating, setIsCalibrating] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [muscleFeedback, setMuscleFeedback] = useState<Record<string, boolean | null>>({});

  const [batteries, setBatteries] = useState<Awaited<ReturnType<typeof calculateGlobalBatteriesAsync>> | null>(null);
  const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});

  const adaptiveCache = useMemo(() => getCachedAdaptiveData(), []);
  const precisionScale = getPrecisionScale(adaptiveCache.totalObservations);

  const sessionMuscles = useMemo(() => {
    if (!pendingWorkout?.session || !exerciseList.length || !Object.keys(perMuscle).length) return [];
    const s = pendingWorkout.session as any;
    const mode = pendingWorkout.weekVariant || 'A';
    const resolved = s?.[`session${mode}`] ?? s;
    const normalizedSession = {
      exercises: resolved?.exercises ?? [],
      parts: resolved?.parts ?? [],
    } as any;
    return getSessionMusclesWithBatteries(normalizedSession, exerciseList, perMuscle).slice(0, 6);
  }, [pendingWorkout, exerciseList, perMuscle]);

  const sessionArticular = useMemo(() => {
    if (!pendingWorkout?.session || !exerciseList.length || !batteries?.articularBatteries) return [];
    const s = pendingWorkout.session as any;
    const mode = pendingWorkout.weekVariant || 'A';
    const resolved = s?.[`session${mode}`] ?? s;
    const normalizedSession = { exercises: resolved?.exercises ?? [], parts: resolved?.parts ?? [] } as any;
    return getSessionArticularBatteries(normalizedSession, exerciseList, batteries.articularBatteries);
  }, [pendingWorkout, exerciseList, batteries?.articularBatteries]);

  const tendonImbalanceAlerts = useMemo(() => {
    if (!batteries?.articularBatteries || !Object.keys(perMuscle).length) return [];
    const muscleIds = sessionMuscles.map((m) => m.id);
    return getTendonImbalanceAlerts(perMuscle, batteries.articularBatteries, muscleIds);
  }, [batteries?.articularBatteries, perMuscle, sessionMuscles]);

  const tendonCompensations = useMemo(() => {
    if (!batteries?.articularBatteries) return [];
    const articularIds = sessionArticular.map((a) => a.id);
    return getTendonCompensationSuggestions(batteries.articularBatteries, articularIds);
  }, [batteries?.articularBatteries, sessionArticular]);

  useEffect(() => {
    if (!isOpen || !history) return;
    calculateGlobalBatteriesAsync(history, sleepLogs || [], dailyWellbeingLogs || [], nutritionLogs || [], settings, exerciseList)
      .then(setBatteries)
      .catch(() => { });
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
      setCalibSpinal(batteries.spinal);
    }
  }, [isOpen, batteries]);

  useEffect(() => {
    if (isOpen) {
      setIsSubmitting(false);
      setMuscleFeedback({});
    }
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
          muscularDelta: calib.muscularDelta ?? 0,
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

  const displayCns = isCalibrating ? calibCns : Math.round(batteries?.cns ?? 0);
  const displaySpinal = isCalibrating ? calibSpinal : Math.round(batteries?.spinal ?? 0);

  return (
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Antes de entrenar, coméntanos cómo te sientes" height="90vh">
      <div className="px-4 pb-[max(1rem,env(safe-area-inset-bottom))] flex flex-col gap-4">
        {/* Anillos SNC y Columna (gris, compactos) */}
        {batteries && (
          <div className="flex justify-center gap-6 py-2 shrink-0">
            <div className="flex flex-col items-center">
              <svg width={56} height={56} className="-rotate-90">
                <circle r={RING_R} cx={28} cy={28} fill="none" stroke={RING_TRACK} strokeWidth="3" />
                <circle
                  r={RING_R}
                  cx={28}
                  cy={28}
                  fill="none"
                  stroke={RING_STROKE}
                  strokeWidth="3"
                  strokeDasharray={`${(displayCns / 100) * RING_CIRC} ${RING_CIRC}`}
                  strokeLinecap="round"
                  style={{ transition: 'stroke-dasharray 0.4s' }}
                />
              </svg>
              <span className="text-[10px] font-medium text-[#525252] mt-1">SNC</span>
              <span className="text-xs font-semibold text-[#1a1a1a]">{displayCns}%</span>
            </div>
            <div className="flex flex-col items-center">
              <svg width={56} height={56} className="-rotate-90">
                <circle r={RING_R} cx={28} cy={28} fill="none" stroke={RING_TRACK} strokeWidth="3" />
                <circle
                  r={RING_R}
                  cx={28}
                  cy={28}
                  fill="none"
                  stroke={RING_STROKE}
                  strokeWidth="3"
                  strokeDasharray={`${(displaySpinal / 100) * RING_CIRC} ${RING_CIRC}`}
                  strokeLinecap="round"
                  style={{ transition: 'stroke-dasharray 0.4s' }}
                />
              </svg>
              <span className="text-[10px] font-medium text-[#525252] mt-1">Columna</span>
              <span className="text-xs font-semibold text-[#1a1a1a]">{displaySpinal}%</span>
            </div>
          </div>
        )}

        {/* Preguntas compactas con selectores de puntos */}
        <div className="space-y-3 shrink-0">
          <div>
            <p className="text-[10px] font-semibold text-[#525252] uppercase tracking-wide mb-1">¿Cómo dormiste?</p>
            <DotSelector value={sleepQuality} onChange={setSleepQuality} labels={['Pésimo', 'Óptimo']} />
          </div>
          <div>
            <p className="text-[10px] font-semibold text-[#525252] uppercase tracking-wide mb-1">¿Ánimo y motivación?</p>
            <DotSelector value={moodMotivation} onChange={setMoodMotivation} labels={['Bajo', 'Alto']} />
          </div>
          <div>
            <p className="text-[10px] font-semibold text-[#525252] uppercase tracking-wide mb-1">¿Nivel de estrés?</p>
            <DotSelector value={stressLevel} onChange={setStressLevel} labels={['Bajo', 'Alto']} />
          </div>
          <div>
            <p className="text-[10px] font-semibold text-[#525252] uppercase tracking-wide mb-1">¿Agujetas o dolor residual?</p>
            <DotSelector value={doms} onChange={setDoms} labels={['Nada', 'Mucho']} />
          </div>
        </div>

        {/* Calibración SNC/Columna (compacta) */}
        {batteries && (
          <div className="shrink-0">
            <button
              type="button"
              onClick={() => setIsCalibrating(!isCalibrating)}
              className={`w-full py-2 text-[11px] font-semibold uppercase rounded-[999px] ${isCalibrating ? 'bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)]' : 'bg-[var(--md-sys-color-surface)] text-[var(--md-sys-color-on-surface-variant)] border border-[var(--md-sys-color-outline-variant)]'}`}
            >
              {isCalibrating ? 'Ajustando SNC / Columna' : 'Ajustar SNC / Columna'}
            </button>
            {isCalibrating && (
              <div className="mt-2 space-y-2">
                <div className="flex items-center gap-2">
                  <span className="text-[9px] text-[#525252] w-12">SNC</span>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={calibCns}
                    onChange={(e) => setCalibCns(parseInt(e.target.value))}
                    className="flex-1 h-1.5 accent-[#525252] bg-[#d4d4d4]"
                  />
                  <span className="text-[9px] font-mono w-6">{calibCns}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-[9px] text-[#525252] w-12">Columna</span>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={calibSpinal}
                    onChange={(e) => setCalibSpinal(parseInt(e.target.value))}
                    className="flex-1 h-1.5 accent-[#525252] bg-[#d4d4d4]"
                  />
                  <span className="text-[9px] font-mono w-6">{calibSpinal}</span>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Alertas de desfase músculo-tendón */}
        {tendonImbalanceAlerts.length > 0 && (
          <div className="shrink-0 space-y-2">
            {tendonImbalanceAlerts.map((a, i) => (
              <div
                key={i}
                className={`p-3 rounded-lg border text-[10px] ${a.type === 'danger'
                    ? 'bg-red-50 border-red-200 text-red-800'
                    : 'bg-amber-50 border-amber-200 text-amber-800'
                  }`}
              >
                <p className="font-semibold uppercase tracking-wide mb-0.5">Precaución: tendones</p>
                <p className="leading-relaxed">{a.message}</p>
              </div>
            ))}
          </div>
        )}

        {/* Sugerencias de compensación tendinosa */}
        {tendonCompensations.length > 0 && (
          <div className="shrink-0 space-y-1.5">
            <p className="text-[10px] font-semibold text-[#525252] uppercase tracking-wide">Recomendaciones</p>
            {tendonCompensations.map((s, i) => (
              <div key={i} className="p-2.5 rounded-lg bg-white/80 border border-[#d4d4d4] text-[10px] text-[#525252]">
                <span className="font-semibold text-[#1a1a1a]">{s.title}:</span> {s.message}
              </div>
            ))}
          </div>
        )}

        {/* Baterías articulares (tendones) de la sesión */}
        {sessionArticular.length > 0 && batteries && (
          <div className="shrink-0">
            <p className="text-[10px] font-semibold text-[#525252] uppercase tracking-wide mb-2">Tendones y articulaciones hoy</p>
            <div className="flex flex-wrap gap-2">
              {sessionArticular.map((ab) => (
                <div key={ab.id} className="flex items-center gap-1.5 px-2.5 py-1.5 bg-white/80 border border-[#d4d4d4] rounded">
                  <span className="text-[9px] text-[#525252]">{ab.shortLabel}</span>
                  <span className={`text-[10px] font-mono font-semibold ${ab.battery >= 70 ? 'text-emerald-600' : ab.battery >= 40 ? 'text-amber-600' : 'text-red-600'}`}>{ab.battery}%</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Músculos involucrados: ¿Coincide? */}
        {sessionMuscles.length > 0 && (
          <div className="shrink-0">
            <p className="text-[10px] font-semibold text-[#525252] uppercase tracking-wide mb-2">Músculos hoy · ¿Coincide con lo que sientes?</p>
            <div className="space-y-1.5">
              {sessionMuscles.map((m) => (
                <div key={m.id} className="flex items-center justify-between py-1.5 border-b border-[#d4d4d4]/80 last:border-0">
                  <span className="text-xs text-[#1a1a1a]">{m.label}</span>
                  <span className="text-[10px] text-[#737373] mr-2">{m.battery}%</span>
                  <div className="flex gap-1">
                    <button
                      type="button"
                      onClick={() => setMuscleFeedback((f) => ({ ...f, [m.id]: true }))}
                      className={`px-2.5 py-1 text-[9px] font-medium ${muscleFeedback[m.id] === true ? 'bg-[#525252] text-white' : 'bg-white text-[#525252] border border-[#a3a3a3]'}`}
                    >
                      Sí
                    </button>
                    <button
                      type="button"
                      onClick={() => setMuscleFeedback((f) => ({ ...f, [m.id]: false }))}
                      className={`px-2.5 py-1 text-[9px] font-medium ${muscleFeedback[m.id] === false ? 'bg-[#525252] text-white' : 'bg-white text-[#525252] border border-[#a3a3a3]'}`}
                    >
                      No
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Botones fijos al fondo */}
        <div className="mt-auto pt-4 space-y-2 shrink-0">
          <button
            onClick={handleStart}
            disabled={isSubmitting}
            className="w-full py-3.5 text-[11px] font-semibold uppercase tracking-wide rounded-[999px] bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] border border-[var(--md-sys-color-primary)] disabled:opacity-50"
          >
            {isSubmitting ? 'Sincronizando...' : 'Comenzar Batalla'}
          </button>
          <button onClick={onClose} disabled={isSubmitting} className="w-full py-2.5 text-[11px] font-medium uppercase tracking-wide text-[var(--md-sys-color-on-surface-variant)]">
            Cancelar
          </button>
        </div>
      </div>
    </WorkoutDrawer>
  );
};

export default ReadinessDrawer;

