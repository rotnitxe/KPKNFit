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
        className="fixed inset-0 z-[99999] bg-black/40 backdrop-blur-sm animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className="fixed bottom-0 left-0 right-0 z-[100000] bg-[var(--md-sys-color-surface-container-high)] flex flex-col animate-slide-up pb-[env(safe-area-inset-bottom,0px)] rounded-t-[32px] shadow-[0_-8px_32px_rgba(0,0,0,0.08)] border-t border-[var(--md-sys-color-outline-variant)]/30"
        style={{ maxHeight: height }}
      >
        <div className="flex items-center justify-between px-6 py-4 shrink-0">
          <h3 className="text-[12px] font-black uppercase tracking-[0.2em] text-[var(--md-sys-color-on-surface)]">
            {title}
          </h3>
          <button
            onClick={onClose}
            className="p-2 -mr-2 text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] transition-colors rounded-full hover:bg-black/5"
            aria-label="Cerrar"
          >
            <XIcon size={24} />
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
