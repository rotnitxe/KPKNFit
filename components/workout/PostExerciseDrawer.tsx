import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircleIcon, XIcon, StarIcon, ActivityIcon, InfoIcon } from '../icons';
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

const PostExerciseDrawer: React.FC<PostExerciseDrawerProps> = ({
  isOpen,
  onClose,
  exerciseName,
  onSave,
  stats = { sets: 3, reps: 12, weight: 100, unit: 'kg' }
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
      mood
    });
    onClose();
  };

  const RPE_DESCRIPTIONS: Record<number, string> = {
    1: "Muy Fácil",
    5: "Moderado",
    8: "Exigente",
    10: "Fallo"
  };

  const MOOD_LABELS: Record<number, string> = {
    1: "Agotado",
    2: "Cansado",
    3: "Bien",
    4: "Enérgico",
    5: "Imparable"
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
          className="absolute inset-0 bg-black/60 backdrop-blur-[4px]"
        />

        <motion.div
          initial={{ translateY: '100%' }}
          animate={{ translateY: 0 }}
          exit={{ translateY: '100%' }}
          transition={{ type: 'spring', damping: 28, stiffness: 220 }}
          className="relative w-full max-w-lg liquid-glass rounded-t-[40px] overflow-hidden"
          style={{
            background: 'linear-gradient(180deg, rgba(28, 27, 31, 0.85) 0%, rgba(15, 15, 15, 0.95) 100%)',
            borderTop: '1px solid rgba(255,255,255,0.15)'
          }}
        >
          <div className="flex flex-col p-6 pb-12">
            <div className="self-center w-12 h-1.5 bg-white/10 rounded-full mb-8" />

            <div className="text-center mb-8">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[var(--m3-primary-container)]/30 border border-[var(--m3-primary)]/20 mb-3">
                <ActivityIcon size={14} className="text-[var(--m3-primary)]" />
                <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--m3-primary)]">Feedback de Ejercicio</span>
              </div>
              <h2 className="text-2xl font-black text-white">{exerciseName}</h2>
            </div>

            {/* Premium Stats Grid */}
            <div className="grid grid-cols-3 gap-2 mb-10">
              {[
                { label: 'Series', value: stats.sets, unit: '' },
                { label: 'Reps', value: stats.reps, unit: '' },
                { label: 'Carga', value: stats.weight, unit: stats.unit }
              ].map((stat, i) => (
                <div key={i} className="bg-white/5 border border-white/10 rounded-2xl p-4 flex flex-col items-center justify-center">
                  <span className="text-[9px] font-bold uppercase tracking-wider text-white/40 mb-1">{stat.label}</span>
                  <div className="flex items-baseline gap-0.5">
                    <span className="text-xl font-black text-white">{stat.value}</span>
                    {stat.unit && <span className="text-[10px] font-medium text-white/50">{stat.unit}</span>}
                  </div>
                </div>
              ))}
            </div>

            {/* RPE Interaction */}
            <div className="mb-10">
              <div className="flex items-center justify-between mb-4 px-1">
                <label className="text-xs font-bold uppercase tracking-widest text-white/60">Esfuerzo Percibido (RPE)</label>
                <span className="text-xs font-bold text-[var(--m3-primary)]">{rpe ? RPE_DESCRIPTIONS[rpe] || `Nivel ${rpe}` : 'Selecciona'}</span>
              </div>
              <div className="flex justify-between gap-1.5">
                {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(v => (
                  <button
                    key={v}
                    onClick={() => { hapticImpact(ImpactStyle.Light); setRpe(v); }}
                    className={`flex-1 aspect-square rounded-xl flex items-center justify-center font-black text-sm transition-all border ${rpe === v
                      ? 'bg-[var(--m3-primary)] border-[var(--m3-primary)] text-[var(--m3-on-primary)] shadow-lg shadow-[var(--m3-primary)]/20'
                      : 'bg-white/5 border-white/10 text-white/40 hover:border-white/30'
                      }`}
                  >
                    {v}
                  </button>
                ))}
              </div>
            </div>

            {/* Emotional Feedback */}
            <div className="mb-12">
              <label className="text-xs font-bold uppercase tracking-widest text-white/60 mb-6 block text-center">Estado de Ánimo</label>
              <div className="flex justify-between items-center px-4">
                {[1, 2, 3, 4, 5].map(v => (
                  <div key={v} className="flex flex-col items-center gap-3">
                    <button
                      onClick={() => { hapticImpact(ImpactStyle.Light); setMood(v); }}
                      className={`text-4xl transition-all duration-300 ${mood === v ? 'scale-125 drop-shadow-[0_0_15px_rgba(255,255,255,0.3)]' : 'opacity-30 scale-90 grayscale'
                        }`}
                    >
                      {v === 1 ? '😫' : v === 2 ? '😕' : v === 3 ? '😐' : v === 4 ? '🙂' : '🔥'}
                    </button>
                    <span className={`text-[9px] font-bold uppercase transition-opacity duration-300 ${mood === v ? 'opacity-100 text-white' : 'opacity-0'}`}>
                      {MOOD_LABELS[v]}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <button
              onClick={handleSave}
              className="w-full h-16 rounded-full bg-[var(--m3-primary)] text-[var(--m3-on-primary)] font-black text-sm uppercase tracking-[0.15em] flex items-center justify-center gap-2 active:scale-95 transition-all shadow-xl shadow-[var(--m3-primary)]/10"
            >
              <CheckCircleIcon size={20} />
              Guardar y Continuar
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};

export default PostExerciseDrawer;


