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
const RING_STROKE = '#7a5d20';
const RING_TRACK = '#e9dcc8';

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
    <div className="flex items-center gap-2">
      {[1, 2, 3, 4, 5].map((v) => (
        <button
          key={v}
          type="button"
          onClick={() => onChange(v)}
          className={`h-8 w-8 rounded-full border transition-all shadow-[inset_0_1px_0_rgba(255,255,255,0.7)] ${
            value === v
              ? 'border-[var(--md-sys-color-primary)] bg-[var(--md-sys-color-primary-container)]'
              : 'border-[var(--md-sys-color-outline-variant)] bg-white/75 hover:border-[var(--md-sys-color-outline)]'
            }`}
          aria-label={`${v} de 5`}
        />
      ))}
    </div>
    {labels && (
      <div className="mt-1 flex w-full justify-between text-[10px] text-[var(--md-sys-color-on-surface-variant)]">
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

  useEffect(() => {
    if (!isOpen) return;
    document.body.classList.add('workout-theme-active');
    return () => {
      document.body.classList.remove('workout-theme-active');
    };
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
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Readiness antes de entrenar" height="90vh">
      <div className="flex flex-col gap-4 px-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
        <div className="liquid-glass-panel rounded-[32px] border border-white/70 p-5 shadow-[0_16px_34px_rgba(80,58,23,0.12)]">
          <div className="flex items-start justify-between gap-3">
            <div>
              <span className="inline-flex rounded-full border border-white/70 bg-[var(--md-sys-color-secondary-container)] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--md-sys-color-on-secondary-container)]">
                Pre-entreno
              </span>
              <h2 className="mt-3 text-[28px] font-medium tracking-[-0.03em] text-[var(--md-sys-color-on-surface)]">
                Como llegas hoy
              </h2>
              <p className="mt-2 text-[13px] leading-5 text-[var(--md-sys-color-on-surface-variant)]">
                Ajustamos la sesion con tu percepcion actual, tus baterias y las alertas de carga para entrar fino desde el primer bloque.
              </p>
            </div>
            <div className="rounded-[20px] border border-white/70 bg-white/60 px-3 py-2 text-right shadow-[0_10px_20px_rgba(80,58,23,0.08)]">
              <span className="block text-[10px] font-semibold uppercase tracking-[0.14em] text-[var(--md-sys-color-on-surface-variant)]">
                Precision
              </span>
              <span className="mt-1 block text-[15px] font-semibold text-[var(--md-sys-color-on-surface)]">
                {precisionScale}
              </span>
              <span className="block text-[10px] text-[var(--md-sys-color-on-surface-variant)]">
                {adaptiveCache.totalObservations} obs
              </span>
            </div>
          </div>

          {batteries && (
            <div className="mt-5 grid grid-cols-2 gap-3">
              {[
                { label: 'SNC', value: displayCns },
                { label: 'Columna', value: displaySpinal },
              ].map((item) => (
                <div key={item.label} className="rounded-[24px] border border-white/70 bg-white/55 p-4 shadow-[0_10px_24px_rgba(80,58,23,0.08)]">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <span className="block text-[10px] font-semibold uppercase tracking-[0.14em] text-[var(--md-sys-color-on-surface-variant)]">
                        {item.label}
                      </span>
                      <span className="mt-2 block text-[24px] font-medium tracking-[-0.03em] text-[var(--md-sys-color-on-surface)]">
                        {item.value}%
                      </span>
                    </div>
                    <svg width={56} height={56} className="-rotate-90">
                      <circle r={RING_R} cx={28} cy={28} fill="none" stroke={RING_TRACK} strokeWidth="4" />
                      <circle
                        r={RING_R}
                        cx={28}
                        cy={28}
                        fill="none"
                        stroke={RING_STROKE}
                        strokeWidth="4"
                        strokeDasharray={`${(item.value / 100) * RING_CIRC} ${RING_CIRC}`}
                        strokeLinecap="round"
                        style={{ transition: 'stroke-dasharray 0.4s' }}
                      />
                    </svg>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="liquid-glass-panel rounded-[32px] border border-white/70 p-5 shadow-[0_16px_34px_rgba(80,58,23,0.12)]">
          <span className="block text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">
            Check rapido
          </span>
          <div className="mt-4 space-y-4">
            <div>
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">Como dormiste</p>
              <DotSelector value={sleepQuality} onChange={setSleepQuality} labels={['Pesimo', 'Optimo']} />
            </div>
            <div>
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">Animo y motivacion</p>
              <DotSelector value={moodMotivation} onChange={setMoodMotivation} labels={['Bajo', 'Alto']} />
            </div>
            <div>
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">Nivel de estres</p>
              <DotSelector value={stressLevel} onChange={setStressLevel} labels={['Bajo', 'Alto']} />
            </div>
            <div>
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">Agujetas o dolor residual</p>
              <DotSelector value={doms} onChange={setDoms} labels={['Nada', 'Mucho']} />
            </div>
          </div>
        </div>

        {batteries && (
          <div className="liquid-glass-panel rounded-[32px] border border-white/70 p-5 shadow-[0_16px_34px_rgba(80,58,23,0.12)]">
            <div className="flex items-center justify-between gap-3">
              <div>
                <span className="block text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">
                  Ajuste fino
                </span>
                <p className="mt-1 text-[13px] text-[var(--md-sys-color-on-surface-variant)]">
                  Corrige el estimado si tu sistema nervioso o tu espalda no coinciden con la sensacion real.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setIsCalibrating(!isCalibrating)}
                className={`min-h-[44px] rounded-full px-4 text-[11px] font-semibold uppercase tracking-[0.14em] transition-all ${
                  isCalibrating
                    ? 'border border-white/70 bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] shadow-[0_12px_24px_rgba(122,93,32,0.16)]'
                    : 'border border-[var(--md-sys-color-outline-variant)] bg-white/60 text-[var(--md-sys-color-on-surface-variant)]'
                }`}
              >
                {isCalibrating ? 'Editando' : 'Calibrar'}
              </button>
            </div>

            {isCalibrating && (
              <div className="mt-4 space-y-3">
                {[
                  { label: 'SNC', value: calibCns, setValue: setCalibCns },
                  { label: 'Columna', value: calibSpinal, setValue: setCalibSpinal },
                ].map((item) => (
                  <div key={item.label} className="rounded-[24px] border border-[var(--md-sys-color-outline-variant)] bg-white/60 px-4 py-3">
                    <div className="mb-2 flex items-center justify-between">
                      <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-[var(--md-sys-color-on-surface-variant)]">{item.label}</span>
                      <span className="text-[13px] font-semibold text-[var(--md-sys-color-on-surface)]">{item.value}%</span>
                    </div>
                    <input
                      type="range"
                      min="0"
                      max="100"
                      value={item.value}
                      onChange={(e) => item.setValue(parseInt(e.target.value))}
                      className="h-2 w-full accent-[var(--md-sys-color-primary)]"
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {tendonImbalanceAlerts.length > 0 && (
          <div className="space-y-2">
            {tendonImbalanceAlerts.map((alert, index) => (
              <div
                key={index}
                className={`rounded-[28px] border p-4 text-[12px] shadow-[0_12px_24px_rgba(80,58,23,0.08)] ${
                  alert.type === 'danger'
                    ? 'border-[var(--md-sys-color-error)]/30 bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)]'
                    : 'border-[var(--md-sys-color-primary)]/20 bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)]'
                }`}
              >
                <p className="mb-1 text-[11px] font-semibold uppercase tracking-[0.16em]">Precaucion de tendones</p>
                <p className="leading-5">{alert.message}</p>
              </div>
            ))}
          </div>
        )}

        {(tendonCompensations.length > 0 || sessionArticular.length > 0) && (
          <div className="liquid-glass-panel rounded-[32px] border border-white/70 p-5 shadow-[0_16px_34px_rgba(80,58,23,0.12)]">
            <span className="block text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">
              Tejido y articulaciones
            </span>

            {sessionArticular.length > 0 && (
              <div className="mt-4 flex flex-wrap gap-2">
                {sessionArticular.map((ab) => (
                  <div key={ab.id} className="rounded-full border border-[var(--md-sys-color-outline-variant)] bg-white/65 px-3 py-2 text-[11px] shadow-[0_8px_16px_rgba(80,58,23,0.05)]">
                    <span className="font-medium text-[var(--md-sys-color-on-surface)]">{ab.shortLabel}</span>
                    <span className={`ml-2 font-semibold ${ab.battery >= 70 ? 'text-emerald-700' : ab.battery >= 40 ? 'text-amber-700' : 'text-[var(--md-sys-color-error)]'}`}>{ab.battery}%</span>
                  </div>
                ))}
              </div>
            )}

            {tendonCompensations.length > 0 && (
              <div className="mt-4 space-y-2">
                {tendonCompensations.map((suggestion, index) => (
                  <div key={index} className="rounded-[22px] border border-[var(--md-sys-color-outline-variant)] bg-white/60 p-3 text-[12px] leading-5 text-[var(--md-sys-color-on-surface-variant)]">
                    <span className="font-semibold text-[var(--md-sys-color-on-surface)]">{suggestion.title}:</span> {suggestion.message}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {sessionMuscles.length > 0 && (
          <div className="liquid-glass-panel rounded-[32px] border border-white/70 p-5 shadow-[0_16px_34px_rgba(80,58,23,0.12)]">
            <span className="block text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">
              Musculos del dia
            </span>
            <p className="mt-1 text-[13px] text-[var(--md-sys-color-on-surface-variant)]">
              Confirma si la lectura muscular coincide con lo que sientes ahora mismo.
            </p>

            <div className="mt-4 space-y-2">
              {sessionMuscles.map((muscle) => (
                <div key={muscle.id} className="rounded-[22px] border border-[var(--md-sys-color-outline-variant)] bg-white/60 p-3 shadow-[0_8px_16px_rgba(80,58,23,0.05)]">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <span className="block text-[14px] font-medium text-[var(--md-sys-color-on-surface)]">{muscle.label}</span>
                      <span className="mt-1 block text-[11px] uppercase tracking-[0.14em] text-[var(--md-sys-color-on-surface-variant)]">
                        {muscle.battery}% disponible
                      </span>
                    </div>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => setMuscleFeedback((feedback) => ({ ...feedback, [muscle.id]: true }))}
                        className={`min-h-[40px] rounded-full px-3 text-[11px] font-semibold uppercase tracking-[0.14em] transition-all ${
                          muscleFeedback[muscle.id] === true
                            ? 'border border-[var(--md-sys-color-primary)] bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)]'
                            : 'border border-[var(--md-sys-color-outline-variant)] bg-white/70 text-[var(--md-sys-color-on-surface-variant)]'
                        }`}
                      >
                        Si
                      </button>
                      <button
                        type="button"
                        onClick={() => setMuscleFeedback((feedback) => ({ ...feedback, [muscle.id]: false }))}
                        className={`min-h-[40px] rounded-full px-3 text-[11px] font-semibold uppercase tracking-[0.14em] transition-all ${
                          muscleFeedback[muscle.id] === false
                            ? 'border border-[var(--md-sys-color-error)] bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)]'
                            : 'border border-[var(--md-sys-color-outline-variant)] bg-white/70 text-[var(--md-sys-color-on-surface-variant)]'
                        }`}
                      >
                        No
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="mt-auto space-y-2 pt-2">
          <button
            onClick={handleStart}
            disabled={isSubmitting}
            className="w-full rounded-full border border-white/75 bg-[var(--md-sys-color-primary)] py-4 text-[11px] font-black uppercase tracking-[0.18em] text-[var(--md-sys-color-on-primary)] shadow-[0_16px_30px_rgba(122,93,32,0.18)] disabled:opacity-50"
          >
            {isSubmitting ? 'Sincronizando' : 'Comenzar sesion'}
          </button>
          <button
            onClick={onClose}
            disabled={isSubmitting}
            className="w-full rounded-full border border-[var(--md-sys-color-outline-variant)] bg-white/60 py-3 text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]"
          >
            Cancelar
          </button>
        </div>
      </div>
    </WorkoutDrawer>
  );
};

export default ReadinessDrawer;

