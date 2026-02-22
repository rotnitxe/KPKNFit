// components/workout/WorkoutDrawer.tsx
// Wrapper drawer genérico para modales de WorkoutSession - estilo NERD

import React from 'react';
import { XIcon } from '../icons';

interface WorkoutDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  height?: string;
}

const WorkoutDrawer: React.FC<WorkoutDrawerProps> = ({
  isOpen,
  onClose,
  title,
  children,
  height = '90vh',
}) => {
  if (!isOpen) return null;

  return (
    <>
      <div
        className="fixed inset-0 z-[110] bg-black/60 backdrop-blur-sm animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className="fixed bottom-0 left-0 right-0 z-[111] bg-[#0a0a0a] border-t border-orange-500/20 rounded-t-2xl flex flex-col animate-slide-up"
        style={{ maxHeight: height }}
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-orange-500/10 shrink-0">
          <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90">
            {title}
          </h3>
          <button
            onClick={onClose}
            className="p-2 -mr-2 text-slate-500 hover:text-white transition-colors rounded-lg hover:bg-white/5"
            aria-label="Cerrar"
          >
            <XIcon size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto custom-scrollbar min-h-0">
          {children}
        </div>
      </div>
    </>
  );
};

export default WorkoutDrawer;
