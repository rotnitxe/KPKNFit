import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircleIcon, ActivityIcon, StarIcon, InfoIcon } from '../icons';
import { hapticImpact, ImpactStyle } from '../../services/hapticsService';

export interface PostExerciseFeedback {
  technicalQuality: number;
  discomforts: string[];
  perceivedFatigue?: number;
  jointLoad?: number;
  mood?: number;
}

interface PostExerciseDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  exerciseName: string;
  onSave: (feedback: PostExerciseFeedback) => void;
  stats?: {
    sets: number;
    reps: number;
    weight: number;
    unit: string;
  };
}

const RPE_DESCRIPTIONS: Record<number, string> = {
  1: 'Muy facil',
  5: 'Moderado',
  8: 'Exigente',
  10: 'Fallo',
};

const MOOD_LABELS: Record<number, string> = {
  1: 'Drenado',
  2: 'Tenso',
  3: 'Estable',
  4: 'Con energia',
  5: 'Imparable',
};

const PostExerciseDrawer: React.FC<PostExerciseDrawerProps> = ({
  isOpen,
  onClose,
  exerciseName,
  onSave,
  stats = { sets: 3, reps: 12, weight: 100, unit: 'kg' },
}) => {
  const [technicalQuality, setTechnicalQuality] = useState(8);
  const [rpe, setRpe] = useState<number | null>(null);
  const [mood, setMood] = useState<number>(3);

  const handleSave = () => {
    hapticImpact(ImpactStyle.Medium);
    onSave({
      technicalQuality,
      perceivedFatigue: rpe || 5,
      discomforts: [],
      jointLoad: 5,
      mood,
    });
    onClose();
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-[110] flex items-end justify-center">
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="workout-modal-backdrop absolute inset-0"
        />

        <motion.div
          initial={{ translateY: '100%' }}
          animate={{ translateY: 0 }}
          exit={{ translateY: '100%' }}
          transition={{ type: 'spring', damping: 28, stiffness: 220 }}
          className="relative w-full max-w-lg liquid-glass-panel overflow-hidden rounded-t-[40px] border-t border-white/70"
        >
          <div className="flex flex-col gap-5 p-5 pb-10">
            <div className="mx-auto h-1.5 w-14 rounded-full bg-[var(--md-sys-color-outline-variant)]" />

            <div className="text-center">
              <div className="inline-flex items-center gap-2 rounded-full border border-white/70 bg-[var(--md-sys-color-secondary-container)] px-3 py-1.5 text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--md-sys-color-on-secondary-container)]">
                <ActivityIcon size={14} />
                Feedback de ejercicio
              </div>
              <h2 className="mt-3 text-[28px] font-medium tracking-[-0.03em] text-[var(--md-sys-color-on-surface)]">
                {exerciseName}
              </h2>
              <p className="mt-1 text-[13px] text-[var(--md-sys-color-on-surface-variant)]">
                Cierra el bloque con una lectura rapida de ejecucion, esfuerzo y sensacion general.
              </p>
            </div>

            <div className="grid grid-cols-3 gap-3">
              {[
                { label: 'Series', value: stats.sets, unit: '' },
                { label: 'Reps', value: stats.reps, unit: '' },
                { label: 'Carga', value: stats.weight, unit: stats.unit },
              ].map((stat) => (
                <div key={stat.label} className="rounded-[24px] border border-white/70 bg-white/55 p-4 text-center shadow-[0_10px_24px_rgba(80,58,23,0.08)]">
                  <span className="block text-[10px] font-semibold uppercase tracking-[0.14em] text-[var(--md-sys-color-on-surface-variant)]">
                    {stat.label}
                  </span>
                  <div className="mt-2 flex items-baseline justify-center gap-1">
                    <span className="text-[26px] font-medium tracking-[-0.03em] text-[var(--md-sys-color-on-surface)]">
                      {stat.value}
                    </span>
                    {stat.unit ? (
                      <span className="text-[11px] font-semibold uppercase text-[var(--md-sys-color-on-surface-variant)]">
                        {stat.unit}
                      </span>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>

            <div className="rounded-[28px] border border-white/70 bg-white/50 p-4 shadow-[0_10px_24px_rgba(80,58,23,0.08)]">
              <div className="mb-4 flex items-center justify-between gap-3">
                <div>
                  <span className="block text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">
                    Esfuerzo percibido
                  </span>
                  <p className="mt-1 text-[13px] text-[var(--md-sys-color-on-surface-variant)]">
                    {rpe ? RPE_DESCRIPTIONS[rpe] || `Nivel ${rpe}` : 'Selecciona el punto que mejor represente la serie.'}
                  </p>
                </div>
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-[var(--md-sys-color-tertiary-container)] text-[var(--md-sys-color-on-tertiary-container)]">
                  <InfoIcon size={18} />
                </div>
              </div>

              <div className="grid grid-cols-5 gap-2">
                {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((value) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => {
                      hapticImpact(ImpactStyle.Light);
                      setRpe(value);
                    }}
                    className={`min-h-[46px] rounded-[18px] border text-sm font-semibold transition-all ${
                      rpe === value
                        ? 'border-[var(--md-sys-color-primary)] bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] shadow-[0_10px_20px_rgba(122,93,32,0.12)]'
                        : 'border-[var(--md-sys-color-outline-variant)] bg-white/70 text-[var(--md-sys-color-on-surface-variant)]'
                    }`}
                  >
                    {value}
                  </button>
                ))}
              </div>
            </div>

            <div className="rounded-[28px] border border-white/70 bg-white/50 p-4 shadow-[0_10px_24px_rgba(80,58,23,0.08)]">
              <div className="mb-4 flex items-center gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)]">
                  <StarIcon size={18} />
                </div>
                <div>
                  <span className="block text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">
                    Calidad tecnica
                  </span>
                  <p className="mt-1 text-[13px] text-[var(--md-sys-color-on-surface-variant)]">
                    {technicalQuality}/10 segun control, estabilidad y limpieza.
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-5 gap-2">
                {[6, 7, 8, 9, 10].map((value) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => {
                      hapticImpact(ImpactStyle.Light);
                      setTechnicalQuality(value);
                    }}
                    className={`min-h-[46px] rounded-[18px] border text-sm font-semibold transition-all ${
                      technicalQuality === value
                        ? 'border-[var(--md-sys-color-tertiary)] bg-[var(--md-sys-color-tertiary-container)] text-[var(--md-sys-color-on-tertiary-container)] shadow-[0_10px_20px_rgba(47,107,104,0.12)]'
                        : 'border-[var(--md-sys-color-outline-variant)] bg-white/70 text-[var(--md-sys-color-on-surface-variant)]'
                    }`}
                  >
                    {value}
                  </button>
                ))}
              </div>
            </div>

            <div className="rounded-[28px] border border-white/70 bg-white/50 p-4 shadow-[0_10px_24px_rgba(80,58,23,0.08)]">
              <span className="block text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">
                Estado al cerrar
              </span>
              <div className="mt-3 grid grid-cols-5 gap-2">
                {[1, 2, 3, 4, 5].map((value) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => {
                      hapticImpact(ImpactStyle.Light);
                      setMood(value);
                    }}
                    className={`min-h-[62px] rounded-[20px] border px-2 py-3 text-center transition-all ${
                      mood === value
                        ? 'border-[var(--md-sys-color-primary)] bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)] shadow-[0_10px_20px_rgba(122,93,32,0.12)]'
                        : 'border-[var(--md-sys-color-outline-variant)] bg-white/70 text-[var(--md-sys-color-on-surface-variant)]'
                    }`}
                  >
                    <span className="block text-[18px] font-medium leading-none">{value}</span>
                    <span className="mt-2 block text-[9px] font-semibold uppercase tracking-[0.12em]">
                      {MOOD_LABELS[value]}
                    </span>
                  </button>
                ))}
              </div>
            </div>

            <div className="flex gap-3 pt-1">
              <button
                type="button"
                onClick={onClose}
                className="workout-pressable h-14 flex-1 rounded-full border border-[var(--md-sys-color-outline-variant)] bg-white/60 px-5 text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]"
              >
                Omitir
              </button>
              <button
                type="button"
                onClick={handleSave}
                className="workout-pressable h-14 flex-[1.4] rounded-full border border-white/70 bg-[var(--md-sys-color-primary)] px-5 text-[11px] font-black uppercase tracking-[0.16em] text-[var(--md-sys-color-on-primary)] shadow-[0_16px_30px_rgba(122,93,32,0.18)]"
              >
                <span className="inline-flex items-center gap-2">
                  <CheckCircleIcon size={18} />
                  Guardar y continuar
                </span>
              </button>
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};

export default PostExerciseDrawer;
