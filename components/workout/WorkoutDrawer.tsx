// components/workout/WorkoutDrawer.tsx
// Wrapper drawer unificado: gris medio-claro, sin bordes, sin colores llamativos

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
        className="fixed inset-0 z-[99999] bg-black/30 animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className="fixed bottom-0 left-0 right-0 z-[100000] bg-[#e5e5e5] flex flex-col animate-slide-up pb-[env(safe-area-inset-bottom,0px)]"
        style={{ maxHeight: height }}
      >
        <div className="flex items-center justify-between px-4 py-3 shrink-0">
          <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-[#1a1a1a]">
            {title}
          </h3>
          <button
            onClick={onClose}
            className="p-2 -mr-2 text-[#525252] hover:text-[#1a1a1a] transition-colors rounded-none hover:bg-black/5"
            aria-label="Cerrar"
          >
            <XIcon size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto min-h-0 pb-[max(2rem,env(safe-area-inset-bottom))]" style={{ WebkitOverflowScrolling: 'touch' }}>
          {children}
        </div>
      </div>
    </>
  );

  return ReactDOM.createPortal(content, document.body);
};

export default WorkoutDrawer;
