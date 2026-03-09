// components/workout/ExerciseCardContextMenu.tsx
// Menu contextual al mantener presionada una tarjeta del carrusel.

import React from 'react';
import { SwapIcon, MinusIcon } from '../icons';

interface ExerciseCardContextMenuProps {
  isOpen: boolean;
  onClose: () => void;
  onReplace: () => void;
  onSkip: () => void;
}

const ExerciseCardContextMenu: React.FC<ExerciseCardContextMenuProps> = ({
  isOpen,
  onClose,
  onReplace,
  onSkip,
}) => {
  if (!isOpen) return null;

  return (
    <>
      <div
        className="fixed inset-0 z-[150] bg-black/20 backdrop-blur-[3px]"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className="fixed left-1/2 bottom-24 z-[151] liquid-glass-panel -translate-x-1/2 rounded-[18px] border border-[var(--md-sys-color-outline-variant)]/70 overflow-hidden animate-fade-in min-w-[220px]"
        style={{
          background: 'linear-gradient(160deg, rgba(254,247,255,0.95) 0%, rgba(247,242,250,0.90) 100%)',
          backdropFilter: 'blur(18px) saturate(140%)',
          WebkitBackdropFilter: 'blur(18px) saturate(140%)',
          boxShadow: '0 20px 35px rgba(0,0,0,0.18)',
        }}
      >
        <button
          type="button"
          onClick={() => { onReplace(); onClose(); }}
          className="workout-pressable w-full flex items-center gap-3 px-4 py-3 text-left text-[var(--md-sys-color-on-surface)] hover:bg-black/[0.03] transition-colors"
        >
          <SwapIcon size={18} className="text-[var(--md-sys-color-primary)]" />
          <span className="text-sm font-medium">Cambiar ejercicio</span>
        </button>
        <button
          type="button"
          onClick={() => { onSkip(); onClose(); }}
          className="workout-pressable w-full flex items-center gap-3 px-4 py-3 text-left text-[var(--md-sys-color-on-surface)] hover:bg-black/[0.03] transition-colors border-t border-[var(--md-sys-color-outline-variant)]/50"
        >
          <MinusIcon size={18} className="text-[var(--md-sys-color-on-surface-variant)]" />
          <span className="text-sm font-medium">Omitir</span>
        </button>
      </div>
    </>
  );
};

export default ExerciseCardContextMenu;


