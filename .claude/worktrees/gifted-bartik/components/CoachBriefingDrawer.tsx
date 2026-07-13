// components/CoachBriefingDrawer.tsx - Drawer de briefing del coach (migración de CoachBriefingModal)

import React from 'react';
import { SparklesIcon } from './icons';
import WorkoutDrawer from './workout/WorkoutDrawer';

interface CoachBriefingDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  briefing: string;
}

const CoachBriefingDrawer: React.FC<CoachBriefingDrawerProps> = ({ isOpen, onClose, briefing }) => {
  return (
    <WorkoutDrawer isOpen={isOpen} onClose={onClose} title="Informe Coach IA" height="85vh">
      <div className="p-5 flex flex-col items-center">
        <SparklesIcon size={40} className="text-cyber-cyan/80 mb-4" />
        <p className="text-slate-300 whitespace-pre-wrap text-center font-mono text-sm leading-relaxed">{briefing}</p>
        <button onClick={onClose} className="mt-8 w-full py-4 rounded-xl bg-cyber-cyan text-white font-mono font-black text-[10px] uppercase tracking-widest hover:bg-cyber-cyan/90 transition-colors border border-cyber-cyan/30">
          Entendido, ¡vamos!
        </button>
      </div>
    </WorkoutDrawer>
  );
};

export default CoachBriefingDrawer;
