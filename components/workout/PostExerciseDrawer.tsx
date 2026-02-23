// components/workout/PostExerciseDrawer.tsx
// Drawer de feedback post-ejercicio - migración de InlineFeedbackCard

import React, { useState } from 'react';
import { ActivityIcon, CheckCircleIcon } from '../icons';
import { hapticImpact } from '../../services/hapticsService';
import { ImpactStyle } from '../../services/hapticsService';
import WorkoutDrawer from './WorkoutDrawer';

interface PostExerciseDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  exerciseName: string;
  onSave: (feedback: {
    jointLoad: number;
    technicalQuality: number;
    perceivedFatigue: number;
  }) => void;
}

const PostExerciseDrawer: React.FC<PostExerciseDrawerProps> = ({
  isOpen,
  onClose,
  exerciseName,
  onSave,
}) => {
  const [jointLoad, setJointLoad] = useState(5);
  const [technicalQuality, setTechnicalQuality] = useState(8);
  const [perceivedFatigue, setPerceivedFatigue] = useState(5);

  const handleSave = () => {
    hapticImpact(ImpactStyle.Medium);
    onSave({ jointLoad, technicalQuality, perceivedFatigue });
    onClose();
  };

  return (
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Feedback Post-Ejercicio" height="75vh">
      <div className="p-5 space-y-6">
        <div className="text-center">
          <p className="text-[10px] font-mono font-black uppercase tracking-widest text-slate-500">
            ¿Cómo sentiste el {exerciseName}?
          </p>
        </div>

        <div className="space-y-6">
          <div>
            <div className="flex justify-between text-[10px] font-mono font-bold text-slate-400 mb-2">
              <span>Carga Articular</span>
              <span className={jointLoad > 7 ? 'text-red-400' : 'text-green-400'}>{jointLoad}/10</span>
            </div>
            <input
              type="range"
              min="1"
              max="10"
              value={jointLoad}
              onChange={(e) => setJointLoad(parseInt(e.target.value))}
              className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-sky-500"
            />
          </div>
          <div>
            <div className="flex justify-between text-[10px] font-mono font-bold text-slate-400 mb-2">
              <span>Técnica</span>
              <span className={technicalQuality < 5 ? 'text-red-400' : 'text-green-400'}>
                {technicalQuality}/10
              </span>
            </div>
            <input
              type="range"
              min="1"
              max="10"
              value={technicalQuality}
              onChange={(e) => setTechnicalQuality(parseInt(e.target.value))}
              className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-purple-500"
            />
          </div>
          <div>
            <div className="flex justify-between text-[10px] font-mono font-bold text-slate-400 mb-2">
              <span>Fatiga Percibida</span>
              <span className="text-cyber-cyan">{perceivedFatigue}/10</span>
            </div>
            <input
              type="range"
              min="1"
              max="10"
              value={perceivedFatigue}
              onChange={(e) => setPerceivedFatigue(parseInt(e.target.value))}
              className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-[#00F0FF]"
            />
          </div>
        </div>

        <button
          onClick={handleSave}
          className="w-full py-4 rounded-xl bg-cyber-cyan text-white font-mono font-black text-[10px] uppercase tracking-widest hover:bg-cyber-cyan/90 transition-colors border border-cyber-cyan/30 flex items-center justify-center gap-2"
        >
          <CheckCircleIcon size={18} />
          Completar Ejercicio
        </button>
      </div>
    </WorkoutDrawer>
  );
};

export default PostExerciseDrawer;
