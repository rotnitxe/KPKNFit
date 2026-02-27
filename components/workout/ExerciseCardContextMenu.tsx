// components/workout/ExerciseCardContextMenu.tsx
// Menú contextual al mantener presionada una tarjeta del carrusel

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
        className="fixed inset-0 z-[150]"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className="fixed left-1/2 bottom-24 z-[151] -translate-x-1/2 bg-slate-900 border border-white/10 rounded-xl shadow-2xl overflow-hidden animate-fade-in min-w-[200px]"
      >
        <button
          type="button"
          onClick={() => { onReplace(); onClose(); }}
          className="w-full flex items-center gap-3 px-4 py-3 text-left text-white hover:bg-white/5 transition-colors"
        >
          <SwapIcon size={18} className="text-cyber-cyan" />
          <span className="text-sm font-semibold">Cambiar ejercicio</span>
        </button>
        <button
          type="button"
          onClick={() => { onSkip(); onClose(); }}
          className="w-full flex items-center gap-3 px-4 py-3 text-left text-white hover:bg-white/5 transition-colors border-t border-white/5"
        >
          <MinusIcon size={18} className="text-slate-400" />
          <span className="text-sm font-semibold">Omitir</span>
        </button>
      </div>
    </>
  );
};

export default ExerciseCardContextMenu;
