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

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-[110] flex items-end justify-center">
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="absolute inset-0 bg-black/40 backdrop-blur-[2px]"
        />

        <motion.div
          initial={{ translateY: '100%' }}
          animate={{ translateY: 0 }}
          exit={{ translateY: '100%' }}
          transition={{ type: 'spring', damping: 25, stiffness: 200 }}
          className="relative w-full max-w-lg liquid-glass rounded-t-[32px] overflow-hidden"
        >
          <div className="flex flex-col p-6 pb-12">
            <div className="self-center w-12 h-1.5 bg-white/20 rounded-full mb-8" />

            <div className="text-center mb-8">
              <h2 className="text-m3-headline text-white mb-1">¡Buen Trabajo!</h2>
              <p className="text-m3-on-surface-variant text-sm">{exerciseName}</p>
            </div>

            {/* Stats Summary */}
            <div className="grid grid-cols-3 gap-4 mb-10 px-4">
              <div className="flex flex-col items-center">
                <span className="text-m3-label text-m3-primary/60 mb-1">Series</span>
                <span className="text-2xl font-black text-white">{stats.sets}</span>
              </div>
              <div className="flex flex-col items-center border-x border-white/5">
                <span className="text-m3-label text-m3-primary/60 mb-1">Reps</span>
                <span className="text-2xl font-black text-white">{stats.reps}</span>
              </div>
              <div className="flex flex-col items-center">
                <span className="text-m3-label text-m3-primary/60 mb-1">Carga</span>
                <span className="text-2xl font-black text-white">{stats.weight}<span className="text-xs font-normal ml-0.5">{stats.unit}</span></span>
              </div>
            </div>

            {/* RPE Selector */}
            <div className="mb-10 text-center">
              <label className="text-m3-label text-m3-primary/80 mb-4 block">Nivel de Esfuerzo (RPE)</label>
              <div className="flex flex-wrap justify-center gap-2">
                {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(v => (
                  <button
                    key={v}
                    onClick={() => { hapticImpact(ImpactStyle.Light); setRpe(v); }}
                    className={`w-10 h-10 rounded-full flex items-center justify-center font-black text-sm transition-all border ${rpe === v ? 'bg-m3-primary border-m3-primary text-m3-on-primary' : 'bg-white/5 border-white/10 text-white/60'}`}
                  >
                    {v}
                  </button>
                ))}
              </div>
            </div>

            {/* Mood / Emoticons */}
            <div className="mb-12 text-center">
              <label className="text-m3-label text-m3-primary/80 mb-4 block">¿Cómo te sentiste?</label>
              <div className="flex justify-center gap-6">
                {[1, 2, 3, 4, 5].map(v => (
                  <button
                    key={v}
                    onClick={() => { hapticImpact(ImpactStyle.Light); setMood(v); }}
                    className={`text-3xl transition-transform ${mood === v ? 'scale-125' : 'opacity-40 grayscale'}`}
                  >
                    {v === 1 ? '😫' : v === 2 ? '😕' : v === 3 ? '😐' : v === 4 ? '🙂' : '🔥'}
                  </button>
                ))}
              </div>
            </div>

            <button
              onClick={handleSave}
              className="w-full py-4 rounded-full bg-m3-primary text-m3-on-primary font-black uppercase tracking-widest active:scale-95 transition-transform"
            >
              Guardar y Continuar
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};

export default PostExerciseDrawer;

