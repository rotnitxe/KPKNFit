// components/workout/WorkoutDrawer.tsx
// Wrapper drawer genérico para modales de WorkoutSession - estilo NERD

import React from 'react';
import ReactDOM from 'react-dom';
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

  const content = (
    <>
      <div
        className="fixed inset-0 z-[99999] bg-black/60 backdrop-blur-sm animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className="fixed bottom-0 left-0 right-0 z-[100000] bg-[#15171E] border-t border-white/10 rounded-t-2xl flex flex-col animate-slide-up pb-[max(1rem,env(safe-area-inset-bottom))]"
        style={{ maxHeight: height }}
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-white/10 shrink-0">
          <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90">
            {title}
          </h3>
          <button
            onClick={onClose}
            className="p-2 -mr-2 text-slate-500 hover:text-white transition-colors rounded-lg hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-cyber-cyan focus:ring-offset-2 focus:ring-offset-[#0a0a0a]"
            aria-label="Cerrar"
          >
            <XIcon size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto custom-scrollbar min-h-0 pb-8">
          {children}
        </div>
      </div>
    </>
  );

  return ReactDOM.createPortal(content, document.body);
};

export default WorkoutDrawer;
