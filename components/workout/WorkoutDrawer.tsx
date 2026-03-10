// components/workout/WorkoutDrawer.tsx
// Drawer base para flujo Workout con lenguaje Material 3 + liquid glass.

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
        className="workout-modal-backdrop fixed inset-0 z-[99999] animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className="fixed bottom-0 left-0 right-0 z-[100000] liquid-glass-panel flex flex-col animate-slide-up pb-[env(safe-area-inset-bottom,0px)] rounded-t-[40px] border-t border-white/75"
        style={{
          maxHeight: height,
          boxShadow: '0 -20px 40px rgba(66,48,23,0.14), inset 0 1px 0 rgba(255,255,255,0.5)',
        }}
      >
        <div className="flex items-center justify-between px-6 py-4 shrink-0 border-b border-[var(--md-sys-color-outline-variant)]/35">
          <h3 className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-on-surface-variant)]">
            {title}
          </h3>
          <button
            onClick={onClose}
            className="workout-pressable p-2 -mr-2 text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] transition-colors rounded-full hover:bg-black/[0.04]"
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
